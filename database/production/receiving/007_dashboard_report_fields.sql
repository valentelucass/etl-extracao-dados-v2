IF DB_NAME()<>N'ETL_SISTEMA_V2' THROW 52010,'DASHBOARD_REPORT_FIELDS_REQUIRE_V2',1;
IF OBJECT_ID(N'stg.usp_production_ingestion_store',N'P') IS NULL
 OR NOT EXISTS(SELECT 1 FROM ctl.production_export_template WHERE entity_name='COLETAS' AND report_id=6908 AND expected_field_count=31)
 OR NOT EXISTS(SELECT 1 FROM ctl.production_export_template WHERE entity_name='FRETES' AND report_id=6389 AND expected_field_count=114)
 THROW 52031,'DASHBOARD_REPORT_FIELDS_REQUIRE_BASELINE',1;

CREATE TABLE ctl.production_export_additional_field (
 entity_name varchar(40) NOT NULL REFERENCES ctl.production_export_template(entity_name),
 field_name varchar(128) COLLATE Latin1_General_100_BIN2 NOT NULL,
 CONSTRAINT pk_production_export_additional_field PRIMARY KEY(entity_name,field_name)
);

INSERT ctl.production_export_additional_field(entity_name,field_name) VALUES
 ('COLETAS','pck_cor_document'),
 ('COLETAS','pck_crn_id'),
 ('COLETAS','pck_ctr_id'),
 ('COLETAS','pck_pds_line_1'),
 ('COLETAS','pck_pds_line_2'),
 ('COLETAS','pck_pds_number'),
 ('COLETAS','pck_uer_id'),
 ('COLETAS','pck_vee_name'),
 ('COLETAS','request_hour'),
 ('FRETES','fit_crn_id'),
 ('FRETES','fit_f_l_calculation_basis'),
 ('FRETES','fit_f_l_cbs_rate'),
 ('FRETES','fit_f_l_cbs_value'),
 ('FRETES','fit_f_l_cfop_code'),
 ('FRETES','fit_f_l_cofins_rate'),
 ('FRETES','fit_f_l_cofins_value'),
 ('FRETES','fit_f_l_cst_type'),
 ('FRETES','fit_f_l_difal_tax_rate'),
 ('FRETES','fit_f_l_difal_tax_value_destination'),
 ('FRETES','fit_f_l_difal_tax_value_origin'),
 ('FRETES','fit_f_l_has_difal'),
 ('FRETES','fit_f_l_ibs_cbs_calculation_basis'),
 ('FRETES','fit_f_l_ibs_cbs_cst_code'),
 ('FRETES','fit_f_l_ibs_rate'),
 ('FRETES','fit_f_l_ibs_value'),
 ('FRETES','fit_f_l_inss_rate'),
 ('FRETES','fit_f_l_inss_value'),
 ('FRETES','fit_f_l_iss_witheld'),
 ('FRETES','fit_f_l_legal_basis'),
 ('FRETES','fit_f_l_pis_cofins_calculation_basis'),
 ('FRETES','fit_f_l_pis_rate'),
 ('FRETES','fit_f_l_pis_value'),
 ('FRETES','fit_f_l_tax_rate'),
 ('FRETES','fit_f_l_tax_value'),
 ('FRETES','fit_fhe_cte_created_at'),
 ('FRETES','fit_fhe_cte_cte_id'),
 ('FRETES','fit_fhe_cte_emission_type'),
 ('FRETES','fit_pyr_id'),
 ('FRETES','fit_rir_id'),
 ('FRETES','fit_rpt_id'),
 ('FRETES','fit_sdr_id'),
 ('FRETES','fit_spr_id'),
 ('FRETES','insurance_enabled'),
 ('FRETES','modal'),
 ('FRETES','service_at_day_hour');
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
  IF (SELECT COUNT(*) FROM OPENJSON(@rows))<>@expected_count THROW 51905,'SOURCE_COUNT_MISMATCH',1;
  -- Validate envelope before invoking JSON_VALUE/OPENJSON on nested strings.
  IF EXISTS(SELECT 1 FROM OPENJSON(@rows) WHERE [type]<>1 OR ISJSON([value])<>1)
   THROW 51906,'SOURCE_DATE_OR_ENVELOPE_MISMATCH',1;
  IF EXISTS(SELECT 1 FROM OPENJSON(@rows) r
   WHERE ISNULL(JSON_VALUE(r.[value],'$.entity'),N'') COLLATE Latin1_General_100_BIN2<>@entity COLLATE Latin1_General_100_BIN2
   OR ISNULL(TRY_CONVERT(int,JSON_VALUE(r.[value],'$.templateId')),-1)<>@report
   OR NOT EXISTS(SELECT 1 FROM OPENJSON(r.[value]) e WHERE e.[key]='record' AND e.[type]=5)
   OR (SELECT COUNT(*) FROM OPENJSON(r.[value]) e WHERE e.[key]='record')<>1)
   THROW 51906,'SOURCE_DATE_OR_ENVELOPE_MISMATCH',1;
  -- One capture uses either the original exact set or the observed dashboard revision.
  DECLARE @additional_fields int=(SELECT COUNT(*) FROM ctl.production_export_additional_field WHERE entity_name=@entity);
  DECLARE @actual_fields int=@fields;
  IF @expected_count>0
   SELECT @actual_fields=COUNT(*) FROM OPENJSON(@rows) envelope
    CROSS APPLY OPENJSON(envelope.[value],'$.record') r WHERE envelope.[key]='0';
  IF @actual_fields NOT IN (@fields,@fields+@additional_fields)
   THROW 51925,'SOURCE_FIELD_CONTRACT_MISMATCH',1;
  IF EXISTS(SELECT 1 FROM OPENJSON(@rows) r
   WHERE (SELECT COUNT(*) FROM OPENJSON(r.[value],'$.record'))<>@actual_fields
   OR (SELECT COUNT(DISTINCT j.[key] COLLATE Latin1_General_100_BIN2) FROM OPENJSON(r.[value],'$.record') j)<>@actual_fields
   OR EXISTS(SELECT 1 FROM OPENJSON(r.[value],'$.record') j
    WHERE NOT EXISTS(SELECT 1 FROM ctl.production_export_field f
      WHERE f.entity_name=@entity AND f.field_name=j.[key] COLLATE Latin1_General_100_BIN2)
      AND (@actual_fields=@fields OR NOT EXISTS(SELECT 1 FROM ctl.production_export_additional_field f
        WHERE f.entity_name=@entity AND f.field_name=j.[key] COLLATE Latin1_General_100_BIN2))))
   THROW 51925,'SOURCE_FIELD_CONTRACT_MISMATCH',1;
  IF @date_field IS NOT NULL
  BEGIN
   DECLARE @date_path nvarchar(256)=N'$.record.'+@date_field;
   -- Fractional seconds are removed for the civil-date check only, avoiding datetimeoffset
   -- rounding at midnight. Original fractions remain untouched in source_envelope.
   IF EXISTS(SELECT 1 FROM OPENJSON(@rows) r
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
  SELECT @run_id,CONVERT(int,[key])+1,@day,[value],HASHBYTES('SHA2_256',CONVERT(varbinary(max),[value]))
  FROM OPENJSON(@rows);
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
