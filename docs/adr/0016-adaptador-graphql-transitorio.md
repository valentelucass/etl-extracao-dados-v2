# ADR 0016 — Adaptador GraphQL transitório limitado e promoção restrita

- Status: Aceito e implementado offline por V2-024; fatia de Usuários implementada em shadow por V2-033
- Data: 2026-08-31

## Contexto

O legado usa GraphQL para Usuários e como apoio de identidade/paridade em Coletas e Fretes. Esse
comportamento é necessário durante a migração, mas o legado também mistura documentos, paginação,
retry e acumulação em memória. O inventário possui 181 folhas GraphQL; migrá-las integralmente
criaria uma API paralela sem justificativa. V2-041 mantém credenciais e chamadas externas suspensas,
portanto este bloco só pode provar o desenho com fixtures sanitizadas e servidor loopback.

## Decisão

O runtime expõe três operações nomeadas e somente leitura, geradas de uma única árvore de seleções:
`USERS_SNAPSHOT`, `PICKS_TRANSITIONAL_SIDECAR` e `FREIGHTS_TRANSITIONAL_SIDECAR`. Não existe porta
para texto arbitrário, mutation, subscription, introspection, fragment ou alias. As 19 folhas
selecionadas formam um ledger fechado e bidirecional com os documentos; cada folha possui
owner-papel, gate de saída, critério objetivo de remoção e tarefa V2-040. O ledger é um subconjunto
explícito da matriz de portabilidade, nunca uma alegação de que as 181 folhas foram migradas.

Usuários tem página máxima 20. Os dois sidecars aceitam no máximo 100, valor preservado apenas como
limite local comprovado pelo documento legado; ele não é alegado como contrato remoto. Após V2-033,
as quatro folhas de `USERS_SNAPSHOT` são `IMPLEMENTED_IN_SHADOW` e admitem exclusivamente
`SHADOW_UPSERT_ONLY` mediante `ContractPromotionPermit`. As outras 15 folhas dos sidecars de Coletas
e Fretes permanecem `SYNTHETIC_ONLY`/`OBSERVATION_ONLY`. Toda a superfície continua
`publicationBlocked`: o permit de Usuários nunca autoriza sweep, desativação, publicação ou cutover,
e nenhum outro leaf recebe opt-in por essa exceção restrita.

A travessia é serial e guarda somente a página atual, contadores escalares e um detector
probabilístico de cursores de tamanho fixo (1 MiB). Repetição não tem falso negativo; um raro falso
positivo falha fechado e exige replay, sem reter cursor ou crescer com a execução. Existem caps
explícitos de páginas, nodes, bytes, profundidade, paths, requests e itens em voo. Página vazia é
anômala; `hasNextPage=true` exige cursor não vazio e não repetido. `hasNextPage=false` prova somente
o fim local daquela travessia, não completude, snapshot ou cobertura do dataset. Cursor não é
persistido como checkpoint neste bloco.

O transporte aceita apenas HTTP POST para endpoint tipado, documento estático, JSON estrito e
status 200 exato. Corpo de sucesso e erro é limitado; content type deve ser `application/json`.
Retry ocorre somente para I/O, timeout de request, 429 e 5xx exceto 501, sob budgets, jitter,
`Retry-After` limitado e circuito por operação. Cancelamento encerra a chamada em voo, libera o
permit e não faz retry. Falhas locais/probes abandonadas preservam o estado do circuito; resposta
alcançável inválida não é confundida com indisponibilidade.

GraphQL e Data Export compartilham identidade, tenant, policy e a mesma instância de
`EslRequestGovernor`. A factory GraphQL recusa cycle, policy ou capability de cancelamento
divergentes antes de criar workload/I/O e materializa `V2_GRAPHQL_TOKEN` somente no caminho HTTP
autorizado. Fingerprints, eventos e exceções excluem token, endpoint em claro, query, cursor, payload
e identificadores de negócio.

## Consequências

- Usuários pode alimentar current/history em sombra sem ampliar o sidecar nem declarar completude
  remota.
- Coletas/Fretes conservam apenas os campos transitórios necessários à paridade e ao crosswalk;
  joins, dedupe e reconciliação continuam set-based no SQL Server.
- Fixtures exercitam `pickItems` populado/vazio, `cte`, tipos, limites 20/100, drift e paginação sem
  credencial. Evidência externa continua pendente e não é simulada.
- V2-041 continua bloqueando token, rede remota, release, deploy, produção e cutover.

## Alternativas rejeitadas

- **Copiar os cinco documentos/181 folhas do legado:** amplia acoplamento e superfície sem requisito.
- **Aceitar query ou mutation arbitrária:** elimina a fronteira read-only e o fingerprint estático.
- **Acumular nodes/cursors em listas ou sets crescentes:** viola os limites de memória e replay.
- **Tratar página terminal como dataset completo:** transforma protocolo de paginação em evidência de
  negócio inexistente.
- **Liberar publicação, sweep/cutover ou os sidecars com baseline sintético:** confunde teste local
  com contrato observado da origem.
