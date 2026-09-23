# Documentação ESL e trava local contra concorrência

Data: 22/09/2026.

## Leitura da documentação recebida

Os cinco trechos fornecidos pelo usuário repetem o contrato Data Export: `per`
aceita de 1 a 100, e a paginação avança uma página por vez até a resposta vazia.
Para o mesmo IP, a ESL exige pelo menos dois segundos entre requisições e
informa `429` quando a regra não é respeitada. Em janela inferior a 31 dias,
não declara bloqueio de horas; entre 31 dias e seis meses declara uma hora, e
acima de seis meses, 12 horas.

Os trechos não documentam `Retry-After`, outro parâmetro de paginação, um valor
de `per` maior que 100 ou uma espera que recupere um `429`. Eles mencionam dois
caminhos que não foram adotados: usar outro IP e solicitar exportação para FTP.
O primeiro mudaria deliberadamente a origem da limitação; o segundo cria uma
operação externa e está fora da allowlist read-only da sonda.

## Correção local aplicada e teste sem rede

As três sondas autorizadas agora compartilham um mutex do sistema operacional
antes de carregar `.env` ou executar `curl.exe`. Uma segunda sonda V2 no mesmo
computador encerra com `LOCAL_CONCURRENT_PROBE` e zero chamadas. A trava é
preventiva: não prova que a rodada anterior teve concorrência e não controla
V1, outro computador ou outro uso do mesmo IP.

O parser dos quatro scripts envolvidos passou; o autoteste financeiro passou
com zero chamadas. Em teste de contenção local, as três sondas foram chamadas
enquanto uma trava sintética estava detida: as três retornaram exit 1,
`LOCAL_CONCURRENT_PROBE` e `calls_attempted=0`. Não houve rede, leitura de
credencial, escrita, banco, DDL/DML, agenda, deploy ou corte.
