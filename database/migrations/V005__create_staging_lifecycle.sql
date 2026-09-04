-- V2-045a: lifecycle governado do staging técnico.
-- Nenhum prazo é semeado por esta migration: políticas somente se tornam utilizáveis depois de
-- aprovação explícita registrada pelo papel de governança. O arquivo é lógico, tipado e local ao
-- banco V2; ele não comprova cold storage, backup físico, criptografia ou RTO/RPO.

SET XACT_ABORT ON;
SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;

ALTER TABLE ctl.execution_attempt WITH CHECK
ADD CONSTRAINT CK_ctl_execution_attempt_terminal_timestamp CHECK (
    (
        current_state IN (
            N'PUBLISHED', N'BLOCKED', N'SKIPPED', N'NOT_APPLICABLE',
            N'FAILED', N'CANCELLED', N'DEGRADED'
        )
        AND terminal_at_utc IS NOT NULL
        AND terminal_at_utc >= started_at_utc
    )
    OR (
        current_state IN (
            N'PLANNED', N'EXTRACTING', N'EXTRACTED', N'STAGED', N'PROMOTED', N'RECONCILED'
        )
        AND terminal_at_utc IS NULL
    )
);
GO

ALTER TABLE ctl.execution_attempt WITH CHECK
ADD CONSTRAINT CK_ctl_execution_attempt_transition_sequence_lifecycle_bound CHECK (
    next_transition_sequence BETWEEN 3 AND 8
);
GO

ALTER TABLE ctl.execution_state_event WITH CHECK
ADD CONSTRAINT CK_ctl_execution_state_event_lifecycle_bound CHECK (
    transition_sequence BETWEEN 1 AND 7
);
GO

CREATE INDEX IX_ctl_execution_attempt_lifecycle
    ON ctl.execution_attempt (partition_id, current_state, terminal_at_utc, execution_id);
GO

CREATE INDEX IX_stg_execution_record_lifecycle
    ON stg.execution_record (execution_id, stage_record_id);
GO

CREATE INDEX IX_recon_quarantine_record_lifecycle
    ON recon.quarantine_record (execution_id, quarantine_record_id);
GO

-- Contrato de extensão vertical do budget. Cada migration vertical pode substituir esta iTVF por
-- uma agregação set-based correlacionada, obrigada a examinar no máximo maximum_extension_rows+1.
-- O placeholder vazio mantém o planner comum independente de qualquer entidade específica.
CREATE FUNCTION recon.ufn_staging_lifecycle_extension_archive_budget (
    @execution_id UNIQUEIDENTIFIER,
    @maximum_extension_rows BIGINT
)
RETURNS TABLE
AS
RETURN (
    SELECT CONVERT(BIGINT, 0) AS extension_rows,
           CONVERT(BIGINT, 0) AS additional_archive_bytes
);
GO

CREATE TABLE ctl.staging_retention_policy (
    policy_id UNIQUEIDENTIFIER NOT NULL,
    environment_name NVARCHAR(32) COLLATE Latin1_General_100_BIN2 NOT NULL,
    source_instance NVARCHAR(128) COLLATE Latin1_General_100_BIN2 NOT NULL,
    tenant_scope NVARCHAR(128) COLLATE Latin1_General_100_BIN2 NOT NULL,
    entity_name NVARCHAR(128) COLLATE Latin1_General_100_BIN2 NOT NULL,
    terminal_outcome_class NVARCHAR(32) COLLATE Latin1_General_100_BIN2 NOT NULL,
    policy_version NVARCHAR(128) COLLATE Latin1_General_100_BIN2 NOT NULL,
    policy_fingerprint CHAR(64) NOT NULL,
    retention_days INT NOT NULL,
    data_owner_evidence_fingerprint CHAR(64) NOT NULL,
    data_owner_role NVARCHAR(128) COLLATE Latin1_General_100_BIN2 NOT NULL,
    compliance_evidence_fingerprint CHAR(64) NOT NULL,
    compliance_owner_role NVARCHAR(128) COLLATE Latin1_General_100_BIN2 NOT NULL,
    approved_at_utc DATETIME2(3) NOT NULL,
    revoked_at_utc DATETIME2(3) NULL,
    CONSTRAINT PK_ctl_staging_retention_policy PRIMARY KEY CLUSTERED (policy_id),
    CONSTRAINT UQ_ctl_staging_retention_policy_version UNIQUE (
        environment_name, source_instance, tenant_scope, entity_name,
        terminal_outcome_class, policy_version
    ),
    CONSTRAINT FK_ctl_staging_retention_policy_source
        FOREIGN KEY (source_instance) REFERENCES ctl.source_catalog (source_instance),
    CONSTRAINT CK_ctl_staging_retention_policy_text CHECK (
        NULLIF(LTRIM(RTRIM(environment_name)), N'') IS NOT NULL
        AND NULLIF(LTRIM(RTRIM(source_instance)), N'') IS NOT NULL
        AND NULLIF(LTRIM(RTRIM(tenant_scope)), N'') IS NOT NULL
        AND NULLIF(LTRIM(RTRIM(entity_name)), N'') IS NOT NULL
        AND NULLIF(LTRIM(RTRIM(policy_version)), N'') IS NOT NULL
        AND NULLIF(LTRIM(RTRIM(data_owner_role)), N'') IS NOT NULL
        AND NULLIF(LTRIM(RTRIM(compliance_owner_role)), N'') IS NOT NULL
        AND DATALENGTH(environment_name) = DATALENGTH(LTRIM(RTRIM(environment_name)))
        AND DATALENGTH(source_instance) = DATALENGTH(LTRIM(RTRIM(source_instance)))
        AND DATALENGTH(tenant_scope) = DATALENGTH(LTRIM(RTRIM(tenant_scope)))
        AND DATALENGTH(entity_name) = DATALENGTH(LTRIM(RTRIM(entity_name)))
        AND DATALENGTH(policy_version) = DATALENGTH(LTRIM(RTRIM(policy_version)))
        AND DATALENGTH(data_owner_role) = DATALENGTH(LTRIM(RTRIM(data_owner_role)))
        AND DATALENGTH(compliance_owner_role) = DATALENGTH(LTRIM(RTRIM(compliance_owner_role)))
        AND data_owner_role COLLATE Latin1_General_100_BIN2 <>
            compliance_owner_role COLLATE Latin1_General_100_BIN2
    ),
    CONSTRAINT CK_ctl_staging_retention_policy_outcome CHECK (
        terminal_outcome_class IN (N'PUBLISHED', N'NON_PUBLISHED_TERMINAL')
    ),
    CONSTRAINT CK_ctl_staging_retention_policy_fingerprint CHECK (
        LEN(policy_fingerprint) = 64
        AND policy_fingerprint COLLATE Latin1_General_100_BIN2 NOT LIKE '%[^0-9A-Fa-f]%'
        AND LEN(data_owner_evidence_fingerprint) = 64
        AND data_owner_evidence_fingerprint COLLATE Latin1_General_100_BIN2
            NOT LIKE '%[^0-9A-Fa-f]%'
        AND LEN(compliance_evidence_fingerprint) = 64
        AND compliance_evidence_fingerprint COLLATE Latin1_General_100_BIN2
            NOT LIKE '%[^0-9A-Fa-f]%'
        AND data_owner_evidence_fingerprint COLLATE Latin1_General_100_BIN2 <>
            compliance_evidence_fingerprint COLLATE Latin1_General_100_BIN2
    ),
    CONSTRAINT CK_ctl_staging_retention_policy_days CHECK (retention_days BETWEEN 1 AND 36500),
    CONSTRAINT CK_ctl_staging_retention_policy_times CHECK (
        revoked_at_utc IS NULL OR revoked_at_utc >= approved_at_utc
    )
);
GO

CREATE UNIQUE INDEX UX_ctl_staging_retention_policy_active_scope
    ON ctl.staging_retention_policy (
        environment_name, source_instance, tenant_scope, entity_name, terminal_outcome_class
    )
    WHERE revoked_at_utc IS NULL;
GO

CREATE TABLE ctl.staging_retention_policy_event (
    policy_event_id BIGINT IDENTITY(1, 1) NOT NULL,
    policy_id UNIQUEIDENTIFIER NOT NULL,
    event_action NVARCHAR(32) COLLATE Latin1_General_100_BIN2 NOT NULL,
    authority_evidence_fingerprint CHAR(64) NOT NULL,
    owner_role NVARCHAR(128) COLLATE Latin1_General_100_BIN2 NOT NULL,
    occurred_at_utc DATETIME2(3) NOT NULL,
    CONSTRAINT PK_ctl_staging_retention_policy_event PRIMARY KEY CLUSTERED (policy_event_id),
    CONSTRAINT UQ_ctl_staging_retention_policy_event_action UNIQUE (policy_id, event_action),
    CONSTRAINT FK_ctl_staging_retention_policy_event_policy
        FOREIGN KEY (policy_id) REFERENCES ctl.staging_retention_policy (policy_id),
    CONSTRAINT CK_ctl_staging_retention_policy_event_action CHECK (
        event_action IN (N'DATA_OWNER_APPROVED', N'COMPLIANCE_APPROVED', N'REVOKED')
    ),
    CONSTRAINT CK_ctl_staging_retention_policy_event_text CHECK (
        NULLIF(LTRIM(RTRIM(owner_role)), N'') IS NOT NULL
        AND DATALENGTH(owner_role) = DATALENGTH(LTRIM(RTRIM(owner_role)))
        AND LEN(authority_evidence_fingerprint) = 64
        AND authority_evidence_fingerprint COLLATE Latin1_General_100_BIN2
            NOT LIKE '%[^0-9A-Fa-f]%'
    )
);
GO

CREATE TABLE ctl.staging_legal_hold (
    hold_id UNIQUEIDENTIFIER NOT NULL,
    execution_id UNIQUEIDENTIFIER NOT NULL,
    reason_code NVARCHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL,
    authority_evidence_fingerprint CHAR(64) NOT NULL,
    owner_role NVARCHAR(128) COLLATE Latin1_General_100_BIN2 NOT NULL,
    hold_scope NVARCHAR(32) COLLATE Latin1_General_100_BIN2 NOT NULL,
    held_at_utc DATETIME2(3) NOT NULL,
    release_authority_evidence_fingerprint CHAR(64) NULL,
    release_owner_role NVARCHAR(128) COLLATE Latin1_General_100_BIN2 NULL,
    released_at_utc DATETIME2(3) NULL,
    CONSTRAINT PK_ctl_staging_legal_hold PRIMARY KEY CLUSTERED (hold_id),
    CONSTRAINT FK_ctl_staging_legal_hold_execution
        FOREIGN KEY (execution_id) REFERENCES ctl.execution_attempt (execution_id),
    CONSTRAINT CK_ctl_staging_legal_hold_reason CHECK (
        LEFT(reason_code, 1) COLLATE Latin1_General_100_BIN2 LIKE '[A-Z]'
        AND reason_code COLLATE Latin1_General_100_BIN2 NOT LIKE '%[^A-Z0-9_]%'
        AND LEN(reason_code) BETWEEN 2 AND 64
        AND DATALENGTH(reason_code) = DATALENGTH(LTRIM(RTRIM(reason_code)))
    ),
    CONSTRAINT CK_ctl_staging_legal_hold_text CHECK (
        NULLIF(LTRIM(RTRIM(owner_role)), N'') IS NOT NULL
        AND DATALENGTH(owner_role) = DATALENGTH(LTRIM(RTRIM(owner_role)))
        AND LEN(authority_evidence_fingerprint) = 64
        AND authority_evidence_fingerprint COLLATE Latin1_General_100_BIN2
            NOT LIKE '%[^0-9A-Fa-f]%'
    ),
    CONSTRAINT CK_ctl_staging_legal_hold_times CHECK (
        released_at_utc IS NULL OR released_at_utc >= held_at_utc
    ),
    CONSTRAINT CK_ctl_staging_legal_hold_release CHECK (
        (
            released_at_utc IS NULL
            AND release_authority_evidence_fingerprint IS NULL
            AND release_owner_role IS NULL
        )
        OR (
            released_at_utc IS NOT NULL
            AND release_authority_evidence_fingerprint IS NOT NULL
            AND LEN(release_authority_evidence_fingerprint) = 64
            AND release_authority_evidence_fingerprint COLLATE Latin1_General_100_BIN2
                NOT LIKE '%[^0-9A-Fa-f]%'
            AND release_owner_role IS NOT NULL
            AND NULLIF(LTRIM(RTRIM(release_owner_role)), N'') IS NOT NULL
            AND DATALENGTH(release_owner_role) =
                DATALENGTH(LTRIM(RTRIM(release_owner_role)))
        )
    ),
    CONSTRAINT CK_ctl_staging_legal_hold_scope CHECK (
        hold_scope IN (N'STAGING_AND_ARCHIVE', N'ARCHIVE_ONLY')
    )
);
GO

CREATE UNIQUE INDEX UX_ctl_staging_legal_hold_active_execution
    ON ctl.staging_legal_hold (execution_id)
    WHERE released_at_utc IS NULL;
GO

CREATE INDEX IX_ctl_staging_legal_hold_execution
    ON ctl.staging_legal_hold (execution_id, hold_id);
GO

CREATE TABLE ctl.staging_legal_hold_event (
    hold_event_id BIGINT IDENTITY(1, 1) NOT NULL,
    hold_id UNIQUEIDENTIFIER NOT NULL,
    execution_id UNIQUEIDENTIFIER NOT NULL,
    event_action NVARCHAR(16) COLLATE Latin1_General_100_BIN2 NOT NULL,
    reason_code NVARCHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL,
    authority_evidence_fingerprint CHAR(64) NOT NULL,
    owner_role NVARCHAR(128) COLLATE Latin1_General_100_BIN2 NOT NULL,
    hold_scope NVARCHAR(32) COLLATE Latin1_General_100_BIN2 NOT NULL,
    occurred_at_utc DATETIME2(3) NOT NULL,
    CONSTRAINT PK_ctl_staging_legal_hold_event PRIMARY KEY CLUSTERED (hold_event_id),
    CONSTRAINT UQ_ctl_staging_legal_hold_event_action UNIQUE (hold_id, event_action),
    CONSTRAINT FK_ctl_staging_legal_hold_event_hold
        FOREIGN KEY (hold_id) REFERENCES ctl.staging_legal_hold (hold_id),
    CONSTRAINT FK_ctl_staging_legal_hold_event_execution
        FOREIGN KEY (execution_id) REFERENCES ctl.execution_attempt (execution_id),
    CONSTRAINT CK_ctl_staging_legal_hold_event_action CHECK (
        event_action IN (N'PLACED', N'RELEASED')
    ),
    CONSTRAINT CK_ctl_staging_legal_hold_event_scope CHECK (
        hold_scope IN (N'STAGING_AND_ARCHIVE', N'ARCHIVE_ONLY')
    ),
    CONSTRAINT CK_ctl_staging_legal_hold_event_reason CHECK (
        LEFT(reason_code, 1) COLLATE Latin1_General_100_BIN2 LIKE '[A-Z]'
        AND reason_code COLLATE Latin1_General_100_BIN2 NOT LIKE '%[^A-Z0-9_]%'
        AND LEN(reason_code) BETWEEN 2 AND 64
        AND LEN(authority_evidence_fingerprint) = 64
        AND authority_evidence_fingerprint COLLATE Latin1_General_100_BIN2
            NOT LIKE '%[^0-9A-Fa-f]%'
    )
);
GO

CREATE INDEX IX_ctl_staging_legal_hold_event_execution
    ON ctl.staging_legal_hold_event (execution_id, hold_id, event_action);
GO

CREATE TABLE ctl.staging_lifecycle_plan (
    plan_id UNIQUEIDENTIFIER NOT NULL,
    policy_id UNIQUEIDENTIFIER NOT NULL,
    policy_version NVARCHAR(128) COLLATE Latin1_General_100_BIN2 NOT NULL,
    policy_fingerprint CHAR(64) NOT NULL,
    environment_name NVARCHAR(32) COLLATE Latin1_General_100_BIN2 NOT NULL,
    source_instance NVARCHAR(128) COLLATE Latin1_General_100_BIN2 NOT NULL,
    tenant_scope NVARCHAR(128) COLLATE Latin1_General_100_BIN2 NOT NULL,
    entity_name NVARCHAR(128) COLLATE Latin1_General_100_BIN2 NOT NULL,
    terminal_outcome_class NVARCHAR(32) COLLATE Latin1_General_100_BIN2 NOT NULL,
    retention_days INT NOT NULL,
    cutoff_at_utc DATETIME2(3) NOT NULL,
    planned_at_utc DATETIME2(3) NOT NULL,
    scan_after_terminal_at_utc DATETIME2(3) NULL,
    scan_after_execution_id UNIQUEIDENTIFIER NULL,
    next_scan_after_terminal_at_utc DATETIME2(3) NULL,
    next_scan_after_execution_id UNIQUEIDENTIFIER NULL,
    maximum_executions INT NOT NULL,
    maximum_examined_executions INT NOT NULL,
    maximum_stage_rows BIGINT NOT NULL,
    maximum_candidate_rows BIGINT NOT NULL,
    maximum_evidence_rows BIGINT NOT NULL,
    maximum_content_rows BIGINT NOT NULL,
    maximum_probe_rows BIGINT NOT NULL,
    maximum_archive_bytes BIGINT NOT NULL,
    examined_executions BIGINT NOT NULL,
    scan_truncated BIT NOT NULL,
    eligible_executions BIGINT NOT NULL,
    held_executions BIGINT NOT NULL,
    lease_blocked_executions BIGINT NOT NULL,
    oversized_executions BIGINT NOT NULL,
    deferred_executions BIGINT NOT NULL,
    selected_executions BIGINT NOT NULL,
    selected_stage_rows BIGINT NOT NULL,
    selected_candidate_rows BIGINT NOT NULL,
    selected_evidence_rows BIGINT NOT NULL,
    selected_archive_bytes BIGINT NOT NULL,
    planned_content_root_version NVARCHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL,
    planned_content_root BINARY(32) NOT NULL,
    CONSTRAINT PK_ctl_staging_lifecycle_plan PRIMARY KEY CLUSTERED (plan_id),
    CONSTRAINT FK_ctl_staging_lifecycle_plan_policy
        FOREIGN KEY (policy_id) REFERENCES ctl.staging_retention_policy (policy_id),
    CONSTRAINT CK_ctl_staging_lifecycle_plan_limits CHECK (
        maximum_executions BETWEEN 1 AND 1000
        AND maximum_examined_executions BETWEEN maximum_executions AND 5000
        AND maximum_stage_rows BETWEEN 1 AND 1000000
        AND maximum_candidate_rows BETWEEN 1 AND 1000000
        AND maximum_evidence_rows BETWEEN 1 AND 5000000
        AND maximum_content_rows BETWEEN 1 AND 100000
        AND maximum_probe_rows BETWEEN 1 AND 10000000
        AND CONVERT(DECIMAL(38, 0), maximum_examined_executions)
            * CONVERT(DECIMAL(38, 0), maximum_stage_rows + maximum_candidate_rows
                + maximum_evidence_rows + 6) + 1 <= maximum_probe_rows
        AND maximum_archive_bytes BETWEEN 1 AND 1073741824
        AND examined_executions BETWEEN 0 AND maximum_examined_executions
        AND ((scan_after_terminal_at_utc IS NULL AND scan_after_execution_id IS NULL)
             OR (scan_after_terminal_at_utc IS NOT NULL AND scan_after_execution_id IS NOT NULL))
        AND ((scan_truncated = 0 AND next_scan_after_terminal_at_utc IS NULL
              AND next_scan_after_execution_id IS NULL)
             OR (scan_truncated = 1 AND next_scan_after_terminal_at_utc IS NOT NULL
                 AND next_scan_after_execution_id IS NOT NULL))
        AND (next_scan_after_terminal_at_utc IS NULL OR scan_after_terminal_at_utc IS NULL
             OR next_scan_after_terminal_at_utc > scan_after_terminal_at_utc
             OR (next_scan_after_terminal_at_utc = scan_after_terminal_at_utc
                 AND next_scan_after_execution_id > scan_after_execution_id))
        AND eligible_executions >= 0 AND held_executions >= 0
        AND lease_blocked_executions >= 0 AND oversized_executions >= 0
        AND deferred_executions BETWEEN 0 AND eligible_executions
        AND selected_executions BETWEEN 0 AND maximum_executions
        AND selected_stage_rows BETWEEN 0 AND maximum_stage_rows
        AND selected_candidate_rows BETWEEN 0 AND maximum_candidate_rows
        AND selected_evidence_rows BETWEEN 0 AND maximum_evidence_rows
        AND selected_stage_rows + selected_candidate_rows + selected_evidence_rows
            <= maximum_content_rows
        AND selected_archive_bytes BETWEEN 0 AND maximum_archive_bytes
    )
);
GO

CREATE TABLE ctl.staging_lifecycle_plan_item (
    plan_id UNIQUEIDENTIFIER NOT NULL,
    execution_id UNIQUEIDENTIFIER NOT NULL,
    terminal_state NVARCHAR(32) COLLATE Latin1_General_100_BIN2 NOT NULL,
    terminal_at_utc DATETIME2(3) NOT NULL,
    stage_rows BIGINT NOT NULL,
    candidate_rows BIGINT NOT NULL,
    evidence_rows BIGINT NOT NULL,
    archive_bytes BIGINT NOT NULL,
    CONSTRAINT PK_ctl_staging_lifecycle_plan_item
        PRIMARY KEY CLUSTERED (plan_id, execution_id),
    CONSTRAINT FK_ctl_staging_lifecycle_plan_item_plan
        FOREIGN KEY (plan_id) REFERENCES ctl.staging_lifecycle_plan (plan_id),
    CONSTRAINT FK_ctl_staging_lifecycle_plan_item_execution
        FOREIGN KEY (execution_id) REFERENCES ctl.execution_attempt (execution_id),
    CONSTRAINT CK_ctl_staging_lifecycle_plan_item_state CHECK (
        terminal_state IN (
            N'PUBLISHED', N'BLOCKED', N'SKIPPED', N'NOT_APPLICABLE',
            N'FAILED', N'CANCELLED', N'DEGRADED'
        )
    ),
    CONSTRAINT CK_ctl_staging_lifecycle_plan_item_counts CHECK (
        stage_rows >= 0 AND candidate_rows >= 0 AND evidence_rows >= 0 AND archive_bytes >= 0
    )
);
GO

CREATE INDEX IX_ctl_staging_lifecycle_plan_item_execution
    ON ctl.staging_lifecycle_plan_item (execution_id, plan_id);
GO

CREATE TABLE recon.staging_lifecycle_archive_manifest (
    plan_id UNIQUEIDENTIFIER NOT NULL,
    archive_fingerprint_version NVARCHAR(128) COLLATE Latin1_General_100_BIN2 NOT NULL,
    archive_fingerprint CHAR(64) NOT NULL,
    archived_executions BIGINT NOT NULL,
    archived_stage_rows BIGINT NOT NULL,
    archived_candidate_rows BIGINT NOT NULL,
    archived_bytes BIGINT NOT NULL,
    archived_quarantine_rows BIGINT NOT NULL,
    archived_application_rows BIGINT NOT NULL,
    archived_reconciliation_rows BIGINT NOT NULL,
    archived_state_event_rows BIGINT NOT NULL,
    archived_page_audit_rows BIGINT NOT NULL,
    archived_count_rows BIGINT NOT NULL,
    archived_publication_event_rows BIGINT NOT NULL,
    archived_promotion_result_rows BIGINT NOT NULL,
    content_root_version NVARCHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL,
    content_root BINARY(32) NOT NULL,
    archived_at_utc DATETIME2(3) NOT NULL,
    CONSTRAINT PK_recon_staging_lifecycle_archive_manifest PRIMARY KEY CLUSTERED (plan_id),
    CONSTRAINT FK_recon_staging_lifecycle_archive_manifest_plan
        FOREIGN KEY (plan_id) REFERENCES ctl.staging_lifecycle_plan (plan_id),
    CONSTRAINT CK_recon_staging_lifecycle_archive_manifest_fingerprint CHECK (
        LEN(archive_fingerprint) = 64
        AND archive_fingerprint COLLATE Latin1_General_100_BIN2
            NOT LIKE '%[^0-9A-Fa-f]%'
    ),
    CONSTRAINT CK_recon_staging_lifecycle_archive_manifest_counts CHECK (
        archived_executions >= 0 AND archived_stage_rows >= 0
        AND archived_candidate_rows >= 0 AND archived_bytes >= 0
        AND archived_quarantine_rows >= 0 AND archived_application_rows >= 0
        AND archived_reconciliation_rows >= 0 AND archived_state_event_rows >= 0
        AND archived_page_audit_rows >= 0 AND archived_count_rows >= 0
        AND archived_publication_event_rows >= 0 AND archived_promotion_result_rows >= 0
    )
);
GO

CREATE TABLE recon.staging_record_archive (
    plan_id UNIQUEIDENTIFIER NOT NULL,
    stage_record_id BIGINT NOT NULL,
    execution_id UNIQUEIDENTIFIER NOT NULL,
    input_batch_number INT NOT NULL,
    input_record_ordinal INT NOT NULL,
    source_key NVARCHAR(256) COLLATE Latin1_General_100_BIN2 NULL,
    row_fingerprint_version NVARCHAR(128) COLLATE Latin1_General_100_BIN2 NULL,
    source_row_hash CHAR(64) NULL,
    presence_fingerprint_version NVARCHAR(128) COLLATE Latin1_General_100_BIN2 NULL,
    presence_fingerprint CHAR(64) NULL,
    source_freshness_at_utc DATETIME2(3) NULL,
    validation_disposition NVARCHAR(16) COLLATE Latin1_General_100_BIN2 NOT NULL,
    quarantine_reason_code NVARCHAR(64) COLLATE Latin1_General_100_BIN2 NULL,
    staged_at_utc DATETIME2(3) NOT NULL,
    archive_row_attestation_version NVARCHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL,
    archive_row_attestation BINARY(32) NOT NULL,
    CONSTRAINT PK_recon_staging_record_archive PRIMARY KEY CLUSTERED (stage_record_id),
    CONSTRAINT UQ_recon_staging_record_archive_plan_stage UNIQUE (plan_id, stage_record_id),
    CONSTRAINT UQ_recon_staging_record_archive_input UNIQUE (
        execution_id, input_batch_number, input_record_ordinal
    ),
    CONSTRAINT FK_recon_staging_record_archive_manifest
        FOREIGN KEY (plan_id) REFERENCES recon.staging_lifecycle_archive_manifest (plan_id),
    CONSTRAINT FK_recon_staging_record_archive_plan_item
        FOREIGN KEY (plan_id, execution_id)
        REFERENCES ctl.staging_lifecycle_plan_item (plan_id, execution_id)
);
GO

CREATE INDEX IX_recon_staging_record_archive_execution
    ON recon.staging_record_archive (execution_id, stage_record_id);
GO

CREATE TABLE recon.staging_candidate_archive (
    plan_id UNIQUEIDENTIFIER NOT NULL,
    execution_id UNIQUEIDENTIFIER NOT NULL,
    source_key NVARCHAR(256) COLLATE Latin1_General_100_BIN2 NOT NULL,
    winner_stage_record_id BIGINT NOT NULL,
    row_fingerprint_version NVARCHAR(128) COLLATE Latin1_General_100_BIN2 NOT NULL,
    source_row_hash CHAR(64) NOT NULL,
    presence_fingerprint_version NVARCHAR(128) COLLATE Latin1_General_100_BIN2 NOT NULL,
    presence_fingerprint CHAR(64) NOT NULL,
    source_freshness_at_utc DATETIME2(3) NULL,
    prepared_at_utc DATETIME2(3) NOT NULL,
    archive_row_attestation_version NVARCHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL,
    archive_row_attestation BINARY(32) NOT NULL,
    CONSTRAINT PK_recon_staging_candidate_archive
        PRIMARY KEY CLUSTERED (execution_id, source_key),
    CONSTRAINT UQ_recon_staging_candidate_archive_plan_candidate
        UNIQUE (plan_id, execution_id, source_key),
    CONSTRAINT UQ_recon_staging_candidate_archive_winner UNIQUE (winner_stage_record_id),
    CONSTRAINT FK_recon_staging_candidate_archive_manifest
        FOREIGN KEY (plan_id) REFERENCES recon.staging_lifecycle_archive_manifest (plan_id),
    CONSTRAINT FK_recon_staging_candidate_archive_plan_item
        FOREIGN KEY (plan_id, execution_id)
        REFERENCES ctl.staging_lifecycle_plan_item (plan_id, execution_id),
    CONSTRAINT FK_recon_staging_candidate_archive_winner
        FOREIGN KEY (plan_id, winner_stage_record_id)
        REFERENCES recon.staging_record_archive (plan_id, stage_record_id)
);
GO

CREATE TABLE recon.quarantine_record_archive (
    plan_id UNIQUEIDENTIFIER NOT NULL,
    quarantine_record_id BIGINT NOT NULL,
    execution_id UNIQUEIDENTIFIER NOT NULL,
    stage_record_id BIGINT NOT NULL,
    reason_code NVARCHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL,
    source_key NVARCHAR(256) COLLATE Latin1_General_100_BIN2 NULL,
    row_fingerprint_version NVARCHAR(128) COLLATE Latin1_General_100_BIN2 NULL,
    source_row_hash CHAR(64) NULL,
    presence_fingerprint_version NVARCHAR(128) COLLATE Latin1_General_100_BIN2 NULL,
    presence_fingerprint CHAR(64) NULL,
    quarantined_at_utc DATETIME2(3) NOT NULL,
    archive_row_attestation_version NVARCHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL,
    archive_row_attestation BINARY(32) NOT NULL,
    CONSTRAINT PK_recon_quarantine_record_archive PRIMARY KEY CLUSTERED (quarantine_record_id),
    CONSTRAINT FK_recon_quarantine_record_archive_manifest
        FOREIGN KEY (plan_id) REFERENCES recon.staging_lifecycle_archive_manifest (plan_id),
    CONSTRAINT FK_recon_quarantine_record_archive_plan_item
        FOREIGN KEY (plan_id, execution_id)
        REFERENCES ctl.staging_lifecycle_plan_item (plan_id, execution_id),
    CONSTRAINT FK_recon_quarantine_record_archive_stage
        FOREIGN KEY (plan_id, stage_record_id)
        REFERENCES recon.staging_record_archive (plan_id, stage_record_id)
);
GO

CREATE INDEX IX_recon_quarantine_record_archive_plan
    ON recon.quarantine_record_archive (plan_id, quarantine_record_id);
GO

CREATE TABLE recon.execution_candidate_application_archive (
    plan_id UNIQUEIDENTIFIER NOT NULL,
    execution_id UNIQUEIDENTIFIER NOT NULL,
    source_key NVARCHAR(256) COLLATE Latin1_General_100_BIN2 NOT NULL,
    record_state_id BIGINT NOT NULL,
    application_disposition NVARCHAR(24) COLLATE Latin1_General_100_BIN2 NOT NULL,
    result_row_fingerprint_version NVARCHAR(128) COLLATE Latin1_General_100_BIN2 NOT NULL,
    result_source_row_hash CHAR(64) NOT NULL,
    result_presence_fingerprint_version NVARCHAR(128) COLLATE Latin1_General_100_BIN2 NOT NULL,
    result_presence_fingerprint CHAR(64) NOT NULL,
    result_source_freshness_at_utc DATETIME2(3) NULL,
    applied_at_utc DATETIME2(3) NOT NULL,
    archive_row_attestation_version NVARCHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL,
    archive_row_attestation BINARY(32) NOT NULL,
    CONSTRAINT PK_recon_execution_candidate_application_archive
        PRIMARY KEY CLUSTERED (execution_id, source_key),
    CONSTRAINT FK_recon_execution_candidate_application_archive_manifest
        FOREIGN KEY (plan_id) REFERENCES recon.staging_lifecycle_archive_manifest (plan_id),
    CONSTRAINT FK_recon_execution_candidate_application_archive_plan_item
        FOREIGN KEY (plan_id, execution_id)
        REFERENCES ctl.staging_lifecycle_plan_item (plan_id, execution_id),
    CONSTRAINT FK_recon_execution_candidate_application_archive_candidate
        FOREIGN KEY (plan_id, execution_id, source_key)
        REFERENCES recon.staging_candidate_archive (plan_id, execution_id, source_key)
);
GO

CREATE INDEX IX_recon_candidate_application_archive_plan
    ON recon.execution_candidate_application_archive (plan_id, execution_id, source_key);
GO

CREATE TABLE recon.execution_reconciliation_result_archive (
    plan_id UNIQUEIDENTIFIER NOT NULL,
    execution_id UNIQUEIDENTIFIER NOT NULL,
    candidate_rows BIGINT NOT NULL,
    inserted_rows BIGINT NOT NULL,
    updated_rows BIGINT NOT NULL,
    reactivated_rows BIGINT NOT NULL,
    noop_rows BIGINT NOT NULL,
    stale_noop_rows BIGINT NOT NULL,
    reconciled_at_utc DATETIME2(3) NOT NULL,
    published_at_utc DATETIME2(3) NOT NULL,
    archive_row_attestation_version NVARCHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL,
    archive_row_attestation BINARY(32) NOT NULL,
    CONSTRAINT PK_recon_execution_reconciliation_result_archive
        PRIMARY KEY CLUSTERED (execution_id),
    CONSTRAINT FK_recon_execution_reconciliation_result_archive_manifest
        FOREIGN KEY (plan_id) REFERENCES recon.staging_lifecycle_archive_manifest (plan_id),
    CONSTRAINT FK_recon_execution_reconciliation_result_archive_plan_item
        FOREIGN KEY (plan_id, execution_id)
        REFERENCES ctl.staging_lifecycle_plan_item (plan_id, execution_id)
);
GO

CREATE INDEX IX_recon_reconciliation_result_archive_plan
    ON recon.execution_reconciliation_result_archive (plan_id, execution_id);
GO

CREATE TABLE recon.execution_state_event_archive (
    plan_id UNIQUEIDENTIFIER NOT NULL,
    state_event_id BIGINT NOT NULL,
    execution_id UNIQUEIDENTIFIER NOT NULL,
    transition_sequence INT NOT NULL,
    previous_state NVARCHAR(32) COLLATE Latin1_General_100_BIN2 NULL,
    next_state NVARCHAR(32) COLLATE Latin1_General_100_BIN2 NOT NULL,
    reason_code NVARCHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL,
    transitioned_at_utc DATETIME2(3) NOT NULL,
    archive_row_attestation_version NVARCHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL,
    archive_row_attestation BINARY(32) NOT NULL,
    CONSTRAINT PK_recon_execution_state_event_archive PRIMARY KEY CLUSTERED (state_event_id),
    CONSTRAINT FK_recon_execution_state_event_archive_manifest
        FOREIGN KEY (plan_id) REFERENCES recon.staging_lifecycle_archive_manifest (plan_id),
    CONSTRAINT FK_recon_execution_state_event_archive_plan_item
        FOREIGN KEY (plan_id, execution_id)
        REFERENCES ctl.staging_lifecycle_plan_item (plan_id, execution_id)
);
GO

CREATE INDEX IX_recon_execution_state_event_archive_plan
    ON recon.execution_state_event_archive (plan_id, state_event_id);
GO

CREATE TABLE recon.execution_page_audit_archive (
    plan_id UNIQUEIDENTIFIER NOT NULL,
    page_audit_id BIGINT NOT NULL,
    execution_id UNIQUEIDENTIFIER NOT NULL,
    page_number INT NOT NULL,
    page_attempt INT NOT NULL,
    requested_page_size INT NOT NULL,
    physical_rows BIGINT NOT NULL,
    distinct_root_keys BIGINT NOT NULL,
    response_bytes BIGINT NOT NULL,
    terminal_empty_page BIT NOT NULL,
    terminal_evidence_kind NVARCHAR(32) COLLATE Latin1_General_100_BIN2 NOT NULL,
    read_at_utc DATETIME2(3) NOT NULL,
    archive_row_attestation_version NVARCHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL,
    archive_row_attestation BINARY(32) NOT NULL,
    CONSTRAINT PK_recon_execution_page_audit_archive PRIMARY KEY CLUSTERED (page_audit_id),
    CONSTRAINT FK_recon_execution_page_audit_archive_manifest
        FOREIGN KEY (plan_id) REFERENCES recon.staging_lifecycle_archive_manifest (plan_id),
    CONSTRAINT FK_recon_execution_page_audit_archive_plan_item
        FOREIGN KEY (plan_id, execution_id)
        REFERENCES ctl.staging_lifecycle_plan_item (plan_id, execution_id)
);
GO

CREATE INDEX IX_recon_execution_page_audit_archive_plan
    ON recon.execution_page_audit_archive (plan_id, page_audit_id);
GO

CREATE TABLE recon.execution_count_archive (
    plan_id UNIQUEIDENTIFIER NOT NULL,
    count_id BIGINT NOT NULL,
    execution_id UNIQUEIDENTIFIER NOT NULL,
    count_phase NVARCHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL,
    physical_rows BIGINT NOT NULL,
    distinct_root_keys BIGINT NOT NULL,
    duplicate_rows BIGINT NOT NULL,
    valid_rows BIGINT NOT NULL,
    quarantined_root_keys BIGINT NOT NULL,
    unidentified_quarantine_rows BIGINT NOT NULL,
    recorded_at_utc DATETIME2(3) NOT NULL,
    archive_row_attestation_version NVARCHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL,
    archive_row_attestation BINARY(32) NOT NULL,
    CONSTRAINT PK_recon_execution_count_archive PRIMARY KEY CLUSTERED (count_id),
    CONSTRAINT FK_recon_execution_count_archive_manifest
        FOREIGN KEY (plan_id) REFERENCES recon.staging_lifecycle_archive_manifest (plan_id),
    CONSTRAINT FK_recon_execution_count_archive_plan_item
        FOREIGN KEY (plan_id, execution_id)
        REFERENCES ctl.staging_lifecycle_plan_item (plan_id, execution_id)
);
GO

CREATE INDEX IX_recon_execution_count_archive_plan
    ON recon.execution_count_archive (plan_id, count_id);
GO

CREATE TABLE recon.execution_publication_event_archive (
    plan_id UNIQUEIDENTIFIER NOT NULL,
    execution_id UNIQUEIDENTIFIER NOT NULL,
    partition_id BIGINT NOT NULL,
    previous_published_execution_id UNIQUEIDENTIFIER NULL,
    published_at_utc DATETIME2(3) NOT NULL,
    incremental_frontier_before_utc DATETIME2(3) NULL,
    incremental_frontier_after_utc DATETIME2(3) NULL,
    watermark_last_partition_id BIGINT NULL,
    archive_row_attestation_version NVARCHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL,
    archive_row_attestation BINARY(32) NOT NULL,
    CONSTRAINT PK_recon_execution_publication_event_archive PRIMARY KEY CLUSTERED (execution_id),
    CONSTRAINT FK_recon_execution_publication_event_archive_manifest
        FOREIGN KEY (plan_id) REFERENCES recon.staging_lifecycle_archive_manifest (plan_id),
    CONSTRAINT FK_recon_execution_publication_event_archive_plan_item
        FOREIGN KEY (plan_id, execution_id)
        REFERENCES ctl.staging_lifecycle_plan_item (plan_id, execution_id)
);
GO

CREATE INDEX IX_recon_publication_event_archive_plan
    ON recon.execution_publication_event_archive (plan_id, execution_id);
GO

CREATE TABLE recon.execution_promotion_result_archive (
    plan_id UNIQUEIDENTIFIER NOT NULL,
    execution_id UNIQUEIDENTIFIER NOT NULL,
    physical_rows BIGINT NOT NULL,
    distinct_root_keys BIGINT NOT NULL,
    candidate_rows BIGINT NOT NULL,
    duplicate_rows BIGINT NOT NULL,
    quarantined_root_keys BIGINT NOT NULL,
    unidentified_quarantine_rows BIGINT NOT NULL,
    quarantined_stage_rows BIGINT NOT NULL,
    promoted_at_utc DATETIME2(3) NOT NULL,
    archive_row_attestation_version NVARCHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL,
    archive_row_attestation BINARY(32) NOT NULL,
    CONSTRAINT PK_recon_execution_promotion_result_archive PRIMARY KEY CLUSTERED (execution_id),
    CONSTRAINT FK_recon_execution_promotion_result_archive_manifest
        FOREIGN KEY (plan_id) REFERENCES recon.staging_lifecycle_archive_manifest (plan_id),
    CONSTRAINT FK_recon_execution_promotion_result_archive_plan_item
        FOREIGN KEY (plan_id, execution_id)
        REFERENCES ctl.staging_lifecycle_plan_item (plan_id, execution_id)
);
GO

CREATE INDEX IX_recon_promotion_result_archive_plan
    ON recon.execution_promotion_result_archive (plan_id, execution_id);
GO

CREATE FUNCTION recon.ufn_archive_row_attestation (
    @object_code NVARCHAR(128),
    @canonical_row NVARCHAR(MAX)
)
RETURNS BINARY(32)
WITH SCHEMABINDING
AS
BEGIN
    RETURN CONVERT(BINARY(32), HASHBYTES(
        'SHA2_256',
        CONVERT(VARBINARY(MAX), CONCAT(
            N'archive-row-json-v1|', @object_code, N'|', @canonical_row
        ))
    ));
END;
GO

CREATE FUNCTION ctl.ufn_invalid_staging_legal_hold (
    @execution_id UNIQUEIDENTIFIER
)
RETURNS TABLE
AS
RETURN (
    SELECT hold_definition.hold_id, hold_definition.execution_id
    FROM ctl.staging_legal_hold AS hold_definition
    WHERE hold_definition.execution_id = @execution_id
      AND (
       NOT EXISTS (
              SELECT 1
              FROM ctl.staging_legal_hold_event AS placed_event
              WHERE placed_event.hold_id = hold_definition.hold_id
                AND placed_event.execution_id = hold_definition.execution_id
                AND placed_event.event_action = N'PLACED'
                AND placed_event.reason_code = hold_definition.reason_code
                AND placed_event.authority_evidence_fingerprint =
                    hold_definition.authority_evidence_fingerprint
                AND placed_event.owner_role = hold_definition.owner_role
                AND placed_event.hold_scope = hold_definition.hold_scope
                AND placed_event.occurred_at_utc = hold_definition.held_at_utc
          )
       OR (
              hold_definition.released_at_utc IS NULL
              AND EXISTS (
                  SELECT 1
                  FROM ctl.staging_legal_hold_event AS released_event
                  WHERE released_event.hold_id = hold_definition.hold_id
                    AND released_event.event_action = N'RELEASED'
              )
          )
       OR (
              hold_definition.released_at_utc IS NOT NULL
              AND NOT EXISTS (
                  SELECT 1
                  FROM ctl.staging_legal_hold_event AS released_event
                  WHERE released_event.hold_id = hold_definition.hold_id
                    AND released_event.execution_id = hold_definition.execution_id
                    AND released_event.event_action = N'RELEASED'
                    AND released_event.reason_code = hold_definition.reason_code
                    AND released_event.authority_evidence_fingerprint =
                        hold_definition.release_authority_evidence_fingerprint
                    AND released_event.owner_role = hold_definition.release_owner_role
                    AND released_event.hold_scope = hold_definition.hold_scope
                    AND released_event.occurred_at_utc = hold_definition.released_at_utc
              )
          )
      )
);
GO

CREATE FUNCTION ctl.ufn_invalid_execution_state_ledger (
    @execution_id UNIQUEIDENTIFIER
)
RETURNS TABLE
AS
RETURN (
    SELECT attempt.execution_id
    FROM ctl.execution_attempt AS attempt
    WHERE attempt.execution_id = @execution_id
      AND attempt.terminal_at_utc IS NOT NULL
      AND (
          EXISTS (
              SELECT 1
              FROM ctl.execution_state_event AS out_of_range_event
              WHERE out_of_range_event.execution_id = attempt.execution_id
                AND (
                    out_of_range_event.transition_sequence < 1
                    OR out_of_range_event.transition_sequence >=
                        attempt.next_transition_sequence
                )
          )
          OR (
              SELECT COUNT_BIG(*)
              FROM ctl.execution_state_event AS counted_event
              WHERE counted_event.execution_id = attempt.execution_id
          ) <> CONVERT(BIGINT, attempt.next_transition_sequence - 1)
          OR NOT EXISTS (
              SELECT 1
              FROM ctl.execution_state_event AS first_event
              WHERE first_event.execution_id = attempt.execution_id
                AND first_event.transition_sequence = 1
                AND first_event.previous_state IS NULL
                AND first_event.next_state = N'PLANNED'
                AND first_event.reason_code = N'EXECUTION_PLANNED'
                AND first_event.transitioned_at_utc = attempt.started_at_utc
          )
          OR NOT EXISTS (
              SELECT 1
              FROM ctl.execution_state_event AS lease_event
              WHERE lease_event.execution_id = attempt.execution_id
                AND lease_event.transition_sequence = 2
                AND lease_event.previous_state = N'PLANNED'
                AND lease_event.next_state = N'EXTRACTING'
                AND lease_event.reason_code = N'LEASE_ACQUIRED'
                AND lease_event.transitioned_at_utc = attempt.started_at_utc
          )
          OR EXISTS (
              SELECT 1
              FROM ctl.execution_state_event AS current_event
              WHERE current_event.execution_id = attempt.execution_id
                AND current_event.transition_sequence > 1
                AND NOT EXISTS (
                    SELECT 1
                    FROM ctl.execution_state_event AS previous_event
                    WHERE previous_event.execution_id = current_event.execution_id
                      AND previous_event.transition_sequence =
                          current_event.transition_sequence - 1
                      AND previous_event.next_state = current_event.previous_state
                      AND previous_event.transitioned_at_utc <=
                          current_event.transitioned_at_utc
                )
          )
          OR EXISTS (
              SELECT 1
              FROM ctl.execution_state_event AS semantic_event
              WHERE semantic_event.execution_id = attempt.execution_id
                AND semantic_event.transition_sequence > 2
                AND NOT (
                    (
                        semantic_event.previous_state = N'EXTRACTING'
                        AND semantic_event.next_state IN (
                            N'EXTRACTED', N'BLOCKED', N'FAILED', N'CANCELLED', N'DEGRADED'
                        )
                    )
                    OR (
                        semantic_event.previous_state = N'EXTRACTED'
                        AND semantic_event.next_state IN (
                            N'STAGED', N'BLOCKED', N'FAILED', N'CANCELLED', N'DEGRADED'
                        )
                    )
                    OR (
                        semantic_event.previous_state = N'STAGED'
                        AND semantic_event.next_state IN (
                            N'BLOCKED', N'FAILED', N'CANCELLED', N'DEGRADED'
                        )
                    )
                    OR (
                        semantic_event.previous_state = N'PROMOTED'
                        AND semantic_event.next_state IN (
                            N'BLOCKED', N'FAILED', N'CANCELLED', N'DEGRADED'
                        )
                    )
                    OR (
                        semantic_event.previous_state = N'RECONCILED'
                        AND semantic_event.next_state IN (
                            N'BLOCKED', N'FAILED', N'CANCELLED', N'DEGRADED'
                        )
                    )
                    OR (
                        semantic_event.previous_state = N'STAGED'
                        AND semantic_event.next_state = N'PROMOTED'
                        AND semantic_event.reason_code = N'CANDIDATE_SET_PREPARED'
                    )
                    OR (
                        semantic_event.previous_state = N'PROMOTED'
                        AND semantic_event.next_state = N'RECONCILED'
                        AND semantic_event.reason_code = N'CANDIDATE_SET_RECONCILED'
                    )
                    OR (
                        semantic_event.previous_state = N'RECONCILED'
                        AND semantic_event.next_state = N'PUBLISHED'
                        AND semantic_event.reason_code = N'RECONCILIATION_PUBLISHED'
                    )
                )
          )
          OR NOT EXISTS (
              SELECT 1
              FROM ctl.execution_state_event AS terminal_event
              WHERE terminal_event.execution_id = attempt.execution_id
                AND terminal_event.transition_sequence =
                    attempt.next_transition_sequence - 1
                AND terminal_event.next_state = attempt.current_state
                AND terminal_event.transitioned_at_utc = attempt.terminal_at_utc
          )
      )
);
GO

CREATE OR ALTER PROCEDURE recon.usp_compute_live_staging_content_root_internal
    @plan_id UNIQUEIDENTIFIER,
    @expected_content_rows BIGINT,
    @content_root BINARY(32) OUTPUT
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;

    IF @plan_id IS NULL OR @expected_content_rows NOT BETWEEN 0 AND 100000
        THROW 51501, N'Limite inválido para cálculo da raiz viva de staging.', 1;

    DECLARE @bounded_content_rows BIGINT;
    SELECT @bounded_content_rows = COUNT_BIG(*)
    FROM (
        SELECT TOP (@expected_content_rows + 1) content_key.marker
        FROM (
            SELECT 1 AS marker FROM stg.execution_record AS source_row WITH (UPDLOCK, HOLDLOCK)
            INNER JOIN ctl.staging_lifecycle_plan_item AS item
                ON item.execution_id = source_row.execution_id AND item.plan_id = @plan_id
            UNION ALL
            SELECT 1 FROM stg.execution_candidate AS source_row WITH (UPDLOCK, HOLDLOCK)
            INNER JOIN ctl.staging_lifecycle_plan_item AS item
                ON item.execution_id = source_row.execution_id AND item.plan_id = @plan_id
            UNION ALL
            SELECT 1 FROM recon.quarantine_record AS source_row WITH (UPDLOCK, HOLDLOCK)
            INNER JOIN ctl.staging_lifecycle_plan_item AS item
                ON item.execution_id = source_row.execution_id AND item.plan_id = @plan_id
            UNION ALL
            SELECT 1 FROM recon.execution_candidate_application AS source_row
                WITH (UPDLOCK, HOLDLOCK)
            INNER JOIN ctl.staging_lifecycle_plan_item AS item
                ON item.execution_id = source_row.execution_id AND item.plan_id = @plan_id
            UNION ALL
            SELECT 1 FROM recon.execution_reconciliation_result AS source_row
                WITH (UPDLOCK, HOLDLOCK)
            INNER JOIN ctl.staging_lifecycle_plan_item AS item
                ON item.execution_id = source_row.execution_id AND item.plan_id = @plan_id
            UNION ALL
            SELECT 1 FROM ctl.execution_state_event AS source_row WITH (UPDLOCK, HOLDLOCK)
            INNER JOIN ctl.staging_lifecycle_plan_item AS item
                ON item.execution_id = source_row.execution_id AND item.plan_id = @plan_id
            UNION ALL
            SELECT 1 FROM ctl.execution_page_audit AS source_row WITH (UPDLOCK, HOLDLOCK)
            INNER JOIN ctl.staging_lifecycle_plan_item AS item
                ON item.execution_id = source_row.execution_id AND item.plan_id = @plan_id
            UNION ALL
            SELECT 1 FROM ctl.execution_count AS source_row WITH (UPDLOCK, HOLDLOCK)
            INNER JOIN ctl.staging_lifecycle_plan_item AS item
                ON item.execution_id = source_row.execution_id AND item.plan_id = @plan_id
            UNION ALL
            SELECT 1 FROM ctl.execution_publication_event AS source_row WITH (UPDLOCK, HOLDLOCK)
            INNER JOIN ctl.staging_lifecycle_plan_item AS item
                ON item.execution_id = source_row.execution_id AND item.plan_id = @plan_id
            UNION ALL
            SELECT 1 FROM ctl.execution_promotion_result AS source_row WITH (UPDLOCK, HOLDLOCK)
            INNER JOIN ctl.staging_lifecycle_plan_item AS item
                ON item.execution_id = source_row.execution_id AND item.plan_id = @plan_id
        ) AS content_key
    ) AS bounded;

    IF @bounded_content_rows <> @expected_content_rows
        THROW 51502, N'Conteúdo vivo divergiu do plano limitado.', 1;

    DECLARE @actual_content_rows BIGINT;
    SELECT @actual_content_rows = COUNT_BIG(*),
           @content_root = CONVERT(BINARY(32), HASHBYTES(
               'SHA2_256',
               CONVERT(VARBINARY(MAX), CONCAT(N'archive-content-set-v1|', COALESCE(STRING_AGG(
                   CONVERT(NVARCHAR(MAX), CONCAT(
                       content_row.object_code, N'|',
                       CONVERT(VARCHAR(64), content_row.row_attestation, 2), N';'
                   )), N'') WITHIN GROUP (ORDER BY content_row.sort_key), N'')))
           ))
    FROM (
        SELECT N'01-staging-record' AS object_code,
               CONCAT(N'01|', CONVERT(NVARCHAR(36), stage_record.execution_id), N'|',
                      RIGHT(REPLICATE(N'0', 20)
                          + CONVERT(NVARCHAR(20), stage_record.stage_record_id), 20))
                   COLLATE Latin1_General_100_BIN2 AS sort_key,
               recon.ufn_archive_row_attestation(
                   N'stg.execution_record', canonical.canonical_row) AS row_attestation
        FROM stg.execution_record AS stage_record WITH (UPDLOCK, HOLDLOCK)
        INNER JOIN ctl.staging_lifecycle_plan_item AS item
            ON item.execution_id = stage_record.execution_id AND item.plan_id = @plan_id
        CROSS APPLY (SELECT (SELECT stage_record.stage_record_id,
                                    stage_record.execution_id,
                                    stage_record.input_batch_number,
                                    stage_record.input_record_ordinal,
                                    stage_record.source_key,
                                    stage_record.row_fingerprint_version,
                                    stage_record.source_row_hash,
                                    stage_record.presence_fingerprint_version,
                                    stage_record.presence_fingerprint,
                                    stage_record.source_freshness_at_utc,
                                    stage_record.validation_disposition,
                                    stage_record.quarantine_reason_code,
                                    stage_record.staged_at_utc
                             FOR JSON PATH, WITHOUT_ARRAY_WRAPPER, INCLUDE_NULL_VALUES)
                            AS canonical_row) AS canonical
        UNION ALL
        SELECT N'02-staging-candidate',
               CONCAT(N'02|', CONVERT(NVARCHAR(36), stage_candidate.execution_id), N'|',
                      stage_candidate.source_key) COLLATE Latin1_General_100_BIN2,
               recon.ufn_archive_row_attestation(
                   N'stg.execution_candidate', canonical.canonical_row)
        FROM stg.execution_candidate AS stage_candidate WITH (UPDLOCK, HOLDLOCK)
        INNER JOIN ctl.staging_lifecycle_plan_item AS item
            ON item.execution_id = stage_candidate.execution_id AND item.plan_id = @plan_id
        CROSS APPLY (SELECT (SELECT stage_candidate.execution_id,
                                    stage_candidate.source_key,
                                    stage_candidate.winner_stage_record_id,
                                    stage_candidate.row_fingerprint_version,
                                    stage_candidate.source_row_hash,
                                    stage_candidate.presence_fingerprint_version,
                                    stage_candidate.presence_fingerprint,
                                    stage_candidate.source_freshness_at_utc,
                                    stage_candidate.prepared_at_utc
                             FOR JSON PATH, WITHOUT_ARRAY_WRAPPER, INCLUDE_NULL_VALUES)
                            AS canonical_row) AS canonical
        UNION ALL
        SELECT N'03-quarantine-record',
               CONCAT(N'03|', CONVERT(NVARCHAR(36), evidence.execution_id), N'|',
                      RIGHT(REPLICATE(N'0', 20)
                          + CONVERT(NVARCHAR(20), evidence.quarantine_record_id), 20))
                   COLLATE Latin1_General_100_BIN2,
               recon.ufn_archive_row_attestation(
                   N'recon.quarantine_record', canonical.canonical_row)
        FROM recon.quarantine_record AS evidence WITH (UPDLOCK, HOLDLOCK)
        INNER JOIN ctl.staging_lifecycle_plan_item AS item
            ON item.execution_id = evidence.execution_id AND item.plan_id = @plan_id
        CROSS APPLY (SELECT (SELECT evidence.quarantine_record_id, evidence.execution_id,
                                    evidence.stage_record_id, evidence.reason_code,
                                    evidence.source_key, evidence.row_fingerprint_version,
                                    evidence.source_row_hash,
                                    evidence.presence_fingerprint_version,
                                    evidence.presence_fingerprint,
                                    evidence.quarantined_at_utc
                             FOR JSON PATH, WITHOUT_ARRAY_WRAPPER, INCLUDE_NULL_VALUES)
                            AS canonical_row) AS canonical
        UNION ALL
        SELECT N'04-candidate-application',
               CONCAT(N'04|', CONVERT(NVARCHAR(36), evidence.execution_id), N'|',
                      evidence.source_key) COLLATE Latin1_General_100_BIN2,
               recon.ufn_archive_row_attestation(
                   N'recon.execution_candidate_application', canonical.canonical_row)
        FROM recon.execution_candidate_application AS evidence WITH (UPDLOCK, HOLDLOCK)
        INNER JOIN ctl.staging_lifecycle_plan_item AS item
            ON item.execution_id = evidence.execution_id AND item.plan_id = @plan_id
        CROSS APPLY (SELECT (SELECT evidence.execution_id, evidence.source_key,
                                    evidence.record_state_id,
                                    evidence.application_disposition,
                                    evidence.result_row_fingerprint_version,
                                    evidence.result_source_row_hash,
                                    evidence.result_presence_fingerprint_version,
                                    evidence.result_presence_fingerprint,
                                    evidence.result_source_freshness_at_utc,
                                    evidence.applied_at_utc
                             FOR JSON PATH, WITHOUT_ARRAY_WRAPPER, INCLUDE_NULL_VALUES)
                            AS canonical_row) AS canonical
        UNION ALL
        SELECT N'05-reconciliation-result',
               CONCAT(N'05|', CONVERT(NVARCHAR(36), evidence.execution_id))
                   COLLATE Latin1_General_100_BIN2,
               recon.ufn_archive_row_attestation(
                   N'recon.execution_reconciliation_result', canonical.canonical_row)
        FROM recon.execution_reconciliation_result AS evidence WITH (UPDLOCK, HOLDLOCK)
        INNER JOIN ctl.staging_lifecycle_plan_item AS item
            ON item.execution_id = evidence.execution_id AND item.plan_id = @plan_id
        CROSS APPLY (SELECT (SELECT evidence.execution_id, evidence.candidate_rows,
                                    evidence.inserted_rows, evidence.updated_rows,
                                    evidence.reactivated_rows, evidence.noop_rows,
                                    evidence.stale_noop_rows, evidence.reconciled_at_utc,
                                    evidence.published_at_utc
                             FOR JSON PATH, WITHOUT_ARRAY_WRAPPER, INCLUDE_NULL_VALUES)
                            AS canonical_row) AS canonical
        UNION ALL
        SELECT N'06-state-event',
               CONCAT(N'06|', CONVERT(NVARCHAR(36), evidence.execution_id), N'|',
                      RIGHT(REPLICATE(N'0', 20)
                          + CONVERT(NVARCHAR(20), evidence.state_event_id), 20))
                   COLLATE Latin1_General_100_BIN2,
               recon.ufn_archive_row_attestation(
                   N'ctl.execution_state_event', canonical.canonical_row)
        FROM ctl.execution_state_event AS evidence WITH (UPDLOCK, HOLDLOCK)
        INNER JOIN ctl.staging_lifecycle_plan_item AS item
            ON item.execution_id = evidence.execution_id AND item.plan_id = @plan_id
        CROSS APPLY (SELECT (SELECT evidence.state_event_id, evidence.execution_id,
                                    evidence.transition_sequence, evidence.previous_state,
                                    evidence.next_state, evidence.reason_code,
                                    evidence.transitioned_at_utc
                             FOR JSON PATH, WITHOUT_ARRAY_WRAPPER, INCLUDE_NULL_VALUES)
                            AS canonical_row) AS canonical
        UNION ALL
        SELECT N'07-page-audit',
               CONCAT(N'07|', CONVERT(NVARCHAR(36), evidence.execution_id), N'|',
                      RIGHT(REPLICATE(N'0', 20)
                          + CONVERT(NVARCHAR(20), evidence.page_audit_id), 20))
                   COLLATE Latin1_General_100_BIN2,
               recon.ufn_archive_row_attestation(
                   N'ctl.execution_page_audit', canonical.canonical_row)
        FROM ctl.execution_page_audit AS evidence WITH (UPDLOCK, HOLDLOCK)
        INNER JOIN ctl.staging_lifecycle_plan_item AS item
            ON item.execution_id = evidence.execution_id AND item.plan_id = @plan_id
        CROSS APPLY (SELECT (SELECT evidence.page_audit_id, evidence.execution_id,
                                    evidence.page_number, evidence.page_attempt,
                                    evidence.requested_page_size, evidence.physical_rows,
                                    evidence.distinct_root_keys, evidence.response_bytes,
                                    evidence.terminal_empty_page,
                                    evidence.terminal_evidence_kind, evidence.read_at_utc
                             FOR JSON PATH, WITHOUT_ARRAY_WRAPPER, INCLUDE_NULL_VALUES)
                            AS canonical_row) AS canonical
        UNION ALL
        SELECT N'08-execution-count',
               CONCAT(N'08|', CONVERT(NVARCHAR(36), evidence.execution_id), N'|',
                      RIGHT(REPLICATE(N'0', 20)
                          + CONVERT(NVARCHAR(20), evidence.count_id), 20))
                   COLLATE Latin1_General_100_BIN2,
               recon.ufn_archive_row_attestation(
                   N'ctl.execution_count', canonical.canonical_row)
        FROM ctl.execution_count AS evidence WITH (UPDLOCK, HOLDLOCK)
        INNER JOIN ctl.staging_lifecycle_plan_item AS item
            ON item.execution_id = evidence.execution_id AND item.plan_id = @plan_id
        CROSS APPLY (SELECT (SELECT evidence.count_id, evidence.execution_id,
                                    evidence.count_phase, evidence.physical_rows,
                                    evidence.distinct_root_keys, evidence.duplicate_rows,
                                    evidence.valid_rows, evidence.quarantined_root_keys,
                                    evidence.unidentified_quarantine_rows,
                                    evidence.recorded_at_utc
                             FOR JSON PATH, WITHOUT_ARRAY_WRAPPER, INCLUDE_NULL_VALUES)
                            AS canonical_row) AS canonical
        UNION ALL
        SELECT N'09-publication-event',
               CONCAT(N'09|', CONVERT(NVARCHAR(36), evidence.execution_id))
                   COLLATE Latin1_General_100_BIN2,
               recon.ufn_archive_row_attestation(
                   N'ctl.execution_publication_event', canonical.canonical_row)
        FROM ctl.execution_publication_event AS evidence WITH (UPDLOCK, HOLDLOCK)
        INNER JOIN ctl.staging_lifecycle_plan_item AS item
            ON item.execution_id = evidence.execution_id AND item.plan_id = @plan_id
        CROSS APPLY (SELECT (SELECT evidence.execution_id, evidence.partition_id,
                                    evidence.previous_published_execution_id,
                                    evidence.published_at_utc,
                                    evidence.incremental_frontier_before_utc,
                                    evidence.incremental_frontier_after_utc,
                                    evidence.watermark_last_partition_id
                             FOR JSON PATH, WITHOUT_ARRAY_WRAPPER, INCLUDE_NULL_VALUES)
                            AS canonical_row) AS canonical
        UNION ALL
        SELECT N'10-promotion-result',
               CONCAT(N'10|', CONVERT(NVARCHAR(36), evidence.execution_id))
                   COLLATE Latin1_General_100_BIN2,
               recon.ufn_archive_row_attestation(
                   N'ctl.execution_promotion_result', canonical.canonical_row)
        FROM ctl.execution_promotion_result AS evidence WITH (UPDLOCK, HOLDLOCK)
        INNER JOIN ctl.staging_lifecycle_plan_item AS item
            ON item.execution_id = evidence.execution_id AND item.plan_id = @plan_id
        CROSS APPLY (SELECT (SELECT evidence.execution_id, evidence.physical_rows,
                                    evidence.distinct_root_keys, evidence.candidate_rows,
                                    evidence.duplicate_rows, evidence.quarantined_root_keys,
                                    evidence.unidentified_quarantine_rows,
                                    evidence.quarantined_stage_rows, evidence.promoted_at_utc
                             FOR JSON PATH, WITHOUT_ARRAY_WRAPPER, INCLUDE_NULL_VALUES)
                            AS canonical_row) AS canonical
    ) AS content_row;

    IF @actual_content_rows <> @expected_content_rows OR @content_root IS NULL
        THROW 51502, N'Conteúdo vivo divergiu do plano limitado.', 1;
END;
GO

CREATE OR ALTER PROCEDURE recon.usp_compute_archive_content_root_internal
    @plan_id UNIQUEIDENTIFIER,
    @expected_content_rows BIGINT,
    @content_root BINARY(32) OUTPUT
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;

    IF @plan_id IS NULL OR @expected_content_rows NOT BETWEEN 0 AND 100000
        THROW 51503, N'Limite inválido para cálculo da raiz do archive.', 1;

    DECLARE @bounded_content_rows BIGINT;
    SELECT @bounded_content_rows = COUNT_BIG(*)
    FROM (
        SELECT TOP (@expected_content_rows + 1) content_key.marker
        FROM (
            SELECT 1 AS marker FROM recon.staging_record_archive AS archived
                WITH (UPDLOCK, HOLDLOCK) WHERE archived.plan_id = @plan_id
            UNION ALL
            SELECT 1 FROM recon.staging_candidate_archive AS archived
                WITH (UPDLOCK, HOLDLOCK) WHERE archived.plan_id = @plan_id
            UNION ALL
            SELECT 1 FROM recon.quarantine_record_archive AS archived
                WITH (UPDLOCK, HOLDLOCK) WHERE archived.plan_id = @plan_id
            UNION ALL
            SELECT 1 FROM recon.execution_candidate_application_archive AS archived
                WITH (UPDLOCK, HOLDLOCK) WHERE archived.plan_id = @plan_id
            UNION ALL
            SELECT 1 FROM recon.execution_reconciliation_result_archive AS archived
                WITH (UPDLOCK, HOLDLOCK) WHERE archived.plan_id = @plan_id
            UNION ALL
            SELECT 1 FROM recon.execution_state_event_archive AS archived
                WITH (UPDLOCK, HOLDLOCK) WHERE archived.plan_id = @plan_id
            UNION ALL
            SELECT 1 FROM recon.execution_page_audit_archive AS archived
                WITH (UPDLOCK, HOLDLOCK) WHERE archived.plan_id = @plan_id
            UNION ALL
            SELECT 1 FROM recon.execution_count_archive AS archived
                WITH (UPDLOCK, HOLDLOCK) WHERE archived.plan_id = @plan_id
            UNION ALL
            SELECT 1 FROM recon.execution_publication_event_archive AS archived
                WITH (UPDLOCK, HOLDLOCK) WHERE archived.plan_id = @plan_id
            UNION ALL
            SELECT 1 FROM recon.execution_promotion_result_archive AS archived
                WITH (UPDLOCK, HOLDLOCK) WHERE archived.plan_id = @plan_id
        ) AS content_key
    ) AS bounded;

    IF @bounded_content_rows <> @expected_content_rows
        THROW 51504, N'Conteúdo do archive divergiu do manifesto limitado.', 1;

    DECLARE @actual_content_rows BIGINT;
    SELECT @actual_content_rows = COUNT_BIG(*),
           @content_root = CONVERT(BINARY(32), HASHBYTES(
               'SHA2_256',
               CONVERT(VARBINARY(MAX), CONCAT(N'archive-content-set-v1|', COALESCE(STRING_AGG(
                   CONVERT(NVARCHAR(MAX), CONCAT(
                       content_row.object_code, N'|',
                       CONVERT(VARCHAR(64), content_row.row_attestation, 2), N';'
                   )), N'') WITHIN GROUP (ORDER BY content_row.sort_key), N'')))
           ))
    FROM (
        SELECT N'01-staging-record' AS object_code,
               CONCAT(N'01|', CONVERT(NVARCHAR(36), archived.execution_id), N'|',
                      RIGHT(REPLICATE(N'0', 20)
                          + CONVERT(NVARCHAR(20), archived.stage_record_id), 20))
                   COLLATE Latin1_General_100_BIN2 AS sort_key,
               archived.archive_row_attestation AS row_attestation
        FROM recon.staging_record_archive AS archived WITH (UPDLOCK, HOLDLOCK)
        WHERE archived.plan_id = @plan_id
        UNION ALL
        SELECT N'02-staging-candidate',
               CONCAT(N'02|', CONVERT(NVARCHAR(36), archived.execution_id), N'|',
                      archived.source_key) COLLATE Latin1_General_100_BIN2,
               archived.archive_row_attestation
        FROM recon.staging_candidate_archive AS archived WITH (UPDLOCK, HOLDLOCK)
        WHERE archived.plan_id = @plan_id
        UNION ALL
        SELECT N'03-quarantine-record',
               CONCAT(N'03|', CONVERT(NVARCHAR(36), archived.execution_id), N'|',
                      RIGHT(REPLICATE(N'0', 20)
                          + CONVERT(NVARCHAR(20), archived.quarantine_record_id), 20))
                   COLLATE Latin1_General_100_BIN2,
               archived.archive_row_attestation
        FROM recon.quarantine_record_archive AS archived WITH (UPDLOCK, HOLDLOCK)
        WHERE archived.plan_id = @plan_id
        UNION ALL
        SELECT N'04-candidate-application',
               CONCAT(N'04|', CONVERT(NVARCHAR(36), archived.execution_id), N'|',
                      archived.source_key) COLLATE Latin1_General_100_BIN2,
               archived.archive_row_attestation
        FROM recon.execution_candidate_application_archive AS archived WITH (UPDLOCK, HOLDLOCK)
        WHERE archived.plan_id = @plan_id
        UNION ALL
        SELECT N'05-reconciliation-result',
               CONCAT(N'05|', CONVERT(NVARCHAR(36), archived.execution_id))
                   COLLATE Latin1_General_100_BIN2,
               archived.archive_row_attestation
        FROM recon.execution_reconciliation_result_archive AS archived WITH (UPDLOCK, HOLDLOCK)
        WHERE archived.plan_id = @plan_id
        UNION ALL
        SELECT N'06-state-event',
               CONCAT(N'06|', CONVERT(NVARCHAR(36), archived.execution_id), N'|',
                      RIGHT(REPLICATE(N'0', 20)
                          + CONVERT(NVARCHAR(20), archived.state_event_id), 20))
                   COLLATE Latin1_General_100_BIN2,
               archived.archive_row_attestation
        FROM recon.execution_state_event_archive AS archived WITH (UPDLOCK, HOLDLOCK)
        WHERE archived.plan_id = @plan_id
        UNION ALL
        SELECT N'07-page-audit',
               CONCAT(N'07|', CONVERT(NVARCHAR(36), archived.execution_id), N'|',
                      RIGHT(REPLICATE(N'0', 20)
                          + CONVERT(NVARCHAR(20), archived.page_audit_id), 20))
                   COLLATE Latin1_General_100_BIN2,
               archived.archive_row_attestation
        FROM recon.execution_page_audit_archive AS archived WITH (UPDLOCK, HOLDLOCK)
        WHERE archived.plan_id = @plan_id
        UNION ALL
        SELECT N'08-execution-count',
               CONCAT(N'08|', CONVERT(NVARCHAR(36), archived.execution_id), N'|',
                      RIGHT(REPLICATE(N'0', 20)
                          + CONVERT(NVARCHAR(20), archived.count_id), 20))
                   COLLATE Latin1_General_100_BIN2,
               archived.archive_row_attestation
        FROM recon.execution_count_archive AS archived WITH (UPDLOCK, HOLDLOCK)
        WHERE archived.plan_id = @plan_id
        UNION ALL
        SELECT N'09-publication-event',
               CONCAT(N'09|', CONVERT(NVARCHAR(36), archived.execution_id))
                   COLLATE Latin1_General_100_BIN2,
               archived.archive_row_attestation
        FROM recon.execution_publication_event_archive AS archived WITH (UPDLOCK, HOLDLOCK)
        WHERE archived.plan_id = @plan_id
        UNION ALL
        SELECT N'10-promotion-result',
               CONCAT(N'10|', CONVERT(NVARCHAR(36), archived.execution_id))
                   COLLATE Latin1_General_100_BIN2,
               archived.archive_row_attestation
        FROM recon.execution_promotion_result_archive AS archived WITH (UPDLOCK, HOLDLOCK)
        WHERE archived.plan_id = @plan_id
    ) AS content_row;

    IF @actual_content_rows <> @expected_content_rows OR @content_root IS NULL
        THROW 51504, N'Conteúdo do archive divergiu do manifesto limitado.', 1;
END;
GO

CREATE TABLE ctl.staging_lifecycle_purge_event (
    plan_id UNIQUEIDENTIFIER NOT NULL,
    archive_fingerprint_version NVARCHAR(128) COLLATE Latin1_General_100_BIN2 NOT NULL,
    archive_fingerprint CHAR(64) NOT NULL,
    purged_executions BIGINT NOT NULL,
    purged_stage_rows BIGINT NOT NULL,
    purged_candidate_rows BIGINT NOT NULL,
    purged_at_utc DATETIME2(3) NOT NULL,
    CONSTRAINT PK_ctl_staging_lifecycle_purge_event PRIMARY KEY CLUSTERED (plan_id),
    CONSTRAINT FK_ctl_staging_lifecycle_purge_event_manifest
        FOREIGN KEY (plan_id) REFERENCES recon.staging_lifecycle_archive_manifest (plan_id),
    CONSTRAINT CK_ctl_staging_lifecycle_purge_event_counts CHECK (
        purged_executions >= 0 AND purged_stage_rows >= 0 AND purged_candidate_rows >= 0
    )
);
GO

CREATE TABLE recon.staging_restore_session (
    restore_id UNIQUEIDENTIFIER NOT NULL,
    plan_id UNIQUEIDENTIFIER NOT NULL,
    execution_id UNIQUEIDENTIFIER NOT NULL,
    archive_fingerprint_version NVARCHAR(128) COLLATE Latin1_General_100_BIN2 NOT NULL,
    archive_fingerprint CHAR(64) NOT NULL,
    reason_code NVARCHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL,
    maximum_stage_rows BIGINT NOT NULL,
    maximum_candidate_rows BIGINT NOT NULL,
    restored_stage_rows BIGINT NOT NULL,
    restored_candidate_rows BIGINT NOT NULL,
    restored_at_utc DATETIME2(3) NOT NULL,
    CONSTRAINT PK_recon_staging_restore_session PRIMARY KEY CLUSTERED (restore_id),
    CONSTRAINT UQ_recon_staging_restore_session_materialization
        UNIQUE (plan_id, execution_id),
    CONSTRAINT FK_recon_staging_restore_session_manifest
        FOREIGN KEY (plan_id) REFERENCES recon.staging_lifecycle_archive_manifest (plan_id),
    CONSTRAINT FK_recon_staging_restore_session_plan_item
        FOREIGN KEY (plan_id, execution_id)
        REFERENCES ctl.staging_lifecycle_plan_item (plan_id, execution_id),
    CONSTRAINT CK_recon_staging_restore_session_reason CHECK (
        LEFT(reason_code, 1) COLLATE Latin1_General_100_BIN2 LIKE '[A-Z]'
        AND reason_code COLLATE Latin1_General_100_BIN2 NOT LIKE '%[^A-Z0-9_]%'
        AND LEN(reason_code) BETWEEN 2 AND 64
    ),
    CONSTRAINT CK_recon_staging_restore_session_counts CHECK (
        maximum_stage_rows BETWEEN 1 AND 1000000
        AND maximum_candidate_rows BETWEEN 1 AND 1000000
        AND restored_stage_rows BETWEEN 0 AND maximum_stage_rows
        AND restored_candidate_rows BETWEEN 0 AND maximum_candidate_rows
    )
);
GO

CREATE TABLE recon.staging_restore_record (
    restore_id UNIQUEIDENTIFIER NOT NULL,
    stage_record_id BIGINT NOT NULL,
    execution_id UNIQUEIDENTIFIER NOT NULL,
    input_batch_number INT NOT NULL,
    input_record_ordinal INT NOT NULL,
    source_key NVARCHAR(256) COLLATE Latin1_General_100_BIN2 NULL,
    row_fingerprint_version NVARCHAR(128) COLLATE Latin1_General_100_BIN2 NULL,
    source_row_hash CHAR(64) NULL,
    presence_fingerprint_version NVARCHAR(128) COLLATE Latin1_General_100_BIN2 NULL,
    presence_fingerprint CHAR(64) NULL,
    source_freshness_at_utc DATETIME2(3) NULL,
    validation_disposition NVARCHAR(16) COLLATE Latin1_General_100_BIN2 NOT NULL,
    quarantine_reason_code NVARCHAR(64) COLLATE Latin1_General_100_BIN2 NULL,
    staged_at_utc DATETIME2(3) NOT NULL,
    CONSTRAINT PK_recon_staging_restore_record
        PRIMARY KEY CLUSTERED (restore_id, stage_record_id),
    CONSTRAINT FK_recon_staging_restore_record_session
        FOREIGN KEY (restore_id) REFERENCES recon.staging_restore_session (restore_id)
);
GO

CREATE TABLE recon.staging_restore_candidate (
    restore_id UNIQUEIDENTIFIER NOT NULL,
    execution_id UNIQUEIDENTIFIER NOT NULL,
    source_key NVARCHAR(256) COLLATE Latin1_General_100_BIN2 NOT NULL,
    winner_stage_record_id BIGINT NOT NULL,
    row_fingerprint_version NVARCHAR(128) COLLATE Latin1_General_100_BIN2 NOT NULL,
    source_row_hash CHAR(64) NOT NULL,
    presence_fingerprint_version NVARCHAR(128) COLLATE Latin1_General_100_BIN2 NOT NULL,
    presence_fingerprint CHAR(64) NOT NULL,
    source_freshness_at_utc DATETIME2(3) NULL,
    prepared_at_utc DATETIME2(3) NOT NULL,
    CONSTRAINT PK_recon_staging_restore_candidate
        PRIMARY KEY CLUSTERED (restore_id, execution_id, source_key),
    CONSTRAINT FK_recon_staging_restore_candidate_session
        FOREIGN KEY (restore_id) REFERENCES recon.staging_restore_session (restore_id),
    CONSTRAINT FK_recon_staging_restore_candidate_winner
        FOREIGN KEY (restore_id, winner_stage_record_id)
        REFERENCES recon.staging_restore_record (restore_id, stage_record_id)
);
GO

-- Depois do archive obrigatório, as evidências recon permanecem ligadas ao row vivo ou à cópia
-- tipada. Os triggers substituem as duas FKs que impediriam o purge físico somente de stg.
CREATE OR ALTER TRIGGER recon.trg_quarantine_record_staging_lineage
ON recon.quarantine_record
AFTER INSERT, UPDATE
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    IF EXISTS (
        SELECT 1
        FROM inserted AS evidence
        WHERE NOT EXISTS (
            SELECT 1 FROM stg.execution_record AS live_record
            WHERE live_record.stage_record_id = evidence.stage_record_id
              AND live_record.execution_id = evidence.execution_id
        )
          AND NOT EXISTS (
            SELECT 1 FROM recon.staging_record_archive AS archived_record
            WHERE archived_record.stage_record_id = evidence.stage_record_id
              AND archived_record.execution_id = evidence.execution_id
        )
    )
        THROW 51500, N'Evidência de quarentena sem lineage de staging verificável.', 1;
END;
GO

CREATE OR ALTER TRIGGER recon.trg_candidate_application_staging_lineage
ON recon.execution_candidate_application
AFTER INSERT, UPDATE
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    IF EXISTS (
        SELECT 1
        FROM inserted AS evidence
        WHERE NOT EXISTS (
            SELECT 1 FROM stg.execution_candidate AS live_candidate
            WHERE live_candidate.execution_id = evidence.execution_id
              AND live_candidate.source_key = evidence.source_key
        )
          AND NOT EXISTS (
            SELECT 1 FROM recon.staging_candidate_archive AS archived_candidate
            WHERE archived_candidate.execution_id = evidence.execution_id
              AND archived_candidate.source_key = evidence.source_key
        )
    )
        THROW 51501, N'Evidência de aplicação sem lineage de staging verificável.', 1;
END;
GO

CREATE OR ALTER TRIGGER stg.trg_execution_record_lifecycle_delete_guard
ON stg.execution_record
AFTER DELETE
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    IF EXISTS (
        SELECT 1
        FROM deleted AS removed
        WHERE NOT EXISTS (
            SELECT 1
            FROM recon.staging_record_archive AS archived
            WHERE archived.stage_record_id = removed.stage_record_id
              AND archived.execution_id = removed.execution_id
        )
    )
        THROW 51505, N'Delete de staging sem archive tipado é proibido.', 1;
END;
GO

CREATE OR ALTER TRIGGER stg.trg_execution_candidate_lifecycle_delete_guard
ON stg.execution_candidate
AFTER DELETE
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    IF EXISTS (
        SELECT 1
        FROM deleted AS removed
        WHERE NOT EXISTS (
            SELECT 1
            FROM recon.staging_candidate_archive AS archived
            WHERE archived.execution_id = removed.execution_id
              AND archived.source_key = removed.source_key
        )
    )
        THROW 51506, N'Delete de candidate sem archive tipado é proibido.', 1;
END;
GO

ALTER TABLE recon.quarantine_record
    DROP CONSTRAINT FK_recon_quarantine_record_stage;
GO

ALTER TABLE recon.execution_candidate_application
    DROP CONSTRAINT FK_recon_execution_candidate_application_candidate;
GO

CREATE OR ALTER PROCEDURE ctl.usp_approve_staging_retention_policy
    @policy_id UNIQUEIDENTIFIER,
    @environment_name NVARCHAR(MAX),
    @source_instance NVARCHAR(MAX),
    @tenant_scope NVARCHAR(MAX),
    @entity_name NVARCHAR(MAX),
    @terminal_outcome_class NVARCHAR(MAX),
    @policy_version NVARCHAR(MAX),
    @policy_fingerprint NVARCHAR(MAX),
    @retention_days INT,
    @data_owner_evidence_fingerprint NVARCHAR(MAX),
    @data_owner_role NVARCHAR(MAX),
    @compliance_evidence_fingerprint NVARCHAR(MAX),
    @compliance_owner_role NVARCHAR(MAX)
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;

    IF @policy_id IS NULL OR @environment_name IS NULL OR @source_instance IS NULL
        OR @tenant_scope IS NULL OR @entity_name IS NULL
        OR @terminal_outcome_class IS NULL OR @policy_version IS NULL
        OR @policy_fingerprint IS NULL OR @retention_days IS NULL
        OR @data_owner_evidence_fingerprint IS NULL OR @data_owner_role IS NULL
        OR @compliance_evidence_fingerprint IS NULL OR @compliance_owner_role IS NULL
        OR DATALENGTH(@environment_name) > 64
        OR DATALENGTH(@source_instance) > 256
        OR DATALENGTH(@tenant_scope) > 256
        OR DATALENGTH(@entity_name) > 256
        OR DATALENGTH(@terminal_outcome_class) > 64
        OR DATALENGTH(@policy_version) > 256
        OR DATALENGTH(@policy_fingerprint) > 128
        OR DATALENGTH(@data_owner_evidence_fingerprint) > 128
        OR DATALENGTH(@data_owner_role) > 256
        OR DATALENGTH(@compliance_evidence_fingerprint) > 128
        OR DATALENGTH(@compliance_owner_role) > 256
        THROW 51502, N'Política de retenção excede os limites do contrato.', 1;

    SET @environment_name = LTRIM(RTRIM(@environment_name));
    SET @source_instance = LTRIM(RTRIM(@source_instance));
    SET @tenant_scope = LTRIM(RTRIM(@tenant_scope));
    SET @entity_name = LTRIM(RTRIM(@entity_name));
    SET @terminal_outcome_class = LTRIM(RTRIM(@terminal_outcome_class));
    SET @policy_version = LTRIM(RTRIM(@policy_version));
    SET @policy_fingerprint = LOWER(LTRIM(RTRIM(@policy_fingerprint)));
    SET @data_owner_evidence_fingerprint =
        LOWER(LTRIM(RTRIM(@data_owner_evidence_fingerprint)));
    SET @data_owner_role = LTRIM(RTRIM(@data_owner_role));
    SET @compliance_evidence_fingerprint =
        LOWER(LTRIM(RTRIM(@compliance_evidence_fingerprint)));
    SET @compliance_owner_role = LTRIM(RTRIM(@compliance_owner_role));

    IF NULLIF(@environment_name, N'') IS NULL
        OR NULLIF(@source_instance, N'') IS NULL
        OR NULLIF(@tenant_scope, N'') IS NULL
        OR NULLIF(@entity_name, N'') IS NULL
        OR @terminal_outcome_class COLLATE Latin1_General_100_BIN2
            NOT IN (N'PUBLISHED', N'NON_PUBLISHED_TERMINAL')
        OR NULLIF(@policy_version, N'') IS NULL
        OR LEN(@policy_fingerprint) <> 64
        OR @policy_fingerprint COLLATE Latin1_General_100_BIN2 LIKE '%[^0-9a-f]%'
        OR @retention_days NOT BETWEEN 1 AND 36500
        OR LEN(@data_owner_evidence_fingerprint) <> 64
        OR @data_owner_evidence_fingerprint COLLATE Latin1_General_100_BIN2
            LIKE '%[^0-9a-f]%'
        OR NULLIF(@data_owner_role, N'') IS NULL
        OR LEN(@compliance_evidence_fingerprint) <> 64
        OR @compliance_evidence_fingerprint COLLATE Latin1_General_100_BIN2
            LIKE '%[^0-9a-f]%'
        OR NULLIF(@compliance_owner_role, N'') IS NULL
        OR @data_owner_evidence_fingerprint COLLATE Latin1_General_100_BIN2 =
            @compliance_evidence_fingerprint COLLATE Latin1_General_100_BIN2
        OR @data_owner_role COLLATE Latin1_General_100_BIN2 =
            @compliance_owner_role COLLATE Latin1_General_100_BIN2
        THROW 51503, N'Política de retenção inválida ou não ratificada.', 1;

    BEGIN TRANSACTION;

    DECLARE @governance_lock_result INT;
    EXEC @governance_lock_result = sys.sp_getapplock
        @Resource = N'V2_STAGING_LIFECYCLE', @LockMode = N'Exclusive',
        @LockOwner = N'Transaction', @LockTimeout = 5000,
        @DbPrincipal = N'public';
    IF @governance_lock_result < 0
        THROW 51518, N'Não foi possível obter o lock de governança de retenção.', 1;

    IF EXISTS (
        SELECT 1
        FROM ctl.staging_retention_policy AS policy WITH (UPDLOCK, HOLDLOCK)
        WHERE policy.policy_id = @policy_id
          AND environment_name = @environment_name
          AND source_instance = @source_instance
          AND tenant_scope = @tenant_scope
          AND entity_name = @entity_name
          AND terminal_outcome_class = @terminal_outcome_class
          AND policy_version = @policy_version
          AND policy_fingerprint = @policy_fingerprint
          AND retention_days = @retention_days
          AND data_owner_evidence_fingerprint = @data_owner_evidence_fingerprint
          AND data_owner_role = @data_owner_role
          AND compliance_evidence_fingerprint = @compliance_evidence_fingerprint
          AND compliance_owner_role = @compliance_owner_role
          AND revoked_at_utc IS NULL
          AND EXISTS (
              SELECT 1 FROM ctl.staging_retention_policy_event AS owner_event
              WHERE owner_event.policy_id = policy.policy_id
                AND owner_event.event_action = N'DATA_OWNER_APPROVED'
                AND owner_event.authority_evidence_fingerprint =
                    policy.data_owner_evidence_fingerprint
                AND owner_event.owner_role = policy.data_owner_role
                AND owner_event.occurred_at_utc = policy.approved_at_utc
          )
          AND EXISTS (
              SELECT 1 FROM ctl.staging_retention_policy_event AS compliance_event
              WHERE compliance_event.policy_id = policy.policy_id
                AND compliance_event.event_action = N'COMPLIANCE_APPROVED'
                AND compliance_event.authority_evidence_fingerprint =
                    policy.compliance_evidence_fingerprint
                AND compliance_event.owner_role = policy.compliance_owner_role
                AND compliance_event.occurred_at_utc = policy.approved_at_utc
          )
          AND NOT EXISTS (
              SELECT 1 FROM ctl.staging_retention_policy_event AS revoked_event
              WHERE revoked_event.policy_id = policy.policy_id
                AND revoked_event.event_action = N'REVOKED'
          )
    )
    BEGIN
        COMMIT TRANSACTION;
        SELECT @policy_id AS policy_id, CAST(1 AS BIT) AS exact_retry;
        RETURN;
    END;

    IF EXISTS (
        SELECT 1 FROM ctl.staging_retention_policy WITH (UPDLOCK, HOLDLOCK)
        WHERE policy_id = @policy_id
    )
        THROW 51504, N'Retry divergente de política de retenção.', 1;

    IF EXISTS (
        SELECT 1 FROM ctl.staging_retention_policy WITH (UPDLOCK, HOLDLOCK)
        WHERE environment_name = @environment_name
          AND source_instance = @source_instance
          AND tenant_scope = @tenant_scope
          AND entity_name = @entity_name
          AND terminal_outcome_class = @terminal_outcome_class
          AND revoked_at_utc IS NULL
    )
        THROW 51519, N'Já existe política ativa no escopo de retenção.', 1;

    IF NOT EXISTS (
        SELECT 1 FROM ctl.source_catalog WITH (UPDLOCK, HOLDLOCK)
        WHERE source_instance = @source_instance AND active = 1
    )
        THROW 51505, N'Fonte da política não está registrada e ativa.', 1;

    DECLARE @approved_at_utc DATETIME2(3) = SYSUTCDATETIME();

    INSERT INTO ctl.staging_retention_policy (
        policy_id, environment_name, source_instance, tenant_scope, entity_name,
        terminal_outcome_class, policy_version, policy_fingerprint, retention_days,
        data_owner_evidence_fingerprint, data_owner_role,
        compliance_evidence_fingerprint, compliance_owner_role,
        approved_at_utc, revoked_at_utc
    ) VALUES (
        @policy_id, @environment_name, @source_instance, @tenant_scope, @entity_name,
        @terminal_outcome_class, @policy_version, @policy_fingerprint, @retention_days,
        @data_owner_evidence_fingerprint, @data_owner_role,
        @compliance_evidence_fingerprint, @compliance_owner_role,
        @approved_at_utc, NULL
    );

    INSERT INTO ctl.staging_retention_policy_event (
        policy_id, event_action, authority_evidence_fingerprint, owner_role, occurred_at_utc
    ) VALUES (
        @policy_id, N'DATA_OWNER_APPROVED',
        @data_owner_evidence_fingerprint, @data_owner_role, @approved_at_utc
    ), (
        @policy_id, N'COMPLIANCE_APPROVED',
        @compliance_evidence_fingerprint, @compliance_owner_role, @approved_at_utc
    );

    COMMIT TRANSACTION;
    SELECT @policy_id AS policy_id, CAST(0 AS BIT) AS exact_retry;
END;
GO

CREATE OR ALTER PROCEDURE ctl.usp_revoke_staging_retention_policy
    @policy_id UNIQUEIDENTIFIER,
    @policy_version NVARCHAR(MAX),
    @policy_fingerprint NVARCHAR(MAX),
    @authority_evidence_fingerprint NVARCHAR(MAX),
    @owner_role NVARCHAR(MAX)
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;

    IF @policy_id IS NULL OR @policy_version IS NULL OR @policy_fingerprint IS NULL
        OR @authority_evidence_fingerprint IS NULL OR @owner_role IS NULL
        OR DATALENGTH(@policy_version) > 256
        OR DATALENGTH(@policy_fingerprint) > 128
        OR DATALENGTH(@authority_evidence_fingerprint) > 128
        OR DATALENGTH(@owner_role) > 256
        THROW 51506, N'Revogação de política excede os limites do contrato.', 1;

    SET @policy_version = LTRIM(RTRIM(@policy_version));
    SET @policy_fingerprint = LOWER(LTRIM(RTRIM(@policy_fingerprint)));
    SET @authority_evidence_fingerprint =
        LOWER(LTRIM(RTRIM(@authority_evidence_fingerprint)));
    SET @owner_role = LTRIM(RTRIM(@owner_role));

    IF NULLIF(@policy_version, N'') IS NULL
        OR LEN(@policy_fingerprint) <> 64
        OR @policy_fingerprint COLLATE Latin1_General_100_BIN2 LIKE '%[^0-9a-f]%'
        OR LEN(@authority_evidence_fingerprint) <> 64
        OR @authority_evidence_fingerprint COLLATE Latin1_General_100_BIN2
            LIKE '%[^0-9a-f]%'
        OR NULLIF(@owner_role, N'') IS NULL
        THROW 51507, N'Revogação de política inválida.', 1;

    BEGIN TRANSACTION;

    DECLARE @governance_lock_result INT;
    EXEC @governance_lock_result = sys.sp_getapplock
        @Resource = N'V2_STAGING_LIFECYCLE', @LockMode = N'Exclusive',
        @LockOwner = N'Transaction', @LockTimeout = 5000,
        @DbPrincipal = N'public';
    IF @governance_lock_result < 0
        THROW 51518, N'Não foi possível obter o lock de governança de retenção.', 1;

    DECLARE @revoked_at_utc DATETIME2(3);
    SELECT @revoked_at_utc = revoked_at_utc
    FROM ctl.staging_retention_policy AS policy WITH (UPDLOCK, HOLDLOCK)
    WHERE policy.policy_id = @policy_id
      AND policy_version = @policy_version
      AND policy_fingerprint = @policy_fingerprint;

    IF @@ROWCOUNT <> 1
        THROW 51508, N'Política de retenção não encontrada para revogação.', 1;

    IF @revoked_at_utc IS NOT NULL
    BEGIN
        IF NOT EXISTS (
            SELECT 1 FROM ctl.staging_retention_policy_event
            WHERE policy_id = @policy_id
              AND event_action = N'REVOKED'
              AND authority_evidence_fingerprint = @authority_evidence_fingerprint
              AND owner_role = @owner_role
              AND occurred_at_utc = @revoked_at_utc
        )
            THROW 51509, N'Retry divergente de revogação da política.', 1;

        COMMIT TRANSACTION;
        SELECT @policy_id AS policy_id, @revoked_at_utc AS revoked_at_utc,
               CAST(1 AS BIT) AS exact_retry;
        RETURN;
    END;

    SET @revoked_at_utc = SYSUTCDATETIME();
    UPDATE ctl.staging_retention_policy
    SET revoked_at_utc = @revoked_at_utc
    WHERE policy_id = @policy_id AND revoked_at_utc IS NULL;

    INSERT INTO ctl.staging_retention_policy_event (
        policy_id, event_action, authority_evidence_fingerprint, owner_role, occurred_at_utc
    ) VALUES (
        @policy_id, N'REVOKED', @authority_evidence_fingerprint, @owner_role, @revoked_at_utc
    );

    COMMIT TRANSACTION;
    SELECT @policy_id AS policy_id, @revoked_at_utc AS revoked_at_utc,
           CAST(0 AS BIT) AS exact_retry;
END;
GO

CREATE OR ALTER PROCEDURE ctl.usp_place_staging_legal_hold
    @hold_id UNIQUEIDENTIFIER,
    @execution_id UNIQUEIDENTIFIER,
    @reason_code NVARCHAR(MAX),
    @authority_evidence_fingerprint NVARCHAR(MAX),
    @owner_role NVARCHAR(MAX)
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;

    IF @hold_id IS NULL OR @execution_id IS NULL OR @reason_code IS NULL
        OR @authority_evidence_fingerprint IS NULL OR @owner_role IS NULL
        OR DATALENGTH(@reason_code) > 128
        OR DATALENGTH(@authority_evidence_fingerprint) > 128
        OR DATALENGTH(@owner_role) > 256
        THROW 51510, N'Legal hold excede os limites do contrato.', 1;

    SET @reason_code = LTRIM(RTRIM(@reason_code));
    SET @authority_evidence_fingerprint =
        LOWER(LTRIM(RTRIM(@authority_evidence_fingerprint)));
    SET @owner_role = LTRIM(RTRIM(@owner_role));

    IF LEFT(@reason_code, 1) COLLATE Latin1_General_100_BIN2 NOT LIKE '[A-Z]'
        OR @reason_code COLLATE Latin1_General_100_BIN2 LIKE '%[^A-Z0-9_]%'
        OR LEN(@reason_code) NOT BETWEEN 2 AND 64
        OR LEN(@authority_evidence_fingerprint) <> 64
        OR @authority_evidence_fingerprint COLLATE Latin1_General_100_BIN2
            LIKE '%[^0-9a-f]%'
        OR NULLIF(@owner_role, N'') IS NULL
        THROW 51511, N'Legal hold inválido.', 1;

    BEGIN TRANSACTION;

    DECLARE @hold_lock_result INT;
    EXEC @hold_lock_result = sys.sp_getapplock
        @Resource = N'V2_STAGING_LIFECYCLE', @LockMode = N'Exclusive',
        @LockOwner = N'Transaction', @LockTimeout = 5000,
        @DbPrincipal = N'public';
    IF @hold_lock_result < 0
        THROW 51518, N'Não foi possível obter o lock de governança de retenção.', 1;

    DECLARE @maximum_hold_ledger_rows_per_execution BIGINT = 4096;
    DECLARE @hold_ledger_base_rows BIGINT;
    DECLARE @hold_ledger_event_rows BIGINT;
    SELECT @hold_ledger_base_rows = COUNT_BIG(*)
    FROM (
        SELECT TOP (@maximum_hold_ledger_rows_per_execution + 1) hold_definition.hold_id
        FROM ctl.staging_legal_hold AS hold_definition WITH (UPDLOCK, HOLDLOCK)
        WHERE hold_definition.execution_id = @execution_id
    ) AS bounded_hold;
    IF @hold_ledger_base_rows > @maximum_hold_ledger_rows_per_execution
        THROW 51528, N'Histórico de legal hold excede o teto técnico por execução.', 1;
    DECLARE @remaining_hold_ledger_rows BIGINT =
        @maximum_hold_ledger_rows_per_execution - @hold_ledger_base_rows;
    SELECT @hold_ledger_event_rows = COUNT_BIG(*)
    FROM (
        SELECT TOP (@remaining_hold_ledger_rows + 1) hold_event.hold_event_id
        FROM ctl.staging_legal_hold_event AS hold_event WITH (UPDLOCK, HOLDLOCK)
        WHERE hold_event.execution_id = @execution_id
    ) AS bounded_event;
    IF @hold_ledger_event_rows > @remaining_hold_ledger_rows
        THROW 51528, N'Histórico de legal hold excede o teto técnico por execução.', 1;

    IF EXISTS (
        SELECT 1 FROM ctl.staging_legal_hold WITH (UPDLOCK, HOLDLOCK)
        WHERE hold_id = @hold_id
    ) AND EXISTS (
        SELECT 1 FROM ctl.ufn_invalid_staging_legal_hold(@execution_id)
        WHERE hold_id = @hold_id
    )
        THROW 51526, N'Ledger do legal hold diverge do estado governado.', 1;

    IF EXISTS (
        SELECT 1
        FROM ctl.staging_legal_hold AS hold_definition WITH (UPDLOCK, HOLDLOCK)
        WHERE hold_definition.hold_id = @hold_id
          AND hold_definition.execution_id = @execution_id
          AND hold_definition.reason_code = @reason_code
          AND hold_definition.authority_evidence_fingerprint =
              @authority_evidence_fingerprint
          AND hold_definition.owner_role = @owner_role
          AND hold_definition.released_at_utc IS NULL
          AND EXISTS (
              SELECT 1
              FROM ctl.staging_legal_hold_event AS placed_event
              WHERE placed_event.hold_id = hold_definition.hold_id
                AND placed_event.execution_id = hold_definition.execution_id
                AND placed_event.event_action = N'PLACED'
                AND placed_event.reason_code = hold_definition.reason_code
                AND placed_event.authority_evidence_fingerprint =
                    hold_definition.authority_evidence_fingerprint
                AND placed_event.owner_role = hold_definition.owner_role
                AND placed_event.hold_scope = hold_definition.hold_scope
                AND placed_event.occurred_at_utc = hold_definition.held_at_utc
          )
    )
    BEGIN
        DECLARE @existing_held_at_utc DATETIME2(3) = (
            SELECT held_at_utc FROM ctl.staging_legal_hold WHERE hold_id = @hold_id
        );
        COMMIT TRANSACTION;
        SELECT @hold_id AS hold_id, @execution_id AS execution_id,
               @existing_held_at_utc AS changed_at_utc, CAST(1 AS BIT) AS active,
               CAST(1 AS BIT) AS exact_retry;
        RETURN;
    END;

    IF EXISTS (SELECT 1 FROM ctl.staging_legal_hold WITH (UPDLOCK, HOLDLOCK)
               WHERE hold_id = @hold_id)
        THROW 51512, N'Retry divergente de legal hold.', 1;

    IF NOT EXISTS (SELECT 1 FROM ctl.execution_attempt WITH (UPDLOCK, HOLDLOCK)
                   WHERE execution_id = @execution_id)
        THROW 51513, N'Execução do legal hold não existe.', 1;

    IF @hold_ledger_base_rows + @hold_ledger_event_rows >
            @maximum_hold_ledger_rows_per_execution - 3
        THROW 51528, N'Novo legal hold excederia o teto técnico por execução.', 1;

    DECLARE @hold_scope NVARCHAR(32) = CASE WHEN EXISTS (
        SELECT 1
        FROM ctl.staging_lifecycle_purge_event AS purge_event WITH (UPDLOCK, HOLDLOCK)
        INNER JOIN ctl.staging_lifecycle_plan_item AS item
            ON item.plan_id = purge_event.plan_id
        WHERE item.execution_id = @execution_id
    ) THEN N'ARCHIVE_ONLY' ELSE N'STAGING_AND_ARCHIVE' END;

    DECLARE @held_at_utc DATETIME2(3) = SYSUTCDATETIME();
    INSERT INTO ctl.staging_legal_hold (
        hold_id, execution_id, reason_code, authority_evidence_fingerprint, owner_role, hold_scope,
        held_at_utc, release_authority_evidence_fingerprint, release_owner_role, released_at_utc
    ) VALUES (
        @hold_id, @execution_id, @reason_code, @authority_evidence_fingerprint, @owner_role,
        @hold_scope,
        @held_at_utc, NULL, NULL, NULL
    );
    INSERT INTO ctl.staging_legal_hold_event (
        hold_id, execution_id, event_action, reason_code,
        authority_evidence_fingerprint, owner_role, hold_scope, occurred_at_utc
    ) VALUES (
        @hold_id, @execution_id, N'PLACED', @reason_code,
        @authority_evidence_fingerprint, @owner_role, @hold_scope, @held_at_utc
    );

    COMMIT TRANSACTION;
    SELECT @hold_id AS hold_id, @execution_id AS execution_id,
           @held_at_utc AS changed_at_utc, CAST(1 AS BIT) AS active,
           CAST(0 AS BIT) AS exact_retry;
END;
GO

CREATE OR ALTER PROCEDURE ctl.usp_release_staging_legal_hold
    @hold_id UNIQUEIDENTIFIER,
    @authority_evidence_fingerprint NVARCHAR(MAX),
    @owner_role NVARCHAR(MAX)
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;

    IF @hold_id IS NULL OR @authority_evidence_fingerprint IS NULL OR @owner_role IS NULL
        OR DATALENGTH(@authority_evidence_fingerprint) > 128
        OR DATALENGTH(@owner_role) > 256
        THROW 51514, N'Liberação de legal hold excede os limites do contrato.', 1;

    SET @authority_evidence_fingerprint =
        LOWER(LTRIM(RTRIM(@authority_evidence_fingerprint)));
    SET @owner_role = LTRIM(RTRIM(@owner_role));
    IF LEN(@authority_evidence_fingerprint) <> 64
        OR @authority_evidence_fingerprint COLLATE Latin1_General_100_BIN2
            LIKE '%[^0-9a-f]%'
        OR NULLIF(@owner_role, N'') IS NULL
        THROW 51515, N'Liberação de legal hold inválida.', 1;

    BEGIN TRANSACTION;
    DECLARE @hold_lock_result INT;
    EXEC @hold_lock_result = sys.sp_getapplock
        @Resource = N'V2_STAGING_LIFECYCLE', @LockMode = N'Exclusive',
        @LockOwner = N'Transaction', @LockTimeout = 5000,
        @DbPrincipal = N'public';
    IF @hold_lock_result < 0
        THROW 51518, N'Não foi possível obter o lock de governança de retenção.', 1;
    DECLARE @execution_id UNIQUEIDENTIFIER;
    DECLARE @reason_code NVARCHAR(64);
    DECLARE @hold_scope NVARCHAR(32);
    DECLARE @stored_release_authority_evidence_fingerprint CHAR(64);
    DECLARE @stored_release_owner_role NVARCHAR(128);
    DECLARE @released_at_utc DATETIME2(3);
    SELECT @execution_id = execution_id, @reason_code = reason_code,
           @hold_scope = hold_scope,
           @stored_release_authority_evidence_fingerprint =
               release_authority_evidence_fingerprint,
           @stored_release_owner_role = release_owner_role,
           @released_at_utc = released_at_utc
    FROM ctl.staging_legal_hold WITH (UPDLOCK, HOLDLOCK)
    WHERE hold_id = @hold_id;

    IF @execution_id IS NULL
        THROW 51516, N'Legal hold não encontrado para liberação.', 1;

    DECLARE @maximum_hold_ledger_rows_per_execution BIGINT = 4096;
    DECLARE @hold_ledger_base_rows BIGINT;
    DECLARE @hold_ledger_event_rows BIGINT;
    SELECT @hold_ledger_base_rows = COUNT_BIG(*)
    FROM (
        SELECT TOP (@maximum_hold_ledger_rows_per_execution + 1) hold_definition.hold_id
        FROM ctl.staging_legal_hold AS hold_definition WITH (UPDLOCK, HOLDLOCK)
        WHERE hold_definition.execution_id = @execution_id
    ) AS bounded_hold;
    IF @hold_ledger_base_rows > @maximum_hold_ledger_rows_per_execution
        THROW 51528, N'Histórico de legal hold excede o teto técnico por execução.', 1;
    DECLARE @remaining_hold_ledger_rows BIGINT =
        @maximum_hold_ledger_rows_per_execution - @hold_ledger_base_rows;
    SELECT @hold_ledger_event_rows = COUNT_BIG(*)
    FROM (
        SELECT TOP (@remaining_hold_ledger_rows + 1) hold_event.hold_event_id
        FROM ctl.staging_legal_hold_event AS hold_event WITH (UPDLOCK, HOLDLOCK)
        WHERE hold_event.execution_id = @execution_id
    ) AS bounded_event;
    IF @hold_ledger_event_rows > @remaining_hold_ledger_rows
        THROW 51528, N'Histórico de legal hold excede o teto técnico por execução.', 1;

    IF EXISTS (
        SELECT 1 FROM ctl.ufn_invalid_staging_legal_hold(@execution_id)
        WHERE hold_id = @hold_id
    )
        THROW 51526, N'Ledger do legal hold diverge do estado governado.', 1;

    IF @released_at_utc IS NOT NULL
    BEGIN
        IF @stored_release_authority_evidence_fingerprint <>
                @authority_evidence_fingerprint
            OR @stored_release_owner_role <> @owner_role
            OR NOT EXISTS (
            SELECT 1 FROM ctl.staging_legal_hold_event
            WHERE hold_id = @hold_id AND event_action = N'RELEASED'
              AND execution_id = @execution_id
              AND reason_code = @reason_code
              AND authority_evidence_fingerprint = @authority_evidence_fingerprint
              AND owner_role = @owner_role
              AND hold_scope = @hold_scope
              AND occurred_at_utc = @released_at_utc
        )
            THROW 51517, N'Retry divergente de liberação do legal hold.', 1;
        COMMIT TRANSACTION;
        SELECT @hold_id AS hold_id, @execution_id AS execution_id,
               @released_at_utc AS changed_at_utc, CAST(0 AS BIT) AS active,
               CAST(1 AS BIT) AS exact_retry;
        RETURN;
    END;

    IF @hold_ledger_base_rows + @hold_ledger_event_rows >=
            @maximum_hold_ledger_rows_per_execution
        THROW 51528, N'Liberação excederia o teto técnico por execução.', 1;

    SET @released_at_utc = SYSUTCDATETIME();
    UPDATE ctl.staging_legal_hold
    SET release_authority_evidence_fingerprint = @authority_evidence_fingerprint,
        release_owner_role = @owner_role,
        released_at_utc = @released_at_utc
    WHERE hold_id = @hold_id AND released_at_utc IS NULL;
    INSERT INTO ctl.staging_legal_hold_event (
        hold_id, execution_id, event_action, reason_code,
        authority_evidence_fingerprint, owner_role, hold_scope, occurred_at_utc
    ) VALUES (
        @hold_id, @execution_id, N'RELEASED', @reason_code,
        @authority_evidence_fingerprint, @owner_role, @hold_scope, @released_at_utc
    );

    COMMIT TRANSACTION;
    SELECT @hold_id AS hold_id, @execution_id AS execution_id,
           @released_at_utc AS changed_at_utc, CAST(0 AS BIT) AS active,
           CAST(0 AS BIT) AS exact_retry;
END;
GO

CREATE OR ALTER PROCEDURE stg.usp_plan_staging_lifecycle_at
    @plan_id UNIQUEIDENTIFIER,
    @policy_id UNIQUEIDENTIFIER,
    @policy_version NVARCHAR(MAX),
    @policy_fingerprint NVARCHAR(MAX),
    @environment_name NVARCHAR(MAX),
    @source_instance NVARCHAR(MAX),
    @tenant_scope NVARCHAR(MAX),
    @entity_name NVARCHAR(MAX),
    @scan_after_terminal_at_utc DATETIME2(3),
    @scan_after_execution_id UNIQUEIDENTIFIER,
    @maximum_executions INT,
    @maximum_examined_executions INT,
    @maximum_stage_rows BIGINT,
    @maximum_candidate_rows BIGINT,
    @maximum_evidence_rows BIGINT,
    @maximum_content_rows BIGINT,
    @maximum_probe_rows BIGINT,
    @maximum_archive_bytes BIGINT,
    @database_now_utc DATETIME2(3)
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;

    IF @plan_id IS NULL OR @policy_id IS NULL OR @policy_version IS NULL
        OR @policy_fingerprint IS NULL OR @environment_name IS NULL
        OR @source_instance IS NULL OR @tenant_scope IS NULL OR @entity_name IS NULL
        OR @maximum_executions IS NULL OR @maximum_examined_executions IS NULL
        OR @maximum_stage_rows IS NULL OR @maximum_candidate_rows IS NULL
        OR @maximum_evidence_rows IS NULL OR @maximum_content_rows IS NULL
        OR @maximum_probe_rows IS NULL OR @maximum_archive_bytes IS NULL
        OR @database_now_utc IS NULL
        OR ((@scan_after_terminal_at_utc IS NULL AND @scan_after_execution_id IS NOT NULL)
            OR (@scan_after_terminal_at_utc IS NOT NULL AND @scan_after_execution_id IS NULL))
        OR DATALENGTH(@policy_version) > 256
        OR DATALENGTH(@policy_fingerprint) > 128
        OR DATALENGTH(@environment_name) > 64
        OR DATALENGTH(@source_instance) > 256
        OR DATALENGTH(@tenant_scope) > 256
        OR DATALENGTH(@entity_name) > 256
        THROW 51520, N'Plano de lifecycle excede os limites textuais.', 1;

    SET @policy_version = LTRIM(RTRIM(@policy_version));
    SET @policy_fingerprint = LOWER(LTRIM(RTRIM(@policy_fingerprint)));
    SET @environment_name = LTRIM(RTRIM(@environment_name));
    SET @source_instance = LTRIM(RTRIM(@source_instance));
    SET @tenant_scope = LTRIM(RTRIM(@tenant_scope));
    SET @entity_name = LTRIM(RTRIM(@entity_name));

    IF NULLIF(@policy_version, N'') IS NULL
        OR LEN(@policy_fingerprint) <> 64
        OR @policy_fingerprint COLLATE Latin1_General_100_BIN2 LIKE '%[^0-9a-f]%'
        OR NULLIF(@environment_name, N'') IS NULL
        OR NULLIF(@source_instance, N'') IS NULL
        OR NULLIF(@tenant_scope, N'') IS NULL
        OR NULLIF(@entity_name, N'') IS NULL
        OR @maximum_executions NOT BETWEEN 1 AND 1000
        OR @maximum_examined_executions NOT BETWEEN @maximum_executions AND 5000
        OR @maximum_stage_rows NOT BETWEEN 1 AND 1000000
        OR @maximum_candidate_rows NOT BETWEEN 1 AND 1000000
        OR @maximum_evidence_rows NOT BETWEEN 1 AND 5000000
        OR @maximum_content_rows NOT BETWEEN 1 AND 100000
        OR @maximum_probe_rows NOT BETWEEN 1 AND 10000000
        OR CONVERT(DECIMAL(38, 0), @maximum_examined_executions)
            * CONVERT(DECIMAL(38, 0), @maximum_stage_rows + @maximum_candidate_rows
                + @maximum_evidence_rows + 6) + 1 > @maximum_probe_rows
        OR @maximum_archive_bytes NOT BETWEEN 1 AND 1073741824
        THROW 51521, N'Plano de lifecycle possui limite obrigatório inválido.', 1;

    IF EXISTS (SELECT 1 FROM ctl.staging_lifecycle_plan WITH (UPDLOCK, HOLDLOCK)
               WHERE plan_id = @plan_id)
    BEGIN
        IF NOT EXISTS (
            SELECT 1 FROM ctl.staging_lifecycle_plan
            WHERE plan_id = @plan_id
              AND policy_id = @policy_id
              AND policy_version = @policy_version
              AND policy_fingerprint = @policy_fingerprint
              AND environment_name = @environment_name
              AND source_instance = @source_instance
              AND tenant_scope = @tenant_scope
              AND entity_name = @entity_name
              AND ((scan_after_terminal_at_utc IS NULL
                    AND @scan_after_terminal_at_utc IS NULL)
                   OR (scan_after_terminal_at_utc = @scan_after_terminal_at_utc
                       AND scan_after_execution_id = @scan_after_execution_id))
              AND maximum_executions = @maximum_executions
              AND maximum_examined_executions = @maximum_examined_executions
              AND maximum_stage_rows = @maximum_stage_rows
              AND maximum_candidate_rows = @maximum_candidate_rows
              AND maximum_evidence_rows = @maximum_evidence_rows
              AND maximum_content_rows = @maximum_content_rows
              AND maximum_probe_rows = @maximum_probe_rows
              AND maximum_archive_bytes = @maximum_archive_bytes
        )
            THROW 51523, N'Retry divergente do plano de lifecycle.', 1;

        SELECT plan_id, policy_id, policy_version, policy_fingerprint,
               planned_at_utc, cutoff_at_utc,
               scan_after_terminal_at_utc, scan_after_execution_id,
               next_scan_after_terminal_at_utc, next_scan_after_execution_id,
               examined_executions, scan_truncated,
               eligible_executions, held_executions, lease_blocked_executions,
               oversized_executions, deferred_executions, selected_executions,
               selected_stage_rows AS stage_rows,
               selected_candidate_rows AS candidate_rows,
               selected_evidence_rows AS evidence_rows,
               selected_archive_bytes AS archive_bytes,
               planned_content_root_version,
               LOWER(CONVERT(VARCHAR(64), planned_content_root, 2)) AS planned_content_root,
               CAST(1 AS BIT) AS exact_retry
        FROM ctl.staging_lifecycle_plan WHERE plan_id = @plan_id;
        RETURN;
    END;

    DECLARE @terminal_outcome_class NVARCHAR(32);
    DECLARE @retention_days INT;
    SELECT @terminal_outcome_class = terminal_outcome_class,
           @retention_days = retention_days
    FROM ctl.staging_retention_policy AS policy WITH (UPDLOCK, HOLDLOCK)
    WHERE policy.policy_id = @policy_id
      AND policy_version = @policy_version
      AND policy_fingerprint = @policy_fingerprint
      AND environment_name = @environment_name
      AND source_instance = @source_instance
      AND tenant_scope = @tenant_scope
      AND entity_name = @entity_name
      AND revoked_at_utc IS NULL
      AND EXISTS (
          SELECT 1 FROM ctl.staging_retention_policy_event AS owner_event
          WHERE owner_event.policy_id = policy.policy_id
            AND owner_event.event_action = N'DATA_OWNER_APPROVED'
            AND owner_event.authority_evidence_fingerprint =
                policy.data_owner_evidence_fingerprint
            AND owner_event.owner_role = policy.data_owner_role
            AND owner_event.occurred_at_utc = policy.approved_at_utc
      )
      AND EXISTS (
          SELECT 1 FROM ctl.staging_retention_policy_event AS compliance_event
          WHERE compliance_event.policy_id = policy.policy_id
            AND compliance_event.event_action = N'COMPLIANCE_APPROVED'
            AND compliance_event.authority_evidence_fingerprint =
                policy.compliance_evidence_fingerprint
            AND compliance_event.owner_role = policy.compliance_owner_role
            AND compliance_event.occurred_at_utc = policy.approved_at_utc
      )
      AND NOT EXISTS (
          SELECT 1 FROM ctl.staging_retention_policy_event AS revoked_event
          WHERE revoked_event.policy_id = policy.policy_id
            AND revoked_event.event_action = N'REVOKED'
      );

    IF @terminal_outcome_class IS NULL
        THROW 51524, N'Política de retenção ativa e duplamente ratificada não encontrada.', 1;

    DECLARE @planned_at_utc DATETIME2(3) = @database_now_utc;
    DECLARE @cutoff_at_utc DATETIME2(3) = DATEADD(DAY, -@retention_days, @planned_at_utc);

    DECLARE @examined_attempts TABLE (
        scan_rank INT NOT NULL PRIMARY KEY,
        execution_id UNIQUEIDENTIFIER NOT NULL UNIQUE,
        terminal_state NVARCHAR(32) COLLATE Latin1_General_100_BIN2 NOT NULL,
        terminal_at_utc DATETIME2(3) NOT NULL
    );

    INSERT INTO @examined_attempts (scan_rank, execution_id, terminal_state, terminal_at_utc)
    SELECT CONVERT(INT, ROW_NUMBER() OVER (
               ORDER BY bounded.terminal_at_utc, bounded.execution_id
           )),
           bounded.execution_id, bounded.current_state, bounded.terminal_at_utc
    FROM (
        SELECT TOP (@maximum_examined_executions + 1)
               attempt.execution_id, attempt.current_state, attempt.terminal_at_utc
        FROM ctl.execution_attempt AS attempt WITH (UPDLOCK, HOLDLOCK,
            INDEX(IX_ctl_execution_attempt_lifecycle))
        INNER JOIN ctl.execution_partition AS partition_definition
            ON partition_definition.partition_id = attempt.partition_id
        WHERE partition_definition.environment_name = @environment_name
          AND partition_definition.source_instance = @source_instance
          AND partition_definition.tenant_scope = @tenant_scope
          AND partition_definition.entity_name = @entity_name
          AND attempt.terminal_at_utc <= @cutoff_at_utc
          AND (@scan_after_terminal_at_utc IS NULL
               OR attempt.terminal_at_utc > @scan_after_terminal_at_utc
               OR (attempt.terminal_at_utc = @scan_after_terminal_at_utc
                   AND attempt.execution_id > @scan_after_execution_id))
          AND (
              (@terminal_outcome_class = N'PUBLISHED'
                  AND attempt.current_state IN (N'PUBLISHED', N'SKIPPED', N'NOT_APPLICABLE'))
              OR (@terminal_outcome_class = N'NON_PUBLISHED_TERMINAL'
                  AND attempt.current_state IN (
                      N'BLOCKED', N'FAILED', N'CANCELLED', N'DEGRADED',
                      N'SKIPPED', N'NOT_APPLICABLE'
                  ))
          )
          AND (
              EXISTS (SELECT 1 FROM stg.execution_record AS stage_record
                      WHERE stage_record.execution_id = attempt.execution_id)
              OR EXISTS (SELECT 1 FROM stg.execution_candidate AS stage_candidate
                         WHERE stage_candidate.execution_id = attempt.execution_id)
          )
          AND NOT EXISTS (SELECT 1 FROM recon.staging_record_archive AS archived_record
                          WHERE archived_record.execution_id = attempt.execution_id)
        ORDER BY attempt.terminal_at_utc, attempt.execution_id
    ) AS bounded;

    DECLARE @scan_truncated BIT = CASE
        WHEN (SELECT COUNT_BIG(*) FROM @examined_attempts) > @maximum_examined_executions
            THEN 1 ELSE 0 END;
    DECLARE @next_scan_after_terminal_at_utc DATETIME2(3);
    DECLARE @next_scan_after_execution_id UNIQUEIDENTIFIER;
    IF @scan_truncated = 1
        SELECT @next_scan_after_terminal_at_utc = terminal_at_utc,
               @next_scan_after_execution_id = execution_id
        FROM @examined_attempts
        WHERE scan_rank = @maximum_examined_executions;
    DELETE FROM @examined_attempts WHERE scan_rank > @maximum_examined_executions;
    DECLARE @examined_executions BIGINT = (SELECT COUNT_BIG(*) FROM @examined_attempts);

    DECLARE @hold_ledger_base_probe_rows BIGINT;
    SELECT @hold_ledger_base_probe_rows = COUNT_BIG(*)
    FROM (
        SELECT TOP (@maximum_probe_rows + 1) hold_definition.hold_id
        FROM ctl.staging_legal_hold AS hold_definition WITH (UPDLOCK, HOLDLOCK)
        INNER JOIN @examined_attempts AS examined
            ON examined.execution_id = hold_definition.execution_id
    ) AS bounded_hold;
    IF @hold_ledger_base_probe_rows > @maximum_probe_rows
        THROW 51528, N'Histórico de legal hold excede o orçamento de probes do plano.', 1;
    DECLARE @remaining_hold_ledger_probe_rows BIGINT =
        @maximum_probe_rows - @hold_ledger_base_probe_rows;
    DECLARE @hold_ledger_event_probe_rows BIGINT;
    SELECT @hold_ledger_event_probe_rows = COUNT_BIG(*)
    FROM (
        SELECT TOP (@remaining_hold_ledger_probe_rows + 1) hold_event.hold_event_id
        FROM ctl.staging_legal_hold_event AS hold_event WITH (UPDLOCK, HOLDLOCK)
        INNER JOIN @examined_attempts AS examined
            ON examined.execution_id = hold_event.execution_id
    ) AS bounded_event;
    IF @hold_ledger_event_probe_rows > @remaining_hold_ledger_probe_rows
        THROW 51528, N'Histórico de legal hold excede o orçamento de probes do plano.', 1;

    IF EXISTS (
        SELECT 1
        FROM @examined_attempts AS examined
        CROSS APPLY ctl.ufn_invalid_staging_legal_hold(examined.execution_id) AS invalid_hold
    )
        THROW 51526, N'Ledger de legal hold inválido na janela examinada.', 1;

    IF EXISTS (
        SELECT 1
        FROM @examined_attempts AS examined
        CROSS APPLY ctl.ufn_invalid_execution_state_ledger(examined.execution_id)
            AS invalid_ledger
    )
        THROW 51527, N'Trilha de estado da execução terminal é inválida.', 1;

    IF EXISTS (SELECT 1 FROM @examined_attempts
               WHERE terminal_state IN (N'SKIPPED', N'NOT_APPLICABLE'))
        THROW 51525, N'Execução sem carga possui staging inesperado na janela examinada.', 1;

    DECLARE @candidate_metrics TABLE (
        execution_id UNIQUEIDENTIFIER NOT NULL PRIMARY KEY,
        terminal_state NVARCHAR(32) COLLATE Latin1_General_100_BIN2 NOT NULL,
        terminal_at_utc DATETIME2(3) NOT NULL,
        hold_blocked BIT NOT NULL,
        lease_blocked BIT NOT NULL,
        stage_rows BIGINT NOT NULL,
        candidate_rows BIGINT NOT NULL,
        evidence_rows BIGINT NOT NULL,
        archive_bytes BIGINT NOT NULL,
        oversized BIT NOT NULL
    );

    INSERT INTO @candidate_metrics (
        execution_id, terminal_state, terminal_at_utc, hold_blocked, lease_blocked,
        stage_rows, candidate_rows, evidence_rows, archive_bytes, oversized
    )
    SELECT examined.execution_id, examined.terminal_state, examined.terminal_at_utc,
           CONVERT(BIT, CASE WHEN hold_definition.hold_id IS NULL THEN 0 ELSE 1 END),
           CONVERT(BIT, CASE WHEN active_lease.lease_id IS NULL THEN 0 ELSE 1 END),
           stage_summary.stage_rows, candidate_summary.candidate_rows,
           evidence_summary.evidence_rows,
           stage_summary.archive_bytes + candidate_summary.archive_bytes
               + evidence_summary.archive_bytes
               + COALESCE(extension_budget.additional_archive_bytes, CONVERT(BIGINT, 0)),
           CONVERT(BIT, CASE
               WHEN stage_summary.stage_rows > @maximum_stage_rows
                   OR candidate_summary.candidate_rows > @maximum_candidate_rows
                   OR evidence_summary.evidence_rows > @maximum_evidence_rows
                   OR extension_budget.extension_rows > @maximum_stage_rows
                   OR CONVERT(DECIMAL(38, 0), stage_summary.stage_rows)
                       + CONVERT(DECIMAL(38, 0), candidate_summary.candidate_rows)
                       + CONVERT(DECIMAL(38, 0), evidence_summary.evidence_rows)
                       > @maximum_content_rows
                   OR stage_summary.archive_bytes + candidate_summary.archive_bytes
                       + evidence_summary.archive_bytes
                       + COALESCE(
                           extension_budget.additional_archive_bytes,
                           CONVERT(BIGINT, 0)
                       ) > @maximum_archive_bytes
               THEN 1 ELSE 0 END)
    FROM @examined_attempts AS examined
    CROSS APPLY recon.ufn_staging_lifecycle_extension_archive_budget(
        examined.execution_id, @maximum_stage_rows
    ) AS extension_budget
    OUTER APPLY (
        SELECT TOP (1) hold_definition.hold_id
        FROM ctl.staging_legal_hold AS hold_definition WITH (UPDLOCK, HOLDLOCK)
        WHERE hold_definition.execution_id = examined.execution_id
          AND hold_definition.released_at_utc IS NULL
    ) AS hold_definition
    OUTER APPLY (
        SELECT TOP (1) lease.lease_id
        FROM ctl.execution_lease AS lease WITH (UPDLOCK, HOLDLOCK)
        WHERE lease.execution_id = examined.execution_id
          AND lease.released_at_utc IS NULL
    ) AS active_lease
    CROSS APPLY (
        SELECT COUNT_BIG(*) AS stage_rows,
               COALESCE(SUM(CONVERT(BIGINT, 86 + DATALENGTH(bounded.canonical_row))), 0)
                   AS archive_bytes
        FROM (
            SELECT TOP (@maximum_stage_rows + 1)
                   (SELECT stage_record.stage_record_id, stage_record.execution_id,
                           stage_record.input_batch_number, stage_record.input_record_ordinal,
                           stage_record.source_key, stage_record.row_fingerprint_version,
                           stage_record.source_row_hash,
                           stage_record.presence_fingerprint_version,
                           stage_record.presence_fingerprint,
                           stage_record.source_freshness_at_utc,
                           stage_record.validation_disposition,
                           stage_record.quarantine_reason_code, stage_record.staged_at_utc
                    FOR JSON PATH, WITHOUT_ARRAY_WRAPPER, INCLUDE_NULL_VALUES) AS canonical_row
            FROM stg.execution_record AS stage_record WITH (UPDLOCK, HOLDLOCK)
            WHERE stage_record.execution_id = examined.execution_id
        ) AS bounded
    ) AS stage_summary
    CROSS APPLY (
        SELECT COUNT_BIG(*) AS candidate_rows,
               COALESCE(SUM(CONVERT(BIGINT, 86 + DATALENGTH(bounded.canonical_row))), 0)
                   AS archive_bytes
        FROM (
            SELECT TOP (@maximum_candidate_rows + 1)
                   (SELECT stage_candidate.execution_id, stage_candidate.source_key,
                           stage_candidate.winner_stage_record_id,
                           stage_candidate.row_fingerprint_version,
                           stage_candidate.source_row_hash,
                           stage_candidate.presence_fingerprint_version,
                           stage_candidate.presence_fingerprint,
                           stage_candidate.source_freshness_at_utc,
                           stage_candidate.prepared_at_utc
                    FOR JSON PATH, WITHOUT_ARRAY_WRAPPER, INCLUDE_NULL_VALUES) AS canonical_row
            FROM stg.execution_candidate AS stage_candidate WITH (UPDLOCK, HOLDLOCK)
            WHERE stage_candidate.execution_id = examined.execution_id
        ) AS bounded
    ) AS candidate_summary
    CROSS APPLY (
        SELECT COUNT_BIG(*) AS evidence_rows,
               COALESCE(SUM(CONVERT(BIGINT, 86 + DATALENGTH(bounded.canonical_row))), 0)
                   AS archive_bytes
        FROM (
            SELECT TOP (@maximum_evidence_rows + 1) evidence.canonical_row
            FROM (
                SELECT N'01|' + RIGHT(REPLICATE(N'0', 20)
                           + CONVERT(NVARCHAR(20), quarantine_record.quarantine_record_id), 20)
                           AS sort_key,
                       (SELECT quarantine_record.quarantine_record_id,
                               quarantine_record.execution_id, quarantine_record.stage_record_id,
                               quarantine_record.reason_code, quarantine_record.source_key,
                               quarantine_record.row_fingerprint_version,
                               quarantine_record.source_row_hash,
                               quarantine_record.presence_fingerprint_version,
                               quarantine_record.presence_fingerprint,
                               quarantine_record.quarantined_at_utc
                        FOR JSON PATH, WITHOUT_ARRAY_WRAPPER, INCLUDE_NULL_VALUES) AS canonical_row
                FROM recon.quarantine_record AS quarantine_record WITH (UPDLOCK, HOLDLOCK)
                WHERE quarantine_record.execution_id = examined.execution_id
                UNION ALL
                SELECT N'02|' + application_record.source_key,
                       (SELECT application_record.execution_id, application_record.source_key,
                               application_record.record_state_id,
                               application_record.application_disposition,
                               application_record.result_row_fingerprint_version,
                               application_record.result_source_row_hash,
                               application_record.result_presence_fingerprint_version,
                               application_record.result_presence_fingerprint,
                               application_record.result_source_freshness_at_utc,
                               application_record.applied_at_utc
                        FOR JSON PATH, WITHOUT_ARRAY_WRAPPER, INCLUDE_NULL_VALUES)
                FROM recon.execution_candidate_application AS application_record
                    WITH (UPDLOCK, HOLDLOCK)
                WHERE application_record.execution_id = examined.execution_id
                UNION ALL
                SELECT N'03',
                       (SELECT reconciliation_record.execution_id,
                               reconciliation_record.candidate_rows,
                               reconciliation_record.inserted_rows,
                               reconciliation_record.updated_rows,
                               reconciliation_record.reactivated_rows,
                               reconciliation_record.noop_rows,
                               reconciliation_record.stale_noop_rows,
                               reconciliation_record.reconciled_at_utc,
                               reconciliation_record.published_at_utc
                        FOR JSON PATH, WITHOUT_ARRAY_WRAPPER, INCLUDE_NULL_VALUES)
                FROM recon.execution_reconciliation_result AS reconciliation_record
                    WITH (UPDLOCK, HOLDLOCK)
                WHERE reconciliation_record.execution_id = examined.execution_id
                UNION ALL
                SELECT N'04|' + RIGHT(REPLICATE(N'0', 20)
                           + CONVERT(NVARCHAR(20), state_event.state_event_id), 20),
                       (SELECT state_event.state_event_id, state_event.execution_id,
                               state_event.transition_sequence, state_event.previous_state,
                               state_event.next_state, state_event.reason_code,
                               state_event.transitioned_at_utc
                        FOR JSON PATH, WITHOUT_ARRAY_WRAPPER, INCLUDE_NULL_VALUES)
                FROM ctl.execution_state_event AS state_event WITH (UPDLOCK, HOLDLOCK)
                WHERE state_event.execution_id = examined.execution_id
                UNION ALL
                SELECT N'05|' + RIGHT(REPLICATE(N'0', 20)
                           + CONVERT(NVARCHAR(20), page_audit.page_audit_id), 20),
                       (SELECT page_audit.page_audit_id, page_audit.execution_id,
                               page_audit.page_number, page_audit.page_attempt,
                               page_audit.requested_page_size, page_audit.physical_rows,
                               page_audit.distinct_root_keys, page_audit.response_bytes,
                               page_audit.terminal_empty_page,
                               page_audit.terminal_evidence_kind, page_audit.read_at_utc
                        FOR JSON PATH, WITHOUT_ARRAY_WRAPPER, INCLUDE_NULL_VALUES)
                FROM ctl.execution_page_audit AS page_audit WITH (UPDLOCK, HOLDLOCK)
                WHERE page_audit.execution_id = examined.execution_id
                UNION ALL
                SELECT N'06|' + RIGHT(REPLICATE(N'0', 20)
                           + CONVERT(NVARCHAR(20), execution_count.count_id), 20),
                       (SELECT execution_count.count_id, execution_count.execution_id,
                               execution_count.count_phase, execution_count.physical_rows,
                               execution_count.distinct_root_keys, execution_count.duplicate_rows,
                               execution_count.valid_rows,
                               execution_count.quarantined_root_keys,
                               execution_count.unidentified_quarantine_rows,
                               execution_count.recorded_at_utc
                        FOR JSON PATH, WITHOUT_ARRAY_WRAPPER, INCLUDE_NULL_VALUES)
                FROM ctl.execution_count AS execution_count WITH (UPDLOCK, HOLDLOCK)
                WHERE execution_count.execution_id = examined.execution_id
                UNION ALL
                SELECT N'07',
                       (SELECT publication_event.execution_id,
                               publication_event.partition_id,
                               publication_event.previous_published_execution_id,
                               publication_event.published_at_utc,
                               publication_event.incremental_frontier_before_utc,
                               publication_event.incremental_frontier_after_utc,
                               publication_event.watermark_last_partition_id
                        FOR JSON PATH, WITHOUT_ARRAY_WRAPPER, INCLUDE_NULL_VALUES)
                FROM ctl.execution_publication_event AS publication_event
                    WITH (UPDLOCK, HOLDLOCK)
                WHERE publication_event.execution_id = examined.execution_id
                UNION ALL
                SELECT N'08',
                       (SELECT promotion_result.execution_id, promotion_result.physical_rows,
                               promotion_result.distinct_root_keys,
                               promotion_result.candidate_rows, promotion_result.duplicate_rows,
                               promotion_result.quarantined_root_keys,
                               promotion_result.unidentified_quarantine_rows,
                               promotion_result.quarantined_stage_rows,
                               promotion_result.promoted_at_utc
                        FOR JSON PATH, WITHOUT_ARRAY_WRAPPER, INCLUDE_NULL_VALUES)
                FROM ctl.execution_promotion_result AS promotion_result
                    WITH (UPDLOCK, HOLDLOCK)
                WHERE promotion_result.execution_id = examined.execution_id
            ) AS evidence
        ) AS bounded
    ) AS evidence_summary;

    DECLARE @eligible_executions BIGINT;
    DECLARE @held_executions BIGINT;
    DECLARE @lease_blocked_executions BIGINT;
    DECLARE @oversized_executions BIGINT;
    SELECT @eligible_executions = COALESCE(SUM(CASE
               WHEN hold_blocked = 0 AND lease_blocked = 0 AND oversized = 0 THEN 1 ELSE 0 END), 0),
           @held_executions = COALESCE(SUM(CONVERT(BIGINT, hold_blocked)), 0),
           @lease_blocked_executions = COALESCE(SUM(CONVERT(BIGINT, lease_blocked)), 0),
           @oversized_executions = COALESCE(SUM(CONVERT(BIGINT, oversized)), 0)
    FROM @candidate_metrics;

    DECLARE @bounded_candidates TABLE (
        execution_id UNIQUEIDENTIFIER NOT NULL PRIMARY KEY,
        terminal_state NVARCHAR(32) COLLATE Latin1_General_100_BIN2 NOT NULL,
        terminal_at_utc DATETIME2(3) NOT NULL,
        stage_rows BIGINT NOT NULL,
        candidate_rows BIGINT NOT NULL,
        evidence_rows BIGINT NOT NULL,
        archive_bytes BIGINT NOT NULL
    );

    INSERT INTO @bounded_candidates (
        execution_id, terminal_state, terminal_at_utc,
        stage_rows, candidate_rows, evidence_rows, archive_bytes
    )
    SELECT TOP (@maximum_executions)
        metric.execution_id, metric.terminal_state, metric.terminal_at_utc,
        metric.stage_rows, metric.candidate_rows, metric.evidence_rows, metric.archive_bytes
    FROM @candidate_metrics AS metric
    WHERE metric.hold_blocked = 0 AND metric.lease_blocked = 0 AND metric.oversized = 0
      AND metric.stage_rows > 0
    ORDER BY metric.terminal_at_utc, metric.execution_id;

    INSERT INTO ctl.staging_lifecycle_plan (
        plan_id, policy_id, policy_version, policy_fingerprint,
        environment_name, source_instance, tenant_scope, entity_name,
        terminal_outcome_class, retention_days, cutoff_at_utc, planned_at_utc,
        scan_after_terminal_at_utc, scan_after_execution_id,
        next_scan_after_terminal_at_utc, next_scan_after_execution_id,
        maximum_executions, maximum_examined_executions,
        maximum_stage_rows, maximum_candidate_rows, maximum_evidence_rows,
        maximum_content_rows, maximum_probe_rows, maximum_archive_bytes,
        examined_executions, scan_truncated,
        eligible_executions, held_executions, lease_blocked_executions,
        oversized_executions, deferred_executions, selected_executions,
        selected_stage_rows, selected_candidate_rows, selected_evidence_rows,
        selected_archive_bytes, planned_content_root_version, planned_content_root
    ) VALUES (
        @plan_id, @policy_id, @policy_version, @policy_fingerprint,
        @environment_name, @source_instance, @tenant_scope, @entity_name,
        @terminal_outcome_class, @retention_days, @cutoff_at_utc, @planned_at_utc,
        @scan_after_terminal_at_utc, @scan_after_execution_id,
        @next_scan_after_terminal_at_utc, @next_scan_after_execution_id,
        @maximum_executions, @maximum_examined_executions,
        @maximum_stage_rows, @maximum_candidate_rows, @maximum_evidence_rows,
        @maximum_content_rows, @maximum_probe_rows, @maximum_archive_bytes,
        @examined_executions, @scan_truncated,
        @eligible_executions, @held_executions, @lease_blocked_executions,
        @oversized_executions, 0, 0, 0, 0, 0, 0,
        N'archive-content-set-v1', HASHBYTES('SHA2_256', 0x)
    );

    ;WITH ordered_candidates AS (
        SELECT candidate.*,
               SUM(stage_rows) OVER (
                   ORDER BY terminal_at_utc, execution_id ROWS UNBOUNDED PRECEDING
               ) AS cumulative_stage_rows,
               SUM(candidate_rows) OVER (
                    ORDER BY terminal_at_utc, execution_id ROWS UNBOUNDED PRECEDING
                ) AS cumulative_candidate_rows,
               SUM(evidence_rows) OVER (
                    ORDER BY terminal_at_utc, execution_id ROWS UNBOUNDED PRECEDING
                ) AS cumulative_evidence_rows,
               SUM(stage_rows + candidate_rows + evidence_rows) OVER (
                    ORDER BY terminal_at_utc, execution_id ROWS UNBOUNDED PRECEDING
                ) AS cumulative_content_rows,
               SUM(archive_bytes) OVER (
                   ORDER BY terminal_at_utc, execution_id ROWS UNBOUNDED PRECEDING
               ) AS cumulative_archive_bytes
        FROM @bounded_candidates AS candidate
    )
    INSERT INTO ctl.staging_lifecycle_plan_item (
        plan_id, execution_id, terminal_state, terminal_at_utc,
        stage_rows, candidate_rows, evidence_rows, archive_bytes
    )
    SELECT @plan_id, execution_id, terminal_state, terminal_at_utc,
           stage_rows, candidate_rows, evidence_rows, archive_bytes
    FROM ordered_candidates
    WHERE cumulative_stage_rows <= @maximum_stage_rows
      AND cumulative_candidate_rows <= @maximum_candidate_rows
      AND cumulative_evidence_rows <= @maximum_evidence_rows
      AND cumulative_content_rows <= @maximum_content_rows
      AND cumulative_archive_bytes <= @maximum_archive_bytes;

    UPDATE ctl.staging_lifecycle_plan
    SET selected_executions = summary.selected_executions,
        deferred_executions = CASE
            WHEN eligible_executions > summary.selected_executions
                THEN eligible_executions - summary.selected_executions
            ELSE 0
        END,
        selected_stage_rows = summary.stage_rows,
        selected_candidate_rows = summary.candidate_rows,
        selected_evidence_rows = summary.evidence_rows,
        selected_archive_bytes = summary.archive_bytes
    FROM ctl.staging_lifecycle_plan AS lifecycle_plan
    CROSS APPLY (
        SELECT COUNT_BIG(*) AS selected_executions,
               COALESCE(SUM(item.stage_rows), 0) AS stage_rows,
               COALESCE(SUM(item.candidate_rows), 0) AS candidate_rows,
               COALESCE(SUM(item.evidence_rows), 0) AS evidence_rows,
               COALESCE(SUM(item.archive_bytes), 0) AS archive_bytes
        FROM ctl.staging_lifecycle_plan_item AS item
        WHERE item.plan_id = @plan_id
    ) AS summary
    WHERE lifecycle_plan.plan_id = @plan_id;

    DECLARE @planned_content_rows BIGINT;
    DECLARE @planned_content_root BINARY(32);
    SELECT @planned_content_rows = selected_stage_rows + selected_candidate_rows
        + selected_evidence_rows
    FROM ctl.staging_lifecycle_plan
    WHERE plan_id = @plan_id;

    EXEC recon.usp_compute_live_staging_content_root_internal
        @plan_id = @plan_id,
        @expected_content_rows = @planned_content_rows,
        @content_root = @planned_content_root OUTPUT;

    UPDATE ctl.staging_lifecycle_plan
    SET planned_content_root = @planned_content_root
    WHERE plan_id = @plan_id;

    SELECT plan_id, policy_id, policy_version, policy_fingerprint,
           planned_at_utc, cutoff_at_utc,
           scan_after_terminal_at_utc, scan_after_execution_id,
           next_scan_after_terminal_at_utc, next_scan_after_execution_id,
           examined_executions, scan_truncated,
           eligible_executions, held_executions, lease_blocked_executions,
           oversized_executions, deferred_executions, selected_executions,
           selected_stage_rows AS stage_rows,
           selected_candidate_rows AS candidate_rows,
           selected_evidence_rows AS evidence_rows,
           selected_archive_bytes AS archive_bytes,
           planned_content_root_version,
           LOWER(CONVERT(VARCHAR(64), planned_content_root, 2)) AS planned_content_root,
           CAST(0 AS BIT) AS exact_retry
    FROM ctl.staging_lifecycle_plan WHERE plan_id = @plan_id;
END;
GO

CREATE OR ALTER PROCEDURE stg.usp_plan_staging_lifecycle
    @plan_id UNIQUEIDENTIFIER,
    @policy_id UNIQUEIDENTIFIER,
    @policy_version NVARCHAR(MAX),
    @policy_fingerprint NVARCHAR(MAX),
    @environment_name NVARCHAR(MAX),
    @source_instance NVARCHAR(MAX),
    @tenant_scope NVARCHAR(MAX),
    @entity_name NVARCHAR(MAX),
    @scan_after_terminal_at_utc DATETIME2(3),
    @scan_after_execution_id UNIQUEIDENTIFIER,
    @maximum_executions INT,
    @maximum_examined_executions INT,
    @maximum_stage_rows BIGINT,
    @maximum_candidate_rows BIGINT,
    @maximum_evidence_rows BIGINT,
    @maximum_content_rows BIGINT,
    @maximum_probe_rows BIGINT,
    @maximum_archive_bytes BIGINT
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    BEGIN TRANSACTION;

    DECLARE @lock_result INT;
    EXEC @lock_result = sys.sp_getapplock
        @Resource = N'V2_STAGING_LIFECYCLE',
        @LockMode = N'Exclusive',
        @LockOwner = N'Transaction',
        @LockTimeout = 5000,
        @DbPrincipal = N'public';
    IF @lock_result < 0
        THROW 51522, N'Não foi possível obter o lock do lifecycle de staging.', 1;

    DECLARE @database_now_utc DATETIME2(3) = SYSUTCDATETIME();
    EXEC stg.usp_plan_staging_lifecycle_at
        @plan_id, @policy_id, @policy_version, @policy_fingerprint,
        @environment_name, @source_instance, @tenant_scope, @entity_name,
        @scan_after_terminal_at_utc, @scan_after_execution_id,
        @maximum_executions, @maximum_examined_executions,
        @maximum_stage_rows, @maximum_candidate_rows, @maximum_evidence_rows,
        @maximum_content_rows, @maximum_probe_rows,
        @maximum_archive_bytes, @database_now_utc;

    COMMIT TRANSACTION;
END;
GO

CREATE OR ALTER PROCEDURE recon.usp_verify_staging_archive_internal
    @plan_id UNIQUEIDENTIFIER
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;

    DECLARE @stage_rows BIGINT;
    DECLARE @candidate_rows BIGINT;
    DECLARE @quarantine_rows BIGINT;
    DECLARE @application_rows BIGINT;
    DECLARE @reconciliation_rows BIGINT;
    DECLARE @state_event_rows BIGINT;
    DECLARE @page_audit_rows BIGINT;
    DECLARE @count_rows BIGINT;
    DECLARE @publication_event_rows BIGINT;
    DECLARE @promotion_result_rows BIGINT;
    DECLARE @content_root_version NVARCHAR(64);
    DECLARE @content_root BINARY(32);
    DECLARE @planned_content_root_version NVARCHAR(64);
    DECLARE @planned_content_root BINARY(32);
    DECLARE @archive_fingerprint_version NVARCHAR(128);
    DECLARE @archive_fingerprint CHAR(64);
    DECLARE @archived_executions BIGINT;
    DECLARE @archived_bytes BIGINT;
    DECLARE @archived_at_utc DATETIME2(3);
    DECLARE @policy_fingerprint CHAR(64);
    DECLARE @selected_executions BIGINT;
    DECLARE @selected_stage_rows BIGINT;
    DECLARE @selected_candidate_rows BIGINT;
    DECLARE @selected_evidence_rows BIGINT;
    DECLARE @selected_archive_bytes BIGINT;
    DECLARE @maximum_stage_rows BIGINT;
    DECLARE @maximum_candidate_rows BIGINT;
    DECLARE @maximum_evidence_rows BIGINT;
    DECLARE @maximum_content_rows BIGINT;

    SELECT @stage_rows = archived_stage_rows,
           @candidate_rows = archived_candidate_rows,
           @quarantine_rows = archived_quarantine_rows,
           @application_rows = archived_application_rows,
           @reconciliation_rows = archived_reconciliation_rows,
           @state_event_rows = archived_state_event_rows,
           @page_audit_rows = archived_page_audit_rows,
           @count_rows = archived_count_rows,
           @publication_event_rows = archived_publication_event_rows,
           @promotion_result_rows = archived_promotion_result_rows,
           @content_root_version = content_root_version,
           @content_root = content_root,
           @archive_fingerprint_version = archive_fingerprint_version,
           @archive_fingerprint = archive_fingerprint,
           @archived_executions = archived_executions,
           @archived_bytes = archived_bytes,
           @archived_at_utc = archived_at_utc,
           @policy_fingerprint = lifecycle_plan.policy_fingerprint,
           @planned_content_root_version = lifecycle_plan.planned_content_root_version,
           @planned_content_root = lifecycle_plan.planned_content_root,
           @selected_executions = lifecycle_plan.selected_executions,
           @selected_stage_rows = lifecycle_plan.selected_stage_rows,
           @selected_candidate_rows = lifecycle_plan.selected_candidate_rows,
           @selected_evidence_rows = lifecycle_plan.selected_evidence_rows,
           @selected_archive_bytes = lifecycle_plan.selected_archive_bytes,
           @maximum_stage_rows = lifecycle_plan.maximum_stage_rows,
           @maximum_candidate_rows = lifecycle_plan.maximum_candidate_rows,
           @maximum_evidence_rows = lifecycle_plan.maximum_evidence_rows,
           @maximum_content_rows = lifecycle_plan.maximum_content_rows
    FROM recon.staging_lifecycle_archive_manifest AS archive_manifest WITH (UPDLOCK, HOLDLOCK)
    INNER JOIN ctl.staging_lifecycle_plan AS lifecycle_plan
        ON lifecycle_plan.plan_id = archive_manifest.plan_id
    WHERE archive_manifest.plan_id = @plan_id;

    IF @stage_rows IS NULL
        THROW 51539, N'Manifesto de archive não encontrado para verificação.', 1;

    DECLARE @manifest_evidence_rows DECIMAL(38, 0) =
        CONVERT(DECIMAL(38, 0), @quarantine_rows)
        + CONVERT(DECIMAL(38, 0), @application_rows)
        + CONVERT(DECIMAL(38, 0), @reconciliation_rows)
        + CONVERT(DECIMAL(38, 0), @state_event_rows)
        + CONVERT(DECIMAL(38, 0), @page_audit_rows)
        + CONVERT(DECIMAL(38, 0), @count_rows)
        + CONVERT(DECIMAL(38, 0), @publication_event_rows)
        + CONVERT(DECIMAL(38, 0), @promotion_result_rows);
    DECLARE @manifest_content_rows DECIMAL(38, 0) =
        CONVERT(DECIMAL(38, 0), @stage_rows)
        + CONVERT(DECIMAL(38, 0), @candidate_rows) + @manifest_evidence_rows;

    IF @archive_fingerprint_version <> N'staging-archive-manifest-v3'
        OR @archived_executions <> @selected_executions
        OR @stage_rows <> @selected_stage_rows OR @stage_rows > @maximum_stage_rows
        OR @candidate_rows <> @selected_candidate_rows
        OR @candidate_rows > @maximum_candidate_rows
        OR @manifest_evidence_rows <> @selected_evidence_rows
        OR @manifest_evidence_rows > @maximum_evidence_rows
        OR @manifest_content_rows > @maximum_content_rows
        OR @archived_bytes <> @selected_archive_bytes
        THROW 51539, N'Manifesto de archive excede ou diverge dos limites imutáveis do plano.', 1;

    IF (SELECT COUNT_BIG(*) FROM (SELECT TOP (@stage_rows + 1) 1 AS marker
            FROM recon.staging_record_archive WHERE plan_id = @plan_id) AS bounded)
            <> @stage_rows
        OR (SELECT COUNT_BIG(*) FROM (SELECT TOP (@candidate_rows + 1) 1 AS marker
            FROM recon.staging_candidate_archive WHERE plan_id = @plan_id) AS bounded)
            <> @candidate_rows
        OR (SELECT COUNT_BIG(*) FROM (SELECT TOP (@quarantine_rows + 1) 1 AS marker
            FROM recon.quarantine_record_archive WHERE plan_id = @plan_id) AS bounded)
            <> @quarantine_rows
        OR (SELECT COUNT_BIG(*) FROM (SELECT TOP (@application_rows + 1) 1 AS marker
            FROM recon.execution_candidate_application_archive
            WHERE plan_id = @plan_id) AS bounded) <> @application_rows
        OR (SELECT COUNT_BIG(*) FROM (SELECT TOP (@reconciliation_rows + 1) 1 AS marker
            FROM recon.execution_reconciliation_result_archive
            WHERE plan_id = @plan_id) AS bounded) <> @reconciliation_rows
        OR (SELECT COUNT_BIG(*) FROM (SELECT TOP (@state_event_rows + 1) 1 AS marker
            FROM recon.execution_state_event_archive
            WHERE plan_id = @plan_id) AS bounded) <> @state_event_rows
        OR (SELECT COUNT_BIG(*) FROM (SELECT TOP (@page_audit_rows + 1) 1 AS marker
            FROM recon.execution_page_audit_archive
            WHERE plan_id = @plan_id) AS bounded) <> @page_audit_rows
        OR (SELECT COUNT_BIG(*) FROM (SELECT TOP (@count_rows + 1) 1 AS marker
            FROM recon.execution_count_archive
            WHERE plan_id = @plan_id) AS bounded) <> @count_rows
        OR (SELECT COUNT_BIG(*) FROM (SELECT TOP (@publication_event_rows + 1) 1 AS marker
            FROM recon.execution_publication_event_archive
            WHERE plan_id = @plan_id) AS bounded) <> @publication_event_rows
        OR (SELECT COUNT_BIG(*) FROM (SELECT TOP (@promotion_result_rows + 1) 1 AS marker
            FROM recon.execution_promotion_result_archive
            WHERE plan_id = @plan_id) AS bounded) <> @promotion_result_rows
        THROW 51539, N'Contagens do archive divergem do manifesto selado.', 1;

    IF EXISTS (
        SELECT 1 FROM recon.staging_record_archive AS archived
        CROSS APPLY (SELECT (SELECT archived.stage_record_id, archived.execution_id,
                                    archived.input_batch_number, archived.input_record_ordinal,
                                    archived.source_key, archived.row_fingerprint_version,
                                    archived.source_row_hash,
                                    archived.presence_fingerprint_version,
                                    archived.presence_fingerprint,
                                    archived.source_freshness_at_utc,
                                    archived.validation_disposition,
                                    archived.quarantine_reason_code, archived.staged_at_utc
                             FOR JSON PATH, WITHOUT_ARRAY_WRAPPER, INCLUDE_NULL_VALUES)
                            AS canonical_row) AS canonical
        WHERE archived.plan_id = @plan_id
          AND (archived.archive_row_attestation_version <> N'archive-row-json-v1'
               OR archived.archive_row_attestation <> recon.ufn_archive_row_attestation(
                   N'stg.execution_record', canonical.canonical_row))
    ) OR EXISTS (
        SELECT 1 FROM recon.staging_candidate_archive AS archived
        CROSS APPLY (SELECT (SELECT archived.execution_id, archived.source_key,
                                    archived.winner_stage_record_id,
                                    archived.row_fingerprint_version,
                                    archived.source_row_hash,
                                    archived.presence_fingerprint_version,
                                    archived.presence_fingerprint,
                                    archived.source_freshness_at_utc, archived.prepared_at_utc
                             FOR JSON PATH, WITHOUT_ARRAY_WRAPPER, INCLUDE_NULL_VALUES)
                            AS canonical_row) AS canonical
        WHERE archived.plan_id = @plan_id
          AND (archived.archive_row_attestation_version <> N'archive-row-json-v1'
               OR archived.archive_row_attestation <> recon.ufn_archive_row_attestation(
                   N'stg.execution_candidate', canonical.canonical_row))
    ) OR EXISTS (
        SELECT 1 FROM recon.quarantine_record_archive AS archived
        CROSS APPLY (SELECT (SELECT archived.quarantine_record_id, archived.execution_id,
                                    archived.stage_record_id, archived.reason_code,
                                    archived.source_key, archived.row_fingerprint_version,
                                    archived.source_row_hash,
                                    archived.presence_fingerprint_version,
                                    archived.presence_fingerprint, archived.quarantined_at_utc
                             FOR JSON PATH, WITHOUT_ARRAY_WRAPPER, INCLUDE_NULL_VALUES)
                            AS canonical_row) AS canonical
        WHERE archived.plan_id = @plan_id
          AND (archived.archive_row_attestation_version <> N'archive-row-json-v1'
               OR archived.archive_row_attestation <> recon.ufn_archive_row_attestation(
                   N'recon.quarantine_record', canonical.canonical_row))
    ) OR EXISTS (
        SELECT 1 FROM recon.execution_candidate_application_archive AS archived
        CROSS APPLY (SELECT (SELECT archived.execution_id, archived.source_key,
                                    archived.record_state_id,
                                    archived.application_disposition,
                                    archived.result_row_fingerprint_version,
                                    archived.result_source_row_hash,
                                    archived.result_presence_fingerprint_version,
                                    archived.result_presence_fingerprint,
                                    archived.result_source_freshness_at_utc,
                                    archived.applied_at_utc
                             FOR JSON PATH, WITHOUT_ARRAY_WRAPPER, INCLUDE_NULL_VALUES)
                            AS canonical_row) AS canonical
        WHERE archived.plan_id = @plan_id
          AND (archived.archive_row_attestation_version <> N'archive-row-json-v1'
               OR archived.archive_row_attestation <> recon.ufn_archive_row_attestation(
                   N'recon.execution_candidate_application', canonical.canonical_row))
    ) OR EXISTS (
        SELECT 1 FROM recon.execution_reconciliation_result_archive AS archived
        CROSS APPLY (SELECT (SELECT archived.execution_id, archived.candidate_rows,
                                    archived.inserted_rows, archived.updated_rows,
                                    archived.reactivated_rows, archived.noop_rows,
                                    archived.stale_noop_rows, archived.reconciled_at_utc,
                                    archived.published_at_utc
                             FOR JSON PATH, WITHOUT_ARRAY_WRAPPER, INCLUDE_NULL_VALUES)
                            AS canonical_row) AS canonical
        WHERE archived.plan_id = @plan_id
          AND (archived.archive_row_attestation_version <> N'archive-row-json-v1'
               OR archived.archive_row_attestation <> recon.ufn_archive_row_attestation(
                   N'recon.execution_reconciliation_result', canonical.canonical_row))
    ) OR EXISTS (
        SELECT 1 FROM recon.execution_state_event_archive AS archived
        CROSS APPLY (SELECT (SELECT archived.state_event_id, archived.execution_id,
                                    archived.transition_sequence, archived.previous_state,
                                    archived.next_state, archived.reason_code,
                                    archived.transitioned_at_utc
                             FOR JSON PATH, WITHOUT_ARRAY_WRAPPER, INCLUDE_NULL_VALUES)
                            AS canonical_row) AS canonical
        WHERE archived.plan_id = @plan_id
          AND (archived.archive_row_attestation_version <> N'archive-row-json-v1'
               OR archived.archive_row_attestation <> recon.ufn_archive_row_attestation(
                   N'ctl.execution_state_event', canonical.canonical_row))
    ) OR EXISTS (
        SELECT 1 FROM recon.execution_page_audit_archive AS archived
        CROSS APPLY (SELECT (SELECT archived.page_audit_id, archived.execution_id,
                                    archived.page_number, archived.page_attempt,
                                    archived.requested_page_size, archived.physical_rows,
                                    archived.distinct_root_keys, archived.response_bytes,
                                    archived.terminal_empty_page,
                                    archived.terminal_evidence_kind, archived.read_at_utc
                             FOR JSON PATH, WITHOUT_ARRAY_WRAPPER, INCLUDE_NULL_VALUES)
                            AS canonical_row) AS canonical
        WHERE archived.plan_id = @plan_id
          AND (archived.archive_row_attestation_version <> N'archive-row-json-v1'
               OR archived.archive_row_attestation <> recon.ufn_archive_row_attestation(
                   N'ctl.execution_page_audit', canonical.canonical_row))
    ) OR EXISTS (
        SELECT 1 FROM recon.execution_count_archive AS archived
        CROSS APPLY (SELECT (SELECT archived.count_id, archived.execution_id,
                                    archived.count_phase, archived.physical_rows,
                                    archived.distinct_root_keys, archived.duplicate_rows,
                                    archived.valid_rows, archived.quarantined_root_keys,
                                    archived.unidentified_quarantine_rows,
                                    archived.recorded_at_utc
                             FOR JSON PATH, WITHOUT_ARRAY_WRAPPER, INCLUDE_NULL_VALUES)
                            AS canonical_row) AS canonical
        WHERE archived.plan_id = @plan_id
          AND (archived.archive_row_attestation_version <> N'archive-row-json-v1'
               OR archived.archive_row_attestation <> recon.ufn_archive_row_attestation(
                   N'ctl.execution_count', canonical.canonical_row))
    ) OR EXISTS (
        SELECT 1 FROM recon.execution_publication_event_archive AS archived
        CROSS APPLY (SELECT (SELECT archived.execution_id, archived.partition_id,
                                    archived.previous_published_execution_id,
                                    archived.published_at_utc,
                                    archived.incremental_frontier_before_utc,
                                    archived.incremental_frontier_after_utc,
                                    archived.watermark_last_partition_id
                             FOR JSON PATH, WITHOUT_ARRAY_WRAPPER, INCLUDE_NULL_VALUES)
                            AS canonical_row) AS canonical
        WHERE archived.plan_id = @plan_id
          AND (archived.archive_row_attestation_version <> N'archive-row-json-v1'
               OR archived.archive_row_attestation <> recon.ufn_archive_row_attestation(
                   N'ctl.execution_publication_event', canonical.canonical_row))
    ) OR EXISTS (
        SELECT 1 FROM recon.execution_promotion_result_archive AS archived
        CROSS APPLY (SELECT (SELECT archived.execution_id, archived.physical_rows,
                                    archived.distinct_root_keys, archived.candidate_rows,
                                    archived.duplicate_rows, archived.quarantined_root_keys,
                                    archived.unidentified_quarantine_rows,
                                    archived.quarantined_stage_rows, archived.promoted_at_utc
                             FOR JSON PATH, WITHOUT_ARRAY_WRAPPER, INCLUDE_NULL_VALUES)
                            AS canonical_row) AS canonical
        WHERE archived.plan_id = @plan_id
          AND (archived.archive_row_attestation_version <> N'archive-row-json-v1'
               OR archived.archive_row_attestation <> recon.ufn_archive_row_attestation(
                   N'ctl.execution_promotion_result', canonical.canonical_row))
    )
        THROW 51539, N'Atestado de conteúdo do archive divergiu.', 1;

    DECLARE @recomputed_content_root BINARY(32);
    DECLARE @expected_content_rows BIGINT = CONVERT(BIGINT, @manifest_content_rows);
    EXEC recon.usp_compute_archive_content_root_internal
        @plan_id = @plan_id,
        @expected_content_rows = @expected_content_rows,
        @content_root = @recomputed_content_root OUTPUT;

    IF @content_root_version <> N'archive-content-set-v1'
        OR @planned_content_root_version <> @content_root_version
        OR @planned_content_root <> @content_root
        OR @content_root <> @recomputed_content_root
        THROW 51539, N'Raiz criptográfica do archive divergiu.', 1;

    DECLARE @recomputed_archive_fingerprint CHAR(64) = LOWER(CONVERT(VARCHAR(64),
        HASHBYTES('SHA2_256', CONVERT(VARBINARY(MAX), CONCAT(
            CONVERT(VARCHAR(36), @plan_id), N'|', @policy_fingerprint, N'|',
            @archived_executions, N'|', @stage_rows, N'|', @candidate_rows, N'|',
            @archived_bytes, N'|', @quarantine_rows, N'|', @application_rows, N'|',
            @reconciliation_rows, N'|', @state_event_rows, N'|',
            @page_audit_rows, N'|', @count_rows, N'|', @publication_event_rows, N'|',
            @promotion_result_rows, N'|', @content_root_version, N'|',
            CONVERT(VARCHAR(64), @content_root, 2), N'|',
            CONVERT(VARCHAR(33), @archived_at_utc, 126)
        ))), 2));
    IF @archive_fingerprint_version <> N'staging-archive-manifest-v3'
        OR @archive_fingerprint <> @recomputed_archive_fingerprint
        THROW 51539, N'Fingerprint do manifesto de archive divergiu.', 1;
END;
GO

CREATE OR ALTER PROCEDURE recon.usp_verify_staging_restore_internal
    @restore_id UNIQUEIDENTIFIER
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;

    DECLARE @plan_id UNIQUEIDENTIFIER;
    DECLARE @execution_id UNIQUEIDENTIFIER;
    DECLARE @stage_rows BIGINT;
    DECLARE @candidate_rows BIGINT;
    SELECT @plan_id = plan_id, @execution_id = execution_id,
           @stage_rows = restored_stage_rows, @candidate_rows = restored_candidate_rows
    FROM recon.staging_restore_session WITH (UPDLOCK, HOLDLOCK)
    WHERE restore_id = @restore_id;

    IF @plan_id IS NULL
        THROW 51557, N'Sessão de restore não encontrada para verificação.', 1;

    IF (SELECT COUNT_BIG(*) FROM (SELECT TOP (@stage_rows + 1) 1 AS marker
            FROM recon.staging_restore_record WHERE restore_id = @restore_id) AS bounded)
            <> @stage_rows
        OR (SELECT COUNT_BIG(*) FROM (SELECT TOP (@candidate_rows + 1) 1 AS marker
            FROM recon.staging_restore_candidate WHERE restore_id = @restore_id) AS bounded)
            <> @candidate_rows
        OR EXISTS (
            SELECT archived.stage_record_id, archived.execution_id,
                   archived.input_batch_number, archived.input_record_ordinal,
                   archived.source_key, archived.row_fingerprint_version,
                   archived.source_row_hash, archived.presence_fingerprint_version,
                   archived.presence_fingerprint, archived.source_freshness_at_utc,
                   archived.validation_disposition, archived.quarantine_reason_code,
                   archived.staged_at_utc
            FROM recon.staging_record_archive AS archived
            WHERE archived.plan_id = @plan_id AND archived.execution_id = @execution_id
            EXCEPT
            SELECT restored.stage_record_id, restored.execution_id,
                   restored.input_batch_number, restored.input_record_ordinal,
                   restored.source_key, restored.row_fingerprint_version,
                   restored.source_row_hash, restored.presence_fingerprint_version,
                   restored.presence_fingerprint, restored.source_freshness_at_utc,
                   restored.validation_disposition, restored.quarantine_reason_code,
                   restored.staged_at_utc
            FROM recon.staging_restore_record AS restored
            WHERE restored.restore_id = @restore_id
        )
        OR EXISTS (
            SELECT restored.stage_record_id, restored.execution_id,
                   restored.input_batch_number, restored.input_record_ordinal,
                   restored.source_key, restored.row_fingerprint_version,
                   restored.source_row_hash, restored.presence_fingerprint_version,
                   restored.presence_fingerprint, restored.source_freshness_at_utc,
                   restored.validation_disposition, restored.quarantine_reason_code,
                   restored.staged_at_utc
            FROM recon.staging_restore_record AS restored
            WHERE restored.restore_id = @restore_id
            EXCEPT
            SELECT archived.stage_record_id, archived.execution_id,
                   archived.input_batch_number, archived.input_record_ordinal,
                   archived.source_key, archived.row_fingerprint_version,
                   archived.source_row_hash, archived.presence_fingerprint_version,
                   archived.presence_fingerprint, archived.source_freshness_at_utc,
                   archived.validation_disposition, archived.quarantine_reason_code,
                   archived.staged_at_utc
            FROM recon.staging_record_archive AS archived
            WHERE archived.plan_id = @plan_id AND archived.execution_id = @execution_id
        )
        OR EXISTS (
            SELECT archived.execution_id, archived.source_key,
                   archived.winner_stage_record_id, archived.row_fingerprint_version,
                   archived.source_row_hash, archived.presence_fingerprint_version,
                   archived.presence_fingerprint, archived.source_freshness_at_utc,
                   archived.prepared_at_utc
            FROM recon.staging_candidate_archive AS archived
            WHERE archived.plan_id = @plan_id AND archived.execution_id = @execution_id
            EXCEPT
            SELECT restored.execution_id, restored.source_key,
                   restored.winner_stage_record_id, restored.row_fingerprint_version,
                   restored.source_row_hash, restored.presence_fingerprint_version,
                   restored.presence_fingerprint, restored.source_freshness_at_utc,
                   restored.prepared_at_utc
            FROM recon.staging_restore_candidate AS restored
            WHERE restored.restore_id = @restore_id
        )
        OR EXISTS (
            SELECT restored.execution_id, restored.source_key,
                   restored.winner_stage_record_id, restored.row_fingerprint_version,
                   restored.source_row_hash, restored.presence_fingerprint_version,
                   restored.presence_fingerprint, restored.source_freshness_at_utc,
                   restored.prepared_at_utc
            FROM recon.staging_restore_candidate AS restored
            WHERE restored.restore_id = @restore_id
            EXCEPT
            SELECT archived.execution_id, archived.source_key,
                   archived.winner_stage_record_id, archived.row_fingerprint_version,
                   archived.source_row_hash, archived.presence_fingerprint_version,
                   archived.presence_fingerprint, archived.source_freshness_at_utc,
                   archived.prepared_at_utc
            FROM recon.staging_candidate_archive AS archived
            WHERE archived.plan_id = @plan_id AND archived.execution_id = @execution_id
        )
        THROW 51557, N'A materialização read-only diverge do archive.', 1;
END;
GO

CREATE OR ALTER PROCEDURE stg.usp_archive_staging_lifecycle
    @plan_id UNIQUEIDENTIFIER,
    @policy_version NVARCHAR(MAX),
    @policy_fingerprint NVARCHAR(MAX)
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;

    IF @plan_id IS NULL OR @policy_version IS NULL OR @policy_fingerprint IS NULL
        OR DATALENGTH(@policy_version) > 256
        OR DATALENGTH(@policy_fingerprint) > 128
        THROW 51530, N'Permit de archive excede os limites do contrato.', 1;

    SET @policy_version = LTRIM(RTRIM(@policy_version));
    SET @policy_fingerprint = LOWER(LTRIM(RTRIM(@policy_fingerprint)));
    IF NULLIF(@policy_version, N'') IS NULL
        OR LEN(@policy_fingerprint) <> 64
        OR @policy_fingerprint COLLATE Latin1_General_100_BIN2 LIKE '%[^0-9a-f]%'
        THROW 51531, N'Permit de archive inválido.', 1;

    BEGIN TRANSACTION;
    DECLARE @lock_result INT;
    EXEC @lock_result = sys.sp_getapplock
        @Resource = N'V2_STAGING_LIFECYCLE', @LockMode = N'Exclusive',
        @LockOwner = N'Transaction', @LockTimeout = 5000, @DbPrincipal = N'public';
    IF @lock_result < 0
        THROW 51532, N'Não foi possível obter o lock do archive de staging.', 1;

    IF EXISTS (SELECT 1 FROM recon.staging_lifecycle_archive_manifest WITH (UPDLOCK, HOLDLOCK)
               WHERE plan_id = @plan_id)
    BEGIN
        IF NOT EXISTS (
            SELECT 1
            FROM recon.staging_lifecycle_archive_manifest AS archive_manifest
            INNER JOIN ctl.staging_lifecycle_plan AS lifecycle_plan
                ON lifecycle_plan.plan_id = archive_manifest.plan_id
            WHERE archive_manifest.plan_id = @plan_id
              AND lifecycle_plan.policy_version = @policy_version
              AND lifecycle_plan.policy_fingerprint = @policy_fingerprint
        )
            THROW 51533, N'Retry divergente do archive de staging.', 1;

        EXEC recon.usp_verify_staging_archive_internal @plan_id;
        COMMIT TRANSACTION;
        SELECT plan_id, archive_fingerprint_version, archive_fingerprint,
                archived_executions, archived_stage_rows, archived_candidate_rows,
                archived_bytes, archived_quarantine_rows, archived_application_rows,
                archived_reconciliation_rows, archived_state_event_rows,
                archived_page_audit_rows, archived_count_rows,
                archived_publication_event_rows, archived_promotion_result_rows,
                content_root_version,
                LOWER(CONVERT(VARCHAR(64), content_root, 2)) AS content_root,
                archived_at_utc,
                CAST(1 AS BIT) AS exact_retry
        FROM recon.staging_lifecycle_archive_manifest WHERE plan_id = @plan_id;
        RETURN;
    END;

    DECLARE @policy_id UNIQUEIDENTIFIER;
    DECLARE @selected_executions BIGINT;
    DECLARE @selected_stage_rows BIGINT;
    DECLARE @selected_candidate_rows BIGINT;
    DECLARE @selected_evidence_rows BIGINT;
    DECLARE @selected_archive_bytes BIGINT;
    DECLARE @maximum_stage_rows BIGINT;
    DECLARE @maximum_candidate_rows BIGINT;
    DECLARE @maximum_evidence_rows BIGINT;
    DECLARE @maximum_content_rows BIGINT;
    DECLARE @maximum_probe_rows BIGINT;
    DECLARE @planned_content_root_version NVARCHAR(64);
    DECLARE @planned_content_root BINARY(32);
    SELECT @policy_id = policy_id,
           @selected_executions = selected_executions,
           @selected_stage_rows = selected_stage_rows,
           @selected_candidate_rows = selected_candidate_rows,
           @selected_evidence_rows = selected_evidence_rows,
           @selected_archive_bytes = selected_archive_bytes,
           @maximum_stage_rows = maximum_stage_rows,
           @maximum_candidate_rows = maximum_candidate_rows,
           @maximum_evidence_rows = maximum_evidence_rows,
           @maximum_content_rows = maximum_content_rows,
           @maximum_probe_rows = maximum_probe_rows,
           @planned_content_root_version = planned_content_root_version,
           @planned_content_root = planned_content_root
    FROM ctl.staging_lifecycle_plan WITH (UPDLOCK, HOLDLOCK)
    WHERE plan_id = @plan_id
      AND policy_version = @policy_version
      AND policy_fingerprint = @policy_fingerprint;

    IF @policy_id IS NULL
        THROW 51534, N'Plano de lifecycle não encontrado para archive.', 1;

    DECLARE @hold_ledger_base_probe_rows BIGINT;
    SELECT @hold_ledger_base_probe_rows = COUNT_BIG(*)
    FROM (
        SELECT TOP (@maximum_probe_rows + 1) hold_definition.hold_id
        FROM ctl.staging_legal_hold AS hold_definition WITH (UPDLOCK, HOLDLOCK)
        INNER JOIN ctl.staging_lifecycle_plan_item AS item WITH (UPDLOCK, HOLDLOCK)
            ON item.execution_id = hold_definition.execution_id
        WHERE item.plan_id = @plan_id
    ) AS bounded_hold;
    IF @hold_ledger_base_probe_rows > @maximum_probe_rows
        THROW 51528, N'Histórico de legal hold excede o orçamento de probes do archive.', 1;
    DECLARE @remaining_hold_ledger_probe_rows BIGINT =
        @maximum_probe_rows - @hold_ledger_base_probe_rows;
    DECLARE @hold_ledger_event_probe_rows BIGINT;
    SELECT @hold_ledger_event_probe_rows = COUNT_BIG(*)
    FROM (
        SELECT TOP (@remaining_hold_ledger_probe_rows + 1) hold_event.hold_event_id
        FROM ctl.staging_legal_hold_event AS hold_event WITH (UPDLOCK, HOLDLOCK)
        INNER JOIN ctl.staging_lifecycle_plan_item AS item WITH (UPDLOCK, HOLDLOCK)
            ON item.execution_id = hold_event.execution_id
        WHERE item.plan_id = @plan_id
    ) AS bounded_event;
    IF @hold_ledger_event_probe_rows > @remaining_hold_ledger_probe_rows
        THROW 51528, N'Histórico de legal hold excede o orçamento de probes do archive.', 1;

    IF NOT EXISTS (
        SELECT 1
        FROM ctl.staging_retention_policy AS policy WITH (UPDLOCK, HOLDLOCK)
        WHERE policy.policy_id = @policy_id
          AND policy.policy_version = @policy_version
          AND policy.policy_fingerprint = @policy_fingerprint
          AND policy.revoked_at_utc IS NULL
          AND EXISTS (
              SELECT 1 FROM ctl.staging_retention_policy_event AS owner_event
              WHERE owner_event.policy_id = policy.policy_id
                AND owner_event.event_action = N'DATA_OWNER_APPROVED'
                AND owner_event.authority_evidence_fingerprint =
                    policy.data_owner_evidence_fingerprint
                AND owner_event.owner_role = policy.data_owner_role
                AND owner_event.occurred_at_utc = policy.approved_at_utc
          )
          AND EXISTS (
              SELECT 1 FROM ctl.staging_retention_policy_event AS compliance_event
              WHERE compliance_event.policy_id = policy.policy_id
                AND compliance_event.event_action = N'COMPLIANCE_APPROVED'
                AND compliance_event.authority_evidence_fingerprint =
                    policy.compliance_evidence_fingerprint
                AND compliance_event.owner_role = policy.compliance_owner_role
                AND compliance_event.occurred_at_utc = policy.approved_at_utc
          )
          AND NOT EXISTS (
              SELECT 1 FROM ctl.staging_retention_policy_event AS revoked_event
              WHERE revoked_event.policy_id = policy.policy_id
                AND revoked_event.event_action = N'REVOKED'
          )
    )
        THROW 51535, N'Política do plano não está ativa para archive.', 1;

    IF EXISTS (
        SELECT 1
        FROM ctl.staging_lifecycle_plan_item AS item WITH (UPDLOCK, HOLDLOCK)
        CROSS APPLY ctl.ufn_invalid_staging_legal_hold(item.execution_id) AS invalid_hold
        WHERE item.plan_id = @plan_id
    )
        THROW 51536, N'Ledger de legal hold divergiu depois do dry-run.', 1;

    IF EXISTS (
        SELECT 1
        FROM ctl.staging_lifecycle_plan_item AS item WITH (UPDLOCK, HOLDLOCK)
        INNER JOIN ctl.execution_attempt AS attempt WITH (UPDLOCK, HOLDLOCK)
            ON attempt.execution_id = item.execution_id
        WHERE item.plan_id = @plan_id
          AND (
              attempt.current_state <> item.terminal_state
              OR attempt.terminal_at_utc <> item.terminal_at_utc
              OR EXISTS (
                  SELECT 1
                  FROM ctl.ufn_invalid_execution_state_ledger(attempt.execution_id)
                      AS invalid_ledger
              )
              OR EXISTS (
                  SELECT 1 FROM ctl.execution_lease AS lease WITH (UPDLOCK, HOLDLOCK)
                  WHERE lease.execution_id = item.execution_id
                    AND lease.released_at_utc IS NULL
              )
              OR EXISTS (
                  SELECT 1 FROM ctl.staging_legal_hold AS hold_definition
                      WITH (UPDLOCK, HOLDLOCK)
                  WHERE hold_definition.execution_id = item.execution_id
                    AND hold_definition.released_at_utc IS NULL
              )
          )
    )
        THROW 51536, N'Estado, lease ou legal hold mudou depois do dry-run.', 1;

    DECLARE @live_content_root BINARY(32);
    DECLARE @live_content_rows BIGINT = @selected_stage_rows + @selected_candidate_rows
        + @selected_evidence_rows;
    EXEC recon.usp_compute_live_staging_content_root_internal
        @plan_id = @plan_id,
        @expected_content_rows = @live_content_rows,
        @content_root = @live_content_root OUTPUT;
    IF @planned_content_root_version <> N'archive-content-set-v1'
        OR @live_content_root <> @planned_content_root
        THROW 51537, N'O conteúdo vivo divergiu do selo criptográfico do plano.', 1;

    DECLARE @actual_stage_rows BIGINT;
    DECLARE @actual_candidate_rows BIGINT;
    SELECT @actual_stage_rows = COUNT_BIG(*) FROM (
        SELECT TOP (@selected_stage_rows + 1) stage_record.stage_record_id
        FROM stg.execution_record AS stage_record WITH (UPDLOCK, HOLDLOCK)
        INNER JOIN ctl.staging_lifecycle_plan_item AS item
            ON item.execution_id = stage_record.execution_id AND item.plan_id = @plan_id
    ) AS bounded_stage;

    SELECT @actual_candidate_rows = COUNT_BIG(*) FROM (
        SELECT TOP (@selected_candidate_rows + 1)
               stage_candidate.execution_id, stage_candidate.source_key
        FROM stg.execution_candidate AS stage_candidate WITH (UPDLOCK, HOLDLOCK)
        INNER JOIN ctl.staging_lifecycle_plan_item AS item
            ON item.execution_id = stage_candidate.execution_id AND item.plan_id = @plan_id
    ) AS bounded_candidate;

    IF @actual_stage_rows <> @selected_stage_rows
        OR @actual_candidate_rows <> @selected_candidate_rows
        OR (SELECT COUNT_BIG(*) FROM ctl.staging_lifecycle_plan_item
            WHERE plan_id = @plan_id) <> @selected_executions
        THROW 51537, N'O staging divergiu do plano limitado antes do archive.', 1;

    DECLARE @archived_quarantine_rows BIGINT = (
        SELECT COUNT_BIG(*) FROM (
        SELECT TOP (@selected_evidence_rows + 1) quarantine_record.quarantine_record_id
        FROM recon.quarantine_record AS quarantine_record WITH (UPDLOCK, HOLDLOCK)
        INNER JOIN ctl.staging_lifecycle_plan_item AS item
            ON item.execution_id = quarantine_record.execution_id
        WHERE item.plan_id = @plan_id
        ) AS bounded
    );
    DECLARE @archived_application_rows BIGINT = (
        SELECT COUNT_BIG(*) FROM (
        SELECT TOP (@selected_evidence_rows + 1) application_record.execution_id,
               application_record.source_key
        FROM recon.execution_candidate_application AS application_record WITH (UPDLOCK, HOLDLOCK)
        INNER JOIN ctl.staging_lifecycle_plan_item AS item
            ON item.execution_id = application_record.execution_id
        WHERE item.plan_id = @plan_id
        ) AS bounded
    );
    DECLARE @archived_reconciliation_rows BIGINT = (
        SELECT COUNT_BIG(*) FROM (
        SELECT TOP (@selected_evidence_rows + 1) reconciliation_record.execution_id
        FROM recon.execution_reconciliation_result AS reconciliation_record WITH (UPDLOCK, HOLDLOCK)
        INNER JOIN ctl.staging_lifecycle_plan_item AS item
            ON item.execution_id = reconciliation_record.execution_id
        WHERE item.plan_id = @plan_id
        ) AS bounded
    );
    DECLARE @archived_state_event_rows BIGINT =
        (SELECT COUNT_BIG(*) FROM (SELECT TOP (@selected_evidence_rows + 1)
             audit_record.state_event_id FROM ctl.execution_state_event AS audit_record
                 WITH (UPDLOCK, HOLDLOCK)
         INNER JOIN ctl.staging_lifecycle_plan_item AS item
             ON item.execution_id = audit_record.execution_id WHERE item.plan_id = @plan_id
             ) AS bounded);
    DECLARE @archived_page_audit_rows BIGINT =
        (SELECT COUNT_BIG(*) FROM (SELECT TOP (@selected_evidence_rows + 1)
             audit_record.page_audit_id FROM ctl.execution_page_audit AS audit_record
                 WITH (UPDLOCK, HOLDLOCK)
         INNER JOIN ctl.staging_lifecycle_plan_item AS item
             ON item.execution_id = audit_record.execution_id WHERE item.plan_id = @plan_id
             ) AS bounded);
    DECLARE @archived_count_rows BIGINT =
        (SELECT COUNT_BIG(*) FROM (SELECT TOP (@selected_evidence_rows + 1)
             audit_record.count_id FROM ctl.execution_count AS audit_record
                 WITH (UPDLOCK, HOLDLOCK)
         INNER JOIN ctl.staging_lifecycle_plan_item AS item
             ON item.execution_id = audit_record.execution_id WHERE item.plan_id = @plan_id
             ) AS bounded);
    DECLARE @archived_publication_event_rows BIGINT =
        (SELECT COUNT_BIG(*) FROM (SELECT TOP (@selected_evidence_rows + 1)
             audit_record.execution_id FROM ctl.execution_publication_event AS audit_record
                 WITH (UPDLOCK, HOLDLOCK)
         INNER JOIN ctl.staging_lifecycle_plan_item AS item
             ON item.execution_id = audit_record.execution_id WHERE item.plan_id = @plan_id
             ) AS bounded);
    DECLARE @archived_promotion_result_rows BIGINT =
        (SELECT COUNT_BIG(*) FROM (SELECT TOP (@selected_evidence_rows + 1)
             audit_record.execution_id FROM ctl.execution_promotion_result AS audit_record
                 WITH (UPDLOCK, HOLDLOCK)
         INNER JOIN ctl.staging_lifecycle_plan_item AS item
             ON item.execution_id = audit_record.execution_id WHERE item.plan_id = @plan_id
             ) AS bounded);

    IF @archived_quarantine_rows + @archived_application_rows
        + @archived_reconciliation_rows + @archived_state_event_rows
        + @archived_page_audit_rows + @archived_count_rows
        + @archived_publication_event_rows + @archived_promotion_result_rows
        <> @selected_evidence_rows
        THROW 51537, N'A evidência durável divergiu do plano limitado antes do archive.', 1;

    IF EXISTS (
        SELECT 1
        FROM ctl.staging_lifecycle_plan_item AS item
        INNER JOIN recon.staging_record_archive AS existing_archive
            ON existing_archive.execution_id = item.execution_id
        WHERE item.plan_id = @plan_id AND existing_archive.plan_id <> @plan_id
    )
        THROW 51538, N'Execução do plano já pertence a outro archive selado.', 1;

    DECLARE @archived_at_utc DATETIME2(3) = SYSUTCDATETIME();
    DECLARE @archive_fingerprint_version NVARCHAR(128) = N'staging-archive-manifest-v3';
    DECLARE @archive_fingerprint CHAR(64) = LOWER(CONVERT(VARCHAR(64),
        HASHBYTES('SHA2_256', CONVERT(VARBINARY(MAX), CONCAT(
            CONVERT(VARCHAR(36), @plan_id), N'|', @policy_fingerprint, N'|',
            @selected_executions, N'|', @selected_stage_rows, N'|',
            @selected_candidate_rows, N'|', @selected_archive_bytes, N'|',
            @archived_quarantine_rows, N'|', @archived_application_rows, N'|',
            @archived_reconciliation_rows, N'|', @archived_state_event_rows, N'|',
            @archived_page_audit_rows, N'|', @archived_count_rows, N'|',
            @archived_publication_event_rows, N'|', @archived_promotion_result_rows, N'|',
            @planned_content_root_version, N'|',
            CONVERT(VARCHAR(64), @planned_content_root, 2), N'|',
            CONVERT(VARCHAR(33), @archived_at_utc, 126)
        ))), 2));

    INSERT INTO recon.staging_lifecycle_archive_manifest (
        plan_id, archive_fingerprint_version, archive_fingerprint,
        archived_executions, archived_stage_rows, archived_candidate_rows, archived_bytes,
        archived_quarantine_rows, archived_application_rows,
        archived_reconciliation_rows, archived_state_event_rows,
        archived_page_audit_rows, archived_count_rows,
        archived_publication_event_rows, archived_promotion_result_rows,
        content_root_version, content_root, archived_at_utc
    ) VALUES (
        @plan_id, @archive_fingerprint_version, @archive_fingerprint,
        @selected_executions, @selected_stage_rows, @selected_candidate_rows,
        @selected_archive_bytes, @archived_quarantine_rows, @archived_application_rows,
        @archived_reconciliation_rows, @archived_state_event_rows,
        @archived_page_audit_rows, @archived_count_rows,
        @archived_publication_event_rows, @archived_promotion_result_rows,
        @planned_content_root_version, @planned_content_root, @archived_at_utc
    );

    INSERT INTO recon.staging_record_archive (
        plan_id, stage_record_id, execution_id, input_batch_number, input_record_ordinal,
        source_key, row_fingerprint_version, source_row_hash,
        presence_fingerprint_version, presence_fingerprint, source_freshness_at_utc,
        validation_disposition, quarantine_reason_code, staged_at_utc,
        archive_row_attestation_version, archive_row_attestation
    )
    SELECT @plan_id, stage_record.stage_record_id, stage_record.execution_id,
           stage_record.input_batch_number, stage_record.input_record_ordinal,
           stage_record.source_key, stage_record.row_fingerprint_version,
           stage_record.source_row_hash, stage_record.presence_fingerprint_version,
            stage_record.presence_fingerprint, stage_record.source_freshness_at_utc,
            stage_record.validation_disposition, stage_record.quarantine_reason_code,
           stage_record.staged_at_utc, N'archive-row-json-v1',
           recon.ufn_archive_row_attestation(N'stg.execution_record', canonical.canonical_row)
    FROM stg.execution_record AS stage_record
    INNER JOIN ctl.staging_lifecycle_plan_item AS item
        ON item.execution_id = stage_record.execution_id AND item.plan_id = @plan_id
    CROSS APPLY (SELECT (SELECT stage_record.stage_record_id, stage_record.execution_id,
                                stage_record.input_batch_number,
                                stage_record.input_record_ordinal, stage_record.source_key,
                                stage_record.row_fingerprint_version,
                                stage_record.source_row_hash,
                                stage_record.presence_fingerprint_version,
                                stage_record.presence_fingerprint,
                                stage_record.source_freshness_at_utc,
                                stage_record.validation_disposition,
                                stage_record.quarantine_reason_code, stage_record.staged_at_utc
                         FOR JSON PATH, WITHOUT_ARRAY_WRAPPER, INCLUDE_NULL_VALUES)
                        AS canonical_row) AS canonical;

    INSERT INTO recon.staging_candidate_archive (
        plan_id, execution_id, source_key, winner_stage_record_id,
        row_fingerprint_version, source_row_hash, presence_fingerprint_version,
        presence_fingerprint, source_freshness_at_utc, prepared_at_utc,
        archive_row_attestation_version, archive_row_attestation
    )
    SELECT @plan_id, stage_candidate.execution_id, stage_candidate.source_key,
           stage_candidate.winner_stage_record_id, stage_candidate.row_fingerprint_version,
           stage_candidate.source_row_hash, stage_candidate.presence_fingerprint_version,
           stage_candidate.presence_fingerprint, stage_candidate.source_freshness_at_utc,
           stage_candidate.prepared_at_utc, N'archive-row-json-v1',
           recon.ufn_archive_row_attestation(N'stg.execution_candidate', canonical.canonical_row)
    FROM stg.execution_candidate AS stage_candidate
    INNER JOIN ctl.staging_lifecycle_plan_item AS item
        ON item.execution_id = stage_candidate.execution_id AND item.plan_id = @plan_id
    CROSS APPLY (SELECT (SELECT stage_candidate.execution_id, stage_candidate.source_key,
                                stage_candidate.winner_stage_record_id,
                                stage_candidate.row_fingerprint_version,
                                stage_candidate.source_row_hash,
                                stage_candidate.presence_fingerprint_version,
                                stage_candidate.presence_fingerprint,
                                stage_candidate.source_freshness_at_utc,
                                stage_candidate.prepared_at_utc
                         FOR JSON PATH, WITHOUT_ARRAY_WRAPPER, INCLUDE_NULL_VALUES)
                        AS canonical_row) AS canonical;

    INSERT INTO recon.quarantine_record_archive (
        plan_id, quarantine_record_id, execution_id, stage_record_id, reason_code,
        source_key, row_fingerprint_version, source_row_hash,
        presence_fingerprint_version, presence_fingerprint, quarantined_at_utc,
        archive_row_attestation_version, archive_row_attestation
    )
    SELECT @plan_id, source_record.quarantine_record_id, source_record.execution_id,
           source_record.stage_record_id, source_record.reason_code, source_record.source_key,
           source_record.row_fingerprint_version, source_record.source_row_hash,
           source_record.presence_fingerprint_version, source_record.presence_fingerprint,
           source_record.quarantined_at_utc, N'archive-row-json-v1',
           recon.ufn_archive_row_attestation(N'recon.quarantine_record', canonical.canonical_row)
    FROM recon.quarantine_record AS source_record WITH (UPDLOCK, HOLDLOCK)
    INNER JOIN ctl.staging_lifecycle_plan_item AS item
        ON item.execution_id = source_record.execution_id AND item.plan_id = @plan_id
    CROSS APPLY (SELECT (SELECT source_record.quarantine_record_id,
                                source_record.execution_id, source_record.stage_record_id,
                                source_record.reason_code, source_record.source_key,
                                source_record.row_fingerprint_version,
                                source_record.source_row_hash,
                                source_record.presence_fingerprint_version,
                                source_record.presence_fingerprint,
                                source_record.quarantined_at_utc
                         FOR JSON PATH, WITHOUT_ARRAY_WRAPPER, INCLUDE_NULL_VALUES)
                        AS canonical_row) AS canonical;

    INSERT INTO recon.execution_candidate_application_archive (
        plan_id, execution_id, source_key, record_state_id, application_disposition,
        result_row_fingerprint_version, result_source_row_hash,
        result_presence_fingerprint_version, result_presence_fingerprint,
        result_source_freshness_at_utc, applied_at_utc,
        archive_row_attestation_version, archive_row_attestation
    )
    SELECT @plan_id, source_record.execution_id, source_record.source_key,
           source_record.record_state_id, source_record.application_disposition,
           source_record.result_row_fingerprint_version, source_record.result_source_row_hash,
           source_record.result_presence_fingerprint_version,
           source_record.result_presence_fingerprint,
           source_record.result_source_freshness_at_utc, source_record.applied_at_utc,
           N'archive-row-json-v1',
           recon.ufn_archive_row_attestation(
               N'recon.execution_candidate_application', canonical.canonical_row)
    FROM recon.execution_candidate_application AS source_record WITH (UPDLOCK, HOLDLOCK)
    INNER JOIN ctl.staging_lifecycle_plan_item AS item
        ON item.execution_id = source_record.execution_id AND item.plan_id = @plan_id
    CROSS APPLY (SELECT (SELECT source_record.execution_id, source_record.source_key,
                                source_record.record_state_id,
                                source_record.application_disposition,
                                source_record.result_row_fingerprint_version,
                                source_record.result_source_row_hash,
                                source_record.result_presence_fingerprint_version,
                                source_record.result_presence_fingerprint,
                                source_record.result_source_freshness_at_utc,
                                source_record.applied_at_utc
                         FOR JSON PATH, WITHOUT_ARRAY_WRAPPER, INCLUDE_NULL_VALUES)
                        AS canonical_row) AS canonical;

    INSERT INTO recon.execution_reconciliation_result_archive (
        plan_id, execution_id, candidate_rows, inserted_rows, updated_rows,
        reactivated_rows, noop_rows, stale_noop_rows, reconciled_at_utc, published_at_utc,
        archive_row_attestation_version, archive_row_attestation
    )
    SELECT @plan_id, source_record.execution_id, source_record.candidate_rows,
           source_record.inserted_rows, source_record.updated_rows,
           source_record.reactivated_rows, source_record.noop_rows,
           source_record.stale_noop_rows, source_record.reconciled_at_utc,
           source_record.published_at_utc, N'archive-row-json-v1',
           recon.ufn_archive_row_attestation(
               N'recon.execution_reconciliation_result', canonical.canonical_row)
    FROM recon.execution_reconciliation_result AS source_record WITH (UPDLOCK, HOLDLOCK)
    INNER JOIN ctl.staging_lifecycle_plan_item AS item
        ON item.execution_id = source_record.execution_id AND item.plan_id = @plan_id
    CROSS APPLY (SELECT (SELECT source_record.execution_id, source_record.candidate_rows,
                                source_record.inserted_rows, source_record.updated_rows,
                                source_record.reactivated_rows, source_record.noop_rows,
                                source_record.stale_noop_rows,
                                source_record.reconciled_at_utc, source_record.published_at_utc
                         FOR JSON PATH, WITHOUT_ARRAY_WRAPPER, INCLUDE_NULL_VALUES)
                        AS canonical_row) AS canonical;

    INSERT INTO recon.execution_state_event_archive (
        plan_id, state_event_id, execution_id, transition_sequence, previous_state,
        next_state, reason_code, transitioned_at_utc,
        archive_row_attestation_version, archive_row_attestation
    )
    SELECT @plan_id, source_record.state_event_id, source_record.execution_id,
           source_record.transition_sequence, source_record.previous_state,
           source_record.next_state, source_record.reason_code,
           source_record.transitioned_at_utc, N'archive-row-json-v1',
           recon.ufn_archive_row_attestation(N'ctl.execution_state_event', canonical.canonical_row)
    FROM ctl.execution_state_event AS source_record WITH (UPDLOCK, HOLDLOCK)
    INNER JOIN ctl.staging_lifecycle_plan_item AS item
        ON item.execution_id = source_record.execution_id AND item.plan_id = @plan_id
    CROSS APPLY (SELECT (SELECT source_record.state_event_id, source_record.execution_id,
                                source_record.transition_sequence, source_record.previous_state,
                                source_record.next_state, source_record.reason_code,
                                source_record.transitioned_at_utc
                         FOR JSON PATH, WITHOUT_ARRAY_WRAPPER, INCLUDE_NULL_VALUES)
                        AS canonical_row) AS canonical;

    INSERT INTO recon.execution_page_audit_archive (
        plan_id, page_audit_id, execution_id, page_number, page_attempt,
        requested_page_size, physical_rows, distinct_root_keys, response_bytes,
        terminal_empty_page, terminal_evidence_kind, read_at_utc,
        archive_row_attestation_version, archive_row_attestation
    )
    SELECT @plan_id, source_record.page_audit_id, source_record.execution_id,
           source_record.page_number, source_record.page_attempt,
           source_record.requested_page_size, source_record.physical_rows,
           source_record.distinct_root_keys, source_record.response_bytes,
           source_record.terminal_empty_page, source_record.terminal_evidence_kind,
           source_record.read_at_utc,
           N'archive-row-json-v1',
           recon.ufn_archive_row_attestation(N'ctl.execution_page_audit', canonical.canonical_row)
    FROM ctl.execution_page_audit AS source_record WITH (UPDLOCK, HOLDLOCK)
    INNER JOIN ctl.staging_lifecycle_plan_item AS item
        ON item.execution_id = source_record.execution_id AND item.plan_id = @plan_id
    CROSS APPLY (SELECT (SELECT source_record.page_audit_id, source_record.execution_id,
                                source_record.page_number, source_record.page_attempt,
                                source_record.requested_page_size, source_record.physical_rows,
                                source_record.distinct_root_keys, source_record.response_bytes,
                                source_record.terminal_empty_page,
                                source_record.terminal_evidence_kind, source_record.read_at_utc
                         FOR JSON PATH, WITHOUT_ARRAY_WRAPPER, INCLUDE_NULL_VALUES)
                        AS canonical_row) AS canonical;

    INSERT INTO recon.execution_count_archive (
        plan_id, count_id, execution_id, count_phase, physical_rows, distinct_root_keys,
        duplicate_rows, valid_rows, quarantined_root_keys, unidentified_quarantine_rows,
        recorded_at_utc, archive_row_attestation_version, archive_row_attestation
    )
    SELECT @plan_id, source_record.count_id, source_record.execution_id,
           source_record.count_phase, source_record.physical_rows,
           source_record.distinct_root_keys, source_record.duplicate_rows,
           source_record.valid_rows, source_record.quarantined_root_keys,
           source_record.unidentified_quarantine_rows, source_record.recorded_at_utc,
           N'archive-row-json-v1',
           recon.ufn_archive_row_attestation(N'ctl.execution_count', canonical.canonical_row)
    FROM ctl.execution_count AS source_record WITH (UPDLOCK, HOLDLOCK)
    INNER JOIN ctl.staging_lifecycle_plan_item AS item
        ON item.execution_id = source_record.execution_id AND item.plan_id = @plan_id
    CROSS APPLY (SELECT (SELECT source_record.count_id, source_record.execution_id,
                                source_record.count_phase, source_record.physical_rows,
                                source_record.distinct_root_keys, source_record.duplicate_rows,
                                source_record.valid_rows, source_record.quarantined_root_keys,
                                source_record.unidentified_quarantine_rows,
                                source_record.recorded_at_utc
                         FOR JSON PATH, WITHOUT_ARRAY_WRAPPER, INCLUDE_NULL_VALUES)
                        AS canonical_row) AS canonical;

    INSERT INTO recon.execution_publication_event_archive (
        plan_id, execution_id, partition_id, previous_published_execution_id,
        published_at_utc, incremental_frontier_before_utc,
        incremental_frontier_after_utc, watermark_last_partition_id,
        archive_row_attestation_version, archive_row_attestation
    )
    SELECT @plan_id, source_record.execution_id, source_record.partition_id,
           source_record.previous_published_execution_id, source_record.published_at_utc,
           source_record.incremental_frontier_before_utc,
           source_record.incremental_frontier_after_utc,
           source_record.watermark_last_partition_id, N'archive-row-json-v1',
           recon.ufn_archive_row_attestation(
               N'ctl.execution_publication_event', canonical.canonical_row)
    FROM ctl.execution_publication_event AS source_record WITH (UPDLOCK, HOLDLOCK)
    INNER JOIN ctl.staging_lifecycle_plan_item AS item
        ON item.execution_id = source_record.execution_id AND item.plan_id = @plan_id
    CROSS APPLY (SELECT (SELECT source_record.execution_id, source_record.partition_id,
                                source_record.previous_published_execution_id,
                                source_record.published_at_utc,
                                source_record.incremental_frontier_before_utc,
                                source_record.incremental_frontier_after_utc,
                                source_record.watermark_last_partition_id
                         FOR JSON PATH, WITHOUT_ARRAY_WRAPPER, INCLUDE_NULL_VALUES)
                        AS canonical_row) AS canonical;

    INSERT INTO recon.execution_promotion_result_archive (
        plan_id, execution_id, physical_rows, distinct_root_keys, candidate_rows,
        duplicate_rows, quarantined_root_keys, unidentified_quarantine_rows,
        quarantined_stage_rows, promoted_at_utc,
        archive_row_attestation_version, archive_row_attestation
    )
    SELECT @plan_id, source_record.execution_id, source_record.physical_rows,
           source_record.distinct_root_keys, source_record.candidate_rows,
           source_record.duplicate_rows, source_record.quarantined_root_keys,
           source_record.unidentified_quarantine_rows,
           source_record.quarantined_stage_rows, source_record.promoted_at_utc,
           N'archive-row-json-v1',
           recon.ufn_archive_row_attestation(
               N'ctl.execution_promotion_result', canonical.canonical_row)
    FROM ctl.execution_promotion_result AS source_record WITH (UPDLOCK, HOLDLOCK)
    INNER JOIN ctl.staging_lifecycle_plan_item AS item
        ON item.execution_id = source_record.execution_id AND item.plan_id = @plan_id
    CROSS APPLY (SELECT (SELECT source_record.execution_id, source_record.physical_rows,
                                source_record.distinct_root_keys, source_record.candidate_rows,
                                source_record.duplicate_rows,
                                source_record.quarantined_root_keys,
                                source_record.unidentified_quarantine_rows,
                                source_record.quarantined_stage_rows,
                                source_record.promoted_at_utc
                         FOR JSON PATH, WITHOUT_ARRAY_WRAPPER, INCLUDE_NULL_VALUES)
                        AS canonical_row) AS canonical;

    IF (SELECT COUNT_BIG(*) FROM recon.staging_record_archive WHERE plan_id = @plan_id)
            <> @selected_stage_rows
        OR (SELECT COUNT_BIG(*) FROM recon.staging_candidate_archive WHERE plan_id = @plan_id)
            <> @selected_candidate_rows
        OR EXISTS (
            SELECT stage_record.stage_record_id, stage_record.execution_id,
                   stage_record.input_batch_number, stage_record.input_record_ordinal,
                   stage_record.source_key, stage_record.row_fingerprint_version,
                   stage_record.source_row_hash, stage_record.presence_fingerprint_version,
                   stage_record.presence_fingerprint, stage_record.source_freshness_at_utc,
                   stage_record.validation_disposition, stage_record.quarantine_reason_code,
                   stage_record.staged_at_utc
            FROM stg.execution_record AS stage_record
            INNER JOIN ctl.staging_lifecycle_plan_item AS item
                ON item.execution_id = stage_record.execution_id AND item.plan_id = @plan_id
            EXCEPT
            SELECT archived_record.stage_record_id, archived_record.execution_id,
                   archived_record.input_batch_number, archived_record.input_record_ordinal,
                   archived_record.source_key, archived_record.row_fingerprint_version,
                   archived_record.source_row_hash, archived_record.presence_fingerprint_version,
                   archived_record.presence_fingerprint, archived_record.source_freshness_at_utc,
                   archived_record.validation_disposition, archived_record.quarantine_reason_code,
                   archived_record.staged_at_utc
            FROM recon.staging_record_archive AS archived_record
            WHERE archived_record.plan_id = @plan_id
        )
        OR EXISTS (
            SELECT archived_record.stage_record_id, archived_record.execution_id,
                   archived_record.input_batch_number, archived_record.input_record_ordinal,
                   archived_record.source_key, archived_record.row_fingerprint_version,
                   archived_record.source_row_hash, archived_record.presence_fingerprint_version,
                   archived_record.presence_fingerprint, archived_record.source_freshness_at_utc,
                   archived_record.validation_disposition, archived_record.quarantine_reason_code,
                   archived_record.staged_at_utc
            FROM recon.staging_record_archive AS archived_record
            WHERE archived_record.plan_id = @plan_id
            EXCEPT
            SELECT stage_record.stage_record_id, stage_record.execution_id,
                   stage_record.input_batch_number, stage_record.input_record_ordinal,
                   stage_record.source_key, stage_record.row_fingerprint_version,
                   stage_record.source_row_hash, stage_record.presence_fingerprint_version,
                   stage_record.presence_fingerprint, stage_record.source_freshness_at_utc,
                   stage_record.validation_disposition, stage_record.quarantine_reason_code,
                   stage_record.staged_at_utc
            FROM stg.execution_record AS stage_record
            INNER JOIN ctl.staging_lifecycle_plan_item AS item
                ON item.execution_id = stage_record.execution_id AND item.plan_id = @plan_id
        )
        OR EXISTS (
            SELECT stage_candidate.execution_id, stage_candidate.source_key,
                   stage_candidate.winner_stage_record_id, stage_candidate.row_fingerprint_version,
                   stage_candidate.source_row_hash, stage_candidate.presence_fingerprint_version,
                   stage_candidate.presence_fingerprint, stage_candidate.source_freshness_at_utc,
                   stage_candidate.prepared_at_utc
            FROM stg.execution_candidate AS stage_candidate
            INNER JOIN ctl.staging_lifecycle_plan_item AS item
                ON item.execution_id = stage_candidate.execution_id AND item.plan_id = @plan_id
            EXCEPT
            SELECT archived_candidate.execution_id, archived_candidate.source_key,
                   archived_candidate.winner_stage_record_id,
                   archived_candidate.row_fingerprint_version,
                   archived_candidate.source_row_hash,
                   archived_candidate.presence_fingerprint_version,
                   archived_candidate.presence_fingerprint,
                   archived_candidate.source_freshness_at_utc,
                   archived_candidate.prepared_at_utc
            FROM recon.staging_candidate_archive AS archived_candidate
            WHERE archived_candidate.plan_id = @plan_id
        )
        OR EXISTS (
            SELECT archived_candidate.execution_id, archived_candidate.source_key,
                   archived_candidate.winner_stage_record_id,
                   archived_candidate.row_fingerprint_version,
                   archived_candidate.source_row_hash,
                   archived_candidate.presence_fingerprint_version,
                   archived_candidate.presence_fingerprint,
                   archived_candidate.source_freshness_at_utc,
                   archived_candidate.prepared_at_utc
            FROM recon.staging_candidate_archive AS archived_candidate
            WHERE archived_candidate.plan_id = @plan_id
            EXCEPT
            SELECT stage_candidate.execution_id, stage_candidate.source_key,
                   stage_candidate.winner_stage_record_id, stage_candidate.row_fingerprint_version,
                   stage_candidate.source_row_hash, stage_candidate.presence_fingerprint_version,
                   stage_candidate.presence_fingerprint, stage_candidate.source_freshness_at_utc,
                   stage_candidate.prepared_at_utc
            FROM stg.execution_candidate AS stage_candidate
            INNER JOIN ctl.staging_lifecycle_plan_item AS item
                ON item.execution_id = stage_candidate.execution_id AND item.plan_id = @plan_id
        )
        THROW 51538, N'O archive tipado não reproduz exatamente o staging planejado.', 1;

    EXEC recon.usp_verify_staging_archive_internal @plan_id;

    COMMIT TRANSACTION;
    SELECT plan_id, archive_fingerprint_version, archive_fingerprint,
           archived_executions, archived_stage_rows, archived_candidate_rows,
           archived_bytes, archived_quarantine_rows, archived_application_rows,
           archived_reconciliation_rows, archived_state_event_rows,
           archived_page_audit_rows, archived_count_rows,
           archived_publication_event_rows, archived_promotion_result_rows,
           content_root_version,
           LOWER(CONVERT(VARCHAR(64), content_root, 2)) AS content_root,
           archived_at_utc,
           CAST(0 AS BIT) AS exact_retry
    FROM recon.staging_lifecycle_archive_manifest WHERE plan_id = @plan_id;
END;
GO

CREATE OR ALTER PROCEDURE stg.usp_purge_staging_lifecycle
    @plan_id UNIQUEIDENTIFIER,
    @policy_version NVARCHAR(MAX),
    @policy_fingerprint NVARCHAR(MAX),
    @archive_fingerprint_version NVARCHAR(MAX),
    @archive_fingerprint NVARCHAR(MAX)
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;

    IF @plan_id IS NULL OR @policy_version IS NULL OR @policy_fingerprint IS NULL
        OR @archive_fingerprint_version IS NULL OR @archive_fingerprint IS NULL
        OR DATALENGTH(@policy_version) > 256 OR DATALENGTH(@policy_fingerprint) > 128
        OR DATALENGTH(@archive_fingerprint_version) > 256
        OR DATALENGTH(@archive_fingerprint) > 128
        THROW 51540, N'Permit de purge excede os limites do contrato.', 1;

    SET @policy_version = LTRIM(RTRIM(@policy_version));
    SET @policy_fingerprint = LOWER(LTRIM(RTRIM(@policy_fingerprint)));
    SET @archive_fingerprint_version = LTRIM(RTRIM(@archive_fingerprint_version));
    SET @archive_fingerprint = LOWER(LTRIM(RTRIM(@archive_fingerprint)));
    IF NULLIF(@policy_version, N'') IS NULL
        OR LEN(@policy_fingerprint) <> 64
        OR @policy_fingerprint COLLATE Latin1_General_100_BIN2 LIKE '%[^0-9a-f]%'
        OR NULLIF(@archive_fingerprint_version, N'') IS NULL
        OR LEN(@archive_fingerprint) <> 64
        OR @archive_fingerprint COLLATE Latin1_General_100_BIN2 LIKE '%[^0-9a-f]%'
        THROW 51541, N'Permit de purge inválido.', 1;

    BEGIN TRANSACTION;
    DECLARE @lock_result INT;
    EXEC @lock_result = sys.sp_getapplock
        @Resource = N'V2_STAGING_LIFECYCLE', @LockMode = N'Exclusive',
        @LockOwner = N'Transaction', @LockTimeout = 5000,
        @DbPrincipal = N'public';
    IF @lock_result < 0
        THROW 51542, N'Não foi possível obter o lock do purge de staging.', 1;

    IF EXISTS (SELECT 1 FROM ctl.staging_lifecycle_purge_event WITH (UPDLOCK, HOLDLOCK)
               WHERE plan_id = @plan_id)
    BEGIN
        IF NOT EXISTS (
            SELECT 1
            FROM ctl.staging_lifecycle_purge_event AS purge_event
            INNER JOIN ctl.staging_lifecycle_plan AS lifecycle_plan
                ON lifecycle_plan.plan_id = purge_event.plan_id
            WHERE purge_event.plan_id = @plan_id
              AND lifecycle_plan.policy_version = @policy_version
              AND lifecycle_plan.policy_fingerprint = @policy_fingerprint
              AND purge_event.archive_fingerprint_version = @archive_fingerprint_version
              AND purge_event.archive_fingerprint = @archive_fingerprint
        )
            THROW 51543, N'Retry divergente do purge de staging.', 1;

        EXEC recon.usp_verify_staging_archive_internal @plan_id;
        IF EXISTS (
            SELECT 1
            FROM ctl.staging_lifecycle_plan_item AS item
            WHERE item.plan_id = @plan_id
              AND (
                  EXISTS (
                      SELECT 1 FROM stg.execution_record AS stage_record
                      WHERE stage_record.execution_id = item.execution_id
                  )
                  OR EXISTS (
                      SELECT 1 FROM stg.execution_candidate AS stage_candidate
                      WHERE stage_candidate.execution_id = item.execution_id
                  )
              )
        )
            THROW 51543, N'Retry de purge encontrou staging reaparecido.', 1;
        COMMIT TRANSACTION;
        SELECT plan_id, purged_executions, purged_stage_rows, purged_candidate_rows,
               purged_at_utc, CAST(1 AS BIT) AS exact_retry
        FROM ctl.staging_lifecycle_purge_event WHERE plan_id = @plan_id;
        RETURN;
    END;

    DECLARE @policy_id UNIQUEIDENTIFIER;
    DECLARE @archived_executions BIGINT;
    DECLARE @archived_stage_rows BIGINT;
    DECLARE @archived_candidate_rows BIGINT;
    DECLARE @archived_quarantine_rows BIGINT;
    DECLARE @archived_application_rows BIGINT;
    DECLARE @archived_reconciliation_rows BIGINT;
    DECLARE @archived_state_event_rows BIGINT;
    DECLARE @archived_page_audit_rows BIGINT;
    DECLARE @archived_count_rows BIGINT;
    DECLARE @archived_publication_event_rows BIGINT;
    DECLARE @archived_promotion_result_rows BIGINT;
    DECLARE @selected_content_rows BIGINT;
    DECLARE @maximum_probe_rows BIGINT;
    DECLARE @planned_content_root_version NVARCHAR(64);
    DECLARE @planned_content_root BINARY(32);
    SELECT @policy_id = lifecycle_plan.policy_id,
           @archived_executions = archive_manifest.archived_executions,
           @archived_stage_rows = archive_manifest.archived_stage_rows,
           @archived_candidate_rows = archive_manifest.archived_candidate_rows,
           @archived_quarantine_rows = archive_manifest.archived_quarantine_rows,
           @archived_application_rows = archive_manifest.archived_application_rows,
           @archived_reconciliation_rows = archive_manifest.archived_reconciliation_rows,
           @archived_state_event_rows = archive_manifest.archived_state_event_rows,
           @archived_page_audit_rows = archive_manifest.archived_page_audit_rows,
           @archived_count_rows = archive_manifest.archived_count_rows,
           @archived_publication_event_rows = archive_manifest.archived_publication_event_rows,
           @archived_promotion_result_rows = archive_manifest.archived_promotion_result_rows,
           @selected_content_rows = lifecycle_plan.selected_stage_rows
               + lifecycle_plan.selected_candidate_rows + lifecycle_plan.selected_evidence_rows,
           @maximum_probe_rows = lifecycle_plan.maximum_probe_rows,
           @planned_content_root_version = lifecycle_plan.planned_content_root_version,
           @planned_content_root = lifecycle_plan.planned_content_root
    FROM recon.staging_lifecycle_archive_manifest AS archive_manifest WITH (UPDLOCK, HOLDLOCK)
    INNER JOIN ctl.staging_lifecycle_plan AS lifecycle_plan WITH (UPDLOCK, HOLDLOCK)
        ON lifecycle_plan.plan_id = archive_manifest.plan_id
    WHERE archive_manifest.plan_id = @plan_id
      AND lifecycle_plan.policy_version = @policy_version
      AND lifecycle_plan.policy_fingerprint = @policy_fingerprint
      AND archive_manifest.archive_fingerprint_version = @archive_fingerprint_version
      AND archive_manifest.archive_fingerprint = @archive_fingerprint;

    IF @policy_id IS NULL
        THROW 51544, N'Archive verificado não encontrado para o purge.', 1;

    DECLARE @hold_ledger_base_probe_rows BIGINT;
    SELECT @hold_ledger_base_probe_rows = COUNT_BIG(*)
    FROM (
        SELECT TOP (@maximum_probe_rows + 1) hold_definition.hold_id
        FROM ctl.staging_legal_hold AS hold_definition WITH (UPDLOCK, HOLDLOCK)
        INNER JOIN ctl.staging_lifecycle_plan_item AS item WITH (UPDLOCK, HOLDLOCK)
            ON item.execution_id = hold_definition.execution_id
        WHERE item.plan_id = @plan_id
    ) AS bounded_hold;
    IF @hold_ledger_base_probe_rows > @maximum_probe_rows
        THROW 51528, N'Histórico de legal hold excede o orçamento de probes do purge.', 1;
    DECLARE @remaining_hold_ledger_probe_rows BIGINT =
        @maximum_probe_rows - @hold_ledger_base_probe_rows;
    DECLARE @hold_ledger_event_probe_rows BIGINT;
    SELECT @hold_ledger_event_probe_rows = COUNT_BIG(*)
    FROM (
        SELECT TOP (@remaining_hold_ledger_probe_rows + 1) hold_event.hold_event_id
        FROM ctl.staging_legal_hold_event AS hold_event WITH (UPDLOCK, HOLDLOCK)
        INNER JOIN ctl.staging_lifecycle_plan_item AS item WITH (UPDLOCK, HOLDLOCK)
            ON item.execution_id = hold_event.execution_id
        WHERE item.plan_id = @plan_id
    ) AS bounded_event;
    IF @hold_ledger_event_probe_rows > @remaining_hold_ledger_probe_rows
        THROW 51528, N'Histórico de legal hold excede o orçamento de probes do purge.', 1;

    IF NOT EXISTS (
        SELECT 1
        FROM ctl.staging_retention_policy AS policy WITH (UPDLOCK, HOLDLOCK)
        WHERE policy.policy_id = @policy_id
          AND policy.policy_version = @policy_version
          AND policy.policy_fingerprint = @policy_fingerprint
          AND policy.revoked_at_utc IS NULL
          AND EXISTS (
              SELECT 1 FROM ctl.staging_retention_policy_event AS owner_event
              WHERE owner_event.policy_id = policy.policy_id
                AND owner_event.event_action = N'DATA_OWNER_APPROVED'
                AND owner_event.authority_evidence_fingerprint =
                    policy.data_owner_evidence_fingerprint
                AND owner_event.owner_role = policy.data_owner_role
                AND owner_event.occurred_at_utc = policy.approved_at_utc
          )
          AND EXISTS (
              SELECT 1 FROM ctl.staging_retention_policy_event AS compliance_event
              WHERE compliance_event.policy_id = policy.policy_id
                AND compliance_event.event_action = N'COMPLIANCE_APPROVED'
                AND compliance_event.authority_evidence_fingerprint =
                    policy.compliance_evidence_fingerprint
                AND compliance_event.owner_role = policy.compliance_owner_role
                AND compliance_event.occurred_at_utc = policy.approved_at_utc
          )
          AND NOT EXISTS (
              SELECT 1 FROM ctl.staging_retention_policy_event AS revoked_event
              WHERE revoked_event.policy_id = policy.policy_id
                AND revoked_event.event_action = N'REVOKED'
          )
    )
        THROW 51545, N'Política do plano foi revogada antes do purge.', 1;

    IF EXISTS (
        SELECT 1
        FROM ctl.staging_lifecycle_plan_item AS item WITH (UPDLOCK, HOLDLOCK)
        CROSS APPLY ctl.ufn_invalid_staging_legal_hold(item.execution_id) AS invalid_hold
        WHERE item.plan_id = @plan_id
    )
        THROW 51546, N'Ledger de legal hold inválido bloqueia o purge.', 1;

    IF EXISTS (
        SELECT 1
        FROM ctl.staging_lifecycle_plan_item AS item WITH (UPDLOCK, HOLDLOCK)
        INNER JOIN ctl.execution_attempt AS attempt WITH (UPDLOCK, HOLDLOCK)
            ON attempt.execution_id = item.execution_id
        WHERE item.plan_id = @plan_id
          AND (
              attempt.current_state <> item.terminal_state
              OR attempt.terminal_at_utc <> item.terminal_at_utc
              OR EXISTS (
                  SELECT 1
                  FROM ctl.ufn_invalid_execution_state_ledger(attempt.execution_id)
                      AS invalid_ledger
              )
              OR EXISTS (
                  SELECT 1 FROM ctl.execution_lease AS lease WITH (UPDLOCK, HOLDLOCK)
                  WHERE lease.execution_id = item.execution_id
                    AND lease.released_at_utc IS NULL
              )
              OR EXISTS (
                  SELECT 1 FROM ctl.staging_legal_hold AS hold_definition
                      WITH (UPDLOCK, HOLDLOCK)
                  WHERE hold_definition.execution_id = item.execution_id
                    AND hold_definition.released_at_utc IS NULL
              )
          )
    )
        THROW 51546, N'Estado, lease ou legal hold bloqueia o purge.', 1;

    DECLARE @live_content_root BINARY(32);
    EXEC recon.usp_compute_live_staging_content_root_internal
        @plan_id = @plan_id,
        @expected_content_rows = @selected_content_rows,
        @content_root = @live_content_root OUTPUT;
    IF @planned_content_root_version <> N'archive-content-set-v1'
        OR @live_content_root <> @planned_content_root
        THROW 51547, N'Conteúdo vivo divergiu do selo criptográfico antes do purge.', 1;

    IF (SELECT COUNT_BIG(*) FROM recon.quarantine_record AS quarantine_record
        INNER JOIN ctl.staging_lifecycle_plan_item AS item
            ON item.execution_id = quarantine_record.execution_id
        WHERE item.plan_id = @plan_id) <> @archived_quarantine_rows
        OR (SELECT COUNT_BIG(*) FROM recon.execution_candidate_application AS application_record
            INNER JOIN ctl.staging_lifecycle_plan_item AS item
                ON item.execution_id = application_record.execution_id
            WHERE item.plan_id = @plan_id) <> @archived_application_rows
        OR (SELECT COUNT_BIG(*) FROM recon.execution_reconciliation_result AS reconciliation_record
            INNER JOIN ctl.staging_lifecycle_plan_item AS item
                ON item.execution_id = reconciliation_record.execution_id
            WHERE item.plan_id = @plan_id) <> @archived_reconciliation_rows
        OR (SELECT COUNT_BIG(*) FROM ctl.execution_state_event AS audit_record
            INNER JOIN ctl.staging_lifecycle_plan_item AS item
                ON item.execution_id = audit_record.execution_id
            WHERE item.plan_id = @plan_id) <> @archived_state_event_rows
        OR (SELECT COUNT_BIG(*) FROM ctl.execution_page_audit AS audit_record
            INNER JOIN ctl.staging_lifecycle_plan_item AS item
                ON item.execution_id = audit_record.execution_id
            WHERE item.plan_id = @plan_id) <> @archived_page_audit_rows
        OR (SELECT COUNT_BIG(*) FROM ctl.execution_count AS audit_record
            INNER JOIN ctl.staging_lifecycle_plan_item AS item
                ON item.execution_id = audit_record.execution_id
            WHERE item.plan_id = @plan_id) <> @archived_count_rows
        OR (SELECT COUNT_BIG(*) FROM ctl.execution_publication_event AS audit_record
            INNER JOIN ctl.staging_lifecycle_plan_item AS item
                ON item.execution_id = audit_record.execution_id
            WHERE item.plan_id = @plan_id) <> @archived_publication_event_rows
        OR (SELECT COUNT_BIG(*) FROM ctl.execution_promotion_result AS audit_record
            INNER JOIN ctl.staging_lifecycle_plan_item AS item
                ON item.execution_id = audit_record.execution_id
            WHERE item.plan_id = @plan_id) <> @archived_promotion_result_rows
        THROW 51547, N'Evidência durável divergiu depois do archive.', 1;

    EXEC recon.usp_verify_staging_archive_internal @plan_id;

    IF (SELECT COUNT_BIG(*) FROM stg.execution_record AS stage_record
        INNER JOIN ctl.staging_lifecycle_plan_item AS item
            ON item.execution_id = stage_record.execution_id
        WHERE item.plan_id = @plan_id) <> @archived_stage_rows
        OR (SELECT COUNT_BIG(*) FROM stg.execution_candidate AS stage_candidate
            INNER JOIN ctl.staging_lifecycle_plan_item AS item
                ON item.execution_id = stage_candidate.execution_id
            WHERE item.plan_id = @plan_id) <> @archived_candidate_rows
        OR EXISTS (
            SELECT stage_record.stage_record_id, stage_record.execution_id,
                   stage_record.input_batch_number, stage_record.input_record_ordinal,
                   stage_record.source_key, stage_record.row_fingerprint_version,
                   stage_record.source_row_hash, stage_record.presence_fingerprint_version,
                   stage_record.presence_fingerprint, stage_record.source_freshness_at_utc,
                   stage_record.validation_disposition, stage_record.quarantine_reason_code,
                   stage_record.staged_at_utc
            FROM stg.execution_record AS stage_record
            INNER JOIN ctl.staging_lifecycle_plan_item AS item
                ON item.execution_id = stage_record.execution_id AND item.plan_id = @plan_id
            EXCEPT
            SELECT archived_record.stage_record_id, archived_record.execution_id,
                   archived_record.input_batch_number, archived_record.input_record_ordinal,
                   archived_record.source_key, archived_record.row_fingerprint_version,
                   archived_record.source_row_hash, archived_record.presence_fingerprint_version,
                   archived_record.presence_fingerprint, archived_record.source_freshness_at_utc,
                   archived_record.validation_disposition, archived_record.quarantine_reason_code,
                   archived_record.staged_at_utc
            FROM recon.staging_record_archive AS archived_record
            WHERE archived_record.plan_id = @plan_id
        )
        OR EXISTS (
            SELECT archived_record.stage_record_id, archived_record.execution_id,
                   archived_record.input_batch_number, archived_record.input_record_ordinal,
                   archived_record.source_key, archived_record.row_fingerprint_version,
                   archived_record.source_row_hash, archived_record.presence_fingerprint_version,
                   archived_record.presence_fingerprint, archived_record.source_freshness_at_utc,
                   archived_record.validation_disposition, archived_record.quarantine_reason_code,
                   archived_record.staged_at_utc
            FROM recon.staging_record_archive AS archived_record
            WHERE archived_record.plan_id = @plan_id
            EXCEPT
            SELECT stage_record.stage_record_id, stage_record.execution_id,
                   stage_record.input_batch_number, stage_record.input_record_ordinal,
                   stage_record.source_key, stage_record.row_fingerprint_version,
                   stage_record.source_row_hash, stage_record.presence_fingerprint_version,
                   stage_record.presence_fingerprint, stage_record.source_freshness_at_utc,
                   stage_record.validation_disposition, stage_record.quarantine_reason_code,
                   stage_record.staged_at_utc
            FROM stg.execution_record AS stage_record
            INNER JOIN ctl.staging_lifecycle_plan_item AS item
                ON item.execution_id = stage_record.execution_id AND item.plan_id = @plan_id
        )
        OR EXISTS (
            SELECT stage_candidate.execution_id, stage_candidate.source_key,
                   stage_candidate.winner_stage_record_id, stage_candidate.row_fingerprint_version,
                   stage_candidate.source_row_hash, stage_candidate.presence_fingerprint_version,
                   stage_candidate.presence_fingerprint, stage_candidate.source_freshness_at_utc,
                   stage_candidate.prepared_at_utc
            FROM stg.execution_candidate AS stage_candidate
            INNER JOIN ctl.staging_lifecycle_plan_item AS item
                ON item.execution_id = stage_candidate.execution_id AND item.plan_id = @plan_id
            EXCEPT
            SELECT archived_candidate.execution_id, archived_candidate.source_key,
                   archived_candidate.winner_stage_record_id,
                   archived_candidate.row_fingerprint_version,
                   archived_candidate.source_row_hash,
                   archived_candidate.presence_fingerprint_version,
                   archived_candidate.presence_fingerprint,
                   archived_candidate.source_freshness_at_utc,
                   archived_candidate.prepared_at_utc
            FROM recon.staging_candidate_archive AS archived_candidate
            WHERE archived_candidate.plan_id = @plan_id
        )
        OR EXISTS (
            SELECT archived_candidate.execution_id, archived_candidate.source_key,
                   archived_candidate.winner_stage_record_id,
                   archived_candidate.row_fingerprint_version,
                   archived_candidate.source_row_hash,
                   archived_candidate.presence_fingerprint_version,
                   archived_candidate.presence_fingerprint,
                   archived_candidate.source_freshness_at_utc,
                   archived_candidate.prepared_at_utc
            FROM recon.staging_candidate_archive AS archived_candidate
            WHERE archived_candidate.plan_id = @plan_id
            EXCEPT
            SELECT stage_candidate.execution_id, stage_candidate.source_key,
                   stage_candidate.winner_stage_record_id, stage_candidate.row_fingerprint_version,
                   stage_candidate.source_row_hash, stage_candidate.presence_fingerprint_version,
                   stage_candidate.presence_fingerprint, stage_candidate.source_freshness_at_utc,
                   stage_candidate.prepared_at_utc
            FROM stg.execution_candidate AS stage_candidate
            INNER JOIN ctl.staging_lifecycle_plan_item AS item
                ON item.execution_id = stage_candidate.execution_id AND item.plan_id = @plan_id
        )
        THROW 51548, N'O staging vivo divergiu do archive verificado.', 1;

    DELETE stage_candidate
    FROM stg.execution_candidate AS stage_candidate
    INNER JOIN ctl.staging_lifecycle_plan_item AS item
        ON item.execution_id = stage_candidate.execution_id AND item.plan_id = @plan_id;
    DECLARE @purged_candidate_rows BIGINT = @@ROWCOUNT;

    DELETE stage_record
    FROM stg.execution_record AS stage_record
    INNER JOIN ctl.staging_lifecycle_plan_item AS item
        ON item.execution_id = stage_record.execution_id AND item.plan_id = @plan_id;
    DECLARE @purged_stage_rows BIGINT = @@ROWCOUNT;

    IF @purged_candidate_rows <> @archived_candidate_rows
        OR @purged_stage_rows <> @archived_stage_rows
        OR EXISTS (SELECT 1 FROM stg.execution_candidate AS stage_candidate
                   INNER JOIN ctl.staging_lifecycle_plan_item AS item
                       ON item.execution_id = stage_candidate.execution_id
                   WHERE item.plan_id = @plan_id)
        OR EXISTS (SELECT 1 FROM stg.execution_record AS stage_record
                   INNER JOIN ctl.staging_lifecycle_plan_item AS item
                       ON item.execution_id = stage_record.execution_id
                   WHERE item.plan_id = @plan_id)
        THROW 51549, N'O purge não removeu exatamente o staging autorizado.', 1;

    DECLARE @purged_at_utc DATETIME2(3) = SYSUTCDATETIME();
    INSERT INTO ctl.staging_lifecycle_purge_event (
        plan_id, archive_fingerprint_version, archive_fingerprint,
        purged_executions, purged_stage_rows, purged_candidate_rows, purged_at_utc
    ) VALUES (
        @plan_id, @archive_fingerprint_version, @archive_fingerprint,
        @archived_executions, @purged_stage_rows, @purged_candidate_rows, @purged_at_utc
    );

    COMMIT TRANSACTION;
    SELECT plan_id, purged_executions, purged_stage_rows, purged_candidate_rows,
           purged_at_utc, CAST(0 AS BIT) AS exact_retry
    FROM ctl.staging_lifecycle_purge_event WHERE plan_id = @plan_id;
END;
GO

CREATE OR ALTER PROCEDURE recon.usp_restore_staging_archive
    @restore_id UNIQUEIDENTIFIER,
    @plan_id UNIQUEIDENTIFIER,
    @execution_id UNIQUEIDENTIFIER,
    @archive_fingerprint_version NVARCHAR(MAX),
    @archive_fingerprint NVARCHAR(MAX),
    @maximum_stage_rows BIGINT,
    @maximum_candidate_rows BIGINT,
    @reason_code NVARCHAR(MAX)
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;

    IF @restore_id IS NULL OR @plan_id IS NULL OR @execution_id IS NULL
        OR @archive_fingerprint_version IS NULL OR @archive_fingerprint IS NULL
        OR @maximum_stage_rows IS NULL OR @maximum_candidate_rows IS NULL
        OR @reason_code IS NULL
        OR DATALENGTH(@archive_fingerprint_version) > 256
        OR DATALENGTH(@archive_fingerprint) > 128
        OR DATALENGTH(@reason_code) > 128
        THROW 51550, N'Requisição de restore excede os limites do contrato.', 1;

    SET @archive_fingerprint_version = LTRIM(RTRIM(@archive_fingerprint_version));
    SET @archive_fingerprint = LOWER(LTRIM(RTRIM(@archive_fingerprint)));
    SET @reason_code = LTRIM(RTRIM(@reason_code));
    IF NULLIF(@archive_fingerprint_version, N'') IS NULL
        OR LEN(@archive_fingerprint) <> 64
        OR @archive_fingerprint COLLATE Latin1_General_100_BIN2 LIKE '%[^0-9a-f]%'
        OR @maximum_stage_rows NOT BETWEEN 1 AND 1000000
        OR @maximum_candidate_rows NOT BETWEEN 1 AND 1000000
        OR LEFT(@reason_code, 1) COLLATE Latin1_General_100_BIN2 NOT LIKE '[A-Z]'
        OR @reason_code COLLATE Latin1_General_100_BIN2 LIKE '%[^A-Z0-9_]%'
        OR LEN(@reason_code) NOT BETWEEN 2 AND 64
        THROW 51551, N'Requisição de restore inválida.', 1;

    BEGIN TRANSACTION;
    DECLARE @lock_result INT;
    EXEC @lock_result = sys.sp_getapplock
        @Resource = N'V2_STAGING_LIFECYCLE', @LockMode = N'Exclusive',
        @LockOwner = N'Transaction', @LockTimeout = 5000,
        @DbPrincipal = N'public';
    IF @lock_result < 0
        THROW 51552, N'Não foi possível obter o lock do restore de staging.', 1;

    IF EXISTS (SELECT 1 FROM recon.staging_restore_session WITH (UPDLOCK, HOLDLOCK)
               WHERE restore_id = @restore_id)
    BEGIN
        IF NOT EXISTS (
            SELECT 1 FROM recon.staging_restore_session
            WHERE restore_id = @restore_id AND plan_id = @plan_id
              AND execution_id = @execution_id
              AND archive_fingerprint_version = @archive_fingerprint_version
              AND archive_fingerprint = @archive_fingerprint
              AND maximum_stage_rows = @maximum_stage_rows
              AND maximum_candidate_rows = @maximum_candidate_rows
              AND reason_code = @reason_code
        )
            THROW 51553, N'Retry divergente do restore de staging.', 1;

        EXEC recon.usp_verify_staging_archive_internal @plan_id = @plan_id;
        EXEC recon.usp_verify_staging_restore_internal @restore_id = @restore_id;

        COMMIT TRANSACTION;
        SELECT restore_id, plan_id, execution_id, restored_stage_rows,
               restored_candidate_rows, restored_at_utc,
               CAST(1 AS BIT) AS read_only, CAST(1 AS BIT) AS exact_retry
        FROM recon.staging_restore_session WHERE restore_id = @restore_id;
        RETURN;
    END;

    IF EXISTS (SELECT 1 FROM recon.staging_restore_session WITH (UPDLOCK, HOLDLOCK)
               WHERE plan_id = @plan_id AND execution_id = @execution_id)
        THROW 51554, N'Archive já possui materialização read-only com outro identificador.', 1;

    IF NOT EXISTS (
        SELECT 1 FROM recon.staging_lifecycle_archive_manifest WITH (UPDLOCK, HOLDLOCK)
        WHERE plan_id = @plan_id
          AND archive_fingerprint_version = @archive_fingerprint_version
          AND archive_fingerprint = @archive_fingerprint
    )
        THROW 51555, N'Archive não verificado para restore.', 1;

    EXEC recon.usp_verify_staging_archive_internal @plan_id = @plan_id;

    DECLARE @stage_rows BIGINT = (
        SELECT COUNT_BIG(*) FROM recon.staging_record_archive WITH (UPDLOCK, HOLDLOCK)
        WHERE plan_id = @plan_id AND execution_id = @execution_id
    );
    DECLARE @candidate_rows BIGINT = (
        SELECT COUNT_BIG(*) FROM recon.staging_candidate_archive WITH (UPDLOCK, HOLDLOCK)
        WHERE plan_id = @plan_id AND execution_id = @execution_id
    );

    IF NOT EXISTS (SELECT 1 FROM ctl.staging_lifecycle_plan_item WITH (UPDLOCK, HOLDLOCK)
                   WHERE plan_id = @plan_id AND execution_id = @execution_id
                     AND stage_rows = @stage_rows AND candidate_rows = @candidate_rows)
        OR @stage_rows > @maximum_stage_rows
        OR @candidate_rows > @maximum_candidate_rows
        THROW 51556, N'Archive excede o limite ou diverge do plano de restore.', 1;

    DECLARE @restored_at_utc DATETIME2(3) = SYSUTCDATETIME();
    INSERT INTO recon.staging_restore_session (
        restore_id, plan_id, execution_id, archive_fingerprint_version,
        archive_fingerprint, maximum_stage_rows, maximum_candidate_rows,
        reason_code, restored_stage_rows,
        restored_candidate_rows, restored_at_utc
    ) VALUES (
        @restore_id, @plan_id, @execution_id, @archive_fingerprint_version,
        @archive_fingerprint, @maximum_stage_rows, @maximum_candidate_rows,
        @reason_code, @stage_rows, @candidate_rows, @restored_at_utc
    );

    INSERT INTO recon.staging_restore_record (
        restore_id, stage_record_id, execution_id, input_batch_number, input_record_ordinal,
        source_key, row_fingerprint_version, source_row_hash,
        presence_fingerprint_version, presence_fingerprint, source_freshness_at_utc,
        validation_disposition, quarantine_reason_code, staged_at_utc
    )
    SELECT @restore_id, archived_record.stage_record_id, archived_record.execution_id,
           archived_record.input_batch_number, archived_record.input_record_ordinal,
           archived_record.source_key, archived_record.row_fingerprint_version,
           archived_record.source_row_hash, archived_record.presence_fingerprint_version,
           archived_record.presence_fingerprint, archived_record.source_freshness_at_utc,
           archived_record.validation_disposition, archived_record.quarantine_reason_code,
           archived_record.staged_at_utc
    FROM recon.staging_record_archive AS archived_record
    WHERE archived_record.plan_id = @plan_id AND archived_record.execution_id = @execution_id;

    INSERT INTO recon.staging_restore_candidate (
        restore_id, execution_id, source_key, winner_stage_record_id,
        row_fingerprint_version, source_row_hash, presence_fingerprint_version,
        presence_fingerprint, source_freshness_at_utc, prepared_at_utc
    )
    SELECT @restore_id, archived_candidate.execution_id, archived_candidate.source_key,
           archived_candidate.winner_stage_record_id,
           archived_candidate.row_fingerprint_version, archived_candidate.source_row_hash,
           archived_candidate.presence_fingerprint_version,
           archived_candidate.presence_fingerprint,
           archived_candidate.source_freshness_at_utc, archived_candidate.prepared_at_utc
    FROM recon.staging_candidate_archive AS archived_candidate
    WHERE archived_candidate.plan_id = @plan_id
      AND archived_candidate.execution_id = @execution_id;

    IF (SELECT COUNT_BIG(*) FROM recon.staging_restore_record WHERE restore_id = @restore_id)
            <> @stage_rows
        OR (SELECT COUNT_BIG(*) FROM recon.staging_restore_candidate
            WHERE restore_id = @restore_id) <> @candidate_rows
        OR EXISTS (
            SELECT archived_record.stage_record_id, archived_record.execution_id,
                   archived_record.input_batch_number, archived_record.input_record_ordinal,
                   archived_record.source_key, archived_record.row_fingerprint_version,
                   archived_record.source_row_hash, archived_record.presence_fingerprint_version,
                   archived_record.presence_fingerprint, archived_record.source_freshness_at_utc,
                   archived_record.validation_disposition, archived_record.quarantine_reason_code,
                   archived_record.staged_at_utc
            FROM recon.staging_record_archive AS archived_record
            WHERE archived_record.plan_id = @plan_id
              AND archived_record.execution_id = @execution_id
            EXCEPT
            SELECT restore_record.stage_record_id, restore_record.execution_id,
                   restore_record.input_batch_number, restore_record.input_record_ordinal,
                   restore_record.source_key, restore_record.row_fingerprint_version,
                   restore_record.source_row_hash, restore_record.presence_fingerprint_version,
                   restore_record.presence_fingerprint, restore_record.source_freshness_at_utc,
                   restore_record.validation_disposition, restore_record.quarantine_reason_code,
                   restore_record.staged_at_utc
            FROM recon.staging_restore_record AS restore_record
            WHERE restore_record.restore_id = @restore_id
        )
        OR EXISTS (
            SELECT restore_record.stage_record_id, restore_record.execution_id,
                   restore_record.input_batch_number, restore_record.input_record_ordinal,
                   restore_record.source_key, restore_record.row_fingerprint_version,
                   restore_record.source_row_hash, restore_record.presence_fingerprint_version,
                   restore_record.presence_fingerprint, restore_record.source_freshness_at_utc,
                   restore_record.validation_disposition, restore_record.quarantine_reason_code,
                   restore_record.staged_at_utc
            FROM recon.staging_restore_record AS restore_record
            WHERE restore_record.restore_id = @restore_id
            EXCEPT
            SELECT archived_record.stage_record_id, archived_record.execution_id,
                   archived_record.input_batch_number, archived_record.input_record_ordinal,
                   archived_record.source_key, archived_record.row_fingerprint_version,
                   archived_record.source_row_hash, archived_record.presence_fingerprint_version,
                   archived_record.presence_fingerprint, archived_record.source_freshness_at_utc,
                   archived_record.validation_disposition, archived_record.quarantine_reason_code,
                   archived_record.staged_at_utc
            FROM recon.staging_record_archive AS archived_record
            WHERE archived_record.plan_id = @plan_id
              AND archived_record.execution_id = @execution_id
        )
        OR EXISTS (
            SELECT archived_candidate.execution_id, archived_candidate.source_key,
                   archived_candidate.winner_stage_record_id,
                   archived_candidate.row_fingerprint_version,
                   archived_candidate.source_row_hash,
                   archived_candidate.presence_fingerprint_version,
                   archived_candidate.presence_fingerprint,
                   archived_candidate.source_freshness_at_utc,
                   archived_candidate.prepared_at_utc
            FROM recon.staging_candidate_archive AS archived_candidate
            WHERE archived_candidate.plan_id = @plan_id
              AND archived_candidate.execution_id = @execution_id
            EXCEPT
            SELECT restore_candidate.execution_id, restore_candidate.source_key,
                   restore_candidate.winner_stage_record_id,
                   restore_candidate.row_fingerprint_version,
                   restore_candidate.source_row_hash,
                   restore_candidate.presence_fingerprint_version,
                   restore_candidate.presence_fingerprint,
                   restore_candidate.source_freshness_at_utc,
                   restore_candidate.prepared_at_utc
            FROM recon.staging_restore_candidate AS restore_candidate
            WHERE restore_candidate.restore_id = @restore_id
        )
        OR EXISTS (
            SELECT restore_candidate.execution_id, restore_candidate.source_key,
                   restore_candidate.winner_stage_record_id,
                   restore_candidate.row_fingerprint_version,
                   restore_candidate.source_row_hash,
                   restore_candidate.presence_fingerprint_version,
                   restore_candidate.presence_fingerprint,
                   restore_candidate.source_freshness_at_utc,
                   restore_candidate.prepared_at_utc
            FROM recon.staging_restore_candidate AS restore_candidate
            WHERE restore_candidate.restore_id = @restore_id
            EXCEPT
            SELECT archived_candidate.execution_id, archived_candidate.source_key,
                   archived_candidate.winner_stage_record_id,
                   archived_candidate.row_fingerprint_version,
                   archived_candidate.source_row_hash,
                   archived_candidate.presence_fingerprint_version,
                   archived_candidate.presence_fingerprint,
                   archived_candidate.source_freshness_at_utc,
                   archived_candidate.prepared_at_utc
            FROM recon.staging_candidate_archive AS archived_candidate
            WHERE archived_candidate.plan_id = @plan_id
              AND archived_candidate.execution_id = @execution_id
        )
        THROW 51557, N'A materialização read-only diverge do archive.', 1;

    EXEC recon.usp_verify_staging_restore_internal @restore_id = @restore_id;

    COMMIT TRANSACTION;
    SELECT restore_id, plan_id, execution_id, restored_stage_rows,
           restored_candidate_rows, restored_at_utc,
           CAST(1 AS BIT) AS read_only, CAST(0 AS BIT) AS exact_retry
    FROM recon.staging_restore_session WHERE restore_id = @restore_id;
END;
GO

EXEC dbo.usp_publish_v2_procedure_grant
    N'ctl', N'usp_approve_staging_retention_policy', N'v2_retention_governor';
EXEC dbo.usp_publish_v2_procedure_grant
    N'ctl', N'usp_revoke_staging_retention_policy', N'v2_retention_governor';
EXEC dbo.usp_publish_v2_procedure_grant
    N'ctl', N'usp_place_staging_legal_hold', N'v2_retention_governor';
EXEC dbo.usp_publish_v2_procedure_grant
    N'ctl', N'usp_release_staging_legal_hold', N'v2_retention_governor';

EXEC dbo.usp_publish_v2_procedure_grant
    N'stg', N'usp_plan_staging_lifecycle', N'v2_lifecycle_reviewer';
EXEC dbo.usp_publish_v2_procedure_grant
    N'stg', N'usp_archive_staging_lifecycle', N'v2_lifecycle_operator';
EXEC dbo.usp_publish_v2_procedure_grant
    N'stg', N'usp_purge_staging_lifecycle', N'v2_lifecycle_operator';
EXEC dbo.usp_publish_v2_procedure_grant
    N'recon', N'usp_restore_staging_archive', N'v2_archive_restorer';
