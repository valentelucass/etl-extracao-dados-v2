# Checkpoint0206 — P07 falho; P08 bloqueado — 21/09/2026

Anterior: checkpoint0205, SHA-256
`000b1a0dd6246d9184647666e7cdebd4b6283a723e7daad20ba7ef0927ff939b`.

P07 `VerifyPhysical` foi reservado e executado uma vez no alvo local autorizado.
Terminou exit 1 após54m43s, sem timeout, log-limit ou problema de UTF-8; rollback
confirmado, 246 tabelas preservadas e zero processos próprios remanescentes.
Failsafe teve 492 testes,2 erros e zero failure/skip. Os dois erros
`EXP_PLAN_REPLAY_ORIGINAL_REQUIRED` são preservados nos XMLs de
`AnalyticScenarioRuntimeIT` e `QualificationReplayIT`.

Diagnóstico: REPLAY encaminha a revisão do ciclo BACKFILL anterior ao plano de
expansão, que exige o BOOTSTRAP original. Código atual coincide com o snapshot
P03 comparado; esses ITs não foram executados na campanha P03 citada. Não houve
alteração de bytes, DDL, fonte, dados de domínio, credencial ou produção.

P08 não iniciou. A/L/M/N permanecem abertos, C–K conservam somente seus aceites
históricos e39/45,67/115 não mudam. Ledger fechado:
`target/macrobloco-qualificacao-pacote-20260913-01/p07-p08-pos0205-ledger-01/ledger.json`.

Próximas ações:
1. Corrigir offline a linhagem BOOTSTRAP exigida no REPLAY e provar o caso sem
   reescrever oráculo/expectativa.
2. Obter nova autorização física finita para reexecutar P07; esta reserva não
   permite retry.
3. Só após P07 PASS, reabrir uma ordem P08 independente para pacote e JAR extraído.
