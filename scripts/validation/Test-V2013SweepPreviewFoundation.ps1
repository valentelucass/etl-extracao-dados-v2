#Requires -Version 7.0

[CmdletBinding()]
param(
    [switch]$ArtifactsOnly
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repositoryRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..'))
$manifestPath = Join-Path $repositoryRoot 'docs\catalogos\sweep-v2-013\manifesto.json'

if (-not (Test-Path -LiteralPath $manifestPath -PathType Leaf)) {
    [Console]::Out.WriteLine(
        'V2_013_SWEEP_FOUNDATION status=FAIL reason=V2_013_SWEEP_FOUNDATION_MANIFEST_MISSING')
    exit 1
}

$utf8Strict = [Text.UTF8Encoding]::new($false, $true)
$ordinal = [StringComparer]::Ordinal
$ordinalIgnoreCase = [StringComparer]::OrdinalIgnoreCase

function Fail([string]$Reason) {
    throw [InvalidOperationException]::new($Reason)
}

function Assert-True([bool]$Condition, [string]$Reason) {
    if (-not $Condition) { Fail $Reason }
}

function Assert-String($Actual, [string]$Expected, [string]$Reason) {
    if ($Actual -isnot [string] -or -not [string]::Equals($Actual, $Expected, [StringComparison]::Ordinal)) {
        Fail $Reason
    }
}

function Assert-Integer($Actual, [long]$Expected, [string]$Reason) {
    if (($Actual -isnot [int] -and $Actual -isnot [long]) -or [long]$Actual -ne $Expected) {
        Fail $Reason
    }
}

function Assert-Boolean($Actual, [bool]$Expected, [string]$Reason) {
    if ($Actual -isnot [bool] -or $Actual -ne $Expected) { Fail $Reason }
}

function Assert-UniqueExactSet($Actual, [string[]]$Expected, [string]$Reason) {
    $actualItems = @($Actual)
    if ($actualItems.Count -ne $Expected.Count) { Fail $Reason }
    $seen = [Collections.Generic.HashSet[string]]::new($ordinal)
    $seenFolded = [Collections.Generic.HashSet[string]]::new($ordinalIgnoreCase)
    foreach ($item in $actualItems) {
        if ($item -isnot [string] -or -not $seen.Add($item) -or -not $seenFolded.Add($item)) {
            Fail $Reason
        }
    }
    foreach ($item in $Expected) {
        if (-not $seen.Contains($item)) { Fail $Reason }
    }
}

function Assert-OrderedExact($Actual, [string[]]$Expected, [string]$Reason) {
    $actualItems = @($Actual)
    Assert-UniqueExactSet $actualItems $Expected $Reason
    Assert-String ($actualItems -join "`u{001f}") ($Expected -join "`u{001f}") $Reason
}

function Assert-Properties($Value, [string[]]$Expected, [string]$Reason) {
    if ($null -eq $Value -or $Value -is [Array] -or $Value -is [string]) { Fail $Reason }
    Assert-UniqueExactSet @($Value.PSObject.Properties.Name) $Expected $Reason
}

function Assert-NoDuplicateJson([System.Text.Json.JsonElement]$Element, [string]$At) {
    if ($Element.ValueKind -eq [System.Text.Json.JsonValueKind]::Object) {
        $names = [Collections.Generic.HashSet[string]]::new($ordinal)
        $folded = [Collections.Generic.HashSet[string]]::new($ordinalIgnoreCase)
        foreach ($property in $Element.EnumerateObject()) {
            if (-not $names.Add($property.Name) -or -not $folded.Add($property.Name)) {
                Fail "V2_013_JSON_DUPLICATE_OR_CASE_COLLISION:$At"
            }
            Assert-NoDuplicateJson $property.Value "$At/$($property.Name)"
        }
    } elseif ($Element.ValueKind -eq [System.Text.Json.JsonValueKind]::Array) {
        $index = 0
        foreach ($item in $Element.EnumerateArray()) {
            Assert-NoDuplicateJson $item "$At/$index"
            $index++
        }
    }
}

function Read-StrictText([string]$Path, [long]$MaximumBytes = 1048576) {
    $bytes = [IO.File]::ReadAllBytes($Path)
    if ($bytes.Length -gt $MaximumBytes) { Fail 'V2_013_ARTIFACT_TOO_LARGE' }
    if ($bytes.Length -ge 3 -and $bytes[0] -eq 0xef -and $bytes[1] -eq 0xbb -and $bytes[2] -eq 0xbf) {
        Fail 'V2_013_UTF8_BOM_FORBIDDEN'
    }
    try { return $utf8Strict.GetString($bytes) } catch { Fail 'V2_013_UTF8_INVALID' }
}

function Read-StrictJson([string]$Path) {
    $text = Read-StrictText $Path
    try {
        $document = [System.Text.Json.JsonDocument]::Parse($text)
        try { Assert-NoDuplicateJson $document.RootElement '$' } finally { $document.Dispose() }
        return ($text | ConvertFrom-Json -Depth 100)
    } catch [InvalidOperationException] { throw }
    catch { Fail 'V2_013_JSON_INVALID' }
}

function Get-LiveHash([string]$RelativePath) {
    if ([IO.Path]::IsPathRooted($RelativePath) -or $RelativePath.Contains('\') -or
        $RelativePath -match '(^|/)\.\.(/|$)' -or $RelativePath -match '(^|/)\.(/|$)') {
        Fail 'V2_013_PATH_INVALID'
    }
    $full = [IO.Path]::GetFullPath((Join-Path $repositoryRoot $RelativePath))
    $prefix = $repositoryRoot.TrimEnd([IO.Path]::DirectorySeparatorChar) + [IO.Path]::DirectorySeparatorChar
    if (-not $full.StartsWith($prefix, [StringComparison]::OrdinalIgnoreCase) -or
        -not (Test-Path -LiteralPath $full -PathType Leaf)) {
        Fail 'V2_013_PATH_ESCAPE_OR_MISSING'
    }
    $cursor = $repositoryRoot
    foreach ($part in ($RelativePath -split '/')) {
        $cursor = Join-Path $cursor $part
        $item = Get-Item -LiteralPath $cursor -Force
        if (($item.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) {
            Fail 'V2_013_REPARSE_POINT_FORBIDDEN'
        }
    }
    return (Get-FileHash -LiteralPath $full -Algorithm SHA256).Hash.ToLowerInvariant()
}

function Get-TextHash([string]$Text) {
    $sha = [Security.Cryptography.SHA256]::Create()
    try {
        return [Convert]::ToHexString($sha.ComputeHash([Text.Encoding]::UTF8.GetBytes($Text))).ToLowerInvariant()
    } finally {
        $sha.Dispose()
    }
}

function Assert-ArtifactList($Items, [Collections.IDictionary]$Expected, [string]$Reason) {
    $itemsArray = @($Items)
    Assert-OrderedExact @($itemsArray | ForEach-Object { $_.path }) @($Expected.Keys) $Reason
    foreach ($item in $itemsArray) {
        Assert-Properties $item @('path', 'sha256') $Reason
        Assert-True ($item.sha256 -is [string] -and $item.sha256 -cmatch '^[0-9a-f]{64}$') $Reason
        $expectedHash = $Expected[$item.path]
        if ($null -ne $expectedHash) { Assert-String $item.sha256 $expectedHash $Reason }
        Assert-String (Get-LiveHash $item.path) $item.sha256 $Reason
    }
}

function Get-RecursiveRelativeFiles([string]$RelativeRoot) {
    $root = [IO.Path]::GetFullPath((Join-Path $repositoryRoot $RelativeRoot))
    if (-not (Test-Path -LiteralPath $root -PathType Container)) { Fail 'V2_013_DIRECTORY_MISSING' }
    $entries = @(Get-ChildItem -LiteralPath $root -Recurse -Force)
    foreach ($entry in $entries) {
        if (($entry.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) {
            Fail 'V2_013_REPARSE_POINT_FORBIDDEN'
        }
    }
    return @($entries | Where-Object { -not $_.PSIsContainer } | ForEach-Object {
        [IO.Path]::GetRelativePath($root, $_.FullName).Replace('\', '/')
    })
}

function Assert-RecursiveAllowlist([string]$Root, [string[]]$Expected) {
    Assert-UniqueExactSet (Get-RecursiveRelativeFiles $Root) $Expected 'V2_013_CLOSED_WORLD_DRIFT'
}

function Assert-NamespaceAllowlist([string]$Root, [string]$NamePattern, [string[]]$Expected) {
    $rootPath = [IO.Path]::GetFullPath((Join-Path $repositoryRoot $Root))
    if (-not (Test-Path -LiteralPath $rootPath -PathType Container)) { Fail 'V2_013_DIRECTORY_MISSING' }
    $matches = @(Get-ChildItem -LiteralPath $rootPath -Recurse -Force | Where-Object {
        -not $_.PSIsContainer -and $_.Name -like $NamePattern
    })
    foreach ($match in $matches) {
        if (($match.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) {
            Fail 'V2_013_REPARSE_POINT_FORBIDDEN'
        }
    }
    $relative = @($matches | ForEach-Object {
        [IO.Path]::GetRelativePath($rootPath, $_.FullName).Replace('\', '/')
    })
    Assert-UniqueExactSet $relative $Expected 'V2_013_CLOSED_WORLD_DRIFT'
}

try {
    $manifestText = Read-StrictText $manifestPath
    $manifest = Read-StrictJson $manifestPath
    Assert-Properties $manifest @(
        'schemaVersion', 'task', 'outcome', 'foundation', 'matrix', 'fixture',
        'governanceArtifacts', 'kernelArtifacts', 'testArtifacts', 'canonicalBindings',
        'evidenceSources', 'openRoutes', 'prohibitedScope') 'V2_013_MANIFEST_SCHEMA'
    Assert-String $manifest.schemaVersion '2026-09-06.v2-013.q-swp-fnd-01.1' 'V2_013_MANIFEST_SCHEMA'
    Assert-Properties $manifest.task @('route', 'block', 'taskId') 'V2_013_MANIFEST_TASK'
    Assert-String $manifest.task.route 'Q-SWP-FND-01' 'V2_013_MANIFEST_TASK'
    Assert-Integer $manifest.task.block 49 'V2_013_MANIFEST_TASK'
    Assert-String $manifest.task.taskId 'V2-013/FUNDACAO_KERNEL_LOCAL' 'V2_013_MANIFEST_TASK'
    Assert-String $manifest.outcome 'FOUNDATION_LOCAL_COMPLETE_NO_ENTITY_SWEEP_ENABLED' 'V2_013_MANIFEST_OUTCOME'

    Assert-Properties $manifest.foundation @(
        'model', 'executionMode', 'decisionMode', 'complexity', 'positiveDisposition',
        'positiveResponsibilityKind', 'entitySweepEnabledCount', 'networkAccess',
        'databaseAccess', 'productKernelFilesystemAccess', 'runtimeWiring', 'applyCapability') 'V2_013_FOUNDATION_SCHEMA'
    Assert-String $manifest.foundation.model 'SOL_ULTRA' 'V2_013_FOUNDATION_VALUE'
    Assert-String $manifest.foundation.executionMode 'SWEEP' 'V2_013_FOUNDATION_VALUE'
    Assert-String $manifest.foundation.decisionMode 'PREVIEW_ONLY' 'V2_013_FOUNDATION_VALUE'
    Assert-String $manifest.foundation.complexity 'O(1)_ONE_RESPONSIBILITY_PER_CALL' 'V2_013_FOUNDATION_VALUE'
    Assert-String $manifest.foundation.positiveDisposition 'PREVIEW_ELIGIBLE_NO_APPLY_CAPABILITY' 'V2_013_FOUNDATION_VALUE'
    Assert-String $manifest.foundation.positiveResponsibilityKind 'ROOT_ONLY_SYNTHETIC_LOCAL' 'V2_013_FOUNDATION_VALUE'
    Assert-Integer $manifest.foundation.entitySweepEnabledCount 0 'V2_013_FOUNDATION_VALUE'
    foreach ($property in @('networkAccess', 'databaseAccess', 'productKernelFilesystemAccess', 'runtimeWiring', 'applyCapability')) {
        Assert-Boolean $manifest.foundation.$property $false 'V2_013_FOUNDATION_VALUE'
    }

    Assert-Properties $manifest.matrix @(
        'path', 'sha256', 'rowCount', 'familyCount', 'enabledCount', 'provenCompleteCount',
        'blockedCount', 'disabledCount', 'notApplicableCount', 'applicabilityVocabulary',
        'responsibilityKindVocabulary') 'V2_013_MATRIX_SCHEMA'
    Assert-String $manifest.matrix.path 'docs/catalogos/sweep-v2-013/matriz-aplicabilidade-v01.csv' 'V2_013_MATRIX_VALUE'
    Assert-String $manifest.matrix.sha256 '69ef33c8c095c200f4025264e38c0dd248fa4f12bde8722aaa00101c570d3bd9' 'V2_013_MATRIX_HASH'
    Assert-Integer $manifest.matrix.rowCount 33 'V2_013_MATRIX_VALUE'
    Assert-Integer $manifest.matrix.familyCount 11 'V2_013_MATRIX_VALUE'
    Assert-Integer $manifest.matrix.enabledCount 0 'V2_013_MATRIX_VALUE'
    Assert-Integer $manifest.matrix.provenCompleteCount 0 'V2_013_MATRIX_VALUE'
    Assert-Integer $manifest.matrix.blockedCount 19 'V2_013_MATRIX_VALUE'
    Assert-Integer $manifest.matrix.disabledCount 5 'V2_013_MATRIX_VALUE'
    Assert-Integer $manifest.matrix.notApplicableCount 9 'V2_013_MATRIX_VALUE'
    Assert-OrderedExact $manifest.matrix.applicabilityVocabulary @('ENABLED', 'DISABLED', 'BLOCKED', 'NOT_APPLICABLE') 'V2_013_MATRIX_VOCABULARY'
    $kindVocabulary = @('ROOT', 'CHILD', 'ONE_TO_ONE_COMPONENT', 'HISTORY', 'CONDITIONAL_COMPONENT', 'OBSERVATION_CHANNEL', 'REFERENCE', 'SOURCE_LINE', 'UNRESOLVED_CANDIDATE')
    Assert-OrderedExact $manifest.matrix.responsibilityKindVocabulary $kindVocabulary 'V2_013_MATRIX_VOCABULARY'
    Assert-String (Get-LiveHash $manifest.matrix.path) $manifest.matrix.sha256 'V2_013_MATRIX_HASH'

    $matrixText = Read-StrictText (Join-Path $repositoryRoot $manifest.matrix.path)
    Assert-True (-not $matrixText.Contains("`r") -and $matrixText.EndsWith("`n", [StringComparison]::Ordinal) -and -not $matrixText.Contains('"')) 'V2_013_MATRIX_ENCODING'
    $matrixLines = @($matrixText.TrimEnd("`n") -split "`n")
    $expectedHeader = 'row_id,family,responsibility,row_kind,parent_row_id,applicability,completeness_status,owner_status,hierarchy_policy,presence_key_path,presence_key_value,presence_key_status,confirmation_policy,guardrail_policy,reason_code,evidence_anchor'
    Assert-String $matrixLines[0] $expectedHeader 'V2_013_MATRIX_HEADER'
    $rows = @($matrixText | ConvertFrom-Csv)
    Assert-Integer $rows.Count 33 'V2_013_MATRIX_ROWS'
    $expectedIds = @(
        'SWP-COLETAS-ROOT','SWP-COLETAS-FRETE-CAND','SWP-FRETES-ROOT','SWP-FRETES-PERFORMANCE','SWP-FRETES-GRAPHQL','SWP-FRETES-COLETA-CAND',
        'SWP-MANIFESTOS-ROOT','SWP-MANIFESTOS-PICK','SWP-MANIFESTOS-MDFE','SWP-MANIFESTOS-COLETA-CAND','SWP-COTACOES-ROOT','SWP-COTACOES-TARIFA-REF',
        'SWP-LOCALIZACAO-ROOT','SWP-LOCALIZACAO-FRETE-CAND','SWP-CAP-ROOT-CAND','SWP-CAP-PARCELA-CAND','SWP-FATURAS-ROOT-CAND','SWP-FATURAS-TITULO-CAND',
        'SWP-FATURAS-NFSE-CAND','SWP-FATURAS-CTE-CAND','SWP-FATURAS-BILLING-CAND','SWP-FATURAS-INVOICE-CAND','SWP-FATURAS-ORDER-CAND',
        'SWP-INVENTARIO-ROOT-CAND','SWP-INVENTARIO-FREIGHT-CAND','SWP-INVENTARIO-INVOICE-CAND','SWP-SINISTROS-ROOT-CAND','SWP-SINISTROS-MINUTA-CAND',
        'SWP-SINISTROS-INVOICE-CAND','SWP-USUARIOS-CURRENT','SWP-USUARIOS-HISTORY','SWP-RASTER-VIAGENS','SWP-RASTER-PARADAS')
    Assert-UniqueExactSet @($rows.row_id) $expectedIds 'V2_013_MATRIX_CLOSED_WORLD'
    $expectedRowDigests = [ordered]@{
        'SWP-COLETAS-ROOT' = '6b501ff6f32f80ab5cf1e96615216e841bd08bad5f48a513cff4b5d713ca6066'
        'SWP-COLETAS-FRETE-CAND' = '08ac19c508e1ab7c8ee178d3c71537118ba0418049a0a1775928d3eceb1d3fe6'
        'SWP-FRETES-ROOT' = '1f9ae8f828107c15b057a42596b69afaad0a731e2380b24285b0bd24317c6182'
        'SWP-FRETES-PERFORMANCE' = '7f4f717c8042579cfeecb61d486852d07c717f361a571533c75461db69b3140b'
        'SWP-FRETES-GRAPHQL' = '60f8fa61d9fb5f7a7bfa34abc582284c0e0253b7385a8cd1b992480f4c255427'
        'SWP-FRETES-COLETA-CAND' = 'b8f82a21d5660195819f99c4a76b0cc72f4a72e35e12dee8e891bea8190c0559'
        'SWP-MANIFESTOS-ROOT' = '12f12061cbff9e940496d193aa4dfd23e9335c5da53cb12f5da702744bb82b68'
        'SWP-MANIFESTOS-PICK' = '9b52e32e6aee37edb4c6920c9a04359df90ca955ed9d396a76d8cf96199d78be'
        'SWP-MANIFESTOS-MDFE' = '8d9501cd120bcdf11d5affe2994b629d9830774381da82b8d605730a4bde2c6c'
        'SWP-MANIFESTOS-COLETA-CAND' = 'd0d8af739d8f92f5bb112e250879588e7f790ebd17613431094b240a901de54e'
        'SWP-COTACOES-ROOT' = 'd9b5768694b0218252f7e652318302d906ea26023034da7556b2ea602d5c6944'
        'SWP-COTACOES-TARIFA-REF' = 'f829908bc37033d6741ee83983e67b7d24b999336755c8fd6cd5df7cd55034fb'
        'SWP-LOCALIZACAO-ROOT' = '2f5ec214e172a9d17b6a70c0b3f88f61370093c5cf6fce3cba02f5b6dc01b864'
        'SWP-LOCALIZACAO-FRETE-CAND' = '6d646ba1a37a3bec9c014b38283919d1545edf71dc278be8d05d7c45d4026f8d'
        'SWP-CAP-ROOT-CAND' = '90fd7414894ff96fcd858d93d8d13bde55fdf366f3289bb074da1328ab8d5f4d'
        'SWP-CAP-PARCELA-CAND' = '3b2166490ace81be9ce3e6a14447fc6fe9509bad033e9b11902aa0566b746e66'
        'SWP-FATURAS-ROOT-CAND' = '913cfc35859603ef033b5322e22b7d887f615761d2f3a7a5442c81c6b64f823a'
        'SWP-FATURAS-TITULO-CAND' = '8d23ceea22ad0d1f0f2d4b6724ee1a5cede8e7e8bbecbc388fa1184f7ae326d8'
        'SWP-FATURAS-NFSE-CAND' = '6876b42f3bc8731f16e7062dcc650a524e4e23bd5ff470218e0c962c12818987'
        'SWP-FATURAS-CTE-CAND' = 'c7a6a0946dd2e60d39284c03041704959755704051b87bd898764cb994d880f3'
        'SWP-FATURAS-BILLING-CAND' = '4cac043629b4e9d80fd188601a83d1926881054be4e55efbeb30123b18812d7b'
        'SWP-FATURAS-INVOICE-CAND' = '9b8a0d85accfe018fbfe8b3568f10f28625a2b7082a8d5c291e9cef12c98813d'
        'SWP-FATURAS-ORDER-CAND' = '9d53fa4df496a6e75e081458fbc6b877d857573d263063bef8a2fbc7c4e6c758'
        'SWP-INVENTARIO-ROOT-CAND' = '077fb2c9bc25bdad1f394066eb39066f83768beea1308044c1bf30a960047643'
        'SWP-INVENTARIO-FREIGHT-CAND' = 'ec8a44f8cf289e5498425add5f95b44d621e74fd51e9edcb0dbc3a4f5c71f04b'
        'SWP-INVENTARIO-INVOICE-CAND' = '83a019319aa9060751993cb188c34ea756e610fb637bf1e137c19095cf3f3c82'
        'SWP-SINISTROS-ROOT-CAND' = 'e0ed8e083942e503ace1071bc75f2df6a9dbbe6e81650bd1dd44006547791fdf'
        'SWP-SINISTROS-MINUTA-CAND' = 'd15afe113709c2ea721fbdacb771aed050c7ba8a73175d43524b98fa26afa4c5'
        'SWP-SINISTROS-INVOICE-CAND' = '2a4ebb7ac94ab645609b90487d08c5be96a09e38a4a71b055e13db6b7857f2f7'
        'SWP-USUARIOS-CURRENT' = 'e6a0ca238fbff81f9508c0da905f4b9b7cdf0c0584b86b8267ef17ba213fd2f3'
        'SWP-USUARIOS-HISTORY' = '2a3b088cc24718dbe3ac7a0e125e77b44fbff74fcb11a9a448dbf0462d4272ad'
        'SWP-RASTER-VIAGENS' = '0cdba7b7058355f140965d130d479469b5d1046ec1f947c106021fd2c2d66cb8'
        'SWP-RASTER-PARADAS' = '24cbb76b784a47cd68ed8b2cf232adb38c34f9ed437c50370853ab93a1ae46c5'
    }
    for ($index = 1; $index -lt $matrixLines.Count; $index++) {
        $rowId = ($matrixLines[$index] -split ',', 2)[0]
        Assert-String (Get-TextHash $matrixLines[$index]) $expectedRowDigests[$rowId] 'V2_013_MATRIX_ROW_MAPPING'
    }
    Assert-UniqueExactSet @($rows.family | Sort-Object -Unique) @('COLETAS','FRETES','MANIFESTOS','COTACOES','LOCALIZACAO_CARGAS','CONTAS_A_PAGAR','FATURAS_POR_CLIENTE','INVENTARIO','SINISTROS','USUARIOS','RASTER') 'V2_013_MATRIX_FAMILIES'
    Assert-UniqueExactSet @($rows.row_kind | Sort-Object -Unique) $kindVocabulary 'V2_013_MATRIX_KINDS'
    Assert-Integer @($rows | Where-Object applicability -eq 'BLOCKED').Count 19 'V2_013_MATRIX_COUNTS'
    Assert-Integer @($rows | Where-Object applicability -eq 'DISABLED').Count 5 'V2_013_MATRIX_COUNTS'
    Assert-Integer @($rows | Where-Object applicability -eq 'NOT_APPLICABLE').Count 9 'V2_013_MATRIX_COUNTS'
    Assert-Integer @($rows | Where-Object applicability -eq 'ENABLED').Count 0 'V2_013_MATRIX_ENABLED'
    Assert-Integer @($rows | Where-Object completeness_status -ne 'BLOCKED_NO_COMPLETENESS_PROOF').Count 0 'V2_013_MATRIX_COMPLETENESS'
    $guardrails = 'PREVIEW_ONLY+NO_APPLY+NO_DELETE+NO_DEACTIVATE+NO_PRUNE+NO_PERSIST+NO_ENTITY_ENABLEMENT+BLOCK_TIMEOUT+BLOCK_MISSING_PAGE+BLOCK_CAP+BLOCK_ANOMALOUS_EMPTY+BLOCK_INVALIDS+BLOCK_QUARANTINE+BLOCK_STRONG_VOLUME_DROP+BLOCK_HISTORY_GAP'
    $confirmation = 'V2_012B_ACCEPTED+NOMINAL_SNAPSHOT_COMPLETENESS_PROOF+SOURCE_COMPLETENESS_PROVEN_COMPLETE+OWNER_NOMINAL+TWO_COMPLETE_TRAVERSALS+TWO_INDEPENDENT_ABSENCES'
    foreach ($row in $rows) {
        Assert-String $row.guardrail_policy $guardrails 'V2_013_MATRIX_GUARDRAILS'
        if ($row.applicability -in @('BLOCKED','DISABLED')) {
            Assert-True ($row.confirmation_policy.StartsWith($confirmation, [StringComparison]::Ordinal)) 'V2_013_MATRIX_CONFIRMATION'
        }
        if ($row.row_kind -eq 'CHILD') {
            Assert-True ($row.confirmation_policy.EndsWith('+PARENT_ROOT_ASSESSMENT_BOUND', [StringComparison]::Ordinal)) 'V2_013_MATRIX_CHILD_BINDING'
        }
        Assert-True (-not $row.evidence_anchor.Contains('RAS-09')) 'V2_013_MATRIX_INVENTED_ROUTE'
    }
    $byId = @{}; foreach ($row in $rows) { $byId[$row.row_id] = $row }
    foreach ($row in $rows) {
        if ($row.parent_row_id) {
            Assert-True $byId.ContainsKey($row.parent_row_id) 'V2_013_MATRIX_PARENT_MISSING'
            Assert-String $byId[$row.parent_row_id].family $row.family 'V2_013_MATRIX_CROSS_FAMILY_PARENT'
            $visitedParents = [Collections.Generic.HashSet[string]]::new($ordinal)
            $cursor = $row
            while ($cursor.parent_row_id) {
                if (-not $visitedParents.Add($cursor.row_id)) { Fail 'V2_013_MATRIX_CYCLE' }
                $cursor = $byId[$cursor.parent_row_id]
            }
        }
    }
    Assert-String $byId['SWP-FRETES-ROOT'].applicability 'DISABLED' 'V2_013_MATRIX_ROOT_STATE'
    Assert-String $byId['SWP-LOCALIZACAO-ROOT'].applicability 'DISABLED' 'V2_013_MATRIX_ROOT_STATE'
    Assert-String $byId['SWP-USUARIOS-CURRENT'].applicability 'DISABLED' 'V2_013_MATRIX_ROOT_STATE'
    Assert-String $byId['SWP-FATURAS-BILLING-CAND'].presence_key_path 'UNOBSERVED_LEGACY_HEURISTIC:billingId' 'V2_013_MATRIX_LOCATOR'
    Assert-String $byId['SWP-RASTER-VIAGENS'].presence_key_path 'LEGACY_COLUMN_CANDIDATE:cod_solicitacao' 'V2_013_MATRIX_LOCATOR'
    Assert-String $byId['SWP-RASTER-PARADAS'].presence_key_path 'LEGACY_COMPOSITE_CANDIDATE:cod_solicitacao+ordem' 'V2_013_MATRIX_LOCATOR'

    Assert-Properties $manifest.fixture @('catalogPath','resourcePath','sha256','caseCount','happyPathCount','mutationCount','executionGate','evidenceKind') 'V2_013_FIXTURE_SCHEMA'
    Assert-String $manifest.fixture.catalogPath 'docs/catalogos/sweep-v2-013/fixtures/kernel-cases-v01.synthetic.json' 'V2_013_FIXTURE_VALUE'
    Assert-String $manifest.fixture.resourcePath 'src/test/resources/contracts/v2-013/q-swp-fnd-01/kernel-cases-v01.synthetic.json' 'V2_013_FIXTURE_VALUE'
    Assert-String $manifest.fixture.sha256 '596aba786543ab2dcc77190a14c1f918ccc4bd2f502312644d5dd2a8abd6f215' 'V2_013_FIXTURE_HASH'
    Assert-Integer $manifest.fixture.caseCount 53 'V2_013_FIXTURE_VALUE'
    Assert-Integer $manifest.fixture.happyPathCount 1 'V2_013_FIXTURE_VALUE'
    Assert-Integer $manifest.fixture.mutationCount 52 'V2_013_FIXTURE_VALUE'
    Assert-String $manifest.fixture.executionGate 'SweepPreviewMutationCatalogTest' 'V2_013_FIXTURE_VALUE'
    Assert-String $manifest.fixture.evidenceKind 'SYNTHETIC_LOCAL_PREVIEW_ONLY' 'V2_013_FIXTURE_VALUE'
    Assert-String (Get-LiveHash $manifest.fixture.catalogPath) $manifest.fixture.sha256 'V2_013_FIXTURE_HASH'
    Assert-String (Get-LiveHash $manifest.fixture.resourcePath) $manifest.fixture.sha256 'V2_013_FIXTURE_HASH'
    $fixtureText = Read-StrictText (Join-Path $repositoryRoot $manifest.fixture.catalogPath)
    Assert-String $fixtureText (Read-StrictText (Join-Path $repositoryRoot $manifest.fixture.resourcePath)) 'V2_013_FIXTURE_COPY_DRIFT'
    $fixture = Read-StrictJson (Join-Path $repositoryRoot $manifest.fixture.catalogPath)
    Assert-Properties $fixture @('schemaVersion','task','fixtureKind','cases') 'V2_013_FIXTURE_SCHEMA'
    Assert-String $fixture.schemaVersion '2026-09-06.v2-013.q-swp-fnd-01.1' 'V2_013_FIXTURE_VALUE'
    Assert-String $fixture.task 'V2-013/FUNDACAO_KERNEL_LOCAL' 'V2_013_FIXTURE_VALUE'
    Assert-String $fixture.fixtureKind 'SYNTHETIC_LOCAL_PREVIEW_ONLY' 'V2_013_FIXTURE_VALUE'
    Assert-Integer @($fixture.cases).Count 53 'V2_013_FIXTURE_CASES'
    foreach ($case in @($fixture.cases)) { Assert-Properties $case @('id','mutation','expectedReason') 'V2_013_FIXTURE_CASE_SCHEMA' }
    $expectedCases = [ordered]@{
        'SWP-HAPPY-001'='NONE|NONE_PREVIEW_ELIGIBLE_NO_APPLY_CAPABILITY'; 'SWP-MODE-001'='MODE_INCREMENTAL|MODE_NOT_SWEEP'; 'SWP-MODE-002'='MODE_BOOTSTRAP|MODE_NOT_SWEEP'; 'SWP-MODE-003'='MODE_BACKFILL|MODE_NOT_SWEEP'; 'SWP-MODE-004'='MODE_REPLAY|MODE_NOT_SWEEP'
        'SWP-APP-001'='APPLICABILITY_DISABLED|APPLICABILITY_NOT_ENABLED'; 'SWP-APP-002'='APPLICABILITY_BLOCKED|APPLICABILITY_NOT_ENABLED'; 'SWP-APP-003'='APPLICABILITY_NOT_APPLICABLE|APPLICABILITY_NOT_ENABLED'
        'SWP-KIND-001'='KIND_HISTORY|RESPONSIBILITY_KIND_NOT_ELIGIBLE'; 'SWP-KIND-002'='KIND_ONE_TO_ONE_COMPONENT|RESPONSIBILITY_KIND_NOT_ELIGIBLE'; 'SWP-KIND-003'='KIND_CONDITIONAL_COMPONENT|RESPONSIBILITY_KIND_NOT_ELIGIBLE'; 'SWP-KIND-004'='KIND_UNRESOLVED_CANDIDATE|RESPONSIBILITY_KIND_NOT_ELIGIBLE'; 'SWP-KIND-005'='KIND_CHILD|RESPONSIBILITY_KIND_NOT_ELIGIBLE'; 'SWP-KIND-006'='KIND_OBSERVATION_CHANNEL|RESPONSIBILITY_KIND_NOT_ELIGIBLE'; 'SWP-KIND-007'='KIND_REFERENCE|RESPONSIBILITY_KIND_NOT_ELIGIBLE'; 'SWP-KIND-008'='KIND_SOURCE_LINE|RESPONSIBILITY_KIND_NOT_ELIGIBLE'
        'SWP-PROOF-001'='COMPLETENESS_BLOCKED|SOURCE_COMPLETENESS_NOT_PROVEN'; 'SWP-PROOF-002'='EVIDENCE_FAILED|EVIDENCE_STATUS_NOT_PROVEN'; 'SWP-PROOF-003'='EVIDENCE_UNVERIFIED|EVIDENCE_STATUS_NOT_PROVEN'; 'SWP-PROOF-004'='TERMINAL_ONLY|SOURCE_COMPLETENESS_NOT_PROVEN'
        'SWP-TRAV-001'='FIRST_TRAVERSAL_INCOMPLETE|TRAVERSAL_INCOMPLETE'; 'SWP-TRAV-002'='SECOND_TRAVERSAL_INCOMPLETE|TRAVERSAL_INCOMPLETE'; 'SWP-TRAV-003'='VISITED_PAGE_MISMATCH|TRAVERSAL_INCOMPLETE'; 'SWP-TRAV-004'='MISSING_PAGE|MISSING_PAGE'
        'SWP-RUNTIME-001'='CAP_REACHED|CAP_REACHED'; 'SWP-RUNTIME-002'='TIMEOUT|TIMEOUT_OR_CANCELLATION'; 'SWP-RUNTIME-003'='CANCELLATION|TIMEOUT_OR_CANCELLATION'; 'SWP-RUNTIME-004'='EMPTY_ANOMALOUS|ANOMALOUS_EMPTY_SOURCE'
        'SWP-LIMIT-001'='INVALID_OVER_LIMIT|INVALID_LIMIT_EXCEEDED'; 'SWP-LIMIT-002'='QUARANTINE_OVER_LIMIT|QUARANTINE_LIMIT_EXCEEDED'; 'SWP-LIMIT-003'='VOLUME_OVER_LIMIT|VOLUME_LIMIT_EXCEEDED'; 'SWP-LIMIT-004'='HISTORY_OVER_LIMIT|HISTORY_LIMIT_EXCEEDED'
        'SWP-GOV-001'='HIERARCHY_UNSAFE|HIERARCHY_UNSAFE'; 'SWP-GOV-002'='OWNER_MISSING|NOMINAL_OWNER_MISSING'
        'SWP-BIND-001'='POLICY_MISMATCH|EVIDENCE_POLICY_MISMATCH'; 'SWP-BIND-002'='SCOPE_MISMATCH|EVIDENCE_SCOPE_MISMATCH'; 'SWP-BIND-003'='SNAPSHOT_MISMATCH|SNAPSHOT_FINGERPRINT_MISMATCH'; 'SWP-BIND-004'='BINDING_MISMATCH|BINDING_FINGERPRINT_MISMATCH'
        'SWP-INDEP-001'='TRAVERSAL_SAME_OCCURRENCE|TRAVERSAL_EVIDENCE_NOT_INDEPENDENT'; 'SWP-INDEP-002'='TRAVERSAL_SAME_RUN|TRAVERSAL_EVIDENCE_NOT_INDEPENDENT'; 'SWP-INDEP-003'='ABSENCE_SAME_OCCURRENCE|ABSENCE_EVIDENCE_NOT_INDEPENDENT'; 'SWP-INDEP-004'='ABSENCE_SAME_RUN|ABSENCE_EVIDENCE_NOT_INDEPENDENT'; 'SWP-INDEP-005'='CROSS_SAME_OCCURRENCE|CROSS_EVIDENCE_NOT_INDEPENDENT'; 'SWP-INDEP-006'='CROSS_SAME_RUN|CROSS_EVIDENCE_NOT_INDEPENDENT'
        'SWP-TRAV-005'='ZERO_PAGES|TRAVERSAL_INCOMPLETE'
        'SWP-CONSTRUCT-001'='NEGATIVE_COUNT|CONSTRUCTION_REJECTED'; 'SWP-CONSTRUCT-002'='OBSERVATION_LIMIT_OVERFLOW|CONSTRUCTION_REJECTED'; 'SWP-CONSTRUCT-003'='POLICY_LIMIT_OVERFLOW|CONSTRUCTION_REJECTED'; 'SWP-CONSTRUCT-004'='MALFORMED_FINGERPRINT|CONSTRUCTION_REJECTED'; 'SWP-CONSTRUCT-005'='PROOF_OCCURRENCE_EQUALS_RUN|CONSTRUCTION_REJECTED'
        'SWP-PRECEDENCE-001'='MULTIPLE_FAILURES|MODE_NOT_SWEEP'
        'SWP-CONSTRUCT-006'='STALE_POLICY_FINGERPRINT|CONSTRUCTION_REJECTED'; 'SWP-CONSTRUCT-007'='STALE_BINDING_FINGERPRINT|CONSTRUCTION_REJECTED'
    }
    Assert-OrderedExact @($fixture.cases.id) @($expectedCases.Keys) 'V2_013_FIXTURE_CASE_MAPPING'
    foreach ($case in @($fixture.cases)) {
        Assert-String "$($case.mutation)|$($case.expectedReason)" $expectedCases[$case.id] 'V2_013_FIXTURE_CASE_MAPPING'
    }
    Assert-UniqueExactSet @($fixture.cases.id) @($fixture.cases.id | Sort-Object -Unique) 'V2_013_FIXTURE_DUPLICATE'
    Assert-UniqueExactSet @($fixture.cases.mutation) @($fixture.cases.mutation | Sort-Object -Unique) 'V2_013_FIXTURE_DUPLICATE'
    Assert-Integer @($fixture.cases | Where-Object mutation -eq 'NONE').Count 1 'V2_013_FIXTURE_HAPPY'
    Assert-Integer @($fixture.cases | Where-Object mutation -ne 'NONE').Count 52 'V2_013_FIXTURE_MUTATIONS'
    Assert-String @($fixture.cases | Where-Object mutation -eq 'NONE')[0].expectedReason 'NONE_PREVIEW_ELIGIBLE_NO_APPLY_CAPABILITY' 'V2_013_FIXTURE_HAPPY'

    $expectedGovernance = [ordered]@{
        'docs/adr/0030-kernel-local-fail-closed-sweep-preview.md' = '15c92e06884f4d2d622b3e4d395afff73a90f30f96f25b32978bd923f96efe9e'
        'docs/catalogos/sweep-v2-013/README.md' = '3770b20b358e60af59945fc050fb0a0000efa57deb17ecb587bbe4ef25b688f1'
        'docs/runbooks/v2-013-fundacao-kernel-sweep-preview-sol.md' = '8b838adea0840fd379d93d0d3e10dd043da6c806c4ded80562d9c6f308865274'
        'scripts/validation/Build-V2013SweepApplicabilityCatalog.ps1' = 'ce525a9142887e79e7cc7ba9de467301a9dcc792b56fa5e536022a3cba3130fb'
        'scripts/validation/Test-V2013SweepPreviewFoundation.ps1' = $null
    }
    $expectedKernel = [ordered]@{
        'src/main/java/br/com/esl/etl/v2/plataforma/reconciliacao/sweep/FailClosedSweepPreviewKernel.java' = 'ea468eac1baba24f034ec084f0e18156b1631409e4398f9c00f7a8000fc73070'
        'src/main/java/br/com/esl/etl/v2/plataforma/reconciliacao/sweep/SweepApplicability.java' = 'f5a8afa9d28ef768f5ac33d795c739facfa9d27f698c1f425c3731b621275012'
        'src/main/java/br/com/esl/etl/v2/plataforma/reconciliacao/sweep/SweepPreviewAssessment.java' = '5ae379d3b7212777bfea1a73d82cc918274cd6ce7783540a12267035cd907b79'
        'src/main/java/br/com/esl/etl/v2/plataforma/reconciliacao/sweep/SweepPreviewBlockReason.java' = '8e4285b6e374cd13ef8863c6bd3a981fbd5f002f8287dad551e9258c29cb2114'
        'src/main/java/br/com/esl/etl/v2/plataforma/reconciliacao/sweep/SweepPreviewEvidence.java' = 'd09ac5166cc2f216670d9e4cda2c991917aa9fa77e93342fe64589c3da565172'
        'src/main/java/br/com/esl/etl/v2/plataforma/reconciliacao/sweep/SweepScope.java' = '149d86186cd440d50b64b42e6bd7f1b20b47d8dbcf6361ae979692b09e052244'
    }
    $expectedTests = [ordered]@{
        'src/test/java/br/com/esl/etl/v2/plataforma/reconciliacao/sweep/FailClosedSweepPreviewKernelTest.java' = '7a82e4e21a7ddd26343abb703a6e2e1c062a85caa76f5f8ac81cc90c01783d2d'
        'src/test/java/br/com/esl/etl/v2/plataforma/reconciliacao/sweep/SweepApplicabilityMatrixTest.java' = '239276cc03ead0c4bf98947f4a388c96db0ffffbe082deacb4b1c1e2de5cdc31'
        'src/test/java/br/com/esl/etl/v2/plataforma/reconciliacao/sweep/SweepPreviewBoundaryTest.java' = 'fe2c69387a3b7c5996837cb9aa8a2ae055e4d3885d3e3f7270e3e6f168f54f41'
        'src/test/java/br/com/esl/etl/v2/plataforma/reconciliacao/sweep/SweepPreviewDeterminismTest.java' = 'bc48cafd802cdf3a583135ffbd407aae6b85ee04ac19de8a194de1fad04903f8'
        'src/test/java/br/com/esl/etl/v2/plataforma/reconciliacao/sweep/SweepPreviewHardeningTest.java' = '7fe04ae0de6a7a173af8cbed8934ad996ed9b8f5fe15efaea3efa59a83f7647f'
        'src/test/java/br/com/esl/etl/v2/plataforma/reconciliacao/sweep/SweepPreviewMutationCatalogTest.java' = 'eed372cc981395c6670ece1c5eb28fe33b1f030b5acaf0205f487c57547d234a'
        'src/test/java/br/com/esl/etl/v2/plataforma/reconciliacao/sweep/SweepPreviewTestFixture.java' = 'e9db40f48509f0f69b71e5956dd114ada92367e3557f8e6f9e16d5d17ec5f557'
    }
    Assert-ArtifactList $manifest.governanceArtifacts $expectedGovernance 'V2_013_GOVERNANCE_DRIFT'
    Assert-ArtifactList $manifest.kernelArtifacts $expectedKernel 'V2_013_KERNEL_DRIFT'
    Assert-ArtifactList $manifest.testArtifacts $expectedTests 'V2_013_TEST_DRIFT'

    $expectedBindings = [ordered]@{
        'BOOTSTRAP_ENTITY_UNIVERSE' = 'docs/catalogos/bootstrap-v2-047/manifesto.json|e6b33e3b035dcc6dd817e4f5e783e8b0585e077ab3ec4a7c5b13ced8730615be'
        'COLETAS_SHADOW' = 'database/manifest/coletas-shadow-vertical.json|68d20e991a0fb7171656245383e7ae2afe166f0295b25644d82d0ce059f31b86'
        'FRETES_SHADOW' = 'database/manifest/fretes-shadow-vertical.json|303c5a6977c8fd52067b2954fee0a1d686f35fd1a78a330762050576596b700c'
        'MANIFESTOS_SHADOW' = 'database/manifest/manifestos-shadow-vertical.json|f95bac1dd539cec9675efca1742d5a3a507f406126ed627588e0f93f3ee79891'
        'COTACOES_SHADOW' = 'database/manifest/cotacoes-shadow-vertical.json|4dc1506cebccb48071b422fee4928a8dc09497235d15abe093a9c5cf21cb509a'
        'LOCALIZACAO_SHADOW' = 'database/manifest/localizacao-cargas-shadow-vertical.json|f8dad82685ef4c5b290922af98a20c0cf50de07a07e5e4d9ea5349fca81708e2'
        'CAP_IDENTITY' = 'docs/catalogos/identidade-contas-a-pagar/manifesto.json|811f49d84f814048f67cff2682417e38c4bc0103d8ef8d9a26d3d9a9fd4e7421'
        'FATURAS_IDENTITY' = 'docs/catalogos/identidade-faturas-por-cliente/manifesto.json|1cfde9f1a5e1255848f242569442199d1938f79fecaf1282a04710049bae7bdc'
        'INVENTARIO_IDENTITY' = 'docs/catalogos/identidade-inventario/manifesto.json|2014801e35de38685b40f275e191ef6eca2b5fec98f339b52b25db5becd5d044'
        'SINISTROS_IDENTITY' = 'docs/catalogos/identidade-sinistros/manifesto.json|9cf986df26c43ad00f14f3176d61616590a81e86fcf86b1db100e37fd2473098'
        'Q_FND_02' = 'docs/catalogos/caracterizacao-v2-012/q-fnd-02/manifesto.json|45f0ac5b8f0f2a032534228b8366abc2843e4b1817a3352781d3fd128500e604'
        'KERNEL_EXECUTION_MODE' = 'src/main/java/br/com/esl/etl/v2/plataforma/controle/ExecutionMode.java|c615ae7d3982c158653644ffe39736ad529a8114fb8fa36063d74a0cb797c1fd'
        'KERNEL_SOURCE_COMPLETENESS_STATUS' = 'src/main/java/br/com/esl/etl/v2/plataforma/contrato/SourceCompletenessStatus.java|56b6f52766b3b96ef76d56c269f13ef54af05196151d8991f2508c1e722c6f4c'
    }
    Assert-OrderedExact @($manifest.canonicalBindings.role) @($expectedBindings.Keys) 'V2_013_BINDING_SET'
    foreach ($binding in @($manifest.canonicalBindings)) {
        Assert-Properties $binding @('role','path','sha256') 'V2_013_BINDING_SCHEMA'
        $expectedParts = $expectedBindings[$binding.role] -split '\|', 2
        Assert-String $binding.path $expectedParts[0] 'V2_013_BINDING_VALUE'
        Assert-String $binding.sha256 $expectedParts[1] 'V2_013_BINDING_VALUE'
        Assert-String (Get-LiveHash $binding.path) $binding.sha256 'V2_013_BINDING_DRIFT'
    }

    $expectedEvidenceSymbols = [ordered]@{
        'STATES.md' = @('CAP-01','CAP-02','CAP-04','COL-07','COT-02','FAT-01','FRE-03','FRE-04','INV-01','INV-02','INV-03','INV-04','LOC-03','MAN-01','MAN-02','MAN-03','Q-*-04','RAS-01','RAS-06','SIN-01','V007','V012','V013','V2-010','V2-011','V2-026','V2-027','V2-028','V2-029','V2-030','V2-031','V2-032','V2-033','V2-034a','V2-034b','V2-035a','V2-046a','V2-046b')
        'docs/adr/0019-usuarios-current-history-em-sombra.md' = @('ADR-0019')
        'docs/catalogos/caracterizacao-v2-012/q-fnd-02/manifesto.json' = @('Q-FND-02')
        'docs/catalogos/identidade-contas-a-pagar/manifesto.json' = @('identidade-contas-a-pagar')
        'docs/catalogos/identidade-faturas-por-cliente/manifesto.json' = @('identidade-faturas-por-cliente')
        'docs/catalogos/identidade-inventario/manifesto.json' = @('identidade-inventario')
        'docs/catalogos/identidade-sinistros/manifesto.json' = @('identidade-sinistros')
    }
    $expectedEvidenceHashes = [ordered]@{
        'docs/adr/0019-usuarios-current-history-em-sombra.md' = '8c14cebc95add8299ddbb9838836050f100d907be8801e5ea66b4bd3df72b9be'
        'docs/catalogos/caracterizacao-v2-012/q-fnd-02/manifesto.json' = '45f0ac5b8f0f2a032534228b8366abc2843e4b1817a3352781d3fd128500e604'
        'docs/catalogos/identidade-contas-a-pagar/manifesto.json' = '811f49d84f814048f67cff2682417e38c4bc0103d8ef8d9a26d3d9a9fd4e7421'
        'docs/catalogos/identidade-faturas-por-cliente/manifesto.json' = '1cfde9f1a5e1255848f242569442199d1938f79fecaf1282a04710049bae7bdc'
        'docs/catalogos/identidade-inventario/manifesto.json' = '2014801e35de38685b40f275e191ef6eca2b5fec98f339b52b25db5becd5d044'
        'docs/catalogos/identidade-sinistros/manifesto.json' = '9cf986df26c43ad00f14f3176d61616590a81e86fcf86b1db100e37fd2473098'
    }
    Assert-OrderedExact @($manifest.evidenceSources.path) @($expectedEvidenceSymbols.Keys) 'V2_013_EVIDENCE_SOURCE_SET'
    $matrixSymbols = @($rows.evidence_anchor | ForEach-Object { $_ -split ';' } | Sort-Object -Unique)
    $declaredSymbols = @($manifest.evidenceSources | ForEach-Object { @($_.symbols) })
    Assert-UniqueExactSet $declaredSymbols $matrixSymbols 'V2_013_EVIDENCE_SYMBOL_SET'
    foreach ($source in @($manifest.evidenceSources)) {
        Assert-OrderedExact @($source.symbols) @($expectedEvidenceSymbols[$source.path]) 'V2_013_EVIDENCE_SYMBOL_MAPPING'
        Assert-True (@($source.symbols).Count -gt 0) 'V2_013_EVIDENCE_EMPTY_SYMBOLS'
        if ($source.bindingMode -eq 'EXACT_SYMBOL_PRESENCE_MUTABLE_CANONICAL_STATE') {
            Assert-Properties $source @('path','bindingMode','symbols') 'V2_013_EVIDENCE_SCHEMA'
            Assert-String $source.path 'STATES.md' 'V2_013_EVIDENCE_SCHEMA'
        } else {
            Assert-Properties $source @('path','bindingMode','sha256','symbols') 'V2_013_EVIDENCE_SCHEMA'
            Assert-String $source.bindingMode 'SHA256_PINNED' 'V2_013_EVIDENCE_SCHEMA'
            Assert-String $source.sha256 $expectedEvidenceHashes[$source.path] 'V2_013_EVIDENCE_SCHEMA'
            Assert-String (Get-LiveHash $source.path) $source.sha256 'V2_013_EVIDENCE_DRIFT'
        }
        Assert-UniqueExactSet @($source.symbols) @($source.symbols | Sort-Object -Unique) 'V2_013_EVIDENCE_DUPLICATE'
        if ($source.path -eq 'STATES.md') {
            $stateEvidence = Read-StrictText (Join-Path $repositoryRoot 'STATES.md') 4000000
            foreach ($symbol in @($source.symbols)) {
                Assert-True $stateEvidence.Contains($symbol, [StringComparison]::Ordinal) 'V2_013_EVIDENCE_SYMBOL_MISSING'
            }
        }
    }

    Assert-OrderedExact $manifest.openRoutes @('V2-013','Q-*-04','V2-034a','V2-034b') 'V2_013_OPEN_GATES'
    Assert-OrderedExact $manifest.prohibitedScope @('SOURCE','NETWORK','ENV_SECRET','DATABASE','FILESYSTEM_RUNTIME','DDL','DML','MIGRATION','ENTITY_SWEEP_EXECUTION','SWEEP_MUTATION','APPLY','DELETE','DEACTIVATE','REACTIVATE','PRUNE','PERSIST','PUBLICATION','RELATION','BOOTSTRAP','PARITY','CUTOVER') 'V2_013_PROHIBITED_SCOPE'

    Assert-RecursiveAllowlist 'docs/catalogos/sweep-v2-013' @('README.md','fixtures/kernel-cases-v01.synthetic.json','manifesto.json','manifesto.sha256','matriz-aplicabilidade-v01.csv')
    Assert-RecursiveAllowlist 'src/main/java/br/com/esl/etl/v2/plataforma/reconciliacao/sweep' @($expectedKernel.Keys | ForEach-Object { Split-Path -Leaf $_ })
    Assert-RecursiveAllowlist 'src/test/java/br/com/esl/etl/v2/plataforma/reconciliacao/sweep' @($expectedTests.Keys | ForEach-Object { Split-Path -Leaf $_ })
    Assert-RecursiveAllowlist 'src/test/resources/contracts/v2-013/q-swp-fnd-01' @('kernel-cases-v01.synthetic.json')
    Assert-NamespaceAllowlist 'scripts/validation' '*V2013*' @(
        'Build-V2013SweepApplicabilityCatalog.ps1',
        'Test-V2013SweepPreviewFoundation.ps1')
    Assert-NamespaceAllowlist 'docs/adr' '0030-*' @('0030-kernel-local-fail-closed-sweep-preview.md')
    Assert-NamespaceAllowlist 'docs/runbooks' 'v2-013-*' @('v2-013-fundacao-kernel-sweep-preview-sol.md')

    $manifestHash = (Get-FileHash -LiteralPath $manifestPath -Algorithm SHA256).Hash.ToLowerInvariant()
    $sidecar = Read-StrictText (Join-Path $repositoryRoot 'docs/catalogos/sweep-v2-013/manifesto.sha256')
    Assert-String $sidecar "$manifestHash  manifesto.json`n" 'V2_013_MANIFEST_SIDECAR'

    $productSource = @($expectedKernel.Keys | ForEach-Object { Read-StrictText (Join-Path $repositoryRoot $_) }) -join "`n"
    Assert-True ($productSource -notmatch '(?i)java\.(sql|net|io)|java\.nio\.file|runtimeaction|runtimerole|sourcedataeffect|contractpromotionpermit|runtimecompositionroot|\b(jdbc|http|socket)\b') 'V2_013_PRODUCT_BOUNDARY'
    Assert-True ($productSource -notmatch '(?i)\b(apply|delete|deactivate|reactivate|prune|persist|mutate|execute)\s*\(') 'V2_013_MUTATION_CAPABILITY'
    $mainJavaRoot = Join-Path $repositoryRoot 'src/main/java'
    $kernelRoot = [IO.Path]::GetFullPath((Join-Path $repositoryRoot 'src/main/java/br/com/esl/etl/v2/plataforma/reconciliacao/sweep'))
    foreach ($runtimeFile in @(Get-ChildItem -LiteralPath $mainJavaRoot -Recurse -File -Filter '*.java')) {
        if ($runtimeFile.FullName.StartsWith($kernelRoot + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) { continue }
        $runtimeText = Read-StrictText $runtimeFile.FullName 2000000
        Assert-True ($runtimeText -notmatch '(?i)reconciliacao[./\\]sweep|FailClosedSweepPreviewKernel|SweepApplicability|SweepPreviewAssessment|SweepPreviewBlockReason|SweepPreviewEvidence|SweepScope') 'V2_013_RUNTIME_WIRING'
    }

    $builderOutput = @(& pwsh -NoProfile -File (Join-Path $repositoryRoot 'scripts/validation/Build-V2013SweepApplicabilityCatalog.ps1') -VerifyGenerated 2>&1)
    Assert-Integer $LASTEXITCODE 0 'V2_013_GENERATOR_DRIFT'
    Assert-String ($builderOutput -join "`n") 'V2_013_SWEEP_CATALOG status=PASS mode=VERIFY rows=33 families=11' 'V2_013_GENERATOR_DRIFT'

    if (-not $ArtifactsOnly) {
        $states = Read-StrictText (Join-Path $repositoryRoot 'STATES.md') 4000000
        $trail = Read-StrictText (Join-Path $repositoryRoot 'docs/runbooks/trilha-de-chats-gpt-5-6.md') 4000000
        Assert-Integer @([regex]::Matches($states, '(?m)^- \[ \] \*\*V2-013 —')).Count 1 'V2_013_PARENT_MUST_REMAIN_OPEN'
        Assert-Integer @([regex]::Matches($states, '(?m)^- \[x\] \*\*V2-013 —')).Count 0 'V2_013_PARENT_MUST_REMAIN_OPEN'
        Assert-Integer @([regex]::Matches($states, '(?m)^\s*- \[x\] \*\*V2-013/FUNDACAO_KERNEL_LOCAL')).Count 1 'V2_013_CLOSURE_STATE'
        Assert-Integer @([regex]::Matches($states, '(?m)^\s*- \[ \] \*\*V2-013/FUNDACAO_KERNEL_LOCAL')).Count 0 'V2_013_CLOSURE_STATE'
        Assert-Integer @([regex]::Matches($states, '(?m)^- \[ \] \*\*V2-050 —')).Count 1 'V2_050_PARENT_MUST_REMAIN_OPEN'
        Assert-Integer @([regex]::Matches($states, '(?m)^- \[x\] \*\*V2-050 —')).Count 0 'V2_050_PARENT_MUST_REMAIN_OPEN'
        Assert-Integer @([regex]::Matches($states, '(?m)^\s*- \[ \] \*\*V2-050/FUNDACAO_MEDICAO_LOCAL')).Count 1 'V2_050_FOUNDATION_MUST_BE_OPEN'
        Assert-Integer @([regex]::Matches($states, '(?m)^\s*- \[x\] \*\*V2-050/FUNDACAO_MEDICAO_LOCAL')).Count 0 'V2_050_FOUNDATION_MUST_BE_OPEN'
        $stateCheckboxes = @([regex]::Matches($states, '(?m)^\s*- \[(?<mark>[ xX])\]'))
        $stateDone = @($stateCheckboxes | Where-Object { $_.Groups['mark'].Value -match '[xX]' }).Count
        Assert-Integer $stateCheckboxes.Count 107 'V2_013_CLOSURE_COUNTS'
        Assert-Integer $stateDone 52 'V2_013_CLOSURE_COUNTS'
        Assert-Integer ($stateCheckboxes.Count - $stateDone) 55 'V2_013_CLOSURE_COUNTS'
        Assert-True $states.Contains('FOUNDATION_LOCAL_COMPLETE_NO_ENTITY_SWEEP_ENABLED', [StringComparison]::Ordinal) 'V2_013_CLOSURE_STATE'
        Assert-True $states.Contains('rotas Q-*-04 permanecem abertas', [StringComparison]::Ordinal) 'V2_013_ENTITY_ROUTES_MUST_REMAIN_OPEN'
        $entitySweepRoutePattern = '(?m)^- \[ \] STATUS=CANDIDATO \| ROTA=Q-(?:USR|COL|MAN|COT|CAP|FRE|LOC|FAT|INV|SIN)-04 \| TAREFA=V2-013 \|'
        Assert-Integer @([regex]::Matches($trail, $entitySweepRoutePattern)).Count 10 'V2_013_ENTITY_ROUTES_MUST_REMAIN_OPEN'
        Assert-Integer @([regex]::Matches($trail, '(?m)^- \[x\].*\| ROTA=Q-(?:USR|COL|MAN|COT|CAP|FRE|LOC|FAT|INV|SIN)-04 \|')).Count 0 'V2_013_ENTITY_ROUTES_MUST_REMAIN_OPEN'
        $closedTrailPattern = '(?m)^- \[x\] STATUS=CONCLUIDO \| ROTA=Q-SWP-FND-01 \| BLOCO=49 \| TAREFA=V2-013/FUNDACAO_KERNEL_LOCAL \| .* \| RESULTADO=FOUNDATION_LOCAL_COMPLETE_NO_ENTITY_SWEEP_ENABLED \| EVIDENCIA=STATES\.md$'
        Assert-Integer @([regex]::Matches($trail, $closedTrailPattern)).Count 1 'V2_013_CLOSURE_TRAIL'
        Assert-Integer @([regex]::Matches($trail, '(?m)^- \[ \] STATUS=AGORA \| ROTA=Q-SWP-FND-01 \|')).Count 0 'V2_013_CLOSURE_TRAIL'
        $nextTrailPattern = '(?m)^- \[ \] STATUS=AGORA \| ROTA=Q-MED-FND-01 \| BLOCO=50 \| TAREFA=V2-050/FUNDACAO_MEDICAO_LOCAL \| FATIA=FUNDACAO_MEDICAO_LOCAL_MULTIESCALA_TEST_ONLY \| .* \| MODELO=SOL_ULTRA \| DEPENDE=V2-021\+V2-023\+Q-FND-01\+Q-FND-02\+Q-SWP-FND-01 \| PROIBE=REDE\+FONTE\+ENV_SECRET\+BANCO\+SQL_PLAN_REAL\+DDL\+DML\+MIGRATION\+ORACULO_EXTERNO\+HEAP_OU_SLO_PRODUTIVO\+PUBLICACAO\+SWEEP\+PRUNE\+CUTOVER$'
        Assert-Integer @([regex]::Matches($trail, $nextTrailPattern)).Count 1 'V2_013_CLOSURE_TRAIL'
        Assert-Integer @([regex]::Matches($trail, '(?m)^- \[ \] STATUS=AGORA \|')).Count 1 'V2_013_CLOSURE_TRAIL'
        $inventedStateRoutePattern = '(?im)^\s*- \[[ xX]\].*\b(?:RAS-(?:0[7-9]|1[01])|Q-(?:USR|COL|MAN|COT|CAP|FRE|LOC|FAT|INV|SIN)-04)\b'
        $inventedTrailRoutePattern = '(?i)\bRAS-(?:0[7-9]|1[01])\b'
        Assert-True ($states -notmatch $inventedStateRoutePattern) 'V2_013_INVENTED_STATE_ROUTE'
        Assert-True ($trail -notmatch $inventedTrailRoutePattern) 'V2_013_INVENTED_TRAIL_ROUTE'
    }

    $mode = if ($ArtifactsOnly) { 'ARTIFACTS_ONLY' } else { 'FULL' }
    [Console]::Out.WriteLine("V2_013_SWEEP_FOUNDATION status=PASS mode=$mode rows=33 families=11 enabled=0 provenComplete=0 cases=53 mutationsCataloged=52 executionGate=SweepPreviewMutationCatalogTest_SEPARATE")
    exit 0
} catch {
    $reason = $_.Exception.Message
    if ([string]::IsNullOrWhiteSpace($reason)) { $reason = 'V2_013_SWEEP_FOUNDATION_UNKNOWN_FAILURE' }
    [Console]::Out.WriteLine("V2_013_SWEEP_FOUNDATION status=FAIL reason=$reason")
    exit 1
}
