# Checkpoint 0266 — Cotações: paginação interrompida por limite físico

Data: 22/09/2026. Sucede o checkpoint 0265.

## Ordem e resultado

Ordem read-only autorizada: consultar o metadado e as páginas 2–7 de 6906, em
uma janela já observada, com `per=100`, sete chamadas no máximo, intervalo de
três segundos, timeout de 30 segundos, resposta de 10 MiB e trava V2. O
metadado e a página 2 retornaram HTTP 200; a página 2 excedeu 100 linhas físicas.

A sonda parou corretamente com `PHYSICAL_ROW_BOUND_EXCEEDED` após duas chamadas.
Páginas 3–7 não foram chamadas, sem retry, fallback ou mudança de transporte.
Não foram retidos payloads, IDs, URLs, cabeçalhos ou dados de negócio; o recibo
sanitizado é `docs/continuidade/probes/2026-09-22-6906-paginacao-limite.md`.

## Conclusão

A expansão física não possui grão/identidade verificável nessa ordem. Não há
página terminal, contagem de entidades, completude, contrato oficial, tarifa,
oráculo ou aceite adicional; nenhuma nova caixa foi marcada. O limite foi uma
recusa correta do controlador, não um erro a ser contornado. Nova paginação só
pode ser proposta com contrato que defina como validar a expansão e nova ordem
explícita.

`git diff --check` passou, com avisos CRLF/LF preexistentes. Não houve banco,
escrita, DDL/DML, GraphQL, 4924, deploy ou corte.
