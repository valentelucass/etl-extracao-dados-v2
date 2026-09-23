-- B60: additive logical-source protocol bindings and Users recovery.
-- Historical source_kind, execution keys and fingerprint material are preserved.
SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;
SET XACT_ABORT ON;
GO
CREATE TABLE ctl.source_protocol_binding (
    source_instance NVARCHAR(128) COLLATE Latin1_General_100_BIN2 NOT NULL,
    source_kind NVARCHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL,
    registered_at_utc DATETIME2(3) NOT NULL,
    CONSTRAINT PK_ctl_source_protocol_binding PRIMARY KEY(source_instance,source_kind),
    CONSTRAINT FK_ctl_source_protocol_binding_catalog FOREIGN KEY(source_instance)
        REFERENCES ctl.source_catalog(source_instance),
    CONSTRAINT CK_ctl_source_protocol_binding_kind CHECK(
        LEN(source_kind)>0 AND DATALENGTH(source_kind)=DATALENGTH(LTRIM(RTRIM(source_kind))))
);
INSERT ctl.source_protocol_binding(source_instance,source_kind,registered_at_utc)
SELECT source_instance,source_kind,registered_at_utc FROM ctl.source_catalog;
GO
CREATE TRIGGER ctl.trg_source_protocol_binding_immutable ON ctl.source_protocol_binding
AFTER UPDATE,DELETE AS
BEGIN
    SET NOCOUNT ON;
    IF EXISTS(SELECT 1 FROM deleted) THROW 52961,N'SOURCE_PROTOCOL_BINDING_IMMUTABLE',1;
END;
GO
CREATE TRIGGER ctl.trg_source_catalog_identity_immutable ON ctl.source_catalog
AFTER UPDATE,DELETE AS
BEGIN
    SET NOCOUNT ON;
    IF EXISTS(SELECT 1 FROM deleted d LEFT JOIN inserted i ON i.source_instance=d.source_instance
        WHERE i.source_instance IS NULL OR i.source_kind<>d.source_kind
            OR DATALENGTH(i.source_kind)<>DATALENGTH(d.source_kind)
            OR i.registered_at_utc<>d.registered_at_utc)
        THROW 52962,N'SOURCE_CATALOG_IDENTITY_IMMUTABLE',1;
END;
GO
CREATE TABLE ctl.execution_source_protocol (
    execution_id UNIQUEIDENTIFIER NOT NULL,
    source_instance NVARCHAR(128) COLLATE Latin1_General_100_BIN2 NOT NULL,
    source_kind NVARCHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL,
    binding_origin NVARCHAR(16) COLLATE Latin1_General_100_BIN2 NOT NULL,
    CONSTRAINT PK_ctl_execution_source_protocol PRIMARY KEY(execution_id),
    CONSTRAINT FK_ctl_execution_source_protocol_attempt FOREIGN KEY(execution_id)
        REFERENCES ctl.execution_attempt(execution_id),
    CONSTRAINT FK_ctl_execution_source_protocol_binding FOREIGN KEY(source_instance,source_kind)
        REFERENCES ctl.source_protocol_binding(source_instance,source_kind),
    CONSTRAINT CK_ctl_execution_source_protocol_origin CHECK(binding_origin IN(N'LEGACY',N'V024'))
);
INSERT ctl.execution_source_protocol(execution_id,source_instance,source_kind,binding_origin)
SELECT a.execution_id,p.source_instance,s.source_kind,N'LEGACY'
FROM ctl.execution_attempt a JOIN ctl.execution_partition p ON p.partition_id=a.partition_id
JOIN ctl.source_catalog s ON s.source_instance=p.source_instance;
GO
CREATE TRIGGER ctl.trg_execution_source_protocol_immutable ON ctl.execution_source_protocol
AFTER UPDATE,DELETE AS
BEGIN
    SET NOCOUNT ON;
    IF EXISTS(SELECT 1 FROM deleted) THROW 52963,N'EXECUTION_SOURCE_PROTOCOL_IMMUTABLE',1;
END;
GO
CREATE TRIGGER ctl.trg_execution_attempt_bind_protocol ON ctl.execution_attempt
AFTER INSERT AS
BEGIN
    SET NOCOUNT ON;
    INSERT ctl.execution_source_protocol(execution_id,source_instance,source_kind,binding_origin)
    SELECT a.execution_id,p.source_instance,
        CASE WHEN p.entity_name=N'usuarios' THEN N'GRAPHQL'
            WHEN p.entity_name IN(N'coletas',N'fretes',N'manifestos',N'cotacoes',N'localizacao_cargas')
                THEN N'DATA_EXPORT' ELSE s.source_kind END,N'V024'
    FROM inserted a JOIN ctl.execution_partition p ON p.partition_id=a.partition_id
    JOIN ctl.source_catalog s ON s.source_instance=p.source_instance;
    -- The composite FK rejects an unregistered protocol atomically with the start.
END;
GO
CREATE OR ALTER PROCEDURE ctl.usp_control_plane_register_source
    @source_instance NVARCHAR(MAX),@source_kind NVARCHAR(MAX),@registered_at_utc DATETIME2(3)
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    DECLARE @actual NVARCHAR(MAX)=(SELECT @source_instance AS [source]
        FOR JSON PATH,WITHOUT_ARRAY_WRAPPER,INCLUDE_NULL_VALUES);
    IF ctl.fn_runtime_consumed_scope(@actual,1,NULL)<>1
        THROW 52500,N'RUNTIME_DURABLE_CONSUMER_FENCE_REQUIRED',1;
    IF DATALENGTH(@source_instance)>256 OR DATALENGTH(@source_kind)>128
        OR NULLIF(LTRIM(RTRIM(@source_instance)),N'') IS NULL
        OR NULLIF(LTRIM(RTRIM(@source_kind)),N'') IS NULL OR @registered_at_utc IS NULL
        THROW 51300,N'SOURCE_CATALOG_INPUT_INVALID',1;
    SET @source_instance=LTRIM(RTRIM(@source_instance));
    SET @source_kind=LTRIM(RTRIM(@source_kind));
    IF ISNULL(IS_SRVROLEMEMBER(N'sysadmin'),0)<>1 AND ISNULL(IS_MEMBER(N'db_owner'),0)<>1
    BEGIN
        IF NOT EXISTS(SELECT 1 FROM (VALUES
            (N'usuarios',N'GRAPHQL'),(N'coletas',N'DATA_EXPORT'),(N'fretes',N'DATA_EXPORT'),
            (N'manifestos',N'DATA_EXPORT'),(N'cotacoes',N'DATA_EXPORT'),(N'localizacao_cargas',N'DATA_EXPORT')) v(entity,protocol)
            CROSS APPLY(SELECT @source_instance AS [source],v.entity AS [entity],v.entity AS [workload]
                FOR JSON PATH,WITHOUT_ARRAY_WRAPPER) j(actual)
            WHERE v.protocol COLLATE Latin1_General_100_BIN2=@source_kind COLLATE Latin1_General_100_BIN2
                AND ctl.fn_runtime_consumed_scope(j.actual,1,NULL)=1)
            THROW 52964,N'SOURCE_PROTOCOL_SCOPE_MISMATCH',1;
    END;
    BEGIN TRY
        BEGIN TRANSACTION;
        DECLARE @existing_kind NVARCHAR(64),@active BIT;
        SELECT @existing_kind=source_kind,@active=active FROM ctl.source_catalog WITH(UPDLOCK,HOLDLOCK)
        WHERE source_instance=@source_instance;
        DECLARE @now DATETIME2(3)=SYSUTCDATETIME();
        IF @existing_kind IS NULL
        BEGIN
            INSERT ctl.source_catalog(source_instance,source_kind,active,registered_at_utc)
            VALUES(@source_instance,@source_kind,1,@now);
            INSERT ctl.source_protocol_binding(source_instance,source_kind,registered_at_utc)
            VALUES(@source_instance,@source_kind,@now);
        END
        ELSE IF @active<>1 OR NOT EXISTS(SELECT 1 FROM ctl.source_protocol_binding WITH(HOLDLOCK)
            WHERE source_instance=@source_instance AND source_kind=@source_kind)
            THROW 51301,N'SOURCE_PROTOCOL_NOT_REGISTERED',1;
        COMMIT TRANSACTION;
    END TRY
    BEGIN CATCH
        IF XACT_STATE()<>0 ROLLBACK TRANSACTION;
        THROW;
    END CATCH;
END;
GO
ALTER TABLE ctl.runtime_contract_evidence DROP CONSTRAINT CK_ctl_runtime_contract_evidence_counts;
ALTER TABLE ctl.runtime_contract_evidence ADD CONSTRAINT CK_ctl_runtime_contract_evidence_counts
    CHECK(audited_rows>0 AND audited_pages>=1);
GO
CREATE TRIGGER ctl.trg_runtime_contract_evidence_protocol_gate ON ctl.runtime_contract_evidence
AFTER INSERT AS
BEGIN
    SET NOCOUNT ON;
    IF EXISTS(SELECT 1 FROM inserted e
        JOIN ctl.execution_attempt a ON a.execution_id=e.execution_id
        JOIN ctl.execution_partition p ON p.partition_id=a.partition_id
        LEFT JOIN ctl.execution_source_protocol b ON b.execution_id=a.execution_id
        WHERE b.execution_id IS NULL OR (p.entity_name<>N'usuarios' AND e.audited_pages<2)
            OR (p.entity_name=N'usuarios' AND (b.source_kind<>N'GRAPHQL' OR a.current_state<>N'PROMOTED'
                OR p.execution_mode NOT IN(N'BACKFILL',N'REPLAY') OR a.window_strategy<>N'FULL'
                OR NOT EXISTS(SELECT 1 FROM recon.execution_data_quality_evaluation q
                    JOIN ctl.usuario_promotion_result t ON t.execution_id=q.execution_id
                    WHERE q.execution_id=e.execution_id AND q.evaluation_state=N'PASSED'
                        AND q.policy_version=e.policy_version AND q.policy_fingerprint=e.policy_fingerprint
                        AND q.expected_checks=4 AND q.completed_checks=4 AND q.passed_checks=4
                        AND q.failed_checks=0 AND q.failed_rows=0 AND t.validation_state=N'PASSED'
                        AND t.typed_candidate_rows=q.candidate_rows))))
        THROW 52965,N'PROTOCOL_TRAVERSAL_SEAL_GATE_REJECTED',1;
END;
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
    -- B60_STAGE_FENCE_BEGIN
    DECLARE @b60_actual NVARCHAR(MAX)=(SELECT LOWER(CONVERT(NVARCHAR(36),@execution_id)) AS [execution],
        N'usuarios' AS [entity],N'usuarios' AS [workload] FOR JSON PATH,WITHOUT_ARRAY_WRAPPER,INCLUDE_NULL_VALUES);
    IF ctl.fn_runtime_consumed_scope(@b60_actual,1,NULL)<>1
        THROW 52500,N'RUNTIME_DURABLE_CONSUMER_FENCE_REQUIRED',1;
    IF @execution_id IS NULL OR @validation_disposition IS NULL
        OR (@validation_disposition=N'VALID' AND @name_presence IS NULL)
        THROW 52966,N'USERS_STAGE_NULL_INPUT',1;
    -- B60_STAGE_FENCE_END
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

CREATE OR ALTER PROCEDURE core.usp_apply_reconcile_publish_usuarios
    @execution_id UNIQUEIDENTIFIER,
    @contract_version NVARCHAR(MAX),
    @contract_fingerprint NVARCHAR(MAX),
    @configuration_version NVARCHAR(MAX),
    @configuration_fingerprint NVARCHAR(MAX)
AS
BEGIN
    -- B60_APPLY_FENCE_BEGIN
    DECLARE @b60_actual NVARCHAR(MAX)=(SELECT LOWER(CONVERT(NVARCHAR(36),@execution_id)) AS [execution],
        N'usuarios' AS [entity],N'usuarios' AS [workload],@contract_version AS [contractVersion],
        @contract_fingerprint AS [contractHash],@configuration_version AS [configurationVersion],
        @configuration_fingerprint AS [configurationHash]
        FOR JSON PATH,WITHOUT_ARRAY_WRAPPER,INCLUDE_NULL_VALUES);
    IF ctl.fn_runtime_consumed_scope(@b60_actual,1,NULL)<>1
        THROW 52500,N'RUNTIME_DURABLE_CONSUMER_FENCE_REQUIRED',1;
    -- B60_APPLY_FENCE_END
    SET NOCOUNT ON;
    SET XACT_ABORT ON;

    BEGIN TRANSACTION;
    -- B60_APPLY_LOCK_BEGIN
    DECLARE @b60_namespace NVARCHAR(1800);
    SELECT @b60_namespace=CONCAT(DATALENGTH(p.environment_name),N':',p.environment_name,N'|',
        DATALENGTH(p.source_instance),N':',p.source_instance,N'|',
        DATALENGTH(p.tenant_scope),N':',p.tenant_scope,N'|',DATALENGTH(p.entity_name),N':',p.entity_name)
    FROM ctl.execution_attempt a JOIN ctl.execution_partition p ON p.partition_id=a.partition_id
    WHERE a.execution_id=@execution_id;
    IF @b60_namespace IS NULL THROW 52967,N'USERS_APPLY_OCCURRENCE_MISSING',1;
    DECLARE @b60_resource NVARCHAR(255)=N'V2_APPLY_'+CONVERT(NVARCHAR(64),HASHBYTES('SHA2_256',@b60_namespace),2);
    DECLARE @b60_lock INT;
    EXEC @b60_lock=sys.sp_getapplock @Resource=@b60_resource,@LockMode=N'Exclusive',
        @LockOwner=N'Transaction',@LockTimeout=10000,@DbPrincipal=N'public';
    IF @b60_lock<0 THROW 52302,N'RUNTIME_RECOVERY_LOCK_UNAVAILABLE',1;
    -- B60_APPLY_LOCK_END

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

    -- B60_APPLY_SEAL_BEGIN
    IF NOT EXISTS(SELECT 1 FROM ctl.runtime_contract_evidence e WITH(HOLDLOCK)
        JOIN ctl.execution_source_protocol b WITH(HOLDLOCK) ON b.execution_id=e.execution_id
        JOIN recon.execution_data_quality_evaluation q WITH(HOLDLOCK) ON q.execution_id=e.execution_id
        WHERE e.execution_id=@execution_id AND b.source_kind=N'GRAPHQL'
            AND e.identity_material=ctl.fn_runtime_recovery_identity(@execution_id)
            AND e.contract_material LIKE N'runtime-recovery-v1|14:GRAPHQL|44:graphql-users-snapshot|%'
            AND q.policy_version=e.policy_version AND q.policy_fingerprint=e.policy_fingerprint
            AND q.evaluation_state=N'PASSED' AND q.expected_checks=4 AND q.completed_checks=4
            AND q.passed_checks=4 AND q.failed_checks=0 AND q.failed_rows=0)
        THROW 52968,N'USERS_APPLY_DURABLE_SEAL_REQUIRED',1;
    -- B60_APPLY_SEAL_END

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

CREATE OR ALTER PROCEDURE ctl.usp_runtime_recovery
    @execution_id UNIQUEIDENTIFIER,
    @operation NVARCHAR(MAX),
    @expected_identity NVARCHAR(MAX),
    @contract_material NVARCHAR(MAX),
    @policy_version NVARCHAR(MAX),
    @policy_fingerprint NVARCHAR(MAX),
    @expected_revision NVARCHAR(MAX),
    @entity NVARCHAR(MAX),@reference_release_id BIGINT=NULL
AS
BEGIN
    -- B54: validate current SQL authority consumption before any effect/read.
    DECLARE @b54_actual NVARCHAR(MAX)=(SELECT LOWER(COALESCE(CONVERT(NVARCHAR(36),@execution_id),N'')) AS [execution] FOR JSON PATH,WITHOUT_ARRAY_WRAPPER,INCLUDE_NULL_VALUES);
    IF ctl.fn_runtime_consumed_scope(@b54_actual,CASE WHEN @operation=N'READ' THEN 0 ELSE 1 END,NULL)<>1 THROW 52500,N'RUNTIME_DURABLE_CONSUMER_FENCE_REQUIRED',1;

    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    IF @operation IS NULL OR @operation COLLATE Latin1_General_100_BIN2 NOT IN(N'READ',N'SEAL',N'RESUME')
        OR @expected_identity IS NULL OR DATALENGTH(@expected_identity) NOT BETWEEN 2 AND 8000
        OR @contract_material IS NULL OR DATALENGTH(@contract_material) NOT BETWEEN 2 AND 8000
        OR @policy_version IS NULL OR DATALENGTH(@policy_version) NOT BETWEEN 2 AND 256
        OR @policy_fingerprint IS NULL OR DATALENGTH(@policy_fingerprint)<>128
        OR @policy_fingerprint COLLATE Latin1_General_100_BIN2 LIKE N'%[^0-9a-f]%'
        OR @entity IS NULL OR @entity COLLATE Latin1_General_100_BIN2 NOT IN(N'coletas',N'fretes',N'manifestos',N'cotacoes',N'localizacao_cargas',N'usuarios')
        THROW 52301,N'RUNTIME_RECOVERY_INPUT_INVALID',1;
    IF @entity=N'cotacoes' AND ctl.fn_runtime_tariff_valid(@execution_id,@reference_release_id)<>1
       THROW 52843,N'TARIFF_SCOPE_OR_REFERENCE_REJECTED',1;
    IF @entity<>N'cotacoes' AND @reference_release_id IS NOT NULL THROW 52843,N'TARIFF_ENTITY_MISMATCH',1;
    IF @entity=N'usuarios' AND @contract_material COLLATE Latin1_General_100_BIN2
        NOT LIKE N'runtime-recovery-v1|14:GRAPHQL|44:graphql-users-snapshot|%'
        THROW 52959,N'USERS_RECOVERY_OPERATION_MISMATCH',1;
    BEGIN TRY
        BEGIN TRANSACTION;
        DECLARE @environment_name NVARCHAR(32),@source_instance NVARCHAR(128),
            @tenant_scope NVARCHAR(128),@entity_name NVARCHAR(128),@partition_id BIGINT;
        SELECT @environment_name=p.environment_name,@source_instance=p.source_instance,
            @tenant_scope=p.tenant_scope,@entity_name=p.entity_name,@partition_id=p.partition_id
        FROM ctl.execution_attempt a JOIN ctl.execution_partition p ON p.partition_id=a.partition_id
        WHERE a.execution_id=@execution_id;
        DECLARE @lock_payload NVARCHAR(1800)=CONCAT(
            DATALENGTH(@environment_name),N':',@environment_name,N'|',
            DATALENGTH(@source_instance),N':',@source_instance,N'|',
            DATALENGTH(@tenant_scope),N':',@tenant_scope,N'|',
            DATALENGTH(@entity_name),N':',@entity_name);
        DECLARE @lock_resource NVARCHAR(255)=N'V2_APPLY_'+CONVERT(NVARCHAR(64),HASHBYTES('SHA2_256',@lock_payload),2);
        DECLARE @lock_result INT;
        EXEC @lock_result=sys.sp_getapplock @Resource=@lock_resource,@LockMode=N'Exclusive',
            @LockOwner=N'Transaction',@LockTimeout=10000,@DbPrincipal=N'public';
        IF @lock_result<0 THROW 52302,N'RUNTIME_RECOVERY_LOCK_UNAVAILABLE',1;
        DECLARE @execution_state NVARCHAR(32),@current_execution UNIQUEIDENTIFIER,@partition_state NVARCHAR(32),
            @next_transition_sequence INT,@contract_version NVARCHAR(128),@contract_fingerprint CHAR(64),
            @configuration_version NVARCHAR(128),@configuration_fingerprint CHAR(64),@mode NVARCHAR(16);
        SELECT @execution_state=a.current_state,@next_transition_sequence=a.next_transition_sequence,
            @contract_version=a.contract_version,@contract_fingerprint=a.contract_fingerprint,
            @configuration_version=a.configuration_version,@configuration_fingerprint=a.configuration_fingerprint,
            @current_execution=p.current_execution_id,@partition_state=p.current_state,@mode=p.execution_mode
        FROM ctl.execution_attempt a WITH(UPDLOCK,HOLDLOCK)
        JOIN ctl.execution_partition p WITH(UPDLOCK,HOLDLOCK) ON p.partition_id=a.partition_id
        JOIN ctl.execution_cycle c WITH(HOLDLOCK) ON c.cycle_id=a.cycle_id
        WHERE a.execution_id=@execution_id;
        DECLARE @attempt_exists BIT=CASE WHEN EXISTS(SELECT 1 FROM ctl.execution_attempt WITH(HOLDLOCK)
            WHERE execution_id=@execution_id) THEN 1 ELSE 0 END;
        DECLARE @identity NVARCHAR(MAX)=ctl.fn_runtime_recovery_identity(@execution_id);
        DECLARE @now DATETIME2(3)=SYSUTCDATETIME(),@lease_id UNIQUEIDENTIFIER,
            @expires DATETIME2(3),@released DATETIME2(3),@lease_valid BIT=0;
        SELECT @lease_id=lease_id,@expires=expires_at_utc,@released=released_at_utc
        FROM ctl.execution_lease WITH(UPDLOCK,HOLDLOCK)
        WHERE execution_id=@execution_id AND partition_id=@partition_id;
        IF @released IS NULL AND @expires>@now AND @current_execution=@execution_id
            AND @partition_state=@execution_state SET @lease_valid=1;
        DECLARE @unique_pages INT,@pages BIGINT,@rows BIGINT,@last_page INT,@first_page INT,@terminal INT,@terminal_page INT;
        SELECT @unique_pages=COUNT(DISTINCT page_number),@pages=COUNT_BIG(*),@rows=COALESCE(SUM(physical_rows),0),
            @last_page=MAX(page_number),@first_page=MIN(page_number),
            @terminal=SUM(CASE WHEN terminal_evidence_kind=N'DATA_EXPORT_EMPTY_PAGE' AND terminal_empty_page=1
                AND physical_rows=0 THEN 1 ELSE 0 END),
            @terminal_page=MAX(CASE WHEN terminal_evidence_kind=N'DATA_EXPORT_EMPTY_PAGE' THEN page_number END)
        FROM ctl.execution_page_audit WITH(HOLDLOCK) WHERE execution_id=@execution_id;
        -- Users has populated local terminality. It never inherits the DE empty-page proof.
        IF @entity=N'usuarios'
            SELECT @terminal=SUM(CASE WHEN terminal_evidence_kind=N'GRAPHQL_PAGE_INFO'
                    AND terminal_empty_page=0 AND physical_rows BETWEEN 1 AND 20 THEN 1 ELSE 0 END),
                @terminal_page=MAX(CASE WHEN terminal_evidence_kind=N'GRAPHQL_PAGE_INFO' THEN page_number END)
            FROM ctl.execution_page_audit WITH(HOLDLOCK) WHERE execution_id=@execution_id;
        DECLARE @audit_hash CHAR(64);
        SELECT @audit_hash=LOWER(CONVERT(CHAR(64),HASHBYTES('SHA2_256',STRING_AGG(
            CAST(CONCAT(page_number,N':',page_attempt,N':',requested_page_size,N':',physical_rows,N':',
                distinct_root_keys,N':',response_bytes,N':',terminal_empty_page,N':',terminal_evidence_kind,
                N':',CONVERT(NVARCHAR(23),read_at_utc,126)) AS NVARCHAR(MAX)),N'|')
                WITHIN GROUP(ORDER BY page_number,page_attempt)),2))
        FROM ctl.execution_page_audit WITH(HOLDLOCK) WHERE execution_id=@execution_id;
        DECLARE @evidence_hash CHAR(64)=LOWER(CONVERT(CHAR(64),HASHBYTES('SHA2_256',CONCAT(
            @identity,@audit_hash,ctl.fn_runtime_recovery_field(@contract_material),
            ctl.fn_runtime_recovery_field(@policy_version),ctl.fn_runtime_recovery_field(@policy_fingerprint),
            ctl.fn_runtime_recovery_field(CONVERT(NVARCHAR(20),@rows)),
            ctl.fn_runtime_recovery_field(CONVERT(NVARCHAR(20),@pages)))),2));
        DECLARE @identity_valid BIT=CASE WHEN @identity COLLATE Latin1_General_100_BIN2=@expected_identity COLLATE Latin1_General_100_BIN2
            AND DATALENGTH(@identity)=DATALENGTH(@expected_identity) AND @entity_name=@entity THEN 1 ELSE 0 END;
        DECLARE @audit_valid BIT=CASE WHEN @pages>=2 AND @rows>0 AND @pages=@unique_pages AND @pages=@last_page AND @first_page=1
            AND @terminal=1 AND @terminal_page=@last_page THEN 1 ELSE 0 END;
        IF @entity=N'usuarios'
        BEGIN
            SET @audit_valid=CASE WHEN @pages>=1 AND @rows>0 AND @pages=@unique_pages
                AND @pages=@last_page AND @first_page=1 AND @terminal=1 AND @terminal_page=@last_page
                AND NOT EXISTS(SELECT 1 FROM ctl.execution_page_audit WITH(HOLDLOCK)
                    WHERE execution_id=@execution_id AND (requested_page_size<>20 OR page_attempt<>1
                        OR physical_rows NOT BETWEEN 1 AND 20 OR terminal_empty_page<>0
                        OR terminal_evidence_kind NOT IN(N'NONE',N'GRAPHQL_PAGE_INFO')))
                THEN 1 ELSE 0 END;
            IF @attempt_exists=1 AND (@mode NOT IN(N'BACKFILL',N'REPLAY') OR NOT EXISTS(
                SELECT 1 FROM ctl.execution_attempt a WITH(HOLDLOCK)
                JOIN ctl.execution_source_protocol s WITH(HOLDLOCK) ON s.execution_id=a.execution_id AND s.source_instance=@source_instance
                WHERE a.execution_id=@execution_id AND a.window_strategy=N'FULL' AND s.source_kind=N'GRAPHQL'))
                SET @identity_valid=0;
        END;
        IF @operation=N'SEAL' AND @entity<>N'usuarios'
        BEGIN
            IF @identity_valid=0 OR @execution_state<>N'EXTRACTING' OR @lease_valid=0 OR @audit_valid=0
                OR @rows<>CASE WHEN @entity=N'manifestos' THEN
                   (SELECT COUNT_BIG(*) FROM stg.manifesto_observation WITH(HOLDLOCK) WHERE execution_id=@execution_id)
                   ELSE (SELECT COUNT_BIG(*) FROM stg.execution_record WITH(HOLDLOCK) WHERE execution_id=@execution_id) END
                THROW 52303,N'RUNTIME_RECOVERY_SEAL_GATE_REJECTED',1;
            IF EXISTS(SELECT 1 FROM ctl.runtime_contract_evidence WITH(UPDLOCK,HOLDLOCK)
                WHERE execution_id=@execution_id AND evidence_hash<>@evidence_hash)
                THROW 52304,N'RUNTIME_RECOVERY_EVIDENCE_CONFLICT',1;
            IF NOT EXISTS(SELECT 1 FROM ctl.runtime_contract_evidence WITH(UPDLOCK,HOLDLOCK) WHERE execution_id=@execution_id)
                INSERT ctl.runtime_contract_evidence VALUES(@execution_id,@identity,@contract_material,
                    @policy_version,@policy_fingerprint,@rows,@pages,@audit_hash,@evidence_hash,@now);
            IF @entity=N'cotacoes' AND NOT EXISTS(SELECT 1 FROM ctl.runtime_cotacao_reference WITH(UPDLOCK,HOLDLOCK) WHERE execution_id=@execution_id)
              INSERT ctl.runtime_cotacao_reference(execution_id,reference_release_id,reference_fingerprint,bound_at_utc)
              SELECT @execution_id,@reference_release_id,source_fingerprint,@now FROM ref.reference_release WHERE reference_release_id=@reference_release_id;
            COMMIT TRANSACTION;
            RETURN;
        END;
        DECLARE @contract_verified BIT=0,@stored_hash CHAR(64);
        SELECT @stored_hash=evidence_hash,@contract_verified=CASE WHEN
            evidence_hash=@evidence_hash AND audit_hash=@audit_hash AND identity_material=@identity
            AND DATALENGTH(identity_material)=DATALENGTH(@identity)
            AND contract_material=@contract_material AND DATALENGTH(contract_material)=DATALENGTH(@contract_material)
            AND policy_version=@policy_version AND policy_fingerprint=@policy_fingerprint
            AND audited_rows=@rows AND audited_pages=@pages AND @audit_valid=1 THEN 1 ELSE 0 END
        FROM ctl.runtime_contract_evidence WITH(HOLDLOCK) WHERE execution_id=@execution_id;
        DECLARE @candidates BIGINT=0,@quality NVARCHAR(16)=N'ABSENT',@evaluation_hash CHAR(64),@typed_valid BIT=0;
        SELECT @candidates=candidate_rows FROM ctl.execution_promotion_result WITH(HOLDLOCK) WHERE execution_id=@execution_id;
        IF EXISTS(SELECT 1 FROM ctl.coleta_promotion_result WITH(HOLDLOCK) WHERE execution_id=@execution_id
            AND @entity=N'coletas' AND validation_state=N'PASSED' AND typed_candidate_rows=@candidates)
            OR EXISTS(SELECT 1 FROM ctl.frete_promotion_result WITH(HOLDLOCK) WHERE execution_id=@execution_id
            AND @entity=N'fretes' AND validation_state=N'PASSED' AND typed_candidate_rows=@candidates)
            OR EXISTS(SELECT 1 FROM ctl.cotacao_promotion_result WITH(HOLDLOCK) WHERE execution_id=@execution_id
              AND @entity=N'cotacoes' AND validation_state=N'PASSED' AND typed_candidate_rows=@candidates)
            OR EXISTS(SELECT 1 FROM ctl.localizacao_carga_promotion_result WITH(HOLDLOCK) WHERE execution_id=@execution_id
              AND @entity=N'localizacao_cargas' AND validation_state=N'PASSED' AND typed_candidate_rows=@candidates)
            OR EXISTS(SELECT 1 FROM ctl.manifesto_promotion_result WITH(HOLDLOCK) WHERE execution_id=@execution_id
              AND @entity=N'manifestos' AND validation_state=N'PASSED' AND reduced_root_rows=@candidates)
            OR EXISTS(SELECT 1 FROM ctl.usuario_promotion_result WITH(HOLDLOCK) WHERE execution_id=@execution_id
              AND @entity=N'usuarios' AND validation_state=N'PASSED' AND typed_candidate_rows=@candidates
              AND generic_candidate_rows=@candidates AND conflicting_root_keys=0 AND generic_quarantine_rows=0)
            SET @typed_valid=1;
        SELECT @quality=evaluation_state,@evaluation_hash=evaluation_fingerprint
        FROM recon.execution_data_quality_evaluation WITH(HOLDLOCK) WHERE execution_id=@execution_id;
        IF EXISTS(SELECT 1 FROM recon.execution_data_quality_evaluation WITH(HOLDLOCK) WHERE execution_id=@execution_id
            AND (policy_version<>@policy_version OR policy_fingerprint<>@policy_fingerprint)) SET @quality=N'OBSOLETE';
        IF @execution_state<>N'PUBLISHED' AND (NOT EXISTS(
            SELECT 1 FROM ctl.data_quality_policy WITH(HOLDLOCK) WHERE policy_version=@policy_version
                AND policy_fingerprint=@policy_fingerprint AND policy_state=N'RATIFIED' AND effective_from_utc<=@now)
            OR EXISTS(SELECT 1 FROM ctl.data_quality_policy p WITH(HOLDLOCK)
                JOIN ctl.data_quality_policy newer WITH(HOLDLOCK) ON newer.scope_fingerprint=p.scope_fingerprint
                WHERE p.policy_version=@policy_version AND p.policy_fingerprint=@policy_fingerprint
                AND newer.policy_state=N'RATIFIED' AND newer.effective_from_utc>p.effective_from_utc
                AND newer.effective_from_utc<=@now)) SET @quality=N'OBSOLETE';
        DECLARE @dq_integrity BIT=1;
        DECLARE @scope CHAR(64)=LOWER(CONVERT(CHAR(64),HASHBYTES('SHA2_256',CONCAT(
            N'dq-scope-v1|',DATALENGTH(@environment_name),N':',@environment_name,
            N'|',DATALENGTH(@source_instance),N':',@source_instance,
            N'|',DATALENGTH(@tenant_scope),N':',@tenant_scope,
            N'|',DATALENGTH(@entity_name),N':',@entity_name,
            N'|',DATALENGTH(@mode),N':',@mode)),2));
        IF EXISTS(SELECT 1 FROM recon.execution_data_quality_evaluation e WITH(HOLDLOCK)
            WHERE e.execution_id=@execution_id AND (e.scope_fingerprint<>@scope OR e.evaluation_fingerprint<>
                LOWER(CONVERT(CHAR(64),HASHBYTES('SHA2_256',CONCAT(
                    N'dq-evaluation-v1|',e.policy_version,N'|',e.policy_fingerprint,N'|',e.scope_fingerprint,N'|',
                    CONVERT(NVARCHAR(36),e.execution_id),N'|',e.candidate_rows,N'|',
                    CONVERT(NVARCHAR(33),e.promotion_recorded_at_utc,126),N'|',
                    e.completed_checks,N'|',e.passed_checks,N'|',e.failed_checks,N'|',
                    e.evaluated_rows,N'|',e.failed_rows,N'|',e.evaluation_state,N'|',
                    (SELECT CONCAT(check_code,N':',evaluated_rows,N':',failed_rows,N':',check_state)
                     FROM recon.execution_data_quality_check_result WITH(HOLDLOCK)
                     WHERE execution_id=@execution_id AND check_ordinal=1),N'|',
                    (SELECT CONCAT(check_code,N':',evaluated_rows,N':',failed_rows,N':',check_state)
                     FROM recon.execution_data_quality_check_result WITH(HOLDLOCK)
                     WHERE execution_id=@execution_id AND check_ordinal=2),N'|',
                    (SELECT CONCAT(check_code,N':',evaluated_rows,N':',failed_rows,N':',check_state)
                     FROM recon.execution_data_quality_check_result WITH(HOLDLOCK)
                     WHERE execution_id=@execution_id AND check_ordinal=3),N'|',
                    (SELECT CONCAT(check_code,N':',evaluated_rows,N':',failed_rows,N':',check_state)
                     FROM recon.execution_data_quality_check_result WITH(HOLDLOCK)
                     WHERE execution_id=@execution_id AND check_ordinal=4))),2)))) SET @dq_integrity=0;
        IF EXISTS(SELECT 1 FROM recon.execution_data_quality_evaluation e WITH(HOLDLOCK)
            WHERE e.execution_id=@execution_id AND (e.expected_checks<>4 OR e.completed_checks<>4
                OR (SELECT COUNT_BIG(*) FROM recon.execution_data_quality_check_result r WITH(HOLDLOCK)
                    JOIN ctl.data_quality_check_policy p WITH(HOLDLOCK) ON p.policy_version=e.policy_version
                        AND p.policy_fingerprint=e.policy_fingerprint AND p.check_ordinal=r.check_ordinal
                        AND p.check_code=r.check_code WHERE r.execution_id=e.execution_id)<>4)) SET @dq_integrity=0;
        -- Users seals only after the same occurrence's typed candidate and complete DQ pass.
        IF @operation=N'SEAL' AND @entity=N'usuarios'
        BEGIN
            IF @identity_valid=0 OR @execution_state<>N'PROMOTED' OR @lease_valid=0 OR @audit_valid=0
                OR @typed_valid=0 OR @quality<>N'PASSED' OR @dq_integrity=0
                OR @rows<>(SELECT COUNT_BIG(*) FROM stg.execution_record WITH(HOLDLOCK) WHERE execution_id=@execution_id)
                OR @rows<>(SELECT COUNT_BIG(*) FROM stg.usuario_record WITH(HOLDLOCK) WHERE execution_id=@execution_id)
                OR NOT EXISTS(SELECT 1 FROM recon.execution_data_quality_evaluation e WITH(HOLDLOCK)
                    JOIN ctl.execution_promotion_result p WITH(HOLDLOCK) ON p.execution_id=e.execution_id
                    WHERE e.execution_id=@execution_id AND e.expected_checks=4 AND e.completed_checks=4
                        AND e.passed_checks=4 AND e.failed_checks=0 AND e.failed_rows=0
                        AND e.candidate_rows=@candidates AND e.promotion_recorded_at_utc=p.promoted_at_utc
                        AND e.evaluated_at_utc<=@now)
                THROW 52960,N'USERS_RECOVERY_SEAL_GATE_REJECTED',1;
            IF EXISTS(SELECT 1 FROM ctl.runtime_contract_evidence WITH(UPDLOCK,HOLDLOCK)
                WHERE execution_id=@execution_id AND evidence_hash<>@evidence_hash)
                THROW 52304,N'RUNTIME_RECOVERY_EVIDENCE_CONFLICT',1;
            IF NOT EXISTS(SELECT 1 FROM ctl.runtime_contract_evidence WITH(UPDLOCK,HOLDLOCK) WHERE execution_id=@execution_id)
                INSERT ctl.runtime_contract_evidence VALUES(@execution_id,@identity,@contract_material,
                    @policy_version,@policy_fingerprint,@rows,@pages,@audit_hash,@evidence_hash,@now);
            COMMIT TRANSACTION;
            RETURN;
        END;
        DECLARE @users_receipt_valid BIT=0;
        IF @entity=N'usuarios' AND EXISTS(
            SELECT 1 FROM recon.usuario_reconciliation_result u WITH(HOLDLOCK)
            JOIN recon.usuario_apply_authorization a WITH(HOLDLOCK) ON a.execution_id=u.execution_id
            JOIN recon.execution_reconciliation_result g WITH(HOLDLOCK) ON g.execution_id=u.execution_id
            WHERE u.execution_id=@execution_id AND a.authorization_state=N'APPLIED'
                AND a.candidate_rows=@candidates AND u.candidate_rows=@candidates
                AND u.candidate_rows=g.candidate_rows AND a.authorized_at_utc<=u.published_at_utc AND a.applied_at_utc>=a.authorized_at_utc AND a.applied_at_utc>=u.published_at_utc AND a.applied_at_utc<=@now
                AND u.reconciled_at_utc=g.reconciled_at_utc AND u.published_at_utc=g.published_at_utc
                AND u.candidate_rows=(SELECT COUNT_BIG(*) FROM recon.usuario_candidate_application WITH(HOLDLOCK)
                    WHERE execution_id=@execution_id AND result_active=1)
                AND u.inserted_rows=(SELECT COUNT_BIG(*) FROM recon.usuario_candidate_application WITH(HOLDLOCK)
                    WHERE execution_id=@execution_id AND application_disposition=N'INSERTED')
                AND u.updated_rows=(SELECT COUNT_BIG(*) FROM recon.usuario_candidate_application WITH(HOLDLOCK)
                    WHERE execution_id=@execution_id AND application_disposition=N'UPDATED')
                AND u.reactivated_rows=(SELECT COUNT_BIG(*) FROM recon.usuario_candidate_application WITH(HOLDLOCK)
                    WHERE execution_id=@execution_id AND application_disposition=N'REACTIVATED')
                AND u.noop_rows=(SELECT COUNT_BIG(*) FROM recon.usuario_candidate_application WITH(HOLDLOCK)
                    WHERE execution_id=@execution_id AND application_disposition IN(N'NO_OP',N'STALE_NO_OP'))
                AND u.stale_noop_rows=(SELECT COUNT_BIG(*) FROM recon.usuario_candidate_application WITH(HOLDLOCK)
                    WHERE execution_id=@execution_id AND application_disposition=N'STALE_NO_OP')
                AND u.history_rows=(SELECT COUNT_BIG(*) FROM core.usuario_history WITH(HOLDLOCK) WHERE execution_id=@execution_id)
                AND NOT EXISTS(SELECT 1 FROM recon.usuario_candidate_application c WITH(HOLDLOCK)
                    WHERE c.execution_id=@execution_id AND c.applied_at_utc<>a.applied_at_utc)
                AND NOT EXISTS(SELECT 1 FROM core.usuario_history h WITH(HOLDLOCK)
                    WHERE h.execution_id=@execution_id AND h.changed_at_utc<>a.applied_at_utc)
                AND NOT EXISTS(SELECT 1 FROM recon.usuario_candidate_application c WITH(HOLDLOCK)
                    LEFT JOIN core.usuario_history h WITH(HOLDLOCK)
                        ON h.execution_id=c.execution_id AND h.usuario_id=c.usuario_id
                    WHERE c.execution_id=@execution_id AND c.application_disposition IN(N'INSERTED',N'UPDATED',N'REACTIVATED')
                        AND (h.usuario_id IS NULL OR h.change_kind<>c.application_disposition
                            OR h.state_hash<>c.result_state_hash OR h.attribute_hash<>c.result_attribute_hash
                            OR h.observation_order_at_utc<>c.observation_order_at_utc
                            OR h.observation_order_execution_id<>c.observation_order_execution_id)))
            SET @users_receipt_valid=1;
        DECLARE @reason NVARCHAR(40)=N'INCONSISTENT';
        IF @attempt_exists=0 SET @reason=N'NOT_FOUND';
        ELSE IF @execution_state IS NULL SET @reason=N'INCONSISTENT';
        ELSE IF @identity_valid=0 OR @dq_integrity=0 SET @reason=N'INCONSISTENT';
        ELSE IF @execution_state=N'PUBLISHED' AND @entity=N'coletas' AND NOT EXISTS(
            SELECT 1 FROM ctl.runtime_coleta_publication_receipt WITH(HOLDLOCK) WHERE execution_id=@execution_id)
            SET @reason=N'EVIDENCE_MISSING';
        ELSE IF @execution_state=N'PUBLISHED'
        BEGIN
            IF @typed_valid=1 AND @quality=N'PASSED' AND (@stored_hash IS NULL OR @contract_verified=1)
            AND (@entity<>N'usuarios' OR @contract_verified=1 AND @users_receipt_valid=1)
            AND (@entity<>N'coletas' OR EXISTS(SELECT 1 FROM ctl.runtime_coleta_publication_receipt r WITH(HOLDLOCK)
                JOIN recon.execution_reconciliation_result g WITH(HOLDLOCK) ON g.execution_id=r.execution_id
                JOIN ctl.execution_publication_event p WITH(HOLDLOCK) ON p.execution_id=r.execution_id
                WHERE r.execution_id=@execution_id AND r.receipt_hash=ctl.fn_runtime_coleta_publication_hash(@execution_id)
                    AND r.candidate_rows=g.candidate_rows AND r.reconciled_at_utc=g.reconciled_at_utc
                    AND r.published_at_utc=g.published_at_utc
                    AND (r.incremental_frontier_before_utc=p.incremental_frontier_before_utc
                        OR r.incremental_frontier_before_utc IS NULL AND p.incremental_frontier_before_utc IS NULL)
                    AND (r.incremental_frontier_after_utc=p.incremental_frontier_after_utc
                        OR r.incremental_frontier_after_utc IS NULL AND p.incremental_frontier_after_utc IS NULL))) AND EXISTS(
                SELECT 1 FROM recon.execution_reconciliation_result result WITH(HOLDLOCK)
                JOIN ctl.execution_publication_event publication WITH(HOLDLOCK) ON publication.execution_id=result.execution_id
                JOIN ctl.execution_promotion_result candidate_set WITH(HOLDLOCK) ON candidate_set.execution_id=result.execution_id
                WHERE result.execution_id=@execution_id AND publication.partition_id=@partition_id
                AND result.candidate_rows=candidate_set.candidate_rows AND candidate_set.quarantined_stage_rows=0
                AND result.published_at_utc=publication.published_at_utc
                AND result.reconciled_at_utc<=result.published_at_utc AND @released IS NOT NULL
                AND ((@mode=N'INCREMENTAL' AND publication.incremental_frontier_before_utc IS NOT NULL
                    AND publication.incremental_frontier_after_utc>=publication.incremental_frontier_before_utc)
                    OR (@mode<>N'INCREMENTAL' AND publication.incremental_frontier_before_utc IS NULL
                    AND publication.incremental_frontier_after_utc IS NULL))
                AND result.candidate_rows=(SELECT COUNT_BIG(*) FROM recon.execution_candidate_application WITH(HOLDLOCK) WHERE execution_id=@execution_id AND 1=1)
                AND result.inserted_rows=(SELECT COUNT_BIG(*) FROM recon.execution_candidate_application WITH(HOLDLOCK) WHERE execution_id=@execution_id AND application_disposition=N'INSERTED')
                AND result.updated_rows=(SELECT COUNT_BIG(*) FROM recon.execution_candidate_application WITH(HOLDLOCK) WHERE execution_id=@execution_id AND application_disposition=N'UPDATED')
                AND result.reactivated_rows=(SELECT COUNT_BIG(*) FROM recon.execution_candidate_application WITH(HOLDLOCK) WHERE execution_id=@execution_id AND application_disposition=N'REACTIVATED')
                AND result.noop_rows=(SELECT COUNT_BIG(*) FROM recon.execution_candidate_application WITH(HOLDLOCK) WHERE execution_id=@execution_id AND application_disposition IN(N'NO_OP',N'STALE_NO_OP'))
                AND result.stale_noop_rows=(SELECT COUNT_BIG(*) FROM recon.execution_candidate_application WITH(HOLDLOCK) WHERE execution_id=@execution_id AND application_disposition=N'STALE_NO_OP')
                AND EXISTS(SELECT 1 FROM ctl.execution_state_event WITH(HOLDLOCK) WHERE execution_id=@execution_id
                    AND transition_sequence=@next_transition_sequence-2 AND previous_state=N'PROMOTED'
                    AND next_state=N'RECONCILED' AND reason_code=N'CANDIDATE_SET_RECONCILED')
                AND EXISTS(SELECT 1 FROM ctl.execution_state_event WITH(HOLDLOCK) WHERE execution_id=@execution_id
                    AND transition_sequence=@next_transition_sequence-1 AND previous_state=N'RECONCILED'
                    AND next_state=N'PUBLISHED' AND reason_code=N'RECONCILIATION_PUBLISHED')
                AND EXISTS(SELECT 1 FROM recon.execution_data_quality_evaluation e WITH(HOLDLOCK)
                    WHERE e.execution_id=@execution_id AND e.expected_checks=4 AND e.completed_checks=4
                    AND e.passed_checks=4 AND e.failed_checks=0 AND e.evaluated_at_utc<=publication.published_at_utc
                    AND e.candidate_rows=result.candidate_rows AND e.promotion_recorded_at_utc=candidate_set.promoted_at_utc
                    AND (SELECT COUNT_BIG(*) FROM recon.execution_data_quality_check_result r WITH(HOLDLOCK)
                        WHERE r.execution_id=@execution_id AND r.check_state=N'PASSED')=4)
            ) SET @reason=N'PUBLISHED';
        END
        ELSE IF EXISTS(SELECT 1 FROM ctl.execution_publication_event WITH(HOLDLOCK) WHERE execution_id=@execution_id)
            OR EXISTS(SELECT 1 FROM recon.execution_reconciliation_result WITH(HOLDLOCK) WHERE execution_id=@execution_id)
            SET @reason=N'INCONSISTENT';
        ELSE IF @execution_state IN(N'FAILED',N'CANCELLED',N'BLOCKED',N'SKIPPED',N'NOT_APPLICABLE',N'DEGRADED') SET @reason=N'TERMINAL';
        ELSE IF @stored_hash IS NOT NULL AND @contract_verified=0 SET @reason=N'INCONSISTENT';
        ELSE IF @lease_valid=0 SET @reason=N'LEASE_LOST';
        ELSE IF @contract_verified=0 SET @reason=CASE WHEN @execution_state=N'EXTRACTING' AND @rows=0
            THEN N'IN_PROGRESS' WHEN @execution_state=N'EXTRACTING' THEN N'PARTIAL_EXTRACTION' ELSE N'EVIDENCE_MISSING' END;
        ELSE IF @quality=N'FAILED' SET @reason=N'DQ_FAILED';
        ELSE IF @quality=N'OBSOLETE' SET @reason=N'DQ_OBSOLETE';
        ELSE IF @execution_state=N'PROMOTED' AND @typed_valid=0 SET @reason=N'INCONSISTENT';
        ELSE IF @execution_state IN(N'EXTRACTING',N'EXTRACTED',N'STAGED',N'PROMOTED') SET @reason=N'ELIGIBLE';
        DECLARE @revision CHAR(64)=LOWER(CONVERT(CHAR(64),HASHBYTES('SHA2_256',CONCAT(
            @identity,N'|',@execution_state,N'|',@next_transition_sequence,N'|',@lease_id,N'|',
            CONVERT(NVARCHAR(23),@expires,126),N'|',@stored_hash,N'|',@evaluation_hash,N'|',@candidates)),2));
        IF @operation=N'RESUME'
        BEGIN
            IF @expected_revision IS NULL OR @expected_revision COLLATE Latin1_General_100_BIN2<>@revision
                OR DATALENGTH(@expected_revision)<>128 THROW 52305,N'RUNTIME_RECOVERY_STATE_CHANGED',1;
            IF @reason<>N'ELIGIBLE' THROW 52306,N'RUNTIME_RECOVERY_CONTINUATION_REJECTED',1;
            IF @execution_state=N'EXTRACTING'
            BEGIN
                EXEC ctl.usp_control_plane_transition_execution @execution_id,N'EXTRACTING',N'EXTRACTED',N'TRAVERSAL_AUDITED',@now;
                SET @execution_state=N'EXTRACTED';
            END;
            IF @execution_state=N'EXTRACTED'
            BEGIN
                EXEC ctl.usp_control_plane_transition_execution @execution_id,N'EXTRACTED',N'STAGED',N'STAGING_COMPLETED',@now;
                SET @execution_state=N'STAGED';
            END;
            IF @execution_state=N'STAGED'
            BEGIN
                IF @entity=N'fretes' EXEC core.usp_prepare_frete_candidate_set @execution_id,@contract_version,
                    @contract_fingerprint,@configuration_version,@configuration_fingerprint;
                ELSE IF @entity=N'manifestos' EXEC core.usp_prepare_manifesto_candidate_set @execution_id,@contract_version,
                    @contract_fingerprint,@configuration_version,@configuration_fingerprint;
                ELSE EXEC core.usp_prepare_staged_execution @execution_id,@contract_version,
                    @contract_fingerprint,@configuration_version,@configuration_fingerprint;
            END;
            -- Entry points existentes revalidam candidates, checks, policy/SLA, lease e publicação.
            -- Sem INSERT EXEC aninhado. O adapter drena resultados antes da leitura de confirmação.
            IF @entity<>N'usuarios' OR @quality<>N'PASSED'
                EXEC recon.usp_evaluate_execution_data_quality @execution_id,@policy_version,@policy_fingerprint;
            IF @entity=N'coletas' EXEC core.usp_apply_reconcile_publish_coletas @execution_id,@contract_version,
                @contract_fingerprint,@configuration_version,@configuration_fingerprint;
            ELSE IF @entity=N'manifestos' EXEC core.usp_apply_reconcile_publish_manifestos @execution_id,@contract_version,
                @contract_fingerprint,@configuration_version,@configuration_fingerprint;
            ELSE IF @entity=N'cotacoes' EXEC core.usp_apply_reconcile_publish_cotacoes @execution_id,@contract_version,
                @contract_fingerprint,@configuration_version,@configuration_fingerprint,@reference_release_id;
            ELSE IF @entity=N'localizacao_cargas' EXEC core.usp_apply_reconcile_publish_localizacao_cargas @execution_id,@contract_version,
                @contract_fingerprint,@configuration_version,@configuration_fingerprint;
            ELSE IF @entity=N'usuarios' EXEC core.usp_apply_reconcile_publish_usuarios @execution_id,@contract_version,
                @contract_fingerprint,@configuration_version,@configuration_fingerprint;
            ELSE EXEC core.usp_apply_reconcile_publish_fretes @execution_id,@contract_version,
                @contract_fingerprint,@configuration_version,@configuration_fingerprint;
            COMMIT TRANSACTION;
            RETURN;
        END;
        SELECT @reason AS reason,@execution_state AS execution_state,@lease_valid AS lease_valid,
            @contract_verified AS contract_verified,@candidates AS candidate_rows,@quality AS quality,@revision AS revision,
            @execution_id AS execution_id,COALESCE(users_receipt.inserted_rows,typed.inserted_rows,result.inserted_rows) AS inserted_rows,
            COALESCE(users_receipt.updated_rows,typed.updated_rows,result.updated_rows) AS updated_rows,
            COALESCE(users_receipt.reactivated_rows,typed.reactivated_rows,result.reactivated_rows) AS reactivated_rows,
            COALESCE(users_receipt.noop_rows,typed.noop_rows,result.noop_rows) AS noop_rows,
            COALESCE(users_receipt.stale_noop_rows,typed.stale_noop_rows,result.stale_noop_rows) AS stale_noop_rows,result.reconciled_at_utc,result.published_at_utc,
            publication.incremental_frontier_before_utc,publication.incremental_frontier_after_utc
        FROM (VALUES(1)) anchor(value)
        LEFT JOIN recon.execution_reconciliation_result result ON result.execution_id=@execution_id AND @reason=N'PUBLISHED'
        LEFT JOIN ctl.execution_publication_event publication ON publication.execution_id=result.execution_id
        LEFT JOIN ctl.runtime_coleta_publication_receipt typed ON typed.execution_id=result.execution_id AND @entity=N'coletas'
        LEFT JOIN recon.usuario_reconciliation_result users_receipt ON users_receipt.execution_id=result.execution_id AND @entity=N'usuarios';
        COMMIT TRANSACTION;
    END TRY
    BEGIN CATCH
        IF XACT_STATE()<>0 ROLLBACK TRANSACTION;
        THROW;
    END CATCH;
END;
GO
