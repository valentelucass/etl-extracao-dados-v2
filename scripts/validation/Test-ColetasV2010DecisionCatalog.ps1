#Requires -Version 7.0

[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$utf8 = [System.Text.UTF8Encoding]::new($false, $true)
$repositoryRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..'))
$catalogPath = Join-Path $repositoryRoot 'docs\catalogos\coletas-v2-010\decisao-v01.json'

function Read-StrictUtf8 {
    param([Parameter(Mandatory)][string]$LiteralPath)

    $item = Get-Item -LiteralPath $LiteralPath -ErrorAction Stop
    if ($item.Length -gt 128KB) {
        throw 'O catálogo de decisão de Coletas excede o limite de bytes.'
    }
    return $utf8.GetString([System.IO.File]::ReadAllBytes($item.FullName))
}

function Assert-ExactSet {
    param(
        [Parameter(Mandatory)][object[]]$Actual,
        [Parameter(Mandatory)][string[]]$Expected,
        [Parameter(Mandatory)][string]$Label
    )

    $actualStrings = @($Actual | ForEach-Object { [string]$_ } | Sort-Object -Unique)
    $expectedStrings = @($Expected | Sort-Object -Unique)
    if ($actualStrings.Count -ne $expectedStrings.Count -or
        (Compare-Object -ReferenceObject $expectedStrings -DifferenceObject $actualStrings)) {
        throw "O conjunto $Label diverge da decisão V01."
    }
}

$catalogText = Read-StrictUtf8 -LiteralPath $catalogPath
try {
    $catalog = $catalogText | ConvertFrom-Json -Depth 32
} catch {
    throw 'O catálogo de decisão de Coletas não é JSON válido.'
}

if ([string]$catalog.catalogVersion -cne '2026-09-04.v2-010-v01.1' -or
    [string]$catalog.decision -cne 'COLETAS_6908_DOMAIN_PRESENCE_FRESHNESS_STATUS_SCHEMA' -or
    [string]$catalog.scope -cne 'DECISION_ONLY_NO_DDL_NO_SOURCE_IO_NO_PUBLICATION' -or
    [string]$catalog.sourceContract -cne 'dataexport-6908' -or
    [string]$catalog.executionOwner -cne 'V02') {
    throw 'A identidade, o escopo ou o dono de execução divergem da decisão V01.'
}

Assert-ExactSet -Actual @($catalog.identity.tuple) -Expected @(
    'source_instance', 'tenant_scope', 'entity', 'source_key'
) -Label 'da tuple de identidade'
if ([string]$catalog.identity.entity -cne 'coletas' -or
    [string]$catalog.identity.sourceKey.path -cne '/id' -or
    [string]$catalog.identity.sourceKey.wireType -cne 'INTEGER' -or
    [string]$catalog.identity.sourceKey.role -cne 'TYPE_TAGGED_SOURCE_KEY' -or
    [string]$catalog.identity.businessAlias.path -cne '/sequence_code' -or
    [string]$catalog.identity.businessAlias.role -cne 'VERSIONED_NON_TECHNICAL_ALIAS' -or
    [string]$catalog.identity.expandedRows -cne 'DEDUPLICATE_LOGICAL_ROOT_SET_BASED_NO_IMPLICIT_CHILD') {
    throw 'A decisão de identidade/grão de Coletas diverge do ADR 0018.'
}

Assert-ExactSet -Actual @($catalog.attributePresenceVocabulary) -Expected @('ABSENT', 'NULL', 'VALUE') -Label 'de presença tri-state'
Assert-ExactSet -Actual @($catalog.logicalSchema) -Expected @(
    'SCOPED_IDENTITY_AND_CANONICAL_REGISTRY',
    'VERSIONED_BUSINESS_ALIAS',
    'RAW_TYPED_AND_TRI_STATE_ATTRIBUTE_PRESENCE',
    'RAW_CANONICAL_VERSIONED_STATUS_AND_TERMINALITY',
    'RAW_TYPED_ORIGINATED_FRESHNESS',
    'SNAPSHOT_PRESENCE_AND_ABSENCE_EVIDENCE',
    'RELATION_CANDIDATE_VALUE_PRESENCE_PROVENANCE'
) -Label 'do schema lógico'

if ([string]$catalog.freshness.typedPreferred -cne 'status_updated_at_em' -or
    [string]$catalog.freshness.rawInput -cne 'status_updated_at' -or
    [string]$catalog.freshness.invalidRaw -cne 'PRESERVE_WITHOUT_REINTERPRETATION' -or
    -not [bool]$catalog.freshness.terminalWinsOverOpenDespiteRetrogradeTimestamp -or
    -not [bool]$catalog.freshness.persistedTerminalNeverRegressesToOpen) {
    throw 'A política de frescor/terminalidade diverge da decisão V01.'
}
if ((Compare-Object -ReferenceObject @('finish_date', 'service_date', 'request_date') -DifferenceObject @($catalog.freshness.fallbackOrder))) {
    throw 'A ordem de fallback de frescor diverge da decisão V01.'
}

$statuses = @($catalog.statusCatalog.entries)
Assert-ExactSet -Actual @($statuses | ForEach-Object { [string]$_.source }) -Expected @(
    'pending', 'treatment', 'manifested', 'in_transit', 'draft', 'finished', 'done', 'canceled', 'cancelled'
) -Label 'do catálogo de status'
$terminalStatuses = @($statuses | Where-Object terminal | ForEach-Object { [string]$_.source })
Assert-ExactSet -Actual $terminalStatuses -Expected @('finished', 'done', 'canceled', 'cancelled') -Label 'de status terminais'
$labels = @{}
foreach ($entry in $statuses) {
    $labels[[string]$entry.source] = [string]$entry.label
}
if ([string]$catalog.statusCatalog.version -cne 'coletas-status-v1' -or
    $labels['done'] -cne 'Coletada' -or $labels['finished'] -cne 'Finalizada' -or
    $labels['canceled'] -cne 'Cancelada' -or $labels['cancelled'] -cne 'Cancelada' -or
    (Compare-Object -ReferenceObject @(
        'FINISHED_OR_DONE_TO_COLETA_REALIZADA', 'NON_BLANK_CANCELLATION_REASON',
        'CANCELED_OR_CANCELLED_TO_COLETA_CANCELADA', 'PENDENTE'
    ) -DifferenceObject @($catalog.statusCatalog.actionPrecedence)) -or
    $catalog.statusCatalog.attempts.terminal -ne 1 -or $catalog.statusCatalog.attempts.open -ne 0 -or
    [string]$catalog.statusCatalog.unknownCode -cne 'PRESERVE_RAW_NO_INFERRED_TERMINALITY') {
    throw 'O catálogo de status ou derivados não preserva a decisão V01.'
}

if ([string]$catalog.rootPresence.activation -cne 'BLOCKED_UNTIL_V2_013_AND_INDEPENDENT_COMPLETE_SNAPSHOT' -or
    [string]$catalog.rootPresence.firstIndependentAbsence -cne 'CANDIDATE_COMPATIBILITY_LABEL_EXCLUIDA' -or
    [string]$catalog.rootPresence.secondIndependentAbsence -cne 'CONFIRMED_SOFT_DELETE' -or
    [string]$catalog.rootPresence.reappearance -cne 'CLEAR_ALL_ABSENCE_STATE' -or
    [string]$catalog.rootPresence.incompleteOrUncertainSnapshot -cne 'NO_ACTIVE_STATE_CHANGE') {
    throw 'A política de presença/ausência não está bloqueada de forma segura.'
}

Assert-ExactSet -Actual @($catalog.relationships.candidateFields) -Expected @(
    'manifesto', 'pick_item_id', 'fit_p_m_pck_sequence_code', 'frete'
) -Label 'de candidatos relacionais'
if ([string]$catalog.relationships.storage -cne 'PRESENCE_AND_PROVENANCE_ONLY' -or
    [string]$catalog.relationships.manifestoToColeta -cne 'BLOCKED_UNTIL_V2_046A' -or
    [string]$catalog.relationships.coletaToFrete -cne 'BLOCKED_UNTIL_V2_046B' -or
    [string]$catalog.relationships.inferredJoin -cne 'FORBIDDEN') {
    throw 'A decisão relacional antecipou uma prova que pertence a V2-046.'
}

if ([string]$catalog.incremental.partition -cne 'request_date' -or
    [string]$catalog.incremental.overlap -cne 'VERSIONED_REQUIRED' -or
    [string]$catalog.incremental.supplementaryLateData -cne 'scopes.by_updated_at' -or
    [string]$catalog.incremental.watermark -cne 'FORBIDDEN_FOR_scopes.by_updated_at' -or
    [string]$catalog.references.productiveReference -cne 'PUBLICATION_AND_CUTOVER_BLOCKED_UNTIL_V2_035A' -or
    [string]$catalog.guardrails -cne 'HISTORICAL_CANDIDATES_NO_V2_DEFAULT') {
    throw 'A decisão incremental, de referências ou guardrails diverge da V01.'
}

if ($catalogText -match '(?i)authorization\s*:\s*bearer|password\s*[=:]|real[-_ ]?(?:id|cursor|document)') {
    throw 'O catálogo de decisão contém dado incompatível com evidência sanitizada.'
}

Write-Output 'PASS: decisão V01 de Coletas 6908 validada; V02 recebe domínio, presença, frescor, status e schema lógico sem DDL, I/O ou relação inferida.'
