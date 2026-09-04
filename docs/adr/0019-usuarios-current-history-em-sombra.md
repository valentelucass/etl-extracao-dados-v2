# ADR 0019 — Usuários current/history em sombra

- Status: Aceito para V2-033; publicação externa e cutover permanecem bloqueados
- Data: 2026-09-01
- Escopo: GraphQL `individual(enabled=true)`, staging tipado e `core.usuario`/`core.usuario_history`

## Contexto

Os ADRs [0016](0016-adaptador-graphql-transitorio.md),
[0017](0017-contratos-primeira-onda-e-completude.md) e
[0018](0018-identidade-e-crosswalk-da-primeira-onda.md) fixaram, respectivamente, o adaptador
GraphQL limitado, o gate de completude e a identidade type-tagged da primeira onda. Eles não
definiram como uma observação válida de Usuários se torna estado atual e histórico.

O comportamento útil do legado é manter uma linha atual por usuário, não criar histórico em replay
sem mudança e reativar o mesmo usuário quando ele reaparece. O legado não prova, porém, snapshot
global, incremental temporal, validade de cursor entre processos ou template Data Export de
Usuários. O número `9901` é somente identificador histórico de auditoria; não autoriza endpoint nem
contrato. A query ratificada pede apenas `id` e `name`, além de `pageInfo`; `updatedAt` não é pedido
nem inferido.

A migration [V007](../../database/migrations/V007__create_usuarios_current_history.sql) implementa
a fatia local de V2-033 sobre os kernels comuns. Esta decisão delimita o efeito permitido para que a
existência de current/history não seja confundida com publicação produtiva ou prova de ausência.

## Decisão

### Efeito permitido

Usuários opera exclusivamente em `SHADOW_UPSERT_ONLY`. Registros observados podem ser validados,
stageados, reconciliados e aplicados ao current/history de sombra. O estado `PUBLISHED` do control
plane e o nome do wrapper SQL representam o commit atômico interno da execução; não criam objeto no
schema `pub`, não tornam o V2 fonte produtiva e não autorizam consumidor, deploy ou cutover.

Não há nesta vertical:

- view ou contrato dimensional publicado;
- sweep, prune ou desativação por ausência;
- incremental baseado em tempo da origem;
- composição produtiva, chamada externa ou fallback Data Export;
- relação materializada com Coletas capaz de alterar o grão de Usuários.

### Travessia GraphQL limitada

A única operação de Usuários é o documento estático e read-only `individual(enabled=true)`, com
`id`, `name`, `pageInfo.hasNextPage` e `pageInfo.endCursor`. Cada pedido usa no máximo 20 nodes. A
travessia é serial e stageia sincronamente uma página por microbatch; a JVM mantém somente a página
atual, contadores escalares e o detector de ciclo de tamanho fixo, nunca o universo de Usuários.

Toda execução começa sem cursor. `endCursor` existe somente para avançar dentro da mesma travessia
e não atravessa auditoria, processo, retry ou replay. `hasNextPage=false` produz a classificação
`LOCAL_PAGE_INFO_TERMINAL_UNVERIFIED`: encerra aquela travessia, mas não prova cobertura,
consistência, snapshot ou ausência no dataset. Página vazia, cursor ausente/repetido, excesso de
página/nodes/bytes, drift, timeout, cancelamento, circuito aberto ou falha de staging encerram a
execução como falha. Páginas já stageadas numa execução incompleta não autorizam apply.

Sem prova versionada de TTL e retomada, retry operacional cria nova execução, novo staging e começa
na página 1. Cursor de uma tentativa anterior nunca é reutilizado.

### Identidade e presença de `name`

A identidade de origem continua sendo a tuple case-sensitive
`(source_instance, tenant_scope, entity_name, source_key)`, com `entity_name=usuarios`. O token JSON
integral é codificado como `INTEGER:<decimal canônico>` e o token textual como `STRING:<texto
exato>`; o `source_key_wire_type` permanece `INTEGER` ou `STRING`. As duas representações não
colidem. `usuario_id BIGINT IDENTITY` é o identificador canônico e nunca deriva do token, nome,
ordem ou hash.

`name` preserva três estados:

- `ABSENT`: o campo não veio; para current existente, conserva nome, presença e hash anteriores;
- `NULL`: a origem enviou nulo explicitamente e isso pode produzir mudança;
- `VALUE`: preserva o texto validado, sem inventar fallback ou normalização de negócio.

Chave inválida, tipo inválido de nome e valor fora dos limites vão para quarantine com reason code
fechado. Duas linhas da mesma execução para a mesma source key com estados de atributo divergentes
geram `CONFLICTING_USER_ATTRIBUTES` e bloqueiam a fatia; Java não resolve o conflito em memória.

### Current, histórico, hashes e ordem total

O SQL Server é o único dono de dedupe, candidate set, conflito, reconciliação e aplicação. O wrapper
`core.usp_apply_reconcile_publish_usuarios` cerca o kernel comum e a aplicação tipada na mesma
transação. `v2_runtime` recebe somente `EXECUTE` nos dois entrypoints publicados e permanece sem
acesso direto às tabelas de `stg`, `core` e `recon`.

Os fingerprints são versionados e calculados no SQL Server:

- `usuarios-attributes-v1` cobre presença e valor de `name`;
- `usuarios-name-presence-v1` cobre o estado de presença;
- `usuarios-state-v1` cobre o hash de atributos e `active=1`.

A ordem observacional é total pela tuple
`(observation_order_at_utc, observation_order_execution_id)`. Em replay, ambos vêm da execução de
origem; nos demais casos, vêm da própria execução. Ela é uma ordem técnica, não `updatedAt`,
watermark nem frescor da fonte.

Cada candidato recebe exatamente uma disposição:

- `INSERTED`: cria current ativo e uma linha de histórico;
- `UPDATED`: uma observação mais nova muda o estado e cria uma linha de histórico;
- `REACTIVATED`: uma tuple já existente e inativa reaparece, preserva o `usuario_id` e cria
  histórico;
- `NO_OP`: estado idêntico mais novo apenas avança last-seen/ordem e não cria histórico;
- `STALE_NO_OP`: observação mais antiga não regride current e não cria histórico.

Um empate da tuple de ordem com estado divergente falha fechado. Retry da mesma execução publicada
só retorna a evidência já persistida depois de conferir autorização, aplicações e histórico; retry
de staging com conteúdo divergente também falha. Assim, USR-01 é preservada sem loop por usuário.

### Ausência, reativação e USR-02

V007 nunca grava `active=0`. A ausência de uma source key, qualquer cap, erro, cancelamento ou
terminalidade local não observada não produz efeito de domínio. `REACTIVATED` existe para preservar
o canonical ID caso um estado inativo legítimo já exista por bootstrap ou por um gate futuro, mas
não autoriza a vertical a criar esse estado.

Desativação continua proibida até V2-012b demonstrar paridade/completude independente e V2-013
ratificar duas travessias completas, consistência, janela e guardrails. Mesmo depois disso, sweep
deve ser uma operação separada e set-based; não será efeito colateral desta extração.

### Lifecycle e minimização

O nome bruto existe somente no staging ativo e nos estados duráveis current/history. O archive
tipado de staging não duplica o nome: conserva wire type, presença, hashes versionados, quantidade
de bytes descartados e atestado da linha minimizada. Restore materializa somente cópia read-only em
`recon`; não reidrata staging nem promove. O purge tipado só pode acompanhar o lifecycle comum após
archive íntegro no mesmo commit.

O lifecycle nunca apaga `core.usuario`, `core.usuario_history`, quarantine ou evidência de
reconciliação. TTL, TDE, backup, mascaramento, legal hold, cold storage e retenção produtiva
continuam nos gates de governança correspondentes. Logs, handoffs e evidências compartilhadas não
podem conter nome, source key, cursor, payload ou fingerprint completo.

O orçamento tipado é agregado por execução e entra na classificação comum antes de oversized,
`TOP` e cumulativos. Assim uma execução antiga que só exceda o cap pelo sidecar não ocupa a vaga de
uma execução posterior menor; uma iTVF com seek por execução e `TOP(max+1)` limita o probe. O
trigger vertical verifica somente lower-bound/caps e não reescreve o plano. O wrapper tipado mantém
row fence sobre a execução e recusa sidecar tardio para generic-only preexistente, fechando a corrida
com plan/archive; o gate rollback-only 029 reproduz esse estado e exige a recusa 51703. Esta fatia
ainda não promete preencher de forma máxima todo orçamento remanescente. A evidência concorrente local
também é deliberadamente composicional: o probe em duas sessões usa a fórmula extraída do lock comum
V004, enquanto o exercício rollback testa os entrypoints reais. O SHOWPLAN 028 atribui cada access
path representativo ao seu statement, incluindo o seek do apply tipado, e recusa conversões ou
warnings operacionais. Nenhuma dessas provas autoriza execução externa ou publicação.

## Rastreabilidade das regras

- **USR-01:** unicidade do current e histórico apenas em `INSERTED`, `UPDATED` ou `REACTIVATED`.
- **USR-02:** reativação preserva o canonical ID; desativação por ausência permanece desligada; a
  vertical não cria join com Coletas.
- **USR-03:** GraphQL `individual` permanece a fonte transitória; substituição exige fonte oficial,
  contrato equivalente e paridade.
- **USR-04:** `9901` não é chamado e `updatedAt` permanece `ABSENT/UNREQUESTED`; nenhum timestamp
  técnico é apresentado como timestamp da origem.

## Consequências

- V2-033 pode atingir o marco `IMPLEMENTADA_EM_SHADOW` sem alegar completude externa.
- No estado histórico deste ADR/V007, a view de Usuários e seu contrato consumidor continuavam em
  V2-035b/V2-037. O follow-up [ADR 0021](0021-dimensao-current-de-usuarios-em-sombra.md) registra
  que V009 passou a fornecer somente `core.v_usuario_dimension_current_v1` em sombra; o contrato
  consumidor permanece em V2-037.
- V2-041 bloqueia credencial, rede, sonda, release, deploy, produção e cutover, mas não a validação
  sintética local rollback-only.
- Composição do runtime, policy produtiva de retenção e habilitação de sweep permanecem gates
  separados; este ADR não lhes concede autorização implícita.

## Alternativas rejeitadas

- **Persistir cursor como checkpoint:** não há validade/TTL provados entre processos.
- **Tratar `pageInfo` terminal como snapshot completo:** confunde terminalidade de protocolo com
  cobertura de negócio.
- **Usar `updatedAt`, `9901` ou timestamp técnico como incremental:** inventa contrato inexistente.
- **Desativar quem não apareceu:** transforma falha, cap ou página incompleta em perda lógica.
- **Usar `Long` sem type tag para todo `id`:** colide tokens INTEGER e STRING distintos.
- **Acumular current/snapshot em `List`, `Map` ou `Set`:** viola memória limitada e retira do SQL o
  processamento relacional.
- **Duplicar nome no archive:** amplia retenção de dado pessoal sem necessidade operacional.
