#Requires -Version 7.0

[CmdletBinding()]
param(
    [Parameter(Mandatory)]
    [ValidateSet('6399', '8656', '10633', '8636', '4924', '6392')]
    [string]$TemplateId
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

if ($TemplateId -ceq '6399') {
    & (Join-Path $PSScriptRoot 'Test-DataExport6399IdentityCatalog.ps1')
    return
}

$utf8 = [System.Text.UTF8Encoding]::new($false, $true)
$repositoryRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..'))
$legacyRoot = [System.IO.Path]::GetFullPath((Join-Path $repositoryRoot '..\etl-extracao-dados'))
$repositoryPrefix = $repositoryRoot.TrimEnd([System.IO.Path]::DirectorySeparatorChar) + [System.IO.Path]::DirectorySeparatorChar
$legacyPrefix = $legacyRoot.TrimEnd([System.IO.Path]::DirectorySeparatorChar) + [System.IO.Path]::DirectorySeparatorChar
$allowedPrefixes = @($repositoryPrefix, $legacyPrefix)
$maximumManifestBytes = 256KB
$maximumFixtureBytes = 128KB
$maximumEvidenceBytes = 4MB

$defaultFixtureRoles = @('METADATA_SELECTED_SUBSET', 'PER_2_PAGE_1', 'PER_2_PAGE_2', 'PER_2_TERMINAL', 'PER_3_PAGE_1', 'PER_3_TERMINAL')
$sinistroFixtureRoles = @('METADATA_SELECTED_SUBSET', 'PER_1_PAGE_1', 'PER_1_PAGE_2', 'PER_1_TERMINAL', 'PER_3_PAGE_1', 'PER_3_TERMINAL')

$configs = @{
    '8656' = [pscustomobject]@{
        Directory = 'identidade-localizacao-cargas'; IdentityVersion = 'localizacao-cargas-identity-v1'
        Task = 'V2-009b/8656'; Route = 'P07'; Entity = 'localizacao_cargas'
        DecisionStatus = 'COMPLETE_LOCAL_BOUNDED'; Close = $true
        ContractFingerprint = 'da95fc17fc3db2fae7635c5456166d72af844b1d826e84ab6c2ba8b6f1b65c24'
        RootAspect = 'BUSINESS_DECISION_PENDING'; ChildAspect = 'ABSENT'
        SourceKeyPath = '/corporation_sequence_number'; SourceKeyName = 'corporation_sequence_number'
        SourceKeyRole = 'SCOPED_SOURCE_KEY_NEVER_CANONICAL_ID'
        SourceKeyDecision = 'ACCEPTED_FOR_SCOPED_LOGICAL_ROOT_ONLY'
        SourceKeyDecisionBasis = 'BOUNDED_PARTIAL_HISTORICAL_AND_LEGACY_STATIC_EVIDENCE_NOT_CURRENT_PROVIDER_GUARANTEE'
        SourceKeyWireTypes = @('INTEGER'); CodecWireTypes = @('INTEGER')
        CanonicalBinding = 'ACCEPTED_FOR_SCOPED_LOGICAL_ROOT'
        CanonicalNotEquivalent = @('corporation_sequence_number', 'sequence_number', 'page_order', 'record_state_id')
        BusinessCandidates = @(); RejectedAliases = @('sequence_number')
        RekeyPolicy = 'NO_REKEY_OR_REPOINT_WITHOUT_VERSIONED_EVIDENCE'
        PhysicalRoot = 'ONE_LOGICAL_LOCATION_RECORD_PER_SCOPED_CORPORATION_SEQUENCE_NUMBER'; Children = @()
        RelationPolicy = 'NO_CHILD_ARRAY_EXPANSION_OR_FREIGHT_RELATION_OBSERVED_OR_INFERRED'
        ProofUniqueness = 'LIMITED_LEGACY_PK_DEDUPE_AND_ONE_SANITIZED_WINDOW_ONLY_NOT_GLOBAL'
        ProofCardinality = 'LIMITED_ONE_TO_ONE_OBSERVED_WINDOW_ONLY'
        Replay = 'SAME_LOGICAL_ROOT_NEVER_A_SECOND_CANONICAL_ROOT'
        PhysicalVariants = 'SCALAR_CHANGE_IS_A_NEW_OBSERVATION_NOT_REKEY'
        DivergentRoot = 'QUARANTINE_AND_BLOCK_PROMOTION_PENDING_V2_028_FRESHNESS_RESOLUTION'
        Reactivation = 'REUSE_EXISTING_CANONICAL_AFTER_VALID_SCOPED_KEY_REAPPEARS'
        Blockers = @(); UnblockEvidence = @()
        ShadowCapability = 'IDENTITY_INPUT_ONLY_REQUIRES_V2_028_AND_V2_009D'
        NextGates = @('V2-028', 'V2-009d', 'V2-025d')
        FixtureRoles = $defaultFixtureRoles; FixtureRole = 'PER_3_PAGE_1'
        FixtureKey = 'corporation_sequence_number'; FixtureWireType = 'INTEGER'; FixtureRows = 3; FixtureDistinctKeys = 3
        RequiredFixtureFields = @(); ForbiddenFixtureFields = @('id', 'sequence_number')
        Anchors = @(
            [pscustomobject]@{ Path = 'STATES.md'; Sha256 = $null; Required = @('LOC-01'); Forbidden = @() },
            [pscustomobject]@{ Path = '../etl-extracao-dados/src/main/java/br/com/extrator/dominio/dataexport/localizacaocarga/LocalizacaoCargaDTO.java'; Sha256 = '09f27231926d7a683190ca669c2c107087b7999ba210ede23207ef560d797462'; Required = @('@JsonProperty\("corporation_sequence_number"\)', '@JsonAlias\("sequence_number"\)'); Forbidden = @() },
            [pscustomobject]@{ Path = '../etl-extracao-dados/src/main/java/br/com/extrator/integracao/mapeamento/dataexport/localizacaocarga/LocalizacaoCargaMapper.java'; Sha256 = 'd33eaf9914ad1109d675a65403efc84c859f030b8f08168d1c8cb608d47d51d3'; Required = @(); Forbidden = @() },
            [pscustomobject]@{ Path = '../etl-extracao-dados/src/main/java/br/com/extrator/integracao/dataexport/support/Deduplicator.java'; Sha256 = '0dd16afbb48fd37722b97c12c7f25aed3729b3c50f54984638b07a405179006e'; Required = @(); Forbidden = @() },
            [pscustomobject]@{ Path = '../etl-extracao-dados/src/main/java/br/com/extrator/persistencia/repositorio/LocalizacaoCargaRepository.java'; Sha256 = '4787a46915be1e0773bbd2daee379487820890638c4b0ab702a5227e667e4fc1'; Required = @(); Forbidden = @() },
            [pscustomobject]@{ Path = '../etl-extracao-dados/docs/legado/pipelines-antigos/02-apis/dataexport/localizacao-carga.md'; Sha256 = 'e0397613311da99563b947e3ea6410c453e824424fda8c538a74e6cb1f7bbbcc'; Required = @('corporation_sequence_number'); Forbidden = @() }
            [pscustomobject]@{ Path = '../etl-extracao-dados/database/tabelas/005_criar_tabela_localizacao_cargas.sql'; Sha256 = '4ea5b15f66db62446ddd8f90bf267b28d62c93b093a9ceeea0a3eadadc319933'; Required = @(); Forbidden = @() }
        )
        ReadmeSha256 = 'aedc192f6913d3fb2b4c5d74e1963b2c3718d6d579bcb1d4b397facd7b27c530'
        ExpectedIdentityFingerprint = '14af11dce5fd7696238907b72f8f6c8c77f86a4cff3045b489da5eb132c0d6f0'
    }
    '10633' = [pscustomobject]@{
        Directory = 'identidade-inventario'; IdentityVersion = 'inventario-identity-v1'
        Task = 'V2-009b/10633'; Route = 'P08'; Entity = 'inventario'
        DecisionStatus = 'UNRESOLVED_ROOT_FREIGHT_AND_INVOICE_MAPPING_IDENTITY'; Close = $false
        ContractFingerprint = '7eeb2844708df34bd1a7877faa4aa0ba2a2c9d13b623d5da093890af89726127'
        RootAspect = 'BUSINESS_DECISION_PENDING'; ChildAspect = 'BUSINESS_DECISION_PENDING'
        SourceKeyPath = '/sequence_code'; SourceKeyName = 'sequence_code'
        SourceKeyRole = 'ROOT_SOURCE_KEY_CANDIDATE_NEVER_CANONICAL_ID'
        SourceKeyDecision = 'UNRESOLVED_LOGICAL_ROOT_CANDIDATE'
        SourceKeyDecisionBasis = 'CONTRACT_AND_INV_01_RETAIN_CANDIDATE_STATUS_LEGACY_USES_COMPOSITE_HASH'
        SourceKeyWireTypes = @('INTEGER'); CodecWireTypes = @('INTEGER')
        CanonicalBinding = 'WITHHELD_FOR_ROOT_AND_CHILD_IDENTITY'
        CanonicalNotEquivalent = @('sequence_code', 'identificador_unico', 'mapping_hash', 'record_state_id')
        BusinessCandidates = @('{"names":["cnr_c_s_fit_corporation_sequence_number"],"paths":["/cnr_c_s_fit_corporation_sequence_number"],"decision":"MINUTA_REFERENCE_CANDIDATE_NOT_ALIAS_IDENTITY_CHILD_OR_CROSSWALK"}')
        RejectedAliases = @('identificador_unico', 'mapping_hash')
        RekeyPolicy = 'NO_REKEY_OR_REPOINT_WITHOUT_VERSIONED_EVIDENCE'
        PhysicalRoot = 'SOURCE_LINE_WITH_SEQUENCE_CODE_AGGREGATE_CANDIDATE_NOT_ACCEPTED'
        Children = @(
            '{"name":"freight_minuta_component","path":"/cnr_c_s_fit_corporation_sequence_number","decision":"UNRESOLVED_SCALAR_COMPONENT_NOT_APPROVED_AS_CHILD_OR_RELATION"}',
            '{"name":"invoice_mapping_expansion","path":null,"pathCandidate":"/cnr_c_s_fit_invoices_mapping/*","decision":"UNRESOLVED_SHAPE_NATURAL_COMPONENTS_COLLISION_AND_CARDINALITY"}'
        )
        RelationPolicy = 'MAPPING_OR_MINUTA_COOCCURRENCE_DOES_NOT_PROVE_INVOICE_FREIGHT_OR_MINUTA_RELATION'
        ProofUniqueness = 'TWENTY_SEVEN_TO_THREE_WINDOW_ONLY_INSUFFICIENT_TO_ACCEPT_LOGICAL_ROOT_UNIQUENESS'
        ProofCardinality = 'ONLY_TWENTY_SEVEN_TO_THREE_OBSERVED_ROOT_FREIGHT_MINUTA_AND_MAPPING_CARDINALITIES_UNPROVEN'
        Replay = 'NO_CANONICAL_REPLAY_CLASSIFICATION_UNTIL_ROOT_GRAIN_IS_PROVEN'
        PhysicalVariants = 'PRESERVE_AS_OBSERVATIONS_WITHOUT_MAPPING_PARSE_COLLAPSE_OR_PROMOTION'
        DivergentRoot = 'QUARANTINE_AND_BLOCK_PROMOTION_PENDING_V2_031'; Reactivation = 'NOT_CLASSIFIABLE_UNTIL_ROOT_IDENTITY_PROVEN'
        Blockers = @('SEQUENCE_CODE_ROOT_UNIQUENESS_AND_STABILITY_UNPROVEN', 'FREIGHT_MINUTA_CHILD_ROLE_UNPROVEN', 'INVOICES_MAPPING_SHAPE_UNPROVEN', 'CHILD_NATURAL_COMPONENTS_UNPROVEN', 'CHILD_COLLISION_ZERO_UNPROVEN', 'ROOT_CHILD_CARDINALITY_UNPROVEN')
        UnblockEvidence = @('VERSIONED_PROVIDER_ROOT_IDENTITY_OR_APPROVED_REPRESENTATIVE_REPEAT_OBSERVATIONS', 'VERSIONED_PROVIDER_ROLE_FOR_FREIGHT_MINUTA_COMPONENT', 'VERSIONED_PROVIDER_SHAPE_AND_TYPES_FOR_INVOICES_MAPPING', 'REPRESENTATIVE_REPEAT_OBSERVATIONS_PROVING_CHILD_NATURAL_KEYS_COLLISIONS_AND_CARDINALITY')
        ShadowCapability = 'BLOCKED_PENDING_COMPLETE_V2_009B_10633_AND_V2_031'
        NextGates = @('V2-009b/10633', 'V2-031', 'V2-009d', 'V2-025d')
        FixtureRoles = $defaultFixtureRoles; FixtureRole = 'PER_3_PAGE_1'
        FixtureKey = 'sequence_code'; FixtureWireType = 'INTEGER'; FixtureRows = 27; FixtureDistinctKeys = 3
        RequiredFixtureFields = @('cnr_c_s_fit_corporation_sequence_number', 'cnr_c_s_fit_invoices_mapping'); ForbiddenFixtureFields = @('id')
        Anchors = @(
            [pscustomobject]@{ Path = 'STATES.md'; Sha256 = $null; Required = @('INV-01'); Forbidden = @() },
            [pscustomobject]@{ Path = '../etl-extracao-dados/src/main/java/br/com/extrator/dominio/dataexport/inventario/InventarioDTO.java'; Sha256 = '1022874a092b4d841abae11f10ecfb8cf9e05de37927436f2f74273eed5924bd'; Required = @('@JsonProperty\("cnr_c_s_fit_invoices_mapping"\)', 'private Object invoicesMapping;'); Forbidden = @() },
            [pscustomobject]@{ Path = '../etl-extracao-dados/src/main/java/br/com/extrator/integracao/mapeamento/dataexport/inventario/InventarioMapper.java'; Sha256 = 'f8e00739d59250a34aa30d21e99c4b5397b6a02baf6a21610fb96bf25a804f27'; Required = @('String\.valueOf\(dto\.getSequenceCode\(\)\)', 'String\.valueOf\(dto\.getNumeroMinuta\(\)\)', 'entity\.getInvoicesMapping\(\)'); Forbidden = @() },
            [pscustomobject]@{ Path = '../etl-extracao-dados/database/tabelas/020_criar_tabela_inventario.sql'; Sha256 = 'f54e43b40a2703dfb9ebdab21b2084817da44e3ffd8f6ede77ca05dad00d50b7'; Required = @(); Forbidden = @() }
        )
        ReadmeSha256 = 'bbfc848d21841f69d77e17b2d33322bc026f70820348a2ee392e5cef1ecad953'
        ExpectedIdentityFingerprint = '739d8e3930b9db497b9cf3e38365ade6f79666a9a6bd80b313b2fbef88479d68'
    }
    '8636' = [pscustomobject]@{
        Directory = 'identidade-contas-a-pagar'; IdentityVersion = 'contas-a-pagar-identity-v1'
        Task = 'V2-009b/8636'; Route = 'P09'; Entity = 'contas_a_pagar'
        DecisionStatus = 'BLOCKED_ROOT_IDENTITY_ABSENT_PHYSICAL_ROW_KEY_REFUTED_AND_GRAIN_UNRESOLVED'; Close = $false
        ContractFingerprint = '948902142c9d35ca76574de13cfac1064b6b06eefb62ae012ea5823022dec4c1'
        RootAspect = 'ABSENT'; ChildAspect = 'BUSINESS_DECISION_PENDING'
        SourceKeyPath = $null; SourceKeyName = $null
        SourceKeyRole = 'LOGICAL_ACCOUNTING_DEBIT_ROOT'; SourceKeyDecision = 'ABSENT_NO_ROOT_SOURCE_KEY_BINDING_ALLOWED'
        SourceKeyDecisionBasis = $null; SourceKeyWireTypes = @(); CodecWireTypes = @()
        CanonicalBinding = 'WITHHELD_ROOT_SOURCE_KEY_ABSENT'
        CanonicalNotEquivalent = @('ant_ils_sequence_code', 'document', 'legacy_primary_key', 'record_state_id')
        BusinessCandidates = @('{"names":["ant_ils_sequence_code"],"paths":["/ant_ils_sequence_code"],"wireTypes":[],"wireTypeDecision":"UNRESOLVED_PARTIAL_HISTORY_INTEGER_VERSUS_LEGACY_STRING_COERCION","decision":"PARCEL_SOURCE_KEY_CANDIDATE_NOT_ROOT_OR_PHYSICAL_ROW_IDENTITY"}')
        RejectedAliases = @('legacy_primary_key', 'document')
        RekeyPolicy = 'NO_REKEY_OR_REPOINT_WITHOUT_ROOT_AND_VERSIONED_EVIDENCE'
        PhysicalRoot = 'SOURCE_LINE_AT_DATA_ITEM_LOGICAL_ACCOUNTING_DEBIT_ROOT_UNIDENTIFIED'; Children = @()
        RelationPolicy = 'NO_ACCOUNTING_DEBIT_PARCEL_COST_CENTER_OR_LEDGER_RELATION_INFERRED'
        ProofUniqueness = 'REFUTED_FOR_PHYSICAL_ROW_BY_PARTIAL_HISTORICAL_CANDIDATE_REPETITIONS_ROOT_UNAVAILABLE'
        ProofCardinality = 'ROOT_PARCEL_AND_ALLOCATION_CARDINALITIES_UNVERIFIED'
        Replay = 'NOT_APPLICABLE_UNTIL_ROOT_SOURCE_KEY_EXISTS'
        PhysicalVariants = 'PRESERVE_ALL_OBSERVATIONS_NO_KEEP_LATEST_OR_PARCEL_COLLAPSE'
        DivergentRoot = 'BLOCK_PROMOTION_NO_ROOT_IDENTITY'; Reactivation = 'NOT_CLASSIFIABLE_UNTIL_ROOT_IDENTITY_PROVEN'
        Blockers = @('ACCOUNTING_DEBIT_ROOT_SOURCE_KEY_ABSENT', 'PARCEL_CANDIDATE_NOT_UNIQUE_PER_PHYSICAL_ROW', 'ROOT_PARCEL_RELATION_UNPROVEN', 'ROOT_PARCEL_ALLOCATION_CARDINALITY_UNPROVEN')
        UnblockEvidence = @('VERSIONED_PROVIDER_ROOT_IDENTIFIER_AND_TYPES', 'REPRESENTATIVE_REPEAT_OBSERVATIONS_PROVING_ROOT_PARCEL_ALLOCATION_GRAIN_AND_CARDINALITY')
        ShadowCapability = 'BLOCKED_PENDING_COMPLETE_V2_009B_8636_AND_V2_029'
        NextGates = @('V2-009b/8636', 'V2-029', 'V2-009d', 'V2-025d')
        FixtureRoles = $defaultFixtureRoles; FixtureRole = 'PER_3_PAGE_1'
        FixtureKey = 'ant_ils_sequence_code'; FixtureWireType = 'UNVERIFIED'; FixtureRows = 5; FixtureDistinctKeys = 5
        RequiredFixtureFields = @(); ForbiddenFixtureFields = @('id')
        Anchors = @(
            [pscustomobject]@{ Path = 'STATES.md'; Sha256 = $null; Required = @('CAP-01', 'CAP-04'); Forbidden = @() },
            [pscustomobject]@{ Path = '../etl-extracao-dados/docs/legado/pipelines-antigos/02-apis/dataexport/contasapagar.md'; Sha256 = '40167d4cb5dc5966d1a4c89cd588c2581f99d9f6b2671ee898c5c30f3b85f0be'; Required = @('ant_ils_sequence_code.*Integer', 'Conta Contábil/Valor.*ant_ils_pas_value'); Forbidden = @() },
            [pscustomobject]@{ Path = '../etl-extracao-dados/src/main/java/br/com/extrator/dominio/dataexport/contasapagar/ContasAPagarDTO.java'; Sha256 = '4dc05ff2965531470c2caab2984a79b779b35aa9e1c33832b8152f648e1c973e'; Required = @('@JsonProperty\("ant_ils_sequence_code"\)', 'private String sequenceCode;'); Forbidden = @() },
            [pscustomobject]@{ Path = '../etl-extracao-dados/src/main/java/br/com/extrator/integracao/mapeamento/dataexport/contasapagar/ContasAPagarMapper.java'; Sha256 = '78c1f83d68dd899693b4f28e2633182da49a02409661fafbc662eb08fdea5cab'; Required = @('ValidadorDTO\.validarIdString\(validacao, "sequence_code", dto\.getSequenceCode\(\)\)', 'parseLong\(dto\.getSequenceCode\(\)\)'); Forbidden = @() },
            [pscustomobject]@{ Path = '../etl-extracao-dados/database/tabelas/006_criar_tabela_contas_a_pagar.sql'; Sha256 = '27f3994900e670205574b216a560826ac1be5861ac7b3abba6b32998cf3721ed'; Required = @(); Forbidden = @() }
        )
        ReadmeSha256 = 'e6b0f1f083b3f39ac639c13bc462ed4a0c755661d6ea82759d8a5a3c109bbf0d'
        ExpectedIdentityFingerprint = '906a6392a5c8d527e911a7579bbf4477ddcd32dcbc1826ae8ec666e0a802805d'
    }
    '4924' = [pscustomobject]@{
        Directory = 'identidade-faturas-por-cliente'; IdentityVersion = 'faturas-por-cliente-identity-v1'
        Task = 'V2-009b/4924'; Route = 'P10'; Entity = 'faturas_por_cliente'
        DecisionStatus = 'UNRESOLVED_LOGICAL_TITLE_AND_CROSSWALKS'; Close = $false
        ContractFingerprint = '38cecfe9119012eae6e964d79931839805ad825ef527b2392c314edfd94d1098'
        RootAspect = 'BUSINESS_DECISION_PENDING'; ChildAspect = 'BUSINESS_DECISION_PENDING'
        SourceKeyPath = '/id'; SourceKeyName = 'id'; SourceKeyRole = 'SCOPED_SOURCE_LINE_KEY_NEVER_CANONICAL_ID'
        SourceKeyDecision = 'ACCEPTED_FOR_SCOPED_SOURCE_LINE_ONLY_NOT_LOGICAL_TITLE'
        SourceKeyDecisionBasis = 'BOUNDED_CONTRACT_AND_ONE_SANITIZED_WINDOW_NOT_PROVIDER_STABILITY_OR_LOGICAL_TITLE'
        SourceKeyWireTypes = @('INTEGER'); CodecWireTypes = @('INTEGER')
        CanonicalBinding = 'ACCEPTED_FOR_SCOPED_SOURCE_LINE_LOGICAL_TITLE_BINDING_WITHHELD'
        CanonicalNotEquivalent = @('id', 'unique_id', 'FPC-HASH', 'fit_ant_document', 'record_state_id')
        BusinessCandidates = @(
            '{"names":["fit_ant_document"],"paths":["/fit_ant_document"],"decision":"TITLE_CANDIDATE_NOT_LINE_KEY_OR_ALIAS"}',
            '{"names":["fit_nse_number","nfse_number"],"paths":["/fit_nse_number","/nfse_number"],"decision":"FISCAL_REFERENCE_CANDIDATE_NOT_ALIAS"}',
            '{"names":["fit_fhe_cte_number","fit_fhe_cte_key"],"paths":["/fit_fhe_cte_number","/fit_fhe_cte_key"],"decision":"FISCAL_REFERENCE_CANDIDATE_NOT_ALIAS"}',
            '{"names":["billingId"],"paths":[],"decision":"LEGACY_HEURISTIC_INPUT_NOT_PROVEN_SOURCE_FIELD_OR_ALIAS"}'
        )
        RejectedAliases = @('unique_id', 'FPC-HASH', 'legacy_nfse_alias', 'legacy_cte_alias', 'legacy_billing_alias')
        RekeyPolicy = 'DOCUMENT_OR_TITLE_CHANGE_NEVER_REKEYS_WITHOUT_VERSIONED_CROSSWALK_EVIDENCE'
        PhysicalRoot = 'SOURCE_LINE_ONLY_NOT_LOGICAL_FREIGHT_OR_TITLE'
        Children = @(
            '{"name":"invoices_mapping_elements","path":"/invoices_mapping/*","decision":"UNRESOLVED_ELEMENT_IDENTITY_SEMANTICS_AND_CARDINALITY"}',
            '{"name":"invoice_order_number_elements","path":"/fit_fte_invoices_order_number/*","decision":"UNRESOLVED_ELEMENT_IDENTITY_SEMANTICS_AND_CARDINALITY"}'
        )
        RelationPolicy = 'SCALAR_COOCCURRENCE_AND_ARRAY_MEMBERSHIP_DO_NOT_PROVE_LINE_TITLE_DOCUMENT_OR_FREIGHT_RELATION'
        ProofUniqueness = 'SOURCE_LINE_LIMITED_THREE_OF_THREE_IN_ONE_SANITIZED_WINDOW_TITLE_REFUTED_AS_LINE_KEY_BY_PARTIAL_HISTORY'
        ProofCardinality = 'PARTIAL_HISTORY_PROVES_MULTIPLICITY_EXISTS_NOT_ITS_UNIVERSAL_CARDINALITY'
        Replay = 'SAME_SCOPED_SOURCE_LINE_REUSES_CANONICAL_LINE_ID_LOGICAL_TITLE_BINDING_WITHHELD'
        PhysicalVariants = 'EXACT_REPLAY_MAY_NO_OP_DIVERGENCE_BLOCKS_PENDING_V2_030_FRESHNESS'
        DivergentRoot = 'QUARANTINE_AND_BLOCK_LOGICAL_TITLE_PROMOTION'
        Reactivation = 'REUSE_CANONICAL_SOURCE_LINE_ONLY_LOGICAL_TITLE_BINDING_REMAINS_WITHHELD'
        Blockers = @('LOGICAL_TITLE_IDENTITY_UNPROVEN', 'LINE_TITLE_DOCUMENT_FREIGHT_CROSSWALKS_UNPROVEN', 'FISCAL_ALIAS_AND_REKEY_PRECEDENCE_CONFLICTING', 'CHILD_IDENTITY_AND_CARDINALITY_UNPROVEN')
        UnblockEvidence = @('VERSIONED_PROVIDER_STABILITY_FOR_SOURCE_LINE_ID', 'REPRESENTATIVE_CROSSWALK_EVIDENCE_FOR_LINE_TITLE_DOCUMENT_FREIGHT_AND_CHILDREN', 'OWNER_ACCEPTED_FISCAL_ALIAS_REKEY_AND_CARDINALITY_RULES')
        ShadowCapability = 'BLOCKED_PENDING_COMPLETE_V2_009B_4924_AND_V2_030'
        NextGates = @('V2-009b/4924', 'V2-030', 'V2-009d', 'V2-025d')
        FixtureRoles = $defaultFixtureRoles; FixtureRole = 'PER_3_PAGE_1'
        FixtureKey = 'id'; FixtureWireType = 'INTEGER'; FixtureRows = 3; FixtureDistinctKeys = 3
        RequiredFixtureFields = @(); ForbiddenFixtureFields = @('unique_id')
        Anchors = @(
            [pscustomobject]@{ Path = 'STATES.md'; Sha256 = $null; Required = @('FAT-01'); Forbidden = @() },
            [pscustomobject]@{ Path = '../etl-extracao-dados/docs/legado/pipelines-antigos/02-apis/dataexport/faturaporcliente.md'; Sha256 = '8a6efa0957d7f079a99a05bf8e01b5b43fc4a764a64081e56775abc5eae4783e'; Required = @('fit_ant_document', 'fit_fhe_cte_number'); Forbidden = @() },
            [pscustomobject]@{ Path = '../etl-extracao-dados/src/main/java/br/com/extrator/dominio/dataexport/faturaporcliente/FaturaPorClienteDTO.java'; Sha256 = 'aebda065e61f2414afd2b8ae39a7312997b6191aee39c801ebab7a769c4eef42'; Required = @('@JsonProperty\("invoices_mapping"\)', '@JsonProperty\("fit_fte_invoices_order_number"\)'); Forbidden = @('@JsonProperty\("id"\)') },
            [pscustomobject]@{ Path = '../etl-extracao-dados/src/main/java/br/com/extrator/integracao/mapeamento/dataexport/faturaporcliente/FaturaPorClienteMapper.java'; Sha256 = '4be5b03f14b1e32478679f1e7aed2bea8affec5ac392b0a815e1a96669d52aa0'; Required = @('calcularAliasNfse', 'calcularAliasCte', 'calcularAliasBilling', 'HASH_PREFIX'); Forbidden = @() },
            [pscustomobject]@{ Path = '../etl-extracao-dados/src/main/java/br/com/extrator/persistencia/repositorio/FaturaPorClienteRepository.java'; Sha256 = 'b249e19c52367814f593910b077130e8225d38c885c5db2deb19cb106575ecb6'; Required = @('alias'); Forbidden = @() }
        )
        ReadmeSha256 = '0af6a7ec95e7b76f7a1fc0c166befe5dd373f23f0a176627b88eff3890808af7'
        ExpectedIdentityFingerprint = '0403c2752b14d872ecbc3a4b9c7aaafc7fe3731532e15f30b8ea8f003c78d557'
    }
    '6392' = [pscustomobject]@{
        Directory = 'identidade-sinistros'; IdentityVersion = 'sinistros-identity-v1'
        Task = 'V2-009b/6392'; Route = 'P11'; Entity = 'sinistros'
        DecisionStatus = 'UNRESOLVED_ROOT_SOURCE_KEY_VERSUS_LEGACY_COMPOSITE_GRAIN'; Close = $false
        ContractFingerprint = '6f837cabc31d50fd7677c07d11bcb45a3589373ab383ab2aebbb45695a9ec147'
        RootAspect = 'BUSINESS_DECISION_PENDING'; ChildAspect = 'BUSINESS_DECISION_PENDING'
        SourceKeyPath = '/sequence_code'; SourceKeyName = 'sequence_code'
        SourceKeyRole = 'ROOT_SOURCE_KEY_CANDIDATE_NEVER_CANONICAL_ID'
        SourceKeyDecision = 'UNRESOLVED_AGAINST_LEGACY_COMPOSITE_PHYSICAL_GRAIN'; SourceKeyDecisionBasis = $null
        SourceKeyWireTypes = @('INTEGER'); CodecWireTypes = @('INTEGER')
        CanonicalBinding = 'WITHHELD_UNTIL_ROOT_SOURCE_KEY_AND_GRAIN_ARE_PROVEN'
        CanonicalNotEquivalent = @('sequence_code', 'identificador_unico', 'composite_hash', 'record_state_id')
        BusinessCandidates = @(
            '{"names":["icm_fis_fit_corporation_sequence_number"],"paths":["/icm_fis_fit_corporation_sequence_number"],"decision":"MINUTA_RELATION_COMPONENT_CANDIDATE_NOT_ALIAS_OR_CHILD"}',
            '{"names":["icm_fis_ioe_number"],"paths":["/icm_fis_ioe_number"],"decision":"INVOICE_OCCURRENCE_COMPONENT_CANDIDATE_NOT_ALIAS_OR_CHILD"}'
        )
        RejectedAliases = @('identificador_unico', 'composite_hash')
        RekeyPolicy = 'NO_REKEY_OR_REPOINT_WITHOUT_VERSIONED_ROOT_AND_COMPONENT_EVIDENCE'
        PhysicalRoot = 'SOURCE_LINE_WITH_LOGICAL_ROOT_UNRESOLVED_BETWEEN_SEQUENCE_AND_COMPOSITE_COMPONENTS'; Children = @()
        RelationPolicy = 'MINUTA_AND_INVOICE_OCCURRENCE_FIELDS_ARE_SCALARS_NO_CHILD_OR_RELATION_ROLE_IS_INFERRED'
        ProofUniqueness = 'TWO_OF_TWO_WINDOW_ONLY_INSUFFICIENT_TO_RESOLVE_AGAINST_LEGACY_COMPOSITE_GRAIN'
        ProofCardinality = 'ROOT_MINUTA_INVOICE_OCCURRENCE_CARDINALITY_UNVERIFIED'
        Replay = 'NO_CANONICAL_REPLAY_CLASSIFICATION_UNTIL_ROOT_GRAIN_IS_PROVEN'
        PhysicalVariants = 'PRESERVE_ALL_OBSERVATIONS_WITHOUT_COMPOSITE_COLLAPSE'
        DivergentRoot = 'QUARANTINE_AND_BLOCK_PROMOTION_PENDING_V2_032'; Reactivation = 'NOT_CLASSIFIABLE_UNTIL_ROOT_IDENTITY_PROVEN'
        Blockers = @('SEQUENCE_CODE_UNIQUENESS_AND_STABILITY_UNPROVEN', 'LEGACY_COMPOSITE_GRAIN_CONFLICT', 'RELATION_COMPONENT_ROLES_UNPROVEN', 'ROOT_COMPONENT_CARDINALITY_UNPROVEN')
        UnblockEvidence = @('VERSIONED_PROVIDER_ROOT_IDENTITY_OR_APPROVED_REPRESENTATIVE_REPEAT_OBSERVATIONS', 'COLLISION_ANALYSIS_COMPARING_SEQUENCE_CODE_WITH_MINUTA_AND_INVOICE_OCCURRENCE_COMPONENTS', 'VERSIONED_COMPONENT_ROLES_AND_REPRESENTATIVE_ROOT_COMPONENT_CARDINALITY')
        ShadowCapability = 'BLOCKED_PENDING_COMPLETE_V2_009B_6392_AND_V2_032'
        NextGates = @('V2-009b/6392', 'V2-032', 'V2-009d', 'V2-025d')
        FixtureRoles = $sinistroFixtureRoles; FixtureRole = 'PER_3_PAGE_1'
        FixtureKey = 'sequence_code'; FixtureWireType = 'INTEGER'; FixtureRows = 2; FixtureDistinctKeys = 2
        RequiredFixtureFields = @('icm_fis_fit_corporation_sequence_number', 'icm_fis_ioe_number'); ForbiddenFixtureFields = @('id')
        Anchors = @(
            [pscustomobject]@{ Path = 'STATES.md'; Sha256 = $null; Required = @('V2-009b/6392', 'SIN-01'); Forbidden = @() },
            [pscustomobject]@{ Path = 'docs/catalogos/portabilidade/regras-negocio.csv'; Sha256 = $null; Required = @('"SIN-01"'); Forbidden = @() },
            [pscustomobject]@{ Path = '../etl-extracao-dados/src/main/java/br/com/extrator/dominio/dataexport/sinistros/SinistroDTO.java'; Sha256 = 'c4d1be395a2607d4e55ef032d82cd1d1445630ea9eff1fd1240aa0e64ccc0428'; Required = @(); Forbidden = @() },
            [pscustomobject]@{ Path = '../etl-extracao-dados/src/main/java/br/com/extrator/integracao/mapeamento/dataexport/sinistros/SinistroMapper.java'; Sha256 = '63a90e8d8632aa810cd4eaf523c82c11c6a3abc74f1f99f1f16b5c82a6d1456b'; Required = @('String\.valueOf\(dto\.getSequenceCode\(\)\)', 'String\.valueOf\(dto\.getInsuranceOccurrenceNumber\(\)\)', 'String\.valueOf\(dto\.getCorporationSequenceNumber\(\)\)'); Forbidden = @() },
            [pscustomobject]@{ Path = '../etl-extracao-dados/database/tabelas/021_criar_tabela_sinistros.sql'; Sha256 = '5336a8d75bd874ae7ca4e451ac9af1364db45dcf2bde96f0889d5ad15dcb48a2'; Required = @('identificador_unico NVARCHAR\(64\) PRIMARY KEY'); Forbidden = @() }
        )
        ReadmeSha256 = '38eccfa07d65afa43354893061cce83b55544ccc61f516519da5b046f4ae2b14'
        ExpectedIdentityFingerprint = '933a43b7728de14a9c2c4017e9ca6d5a9b8b0bcced8c0033cdde1d347bbb57b7'
    }
}
$config = $configs[$TemplateId]

function Assert-RequiredProperty {
    param([Parameter(Mandatory)][object]$Object, [Parameter(Mandatory)][string]$Name, [Parameter(Mandatory)][string]$Label)
    if ($Object -isnot [pscustomobject] -or @($Object.PSObject.Properties | Where-Object { $_.Name -ceq $Name }).Count -ne 1) { throw "A propriedade '$Name' está ausente em $Label para o Data Export $TemplateId." }
}

function Get-ExactPropertyValue {
    param([Parameter(Mandatory)][object]$Object, [Parameter(Mandatory)][string]$Name, [Parameter(Mandatory)][string]$Label)
    Assert-RequiredProperty -Object $Object -Name $Name -Label $Label
    $property = @($Object.PSObject.Properties | Where-Object { $_.Name -ceq $Name })[0]
    return ,$property.Value
}

function Assert-ArrayProperty {
    param([Parameter(Mandatory)][object]$Object, [Parameter(Mandatory)][string]$Name, [Parameter(Mandatory)][string]$Label)
    $value = Get-ExactPropertyValue -Object $Object -Name $Name -Label $Label
    if ($value -isnot [System.Array]) { throw "A propriedade '$Name' não é array JSON em $Label para o Data Export $TemplateId." }
}

function Assert-BooleanProperty {
    param([Parameter(Mandatory)][object]$Object, [Parameter(Mandatory)][string]$Name, [Parameter(Mandatory)][bool]$Expected, [Parameter(Mandatory)][string]$Label)
    Assert-RequiredProperty -Object $Object -Name $Name -Label $Label
    $value = Get-ExactPropertyValue -Object $Object -Name $Name -Label $Label
    if ($value -isnot [bool] -or $value -ne $Expected) { throw "A propriedade booleana '$Name' diverge em $Label para o Data Export $TemplateId." }
}

function Assert-ExactSet {
    param([AllowEmptyCollection()][object[]]$Actual, [AllowEmptyCollection()][object[]]$Expected, [Parameter(Mandatory)][string]$Label)
    $actualSet = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::Ordinal)
    foreach ($item in @($Actual)) {
        if ($item -isnot [string]) { throw "O conjunto $Label contém item que não é string JSON." }
        if (-not $actualSet.Add($item)) { throw "O conjunto $Label contém duplicata exata." }
    }
    $expectedSet = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::Ordinal)
    foreach ($item in @($Expected)) {
        if ($item -isnot [string]) { throw "A baseline $Label contém item que não é string." }
        if (-not $expectedSet.Add($item)) { throw "A baseline $Label contém duplicata exata." }
    }
    if ($actualSet.Count -ne $expectedSet.Count) { throw "O conjunto $Label diverge para o Data Export $TemplateId." }
    foreach ($item in $expectedSet) { if (-not $actualSet.Contains($item)) { throw "O conjunto $Label diverge para o Data Export $TemplateId." } }
}

function Assert-NoDuplicateJsonElement {
    param([Parameter(Mandatory)][System.Text.Json.JsonElement]$Element, [Parameter(Mandatory)][string]$Path)
    if ($Element.ValueKind -eq [System.Text.Json.JsonValueKind]::Object) {
        $names = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::Ordinal)
        foreach ($property in $Element.EnumerateObject()) {
            if (-not $names.Add($property.Name)) { throw "O JSON contém propriedade duplicada ordinal em $Path." }
            Assert-NoDuplicateJsonElement -Element $property.Value -Path ($Path + '.' + $property.Name)
        }
    } elseif ($Element.ValueKind -eq [System.Text.Json.JsonValueKind]::Array) {
        $index = 0
        foreach ($item in $Element.EnumerateArray()) {
            Assert-NoDuplicateJsonElement -Element $item -Path ($Path + '[' + $index + ']')
            $index++
        }
    }
}

function Assert-NoDuplicateJsonProperties {
    param([Parameter(Mandatory)][string]$Text)
    $document = [System.Text.Json.JsonDocument]::Parse($Text)
    try { Assert-NoDuplicateJsonElement -Element $document.RootElement -Path '$' } finally { $document.Dispose() }
}

function Convert-JsonElementToCanonicalText {
    param([Parameter(Mandatory)][System.Text.Json.JsonElement]$Element, [Parameter(Mandatory)][string]$Path)
    switch ($Element.ValueKind) {
        ([System.Text.Json.JsonValueKind]::Object) {
            $map = [System.Collections.Generic.Dictionary[string, System.Text.Json.JsonElement]]::new([System.StringComparer]::Ordinal)
            foreach ($property in $Element.EnumerateObject()) {
                if ($Path -ceq '$.identity' -and ($property.Name -ceq 'fingerprintInputs' -or $property.Name -ceq 'fingerprints')) { continue }
                if (-not $map.TryAdd($property.Name, $property.Value)) { throw "O JSON contém propriedade duplicada ordinal em $Path." }
            }
            [string[]]$names = @($map.Keys)
            [System.Array]::Sort($names, [System.StringComparer]::Ordinal)
            $parts = foreach ($name in $names) {
                $encodedName = [System.Text.Json.JsonEncodedText]::Encode($name).ToString()
                '"' + $encodedName + '":' + (Convert-JsonElementToCanonicalText -Element $map[$name] -Path ($Path + '.' + $name))
            }
            return '{' + ($parts -join ',') + '}'
        }
        ([System.Text.Json.JsonValueKind]::Array) {
            $parts = foreach ($item in $Element.EnumerateArray()) { Convert-JsonElementToCanonicalText -Element $item -Path ($Path + '[]') }
            return '[' + ($parts -join ',') + ']'
        }
        ([System.Text.Json.JsonValueKind]::String) {
            return '"' + [System.Text.Json.JsonEncodedText]::Encode($Element.GetString()).ToString() + '"'
        }
        ([System.Text.Json.JsonValueKind]::Number) { return $Element.GetRawText() }
        ([System.Text.Json.JsonValueKind]::True) { return 'true' }
        ([System.Text.Json.JsonValueKind]::False) { return 'false' }
        ([System.Text.Json.JsonValueKind]::Null) { return 'null' }
        default { throw "Tipo JSON não suportado no fingerprint em $Path." }
    }
}

function Read-StrictUtf8 {
    param([Parameter(Mandatory)][string]$LiteralPath, [Parameter(Mandatory)][long]$MaximumBytes)
    $item = Get-Item -LiteralPath $LiteralPath -ErrorAction Stop
    if ($item.Length -gt $MaximumBytes) { throw "O artefato '$($item.Name)' excede o limite de bytes." }
    $bytes = [System.IO.File]::ReadAllBytes($item.FullName)
    if ($bytes.Length -ge 3 -and $bytes[0] -eq 0xEF -and $bytes[1] -eq 0xBB -and $bytes[2] -eq 0xBF) { throw "O artefato '$($item.Name)' contém BOM UTF-8." }
    return $utf8.GetString($bytes)
}

function Read-Json {
    param([Parameter(Mandatory)][string]$Path, [Parameter(Mandatory)][long]$MaximumBytes, [Parameter(Mandatory)][string]$Label)
    $text = Read-StrictUtf8 -LiteralPath $Path -MaximumBytes $MaximumBytes
    try { Assert-NoDuplicateJsonProperties -Text $text } catch { throw "O JSON $Label contém estrutura inválida ou propriedade duplicada." }
    try { $value = $text | ConvertFrom-Json -Depth 100 } catch { throw "O JSON $Label é inválido." }
    return [pscustomobject]@{ Text = $text; Value = $value }
}

function Get-Utf8Sha256 {
    param([Parameter(Mandatory)][string]$Value)
    return [Convert]::ToHexString([System.Security.Cryptography.SHA256]::HashData($utf8.GetBytes($Value))).ToLowerInvariant()
}

function Resolve-EvidencePath {
    param([Parameter(Mandatory)][string]$RelativePath)
    $platformPath = $RelativePath.Replace('/', [System.IO.Path]::DirectorySeparatorChar)
    $resolvedPath = [System.IO.Path]::GetFullPath((Join-Path $repositoryRoot $platformPath))
    $insideAllowedRoot = $false
    foreach ($prefix in $allowedPrefixes) {
        if ($resolvedPath.StartsWith($prefix, [System.StringComparison]::OrdinalIgnoreCase)) { $insideAllowedRoot = $true; break }
    }
    if (-not $insideAllowedRoot -or -not (Test-Path -LiteralPath $resolvedPath -PathType Leaf)) { throw "A evidência '$RelativePath' está ausente ou fora dos dois repositórios permitidos." }
    return $resolvedPath
}

function Get-SemanticManifestText {
    param([Parameter(Mandatory)][string]$JsonText)
    $document = [System.Text.Json.JsonDocument]::Parse($JsonText)
    try { return Convert-JsonElementToCanonicalText -Element $document.RootElement -Path '$' } finally { $document.Dispose() }
}

$identityDirectory = Join-Path $repositoryRoot ("docs\catalogos\" + $config.Directory)
$identityManifestPath = Join-Path $identityDirectory 'manifesto.json'
$readmePath = Join-Path $identityDirectory 'README.md'
$contractManifestPath = Join-Path $repositoryRoot ("docs\catalogos\contratos-esl-$TemplateId\manifesto.json")
$identityJson = Read-Json -Path $identityManifestPath -MaximumBytes $maximumManifestBytes -Label 'de identidade'
$contractJson = Read-Json -Path $contractManifestPath -MaximumBytes $maximumManifestBytes -Label 'do contrato'
$readmeText = Read-StrictUtf8 -LiteralPath $readmePath -MaximumBytes $maximumManifestBytes
$readmeHash = (Get-FileHash -LiteralPath $readmePath -Algorithm SHA256).Hash.ToLowerInvariant()
$manifest = $identityJson.Value
$identity = $manifest.identity
$contract = $contractJson.Value.contract

$expectedEvidenceVocabulary = @('HISTORICAL_SANITIZED_OBSERVATION', 'LEGACY_STATIC_EVIDENCE', 'SYNTHETIC_FIXTURES', 'NO_TEMPORAL_OR_TENANT_PAYLOAD_PROOF', 'NO_INDEPENDENT_COMPLETENESS_EVIDENCE')
$expectedQuarantineReasons = @('AMBIGUOUS_BUSINESS_ALIAS', 'CARDINALITY_DIVERGENCE', 'INVALID_SOURCE_KEY_TYPE', 'INVALID_SOURCE_KEY_VALUE', 'MISSING_SOURCE_KEY', 'SCOPE_MISMATCH', 'SOURCE_KEY_COLLISION', 'SOURCE_KEY_TOO_LONG', 'UNPROVEN_ALIAS_CHANGE', 'UNPROVEN_REKEY')
$expectedActions = @('BLOCK_PROMOTION_PENDING_VERTICAL_RESOLUTION', 'KEEP_CANONICAL', 'NO_OP_REPLAY', 'PRESERVE_UNRESOLVED_OBSERVATION', 'QUARANTINE', 'REACTIVATE_EXACT_BINDING', 'REGISTER_NEW_CANONICAL', 'VERSION_ALIASES_PRESERVE_CANONICAL')

foreach ($propertyName in @('catalogVersion', 'identityFingerprintVersion', 'task', 'route', 'decisionStatus', 'evidenceVocabulary', 'quarantineReasons', 'actions', 'sourceFamily', 'registry', 'scope', 'canonicalization', 'identity')) { Assert-RequiredProperty -Object $manifest -Name $propertyName -Label 'manifesto' }
foreach ($arrayProperty in @('evidenceVocabulary', 'quarantineReasons', 'actions')) { Assert-ArrayProperty -Object $manifest -Name $arrayProperty -Label 'manifesto' }
if ([string]$manifest.catalogVersion -cne '2026-09-04.v2-009b.1' -or [string]$manifest.identityFingerprintVersion -cne $config.IdentityVersion -or [string]$manifest.task -cne $config.Task -or [string]$manifest.route -cne $config.Route -or [string]$manifest.decisionStatus -cne $config.DecisionStatus) { throw "Versão, tarefa, rota ou resultado do catálogo $TemplateId diverge da baseline." }
if ($readmeHash -cne [string]$config.ReadmeSha256 -or $readmeText -cnotmatch [regex]::Escape($config.Task) -or $readmeText -cnotmatch [regex]::Escape($config.DecisionStatus) -or $readmeText -cnotmatch 'V2-041') { throw "O README $TemplateId diverge do SHA-256 revisado ou não explicita tarefa, resultado próprio e hold V2-041." }
Assert-ExactSet -Actual @($manifest.evidenceVocabulary) -Expected $expectedEvidenceVocabulary -Label 'do vocabulário de evidência'
Assert-ExactSet -Actual @($manifest.quarantineReasons) -Expected $expectedQuarantineReasons -Label 'de motivos de quarentena'
Assert-ExactSet -Actual @($manifest.actions) -Expected $expectedActions -Label 'de ações de identidade'

if ([string]$manifest.sourceFamily.sourceKind -cne 'ESL' -or [string]$manifest.sourceFamily.transport -cne 'DATA_EXPORT' -or [string]$manifest.registry.comparison -cne 'CASE_SENSITIVE_EXACT_BIN2' -or [string]$manifest.registry.canonicalIdStrategy -cne 'SQL_SURROGATE_BIGINT_IDENTITY' -or [string]$manifest.registry.physicalEnforcementGate -cne 'V2-009d') { throw "Source family ou registry $TemplateId diverge do ADR 0018." }
Assert-BooleanProperty -Object $manifest.registry -Name 'recordStateIdIsCanonicalId' -Expected $false -Label 'registry'
Assert-ArrayProperty -Object $manifest.registry -Name 'tuple' -Label 'registry'
if ((@($manifest.registry.tuple | ForEach-Object { [string]$_ }) -join '|') -cne 'source_instance|tenant_scope|entity|source_key') { throw "A tuple de identidade $TemplateId não é a sequência aprovada." }

Assert-BooleanProperty -Object $manifest.scope -Name 'globalUniquenessProven' -Expected $false -Label 'scope'
Assert-RequiredProperty -Object $manifest.scope -Name 'tenantPayloadPath' -Label 'scope'
Assert-ArrayProperty -Object $manifest.scope -Name 'forbiddenTenantSentinels' -Label 'scope'
if ([string]$manifest.scope.policy -cne 'EXPLICIT_SOURCE_INSTANCE_AND_TENANT_REQUIRED' -or $null -ne $manifest.scope.tenantPayloadPath -or [string]$manifest.scope.tenantDerivation -cne 'EXTERNAL_CONFIGURATION_REQUIRED_NO_BUSINESS_FIELD_DERIVATION') { throw "O catálogo $TemplateId inventa tenant ou unicidade global." }
Assert-ExactSet -Actual @($manifest.scope.forbiddenTenantSentinels) -Expected @('DEFAULT', 'GLOBAL', 'SINGLETON') -Label 'de sentinels proibidos'

Assert-BooleanProperty -Object $manifest.canonicalization -Name 'integerAndStringEquivalent' -Expected $false -Label 'canonicalization'
Assert-ArrayProperty -Object $manifest.canonicalization -Name 'acceptedWireTypes' -Label 'canonicalization'
$maximumStorageCharacters = Get-ExactPropertyValue -Object $manifest.canonicalization -Name 'maximumStorageCharacters' -Label 'canonicalization'
if (($maximumStorageCharacters -isnot [int] -and $maximumStorageCharacters -isnot [long]) -or $maximumStorageCharacters -ne 256 -or [string]$manifest.canonicalization.integerEncoding -cne 'INTEGER:<CANONICAL_DECIMAL>' -or [string]$manifest.canonicalization.stringEncoding -cne 'STRING:<EXACT_TEXT>' -or [string]$manifest.canonicalization.textNormalization -cne 'NONE_FAIL_ON_BLANK_BOUNDARY_SPACE_CONTROL_OR_INVALID_UNICODE' -or [string]$manifest.canonicalization.processingBound -cne 'ONE_OBSERVATION_AT_A_TIME') { throw "O codec de identidade $TemplateId diverge do limite aprovado." }
Assert-ExactSet -Actual @($manifest.canonicalization.acceptedWireTypes) -Expected $config.CodecWireTypes -Label 'de tipos do codec'

if ([string]$contract.contractId -cne "dataexport-$TemplateId" -or [string]$contract.contractVersion -cne '2026-09-04.v2-025b.1' -or [string]$contract.fingerprints.release -cne $config.ContractFingerprint -or [string]$contract.aspects.rootIdentity.classification -cne $config.RootAspect -or [string]$contract.aspects.childIdentity.classification -cne $config.ChildAspect) { throw "O catálogo $TemplateId não está ligado ao contrato V2-025b exato." }
foreach ($fingerprintName in @('semantics', 'metadata', 'response')) {
    $contractFingerprint = Get-Utf8Sha256 -Value ([string]$contract.fingerprintInputs.$fingerprintName)
    if ([string]$contract.fingerprints.$fingerprintName -cne $contractFingerprint) { throw "O fingerprint componente '$fingerprintName' do contrato V2-025b/$TemplateId diverge do material declarado." }
}
$contractReleaseMaterial = ([string]$contract.fingerprintInputs.release) + '|' +
    ([string]$contract.fingerprints.semantics) + '|' +
    ([string]$contract.fingerprints.metadata) + '|' +
    ([string]$contract.fingerprints.response)
if ([string]$contract.fingerprints.release -cne (Get-Utf8Sha256 -Value $contractReleaseMaterial)) { throw "O fingerprint de release do contrato V2-025b/$TemplateId diverge dos componentes." }

foreach ($propertyName in @('entity', 'contractId', 'contractVersion', 'sourceContractReleaseFingerprint', 'recordRoot', 'sourceKey', 'canonicalKey', 'businessKeys', 'aliases', 'physicalGrain', 'proof', 'repeatAndConflictPolicy', 'outcome', 'capabilities', 'nextGates', 'evidenceBounds', 'fingerprintInputs', 'fingerprints')) { Assert-RequiredProperty -Object $identity -Name $propertyName -Label 'identity' }
foreach ($propertyName in @('path', 'name', 'wireTypes', 'role', 'decision')) { Assert-RequiredProperty -Object $identity.sourceKey -Name $propertyName -Label 'sourceKey' }
Assert-ArrayProperty -Object $identity.sourceKey -Name 'wireTypes' -Label 'sourceKey'
if ([string]$identity.entity -cne $config.Entity -or [string]$identity.contractId -cne "dataexport-$TemplateId" -or [string]$identity.contractVersion -cne '2026-09-04.v2-025b.1' -or [string]$identity.sourceContractReleaseFingerprint -cne [string]$contract.fingerprints.release -or [string]$identity.recordRoot -cne '/data/*' -or [string]$identity.sourceKey.role -cne $config.SourceKeyRole -or [string]$identity.sourceKey.decision -cne $config.SourceKeyDecision) { throw "A raiz ou source key $TemplateId diverge da decisão registrada." }
if ($null -eq $config.SourceKeyPath) {
    if ($null -ne $identity.sourceKey.path -or $null -ne $identity.sourceKey.name) { throw "O catálogo $TemplateId inventa source key ausente." }
} elseif ([string]$identity.sourceKey.path -cne $config.SourceKeyPath -or [string]$identity.sourceKey.name -cne $config.SourceKeyName) { throw "O caminho ou nome da source key $TemplateId diverge." }
$hasDecisionBasis = $identity.sourceKey.PSObject.Properties.Name -ccontains 'decisionBasis'
if ($null -eq $config.SourceKeyDecisionBasis) {
    if ($hasDecisionBasis) { throw "A source key $TemplateId possui base decisória inesperada." }
} elseif (-not $hasDecisionBasis -or [string]$identity.sourceKey.decisionBasis -cne $config.SourceKeyDecisionBasis) { throw "A base da decisão da source key $TemplateId diverge." }
Assert-ExactSet -Actual @($identity.sourceKey.wireTypes) -Expected $config.SourceKeyWireTypes -Label 'de tipos da source key'

if ([string]$identity.canonicalKey.strategy -cne 'SQL_SURROGATE_BIGINT_IDENTITY' -or [string]$identity.canonicalKey.binding -cne $config.CanonicalBinding) { throw "A canonical key $TemplateId diverge da decisão registrada." }
Assert-ArrayProperty -Object $identity.canonicalKey -Name 'notEquivalentTo' -Label 'canonicalKey'
Assert-ExactSet -Actual @($identity.canonicalKey.notEquivalentTo) -Expected $config.CanonicalNotEquivalent -Label 'de não equivalência canônica'

foreach ($propertyName in @('accepted', 'candidates')) { Assert-RequiredProperty -Object $identity.businessKeys -Name $propertyName -Label 'businessKeys' }
foreach ($arrayProperty in @('accepted', 'candidates')) { Assert-ArrayProperty -Object $identity.businessKeys -Name $arrayProperty -Label 'businessKeys' }
Assert-ExactSet -Actual @($identity.businessKeys.accepted) -Expected @() -Label 'de business keys aceitas'
$businessCandidateJson = @($identity.businessKeys.candidates | ForEach-Object { $_ | ConvertTo-Json -Depth 20 -Compress })
Assert-ExactSet -Actual $businessCandidateJson -Expected $config.BusinessCandidates -Label 'de candidatas de business key'
Assert-ArrayProperty -Object $identity.aliases -Name 'accepted' -Label 'aliases'
Assert-ArrayProperty -Object $identity.aliases -Name 'rejected' -Label 'aliases'
Assert-ExactSet -Actual @($identity.aliases.accepted) -Expected @() -Label 'de aliases aceitos'
Assert-ExactSet -Actual @($identity.aliases.rejected) -Expected $config.RejectedAliases -Label 'de aliases rejeitados'
if ([string]$identity.aliases.rekeyPolicy -cne $config.RekeyPolicy) { throw "A política de rekey $TemplateId diverge." }

if ([string]$identity.physicalGrain.root -cne $config.PhysicalRoot -or [string]$identity.physicalGrain.relationPolicy -cne $config.RelationPolicy) { throw "O grão físico ou limite relacional $TemplateId diverge." }
Assert-ArrayProperty -Object $identity.physicalGrain -Name 'children' -Label 'physicalGrain'
$childJson = @($identity.physicalGrain.children | ForEach-Object { $_ | ConvertTo-Json -Depth 20 -Compress })
Assert-ExactSet -Actual $childJson -Expected $config.Children -Label 'de componentes físicos/filhos'
if ([string]$identity.proof.uniqueness -cne $config.ProofUniqueness -or [string]$identity.proof.cardinality -cne $config.ProofCardinality -or [string]$identity.proof.temporalStability -cnotmatch '^UNPROVEN_' -or [string]$identity.proof.tenantScope -cne 'UNPROVEN_IN_PAYLOAD_EXPLICIT_EXTERNAL_SCOPE_REQUIRED') { throw "Os limites de unicidade, estabilidade, tenant ou cardinalidade $TemplateId divergem." }

if ([string]$identity.repeatAndConflictPolicy.sameScopedRootSourceKey -cne $config.Replay -or [string]$identity.repeatAndConflictPolicy.physicalVariants -cne $config.PhysicalVariants -or [string]$identity.repeatAndConflictPolicy.divergentRoot -cne $config.DivergentRoot -or [string]$identity.repeatAndConflictPolicy.aliasCollision -cne 'BLOCK_DEPENDENT_RESOLUTION_NO_AUTOMATIC_REPOINT' -or [string]$identity.repeatAndConflictPolicy.alternateKeyOrRekey -cnotmatch '^QUARANTINE_UNPROVEN_REKEY_' -or [string]$identity.repeatAndConflictPolicy.missingOrInvalidScopeOrKey -cne 'QUARANTINE' -or [string]$identity.repeatAndConflictPolicy.reactivation -cne $config.Reactivation) { throw "Replay, colisão, rekey ou reativação $TemplateId diverge." }

Assert-BooleanProperty -Object $identity.outcome -Name 'closeSubcheckbox' -Expected ([bool]$config.Close) -Label 'outcome'
Assert-ArrayProperty -Object $identity.outcome -Name 'blockers' -Label 'outcome'
Assert-ArrayProperty -Object $identity.outcome -Name 'unblockEvidence' -Label 'outcome'
Assert-ExactSet -Actual @($identity.outcome.blockers) -Expected $config.Blockers -Label 'de bloqueios'
Assert-ExactSet -Actual @($identity.outcome.unblockEvidence) -Expected $config.UnblockEvidence -Label 'de evidências de desbloqueio'
if ([string]$identity.capabilities.shadowDomainImplementation -cne $config.ShadowCapability -or [string]$identity.capabilities.sweepOrDeactivation -cne 'BLOCKED_NO_COMPLETENESS_PROOF' -or [string]$identity.capabilities.cutover -cne 'BLOCKED_NO_COMPLETENESS_PROOF' -or [string]$identity.capabilities.externalHolds.'V2-025d' -cne 'BLOCKED_BY_V2_041_EXTERNAL_HOLD') { throw "As capabilities ou o hold V2-041 de $TemplateId divergem." }
Assert-ExactSet -Actual @($identity.nextGates) -Expected $config.NextGates -Label 'de próximos gates'
if ($identity.nextGates -isnot [System.Array]) { throw "nextGates $TemplateId não é array JSON." }

foreach ($propertyName in @('supports', 'doesNotSupport', 'files')) { Assert-RequiredProperty -Object $identity.evidenceBounds -Name $propertyName -Label 'evidenceBounds' }
foreach ($arrayProperty in @('supports', 'doesNotSupport', 'files')) { Assert-ArrayProperty -Object $identity.evidenceBounds -Name $arrayProperty -Label 'evidenceBounds' }
$evidenceFiles = @($identity.evidenceBounds.files)
if (@($identity.evidenceBounds.supports).Count -lt 3 -or @($identity.evidenceBounds.doesNotSupport).Count -lt 5 -or $evidenceFiles.Count -lt 8) { throw "Os limites de evidência $TemplateId estão incompletos." }
$evidencePaths = @()
foreach ($evidenceFile in $evidenceFiles) {
    foreach ($propertyName in @('path', 'role', 'readOnly')) { Assert-RequiredProperty -Object $evidenceFile -Name $propertyName -Label 'arquivo de evidência' }
    Assert-BooleanProperty -Object $evidenceFile -Name 'readOnly' -Expected $true -Label 'arquivo de evidência'
    if ([string]::IsNullOrWhiteSpace([string]$evidenceFile.path) -or [string]::IsNullOrWhiteSpace([string]$evidenceFile.role)) { throw "Uma evidência $TemplateId não possui path/role explícito." }
    $evidencePaths += [string]$evidenceFile.path
    [void](Resolve-EvidencePath -RelativePath ([string]$evidenceFile.path))
}
Assert-ExactSet -Actual $evidencePaths -Expected $evidencePaths -Label 'de paths de evidência sem duplicatas'

foreach ($anchor in $config.Anchors) {
    if ($evidencePaths -cnotcontains [string]$anchor.Path) { throw "A evidência ancorada '$($anchor.Path)' não consta do catálogo $TemplateId." }
    $resolvedAnchor = Resolve-EvidencePath -RelativePath ([string]$anchor.Path)
    $anchorItem = Get-Item -LiteralPath $resolvedAnchor
    if ($anchorItem.Length -gt $maximumEvidenceBytes) { throw "A evidência ancorada de $TemplateId excede o limite de leitura." }
    if ($null -ne $anchor.Sha256) {
        $actualHash = (Get-FileHash -LiteralPath $resolvedAnchor -Algorithm SHA256).Hash.ToLowerInvariant()
        if ($actualHash -cne [string]$anchor.Sha256) { throw "A evidência estática ancorada de $TemplateId diverge do SHA-256 revisado." }
    }
    $anchorText = [System.IO.File]::ReadAllText($resolvedAnchor, [System.Text.Encoding]::UTF8)
    foreach ($pattern in @($anchor.Required)) { if (-not [regex]::IsMatch($anchorText, [string]$pattern, [System.Text.RegularExpressions.RegexOptions]::Multiline)) { throw "Uma âncora semântica obrigatória está ausente na evidência de $TemplateId." } }
    foreach ($pattern in @($anchor.Forbidden)) { if ([regex]::IsMatch($anchorText, [string]$pattern, [System.Text.RegularExpressions.RegexOptions]::Multiline)) { throw "Uma âncora semântica proibida apareceu na evidência de $TemplateId." } }
}

if ($TemplateId -ceq '8636') {
    $historyPath = Resolve-EvidencePath -RelativePath '../etl-extracao-dados/docs/legado/pipelines-antigos/02-apis/dataexport/contasapagar.md'
    $historyText = [System.IO.File]::ReadAllText($historyPath, [System.Text.Encoding]::UTF8)
    $matches = [regex]::Matches($historyText, '"ant_ils_sequence_code"\s*:\s*(?<key>\d+)[^{}]*?"ant_ils_pas_value"\s*:\s*"(?<value>[^"]*)"', [System.Text.RegularExpressions.RegexOptions]::IgnoreCase)
    $counterexampleFound = $false
    foreach ($group in @($matches | ForEach-Object { [pscustomobject]@{ Key = $_.Groups['key'].Value; Value = $_.Groups['value'].Value } } | Group-Object Key)) {
        $values = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::Ordinal)
        foreach ($row in $group.Group) { [void]$values.Add([string]$row.Value) }
        if ($group.Count -gt 1 -and $values.Count -gt 1) { $counterexampleFound = $true; break }
    }
    if (-not $counterexampleFound) { throw 'A contraevidência histórica 8636 de candidata repetida com campo contábil divergente não foi preservada.' }
}
if ($TemplateId -ceq '4924') {
    $historyPath = Resolve-EvidencePath -RelativePath '../etl-extracao-dados/docs/legado/pipelines-antigos/02-apis/dataexport/faturaporcliente.md'
    $historyText = [System.IO.File]::ReadAllText($historyPath, [System.Text.Encoding]::UTF8)
    $matches = [regex]::Matches($historyText, '"fit_ant_document"\s*:\s*"(?<document>[^"]+)"[^{}]*?"fit_fhe_cte_number"\s*:\s*(?<cte>\d+|"[^"]+")', [System.Text.RegularExpressions.RegexOptions]::IgnoreCase)
    $multiplicityFound = $false
    foreach ($group in @($matches | ForEach-Object { [pscustomobject]@{ Document = $_.Groups['document'].Value; Cte = $_.Groups['cte'].Value } } | Group-Object Document)) {
        $ctes = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::Ordinal)
        foreach ($row in $group.Group) { [void]$ctes.Add([string]$row.Cte) }
        if ($group.Count -gt 1 -and $ctes.Count -gt 1) { $multiplicityFound = $true; break }
    }
    if (-not $multiplicityFound) { throw 'A contraevidência histórica 4924 de multiplicidade título/CT-e não foi preservada.' }
}

$fixtures = @($contract.fixtures)
Assert-ExactSet -Actual @($fixtures | ForEach-Object { [string]$_.role }) -Expected $config.FixtureRoles -Label 'de roles das fixtures'
Assert-ExactSet -Actual @($fixtures | ForEach-Object { [string]$_.path }) -Expected @($fixtures | ForEach-Object { [string]$_.path }) -Label 'de paths das fixtures sem duplicatas'
$fixtureUnderTest = $null
foreach ($fixture in $fixtures) {
    foreach ($propertyName in @('path', 'role', 'sha256')) { Assert-RequiredProperty -Object $fixture -Name $propertyName -Label 'fixture contratual' }
    $resolvedFixture = Resolve-EvidencePath -RelativePath ([string]$fixture.path)
    if (-not $resolvedFixture.StartsWith($repositoryPrefix, [System.StringComparison]::OrdinalIgnoreCase)) { throw "Uma fixture $TemplateId sai do repositório V2." }
    $fixtureJson = Read-Json -Path $resolvedFixture -MaximumBytes $maximumFixtureBytes -Label 'de fixture'
    $actualHash = (Get-FileHash -LiteralPath $resolvedFixture -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($actualHash -cne [string]$fixture.sha256) { throw "Uma fixture $TemplateId diverge do SHA-256 do contrato." }
    if ([string]$fixture.role -ceq 'METADATA_SELECTED_SUBSET') {
        Assert-BooleanProperty -Object $fixtureJson.Value -Name 'syntheticOnly' -Expected $true -Label 'metadata sintética'
    } else {
        Assert-RequiredProperty -Object $fixtureJson.Value -Name 'data' -Label 'payload da fixture'
        if ((Get-ExactPropertyValue -Object $fixtureJson.Value -Name 'data' -Label 'payload da fixture') -isnot [System.Array]) { throw "A propriedade data da fixture sintética $TemplateId não é array JSON." }
        if ([string]$fixture.role -cmatch '_TERMINAL$' -and @($fixtureJson.Value.data).Count -ne 0) { throw "Uma fixture terminal sintética $TemplateId não está vazia." }
        if ([string]$fixture.role -ceq $config.FixtureRole) { $fixtureUnderTest = $fixtureJson.Value }
    }
}
if ($null -eq $fixtureUnderTest) { throw "A fixture sintética de shape $TemplateId não foi encontrada." }

$rows = @($fixtureUnderTest.data)
$validFixtureWireTypes = @('INTEGER', 'UNVERIFIED')
if ([string]$config.FixtureWireType -cnotin $validFixtureWireTypes) { throw "A baseline de wire type da fixture $TemplateId não pertence ao vocabulário fechado." }
$keySet = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::Ordinal)
foreach ($row in $rows) {
    Assert-RequiredProperty -Object $row -Name $config.FixtureKey -Label 'linha sintética'
    $value = $row.PSObject.Properties[$config.FixtureKey].Value
    if ($config.FixtureWireType -ceq 'INTEGER' -and $value -isnot [int] -and $value -isnot [long]) { throw "A fixture sintética $TemplateId não preserva o shape INTEGER selecionado." }
    $keyIdentity = if ($null -eq $value) { 'NULL' } else { $value.GetType().FullName + ':' + [string]$value }
    [void]$keySet.Add($keyIdentity)
    foreach ($requiredField in $config.RequiredFixtureFields) { Assert-RequiredProperty -Object $row -Name $requiredField -Label 'linha sintética' }
    foreach ($forbiddenField in $config.ForbiddenFixtureFields) { if ($row.PSObject.Properties.Name -ccontains $forbiddenField) { throw "A fixture sintética $TemplateId inventa o campo proibido '$forbiddenField'." } }
}
if ($rows.Count -ne $config.FixtureRows -or $keySet.Count -ne $config.FixtureDistinctKeys) { throw "A fixture sintética selecionada $TemplateId diverge da shape sanitizada declarada; isso não é prova de cardinalidade." }

$semanticManifestText = Get-SemanticManifestText -JsonText $identityJson.Text
$identityFingerprint = Get-Utf8Sha256 -Value (([string]$identity.fingerprintInputs.identity) + '|' + $semanticManifestText)
if ([string]$identity.fingerprints.identity -cne $identityFingerprint -or $identityFingerprint -cne $config.ExpectedIdentityFingerprint) { throw "O fingerprint semântico de identidade $TemplateId diverge do manifesto ou da baseline revisada (calculado: $identityFingerprint)." }
$expectedReleaseInput = '2026-09-04.v2-009b.1|' + $config.IdentityVersion + '|dataexport-' + $TemplateId
if ([string]$identity.fingerprintInputs.release -cne $expectedReleaseInput) { throw "O material de release $TemplateId diverge da baseline derivada." }
$releaseMaterial = ([string]$identity.fingerprintInputs.release) + '|' + $identityFingerprint + '|' + [string]$identity.sourceContractReleaseFingerprint
if ([string]$identity.fingerprints.release -cne (Get-Utf8Sha256 -Value $releaseMaterial)) { throw "O fingerprint de release $TemplateId diverge dos componentes." }

$sanitizedText = $identityJson.Text + "`n" + $readmeText
if ($sanitizedText -match '(?i)authorization\s*:\s*bearer|password\s*[=:]|https?://|real[-_ ]?(?:id|cursor|document)') { throw "O catálogo $TemplateId contém marcador incompatível com a evidência sanitizada." }

$closing = if ($config.Close) { 'subcheckbox concluível' } else { 'subcheckbox preservado aberto' }
Write-Output "PASS: decisão V2-009b/$TemplateId validada como $($config.DecisionStatus), $closing, com semântica, evidência estática, fixtures sintéticas, limites, bloqueios e fingerprints próprios."
