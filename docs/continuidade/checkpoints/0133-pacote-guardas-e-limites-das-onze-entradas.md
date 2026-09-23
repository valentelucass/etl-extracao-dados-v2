# 0133 — Pacote, guardas e limites das onze entradas

EM_EXECUCAO A–N em13/09/2026, continuidade do pedido integral adotado.
Predecessor0132 SHA256
3347e126d4ebb82d35db27d57ec0ae3c5ff9adcc8fea2593905d2015abb98fe0.
Este checkpoint registra progresso; construção37/45 e aceites67/115 mantidos.

O candidato04 executou inspect/plan/run/status/resume/compare em diretórios novos
com espaço/Unicode, nos casos positivo, REPLAY, RECOMPOSE e ABSENCE. Os quatro
smokes passaram; ausência comparou SQL03 e SQL04 nas etapas candidata,
confirmação independente e reaparecimento. A consulta física guardou971colunas.
Revision b297b73a8a762c66ae95b52ed66bc233128aca187c6a83dceca38c91797e2efa,
manifesto4e89734e9bd69d86773185ed8e5f7195e3fa436d64959f622dd7c61895367b49,
ZIP7a32e151fbd94d0c5ddb9740a11db959d1acfb353ae93cc524afe59ad3d44941.
Pacote168membros, oito libs e DLL; PackageDirected05 executou25unitários/0skip.

Vinte mutações de configuração/conteúdo foram recusadas pelo entrypoint do
candidato03, antes de controle/filho/JDBC. A primeira tentativa falhou apenas
na expectativa do código de recusa do SBOM; preservada. Oito mutações de controle
do candidato03 descobriram que log parcial era aceito. O novo selo vincula
processo, recibo, reconciliação, stdout e stderr; as oito mutações passaram no
candidato04, incluindo log parcial, exit divergente, PID reutilizado, saída
duplicada, resultado incompatível, journal truncado e arquivo não declarado.
Resume mantém o lock do controlador e não duplica caso reservado.

A revisão posterior ao candidato04 passou12IT físicas, sem skips, em
metrics-limits-gates-physical-01: concorrência1, controle3, degradação4, métricas2
e metadados2. Agregados SQL antes/depois iguais e rollback confirmado.
Concorrência usa dois SPIDs, recusa53401, cancelamento JDBC e reconstrução/consumo
de fixture após rollback; também passou isoladamente em concurrency-physical-06.

As métricas agora recebem leituras/staging reais das onze entradas, incluindo
Usuários, Cotações, Raster e hidratação. Há exatamente onze contadores, sem
retenção de payload; os67registros de duas raízes fecharam por entrada. Limites
de linhas/bytes/páginas são por entrada e caso; excesso foi recusado antes do
lote excedente. O limite JDBC é compartilhado entre sessões próprias e cobre
prepareCall. Fechamento e rollback permanecem disponíveis após a recusa.
Essas mudanças ainda precisam de pacote extraído e escalas, sem alegação de platô.

Uma divergência de oráculo agora prevalece como FAILED sobre bloqueio esperado
de dependência; a janela não pode sobrescrever essa falha. A contraprova física
alterou um valor de CAP com Raster incompleto: CAP divergiu e o resultado foi
FAILED, mantendo o bloqueio específico de Raster. O guard físico também recusou
mutação de nulabilidade. Não houve migration nova ou alteração em V001–V098.

Evidências privadas em target/macrobloco-qualificacao-pacote-20260913-01,
incluindo todas as tentativas falhas. Nenhum processo próprio ativo neste
checkpoint; consultar WORKLOG.md para operações posteriores.

Próximas ações:
1. Empacotar a revisão de métricas/limites; executar faults, concorrência e
   barreiras pelo entrypoint, conferindo recibos e recursos.
2. Completar variantes não nulas/presença e agenda física da matriz de workloads,
   com oráculos independentes e adiamento de dependentes.
3. Executar escalas e verify integral Java17, preservar378ITanteriores+novas,
   quatro skips unitários históricos; fechar dois builds reproduzíveis, smokes,
   scanners/guards/sucessão, inventários/diffs e entrega A–N selada.

SQL somente localhost/ETL_SISTEMA_V2_SHADOW, Windows integrado, duas travas e
sintéticos rollback-only. Fonte real, gates externos, assinatura, CI, release,
COMMIT/crash durável, produção e platô/SLO permanecem não comprovados.
