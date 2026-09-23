# 0171 — Trilha de conclusão revisada por precedência

## Identificação e objetivo

- Checkpoint documental em 2026-09-19 16:19:51 UTC; `ACEITO_NO_ESCOPO` somente para o planejamento entregue. Campanha técnica permanece `EM_EXECUCAO`.
- Anterior: [0170](0170-roteamento-por-custo-total.md), SHA-256 `3eb2902f08c387680bc9fd604deb13215dd884b234ccecd860b7821964f7efe9`, preservado. A última fotografia técnica continua sendo0168, complementada pelos recibos posteriores a reconciliar em P01.
- Objetivo do usuário: revisar seriamente a trilha pelo STATES, ordenar por prioridade e pelo que realmente precisa anteceder outra tarefa, mantendo economia por modelo/esforço.
- Critérios: `Tarefas pendentes` e ordem obrigatória do STATES, contrato/matriz A–N e gates G01–G08. Entrega: revisão3 de [TRILHA_CONCLUSAO_POR_MODELO.md](../../../TRILHA_CONCLUSAO_POR_MODELO.md).

## Autorização e limites

- Instrução efetiva de 19/09/2026: “revise de forma séria [...] a melhor trilha de conclusão com base no states.md [...] em ordem de prioridade e do que precisa [...] antes de outro”. Autoriza esta revisão documental; não executa os passos propostos.
- Alvos: trilha na raiz, notas no STATES, trilha antiga, RETOMADA e este checkpoint. Sem subagentes, código, migration, fonte, credencial, banco, publicação remota, deploy, commit, push ou corte.
- Campanha física, prazo, orçamento e ledger: sem consumo/renovação por este documento. A campanha A–N conserva os próprios limites e deve ser reconciliada antes de retomada física.
- Nenhuma aprovação pendente para entregar esta documentação. Efeitos futuros seguem sua autorização específica; G01–G08 não são supridos pela trilha.
- Passo, pré-condições, alvos, limites e recuperação registrados antes das edições em `target/trilha-prioridades-20260919-01/WORKLOG.md`. Recuperar somente por diff contra os snapshots desta rodada, preservando alterações alheias.

## Alterações e decisões

- Inventário anterior: `target/trilha-prioridades-20260919-01/before.json`, `before/` e `git-status-before.txt`. As oito exclusões preexistentes de Java/testes foram preservadas, sem restauração ou mudança de índice.
- A trilha agora tem P01–P33: tarefa, modelo/esforço, dependências por escopo, trabalho e saída verificável. Prefácios nos outros documentos apontam para a nova ordem e conservam o texto histórico.
- Prioridade imediata: reconciliar0168/resultados04/05 e autoria de sete etapas; corrigir e provar a revisão atual; supervisor/preview; escalas; revisão e regressão L antes de pacote M e selo N.
- Precedência posterior: ambiente apto antes do efeito material; contrato e identidade antes da vertical real; V2-012a antes do bootstrap; relações aplicáveis antes de V2-012b; referências antes dos consumidores, dimensões depois das próprias fontes; fatos após entradas PUBLISHED/paridade core e antes de V2-012c.
- Política/aplicação de ausência e fatos/contratos são ramos independentes quando seus critérios permitem. A tabela dos cinco fatos conserva somente suas entradas pertinentes. CI, feed e sonda de fonte não receberam dependências globais artificiais.
- Qualificação por entidade converge para a unidade `CUTOVER-DB-01 / DATABASE_WIDE`; não foi proposta troca por entidade sem ratificação. Raster e GraphQL transitório conservam seus aceites/responsabilidades.
- Distribuição recomendada: 28 passos Terra (oito Medium, vinte High), cinco Astra (dois Medium, três High), zero Sol obrigatório. Luna é auxílio opcional para evidência explícita. Não houve benchmark de custo; recomendações não demonstram economia observada.
- Rejeitado o uso de checklist agregado como prova de implementação ausente, pacote anterior à revisão, paridade analítica como pré-requisito do próprio fato e encerramento produtivo por prova sintética/rollback.
- Ocorrência documental: o guard detectou que a trilha havia voltado aos bytes exatos da primeira revisão conhecida. A comparação com o snapshot anterior confirmou ausência de conteúdo novo nessa reversão. Ela foi preservada em `trilha-concurrent-known-revision.md` antes da instalação guardada da revisão3; não foi atribuída uma causa à ocorrência.

## Execução e evidência

| Passo/critério | Camada | Comando/limites | Observado | Evidência |
| --- | --- | --- | --- | --- |
| Confronto de precedência | Documental/read-only | Critérios originais, matrizes e recibos locais; sem fonte/banco | Ordem corrigida e condicionais por entidade/fato/contrato; cobertura dos 48 IDs abertos | Trilha revisão3 e `target/trilha-prioridades-20260919-01/dependency-audit.json` |
| Estrutura e preservação dos aceites | Documental | Parse das tabelas/IDs/checkboxes, links e UTF-8 | PASS: 33 passos únicos, 32 conjuntos explícitos de dependências acíclicas, nove links locais, 115 checkboxes/67 concluídos idênticos | `target/trilha-prioridades-20260919-01/validation.json` |
| Validador histórico da trilha | Documental | `pwsh -NoProfile -File scripts/validation/Test-Gpt56ChatTrail.ps1` | FAIL/exit1 em `STATES_SUCCESSION_HASH_docs/continuidade/RETOMADA.md`, mesma falha anterior | `target/trilha-prioridades-20260919-01/trail-after.log` |

- Verificações documentais complementares e hashes finais ficam na mesma rodada privada. Não há alegação de gate integral verde: o scanner integral anterior tinha oito `MISSING_CANDIDATE`; ele e os testes Java/SQL não foram repetidos para esta mudança de documentação.
- Nenhum efeito físico desta rodada nem processo próprio pendente. Recibos da campanha anterior são evidência histórica, não nova execução desta manutenção.
- Preservados: checkpoints anteriores, snapshots, manifests/ledgers e alterações preexistentes. Aceites funcionais fechados: nenhum. Contadores39/45 e67/115 inalterados.

## Retomada imediata — até três ações

| Ordem | Ação concreta | Pré-condição | Prova esperada | Alternativa independente |
| --- | --- | --- | --- | --- |
| 1 | P01 / Terra Medium: reconciliar revisão/evidências e bloqueios locais | Documentos obrigatórios e recibos da campanha lidos; prompt da seção11 da trilha | Checkpoint técnico atualizado, causas conhecidas, provas reutilizáveis e próximo passo elegível | Preparar a lista de inputs externos ainda faltantes, sem mensagens a terceiros |
| 2 | P02 / Astra Medium somente se restar diagnóstico | P01 e uma falha atual delimitada | Causa e contraprova sem relaxar contrato | Se a causa já está provada, registrar dispensa e seguir para P03 |
| 3 | P03 / Terra High: corrigir/provar a sequência atual | P01, diagnóstico necessário resolvido e autorização/limites da execução conferidos | Provas da mesma revisão entregue, sem renovar campanha por inferência | Preparar correção/testes sem efeito físico quando faltar gate |

- Bloqueios externos: inputs G01–G08 e feed, nos papéis/artefatos e etapas da seção4. Eles bloqueiam apenas o alcance correspondente.
- Parada: dependência não satisfeita, evidência incompatível, resultado desconhecido ou autorização/teto insuficiente; não repetir efeito por resumo incompleto.
- Conclusão desta unidade: roteiro ordenado entregue e verificado. Conclusão do projeto: critérios originais satisfeitos, operação qualificada, corte da unidade aceito e retirada encerrada; nenhum desses resultados é criado por este checkpoint.
