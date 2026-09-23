# Decisão local de Localização de Cargas 8656 — V2-028a

Este catálogo fecha somente a decisão local `LOC-01`–`LOC-07` do Bloco 46/V10A. O resultado
máximo é `COMPLETE_LOCAL_DECISION_ONLY`: V2-028 continua aberta para a implementação V10.

## Artefatos e evidência

- `decisao-v01.json`: contrato fechado da decisão e bindings exatos do contrato/identidade 8656;
- `fixtures/casos-v01.synthetic.json`: 24 casos exclusivamente sintéticos, sem valor ou ID real;
- `../../adr/0028-localizacao-cargas-8656-dominio-presenca-frescor-status-e-schema.md`: decisão e
  consequências arquiteturais;
- `../../../scripts/validation/Test-LocalizacaoCargasV2028DecisionCatalog.ps1`: gate determinístico,
  offline e fail-closed.

As fixtures são `SYNTHETIC_DECISION_CASES_ONLY_NOT_SOURCE_CONTRACT_EVIDENCE`. Elas exercitam as
regras, mas não ampliam o `/info`, a resposta observada, os tipos publicados nem a completude do
contrato V2-025b/8656. O SHA-256 da fixture é vinculado na decisão. O validador também confere os
fingerprints existentes do contrato e da identidade, além das sete regras LOC da portabilidade.

## Fronteira relacional

`COALESCE(volumes_localizacao, volumes_fretes, 0)` fica congelado como semântica downstream, com
zero local como valor real. O possível pareamento com Fretes é somente candidato exato e escopado,
com presença e proveniência. Ambiguidade bloqueia; nenhum join, FK, crosswalk, `TOP 1` ou match por
nome é autorizado. V2-046a e V2-046b continuam abertas e nenhuma delas é fechada ou antecipada.

## Limites

Não há mapper, Java produtivo, migration, SQL, banco, runtime, fonte, rede, relação,
caracterização, bootstrap, publicação, sweep, deploy ou cutover. Maven e SQL são `N/A` neste
recorte documental/JSON/PowerShell; serão gates da implementação V10.

## Validação

```powershell
pwsh -NoProfile -File .\scripts\validation\Test-LocalizacaoCargasV2028DecisionCatalog.ps1
```

