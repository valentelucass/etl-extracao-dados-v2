# ADR 0030 — Kernel local fail-closed de Sweep Preview

- Status: aceito somente para a fundação local do Bloco 49
- Decisão: `FOUNDATION_LOCAL_COMPLETE_NO_ENTITY_SWEEP_ENABLED`
- Escopo: `Q-SWP-FND-01` / `V2-013/FUNDACAO_KERNEL_LOCAL`

## Contexto

O fim local da paginação, página vazia, `hasNext=false` ou uma única travessia não provam snapshot nem completude. A execução destrutiva por entidade depende de V2-012b aceita, owner nominal, retenção e prova independente. Esta fatia não possui essas evidências.

## Decisão

O núcleo recebe somente enums, flags, contagens limitadas e fingerprints técnicos referentes a uma responsabilidade. A política governa aplicabilidade, tipo da responsabilidade, hierarquia, owner, fonte vazia e limites; seu fingerprint é SHA-256 canônico domain-separated. O binding combina política, escopo e snapshot. Cada uma das duas travessias e duas observações de ausência carrega ordinal, ocorrência, execução, política, escopo, snapshot e binding. Repetição ou cruzamento de ocorrência/execução bloqueia.

`ExecutionMode.SWEEP` e `SourceCompletenessStatus` existentes são compostos diretamente. `FAILED`, `UNVERIFIED`, completude não provada, página faltante, cap, timeout, cancelamento, vazio anômalo, limites excedidos, hierarquia insegura, owner ausente e qualquer divergência bloqueiam em precedência fechada. A única saída positiva é `PREVIEW_ELIGIBLE_NO_APPLY_CAPABILITY`, ainda exclusivamente sintética e local, e só pode classificar `ROOT`. `CHILD` permanece bloqueado até um planner nominal por entidade provar binding e avaliação do pai.

O envelope é O(1): não recebe coleção, universo de chaves, callback, cursor, payload, ID de negócio, SQL ou conexão. Fingerprints e contagens são redigidos em `toString`. Ordinais e fingerprints de ocorrência/execução são envelopes técnicos O(1), não prova de autenticidade ou proveniência externa. A mera diferença entre hashes jamais satisfaz o gate nominal de uma entidade. O kernel valida somente coerência estrutural do envelope; autenticidade, proveniência nominal e autoridade permanecem gates de cada entidade.

## Matriz

A matriz fechada contém 33 responsabilidades de 11 famílias. Ela distingue raízes, filhos, componentes 1:1, histórico append-only, canais observacionais, referências, source lines, condicionais e candidatos ainda não resolvidos. Há zero linha `ENABLED` e zero `PROVEN_COMPLETE`. As raízes Fretes, Localização e Usuários permanecem `DISABLED`; as duas linhas condicionais Raster também ficam `DISABLED`; todas as demais linhas são `BLOCKED` ou `NOT_APPLICABLE`.

## Consequências

- Nenhuma entidade recebe sweep, prune, delete, desativação, persistência ou publicação.
- O único resultado positivo é um happy path sintético/local e habilita zero entidades.
- `SWEEP_PREVIEW` e `SWEEP_APPLY` do runtime permanecem deny-all e não são conectados ao kernel.
- Q-*-04, V2-034a, V2-034b e V2-013 pai permanecem abertos.
- Uma mudança na árvore canônica, matriz, fixture ou binding exige nova versão e falha o validator até revisão explícita.
