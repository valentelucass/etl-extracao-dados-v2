# 0176 — Estabilização diagnosticada; correção SQL bloqueada pelo escopo

## Identificação e objetivo
19/09/2026. P01 reconciliado; P02 com causas sustentadas; recorteP03 BLOQUEADO_POR_INPUT. A estabilização integral solicitada não foi concluída.
Anterior:0175-reconciliacao-e-diagnostico-dirigido.md, SHA256 4bc3b6ca65be9b090c3def84a6d4131a766a2efcedfd66ed4f087d0ba482ff7f.
Relatório: docs/catalogos/campanhas-integrais/ESTABILIZACAO-20260919.md.
Pedido efetivo: P01→P02→fatiaP03, semP04–P08.39/45 e67/115 preservados; nenhum aceite nominal novo.

## Autorização e limites
A mensagem19/09 autoriza provas locais e correções delimitadas, mas estabelece “Sem DDL/migrations”. Nenhuma migração foi preparada/aplicada, nenhuma fonte/credencial/produção/índice Git foi alterada. Alvo físico localhost/ETL_SISTEMA_V2_SHADOW, Windows existente, dados sintéticos, duas travas Maven, commit bloqueado/rollback.
Tentativa06 pelo controlador existente:3600s,1800s/seq,240s/etapa,heap512MiB/query60s. Maven19m24s, semtimeout; resultado conhecido. Sem renovação de orçamento/prazo/reserva antiga, sem retry após06. Nenhum processo próprio permanece ativo ou efeito desconhecido exige repetição.

## Alterações e decisões
Inventário3540 e snapshots antes: target/macrobloco-campanhas-integrais-20260915-01/stabilization-20260919/. Base0165 isolada validada:3505hashes/7contraprovas. Delta preexistente25modificados/35novos; oito exclusões Git preservadas.04/05 e todas as dez tentativas anteriores reconciliadas.
Três testes ajustados e helper novo: LocalArtifactSequenceTest, IntegralCampaignIT, SequenceReferenceIT e SequenceBindingDiagnostics. Asserções expõem a falha real, validam preservação da fonte e mantêm sete/três etapas e33previews. Quatro Java receberam somente formatter/newlines: IntegralCampaignFixtures, SequenceReferenceFixtures, LocalArtifactSequenceMain e LocalArtifactSequence. Oito arquivos Java finais iguais ao build testado. Nenhuma correção funcional de produto aplicada.
STATES sincronizado antes da trilha revisão3.4; novo relatório explicita causas, limitações e correções necessárias. Não alterar manifesto histórico ou restaurar exclusão para forçar gate. Sucessão de prova via snapshot/delta/inputs é distinta do fechamentoP08.

## Execução e evidência
Rodada física: target/macrobloco-campanhas-integrais-20260915-01/sequence-campaign-sql-06/.
Recibos inputs/reservation/process/result, XMLs e agregados antes/depois preservados. resultOBSERVED/exit1/rollbackConfirmed=true, logs íntegros, semtimeout. Comando pelo Invoke-Build.ps1: Physical; IntegralCampaignIT,SequenceReferenceIT,SequenceRecompositionIT; unit LocalArtifactSequenceTest,SequenceMeasurementsTest; BudgetSeconds3600.

| Critério | Camada | Resultado |
| --- | --- | --- |
| Compilação, Enforcer, Spotless, Checkstyle | Java offline | PASS |
| LocalArtifactSequenceTest + SequenceMeasurementsTest | Unit | 27PASS, zero falhas/erros/skips |
| IntegralCampaignIT A/B atuais | SQL local | 2erros; quatro etapas concluídas, quinta falha |
| SequenceReferenceIT A/B | SQL local | 2falhas; SQL-05 expected2/observed0 |
| SequenceRecompositionIT A/B | SQL local | 2PASS; cinco fatos/19saídas e33capturas sem crescimento |
| Rollback e agregados | SQL local | Confirmados; before/after idênticos |
| Scanner self-test | Offline | PASS16casos |
| Scanner integral antes deste checkpoint | Offline | FAIL:3551candidatos,3542textos,1binário,8MISSING_CANDIDATE preexistentes, nenhum finding de conteúdo |
| Validator histórico contra worktree | Offline | FAIL STATES_SUCCESSION_HASH_docs/continuidade/RETOMADA.md; histórico isolado PASS |
| Diff próprio/UTF-8/contadores/índice/schema/manifests | Offline | Conferidos; git diff check exit0;115/67 preservados |

Evidências resumidas: stabilization-20260919/proof-summary.json, reconciliation.json, previous-attempts.json, historical.json, final-audit.json, scanner-final.log, trail-final.log e review.diff. Readback após este checkpoint em closing-readback.json. Não há gate funcional verde nem selo de entregaP08.

## Causas demonstradas e limites
Campanha: generation troca origin_component MC; V034 ranqueia por origem/componente e mantém dois componentes anteriores atuais. Diagnóstico físico A/B: completed4, activeMC0, latestMC4, conflictingMC4, olderComponents2. ONE_TO_ONE recusa os quatro; guard de linhagem corretamente encontra zero vínculos para duas raízes. compareRecomposition é método comum, não prova etapa final executada.
Referência: fixture05 já declarava3etapas; runtime retorna2 após gate falho. V011/V085 condicionam atualização de tarifa/snapshot a freshness maior; fonte idêntica mantém snapshot antigo e V086 exige release nova. SQL05 perde as duas linhas. A asserção de tamanho ocultava esse resultado; diagnóstico corrigido na06. Não reduzirexpected, mudarcardinalidade/identidade ou inventarfreshness.
Controle independente: recomposição sem troca de relações/referência passa A/B. SequenceFailureIT03 não repetida, sem delta na ordemCOL→FRE. Escalas/regressão integral/supervisor/pacote não iniciados.

## Retomada — até três ações
1. Obter escopo explícito para preparar/qualificar/aplicar evolução SQL aditiva somente no alvo local, com baseline e contraprovas. Fonte do bloqueio: proibiçãoDDL/migrations na mensagem19/09; quem libera: usuário. Nenhum pedido de confirmação de rotina pendente.
2. Com esse escopo, corrigir sucessão do conjunto relacional e revisão tarifária independente do frescor, preservando histórico, identidade, cardinalidade, replay e revisões distintas. Proposta: GPT-6 Astra/High para esse macrobloco coeso.
3. Qualificar A/B sete etapas e referência três etapas, cinco fatos/19saídas/33previews; repetir apenas provas atingidas, mediante admissão permitida. Só então reavaliarP04. Sem autorização, não executarSQL nem repetir as falhas.

Condição de conclusão futura: ambas as sequências aprovadas na revisão entregue, contraprovas, rollback e integridade. P03 inteiro/paisV2/aceites reais permanecem abertos. Este checkpoint consolida resultado parcial por bloqueio concreto, não uma entrega produtiva.