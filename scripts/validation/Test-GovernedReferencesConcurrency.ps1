[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$migrationPath = Join-Path $repositoryRoot `
    'database\migrations\V008__create_governed_references.sql'
$schemaFoundationMigrationPath = Join-Path $repositoryRoot `
    'database\migrations\V001__create_v2_schema_foundation.sql'
$migration = Get-Content -LiteralPath $migrationPath -Raw -Encoding utf8
$receiptTriggerMatch = [Text.RegularExpressions.Regex]::Match(
    $migration,
    '(?ms)^CREATE TRIGGER ref\.trg_reference_import_receipt_guard\b.*?^GO\s*$'
)
if (-not $receiptTriggerMatch.Success `
        -or $receiptTriggerMatch.Value.IndexOf(
            'UPDLOCK, HOLDLOCK', [StringComparison]::Ordinal
        ) -lt 0 `
        -or $receiptTriggerMatch.Value.IndexOf(
            '@actual_rows', [StringComparison]::Ordinal
        ) -lt 0) {
    throw 'O seal não contém lock da release e prova de cardinalidade física.'
}
$contentTriggerNames = @(
    'calendario', 'status_coleta', 'filial_operacional', 'regiao_destino_alias',
    'filial_documento', 'frota_propria', 'classificacao_frota_alias',
    'classificacao_frota_matriz', 'classificacao_frota_excecao', 'atribuicao_filial',
    'exclusao_cubagem', 'regiao_logistica_cep', 'regiao_logistica_cidade',
    'tarifa_rota_uf'
)
foreach ($triggerName in $contentTriggerNames) {
    $contentTrigger = [Text.RegularExpressions.Regex]::Match(
        $migration,
        "(?ms)^CREATE TRIGGER ref\.trg_${triggerName}_insert_guard\b.*?^GO\s*`$"
    )
    $releaseLockIndex = $contentTrigger.Value.IndexOf(
        'DECLARE @locked_release_count BIGINT', [StringComparison]::Ordinal
    )
    $receiptReadIndex = $contentTrigger.Value.IndexOf(
        'INNER JOIN ref.reference_import_receipt', [StringComparison]::Ordinal
    )
    if (-not $contentTrigger.Success `
            -or $releaseLockIndex -lt 0 `
            -or $receiptReadIndex -le $releaseLockIndex `
            -or $contentTrigger.Value.Substring(
                $releaseLockIndex, $receiptReadIndex - $releaseLockIndex
            ).IndexOf('FROM ref.reference_release WITH (UPDLOCK, HOLDLOCK)',
                [StringComparison]::Ordinal) -lt 0) {
        throw "O trigger $triggerName não compartilha o lock/seal da release."
    }
}
$triggerMatch = [Text.RegularExpressions.Regex]::Match(
    $migration,
    '(?ms)^CREATE TRIGGER ref\.trg_reference_ratification_guard\b.*?^GO\s*$'
)
if (-not $triggerMatch.Success) {
    throw 'O trigger de ratificação não pôde ser isolado para o probe concorrente.'
}
$triggerDefinition = $triggerMatch.Value
$formulaStartMarker = 'SET @lock_resource = CONCAT('
$formulaEndMarker = 'EXEC @lock_result = sys.sp_getapplock'
$formulaStart = $triggerDefinition.IndexOf($formulaStartMarker, [StringComparison]::Ordinal)
$formulaEnd = $triggerDefinition.IndexOf($formulaEndMarker, [StringComparison]::Ordinal)
if ($formulaStart -lt 0 -or $formulaEnd -le $formulaStart) {
    throw 'A fórmula canônica do namespace de ratificação não pôde ser isolada.'
}
$canonicalLockFormula = $triggerDefinition.Substring(
    $formulaStart,
    $formulaEnd - $formulaStart
).Trim()

foreach ($requiredFormulaToken in @(
        'DATALENGTH(@family)', 'DATALENGTH(@scope)',
        'DATALENGTH(@activation_scope)', "N'V2_REF_RATIFY_'"
    )) {
    if ($canonicalLockFormula.IndexOf(
            $requiredFormulaToken,
            [StringComparison]::Ordinal
        ) -lt 0) {
        throw "A fórmula canônica de ratificação não contém $requiredFormulaToken."
    }
}
foreach ($requiredTriggerToken in @(
        'sys.sp_getapplock', "@LockOwner = 'Transaction'", 'UPDLOCK, HOLDLOCK',
        'IX_ref_reference_release_scope_validity'
    )) {
    if ($triggerDefinition.IndexOf(
            $requiredTriggerToken,
            [StringComparison]::Ordinal
        ) -lt 0) {
        throw "O trigger de ratificação não contém $requiredTriggerToken."
    }
}

$targetDatabase = 'ETL_SISTEMA_V2_SHADOW'
$dataProbeDatabase = 'ETL_V2_REF_PROBE_' + [Guid]::NewGuid().ToString('N')
if ($dataProbeDatabase -cnotmatch '^ETL_V2_REF_PROBE_[0-9a-f]{32}$') {
    throw 'Nome do banco efêmero de referências não passou pela allowlist.'
}
$sqlcmd = Get-Command sqlcmd.exe -ErrorAction Stop
$temporaryRoot = [IO.Path]::GetFullPath([IO.Path]::GetTempPath())
$probeDirectory = [IO.Path]::GetFullPath(
    (Join-Path $temporaryRoot ('etl-v2-references-lock-' + [Guid]::NewGuid().ToString('N')))
)
$holderProcess = $null
$contentHolderProcess = $null
$contentProbeCreated = $false
$contentCleanupPath = $null
$dependencyProbeCreated = $false
$dependencyCleanupPath = $null
$dataProbeDatabaseCreated = $false
$dataProbeCreatePath = $null
$dataProbeDropPath = $null

function Read-ProbeOutput {
    param([string[]]$Paths)

    return ($Paths | ForEach-Object {
        if (Test-Path -LiteralPath $_ -PathType Leaf) {
            Get-Content -LiteralPath $_ -Raw
        }
    }) -join "`n"
}

function Invoke-BlockingRaceProbe {
    param(
        [Parameter(Mandatory)][string]$Name,
        [Parameter(Mandatory)][string]$HolderPath,
        [Parameter(Mandatory)][string]$ReadyProbePath,
        [Parameter(Mandatory)][string]$ReadyMarker,
        [Parameter(Mandatory)][string]$ContenderPath,
        [Parameter(Mandatory)][string]$ContenderMarker,
        [Parameter(Mandatory)][string]$HolderMarker,
        [Parameter(Mandatory)][string]$RetryPath,
        [Parameter(Mandatory)][string]$RetryMarker,
        [Parameter(Mandatory)][string]$OutputDirectory,
        [Parameter(Mandatory)][string]$DatabaseName
    )

    $probeHolder = $null
    $probeHolderOutputPath = Join-Path $OutputDirectory "$Name-holder.out"
    $probeHolderErrorPath = Join-Path $OutputDirectory "$Name-holder.err"
    try {
        $probeHolder = Start-Process -FilePath $sqlcmd.Source -WindowStyle Hidden -PassThru `
            -ArgumentList @(
                '-S', 'localhost', '-C', '-E', '-f', '65001', '-d', $DatabaseName,
                '-i', $HolderPath, '-b', '-h', '-1', '-W'
            ) `
            -RedirectStandardOutput $probeHolderOutputPath `
            -RedirectStandardError $probeHolderErrorPath

        $readyDeadline = [DateTimeOffset]::UtcNow.AddSeconds(5)
        $ready = $false
        while (-not $ready -and [DateTimeOffset]::UtcNow -lt $readyDeadline) {
            if ($probeHolder.HasExited) {
                $holderOutput = Read-ProbeOutput @(
                    $probeHolderOutputPath, $probeHolderErrorPath
                )
                throw "$Name holder terminou cedo. $holderOutput"
            }
            $readyOutput = @(
                & $sqlcmd.Source -S localhost -C -E -f 65001 -d $DatabaseName `
                    -i $ReadyProbePath -b -h -1 -W
            )
            if ($LASTEXITCODE -ne 0) {
                throw "$Name probe de prontidão falhou. $($readyOutput -join ' ')"
            }
            $ready = ($readyOutput -join "`n").IndexOf(
                $ReadyMarker, [StringComparison]::Ordinal
            ) -ge 0
            if (-not $ready) { Start-Sleep -Milliseconds 100 }
        }
        if (-not $ready) { throw "$Name holder não ficou pronto no prazo." }

        $contenderOutput = @(
            & $sqlcmd.Source -S localhost -C -E -f 65001 -d $DatabaseName `
                -i $ContenderPath -b -h -1 -W
        )
        if ($LASTEXITCODE -ne 0 `
                -or ($contenderOutput -join "`n").IndexOf(
                    $ContenderMarker, [StringComparison]::Ordinal
                ) -lt 0) {
            throw "$Name contender divergiu. $($contenderOutput -join ' ')"
        }

        if (-not $probeHolder.WaitForExit(12000)) {
            throw "$Name holder não terminou no prazo."
        }
        $probeHolder.WaitForExit()
        $holderOutput = Read-ProbeOutput @($probeHolderOutputPath, $probeHolderErrorPath)
        if ($probeHolder.ExitCode -ne 0 `
                -or $holderOutput.IndexOf(
                    $HolderMarker, [StringComparison]::Ordinal
                ) -lt 0) {
            throw "$Name holder falhou. $holderOutput"
        }

        $retryOutput = @(
            & $sqlcmd.Source -S localhost -C -E -f 65001 -d $DatabaseName `
                -i $RetryPath -b -h -1 -W
        )
        if ($LASTEXITCODE -ne 0 `
                -or ($retryOutput -join "`n").IndexOf(
                    $RetryMarker, [StringComparison]::Ordinal
                ) -lt 0) {
            throw "$Name retry divergiu. $($retryOutput -join ' ')"
        }
    } finally {
        if ($null -ne $probeHolder -and -not $probeHolder.HasExited) {
            $probeHolder.Kill()
            $probeHolder.WaitForExit()
        }
    }
}

$lockInputs = @'
DECLARE @family NVARCHAR(32) = N'PICK_STATUS';
DECLARE @scope NVARCHAR(128) = N'LOCAL_SYNTHETIC_CONCURRENCY';
DECLARE @activation_scope NVARCHAR(16) = N'SHADOW';
DECLARE @lock_resource NVARCHAR(255);
'@
$lockDeclaration = $lockInputs + "`r`n" + $canonicalLockFormula `
    + "`r`nDECLARE @lock_result INT;"
$otherScopeLockDeclaration = $lockDeclaration.Replace(
    "N'LOCAL_SYNTHETIC_CONCURRENCY'",
    "N'LOCAL_SYNTHETIC_OTHER_SCOPE'"
)

$holderSql = @"
:On Error exit
IF DB_NAME() <> N'$targetDatabase'
    THROW 51992, N'O probe concorrente de referências só aceita o banco local V2.', 1;
SET NOCOUNT ON;
SET XACT_ABORT ON;
BEGIN TRANSACTION;
$lockDeclaration
EXEC @lock_result = sys.sp_getapplock
    @Resource = @lock_resource,
    @LockMode = 'Exclusive',
    @LockOwner = 'Transaction',
    @LockTimeout = 5000,
    @DbPrincipal = 'public';
IF @lock_result < 0
    THROW 51993, N'A sessão holder não adquiriu o namespace de ratificação.', 1;
RAISERROR(N'REFERENCES_LOCK_HELD', 10, 1) WITH NOWAIT;
WAITFOR DELAY '00:00:06';
ROLLBACK TRANSACTION;
PRINT N'REFERENCES_LOCK_RELEASED';
"@

$contenderSql = @"
:On Error exit
IF DB_NAME() <> N'$targetDatabase'
    THROW 51994, N'O probe concorrente de referências só aceita o banco local V2.', 1;
SET NOCOUNT ON;
SET XACT_ABORT ON;
BEGIN TRANSACTION;
$lockDeclaration
EXEC @lock_result = sys.sp_getapplock
    @Resource = @lock_resource,
    @LockMode = 'Exclusive',
    @LockOwner = 'Transaction',
    @LockTimeout = 0,
    @DbPrincipal = 'public';
IF @lock_result = -1
BEGIN
    ROLLBACK TRANSACTION;
    PRINT N'REFERENCES_CONTENTION_CONFIRMED';
END
ELSE IF @lock_result >= 0
BEGIN
    ROLLBACK TRANSACTION;
    PRINT N'REFERENCES_LOCK_ACQUIRED_BEFORE_HOLDER';
END
ELSE
BEGIN
    ROLLBACK TRANSACTION;
    THROW 51995, N'O contender retornou estado de lock inesperado.', 1;
END;
"@

$otherScopeSql = @"
:On Error exit
IF DB_NAME() <> N'$targetDatabase'
    THROW 51996, N'O probe concorrente de referências só aceita o banco local V2.', 1;
SET NOCOUNT ON;
SET XACT_ABORT ON;
BEGIN TRANSACTION;
$otherScopeLockDeclaration
EXEC @lock_result = sys.sp_getapplock
    @Resource = @lock_resource,
    @LockMode = 'Exclusive',
    @LockOwner = 'Transaction',
    @LockTimeout = 0,
    @DbPrincipal = 'public';
IF @lock_result < 0
    THROW 51997, N'Outro scope de referência sofreu contenção indevida.', 1;
ROLLBACK TRANSACTION;
PRINT N'REFERENCES_OTHER_SCOPE_ISOLATED';
"@

$reacquireSql = @"
:On Error exit
IF DB_NAME() <> N'$targetDatabase'
    THROW 51998, N'O probe concorrente de referências só aceita o banco local V2.', 1;
SET NOCOUNT ON;
SET XACT_ABORT ON;
BEGIN TRANSACTION;
$lockDeclaration
EXEC @lock_result = sys.sp_getapplock
    @Resource = @lock_resource,
    @LockMode = 'Exclusive',
    @LockOwner = 'Transaction',
    @LockTimeout = 0,
    @DbPrincipal = 'public';
IF @lock_result < 0
    THROW 51999, N'O rollback não liberou o namespace de ratificação.', 1;
ROLLBACK TRANSACTION;
PRINT N'REFERENCES_LOCK_REACQUIRED_AFTER_ROLLBACK';
"@

$contentProbeScope = 'LOCAL_SYNTHETIC_SEAL_' + [Guid]::NewGuid().ToString('N')
$contentReadyResource = 'V2_REF_CONTENT_READY_' + [Guid]::NewGuid().ToString('N')
$contentSetupSql = @"
:On Error exit
IF DB_NAME() <> N'$dataProbeDatabase'
    THROW 52050, N'O setup content/seal aceita somente o banco local V2.', 1;
SET NOCOUNT ON;
SET XACT_ABORT ON;
INSERT INTO ref.reference_release (
    family_code, scope_code, release_version, schema_version, source_kind,
    source_artifact_ref, source_fingerprint, source_row_count, valid_from,
    valid_to_exclusive, reason_code, author_role
) VALUES (
    N'PICK_STATUS', N'$contentProbeScope', N'content-seal-v1', 1,
    N'DETERMINISTIC_LOCAL', N'SYNTHETIC_CONTENT_SEAL', REPLICATE('a', 64), 1,
    '20360101', '20370101', N'SYNTHETIC_CONCURRENCY', N'REFERENCE_AUTHOR'
);
PRINT N'CONTENT_SEAL_SETUP_READY';
"@

$contentHolderSql = @"
:On Error exit
IF DB_NAME() <> N'$dataProbeDatabase'
    THROW 52051, N'O holder content/seal aceita somente o banco local V2.', 1;
SET NOCOUNT ON;
SET XACT_ABORT ON;
DECLARE @release_id BIGINT = (
    SELECT reference_release_id FROM ref.reference_release
    WHERE family_code = N'PICK_STATUS' AND scope_code = N'$contentProbeScope'
      AND release_version = N'content-seal-v1'
);
IF @release_id IS NULL THROW 52052, N'Release sintética do holder ausente.', 1;
BEGIN TRANSACTION;
INSERT INTO ref.status_coleta (
    reference_release_id, family_code, raw_status, normalization_version,
    canonical_status, display_label, is_terminal, valid_from, valid_to_exclusive, reason_code
) VALUES (
    @release_id, N'PICK_STATUS', N'pending', N'legacy-pick-status-v1',
    N'pending', N'Pendente sintético', 0, '20360101', '20370101',
    N'SYNTHETIC_CONCURRENCY'
);
DECLARE @ready_lock_result INT;
EXEC @ready_lock_result = sys.sp_getapplock
    @Resource = N'$contentReadyResource',
    @LockMode = 'Exclusive',
    @LockOwner = 'Transaction',
    @LockTimeout = 5000,
    @DbPrincipal = 'public';
IF @ready_lock_result < 0
    THROW 52060, N'O holder não publicou o sinal transacional de prontidão.', 1;
RAISERROR(N'CONTENT_PARENT_LOCK_HELD', 10, 1) WITH NOWAIT;
WAITFOR DELAY '00:00:06';
COMMIT TRANSACTION;
PRINT N'CONTENT_COMMITTED_AFTER_HOLD';
"@

$contentReadyProbeSql = @"
:On Error exit
IF DB_NAME() <> N'$dataProbeDatabase'
    THROW 52061, N'O probe de prontidão aceita somente o banco local V2.', 1;
SET NOCOUNT ON;
BEGIN TRANSACTION;
DECLARE @ready_lock_result INT;
EXEC @ready_lock_result = sys.sp_getapplock
    @Resource = N'$contentReadyResource',
    @LockMode = 'Exclusive',
    @LockOwner = 'Transaction',
    @LockTimeout = 0,
    @DbPrincipal = 'public';
IF @ready_lock_result = -1
    PRINT N'CONTENT_PARENT_LOCK_HELD';
ELSE IF @ready_lock_result < 0
    THROW 52062, N'Estado inesperado no probe de prontidão.', 1;
ELSE
    PRINT N'CONTENT_PARENT_LOCK_NOT_READY';
ROLLBACK TRANSACTION;
"@

$sealContenderSql = @"
:On Error exit
IF DB_NAME() <> N'$dataProbeDatabase'
    THROW 52053, N'O contender content/seal aceita somente o banco local V2.', 1;
SET NOCOUNT ON;
SET XACT_ABORT ON;
SET LOCK_TIMEOUT 0;
DECLARE @release_id BIGINT = (
    SELECT reference_release_id FROM ref.reference_release
    WHERE family_code = N'PICK_STATUS' AND scope_code = N'$contentProbeScope'
      AND release_version = N'content-seal-v1'
);
DECLARE @observed_error INT = 0;
BEGIN TRY
    BEGIN TRANSACTION;
    INSERT INTO ref.reference_import_receipt (
        reference_release_id, contract_version, content_fingerprint,
        imported_row_count, imported_byte_count, importer_role
    ) VALUES (
        @release_id, N'governed-references-v1', REPLICATE('a', 64), 1, 256,
        N'REFERENCE_IMPORTER'
    );
    COMMIT TRANSACTION;
END TRY
BEGIN CATCH
    SET @observed_error = ERROR_NUMBER();
    IF XACT_STATE() <> 0 ROLLBACK TRANSACTION;
END CATCH;
IF @observed_error <> 1222
    THROW 52054, N'O seal não sofreu contenção no lock físico da release.', 1;
PRINT N'CONTENT_SEAL_CONTENTION_CONFIRMED';
"@

$sealRetrySql = @"
:On Error exit
IF DB_NAME() <> N'$dataProbeDatabase'
    THROW 52055, N'O retry content/seal aceita somente o banco local V2.', 1;
SET NOCOUNT ON;
SET XACT_ABORT ON;
DECLARE @release_id BIGINT = (
    SELECT reference_release_id FROM ref.reference_release
    WHERE family_code = N'PICK_STATUS' AND scope_code = N'$contentProbeScope'
      AND release_version = N'content-seal-v1'
);
INSERT INTO ref.reference_import_receipt (
    reference_release_id, contract_version, content_fingerprint,
    imported_row_count, imported_byte_count, importer_role
) VALUES (
    @release_id, N'governed-references-v1', REPLICATE('a', 64), 1, 256,
    N'REFERENCE_IMPORTER'
);
DECLARE @late_content_rejected BIT = 0;
BEGIN TRY
    INSERT INTO ref.status_coleta (
        reference_release_id, family_code, raw_status, normalization_version,
        canonical_status, display_label, is_terminal,
        valid_from, valid_to_exclusive, reason_code
    ) VALUES (
        @release_id, N'PICK_STATUS', N'done', N'legacy-pick-status-v1',
        N'done', N'Concluído sintético', 1, '20360101', '20370101',
        N'SYNTHETIC_CONCURRENCY'
    );
END TRY
BEGIN CATCH
    IF ERROR_NUMBER() = 51913 SET @late_content_rejected = 1; ELSE THROW;
END CATCH;
IF @late_content_rejected = 0
    THROW 52056, N'Conteúdo foi aceito depois do seal concorrente.', 1;
IF (SELECT COUNT_BIG(*) FROM ref.status_coleta WHERE reference_release_id = @release_id) <> 1
   OR NOT EXISTS (
       SELECT 1 FROM ref.reference_import_receipt WHERE reference_release_id = @release_id
   ) THROW 52057, N'Estado final do probe content/seal divergiu.', 1;
PRINT N'CONTENT_SEAL_RETRY_AND_REVERSE_GUARD_CONFIRMED';
"@

$contentCleanupSql = @"
:On Error exit
IF DB_NAME() <> N'$dataProbeDatabase'
    THROW 52058, N'O cleanup content/seal aceita somente o banco local V2.', 1;
SET NOCOUNT ON;
SET XACT_ABORT ON;
DECLARE @release_id BIGINT = (
    SELECT reference_release_id FROM ref.reference_release
    WHERE family_code = N'PICK_STATUS' AND scope_code = N'$contentProbeScope'
      AND release_version = N'content-seal-v1'
);
IF @release_id IS NOT NULL
BEGIN
    BEGIN TRANSACTION;
    DISABLE TRIGGER ref.trg_reference_import_receipt_immutable
        ON ref.reference_import_receipt;
    DISABLE TRIGGER ref.trg_status_coleta_insert_guard ON ref.status_coleta;
    DISABLE TRIGGER ref.trg_reference_release_immutable ON ref.reference_release;
    DELETE FROM ref.reference_import_receipt WHERE reference_release_id = @release_id;
    DELETE FROM ref.status_coleta WHERE reference_release_id = @release_id;
    DELETE FROM ref.reference_release WHERE reference_release_id = @release_id;
    ENABLE TRIGGER ref.trg_reference_import_receipt_immutable
        ON ref.reference_import_receipt;
    ENABLE TRIGGER ref.trg_status_coleta_insert_guard ON ref.status_coleta;
    ENABLE TRIGGER ref.trg_reference_release_immutable ON ref.reference_release;
    COMMIT TRANSACTION;
END;
IF EXISTS (
    SELECT 1 FROM ref.reference_release
    WHERE family_code = N'PICK_STATUS' AND scope_code = N'$contentProbeScope'
) THROW 52059, N'O cleanup content/seal não removeu a fixture sintética.', 1;
PRINT N'CONTENT_SEAL_FIXTURE_REMOVED';
"@

$dependencyProbeScope = 'LOCAL_SYNTHETIC_DEP_' + [Guid]::NewGuid().ToString('N')
$dependencyScopeA = $dependencyProbeScope + '_A'
$dependencyScopeB = $dependencyProbeScope + '_B'
$ratifyReadyResource = 'V2_REF_DEP_RATIFY_' + [Guid]::NewGuid().ToString('N')
$revokeReadyResource = 'V2_REF_DEP_REVOKE_' + [Guid]::NewGuid().ToString('N')
$dependencySetupSql = @"
:On Error exit
IF DB_NAME() <> N'$dataProbeDatabase'
    THROW 52063, N'O setup de dependência aceita somente o banco local V2.', 1;
SET NOCOUNT ON;
SET XACT_ABORT ON;
DECLARE @branch_a BIGINT;
DECLARE @attribution_a BIGINT;
DECLARE @branch_b BIGINT;
DECLARE @attribution_b BIGINT;

INSERT INTO ref.reference_release (
    family_code, scope_code, release_version, schema_version, source_kind,
    source_artifact_ref, source_fingerprint, source_row_count, valid_from,
    valid_to_exclusive, reason_code, author_role
) VALUES (
    N'BRANCH_OPERATIONS', N'$dependencyScopeA', N'branch-a-v1', 1,
    N'OWNER_AUTHORED', N'SYNTHETIC_DEP_BRANCH_A', REPLICATE('b', 64), 1,
    '20360101', '20370101', N'SYNTHETIC_CONCURRENCY', N'REFERENCE_AUTHOR'
);
SET @branch_a = CONVERT(BIGINT, SCOPE_IDENTITY());
INSERT INTO ref.filial_operacional VALUES (
    @branch_a, N'BRANCH_OPERATIONS', N'BRANCH_A', N'Filial sintética A',
    N'SYNTHETIC_CONCURRENCY'
);
INSERT INTO ref.reference_import_receipt VALUES (
    @branch_a, N'governed-references-v1', REPLICATE('b', 64), 1, 128,
    N'REFERENCE_IMPORTER', SYSUTCDATETIME()
);
INSERT INTO ref.reference_release_ratification (
    reference_release_id, activation_scope, approver_role,
    approval_evidence_ref, approval_fingerprint
) VALUES (
    @branch_a, N'SHADOW', N'REFERENCE_APPROVER',
    N'SYNTHETIC_DEP_BRANCH_A_APPROVAL', REPLICATE('1', 64)
);
INSERT INTO ref.reference_release (
    family_code, scope_code, release_version, schema_version, source_kind,
    source_artifact_ref, source_fingerprint, source_row_count, valid_from,
    valid_to_exclusive, reason_code, author_role
) VALUES (
    N'BRANCH_ATTRIBUTION', N'$dependencyScopeA', N'attribution-a-v1', 1,
    N'OWNER_AUTHORED', N'SYNTHETIC_DEP_ATTR_A', REPLICATE('c', 64), 1,
    '20360101', '20370101', N'SYNTHETIC_CONCURRENCY', N'REFERENCE_AUTHOR'
);
SET @attribution_a = CONVERT(BIGINT, SCOPE_IDENTITY());
INSERT INTO ref.atribuicao_filial VALUES (
    @attribution_a, N'BRANCH_ATTRIBUTION', REPLICATE('a', 64),
    N'hmac-sha256-synthetic-v1', @branch_a, N'BRANCH_A',
    '20360101', '20370101', N'SYNTHETIC_CONCURRENCY'
);
INSERT INTO ref.reference_import_receipt VALUES (
    @attribution_a, N'governed-references-v1', REPLICATE('c', 64), 1, 128,
    N'REFERENCE_IMPORTER', SYSUTCDATETIME()
);

INSERT INTO ref.reference_release (
    family_code, scope_code, release_version, schema_version, source_kind,
    source_artifact_ref, source_fingerprint, source_row_count, valid_from,
    valid_to_exclusive, reason_code, author_role
) VALUES (
    N'BRANCH_OPERATIONS', N'$dependencyScopeB', N'branch-b-v1', 1,
    N'OWNER_AUTHORED', N'SYNTHETIC_DEP_BRANCH_B', REPLICATE('d', 64), 1,
    '20360101', '20370101', N'SYNTHETIC_CONCURRENCY', N'REFERENCE_AUTHOR'
);
SET @branch_b = CONVERT(BIGINT, SCOPE_IDENTITY());
INSERT INTO ref.filial_operacional VALUES (
    @branch_b, N'BRANCH_OPERATIONS', N'BRANCH_B', N'Filial sintética B',
    N'SYNTHETIC_CONCURRENCY'
);
INSERT INTO ref.reference_import_receipt VALUES (
    @branch_b, N'governed-references-v1', REPLICATE('d', 64), 1, 128,
    N'REFERENCE_IMPORTER', SYSUTCDATETIME()
);
INSERT INTO ref.reference_release_ratification (
    reference_release_id, activation_scope, approver_role,
    approval_evidence_ref, approval_fingerprint
) VALUES (
    @branch_b, N'SHADOW', N'REFERENCE_APPROVER',
    N'SYNTHETIC_DEP_BRANCH_B_APPROVAL', REPLICATE('2', 64)
);
INSERT INTO ref.reference_release (
    family_code, scope_code, release_version, schema_version, source_kind,
    source_artifact_ref, source_fingerprint, source_row_count, valid_from,
    valid_to_exclusive, reason_code, author_role
) VALUES (
    N'BRANCH_ATTRIBUTION', N'$dependencyScopeB', N'attribution-b-v1', 1,
    N'OWNER_AUTHORED', N'SYNTHETIC_DEP_ATTR_B', REPLICATE('e', 64), 1,
    '20360101', '20370101', N'SYNTHETIC_CONCURRENCY', N'REFERENCE_AUTHOR'
);
SET @attribution_b = CONVERT(BIGINT, SCOPE_IDENTITY());
INSERT INTO ref.atribuicao_filial VALUES (
    @attribution_b, N'BRANCH_ATTRIBUTION', REPLICATE('b', 64),
    N'hmac-sha256-synthetic-v1', @branch_b, N'BRANCH_B',
    '20360101', '20370101', N'SYNTHETIC_CONCURRENCY'
);
INSERT INTO ref.reference_import_receipt VALUES (
    @attribution_b, N'governed-references-v1', REPLICATE('e', 64), 1, 128,
    N'REFERENCE_IMPORTER', SYSUTCDATETIME()
);
PRINT N'DEPENDENCY_RACE_SETUP_READY';
"@

$ratifyHolderSql = @"
:On Error exit
SET NOCOUNT ON;
SET XACT_ABORT ON;
DECLARE @release_id BIGINT = (
    SELECT reference_release_id FROM ref.reference_release
    WHERE family_code = N'BRANCH_ATTRIBUTION' AND scope_code = N'$dependencyScopeA'
);
BEGIN TRANSACTION;
INSERT INTO ref.reference_release_ratification (
    reference_release_id, activation_scope, approver_role,
    approval_evidence_ref, approval_fingerprint
) VALUES (
    @release_id, N'SHADOW', N'REFERENCE_APPROVER',
    N'SYNTHETIC_DEP_ATTR_A_APPROVAL', REPLICATE('3', 64)
);
DECLARE @ready_lock_result INT;
EXEC @ready_lock_result = sys.sp_getapplock
    @Resource = N'$ratifyReadyResource', @LockMode = 'Exclusive',
    @LockOwner = 'Transaction', @LockTimeout = 5000, @DbPrincipal = 'public';
IF @ready_lock_result < 0 THROW 52064, N'Holder ratify não sinalizou prontidão.', 1;
WAITFOR DELAY '00:00:06';
COMMIT TRANSACTION;
PRINT N'DEPENDENCY_RATIFICATION_COMMITTED';
"@

$ratifyReadyProbeSql = @"
:On Error exit
SET NOCOUNT ON;
BEGIN TRANSACTION;
DECLARE @result INT;
EXEC @result = sys.sp_getapplock
    @Resource = N'$ratifyReadyResource', @LockMode = 'Exclusive',
    @LockOwner = 'Transaction', @LockTimeout = 0, @DbPrincipal = 'public';
IF @result = -1 PRINT N'DEPENDENCY_RATIFICATION_LOCK_HELD';
ELSE IF @result < 0 THROW 52065, N'Prontidão ratify retornou estado inesperado.', 1;
ELSE PRINT N'DEPENDENCY_RATIFICATION_NOT_READY';
ROLLBACK TRANSACTION;
"@

$revokeContenderSql = @"
:On Error exit
SET NOCOUNT ON;
SET XACT_ABORT ON;
SET LOCK_TIMEOUT 0;
DECLARE @release_id BIGINT = (
    SELECT reference_release_id FROM ref.reference_release
    WHERE family_code = N'BRANCH_OPERATIONS' AND scope_code = N'$dependencyScopeA'
);
DECLARE @error INT = 0;
BEGIN TRY
    BEGIN TRANSACTION;
    INSERT INTO ref.reference_release_revocation (
        reference_release_id, activation_scope, revoker_role, reason_code,
        evidence_ref, evidence_fingerprint
    ) VALUES (
        @release_id, N'SHADOW', N'REFERENCE_APPROVER', N'SYNTHETIC_REVOKE',
        N'SYNTHETIC_DEP_BRANCH_A_REVOCATION', REPLICATE('4', 64)
    );
    COMMIT TRANSACTION;
END TRY
BEGIN CATCH
    SET @error = ERROR_NUMBER();
    IF XACT_STATE() <> 0 ROLLBACK TRANSACTION;
END CATCH;
IF @error <> 1222 THROW 52066, N'Revogação não bloqueou atrás da ratificação.', 1;
PRINT N'DEPENDENCY_REVOKE_BLOCKED_BY_RATIFICATION';
"@

$revokeRetrySql = @"
:On Error exit
SET NOCOUNT ON;
SET XACT_ABORT ON;
DECLARE @release_id BIGINT = (
    SELECT reference_release_id FROM ref.reference_release
    WHERE family_code = N'BRANCH_OPERATIONS' AND scope_code = N'$dependencyScopeA'
);
DECLARE @rejected BIT = 0;
BEGIN TRY
    BEGIN TRANSACTION;
    INSERT INTO ref.reference_release_revocation (
        reference_release_id, activation_scope, revoker_role, reason_code,
        evidence_ref, evidence_fingerprint
    ) VALUES (
        @release_id, N'SHADOW', N'REFERENCE_APPROVER', N'SYNTHETIC_REVOKE',
        N'SYNTHETIC_DEP_BRANCH_A_REVOCATION', REPLICATE('4', 64)
    );
    COMMIT TRANSACTION;
END TRY
BEGIN CATCH
    IF ERROR_NUMBER() = 51948 SET @rejected = 1; ELSE THROW;
    IF XACT_STATE() <> 0 ROLLBACK TRANSACTION;
END CATCH;
IF @rejected = 0 THROW 52067, N'Revogação venceu após ratificação ativa.', 1;
PRINT N'DEPENDENCY_REVOKE_RETRY_REJECTED';
"@

$revokeHolderSql = @"
:On Error exit
SET NOCOUNT ON;
SET XACT_ABORT ON;
DECLARE @release_id BIGINT = (
    SELECT reference_release_id FROM ref.reference_release
    WHERE family_code = N'BRANCH_OPERATIONS' AND scope_code = N'$dependencyScopeB'
);
BEGIN TRANSACTION;
INSERT INTO ref.reference_release_revocation (
    reference_release_id, activation_scope, revoker_role, reason_code,
    evidence_ref, evidence_fingerprint
) VALUES (
    @release_id, N'SHADOW', N'REFERENCE_APPROVER', N'SYNTHETIC_REVOKE',
    N'SYNTHETIC_DEP_BRANCH_B_REVOCATION', REPLICATE('5', 64)
);
DECLARE @ready_lock_result INT;
EXEC @ready_lock_result = sys.sp_getapplock
    @Resource = N'$revokeReadyResource', @LockMode = 'Exclusive',
    @LockOwner = 'Transaction', @LockTimeout = 5000, @DbPrincipal = 'public';
IF @ready_lock_result < 0 THROW 52068, N'Holder revoke não sinalizou prontidão.', 1;
WAITFOR DELAY '00:00:06';
COMMIT TRANSACTION;
PRINT N'DEPENDENCY_REVOCATION_COMMITTED';
"@

$revokeReadyProbeSql = @"
:On Error exit
SET NOCOUNT ON;
BEGIN TRANSACTION;
DECLARE @result INT;
EXEC @result = sys.sp_getapplock
    @Resource = N'$revokeReadyResource', @LockMode = 'Exclusive',
    @LockOwner = 'Transaction', @LockTimeout = 0, @DbPrincipal = 'public';
IF @result = -1 PRINT N'DEPENDENCY_REVOCATION_LOCK_HELD';
ELSE IF @result < 0 THROW 52069, N'Prontidão revoke retornou estado inesperado.', 1;
ELSE PRINT N'DEPENDENCY_REVOCATION_NOT_READY';
ROLLBACK TRANSACTION;
"@

$ratifyContenderSql = @"
:On Error exit
SET NOCOUNT ON;
SET XACT_ABORT ON;
SET LOCK_TIMEOUT 0;
DECLARE @release_id BIGINT = (
    SELECT reference_release_id FROM ref.reference_release
    WHERE family_code = N'BRANCH_ATTRIBUTION' AND scope_code = N'$dependencyScopeB'
);
DECLARE @error INT = 0;
BEGIN TRY
    BEGIN TRANSACTION;
    INSERT INTO ref.reference_release_ratification (
        reference_release_id, activation_scope, approver_role,
        approval_evidence_ref, approval_fingerprint
    ) VALUES (
        @release_id, N'SHADOW', N'REFERENCE_APPROVER',
        N'SYNTHETIC_DEP_ATTR_B_APPROVAL', REPLICATE('6', 64)
    );
    COMMIT TRANSACTION;
END TRY
BEGIN CATCH
    SET @error = ERROR_NUMBER();
    IF XACT_STATE() <> 0 ROLLBACK TRANSACTION;
END CATCH;
IF @error <> 1222 THROW 52070, N'Ratificação não bloqueou atrás da revogação.', 1;
PRINT N'DEPENDENCY_RATIFY_BLOCKED_BY_REVOCATION';
"@

$ratifyRetrySql = @"
:On Error exit
SET NOCOUNT ON;
SET XACT_ABORT ON;
DECLARE @release_id BIGINT = (
    SELECT reference_release_id FROM ref.reference_release
    WHERE family_code = N'BRANCH_ATTRIBUTION' AND scope_code = N'$dependencyScopeB'
);
DECLARE @rejected BIT = 0;
BEGIN TRY
    BEGIN TRANSACTION;
    INSERT INTO ref.reference_release_ratification (
        reference_release_id, activation_scope, approver_role,
        approval_evidence_ref, approval_fingerprint
    ) VALUES (
        @release_id, N'SHADOW', N'REFERENCE_APPROVER',
        N'SYNTHETIC_DEP_ATTR_B_APPROVAL', REPLICATE('6', 64)
    );
    COMMIT TRANSACTION;
END TRY
BEGIN CATCH
    IF ERROR_NUMBER() = 51945 SET @rejected = 1; ELSE THROW;
    IF XACT_STATE() <> 0 ROLLBACK TRANSACTION;
END CATCH;
IF @rejected = 0 THROW 52071, N'Ratificação venceu após revogação da filial.', 1;
PRINT N'DEPENDENCY_RATIFY_RETRY_REJECTED';
"@

$dependencyCleanupSql = @"
:On Error exit
IF DB_NAME() <> N'$dataProbeDatabase'
    THROW 52072, N'O cleanup de dependência aceita somente o banco local V2.', 1;
SET NOCOUNT ON;
SET XACT_ABORT ON;
DECLARE @release_ids TABLE (reference_release_id BIGINT NOT NULL PRIMARY KEY);
INSERT INTO @release_ids
SELECT reference_release_id FROM ref.reference_release
WHERE scope_code IN (N'$dependencyScopeA', N'$dependencyScopeB');
IF EXISTS (SELECT 1 FROM @release_ids)
BEGIN
    BEGIN TRANSACTION;
    DISABLE TRIGGER ref.trg_reference_revocation_immutable
        ON ref.reference_release_revocation;
    DISABLE TRIGGER ref.trg_reference_ratification_immutable
        ON ref.reference_release_ratification;
    DISABLE TRIGGER ref.trg_reference_import_receipt_immutable
        ON ref.reference_import_receipt;
    DISABLE TRIGGER ref.trg_atribuicao_filial_insert_guard ON ref.atribuicao_filial;
    DISABLE TRIGGER ref.trg_filial_operacional_insert_guard ON ref.filial_operacional;
    DISABLE TRIGGER ref.trg_reference_release_immutable ON ref.reference_release;
    DELETE FROM ref.reference_release_revocation
    WHERE reference_release_id IN (SELECT reference_release_id FROM @release_ids);
    DELETE FROM ref.reference_release_ratification
    WHERE reference_release_id IN (SELECT reference_release_id FROM @release_ids);
    DELETE FROM ref.reference_import_receipt
    WHERE reference_release_id IN (SELECT reference_release_id FROM @release_ids);
    DELETE FROM ref.atribuicao_filial
    WHERE reference_release_id IN (SELECT reference_release_id FROM @release_ids);
    DELETE FROM ref.filial_operacional
    WHERE reference_release_id IN (SELECT reference_release_id FROM @release_ids);
    DELETE FROM ref.reference_release
    WHERE reference_release_id IN (SELECT reference_release_id FROM @release_ids);
    ENABLE TRIGGER ref.trg_reference_revocation_immutable
        ON ref.reference_release_revocation;
    ENABLE TRIGGER ref.trg_reference_ratification_immutable
        ON ref.reference_release_ratification;
    ENABLE TRIGGER ref.trg_reference_import_receipt_immutable
        ON ref.reference_import_receipt;
    ENABLE TRIGGER ref.trg_atribuicao_filial_insert_guard ON ref.atribuicao_filial;
    ENABLE TRIGGER ref.trg_filial_operacional_insert_guard ON ref.filial_operacional;
    ENABLE TRIGGER ref.trg_reference_release_immutable ON ref.reference_release;
    COMMIT TRANSACTION;
END;
IF EXISTS (
    SELECT 1 FROM ref.reference_release
    WHERE scope_code IN (N'$dependencyScopeA', N'$dependencyScopeB')
) THROW 52073, N'O cleanup de dependência não removeu as fixtures.', 1;
PRINT N'DEPENDENCY_RACE_FIXTURES_REMOVED';
"@

$dataProbeCreateSql = @"
:On Error exit
USE master;
IF DB_ID(N'$dataProbeDatabase') IS NOT NULL
    THROW 52074, N'O banco efêmero de referências já existe.', 1;
CREATE DATABASE [$dataProbeDatabase];
PRINT N'REFERENCES_EPHEMERAL_DATABASE_CREATED';
"@

$dataProbeDropSql = @"
:On Error exit
USE master;
IF DB_ID(N'$dataProbeDatabase') IS NOT NULL
BEGIN
    ALTER DATABASE [$dataProbeDatabase] SET SINGLE_USER WITH ROLLBACK IMMEDIATE;
    DROP DATABASE [$dataProbeDatabase];
END;
IF DB_ID(N'$dataProbeDatabase') IS NOT NULL
    THROW 52075, N'O banco efêmero de referências não foi removido.', 1;
PRINT N'REFERENCES_EPHEMERAL_DATABASE_DROPPED';
"@

try {
    $null = New-Item -ItemType Directory -Path $probeDirectory
    $holderPath = Join-Path $probeDirectory 'holder.sql'
    $contenderPath = Join-Path $probeDirectory 'contender.sql'
    $otherScopePath = Join-Path $probeDirectory 'other-scope.sql'
    $reacquirePath = Join-Path $probeDirectory 'reacquire.sql'
    $holderOutputPath = Join-Path $probeDirectory 'holder.out'
    $holderErrorPath = Join-Path $probeDirectory 'holder.err'
    $dataProbeCreatePath = Join-Path $probeDirectory 'data-probe-create.sql'
    $dataProbeDropPath = Join-Path $probeDirectory 'data-probe-drop.sql'

    [IO.File]::WriteAllText($holderPath, $holderSql, [Text.UTF8Encoding]::new($false))
    [IO.File]::WriteAllText($contenderPath, $contenderSql, [Text.UTF8Encoding]::new($false))
    [IO.File]::WriteAllText($otherScopePath, $otherScopeSql, [Text.UTF8Encoding]::new($false))
    [IO.File]::WriteAllText($reacquirePath, $reacquireSql, [Text.UTF8Encoding]::new($false))
    [IO.File]::WriteAllText(
        $dataProbeCreatePath, $dataProbeCreateSql, [Text.UTF8Encoding]::new($false)
    )
    [IO.File]::WriteAllText(
        $dataProbeDropPath, $dataProbeDropSql, [Text.UTF8Encoding]::new($false)
    )

    $holderProcess = Start-Process -FilePath $sqlcmd.Source -WindowStyle Hidden -PassThru `
        -ArgumentList @(
            '-S', 'localhost', '-C', '-E', '-f', '65001', '-d', $targetDatabase,
            '-i', $holderPath, '-b', '-h', '-1', '-W'
        ) `
        -RedirectStandardOutput $holderOutputPath -RedirectStandardError $holderErrorPath

    $readyDeadline = [DateTimeOffset]::UtcNow.AddSeconds(5)
    $contentionConfirmed = $false
    $contenderOutput = @()
    while (-not $contentionConfirmed -and [DateTimeOffset]::UtcNow -lt $readyDeadline) {
        if ($holderProcess.HasExited) {
            $holderOutput = Read-ProbeOutput @($holderOutputPath, $holderErrorPath)
            throw "A sessão holder terminou antes da contenção. $holderOutput"
        }
        $contenderOutput = @(
            & $sqlcmd.Source -S localhost -C -E -f 65001 -d $targetDatabase `
                -i $contenderPath -b -h -1 -W
        )
        if ($LASTEXITCODE -ne 0) {
            throw "O contender de referências falhou. $($contenderOutput -join ' ')"
        }
        $contentionConfirmed = ($contenderOutput -join "`n").IndexOf(
            'REFERENCES_CONTENTION_CONFIRMED',
            [StringComparison]::Ordinal
        ) -ge 0
        if (-not $contentionConfirmed) { Start-Sleep -Milliseconds 100 }
    }
    if (-not $contentionConfirmed) {
        throw "A segunda sessão não confirmou contenção. $($contenderOutput -join ' ')"
    }

    $otherScopeOutput = @(
        & $sqlcmd.Source -S localhost -C -E -f 65001 -d $targetDatabase `
            -i $otherScopePath -b -h -1 -W
    )
    if ($LASTEXITCODE -ne 0 `
            -or ($otherScopeOutput -join "`n").IndexOf(
                'REFERENCES_OTHER_SCOPE_ISOLATED', [StringComparison]::Ordinal
            ) -lt 0) {
        throw "O namespace de outro scope não ficou isolado. $($otherScopeOutput -join ' ')"
    }

    if (-not $holderProcess.WaitForExit(12000)) {
        throw 'A sessão holder de referências não terminou no prazo.'
    }
    $holderProcess.WaitForExit()
    $holderOutput = Read-ProbeOutput @($holderOutputPath, $holderErrorPath)
    if ($holderProcess.ExitCode -ne 0 `
            -or $holderOutput.IndexOf(
                'REFERENCES_LOCK_RELEASED', [StringComparison]::Ordinal
            ) -lt 0) {
        throw "A sessão holder de referências falhou. $holderOutput"
    }

    $reacquireOutput = @(
        & $sqlcmd.Source -S localhost -C -E -f 65001 -d $targetDatabase `
            -i $reacquirePath -b -h -1 -W
    )
    if ($LASTEXITCODE -ne 0 `
            -or ($reacquireOutput -join "`n").IndexOf(
                'REFERENCES_LOCK_REACQUIRED_AFTER_ROLLBACK',
                [StringComparison]::Ordinal
            ) -lt 0) {
        throw "O lock de referências não foi readquirido. $($reacquireOutput -join ' ')"
    }

    $dataProbeCreateOutput = @(
        & $sqlcmd.Source -S localhost -C -E -f 65001 -d master `
            -i $dataProbeCreatePath -b -h -1 -W
    )
    if ($LASTEXITCODE -eq 0) { $dataProbeDatabaseCreated = $true }
    if (-not $dataProbeDatabaseCreated `
            -or ($dataProbeCreateOutput -join "`n").IndexOf(
                'REFERENCES_EPHEMERAL_DATABASE_CREATED', [StringComparison]::Ordinal
            ) -lt 0) {
        throw "A criação do banco efêmero de referências falhou. $($dataProbeCreateOutput -join ' ')"
    }
    foreach ($foundationMigrationPath in @(
            $schemaFoundationMigrationPath, $migrationPath
        )) {
        $migrationOutput = @(
            & $sqlcmd.Source -S localhost -C -E -f 65001 -d $dataProbeDatabase `
                -i $foundationMigrationPath -b -h -1 -W
        )
        if ($LASTEXITCODE -ne 0) {
            throw "A migration do banco efêmero falhou. $($migrationOutput -join ' ')"
        }
    }

    $contentSetupPath = Join-Path $probeDirectory 'content-setup.sql'
    $contentHolderPath = Join-Path $probeDirectory 'content-holder.sql'
    $contentReadyProbePath = Join-Path $probeDirectory 'content-ready-probe.sql'
    $sealContenderPath = Join-Path $probeDirectory 'seal-contender.sql'
    $sealRetryPath = Join-Path $probeDirectory 'seal-retry.sql'
    $contentCleanupPath = Join-Path $probeDirectory 'content-cleanup.sql'
    $contentHolderOutputPath = Join-Path $probeDirectory 'content-holder.out'
    $contentHolderErrorPath = Join-Path $probeDirectory 'content-holder.err'
    [IO.File]::WriteAllText(
        $contentSetupPath, $contentSetupSql, [Text.UTF8Encoding]::new($false)
    )
    [IO.File]::WriteAllText(
        $contentHolderPath, $contentHolderSql, [Text.UTF8Encoding]::new($false)
    )
    [IO.File]::WriteAllText(
        $contentReadyProbePath, $contentReadyProbeSql, [Text.UTF8Encoding]::new($false)
    )
    [IO.File]::WriteAllText(
        $sealContenderPath, $sealContenderSql, [Text.UTF8Encoding]::new($false)
    )
    [IO.File]::WriteAllText(
        $sealRetryPath, $sealRetrySql, [Text.UTF8Encoding]::new($false)
    )
    [IO.File]::WriteAllText(
        $contentCleanupPath, $contentCleanupSql, [Text.UTF8Encoding]::new($false)
    )

    $contentProbeCreated = $true
    $contentSetupOutput = @(
        & $sqlcmd.Source -S localhost -C -E -f 65001 -d $dataProbeDatabase `
            -i $contentSetupPath -b -h -1 -W
    )
    if ($LASTEXITCODE -ne 0 `
            -or ($contentSetupOutput -join "`n").IndexOf(
                'CONTENT_SEAL_SETUP_READY', [StringComparison]::Ordinal
            ) -lt 0) {
        throw "O setup content/seal falhou. $($contentSetupOutput -join ' ')"
    }

    $contentHolderProcess = Start-Process -FilePath $sqlcmd.Source `
        -WindowStyle Hidden -PassThru `
        -ArgumentList @(
            '-S', 'localhost', '-C', '-E', '-f', '65001', '-d', $dataProbeDatabase,
            '-i', $contentHolderPath, '-b', '-h', '-1', '-W'
        ) `
        -RedirectStandardOutput $contentHolderOutputPath `
        -RedirectStandardError $contentHolderErrorPath

    $contentReadyDeadline = [DateTimeOffset]::UtcNow.AddSeconds(5)
    $contentReady = $false
    while (-not $contentReady -and [DateTimeOffset]::UtcNow -lt $contentReadyDeadline) {
        if ($contentHolderProcess.HasExited) {
            $contentHolderOutput = Read-ProbeOutput @(
                $contentHolderOutputPath, $contentHolderErrorPath
            )
            throw "O holder content/seal terminou cedo. $contentHolderOutput"
        }
        $contentReadyOutput = @(
            & $sqlcmd.Source -S localhost -C -E -f 65001 -d $dataProbeDatabase `
                -i $contentReadyProbePath -b -h -1 -W
        )
        if ($LASTEXITCODE -ne 0) {
            throw "O probe de prontidão content/seal falhou. $($contentReadyOutput -join ' ')"
        }
        $contentReady = ($contentReadyOutput -join "`n").IndexOf(
            'CONTENT_PARENT_LOCK_HELD', [StringComparison]::Ordinal
        ) -ge 0
        if (-not $contentReady) { Start-Sleep -Milliseconds 100 }
    }
    if (-not $contentReady) {
        throw 'O holder content/seal não confirmou o lock físico no prazo.'
    }

    $sealContenderOutput = @(
        & $sqlcmd.Source -S localhost -C -E -f 65001 -d $dataProbeDatabase `
            -i $sealContenderPath -b -h -1 -W
    )
    if ($LASTEXITCODE -ne 0 `
            -or ($sealContenderOutput -join "`n").IndexOf(
                'CONTENT_SEAL_CONTENTION_CONFIRMED', [StringComparison]::Ordinal
            ) -lt 0) {
        throw "A corrida content/seal não bloqueou. $($sealContenderOutput -join ' ')"
    }

    if (-not $contentHolderProcess.WaitForExit(12000)) {
        throw 'O holder content/seal não terminou no prazo.'
    }
    $contentHolderProcess.WaitForExit()
    $contentHolderOutput = Read-ProbeOutput @(
        $contentHolderOutputPath, $contentHolderErrorPath
    )
    if ($contentHolderProcess.ExitCode -ne 0 `
            -or $contentHolderOutput.IndexOf(
                'CONTENT_COMMITTED_AFTER_HOLD', [StringComparison]::Ordinal
            ) -lt 0) {
        throw "O holder content/seal falhou. $contentHolderOutput"
    }

    $sealRetryOutput = @(
        & $sqlcmd.Source -S localhost -C -E -f 65001 -d $dataProbeDatabase `
            -i $sealRetryPath -b -h -1 -W
    )
    if ($LASTEXITCODE -ne 0 `
            -or ($sealRetryOutput -join "`n").IndexOf(
                'CONTENT_SEAL_RETRY_AND_REVERSE_GUARD_CONFIRMED',
                [StringComparison]::Ordinal
            ) -lt 0) {
        throw "O retry/reverse guard content/seal falhou. $($sealRetryOutput -join ' ')"
    }

    $dependencySetupPath = Join-Path $probeDirectory 'dependency-setup.sql'
    $ratifyHolderPath = Join-Path $probeDirectory 'dependency-ratify-holder.sql'
    $ratifyReadyProbePath = Join-Path $probeDirectory 'dependency-ratify-ready.sql'
    $revokeContenderPath = Join-Path $probeDirectory 'dependency-revoke-contender.sql'
    $revokeRetryPath = Join-Path $probeDirectory 'dependency-revoke-retry.sql'
    $revokeHolderPath = Join-Path $probeDirectory 'dependency-revoke-holder.sql'
    $revokeReadyProbePath = Join-Path $probeDirectory 'dependency-revoke-ready.sql'
    $ratifyContenderPath = Join-Path $probeDirectory 'dependency-ratify-contender.sql'
    $ratifyRetryPath = Join-Path $probeDirectory 'dependency-ratify-retry.sql'
    $dependencyCleanupPath = Join-Path $probeDirectory 'dependency-cleanup.sql'
    $dependencyFiles = @(
        @($dependencySetupPath, $dependencySetupSql),
        @($ratifyHolderPath, $ratifyHolderSql),
        @($ratifyReadyProbePath, $ratifyReadyProbeSql),
        @($revokeContenderPath, $revokeContenderSql),
        @($revokeRetryPath, $revokeRetrySql),
        @($revokeHolderPath, $revokeHolderSql),
        @($revokeReadyProbePath, $revokeReadyProbeSql),
        @($ratifyContenderPath, $ratifyContenderSql),
        @($ratifyRetryPath, $ratifyRetrySql),
        @($dependencyCleanupPath, $dependencyCleanupSql)
    )
    foreach ($dependencyFile in $dependencyFiles) {
        [IO.File]::WriteAllText(
            $dependencyFile[0], $dependencyFile[1], [Text.UTF8Encoding]::new($false)
        )
    }

    $dependencyProbeCreated = $true
    $dependencySetupOutput = @(
        & $sqlcmd.Source -S localhost -C -E -f 65001 -d $dataProbeDatabase `
            -i $dependencySetupPath -b -h -1 -W
    )
    if ($LASTEXITCODE -ne 0 `
            -or ($dependencySetupOutput -join "`n").IndexOf(
                'DEPENDENCY_RACE_SETUP_READY', [StringComparison]::Ordinal
            ) -lt 0) {
        throw "O setup da corrida de dependência falhou. $($dependencySetupOutput -join ' ')"
    }

    Invoke-BlockingRaceProbe -Name 'dependency-ratify-wins' `
        -HolderPath $ratifyHolderPath -ReadyProbePath $ratifyReadyProbePath `
        -ReadyMarker 'DEPENDENCY_RATIFICATION_LOCK_HELD' `
        -ContenderPath $revokeContenderPath `
        -ContenderMarker 'DEPENDENCY_REVOKE_BLOCKED_BY_RATIFICATION' `
        -HolderMarker 'DEPENDENCY_RATIFICATION_COMMITTED' `
        -RetryPath $revokeRetryPath -RetryMarker 'DEPENDENCY_REVOKE_RETRY_REJECTED' `
        -OutputDirectory $probeDirectory -DatabaseName $dataProbeDatabase

    Invoke-BlockingRaceProbe -Name 'dependency-revoke-wins' `
        -HolderPath $revokeHolderPath -ReadyProbePath $revokeReadyProbePath `
        -ReadyMarker 'DEPENDENCY_REVOCATION_LOCK_HELD' `
        -ContenderPath $ratifyContenderPath `
        -ContenderMarker 'DEPENDENCY_RATIFY_BLOCKED_BY_REVOCATION' `
        -HolderMarker 'DEPENDENCY_REVOCATION_COMMITTED' `
        -RetryPath $ratifyRetryPath -RetryMarker 'DEPENDENCY_RATIFY_RETRY_REJECTED' `
        -OutputDirectory $probeDirectory -DatabaseName $dataProbeDatabase
} finally {
    $cleanupFailures = [Collections.Generic.List[string]]::new()
    if ($null -ne $holderProcess -and -not $holderProcess.HasExited) {
        $holderProcess.Kill()
        $holderProcess.WaitForExit()
    }
    if ($null -ne $contentHolderProcess -and -not $contentHolderProcess.HasExited) {
        $contentHolderProcess.Kill()
        $contentHolderProcess.WaitForExit()
    }
    if ($contentProbeCreated -and $null -ne $contentCleanupPath `
            -and (Test-Path -LiteralPath $contentCleanupPath -PathType Leaf)) {
        try {
            $contentCleanupOutput = @(
                & $sqlcmd.Source -S localhost -C -E -f 65001 -d $dataProbeDatabase `
                    -i $contentCleanupPath -b -h -1 -W
            )
            if ($LASTEXITCODE -ne 0 `
                    -or ($contentCleanupOutput -join "`n").IndexOf(
                        'CONTENT_SEAL_FIXTURE_REMOVED', [StringComparison]::Ordinal
                    ) -lt 0) {
                throw "O cleanup content/seal falhou. $($contentCleanupOutput -join ' ')"
            }
        } catch {
            $cleanupFailures.Add($_.Exception.Message)
        }
    }
    if ($dependencyProbeCreated -and $null -ne $dependencyCleanupPath `
            -and (Test-Path -LiteralPath $dependencyCleanupPath -PathType Leaf)) {
        try {
            $dependencyCleanupOutput = @(
                & $sqlcmd.Source -S localhost -C -E -f 65001 -d $dataProbeDatabase `
                    -i $dependencyCleanupPath -b -h -1 -W
            )
            if ($LASTEXITCODE -ne 0 `
                    -or ($dependencyCleanupOutput -join "`n").IndexOf(
                        'DEPENDENCY_RACE_FIXTURES_REMOVED', [StringComparison]::Ordinal
                    ) -lt 0) {
                throw "O cleanup das dependências falhou. $($dependencyCleanupOutput -join ' ')"
            }
        } catch {
            $cleanupFailures.Add($_.Exception.Message)
        }
    }
    if ($null -ne $dataProbeDropPath `
            -and (Test-Path -LiteralPath $dataProbeDropPath -PathType Leaf)) {
        try {
            $dataProbeDropOutput = @(
                & $sqlcmd.Source -S localhost -C -E -f 65001 -d master `
                    -i $dataProbeDropPath -b -h -1 -W
            )
            if ($LASTEXITCODE -ne 0 `
                    -or ($dataProbeDropOutput -join "`n").IndexOf(
                        'REFERENCES_EPHEMERAL_DATABASE_DROPPED',
                        [StringComparison]::Ordinal
                    ) -lt 0) {
                throw "O descarte do banco efêmero falhou. $($dataProbeDropOutput -join ' ')"
            }
            $dataProbeDatabaseCreated = $false
        } catch {
            $cleanupFailures.Add($_.Exception.Message)
        }
    }
    if (Test-Path -LiteralPath $probeDirectory) {
        $resolvedProbeDirectory = [IO.Path]::GetFullPath((Resolve-Path $probeDirectory).Path)
        $expectedPrefix = [IO.Path]::GetFullPath(
            (Join-Path $temporaryRoot 'etl-v2-references-lock-')
        )
        if (-not $resolvedProbeDirectory.StartsWith(
                $expectedPrefix,
                [StringComparison]::OrdinalIgnoreCase
            )) {
            throw "Diretório temporário inesperado; cleanup recusado: $resolvedProbeDirectory"
        }
        Remove-Item -LiteralPath $resolvedProbeDirectory -Recurse -Force
    }
    if ($cleanupFailures.Count -gt 0) {
        throw ($cleanupFailures -join ' ')
    }
}

Write-Output (
    'Ratificação, corrida física content/seal e dois interleavings de dependência ' +
    'comprovados; fixtures e banco efêmero removidos.'
)
