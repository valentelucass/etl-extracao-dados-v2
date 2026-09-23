# Checkpoint 0006 — amostras reais limitadas B56

Sucessor de 0005, em 08/09/2026. O mesmo objetivo e limites continuam vigentes.
Quatro GET de dados, HTTP 200/curl 0, cada qual com duas linhas físicas; janela
fechada 04/09/2026, page 1/per 2. Os dois filtros de Contas a Pagar foram enviados.
O ledger privado registra quatro reservas e quatro resultados observados, sem
retry, próxima página, erro, excesso de limite ou resultado desconhecido.

Evidência nova, estritamente limitada à amostra:

- 8636: `ant_ils_sequence_code` INTEGER; ID raiz não veio em `/id` ou
  `/accounting_debit_id`. Duas parcelas distintas não refutam a colisão histórica.
- 4924: `/id` INTEGER; `invoices_mapping` ARRAY com quatro STRING ao todo;
  `fit_fte_invoices_order_number` ARRAY, um vazio, dois STRING ao todo.
- 10633: `/sequence_code` INTEGER; mapping ARRAY com três STRING ao todo.
  A lacuna de observação do shape recebeu evidência real, sem definir chave filha.
- 6392: `/sequence_code` INTEGER; `icm_fis_ioe_number` STRING; ID raiz não exposto
  nos paths testados. Ainda falta interpretar a composição e suas cardinalidades.

Artefatos: `target/bloco56-continuacao/request-data.json`, `data-ledger.jsonl`,
`data-<template>.json`. Respostas brutas e valores de identidade não persistidos.
Cada resumo declara `identityProven=false`; nada transforma esses oito registros
em prova universal, comparação temporal, garantia de fornecedor ou aceite.

As quatro identidades e verticais permanecem bloqueadas. Contagem 65/115.
Há oito consultas remotas observadas nesta continuação (quatro info/quatro dados),
zero SQL, campanha runtime, rotação, alteração de grants ou saldo transferido.

Próximas ações:

1. Introspecção de consulta GraphQL, com documento estático, sem buscar registros,
   para descobrir somente entidades/relacionamentos relevantes e tipos declarados.
2. Confrontar o schema retornado e os critérios originais, distinguindo o que
   pode ser comprovado por contrato da decisão fiscal/identidade ainda ausente.
3. Entregar matriz revista de evidências/impedimentos e sincronizar a continuidade
   por sucessão exata; não repetir as oito consultas já observadas.
