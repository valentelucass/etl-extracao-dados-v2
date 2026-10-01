# Checkpoint 0296 — P10/G02, captura JDBC de usuários separada; gate aberto — 25/09/2026

## Autoridade, alvo e recuperação

- Anterior: [0295](0295-p10-g02-cadeia-sql-supervisor-gate-aberto.md), SHA-256 `978ac5edad30a356c87c82153efa97db4db4a55c7001358df5d4f522bfda0b37`.
- Instrução: ranquear rotas mistas no XML v25, separar a maior rota JDBC causal, conservar validação/decisão pura no Ubuntu, provar delta, pin, check shadow, mutante de fonte e testes nos bytes de código finais.
- Base remota: `b9dac416737ac69c98d0e63715411b7c62fa03f7`. Alvo de escrita: fontes, teste, POM, manifesto e continuidade do worktree; espelho isolado `target/ci-p10-20260925-01/candidate-repo-mirror-v26`. Índice real, backups, banco, provedor e credenciais preservados. Recuperação: revisar/reverter somente este diff local; nenhum push, merge ou deploy.

## Fronteira causal e contraprovas

O XML v25 ranqueou `LocalAnalyticUsersRuntime` em 4/126 linhas e 3/17 ramos. `AnalyticScenarioRuntime.captureUsers` chama `capture` (0/14) → `session.getConnection`/savepoint → `captureWithin` (0/71, 0/14) → qualidade, controle, staging, promoção e dimensões JDBC. `LocalRelationalRuntime` tinha 5/108, 4/24; `DeclaredSqlOracles` 111/202, 60/110; `LocalArtifactScenario` 137/219, 49/86. A captura de usuários era a maior rota física própria descoberta.

`LocalAnalyticUsersRuntime$SqlCapture` agora contém somente a transação e composição de adaptadores JDBC. A classe externa preserva `observationMode`, configuração sintética, admissão de modo/replay antes da conexão e `captureClosed` no `finally`. `RuntimeUsersOperationalRequestTest` passou para BACKFILL/REPLAY permitidos e quatro combinações rejeitadas com `ANA_USERS_OBSERVATION_MODE` sem SQL. O manifesto fixa SHA LF `ae4249c0894e2c88940f9c297ee213fbc2d4ecbe43ccfd2f8776bcca0301fd78`; POM exclui apenas o `.class` físico do Ubuntu e o inclui no check shadow 80/60. `CiCoverageScopePolicyTest` reprova fonte alterada sem pin e POM sem esse include, e constatou a classe compilada. Essa exigência é estrutural: a IT física não foi executada.

## Evidência

| Critério | Camada | Resultado e limite |
| --- | --- | --- |
| Testes focais | Maven JDK 17, heap 512 MiB | `RuntimeUsersOperationalRequestTest` e `CiCoverageScopePolicyTest` passaram. Spotless aplicado localmente; comparação com espelho v25 mostrou somente os dois Java intencionais diferentes entre fontes rastreadas. |
| Build v26 | Espelho fresco do HEAD+overlay, Git indexado somente nele | `clean verify` exit 1 só JaCoCo `bootstrap`; 2.315 Surefire + duas Failsafe offline, zero falhas/erros, cinco skips históricos. Demais gates Maven passaram. Log privado `clean-verify-v26-private.log`. |
| Delta JaCoCo | XML v25→v26 pós-exclusões exatas | Bootstrap 3.642/5.966→3.645/5.877 linhas (0,620) e 1.594/2.964→1.604/2.960 ramos (0,542), contra 80/60. Externa 4/126,3/17→7/37,13/13; filho físico 0/96,0/4. +3 linhas/+10 ramos cobertos; -89 linhas/-4 ramos no denominador. Faltam 1.057 linhas/172 ramos no gate Ubuntu. |
| Segurança v26 | Mesmo espelho, 4.005 arquivos indexados | Scanner PASS 4.005 candidatos/4.004 textos/um binário, zero achados/não inspecionados; Gitleaks 8.29.1 exit 0, zero achados. Logs privados `scanner-v26-private.log`/`gitleaks-v26-private.log`. |
| Shadow físico | Perfil `shadow-local-integration` | **Não executado** sem `V2_SHADOW_JDBC_URL`; check separado preserva localhost/`ETL_SISTEMA_V2_SHADOW`, Windows integrado, sintéticos, rollback e 80/60. |

O crash anterior de Graphify não foi reintentado. `STATES.md` foi atualizado primeiro. Nenhum aceite G02: falta `verify` local verde e novo SHA/checks remotos verdes/owner nominal.

## Retomada

1. Classificar por método `LocalRelationalRuntime`, `DeclaredSqlOracles` e `LocalArtifactScenario`; mover só a próxima execução JDBC física com pin/check/mutante e manter validação/oráculo puro no Ubuntu.
2. Medir XML por classe e repetir `clean verify` e scanners nos bytes de código da próxima unidade; manter heap 512 MiB, JaCoCo 80/60 e os cinco skips históricos explícitos.
3. Sincronizar `STATES.md` antes do próximo checkpoint/trilha. Shadow IT segue não executada, G02 aberto e sem publicação.
