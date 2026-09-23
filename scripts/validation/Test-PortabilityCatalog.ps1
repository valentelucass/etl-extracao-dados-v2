[CmdletBinding()]
param(
    [Parameter()]
    [switch]$VerifyGenerated
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$v2Root = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$catalogRoot = Join-Path $v2Root 'docs\catalogos\portabilidade'
$catalogFiles = @(
    'inventario-artefatos.csv',
    'matriz-campos.csv',
    'regras-negocio.csv',
    'matriz-protecao-dados.csv',
    'manifesto.json'
)

function Assert-True {
    param(
        [Parameter(Mandatory)][bool]$Condition,
        [Parameter(Mandatory)][string]$Message
    )
    if (-not $Condition) { throw $Message }
}

function Assert-ExactCount {
    param(
        [Parameter(Mandatory)][object[]]$Rows,
        [Parameter(Mandatory)][scriptblock]$Where,
        [Parameter(Mandatory)][int]$Expected,
        [Parameter(Mandatory)][string]$Label
    )
    $actual = @($Rows | Where-Object $Where).Count
    Assert-True ($actual -eq $Expected) "${Label}: esperado=$Expected, atual=$actual."
}

foreach ($file in $catalogFiles) {
    $path = Join-Path $catalogRoot $file
    Assert-True (Test-Path -LiteralPath $path -PathType Leaf) "Artefato obrigatório ausente: $file."
}

if ($VerifyGenerated) {
    $tempOutput = Join-Path $v2Root ('target\portability-catalog-verify-' + [guid]::NewGuid().ToString('N'))
    try {
        & (Join-Path $PSScriptRoot 'Build-PortabilityCatalog.ps1') -OutputRoot $tempOutput | Out-Host
        foreach ($file in $catalogFiles) {
            $expected = (Get-FileHash -Algorithm SHA256 -LiteralPath (Join-Path $catalogRoot $file)).Hash
            $actual = (Get-FileHash -Algorithm SHA256 -LiteralPath (Join-Path $tempOutput $file)).Hash
            Assert-True ($expected -eq $actual) "Artefato canônico desatualizado ou geração não determinística: $file."
        }
    } finally {
        if (Test-Path -LiteralPath $tempOutput) { Remove-Item -LiteralPath $tempOutput -Recurse -Force }
    }
}

$artifacts = @(Import-Csv -LiteralPath (Join-Path $catalogRoot 'inventario-artefatos.csv'))
$fields = @(Import-Csv -LiteralPath (Join-Path $catalogRoot 'matriz-campos.csv'))
$rules = @(Import-Csv -LiteralPath (Join-Path $catalogRoot 'regras-negocio.csv'))
$protection = @(Import-Csv -LiteralPath (Join-Path $catalogRoot 'matriz-protecao-dados.csv'))
$manifest = Get-Content -LiteralPath (Join-Path $catalogRoot 'manifesto.json') -Raw | ConvertFrom-Json

$requiredArtifactColumns = @('artifact_type','artifact_id','source_path','responsibility','decision','v2_destination','owner_role','acceptor','due_gate','status','evidence','publication_blocked','notes')
$requiredFieldColumns = @('matrix_id','row_kind','entity','source_contract','contract_version_or_fingerprint','template_or_document','source_root','source_path','observation_channel','source_type','presence','cardinality','requested','dto_field','dto_type','dto_presence','legacy_mapping','lineage_group','v2_zone','v2_target','transformation','time_zone','reducer_or_fallback','unit','currency','precision_scale','rounding','protection_class','protection_policy_id','retention_policy','consumer','impact','decision','owner_role','acceptor','status','due_gate','dependencies','evidence','fixture','expected_evidence','publication_blocked')
$requiredRuleColumns = @('rule_id','domain','title','rule','source','positive_example','counterexample','affected_contract','expected_test','owner_role','acceptor','decision','status','due_gate','publication_blocked','evidence')
$requiredProtectionColumns = @('protection_id','scope','purpose','column_patterns','minimization','at_rest_encryption','key_management','in_transit','masking','allowed_profiles','denied_profiles','retention_policy','retention_status','legal_hold','owner_role','acceptor','due_gate','status','evidence','publication_blocked')

foreach ($column in $requiredArtifactColumns) { Assert-True ($artifacts[0].PSObject.Properties.Name -contains $column) "Coluna ausente no inventário: $column." }
foreach ($column in $requiredFieldColumns) { Assert-True ($fields[0].PSObject.Properties.Name -contains $column) "Coluna ausente na matriz de campos: $column." }
foreach ($column in $requiredRuleColumns) { Assert-True ($rules[0].PSObject.Properties.Name -contains $column) "Coluna ausente no catálogo de regras: $column." }
foreach ($column in $requiredProtectionColumns) { Assert-True ($protection[0].PSObject.Properties.Name -contains $column) "Coluna ausente na matriz de proteção: $column." }

$allText = foreach ($file in $catalogFiles) { Get-Content -LiteralPath (Join-Path $catalogRoot $file) -Raw }
Assert-True (-not (($allText -join "`n") -match '(?i)(etl-dashboard|dashboard-powerbi|dashboards[\\/])')) 'O catálogo referencia uma árvore de dashboard proibida.'
Assert-True (@($artifacts | Where-Object { $_.decision -match '(?i)^UNCLASSIFIED$' -or $_.status -match '(?i)^UNCLASSIFIED$' }).Count -eq 0) 'Há artefato não classificado.'
Assert-True (@($fields | Where-Object { $_.decision -match '(?i)^UNCLASSIFIED$' -or $_.status -match '(?i)^UNCLASSIFIED$' }).Count -eq 0) 'Há campo não classificado.'
Assert-True (@($rules | Where-Object { $_.decision -match '(?i)^UNCLASSIFIED$' -or $_.status -match '(?i)^UNCLASSIFIED$' }).Count -eq 0) 'Há regra não classificada.'
Assert-True (@($protection | Where-Object { $_.status -match '(?i)^UNCLASSIFIED$' }).Count -eq 0) 'Há classe de proteção não classificada.'

$artifactDecisions = @('PRESERVE','SUBSTITUTE','CONSOLIDATE','RETIRE','CONDITIONAL')
$fieldDecisions = @('PRESERVE','PROMOTE','NORMALIZE','SPLIT','DERIVE','ALIAS','RETIRE')
foreach ($row in $artifacts) {
    Assert-True ($artifactDecisions -contains $row.decision) "Decisão inválida no artefato $($row.artifact_id): $($row.decision)."
    foreach ($column in @('artifact_type','artifact_id','source_path','responsibility','v2_destination','owner_role','acceptor','due_gate','status','evidence','publication_blocked')) {
        Assert-True (-not [string]::IsNullOrWhiteSpace($row.$column)) "Campo $column vazio no artefato $($row.artifact_id)."
    }
    if ($row.acceptor -eq 'TIME_NOMINAL_NAO_INFORMADO') { Assert-True ($row.publication_blocked -eq 'YES') "Artefato sem aceitante não bloqueia publicação: $($row.artifact_id)." }
}
foreach ($row in $fields) {
    Assert-True ($fieldDecisions -contains $row.decision) "Decisão inválida na linha $($row.matrix_id): $($row.decision)."
    foreach ($column in $requiredFieldColumns) {
        Assert-True (-not [string]::IsNullOrWhiteSpace($row.$column)) "Campo $column vazio na matriz $($row.matrix_id)."
    }
    Assert-True ($row.v2_zone -in @('stg','core','ref','crosswalk','mart','pub','ctl','recon')) "Zona V2 inválida em $($row.matrix_id): $($row.v2_zone)."
    Assert-True ($row.v2_target -notmatch '(?i)(^|\.)metadata($|\.)') "metadata é destino implícito em $($row.matrix_id)."
    if ($row.status -match 'UNRESOLVED|PENDING|CONDITIONAL') {
        Assert-True ($row.publication_blocked -eq 'YES') "Linha aberta precisa bloquear publicação: $($row.matrix_id)."
        Assert-True ($row.owner_role -notmatch '^(UNKNOWN|UNASSIGNED)$') "Linha aberta sem owner-papel: $($row.matrix_id)."
        Assert-True ($row.due_gate -match '^V2-') "Linha aberta sem gate V2: $($row.matrix_id)."
    }
    if ($row.acceptor -eq 'TIME_NOMINAL_NAO_INFORMADO') { Assert-True ($row.publication_blocked -eq 'YES') "Campo sem aceitante não bloqueia publicação: $($row.matrix_id)." }
    if (($row.contract_version_or_fingerprint + ' ' + $row.source_type + ' ' + $row.presence + ' ' + $row.cardinality + ' ' + $row.dto_type + ' ' + $row.legacy_mapping + ' ' + $row.v2_target + ' ' + $row.reducer_or_fallback + ' ' + $row.precision_scale) -match '(?i)UNVERSIONED|UNVERIFIED|UNRESOLVED|PENDING|TO_RECONCILE|TO_FINGERPRINT|NOT_CURRENT') {
        Assert-True ($row.publication_blocked -eq 'YES' -and $row.due_gate -match '^V2-') "Dependência semântica aberta sem bloqueio/gate: $($row.matrix_id)."
    }
    if ($row.dto_presence -eq 'YES') {
        Assert-True ($row.dto_field -ne 'NOT_PRESENT_OR_UNRESOLVED' -and $row.dto_type -ne 'NOT_PRESENT_OR_UNRESOLVED') "Campo DTO presente sem nome/tipo em $($row.matrix_id)."
    }
}

Assert-True (@($artifacts | Group-Object artifact_type, artifact_id | Where-Object Count -gt 1).Count -eq 0) 'Há artifact_type/artifact_id duplicado.'
Assert-True (@($fields | Group-Object matrix_id | Where-Object Count -gt 1).Count -eq 0) 'Há matrix_id duplicado.'
Assert-True (@($rules | Group-Object rule_id | Where-Object Count -gt 1).Count -eq 0) 'Há rule_id duplicado.'
Assert-True (@($protection | Group-Object protection_id | Where-Object Count -gt 1).Count -eq 0) 'Há protection_id duplicado.'

# Baseline estrutural do legado.
$artifactExpected = [ordered]@{
    command_flag = 37
    command_default = 1
    entity_source = 11
    table_script = 35
    migration = 58
    index_group = 4
    validation = 24
    procedure = 5
    view_etl = 19
    view_wrapper = 2
    sql_auxiliary = 3
    windows_script = 15
    windows_alias = 2
    database_runner = 1
    seed_responsibility = 8
    embedded_unique_index = 6
}
foreach ($entry in $artifactExpected.GetEnumerator()) {
    Assert-ExactCount -Rows $artifacts -Where { $_.artifact_type -eq $entry.Key } -Expected $entry.Value -Label ('artefatos ' + $entry.Key)
}
Assert-ExactCount -Rows $artifacts -Where { $_.artifact_type -eq 'constraint' } -Expected 170 -Label 'constraints semanticamente distintas'
Assert-True (@($artifacts | Where-Object { $_.artifact_type -eq 'constraint' -and $_.artifact_id -match ':ANONYMOUS_' }).Count -eq 1) 'Constraints anônimas duplicadas ou ausentes.'
Assert-True (@($artifacts | Where-Object { $_.artifact_type -eq 'constraint' -and $_.artifact_id -eq '016_alter_tabela_dim_usuarios_estado:DF_dim_usuarios_ativo' }).Count -eq 1) 'Constraint DF_dim_usuarios_ativo ausente.'
Assert-True (@($artifacts | Where-Object { $_.artifact_type -eq 'constraint' -and $_.artifact_id -match '^0(13|18)_' -and $_.decision -ne 'RETIRE' }).Count -eq 0) 'Constraint de tabela retirada possui decisão diferente de RETIRE.'
$retiredConstraints = @($artifacts | Where-Object { $_.artifact_type -eq 'constraint' -and $_.artifact_id -match '^0(13|18)_' })
Assert-True (@($retiredConstraints | Where-Object { $_.v2_destination -notmatch '^nenhum equivalente físico' -or $_.notes -notmatch 'retirar junto' }).Count -eq 0) 'Texto de destino/notas contradiz decisão RETIRE de constraint.'
Assert-True (@($artifacts | Where-Object { $_.artifact_type -eq 'index_group' -and $_.responsibility -eq '39 índices legados' }).Count -eq 1) 'Grupo de 39 índices ausente.'
Assert-True (@($artifacts | Where-Object { $_.artifact_type -eq 'index_group' -and $_.responsibility -eq '5 índices legados' }).Count -eq 1) 'Grupo de 5 índices ausente.'
Assert-True (@($artifacts | Where-Object { $_.artifact_type -eq 'index_group' -and $_.responsibility -eq '2 índices legados' }).Count -eq 1) 'Grupo de 2 índices ausente.'
Assert-True (@($artifacts | Where-Object { $_.artifact_type -eq 'index_group' -and $_.responsibility -eq '1 índices legados' }).Count -eq 1) 'Grupo de 1 índice ausente.'
$embeddedUniqueNames = @('ux_page_audit_run_template_page','UX_fato_gv_fretes_indicador_minuta','UX_fato_gv_coletores_data_filial_classif','UX_fato_ff_frete_data','UX_fato_gvf_unique_id_data','UX_regras_atribuicao_filial_pagador_ativo')
foreach ($name in $embeddedUniqueNames) {
    Assert-True (@($artifacts | Where-Object { $_.artifact_type -eq 'embedded_unique_index' -and $_.artifact_id -like ('*:' + $name) }).Count -eq 1) "Índice UNIQUE embutido ausente: $name."
}
Assert-True (@($artifacts | Where-Object { $_.artifact_type -eq 'command_flag' -and $_.notes -match 'OMITIDA_DO_HELP' }).Count -eq 8) 'Gap de oito flags omitidas do help não foi preservado.'
$externalReferenceSeeds = @(
    'destination_region_alias', 'branch_attribution', 'owned_fleet_documents',
    'logistics_regions', 'tariffs_and_financial_rules',
    'mutable_external_reference_baseline'
)
Assert-True (@($artifacts | Where-Object {
            $_.artifact_type -eq 'seed_responsibility' `
                -and $_.artifact_id -in $externalReferenceSeeds `
                -and $_.status -eq 'OFFLINE_SCHEMA_READY_EXTERNAL_INPUT_REQUIRED'
        }).Count -eq 6) `
    'Baselines externos mutáveis não preservam estrutura local + bloqueio externo.'
Assert-True (@($artifacts | Where-Object {
            $_.artifact_type -eq 'seed_responsibility' `
                -and $_.artifact_id -in @('calendar','pick_status_catalog') `
                -and $_.status -eq 'IMPLEMENTED_OFFLINE_CANDIDATE'
        }).Count -eq 2) `
    'Seeds determinísticos não estão registrados como candidatos offline inertes.'
$usuariosArtifact = @($artifacts | Where-Object { $_.artifact_type -eq 'entity_source' -and $_.artifact_id -eq 'usuarios' })
Assert-True ($usuariosArtifact.Count -eq 1 `
        -and $usuariosArtifact[0].v2_destination -eq `
            'core.usuario + core.usuario_history + core.v_usuario_dimension_current_v1' `
        -and $usuariosArtifact[0].status -eq 'IMPLEMENTED_IN_SHADOW' `
        -and $usuariosArtifact[0].publication_blocked -eq 'YES') `
    'A entidade Usuários não reflete current/history + dimensão implementados em sombra.'
$usuariosTableArtifacts = @($artifacts | Where-Object { $_.artifact_type -eq 'table_script' -and $_.artifact_id -match '^0(11|16|17)_' })
Assert-True ($usuariosTableArtifacts.Count -eq 3 `
        -and @($usuariosTableArtifacts | Where-Object { $_.v2_destination -match '^ref\.' -or $_.status -ne 'IMPLEMENTED_IN_SHADOW' }).Count -eq 0) `
    'Responsabilidades físicas legadas de Usuários ainda apontam para ref ou não estão fechadas em sombra.'
$usuariosFields = @($fields | Where-Object { $_.row_kind -eq 'V1_USER_COLUMN' })
Assert-True ($usuariosFields.Count -eq 18 `
        -and @($usuariosFields | Where-Object {
                $_.consumer -notmatch 'core\.v_usuario_dimension_current_v1 implemented in shadow' `
                    -or $_.consumer -notmatch 'pub\.vw_dim_usuarios pending V2-037' `
                    -or $_.status -ne 'IMPLEMENTED_IN_SHADOW' `
                    -or $_.publication_blocked -ne 'YES'
            }).Count -eq 0) `
    'As 18 responsabilidades de Usuários não distinguem dimensão interna e contrato consumidor.'
$usuariosConsumerOutputs = @($fields | Where-Object {
        $_.matrix_id -in @('PUB-0013', 'PUB-0014', 'PUB-0015')
    })
Assert-True ($usuariosConsumerOutputs.Count -eq 3 `
        -and @($usuariosConsumerOutputs | Where-Object {
                $_.entity -ne 'vw_dim_usuarios' `
                    -or $_.status -ne 'CONSUMER_CONTRACT_PENDING' `
                    -or $_.due_gate -ne 'V2-037' `
                    -or $_.publication_blocked -ne 'YES'
            }).Count -eq 0) `
    'Os três outputs legados de Usuários foram antecipados antes de V2-037.'
Assert-True (@($artifacts | Where-Object { $_.artifact_type -eq 'windows_script' -and $_.evidence -match 'classificação estática entre os 15|15 arquivos em scripts/windows' }).Count -eq 0) 'Script Windows possui evidência tautológica.'
$tariffSeed = @($artifacts | Where-Object { $_.artifact_type -eq 'seed_responsibility' -and $_.artifact_id -eq 'tariffs_and_financial_rules' })
Assert-True ($tariffSeed.Count -eq 1 -and ([regex]::Matches($tariffSeed[0].source_path, 'database/procedures/00[1-5]_[^;]+\.sql')).Count -eq 5) 'Fontes concretas das regras financeiras/tarifárias estão incompletas.'
Assert-True (@($artifacts | Where-Object { $_.artifact_type -eq 'migration' -and $_.source_path -match 'historico_arquivado' }).Count -eq 47) 'Baseline de 47 migrations arquivadas divergente.'
Assert-True (@($artifacts | Where-Object { $_.artifact_type -eq 'migration' -and $_.source_path -notmatch 'historico_arquivado' }).Count -eq 11) 'Baseline de 11 migrations ativas divergente.'

# Baseline por campo.
$dataExportExpected = [ordered]@{
    coletas=31; fretes=109; manifestos=91; cotacoes=37; localizacao_cargas=24;
    contas_a_pagar=28; faturas_por_cliente=52; inventario=26; sinistros=44
}
foreach ($entry in $dataExportExpected.GetEnumerator()) {
    Assert-ExactCount -Rows $fields -Where { $_.row_kind -eq 'DATA_EXPORT_FIELD' -and $_.entity -eq $entry.Key } -Expected $entry.Value -Label ('Data Export ' + $entry.Key)
}
Assert-ExactCount -Rows $fields -Where { $_.row_kind -eq 'DATA_EXPORT_FIELD' } -Expected 442 -Label 'campos Data Export'
$dataExportDataExpected = [ordered]@{
    coletas=7; fretes=7; manifestos=90; cotacoes=36; localizacao_cargas=17;
    contas_a_pagar=27; faturas_por_cliente=29; inventario=26; sinistros=31
}
foreach ($entry in $dataExportDataExpected.GetEnumerator()) {
    Assert-ExactCount -Rows $fields -Where { $_.row_kind -eq 'DATA_EXPORT_DATA_FIELD' -and $_.entity -eq $entry.Key } -Expected $entry.Value -Label ('candidatos /data ' + $entry.Key)
}
Assert-ExactCount -Rows $fields -Where { $_.row_kind -eq 'DATA_EXPORT_DATA_FIELD' } -Expected 270 -Label 'paths candidatos /data'
Assert-ExactCount -Rows $fields -Where { $_.row_kind -eq 'V1_OPERATIONAL_COLUMN' } -Expected 459 -Label 'colunas V1 operacionais'
Assert-ExactCount -Rows $fields -Where { $_.row_kind -eq 'V1_USER_COLUMN' } -Expected 18 -Label 'colunas V1 Usuários'
$userPhysicalFields = @($fields | Where-Object row_kind -eq 'V1_USER_COLUMN')
Assert-True (@($userPhysicalFields | Where-Object { $_.v2_zone -eq 'ref' -or $_.v2_target -match '^ref\.' }).Count -eq 0) `
    'A fatia física de Usuários ainda usa o destino ref obsoleto.'
Assert-True (@($userPhysicalFields | Where-Object status -ne 'IMPLEMENTED_IN_SHADOW').Count -eq 0) `
    'As 18 responsabilidades current/history de Usuários não estão fechadas em sombra.'
$userSourceIdentityRows = @($userPhysicalFields | Where-Object source_path -eq 'individual.edges.node.id')
Assert-True ($userSourceIdentityRows.Count -eq 2 `
        -and @($userSourceIdentityRows | Where-Object protection_class -ne 'TECHNICAL_IDENTIFIER').Count -eq 0 `
        -and @($userSourceIdentityRows | Where-Object transformation -notmatch 'type-tagged|type-tag').Count -eq 0) `
    'user_id não preserva identidade técnica type-tagged no current/history.'
Assert-True (@($userPhysicalFields | Where-Object { $_.source_path -match 'updatedAt' -and ($_.status -ne 'IMPLEMENTED_IN_SHADOW' -or $_.decision -ne 'RETIRE' -or $_.reducer_or_fallback -notmatch 'never|no source') }).Count -eq 0) `
    'updatedAt ausente ainda está aberto ou ganhou frescor inventado na fatia de Usuários.'
Assert-ExactCount -Rows $fields -Where { $_.row_kind -eq 'V1_RASTER_COLUMN' } -Expected 58 -Label 'colunas V1 Raster'
Assert-ExactCount -Rows $fields -Where { $_.row_kind -eq 'V1_FACT_COLUMN' } -Expected 336 -Label 'colunas dos cinco fatos'
Assert-ExactCount -Rows $fields -Where { $_.row_kind -eq 'V1_VIEW_OUTPUT' } -Expected 673 -Label 'outputs das 19 views'
Assert-ExactCount -Rows $fields -Where { $_.row_kind -eq 'GRAPHQL_SELECTION' } -Expected 181 -Label 'folhas GraphQL locais'
Assert-ExactCount -Rows $fields -Where { $_.row_kind -eq 'GRAPHQL_SELECTION' -and $_.legacy_mapping -eq 'metadata-only' } -Expected 17 -Label 'campos metadata-only de Coletas'

$viewExpected = [ordered]@{
    vw_faturas_por_cliente_powerbi=42; vw_fretes_powerbi=123; vw_coletas_powerbi=41;
    vw_coletas_excluidas_origem=13; vw_cotacoes_powerbi=54; vw_contas_a_pagar_powerbi=31;
    vw_localizacao_cargas_powerbi=29; vw_manifestos_powerbi=105; vw_fato_manifestos_dash=106;
    vw_bi_monitoramento=9; vw_inventario_powerbi=34; vw_sinistros_powerbi=34;
    vw_raster_sm_transit_time=37; vw_dim_filiais=2; vw_dim_clientes=1; vw_dim_veiculos=4;
    vw_dim_motoristas=2; vw_dim_planocontas=3; vw_dim_usuarios=3
}
foreach ($entry in $viewExpected.GetEnumerator()) {
    Assert-ExactCount -Rows $fields -Where { $_.row_kind -eq 'V1_VIEW_OUTPUT' -and $_.entity -eq $entry.Key } -Expected $entry.Value -Label ('view ' + $entry.Key)
}
Assert-True (@($fields | Where-Object row_kind -eq 'V1_VIEW_OUTPUT' | Select-Object -ExpandProperty entity -Unique).Count -eq 19) 'A matriz não contém exatamente 19 views distintas.'

$factExpected = [ordered]@{
    fato_gestao_vista_fretes=54; fato_gestao_vista_coletores=21; fato_fretes_faturamento=83;
    fato_gestao_vista_faturas=54; fato_gestao_vista_manifestos=124
}
foreach ($entry in $factExpected.GetEnumerator()) {
    Assert-ExactCount -Rows $fields -Where { $_.row_kind -eq 'V1_FACT_COLUMN' -and $_.entity -eq $entry.Key } -Expected $entry.Value -Label ('fato ' + $entry.Key)
}

$graphqlExpected = [ordered]@{
    QUERY_COLETAS=51; QUERY_FRETES=107; QUERY_NFSE=15; QUERY_RESOLVER_CONTA_BANCARIA=4; QUERY_USUARIOS_SISTEMA=4
}
foreach ($entry in $graphqlExpected.GetEnumerator()) {
    Assert-ExactCount -Rows $fields -Where { $_.row_kind -eq 'GRAPHQL_SELECTION' -and $_.template_or_document -eq $entry.Key } -Expected $entry.Value -Label ('GraphQL ' + $entry.Key)
}
$userGraphQlFields = @($fields | Where-Object { $_.row_kind -eq 'GRAPHQL_SELECTION' -and $_.template_or_document -eq 'QUERY_USUARIOS_SISTEMA' })
Assert-True ($userGraphQlFields.Count -eq 4 `
        -and @($userGraphQlFields | Where-Object { $_.status -ne 'IMPLEMENTED_IN_SHADOW' -or $_.v2_zone -notin @('stg','ctl') }).Count -eq 0 `
        -and @($userGraphQlFields | Where-Object v2_target -match '^ref\.').Count -eq 0) `
    'As quatro folhas GraphQL de Usuários não apontam para o protocolo stg/ctl em sombra.'
$userTerminalField = @($userGraphQlFields | Where-Object source_path -match 'hasNextPage$')
Assert-True ($userTerminalField.Count -eq 1 `
        -and $userTerminalField[0].v2_target -eq 'ctl.execution_page_audit.terminal_evidence_kind' `
        -and $userTerminalField[0].reducer_or_fallback -match 'never completeness') `
    'hasNextPage de Usuários não está tipado como terminalidade local sem prova de completude.'
$userCursorField = @($userGraphQlFields | Where-Object source_path -match 'endCursor$')
Assert-True ($userCursorField.Count -eq 1 `
        -and $userCursorField[0].transformation -match 'only inside the live traversal' `
        -and $userCursorField[0].transformation -match 'never cursor value') `
    'endCursor de Usuários não está restrito à travessia em andamento.'

# /info permanece opaco até fingerprint autorizado; /data local é inventário separado.
$dataCandidates = @($fields | Where-Object row_kind -eq 'DATA_EXPORT_DATA_FIELD')
Assert-True (@($dataCandidates | Group-Object template_or_document, source_path | Where-Object Count -gt 1).Count -eq 0) 'Há source path candidato /data duplicado (incluindo qoe_cor_name).'
$firstWaveFreightLink = @($dataCandidates | Where-Object {
        $_.entity -eq 'fretes' -and
        $_.template_or_document -eq '6389' -and
        $_.source_path -eq '/data/fit_p_m_pck_sequence_code'
    })
Assert-True (
    $firstWaveFreightLink.Count -eq 1 -and
    $firstWaveFreightLink[0].status -eq 'OBSERVED_DATA_SAMPLE_PATH' -and
    $firstWaveFreightLink[0].publication_blocked -eq 'YES'
) 'O vínculo candidato 6389 da primeira onda não está preservado com gate.'
$unresolvedSlots = @($fields | Where-Object { $_.row_kind -eq 'DATA_EXPORT_FIELD' -and $_.status -eq 'UNRESOLVED_SOURCE_PATH' })
Assert-True ($unresolvedSlots.Count -eq 442) 'Os 442 slots /info devem permanecer explicitamente opacos até V2-025d.'
foreach ($row in $unresolvedSlots) {
    Assert-True ($row.source_path -match '^/info/__unresolved_info_field_\d{3}$') "Slot Data Export não é ordinal/opaco: $($row.matrix_id)."
    Assert-True ($row.publication_blocked -eq 'YES' -and $row.due_gate -match '^V2-041->V2-025d') "Slot Data Export não expressa a precedência V2-041→V2-025d: $($row.matrix_id)."
}

# P01/V03 fecha somente os paths e reducers locais de Manifestos. Os 91 slots /info
# continuam opacos, e os três filhos físicos não podem transformar status/coocorrência
# em identidade nem antecipar a relação Manifesto→Coleta.
$manifestInfoSlots = @($fields | Where-Object { $_.entity -eq 'manifestos' -and $_.row_kind -eq 'DATA_EXPORT_FIELD' })
Assert-True ($manifestInfoSlots.Count -eq 91 `
        -and @($manifestInfoSlots | Where-Object {
                $_.status -ne 'UNRESOLVED_SOURCE_PATH' `
                    -or $_.reducer_or_fallback -ne 'UNRESOLVED_BY_FIELD' `
                    -or $_.due_gate -notmatch '^V2-041->V2-025d'
            }).Count -eq 0) `
    'Os 91 slots /info de Manifestos foram resolvidos ou receberam reducer sem fingerprint autorizado.'

$manifestData = @($dataCandidates | Where-Object entity -eq 'manifestos')
Assert-True ($manifestData.Count -eq 90 `
        -and @($manifestData | Where-Object {
                $_.status -ne 'LOCAL_STATIC_PATH_AND_REDUCER_DECIDED_P01_V03' `
                    -or $_.due_gate -ne 'V2-026' `
                    -or $_.evidence -notmatch 'identidade-manifestos.+manifestos-v2-026'
            }).Count -eq 0) `
    'Os 90 paths /data de Manifestos não estão ancorados na decisão local P01/V03 com execução em V2-026.'

$manifestDataChildren = @($manifestData | Where-Object decision -eq 'SPLIT')
$expectedManifestDataChildIds = @('DE-DATA-6399-008','DE-DATA-6399-009','DE-DATA-6399-027')
Assert-True ($manifestDataChildren.Count -eq 3 `
        -and ((@($manifestDataChildren.matrix_id | Sort-Object) -join '|') -ceq ($expectedManifestDataChildIds -join '|')) `
        -and @($manifestDataChildren | Where-Object {
                $_.cardinality -ne 'ROOT_ZERO_TO_MANY_PHYSICAL_RECORD_ZERO_OR_ONE_FAIL_CLOSED' `
                    -or $_.v2_target -notmatch '^stg\.manifestos\.(pick|mdfe)_child_observation\.' `
                    -or $_.v2_target -match '(?i)relation|crosswalk'
            }).Count -eq 0) `
    'Manifestos /data deve possuir somente pick, MDF-e number e MDF-e key como filhos 0..N fail-closed.'

$manifestV1 = @($fields | Where-Object { $_.entity -eq 'manifestos' -and $_.row_kind -eq 'V1_OPERATIONAL_COLUMN' })
$manifestV1Children = @($manifestV1 | Where-Object decision -eq 'SPLIT')
$expectedManifestV1ChildIds = @('V1-MANIFESTOS-009','V1-MANIFESTOS-010','V1-MANIFESTOS-029')
Assert-True ($manifestV1Children.Count -eq 3 `
        -and ((@($manifestV1Children.matrix_id | Sort-Object) -join '|') -ceq ($expectedManifestV1ChildIds -join '|')) `
        -and @($manifestV1Children | Where-Object {
                $_.cardinality -ne 'ROOT_ZERO_TO_MANY_LEGACY_PHYSICAL_ROW_ZERO_OR_ONE_FAIL_CLOSED' `
                    -or $_.v2_target -notmatch '^core\.manifestos\.(pick|mdfe)_child_observation\.' `
                    -or $_.v2_zone -ne 'core' `
                    -or $_.v2_target -match '(?i)relation|crosswalk'
            }).Count -eq 0) `
    'A V1 de Manifestos deve mapear somente pick, MDF-e number e MDF-e key para observações filhas, nunca relações.'

$manifestMdfeStatus = @($fields | Where-Object {
        $_.entity -eq 'manifestos' `
            -and $_.source_path -eq '/data/mdfe_status' `
            -and $_.row_kind -in @('DATA_EXPORT_DATA_FIELD','V1_OPERATIONAL_COLUMN')
    })
Assert-True ($manifestMdfeStatus.Count -eq 2 `
        -and @($manifestMdfeStatus | Where-Object {
                $_.decision -ne 'PRESERVE' `
                    -or $_.v2_target -notmatch '^((stg)|(core))\.manifestos\.root_observation\.mdfe_status$' `
                    -or $_.reducer_or_fallback -ne 'ROOT_SCALAR_REPLICATED_BY_EXPANSION_TRI_STATE_UNIQUE_AT_WINNING_FRESHNESS_NOT_CHILD_SIGNAL_V03' `
                    -or $_.transformation -notmatch 'never signal or identify an MDF-e child' `
                    -or $_.cardinality -match 'ZERO_TO_MANY|CHILD'
            }).Count -eq 0) `
    'mdfe_status deve permanecer escalar tri-state da raiz, replicado pela expansão e incapaz de sinalizar filho.'

$manifestMdfeKey = @($fields | Where-Object {
        $_.entity -eq 'manifestos' `
            -and $_.source_path -eq '/data/mft_mfs_key' `
            -and $_.row_kind -in @('DATA_EXPORT_DATA_FIELD','V1_OPERATIONAL_COLUMN')
    })
$manifestMdfeNumber = @($fields | Where-Object {
        $_.entity -eq 'manifestos' `
            -and $_.source_path -eq '/data/mft_mfs_number' `
            -and $_.row_kind -in @('DATA_EXPORT_DATA_FIELD','V1_OPERATIONAL_COLUMN')
    })
Assert-True ($manifestMdfeKey.Count -eq 2 `
        -and @($manifestMdfeKey | Where-Object {
                $_.reducer_or_fallback -ne 'ROOT_SCOPED_MDFE_CHILD_KEY_P01_EXACT_STRING44_V03' `
                    -or $_.transformation -notmatch '44 ASCII digits' `
                    -or $_.transformation -notmatch 'never combine number into identity'
            }).Count -eq 0) `
    'mft_mfs_key não está congelada como única componente natural MDF-e root-scoped e STRING44.'
Assert-True ($manifestMdfeNumber.Count -eq 2 `
        -and @($manifestMdfeNumber | Where-Object {
                $_.reducer_or_fallback -ne 'MDFE_CHILD_ATTRIBUTE_PHYSICALLY_PAIRED_WITH_KEY_NOT_IDENTITY_V03' `
                    -or $_.transformation -notmatch 'paired with key' `
                    -or $_.transformation -notmatch 'never identity'
            }).Count -eq 0) `
    'mft_mfs_number deve ser somente atributo do par físico com a key, nunca identidade ou reducer independente.'

$manifestRootDefaultReducer = 'ROOT_SCALAR_TRI_STATE_UNIQUE_AT_WINNING_FRESHNESS_V03'
Assert-ExactCount -Rows $manifestData -Where { $_.reducer_or_fallback -eq $manifestRootDefaultReducer } -Expected 73 -Label 'reducers default da raiz nos 90 paths /data de Manifestos'
Assert-ExactCount -Rows $manifestV1 -Where { $_.reducer_or_fallback -eq $manifestRootDefaultReducer } -Expected 75 -Label 'reducers default da raiz nas colunas V1 de Manifestos'
Assert-True (@(($manifestData + $manifestV1) | Where-Object reducer_or_fallback -eq 'FIELD_SPECIFIC_PENDING_VERTICAL').Count -eq 0) `
    'Manifestos ainda possui reducer FIELD_SPECIFIC_PENDING_VERTICAL após V03.'
$manifestTechnicalOrRetired = @($manifestV1 | Where-Object { $_.decision -eq 'RETIRE' -or $_.source_path -eq 'DERIVED_BY_PIPELINE' })
Assert-True ($manifestTechnicalOrRetired.Count -eq 7 `
        -and @($manifestTechnicalOrRetired | Where-Object { $_.reducer_or_fallback -eq $manifestRootDefaultReducer }).Count -eq 0) `
    'Coluna V1 técnica, metadata ou identidade legada retirada recebeu reducer de negócio da raiz.'

$manifestCapacityProjections = @($manifestV1 | Where-Object source_path -eq '/data/mft_vie_weight_capacity')
Assert-True ($manifestCapacityProjections.Count -eq 2 `
        -and @($manifestCapacityProjections | Where-Object legacy_mapping -eq 'dbo.manifestos.vehicle_weight_capacity').Count -eq 1 `
        -and @($manifestCapacityProjections | Where-Object {
                $_.legacy_mapping -eq 'dbo.manifestos.capacidade_kg' `
                    -and $_.decision -eq 'DERIVE' `
                    -and $_.reducer_or_fallback -eq 'DERIVE_FROM_REDUCED_MFT_VIE_WEIGHT_CAPACITY_NO_INDEPENDENT_REDUCER_V03'
            }).Count -eq 1) `
    'As duas projeções de capacidade não derivam do mesmo sinal mft_vie_weight_capacity de forma não independente.'

Assert-True (@($fields | Where-Object decision -eq 'SPLIT').Count -gt 0) 'Nenhuma relação/filho recebeu decisão SPLIT.'
Assert-True (@($fields | Where-Object decision -eq 'ALIAS').Count -gt 0) 'Nenhuma business key recebeu decisão ALIAS.'
Assert-True (@($fields | Where-Object decision -eq 'NORMALIZE').Count -gt 0) 'Nenhum temporal/status recebeu decisão NORMALIZE.'
foreach ($row in ($fields | Where-Object { $_.row_kind -eq 'V1_OPERATIONAL_COLUMN' -and $_.source_path -match '^/data/' })) {
    $candidate = @($dataCandidates | Where-Object { $_.entity -eq $row.entity -and $_.source_path -eq $row.source_path })
    Assert-True ($candidate.Count -eq 1) "Correspondência candidata /data ausente para $($row.matrix_id)."
    Assert-True ($candidate[0].lineage_group -eq $row.lineage_group) "Grupo de linhagem divergente para $($row.matrix_id)."
}
$lineageUniquenessScopes = @(
    @('DATA_EXPORT_FIELD','entity'), @('DATA_EXPORT_DATA_FIELD','entity'),
    @('GRAPHQL_SELECTION','entity,template_or_document'), @('V1_FACT_COLUMN','entity'),
    @('V1_VIEW_OUTPUT','entity')
)
foreach ($scope in $lineageUniquenessScopes) {
    $kind = $scope[0]
    $groupProperties = @($scope[1].Split(',') + 'lineage_group')
    $duplicates = @($fields | Where-Object row_kind -eq $kind | Group-Object -Property $groupProperties | Where-Object Count -gt 1)
    Assert-True ($duplicates.Count -eq 0) "Grupo de linhagem provisório colide dentro de $kind."
}
$protectionIds = @($protection | Select-Object -ExpandProperty protection_id)
foreach ($row in $fields) {
    foreach ($policyId in ($row.protection_policy_id -split '/')) {
        Assert-True ($protectionIds -contains $policyId) "Política de proteção inexistente em $($row.matrix_id): $policyId."
    }
}

# Regras canônicas e proteção.
$ruleExpected = [ordered]@{COL=11;FRE=7;MAN=7;COT=2;LOC=7;CAP=5;FAT=7;INV=4;SIN=2;USR=4;RAS=6;MAT=5;PUB=8}
Assert-True ($rules.Count -eq 75) "Catálogo de regras divergente: esperado=75, atual=$($rules.Count)."
foreach ($entry in $ruleExpected.GetEnumerator()) {
    Assert-ExactCount -Rows $rules -Where { $_.domain -eq $entry.Key } -Expected $entry.Value -Label ('regras ' + $entry.Key)
}
foreach ($rule in $rules) {
    Assert-True ($rule.rule_id -match '^[A-Z]{3}-\d{2}$') "ID de regra inválido: $($rule.rule_id)."
    foreach ($column in @('title','rule','source','positive_example','counterexample','affected_contract','expected_test','owner_role','acceptor','decision','status','due_gate','publication_blocked','evidence')) {
        Assert-True (-not [string]::IsNullOrWhiteSpace($rule.$column)) "Campo $column vazio na regra $($rule.rule_id)."
    }
    if ($rule.status -match 'UNRESOLVED|PENDING') {
        Assert-True ($rule.publication_blocked -eq 'YES') "Regra aberta não bloqueia publicação: $($rule.rule_id)."
    }
    if ($rule.acceptor -eq 'TIME_NOMINAL_NAO_INFORMADO') { Assert-True ($rule.publication_blocked -eq 'YES') "Regra sem aceitante não bloqueia publicação: $($rule.rule_id)." }
    Assert-True ($rule.positive_example -ne $rule.counterexample) "Exemplo positivo e contraexemplo iguais em $($rule.rule_id)."
    Assert-True (($rule.positive_example + ' ' + $rule.counterexample) -notmatch '(?i)fixture .*obrigat|exemplo pendente|a definir') "Placeholder de caso sintético em $($rule.rule_id)."
    Assert-True ($rule.expected_test -match [regex]::Escape($rule.rule_id.Replace('-','_'))) "Teste-alvo não é específico da regra $($rule.rule_id)."
}
$userRules = @($rules | Where-Object domain -eq 'USR')
Assert-True ($userRules.Count -eq 4 `
        -and @($userRules | Where-Object status -ne 'IMPLEMENTED_IN_SHADOW').Count -eq 0 `
        -and @($userRules | Where-Object publication_blocked -ne 'YES').Count -eq 0) `
    'USR-01..USR-04 não estão implementadas em sombra com publicação bloqueada.'
$frozenManifestRules = @($rules | Where-Object rule_id -in @('MAN-01','MAN-02','MAN-04','MAN-07'))
Assert-True ($frozenManifestRules.Count -eq 4 `
        -and @($frozenManifestRules | Where-Object {
                $_.status -ne 'LOCAL_DECISION_FROZEN_V03_EXECUTION_PENDING' `
                    -or $_.acceptor -ne 'OWNER_EXPLICIT_AUTHORIZATION_2026_09_04' `
                    -or $_.evidence -notmatch 'V03 local fail-closed'
            }).Count -eq 0) `
    'MAN-01/MAN-02/MAN-04/MAN-07 não estão congeladas somente como decisão local V03.'
$manifestRule01 = @($rules | Where-Object rule_id -eq 'MAN-01')
$manifestRule02 = @($rules | Where-Object rule_id -eq 'MAN-02')
Assert-True ($manifestRule01.Count -eq 1 `
        -and $manifestRule01[0].positive_example -match 'mft_pfs_pck_sequence_code.+mft_mfs_key.+mdfe_status permanece escalar da raiz' `
        -and $manifestRule01[0].counterexample -match 'status MDF-e isolado.+não criam identidade, filho ou relação') `
    'O caso sintético MAN-01 não distingue os dois filhos do mdfe_status de raiz.'
Assert-True ($manifestRule02.Count -eq 1 `
        -and $manifestRule02[0].positive_example -match 'presença tri-state' `
        -and $manifestRule02[0].counterexample -match 'mdfe_status sem chave não decide nem cria filho') `
    'O caso sintético MAN-02 não prova presença/reducer separado nem refuta status como sinal de filho.'
Assert-True ($protection.Count -ge 16) 'Matriz de proteção insuficiente.'
foreach ($row in $protection) {
    foreach ($column in $requiredProtectionColumns) {
        Assert-True (-not [string]::IsNullOrWhiteSpace($row.$column)) "Campo $column vazio em $($row.protection_id)."
    }
    if ($row.status -match 'PENDING') {
        Assert-True ($row.publication_blocked -eq 'YES') "Política aberta não bloqueia publicação: $($row.protection_id)."
    }
    if ($row.acceptor -eq 'TIME_NOMINAL_NAO_INFORMADO') { Assert-True ($row.publication_blocked -eq 'YES') "Política sem aceitante não bloqueia publicação: $($row.protection_id)." }
}
Assert-True (@($protection | Where-Object retention_policy -eq 'CANDIDATE_7_DAYS').Count -eq 1) 'Candidato de staging bem-sucedido por 7 dias ausente/duplicado.'
Assert-True (@($protection | Where-Object retention_policy -eq 'CANDIDATE_30_DAYS').Count -eq 1) 'Candidato de staging falho por 30 dias ausente/duplicado.'
Assert-True (@($fields | Where-Object {
            $_.retention_policy -eq 'R-STG-CANDIDATE' -and $_.v2_zone -ne 'stg'
        }).Count -eq 0) 'R-STG-CANDIDATE foi atribuído fora da zona stg.'
$failedStagingPolicy = @($protection | Where-Object protection_id -eq 'PROT-12')
Assert-True ($failedStagingPolicy.Count -eq 1 `
        -and $failedStagingPolicy[0].column_patterns -eq 'stg.* rejected' `
        -and $failedStagingPolicy[0].scope -eq 'staging falho') `
    'PROT-12 deve limitar o candidato de 30 dias ao staging falho.'
$durableEvidencePolicy = @($protection | Where-Object protection_id -eq 'PROT-16')
Assert-True ($durableEvidencePolicy.Count -eq 1 `
        -and $durableEvidencePolicy[0].column_patterns -match 'recon\.\*') `
    'PROT-16 deve preservar quarantine/recon/evidência no lifecycle append-only.'
Assert-True (@($protection | Where-Object scope -match 'payload/metadata').Count -eq 1) 'Política explícita de payload/metadata ausente.'

Assert-True ([int]$manifest.baselines.data_export_info_slots -eq 442) 'Manifesto divergente para slots /info Data Export.'
Assert-True ([int]$manifest.baselines.data_export_data_candidates -eq 270) 'Manifesto divergente para candidatos /data.'
Assert-True ([int]$manifest.baselines.v1_operational_columns -eq 459) 'Manifesto divergente para colunas V1.'
Assert-True ([int]$manifest.baselines.v1_user_columns -eq 18) 'Manifesto divergente para Usuários.'
Assert-True ([int]$manifest.baselines.v1_raster_columns -eq 58) 'Manifesto divergente para Raster.'
Assert-True ([int]$manifest.baselines.physical_columns_without_raster -eq 477) 'Manifesto divergente para 477 colunas sem Raster.'
Assert-True ([int]$manifest.baselines.physical_columns_with_raster -eq 535) 'Manifesto divergente para 535 colunas com Raster.'
Assert-True ([int]$manifest.baselines.distinct_constraints -eq 170) 'Manifesto divergente para constraints distintas.'
Assert-True ([int]$manifest.baselines.dedicated_index_declarations -eq 47) 'Manifesto divergente para índices dedicados.'
Assert-True ([int]$manifest.baselines.embedded_unique_indexes -eq 6) 'Manifesto divergente para índices UNIQUE embutidos.'
Assert-True ($manifest.catalog_version -ceq '2026-09-04.v2-026a-manifestos-v03' `
        -and $manifest.vertical_slices.usuarios -match `
            '^IMPLEMENTED_IN_SHADOW; current/history \+ core dimension view' `
        -and $manifest.vertical_slices.governed_references -match `
            '^OFFLINE_FOUNDATION_COMPLETE' `
        -and $manifest.vertical_slices.manifestos_decision -match `
            '^LOCAL_DECISION_FROZEN_V03; P01 identity and MAN-01/MAN-02/MAN-04/MAN-07 only') `
    'Manifesto não registra P01/V03 sem perder Usuários e a fundação local V2-035a.'
Assert-True ($fields.Count -eq 2437) "Total da matriz de campos divergente: esperado=2437, atual=$($fields.Count)."

Write-Host ("PASS: catálogo V2-017 validado: {0} artefatos, {1} campos, {2} regras, {3} classes de proteção; zero UNCLASSIFIED." -f $artifacts.Count, $fields.Count, $rules.Count, $protection.Count)
