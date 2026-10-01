# Checkpoint 0323 — P08 V105 e validador de epoch preparados, sem SQL

## Identificação, autoridade e limite

- 29/09/2026 03:11 UTC; Builder Banco e Persistência. Anterior:
  [0322](0322-p08-triagem-validadores-versionados.md) SHA-256
  `3BDC9D66D630FE97EF4CA0AD02036D146B257EC30D5A1F7B04EA0F2F2CA0C55F`.
- Ordem do usuário: **preparar, sem executar SQL**, validador estrutural estrito
  do epoch V104/V105 e migration V105 para collations de
  `ctl.execution_audit`, `ref.expansion_lab_label` e TVP dependente; conservar
  V001–V104 e FAIL 003/005/030, testar offline e enviar bytes/impacto/
  recuperação/readback ao Supervisor antes de qualquer migrate.
- Alvo físico futuro condicionado a nova decisão: somente
  `localhost/ETL_SISTEMA_V2_SHADOW`, autenticação Windows. Nesta unidade houve
  zero chamadas SQL Server, Flyway, fonte real, remoto ou produção e nenhuma
  reserva de efeito físico. Nenhum aceite P08/P01–P33 foi promovido.

## Alterações e decisões

| Artefato | SHA-256 | Papel |
| --- | --- | --- |
| `database/migrations/V105__align_audit_and_expansion_label_bin2.sql` | `578355923BDDB402F8321B10F2ED5D0A5571B1BAF3B4ABF83117C0912FB99E07` | Candidato transacional com precondições e readback agregado interno. |
| `database/validation/063_validate_epoch_v104_v105.sql` | `ED739ABC646538FEF636D0F4E52816E6986B5A9F3739E92E15E9BEC39B40B0FC` | Inventário fechado bidirecional; V104 permanece `MIGRATION_PENDING`. |
| `database/validation/064_snapshot_epoch_v105.sql` | `A888C43D4670C9DFDE11B7ED08D3B035B309C38D50ED9D4EFD96E47300B14AF6` | Snapshot futuro somente leitura, ainda não executado. |
| `database/baseline/001_schema_foundation_baseline.sql` | `291871CE9B1CFF3C524CCAD8C8C4C68A67FAE3BC0672A49B803A04038CBA4126` | V105 acrescentada após V104. |
| `database/manifest/epoch-v104-v105-inventory.json` | `D06407B5D0C53C28D37A8240140F6845F8C71693AF28C19C4F135FD14DB041D1` | 105 fontes com SHA, 569 objetos, 23 TVPs, 1.019 constraints nomeadas, 215 sem nome e 100 índices. |
| `database/manifest/epoch-v105-collation-repair.json` | `20A2DECD28B38F65AB643DACA065C06DB998C72253EBC21D3BE358BB7B0E5633` | Escopo semântico e contrato JDBC/TVP. |

O gerador `Build-EpochSchemaInventory.mjs`, o template SQL e
`Build-EpochStructuralValidator.mjs` derivam o validador das 105 entradas do
baseline, sem usar o catálogo implantado como allowlist. Excluem apenas
`ctl.flyway_schema_history` e dependências técnicas por identidade exata. Os
checkers `Test-SchemaFoundationManifest.ps1` e `Test-ProgressiveDataGate.ps1`
passaram a exigir V105. O scanner de segredos passou a ler `.mjs` como texto;
não houve exclusão ou supressão de regra. V001–V104, validadores 003/005/030,
manifests históricos e ledgers não foram editados. Há outros deltas no worktree
de outros Builders, preservados.

V105 mantém o nome/schema, três colunas, ordem, tipos, larguras, nulidade e PK
do TVP. Apenas `label` da tabela e do TVP muda de CI para
`Latin1_General_100_BIN2`; `category`/`raw_value` já BIN2 continuam assim.
A procedure recriada é textualmente igual à V042: dez parâmetros, TVP no sexto,
recibo de uma linha com quatro IDs. `EXCEPT` nos dois sentidos deverá distinguir
replay exato de replay com label alterado só por caixa. Dados existentes não são
reescritos. Drift de shape, dono, dependência, grants diretos, checks ou status
existente incompatível deve parar antes do DROP. Grants de schema permanecem;
grants diretos inesperados exigem revisão separada. A mudança de `user_type_id`
é esperada porque o tipo é recriado.

Plano de impacto, preflight, reserva, snapshots, testes rollback-only e recuperação:
`docs/runbooks/p08-v105-preparacao-20260928.md`. A janela futura precisa evitar
consumidores ativos do TVP, aferir backup local e confirmar `flyway:validate`
separado. Falha/resultado incerto exige readback autoritativo, sem `clean`,
`repair`, `drop` ou retry cego. V105 aplicada não poderá ser editada; correção
posterior só por nova migration revisada ou restauração autorizada separadamente.

## Execução e evidência

| Prova | Camada | Observado | Recibo privado SHA-256 |
| --- | --- | --- | --- |
| `Test-EpochV105Offline.mjs` | Offline | PASS; três mutantes de ordem/BIN2, cinco de collation e quatro de inventário rejeitados; procedure V042 exata; 334 objetos pós-V017 reconciliados. | `target/shadow-local-rebuild-20260928-01/p08-prep-0323-offline.out` `FA64C1AD6B9579F75B514D9C29608081C824B6FDBB9877001B54FD17D7C44760` |
| Checkers de fundação e progressivo, PowerShell 7.6.6 | Offline | PASS/PASS, exigem V001–V105. | `p08-prep-0323-foundation.out` `CF9A0D58A76A74768CE13B24BF279926402EB441081377C36024ADADDF53F02A`; `p08-prep-0323-progressive.out` `1BB674AF268604948C4C62C5139BF3DB8D220BCB5DF8AD563D9F58467ED44BD1` |
| `Test-TrilhaPreparation.ps1` | Offline | PASS, 33 etapas, 48 IDs abertos, nove pacotes. | `p08-prep-0323-trilha.out` `B631C3CF43CFD377A3CB36774F1CF30171075154BF66E01D6B783E164225D75C` |
| `Invoke-OfflineSecretScan.ps1` | Offline | Primeiro exit 1: três `.mjs` classificados não texto, saída só no terminal; após lista de texto corrigida exit 0, 4.070 textos, zero achados. | `p08-prep-0323-secret-scan.out` `F4B940E0349E25649D151FD686633BDDB56DD2CD7BF545700E399D29B5EF9E6F` |

Os FAIL originais persistem: `003` SHA-256
`001D904FA31CD4C555C656F19EF5A6B626F971EC54B822E4FFD0FBE205FE7A84`,
`005` `9D133CC814953C327B35AAFB6F9B500C8EB52BB0EFF2B27B091DBF997F5E7D7A`,
`030` `B51E6D48151A9FE19B5659056EE3F8B22B1BE7C6D09A55408A50E89396597C28`.
Não há processo próprio restante nem resultado de efeito desconhecido.

Handoff Runtime recebido: `JdbcExpansionReferences` usa `setStructured(6,
"ref.expansion_lab_label_batch", labels)`, três colunas acima e recibo de quatro
IDs. Testes adjacentes offline 15+19 PASS, sem cobrir V105. Callback direto a
Runtime não tem conexão Maestri; Supervisor recebe a dependência. Depois de
autorização física, Banco deve testar replay exato positivo e case-only negativo
(`EXP_REF_CONTENT_DIVERGENT`) em rollback, contagens/IDs antes/depois, e IT
selecionada `ExpansionLaboratoryReferencesIT`. O empacotador Runtime ainda fixa
104 migrations/`lastVersion=104`.

## Retomada imediata — até três ações

1. Supervisor revisar bytes/hashes, lock e dependências TVP/procedure, dados,
   privilégios, backup e recuperação; decidir autorização explícita para V105.
2. Se autorizada, Banco refazer preflight `master`/alvo/listeners, reserva e
   snapshot 064 antes; executar no máximo um migrate V105, readback 064 e
   `flyway:validate` em gates separados. Divergência para imediatamente.
3. Após schema PASS, Banco coordenar com Runtime o replay rollback-only e IT;
   Runtime alinhar empacotador 104→105 antes de pacote. P08 só fecha por
   evidência agregada, não por esta preparação.
