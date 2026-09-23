# Fundação de schema V2

## Estado atual — 08/09/2026

Bloco 54 concluído no laboratório A–G: V021 aplicada, 25 grants, revisão v7 física,
1098 testes aprovados, 302 reservas e 30 campanhas encerradas. O [relatório atual](../docs/runbooks/v2-022-bloco54-conclusao-local.md)
registra os resultados, os limites nominais, o diagnóstico V021 e a recuperação.
As afirmações de pendência/preparação/revisão final abaixo pertencem às fotografias
históricas indicadas; não são o procedimento atual. Preservadas para rastreabilidade.

## Histórico preservado

## Estado local após Bloco 53 — 07/09/2026

V001–V017 instaladas no banco existente localhost/ETL_SISTEMA_V2_SHADOW. V001–V015
foram comparadas por baseline/migrations em rollback antes da transição histórica vazia.
V016/V017 entraram somente como evolução aditiva, com equivalência de catálogo e contagens
preservadas. Não foi criado histórico Flyway artificial; migrations aplicadas são imutáveis.
O schema inclui auditoria/consumo Windows e plano temporal, sem identity mapping, authority
configurada ou grants operacionais. Dados sintéticos confirmados permanecem no banco.

Não executar o reset histórico nem a baseline inteira sobre esse estado. O runner
`Invoke-RuntimePhysicalSchema.ps1` continua exclusivo da topologia histórica vazia e deve
recusar o banco modernizado. `Invoke-RuntimePhysicalEvolution.ps1` qualifica a transição
exata V015→V017; depois de instalada, também recusa reexecução destrutiva ou drift. Os
validators 048, 050, 051 e 052 operam sobre o schema moderno; 050 reverte seus próprios
fixtures, 051 exige a fase sem provisionamento e 052 usa apenas leitura de resumos sintéticos.

Ver [evidência do Bloco 53](../docs/runbooks/v2-022-bloco53-integrado.md),
[ADR 0035](../docs/adr/0035-authority-windows-sql-consumo-duravel-e-plano-temporal.md) e
[pacote revisável de provisionamento](../docs/runbooks/v2-042-provisionamento-windows-sql.md).
As seções históricas abaixo preservam o contexto em que cada migration foi preparada.

Esta pasta é a fonte estrutural do banco V2 de sombra. Ela não cria banco, login, usuário,
credencial, job, deploy, objeto em `ETL_SISTEMA` nem dependência de dashboards.

O caminho Flyway ativo implementa a fundação de V2-019, os subgates seguros do control plane
V2-020, o kernel técnico V2-021, o mecanismo local de lifecycle V2-045a e o framework comum
V2-023 de observabilidade e Data Quality, a vertical de Usuários V2-033 somente em sombra, a
fundação offline de referências governadas V2-035a, a fatia dimensional de Usuários V2-035b e as
verticais base de Coletas 6908, Cotações 6906, Manifestos 6399, Fretes 6389 e Localização de
Cargas 8656 em sombra:

- `ctl`, `stg`, `core`, `ref`, `mart`, `pub` e `recon`;
- roles `v2_migrator`, `v2_runtime` e quatro roles segregadas de lifecycle, com grants e denies
  mínimos auditáveis;
- treze manifests/fingerprints: fundação, control plane, kernel, lifecycle,
  observabilidade/Data Quality, Usuários current/history, referências governadas, dimensão
  current de Usuários, Coletas, Cotações, Manifestos, Fretes e Localização de Cargas;
- baseline SQLCMD e validators somente leitura derivados dos contratos estruturais;
- catálogo de fontes, ciclos, partições, tentativas, eventos imutáveis, leases, páginas,
  equações de contagem, observations de watermark e estruturas de publication pointer em `ctl`;
- evento imutável de publicação e protocolo positivo evidence-bound; o stub genérico de `ctl`
  permanece fail-closed, enquanto o entrypoint final de `core` exige a evidência reconciliada;
- staging técnico genérico por execução, quarantine append-only e resultado agregado de candidate
  set sem payload; sidecars tipados preservam apenas os envelopes governados por cada vertical;
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
- V010 materializa somente Coletas 6908 em sombra: presença `ABSENT/NULL/VALUE`, catálogo
  `coletas-status-v1`, frescor com fallback documentado, staging tipado, identidade escopada,
  alias `sequence_code` versionado, promoção set-based e evidência de presença. Não cria objeto
  `pub`, sweep, desativação por ausência ou relação Manifesto→Coleta/Coleta→Frete; candidatos
  relacionais ficam com valor, presença e proveniência até V2-046a/V2-046b;
- V011 materializa somente Cotações 6906 em sombra: `sequence_code` inteiro type-tagged sob
  `source_instance` e `tenant_scope` explícitos, precedência única de frescor
  NFS-e→CT-e→solicitação, quarentena para empate divergente e tarifa direcional exclusivamente de
  `ref.tarifa_rota_uf` por release explícita ratificada para `SHADOW`. Ausência, rota sem cobertura,
  overlap, UF ausente ou referência inválida falham fechados; não há matriz legada, objeto `pub`,
  sweep, desativação, dispatcher ou integração externa;
- V012 materializa somente Manifestos 6399 em sombra: a raiz P01 é escopada e type-tagged por
  `sequence_code`; Pick e MDF-e pertencem exclusivamente à raiz, enquanto `mdfe_status` permanece
  escalar da raiz. As observações físicas são append-only e a redução MAN-01/MAN-02/MAN-04/MAN-07
  exige frescor canônico; empate de mesmo frescor divergente bloqueia promoção. Os candidatos de
  relação a Coletas preservam presença e proveniência em `recon`, sem FK, lookup ou resolução até
  V2-046a. Limites textuais são UTF-16, sem truncamento; não há `pub`, sweep, desativação por
  ausência, dispatcher ou integração externa;
- V013 materializa somente a base de Fretes 6389 em sombra. `/id` INTEGER é a única chave de
  origem e `corporation_sequence_number` permanece alias; a mesma precedência de frescor
  CT-e criada→CT-e emitida→criado→serviço governa dedupe e promoção. Performance oficial e
  fallback declarado ficam em staging independente, assim como o sidecar GraphQL limitado a
  dez paths e 100 edges. Candidatos Coleta–Frete são append-only não resolvidos até V2-046b;
  V2-046a/b continuam abertas e não há FK, join, crosswalk, `pub`, sweep ou cutover;
- V014 materializa somente Localização de Cargas 8656 em sombra, com raiz plural
  `core.localizacao_cargas`, identidade INTEGER type-tagged em `/corporation_sequence_number`,
  frescor exclusivo de `/service_at` e envelope fechado de 17 paths com sete metadados por field.
  O sidecar persiste quarentena e léxico longo sem promover; o reducer efetivo conserva `ABSENT`,
  aplica `NULL`/`VALUE` e liga scalars ao raw do mesmo envelope. Datas civis usam
  `America/Sao_Paulo` e falham em gap/overlap. Relação/fallback com Fretes, `pub`, sweep,
  desativação e cutover continuam deferidos;
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

As verticais V010–V014 permanecem exclusivamente em sombra; não há fatos ou views públicas. O
kernel V2-021 armazena
apenas chave de origem opaca, hashes, fingerprint de presença,
frescor, disposição e motivo de quarantine; cada outra vertical continua responsável por sua
identidade física em V2-009d e por sua própria migration. V007 fecha somente a identidade e o grão
de Usuários definidos em V2-009a, sem publicar consumidor. Payload só existe nos sidecars tipados
que o contrato da vertical exige; nenhuma migration persiste URL, token ou credencial. O schema
`shadow` não faz parte do modelo V2 e é rejeitado pelo validator.

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

O exercício específico de V012 inclui o baseline, valida o contrato estrutural, executa observações
físicas, redução, replay, ownership e overflow Unicode inteiramente em rollback. Use somente após
o mesmo preflight em `master`:

```powershell
sqlcmd -S localhost -C -E -f 65001 -d ETL_SISTEMA_V2_SHADOW -i 043_exercise_manifestos_shadow_vertical_rollback.sql -b
```

O runner de V013 consulta `master`, exige opt-in, executa os validators 044/045 e a contenção em
duas sessões, compara o estado anterior/final e reverte todos os dados sintéticos:

```powershell
.\scripts\validation\Invoke-FretesV2011ShadowValidation.ps1 -ExecuteLocalShadow
```

O runner de V014 aplica o mesmo preflight literal, executa os validators 046/047 e comprova
contenção, isolamento por environment/tenant e liberação do lock, sempre em rollback:

```powershell
.\scripts\validation\Invoke-LocalizacaoCargasV2028ShadowValidation.ps1 -ExecuteLocalShadow
```

O gate progressivo V2-015b executa primeiro os validadores de manifesto/fingerprint e então
compara, sempre em transações revertidas, o baseline SQLCMD com a aplicação individual de V001 a
V014. Probes com duas sessões comprovam a exclusão por namespace e pelo lifecycle, além da liberação
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
de domínio reais, token, URL de tenant, documento ou ID de negócio em nenhum script; os payloads de
exercício são sintéticos e rollback-only.

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

## P02R — evolução preparada em V015

V015/048/049 acrescentam selo pós-guard, leitura/continuação durável e recibo tipado
atômico de Coletas. Baseline e gate progressivo incluem V015; V001–V014 não foram
editadas. O entrypoint de Coletas é evoluído aditivamente, conservando COL-03 e os
grants anteriores; nenhuma permissão nova é concedida. Manifesto específico:
`manifest/runtime-durable-recovery.json`. O [ADR 0034](../docs/adr/0034-recuperacao-duravel-local-sem-reemissao-de-permits.md)
e o [runbook P02R](../docs/runbooks/v2-022-recuperacao-duravel-local.md) delimitam os aceites.

**No Bloco 52 esses arquivos foram somente preparados e validados estaticamente.**
Não houve SQLCMD, aplicação de migration, perfil JDBC físico ou validação contra SQL
Server. A autorização desta rodada proíbe SQL físico mesmo onde seções históricas
acima descrevem autorizações anteriores. Qualificação física futura exige autorização
específica; prova entre processos Java com persistência sintética não a substitui.

## Checkpoint B54 — V020 e perfil de observabilidade instalados

V001–V020 estão aplicadas no alvo único localhost/ETL_SISTEMA_V2_SHADOW e são
imutáveis. V019/V020 passaram por baseline/upgrade em rollback e pelos cinco hashes
de módulo antes do commit na quinta campanha autorizada. Os três grants exatos do
[pacote de observabilidade](proposals/bloco54-observability/README.md) foram aplicados;
nova conexão verificou 25 EXECUTEs. Não repetir a baseline nem os instaladores.

Catálogo atual: 6a4d90971e7a111d695ace72cac98983fef8a1ef6b080153b0897a8dc27af45a,
5.328 linhas. SERVICE mapping v15 após compensação UTC de OPS02;
OPERATOR v1, scope original 1 v10/demais sete v1, direitos e vencimento originais preservados.
Não houve REPLAY/FORCE_RUN nem scope novo. Diagnóstico atual: verify.sql e 055;
053 continua exigindo a fotografia histórica de 22 grants.

B53 conserva 95 tentativas, 75 publicações, oito EXTRACTING e 112 reservas.
B54 tem oito tentativas, quatro publicações, seis janelas de plano únicas sem
extração temporal e 109/128 reservas em seis campanhas encerradas. Matriz parcial:
31 casos anteriores e oito novos em test-classpath passaram; falha no gravador de saída SQL vazia após commit de revogação
foi corrigida offline, e a compensação foi conferida por leitura exata e nova conexão.
O [manifest B54](manifest/runtime-bloco54.json) fixa o checkpoint e os hashes das
provas privadas. Os manifests e textos B53 acima conservam suas fases históricas.
Não fabricar histórico Flyway, limpar auditoria, renovar ou conectar a outra base.

A sexta campanha comprovou seis mutações entre decisão/consumo e dois processos
para consumo/reinício. OPS02 foi recuperado sob a reserva 109, após corrigir a
conversão de validade UTC, sem renovar. O [pacote residual](proposals/bloco54-residual-runtime/README.md)
prepara 19 unidades, sem sétima campanha autorizada. Nenhuma migration foi alterada.
