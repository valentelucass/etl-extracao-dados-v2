[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$showplanScript = Join-Path $repositoryRoot `
    'database\validation\025_validate_observability_data_quality_showplan.sql'
$targetDatabase = 'ETL_SISTEMA_V2_SHADOW'
$maximumPlanCharacters = 16MB

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
        + 'Application Name=ETL-V2-Observability-Showplan'
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
        throw 'A conexão SHOWPLAN V2-023 não está no alvo local autorizado.'
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
                                throw 'SHOWPLAN V2-023 excedeu o limite local sanitizado.'
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
        throw 'O SHOWPLAN V2-023 não confirmou rollback integral.'
    }
    if ($plans.Count -ne 1) {
        throw 'O SHOWPLAN V2-023 exige exatamente um documento compilado.'
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
                    throw 'SHOWPLAN V2-023 referenciou database fora do alvo local.'
                }
            }
            $indexAttribute = $objectNode.Attributes['Index']
            if ($null -ne $indexAttribute) {
                [void]$observedIndexes.Add($indexAttribute.Value.Trim('[', ']'))
            }
        }
    }

    if ($relationalOperators -le 0 -or $seekOperators -le 0) {
        throw 'SHOWPLAN V2-023 não produziu operadores relacionais/seeks verificáveis.'
    }
    if ($fatalWarningKinds.Count -ne 0) {
        throw "SHOWPLAN V2-023 produziu warning operacional: $(@($fatalWarningKinds) -join ',')."
    }
    foreach ($requiredIndex in @(
            'IX_ctl_execution_attempt_health_state_started',
            'IX_recon_execution_data_quality_evaluation_state',
            'UX_ctl_data_quality_policy_scope_effective'
        )) {
        if (-not $observedIndexes.Contains($requiredIndex)) {
            throw "SHOWPLAN V2-023 não comprovou o índice $requiredIndex."
        }
    }

    Write-Output (
        'SHOWPLAN V2-023 validado e revertido: documents=1; relops={0}; seeks={1}; scans={2}; conversionWarnings={3}; operationalWarnings=0.' `
            -f $relationalOperators, $seekOperators, $scanOperators, $conversionWarnings
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
