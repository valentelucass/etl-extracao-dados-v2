[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$showplanScript = Join-Path $repositoryRoot `
    'database\validation\028_validate_usuarios_current_history_showplan.sql'
$targetDatabase = 'ETL_SISTEMA_V2_SHADOW'
$maximumPlanCharacters = 32MB

function Expand-SqlCmdScript {
    param([string]$Path)

    $resolvedPath = (Resolve-Path -LiteralPath $Path).Path
    $builder = [Text.StringBuilder]::new()
    foreach ($line in [IO.File]::ReadLines($resolvedPath)) {
        if ($line -match '^\s*:r\s+"?([^"\r\n]+)"?\s*$') {
            $includePath = Join-Path (Split-Path $resolvedPath -Parent) $Matches[1]
            [void]$builder.AppendLine((Expand-SqlCmdScript -Path $includePath))
        } elseif ($line -notmatch '^\s*:(?:setvar|On\s+Error)\b') {
            [void]$builder.AppendLine($line)
        }
    }
    return $builder.ToString().Replace('$(DatabaseName)', $targetDatabase)
}

function Invoke-NonQuery {
    param(
        [System.Data.SqlClient.SqlConnection]$Connection,
        [string]$CommandText
    )

    $command = $Connection.CreateCommand()
    try {
        $command.CommandTimeout = 120
        $command.CommandText = $CommandText
        [void]$command.ExecuteNonQuery()
    } finally {
        $command.Dispose()
    }
}

$connection = [System.Data.SqlClient.SqlConnection]::new(
    "Server=localhost;Database=$targetDatabase;Integrated Security=true;" `
        + 'Encrypt=true;TrustServerCertificate=true;' `
        + 'Application Name=ETL-V2-Usuarios-Showplan'
)
$showplanEnabled = $false

try {
    $connection.Open()
    $databaseCommand = $connection.CreateCommand()
    try {
        $databaseCommand.CommandText = 'SELECT DB_NAME();'
        $actualDatabase = [string]$databaseCommand.ExecuteScalar()
    } finally {
        $databaseCommand.Dispose()
    }
    if (-not $actualDatabase.Equals($targetDatabase, [StringComparison]::Ordinal)) {
        throw 'A conexão SHOWPLAN de Usuários não está no alvo local autorizado.'
    }

    $expandedScript = Expand-SqlCmdScript -Path $showplanScript
    $batches = [Text.RegularExpressions.Regex]::Split(
        $expandedScript,
        '(?im)^\s*GO\s*(?:--[^\r\n]*)?$'
    )
    $plans = [Collections.Generic.List[string]]::new()
    $capturedCharacters = 0L

    foreach ($batch in $batches) {
        $trimmedBatch = $batch.Trim()
        if ([string]::IsNullOrWhiteSpace($trimmedBatch)) {
            continue
        }
        if ($trimmedBatch.Equals('SET SHOWPLAN_XML ON;', [StringComparison]::OrdinalIgnoreCase)) {
            Invoke-NonQuery -Connection $connection -CommandText $trimmedBatch
            $showplanEnabled = $true
            continue
        }
        if ($trimmedBatch.Equals('SET SHOWPLAN_XML OFF;', [StringComparison]::OrdinalIgnoreCase)) {
            Invoke-NonQuery -Connection $connection -CommandText $trimmedBatch
            $showplanEnabled = $false
            continue
        }
        if (-not $showplanEnabled) {
            Invoke-NonQuery -Connection $connection -CommandText $trimmedBatch
            continue
        }

        $command = $connection.CreateCommand()
        try {
            $command.CommandTimeout = 120
            $command.CommandText = $trimmedBatch
            $reader = $command.ExecuteReader()
            try {
                do {
                    while ($reader.Read()) {
                        for ($column = 0; $column -lt $reader.FieldCount; $column++) {
                            if ($reader.IsDBNull($column)) {
                                continue
                            }
                            $candidate = [string]$reader.GetValue($column)
                            if (-not $candidate.StartsWith(
                                    '<ShowPlanXML',
                                    [StringComparison]::Ordinal
                                )) {
                                continue
                            }
                            $capturedCharacters += $candidate.Length
                            if ($capturedCharacters -gt $maximumPlanCharacters) {
                                throw 'SHOWPLAN de Usuários excedeu o limite local sanitizado.'
                            }
                            $plans.Add($candidate)
                        }
                    }
                } while ($reader.NextResult())
            } finally {
                $reader.Dispose()
            }
        } finally {
            $command.Dispose()
        }
    }

    $transactionCommand = $connection.CreateCommand()
    try {
        $transactionCommand.CommandText = 'SELECT @@TRANCOUNT;'
        $transactionCount = [int]$transactionCommand.ExecuteScalar()
    } finally {
        $transactionCommand.Dispose()
    }
    if ($transactionCount -ne 0) {
        throw 'O SHOWPLAN de Usuários não confirmou rollback integral.'
    }
    if ($plans.Count -lt 1) {
        throw 'O SHOWPLAN de Usuários não produziu documento compilado.'
    }

    $relationalOperators = 0
    $seekOperators = 0
    $scanOperators = 0
    $conversionWarnings = 0
    $fatalWarningKinds = [Collections.Generic.HashSet[string]]::new(
        [StringComparer]::Ordinal
    )
    $observedIndexes = [Collections.Generic.HashSet[string]]::new(
        [StringComparer]::Ordinal
    )
    $observedEntrypoints = [Collections.Generic.HashSet[string]]::new(
        [StringComparer]::Ordinal
    )
    $observedStatementPaths = [Collections.Generic.HashSet[string]]::new(
        [StringComparer]::Ordinal
    )
    $requiredStatementPaths = @(
        [pscustomobject]@{
            Label = 'stage-microbatch'
            Fragment = 'INDEX(IX_stg_usuario_record_execution_source), FORCESEEK'
            Index = 'IX_stg_usuario_record_execution_source'
        },
        [pscustomobject]@{
            Label = 'current-lookup'
            Fragment = 'FROM core.usuario WITH (INDEX(UQ_core_usuario_source), FORCESEEK)'
            Index = 'UQ_core_usuario_source'
        },
        [pscustomobject]@{
            Label = 'history-timeline'
            Fragment = 'INDEX(IX_core_usuario_history_timeline), FORCESEEK'
            Index = 'IX_core_usuario_history_timeline'
        },
        [pscustomobject]@{
            Label = 'candidate-application'
            Fragment = 'INDEX(PK_recon_usuario_candidate_application), FORCESEEK'
            Index = 'PK_recon_usuario_candidate_application'
        },
        [pscustomobject]@{
            Label = 'typed-apply-current'
            Fragment = 'LEFT JOIN core.usuario AS current_record WITH ('
            Index = 'UQ_core_usuario_source'
        }
    )
    foreach ($plan in $plans) {
        $document = [Xml.XmlDocument]::new()
        $document.PreserveWhitespace = $false
        $document.LoadXml($plan)
        $namespace = [Xml.XmlNamespaceManager]::new($document.NameTable)
        $namespace.AddNamespace('sp', 'http://schemas.microsoft.com/sqlserver/2004/07/showplan')

        $relationalOperators += @($document.SelectNodes('//sp:RelOp', $namespace)).Count
        $seekOperators += @($document.SelectNodes(
                "//sp:RelOp[contains(@PhysicalOp, 'Seek')]",
                $namespace
            )).Count
        $scanOperators += @($document.SelectNodes(
                "//sp:RelOp[contains(@PhysicalOp, 'Scan')]",
                $namespace
            )).Count
        foreach ($warning in @($document.SelectNodes('//sp:Warnings', $namespace))) {
            foreach ($attribute in @($warning.Attributes)) {
                [void]$fatalWarningKinds.Add($attribute.Name)
            }
            foreach ($child in @($warning.ChildNodes)) {
                if ($child.LocalName -eq 'PlanAffectingConvert' `
                        -and $child.Attributes['ConvertIssue'].Value -eq `
                            'Cardinality Estimate') {
                    $conversionWarnings++
                } else {
                    [void]$fatalWarningKinds.Add($child.LocalName)
                }
            }
        }
        foreach ($objectNode in @($document.SelectNodes('//sp:Object', $namespace))) {
            $databaseAttribute = $objectNode.Attributes['Database']
            if ($null -ne $databaseAttribute) {
                $databaseName = $databaseAttribute.Value.Trim('[', ']')
                if (-not $databaseName.Equals(
                        $targetDatabase,
                        [StringComparison]::OrdinalIgnoreCase
                    )) {
                    throw 'SHOWPLAN de Usuários referenciou database fora do alvo local.'
                }
            }
            $indexAttribute = $objectNode.Attributes['Index']
            if ($null -ne $indexAttribute) {
                [void]$observedIndexes.Add($indexAttribute.Value.Trim('[', ']'))
            }
        }
        foreach ($statement in @($document.SelectNodes('//*[@StatementText]', $namespace))) {
            $statementText = [Text.RegularExpressions.Regex]::Replace(
                [string]$statement.Attributes['StatementText'].Value,
                '\s+',
                ' '
            )
            foreach ($entrypoint in @(
                    'EXEC stg.usp_stage_usuario_record',
                    'EXEC core.usp_apply_reconcile_publish_usuarios'
                )) {
                if ($statementText.IndexOf(
                        $entrypoint,
                        [StringComparison]::OrdinalIgnoreCase
                    ) -ge 0) {
                    [void]$observedEntrypoints.Add($entrypoint)
                }
            }
            foreach ($requiredPath in $requiredStatementPaths) {
                if ($statementText.IndexOf(
                        $requiredPath.Fragment,
                        [StringComparison]::OrdinalIgnoreCase
                    ) -lt 0) {
                    continue
                }
                $statementIndexes = @($statement.SelectNodes('.//sp:Object', $namespace) |
                    ForEach-Object {
                        if ($null -ne $_.Attributes['Index']) {
                            $_.Attributes['Index'].Value.Trim('[', ']')
                        }
                    })
                if ($statementIndexes -ccontains $requiredPath.Index) {
                    [void]$observedStatementPaths.Add($requiredPath.Label)
                }
            }
        }
    }

    if ($relationalOperators -le 0 -or $seekOperators -le 0) {
        throw 'SHOWPLAN de Usuários não produziu operadores relacionais/seeks verificáveis.'
    }
    if ($fatalWarningKinds.Count -ne 0) {
        throw "SHOWPLAN de Usuários produziu warning operacional: $(@($fatalWarningKinds) -join ',')."
    }
    if ($conversionWarnings -ne 0) {
        throw 'SHOWPLAN de Usuários produziu conversão com impacto de cardinalidade.'
    }
    foreach ($entrypoint in @(
            'EXEC stg.usp_stage_usuario_record',
            'EXEC core.usp_apply_reconcile_publish_usuarios'
        )) {
        if (-not $observedEntrypoints.Contains($entrypoint)) {
            throw "SHOWPLAN de Usuários não atribuiu plano ao entrypoint $entrypoint."
        }
    }
    foreach ($requiredPath in $requiredStatementPaths) {
        if (-not $observedStatementPaths.Contains($requiredPath.Label)) {
            throw "SHOWPLAN de Usuários não atribuiu $($requiredPath.Index) ao path $($requiredPath.Label)."
        }
    }
    foreach ($requiredIndex in @(
            'IX_stg_usuario_record_execution_source',
            'UQ_core_usuario_source',
            'IX_core_usuario_history_timeline',
            'PK_recon_usuario_candidate_application'
        )) {
        if (-not $observedIndexes.Contains($requiredIndex)) {
            throw "SHOWPLAN de Usuários não comprovou o índice $requiredIndex."
        }
    }

    Write-Output (
        'SHOWPLAN Usuários validado e revertido: documents={0}; relops={1}; seeks={2}; scans={3}; statementPaths={4}; conversionWarnings={5}; operationalWarnings=0.' `
            -f $plans.Count, $relationalOperators, $seekOperators, $scanOperators, `
                $observedStatementPaths.Count, $conversionWarnings
    )
} finally {
    if ($connection.State -eq [Data.ConnectionState]::Open) {
        if ($showplanEnabled) {
            try {
                Invoke-NonQuery -Connection $connection -CommandText 'SET SHOWPLAN_XML OFF;'
            } catch {
                # O fechamento da sessão ainda contém o estado SHOWPLAN.
            }
        }
        try {
            Invoke-NonQuery -Connection $connection -CommandText `
                'IF XACT_STATE() <> 0 ROLLBACK TRANSACTION;'
        } catch {
            # O fechamento fail-safe abaixo encerra qualquer transação local pendente.
        }
    }
    $connection.Dispose()
}
