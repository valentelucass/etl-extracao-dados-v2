#requires -Version 7.0

[CmdletBinding()]
param(
    [Parameter()]
    [switch]$VerifyGenerated
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$v2Root = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$catalogRoot = Join-Path $v2Root 'docs\catalogos\cutover'
$portabilityRoot = Join-Path $v2Root 'docs\catalogos\portabilidade'
$generatedFiles = @('responsabilidades.csv', 'dag.csv', 'fences.csv', 'manifesto.json', 'manifesto.sha256')
$requiredDocuments = @(
    'docs\adr\0015-topologia-material-e-fences-de-cutover.md',
    'docs\runbooks\freeze-cutover-e-recuperacao.md',
    'docs\catalogos\cutover\README.md'
)

& (Join-Path $PSScriptRoot 'Test-PortabilityCatalog.ps1') -VerifyGenerated | Out-Host
& (Join-Path $PSScriptRoot 'Test-SchemaFoundationManifest.ps1') | Out-Host

function Assert-True {
    param(
        [Parameter(Mandatory)][bool]$Condition,
        [Parameter(Mandatory)][string]$Message
    )

    if (-not $Condition) {
        throw $Message
    }
}

foreach ($file in $generatedFiles) {
    Assert-True (Test-Path -LiteralPath (Join-Path $catalogRoot $file) -PathType Leaf) "Artefato obrigatório ausente: $file."
}
foreach ($document in $requiredDocuments) {
    Assert-True (Test-Path -LiteralPath (Join-Path $v2Root $document) -PathType Leaf) "Documento obrigatório ausente: $document."
}

if ($VerifyGenerated) {
    $targetRoot = [System.IO.Path]::GetFullPath((Join-Path $v2Root 'target'))
    $tempOutput = [System.IO.Path]::GetFullPath((Join-Path $targetRoot ('cutover-topology-verify-' + [guid]::NewGuid().ToString('N'))))
    $pathComparison = if ($IsWindows) {
        [System.StringComparison]::OrdinalIgnoreCase
    } else {
        [System.StringComparison]::Ordinal
    }
    $tempParent = [System.IO.Path]::GetDirectoryName($tempOutput)
    Assert-True ($tempParent.Equals($targetRoot, $pathComparison) -and
        [System.IO.Path]::GetFileName($tempOutput) -cmatch '^cutover-topology-verify-[0-9a-f]{32}$') 'Diretório temporário fora do boundary direto de target.'
    try {
        & (Join-Path $PSScriptRoot 'Build-CutoverTopologyCatalog.ps1') -OutputRoot $tempOutput | Out-Host
        foreach ($file in $generatedFiles) {
            $expected = (Get-FileHash -LiteralPath (Join-Path $catalogRoot $file) -Algorithm SHA256).Hash
            $actual = (Get-FileHash -LiteralPath (Join-Path $tempOutput $file) -Algorithm SHA256).Hash
            Assert-True ($expected -ceq $actual) "Artefato canônico desatualizado ou geração não determinística: $file."
        }
    } finally {
        if (Test-Path -LiteralPath $tempOutput) {
            $tempItem = Get-Item -LiteralPath $tempOutput -Force
            Assert-True (-not ($tempItem.Attributes -band [System.IO.FileAttributes]::ReparsePoint) -and
                [System.IO.Path]::GetDirectoryName($tempItem.FullName).Equals($targetRoot, $pathComparison) -and
                $tempItem.Name -cmatch '^cutover-topology-verify-[0-9a-f]{32}$') 'Boundary temporário inválido; remoção recusada.'
            Remove-Item -LiteralPath $tempOutput -Recurse -Force
        }
    }
}

$responsibilities = @(Import-Csv -LiteralPath (Join-Path $catalogRoot 'responsabilidades.csv') -Encoding utf8)
$dag = @(Import-Csv -LiteralPath (Join-Path $catalogRoot 'dag.csv') -Encoding utf8)
$fences = @(Import-Csv -LiteralPath (Join-Path $catalogRoot 'fences.csv') -Encoding utf8)
$manifestPath = Join-Path $catalogRoot 'manifesto.json'
$manifest = Get-Content -LiteralPath $manifestPath -Raw -Encoding utf8 | ConvertFrom-Json
$inventory = @(Import-Csv -LiteralPath (Join-Path $portabilityRoot 'inventario-artefatos.csv') -Encoding utf8)
$schemaManifestPath = Join-Path $v2Root 'database\manifest\schema-foundation.json'
$schemaManifest = Get-Content -LiteralPath $schemaManifestPath -Raw -Encoding utf8 | ConvertFrom-Json
$schemaManifestSha256 = (Get-FileHash -LiteralPath $schemaManifestPath -Algorithm SHA256).Hash.ToLowerInvariant()

$requiredResponsibilityColumns = @(
    'artifact_key','artifact_type','artifact_id','source_path','responsibility','logical_responsibility','legacy_execution_effect','decision',
    'v2_destination','owner_role','due_gate','cutover_unit','cutover_inclusion',
    'legacy_write_fence','v2_write_boundary','route_boundary','evidence_status'
)
$requiredDagColumns = @(
    'node_id','node_kind','v2_zone','v2_target','depends_on','owner_task','legacy_artifacts',
    'cutover_unit','route_boundary','write_boundary','consumer_boundary','readiness'
)
$requiredFenceColumns = @(
    'sequence','fence_id','boundary','required_state','sanitized_evidence','owner_role',
    'due_gate','rollback_rule','status'
)

foreach ($column in $requiredResponsibilityColumns) {
    Assert-True ($responsibilities[0].PSObject.Properties.Name -contains $column) "Coluna ausente em responsabilidades.csv: $column."
}
foreach ($column in $requiredDagColumns) {
    Assert-True ($dag[0].PSObject.Properties.Name -contains $column) "Coluna ausente em dag.csv: $column."
}
foreach ($column in $requiredFenceColumns) {
    Assert-True ($fences[0].PSObject.Properties.Name -contains $column) "Coluna ausente em fences.csv: $column."
}

$fingerprintLine = (Get-Content -LiteralPath (Join-Path $catalogRoot 'manifesto.sha256') -Raw -Encoding utf8).Trim()
Assert-True ($fingerprintLine -cmatch '^[0-9a-f]{64}  manifesto\.json$') 'Formato inválido de manifesto.sha256.'
$expectedFingerprint = $fingerprintLine.Substring(0, 64)
$actualFingerprint = (Get-FileHash -LiteralPath $manifestPath -Algorithm SHA256).Hash.ToLowerInvariant()
Assert-True ($expectedFingerprint -ceq $actualFingerprint) 'Fingerprint do manifesto de cutover divergente.'

Assert-True ($manifest.roadmap_task -ceq 'V2-048a') 'O catálogo não pertence a V2-048a.'
Assert-True ($manifest.decision_state -ceq 'LOCAL_DESIGN_COMPLETE_EXTERNAL_REHEARSAL_PENDING') 'Estado local/externo do catálogo divergente.'
Assert-True ($manifest.topology.strategy -ceq 'NEW_DEDICATED_V2_DATABASE') 'A estratégia precisa usar banco V2 novo e dedicado.'
Assert-True ($manifest.topology.shadow_database_may_be_renamed -eq $false) 'O banco shadow não pode ser renomeado como produção.'
Assert-True ($manifest.topology.in_place_legacy_database -eq $false) 'Cutover in-place no legado não está autorizado.'
Assert-True ($manifest.topology.cross_database_wrappers -eq $false) 'Wrappers cross-database estão proibidos.'
Assert-True ($manifest.topology.final_database_name -ceq 'EXTERNAL_INPUT_REQUIRED') 'O nome final do banco não pode ser inventado.'
Assert-True ($manifest.topology.routing_mechanism -ceq 'EXTERNAL_INPUT_REQUIRED') 'O mecanismo de roteamento não pode ser inventado.'
Assert-True ($manifest.topology.safe_cutover_granularity -ceq 'DATABASE_WIDE') 'A granularidade segura deve permanecer database-wide.'
Assert-True ($manifest.topology.granular_cutover_proven -eq $false) 'O catálogo não pode alegar corte granular sem prova física.'
Assert-True ($manifest.topology.cutover_unit -ceq 'CUTOVER-DB-01') 'A unidade de corte canônica diverge.'
Assert-True ($manifest.topology.technical_shadow_publication_is_point_of_no_return -eq $false) 'Publicação em shadow não pode ser PNR.'
Assert-True ($manifest.topology.point_of_no_return -ceq 'FIRST_ACCEPTED_AUTHORITATIVE_V2_PRODUCTION_PUBLICATION') 'Definição do ponto de não retorno divergente.'
Assert-True ($manifest.topology.recovery_after_point_of_no_return -ceq 'ROLL_FORWARD_ONLY') 'Recuperação pós-PNR deve ser roll-forward.'
Assert-True (@(Compare-Object @('ctl','stg','core','ref','mart','pub','recon') @($manifest.baselines.schemas) -CaseSensitive).Count -eq 0) 'Schemas V2-019 divergentes no catálogo de cutover.'
Assert-True ([int]$manifest.baselines.grant_allowlist_triplets -eq
    [int]$schemaManifest.grantPublisher.allowedTriplets) `
    'A quantidade de triplets deve corresponder à fundação validada, sem conceder permissões.'
Assert-True ($manifest.schema_foundation_contract.roadmap_task -ceq 'V2-019' -and
    [int]$manifest.schema_foundation_contract.manifest_version -eq [int]$schemaManifest.manifestVersion -and
    $manifest.schema_foundation_contract.manifest_sha256 -ceq $schemaManifestSha256 -and
    $manifest.schema_foundation_contract.schema_owner -ceq [string]$schemaManifest.schemaOwner.name -and
    $manifest.schema_foundation_contract.grant_publisher -ceq [string]$schemaManifest.grantPublisher.procedure -and
    @(Compare-Object @($manifest.schema_foundation_contract.role_names) @($schemaManifest.roles.PSObject.Properties.Name) -CaseSensitive).Count -eq 0) 'Fingerprint/roles/grants da fundação V2-019 divergentes no catálogo.'
Assert-True ($manifest.usuario_dimension_contract.roadmap_task -ceq 'V2-035b-USUARIOS' `
        -and [int]$manifest.usuario_dimension_contract.manifest_version -eq 1 `
        -and $manifest.usuario_dimension_contract.object -ceq `
            'core.v_usuario_dimension_current_v1' `
        -and $manifest.usuario_dimension_contract.local_state -ceq `
            'IMPLEMENTED_IN_SHADOW' `
        -and $manifest.usuario_dimension_contract.consumer_contract -ceq `
            'DEFERRED_TO_V2_037' `
        -and $manifest.usuario_dimension_contract.manifest_sha256 -ceq `
            (Get-FileHash -LiteralPath (Join-Path $v2Root `
                'database\manifest\usuarios-dimension-current.json') `
                -Algorithm SHA256).Hash.ToLowerInvariant()) `
    'A DAG não está ligada ao manifesto da dimensão current de Usuários.'

Assert-True ($responsibilities.Count -eq [int]$manifest.baselines.responsibility_rows) 'Quantidade de responsabilidades diverge do manifesto.'
Assert-True ($dag.Count -eq [int]$manifest.baselines.dag_nodes) 'Quantidade de nós DAG diverge do manifesto.'
Assert-True ($fences.Count -eq [int]$manifest.baselines.fence_steps) 'Quantidade de fences diverge do manifesto.'
Assert-True ([int]$manifest.baselines.logical_table_responsibilities -eq 34) 'O baseline deve registrar 34 responsabilidades lógicas de tabela.'

$logicalTableResponsibilities = @($responsibilities | Where-Object { $_.artifact_type -eq 'table_script' })
$logicalTableGroups = @($logicalTableResponsibilities | Group-Object logical_responsibility)
Assert-True (@($logicalTableResponsibilities | Where-Object { [string]::IsNullOrWhiteSpace($_.logical_responsibility) }).Count -eq 0) 'Script de tabela sem responsabilidade lógica.'
Assert-True ($logicalTableGroups.Count -eq [int]$manifest.baselines.logical_table_responsibilities) 'A matriz não demonstra as 34 responsabilidades lógicas de tabela.'
Assert-True (@($logicalTableGroups | Where-Object { $_.Name -ceq 'TABLE:dim_usuarios' -and $_.Count -eq 2 }).Count -eq 1) 'CREATE/ALTER de dim_usuarios não foram consolidados na mesma responsabilidade lógica.'
Assert-True (@($logicalTableGroups | Where-Object { $_.Name -cne 'TABLE:dim_usuarios' -and $_.Count -ne 1 }).Count -eq 0) 'Há scripts de tabela agrupados sem regra explícita.'

$coveredTypes = @('database_runner','command_default','command_flag','entity_source','procedure','sql_auxiliary','table_script','view_etl','view_wrapper','windows_alias','windows_script')
$expectedArtifactTypeCounts = [ordered]@{
    database_runner = 1
    command_default = 1
    command_flag = 37
    entity_source = 11
    procedure = 5
    sql_auxiliary = 3
    table_script = 35
    view_etl = 19
    view_wrapper = 2
    windows_alias = 2
    windows_script = 15
}
foreach ($type in $expectedArtifactTypeCounts.Keys) {
    Assert-True (@($responsibilities | Where-Object artifact_type -ceq $type).Count -eq
        $expectedArtifactTypeCounts[$type]) "Quantidade de artefatos $type exige revisão do desenho de cutover."
}
$expectedArtifacts = @($inventory | Where-Object { $_.artifact_type -in $coveredTypes } | ForEach-Object {
        "$($_.artifact_type):$($_.artifact_id):$($_.source_path)"
    } | Sort-Object)
$actualArtifacts = @($responsibilities.artifact_key | Sort-Object)
Assert-True (@(Compare-Object $expectedArtifacts $actualArtifacts -CaseSensitive).Count -eq 0) 'A matriz não cobre exatamente os artefatos de cutover do catálogo V2-017.'
Assert-True (@($responsibilities.artifact_key | Group-Object | Where-Object Count -ne 1).Count -eq 0) 'Há artifact_key duplicada na matriz.'
Assert-True (@($responsibilities | Where-Object { $_.decision -notin @('PRESERVE','SUBSTITUTE','CONSOLIDATE','RETIRE','CONDITIONAL') }).Count -eq 0) 'Decisão de portabilidade fora da allowlist.'
Assert-True (@($responsibilities | Where-Object { [string]::IsNullOrWhiteSpace($_.owner_role) -or [string]::IsNullOrWhiteSpace($_.due_gate) }).Count -eq 0) 'Responsabilidade sem owner-papel ou gate.'
Assert-True (@($responsibilities | Where-Object { $_.cutover_unit -cne 'CUTOVER-DB-01' }).Count -eq 0) 'Responsabilidade fora da unidade database-wide.'
Assert-True (@($responsibilities | Where-Object { $_.artifact_type -eq 'view_wrapper' -and ($_.decision -cne 'RETIRE' -or $_.cutover_inclusion -cne 'FORBIDDEN_CROSS_DATABASE' -or $_.route_boundary -cne 'FORBIDDEN') }).Count -eq 0) 'Wrapper cross-database não está integralmente retirado.'
Assert-True (@($responsibilities | Where-Object { $_.decision -eq 'RETIRE' -and $_.artifact_type -ne 'view_wrapper' -and ($_.route_boundary -cne 'NOT_APPLICABLE_RETIRED' -or $_.v2_write_boundary -cne 'NOT_APPLICABLE_RETIRED') }).Count -eq 0) 'Responsabilidade retirada sugere boundary V2 ativo.'
$monitoringResponsibilities = @($responsibilities | Where-Object {
        $_.artifact_type -eq 'view_etl' -and $_.artifact_id -eq '019_criar_view_bi_monitoramento'
    })
$consumerFacingResponsibilities = @($responsibilities | Where-Object {
        $_.artifact_type -eq 'view_etl' -and $_.artifact_id -ne '019_criar_view_bi_monitoramento'
    })
Assert-True ($monitoringResponsibilities.Count -eq 1 -and
    $monitoringResponsibilities[0].route_boundary -ceq 'INTERNAL_NOT_PUBLIC_BY_DEFAULT' -and
    $monitoringResponsibilities[0].v2_write_boundary -ceq 'READ_ONLY_INTERNAL_GRANT_PENDING_V2_037') 'O contrato de monitoramento deve permanecer interno por default.'
Assert-True ($consumerFacingResponsibilities.Count -eq 18 -and
    @($consumerFacingResponsibilities | Where-Object {
            $_.route_boundary -cne 'DATABASE_ENDPOINT_OR_ALIAS_UNPROVEN' -or
            $_.v2_write_boundary -cne 'READ_ONLY_PUB_GRANT_PENDING_V2_037'
        }).Count -eq 0) 'Contrato consumer-facing ganhou rota/grant não comprovado.'
$invocationResponsibilities = @($responsibilities | Where-Object {
        $_.artifact_type -in @('command_default','command_flag','windows_alias','windows_script')
    })
Assert-True ($invocationResponsibilities.Count -eq 55) 'A matriz deve cobrir 38 comandos, dois aliases e 15 scripts Windows.'
Assert-True (@($invocationResponsibilities | Where-Object {
            $_.legacy_execution_effect -eq 'READ_ONLY_PROVEN_LOCAL' -and
            $_.legacy_write_fence -cne 'NO_WRITE_FENCE_READ_ONLY_LOCAL'
        }).Count -eq 0) 'Superfície read-only foi confundida com writer legado.'
Assert-True (@($invocationResponsibilities | Where-Object {
            $_.legacy_execution_effect -eq 'NO_DOMAIN_WRITE_CLAIM_PENDING_EXTERNAL_INVENTORY' -and
            $_.legacy_write_fence -cne 'FREEZE_UNLESS_NO_WRITE_IS_POSITIVELY_PROVEN'
        }).Count -eq 0) 'Superfície sem DML comprovado deve falhar fechada até inventário externo.'
Assert-True (@($invocationResponsibilities | Where-Object {
            $_.legacy_execution_effect -eq 'WRITE_OR_CONTROL_CAPABLE' -and
            $_.legacy_write_fence -cne 'PROCESS_FREEZE_PLUS_DATABASE_PRINCIPAL_REVOKE'
        }).Count -eq 0) 'Superfície mutante/de controle não recebeu freeze e fence material.'
Assert-True (@($responsibilities | Where-Object { $_.legacy_write_fence -eq 'APPLICATION_LOCK_ONLY' }).Count -eq 0) 'Application lock não pode ser tratado como fence material.'

$expectedKinds = [ordered]@{
    PLATFORM = 5
    REFERENCE_SET = 1
    SOURCE = 11
    RELATION = 2
    DIMENSION = 6
    MART_FACT = 5
    PUB_CONTRACT = 19
}
foreach ($kind in $expectedKinds.Keys) {
    Assert-True (@($dag | Where-Object { $_.node_kind -ceq $kind }).Count -eq $expectedKinds[$kind]) "Quantidade de nós $kind divergente."
}
Assert-True (@($dag.node_id | Group-Object | Where-Object Count -ne 1).Count -eq 0) 'Há node_id duplicado no DAG.'
Assert-True (@($dag | Where-Object { $_.cutover_unit -cne 'CUTOVER-DB-01' }).Count -eq 0) 'Nó DAG fora da unidade database-wide.'
Assert-True (@($dag | Where-Object { $_.node_kind -eq 'PUB_CONTRACT' -and $_.consumer_boundary -notin @('EXTERNAL_CONSUMER_MANIFEST_PENDING','INTERNAL_SCOPE_PENDING_V2_037') }).Count -eq 0) 'Contrato publicado sem consumer boundary pendente explícito.'
Assert-True (@($dag | Where-Object { $_.node_kind -ne 'PUB_CONTRACT' -and $_.consumer_boundary -cne 'NOT_A_CONSUMER_CONTRACT' }).Count -eq 0) 'Nó interno classificado incorretamente como contrato de consumidor.'
$usuariosNode = @($dag | Where-Object node_id -eq 'SRC_USUARIOS')
Assert-True ($usuariosNode.Count -eq 1 `
        -and $usuariosNode[0].v2_zone -ceq 'core' `
        -and $usuariosNode[0].v2_target -ceq 'usuario current + usuario_history in shadow' `
        -and $usuariosNode[0].readiness -ceq 'LOCAL_SHADOW_IMPLEMENTED_PUBLICATION_BLOCKED' `
        -and $usuariosNode[0].depends_on -ceq 'PLAT_RUNTIME') `
    'O DAG não diferencia a implementação local de Usuários dos gates externos/publicação ainda pendentes.'
$usuariosDimensionNode = @($dag | Where-Object node_id -eq 'DIM_USUARIOS')
$usuariosConsumerNode = @($dag | Where-Object node_id -eq 'PUB_DIM_USUARIOS')
Assert-True ($usuariosDimensionNode.Count -eq 1 `
        -and $usuariosDimensionNode[0].v2_zone -ceq 'core' `
        -and $usuariosDimensionNode[0].v2_target -ceq `
            'core.v_usuario_dimension_current_v1' `
        -and $usuariosDimensionNode[0].owner_task -ceq 'V2-035b' `
        -and $usuariosDimensionNode[0].route_boundary -ceq `
            'INTERNAL_SAME_DATABASE' `
        -and $usuariosDimensionNode[0].write_boundary -ceq `
            'READ_ONLY_INTERNAL_NO_CONSUMER_GRANT' `
        -and $usuariosDimensionNode[0].readiness -ceq `
            'LOCAL_SHADOW_IMPLEMENTED_CONSUMER_CONTRACT_BLOCKED') `
    'A dimensão interna de Usuários não está fechada separadamente na DAG.'
Assert-True ($usuariosConsumerNode.Count -eq 1 `
        -and $usuariosConsumerNode[0].v2_zone -ceq 'pub' `
        -and $usuariosConsumerNode[0].v2_target -ceq `
            'object name pending V2-037' `
        -and $usuariosConsumerNode[0].route_boundary -ceq `
            'DATABASE_ENDPOINT_OR_ALIAS_UNPROVEN' `
        -and $usuariosConsumerNode[0].write_boundary -ceq `
            'READ_ONLY_PUB_GRANT_PENDING_V2_037' `
        -and $usuariosConsumerNode[0].consumer_boundary -ceq `
            'EXTERNAL_CONSUMER_MANIFEST_PENDING' `
        -and $usuariosConsumerNode[0].readiness -ceq `
            'PENDING_IMPLEMENTATION_OR_EXTERNAL_GATE') `
    'O contrato consumidor de Usuários foi antecipado antes de V2-037.'
$referencesNode = @($dag | Where-Object node_id -eq 'REF_GOVERNADAS')
Assert-True ($referencesNode.Count -eq 1 `
        -and $referencesNode[0].v2_zone -ceq 'ref' `
        -and $referencesNode[0].readiness -ceq `
            'LOCAL_FOUNDATION_PRESENT_EXTERNAL_BASELINE_BLOCKED' `
        -and $referencesNode[0].write_boundary -ceq `
            'OWNER_ONLY_IMPORT_RUNTIME_NO_DIRECT_ACCESS' `
        -and $referencesNode[0].v2_target -match 'no productive baseline') `
    'O DAG não separa a fundação local de referências da baseline/ativação externa.'

$nodeIds = @($dag.node_id)
foreach ($node in $dag) {
    $dependencies = if ([string]::IsNullOrWhiteSpace($node.depends_on)) { @() } else { @($node.depends_on.Split(';')) }
    Assert-True ($dependencies -notcontains $node.node_id) "Nó $($node.node_id) depende de si mesmo."
    foreach ($dependency in $dependencies) {
        Assert-True ($nodeIds -ccontains $dependency) "Dependência inexistente no DAG: $($node.node_id) -> $dependency."
    }
}

$resolved = @{}
while ($resolved.Count -lt $dag.Count) {
    $ready = @()
    foreach ($node in $dag) {
        if ($resolved.ContainsKey($node.node_id)) {
            continue
        }
        $dependencies = if ([string]::IsNullOrWhiteSpace($node.depends_on)) {
            @()
        } else {
            @($node.depends_on.Split(';'))
        }
        $unresolvedDependencies = @($dependencies | Where-Object { -not $resolved.ContainsKey($_) })
        if ($unresolvedDependencies.Count -eq 0) {
            $ready += $node
        }
    }
    Assert-True ($ready.Count -gt 0) 'O DAG contém ciclo.'
    foreach ($node in $ready) {
        $resolved[$node.node_id] = $true
    }
}

function Get-MappedArtifactKeys {
    param([Parameter(Mandatory)][string]$Prefix)

    return @($dag | ForEach-Object {
            if (-not [string]::IsNullOrWhiteSpace($_.legacy_artifacts)) {
                $_.legacy_artifacts.Split(';')
            }
        } | Where-Object { $_ -clike "$Prefix*" } | Sort-Object)
}

foreach ($type in @('entity_source','procedure','view_etl','command_default','command_flag','windows_alias','windows_script')) {
    $expected = @($inventory | Where-Object { $_.artifact_type -ceq $type } | ForEach-Object { "$type`:$($_.artifact_id)" } | Sort-Object)
    $actual = @(Get-MappedArtifactKeys -Prefix "$type`:")
    Assert-True (@(Compare-Object $expected $actual -CaseSensitive).Count -eq 0) "O DAG não mapeia exatamente os artefatos $type."
    Assert-True (@($actual | Group-Object | Where-Object Count -ne 1).Count -eq 0) "Artefato $type duplicado no DAG."
}

$orderedFences = @($fences | Sort-Object { [int]$_.sequence })
for ($index = 0; $index -lt $orderedFences.Count; $index++) {
    Assert-True ([int]$orderedFences[$index].sequence -eq ($index + 1)) 'Sequência de fences possui lacuna ou duplicata.'
}
Assert-True (@($fences | Where-Object { $_.rollback_rule -ceq 'POINT_OF_NO_RETURN' }).Count -eq 1) 'Deve existir exatamente um ponto de não retorno.'
$legacyFence = $fences | Where-Object fence_id -ceq 'FENCE-06-LEGACY-WRITE'
$v2Fence = $fences | Where-Object fence_id -ceq 'FENCE-07-V2-WRITE'
$routeFence = $fences | Where-Object fence_id -ceq 'FENCE-08-ROUTE'
$pnrFence = $fences | Where-Object fence_id -ceq 'FENCE-10-PNR'
$readinessFence = $fences | Where-Object fence_id -ceq 'FENCE-04-GATE'
Assert-True ([int]$legacyFence.sequence -lt [int]$v2Fence.sequence -and [int]$v2Fence.sequence -lt [int]$routeFence.sequence -and [int]$routeFence.sequence -lt [int]$pnrFence.sequence) 'Ordem material de write-fences, rota e PNR inválida.'
Assert-True ($legacyFence.required_state -match 'revogado|negado|desabilitado') 'Fence legado não exige revogação/negação verificável.'
Assert-True ($v2Fence.required_state -match 'scheduler desligado isoladamente não é fence' -and
    $v2Fence.required_state -match 'Invocação direta do JAR deve falhar' -and
    ($fences | Where-Object fence_id -ceq 'FENCE-09-START').required_state -match 'membership/autorização run') 'O fence V2 não impede invocação direta antes da ativação coordenada.'
Assert-True ($pnrFence.required_state -match 'shadow não conta') 'PNR não diferencia publicação produtiva de shadow.'
Assert-True ($pnrFence.required_state -match 'SIMULATED_PNR' -and $pnrFence.due_gate -ceq 'V2-048b_SIMULATION/V2-014_PRODUCTION') 'O rehearsal V2-048b deve permanecer distinto do PNR produtivo de V2-014.'
Assert-True ($readinessFence.required_state -match 'V2-014 permanece aberto até V2-048b') 'O readiness não pode criar dependência circular V2-048b/V2-014.'
Assert-True (@($fences | Where-Object { [string]::IsNullOrWhiteSpace($_.owner_role) -or [string]::IsNullOrWhiteSpace($_.due_gate) }).Count -eq 0) 'Fence sem owner-papel ou gate.'

$documentText = foreach ($document in $requiredDocuments) {
    Get-Content -LiteralPath (Join-Path $v2Root $document) -Raw -Encoding utf8
}
$allText = ($documentText + @(
        (Get-Content -LiteralPath (Join-Path $catalogRoot 'responsabilidades.csv') -Raw -Encoding utf8),
        (Get-Content -LiteralPath (Join-Path $catalogRoot 'dag.csv') -Raw -Encoding utf8),
        (Get-Content -LiteralPath (Join-Path $catalogRoot 'fences.csv') -Raw -Encoding utf8),
        (Get-Content -LiteralPath $manifestPath -Raw -Encoding utf8)
    )) -join "`n"
Assert-True ($allText -notmatch '(?i)dashboards[\\/]') 'O bundle referencia uma árvore de dashboards proibida.'
Assert-True ($allText -notmatch '(?i)jdbc:sqlserver://') 'O bundle não pode conter endpoint JDBC.'
Assert-True ($allText -notmatch '(?i)https?://') 'O bundle não pode conter URL externa.'
Assert-True ($allText -notmatch '(?i)BEGIN[ _-]?(RSA|OPENSSH|EC|DSA)[ _-]?PRIVATE') 'O bundle contém marcador de chave privada.'
Assert-True ($allText -match 'SIMULATED_PNR') 'O bundle não separa rehearsal do ponto de não retorno produtivo.'
Assert-True ($manifest.powershell_minimum_version -ceq '7.0') 'A versão mínima do gerador deve permanecer explícita.'

Write-Output "PASS: catálogo V2-048a validado: $($responsibilities.Count) responsabilidades, $($dag.Count) nós DAG, $($fences.Count) fences; unidade DATABASE_WIDE e PNR fail-closed."
