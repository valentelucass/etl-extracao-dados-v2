# Sonda Data Export — recusa por limite de acesso

Data: 22/09/2026.

## Escopo e limites

Pedido do usuário: procurar uma amostra nos 30 dias fechados anteriores a
22/09/2026. A sonda aceita no máximo sete dias por janela; a janela inteira foi
recusada localmente antes de credencial ou rede. A primeira partição autorizada
foi 15/09/2026 a 21/09/2026, limitada a Coletas (6908) e Fretes (6389), somente
leitura, `GET_WITH_QUERY`, páginas 2 e 5, dez chamadas seriais no total,
intervalo de dois segundos, timeout de até 30 segundos e resposta de até 10 MiB.

## Resultado sanitizado

Foram usadas duas das dez chamadas. Os metadados de Coletas responderam HTTP
200 e tiveram forma estrutural válida. A primeira consulta de dados respondeu
HTTP 429. A sonda parou imediatamente; não existe página de dados, contagem de
entidades, amostra de Fretes ou comparação de identidade desta rodada.

O HTTP 429 prova apenas que a fonte recusou temporariamente a continuação. Não
prova falha de credencial, filtro incorreto ou ausência de dados. Não houve retry,
fallback, espera seguida de nova tentativa, GraphQL, 4924, escrita, banco,
DDL/DML, commit, agendamento, deploy ou corte. Nenhum segredo, URL, payload,
cursor, identificador, hash de identificador, cabeçalho sensível ou dado de
negócio foi preservado.
