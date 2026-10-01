# Checkpoint 0297 — P10/G02, captura JDBC relacional separada; gate aberto — 25/09/2026

## Autoridade, alvo e recuperação

- Anterior: [0296](0296-p10-g02-runtime-users-jdbc-separado-gate-aberto.md), SHA-256 `70ce3a01726a382341e37a5a4dab689444f83662d42c92223fa2fa9a63f877cf`.
- Instrução: conferir o nome literal da classe de usuários após a expansão de `$` apenas na mensagem Maestri; ranquear os três mistos restantes, separar a maior rota JDBC, preservar admissão pura, medir delta e repetir gates sem enfraquecer 80/60.
- Base remota `b9dac416737ac69c98d0e63715411b7c62fa03f7`; alvo apenas worktree e espelho privado `target/ci-p10-20260925-01/candidate-repo-mirror-v28`. Índice real, backups, banco, provedor e credenciais preservados. Recuperação: revisar/reverter somente este diff local; sem push, merge ou deploy.

## Confirmação anterior e ranking causal

O nome completo da classe anterior está em `STATES.md`, checkpoint 0296, manifesto e POM: `br.com.esl.etl.v2.bootstrap.LocalAnalyticUsersRuntime$SqlCapture`. O `.class` compilado existe no v26; o POM contém exclusão Ubuntu e inclusão exata no check shadow. O vazio na mensagem Maestri foi apenas interpolação do `$` pelo shell da mensagem.

XML v26: `LocalRelationalRuntime` 5/108 linhas, 4/24 ramos, com `capture` explícito 0/75 e 0/20; `DeclaredSqlOracles` 111/202, 60/110, com `monitoring`/`monitorDeclarations` físico 57 linhas/18 ramos descobertos e `row`/`cell` puros; `LocalArtifactScenario` 137/219, 49/86, com `execute`/`previewSequence` físico 54 linhas/oito ramos e preflight/fingerprints puros. A maior rota JDBC era a captura relacional, consumida por `RelationalLaboratoryMain`/`AnalyticScenarioRuntime` e iniciada em `JdbcRelationalLaboratory.policy/scope` antes da conexão.

`LocalRelationalRuntime$SqlCapture` contém essa leitura persistida e a transação/dispatcher físico. A externa conserva `entity`, `Capture` e rejeição antes do banco de execução nula, dia divergente, data fora da política, SWEEP/replay inconsistente e cancelamento. `RelationalLaboratoryContractTest` executou admissões BOOTSTRAP/REPLAY e falhas com códigos observáveis sem SQL. Fonte pinada por SHA LF `5b110cab4c8dfaff330ac2ed8b65cc827645cc1d6d51501609bd2f13e82e4bc9`; manifesto nomeia classe interna compilada, POM a exclui somente do Ubuntu e inclui no check shadow. Mutantes de fonte alterada e include retirado reprovam a política. Isso esclarece a fronteira testável e deixa a execução física exigível, embora não executada.

## Evidência

| Critério | Camada | Observado e limite |
| --- | --- | --- |
| Foco | Maven JDK 17, heap 512 MiB | `RelationalLaboratoryContractTest` e `CiCoverageScopePolicyTest` passaram. Spotless foi aplicado; comparação com espelho v27 mostrou só os dois Java intencionais diferentes entre fontes rastreadas. |
| Build v28 | Espelho fresco do HEAD+overlay, indexado só nele | `clean verify` exit 1 apenas JaCoCo `bootstrap`; 2.316 Surefire + duas Failsafe offline, zero falhas/erros, cinco skips históricos. Demais gates Maven passaram. Log privado `clean-verify-v28-private.log`. |
| JaCoCo v26→v28 | XML após exclusões exatas | Bootstrap 3.645/5.877→3.659/5.800 linhas (0,631) e 1.604/2.960→1.616/2.954 ramos (0,547), abaixo de 80/60. Externa 5/108,4/24→16/31,16/18; filho físico 0/83,0/6. +11 linhas/+12 ramos na admissão externa, +3 linhas em `LaboratoryCaptureWindow.day`; -77 linhas/-6 ramos no denominador. Faltam 981 linhas/157 ramos. |
| Segurança v28 | Mesmo espelho, 4.006 arquivos | Scanner PASS 4.006 candidatos/4.005 textos/um binário, zero achados/não inspecionados; Gitleaks 8.29.1 exit 0, zero achados. Logs privados `scanner-v28-private.log`/`gitleaks-v28-private.log`. |
| Shadow físico | Perfil `shadow-local-integration` | **Não executado** sem `V2_SHADOW_JDBC_URL`; check separado mantém localhost/`ETL_SISTEMA_V2_SHADOW`, Windows integrado, sintéticos, rollback e 80/60. |

`STATES.md` foi sincronizado antes deste checkpoint. Nenhum aceite G02: falta `verify` local verde e novo SHA/checks remotos verdes/owner nominal. Crash prévio de Graphify não reintentado sem causa nova.

## Retomada

1. Classificar por método `DeclaredSqlOracles` e `LocalArtifactScenario`, preservando `row`/`cell`, pins, fingerprints e preflight no Ubuntu; extrair somente SQL demonstrado com pin/check/mutante.
2. Medir novo XML e repetir `clean verify`/scanners nos bytes de código finais, sem reduzir JaCoCo 80/60 ou heap 512 MiB.
3. Atualizar `STATES.md` antes do próximo checkpoint/trilha. Shadow IT segue não executada; G02 aberto sem publicação.
