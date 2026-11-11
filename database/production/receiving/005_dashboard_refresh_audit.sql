IF DB_NAME() <> N'ETL_SISTEMA_V2' THROW 52020,'REFRESH_AUDIT_PRODUCTION_DATABASE_REQUIRED',1;
GO
CREATE TABLE ctl.dashboard_refresh_cycle (
    cycle_id bigint IDENTITY NOT NULL PRIMARY KEY,
    source_cycle uniqueidentifier NOT NULL UNIQUE,
    started_utc datetime2(3) NOT NULL,
    completed_utc datetime2(3) NOT NULL,
    passed bit NOT NULL,
    failure_code varchar(96) NULL,
    source_hash binary(32) NOT NULL,
    CONSTRAINT CK_dashboard_refresh_dates CHECK(completed_utc>=started_utc),
    CONSTRAINT CK_dashboard_refresh_state CHECK((passed=1 AND failure_code IS NULL) OR (passed=0 AND failure_code IS NOT NULL))
);
CREATE TABLE ctl.dashboard_refresh_log (
    log_id bigint IDENTITY NOT NULL PRIMARY KEY,
    cycle_id bigint NOT NULL REFERENCES ctl.dashboard_refresh_cycle(cycle_id),
    run_id uniqueidentifier NOT NULL UNIQUE REFERENCES ctl.production_ingestion_run(run_id),
    pages int NOT NULL CHECK(pages>=0),
    unchanged_rows int NOT NULL CHECK(unchanged_rows>=0)
);
GO
CREATE PROCEDURE ctl.usp_store_dashboard_refresh_audit
    @source_cycle uniqueidentifier,@started_at datetimeoffset(3),@completed_at datetimeoffset(3),
    @passed bit,@failure_code varchar(96),@report_hash binary(32),@operations nvarchar(max)
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    DECLARE @started_utc datetime2(3)=CONVERT(datetime2(3),SWITCHOFFSET(@started_at,'+00:00')),
            @completed_utc datetime2(3)=CONVERT(datetime2(3),SWITCHOFFSET(@completed_at,'+00:00'));
    IF @source_cycle IS NULL OR @report_hash IS NULL OR @started_utc IS NULL OR @completed_utc IS NULL
       OR @passed IS NULL OR (@passed=1 AND @failure_code IS NOT NULL)
       OR (@passed=0 AND (NULLIF(@failure_code,'') IS NULL OR @failure_code COLLATE Latin1_General_100_BIN2 LIKE '%[^A-Z0-9_]%'))
       OR @started_utc>@completed_utc OR ISNULL(ISJSON(@operations,ARRAY),0)<>1
       OR DATALENGTH(@operations)>4194304
        THROW 52021,'REFRESH_AUDIT_RECEIPT_INVALID',1;

    DECLARE @runs TABLE(run_id uniqueidentifier NOT NULL PRIMARY KEY,entity_name varchar(40),pages int,reported_rows int);
    INSERT @runs
    SELECT j.run_id,j.entity_name,j.pages,j.reported_rows FROM OPENJSON(@operations) WITH(
        run_id uniqueidentifier '$.runId',entity_name varchar(40) '$.entity',
        pages int '$.pages',reported_rows int '$.rows'
    ) j;
    IF EXISTS(
       SELECT 1 FROM @runs i LEFT JOIN ctl.production_ingestion_run r ON r.run_id=i.run_id
       WHERE r.run_id IS NULL OR i.entity_name IS NULL OR r.entity_name<>i.entity_name OR r.run_state='RUNNING'
          OR i.pages IS NULL OR i.pages<0
          OR (r.run_state='STORED' AND (i.reported_rows IS NULL OR r.row_count<>i.reported_rows))
          OR (r.run_state='FAILED' AND (@passed=1 OR i.reported_rows IS NOT NULL))
          OR r.started_utc<@started_utc OR r.completed_utc IS NULL OR r.completed_utc>@completed_utc)
        THROW 52022,'REFRESH_AUDIT_STORED_RUNS_DIFFER',1;

    DECLARE @id bigint,@existing_hash binary(32);
    BEGIN TRY
        BEGIN TRANSACTION;
        SELECT @id=cycle_id,@existing_hash=source_hash
        FROM ctl.dashboard_refresh_cycle WITH(UPDLOCK,HOLDLOCK) WHERE source_cycle=@source_cycle;
        IF @id IS NOT NULL AND @existing_hash<>@report_hash
            THROW 52023,'REFRESH_AUDIT_CYCLE_DRIFT',1;
        IF @id IS NULL
        BEGIN
            INSERT ctl.dashboard_refresh_cycle(source_cycle,started_utc,completed_utc,passed,failure_code,source_hash)
            VALUES(@source_cycle,@started_utc,@completed_utc,@passed,@failure_code,@report_hash);
            SET @id=CONVERT(bigint,SCOPE_IDENTITY());
            -- Compare occurrences as a multiset, ignoring transport and capture identifiers.
            WITH inputs AS (
                SELECT i.run_id,i.pages,prev.run_id AS previous_run_id
                FROM @runs i JOIN ctl.production_ingestion_run r ON r.run_id=i.run_id
                OUTER APPLY (
                    SELECT TOP(1) p.run_id FROM ctl.production_ingestion_run p
                    WHERE p.source_scope=r.source_scope AND p.entity_name=r.entity_name
                      AND p.period_start=r.period_start AND p.run_state='STORED'
                      AND p.completed_utc<=r.started_utc AND p.run_id<>r.run_id
                    ORDER BY p.completed_utc DESC,p.run_id DESC
                ) prev
            ), paired_runs AS (
                SELECT run_id,run_id AS occurrence_run,1 AS is_current FROM inputs
                UNION ALL
                SELECT run_id,previous_run_id,0 FROM inputs WHERE previous_run_id IS NOT NULL
            ), counts AS (
                SELECT p.run_id,p.is_current,h.fingerprint,COUNT(*) AS occurrences
                FROM paired_runs p JOIN stg.production_export_occurrence s ON s.run_id=p.occurrence_run
                CROSS APPLY(VALUES(HASHBYTES('SHA2_256',JSON_QUERY(s.source_envelope,'$.record')))) h(fingerprint)
                GROUP BY p.run_id,p.is_current,h.fingerprint
            ), unchanged AS (
                SELECT c.run_id,SUM(LEAST(c.occurrences,p.occurrences)) AS rows_count
                FROM counts c JOIN counts p ON p.run_id=c.run_id AND p.fingerprint=c.fingerprint AND p.is_current=0
                WHERE c.is_current=1 GROUP BY c.run_id
            )
            INSERT ctl.dashboard_refresh_log(cycle_id,run_id,pages,unchanged_rows)
            SELECT @id,i.run_id,i.pages,COALESCE(u.rows_count,0)
            FROM inputs i LEFT JOIN unchanged u ON u.run_id=i.run_id;
        END;
        COMMIT;
        SELECT @id AS cycle_id;
    END TRY
    BEGIN CATCH
        IF XACT_STATE()<>0 ROLLBACK;
        THROW;
    END CATCH;
END;
GO
