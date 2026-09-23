# Sonda Data Export — 6389 recente sem registros

Data: 22/09/2026.

## Autorização e limites

Autorização explícita do usuário para testes de sombra das fontes já permitidas.
Consulta somente leitura, por meio da sonda versionada de contrato e `curl.exe`.
Alvo permitido: template 6389. Janela fechada recente: 20/09/2026. Teto: cinco
chamadas seriais, intervalo mínimo de dois segundos, timeout de até 30 segundos
e resposta máxima de 10 MiB. A rodada pararia no primeiro HTTP não-2xx, 429,
erro de contrato, limite não verificável ou teto atingido.

## Resultado sanitizado

Foram usadas três das cinco chamadas permitidas, sem motivo de parada. A leitura
de metadados e as duas leituras iniciais de dados responderam HTTP 200. As
respostas de dados eram arrays JSON válidos, vazios; portanto, a contagem de
entidades distintas foi verificável e igual a zero. A segunda página de cada
amostra foi dispensada porque a primeira estava vazia.

Isso confirma que a credencial, a rota e o formato atual documentado da consulta
de Fretes funcionam para essa janela recente. Não confirma identidade, paginação
com dados ou paridade, pois não houve registros. A recusa HTTP 422 da janela de
02/01/2026 permanece sem causa estruturada registrada; a regra histórica do
fornecedor é apenas uma hipótese compatível, não uma conclusão.

Não houve GraphQL, template 4924, repetição, escrita, banco, DDL/DML, commit,
agendamento, deploy ou corte. Nenhum segredo, URL, payload, cursor, identificador
ou dado de negócio foi preservado neste recibo.
