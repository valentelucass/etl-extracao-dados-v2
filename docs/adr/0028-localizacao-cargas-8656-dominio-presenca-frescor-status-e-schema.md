# ADR 0028 — Localização de Cargas 8656: identidade, presença, frescor, status e schema lógico

- Status: aceito somente para V2-028a; implementação permanece em V10/V2-028
- Data: 2026-09-06
- Escopo: decisão offline e fail-closed de LOC-01–LOC-07

## Contexto

O contrato `dataexport-8656` V2-025b registra uma observação sanitizada limitada e não prova
completude, tipos atuais do fornecedor, estabilidade temporal ou relação com Fretes. A identidade
P07/V2-009b aceita somente `/corporation_sequence_number` inteiro na tupla escopada como chave da
raiz lógica; `sequence_number` é apenas nome histórico de ordenação e foi explicitamente rejeitado
como chave ou alias.

Esta decisão transforma LOC-01–LOC-07 em regras locais implementáveis sem ampliar a evidência da
origem. Os casos do catálogo são sintéticos e não constituem contrato de fonte.

## Decisão

### LOC-01 — identidade e escopo

A raiz usa exclusivamente `(source_instance, tenant_scope, entity, source_key)`, com
`/corporation_sequence_number` de wire type `INTEGER`. `source_instance` e `tenant_scope` são
configuração externa explícita; `DEFAULT`, `GLOBAL` e `SINGLETON` são inválidos. O identificador
canônico interno será surrogate `BIGINT IDENTITY`. `/sequence_number` nunca é chave, alias,
fallback ou rekey.

### LOC-02 — presença e ausência

Cada atributo conserva valor bruto, valor tipado, estado de parse, path, presença e proveniência.
O vocabulário é `ABSENT/NULL/VALUE`: `ABSENT` preserva o valor conhecido; `NULL` só limpa em uma
observação mais nova aceita; `VALUE` só aplica o valor tipado com sua evidência. Ausência em janela
incremental não muda estado ativo. Completude não foi provada, portanto sweep permanece desligado.

### LOC-03 — volume e candidato de Fretes

A semântica downstream é exatamente `COALESCE(volumes_localizacao, volumes_fretes, 0)`. Zero em
Localização é valor real e não aciona fallback. O volume de Fretes é apenas candidato futuro pela
igualdade exata do alias tipado `/corporation_sequence_number` no mesmo source instance e tenant.
Zero ou múltiplos candidatos bloqueiam a resolução; não existe escolha por primeiro resultado.

Nesta fatia são preservados somente valor candidato, presença e proveniência. FK, join, crosswalk
final, `TOP 1`, nome aproximado e publicação estão proibidos. V2-046a e V2-046b continuam abertas;
esta ADR não atribui artificialmente a elas um crosswalk que o roadmap ainda não ratificou.

### LOC-04 — números

`/invoices_volumes` usa envelope lógico inteiro não negativo de 32 bits, léxico ASCII sem sinal,
agrupamento ou espaços. `/taxed_weight`, `/invoices_value` e `/total` usam envelope lógico
`DECIMAL(38,9)`, léxico ASCII com ponto decimal opcional, sem agrupamento ou espaços. O envelope é
uma decisão interna conservadora para a futura staging, não uma alegação de tipo do fornecedor.

Valor inválido, overflow, escala maior que nove ou representação de locale ambígua é preservado
bruto e quarantinado; nunca vira zero ou nulo silencioso. Não há inferência de moeda, unidade,
precisão comercial ou arredondamento, e nenhuma aritmética é autorizada.

### LOC-05 — frescor, empate e temporalidade

Somente `/service_at` presente, tipado e válido governa dedupe e promoção. `data_extracao`,
timestamp de chegada, hash, página e ordem física jamais são frescor ou desempate. Valor inválido
é preservado e quarantinado. Observação antiga é auditada e não regride. Mesmo `service_at` com
conteúdo canônico idêntico é replay/no-op; com conteúdo divergente, quarentena. Dois `service_at`
nulos idênticos são replay/no-op; divergentes vão à quarentena, nunca a keep-last.

A partição lógica é `service_at`, em `America/Sao_Paulo`, com janela interna
`[start,endExclusive)`. A tradução às bordas civis inclusivas da origem permanece não provada e
bloqueia execução operacional. Overlap deve ser limitado e versionado na implementação V10.

### LOC-06 — status

`/fit_fln_status` conserva bruto e normaliza com trim seguido de lowercase invariante. Nulo ou
branco resulta em `sem_status`. Somente `finished`, `delivered`, `canceled` e `cancelled` são
terminais nesta versão do catálogo. Qualquer outro código, inclusive semelhante, é preservado
como desconhecido e não terminal; nenhuma publicação ou label de consumidor nasce nesta fatia.

### LOC-07 — campo sem fonte

`status_branch_nickname` permanece `ABSENT` com proveniência `UNSOURCED_LEGACY`. Os campos
`fit_crn_psn_nickname`, `fit_dyn_drt_nickname`, `fit_fln_cln_nickname` e
`fit_o_n_drt_nickname` não são fallback. Apenas um source path versionado futuro ou a retirada
aceita pelo consumidor pode resolver o campo.

### Limites históricos

Os números legados `per=10000`, timeout de 90 segundos, teto de 1.200 páginas e 150.000 linhas
são somente candidatos históricos e não defaults da V2. O contrato atual tampouco autoriza
completude, sweep ou cutover.

## Consequências

- V2-028a termina como `COMPLETE_LOCAL_DECISION_ONLY`.
- V2-028 continua aberta até a implementação V10 e seus testes em shadow.
- A implementação deve separar presença, parsing, frescor, candidato relacional e quarentena.
- Relação, paridade, publicação, caracterização, bootstrap, sweep e cutover ficam nos gates próprios.
- Este bloco não cria Java produtivo, mapper, migration, SQL, conexão a banco ou acesso à fonte.

## Alternativas rejeitadas

- usar `sequence_number` ou nickname como identidade/fallback;
- repetir keep-last, desempatar por extração/chegada/hash/página ou regredir por out-of-order;
- transformar erro numérico em zero/nulo ou adivinhar locale, moeda, unidade e arredondamento;
- escolher um Frete ambíguo com `TOP 1`, nome aproximado ou materializar relação;
- ativar sweep/publicação sem completude, paridade e contrato consumidor.

