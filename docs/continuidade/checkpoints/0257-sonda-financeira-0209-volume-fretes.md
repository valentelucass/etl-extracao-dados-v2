# Checkpoint 0257 — volume real de Fretes em 02/09

Data: 22/09/2026. Sucede o checkpoint
`docs/continuidade/checkpoints/0256-sonda-financeira-0209-limite.md`
(SHA-256 `e86a7c5849fe867ed0cb232b02a714d56aa6af23c3b5079fcebd11ff0f00135f`).

## Objetivo e autorização

O objetivo do usuário foi superar a interrupção da sonda financeira. A
autorização vigente continuou limitada a consultas serializadas e somente
leitura em memória pelos templates 6908, 6389 e 4924, com GraphQL estático
apenas como auditoria. Não houve produção, escrita, banco, DDL/DML, agenda,
deploy ou corte.

As ampliações temporárias de quatro, cinco e seis páginas foram justificadas
pelas páginas anteriores não terminais e mantiveram resposta máxima de 10 MiB,
timeout de 30 segundos e parada imediata. A última rodada teve teto de 25
chamadas; consumiu dez e parou por página de Fretes não terminal. Esse teto foi
restaurado no controlador para quatro páginas e dez chamadas após a evidência
de que a partição não possui fim conhecido no envelope atual.

## Execução e evidência

| Passo | Camada | Observado | Evidência |
| --- | --- | --- | --- |
| Coletas, quarta página | fonte real somente leitura | terminal, 239 entidades distintas | recibo anterior 0256 |
| Fretes, sexta página | fonte real somente leitura | não terminal, 600 entidades distintas | `docs/continuidade/probes/2026-09-22-financeiro-0209-volume-fretes.md` |

O resultado não é falha da ESL nem ausência de dados: ele só mostra uma massa de
Fretes superior à que a prova financeira pode consumir com segurança. Faturas e
GraphQL não foram chamados nessa execução, portanto relações, receita, CT-e,
Fatura e campos financeiros não foram aceitos.

## Decisão e retomada

Não aumentar páginas em sequência. A próxima prova exige uma das alternativas:

1. Uma partição menor confirmada pela fonte, com semântica, limites,
cobertura e deduplicação documentados; ou
2. Uma data fechada conhecida pelo negócio por conter Fretes e caber no teto
atual.

`scopes.by_updated_at` é complementar no contrato vigente, mas sua semântica de
partição/cobertura não foi comprovada e não pode ser usada para cortar massa por
suposição. A frente permanece `IMPLEMENTADO_NAO_QUALIFICADO` para vínculo,
receita, CT-e, Fatura e equivalência financeira. Nenhum aceite foi fechado.

## Verificação final

O autoteste local passou com quatro páginas e zero chamadas de rede. A inspeção
offline confirmou que as travas foram restauradas para quatro páginas e dez
chamadas. `git diff --check` passou sem erro de whitespace; os avisos CRLF/LF
são preexistentes. O validador de continuidade retornou `HANDOFF_PIN`; a
pendência histórica foi preservada sem reescrever manifestos ou ledgers.
