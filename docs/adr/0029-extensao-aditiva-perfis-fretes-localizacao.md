# ADR 0029 — extensão aditiva dos perfis Fretes e Localização

- Status: Aceito somente para Q-FND-02/Bloco 48
- Data: 2026-09-06
- Escopo: harness `test-only`, offline, sem executar oráculo

## Contexto

Q-FND-01 já congela o núcleo provider-neutral para quatro entidades. Fretes 6389 possui dois
canais com autoridade distinta: o Data Export identifica a raiz por `/id`; o sidecar GraphQL tem
dez paths transitórios e serve apenas para observação. Localização 8656 possui chave exclusiva
`/corporation_sequence_number` e dezesseis campos cujo tipo provider ainda depende de oráculo.

## Decisão

Q-FND-01 fica byte a byte inalterado. A extensão vive somente em subdiretórios novos e modela
exatamente dois perfis de entidade com três canais isolados. O Data Export de Fretes conserva os
sete paths contratados, `updated_at` sem autoridade de frescor, página curta não terminal e
completude ausente. O sidecar é `SYNTHETIC_ONLY`, `OBSERVATION_ONLY`, limitado a cem edges,
`publicationBlocked=true` e `rootOrFreshnessAuthority=false`.

Localização conserva os dezessete paths, chave INTEGER exclusiva, tipos restantes
`UNVERIFIED_ORACLE_REQUIRED`, `status_branch_nickname=ABSENT_UNSOURCED_LEGACY` e `service_at`
somente como candidato cuja tradução continua não provada. LOC-01..LOC-07 e FRE-01..FRE-07 são
bindings literais; empates, regressão, nulos divergentes, parsing, terminalidade, performance e
ausência continuam fail-closed conforme as decisões versionadas.

Receipts contêm apenas IDs fechados, fingerprints, status e resultado; são substituídos
atomicamente somente sob `target/v2-012-characterization/q-fnd-02`. Não carregam payload, cursor,
escopo, ID de negócio ou valor observado.

## Consequências

O resultado máximo é
`FOUNDATION_OFFLINE_COMPLETE_Q02_FRETES_LOCALIZACAO_PENDING_ORACLES`. Q-FRE-01, Q-LOC-01,
V2-012, V2-012a/b/c e todos os gates externos permanecem abertos. Relação/crosswalk, bootstrap,
paridade, sweep, publicação e cutover continuam proibidos.
