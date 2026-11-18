# Recuperação de períodos — implementação 0757

Consultar sempre este documento, o [plano de produção](PLANO-PRODUCAO.md), a [comparação V1/V2](REGRAS-EXTRACAO-V1-V2.md), o [ADR 0077](../../adr/0077-recuperacao-explicita-de-periodos.md) e STATES antes de adaptar operação, banco ou dashboard.

## O que a função faz

Recebe a primeira data pendente e o corte final observado. Recupera as partições civis já fechadas, mesmo anteriores aos sete dias normais. Para cada período: confere o registro durável; cria o plano quando ele não existe; chama a extração operacional existente; confirma a publicação antes de avançar. Um dia posterior publicado não faz desaparecer uma lacuna anterior.

Por exemplo, uma parada de 20/02/2024 até 12/03/2024 exige 21 dias fechados, incluindo 29/02. A entrada de laboratório recupera até quatro por lote, portanto esse exemplo exige seis lotes. O próximo lote começa na fronteira confirmada pelo anterior. O dia 12 ainda aberto não recebe confirmação de dia fechado.

Esta implementação é um comando explícito e uma função reutilizável. O acionamento automático pelo futuro ciclo de 60 minutos e a conservação durável da fronteira por escopo continuam pendentes de integração produtiva. O código não inicia tarefa periódica por conta própria.

## Entrada operacional

```text
run --config <configuração> --temporal <política-json> --recover-period --request <modelo-json>
```

Não executar este exemplo como liberação operacional. Ele requer configuração, alvo, autoridade e recibos vigentes aceitos pelo runtime existente. Não provisionar identidades, alterar TLS ou remover guardas para fazê-lo executar.

A política temporal continua com seu schema fechado atual. Requisitos específicos:

| Campo | Significado na recuperação |
| --- | --- |
| `firstUnpublished` | Primeira data necessária do escopo; nunca recalcular como hoje menos sete dias para esconder o backlog |
| `observedAt` | Corte observado para determinar quais partições já fecharam e venceram a estabilização |
| `mode` | `BACKFILL`, extração integral pelo período de negócio |
| `lookbackSeconds` | `0`; sobreposição de atualização não substitui data de negócio |
| `zone` e `civilBoundary` | Fuso e fronteira civil explícitos da política qualificada |
| `maximumBacklog` | Limite do lote; a entrada atual de laboratório permite no máximo quatro |
| `maximumReconciliation` | Limite de resumos por consulta; nenhuma massa de negócio é carregada para decidir cobertura |
| `invocation` | Nova invocação operacional, com os recibos exigidos por ocorrência e fase |

O modelo operacional mantém contrato, filtros por entidade, limites de páginas/linhas, qualidade, dependências e destino. Fretes continua exigindo Coletas da mesma janela. A entrada temporal permanece restrita aos workloads e ambiente de laboratório já admitidos.

## Saída e retomada

`PERIOD_RECOVERY` informa `contiguous`, `resumeFrom`, `dispatched`, `pending`, `action`, `backlog`, `blackout` e `scheduler=0`. `dispatched` é número de chamadas ao executor, não contagem de linhas nem comprovação de todas as entidades. `resumeFrom` é a próxima data ainda sem cobertura contínua após os períodos confirmados.

| Situação | Comportamento |
| --- | --- |
| Período ausente | Persiste plano idempotente, confirma sua existência e inicia a extração atual |
| `PUBLISHED` | Não extrai novamente; avança somente o prefixo contínuo |
| Ocorrência em andamento | Usa recuperação da ocorrência original; não cria outra identidade |
| Falha/cancelamento/bloqueio terminal | Informa `EXPLICIT_REPLAY_REQUIRED`, mantém pendência e para |
| Executor retorna sem publicação | Mantém fronteira anterior e para antes dos próximos períodos |
| Recibo perdido/erro SQL | Interrompe; próxima execução concilia o registro durável antes de repetir |
| Blackout | Não pula o período bloqueado |
| Lote concluído, mais períodos devidos | Retorna backlog e a data de continuação; não declara recuperação integral |

Códigos: 0 sem pendência devida no lote/horizonte; 10 lote limitado com backlog; 20 autorização recusada; 30 erro ou resultado durável não confirmado; 40 pendência/replay/blackout, ou código mais grave devolvido pela execução atual. Entrada inválida retorna 2. Usar `backlog` e a fronteira junto com o código; ausência de janela devida não confirma extração do dia aberto.

O operador/supervisor conserva a fronteira confirmada por entidade, origem/tenant, contrato e filtros. Em uma retomada com resultado desconhecido, usar um início anterior seguro e conferir os registros existentes; não assumir que houve publicação. Uma fronteira fornecida indevidamente depois da lacuna não pode ser corrigida por este comando, pois ela é uma entrada do controle operacional.

## Regras e evidências

Origem: pedido do responsável neste chat, 08/11/2026. Responsável de negócio: responsável pelo sistema; nome formal não informado.

| Regra | Exemplo | Prova automatizada |
| --- | --- | --- |
| REC-01: recuperação independe de D-7 | 21 dias fechados em seis lotes | `recoversTwentyOneDaysAcrossLeapDayInBoundedPassesAfterRestart` |
| REC-02: fronteira só avança após publicação | Dia 2 publicado, dia 1 faltante | `missingDayBeforeLaterPublishedDayIsExtractedAndFrontierNeverJumpsIt` |
| REC-03: conciliar antes de repetir | Queda após commit do plano ou publicação | `crashAfterPlanCommitIsReconciledBeforeAnyRepeatedPlanWrite`, `crashAfterPublicationResumesFromDurableSummaryWithoutExtractingThePrefix` |
| REC-04: replay terminal explícito | Seis estados terminais | `terminalFailuresRequireExplicitReplayAndStopLaterSourceCalls` |
| REC-05: identidade estável por período | Mesmo dia em outro lote e observação | `recoveryOccurrenceRemainsIdenticalWhenBatchAndObservationChange` |
| REC-06: isolamento por escopo | Uma entidade/tenant publicado, outro pendente | `oneEntityCannotCompleteAnotherEntityOrTenantNamespace` |
| REC-07: extração de negócio sem filtro de atualização adicional | Dia antigo e fevereiro bissexto inteiro | Testes de requisições de recuperação em `RuntimeTemporalBindingTest` |

Recibos, resultados e limitações executados estão no checkpoint 0757 e em `outputs/testes-recuperacao0757.json` do chat. Preservar também as tentativas falhas de preparação do executor de testes.

Antes de produção ainda é necessário exercitar o cliente real, fonte e SQL com dados não vazios; provar paginação, publicação, identidade/granularidade e falha de processo; integrar supervisor e fronteira durável; qualificar os contratos de todas as entidades; medir tempo total e executar os demais gates de P0–P6. Não alterar dashboard, produção, agendamento ou migrations com base apenas nos testes locais.
