# Checkpoint 0251 — Coletas em sete dias aceita pela ESL

Data: 22/09/2026. Sucede o checkpoint 0250.

## Fotografia

O problema operacional foi contornado com uma ordem independente menor: somente
Coletas, uma janela fechada ainda não consultada, cinco chamadas no máximo e
três segundos entre elas. A execução de 01/09 a 07/09 concluiu cinco de cinco
chamadas sem parada, todas HTTP 200/JSON válido. A sonda confirmou que consegue
validar limite de entidades e perfil sanitizado de atualização nesta janela.

Os testes locais também passaram: parser PowerShell e 14 testes Java sintéticos
do contrato. A primeira tentativa Java foi corretamente barrada antes dos testes
por usar JDK 25; a execução efetiva utilizou JDK 17 somente no processo.

Recibo sanitizado:
`docs/continuidade/probes/2026-09-22-dataexport-6908-7d-sucesso.md`.

## Próximas ações

1. Se a paridade de Coletas for necessária, registrar ordem independente para
   a sonda GraphQL na mesma janela, com teto próprio.
2. Tratar a janela que recebeu HTTP 429 e a de resultado desconhecido como
   históricas; não as repetir por inferência.
3. Para Fretes, preparar ordem independente sob o mesmo espaçamento apenas se
   houver objetivo de evidência para essa entidade.

Não houve Fretes, GraphQL, 4924, escrita, banco, DDL/DML, agenda, deploy ou
corte.
