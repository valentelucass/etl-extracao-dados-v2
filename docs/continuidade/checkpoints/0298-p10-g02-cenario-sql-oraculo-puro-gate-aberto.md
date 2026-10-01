# Checkpoint 0298 — P10/G02, cenário SQL e oráculo puro; gate aberto — 25/09/2026

## Autoridade, alvo e recuperação

- Anterior: [0297](0297-p10-g02-runtime-relacional-jdbc-separado-gate-aberto.md), SHA-256 `621b2f1c473c202bcb622210f2350ee96a00fc7d34b901618c1cbca2269d70e1`.
- Instrução: comparar `DeclaredSqlOracles` e `LocalArtifactScenario` por XML/call graph; mover só a maior rota física, manter validação/resultado puro no Ubuntu, provar pin/check shadow/mutantes, medir delta e, se pequeno, priorizar cenário puro observável.
- Base remota `b9dac416737ac69c98d0e63715411b7c62fa03f7`; alvos de escrita worktree local e espelhos privados v30/v31 em `target/ci-p10-20260925-01/`. Índice real, backups, banco, provedor e credenciais preservados. Recuperação: revisar/reverter somente os deltas locais; sem push, merge ou deploy.

## Escolha causal e testes

No XML v28, `DeclaredSqlOracles.monitoring`/`monitorDeclarations` somavam 57 linhas/18 ramos descobertos, mas `monitorDeclarations` constrói seletores e expectativas puros em torno de uma consulta curta de recibos SQL; `row`/`cell` são puros. `LocalArtifactScenario.execute` tinha 0/38 linhas e 0/8 ramos, `previewSequence` 0/16 linhas, além de delegações físicas a metadata, runtime, verificador e recomposição. Fonte/call graph ligam `LocalArtifactScenarioMain` e `LocalArtifactSequence` a essa cadeia JDBC. Graphify serviu só à navegação porque seu update local anterior falhou; a prova é fonte/XML.

`LocalArtifactScenario$SqlExecution` contém metadata, captura, verificação SQL, recomposição e preview transacional. A externa conserva `verifyFiles` antes da sessão, guarda integral, seleção de raster, callback e escolha de resultado PASS/FAIL com preview. `QualificationArtifactScenarioRefusalTest` passou com raster válido, cardinalidade/revisão erradas e sequência baseline recusada antes de SQL. Fonte SHA LF `4d4b4752ca24023f7bba42ee08736cada9e73cf50b3b24c1b49fa50f5baaa16d`, classe compilada exata no manifesto e POM: exclusão apenas Ubuntu e inclusão shadow 80/60. Teste de política reprovou fonte alterada e include shadow removido. O record `Captured` fica sob gate Ubuntu.

Como o delta físico v30 foi pequeno, `QualificationOracleTest` passou a exercitar `DeclaredSqlOracles.expected` com fixture/pins sintéticos sem SQL: chave SQL-01 escopada ao UUID com sufixo preservado, ordinal fora do count rejeitado, `observedTime` SQL-03 aceito até tolerância de 1 ms e recusado a 2 ms, antes da janela ou com tipo incorreto. Essa prova cobre regra e falha que uma mudança de chave/limite detectaria; não afirma cobertura da consulta de partição nem de rollback físico.

## Evidência executada

| Critério | Camada | Observado e limite |
| --- | --- | --- |
| Foco/política | Maven JDK 17, heap 512 MiB | Testes de recusa/preflight, política de escopo e oráculo focal passaram. A versão final com bordas ±1 ms passou no `clean verify` v31. |
| Build v30 | Espelho fresco do HEAD+overlay indexado só nele | 2.317 Surefire + duas Failsafe offline, zero falhas/erros, cinco skips; `clean verify` exit 1 só JaCoCo `bootstrap`. XML filtrado 3.663/5.768 linhas, 1.621/2.950 ramos. Externa `LocalArtifactScenario` 137/219,49/86→141/186,54/82; filho SQL 0/37,0/4; record puro 0/1. Ganho +4 linhas/+5 ramos, -32 linhas/-4 ramos do denominador. |
| Build v31 | Mesmo HEAD, novo espelho indexado só nele | 2.318 Surefire + duas Failsafe offline, zero falhas/erros, cinco skips; `clean verify` exit 1 **somente JaCoCo bootstrap**. `DeclaredSqlOracles` 111/202,60/110→120/202,66/110; anônima pura `expected` +33 linhas/+20 ramos. Pacote filtrado 3.705/5.768 linhas (0,642), 1.647/2.950 ramos (0,558), abaixo de 80/60; faltam 910 linhas/123 ramos. Demais gates Maven passaram. Log privado `clean-verify-v31-private.log`. |
| Segurança v31 | Mesmo espelho, 4.007 arquivos Git | Scanner PASS 4.007 candidatos/4.006 textos/um binário, zero achados/não inspecionados; Gitleaks 8.29.1 exit 0, zero achados. Logs privados `scanner-v31-private.log`/`gitleaks-v31-private.log`. |
| Shadow físico | Perfil `shadow-local-integration` | **Não executado** sem `V2_SHADOW_JDBC_URL`; check separado mantém localhost/`ETL_SISTEMA_V2_SHADOW`, Windows integrado, sintéticos, rollback e 80/60. A inclusão estrutural da classe SQL não é PASS físico. |

`STATES.md` foi sincronizado antes deste checkpoint. Nenhum aceite G02: `verify` local ainda vermelho, sem novo SHA/checks remotos verdes ou owner nominal. Crash local Graphify não reintentado sem diagnóstico novo.

## Retomada

1. Priorizar caminhos puros ainda descobertos em `DeclaredSqlOracles`, `LocalArtifactScenario` e decisões CLI/resultado por oráculo observável; medir XML antes/depois. Consultas JDBC curtas só saem com pin/check shadow e sem ocultar mapeamento puro.
2. Repetir `clean verify`/scanners em espelho fresco dos bytes de código finais; manter 80/60, heap 512 MiB, cinco skips explícitos e shadow IT não executada enquanto faltar configuração exata.
3. Atualizar `STATES.md` antes do próximo checkpoint/trilha. G02 aberto até SHA/checks remotos verdes/owner; sem publicação.
