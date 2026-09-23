# 0179 — P03: campanhas e referência aprovadas; regressão em execução

19/09/2026. Continuação de0178, mesmo pedido/autorizações/limites. Schema104
instalado com recibo e readback; V001–V102 preservadas. Sem DDL adicional.

p03-campaign-sql-07 terminou OBSERVED/exit0, rollbackConfirmed=true, semtimeout,
zero falhas/erros/skips.30unit(25sequência+2medição+3schema) e6IT PASS:
IntegralCampaignIT2(A/B,sete etapas,480.46s), SequenceRecompositionIT2(209.781s),
SequenceReferenceIT2(três etapas,216.5s). Cinco fatos,19saídas e33previews nos
pontos contratuais. A referência verifica páginas/revisão de fonte preservadas,
duas novas revisões de snapshot com mesmo frescor/hash e releases distintas,
quatro snapshots totais após recomposição. Maven19m53s sob teto3600s.

Recibos, XMLs, inputs, logs e before/after:
target/macrobloco-campanhas-integrais-20260915-01/p03-campaign-sql-07/.
Resumo corrente: p03-corrections-20260919/proof-summary.json. O formatter
preservou edição posterior da contraprova COT; essa classe será testada na
regressão própria, não recebe aceite pela compilação anterior.

Nova tentativa p03-regression-sql-01 em execução, serial após07, reserva própria
até3600s,heap512MiB,queries60s ou menores, dados sintéticos/rollback:
RelationalLaboratoryMatrixIT, RelationalLaboratoryLocalIntegrationIT,
AnalyticLaboratoryQuotesIT, IntegralArtifactReplayIT, IntegralContextIsolationIT
e SequenceFailureIT. Seleção causal em regression-selection.md; sem saldo antigo.

Checks estáticos foundation/progressive/COT/package guards e self-test scanner
PASS. Scanner integral mantém apenas8MISSING_CANDIDATE preexistentes; validator
histórico mantém STATES_SUCCESSION_HASH_docs/continuidade/RETOMADA.md. Históricos
preservados, sem PASS artificial; código/diff final ainda será vinculado às provas.

Próximas ações:
1. Reconciliar result/XML/agregados de regression01; corrigir somente falha causal
   demonstrada e não repetir tentativa sem nova admissão permitida.
2. Conferir bytes testados, preservação, UTF-8/diff e verificações finais do recorte.
3. Sincronizar STATES → trilha → verificações → checkpoint → RETOMADA.

P03 inteiro/paisV2 continuam abertos;39/45 e67/115 preservados. P04–P08 não iniciados.
Recuperação: rollback de testes; schema só por nova migration compensatória
revisada. Sem fonte/credencial/produção/DDL improvisado/COMMIT de domínio/Git index.
