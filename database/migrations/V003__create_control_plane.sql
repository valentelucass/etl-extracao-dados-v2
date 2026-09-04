-- V2-020: control plane compartilhado. Não cria objeto de domínio, staging, fato, view pública,
-- login, usuário, credencial ou job. Estado de execução é separado de payload e quarentena.

SET XACT_ABORT ON;
SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;

CREATE TABLE ctl.source_catalog (
    source_instance NVARCHAR(128) COLLATE Latin1_General_100_BIN2 NOT NULL,
    source_kind NVARCHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL,
    active BIT NOT NULL CONSTRAINT DF_ctl_source_catalog_active DEFAULT (1),
    registered_at_utc DATETIME2(3) NOT NULL,
    CONSTRAINT PK_ctl_source_catalog PRIMARY KEY CLUSTERED (source_instance),
    CONSTRAINT CK_ctl_source_catalog_source_instance_non_blank
        CHECK (
            LEN(LTRIM(RTRIM(source_instance))) > 0
            AND DATALENGTH(source_instance) = DATALENGTH(LTRIM(RTRIM(source_instance)))
        ),
    CONSTRAINT CK_ctl_source_catalog_source_kind_non_blank
        CHECK (
            LEN(LTRIM(RTRIM(source_kind))) > 0
            AND DATALENGTH(source_kind) = DATALENGTH(LTRIM(RTRIM(source_kind)))
        )
);
GO

CREATE TABLE ctl.execution_cycle (
    cycle_id UNIQUEIDENTIFIER NOT NULL,
    plan_version NVARCHAR(128) COLLATE Latin1_General_100_BIN2 NOT NULL,
    plan_fingerprint CHAR(64) NOT NULL,
    planned_at_utc DATETIME2(3) NOT NULL,
    CONSTRAINT PK_ctl_execution_cycle PRIMARY KEY CLUSTERED (cycle_id),
    CONSTRAINT CK_ctl_execution_cycle_plan_version
        CHECK (
            LEN(LTRIM(RTRIM(plan_version))) > 0
            AND DATALENGTH(plan_version) = DATALENGTH(LTRIM(RTRIM(plan_version)))
        ),
    CONSTRAINT CK_ctl_execution_cycle_plan_fingerprint
        CHECK (plan_fingerprint NOT LIKE '%[^0-9A-Fa-f]%')
);
GO

CREATE TABLE ctl.execution_partition (
    partition_id BIGINT IDENTITY(1, 1) NOT NULL,
    environment_name NVARCHAR(32) COLLATE Latin1_General_100_BIN2 NOT NULL,
    source_instance NVARCHAR(128) COLLATE Latin1_General_100_BIN2 NOT NULL,
    tenant_scope NVARCHAR(128) COLLATE Latin1_General_100_BIN2 NOT NULL,
    entity_name NVARCHAR(128) COLLATE Latin1_General_100_BIN2 NOT NULL,
    execution_mode NVARCHAR(16) COLLATE Latin1_General_100_BIN2 NOT NULL,
    partition_start_utc DATETIME2(3) NOT NULL,
    partition_end_exclusive_utc DATETIME2(3) NOT NULL,
    current_execution_id UNIQUEIDENTIFIER NULL,
    current_state NVARCHAR(32) COLLATE Latin1_General_100_BIN2 NULL,
    next_attempt_number INT NOT NULL
        CONSTRAINT DF_ctl_execution_partition_next_attempt_number DEFAULT (1),
    created_at_utc DATETIME2(3) NOT NULL,
    CONSTRAINT PK_ctl_execution_partition PRIMARY KEY CLUSTERED (partition_id),
    CONSTRAINT UQ_ctl_execution_partition_semantic_key UNIQUE (
        environment_name,
        source_instance,
        tenant_scope,
        entity_name,
        execution_mode,
        partition_start_utc,
        partition_end_exclusive_utc
    ),
    CONSTRAINT FK_ctl_execution_partition_source_catalog
        FOREIGN KEY (source_instance) REFERENCES ctl.source_catalog (source_instance),
    CONSTRAINT CK_ctl_execution_partition_non_blank CHECK (
        LEN(LTRIM(RTRIM(environment_name))) > 0
        AND LEN(LTRIM(RTRIM(source_instance))) > 0
        AND LEN(LTRIM(RTRIM(tenant_scope))) > 0
        AND LEN(LTRIM(RTRIM(entity_name))) > 0
        AND DATALENGTH(environment_name) = DATALENGTH(LTRIM(RTRIM(environment_name)))
        AND DATALENGTH(source_instance) = DATALENGTH(LTRIM(RTRIM(source_instance)))
        AND DATALENGTH(tenant_scope) = DATALENGTH(LTRIM(RTRIM(tenant_scope)))
        AND DATALENGTH(entity_name) = DATALENGTH(LTRIM(RTRIM(entity_name)))
    ),
    CONSTRAINT CK_ctl_execution_partition_mode CHECK (
        execution_mode IN (N'INCREMENTAL', N'BOOTSTRAP', N'BACKFILL', N'REPLAY', N'SWEEP')
        AND DATALENGTH(execution_mode) = DATALENGTH(LTRIM(RTRIM(execution_mode)))
        AND (
            current_state IS NULL
            OR (
                current_state IN (
                    N'PLANNED', N'EXTRACTING', N'EXTRACTED', N'STAGED', N'PROMOTED',
                    N'RECONCILED', N'PUBLISHED', N'BLOCKED', N'SKIPPED',
                    N'NOT_APPLICABLE', N'FAILED', N'CANCELLED', N'DEGRADED'
                )
                AND DATALENGTH(current_state) = DATALENGTH(LTRIM(RTRIM(current_state)))
            )
        )
    ),
    CONSTRAINT CK_ctl_execution_partition_interval CHECK (
        partition_start_utc < partition_end_exclusive_utc
    ),
    CONSTRAINT CK_ctl_execution_partition_next_attempt_number CHECK (next_attempt_number >= 1)
);
GO

CREATE TABLE ctl.execution_attempt (
    execution_id UNIQUEIDENTIFIER NOT NULL,
    partition_id BIGINT NOT NULL,
    cycle_id UNIQUEIDENTIFIER NOT NULL,
    attempt_number INT NOT NULL,
    window_strategy NVARCHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL,
    contract_version NVARCHAR(128) COLLATE Latin1_General_100_BIN2 NOT NULL,
    contract_fingerprint CHAR(64) NOT NULL,
    configuration_version NVARCHAR(128) COLLATE Latin1_General_100_BIN2 NOT NULL,
    configuration_fingerprint CHAR(64) NOT NULL,
    idempotency_key NVARCHAR(128) COLLATE Latin1_General_100_BIN2 NOT NULL,
    replay_of_execution_id UNIQUEIDENTIFIER NULL,
    current_state NVARCHAR(32) COLLATE Latin1_General_100_BIN2 NOT NULL,
    next_transition_sequence INT NOT NULL
        CONSTRAINT DF_ctl_execution_attempt_next_transition_sequence DEFAULT (3),
    started_at_utc DATETIME2(3) NOT NULL,
    terminal_at_utc DATETIME2(3) NULL,
    CONSTRAINT PK_ctl_execution_attempt PRIMARY KEY CLUSTERED (execution_id),
    CONSTRAINT UQ_ctl_execution_attempt_partition_attempt UNIQUE (partition_id, attempt_number),
    CONSTRAINT UQ_ctl_execution_attempt_partition_execution UNIQUE (partition_id, execution_id),
    CONSTRAINT UQ_ctl_execution_attempt_idempotency_key UNIQUE (idempotency_key),
    CONSTRAINT FK_ctl_execution_attempt_partition
        FOREIGN KEY (partition_id) REFERENCES ctl.execution_partition (partition_id),
    CONSTRAINT FK_ctl_execution_attempt_cycle
        FOREIGN KEY (cycle_id) REFERENCES ctl.execution_cycle (cycle_id),
    CONSTRAINT FK_ctl_execution_attempt_replay
        FOREIGN KEY (replay_of_execution_id) REFERENCES ctl.execution_attempt (execution_id),
    CONSTRAINT CK_ctl_execution_attempt_non_blank CHECK (
        LEN(LTRIM(RTRIM(window_strategy))) > 0
        AND LEN(LTRIM(RTRIM(contract_version))) > 0
        AND LEN(LTRIM(RTRIM(configuration_version))) > 0
        AND LEN(LTRIM(RTRIM(idempotency_key))) > 0
        AND DATALENGTH(window_strategy) = DATALENGTH(LTRIM(RTRIM(window_strategy)))
        AND DATALENGTH(contract_version) = DATALENGTH(LTRIM(RTRIM(contract_version)))
        AND DATALENGTH(configuration_version) = DATALENGTH(LTRIM(RTRIM(configuration_version)))
        AND DATALENGTH(idempotency_key) = DATALENGTH(LTRIM(RTRIM(idempotency_key)))
    ),
    CONSTRAINT CK_ctl_execution_attempt_contract_fingerprint
        CHECK (contract_fingerprint NOT LIKE '%[^0-9A-Fa-f]%'),
    CONSTRAINT CK_ctl_execution_attempt_configuration_fingerprint
        CHECK (configuration_fingerprint NOT LIKE '%[^0-9A-Fa-f]%'),
    CONSTRAINT CK_ctl_execution_attempt_next_transition_sequence
        CHECK (next_transition_sequence >= 3),
    CONSTRAINT CK_ctl_execution_attempt_state CHECK (
        current_state IN (
            N'PLANNED', N'EXTRACTING', N'EXTRACTED', N'STAGED', N'PROMOTED', N'RECONCILED', N'PUBLISHED',
            N'BLOCKED', N'SKIPPED', N'NOT_APPLICABLE', N'FAILED', N'CANCELLED', N'DEGRADED'
        )
        AND DATALENGTH(current_state) = DATALENGTH(LTRIM(RTRIM(current_state)))
    )
);
GO

ALTER TABLE ctl.execution_partition
ADD CONSTRAINT FK_ctl_execution_partition_current_execution
    FOREIGN KEY (current_execution_id) REFERENCES ctl.execution_attempt (execution_id);
GO

CREATE TABLE ctl.execution_state_event (
    state_event_id BIGINT IDENTITY(1, 1) NOT NULL,
    execution_id UNIQUEIDENTIFIER NOT NULL,
    transition_sequence INT NOT NULL,
    previous_state NVARCHAR(32) COLLATE Latin1_General_100_BIN2 NULL,
    next_state NVARCHAR(32) COLLATE Latin1_General_100_BIN2 NOT NULL,
    reason_code NVARCHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL,
    transitioned_at_utc DATETIME2(3) NOT NULL,
    CONSTRAINT PK_ctl_execution_state_event PRIMARY KEY CLUSTERED (state_event_id),
    CONSTRAINT UQ_ctl_execution_state_event_sequence UNIQUE (execution_id, transition_sequence),
    CONSTRAINT FK_ctl_execution_state_event_execution
        FOREIGN KEY (execution_id) REFERENCES ctl.execution_attempt (execution_id),
    CONSTRAINT CK_ctl_execution_state_event_previous_state CHECK (
        previous_state IS NULL OR previous_state IN (
            N'PLANNED', N'EXTRACTING', N'EXTRACTED', N'STAGED', N'PROMOTED', N'RECONCILED', N'PUBLISHED',
            N'BLOCKED', N'SKIPPED', N'NOT_APPLICABLE', N'FAILED', N'CANCELLED', N'DEGRADED'
        ) AND DATALENGTH(previous_state) = DATALENGTH(LTRIM(RTRIM(previous_state)))
    ),
    CONSTRAINT CK_ctl_execution_state_event_next_state CHECK (
        next_state IN (
            N'PLANNED', N'EXTRACTING', N'EXTRACTED', N'STAGED', N'PROMOTED', N'RECONCILED', N'PUBLISHED',
            N'BLOCKED', N'SKIPPED', N'NOT_APPLICABLE', N'FAILED', N'CANCELLED', N'DEGRADED'
        )
        AND DATALENGTH(next_state) = DATALENGTH(LTRIM(RTRIM(next_state)))
    ),
    CONSTRAINT CK_ctl_execution_state_event_reason_code CHECK (
        LEFT(reason_code, 1) COLLATE Latin1_General_100_BIN2 LIKE '[A-Z]'
        AND reason_code COLLATE Latin1_General_100_BIN2 NOT LIKE '%[^A-Z0-9_]%'
        AND LEN(reason_code) BETWEEN 2 AND 64
        AND DATALENGTH(reason_code) = DATALENGTH(LTRIM(RTRIM(reason_code)))
    )
);
GO

CREATE TABLE ctl.execution_lease (
    lease_id UNIQUEIDENTIFIER NOT NULL,
    partition_id BIGINT NOT NULL,
    execution_id UNIQUEIDENTIFIER NOT NULL,
    acquired_at_utc DATETIME2(3) NOT NULL,
    heartbeat_at_utc DATETIME2(3) NOT NULL,
    expires_at_utc DATETIME2(3) NOT NULL,
    released_at_utc DATETIME2(3) NULL,
    CONSTRAINT PK_ctl_execution_lease PRIMARY KEY CLUSTERED (lease_id),
    CONSTRAINT UQ_ctl_execution_lease_execution UNIQUE (execution_id),
    CONSTRAINT FK_ctl_execution_lease_partition
        FOREIGN KEY (partition_id) REFERENCES ctl.execution_partition (partition_id),
    CONSTRAINT FK_ctl_execution_lease_execution
        FOREIGN KEY (execution_id) REFERENCES ctl.execution_attempt (execution_id),
    CONSTRAINT CK_ctl_execution_lease_expiry CHECK (expires_at_utc > acquired_at_utc),
    CONSTRAINT CK_ctl_execution_lease_release CHECK (
        released_at_utc IS NULL OR released_at_utc >= acquired_at_utc
    )
);
GO

CREATE UNIQUE INDEX UX_ctl_execution_lease_active_partition
    ON ctl.execution_lease (partition_id)
    WHERE released_at_utc IS NULL;
GO

CREATE TABLE ctl.execution_page_audit (
    page_audit_id BIGINT IDENTITY(1, 1) NOT NULL,
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
    CONSTRAINT PK_ctl_execution_page_audit PRIMARY KEY CLUSTERED (page_audit_id),
    CONSTRAINT UQ_ctl_execution_page_audit_attempt UNIQUE (execution_id, page_number, page_attempt),
    CONSTRAINT FK_ctl_execution_page_audit_execution
        FOREIGN KEY (execution_id) REFERENCES ctl.execution_attempt (execution_id),
    CONSTRAINT CK_ctl_execution_page_audit_values CHECK (
        page_number > 0
        AND page_attempt > 0
        AND requested_page_size > 0
        AND physical_rows >= 0
        AND distinct_root_keys >= 0
        AND distinct_root_keys <= physical_rows
        AND response_bytes >= 0
        AND terminal_evidence_kind IN (
            N'NONE', N'DATA_EXPORT_EMPTY_PAGE', N'GRAPHQL_PAGE_INFO'
        )
        AND (
            (terminal_evidence_kind = N'NONE' AND terminal_empty_page = 0)
            OR (
                terminal_evidence_kind = N'DATA_EXPORT_EMPTY_PAGE'
                AND terminal_empty_page = 1
                AND physical_rows = 0
            )
            OR (
                terminal_evidence_kind = N'GRAPHQL_PAGE_INFO'
                AND terminal_empty_page = 0
                AND physical_rows > 0
            )
        )
    )
);
GO

CREATE TABLE ctl.execution_count (
    count_id BIGINT IDENTITY(1, 1) NOT NULL,
    execution_id UNIQUEIDENTIFIER NOT NULL,
    count_phase NVARCHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL,
    physical_rows BIGINT NOT NULL,
    distinct_root_keys BIGINT NOT NULL,
    duplicate_rows BIGINT NOT NULL,
    valid_rows BIGINT NOT NULL,
    quarantined_root_keys BIGINT NOT NULL,
    unidentified_quarantine_rows BIGINT NOT NULL,
    recorded_at_utc DATETIME2(3) NOT NULL,
    CONSTRAINT PK_ctl_execution_count PRIMARY KEY CLUSTERED (count_id),
    CONSTRAINT UQ_ctl_execution_count_phase UNIQUE (execution_id, count_phase),
    CONSTRAINT FK_ctl_execution_count_execution
        FOREIGN KEY (execution_id) REFERENCES ctl.execution_attempt (execution_id),
    CONSTRAINT CK_ctl_execution_count_phase_non_blank CHECK (
        LEN(LTRIM(RTRIM(count_phase))) > 0
        AND DATALENGTH(count_phase) = DATALENGTH(LTRIM(RTRIM(count_phase)))
    ),
    CONSTRAINT CK_ctl_execution_count_equation CHECK (
        physical_rows >= 0
        AND distinct_root_keys >= 0
        AND duplicate_rows >= 0
        AND valid_rows >= 0
        AND quarantined_root_keys >= 0
        AND unidentified_quarantine_rows >= 0
        AND physical_rows = distinct_root_keys + duplicate_rows + unidentified_quarantine_rows
        AND distinct_root_keys = valid_rows + quarantined_root_keys
    )
);
GO

CREATE TABLE ctl.source_watermark_observation (
    source_watermark_observation_id BIGINT IDENTITY(1, 1) NOT NULL,
    partition_id BIGINT NOT NULL,
    watermark_name NVARCHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL,
    observed_value_at_utc DATETIME2(3) NOT NULL,
    contract_fingerprint CHAR(64) NOT NULL,
    verified_for_incremental BIT NOT NULL,
    recorded_at_utc DATETIME2(3) NOT NULL,
    CONSTRAINT PK_ctl_source_watermark_observation PRIMARY KEY CLUSTERED (source_watermark_observation_id),
    CONSTRAINT FK_ctl_source_watermark_observation_partition
        FOREIGN KEY (partition_id) REFERENCES ctl.execution_partition (partition_id),
    CONSTRAINT CK_ctl_source_watermark_observation_name_non_blank
        CHECK (
            LEN(LTRIM(RTRIM(watermark_name))) > 0
            AND DATALENGTH(watermark_name) = DATALENGTH(LTRIM(RTRIM(watermark_name)))
        ),
    CONSTRAINT CK_ctl_source_watermark_observation_fingerprint
        CHECK (contract_fingerprint NOT LIKE '%[^0-9A-Fa-f]%')
);
GO

CREATE TABLE ctl.partition_publication_pointer (
    partition_id BIGINT NOT NULL,
    published_execution_id UNIQUEIDENTIFIER NOT NULL,
    published_at_utc DATETIME2(3) NOT NULL,
    CONSTRAINT PK_ctl_partition_publication_pointer PRIMARY KEY CLUSTERED (partition_id),
    CONSTRAINT UQ_ctl_partition_publication_pointer_execution UNIQUE (published_execution_id),
    CONSTRAINT FK_ctl_partition_publication_pointer_partition
        FOREIGN KEY (partition_id) REFERENCES ctl.execution_partition (partition_id),
    CONSTRAINT FK_ctl_partition_publication_pointer_execution
        FOREIGN KEY (published_execution_id) REFERENCES ctl.execution_attempt (execution_id),
    CONSTRAINT FK_ctl_partition_publication_pointer_partition_execution
        FOREIGN KEY (partition_id, published_execution_id)
        REFERENCES ctl.execution_attempt (partition_id, execution_id)
);
GO

CREATE TABLE ctl.execution_publication_event (
    execution_id UNIQUEIDENTIFIER NOT NULL,
    partition_id BIGINT NOT NULL,
    previous_published_execution_id UNIQUEIDENTIFIER NULL,
    published_at_utc DATETIME2(3) NOT NULL,
    incremental_frontier_before_utc DATETIME2(3) NULL,
    incremental_frontier_after_utc DATETIME2(3) NULL,
    watermark_last_partition_id BIGINT NULL,
    CONSTRAINT PK_ctl_execution_publication_event PRIMARY KEY CLUSTERED (execution_id),
    CONSTRAINT FK_ctl_execution_publication_event_execution
        FOREIGN KEY (execution_id) REFERENCES ctl.execution_attempt (execution_id),
    CONSTRAINT FK_ctl_execution_publication_event_partition_execution
        FOREIGN KEY (partition_id, execution_id)
        REFERENCES ctl.execution_attempt (partition_id, execution_id),
    CONSTRAINT FK_ctl_execution_publication_event_partition
        FOREIGN KEY (partition_id) REFERENCES ctl.execution_partition (partition_id),
    CONSTRAINT FK_ctl_execution_publication_event_previous_execution
        FOREIGN KEY (previous_published_execution_id)
        REFERENCES ctl.execution_attempt (execution_id),
    CONSTRAINT FK_ctl_execution_publication_event_watermark_partition
        FOREIGN KEY (watermark_last_partition_id)
        REFERENCES ctl.execution_partition (partition_id),
    CONSTRAINT CK_ctl_execution_publication_event_frontier CHECK (
        (
            incremental_frontier_before_utc IS NULL
            AND incremental_frontier_after_utc IS NULL
            AND watermark_last_partition_id IS NULL
        )
        OR (
            incremental_frontier_before_utc IS NOT NULL
            AND incremental_frontier_after_utc IS NOT NULL
            AND incremental_frontier_after_utc >= incremental_frontier_before_utc
            AND (
                incremental_frontier_after_utc = incremental_frontier_before_utc
                OR watermark_last_partition_id IS NOT NULL
            )
        )
    )
);
GO

CREATE TABLE ctl.incremental_publication_watermark (
    environment_name NVARCHAR(32) COLLATE Latin1_General_100_BIN2 NOT NULL,
    source_instance NVARCHAR(128) COLLATE Latin1_General_100_BIN2 NOT NULL,
    tenant_scope NVARCHAR(128) COLLATE Latin1_General_100_BIN2 NOT NULL,
    entity_name NVARCHAR(128) COLLATE Latin1_General_100_BIN2 NOT NULL,
    contiguous_partition_end_utc DATETIME2(3) NOT NULL,
    last_partition_id BIGINT NULL,
    registered_at_utc DATETIME2(3) NOT NULL,
    advanced_at_utc DATETIME2(3) NULL,
    CONSTRAINT PK_ctl_incremental_publication_watermark PRIMARY KEY CLUSTERED (
        environment_name, source_instance, tenant_scope, entity_name
    ),
    CONSTRAINT FK_ctl_incremental_publication_watermark_source
        FOREIGN KEY (source_instance) REFERENCES ctl.source_catalog (source_instance),
    CONSTRAINT FK_ctl_incremental_publication_watermark_last_partition
        FOREIGN KEY (last_partition_id) REFERENCES ctl.execution_partition (partition_id),
    CONSTRAINT CK_ctl_incremental_publication_watermark_non_blank CHECK (
        LEN(LTRIM(RTRIM(environment_name))) > 0
        AND LEN(LTRIM(RTRIM(source_instance))) > 0
        AND LEN(LTRIM(RTRIM(tenant_scope))) > 0
        AND LEN(LTRIM(RTRIM(entity_name))) > 0
        AND DATALENGTH(environment_name) = DATALENGTH(LTRIM(RTRIM(environment_name)))
        AND DATALENGTH(source_instance) = DATALENGTH(LTRIM(RTRIM(source_instance)))
        AND DATALENGTH(tenant_scope) = DATALENGTH(LTRIM(RTRIM(tenant_scope)))
        AND DATALENGTH(entity_name) = DATALENGTH(LTRIM(RTRIM(entity_name)))
    )
);
GO

CREATE OR ALTER PROCEDURE ctl.usp_control_plane_register_source
    @source_instance NVARCHAR(MAX),
    @source_kind NVARCHAR(MAX),
    @registered_at_utc DATETIME2(3)
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;

    IF DATALENGTH(@source_instance) > 256 OR DATALENGTH(@source_kind) > 128
        THROW 51300, N'Catálogo de fonte excede o limite textual.', 1;

    SET @source_instance = LTRIM(RTRIM(@source_instance));
    SET @source_kind = LTRIM(RTRIM(@source_kind));

    IF NULLIF(LTRIM(RTRIM(@source_instance)), N'') IS NULL
        OR NULLIF(LTRIM(RTRIM(@source_kind)), N'') IS NULL
        OR @registered_at_utc IS NULL
        THROW 51300, N'Catálogo de fonte inválido.', 1;

    BEGIN TRANSACTION;

    DECLARE @existing_source_kind NVARCHAR(64);
    SELECT @existing_source_kind = source_kind
    FROM ctl.source_catalog WITH (UPDLOCK, HOLDLOCK)
    WHERE source_instance = @source_instance;

    -- registered_at_utc is a technical persistence timestamp.  Capture it only
    -- after the catalog key has been locked; the caller value is compatibility metadata.
    DECLARE @database_now_utc DATETIME2(3) = SYSUTCDATETIME();

    IF @existing_source_kind IS NULL
    BEGIN
        INSERT INTO ctl.source_catalog (source_instance, source_kind, active, registered_at_utc)
        VALUES (@source_instance, @source_kind, 1, @database_now_utc);
    END
    ELSE IF @existing_source_kind COLLATE Latin1_General_100_BIN2
            <> @source_kind COLLATE Latin1_General_100_BIN2
    BEGIN
        THROW 51301, N'Instância de fonte já pertence a outro catálogo.', 1;
    END;

    COMMIT TRANSACTION;
END;
GO

CREATE OR ALTER PROCEDURE ctl.usp_control_plane_start_cycle
    @cycle_id UNIQUEIDENTIFIER,
    @plan_version NVARCHAR(MAX),
    @plan_fingerprint NVARCHAR(MAX),
    @planned_at_utc DATETIME2(3)
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;

    IF DATALENGTH(@plan_version) > 256 OR DATALENGTH(@plan_fingerprint) > 128
        THROW 51302, N'Ciclo de execução excede o limite textual.', 1;

    SET @plan_version = LTRIM(RTRIM(@plan_version));
    SET @plan_fingerprint = LOWER(@plan_fingerprint);

    IF @cycle_id IS NULL
        OR @planned_at_utc IS NULL
        OR NULLIF(LTRIM(RTRIM(@plan_version)), N'') IS NULL
        OR @plan_fingerprint IS NULL
        OR LEN(@plan_fingerprint) <> 64
        OR @plan_fingerprint COLLATE Latin1_General_100_BIN2 LIKE '%[^0-9A-Fa-f]%'
        THROW 51302, N'Ciclo de execução inválido.', 1;

    BEGIN TRANSACTION;

    DECLARE @existing_version NVARCHAR(128);
    DECLARE @existing_fingerprint CHAR(64);
    SELECT
        @existing_version = plan_version,
        @existing_fingerprint = plan_fingerprint
    FROM ctl.execution_cycle WITH (UPDLOCK, HOLDLOCK)
    WHERE cycle_id = @cycle_id;

    -- planned_at_utc is a technical persistence timestamp.  Capture it only
    -- after the cycle key has been locked; retries do not compare caller clocks.
    DECLARE @database_now_utc DATETIME2(3) = SYSUTCDATETIME();

    IF @existing_fingerprint IS NULL
    BEGIN
        INSERT INTO ctl.execution_cycle (cycle_id, plan_version, plan_fingerprint, planned_at_utc)
        VALUES (@cycle_id, @plan_version, LOWER(@plan_fingerprint), @database_now_utc);
    END
    ELSE IF @existing_version COLLATE Latin1_General_100_BIN2
            <> @plan_version COLLATE Latin1_General_100_BIN2
        OR @existing_fingerprint <> @plan_fingerprint
    BEGIN
        THROW 51303, N'O ciclo não pode ser regravado com outro plano.', 1;
    END;

    COMMIT TRANSACTION;
END;
GO

CREATE OR ALTER PROCEDURE ctl.usp_control_plane_start_execution
    @execution_id UNIQUEIDENTIFIER,
    @cycle_id UNIQUEIDENTIFIER,
    @environment_name NVARCHAR(MAX),
    @source_instance NVARCHAR(MAX),
    @tenant_scope NVARCHAR(MAX),
    @entity_name NVARCHAR(MAX),
    @execution_mode NVARCHAR(MAX),
    @partition_start_utc DATETIME2(3),
    @partition_end_exclusive_utc DATETIME2(3),
    @window_strategy NVARCHAR(MAX),
    @contract_version NVARCHAR(MAX),
    @contract_fingerprint NVARCHAR(MAX),
    @configuration_version NVARCHAR(MAX),
    @configuration_fingerprint NVARCHAR(MAX),
    @idempotency_key NVARCHAR(MAX),
    @replay_of_execution_id UNIQUEIDENTIFIER = NULL,
    @lease_seconds INT,
    @started_at_utc DATETIME2(3)
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;

    IF DATALENGTH(@environment_name) > 64
        OR DATALENGTH(@source_instance) > 256
        OR DATALENGTH(@tenant_scope) > 256
        OR DATALENGTH(@entity_name) > 256
        OR DATALENGTH(@execution_mode) > 32
        OR DATALENGTH(@window_strategy) > 128
        OR DATALENGTH(@contract_version) > 256
        OR DATALENGTH(@contract_fingerprint) > 128
        OR DATALENGTH(@configuration_version) > 256
        OR DATALENGTH(@configuration_fingerprint) > 128
        OR DATALENGTH(@idempotency_key) > 256
        THROW 51304, N'Início de execução excede o limite textual.', 1;

    SET @environment_name = LTRIM(RTRIM(@environment_name));
    SET @source_instance = LTRIM(RTRIM(@source_instance));
    SET @tenant_scope = LTRIM(RTRIM(@tenant_scope));
    SET @entity_name = LTRIM(RTRIM(@entity_name));
    SET @execution_mode = LTRIM(RTRIM(@execution_mode));
    SET @window_strategy = LTRIM(RTRIM(@window_strategy));
    SET @contract_version = LTRIM(RTRIM(@contract_version));
    SET @contract_fingerprint = LOWER(@contract_fingerprint);
    SET @configuration_version = LTRIM(RTRIM(@configuration_version));
    SET @configuration_fingerprint = LOWER(@configuration_fingerprint);
    SET @idempotency_key = LTRIM(RTRIM(@idempotency_key));

    DECLARE @database_now_utc DATETIME2(3) = SYSUTCDATETIME();

    IF @execution_id IS NULL
        OR @cycle_id IS NULL
        OR NULLIF(LTRIM(RTRIM(@environment_name)), N'') IS NULL
        OR NULLIF(LTRIM(RTRIM(@source_instance)), N'') IS NULL
        OR NULLIF(LTRIM(RTRIM(@tenant_scope)), N'') IS NULL
        OR NULLIF(LTRIM(RTRIM(@entity_name)), N'') IS NULL
        OR @execution_mode COLLATE Latin1_General_100_BIN2
            NOT IN (N'INCREMENTAL', N'BOOTSTRAP', N'BACKFILL', N'REPLAY', N'SWEEP')
        OR @partition_start_utc IS NULL
        OR @partition_end_exclusive_utc IS NULL
        OR @partition_start_utc >= @partition_end_exclusive_utc
        OR NULLIF(LTRIM(RTRIM(@window_strategy)), N'') IS NULL
        OR NULLIF(LTRIM(RTRIM(@contract_version)), N'') IS NULL
        OR @contract_fingerprint IS NULL
        OR LEN(@contract_fingerprint) <> 64
        OR @contract_fingerprint COLLATE Latin1_General_100_BIN2 LIKE '%[^0-9A-Fa-f]%'
        OR NULLIF(LTRIM(RTRIM(@configuration_version)), N'') IS NULL
        OR @configuration_fingerprint IS NULL
        OR LEN(@configuration_fingerprint) <> 64
        OR @configuration_fingerprint COLLATE Latin1_General_100_BIN2 LIKE '%[^0-9A-Fa-f]%'
        OR NULLIF(LTRIM(RTRIM(@idempotency_key)), N'') IS NULL
        OR @lease_seconds NOT BETWEEN 1 AND 86400
        OR @started_at_utc IS NULL
        OR (@execution_mode COLLATE Latin1_General_100_BIN2 = N'REPLAY'
            AND @replay_of_execution_id IS NULL)
        OR (@execution_mode COLLATE Latin1_General_100_BIN2 <> N'REPLAY'
            AND @replay_of_execution_id IS NOT NULL)
        OR @replay_of_execution_id = @execution_id
        THROW 51304, N'Início de execução inválido.', 1;

    BEGIN TRANSACTION;

    DECLARE @existing_execution_id UNIQUEIDENTIFIER;
    SELECT @existing_execution_id = execution_id
    FROM ctl.execution_attempt WITH (UPDLOCK, HOLDLOCK)
    WHERE execution_id = @execution_id;

    IF @existing_execution_id IS NOT NULL
    BEGIN
        IF NOT EXISTS (
            SELECT 1
            FROM ctl.execution_attempt AS attempt
            INNER JOIN ctl.execution_partition AS partition ON partition.partition_id = attempt.partition_id
            WHERE attempt.execution_id = @execution_id
              AND attempt.cycle_id = @cycle_id
              AND partition.environment_name = @environment_name
              AND partition.source_instance = @source_instance
              AND partition.tenant_scope = @tenant_scope
              AND partition.entity_name = @entity_name
              AND partition.execution_mode = @execution_mode
              AND partition.partition_start_utc = @partition_start_utc
              AND partition.partition_end_exclusive_utc = @partition_end_exclusive_utc
              AND attempt.window_strategy = @window_strategy
              AND attempt.contract_version = @contract_version
              AND attempt.contract_fingerprint = @contract_fingerprint
              AND attempt.configuration_version = @configuration_version
              AND attempt.configuration_fingerprint = @configuration_fingerprint
              AND attempt.idempotency_key = @idempotency_key
              AND (
                    attempt.replay_of_execution_id = @replay_of_execution_id
                    OR (attempt.replay_of_execution_id IS NULL AND @replay_of_execution_id IS NULL)
              )
        )
            THROW 51305, N'Execution_id já existe com ocorrência imutável divergente.', 1;

        COMMIT TRANSACTION;
        RETURN;
    END;

    SET @database_now_utc = SYSUTCDATETIME();
    IF ABS(DATEDIFF_BIG(SECOND, @started_at_utc, @database_now_utc)) > 300
        THROW 51304, N'Início de execução fora do limite de skew permitido.', 1;

    IF EXISTS (
        SELECT 1
        FROM ctl.execution_attempt WITH (UPDLOCK, HOLDLOCK)
        WHERE idempotency_key = @idempotency_key
    )
        THROW 51306, N'Chave de idempotência já pertence a outra ocorrência.', 1;

    IF NOT EXISTS (SELECT 1 FROM ctl.execution_cycle WHERE cycle_id = @cycle_id)
        THROW 51307, N'Ciclo de execução inexistente.', 1;

    IF NOT EXISTS (
        SELECT 1 FROM ctl.source_catalog
        WHERE source_instance = @source_instance AND active = 1
    )
        THROW 51308, N'Instância de fonte não está ativa no catálogo.', 1;

    -- REPLAY ocupa uma partição própria; a origem deve ser uma execução anterior não-REPLAY
    -- no mesmo namespace (ambiente/fonte/tenant/entidade) e na mesma janela canônica.
    IF @execution_mode COLLATE Latin1_General_100_BIN2 = N'REPLAY'
       AND NOT EXISTS (
            SELECT 1
            FROM ctl.execution_attempt AS replay_origin WITH (UPDLOCK, HOLDLOCK)
            INNER JOIN ctl.execution_partition AS origin_partition WITH (UPDLOCK, HOLDLOCK)
                ON origin_partition.partition_id = replay_origin.partition_id
            WHERE replay_origin.execution_id = @replay_of_execution_id
              AND origin_partition.environment_name = @environment_name
              AND origin_partition.source_instance = @source_instance
              AND origin_partition.tenant_scope = @tenant_scope
              AND origin_partition.entity_name = @entity_name
              AND origin_partition.execution_mode IN (
                  N'INCREMENTAL', N'BOOTSTRAP', N'BACKFILL', N'SWEEP'
              )
              AND origin_partition.partition_start_utc = @partition_start_utc
              AND origin_partition.partition_end_exclusive_utc = @partition_end_exclusive_utc
       )
        THROW 51333, N'A origem do replay não existe antes da nova tentativa ou diverge do namespace e janela.', 1;

    DECLARE @partition_id BIGINT;
    SELECT @partition_id = partition_id
    FROM ctl.execution_partition WITH (UPDLOCK, HOLDLOCK)
    WHERE environment_name = @environment_name
      AND source_instance = @source_instance
      AND tenant_scope = @tenant_scope
      AND entity_name = @entity_name
      AND execution_mode = @execution_mode
      AND partition_start_utc = @partition_start_utc
      AND partition_end_exclusive_utc = @partition_end_exclusive_utc;

    SET @database_now_utc = SYSUTCDATETIME();

    IF @partition_id IS NULL
    BEGIN
        INSERT INTO ctl.execution_partition (
            environment_name, source_instance, tenant_scope, entity_name, execution_mode,
            partition_start_utc, partition_end_exclusive_utc, created_at_utc
        ) VALUES (
            @environment_name, @source_instance, @tenant_scope, @entity_name, @execution_mode,
            @partition_start_utc, @partition_end_exclusive_utc, @database_now_utc
        );
        SET @partition_id = CONVERT(BIGINT, SCOPE_IDENTITY());
    END;

    DECLARE @existing_lease_expires_at_utc DATETIME2(3);
    SELECT @existing_lease_expires_at_utc = expires_at_utc
    FROM ctl.execution_lease WITH (UPDLOCK, HOLDLOCK)
    WHERE partition_id = @partition_id
      AND released_at_utc IS NULL;

    -- Capture o relógio depois de adquirir os locks usados para decidir a lease.
    SET @database_now_utc = SYSUTCDATETIME();

    IF @existing_lease_expires_at_utc > @database_now_utc
        THROW 51309, N'Já existe lease ativa para a partição semântica.', 1;

    IF @existing_lease_expires_at_utc <= @database_now_utc
        THROW 51310, N'Lease expirada exige recuperação determinística antes de nova execução.', 1;

    DECLARE @allocated_attempt TABLE (attempt_number INT NOT NULL);
    UPDATE ctl.execution_partition
    SET next_attempt_number = next_attempt_number + 1
    OUTPUT deleted.next_attempt_number INTO @allocated_attempt (attempt_number)
    WHERE partition_id = @partition_id;

    DECLARE @attempt_number INT;
    SELECT @attempt_number = attempt_number FROM @allocated_attempt;
    IF @attempt_number IS NULL
        THROW 51331, N'Não foi possível alocar attempt_number atomicamente.', 1;

    INSERT INTO ctl.execution_attempt (
        execution_id, partition_id, cycle_id, attempt_number, window_strategy,
        contract_version, contract_fingerprint, configuration_version, configuration_fingerprint,
        idempotency_key, replay_of_execution_id, current_state, next_transition_sequence,
        started_at_utc
    ) VALUES (
        @execution_id, @partition_id, @cycle_id, @attempt_number, @window_strategy,
        @contract_version, @contract_fingerprint, @configuration_version, @configuration_fingerprint,
        @idempotency_key, @replay_of_execution_id, N'EXTRACTING', 3, @database_now_utc
    );

    INSERT INTO ctl.execution_state_event (
        execution_id, transition_sequence, previous_state, next_state, reason_code, transitioned_at_utc
    ) VALUES
        (@execution_id, 1, NULL, N'PLANNED', N'EXECUTION_PLANNED', @database_now_utc),
        (@execution_id, 2, N'PLANNED', N'EXTRACTING', N'LEASE_ACQUIRED', @database_now_utc);

    INSERT INTO ctl.execution_lease (
        lease_id, partition_id, execution_id, acquired_at_utc, heartbeat_at_utc, expires_at_utc
    ) VALUES (
        NEWID(), @partition_id, @execution_id, @database_now_utc, @database_now_utc,
        DATEADD(SECOND, @lease_seconds, @database_now_utc)
    );

    UPDATE ctl.execution_partition
    SET current_execution_id = @execution_id,
        current_state = N'EXTRACTING'
    WHERE partition_id = @partition_id;

    COMMIT TRANSACTION;
END;
GO

CREATE OR ALTER PROCEDURE ctl.usp_control_plane_heartbeat_lease
    @execution_id UNIQUEIDENTIFIER,
    @heartbeat_at_utc DATETIME2(3),
    @lease_seconds INT,
    @expires_at_utc DATETIME2(3)
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;

    IF @execution_id IS NULL
        OR @heartbeat_at_utc IS NULL
        OR @lease_seconds NOT BETWEEN 1 AND 86400
        OR @expires_at_utc <> DATEADD(SECOND, @lease_seconds, @heartbeat_at_utc)
        THROW 51311, N'Heartbeat de lease inválido.', 1;

    BEGIN TRANSACTION;

    DECLARE @partition_id BIGINT;
    DECLARE @lease_expires_at_utc DATETIME2(3);
    DECLARE @lease_released_at_utc DATETIME2(3);
    SELECT
        @partition_id = attempt.partition_id,
        @lease_expires_at_utc = lease.expires_at_utc,
        @lease_released_at_utc = lease.released_at_utc
    FROM ctl.execution_attempt AS attempt WITH (UPDLOCK, HOLDLOCK)
    INNER JOIN ctl.execution_partition AS partition WITH (UPDLOCK, HOLDLOCK)
        ON partition.partition_id = attempt.partition_id
       AND partition.current_execution_id = attempt.execution_id
    INNER JOIN ctl.execution_lease AS lease WITH (UPDLOCK, HOLDLOCK)
        ON lease.partition_id = partition.partition_id
       AND lease.execution_id = attempt.execution_id
    WHERE attempt.execution_id = @execution_id
      AND attempt.current_state IN (N'EXTRACTING', N'EXTRACTED', N'STAGED', N'PROMOTED');

    DECLARE @database_now_utc DATETIME2(3) = SYSUTCDATETIME();

    IF @partition_id IS NULL
        OR @lease_released_at_utc IS NOT NULL
        OR @lease_expires_at_utc <= @database_now_utc
        THROW 51312, N'Lease corrente e ativa não encontrada para heartbeat.', 1;

    UPDATE ctl.execution_lease
    SET heartbeat_at_utc = @database_now_utc,
        expires_at_utc = DATEADD(SECOND, @lease_seconds, @database_now_utc)
    WHERE partition_id = @partition_id
      AND execution_id = @execution_id
      AND released_at_utc IS NULL;

    IF @@ROWCOUNT <> 1
        THROW 51312, N'Lease ativa não encontrada para heartbeat.', 1;

    COMMIT TRANSACTION;
END;
GO

CREATE OR ALTER PROCEDURE ctl.usp_control_plane_record_page
    @execution_id UNIQUEIDENTIFIER,
    @page_number INT,
    @page_attempt INT,
    @requested_page_size INT,
    @physical_rows BIGINT,
    @distinct_root_keys BIGINT,
    @response_bytes BIGINT,
    @terminal_empty_page BIT,
    @read_at_utc DATETIME2(3),
    @terminal_evidence_kind NVARCHAR(MAX) = NULL
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;

    IF DATALENGTH(@terminal_evidence_kind) > 64
        THROW 51313, N'Auditoria de página inválida.', 1;

    SET @terminal_evidence_kind = NULLIF(LTRIM(RTRIM(@terminal_evidence_kind)), N'');
    SET @terminal_evidence_kind = COALESCE(
        @terminal_evidence_kind,
        CASE WHEN @terminal_empty_page = 1
             THEN N'DATA_EXPORT_EMPTY_PAGE' ELSE N'NONE' END
    );

    IF @execution_id IS NULL
        OR @page_number IS NULL
        OR @page_number < 1
        OR @page_attempt IS NULL
        OR @page_attempt < 1
        OR @requested_page_size IS NULL
        OR @requested_page_size < 1
        OR @physical_rows IS NULL
        OR @physical_rows < 0
        OR @distinct_root_keys IS NULL
        OR @distinct_root_keys < 0
        OR @distinct_root_keys > @physical_rows
        OR @response_bytes IS NULL
        OR @response_bytes < 0
        OR @terminal_empty_page IS NULL
        OR @terminal_evidence_kind COLLATE Latin1_General_100_BIN2 NOT IN (
            N'NONE', N'DATA_EXPORT_EMPTY_PAGE', N'GRAPHQL_PAGE_INFO'
        )
        OR (
            @terminal_evidence_kind COLLATE Latin1_General_100_BIN2 = N'NONE'
            AND @terminal_empty_page <> 0
        )
        OR (
            @terminal_evidence_kind COLLATE Latin1_General_100_BIN2 = N'DATA_EXPORT_EMPTY_PAGE'
            AND (@terminal_empty_page <> 1 OR @physical_rows <> 0)
        )
        OR (
            @terminal_evidence_kind COLLATE Latin1_General_100_BIN2 = N'GRAPHQL_PAGE_INFO'
            AND (@terminal_empty_page <> 0 OR @physical_rows = 0)
        )
        OR @read_at_utc IS NULL
        THROW 51313, N'Auditoria de página inválida.', 1;

    BEGIN TRANSACTION;

    DECLARE @partition_id BIGINT;
    DECLARE @execution_state NVARCHAR(32);
    SELECT
        @partition_id = partition_id,
        @execution_state = current_state
    FROM ctl.execution_attempt WITH (UPDLOCK, HOLDLOCK)
    WHERE execution_id = @execution_id;

    IF @partition_id IS NULL
        THROW 51314, N'Execução inexistente para auditoria de página.', 1;

    DECLARE @existing_page_audit_id BIGINT;
    DECLARE @existing_requested_page_size INT;
    DECLARE @existing_physical_rows BIGINT;
    DECLARE @existing_distinct_root_keys BIGINT;
    DECLARE @existing_response_bytes BIGINT;
    DECLARE @existing_terminal_empty_page BIT;
    DECLARE @existing_terminal_evidence_kind NVARCHAR(32);
    SELECT
        @existing_page_audit_id = page_audit_id,
        @existing_requested_page_size = requested_page_size,
        @existing_physical_rows = physical_rows,
        @existing_distinct_root_keys = distinct_root_keys,
        @existing_response_bytes = response_bytes,
        @existing_terminal_empty_page = terminal_empty_page,
        @existing_terminal_evidence_kind = terminal_evidence_kind
    FROM ctl.execution_page_audit WITH (UPDLOCK, HOLDLOCK)
    WHERE execution_id = @execution_id
      AND page_number = @page_number
      AND page_attempt = @page_attempt;

    IF @existing_page_audit_id IS NOT NULL
    BEGIN
        IF @existing_requested_page_size <> @requested_page_size
            OR @existing_physical_rows <> @physical_rows
            OR @existing_distinct_root_keys <> @distinct_root_keys
            OR @existing_response_bytes <> @response_bytes
            OR @existing_terminal_empty_page <> @terminal_empty_page
            OR @existing_terminal_evidence_kind COLLATE Latin1_General_100_BIN2
                <> @terminal_evidence_kind COLLATE Latin1_General_100_BIN2
            THROW 51334, N'Auditoria de página já existe com conteúdo imutável divergente.', 1;

        COMMIT TRANSACTION;
        RETURN;
    END;

    DECLARE @lease_expires_at_utc DATETIME2(3);
    DECLARE @lease_released_at_utc DATETIME2(3);
    SELECT
        @lease_expires_at_utc = lease.expires_at_utc,
        @lease_released_at_utc = lease.released_at_utc
    FROM ctl.execution_partition AS partition WITH (UPDLOCK, HOLDLOCK)
        INNER JOIN ctl.execution_lease AS lease WITH (UPDLOCK, HOLDLOCK)
            ON lease.partition_id = partition.partition_id
           AND lease.execution_id = @execution_id
    WHERE partition.partition_id = @partition_id
      AND partition.current_execution_id = @execution_id;

    DECLARE @database_now_utc DATETIME2(3) = SYSUTCDATETIME();

    IF @execution_state COLLATE Latin1_General_100_BIN2 <> N'EXTRACTING'
        OR @lease_expires_at_utc IS NULL
        OR @lease_released_at_utc IS NOT NULL
        OR @lease_expires_at_utc <= @database_now_utc
        THROW 51314, N'Auditoria de página exige execução corrente, EXTRACTING e com lease ativa.', 1;

    INSERT INTO ctl.execution_page_audit (
        execution_id, page_number, page_attempt, requested_page_size, physical_rows,
        distinct_root_keys, response_bytes, terminal_empty_page, terminal_evidence_kind,
        read_at_utc
    ) VALUES (
        @execution_id, @page_number, @page_attempt, @requested_page_size, @physical_rows,
        @distinct_root_keys, @response_bytes, @terminal_empty_page, @terminal_evidence_kind,
        @database_now_utc
    );

    COMMIT TRANSACTION;
END;
GO

CREATE OR ALTER PROCEDURE ctl.usp_control_plane_record_counts
    @execution_id UNIQUEIDENTIFIER,
    @count_phase NVARCHAR(MAX),
    @physical_rows BIGINT,
    @distinct_root_keys BIGINT,
    @duplicate_rows BIGINT,
    @valid_rows BIGINT,
    @quarantined_root_keys BIGINT,
    @recorded_at_utc DATETIME2(3),
    @unidentified_quarantine_rows BIGINT = 0
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;

    IF DATALENGTH(@count_phase) > 128
        THROW 51315, N'Fase da contagem excede o limite textual.', 1;

    SET @count_phase = LTRIM(RTRIM(@count_phase));

    IF @execution_id IS NULL
        OR NULLIF(LTRIM(RTRIM(@count_phase)), N'') IS NULL
        OR @count_phase COLLATE Latin1_General_100_BIN2 = N'STAGING_KERNEL'
        OR @physical_rows IS NULL
        OR @physical_rows < 0
        OR @distinct_root_keys IS NULL
        OR @distinct_root_keys < 0
        OR @duplicate_rows IS NULL
        OR @duplicate_rows < 0
        OR @valid_rows IS NULL
        OR @valid_rows < 0
        OR @quarantined_root_keys IS NULL
        OR @quarantined_root_keys < 0
        OR @unidentified_quarantine_rows IS NULL
        OR @unidentified_quarantine_rows < 0
        OR @physical_rows <> @distinct_root_keys + @duplicate_rows + @unidentified_quarantine_rows
        OR @distinct_root_keys <> @valid_rows + @quarantined_root_keys
        OR @recorded_at_utc IS NULL
        THROW 51315, N'Equação de contagens inválida.', 1;

    BEGIN TRANSACTION;

    DECLARE @partition_id BIGINT;
    DECLARE @execution_state NVARCHAR(32);
    SELECT
        @partition_id = partition_id,
        @execution_state = current_state
    FROM ctl.execution_attempt WITH (UPDLOCK, HOLDLOCK)
    WHERE execution_id = @execution_id;

    IF @partition_id IS NULL
        THROW 51316, N'Execução inexistente para contagens.', 1;

    DECLARE @existing_count_id BIGINT;
    DECLARE @existing_physical_rows BIGINT;
    DECLARE @existing_distinct_root_keys BIGINT;
    DECLARE @existing_duplicate_rows BIGINT;
    DECLARE @existing_valid_rows BIGINT;
    DECLARE @existing_quarantined_root_keys BIGINT;
    DECLARE @existing_unidentified_quarantine_rows BIGINT;
    SELECT
        @existing_count_id = count_id,
        @existing_physical_rows = physical_rows,
        @existing_distinct_root_keys = distinct_root_keys,
        @existing_duplicate_rows = duplicate_rows,
        @existing_valid_rows = valid_rows,
        @existing_quarantined_root_keys = quarantined_root_keys,
        @existing_unidentified_quarantine_rows = unidentified_quarantine_rows
    FROM ctl.execution_count WITH (UPDLOCK, HOLDLOCK)
    WHERE execution_id = @execution_id
      AND count_phase = @count_phase;

    IF @existing_count_id IS NOT NULL
    BEGIN
        IF @existing_physical_rows <> @physical_rows
            OR @existing_distinct_root_keys <> @distinct_root_keys
            OR @existing_duplicate_rows <> @duplicate_rows
            OR @existing_valid_rows <> @valid_rows
            OR @existing_quarantined_root_keys <> @quarantined_root_keys
            OR @existing_unidentified_quarantine_rows <> @unidentified_quarantine_rows
            THROW 51335, N'Contagem já existe com conteúdo imutável divergente.', 1;

        COMMIT TRANSACTION;
        RETURN;
    END;

    DECLARE @lease_expires_at_utc DATETIME2(3);
    DECLARE @lease_released_at_utc DATETIME2(3);
    SELECT
        @lease_expires_at_utc = lease.expires_at_utc,
        @lease_released_at_utc = lease.released_at_utc
    FROM ctl.execution_partition AS partition WITH (UPDLOCK, HOLDLOCK)
        INNER JOIN ctl.execution_lease AS lease WITH (UPDLOCK, HOLDLOCK)
            ON lease.partition_id = partition.partition_id
           AND lease.execution_id = @execution_id
    WHERE partition.partition_id = @partition_id
      AND partition.current_execution_id = @execution_id;

    DECLARE @database_now_utc DATETIME2(3) = SYSUTCDATETIME();

    IF @execution_state COLLATE Latin1_General_100_BIN2
            NOT IN (N'EXTRACTING', N'EXTRACTED', N'STAGED', N'PROMOTED')
        OR @lease_expires_at_utc IS NULL
        OR @lease_released_at_utc IS NOT NULL
        OR @lease_expires_at_utc <= @database_now_utc
        THROW 51316, N'Contagens exigem execução corrente, não terminal e com lease ativa.', 1;

    INSERT INTO ctl.execution_count (
        execution_id, count_phase, physical_rows, distinct_root_keys, duplicate_rows,
        valid_rows, quarantined_root_keys, unidentified_quarantine_rows, recorded_at_utc
    ) VALUES (
        @execution_id, @count_phase, @physical_rows, @distinct_root_keys, @duplicate_rows,
        @valid_rows, @quarantined_root_keys, @unidentified_quarantine_rows, @database_now_utc
    );

    COMMIT TRANSACTION;
END;
GO

CREATE OR ALTER PROCEDURE ctl.usp_control_plane_transition_execution
    @execution_id UNIQUEIDENTIFIER,
    @expected_current_state NVARCHAR(MAX),
    @next_state NVARCHAR(MAX),
    @reason_code NVARCHAR(MAX),
    @transitioned_at_utc DATETIME2(3)
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;

    IF DATALENGTH(@expected_current_state) > 64
        OR DATALENGTH(@next_state) > 64
        OR DATALENGTH(@reason_code) > 128
        THROW 51317, N'Transição excede o limite textual.', 1;

    SET @expected_current_state = LTRIM(RTRIM(@expected_current_state));
    SET @next_state = LTRIM(RTRIM(@next_state));
    SET @reason_code = LTRIM(RTRIM(@reason_code));

    IF @execution_id IS NULL
        OR @expected_current_state IS NULL
        OR @next_state IS NULL
        OR NULLIF(LTRIM(RTRIM(@reason_code)), N'') IS NULL
        OR LEFT(@reason_code, 1) COLLATE Latin1_General_100_BIN2 NOT LIKE '[A-Z]'
        OR @reason_code COLLATE Latin1_General_100_BIN2 LIKE '%[^A-Z0-9_]%'
        OR LEN(@reason_code) NOT BETWEEN 2 AND 64
        OR @transitioned_at_utc IS NULL
        OR @next_state COLLATE Latin1_General_100_BIN2
            IN (N'PROMOTED', N'RECONCILED', N'PUBLISHED')
        THROW 51317, N'Transição de execução inválida.', 1;

    BEGIN TRANSACTION;

    DECLARE @partition_id BIGINT;
    DECLARE @current_state NVARCHAR(32);
    DECLARE @next_transition_sequence INT;
    SELECT
        @partition_id = partition_id,
        @current_state = current_state,
        @next_transition_sequence = next_transition_sequence
    FROM ctl.execution_attempt WITH (UPDLOCK, HOLDLOCK)
    WHERE execution_id = @execution_id;

    IF @partition_id IS NULL
        THROW 51318, N'Execução inexistente para transição.', 1;

    DECLARE @event_sequence_to_lock INT = CASE
        WHEN @current_state COLLATE Latin1_General_100_BIN2
                = @next_state COLLATE Latin1_General_100_BIN2
            THEN @next_transition_sequence - 1
        ELSE @next_transition_sequence
    END;
    DECLARE @existing_event_id BIGINT;
    DECLARE @existing_event_previous_state NVARCHAR(32);
    DECLARE @existing_event_next_state NVARCHAR(32);
    DECLARE @existing_event_reason_code NVARCHAR(64);
    SELECT
        @existing_event_id = state_event_id,
        @existing_event_previous_state = previous_state,
        @existing_event_next_state = next_state,
        @existing_event_reason_code = reason_code
    FROM ctl.execution_state_event WITH (UPDLOCK, HOLDLOCK)
    WHERE execution_id = @execution_id
      AND transition_sequence = @event_sequence_to_lock;

    IF @current_state COLLATE Latin1_General_100_BIN2
            = @next_state COLLATE Latin1_General_100_BIN2
    BEGIN
        IF @existing_event_id IS NOT NULL
            AND @existing_event_previous_state COLLATE Latin1_General_100_BIN2
                = @expected_current_state COLLATE Latin1_General_100_BIN2
            AND @existing_event_next_state COLLATE Latin1_General_100_BIN2
                = @next_state COLLATE Latin1_General_100_BIN2
            AND @existing_event_reason_code COLLATE Latin1_General_100_BIN2
                = @reason_code COLLATE Latin1_General_100_BIN2
        BEGIN
            COMMIT TRANSACTION;
            RETURN;
        END;

        THROW 51319, N'Estado atual já foi alcançado por outra transição imutável.', 1;
    END;

    IF @existing_event_id IS NOT NULL
        THROW 51319, N'O slot imutável da próxima transição já está ocupado.', 1;

    IF @current_state COLLATE Latin1_General_100_BIN2
            <> @expected_current_state COLLATE Latin1_General_100_BIN2
        THROW 51319, N'Estado atual diverge da transição esperada.', 1;

    DECLARE @lease_expires_at_utc DATETIME2(3);
    DECLARE @lease_released_at_utc DATETIME2(3);
    SELECT
        @lease_expires_at_utc = lease.expires_at_utc,
        @lease_released_at_utc = lease.released_at_utc
    FROM ctl.execution_partition AS partition WITH (UPDLOCK, HOLDLOCK)
        INNER JOIN ctl.execution_lease AS lease WITH (UPDLOCK, HOLDLOCK)
            ON lease.partition_id = partition.partition_id
           AND lease.execution_id = @execution_id
    WHERE partition.partition_id = @partition_id
      AND partition.current_execution_id = @execution_id;

    DECLARE @database_now_utc DATETIME2(3) = SYSUTCDATETIME();

    IF @lease_expires_at_utc IS NULL
        OR @lease_released_at_utc IS NOT NULL
        OR @lease_expires_at_utc <= @database_now_utc
        THROW 51330, N'Execução sem lease corrente e ativa para transição.', 1;

    IF NOT (
        (@current_state COLLATE Latin1_General_100_BIN2 = N'PLANNED'
            AND @next_state COLLATE Latin1_General_100_BIN2
                IN (N'EXTRACTING', N'BLOCKED', N'SKIPPED', N'NOT_APPLICABLE', N'FAILED', N'CANCELLED'))
        OR (@current_state COLLATE Latin1_General_100_BIN2 = N'EXTRACTING'
            AND @next_state COLLATE Latin1_General_100_BIN2
                IN (N'EXTRACTED', N'BLOCKED', N'FAILED', N'CANCELLED', N'DEGRADED'))
        OR (@current_state COLLATE Latin1_General_100_BIN2 = N'EXTRACTED'
            AND @next_state COLLATE Latin1_General_100_BIN2
                IN (N'STAGED', N'BLOCKED', N'FAILED', N'CANCELLED', N'DEGRADED'))
        OR (@current_state COLLATE Latin1_General_100_BIN2 = N'STAGED'
            AND @next_state COLLATE Latin1_General_100_BIN2
                IN (N'BLOCKED', N'FAILED', N'CANCELLED', N'DEGRADED'))
        OR (@current_state COLLATE Latin1_General_100_BIN2 = N'PROMOTED'
            AND @next_state COLLATE Latin1_General_100_BIN2
                IN (N'BLOCKED', N'FAILED', N'CANCELLED', N'DEGRADED'))
        OR (@current_state COLLATE Latin1_General_100_BIN2 = N'RECONCILED'
            AND @next_state COLLATE Latin1_General_100_BIN2
                IN (N'BLOCKED', N'FAILED', N'CANCELLED', N'DEGRADED'))
    )
        THROW 51320, N'Transição de estado não permitida.', 1;

    DECLARE @allocated_transition TABLE (transition_sequence INT NOT NULL);
    UPDATE ctl.execution_attempt
    SET current_state = @next_state,
        terminal_at_utc = CASE WHEN @next_state COLLATE Latin1_General_100_BIN2
                IN (N'BLOCKED', N'SKIPPED', N'NOT_APPLICABLE', N'FAILED', N'CANCELLED', N'DEGRADED')
                               THEN @database_now_utc ELSE NULL END,
        next_transition_sequence = next_transition_sequence + 1
    OUTPUT deleted.next_transition_sequence
        INTO @allocated_transition (transition_sequence)
    WHERE execution_id = @execution_id
      AND current_state = @current_state;

    DECLARE @sequence INT;
    SELECT @sequence = transition_sequence FROM @allocated_transition;
    IF @sequence IS NULL
        THROW 51332, N'Não foi possível alocar transition_sequence atomicamente.', 1;

    INSERT INTO ctl.execution_state_event (
        execution_id, transition_sequence, previous_state, next_state, reason_code, transitioned_at_utc
    ) VALUES (
        @execution_id, @sequence, @current_state, @next_state, @reason_code, @database_now_utc
    );

    UPDATE ctl.execution_partition
    SET current_execution_id = @execution_id,
        current_state = @next_state
    WHERE partition_id = @partition_id;

    IF @next_state COLLATE Latin1_General_100_BIN2
            IN (N'BLOCKED', N'SKIPPED', N'NOT_APPLICABLE', N'FAILED', N'CANCELLED', N'DEGRADED')
    BEGIN
        UPDATE ctl.execution_lease
        SET released_at_utc = @database_now_utc
        WHERE execution_id = @execution_id AND released_at_utc IS NULL;
    END;

    COMMIT TRANSACTION;
END;
GO

CREATE OR ALTER PROCEDURE ctl.usp_control_plane_register_incremental_frontier
    @environment_name NVARCHAR(MAX),
    @source_instance NVARCHAR(MAX),
    @tenant_scope NVARCHAR(MAX),
    @entity_name NVARCHAR(MAX),
    @initial_contiguous_end_utc DATETIME2(3),
    @registered_at_utc DATETIME2(3)
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;

    IF DATALENGTH(@environment_name) > 64
        OR DATALENGTH(@source_instance) > 256
        OR DATALENGTH(@tenant_scope) > 256
        OR DATALENGTH(@entity_name) > 256
        THROW 51321, N'Fronteira incremental excede o limite textual.', 1;

    SET @environment_name = LTRIM(RTRIM(@environment_name));
    SET @source_instance = LTRIM(RTRIM(@source_instance));
    SET @tenant_scope = LTRIM(RTRIM(@tenant_scope));
    SET @entity_name = LTRIM(RTRIM(@entity_name));

    IF NULLIF(LTRIM(RTRIM(@environment_name)), N'') IS NULL
        OR NULLIF(LTRIM(RTRIM(@source_instance)), N'') IS NULL
        OR NULLIF(LTRIM(RTRIM(@tenant_scope)), N'') IS NULL
        OR NULLIF(LTRIM(RTRIM(@entity_name)), N'') IS NULL
        OR @initial_contiguous_end_utc IS NULL
        OR @registered_at_utc IS NULL
        THROW 51321, N'Fronteira incremental inválida.', 1;

    BEGIN TRANSACTION;

    IF NOT EXISTS (
        SELECT 1 FROM ctl.source_catalog WHERE source_instance = @source_instance AND active = 1
    )
        THROW 51322, N'Fonte inativa não pode ter watermark operacional.', 1;

    DECLARE @existing_end DATETIME2(3);
    SELECT @existing_end = contiguous_partition_end_utc
    FROM ctl.incremental_publication_watermark WITH (UPDLOCK, HOLDLOCK)
    WHERE environment_name = @environment_name
      AND source_instance = @source_instance
      AND tenant_scope = @tenant_scope
      AND entity_name = @entity_name;

    DECLARE @database_now_utc DATETIME2(3) = SYSUTCDATETIME();

    IF @existing_end IS NULL
    BEGIN
        INSERT INTO ctl.incremental_publication_watermark (
            environment_name, source_instance, tenant_scope, entity_name,
            contiguous_partition_end_utc, registered_at_utc
        ) VALUES (
            @environment_name, @source_instance, @tenant_scope, @entity_name,
            @initial_contiguous_end_utc, @database_now_utc
        );
    END
    ELSE IF @existing_end <> @initial_contiguous_end_utc
    BEGIN
        THROW 51323, N'Fronteira incremental já existe e é imutável.', 1;
    END;

    COMMIT TRANSACTION;
END;
GO

CREATE OR ALTER PROCEDURE ctl.usp_control_plane_publish_execution
    @execution_id UNIQUEIDENTIFIER,
    @published_at_utc DATETIME2(3)
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;

    IF @execution_id IS NULL OR @published_at_utc IS NULL
        THROW 51324, N'Publicação de execução inválida.', 1;

    -- Contenção deliberada: V003 não possui evidência de reconciliação nem unidade atômica que
    -- aplique o candidate set em core junto com pointer/watermark. A publicação positiva será
    -- criada somente pela migration dona desse protocolo; este entry point permanece fail-closed.
    THROW 51326, N'Publicação indisponível sem evidência de reconciliação e commit atômico.', 1;
END;
GO

CREATE OR ALTER PROCEDURE ctl.usp_control_plane_recover_stale_executions
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;

    BEGIN TRANSACTION;

    DECLARE @stale TABLE (
        execution_id UNIQUEIDENTIFIER NOT NULL PRIMARY KEY,
        partition_id BIGINT NOT NULL,
        previous_state NVARCHAR(32) COLLATE Latin1_General_100_BIN2 NOT NULL,
        transition_sequence INT NOT NULL
    );

    -- O relógio SQL é amostrado somente depois que a operação entrou na transação. O caller não
    -- fornece cutoff, terminal_at nem release_at e, portanto, não pode antecipar a expiração.
    DECLARE @database_now_utc DATETIME2(3) = SYSUTCDATETIME();

    UPDATE attempt
    SET current_state = N'FAILED',
        terminal_at_utc = @database_now_utc,
        next_transition_sequence = next_transition_sequence + 1
    OUTPUT
        inserted.execution_id,
        inserted.partition_id,
        deleted.current_state,
        deleted.next_transition_sequence
    INTO @stale (execution_id, partition_id, previous_state, transition_sequence)
    FROM ctl.execution_attempt AS attempt WITH (UPDLOCK, HOLDLOCK)
    INNER JOIN ctl.execution_lease AS lease WITH (UPDLOCK, HOLDLOCK)
        ON lease.execution_id = attempt.execution_id
    INNER JOIN ctl.execution_partition AS partition WITH (UPDLOCK, HOLDLOCK)
        ON partition.partition_id = attempt.partition_id
       AND partition.current_execution_id = attempt.execution_id
    WHERE lease.released_at_utc IS NULL
      AND lease.expires_at_utc <= @database_now_utc
      AND attempt.current_state IN (N'EXTRACTING', N'EXTRACTED', N'STAGED', N'PROMOTED', N'RECONCILED');

    INSERT INTO ctl.execution_state_event (
        execution_id, transition_sequence, previous_state, next_state, reason_code, transitioned_at_utc
    )
    SELECT
        stale.execution_id,
        stale.transition_sequence,
        stale.previous_state,
        N'FAILED',
        N'STALE_LEASE_RECOVERED',
        @database_now_utc
    FROM @stale AS stale;

    UPDATE partition
    SET current_state = N'FAILED'
    FROM ctl.execution_partition AS partition
    INNER JOIN @stale AS stale ON stale.partition_id = partition.partition_id
    WHERE partition.current_execution_id = stale.execution_id;

    UPDATE lease
    SET released_at_utc = @database_now_utc
    FROM ctl.execution_lease AS lease
    INNER JOIN @stale AS stale ON stale.execution_id = lease.execution_id
    WHERE lease.released_at_utc IS NULL;

    DECLARE @recovered_executions BIGINT = (SELECT COUNT_BIG(*) FROM @stale);

    COMMIT TRANSACTION;

    SELECT @recovered_executions AS recovered_executions;
END;
GO

-- Runtime só invoca o protocolo. Não recebe DML direto nas tabelas append-only de ctl.
EXEC dbo.usp_publish_v2_procedure_grant N'ctl', N'usp_control_plane_register_source', N'v2_runtime';
EXEC dbo.usp_publish_v2_procedure_grant N'ctl', N'usp_control_plane_start_cycle', N'v2_runtime';
EXEC dbo.usp_publish_v2_procedure_grant N'ctl', N'usp_control_plane_start_execution', N'v2_runtime';
EXEC dbo.usp_publish_v2_procedure_grant N'ctl', N'usp_control_plane_heartbeat_lease', N'v2_runtime';
EXEC dbo.usp_publish_v2_procedure_grant N'ctl', N'usp_control_plane_record_page', N'v2_runtime';
EXEC dbo.usp_publish_v2_procedure_grant N'ctl', N'usp_control_plane_record_counts', N'v2_runtime';
EXEC dbo.usp_publish_v2_procedure_grant N'ctl', N'usp_control_plane_transition_execution', N'v2_runtime';
EXEC dbo.usp_publish_v2_procedure_grant N'ctl', N'usp_control_plane_recover_stale_executions', N'v2_runtime';
DENY INSERT, UPDATE, DELETE, SELECT ON SCHEMA::ctl TO v2_runtime;
