# Sonda financeira — ETL de produção pausado, interrupção não-2xx

Data: 22/09/2026.

## Resultado sanitizado

Antes da rodada, o processo de produção indicado pelo usuário estava parado. A
trava local V2 também estava disponível. A sonda financeira executou uma única
travessia serial para 02/09, com `per=100`, teto de nove páginas por fonte, 35
chamadas, três segundos entre chamadas, timeout de 30 segundos e resposta de no
máximo 10 MiB.

Foram realizadas oito chamadas; a primeira resposta recusada encerrou a rodada
em `HTTP_NON_2XX`. Coletas terminou em quatro páginas com 376 linhas físicas,
239 entidades distintas, 137 linhas físicas repetidas, identidade verificável e
nenhuma entidade inconsistente. Fretes chegou a três páginas válidas, ainda não
terminais, com 304 linhas físicas, 300 entidades distintas, quatro linhas
físicas repetidas, identidade verificável e nenhuma entidade inconsistente.
Faturas 4924 e GraphQL não foram chamados.

A versão que executou esta rodada não incluía o número exato do status no resumo
sanitizado; logo, ele permanece desconhecido e não deve ser inferido. A sonda
foi corrigida localmente para incluir o próximo status HTTP e a etapa de
processamento no resumo, sem reter corpo, URL, segredo, cabeçalho, cursor ou
dado de negócio.

## Conclusão

Pausar o ETL de produção não eliminou a interrupção da ESL. Isso descarta esse
consumidor conhecido como explicação suficiente, mas não prova causa do erro nem
exclui outros consumidores do IP ou comportamento da própria fonte. Não houve
retry, fallback, escrita, banco, DDL/DML, agenda, deploy ou corte.
