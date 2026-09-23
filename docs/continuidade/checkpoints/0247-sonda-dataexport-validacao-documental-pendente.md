# Checkpoint 0247 — validação documental da sonda pendente

- Checkpoint: 0247; 22/09/2026 15:22 UTC; encerramento documental da sonda.
- Anterior: `docs/continuidade/checkpoints/0246-sonda-dataexport-sombra-interrompida.md`, SHA-256 `3883661bb6249a49cd024ddfdec67f612ecd7c1a5fa7bd54695a5c0419f32c28`.
- Objetivo do usuário: testar as fontes permitidas em modo sombra, sem escrita.
- Estado da frente: `BLOQUEADO_POR_INPUT`; a fonte recusou a consulta HTTP 422 e
  a sucessão documental do novo delta ainda não possui manifesto próprio.

## Resultado do fechamento

- `git diff --check`: sem erro de whitespace.
- `Test-ContinuidadeAgentes.ps1`: recusado com `HANDOFF_PIN` após os novos
  registros de States, trilha e retomada. Manifestos e ledgers históricos foram
  preservados, portanto a recusa não foi ocultada por alteração de hashes antigos.
- Resultado externo preservado: 7/7 chamadas da sonda de contrato, parada no
  primeiro HTTP não-2xx; GraphQL e 4924 não executados.

## Retomada imediata

| Ordem | Ação concreta | Pré-condição | Prova esperada | Alternativa independente |
| --- | --- | --- | --- | --- |
| 1 | Diagnosticar offline a recusa HTTP 422. | Recibo sanitizado 0246. | Causa e correção contratual delimitadas. | Nenhuma chamada externa. |
| 2 | Criar sucessão documental própria, sem alterar histórico. | Fluxo de sucessão identificado. | Validação documental aplicável. | Preservar o estado atual. |
| 3 | Nova rodada serial somente sob nova ordem. | Causa resolvida, teto novo e sucessão válida. | Recibo sanitizado. | Nenhuma. |

Nenhum aceite, escrita, DDL/DML, banco, deploy ou corte ocorreu nesta unidade.
