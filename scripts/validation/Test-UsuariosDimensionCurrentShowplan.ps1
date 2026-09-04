[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$showplanScript = Join-Path $repositoryRoot `
    'database\validation\037_validate_usuarios_dimension_current_showplan.sql'
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
        + 'Application Name=ETL-V2-Usuarios-Dimension-Showplan'
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
        throw 'A conexão SHOWPLAN dimensional não está no alvo local autorizado.'
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
                            if (-not $candidate.StartsWith('<ShowPlanXML', [StringComparison]::Ordinal)) {
                                continue
                            }
                            $capturedCharacters += $candidate.Length
                            if ($capturedCharacters -gt $maximumPlanCharacters) {
                                throw 'SHOWPLAN dimensional excedeu o limite local sanitizado.'
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
        throw 'O SHOWPLAN dimensional não confirmou rollback integral.'
    }
    if ($plans.Count -ne 3) {
        throw "O SHOWPLAN dimensional deveria produzir três documentos; produziu $($plans.Count)."
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
    $observedPaths = [Collections.Generic.HashSet[string]]::new(
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
                "//sp:RelOp[contains(@PhysicalOp, 'Seek')]", $namespace
            )).Count
        $scanOperators += @($document.SelectNodes(
                "//sp:RelOp[contains(@PhysicalOp, 'Scan')]", $namespace
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
                if (-not $databaseName.Equals($targetDatabase, [StringComparison]::OrdinalIgnoreCase)) {
                    throw 'SHOWPLAN dimensional referenciou database fora do alvo local.'
                }
            }
            $indexAttribute = $objectNode.Attributes['Index']
            if ($null -ne $indexAttribute) {
                [void]$observedIndexes.Add($indexAttribute.Value.Trim('[', ']'))
            }
        }

        foreach ($statement in @($document.SelectNodes('//*[@StatementText]', $namespace))) {
            $statementText = [Text.RegularExpressions.Regex]::Replace(
                [string]$statement.Attributes['StatementText'].Value, '\s+', ' '
            )
            if ($statementText.IndexOf(
                    'core.v_usuario_dimension_current_v1 AS dimension',
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
            if ($statementText.IndexOf(
                    'dimension.usuario_id = @usuario_id',
                    [StringComparison]::OrdinalIgnoreCase
                ) -ge 0) {
                if ($statementIndexes -ccontains 'PK_core_usuario') {
                    [void]$observedPaths.Add('canonical-id')
                }
            } elseif ($statementText.IndexOf(
                    'dimension.[source_key_token] = @source_key_token',
                    [StringComparison]::OrdinalIgnoreCase
                ) -ge 0) {
                if ($statementIndexes -ccontains 'UQ_core_usuario_source') {
                    [void]$observedPaths.Add('scoped-source-identity')
                }
            } elseif ($statementText.IndexOf('WHERE ', [StringComparison]::OrdinalIgnoreCase) -lt 0) {
                [void]$observedPaths.Add('bounded-current-projection')
            }
        }
    }

    if ($relationalOperators -le 0 -or $seekOperators -lt 2) {
        throw 'SHOWPLAN dimensional não produziu operadores/seeks verificáveis.'
    }
    if ($fatalWarningKinds.Count -ne 0) {
        throw "SHOWPLAN dimensional produziu warning operacional: $(@($fatalWarningKinds) -join ',')."
    }
    if ($conversionWarnings -ne 0) {
        throw 'SHOWPLAN dimensional produziu conversão com impacto de cardinalidade.'
    }
    foreach ($requiredIndex in @('PK_core_usuario', 'UQ_core_usuario_source')) {
        if (-not $observedIndexes.Contains($requiredIndex)) {
            throw "SHOWPLAN dimensional não comprovou o índice $requiredIndex."
        }
    }
    foreach ($requiredPath in @(
            'canonical-id', 'scoped-source-identity', 'bounded-current-projection'
        )) {
        if (-not $observedPaths.Contains($requiredPath)) {
            throw "SHOWPLAN dimensional não comprovou o path $requiredPath."
        }
    }

    Write-Output (
        'SHOWPLAN dimensão Usuários validado e revertido: documents={0}; relops={1}; seeks={2}; scans={3}; paths={4}; conversionWarnings=0; operationalWarnings=0.' `
            -f $plans.Count, $relationalOperators, $seekOperators, $scanOperators,
                $observedPaths.Count
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
            # O fechamento fail-safe encerra qualquer transação local pendente.
        }
    }
    $connection.Dispose()
}
