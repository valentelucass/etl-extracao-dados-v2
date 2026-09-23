# Sonda Data Export — Coletas, sete dias, aceita

Data: 22/09/2026.

## Ordem e limites

Após a recusa HTTP 429 anterior, uma ordem independente consultou somente
Coletas (6908) na janela fechada 01/09/2026 a 07/09/2026. Foi usado apenas
`GET_WITH_QUERY`, ordem estável documentada, páginas 2 e 5, teto de cinco
chamadas seriais, intervalo de três segundos, timeout de até 30 segundos e
resposta máxima de 10 MiB. A janela não repete a que recebeu 429 nem a de
resultado desconhecido.

## Resultado sanitizado

As cinco chamadas previstas foram concluídas, sem parada e com exit code zero.
O metadado e as quatro páginas de amostra responderam HTTP 200 e JSON válido.
As verificações de entidade por `id` foram aplicadas às páginas; o perfil
sanitizado observou `updated_at` em formato temporal com offset. Não houve
erro de contrato, resposta acima do limite ou recusa por limite de acesso.

O resultado confirma contrato e paginação apenas nesta janela e não prova
paridade com GraphQL, cobertura global, snapshot ou unicidade global. O espaço
de três segundos e o teto de cinco chamadas evitaram o 429 nesta ordem, sem
permitir atribuir causalidade ao 429 anterior.

Não houve Fretes, GraphQL, 4924, retry, escrita, banco, DDL/DML, commit,
agendamento, deploy ou corte. Nenhum segredo, URL, payload, cursor,
identificador, hash de identificador, cabeçalho sensível ou dado de negócio foi
preservado.
