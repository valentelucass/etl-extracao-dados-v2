# Checkpoint0207 — P07 Replay corrigido e requalificado — 21/09/2026

Anterior: checkpoint0206. A falha histórica permanece preservada.

P07 `p07-replay-pos0207-verify-01` passou uma única VerifyPhysical no alvo local
autorizado. A correção propaga a revisão BOOTSTRAP até REPLAY, sem mudar V049.
Offline:2.162 testes sem falhas/erros,4 skips históricos. Física:492 ITs sem
falha/erro/skip,exit0,rollback,logs UTF-8/limites e agregados246tabelas/1.816objetos
preservados;zero processo próprio.

Ledger fechado: `target/macrobloco-qualificacao-pacote-20260913-01/p07-replay-pos0207-ledger-01/ledger.json`.
P08 não iniciou e requer ordem independente. A/L/M/N abertos;C–K históricos;
39/45 e67/115 inalterados.

Próximas ações:
1. Preservar a evidência P07 e não repetir a tentativa.
2. Solicitar/usar apenas ordem própria futura para P08, desde pacote.
3. Manter os critérios A–N e contadores sem promoção indevida.
