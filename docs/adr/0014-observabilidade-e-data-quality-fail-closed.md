# ADR 0014 — Observabilidade e Data Quality fail-closed

## Estado

Aceito localmente em 31/08/2026 para V2-023. A ativação produtiva de policies, retenção física,
entrega externa de alertas e health remoto continua em V2-045b/V2-041 conforme o gate aplicável.

## Decisão

O SQL Server é a autoridade para `COUNT`, cardinalidade, equações, SLA e thresholds sobre massa.
Uma avaliação comum executa quatro checks fixos, em uma transação, sob o mesmo application lock do
apply. Ela persiste somente contagens, códigos técnicos e fingerprints. Java recebe exatamente uma
linha agregada O(1); ausência, duplicidade, shape inválido, erro SQL, `FAILED` ou execução parcial
falham fechados.

O protocolo de promoção passa a exigir dois permits no boundary Java: contrato/configuração e Data
Quality. Independentemente do caller, um trigger no evento de publicação revalida no banco a
avaliação `PASSED`, a policy vigente, todos os resultados e o vínculo com a promoção. Como o trigger
roda na transação do apply, a recusa desfaz core, reconciliação, pointer e watermark.

Policies exigem owners explícitos para threshold, SLA de quarantine e retenção. A
migration não semeia policy, owner, TTL ou default produtivo. Checks de domínio continuam nas
verticais; o framework comum não aceita SQL arbitrário.

Cada policy é vinculada ao SHA-256 canônico do escopo ambiente/fonte/tenant/entidade/modo e ao
SHA-256 de todo o seu material, inclusive owners, retenção, vigência e quatro checks. Só a última
linha efetiva do escopo pode autorizar uma avaliação e ela precisa estar `RATIFIED`; revogar a mais
nova não reativa uma anterior. Os três checks estruturais têm threshold zero. O threshold tolerante
existe somente em `QUARANTINE_SLA`, combina limite absoluto e basis points com `AND`, e é
recalculado no health e no trigger para impedir que uma avaliação antiga autorize publicação após
o cruzamento temporal do SLA.

Logs são JSON e eventos produtivos usam campos fechados, correlação opaca, redaction prévia e budget
O(1). Stack, argumentos, MDC, mensagens do driver, IDs e payloads não são serializados pelo encoder.
Métricas têm dimensões fixas; alertas persistem apenas código, severidade, owner e contagem. Health
local retorna uma única linha agregada e SQL indisponível ou shape inválido significa `DOWN`.

O root do Logback fica `OFF`; somente os dois adapters Data Export explicitamente listados podem
alcançar os appenders e ambos passam pelo logger limitado. Cada instância estática por
componente/processo aceita até 1.000.000 eventos primários e 256 MiB estimados, mais no máximo um
resumo técnico de exaustão quando ainda couber no teto de bytes. A correlação thread-bound guarda
somente o hash opaco, exige fechamento LIFO e cobre o streamer síncrono atual. V2-022 deverá fazer
propagação explícita se introduzir handoff assíncrono; o contexto não é herdado entre threads.

O adapter JDBC exige timeout de statement em segundos inteiros. Isso não substitui login/socket
timeout nem cancelamento cooperativo: a composição produtiva de V2-022 deve configurar esses três
limites no `DataSource`/driver e ligar cancelamento ao statement sem relaxar o fail-closed.

Diagnóstico detalhado é uma procedure separada com `TOP(N)` obrigatório entre 1 e 32 e campos
sanitizados. Nenhuma query devolve o universo de chaves/linhas para comparação na JVM.

## Consequências

- Resultados DQ, métricas e alertas são evidência durável e não entram no hard delete de V005.
- A publicação permanece inerte sem policy ratificada; isso é deny-by-default intencional.
- A retenção produtiva dessas novas evidências depende do gate externo V2-045b, sem alterar o
  `maxHistory=0` dos logs.
- Métrica e alerta expõem falhas tipadas no pacote de observabilidade, sem obrigar o orquestrador a
  depender do adapter JDBC; retry continua condicionado à classificação fechada.
- Sondas ESL/Raster, credenciais, deploy e integrações externas não fazem parte deste bloco.
