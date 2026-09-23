# Checkpoint0135 — agenda física, capturas e fronteiras

13/09/2026, depois de temporal-matrix-physical-07 e temporal-plans-physical-01.
Objetivo permanece executar A–N do pedido integral de qualificação/pacote até
entrega única final. EM_EXECUCAO; construção37/45 e aceites67/115 mantidos.
Anterior:0134-variantes-tipadas-e-ondas-pelo-pacote.md, SHA256
79efa37e5d62a441d9ac61c0272cfbee0e015cffb39de11b6961f048e25c89c4.

LaboratoryCaptureWindow separa partição de publicação e datas inclusivas da
fonte. Sobrecargas nos três caminhos existentes preservam seus defaults.
QualificationTemporalMatrix consome as cinco políticas atuais copiadas com pins,
RuntimeTemporalPlanner/Coordinator e suas procedures, capturas e dispatcher COT.
V034 exige um dia por captura relacional: lookback de COL/FRE é decomposto em
duas capturas reais, sem truncamento. Cada prova isolada usa savepoint/rollback.
COT publica pelo dispatcher; COL/FRE/MAN/LOC conservam DEGRADED de laboratório.
Nenhuma nova migration ou publicação fictícia.

temporal-matrix-physical-07:1IT/14,64s/0skip/PASS/rollback. Conferiu cinco workloads,
UUID/partições/auditorias reais, antigo fora da janela, late data e overlap,
BOOTSTRAP/INCREMENTAL/BACKFILL/REPLAY, publicação não contígua sem avanço,
entrada incompleta sem avanço, fechamento da lacuna avançando ambas as partições,
replay/bootstrap/backfill sem alterar watermark, meia-noite inexistente recusada,
limite civil03:00 com partição real de23horas publicada e catch-up SQL limitado
mantido NOT_STARTED. Planos de caso e fronteira da fonte permanecem distintos.

temporal-plans-physical-01:1IT/5,769s/0skip/PASS/rollback. Três planos reais das
novas consultas bounded foram preservados em build/target/qualification-temporal-plans.
Dois usam seeks; o agregado de staging usa scan sobre a pequena fixture. Nenhum
spill ou PlanAffectingConvert. Sem pedido de grant ou mudança de índice/schema.
Reuso do gravador de planos do ScaleIT muda apenas visibilidade do helper de teste;
as378IT anteriores ainda precisam ser preservadas no verify integral desta revisão.

Tentativas temporais01–06 preservadas: estilo, parser inacessível, fim relacional
inclusivo, single-day exigido por V034 e tabela de auditoria diferente em COT.
Falhas levaram à correção do caller/fixture/verificação, mantendo contratos SQL.
Runner de build agora salva bytes brutos separados, limita logs a16MiB, valida
UTF-8 estrito e fecha apenas a árvore de processo própria. Quatro tentativas
posteriores comprovaram captura íntegra e rollback; não há processo próprio ativo
ao gravar este checkpoint. Conferir WORKLOG para ações iniciadas depois dele.

Candidato05 ADMISSION passou6comandos/3casos: blackout, expired e not-due ficaram
BLOCKED_DEPENDENCY/testPassed, sem filho/recibo; status/resume/compare preservaram
o adiamento. Candidato05 segue distinto do Java atual. TEMPORAL ainda não passou
pelo pacote; variante corrigida também aguarda smoke atual. Os dois testes
unitários novos de janela/pins compilaram, mas ainda não foram executados.
Matrizes descritivas35responsabilidades/673colunas foram geradas como pendentes
de verify final. Não constituem novas provas ou unidades de construção.

Próximas ações:
1. PackageDirected atual/28unitários, candidato novo e smokes VARIANTS/TEMPORAL,
   ADMISSION, quatro barreiras e escalas4/16/32/16; preservar todas as tentativas.
2. Verify integral Java17,378IT anteriores por identidade+novas,0skipnovo e quatro
   skips unitários históricos; corrigir gates/cobertura/revisão realmente falhos.
3. Dois builds independentes do mesmo snapshot, pacote/smokes finais, scanners,
   schema/runtime/continuidade e contraprovas, diffs contra2988snapshots iniciais,
   relatório/comandos/quadros/manifestos/selos e sucessão final exata A–N.

Somente localhost/ETL_SISTEMA_V2_SHADOW, Windows integrado, duas travas,
sintéticos rollback-only. Sem DDL, COMMIT de domínio, fonte real, serviço,
scheduler, grants, feed/NVD, commit/push, limpeza ou outra fronteira operacional.
Prosseguir autonomamente após compactações; não encerrar parcialmente.
