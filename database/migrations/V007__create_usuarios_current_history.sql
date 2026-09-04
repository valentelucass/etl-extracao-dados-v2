-- V2-033: vertical shadow de Usuários sobre GraphQL individual(enabled=true).
-- O envelope genérico usa hash técnico estável da identidade; name/presença/current/history
-- pertencem a esta vertical. observation_order_at_utc é ordem técnica, nunca source freshness,
-- updatedAt, watermark ou prova de completude.

SET XACT_ABORT ON;
SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;

CREATE UNIQUE INDEX UX_stg_execution_record_usuario_binding
    ON stg.execution_record (stage_record_id, execution_id, source_key);
GO

CREATE TABLE stg.usuario_record (
    stage_record_id BIGINT NOT NULL,
    execution_id UNIQUEIDENTIFIER NOT NULL,
    source_key NVARCHAR(256) COLLATE Latin1_General_100_BIN2 NOT NULL,
    source_key_wire_type NVARCHAR(16) COLLATE Latin1_General_100_BIN2 NOT NULL,
    name_presence NVARCHAR(8) COLLATE Latin1_General_100_BIN2 NOT NULL,
    usuario_name NVARCHAR(255) COLLATE Latin1_General_100_BIN2 NULL,
    attribute_fingerprint_version NVARCHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL,
    attribute_hash CHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL,
    presence_fingerprint_version NVARCHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL,
    name_presence_hash CHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL,
    CONSTRAINT PK_stg_usuario_record PRIMARY KEY CLUSTERED (stage_record_id),
    CONSTRAINT FK_stg_usuario_record_stage FOREIGN KEY (
        stage_record_id, execution_id, source_key
    ) REFERENCES stg.execution_record (stage_record_id, execution_id, source_key),
    CONSTRAINT UQ_stg_usuario_record_execution_stage UNIQUE (execution_id, stage_record_id),
    CONSTRAINT CK_stg_usuario_record_wire_type CHECK (
        source_key_wire_type IN (N'INTEGER', N'STRING')
    ),
    CONSTRAINT CK_stg_usuario_record_name_presence CHECK (
        name_presence IN (N'ABSENT', N'NULL', N'VALUE')
        AND (
            (name_presence IN (N'ABSENT', N'NULL') AND usuario_name IS NULL)
            OR (name_presence = N'VALUE' AND usuario_name IS NOT NULL)
        )
    ),
    CONSTRAINT CK_stg_usuario_record_fingerprints CHECK (
        attribute_fingerprint_version = N'usuarios-attributes-v1'
        AND presence_fingerprint_version = N'usuarios-name-presence-v1'
        AND LEN(attribute_hash) = 64
        AND attribute_hash NOT LIKE '%[^0-9A-Fa-f]%'
        AND LEN(name_presence_hash) = 64
        AND name_presence_hash NOT LIKE '%[^0-9A-Fa-f]%'
    )
);
GO

CREATE INDEX IX_stg_usuario_record_execution_source
    ON stg.usuario_record (execution_id, source_key, attribute_hash, name_presence_hash)
    INCLUDE (stage_record_id, source_key_wire_type, name_presence, usuario_name);
GO

CREATE TABLE recon.usuario_quarantine (
    usuario_quarantine_id BIGINT IDENTITY(1, 1) NOT NULL,
    execution_id UNIQUEIDENTIFIER NOT NULL,
    source_key NVARCHAR(256) COLLATE Latin1_General_100_BIN2 NOT NULL,
    reason_code NVARCHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL,
    distinct_attribute_states BIGINT NOT NULL,
    quarantined_at_utc DATETIME2(3) NOT NULL,
    CONSTRAINT PK_recon_usuario_quarantine PRIMARY KEY CLUSTERED (usuario_quarantine_id),
    CONSTRAINT UQ_recon_usuario_quarantine_execution_source UNIQUE (execution_id, source_key),
    CONSTRAINT FK_recon_usuario_quarantine_execution FOREIGN KEY (execution_id)
        REFERENCES ctl.execution_attempt (execution_id),
    CONSTRAINT CK_recon_usuario_quarantine_values CHECK (
        reason_code = N'CONFLICTING_USER_ATTRIBUTES'
        AND distinct_attribute_states > 1
    )
);
GO

CREATE TABLE ctl.usuario_promotion_result (
    execution_id UNIQUEIDENTIFIER NOT NULL,
    generic_candidate_rows BIGINT NOT NULL,
    typed_candidate_rows BIGINT NOT NULL,
    conflicting_root_keys BIGINT NOT NULL,
    generic_quarantine_rows BIGINT NOT NULL,
    typed_stage_rows BIGINT NOT NULL,
    typed_stage_bytes BIGINT NOT NULL,
    validation_state NVARCHAR(16) COLLATE Latin1_General_100_BIN2 NOT NULL,
    validated_at_utc DATETIME2(3) NOT NULL,
    CONSTRAINT PK_ctl_usuario_promotion_result PRIMARY KEY CLUSTERED (execution_id),
    CONSTRAINT FK_ctl_usuario_promotion_result_generic FOREIGN KEY (execution_id)
        REFERENCES ctl.execution_promotion_result (execution_id),
    CONSTRAINT CK_ctl_usuario_promotion_result_counts CHECK (
        generic_candidate_rows >= 0
        AND typed_candidate_rows >= 0
        AND conflicting_root_keys >= 0
        AND generic_quarantine_rows >= 0
        AND typed_stage_rows >= typed_candidate_rows
        AND typed_stage_bytes >= 0
        AND validation_state IN (N'PASSED', N'BLOCKED')
        AND (
            validation_state = N'PASSED'
            AND generic_candidate_rows = typed_candidate_rows
            AND conflicting_root_keys = 0
            AND generic_quarantine_rows = 0
            OR validation_state = N'BLOCKED'
        )
    )
);
GO

CREATE UNIQUE INDEX UX_core_entity_record_state_usuario_binding
    ON core.entity_record_state (
        record_state_id, environment_name, source_instance, tenant_scope, entity_name, source_key
    );
GO

CREATE TABLE core.usuario (
    usuario_id BIGINT IDENTITY(1, 1) NOT NULL,
    record_state_id BIGINT NOT NULL,
    environment_name NVARCHAR(32) COLLATE Latin1_General_100_BIN2 NOT NULL,
    source_instance NVARCHAR(128) COLLATE Latin1_General_100_BIN2 NOT NULL,
    tenant_scope NVARCHAR(128) COLLATE Latin1_General_100_BIN2 NOT NULL,
    entity_name NVARCHAR(128) COLLATE Latin1_General_100_BIN2 NOT NULL,
    source_key NVARCHAR(256) COLLATE Latin1_General_100_BIN2 NOT NULL,
    source_key_wire_type NVARCHAR(16) COLLATE Latin1_General_100_BIN2 NOT NULL,
    name_presence NVARCHAR(8) COLLATE Latin1_General_100_BIN2 NOT NULL,
    usuario_name NVARCHAR(255) COLLATE Latin1_General_100_BIN2 NULL,
    attribute_fingerprint_version NVARCHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL,
    attribute_hash CHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL,
    state_fingerprint_version NVARCHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL,
    state_hash CHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL,
    active BIT NOT NULL,
    first_seen_execution_id UNIQUEIDENTIFIER NOT NULL,
    last_seen_execution_id UNIQUEIDENTIFIER NOT NULL,
    last_changed_execution_id UNIQUEIDENTIFIER NOT NULL,
    first_seen_at_utc DATETIME2(3) NOT NULL,
    last_seen_at_utc DATETIME2(3) NOT NULL,
    last_changed_at_utc DATETIME2(3) NOT NULL,
    observation_order_at_utc DATETIME2(3) NOT NULL,
    observation_order_execution_id UNIQUEIDENTIFIER NOT NULL,
    CONSTRAINT PK_core_usuario PRIMARY KEY CLUSTERED (usuario_id),
    CONSTRAINT UQ_core_usuario_record_state UNIQUE (record_state_id),
    CONSTRAINT UQ_core_usuario_source UNIQUE (
        environment_name, source_instance, tenant_scope, entity_name, source_key
    ),
    CONSTRAINT FK_core_usuario_record_binding FOREIGN KEY (
        record_state_id, environment_name, source_instance, tenant_scope, entity_name, source_key
    ) REFERENCES core.entity_record_state (
        record_state_id, environment_name, source_instance, tenant_scope, entity_name, source_key
    ),
    CONSTRAINT FK_core_usuario_first_execution FOREIGN KEY (first_seen_execution_id)
        REFERENCES ctl.execution_attempt (execution_id),
    CONSTRAINT FK_core_usuario_last_execution FOREIGN KEY (last_seen_execution_id)
        REFERENCES ctl.execution_attempt (execution_id),
    CONSTRAINT FK_core_usuario_changed_execution FOREIGN KEY (last_changed_execution_id)
        REFERENCES ctl.execution_attempt (execution_id),
    CONSTRAINT FK_core_usuario_order_execution FOREIGN KEY (observation_order_execution_id)
        REFERENCES ctl.execution_attempt (execution_id),
    CONSTRAINT CK_core_usuario_entity CHECK (entity_name = N'usuarios'),
    CONSTRAINT CK_core_usuario_wire_type CHECK (
        source_key_wire_type IN (N'INTEGER', N'STRING')
    ),
    CONSTRAINT CK_core_usuario_name_presence CHECK (
        name_presence IN (N'ABSENT', N'NULL', N'VALUE')
        AND (
            (name_presence IN (N'ABSENT', N'NULL') AND usuario_name IS NULL)
            OR (name_presence = N'VALUE' AND usuario_name IS NOT NULL)
        )
    ),
    CONSTRAINT CK_core_usuario_fingerprints CHECK (
        attribute_fingerprint_version = N'usuarios-attributes-v1'
        AND state_fingerprint_version = N'usuarios-state-v1'
        AND LEN(attribute_hash) = 64 AND attribute_hash NOT LIKE '%[^0-9A-Fa-f]%'
        AND LEN(state_hash) = 64 AND state_hash NOT LIKE '%[^0-9A-Fa-f]%'
    ),
    CONSTRAINT CK_core_usuario_times CHECK (
        first_seen_at_utc <= last_seen_at_utc
        AND first_seen_at_utc <= last_changed_at_utc
    )
);
GO

CREATE INDEX IX_core_usuario_active
    ON core.usuario (active, usuario_id)
    INCLUDE (
        attribute_hash, state_hash, observation_order_at_utc,
        observation_order_execution_id, last_seen_execution_id
    );
GO

CREATE TABLE core.usuario_history (
    usuario_history_id BIGINT IDENTITY(1, 1) NOT NULL,
    usuario_id BIGINT NOT NULL,
    execution_id UNIQUEIDENTIFIER NOT NULL,
    change_kind NVARCHAR(16) COLLATE Latin1_General_100_BIN2 NOT NULL,
    name_presence NVARCHAR(8) COLLATE Latin1_General_100_BIN2 NOT NULL,
    usuario_name NVARCHAR(255) COLLATE Latin1_General_100_BIN2 NULL,
    attribute_fingerprint_version NVARCHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL,
    attribute_hash CHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL,
    state_fingerprint_version NVARCHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL,
    state_hash CHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL,
    active BIT NOT NULL,
    observation_order_at_utc DATETIME2(3) NOT NULL,
    observation_order_execution_id UNIQUEIDENTIFIER NOT NULL,
    changed_at_utc DATETIME2(3) NOT NULL,
    CONSTRAINT PK_core_usuario_history PRIMARY KEY CLUSTERED (usuario_history_id),
    CONSTRAINT UQ_core_usuario_history_execution UNIQUE (execution_id, usuario_id),
    CONSTRAINT FK_core_usuario_history_usuario FOREIGN KEY (usuario_id)
        REFERENCES core.usuario (usuario_id),
    CONSTRAINT FK_core_usuario_history_execution FOREIGN KEY (execution_id)
        REFERENCES ctl.execution_attempt (execution_id),
    CONSTRAINT FK_core_usuario_history_order_execution
        FOREIGN KEY (observation_order_execution_id)
        REFERENCES ctl.execution_attempt (execution_id),
    CONSTRAINT CK_core_usuario_history_change CHECK (
        change_kind IN (N'INSERTED', N'UPDATED', N'REACTIVATED')
    ),
    CONSTRAINT CK_core_usuario_history_name_presence CHECK (
        name_presence IN (N'ABSENT', N'NULL', N'VALUE')
        AND (
            (name_presence IN (N'ABSENT', N'NULL') AND usuario_name IS NULL)
            OR (name_presence = N'VALUE' AND usuario_name IS NOT NULL)
        )
    ),
    CONSTRAINT CK_core_usuario_history_fingerprints CHECK (
        attribute_fingerprint_version = N'usuarios-attributes-v1'
        AND state_fingerprint_version = N'usuarios-state-v1'
        AND LEN(attribute_hash) = 64 AND attribute_hash NOT LIKE '%[^0-9A-Fa-f]%'
        AND LEN(state_hash) = 64 AND state_hash NOT LIKE '%[^0-9A-Fa-f]%'
        AND active = 1
    )
);
GO

CREATE INDEX IX_core_usuario_history_timeline
    ON core.usuario_history (
        usuario_id, observation_order_at_utc DESC,
        observation_order_execution_id DESC, usuario_history_id DESC
    ) INCLUDE (execution_id, change_kind, state_hash);
GO

CREATE TABLE recon.usuario_apply_authorization (
    execution_id UNIQUEIDENTIFIER NOT NULL,
    authorization_state NVARCHAR(16) COLLATE Latin1_General_100_BIN2 NOT NULL,
    candidate_rows BIGINT NOT NULL,
    authorized_at_utc DATETIME2(3) NOT NULL,
    applied_at_utc DATETIME2(3) NULL,
    CONSTRAINT PK_recon_usuario_apply_authorization PRIMARY KEY CLUSTERED (execution_id),
    CONSTRAINT FK_recon_usuario_apply_authorization_validation FOREIGN KEY (execution_id)
        REFERENCES ctl.usuario_promotion_result (execution_id),
    CONSTRAINT CK_recon_usuario_apply_authorization_values CHECK (
        candidate_rows >= 0
        AND (
            authorization_state = N'APPLYING' AND applied_at_utc IS NULL
            OR authorization_state = N'APPLIED' AND applied_at_utc IS NOT NULL
        )
    )
);
GO

CREATE TABLE recon.usuario_candidate_application (
    execution_id UNIQUEIDENTIFIER NOT NULL,
    source_key NVARCHAR(256) COLLATE Latin1_General_100_BIN2 NOT NULL,
    usuario_id BIGINT NOT NULL,
    application_disposition NVARCHAR(24) COLLATE Latin1_General_100_BIN2 NOT NULL,
    result_attribute_hash CHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL,
    result_state_hash CHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL,
    result_active BIT NOT NULL,
    observation_order_at_utc DATETIME2(3) NOT NULL,
    observation_order_execution_id UNIQUEIDENTIFIER NOT NULL,
    applied_at_utc DATETIME2(3) NOT NULL,
    CONSTRAINT PK_recon_usuario_candidate_application
        PRIMARY KEY CLUSTERED (execution_id, source_key),
    CONSTRAINT UQ_recon_usuario_candidate_application_usuario UNIQUE (execution_id, usuario_id),
    CONSTRAINT FK_recon_usuario_candidate_application_execution FOREIGN KEY (execution_id)
        REFERENCES ctl.execution_attempt (execution_id),
    CONSTRAINT FK_recon_usuario_candidate_application_usuario FOREIGN KEY (usuario_id)
        REFERENCES core.usuario (usuario_id),
    CONSTRAINT FK_recon_usuario_candidate_application_order_execution
        FOREIGN KEY (observation_order_execution_id)
        REFERENCES ctl.execution_attempt (execution_id),
    CONSTRAINT CK_recon_usuario_candidate_application_values CHECK (
        application_disposition IN (
            N'INSERTED', N'UPDATED', N'REACTIVATED', N'NO_OP', N'STALE_NO_OP'
        )
        AND LEN(result_attribute_hash) = 64
        AND result_attribute_hash NOT LIKE '%[^0-9A-Fa-f]%'
        AND LEN(result_state_hash) = 64
        AND result_state_hash NOT LIKE '%[^0-9A-Fa-f]%'
        AND result_active = 1
    )
);
GO

CREATE TABLE recon.usuario_reconciliation_result (
    execution_id UNIQUEIDENTIFIER NOT NULL,
    candidate_rows BIGINT NOT NULL,
    inserted_rows BIGINT NOT NULL,
    updated_rows BIGINT NOT NULL,
    reactivated_rows BIGINT NOT NULL,
    noop_rows BIGINT NOT NULL,
    stale_noop_rows BIGINT NOT NULL,
    history_rows BIGINT NOT NULL,
    reconciled_at_utc DATETIME2(3) NOT NULL,
    published_at_utc DATETIME2(3) NOT NULL,
    CONSTRAINT PK_recon_usuario_reconciliation_result PRIMARY KEY CLUSTERED (execution_id),
    CONSTRAINT FK_recon_usuario_reconciliation_result_authorization FOREIGN KEY (execution_id)
        REFERENCES recon.usuario_apply_authorization (execution_id),
    CONSTRAINT CK_recon_usuario_reconciliation_result_counts CHECK (
        candidate_rows = inserted_rows + updated_rows + reactivated_rows + noop_rows
        AND stale_noop_rows <= noop_rows
        AND history_rows = inserted_rows + updated_rows + reactivated_rows
        AND candidate_rows >= 0 AND inserted_rows >= 0 AND updated_rows >= 0
        AND reactivated_rows >= 0 AND noop_rows >= 0 AND stale_noop_rows >= 0
        AND reconciled_at_utc <= published_at_utc
    )
);
GO

CREATE TABLE recon.usuario_stage_disposal_evidence (
    plan_id UNIQUEIDENTIFIER NOT NULL,
    execution_id UNIQUEIDENTIFIER NOT NULL,
    typed_rows BIGINT NOT NULL,
    name_bytes BIGINT NOT NULL,
    disposed_at_utc DATETIME2(3) NOT NULL,
    CONSTRAINT PK_recon_usuario_stage_disposal_evidence
        PRIMARY KEY CLUSTERED (plan_id, execution_id),
    CONSTRAINT FK_recon_usuario_stage_disposal_manifest FOREIGN KEY (plan_id)
        REFERENCES recon.staging_lifecycle_archive_manifest (plan_id),
    CONSTRAINT FK_recon_usuario_stage_disposal_item FOREIGN KEY (plan_id, execution_id)
        REFERENCES ctl.staging_lifecycle_plan_item (plan_id, execution_id),
    CONSTRAINT CK_recon_usuario_stage_disposal_counts CHECK (
        typed_rows > 0 AND name_bytes >= 0
    )
);
GO

-- O lifecycle preserva uma cópia tipada minimizada e atestada. O nome bruto não é duplicado no
-- archive: current/history já são o estado durável, enquanto presença/hashes permitem verificar a
-- linha descartada e o byte budget inclui exatamente a representação tipada arquivada.
CREATE TABLE recon.usuario_stage_archive (
    plan_id UNIQUEIDENTIFIER NOT NULL,
    stage_record_id BIGINT NOT NULL,
    execution_id UNIQUEIDENTIFIER NOT NULL,
    source_key_wire_type NVARCHAR(16) COLLATE Latin1_General_100_BIN2 NOT NULL,
    name_presence NVARCHAR(8) COLLATE Latin1_General_100_BIN2 NOT NULL,
    attribute_fingerprint_version NVARCHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL,
    attribute_hash CHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL,
    presence_fingerprint_version NVARCHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL,
    name_presence_hash CHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL,
    discarded_name_bytes BIGINT NOT NULL,
    archive_row_attestation_version NVARCHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL,
    archive_row_attestation BINARY(32) NOT NULL,
    CONSTRAINT PK_recon_usuario_stage_archive PRIMARY KEY CLUSTERED (stage_record_id),
    CONSTRAINT UQ_recon_usuario_stage_archive_plan_stage UNIQUE (plan_id, stage_record_id),
    CONSTRAINT FK_recon_usuario_stage_archive_generic FOREIGN KEY (plan_id, stage_record_id)
        REFERENCES recon.staging_record_archive (plan_id, stage_record_id),
    CONSTRAINT FK_recon_usuario_stage_archive_plan_item FOREIGN KEY (plan_id, execution_id)
        REFERENCES ctl.staging_lifecycle_plan_item (plan_id, execution_id),
    CONSTRAINT CK_recon_usuario_stage_archive_values CHECK (
        source_key_wire_type IN (N'INTEGER', N'STRING')
        AND name_presence IN (N'ABSENT', N'NULL', N'VALUE')
        AND attribute_fingerprint_version = N'usuarios-attributes-v1'
        AND presence_fingerprint_version = N'usuarios-name-presence-v1'
        AND LEN(attribute_hash) = 64
        AND attribute_hash NOT LIKE '%[^0-9A-Fa-f]%'
        AND LEN(name_presence_hash) = 64
        AND name_presence_hash NOT LIKE '%[^0-9A-Fa-f]%'
        AND discarded_name_bytes BETWEEN 0 AND 510
        AND archive_row_attestation_version = N'archive-row-json-v1'
    )
);
GO

CREATE INDEX IX_recon_usuario_stage_archive_execution
    ON recon.usuario_stage_archive (execution_id, stage_record_id);
GO

CREATE TABLE recon.usuario_stage_restore (
    restore_id UNIQUEIDENTIFIER NOT NULL,
    stage_record_id BIGINT NOT NULL,
    execution_id UNIQUEIDENTIFIER NOT NULL,
    source_key_wire_type NVARCHAR(16) COLLATE Latin1_General_100_BIN2 NOT NULL,
    name_presence NVARCHAR(8) COLLATE Latin1_General_100_BIN2 NOT NULL,
    attribute_fingerprint_version NVARCHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL,
    attribute_hash CHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL,
    presence_fingerprint_version NVARCHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL,
    name_presence_hash CHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL,
    discarded_name_bytes BIGINT NOT NULL,
    archive_row_attestation_version NVARCHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL,
    archive_row_attestation BINARY(32) NOT NULL,
    CONSTRAINT PK_recon_usuario_stage_restore
        PRIMARY KEY CLUSTERED (restore_id, stage_record_id),
    CONSTRAINT FK_recon_usuario_stage_restore_generic FOREIGN KEY (restore_id, stage_record_id)
        REFERENCES recon.staging_restore_record (restore_id, stage_record_id),
    CONSTRAINT CK_recon_usuario_stage_restore_values CHECK (
        source_key_wire_type IN (N'INTEGER', N'STRING')
        AND name_presence IN (N'ABSENT', N'NULL', N'VALUE')
        AND discarded_name_bytes BETWEEN 0 AND 510
        AND archive_row_attestation_version = N'archive-row-json-v1'
    )
);
GO

CREATE OR ALTER PROCEDURE stg.usp_stage_usuario_record
    @execution_id UNIQUEIDENTIFIER,
    @input_batch_number INT,
    @input_record_ordinal INT,
    @source_key NVARCHAR(MAX),
    @source_key_wire_type NVARCHAR(MAX),
    @name_presence NVARCHAR(MAX),
    @usuario_name NVARCHAR(MAX),
    @validation_disposition NVARCHAR(MAX),
    @quarantine_reason_code NVARCHAR(MAX),
    @observed_at_utc DATETIME2(3)
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;

    IF @input_record_ordinal IS NULL OR @input_record_ordinal NOT BETWEEN 1 AND 20
        THROW 51700, N'Ordinal vertical de Usuários deve estar entre 1 e 20.', 1;

    IF DATALENGTH(@source_key) > 512
        OR DATALENGTH(@source_key_wire_type) > 32
        OR DATALENGTH(@name_presence) > 16
        OR DATALENGTH(@usuario_name) > 510
        OR DATALENGTH(@validation_disposition) > 32
        OR DATALENGTH(@quarantine_reason_code) > 128
        THROW 51700, N'Registro tipado de Usuários excede o limite textual.', 1;

    SET @source_key_wire_type = NULLIF(LTRIM(RTRIM(@source_key_wire_type)), N'');
    SET @name_presence = NULLIF(LTRIM(RTRIM(@name_presence)), N'');
    SET @validation_disposition = NULLIF(LTRIM(RTRIM(@validation_disposition)), N'');

    DECLARE @integer_value NVARCHAR(256);
    DECLARE @integer_digits NVARCHAR(256);
    IF @source_key_wire_type COLLATE Latin1_General_100_BIN2 = N'INTEGER'
    BEGIN
        IF LEFT(@source_key, 8) COLLATE Latin1_General_100_BIN2 <> N'INTEGER:'
            THROW 51700, N'Source key tipada de Usuários é inválida.', 1;
        SET @integer_value = SUBSTRING(@source_key, 9, 256);
        SET @integer_digits = CASE WHEN LEFT(@integer_value, 1) = N'-'
                                   THEN SUBSTRING(@integer_value, 2, 256)
                                   ELSE @integer_value END;
        IF NULLIF(@integer_digits, N'') IS NULL
            OR @integer_digits COLLATE Latin1_General_100_BIN2 LIKE N'%[^0-9]%'
            OR (LEN(@integer_digits) > 1 AND LEFT(@integer_digits, 1) = N'0')
            OR @integer_value = N'-0'
            THROW 51700, N'Source key tipada de Usuários é inválida.', 1;
    END
    ELSE IF @source_key_wire_type COLLATE Latin1_General_100_BIN2 = N'STRING'
    BEGIN
        IF LEFT(@source_key, 7) COLLATE Latin1_General_100_BIN2 <> N'STRING:'
            OR NULLIF(SUBSTRING(@source_key, 8, 256), N'') IS NULL
            OR DATALENGTH(SUBSTRING(@source_key, 8, 256))
                <> DATALENGTH(LTRIM(RTRIM(SUBSTRING(@source_key, 8, 256))))
            THROW 51700, N'Source key tipada de Usuários é inválida.', 1;
    END
    ELSE IF @source_key IS NOT NULL OR @source_key_wire_type IS NOT NULL
        THROW 51700, N'Source key tipada de Usuários é inválida.', 1;

    IF @validation_disposition COLLATE Latin1_General_100_BIN2 = N'VALID'
       AND (
           @source_key IS NULL
           OR @source_key_wire_type IS NULL
           OR @name_presence COLLATE Latin1_General_100_BIN2
                NOT IN (N'ABSENT', N'NULL', N'VALUE')
           OR @name_presence IN (N'ABSENT', N'NULL') AND @usuario_name IS NOT NULL
           OR @name_presence = N'VALUE' AND @usuario_name IS NULL
           OR @quarantine_reason_code IS NOT NULL
       )
        THROW 51700, N'Registro tipado de Usuários é inválido.', 1;

    IF @validation_disposition COLLATE Latin1_General_100_BIN2 NOT IN (N'VALID', N'QUARANTINE')
        OR @validation_disposition = N'QUARANTINE' AND @quarantine_reason_code IS NULL
        OR @observed_at_utc IS NULL
        THROW 51700, N'Registro tipado de Usuários é inválido.', 1;

    IF EXISTS (
        SELECT 1
        FROM (VALUES
            (0), (1), (2), (3), (4), (5), (6), (7), (8), (9), (10), (11), (12), (13),
            (14), (15), (16), (17), (18), (19), (20), (21), (22), (23), (24), (25), (26),
            (27), (28), (29), (30), (31), (127),
            (128), (129), (130), (131), (132), (133), (134), (135), (136), (137),
            (138), (139), (140), (141), (142), (143), (144), (145), (146), (147),
            (148), (149), (150), (151), (152), (153), (154), (155), (156), (157),
            (158), (159)
        ) AS forbidden(code_point)
        WHERE CHARINDEX(NCHAR(forbidden.code_point), COALESCE(@source_key, N'')) > 0
           OR CHARINDEX(NCHAR(forbidden.code_point), COALESCE(@usuario_name, N'')) > 0
    )
        THROW 51700, N'Registro tipado de Usuários contém controle inválido.', 1;

    DECLARE @identity_row_hash CHAR(64) = NULL;
    DECLARE @identity_presence_hash CHAR(64) = NULL;
    DECLARE @attribute_hash CHAR(64) = NULL;
    DECLARE @name_presence_hash CHAR(64) = NULL;
    IF @validation_disposition = N'VALID'
    BEGIN
        SET @identity_row_hash = LOWER(CONVERT(CHAR(64), HASHBYTES(
            'SHA2_256', CONVERT(VARBINARY(MAX), CONCAT(
                N'usuarios-identity-envelope-v1|', DATALENGTH(@source_key), N'|', @source_key
            ))
        ), 2));
        SET @identity_presence_hash = LOWER(CONVERT(CHAR(64), HASHBYTES(
            'SHA2_256', CONVERT(VARBINARY(MAX), N'usuarios-identity-presence-v1')
        ), 2));
        SET @attribute_hash = LOWER(CONVERT(CHAR(64), HASHBYTES(
            'SHA2_256', CONVERT(VARBINARY(MAX), CONCAT(
                N'usuarios-attributes-v1|', @name_presence, N'|',
                COALESCE(CONVERT(NVARCHAR(20), DATALENGTH(@usuario_name)), N'NULL'),
                N'|', COALESCE(@usuario_name, N'')
            ))
        ), 2));
        SET @name_presence_hash = LOWER(CONVERT(CHAR(64), HASHBYTES(
            'SHA2_256', CONVERT(VARBINARY(MAX), CONCAT(
                N'usuarios-name-presence-v1|', @name_presence
            ))
        ), 2));
    END;

    BEGIN TRANSACTION;

    -- O mesmo row lock usado pelo planner/archive fecha a corrida generic-only -> sidecar tardio.
    -- Retry terminal é permitido somente quando o sidecar tipado já existe e será comparado.
    DECLARE @entity_name NVARCHAR(128);
    SELECT @entity_name = partition.entity_name
    FROM ctl.execution_attempt AS attempt WITH (UPDLOCK, HOLDLOCK)
    INNER JOIN ctl.execution_partition AS partition WITH (HOLDLOCK)
        ON partition.partition_id = attempt.partition_id
    WHERE attempt.execution_id = @execution_id;

    IF @entity_name IS NULL
       OR @entity_name COLLATE Latin1_General_100_BIN2 <> N'usuarios'
        THROW 51701, N'A execução não pertence à vertical de Usuários.', 1;

    DECLARE @preexisting_stage_record_id BIGINT;
    SELECT @preexisting_stage_record_id = persisted.stage_record_id
    FROM stg.execution_record AS persisted WITH (UPDLOCK, HOLDLOCK)
    WHERE persisted.execution_id = @execution_id
      AND persisted.input_batch_number = @input_batch_number
      AND persisted.input_record_ordinal = @input_record_ordinal;

    IF @validation_disposition = N'VALID'
       AND @preexisting_stage_record_id IS NOT NULL
       AND NOT EXISTS (
           SELECT 1
           FROM stg.usuario_record AS typed WITH (UPDLOCK, HOLDLOCK)
           WHERE typed.stage_record_id = @preexisting_stage_record_id
       )
        THROW 51703, N'Registro genérico preexistente não aceita sidecar tardio.', 1;

    DECLARE @identity_row_fingerprint_version NVARCHAR(128) =
        CASE WHEN @validation_disposition = N'VALID'
             THEN N'usuarios-identity-envelope-v1' END;
    DECLARE @identity_presence_fingerprint_version NVARCHAR(128) =
        CASE WHEN @validation_disposition = N'VALID'
             THEN N'usuarios-identity-presence-v1' END;

    EXEC stg.usp_stage_record
        @execution_id = @execution_id,
        @input_batch_number = @input_batch_number,
        @input_record_ordinal = @input_record_ordinal,
        @source_key = @source_key,
        @row_fingerprint_version = @identity_row_fingerprint_version,
        @source_row_hash = @identity_row_hash,
        @presence_fingerprint_version = @identity_presence_fingerprint_version,
        @presence_fingerprint = @identity_presence_hash,
        @source_freshness_at_utc = NULL,
        @validation_disposition = @validation_disposition,
        @quarantine_reason_code = @quarantine_reason_code,
        @staged_at_utc = @observed_at_utc;

    IF @validation_disposition = N'QUARANTINE'
    BEGIN
        COMMIT TRANSACTION;
        RETURN;
    END;

    DECLARE @stage_record_id BIGINT;
    SELECT @stage_record_id = stage_record_id
    FROM stg.execution_record WITH (UPDLOCK, HOLDLOCK)
    WHERE execution_id = @execution_id
      AND input_batch_number = @input_batch_number
      AND input_record_ordinal = @input_record_ordinal;

    IF EXISTS (
        SELECT 1 FROM stg.usuario_record WITH (UPDLOCK, HOLDLOCK)
        WHERE stage_record_id = @stage_record_id
          AND (
              execution_id <> @execution_id OR source_key <> @source_key
              OR source_key_wire_type <> @source_key_wire_type
              OR name_presence <> @name_presence
              OR (usuario_name <> @usuario_name)
              OR (usuario_name IS NULL AND @usuario_name IS NOT NULL)
              OR (usuario_name IS NOT NULL AND @usuario_name IS NULL)
              OR attribute_hash <> @attribute_hash
              OR name_presence_hash <> @name_presence_hash
          )
    )
        THROW 51702, N'Retry tipado de Usuários possui conteúdo divergente.', 1;

    IF NOT EXISTS (
        SELECT 1 FROM stg.usuario_record WITH (UPDLOCK, HOLDLOCK)
        WHERE stage_record_id = @stage_record_id
    )
        INSERT INTO stg.usuario_record (
            stage_record_id, execution_id, source_key, source_key_wire_type,
            name_presence, usuario_name, attribute_fingerprint_version, attribute_hash,
            presence_fingerprint_version, name_presence_hash
        ) VALUES (
            @stage_record_id, @execution_id, @source_key, @source_key_wire_type,
            @name_presence, @usuario_name, N'usuarios-attributes-v1', @attribute_hash,
            N'usuarios-name-presence-v1', @name_presence_hash
        );

    COMMIT TRANSACTION;
END;
GO

CREATE OR ALTER TRIGGER ctl.trg_usuario_prepare_candidate_set
ON ctl.execution_promotion_result
AFTER INSERT
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;

    ;WITH relevant AS (
        SELECT inserted.execution_id, inserted.candidate_rows,
               inserted.quarantined_stage_rows
        FROM inserted
        INNER JOIN ctl.execution_attempt AS attempt
            ON attempt.execution_id = inserted.execution_id
        INNER JOIN ctl.execution_partition AS partition
            ON partition.partition_id = attempt.partition_id
        WHERE partition.entity_name = N'usuarios'
    ), conflicting AS (
        SELECT typed.execution_id, typed.source_key,
               COUNT_BIG(DISTINCT CONCAT(typed.attribute_hash, N'|', typed.name_presence_hash))
                    AS distinct_attribute_states
        FROM stg.usuario_record AS typed
        INNER JOIN relevant ON relevant.execution_id = typed.execution_id
        GROUP BY typed.execution_id, typed.source_key
        HAVING MIN(CONCAT(typed.attribute_hash, N'|', typed.name_presence_hash))
            <> MAX(CONCAT(typed.attribute_hash, N'|', typed.name_presence_hash))
    )
    INSERT INTO recon.usuario_quarantine (
        execution_id, source_key, reason_code, distinct_attribute_states, quarantined_at_utc
    )
    SELECT execution_id, source_key, N'CONFLICTING_USER_ATTRIBUTES',
           distinct_attribute_states, SYSUTCDATETIME()
    FROM conflicting;

    INSERT INTO ctl.usuario_promotion_result (
        execution_id, generic_candidate_rows, typed_candidate_rows, conflicting_root_keys,
        generic_quarantine_rows, typed_stage_rows, typed_stage_bytes,
        validation_state, validated_at_utc
    )
    SELECT relevant.execution_id, relevant.candidate_rows,
           COALESCE(typed_candidate.typed_candidate_rows, 0),
           COALESCE(conflict.conflicting_root_keys, 0),
           relevant.quarantined_stage_rows,
           COALESCE(typed_stage.typed_stage_rows, 0),
           COALESCE(typed_stage.typed_stage_bytes, 0),
           CASE WHEN relevant.quarantined_stage_rows = 0
                      AND COALESCE(conflict.conflicting_root_keys, 0) = 0
                      AND relevant.candidate_rows
                          = COALESCE(typed_candidate.typed_candidate_rows, 0)
                THEN N'PASSED' ELSE N'BLOCKED' END,
           SYSUTCDATETIME()
    FROM (
        SELECT inserted.execution_id, inserted.candidate_rows,
               inserted.quarantined_stage_rows
        FROM inserted
        INNER JOIN ctl.execution_attempt AS attempt
            ON attempt.execution_id = inserted.execution_id
        INNER JOIN ctl.execution_partition AS partition
            ON partition.partition_id = attempt.partition_id
        WHERE partition.entity_name = N'usuarios'
    ) AS relevant
    OUTER APPLY (
        SELECT COUNT_BIG(*) AS typed_candidate_rows
        FROM stg.execution_candidate AS candidate
        INNER JOIN stg.usuario_record AS typed
            ON typed.stage_record_id = candidate.winner_stage_record_id
           AND typed.execution_id = candidate.execution_id
           AND typed.source_key = candidate.source_key
        WHERE candidate.execution_id = relevant.execution_id
    ) AS typed_candidate
    OUTER APPLY (
        SELECT COUNT_BIG(*) AS conflicting_root_keys
        FROM recon.usuario_quarantine AS quarantine
        WHERE quarantine.execution_id = relevant.execution_id
    ) AS conflict
    OUTER APPLY (
        SELECT COUNT_BIG(*) AS typed_stage_rows,
               COALESCE(SUM(CONVERT(BIGINT, DATALENGTH(typed.usuario_name))), 0)
                    AS typed_stage_bytes
        FROM stg.usuario_record AS typed
        WHERE typed.execution_id = relevant.execution_id
    ) AS typed_stage;
END;
GO

CREATE OR ALTER TRIGGER ctl.trg_usuario_publication_requires_apply_wrapper
ON ctl.execution_publication_event
AFTER INSERT
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;

    IF EXISTS (
        SELECT 1
        FROM inserted AS publication
        INNER JOIN ctl.execution_attempt AS attempt
            ON attempt.execution_id = publication.execution_id
        INNER JOIN ctl.execution_partition AS partition
            ON partition.partition_id = attempt.partition_id
        LEFT JOIN ctl.usuario_promotion_result AS validation
            ON validation.execution_id = publication.execution_id
        LEFT JOIN recon.usuario_apply_authorization AS apply_auth
            ON apply_auth.execution_id = publication.execution_id
        WHERE partition.entity_name = N'usuarios'
          AND (
              validation.validation_state IS NULL
              OR validation.validation_state <> N'PASSED'
              OR apply_auth.authorization_state IS NULL
              OR apply_auth.authorization_state <> N'APPLYING'
              OR apply_auth.candidate_rows <> validation.typed_candidate_rows
          )
    )
        THROW 51710, N'Publicação de Usuários exige validação e wrapper atômico.', 1;
END;
GO

-- A avaliação comum só pode ser persistida para Usuários quando o candidate set tipado
-- correspondente também passou. A ausência da avaliação mantém o health check fail-closed.
CREATE OR ALTER TRIGGER recon.trg_usuario_data_quality_requires_typed_pass
ON recon.execution_data_quality_evaluation
AFTER INSERT
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;

    IF EXISTS (
        SELECT 1
        FROM inserted AS evaluation
        INNER JOIN ctl.execution_attempt AS attempt WITH (HOLDLOCK)
            ON attempt.execution_id = evaluation.execution_id
        INNER JOIN ctl.execution_partition AS partition WITH (HOLDLOCK)
            ON partition.partition_id = attempt.partition_id
        LEFT JOIN ctl.execution_promotion_result AS generic_promotion WITH (HOLDLOCK)
            ON generic_promotion.execution_id = evaluation.execution_id
        LEFT JOIN ctl.usuario_promotion_result AS typed_promotion WITH (HOLDLOCK)
            ON typed_promotion.execution_id = evaluation.execution_id
        WHERE partition.entity_name = N'usuarios'
          AND (
              typed_promotion.execution_id IS NULL
              OR typed_promotion.validation_state <> N'PASSED'
              OR generic_promotion.execution_id IS NULL
              OR typed_promotion.generic_candidate_rows <> generic_promotion.candidate_rows
              OR typed_promotion.typed_candidate_rows <> generic_promotion.candidate_rows
              OR evaluation.candidate_rows <> generic_promotion.candidate_rows
          )
    )
        THROW 51729, N'Data Quality de Usuários exige promoção tipada íntegra e aprovada.', 1;
END;
GO

-- Os dois gates permanecem dentro do mesmo INSERT de publicação; a ordem explícita torna o
-- gate comum o primeiro e a autorização tipada o último, sem desabilitar nenhum deles.
EXEC sys.sp_settriggerorder
    @triggername = N'ctl.trg_execution_publication_requires_data_quality',
    @order = N'First',
    @stmttype = N'INSERT';
EXEC sys.sp_settriggerorder
    @triggername = N'ctl.trg_usuario_publication_requires_apply_wrapper',
    @order = N'Last',
    @stmttype = N'INSERT';
GO

CREATE OR ALTER PROCEDURE core.usp_apply_reconcile_publish_usuarios
    @execution_id UNIQUEIDENTIFIER,
    @contract_version NVARCHAR(MAX),
    @contract_fingerprint NVARCHAR(MAX),
    @configuration_version NVARCHAR(MAX),
    @configuration_fingerprint NVARCHAR(MAX)
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;

    BEGIN TRANSACTION;

    DECLARE @execution_state NVARCHAR(32);
    DECLARE @environment_name NVARCHAR(32);
    DECLARE @source_instance NVARCHAR(128);
    DECLARE @tenant_scope NVARCHAR(128);
    DECLARE @entity_name NVARCHAR(128);
    DECLARE @attempt_started_at_utc DATETIME2(3);
    DECLARE @replay_origin_started_at_utc DATETIME2(3);
    DECLARE @replay_origin_execution_id UNIQUEIDENTIFIER;
    SELECT @execution_state = attempt.current_state,
           @environment_name = partition.environment_name,
           @source_instance = partition.source_instance,
           @tenant_scope = partition.tenant_scope,
           @entity_name = partition.entity_name,
           @attempt_started_at_utc = attempt.started_at_utc,
           @replay_origin_started_at_utc = replay_origin.started_at_utc,
           @replay_origin_execution_id = replay_origin.execution_id
    FROM ctl.execution_attempt AS attempt WITH (UPDLOCK, HOLDLOCK)
    INNER JOIN ctl.execution_partition AS partition WITH (UPDLOCK, HOLDLOCK)
        ON partition.partition_id = attempt.partition_id
    LEFT JOIN ctl.execution_attempt AS replay_origin
        ON replay_origin.execution_id = attempt.replay_of_execution_id
    WHERE attempt.execution_id = @execution_id;

    IF @entity_name COLLATE Latin1_General_100_BIN2 <> N'usuarios'
        THROW 51711, N'A aplicação não pertence à vertical de Usuários.', 1;

    DECLARE @common_result TABLE (
        execution_id UNIQUEIDENTIFIER NOT NULL,
        candidate_rows BIGINT NOT NULL,
        inserted_rows BIGINT NOT NULL,
        updated_rows BIGINT NOT NULL,
        reactivated_rows BIGINT NOT NULL,
        noop_rows BIGINT NOT NULL,
        stale_noop_rows BIGINT NOT NULL,
        reconciled_at_utc DATETIME2(3) NOT NULL,
        published_at_utc DATETIME2(3) NOT NULL,
        incremental_frontier_before_utc DATETIME2(3) NULL,
        incremental_frontier_after_utc DATETIME2(3) NULL
    );

    IF @execution_state = N'PUBLISHED'
    BEGIN
        INSERT INTO @common_result
        EXEC core.usp_apply_reconcile_publish_execution
            @execution_id, @contract_version, @contract_fingerprint,
            @configuration_version, @configuration_fingerprint;

        IF NOT EXISTS (
            SELECT 1
            FROM recon.usuario_apply_authorization AS apply_auth
            INNER JOIN recon.usuario_reconciliation_result AS result
                ON result.execution_id = apply_auth.execution_id
            WHERE apply_auth.execution_id = @execution_id
              AND apply_auth.authorization_state = N'APPLIED'
              AND apply_auth.candidate_rows = result.candidate_rows
              AND result.candidate_rows = (
                  SELECT COUNT_BIG(*) FROM recon.usuario_candidate_application
                  WHERE execution_id = @execution_id
              )
              AND result.history_rows = (
                  SELECT COUNT_BIG(*) FROM core.usuario_history
                  WHERE execution_id = @execution_id
              )
        )
            THROW 51712, N'Retry publicado de Usuários possui evidência divergente.', 1;

        COMMIT TRANSACTION;
        SELECT result.execution_id, result.candidate_rows, result.inserted_rows,
               result.updated_rows, result.reactivated_rows, result.noop_rows,
               result.stale_noop_rows, result.reconciled_at_utc, result.published_at_utc,
               common.incremental_frontier_before_utc,
               common.incremental_frontier_after_utc
        FROM recon.usuario_reconciliation_result AS result
        CROSS JOIN @common_result AS common
        WHERE result.execution_id = @execution_id;
        RETURN;
    END;

    DECLARE @candidate_rows BIGINT;
    SELECT @candidate_rows = typed_candidate_rows
    FROM ctl.usuario_promotion_result WITH (UPDLOCK, HOLDLOCK)
    WHERE execution_id = @execution_id AND validation_state = N'PASSED';

    IF @execution_state <> N'PROMOTED' OR @candidate_rows IS NULL
        THROW 51713, N'Candidate set tipado de Usuários não está apto.', 1;

    INSERT INTO recon.usuario_apply_authorization (
        execution_id, authorization_state, candidate_rows, authorized_at_utc, applied_at_utc
    ) VALUES (
        @execution_id, N'APPLYING', @candidate_rows, SYSUTCDATETIME(), NULL
    );

    INSERT INTO @common_result
    EXEC core.usp_apply_reconcile_publish_execution
        @execution_id, @contract_version, @contract_fingerprint,
        @configuration_version, @configuration_fingerprint;

    DECLARE @observation_order_at_utc DATETIME2(3) =
        COALESCE(@replay_origin_started_at_utc, @attempt_started_at_utc);
    DECLARE @observation_order_execution_id UNIQUEIDENTIFIER =
        COALESCE(@replay_origin_execution_id, @execution_id);
    DECLARE @database_now_utc DATETIME2(3) = SYSUTCDATETIME();

    DECLARE @application_plan TABLE (
        source_key NVARCHAR(256) COLLATE Latin1_General_100_BIN2 NOT NULL PRIMARY KEY,
        record_state_id BIGINT NOT NULL,
        usuario_id BIGINT NULL,
        source_key_wire_type NVARCHAR(16) COLLATE Latin1_General_100_BIN2 NOT NULL,
        name_presence NVARCHAR(8) COLLATE Latin1_General_100_BIN2 NOT NULL,
        usuario_name NVARCHAR(255) COLLATE Latin1_General_100_BIN2 NULL,
        attribute_hash CHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL,
        candidate_state_hash CHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL,
        generic_application_disposition NVARCHAR(24)
            COLLATE Latin1_General_100_BIN2 NOT NULL,
        application_disposition NVARCHAR(24) COLLATE Latin1_General_100_BIN2 NOT NULL,
        apply_candidate_values BIT NOT NULL
    );

    INSERT INTO @application_plan (
        source_key, record_state_id, usuario_id, source_key_wire_type, name_presence,
        usuario_name, attribute_hash, candidate_state_hash,
        generic_application_disposition, application_disposition, apply_candidate_values
    )
    SELECT candidate.source_key, generic_application.record_state_id, current_record.usuario_id,
           typed.source_key_wire_type, resolved.name_presence, resolved.usuario_name,
           resolved.attribute_hash, calculated.candidate_state_hash,
           generic_application.application_disposition,
           CASE
               WHEN current_record.usuario_id IS NULL THEN N'INSERTED'
               WHEN @observation_order_at_utc < current_record.observation_order_at_utc
                    OR (
                        @observation_order_at_utc = current_record.observation_order_at_utc
                        AND @observation_order_execution_id
                            < current_record.observation_order_execution_id
                    )
                   THEN N'STALE_NO_OP'
               WHEN current_record.active = 0 THEN N'REACTIVATED'
               WHEN current_record.state_hash <> calculated.candidate_state_hash THEN N'UPDATED'
               ELSE N'NO_OP'
           END,
           CASE
               WHEN current_record.usuario_id IS NULL THEN 1
               WHEN (
                    @observation_order_at_utc > current_record.observation_order_at_utc
                    OR (
                         @observation_order_at_utc = current_record.observation_order_at_utc
                         AND @observation_order_execution_id
                             >= current_record.observation_order_execution_id
                    )
               )
                    AND (
                        current_record.active = 0
                        OR current_record.state_hash <> calculated.candidate_state_hash
                    ) THEN 1
               ELSE 0
           END
    FROM stg.execution_candidate AS candidate
    INNER JOIN stg.usuario_record AS typed
        ON typed.stage_record_id = candidate.winner_stage_record_id
       AND typed.execution_id = candidate.execution_id
       AND typed.source_key = candidate.source_key
    INNER JOIN recon.execution_candidate_application AS generic_application
        ON generic_application.execution_id = candidate.execution_id
       AND generic_application.source_key = candidate.source_key
    LEFT JOIN core.usuario AS current_record WITH (
        UPDLOCK, HOLDLOCK, INDEX(UQ_core_usuario_source), FORCESEEK
    )
        ON current_record.environment_name = @environment_name
       AND current_record.source_instance = @source_instance
       AND current_record.tenant_scope = @tenant_scope
       AND current_record.entity_name = @entity_name
       AND current_record.source_key = candidate.source_key
    CROSS APPLY (SELECT
        CASE WHEN typed.name_presence = N'ABSENT' AND current_record.usuario_id IS NOT NULL
             THEN current_record.name_presence ELSE typed.name_presence END AS name_presence,
        CASE WHEN typed.name_presence = N'ABSENT' AND current_record.usuario_id IS NOT NULL
             THEN current_record.usuario_name ELSE typed.usuario_name END AS usuario_name,
        CASE WHEN typed.name_presence = N'ABSENT' AND current_record.usuario_id IS NOT NULL
             THEN current_record.attribute_hash ELSE typed.attribute_hash END AS attribute_hash
    ) AS resolved
    CROSS APPLY (SELECT LOWER(CONVERT(CHAR(64), HASHBYTES(
        'SHA2_256', CONVERT(VARBINARY(MAX), CONCAT(
            N'usuarios-state-v1|', resolved.attribute_hash, N'|active=1'
        ))
    ), 2)) AS candidate_state_hash) AS calculated
    WHERE candidate.execution_id = @execution_id;

    IF (SELECT COUNT_BIG(*) FROM @application_plan) <> @candidate_rows
        THROW 51714, N'O plano tipado não cobre todo o candidate set de Usuários.', 1;

    IF EXISTS (
        SELECT 1
        FROM @application_plan
        WHERE (generic_application_disposition = N'REACTIVATED'
               AND application_disposition <> N'REACTIVATED')
           OR (generic_application_disposition <> N'REACTIVATED'
               AND application_disposition = N'REACTIVATED')
    )
        THROW 51730, N'Reativação de Usuários diverge do estado técnico genérico.', 1;

    IF EXISTS (
        SELECT 1 FROM @application_plan AS planned
        INNER JOIN core.usuario AS current_record
            ON current_record.usuario_id = planned.usuario_id
        WHERE current_record.observation_order_at_utc = @observation_order_at_utc
          AND current_record.observation_order_execution_id = @observation_order_execution_id
          AND current_record.state_hash <> planned.candidate_state_hash
    )
        THROW 51715, N'Empate de ordem possui estado de Usuários divergente.', 1;

    INSERT INTO core.usuario (
        record_state_id, environment_name, source_instance, tenant_scope, entity_name,
        source_key, source_key_wire_type, name_presence, usuario_name,
        attribute_fingerprint_version, attribute_hash, state_fingerprint_version, state_hash,
        active, first_seen_execution_id, last_seen_execution_id, last_changed_execution_id,
        first_seen_at_utc, last_seen_at_utc, last_changed_at_utc, observation_order_at_utc,
        observation_order_execution_id
    )
    SELECT planned.record_state_id, @environment_name, @source_instance, @tenant_scope, @entity_name,
           planned.source_key, planned.source_key_wire_type, planned.name_presence,
           planned.usuario_name, N'usuarios-attributes-v1', planned.attribute_hash,
           N'usuarios-state-v1', planned.candidate_state_hash, 1,
           @execution_id, @execution_id, @execution_id,
           @database_now_utc, @database_now_utc, @database_now_utc,
           @observation_order_at_utc, @observation_order_execution_id
    FROM @application_plan AS planned
    WHERE planned.application_disposition = N'INSERTED';

    UPDATE current_record
    SET source_key_wire_type = planned.source_key_wire_type,
        name_presence = planned.name_presence,
        usuario_name = planned.usuario_name,
        attribute_hash = planned.attribute_hash,
        state_hash = planned.candidate_state_hash,
        active = 1,
        last_seen_execution_id = @execution_id,
        last_changed_execution_id = @execution_id,
        last_seen_at_utc = @database_now_utc,
        last_changed_at_utc = @database_now_utc,
        observation_order_at_utc = @observation_order_at_utc,
        observation_order_execution_id = @observation_order_execution_id
    FROM core.usuario AS current_record
    INNER JOIN @application_plan AS planned
        ON planned.usuario_id = current_record.usuario_id
    WHERE planned.application_disposition IN (N'UPDATED', N'REACTIVATED')
      AND planned.apply_candidate_values = 1;

    UPDATE current_record
    SET last_seen_execution_id = @execution_id,
        last_seen_at_utc = @database_now_utc,
        observation_order_at_utc = @observation_order_at_utc,
        observation_order_execution_id = @observation_order_execution_id
    FROM core.usuario AS current_record
    INNER JOIN @application_plan AS planned
        ON planned.usuario_id = current_record.usuario_id
    WHERE planned.application_disposition = N'NO_OP'
      AND (
          @observation_order_at_utc > current_record.observation_order_at_utc
          OR (
              @observation_order_at_utc = current_record.observation_order_at_utc
              AND @observation_order_execution_id
                  > current_record.observation_order_execution_id
          )
      );

    UPDATE planned
    SET usuario_id = current_record.usuario_id
    FROM @application_plan AS planned
    INNER JOIN core.usuario AS current_record
        ON current_record.environment_name = @environment_name
       AND current_record.source_instance = @source_instance
       AND current_record.tenant_scope = @tenant_scope
       AND current_record.entity_name = @entity_name
       AND current_record.source_key = planned.source_key;

    IF EXISTS (SELECT 1 FROM @application_plan WHERE usuario_id IS NULL)
        THROW 51716, N'A aplicação não resolveu canonical_id de Usuários.', 1;

    INSERT INTO core.usuario_history (
        usuario_id, execution_id, change_kind, name_presence, usuario_name,
        attribute_fingerprint_version, attribute_hash, state_fingerprint_version,
        state_hash, active, observation_order_at_utc, observation_order_execution_id,
        changed_at_utc
    )
    SELECT planned.usuario_id, @execution_id, planned.application_disposition,
           current_record.name_presence, current_record.usuario_name,
           current_record.attribute_fingerprint_version, current_record.attribute_hash,
           current_record.state_fingerprint_version, current_record.state_hash,
           current_record.active, @observation_order_at_utc,
           @observation_order_execution_id, @database_now_utc
    FROM @application_plan AS planned
    INNER JOIN core.usuario AS current_record
        ON current_record.usuario_id = planned.usuario_id
    WHERE planned.application_disposition IN (N'INSERTED', N'UPDATED', N'REACTIVATED');

    INSERT INTO recon.usuario_candidate_application (
        execution_id, source_key, usuario_id, application_disposition,
        result_attribute_hash, result_state_hash, result_active,
        observation_order_at_utc, observation_order_execution_id, applied_at_utc
    )
    SELECT @execution_id, planned.source_key, planned.usuario_id,
           planned.application_disposition, current_record.attribute_hash,
           current_record.state_hash, current_record.active,
           @observation_order_at_utc, @observation_order_execution_id, @database_now_utc
    FROM @application_plan AS planned
    INNER JOIN core.usuario AS current_record
        ON current_record.usuario_id = planned.usuario_id;

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

    INSERT INTO recon.usuario_reconciliation_result (
        execution_id, candidate_rows, inserted_rows, updated_rows, reactivated_rows,
        noop_rows, stale_noop_rows, history_rows, reconciled_at_utc, published_at_utc
    )
    SELECT @execution_id, @candidate_rows, @inserted_rows, @updated_rows, @reactivated_rows,
           @noop_rows, @stale_noop_rows,
           @inserted_rows + @updated_rows + @reactivated_rows,
           common.reconciled_at_utc, common.published_at_utc
    FROM @common_result AS common;

    UPDATE recon.usuario_apply_authorization
    SET authorization_state = N'APPLIED', applied_at_utc = @database_now_utc
    WHERE execution_id = @execution_id AND authorization_state = N'APPLYING';

    IF @@ROWCOUNT <> 1
        THROW 51717, N'A autorização atômica de Usuários divergiu.', 1;

    COMMIT TRANSACTION;

    SELECT @execution_id AS execution_id, @candidate_rows AS candidate_rows,
           @inserted_rows AS inserted_rows, @updated_rows AS updated_rows,
           @reactivated_rows AS reactivated_rows, @noop_rows AS noop_rows,
           @stale_noop_rows AS stale_noop_rows, common.reconciled_at_utc,
           common.published_at_utc, common.incremental_frontier_before_utc,
           common.incremental_frontier_after_utc
    FROM @common_result AS common;
END;
GO

-- Extensão set-based e correlacionada do budget comum. O plan_id não altera o tamanho do JSON
-- porque todo GUID textual possui o mesmo comprimento; execution_id evita estado intermediário.
-- TOP(max+1) torna o probe logicamente limitado e FORCESEEK impede scan global do sidecar.
CREATE OR ALTER FUNCTION recon.ufn_staging_lifecycle_extension_archive_budget (
    @execution_id UNIQUEIDENTIFIER,
    @maximum_extension_rows BIGINT
)
RETURNS TABLE
AS
RETURN (
    SELECT COUNT_BIG(*) AS extension_rows,
           COALESCE(
               SUM(CONVERT(BIGINT, 86 + DATALENGTH(canonical.canonical_row))),
               CONVERT(BIGINT, 0)
           ) AS additional_archive_bytes
    FROM (
        SELECT TOP (@maximum_extension_rows + 1)
               typed.stage_record_id, typed.execution_id, typed.source_key_wire_type,
               typed.name_presence, typed.attribute_fingerprint_version,
               typed.attribute_hash, typed.presence_fingerprint_version,
               typed.name_presence_hash, typed.usuario_name
        FROM stg.usuario_record AS typed WITH (
            INDEX(UQ_stg_usuario_record_execution_stage), FORCESEEK
        )
        WHERE typed.execution_id = @execution_id
        ORDER BY typed.stage_record_id
    ) AS typed
    CROSS APPLY (SELECT (SELECT typed.execution_id AS plan_id, typed.stage_record_id,
                                typed.execution_id, typed.source_key_wire_type,
                                typed.name_presence, typed.attribute_fingerprint_version,
                                typed.attribute_hash, typed.presence_fingerprint_version,
                                typed.name_presence_hash,
                                COALESCE(DATALENGTH(typed.usuario_name), 0)
                                    AS discarded_name_bytes
                         FOR JSON PATH, WITHOUT_ARRAY_WRAPPER, INCLUDE_NULL_VALUES)
                        AS canonical_row) AS canonical
);
GO

-- Guarda de defesa em profundidade: V007 não corrige nem reordena o plano depois do INSERT.
-- Ela recusa probe tipado truncado, total abaixo do lower bound tipado e estouro individual ou
-- cumulativo. A igualdade integral é responsabilidade do único writer, o planner V005.
CREATE OR ALTER TRIGGER ctl.trg_usuario_lifecycle_plan_budget
ON ctl.staging_lifecycle_plan_item
AFTER INSERT
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;

    IF EXISTS (
        SELECT 1
        FROM inserted AS planned
        INNER JOIN ctl.staging_lifecycle_plan AS lifecycle_plan
            ON lifecycle_plan.plan_id = planned.plan_id
        CROSS APPLY recon.ufn_staging_lifecycle_extension_archive_budget(
            planned.execution_id, lifecycle_plan.maximum_stage_rows
        ) AS extension_budget
        WHERE extension_budget.extension_rows > lifecycle_plan.maximum_stage_rows
           OR planned.archive_bytes < extension_budget.additional_archive_bytes
    )
        THROW 51731, N'Plano de lifecycle violou o lower bound tipado de Usuários.', 1;

    IF EXISTS (
        SELECT 1
        FROM inserted AS planned
        INNER JOIN ctl.staging_lifecycle_plan AS lifecycle_plan
            ON lifecycle_plan.plan_id = planned.plan_id
        WHERE planned.archive_bytes > lifecycle_plan.maximum_archive_bytes
    )
        THROW 51732, N'Plano de lifecycle admitiu item tipado oversized.', 1;

    IF EXISTS (
        SELECT 1
        FROM (
            SELECT planned.plan_id, planned.execution_id,
                   SUM(CONVERT(DECIMAL(38, 0), planned.archive_bytes)) OVER (
                       PARTITION BY planned.plan_id
                       ORDER BY planned.terminal_at_utc, planned.execution_id
                       ROWS UNBOUNDED PRECEDING
                   ) AS cumulative_archive_bytes
            FROM ctl.staging_lifecycle_plan_item AS planned
            WHERE EXISTS (SELECT 1 FROM inserted WHERE inserted.plan_id = planned.plan_id)
        ) AS ordered
        INNER JOIN ctl.staging_lifecycle_plan AS lifecycle_plan
            ON lifecycle_plan.plan_id = ordered.plan_id
        WHERE ordered.cumulative_archive_bytes > lifecycle_plan.maximum_archive_bytes
    )
        THROW 51733, N'Plano de lifecycle ultrapassou o budget tipado cumulativo.', 1;
END;
GO

-- O insert set-based do archive genérico materializa a cópia tipada minimizada no mesmo commit.
CREATE OR ALTER TRIGGER recon.trg_usuario_stage_archive_from_generic
ON recon.staging_record_archive
AFTER INSERT
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;

    INSERT INTO recon.usuario_stage_archive (
        plan_id, stage_record_id, execution_id, source_key_wire_type, name_presence,
        attribute_fingerprint_version, attribute_hash, presence_fingerprint_version,
        name_presence_hash, discarded_name_bytes,
        archive_row_attestation_version, archive_row_attestation
    )
    SELECT inserted.plan_id, typed.stage_record_id, typed.execution_id,
           typed.source_key_wire_type, typed.name_presence,
           typed.attribute_fingerprint_version, typed.attribute_hash,
           typed.presence_fingerprint_version, typed.name_presence_hash,
           COALESCE(DATALENGTH(typed.usuario_name), 0), N'archive-row-json-v1',
           recon.ufn_archive_row_attestation(N'stg.usuario_record', canonical.canonical_row)
    FROM inserted
    INNER JOIN stg.usuario_record AS typed
        ON typed.stage_record_id = inserted.stage_record_id
       AND typed.execution_id = inserted.execution_id
       AND typed.source_key = inserted.source_key
    CROSS APPLY (SELECT (SELECT inserted.plan_id, typed.stage_record_id,
                                typed.execution_id, typed.source_key_wire_type,
                                typed.name_presence, typed.attribute_fingerprint_version,
                                typed.attribute_hash, typed.presence_fingerprint_version,
                                typed.name_presence_hash,
                                COALESCE(DATALENGTH(typed.usuario_name), 0)
                                    AS discarded_name_bytes
                         FOR JSON PATH, WITHOUT_ARRAY_WRAPPER, INCLUDE_NULL_VALUES)
                        AS canonical_row) AS canonical;
END;
GO

-- Restore é sempre uma materialização read-only em recon e mantém o mesmo conteúdo minimizado.
CREATE OR ALTER TRIGGER recon.trg_usuario_stage_restore_from_generic
ON recon.staging_restore_record
AFTER INSERT
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;

    INSERT INTO recon.usuario_stage_restore (
        restore_id, stage_record_id, execution_id, source_key_wire_type, name_presence,
        attribute_fingerprint_version, attribute_hash, presence_fingerprint_version,
        name_presence_hash, discarded_name_bytes,
        archive_row_attestation_version, archive_row_attestation
    )
    SELECT inserted.restore_id, archived.stage_record_id, archived.execution_id,
           archived.source_key_wire_type, archived.name_presence,
           archived.attribute_fingerprint_version, archived.attribute_hash,
           archived.presence_fingerprint_version, archived.name_presence_hash,
           archived.discarded_name_bytes, archived.archive_row_attestation_version,
           archived.archive_row_attestation
    FROM inserted
    INNER JOIN recon.staging_restore_session AS restore_session
        ON restore_session.restore_id = inserted.restore_id
    INNER JOIN recon.usuario_stage_archive AS archived
        ON archived.plan_id = restore_session.plan_id
       AND archived.stage_record_id = inserted.stage_record_id
       AND archived.execution_id = inserted.execution_id;
END;
GO

-- O lifecycle remove o valor tipado somente depois de uma cópia minimizada, atestada e ligada ao
-- archive genérico; o nome bruto não é duplicado fora de current/history.
CREATE OR ALTER TRIGGER stg.trg_execution_record_delete_usuario_stage
ON stg.execution_record
INSTEAD OF DELETE
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;

    IF EXISTS (
        SELECT 1
        FROM deleted AS removed
        INNER JOIN stg.usuario_record AS typed
            ON typed.stage_record_id = removed.stage_record_id
        LEFT JOIN recon.staging_record_archive AS generic_archive
            ON generic_archive.stage_record_id = removed.stage_record_id
           AND generic_archive.execution_id = removed.execution_id
        LEFT JOIN recon.usuario_stage_archive AS typed_archive
            ON typed_archive.plan_id = generic_archive.plan_id
           AND typed_archive.stage_record_id = typed.stage_record_id
           AND typed_archive.execution_id = typed.execution_id
        OUTER APPLY (SELECT (SELECT generic_archive.plan_id, typed.stage_record_id,
                                    typed.execution_id, typed.source_key_wire_type,
                                    typed.name_presence, typed.attribute_fingerprint_version,
                                    typed.attribute_hash, typed.presence_fingerprint_version,
                                    typed.name_presence_hash,
                                    COALESCE(DATALENGTH(typed.usuario_name), 0)
                                        AS discarded_name_bytes
                             FOR JSON PATH, WITHOUT_ARRAY_WRAPPER, INCLUDE_NULL_VALUES)
                            AS canonical_row) AS canonical
        WHERE typed_archive.stage_record_id IS NULL
           OR typed_archive.source_key_wire_type <> typed.source_key_wire_type
           OR typed_archive.name_presence <> typed.name_presence
           OR typed_archive.attribute_fingerprint_version
                <> typed.attribute_fingerprint_version
           OR typed_archive.attribute_hash <> typed.attribute_hash
           OR typed_archive.presence_fingerprint_version
                <> typed.presence_fingerprint_version
           OR typed_archive.name_presence_hash <> typed.name_presence_hash
           OR typed_archive.discarded_name_bytes
                <> COALESCE(DATALENGTH(typed.usuario_name), 0)
           OR typed_archive.archive_row_attestation_version <> N'archive-row-json-v1'
           OR typed_archive.archive_row_attestation
                <> recon.ufn_archive_row_attestation(N'stg.usuario_record', canonical.canonical_row)
    )
        THROW 51719, N'O purge de Usuários exige archive tipado íntegro.', 1;

    INSERT INTO recon.usuario_stage_disposal_evidence (
        plan_id, execution_id, typed_rows, name_bytes, disposed_at_utc
    )
    SELECT archived.plan_id, typed.execution_id, COUNT_BIG(*),
           COALESCE(SUM(CONVERT(BIGINT, DATALENGTH(typed.usuario_name))), 0), SYSUTCDATETIME()
    FROM deleted AS removed
    INNER JOIN stg.usuario_record AS typed ON typed.stage_record_id = removed.stage_record_id
    INNER JOIN recon.staging_record_archive AS archived
        ON archived.stage_record_id = removed.stage_record_id
       AND archived.execution_id = removed.execution_id
    GROUP BY archived.plan_id, typed.execution_id;

    DELETE typed
    FROM stg.usuario_record AS typed
    INNER JOIN deleted AS removed ON removed.stage_record_id = typed.stage_record_id;

    DELETE persisted
    FROM stg.execution_record AS persisted
    INNER JOIN deleted AS removed ON removed.stage_record_id = persisted.stage_record_id;
END;
GO

CREATE OR ALTER TRIGGER recon.trg_usuario_apply_authorization_immutable
ON recon.usuario_apply_authorization
AFTER UPDATE, DELETE
AS
BEGIN
    SET NOCOUNT ON;
    IF EXISTS (SELECT 1 FROM deleted WHERE NOT EXISTS (
        SELECT 1 FROM inserted WHERE inserted.execution_id = deleted.execution_id
    ))
        THROW 51720, N'Evidência de autorização de Usuários é append-only.', 1;
    IF EXISTS (
        SELECT 1 FROM deleted
        INNER JOIN inserted ON inserted.execution_id = deleted.execution_id
        WHERE deleted.authorization_state <> N'APPLYING'
           OR inserted.authorization_state <> N'APPLIED'
           OR deleted.candidate_rows <> inserted.candidate_rows
           OR deleted.authorized_at_utc <> inserted.authorized_at_utc
           OR deleted.applied_at_utc IS NOT NULL
           OR inserted.applied_at_utc IS NULL
    )
        THROW 51720, N'Transição de autorização de Usuários é inválida.', 1;
END;
GO

CREATE OR ALTER TRIGGER core.trg_usuario_history_immutable
ON core.usuario_history
AFTER UPDATE, DELETE
AS
BEGIN
    SET NOCOUNT ON;
    THROW 51721, N'Histórico de Usuários é append-only.', 1;
END;
GO

CREATE OR ALTER TRIGGER recon.trg_usuario_application_immutable
ON recon.usuario_candidate_application
AFTER UPDATE, DELETE
AS
BEGIN
    SET NOCOUNT ON;
    THROW 51722, N'Evidência de aplicação de Usuários é append-only.', 1;
END;
GO

CREATE OR ALTER TRIGGER recon.trg_usuario_result_immutable
ON recon.usuario_reconciliation_result
AFTER UPDATE, DELETE
AS
BEGIN
    SET NOCOUNT ON;
    THROW 51723, N'Resultado de reconciliação de Usuários é append-only.', 1;
END;
GO

CREATE OR ALTER TRIGGER recon.trg_usuario_quarantine_immutable
ON recon.usuario_quarantine
AFTER UPDATE, DELETE
AS
BEGIN
    SET NOCOUNT ON;
    THROW 51727, N'Quarentena tipada de Usuários é imutável.', 1;
END;
GO

CREATE OR ALTER TRIGGER ctl.trg_usuario_promotion_result_immutable
ON ctl.usuario_promotion_result
AFTER UPDATE, DELETE
AS
BEGIN
    SET NOCOUNT ON;
    THROW 51728, N'Resultado de promoção tipada de Usuários é imutável.', 1;
END;
GO

CREATE OR ALTER TRIGGER recon.trg_usuario_stage_archive_immutable
ON recon.usuario_stage_archive
AFTER UPDATE, DELETE
AS
BEGIN
    SET NOCOUNT ON;
    THROW 51724, N'Archive tipado de Usuários é imutável.', 1;
END;
GO

CREATE OR ALTER TRIGGER recon.trg_usuario_stage_restore_immutable
ON recon.usuario_stage_restore
AFTER UPDATE, DELETE
AS
BEGIN
    SET NOCOUNT ON;
    THROW 51725, N'Restore tipado de Usuários é imutável.', 1;
END;
GO

CREATE OR ALTER TRIGGER recon.trg_usuario_stage_disposal_immutable
ON recon.usuario_stage_disposal_evidence
AFTER UPDATE, DELETE
AS
BEGIN
    SET NOCOUNT ON;
    THROW 51726, N'Evidência de descarte de Usuários é imutável.', 1;
END;
GO

EXEC dbo.usp_publish_v2_procedure_grant N'stg', N'usp_stage_usuario_record', N'v2_runtime';
EXEC dbo.usp_publish_v2_procedure_grant
    N'core', N'usp_apply_reconcile_publish_usuarios', N'v2_runtime';

DENY INSERT, UPDATE, DELETE, SELECT ON SCHEMA::stg TO v2_runtime;
DENY INSERT, UPDATE, DELETE, SELECT ON SCHEMA::core TO v2_runtime;
DENY INSERT, UPDATE, DELETE, SELECT ON SCHEMA::recon TO v2_runtime;
GO
