# ADR 0023 — Coletas 6908: domínio, presença, frescor, status e schema

- Status: Aceito para a decisão V01 de V2-010; execução permanece em V02
- Data: 2026-09-04
- Escopo: semântica de domínio em sombra para a raiz lógica `coletas`

## Contexto

O contrato `dataexport-6908` e o ADR 0018 permitem identificar uma observação por
`(source_instance, tenant_scope, entity=coletas, source_key=id)` e preservam
`sequence_code` como alias de negócio. Eles não autorizam inferir identidade global, filho,
relação, completude ou publicação. A V2 ainda não possui mapper, staging, promoção, migration ou
testes da vertical de Coletas; esses artefatos pertencem à fatia V02.

## Decisão

### Grão, identidade e schema lógico

Uma Coleta é uma raiz lógica identificada exclusivamente pela tuple do ADR 0018, cujo `id` 6908 é
inteiro e vira source key type-tagged. `sequence_code` é alias de negócio versionado e nunca
substitui a source key ou o surrogate canônico. Expansão física de linhas de uma mesma raiz é
deduplicada na promoção set-based futura; ela não cria duas Coletas nem um filho implícito.

O schema lógico que V02 deverá materializar, em tabelas de sombra novas e versionadas, separa:

1. identidade escopada e `canonical_id` do registry;
2. alias `sequence_code` com vigência/proveniência;
3. payload bruto preservado, campos tipados e presença tri-state por atributo (`ABSENT`, `NULL`,
   `VALUE`);
4. `status_raw`, `status_code`, `status_label`, `status_catalog_version` e `terminal`;
5. `freshness_raw`, `freshness_at_utc`, `freshness_origin` e resultado de parse;
6. evidência de presença por execução/snapshot e estado de ausência;
7. candidatos de relação como valor, presença e proveniência, sem FK canônica.

Esses são nomes lógicos, não instrução de DDL, constraint, índice, default, grant, view `pub` ou
coluna legada. V02 decide o desenho físico somente dentro do banco local de sombra e com migration
nova, manifesto, rollback e testes próprios; não copia migrations ou views V1.

### Frescor e redução

`status_updated_at` é convertido em `status_updated_at_em` tipado e é a fonte preferida de
frescor. Se ausente ou inválido, a ordem de fallback é `finish_date`, `service_date`,
`request_date`, usando somente valor tipado válido; o valor original inválido nunca é apagado ou
reinterpretado. A origem do vencedor é armazenada. Timestamp por si só não reabre uma Coleta
terminal: estado terminal observado vence estado aberto mesmo retrocedendo no tempo, e terminal
persistido não regride para aberto.

### Status e derivados

O catálogo `coletas-status-v1` é fechado nesta fatia:

| Código de origem | Label | Terminal |
| --- | --- | --- |
| `pending` | Pendente | não |
| `treatment` | Em tratativa | não |
| `manifested` | Manifestada | não |
| `in_transit` | Em trânsito | não |
| `draft` | Rascunho | não |
| `finished` | Finalizada | sim |
| `done` | Coletada | sim |
| `canceled`, `cancelled` | Cancelada | sim |

O derivado de ação respeita estritamente: `finished`/`done` gera `Coleta Realizada`; caso
contrário, motivo de cancelamento não vazio vence; depois `canceled`/`cancelled` gera `Coleta
cancelada`; no restante, `Pendente`. Tentativas é `1` para qualquer terminal e `0` para aberto.
Código bruto, código canônico, label versionada e terminal são campos distintos. Código fora do
catálogo preserva o bruto, não ganha equivalência inventada e não altera terminalidade sem nova
versão/fixture/decisão.

### Presença, ausência, relações e incremental

Presença de campo e presença da raiz são separadas. A ausência da raiz só poderá ser avaliada após
snapshot completo e independente comprovado. Quando V2-013 autorizar esse mecanismo, a primeira
ausência será candidata e compatibilizada como `Excluída`; a segunda confirmação independente
fará soft delete; reaparecimento limpará integralmente o estado de ausência. Até lá, V02 armazena
somente evidência de observação e mantém sweep/desativação/publicação de ausência desligados.
Snapshot incompleto, página vazia anômala, cap, chave nula ou erro nunca altera ativo.

Campos de Manifesto, item, `pick_item_id`, `fit_p_m_pck_sequence_code` e Frete são preservados
somente como candidatos com presença e proveniência. Manifesto→Coleta depende de V2-046a;
Coleta→Frete depende de V2-046b. Nenhum join, FK, cardinalidade ou multiplicação da raiz é
inferido por esta decisão.

O incremental é por partição concluída de `request_date` com overlap versionado. A busca em
`scopes.by_updated_at` é complementar para late data e nunca watermark/checkpoint. Guardrails
históricos (90 dias, lotes e percentuais) continuam candidatos sem default até medição, owner e
testes aprovados. Região continua governada: CEP válido primeiro, depois cidade/UF; a ausência de
referência produtiva bloqueia publicação/cutover, não o modelo sintético em sombra.

## Consequências

- V02 recebe limites de decisão fechados para implementar mapper, staging, promoção, migration e
  testes sem reabrir identidade, ausência, status ou relações.
- V02 deve criar fixtures de reducer e presença, incluindo timestamp inválido, terminal
  retroativo, duas confirmações isoladas e reaparecimento; esse último permanece não ativável até
  V2-013.
- Não há fonte, credencial, rede, DDL, banco, publish, sweep, cutover ou decisão de relação neste
  documento.

## Alternativas rejeitadas

- **Usar `sequence_code` como chave técnica:** conflita com o ADR 0018 e mistura alias com
  identidade.
- **Deixar status terminal regredir pelo maior timestamp:** perde a semântica operacional
  observada.
- **Converter ausência incremental em exclusão:** confunde janela parcial com snapshot completo.
- **Criar FK de Manifesto/Frete agora:** antecipa V2-046a/V2-046b e pode multiplicar o grão.
- **Copiar tabela, defaults ou views V1:** transforma evidência histórica em schema V2 sem
  migration, contrato físico ou prova de workload.
