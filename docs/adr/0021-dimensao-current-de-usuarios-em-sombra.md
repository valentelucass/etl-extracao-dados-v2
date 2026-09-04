# ADR 0021 — Dimensão current de Usuários em sombra

- Status: Aceito para a fatia Usuários de V2-035b; contrato consumidor continua pendente
- Data: 2026-09-01
- Escopo: projeção dimensional interna sobre `core.usuario`

## Contexto

V2-033 já mantém `core.usuario` e `core.usuario_history` com identidade escopada, presença
tri-state, aplicação set-based, replay idempotente e desativação por ausência desligada. O próximo
passo permitido pelo roadmap é somente a primeira fatia de V2-035b: uma dimensão de Usuários em
sombra sobre esse estado.

O legado demonstra que consumidores históricos receberam três aliases e um trim de nome, mas não
há manifesto de consumidor aprovado. Além disso, a view legada usa um marcador de exclusão que não
equivale ao `active` governado da V2 e chama timestamp técnico de atualização sem provar frescor da
origem. O ADR 0006 reserva `pub` para contratos aprovados por consumidor; V2-037 continua sendo o
gate desses nomes, tipos, filtros, grants e aliases.

## Decisão

A migration V009 cria somente a view schemabound e versionada
`core.v_usuario_dimension_current_v1`. “Sombra” permanece estado do ambiente e do manifesto, não
um schema de negócio.

O grão primário é uma linha ativa por `usuario_id` canônico. A identidade alternativa é
`(environment_name, source_instance, tenant_scope, source_key_token)`; a entidade é sempre
`usuarios`. A view projeta, nessa ordem:

1. `usuario_id`;
2. `environment_name`, `source_instance` e `tenant_scope`;
3. `source_key_token` e `source_key_wire_type`;
4. `name_presence` e `usuario_name`;
5. `last_changed_at_utc` e `last_seen_at_utc`.

`source_key_token` é a chave type-tagged da origem e não recebe o alias legado “User ID”. O nome é
o valor validado persistido, sem trim ou normalização de negócio. Os dois timestamps são técnicos
UTC e não representam `updatedAt`, watermark ou frescor da fonte.

A view filtra apenas `active=1` e não junta histórico, não usa `DISTINCT`, agregação ou window. O
histórico comprova mudanças e replay, mas nunca multiplica o grão dimensional. Hashes, IDs de
execução e a constante `active` não são expostos.

V009 não cria índice, role, principal, `GRANT` ou `DENY`. O PK clustered de `core.usuario` atende o
lookup canônico e `UQ_core_usuario_source` atende a identidade escopada. Duplicar nome/chave em um
novo índice aumentaria retenção e write amplification sem carga ou consumidor medidos. `public` e
`v2_runtime` permanecem sem `SELECT`; um `DENY` explícito a `public` também não é criado para não
impedir um grant futuro por role após aprovação.

## Limite com V2-037

Este ADR não cria `pub.vw_dim_usuarios`, aliases `[User ID]`, `[Nome]` ou
`[Data Atualizacao]`, trim, manifesto de consumidor, rota, grant, deploy ou cutover. V2-037 deverá
decidir esses itens a partir de evidência externa aprovada e poderá projetar o contrato consumidor
sobre esta view interna sem alterar o grão canônico.

## Consequências

- A fatia Usuários de V2-035b pode atingir `IMPLEMENTADA_EM_SHADOW` separadamente.
- V2-035b agregada permanece aberta para as outras cinco dimensões.
- O contrato legado continua `CONSUMER_CONTRACT_PENDING`; a existência da view interna não autoriza
  publicação externa.
- Replay/no-op/stale e histórico continuam pertencendo a V007/V2-033; V009 é uma projeção
  determinística do current ativo.
- Não há chamada externa, uso de credencial, bootstrap, sweep, produção ou cutover neste bloco.

## Alternativas rejeitadas

- **Criar diretamente `pub.vw_dim_usuarios`:** anteciparia V2-037 e um contrato consumidor ainda
  não aprovado.
- **Criar schema `shadow`:** contradiz o ADR 0006; sombra é ambiente, não camada de negócio.
- **Juntar `core.usuario_history`:** multiplicaria o grão e confundiria dimensão current com trilha
  histórica.
- **Copiar trim, aliases e timestamp do legado:** cristalizaria decisões de compatibilidade sem
  manifesto de consumidor.
- **Adicionar índice covering com nome/chave:** duplicaria dado potencialmente pessoal e custo de
  escrita sem benchmark ou workload autorizado.
