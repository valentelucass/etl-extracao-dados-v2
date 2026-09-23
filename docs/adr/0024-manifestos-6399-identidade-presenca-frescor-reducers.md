# ADR 0024 — Manifestos 6399: identidade, presença, frescor e reducers

- Status: Aceito somente para a decisão V03 de V2-026a; execução permanece não iniciada em V04
- Data: 2026-09-04
- Escopo: decisão local conservadora de MAN-01, MAN-02, MAN-04 e MAN-07, sem DDL ou runtime

## Contexto

O contrato offline `dataexport-6399` versão `2026-09-04.v2-025b.2`, release
`30181093f00f47507d1910d9806a97697118ab671aeae0598d21a4a2726a7cb8`, a reauditoria P01 e a
caracterização estática versionada do
legado permitem separar a raiz Manifesto das expansões de pick e MDF-e. A prova é delimitada: não
é fingerprint atual do fornecedor, garantia de estabilidade temporal, snapshot completo ou prova
de relação com Coleta.

O corpus histórico caracterizado possui 228 linhas físicas para 100 raízes. Foram observadas 193
chaves escopadas de pick e 43 chaves escopadas de MDF-e. Os máximos observados são dez picks e dois
MDF-es distintos por raiz, mas esses números não viram limites do contrato: ambas as
cardinalidades são `0..N`.

O legado não pode ser copiado como regra. O DTO elimina a diferença entre campo ausente e nulo; o
mapper converte erros de parse em nulo ou apenas registra log; a deduplicação usa somente
`finished_at/created_at`, score de métricas, zero como ausência e complemento vindo de versão
velha; a promoção usa outra precedência, aceita empate por chegada e sobrescreve a linha. A tabela
mistura raiz e filhos, e a procedure de fato aplica `MAX`, `SUM` e `STRING_AGG` sobre a expansão,
podendo sintetizar combinações que nunca existiram numa observação física.

As views Power BI/dash, a tabela física do fato e os validators legados 032, 037, 044 e 045
confirmam somente mecânicas de consumo, órfãos, presença não zero, capacidade agregada e uma linha
por `sequence_code`. Eles não provam identidade natural de filho, tri-state, reducer, frescor,
colisão ou cardinalidade do fornecedor e são tratados exclusivamente como contraevidência/limite.

## Decisão

### Identidade e grãos

A raiz lógica usa exclusivamente a tuple:

`(source_instance, tenant_scope, entity=manifestos, INTEGER:<sequence_code>)`.

`source_instance` e tenant são configuração explícita. Ordem, hash, número MDF-e isolado,
coocorrência e identificadores derivados do legado nunca substituem a source key ou o surrogate
canônico.

Pick é filho da raiz por:

`(root_canonical_id, INTEGER:<mft_pfs_pck_sequence_code>)`.

O valor também é preservado como candidato de relação Manifesto→Coleta para V2-046a, sem lookup,
FK, escolha `TOP 1`, normalização de órfão ou relação materializada.

MDF-e é filho da raiz por:

`(root_canonical_id, STRING:<mft_mfs_key>)`.

A chave aceita no limite P01 possui exatamente 44 dígitos ASCII. `mft_mfs_number` é atributo
inteiro positivo correlato e nunca identidade. Número e chave ocorreram juntos em 80 linhas do
corpus, sem assimetria; número sem chave ou chave sem número em observação futura é preservado e
quarentenado, sem filho inventado. Ao reduzir MDF-e, número e chave permanecem ligados à mesma
linha física; não existe `MAX` independente que possa criar um par sintético.

`mdfe_status` não integra a identidade nem o par físico obrigatório do filho. Ele está presente
nas 228 linhas, inclusive em 148 linhas sem chave MDF-e, possui dois valores observados e não
diverge em nenhuma das 100 raízes. Portanto é escalar da raiz replicado pela expansão. Quando uma
chave MDF-e válida existe, somente sua proveniência bruta pode acompanhar o contexto da
observação do filho; ela nunca vira atributo canônico ou lifecycle do MDF-e. Sem chave, o status
continua sendo reduzido na raiz e jamais cria ou quarentena um filho por si só.

Coocorrência também não prova relação: 59 linhas possuem pick e chave MDF-e, 134 somente pick, 21
somente chave e 14 nenhum dos dois. Filhos distintos na mesma coorte são preservados separadamente
e não constituem divergência da raiz.

### Presença e parse

Cada folha conserva `source_path`, presença, valor bruto, valor tipado, estado do parse e
proveniência. O vocabulário é fechado:

- `ABSENT`: o path não ocorreu e não faz afirmação sobre o valor;
- `NULL`: o path ocorreu com nulo explícito;
- `VALUE`: o path ocorreu com valor, cujo parse precisa ser válido para promoção.

Valor presente inválido preserva o bruto e entra em quarentena; ele nunca é convertido em
`NULL`. A saída da coorte corrente preserva `ABSENT` como ausência, `NULL` como nulo explícito e
`VALUE` como valor tipado. Estado de coorte anterior fica somente no histórico/audit e nunca
completa a coorte vencedora.

### Frescor único, replay e empate

Dedupe e promoção usam a mesma ordem:

1. `finished_at`;
2. `closed_at`;
3. `departured_at`;
4. `created_at`.

Todos os temporais presentes precisam validar. `ABSENT/NULL` permite seguir ao próximo path;
valor presente inválido bloqueia a observação e não autoriza fallback. O primeiro valor válido da
ordem é normalizado para instante UTC, preservando bruto e offset original. Timestamp local sem
offset é inválido enquanto não existir contrato versionado de timezone.

Sem frescor válido, a observação é preservada fora de promoção. Observação mais velha não regride
estado. Em empate, primeiro são executados os reducers explícitos; resultado canônico idêntico é
replay/no-op e divergência residual vira `EQUAL_FRESHNESS_CONFLICT`. Chegada, página, ordem e hash
não desempatarão. Hash pode apenas antecipar uma comparação canônica exata.

Raiz, cada pick e cada MDF-e formam suas próprias coortes. O filho herda o frescor da observação
da raiz como contexto de ingestão, sem alegar que ele representa um evento do ciclo de vida do
pick ou MDF-e.

### Reducer comum, status e MAN-07

Reducers atuam somente dentro da coorte de maior frescor; uma versão anterior nunca complementa a
coorte vencedora.

O reducer comum de campo raiz ignora `ABSENT`. Somente ausentes permanece `ABSENT`; nulos com
ausentes, sem valor, resulta em `NULL`; um único valor canônico repetido, sem nulo, resulta nesse
valor. `NULL+VALUE`, valores canônicos distintos ou parse inválido bloqueiam promoção e entram em
quarentena. Não há `SUM`, `MAX` genérico ou keep-last.

Para `/status`, valores conhecidos na mesma coorte usam `closed > in_transit > pending`. Um único
desconhecido repetido é preservado bruto, sem terminalidade inferida. Mistura de conhecido com
desconhecido, desconhecidos distintos ou `NULL+VALUE` é conflito. Um status de coorte estritamente
mais nova vence o antigo; o corpus não prova que `closed` seja irreversível.

`/mdfe_status` usa o reducer comum da raiz, sem catálogo semântico inventado e sem funcionar como
sinal de filho.

MAN-07 congela oito atributos lógicos:

| Atributo lógico | Path |
| --- | --- |
| `km` | `/km` |
| `totalCost` | `/total_cost` |
| `manifestFreightsTotal` | `/manifest_freights_total` |
| `totalTaxedWeight` | `/total_taxed_weight` |
| `vehicleWeightCapacity` | `/mft_vie_weight_capacity` |
| `capacidadeKg` | `/mft_vie_weight_capacity` |
| `manifestItemsCount` | `/manifest_items_count` |
| `finalizedManifestItemsCount` | `/finalized_manifest_items_count` |

Para essas métricas, `NULL/ABSENT` pode ser complementado por um único `VALUE` canônico dentro da
mesma coorte, registrando `COMPLEMENTED_FROM_EXPANSION`. Zero é valor real. Zero versus não zero ou
quaisquer dois valores distintos vira `METRIC_VALUE_CONFLICT`; não há soma, máximo ou score.
`capacidadeKg` é apenas projeção compatível do mesmo valor reduzido de
`mft_vie_weight_capacity`; ela nunca pode divergir de `vehicleWeightCapacity`.

### Competência

Competência usa `/departured_at` tipado válido. Somente quando ele estiver `ABSENT/NULL`, usa
`/created_at`. Valor de saída presente e inválido bloqueia fallback; fallback presente e inválido
também entra em quarentena. Sem valor nos dois paths, competência é `UNKNOWN` e bloqueia o
consumidor posterior. Path, bruto, origem do fallback e instante UTC são preservados. Tempo de
extração, `MAX`, fechamento e finalização não substituem essa regra; bucketing por data civil
aguarda contrato de timezone.

### Relações e ausência

O candidato Manifesto→Coleta é append-only, com presença e proveniência, e pertence à resolução
V2-046a. Coocorrência de pick, MDF-e e raiz não cria relação.

Raiz ou filho não observado numa página ou janela não é evidência negativa. Página vazia encerra
paginação, mas não aciona sweep. Sweep, delete, soft delete, desativação e publicação permanecem
bloqueados até V2-013 e prova independente de snapshot completo.

## Consequências

- V03 fecha apenas como `COMPLETE_LOCAL_DECISION_ONLY`.
- V2-026 pai permanece aberto; V04 é o único dono da futura execução e está `NOT_STARTED`.
- V04 poderá implementar mapper, staging/core, migration e testes, mas não poderá materializar a
  relação Manifesto→Coleta nem ativar ausência por inferência.
- A matriz V2-017a deve classificar somente pick, chave e número MDF-e como linhas de filho;
  `mdfe_status` é raiz replicada. Campos de nome contendo `pick` ou `mdfe` não são filhos por regex.
- Os 91 slots `/info` sem nome persistido continuam bloqueio contratual de execução/publicação,
  não autorização para inventar paths.

## Limites factuais

Esta decisão não prova schema atual do fornecedor, unicidade global, estabilidade temporal,
tenant no payload, máximos de cardinalidade, semântica de ciclo de vida do MDF-e, relação com
Coleta, completude, snapshot, timezone civil, publicação ou correção de runtime.

Nenhuma rede, credencial, banco, payload novo, Java, migration, schema, tabela, procedure, view,
fato, grant, integração, deploy ou runtime é criado por este ADR.

## Alternativas rejeitadas

- **Usar hash, ordem ou chegada como identidade/desempate:** não há prova natural e o resultado
  depende do transporte.
- **Usar número MDF-e como identidade:** ele é apenas atributo correlato da chave.
- **Tratar `mdfe_status` como bundle obrigatório do filho:** 148 linhas têm status sem chave e o
  status é invariável por raiz no corpus.
- **Reduzir número, chave e status por `MAX` independente:** pode criar combinação inexistente.
- **Completar métricas a partir de versão velha ou tratar zero como ausência:** altera o sentido da
  coorte vencedora e perde valores reais.
- **Materializar Manifesto→Coleta pelo pick:** antecipa V2-046a sem prova relacional.
- **Converter ausência incremental em exclusão:** confunde janela parcial com snapshot completo.

## Revalidação

```powershell
pwsh -NoProfile -File .\scripts\validation\Test-ManifestosV2026DecisionCatalog.ps1
```
