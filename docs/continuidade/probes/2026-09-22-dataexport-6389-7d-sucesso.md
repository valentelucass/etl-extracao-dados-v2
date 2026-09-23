# Sonda Data Export — Fretes, sete dias, aceita

Data: 22/09/2026.

## Ordem e limites

Por instrução do usuário, a sonda consultou apenas Fretes (6389) por Data
Export, sem GraphQL, na janela fechada 01/09/2026 a 07/09/2026. Foram usados
`GET_WITH_QUERY`, ordem estável documentada, páginas 2 e 5, teto de cinco
chamadas seriais, intervalo de três segundos, timeout de até 30 segundos e
resposta máxima de 10 MiB.

## Resultado sanitizado

As cinco chamadas previstas foram concluídas, sem parada e com exit code zero.
O metadado e as quatro páginas de amostra responderam HTTP 200 e JSON válido.
As verificações por `id` confirmaram que o teto de entidades pôde ser avaliado;
o perfil sanitizado observou `service_at` e `updated_at` em formato temporal com
offset. Não ocorreu erro de contrato, resposta acima do limite ou limite de
acesso.

O resultado confirma contrato e paginação somente nesta janela. Não prova
cobertura global, identidade canônica, vínculo entre Frete e Coleta, receita ou
relação financeira.

O template 4924 não foi chamado: a sonda financeira permitida combina Data
Export e GraphQL, e a documentação exige confrontar os conjuntos para provar a
relação. Não houve GraphQL, 4924, retry, escrita, banco, DDL/DML, commit,
agendamento, deploy ou corte. Nenhum segredo, URL, payload, cursor,
identificador, hash de identificador, cabeçalho sensível ou dado de negócio foi
preservado.
