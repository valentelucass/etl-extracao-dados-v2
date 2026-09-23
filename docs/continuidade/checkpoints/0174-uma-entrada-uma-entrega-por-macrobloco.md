# 0174 — Uma entrada e uma entrega final por macrobloco

## Identificação e objetivo

- 2026-09-19; `ACEITO_NO_ESCOPO` documental. Anterior: [0173](0173-macrobloco-de-chat-sob-demanda.md), SHA-256 `1895df0a9c90bf8a7d051970e6019abc6670c15974ffbe10fd5c9e735284b551`, preservado.
- Pedido efetivo: explicitar apenas uma entrada e uma saída por prompt/chat. A regra anterior já vedava pedir continuação por item, mas não definia a entrega única.
- Entrega: [trilha revisão3.3](../../../TRILHA_CONCLUSAO_POR_MODELO.md), seções11/13 e modelo de prompt, STATES e protocolo sincronizados.

## Autorização e limites

- Manutenção documental solicitada; alvos: quatro documentos inventariados e este checkpoint. Sem código, fonte, banco, credencial, subagentes, índice Git ou publicação. Nenhuma campanha, reserva ou prazo aberto/renovado; nenhuma aprovação pendente para esta entrega.
- Inventário e ação prévios: `target/trilha-entrada-saida-20260919-01/before.json`, `before/`, `git-status-before.txt` e `WORKLOG.md`. Recuperação por diff exclusivo desta manutenção, preservando alterações concorrentes e históricas.

## Alterações e decisões

- Uma entrada inicia o macrobloco; execução autônoma percorre as etapas internas elegíveis; uma entrega final consolida resultado, provas e pendências. Sem pedir continue/confirmação de rotina ou encerrar apenas com plano quando há trabalho coberto.
- Checkpoints e atualizações breves de andamento não exigem resposta. Bloqueio real preserva limites; trabalho independente autorizado pode continuar antes de consolidar impedimento/input na entrega. A regra não inventa autorização nem garante conclusão sem dependências.
- Seleção do próximo macrobloco/GPT pelo STATES + trilha continua sob demanda. O prompt de estabilização sugerido na conversa não foi executado nesta manutenção.
- Mantidos33passos, modelos, dependências, critérios e contadores; rejeitado interpretar saída única como permissão para ignorar bloqueios ou omitir a atualização dos registros.

## Execução e evidência

- PASS documental:33linhas de etapas/modelos/dependências idênticas;115checkboxes/67concluídos preservados;13links locais e diff check exit0. Evidência: `target/trilha-entrada-saida-20260919-01/validation.json`. Hashes/checagens finais complementares na mesma rodada.
- Nenhum teste Java/SQL/fonte ou scanner integral executado. Validador histórico de falha de sucessão conhecida não repetido sem mudança da dependência; não há alegação de gate integral verde.
- Nenhum efeito físico/processo próprio pendente nesta unidade. Checkpoints/manifests/ledgers e trabalho anterior preservados. Aceites funcionais fechados: nenhum;39/45 e67/115 mantidos.

## Retomada imediata — até três ações

| Ordem | Ação | Pré-condição | Saída esperada | Alternativa independente |
| --- | --- | --- | --- | --- |
| 1 | Incluir esta regra em cada prompt de macrobloco solicitado | STATES/trilha correntes e alcance elegível | Prompt preenchido com entrada/entrega únicas e ponto de parada | Delimitar input faltante se necessário |
| 2 | Executar o macrobloco adotado, sem pedir continuação interna | Dependências, autorização e limites conferidos | Trabalho comprovado e checkpoint por unidade coerente | Executar somente a parcela independente autorizada |
| 3 | Consolidar a entrega e atualizar os registros | Resultados reconciliados | Uma resposta final com provas e lacunas | Registrar bloqueio real sem fingir conclusão |

- Condição de parada: limite/bloqueio pertinente ao efeito. Conclusão desta unidade: regra explícita documentada; nenhum avanço funcional presumido. Conferir este checkpoint antes de atualizar RETOMADA.
