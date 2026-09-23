# Decisão local de Fretes 6389 — V2-011a

Este catálogo fecha somente a decisão local FRE-01–FRE-07 do Bloco 44/V08. Ele não contém payload
real e não autoriza fonte, mapper, migration, SQL, runtime, relação, caracterização, bootstrap,
publicação ou cutover.

## Artefatos

- `decisao-v01.json`: contrato fechado da decisão e bindings versionados.
- `fixtures/casos-v01.synthetic.json`: 14 casos exclusivamente sintéticos, positivo e negativo por
  regra.
- `../../adr/0027-fretes-6389-decisao-local-presenca-frescor-performance.md`: justificativa e
  consequências arquiteturais.
- `../../../scripts/validation/Test-FretesV2011DecisionCatalog.ps1`: gate determinístico,
  fail-closed e sem I/O externo.

O SHA-256 da fixture é vinculado dentro da decisão. O validator também recalcula os bindings do
contrato 6389, da identidade de primeira onda e dos dez paths do sidecar GraphQL transitório.

## Limite relacional

`V2_046A_NOT_REQUIRED_FOR_BASE_SHADOW` e
`V2_046A/V2_046B_REQUIRED_ONLY_FOR_RELATIONAL/PARITY_GATES`. O campo `pickItemId` é conservado
somente como candidato; V2-046b continua dona do crosswalk Coleta–Frete.

## Validação

```powershell
pwsh -NoProfile -File .\scripts\validation\Test-FretesV2011DecisionCatalog.ps1
```

O resultado verde máximo é `COMPLETE_LOCAL_DECISION_ONLY`, com implementação e relação abertas.

## Implementação posterior da decisão

O catálogo acima permanece como fotografia histórica de V2-011a. A execução base posterior de
V09 é rastreada separadamente em `database/manifest/fretes-shadow-vertical.json` e no runbook
`docs/runbooks/v2-011-fretes-shadow-base.md`; ela não amplia a evidência de origem deste catálogo e
mantém V2-046a/V2-046b abertas.
