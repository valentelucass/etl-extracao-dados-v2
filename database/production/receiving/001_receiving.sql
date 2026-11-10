-- Production receiving qualification, separate from immutable Shadow migrations.
-- This layer stores source occurrences. It does not publish dashboard facts.
IF DB_NAME() <> N'ETL_SISTEMA_V2' THROW 51900, 'PRODUCTION_DATABASE_REQUIRED', 1;
GO
CREATE TABLE ctl.production_ingestion_run (
 run_id uniqueidentifier NOT NULL PRIMARY KEY,
 source_scope char(64) COLLATE Latin1_General_100_BIN2 NOT NULL,
 entity_name varchar(40) NOT NULL CHECK(entity_name='FATURAS_POR_CLIENTE'),
 period_start date NOT NULL, period_end date NOT NULL,
 started_utc datetime2(3) NOT NULL DEFAULT SYSUTCDATETIME(),
 completed_utc datetime2(3) NULL,
 run_state varchar(16) NOT NULL CHECK(run_state IN ('RUNNING','STORED','FAILED')),
 request_sha256 char(64) NOT NULL,
 capture_sha256 char(64) NULL, row_count int NULL,
 failure_code varchar(96) NULL,
 CHECK(period_start=period_end),
 CHECK((run_state='STORED' AND completed_utc IS NOT NULL AND capture_sha256 IS NOT NULL AND row_count>=0)
 OR (run_state IN ('RUNNING','FAILED') AND capture_sha256 IS NULL AND row_count IS NULL))
);
GO
CREATE TABLE stg.production_export_occurrence (
 run_id uniqueidentifier NOT NULL REFERENCES ctl.production_ingestion_run(run_id),
 occurrence_ordinal int NOT NULL CHECK(occurrence_ordinal>0),
 business_date date NOT NULL,
 source_envelope nvarchar(max) NOT NULL CHECK(ISJSON(source_envelope)=1),
 envelope_sha256 binary(32) NOT NULL,
 PRIMARY KEY(run_id,occurrence_ordinal)
);
GO
CREATE TABLE ctl.production_ingestion_coverage (
 source_scope char(64) COLLATE Latin1_General_100_BIN2 NOT NULL,
 entity_name varchar(40) NOT NULL,
 business_date date NOT NULL,
 latest_run_id uniqueidentifier NOT NULL REFERENCES ctl.production_ingestion_run(run_id),
 stored_utc datetime2(3) NOT NULL,
 PRIMARY KEY(source_scope,entity_name,business_date)
);
GO
CREATE PROCEDURE ctl.usp_production_ingestion_start
 @run_id uniqueidentifier,@source_scope char(64),@business_date date,@request_sha256 char(64)
AS
BEGIN
 SET NOCOUNT ON; SET XACT_ABORT ON;
 IF @@TRANCOUNT<>0 THROW 51901,'OUTER_TRANSACTION_REFUSED',1;
 IF @run_id IS NULL OR @business_date IS NULL OR @source_scope IS NULL OR @request_sha256 IS NULL
 OR @source_scope COLLATE Latin1_General_100_BIN2 LIKE '%[^0-9a-f]%'
 OR @request_sha256 COLLATE Latin1_General_100_BIN2 LIKE '%[^0-9a-f]%'
 THROW 51902,'EXPLICIT_SCOPE_REQUIRED',1;
 INSERT ctl.production_ingestion_run(run_id,source_scope,entity_name,period_start,period_end,run_state,request_sha256)
 VALUES(@run_id,@source_scope,'FATURAS_POR_CLIENTE',@business_date,@business_date,'RUNNING',@request_sha256);
END;
GO
CREATE PROCEDURE stg.usp_production_ingestion_store
 @run_id uniqueidentifier,@rows nvarchar(max),@expected_count int,@capture_sha256 char(64)
AS
BEGIN
 SET NOCOUNT ON; SET XACT_ABORT ON;
 IF @@TRANCOUNT<>0 THROW 51901,'OUTER_TRANSACTION_REFUSED',1;
 IF @expected_count IS NULL OR @expected_count<0 OR @expected_count>12000
 OR @rows IS NULL OR DATALENGTH(@rows)>33554432 OR ISJSON(@rows)<>1
 OR LEFT(LTRIM(@rows),1)<>'[' OR @capture_sha256 IS NULL
 OR @capture_sha256 COLLATE Latin1_General_100_BIN2 LIKE '%[^0-9a-f]%'
 THROW 51903,'COMPLETE_BOUNDED_CAPTURE_REQUIRED',1;
 BEGIN TRY
  BEGIN TRANSACTION;
  DECLARE @day date,@scope char(64),@state varchar(16);
  SELECT @day=period_start,@scope=source_scope,@state=run_state
  FROM ctl.production_ingestion_run WITH(UPDLOCK,HOLDLOCK) WHERE run_id=@run_id;
  IF @state IS NULL OR @state<>'RUNNING' THROW 51904,'RUNNING_RUN_REQUIRED',1;
  IF (SELECT COUNT(*) FROM OPENJSON(@rows))<>@expected_count THROW 51905,'SOURCE_COUNT_MISMATCH',1;
  IF EXISTS(SELECT 1 FROM OPENJSON(@rows) WHERE [type]<>1 OR ISJSON([value])<>1
   OR TRY_CONVERT(date,LEFT(JSON_VALUE([value],'$.data.service_at'),10)) IS NULL
   OR TRY_CONVERT(date,LEFT(JSON_VALUE([value],'$.data.service_at'),10))<>@day)
   THROW 51906,'SOURCE_DATE_OR_ENVELOPE_MISMATCH',1;
  INSERT stg.production_export_occurrence(run_id,occurrence_ordinal,business_date,source_envelope,envelope_sha256)
  SELECT @run_id,CONVERT(int,[key])+1,@day,[value],HASHBYTES('SHA2_256',CONVERT(varbinary(max),[value]))
  FROM OPENJSON(@rows);
  UPDATE ctl.production_ingestion_run SET run_state='STORED',completed_utc=SYSUTCDATETIME(),
   row_count=@expected_count,capture_sha256=@capture_sha256 WHERE run_id=@run_id;
  UPDATE ctl.production_ingestion_coverage WITH(UPDLOCK,HOLDLOCK)
   SET latest_run_id=@run_id,stored_utc=SYSUTCDATETIME()
   WHERE source_scope=@scope AND entity_name='FATURAS_POR_CLIENTE' AND business_date=@day;
  IF @@ROWCOUNT=0 INSERT ctl.production_ingestion_coverage VALUES(@scope,'FATURAS_POR_CLIENTE',@day,@run_id,SYSUTCDATETIME());
  COMMIT;
 END TRY BEGIN CATCH
  IF XACT_STATE()<>0 ROLLBACK;
  THROW;
 END CATCH;
END;
GO
CREATE PROCEDURE ctl.usp_production_ingestion_fail @run_id uniqueidentifier,@failure_code varchar(96)
AS
BEGIN
 SET NOCOUNT ON; SET XACT_ABORT ON;
 IF @@TRANCOUNT<>0 THROW 51901,'OUTER_TRANSACTION_REFUSED',1;
 IF @failure_code IS NULL OR @failure_code='' OR @failure_code COLLATE Latin1_General_100_BIN2 LIKE '%[^A-Z0-9_]%'
 THROW 51907,'SANITIZED_FAILURE_CODE_REQUIRED',1;
 UPDATE ctl.production_ingestion_run SET run_state='FAILED',completed_utc=SYSUTCDATETIME(),failure_code=@failure_code
 WHERE run_id=@run_id AND run_state='RUNNING';
END;
GO
CREATE PROCEDURE ctl.usp_production_ingestion_observe @run_id uniqueidentifier
AS
BEGIN
 SET NOCOUNT ON;
 SELECT run_id,source_scope,entity_name,period_start,period_end,run_state,row_count,capture_sha256
 FROM ctl.production_ingestion_run WHERE run_id=@run_id;
 -- Runtime readback is restricted to the occurrence being reconciled.
 SELECT occurrence_ordinal,source_envelope,envelope_sha256 FROM stg.production_export_occurrence
 WHERE run_id=@run_id ORDER BY occurrence_ordinal;
END;
GO
CREATE PROCEDURE ctl.usp_production_ingestion_plan
 @source_scope char(64),@anchor date,@until date
AS
BEGIN
 SET NOCOUNT ON;
 IF @anchor IS NULL OR @until IS NULL OR @until<@anchor OR DATEDIFF(day,@anchor,@until)>3660
 THROW 51908,'BOUNDED_RECOVERY_RANGE_REQUIRED',1;
 WITH days AS(SELECT @anchor AS business_date UNION ALL SELECT DATEADD(day,1,business_date) FROM days WHERE business_date<@until)
 SELECT d.business_date,CONVERT(bit,CASE WHEN c.latest_run_id IS NULL THEN 0 ELSE 1 END) AS stored
 FROM days d LEFT JOIN ctl.production_ingestion_coverage c ON c.source_scope=@source_scope
 AND c.entity_name='FATURAS_POR_CLIENTE' AND c.business_date=d.business_date
 ORDER BY d.business_date OPTION(MAXRECURSION 3661);
END;
GO
CREATE VIEW pub.v2_ingestion_status AS
 SELECT entity_name,period_start,period_end,run_state,started_utc,completed_utc,row_count,failure_code
 FROM ctl.production_ingestion_run;
GO
-- Owner-reviewed additions to the existing exact procedure publication boundary.
INSERT dbo.v2_procedure_grant_allowlist(schema_name,procedure_name,role_name) VALUES
 ('ctl','usp_production_ingestion_start','v2_runtime'),
 ('stg','usp_production_ingestion_store','v2_runtime'),
 ('ctl','usp_production_ingestion_fail','v2_runtime'),
 ('ctl','usp_production_ingestion_observe','v2_runtime'),
 ('ctl','usp_production_ingestion_plan','v2_runtime');
EXEC dbo.usp_publish_v2_procedure_grant 'ctl','usp_production_ingestion_start','v2_runtime';
EXEC dbo.usp_publish_v2_procedure_grant 'stg','usp_production_ingestion_store','v2_runtime';
EXEC dbo.usp_publish_v2_procedure_grant 'ctl','usp_production_ingestion_fail','v2_runtime';
EXEC dbo.usp_publish_v2_procedure_grant 'ctl','usp_production_ingestion_observe','v2_runtime';
EXEC dbo.usp_publish_v2_procedure_grant 'ctl','usp_production_ingestion_plan','v2_runtime';
