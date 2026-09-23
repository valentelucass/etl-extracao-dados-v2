# Identidade e grão — Data Export 6399 (Manifestos)

Este catálogo fecha somente `V2-009b/6399` (P01) como
`COMPLETE_LOCAL_CONSERVATIVE_FAIL_CLOSED`. A decisão usa o contrato offline, a matriz V2-017a,
as políticas MAN e o legado em leitura, inclusive o corpus histórico já versionado. Não houve
rede, credencial, payload novo, banco ou alteração do legado.

## Raiz e envelopes

Cada registro é normalizado a partir de exatamente um dos dois envelopes aceitos: `/data/*` no
contrato local ou `/*` no corpus histórico e no parser legado. Outro formato, ou forma ambígua, é
quarentena. Todos os paths abaixo são relativos ao registro normalizado.

A raiz usa `/sequence_code` inteiro positivo. Sua identidade natural é a tuple
`(source_instance, tenant_scope, manifestos, INTEGER:<sequence_code>)`; `source_instance` e tenant
são configuração externa explícita e não podem ser `DEFAULT`, `GLOBAL` ou `SINGLETON`. O ID
canônico futuro continua surrogate. Ordem, hash, número MDF-e, coocorrência e IDs derivados do
legado nunca substituem a tuple.

## Filhos e cardinalidade

- Pick: `(root_canonical_id, INTEGER:<mft_pfs_pck_sequence_code>)`, path
  `/mft_pfs_pck_sequence_code`. O nome `/pick_sequence_code` foi refutado como source path.
- MDF-e: `(root_canonical_id, STRING:<mft_mfs_key>)`, path `/mft_mfs_key`, com exatamente 44
  dígitos no contrato conservador. `/mft_mfs_number` é atributo inteiro do mesmo registro, nunca
  identidade.

Ambos os filhos são `0..N` por raiz, sem converter máximos observados em limite do fornecedor.
Pick ausente/nulo sem outro sinal significa zero pick. Número e chave MDF-e ausentes/nulos
significam zero MDF-e. Número sem chave válida ou chave sem número é preservado e quarentenado.
`/mdfe_status` não integra esse par: no corpus ele é `VALUE` nas 228 linhas, inclusive 148 sem
MDF-e, e não diverge dentro das 100 raízes. Por isso é escalar da raiz replicado pela expansão;
sua presença isolada jamais cria filho ou erro de chave. Quando existir MDF-e, o status pode ser
copiado somente como contexto com proveniência.

O corpus delimitado contém 228 linhas físicas, 100 raízes, 193 chaves raiz+pick distintas e 43
chaves raiz+MDF-e. Há 80 pares número+chave MDF-e e 148 linhas sem ambos; 37 repetições além da
primeira pertencem a 16 grupos raiz+chave, com máximo observado de sete. Não houve colisão
raiz+pick, divergência de número no mesmo raiz+chave, assimetria número/chave nem divergência de
status por raiz. Esses agregados sustentam o contrato conservador, mas não provam unicidade
global, estabilidade temporal, tenant no payload ou limite máximo remoto.

## Replay, colisão e relações

Replay da mesma chave natural reutiliza a raiz ou o filho. Conteúdo idêntico é no-op; divergência
residual no mesmo frescor é quarentena segundo V2-026a. Nenhuma versão anterior fornece backfill,
e chegada, página, ordem ou hash não desempata. Rekey e colisão não reposicionam aliases.

Picks e MDF-es são reduzidos separadamente. A coocorrência de pick, MDF-e ou campos de Coleta na
mesma linha não cria relação. Candidatos Manifesto→Coleta são apenas preservados para V2-046a,
sem FK, lookup, `TOP 1`, nulificação de órfão ou materialização antecipada.

## Limites

Esta decisão não cria migration, schema, tabela, procedure, view, fato, grant, mapper,
integração, staging, promoção ou runtime. Ausência nunca autoriza sweep, delete, desativação,
publicação ou cutover. A execução continua em V2-026/V04; enforcement físico em V2-009d; e
`V2-025d` permanece bloqueada por `V2-041` em `EXTERNAL_HOLD`. O checkbox pai V2-009b continua
aberto pelas demais fatias.

## Revalidação

```powershell
pwsh -NoProfile -File .\scripts\validation\Test-DataExport6399ContractCatalog.ps1
pwsh -NoProfile -File .\scripts\validation\Test-DataExport6399IdentityCatalog.ps1
pwsh -NoProfile -File .\scripts\validation\Test-ManifestosV2026DecisionCatalog.ps1
```
