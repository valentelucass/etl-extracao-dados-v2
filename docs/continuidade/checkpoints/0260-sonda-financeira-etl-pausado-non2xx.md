# Checkpoint 0260 — sonda financeira com ETL de produção pausado

Data: 22/09/2026. Sucede o checkpoint
`docs/continuidade/checkpoints/0259-documentacao-esl-trava-local-429.md`
(SHA-256 `3516ac8e22aaf8c2e3ee8b766e7b2f9835bc0fd2872a56e38b451cb1071773b1`).

## Objetivo e autorização

O usuário informou que pausou o processo de ETL de produção e autorizou a nova
rodada. O estado parado foi confirmado localmente antes da execução. A rodada
continuou limitada aos endpoints, templates, leitura em memória, teto e regras
de parada já autorizados; não iniciou, alterou ou reativou o processo de
produção.

## Execução e evidência

| Passo | Camada | Limites | Observado | Evidência |
| --- | --- | --- | --- | --- |
| Confirmação do ETL de produção | local, somente leitura | processo indicado pelo usuário | estado parado | `STATES.md` |
| Trava de sondas V2 | local, sem rede | exclusividade local | livre, zero chamadas | `STATES.md` |
| Sonda financeira 02/09 | fonte real somente leitura | `per=100`, 9 páginas/fonte, 35 chamadas, 3 s | 8 chamadas; parada por `HTTP_NON_2XX` | `docs/continuidade/probes/2026-09-22-financeiro-0209-etl-pausado-non2xx.md` |
| Correção do recibo | local, sem rede | nenhum endpoint novo | próximo resumo incluirá status HTTP e etapa | mesmo recibo |

Coletas foi terminal; Fretes estava na terceira página válida quando a chamada
seguinte foi recusada. Faturas e GraphQL não foram chamados. Não há relação
financeira aceita.

## Decisão e retomada

O ETL de produção pausado não resolveu o problema, portanto não há motivo para
mantê-lo parado em nome desta sonda. A causa do não-2xx não pode ser deduzida,
pois a versão anterior não emitiu o status numérico. A melhoria local preserva o
próximo status e etapa de forma sanitizada, mas não autoriza nem executa retry.

| Ordem | Ação concreta | Pré-condição | Prova esperada | Alternativa independente autorizada |
| --- | --- | --- | --- | --- |
| 1 | Reativar o ETL de produção quando o operador desejar | decisão operacional do usuário | processo sob controle do usuário | nenhuma ação V2 necessária |
| 2 | Nova rodada somente mediante ordem independente após condição externa nova | autorização, janela e teto registrados | status HTTP/etapa sanitizados ou terminalidade | manter a frente aberta sem inferir causa |

A condição de parada permanece HTTP não-2xx, `429`, erro de contrato,
identidade/limite inválidos, página não terminal no teto ou orçamento atingido.

## Verificação local

O parser PowerShell e o autoteste financeiro passaram após a melhoria do
recibo, com zero chamadas de rede. Nenhum segredo, URL, payload, cursor,
identificador, hash de identificador, cabeçalho sensível ou dado de negócio foi
registrado.

`git diff --check` passou sem erro de whitespace. O validador
`Test-ContinuidadeAgentes.ps1` retornou `HANDOFF_PIN` (exit 1); nenhum
manifesto ou ledger histórico foi alterado para mascarar esse drift.
