# Bloco 46 — V2-028a — decisão local de Localização de Cargas 8656

## Resultado autorizado

Executar somente V10A/V2-028a em GPT-5.6 Sol Ultra, offline e fail-closed. O resultado máximo é
`COMPLETE_LOCAL_DECISION_ONLY`. V2-028 permanece aberta para V10; V2-046a e V2-046b também ficam
abertas.

## Sequência e evidência RED → GREEN

1. Ler as instruções, o estado, a trilha, o contrato e a identidade 8656.
2. Criar primeiro o validador e executá-lo sem catálogo.
3. Registrar o RED estável abaixo.
4. Materializar ADR, catálogo fechado, README e casos exclusivamente sintéticos.
5. Executar o validator até GREEN, incluindo mutações negativas.
6. Atualizar primeiro `STATES.md`; só depois a trilha e seu gate.

RED observado antes do catálogo:

```text
LOCALIZACAO_V2_028A_DECISION status=FAIL reason=LOCALIZACAO_DECISION_CATALOG_MISSING
```

Comando focado:

```powershell
pwsh -NoProfile -File .\scripts\validation\Test-LocalizacaoCargasV2028DecisionCatalog.ps1
```

O GREEN deve declarar sete regras, 24 casos sintéticos e 13 mutações recusadas. As mutações cobrem
regra ausente, identidade errada, fallbacks por `sequence_number` e nickname, keep-last, data de
extração como frescor, coerção numérica, match ambíguo de Fretes, sweep, publicação, relação e os
dois fingerprints vinculantes.

## Gates correlatos

```powershell
pwsh -NoProfile -File .\scripts\validation\Test-DataExport8656IdentityCatalog.ps1
pwsh -NoProfile -File .\scripts\validation\Test-FretesV2011DecisionCatalog.ps1
pwsh -NoProfile -File .\scripts\validation\Test-PortabilityCatalog.ps1 -VerifyGenerated
pwsh -NoProfile -File .\scripts\validation\Test-Gpt56ChatTrail.ps1
pwsh -NoProfile -File .\scripts\security\Test-OfflineSecretScan.ps1
pwsh -NoProfile -File .\scripts\security\Invoke-OfflineSecretScan.ps1 -Source .
git diff --check
```

Maven e SQL são `N/A` neste bloco porque nenhum Java/POM, migration, SQL ou banco pode ser tocado.
Não usar essa classificação para dispensá-los da implementação V10.

## Proibições

Sem rede, `.env`, segredo, credencial, fonte, banco, Java produtivo, mapper, migration, SQL,
runtime, relação, FK, crosswalk, `TOP 1`, caracterização, bootstrap, publicação, sweep, deploy,
cutover, commit ou push. As fixtures não são evidência do contrato da origem.

