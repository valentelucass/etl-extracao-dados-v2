# Sonda Data Export — resultado desconhecido

Data: 22/09/2026.

## Ordem registrada

Por instrução explícita posterior do usuário, foi preparada uma nova ordem
independente para a janela fechada 08/09/2026 a 14/09/2026, sem repetir a
partição anterior que recebeu HTTP 429. O escopo era Coletas (6908) e Fretes
(6389), somente leitura por `GET_WITH_QUERY`, páginas 2 e 5, até dez chamadas
seriais, intervalo de dois segundos, timeout de até 30 segundos e resposta de
até 10 MiB. GraphQL, 4924, escrita e banco ficaram excluídos.

## Estado observado

O controlador não recebeu stdout, código de saída ou identificador de sessão ao
fim do primeiro período de espera. A consulta local posterior não encontrou
processo da sonda ainda ativo. Como a sonda direta não persiste saída, não há
evidência autoritativa que permita determinar se a fonte recebeu alguma chamada
ou qual foi o seu resultado.

O estado é `RESULTADO_DESCONHECIDO`: não repetir esta janela, não declarar
sucesso ou falha e não usar a ausência de processo como prova sobre a ESL. Não
houve escrita, banco, DDL/DML, commit, agendamento, deploy ou corte. Nenhum
segredo, URL, payload, cursor, identificador, hash de identificador, cabeçalho
sensível ou dado de negócio foi preservado.
