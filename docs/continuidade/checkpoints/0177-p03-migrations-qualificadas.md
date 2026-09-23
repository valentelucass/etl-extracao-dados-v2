# 0177 — P03: migrations preparadas e qualificadas, instalação pendente

19/09/2026. Pedido atual autoriza V2 aditivo somente em localhost /
ETL_SISTEMA_V2_SHADOW, qualificação rollback separada da instalação DDL.
Predecessor0176 preservado. Não há autorização de COMMIT de dados de domínio.

Inventário inicial e diff em
target/macrobloco-campanhas-integrais-20260915-01/p03-corrections-20260919/.
Oito exclusões e índice Git preservados; recibos06 reconferidos, PID anterior
ausente. Master confirmou banco ONLINE/read-write. Schema102: objetos efetivos
e coluna de V102 observados; não existe tabela técnica de migration neste alvo.

ADR0052 define declaração completa MC por origem incluída, sem sweep de origens
ausentes, e revisão de tarifa com fonte preservada. V103/V104 novas, baseline
e validator sincronizados. V022/V034/V100 instaladas não foram editadas.
Java aplica declaração somente após todos os lotes do suplemento integral.
Contraprovas Java adicionais preparadas; build p03-directed-01 em andamento.

Qualificação SQL: p03-schema-upgrade-01 e baseline-01 passaram com rollback,
mas nomes automáticos das cinco FKs novas divergiram entre os catálogos.
Correção causal antes de instalar: nomes explícitos. upgrade-02/baseline-02
passaram: logs de catálogo qualificado idênticos, contraprovas de substituição,
omissão parcial, origem omitida, replay e conflito contemporâneo aprovadas.
Catálogo e244contagens anteriores restaurados por rollback nas quatro provas.
São provas do sufixo sobre schema102, não recriação física do banco.

Próximas ações:
1. Conferir resultado do build dirigido, diff, hashes e catálogo; instalar somente
   V103/V104 qualificadas em nova reserva pelo controlador existente.
2. Confirmar schema efetivo e executar A/B sete etapas, referência três etapas,
   recomposição e contraprovas/regressões atingidas, serial e rollback-only.
3. Sincronizar STATES → trilha → verificações → checkpoint → RETOMADA.

Limites: heap512MiB, etapa240s, sequência1800s, tentativa3600s, query≤60s
(schema30s). Resultado desconhecido exige reconciliação. Recuperação pré-COMMIT:
rollback; pós-instalação: migration compensatória revisada. P03 inteiro/P04–P08
e paisV2 não concluídos;39/45 e67/115 preservados.
