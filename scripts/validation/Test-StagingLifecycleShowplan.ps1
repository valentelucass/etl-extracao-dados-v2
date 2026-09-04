[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$showplanScript = Join-Path $repositoryRoot `
    'database\validation\017_validate_staging_lifecycle_showplan.sql'
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
        + 'Application Name=ETL-V2-Staging-Lifecycle-Showplan'
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
        throw 'A conexão SHOWPLAN não está no alvo local autorizado.'
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
                                throw 'SHOWPLAN excedeu o limite local sanitizado.'
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

    $transactionStateCommand = $connection.CreateCommand()
    try {
        $transactionStateCommand.CommandText =
            'SELECT @@TRANCOUNT;'
        $transactionCount = [int]$transactionStateCommand.ExecuteScalar()
    } finally {
        $transactionStateCommand.Dispose()
    }
    if ($transactionCount -ne 0) {
        throw 'O SHOWPLAN não confirmou rollback integral.'
    }
    if ($plans.Count -lt 1) {
        throw 'Nenhum documento SHOWPLAN_XML foi compilado.'
    }

    $relationalOperators = 0
    $seekOperators = 0
    $scanOperators = 0
    $conversionWarnings = 0
    $fatalWarningKinds = [Collections.Generic.HashSet[string]]::new(
        [StringComparer]::Ordinal
    )
    $selectionIndexObserved = $false
    $typedBudgetIndexObserved = $false
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
        $warnings = @($document.SelectNodes('//sp:Warnings', $namespace))
        foreach ($warning in $warnings) {
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
                    throw 'SHOWPLAN referenciou database fora do alvo local autorizado.'
                }
            }
            $indexAttribute = $objectNode.Attributes['Index']
            if ($null -ne $indexAttribute `
                    -and $indexAttribute.Value.IndexOf(
                        'IX_ctl_execution_attempt_lifecycle',
                        [StringComparison]::Ordinal
                    ) -ge 0) {
                $selectionIndexObserved = $true
            }
            if ($null -ne $indexAttribute `
                    -and $indexAttribute.Value.IndexOf(
                        'UQ_stg_usuario_record_execution_stage',
                        [StringComparison]::Ordinal
                    ) -ge 0) {
                $typedBudgetIndexObserved = $true
            }
        }
    }

    if ($relationalOperators -le 0 -or $seekOperators -le 0) {
        throw 'SHOWPLAN não produziu operadores relacionais/seeks verificáveis.'
    }
    if ($fatalWarningKinds.Count -ne 0) {
        throw "SHOWPLAN produziu warning operacional: $(@($fatalWarningKinds) -join ',')."
    }
    if (-not $selectionIndexObserved) {
        throw 'SHOWPLAN não comprovou o índice de seleção bounded do lifecycle.'
    }
    if (-not $typedBudgetIndexObserved) {
        throw 'SHOWPLAN não comprovou o seek bounded do budget tipado de Usuários.'
    }

    Write-Output (
        'SHOWPLAN lifecycle validado e revertido: documents={0}; relops={1}; seeks={2}; scans={3}; conversionWarnings={4}; typedBudgetSeek=1; operationalWarnings=0.' `
            -f $plans.Count, $relationalOperators, $seekOperators, $scanOperators, `
                $conversionWarnings
    )
} finally {
    if ($connection.State -eq [Data.ConnectionState]::Open) {
        if ($showplanEnabled) {
            try {
                Invoke-NonQuery -Connection $connection -CommandText 'SET SHOWPLAN_XML OFF;'
            } catch {
                # O fechamento da conexão ainda desfaz qualquer transação local pendente.
            }
        }
        try {
            Invoke-NonQuery -Connection $connection -CommandText `
                'IF XACT_STATE() <> 0 ROLLBACK TRANSACTION;'
        } catch {
            # O fechamento fail-safe abaixo também encerra a sessão e sua transação.
        }
    }
    $connection.Dispose()
}
