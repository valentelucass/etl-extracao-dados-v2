[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$manifestPath = Join-Path $repositoryRoot 'database\manifest\governed-references.json'
$fingerprintPath = Join-Path $repositoryRoot 'database\manifest\governed-references.sha256'
$migrationPath = Join-Path $repositoryRoot 'database\migrations\V008__create_governed_references.sql'
$baselinePath = Join-Path $repositoryRoot 'database\baseline\001_schema_foundation_baseline.sql'
$validatorPath = Join-Path $repositoryRoot 'database\validation\030_validate_governed_references.sql'
$exercisePath = Join-Path $repositoryRoot 'database\validation\031_exercise_governed_references_rollback.sql'
$migratorExercisePath = Join-Path $repositoryRoot 'database\validation\032_exercise_governed_references_migrator_rollback.sql'
$negativeExercisePath = Join-Path $repositoryRoot 'database\validation\033_exercise_governed_references_negative_rollback.sql'
$concurrencyGatePath = Join-Path $repositoryRoot 'scripts\validation\Test-GovernedReferencesConcurrency.ps1'
$showplanExercisePath = Join-Path $repositoryRoot 'database\validation\034_validate_governed_references_showplan.sql'
$showplanGatePath = Join-Path $repositoryRoot 'scripts\validation\Test-GovernedReferencesShowplan.ps1'
$adrPath = Join-Path $repositoryRoot 'docs\adr\0020-referencias-governadas-sem-default-produtivo.md'
$runbookPath = Join-Path $repositoryRoot 'docs\runbooks\importar-ratificar-e-revogar-referencias.md'

function Require-True {
    param([bool]$Condition, [string]$Message)
    if (-not $Condition) { throw $Message }
}

function Test-OrdinalContains {
    param([string]$Text, [string]$Value)
    return $Text.IndexOf($Value, [StringComparison]::Ordinal) -ge 0
}

function Get-Sha256Hex {
    param([byte[]]$Bytes)
    $sha256 = [Security.Cryptography.SHA256]::Create()
    try {
        return ([Convert]::ToHexString($sha256.ComputeHash($Bytes))).ToLowerInvariant()
    } finally {
        $sha256.Dispose()
    }
}

function New-PackageIndexBytes {
    param(
        [Parameter(Mandatory)]$Framing,
        [Parameter(Mandatory)][int]$SchemaVersion,
        [Parameter(Mandatory)][string]$FamilyCode,
        [Parameter(Mandatory)][object[]]$TableEntries
    )

    $lines = @(
        [string]$Framing.magicLine,
        ([string]$Framing.schemaVersionLinePrefix `
            + $SchemaVersion.ToString([Globalization.CultureInfo]::InvariantCulture)),
        ([string]$Framing.familyCodeLinePrefix + $FamilyCode)
    )
    foreach ($entry in $TableEntries) {
        $values = @{
            table = [string]$entry.table
            rows = ([long]$entry.rowCount).ToString([Globalization.CultureInfo]::InvariantCulture)
            bytes = ([long]$entry.canonicalByteCount).ToString(
                [Globalization.CultureInfo]::InvariantCulture
            )
            sha256 = [string]$entry.expectedTableSha256
        }
        $fields = @($Framing.tableLineFieldOrder | ForEach-Object {
            [string]$_ + [string]$Framing.keyValueSeparator + [string]$values[[string]$_]
        })
        $lines += $fields -join [string]$Framing.fieldSeparator
    }
    $indexText = ($lines -join "`n") + "`n"
    return ,([Text.UTF8Encoding]::new($false).GetBytes($indexText))
}

foreach ($requiredPath in @(
        $manifestPath, $fingerprintPath, $migrationPath, $baselinePath,
        $validatorPath, $exercisePath, $migratorExercisePath, $negativeExercisePath,
        $concurrencyGatePath, $showplanExercisePath, $showplanGatePath, $adrPath,
        $runbookPath
    )) {
    Require-True (Test-Path -LiteralPath $requiredPath -PathType Leaf) `
        "Artefato obrigatório de referências ausente: $requiredPath"
}

$manifest = Get-Content -LiteralPath $manifestPath -Raw -Encoding utf8 | ConvertFrom-Json
$migration = Get-Content -LiteralPath $migrationPath -Raw -Encoding utf8
$baseline = Get-Content -LiteralPath $baselinePath -Raw -Encoding utf8
$validator = Get-Content -LiteralPath $validatorPath -Raw -Encoding utf8
$exercise = Get-Content -LiteralPath $exercisePath -Raw -Encoding utf8
$migratorExercise = Get-Content -LiteralPath $migratorExercisePath -Raw -Encoding utf8
$negativeExercise = Get-Content -LiteralPath $negativeExercisePath -Raw -Encoding utf8
$concurrencyGate = Get-Content -LiteralPath $concurrencyGatePath -Raw -Encoding utf8
$showplanExercise = Get-Content -LiteralPath $showplanExercisePath -Raw -Encoding utf8
$showplanGate = Get-Content -LiteralPath $showplanGatePath -Raw -Encoding utf8
$adr = Get-Content -LiteralPath $adrPath -Raw -Encoding utf8
$runbook = Get-Content -LiteralPath $runbookPath -Raw -Encoding utf8

Require-True ($manifest.manifestVersion -eq 1) 'A versão do manifesto de referências deve ser 1.'
Require-True ($manifest.roadmapTask -ceq 'V2-035a') 'O manifesto deve pertencer a V2-035a.'
Require-True ($manifest.localState -ceq `
        'FOUNDATION_OFFLINE_COMPLETE_EXTERNAL_BASELINE_PENDING') `
    'O estado local/externo de V2-035a diverge.'
Require-True ($manifest.migration -ceq 'V008__create_governed_references.sql') `
    'A migration governada diverge.'
Require-True ($manifest.productiveDefaults -eq $false `
        -and $manifest.migrationCreatesRows -eq $false `
        -and $manifest.releaseSelection -ceq 'EXPLICIT_RELEASE_ID_ONLY') `
    'A fundação não pode criar conteúdo/default produtivo nem ponteiro corrente.'
Require-True ($manifest.runtimeDirectReferenceAccess -ceq 'PROHIBITED') `
    'O runtime deve permanecer sem acesso direto em ref.'
Require-True ($manifest.civilDatePolicy.fixedHorizon -eq $false `
        -and $manifest.civilDatePolicy.maximumRequestedDays -eq 3660 `
        -and $manifest.civilDatePolicy.maximumLookbackDays -eq 31 `
        -and $manifest.civilDatePolicy.candidateAddsBoundedLookbackWhenValidFromIsNotBusiness `
            -eq $true `
        -and $manifest.civilDatePolicy.lookbackRowsCountInSourceRowsAndCanonicalBytes `
            -eq $true `
        -and $manifest.civilDatePolicy.languageOrDateFirstDependency -eq $false) `
    'O contrato de calendário rolante/limitado diverge.'
Require-True ($manifest.governance.ratificationRequiresDistinctAuthorAndApproverRoles -eq $true `
        -and $manifest.governance.ratificationRequiresDistinctImporterAndApproverRoles `
            -eq $true `
        -and $manifest.governance.ratificationChecksPhysicalRowCount -eq $true `
        -and $manifest.governance.ratificationRequiresReceiptSealedAgainstReleaseFingerprint `
            -eq $true `
        -and $manifest.governance.approvalFingerprintMeaning -ceq `
            'SHA256_OF_APPROVAL_EVIDENCE_NOT_CONTENT_FINGERPRINT' `
        -and $manifest.governance.PSObject.Properties.Name -cnotcontains `
            'ratificationRequiresMatchingFingerprintReceipt' `
        -and $manifest.governance.activationScopesAreIndependent -eq $true `
        -and $manifest.governance.ratifiedValidityOverlap -ceq `
            'REJECTED_PER_ACTIVATION_SCOPE' `
        -and $manifest.governance.activePointerOrFallback -ceq 'ABSENT') `
    'A governança append-only/fail-closed diverge.'
Require-True ($manifest.documentTokenContract.schemeVersionIsPartOfGrainAndLookup -eq $true `
        -and $manifest.documentTokenContract.comparison -ceq `
            'EXACT_BIN2_SCHEME_VERSION_AND_TOKEN' `
        -and $manifest.documentTokenContract.unknownSchemeVersion -ceq `
            'NO_MATCH_FAIL_CLOSED') `
    'O contrato versionado de token documental diverge.'
Require-True ($manifest.canonicalKeyContract.input -ceq `
        'OWNER_AUTHORED_CANONICAL_KEY_ALREADY_NORMALIZED' `
        -and $manifest.canonicalKeyContract.comparison -ceq `
            'EXACT_BIN2_NORMALIZATION_VERSION_AND_KEY' `
        -and $manifest.canonicalKeyContract.normalizationVersionIsPartOfGrainAndLookup `
            -eq $true `
        -and $manifest.canonicalKeyContract.genericNormalizerPublishedInV008 -eq $false `
        -and $manifest.canonicalKeyContract.unknownNormalizationVersion -ceq `
            'NO_MATCH_FAIL_CLOSED' `
        -and (@($manifest.canonicalKeyContract.versionedTables) -join '|') -ceq (
            'ref.regiao_destino_alias|ref.classificacao_frota_alias|' `
            + 'ref.regiao_logistica_cidade_uf'
        )) `
    'O contrato versionado das chaves canônicas genéricas diverge.'

$expectedFamilies = @(
    'CALENDAR', 'PICK_STATUS', 'BRANCH_OPERATIONS', 'OWNED_FLEET',
    'BRANCH_ATTRIBUTION', 'CUBAGE_EXCLUSION', 'LOGISTICS_REGION', 'QUOTE_TARIFF'
)
$actualFamilies = @($manifest.families | ForEach-Object familyCode)
Require-True ($actualFamilies.Count -eq 8 `
        -and @(Compare-Object $expectedFamilies $actualFamilies -CaseSensitive).Count -eq 0) `
    'As oito famílias tipadas de referência divergem.'
foreach ($externalFamily in @(
        'BRANCH_OPERATIONS', 'OWNED_FLEET', 'BRANCH_ATTRIBUTION',
        'CUBAGE_EXCLUSION', 'LOGISTICS_REGION', 'QUOTE_TARIFF'
    )) {
    $family = @($manifest.families | Where-Object familyCode -CEQ $externalFamily)
    Require-True ($family.Count -eq 1 `
            -and $family[0].externalBaseline -match 'REQUIRED') `
        "A família mutável $externalFamily não preserva o gate externo."
}
$tariff = @($manifest.families | Where-Object familyCode -CEQ 'QUOTE_TARIFF')[0]
Require-True ($tariff.routeDirection -ceq 'DIRECTED' `
        -and $tariff.amountType -ceq 'DECIMAL(19,4)' `
        -and $tariff.missingCombination -ceq 'NO_MATCH_FAIL_CLOSED_NEVER_ZERO') `
    'A tarifa direcional/decimal/sem fallback diverge.'
$region = @($manifest.families | Where-Object familyCode -CEQ 'LOGISTICS_REGION')[0]
Require-True ($region.selectorModel -ceq 'SEPARATE_TABLES_XOR_BY_CONSTRUCTION' `
        -and $region.lookupKey[0] -ceq 'CEP_FIRST' `
        -and $region.lookupKey[1] -ceq 'CITY_UF_SECOND') `
    'A precedência CEP→cidade/UF não está tipada.'
$fleet = @($manifest.families | Where-Object familyCode -CEQ 'OWNED_FLEET')[0]
Require-True ((@($fleet.classificationPolicies.DRIVER_OWNERSHIP) -join '|') -ceq (
        '1_DOCUMENT_TOKEN_MEMBERSHIP_RETURNS_FLEET|' `
        + '2_DRIVER_OWNERSHIP_OWNER_NAME_TOKEN_EXCEPTION_LOWEST_PRIORITY_NUMBER_WINS|' `
        + '3_NULL_OR_TRIMMED_EMPTY_DRIVER_OWNERSHIP_CONTRACT_RETURNS_THIRD_PARTY|' `
        + '4_EXACT_VERSIONED_DRIVER_OWNERSHIP_CONTRACT_ALIAS|' `
        + '5_NONEMPTY_NO_MATCH_FAIL_CLOSED'
    ) -and (@($fleet.classificationPolicies.VEHICLE_DRIVER_CONTRACT) -join '|') -ceq (
        '1_EXACT_VERSIONED_VEHICLE_CONTRACT_ALIAS|' `
        + '2_OWNER_NAME_TOKEN_EXCEPTION_ONLY_WHEN_REQUIRED_VEHICLE_CLASS_MATCHES|' `
        + '3_NULL_OR_TRIMMED_EMPTY_DRIVER_CONTRACT_MAPS_TO_UNSPECIFIED_THEN_MATRIX|' `
        + '4_EXACT_VERSIONED_DRIVER_CONTRACT_ALIAS_THEN_MATRIX|' `
        + '5_NONEMPTY_NO_MATCH_FAIL_CLOSED'
    ) -and $fleet.missingInputContract.definition -ceq `
        'NULL_OR_TRIMMED_U0020_EMPTY_ONLY' `
        -and $fleet.missingInputContract.DRIVER_OWNERSHIP -ceq `
            'THIRD_PARTY_AFTER_MEMBERSHIP_AND_OWNER_EXCEPTION' `
        -and $fleet.missingInputContract.VEHICLE_DRIVER_CONTRACT -ceq `
            'UNSPECIFIED_AFTER_VEHICLE_ALIAS_AND_OWNER_EXCEPTION_THEN_MATRIX' `
        -and $fleet.missingInputContract.nonEmptyUnknown -ceq `
            'NO_MATCH_FAIL_CLOSED' `
        -and $fleet.matrixCoverageContract.supportedCartesianShape -ceq `
            '2_VEHICLE_CLASSES_X_4_DRIVER_CLASSES' `
        -and $fleet.matrixCoverageContract.v008CoverageEnforcement -ceq `
            'NOT_PUBLISHED_NO_CONTENT_IMPORTER_OR_CONSUMER' `
        -and $fleet.matrixCoverageContract.missingCell -ceq `
            'NO_MATCH_FAIL_CLOSED') `
    'As duas políticas de frota ou seus estados de ausência divergiram.'
Require-True ($manifest.documentTokenContract.rawDocumentInDatabaseOrRepository -eq $false `
        -and $manifest.documentTokenContract.schemeVersionRequired -eq $true `
        -and $manifest.documentTokenContract.secretInProcessArgumentsOrArtifacts -eq $false) `
    'O contrato de minimização/tokenização documental diverge.'
Require-True ($manifest.importContract.maximumRowsPerRelease -eq 100000 `
        -and $manifest.importContract.maximumBytesPerRelease -eq 16777216 `
        -and $manifest.importContract.maximumRowsInFlight -eq 1000 `
        -and $manifest.importContract.relationalValidationAndPromotion -ceq 'SET_BASED_SQL' `
        -and $manifest.importContract.javaFullDatasetCollections -ceq 'PROHIBITED' `
        -and $manifest.importContract.releaseRegistrationEntrypointPublishedInV008 -eq $true `
        -and $manifest.importContract.contentImportEntrypointPublishedInV008 -eq $false `
        -and $manifest.importContract.importEntrypointPublishedInV008 -eq $false) `
    'Os limites/import contract offline divergem.'
Require-True ((@($manifest.importContract.supportedSchemaVersions) -join '|') -ceq '1' `
        -and $manifest.importContract.contractVersionToSchemaVersion.'governed-references-v1' `
            -eq 1) 'O mapeamento contrato→schema suportado diverge.'
foreach ($requiredCanonicalField in @(
        'canonicalEncoding', 'canonicalLineEnding', 'canonicalFileTermination',
        'canonicalHeader', 'canonicalQuoting', 'nullEncoding', 'booleanEncoding',
        'dateEncoding', 'integerEncoding', 'decimalEncoding', 'textNormalization',
        'canonicalOrdering', 'fingerprintInput',
        'fingerprintAlgorithm', 'sourceRowCountDefinition', 'importedByteCountDefinition'
    )) {
    Require-True (-not [string]::IsNullOrWhiteSpace(
            [string]$manifest.importContract.$requiredCanonicalField
        )) "Serialização canônica não define $requiredCanonicalField."
}
$framing = $manifest.importContract.packageFraming
Require-True ($framing.encoding -ceq 'UTF-8_NO_BOM' `
        -and $framing.lineEnding -ceq 'LF' `
        -and $framing.termination -ceq 'EXACTLY_ONE_TRAILING_LF' `
        -and -not [string]::IsNullOrWhiteSpace([string]$framing.magicLine) `
        -and -not [string]::IsNullOrWhiteSpace([string]$framing.schemaVersionLinePrefix) `
        -and -not [string]::IsNullOrWhiteSpace([string]$framing.familyCodeLinePrefix) `
        -and (@($framing.tableLineFieldOrder) -join '|') -ceq 'table|rows|bytes|sha256' `
        -and $framing.keyValueSeparator -ceq '=' `
        -and $framing.fieldSeparator -ceq ';' `
        -and $framing.tableIdSource -ceq 'tableContracts.table' `
        -and $framing.tableOrder -ceq 'fileOrdinal_ASC_WITHIN_FAMILY' `
        -and $framing.includedTables -ceq 'ALL_AND_ONLY_FAMILY_TABLES_EVEN_WHEN_EMPTY') `
    'O framing máquina-legível do índice canônico diverge.'

$expectedContentTables = @(
    'ref.calendario', 'ref.status_coleta', 'ref.filial_operacional',
    'ref.regiao_destino_alias', 'ref.filial_operacional_documento',
    'ref.frota_propria_documento', 'ref.classificacao_frota_alias',
    'ref.classificacao_frota_matriz', 'ref.classificacao_frota_excecao_token',
    'ref.atribuicao_filial', 'ref.pagador_exclusao_cubagem',
    'ref.regiao_logistica_cep', 'ref.regiao_logistica_cidade_uf', 'ref.tarifa_rota_uf'
)
$tableContracts = @($manifest.tableContracts)
Require-True ($tableContracts.Count -eq $expectedContentTables.Count) `
    'O manifesto deve declarar exatamente os 14 contratos físicos de conteúdo.'
Require-True (@(Compare-Object $expectedContentTables @($tableContracts.table) `
        -CaseSensitive).Count -eq 0) 'As tabelas do contrato de importação divergem.'
Require-True (@(Compare-Object (1..14) @($tableContracts.fileOrdinal)).Count -eq 0) `
    'Os ordinais canônicos de arquivo devem ser únicos e contínuos de 1 a 14.'
Require-True (@($tableContracts.fileName | Sort-Object -Unique).Count -eq 14) `
    'Os nomes canônicos de arquivo devem ser únicos.'
foreach ($tableContract in $tableContracts) {
    $tableName = [string]$tableContract.table
    $objectName = $tableName.Substring('ref.'.Length)
    $tableMatch = [Text.RegularExpressions.Regex]::Match(
        $migration,
        "(?ms)^CREATE TABLE ref\.$([Regex]::Escape($objectName)) \((.*?)^\);\s*`r?`nGO\s*`$"
    )
    Require-True $tableMatch.Success "DDL da tabela $tableName não pôde ser isolado."
    $tableDefinition = $tableMatch.Groups[1].Value
    $columns = @($tableContract.columns)
    Require-True ($columns.Count -gt 0) "Contrato $tableName não declara colunas."
    Require-True (@($columns.name | Sort-Object -Unique).Count -eq $columns.Count) `
        "Contrato $tableName repete coluna."
    $lastPosition = -1
    foreach ($column in $columns) {
        Require-True ($column.source -cin @(
                'ENVELOPE_RELEASE_ID', 'ENVELOPE_FAMILY_CODE', 'FILE',
                'RESOLVED_FOREIGN_KEY'
            )) `
            "Origem da coluna $tableName.$($column.name) é inválida."
        $nullToken = if ([bool]$column.nullable) { 'NULL' } else { 'NOT NULL' }
        $columnPattern = "(?im)^\s*$([Regex]::Escape([string]$column.name))\s+" `
            + "$([Regex]::Escape([string]$column.type))" `
            + "(?:\s+COLLATE\s+Latin1_General_100_BIN2)?\s+$nullToken(?:\s|,|`$)"
        $columnMatch = [Text.RegularExpressions.Regex]::Match($tableDefinition, $columnPattern)
        Require-True $columnMatch.Success `
            "DDL de $tableName.$($column.name) diverge em tipo ou nulabilidade."
        Require-True ($columnMatch.Index -gt $lastPosition) `
            "Ordem física de $tableName.$($column.name) diverge do manifesto."
        $lastPosition = $columnMatch.Index
    }
    $fileColumns = @($columns | Where-Object source -CEQ 'FILE' | ForEach-Object name)
    $sourceOnlyColumns = @()
    if ($null -ne $tableContract.PSObject.Properties['sourceOnlyColumns']) {
        $sourceOnlyColumns = @($tableContract.sourceOnlyColumns | ForEach-Object name)
    }
    $payloadColumns = @($tableContract.payloadColumns)
    Require-True ($payloadColumns.Count -eq ($fileColumns.Count + $sourceOnlyColumns.Count)) `
        "Header de $tableName não cobre exatamente colunas FILE + source-only."
    Require-True (@(Compare-Object $payloadColumns @($fileColumns + $sourceOnlyColumns) `
            -CaseSensitive).Count -eq 0) `
        "Header de $tableName contém coluna física resolvida ou omite payload."
    if ($tableName -cne 'ref.atribuicao_filial') {
        Require-True (($payloadColumns -join '|') -ceq ($fileColumns -join '|')) `
            "Ordem lógica do header de $tableName diverge da ordem física FILE."
    }
    foreach ($grainColumn in @($tableContract.grain)) {
        Require-True ($grainColumn -cin $payloadColumns) `
            "Grão $grainColumn de $tableName não pertence ao header do arquivo."
    }
    $primaryKeyMatch = [Text.RegularExpressions.Regex]::Match(
        $tableDefinition,
        '(?ms)CONSTRAINT\s+PK_[A-Za-z0-9_]+\s+PRIMARY KEY CLUSTERED\s*\((.*?)\)'
    )
    Require-True $primaryKeyMatch.Success "PK física de $tableName não pôde ser isolada."
    $actualPrimaryKey = @($primaryKeyMatch.Groups[1].Value -split ',' | ForEach-Object {
        (($_ -replace '(?i)\s+(ASC|DESC)\s*$', '').Trim()).Trim('[', ']')
    })
    $expectedPrimaryKey = @('reference_release_id') + @($tableContract.grain)
    Require-True (($actualPrimaryKey -join '|') -ceq ($expectedPrimaryKey -join '|')) `
        "PK física de $tableName diverge de reference_release_id + grão do manifesto."
}
Require-True (@($tableContracts | Where-Object table -CEQ 'ref.atribuicao_filial')[0].payloadColumns `
        -cnotcontains 'branch_reference_release_id') `
    'Identity BIGINT da release de filial não pode integrar payload/hash portátil.'
$attributionContract = @($tableContracts | Where-Object table -CEQ 'ref.atribuicao_filial')[0]
Require-True ((@($attributionContract.payloadColumns) -join '|') -ceq (
        'payer_document_token|token_scheme_version|branch_release_scope_code|' `
        + 'branch_release_version|branch_code|valid_from|valid_to_exclusive|reason_code'
    )) 'Header lógico portátil da atribuição diverge.'
Require-True ($attributionContract.foreignKeyResolution.resolvedColumn -ceq `
        'branch_reference_release_id' `
        -and $attributionContract.foreignKeyResolution.familyCode -ceq 'BRANCH_OPERATIONS') `
    'Resolução natural da release de filial diverge.'

$golden = $manifest.canonicalGoldenFixture
$goldenBytes = [Convert]::FromBase64String([string]$golden.canonicalBytesBase64)
Require-True ($goldenBytes.Length -eq [int]$golden.canonicalByteCount) `
    'Byte count da fixture canônica diverge.'
Require-True (-not ($goldenBytes.Length -ge 3 `
        -and $goldenBytes[0] -eq 0xEF -and $goldenBytes[1] -eq 0xBB `
        -and $goldenBytes[2] -eq 0xBF)) 'Fixture canônica contém BOM.'
Require-True (-not ($goldenBytes -contains 13) -and $goldenBytes[-1] -eq 10) `
    'Fixture canônica deve usar somente LF e terminar em LF.'
$strictUtf8 = [Text.UTF8Encoding]::new($false, $true)
$goldenText = $strictUtf8.GetString($goldenBytes)
Require-True ($goldenText.Normalize([Text.NormalizationForm]::FormC) -ceq $goldenText) `
    'Fixture canônica não está em NFC.'
$goldenContract = @($tableContracts | Where-Object table -CEQ $golden.table)[0]
$expectedHeader = (@($goldenContract.payloadColumns) -join ',') + "`n"
Require-True ($goldenText.StartsWith($expectedHeader, [StringComparison]::Ordinal)) `
    'Header da fixture canônica diverge da ordem lógica declarada.'
$tableHash = Get-Sha256Hex -Bytes $goldenBytes
Require-True ($tableHash -ceq [string]$golden.expectedTableSha256) `
    'SHA-256 da tabela canônica diverge da fixture.'
$singleTableEntry = [pscustomobject]@{
    table = [string]$golden.table
    rowCount = [long]$golden.rowCount
    canonicalByteCount = [long]$golden.canonicalByteCount
    expectedTableSha256 = $tableHash
}
$packageBytes = New-PackageIndexBytes -Framing $framing `
    -SchemaVersion $golden.schemaVersion -FamilyCode $golden.familyCode `
    -TableEntries @($singleTableEntry)
$packageHash = Get-Sha256Hex -Bytes $packageBytes
Require-True ($packageHash -ceq [string]$golden.expectedPackageIndexSha256) `
    'SHA-256 do índice canônico do pacote diverge da fixture.'

$packageGolden = $manifest.packageIndexGoldenFixture
$familyContracts = @($tableContracts | Where-Object familyCode -CEQ $packageGolden.familyCode `
    | Sort-Object fileOrdinal)
$packageGoldenTables = @($packageGolden.tables)
Require-True ($packageGoldenTables.Count -gt 1 `
        -and @($packageGoldenTables | Where-Object rowCount -eq 0).Count -gt 0 `
        -and (@($familyContracts.table) -join '|') -ceq (@($packageGoldenTables.table) -join '|')) `
    'A golden do pacote deve cobrir, em ordem, família multi-tabela e arquivo vazio.'
foreach ($entry in $packageGoldenTables) {
    $entryBytes = [Convert]::FromBase64String([string]$entry.canonicalBytesBase64)
    $entryContract = @($tableContracts | Where-Object table -CEQ $entry.table)[0]
    $entryText = $strictUtf8.GetString($entryBytes)
    Require-True ($entryBytes.Length -eq [int]$entry.canonicalByteCount `
            -and (Get-Sha256Hex -Bytes $entryBytes) -ceq [string]$entry.expectedTableSha256 `
            -and $entryText.StartsWith(
                ((@($entryContract.payloadColumns) -join ',') + "`n"),
                [StringComparison]::Ordinal
            ) `
            -and -not ($entryBytes -contains 13) `
            -and $entryBytes[-1] -eq 10) `
        "Golden canônica da tabela $($entry.table) diverge."
    if ([long]$entry.rowCount -eq 0) {
        Require-True ($entryText -ceq ((@($entryContract.payloadColumns) -join ',') + "`n")) `
            "Arquivo vazio $($entry.table) contém linha de dados."
    }
}
$multiPackageBytes = New-PackageIndexBytes -Framing $framing `
    -SchemaVersion $packageGolden.schemaVersion -FamilyCode $packageGolden.familyCode `
    -TableEntries $packageGoldenTables
Require-True ($multiPackageBytes.Length -eq [int]$packageGolden.expectedIndexByteCount `
        -and [Convert]::ToBase64String($multiPackageBytes) -ceq `
            [string]$packageGolden.expectedIndexBytesBase64 `
        -and (Get-Sha256Hex -Bytes $multiPackageBytes) -ceq `
            [string]$packageGolden.expectedPackageIndexSha256) `
    'Bytes/hash da golden multi-tabela do índice canônico divergiram.'
$nullHash = Get-Sha256Hex -Bytes ([Text.Encoding]::UTF8.GetBytes("value`n\N`n"))
$emptyHash = Get-Sha256Hex -Bytes ([Text.Encoding]::UTF8.GetBytes("value`n`"`"`n"))
Require-True ($nullHash -ceq [string]$manifest.canonicalDistinctionFixture.expectedNullSha256 `
        -and $emptyHash -ceq [string]$manifest.canonicalDistinctionFixture.expectedEmptyStringSha256 `
        -and $nullHash -cne $emptyHash) 'NULL e string vazia não têm distinção canônica.'
Require-True (@($manifest.externalGates).Count -eq 3 `
        -and @($manifest.externalGates | Where-Object condition -match 'AUTHORIZED').Count -ge 2) `
    'Os subgates externos não estão explícitos.'
Require-True ($manifest.verification.structuralValidator -ceq `
        'database/validation/030_validate_governed_references.sql' `
        -and $manifest.verification.rollbackExercise -ceq `
            'database/validation/031_exercise_governed_references_rollback.sql' `
        -and $manifest.verification.migratorExercise -ceq `
            'database/validation/032_exercise_governed_references_migrator_rollback.sql' `
        -and $manifest.verification.negativeExercise -ceq `
            'database/validation/033_exercise_governed_references_negative_rollback.sql' `
        -and $manifest.verification.concurrencyGate -ceq `
            'scripts/validation/Test-GovernedReferencesConcurrency.ps1' `
        -and $manifest.verification.showplanExercise -ceq `
            'database/validation/034_validate_governed_references_showplan.sql' `
        -and $manifest.verification.showplanGate -ceq `
            'scripts/validation/Test-GovernedReferencesShowplan.ps1') `
    'O inventário de validação dinâmica das referências diverge.'

$expectedTables = @(
    'ref.reference_release', 'ref.reference_import_receipt',
    'ref.reference_release_ratification',
    'ref.reference_release_revocation', 'ref.calendario', 'ref.status_coleta',
    'ref.filial_operacional', 'ref.regiao_destino_alias',
    'ref.filial_operacional_documento', 'ref.frota_propria_documento',
    'ref.classificacao_frota_alias', 'ref.classificacao_frota_matriz',
    'ref.classificacao_frota_excecao_token',
    'ref.atribuicao_filial', 'ref.pagador_exclusao_cubagem',
    'ref.regiao_logistica_cep', 'ref.regiao_logistica_cidade_uf',
    'ref.tarifa_rota_uf'
)
foreach ($table in $expectedTables) {
    Require-True (Test-OrdinalContains $migration ('CREATE TABLE ' + $table + ' (')) `
        "A migration não cria a tabela tipada $table."
}
foreach ($token in @(
        'Latin1_General_100_BIN2', 'valid_to_exclusive', 'source_fingerprint',
        'source_row_count', 'author_role', 'approver_role',
        'Aprovador deve ser distinto do autor e do importador.',
        'schema_version = 1',
        'source_row_count BETWEEN 0 AND 100000',
        'imported_byte_count BETWEEN 1 AND 16777216',
        'reference_release_id, activation_scope',
        'sys.sp_getapplock', "@LockOwner = 'Transaction'", 'UPDLOCK, HOLDLOCK',
        'CREATE VIEW ref.v_status_coleta_seed_candidate_v1',
        'CREATE PROCEDURE ref.usp_register_reference_release',
        'CREATE FUNCTION ref.ufn_normalize_pick_status_v1',
        'LTRIM(RTRIM(@raw)) COLLATE Latin1_General_100_BIN2',
        'CREATE FUNCTION ref.ufn_calendar_seed_candidate_v1',
        'DECLARE @active_span_days INT = DATEDIFF(DAY, @window_start, @window_end_exclusive)',
        'OR @active_span_days > 3660', 'DATEADD(DAY, -31, @window_start)',
        'BLACK_CONSCIOUSNESS_DAY', 'billing_reference_date',
        'CREATE TABLE ref.reference_import_receipt',
        'branch_reference_release_id', 'classificacao_frota_matriz',
        'coverage_state = N''UNAVAILABLE'' AND minimum_amount IS NULL',
        'Nenhum GRANT é publicado por V008'
    )) {
    Require-True (Test-OrdinalContains $migration $token) `
        "A migration não contém o fechamento $token."
}
Require-True (-not (Test-OrdinalContains $migration 'CREATE TABLE pub.')) `
    'V2-035a não pode iniciar V2-035b/pub.'
Require-True (-not (Test-OrdinalContains $migration 'GRANT SELECT ON SCHEMA::ref')) `
    'V008 não pode conceder leitura ampla em ref.'
Require-True (-not (Test-OrdinalContains $migration 'MERGE ')) `
    'Referências não podem reintroduzir MERGE mutável do legado.'

Require-True (Test-OrdinalContains $baseline 'V008__create_governed_references.sql') `
    'O baseline não inclui V008.'
foreach ($token in @(
        'Referências governadas V2 validadas com sucesso.',
        'IX_ref_regiao_logistica_cep_lookup',
        'IX_ref_classificacao_frota_matriz_lookup',
        'IX_ref_atribuicao_filial_branch_dependency',
        'reference_import_receipt',
        'Recibo diverge do envelope, conteúdo físico ou cronologia.',
        'physical_content.actual_rows',
        'v_status_coleta_seed_candidate_v1',
        'ufn_calendar_seed_candidate_v1',
        'Principal não autorizado possui acesso direto positivo em ref.'
    )) {
    Require-True (Test-OrdinalContains $validator $token) `
        "O validator não contém $token."
}
foreach ($token in @(
        'V008 criou conteúdo ou ativação por default.',
        '20360229', 'GOOD_FRIDAY', 'BLACK_CONSCIOUSNESS_DAY',
        'REPLAY', 'synthetic-empty-v1', 'Normalização explícita de status',
        'Versões distintas de chave canônica ou token não coexistiram.',
        'Políticas independentes e matriz sintética 2x4 divergiram.',
        'Revogação ordenada atribuição→filial não foi preservada.',
        'Runtime obteve SELECT direto em ref.', 'ROLLBACK TRANSACTION'
    )) {
    Require-True (Test-OrdinalContains $exercise $token) `
        "O exercício rollback-only não cobre $token."
}
foreach ($token in @(
        'Sobreposição tarifária não foi rejeitada.',
        'Faixa CEP inválida não foi rejeitada.',
        'Autor conseguiu aprovar a própria release.',
        'Importador conseguiu aprovar o próprio recibo.',
        'Cardinalidade física divergente não foi rejeitada.',
        'Release ratificada aceitou conteúdo tardio.',
        'Release governada aceitou UPDATE.',
        'Vigências ratificadas sobrepostas não foram rejeitadas.',
        'Replay divergente não foi rejeitado.',
        'Calendário esparso foi ratificado.',
        'Referência útil não exata foi ratificada.',
        'Atribuição ativou sem release de filiais no mesmo escopo.',
        'Schema de referência desconhecido foi aceito.',
        'Alias na mesma normalization_version aceitou overlap.',
        'Token no mesmo token_scheme_version aceitou overlap.',
        'Lookback esparso ou anterior ao último dia útil foi ratificado.',
        'Dia útil sem feriado foi classificado como não útil.',
        'Matriz de frota aceitou classe do eixo errado.',
        'Filial ativa foi revogada antes de sua atribuição dependente.',
        'ROLLBACK TRANSACTION'
    )) {
    Require-True (Test-OrdinalContains $negativeExercise $token) `
        "O exercício negativo isolado não cobre $token."
}
foreach ($token in @(
        "EXECUTE AS USER = N'v2_migrator_references_probe'",
        'HAS_PERMS_BY_NAME', 'V008__create_governed_references.sql',
        'ROLLBACK TRANSACTION'
    )) {
    Require-True (Test-OrdinalContains $migratorExercise $token) `
        "A prova do migrator não cobre $token."
}
foreach ($token in @(
        'trg_reference_ratification_guard', 'DATALENGTH(@family)',
        'DATALENGTH(@scope)', 'DATALENGTH(@activation_scope)',
        'Start-Process', '-WindowStyle Hidden', 'REFERENCES_CONTENTION_CONFIRMED',
        'REFERENCES_OTHER_SCOPE_ISOLATED',
        'REFERENCES_LOCK_REACQUIRED_AFTER_ROLLBACK',
        'CONTENT_SEAL_CONTENTION_CONFIRMED',
        'DEPENDENCY_REVOKE_BLOCKED_BY_RATIFICATION',
        'DEPENDENCY_RATIFY_BLOCKED_BY_REVOCATION',
        'REFERENCES_EPHEMERAL_DATABASE_DROPPED', 'ROLLBACK TRANSACTION'
    )) {
    Require-True (Test-OrdinalContains $concurrencyGate $token) `
        "O gate concorrente de referências não cobre $token."
}
foreach ($token in @(
        'SET SHOWPLAN_XML ON', 'IX_ref_reference_release_scope_validity',
        'IX_ref_calendario_business_date', 'IX_ref_status_coleta_lookup',
        'IX_ref_classificacao_frota_alias_lookup',
        'IX_ref_classificacao_frota_matriz_lookup',
        'IX_ref_classificacao_frota_excecao_lookup',
        'IX_ref_regiao_logistica_cep_lookup', 'IX_ref_tarifa_rota_uf_lookup',
        'GOVERNED_REFERENCES_SHOWPLAN_ROLLED_BACK', 'ROLLBACK TRANSACTION'
    )) {
    Require-True (Test-OrdinalContains $showplanExercise $token) `
        "O exercício SHOWPLAN de referências não cobre $token."
}
foreach ($token in @(
        '034_validate_governed_references_showplan.sql', 'SET SHOWPLAN_XML ON',
        'IX_ref_reference_release_scope_validity', 'IX_ref_calendario_business_date',
        'IX_ref_status_coleta_lookup', 'IX_ref_regiao_destino_alias_lookup',
        'IX_ref_filial_operacional_documento_lookup',
        'IX_ref_frota_propria_documento_lookup', 'IX_ref_atribuicao_filial_lookup',
        'IX_ref_atribuicao_filial_branch_dependency',
        'IX_ref_classificacao_frota_alias_lookup',
        'IX_ref_classificacao_frota_matriz_lookup',
        'IX_ref_classificacao_frota_excecao_lookup',
        'IX_ref_pagador_exclusao_cubagem_lookup', 'IX_ref_regiao_logistica_cep_lookup',
        'IX_ref_regiao_logistica_cidade_uf_lookup', 'IX_ref_tarifa_rota_uf_lookup',
        'operationalWarnings=0', '$conversionWarnings -ne 0',
        'Integrated Security=true', 'maximumPlanCharacters'
    )) {
    Require-True (Test-OrdinalContains $showplanGate $token) `
        "O gate SHOWPLAN de referências não cobre $token."
}

foreach ($documentation in @($adr, $runbook)) {
    foreach ($token in @(
            'EXACT_BIN2', 'token_scheme_version', 'normalization_version',
            'DRIVER_OWNERSHIP', 'VEHICLE_DRIVER_CONTRACT', 'lookback',
            'U+0020', 'THIRD_PARTY', 'UNSPECIFIED', 'atribuição', 'revoga'
        )) {
        Require-True (Test-OrdinalContains $documentation $token) `
            "A documentação de referências não contém o fechamento $token."
    }
}
Require-True (Test-OrdinalContains $runbook 'ETL_V2_REF_PROBE_<guid>') `
    'O runbook não declara o banco efêmero allowlisted do probe concorrente.'

$fingerprint = (Get-FileHash -LiteralPath $manifestPath -Algorithm SHA256).Hash.ToLowerInvariant()
$recordedFingerprint = ((Get-Content -LiteralPath $fingerprintPath -Raw).Trim() -split '\s+')[0].ToLowerInvariant()
Require-True ($fingerprint -eq $recordedFingerprint) `
    'O fingerprint do manifesto de referências está desatualizado.'

Write-Output 'Manifesto, migration, limites, fixtures e gates de referências governadas validados.'
