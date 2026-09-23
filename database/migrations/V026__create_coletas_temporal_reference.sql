-- ADR0047: adopted observational staging. Operational promotion remains denied.
SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;
SET XACT_ABORT ON;
GO
CREATE TABLE stg.coleta_temporal_run (
    execution_id UNIQUEIDENTIFIER NOT NULL PRIMARY KEY,
    source_instance NVARCHAR(128) COLLATE Latin1_General_100_BIN2 NOT NULL,
    tenant_scope NVARCHAR(128) COLLATE Latin1_General_100_BIN2 NOT NULL,
    query_date DATE NOT NULL,
    contract_version NVARCHAR(128) COLLATE Latin1_General_100_BIN2 NOT NULL,
    selection_version NVARCHAR(128) COLLATE Latin1_General_100_BIN2 NOT NULL,
    selection_sha256 CHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL,
    capture_state NVARCHAR(16) NOT NULL CHECK(capture_state IN(N'OPEN',N'TERMINAL_LOCAL')),
    pages INT NULL,
    nodes BIGINT NULL,
    CHECK((capture_state=N'OPEN' AND pages IS NULL AND nodes IS NULL)
        OR (capture_state=N'TERMINAL_LOCAL' AND pages>=1 AND nodes>=pages AND nodes<=CONVERT(BIGINT,pages)*20))
);
CREATE TABLE stg.coleta_temporal_observation (
    execution_id UNIQUEIDENTIFIER NOT NULL REFERENCES stg.coleta_temporal_run(execution_id),
    page_number INT NOT NULL CHECK(page_number>=1),
    input_ordinal INT NOT NULL CHECK(input_ordinal BETWEEN 1 AND 20),
    source_key NVARCHAR(256) COLLATE Latin1_General_100_BIN2 NOT NULL,
    observation_json NVARCHAR(MAX) COLLATE Latin1_General_100_BIN2 NOT NULL CHECK(ISJSON(observation_json)=1),
    status_code NVARCHAR(32) COLLATE Latin1_General_100_BIN2 NULL,
    request_date_text NVARCHAR(32) COLLATE Latin1_General_100_BIN2 NULL,
    epoch_second BIGINT NULL,
    nano INT NULL,
    PRIMARY KEY(execution_id,page_number,input_ordinal),
    CHECK((epoch_second IS NULL AND nano IS NULL) OR (epoch_second IS NOT NULL AND nano BETWEEN 0 AND 999999999))
);
CREATE INDEX IX_coleta_temporal_identity ON stg.coleta_temporal_observation(execution_id,source_key)
    INCLUDE(status_code,request_date_text,epoch_second,nano);
CREATE TABLE stg.coleta_temporal_binding (
    data_export_execution_id UNIQUEIDENTIFIER NOT NULL REFERENCES ctl.execution_attempt(execution_id),
    reference_execution_id UNIQUEIDENTIFIER NOT NULL REFERENCES stg.coleta_temporal_run(execution_id),
    data_export_key NVARCHAR(256) COLLATE Latin1_General_100_BIN2 NOT NULL,
    reference_key NVARCHAR(256) COLLATE Latin1_General_100_BIN2 NOT NULL,
    request_date DATE NOT NULL,
    evidence_version NVARCHAR(128) COLLATE Latin1_General_100_BIN2 NOT NULL,
    evidence_sha256 CHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL,
    PRIMARY KEY(data_export_execution_id,reference_execution_id,data_export_key),
    UNIQUE(data_export_execution_id,reference_execution_id,reference_key)
);
GO
CREATE PROCEDURE stg.usp_lock_coleta_temporal @execution_id UNIQUEIDENTIFIER
AS
BEGIN
    SET NOCOUNT ON;
    IF @@TRANCOUNT=0 OR @execution_id IS NULL THROW 53008,N'COL_TEMPORAL_TRANSACTION_REQUIRED',1;
    DECLARE @resource NVARCHAR(255)=CONCAT(N'coleta-temporal:',LOWER(CONVERT(NVARCHAR(36),@execution_id))),@result INT;
    EXEC @result=sys.sp_getapplock @Resource=@resource,@LockMode=N'Exclusive',@LockOwner=N'Transaction',@LockTimeout=1500;
    IF @result<0 THROW 53008,N'COL_TEMPORAL_LOCK_UNAVAILABLE',1;
END;
GO
CREATE PROCEDURE stg.usp_stage_coleta_temporal @observation_json NVARCHAR(MAX)
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    IF ISJSON(@observation_json)<>1 OR @observation_json IS NULL OR DATALENGTH(@observation_json)>131072
        THROW 53000,N'COL_TEMPORAL_INPUT_INVALID',1;
    DECLARE @execution UNIQUEIDENTIFIER=TRY_CONVERT(UNIQUEIDENTIFIER,JSON_VALUE(@observation_json,'$.executionId')),
        @source NVARCHAR(4000)=JSON_VALUE(@observation_json,'$.source'),
        @tenant NVARCHAR(4000)=JSON_VALUE(@observation_json,'$.tenant'),
        @date DATE=TRY_CONVERT(DATE,JSON_VALUE(@observation_json,'$.queryDate'),23),
        @contract NVARCHAR(4000)=JSON_VALUE(@observation_json,'$.contractVersion'),
        @selection NVARCHAR(4000)=JSON_VALUE(@observation_json,'$.selectionVersion'),
        @sha NVARCHAR(4000)=JSON_VALUE(@observation_json,'$.selectionSha256'),
        @key NVARCHAR(4000)=JSON_VALUE(@observation_json,'$.sourceKey'),
        @page INT=TRY_CONVERT(INT,JSON_VALUE(@observation_json,'$.page')),
        @ordinal INT=TRY_CONVERT(INT,JSON_VALUE(@observation_json,'$.ordinal')),
        @status NVARCHAR(4000)=JSON_VALUE(@observation_json,'$.statusCode'),
        @request NVARCHAR(4000)=JSON_VALUE(@observation_json,'$.requestDateText'),
        @seconds BIGINT=TRY_CONVERT(BIGINT,JSON_VALUE(@observation_json,'$.epochSecond')),
        @nano INT=TRY_CONVERT(INT,JSON_VALUE(@observation_json,'$.nano'));
    IF @execution IS NULL OR @source IS NULL OR @tenant IS NULL OR @date IS NULL
        OR LEN(@source) NOT BETWEEN 1 AND 128 OR LEN(@tenant) NOT BETWEEN 1 AND 128
        OR @source COLLATE Latin1_General_100_BIN2 LIKE N'%[^-A-Za-z0-9._]%'
        OR LEFT(@source,1) COLLATE Latin1_General_100_BIN2 LIKE N'[^A-Za-z0-9]'
        OR @tenant COLLATE Latin1_General_100_BIN2 LIKE N'%[^-A-Za-z0-9._]%'
        OR LEFT(@tenant,1) COLLATE Latin1_General_100_BIN2 LIKE N'[^A-Za-z0-9]'
        OR UPPER(@tenant) IN(N'GLOBAL',N'DEFAULT',N'SINGLETON')
        OR @contract IS NULL OR @contract<>N'2026-09-10.coletas-temporal.1'
        OR @selection IS NULL OR @selection COLLATE Latin1_General_100_BIN2<>N'graphql-document-v1'
        OR @sha IS NULL OR DATALENGTH(@sha)<>128 OR @sha COLLATE Latin1_General_100_BIN2 LIKE N'%[^a-f0-9]%'
        OR @sha COLLATE Latin1_General_100_BIN2<>N'422e85b03c534a7addc32c5711c4a9de1275d4245213030733bd95f444113c35'
        OR @key IS NULL OR DATALENGTH(@key)>512 OR (LEFT(@key,8)<>N'INTEGER:' AND LEFT(@key,7)<>N'STRING:')
        OR @page IS NULL OR @page<1 OR @ordinal IS NULL OR @ordinal NOT BETWEEN 1 AND 20
        OR (@status IS NOT NULL AND @status COLLATE Latin1_General_100_BIN2 NOT IN
            (N'pending',N'treatment',N'manifested',N'in_transit',N'draft',N'finished',N'done',N'canceled',N'cancelled'))
        OR DATALENGTH(@request)>64
        OR (@seconds IS NULL AND JSON_VALUE(@observation_json,'$.epochSecond') IS NOT NULL)
        OR (@nano IS NULL AND JSON_VALUE(@observation_json,'$.nano') IS NOT NULL)
        OR (@seconds IS NULL AND @nano IS NOT NULL) OR (@seconds IS NOT NULL AND (@nano IS NULL OR @nano NOT BETWEEN 0 AND 999999999))
        OR (@seconds IS NOT NULL AND JSON_VALUE(@observation_json,'$.statusUpdatedAtText') IS NULL)
        OR JSON_VALUE(@observation_json,'$.observedAt') IS NULL
        THROW 53000,N'COL_TEMPORAL_INPUT_INVALID',1;
    -- Presence and raw values are kept independently, including wrong JSON types.
    IF EXISTS(SELECT 1 FROM (VALUES(N'status'),(N'statusUpdatedAt'),(N'requestDate')) f(name)
        CROSS APPLY(SELECT JSON_VALUE(@observation_json,CONCAT('$.',name,'Presence')) presence,
            JSON_VALUE(@observation_json,CONCAT('$.',name,'RawJson')) raw_value,
            JSON_VALUE(@observation_json,CONCAT('$.',name,'Text')) text_value) v
        WHERE presence IS NULL OR presence NOT IN(N'ABSENT',N'NULL',N'VALUE')
            OR (presence=N'VALUE' AND raw_value IS NULL)
            OR (presence<>N'VALUE' AND (raw_value IS NOT NULL OR text_value IS NOT NULL)))
        THROW 53000,N'COL_TEMPORAL_PRESENCE_INVALID',1;
    BEGIN TRANSACTION;
    EXEC stg.usp_lock_coleta_temporal @execution;
    DECLARE @state NVARCHAR(16);
    SELECT @state=capture_state FROM stg.coleta_temporal_run WITH(UPDLOCK,HOLDLOCK) WHERE execution_id=@execution;
    IF @state IS NULL
        INSERT stg.coleta_temporal_run VALUES(@execution,@source,@tenant,@date,@contract,@selection,@sha,N'OPEN',NULL,NULL);
    ELSE IF NOT EXISTS(SELECT 1 FROM stg.coleta_temporal_run WHERE execution_id=@execution
        AND source_instance=@source AND tenant_scope=@tenant AND query_date=@date
        AND contract_version=@contract AND selection_version=@selection AND selection_sha256=@sha)
        THROW 53001,N'COL_TEMPORAL_RUN_MISMATCH',1;
    IF EXISTS(SELECT 1 FROM stg.coleta_temporal_observation WHERE execution_id=@execution
        AND page_number=@page AND input_ordinal=@ordinal)
    BEGIN
        IF NOT EXISTS(SELECT 1 FROM stg.coleta_temporal_observation WHERE execution_id=@execution
            AND page_number=@page AND input_ordinal=@ordinal AND observation_json=@observation_json
            AND DATALENGTH(observation_json)=DATALENGTH(@observation_json))
            THROW 53002,N'COL_TEMPORAL_RETRY_CONFLICT',1;
    END
    ELSE
    BEGIN
        IF @state=N'TERMINAL_LOCAL' THROW 53003,N'COL_TEMPORAL_CAPTURE_SEALED',1;
        INSERT stg.coleta_temporal_observation VALUES(@execution,@page,@ordinal,@key,@observation_json,@status,@request,@seconds,@nano);
    END;
    COMMIT TRANSACTION;
END;
GO
CREATE PROCEDURE stg.usp_complete_coleta_temporal @execution_id UNIQUEIDENTIFIER,@pages INT,@nodes BIGINT
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    BEGIN TRANSACTION;
    EXEC stg.usp_lock_coleta_temporal @execution_id;
    IF NOT EXISTS(SELECT 1 FROM stg.coleta_temporal_run WITH(UPDLOCK,HOLDLOCK) WHERE execution_id=@execution_id)
        THROW 53004,N'COL_TEMPORAL_CAPTURE_MISSING',1;
    IF @pages IS NULL OR @pages<1 OR @nodes IS NULL OR @nodes<1
        OR NOT EXISTS(SELECT execution_id FROM stg.coleta_temporal_observation WHERE execution_id=@execution_id
            GROUP BY execution_id HAVING COUNT_BIG(*)=@nodes AND MIN(page_number)=1
                AND MAX(page_number)=@pages AND COUNT(DISTINCT page_number)=@pages)
        OR EXISTS(SELECT page_number FROM stg.coleta_temporal_observation WHERE execution_id=@execution_id
            GROUP BY page_number HAVING MIN(input_ordinal)<>1 OR MAX(input_ordinal)<>COUNT_BIG(*))
        THROW 53004,N'COL_TEMPORAL_CAPTURE_INCOMPLETE',1;
    UPDATE stg.coleta_temporal_run SET capture_state=N'TERMINAL_LOCAL',pages=@pages,nodes=@nodes
        WHERE execution_id=@execution_id AND capture_state=N'OPEN';
    COMMIT TRANSACTION;
END;
GO
CREATE PROCEDURE stg.usp_bind_coleta_temporal
    @data_export_execution_id UNIQUEIDENTIFIER,@reference_execution_id UNIQUEIDENTIFIER,
    @source NVARCHAR(MAX),@tenant NVARCHAR(MAX),@data_export_key NVARCHAR(MAX),@reference_key NVARCHAR(MAX),
    @request_date DATE,@evidence_version NVARCHAR(MAX),@evidence_sha256 NVARCHAR(MAX)
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    IF @data_export_key IS NULL OR LEFT(@data_export_key,8) COLLATE Latin1_General_100_BIN2<>N'INTEGER:'
        OR DATALENGTH(@data_export_key)>512 OR @reference_key IS NULL OR DATALENGTH(@reference_key)>512
        OR @evidence_version IS NULL OR LEN(@evidence_version) NOT BETWEEN 1 AND 128
        OR @evidence_sha256 IS NULL OR DATALENGTH(@evidence_sha256)<>128
        OR @evidence_sha256 COLLATE Latin1_General_100_BIN2 LIKE N'%[^a-f0-9]%'
        THROW 53005,N'COL_TEMPORAL_BINDING_INVALID',1;
    BEGIN TRANSACTION;
    -- Same lock order as qualification and old staging: Data Export attempt, reference run.
    IF NOT EXISTS(SELECT 1 FROM ctl.execution_attempt a WITH(UPDLOCK,HOLDLOCK)
        JOIN ctl.execution_partition p ON p.partition_id=a.partition_id
        JOIN ctl.execution_source_protocol protocol ON protocol.execution_id=a.execution_id
        WHERE a.execution_id=@data_export_execution_id AND p.entity_name=N'coletas'
            AND p.environment_name=N'LOCAL_SHADOW' AND p.source_instance=@source AND p.tenant_scope=@tenant
            AND protocol.source_kind=N'DATA_EXPORT')
        THROW 53006,N'COL_TEMPORAL_DATA_EXPORT_SCOPE_MISMATCH',1;
    EXEC stg.usp_lock_coleta_temporal @reference_execution_id;
    IF NOT EXISTS(SELECT 1 FROM stg.coleta_temporal_run WITH(UPDLOCK,HOLDLOCK)
        WHERE execution_id=@reference_execution_id AND source_instance=@source AND tenant_scope=@tenant AND query_date=@request_date)
        THROW 53006,N'COL_TEMPORAL_REFERENCE_SCOPE_MISMATCH',1;
    IF EXISTS(SELECT 1 FROM stg.coleta_temporal_binding WHERE data_export_execution_id=@data_export_execution_id
        AND reference_execution_id=@reference_execution_id AND (data_export_key=@data_export_key OR reference_key=@reference_key))
    BEGIN
        IF NOT EXISTS(SELECT 1 FROM stg.coleta_temporal_binding WHERE data_export_execution_id=@data_export_execution_id
            AND reference_execution_id=@reference_execution_id AND data_export_key=@data_export_key AND reference_key=@reference_key
            AND request_date=@request_date AND evidence_version=@evidence_version AND evidence_sha256=@evidence_sha256)
            THROW 53007,N'COL_TEMPORAL_BINDING_AMBIGUOUS',1;
    END
    ELSE INSERT stg.coleta_temporal_binding VALUES(@data_export_execution_id,@reference_execution_id,
        @data_export_key,@reference_key,@request_date,@evidence_version,@evidence_sha256);
    COMMIT TRANSACTION;
END;
GO
CREATE VIEW recon.vw_coleta_temporal_candidate
AS
WITH reference_variants AS (
    SELECT execution_id,source_key,status_code,request_date_text,epoch_second,nano,
        CONVERT(NVARCHAR(255),JSON_VALUE(observation_json,'$.statusUpdatedAtText')) AS status_time_text
    FROM stg.coleta_temporal_observation
    GROUP BY execution_id,source_key,status_code,request_date_text,epoch_second,nano,
        CONVERT(NVARCHAR(255),JSON_VALUE(observation_json,'$.statusUpdatedAtText'))
), reference_groups AS (
    SELECT execution_id,source_key,COUNT_BIG(*) variants FROM reference_variants GROUP BY execution_id,source_key
), data_export_groups AS (
    SELECT execution_id,source_key,COUNT(DISTINCT attribute_hash) variants
    FROM stg.coleta_record GROUP BY execution_id,source_key
), linked AS (
    SELECT d.execution_id AS data_export_execution_id,r.execution_id AS reference_execution_id,
        d.stage_record_id,d.source_key,b.reference_key,r.source_instance,r.tenant_scope,r.query_date,
        b.evidence_version,b.evidence_sha256,v.epoch_second,v.nano,
        CASE WHEN a.current_state<>N'STAGED' THEN N'DATA_EXPORT_INCOMPLETE'
            WHEN r.capture_state<>N'TERMINAL_LOCAL' THEN N'CAPTURE_INCOMPLETE'
            WHEN generic.validation_disposition<>N'VALID' THEN N'DATA_EXPORT_QUARANTINED'
            WHEN dg.variants<>1 THEN N'DATA_EXPORT_ROOT_CONFLICT'
            WHEN b.reference_key IS NULL THEN N'BINDING_ABSENT'
            WHEN g.variants IS NULL THEN N'REFERENCE_ABSENT'
            WHEN g.variants<>1 THEN N'REFERENCE_CONFLICT'
            WHEN JSON_VALUE(d.payload_json,'$.request_date') IS NULL
                OR JSON_VALUE(d.payload_json,'$.request_date') COLLATE Latin1_General_100_BIN2<>CONVERT(NVARCHAR(10),r.query_date,23)
                OR v.request_date_text IS NULL OR v.request_date_text<>CONVERT(NVARCHAR(10),r.query_date,23)
                THEN N'WINDOW_MISMATCH'
            WHEN v.status_code IS NULL OR d.status_code IS NULL OR v.status_code<>d.status_code THEN N'STATUS_MISMATCH'
            WHEN v.epoch_second IS NULL THEN N'REFERENCE_INVALID'
            WHEN d.freshness_origin=N'STATUS_UPDATED_AT' AND (d.freshness_raw IS NULL
                OR d.freshness_raw COLLATE Latin1_General_100_BIN2<>v.status_time_text
                OR DATALENGTH(d.freshness_raw)<>DATALENGTH(v.status_time_text)) THEN N'NATIVE_PRECISION_UNVERIFIED'
            WHEN d.freshness_origin=N'STATUS_UPDATED_AT' THEN N'DATA_EXPORT_TIME_RETAINED'
            ELSE N'COMPLEMENTED' END AS outcome
    FROM stg.coleta_record d
    JOIN data_export_groups dg ON dg.execution_id=d.execution_id AND dg.source_key=d.source_key
    JOIN stg.execution_record generic ON generic.stage_record_id=d.stage_record_id
    JOIN ctl.execution_attempt a ON a.execution_id=d.execution_id
    JOIN ctl.execution_partition p ON p.partition_id=a.partition_id
    JOIN ctl.execution_source_protocol protocol ON protocol.execution_id=a.execution_id AND protocol.source_kind=N'DATA_EXPORT'
    JOIN stg.coleta_temporal_run r ON r.source_instance=p.source_instance AND r.tenant_scope=p.tenant_scope
    LEFT JOIN stg.coleta_temporal_binding b ON b.data_export_execution_id=d.execution_id
        AND b.reference_execution_id=r.execution_id AND b.data_export_key=d.source_key
    LEFT JOIN reference_groups g ON g.execution_id=r.execution_id AND g.source_key=b.reference_key
    LEFT JOIN reference_variants v ON v.execution_id=g.execution_id AND v.source_key=g.source_key AND g.variants=1
    WHERE p.entity_name=N'coletas' AND p.environment_name=N'LOCAL_SHADOW'
)
SELECT data_export_execution_id,reference_execution_id,stage_record_id,source_key,reference_key,
    source_instance,tenant_scope,query_date,evidence_version,evidence_sha256,outcome,
    CASE WHEN outcome IN(N'COMPLEMENTED',N'DATA_EXPORT_TIME_RETAINED') THEN epoch_second END AS candidate_epoch_second,
    CASE WHEN outcome IN(N'COMPLEMENTED',N'DATA_EXPORT_TIME_RETAINED') THEN nano END AS candidate_nano,
    CONVERT(BIT,CASE WHEN outcome IN(N'COMPLEMENTED',N'DATA_EXPORT_TIME_RETAINED') AND nano%1000000=0 THEN 1 ELSE 0 END) AS millisecond_exact,
    CONVERT(BIT,0) AS promotion_authorized
FROM linked;
GO
CREATE PROCEDURE recon.usp_qualify_coleta_temporal @data_export_execution_id UNIQUEIDENTIFIER,@reference_execution_id UNIQUEIDENTIFIER
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
    -- Any generic quarantined row is deliberately counted as blocked, even without typed sidecar.
    SELECT COUNT_BIG(*) AS considered_rows,
        SUM(CONVERT(BIGINT,CASE WHEN c.candidate_epoch_second IS NOT NULL THEN 1 ELSE 0 END)) AS candidate_rows,
        SUM(CONVERT(BIGINT,CASE WHEN c.candidate_epoch_second IS NULL THEN 1 ELSE 0 END)) AS blocked_rows
    FROM stg.execution_record d WITH(HOLDLOCK)
    LEFT JOIN recon.vw_coleta_temporal_candidate c ON c.stage_record_id=d.stage_record_id
        AND c.data_export_execution_id=@data_export_execution_id AND c.reference_execution_id=@reference_execution_id
    WHERE d.execution_id=@data_export_execution_id;
    COMMIT TRANSACTION;
END;
GO
