-- Gate progressivo de dados. Somente leitura: valida V001-V009 já aplicadas.
-- Fonte estrutural: manifests/fingerprints da fundação, control plane e kernel, conferidos pelo runner.

SET NOCOUNT ON;

DECLARE @failures TABLE (
    category NVARCHAR(40) NOT NULL,
    object_name NVARCHAR(256) NOT NULL,
    detail NVARCHAR(400) NOT NULL
);

DECLARE @expected_schemas TABLE (schema_name SYSNAME COLLATE DATABASE_DEFAULT NOT NULL PRIMARY KEY);
INSERT INTO @expected_schemas (schema_name)
VALUES (N'ctl'), (N'stg'), (N'core'), (N'ref'), (N'mart'), (N'pub'), (N'recon');

INSERT INTO @failures (category, object_name, detail)
SELECT N'SCHEMA', expected.schema_name, N'Schema obrigatório ausente.'
FROM @expected_schemas AS expected
WHERE SCHEMA_ID(expected.schema_name) IS NULL;

INSERT INTO @failures (category, object_name, detail)
SELECT N'SCHEMA', N'shadow', N'Schema histórico proibido no caminho Flyway ativo.'
WHERE SCHEMA_ID(N'shadow') IS NOT NULL;

DECLARE @expected_tables TABLE (object_name SYSNAME COLLATE DATABASE_DEFAULT NOT NULL PRIMARY KEY);
INSERT INTO @expected_tables (object_name)
VALUES
    (N'source_catalog'),
    (N'execution_cycle'),
    (N'execution_partition'),
    (N'execution_attempt'),
    (N'execution_state_event'),
    (N'execution_lease'),
    (N'execution_page_audit'),
    (N'execution_count'),
    (N'source_watermark_observation'),
    (N'partition_publication_pointer'),
    (N'execution_publication_event'),
    (N'incremental_publication_watermark'),
    (N'execution_promotion_result');

INSERT INTO @failures (category, object_name, detail)
SELECT N'TABLE', CONCAT(N'ctl.', expected.object_name), N'Tabela obrigatória ausente.'
FROM @expected_tables AS expected
WHERE OBJECT_ID(CONCAT(N'ctl.', expected.object_name), N'U') IS NULL;

DECLARE @expected_procedures TABLE (object_name SYSNAME COLLATE DATABASE_DEFAULT NOT NULL PRIMARY KEY);
INSERT INTO @expected_procedures (object_name)
VALUES
    (N'usp_control_plane_register_source'),
    (N'usp_control_plane_start_cycle'),
    (N'usp_control_plane_start_execution'),
    (N'usp_control_plane_heartbeat_lease'),
    (N'usp_control_plane_record_page'),
    (N'usp_control_plane_record_counts'),
    (N'usp_control_plane_transition_execution'),
    (N'usp_control_plane_register_incremental_frontier'),
    (N'usp_control_plane_publish_execution'),
    (N'usp_control_plane_recover_stale_executions');

INSERT INTO @failures (category, object_name, detail)
SELECT N'PROCEDURE', CONCAT(N'ctl.', expected.object_name), N'Procedure obrigatória ausente.'
FROM @expected_procedures AS expected
WHERE OBJECT_ID(CONCAT(N'ctl.', expected.object_name), N'P') IS NULL;

-- O gate cobre exatamente a fundação, o plano comum e a vertical de Usuários autorizada. O
-- histórico interno Flyway é permitido quando a transição guardada for aplicada pelo owner.
INSERT INTO @failures (category, object_name, detail)
SELECT N'OBJECT', CONCAT(schema_definition.name, N'.', object_definition.name),
       N'Objeto fora do escopo V001-V009 detectado.'
FROM sys.objects AS object_definition
INNER JOIN sys.schemas AS schema_definition
    ON schema_definition.schema_id = object_definition.schema_id
INNER JOIN @expected_schemas AS expected_schema
    ON expected_schema.schema_name = schema_definition.name COLLATE DATABASE_DEFAULT
WHERE object_definition.is_ms_shipped = 0
  AND object_definition.type IN (N'U', N'P', N'V', N'FN', N'IF', N'TF', N'TR')
  AND NOT (
      schema_definition.name = N'ctl'
      AND object_definition.type = N'U'
      AND object_definition.name IN (
          N'flyway_schema_history',
          N'source_catalog',
          N'execution_cycle',
          N'execution_partition',
          N'execution_attempt',
          N'execution_state_event',
          N'execution_lease',
          N'execution_page_audit',
          N'execution_count',
          N'source_watermark_observation',
          N'partition_publication_pointer',
          N'execution_publication_event',
          N'incremental_publication_watermark',
          N'execution_promotion_result',
          N'staging_retention_policy',
          N'staging_retention_policy_event',
          N'staging_legal_hold',
          N'staging_legal_hold_event',
          N'staging_lifecycle_plan',
          N'staging_lifecycle_plan_item',
          N'staging_lifecycle_purge_event',
          N'data_quality_policy',
          N'data_quality_check_policy',
          N'usuario_promotion_result'
      )
  )
  AND NOT (
      schema_definition.name = N'ctl'
      AND object_definition.type = N'P'
      AND object_definition.name IN (
          N'usp_control_plane_register_source',
          N'usp_control_plane_start_cycle',
          N'usp_control_plane_start_execution',
          N'usp_control_plane_heartbeat_lease',
          N'usp_control_plane_record_page',
          N'usp_control_plane_record_counts',
          N'usp_control_plane_transition_execution',
          N'usp_control_plane_register_incremental_frontier',
          N'usp_control_plane_publish_execution',
          N'usp_control_plane_recover_stale_executions',
          N'usp_approve_staging_retention_policy',
          N'usp_revoke_staging_retention_policy',
          N'usp_place_staging_legal_hold',
          N'usp_release_staging_legal_hold',
          N'usp_observe_platform_health'
      )
  )
  AND NOT (
      schema_definition.name = N'ctl'
      AND object_definition.type = N'IF'
      AND object_definition.name IN (
          N'ufn_invalid_execution_state_ledger',
          N'ufn_invalid_staging_legal_hold'
      )
  )
  AND NOT (
      schema_definition.name = N'stg'
      AND object_definition.type = N'U'
      AND object_definition.name IN (
          N'execution_record', N'execution_candidate', N'usuario_record'
      )
  )
  AND NOT (
      schema_definition.name = N'recon'
      AND object_definition.type = N'IF'
      AND object_definition.name = N'ufn_staging_lifecycle_extension_archive_budget'
  )
  AND NOT (
      schema_definition.name = N'recon'
      AND object_definition.type = N'U'
      AND object_definition.name IN (
          N'quarantine_record',
          N'execution_candidate_application',
          N'execution_reconciliation_result',
          N'execution_data_quality_evaluation',
          N'execution_data_quality_check_result',
          N'execution_metric_snapshot',
          N'observability_alert',
          N'usuario_quarantine',
          N'usuario_apply_authorization',
          N'usuario_candidate_application',
          N'usuario_reconciliation_result',
          N'usuario_stage_disposal_evidence',
          N'usuario_stage_archive',
          N'usuario_stage_restore'
      )
  )
  AND NOT (
      schema_definition.name = N'core'
      AND object_definition.type = N'U'
      AND object_definition.name IN (
          N'entity_record_state', N'usuario', N'usuario_history'
      )
  )
  AND NOT (
      schema_definition.name = N'core'
      AND object_definition.type = N'V'
      AND object_definition.name = N'v_usuario_dimension_current_v1'
  )
  AND NOT (
      schema_definition.name = N'stg'
      AND object_definition.type = N'P'
      AND object_definition.name IN (
          N'usp_stage_record',
          N'usp_stage_usuario_record',
          N'usp_plan_staging_lifecycle',
          N'usp_plan_staging_lifecycle_at',
          N'usp_archive_staging_lifecycle',
          N'usp_purge_staging_lifecycle'
      )
  )
  AND NOT (
      schema_definition.name = N'core'
      AND object_definition.type = N'P'
      AND object_definition.name IN (
          N'usp_prepare_staged_execution',
          N'usp_apply_reconcile_publish_execution',
          N'usp_apply_reconcile_publish_usuarios'
      )
  )
  AND NOT (
      schema_definition.name = N'recon'
      AND object_definition.type = N'U'
      AND object_definition.name IN (
          N'staging_lifecycle_archive_manifest',
          N'staging_record_archive',
          N'staging_candidate_archive',
          N'quarantine_record_archive',
          N'execution_candidate_application_archive',
          N'execution_reconciliation_result_archive',
          N'execution_state_event_archive',
          N'execution_page_audit_archive',
          N'execution_count_archive',
          N'execution_publication_event_archive',
          N'execution_promotion_result_archive',
          N'staging_restore_session',
          N'staging_restore_record',
          N'staging_restore_candidate'
      )
  )
  AND NOT (
      schema_definition.name = N'recon'
      AND object_definition.type = N'P'
      AND object_definition.name IN (
          N'usp_restore_staging_archive',
          N'usp_compute_live_staging_content_root_internal',
          N'usp_compute_archive_content_root_internal',
          N'usp_verify_staging_archive_internal',
          N'usp_verify_staging_restore_internal',
          N'usp_evaluate_execution_data_quality',
          N'usp_observe_execution_data_quality',
          N'usp_record_execution_metric',
          N'usp_raise_observability_alert'
      )
  )
  AND NOT (
      schema_definition.name = N'recon'
      AND object_definition.type = N'FN'
      AND object_definition.name = N'ufn_archive_row_attestation'
  )
  AND NOT (
      schema_definition.name = N'recon'
      AND object_definition.type = N'TR'
      AND object_definition.name IN (
          N'trg_quarantine_record_staging_lineage',
          N'trg_candidate_application_staging_lineage',
           N'trg_usuario_stage_archive_from_generic',
           N'trg_usuario_stage_restore_from_generic',
           N'trg_usuario_data_quality_requires_typed_pass',
           N'trg_usuario_apply_authorization_immutable',
          N'trg_usuario_application_immutable',
          N'trg_usuario_result_immutable',
          N'trg_usuario_stage_archive_immutable',
          N'trg_usuario_stage_restore_immutable',
          N'trg_usuario_stage_disposal_immutable',
          N'trg_usuario_quarantine_immutable'
      )
  )
  AND NOT (
      schema_definition.name = N'ctl'
      AND object_definition.type = N'TR'
      AND object_definition.name IN (
          N'trg_data_quality_policy_immutable',
          N'trg_data_quality_check_policy_immutable',
          N'trg_execution_publication_requires_data_quality',
           N'trg_usuario_prepare_candidate_set',
           N'trg_usuario_publication_requires_apply_wrapper',
           N'trg_usuario_lifecycle_plan_budget',
           N'trg_usuario_promotion_result_immutable'
      )
  )
  AND NOT (
      schema_definition.name = N'stg'
      AND object_definition.type = N'TR'
      AND object_definition.name IN (
          N'trg_execution_record_lifecycle_delete_guard',
          N'trg_execution_candidate_lifecycle_delete_guard',
          N'trg_execution_record_delete_usuario_stage'
      )
  )
  AND NOT (
      schema_definition.name = N'core'
      AND object_definition.type = N'TR'
      AND object_definition.name = N'trg_usuario_history_immutable'
  )
  AND NOT (
      schema_definition.name = N'ref'
      AND (
          object_definition.type = N'U'
          AND object_definition.name IN (
              N'reference_release', N'reference_import_receipt',
              N'reference_release_ratification',
              N'reference_release_revocation', N'calendario', N'status_coleta',
              N'filial_operacional', N'regiao_destino_alias',
              N'filial_operacional_documento', N'frota_propria_documento',
              N'classificacao_frota_alias', N'classificacao_frota_matriz',
              N'classificacao_frota_excecao_token',
              N'atribuicao_filial', N'pagador_exclusao_cubagem',
              N'regiao_logistica_cep', N'regiao_logistica_cidade_uf',
              N'tarifa_rota_uf'
          )
          OR object_definition.type = N'V'
             AND object_definition.name = N'v_status_coleta_seed_candidate_v1'
          OR object_definition.type = N'P'
             AND object_definition.name = N'usp_register_reference_release'
          OR object_definition.type = N'FN'
             AND object_definition.name = N'ufn_normalize_pick_status_v1'
          OR object_definition.type = N'TF'
             AND object_definition.name = N'ufn_calendar_seed_candidate_v1'
          OR object_definition.type = N'TR'
             AND object_definition.name IN (
                 N'trg_reference_release_immutable',
                 N'trg_reference_import_receipt_immutable',
                 N'trg_reference_import_receipt_guard',
                 N'trg_reference_ratification_immutable',
                 N'trg_reference_revocation_immutable',
                 N'trg_reference_revocation_guard',
                 N'trg_reference_ratification_guard',
                 N'trg_calendario_insert_guard',
                 N'trg_status_coleta_insert_guard',
                 N'trg_filial_operacional_insert_guard',
                 N'trg_regiao_destino_alias_insert_guard',
                 N'trg_filial_documento_insert_guard',
                 N'trg_frota_propria_insert_guard',
                 N'trg_classificacao_frota_alias_insert_guard',
                 N'trg_classificacao_frota_matriz_insert_guard',
                 N'trg_classificacao_frota_excecao_insert_guard',
                 N'trg_atribuicao_filial_insert_guard',
                 N'trg_exclusao_cubagem_insert_guard',
                 N'trg_regiao_logistica_cep_insert_guard',
                 N'trg_regiao_logistica_cidade_insert_guard',
                 N'trg_tarifa_rota_uf_insert_guard'
             )
      )
  );

DECLARE @expected_constraints TABLE (
    constraint_name SYSNAME COLLATE DATABASE_DEFAULT NOT NULL PRIMARY KEY,
    object_type CHAR(2) COLLATE DATABASE_DEFAULT NOT NULL,
    parent_table SYSNAME COLLATE DATABASE_DEFAULT NOT NULL
);

INSERT INTO @expected_constraints (constraint_name, object_type, parent_table)
VALUES
    (N'PK_ctl_source_catalog', N'PK', N'source_catalog'),
    (N'DF_ctl_source_catalog_active', N'D', N'source_catalog'),
    (N'CK_ctl_source_catalog_source_instance_non_blank', N'C', N'source_catalog'),
    (N'CK_ctl_source_catalog_source_kind_non_blank', N'C', N'source_catalog'),
    (N'PK_ctl_execution_cycle', N'PK', N'execution_cycle'),
    (N'CK_ctl_execution_cycle_plan_version', N'C', N'execution_cycle'),
    (N'CK_ctl_execution_cycle_plan_fingerprint', N'C', N'execution_cycle'),
    (N'PK_ctl_execution_partition', N'PK', N'execution_partition'),
    (N'UQ_ctl_execution_partition_semantic_key', N'UQ', N'execution_partition'),
    (N'FK_ctl_execution_partition_source_catalog', N'F', N'execution_partition'),
    (N'CK_ctl_execution_partition_non_blank', N'C', N'execution_partition'),
    (N'CK_ctl_execution_partition_mode', N'C', N'execution_partition'),
    (N'CK_ctl_execution_partition_interval', N'C', N'execution_partition'),
    (N'DF_ctl_execution_partition_next_attempt_number', N'D', N'execution_partition'),
    (N'CK_ctl_execution_partition_next_attempt_number', N'C', N'execution_partition'),
    (N'FK_ctl_execution_partition_current_execution', N'F', N'execution_partition'),
    (N'PK_ctl_execution_attempt', N'PK', N'execution_attempt'),
    (N'UQ_ctl_execution_attempt_partition_attempt', N'UQ', N'execution_attempt'),
    (N'UQ_ctl_execution_attempt_partition_execution', N'UQ', N'execution_attempt'),
    (N'UQ_ctl_execution_attempt_idempotency_key', N'UQ', N'execution_attempt'),
    (N'FK_ctl_execution_attempt_partition', N'F', N'execution_attempt'),
    (N'FK_ctl_execution_attempt_cycle', N'F', N'execution_attempt'),
    (N'FK_ctl_execution_attempt_replay', N'F', N'execution_attempt'),
    (N'CK_ctl_execution_attempt_non_blank', N'C', N'execution_attempt'),
    (N'CK_ctl_execution_attempt_contract_fingerprint', N'C', N'execution_attempt'),
    (N'CK_ctl_execution_attempt_configuration_fingerprint', N'C', N'execution_attempt'),
    (N'DF_ctl_execution_attempt_next_transition_sequence', N'D', N'execution_attempt'),
    (N'CK_ctl_execution_attempt_next_transition_sequence', N'C', N'execution_attempt'),
    (N'CK_ctl_execution_attempt_state', N'C', N'execution_attempt'),
    (N'CK_ctl_execution_attempt_terminal_timestamp', N'C', N'execution_attempt'),
    (N'CK_ctl_execution_attempt_transition_sequence_lifecycle_bound', N'C', N'execution_attempt'),
    (N'PK_ctl_execution_state_event', N'PK', N'execution_state_event'),
    (N'UQ_ctl_execution_state_event_sequence', N'UQ', N'execution_state_event'),
    (N'FK_ctl_execution_state_event_execution', N'F', N'execution_state_event'),
    (N'CK_ctl_execution_state_event_previous_state', N'C', N'execution_state_event'),
    (N'CK_ctl_execution_state_event_next_state', N'C', N'execution_state_event'),
    (N'CK_ctl_execution_state_event_reason_code', N'C', N'execution_state_event'),
    (N'CK_ctl_execution_state_event_lifecycle_bound', N'C', N'execution_state_event'),
    (N'PK_ctl_execution_lease', N'PK', N'execution_lease'),
    (N'UQ_ctl_execution_lease_execution', N'UQ', N'execution_lease'),
    (N'FK_ctl_execution_lease_partition', N'F', N'execution_lease'),
    (N'FK_ctl_execution_lease_execution', N'F', N'execution_lease'),
    (N'CK_ctl_execution_lease_expiry', N'C', N'execution_lease'),
    (N'CK_ctl_execution_lease_release', N'C', N'execution_lease'),
    (N'PK_ctl_execution_page_audit', N'PK', N'execution_page_audit'),
    (N'UQ_ctl_execution_page_audit_attempt', N'UQ', N'execution_page_audit'),
    (N'FK_ctl_execution_page_audit_execution', N'F', N'execution_page_audit'),
    (N'CK_ctl_execution_page_audit_values', N'C', N'execution_page_audit'),
    (N'PK_ctl_execution_count', N'PK', N'execution_count'),
    (N'UQ_ctl_execution_count_phase', N'UQ', N'execution_count'),
    (N'FK_ctl_execution_count_execution', N'F', N'execution_count'),
    (N'CK_ctl_execution_count_phase_non_blank', N'C', N'execution_count'),
    (N'CK_ctl_execution_count_equation', N'C', N'execution_count'),
    (N'PK_ctl_source_watermark_observation', N'PK', N'source_watermark_observation'),
    (N'FK_ctl_source_watermark_observation_partition', N'F', N'source_watermark_observation'),
    (N'CK_ctl_source_watermark_observation_name_non_blank', N'C', N'source_watermark_observation'),
    (N'CK_ctl_source_watermark_observation_fingerprint', N'C', N'source_watermark_observation'),
    (N'PK_ctl_partition_publication_pointer', N'PK', N'partition_publication_pointer'),
    (N'UQ_ctl_partition_publication_pointer_execution', N'UQ', N'partition_publication_pointer'),
    (N'FK_ctl_partition_publication_pointer_partition', N'F', N'partition_publication_pointer'),
    (N'FK_ctl_partition_publication_pointer_execution', N'F', N'partition_publication_pointer'),
    (N'FK_ctl_partition_publication_pointer_partition_execution', N'F', N'partition_publication_pointer'),
    (N'PK_ctl_execution_publication_event', N'PK', N'execution_publication_event'),
    (N'FK_ctl_execution_publication_event_execution', N'F', N'execution_publication_event'),
    (N'FK_ctl_execution_publication_event_partition_execution', N'F', N'execution_publication_event'),
    (N'FK_ctl_execution_publication_event_partition', N'F', N'execution_publication_event'),
    (N'FK_ctl_execution_publication_event_previous_execution', N'F', N'execution_publication_event'),
    (N'FK_ctl_execution_publication_event_watermark_partition', N'F', N'execution_publication_event'),
    (N'CK_ctl_execution_publication_event_frontier', N'C', N'execution_publication_event'),
    (N'PK_ctl_incremental_publication_watermark', N'PK', N'incremental_publication_watermark'),
    (N'FK_ctl_incremental_publication_watermark_source', N'F', N'incremental_publication_watermark'),
    (N'FK_ctl_incremental_publication_watermark_last_partition', N'F', N'incremental_publication_watermark'),
    (N'CK_ctl_incremental_publication_watermark_non_blank', N'C', N'incremental_publication_watermark'),
    (N'PK_ctl_execution_promotion_result', N'PK', N'execution_promotion_result'),
    (N'FK_ctl_execution_promotion_result_execution', N'F', N'execution_promotion_result'),
    (N'CK_ctl_execution_promotion_result_counts', N'C', N'execution_promotion_result');

INSERT INTO @failures (category, object_name, detail)
SELECT N'CONSTRAINT', expected.constraint_name, N'Constraint obrigatória ausente ou associada à tabela errada.'
FROM @expected_constraints AS expected
LEFT JOIN sys.objects AS object_definition
    ON object_definition.name COLLATE DATABASE_DEFAULT = expected.constraint_name
   AND object_definition.type COLLATE DATABASE_DEFAULT = expected.object_type
LEFT JOIN sys.schemas AS schema_definition
    ON schema_definition.schema_id = object_definition.schema_id
WHERE object_definition.object_id IS NULL
   OR schema_definition.name COLLATE DATABASE_DEFAULT <> N'ctl'
   OR OBJECT_NAME(object_definition.parent_object_id) COLLATE DATABASE_DEFAULT <> expected.parent_table;

INSERT INTO @failures (category, object_name, detail)
SELECT N'CONSTRAINT', CONCAT(N'ctl.', OBJECT_NAME(object_definition.parent_object_id), N'.', object_definition.name),
       N'Constraint fora do contrato V001-V009.'
FROM sys.objects AS object_definition
INNER JOIN sys.schemas AS schema_definition
    ON schema_definition.schema_id = object_definition.schema_id
WHERE schema_definition.name COLLATE DATABASE_DEFAULT = N'ctl'
  AND object_definition.type IN (N'PK', N'UQ', N'F', N'C', N'D')
  AND OBJECT_NAME(object_definition.parent_object_id) COLLATE DATABASE_DEFAULT NOT IN (
      N'staging_retention_policy',
      N'staging_retention_policy_event',
      N'staging_legal_hold',
      N'staging_legal_hold_event',
      N'staging_lifecycle_plan',
      N'staging_lifecycle_plan_item',
      N'staging_lifecycle_purge_event',
      N'data_quality_policy',
      N'data_quality_check_policy',
      N'usuario_promotion_result'
  )
  AND NOT EXISTS (
      SELECT 1
      FROM @expected_constraints AS expected
      WHERE expected.constraint_name = object_definition.name COLLATE DATABASE_DEFAULT
        AND expected.object_type = object_definition.type COLLATE DATABASE_DEFAULT
        AND expected.parent_table = OBJECT_NAME(object_definition.parent_object_id) COLLATE DATABASE_DEFAULT
  );

INSERT INTO @failures (category, object_name, detail)
SELECT N'INDEX', N'UX_ctl_execution_lease_active_partition',
       N'Índice único filtrado da lease ativa está ausente ou diverge.'
WHERE NOT EXISTS (
    SELECT 1
    FROM sys.indexes AS index_definition
    WHERE index_definition.object_id = OBJECT_ID(N'ctl.execution_lease', N'U')
      AND index_definition.name COLLATE DATABASE_DEFAULT = N'UX_ctl_execution_lease_active_partition'
      AND index_definition.is_unique = 1
      AND index_definition.has_filter = 1
      AND REPLACE(REPLACE(REPLACE(REPLACE(
              index_definition.filter_definition COLLATE DATABASE_DEFAULT,
              N'[', N''), N']', N''), N'(', N''), N')', N'') =
          N'released_at_utc IS NULL'
);

DECLARE @expected_permissions TABLE (
    role_name SYSNAME COLLATE DATABASE_DEFAULT NOT NULL,
    permission_state CHAR(1) COLLATE DATABASE_DEFAULT NOT NULL,
    permission_class TINYINT NOT NULL,
    securable_schema SYSNAME COLLATE DATABASE_DEFAULT NOT NULL,
    securable_object SYSNAME COLLATE DATABASE_DEFAULT NOT NULL,
    permission_name NVARCHAR(128) COLLATE DATABASE_DEFAULT NOT NULL
);

INSERT INTO @failures (category, object_name, detail)
SELECT N'PRIVILEGE_BOUNDARY', N'v2_schema_owner',
       N'Owner compartilhado dos schemas V2 deve ser interno, sem login e sem dbo.'
WHERE NOT EXISTS (
          SELECT 1 FROM sys.database_principals
          WHERE name = N'v2_schema_owner'
            AND type = N'S'
            AND authentication_type_desc = N'NONE'
      )
   OR EXISTS (
          SELECT 1
          FROM (VALUES (N'ctl'), (N'stg'), (N'core'), (N'ref'),
                       (N'mart'), (N'pub'), (N'recon')) AS expected (schema_name)
          INNER JOIN sys.schemas AS schema_definition
              ON schema_definition.name = expected.schema_name
          WHERE schema_definition.principal_id <>
              DATABASE_PRINCIPAL_ID(N'v2_schema_owner')
      );

INSERT INTO @failures (category, object_name, detail)
SELECT N'PRIVILEGE_BOUNDARY', schema_definition.name,
       N'Owner V2 sem login possui schema fora da allowlist exata.'
FROM sys.schemas AS schema_definition
WHERE schema_definition.principal_id = DATABASE_PRINCIPAL_ID(N'v2_schema_owner')
  AND schema_definition.name NOT IN (
      N'ctl', N'stg', N'core', N'ref', N'mart', N'pub', N'recon'
  );

INSERT INTO @failures (category, object_name, detail)
SELECT N'PRIVILEGE_BOUNDARY',
       CONCAT(schema_definition.name, N'.', object_definition.name),
       N'Objeto V2 sobrescreveu o owner restrito do schema.'
FROM sys.objects AS object_definition
INNER JOIN sys.schemas AS schema_definition
    ON schema_definition.schema_id = object_definition.schema_id
WHERE schema_definition.name IN (N'ctl', N'stg', N'core', N'ref', N'mart', N'pub', N'recon')
  AND object_definition.is_ms_shipped = 0
  AND object_definition.principal_id IS NOT NULL;

INSERT INTO @failures (category, object_name, detail)
SELECT N'PRIVILEGE_BOUNDARY', principal_definition.name,
       N'Owner V2 sem login não pode possuir principal ou role.'
FROM sys.database_principals AS principal_definition
WHERE principal_definition.owning_principal_id =
      DATABASE_PRINCIPAL_ID(N'v2_schema_owner');

INSERT INTO @failures (category, object_name, detail)
SELECT N'ROLE', expected.role_name,
       N'Role V2 ausente, com tipo divergente ou owner diferente de dbo.'
FROM (VALUES
    (N'v2_migrator'), (N'v2_runtime'), (N'v2_retention_governor'),
    (N'v2_lifecycle_reviewer'), (N'v2_lifecycle_operator'), (N'v2_archive_restorer')
) AS expected (role_name)
WHERE NOT EXISTS (
    SELECT 1 FROM sys.database_principals AS role_definition
    WHERE role_definition.name = expected.role_name
      AND role_definition.type = N'R'
      AND role_definition.owning_principal_id = DATABASE_PRINCIPAL_ID(N'dbo')
);

INSERT INTO @failures (category, object_name, detail)
SELECT N'PRIVILEGE_BOUNDARY', schema_definition.name,
       N'Role V2 não pode possuir schema e adquirir CONTROL implícito.'
FROM sys.schemas AS schema_definition
INNER JOIN sys.database_principals AS owner_definition
    ON owner_definition.principal_id = schema_definition.principal_id
WHERE owner_definition.name IN (
    N'v2_migrator', N'v2_runtime', N'v2_retention_governor',
    N'v2_lifecycle_reviewer', N'v2_lifecycle_operator', N'v2_archive_restorer'
);

INSERT INTO @failures (category, object_name, detail)
SELECT N'PRIVILEGE_BOUNDARY', principal_definition.name,
       N'Role V2 não pode possuir outro principal.'
FROM sys.database_principals AS principal_definition
INNER JOIN sys.database_principals AS owner_definition
    ON owner_definition.principal_id = principal_definition.owning_principal_id
WHERE owner_definition.name IN (
    N'v2_migrator', N'v2_runtime', N'v2_retention_governor',
    N'v2_lifecycle_reviewer', N'v2_lifecycle_operator', N'v2_archive_restorer'
);

INSERT INTO @failures (category, object_name, detail)
SELECT N'PRIVILEGE_BOUNDARY',
       CONCAT(schema_definition.name, N'.', object_definition.name),
       N'Role V2 não pode possuir objeto e adquirir CONTROL implícito.'
FROM sys.objects AS object_definition
INNER JOIN sys.schemas AS schema_definition
    ON schema_definition.schema_id = object_definition.schema_id
INNER JOIN sys.database_principals AS owner_definition
    ON owner_definition.principal_id = object_definition.principal_id
WHERE object_definition.is_ms_shipped = 0
  AND owner_definition.name IN (
      N'v2_migrator', N'v2_runtime', N'v2_retention_governor',
      N'v2_lifecycle_reviewer', N'v2_lifecycle_operator', N'v2_archive_restorer'
  );

DECLARE @expected_grant_allowlist TABLE (
    schema_name SYSNAME COLLATE Latin1_General_100_BIN2 NOT NULL,
    procedure_name SYSNAME COLLATE Latin1_General_100_BIN2 NOT NULL,
    role_name SYSNAME COLLATE Latin1_General_100_BIN2 NOT NULL,
    PRIMARY KEY (schema_name, procedure_name, role_name)
);
INSERT INTO @expected_grant_allowlist (schema_name, procedure_name, role_name)
VALUES
    (N'ctl', N'usp_control_plane_register_source', N'v2_runtime'),
    (N'ctl', N'usp_control_plane_start_cycle', N'v2_runtime'),
    (N'ctl', N'usp_control_plane_start_execution', N'v2_runtime'),
    (N'ctl', N'usp_control_plane_heartbeat_lease', N'v2_runtime'),
    (N'ctl', N'usp_control_plane_record_page', N'v2_runtime'),
    (N'ctl', N'usp_control_plane_record_counts', N'v2_runtime'),
    (N'ctl', N'usp_control_plane_transition_execution', N'v2_runtime'),
    (N'ctl', N'usp_control_plane_recover_stale_executions', N'v2_runtime'),
    (N'stg', N'usp_stage_record', N'v2_runtime'),
    (N'core', N'usp_prepare_staged_execution', N'v2_runtime'),
    (N'core', N'usp_apply_reconcile_publish_execution', N'v2_runtime'),
    (N'stg', N'usp_stage_usuario_record', N'v2_runtime'),
    (N'core', N'usp_apply_reconcile_publish_usuarios', N'v2_runtime'),
    (N'ctl', N'usp_approve_staging_retention_policy', N'v2_retention_governor'),
    (N'ctl', N'usp_revoke_staging_retention_policy', N'v2_retention_governor'),
    (N'ctl', N'usp_place_staging_legal_hold', N'v2_retention_governor'),
    (N'ctl', N'usp_release_staging_legal_hold', N'v2_retention_governor'),
    (N'stg', N'usp_plan_staging_lifecycle', N'v2_lifecycle_reviewer'),
    (N'stg', N'usp_archive_staging_lifecycle', N'v2_lifecycle_operator'),
    (N'stg', N'usp_purge_staging_lifecycle', N'v2_lifecycle_operator'),
    (N'recon', N'usp_restore_staging_archive', N'v2_archive_restorer'),
    (N'recon', N'usp_evaluate_execution_data_quality', N'v2_runtime'),
    (N'recon', N'usp_observe_execution_data_quality', N'v2_runtime'),
    (N'recon', N'usp_record_execution_metric', N'v2_runtime'),
    (N'recon', N'usp_raise_observability_alert', N'v2_runtime'),
    (N'ctl', N'usp_observe_platform_health', N'v2_runtime');

INSERT INTO @expected_permissions (
    role_name, permission_state, permission_class, securable_schema, securable_object, permission_name
)
VALUES
    (N'v2_migrator', N'G', 0, N'', N'', N'CREATE FUNCTION'),
    (N'v2_migrator', N'G', 0, N'', N'', N'CREATE PROCEDURE'),
    (N'v2_migrator', N'G', 0, N'', N'', N'CREATE TABLE'),
    (N'v2_migrator', N'G', 0, N'', N'', N'CREATE TYPE'),
    (N'v2_migrator', N'G', 0, N'', N'', N'CREATE VIEW'),
    (N'v2_migrator', N'G', 3, N'ctl', N'', N'ALTER'),
    (N'v2_migrator', N'G', 3, N'ctl', N'', N'REFERENCES'),
    (N'v2_migrator', N'G', 3, N'ctl', N'', N'SELECT'),
    (N'v2_migrator', N'G', 3, N'ctl', N'', N'INSERT'),
    (N'v2_migrator', N'G', 3, N'ctl', N'', N'UPDATE'),
    (N'v2_migrator', N'G', 3, N'ctl', N'', N'DELETE'),
    (N'v2_migrator', N'G', 3, N'stg', N'', N'ALTER'),
    (N'v2_migrator', N'G', 3, N'stg', N'', N'REFERENCES'),
    (N'v2_migrator', N'G', 3, N'core', N'', N'ALTER'),
    (N'v2_migrator', N'G', 3, N'core', N'', N'REFERENCES'),
    (N'v2_migrator', N'G', 3, N'ref', N'', N'ALTER'),
    (N'v2_migrator', N'G', 3, N'ref', N'', N'REFERENCES'),
    (N'v2_migrator', N'G', 3, N'mart', N'', N'ALTER'),
    (N'v2_migrator', N'G', 3, N'mart', N'', N'REFERENCES'),
    (N'v2_migrator', N'G', 3, N'pub', N'', N'ALTER'),
    (N'v2_migrator', N'G', 3, N'pub', N'', N'REFERENCES'),
    (N'v2_migrator', N'G', 3, N'recon', N'', N'ALTER'),
    (N'v2_migrator', N'G', 3, N'recon', N'', N'REFERENCES'),
    (N'v2_migrator', N'D', 3, N'dbo', N'', N'ALTER'),
    (N'v2_migrator', N'D', 4, N'dbo', N'', N'IMPERSONATE'),
    (N'v2_migrator', N'D', 4, N'v2_schema_owner', N'', N'IMPERSONATE'),
    (N'v2_migrator', N'G', 1, N'dbo', N'usp_publish_v2_procedure_grant', N'EXECUTE'),
    (N'v2_migrator', N'D', 1, N'dbo', N'usp_publish_v2_procedure_grant', N'ALTER'),
    (N'v2_migrator', N'D', 1, N'dbo', N'usp_publish_v2_procedure_grant', N'TAKE OWNERSHIP'),
    (N'v2_schema_owner', N'D', 4, N'dbo', N'', N'IMPERSONATE'),
    (N'v2_schema_owner', N'G', 0, N'', N'', N'CONNECT'),
    (N'public', N'D', 4, N'dbo', N'', N'IMPERSONATE'),
    (N'public', N'D', 4, N'v2_schema_owner', N'', N'IMPERSONATE'),
    (N'public', N'D', 1, N'dbo', N'v2_procedure_grant_allowlist', N'SELECT'),
    (N'public', N'D', 1, N'dbo', N'v2_procedure_grant_allowlist', N'INSERT'),
    (N'public', N'D', 1, N'dbo', N'v2_procedure_grant_allowlist', N'UPDATE'),
    (N'public', N'D', 1, N'dbo', N'v2_procedure_grant_allowlist', N'DELETE'),
    (N'public', N'D', 1, N'dbo', N'v2_procedure_grant_allowlist', N'ALTER'),
    (N'public', N'D', 1, N'dbo', N'v2_procedure_grant_allowlist', N'TAKE OWNERSHIP'),
    (N'public', N'D', 1, N'dbo', N'usp_publish_v2_procedure_grant', N'ALTER'),
    (N'public', N'D', 1, N'dbo', N'usp_publish_v2_procedure_grant', N'TAKE OWNERSHIP'),
    (N'v2_runtime', N'G', 0, N'', N'', N'CONNECT'),
    (N'v2_runtime', N'D', 0, N'', N'', N'ALTER ANY ROLE'),
    (N'v2_runtime', N'D', 0, N'', N'', N'ALTER ANY SCHEMA'),
    (N'v2_runtime', N'D', 0, N'', N'', N'CREATE FUNCTION'),
    (N'v2_runtime', N'D', 0, N'', N'', N'CREATE PROCEDURE'),
    (N'v2_runtime', N'D', 0, N'', N'', N'CREATE RULE'),
    (N'v2_runtime', N'D', 0, N'', N'', N'CREATE SCHEMA'),
    (N'v2_runtime', N'D', 0, N'', N'', N'CREATE SYNONYM'),
    (N'v2_runtime', N'D', 0, N'', N'', N'CREATE TABLE'),
    (N'v2_runtime', N'D', 0, N'', N'', N'CREATE TYPE'),
    (N'v2_runtime', N'D', 0, N'', N'', N'CREATE VIEW'),
    (N'v2_runtime', N'D', 0, N'', N'', N'CREATE XML SCHEMA COLLECTION'),
    (N'v2_runtime', N'D', 3, N'ctl', N'', N'ALTER'),
    (N'v2_runtime', N'D', 3, N'stg', N'', N'ALTER'),
    (N'v2_runtime', N'D', 3, N'core', N'', N'ALTER'),
    (N'v2_runtime', N'D', 3, N'ref', N'', N'ALTER'),
    (N'v2_runtime', N'D', 3, N'mart', N'', N'ALTER'),
    (N'v2_runtime', N'D', 3, N'pub', N'', N'ALTER'),
    (N'v2_runtime', N'D', 3, N'recon', N'', N'ALTER'),
    (N'v2_runtime', N'D', 3, N'ctl', N'', N'SELECT'),
    (N'v2_runtime', N'D', 3, N'ctl', N'', N'INSERT'),
    (N'v2_runtime', N'D', 3, N'ctl', N'', N'UPDATE'),
    (N'v2_runtime', N'D', 3, N'ctl', N'', N'DELETE'),
    (N'v2_runtime', N'D', 3, N'stg', N'', N'SELECT'),
    (N'v2_runtime', N'D', 3, N'stg', N'', N'INSERT'),
    (N'v2_runtime', N'D', 3, N'stg', N'', N'UPDATE'),
    (N'v2_runtime', N'D', 3, N'stg', N'', N'DELETE'),
    (N'v2_runtime', N'D', 3, N'core', N'', N'SELECT'),
    (N'v2_runtime', N'D', 3, N'core', N'', N'INSERT'),
    (N'v2_runtime', N'D', 3, N'core', N'', N'UPDATE'),
    (N'v2_runtime', N'D', 3, N'core', N'', N'DELETE'),
    (N'v2_runtime', N'D', 3, N'recon', N'', N'SELECT'),
    (N'v2_runtime', N'D', 3, N'recon', N'', N'INSERT'),
    (N'v2_runtime', N'D', 3, N'recon', N'', N'UPDATE'),
    (N'v2_runtime', N'D', 3, N'recon', N'', N'DELETE'),
    (N'v2_runtime', N'G', 1, N'ctl', N'usp_control_plane_register_source', N'EXECUTE'),
    (N'v2_runtime', N'G', 1, N'ctl', N'usp_control_plane_start_cycle', N'EXECUTE'),
    (N'v2_runtime', N'G', 1, N'ctl', N'usp_control_plane_start_execution', N'EXECUTE'),
    (N'v2_runtime', N'G', 1, N'ctl', N'usp_control_plane_heartbeat_lease', N'EXECUTE'),
    (N'v2_runtime', N'G', 1, N'ctl', N'usp_control_plane_record_page', N'EXECUTE'),
    (N'v2_runtime', N'G', 1, N'ctl', N'usp_control_plane_record_counts', N'EXECUTE'),
    (N'v2_runtime', N'G', 1, N'ctl', N'usp_control_plane_transition_execution', N'EXECUTE'),
    (N'v2_runtime', N'G', 1, N'ctl', N'usp_control_plane_recover_stale_executions', N'EXECUTE'),
    (N'v2_runtime', N'G', 1, N'stg', N'usp_stage_record', N'EXECUTE'),
    (N'v2_runtime', N'G', 1, N'stg', N'usp_stage_usuario_record', N'EXECUTE'),
    (N'v2_runtime', N'G', 1, N'core', N'usp_prepare_staged_execution', N'EXECUTE'),
    (N'v2_runtime', N'G', 1, N'core', N'usp_apply_reconcile_publish_execution', N'EXECUTE'),
    (N'v2_runtime', N'G', 1, N'core', N'usp_apply_reconcile_publish_usuarios', N'EXECUTE'),
    (N'v2_runtime', N'G', 1, N'recon', N'usp_evaluate_execution_data_quality', N'EXECUTE'),
    (N'v2_runtime', N'G', 1, N'recon', N'usp_observe_execution_data_quality', N'EXECUTE'),
    (N'v2_runtime', N'G', 1, N'recon', N'usp_record_execution_metric', N'EXECUTE'),
    (N'v2_runtime', N'G', 1, N'recon', N'usp_raise_observability_alert', N'EXECUTE'),
    (N'v2_runtime', N'G', 1, N'ctl', N'usp_observe_platform_health', N'EXECUTE');

INSERT INTO @failures (category, object_name, detail)
SELECT N'PERMISSION', CONCAT(expected.role_name, N':', expected.permission_name),
       N'Permissão obrigatória ausente.'
FROM @expected_permissions AS expected
WHERE NOT EXISTS (
    SELECT 1
    FROM sys.database_permissions AS permission_definition
    INNER JOIN sys.database_principals AS principal_definition
        ON principal_definition.principal_id = permission_definition.grantee_principal_id
    WHERE principal_definition.name COLLATE DATABASE_DEFAULT = expected.role_name
      AND permission_definition.state COLLATE DATABASE_DEFAULT = expected.permission_state
      AND permission_definition.class = expected.permission_class
      AND permission_definition.permission_name COLLATE DATABASE_DEFAULT = expected.permission_name
      AND (expected.permission_class <> 1 OR permission_definition.minor_id = 0)
      AND (
          (expected.permission_class = 0 AND permission_definition.major_id = 0)
          OR (
              expected.permission_class = 3
              AND permission_definition.major_id = SCHEMA_ID(expected.securable_schema)
          )
          OR (
              expected.permission_class = 1
              AND permission_definition.major_id = OBJECT_ID(
                  CONCAT(
                      expected.securable_schema COLLATE DATABASE_DEFAULT,
                      N'.',
                      expected.securable_object COLLATE DATABASE_DEFAULT
                  )
              )
          )
          OR (
              expected.permission_class = 4
              AND permission_definition.major_id =
                  DATABASE_PRINCIPAL_ID(expected.securable_schema)
          )
      )
);

INSERT INTO @failures (category, object_name, detail)
SELECT N'PERMISSION', CONCAT(principal_definition.name, N':', permission_definition.permission_name),
       N'Permissão direta fora do contrato mínimo V001-V009.'
FROM sys.database_permissions AS permission_definition
INNER JOIN sys.database_principals AS principal_definition
    ON principal_definition.principal_id = permission_definition.grantee_principal_id
WHERE principal_definition.name COLLATE DATABASE_DEFAULT IN (
        N'v2_migrator', N'v2_runtime', N'v2_schema_owner'
      )
  AND NOT EXISTS (
      SELECT 1
      FROM @expected_permissions AS expected
      WHERE expected.role_name = principal_definition.name COLLATE DATABASE_DEFAULT
        AND expected.permission_state = permission_definition.state COLLATE DATABASE_DEFAULT
        AND expected.permission_class = permission_definition.class
        AND expected.permission_name = permission_definition.permission_name COLLATE DATABASE_DEFAULT
        AND (expected.permission_class <> 1 OR permission_definition.minor_id = 0)
        AND (
            (expected.permission_class = 0 AND permission_definition.major_id = 0)
            OR (
                expected.permission_class = 3
                AND permission_definition.major_id = SCHEMA_ID(expected.securable_schema)
            )
            OR (
                expected.permission_class = 1
              AND permission_definition.major_id = OBJECT_ID(
                  CONCAT(
                      expected.securable_schema COLLATE DATABASE_DEFAULT,
                      N'.',
                      expected.securable_object COLLATE DATABASE_DEFAULT
                  )
                )
            )
            OR (
                expected.permission_class = 4
                AND permission_definition.major_id =
                    DATABASE_PRINCIPAL_ID(expected.securable_schema)
            )
        )
  );

INSERT INTO @failures (category, object_name, detail)
SELECT N'PRIVILEGE_BOUNDARY', permission_definition.permission_name,
       N'Grant público permite escalada do owner-context restrito.'
FROM sys.database_permissions AS permission_definition
INNER JOIN sys.database_principals AS principal_definition
    ON principal_definition.principal_id = permission_definition.grantee_principal_id
WHERE principal_definition.name = N'public'
  AND permission_definition.state IN (N'G', N'W')
  AND permission_definition.permission_name IN (
      N'CONTROL', N'ALTER ANY ROLE', N'ALTER ANY USER',
      N'IMPERSONATE ANY USER', N'TAKE OWNERSHIP', N'CREATE SCHEMA'
  );

INSERT INTO @failures (category, object_name, detail)
SELECT N'PRIVILEGE_BOUNDARY', USER_NAME(permission_definition.major_id),
       N'Grant público permite impersonar dbo ou o owner restrito.'
FROM sys.database_permissions AS permission_definition
WHERE permission_definition.grantee_principal_id = DATABASE_PRINCIPAL_ID(N'public')
  AND permission_definition.state IN (N'G', N'W')
  AND permission_definition.class = 4
  AND permission_definition.permission_name = N'IMPERSONATE'
  AND permission_definition.major_id IN (
      DATABASE_PRINCIPAL_ID(N'dbo'), DATABASE_PRINCIPAL_ID(N'v2_schema_owner')
  );

INSERT INTO @failures (category, object_name, detail)
SELECT N'PRIVILEGE_BOUNDARY', SCHEMA_NAME(permission_definition.major_id),
       N'Grant público permite controlar ou tomar ownership de schema V2.'
FROM sys.database_permissions AS permission_definition
WHERE permission_definition.grantee_principal_id = DATABASE_PRINCIPAL_ID(N'public')
  AND permission_definition.state IN (N'G', N'W')
  AND permission_definition.class = 3
  AND permission_definition.permission_name IN (N'ALTER', N'CONTROL', N'TAKE OWNERSHIP')
  AND SCHEMA_NAME(permission_definition.major_id) IN (
      N'ctl', N'stg', N'core', N'ref', N'mart', N'pub', N'recon'
  );

INSERT INTO @failures (category, object_name, detail)
SELECT N'PRIVILEGE_BOUNDARY', N'dbo.v2_procedure_grant_allowlist',
       N'Allowlist imutável possui GRANT positivo explícito fora de dbo.'
FROM sys.database_permissions AS permission_definition
WHERE permission_definition.class = 1
  AND permission_definition.major_id = OBJECT_ID(N'dbo.v2_procedure_grant_allowlist', N'U')
  AND permission_definition.state IN (N'G', N'W')
  AND permission_definition.grantee_principal_id <> DATABASE_PRINCIPAL_ID(N'dbo');

INSERT INTO @failures (category, object_name, detail)
SELECT N'PRIVILEGE_BOUNDARY', N'dbo.usp_publish_v2_procedure_grant',
       N'Publicador possui GRANT positivo além do EXECUTE exato do migrator.'
FROM sys.database_permissions AS permission_definition
INNER JOIN sys.database_principals AS principal_definition
    ON principal_definition.principal_id = permission_definition.grantee_principal_id
WHERE permission_definition.class = 1
  AND permission_definition.major_id = OBJECT_ID(N'dbo.usp_publish_v2_procedure_grant', N'P')
  AND permission_definition.state IN (N'G', N'W')
  AND principal_definition.name <> N'dbo'
  AND NOT (
      principal_definition.name = N'v2_migrator'
      AND permission_definition.permission_name = N'EXECUTE'
      AND permission_definition.minor_id = 0
  );

INSERT INTO @failures (category, object_name, detail)
SELECT N'PRIVILEGE_BOUNDARY', N'dbo.v2_procedure_grant_allowlist',
       N'Allowlist de grants ausente, com shape/owner/conjunto inexato.'
WHERE OBJECT_ID(N'dbo.v2_procedure_grant_allowlist', N'U') IS NULL
   OR COALESCE(
          (SELECT principal_id FROM sys.objects
           WHERE object_id = OBJECT_ID(N'dbo.v2_procedure_grant_allowlist', N'U')),
          (SELECT principal_id FROM sys.schemas WHERE name = N'dbo')
      ) <> DATABASE_PRINCIPAL_ID(N'dbo')
   OR (SELECT COUNT_BIG(*) FROM dbo.v2_procedure_grant_allowlist) <> 26
   OR (SELECT COUNT_BIG(*) FROM sys.columns
       WHERE object_id = OBJECT_ID(N'dbo.v2_procedure_grant_allowlist', N'U')) <> 3
   OR EXISTS (
          SELECT 1
          FROM (VALUES
              (1, N'schema_name'), (2, N'procedure_name'), (3, N'role_name')
          ) AS expected_column (column_id, column_name)
          WHERE NOT EXISTS (
              SELECT 1 FROM sys.columns AS column_definition
              WHERE column_definition.object_id =
                    OBJECT_ID(N'dbo.v2_procedure_grant_allowlist', N'U')
                AND column_definition.column_id = expected_column.column_id
                AND column_definition.name = expected_column.column_name
                AND TYPE_NAME(column_definition.user_type_id) = N'sysname'
                AND column_definition.max_length = 256
                AND column_definition.is_nullable = 0
                AND column_definition.collation_name = N'Latin1_General_100_BIN2'
          )
      )
   OR NOT EXISTS (
          SELECT 1 FROM sys.indexes AS index_definition
          WHERE index_definition.object_id =
                OBJECT_ID(N'dbo.v2_procedure_grant_allowlist', N'U')
            AND index_definition.name = N'PK_dbo_v2_procedure_grant_allowlist'
            AND index_definition.is_primary_key = 1
            AND index_definition.is_unique = 1
            AND (SELECT COUNT_BIG(*) FROM sys.index_columns AS key_count
                 WHERE key_count.object_id = index_definition.object_id
                   AND key_count.index_id = index_definition.index_id
                   AND key_count.key_ordinal > 0) = 3
            AND NOT EXISTS (
                SELECT 1
                FROM (VALUES
                    (1, N'schema_name'), (2, N'procedure_name'), (3, N'role_name')
                ) AS expected_key (key_ordinal, column_name)
                WHERE NOT EXISTS (
                    SELECT 1 FROM sys.index_columns AS actual_key
                    INNER JOIN sys.columns AS actual_column
                        ON actual_column.object_id = actual_key.object_id
                       AND actual_column.column_id = actual_key.column_id
                    WHERE actual_key.object_id = index_definition.object_id
                      AND actual_key.index_id = index_definition.index_id
                      AND actual_key.key_ordinal = expected_key.key_ordinal
                      AND actual_column.name = expected_key.column_name
                )
            )
      )
   OR EXISTS (
          SELECT schema_name, procedure_name, role_name
          FROM @expected_grant_allowlist
          EXCEPT
          SELECT schema_name, procedure_name, role_name
          FROM dbo.v2_procedure_grant_allowlist
      )
   OR EXISTS (
          SELECT schema_name, procedure_name, role_name
          FROM dbo.v2_procedure_grant_allowlist
          EXCEPT
          SELECT schema_name, procedure_name, role_name
          FROM @expected_grant_allowlist
      );

INSERT INTO @failures (category, object_name, detail)
SELECT N'PRIVILEGE_BOUNDARY', N'dbo.usp_publish_v2_procedure_grant',
       N'Publicador ausente, mutável pelo migrator, sem owner execution ou fora da allowlist exata.'
WHERE OBJECT_ID(N'dbo.usp_publish_v2_procedure_grant', N'P') IS NULL
   OR COALESCE(
          (SELECT principal_id FROM sys.objects
           WHERE object_id = OBJECT_ID(N'dbo.usp_publish_v2_procedure_grant', N'P')),
          (SELECT principal_id FROM sys.schemas WHERE name = N'dbo')
      ) <> DATABASE_PRINCIPAL_ID(N'dbo')
   OR NOT EXISTS (
       SELECT 1 FROM sys.sql_modules AS module_definition
       WHERE module_definition.object_id =
                 OBJECT_ID(N'dbo.usp_publish_v2_procedure_grant', N'P')
         AND module_definition.execute_as_principal_id = -2
         AND module_definition.definition IS NOT NULL
         AND module_definition.definition LIKE
             N'%FROM dbo.v2_procedure_grant_allowlist AS allowed_grant%'
         AND module_definition.definition LIKE N'%execute_as_principal_id IS NOT NULL%'
   );

INSERT INTO @failures (category, object_name, detail)
SELECT N'PRIVILEGE_BOUNDARY',
       CONCAT(schema_definition.name, N'.', object_definition.name),
       N'Módulo em schema alterável pelo migrator não pode usar EXECUTE AS.'
FROM sys.sql_modules AS module_definition
INNER JOIN sys.objects AS object_definition
    ON object_definition.object_id = module_definition.object_id
INNER JOIN sys.schemas AS schema_definition
    ON schema_definition.schema_id = object_definition.schema_id
WHERE schema_definition.name IN (N'ctl', N'stg', N'core', N'ref', N'mart', N'pub', N'recon')
  AND module_definition.execute_as_principal_id IS NOT NULL;

INSERT INTO @failures (category, object_name, detail)
SELECT N'ROLE_MEMBERSHIP', CONCAT(role_definition.name, N'->', member_definition.name),
       N'Role V2 não pode herdar privilégios por membership nesta fundação.'
FROM sys.database_role_members AS membership
INNER JOIN sys.database_principals AS role_definition
    ON role_definition.principal_id = membership.role_principal_id
INNER JOIN sys.database_principals AS member_definition
    ON member_definition.principal_id = membership.member_principal_id
WHERE role_definition.name COLLATE DATABASE_DEFAULT IN (
        N'v2_migrator', N'v2_runtime', N'v2_retention_governor',
        N'v2_lifecycle_reviewer', N'v2_lifecycle_operator', N'v2_archive_restorer'
      )
   OR member_definition.name COLLATE DATABASE_DEFAULT IN (
        N'v2_migrator', N'v2_runtime', N'v2_retention_governor',
        N'v2_lifecycle_reviewer', N'v2_lifecycle_operator', N'v2_archive_restorer'
      );

INSERT INTO @failures (category, object_name, detail)
SELECT N'ROLE_MEMBERSHIP', CONCAT(role_definition.name, N'->', member_definition.name),
       N'Owner de schema sem login não pode participar de roles nem possuir membros.'
FROM sys.database_role_members AS membership
INNER JOIN sys.database_principals AS role_definition
    ON role_definition.principal_id = membership.role_principal_id
INNER JOIN sys.database_principals AS member_definition
    ON member_definition.principal_id = membership.member_principal_id
WHERE role_definition.name COLLATE DATABASE_DEFAULT = N'v2_schema_owner'
   OR member_definition.name COLLATE DATABASE_DEFAULT = N'v2_schema_owner';

IF EXISTS (SELECT 1 FROM @failures)
BEGIN
    SELECT category, object_name, detail
    FROM @failures
    ORDER BY category, object_name;
    THROW 51340, N'Gate progressivo de dados V2 divergente do contrato.', 1;
END;

PRINT N'Gate progressivo de dados V2 validado com sucesso.';
