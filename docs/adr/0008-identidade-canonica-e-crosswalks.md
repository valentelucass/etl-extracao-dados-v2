# ADR 0008 — Identidade canônica, aliases e crosswalks

- Status: Aceito como framework; constraints por entidade dependem de V2-009/V2-046
- Data: 2026-08-30

## Contexto

O legado mistura IDs técnicos, sequências de negócio, hashes heurísticos, ordenação e relações inferidas. Algumas fontes expandem raiz×filhos e vários grãos ainda não têm unicidade, estabilidade, tenant scope ou cardinalidade provados.

## Decisão

Entidades canônicas usam surrogate `BIGINT IDENTITY`. A identidade de fonte fica em registry único por `(source_instance, tenant_scope, entity, source_key)`.

- `source_instance` é identificador estável e não secreto da origem/conta. Tenant/corporação participa da chave salvo prova formal de chave global.
- Business keys permanecem explícitas e podem ter aliases versionados; nunca substituem source ID por semelhança ou `order_by`.
- Rekey cria alias/histórico com vigência e evidência; não reescreve identidade silenciosamente.
- Chave nula, colisão, alias ambíguo ou cardinalidade divergente vai à quarentena e bloqueia promoção dependente.
- Relações são crosswalks tipados com proveniência. Coleta–Manifesto–Frete, título–documento–Frete e filhos expandidos só recebem FK/unique após V2-009/V2-046 provar grão e cardinalidade.
- Hash pode acelerar no-op/comparação, nunca ser a única prova de identidade.
- Hipóteses atuais permanecem no catálogo de regras; candidatos não provados não viram constraint física.

## Consequências

- IDs de contas GraphQL, `fit_ant_*`, documento fiscal, minuta, `unique_id` e nomes parecidos não são equivalentes por inferência.
- O DDL legado e seus UQs/FKs são evidência; V2-009d aplica apenas constraints ratificadas e testadas.
- A matriz por campo registra source key, business key, alias, filho e relação separadamente.
