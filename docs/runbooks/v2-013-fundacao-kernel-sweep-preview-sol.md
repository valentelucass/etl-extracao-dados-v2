# Runbook — V2-013/FUNDACAO_KERNEL_LOCAL

## Fronteira

Execute somente offline. O bloco não consulta fonte, rede, `.env`, segredo, banco ou runtime; não executa SQLCMD/Flyway; não habilita sweep. Dados e fingerprints dos testes são sintéticos. O kernel avalia uma responsabilidade por chamada e nunca carrega chaves.

## Verificação

```powershell
pwsh -NoProfile -File scripts/validation/Build-V2013SweepApplicabilityCatalog.ps1 -VerifyGenerated
pwsh -NoProfile -File scripts/validation/Test-V2013SweepPreviewFoundation.ps1 -ArtifactsOnly
.\mvnw.cmd --offline --batch-mode --no-transfer-progress "-Dtest=FailClosedSweepPreviewKernelTest,SweepApplicabilityMatrixTest,SweepPreviewBoundaryTest,SweepPreviewDeterminismTest,SweepPreviewHardeningTest,SweepPreviewMutationCatalogTest,FirstWaveCompletenessGateTest,MainTest" test
pwsh -NoProfile -File scripts/validation/Test-V2013SweepPreviewFoundation.ps1
```

Use JDK 17 somente no processo. O validator deve mostrar 33 linhas, 11 famílias, zero `ENABLED`, zero `PROVEN_COMPLETE`, 53 casos catalogados, um caso positivo sintético e 52 mutantes executados pelo teste Java. O gerador em `-VerifyGenerated` não escreve.

## Interpretação

`PREVIEW_ELIGIBLE_NO_APPLY_CAPABILITY` comprova apenas que um envelope sintético local satisfaz o kernel e habilita zero entidades. Ordinais e fingerprints de ocorrência/execução são metadados técnicos O(1); diferença de hash isolada não prova independência autêntica, proveniência, snapshot, completude, owner, retenção, segurança operacional ou autorização. Esses gates continuam nominais por entidade; Q-*-04, V2-034a e V2-034b permanecem abertos.

O caminho positivo aceita apenas uma responsabilidade `ROOT`. Qualquer `CHILD` continua bloqueado até existir planner nominal por entidade que vincule explicitamente o assessment do pai; esta fundação não o fornece.

Falha, drift, evidência repetida, fingerprint divergente ou gate ausente mantém tudo bloqueado. Não existe fallback de apply, persistência, anti-join, lista de chaves ou ação corretiva neste runbook.
