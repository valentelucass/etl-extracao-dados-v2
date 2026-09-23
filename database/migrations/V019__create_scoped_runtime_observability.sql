-- B54 pending additive schema: scoped, bounded observation and alert consumer.
-- This file grants nothing. Installation and grants have separate receipts.
SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;
SET XACT_ABORT ON;
GO
CREATE PROCEDURE ctl.usp_runtime_observation @execution_id UNIQUEIDENTIFIER
AS
BEGIN
    SET NOCOUNT ON;
    IF DB_NAME()<>N'ETL_SISTEMA_V2_SHADOW' OR @execution_id IS NULL
        THROW 52510,N'RUNTIME_OBSERVATION_TARGET_REQUIRED',1;
    DECLARE @scope NVARCHAR(MAX)=(SELECT LOWER(CONVERT(CHAR(36),@execution_id)) AS execution FOR JSON PATH,WITHOUT_ARRAY_WRAPPER);
    IF ctl.fn_runtime_consumed_scope(@scope,0,DEFAULT)<>1
        THROW 52510,N'RUNTIME_OBSERVATION_CONSUMPTION_REQUIRED',1;
    DECLARE @now DATETIME2(3)=SYSUTCDATETIME();
    ;WITH pages AS (
        SELECT page_number,physical_rows,response_bytes,
          ROW_NUMBER() OVER(PARTITION BY page_number ORDER BY page_attempt DESC) AS ordinal
        FROM ctl.execution_page_audit WHERE execution_id=@execution_id
    )
    SELECT COALESCE(a.current_state,N'NOT_FOUND') AS state,
      CASE WHEN a.execution_id IS NULL THEN N'UNKNOWN'
           WHEN a.current_state=N'PUBLISHED' AND q.evaluation_state=N'PASSED' THEN N'UP'
           ELSE N'DEGRADED' END AS health,
      (SELECT COUNT_BIG(*) FROM pages WHERE ordinal=1) AS pages,
      (SELECT COALESCE(SUM(physical_rows),0) FROM pages WHERE ordinal=1) AS physical_rows,
      (SELECT COALESCE(SUM(response_bytes),0) FROM pages WHERE ordinal=1) AS response_bytes,
      p.candidate_rows,
      (SELECT COUNT_BIG(*) FROM ctl.execution_publication_event WHERE execution_id=@execution_id) AS publications,
      COALESCE(q.evaluation_state,N'ABSENT') AS quality,
      CASE WHEN a.terminal_at_utc IS NOT NULL THEN DATEDIFF_BIG(MILLISECOND,a.started_at_utc,a.terminal_at_utc) END AS duration_milliseconds,
      @now AS observed_at_utc
    FROM (VALUES(1)) seed(n)
    LEFT JOIN ctl.execution_attempt a ON a.execution_id=@execution_id
    LEFT JOIN ctl.execution_promotion_result p ON p.execution_id=@execution_id
    LEFT JOIN recon.execution_data_quality_evaluation q ON q.execution_id=@execution_id
    OPTION(MAXDOP 1);
END;
GO
CREATE PROCEDURE recon.usp_runtime_raise_alert
    @execution_id UNIQUEIDENTIFIER,@alert_sequence INT,@severity NVARCHAR(MAX),
    @alert_code NVARCHAR(MAX),@owner_role NVARCHAR(MAX),@occurrence_count BIGINT,
    @occurred_at_utc DATETIME2(3)
AS
BEGIN
    SET NOCOUNT ON;
    IF DB_NAME()<>N'ETL_SISTEMA_V2_SHADOW' OR @execution_id IS NULL
        THROW 52511,N'RUNTIME_ALERT_TARGET_REQUIRED',1;
    DECLARE @scope NVARCHAR(MAX)=(SELECT LOWER(CONVERT(CHAR(36),@execution_id)) AS execution FOR JSON PATH,WITHOUT_ARRAY_WRAPPER);
    IF ctl.fn_runtime_consumed_scope(@scope,1,DEFAULT)<>1
        THROW 52511,N'RUNTIME_ALERT_CONSUMPTION_REQUIRED',1;
    IF NOT EXISTS(SELECT 1 FROM ctl.execution_attempt WHERE execution_id=@execution_id)
        THROW 52511,N'RUNTIME_ALERT_OCCURRENCE_REQUIRED',1;
    DECLARE @correlation NVARCHAR(64)=LOWER(CONVERT(CHAR(64),
        HASHBYTES('SHA2_256',LOWER(CONVERT(VARCHAR(36),@execution_id))),2));
    EXEC recon.usp_raise_observability_alert @correlation,@alert_sequence,@severity,
        @alert_code,@owner_role,@occurrence_count,@occurred_at_utc;
END;
GO
