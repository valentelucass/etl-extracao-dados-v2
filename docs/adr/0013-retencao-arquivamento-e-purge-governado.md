# ADR 0013 — Retenção, arquivamento e purge governado de staging

- Status: Aceito e implementado offline por V2-045a; ativação produtiva em `EXTERNAL_HOLD`
- Data: 2026-08-31

## Contexto

O staging V2 é transitório, mas não pode ser removido por idade presumida, relógio do caller ou
job com acesso direto às tabelas. O purge também não pode apagar quarantine, reconciliação,
auditoria, histórico, domínio ou fatos. As dependências de lineage criadas pelo kernel V2-021
precisam continuar verificáveis depois da retirada das linhas transitórias.

Os prazos de sete dias para execuções publicadas e trinta dias para execuções falhas são apenas
candidatos do catálogo. Não existe evidência versionada de aceite por data owner e compliance, nem
prova local de cold storage, criptografia física, backup/restore ou RTO/RPO produtivos. Por isso,
V2-045 separa o mecanismo local seguro da ratificação e ativação operacional.

## Decisão

A V005 cria um lifecycle fail-closed, desabilitado na ausência de política ativa e duplamente
ratificada. Nenhuma política ou TTL é semeado. A aprovação exige fingerprints independentes das
evidências do data owner e de compliance, papéis distintos e uma versão de política; revogação produz
evento imutável. TTL e cutoff nunca são parâmetros de plan, archive ou purge. O SQL Server obtém o
instante depois de adquirir o lock e calcula o cutoff a partir da política ratificada.

São elegíveis apenas ocorrências com staging não vazio, `terminal_at_utc` coerente e estado:

- `PUBLISHED`, pela política da classe publicada; ou
- `BLOCKED`, `FAILED`, `CANCELLED` ou `DEGRADED`, pela política da classe terminal não publicada.

Estados intermediários e `PROMOTED` nunca são elegíveis. `SKIPPED` e `NOT_APPLICABLE` são terminais
sem carga: qualquer staging nessas ocorrências é uma inconsistência fail-closed, não material para
purge. Lease ativa, legal hold ativa ou com ledger divergente, ausência/revogação da política,
archive ausente, mudança de evidência ou qualquer inconsistência bloqueiam a operação.
O último `execution_state_event` também precisa ter sequência, estado e timestamp exatamente iguais
ao estado terminal do `execution_attempt`; essa prova é refeita no plan, archive e purge.

O fluxo autorizado é:

1. `stg.usp_plan_staging_lifecycle` produz um dry-run persistido e imutável, com escopo exato,
   cutoff calculado pelo banco, contagens, bytes estimados e limites explícitos.
2. `stg.usp_archive_staging_lifecycle` copia dez fontes tipadas para dez tabelas de archive em
   `recon`: os dois conjuntos de staging e oito conjuntos de quarantine/auditoria/reconciliação.
   Cada linha recebe atestado SHA-256; raiz de conteúdo e fingerprint v3 do manifesto são
   recalculados por verifier independente, além de `COUNT_BIG` e `EXCEPT` bidirecional.
3. `stg.usp_purge_staging_lifecycle` revalida política, estado terminal, lease, hold, plano,
   contagens e atestado de archive sob lock. Só então apaga, nesta ordem,
   `stg.execution_candidate` e `stg.execution_record`.
4. `recon.usp_restore_staging_archive` materializa uma cópia persistente, única, verificada e
   limitada nas tabelas `recon.staging_restore_*`. Restore nunca repovoa staging ativo, aplica
   candidato, publica ou altera `core`. Não existe entrypoint público para ler linhas/chaves dessa
   materialização sem um futuro protocolo de receipt de leitura auditado.

Policy, hold, plan, archive, purge e restore compartilham o application lock transacional exclusivo
`V2_STAGING_LIFECYCLE`, com timeout finito. Cada comando possui chave idempotente e rejeita retry
divergente. Os planos persistem cursor `(terminal_at_utc, execution_id)` e limites de execuções
examinadas/selecionadas, staging, candidatos, evidências, conteúdo combinado, probes e bytes. Esses
tetos são bounds lógicos de admissão, não alegação de limite global de I/O físico. Não existe
travessia Java ou coleção ilimitada. A trilha de política, hold, archive e purge permanece fora da
allowlist de hard delete.

O estado base de policy e legal hold precisa coincidir integralmente com seus eventos imutáveis.
Retry de aprovação/revogação, colocação/liberação e qualquer plan/archive/purge falham se papel,
fingerprint, escopo, motivo ou timestamp divergir. Retry de purge também falha se staging reaparecer
depois do receipt; não o apaga silenciosamente.

O histórico de legal hold é indexado por execução e limitado tecnicamente a 4.096 linhas somadas de
base e eventos por execução, sempre reservando espaço para a futura liberação. Plan, archive e purge
contam somente o escopo limitado sob análise e submetem essas linhas ao teto explícito de
probes. A trilha terminal também é limitada a sete eventos e valida origem, cardinalidade,
continuidade, relógio, grafo semântico e correspondência do último evento com a tentativa.

As FKs de quarantine e candidate application para staging foram substituídas por triggers que
exigem lineage tipada existente no staging ativo ou no archive. Assim, `recon.quarantine_record`,
`recon.execution_candidate_application`, resultados de reconciliação e eventos de `ctl` continuam
duráveis quando o staging elegível é removido.

Menor privilégio é separado por função:

- `v2_retention_governor`: aprova/revoga política e coloca/libera legal hold;
- `v2_lifecycle_reviewer`: executa somente o plan/dry-run;
- `v2_lifecycle_operator`: executa somente archive e purge;
- `v2_archive_restorer`: executa somente a materialização de restore, cujo verifier interno faz o
  read-back bidirecional.

As roles não recebem membership automático, DDL ou acesso direto às tabelas. `v2_runtime` não
recebe nenhum entrypoint de lifecycle. O migrator publica esses grants de objeto por um helper
interno em `dbo`, `EXECUTE AS OWNER`, com allowlist de schemas, roles e somente procedures. Os sete
schemas V2 pertencem a `v2_schema_owner`, usuário sem login e sem impersonação de `dbo`; módulos
owner-context em schema mutável ficam confinados a esse principal. O migrator não recebe `CONTROL`,
`db_owner` nem `EXECUTE` amplo. A tabela de allowlist e o helper negam ao `public` leitura/DML,
alteração e tomada de ownership; o helper também rejeita publicar qualquer alvo com `EXECUTE AS`.
A composição de comandos operacionais pertence a V2-022
e não transforma o `dry-run` de bootstrap, que não abre I/O, em purge implícito.

Fingerprints/evidências entram nos procedures como Unicode e são normalizados somente depois de
limites de bytes; a gramática ASCII hexadecimal é então validada com collation BIN2. Isso impede
conversão ANSI best-fit de homoglyphs antes da validação.

O lifecycle local de logs é manual e opt-in. Ele exige política JSON estrita, raízes locais
separadas, TTL e caps explícitos, fingerprints de policy/escopo e evidências distintas de data
owner e compliance.
Mutex e file lock cercam origem e archive; marker persistente `ACTIVE/COMPLETE`, permit e completion
receipts impedem replay divergente. A cópia é streaming, recebe flush/hash antes do tombstone da
origem e revalida integralmente o retry da operação apontada pelo marker atual. Dry-run não apaga
nem cria artefato. O marker é um ponteiro mutável para a operação mais recente: a integridade
contínua de archives/receipts de operações anteriores não é provada pelo comando local e depende
dos controles de cold storage/WORM e backup de V2-045b. O Logback
continua com `maxHistory=0`; nenhum cron/job produtivo foi ativado. Policy disabled ou em legal hold
também adquire o fence da origem e falha diante de marker, receipt ou tombstone incompleto, mas não
cria lock file, namespace ou receipt em uma origem nova.

## Limite da decisão local

O archive implementado é uma cópia lógica tipada no próprio banco V2 para provar lineage,
atestado, purge e read-back de maneira rollback-only. Ele não é evidência de cold storage,
particionamento físico, isolamento de mídia, TDE, backup/restore operacional, RTO ou RPO. O
`maxHistory` destrutivo do Logback permanece desabilitado.

V2-045b continua em `EXTERNAL_HOLD`. Ativação produtiva exige artefato versionado e aceite nominal
de data owner/compliance para TTLs, lifecycle de logs, retenção de archive e autoridade de
hold/release, além dos controles físicos aplicáveis por DBA/Operações. Os candidatos de 7/30 dias
não foram ratificados por este ADR.

## Evidência local

O gate local recompõe V001–V005 em transação, valida manifesto/fingerprint, shapes exatos de 21 PKs,
8 UQs, 38 FKs e 17 índices, roles/grants e fixtures exclusivamente sintéticas. Os exercícios
013–020 cobrem antes/no/depois do cutoff, estado não terminal, lease, hold e ledger adulterado,
ratificação adulterada, fingerprint ASCII/BIN2, aplicação real de V005 pela role migrator, archive
obrigatório, retry de hold com `RELEASED` espúrio e aresta semanticamente impossível na cadeia terminal,
plan/archive/purge/restore, limites, concorrência e rollback integral. O
SHOWPLAN local compilou os entrypoints e exige o índice de seleção, zero warning operacional e
nenhuma referência cross-database; conversões explícitas de serialização/hash são reportadas à
parte. Isso não é medição de escala, que continua em V2-050.

## Consequências

- Não há purge automático nem TTL produtivo por default.
- Hard delete permanece restrito às duas tabelas `stg` declaradas no manifesto.
- Quarantine, auditoria, reconciliação, histórico, domínio e fatos não são apagados pela aplicação.
- O relógio do caller não consegue antecipar elegibilidade.
- Restore local é verificável e read-only, mas recuperação física produtiva ainda precisa de
  ratificação e ensaio próprios.
- V2-023 persiste DQ, métricas e alertas fora do conjunto de hard delete V005, sem ativar prazos
  produtivos; a política de retenção dessas evidências permanece em V2-045b.

## Alternativas rejeitadas

- **Job com `DELETE` direto ou `TRUNCATE`:** contorna gates, lineage e trilha imutável.
- **TTL fixo no código ou em configuração runtime:** apresenta candidato como política aprovada.
- **Cutoff fornecido pelo caller:** permite clock skew ou escolha arbitrária da janela.
- **Cascade sobre evidência durável:** apaga prova necessária para auditoria e replay.
- **Restore para staging ativo:** pode reexecutar promoção/publicação fora do protocolo normal.
- **Archive somente declarativo:** não comprova cópia, conteúdo nem read-back antes do purge.
