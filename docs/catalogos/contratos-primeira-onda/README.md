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

O [release observado de Fretes em24/10](observados/fretes-6389-20261024/manifesto.json) fica separado
do catálogo ativo:114campos/16filtros,115paths de resposta,15paths sempreNULL na janela observada,
com tipos não nulos ainda UNOBSERVED nesses15. Duas travessias cURL e duas Java do dia17/10,
com per25/per20 e terminal vazio, produziram37IDs/37linhas e mapas completos de valores iguais;
os37registros foram aceitos pelo mapper atual. O Java validou um contrato congelado antes das
chamadas, usando GET_WITH_BODY/id asc. Isso qualifica os componentes e a partição observada,
não substitui automaticamente o catálogo sintético, a coorte/admissão Main ou prova de persistência.
Os fingerprints e o hash do arquivo ficam no manifesto observado; valores/IDs de negócio não.

A regressão do checkpoint0558 usou o pacote novo e carregou BODY pela configuração oficial do V2:
per50 no mesmo dia chegou a37IDs/37linhas e à página2 vazia, sem timeout e sem quarentena.
Isso confirma o funcionamento desta rota; o timeout histórico continua preservado e não se
declara que toda intermitência da origem tenha sido eliminada.

O checkpoint0560 integra a escolha explícita desse release ao pedido principal pela propriedade
`sourceContractVersion=2026-10-24.fretes-observed-curl.v1`, com BODY/uma tentativa e fora do
perfil laboratorial. A seleção e os vínculos passaram nos testes locais; sem a propriedade, vale
o comportamento anterior. O recurso próprio do JAR é idêntico ao release observado publicado.
Essa capacidade de seleção não constitui admissão Main, gravação SQL ou adoção operacional.

`PROVEN` sempre deve ser lido com o `scope` da mesma linha. Por exemplo, o runtime prova localmente
o limite de 20 nodes de Usuários e a paginação defensiva; isso não prova terminal remoto ou dataset
completo. Uma página vazia Data Export e `hasNextPage=false` provam apenas o término protocolar da
travessia local.

## Decisões fixadas

- `6908`: `picks.request_date`, `scopes.by_updated_at` como sibling complementar,
  `sequence_code asc` e `id` como audit/source key. O nome histórico de metadata `request_date` é
  deliberadamente distinto do path de query.
- `6389`: `freights.service_at`, `scopes.by_updated_at` apenas complementar,
  `id asc` e `id` como audit/source key, na revisão semântica `2026-10-24.fretes-id.v1`.
  A sequência corporativa continua business alias, nunca identidade técnica. `finished_at` e
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

A revisão de ordem de Fretes se apoia nas consultas reais dos checkpoints0551/0552:
ID asc funcionou em corpo/query, per1/10/100 e duas datas; uma travessia per25 chegou
a37fretes e página vazia. Uma segunda passagem per50 também sofreu timeout, portanto
a nova ordem não elimina instabilidade nem prova snapshot ou completude global.
A baseline de metadata/resposta permanece sintética; os114campos observados não foram
promovidos automaticamente a esse release. Classificações/gates de completude continuam.
O [manifesto anterior](historico/manifesto-2026-09-01.v2-025a.3.json) está preservado
byte a byte; seus pins não autorizam a revisão nova. Demais templates conservam seus contratos.

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
