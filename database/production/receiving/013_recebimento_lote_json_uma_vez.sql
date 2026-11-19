IF DB_NAME()<>N'ETL_SISTEMA_V2' OR OBJECT_ID(N'ctl.production_export_additional_field','U') IS NULL
    THROW 52111,'Recebimento V2 com contrato de campos exigido.',1;
GO
CREATE OR ALTER PROCEDURE stg.usp_production_ingestion_store
 @run_id uniqueidentifier,@rows nvarchar(max),@expected_count int,@capture_sha256 char(64)
AS
BEGIN
 SET NOCOUNT ON; SET XACT_ABORT ON;
 IF @@TRANCOUNT<>0 THROW 51901,'OUTER_TRANSACTION_REFUSED',1;
 IF @expected_count IS NULL OR @expected_count<0 OR @expected_count>100000
 OR @rows IS NULL OR DATALENGTH(@rows)>134217728 OR ISJSON(@rows)<>1
 OR LEFT(LTRIM(@rows),1)<>'[' OR @capture_sha256 IS NULL OR LEN(@capture_sha256)<>64
 OR @capture_sha256 COLLATE Latin1_General_100_BIN2 LIKE '%[^0-9a-f]%'
 THROW 51903,'COMPLETE_BOUNDED_CAPTURE_REQUIRED',1;
 BEGIN TRY
  BEGIN TRANSACTION;
  DECLARE @day date,@scope char(64),@state varchar(16),@entity varchar(40),@report int,@date_field varchar(128),@fields int;
  SELECT @day=r.period_start,@scope=r.source_scope,@state=r.run_state,@entity=r.entity_name,
   @report=t.report_id,@date_field=t.row_date_field,@fields=t.expected_field_count
  FROM ctl.production_ingestion_run r WITH(UPDLOCK,HOLDLOCK)
  JOIN ctl.production_export_template t ON t.entity_name=r.entity_name WHERE r.run_id=@run_id;
  IF @state IS NULL OR @state<>'RUNNING' THROW 51904,'RUNNING_RUN_REQUIRED',1;
  -- Ler o pacote uma vez. A ordem, o texto original e o tipo são preservados para a conferência e a gravação.
  CREATE TABLE #recebimento_linhas (
      ordinal int NOT NULL PRIMARY KEY,
      [type] int NOT NULL,
      [value] nvarchar(max) COLLATE DATABASE_DEFAULT NULL
  );
  INSERT #recebimento_linhas(ordinal,[type],[value])
    SELECT CONVERT(int,[key])+1,[type],[value] FROM OPENJSON(@rows);
  IF (SELECT COUNT(*) FROM #recebimento_linhas)<>@expected_count THROW 51905,'SOURCE_COUNT_MISMATCH',1;
  -- Validate envelope before invoking JSON_VALUE/OPENJSON on nested strings.
  IF EXISTS(SELECT 1 FROM #recebimento_linhas WHERE [type]<>1 OR ISJSON([value])<>1)
   THROW 51906,'SOURCE_DATE_OR_ENVELOPE_MISMATCH',1;
  IF EXISTS(SELECT 1 FROM #recebimento_linhas r
   WHERE ISNULL(JSON_VALUE(r.[value],'$.entity'),N'') COLLATE Latin1_General_100_BIN2<>@entity COLLATE Latin1_General_100_BIN2
   OR ISNULL(TRY_CONVERT(int,JSON_VALUE(r.[value],'$.templateId')),-1)<>@report
   OR NOT EXISTS(SELECT 1 FROM OPENJSON(r.[value]) e WHERE e.[key]='record' AND e.[type]=5)
   OR (SELECT COUNT(*) FROM OPENJSON(r.[value]) e WHERE e.[key]='record')<>1)
   THROW 51906,'SOURCE_DATE_OR_ENVELOPE_MISMATCH',1;
  -- One capture uses either the original exact set or the observed dashboard revision.
  DECLARE @additional_fields int=(SELECT COUNT(*) FROM ctl.production_export_additional_field WHERE entity_name=@entity);
  DECLARE @actual_fields int=@fields;
  IF @expected_count>0
   SELECT @actual_fields=COUNT(*) FROM #recebimento_linhas envelope
    CROSS APPLY OPENJSON(envelope.[value],'$.record') r WHERE envelope.ordinal=1;
  IF @actual_fields NOT IN (@fields,@fields+@additional_fields)
   THROW 51925,'SOURCE_FIELD_CONTRACT_MISMATCH',1;
  -- A mesma passagem verifica a quantidade, os nomes duplicados e os campos permitidos.
  IF EXISTS (
      SELECT 1 FROM #recebimento_linhas r
      CROSS APPLY (
          SELECT COUNT(*) AS quantidade,
                 COUNT(DISTINCT j.[key] COLLATE Latin1_General_100_BIN2) AS unicos,
                 MAX(CASE WHEN f.field_name IS NULL AND a.field_name IS NULL THEN 1 ELSE 0 END) AS desconhecido
          FROM OPENJSON(r.[value],'$.record') j
          LEFT JOIN ctl.production_export_field f ON f.entity_name=@entity
              AND f.field_name=j.[key] COLLATE Latin1_General_100_BIN2
          LEFT JOIN ctl.production_export_additional_field a ON @actual_fields<>@fields AND a.entity_name=@entity
              AND a.field_name=j.[key] COLLATE Latin1_General_100_BIN2
      ) teste
      WHERE teste.quantidade<>@actual_fields OR teste.unicos<>@actual_fields OR teste.desconhecido=1
  )
   THROW 51925,'SOURCE_FIELD_CONTRACT_MISMATCH',1;
  IF @date_field IS NOT NULL
  BEGIN
   DECLARE @date_path nvarchar(256)=N'$.record.'+@date_field;
   -- Fractional seconds are removed for the civil-date check only, avoiding datetimeoffset
   -- rounding at midnight. Original fractions remain untouched in source_envelope.
   IF EXISTS(SELECT 1 FROM #recebimento_linhas r
    CROSS APPLY(SELECT JSON_VALUE(r.[value],@date_path) AS value) d
    CROSS APPLY(SELECT CASE
     WHEN RIGHT(d.value,1)='Z' THEN LEFT(d.value,19)+'+00:00'
     WHEN d.value COLLATE Latin1_General_100_BIN2 LIKE '%[+-][0-9][0-9]:[0-9][0-9]' THEN LEFT(d.value,19)+RIGHT(d.value,6)
     ELSE NULL END AS offset_value) o
    CROSS APPLY(SELECT CASE WHEN o.offset_value IS NOT NULL
     THEN CONVERT(date,TRY_CONVERT(datetimeoffset(0),o.offset_value) AT TIME ZONE 'E. South America Standard Time')
     ELSE TRY_CONVERT(date,LEFT(d.value,10)) END AS civil_date) c
    WHERE c.civil_date IS NULL OR c.civil_date<>@day)
    THROW 51906,'SOURCE_DATE_OR_ENVELOPE_MISMATCH',1;
  END;
  INSERT stg.production_export_occurrence(run_id,occurrence_ordinal,business_date,source_envelope,envelope_sha256)
  SELECT @run_id,ordinal,@day,[value],HASHBYTES('SHA2_256',CONVERT(varbinary(max),[value]))
  FROM #recebimento_linhas;
  UPDATE ctl.production_ingestion_run SET run_state='STORED',completed_utc=SYSUTCDATETIME(),
   row_count=@expected_count,capture_sha256=@capture_sha256 WHERE run_id=@run_id;
  UPDATE ctl.production_ingestion_coverage WITH(UPDLOCK,HOLDLOCK)
   SET latest_run_id=@run_id,stored_utc=SYSUTCDATETIME()
   WHERE source_scope=@scope AND entity_name=@entity AND business_date=@day;
  IF @@ROWCOUNT=0 INSERT ctl.production_ingestion_coverage VALUES(@scope,@entity,@day,@run_id,SYSUTCDATETIME());
  COMMIT;
 END TRY BEGIN CATCH
  IF XACT_STATE()<>0 ROLLBACK;
  THROW;
 END CATCH;
END;
GO
