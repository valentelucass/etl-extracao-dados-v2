# Identidade e grão — Data Export 6392 (Sinistros)

Este catálogo registra a tentativa offline `V2-009b/6392`. Ele não simplifica o hash legado e não
executa `V2-032`, relações, reducer, mapper, schema, banco ou rede.

## Resultado: `UNRESOLVED_ROOT_SOURCE_KEY_VERSUS_LEGACY_COMPOSITE_GRAIN`

`/sequence_code` inteiro é obrigatório no mapper e candidato de raiz, mas não pode ser aceito como
source key definitiva: a V1 persiste por hash de sequência, ocorrência de invoice e minuta. A
janela sanitizada 2:2 não testa a colisão que permitiria simplificar esse grão. O futuro
`canonical_id` continua surrogate `BIGINT IDENTITY`, sem binding enquanto a source key permanecer
indefinida; o hash, a sequência e `record_state_id` não são canonical keys.

`/icm_fis_fit_corporation_sequence_number` e `/icm_fis_ioe_number` são somente componentes
relacionais candidatos. Não há alias, rekey ou filho aprovado; nenhum array foi observado. A
coocorrência dos campos escalares não prova relação nem cardinalidade raiz–minuta–ocorrência.

## Replay, colisão e limite da prova

Sem grão aceito, repetição de `sequence_code` não pode ser classificada como replay canônico. As
observações são preservadas, sem colapso pelo hash; divergência e rekey ficam em quarentena e
bloqueiam promoção. A fixture é artificial e não prova unicidade global, estabilidade temporal,
tenant, papel dos componentes, cardinalidade, completude, sweep ou cutover.

P11 requer garantia versionada da raiz ou observações representativas repetidas com análise de
colisão entre `sequence_code`, minuta e ocorrência de invoice, além de papéis versionados dos
componentes e cardinalidade representativa raiz–componentes.

`V2-025d` permanece bloqueada pelo hold externo de `V2-041`.

## Revalidação

```powershell
pwsh -NoProfile -File .\scripts\validation\Test-DataExport6392IdentityCatalog.ps1
```
