# Checkpoint 0258 — sonda financeira interrompida por limite de taxa

Data: 22/09/2026. Sucede o checkpoint
`docs/continuidade/checkpoints/0257-sonda-financeira-0209-volume-fretes.md`
(SHA-256 `8d8465bc512c9f0a1b73260d1eb28f6b2f98a506c911bb401cd8cf1b2922147f`).

## Objetivo e autorização

O usuário pediu para ler a documentação ESL indicada e encontrar/testar a
solução para a parte pendente. A leitura corrigiu a conclusão anterior: o
contrato histórico de 6389 já registrava oito páginas não vazias mais uma página
vazia com `per=100`. A rodada autorizada continuou limitada a leitura serial em
memória dos templates 6908, 6389 e 4924 e GraphQL estático somente como
auditoria. Produção, escrita, banco, DDL/DML, agenda, deploy e corte permaneceram
proibidos.

## Execução e evidência

| Passo | Camada | Limites | Observado | Evidência |
| --- | --- | --- | --- | --- |
| Validação do controlador | local, sem rede | até 9 páginas, 35 chamadas | parser e autoteste aprovados; zero chamadas | `STATES.md` |
| Sonda financeira 02/09 | fonte real somente leitura | `per=100`, 9 páginas/fonte, 35 chamadas, 3 s | 11 chamadas; parada imediata por `HTTP_429` | `docs/continuidade/probes/2026-09-22-financeiro-0209-http429.md` |

Coletas chegou ao terminal; Fretes tinha seis páginas válidas e ainda não
terminais quando a décima primeira chamada foi recusada. Faturas 4924 e
GraphQL não foram chamados. Não se pode concluir qualquer relação financeira.

## Decisão e retomada

O controlador passa a suportar o teto documentado de nove páginas e 35 chamadas
somente por ordem explícita, mantendo o padrão restritivo. A solução de
paginação foi encontrada, mas a fonte aplicou limite de taxa antes de permitir a
travessia completa. A documentação só declara intervalo mínimo de dois segundos
e cooldown histórico; como a tentativa usou três segundos em janela recente,
ela não determina uma espera adicional suficiente nem autoriza retry.

| Ordem | Ação concreta | Pré-condição | Prova esperada | Alternativa independente autorizada |
| --- | --- | --- | --- | --- |
| 1 | Obter da ESL/operador o limite efetivo ou uma janela sem concorrência no IP | regra operacional verificável, sem repetir esta ordem | teto/espera que permita uma nova ordem independente | manter a frente financeira aberta sem inferir relações |
| 2 | Executar uma nova ordem serial somente após a pré-condição | orçamento e limite renovados no registro | resumo terminal ou nova parada sanitizada | não fazer retry automático/manual |

A condição de parada continua sendo HTTP não-2xx, `429`, erro de contrato,
identidade/limite não verificável, nona página não terminal ou teto. Nenhum
aceite de domínio foi fechado.

## Verificação final

O autoteste da sonda passou com zero chamadas de rede antes da rodada. A rodada
remota produziu resumo sanitizado e exit code de falha esperado para a parada
controlada. `git diff --check` passou sem erro de whitespace. O validador
`Test-ContinuidadeAgentes.ps1` retornou `HANDOFF_PIN` (exit 1); nenhum manifesto
ou ledger histórico foi alterado para mascarar esse drift. O checkpoint preserva
a fotografia 0257 e não altera manifests ou ledgers históricos.
