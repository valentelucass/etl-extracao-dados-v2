# Bloco 44 — V2-011a — decisão local de Fretes 6389

## Resultado autorizado

Executar somente V08/V2-011a em GPT-5.6 Sol Ultra, offline e fail-closed. O resultado máximo é
`COMPLETE_LOCAL_DECISION_ONLY`. V2-011, V2-046a e V2-046b ficam abertos.

Política de fronteira:

- `V2_046A_NOT_REQUIRED_FOR_BASE_SHADOW`;
- `V2_046A/V2_046B_REQUIRED_ONLY_FOR_RELATIONAL/PARITY_GATES`;
- V2-046b é a única dona do crosswalk Coleta–Frete.

## Sequência

1. Ler `AGENTS.md`, `STATES.md`, `../CONTEXTO_GLOBAL.md` e a trilha.
2. Executar o gate antes dos artefatos e registrar o RED exato.
3. Materializar ADR, catálogo fechado e casos exclusivamente sintéticos.
4. Atualizar primeiro `STATES.md`; depois a trilha e seu validator.
5. Rodar os gates locais e manter todos os limites abaixo.

RED registrado antes do catálogo:

```text
FRETES_V2_011A_DECISION status=FAIL reason=FRETES_DECISION_CATALOG_MISSING
```

GREEN focado:

```text
PASS: decisão local V2-011a/Fretes 6389 validada; rules=7 synthetic_cases=14 negative_mutations=9 result=COMPLETE_LOCAL_DECISION_ONLY implementation=OPEN base_shadow=UNBLOCKED relation=OPEN.
```

## Gates

```powershell
pwsh -NoProfile -File .\scripts\validation\Test-FretesV2011DecisionCatalog.ps1
pwsh -NoProfile -File .\scripts\validation\Test-FirstWaveContractCatalog.ps1
pwsh -NoProfile -File .\scripts\validation\Test-FirstWaveIdentityCatalog.ps1
pwsh -NoProfile -File .\scripts\validation\Test-PortabilityCatalog.ps1 -VerifyGenerated
pwsh -NoProfile -File .\scripts\validation\Test-Gpt56ChatTrail.ps1
pwsh -NoProfile -File .\scripts\security\Test-OfflineSecretScan.ps1
pwsh -NoProfile -File .\scripts\security\Invoke-OfflineSecretScan.ps1 -Source .
git diff --check
```

Maven só se Java ou POM mudar; não é aplicável a este recorte documental/JSON/PowerShell.

Fechamento verificado em 2026-09-06: todos os gates acima passaram. A portabilidade confirmou 401
artefatos, 2.437 campos, 75 regras, 16 classes e zero `UNCLASSIFIED`; a trilha confirmou 47/103 e
V09/Bloco 45 como única rota `AGORA`; o scanner passou 9 casos e examinou 889 candidatos, 888
textos e um binário, sem finding; nove arquivos passaram UTF-8 estrito e `git diff --check` passou.

## Proibições

Não usar rede, `.env`, segredo, banco, SQLCMD, legado, produção, deploy, commit ou push. Não criar
mapper, migration, SQL, runtime, relação, caracterização, bootstrap, publicação ou cutover. Não
inferir moeda, regra financeira, data ambígua, identidade por alias ou associação Coleta–Frete.
