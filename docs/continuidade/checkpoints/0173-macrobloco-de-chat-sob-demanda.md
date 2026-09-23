# 0173 — Seleção de macrobloco e GPT sob demanda

## Identificação e objetivo

- 2026-09-19 16:56:20 UTC; `ACEITO_NO_ESCOPO` documental. Anterior: [0172](0172-passagem-de-tarefa-entre-chats.md), SHA-256 `62759fc38a77be78f8dbac28809b5f16a15cc467195435fa1e22b7ebb8f2d2cb`, preservado.
- Correção efetiva do usuário: ele pedirá um prompt com base nos dois MD; o chat deve identificar qual macrobloco pode ser feito de uma vez no mesmo chat e qual tipo de GPT usar.
- Entrega: [trilha revisão3.2](../../../TRILHA_CONCLUSAO_POR_MODELO.md), seções11/13, com seleção sob demanda pelo STATES + trilha. O pedido não executa P01 nem outro macrobloco.

## Autorização e limites

- Escopo autorizado: corrigir a documentação do fluxo de uso. Alvos: STATES, trilha na raiz, protocolo de continuidade, RETOMADA e este checkpoint.
- Sem código, banco, fonte, credencial, subagentes, índice Git, publicação, commit ou push. Sem novo orçamento, prazo ou ledger físico; nenhuma aprovação pendente para esta manutenção.
- Ação/pré-condições/limites/recuperação registrados antes das edições em `target/trilha-macroblocos-chat-20260919-01/WORKLOG.md`. Inventário e versões anteriores em `before.json`, `before/` e `git-status-before.txt`. Recuperação por diff exclusivo da manutenção, preservando alterações concorrentes/preexistentes.

## Alterações e decisões

- Rejeitada pelo usuário a interpretação0172 de gerar passagem automaticamente a cada tarefa. Ela permanece no histórico, explicitamente superada pelos novos prefácios e protocolo vigente.
- Ao solicitar um prompt, selecionar um conjunto coeso de etapas/fatias em ordem, com dependências externas satisfeitas e dependências internas comprovadas antes de avançar. Os33passos não representam33chats.
- Escolher um GPT/nível suficiente para o conjunto e explicar a economia esperada de contexto, sem alegar benchmark. Exemplos candidatos: P03–P05/Terra High, revisão P06/Astra Medium, P07–P08/Terra High, fatias de uma vertical ou saída analítica. Exemplos não criam elegibilidade ou obrigação de agrupamento.
- Executor prossegue entre tarefas internas cobertas, sem pedir continuação por item; checkpoints continuam por unidade coerente. Para em limite, bloqueio ou decisão externa. Ao encerrar, atualiza os registros; outro prompt só quando o usuário pedir.
- Mantidos os critérios, dependências, modelos individuais e ordem das33etapas. Preparar prompt não executa efeitos nem cria/renova autorização. Nenhum macrobloco físico novo foi admitido.

## Execução e evidência

| Verificação | Camada | Observado | Evidência |
| --- | --- | --- | --- |
| Comparação contra snapshots | Documental | PASS:33linhas de tarefas/dependências/modelos e115checkboxes/67concluídos idênticos | `target/trilha-macroblocos-chat-20260919-01/validation.json` |
| Links e diff | Documental | PASS:13links locais, diff check exit0 | Mesmo relatório; checagens finais no WORKLOG/after.json |

- A falha histórica `STATES_SUCCESSION_HASH_docs/continuidade/RETOMADA.md` continua pendente; o validador não foi repetido sem mudança de sua dependência. Não há alegação de gate integral verde.
- Sem build, teste Java/SQL/fonte ou scanner integral. Nenhum efeito físico/processo próprio desta rodada nem aceite funcional novo. Manifests, ledgers e checkpoints anteriores preservados;39/45 e67/115 mantidos.

## Retomada imediata — até três ações

| Ordem | Ação | Pré-condição | Prova/saída esperada | Alternativa independente |
| --- | --- | --- | --- | --- |
| 1 | Quando solicitado, gerar o prompt do próximo macrobloco pelo STATES + trilha | Estado atual e evidências da frente conferidos | Prompt preenchido com etapas/fatias, GPT/nível e ponto de parada | Informar input faltante se nenhum macrobloco estiver elegível |
| 2 | Executar o macrobloco adotado pelo usuário | Escopo, dependências e autorizações pertinentes | Resultado verificável e registros atualizados | Preparação independente dentro do escopo, se existir |
| 3 | Quando solicitado novamente, recalcular o macrobloco seguinte | Resultado anterior registrado | Novo prompt derivado do estado atualizado | Retomar a lacuna parcial antes de avançar, quando aplicável |

- Na fotografia atual, P01/reconciliação em Terra Medium precede a decisão sobre P02; a lista de candidatos não autoriza embutir execução física não delimitada.
- Parada: evidência/dependência/autorização insuficiente ou limite aplicável. Conclusão desta unidade: documentação corrigida; conclusão funcional permanece nos critérios canônicos. Conferir este checkpoint antes de apontar RETOMADA.
