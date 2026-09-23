# V2-012 — Fundação offline de caracterização

Este runbook cobre somente a execução offline da fundação, a manutenção de fixtures sintéticas,
validators locais e os gates necessários para oráculos futuros. Ele não é procedimento de acesso
a fornecedor.

## Executar a fundação

Use o JDK 17 já provisionado para o workspace e mantenha Maven offline:

```powershell
.\mvnw.cmd --offline --batch-mode --no-transfer-progress clean verify
pwsh -NoProfile -File .\scripts\validation\Test-V2012CharacterizationFoundation.ps1
```

O resultado agregado esperado é
`FOUNDATION_OFFLINE_COMPLETE_Q01_PENDING_ORACLES`. Os quatro perfis devem continuar
`PREPARED_NOT_EXECUTED`, com gate `ORACLE_REQUIRED`. Um resultado diferente encerra a execução
local como fail-closed; não altere um Q-*-01 para compensar a falha.

Receipts de teste, quando gerados pela suíte, ficam exclusivamente em
`target/v2-012-characterization/<SYNTH_ID>/receipt.json`. Eles são descartáveis e não constituem
evidência externa.

## Manter fixtures sintéticas

1. Altere somente o bundle da entidade em `src/test/resources/contracts/v2-012/fixtures/` e use
   identificadores `SYNTH_*` para cenários e escopos.
2. Preserve o cenário aceito e acrescente uma contraprova fail-closed com motivos exatos para cada
   nova regra estrutural.
3. Execute a suíte offline. O teste de binding informará divergência enquanto o fingerprint
   normalizado ainda estiver antigo.
4. Depois de congelar o bundle, fixe o fingerprint normalizado no perfil correspondente e atualize
   no manifesto os SHA-256 brutos, o fingerprint normalizado e as contagens de cenários.
5. Reexecute `clean verify` e o validator da fundação. Não aceite fixture sem marcador sintético,
   JSON desconhecido, hash divergente ou receipt não sanitizado.

Não copie amostras, IDs, cursores ou valores de uma fonte real para os fixtures.

## Validators locais aplicáveis

Execute apenas os validators offline que existirem no checkout:

```powershell
pwsh -NoProfile -File .\scripts\validation\Test-FirstWaveContractCatalog.ps1
pwsh -NoProfile -File .\scripts\validation\Test-FirstWaveIdentityCatalog.ps1
pwsh -NoProfile -File .\scripts\validation\Test-ColetasV2010DecisionCatalog.ps1
pwsh -NoProfile -File .\scripts\validation\Test-ColetasV2010ShadowVertical.ps1
pwsh -NoProfile -File .\scripts\validation\Test-DataExport6399ContractCatalog.ps1
pwsh -NoProfile -File .\scripts\validation\Test-DataExport6399IdentityCatalog.ps1
pwsh -NoProfile -File .\scripts\validation\Test-ManifestosV2026DecisionCatalog.ps1
pwsh -NoProfile -File .\scripts\validation\Test-ManifestosV2026ShadowVertical.ps1
pwsh -NoProfile -File .\scripts\validation\Test-DataExport6906IdentityCatalog.ps1
pwsh -NoProfile -File .\scripts\validation\Test-CotacoesV2027ShadowVertical.ps1
pwsh -NoProfile -File .\scripts\validation\Test-UsuariosCurrentHistoryManifest.ps1
pwsh -NoProfile -File .\scripts\validation\Test-UsuariosDimensionCurrentManifest.ps1
```

Não execute validator de concorrência, plano de execução ou banco como parte desta fundação.

## Gates para oráculos futuros

| Rota | Estado preservado | Gate mínimo de uma fatia posterior |
|---|---|---|
| `Q-COL-01` | aberta | autorização do owner, escopos explícitos e oráculo Data Export 6908 |
| `Q-MAN-01` | `EXTERNAL_HOLD` | remoção formal do hold, autorização, escopos e oráculo Data Export 6399 |
| `Q-COT-01` | aberta | autorização do owner, escopos explícitos e oráculo Data Export 6906 |
| `Q-USR-01` | aberta | autorização do owner, escopos explícitos e oráculo GraphQL individual |

Cada rota deve ser executada e avaliada separadamente, com limites aprovados e somente metadados
estruturais sanitizados. Uma rota não herda autorização, terminalidade, chave, timezone ou
completude de outra.

Pare antes de qualquer execução se faltar autorização, source_instance, tenant_scope, oráculo,
limite ou forma segura de sanitizar a evidência. Bootstrap, R01, R02, paridade real, sweep,
publicação, cutover e E2E permanecem fora deste runbook.
