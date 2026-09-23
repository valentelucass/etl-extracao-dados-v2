[CmdletBinding()]
param()
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$root = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..'))
function Require([bool]$ok,[string]$message) {
    if (-not $ok) { throw "LOCALIZACAO_CARGAS_SHADOW_VERTICAL_MISSING: $message" }
}
function Read([string]$path) {
    $full = Join-Path $root $path
    Require (Test-Path -LiteralPath $full -PathType Leaf) "arquivo=$path"
    Get-Content -LiteralPath $full -Raw
}
$required = @(
 'database/migrations/V014__create_localizacao_cargas_shadow_vertical.sql',
 'database/validation/046_validate_localizacao_cargas_shadow_vertical.sql',
 'database/validation/047_exercise_localizacao_cargas_shadow_vertical_rollback.sql',
 'database/manifest/localizacao-cargas-shadow-vertical.json',
 'database/manifest/localizacao-cargas-shadow-vertical.sha256',
 'scripts/validation/Invoke-LocalizacaoCargasV2028ShadowValidation.ps1',
 'scripts/validation/Test-LocalizacaoCargasShadowConcurrency.ps1',
 'docs/runbooks/v2-028-localizacao-cargas-shadow.md',
 'src/main/java/br/com/esl/etl/v2/modulos/localizacaocargas/aplicacao/LocalizacaoCargaDataExportRecordMapper.java',
 'src/main/java/br/com/esl/etl/v2/plataforma/persistencia/localizacaocargas/JdbcSqlServerLocalizacaoCargaStagingGateway.java')
foreach ($path in $required) { $null = Read $path }

$manifestPath = Join-Path $root 'database/manifest/localizacao-cargas-shadow-vertical.json'
$manifest = Get-Content -LiteralPath $manifestPath -Raw | ConvertFrom-Json
$actualSha = (Get-FileHash -LiteralPath $manifestPath -Algorithm SHA256).Hash.ToLowerInvariant()
Require ((Read 'database/manifest/localizacao-cargas-shadow-vertical.sha256').Trim() -ceq
    "$actualSha  localizacao-cargas-shadow-vertical.json") 'SHA do manifesto divergiu.'
Require ($manifest.localState -ceq 'IMPLEMENTADA_EM_SHADOW') 'estado local divergiu.'
Require ($manifest.persistence.current -ceq 'core.localizacao_cargas') 'raiz core não é plural.'
Require ($manifest.bindings.contract.releaseFingerprint -ceq
    'da95fc17fc3db2fae7635c5456166d72af844b1d826e84ab6c2ba8b6f1b65c24') 'binding 8656 divergiu.'
Require ($manifest.bindings.identity.identityFingerprint -ceq
    '14af11dce5fd7696238907b72f8f6c8c77f86a4cff3045b489da5eb132c0d6f0') 'identidade divergiu.'
Require (@($manifest.bindings.portability.matrixIds).Count -eq 17) 'matriz 8656 não tem 17 IDs.'
Require (@($manifest.implementedRules).Count -eq 7) 'LOC-01..07 incompletos.'

$concurrency = Read 'scripts/validation/Test-LocalizacaoCargasShadowConcurrency.ps1'
foreach ($token in @('LOCALIZACAO_CARGAS_OTHER_ENVIRONMENT_ISOLATED',
 'LOCALIZACAO_CARGAS_OTHER_TENANT_ISOLATED')) {
    Require ($concurrency.Contains($token)) "prova concorrente sem isolamento=$token"
}

$migration = Read 'database/migrations/V014__create_localizacao_cargas_shadow_vertical.sql'
foreach ($token in @('CREATE TABLE core.localizacao_cargas','validation_disposition',
 'quarantine_reason_code','RAW_PAYLOAD_PRESENCE_REQUIRED',"OPENJSON(@field_presence_json,N'$.fields')",
 'COUNT_BIG(*)<>7','rawWireLexeme','LOCALIZACAO_8656_17_PATHS_V1',
 'FREIGHT_VOLUME_FALLBACK_DEFERRED_RELATION_AND_PUBLICATION','DATAEXPORT_8656',
 '@effective_field_catalog','STRING_AGG','UQ_core_localizacao_cargas_source',
 '@service_at_envelope_raw_type','service_at_derived_utc','service_at_valid_offset_count',
 'E. South America Standard Time','@decimal_binding','@status_envelope_raw_type',
 '@status_raw NVARCHAR(MAX)','@status_normalized NVARCHAR(MAX)',
 'BLOCKED_NO_COMPLETENESS_PROOF','NOT_EVALUATED_PRUNE_DISABLED')) {
    Require ($migration.Contains($token)) "V014 sem token=$token"
}
Require (-not [regex]::IsMatch($migration,'core\.localizacao_carga(?!s)')) 'singular residual em V014.'
Require (-not [regex]::IsMatch($migration,'(?i)\bTOP\s*\(?\s*1')) 'TOP 1 proibido em V014.'
Require ([regex]::Matches($migration,'(?m)^CREATE OR ALTER PROCEDURE ').Count -eq 2) 'V014 não tem dois entrypoints.'

$validator = Read 'database/validation/046_validate_localizacao_cargas_shadow_vertical.sql'
$exercise = Read 'database/validation/047_exercise_localizacao_cargas_shadow_vertical_rollback.sql'
foreach ($token in @('core.localizacao_cargas','<>41','$.fields.sequence_number','COUNT_BIG(*)<>7')) {
    Require (($validator + $exercise).Contains($token)) "SQL046/047 sem token=$token"
}
foreach ($token in @('#build_localizacao_presence','QUARANTINE_RAW_PRESERVED',
 'MISSING_VALID_SERVICE_AT','VALUE->ABSENT','/sequence_number',
 '2018-11-04T00:30:00','2018-02-17T23:30:00','REPLICATE(N''9'',5001)',
 'ERROR_NUMBER()=52203','ERROR_NUMBER()=52204','ERROR_NUMBER()=52205',
 'ERROR_NUMBER()=52206')) {
    Require ($exercise.Contains($token)) "SQL047 sem prova=$token"
}
Require (-not [regex]::IsMatch($exercise,'core\.localizacao_carga(?!s)')) 'singular residual em 047.'
Require (-not $exercise.Contains('"state"') -and -not $exercise.Contains('"v"')) 'fixture fora do mapper.'

$roles = Read 'database/migrations/V002__create_v2_database_roles.sql'
$foundation = Get-Content -LiteralPath (Join-Path $root 'database/manifest/schema-foundation.json') -Raw | ConvertFrom-Json
Require ($foundation.manifestVersion -eq 5 -and $foundation.grantPublisher.allowedTriplets -eq 41) 'foundation não está em v5/41.'
Require ($roles.Contains('usp_stage_localizacao_carga_record') -and
         $roles.Contains('usp_apply_reconcile_publish_localizacao_cargas')) 'V002 sem dois triplets.'
Require ((Read 'database/baseline/001_schema_foundation_baseline.sql').Contains(
    'V014__create_localizacao_cargas_shadow_vertical.sql')) 'baseline sem V014.'

$mapper = Read 'src/main/java/br/com/esl/etl/v2/modulos/localizacaocargas/aplicacao/LocalizacaoCargaDataExportRecordMapper.java'
foreach ($token in @('rawWireLexeme','DATAEXPORT_8656','LOCALIZACAO_8656_17_PATHS_V1')) {
    Require ($mapper.Contains($token)) "mapper sem token=$token"
}
$jdbc = Read 'src/main/java/br/com/esl/etl/v2/plataforma/persistencia/localizacaocargas/JdbcSqlServerLocalizacaoCargaStagingGateway.java'
$call = [regex]::Match($jdbc,'(?s)\{call stg\.usp_stage_localizacao_carga_record\((.*?)\)\}')
Require ($call.Success -and ([regex]::Matches($call.Groups[1].Value,'\?').Count -eq 27)) 'CALL JDBC não tem 27 parâmetros.'
Write-Output 'LOCALIZACAO_CARGAS_V2_028_SHADOW status=PASS'
