# P07 — correção de linhagem Replay e requalificação física

## Resultado

P07 `p07-replay-pos0207-verify-01` passou uma única vez em
`localhost/ETL_SISTEMA_V2_SHADOW`, com Windows integrado, dados sintéticos e
rollback-only. Não houve operação P08.

| Evidência | Resultado sanitizado |
| --- | --- |
| Correção | REPLAY recebe a revisão BOOTSTRAP preservada na linhagem, não a revisão BACKFILL anterior. |
| Contrato SQL | V049 não foi alterada. |
| Offline | 2.162 testes unitários: 0 falhas, 0 erros, 4 skips históricos; formatter, Checkstyle e Test-TrilhaPreparation PASS. |
| Física | exit 0; Failsafe 492 testes, 0 falhas, 0 erros, 0 skips. |
| Segurança operacional | rollback confirmado; UTF-8 e limites de log íntegros; zero processo próprio após término. |
| Integridade | agregados antes/depois iguais: 246 tabelas e 1.816 objetos. |

`AnalyticScenarioReplayLineageTest` cobre a propagação BOOTSTRAP→INCREMENTAL→BACKFILL→REPLAY; `AnalyticScenarioRuntimeIT` e `QualificationReplayIT` passaram na VerifyPhysical. O validador `Test-QualificationRegression.ps1` confirmou a suíte, cobertura e rollback.

O manifesto sucessor está em `docs/catalogos/p07-replay-correção/manifesto.json`.
`Test-Gpt56ChatTrail.ps1` continua vermelho porque o manifesto histórico P06 fixa
o hash anterior de `AnalyticScenarioRuntime`; ele não foi reescrito para ocultar
a correção P07. `Test-TrilhaPreparation.ps1`, JSON e `git diff --check` passaram.

## Limites

P08 não iniciou: não houve autoria de exemplos, pacote, extração, smoke,
sequências A/B, provas ou guards. Ele é elegível somente para ordem futura
independente. A/L/M/N seguem abertos; C–K permanecem aceites históricos e os
contadores continuam 39/45 e 67/115. A falha terminal P07 POS0205 permanece
preservada e não foi alterada.
