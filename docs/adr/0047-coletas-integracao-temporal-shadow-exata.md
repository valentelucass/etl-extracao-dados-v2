# ADR0047 — integração temporal exata e consumo sintético de Coletas

10/09/2026. Novo escopo explicitamente adotado pelo usuário após o encerramento
do B63. Não reabre o B63 nem cria bloco oficial ou aceite do roadmap.
Responsável técnico: implementação local. Aceitante nominal de negócio: pendente.

## Contratos e compatibilidade

**COL-TIME-11 — representação exata aditiva.** V025 recompõe somente as duas
tabelas e quatro procedimentos de metadados necessários ao adaptador existente
JdbcDataExportExtractionAudit. O shadow atual tem o control plane moderno e não
continha esses objetos históricos. Não reinstalar schema antigo, watermark,
roles ou grants. V026 adota integralmente a proposta observacional da ADR0046;
o arquivo de proposta continua imutável. V027 acrescenta uma representação
`coletas-exact-time-v1`, ligada por FK à linha tipada, contendo epoch second/nano,
presença, JSON bruto, parse e motivo do fallback. Execução, contrato/fingerprint,
fonte/tenant, payload/presença e origem do fallback continuam nos registros ligados.
Nenhuma comparação exata se baseia no DATETIME2(3) histórico.

O overload explícito do staging acrescenta o registro exato na mesma conexão e
transação. Construtores antigos e bindings anteriores mantêm a semântica antiga.
Não recuperar nanos de dados históricos em milissegundos: ausência da nova
representação retorna EXACT_REPRESENTATION_ABSENT.

**COL-TIME-12 — revisão explícita da decisão SQL.** A view/procedure `v2` são
consumidores novos, sem alteração da view observacional histórica. Referências
com o mesmo status/data/instante exato são equivalentes mesmo quando o offset
ou a quantidade de dígitos difere. Instantes distintos por 1ns são conflitantes.
O nativo válido prevalece somente quando a referência presente também é válida
e concorda; referência inexistente permite conservar o nativo, conforme ADR0045.
Referência inválida presente, identidade/escopo/janela divergentes bloqueiam.
Datas civis continuam fallback de frescor, nunca instante do status.

SQL deduplica projeções tipadas da raiz (alias, status, terminalidade e tempo),
preservando todas as linhas físicas detalhadas em staging. Não usa payload de
uma linha arbitrária como representante de toda a raiz. Não cruza massa na JVM.

**COL-TIME-13 — consumidor próprio de laboratório.** O destino
`core.coleta_temporal_laboratory` contém somente a projeção tipada sintética.
Não escreve em core.coleta, não publica, não avança watermark e não pede permit.
Exige opt-in, transação externa, alvo shadow, fonte/tenant sintéticos fixos,
marcador explícito no payload e binding `synthetic-coletas-temporal-v1`.
Fingerprint identifica evidência e nunca autoriza promoção de dados reais.
A sessão Java valida V2_SHADOW_JDBC_URL e autenticação Windows, suprime commits
dos adaptadores existentes e sempre faz rollback e fechamento da conexão física.
Main/RuntimeCompositionRoot e o contrato GraphQL OBSERVATION_ONLY ficam intactos.

**COL-TIME-14 — conflito e antirregressão compatíveis.** Antes da precedência
terminal, empate de instante exato com conteúdo tipado divergente lança 51428.
Aberto→terminal pode prevalecer mesmo em ordem temporal inversa, conforme V010.
Terminal→aberto não regride. Demais estados respeitam a ordem temporal exata.
Replay da execução é idempotente; referência/instante divergentes são recusados.
As operações efetivas de staging, qualificação e consumo usam travas transacionais;
o consumidor serializa o pequeno laboratório por escopo sintético.

## Validação, recuperação e limites

As migrations são instaladas separadamente da IT, após preflight master/alvo.
O banco já possui dados/histórico de outras provas: nenhum reset, baseline
físico do zero ou criação de banco é autorizado. Baseline inclui as migrations
em ordem; equivalência estática não é anunciada como equivalência física.
Tentativas, logs, inventário e rollback dos dados ficam em
target/coletas-temporal-integration-20260910/.

Testes previstos e resultados efetivamente executados devem ser consultados no
relatório desta execução, sem tratar este ADR como evidência de PASS.
Rollback de código usa somente o diff próprio; schema aditivo permanece instalado
por migration. Qualquer recuperação estrutural posterior exige migration própria.
Oráculo independente, correspondências reais, representatividade, aceite nominal
e V2-041 continuam requisitos externos. Não há nova chamada de API nesta decisão.

## Correção aditiva comprovada

A tentativa física03 expôs a diferença de collation no EXCEPT entre a presença
do alias legado (BIN2) e a coluna nova. V028 alinha essa coluna à V010.
V027 já instalada foi preservada por hash; V028 foi qualificada com rollback e
instalada separadamente. physical-06 comprova o consumo, o empate51428,
antirregressão e concorrência após a correção. A representação exata não mudou.
