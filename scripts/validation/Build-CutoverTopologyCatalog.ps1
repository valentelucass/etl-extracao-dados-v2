#requires -Version 7.0

[CmdletBinding()]
param(
    [Parameter()]
    [string]$OutputRoot = (Join-Path (Split-Path (Split-Path $PSScriptRoot -Parent) -Parent) 'docs\catalogos\cutover')
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$v2Root = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$portabilityRoot = Join-Path $v2Root 'docs\catalogos\portabilidade'
$inventoryPath = Join-Path $portabilityRoot 'inventario-artefatos.csv'
$portabilityManifestPath = Join-Path $portabilityRoot 'manifesto.json'
$schemaManifestPath = Join-Path $v2Root 'database\manifest\schema-foundation.json'
$usuariosDimensionManifestPath = Join-Path $v2Root `
    'database\manifest\usuarios-dimension-current.json'
$outputRootFull = [System.IO.Path]::GetFullPath($OutputRoot)
$canonicalOutputRoot = [System.IO.Path]::GetFullPath((Join-Path $v2Root 'docs\catalogos\cutover'))
$targetRoot = [System.IO.Path]::GetFullPath((Join-Path $v2Root 'target'))
$pathComparison = if ($IsWindows) {
    [System.StringComparison]::OrdinalIgnoreCase
} else {
    [System.StringComparison]::Ordinal
}

$isCanonicalOutput = $outputRootFull.Equals($canonicalOutputRoot, $pathComparison)
$outputParent = [System.IO.Path]::GetDirectoryName($outputRootFull)
$isVerifierOutput = $outputParent.Equals($targetRoot, $pathComparison) -and
    [System.IO.Path]::GetFileName($outputRootFull) -cmatch '^cutover-topology-verify-[0-9a-f]{32}$'
if (-not ($isCanonicalOutput -or $isVerifierOutput)) {
    throw 'OutputRoot deve ser o catálogo canônico ou um diretório de verificação direto em target.'
}

$existingBoundary = if (Test-Path -LiteralPath $outputRootFull) {
    Get-Item -LiteralPath $outputRootFull -Force
} elseif (Test-Path -LiteralPath $outputParent) {
    Get-Item -LiteralPath $outputParent -Force
}
if ($null -ne $existingBoundary -and
    ($existingBoundary.Attributes -band [System.IO.FileAttributes]::ReparsePoint)) {
    throw 'OutputRoot não pode atravessar junction ou symbolic link no boundary de escrita.'
}

if (-not (Test-Path -LiteralPath $inventoryPath -PathType Leaf) -or
    -not (Test-Path -LiteralPath $portabilityManifestPath -PathType Leaf) -or
    -not (Test-Path -LiteralPath $schemaManifestPath -PathType Leaf) -or
    -not (Test-Path -LiteralPath $usuariosDimensionManifestPath -PathType Leaf)) {
    throw 'Os baselines V2-017/V2-019/V2-035b são obrigatórios para gerar o catálogo de cutover.'
}

[System.IO.Directory]::CreateDirectory($outputRootFull) | Out-Null
$utf8NoBom = [System.Text.UTF8Encoding]::new($false)

function Write-DeterministicText {
    param(
        [Parameter(Mandatory)][string]$Path,
        [Parameter(Mandatory)][string]$Content
    )

    $normalized = $Content.Replace("`r`n", "`n").Replace("`r", "`n")
    if (-not $normalized.EndsWith("`n", [System.StringComparison]::Ordinal)) {
        $normalized += "`n"
    }
    [System.IO.File]::WriteAllText($Path, $normalized, $utf8NoBom)
}

function Write-DeterministicCsv {
    param(
        [Parameter(Mandatory)][string]$Path,
        [Parameter(Mandatory)][object[]]$Rows
    )

    if ($Rows.Count -eq 0) {
        throw "Nenhuma linha gerada para $Path."
    }
    Write-DeterministicText -Path $Path -Content (($Rows | ConvertTo-Csv -NoTypeInformation) -join "`n")
}

function New-DagNode {
    param(
        [Parameter(Mandatory)][string]$NodeId,
        [Parameter(Mandatory)][string]$NodeKind,
        [Parameter(Mandatory)][string]$V2Zone,
        [Parameter(Mandatory)][string]$V2Target,
        [Parameter()][string[]]$DependsOn = @(),
        [Parameter(Mandatory)][string]$OwnerTask,
        [Parameter()][string[]]$LegacyArtifacts = @(),
        [Parameter(Mandatory)][string]$RouteBoundary,
        [Parameter(Mandatory)][string]$WriteBoundary,
        [Parameter(Mandatory)][string]$Readiness
    )

    [pscustomobject][ordered]@{
        node_id = $NodeId
        node_kind = $NodeKind
        v2_zone = $V2Zone
        v2_target = $V2Target
        depends_on = ($DependsOn -join ';')
        owner_task = $OwnerTask
        legacy_artifacts = ($LegacyArtifacts -join ';')
        cutover_unit = 'CUTOVER-DB-01'
        route_boundary = $RouteBoundary
        write_boundary = $WriteBoundary
        consumer_boundary = if ($NodeKind -eq 'PUB_CONTRACT' -and
            $RouteBoundary -eq 'INTERNAL_NOT_PUBLIC_BY_DEFAULT') {
            'INTERNAL_SCOPE_PENDING_V2_037'
        } elseif ($NodeKind -eq 'PUB_CONTRACT') {
            'EXTERNAL_CONSUMER_MANIFEST_PENDING'
        } else {
            'NOT_A_CONSUMER_CONTRACT'
        }
        readiness = $Readiness
    }
}

function New-Fence {
    param(
        [Parameter(Mandatory)][int]$Sequence,
        [Parameter(Mandatory)][string]$FenceId,
        [Parameter(Mandatory)][string]$Boundary,
        [Parameter(Mandatory)][string]$RequiredState,
        [Parameter(Mandatory)][string]$SanitizedEvidence,
        [Parameter(Mandatory)][string]$OwnerRole,
        [Parameter(Mandatory)][string]$DueGate,
        [Parameter(Mandatory)][string]$RollbackRule,
        [Parameter(Mandatory)][string]$Status
    )

    [pscustomobject][ordered]@{
        sequence = $Sequence
        fence_id = $FenceId
        boundary = $Boundary
        required_state = $RequiredState
        sanitized_evidence = $SanitizedEvidence
        owner_role = $OwnerRole
        due_gate = $DueGate
        rollback_rule = $RollbackRule
        status = $Status
    }
}

$inventory = @(Import-Csv -LiteralPath $inventoryPath -Encoding utf8)
$portabilityManifest = Get-Content -LiteralPath $portabilityManifestPath -Raw -Encoding utf8 | ConvertFrom-Json
$schemaManifest = Get-Content -LiteralPath $schemaManifestPath -Raw -Encoding utf8 | ConvertFrom-Json
$schemaManifestSha256 = (Get-FileHash -LiteralPath $schemaManifestPath -Algorithm SHA256).Hash.ToLowerInvariant()
$usuariosDimensionManifest = Get-Content -LiteralPath $usuariosDimensionManifestPath `
    -Raw -Encoding utf8 | ConvertFrom-Json
$usuariosDimensionManifestSha256 = (Get-FileHash -LiteralPath `
        $usuariosDimensionManifestPath -Algorithm SHA256).Hash.ToLowerInvariant()
$coveredArtifactTypes = @(
    'database_runner',
    'command_default',
    'command_flag',
    'entity_source',
    'procedure',
    'sql_auxiliary',
    'table_script',
    'view_etl',
    'view_wrapper',
    'windows_alias',
    'windows_script'
)

$responsibilities = foreach ($artifact in ($inventory |
        Where-Object { $_.artifact_type -in $coveredArtifactTypes } |
        Sort-Object artifact_type, artifact_id, source_path)) {
    $statusIsAllowed = switch ($artifact.artifact_type) {
        'view_etl' { $artifact.status -eq 'CONSUMER_MANIFEST_PENDING' }
        'entity_source' {
            $artifact.status -eq 'CLASSIFIED' -or
                ($artifact.artifact_id -eq 'usuarios' -and $artifact.status -eq 'IMPLEMENTED_IN_SHADOW') -or
                ($artifact.artifact_id -eq 'raster' -and $artifact.status -eq 'CONDITIONAL_DISABLED')
        }
        'table_script' {
            $artifact.status -eq 'CLASSIFIED' -or
                ($artifact.artifact_id -match '^0(11|16|17)_' -and $artifact.status -eq 'IMPLEMENTED_IN_SHADOW') -or
                ($artifact.artifact_id -match '^0(08|35)_' -and $artifact.status -eq 'IMPLEMENTED_OFFLINE_CANDIDATE') -or
                ($artifact.artifact_id -match '^0(26|27|33|34)_' -and $artifact.status -eq 'OFFLINE_SCHEMA_READY_EXTERNAL_INPUT_REQUIRED')
        }
        default { $artifact.status -eq 'CLASSIFIED' }
    }
    if (-not $statusIsAllowed) {
        throw "Status de portabilidade não aceito para cutover: $($artifact.artifact_type)/$($artifact.artifact_id)=$($artifact.status)."
    }

    $logicalResponsibility = if ($artifact.artifact_type -eq 'table_script') {
        if ($artifact.artifact_id -eq '016_alter_tabela_dim_usuarios_estado') {
            'TABLE:dim_usuarios'
        } elseif ($artifact.artifact_id -cmatch '^\d+_criar_tabela_(.+)$') {
            "TABLE:$($Matches[1])"
        } else {
            throw "Script de tabela sem responsabilidade lógica explícita: $($artifact.artifact_id)."
        }
    } else {
        "ARTIFACT:$($artifact.artifact_type):$($artifact.artifact_id)"
    }

    $isInvocationSurface = $artifact.artifact_type -in @(
        'command_default','command_flag','windows_alias','windows_script'
    )
    $legacyExecutionEffect = if (-not $isInvocationSurface) {
        'NOT_AN_INVOCATION_SURFACE'
    } elseif ($artifact.artifact_type -eq 'command_flag' -and
        $artifact.artifact_id -in @('--ajuda','--help')) {
        'READ_ONLY_PROVEN_LOCAL'
    } elseif (($artifact.artifact_type -eq 'command_flag' -and
            ($artifact.artifact_id -in @('--auditar-api','--auth-check','--auth-info','--exportar-csv','--introspeccao','--testar-api') -or
                $artifact.artifact_id -clike '--validar*' -or
                $artifact.artifact_id -clike '--verificar*')) -or
        ($artifact.artifact_type -eq 'windows_script' -and
            $artifact.artifact_id -in @(
                '02-testar_api_especifica.bat','03-validar_config.bat',
                '06-relatorio-completo-validacao.bat','07-exportar_csv.bat',
                '08-auditar_api.bat','verificar_execucao_ativa.ps1'
            ))) {
        'NO_DOMAIN_WRITE_CLAIM_PENDING_EXTERNAL_INVENTORY'
    } else {
        'WRITE_OR_CONTROL_CAPABLE'
    }

    $cutoverInclusion = switch ($artifact.decision) {
        'RETIRE' { 'EXCLUDED_RETIRED' }
        'CONDITIONAL' { 'CONDITIONAL_DATABASE_WIDE' }
        'PRESERVE' { 'INCLUDED_DATABASE_WIDE' }
        'SUBSTITUTE' { 'INCLUDED_DATABASE_WIDE' }
        'CONSOLIDATE' { 'INCLUDED_DATABASE_WIDE' }
        default { throw "Decisão de portabilidade desconhecida: $($artifact.decision)." }
    }
    if ($artifact.artifact_type -eq 'view_wrapper') {
        $cutoverInclusion = 'FORBIDDEN_CROSS_DATABASE'
    }

    $legacyWriteFence = switch ($artifact.artifact_type) {
        'view_etl' { 'READ_ONLY_NO_WRITE_FENCE' }
        'view_wrapper' { 'FORBIDDEN' }
        { $_ -in @('command_default','command_flag','windows_alias','windows_script') } {
            switch ($legacyExecutionEffect) {
                'READ_ONLY_PROVEN_LOCAL' { 'NO_WRITE_FENCE_READ_ONLY_LOCAL' }
                'NO_DOMAIN_WRITE_CLAIM_PENDING_EXTERNAL_INVENTORY' {
                    'FREEZE_UNLESS_NO_WRITE_IS_POSITIVELY_PROVEN'
                }
                default { 'PROCESS_FREEZE_PLUS_DATABASE_PRINCIPAL_REVOKE' }
            }
        }
        default { 'DATABASE_PRINCIPAL_REVOKE_AUTHORITATIVE' }
    }

    $isInternalMonitoringContract = $artifact.artifact_type -eq 'view_etl' -and
        $artifact.artifact_id -eq '019_criar_view_bi_monitoramento'

    $v2WriteBoundary = switch ($artifact.artifact_type) {
        'view_etl' {
            if ($isInternalMonitoringContract) {
                'READ_ONLY_INTERNAL_GRANT_PENDING_V2_037'
            } else {
                'READ_ONLY_PUB_GRANT_PENDING_V2_037'
            }
        }
        'view_wrapper' { 'FORBIDDEN' }
        'database_runner' { 'FLYWAY_MIGRATOR_ONLY' }
        'windows_script' { 'EXTERNAL_SCHEDULER_WITHOUT_DATABASE_PRIVILEGE' }
        'windows_alias' { 'RETIRED_OR_THIN_LAUNCHER_PENDING_V2_022' }
        'command_default' { 'TYPED_ONE_SHOT_COMMAND_OR_RETIRED_PENDING_V2_022' }
        'command_flag' { 'TYPED_ONE_SHOT_COMMAND_OR_RETIRED_PENDING_V2_022' }
        'sql_auxiliary' { 'GOVERNED_ENTRYPOINT_OR_RETIRED' }
        default { 'ALLOWLISTED_PROCEDURES_WITHOUT_DIRECT_DML' }
    }

    $routeBoundary = switch ($artifact.artifact_type) {
        'view_etl' {
            if ($isInternalMonitoringContract) {
                'INTERNAL_NOT_PUBLIC_BY_DEFAULT'
            } else {
                'DATABASE_ENDPOINT_OR_ALIAS_UNPROVEN'
            }
        }
        'view_wrapper' { 'FORBIDDEN' }
        default { 'INTERNAL_OR_NOT_A_CONSUMER_ROUTE' }
    }

    if ($artifact.decision -eq 'RETIRE' -and $artifact.artifact_type -ne 'view_wrapper') {
        $v2WriteBoundary = 'NOT_APPLICABLE_RETIRED'
        $routeBoundary = 'NOT_APPLICABLE_RETIRED'
    }

    [pscustomobject][ordered]@{
        artifact_key = "$($artifact.artifact_type):$($artifact.artifact_id):$($artifact.source_path)"
        artifact_type = $artifact.artifact_type
        artifact_id = $artifact.artifact_id
        source_path = $artifact.source_path
        responsibility = $artifact.responsibility
        logical_responsibility = $logicalResponsibility
        legacy_execution_effect = $legacyExecutionEffect
        decision = $artifact.decision
        v2_destination = $artifact.v2_destination
        owner_role = $artifact.owner_role
        due_gate = $artifact.due_gate
        cutover_unit = 'CUTOVER-DB-01'
        cutover_inclusion = $cutoverInclusion
        legacy_write_fence = $legacyWriteFence
        v2_write_boundary = $v2WriteBoundary
        route_boundary = $routeBoundary
        evidence_status = if ($cutoverInclusion -in @('EXCLUDED_RETIRED', 'FORBIDDEN_CROSS_DATABASE')) {
            'LOCAL_EXCLUSION_MAPPED'
        } else {
            'LOCAL_MAPPING_EXTERNAL_RATIFICATION_REQUIRED'
        }
    }
}

$internalRoute = 'INTERNAL_SAME_DATABASE'
$sourceWrite = 'DATABASE_WIDE_FENCE_AND_ALLOWLISTED_PROCEDURES'
$pubRoute = 'DATABASE_ENDPOINT_OR_ALIAS_UNPROVEN'
$pubWrite = 'READ_ONLY_PUB_GRANT_PENDING_V2_037'
$pending = 'PENDING_IMPLEMENTATION_OR_EXTERNAL_GATE'
$invocationArtifactKeys = @($inventory |
        Where-Object { $_.artifact_type -in @('command_default','command_flag','windows_alias','windows_script') } |
        Sort-Object artifact_type, artifact_id |
        ForEach-Object { "$($_.artifact_type):$($_.artifact_id)" })

$dag = @(
    New-DagNode 'PLAT_SCHEMA' 'PLATFORM' 'ctl/stg/core/ref/mart/pub/recon' 'schema foundation' @() 'V2-019' @() $internalRoute 'MIGRATOR_ONLY_RUNTIME_NO_DDL' 'LOCAL_FOUNDATION_PRESENT'
    New-DagNode 'PLAT_CONTROL' 'PLATFORM' 'ctl' 'control plane and publication ledger' @('PLAT_SCHEMA') 'V2-020' @() $internalRoute 'ALLOWLISTED_PROCEDURES_NO_DIRECT_DML' 'LOCAL_FOUNDATION_PRESENT'
    New-DagNode 'PLAT_STAGING' 'PLATFORM' 'stg/core/recon' 'staging and promotion kernel' @('PLAT_CONTROL') 'V2-021' @() $internalRoute 'ALLOWLISTED_PROCEDURES_NO_DIRECT_DML' 'LOCAL_FOUNDATION_PRESENT'
    New-DagNode 'PLAT_GATES' 'PLATFORM' 'ctl/recon' 'contract and data quality gates' @('PLAT_STAGING') 'V2-023/V2-044' @() $internalRoute 'PUBLICATION_FAIL_CLOSED' 'LOCAL_FOUNDATION_PRESENT'
    New-DagNode 'PLAT_RUNTIME' 'PLATFORM' 'external scheduler + ctl' 'one-shot runtime and authorization boundary' @('PLAT_GATES') 'V2-022/V2-042/V2-043' $invocationArtifactKeys 'EXTERNAL_SCHEDULER_UNPROVEN' 'DENY_ALL_UNTIL_EXTERNAL_IDENTITY' $pending

    New-DagNode 'REF_GOVERNADAS' 'REFERENCE_SET' 'ref' 'typed governed reference foundation; no productive baseline' @('PLAT_GATES') 'V2-035a' @() $internalRoute 'OWNER_ONLY_IMPORT_RUNTIME_NO_DIRECT_ACCESS' 'LOCAL_FOUNDATION_PRESENT_EXTERNAL_BASELINE_BLOCKED'

    New-DagNode 'SRC_USUARIOS' 'SOURCE' 'core' 'usuario current + usuario_history in shadow' @('PLAT_RUNTIME') 'V2-024/V2-033' @('entity_source:usuarios') $internalRoute $sourceWrite 'LOCAL_SHADOW_IMPLEMENTED_PUBLICATION_BLOCKED'
    New-DagNode 'SRC_COLETAS' 'SOURCE' 'core' 'coletas' @('PLAT_RUNTIME','SRC_USUARIOS','REF_GOVERNADAS') 'V2-010' @('entity_source:coletas') $internalRoute $sourceWrite $pending
    New-DagNode 'SRC_MANIFESTOS' 'SOURCE' 'core' 'manifestos + typed children' @('PLAT_RUNTIME') 'V2-026' @('entity_source:manifestos') $internalRoute $sourceWrite $pending
    New-DagNode 'REL_MANIFESTO_COLETA' 'RELATION' 'core' 'manifesto-to-coleta crosswalk' @('SRC_MANIFESTOS','SRC_COLETAS') 'V2-046a' @() $internalRoute $sourceWrite $pending
    New-DagNode 'SRC_FRETES' 'SOURCE' 'core' 'fretes + typed sidecars' @('PLAT_RUNTIME','SRC_COLETAS','REL_MANIFESTO_COLETA','REF_GOVERNADAS') 'V2-011' @('entity_source:fretes') $internalRoute $sourceWrite $pending
    New-DagNode 'REL_COLETA_FRETE' 'RELATION' 'core' 'coleta-to-frete crosswalk' @('REL_MANIFESTO_COLETA','SRC_COLETAS','SRC_FRETES') 'V2-046b' @() $internalRoute $sourceWrite $pending
    New-DagNode 'SRC_COTACOES' 'SOURCE' 'core' 'cotacoes' @('PLAT_RUNTIME','REF_GOVERNADAS') 'V2-027' @('entity_source:cotacoes') $internalRoute $sourceWrite $pending
    New-DagNode 'SRC_LOCALIZACAO' 'SOURCE' 'core' 'localizacao de cargas' @('PLAT_RUNTIME','SRC_FRETES','REF_GOVERNADAS') 'V2-028' @('entity_source:localizacao_cargas') $internalRoute $sourceWrite $pending
    New-DagNode 'SRC_CONTAS' 'SOURCE' 'core' 'contas a pagar + parcelas' @('PLAT_RUNTIME','REF_GOVERNADAS') 'V2-029' @('entity_source:contas_a_pagar') $internalRoute $sourceWrite $pending
    New-DagNode 'SRC_FATURAS' 'SOURCE' 'core' 'faturas por cliente + crosswalks' @('PLAT_RUNTIME','SRC_FRETES','REF_GOVERNADAS') 'V2-030' @('entity_source:faturas_por_cliente') $internalRoute $sourceWrite $pending
    New-DagNode 'SRC_INVENTARIO' 'SOURCE' 'core' 'inventario + typed children' @('PLAT_RUNTIME','SRC_FRETES') 'V2-031' @('entity_source:inventario') $internalRoute $sourceWrite $pending
    New-DagNode 'SRC_SINISTROS' 'SOURCE' 'core' 'sinistros + typed relations' @('PLAT_RUNTIME','SRC_FRETES') 'V2-032' @('entity_source:sinistros') $internalRoute $sourceWrite $pending
    New-DagNode 'SRC_RASTER' 'SOURCE' 'core' 'raster viagens + paradas' @('PLAT_RUNTIME') 'V2-034a/V2-034b' @('entity_source:raster') $internalRoute $sourceWrite 'CONDITIONAL_DISABLED'

    New-DagNode 'DIM_FILIAIS' 'DIMENSION' 'pub' 'filiais contract pending V2-037' @('SRC_FRETES','SRC_MANIFESTOS','SRC_CONTAS','SRC_FATURAS','REF_GOVERNADAS') 'V2-035b/V2-037' @() $pubRoute $pubWrite $pending
    New-DagNode 'DIM_CLIENTES' 'DIMENSION' 'pub' 'clientes contract pending V2-037' @('SRC_COLETAS','SRC_FRETES','SRC_FATURAS') 'V2-035b/V2-037' @() $pubRoute $pubWrite $pending
    New-DagNode 'DIM_VEICULOS' 'DIMENSION' 'pub' 'veiculos contract pending V2-037' @('SRC_MANIFESTOS') 'V2-035b/V2-037' @() $pubRoute $pubWrite $pending
    New-DagNode 'DIM_MOTORISTAS' 'DIMENSION' 'pub' 'motoristas contract pending V2-037' @('SRC_MANIFESTOS') 'V2-035b/V2-037' @() $pubRoute $pubWrite $pending
    New-DagNode 'DIM_PLANOCONTAS' 'DIMENSION' 'pub' 'plano de contas contract pending V2-037' @('SRC_CONTAS') 'V2-035b/V2-037' @() $pubRoute $pubWrite $pending
    New-DagNode 'DIM_USUARIOS' 'DIMENSION' 'core' 'core.v_usuario_dimension_current_v1' @('SRC_USUARIOS') 'V2-035b' @() $internalRoute 'READ_ONLY_INTERNAL_NO_CONSUMER_GRANT' 'LOCAL_SHADOW_IMPLEMENTED_CONSUMER_CONTRACT_BLOCKED'

    New-DagNode 'MART_FRETES' 'MART_FACT' 'mart' 'fato de fretes operacionais' @('SRC_FRETES','SRC_LOCALIZACAO','REF_GOVERNADAS') 'V2-036/MAT-01' @('procedure:001_criar_sp_carga_fato_gestao_vista_fretes') $internalRoute $sourceWrite $pending
    New-DagNode 'MART_COLETORES' 'MART_FACT' 'mart' 'fato de coletores' @('SRC_FRETES','SRC_MANIFESTOS','SRC_INVENTARIO','REF_GOVERNADAS') 'V2-036/MAT-02' @('procedure:002_criar_sp_carga_fato_gestao_vista_coletores') $internalRoute $sourceWrite $pending
    New-DagNode 'MART_FATURAMENTO' 'MART_FACT' 'mart' 'fato de faturamento' @('SRC_FRETES','SRC_LOCALIZACAO','SRC_FATURAS','REF_GOVERNADAS') 'V2-036/MAT-03' @('procedure:003_criar_sp_carga_fato_fretes_faturamento') $internalRoute $sourceWrite $pending
    New-DagNode 'MART_FATURAS' 'MART_FACT' 'mart' 'fato de faturas' @('SRC_FATURAS') 'V2-036/MAT-04' @('procedure:004_criar_sp_carga_fato_gestao_vista_faturas') $internalRoute $sourceWrite $pending
    New-DagNode 'MART_MANIFESTOS' 'MART_FACT' 'mart' 'fato de manifestos' @('SRC_MANIFESTOS','SRC_COLETAS','SRC_FRETES','REL_MANIFESTO_COLETA','REL_COLETA_FRETE','REF_GOVERNADAS') 'V2-036/MAT-05' @('procedure:005_criar_sp_carga_fato_gestao_vista_manifestos') $internalRoute $sourceWrite $pending

    New-DagNode 'PUB_FATURAS' 'PUB_CONTRACT' 'pub' 'object name pending V2-037' @('SRC_FATURAS') 'V2-030/V2-037' @('view_etl:011_criar_view_faturas_por_cliente_powerbi') $pubRoute $pubWrite $pending
    New-DagNode 'PUB_FRETES' 'PUB_CONTRACT' 'pub' 'object name pending V2-037' @('SRC_FRETES','SRC_LOCALIZACAO','SRC_INVENTARIO','REF_GOVERNADAS') 'V2-011/V2-028/V2-031/V2-037' @('view_etl:012_criar_view_fretes_powerbi') $pubRoute $pubWrite $pending
    New-DagNode 'PUB_COLETAS' 'PUB_CONTRACT' 'pub' 'object name pending V2-037' @('SRC_COLETAS','SRC_MANIFESTOS','SRC_USUARIOS','REL_MANIFESTO_COLETA','REF_GOVERNADAS') 'V2-010/V2-026/V2-033/V2-035b/V2-037' @('view_etl:013_criar_view_coletas_powerbi') $pubRoute $pubWrite $pending
    New-DagNode 'PUB_COLETAS_EXCLUIDAS' 'PUB_CONTRACT' 'pub' 'object name pending V2-037' @('SRC_COLETAS','PLAT_GATES') 'V2-010/V2-013/V2-037' @('view_etl:014_criar_view_coletas_excluidas_origem') $pubRoute $pubWrite $pending
    New-DagNode 'PUB_COTACOES' 'PUB_CONTRACT' 'pub' 'object name pending V2-037' @('SRC_COTACOES','REF_GOVERNADAS') 'V2-027/V2-035a/V2-037' @('view_etl:015_criar_view_cotacoes_powerbi') $pubRoute $pubWrite $pending
    New-DagNode 'PUB_CONTAS' 'PUB_CONTRACT' 'pub' 'object name pending V2-037' @('SRC_CONTAS','REF_GOVERNADAS') 'V2-029/V2-037' @('view_etl:016_criar_view_contas_a_pagar_powerbi') $pubRoute $pubWrite $pending
    New-DagNode 'PUB_LOCALIZACAO' 'PUB_CONTRACT' 'pub' 'object name pending V2-037' @('SRC_LOCALIZACAO','REF_GOVERNADAS') 'V2-028/V2-035a/V2-037' @('view_etl:017_criar_view_localizacao_cargas_powerbi') $pubRoute $pubWrite $pending
    New-DagNode 'PUB_MANIFESTOS' 'PUB_CONTRACT' 'pub' 'object name pending V2-037' @('SRC_MANIFESTOS') 'V2-026/V2-037' @('view_etl:018_criar_view_manifestos_powerbi') $pubRoute $pubWrite $pending
    New-DagNode 'PUB_MONITORAMENTO' 'PUB_CONTRACT' 'ctl/recon' 'internal object name pending V2-037' @('PLAT_CONTROL','PLAT_GATES') 'V2-020/V2-023/V2-037' @('view_etl:019_criar_view_bi_monitoramento') 'INTERNAL_NOT_PUBLIC_BY_DEFAULT' 'READ_ONLY_INTERNAL_GRANT_PENDING_V2_037' $pending
    New-DagNode 'PUB_DIM_FILIAIS' 'PUB_CONTRACT' 'pub' 'object name pending V2-037' @('DIM_FILIAIS') 'V2-035b/V2-037' @('view_etl:019_criar_view_dim_filiais') $pubRoute $pubWrite $pending
    New-DagNode 'PUB_DIM_CLIENTES' 'PUB_CONTRACT' 'pub' 'object name pending V2-037' @('DIM_CLIENTES') 'V2-035b/V2-037' @('view_etl:020_criar_view_dim_clientes') $pubRoute $pubWrite $pending
    New-DagNode 'PUB_INVENTARIO' 'PUB_CONTRACT' 'pub' 'object name pending V2-037' @('SRC_INVENTARIO','SRC_FRETES') 'V2-011/V2-031/V2-037' @('view_etl:020_criar_view_inventario_powerbi') $pubRoute $pubWrite $pending
    New-DagNode 'PUB_DIM_VEICULOS' 'PUB_CONTRACT' 'pub' 'object name pending V2-037' @('DIM_VEICULOS') 'V2-035b/V2-037' @('view_etl:021_criar_view_dim_veiculos') $pubRoute $pubWrite $pending
    New-DagNode 'PUB_SINISTROS' 'PUB_CONTRACT' 'pub' 'object name pending V2-037' @('SRC_SINISTROS') 'V2-032/V2-037' @('view_etl:021_criar_view_sinistros_powerbi') $pubRoute $pubWrite $pending
    New-DagNode 'PUB_DIM_MOTORISTAS' 'PUB_CONTRACT' 'pub' 'object name pending V2-037' @('DIM_MOTORISTAS') 'V2-035b/V2-037' @('view_etl:022_criar_view_dim_motoristas') $pubRoute $pubWrite $pending
    New-DagNode 'PUB_RASTER' 'PUB_CONTRACT' 'pub' 'object name pending V2-037' @('SRC_RASTER') 'V2-034a/V2-034b/V2-037' @('view_etl:022_criar_view_raster_sm_transit_time') $pubRoute $pubWrite 'CONDITIONAL_DISABLED'
    New-DagNode 'PUB_DIM_PLANOCONTAS' 'PUB_CONTRACT' 'pub' 'object name pending V2-037' @('DIM_PLANOCONTAS') 'V2-035b/V2-037' @('view_etl:023_criar_view_dim_planocontas') $pubRoute $pubWrite $pending
    New-DagNode 'PUB_DIM_USUARIOS' 'PUB_CONTRACT' 'pub' 'object name pending V2-037' @('DIM_USUARIOS') 'V2-033/V2-035b/V2-037' @('view_etl:024_criar_view_dim_usuarios') $pubRoute $pubWrite $pending
    New-DagNode 'PUB_FATO_MANIFESTOS' 'PUB_CONTRACT' 'pub' 'object name pending V2-037' @('MART_MANIFESTOS') 'V2-036/V2-037' @('view_etl:025_criar_view_fato_manifestos_dash') $pubRoute $pubWrite $pending
)

$fences = @(
    New-Fence 1 'FENCE-01-TARGET' 'TARGET_IDENTITY' 'Banco V2 produtivo novo, nome/servidor/alias exatos e allowlistados; sem renomear shadow, in-place ou wrapper cross-database.' 'Fingerprint sanitizado de alvo e aprovação; sem endpoint real no Git.' 'DBA_E_PLATAFORMA_DE_DADOS' 'V2-039a/V2-048b' 'NO_ROLLBACK_EFFECT_YET' 'EXTERNAL_INPUT_REQUIRED'
    New-Fence 2 'FENCE-02-SCHEMA' 'MIGRATION_AND_GRANTS' 'Migrations/fingerprint completos; migrator separado; runtime sem DDL/DML direto; reader somente nos contratos aprovados.' 'Versão, SHA, contagens de objetos/grants/denies e testes negativos.' 'DBA_E_PLATAFORMA_DE_DADOS' 'V2-037/V2-039/V2-048b' 'REBUILD_FROM_MIGRATIONS' 'DESIGN_RATIFIED_LOCAL'
    New-Fence 3 'FENCE-03-BOOTSTRAP' 'HISTORICAL_AND_DELTA' 'Bootstrap consistente por entidade e delta até Tcut fechados sem elevar watermark incremental.' 'Contagens/hashes sanitizados e ledger de partições; sem IDs reais.' 'OPERACOES_E_PLATAFORMA_DE_DADOS' 'V2-047/V2-048b' 'REBUILD_OR_REPLAY_BEFORE_ROUTE' 'PENDING_DEPENDENCIES'
    New-Fence 4 'FENCE-04-GATE' 'CUTOVER_READINESS' 'Toda responsabilidade da unidade database-wide satisfaz os subgates prévios de V2-014; condicionais estão aceitas como incluídas ou NOT_APPLICABLE. V2-014 permanece aberto até V2-048b.' 'Receipts por tarefa/onda, fingerprints e aceite nominal.' 'OWNER_DA_UNIDADE_DE_CUTOVER' 'V2-048b_PRECONDITIONS/V2-014_AFTER_REHEARSAL' 'BLOCK_CUTOVER' 'EXTERNAL_INPUT_REQUIRED'
    New-Fence 5 'FENCE-05-FREEZE' 'LEGACY_PROCESS_FREEZE' 'Schedulers/launchers do writer antigo parados e nenhuma execução em voo.' 'Estado agregado do scheduler/processo e lock, sem nome de principal ou caminho sensível.' 'OPERACOES' 'V2-048b' 'RETURN_TO_FROZEN_LEGACY_ONLY_BEFORE_PNR' 'EXTERNAL_INPUT_REQUIRED'
    New-Fence 6 'FENCE-06-LEGACY-WRITE' 'LEGACY_DATABASE_WRITE_FENCE' 'Principal real do writer antigo revogado, negado ou desabilitado para DML/EXECUTE/DDL; teste negativo obrigatório. Lock de aplicação isolado não atende este fence.' 'Resultado allow/deny sanitizado por classe de permissão e banco, sem principal nominal.' 'DBA_E_OPERACOES' 'V2-048b' 'RESTORE_OLD_PERMISSION_ONLY_BEFORE_PNR_AND_ONLY_BY_OWNER' 'EXTERNAL_INPUT_REQUIRED'
    New-Fence 7 'FENCE-07-V2-WRITE' 'V2_RUNTIME_PRE_ACTIVATION_FENCE' 'Role/procedures mínimas podem estar preparadas, mas o principal de serviço V2 permanece sem membership efetiva ou com autorização run deny-all; scheduler desligado isoladamente não é fence. Invocação direta do JAR deve falhar antes de FENCE-09.' 'Membership/authorization negada, grants/denies e teste negativo de invocação direta, todos sanitizados.' 'DBA_SEGURANCA_E_OPERACOES' 'V2-042/V2-039/V2-048b' 'KEEP_V2_RUN_AUTHORITY_DISABLED_BEFORE_PNR' 'EXTERNAL_INPUT_REQUIRED'
    New-Fence 8 'FENCE-08-ROUTE' 'CONSUMER_ROUTE' 'Troca atômica da conexão/alias/configuração para o banco V2 inteiro; rota por objeto/entidade continua proibida sem prova positiva.' 'Fingerprint do destino e smoke read-only dos contratos aprovados.' 'OWNER_DO_CONSUMIDOR_E_OPERACOES' 'V2-037/V2-048b' 'ROUTE_BACK_TO_FROZEN_LEGACY_ONLY_BEFORE_PNR' 'EXTERNAL_INPUT_REQUIRED'
    New-Fence 9 'FENCE-09-START' 'V2_RUN_AUTHORITY_AND_SCHEDULER_ENABLE' 'Somente depois dos fences e da rota, liberar membership/autorização run do principal V2 e habilitar o scheduler one-shot como uma ativação coordenada; legacy continua sem escrita.' 'Transição de autoridade, estado do scheduler, teste positivo autorizado e primeira janela planejada, todos sanitizados.' 'DBA_SEGURANCA_E_OPERACOES' 'V2-039/V2-042/V2-048b' 'REVOKE_V2_RUN_AUTHORITY_AND_STOP_SCHEDULER_ONLY_BEFORE_PNR' 'EXTERNAL_INPUT_REQUIRED'
    New-Fence 10 'FENCE-10-PNR' 'FIRST_AUTHORITATIVE_PRODUCTION_PUBLICATION' 'Primeira publicação V2 aceita como autoritativa em produção após freeze, write-fences e rota. V2-048b prova somente SIMULATED_PNR em ambiente isolado; PUBLISHED em shadow não conta.' 'No rehearsal: receipt SIMULATED_PNR opaco. Em produção: execution/cutover receipt, fingerprint, estado PUBLISHED e aceite nominal; sem ID de negócio.' 'OWNER_DA_UNIDADE_E_OWNER_DO_CONSUMIDOR' 'V2-048b_SIMULATION/V2-014_PRODUCTION' 'POINT_OF_NO_RETURN' 'EXTERNAL_INPUT_REQUIRED'
    New-Fence 11 'FENCE-11-ROLLFORWARD' 'POST_PNR_RECOVERY' 'Legado permanece read-only; recuperação usa restore/rebuild do V2 e replay do delta desde evidência imutável dentro de RPO.' 'Resultado de restore/replay, RTO/RPO medidos e reconciliação sanitizada.' 'DBA_OPERACOES_E_OWNER_DA_UNIDADE' 'V2-039/V2-048b' 'ROLL_FORWARD_ONLY' 'EXTERNAL_INPUT_REQUIRED'
)

$artifactCounts = [ordered]@{}
foreach ($type in $coveredArtifactTypes) {
    $artifactCounts[$type] = @($responsibilities | Where-Object { $_.artifact_type -eq $type }).Count
}
$logicalTableResponsibilityCount = @($responsibilities |
        Where-Object { $_.artifact_type -eq 'table_script' } |
        Select-Object -ExpandProperty logical_responsibility -Unique).Count

$manifest = [ordered]@{
    catalog_version = '2026-09-10.pos-b60-gates'
    roadmap_task = 'V2-048a'
    generator = 'scripts/validation/Build-CutoverTopologyCatalog.ps1'
    powershell_minimum_version = '7.0'
    source_policy = 'offline from V2-017 catalog and V2-019 manifests; no dashboards, credentials, network, database or payload values'
    decision_state = 'LOCAL_DESIGN_COMPLETE_EXTERNAL_REHEARSAL_PENDING'
    topology = [ordered]@{
        strategy = 'NEW_DEDICATED_V2_DATABASE'
        shadow_database_may_be_renamed = $false
        in_place_legacy_database = $false
        cross_database_wrappers = $false
        final_database_name = 'EXTERNAL_INPUT_REQUIRED'
        routing_mechanism = 'EXTERNAL_INPUT_REQUIRED'
        safe_cutover_granularity = 'DATABASE_WIDE'
        granular_cutover_proven = $false
        cutover_unit = 'CUTOVER-DB-01'
        technical_shadow_publication_is_point_of_no_return = $false
        point_of_no_return = 'FIRST_ACCEPTED_AUTHORITATIVE_V2_PRODUCTION_PUBLICATION'
        recovery_before_point_of_no_return = 'RETURN_TO_FROZEN_LEGACY_ONLY_AFTER_REHEARSED_OWNER_DECISION'
        recovery_after_point_of_no_return = 'ROLL_FORWARD_ONLY'
    }
    baselines = [ordered]@{
        schemas = @($schemaManifest.schemas)
        grant_allowlist_triplets = [int]$schemaManifest.grantPublisher.allowedTriplets
        entity_sources = [int]$portabilityManifest.artifact_counts.entity_source
        table_scripts = [int]$portabilityManifest.artifact_counts.table_script
        logical_table_responsibilities = $logicalTableResponsibilityCount
        materialized_facts = [int]$portabilityManifest.baselines.materialized_facts
        etl_owned_views = [int]$portabilityManifest.baselines.etl_owned_views
        procedures = [int]$portabilityManifest.artifact_counts.procedure
        windows_scripts = [int]$portabilityManifest.artifact_counts.windows_script
        windows_aliases = [int]$portabilityManifest.artifact_counts.windows_alias
        legacy_command_entries = [int]$portabilityManifest.artifact_counts.command_default + [int]$portabilityManifest.artifact_counts.command_flag
        responsibility_rows = $responsibilities.Count
        dag_nodes = $dag.Count
        fence_steps = $fences.Count
    }
    schema_foundation_contract = [ordered]@{
        roadmap_task = [string]$schemaManifest.roadmapTask
        manifest_version = [int]$schemaManifest.manifestVersion
        manifest_sha256 = $schemaManifestSha256
        schema_owner = [string]$schemaManifest.schemaOwner.name
        grant_publisher = [string]$schemaManifest.grantPublisher.procedure
        role_names = @($schemaManifest.roles.PSObject.Properties.Name | Sort-Object)
    }
    usuario_dimension_contract = [ordered]@{
        roadmap_task = [string]$usuariosDimensionManifest.roadmapTask
        manifest_version = [int]$usuariosDimensionManifest.manifestVersion
        manifest_sha256 = $usuariosDimensionManifestSha256
        object = [string]$usuariosDimensionManifest.object.schema + '.' + `
            [string]$usuariosDimensionManifest.object.name
        local_state = [string]$usuariosDimensionManifest.localState
        consumer_contract = [string]$usuariosDimensionManifest.consumerBoundary.pubObject
    }
    responsibility_artifact_counts = $artifactCounts
    invariants = @(
        'Implementation and parity may advance per entity, but production routing remains database-wide until both read routing and write-fence granularity are positively proven.',
        'Stopping a process, a scheduler state, sp_getapplock or a V2 lease alone is not a write-fence for the legacy writer.',
        'The authoritative legacy fence is process freeze plus verified database permission revocation or denial for the actual writer principal.',
        'The V2 runtime writes only through allowlisted procedures and never receives DDL or direct domain DML.',
        'A disabled V2 scheduler alone is not a write-fence: before activation, the service principal has no effective run membership or the authorization boundary denies run, and direct invocation must fail.',
        'The 18 consumer-facing ETL-owned contracts require an external consumer manifest and route evidence; the monitoring contract remains internal by default and requires explicit V2-037 scope/grants.',
        'A PUBLISHED event in shadow is technical evidence only and never triggers the point of no return.',
        'After the first accepted authoritative V2 production publication, the legacy writer is never silently re-enabled.'
    )
    external_inputs = @(
        'final production server/database and routing mechanism',
        'consumer manifest, reader principals and nominal acceptance for 18 consumer-facing contracts and five facts; internal scope/grants for the monitoring contract',
        'actual legacy writer principal, scheduler/process inventory and revocation authority',
        'V2 runtime, migrator, bootstrap and reader service principals/memberships',
        'cutover window, Tcut, RTO/RPO, backup/restore and replay evidence',
        'decision and acceptance for conditional Raster responsibility'
    )
}

Write-DeterministicCsv -Path (Join-Path $outputRootFull 'responsabilidades.csv') -Rows @($responsibilities)
Write-DeterministicCsv -Path (Join-Path $outputRootFull 'dag.csv') -Rows @($dag | Sort-Object node_id)
Write-DeterministicCsv -Path (Join-Path $outputRootFull 'fences.csv') -Rows @($fences | Sort-Object sequence)
$manifestPath = Join-Path $outputRootFull 'manifesto.json'
Write-DeterministicText -Path $manifestPath -Content ($manifest | ConvertTo-Json -Depth 8)
$manifestSha256 = (Get-FileHash -LiteralPath $manifestPath -Algorithm SHA256).Hash.ToLowerInvariant()
Write-DeterministicText -Path (Join-Path $outputRootFull 'manifesto.sha256') -Content "$manifestSha256  manifesto.json"

Write-Output "PASS: catálogo V2-048a gerado: $($responsibilities.Count) responsabilidades, $($dag.Count) nós DAG, $($fences.Count) fences."
