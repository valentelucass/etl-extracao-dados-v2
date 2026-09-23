# Runbook — V2-050/FUNDACAO_MEDICAO_LOCAL

## Fronteira

Execute somente offline e com o JDK 17 já instalado. Esta rota não consulta rede, API, fonte,
`.env`, segredo ou banco; não executa SQLCMD, Flyway, SQL real, DDL, DML ou migration; não altera
runtime nem habilita uma entidade. Toda entrada é `SYNTHETIC_FIXTURE`.

O resultado máximo é `FOUNDATION_LOCAL_COMPLETE_NO_ENTITY_SCALE_GATE_PASSED`. Mesmo após o
GREEN, `ENTITY_V2_050_GATE=OPEN`, `ENTITY_HEAP_PLATEAU_NOT_PROVEN`,
`ENTITY_SQL_PLAN_NOT_EXECUTED` e `ENTITY_PUSH_DOWN_PLAN_NOT_PROVEN` continuam vigentes.

## Execução

```powershell
$env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
pwsh -NoProfile -File .\scripts\validation\Invoke-V2050MeasurementFoundation.ps1
pwsh -NoProfile -File .\scripts\validation\Test-V2050MeasurementFoundation.ps1 -ArtifactsOnly
```

O runner usa Maven offline para exercitar o núcleo/harness e as integrações reais
`DataExportPageStreamer` e `GraphQlPageStreamer` em 16, 256 e 4096 páginas, sempre com oito
registros por página. Ele publica atomicamente o receipt sob
`target/v2-050-measurement/measurement-foundation-receipt.json` e chama o validator.

O sucesso deve registrar `MANAGED_IN_FLIGHT_BOUND_PROVEN_SYNTHETICALLY`, pico em voo exatamente
um, final em voo zero e contagens reconciliadas. O mutante deve registrar
`LEAK_MUTANT_REJECTED` após ser recusado por `EXECUTION_WIDE_PAGE_RETENTION_DETECTED`. Limpe a
retenção depois da asserção.

## Regressão e fechamento

Antes de alterar os registros canônicos, execute:

```powershell
.\mvnw.cmd --offline --batch-mode --no-transfer-progress "-Dtest=Measurement*Test,ManagedPageGaugeTest,ExecutionWidePageRetentionMutantTest,DataExportPageStreamerMultiscaleMeasurementTest,GraphQlPageStreamerMultiscaleMeasurementTest,DataExportPageStreamerTest,GraphQlPageStreamerTest,FirstWaveCompletenessGateTest,MainTest" test
pwsh -NoProfile -File .\scripts\validation\Test-V2013SweepPreviewFoundation.ps1
pwsh -NoProfile -File .\scripts\security\Test-OfflineSecretScan.ps1
pwsh -NoProfile -File .\scripts\security\Invoke-OfflineSecretScan.ps1
.\mvnw.cmd --offline --batch-mode --no-transfer-progress clean verify
pwsh -NoProfile -File .\scripts\validation\Invoke-V2050MeasurementFoundation.ps1
pwsh -NoProfile -File .\scripts\validation\Test-V2050MeasurementFoundation.ps1 -ArtifactsOnly
```

O `clean verify` remove todo o diretório `target`, inclusive o receipt. Por isso, a segunda
execução do runner após o último `clean verify` é obrigatória: ela recria o receipt a partir dos
streamers reais antes do fechamento e dos modos `ArtifactsOnly`/FULL. Não use um receipt de uma
rodada anterior como evidência.

Depois de todos os gates verdes, marque somente `V2-050/FUNDACAO_MEDICAO_LOCAL`, atualize em
seguida a linha do Bloco 50 e rode o validator em FULL e `Test-Gpt56ChatTrail.ps1`. A fotografia
final deve ser 53/107 concluídos, 54 pendentes e zero `STATUS=AGORA` se nenhuma rota estiver
elegível. Não materialize nem inicie Bloco 51.

Heap e duração do receipt são `JVM_HEAP_DIAGNOSTIC_ONLY`: variam naturalmente entre processos.
A serialização é estável para o mesmo objeto, mas hashes de execuções com diagnósticos diferentes
não precisam coincidir.
