# Validação de falhas da carga do ETL V2

## Falhas observadas

| Ocorrência | Tratamento | Evidência |
| --- | --- | --- |
| Fretes de 11/11/2026, página 20, HTTP 500 | Repetir a mesma página dentro do limite de tentativas | A página voltou a responder 200. O dia completo retornou 1.028 linhas. |
| Fretes de 09/11/2026, páginas 7 e 13, HTTP 422 `timeout` | Repetir somente se o código da resposta for `timeout` | A página 13 retornou 200 na tentativa automática e o dia terminou com 803 linhas. |
| Contas a Pagar, HTTP 422 `timeout` | Aplicar a mesma regra de transporte | O erro está no registro da qualificação de 13/11. A paginação terminou em uma consulta posterior. |
| Inventário, HTTP 422 `timeout` | Repetir a mesma página dentro do lote | Retornou 200 na tentativa automática. O ciclo de um dia terminou com 9.974 linhas de 12 fontes conferidas no SQL. |

## Falhas reproduzidas em testes controlados

Os testes de transporte exercitam HTTP 500, 422 com `timeout`, 422 de consulta inválida, 401, 403, 429, tempo limite de transporte e limite de tamanho. O teste do ciclo injeta HTTP 503 nos metadados. A rotina de recuperação também verifica o limite de tentativas e a recusa de uma nova chamada quando o orçamento se esgota.

Os testes de carga inicial exercitam a indisponibilidade de metadados, o atraso de uma fonte enquanto outras avançam, a parada após três lotes com a mesma falha, pouco espaço em disco, rejeição de gravação e perda da conexão depois de gravar e antes de confirmar a leitura. A data pendente fica no cursor. O teste de retomada refaz a data inteira e confirma que a visão vigente contém uma única fotografia, preservando as repetições reais das linhas da fonte.

Os testes de ciclo também cobrem mudança de estrutura do relatório, data fora da janela, cadência de 60 minutos, mudança de dia à meia-noite, 1.080 datas por fonte no histórico simulado e reconferência de alterações. As simulações de disco e de SQL não exigem interromper o serviço ou encher o volume do servidor.
