# Checkpoint 0249 — janela de 30 dias interrompida por limite da fonte

Data: 22/09/2026. Sucede o checkpoint 0248.

## Fotografia

O pedido do usuário foi convertido nos 30 dias fechados anteriores a 22/09/2026.
A sonda não permite janela maior que sete dias e recusou localmente a janela
inteira, sem qualquer chamada externa. A primeira partição (15/09 a 21/09) foi
então executada dentro dos limites registrados: o metadado 6908 recebeu HTTP
200, mas a primeira consulta de dados recebeu HTTP 429. Duas de dez chamadas
foram usadas e a sonda encerrou a rodada corretamente.

Recibo sanitizado:
`docs/continuidade/probes/2026-09-22-dataexport-30d-rate-limit.md`.

## Próximas ações

1. Não repetir a consulta que recebeu HTTP 429 nem aguardar para repeti-la nesta
   rodada.
2. Em outra ocasião, registrar uma ordem independente para uma partição ainda
   não consultada dentro dos 30 dias, respeitando o teto e a parada imediata.
3. Só preparar comparação GraphQL se uma partição produzir páginas Data Export
   válidas com entidades verificáveis.

Não houve GraphQL, 4924, escrita, banco, DDL/DML, agenda, deploy ou corte.
