# Paginação 6906 — parada por limite físico — 22/09/2026

## Ordem

Caracterização read-only autorizada para Cotações: metadado e páginas 2–7 da
janela já observada, com `per=100`, teto de sete chamadas, intervalo de três
segundos, timeout de 30 segundos, resposta máxima de 10 MiB e trava
interprocessos V2. Parar no primeiro HTTP não-2xx, 429, JSON/contrato inválido,
limite físico excedido ou página não terminal no teto. Sem retry, fallback,
GraphQL, banco, escrita, DDL/DML, agenda, deploy ou corte.

## Resultado sanitizado

O metadado e a página 2 responderam HTTP 200. A página 2 retornou mais de 100
linhas físicas, excedendo o limite da ordem. A sonda encerrou corretamente com
`PHYSICAL_ROW_BOUND_EXCEEDED`, após duas chamadas; páginas 3–7 não foram
consultadas. Não foi registrado payload, ID, URL, cabeçalho ou dado de negócio.

O resultado não prova quantidade de entidades, grão da expansão, terminal,
estabilidade, completude ou paridade. Uma futura paginação só pode prosseguir
sob nova ordem e contrato que defina como validar o grão/identidade quando uma
página física exceder `per`.
