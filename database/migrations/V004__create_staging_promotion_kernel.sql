-- V2-021: kernel compartilhado de staging, quarantine, aplicação e publicação set-based.
-- Não cria entidade de domínio, payload bruto, logins, usuários, credenciais ou jobs.
-- A preparação fecha um candidate set imutável. Somente o protocolo final aplica esse conjunto ao
-- estado técnico de core e confirma reconciliação/publicação na mesma transação recuperável.

SET XACT_ABORT ON;
SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;

CREATE TABLE stg.execution_record (
    stage_record_id BIGINT IDENTITY(1, 1) NOT NULL,
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
    CONSTRAINT PK_stg_execution_record PRIMARY KEY CLUSTERED (stage_record_id),
    CONSTRAINT UQ_stg_execution_record_input UNIQUE (
        execution_id, input_batch_number, input_record_ordinal
    ),
    CONSTRAINT FK_stg_execution_record_execution
        FOREIGN KEY (execution_id) REFERENCES ctl.execution_attempt (execution_id),
    CONSTRAINT CK_stg_execution_record_batch_limits
        CHECK (input_batch_number > 0 AND input_record_ordinal BETWEEN 1 AND 10000),
    CONSTRAINT CK_stg_execution_record_disposition
        CHECK (
            validation_disposition IN (N'VALID', N'QUARANTINE')
            AND DATALENGTH(validation_disposition)
                = DATALENGTH(LTRIM(RTRIM(validation_disposition)))
        ),
    CONSTRAINT CK_stg_execution_record_fingerprints
        CHECK (
            (
                source_key IS NULL
                OR (
                    NULLIF(LTRIM(RTRIM(source_key)), N'') IS NOT NULL
                    AND DATALENGTH(source_key) = DATALENGTH(LTRIM(RTRIM(source_key)))
                )
            )
            AND
            (
                (
                    source_row_hash IS NULL
                    AND row_fingerprint_version IS NULL
                )
                OR (
                    source_row_hash IS NOT NULL
                    AND LEN(source_row_hash) = 64
                    AND source_row_hash NOT LIKE '%[^0-9A-Fa-f]%'
                    AND NULLIF(LTRIM(RTRIM(row_fingerprint_version)), N'') IS NOT NULL
                    AND DATALENGTH(row_fingerprint_version)
                        = DATALENGTH(LTRIM(RTRIM(row_fingerprint_version)))
                )
            )
            AND (
            (
                presence_fingerprint IS NULL
                AND presence_fingerprint_version IS NULL
            )
            OR (
                presence_fingerprint IS NOT NULL
                AND LEN(presence_fingerprint) = 64
                AND presence_fingerprint NOT LIKE '%[^0-9A-Fa-f]%'
                AND NULLIF(LTRIM(RTRIM(presence_fingerprint_version)), N'') IS NOT NULL
                AND DATALENGTH(presence_fingerprint_version)
                    = DATALENGTH(LTRIM(RTRIM(presence_fingerprint_version)))
            )
            )
        ),
    CONSTRAINT CK_stg_execution_record_validity
        CHECK (
            (
                validation_disposition = N'VALID'
                AND NULLIF(LTRIM(RTRIM(source_key)), N'') IS NOT NULL
                AND row_fingerprint_version IS NOT NULL
                AND source_row_hash IS NOT NULL
                AND presence_fingerprint_version IS NOT NULL
                AND presence_fingerprint IS NOT NULL
                AND quarantine_reason_code IS NULL
            )
            OR (
                validation_disposition = N'QUARANTINE'
                AND NULLIF(LTRIM(RTRIM(quarantine_reason_code)), N'') IS NOT NULL
                AND DATALENGTH(quarantine_reason_code)
                    = DATALENGTH(LTRIM(RTRIM(quarantine_reason_code)))
                AND LEFT(quarantine_reason_code, 1) COLLATE Latin1_General_100_BIN2
                    LIKE '[A-Z]'
                AND quarantine_reason_code COLLATE Latin1_General_100_BIN2
                    NOT LIKE '%[^A-Z0-9_]%'
                AND LEN(quarantine_reason_code) BETWEEN 2 AND 64
            )
        )
);
GO

CREATE INDEX IX_stg_execution_record_dedupe
    ON stg.execution_record (
        execution_id,
        validation_disposition,
        source_key,
        source_freshness_at_utc DESC,
        input_batch_number DESC,
        input_record_ordinal DESC
    )
    INCLUDE (
        row_fingerprint_version,
        source_row_hash,
        presence_fingerprint_version,
        presence_fingerprint,
        quarantine_reason_code
    );
GO

CREATE TABLE recon.quarantine_record (
    quarantine_record_id BIGINT IDENTITY(1, 1) NOT NULL,
    execution_id UNIQUEIDENTIFIER NOT NULL,
    stage_record_id BIGINT NOT NULL,
    reason_code NVARCHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL,
    source_key NVARCHAR(256) COLLATE Latin1_General_100_BIN2 NULL,
    row_fingerprint_version NVARCHAR(128) COLLATE Latin1_General_100_BIN2 NULL,
    source_row_hash CHAR(64) NULL,
    presence_fingerprint_version NVARCHAR(128) COLLATE Latin1_General_100_BIN2 NULL,
    presence_fingerprint CHAR(64) NULL,
    quarantined_at_utc DATETIME2(3) NOT NULL,
    CONSTRAINT PK_recon_quarantine_record PRIMARY KEY CLUSTERED (quarantine_record_id),
    CONSTRAINT UQ_recon_quarantine_record_stage UNIQUE (stage_record_id),
    CONSTRAINT FK_recon_quarantine_record_execution
        FOREIGN KEY (execution_id) REFERENCES ctl.execution_attempt (execution_id),
    CONSTRAINT FK_recon_quarantine_record_stage
        FOREIGN KEY (stage_record_id) REFERENCES stg.execution_record (stage_record_id),
    CONSTRAINT CK_recon_quarantine_record_reason_code
        CHECK (
            LEFT(reason_code, 1) COLLATE Latin1_General_100_BIN2 LIKE '[A-Z]'
            AND reason_code COLLATE Latin1_General_100_BIN2 NOT LIKE '%[^A-Z0-9_]%'
            AND LEN(reason_code) BETWEEN 2 AND 64
            AND DATALENGTH(reason_code) = DATALENGTH(LTRIM(RTRIM(reason_code)))
        ),
    CONSTRAINT CK_recon_quarantine_record_source_key
        CHECK (
            source_key IS NULL
            OR (
                NULLIF(LTRIM(RTRIM(source_key)), N'') IS NOT NULL
                AND DATALENGTH(source_key) = DATALENGTH(LTRIM(RTRIM(source_key)))
            )
        ),
    CONSTRAINT CK_recon_quarantine_record_fingerprints
        CHECK (
            (
                (
                    source_row_hash IS NULL
                    AND row_fingerprint_version IS NULL
                )
                OR (
                    source_row_hash IS NOT NULL
                    AND LEN(source_row_hash) = 64
                    AND source_row_hash NOT LIKE '%[^0-9A-Fa-f]%'
                    AND NULLIF(LTRIM(RTRIM(row_fingerprint_version)), N'') IS NOT NULL
                    AND DATALENGTH(row_fingerprint_version)
                        = DATALENGTH(LTRIM(RTRIM(row_fingerprint_version)))
                )
            )
            AND (
            (
                presence_fingerprint IS NULL
                AND presence_fingerprint_version IS NULL
            )
            OR (
                presence_fingerprint IS NOT NULL
                AND LEN(presence_fingerprint) = 64
                AND presence_fingerprint NOT LIKE '%[^0-9A-Fa-f]%'
                AND NULLIF(LTRIM(RTRIM(presence_fingerprint_version)), N'') IS NOT NULL
                AND DATALENGTH(presence_fingerprint_version)
                    = DATALENGTH(LTRIM(RTRIM(presence_fingerprint_version)))
            )
            )
        )
);
GO

CREATE INDEX IX_recon_quarantine_record_execution
    ON recon.quarantine_record (execution_id, quarantined_at_utc, reason_code);
GO

-- Candidate set isolado por execução. Somente a publicação reconciliada pode consumi-lo.
CREATE TABLE stg.execution_candidate (
    execution_id UNIQUEIDENTIFIER NOT NULL,
    source_key NVARCHAR(256) COLLATE Latin1_General_100_BIN2 NOT NULL,
    winner_stage_record_id BIGINT NOT NULL,
    row_fingerprint_version NVARCHAR(128) COLLATE Latin1_General_100_BIN2 NOT NULL,
    source_row_hash CHAR(64) NOT NULL,
    presence_fingerprint_version NVARCHAR(128) COLLATE Latin1_General_100_BIN2 NOT NULL,
    presence_fingerprint CHAR(64) NOT NULL,
    source_freshness_at_utc DATETIME2(3) NULL,
    prepared_at_utc DATETIME2(3) NOT NULL,
    CONSTRAINT PK_stg_execution_candidate PRIMARY KEY CLUSTERED (execution_id, source_key),
    CONSTRAINT UQ_stg_execution_candidate_winner UNIQUE (winner_stage_record_id),
    CONSTRAINT FK_stg_execution_candidate_execution
        FOREIGN KEY (execution_id) REFERENCES ctl.execution_attempt (execution_id),
    CONSTRAINT FK_stg_execution_candidate_winner
        FOREIGN KEY (winner_stage_record_id) REFERENCES stg.execution_record (stage_record_id),
    CONSTRAINT CK_stg_execution_candidate_non_blank CHECK (
        NULLIF(LTRIM(RTRIM(source_key)), N'') IS NOT NULL
        AND NULLIF(LTRIM(RTRIM(row_fingerprint_version)), N'') IS NOT NULL
        AND NULLIF(LTRIM(RTRIM(presence_fingerprint_version)), N'') IS NOT NULL
        AND DATALENGTH(source_key) = DATALENGTH(LTRIM(RTRIM(source_key)))
        AND DATALENGTH(row_fingerprint_version)
            = DATALENGTH(LTRIM(RTRIM(row_fingerprint_version)))
        AND DATALENGTH(presence_fingerprint_version)
            = DATALENGTH(LTRIM(RTRIM(presence_fingerprint_version)))
    ),
    CONSTRAINT CK_stg_execution_candidate_fingerprints CHECK (
        LEN(source_row_hash) = 64
        AND source_row_hash NOT LIKE '%[^0-9A-Fa-f]%'
        AND LEN(presence_fingerprint) = 64
        AND presence_fingerprint NOT LIKE '%[^0-9A-Fa-f]%'
    )
);
GO

-- Estado corrente técnico aplicado somente pelo protocolo atômico ao fim desta migration.
CREATE TABLE core.entity_record_state (
    record_state_id BIGINT IDENTITY(1, 1) NOT NULL,
    environment_name NVARCHAR(32) COLLATE Latin1_General_100_BIN2 NOT NULL,
    source_instance NVARCHAR(128) COLLATE Latin1_General_100_BIN2 NOT NULL,
    tenant_scope NVARCHAR(128) COLLATE Latin1_General_100_BIN2 NOT NULL,
    entity_name NVARCHAR(128) COLLATE Latin1_General_100_BIN2 NOT NULL,
    source_key NVARCHAR(256) COLLATE Latin1_General_100_BIN2 NOT NULL,
    row_fingerprint_version NVARCHAR(128) COLLATE Latin1_General_100_BIN2 NOT NULL,
    source_row_hash CHAR(64) NOT NULL,
    presence_fingerprint_version NVARCHAR(128) COLLATE Latin1_General_100_BIN2 NOT NULL,
    presence_fingerprint CHAR(64) NOT NULL,
    source_freshness_at_utc DATETIME2(3) NULL,
    active BIT NOT NULL CONSTRAINT DF_core_entity_record_state_active DEFAULT (1),
    first_promoted_execution_id UNIQUEIDENTIFIER NOT NULL,
    last_promoted_execution_id UNIQUEIDENTIFIER NOT NULL,
    first_promoted_at_utc DATETIME2(3) NOT NULL,
    last_promoted_at_utc DATETIME2(3) NOT NULL,
    CONSTRAINT PK_core_entity_record_state PRIMARY KEY CLUSTERED (record_state_id),
    CONSTRAINT UQ_core_entity_record_state_source UNIQUE (
        environment_name, source_instance, tenant_scope, entity_name, source_key
    ),
    CONSTRAINT FK_core_entity_record_state_source
        FOREIGN KEY (source_instance) REFERENCES ctl.source_catalog (source_instance),
    CONSTRAINT FK_core_entity_record_state_first_execution
        FOREIGN KEY (first_promoted_execution_id) REFERENCES ctl.execution_attempt (execution_id),
    CONSTRAINT FK_core_entity_record_state_last_execution
        FOREIGN KEY (last_promoted_execution_id) REFERENCES ctl.execution_attempt (execution_id),
    CONSTRAINT CK_core_entity_record_state_non_blank
        CHECK (
            NULLIF(LTRIM(RTRIM(environment_name)), N'') IS NOT NULL
            AND NULLIF(LTRIM(RTRIM(source_instance)), N'') IS NOT NULL
            AND NULLIF(LTRIM(RTRIM(tenant_scope)), N'') IS NOT NULL
            AND NULLIF(LTRIM(RTRIM(entity_name)), N'') IS NOT NULL
            AND NULLIF(LTRIM(RTRIM(source_key)), N'') IS NOT NULL
            AND NULLIF(LTRIM(RTRIM(row_fingerprint_version)), N'') IS NOT NULL
            AND NULLIF(LTRIM(RTRIM(presence_fingerprint_version)), N'') IS NOT NULL
            AND DATALENGTH(environment_name) = DATALENGTH(LTRIM(RTRIM(environment_name)))
            AND DATALENGTH(source_instance) = DATALENGTH(LTRIM(RTRIM(source_instance)))
            AND DATALENGTH(tenant_scope) = DATALENGTH(LTRIM(RTRIM(tenant_scope)))
            AND DATALENGTH(entity_name) = DATALENGTH(LTRIM(RTRIM(entity_name)))
            AND DATALENGTH(source_key) = DATALENGTH(LTRIM(RTRIM(source_key)))
            AND DATALENGTH(row_fingerprint_version)
                = DATALENGTH(LTRIM(RTRIM(row_fingerprint_version)))
            AND DATALENGTH(presence_fingerprint_version)
                = DATALENGTH(LTRIM(RTRIM(presence_fingerprint_version)))
        ),
    CONSTRAINT CK_core_entity_record_state_fingerprints
        CHECK (
            LEN(source_row_hash) = 64 AND source_row_hash NOT LIKE '%[^0-9A-Fa-f]%'
            AND LEN(presence_fingerprint) = 64 AND presence_fingerprint NOT LIKE '%[^0-9A-Fa-f]%'
        ),
    CONSTRAINT CK_core_entity_record_state_promoted_at
        CHECK (first_promoted_at_utc <= last_promoted_at_utc)
);
GO

CREATE INDEX IX_core_entity_record_state_active
    ON core.entity_record_state (
        environment_name, source_instance, tenant_scope, entity_name, active, source_key
    );
GO

CREATE TABLE ctl.execution_promotion_result (
    execution_id UNIQUEIDENTIFIER NOT NULL,
    physical_rows BIGINT NOT NULL,
    distinct_root_keys BIGINT NOT NULL,
    candidate_rows BIGINT NOT NULL,
    duplicate_rows BIGINT NOT NULL,
    quarantined_root_keys BIGINT NOT NULL,
    unidentified_quarantine_rows BIGINT NOT NULL,
    quarantined_stage_rows BIGINT NOT NULL,
    promoted_at_utc DATETIME2(3) NOT NULL,
    CONSTRAINT PK_ctl_execution_promotion_result PRIMARY KEY CLUSTERED (execution_id),
    CONSTRAINT FK_ctl_execution_promotion_result_execution
        FOREIGN KEY (execution_id) REFERENCES ctl.execution_attempt (execution_id),
    CONSTRAINT CK_ctl_execution_promotion_result_counts
        CHECK (
            physical_rows >= 0
            AND distinct_root_keys >= 0
            AND candidate_rows >= 0
            AND duplicate_rows >= 0
            AND quarantined_root_keys >= 0
            AND unidentified_quarantine_rows >= 0
            AND quarantined_stage_rows >= 0
            AND physical_rows = distinct_root_keys + duplicate_rows + unidentified_quarantine_rows
            AND distinct_root_keys = candidate_rows + quarantined_root_keys
            AND quarantined_stage_rows >= quarantined_root_keys + unidentified_quarantine_rows
        )
);
GO

CREATE TABLE recon.execution_candidate_application (
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
    CONSTRAINT PK_recon_execution_candidate_application
        PRIMARY KEY CLUSTERED (execution_id, source_key),
    CONSTRAINT UQ_recon_execution_candidate_application_record
        UNIQUE (execution_id, record_state_id),
    CONSTRAINT FK_recon_execution_candidate_application_candidate
        FOREIGN KEY (execution_id, source_key)
        REFERENCES stg.execution_candidate (execution_id, source_key),
    CONSTRAINT FK_recon_execution_candidate_application_record
        FOREIGN KEY (record_state_id) REFERENCES core.entity_record_state (record_state_id),
    CONSTRAINT CK_recon_execution_candidate_application_disposition CHECK (
        application_disposition IN (
            N'INSERTED', N'UPDATED', N'REACTIVATED', N'NO_OP', N'STALE_NO_OP'
        )
        AND DATALENGTH(application_disposition)
            = DATALENGTH(LTRIM(RTRIM(application_disposition)))
    ),
    CONSTRAINT CK_recon_execution_candidate_application_fingerprints CHECK (
        NULLIF(LTRIM(RTRIM(result_row_fingerprint_version)), N'') IS NOT NULL
        AND NULLIF(LTRIM(RTRIM(result_presence_fingerprint_version)), N'') IS NOT NULL
        AND DATALENGTH(result_row_fingerprint_version)
            = DATALENGTH(LTRIM(RTRIM(result_row_fingerprint_version)))
        AND DATALENGTH(result_presence_fingerprint_version)
            = DATALENGTH(LTRIM(RTRIM(result_presence_fingerprint_version)))
        AND LEN(result_source_row_hash) = 64
        AND result_source_row_hash NOT LIKE '%[^0-9A-Fa-f]%'
        AND LEN(result_presence_fingerprint) = 64
        AND result_presence_fingerprint NOT LIKE '%[^0-9A-Fa-f]%'
    )
);
GO

CREATE INDEX IX_recon_execution_candidate_application_record
    ON recon.execution_candidate_application (record_state_id, execution_id);
GO

CREATE TABLE recon.execution_reconciliation_result (
    execution_id UNIQUEIDENTIFIER NOT NULL,
    candidate_rows BIGINT NOT NULL,
    inserted_rows BIGINT NOT NULL,
    updated_rows BIGINT NOT NULL,
    reactivated_rows BIGINT NOT NULL,
    noop_rows BIGINT NOT NULL,
    stale_noop_rows BIGINT NOT NULL,
    reconciled_at_utc DATETIME2(3) NOT NULL,
    published_at_utc DATETIME2(3) NOT NULL,
    CONSTRAINT PK_recon_execution_reconciliation_result PRIMARY KEY CLUSTERED (execution_id),
    CONSTRAINT FK_recon_execution_reconciliation_result_execution
        FOREIGN KEY (execution_id) REFERENCES ctl.execution_attempt (execution_id),
    CONSTRAINT FK_recon_execution_reconciliation_result_candidate_set
        FOREIGN KEY (execution_id) REFERENCES ctl.execution_promotion_result (execution_id),
    CONSTRAINT CK_recon_execution_reconciliation_result_counts CHECK (
        candidate_rows >= 0
        AND inserted_rows >= 0
        AND updated_rows >= 0
        AND reactivated_rows >= 0
        AND noop_rows >= 0
        AND stale_noop_rows >= 0
        AND stale_noop_rows <= noop_rows
        AND candidate_rows = inserted_rows + updated_rows + reactivated_rows + noop_rows
    ),
    CONSTRAINT CK_recon_execution_reconciliation_result_times CHECK (
        reconciled_at_utc <= published_at_utc
    )
);
GO

-- O adapter JDBC envia este comando em executeBatch dentro de uma única transação de conexão.
-- Cada linha é idempotente por (execution_id, input_batch_number, input_record_ordinal).
CREATE OR ALTER PROCEDURE stg.usp_stage_record
    @execution_id UNIQUEIDENTIFIER,
    @input_batch_number INT,
    @input_record_ordinal INT,
    @source_key NVARCHAR(MAX),
    @row_fingerprint_version NVARCHAR(MAX),
    @source_row_hash NVARCHAR(MAX),
    @presence_fingerprint_version NVARCHAR(MAX),
    @presence_fingerprint NVARCHAR(MAX),
    @source_freshness_at_utc DATETIME2(3),
    @validation_disposition NVARCHAR(MAX),
    @quarantine_reason_code NVARCHAR(MAX),
    @staged_at_utc DATETIME2(3)
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;

    IF DATALENGTH(@source_key) > 512
        OR DATALENGTH(@row_fingerprint_version) > 256
        OR DATALENGTH(@source_row_hash) > 128
        OR DATALENGTH(@presence_fingerprint_version) > 256
        OR DATALENGTH(@presence_fingerprint) > 128
        OR DATALENGTH(@validation_disposition) > 32
        OR DATALENGTH(@quarantine_reason_code) > 128
        THROW 51400, N'Registro de staging excede o limite textual.', 1;

    SET @source_key = NULLIF(LTRIM(RTRIM(@source_key)), N'');
    SET @row_fingerprint_version = NULLIF(LTRIM(RTRIM(@row_fingerprint_version)), N'');
    SET @source_row_hash = LOWER(@source_row_hash);
    SET @presence_fingerprint_version = NULLIF(LTRIM(RTRIM(@presence_fingerprint_version)), N'');
    SET @presence_fingerprint = LOWER(@presence_fingerprint);
    SET @validation_disposition = LTRIM(RTRIM(@validation_disposition));
    SET @quarantine_reason_code = NULLIF(LTRIM(RTRIM(@quarantine_reason_code)), N'');

    IF @execution_id IS NULL
        OR @input_batch_number IS NULL
        OR @input_batch_number < 1
        OR @input_record_ordinal IS NULL
        OR @input_record_ordinal NOT BETWEEN 1 AND 10000
        OR @staged_at_utc IS NULL
        OR @validation_disposition COLLATE Latin1_General_100_BIN2
            NOT IN (N'VALID', N'QUARANTINE')
        OR ((@source_row_hash IS NULL AND @row_fingerprint_version IS NOT NULL)
            OR (@source_row_hash IS NOT NULL AND @row_fingerprint_version IS NULL))
        OR ((@presence_fingerprint IS NULL AND @presence_fingerprint_version IS NOT NULL)
            OR (@presence_fingerprint IS NOT NULL AND @presence_fingerprint_version IS NULL))
        OR (@source_row_hash IS NOT NULL AND (
            LEN(@source_row_hash) <> 64
            OR @source_row_hash COLLATE Latin1_General_100_BIN2 LIKE '%[^0-9A-Fa-f]%'
        ))
        OR (@presence_fingerprint IS NOT NULL AND (
            LEN(@presence_fingerprint) <> 64
            OR @presence_fingerprint COLLATE Latin1_General_100_BIN2 LIKE '%[^0-9A-Fa-f]%'
        ))
        OR (
            @validation_disposition COLLATE Latin1_General_100_BIN2 = N'VALID'
            AND (
                @source_key IS NULL
                OR @row_fingerprint_version IS NULL
                OR @source_row_hash IS NULL
                OR @presence_fingerprint_version IS NULL
                OR @presence_fingerprint IS NULL
                OR @quarantine_reason_code IS NOT NULL
            )
        )
        OR (
            @validation_disposition COLLATE Latin1_General_100_BIN2 = N'QUARANTINE'
            AND (
                NULLIF(LTRIM(RTRIM(@quarantine_reason_code)), N'') IS NULL
                OR LEFT(@quarantine_reason_code, 1) COLLATE Latin1_General_100_BIN2
                    NOT LIKE '[A-Z]'
                OR @quarantine_reason_code COLLATE Latin1_General_100_BIN2
                    LIKE '%[^A-Z0-9_]%'
                OR LEN(@quarantine_reason_code) NOT BETWEEN 2 AND 64
            )
        )
        THROW 51400, N'Registro de staging inválido.', 1;

    BEGIN TRANSACTION;

    DECLARE @execution_state NVARCHAR(32);
    DECLARE @partition_id BIGINT;
    SELECT
        @execution_state = attempt.current_state,
        @partition_id = attempt.partition_id
    FROM ctl.execution_attempt AS attempt WITH (UPDLOCK, HOLDLOCK)
    WHERE attempt.execution_id = @execution_id;

    IF @partition_id IS NULL
        THROW 51401, N'A execução não existe para staging.', 1;

    IF EXISTS (
        SELECT 1
        FROM stg.execution_record AS persisted WITH (UPDLOCK, HOLDLOCK)
        WHERE persisted.execution_id = @execution_id
          AND persisted.input_batch_number = @input_batch_number
          AND persisted.input_record_ordinal = @input_record_ordinal
          AND (
              (persisted.source_key <> @source_key)
              OR (persisted.source_key IS NULL AND @source_key IS NOT NULL)
              OR (persisted.source_key IS NOT NULL AND @source_key IS NULL)
              OR (persisted.row_fingerprint_version <> @row_fingerprint_version)
              OR (persisted.row_fingerprint_version IS NULL AND @row_fingerprint_version IS NOT NULL)
              OR (persisted.row_fingerprint_version IS NOT NULL AND @row_fingerprint_version IS NULL)
              OR (persisted.source_row_hash <> @source_row_hash)
              OR (persisted.source_row_hash IS NULL AND @source_row_hash IS NOT NULL)
              OR (persisted.source_row_hash IS NOT NULL AND @source_row_hash IS NULL)
              OR (persisted.presence_fingerprint_version <> @presence_fingerprint_version)
              OR (persisted.presence_fingerprint_version IS NULL AND @presence_fingerprint_version IS NOT NULL)
              OR (persisted.presence_fingerprint_version IS NOT NULL AND @presence_fingerprint_version IS NULL)
              OR (persisted.presence_fingerprint <> @presence_fingerprint)
              OR (persisted.presence_fingerprint IS NULL AND @presence_fingerprint IS NOT NULL)
              OR (persisted.presence_fingerprint IS NOT NULL AND @presence_fingerprint IS NULL)
              OR (persisted.source_freshness_at_utc <> @source_freshness_at_utc)
              OR (persisted.source_freshness_at_utc IS NULL AND @source_freshness_at_utc IS NOT NULL)
              OR (persisted.source_freshness_at_utc IS NOT NULL AND @source_freshness_at_utc IS NULL)
              OR persisted.validation_disposition <> @validation_disposition
              OR (persisted.quarantine_reason_code <> @quarantine_reason_code)
              OR (persisted.quarantine_reason_code IS NULL AND @quarantine_reason_code IS NOT NULL)
              OR (persisted.quarantine_reason_code IS NOT NULL AND @quarantine_reason_code IS NULL)
          )
    )
        THROW 51403, N'Registro de staging já existe com ocorrência imutável divergente.', 1;

    IF EXISTS (
        SELECT 1
        FROM stg.execution_record AS persisted WITH (UPDLOCK, HOLDLOCK)
        WHERE persisted.execution_id = @execution_id
          AND persisted.input_batch_number = @input_batch_number
          AND persisted.input_record_ordinal = @input_record_ordinal
    )
    BEGIN
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

    IF @lease_expires_at_utc IS NULL
        OR @lease_released_at_utc IS NOT NULL
        OR @lease_expires_at_utc <= @database_now_utc
        THROW 51401, N'A execução não possui lease corrente e ativa para staging.', 1;

    IF @execution_state COLLATE Latin1_General_100_BIN2 IN (N'STAGED', N'PROMOTED')
        THROW 51404, N'Candidate set fechado não aceita novo registro de staging.', 1;

    IF @execution_state COLLATE Latin1_General_100_BIN2 NOT IN (N'EXTRACTING', N'EXTRACTED')
        THROW 51402, N'A execução não aceita staging neste estado.', 1;

    INSERT INTO stg.execution_record (
        execution_id, input_batch_number, input_record_ordinal, source_key,
        row_fingerprint_version, source_row_hash, presence_fingerprint_version,
        presence_fingerprint, source_freshness_at_utc, validation_disposition,
        quarantine_reason_code, staged_at_utc
    ) VALUES (
        @execution_id, @input_batch_number, @input_record_ordinal, @source_key,
        @row_fingerprint_version, @source_row_hash, @presence_fingerprint_version,
        @presence_fingerprint, @source_freshness_at_utc, @validation_disposition,
        @quarantine_reason_code, @database_now_utc
    );

    COMMIT TRANSACTION;
END;
GO

CREATE OR ALTER PROCEDURE core.usp_prepare_staged_execution
    @execution_id UNIQUEIDENTIFIER,
    @contract_version NVARCHAR(MAX),
    @contract_fingerprint NVARCHAR(MAX),
    @configuration_version NVARCHAR(MAX),
    @configuration_fingerprint NVARCHAR(MAX)
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;

    IF DATALENGTH(@contract_version) > 256
        OR DATALENGTH(@contract_fingerprint) > 128
        OR DATALENGTH(@configuration_version) > 256
        OR DATALENGTH(@configuration_fingerprint) > 128
        THROW 51410, N'Preparação de candidate set inválida.', 1;

    SET @contract_version = LTRIM(RTRIM(@contract_version));
    SET @contract_fingerprint = LOWER(@contract_fingerprint);
    SET @configuration_version = LTRIM(RTRIM(@configuration_version));
    SET @configuration_fingerprint = LOWER(@configuration_fingerprint);

    IF @execution_id IS NULL
        OR NULLIF(@contract_version, N'') IS NULL
        OR @contract_fingerprint IS NULL
        OR LEN(@contract_fingerprint) <> 64
        OR @contract_fingerprint COLLATE Latin1_General_100_BIN2 LIKE '%[^0-9a-f]%'
        OR NULLIF(@configuration_version, N'') IS NULL
        OR @configuration_fingerprint IS NULL
        OR LEN(@configuration_fingerprint) <> 64
        OR @configuration_fingerprint COLLATE Latin1_General_100_BIN2 LIKE '%[^0-9a-f]%'
        THROW 51410, N'Preparação de candidate set inválida.', 1;

    BEGIN TRANSACTION;

    DECLARE @execution_state NVARCHAR(32);
    DECLARE @partition_id BIGINT;
    DECLARE @next_transition_sequence INT;
    DECLARE @persisted_contract_version NVARCHAR(128);
    DECLARE @persisted_contract_fingerprint CHAR(64);
    DECLARE @persisted_configuration_version NVARCHAR(128);
    DECLARE @persisted_configuration_fingerprint CHAR(64);
    SELECT
        @execution_state = attempt.current_state,
        @partition_id = attempt.partition_id,
        @next_transition_sequence = attempt.next_transition_sequence,
        @persisted_contract_version = attempt.contract_version,
        @persisted_contract_fingerprint = attempt.contract_fingerprint,
        @persisted_configuration_version = attempt.configuration_version,
        @persisted_configuration_fingerprint = attempt.configuration_fingerprint
    FROM ctl.execution_attempt AS attempt WITH (UPDLOCK, HOLDLOCK)
    WHERE attempt.execution_id = @execution_id;

    IF @partition_id IS NULL
        THROW 51411, N'A execução não existe para promoção.', 1;

    IF @persisted_contract_version IS NULL
        OR @persisted_contract_fingerprint IS NULL
        OR @persisted_configuration_version IS NULL
        OR @persisted_configuration_fingerprint IS NULL
        OR @persisted_contract_version COLLATE Latin1_General_100_BIN2
            <> @contract_version COLLATE Latin1_General_100_BIN2
        OR @persisted_contract_fingerprint COLLATE Latin1_General_100_BIN2
            <> @contract_fingerprint COLLATE Latin1_General_100_BIN2
        OR @persisted_configuration_version COLLATE Latin1_General_100_BIN2
            <> @configuration_version COLLATE Latin1_General_100_BIN2
        OR @persisted_configuration_fingerprint COLLATE Latin1_General_100_BIN2
            <> @configuration_fingerprint COLLATE Latin1_General_100_BIN2
        THROW 51418, N'A autorização de contrato diverge da ocorrência.', 1;

    IF @execution_state COLLATE Latin1_General_100_BIN2 = N'PROMOTED'
    BEGIN
        IF NOT EXISTS (
            SELECT 1
            FROM ctl.execution_promotion_result AS result WITH (UPDLOCK, HOLDLOCK)
            WHERE result.execution_id = @execution_id
              AND result.physical_rows = (
                  SELECT COUNT_BIG(*) FROM stg.execution_record WITH (UPDLOCK, HOLDLOCK)
                  WHERE execution_id = @execution_id
              )
              AND result.candidate_rows = (
                  SELECT COUNT_BIG(*) FROM stg.execution_candidate WITH (UPDLOCK, HOLDLOCK)
                  WHERE execution_id = @execution_id
              )
              AND result.quarantined_stage_rows = (
                  SELECT COUNT_BIG(*) FROM recon.quarantine_record WITH (UPDLOCK, HOLDLOCK)
                  WHERE execution_id = @execution_id
              )
              AND result.unidentified_quarantine_rows = (
                  SELECT COUNT_BIG(*) FROM recon.quarantine_record WITH (UPDLOCK, HOLDLOCK)
                  WHERE execution_id = @execution_id AND source_key IS NULL
              )
              AND result.quarantined_root_keys = (
                  SELECT COUNT_BIG(DISTINCT source_key)
                  FROM recon.quarantine_record WITH (UPDLOCK, HOLDLOCK)
                  WHERE execution_id = @execution_id AND source_key IS NOT NULL
              )
              AND EXISTS (
                  SELECT 1
                  FROM ctl.execution_count AS count_audit WITH (UPDLOCK, HOLDLOCK)
                  WHERE count_audit.execution_id = result.execution_id
                    AND count_audit.count_phase = N'STAGING_KERNEL'
                    AND count_audit.physical_rows = result.physical_rows
                    AND count_audit.distinct_root_keys = result.distinct_root_keys
                    AND count_audit.duplicate_rows = result.duplicate_rows
                    AND count_audit.valid_rows = result.candidate_rows
                    AND count_audit.quarantined_root_keys = result.quarantined_root_keys
                    AND count_audit.unidentified_quarantine_rows =
                        result.unidentified_quarantine_rows
              )
              AND NOT EXISTS (
                  SELECT 1
                  FROM stg.execution_candidate AS candidate WITH (UPDLOCK, HOLDLOCK)
                  INNER JOIN stg.execution_record AS staged WITH (UPDLOCK, HOLDLOCK)
                      ON staged.stage_record_id = candidate.winner_stage_record_id
                  WHERE candidate.execution_id = @execution_id
                    AND (
                        staged.execution_id <> candidate.execution_id
                        OR staged.validation_disposition <> N'VALID'
                        OR staged.source_key <> candidate.source_key
                        OR staged.row_fingerprint_version <> candidate.row_fingerprint_version
                        OR staged.source_row_hash <> candidate.source_row_hash
                        OR staged.presence_fingerprint_version <>
                            candidate.presence_fingerprint_version
                        OR staged.presence_fingerprint <> candidate.presence_fingerprint
                        OR staged.source_freshness_at_utc <> candidate.source_freshness_at_utc
                        OR (
                            staged.source_freshness_at_utc IS NULL
                            AND candidate.source_freshness_at_utc IS NOT NULL
                        )
                        OR (
                            staged.source_freshness_at_utc IS NOT NULL
                            AND candidate.source_freshness_at_utc IS NULL
                        )
                    )
              )
              AND NOT EXISTS (
                  SELECT 1
                  FROM recon.quarantine_record AS quarantine WITH (UPDLOCK, HOLDLOCK)
                  INNER JOIN stg.execution_record AS staged WITH (UPDLOCK, HOLDLOCK)
                      ON staged.stage_record_id = quarantine.stage_record_id
                  WHERE quarantine.execution_id = @execution_id
                    AND (
                        staged.execution_id <> quarantine.execution_id
                        OR (
                            staged.validation_disposition = N'QUARANTINE'
                            AND quarantine.reason_code <> staged.quarantine_reason_code
                        )
                        OR (
                            staged.validation_disposition = N'VALID'
                            AND quarantine.reason_code NOT IN (
                                N'EQUAL_FRESHNESS_CONFLICT',
                                N'UNKNOWN_FRESHNESS_CONFLICT',
                                N'KEY_HAS_QUARANTINED_ROW'
                            )
                        )
                        OR (staged.source_key <> quarantine.source_key)
                        OR (staged.source_key IS NULL AND quarantine.source_key IS NOT NULL)
                        OR (staged.source_key IS NOT NULL AND quarantine.source_key IS NULL)
                        OR (staged.row_fingerprint_version <>
                            quarantine.row_fingerprint_version)
                        OR (
                            staged.row_fingerprint_version IS NULL
                            AND quarantine.row_fingerprint_version IS NOT NULL
                        )
                        OR (
                            staged.row_fingerprint_version IS NOT NULL
                            AND quarantine.row_fingerprint_version IS NULL
                        )
                        OR (staged.source_row_hash <> quarantine.source_row_hash)
                        OR (staged.source_row_hash IS NULL AND quarantine.source_row_hash IS NOT NULL)
                        OR (staged.source_row_hash IS NOT NULL AND quarantine.source_row_hash IS NULL)
                        OR (staged.presence_fingerprint_version <>
                            quarantine.presence_fingerprint_version)
                        OR (
                            staged.presence_fingerprint_version IS NULL
                            AND quarantine.presence_fingerprint_version IS NOT NULL
                        )
                        OR (
                            staged.presence_fingerprint_version IS NOT NULL
                            AND quarantine.presence_fingerprint_version IS NULL
                        )
                        OR (staged.presence_fingerprint <> quarantine.presence_fingerprint)
                        OR (
                            staged.presence_fingerprint IS NULL
                            AND quarantine.presence_fingerprint IS NOT NULL
                        )
                        OR (
                            staged.presence_fingerprint IS NOT NULL
                            AND quarantine.presence_fingerprint IS NULL
                        )
                    )
              )
              AND EXISTS (
                  SELECT 1
                  FROM ctl.execution_state_event AS promotion_event WITH (UPDLOCK, HOLDLOCK)
                  WHERE promotion_event.execution_id = @execution_id
                    AND promotion_event.transition_sequence = @next_transition_sequence - 1
                    AND promotion_event.previous_state = N'STAGED'
                    AND promotion_event.next_state = N'PROMOTED'
                    AND promotion_event.reason_code = N'CANDIDATE_SET_PREPARED'
              )
              AND EXISTS (
                  SELECT 1
                  FROM ctl.execution_partition AS promotion_partition WITH (UPDLOCK, HOLDLOCK)
                  WHERE promotion_partition.partition_id = @partition_id
                    AND promotion_partition.current_execution_id = @execution_id
                    AND promotion_partition.current_state = N'PROMOTED'
              )
        )
            THROW 51412, N'Execução promovida sem candidate set e auditoria coerentes.', 1;
        COMMIT TRANSACTION;
        RETURN;
    END;

    IF @execution_state COLLATE Latin1_General_100_BIN2 <> N'STAGED'
        THROW 51413, N'A execução não está pronta para preparar o candidate set.', 1;

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
        THROW 51411, N'A execução não possui lease corrente e ativa para promoção.', 1;

    IF NOT EXISTS (SELECT 1 FROM stg.execution_record WHERE execution_id = @execution_id)
        THROW 51414, N'Não há registros de staging para promover.', 1;

    DECLARE @physical_rows BIGINT;
    DECLARE @candidate_rows BIGINT;
    DECLARE @duplicate_rows BIGINT;
    DECLARE @quarantined_root_keys BIGINT;
    DECLARE @unidentified_quarantine_rows BIGINT;
    DECLARE @quarantined_stage_rows BIGINT;
    DECLARE @distinct_rows BIGINT;

    SELECT @physical_rows = COUNT_BIG(*)
    FROM stg.execution_record WITH (UPDLOCK, HOLDLOCK)
    WHERE execution_id = @execution_id;

    DECLARE @quarantined_keys TABLE (
        source_key NVARCHAR(256) COLLATE Latin1_General_100_BIN2 NOT NULL PRIMARY KEY,
        reason_code NVARCHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL
    );

    ;WITH maximum_freshness AS (
        SELECT source_key, MAX(source_freshness_at_utc) AS maximum_freshness_at_utc
        FROM stg.execution_record
        WHERE execution_id = @execution_id AND validation_disposition = N'VALID'
        GROUP BY source_key
    ), top_signatures AS (
        SELECT DISTINCT
            staged.source_key,
            maximum.maximum_freshness_at_utc,
            staged.row_fingerprint_version,
            staged.source_row_hash,
            staged.presence_fingerprint_version,
            staged.presence_fingerprint
        FROM stg.execution_record AS staged
        INNER JOIN maximum_freshness AS maximum ON maximum.source_key = staged.source_key
        WHERE staged.execution_id = @execution_id
          AND staged.validation_disposition = N'VALID'
          AND (
              staged.source_freshness_at_utc = maximum.maximum_freshness_at_utc
              OR (staged.source_freshness_at_utc IS NULL AND maximum.maximum_freshness_at_utc IS NULL)
          )
    )
    INSERT INTO @quarantined_keys (source_key, reason_code)
    SELECT
        source_key,
        CASE WHEN maximum_freshness_at_utc IS NULL
            THEN N'UNKNOWN_FRESHNESS_CONFLICT'
            ELSE N'EQUAL_FRESHNESS_CONFLICT'
        END
    FROM top_signatures
    GROUP BY source_key, maximum_freshness_at_utc
    HAVING COUNT_BIG(*) > 1;

    INSERT INTO @quarantined_keys (source_key, reason_code)
    SELECT DISTINCT staged.source_key, N'KEY_HAS_QUARANTINED_ROW'
    FROM stg.execution_record AS staged
    WHERE staged.execution_id = @execution_id
      AND staged.validation_disposition = N'QUARANTINE'
      AND staged.source_key IS NOT NULL
      AND NOT EXISTS (
          SELECT 1
          FROM @quarantined_keys AS quarantined
          WHERE quarantined.source_key = staged.source_key
      );

    DECLARE @ranked TABLE (
        stage_record_id BIGINT NOT NULL PRIMARY KEY,
        source_key NVARCHAR(256) COLLATE Latin1_General_100_BIN2 NOT NULL,
        row_fingerprint_version NVARCHAR(128) COLLATE Latin1_General_100_BIN2 NOT NULL,
        source_row_hash CHAR(64) NOT NULL,
        presence_fingerprint_version NVARCHAR(128) COLLATE Latin1_General_100_BIN2 NOT NULL,
        presence_fingerprint CHAR(64) NOT NULL,
        source_freshness_at_utc DATETIME2(3) NULL,
        dedupe_rank BIGINT NOT NULL
    );

    INSERT INTO @ranked (
        stage_record_id, source_key, row_fingerprint_version, source_row_hash,
        presence_fingerprint_version, presence_fingerprint, source_freshness_at_utc, dedupe_rank
    )
    SELECT
        staged.stage_record_id,
        staged.source_key,
        staged.row_fingerprint_version,
        staged.source_row_hash,
        staged.presence_fingerprint_version,
        staged.presence_fingerprint,
        staged.source_freshness_at_utc,
        ROW_NUMBER() OVER (
            PARTITION BY staged.source_key
            ORDER BY
                CASE WHEN staged.source_freshness_at_utc IS NULL THEN 1 ELSE 0 END,
                staged.source_freshness_at_utc DESC,
                staged.input_batch_number DESC,
                staged.input_record_ordinal DESC
        )
    FROM stg.execution_record AS staged WITH (UPDLOCK, HOLDLOCK)
    WHERE staged.execution_id = @execution_id
      AND staged.validation_disposition = N'VALID'
      AND NOT EXISTS (
          SELECT 1 FROM @quarantined_keys AS quarantined
          WHERE quarantined.source_key = staged.source_key
      );

    INSERT INTO recon.quarantine_record (
        execution_id, stage_record_id, reason_code, source_key,
        row_fingerprint_version, source_row_hash, presence_fingerprint_version,
        presence_fingerprint, quarantined_at_utc
    )
    SELECT
        staged.execution_id,
        staged.stage_record_id,
        CASE WHEN staged.validation_disposition = N'QUARANTINE'
            THEN staged.quarantine_reason_code ELSE quarantined.reason_code END,
        staged.source_key,
        staged.row_fingerprint_version,
        staged.source_row_hash,
        staged.presence_fingerprint_version,
        staged.presence_fingerprint,
        @database_now_utc
    FROM stg.execution_record AS staged
    LEFT JOIN @quarantined_keys AS quarantined ON quarantined.source_key = staged.source_key
    WHERE staged.execution_id = @execution_id
      AND (staged.validation_disposition = N'QUARANTINE' OR quarantined.source_key IS NOT NULL);

    INSERT INTO stg.execution_candidate (
        execution_id, source_key, winner_stage_record_id, row_fingerprint_version,
        source_row_hash, presence_fingerprint_version, presence_fingerprint,
        source_freshness_at_utc, prepared_at_utc
    )
    SELECT
        @execution_id, winner.source_key, winner.stage_record_id, winner.row_fingerprint_version,
        winner.source_row_hash, winner.presence_fingerprint_version, winner.presence_fingerprint,
        winner.source_freshness_at_utc, @database_now_utc
    FROM @ranked AS winner
    WHERE winner.dedupe_rank = 1;

    SELECT @candidate_rows = COUNT_BIG(*)
    FROM stg.execution_candidate WHERE execution_id = @execution_id;

    SELECT @quarantined_root_keys = COUNT_BIG(*) FROM @quarantined_keys;

    SELECT @unidentified_quarantine_rows = COUNT_BIG(*)
    FROM stg.execution_record
    WHERE execution_id = @execution_id AND source_key IS NULL;

    SELECT @quarantined_stage_rows = COUNT_BIG(*)
    FROM recon.quarantine_record WHERE execution_id = @execution_id;

    SET @distinct_rows = @candidate_rows + @quarantined_root_keys;
    SET @duplicate_rows = @physical_rows - @distinct_rows - @unidentified_quarantine_rows;

    IF @duplicate_rows < 0
        OR @physical_rows <> @distinct_rows + @duplicate_rows + @unidentified_quarantine_rows
        OR @distinct_rows <> @candidate_rows + @quarantined_root_keys
        OR @quarantined_stage_rows < @quarantined_root_keys + @unidentified_quarantine_rows
        THROW 51415, N'Equações canônicas do candidate set são inconsistentes.', 1;

    -- STAGING_KERNEL é namespace interno: a API runtime de contagens o rejeita.  Este INSERT
    -- permanece dentro da mesma transação que já travou attempt, partition, lease e staging.
    INSERT INTO ctl.execution_count (
        execution_id, count_phase, physical_rows, distinct_root_keys, duplicate_rows,
        valid_rows, quarantined_root_keys, unidentified_quarantine_rows, recorded_at_utc
    ) VALUES (
        @execution_id, N'STAGING_KERNEL', @physical_rows, @distinct_rows, @duplicate_rows,
        @candidate_rows, @quarantined_root_keys, @unidentified_quarantine_rows, @database_now_utc
    );

    INSERT INTO ctl.execution_promotion_result (
        execution_id, physical_rows, distinct_root_keys, candidate_rows,
        duplicate_rows, quarantined_root_keys, unidentified_quarantine_rows,
        quarantined_stage_rows, promoted_at_utc
    ) VALUES (
        @execution_id, @physical_rows, @distinct_rows, @candidate_rows,
        @duplicate_rows, @quarantined_root_keys, @unidentified_quarantine_rows,
        @quarantined_stage_rows, @database_now_utc
    );

    DECLARE @allocated_transition TABLE (transition_sequence INT NOT NULL);
    UPDATE ctl.execution_attempt
    SET current_state = N'PROMOTED',
        next_transition_sequence = next_transition_sequence + 1
    OUTPUT deleted.next_transition_sequence
        INTO @allocated_transition (transition_sequence)
    WHERE execution_id = @execution_id AND current_state = N'STAGED';

    IF @@ROWCOUNT <> 1
        THROW 51416, N'O estado mudou durante a preparação do candidate set.', 1;

    DECLARE @transition_sequence INT;
    SELECT @transition_sequence = transition_sequence FROM @allocated_transition;
    IF @transition_sequence IS NULL
        THROW 51416, N'Não foi possível alocar transition_sequence atomicamente.', 1;

    INSERT INTO ctl.execution_state_event (
        execution_id, transition_sequence, previous_state, next_state, reason_code, transitioned_at_utc
    ) VALUES (
        @execution_id, @transition_sequence, N'STAGED', N'PROMOTED',
        N'CANDIDATE_SET_PREPARED', @database_now_utc
    );

    UPDATE ctl.execution_partition
    SET current_state = N'PROMOTED'
    WHERE partition_id = @partition_id AND current_execution_id = @execution_id;

    IF @@ROWCOUNT <> 1
        THROW 51417, N'A execução deixou de ser corrente durante a promoção.', 1;

    COMMIT TRANSACTION;
END;
GO

-- Único entry point positivo de aplicação, reconciliação e publicação. O candidate set já foi
-- fechado pela preparação acima; deste ponto em diante todos os efeitos recuperáveis compartilham
-- a mesma transação, o mesmo fencing e o relógio autoritativo do SQL Server.
CREATE OR ALTER PROCEDURE core.usp_apply_reconcile_publish_execution
    @execution_id UNIQUEIDENTIFIER,
    @contract_version NVARCHAR(MAX),
    @contract_fingerprint NVARCHAR(MAX),
    @configuration_version NVARCHAR(MAX),
    @configuration_fingerprint NVARCHAR(MAX)
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;

    IF DATALENGTH(@contract_version) > 256
        OR DATALENGTH(@contract_fingerprint) > 128
        OR DATALENGTH(@configuration_version) > 256
        OR DATALENGTH(@configuration_fingerprint) > 128
        THROW 51420, N'Aplicação reconciliada inválida.', 1;

    SET @contract_version = LTRIM(RTRIM(@contract_version));
    SET @contract_fingerprint = LOWER(@contract_fingerprint);
    SET @configuration_version = LTRIM(RTRIM(@configuration_version));
    SET @configuration_fingerprint = LOWER(@configuration_fingerprint);

    IF @execution_id IS NULL
        OR NULLIF(@contract_version, N'') IS NULL
        OR @contract_fingerprint IS NULL
        OR LEN(@contract_fingerprint) <> 64
        OR @contract_fingerprint COLLATE Latin1_General_100_BIN2 LIKE '%[^0-9a-f]%'
        OR NULLIF(@configuration_version, N'') IS NULL
        OR @configuration_fingerprint IS NULL
        OR LEN(@configuration_fingerprint) <> 64
        OR @configuration_fingerprint COLLATE Latin1_General_100_BIN2 LIKE '%[^0-9a-f]%'
        THROW 51420, N'Aplicação reconciliada inválida.', 1;

    BEGIN TRANSACTION;

    DECLARE @partition_id BIGINT;
    DECLARE @environment_name NVARCHAR(32);
    DECLARE @source_instance NVARCHAR(128);
    DECLARE @tenant_scope NVARCHAR(128);
    DECLARE @entity_name NVARCHAR(128);
    DECLARE @execution_mode NVARCHAR(16);

    SELECT
        @partition_id = attempt.partition_id,
        @environment_name = partition.environment_name,
        @source_instance = partition.source_instance,
        @tenant_scope = partition.tenant_scope,
        @entity_name = partition.entity_name,
        @execution_mode = partition.execution_mode
    FROM ctl.execution_attempt AS attempt
    INNER JOIN ctl.execution_partition AS partition
        ON partition.partition_id = attempt.partition_id
    WHERE attempt.execution_id = @execution_id;

    IF @partition_id IS NULL
        THROW 51421, N'A execução não existe para aplicação reconciliada.', 1;

    DECLARE @lock_payload NVARCHAR(2000) = CONCAT(
        DATALENGTH(@environment_name), N':', @environment_name, N'|',
        DATALENGTH(@source_instance), N':', @source_instance, N'|',
        DATALENGTH(@tenant_scope), N':', @tenant_scope, N'|',
        DATALENGTH(@entity_name), N':', @entity_name
    );
    DECLARE @lock_resource NVARCHAR(255) = CONCAT(
        N'V2_APPLY_', CONVERT(NVARCHAR(64), HASHBYTES('SHA2_256', @lock_payload), 2)
    );
    DECLARE @application_lock_result INT;

    EXEC @application_lock_result = sys.sp_getapplock
        @Resource = @lock_resource,
        @LockMode = 'Exclusive',
        @LockOwner = 'Transaction',
        @LockTimeout = 10000,
        @DbPrincipal = 'public';

    IF @application_lock_result < 0
        THROW 51422, N'Não foi possível serializar a aplicação do namespace.', 1;

    DECLARE @execution_state NVARCHAR(32);
    DECLARE @partition_state NVARCHAR(32);
    DECLARE @current_execution_id UNIQUEIDENTIFIER;
    DECLARE @next_transition_sequence INT;
    DECLARE @persisted_contract_version NVARCHAR(128);
    DECLARE @persisted_contract_fingerprint CHAR(64);
    DECLARE @persisted_configuration_version NVARCHAR(128);
    DECLARE @persisted_configuration_fingerprint CHAR(64);

    SELECT
        @execution_state = attempt.current_state,
        @next_transition_sequence = attempt.next_transition_sequence,
        @partition_state = partition.current_state,
        @current_execution_id = partition.current_execution_id,
        @persisted_contract_version = attempt.contract_version,
        @persisted_contract_fingerprint = attempt.contract_fingerprint,
        @persisted_configuration_version = attempt.configuration_version,
        @persisted_configuration_fingerprint = attempt.configuration_fingerprint,
        @partition_id = partition.partition_id,
        @environment_name = partition.environment_name,
        @source_instance = partition.source_instance,
        @tenant_scope = partition.tenant_scope,
        @entity_name = partition.entity_name,
        @execution_mode = partition.execution_mode
    FROM ctl.execution_attempt AS attempt WITH (UPDLOCK, HOLDLOCK)
    INNER JOIN ctl.execution_partition AS partition WITH (UPDLOCK, HOLDLOCK)
        ON partition.partition_id = attempt.partition_id
    WHERE attempt.execution_id = @execution_id;

    IF @persisted_contract_version IS NULL
        OR @persisted_contract_fingerprint IS NULL
        OR @persisted_configuration_version IS NULL
        OR @persisted_configuration_fingerprint IS NULL
        OR @persisted_contract_version COLLATE Latin1_General_100_BIN2
            <> @contract_version COLLATE Latin1_General_100_BIN2
        OR @persisted_contract_fingerprint COLLATE Latin1_General_100_BIN2
            <> @contract_fingerprint COLLATE Latin1_General_100_BIN2
        OR @persisted_configuration_version COLLATE Latin1_General_100_BIN2
            <> @configuration_version COLLATE Latin1_General_100_BIN2
        OR @persisted_configuration_fingerprint COLLATE Latin1_General_100_BIN2
            <> @configuration_fingerprint COLLATE Latin1_General_100_BIN2
        THROW 51418, N'A autorização de contrato diverge da ocorrência.', 1;

    -- Retry depois de commit incerto: a resposta vem apenas da evidência imutável da execução.
    -- O core e o pointer podem legitimamente ter sido atualizados por uma execução posterior.
    IF @execution_state COLLATE Latin1_General_100_BIN2 = N'PUBLISHED'
    BEGIN
        IF NOT EXISTS (
            SELECT 1
            FROM recon.execution_reconciliation_result AS result WITH (UPDLOCK, HOLDLOCK)
            INNER JOIN ctl.execution_promotion_result AS candidate_set WITH (UPDLOCK, HOLDLOCK)
                ON candidate_set.execution_id = result.execution_id
            INNER JOIN ctl.execution_publication_event AS publication WITH (UPDLOCK, HOLDLOCK)
                ON publication.execution_id = result.execution_id
            WHERE result.execution_id = @execution_id
              AND publication.partition_id = @partition_id
              AND candidate_set.candidate_rows = result.candidate_rows
              AND candidate_set.quarantined_stage_rows = 0
              AND result.candidate_rows = (
                  SELECT COUNT_BIG(*)
                  FROM recon.execution_candidate_application WITH (UPDLOCK, HOLDLOCK)
                  WHERE execution_id = @execution_id
              )
              AND result.inserted_rows = (
                  SELECT COUNT_BIG(*)
                  FROM recon.execution_candidate_application WITH (UPDLOCK, HOLDLOCK)
                  WHERE execution_id = @execution_id
                    AND application_disposition = N'INSERTED'
              )
              AND result.updated_rows = (
                  SELECT COUNT_BIG(*)
                  FROM recon.execution_candidate_application WITH (UPDLOCK, HOLDLOCK)
                  WHERE execution_id = @execution_id
                    AND application_disposition = N'UPDATED'
              )
              AND result.reactivated_rows = (
                  SELECT COUNT_BIG(*)
                  FROM recon.execution_candidate_application WITH (UPDLOCK, HOLDLOCK)
                  WHERE execution_id = @execution_id
                    AND application_disposition = N'REACTIVATED'
              )
              AND result.noop_rows = (
                  SELECT COUNT_BIG(*)
                  FROM recon.execution_candidate_application WITH (UPDLOCK, HOLDLOCK)
                  WHERE execution_id = @execution_id
                    AND application_disposition IN (N'NO_OP', N'STALE_NO_OP')
              )
              AND result.stale_noop_rows = (
                  SELECT COUNT_BIG(*)
                  FROM recon.execution_candidate_application WITH (UPDLOCK, HOLDLOCK)
                  WHERE execution_id = @execution_id
                    AND application_disposition = N'STALE_NO_OP'
              )
              AND EXISTS (
                  SELECT 1
                  FROM ctl.execution_state_event AS event WITH (UPDLOCK, HOLDLOCK)
                  WHERE event.execution_id = @execution_id
                    AND event.transition_sequence = @next_transition_sequence - 2
                    AND event.previous_state = N'PROMOTED'
                    AND event.next_state = N'RECONCILED'
                    AND event.reason_code = N'CANDIDATE_SET_RECONCILED'
              )
              AND EXISTS (
                  SELECT 1
                  FROM ctl.execution_state_event AS event WITH (UPDLOCK, HOLDLOCK)
                  WHERE event.execution_id = @execution_id
                    AND event.transition_sequence = @next_transition_sequence - 1
                    AND event.previous_state = N'RECONCILED'
                    AND event.next_state = N'PUBLISHED'
                    AND event.reason_code = N'RECONCILIATION_PUBLISHED'
              )
              AND EXISTS (
                  SELECT 1
                  FROM ctl.execution_lease AS lease WITH (UPDLOCK, HOLDLOCK)
                  WHERE lease.execution_id = @execution_id
                    AND lease.partition_id = @partition_id
                    AND lease.released_at_utc IS NOT NULL
              )
        )
            THROW 51423, N'Execução publicada sem evidência imutável coerente.', 1;

        COMMIT TRANSACTION;

        SELECT
            result.execution_id,
            result.candidate_rows,
            result.inserted_rows,
            result.updated_rows,
            result.reactivated_rows,
            result.noop_rows,
            result.stale_noop_rows,
            result.reconciled_at_utc,
            result.published_at_utc,
            publication.incremental_frontier_before_utc,
            publication.incremental_frontier_after_utc
        FROM recon.execution_reconciliation_result AS result
        INNER JOIN ctl.execution_publication_event AS publication
            ON publication.execution_id = result.execution_id
        WHERE result.execution_id = @execution_id;
        RETURN;
    END;

    IF @execution_state COLLATE Latin1_General_100_BIN2 <> N'PROMOTED'
        OR @partition_state COLLATE Latin1_General_100_BIN2 <> N'PROMOTED'
        OR @current_execution_id <> @execution_id
        THROW 51424, N'A execução não está promovida e corrente para aplicação.', 1;

    IF EXISTS (
        SELECT 1 FROM recon.execution_candidate_application WITH (UPDLOCK, HOLDLOCK)
        WHERE execution_id = @execution_id
    ) OR EXISTS (
        SELECT 1 FROM recon.execution_reconciliation_result WITH (UPDLOCK, HOLDLOCK)
        WHERE execution_id = @execution_id
    ) OR EXISTS (
        SELECT 1 FROM ctl.execution_publication_event WITH (UPDLOCK, HOLDLOCK)
        WHERE execution_id = @execution_id
    ) OR EXISTS (
        SELECT 1 FROM ctl.partition_publication_pointer WITH (UPDLOCK, HOLDLOCK)
        WHERE published_execution_id = @execution_id
    )
        THROW 51425, N'Aplicação parcial pré-existente foi recusada.', 1;

    DECLARE @database_now_utc DATETIME2(3) = SYSUTCDATETIME();
    DECLARE @lease_expires_at_utc DATETIME2(3);
    DECLARE @lease_released_at_utc DATETIME2(3);

    SELECT
        @lease_expires_at_utc = lease.expires_at_utc,
        @lease_released_at_utc = lease.released_at_utc
    FROM ctl.execution_lease AS lease WITH (UPDLOCK, HOLDLOCK)
    WHERE lease.partition_id = @partition_id
      AND lease.execution_id = @execution_id;

    IF @lease_expires_at_utc IS NULL
        OR @lease_released_at_utc IS NOT NULL
        OR @lease_expires_at_utc <= @database_now_utc
        THROW 51426, N'A execução não possui lease corrente e ativa para aplicação.', 1;

    DECLARE @candidate_rows BIGINT;
    SELECT @candidate_rows = result.candidate_rows
    FROM ctl.execution_promotion_result AS result WITH (UPDLOCK, HOLDLOCK)
    WHERE result.execution_id = @execution_id
      AND result.quarantined_root_keys = 0
      AND result.unidentified_quarantine_rows = 0
      AND result.quarantined_stage_rows = 0
      AND result.candidate_rows = (
          SELECT COUNT_BIG(*)
          FROM stg.execution_candidate WITH (UPDLOCK, HOLDLOCK)
          WHERE execution_id = @execution_id
      )
      AND result.physical_rows = result.distinct_root_keys + result.duplicate_rows
      AND result.distinct_root_keys = result.candidate_rows;

    IF @candidate_rows IS NULL
        THROW 51427, N'Candidate set ausente, divergente ou com quarantine.', 1;

    IF EXISTS (
        SELECT 1
        FROM stg.execution_candidate AS candidate WITH (UPDLOCK, HOLDLOCK)
        INNER JOIN core.entity_record_state AS current_record WITH (
            UPDLOCK, HOLDLOCK, INDEX(UQ_core_entity_record_state_source)
        )
            ON current_record.environment_name = @environment_name
           AND current_record.source_instance = @source_instance
           AND current_record.tenant_scope = @tenant_scope
           AND current_record.entity_name = @entity_name
           AND current_record.source_key = candidate.source_key
        WHERE candidate.execution_id = @execution_id
          AND NOT (
              current_record.row_fingerprint_version = candidate.row_fingerprint_version
              AND current_record.source_row_hash = candidate.source_row_hash
              AND current_record.presence_fingerprint_version =
                  candidate.presence_fingerprint_version
              AND current_record.presence_fingerprint = candidate.presence_fingerprint
              AND (
                  current_record.source_freshness_at_utc = candidate.source_freshness_at_utc
                  OR (
                      current_record.source_freshness_at_utc IS NULL
                      AND candidate.source_freshness_at_utc IS NULL
                  )
              )
          )
          AND NOT (
              candidate.source_freshness_at_utc IS NOT NULL
              AND (
                  current_record.source_freshness_at_utc IS NULL
                  OR candidate.source_freshness_at_utc > current_record.source_freshness_at_utc
              )
          )
          AND NOT (
              current_record.source_freshness_at_utc IS NOT NULL
              AND (
                  candidate.source_freshness_at_utc IS NULL
                  OR candidate.source_freshness_at_utc < current_record.source_freshness_at_utc
              )
          )
    )
        THROW 51428, N'Frescor igual ou desconhecido possui conteúdo divergente.', 1;

    DECLARE @application_plan TABLE (
        source_key NVARCHAR(256) COLLATE Latin1_General_100_BIN2 NOT NULL PRIMARY KEY,
        record_state_id BIGINT NULL,
        application_disposition NVARCHAR(24) COLLATE Latin1_General_100_BIN2 NOT NULL,
        apply_candidate_values BIT NOT NULL
    );

    INSERT INTO @application_plan (
        source_key, record_state_id, application_disposition, apply_candidate_values
    )
    SELECT
        candidate.source_key,
        current_record.record_state_id,
        CASE
            WHEN current_record.record_state_id IS NULL THEN N'INSERTED'
            WHEN current_record.row_fingerprint_version = candidate.row_fingerprint_version
             AND current_record.source_row_hash = candidate.source_row_hash
             AND current_record.presence_fingerprint_version = candidate.presence_fingerprint_version
             AND current_record.presence_fingerprint = candidate.presence_fingerprint
             AND (
                 current_record.source_freshness_at_utc = candidate.source_freshness_at_utc
                 OR (
                     current_record.source_freshness_at_utc IS NULL
                     AND candidate.source_freshness_at_utc IS NULL
                 )
             )
                THEN CASE WHEN current_record.active = 0 THEN N'REACTIVATED' ELSE N'NO_OP' END
            WHEN candidate.source_freshness_at_utc IS NOT NULL
             AND (
                 current_record.source_freshness_at_utc IS NULL
                 OR candidate.source_freshness_at_utc > current_record.source_freshness_at_utc
             )
                THEN CASE WHEN current_record.active = 0 THEN N'REACTIVATED' ELSE N'UPDATED' END
            ELSE CASE WHEN current_record.active = 0 THEN N'REACTIVATED' ELSE N'STALE_NO_OP' END
        END,
        CASE
            WHEN current_record.record_state_id IS NULL THEN 1
            WHEN candidate.source_freshness_at_utc IS NOT NULL
             AND (
                 current_record.source_freshness_at_utc IS NULL
                 OR candidate.source_freshness_at_utc > current_record.source_freshness_at_utc
             ) THEN 1
            ELSE 0
        END
    FROM stg.execution_candidate AS candidate WITH (UPDLOCK, HOLDLOCK)
    LEFT JOIN core.entity_record_state AS current_record WITH (
        UPDLOCK, HOLDLOCK, INDEX(UQ_core_entity_record_state_source)
    )
        ON current_record.environment_name = @environment_name
       AND current_record.source_instance = @source_instance
       AND current_record.tenant_scope = @tenant_scope
       AND current_record.entity_name = @entity_name
       AND current_record.source_key = candidate.source_key
    WHERE candidate.execution_id = @execution_id;

    IF (SELECT COUNT_BIG(*) FROM @application_plan) <> @candidate_rows
        THROW 51429, N'O plano de aplicação não cobre todo o candidate set.', 1;

    INSERT INTO core.entity_record_state (
        environment_name, source_instance, tenant_scope, entity_name, source_key,
        row_fingerprint_version, source_row_hash, presence_fingerprint_version,
        presence_fingerprint, source_freshness_at_utc, active,
        first_promoted_execution_id, last_promoted_execution_id,
        first_promoted_at_utc, last_promoted_at_utc
    )
    SELECT
        @environment_name, @source_instance, @tenant_scope, @entity_name, candidate.source_key,
        candidate.row_fingerprint_version, candidate.source_row_hash,
        candidate.presence_fingerprint_version, candidate.presence_fingerprint,
        candidate.source_freshness_at_utc, 1,
        @execution_id, @execution_id, @database_now_utc, @database_now_utc
    FROM @application_plan AS application
    INNER JOIN stg.execution_candidate AS candidate
        ON candidate.execution_id = @execution_id
       AND candidate.source_key = application.source_key
    WHERE application.application_disposition = N'INSERTED';

    UPDATE current_record
    SET row_fingerprint_version = CASE WHEN application.apply_candidate_values = 1
            THEN candidate.row_fingerprint_version ELSE current_record.row_fingerprint_version END,
        source_row_hash = CASE WHEN application.apply_candidate_values = 1
            THEN candidate.source_row_hash ELSE current_record.source_row_hash END,
        presence_fingerprint_version = CASE WHEN application.apply_candidate_values = 1
            THEN candidate.presence_fingerprint_version
            ELSE current_record.presence_fingerprint_version END,
        presence_fingerprint = CASE WHEN application.apply_candidate_values = 1
            THEN candidate.presence_fingerprint ELSE current_record.presence_fingerprint END,
        source_freshness_at_utc = CASE WHEN application.apply_candidate_values = 1
            THEN candidate.source_freshness_at_utc ELSE current_record.source_freshness_at_utc END,
        active = 1,
        last_promoted_execution_id = @execution_id,
        last_promoted_at_utc = @database_now_utc
    FROM core.entity_record_state AS current_record
    INNER JOIN @application_plan AS application
        ON application.record_state_id = current_record.record_state_id
    INNER JOIN stg.execution_candidate AS candidate
        ON candidate.execution_id = @execution_id
       AND candidate.source_key = application.source_key
    WHERE application.application_disposition IN (N'UPDATED', N'REACTIVATED');

    UPDATE application
    SET record_state_id = current_record.record_state_id
    FROM @application_plan AS application
    INNER JOIN core.entity_record_state AS current_record
        ON current_record.environment_name = @environment_name
       AND current_record.source_instance = @source_instance
       AND current_record.tenant_scope = @tenant_scope
       AND current_record.entity_name = @entity_name
       AND current_record.source_key = application.source_key;

    IF EXISTS (SELECT 1 FROM @application_plan WHERE record_state_id IS NULL)
        THROW 51430, N'A aplicação não produziu identidade técnica para todo candidato.', 1;

    INSERT INTO recon.execution_candidate_application (
        execution_id, source_key, record_state_id, application_disposition,
        result_row_fingerprint_version, result_source_row_hash,
        result_presence_fingerprint_version, result_presence_fingerprint,
        result_source_freshness_at_utc, applied_at_utc
    )
    SELECT
        @execution_id, application.source_key, application.record_state_id,
        application.application_disposition,
        current_record.row_fingerprint_version, current_record.source_row_hash,
        current_record.presence_fingerprint_version, current_record.presence_fingerprint,
        current_record.source_freshness_at_utc, @database_now_utc
    FROM @application_plan AS application
    INNER JOIN core.entity_record_state AS current_record
        ON current_record.record_state_id = application.record_state_id;

    DECLARE @inserted_rows BIGINT = (
        SELECT COUNT_BIG(*) FROM @application_plan WHERE application_disposition = N'INSERTED'
    );
    DECLARE @updated_rows BIGINT = (
        SELECT COUNT_BIG(*) FROM @application_plan WHERE application_disposition = N'UPDATED'
    );
    DECLARE @reactivated_rows BIGINT = (
        SELECT COUNT_BIG(*) FROM @application_plan WHERE application_disposition = N'REACTIVATED'
    );
    DECLARE @noop_rows BIGINT = (
        SELECT COUNT_BIG(*) FROM @application_plan
        WHERE application_disposition IN (N'NO_OP', N'STALE_NO_OP')
    );
    DECLARE @stale_noop_rows BIGINT = (
        SELECT COUNT_BIG(*) FROM @application_plan WHERE application_disposition = N'STALE_NO_OP'
    );
    DECLARE @reconciled_at_utc DATETIME2(3) = SYSUTCDATETIME();
    DECLARE @published_at_utc DATETIME2(3) = SYSUTCDATETIME();

    IF @candidate_rows <> @inserted_rows + @updated_rows + @reactivated_rows + @noop_rows
        THROW 51431, N'As contagens da aplicação não reconciliam o candidate set.', 1;

    IF @lease_expires_at_utc <= @published_at_utc
        THROW 51432, N'O lease expirou antes do commit de publicação.', 1;

    INSERT INTO recon.execution_reconciliation_result (
        execution_id, candidate_rows, inserted_rows, updated_rows, reactivated_rows,
        noop_rows, stale_noop_rows, reconciled_at_utc, published_at_utc
    ) VALUES (
        @execution_id, @candidate_rows, @inserted_rows, @updated_rows, @reactivated_rows,
        @noop_rows, @stale_noop_rows, @reconciled_at_utc, @published_at_utc
    );

    UPDATE ctl.execution_attempt
    SET current_state = N'RECONCILED',
        next_transition_sequence = next_transition_sequence + 1
    WHERE execution_id = @execution_id
      AND current_state = N'PROMOTED'
      AND next_transition_sequence = @next_transition_sequence;

    IF @@ROWCOUNT <> 1
        THROW 51433, N'O estado mudou antes da reconciliação.', 1;

    INSERT INTO ctl.execution_state_event (
        execution_id, transition_sequence, previous_state, next_state,
        reason_code, transitioned_at_utc
    ) VALUES (
        @execution_id, @next_transition_sequence, N'PROMOTED', N'RECONCILED',
        N'CANDIDATE_SET_RECONCILED', @reconciled_at_utc
    );

    UPDATE ctl.execution_partition
    SET current_state = N'RECONCILED'
    WHERE partition_id = @partition_id
      AND current_execution_id = @execution_id
      AND current_state = N'PROMOTED';

    IF @@ROWCOUNT <> 1
        THROW 51434, N'A partição deixou de ser corrente durante a reconciliação.', 1;

    UPDATE ctl.execution_attempt
    SET current_state = N'PUBLISHED',
        terminal_at_utc = @published_at_utc,
        next_transition_sequence = next_transition_sequence + 1
    WHERE execution_id = @execution_id
      AND current_state = N'RECONCILED'
      AND next_transition_sequence = @next_transition_sequence + 1;

    IF @@ROWCOUNT <> 1
        THROW 51435, N'O estado mudou antes da publicação.', 1;

    INSERT INTO ctl.execution_state_event (
        execution_id, transition_sequence, previous_state, next_state,
        reason_code, transitioned_at_utc
    ) VALUES (
        @execution_id, @next_transition_sequence + 1, N'RECONCILED', N'PUBLISHED',
        N'RECONCILIATION_PUBLISHED', @published_at_utc
    );

    UPDATE ctl.execution_partition
    SET current_state = N'PUBLISHED'
    WHERE partition_id = @partition_id
      AND current_execution_id = @execution_id
      AND current_state = N'RECONCILED';

    IF @@ROWCOUNT <> 1
        THROW 51436, N'A partição deixou de ser corrente durante a publicação.', 1;

    DECLARE @previous_published_execution_id UNIQUEIDENTIFIER;
    SELECT @previous_published_execution_id = pointer.published_execution_id
    FROM ctl.partition_publication_pointer AS pointer WITH (UPDLOCK, HOLDLOCK)
    WHERE pointer.partition_id = @partition_id;

    IF @previous_published_execution_id IS NULL
    BEGIN
        INSERT INTO ctl.partition_publication_pointer (
            partition_id, published_execution_id, published_at_utc
        ) VALUES (
            @partition_id, @execution_id, @published_at_utc
        );
    END
    ELSE
    BEGIN
        UPDATE ctl.partition_publication_pointer
        SET published_execution_id = @execution_id,
            published_at_utc = @published_at_utc
        WHERE partition_id = @partition_id
          AND published_execution_id = @previous_published_execution_id;

        IF @@ROWCOUNT <> 1
            THROW 51437, N'O pointer de publicação mudou durante o commit.', 1;
    END;

    DECLARE @incremental_frontier_before_utc DATETIME2(3) = NULL;
    DECLARE @incremental_frontier_after_utc DATETIME2(3) = NULL;
    DECLARE @watermark_last_partition_before BIGINT = NULL;
    DECLARE @watermark_last_partition_after BIGINT = NULL;

    IF @execution_mode COLLATE Latin1_General_100_BIN2 = N'INCREMENTAL'
    BEGIN
        SELECT
            @incremental_frontier_before_utc = watermark.contiguous_partition_end_utc,
            @watermark_last_partition_before = watermark.last_partition_id
        FROM ctl.incremental_publication_watermark AS watermark WITH (UPDLOCK, HOLDLOCK)
        WHERE watermark.environment_name = @environment_name
          AND watermark.source_instance = @source_instance
          AND watermark.tenant_scope = @tenant_scope
          AND watermark.entity_name = @entity_name;

        IF @incremental_frontier_before_utc IS NULL
            THROW 51438, N'Publicação incremental exige fronteira previamente registrada.', 1;

        ;WITH contiguous_publications AS (
            SELECT
                CAST(NULL AS BIGINT) AS partition_id,
                @incremental_frontier_before_utc AS contiguous_end_utc,
                CAST(0 AS INT) AS traversal_depth
            UNION ALL
            SELECT
                next_partition.partition_id,
                next_partition.partition_end_exclusive_utc,
                contiguous.traversal_depth + 1
            FROM contiguous_publications AS contiguous
            INNER JOIN ctl.execution_partition AS next_partition
                ON next_partition.environment_name = @environment_name
               AND next_partition.source_instance = @source_instance
               AND next_partition.tenant_scope = @tenant_scope
               AND next_partition.entity_name = @entity_name
               AND next_partition.execution_mode = N'INCREMENTAL'
               AND next_partition.partition_start_utc = contiguous.contiguous_end_utc
               AND next_partition.partition_end_exclusive_utc > contiguous.contiguous_end_utc
            INNER JOIN ctl.partition_publication_pointer AS next_pointer
                ON next_pointer.partition_id = next_partition.partition_id
            INNER JOIN ctl.execution_attempt AS published_attempt
                ON published_attempt.partition_id = next_partition.partition_id
               AND published_attempt.execution_id = next_pointer.published_execution_id
               AND published_attempt.current_state = N'PUBLISHED'
        )
        SELECT TOP (1)
            @incremental_frontier_after_utc = contiguous_end_utc,
            @watermark_last_partition_after =
                COALESCE(partition_id, @watermark_last_partition_before)
        FROM contiguous_publications
        ORDER BY contiguous_end_utc DESC, partition_id DESC
        OPTION (MAXRECURSION 32767);

        IF @incremental_frontier_after_utc > @incremental_frontier_before_utc
        BEGIN
            UPDATE ctl.incremental_publication_watermark
            SET contiguous_partition_end_utc = @incremental_frontier_after_utc,
                last_partition_id = @watermark_last_partition_after,
                advanced_at_utc = @published_at_utc
            WHERE environment_name = @environment_name
              AND source_instance = @source_instance
              AND tenant_scope = @tenant_scope
              AND entity_name = @entity_name
              AND contiguous_partition_end_utc = @incremental_frontier_before_utc;

            IF @@ROWCOUNT <> 1
                THROW 51439, N'A fronteira incremental mudou durante o commit.', 1;
        END;
    END;

    INSERT INTO ctl.execution_publication_event (
        execution_id, partition_id, previous_published_execution_id, published_at_utc,
        incremental_frontier_before_utc, incremental_frontier_after_utc,
        watermark_last_partition_id
    ) VALUES (
        @execution_id, @partition_id, @previous_published_execution_id, @published_at_utc,
        @incremental_frontier_before_utc, @incremental_frontier_after_utc,
        @watermark_last_partition_after
    );

    UPDATE ctl.execution_lease
    SET released_at_utc = @published_at_utc
    WHERE partition_id = @partition_id
      AND execution_id = @execution_id
      AND released_at_utc IS NULL
      AND expires_at_utc > SYSUTCDATETIME();

    IF @@ROWCOUNT <> 1
        THROW 51440, N'O lease expirou ou deixou de estar ativo durante a publicação.', 1;

    COMMIT TRANSACTION;

    SELECT
        @execution_id AS execution_id,
        @candidate_rows AS candidate_rows,
        @inserted_rows AS inserted_rows,
        @updated_rows AS updated_rows,
        @reactivated_rows AS reactivated_rows,
        @noop_rows AS noop_rows,
        @stale_noop_rows AS stale_noop_rows,
        @reconciled_at_utc AS reconciled_at_utc,
        @published_at_utc AS published_at_utc,
        @incremental_frontier_before_utc AS incremental_frontier_before_utc,
        @incremental_frontier_after_utc AS incremental_frontier_after_utc;
END;
GO

EXEC dbo.usp_publish_v2_procedure_grant N'stg', N'usp_stage_record', N'v2_runtime';
EXEC dbo.usp_publish_v2_procedure_grant N'core', N'usp_prepare_staged_execution', N'v2_runtime';
EXEC dbo.usp_publish_v2_procedure_grant N'core', N'usp_apply_reconcile_publish_execution', N'v2_runtime';

DENY INSERT, UPDATE, DELETE, SELECT ON SCHEMA::stg TO v2_runtime;
DENY INSERT, UPDATE, DELETE, SELECT ON SCHEMA::core TO v2_runtime;
DENY INSERT, UPDATE, DELETE, SELECT ON SCHEMA::recon TO v2_runtime;
GO
