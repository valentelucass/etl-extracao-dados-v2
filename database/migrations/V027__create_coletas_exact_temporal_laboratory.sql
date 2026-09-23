-- ADR0047 / COL-TIME-11..14. Exact comparison and rollback-only synthetic consumer.
SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;
SET XACT_ABORT ON;
GO
CREATE TABLE stg.coleta_exact_time (
    stage_record_id BIGINT NOT NULL CONSTRAINT PK_coleta_exact_time PRIMARY KEY
        REFERENCES stg.coleta_record(stage_record_id),
    representation_version NVARCHAR(64) NOT NULL CHECK(representation_version=N'coletas-exact-time-v1'),
    epoch_second BIGINT NULL,
    nano INT NULL,
    native_presence NVARCHAR(8) NOT NULL CHECK(native_presence IN(N'ABSENT',N'NULL',N'VALUE')),
    native_raw_json NVARCHAR(MAX) NULL,
    native_parse_state NVARCHAR(8) NOT NULL CHECK(native_parse_state IN(N'ABSENT',N'NULL',N'INVALID',N'VALID')),
    fallback_reason NVARCHAR(32) NOT NULL CHECK(fallback_reason IN(N'NONE',N'NATIVE_ABSENT',N'NATIVE_NULL',N'NATIVE_INVALID')),
    CHECK((epoch_second IS NULL AND nano IS NULL) OR (epoch_second IS NOT NULL AND nano BETWEEN 0 AND 999999999)),
    CHECK((native_presence=N'VALUE' AND native_raw_json IS NOT NULL AND native_parse_state IN(N'INVALID',N'VALID'))
        OR (native_presence IN(N'ABSENT',N'NULL') AND native_raw_json IS NULL AND native_parse_state=native_presence))
);
GO
CREATE PROCEDURE stg.usp_stage_coleta_exact_time
    @execution_id UNIQUEIDENTIFIER,@batch INT,@ordinal INT,@epoch_second BIGINT,@nano INT,
    @native_presence NVARCHAR(8),@native_raw_json NVARCHAR(MAX),@native_parse_state NVARCHAR(8),@fallback_reason NVARCHAR(32)
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    IF @@TRANCOUNT=0 THROW 53100,N'COL_EXACT_SHARED_TRANSACTION_REQUIRED',1;
    DECLARE @stage BIGINT,@origin NVARCHAR(32),@presence NVARCHAR(8),@state NVARCHAR(32);
    SELECT @state=current_state FROM ctl.execution_attempt WITH(UPDLOCK,HOLDLOCK) WHERE execution_id=@execution_id;
    SELECT @stage=d.stage_record_id,@origin=d.freshness_origin,
        @presence=JSON_VALUE(d.field_presence_json,'$.status_updated_at')
    FROM stg.coleta_record d JOIN stg.execution_record g ON g.stage_record_id=d.stage_record_id
    WHERE d.execution_id=@execution_id AND g.input_batch_number=@batch AND g.input_record_ordinal=@ordinal;
    IF @stage IS NULL OR @state<>N'EXTRACTING' THROW 53101,N'COL_EXACT_STAGE_UNAVAILABLE',1;
    IF @native_presence IS NULL OR @native_parse_state IS NULL OR @fallback_reason IS NULL
        OR @native_presence<>@presence
        OR (@origin=N'UNAVAILABLE' AND (@epoch_second IS NOT NULL OR @nano IS NOT NULL))
        OR (@origin<>N'UNAVAILABLE' AND (@epoch_second IS NULL OR @nano IS NULL))
        OR (@origin=N'STATUS_UPDATED_AT' AND (@native_parse_state<>N'VALID' OR @fallback_reason<>N'NONE'))
        OR (@origin<>N'STATUS_UPDATED_AT' AND (@native_parse_state=N'VALID' OR @fallback_reason<>CONCAT(N'NATIVE_',@native_parse_state)))
        THROW 53102,N'COL_EXACT_REPRESENTATION_MISMATCH',1;
    IF EXISTS(SELECT 1 FROM stg.coleta_exact_time WHERE stage_record_id=@stage)
    BEGIN
        IF EXISTS(SELECT @epoch_second,@nano,@native_presence,@native_raw_json,@native_parse_state,@fallback_reason
            EXCEPT SELECT epoch_second,nano,native_presence,native_raw_json,native_parse_state,fallback_reason
            FROM stg.coleta_exact_time WHERE stage_record_id=@stage)
            THROW 53103,N'COL_EXACT_RETRY_CONFLICT',1;
        RETURN;
    END;
    INSERT stg.coleta_exact_time VALUES(@stage,N'coletas-exact-time-v1',@epoch_second,@nano,
        @native_presence,@native_raw_json,@native_parse_state,@fallback_reason);
END;
GO
-- The historical observational view remains unchanged. V2 explicitly revises comparison semantics.
CREATE VIEW recon.vw_coleta_temporal_decision_v2
AS
WITH reference_variants AS (
    SELECT execution_id,source_key,status_code,request_date_text,epoch_second,nano,
        CASE WHEN epoch_second IS NULL THEN CONVERT(NVARCHAR(255),JSON_VALUE(observation_json,'$.statusUpdatedAtRawJson')) END invalid_raw
    FROM stg.coleta_temporal_observation
    GROUP BY execution_id,source_key,status_code,request_date_text,epoch_second,nano,
        CASE WHEN epoch_second IS NULL THEN CONVERT(NVARCHAR(255),JSON_VALUE(observation_json,'$.statusUpdatedAtRawJson')) END
), reference_groups AS (
    SELECT execution_id,source_key,COUNT_BIG(*) variants FROM reference_variants GROUP BY execution_id,source_key
), data_variants AS (
    SELECT d.execution_id,d.source_key,d.sequence_code_presence,d.sequence_code_json,d.status_code,d.terminal,
        d.freshness_origin,e.epoch_second,e.nano
    FROM stg.coleta_record d LEFT JOIN stg.coleta_exact_time e ON e.stage_record_id=d.stage_record_id
    GROUP BY d.execution_id,d.source_key,d.sequence_code_presence,d.sequence_code_json,d.status_code,d.terminal,
        d.freshness_origin,e.epoch_second,e.nano
), data_groups AS (
    SELECT execution_id,source_key,COUNT_BIG(*) variants FROM data_variants GROUP BY execution_id,source_key
), linked AS (
    SELECT d.execution_id data_export_execution_id,r.execution_id reference_execution_id,d.stage_record_id,
        d.source_key,b.reference_key,r.source_instance,r.tenant_scope,r.query_date,
        b.evidence_version,b.evidence_sha256,e.native_presence,e.native_parse_state,e.fallback_reason,
        CASE WHEN e.native_parse_state=N'VALID' THEN e.epoch_second ELSE v.epoch_second END epoch_second,
        CASE WHEN e.native_parse_state=N'VALID' THEN e.nano ELSE v.nano END nano,
        CASE WHEN a.current_state<>N'STAGED' THEN N'DATA_EXPORT_INCOMPLETE'
            WHEN r.capture_state<>N'TERMINAL_LOCAL' THEN N'CAPTURE_INCOMPLETE'
            WHEN generic.validation_disposition<>N'VALID' THEN N'DATA_EXPORT_QUARANTINED'
            WHEN e.stage_record_id IS NULL THEN N'EXACT_REPRESENTATION_ABSENT'
            WHEN dg.variants<>1 THEN N'DATA_EXPORT_ROOT_CONFLICT'
            WHEN b.reference_key IS NULL THEN N'BINDING_ABSENT'
            WHEN JSON_VALUE(d.payload_json,'$.request_date') IS NULL
                OR JSON_VALUE(d.payload_json,'$.request_date') COLLATE Latin1_General_100_BIN2<>CONVERT(NVARCHAR(10),r.query_date,23)
                THEN N'WINDOW_MISMATCH'
            WHEN g.variants IS NULL AND e.native_parse_state=N'VALID' THEN N'DATA_EXPORT_TIME_RETAINED'
            WHEN g.variants IS NULL THEN N'REFERENCE_ABSENT'
            WHEN g.variants<>1 THEN N'REFERENCE_CONFLICT'
            WHEN v.request_date_text IS NULL OR v.request_date_text<>CONVERT(NVARCHAR(10),r.query_date,23) THEN N'WINDOW_MISMATCH'
            WHEN v.status_code IS NULL OR d.status_code IS NULL OR v.status_code<>d.status_code THEN N'STATUS_MISMATCH'
            WHEN v.epoch_second IS NULL THEN N'REFERENCE_INVALID'
            WHEN e.native_parse_state=N'VALID' AND (e.epoch_second<>v.epoch_second OR e.nano<>v.nano) THEN N'SOURCE_TIME_CONFLICT'
            WHEN e.native_parse_state=N'VALID' THEN N'DATA_EXPORT_TIME_RETAINED'
            ELSE N'COMPLEMENTED' END outcome
    FROM stg.coleta_record d
    JOIN data_groups dg ON dg.execution_id=d.execution_id AND dg.source_key=d.source_key
    JOIN stg.execution_record generic ON generic.stage_record_id=d.stage_record_id
    JOIN ctl.execution_attempt a ON a.execution_id=d.execution_id
    JOIN ctl.execution_partition p ON p.partition_id=a.partition_id
    JOIN ctl.execution_source_protocol protocol ON protocol.execution_id=a.execution_id AND protocol.source_kind=N'DATA_EXPORT'
    JOIN stg.coleta_temporal_run r ON r.source_instance=p.source_instance AND r.tenant_scope=p.tenant_scope
    LEFT JOIN stg.coleta_exact_time e ON e.stage_record_id=d.stage_record_id
    LEFT JOIN stg.coleta_temporal_binding b ON b.data_export_execution_id=d.execution_id
        AND b.reference_execution_id=r.execution_id AND b.data_export_key=d.source_key
    LEFT JOIN reference_groups g ON g.execution_id=r.execution_id AND g.source_key=b.reference_key
    LEFT JOIN reference_variants v ON v.execution_id=g.execution_id AND v.source_key=g.source_key AND g.variants=1
    WHERE p.entity_name=N'coletas' AND p.environment_name=N'LOCAL_SHADOW'
)
SELECT data_export_execution_id,reference_execution_id,stage_record_id,source_key,reference_key,
    source_instance,tenant_scope,query_date,evidence_version,evidence_sha256,native_presence,native_parse_state,fallback_reason,outcome,
    CASE WHEN outcome IN(N'COMPLEMENTED',N'DATA_EXPORT_TIME_RETAINED') THEN epoch_second END candidate_epoch_second,
    CASE WHEN outcome IN(N'COMPLEMENTED',N'DATA_EXPORT_TIME_RETAINED') THEN nano END candidate_nano,
    CONVERT(BIT,0) promotion_authorized
FROM linked;
GO
CREATE PROCEDURE recon.usp_qualify_coleta_temporal_v2
    @data_export_execution_id UNIQUEIDENTIFIER,@reference_execution_id UNIQUEIDENTIFIER
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    BEGIN TRANSACTION;
    IF NOT EXISTS(SELECT 1 FROM ctl.execution_attempt a WITH(UPDLOCK,HOLDLOCK)
        JOIN ctl.execution_partition p ON p.partition_id=a.partition_id
        WHERE a.execution_id=@data_export_execution_id AND a.current_state=N'STAGED'
            AND p.entity_name=N'coletas' AND p.environment_name=N'LOCAL_SHADOW')
        THROW 53006,N'COL_TEMPORAL_DATA_EXPORT_SCOPE_MISMATCH',1;
    EXEC stg.usp_lock_coleta_temporal @reference_execution_id;
    IF NOT EXISTS(SELECT 1 FROM stg.coleta_temporal_run r WITH(UPDLOCK,HOLDLOCK)
        JOIN ctl.execution_partition p ON p.source_instance=r.source_instance AND p.tenant_scope=r.tenant_scope
        JOIN ctl.execution_attempt a ON a.partition_id=p.partition_id
        WHERE r.execution_id=@reference_execution_id AND a.execution_id=@data_export_execution_id AND r.capture_state=N'TERMINAL_LOCAL')
        THROW 53004,N'COL_TEMPORAL_CAPTURE_INCOMPLETE',1;
    SELECT COUNT_BIG(*) considered_rows,
        COALESCE(SUM(CONVERT(BIGINT,CASE WHEN c.candidate_epoch_second IS NOT NULL THEN 1 ELSE 0 END)),0) candidate_rows,
        COALESCE(SUM(CONVERT(BIGINT,CASE WHEN c.candidate_epoch_second IS NULL THEN 1 ELSE 0 END)),0) blocked_rows
    FROM stg.execution_record d WITH(HOLDLOCK)
    LEFT JOIN recon.vw_coleta_temporal_decision_v2 c ON c.stage_record_id=d.stage_record_id
        AND c.data_export_execution_id=@data_export_execution_id AND c.reference_execution_id=@reference_execution_id
    WHERE d.execution_id=@data_export_execution_id;
    COMMIT TRANSACTION;
END;
GO
CREATE TABLE core.coleta_temporal_laboratory (
    source_instance NVARCHAR(128) COLLATE Latin1_General_100_BIN2 NOT NULL CHECK(source_instance=N'SYNTHETIC_COLETAS_TEMPORAL_LAB'),
    tenant_scope NVARCHAR(128) COLLATE Latin1_General_100_BIN2 NOT NULL CHECK(tenant_scope=N'SYNTHETIC_COLETAS_TENANT'),
    source_key NVARCHAR(256) COLLATE Latin1_General_100_BIN2 NOT NULL,
    sequence_code_presence NVARCHAR(8) NOT NULL,
    sequence_code_json NVARCHAR(256) COLLATE Latin1_General_100_BIN2 NULL,
    status_code NVARCHAR(32) COLLATE Latin1_General_100_BIN2 NOT NULL,
    status_label NVARCHAR(64) NOT NULL,
    terminal BIT NOT NULL,
    epoch_second BIGINT NOT NULL,
    nano INT NOT NULL CHECK(nano BETWEEN 0 AND 999999999),
    instant_origin NVARCHAR(32) NOT NULL CHECK(instant_origin IN(N'COMPLEMENTED',N'DATA_EXPORT_TIME_RETAINED')),
    execution_id UNIQUEIDENTIFIER NOT NULL REFERENCES ctl.execution_attempt(execution_id),
    reference_execution_id UNIQUEIDENTIFIER NOT NULL REFERENCES stg.coleta_temporal_run(execution_id),
    evidence_version NVARCHAR(128) NOT NULL CHECK(evidence_version=N'synthetic-coletas-temporal-v1'),
    evidence_sha256 CHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL,
    active BIT NOT NULL CHECK(active=1),
    CONSTRAINT PK_coleta_temporal_laboratory PRIMARY KEY NONCLUSTERED(source_instance,tenant_scope,source_key)
);
CREATE TABLE recon.coleta_temporal_laboratory_application (
    execution_id UNIQUEIDENTIFIER NOT NULL REFERENCES ctl.execution_attempt(execution_id),
    reference_execution_id UNIQUEIDENTIFIER NOT NULL REFERENCES stg.coleta_temporal_run(execution_id),
    source_key NVARCHAR(256) COLLATE Latin1_General_100_BIN2 NOT NULL,
    disposition NVARCHAR(24) NOT NULL CHECK(disposition IN(N'INSERTED',N'UPDATED',N'NO_OP',N'STALE_NO_OP',N'TERMINAL_NO_OP')),
    candidate_epoch_second BIGINT NOT NULL,
    candidate_nano INT NOT NULL,
    CONSTRAINT PK_coleta_temporal_lab_application PRIMARY KEY(execution_id,source_key)
);
GO
CREATE PROCEDURE core.usp_consume_coleta_temporal_laboratory
    @data_export_execution_id UNIQUEIDENTIFIER,@reference_execution_id UNIQUEIDENTIFIER,@synthetic_enabled BIT
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    -- Caller owns the outer transaction. This procedure cannot publish, authorize GraphQL or commit it.
    IF @synthetic_enabled IS NULL OR @synthetic_enabled<>1 OR @@TRANCOUNT=0
        OR DB_NAME()<>N'ETL_SISTEMA_V2_SHADOW'
        THROW 53110,N'COL_LAB_EXPLICIT_TRANSACTION_REQUIRED',1;
    DECLARE @locked INT;
    EXEC @locked=sys.sp_getapplock @Resource=N'coletas-temporal-synthetic-consumer',
        @LockMode=N'Exclusive',@LockOwner=N'Transaction',@LockTimeout=1500;
    IF @locked<0 THROW 53111,N'COL_LAB_CONCURRENT_CONSUMER',1;
    IF NOT EXISTS(SELECT 1 FROM ctl.execution_attempt a WITH(UPDLOCK,HOLDLOCK)
        JOIN ctl.execution_partition p ON p.partition_id=a.partition_id
        JOIN ctl.execution_audit audit ON audit.execution_id=a.execution_id AND audit.status=N'COMPLETED'
        WHERE a.execution_id=@data_export_execution_id AND a.current_state=N'STAGED'
            AND p.environment_name=N'LOCAL_SHADOW' AND p.entity_name=N'coletas'
            AND p.source_instance=N'SYNTHETIC_COLETAS_TEMPORAL_LAB' AND p.tenant_scope=N'SYNTHETIC_COLETAS_TENANT')
        THROW 53112,N'COL_LAB_SYNTHETIC_AUDITED_SCOPE_REQUIRED',1;
    DECLARE @qualification TABLE(considered BIGINT,candidates BIGINT,blocked BIGINT);
    INSERT @qualification EXEC recon.usp_qualify_coleta_temporal_v2 @data_export_execution_id,@reference_execution_id;
    IF NOT EXISTS(SELECT 1 FROM @qualification WHERE considered>0 AND considered=candidates AND blocked=0)
        THROW 53113,N'COL_LAB_DECISION_BLOCKED',1;
    IF EXISTS(SELECT 1 FROM recon.vw_coleta_temporal_decision_v2 c JOIN stg.coleta_record d ON d.stage_record_id=c.stage_record_id
        WHERE c.data_export_execution_id=@data_export_execution_id AND c.reference_execution_id=@reference_execution_id
            AND (c.evidence_version<>N'synthetic-coletas-temporal-v1' OR c.evidence_version IS NULL
                OR ISNULL(JSON_VALUE(d.payload_json,'$.synthetic_fixture'),N'false')<>N'true'))
        THROW 53112,N'COL_LAB_SYNTHETIC_FIXTURE_REQUIRED',1;
    -- DISTINCT collapses equivalent root projections only. No hash/ordinal chooses a winner.
    SELECT DISTINCT c.source_instance,c.tenant_scope,c.source_key,d.sequence_code_presence,d.sequence_code_json,
        d.status_code,d.status_label,d.terminal,c.candidate_epoch_second epoch_second,c.candidate_nano nano,
        c.outcome instant_origin,c.evidence_version,c.evidence_sha256
    INTO #candidate
    FROM recon.vw_coleta_temporal_decision_v2 c JOIN stg.coleta_record d ON d.stage_record_id=c.stage_record_id
    WHERE c.data_export_execution_id=@data_export_execution_id AND c.reference_execution_id=@reference_execution_id;
    IF EXISTS(SELECT source_key FROM #candidate GROUP BY source_key HAVING COUNT_BIG(*)<>1)
        THROW 53113,N'COL_LAB_ROOT_CONFLICT',1;
    IF EXISTS(SELECT 1 FROM recon.coleta_temporal_laboratory_application WHERE execution_id=@data_export_execution_id)
    BEGIN
        IF EXISTS(SELECT source_key,@reference_execution_id,epoch_second,nano FROM #candidate
            EXCEPT SELECT source_key,reference_execution_id,candidate_epoch_second,candidate_nano
            FROM recon.coleta_temporal_laboratory_application WHERE execution_id=@data_export_execution_id)
            OR (SELECT COUNT_BIG(*) FROM #candidate)<>(SELECT COUNT_BIG(*) FROM recon.coleta_temporal_laboratory_application WHERE execution_id=@data_export_execution_id)
            THROW 53114,N'COL_LAB_DIVERGENT_REPLAY',1;
        SELECT COUNT_BIG(*) considered_roots,CONVERT(BIGINT,0) inserted_roots,CONVERT(BIGINT,0) updated_roots,
            COUNT_BIG(*) noop_roots,CONVERT(BIT,1) replay FROM #candidate;
        RETURN;
    END;
    -- Preserve 51428 before terminal precedence: equal exact time with divergent root content is a conflict.
    IF EXISTS(SELECT 1 FROM #candidate c JOIN core.coleta_temporal_laboratory t WITH(UPDLOCK,HOLDLOCK)
        ON t.source_instance=c.source_instance AND t.tenant_scope=c.tenant_scope AND t.source_key=c.source_key
        WHERE c.epoch_second=t.epoch_second AND c.nano=t.nano
            AND EXISTS(SELECT c.sequence_code_presence,c.sequence_code_json,c.status_code,c.terminal
                EXCEPT SELECT t.sequence_code_presence,t.sequence_code_json,t.status_code,t.terminal))
        THROW 51428,N'Frescor igual ou desconhecido possui conteúdo divergente.',1;
    SELECT c.*,CASE WHEN t.source_key IS NULL THEN N'INSERTED'
        WHEN t.terminal=0 AND c.terminal=1 THEN N'UPDATED'
        WHEN c.epoch_second<t.epoch_second OR (c.epoch_second=t.epoch_second AND c.nano<t.nano) THEN N'STALE_NO_OP'
        WHEN t.terminal=1 AND c.terminal=0 THEN N'TERMINAL_NO_OP'
        WHEN c.epoch_second=t.epoch_second AND c.nano=t.nano THEN N'NO_OP'
        ELSE N'UPDATED' END disposition
    INTO #plan FROM #candidate c LEFT JOIN core.coleta_temporal_laboratory t WITH(UPDLOCK,HOLDLOCK)
        ON t.source_instance=c.source_instance AND t.tenant_scope=c.tenant_scope AND t.source_key=c.source_key;
    INSERT core.coleta_temporal_laboratory
    SELECT source_instance,tenant_scope,source_key,sequence_code_presence,sequence_code_json,status_code,status_label,terminal,
        epoch_second,nano,instant_origin,@data_export_execution_id,@reference_execution_id,evidence_version,evidence_sha256,1
    FROM #plan WHERE disposition=N'INSERTED';
    UPDATE t SET sequence_code_presence=p.sequence_code_presence,sequence_code_json=p.sequence_code_json,
        status_code=p.status_code,status_label=p.status_label,terminal=p.terminal,epoch_second=p.epoch_second,nano=p.nano,
        instant_origin=p.instant_origin,execution_id=@data_export_execution_id,reference_execution_id=@reference_execution_id,
        evidence_version=p.evidence_version,evidence_sha256=p.evidence_sha256
    FROM core.coleta_temporal_laboratory t JOIN #plan p ON p.source_instance=t.source_instance
        AND p.tenant_scope=t.tenant_scope AND p.source_key=t.source_key WHERE p.disposition=N'UPDATED';
    INSERT recon.coleta_temporal_laboratory_application
    SELECT @data_export_execution_id,@reference_execution_id,source_key,disposition,epoch_second,nano FROM #plan;
    IF (SELECT COUNT_BIG(*) FROM recon.coleta_temporal_laboratory_application WHERE execution_id=@data_export_execution_id)
        <>(SELECT COUNT_BIG(*) FROM #candidate) THROW 53115,N'COL_LAB_RECONCILIATION_FAILED',1;
    SELECT COUNT_BIG(*) considered_roots,
        SUM(CONVERT(BIGINT,CASE WHEN disposition=N'INSERTED' THEN 1 ELSE 0 END)) inserted_roots,
        SUM(CONVERT(BIGINT,CASE WHEN disposition=N'UPDATED' THEN 1 ELSE 0 END)) updated_roots,
        SUM(CONVERT(BIGINT,CASE WHEN disposition IN(N'NO_OP',N'STALE_NO_OP',N'TERMINAL_NO_OP') THEN 1 ELSE 0 END)) noop_roots,
        CONVERT(BIT,0) replay FROM #plan;
END;
GO
CREATE PROCEDURE stg.usp_complete_coleta_temporal_data_export @execution_id UNIQUEIDENTIFIER
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    IF @@TRANCOUNT=0 THROW 53100,N'COL_EXACT_SHARED_TRANSACTION_REQUIRED',1;
    DECLARE @state NVARCHAR(32),@now DATETIME2(3)=SYSUTCDATETIME();
    SELECT @state=a.current_state FROM ctl.execution_attempt a WITH(UPDLOCK,HOLDLOCK)
        JOIN ctl.execution_partition p ON p.partition_id=a.partition_id
        WHERE a.execution_id=@execution_id AND p.entity_name=N'coletas' AND p.environment_name=N'LOCAL_SHADOW'
            AND p.source_instance=N'SYNTHETIC_COLETAS_TEMPORAL_LAB' AND p.tenant_scope=N'SYNTHETIC_COLETAS_TENANT';
    IF @state IS NULL OR @state NOT IN(N'EXTRACTING',N'STAGED')
        OR NOT EXISTS(SELECT 1 FROM ctl.execution_audit a WHERE a.execution_id=@execution_id AND a.status=N'COMPLETED'
            AND a.template_id=6908 AND a.records_delivered=(SELECT COUNT_BIG(*) FROM stg.execution_record WHERE execution_id=@execution_id)
            AND a.pages_fetched=(SELECT COUNT_BIG(*) FROM ctl.page_audit WHERE execution_id=@execution_id)
            AND a.terminal_page=a.pages_fetched)
        THROW 53116,N'COL_LAB_EXTRACTION_AUDIT_INCOMPLETE',1;
    IF @state=N'STAGED' RETURN;
    EXEC ctl.usp_control_plane_transition_execution @execution_id,N'EXTRACTING',N'EXTRACTED',N'EXTRACTION_OK',@now;
    EXEC ctl.usp_control_plane_transition_execution @execution_id,N'EXTRACTED',N'STAGED',N'STAGING_OK',@now;
END;
GO
