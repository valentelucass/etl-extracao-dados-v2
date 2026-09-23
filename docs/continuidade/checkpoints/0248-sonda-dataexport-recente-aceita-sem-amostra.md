# Checkpoint 0248 — consulta recente aceita, sem amostra de Fretes

Data: 22/09/2026. Sucede o checkpoint 0247.

## Fotografia

A documentação ESL foi revisada antes da nova ordem. Ela registra tratamento
restrito para reextração histórica; isso explica de forma plausível, mas não
provada, a recusa HTTP 422 da janela de janeiro. A consulta controlada apenas ao
template 6389 para uma data recente concluiu sem falha: três de cinco chamadas,
todos os status HTTP 200 e arrays válidos, porém vazios. A conexão e o formato
documentado foram aceitos; não há dados para comparar identidade ou paridade.

Recibo sanitizado:
`docs/continuidade/probes/2026-09-22-dataexport-6389-recente-vazio.md`.

## Próximas ações

1. Obter do responsável de negócio uma data recente, fechada e conhecida por ter
   Fretes.
2. Registrar nova ordem serial e limitada para essa data; executar apenas a
   sonda de contrato 6389.
3. Somente se o contrato produzir amostra válida, preparar a prova de identidade
   GraphQL nos limites já autorizados.

Não houve escrita, banco, DDL/DML, GraphQL, 4924, agenda, deploy ou corte.
