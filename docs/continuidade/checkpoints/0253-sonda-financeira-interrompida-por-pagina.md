# Checkpoint 0253 — sonda financeira interrompida por limite de páginas

Data: 22/09/2026. Sucede o checkpoint 0252.

## Fotografia

O usuário esclareceu que GraphQL é comparador transitório, enquanto Data Export
continua fonte do V2. O autoteste da sonda financeira passou sem rede. A execução
real em 01/09 parou corretamente após duas páginas válidas de Coletas: a segunda
não era terminal e a sonda tem limite fixo de duas páginas por fonte. Foram duas
chamadas, sem Fretes, 4924 ou GraphQL.

Recibo sanitizado:
`docs/continuidade/probes/2026-09-22-financeiro-pagina-limite.md`.

## Próximas ações

1. Não repetir 01/09 com a mesma sonda nem aumentar páginas por inferência.
2. Para executar a prova financeira vigente, obter uma data fechada com volume
   de Coletas compatível com duas páginas de 100 entidades, ou aprovar uma
   alteração de desenho/limite da sonda com orçamento e validação próprios.
3. Após uma prova financeira terminal válida, avaliar Frete–Coleta, CT-e,
   receita e campos financeiros pelos critérios documentados.

Não houve Fretes, 4924, GraphQL, escrita, banco, DDL/DML, agenda, deploy ou
corte.
