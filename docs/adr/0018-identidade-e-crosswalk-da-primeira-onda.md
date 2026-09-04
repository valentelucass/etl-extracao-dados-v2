# ADR 0018 — Identidade e crosswalk da primeira onda

- Status: Aceito para V2-009a; persistência e constraints permanecem em V2-009d
- Data: 2026-09-01
- Escopo: Data Export 6908, Data Export 6389 e GraphQL `individual`

## Contexto

O ADR 0008 definiu o framework, mas deixou a matriz por entidade para V2-009. V2-025a passou a
oferecer releases estruturais offline das três fontes e confirmou que `order_by`, hash e chave de
negócio não podem ocupar o lugar da identidade técnica.

O legado continua sendo evidência funcional, não autoridade arquitetural. Nele, Coletas GraphQL
usam `id` textual e `sequence_code` único no banco; Fretes GraphQL usam `id` e permitem minuta
repetida; Usuários persistem `user_id` numérico. Isso não prova que os tokens remotos sejam globais,
que IDs de transportes diferentes sejam iguais ou que a representação GraphQL de Usuários seja
sempre numérica. Em particular, a comparação histórica de uma janela 6908/6389 com GraphQL é
evidência de paridade daquela janela, não criação de um ID canônico.

## Decisão

### Namespace e ID canônico

Cada observação resolve a identidade de origem pela tuple exata e case-sensitive:

`(source_instance, tenant_scope, entity, source_key)`.

- `source_instance` identifica a conta/origem lógica ESL estável e não secreta, compartilhada por
  Data Export e GraphQL quando ambos estiverem habilitados. Transporte não entra na tuple.
- O `source_kind` do catálogo de controle deve representar a família lógica `ESL`, não registrar
  `DATA_EXPORT` e `GRAPHQL` como duas espécies concorrentes sob a mesma instância. A composição
  física será verificada na vertical/V2-009d.
- `tenant_scope` é obrigatório. `GLOBAL`, `SINGLETON` e `DEFAULT` não são substitutos aceitos, pois
  não existe prova formal de unicidade global ou fonte singleton.
- `entity` usa somente `coletas`, `fretes` ou `usuarios`, com esse casing.
- `canonical_id` é um surrogate `BIGINT IDENTITY` alocado pelo SQL Server. Não deriva de
  `source_key`, business key, ordem, hash ou `core.entity_record_state.record_state_id`.

O registry físico será de `core`, aliases versionados serão de `ref`, observações ficam em `stg` e
conflitos/evidências em `recon`. Não será criado um pseudo-schema `crosswalk`. Nenhum desses objetos
é criado neste bloco; DDL, locks, grants, índices, concorrência e replay físico pertencem a V2-009d
junto à vertical dona.

### Matriz ratificada

| Entidade | Release V2-025a | Source key | Business alias | Cardinalidade comprovada |
|---|---|---|---|---|
| `coletas` | Data Export 6908 | `/id`, JSON `INTEGER`, nome `id` | `/sequence_code`, versionado e nunca técnico | a mesma raiz pode repetir em linhas físicas expandidas; 1:1 com o alias apenas nas janelas fechadas observadas, sem garantia global |
| `fretes` | Data Export 6389 | `/id`, JSON `INTEGER`, nome `id` | `/corporation_sequence_number`, versionado e nunca técnico | a raiz pode repetir por expansão; o legado permite 0..N Fretes por minuta, logo resolução por alias com mais de um candidato é ambígua |
| `usuarios` | GraphQL `individual` | `/node/id`, nome `user_id`, JSON `INTEGER` ou `STRING` | ausente; `name` é atributo mutável | um node por edge observado; paginação terminal e snapshot global não estão provados |

`fit_p_m_pck_sequence_code`, `pick_item_id`, sequência, minuta, documento, nome, ordem e hash não
criam relações entre entidades. Crosswalks Coleta–Manifesto–Frete e papéis/tipos de usuário
continuam nos gates V2-046 e das verticais; `Individual`, usuário de cancelamento e usuário de
exclusão não são fundidos por semelhança.

### Codec da source key

A versão `first-wave-identity-v1` preserva o tipo do token antes do staging:

- inteiro JSON vira `INTEGER:<decimal integral canônico>`;
- texto JSON vira `STRING:<texto exato>`;
- `INTEGER:42` e `STRING:42` são identidades diferentes;
- texto não sofre trim ou coerção; branco, espaço ASCII nas bordas, controle, Unicode inválido e
  valor que exceda 256 caracteres já com a tag vão para quarentena;
- nulo, ausente, número fracionário, booleano, objeto ou array vão para quarentena;
- mensagens e `toString()` nunca incluem o valor, a instância ou o tenant.

O codec processa um registro por vez. Java não procura colisões nem mantém `List`, `Map` ou `Set` do
universo; preflights de cardinalidade, dedupe, alias e rekey serão set-based no SQL Server.

### Alias, rekey, replay e quarentena

- Repetição física exata da mesma raiz é replay/no-op, não uma nova entidade.
- Reaparecimento da tuple exata reativa o mesmo `canonical_id`.
- Mudança de source key ou business alias exige evidência aprovada, cria versão/histórico com
  vigência e preserva o `canonical_id`; nunca há repoint ou rewrite silencioso.
- Alias pode ser lookup 0..N. Mais de um canonical candidato bloqueia somente a resolução
  dependente; não invalida por si só uma raiz já identificada por source key.
- Mismatch de scope, chave nula/inválida, colisão de source key, alias ambíguo, cardinalidade
  divergente ou rekey/alteração de alias sem evidência gera reason code fechado em `recon` e bloqueia
  a promoção dependente.
- Completude continua separada de identidade. Nada neste ADR autoriza sweep, desativação por
  ausência ou cutover.

## Consequências

- O catálogo produtivo e sua policy O(1) ficam ligados aos fingerprints V2-025a; replay não pode
  reinterpretar uma chave sob outro codec sem nova versão.
- A nomenclatura de paridade passa a tratar IDs GraphQL/Data Export como `source_id`; comparação
  entre eles não os transforma em `canonical_id`.
- V2-009a pode ser encerrada offline. V2-041 continua bloqueando apenas segredo, rede, sonda, health
  check, release, deploy e cutover; não bloqueia esta decisão.
- Toda alegação de unicidade/cardinalidade permanece limitada às fixtures sintéticas e janelas
  históricas sanitizadas registradas. Nenhuma unicidade global foi inventada.
