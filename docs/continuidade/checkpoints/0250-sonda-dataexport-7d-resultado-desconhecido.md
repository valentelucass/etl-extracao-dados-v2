# Checkpoint 0250 — sonda de sete dias com resultado desconhecido

Data: 22/09/2026. Sucede o checkpoint 0249.

## Fotografia

Após a instrução explícita do usuário para testar sete dias, uma nova ordem foi
registrada para 08/09 a 14/09, sem repetir a partição que recebeu HTTP 429. O
controlador não recebeu saída, código de saída nem sessão da execução. A consulta
local não localizou processo ativo e a sonda não mantém arquivo de resultado.
Não há prova segura de que houve chamada remota, tampouco de seu resultado.

Recibo sanitizado:
`docs/continuidade/probes/2026-09-22-dataexport-7d-resultado-desconhecido.md`.

## Próximas ações

1. Não repetir a janela 08/09 a 14/09 sem reconciliação autoritativa própria.
2. Não tratar a ausência de processo como confirmação de ausência de chamada.
3. Antes de qualquer nova sonda, confirmar uma forma autorizada de obter recibo
   de execução que sobreviva à perda de stdout, sem ampliar endpoints ou gravar
   dados de negócio.

Não houve GraphQL, 4924, escrita, banco, DDL/DML, agenda, deploy ou corte.
