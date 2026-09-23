# Identidade e grão — Data Export 4924 (Faturas por Cliente)

Este catálogo registra a tentativa offline `V2-009b/4924`. Ele não executa `V2-030`, não escolhe
precedência fiscal e não cria crosswalk, mapper, schema ou acesso externo.

## Resultado: `UNRESOLVED_LOGICAL_TITLE_AND_CROSSWALKS`

`/id` inteiro é aceito somente como source key da linha física `/data/*`, escopada por
`source_instance` e `tenant_scope`, e recebe binding canônico apenas nesse grão de linha. Ele não
vincula a raiz lógica de título. O futuro `canonical_id` é surrogate `BIGINT IDENTITY`; nunca
`/id`, `unique_id`, `FPC-HASH-*`, documento ou
ordenação. `fit_ant_document`, NFS-e, CT-e e `billingId` são candidatos de negócio/heurística, não
aliases, source keys ou rekeys aprovados.

Os únicos filhos físicos visíveis são elementos de `/invoices_mapping/*` e
`/fit_fte_invoices_order_number/*`; identidade, semântica relacional e cardinalidade permanecem
desconhecidas. Coocorrência de título, documento ou total na linha não prova relação com Frete.
O histórico parcial mostra multiplicidade de linhas por título e CT-es distintos; serve somente
como contraexemplo a título=linha, não como cardinalidade universal. As prioridades e os hashes
legados são conflitantes e não constituem crosswalk.

## Replay, colisão e limite da prova

Replay exato do mesmo `/id` escopado pode ser no-op da linha; divergência bloqueia até a regra de
frescor. Mudança de título/documento ou `/id` nunca reponta o canonical automaticamente. Os três
IDs da fixture sintética `per=3` não provam estabilidade temporal, unicidade global, crosswalks,
tenant, filhos, completude, sweep ou cutover.

P10 exige estabilidade versionada do ID, crosswalk representativo linha–título–documento–Frete,
identidade/cardinalidade dos filhos e regra de alias/rekey fiscal aceita.

`V2-025d` permanece bloqueada pelo hold externo de `V2-041`.

## Revalidação

```powershell
pwsh -NoProfile -File .\scripts\validation\Test-DataExport4924IdentityCatalog.ps1
```
