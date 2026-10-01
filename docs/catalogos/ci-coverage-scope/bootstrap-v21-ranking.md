# Bootstrap — ranking causal do XML JaCoCo v21

Fonte: `target/site/jacoco/jacoco.xml` gerado pelo `verify` v21, classes ainda
incluídas no check Ubuntu após as exclusões exatas do `pom.xml`. `LC/LM` são
linhas cobertas/descobertas; `BC/BM`, ramos cobertos/descobertos. Teste `IT`
existente não significa execução no `verify` default: o perfil shadow físico
continua não executado.

| Ordem | Classe; LC/LM; BC/BM | Rota descoberta e consumidor concreto | Teste existente e ação causal |
| --- | --- | --- | --- |
| 1 | `QualificationSupervisor`; 138/382; 44/194 | `runChild` inicia `QualificationWorker` (SQL shadow); `reconcilePending`, `status` e `receipt` tomam decisões de campanha/arquivos. Consumido por `QualificationLaboratoryMain`. | `QualificationPackageIntegrityIT` cobre admissão bloqueada e plano; `QualificationResumeAdmissionIT`/`QualificationSequenceSupervisorIT` são campanhas físicas opt-in. Preservar `status`/plano/recibo no Ubuntu; exercitar transições offline e separar somente o filho físico comprovado. |
| 2 | `LocalRelationalRuntime`; 5/146; 4/20 | `capture` usa sessão e adaptadores JDBC; `entity` mapeia entidade sem SQL. Consumido por `AnalyticScenarioRuntime`, runtime de coleção/manifesto e `RelationalLaboratoryMain`. | `RelationalLaboratoryContractTest` cobre `entity`; ITs físicos são opt-in. Manter mapeamento no Ubuntu; separar corpo SQL se a classe exata for excluída. |
| 3 | `LocalAnalyticUsersRuntime`; 4/122; 3/14 | `capture` abre conexão e promove staging; `observationMode` escolhe modo sem SQL. Consumido por `AnalyticScenarioRuntime`. | `RuntimeUsersOperationalRequestTest` cobre os modos; ITs de usuários são físicos. Manter modo no Ubuntu, classificar captura por método/classe. |
| 4 | `AnalyticLaboratoryMain`; 0/117; 0/34 | CLI analisa `AnalyticLaboratoryOptions`, depois `run` abre sessão e `execute` consulta `JdbcAnalyticScenario`; erro de configuração e `requireReplayNoop` são puros. Entrypoint one-shot. | Parser tem testes próprios; não há teste direto deste wrapper. Testar saída/código de configuração e replay puro antes de qualquer separação física. |
| 5 | `DeclaredSqlOracles`; 132/105; 60/50 | `monitorDeclarations` consulta sessão SQL; `row`/`cell`/`expected` e pins são oráculos puros. Consumido por `LocalArtifactScenario`/`QualificationScenarioVerifier`. | `IntegralArtifactReplayIT` é físico. Testes de pins existem em `QualificationOracleTest`; testar saída esperada/erro de contrato em `expected` e isolar consulta. |
| 6 | `LocalArtifactScenario`; 178/105; 49/37 | `execute`/`previewSequence` chamam runtime e conexão; construtor, `verifyFiles` e fingerprints são puros. Consumido por `LocalArtifactSequence`, CLI e caso de qualificação. | `IntegralArtifactAdmissionTest`/`QualificationArtifactPreflightTest` cobrem admissão; ITs de cenário são físicos. Preservar preflight no Ubuntu; isolar execução SQL. |
| 7 | `QualificationCaseControl`; 0/100; 0/34 | `watch`, nonce, prazo e barrier `BEFORE_RECEIPT` controlam arquivo/processo sem SQL; apenas `DURING_CAPTURE` cancela statement da sessão. Consumido por `QualificationWorker`. | `QualificationControlIT` só executa no perfil físico. Novo `QualificationCaseControlOfflineTest` v22 prova barreira exata, nonce, motivo e prazo sem SQL; teste focal elevou a classe a 64/100 linhas e 18/34 ramos. Não excluir a classe mista. |
| 8 | `RelationalLaboratoryMain`; 3/98; 0/20 | Parser `Options` puro e resultado/categoria; `run` abre sessão e `execute` usa JDBC. Entrypoint one-shot. | `RelationalLaboratoryContractTest` prova rejeição CLI; IT é físico. Preservar retorno de rejeição e parser no Ubuntu; classificar execução SQL exata. |
| 9 | `QualificationLaboratoryMain`; 30/96; 15/47 | Admissão de comando, `plan`, `status`, preflight e relatório offline coexistem com execução do supervisor. Entrypoint da campanha. | `QualificationBoundedContractsTest` prova rejeição; `QualificationPackageIntegrityIT` executa `plan` empacotado. Continua integralmente no Ubuntu; testar transições de saída observáveis. |
| 10 | `AnalyticScenarioEnrichment`; 0/95; 0/26 | `apply` faz bindings JDBC; `freightData` lê e corrige fixture sintética sem SQL. Consumido por `AnalyticScenarioRuntime`. | ITs de atributos são físicos. Em v22, `AnalyticFreightScenarioData` passou a conter a transformação pura, testada com revisão, correção temporal/km tipada e recaptura sem mutar a fixture; a classe SQL restante foi classificada exatamente no shadow. |
| 11 | `ExpansionLaboratoryMain`; 3/79; 0/27 | `Options.parse` puro e códigos de erro; `run` abre sessão, `execute` compõe JDBC. Entrypoint one-shot. | `ExpansionLaboratoryMainTest` cobre rejeição; IT é físico. Preservar parser/código no Ubuntu, classificar execução por fluxo. |
| 12 | `LocalArtifactSequenceMain`; 0/73; 0/22 | Valida comando/arquivo de sequência antes de `openFromEnvironment`; emite resultados após execução SQL. Consumido por `Main`. | Não há teste direto. Testar rejeição pré-sessão e saída; isolar sessão física somente após manter essa lógica no Ubuntu. |

**Decisão arquitetural em curso.** O escopo unitário v21 ainda precisa de
1.564 linhas cobertas adicionais se o denominador permanecer 6.652, e 280
ramos para 60%. Apenas testar getters ou branches triviais não prova os riscos.
A rota sustentável é testar operações offline de campanha, controle e
preflight com saídas/decisões observáveis; separar em classes internas exatas
os blocos de execução SQL realmente inseparáveis de shadow, mantendo seus
contratos puros no Ubuntu. Excluir uma classe externa só porque se chama
`Main` ou chama `openFromEnvironment` ocultaria rejeição/configuração pura.
Cada próxima separação exige fluxo, hash da fonte no manifesto fail-closed,
exclusão Ubuntu exata e inclusão shadow; o perfil físico requer configuração
local e não recebeu PASS.

## Traço de método no XML limpo v23

O XML do espelho v23 (antes da extração v24) limita a classificação seguinte:

| Classe/chamada | Linhas cobertas/descobertas; ramos cobertos/descobertos | Decisão de escopo |
| --- | --- | --- |
| `QualificationLaboratoryMain.execute` → `QualificationSupervisor.controlled` → `runChild` → `QualificationSqlEvidence.master/snapshot` → `DriverManager`/sessão SQL → worker empacotado | `runChild` 0/85; 0/28; `master` 0/17; 0/12; `snapshot` 0/22; 0/14 | v24 moveu só o filho para `SqlChildExecution` pinado no shadow; `status` (65/25; 29/33), `receipt` (20/13; 13/21) e `reconcilePending` (13/61; 8/30) permanecem no Supervisor Ubuntu. `QualificationSqlOptIn` preserva o guard booleano puro, e `QualificationSqlEvidence` restante é físico exato. |
| `AnalyticScenarioRuntime` → `LocalAnalyticUsersRuntime.capture` → `session.getConnection` → `captureWithin`/JDBC de staging e promoção | `capture` 0/14; `captureWithin` 0/71; 0/14; `configuration` 0/15 | `observationMode` é puro e testado; `configuration` ainda precisa de inspeção/contrato antes de qualquer exclusão. Classe externa continua Ubuntu. |
| `RelationalLaboratoryMain`/`AnalyticScenarioRuntime` → `LocalRelationalRuntime.capture` → `session.getConnection`/`JdbcRelationalLaboratory` | `capture` 0/75; 0/20; construtor 0/8 | `entity` puro foi testado. Classe externa continua Ubuntu; capturas físicas só poderão sair por colaborador exato. |
| `LocalArtifactScenario`/`QualificationScenarioVerifier` → `DeclaredSqlOracles.monitorDeclarations` → `session.getConnection` | `monitorDeclarations` 0/46; 0/10; `monitoring` 0/10; 0/8 | `row` 0/13 e `cell` 39/9 são oráculos puros; manter classe externa no Ubuntu e separar consulta física apenas após teste causal de oráculo. |
| `LocalArtifactScenario.execute` → runtime de cenário/`previewSequence` → sessão SQL | `execute` 0/38; 0/8; `previewSequence` 0/16 | Construtor, pins e `runtimeFingerprint` permanecem puros; classe externa fica no Ubuntu até separação fina. |

O harness v23 de Supervisor elevou essa classe de 123/431 para 175/431
linhas e de 44/240 para 70/240 ramos por estados observáveis. Ele não
instrumenta a JVM da CLI empacotada nem executa SQL. O traço Graphify
existente apontou `runChild` → `QualificationSqlEvidence.master/snapshot`;
por estar desatualizado após os crashes do update, as linhas acima foram
conferidas na fonte e no XML limpo, não inferidas só do grafo.

## Ranking das rotas mistas no XML limpo v25

| Classe | Linhas cobertas/total; ramos cobertos/total | Maior rota descoberta e consumidor | Fronteira pura mantida |
| --- | --- | --- | --- |
| `LocalAnalyticUsersRuntime` | 4/126; 3/17 | `AnalyticScenarioRuntime.captureUsers` chama `capture` (0/14) → sessão JDBC/savepoint → `captureWithin` (0/71; 0/14) → controle, staging, promoção e dimensão JDBC; lambdas da captura somam 13 linhas descobertas. | `observationMode` (4/4; 3/3), admissão modo/replay e configuração sintética. A execução física foi isolada em `$SqlCapture` pinado; o resultado de cobertura será medido em XML posterior. |
| `LocalRelationalRuntime` | 5/108; 4/24 | `RelationalLaboratoryMain` e `AnalyticScenarioRuntime` chamam `capture` (0/75; 0/20) → `JdbcRelationalLaboratory.policy/scope` e conexão; ainda não separado. | `entity` (5/5; 4/4) e validações de data/modo merecem gate Ubuntu. |
| `DeclaredSqlOracles` | 111/202; 60/110 | `monitorDeclarations` (0/46; 0/10) consulta sessão; `monitoring` (0/10; 0/8) agrega dados do SQL; ainda não separado. | `row`, `cell`, pins e arquivos permanecem puros, com déficits próprios. |
| `LocalArtifactScenario` | 137/219; 49/86 | `execute` (0/38; 0/8) e `previewSequence` (0/16) alcançam execução SQL; ainda não separado. | Construtor, pins, fingerprint e preflight de arquivo permanecem Ubuntu. |

A maior rota física por linhas próprias foi a captura de usuários. No XML
limpo v26, a classe externa mudou de 4/126 linhas, 3/17 ramos para 7/37,
13/13, enquanto `$SqlCapture` compilada tem 0/96 linhas e 0/4 ramos. O
escopo filtrado de `bootstrap` mudou de 3.642/5.966 linhas, 1.594/2.964
ramos para 3.645/5.877 e 1.604/2.960. O ganho puro é +3 linhas/+10 ramos;
a transferência física retira 89 linhas e quatro ramos do denominador
Ubuntu. O POM exige a classe exata no shadow e mutantes de fonte alterada e
include removido reprovam a política; a captura SQL ainda não foi executada.

## Ranking v26 e fronteira relacional

O XML v26 deixou `LocalRelationalRuntime` em 5/108 linhas e 4/24 ramos:
o overload de `capture` que usa execução explícita tem 0/75 linhas e 0/20
ramos, alcançando `JdbcRelationalLaboratory.policy/scope`, conexão,
savepoint, controle, extração auditada, staging e receipt. Os consumidores
são `RelationalLaboratoryMain` e `AnalyticScenarioRuntime`; `entity` está
5/5 linhas e 4/4 ramos. `DeclaredSqlOracles` tem 111/202 linhas, 60/110
ramos, mas sua rota física `monitoring`/`monitorDeclarations` soma 57
linhas descobertas e 18 ramos, enquanto `row` e `cell` são puros.
`LocalArtifactScenario` tem 137/219 linhas, 49/86 ramos; a rota física
`execute`/`previewSequence` soma 54 linhas descobertas e oito ramos,
mantendo construtor, fingerprints e `verifyFiles` puros. A maior rota JDBC
é portanto a captura relacional.

`LocalRelationalRuntime$SqlCapture` contém a primeira consulta a política
persistida e todo o trabalho subsequente da sessão. A classe externa rejeita
dia divergente, data fora da política, SWEEP/replay inconsistente e
cancelamento antes de consultar SQL; o teste exercita esses resultados.
POM e manifesto nomeiam o `.class` exato; mutantes de fonte/POM reprovam.
No XML limpo v28, a classe externa mudou de 5/108 linhas, 4/24 ramos para
16/31, 16/18; `$SqlCapture` física tem 0/83 linhas e 0/6 ramos. O pacote
filtrado mudou de 3.645/5.877 para 3.659/5.800 linhas e de 1.604/2.960
para 1.616/2.954 ramos. O teste de admissão cobriu 11 linhas/12 ramos na
classe externa e mais três linhas em `LaboratoryCaptureWindow.day`; a
transferência retira 77 linhas/seis ramos do denominador Ubuntu. O perfil
shadow físico continua não executado.

## Fronteira física do cenário no XML v28

O XML v28 mantém `DeclaredSqlOracles` em 111/202 linhas, 60/110 ramos:
`monitoring`/`monitorDeclarations` têm 57 linhas e 18 ramos descobertos,
mas a maior parte de `monitorDeclarations` constrói seletores e expectativas
puros; somente a consulta de recibos da partição e a construção de
`QualificationMonitoring` dependem da sessão. `row` (0/13, 0/8) e
`cell` (39/48, 29/42) também permanecem puros. Movê-los em bloco ocultaria
oráculos exercitáveis no Ubuntu.

`LocalArtifactScenario` tem 137/219 linhas, 49/86 ramos no XML v28.
`execute` físico descoberto soma 38 linhas/oito ramos;
`previewSequence` soma 16 linhas, além de comparações/recomposição SQL
delegadas e da seleção de raster em lambda (quatro linhas/quatro ramos)
que é pura. O call graph conferido na fonte liga
`LocalArtifactScenarioMain.execute` e `LocalArtifactSequence` ao cenário,
e este chama `QualificationPhysicalMetadata.verify`,
`AnalyticScenarioRuntime.start/capture`, `QualificationScenarioVerifier`
e `LocalAnalyticCollectionSweep`, todos com sessão JDBC. A rota física
concreta é maior que a consulta isolável dos oráculos. A nova classe
`LocalArtifactScenario$SqlExecution` contém somente essas chamadas físicas;
`verifyFiles`, seleção de raster, admissão de sequência e a escolha de
resultado continuam na externa Ubuntu. O próximo XML limpo precisa medir
o delta; pin, include shadow e mutantes devem passar antes de manter a
extração. No XML limpo v30, a classe externa mudou de 137/219 linhas,
49/86 ramos para 141/186, 54/82; `$SqlExecution` física tem 0/37
linhas e 0/4 ramos, enquanto o record puro `Captured` tem 0/1 linha.
O pacote filtrado mudou de 3.659/5.800 para 3.663/5.768 linhas e de
1.616/2.954 para 1.621/2.950 ramos. O ganho direto é pequeno (+4
linhas/+5 ramos e -32 linhas/-4 ramos no denominador), embora a fronteira
seja clara e o check shadow exija a classe exata. O próximo trabalho
prioriza os oráculos puros `expected`/`row`/`cell` com resultado observável.
O grafo Graphify foi usado só como navegação, pois seu update local falhou
anteriormente; fluxo e contadores foram conferidos na fonte/XML.

## Prova pura v31 do oráculo declarado

`QualificationOracleTest` carrega fixture integral sintética por pin e
exercita `DeclaredSqlOracles.expected` sem SQL. A chave SQL-01 é escopada
ao UUID da execução e conserva o sufixo entre execuções; ordinal fora do
intervalo é recusado. A coluna técnica SQL-03 aceita `observedTime` até
1 ms além da fronteira declarada e recusa 2 ms, tempo anterior e tipo
incompatível. O XML limpo v31 atribui +9 linhas/+6 ramos à classe externa
e +33 linhas/+20 ramos à anônima pura `$1` de `expected`.
O pacote filtrado subiu de 3.663/5.768 para 3.705/5.768 linhas e de
1.621/2.950 para 1.647/2.950 ramos. A consulta de partição em
`monitorDeclarations` continua física e descoberta; não foi excluída
junto com o mapeamento puro.
