# Runbook — Retenção, arquivamento, purge e restore de staging

Este runbook descreve o mecanismo local V2-045a. Ele não aprova os candidatos de 7/30 dias, não
autoriza ativação produtiva, deploy, criação de membership, DML manual nem acesso a banco remoto.
Enquanto V2-045b estiver em `EXTERNAL_HOLD`, use somente fixtures sintéticas no SQL Server local
autorizado e transações rollback-only.

## Invariantes

- O alvo local permitido é exatamente `localhost/ETL_SISTEMA_V2_SHADOW`, com Windows
  Authentication. Nunca aponte estes comandos ao legado, produção ou dashboards.
- Uma política ausente, revogada ou sem evidências e papéis distintos para as duas ratificações
  bloqueia o plan.
- O relógio e o cutoff vêm do SQL Server. Operadores não fornecem TTL ou timestamp de corte.
- Legal hold prevalece sobre plan, archive e purge; o estado base precisa coincidir com os eventos
  `PLACED`/`RELEASED`, inclusive motivo, escopo, papel, evidência e timestamp.
- A trilha de estado deve respeitar origem, cardinalidade, sequência contígua, relógio e grafo
  semântico, e o último evento deve coincidir com estado e timestamp terminal da execução; plan,
  archive e purge falham fechado se qualquer parte divergir.
- Só `stg.execution_candidate` e `stg.execution_record` entram na allowlist de hard delete.
- Archive verificado precede purge. Quarantine, auditoria, recon, histórico, `core`, `mart` e `pub`
  nunca são apagados por este fluxo.
- Restore materializa cópia limitada em `recon.staging_restore_*`; nunca repovoa staging nem
  promove/publica dados. Não há reader público de linhas/chaves restauradas sem receipt auditado.
- Não execute DML direto, altere os procedures, contorne o application lock ou conceda lifecycle a
  `v2_runtime`.
- IDs, fingerprints, contagens e tempos operacionais pertencem à evidência restrita. O repositório
  e logs compartilhados recebem somente resultado sanitizado e agregado.
- Policy de logs disabled ou em legal hold ainda revalida marker/receipts/tombstones sob o fence da
  origem; a operação apontada pelo marker atual incompleta falha fechado, enquanto uma origem nova
  permanece sem artefatos.

## Papéis e segregação

| Ação | Role SQL mínima | Owner-papel operacional |
|---|---|---|
| Aprovar/revogar política; colocar/liberar hold | `v2_retention_governor` | data owner/compliance para política; autoridade de hold definida na política |
| Plan/dry-run | `v2_lifecycle_reviewer` | revisão operacional autorizada |
| Archive e purge | `v2_lifecycle_operator` | Operações, após gates e janela aprovados |
| Materializar restore; verifier interno faz read-back | `v2_archive_restorer` | Operações/DBA, conforme incidente aprovado |

Nenhuma membership é criada pela migration. Identidades reais, segregação nominal e concessões
pertencem ao owner de identidade/DBA e ao gate produtivo.

## Validação local segura

Confirme primeiro o alvo exato consultando `master`. O runner abaixo faz essa guarda novamente,
recompõe V001–V007, executa os cenários dentro de transações e confirma rollback integral; a
extensão tipada e minimizada de Usuários é exercitada pelos cenários 027 (lifecycle completo) e 029
(sidecar tardio recusado):

```powershell
.\scripts\validation\Invoke-ProgressiveDataGate.ps1
```

Para validar apenas contratos estáticos e o manifesto do lifecycle:

```powershell
.\scripts\validation\Test-StagingLifecycleManifest.ps1
.\scripts\validation\Test-ProgressiveDataGate.ps1
```

Para reproduzir somente contenção entre duas sessões:

```powershell
.\scripts\validation\Test-StagingLifecycleConcurrency.ps1
```

Para recompilar os planos estimados reais sob rollback e verificar índice de seleção, referência
somente ao database local e ausência de warnings operacionais:

```powershell
.\scripts\validation\Test-StagingLifecycleShowplan.ps1
```

Para o lifecycle local de logs, execute apenas o teste com diretórios/arquivos sintéticos; ele
cobre dry-run, execução, retries, corrupção, caps, locks e recuperação sem ativar job:

```powershell
.\scripts\validation\Test-GovernedLogLifecycle.ps1
```

Esses comandos não aprovam política produtiva, storage físico ou RTO/RPO. Não edite o exercício
para usar IDs, payloads, URLs, cursores ou documentos reais.

## Sequência operacional futura

Esta sequência só pode ser usada fora do exercício local depois da liberação objetiva de
V2-045b e da autorização nominal da janela:

1. Verificar a política versionada ativa, seus dois fingerprints de evidência, escopo, versão e
   autoridade de hold/release. Não copie o conteúdo das evidências para logs.
2. Confirmar backup/restore, criptografia, capacidade do archive, alertas e rollback conforme os
   controles aprovados por DBA/Operações.
3. Executar plan/dry-run com limites abaixo dos tetos máximos. Revisar somente contagens agregadas,
   bytes estimados, classes terminais e resultado de bloqueios.
4. Parar se houver lease ativa, hold, estado inesperado, staging vazio inconsistente, excesso de
   limite, política revogada ou divergência entre contagem e escopo.
5. Executar archive para o mesmo `plan_id`. Conferir o atestado verificado e as contagens
   agregadas; retry deve usar exatamente a mesma identidade e parâmetros.
6. Executar purge somente para o plano arquivado e dentro da janela autorizada. O procedure
   revalida todas as condições sob o lock antes do primeiro delete.
7. Conferir o receipt imutável, a ausência apenas das linhas de staging previstas e a preservação
   de todas as evidências duráveis. Não usar consultas ad hoc que exponham conteúdo.
8. Registrar resultado categórico, versão da política, contagens agregadas e referência restrita da
   evidência; nunca registrar IDs, fingerprints completos ou dados de negócio no handoff público.

Os tetos absolutos do contrato local são: 1.000 execuções selecionadas, 5.000 examinadas, 1.000.000
de records, 1.000.000 de candidates, 5.000.000 de evidências, 100.000 linhas combinadas admitidas,
10.000.000 probes, 4.096 linhas somadas de ledger de hold por execução, sete eventos de estado por
execução e 1 GiB estimado. Eles são limites de admissão e não provam teto global de I/O,
tempo, tempdb ou memória do SQL Server. Use valores menores aprovados na janela e pare quando o
plano sinalizar truncamento/cursor ou item oversized.

## Legal hold

- Coloque o hold antes de qualquer plan/archive/purge. Todas as ações usam o mesmo lock global.
- Hold ativo bloqueia até planos criados anteriormente; o purge revalida o estado atual.
- Liberação exige o papel e a evidência definidos na política governada. Não reutilize o motivo de
  criação como autorização implícita.
- Uma falha ou disputa de lock não autoriza retry com DML manual. Preserve o hold e escale ao
  owner-papel correspondente.
- Divergência entre a linha base e o ledger é incidente de integridade. Não “corrija” evento ou
  timestamp com `UPDATE`; preserve evidência, bloqueie a janela e escale ao owner/DBA.

## Restore e read-back

Use restore somente a partir de manifesto marcado como verificado e com limites explícitos de
registros e candidatos. O resultado esperado é uma sessão idempotente em `recon` com
`read_only = 1` e igualdade bidirecional com o archive. Se o mesmo `restore_id` reaparecer com
escopo diferente, trate como incidente e não tente corrigir a trilha.

O read-back é interno ao verifier e produz somente resultado agregado. A role de restore não recebe
`SELECT` direto nem procedure para exportar conteúdo; qualquer leitura futura exige desenho próprio
com receipt, autorização, caps e evidência sanitizada.

O mecanismo local não substitui restore físico. Perda/corrupção do banco, indisponibilidade do
archive, falha de backup ou necessidade de RTO/RPO exige procedimento aprovado por DBA/Operações;
não reidrate `stg`, não execute promoção e não publique como atalho.

## Lifecycle local de logs

`scripts/operations/Invoke-GovernedLogLifecycle.ps1` é um comando manual PowerShell 7, não um job.
Ele aceita somente diretórios locais existentes, distintos e fora de raízes de filesystem; recusa
UNC, reparse points e relação de ancestralidade entre origem/archive. A policy estrita inclui
`retentionDays`, `maximumEntriesScanned`, `maximumFilesPerRun`, `maximumSingleFileBytes`,
`maximumSourceBytes`, fingerprints de policy/escopo, flags de legal hold e evidências categóricas de
data owner/compliance. Os tetos locais são 36.500 dias, 100.000 entradas, 1.000 arquivos e 1 GiB por
arquivo/origem; isso não os transforma em valores produtivos aprovados.

Sem `-Execute`, o comando adquire os fences existentes, revalida marker anterior e retorna apenas
preview agregado, sem criar artefato ou apagar arquivo. Com `-Execute`, mutex/file locks de origem e
escopo cercam scan e transição; o arquivo é copiado por stream para pending, recebe flush durável e
SHA-256, depois permit receipt, tombstone/delete da origem e completion receipt. O marker persistente
`ACTIVE/COMPLETE` vincula archive root, escopo, policy, receipt e relógio monotônico. Retry só retorna
sucesso após read-back de receipts, archives, hashes e ausência da origem/tombstone da operação
apontada pelo marker atual. Artefato referenciado ausente ou divergente e tombstone órfão no escopo
atual falham fechado. Como o marker é sobrescrito por uma operação posterior, o comando não prova a
integridade contínua do histórico anterior; essa garantia requer os controles externos de
cold storage/WORM e backup de V2-045b.

Enquanto V2-045b estiver em `EXTERNAL_HOLD`, não crie policy real, não use diretórios de serviço,
não agende o script e não habilite exclusão do Logback. Cold storage/WORM, ACL da identidade de
serviço, redaction produtiva, TTL, capacidade, backup e observabilidade ainda exigem ratificação e
prova externa.

## Condições de parada

- V2-045b sem política ratificada e aceite nominal.
- Alvo diferente do banco local autorizado durante validação offline.
- Política/hold/plan/archive alterados, expirados, revogados ou divergentes.
- Lease ativa, ocorrência não terminal ou `terminal_at_utc` inconsistente.
- Limite de execuções examinadas/selecionadas, staging, candidatos, evidências, conteúdo, probes ou
  bytes excedido.
- Archive não verificado, contagem/fingerprint divergente ou lineage ausente.
- Contenção de lock, erro não categorizado ou qualquer tentativa de acesso direto às tabelas.
- Saída capaz de expor segredo, URL, payload, cursor, documento, ID ou dado de negócio.

## Evidência sanitizada mínima

- Data/hora, ambiente categórico e papel executor, sem identidade real no repositório.
- Versão da política e confirmação categórica das duas aprovações, sem seu conteúdo.
- Limites solicitados e contagens agregadas de elegível/bloqueado/arquivado/purgado/restaurado.
- Resultado `PASS/FAIL/BLOCKED`, código sanitizado e estado do lock/hold.
- Resultado de verificação do archive/restore interno, sem linhas, chaves ou fingerprints completos.
- Referência restrita da autorização, janela, backup/restore e plano de recuperação.

## Gate externo para ativação

V2-045b só sai de `EXTERNAL_HOLD` quando existir artefato versionado, nominalmente aceito, que
defina TTLs de staging, lifecycle de logs, retenção/arquivo, autoridade de hold/release e owner por
escopo, com controles físicos comprovados por DBA/Operações. Até lá, `maxHistory` permanece zero,
nenhuma política é semeada e este runbook serve apenas para validação local rollback-only.
