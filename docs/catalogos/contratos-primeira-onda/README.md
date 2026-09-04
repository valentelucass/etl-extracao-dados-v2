# Contratos da primeira onda e Usuários

Este diretório é a baseline offline de V2-025a para Data Export `6908`, Data Export `6389` e
GraphQL `individual`. O [manifesto](manifesto.json) separa três conceitos que não podem ser
fundidos:

- a classificação do aspecto (`PROVEN`, `TRANSITIONAL`, `ABSENT` ou
  `BUSINESS_DECISION_PENDING`);
- a proveniência da evidência, que pode ser código/fixture local, observação histórica sanitizada,
  evidência estática do legado ou ausência de oráculo independente;
- o efeito permitido. O gate de completude não impede que registros efetivamente observados
  avancem aos gates contratuais e de entidade para upsert em sombra; ele não substitui esses gates.
  Sweep, desativação por ausência e cutover ficam `BLOCKED_NO_COMPLETENESS_PROOF`.

`PROVEN` sempre deve ser lido com o `scope` da mesma linha. Por exemplo, o runtime prova localmente
o limite de 20 nodes de Usuários e a paginação defensiva; isso não prova terminal remoto ou dataset
completo. Uma página vazia Data Export e `hasNextPage=false` provam apenas o término protocolar da
travessia local.

## Decisões fixadas

- `6908`: `picks.request_date`, `scopes.by_updated_at` como sibling complementar,
  `sequence_code asc` e `id` como audit/source key. O nome histórico de metadata `request_date` é
  deliberadamente distinto do path de query.
- `6389`: `freights.service_at`, `scopes.by_updated_at` apenas complementar,
  `corporation_sequence_number asc` e `id` como audit/source key. A sequência corporativa continua
  business key/ordenação, nunca identidade técnica. `finished_at` e
  `fit_dpn_performance_finished_at` permanecem no contrato sintético porque são os campos úteis do
  sidecar legado; data ambígua não recebe heurística e deverá ser quarentenada.
- `individual`: query estática `id/name/pageInfo`, `enabled=true`, no máximo 20 nodes, cursor somente
  dentro da execução e restart seguro desde a página 1. A baseline transitória aceita `id` JSON
  integral ou textual não vazio: o `Long` legado prova o destino interno, mas não distingue token
  numérico de texto coercível. V2-009a fechou a canonicalização conservadora com tags de tipo,
  registry escopado e canonical ID surrogate no
  [catálogo de identidade](../identidade-primeira-onda/README.md); unicidade/estabilidade globais
  continuam não provadas. Não há `updatedAt`, filtro temporal, ordenação, template Data Export
  oficial nem autorização para inferir `9901`.

A tradução do intervalo interno `[start,endExclusive)` para as bordas inclusivas atualmente
representadas pelo cliente Data Export continua `BUSINESS_DECISION_PENDING`. A baseline não subtrai
dia ou segundo por inferência e não transforma `by_updated_at` em watermark.

## Integridade e revalidação

Cada fixture possui SHA-256 e cada contrato possui fingerprints independentes de semântica,
metadata, resposta e release. Os testes Java recomputam os releases por meio do gate V2-044; o
validador offline confere vocabulário, cobertura das dimensões, hashes, paths e bloqueios:

```powershell
pwsh -NoProfile -File .\scripts\validation\Test-FirstWaveContractCatalog.ps1
```

As fixtures contêm somente valores artificiais e limitados. As duas travessias Data Export usam
`per=2` e `per=3`, preservam o mesmo conjunto sintético de três IDs e demonstram expansão física sem
acumulação produtiva. Usuários tem primeira página e página terminal, inclusive `name` nulo/ausente.

V2-025d é outro subbloco: só poderá substituir evidência `TRANSITIONAL` por uma baseline externa
atual depois da liberação de V2-041 e de autorização específica de janela/teto. Este catálogo não
autoriza rede, credencial, deploy, sweep ou cutover.
