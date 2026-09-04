# Identidade e crosswalk da primeira onda

Este catálogo é a baseline offline de V2-009a para `coletas`/6908, `fretes`/6389 e
`usuarios`/GraphQL `individual`. Ele complementa, sem reescrever, os contratos estruturais V2-025a.

O [manifesto](manifesto.json) fecha quatro limites:

- registry por `(source_instance, tenant_scope, entity, source_key)`, sempre com source e tenant
  explícitos;
- `canonical_id` surrogate alocado no SQL Server e nunca derivado de uma chave da fonte;
- aliases de negócio versionados, distintos de source key e de ordenação;
- reason codes e ações fechados para replay, reativação, rekey, colisão, cardinalidade e quarentena.

## Leitura correta da matriz

- 6908 e 6389 usam o `id` integral de cada release como source key. A repetição do mesmo ID em
  linhas expandidas não cria outra raiz.
- `sequence_code` e `corporation_sequence_number` são aliases. O primeiro só foi 1:1 nas janelas
  caracterizadas; o legado permite minuta de Frete repetida, portanto uma busca por alias pode ser
  0..N e deve falhar fechada quando ambígua.
- Usuários usa `node.id`, normalizado semanticamente como `user_id`. `name` não é chave. O wire
  contract aceita inteiro ou texto; a tag de tipo impede colapso entre `42` e `"42"`.
- Comparações históricas entre Data Export e GraphQL mostram paridade limitada à janela. IDs desses
  transportes continuam source IDs até um crosswalk versionado resolvê-los.

O `source_instance` representa a origem lógica ESL compartilhada, não o transporte. O
`tenant_scope` não aceita sentinel global. Os nomes de entidade são fechados e case-sensitive.

## Limite arquitetural

O Java valida e codifica uma observação por vez. Preflight de colisão/cardinalidade e toda
persistência relacional permanecem set-based no SQL Server. O kernel comum já existe; a fatia física
V2-009d de Usuários foi fechada por V007/V2-033 com identidade tipada, isolamento por ambiente,
constraints, índices, grants e testes de colisão, nulo, concorrência e replay. Coletas e Fretes
continuam aguardando o enforcement de suas próprias verticais. `core.entity_record_state.record_state_id`
não é o canonical ID e nenhum schema `crosswalk` será criado.

Este catálogo não prova completude, snapshot ou unicidade global e não autoriza credencial, rede,
sweep, desativação, deploy ou cutover.

## Revalidação

```powershell
pwsh -NoProfile -File .\scripts\validation\Test-FirstWaveIdentityCatalog.ps1
```

O validador exige UTF-8 estrito, vocabulários exatos, três matrizes, vínculo aos fingerprints
V2-025a e ausência de marcadores sensíveis. Os testes Java recomputam os fingerprints de identidade
e exercitam tipo, limite, scope, replay, reativação, alias, rekey e quarentena com valores sintéticos.
