# ADR 0027 — Fretes 6389: decisão local de presença, frescor e performance

- Status: aceito somente para V2-011a; implementação base permanece em V09/V2-011
- Data: 2026-09-06
- Escopo: decisão offline e fail-closed de FRE-01–FRE-07

## Contexto

O contrato `dataexport-6389` e a identidade V2-009a já fixam `/id` inteiro como source key da
origem, sob `source_instance`, `tenant_scope` e entidade. Eles não autorizam transformar
`/corporation_sequence_number` em identidade, consultar a fonte, criar schema ou inferir relações.

COL-07 estabelece ainda que uma relação não provada não bloqueia nem multiplica a ingestão base.
A ordem anterior da trilha contrariava essa regra ao fazer a decisão de Fretes depender de
V2-046a. Esta decisão explicita a fronteira correta:

- `V2_046A_NOT_REQUIRED_FOR_BASE_SHADOW`;
- `V2_046A/V2_046B_REQUIRED_ONLY_FOR_RELATIONAL/PARITY_GATES`.

V2-046a e V2-046b continuam abertas. V2-046b é a única dona do crosswalk final Coleta–Frete.

## Decisão

### Identidade, presença e redução

Uma raiz de Frete usa exclusivamente a tuple escopada com `/id` inteiro e type-tagged.
`/corporation_sequence_number` é alias não técnico versionado e ambiguidade zero-para-muitos
bloqueia lookup. Linhas físicas expandidas são deduplicadas por conjunto no mesmo ID escopado e
não criam filho implícito.

Cada atributo relevante preserva `ABSENT`, `NULL` ou `VALUE`, valor bruto, resultado de parse e
proveniência. O frescor de dedupe e promoção é a mesma seleção do primeiro valor presente, tipado e
válido em `cte_created_at → cte_issued_at → criado_em → servico_em`. Valor presente inválido é
preservado e quarantinado sem cair ao próximo campo. `updated_at` não é frescor nem watermark.
Observação mais antiga é auditada e não regride; empate canonicamente idêntico é replay/no-op;
empate divergente é quarentena. Página, ordem, chegada e hash nunca desempatarão.

Os códigos `finished` e `done` publicariam `finalizado`; `canceled` e `cancelled`, `cancelada`,
sempre preservando o código bruto. Terminal persistido não regride a aberto. Status desconhecido
não recebe terminalidade inferida. Payload parcial ou terminal não apaga CT-e, finalizações,
performance, financeiro ou candidatos relacionais já conhecidos.

### Performance 6389 e datas

`/fit_dpn_performance_finished_at` é o campo oficial e `/finished_at` é fallback explícito. A
seleção conserva origem, bruto, tipado e parse state. Datas cujo dia e mês sejam ambos menores ou
iguais a 12 são ambíguas e vão para quarentena; nenhuma preferência US/BR ou timezone do host é
permitida. Um formato só é aceito quando o próprio valor e sua evidência o tornam inequívoco.

### Sidecars, relação e financeiro

Os dez paths de `FREIGHTS_TRANSITIONAL_SIDECAR` ficam em staging independente com proveniência,
uma página mais batch limitado em memória e falha isolada da raiz. `/freight/edges/node/pickItemId`
é apenas candidato Coleta–Frete. Não há join, FK, lookup, cardinalidade ou crosswalk nesta fatia.

CT-e e campos financeiros conservam somente bruto, tipado, presença e proveniência. Moeda,
unidade, precisão, arredondamento e regra aritmética permanecem não resolvidos; nenhum total é
interpretado ou somado sem referência e owner governados.

### Incremental, replay e ausência

A partição lógica é `freights.service_at`, em `America/Sao_Paulo`, com janela interna
`[start,endExclusive)`. A tradução para borda inclusiva do fornecedor precisa de prova antes de
execução operacional. Overlap é limitado e versionado; `scopes.by_updated_at` serve apenas a late
data complementar; watermark avança só por partições publicadas contíguas de `service_at`.
Replay do mesmo conteúdo canônico no mesmo ID escopado é no-op.

Ausência incremental nunca prova remoção. Prune fica `DISABLED`; habilitação futura pertence a
V2-013, depois de caracterização/paridade, snapshot completo independente, duas ausências,
quarentena, guardrails e aceite nominal.

## Consequências

- V2-011a termina como `COMPLETE_LOCAL_DECISION_ONLY`.
- V2-011 permanece aberto até a implementação base em sombra e seus testes.
- A implementação base não depende de V2-046a, mas é proibida de materializar relação.
- Relações e paridade relacional continuam nos gates V2-046a/V2-046b/V2-012b.
- Não há mapper, migration, SQL, runtime, caracterização, bootstrap, publicação ou cutover.

## Alternativas rejeitadas

- Bloquear a decisão ou a ingestão base em V2-046a: contradiz COL-07 e cria ciclo desnecessário.
- Usar alias corporativo como chave: mistura identidade técnica e chave de negócio ambígua.
- Aceitar `updated_at`, chegada ou hash como frescor/desempate: não há contrato que os autorize.
- Reproduzir a heurística de data do legado: altera silenciosamente o significado de datas.
- Carregar todo sidecar em `Map` ou criar join em loop: viola o limite de memória e antecipa o
  crosswalk.
- Inferir moeda ou executar aritmética financeira: não existe regra governada suficiente.

