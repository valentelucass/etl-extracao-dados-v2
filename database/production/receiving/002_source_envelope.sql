-- Correct the receiving adapter to the qualified Faturas source envelope. No source/migration is rewritten.
IF DB_NAME()<>N'ETL_SISTEMA_V2' THROW 51900,'PRODUCTION_DATABASE_REQUIRED',1;
GO
ALTER PROCEDURE stg.usp_production_ingestion_store
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
  IF EXISTS(SELECT 1 FROM OPENJSON(@rows) WHERE [type]<>1 OR ISJSON([value])<>1 OR JSON_VALUE([value],'$.entity')<>N'FATURAS_POR_CLIENTE' OR TRY_CONVERT(int,JSON_VALUE([value],'$.templateId'))<>4924
   OR TRY_CONVERT(date,LEFT(JSON_VALUE([value],'$.record.service_at'),10)) IS NULL
   OR TRY_CONVERT(date,LEFT(JSON_VALUE([value],'$.record.service_at'),10))<>@day)
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
