-- Vertical Coletas 6908 em sombra. Não cria objetos pub, sweep, relação canônica ou acesso externo.
SET XACT_ABORT ON;

CREATE TABLE stg.coleta_record (
    stage_record_id BIGINT NOT NULL,
    execution_id UNIQUEIDENTIFIER NOT NULL,
    source_key NVARCHAR(256) COLLATE Latin1_General_100_BIN2 NOT NULL,
    source_key_wire_type NVARCHAR(16) COLLATE Latin1_General_100_BIN2 NOT NULL,
    sequence_code_presence NVARCHAR(8) COLLATE Latin1_General_100_BIN2 NOT NULL,
    sequence_code_json NVARCHAR(256) COLLATE Latin1_General_100_BIN2 NULL,
    payload_json NVARCHAR(MAX) NOT NULL,
    field_presence_json NVARCHAR(MAX) NOT NULL,
    relation_candidates_json NVARCHAR(MAX) NOT NULL,
    status_raw NVARCHAR(255) NULL,
    status_code NVARCHAR(32) COLLATE Latin1_General_100_BIN2 NULL,
    status_label NVARCHAR(64) NULL,
    status_catalog_version NVARCHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL,
    terminal BIT NOT NULL,
    occurrence_action NVARCHAR(2048) NULL,
    attempt_count SMALLINT NOT NULL,
    freshness_raw NVARCHAR(255) NULL,
    freshness_at_utc DATETIME2(3) NULL,
    freshness_origin NVARCHAR(32) COLLATE Latin1_General_100_BIN2 NOT NULL,
    attribute_fingerprint_version NVARCHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL,
    attribute_hash CHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL,
    presence_fingerprint_version NVARCHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL,
    presence_hash CHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL,
    CONSTRAINT PK_stg_coleta_record PRIMARY KEY CLUSTERED (stage_record_id),
    CONSTRAINT FK_stg_coleta_record_stage FOREIGN KEY (stage_record_id, execution_id, source_key)
        REFERENCES stg.execution_record (stage_record_id, execution_id, source_key),
    CONSTRAINT UQ_stg_coleta_record_execution_stage UNIQUE (execution_id, stage_record_id),
    CONSTRAINT CK_stg_coleta_record_source_key CHECK (
        source_key_wire_type = N'INTEGER' AND source_key LIKE N'INTEGER:%'
    ),
    CONSTRAINT CK_stg_coleta_record_presence CHECK (
        sequence_code_presence IN (N'ABSENT', N'NULL', N'VALUE')
        AND ((sequence_code_presence = N'VALUE' AND sequence_code_json IS NOT NULL)
             OR (sequence_code_presence IN (N'ABSENT', N'NULL') AND sequence_code_json IS NULL))
    ),
    CONSTRAINT CK_stg_coleta_record_json CHECK (
        ISJSON(payload_json) = 1 AND ISJSON(field_presence_json) = 1
        AND ISJSON(relation_candidates_json) = 1
    ),
    CONSTRAINT CK_stg_coleta_record_status CHECK (
        status_catalog_version = N'coletas-status-v1'
        AND attempt_count IN (0, 1)
        AND ((status_code IS NULL AND status_label IS NULL AND terminal = 0)
             OR (status_code = N'pending' AND status_label = N'Pendente' AND terminal = 0)
             OR (status_code = N'treatment' AND status_label = N'Em tratativa' AND terminal = 0)
             OR (status_code = N'manifested' AND status_label = N'Manifestada' AND terminal = 0)
             OR (status_code = N'in_transit' AND status_label = N'Em trânsito' AND terminal = 0)
             OR (status_code = N'draft' AND status_label = N'Rascunho' AND terminal = 0)
             OR (status_code = N'finished' AND status_label = N'Finalizada' AND terminal = 1)
             OR (status_code = N'done' AND status_label = N'Coletada' AND terminal = 1)
             OR (status_code IN (N'canceled', N'cancelled') AND status_label = N'Cancelada' AND terminal = 1))
        AND attempt_count = CASE WHEN terminal = 1 THEN 1 ELSE 0 END
    ),
    CONSTRAINT CK_stg_coleta_record_freshness CHECK (
        freshness_origin IN (
            N'STATUS_UPDATED_AT', N'FINISH_DATE', N'SERVICE_DATE', N'REQUEST_DATE', N'UNAVAILABLE'
        )
        AND ((freshness_origin = N'UNAVAILABLE' AND freshness_at_utc IS NULL)
             OR (freshness_origin <> N'UNAVAILABLE' AND freshness_at_utc IS NOT NULL))
    ),
    CONSTRAINT CK_stg_coleta_record_fingerprints CHECK (
        attribute_fingerprint_version = N'coletas-attributes-v1'
        AND presence_fingerprint_version = N'coletas-presence-v1'
        AND attribute_hash NOT LIKE '%[^0-9A-Fa-f]%'
        AND presence_hash NOT LIKE '%[^0-9A-Fa-f]%'
    )
);
GO

CREATE INDEX IX_stg_coleta_record_execution_source
    ON stg.coleta_record (execution_id, source_key, attribute_hash)
    INCLUDE (stage_record_id, sequence_code_presence, sequence_code_json, terminal,
             freshness_at_utc, freshness_origin);
GO

CREATE TABLE ctl.coleta_promotion_result (
    execution_id UNIQUEIDENTIFIER NOT NULL,
    generic_candidate_rows BIGINT NOT NULL,
    typed_candidate_rows BIGINT NOT NULL,
    conflicting_root_keys BIGINT NOT NULL,
    generic_quarantine_rows BIGINT NOT NULL,
    typed_stage_rows BIGINT NOT NULL,
    validation_state NVARCHAR(16) COLLATE Latin1_General_100_BIN2 NOT NULL,
    validated_at_utc DATETIME2(3) NOT NULL,
    CONSTRAINT PK_ctl_coleta_promotion_result PRIMARY KEY CLUSTERED (execution_id),
    CONSTRAINT FK_ctl_coleta_promotion_result_generic FOREIGN KEY (execution_id)
        REFERENCES ctl.execution_promotion_result (execution_id),
    CONSTRAINT CK_ctl_coleta_promotion_result CHECK (
        generic_candidate_rows >= 0 AND typed_candidate_rows >= 0
        AND conflicting_root_keys >= 0 AND generic_quarantine_rows >= 0
        AND typed_stage_rows >= typed_candidate_rows
        AND validation_state IN (N'PASSED', N'BLOCKED')
        AND ((validation_state = N'PASSED' AND generic_candidate_rows = typed_candidate_rows
              AND conflicting_root_keys = 0 AND generic_quarantine_rows = 0)
             OR validation_state = N'BLOCKED')
    )
);
GO

CREATE TABLE core.coleta (
    coleta_id BIGINT IDENTITY(1, 1) NOT NULL,
    record_state_id BIGINT NOT NULL,
    environment_name NVARCHAR(32) COLLATE Latin1_General_100_BIN2 NOT NULL,
    source_instance NVARCHAR(128) COLLATE Latin1_General_100_BIN2 NOT NULL,
    tenant_scope NVARCHAR(128) COLLATE Latin1_General_100_BIN2 NOT NULL,
    entity_name NVARCHAR(128) COLLATE Latin1_General_100_BIN2 NOT NULL,
    source_key NVARCHAR(256) COLLATE Latin1_General_100_BIN2 NOT NULL,
    source_key_wire_type NVARCHAR(16) COLLATE Latin1_General_100_BIN2 NOT NULL,
    sequence_code_presence NVARCHAR(8) COLLATE Latin1_General_100_BIN2 NOT NULL,
    sequence_code_json NVARCHAR(256) COLLATE Latin1_General_100_BIN2 NULL,
    payload_json NVARCHAR(MAX) NOT NULL,
    field_presence_json NVARCHAR(MAX) NOT NULL,
    relation_candidates_json NVARCHAR(MAX) NOT NULL,
    status_raw NVARCHAR(255) NULL,
    status_code NVARCHAR(32) COLLATE Latin1_General_100_BIN2 NULL,
    status_label NVARCHAR(64) NULL,
    status_catalog_version NVARCHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL,
    terminal BIT NOT NULL,
    occurrence_action NVARCHAR(2048) NULL,
    attempt_count SMALLINT NOT NULL,
    freshness_raw NVARCHAR(255) NULL,
    freshness_at_utc DATETIME2(3) NULL,
    freshness_origin NVARCHAR(32) COLLATE Latin1_General_100_BIN2 NOT NULL,
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
    CONSTRAINT PK_core_coleta PRIMARY KEY CLUSTERED (coleta_id),
    CONSTRAINT UQ_core_coleta_record_state UNIQUE (record_state_id),
    CONSTRAINT UQ_core_coleta_source UNIQUE (
        environment_name, source_instance, tenant_scope, entity_name, source_key
    ),
    CONSTRAINT FK_core_coleta_record_binding FOREIGN KEY (
        record_state_id, environment_name, source_instance, tenant_scope, entity_name, source_key
    ) REFERENCES core.entity_record_state (
        record_state_id, environment_name, source_instance, tenant_scope, entity_name, source_key
    ),
    CONSTRAINT FK_core_coleta_first_execution FOREIGN KEY (first_seen_execution_id)
        REFERENCES ctl.execution_attempt (execution_id),
    CONSTRAINT FK_core_coleta_last_execution FOREIGN KEY (last_seen_execution_id)
        REFERENCES ctl.execution_attempt (execution_id),
    CONSTRAINT FK_core_coleta_changed_execution FOREIGN KEY (last_changed_execution_id)
        REFERENCES ctl.execution_attempt (execution_id),
    CONSTRAINT CK_core_coleta_identity CHECK (
        entity_name = N'coletas' AND source_key_wire_type = N'INTEGER'
        AND source_key LIKE N'INTEGER:%'
    ),
    CONSTRAINT CK_core_coleta_json CHECK (
        ISJSON(payload_json) = 1 AND ISJSON(field_presence_json) = 1
        AND ISJSON(relation_candidates_json) = 1
    ),
    CONSTRAINT CK_core_coleta_active CHECK (active = 1),
    CONSTRAINT CK_core_coleta_freshness CHECK (
        (freshness_origin = N'UNAVAILABLE' AND freshness_at_utc IS NULL)
        OR (freshness_origin <> N'UNAVAILABLE' AND freshness_at_utc IS NOT NULL)
    )
);
GO

CREATE INDEX IX_core_coleta_active_freshness
    ON core.coleta (environment_name, source_instance, tenant_scope, freshness_at_utc)
    INCLUDE (coleta_id, terminal, status_code, sequence_code_json) WHERE active = 1;
GO

CREATE TABLE ref.coleta_sequence_code_alias (
    coleta_sequence_code_alias_id BIGINT IDENTITY(1, 1) NOT NULL,
    coleta_id BIGINT NOT NULL,
    sequence_code_json NVARCHAR(256) COLLATE Latin1_General_100_BIN2 NOT NULL,
    observed_execution_id UNIQUEIDENTIFIER NOT NULL,
    valid_from_utc DATETIME2(3) NOT NULL,
    valid_to_utc DATETIME2(3) NULL,
    provenance NVARCHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL,
    CONSTRAINT PK_ref_coleta_sequence_code_alias PRIMARY KEY CLUSTERED (coleta_sequence_code_alias_id),
    CONSTRAINT FK_ref_coleta_sequence_code_alias_coleta FOREIGN KEY (coleta_id)
        REFERENCES core.coleta (coleta_id),
    CONSTRAINT FK_ref_coleta_sequence_code_alias_execution FOREIGN KEY (observed_execution_id)
        REFERENCES ctl.execution_attempt (execution_id),
    CONSTRAINT CK_ref_coleta_sequence_code_alias_values CHECK (
        ISJSON(sequence_code_json) = 1
        AND (valid_to_utc IS NULL OR valid_to_utc >= valid_from_utc)
    )
);
GO

CREATE UNIQUE INDEX UQ_ref_coleta_sequence_code_alias_current
    ON ref.coleta_sequence_code_alias (coleta_id) WHERE valid_to_utc IS NULL;
GO

CREATE TABLE recon.coleta_root_presence_observation (
    execution_id UNIQUEIDENTIFIER NOT NULL,
    coleta_id BIGINT NOT NULL,
    observed_at_utc DATETIME2(3) NOT NULL,
    snapshot_completeness NVARCHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL,
    absence_evaluation NVARCHAR(32) COLLATE Latin1_General_100_BIN2 NOT NULL,
    CONSTRAINT PK_recon_coleta_root_presence_observation PRIMARY KEY CLUSTERED (execution_id, coleta_id),
    CONSTRAINT FK_recon_coleta_root_presence_execution FOREIGN KEY (execution_id)
        REFERENCES ctl.execution_attempt (execution_id),
    CONSTRAINT FK_recon_coleta_root_presence_coleta FOREIGN KEY (coleta_id)
        REFERENCES core.coleta (coleta_id),
    CONSTRAINT CK_recon_coleta_root_presence_values CHECK (
        snapshot_completeness = N'BLOCKED_NO_COMPLETENESS_PROOF'
        AND absence_evaluation = N'NOT_EVALUATED'
    )
);
GO

CREATE OR ALTER PROCEDURE stg.usp_stage_coleta_record
    @execution_id UNIQUEIDENTIFIER,
    @input_batch_number INT,
    @input_record_ordinal INT,
    @source_key NVARCHAR(MAX),
    @source_key_wire_type NVARCHAR(MAX),
    @sequence_code_presence NVARCHAR(MAX),
    @sequence_code_json NVARCHAR(MAX),
    @payload_json NVARCHAR(MAX),
    @field_presence_json NVARCHAR(MAX),
    @relation_candidates_json NVARCHAR(MAX),
    @status_raw NVARCHAR(MAX),
    @status_code NVARCHAR(MAX),
    @status_label NVARCHAR(MAX),
    @status_catalog_version NVARCHAR(MAX),
    @terminal BIT,
    @occurrence_action NVARCHAR(MAX),
    @attempt_count SMALLINT,
    @freshness_raw NVARCHAR(MAX),
    @freshness_at_utc DATETIME2(3),
    @freshness_origin NVARCHAR(MAX),
    @validation_disposition NVARCHAR(MAX),
    @quarantine_reason_code NVARCHAR(MAX),
    @observed_at_utc DATETIME2(3)
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;

    IF @input_record_ordinal IS NULL OR @input_record_ordinal NOT BETWEEN 1 AND 100
        THROW 51800, N'Ordinal vertical de Coletas deve estar entre 1 e 100.', 1;
    IF DATALENGTH(@source_key) > 512 OR DATALENGTH(@source_key_wire_type) > 32
        OR DATALENGTH(@sequence_code_presence) > 16 OR DATALENGTH(@sequence_code_json) > 512
        OR DATALENGTH(@payload_json) > 20971520 OR DATALENGTH(@field_presence_json) > 20971520
        OR DATALENGTH(@relation_candidates_json) > 20971520 OR DATALENGTH(@status_raw) > 510
        OR DATALENGTH(@status_code) > 64 OR DATALENGTH(@status_label) > 128
        OR DATALENGTH(@status_catalog_version) > 128 OR DATALENGTH(@occurrence_action) > 4096
        OR DATALENGTH(@freshness_raw) > 510 OR DATALENGTH(@freshness_origin) > 64
        OR DATALENGTH(@validation_disposition) > 32 OR DATALENGTH(@quarantine_reason_code) > 128
        THROW 51800, N'Registro tipado de Coletas excede o limite textual.', 1;

    SET @source_key_wire_type = NULLIF(LTRIM(RTRIM(@source_key_wire_type)), N'');
    SET @sequence_code_presence = NULLIF(LTRIM(RTRIM(@sequence_code_presence)), N'');
    SET @status_code = NULLIF(LTRIM(RTRIM(@status_code)), N'');
    SET @status_catalog_version = NULLIF(LTRIM(RTRIM(@status_catalog_version)), N'');
    SET @freshness_origin = NULLIF(LTRIM(RTRIM(@freshness_origin)), N'');
    SET @validation_disposition = NULLIF(LTRIM(RTRIM(@validation_disposition)), N'');
    SET @quarantine_reason_code = NULLIF(LTRIM(RTRIM(@quarantine_reason_code)), N'');

    IF @source_key_wire_type COLLATE Latin1_General_100_BIN2 <> N'INTEGER'
       OR LEFT(@source_key, 8) COLLATE Latin1_General_100_BIN2 <> N'INTEGER:'
       OR SUBSTRING(@source_key, 9, 256) COLLATE Latin1_General_100_BIN2 LIKE N'%[^0-9-]%'
       OR @source_key = N'INTEGER:' OR @source_key = N'INTEGER:-'
        THROW 51800, N'Source key tipada de Coletas é inválida.', 1;

    IF @validation_disposition COLLATE Latin1_General_100_BIN2 NOT IN (N'VALID', N'QUARANTINE')
       OR @observed_at_utc IS NULL
       OR (@validation_disposition = N'QUARANTINE' AND @quarantine_reason_code IS NULL)
       OR (@validation_disposition = N'VALID' AND (
            @source_key IS NULL OR @sequence_code_presence NOT IN (N'ABSENT', N'NULL', N'VALUE')
            OR (@sequence_code_presence = N'VALUE' AND ISJSON(@sequence_code_json) <> 1)
            OR (@sequence_code_presence IN (N'ABSENT', N'NULL') AND @sequence_code_json IS NOT NULL)
            OR ISJSON(@payload_json) <> 1 OR ISJSON(@field_presence_json) <> 1
            OR ISJSON(@relation_candidates_json) <> 1
            OR @status_catalog_version <> N'coletas-status-v1'
            OR @terminal IS NULL OR @attempt_count <> CASE WHEN @terminal = 1 THEN 1 ELSE 0 END
            OR @freshness_origin NOT IN (
                N'STATUS_UPDATED_AT', N'FINISH_DATE', N'SERVICE_DATE', N'REQUEST_DATE', N'UNAVAILABLE'
            )
            OR ((@freshness_origin = N'UNAVAILABLE' AND @freshness_at_utc IS NOT NULL)
                OR (@freshness_origin <> N'UNAVAILABLE' AND @freshness_at_utc IS NULL))
            OR @quarantine_reason_code IS NOT NULL
       ))
        THROW 51800, N'Registro tipado de Coletas é inválido.', 1;

    IF @validation_disposition = N'VALID' AND NOT (
        (@status_code IS NULL AND @status_label IS NULL AND @terminal = 0)
        OR (@status_code = N'pending' AND @status_label = N'Pendente' AND @terminal = 0)
        OR (@status_code = N'treatment' AND @status_label = N'Em tratativa' AND @terminal = 0)
        OR (@status_code = N'manifested' AND @status_label = N'Manifestada' AND @terminal = 0)
        OR (@status_code = N'in_transit' AND @status_label = N'Em trânsito' AND @terminal = 0)
        OR (@status_code = N'draft' AND @status_label = N'Rascunho' AND @terminal = 0)
        OR (@status_code = N'finished' AND @status_label = N'Finalizada' AND @terminal = 1)
        OR (@status_code = N'done' AND @status_label = N'Coletada' AND @terminal = 1)
        OR (@status_code IN (N'canceled', N'cancelled') AND @status_label = N'Cancelada' AND @terminal = 1)
    )
        THROW 51800, N'Catálogo de status de Coletas diverge.', 1;

    DECLARE @identity_row_hash CHAR(64) = NULL;
    DECLARE @identity_presence_hash CHAR(64) = NULL;
    DECLARE @attribute_hash CHAR(64) = NULL;
    DECLARE @presence_hash CHAR(64) = NULL;
    IF @validation_disposition = N'VALID'
    BEGIN
        SET @identity_row_hash = LOWER(CONVERT(CHAR(64), HASHBYTES(
            'SHA2_256', CONVERT(VARBINARY(MAX), CONCAT(N'coletas-envelope-v1|', @payload_json))
        ), 2));
        SET @identity_presence_hash = LOWER(CONVERT(CHAR(64), HASHBYTES(
            'SHA2_256', CONVERT(VARBINARY(MAX), CONCAT(N'coletas-presence-v1|', @field_presence_json))
        ), 2));
        SET @attribute_hash = LOWER(CONVERT(CHAR(64), HASHBYTES(
            'SHA2_256', CONVERT(VARBINARY(MAX), CONCAT(
                N'coletas-attributes-v1|', @sequence_code_presence, N'|',
                COALESCE(@sequence_code_json, N'<null>'), N'|', COALESCE(@status_code, N'<unknown>'),
                N'|', CONVERT(NVARCHAR(1), @terminal), N'|', COALESCE(@freshness_origin, N'UNAVAILABLE'),
                N'|', COALESCE(CONVERT(NVARCHAR(33), @freshness_at_utc, 126), N'<null>')
            ))
        ), 2));
        SET @presence_hash = @identity_presence_hash;
    END;

    BEGIN TRANSACTION;
    DECLARE @entity_name NVARCHAR(128);
    SELECT @entity_name = partition.entity_name
    FROM ctl.execution_attempt AS attempt WITH (UPDLOCK, HOLDLOCK)
    INNER JOIN ctl.execution_partition AS partition WITH (HOLDLOCK)
        ON partition.partition_id = attempt.partition_id
    WHERE attempt.execution_id = @execution_id;
    IF @entity_name IS NULL OR @entity_name COLLATE Latin1_General_100_BIN2 <> N'coletas'
        THROW 51801, N'A execução não pertence à vertical de Coletas.', 1;

    DECLARE @preexisting_stage_record_id BIGINT;
    SELECT @preexisting_stage_record_id = stage_record_id
    FROM stg.execution_record WITH (UPDLOCK, HOLDLOCK)
    WHERE execution_id = @execution_id AND input_batch_number = @input_batch_number
      AND input_record_ordinal = @input_record_ordinal;
    IF @validation_disposition = N'VALID' AND @preexisting_stage_record_id IS NOT NULL
       AND NOT EXISTS (SELECT 1 FROM stg.coleta_record WITH (UPDLOCK, HOLDLOCK)
                       WHERE stage_record_id = @preexisting_stage_record_id)
        THROW 51803, N'Registro genérico preexistente não aceita sidecar tardio.', 1;

    DECLARE @identity_row_fingerprint_version NVARCHAR(128) =
        CASE WHEN @validation_disposition = N'VALID' THEN N'coletas-envelope-v1' END;
    DECLARE @identity_presence_fingerprint_version NVARCHAR(128) =
        CASE WHEN @validation_disposition = N'VALID' THEN N'coletas-presence-v1' END;

    EXEC stg.usp_stage_record
        @execution_id = @execution_id, @input_batch_number = @input_batch_number,
        @input_record_ordinal = @input_record_ordinal, @source_key = @source_key,
        @row_fingerprint_version = @identity_row_fingerprint_version,
        @source_row_hash = @identity_row_hash,
        @presence_fingerprint_version = @identity_presence_fingerprint_version,
        @presence_fingerprint = @identity_presence_hash,
        @source_freshness_at_utc = @freshness_at_utc,
        @validation_disposition = @validation_disposition,
        @quarantine_reason_code = @quarantine_reason_code, @staged_at_utc = @observed_at_utc;

    IF @validation_disposition = N'QUARANTINE'
    BEGIN
        COMMIT TRANSACTION;
        RETURN;
    END;

    DECLARE @stage_record_id BIGINT;
    SELECT @stage_record_id = stage_record_id
    FROM stg.execution_record WITH (UPDLOCK, HOLDLOCK)
    WHERE execution_id = @execution_id AND input_batch_number = @input_batch_number
      AND input_record_ordinal = @input_record_ordinal;
    IF EXISTS (SELECT 1 FROM stg.coleta_record WITH (UPDLOCK, HOLDLOCK)
               WHERE stage_record_id = @stage_record_id AND (
                   attribute_hash <> @attribute_hash OR presence_hash <> @presence_hash
                   OR payload_json <> @payload_json OR field_presence_json <> @field_presence_json
                   OR relation_candidates_json <> @relation_candidates_json
               ))
        THROW 51802, N'Retry tipado de Coletas possui conteúdo divergente.', 1;
    IF NOT EXISTS (SELECT 1 FROM stg.coleta_record WITH (UPDLOCK, HOLDLOCK)
                   WHERE stage_record_id = @stage_record_id)
        INSERT INTO stg.coleta_record (
            stage_record_id, execution_id, source_key, source_key_wire_type,
            sequence_code_presence, sequence_code_json, payload_json, field_presence_json,
            relation_candidates_json, status_raw, status_code, status_label, status_catalog_version,
            terminal, occurrence_action, attempt_count, freshness_raw, freshness_at_utc,
            freshness_origin, attribute_fingerprint_version, attribute_hash,
            presence_fingerprint_version, presence_hash
        ) VALUES (
            @stage_record_id, @execution_id, @source_key, @source_key_wire_type,
            @sequence_code_presence, @sequence_code_json, @payload_json, @field_presence_json,
            @relation_candidates_json, @status_raw, @status_code, @status_label,
            @status_catalog_version, @terminal, @occurrence_action, @attempt_count, @freshness_raw,
            @freshness_at_utc, @freshness_origin, N'coletas-attributes-v1', @attribute_hash,
            N'coletas-presence-v1', @presence_hash
        );
    COMMIT TRANSACTION;
END;
GO

CREATE OR ALTER TRIGGER ctl.trg_coleta_prepare_candidate_set
ON ctl.execution_promotion_result
AFTER INSERT
AS
BEGIN
    SET NOCOUNT ON;
    INSERT INTO ctl.coleta_promotion_result (
        execution_id, generic_candidate_rows, typed_candidate_rows, conflicting_root_keys,
        generic_quarantine_rows, typed_stage_rows, validation_state, validated_at_utc
    )
    SELECT inserted.execution_id, inserted.candidate_rows,
           COALESCE(typed_candidates.typed_candidate_rows, 0),
           COALESCE(conflicts.conflicting_root_keys, 0), inserted.quarantined_root_keys,
           COALESCE(typed_rows.typed_stage_rows, 0),
           CASE WHEN inserted.candidate_rows = COALESCE(typed_candidates.typed_candidate_rows, 0)
                     AND inserted.quarantined_root_keys = 0
                     AND COALESCE(conflicts.conflicting_root_keys, 0) = 0
                THEN N'PASSED' ELSE N'BLOCKED' END,
           SYSUTCDATETIME()
    FROM inserted
    INNER JOIN ctl.execution_attempt AS attempt ON attempt.execution_id = inserted.execution_id
    INNER JOIN ctl.execution_partition AS partition ON partition.partition_id = attempt.partition_id
    OUTER APPLY (SELECT COUNT_BIG(*) AS typed_candidate_rows
                 FROM stg.execution_candidate AS candidate
                 INNER JOIN stg.coleta_record AS typed
                   ON typed.stage_record_id = candidate.winner_stage_record_id
                 WHERE candidate.execution_id = inserted.execution_id) AS typed_candidates
    OUTER APPLY (SELECT COUNT_BIG(*) AS typed_stage_rows
                 FROM stg.coleta_record WHERE execution_id = inserted.execution_id) AS typed_rows
    OUTER APPLY (SELECT COUNT_BIG(*) AS conflicting_root_keys
                 FROM (
                    SELECT source_key FROM stg.coleta_record
                    WHERE execution_id = inserted.execution_id
                    GROUP BY source_key HAVING COUNT(DISTINCT attribute_hash) > 1
                 ) AS grouped) AS conflicts
    WHERE partition.entity_name COLLATE Latin1_General_100_BIN2 = N'coletas';
END;
GO

CREATE OR ALTER PROCEDURE core.usp_apply_reconcile_publish_coletas
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

    DECLARE @environment_name NVARCHAR(32), @source_instance NVARCHAR(128),
            @tenant_scope NVARCHAR(128), @entity_name NVARCHAR(128);
    SELECT @environment_name = partition.environment_name,
           @source_instance = partition.source_instance,
           @tenant_scope = partition.tenant_scope,
           @entity_name = partition.entity_name
    FROM ctl.execution_attempt AS attempt WITH (UPDLOCK, HOLDLOCK)
    INNER JOIN ctl.execution_partition AS partition WITH (UPDLOCK, HOLDLOCK)
        ON partition.partition_id = attempt.partition_id
    WHERE attempt.execution_id = @execution_id;
    IF @entity_name COLLATE Latin1_General_100_BIN2 <> N'coletas'
        THROW 51811, N'A aplicação não pertence à vertical de Coletas.', 1;
    IF NOT EXISTS (SELECT 1 FROM ctl.coleta_promotion_result WITH (UPDLOCK, HOLDLOCK)
                   WHERE execution_id = @execution_id AND validation_state = N'PASSED')
        THROW 51812, N'Candidate set tipado de Coletas não está apto.', 1;
    IF EXISTS (
        SELECT 1 FROM stg.execution_candidate AS candidate
        INNER JOIN stg.coleta_record AS typed ON typed.stage_record_id = candidate.winner_stage_record_id
        INNER JOIN core.coleta AS current_record WITH (UPDLOCK, HOLDLOCK, INDEX(UQ_core_coleta_source))
          ON current_record.environment_name = @environment_name
         AND current_record.source_instance = @source_instance
         AND current_record.tenant_scope = @tenant_scope
         AND current_record.entity_name = @entity_name
         AND current_record.source_key = typed.source_key
        WHERE candidate.execution_id = @execution_id AND typed.sequence_code_presence = N'VALUE'
          AND current_record.sequence_code_presence = N'VALUE'
          AND current_record.sequence_code_json <> typed.sequence_code_json
    )
        THROW 51813, N'Alteração de alias exige evidência aprovada.', 1;

    DECLARE @common_result TABLE (
        execution_id UNIQUEIDENTIFIER NOT NULL, candidate_rows BIGINT NOT NULL,
        inserted_rows BIGINT NOT NULL, updated_rows BIGINT NOT NULL, reactivated_rows BIGINT NOT NULL,
        noop_rows BIGINT NOT NULL, stale_noop_rows BIGINT NOT NULL,
        reconciled_at_utc DATETIME2(3) NOT NULL, published_at_utc DATETIME2(3) NOT NULL,
        incremental_frontier_before_utc DATETIME2(3) NULL, incremental_frontier_after_utc DATETIME2(3) NULL
    );
    INSERT INTO @common_result
    EXEC core.usp_apply_reconcile_publish_execution @execution_id, @contract_version,
         @contract_fingerprint, @configuration_version, @configuration_fingerprint;

    DECLARE @now_utc DATETIME2(3) = SYSUTCDATETIME();
    DECLARE @plan TABLE (
        source_key NVARCHAR(256) COLLATE Latin1_General_100_BIN2 NOT NULL PRIMARY KEY,
        record_state_id BIGINT NOT NULL, coleta_id BIGINT NULL, stage_record_id BIGINT NOT NULL,
        application_disposition NVARCHAR(24) COLLATE Latin1_General_100_BIN2 NOT NULL,
        apply_values BIT NOT NULL
    );
    INSERT INTO @plan (source_key, record_state_id, coleta_id, stage_record_id,
                       application_disposition, apply_values)
    SELECT candidate.source_key, generic_application.record_state_id, current_record.coleta_id,
           typed.stage_record_id,
           CASE WHEN current_record.coleta_id IS NULL THEN N'INSERTED'
                WHEN current_record.terminal = 0 AND typed.terminal = 1 THEN N'UPDATED'
                WHEN generic_application.application_disposition = N'STALE_NO_OP' THEN N'STALE_NO_OP'
                WHEN current_record.terminal = 1 AND typed.terminal = 0 THEN N'NO_OP'
                WHEN current_record.attribute_hash = typed.attribute_hash THEN N'NO_OP'
                ELSE N'UPDATED' END,
           CASE WHEN current_record.coleta_id IS NULL THEN 1
                WHEN current_record.terminal = 0 AND typed.terminal = 1 THEN 1
                WHEN generic_application.application_disposition = N'STALE_NO_OP' THEN 0
                WHEN current_record.terminal = 1 AND typed.terminal = 0 THEN 0
                WHEN current_record.attribute_hash = typed.attribute_hash THEN 0 ELSE 1 END
    FROM stg.execution_candidate AS candidate
    INNER JOIN stg.coleta_record AS typed ON typed.stage_record_id = candidate.winner_stage_record_id
    INNER JOIN recon.execution_candidate_application AS generic_application
      ON generic_application.execution_id = candidate.execution_id
     AND generic_application.source_key = candidate.source_key
    LEFT JOIN core.coleta AS current_record WITH (UPDLOCK, HOLDLOCK, INDEX(UQ_core_coleta_source))
      ON current_record.environment_name = @environment_name
     AND current_record.source_instance = @source_instance
     AND current_record.tenant_scope = @tenant_scope
     AND current_record.entity_name = @entity_name
     AND current_record.source_key = candidate.source_key
    WHERE candidate.execution_id = @execution_id;
    IF (SELECT COUNT_BIG(*) FROM @plan) <> (SELECT candidate_rows FROM @common_result)
        THROW 51814, N'O plano tipado não cobre todo o candidate set de Coletas.', 1;

    INSERT INTO core.coleta (
        record_state_id, environment_name, source_instance, tenant_scope, entity_name, source_key,
        source_key_wire_type, sequence_code_presence, sequence_code_json, payload_json,
        field_presence_json, relation_candidates_json, status_raw, status_code, status_label,
        status_catalog_version, terminal, occurrence_action, attempt_count, freshness_raw,
        freshness_at_utc, freshness_origin, attribute_fingerprint_version, attribute_hash,
        state_fingerprint_version, state_hash, active, first_seen_execution_id, last_seen_execution_id,
        last_changed_execution_id, first_seen_at_utc, last_seen_at_utc, last_changed_at_utc
    )
    SELECT planned.record_state_id, @environment_name, @source_instance, @tenant_scope, @entity_name,
           typed.source_key, typed.source_key_wire_type, typed.sequence_code_presence,
           typed.sequence_code_json, typed.payload_json, typed.field_presence_json,
           typed.relation_candidates_json, typed.status_raw, typed.status_code, typed.status_label,
           typed.status_catalog_version, typed.terminal, typed.occurrence_action, typed.attempt_count,
           typed.freshness_raw, typed.freshness_at_utc, typed.freshness_origin,
           typed.attribute_fingerprint_version, typed.attribute_hash, N'coletas-state-v1',
            LOWER(CONVERT(CHAR(64), HASHBYTES('SHA2_256', CONVERT(VARBINARY(MAX), CONCAT(
                N'coletas-state-v1|', typed.attribute_hash, N'|active=1'
            ))), 2)), 1, @execution_id, @execution_id, @execution_id, @now_utc, @now_utc, @now_utc
    FROM @plan AS planned INNER JOIN stg.coleta_record AS typed
      ON typed.stage_record_id = planned.stage_record_id
    WHERE planned.application_disposition = N'INSERTED';

    UPDATE current_record
    SET sequence_code_presence = CASE WHEN typed.sequence_code_presence = N'ABSENT'
                                      THEN current_record.sequence_code_presence
                                      ELSE typed.sequence_code_presence END,
        sequence_code_json = CASE WHEN typed.sequence_code_presence = N'ABSENT'
                                  THEN current_record.sequence_code_json
                                  ELSE typed.sequence_code_json END,
        payload_json = typed.payload_json,
        field_presence_json = typed.field_presence_json, relation_candidates_json = typed.relation_candidates_json,
        status_raw = CASE WHEN JSON_VALUE(typed.field_presence_json, N'$.status') = N'ABSENT'
                          THEN current_record.status_raw ELSE typed.status_raw END,
        status_code = CASE WHEN JSON_VALUE(typed.field_presence_json, N'$.status') = N'ABSENT'
                           THEN current_record.status_code ELSE typed.status_code END,
        status_label = CASE WHEN JSON_VALUE(typed.field_presence_json, N'$.status') = N'ABSENT'
                            THEN current_record.status_label ELSE typed.status_label END,
        status_catalog_version = CASE WHEN JSON_VALUE(typed.field_presence_json, N'$.status') = N'ABSENT'
                                      THEN current_record.status_catalog_version
                                      ELSE typed.status_catalog_version END,
        terminal = CASE WHEN JSON_VALUE(typed.field_presence_json, N'$.status') = N'ABSENT'
                        THEN current_record.terminal ELSE typed.terminal END,
        occurrence_action = CASE WHEN JSON_VALUE(typed.field_presence_json, N'$.status') = N'ABSENT'
                                 THEN current_record.occurrence_action ELSE typed.occurrence_action END,
        attempt_count = CASE WHEN JSON_VALUE(typed.field_presence_json, N'$.status') = N'ABSENT'
                             THEN current_record.attempt_count ELSE typed.attempt_count END,
        freshness_raw = COALESCE(typed.freshness_raw, current_record.freshness_raw),
        freshness_at_utc = CASE WHEN typed.freshness_origin = N'UNAVAILABLE'
                                THEN current_record.freshness_at_utc ELSE typed.freshness_at_utc END,
        freshness_origin = CASE WHEN typed.freshness_origin = N'UNAVAILABLE'
                                THEN current_record.freshness_origin ELSE typed.freshness_origin END,
        attribute_fingerprint_version = typed.attribute_fingerprint_version,
        attribute_hash = typed.attribute_hash,
        state_hash = LOWER(CONVERT(CHAR(64), HASHBYTES('SHA2_256', CONVERT(VARBINARY(MAX), CONCAT(
            N'coletas-state-v1|', typed.attribute_hash, N'|active=1'
        ))), 2)), last_seen_execution_id = @execution_id, last_changed_execution_id = @execution_id,
        last_seen_at_utc = @now_utc, last_changed_at_utc = @now_utc
    FROM core.coleta AS current_record INNER JOIN @plan AS planned
      ON planned.coleta_id = current_record.coleta_id
    INNER JOIN stg.coleta_record AS typed ON typed.stage_record_id = planned.stage_record_id
    WHERE planned.application_disposition = N'UPDATED' AND planned.apply_values = 1;

    UPDATE current_record SET last_seen_execution_id = @execution_id, last_seen_at_utc = @now_utc
    FROM core.coleta AS current_record INNER JOIN @plan AS planned
      ON planned.coleta_id = current_record.coleta_id
    WHERE planned.application_disposition IN (N'NO_OP', N'STALE_NO_OP');

    UPDATE planned SET coleta_id = current_record.coleta_id
    FROM @plan AS planned INNER JOIN core.coleta AS current_record
      ON current_record.environment_name = @environment_name
     AND current_record.source_instance = @source_instance
     AND current_record.tenant_scope = @tenant_scope
     AND current_record.entity_name = @entity_name AND current_record.source_key = planned.source_key;
    IF EXISTS (SELECT 1 FROM @plan WHERE coleta_id IS NULL)
        THROW 51815, N'A aplicação não resolveu canonical_id de Coletas.', 1;

    INSERT INTO ref.coleta_sequence_code_alias (
        coleta_id, sequence_code_json, observed_execution_id, valid_from_utc, valid_to_utc, provenance
    )
    SELECT planned.coleta_id, typed.sequence_code_json, @execution_id, @now_utc, NULL,
           N'dataexport-6908-v02'
    FROM @plan AS planned INNER JOIN stg.coleta_record AS typed
      ON typed.stage_record_id = planned.stage_record_id
    WHERE typed.sequence_code_presence = N'VALUE'
      AND NOT EXISTS (SELECT 1 FROM ref.coleta_sequence_code_alias AS alias
                      WHERE alias.coleta_id = planned.coleta_id AND alias.valid_to_utc IS NULL);

    INSERT INTO recon.coleta_root_presence_observation (
        execution_id, coleta_id, observed_at_utc, snapshot_completeness, absence_evaluation
    )
    SELECT @execution_id, coleta_id, @now_utc, N'BLOCKED_NO_COMPLETENESS_PROOF', N'NOT_EVALUATED'
    FROM @plan;

    DECLARE @inserted_rows BIGINT = (SELECT COUNT_BIG(*) FROM @plan WHERE application_disposition = N'INSERTED');
    DECLARE @updated_rows BIGINT = (SELECT COUNT_BIG(*) FROM @plan WHERE application_disposition = N'UPDATED');
    DECLARE @noop_rows BIGINT = (SELECT COUNT_BIG(*) FROM @plan WHERE application_disposition IN (N'NO_OP', N'STALE_NO_OP'));
    DECLARE @stale_noop_rows BIGINT = (SELECT COUNT_BIG(*) FROM @plan WHERE application_disposition = N'STALE_NO_OP');
    COMMIT TRANSACTION;
    SELECT @execution_id AS execution_id, (SELECT candidate_rows FROM @common_result) AS candidate_rows,
           @inserted_rows AS inserted_rows, @updated_rows AS updated_rows,
           CAST(0 AS BIGINT) AS reactivated_rows, @noop_rows AS noop_rows,
           @stale_noop_rows AS stale_noop_rows, reconciled_at_utc, published_at_utc,
           incremental_frontier_before_utc, incremental_frontier_after_utc FROM @common_result;
END;
GO

EXEC dbo.usp_publish_v2_procedure_grant N'stg', N'usp_stage_coleta_record', N'v2_runtime';
EXEC dbo.usp_publish_v2_procedure_grant N'core', N'usp_apply_reconcile_publish_coletas', N'v2_runtime';
DENY INSERT, UPDATE, DELETE, SELECT ON SCHEMA::stg TO v2_runtime;
DENY INSERT, UPDATE, DELETE, SELECT ON SCHEMA::core TO v2_runtime;
DENY INSERT, UPDATE, DELETE, SELECT ON SCHEMA::recon TO v2_runtime;
GO
