# Escopo de cobertura P10/G02

`packages.txt` fixa os pacotes de fontes. `mixed-class-scope.txt` fixa cada fonte
de `bootstrap` e `qualificacao` por path, SHA-256 dos bytes com LF e rota
`UNIT_INCLUDED` ou `SHADOW_EXCLUDED`. `physical-sources.txt` fixa as fontes com
execução JDBC física, inclusive os três pacotes JDBC e a classe de Coletas.
O teste `CiCoverageScopePolicyTest` reprova fontes novas/alteradas sem revisão,
drift dos manifests, limiares enfraquecidos e exclusões JaCoCo não declaradas.
Esses manifests descrevem escopo de gate; não são prova de cobertura executada.

## Classes físicas exatas nos pacotes mistos

| Classe | Caminho executado que exige sombra SQL | Caminho puro preservado |
| --- | --- | --- |
| `QualificationLineageEvidence` | construtor chama `expansion`, `manifests`, `quotations` e `collections`, cada um obtém conexão da sessão | `QualificationWireOracle` e índice de artefatos seguem no Ubuntu |
| `QualificationTemporalMatrix` | `execute` isola workloads e abre `JdbcSqlServerTemporalPlan`, capturas e fronteiras SQL | `QualificationTemporalPolicyCatalog` foi extraído e seu carregador real é testado no Ubuntu |
| `QualificationMonitoring` | ambos os construtores chamam `observeTimes`; `partitions`, `hydration` e `rejectedCollections` obtêm conexão da sessão; `row` depende do estado observado | record `Expectation` permanece classe distinta, sem exclusão |
| `QualificationWorker` | `run` abre `ColetaTemporalLaboratorySession` antes de executar o caso | campanha, pins e arquivos de controle são testados no Ubuntu |
| `ExpansionLaboratoryExecutor` | `execute` usa `JdbcExpansionRecomposition.partition/stepsBatch` e `JdbcExpansionLaboratory.policy` | fixtures e política de expansão seguem no Ubuntu |
| `ExpansionLaboratoryHydrator` | `hydrate` usa `JdbcExpansionRelations.claimBatch/finish/resolve`; captura depende de runtime SQL | record `Result` permanece classe distinta, sem exclusão |
| `RelationalLaboratoryExecutor` | `plan`, `executePartition` e `hydrate` usam `JdbcRelationalLaboratory` e `JdbcRelationalRecomposition` | fixtures relacionais seguem no Ubuntu |
| `JdbcColetaTemporalLaboratory` | corpo executa operações JDBC da sessão shadow | record de resultado segue no pacote unitário |
| `AnalyticScenarioRuntime` | `start` e `capture` usam `JdbcAnalyticScenario`, controle SQL e materializações | `Run` e `Cycle` são classes compiladas distintas, ainda no Ubuntu |
| `AnalyticExpansionCapture` | `capture` consulta política e plano em `JdbcExpansionLaboratory` e `JdbcExpansionRecomposition` | `Result` permanece classe distinta |
| `QualificationWindowExecutor` | `execute`, `frontier` e `verifyReceipt` consultam a sessão e materializações JDBC | records `Applied` e `Result` permanecem distintos |
| `QualificationConcurrency` | `execute` abre duas sessões shadow e disputa `JdbcExpansionRelations` | `Result` permanece classe distinta |
| `SequenceRecomposition` | `execute` aplica `JdbcRelationalLaboratory` e materializações JDBC | `Receipt` permanece classe distinta |
| `LocalAnalyticCollectionSweep` | `observe` usa `JdbcAnalyticCollectionSweep` e conexão da sessão | `Observation` permanece classe distinta |
| `LocalAnalyticManifestRuntime` | `capture` abre transação e chama `JdbcAnalyticManifestPreparation` | `Capture` permanece classe distinta |
| `LocalAnalyticCollectionRuntime` | `capture` abre transação e chama `JdbcAnalyticCollectionPreparation` | `Capture` permanece classe distinta |
| `LocalExpansionRuntime` | `capture` obtém conexão e usa `JdbcExpansionStaging`/`JdbcExpansionLaboratory` | `Capture` permanece classe distinta |
| `LocalExpansionDependencyRuntime` | `capture` obtém conexão e usa `JdbcExpansionDependencies` | `Capture` permanece classe distinta |
| `LocalAnalyticQuotesRuntime` | `capture` obtém conexão e usa staging/promoção JDBC | receipts e DTOs seguem em suas classes |
| `LocalRasterRuntime` | `capture` obtém conexão e cria `JdbcRasterBatch` | `Progress` e `Observer` permanecem classes distintas |
| `LocalColetasTemporalRuntime` | `execute` delega a `JdbcColetaTemporalLaboratory` | `Input` permanece classe distinta |
| `LocalArtifactSequence$SqlExecution` | `execute` orquestra sessão e consulta `JdbcAnalyticScenario` | a classe externa valida manifesto, relatório, fronteira e pins no Ubuntu |
| `DeclaredAnalyticSupport$SqlApply` | `apply`, `applyBatch`, `sources` e `applyFiscal` consultam/adicionam bindings JDBC e verificam contagens SQL | a classe externa valida pins, documentos, termos financeiros e relações sem SQL |
| `DeclaredAnalyticSupport$SqlApply$SourceIdentity` | identidade técnica produzida exclusivamente na resolução SQL de `sources` | `DeclaredAnalyticSupport` preserva os invariantes de fonte no teste sem SQL |
| `QualificationScenarioVerifier$SqlVerification` e `$Evidence` | `verify`, `verifyIntegral`, `sourceGates` e `factGates` consultam recibos e linhas JDBC; `Evidence` lê lineage/monitoring dessa sessão | a classe externa mantém binding, oráculo esperado e contrato imutável de resultado no Ubuntu |
| `QualificationCaseExecutor$SqlExecution` e `$AbsenceEvidence` | `execute`, `sequence`, `artifact` e `absence` capturam via sessão SQL; `AbsenceEvidence` depende da lineage física | a classe externa mantém serialização de resultados e coordenadas de diferença no Ubuntu |
| `QualificationPhysicalMetadata$SqlVerification` | `verify` obtém conexão da sessão e consulta `sys.views`/`sys.columns` do banco shadow para comparar 971 colunas físicas | o construtor externo valida estrutura, hash e contagem do documento no Ubuntu |
| `AnalyticScenarioEnrichment` | `apply` pagina `JdbcAnalyticFixtureBindings` e grava atributos/relações/composições pelos adaptadores JDBC; `freight` e `collections` dependem da sessão | `AnalyticFreightScenarioData` lê recurso sintético limitado, aplica revisão/correção e alimenta o mapper tipado sem SQL no Ubuntu |
| `QualificationSupervisor$SqlChildExecution` | `run` confirma `master` e snapshot por `QualificationSqlEvidence`, lança o worker JDBC selado, observa seu processo e só então chama reconciliação | Supervisor externo mantém admissão, journal, status, recibo, validação de processo/barreira/log e retomada offline no Ubuntu |
| `QualificationSqlEvidence` | `master` abre conexão read-only via `DriverManager`; `snapshot` abre `ColetaTemporalLaboratorySession`, consulta `sys.tables` e `spid` consulta a sessão | `QualificationSqlOptIn` exige as duas travas booleanas antes da conexão, testadas em todas as combinações no Ubuntu; `Snapshot` permanece classe record distinta |
| `LocalAnalyticUsersRuntime$SqlCapture` | `captureWithinTransaction` obtém conexão/savepoint; `captureWithin` registra qualidade, monta `RuntimeDispatcher` com controle, staging e promoção JDBC e anexa dimensão à execução. Consumidor: `AnalyticScenarioRuntime.captureUsers` | A classe externa mantém `observationMode`, valida modo/replay antes de abrir SQL, emite `captureClosed` em `finally` e constrói configuração sintética. Rejeições e modos permitidos são testados sem SQL no Ubuntu. |
| `LocalRelationalRuntime$SqlCapture` | `capture` consulta `JdbcRelationalLaboratory.policy/scope`, constrói plano de extração e abre conexão/savepoint para controle, auditoria, staging e receipt JDBC. Consumidores: `RelationalLaboratoryMain` e `AnalyticScenarioRuntime`. | Classe externa valida execução, dia único, limites de data, modo/replay e cancelamento antes da primeira consulta SQL; `entity` continua decisão pura e `Capture` record distinto. Teste offline confere admissões e erros de contrato. |
| `LocalArtifactScenario$SqlExecution` | `capture` verifica metadata física, inicia/captura `AnalyticScenarioRuntime`; `verifyIntegral`/`verifyBaseline` consultam comparação física; `observeSweep` e `previewSequence` usam sessão/savepoint; recomposição delega execução SQL. Consumidores: `LocalArtifactScenarioMain`, `LocalArtifactSequence` e caso de qualificação. | Externa mantém pins e `verifyFiles` antes do SQL, admissão de sequência integral, seleção de raster, callback de preparação, escolha de resultado PASS/FAIL e montagem do resultado com preview. Teste offline rejeita raster divergente e sequência em cenário baseline. `Captured` record é classe separada sob gate Ubuntu. |

Cada exclusão do gate Ubuntu nomeia somente a classe compilada exata; classes
aninhadas não listadas ficam sujeitas à regra do pacote. O gate físico opt-in exige 80% de
linhas e 60% de ramos para estas classes e para os três pacotes JDBC; sua
execução requer as travas locais documentadas em `AGENTS.md`. O perfil físico
não foi executado nesta rodada. `bootstrap` ainda reprova 80/60 no Ubuntu; as
demais classes mistas permanecem `UNIT_INCLUDED` até prova causal individual.
