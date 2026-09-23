# 0132 — Replay, degradações e metadados físicos

EM_EXECUCAO A–N,13/09/2026. Predecessor0131 SHA256
4b1e22ec58abc02603a3b83fccbc16f437bab937fb053e4e0dddc0c484e36bcf.
Continua o pedido integral adotado; este checkpoint não é entrega final.
Construção37/45 e aceites67/115 mantidos, sem novo aceite operacional.

O verificador comparou as dezenove saídas após BOOTSTRAP, INCREMENTAL,
BACKFILL com correção e REPLAY. A fronteira SOURCE avançou apenas no incremental.
O replay preservou valores de negócio e avançou a linhagem por NOOP, conforme
V094. A expectativa de source_rows agora usa três linhas por captura declarada
no mesmo grupo de frescor, incluindo replay. V069 reconstrói esse grupo entre
capturas; a hipótese anterior de conservar o ponteiro antigo foi refutada.
Nenhuma migration foi alterada ou acrescentada.

As quatro degradações passaram pelo mesmo comparador: Raster incompleto, frota
de Manifestos ausente, referência financeira inválida e snapshot de Coletas
rejeitado. Cada prova comparou19saídas; Captação passou e os dependentes foram
bloqueados. A seleção completa exige todos35escopos, incluindo MAT03/MAT04.
Frota ausente deixa o trator associado por Sinistros e exclui os dois reboques
sem binding; o oráculo declara as três linhas de validade restantes.

O novo guard compara971colunas físicas com a descrição V098 aprovada, incluindo
nomes, ordem, tipos, tamanhos, precisão, escala, nulabilidade e collation.
Está integrado ao filho antes das cargas; seu teste JDBC passou. O pacote deve
conferir esse recurso externo com o mesmo recurso no JAR. Contraprovas e novo
pacote com essa revisão ainda faltam.

| Tentativa em target/macrobloco-qualificacao-pacote-20260913-01 | Resultado observado |
| --- | --- |
| candidate-smoke-replay-01/recompose-01 | Falharam no candidato02; rollback confirmado, erros do oráculo registrados |
| replay-oracle-physical-01/02/03 | Falhas preservadas; corrigida filial emissora de Frete em SQL11 e investigado Metadata de Manifestos |
| replay-and-degradation-physical-04 |6IT:3passaram,2falhas e1erro;0skip, rollback confirmado. Metadados971, Raster e financeiro passaram; diagnóstico identificou source_rows |
| replay-degradation-concurrency-physical-05 |6IT:5passaram e1erro;0skip, rollback confirmado. Replay1 e degradações4 passaram; concorrência falhou antes da disputa esperada |

QualificationConcurrency implementa duas sessões, claims, cancelamento e
reconstrução/consumo após rollback. A primeira prova retornou recusa diferente
da esperada; conferir BEGIN da segunda transação e registrar código exato.
O caminho está integrado à ação CONCURRENCY, ainda não qualificada.

QualificationProcessEvidence e seus quatro testes foram criados depois do
snapshot05; ainda não compilados nem consumidos pelo supervisor. Próxima mudança:
vincular hashes de processo/recibo/reconciliação ao journal, conferir conjuntos
exatos e manter lock do controlador durante toda retomada.

Não há processo próprio ativo ao registrar este checkpoint. Todas as falhas
anteriores permanecem. O candidato02 continua sendo o pacote já executado;
ele não contém as correções recentes e não prova a revisão atual.

Próximas ações:
1. Corrigir e provar concorrência; integrar/qualificar journal e retomada
   adversarial, incluindo exit divergente e PID reutilizado, sem duplicação.
2. Executar novo pacote com replay/recomposição/ausência/faults; completar
   variantes dos oráculos, agenda, limites/métricas e três escalas mais repetição.
3. Verify integral Java17 com378ITanteriores+novas, zero skip novo, quatro
   skips unitários históricos; dois builds reproduzíveis, smoke final,
   scanners/guards, sucessão exata, diffs contra2988iniciais e entrega selada.

SQL exclusivamente localhost/ETL_SISTEMA_V2_SHADOW, Windows integrado, duas
travas, sintéticos rollback-only. Sem COMMIT de domínio, DDL nesta unidade,
fonte real, produção, V1/dashboard, serviços, grants, feed/NVD ou commit/push.
Persistir até a entrega A–N, sem perguntas, subagentes ou pedido de continue.
