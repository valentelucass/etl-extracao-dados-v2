-- Root preparation over actual 6908 observations. Child/display aliases remain typed per observation.
SET ANSI_NULLS ON;SET QUOTED_IDENTIFIER ON;
GO
CREATE TABLE stg.analytic_collection_attributes (
 stage_record_id BIGINT NOT NULL CONSTRAINT PK_ana_collection_attributes PRIMARY KEY REFERENCES stg.coleta_record(stage_record_id),
 [id] BIGINT NULL,[id_p] VARCHAR(6) NOT NULL,[id_w] INT NULL,[id_raw] NVARCHAR(4000) COLLATE Latin1_General_100_BIN2 NULL,
[pck_crn_psn_nickname] NVARCHAR(1024) COLLATE Latin1_General_100_BIN2 NULL,[pck_crn_psn_nickname_p] VARCHAR(6) NOT NULL,[pck_crn_psn_nickname_w] INT NULL,[pck_crn_psn_nickname_raw] NVARCHAR(4000) COLLATE Latin1_General_100_BIN2 NULL,
[sequence_code] BIGINT NULL,[sequence_code_p] VARCHAR(6) NOT NULL,[sequence_code_w] INT NULL,[sequence_code_raw] NVARCHAR(4000) COLLATE Latin1_General_100_BIN2 NULL,
[created_at] DATETIMEOFFSET(7) NULL,[created_at_p] VARCHAR(6) NOT NULL,[created_at_w] INT NULL,[created_at_raw] NVARCHAR(4000) COLLATE Latin1_General_100_BIN2 NULL,[created_at_nano] INT NULL,
[request_date] DATE NULL,[request_date_p] VARCHAR(6) NOT NULL,[request_date_w] INT NULL,[request_date_raw] NVARCHAR(4000) COLLATE Latin1_General_100_BIN2 NULL,
[service_date] DATE NULL,[service_date_p] VARCHAR(6) NOT NULL,[service_date_w] INT NULL,[service_date_raw] NVARCHAR(4000) COLLATE Latin1_General_100_BIN2 NULL,
[finish_date] DATE NULL,[finish_date_p] VARCHAR(6) NOT NULL,[finish_date_w] INT NULL,[finish_date_raw] NVARCHAR(4000) COLLATE Latin1_General_100_BIN2 NULL,
[pck_cor_nickname] NVARCHAR(1024) COLLATE Latin1_General_100_BIN2 NULL,[pck_cor_nickname_p] VARCHAR(6) NOT NULL,[pck_cor_nickname_w] INT NULL,[pck_cor_nickname_raw] NVARCHAR(4000) COLLATE Latin1_General_100_BIN2 NULL,
[invoices_volumes] BIGINT NULL,[invoices_volumes_p] VARCHAR(6) NOT NULL,[invoices_volumes_w] INT NULL,[invoices_volumes_raw] NVARCHAR(4000) COLLATE Latin1_General_100_BIN2 NULL,
[invoices_weight] DECIMAL(28,8) NULL,[invoices_weight_p] VARCHAR(6) NOT NULL,[invoices_weight_w] INT NULL,[invoices_weight_raw] NVARCHAR(4000) COLLATE Latin1_General_100_BIN2 NULL,
[taxed_weight] DECIMAL(28,8) NULL,[taxed_weight_p] VARCHAR(6) NOT NULL,[taxed_weight_w] INT NULL,[taxed_weight_raw] NVARCHAR(4000) COLLATE Latin1_General_100_BIN2 NULL,
[invoices_value] DECIMAL(28,8) NULL,[invoices_value_p] VARCHAR(6) NOT NULL,[invoices_value_w] INT NULL,[invoices_value_raw] NVARCHAR(4000) COLLATE Latin1_General_100_BIN2 NULL,
[status] NVARCHAR(1024) COLLATE Latin1_General_100_BIN2 NULL,[status_p] VARCHAR(6) NOT NULL,[status_w] INT NULL,[status_raw] NVARCHAR(4000) COLLATE Latin1_General_100_BIN2 NULL,
[pck_pds_cty_name] NVARCHAR(1024) COLLATE Latin1_General_100_BIN2 NULL,[pck_pds_cty_name_p] VARCHAR(6) NOT NULL,[pck_pds_cty_name_w] INT NULL,[pck_pds_cty_name_raw] NVARCHAR(4000) COLLATE Latin1_General_100_BIN2 NULL,
[pck_pds_cty_sae_code] NVARCHAR(1024) COLLATE Latin1_General_100_BIN2 NULL,[pck_pds_cty_sae_code_p] VARCHAR(6) NOT NULL,[pck_pds_cty_sae_code_w] INT NULL,[pck_pds_cty_sae_code_raw] NVARCHAR(4000) COLLATE Latin1_General_100_BIN2 NULL,
[pck_pds_postal_code] NVARCHAR(1024) COLLATE Latin1_General_100_BIN2 NULL,[pck_pds_postal_code_p] VARCHAR(6) NOT NULL,[pck_pds_postal_code_w] INT NULL,[pck_pds_postal_code_raw] NVARCHAR(4000) COLLATE Latin1_General_100_BIN2 NULL,
[pck_pds_neighborhood] NVARCHAR(1024) COLLATE Latin1_General_100_BIN2 NULL,[pck_pds_neighborhood_p] VARCHAR(6) NOT NULL,[pck_pds_neighborhood_w] INT NULL,[pck_pds_neighborhood_raw] NVARCHAR(4000) COLLATE Latin1_General_100_BIN2 NULL,
[pck_prn_name] NVARCHAR(1024) COLLATE Latin1_General_100_BIN2 NULL,[pck_prn_name_p] VARCHAR(6) NOT NULL,[pck_prn_name_w] INT NULL,[pck_prn_name_raw] NVARCHAR(4000) COLLATE Latin1_General_100_BIN2 NULL,
[pck_prn_drt_nickname] NVARCHAR(1024) COLLATE Latin1_General_100_BIN2 NULL,[pck_prn_drt_nickname_p] VARCHAR(6) NOT NULL,[pck_prn_drt_nickname_w] INT NULL,[pck_prn_drt_nickname_raw] NVARCHAR(4000) COLLATE Latin1_General_100_BIN2 NULL,
[updated_at] DATETIMEOFFSET(7) NULL,[updated_at_p] VARCHAR(6) NOT NULL,[updated_at_w] INT NULL,[updated_at_raw] NVARCHAR(4000) COLLATE Latin1_General_100_BIN2 NULL,[updated_at_nano] INT NULL,
[pck_loe_ore_description] NVARCHAR(1024) COLLATE Latin1_General_100_BIN2 NULL,[pck_loe_ore_description_p] VARCHAR(6) NOT NULL,[pck_loe_ore_description_w] INT NULL,[pck_loe_ore_description_raw] NVARCHAR(4000) COLLATE Latin1_General_100_BIN2 NULL,
[pck_pus_ore_description] NVARCHAR(1024) COLLATE Latin1_General_100_BIN2 NULL,[pck_pus_ore_description_p] VARCHAR(6) NOT NULL,[pck_pus_ore_description_w] INT NULL,[pck_pus_ore_description_raw] NVARCHAR(4000) COLLATE Latin1_General_100_BIN2 NULL,
[pck_pus_ore_trigger] NVARCHAR(1024) COLLATE Latin1_General_100_BIN2 NULL,[pck_pus_ore_trigger_p] VARCHAR(6) NOT NULL,[pck_pus_ore_trigger_w] INT NULL,[pck_pus_ore_trigger_raw] NVARCHAR(4000) COLLATE Latin1_General_100_BIN2 NULL,
[pck_uer_name] NVARCHAR(1024) COLLATE Latin1_General_100_BIN2 NULL,[pck_uer_name_p] VARCHAR(6) NOT NULL,[pck_uer_name_w] INT NULL,[pck_uer_name_raw] NVARCHAR(4000) COLLATE Latin1_General_100_BIN2 NULL,
[pck_ctr_name] NVARCHAR(1024) COLLATE Latin1_General_100_BIN2 NULL,[pck_ctr_name_p] VARCHAR(6) NOT NULL,[pck_ctr_name_w] INT NULL,[pck_ctr_name_raw] NVARCHAR(4000) COLLATE Latin1_General_100_BIN2 NULL,
[cancellation_reason] NVARCHAR(1024) COLLATE Latin1_General_100_BIN2 NULL,[cancellation_reason_p] VARCHAR(6) NOT NULL,[cancellation_reason_w] INT NULL,[cancellation_reason_raw] NVARCHAR(4000) COLLATE Latin1_General_100_BIN2 NULL,
[pck_mik_attempt_number] BIGINT NULL,[pck_mik_attempt_number_p] VARCHAR(6) NOT NULL,[pck_mik_attempt_number_w] INT NULL,[pck_mik_attempt_number_raw] NVARCHAR(4000) COLLATE Latin1_General_100_BIN2 NULL,
[pck_mik_mft_crn_psn_nickname] NVARCHAR(1024) COLLATE Latin1_General_100_BIN2 NULL,[pck_mik_mft_crn_psn_nickname_p] VARCHAR(6) NOT NULL,[pck_mik_mft_crn_psn_nickname_w] INT NULL,[pck_mik_mft_crn_psn_nickname_raw] NVARCHAR(4000) COLLATE Latin1_General_100_BIN2 NULL,
[pck_mik_mft_sequence_code] BIGINT NULL,[pck_mik_mft_sequence_code_p] VARCHAR(6) NOT NULL,[pck_mik_mft_sequence_code_w] INT NULL,[pck_mik_mft_sequence_code_raw] NVARCHAR(4000) COLLATE Latin1_General_100_BIN2 NULL,
[pck_mik_mft_vie_license_plate] NVARCHAR(1024) COLLATE Latin1_General_100_BIN2 NULL,[pck_mik_mft_vie_license_plate_p] VARCHAR(6) NOT NULL,[pck_mik_mft_vie_license_plate_w] INT NULL,[pck_mik_mft_vie_license_plate_raw] NVARCHAR(4000) COLLATE Latin1_General_100_BIN2 NULL,
[pck_mik_mft_vie_vee_name] NVARCHAR(1024) COLLATE Latin1_General_100_BIN2 NULL,[pck_mik_mft_vie_vee_name_p] VARCHAR(6) NOT NULL,[pck_mik_mft_vie_vee_name_w] INT NULL,[pck_mik_mft_vie_vee_name_raw] NVARCHAR(4000) COLLATE Latin1_General_100_BIN2 NULL
);
GO
CREATE TRIGGER stg.trg_analytic_collection_attributes ON stg.analytic_collection_attributes AFTER UPDATE,DELETE AS
BEGIN SET NOCOUNT ON;THROW 53740,N'ANA_COLLECTION_ATTRIBUTES_IMMUTABLE',1;END;
GO
CREATE TABLE core.analytic_collection_snapshot (
 snapshot_id BIGINT IDENTITY NOT NULL CONSTRAINT PK_ana_collection_snapshot PRIMARY KEY,
 run_id UNIQUEIDENTIFIER NOT NULL REFERENCES ctl.analytic_lab_run(run_id),source_key NVARCHAR(128) COLLATE Latin1_General_100_BIN2 NOT NULL,
 source_execution UNIQUEIDENTIFIER NOT NULL,previous_snapshot BIGINT NULL REFERENCES core.analytic_collection_snapshot(snapshot_id),
 representative_stage BIGINT NOT NULL REFERENCES stg.analytic_collection_attributes(stage_record_id),
 fresh_second BIGINT NOT NULL,fresh_nano INT NOT NULL,terminal BIT NOT NULL,
 status_code NVARCHAR(50) COLLATE Latin1_General_100_BIN2 NULL,status_label NVARCHAR(64) COLLATE Latin1_General_100_BIN2 NULL,
 occurrence_action NVARCHAR(2048) COLLATE Latin1_General_100_BIN2 NULL,attempt_count SMALLINT NOT NULL,
 business_date DATE NOT NULL,extracted_at DATETIME2(7) NOT NULL,
 [id] BIGINT NULL,[id_p] VARCHAR(6) NOT NULL,[id_w] INT NULL,[id_raw] NVARCHAR(4000) COLLATE Latin1_General_100_BIN2 NULL,
[pck_crn_psn_nickname] NVARCHAR(1024) COLLATE Latin1_General_100_BIN2 NULL,[pck_crn_psn_nickname_p] VARCHAR(6) NOT NULL,[pck_crn_psn_nickname_w] INT NULL,[pck_crn_psn_nickname_raw] NVARCHAR(4000) COLLATE Latin1_General_100_BIN2 NULL,
[sequence_code] BIGINT NULL,[sequence_code_p] VARCHAR(6) NOT NULL,[sequence_code_w] INT NULL,[sequence_code_raw] NVARCHAR(4000) COLLATE Latin1_General_100_BIN2 NULL,
[created_at] DATETIMEOFFSET(7) NULL,[created_at_p] VARCHAR(6) NOT NULL,[created_at_w] INT NULL,[created_at_raw] NVARCHAR(4000) COLLATE Latin1_General_100_BIN2 NULL,[created_at_nano] INT NULL,
[request_date] DATE NULL,[request_date_p] VARCHAR(6) NOT NULL,[request_date_w] INT NULL,[request_date_raw] NVARCHAR(4000) COLLATE Latin1_General_100_BIN2 NULL,
[service_date] DATE NULL,[service_date_p] VARCHAR(6) NOT NULL,[service_date_w] INT NULL,[service_date_raw] NVARCHAR(4000) COLLATE Latin1_General_100_BIN2 NULL,
[finish_date] DATE NULL,[finish_date_p] VARCHAR(6) NOT NULL,[finish_date_w] INT NULL,[finish_date_raw] NVARCHAR(4000) COLLATE Latin1_General_100_BIN2 NULL,
[pck_cor_nickname] NVARCHAR(1024) COLLATE Latin1_General_100_BIN2 NULL,[pck_cor_nickname_p] VARCHAR(6) NOT NULL,[pck_cor_nickname_w] INT NULL,[pck_cor_nickname_raw] NVARCHAR(4000) COLLATE Latin1_General_100_BIN2 NULL,
[invoices_volumes] BIGINT NULL,[invoices_volumes_p] VARCHAR(6) NOT NULL,[invoices_volumes_w] INT NULL,[invoices_volumes_raw] NVARCHAR(4000) COLLATE Latin1_General_100_BIN2 NULL,
[invoices_weight] DECIMAL(28,8) NULL,[invoices_weight_p] VARCHAR(6) NOT NULL,[invoices_weight_w] INT NULL,[invoices_weight_raw] NVARCHAR(4000) COLLATE Latin1_General_100_BIN2 NULL,
[taxed_weight] DECIMAL(28,8) NULL,[taxed_weight_p] VARCHAR(6) NOT NULL,[taxed_weight_w] INT NULL,[taxed_weight_raw] NVARCHAR(4000) COLLATE Latin1_General_100_BIN2 NULL,
[invoices_value] DECIMAL(28,8) NULL,[invoices_value_p] VARCHAR(6) NOT NULL,[invoices_value_w] INT NULL,[invoices_value_raw] NVARCHAR(4000) COLLATE Latin1_General_100_BIN2 NULL,
[status] NVARCHAR(1024) COLLATE Latin1_General_100_BIN2 NULL,[status_p] VARCHAR(6) NOT NULL,[status_w] INT NULL,[status_raw] NVARCHAR(4000) COLLATE Latin1_General_100_BIN2 NULL,
[pck_pds_cty_name] NVARCHAR(1024) COLLATE Latin1_General_100_BIN2 NULL,[pck_pds_cty_name_p] VARCHAR(6) NOT NULL,[pck_pds_cty_name_w] INT NULL,[pck_pds_cty_name_raw] NVARCHAR(4000) COLLATE Latin1_General_100_BIN2 NULL,
[pck_pds_cty_sae_code] NVARCHAR(1024) COLLATE Latin1_General_100_BIN2 NULL,[pck_pds_cty_sae_code_p] VARCHAR(6) NOT NULL,[pck_pds_cty_sae_code_w] INT NULL,[pck_pds_cty_sae_code_raw] NVARCHAR(4000) COLLATE Latin1_General_100_BIN2 NULL,
[pck_pds_postal_code] NVARCHAR(1024) COLLATE Latin1_General_100_BIN2 NULL,[pck_pds_postal_code_p] VARCHAR(6) NOT NULL,[pck_pds_postal_code_w] INT NULL,[pck_pds_postal_code_raw] NVARCHAR(4000) COLLATE Latin1_General_100_BIN2 NULL,
[pck_pds_neighborhood] NVARCHAR(1024) COLLATE Latin1_General_100_BIN2 NULL,[pck_pds_neighborhood_p] VARCHAR(6) NOT NULL,[pck_pds_neighborhood_w] INT NULL,[pck_pds_neighborhood_raw] NVARCHAR(4000) COLLATE Latin1_General_100_BIN2 NULL,
[pck_prn_name] NVARCHAR(1024) COLLATE Latin1_General_100_BIN2 NULL,[pck_prn_name_p] VARCHAR(6) NOT NULL,[pck_prn_name_w] INT NULL,[pck_prn_name_raw] NVARCHAR(4000) COLLATE Latin1_General_100_BIN2 NULL,
[pck_prn_drt_nickname] NVARCHAR(1024) COLLATE Latin1_General_100_BIN2 NULL,[pck_prn_drt_nickname_p] VARCHAR(6) NOT NULL,[pck_prn_drt_nickname_w] INT NULL,[pck_prn_drt_nickname_raw] NVARCHAR(4000) COLLATE Latin1_General_100_BIN2 NULL,
[updated_at] DATETIMEOFFSET(7) NULL,[updated_at_p] VARCHAR(6) NOT NULL,[updated_at_w] INT NULL,[updated_at_raw] NVARCHAR(4000) COLLATE Latin1_General_100_BIN2 NULL,[updated_at_nano] INT NULL,
[pck_loe_ore_description] NVARCHAR(1024) COLLATE Latin1_General_100_BIN2 NULL,[pck_loe_ore_description_p] VARCHAR(6) NOT NULL,[pck_loe_ore_description_w] INT NULL,[pck_loe_ore_description_raw] NVARCHAR(4000) COLLATE Latin1_General_100_BIN2 NULL,
[pck_pus_ore_description] NVARCHAR(1024) COLLATE Latin1_General_100_BIN2 NULL,[pck_pus_ore_description_p] VARCHAR(6) NOT NULL,[pck_pus_ore_description_w] INT NULL,[pck_pus_ore_description_raw] NVARCHAR(4000) COLLATE Latin1_General_100_BIN2 NULL,
[pck_pus_ore_trigger] NVARCHAR(1024) COLLATE Latin1_General_100_BIN2 NULL,[pck_pus_ore_trigger_p] VARCHAR(6) NOT NULL,[pck_pus_ore_trigger_w] INT NULL,[pck_pus_ore_trigger_raw] NVARCHAR(4000) COLLATE Latin1_General_100_BIN2 NULL,
[pck_uer_name] NVARCHAR(1024) COLLATE Latin1_General_100_BIN2 NULL,[pck_uer_name_p] VARCHAR(6) NOT NULL,[pck_uer_name_w] INT NULL,[pck_uer_name_raw] NVARCHAR(4000) COLLATE Latin1_General_100_BIN2 NULL,
[pck_ctr_name] NVARCHAR(1024) COLLATE Latin1_General_100_BIN2 NULL,[pck_ctr_name_p] VARCHAR(6) NOT NULL,[pck_ctr_name_w] INT NULL,[pck_ctr_name_raw] NVARCHAR(4000) COLLATE Latin1_General_100_BIN2 NULL,
[cancellation_reason] NVARCHAR(1024) COLLATE Latin1_General_100_BIN2 NULL,[cancellation_reason_p] VARCHAR(6) NOT NULL,[cancellation_reason_w] INT NULL,[cancellation_reason_raw] NVARCHAR(4000) COLLATE Latin1_General_100_BIN2 NULL,
 CONSTRAINT UQ_ana_collection_snapshot UNIQUE(run_id,source_key,source_execution),
 CONSTRAINT FK_ana_collection_snapshot_capture FOREIGN KEY(source_execution,source_key) REFERENCES stg.relational_lab_root(execution_id,source_key),
 CONSTRAINT CK_ana_collection_snapshot CHECK(fresh_nano BETWEEN 0 AND 999999999)
);
GO
CREATE TRIGGER core.trg_analytic_collection_snapshot ON core.analytic_collection_snapshot AFTER UPDATE,DELETE AS
BEGIN SET NOCOUNT ON;THROW 53741,N'ANA_COLLECTION_SNAPSHOT_IMMUTABLE',1;END;
GO
CREATE TABLE core.analytic_collection_lineage (
 snapshot_id BIGINT NOT NULL REFERENCES core.analytic_collection_snapshot(snapshot_id),
 stage_record_id BIGINT NOT NULL REFERENCES stg.analytic_collection_attributes(stage_record_id),
 CONSTRAINT PK_ana_collection_lineage PRIMARY KEY(snapshot_id,stage_record_id)
);
GO
CREATE TRIGGER core.trg_analytic_collection_lineage ON core.analytic_collection_lineage AFTER UPDATE,DELETE AS
BEGIN SET NOCOUNT ON;THROW 53741,N'ANA_COLLECTION_LINEAGE_IMMUTABLE',1;END;
GO
CREATE TABLE core.analytic_collection_current (
 run_id UNIQUEIDENTIFIER NOT NULL REFERENCES ctl.analytic_lab_run(run_id),source_key NVARCHAR(128) COLLATE Latin1_General_100_BIN2 NOT NULL,
 snapshot_id BIGINT NOT NULL REFERENCES core.analytic_collection_snapshot(snapshot_id),
 last_observation UNIQUEIDENTIFIER NOT NULL REFERENCES ctl.relational_lab_capture(execution_id),business_date DATE NOT NULL,latest_extracted_at DATETIME2(7) NOT NULL,
 CONSTRAINT PK_ana_collection_current PRIMARY KEY(run_id,source_key)
);
CREATE INDEX IX_ana_collection_current_window ON core.analytic_collection_current(run_id,business_date,source_key) INCLUDE(snapshot_id,last_observation,latest_extracted_at);
GO
CREATE TABLE ctl.analytic_collection_preparation (
 execution_id UNIQUEIDENTIFIER NOT NULL CONSTRAINT PK_ana_collection_preparation PRIMARY KEY REFERENCES ctl.relational_lab_capture(execution_id),
 run_id UNIQUEIDENTIFIER NOT NULL REFERENCES ctl.analytic_lab_run(run_id),fingerprint CHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL,
 physical_rows BIGINT NOT NULL,roots BIGINT NOT NULL,updates BIGINT NOT NULL,noops BIGINT NOT NULL,
 prepared_at DATETIME2(7) NOT NULL,CONSTRAINT CK_ana_collection_preparation CHECK(physical_rows>=roots AND roots=updates+noops)
);
GO
CREATE TRIGGER ctl.trg_analytic_collection_preparation ON ctl.analytic_collection_preparation AFTER UPDATE,DELETE AS
BEGIN SET NOCOUNT ON;THROW 53741,N'ANA_COLLECTION_PREPARATION_IMMUTABLE',1;END;
GO
CREATE PROCEDURE core.usp_prepare_analytic_collections @run_id UNIQUEIDENTIFIER,@execution_id UNIQUEIDENTIFIER,@fingerprint CHAR(64) AS
BEGIN
 SET NOCOUNT ON;SET XACT_ABORT OFF;
 DECLARE @rel UNIQUEIDENTIFIER,@rows BIGINT,@max INT,@now DATETIME2(7)=SYSUTCDATETIME();
 SELECT @rel=g.relational_run,@max=r.maximum_rows FROM ctl.analytic_lab_source_group g JOIN ctl.analytic_lab_run r ON r.run_id=g.run_id WHERE g.run_id=@run_id;
 IF @@TRANCOUNT=0 OR @rel IS NULL THROW 53742,N'ANA_COLLECTION_PREPARATION_SCOPE',1;
 EXEC ctl.usp_analytic_lab_lock @run_id,'DIMENSION';EXEC ctl.usp_relational_lab_lock @rel;
 SELECT @rows=c.physical_rows FROM ctl.relational_lab_capture c JOIN ctl.relational_lab_contract k ON k.run_id=c.run_id AND k.entity_name=c.entity_name
 JOIN ctl.execution_attempt e ON e.execution_id=c.execution_id
 WHERE c.execution_id=@execution_id AND c.run_id=@rel AND c.entity_name=N'coletas' AND c.contract_fingerprint=@fingerprint COLLATE Latin1_General_100_BIN2
 AND k.contract_fingerprint=c.contract_fingerprint AND e.contract_fingerprint COLLATE Latin1_General_100_BIN2=c.contract_fingerprint AND e.contract_version=N'synthetic-relational-v1';
 IF @rows IS NULL OR @rows>@max THROW 53743,N'ANA_COLLECTION_CAPTURE_CONTRACT',1;
 IF EXISTS(SELECT 1 FROM ctl.analytic_collection_preparation WHERE execution_id=@execution_id)
 BEGIN
  IF NOT EXISTS(SELECT 1 FROM ctl.analytic_collection_preparation WHERE execution_id=@execution_id AND run_id=@run_id AND fingerprint=@fingerprint) THROW 53744,N'ANA_COLLECTION_PREPARATION_RETRY',1;
  SELECT physical_rows,roots,updates,noops FROM ctl.analytic_collection_preparation WHERE execution_id=@execution_id;RETURN;
 END;
 SELECT d.*,e.epoch_second,e.nano,g.staged_at_utc INTO #observations FROM stg.coleta_record d
 JOIN stg.coleta_exact_time e ON e.stage_record_id=d.stage_record_id JOIN stg.execution_record g ON g.stage_record_id=d.stage_record_id
 WHERE d.execution_id=@execution_id;
 IF @rows<>(SELECT COUNT_BIG(*) FROM #observations) THROW 53745,N'ANA_COLLECTION_SOURCE_RECONCILIATION',1;
 SELECT o.stage_record_id,d.name field_name,d.kind,j.type wire_type,j.value COLLATE Latin1_General_100_BIN2 raw_value,
 CONVERT(VARCHAR(6),CASE WHEN j.[key] IS NULL THEN 'ABSENT' WHEN j.type=0 THEN 'NULL' ELSE 'VALUE' END) presence
 INTO #fields FROM #observations o CROSS APPLY(VALUES(N'id','INTEGER'),(N'pck_crn_psn_nickname','TEXT'),(N'sequence_code','INTEGER'),(N'created_at','INSTANT'),(N'request_date','DATE'),(N'service_date','DATE'),(N'finish_date','DATE'),(N'pck_cor_nickname','TEXT'),(N'invoices_volumes','INTEGER'),(N'invoices_weight','DECIMAL'),(N'taxed_weight','DECIMAL'),(N'invoices_value','DECIMAL'),(N'status','TEXT'),(N'pck_pds_cty_name','TEXT'),(N'pck_pds_cty_sae_code','TEXT'),(N'pck_pds_postal_code','TEXT'),(N'pck_pds_neighborhood','TEXT'),(N'pck_prn_name','TEXT'),(N'pck_prn_drt_nickname','TEXT'),(N'updated_at','INSTANT'),(N'pck_loe_ore_description','TEXT'),(N'pck_pus_ore_description','TEXT'),(N'pck_pus_ore_trigger','TEXT'),(N'pck_uer_name','TEXT'),(N'pck_ctr_name','TEXT'),(N'cancellation_reason','TEXT'),(N'pck_mik_attempt_number','INTEGER'),(N'pck_mik_mft_crn_psn_nickname','TEXT'),(N'pck_mik_mft_sequence_code','INTEGER'),(N'pck_mik_mft_vie_license_plate','TEXT'),(N'pck_mik_mft_vie_vee_name','TEXT')) d(name,kind)
 OUTER APPLY(SELECT * FROM OPENJSON(o.payload_json) j WHERE j.[key] COLLATE Latin1_General_100_BIN2=d.name COLLATE Latin1_General_100_BIN2) j;
 IF EXISTS(SELECT 1 FROM #fields GROUP BY stage_record_id,field_name HAVING COUNT_BIG(*)>1)
 OR EXISTS(SELECT 1 FROM #fields WHERE DATALENGTH(raw_value)>8000) THROW 53746,N'ANA_COLLECTION_FIELD_CAPTURE_BOUND',1;
 SELECT f.*,TRY_CONVERT(BIGINT,CASE WHEN kind='INTEGER' THEN raw_value END) integer_value,
 stg.ufn_analytic_decimal_exact(CASE WHEN kind='DECIMAL' THEN raw_value END) decimal_value,
 TRY_CONVERT(DATE,CASE WHEN kind='DATE' THEN raw_value END,23) date_value,
 core.ufn_analytic_iso_time(CASE WHEN kind='INSTANT' AND DATALENGTH(raw_value)<=128 THEN raw_value END) instant_value,
 CONVERT(INT,CASE WHEN kind='INSTANT' THEN CASE WHEN CHARINDEX(N'.',raw_value)=0 THEN 0
 ELSE TRY_CONVERT(INT,LEFT(SUBSTRING(raw_value,CHARINDEX(N'.',raw_value)+1,9)+N'000000000',9)) END END) instant_nano,
 CONVERT(NVARCHAR(1024),CASE WHEN kind='TEXT' AND DATALENGTH(raw_value)<=2048 THEN raw_value END) COLLATE Latin1_General_100_BIN2 text_value
 INTO #parsed FROM #fields f;
 -- Fraction extraction must stop before the offset suffix, preserving nine digits without rounding.
 UPDATE #parsed SET instant_nano=TRY_CONVERT(INT,RIGHT(core.ufn_analytic_time_comparison(raw_value),CHARINDEX(N':',REVERSE(core.ufn_analytic_time_comparison(raw_value)))-1)) WHERE kind='INSTANT';
 IF EXISTS(SELECT 1 FROM #parsed WHERE presence='VALUE' AND(
 (kind='INTEGER' AND(wire_type<>2 OR integer_value IS NULL OR raw_value COLLATE Latin1_General_100_BIN2 LIKE N'%[^0-9-]%'))
 OR(kind<>'INTEGER' AND wire_type<>1) OR(kind='TEXT' AND text_value IS NULL)
 OR(kind='DECIMAL' AND decimal_value IS NULL)
 OR(kind='DATE' AND(date_value IS NULL OR DATALENGTH(raw_value)<>20 OR CONVERT(NVARCHAR(10),date_value,23)<>raw_value))
 OR(kind='INSTANT' AND(instant_value IS NULL OR instant_nano IS NULL)))) THROW 53747,N'ANA_COLLECTION_FIELD_INVALID',1;
 INSERT stg.analytic_collection_attributes(stage_record_id,[id],[id_p],[id_w],[id_raw],[pck_crn_psn_nickname],[pck_crn_psn_nickname_p],[pck_crn_psn_nickname_w],[pck_crn_psn_nickname_raw],[sequence_code],[sequence_code_p],[sequence_code_w],[sequence_code_raw],[created_at],[created_at_p],[created_at_w],[created_at_raw],[created_at_nano],[request_date],[request_date_p],[request_date_w],[request_date_raw],[service_date],[service_date_p],[service_date_w],[service_date_raw],[finish_date],[finish_date_p],[finish_date_w],[finish_date_raw],[pck_cor_nickname],[pck_cor_nickname_p],[pck_cor_nickname_w],[pck_cor_nickname_raw],[invoices_volumes],[invoices_volumes_p],[invoices_volumes_w],[invoices_volumes_raw],[invoices_weight],[invoices_weight_p],[invoices_weight_w],[invoices_weight_raw],[taxed_weight],[taxed_weight_p],[taxed_weight_w],[taxed_weight_raw],[invoices_value],[invoices_value_p],[invoices_value_w],[invoices_value_raw],[status],[status_p],[status_w],[status_raw],[pck_pds_cty_name],[pck_pds_cty_name_p],[pck_pds_cty_name_w],[pck_pds_cty_name_raw],[pck_pds_cty_sae_code],[pck_pds_cty_sae_code_p],[pck_pds_cty_sae_code_w],[pck_pds_cty_sae_code_raw],[pck_pds_postal_code],[pck_pds_postal_code_p],[pck_pds_postal_code_w],[pck_pds_postal_code_raw],[pck_pds_neighborhood],[pck_pds_neighborhood_p],[pck_pds_neighborhood_w],[pck_pds_neighborhood_raw],[pck_prn_name],[pck_prn_name_p],[pck_prn_name_w],[pck_prn_name_raw],[pck_prn_drt_nickname],[pck_prn_drt_nickname_p],[pck_prn_drt_nickname_w],[pck_prn_drt_nickname_raw],[updated_at],[updated_at_p],[updated_at_w],[updated_at_raw],[updated_at_nano],[pck_loe_ore_description],[pck_loe_ore_description_p],[pck_loe_ore_description_w],[pck_loe_ore_description_raw],[pck_pus_ore_description],[pck_pus_ore_description_p],[pck_pus_ore_description_w],[pck_pus_ore_description_raw],[pck_pus_ore_trigger],[pck_pus_ore_trigger_p],[pck_pus_ore_trigger_w],[pck_pus_ore_trigger_raw],[pck_uer_name],[pck_uer_name_p],[pck_uer_name_w],[pck_uer_name_raw],[pck_ctr_name],[pck_ctr_name_p],[pck_ctr_name_w],[pck_ctr_name_raw],[cancellation_reason],[cancellation_reason_p],[cancellation_reason_w],[cancellation_reason_raw],[pck_mik_attempt_number],[pck_mik_attempt_number_p],[pck_mik_attempt_number_w],[pck_mik_attempt_number_raw],[pck_mik_mft_crn_psn_nickname],[pck_mik_mft_crn_psn_nickname_p],[pck_mik_mft_crn_psn_nickname_w],[pck_mik_mft_crn_psn_nickname_raw],[pck_mik_mft_sequence_code],[pck_mik_mft_sequence_code_p],[pck_mik_mft_sequence_code_w],[pck_mik_mft_sequence_code_raw],[pck_mik_mft_vie_license_plate],[pck_mik_mft_vie_license_plate_p],[pck_mik_mft_vie_license_plate_w],[pck_mik_mft_vie_license_plate_raw],[pck_mik_mft_vie_vee_name],[pck_mik_mft_vie_vee_name_p],[pck_mik_mft_vie_vee_name_w],[pck_mik_mft_vie_vee_name_raw])
 SELECT stage_record_id,CONVERT(BIGINT,MAX(CASE WHEN field_name=N'id' THEN integer_value END)) [id],
MAX(CASE WHEN field_name=N'id' THEN presence END) [id_p],
MAX(CASE WHEN field_name=N'id' THEN wire_type END) [id_w],
MAX(CASE WHEN field_name=N'id' THEN raw_value END) [id_raw],
CONVERT(NVARCHAR(1024),MAX(CASE WHEN field_name=N'pck_crn_psn_nickname' THEN text_value END)) [pck_crn_psn_nickname],
MAX(CASE WHEN field_name=N'pck_crn_psn_nickname' THEN presence END) [pck_crn_psn_nickname_p],
MAX(CASE WHEN field_name=N'pck_crn_psn_nickname' THEN wire_type END) [pck_crn_psn_nickname_w],
MAX(CASE WHEN field_name=N'pck_crn_psn_nickname' THEN raw_value END) [pck_crn_psn_nickname_raw],
CONVERT(BIGINT,MAX(CASE WHEN field_name=N'sequence_code' THEN integer_value END)) [sequence_code],
MAX(CASE WHEN field_name=N'sequence_code' THEN presence END) [sequence_code_p],
MAX(CASE WHEN field_name=N'sequence_code' THEN wire_type END) [sequence_code_w],
MAX(CASE WHEN field_name=N'sequence_code' THEN raw_value END) [sequence_code_raw],
CONVERT(DATETIMEOFFSET(7),MAX(CASE WHEN field_name=N'created_at' THEN instant_value END)) [created_at],
MAX(CASE WHEN field_name=N'created_at' THEN presence END) [created_at_p],
MAX(CASE WHEN field_name=N'created_at' THEN wire_type END) [created_at_w],
MAX(CASE WHEN field_name=N'created_at' THEN raw_value END) [created_at_raw],
MAX(CASE WHEN field_name=N'created_at' THEN instant_nano END) [created_at_nano],
CONVERT(DATE,MAX(CASE WHEN field_name=N'request_date' THEN date_value END)) [request_date],
MAX(CASE WHEN field_name=N'request_date' THEN presence END) [request_date_p],
MAX(CASE WHEN field_name=N'request_date' THEN wire_type END) [request_date_w],
MAX(CASE WHEN field_name=N'request_date' THEN raw_value END) [request_date_raw],
CONVERT(DATE,MAX(CASE WHEN field_name=N'service_date' THEN date_value END)) [service_date],
MAX(CASE WHEN field_name=N'service_date' THEN presence END) [service_date_p],
MAX(CASE WHEN field_name=N'service_date' THEN wire_type END) [service_date_w],
MAX(CASE WHEN field_name=N'service_date' THEN raw_value END) [service_date_raw],
CONVERT(DATE,MAX(CASE WHEN field_name=N'finish_date' THEN date_value END)) [finish_date],
MAX(CASE WHEN field_name=N'finish_date' THEN presence END) [finish_date_p],
MAX(CASE WHEN field_name=N'finish_date' THEN wire_type END) [finish_date_w],
MAX(CASE WHEN field_name=N'finish_date' THEN raw_value END) [finish_date_raw],
CONVERT(NVARCHAR(1024),MAX(CASE WHEN field_name=N'pck_cor_nickname' THEN text_value END)) [pck_cor_nickname],
MAX(CASE WHEN field_name=N'pck_cor_nickname' THEN presence END) [pck_cor_nickname_p],
MAX(CASE WHEN field_name=N'pck_cor_nickname' THEN wire_type END) [pck_cor_nickname_w],
MAX(CASE WHEN field_name=N'pck_cor_nickname' THEN raw_value END) [pck_cor_nickname_raw],
CONVERT(BIGINT,MAX(CASE WHEN field_name=N'invoices_volumes' THEN integer_value END)) [invoices_volumes],
MAX(CASE WHEN field_name=N'invoices_volumes' THEN presence END) [invoices_volumes_p],
MAX(CASE WHEN field_name=N'invoices_volumes' THEN wire_type END) [invoices_volumes_w],
MAX(CASE WHEN field_name=N'invoices_volumes' THEN raw_value END) [invoices_volumes_raw],
CONVERT(DECIMAL(28,8),MAX(CASE WHEN field_name=N'invoices_weight' THEN decimal_value END)) [invoices_weight],
MAX(CASE WHEN field_name=N'invoices_weight' THEN presence END) [invoices_weight_p],
MAX(CASE WHEN field_name=N'invoices_weight' THEN wire_type END) [invoices_weight_w],
MAX(CASE WHEN field_name=N'invoices_weight' THEN raw_value END) [invoices_weight_raw],
CONVERT(DECIMAL(28,8),MAX(CASE WHEN field_name=N'taxed_weight' THEN decimal_value END)) [taxed_weight],
MAX(CASE WHEN field_name=N'taxed_weight' THEN presence END) [taxed_weight_p],
MAX(CASE WHEN field_name=N'taxed_weight' THEN wire_type END) [taxed_weight_w],
MAX(CASE WHEN field_name=N'taxed_weight' THEN raw_value END) [taxed_weight_raw],
CONVERT(DECIMAL(28,8),MAX(CASE WHEN field_name=N'invoices_value' THEN decimal_value END)) [invoices_value],
MAX(CASE WHEN field_name=N'invoices_value' THEN presence END) [invoices_value_p],
MAX(CASE WHEN field_name=N'invoices_value' THEN wire_type END) [invoices_value_w],
MAX(CASE WHEN field_name=N'invoices_value' THEN raw_value END) [invoices_value_raw],
CONVERT(NVARCHAR(1024),MAX(CASE WHEN field_name=N'status' THEN text_value END)) [status],
MAX(CASE WHEN field_name=N'status' THEN presence END) [status_p],
MAX(CASE WHEN field_name=N'status' THEN wire_type END) [status_w],
MAX(CASE WHEN field_name=N'status' THEN raw_value END) [status_raw],
CONVERT(NVARCHAR(1024),MAX(CASE WHEN field_name=N'pck_pds_cty_name' THEN text_value END)) [pck_pds_cty_name],
MAX(CASE WHEN field_name=N'pck_pds_cty_name' THEN presence END) [pck_pds_cty_name_p],
MAX(CASE WHEN field_name=N'pck_pds_cty_name' THEN wire_type END) [pck_pds_cty_name_w],
MAX(CASE WHEN field_name=N'pck_pds_cty_name' THEN raw_value END) [pck_pds_cty_name_raw],
CONVERT(NVARCHAR(1024),MAX(CASE WHEN field_name=N'pck_pds_cty_sae_code' THEN text_value END)) [pck_pds_cty_sae_code],
MAX(CASE WHEN field_name=N'pck_pds_cty_sae_code' THEN presence END) [pck_pds_cty_sae_code_p],
MAX(CASE WHEN field_name=N'pck_pds_cty_sae_code' THEN wire_type END) [pck_pds_cty_sae_code_w],
MAX(CASE WHEN field_name=N'pck_pds_cty_sae_code' THEN raw_value END) [pck_pds_cty_sae_code_raw],
CONVERT(NVARCHAR(1024),MAX(CASE WHEN field_name=N'pck_pds_postal_code' THEN text_value END)) [pck_pds_postal_code],
MAX(CASE WHEN field_name=N'pck_pds_postal_code' THEN presence END) [pck_pds_postal_code_p],
MAX(CASE WHEN field_name=N'pck_pds_postal_code' THEN wire_type END) [pck_pds_postal_code_w],
MAX(CASE WHEN field_name=N'pck_pds_postal_code' THEN raw_value END) [pck_pds_postal_code_raw],
CONVERT(NVARCHAR(1024),MAX(CASE WHEN field_name=N'pck_pds_neighborhood' THEN text_value END)) [pck_pds_neighborhood],
MAX(CASE WHEN field_name=N'pck_pds_neighborhood' THEN presence END) [pck_pds_neighborhood_p],
MAX(CASE WHEN field_name=N'pck_pds_neighborhood' THEN wire_type END) [pck_pds_neighborhood_w],
MAX(CASE WHEN field_name=N'pck_pds_neighborhood' THEN raw_value END) [pck_pds_neighborhood_raw],
CONVERT(NVARCHAR(1024),MAX(CASE WHEN field_name=N'pck_prn_name' THEN text_value END)) [pck_prn_name],
MAX(CASE WHEN field_name=N'pck_prn_name' THEN presence END) [pck_prn_name_p],
MAX(CASE WHEN field_name=N'pck_prn_name' THEN wire_type END) [pck_prn_name_w],
MAX(CASE WHEN field_name=N'pck_prn_name' THEN raw_value END) [pck_prn_name_raw],
CONVERT(NVARCHAR(1024),MAX(CASE WHEN field_name=N'pck_prn_drt_nickname' THEN text_value END)) [pck_prn_drt_nickname],
MAX(CASE WHEN field_name=N'pck_prn_drt_nickname' THEN presence END) [pck_prn_drt_nickname_p],
MAX(CASE WHEN field_name=N'pck_prn_drt_nickname' THEN wire_type END) [pck_prn_drt_nickname_w],
MAX(CASE WHEN field_name=N'pck_prn_drt_nickname' THEN raw_value END) [pck_prn_drt_nickname_raw],
CONVERT(DATETIMEOFFSET(7),MAX(CASE WHEN field_name=N'updated_at' THEN instant_value END)) [updated_at],
MAX(CASE WHEN field_name=N'updated_at' THEN presence END) [updated_at_p],
MAX(CASE WHEN field_name=N'updated_at' THEN wire_type END) [updated_at_w],
MAX(CASE WHEN field_name=N'updated_at' THEN raw_value END) [updated_at_raw],
MAX(CASE WHEN field_name=N'updated_at' THEN instant_nano END) [updated_at_nano],
CONVERT(NVARCHAR(1024),MAX(CASE WHEN field_name=N'pck_loe_ore_description' THEN text_value END)) [pck_loe_ore_description],
MAX(CASE WHEN field_name=N'pck_loe_ore_description' THEN presence END) [pck_loe_ore_description_p],
MAX(CASE WHEN field_name=N'pck_loe_ore_description' THEN wire_type END) [pck_loe_ore_description_w],
MAX(CASE WHEN field_name=N'pck_loe_ore_description' THEN raw_value END) [pck_loe_ore_description_raw],
CONVERT(NVARCHAR(1024),MAX(CASE WHEN field_name=N'pck_pus_ore_description' THEN text_value END)) [pck_pus_ore_description],
MAX(CASE WHEN field_name=N'pck_pus_ore_description' THEN presence END) [pck_pus_ore_description_p],
MAX(CASE WHEN field_name=N'pck_pus_ore_description' THEN wire_type END) [pck_pus_ore_description_w],
MAX(CASE WHEN field_name=N'pck_pus_ore_description' THEN raw_value END) [pck_pus_ore_description_raw],
CONVERT(NVARCHAR(1024),MAX(CASE WHEN field_name=N'pck_pus_ore_trigger' THEN text_value END)) [pck_pus_ore_trigger],
MAX(CASE WHEN field_name=N'pck_pus_ore_trigger' THEN presence END) [pck_pus_ore_trigger_p],
MAX(CASE WHEN field_name=N'pck_pus_ore_trigger' THEN wire_type END) [pck_pus_ore_trigger_w],
MAX(CASE WHEN field_name=N'pck_pus_ore_trigger' THEN raw_value END) [pck_pus_ore_trigger_raw],
CONVERT(NVARCHAR(1024),MAX(CASE WHEN field_name=N'pck_uer_name' THEN text_value END)) [pck_uer_name],
MAX(CASE WHEN field_name=N'pck_uer_name' THEN presence END) [pck_uer_name_p],
MAX(CASE WHEN field_name=N'pck_uer_name' THEN wire_type END) [pck_uer_name_w],
MAX(CASE WHEN field_name=N'pck_uer_name' THEN raw_value END) [pck_uer_name_raw],
CONVERT(NVARCHAR(1024),MAX(CASE WHEN field_name=N'pck_ctr_name' THEN text_value END)) [pck_ctr_name],
MAX(CASE WHEN field_name=N'pck_ctr_name' THEN presence END) [pck_ctr_name_p],
MAX(CASE WHEN field_name=N'pck_ctr_name' THEN wire_type END) [pck_ctr_name_w],
MAX(CASE WHEN field_name=N'pck_ctr_name' THEN raw_value END) [pck_ctr_name_raw],
CONVERT(NVARCHAR(1024),MAX(CASE WHEN field_name=N'cancellation_reason' THEN text_value END)) [cancellation_reason],
MAX(CASE WHEN field_name=N'cancellation_reason' THEN presence END) [cancellation_reason_p],
MAX(CASE WHEN field_name=N'cancellation_reason' THEN wire_type END) [cancellation_reason_w],
MAX(CASE WHEN field_name=N'cancellation_reason' THEN raw_value END) [cancellation_reason_raw],
CONVERT(BIGINT,MAX(CASE WHEN field_name=N'pck_mik_attempt_number' THEN integer_value END)) [pck_mik_attempt_number],
MAX(CASE WHEN field_name=N'pck_mik_attempt_number' THEN presence END) [pck_mik_attempt_number_p],
MAX(CASE WHEN field_name=N'pck_mik_attempt_number' THEN wire_type END) [pck_mik_attempt_number_w],
MAX(CASE WHEN field_name=N'pck_mik_attempt_number' THEN raw_value END) [pck_mik_attempt_number_raw],
CONVERT(NVARCHAR(1024),MAX(CASE WHEN field_name=N'pck_mik_mft_crn_psn_nickname' THEN text_value END)) [pck_mik_mft_crn_psn_nickname],
MAX(CASE WHEN field_name=N'pck_mik_mft_crn_psn_nickname' THEN presence END) [pck_mik_mft_crn_psn_nickname_p],
MAX(CASE WHEN field_name=N'pck_mik_mft_crn_psn_nickname' THEN wire_type END) [pck_mik_mft_crn_psn_nickname_w],
MAX(CASE WHEN field_name=N'pck_mik_mft_crn_psn_nickname' THEN raw_value END) [pck_mik_mft_crn_psn_nickname_raw],
CONVERT(BIGINT,MAX(CASE WHEN field_name=N'pck_mik_mft_sequence_code' THEN integer_value END)) [pck_mik_mft_sequence_code],
MAX(CASE WHEN field_name=N'pck_mik_mft_sequence_code' THEN presence END) [pck_mik_mft_sequence_code_p],
MAX(CASE WHEN field_name=N'pck_mik_mft_sequence_code' THEN wire_type END) [pck_mik_mft_sequence_code_w],
MAX(CASE WHEN field_name=N'pck_mik_mft_sequence_code' THEN raw_value END) [pck_mik_mft_sequence_code_raw],
CONVERT(NVARCHAR(1024),MAX(CASE WHEN field_name=N'pck_mik_mft_vie_license_plate' THEN text_value END)) [pck_mik_mft_vie_license_plate],
MAX(CASE WHEN field_name=N'pck_mik_mft_vie_license_plate' THEN presence END) [pck_mik_mft_vie_license_plate_p],
MAX(CASE WHEN field_name=N'pck_mik_mft_vie_license_plate' THEN wire_type END) [pck_mik_mft_vie_license_plate_w],
MAX(CASE WHEN field_name=N'pck_mik_mft_vie_license_plate' THEN raw_value END) [pck_mik_mft_vie_license_plate_raw],
CONVERT(NVARCHAR(1024),MAX(CASE WHEN field_name=N'pck_mik_mft_vie_vee_name' THEN text_value END)) [pck_mik_mft_vie_vee_name],
MAX(CASE WHEN field_name=N'pck_mik_mft_vie_vee_name' THEN presence END) [pck_mik_mft_vie_vee_name_p],
MAX(CASE WHEN field_name=N'pck_mik_mft_vie_vee_name' THEN wire_type END) [pck_mik_mft_vie_vee_name_w],
MAX(CASE WHEN field_name=N'pck_mik_mft_vie_vee_name' THEN raw_value END) [pck_mik_mft_vie_vee_name_raw] FROM #parsed GROUP BY stage_record_id;
 SELECT o.stage_record_id,o.source_key,o.epoch_second,o.nano,o.terminal,o.status_code,o.status_label,o.occurrence_action,o.attempt_count,o.staged_at_utc
 INTO #cohort FROM #observations o JOIN stg.relational_lab_root r ON r.execution_id=@execution_id AND r.source_key=o.source_key
 AND r.epoch_second=o.epoch_second AND r.nano=o.nano AND r.terminal=o.terminal;
 IF EXISTS(SELECT 1 FROM #cohort o JOIN #parsed f ON f.stage_record_id=o.stage_record_id WHERE f.field_name NOT LIKE N'pck[_]mik[_]%'
 GROUP BY o.source_key,f.field_name HAVING(MIN(CASE f.presence WHEN 'ABSENT' THEN NULL WHEN 'NULL' THEN 0 ELSE 1 END)=0 AND MAX(CASE f.presence WHEN 'VALUE' THEN 1 ELSE 0 END)=1)
 OR COUNT(DISTINCT CASE WHEN f.kind='INSTANT' THEN core.ufn_analytic_time_comparison(f.raw_value) WHEN f.kind='DECIMAL' THEN CONVERT(NVARCHAR(100),f.decimal_value) ELSE f.raw_value END)>1)
 THROW 53748,N'ANA_COLLECTION_ROOT_ATTRIBUTE_CONFLICT',1;
 SELECT o.source_key,MIN(o.stage_record_id) representative_stage,MAX(o.epoch_second) fresh_second,MAX(o.nano) fresh_nano,
 CONVERT(BIT,MAX(CONVERT(INT,o.terminal))) terminal,MAX(o.status_code) status_code,MAX(o.status_label) status_label,
 MAX(o.occurrence_action) occurrence_action,MAX(o.attempt_count) attempt_count,MAX(o.staged_at_utc) extracted_at,
 MAX(a.[id]) [id],
CONVERT(VARCHAR(6),CASE WHEN MAX(CASE a.[id_p] WHEN 'VALUE' THEN 2 WHEN 'NULL' THEN 1 ELSE 0 END)=2 THEN 'VALUE' WHEN MAX(CASE a.[id_p] WHEN 'NULL' THEN 1 ELSE 0 END)=1 THEN 'NULL' ELSE 'ABSENT' END) [id_p],
MAX(a.[id_w]) [id_w],
MAX(a.[id_raw]) [id_raw],
MAX(a.[pck_crn_psn_nickname]) [pck_crn_psn_nickname],
CONVERT(VARCHAR(6),CASE WHEN MAX(CASE a.[pck_crn_psn_nickname_p] WHEN 'VALUE' THEN 2 WHEN 'NULL' THEN 1 ELSE 0 END)=2 THEN 'VALUE' WHEN MAX(CASE a.[pck_crn_psn_nickname_p] WHEN 'NULL' THEN 1 ELSE 0 END)=1 THEN 'NULL' ELSE 'ABSENT' END) [pck_crn_psn_nickname_p],
MAX(a.[pck_crn_psn_nickname_w]) [pck_crn_psn_nickname_w],
MAX(a.[pck_crn_psn_nickname_raw]) [pck_crn_psn_nickname_raw],
MAX(a.[sequence_code]) [sequence_code],
CONVERT(VARCHAR(6),CASE WHEN MAX(CASE a.[sequence_code_p] WHEN 'VALUE' THEN 2 WHEN 'NULL' THEN 1 ELSE 0 END)=2 THEN 'VALUE' WHEN MAX(CASE a.[sequence_code_p] WHEN 'NULL' THEN 1 ELSE 0 END)=1 THEN 'NULL' ELSE 'ABSENT' END) [sequence_code_p],
MAX(a.[sequence_code_w]) [sequence_code_w],
MAX(a.[sequence_code_raw]) [sequence_code_raw],
MAX(a.[created_at]) [created_at],
CONVERT(VARCHAR(6),CASE WHEN MAX(CASE a.[created_at_p] WHEN 'VALUE' THEN 2 WHEN 'NULL' THEN 1 ELSE 0 END)=2 THEN 'VALUE' WHEN MAX(CASE a.[created_at_p] WHEN 'NULL' THEN 1 ELSE 0 END)=1 THEN 'NULL' ELSE 'ABSENT' END) [created_at_p],
MAX(a.[created_at_w]) [created_at_w],
MAX(a.[created_at_raw]) [created_at_raw],
MAX(a.[created_at_nano]) [created_at_nano],
MAX(a.[request_date]) [request_date],
CONVERT(VARCHAR(6),CASE WHEN MAX(CASE a.[request_date_p] WHEN 'VALUE' THEN 2 WHEN 'NULL' THEN 1 ELSE 0 END)=2 THEN 'VALUE' WHEN MAX(CASE a.[request_date_p] WHEN 'NULL' THEN 1 ELSE 0 END)=1 THEN 'NULL' ELSE 'ABSENT' END) [request_date_p],
MAX(a.[request_date_w]) [request_date_w],
MAX(a.[request_date_raw]) [request_date_raw],
MAX(a.[service_date]) [service_date],
CONVERT(VARCHAR(6),CASE WHEN MAX(CASE a.[service_date_p] WHEN 'VALUE' THEN 2 WHEN 'NULL' THEN 1 ELSE 0 END)=2 THEN 'VALUE' WHEN MAX(CASE a.[service_date_p] WHEN 'NULL' THEN 1 ELSE 0 END)=1 THEN 'NULL' ELSE 'ABSENT' END) [service_date_p],
MAX(a.[service_date_w]) [service_date_w],
MAX(a.[service_date_raw]) [service_date_raw],
MAX(a.[finish_date]) [finish_date],
CONVERT(VARCHAR(6),CASE WHEN MAX(CASE a.[finish_date_p] WHEN 'VALUE' THEN 2 WHEN 'NULL' THEN 1 ELSE 0 END)=2 THEN 'VALUE' WHEN MAX(CASE a.[finish_date_p] WHEN 'NULL' THEN 1 ELSE 0 END)=1 THEN 'NULL' ELSE 'ABSENT' END) [finish_date_p],
MAX(a.[finish_date_w]) [finish_date_w],
MAX(a.[finish_date_raw]) [finish_date_raw],
MAX(a.[pck_cor_nickname]) [pck_cor_nickname],
CONVERT(VARCHAR(6),CASE WHEN MAX(CASE a.[pck_cor_nickname_p] WHEN 'VALUE' THEN 2 WHEN 'NULL' THEN 1 ELSE 0 END)=2 THEN 'VALUE' WHEN MAX(CASE a.[pck_cor_nickname_p] WHEN 'NULL' THEN 1 ELSE 0 END)=1 THEN 'NULL' ELSE 'ABSENT' END) [pck_cor_nickname_p],
MAX(a.[pck_cor_nickname_w]) [pck_cor_nickname_w],
MAX(a.[pck_cor_nickname_raw]) [pck_cor_nickname_raw],
MAX(a.[invoices_volumes]) [invoices_volumes],
CONVERT(VARCHAR(6),CASE WHEN MAX(CASE a.[invoices_volumes_p] WHEN 'VALUE' THEN 2 WHEN 'NULL' THEN 1 ELSE 0 END)=2 THEN 'VALUE' WHEN MAX(CASE a.[invoices_volumes_p] WHEN 'NULL' THEN 1 ELSE 0 END)=1 THEN 'NULL' ELSE 'ABSENT' END) [invoices_volumes_p],
MAX(a.[invoices_volumes_w]) [invoices_volumes_w],
MAX(a.[invoices_volumes_raw]) [invoices_volumes_raw],
MAX(a.[invoices_weight]) [invoices_weight],
CONVERT(VARCHAR(6),CASE WHEN MAX(CASE a.[invoices_weight_p] WHEN 'VALUE' THEN 2 WHEN 'NULL' THEN 1 ELSE 0 END)=2 THEN 'VALUE' WHEN MAX(CASE a.[invoices_weight_p] WHEN 'NULL' THEN 1 ELSE 0 END)=1 THEN 'NULL' ELSE 'ABSENT' END) [invoices_weight_p],
MAX(a.[invoices_weight_w]) [invoices_weight_w],
MAX(a.[invoices_weight_raw]) [invoices_weight_raw],
MAX(a.[taxed_weight]) [taxed_weight],
CONVERT(VARCHAR(6),CASE WHEN MAX(CASE a.[taxed_weight_p] WHEN 'VALUE' THEN 2 WHEN 'NULL' THEN 1 ELSE 0 END)=2 THEN 'VALUE' WHEN MAX(CASE a.[taxed_weight_p] WHEN 'NULL' THEN 1 ELSE 0 END)=1 THEN 'NULL' ELSE 'ABSENT' END) [taxed_weight_p],
MAX(a.[taxed_weight_w]) [taxed_weight_w],
MAX(a.[taxed_weight_raw]) [taxed_weight_raw],
MAX(a.[invoices_value]) [invoices_value],
CONVERT(VARCHAR(6),CASE WHEN MAX(CASE a.[invoices_value_p] WHEN 'VALUE' THEN 2 WHEN 'NULL' THEN 1 ELSE 0 END)=2 THEN 'VALUE' WHEN MAX(CASE a.[invoices_value_p] WHEN 'NULL' THEN 1 ELSE 0 END)=1 THEN 'NULL' ELSE 'ABSENT' END) [invoices_value_p],
MAX(a.[invoices_value_w]) [invoices_value_w],
MAX(a.[invoices_value_raw]) [invoices_value_raw],
MAX(a.[status]) [status],
CONVERT(VARCHAR(6),CASE WHEN MAX(CASE a.[status_p] WHEN 'VALUE' THEN 2 WHEN 'NULL' THEN 1 ELSE 0 END)=2 THEN 'VALUE' WHEN MAX(CASE a.[status_p] WHEN 'NULL' THEN 1 ELSE 0 END)=1 THEN 'NULL' ELSE 'ABSENT' END) [status_p],
MAX(a.[status_w]) [status_w],
MAX(a.[status_raw]) [status_raw],
MAX(a.[pck_pds_cty_name]) [pck_pds_cty_name],
CONVERT(VARCHAR(6),CASE WHEN MAX(CASE a.[pck_pds_cty_name_p] WHEN 'VALUE' THEN 2 WHEN 'NULL' THEN 1 ELSE 0 END)=2 THEN 'VALUE' WHEN MAX(CASE a.[pck_pds_cty_name_p] WHEN 'NULL' THEN 1 ELSE 0 END)=1 THEN 'NULL' ELSE 'ABSENT' END) [pck_pds_cty_name_p],
MAX(a.[pck_pds_cty_name_w]) [pck_pds_cty_name_w],
MAX(a.[pck_pds_cty_name_raw]) [pck_pds_cty_name_raw],
MAX(a.[pck_pds_cty_sae_code]) [pck_pds_cty_sae_code],
CONVERT(VARCHAR(6),CASE WHEN MAX(CASE a.[pck_pds_cty_sae_code_p] WHEN 'VALUE' THEN 2 WHEN 'NULL' THEN 1 ELSE 0 END)=2 THEN 'VALUE' WHEN MAX(CASE a.[pck_pds_cty_sae_code_p] WHEN 'NULL' THEN 1 ELSE 0 END)=1 THEN 'NULL' ELSE 'ABSENT' END) [pck_pds_cty_sae_code_p],
MAX(a.[pck_pds_cty_sae_code_w]) [pck_pds_cty_sae_code_w],
MAX(a.[pck_pds_cty_sae_code_raw]) [pck_pds_cty_sae_code_raw],
MAX(a.[pck_pds_postal_code]) [pck_pds_postal_code],
CONVERT(VARCHAR(6),CASE WHEN MAX(CASE a.[pck_pds_postal_code_p] WHEN 'VALUE' THEN 2 WHEN 'NULL' THEN 1 ELSE 0 END)=2 THEN 'VALUE' WHEN MAX(CASE a.[pck_pds_postal_code_p] WHEN 'NULL' THEN 1 ELSE 0 END)=1 THEN 'NULL' ELSE 'ABSENT' END) [pck_pds_postal_code_p],
MAX(a.[pck_pds_postal_code_w]) [pck_pds_postal_code_w],
MAX(a.[pck_pds_postal_code_raw]) [pck_pds_postal_code_raw],
MAX(a.[pck_pds_neighborhood]) [pck_pds_neighborhood],
CONVERT(VARCHAR(6),CASE WHEN MAX(CASE a.[pck_pds_neighborhood_p] WHEN 'VALUE' THEN 2 WHEN 'NULL' THEN 1 ELSE 0 END)=2 THEN 'VALUE' WHEN MAX(CASE a.[pck_pds_neighborhood_p] WHEN 'NULL' THEN 1 ELSE 0 END)=1 THEN 'NULL' ELSE 'ABSENT' END) [pck_pds_neighborhood_p],
MAX(a.[pck_pds_neighborhood_w]) [pck_pds_neighborhood_w],
MAX(a.[pck_pds_neighborhood_raw]) [pck_pds_neighborhood_raw],
MAX(a.[pck_prn_name]) [pck_prn_name],
CONVERT(VARCHAR(6),CASE WHEN MAX(CASE a.[pck_prn_name_p] WHEN 'VALUE' THEN 2 WHEN 'NULL' THEN 1 ELSE 0 END)=2 THEN 'VALUE' WHEN MAX(CASE a.[pck_prn_name_p] WHEN 'NULL' THEN 1 ELSE 0 END)=1 THEN 'NULL' ELSE 'ABSENT' END) [pck_prn_name_p],
MAX(a.[pck_prn_name_w]) [pck_prn_name_w],
MAX(a.[pck_prn_name_raw]) [pck_prn_name_raw],
MAX(a.[pck_prn_drt_nickname]) [pck_prn_drt_nickname],
CONVERT(VARCHAR(6),CASE WHEN MAX(CASE a.[pck_prn_drt_nickname_p] WHEN 'VALUE' THEN 2 WHEN 'NULL' THEN 1 ELSE 0 END)=2 THEN 'VALUE' WHEN MAX(CASE a.[pck_prn_drt_nickname_p] WHEN 'NULL' THEN 1 ELSE 0 END)=1 THEN 'NULL' ELSE 'ABSENT' END) [pck_prn_drt_nickname_p],
MAX(a.[pck_prn_drt_nickname_w]) [pck_prn_drt_nickname_w],
MAX(a.[pck_prn_drt_nickname_raw]) [pck_prn_drt_nickname_raw],
MAX(a.[updated_at]) [updated_at],
CONVERT(VARCHAR(6),CASE WHEN MAX(CASE a.[updated_at_p] WHEN 'VALUE' THEN 2 WHEN 'NULL' THEN 1 ELSE 0 END)=2 THEN 'VALUE' WHEN MAX(CASE a.[updated_at_p] WHEN 'NULL' THEN 1 ELSE 0 END)=1 THEN 'NULL' ELSE 'ABSENT' END) [updated_at_p],
MAX(a.[updated_at_w]) [updated_at_w],
MAX(a.[updated_at_raw]) [updated_at_raw],
MAX(a.[updated_at_nano]) [updated_at_nano],
MAX(a.[pck_loe_ore_description]) [pck_loe_ore_description],
CONVERT(VARCHAR(6),CASE WHEN MAX(CASE a.[pck_loe_ore_description_p] WHEN 'VALUE' THEN 2 WHEN 'NULL' THEN 1 ELSE 0 END)=2 THEN 'VALUE' WHEN MAX(CASE a.[pck_loe_ore_description_p] WHEN 'NULL' THEN 1 ELSE 0 END)=1 THEN 'NULL' ELSE 'ABSENT' END) [pck_loe_ore_description_p],
MAX(a.[pck_loe_ore_description_w]) [pck_loe_ore_description_w],
MAX(a.[pck_loe_ore_description_raw]) [pck_loe_ore_description_raw],
MAX(a.[pck_pus_ore_description]) [pck_pus_ore_description],
CONVERT(VARCHAR(6),CASE WHEN MAX(CASE a.[pck_pus_ore_description_p] WHEN 'VALUE' THEN 2 WHEN 'NULL' THEN 1 ELSE 0 END)=2 THEN 'VALUE' WHEN MAX(CASE a.[pck_pus_ore_description_p] WHEN 'NULL' THEN 1 ELSE 0 END)=1 THEN 'NULL' ELSE 'ABSENT' END) [pck_pus_ore_description_p],
MAX(a.[pck_pus_ore_description_w]) [pck_pus_ore_description_w],
MAX(a.[pck_pus_ore_description_raw]) [pck_pus_ore_description_raw],
MAX(a.[pck_pus_ore_trigger]) [pck_pus_ore_trigger],
CONVERT(VARCHAR(6),CASE WHEN MAX(CASE a.[pck_pus_ore_trigger_p] WHEN 'VALUE' THEN 2 WHEN 'NULL' THEN 1 ELSE 0 END)=2 THEN 'VALUE' WHEN MAX(CASE a.[pck_pus_ore_trigger_p] WHEN 'NULL' THEN 1 ELSE 0 END)=1 THEN 'NULL' ELSE 'ABSENT' END) [pck_pus_ore_trigger_p],
MAX(a.[pck_pus_ore_trigger_w]) [pck_pus_ore_trigger_w],
MAX(a.[pck_pus_ore_trigger_raw]) [pck_pus_ore_trigger_raw],
MAX(a.[pck_uer_name]) [pck_uer_name],
CONVERT(VARCHAR(6),CASE WHEN MAX(CASE a.[pck_uer_name_p] WHEN 'VALUE' THEN 2 WHEN 'NULL' THEN 1 ELSE 0 END)=2 THEN 'VALUE' WHEN MAX(CASE a.[pck_uer_name_p] WHEN 'NULL' THEN 1 ELSE 0 END)=1 THEN 'NULL' ELSE 'ABSENT' END) [pck_uer_name_p],
MAX(a.[pck_uer_name_w]) [pck_uer_name_w],
MAX(a.[pck_uer_name_raw]) [pck_uer_name_raw],
MAX(a.[pck_ctr_name]) [pck_ctr_name],
CONVERT(VARCHAR(6),CASE WHEN MAX(CASE a.[pck_ctr_name_p] WHEN 'VALUE' THEN 2 WHEN 'NULL' THEN 1 ELSE 0 END)=2 THEN 'VALUE' WHEN MAX(CASE a.[pck_ctr_name_p] WHEN 'NULL' THEN 1 ELSE 0 END)=1 THEN 'NULL' ELSE 'ABSENT' END) [pck_ctr_name_p],
MAX(a.[pck_ctr_name_w]) [pck_ctr_name_w],
MAX(a.[pck_ctr_name_raw]) [pck_ctr_name_raw],
MAX(a.[cancellation_reason]) [cancellation_reason],
CONVERT(VARCHAR(6),CASE WHEN MAX(CASE a.[cancellation_reason_p] WHEN 'VALUE' THEN 2 WHEN 'NULL' THEN 1 ELSE 0 END)=2 THEN 'VALUE' WHEN MAX(CASE a.[cancellation_reason_p] WHEN 'NULL' THEN 1 ELSE 0 END)=1 THEN 'NULL' ELSE 'ABSENT' END) [cancellation_reason_p],
MAX(a.[cancellation_reason_w]) [cancellation_reason_w],
MAX(a.[cancellation_reason_raw]) [cancellation_reason_raw] INTO #candidate FROM #cohort o JOIN stg.analytic_collection_attributes a ON a.stage_record_id=o.stage_record_id GROUP BY o.source_key;
 IF (SELECT COUNT_BIG(*) FROM #candidate)<>(SELECT root_rows FROM ctl.relational_lab_capture WHERE execution_id=@execution_id)
 THROW 53749,N'ANA_COLLECTION_COHORT_INCOMPLETE',1;
 IF EXISTS(SELECT 1 FROM #candidate c LEFT JOIN core.analytic_collection_current p ON p.run_id=@run_id AND p.source_key=c.source_key
 JOIN core.relational_lab_root actual ON actual.run_id=@rel AND actual.entity_name=N'coletas' AND actual.source_key=c.source_key
 WHERE p.snapshot_id IS NULL AND actual.execution_id<>@execution_id) THROW 53750,N'ANA_COLLECTION_EFFECTIVE_CONTEXT_MISSING',1;
 IF EXISTS(SELECT 1 FROM #candidate c JOIN core.analytic_collection_current p ON p.run_id=@run_id AND p.source_key=c.source_key
 JOIN core.analytic_collection_snapshot previous ON previous.snapshot_id=p.snapshot_id
 WHERE c.fresh_second=previous.fresh_second AND c.fresh_nano=previous.fresh_nano
 AND EXISTS(SELECT CASE WHEN c.[id_p]='ABSENT' THEN previous.[id] ELSE c.[id] END,CASE WHEN c.[pck_crn_psn_nickname_p]='ABSENT' THEN previous.[pck_crn_psn_nickname] ELSE c.[pck_crn_psn_nickname] END,CASE WHEN c.[sequence_code_p]='ABSENT' THEN previous.[sequence_code] ELSE c.[sequence_code] END,CASE WHEN c.[created_at_p]='ABSENT' THEN previous.[created_at] ELSE c.[created_at] END,CASE WHEN c.[created_at_p]='ABSENT' THEN previous.[created_at_nano] ELSE c.[created_at_nano] END,CASE WHEN c.[request_date_p]='ABSENT' THEN previous.[request_date] ELSE c.[request_date] END,CASE WHEN c.[service_date_p]='ABSENT' THEN previous.[service_date] ELSE c.[service_date] END,CASE WHEN c.[finish_date_p]='ABSENT' THEN previous.[finish_date] ELSE c.[finish_date] END,CASE WHEN c.[pck_cor_nickname_p]='ABSENT' THEN previous.[pck_cor_nickname] ELSE c.[pck_cor_nickname] END,CASE WHEN c.[invoices_volumes_p]='ABSENT' THEN previous.[invoices_volumes] ELSE c.[invoices_volumes] END,CASE WHEN c.[invoices_weight_p]='ABSENT' THEN previous.[invoices_weight] ELSE c.[invoices_weight] END,CASE WHEN c.[taxed_weight_p]='ABSENT' THEN previous.[taxed_weight] ELSE c.[taxed_weight] END,CASE WHEN c.[invoices_value_p]='ABSENT' THEN previous.[invoices_value] ELSE c.[invoices_value] END,CASE WHEN c.[status_p]='ABSENT' THEN previous.[status] ELSE c.[status] END,CASE WHEN c.[pck_pds_cty_name_p]='ABSENT' THEN previous.[pck_pds_cty_name] ELSE c.[pck_pds_cty_name] END,CASE WHEN c.[pck_pds_cty_sae_code_p]='ABSENT' THEN previous.[pck_pds_cty_sae_code] ELSE c.[pck_pds_cty_sae_code] END,CASE WHEN c.[pck_pds_postal_code_p]='ABSENT' THEN previous.[pck_pds_postal_code] ELSE c.[pck_pds_postal_code] END,CASE WHEN c.[pck_pds_neighborhood_p]='ABSENT' THEN previous.[pck_pds_neighborhood] ELSE c.[pck_pds_neighborhood] END,CASE WHEN c.[pck_prn_name_p]='ABSENT' THEN previous.[pck_prn_name] ELSE c.[pck_prn_name] END,CASE WHEN c.[pck_prn_drt_nickname_p]='ABSENT' THEN previous.[pck_prn_drt_nickname] ELSE c.[pck_prn_drt_nickname] END,CASE WHEN c.[updated_at_p]='ABSENT' THEN previous.[updated_at] ELSE c.[updated_at] END,CASE WHEN c.[updated_at_p]='ABSENT' THEN previous.[updated_at_nano] ELSE c.[updated_at_nano] END,CASE WHEN c.[pck_loe_ore_description_p]='ABSENT' THEN previous.[pck_loe_ore_description] ELSE c.[pck_loe_ore_description] END,CASE WHEN c.[pck_pus_ore_description_p]='ABSENT' THEN previous.[pck_pus_ore_description] ELSE c.[pck_pus_ore_description] END,CASE WHEN c.[pck_pus_ore_trigger_p]='ABSENT' THEN previous.[pck_pus_ore_trigger] ELSE c.[pck_pus_ore_trigger] END,CASE WHEN c.[pck_uer_name_p]='ABSENT' THEN previous.[pck_uer_name] ELSE c.[pck_uer_name] END,CASE WHEN c.[pck_ctr_name_p]='ABSENT' THEN previous.[pck_ctr_name] ELSE c.[pck_ctr_name] END,CASE WHEN c.[cancellation_reason_p]='ABSENT' THEN previous.[cancellation_reason] ELSE c.[cancellation_reason] END EXCEPT SELECT previous.[id],previous.[pck_crn_psn_nickname],previous.[sequence_code],previous.[created_at],previous.[created_at_nano],previous.[request_date],previous.[service_date],previous.[finish_date],previous.[pck_cor_nickname],previous.[invoices_volumes],previous.[invoices_weight],previous.[taxed_weight],previous.[invoices_value],previous.[status],previous.[pck_pds_cty_name],previous.[pck_pds_cty_sae_code],previous.[pck_pds_postal_code],previous.[pck_pds_neighborhood],previous.[pck_prn_name],previous.[pck_prn_drt_nickname],previous.[updated_at],previous.[updated_at_nano],previous.[pck_loe_ore_description],previous.[pck_pus_ore_description],previous.[pck_pus_ore_trigger],previous.[pck_uer_name],previous.[pck_ctr_name],previous.[cancellation_reason]))
 THROW 53751,N'ANA_COLLECTION_EQUAL_CLOCK_ATTRIBUTE_CONFLICT',1;
 INSERT core.analytic_collection_snapshot(run_id,source_key,source_execution,previous_snapshot,representative_stage,fresh_second,fresh_nano,terminal,
 status_code,status_label,occurrence_action,attempt_count,business_date,extracted_at,[id],[id_p],[id_w],[id_raw],[pck_crn_psn_nickname],[pck_crn_psn_nickname_p],[pck_crn_psn_nickname_w],[pck_crn_psn_nickname_raw],[sequence_code],[sequence_code_p],[sequence_code_w],[sequence_code_raw],[created_at],[created_at_p],[created_at_w],[created_at_raw],[created_at_nano],[request_date],[request_date_p],[request_date_w],[request_date_raw],[service_date],[service_date_p],[service_date_w],[service_date_raw],[finish_date],[finish_date_p],[finish_date_w],[finish_date_raw],[pck_cor_nickname],[pck_cor_nickname_p],[pck_cor_nickname_w],[pck_cor_nickname_raw],[invoices_volumes],[invoices_volumes_p],[invoices_volumes_w],[invoices_volumes_raw],[invoices_weight],[invoices_weight_p],[invoices_weight_w],[invoices_weight_raw],[taxed_weight],[taxed_weight_p],[taxed_weight_w],[taxed_weight_raw],[invoices_value],[invoices_value_p],[invoices_value_w],[invoices_value_raw],[status],[status_p],[status_w],[status_raw],[pck_pds_cty_name],[pck_pds_cty_name_p],[pck_pds_cty_name_w],[pck_pds_cty_name_raw],[pck_pds_cty_sae_code],[pck_pds_cty_sae_code_p],[pck_pds_cty_sae_code_w],[pck_pds_cty_sae_code_raw],[pck_pds_postal_code],[pck_pds_postal_code_p],[pck_pds_postal_code_w],[pck_pds_postal_code_raw],[pck_pds_neighborhood],[pck_pds_neighborhood_p],[pck_pds_neighborhood_w],[pck_pds_neighborhood_raw],[pck_prn_name],[pck_prn_name_p],[pck_prn_name_w],[pck_prn_name_raw],[pck_prn_drt_nickname],[pck_prn_drt_nickname_p],[pck_prn_drt_nickname_w],[pck_prn_drt_nickname_raw],[updated_at],[updated_at_p],[updated_at_w],[updated_at_raw],[updated_at_nano],[pck_loe_ore_description],[pck_loe_ore_description_p],[pck_loe_ore_description_w],[pck_loe_ore_description_raw],[pck_pus_ore_description],[pck_pus_ore_description_p],[pck_pus_ore_description_w],[pck_pus_ore_description_raw],[pck_pus_ore_trigger],[pck_pus_ore_trigger_p],[pck_pus_ore_trigger_w],[pck_pus_ore_trigger_raw],[pck_uer_name],[pck_uer_name_p],[pck_uer_name_w],[pck_uer_name_raw],[pck_ctr_name],[pck_ctr_name_p],[pck_ctr_name_w],[pck_ctr_name_raw],[cancellation_reason],[cancellation_reason_p],[cancellation_reason_w],[cancellation_reason_raw])
 SELECT @run_id,c.source_key,@execution_id,previous.snapshot_id,c.representative_stage,c.fresh_second,c.fresh_nano,c.terminal,
 c.status_code,c.status_label,c.occurrence_action,c.attempt_count,
 COALESCE(CASE WHEN c.request_date_p='ABSENT' THEN previous.request_date ELSE c.request_date END,capture.business_date),c.extracted_at,CASE WHEN c.[id_p]='ABSENT' AND previous.snapshot_id IS NOT NULL THEN previous.[id] ELSE c.[id] END,
CASE WHEN c.[id_p]='ABSENT' AND previous.snapshot_id IS NOT NULL THEN previous.[id_p] ELSE c.[id_p] END,
CASE WHEN c.[id_p]='ABSENT' AND previous.snapshot_id IS NOT NULL THEN previous.[id_w] ELSE c.[id_w] END,
CASE WHEN c.[id_p]='ABSENT' AND previous.snapshot_id IS NOT NULL THEN previous.[id_raw] ELSE c.[id_raw] END,
CASE WHEN c.[pck_crn_psn_nickname_p]='ABSENT' AND previous.snapshot_id IS NOT NULL THEN previous.[pck_crn_psn_nickname] ELSE c.[pck_crn_psn_nickname] END,
CASE WHEN c.[pck_crn_psn_nickname_p]='ABSENT' AND previous.snapshot_id IS NOT NULL THEN previous.[pck_crn_psn_nickname_p] ELSE c.[pck_crn_psn_nickname_p] END,
CASE WHEN c.[pck_crn_psn_nickname_p]='ABSENT' AND previous.snapshot_id IS NOT NULL THEN previous.[pck_crn_psn_nickname_w] ELSE c.[pck_crn_psn_nickname_w] END,
CASE WHEN c.[pck_crn_psn_nickname_p]='ABSENT' AND previous.snapshot_id IS NOT NULL THEN previous.[pck_crn_psn_nickname_raw] ELSE c.[pck_crn_psn_nickname_raw] END,
CASE WHEN c.[sequence_code_p]='ABSENT' AND previous.snapshot_id IS NOT NULL THEN previous.[sequence_code] ELSE c.[sequence_code] END,
CASE WHEN c.[sequence_code_p]='ABSENT' AND previous.snapshot_id IS NOT NULL THEN previous.[sequence_code_p] ELSE c.[sequence_code_p] END,
CASE WHEN c.[sequence_code_p]='ABSENT' AND previous.snapshot_id IS NOT NULL THEN previous.[sequence_code_w] ELSE c.[sequence_code_w] END,
CASE WHEN c.[sequence_code_p]='ABSENT' AND previous.snapshot_id IS NOT NULL THEN previous.[sequence_code_raw] ELSE c.[sequence_code_raw] END,
CASE WHEN c.[created_at_p]='ABSENT' AND previous.snapshot_id IS NOT NULL THEN previous.[created_at] ELSE c.[created_at] END,
CASE WHEN c.[created_at_p]='ABSENT' AND previous.snapshot_id IS NOT NULL THEN previous.[created_at_p] ELSE c.[created_at_p] END,
CASE WHEN c.[created_at_p]='ABSENT' AND previous.snapshot_id IS NOT NULL THEN previous.[created_at_w] ELSE c.[created_at_w] END,
CASE WHEN c.[created_at_p]='ABSENT' AND previous.snapshot_id IS NOT NULL THEN previous.[created_at_raw] ELSE c.[created_at_raw] END,
CASE WHEN c.[created_at_p]='ABSENT' AND previous.snapshot_id IS NOT NULL THEN previous.[created_at_nano] ELSE c.[created_at_nano] END,
CASE WHEN c.[request_date_p]='ABSENT' AND previous.snapshot_id IS NOT NULL THEN previous.[request_date] ELSE c.[request_date] END,
CASE WHEN c.[request_date_p]='ABSENT' AND previous.snapshot_id IS NOT NULL THEN previous.[request_date_p] ELSE c.[request_date_p] END,
CASE WHEN c.[request_date_p]='ABSENT' AND previous.snapshot_id IS NOT NULL THEN previous.[request_date_w] ELSE c.[request_date_w] END,
CASE WHEN c.[request_date_p]='ABSENT' AND previous.snapshot_id IS NOT NULL THEN previous.[request_date_raw] ELSE c.[request_date_raw] END,
CASE WHEN c.[service_date_p]='ABSENT' AND previous.snapshot_id IS NOT NULL THEN previous.[service_date] ELSE c.[service_date] END,
CASE WHEN c.[service_date_p]='ABSENT' AND previous.snapshot_id IS NOT NULL THEN previous.[service_date_p] ELSE c.[service_date_p] END,
CASE WHEN c.[service_date_p]='ABSENT' AND previous.snapshot_id IS NOT NULL THEN previous.[service_date_w] ELSE c.[service_date_w] END,
CASE WHEN c.[service_date_p]='ABSENT' AND previous.snapshot_id IS NOT NULL THEN previous.[service_date_raw] ELSE c.[service_date_raw] END,
CASE WHEN c.[finish_date_p]='ABSENT' AND previous.snapshot_id IS NOT NULL THEN previous.[finish_date] ELSE c.[finish_date] END,
CASE WHEN c.[finish_date_p]='ABSENT' AND previous.snapshot_id IS NOT NULL THEN previous.[finish_date_p] ELSE c.[finish_date_p] END,
CASE WHEN c.[finish_date_p]='ABSENT' AND previous.snapshot_id IS NOT NULL THEN previous.[finish_date_w] ELSE c.[finish_date_w] END,
CASE WHEN c.[finish_date_p]='ABSENT' AND previous.snapshot_id IS NOT NULL THEN previous.[finish_date_raw] ELSE c.[finish_date_raw] END,
CASE WHEN c.[pck_cor_nickname_p]='ABSENT' AND previous.snapshot_id IS NOT NULL THEN previous.[pck_cor_nickname] ELSE c.[pck_cor_nickname] END,
CASE WHEN c.[pck_cor_nickname_p]='ABSENT' AND previous.snapshot_id IS NOT NULL THEN previous.[pck_cor_nickname_p] ELSE c.[pck_cor_nickname_p] END,
CASE WHEN c.[pck_cor_nickname_p]='ABSENT' AND previous.snapshot_id IS NOT NULL THEN previous.[pck_cor_nickname_w] ELSE c.[pck_cor_nickname_w] END,
CASE WHEN c.[pck_cor_nickname_p]='ABSENT' AND previous.snapshot_id IS NOT NULL THEN previous.[pck_cor_nickname_raw] ELSE c.[pck_cor_nickname_raw] END,
CASE WHEN c.[invoices_volumes_p]='ABSENT' AND previous.snapshot_id IS NOT NULL THEN previous.[invoices_volumes] ELSE c.[invoices_volumes] END,
CASE WHEN c.[invoices_volumes_p]='ABSENT' AND previous.snapshot_id IS NOT NULL THEN previous.[invoices_volumes_p] ELSE c.[invoices_volumes_p] END,
CASE WHEN c.[invoices_volumes_p]='ABSENT' AND previous.snapshot_id IS NOT NULL THEN previous.[invoices_volumes_w] ELSE c.[invoices_volumes_w] END,
CASE WHEN c.[invoices_volumes_p]='ABSENT' AND previous.snapshot_id IS NOT NULL THEN previous.[invoices_volumes_raw] ELSE c.[invoices_volumes_raw] END,
CASE WHEN c.[invoices_weight_p]='ABSENT' AND previous.snapshot_id IS NOT NULL THEN previous.[invoices_weight] ELSE c.[invoices_weight] END,
CASE WHEN c.[invoices_weight_p]='ABSENT' AND previous.snapshot_id IS NOT NULL THEN previous.[invoices_weight_p] ELSE c.[invoices_weight_p] END,
CASE WHEN c.[invoices_weight_p]='ABSENT' AND previous.snapshot_id IS NOT NULL THEN previous.[invoices_weight_w] ELSE c.[invoices_weight_w] END,
CASE WHEN c.[invoices_weight_p]='ABSENT' AND previous.snapshot_id IS NOT NULL THEN previous.[invoices_weight_raw] ELSE c.[invoices_weight_raw] END,
CASE WHEN c.[taxed_weight_p]='ABSENT' AND previous.snapshot_id IS NOT NULL THEN previous.[taxed_weight] ELSE c.[taxed_weight] END,
CASE WHEN c.[taxed_weight_p]='ABSENT' AND previous.snapshot_id IS NOT NULL THEN previous.[taxed_weight_p] ELSE c.[taxed_weight_p] END,
CASE WHEN c.[taxed_weight_p]='ABSENT' AND previous.snapshot_id IS NOT NULL THEN previous.[taxed_weight_w] ELSE c.[taxed_weight_w] END,
CASE WHEN c.[taxed_weight_p]='ABSENT' AND previous.snapshot_id IS NOT NULL THEN previous.[taxed_weight_raw] ELSE c.[taxed_weight_raw] END,
CASE WHEN c.[invoices_value_p]='ABSENT' AND previous.snapshot_id IS NOT NULL THEN previous.[invoices_value] ELSE c.[invoices_value] END,
CASE WHEN c.[invoices_value_p]='ABSENT' AND previous.snapshot_id IS NOT NULL THEN previous.[invoices_value_p] ELSE c.[invoices_value_p] END,
CASE WHEN c.[invoices_value_p]='ABSENT' AND previous.snapshot_id IS NOT NULL THEN previous.[invoices_value_w] ELSE c.[invoices_value_w] END,
CASE WHEN c.[invoices_value_p]='ABSENT' AND previous.snapshot_id IS NOT NULL THEN previous.[invoices_value_raw] ELSE c.[invoices_value_raw] END,
CASE WHEN c.[status_p]='ABSENT' AND previous.snapshot_id IS NOT NULL THEN previous.[status] ELSE c.[status] END,
CASE WHEN c.[status_p]='ABSENT' AND previous.snapshot_id IS NOT NULL THEN previous.[status_p] ELSE c.[status_p] END,
CASE WHEN c.[status_p]='ABSENT' AND previous.snapshot_id IS NOT NULL THEN previous.[status_w] ELSE c.[status_w] END,
CASE WHEN c.[status_p]='ABSENT' AND previous.snapshot_id IS NOT NULL THEN previous.[status_raw] ELSE c.[status_raw] END,
CASE WHEN c.[pck_pds_cty_name_p]='ABSENT' AND previous.snapshot_id IS NOT NULL THEN previous.[pck_pds_cty_name] ELSE c.[pck_pds_cty_name] END,
CASE WHEN c.[pck_pds_cty_name_p]='ABSENT' AND previous.snapshot_id IS NOT NULL THEN previous.[pck_pds_cty_name_p] ELSE c.[pck_pds_cty_name_p] END,
CASE WHEN c.[pck_pds_cty_name_p]='ABSENT' AND previous.snapshot_id IS NOT NULL THEN previous.[pck_pds_cty_name_w] ELSE c.[pck_pds_cty_name_w] END,
CASE WHEN c.[pck_pds_cty_name_p]='ABSENT' AND previous.snapshot_id IS NOT NULL THEN previous.[pck_pds_cty_name_raw] ELSE c.[pck_pds_cty_name_raw] END,
CASE WHEN c.[pck_pds_cty_sae_code_p]='ABSENT' AND previous.snapshot_id IS NOT NULL THEN previous.[pck_pds_cty_sae_code] ELSE c.[pck_pds_cty_sae_code] END,
CASE WHEN c.[pck_pds_cty_sae_code_p]='ABSENT' AND previous.snapshot_id IS NOT NULL THEN previous.[pck_pds_cty_sae_code_p] ELSE c.[pck_pds_cty_sae_code_p] END,
CASE WHEN c.[pck_pds_cty_sae_code_p]='ABSENT' AND previous.snapshot_id IS NOT NULL THEN previous.[pck_pds_cty_sae_code_w] ELSE c.[pck_pds_cty_sae_code_w] END,
CASE WHEN c.[pck_pds_cty_sae_code_p]='ABSENT' AND previous.snapshot_id IS NOT NULL THEN previous.[pck_pds_cty_sae_code_raw] ELSE c.[pck_pds_cty_sae_code_raw] END,
CASE WHEN c.[pck_pds_postal_code_p]='ABSENT' AND previous.snapshot_id IS NOT NULL THEN previous.[pck_pds_postal_code] ELSE c.[pck_pds_postal_code] END,
CASE WHEN c.[pck_pds_postal_code_p]='ABSENT' AND previous.snapshot_id IS NOT NULL THEN previous.[pck_pds_postal_code_p] ELSE c.[pck_pds_postal_code_p] END,
CASE WHEN c.[pck_pds_postal_code_p]='ABSENT' AND previous.snapshot_id IS NOT NULL THEN previous.[pck_pds_postal_code_w] ELSE c.[pck_pds_postal_code_w] END,
CASE WHEN c.[pck_pds_postal_code_p]='ABSENT' AND previous.snapshot_id IS NOT NULL THEN previous.[pck_pds_postal_code_raw] ELSE c.[pck_pds_postal_code_raw] END,
CASE WHEN c.[pck_pds_neighborhood_p]='ABSENT' AND previous.snapshot_id IS NOT NULL THEN previous.[pck_pds_neighborhood] ELSE c.[pck_pds_neighborhood] END,
CASE WHEN c.[pck_pds_neighborhood_p]='ABSENT' AND previous.snapshot_id IS NOT NULL THEN previous.[pck_pds_neighborhood_p] ELSE c.[pck_pds_neighborhood_p] END,
CASE WHEN c.[pck_pds_neighborhood_p]='ABSENT' AND previous.snapshot_id IS NOT NULL THEN previous.[pck_pds_neighborhood_w] ELSE c.[pck_pds_neighborhood_w] END,
CASE WHEN c.[pck_pds_neighborhood_p]='ABSENT' AND previous.snapshot_id IS NOT NULL THEN previous.[pck_pds_neighborhood_raw] ELSE c.[pck_pds_neighborhood_raw] END,
CASE WHEN c.[pck_prn_name_p]='ABSENT' AND previous.snapshot_id IS NOT NULL THEN previous.[pck_prn_name] ELSE c.[pck_prn_name] END,
CASE WHEN c.[pck_prn_name_p]='ABSENT' AND previous.snapshot_id IS NOT NULL THEN previous.[pck_prn_name_p] ELSE c.[pck_prn_name_p] END,
CASE WHEN c.[pck_prn_name_p]='ABSENT' AND previous.snapshot_id IS NOT NULL THEN previous.[pck_prn_name_w] ELSE c.[pck_prn_name_w] END,
CASE WHEN c.[pck_prn_name_p]='ABSENT' AND previous.snapshot_id IS NOT NULL THEN previous.[pck_prn_name_raw] ELSE c.[pck_prn_name_raw] END,
CASE WHEN c.[pck_prn_drt_nickname_p]='ABSENT' AND previous.snapshot_id IS NOT NULL THEN previous.[pck_prn_drt_nickname] ELSE c.[pck_prn_drt_nickname] END,
CASE WHEN c.[pck_prn_drt_nickname_p]='ABSENT' AND previous.snapshot_id IS NOT NULL THEN previous.[pck_prn_drt_nickname_p] ELSE c.[pck_prn_drt_nickname_p] END,
CASE WHEN c.[pck_prn_drt_nickname_p]='ABSENT' AND previous.snapshot_id IS NOT NULL THEN previous.[pck_prn_drt_nickname_w] ELSE c.[pck_prn_drt_nickname_w] END,
CASE WHEN c.[pck_prn_drt_nickname_p]='ABSENT' AND previous.snapshot_id IS NOT NULL THEN previous.[pck_prn_drt_nickname_raw] ELSE c.[pck_prn_drt_nickname_raw] END,
CASE WHEN c.[updated_at_p]='ABSENT' AND previous.snapshot_id IS NOT NULL THEN previous.[updated_at] ELSE c.[updated_at] END,
CASE WHEN c.[updated_at_p]='ABSENT' AND previous.snapshot_id IS NOT NULL THEN previous.[updated_at_p] ELSE c.[updated_at_p] END,
CASE WHEN c.[updated_at_p]='ABSENT' AND previous.snapshot_id IS NOT NULL THEN previous.[updated_at_w] ELSE c.[updated_at_w] END,
CASE WHEN c.[updated_at_p]='ABSENT' AND previous.snapshot_id IS NOT NULL THEN previous.[updated_at_raw] ELSE c.[updated_at_raw] END,
CASE WHEN c.[updated_at_p]='ABSENT' AND previous.snapshot_id IS NOT NULL THEN previous.[updated_at_nano] ELSE c.[updated_at_nano] END,
CASE WHEN c.[pck_loe_ore_description_p]='ABSENT' AND previous.snapshot_id IS NOT NULL THEN previous.[pck_loe_ore_description] ELSE c.[pck_loe_ore_description] END,
CASE WHEN c.[pck_loe_ore_description_p]='ABSENT' AND previous.snapshot_id IS NOT NULL THEN previous.[pck_loe_ore_description_p] ELSE c.[pck_loe_ore_description_p] END,
CASE WHEN c.[pck_loe_ore_description_p]='ABSENT' AND previous.snapshot_id IS NOT NULL THEN previous.[pck_loe_ore_description_w] ELSE c.[pck_loe_ore_description_w] END,
CASE WHEN c.[pck_loe_ore_description_p]='ABSENT' AND previous.snapshot_id IS NOT NULL THEN previous.[pck_loe_ore_description_raw] ELSE c.[pck_loe_ore_description_raw] END,
CASE WHEN c.[pck_pus_ore_description_p]='ABSENT' AND previous.snapshot_id IS NOT NULL THEN previous.[pck_pus_ore_description] ELSE c.[pck_pus_ore_description] END,
CASE WHEN c.[pck_pus_ore_description_p]='ABSENT' AND previous.snapshot_id IS NOT NULL THEN previous.[pck_pus_ore_description_p] ELSE c.[pck_pus_ore_description_p] END,
CASE WHEN c.[pck_pus_ore_description_p]='ABSENT' AND previous.snapshot_id IS NOT NULL THEN previous.[pck_pus_ore_description_w] ELSE c.[pck_pus_ore_description_w] END,
CASE WHEN c.[pck_pus_ore_description_p]='ABSENT' AND previous.snapshot_id IS NOT NULL THEN previous.[pck_pus_ore_description_raw] ELSE c.[pck_pus_ore_description_raw] END,
CASE WHEN c.[pck_pus_ore_trigger_p]='ABSENT' AND previous.snapshot_id IS NOT NULL THEN previous.[pck_pus_ore_trigger] ELSE c.[pck_pus_ore_trigger] END,
CASE WHEN c.[pck_pus_ore_trigger_p]='ABSENT' AND previous.snapshot_id IS NOT NULL THEN previous.[pck_pus_ore_trigger_p] ELSE c.[pck_pus_ore_trigger_p] END,
CASE WHEN c.[pck_pus_ore_trigger_p]='ABSENT' AND previous.snapshot_id IS NOT NULL THEN previous.[pck_pus_ore_trigger_w] ELSE c.[pck_pus_ore_trigger_w] END,
CASE WHEN c.[pck_pus_ore_trigger_p]='ABSENT' AND previous.snapshot_id IS NOT NULL THEN previous.[pck_pus_ore_trigger_raw] ELSE c.[pck_pus_ore_trigger_raw] END,
CASE WHEN c.[pck_uer_name_p]='ABSENT' AND previous.snapshot_id IS NOT NULL THEN previous.[pck_uer_name] ELSE c.[pck_uer_name] END,
CASE WHEN c.[pck_uer_name_p]='ABSENT' AND previous.snapshot_id IS NOT NULL THEN previous.[pck_uer_name_p] ELSE c.[pck_uer_name_p] END,
CASE WHEN c.[pck_uer_name_p]='ABSENT' AND previous.snapshot_id IS NOT NULL THEN previous.[pck_uer_name_w] ELSE c.[pck_uer_name_w] END,
CASE WHEN c.[pck_uer_name_p]='ABSENT' AND previous.snapshot_id IS NOT NULL THEN previous.[pck_uer_name_raw] ELSE c.[pck_uer_name_raw] END,
CASE WHEN c.[pck_ctr_name_p]='ABSENT' AND previous.snapshot_id IS NOT NULL THEN previous.[pck_ctr_name] ELSE c.[pck_ctr_name] END,
CASE WHEN c.[pck_ctr_name_p]='ABSENT' AND previous.snapshot_id IS NOT NULL THEN previous.[pck_ctr_name_p] ELSE c.[pck_ctr_name_p] END,
CASE WHEN c.[pck_ctr_name_p]='ABSENT' AND previous.snapshot_id IS NOT NULL THEN previous.[pck_ctr_name_w] ELSE c.[pck_ctr_name_w] END,
CASE WHEN c.[pck_ctr_name_p]='ABSENT' AND previous.snapshot_id IS NOT NULL THEN previous.[pck_ctr_name_raw] ELSE c.[pck_ctr_name_raw] END,
CASE WHEN c.[cancellation_reason_p]='ABSENT' AND previous.snapshot_id IS NOT NULL THEN previous.[cancellation_reason] ELSE c.[cancellation_reason] END,
CASE WHEN c.[cancellation_reason_p]='ABSENT' AND previous.snapshot_id IS NOT NULL THEN previous.[cancellation_reason_p] ELSE c.[cancellation_reason_p] END,
CASE WHEN c.[cancellation_reason_p]='ABSENT' AND previous.snapshot_id IS NOT NULL THEN previous.[cancellation_reason_w] ELSE c.[cancellation_reason_w] END,
CASE WHEN c.[cancellation_reason_p]='ABSENT' AND previous.snapshot_id IS NOT NULL THEN previous.[cancellation_reason_raw] ELSE c.[cancellation_reason_raw] END
 FROM #candidate c JOIN core.relational_lab_root actual ON actual.run_id=@rel AND actual.entity_name=N'coletas' AND actual.source_key=c.source_key AND actual.execution_id=@execution_id
 JOIN ctl.relational_lab_capture capture ON capture.execution_id=@execution_id
 LEFT JOIN core.analytic_collection_current pointer ON pointer.run_id=@run_id AND pointer.source_key=c.source_key
 LEFT JOIN core.analytic_collection_snapshot previous ON previous.snapshot_id=pointer.snapshot_id;
 DECLARE @updates BIGINT=@@ROWCOUNT;
 INSERT core.analytic_collection_lineage SELECT s.snapshot_id,o.stage_record_id FROM core.analytic_collection_snapshot s JOIN #cohort o ON o.source_key=s.source_key
 WHERE s.run_id=@run_id AND s.source_execution=@execution_id;
 UPDATE p SET snapshot_id=s.snapshot_id,business_date=s.business_date FROM core.analytic_collection_current p
 JOIN core.analytic_collection_snapshot s ON s.run_id=p.run_id AND s.source_key=p.source_key WHERE s.source_execution=@execution_id AND s.run_id=@run_id;
 INSERT core.analytic_collection_current SELECT s.run_id,s.source_key,s.snapshot_id,@execution_id,s.business_date,s.extracted_at FROM core.analytic_collection_snapshot s
 WHERE s.run_id=@run_id AND s.source_execution=@execution_id AND NOT EXISTS(SELECT 1 FROM core.analytic_collection_current p WHERE p.run_id=s.run_id AND p.source_key=s.source_key);
 UPDATE p SET last_observation=@execution_id,latest_extracted_at=CASE WHEN c.extracted_at>p.latest_extracted_at THEN c.extracted_at ELSE p.latest_extracted_at END
 FROM core.analytic_collection_current p JOIN #candidate c ON c.source_key=p.source_key WHERE p.run_id=@run_id;
 INSERT ctl.analytic_collection_preparation SELECT @execution_id,@run_id,@fingerprint,@rows,COUNT_BIG(*),@updates,COUNT_BIG(*)-@updates,@now FROM #candidate;
 SELECT physical_rows,roots,updates,noops FROM ctl.analytic_collection_preparation WHERE execution_id=@execution_id;
END;
GO
