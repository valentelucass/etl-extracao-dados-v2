# 0172 — Passagem de tarefa entre chats

## Identificação e objetivo

- 2026-09-19 16:26:45 UTC; `ACEITO_NO_ESCOPO` documental. Anterior: [0171](0171-trilha-priorizada-por-dependencias.md), SHA-256 `8789b4d8127a743b5e2dec5bbaa306f6ad7ef4df42c1929974d4c46c16ab7119`, preservado.
- Pedido do usuário: deixar pronta a documentação para solicitar, a cada chat que encerra uma tarefa, um prompt na própria conversa que permita ao próximo chat retomar com o contexto necessário.
- Entrega: seção13 e prompt inicial da [trilha revisão3.1](../../../TRILHA_CONCLUSAO_POR_MODELO.md), com regra correspondente no protocolo de continuidade. A ordem/modelos P01–P33 permanecem os da revisão3.

## Autorização e limites

- Autorização: manutenção documental pedida em 19/09/2026. Alvos: STATES, trilha da raiz, protocolo, RETOMADA e este checkpoint.
- Sem execução de P01, subagentes, código, schema, fonte, banco, credencial, publicação externa, commit ou push. Nenhum prazo/teto/ledger físico foi aberto ou renovado; aprovação pendente para esta entrega: nenhuma.
- Inventário e ação registrados antes das edições em `target/trilha-handoff-20260919-01/before.json`, `before/`, `git-status-before.txt` e `WORKLOG.md`. Recuperação por diff exclusivo desta manutenção, preservando alterações concorrentes/preexistentes.

## Alterações e decisões

- Instrução copiável para o usuário pedir encerramento e prompt; obrigação incorporada ao primeiro prompt P01 e ao protocolo lido em cada retomada.
- Cada passagem exige modelo/esforço, etapa/fatia e IDs, resultado real, arquivos/revisão, testes/recibos, pendências, processos/resultados desconhecidos, dependências, autorizações/limites, até três ações e critério de saída.
- O executor preenche os dados reais e entrega um único bloco `text` na resposta final, sem depender do histórico do chat ou exigir que o usuário complete marcadores. A passagem inclui a mesma obrigação para o chat seguinte.
- Tarefa parcial transmite a retomada exata; bloqueio não promove a etapa. Projeto concluído com evidência não recebe tarefa fictícia. O prompt não transfere automaticamente autorização nem disponibiliza artefatos privados ausentes.
- Rejeitado entregar somente um link/arquivo, avançar automaticamente o P, copiar logs inteiros ou executar a próxima tarefa ao preparar a passagem.

## Execução e evidência

| Verificação | Camada/limites | Resultado | Evidência |
| --- | --- | --- | --- |
| Comparação das 33 etapas/modelos e dos checkboxes | Documental, contra snapshots prévios | PASS: linhas idênticas;115checkboxes/67concluídos preservados | `target/trilha-handoff-20260919-01/validation.json` |
| Links, UTF-8 e diff | Documental | PASS:13links locais, três documentos; diff check exit0 | Mesmo relatório; hashes finais/checagens complementares na rodada |
| Validador histórico | `pwsh -NoProfile -File scripts/validation/Test-Gpt56ChatTrail.ps1` | FAIL/exit1, mesma falha de sucessão em RETOMADA já registrada | `target/trilha-handoff-20260919-01/trail-after.log` |

- Nenhum build, Java, SQL, fonte ou scanner integral executado nesta manutenção; não há alegação de gate integral verde. Verificações complementares estritamente documentais ficam no WORKLOG da rodada.
- Efeitos físicos/processos próprios desta rodada: nenhum. Preservados checkpoints, manifests/ledgers e trabalho preexistente. Aceites funcionais novos: nenhum;39/45 e67/115 inalterados. A campanha técnica segue `EM_EXECUCAO`, última fotografia0168 mais recibos posteriores.

## Retomada imediata — até três ações

| Ordem | Ação | Pré-condição | Prova esperada | Alternativa independente |
| --- | --- | --- | --- | --- |
| 1 | P01/Terra Medium: reconciliação e passagem preenchida ao final | Documentos obrigatórios, checkpoint0168, WORKLOG/recibos04/05 e autoria atual | Checkpoint técnico atualizado e próximo escopo justificado | Listar inputs externos faltantes sem contatar terceiros |
| 2 | P02/Astra Medium se restar diagnóstico | P01 e falha atual delimitada | Causa e contraprova | Dispensar justificadamente se a causa já está comprovada |
| 3 | P03/Terra High: correção/prova da sequência atual | Dependências e autorização/limites pertinentes | Provas da mesma revisão entregue | Preparação sem efeito físico quando faltar gate |

- Bloqueios externos e condições de parada permanecem os da trilha e campanha; falta de evidência/autorização impede apenas o efeito dependente.
- Conclusão desta unidade: documentação de passagem pronta. Próximo chat deve ler o estado corrente e os artefatos, sem inferir execução de P01 por este registro. Salvar/conferir este checkpoint antes de atualizar RETOMADA.
