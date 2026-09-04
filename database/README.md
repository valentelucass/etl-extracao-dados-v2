# Fundação de schema V2

Esta pasta é a fonte estrutural do banco V2 de sombra. Ela não cria banco, login, usuário,
credencial, job, deploy, objeto em `ETL_SISTEMA` nem dependência de dashboards.

O caminho Flyway ativo implementa a fundação de V2-019, os subgates seguros do control plane
V2-020, o kernel técnico V2-021, o mecanismo local de lifecycle V2-045a e o framework comum
V2-023 de observabilidade e Data Quality, a vertical de Usuários V2-033 somente em sombra, a
fundação offline de referências governadas V2-035a e a fatia dimensional de Usuários V2-035b:

- `ctl`, `stg`, `core`, `ref`, `mart`, `pub` e `recon`;
- roles `v2_migrator`, `v2_runtime` e quatro roles segregadas de lifecycle, com grants e denies
  mínimos auditáveis;
- oito manifests/fingerprints: fundação, control plane, kernel, lifecycle,
  observabilidade/Data Quality, Usuários current/history, referências governadas e dimensão
  current de Usuários;
- baseline SQLCMD e validators somente leitura derivados dos contratos estruturais;
- catálogo de fontes, ciclos, partições, tentativas, eventos imutáveis, leases, páginas,
  equações de contagem, observations de watermark e estruturas de publication pointer em `ctl`;
- evento imutável de publicação e protocolo positivo evidence-bound; o stub genérico de `ctl`
  permanece fail-closed, enquanto o entrypoint final de `core` exige a evidência reconciliada;
- staging por execução, quarantine append-only e resultado agregado de candidate set, sem payload;
- dedupe set-based de toda a execução com `ROW_NUMBER` e candidate set imutável; a preparação não
  muta `core`, e o comando final classifica/aplica insert, update, reativação, no-op e stale no-op;
- lotes JDBC de até 10.000 registros, com ordinal 1..10.000 único no lote para que retry idempotente
  não transforme duplicata física em `no-op` silencioso;
- identidade textual técnica sob `Latin1_General_100_BIN2`, sem padding; procedures recebem entradas
  largas, validam `DATALENGTH` antes de normalizar, removem somente espaço U+0020 quando previsto e
  recusam overflow sem colisão por truncamento; reason codes exigem maiúsculas sem case-fold;
- equações `physical_rows = distinct_root_keys + duplicate_rows + unidentified_quarantine_rows` e
  `distinct_root_keys = candidate_rows + quarantined_root_keys`; `quarantined_stage_rows` conta
  artefatos append-only e pode superar o número de raízes quarentenadas;
- retries integralmente coincidentes de página, contagem, transição, staging, candidate set,
  recovery e publicação retornam resultado `O(1)` pelo estado persistido; divergência ou escrita
  nova continua fenced;
- `STAGING_KERNEL` é fase interna reservada. Só
  `core.usp_apply_reconcile_publish_execution` altera o estado técnico de `core`, publication
  pointer e, exclusivamente para modo incremental, a fronteira contígua; o último write revalida a
  lease ativa pelo relógio do banco.
- preparação e publicação recebem versão/SHA do contrato e da configuração, comparam em
  `Latin1_General_100_BIN2` os quatro valores já persistidos na ocorrência e falham antes de retry
  ou mutação quando o permit diverge;
- policies DQ versionadas exigem owner de threshold, owner do SLA de quarantine e owner de retenção,
  sem seed produtivo. Quatro checks comuns calculam equações, terminalidade, reconciliação e SLA no
  SQL Server; thresholds combinam limite absoluto e basis points sem divisão truncada;
- a avaliação DQ é idempotente e vinculada à execução, policy e candidate set. Publicação exige
  `PASSED` completo no boundary Java e no trigger transacional do banco; Java recebe uma linha
  agregada `O(1)` e o diagnóstico aceita somente `TOP(N)` sanitizado entre 1 e 32;
- métricas têm cardinalidade fixa; alertas e health persistem/devolvem apenas códigos e contagens.
  Falha SQL, shape ausente/duplicado ou avaliação parcial falha fechada;
- V007 liga o envelope técnico à fatia tipada `stg.usuario_record`, resolve a identidade com escopo
  de ambiente e mantém `core.usuario` current + `core.usuario_history` append-only. Presença de
  `name` é `ABSENT/NULL/VALUE`; fingerprints, conflito, no-op/stale, reativação, aplicação e
  reconciliação são set-based e serializados por execution/source scope;
- o wrapper atômico de Usuários exige terminalidade `GRAPHQL_PAGE_INFO`, gates comum e tipado de DQ,
  e grava current/history/auditoria no mesmo commit. Ele nunca desativa por ausência, não cria view
  `pub` e não interpreta tempos técnicos como `updatedAt`/frescor da origem;
- V008 cria releases, recibo de importação e ratificação/revogação append-only, além de tabelas
  tipadas para calendário/status, filiais/aliases/documentos tokenizados, matriz/exceções de frota,
  atribuição vinculada à release de filiais, exclusão de cubagem, região CEP ou cidade/UF e tarifa
  direcional. Versões de normalização/tokenização integram os grãos e lookups `EXACT_BIN2`, sem
  fallback entre versões. Nenhuma linha, release ratificada, ponteiro corrente ou grant é
  semeado; calendário/status são apenas candidatos determinísticos inertes e toda baseline mutável
  continua externa;
- V009 cria somente `core.v_usuario_dimension_current_v1`, uma view schemabound do current ativo.
  O grão é uma linha por `usuario_id`, a identidade alternativa permanece escopada e type-tagged,
  o nome não sofre trim e os timestamps são técnicos UTC. Não há join com histórico, índice,
  principal, grant ou objeto `pub`; o contrato consumidor continua em V2-037;
- lifecycle nasce sem policy ou TTL semeado: plan/dry-run usa relógio SQL, estado terminal, lease,
  ledger íntegro de legal hold, cursor e limites de admissão; dez fontes recebem archive tipado,
  atestado SHA-256 por linha, raiz de conteúdo e manifesto v3 antes de purge exclusivo de
  `stg.execution_candidate`/`stg.execution_record`; restore materializa somente uma cópia read-only
  em `recon`, sem reader público de conteúdo;
- policy, hold, plan, archive, purge e restore compartilham lock transacional; retries exatos são
  idempotentes e `v2_runtime` não recebe nenhum desses entrypoints;
- evidências de DQ, métricas e alertas são duráveis e não entram no hard delete de V005. A retenção
  produtiva delas continua sob V2-045b. A extensão de Usuários arquiva staging sem duplicar o nome,
  restaura somente evidência minimizada em `recon` e não apaga current/history/quarantine;

Fora de V007 não há ainda outra tabela de domínio vertical; V008 acrescenta somente a fundação de
referências, sem baseline ativada, e V009 somente a view interna sobre Usuários. Ainda não há fatos
ou views públicas. O kernel V2-021 armazena
apenas chave de origem opaca, hashes, fingerprint de presença,
frescor, disposição e motivo de quarantine; cada outra vertical continua responsável por sua
identidade física em V2-009d e por sua própria migration. V007 fecha somente a identidade e o grão
de Usuários definidos em V2-009a, sem publicar consumidor. Nenhuma migration persiste payload, URL,
token ou credencial. O schema `shadow` não faz parte do modelo V2 e é rejeitado pelo validator.

## Organização

- `migrations/`: história Flyway limpa e ativa. Nunca inclua migrations 001–059 do legado.
- `manifest/`: contratos estruturais e SHA-256 que os tornam rastreáveis.
- `baseline/`: reprodução SQLCMD da mesma estrutura, para comparar banco vazio e baseline aprovado.
  O histórico interno `ctl.flyway_schema_history` é administrado pelo Flyway e fica fora dessa
  comparação estrutural.
- `validation/`: validações somente leitura e exercícios transacionais revertidos do baseline.
- `transition/`: transição única, estritamente guardada, da prova local V001–V003 para a fundação.
- `evidence/historical-shadow-v001-v003/`: prova histórica preservada; ela não é migration ativa.

## Aplicação e validação locais autorizadas

A autorização local é restrita a `localhost` e ao banco
`ETL_SISTEMA_V2_SHADOW`, sempre com Windows Authentication. Antes de qualquer DDL, consulte
`master` e confirme o nome exato do alvo. Nunca conecte este fluxo a `ETL_SISTEMA`, `esl_cloud`,
`DASHBOARDS` ou `DASHBOARDS_DEV`.

O alvo local ainda contém a prova histórica sem `flyway_schema_history`; portanto, não execute
`flyway:migrate` nele diretamente. A transição só aceita o fingerprint histórico conhecido, exige
as três tabelas de auditoria vazias e falha caso encontre objetos V2 desconhecidos, dados ou outro
alvo. Ela remove somente procedures/tabelas/role sintéticos da prova e o schema vazio `shadow`.
Não usa `flyway:clean`, não cria banco e pode ser chamada dentro de uma transação externa.

O exercício abaixo verifica, em rollback, a transição, o baseline e os mecanismos isolados de
grants, lease, replay, transições, publicação, fronteira incremental e stale recovery, sem deixar
DDL persistido. Os exercícios 008/010 acrescentam candidate set e o protocolo positivo atômico de
V2-020b/V2-021b. Execute do diretório
`database/validation`, para que os includes SQLCMD tenham caminhos determinísticos:

```powershell
sqlcmd -S localhost -C -E -f 65001 -d ETL_SISTEMA_V2_SHADOW -i 002_exercise_schema_foundation_baseline_rollback.sql -b
```

O gate progressivo V2-015b executa primeiro os oito validadores de manifesto/fingerprint e então
compara, sempre em transações revertidas, o baseline SQLCMD com a aplicação individual de V001 a
V009. Probes com duas sessões comprovam a exclusão por namespace e pelo lifecycle, além da liberação
dos application locks no rollback. Depois exercita staging/quarantine/dedupe e aplicação técnica com insert, update,
reativação, no-op e stale no-op, retry pós-commit, pointer e fronteira contígua. Ele rejeita constraint,
objeto, permission ou membership de role fora do contrato mínimo, incluindo `SELECT`/DML diretos
do runtime em `ctl`, `stg`, `core` e `recon`. O exercício 011 confirma, sempre em rollback, que um
permit divergente não prepara candidato, publica, duplica evento nem avança pointer/lease. Os
exercícios 013–020 validam cutoff/terminalidade, archive/purge/restore, ratificação, ledger de hold
e cadeia terminal completa adulterados, grammar ASCII/BIN2, aplicação integral de V005 sob
impersonação da role migrator, confinamento de owner-context e SHOWPLAN compilado sem efeitos. O
validator 021 e os exercícios 022–024 cobrem policy/checks DQ, retry exato, aplicação de V006 pela
role migrator, thresholds absoluto/percentual, crossing temporal do SLA, health agregado e recusa
atômica de publicação sem `PASSED` completo. O SHOWPLAN 025 compila os cinco entrypoints e exige os
índices governados, seeks, zero warning operacional e nenhuma referência a outro database, sem
alegar escala. O validator 026 e o exercício 027 cobrem V007, incluindo unicidade por ambiente,
presença tri-state, conflito, mudança/no-op/stale/replay/reativação, DQ tipada, grants, triggers de
imutabilidade e lifecycle minimizado de Usuários. O probe concorrente usa em duas sessões a fórmula
extraída do entrypoint comum V004 e compõe essa evidência com o exercício rollback dos entrypoints;
não a apresenta como execução simultânea end-to-end. O exercício/gate 028 atribui cinco paths aos
statements correspondentes, exige os quatro índices previstos, zero conversão/warning operacional e
rollback integral. O budget tipado de V007 é agregado set-based e entra no planner V005 antes de
oversized, `TOP` e cumulativos; 027 prova que um item antigo oversized não causa starvation do menor
seguinte e confere o budget contra a representação efetivamente arquivada. O exercício 029 recusa o
sidecar tipado tardio de uma execução generic-only terminal sob o mesmo row fence. O validator 030
e os exercícios 031–033 cobrem V008, seeds candidatos, calendário pós-2032/contínuo, normalização,
seal, replay/conflito, vigência/overlap, matriz de frota, dependência de filial,
ratificação/revogação por escopo, baseline vazia, ausência de default, tokens sintéticos, grants negativos e aplicação pela
role migrator; rejeições que podem invalidar a transação usam escopos rollback-only isolados. O
probe concorrente comprova contenção e isolamento da ratificação por scope, exclusão entre conteúdo
e seal e ambos os interleavings atribuição×revogação em banco efêmero removido ao final; 034 compila
quinze access paths com seeks, sem warning operacional nem conversão de cardinalidade. O validator
035 e o exercício 036 comprovam shape/grão, filtro ativo, identidade type-tagged, presença
tri-state, ausência de trim, histórico sem multiplicação, menor privilégio e rollback da dimensão
current. O SHOWPLAN 037 exige os seeks por PK/identidade escopada e zero warning/conversão, sem
proibir o scan legítimo da projeção completa. Os validators
comparam os shapes e allowlists do
manifesto atual. Não deixa banco, login, credencial, dado de domínio ou artefato persistente. O
probe concorrente cria e descarta em `finally` o banco local allowlisted
`ETL_V2_REF_PROBE_<guid>`; usuários sintéticos dos demais exercícios permanecem restritos às
transações revertidas:

```powershell
.\scripts\validation\Invoke-ProgressiveDataGate.ps1
```

Os checkers PowerShell validam a estrutura dos scripts e manifests; não substituem a execução deste
gate contra SQL Server. Depois de qualquer alteração em migration ou manifesto ativo, a evidência
dinâmica só volta a valer após nova rodada rollback-only em instância descartável/local autorizada.

Depois de uma transição realmente confirmada pelo owner, o perfil
`shadow-migrations-windows-auth` aplica somente as migrations ativas com `ctl` como schema do
histórico Flyway. Ele requer uma URL JDBC integrada, local e não secreta no ambiente do processo;
não aceita usuário ou senha de migrator. A associação de identities aos papéis continua pertencendo
ao DBA/owner e a V2-042. `cleanDisabled=true`, `baselineOnMigrate=false`, validação de nomes e
validação antes de migrar permanecem obrigatórias.

As validações são somente leitura, exceto o exercício que usa uma transação revertida. Não há dados
de domínio, payload, token, URL de tenant, documento ou ID de negócio em nenhum script.

## Papéis e menor privilégio

`v2_migrator` recebe somente DDL explicitamente necessário e `ALTER`/`REFERENCES` nos sete schemas
V2; DML fica limitado a `ctl` para o histórico Flyway. Como SQL Server não repassa `GRANT OPTION`
herdado via role, `dbo.usp_publish_v2_procedure_grant` roda como owner e aceita somente procedures,
schemas e roles V2 de uma allowlist estrita. A role executa apenas esse helper para publicar grants
de objeto; não recebe `CONTROL`, `db_owner`, `EXECUTE` amplo nem permissão cross-database. Os sete
schemas mutáveis pertencem ao usuário interno sem login `v2_schema_owner`, não a `dbo`: um módulo
transitório `EXECUTE AS OWNER` criado durante uma migration fica confinado aos schemas V2. O
migrator não pode impersonar esse owner ou `dbo`, nem alterar/criar módulo em `dbo`. A prova 015
aplica V005 inteira sob um usuário sintético membro da role, confirma o contexto efetivo restrito,
a ausência de privilégios de escalada e a rejeição de um grant fora da allowlist, e reverte tudo. O bootstrap inicial
de um banco novo exige que o DBA forneça uma identidade com capacidade temporária de criar as
roles/schemas; isso não é concedido ao runtime.

`v2_runtime` recebe `EXECUTE` somente nas procedures parametrizadas necessárias ao ciclo, ao
candidate set, ao recovery stale, ao entrypoint atômico final e aos cinco entrypoints fechados de
avaliação/observação DQ, métrica, alerta e health. Não recebe o cadastro administrativo
da fronteira nem o stub genérico de publicação; V007 acrescenta somente os dois entrypoints fechados
de staging e apply/reconcile/publish de Usuários. `SELECT`/`INSERT`/
`UPDATE`/`DELETE` diretos são negados em `ctl`, `stg`, `core`
e `recon`, inclusive nas tabelas V006, e não existem grants positivos diretos em `ref`; a role não
recebe DDL. Migrations futuras concedem somente os direitos do objeto já
aprovado. Logins, usuários, service principals, membership e autorização de comandos pertencem a
V2-042 e não são criados aqui.

As roles `v2_retention_governor`, `v2_lifecycle_reviewer`, `v2_lifecycle_operator` e
`v2_archive_restorer` recebem `EXECUTE` apenas nos entrypoints da respectiva função. Todas têm
acesso direto e DDL negados, não possuem membership semeada e não ampliam `v2_runtime`. V2-045b
continua em `EXTERNAL_HOLD`: nenhuma dessas roles, por si só, autoriza política, identidade ou
operação produtiva. Consulte o
[ADR 0013](../docs/adr/0013-retencao-arquivamento-e-purge-governado.md) e o
[runbook](../docs/runbooks/retencao-arquivamento-e-restore.md). Para a extensão de Usuários,
consulte também o [ADR 0019](../docs/adr/0019-usuarios-current-history-em-sombra.md) e o
[runbook específico](../docs/runbooks/usuarios-current-history-em-sombra.md). Para a dimensão
current, consulte o
[ADR 0021](../docs/adr/0021-dimensao-current-de-usuarios-em-sombra.md).

Para V2-035a, consulte o
[ADR 0020](../docs/adr/0020-referencias-governadas-sem-default-produtivo.md) e o
[runbook de importação/ratificação](../docs/runbooks/importar-ratificar-e-revogar-referencias.md).
