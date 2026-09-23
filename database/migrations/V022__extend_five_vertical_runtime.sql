-- B55 additive technical evidence. No principal, grant, reference content or production output.
SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;
SET XACT_ABORT ON;
GO
CREATE TABLE ctl.runtime_cotacao_reference (
 execution_id UNIQUEIDENTIFIER NOT NULL CONSTRAINT PK_runtime_cotacao_reference PRIMARY KEY,
 reference_release_id BIGINT NOT NULL,
 reference_fingerprint CHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL,
 bound_at_utc DATETIME2(3) NOT NULL,
 CONSTRAINT FK_runtime_cotacao_reference_attempt FOREIGN KEY(execution_id) REFERENCES ctl.execution_attempt(execution_id),
 CONSTRAINT FK_runtime_cotacao_reference_release FOREIGN KEY(reference_release_id) REFERENCES ref.reference_release(reference_release_id)
);
GO
CREATE TABLE recon.runtime_vertical_output (
 output_id BIGINT IDENTITY NOT NULL CONSTRAINT PK_runtime_vertical_output PRIMARY KEY,
 execution_id UNIQUEIDENTIFIER NOT NULL,
 entity_name NVARCHAR(128) COLLATE Latin1_General_100_BIN2 NOT NULL,
 row_kind VARCHAR(8) COLLATE Latin1_General_100_BIN2 NOT NULL,
 source_key NVARCHAR(256) COLLATE Latin1_General_100_BIN2 NOT NULL,
 child_key NVARCHAR(256) COLLATE Latin1_General_100_BIN2 NOT NULL,
 projection_json NVARCHAR(MAX) NOT NULL,
 presence_json NVARCHAR(MAX) NOT NULL,
 reference_release_id BIGINT NULL,
 captured_at_utc DATETIME2(3) NOT NULL,
 CONSTRAINT UQ_runtime_vertical_output UNIQUE NONCLUSTERED(execution_id,row_kind,source_key,child_key),
 CONSTRAINT FK_runtime_vertical_output_publication FOREIGN KEY(execution_id) REFERENCES ctl.execution_attempt(execution_id),
 CONSTRAINT CK_runtime_vertical_output_kind CHECK(row_kind IN('ROOT','PICK','MDFE')),
 CONSTRAINT CK_runtime_vertical_output_json CHECK(ISJSON(projection_json)=1 AND ISJSON(presence_json)=1)
);
GO
ALTER TABLE core.manifesto_pick ADD freshness_at_utc DATETIME2(3) NULL;
ALTER TABLE core.manifesto_mdfe ADD freshness_at_utc DATETIME2(3) NULL;
ALTER TABLE stg.manifesto_pick_candidate ADD freshness_at_utc DATETIME2(3) NULL;
ALTER TABLE stg.manifesto_mdfe_candidate ADD freshness_at_utc DATETIME2(3) NULL;
GO
CREATE TRIGGER ctl.trg_runtime_cotacao_reference_immutable ON ctl.runtime_cotacao_reference INSTEAD OF UPDATE,DELETE AS
BEGIN THROW 52801,N'RUNTIME_REFERENCE_IMMUTABLE',1; END;
GO
CREATE TRIGGER recon.trg_runtime_vertical_output_immutable ON recon.runtime_vertical_output INSTEAD OF UPDATE,DELETE AS
BEGIN THROW 52802,N'RUNTIME_OUTPUT_IMMUTABLE',1; END;
GO
CREATE OR ALTER FUNCTION ctl.fn_runtime_tariff_valid(@execution_id UNIQUEIDENTIFIER,@reference_release_id BIGINT)
RETURNS BIT AS
BEGIN
 IF @reference_release_id IS NULL OR @reference_release_id<1 RETURN 0;
 IF NOT EXISTS(SELECT 1 FROM ref.reference_release r
   JOIN ref.reference_release_ratification rat ON rat.reference_release_id=r.reference_release_id AND rat.activation_scope=N'SHADOW'
   WHERE r.reference_release_id=@reference_release_id AND r.family_code=N'QUOTE_TARIFF'
   AND r.scope_code=N'LOCAL_SHADOW/LOCAL_V2/LOCAL_V2/cotacoes'
   AND NOT EXISTS(SELECT 1 FROM ref.reference_release_revocation rev
     WHERE rev.reference_release_id=r.reference_release_id AND rev.activation_scope=N'SHADOW')) RETURN 0;
 IF EXISTS(SELECT 1 FROM ctl.runtime_cotacao_reference b JOIN ref.reference_release r ON r.reference_release_id=b.reference_release_id
   WHERE b.execution_id=@execution_id AND (b.reference_release_id<>@reference_release_id OR b.reference_fingerprint<>r.source_fingerprint)) RETURN 0;
 IF IS_SRVROLEMEMBER(N'sysadmin')=1 OR IS_MEMBER(N'db_owner')=1 RETURN 1;
 DECLARE @actual NVARCHAR(MAX)=CONCAT(N'{"execution":"',LOWER(CONVERT(NVARCHAR(36),@execution_id)),
   N'","entity":"cotacoes","workload":"cotacoes","mode":"BACKFILL","referenceReleaseId":"',
   CONVERT(NVARCHAR(20),@reference_release_id),N'"}');
 RETURN ctl.fn_runtime_consumed_scope(@actual,0,NULL);
END;
GO

CREATE OR ALTER PROCEDURE recon.usp_capture_runtime_vertical_output @execution_id UNIQUEIDENTIFIER
AS
BEGIN
 SET NOCOUNT ON;
 IF @@TRANCOUNT=0 OR NOT EXISTS(SELECT 1 FROM ctl.execution_publication_event WHERE execution_id=@execution_id)
   THROW 52830,N'OUTPUT_REQUIRES_PUBLICATION_TRANSACTION',1;
 IF EXISTS(SELECT 1 FROM recon.runtime_vertical_output WHERE execution_id=@execution_id) RETURN;
 DECLARE @entity NVARCHAR(128),@now DATETIME2(3)=SYSUTCDATETIME();
 SELECT @entity=p.entity_name FROM ctl.execution_attempt a JOIN ctl.execution_partition p ON p.partition_id=a.partition_id
 WHERE a.execution_id=@execution_id AND p.environment_name=N'LOCAL_SHADOW' AND p.source_instance=N'LOCAL_V2' AND p.tenant_scope=N'LOCAL_V2';
 IF @entity IS NULL RETURN;
 DECLARE @actual NVARCHAR(MAX)=(SELECT LOWER(CONVERT(NVARCHAR(36),@execution_id)) AS [execution],@entity AS [entity] FOR JSON PATH,WITHOUT_ARRAY_WRAPPER);
 IF ctl.fn_runtime_consumed_scope(@actual,1,NULL)<>1 THROW 52831,N'OUTPUT_CONSUMPTION_REQUIRED',1;
 IF @entity=N'coletas'
 INSERT recon.runtime_vertical_output(execution_id,entity_name,row_kind,source_key,child_key,projection_json,presence_json,captured_at_utc)
 SELECT @execution_id,@entity,'ROOT',c.source_key,N'',c.payload_json,c.field_presence_json,@now
 FROM recon.execution_candidate_application a JOIN core.coleta c ON c.record_state_id=a.record_state_id WHERE a.execution_id=@execution_id;
 ELSE IF @entity=N'fretes'
 INSERT recon.runtime_vertical_output(execution_id,entity_name,row_kind,source_key,child_key,projection_json,presence_json,captured_at_utc)
 SELECT @execution_id,@entity,'ROOT',c.source_key,N'',c.payload_json,c.field_presence_json,@now
 FROM recon.execution_candidate_application a JOIN core.frete c ON c.record_state_id=a.record_state_id WHERE a.execution_id=@execution_id;
 ELSE IF @entity=N'cotacoes'
 INSERT recon.runtime_vertical_output(execution_id,entity_name,row_kind,source_key,child_key,projection_json,presence_json,reference_release_id,captured_at_utc)
 SELECT @execution_id,@entity,'ROOT',c.source_key,N'',c.payload_json,c.field_presence_json,c.tariff_reference_release_id,@now
 FROM recon.execution_candidate_application a JOIN core.cotacao c ON c.record_state_id=a.record_state_id WHERE a.execution_id=@execution_id;
 ELSE IF @entity=N'localizacao_cargas'
 INSERT recon.runtime_vertical_output(execution_id,entity_name,row_kind,source_key,child_key,projection_json,presence_json,captured_at_utc)
 SELECT @execution_id,@entity,'ROOT',c.source_key,N'',c.payload_json,c.field_presence_json,@now
 FROM recon.execution_candidate_application a JOIN core.localizacao_carga c ON c.record_state_id=a.record_state_id WHERE a.execution_id=@execution_id;
 ELSE IF @entity=N'manifestos'
 BEGIN
 INSERT recon.runtime_vertical_output(execution_id,entity_name,row_kind,source_key,child_key,projection_json,presence_json,captured_at_utc)
 SELECT @execution_id,@entity,'ROOT',c.source_key,N'',
  (SELECT JSON_QUERY(c.root_payload_json) AS root,JSON_QUERY(c.metric_values_json) AS metrics,JSON_QUERY(c.competence_json) AS competence
   FOR JSON PATH,WITHOUT_ARRAY_WRAPPER),c.root_presence_json,@now
 FROM recon.execution_candidate_application a JOIN core.manifesto c ON c.record_state_id=a.record_state_id WHERE a.execution_id=@execution_id;
 INSERT recon.runtime_vertical_output(execution_id,entity_name,row_kind,source_key,child_key,projection_json,presence_json,captured_at_utc)
 SELECT @execution_id,@entity,'PICK',c.source_key,p.pick_source_key,N'{"presence":"VALUE","relation":"UNRESOLVED_V2_046A"}',N'{"key":"VALUE"}',@now
 FROM recon.execution_candidate_application a JOIN core.manifesto c ON c.record_state_id=a.record_state_id
 JOIN core.manifesto_pick p ON p.manifesto_id=c.manifesto_id WHERE a.execution_id=@execution_id;
 INSERT recon.runtime_vertical_output(execution_id,entity_name,row_kind,source_key,child_key,projection_json,presence_json,captured_at_utc)
 SELECT @execution_id,@entity,'MDFE',c.source_key,p.mdfe_key,
   (SELECT p.mdfe_number AS number FOR JSON PATH,WITHOUT_ARRAY_WRAPPER),N'{"key":"VALUE","number":"VALUE"}',@now
 FROM recon.execution_candidate_application a JOIN core.manifesto c ON c.record_state_id=a.record_state_id
 JOIN core.manifesto_mdfe p ON p.manifesto_id=c.manifesto_id WHERE a.execution_id=@execution_id;
 END;
 IF (SELECT COUNT_BIG(*) FROM recon.runtime_vertical_output WHERE execution_id=@execution_id)>512
   THROW 52832,N'OUTPUT_DERIVED_LIMIT',1;
END;
GO

CREATE OR ALTER PROCEDURE core.usp_prepare_manifesto_candidate_set
 @execution_id UNIQUEIDENTIFIER,@contract_version NVARCHAR(MAX),@contract_fingerprint NVARCHAR(MAX),
 @configuration_version NVARCHAR(MAX),@configuration_fingerprint NVARCHAR(MAX)
AS
BEGIN
 SET NOCOUNT ON; SET XACT_ABORT ON;
 DECLARE @actual NVARCHAR(MAX)=(SELECT LOWER(CONVERT(NVARCHAR(36),@execution_id)) AS [execution],N'manifestos' AS [entity],
   @contract_version AS [contractVersion],@contract_fingerprint AS [contractHash],
   @configuration_version AS [configurationVersion],@configuration_fingerprint AS [configurationHash]
   FOR JSON PATH,WITHOUT_ARRAY_WRAPPER);
 IF ctl.fn_runtime_consumed_scope(@actual,1,NULL)<>1 THROW 52810,N'MANIFESTO_CONSUMER_FENCE',1;
 BEGIN TRY
 BEGIN TRANSACTION;
 DECLARE @now DATETIME2(3)=SYSUTCDATETIME(),@state NVARCHAR(32);
 SELECT @state=a.current_state FROM ctl.execution_attempt a WITH(UPDLOCK,HOLDLOCK)
 JOIN ctl.execution_partition p WITH(UPDLOCK,HOLDLOCK) ON p.partition_id=a.partition_id
 WHERE a.execution_id=@execution_id AND p.entity_name=N'manifestos'
   AND a.contract_version=@contract_version AND a.contract_fingerprint=@contract_fingerprint
   AND a.configuration_version=@configuration_version AND a.configuration_fingerprint=@configuration_fingerprint;
 IF @state=N'PROMOTED' AND EXISTS(SELECT 1 FROM ctl.manifesto_promotion_result WHERE execution_id=@execution_id AND validation_state=N'PASSED')
 BEGIN
   EXEC core.usp_prepare_staged_execution @execution_id,@contract_version,@contract_fingerprint,@configuration_version,@configuration_fingerprint;
   COMMIT TRANSACTION; RETURN;
 END;
 IF @state IS NULL OR @state<>N'STAGED' OR NOT EXISTS(SELECT 1 FROM ctl.execution_lease l WITH(UPDLOCK,HOLDLOCK)
   JOIN ctl.execution_partition p ON p.partition_id=l.partition_id AND p.current_execution_id=l.execution_id
   WHERE l.execution_id=@execution_id AND l.released_at_utc IS NULL AND l.expires_at_utc>@now)
   THROW 52811,N'MANIFESTO_STAGE_LEASE_REQUIRED',1;
 IF NOT EXISTS(SELECT 1 FROM ctl.runtime_contract_evidence WHERE execution_id=@execution_id)
   THROW 52812,N'MANIFESTO_TRAVERSAL_SEAL_REQUIRED',1;
 IF EXISTS(SELECT 1 FROM stg.manifesto_reduced_candidate WHERE execution_id=@execution_id)
   OR EXISTS(SELECT 1 FROM stg.execution_record WHERE execution_id=@execution_id)
   THROW 52813,N'CALLER_MANIFESTO_CANDIDATE_REJECTED',1;
 IF (SELECT COUNT_BIG(*) FROM stg.manifesto_observation WITH(HOLDLOCK) WHERE execution_id=@execution_id) NOT BETWEEN 1 AND 16
   THROW 52814,N'MANIFESTO_LABORATORY_OBSERVATION_LIMIT',1;
 IF EXISTS(SELECT 1 FROM stg.manifesto_observation WHERE execution_id=@execution_id AND validation_disposition<>N'VALID')
   THROW 52815,N'MANIFESTO_OBSERVATION_QUARANTINE',1;

 -- Independent cohorts are selected in SQL across every page of this occurrence.
 SELECT o.*,DENSE_RANK() OVER(PARTITION BY o.source_key ORDER BY o.freshness_at_utc DESC) AS cohort_rank
 INTO #observations FROM stg.manifesto_observation o WITH(HOLDLOCK) WHERE execution_id=@execution_id;
 SELECT * INTO #cohort FROM #observations WHERE cohort_rank=1;
 CREATE INDEX IX_cohort_root ON #cohort(source_key);
 SELECT c.source_key,j.[key] COLLATE Latin1_General_100_BIN2 AS field_name,j.[type] AS wire_type,
   j.[value] COLLATE Latin1_General_100_BIN2 AS value
 INTO #fields FROM #cohort c CROSS APPLY OPENJSON(c.root_fields_json) j;
 IF EXISTS(SELECT 1 FROM #fields GROUP BY source_key,field_name
   HAVING (MIN(wire_type)=0 AND MAX(wire_type)<>0)
      OR (field_name<>N'status' AND COUNT(DISTINCT CONCAT(wire_type,N':',value))>1)
      OR (field_name=N'status' AND COUNT(DISTINCT value)>1
          AND MIN(CASE WHEN value IN(N'closed',N'in_transit',N'pending') THEN 1 ELSE 0 END)=0))
   THROW 52816,N'EQUAL_FRESHNESS_CONFLICT',1;
 SELECT source_key,field_name,MIN(wire_type) AS wire_type,
   CASE WHEN field_name=N'status' AND MIN(CASE WHEN value IN(N'closed',N'in_transit',N'pending') THEN 1 ELSE 0 END)=1
    THEN CASE MAX(CASE value WHEN N'closed' THEN 3 WHEN N'in_transit' THEN 2 ELSE 1 END)
     WHEN 3 THEN N'closed' WHEN 2 THEN N'in_transit' ELSE N'pending' END ELSE MIN(value) END AS value
 INTO #reduced_fields FROM #fields GROUP BY source_key,field_name;
 -- The approved root dictionary preserves ABSENT independently from an explicit JSON null.
 SELECT r.source_key,n.field_name,f.wire_type,f.value INTO #root_presence
 FROM (SELECT DISTINCT source_key FROM #cohort) r
 CROSS JOIN (VALUES(N'sequence_code'),(N'status'),(N'mdfe_status'),
 (N'mft_ape_name'),(N'mft_man_name'),(N'mft_vie_license_plate'),(N'mft_vie_vee_name'),
 (N'mft_vie_onr_name'),(N'mft_mdr_iil_name'),(N'mft_crn_psn_nickname'),(N'mft_cat_cot_number'),
 (N'contract_type'),(N'mft_mdr_contract_type'),(N'calculation_type'),(N'cargo_type'),
 (N'mft_uer_name'),(N'mft_aoe_rer_name'),(N'mft_aoe_comments'),(N'mft_cat_cot_status'),
 (N'mft_iks_id'),(N'mft_s_n_sequence_code'),(N'mft_tl1_license_plate'),(N'mft_tl2_license_plate'),
 (N'operational_comments'),(N'closing_comments'),(N'mft_s_n_svs_sge_pyr_nickname'),
 (N'mft_s_n_svs_sge_sse_name')) n(field_name)
 LEFT JOIN #reduced_fields f ON f.source_key=r.source_key AND f.field_name=n.field_name;
 SELECT c.source_key,j.[key] AS metric_name,JSON_VALUE(j.value,'$.presence') AS presence,
   JSON_VALUE(j.value,'$.value') AS value INTO #metrics
 FROM #cohort c CROSS APPLY OPENJSON(c.metric_values_json) j;
 IF EXISTS(SELECT 1 FROM #metrics WHERE presence=N'VALUE' GROUP BY source_key,metric_name HAVING COUNT(DISTINCT value)>1)
   THROW 52817,N'METRIC_VALUE_CONFLICT',1;
 IF EXISTS(SELECT 1 FROM #cohort GROUP BY source_key HAVING COUNT(DISTINCT competence_json)>1)
   THROW 52818,N'COMPETENCE_FRESHNESS_CONFLICT',1;
 SELECT source_key,metric_name,CASE MAX(CASE presence WHEN N'VALUE' THEN 3 WHEN N'NULL' THEN 2 ELSE 1 END)
   WHEN 3 THEN N'VALUE' WHEN 2 THEN N'NULL' ELSE N'ABSENT' END AS presence,MIN(value) AS value
 INTO #reduced_metrics FROM #metrics GROUP BY source_key,metric_name;
 SELECT source_key,MAX(freshness_at_utc) AS freshness_at_utc,
   CASE MIN(CASE freshness_origin WHEN N'FINISHED_AT' THEN 1 WHEN N'CLOSED_AT' THEN 2 WHEN N'DEPARTURED_AT' THEN 3 ELSE 4 END)
   WHEN 1 THEN N'FINISHED_AT' WHEN 2 THEN N'CLOSED_AT' WHEN 3 THEN N'DEPARTURED_AT' ELSE N'CREATED_AT' END AS freshness_origin,
   MIN(competence_json) AS competence_json INTO #roots FROM #cohort GROUP BY source_key;
 SELECT r.*,CONVERT(INT,ROW_NUMBER() OVER(ORDER BY r.source_key)) AS ordinal,
   COALESCE((SELECT N'{'+STRING_AGG(CONVERT(NVARCHAR(MAX),N'"'+STRING_ESCAPE(f.field_name,'json')+N'":'+
     CASE f.wire_type WHEN 0 THEN N'null' WHEN 1 THEN N'"'+STRING_ESCAPE(f.value,'json')+N'"' ELSE f.value END) COLLATE Latin1_General_100_BIN2,N',' COLLATE Latin1_General_100_BIN2)
     WITHIN GROUP(ORDER BY f.field_name)+N'}' FROM #reduced_fields f WHERE f.source_key=r.source_key),N'{}') AS root_json,
   COALESCE((SELECT N'{'+STRING_AGG(CONVERT(NVARCHAR(MAX),N'"'+STRING_ESCAPE(f.field_name,'json')+N'":"'+
     CASE WHEN f.wire_type IS NULL THEN N'ABSENT' WHEN f.wire_type=0 THEN N'NULL' ELSE N'VALUE' END+N'"') COLLATE Latin1_General_100_BIN2,N',' COLLATE Latin1_General_100_BIN2) WITHIN GROUP(ORDER BY f.field_name)+N'}'
     FROM #root_presence f WHERE f.source_key=r.source_key),N'{}') AS presence_json,
   (SELECT N'{'+STRING_AGG(CONVERT(NVARCHAR(MAX),N'"'+m.metric_name+N'":{"presence":"'+m.presence+N'","value":'+
       CASE WHEN m.value IS NULL THEN N'null' ELSE N'"'+m.value+N'"' END+N'}') COLLATE Latin1_General_100_BIN2,N',' COLLATE Latin1_General_100_BIN2) WITHIN GROUP(ORDER BY m.metric_name)+N'}'
       FROM #reduced_metrics m WHERE m.source_key=r.source_key) AS metrics_json,
   COALESCE((SELECT CASE wire_type WHEN 0 THEN N'NULL' ELSE N'VALUE' END FROM #reduced_fields WHERE source_key=r.source_key AND field_name=N'mdfe_status'),N'ABSENT') AS mdfe_presence,
   (SELECT value FROM #reduced_fields WHERE source_key=r.source_key AND field_name=N'mdfe_status') AS mdfe_status
 INTO #reduced FROM #roots r;
 ALTER TABLE #reduced ADD attribute_hash CHAR(64),presence_hash CHAR(64);
 UPDATE #reduced SET attribute_hash=LOWER(CONVERT(CHAR(64),HASHBYTES('SHA2_256',CONCAT(N'manifestos-root-v1|',root_json,N'|',metrics_json,N'|',competence_json,N'|',mdfe_presence,N'|',COALESCE(mdfe_status,N'<null>'))),2)),
   presence_hash=LOWER(CONVERT(CHAR(64),HASHBYTES('SHA2_256',CONCAT(N'manifestos-presence-v1|',presence_json,N'|',mdfe_presence)),2));
 -- Only this SQL consumer creates generic reduced rows; the runtime cannot supply candidates.
 INSERT stg.execution_record(execution_id,input_batch_number,input_record_ordinal,source_key,row_fingerprint_version,
   source_row_hash,presence_fingerprint_version,presence_fingerprint,source_freshness_at_utc,validation_disposition,staged_at_utc)
 SELECT @execution_id,1,ordinal,source_key,N'manifestos-root-v1',attribute_hash,N'manifestos-presence-v1',presence_hash,
   freshness_at_utc,N'VALID',@now FROM #reduced;
 INSERT stg.manifesto_reduced_candidate(execution_id,source_key,stage_record_id,root_payload_json,root_presence_json,
   metric_values_json,competence_json,mdfe_status_presence,mdfe_status_value,freshness_at_utc,freshness_origin,attribute_hash,presence_hash,reduced_at_utc)
 SELECT @execution_id,r.source_key,s.stage_record_id,r.root_json,r.presence_json,r.metrics_json,r.competence_json,
   r.mdfe_presence,r.mdfe_status,r.freshness_at_utc,r.freshness_origin,r.attribute_hash,r.presence_hash,@now
 FROM #reduced r JOIN stg.execution_record s ON s.execution_id=@execution_id AND s.source_key=r.source_key;
 INSERT stg.manifesto_pick_candidate(manifesto_candidate_id,execution_id,source_key,pick_source_key,candidate_presence,provenance_json,freshness_at_utc)
 SELECT c.manifesto_candidate_id,@execution_id,o.source_key,o.pick_source_key,N'VALUE',N'{"reducer":"MAN-01","relation":"UNRESOLVED_V2_046A"}',MAX(o.freshness_at_utc)
 FROM #observations o JOIN stg.manifesto_reduced_candidate c ON c.execution_id=@execution_id AND c.source_key=o.source_key
 WHERE o.pick_source_key IS NOT NULL GROUP BY c.manifesto_candidate_id,o.source_key,o.pick_source_key;
 SELECT *,DENSE_RANK() OVER(PARTITION BY source_key,mdfe_key ORDER BY freshness_at_utc DESC) AS child_rank
 INTO #mdfe FROM #observations WHERE mdfe_key IS NOT NULL;
 IF EXISTS(SELECT 1 FROM #mdfe WHERE child_rank=1 GROUP BY source_key,mdfe_key HAVING COUNT(DISTINCT mdfe_number)>1)
   THROW 52819,N'MDFE_PAIR_CONFLICT',1;
 INSERT stg.manifesto_mdfe_candidate(manifesto_candidate_id,execution_id,source_key,mdfe_key,mdfe_number,provenance_json,freshness_at_utc)
 SELECT c.manifesto_candidate_id,@execution_id,o.source_key,o.mdfe_key,MIN(o.mdfe_number),N'{"reducer":"MAN-02","pair":"SAME_PHYSICAL_OBSERVATION"}',MAX(o.freshness_at_utc)
 FROM #mdfe o JOIN stg.manifesto_reduced_candidate c ON c.execution_id=@execution_id AND c.source_key=o.source_key
 WHERE o.child_rank=1 GROUP BY c.manifesto_candidate_id,o.source_key,o.mdfe_key;
 EXEC core.usp_prepare_staged_execution @execution_id,@contract_version,@contract_fingerprint,@configuration_version,@configuration_fingerprint;
 INSERT ctl.manifesto_promotion_result(execution_id,physical_observation_rows,reduced_root_rows,quarantined_observation_rows,validation_state,validated_at_utc)
 SELECT @execution_id,(SELECT COUNT_BIG(*) FROM #observations),(SELECT COUNT_BIG(*) FROM #reduced),0,N'PASSED',@now;
 COMMIT TRANSACTION;
 END TRY BEGIN CATCH IF @@TRANCOUNT>0 ROLLBACK TRANSACTION; THROW; END CATCH;
END;
GO

CREATE OR ALTER PROCEDURE stg.usp_stage_cotacao_record
  @execution_id UNIQUEIDENTIFIER, @input_batch_number INT, @input_record_ordinal INT,
  @source_key NVARCHAR(MAX), @payload_json NVARCHAR(MAX), @field_presence_json NVARCHAR(MAX),
  @user_name_normalized NVARCHAR(MAX),
  @nfse_issued_at_utc DATETIME2(3), @cte_issued_at_utc DATETIME2(3), @requested_at_utc DATETIME2(3),
  @freshness_business_date DATE,
  @total_amount DECIMAL(19,4), @currency_code CHAR(3), @origin_uf CHAR(2), @destination_uf CHAR(2),
  @validation_disposition NVARCHAR(16), @quarantine_reason_code NVARCHAR(128), @observed_at_utc DATETIME2(3)
AS
BEGIN
    DECLARE @b55_actual NVARCHAR(MAX)=(SELECT LOWER(CONVERT(NVARCHAR(36),@execution_id)) AS [execution],
       N'cotacoes' AS [entity],N'cotacoes' AS [workload],N'BACKFILL' AS [mode] FOR JSON PATH,WITHOUT_ARRAY_WRAPPER);
    IF ctl.fn_runtime_consumed_scope(@b55_actual,1,NULL)<>1 THROW 52840,N'B55_VERTICAL_CONSUMER_FENCE',1;
    IF NOT EXISTS(SELECT 1 FROM ctl.execution_attempt a
      JOIN ctl.execution_partition p ON p.partition_id=a.partition_id AND p.current_execution_id=a.execution_id
      JOIN ctl.execution_lease l ON l.execution_id=a.execution_id AND l.partition_id=p.partition_id
      WHERE a.execution_id=@execution_id AND a.current_state=N'EXTRACTING' AND l.released_at_utc IS NULL
        AND l.expires_at_utc>SYSUTCDATETIME()) THROW 52841,N'B55_STAGE_LEASE_REQUIRED',1;
  SET NOCOUNT ON;
  IF @input_record_ordinal NOT BETWEEN 1 AND 1000 THROW 51900,N'Ordinal vertical de Cotações deve estar entre 1 e 1000.',1;
  IF @validation_disposition NOT IN(N'VALID',N'QUARANTINE') OR @observed_at_utc IS NULL
     OR (@validation_disposition=N'QUARANTINE' AND @quarantine_reason_code IS NULL) THROW 51900,N'Registro de Cotações inválido.',1;
  IF @validation_disposition=N'VALID' AND (
     @source_key IS NULL OR LEFT(@source_key,8) COLLATE Latin1_General_100_BIN2<>N'INTEGER:'
     OR TRY_CONVERT(BIGINT,SUBSTRING(@source_key,9,256)) IS NULL
     OR TRY_CONVERT(BIGINT,SUBSTRING(@source_key,9,256))<=CONVERT(BIGINT,0)
     OR DATALENGTH(@source_key)<>DATALENGTH(CONCAT(N'INTEGER:',CONVERT(NVARCHAR(20),TRY_CONVERT(BIGINT,SUBSTRING(@source_key,9,256)))))
     OR @source_key COLLATE Latin1_General_100_BIN2<>CONCAT(N'INTEGER:',CONVERT(NVARCHAR(20),TRY_CONVERT(BIGINT,SUBSTRING(@source_key,9,256)))) COLLATE Latin1_General_100_BIN2
     OR ISJSON(@payload_json)<>1 OR ISJSON(@field_presence_json)<>1
     OR @currency_code IS NOT NULL
     OR COALESCE(@nfse_issued_at_utc,@cte_issued_at_utc,@requested_at_utc) IS NULL
     OR @freshness_business_date IS NULL
  ) THROW 51900,N'Campos tipados de Cotações inválidos.',1;
  DECLARE @presence_sequence NVARCHAR(8)=JSON_VALUE(@field_presence_json,N'$.sequence_code');
  DECLARE @presence_requested NVARCHAR(8)=JSON_VALUE(@field_presence_json,N'$.requested_at');
  DECLARE @presence_nfse NVARCHAR(8)=JSON_VALUE(@field_presence_json,N'$.qoe_qes_fit_nse_issued_at');
  DECLARE @presence_cte NVARCHAR(8)=JSON_VALUE(@field_presence_json,N'$.qoe_qes_fit_fhe_cte_issued_at');
  DECLARE @presence_total NVARCHAR(8)=JSON_VALUE(@field_presence_json,N'$.qoe_qes_total');
  DECLARE @presence_nickname NVARCHAR(8)=JSON_VALUE(@field_presence_json,N'$.qoe_crn_psn_nickname');
  DECLARE @presence_user NVARCHAR(8)=JSON_VALUE(@field_presence_json,N'$.qoe_uer_name');
  DECLARE @presence_origin NVARCHAR(8)=JSON_VALUE(@field_presence_json,N'$.qoe_qes_ony_sae_code');
  DECLARE @presence_destination NVARCHAR(8)=JSON_VALUE(@field_presence_json,N'$.qoe_qes_diy_sae_code');
  IF @validation_disposition=N'VALID' AND (
     COALESCE(@presence_sequence,N'INVALID') COLLATE Latin1_General_100_BIN2<>N'VALUE'
     OR COALESCE(@presence_requested,N'INVALID') COLLATE Latin1_General_100_BIN2 NOT IN(N'ABSENT',N'NULL',N'VALUE')
     OR COALESCE(@presence_nfse,N'INVALID') COLLATE Latin1_General_100_BIN2 NOT IN(N'ABSENT',N'NULL',N'VALUE')
     OR COALESCE(@presence_cte,N'INVALID') COLLATE Latin1_General_100_BIN2 NOT IN(N'ABSENT',N'NULL',N'VALUE')
     OR COALESCE(@presence_total,N'INVALID') COLLATE Latin1_General_100_BIN2 NOT IN(N'ABSENT',N'NULL',N'VALUE')
     OR COALESCE(@presence_nickname,N'INVALID') COLLATE Latin1_General_100_BIN2 NOT IN(N'ABSENT',N'NULL',N'VALUE')
     OR COALESCE(@presence_user,N'INVALID') COLLATE Latin1_General_100_BIN2 NOT IN(N'ABSENT',N'NULL',N'VALUE')
     OR COALESCE(@presence_origin,N'INVALID') COLLATE Latin1_General_100_BIN2 NOT IN(N'ABSENT',N'NULL',N'VALUE')
     OR COALESCE(@presence_destination,N'INVALID') COLLATE Latin1_General_100_BIN2 NOT IN(N'ABSENT',N'NULL',N'VALUE')
     OR (@presence_requested=N'VALUE' AND @requested_at_utc IS NULL)
     OR (@presence_requested IN(N'ABSENT',N'NULL') AND @requested_at_utc IS NOT NULL)
     OR (@presence_nfse=N'VALUE' AND @nfse_issued_at_utc IS NULL)
     OR (@presence_nfse IN(N'ABSENT',N'NULL') AND @nfse_issued_at_utc IS NOT NULL)
     OR (@presence_cte=N'VALUE' AND @cte_issued_at_utc IS NULL)
     OR (@presence_cte IN(N'ABSENT',N'NULL') AND @cte_issued_at_utc IS NOT NULL)
     OR (@presence_total=N'VALUE' AND @total_amount IS NULL)
     OR (@presence_total IN(N'ABSENT',N'NULL') AND @total_amount IS NOT NULL)
     OR (@presence_user IN(N'ABSENT',N'NULL') AND @user_name_normalized IS NOT NULL)
     OR (@presence_origin=N'VALUE' AND @origin_uf IS NULL)
     OR (@presence_origin IN(N'ABSENT',N'NULL') AND @origin_uf IS NOT NULL)
     OR (@presence_destination=N'VALUE' AND @destination_uf IS NULL)
     OR (@presence_destination IN(N'ABSENT',N'NULL') AND @destination_uf IS NOT NULL)
  ) THROW 51900,N'Presença tri-state de Cotações é inválida.',1;
  -- As rejeições puramente contratuais acima não iniciam transação. A escrita
  -- tipada abaixo continua protegida por XACT_ABORT e transação própria.
  SET XACT_ABORT ON;
  BEGIN TRANSACTION;
  DECLARE @entity NVARCHAR(128),@source_instance NVARCHAR(128),@tenant_scope NVARCHAR(128);
  SELECT @entity=p.entity_name,@source_instance=p.source_instance,@tenant_scope=p.tenant_scope
  FROM ctl.execution_attempt a WITH(UPDLOCK,HOLDLOCK)
  JOIN ctl.execution_partition p WITH(HOLDLOCK) ON p.partition_id=a.partition_id
  WHERE a.execution_id=@execution_id;
  IF @entity COLLATE Latin1_General_100_BIN2<>N'cotacoes' THROW 51901,N'A execução não pertence a Cotações.',1;
  IF UPPER(@source_instance) IN(N'DEFAULT',N'GLOBAL',N'SINGLETON')
     OR UPPER(@tenant_scope) IN(N'DEFAULT',N'GLOBAL',N'SINGLETON')
    THROW 51903,N'Cotações exige source_instance e tenant_scope explícitos, sem sentinelas.',1;
  DECLARE @hash CHAR(64)=CASE WHEN @validation_disposition=N'VALID' THEN LOWER(CONVERT(CHAR(64),HASHBYTES('SHA2_256',CONVERT(VARBINARY(MAX),CONCAT(N'cotacoes-attributes-v1|',@payload_json,N'|',@field_presence_json))),2)) END;
  DECLARE @row_fingerprint_version NVARCHAR(128)=
    CASE WHEN @validation_disposition=N'VALID' THEN N'cotacoes-envelope-v1' END;
  DECLARE @presence_fingerprint_version NVARCHAR(128)=
    CASE WHEN @validation_disposition=N'VALID' THEN N'cotacoes-presence-v1' END;
  DECLARE @source_freshness_at_utc DATETIME2(3)=
    COALESCE(@nfse_issued_at_utc,@cte_issued_at_utc,@requested_at_utc);
  EXEC stg.usp_stage_record @execution_id,@input_batch_number,@input_record_ordinal,@source_key,
    @row_fingerprint_version,@hash,@presence_fingerprint_version,@hash,
    @source_freshness_at_utc,@validation_disposition,@quarantine_reason_code,@observed_at_utc;
  IF @validation_disposition=N'QUARANTINE' BEGIN COMMIT TRANSACTION; RETURN; END;
  DECLARE @stage_record_id BIGINT; SELECT @stage_record_id=stage_record_id FROM stg.execution_record WITH(UPDLOCK,HOLDLOCK) WHERE execution_id=@execution_id AND input_batch_number=@input_batch_number AND input_record_ordinal=@input_record_ordinal;
  IF EXISTS(SELECT 1 FROM stg.cotacao_record WITH(UPDLOCK,HOLDLOCK) WHERE stage_record_id=@stage_record_id AND attribute_hash<>@hash) THROW 51902,N'Retry de Cotações possui conteúdo divergente.',1;
  IF NOT EXISTS(SELECT 1 FROM stg.cotacao_record WHERE stage_record_id=@stage_record_id)
    INSERT stg.cotacao_record(
      stage_record_id,execution_id,source_key,source_key_wire_type,payload_json,field_presence_json,
      user_name_normalized,nfse_issued_at_utc,cte_issued_at_utc,requested_at_utc,freshness_at_utc,
      freshness_origin,freshness_business_date,total_amount,currency_code,origin_uf,destination_uf,attribute_hash
    ) VALUES(
      @stage_record_id,@execution_id,@source_key,N'INTEGER',@payload_json,@field_presence_json,
      @user_name_normalized,@nfse_issued_at_utc,@cte_issued_at_utc,@requested_at_utc,
      COALESCE(@nfse_issued_at_utc,@cte_issued_at_utc,@requested_at_utc),
      CASE WHEN @nfse_issued_at_utc IS NOT NULL THEN N'NFSE_ISSUED_AT' WHEN @cte_issued_at_utc IS NOT NULL THEN N'CTE_ISSUED_AT' ELSE N'REQUESTED_AT' END,
      @freshness_business_date,@total_amount,@currency_code,@origin_uf,@destination_uf,@hash
    );
  COMMIT TRANSACTION;
END;
GO

CREATE OR ALTER PROCEDURE stg.usp_stage_manifesto_observation
    @execution_id UNIQUEIDENTIFIER,@input_batch_number INT,@input_record_ordinal INT,
    @source_key NVARCHAR(MAX),@payload_json NVARCHAR(MAX),@field_presence_json NVARCHAR(MAX),
    @root_fields_json NVARCHAR(MAX),@metric_values_json NVARCHAR(MAX),@relation_candidates_json NVARCHAR(MAX),
    @mdfe_status_presence NVARCHAR(MAX),@mdfe_status_value NVARCHAR(MAX),@pick_source_key NVARCHAR(MAX),
    @mdfe_key NVARCHAR(MAX),@mdfe_number NVARCHAR(MAX),@freshness_at_utc DATETIME2(3),
    @freshness_origin NVARCHAR(MAX),@competence_json NVARCHAR(MAX),@validation_disposition NVARCHAR(MAX),
    @quarantine_reason_code NVARCHAR(MAX),@observed_at_utc DATETIME2(3)
AS
BEGIN
    DECLARE @b55_actual NVARCHAR(MAX)=(SELECT LOWER(CONVERT(NVARCHAR(36),@execution_id)) AS [execution],
       N'manifestos' AS [entity],N'manifestos' AS [workload],N'BACKFILL' AS [mode] FOR JSON PATH,WITHOUT_ARRAY_WRAPPER);
    IF ctl.fn_runtime_consumed_scope(@b55_actual,1,NULL)<>1 THROW 52840,N'B55_VERTICAL_CONSUMER_FENCE',1;
    IF NOT EXISTS(SELECT 1 FROM ctl.execution_attempt a
      JOIN ctl.execution_partition p ON p.partition_id=a.partition_id AND p.current_execution_id=a.execution_id
      JOIN ctl.execution_lease l ON l.execution_id=a.execution_id AND l.partition_id=p.partition_id
      WHERE a.execution_id=@execution_id AND a.current_state=N'EXTRACTING' AND l.released_at_utc IS NULL
        AND l.expires_at_utc>SYSUTCDATETIME()) THROW 52841,N'B55_STAGE_LEASE_REQUIRED',1;
    SET NOCOUNT ON; SET XACT_ABORT ON;
    IF @input_record_ordinal NOT BETWEEN 1 AND 100 OR @observed_at_utc IS NULL
       OR DATALENGTH(@source_key)>512 OR DATALENGTH(@mdfe_status_value)>100
       OR DATALENGTH(@pick_source_key)>512 OR DATALENGTH(@mdfe_key)>88 OR DATALENGTH(@mdfe_number)>256
       OR DATALENGTH(@validation_disposition)>32 OR DATALENGTH(@quarantine_reason_code)>128
        THROW 52000,N'Observação física de Manifestos excede limite ou ordinal.',1;
    SET @validation_disposition=LTRIM(RTRIM(@validation_disposition));
    SET @quarantine_reason_code=LTRIM(RTRIM(@quarantine_reason_code));
    SET @mdfe_status_presence=LTRIM(RTRIM(@mdfe_status_presence));
    SET @freshness_origin=LTRIM(RTRIM(@freshness_origin));
    IF @validation_disposition NOT IN(N'VALID',N'QUARANTINE')
        THROW 52004,N'Observação física de Manifestos possui disposition inválida.',1;
    IF @validation_disposition=N'VALID' AND @quarantine_reason_code IS NOT NULL
        THROW 52005,N'Observação válida de Manifestos não pode receber razão de quarentena.',1;
    IF @validation_disposition=N'VALID' AND ISJSON(@payload_json)<>1
        THROW 52051,N'Observação válida de Manifestos não contém payload JSON canônico.',1;
    IF @validation_disposition=N'VALID' AND ISJSON(@field_presence_json)<>1
        THROW 52052,N'Observação válida de Manifestos não contém presenças JSON canônicas.',1;
    IF @validation_disposition=N'VALID' AND ISJSON(@root_fields_json)<>1
        THROW 52053,N'Observação válida de Manifestos não contém raiz JSON canônica.',1;
    IF @validation_disposition=N'VALID' AND ISJSON(@metric_values_json)<>1
        THROW 52054,N'Observação válida de Manifestos não contém métricas JSON canônicas.',1;
    IF @validation_disposition=N'VALID' AND ISJSON(@relation_candidates_json)<>1
        THROW 52055,N'Observação válida de Manifestos não contém candidatos relacionais JSON canônicos.',1;
    IF @validation_disposition=N'VALID' AND (@freshness_at_utc IS NULL
       OR @freshness_origin NOT IN(N'FINISHED_AT',N'CLOSED_AT',N'DEPARTURED_AT',N'CREATED_AT'))
        THROW 52008,N'Observação válida de Manifestos não contém freshness canônico.',1;
    IF @validation_disposition=N'QUARANTINE' AND @quarantine_reason_code IS NULL
        THROW 52006,N'Quarentena física de Manifestos requer razão preservada.',1;
    IF @validation_disposition=N'VALID' AND (
        @source_key NOT LIKE N'INTEGER:[1-9]%' OR SUBSTRING(@source_key,9,256) LIKE N'%[^0-9]%'
        OR @pick_source_key IS NOT NULL AND (@pick_source_key NOT LIKE N'INTEGER:[1-9]%' OR SUBSTRING(@pick_source_key,9,256) LIKE N'%[^0-9]%')
        OR ((@mdfe_key IS NULL AND @mdfe_number IS NOT NULL) OR (@mdfe_key IS NOT NULL AND @mdfe_number IS NULL))
        OR (@mdfe_key IS NOT NULL AND (@mdfe_key LIKE N'%[^0-9]%' OR LEN(@mdfe_key)<>44 OR @mdfe_number NOT LIKE N'[1-9]%' OR @mdfe_number LIKE N'%[^0-9]%'))
        OR @mdfe_status_presence NOT IN(N'ABSENT',N'NULL',N'VALUE')
        OR (@mdfe_status_presence=N'VALUE' AND @mdfe_status_value IS NULL)
        OR (@mdfe_status_presence IN(N'ABSENT',N'NULL') AND @mdfe_status_value IS NOT NULL)
        OR EXISTS(
            SELECT 1 FROM (VALUES
                (N'status',50),(N'mdfe_status',50),(N'mft_ape_name',255),(N'mft_man_name',255),
                (N'mft_vie_license_plate',10),(N'mft_vie_vee_name',255),(N'mft_vie_onr_name',255),
                (N'mft_mdr_iil_name',255),(N'mft_crn_psn_nickname',255),(N'mft_cat_cot_number',50),
                (N'contract_type',50),(N'mft_mdr_contract_type',50),(N'calculation_type',50),(N'cargo_type',255),
                (N'mft_uer_name',255),(N'mft_aoe_rer_name',255),(N'mft_aoe_comments',4000),(N'mft_cat_cot_status',50),
                (N'mft_iks_id',100),(N'mft_s_n_sequence_code',50),(N'mft_tl1_license_plate',10),
                (N'mft_tl2_license_plate',10),(N'operational_comments',4000),(N'closing_comments',4000),
                (N'mft_s_n_svs_sge_pyr_nickname',255),(N'mft_s_n_svs_sge_sse_name',255)
            ) AS limits(field_name,maximum_utf16_units)
            JOIN OPENJSON(@root_fields_json) AS text_value ON text_value.[key]=limits.field_name
            WHERE text_value.[type]=1 AND DATALENGTH(text_value.[value])>limits.maximum_utf16_units*2
        )
    ) THROW 52001,N'Observação física de Manifestos falha fechada por chave, filho ou texto.',1;
    BEGIN TRANSACTION;
    DECLARE @entity_name NVARCHAR(128),@source_instance NVARCHAR(128),@tenant_scope NVARCHAR(128);
    SELECT @entity_name=p.entity_name,@source_instance=p.source_instance,@tenant_scope=p.tenant_scope
    FROM ctl.execution_attempt a WITH(UPDLOCK,HOLDLOCK)
    JOIN ctl.execution_partition p WITH(HOLDLOCK) ON p.partition_id=a.partition_id WHERE a.execution_id=@execution_id;
    IF @entity_name<>N'manifestos' OR UPPER(@source_instance) IN(N'DEFAULT',N'GLOBAL',N'SINGLETON')
       OR UPPER(@tenant_scope) IN(N'DEFAULT',N'GLOBAL',N'SINGLETON') THROW 52002,N'Escopo de Manifestos inválido.',1;
    IF EXISTS(SELECT 1 FROM stg.manifesto_observation WITH(UPDLOCK,HOLDLOCK)
              WHERE execution_id=@execution_id AND input_batch_number=@input_batch_number AND input_record_ordinal=@input_record_ordinal
                AND (payload_json<>@payload_json OR field_presence_json<>@field_presence_json OR validation_disposition<>@validation_disposition
                     OR ISNULL(source_key,N'<null>')<>ISNULL(@source_key,N'<null>')))
        THROW 52003,N'Retry de observação física possui conteúdo divergente.',1;
    IF NOT EXISTS(SELECT 1 FROM stg.manifesto_observation WHERE execution_id=@execution_id AND input_batch_number=@input_batch_number AND input_record_ordinal=@input_record_ordinal)
    BEGIN
        INSERT stg.manifesto_observation(execution_id,input_batch_number,input_record_ordinal,source_key,validation_disposition,quarantine_reason_code,payload_json,field_presence_json,root_fields_json,metric_values_json,relation_candidates_json,mdfe_status_presence,mdfe_status_value,pick_source_key,mdfe_key,mdfe_number,freshness_at_utc,freshness_origin,competence_json,observed_at_utc)
        VALUES(@execution_id,@input_batch_number,@input_record_ordinal,@source_key,@validation_disposition,@quarantine_reason_code,@payload_json,@field_presence_json,@root_fields_json,@metric_values_json,@relation_candidates_json,@mdfe_status_presence,@mdfe_status_value,@pick_source_key,@mdfe_key,@mdfe_number,@freshness_at_utc,@freshness_origin,@competence_json,@observed_at_utc);
        DECLARE @observation_id BIGINT=CONVERT(BIGINT,SCOPE_IDENTITY());
        INSERT recon.manifesto_coleta_relation_candidate(execution_id,manifesto_observation_id,root_source_key,pick_source_key,candidate_presence,provenance_json,observed_at_utc)
        SELECT @execution_id,@observation_id,@source_key,@pick_source_key,
               COALESCE(JSON_VALUE(@relation_candidates_json,N'$.mft_pfs_pck_sequence_code.presence'),N'ABSENT'),
               @relation_candidates_json,@observed_at_utc;
    END;
    COMMIT TRANSACTION;
END;
GO

CREATE OR ALTER PROCEDURE stg.usp_stage_localizacao_carga_record
    @execution_id UNIQUEIDENTIFIER,
    @input_batch_number INT,
    @input_record_ordinal INT,
    @source_key NVARCHAR(256),
    @payload_json NVARCHAR(MAX),
    @field_presence_json NVARCHAR(MAX),
    @service_at_raw NVARCHAR(MAX),
    @service_at_utc DATETIME2(3),
    @service_at_presence NVARCHAR(8),
    @service_at_parse_state NVARCHAR(16),
    @invoices_volumes_raw NVARCHAR(MAX),
    @invoices_volumes_typed INT,
    @invoices_volumes_presence NVARCHAR(8),
    @invoices_volumes_parse_state NVARCHAR(16),
    @taxed_weight_raw NVARCHAR(MAX),
    @taxed_weight_typed DECIMAL(38,9),
    @invoices_value_raw NVARCHAR(MAX),
    @invoices_value_typed DECIMAL(38,9),
    @total_raw NVARCHAR(MAX),
    @total_typed DECIMAL(38,9),
    @status_raw NVARCHAR(MAX),
    @status_normalized NVARCHAR(MAX),
    @status_terminal BIT,
    @status_branch_nickname_provenance NVARCHAR(64),
    @validation_disposition NVARCHAR(16),
    @quarantine_reason_code NVARCHAR(128),
    @observed_at_utc DATETIME2(3)
AS
BEGIN
    DECLARE @b55_actual NVARCHAR(MAX)=(SELECT LOWER(CONVERT(NVARCHAR(36),@execution_id)) AS [execution],
       N'localizacao_cargas' AS [entity],N'localizacao_cargas' AS [workload],N'BACKFILL' AS [mode] FOR JSON PATH,WITHOUT_ARRAY_WRAPPER);
    IF ctl.fn_runtime_consumed_scope(@b55_actual,1,NULL)<>1 THROW 52840,N'B55_VERTICAL_CONSUMER_FENCE',1;
    IF NOT EXISTS(SELECT 1 FROM ctl.execution_attempt a
      JOIN ctl.execution_partition p ON p.partition_id=a.partition_id AND p.current_execution_id=a.execution_id
      JOIN ctl.execution_lease l ON l.execution_id=a.execution_id AND l.partition_id=p.partition_id
      WHERE a.execution_id=@execution_id AND a.current_state=N'EXTRACTING' AND l.released_at_utc IS NULL
        AND l.expires_at_utc>SYSUTCDATETIME()) THROW 52841,N'B55_STAGE_LEASE_REQUIRED',1;
    SET NOCOUNT ON;
    IF @execution_id IS NULL OR @input_batch_number IS NULL OR @input_batch_number<1
       OR @input_record_ordinal IS NULL OR @input_record_ordinal NOT BETWEEN 1 AND 100
       OR @observed_at_utc IS NULL
       OR @validation_disposition NOT IN(N'VALID',N'QUARANTINE')
       OR (@validation_disposition=N'QUARANTINE'
           AND (@quarantine_reason_code IS NULL OR LEN(@quarantine_reason_code) NOT BETWEEN 2 AND 64
             OR LEFT(@quarantine_reason_code,1) COLLATE Latin1_General_100_BIN2 NOT LIKE N'[A-Z]'
             OR @quarantine_reason_code COLLATE Latin1_General_100_BIN2 LIKE N'%[^A-Z0-9_]%'))
       OR (@validation_disposition=N'VALID' AND @quarantine_reason_code IS NOT NULL)
        THROW 52200,N'Envelope físico de Localização 8656 inválido.',1;

    IF @payload_json IS NULL OR @field_presence_json IS NULL
       OR ISJSON(@payload_json)<>1 OR ISJSON(@field_presence_json)<>1
        THROW 52201,N'RAW_PAYLOAD_PRESENCE_REQUIRED: JSON de Localização inválido.',1;

    DECLARE @expected_presence_fields TABLE(
        field_ordinal TINYINT NOT NULL PRIMARY KEY,
        field_name NVARCHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL UNIQUE,
        typed_state NVARCHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL
    );
    INSERT @expected_presence_fields VALUES
        (1,N'corporation_sequence_number',N'INTEGER_TYPE_TAGGED_SOURCE_KEY'),
        (2,N'type',N'UNVERIFIED_SOURCE_TYPE_PRESERVED_RAW'),
        (3,N'service_at',N'STRICT_TYPED_COLUMN'),
        (4,N'invoices_volumes',N'STRICT_TYPED_COLUMN'),
        (5,N'taxed_weight',N'STRICT_TYPED_COLUMN'),
        (6,N'invoices_value',N'STRICT_TYPED_COLUMN'),
        (7,N'total',N'STRICT_TYPED_COLUMN'),
        (8,N'service_type',N'UNVERIFIED_SOURCE_TYPE_PRESERVED_RAW'),
        (9,N'fit_crn_psn_nickname',N'UNVERIFIED_SOURCE_TYPE_PRESERVED_RAW'),
        (10,N'fit_dpn_delivery_prediction_at',N'UNVERIFIED_SOURCE_TYPE_PRESERVED_RAW'),
        (11,N'fit_dyn_name',N'UNVERIFIED_SOURCE_TYPE_PRESERVED_RAW'),
        (12,N'fit_dyn_drt_nickname',N'UNVERIFIED_SOURCE_TYPE_PRESERVED_RAW'),
        (13,N'fit_fsn_name',N'UNVERIFIED_SOURCE_TYPE_PRESERVED_RAW'),
        (14,N'fit_fln_status',N'STRICT_TYPED_COLUMN'),
        (15,N'fit_fln_cln_nickname',N'UNVERIFIED_SOURCE_TYPE_PRESERVED_RAW'),
        (16,N'fit_o_n_name',N'UNVERIFIED_SOURCE_TYPE_PRESERVED_RAW'),
        (17,N'fit_o_n_drt_nickname',N'UNVERIFIED_SOURCE_TYPE_PRESERVED_RAW');

    IF (SELECT COUNT_BIG(*) FROM OPENJSON(@field_presence_json))<>2
       OR (SELECT [type] FROM OPENJSON(@field_presence_json) WHERE [key]=N'fields')<>5
       OR (SELECT [type] FROM OPENJSON(@field_presence_json) WHERE [key]=N'policy')<>5
       OR EXISTS(SELECT 1 FROM OPENJSON(@field_presence_json)
                 WHERE [key] NOT IN(N'fields',N'policy'))
       OR (SELECT COUNT_BIG(*) FROM OPENJSON(@field_presence_json,N'$.fields'))<>17
       OR EXISTS(
            SELECT 1 FROM OPENJSON(@field_presence_json,N'$.fields') actual
            WHERE actual.[key] COLLATE Latin1_General_100_BIN2 NOT IN(
                SELECT field_name FROM @expected_presence_fields)
               OR actual.[type]<>5)
       OR EXISTS(
            SELECT 1 FROM @expected_presence_fields expected
            WHERE JSON_QUERY(@field_presence_json,
                    CONCAT(N'$.fields.',expected.field_name)) IS NULL)
       OR EXISTS(
            SELECT 1
            FROM @expected_presence_fields expected
            CROSS APPLY OPENJSON(JSON_QUERY(@field_presence_json,
                CONCAT(N'$.fields.',expected.field_name))) property
            GROUP BY expected.field_ordinal,expected.field_name
            HAVING COUNT_BIG(*)<>7
               OR SUM(CASE WHEN property.[key] IN(
                    N'path',N'presence',N'provenance',N'parseState',N'typedState',N'raw',
                    N'rawWireLexeme')
                    THEN 0 ELSE 1 END)>0
               OR SUM(CASE WHEN property.[key]=N'path' THEN 1 ELSE 0 END)<>1
               OR SUM(CASE WHEN property.[key]=N'presence' THEN 1 ELSE 0 END)<>1
               OR SUM(CASE WHEN property.[key]=N'provenance' THEN 1 ELSE 0 END)<>1
               OR SUM(CASE WHEN property.[key]=N'parseState' THEN 1 ELSE 0 END)<>1
               OR SUM(CASE WHEN property.[key]=N'typedState' THEN 1 ELSE 0 END)<>1
               OR SUM(CASE WHEN property.[key]=N'raw' THEN 1 ELSE 0 END)<>1
               OR SUM(CASE WHEN property.[key]=N'rawWireLexeme' THEN 1 ELSE 0 END)<>1)
       OR EXISTS(
            SELECT 1
            FROM @expected_presence_fields expected
            CROSS APPLY(SELECT JSON_QUERY(@field_presence_json,
                CONCAT(N'$.fields.',expected.field_name)) AS field_json) envelope
            CROSS APPLY(SELECT
                JSON_VALUE(envelope.field_json,N'$.presence') AS presence_state,
                JSON_VALUE(envelope.field_json,N'$.parseState') AS parse_state,
                (SELECT MAX(property.[value]) FROM OPENJSON(envelope.field_json) property
                 WHERE property.[key]=N'rawWireLexeme') AS raw_wire_lexeme,
                (SELECT MAX(property.[type]) FROM OPENJSON(envelope.field_json) property
                 WHERE property.[key]=N'rawWireLexeme') AS raw_wire_type,
                (SELECT MAX(property.[type]) FROM OPENJSON(envelope.field_json) property
                 WHERE property.[key]=N'raw') AS raw_type) evidence
            WHERE JSON_VALUE(envelope.field_json,N'$.path') COLLATE Latin1_General_100_BIN2
                    <>CONCAT(N'/',expected.field_name)
               OR JSON_VALUE(envelope.field_json,N'$.provenance')
                    COLLATE Latin1_General_100_BIN2<>N'DATAEXPORT_8656'
               OR JSON_VALUE(envelope.field_json,N'$.typedState')
                    COLLATE Latin1_General_100_BIN2<>expected.typed_state
               OR evidence.presence_state COLLATE Latin1_General_100_BIN2
                    NOT IN(N'ABSENT',N'NULL',N'VALUE')
               OR evidence.parse_state COLLATE Latin1_General_100_BIN2<>
                    CASE evidence.presence_state WHEN N'ABSENT' THEN N'NOT_PRESENT'
                         WHEN N'NULL' THEN N'EXPLICIT_NULL'
                         WHEN N'VALUE' THEN N'PRESERVED_OR_STRICTLY_PARSED_BY_NAMED_FIELD'
                    END
               OR evidence.raw_wire_type<>1
               OR (evidence.presence_state IN(N'ABSENT',N'NULL') AND evidence.raw_type<>0)
               OR (evidence.presence_state=N'VALUE' AND evidence.raw_type NOT IN(1,2,3))
               OR (evidence.presence_state IN(N'ABSENT',N'NULL')
                   AND evidence.raw_wire_lexeme<>N'NOT_APPLICABLE_WITHOUT_VALUE')
               OR (evidence.presence_state=N'VALUE' AND evidence.raw_type IN(1,3)
                   AND evidence.raw_wire_lexeme<>N'NOT_APPLICABLE_NON_NUMERIC_TOKEN')
               OR (evidence.presence_state=N'VALUE' AND evidence.raw_type=2
                   AND expected.field_name NOT IN(
                       N'invoices_volumes',N'taxed_weight',N'invoices_value',N'total')
                   AND evidence.raw_wire_lexeme<>N'UNAVAILABLE_AFTER_JSON_TREE_NORMALIZATION')
               OR (evidence.presence_state=N'VALUE' AND evidence.raw_type=2
                   AND expected.field_name IN(
                       N'invoices_volumes',N'taxed_weight',N'invoices_value',N'total')
                   AND (evidence.raw_wire_lexeme IS NULL OR evidence.raw_wire_lexeme=N'')))
       OR (SELECT COUNT_BIG(*) FROM OPENJSON(@field_presence_json,N'$.policy'))<>3
       OR JSON_VALUE(@field_presence_json,N'$.policy.matrix')
            COLLATE Latin1_General_100_BIN2<>N'LOCALIZACAO_8656_17_PATHS_V1'
       OR JSON_VALUE(@field_presence_json,N'$.policy.freightVolumeFallback')
            COLLATE Latin1_General_100_BIN2<>
                N'FREIGHT_VOLUME_FALLBACK_DEFERRED_RELATION_AND_PUBLICATION'
       OR JSON_QUERY(@field_presence_json,N'$.policy.statusBranchNickname') IS NULL
       OR EXISTS(SELECT 1 FROM OPENJSON(@field_presence_json,N'$.policy')
                 WHERE [key] NOT IN(N'matrix',N'freightVolumeFallback',N'statusBranchNickname'))
       OR (SELECT COUNT_BIG(*)
           FROM OPENJSON(@field_presence_json,N'$.policy.statusBranchNickname'))<>3
       OR JSON_VALUE(@field_presence_json,N'$.policy.statusBranchNickname.path')<>N'ABSENT'
       OR JSON_VALUE(@field_presence_json,N'$.policy.statusBranchNickname.presence')<>N'ABSENT'
       OR JSON_VALUE(@field_presence_json,N'$.policy.statusBranchNickname.provenance')
            <>N'UNSOURCED_LEGACY'
       OR EXISTS(SELECT 1
           FROM OPENJSON(@field_presence_json,N'$.policy.statusBranchNickname')
           WHERE [key] NOT IN(N'path',N'presence',N'provenance') OR [type]<>1)
        THROW 52202,N'PRESENCE_SCHEMA_INVALID: envelope fechado dos 17 paths 8656 inválido.',1;

    DECLARE @taxed_weight_presence NVARCHAR(8)=
        JSON_VALUE(@field_presence_json,N'$.fields.taxed_weight.presence');
    DECLARE @invoices_value_presence NVARCHAR(8)=
        JSON_VALUE(@field_presence_json,N'$.fields.invoices_value.presence');
    DECLARE @total_presence NVARCHAR(8)=
        JSON_VALUE(@field_presence_json,N'$.fields.total.presence');
    DECLARE @status_presence NVARCHAR(8)=
        JSON_VALUE(@field_presence_json,N'$.fields.fit_fln_status.presence');

    IF @validation_disposition=N'VALID'
    BEGIN
        IF @source_key IS NULL
           OR LEFT(@source_key,8) COLLATE Latin1_General_100_BIN2<>N'INTEGER:'
           OR NOT (
                SUBSTRING(@source_key,9,248) COLLATE Latin1_General_100_BIN2=N'0'
                OR (SUBSTRING(@source_key,9,248) COLLATE Latin1_General_100_BIN2 LIKE N'[1-9]%'
                    AND SUBSTRING(@source_key,9,248) COLLATE Latin1_General_100_BIN2 NOT LIKE N'%[^0-9]%')
                OR (SUBSTRING(@source_key,9,248) COLLATE Latin1_General_100_BIN2 LIKE N'-[1-9]%'
                    AND SUBSTRING(@source_key,10,247) COLLATE Latin1_General_100_BIN2 NOT LIKE N'%[^0-9]%')
           )
            THROW 52201,N'Identidade INTEGER type-tagged de Localização inválida.',1;

        DECLARE @payload_source_key NVARCHAR(MAX),@payload_source_key_type INT,
                @presence_source_key NVARCHAR(MAX),@presence_source_key_type INT;
        SELECT @payload_source_key=[value],@payload_source_key_type=[type]
        FROM OPENJSON(@payload_json) WHERE [key]=N'corporation_sequence_number';
        SELECT @presence_source_key=[value],@presence_source_key_type=[type]
        FROM OPENJSON(JSON_QUERY(@field_presence_json,
             N'$.fields.corporation_sequence_number')) WHERE [key]=N'raw';
        IF JSON_VALUE(@field_presence_json,
                N'$.fields.corporation_sequence_number.presence')<>N'VALUE'
           OR @payload_source_key_type<>2 OR @presence_source_key_type<>2
           OR CONCAT(N'INTEGER:',@payload_source_key) COLLATE Latin1_General_100_BIN2<>@source_key
           OR @payload_source_key COLLATE Latin1_General_100_BIN2<>@presence_source_key
           OR EXISTS(SELECT 1 FROM OPENJSON(@payload_json) actual
                     WHERE actual.[key] COLLATE Latin1_General_100_BIN2 NOT IN(
                         SELECT field_name FROM @expected_presence_fields))
           OR EXISTS(SELECT [key] FROM OPENJSON(@payload_json)
                     GROUP BY [key] HAVING COUNT_BIG(*)>1)
           OR EXISTS(
                SELECT 1
                FROM @expected_presence_fields expected
                CROSS APPLY(SELECT JSON_QUERY(@field_presence_json,
                    CONCAT(N'$.fields.',expected.field_name)) AS field_json) envelope
                OUTER APPLY(SELECT MAX(actual.[value]) AS [value],MAX(actual.[type]) AS [type]
                    FROM OPENJSON(@payload_json) actual
                    WHERE actual.[key] COLLATE Latin1_General_100_BIN2=expected.field_name) payload
                OUTER APPLY(SELECT MAX(raw_property.[value]) AS [value],
                                   MAX(raw_property.[type]) AS [type]
                    FROM OPENJSON(envelope.field_json) raw_property
                    WHERE raw_property.[key]=N'raw') raw_evidence
                WHERE (JSON_VALUE(envelope.field_json,N'$.presence')=N'ABSENT'
                        AND payload.[type] IS NOT NULL)
                   OR (JSON_VALUE(envelope.field_json,N'$.presence')=N'NULL'
                        AND ISNULL(payload.[type],-1)<>0)
                   OR (JSON_VALUE(envelope.field_json,N'$.presence')=N'VALUE'
                        AND (payload.[type] IS NULL OR payload.[type]<>raw_evidence.[type]
                          OR payload.[value] COLLATE Latin1_General_100_BIN2
                             <>raw_evidence.[value] COLLATE Latin1_General_100_BIN2)))
            THROW 52201,N'SOURCE_KEY_PAYLOAD_DIVERGENT_OR_UNAPPROVED_PATH.',1;

        DECLARE @service_at_envelope_raw_value NVARCHAR(MAX),
                @service_at_envelope_raw_type INT,
                @service_at_scalar_raw_value NVARCHAR(MAX),
                @service_at_scalar_raw_type INT;
        SELECT @service_at_envelope_raw_value=raw_property.[value],
               @service_at_envelope_raw_type=raw_property.[type]
        FROM OPENJSON(JSON_QUERY(@field_presence_json,N'$.fields.service_at')) raw_property
        WHERE raw_property.[key]=N'raw';
        IF @service_at_raw IS NULL
           OR ISJSON(CONCAT(N'[',@service_at_raw,N']'))<>1
            THROW 52203,N'SERVICE_AT_REQUIRED_VALID: raw não é um token JSON íntegro.',1;
        SELECT @service_at_scalar_raw_value=scalar_raw.[value],
               @service_at_scalar_raw_type=scalar_raw.[type]
        FROM OPENJSON(CONCAT(N'[',@service_at_raw,N']')) scalar_raw;
        DECLARE @service_at_has_explicit_offset BIT=CASE
            WHEN RIGHT(@service_at_scalar_raw_value,1) COLLATE Latin1_General_100_BIN2=N'Z'
              OR (LEN(@service_at_scalar_raw_value)>=6
                AND SUBSTRING(@service_at_scalar_raw_value,
                    LEN(@service_at_scalar_raw_value)-5,1) IN(N'+',N'-')
                AND SUBSTRING(@service_at_scalar_raw_value,
                    LEN(@service_at_scalar_raw_value)-2,1)=N':')
            THEN 1 ELSE 0 END;
        DECLARE @service_at_local_value DATETIME2(7)=CASE
            WHEN @service_at_has_explicit_offset=0
            THEN TRY_CONVERT(DATETIME2(7),@service_at_scalar_raw_value,126) END;
        -- Java aceita data civil somente quando America/Sao_Paulo oferece um único offset.
        -- Os candidatos antes/depois da transição detectam tanto gap quanto overlap.
        DECLARE @service_at_offset_before INT=CASE WHEN @service_at_local_value IS NOT NULL
            THEN DATEPART(TZOFFSET,DATEADD(DAY,-1,@service_at_local_value)
                AT TIME ZONE N'E. South America Standard Time') END;
        DECLARE @service_at_offset_after INT=CASE WHEN @service_at_local_value IS NOT NULL
            THEN DATEPART(TZOFFSET,DATEADD(DAY,1,@service_at_local_value)
                AT TIME ZONE N'E. South America Standard Time') END;
        DECLARE @service_at_candidate_before DATETIMEOFFSET(7)=CASE
            WHEN @service_at_local_value IS NOT NULL
            THEN TODATETIMEOFFSET(@service_at_local_value,@service_at_offset_before) END;
        DECLARE @service_at_candidate_after DATETIMEOFFSET(7)=CASE
            WHEN @service_at_local_value IS NOT NULL
            THEN TODATETIMEOFFSET(@service_at_local_value,@service_at_offset_after) END;
        DECLARE @service_at_roundtrip_before DATETIMEOFFSET(7)=CASE
            WHEN @service_at_candidate_before IS NOT NULL THEN
                @service_at_candidate_before AT TIME ZONE N'E. South America Standard Time' END;
        DECLARE @service_at_roundtrip_after DATETIMEOFFSET(7)=CASE
            WHEN @service_at_candidate_after IS NOT NULL THEN
                @service_at_candidate_after AT TIME ZONE N'E. South America Standard Time' END;
        DECLARE @service_at_before_valid BIT=CASE
            WHEN CONVERT(DATETIME2(7),@service_at_roundtrip_before)=@service_at_local_value
             AND DATEPART(TZOFFSET,@service_at_roundtrip_before)=@service_at_offset_before
            THEN 1 ELSE 0 END;
        DECLARE @service_at_after_valid BIT=CASE
            WHEN @service_at_offset_after<>@service_at_offset_before
             AND CONVERT(DATETIME2(7),@service_at_roundtrip_after)=@service_at_local_value
             AND DATEPART(TZOFFSET,@service_at_roundtrip_after)=@service_at_offset_after
            THEN 1 ELSE 0 END;
        DECLARE @service_at_valid_offset_count TINYINT=
            CONVERT(TINYINT,@service_at_before_valid)+CONVERT(TINYINT,@service_at_after_valid);
        DECLARE @service_at_local_zoned DATETIMEOFFSET(7)=CASE
            WHEN @service_at_valid_offset_count=1 AND @service_at_before_valid=1
                THEN @service_at_candidate_before
            WHEN @service_at_valid_offset_count=1 AND @service_at_after_valid=1
                THEN @service_at_candidate_after END;
        DECLARE @service_at_derived_utc DATETIME2(3)=CASE
            WHEN @service_at_has_explicit_offset=1 THEN CONVERT(DATETIME2(3),SWITCHOFFSET(
                TRY_CONVERT(DATETIMEOFFSET(7),@service_at_scalar_raw_value,127),N'+00:00'))
            WHEN @service_at_valid_offset_count=1
            THEN CONVERT(DATETIME2(3),SWITCHOFFSET(@service_at_local_zoned,N'+00:00')) END;
        IF @service_at_presence<>N'VALUE' OR @service_at_parse_state<>N'VALID'
           OR @service_at_utc IS NULL OR @service_at_derived_utc IS NULL
           OR @service_at_scalar_raw_type<>1 OR @service_at_envelope_raw_type<>1
           OR @service_at_scalar_raw_value COLLATE Latin1_General_100_BIN2
                <>@service_at_envelope_raw_value COLLATE Latin1_General_100_BIN2
           OR @service_at_utc<>@service_at_derived_utc
           OR @service_at_presence<>
                JSON_VALUE(@field_presence_json,N'$.fields.service_at.presence')
            THROW 52203,N'SERVICE_AT_REQUIRED_VALID: frescor de Localização inválido.',1;

        IF EXISTS(
            SELECT 1 FROM @expected_presence_fields expected
            CROSS APPLY(SELECT JSON_QUERY(@field_presence_json,
                CONCAT(N'$.fields.',expected.field_name)) AS field_json) envelope
            CROSS APPLY(SELECT
                MAX(CASE WHEN property.[key]=N'raw' THEN property.[type] END) AS raw_type,
                MAX(CASE WHEN property.[key]=N'rawWireLexeme'
                         THEN property.[value] END) AS raw_wire_lexeme
                FROM OPENJSON(envelope.field_json) property) raw_value
            WHERE expected.field_name IN(
                    N'invoices_volumes',N'taxed_weight',N'invoices_value',N'total')
              AND JSON_VALUE(envelope.field_json,N'$.presence')=N'VALUE'
              AND raw_value.raw_type=2
              AND raw_value.raw_wire_lexeme COLLATE Latin1_General_100_BIN2=
                    N'UNAVAILABLE_AFTER_JSON_TREE_NORMALIZATION'
        ) THROW 52205,N'DECIMAL_INVALID: léxico numérico wire indisponível.',1;

        DECLARE @invoices_volumes_envelope_raw_value NVARCHAR(MAX),
                @invoices_volumes_envelope_raw_type INT,
                @invoices_volumes_scalar_raw_value NVARCHAR(MAX),
                @invoices_volumes_scalar_raw_type INT;
        SELECT @invoices_volumes_envelope_raw_value=raw_property.[value],
               @invoices_volumes_envelope_raw_type=raw_property.[type]
        FROM OPENJSON(JSON_QUERY(@field_presence_json,N'$.fields.invoices_volumes')) raw_property
        WHERE raw_property.[key]=N'raw';
        IF @invoices_volumes_raw IS NOT NULL
           AND ISJSON(CONCAT(N'[',@invoices_volumes_raw,N']'))<>1
            THROW 52204,N'INVOICES_VOLUMES_INVALID: raw não é um token JSON íntegro.',1;
        IF @invoices_volumes_raw IS NOT NULL
            SELECT @invoices_volumes_scalar_raw_value=scalar_raw.[value],
                   @invoices_volumes_scalar_raw_type=scalar_raw.[type]
            FROM OPENJSON(CONCAT(N'[',@invoices_volumes_raw,N']')) scalar_raw;
        DECLARE @invoices_volumes_wire_lexeme NVARCHAR(MAX);
        SELECT @invoices_volumes_wire_lexeme=property.[value]
        FROM OPENJSON(JSON_QUERY(@field_presence_json,N'$.fields.invoices_volumes')) property
        WHERE property.[key]=N'rawWireLexeme';
        IF @invoices_volumes_presence<>
                JSON_VALUE(@field_presence_json,N'$.fields.invoices_volumes.presence')
           OR @invoices_volumes_presence NOT IN(N'ABSENT',N'NULL',N'VALUE')
           OR (@invoices_volumes_presence=N'ABSENT'
               AND (@invoices_volumes_parse_state<>N'NOT_PRESENT'
                 OR @invoices_volumes_raw IS NOT NULL OR @invoices_volumes_typed IS NOT NULL))
           OR (@invoices_volumes_presence=N'NULL'
               AND (@invoices_volumes_parse_state<>N'EXPLICIT_NULL'
                 OR @invoices_volumes_raw IS NOT NULL OR @invoices_volumes_typed IS NOT NULL))
           OR (@invoices_volumes_presence=N'VALUE'
               AND (@invoices_volumes_parse_state<>N'VALID'
                  OR @invoices_volumes_raw IS NULL OR @invoices_volumes_typed IS NULL
                  OR @invoices_volumes_typed<0
                  OR @invoices_volumes_scalar_raw_type NOT IN(1,2)
                  OR @invoices_volumes_scalar_raw_type<>@invoices_volumes_envelope_raw_type
                  OR (@invoices_volumes_scalar_raw_type=1
                    AND @invoices_volumes_scalar_raw_value COLLATE Latin1_General_100_BIN2
                       <>@invoices_volumes_envelope_raw_value COLLATE Latin1_General_100_BIN2)
                  OR (@invoices_volumes_scalar_raw_type=2
                    AND (@invoices_volumes_wire_lexeme COLLATE Latin1_General_100_BIN2
                           <>@invoices_volumes_raw COLLATE Latin1_General_100_BIN2
                      OR TRY_CONVERT(INT,@invoices_volumes_envelope_raw_value) IS NULL
                      OR TRY_CONVERT(INT,@invoices_volumes_envelope_raw_value)
                           <>@invoices_volumes_typed))))
            THROW 52204,N'INVOICES_VOLUMES_INVALID: inteiro estrito inválido.',1;

        DECLARE @invoices_volumes_lexeme NVARCHAR(MAX)=CASE
            WHEN @invoices_volumes_wire_lexeme NOT IN(
                    N'NOT_APPLICABLE_NON_NUMERIC_TOKEN',
                    N'UNAVAILABLE_AFTER_JSON_TREE_NORMALIZATION')
              THEN @invoices_volumes_wire_lexeme
            WHEN LEFT(@invoices_volumes_raw,1)=N'"' AND RIGHT(@invoices_volumes_raw,1)=N'"'
              AND DATALENGTH(@invoices_volumes_raw)>=4
              THEN SUBSTRING(@invoices_volumes_raw,2,LEN(@invoices_volumes_raw)-2)
            ELSE @invoices_volumes_raw END;
        IF @invoices_volumes_presence=N'VALUE'
           AND (@invoices_volumes_lexeme=N''
             OR (@invoices_volumes_scalar_raw_type=2
                 AND @invoices_volumes_wire_lexeme COLLATE Latin1_General_100_BIN2
                    <>@invoices_volumes_raw COLLATE Latin1_General_100_BIN2)
             OR @invoices_volumes_lexeme COLLATE Latin1_General_100_BIN2 LIKE N'%[^0-9]%'
             OR TRY_CONVERT(INT,@invoices_volumes_lexeme) IS NULL
             OR TRY_CONVERT(INT,@invoices_volumes_lexeme)<>@invoices_volumes_typed)
            THROW 52204,N'INVOICES_VOLUMES_INVALID: léxico ASCII, overflow ou valor divergente.',1;

        DECLARE @taxed_weight_parse_state NVARCHAR(16)=CASE @taxed_weight_presence
            WHEN N'ABSENT' THEN N'NOT_PRESENT' WHEN N'NULL' THEN N'EXPLICIT_NULL'
            WHEN N'VALUE' THEN N'VALID' ELSE N'INVALID' END;
        DECLARE @invoices_value_parse_state NVARCHAR(16)=CASE @invoices_value_presence
            WHEN N'ABSENT' THEN N'NOT_PRESENT' WHEN N'NULL' THEN N'EXPLICIT_NULL'
            WHEN N'VALUE' THEN N'VALID' ELSE N'INVALID' END;
        DECLARE @total_parse_state NVARCHAR(16)=CASE @total_presence
            WHEN N'ABSENT' THEN N'NOT_PRESENT' WHEN N'NULL' THEN N'EXPLICIT_NULL'
            WHEN N'VALUE' THEN N'VALID' ELSE N'INVALID' END;
        DECLARE @decimal_binding TABLE(
            field_name NVARCHAR(32) COLLATE Latin1_General_100_BIN2 NOT NULL PRIMARY KEY,
            presence NVARCHAR(8) COLLATE Latin1_General_100_BIN2 NOT NULL,
            scalar_raw NVARCHAR(MAX) NULL,
            typed_value DECIMAL(38,9) NULL,
            scalar_raw_value NVARCHAR(MAX) NULL,
            scalar_raw_type INT NULL,
            envelope_raw_value NVARCHAR(MAX) NULL,
            envelope_raw_type INT NULL,
            raw_wire_lexeme NVARCHAR(MAX) NULL
        );
        INSERT @decimal_binding(field_name,presence,scalar_raw,typed_value) VALUES
            (N'taxed_weight',@taxed_weight_presence,@taxed_weight_raw,@taxed_weight_typed),
            (N'invoices_value',@invoices_value_presence,@invoices_value_raw,@invoices_value_typed),
            (N'total',@total_presence,@total_raw,@total_typed);
        IF EXISTS(SELECT 1 FROM @decimal_binding binding
                  WHERE binding.scalar_raw IS NOT NULL
                    AND ISJSON(CONCAT(N'[',binding.scalar_raw,N']'))<>1)
            THROW 52205,N'DECIMAL_INVALID: raw não é um token JSON íntegro.',1;
        UPDATE binding SET
            envelope_raw_value=evidence.raw_value,
            envelope_raw_type=evidence.raw_type,
            raw_wire_lexeme=evidence.raw_wire_lexeme
        FROM @decimal_binding binding
        CROSS APPLY(
            SELECT
                MAX(CASE WHEN property.[key]=N'raw' THEN property.[value] END) AS raw_value,
                MAX(CASE WHEN property.[key]=N'raw' THEN property.[type] END) AS raw_type,
                MAX(CASE WHEN property.[key]=N'rawWireLexeme'
                         THEN property.[value] END) AS raw_wire_lexeme
            FROM OPENJSON(JSON_QUERY(@field_presence_json,
                CONCAT(N'$.fields.',binding.field_name))) property
        ) evidence;
        UPDATE binding SET
            scalar_raw_value=scalar_raw.[value],scalar_raw_type=scalar_raw.[type]
        FROM @decimal_binding binding
        CROSS APPLY OPENJSON(CONCAT(N'[',binding.scalar_raw,N']')) scalar_raw
        WHERE binding.scalar_raw IS NOT NULL;
        IF EXISTS(
            SELECT 1 FROM @decimal_binding binding
            WHERE (binding.presence IN(N'ABSENT',N'NULL')
                    AND (binding.scalar_raw IS NOT NULL OR binding.typed_value IS NOT NULL))
                OR (binding.presence=N'VALUE'
                    AND (binding.scalar_raw IS NULL OR binding.typed_value IS NULL
                      OR binding.scalar_raw_type NOT IN(1,2)
                      OR binding.scalar_raw_type<>binding.envelope_raw_type
                      OR (binding.scalar_raw_type=1
                        AND binding.scalar_raw_value COLLATE Latin1_General_100_BIN2
                           <>binding.envelope_raw_value COLLATE Latin1_General_100_BIN2)
                      OR (binding.scalar_raw_type=2
                        AND (binding.raw_wire_lexeme COLLATE Latin1_General_100_BIN2
                              <>binding.scalar_raw COLLATE Latin1_General_100_BIN2
                          OR TRY_CONVERT(DECIMAL(38,9),binding.envelope_raw_value) IS NULL
                          OR TRY_CONVERT(DECIMAL(38,9),binding.envelope_raw_value)
                              <>binding.typed_value))))
        )
            THROW 52205,N'DECIMAL_INVALID: presença, raw ou typed divergentes.',1;

        DECLARE @taxed_weight_wire_lexeme NVARCHAR(MAX),
                @invoices_value_wire_lexeme NVARCHAR(MAX),
                @total_wire_lexeme NVARCHAR(MAX);
        SELECT @taxed_weight_wire_lexeme=raw_wire_lexeme
        FROM @decimal_binding WHERE field_name=N'taxed_weight';
        SELECT @invoices_value_wire_lexeme=raw_wire_lexeme
        FROM @decimal_binding WHERE field_name=N'invoices_value';
        SELECT @total_wire_lexeme=raw_wire_lexeme
        FROM @decimal_binding WHERE field_name=N'total';
        DECLARE @taxed_weight_lexeme NVARCHAR(MAX)=CASE
            WHEN @taxed_weight_wire_lexeme NOT IN(
                    N'NOT_APPLICABLE_NON_NUMERIC_TOKEN',
                    N'UNAVAILABLE_AFTER_JSON_TREE_NORMALIZATION')
              THEN @taxed_weight_wire_lexeme
            WHEN LEFT(@taxed_weight_raw,1)=N'"' AND RIGHT(@taxed_weight_raw,1)=N'"'
              AND DATALENGTH(@taxed_weight_raw)>=4
              THEN SUBSTRING(@taxed_weight_raw,2,LEN(@taxed_weight_raw)-2)
            ELSE @taxed_weight_raw END;
        DECLARE @invoices_value_lexeme NVARCHAR(MAX)=CASE
            WHEN @invoices_value_wire_lexeme NOT IN(
                    N'NOT_APPLICABLE_NON_NUMERIC_TOKEN',
                    N'UNAVAILABLE_AFTER_JSON_TREE_NORMALIZATION')
              THEN @invoices_value_wire_lexeme
            WHEN LEFT(@invoices_value_raw,1)=N'"' AND RIGHT(@invoices_value_raw,1)=N'"'
              AND DATALENGTH(@invoices_value_raw)>=4
              THEN SUBSTRING(@invoices_value_raw,2,LEN(@invoices_value_raw)-2)
            ELSE @invoices_value_raw END;
        DECLARE @total_lexeme NVARCHAR(MAX)=CASE
            WHEN @total_wire_lexeme NOT IN(
                    N'NOT_APPLICABLE_NON_NUMERIC_TOKEN',
                    N'UNAVAILABLE_AFTER_JSON_TREE_NORMALIZATION')
              THEN @total_wire_lexeme
            WHEN LEFT(@total_raw,1)=N'"' AND RIGHT(@total_raw,1)=N'"'
              AND DATALENGTH(@total_raw)>=4
              THEN SUBSTRING(@total_raw,2,LEN(@total_raw)-2)
            ELSE @total_raw END;
        DECLARE @decimal_lexemes TABLE(
            field_name NVARCHAR(32) COLLATE Latin1_General_100_BIN2 NOT NULL,
            presence NVARCHAR(8) COLLATE Latin1_General_100_BIN2 NOT NULL,
            lexeme NVARCHAR(MAX) NULL,
            typed_value DECIMAL(38,9) NULL
        );
        INSERT @decimal_lexemes VALUES
            (N'taxed_weight',@taxed_weight_presence,@taxed_weight_lexeme,@taxed_weight_typed),
            (N'invoices_value',@invoices_value_presence,@invoices_value_lexeme,@invoices_value_typed),
            (N'total',@total_presence,@total_lexeme,@total_typed);
        IF EXISTS(
            SELECT 1 FROM @decimal_lexemes value
            CROSS APPLY(SELECT CASE WHEN LEFT(value.lexeme,1)=N'-'
                                    THEN SUBSTRING(value.lexeme,2,LEN(value.lexeme)-1)
                                    ELSE value.lexeme END AS unsigned_lexeme) unsigned_value
            CROSS APPLY(SELECT CHARINDEX(N'.',unsigned_value.unsigned_lexeme) AS dot_position,
                               LEN(unsigned_value.unsigned_lexeme)
                                 -LEN(REPLACE(unsigned_value.unsigned_lexeme,N'.',N'')) AS dot_count)
                punctuation
            WHERE value.presence=N'VALUE'
              AND (value.lexeme IS NULL OR value.lexeme=N''
                OR value.lexeme COLLATE Latin1_General_100_BIN2 LIKE N'%[^0-9.-]%'
                OR value.lexeme COLLATE Latin1_General_100_BIN2 LIKE N'%-%-%'
                OR CHARINDEX(N'-',value.lexeme)>1
                OR unsigned_value.unsigned_lexeme=N''
                OR punctuation.dot_count>1 OR punctuation.dot_position=1
                OR (punctuation.dot_position>0
                    AND punctuation.dot_position=LEN(unsigned_value.unsigned_lexeme))
                OR (punctuation.dot_position>0
                    AND LEN(unsigned_value.unsigned_lexeme)-punctuation.dot_position>9)
                OR LEN(REPLACE(unsigned_value.unsigned_lexeme,N'.',N''))>38
                OR TRY_CONVERT(DECIMAL(38,9),value.lexeme) IS NULL
                OR TRY_CONVERT(DECIMAL(38,9),value.lexeme)<>value.typed_value)
        ) THROW 52205,N'DECIMAL_INVALID: léxico ASCII, precisão, escala ou typed divergente.',1;

        DECLARE @status_envelope_raw_value NVARCHAR(MAX),@status_envelope_raw_type INT;
        SELECT @status_envelope_raw_value=raw_property.[value],
               @status_envelope_raw_type=raw_property.[type]
        FROM OPENJSON(JSON_QUERY(@field_presence_json,N'$.fields.fit_fln_status')) raw_property
        WHERE raw_property.[key]=N'raw';
        IF @status_normalized IS NULL OR @status_terminal IS NULL
           OR (@status_presence IN(N'ABSENT',N'NULL')
              AND (@status_raw IS NOT NULL OR @status_normalized<>N'sem_status'))
           OR (@status_presence=N'VALUE'
              AND (@status_raw IS NULL OR @status_envelope_raw_type<>1
                OR @status_raw COLLATE Latin1_General_100_BIN2
                   <>@status_envelope_raw_value COLLATE Latin1_General_100_BIN2))
           OR (@status_presence=N'VALUE'
              AND @status_normalized COLLATE Latin1_General_100_BIN2<>
                  CASE WHEN LTRIM(RTRIM(@status_raw))=N'' THEN N'sem_status'
                       ELSE LOWER(LTRIM(RTRIM(@status_raw))) END COLLATE Latin1_General_100_BIN2)
           OR (@status_normalized IN(N'finished',N'delivered',N'canceled',N'cancelled')
               AND @status_terminal<>1)
           OR (@status_normalized NOT IN(N'finished',N'delivered',N'canceled',N'cancelled')
               AND @status_terminal<>0)
            THROW 52206,N'STATUS_INVALID: status de Localização diverge do catálogo exato.',1;

        IF @status_branch_nickname_provenance<>N'UNSOURCED_LEGACY'
            THROW 52207,N'STATUS_BRANCH_NICKNAME deve permanecer UNSOURCED_LEGACY.',1;
    END;

    SET XACT_ABORT ON;
    BEGIN TRANSACTION;
    DECLARE @entity_name NVARCHAR(128),@source_instance NVARCHAR(128),@tenant_scope NVARCHAR(128);
    SELECT @entity_name=partition.entity_name,@source_instance=partition.source_instance,
           @tenant_scope=partition.tenant_scope
    FROM ctl.execution_attempt attempt WITH(UPDLOCK,HOLDLOCK)
    JOIN ctl.execution_partition partition WITH(HOLDLOCK)
      ON partition.partition_id=attempt.partition_id
    WHERE attempt.execution_id=@execution_id;
    IF @entity_name COLLATE Latin1_General_100_BIN2<>N'localizacao_cargas'
        THROW 52208,N'A execução não pertence a Localização de Cargas.',1;
    IF UPPER(@source_instance) IN(N'DEFAULT',N'GLOBAL',N'SINGLETON')
       OR UPPER(@tenant_scope) IN(N'DEFAULT',N'GLOBAL',N'SINGLETON')
        THROW 52209,N'Localização exige source_instance/tenant_scope explícitos.',1;

    -- Mesmo QUARANTINE recebe fingerprints e sidecar imutável: o kernel genérico não
    -- carrega payload/raw/presença e, isoladamente, seria evidência insuficiente.
    DECLARE @attribute_hash CHAR(64)=
        LOWER(CONVERT(CHAR(64),HASHBYTES('SHA2_256',CONVERT(VARBINARY(MAX),CONCAT(
            N'localizacao-cargas-envelope-v2|',COALESCE(@source_key,N'<NULL>'),N'|',
            @payload_json,N'|',@field_presence_json,N'|',@validation_disposition,N'|',
            COALESCE(@quarantine_reason_code,N'<NULL>'),N'|',
            @service_at_raw,N'|',CONVERT(NVARCHAR(33),@service_at_utc,126),N'|',
            COALESCE(@invoices_volumes_raw,N'<ABSENT_OR_NULL>'),N'|',
            COALESCE(CONVERT(NVARCHAR(32),@invoices_volumes_typed),N'<NULL>'),N'|',
            COALESCE(@taxed_weight_raw,N'<ABSENT_OR_NULL>'),N'|',
            COALESCE(CONVERT(NVARCHAR(64),@taxed_weight_typed),N'<NULL>'),N'|',
            COALESCE(@invoices_value_raw,N'<ABSENT_OR_NULL>'),N'|',
            COALESCE(CONVERT(NVARCHAR(64),@invoices_value_typed),N'<NULL>'),N'|',
            COALESCE(@total_raw,N'<ABSENT_OR_NULL>'),N'|',
            COALESCE(CONVERT(NVARCHAR(64),@total_typed),N'<NULL>'),N'|',
            COALESCE(@status_raw,N'<NULL>'),N'|',COALESCE(@status_normalized,N'<NULL>'),N'|',
            COALESCE(CONVERT(NVARCHAR(1),@status_terminal),N'<NULL>'),N'|',
            COALESCE(@status_branch_nickname_provenance,N'<NULL>')))),2));
    DECLARE @presence_hash CHAR(64)=
        LOWER(CONVERT(CHAR(64),HASHBYTES('SHA2_256',CONVERT(VARBINARY(MAX),
            CONCAT(N'localizacao-cargas-presence-v2|',@field_presence_json))),2));
    EXEC stg.usp_stage_record
        @execution_id,@input_batch_number,@input_record_ordinal,@source_key,
        N'localizacao-cargas-envelope-v2',@attribute_hash,
        N'localizacao-cargas-presence-v2',
        @presence_hash,@service_at_utc,@validation_disposition,@quarantine_reason_code,
        @observed_at_utc;

    DECLARE @stage_record_id BIGINT;
    SELECT @stage_record_id=stage_record_id
    FROM stg.execution_record WITH(UPDLOCK,HOLDLOCK)
    WHERE execution_id=@execution_id AND input_batch_number=@input_batch_number
      AND input_record_ordinal=@input_record_ordinal;
    IF EXISTS(
        SELECT 1 FROM stg.localizacao_carga_record WITH(UPDLOCK,HOLDLOCK)
        WHERE stage_record_id=@stage_record_id AND attribute_hash<>@attribute_hash
    ) THROW 52210,N'Retry de Localização possui conteúdo divergente.',1;
    IF NOT EXISTS(SELECT 1 FROM stg.localizacao_carga_record WHERE stage_record_id=@stage_record_id)
        INSERT stg.localizacao_carga_record(
            stage_record_id,execution_id,source_key,source_key_wire_type,payload_json,
            field_presence_json,validation_disposition,quarantine_reason_code,
            service_at_raw,service_at_utc,service_at_presence,
            service_at_parse_state,invoices_volumes_raw,invoices_volumes_typed,
            invoices_volumes_presence,invoices_volumes_parse_state,taxed_weight_raw,
            taxed_weight_typed,taxed_weight_presence,taxed_weight_parse_state,
            invoices_value_raw,invoices_value_typed,invoices_value_presence,
            invoices_value_parse_state,total_raw,total_typed,total_presence,total_parse_state,
            status_raw,status_normalized,status_presence,status_catalog_version,status_terminal,
            status_branch_nickname,status_branch_nickname_presence,
            status_branch_nickname_provenance,freight_candidate_presence,
            freight_candidate_provenance,freight_candidate_state,freshness_at_utc,
            freshness_origin,partition_basis,overlap_policy_version,attribute_hash,observed_at_utc
        ) VALUES(
            @stage_record_id,@execution_id,@source_key,
            CASE WHEN @source_key IS NULL THEN NULL ELSE N'INTEGER' END,@payload_json,
            @field_presence_json,@validation_disposition,@quarantine_reason_code,
            @service_at_raw,@service_at_utc,@service_at_presence,
            @service_at_parse_state,@invoices_volumes_raw,@invoices_volumes_typed,
            @invoices_volumes_presence,@invoices_volumes_parse_state,@taxed_weight_raw,
            @taxed_weight_typed,@taxed_weight_presence,@taxed_weight_parse_state,
            @invoices_value_raw,@invoices_value_typed,@invoices_value_presence,
            @invoices_value_parse_state,@total_raw,@total_typed,@total_presence,@total_parse_state,
            @status_raw,@status_normalized,@status_presence,
            CASE WHEN @validation_disposition=N'VALID'
                 THEN N'localizacao-cargas-status-v1' END,
            @status_terminal,NULL,
            CASE WHEN @validation_disposition=N'VALID' THEN N'ABSENT' END,
            CASE WHEN @validation_disposition=N'VALID' THEN N'UNSOURCED_LEGACY' END,
            CASE WHEN @validation_disposition=N'VALID' THEN N'VALUE' END,
            CASE WHEN @validation_disposition=N'VALID'
                 THEN N'LOCAL_ROOT_SOURCE_KEY_ONLY_NO_RELATION' END,
            CASE WHEN @validation_disposition=N'VALID'
                 THEN N'EXACT_SCOPED_ALIAS_CANDIDATE_UNRESOLVED' END,
            CASE WHEN @validation_disposition=N'VALID' THEN @service_at_utc END,
            CASE WHEN @validation_disposition=N'VALID' THEN N'SERVICE_AT' END,
            CASE WHEN @validation_disposition=N'VALID' THEN N'freights.service_at' END,
            CASE WHEN @validation_disposition=N'VALID'
                 THEN N'localizacao-cargas-service-at-overlap-v1' END,
            @attribute_hash,@observed_at_utc
        );
    IF @validation_disposition=N'QUARANTINE'
    BEGIN
        COMMIT TRANSACTION;
        RETURN;
    END;
    COMMIT TRANSACTION;
END;
GO

CREATE OR ALTER PROCEDURE core.usp_apply_reconcile_publish_coletas
    @execution_id UNIQUEIDENTIFIER,
    @contract_version NVARCHAR(MAX),
    @contract_fingerprint NVARCHAR(MAX),
    @configuration_version NVARCHAR(MAX),
    @configuration_fingerprint NVARCHAR(MAX)
AS
BEGIN
    -- B54: validate current SQL authority consumption before any effect/read.
    DECLARE @b54_actual NVARCHAR(MAX)=(SELECT LOWER(COALESCE(CONVERT(NVARCHAR(36),@execution_id),N'')) AS [execution],@contract_version AS [contractVersion],@contract_fingerprint AS [contractHash],@configuration_version AS [configurationVersion],@configuration_fingerprint AS [configurationHash] FOR JSON PATH,WITHOUT_ARRAY_WRAPPER,INCLUDE_NULL_VALUES);
    IF ctl.fn_runtime_consumed_scope(@b54_actual,1,NULL)<>1 THROW 52500,N'RUNTIME_DURABLE_CONSUMER_FENCE_REQUIRED',1;

    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    BEGIN TRANSACTION;

    DECLARE @environment_name NVARCHAR(32), @source_instance NVARCHAR(128),
            @tenant_scope NVARCHAR(128), @entity_name NVARCHAR(128);
    -- P02R BEGIN LOCK: mesma ordem application lock -> row fence do recovery/kernel.
    SELECT @environment_name=p.environment_name,@source_instance=p.source_instance,
        @tenant_scope=p.tenant_scope,@entity_name=p.entity_name
    FROM ctl.execution_attempt a JOIN ctl.execution_partition p ON p.partition_id=a.partition_id
    WHERE a.execution_id=@execution_id;
    DECLARE @runtime_lock_payload NVARCHAR(2000)=CONCAT(DATALENGTH(@environment_name),N':',@environment_name,N'|',
        DATALENGTH(@source_instance),N':',@source_instance,N'|',DATALENGTH(@tenant_scope),N':',@tenant_scope,N'|',
        DATALENGTH(@entity_name),N':',@entity_name);
    DECLARE @runtime_lock_resource NVARCHAR(255)=N'V2_APPLY_'+CONVERT(NVARCHAR(64),HASHBYTES('SHA2_256',@runtime_lock_payload),2);
    DECLARE @runtime_lock INT;
    EXEC @runtime_lock=sys.sp_getapplock @Resource=@runtime_lock_resource,@LockMode=N'Exclusive',
        @LockOwner=N'Transaction',@LockTimeout=10000,@DbPrincipal=N'public';
    IF @runtime_lock<0 THROW 52312,N'RUNTIME_COLETA_LOCK_UNAVAILABLE',1;
    -- P02R END LOCK

    SELECT @environment_name = partition.environment_name,
           @source_instance = partition.source_instance,
           @tenant_scope = partition.tenant_scope,
           @entity_name = partition.entity_name
    FROM ctl.execution_attempt AS attempt WITH (UPDLOCK, HOLDLOCK)
    INNER JOIN ctl.execution_partition AS partition WITH (UPDLOCK, HOLDLOCK)
        ON partition.partition_id = attempt.partition_id
    WHERE attempt.execution_id = @execution_id;
    IF @entity_name COLLATE Latin1_General_100_BIN2 <> N'coletas'
        THROW 51811, N'A aplicação não pertence à vertical de Coletas.', 1;
    IF NOT EXISTS (SELECT 1 FROM ctl.coleta_promotion_result WITH (UPDLOCK, HOLDLOCK)
                   WHERE execution_id = @execution_id AND validation_state = N'PASSED')
        THROW 51812, N'Candidate set tipado de Coletas não está apto.', 1;
    -- P02R BEGIN READBACK: nunca recompõe COL-03 a partir do current alterado.
    IF EXISTS(SELECT 1 FROM ctl.execution_attempt WHERE execution_id=@execution_id AND current_state=N'PUBLISHED')
    BEGIN
        IF NOT EXISTS(SELECT 1 FROM ctl.runtime_coleta_publication_receipt WITH(HOLDLOCK) WHERE execution_id=@execution_id
            AND receipt_hash=ctl.fn_runtime_coleta_publication_hash(@execution_id))
            THROW 52313,N'RUNTIME_COLETA_HISTORICAL_RECEIPT_REQUIRED',1;
        DECLARE @prior_generic TABLE(execution_id UNIQUEIDENTIFIER,candidate_rows BIGINT,inserted_rows BIGINT,
            updated_rows BIGINT,reactivated_rows BIGINT,noop_rows BIGINT,stale_noop_rows BIGINT,
            reconciled_at_utc DATETIME2(3),published_at_utc DATETIME2(3),
            incremental_frontier_before_utc DATETIME2(3),incremental_frontier_after_utc DATETIME2(3));
        INSERT @prior_generic EXEC core.usp_apply_reconcile_publish_execution @execution_id,@contract_version,
            @contract_fingerprint,@configuration_version,@configuration_fingerprint;
        COMMIT TRANSACTION;
        SELECT r.execution_id,r.candidate_rows,r.inserted_rows,r.updated_rows,r.reactivated_rows,
            r.noop_rows,r.stale_noop_rows,r.reconciled_at_utc,r.published_at_utc,
            r.incremental_frontier_before_utc,r.incremental_frontier_after_utc
        FROM ctl.runtime_coleta_publication_receipt r WHERE r.execution_id=@execution_id;
        RETURN;
    END;
    -- P02R END READBACK
    IF EXISTS (
        SELECT 1 FROM stg.execution_candidate AS candidate
        INNER JOIN stg.coleta_record AS typed ON typed.stage_record_id = candidate.winner_stage_record_id
        INNER JOIN core.coleta AS current_record WITH (UPDLOCK, HOLDLOCK, INDEX(UQ_core_coleta_source))
          ON current_record.environment_name = @environment_name
         AND current_record.source_instance = @source_instance
         AND current_record.tenant_scope = @tenant_scope
         AND current_record.entity_name = @entity_name
         AND current_record.source_key = typed.source_key
        WHERE candidate.execution_id = @execution_id AND typed.sequence_code_presence = N'VALUE'
          AND current_record.sequence_code_presence = N'VALUE'
          AND current_record.sequence_code_json <> typed.sequence_code_json
    )
        THROW 51813, N'Alteração de alias exige evidência aprovada.', 1;

    DECLARE @common_result TABLE (
        execution_id UNIQUEIDENTIFIER NOT NULL, candidate_rows BIGINT NOT NULL,
        inserted_rows BIGINT NOT NULL, updated_rows BIGINT NOT NULL, reactivated_rows BIGINT NOT NULL,
        noop_rows BIGINT NOT NULL, stale_noop_rows BIGINT NOT NULL,
        reconciled_at_utc DATETIME2(3) NOT NULL, published_at_utc DATETIME2(3) NOT NULL,
        incremental_frontier_before_utc DATETIME2(3) NULL, incremental_frontier_after_utc DATETIME2(3) NULL
    );
    INSERT INTO @common_result
    EXEC core.usp_apply_reconcile_publish_execution @execution_id, @contract_version,
         @contract_fingerprint, @configuration_version, @configuration_fingerprint;

    DECLARE @now_utc DATETIME2(3) = SYSUTCDATETIME();
    DECLARE @plan TABLE (
        source_key NVARCHAR(256) COLLATE Latin1_General_100_BIN2 NOT NULL PRIMARY KEY,
        record_state_id BIGINT NOT NULL, coleta_id BIGINT NULL, stage_record_id BIGINT NOT NULL,
        application_disposition NVARCHAR(24) COLLATE Latin1_General_100_BIN2 NOT NULL,
        apply_values BIT NOT NULL
    );
    INSERT INTO @plan (source_key, record_state_id, coleta_id, stage_record_id,
                       application_disposition, apply_values)
    SELECT candidate.source_key, generic_application.record_state_id, current_record.coleta_id,
           typed.stage_record_id,
           CASE WHEN current_record.coleta_id IS NULL THEN N'INSERTED'
                WHEN current_record.terminal = 0 AND typed.terminal = 1 THEN N'UPDATED'
                WHEN generic_application.application_disposition = N'STALE_NO_OP' THEN N'STALE_NO_OP'
                WHEN current_record.terminal = 1 AND typed.terminal = 0 THEN N'NO_OP'
                WHEN current_record.attribute_hash = typed.attribute_hash THEN N'NO_OP'
                ELSE N'UPDATED' END,
           CASE WHEN current_record.coleta_id IS NULL THEN 1
                WHEN current_record.terminal = 0 AND typed.terminal = 1 THEN 1
                WHEN generic_application.application_disposition = N'STALE_NO_OP' THEN 0
                WHEN current_record.terminal = 1 AND typed.terminal = 0 THEN 0
                WHEN current_record.attribute_hash = typed.attribute_hash THEN 0 ELSE 1 END
    FROM stg.execution_candidate AS candidate
    INNER JOIN stg.coleta_record AS typed ON typed.stage_record_id = candidate.winner_stage_record_id
    INNER JOIN recon.execution_candidate_application AS generic_application
      ON generic_application.execution_id = candidate.execution_id
     AND generic_application.source_key = candidate.source_key
    LEFT JOIN core.coleta AS current_record WITH (UPDLOCK, HOLDLOCK, INDEX(UQ_core_coleta_source))
      ON current_record.environment_name = @environment_name
     AND current_record.source_instance = @source_instance
     AND current_record.tenant_scope = @tenant_scope
     AND current_record.entity_name = @entity_name
     AND current_record.source_key = candidate.source_key
    WHERE candidate.execution_id = @execution_id;
    IF (SELECT COUNT_BIG(*) FROM @plan) <> (SELECT candidate_rows FROM @common_result)
        THROW 51814, N'O plano tipado não cobre todo o candidate set de Coletas.', 1;

    INSERT INTO core.coleta (
        record_state_id, environment_name, source_instance, tenant_scope, entity_name, source_key,
        source_key_wire_type, sequence_code_presence, sequence_code_json, payload_json,
        field_presence_json, relation_candidates_json, status_raw, status_code, status_label,
        status_catalog_version, terminal, occurrence_action, attempt_count, freshness_raw,
        freshness_at_utc, freshness_origin, attribute_fingerprint_version, attribute_hash,
        state_fingerprint_version, state_hash, active, first_seen_execution_id, last_seen_execution_id,
        last_changed_execution_id, first_seen_at_utc, last_seen_at_utc, last_changed_at_utc
    )
    SELECT planned.record_state_id, @environment_name, @source_instance, @tenant_scope, @entity_name,
           typed.source_key, typed.source_key_wire_type, typed.sequence_code_presence,
           typed.sequence_code_json, typed.payload_json, typed.field_presence_json,
           typed.relation_candidates_json, typed.status_raw, typed.status_code, typed.status_label,
           typed.status_catalog_version, typed.terminal, typed.occurrence_action, typed.attempt_count,
           typed.freshness_raw, typed.freshness_at_utc, typed.freshness_origin,
           typed.attribute_fingerprint_version, typed.attribute_hash, N'coletas-state-v1',
            LOWER(CONVERT(CHAR(64), HASHBYTES('SHA2_256', CONVERT(VARBINARY(MAX), CONCAT(
                N'coletas-state-v1|', typed.attribute_hash, N'|active=1'
            ))), 2)), 1, @execution_id, @execution_id, @execution_id, @now_utc, @now_utc, @now_utc
    FROM @plan AS planned INNER JOIN stg.coleta_record AS typed
      ON typed.stage_record_id = planned.stage_record_id
    WHERE planned.application_disposition = N'INSERTED';

    UPDATE current_record
    SET sequence_code_presence = CASE WHEN typed.sequence_code_presence = N'ABSENT'
                                      THEN current_record.sequence_code_presence
                                      ELSE typed.sequence_code_presence END,
        sequence_code_json = CASE WHEN typed.sequence_code_presence = N'ABSENT'
                                  THEN current_record.sequence_code_json
                                  ELSE typed.sequence_code_json END,
        payload_json = typed.payload_json,
        field_presence_json = typed.field_presence_json, relation_candidates_json = typed.relation_candidates_json,
        status_raw = CASE WHEN JSON_VALUE(typed.field_presence_json, N'$.status') = N'ABSENT'
                          THEN current_record.status_raw ELSE typed.status_raw END,
        status_code = CASE WHEN JSON_VALUE(typed.field_presence_json, N'$.status') = N'ABSENT'
                           THEN current_record.status_code ELSE typed.status_code END,
        status_label = CASE WHEN JSON_VALUE(typed.field_presence_json, N'$.status') = N'ABSENT'
                            THEN current_record.status_label ELSE typed.status_label END,
        status_catalog_version = CASE WHEN JSON_VALUE(typed.field_presence_json, N'$.status') = N'ABSENT'
                                      THEN current_record.status_catalog_version
                                      ELSE typed.status_catalog_version END,
        terminal = CASE WHEN JSON_VALUE(typed.field_presence_json, N'$.status') = N'ABSENT'
                        THEN current_record.terminal ELSE typed.terminal END,
        occurrence_action = CASE WHEN JSON_VALUE(typed.field_presence_json, N'$.status') = N'ABSENT'
                                 THEN current_record.occurrence_action ELSE typed.occurrence_action END,
        attempt_count = CASE WHEN JSON_VALUE(typed.field_presence_json, N'$.status') = N'ABSENT'
                             THEN current_record.attempt_count ELSE typed.attempt_count END,
        freshness_raw = COALESCE(typed.freshness_raw, current_record.freshness_raw),
        freshness_at_utc = CASE WHEN typed.freshness_origin = N'UNAVAILABLE'
                                THEN current_record.freshness_at_utc ELSE typed.freshness_at_utc END,
        freshness_origin = CASE WHEN typed.freshness_origin = N'UNAVAILABLE'
                                THEN current_record.freshness_origin ELSE typed.freshness_origin END,
        attribute_fingerprint_version = typed.attribute_fingerprint_version,
        attribute_hash = typed.attribute_hash,
        state_hash = LOWER(CONVERT(CHAR(64), HASHBYTES('SHA2_256', CONVERT(VARBINARY(MAX), CONCAT(
            N'coletas-state-v1|', typed.attribute_hash, N'|active=1'
        ))), 2)), last_seen_execution_id = @execution_id, last_changed_execution_id = @execution_id,
        last_seen_at_utc = @now_utc, last_changed_at_utc = @now_utc
    FROM core.coleta AS current_record INNER JOIN @plan AS planned
      ON planned.coleta_id = current_record.coleta_id
    INNER JOIN stg.coleta_record AS typed ON typed.stage_record_id = planned.stage_record_id
    WHERE planned.application_disposition = N'UPDATED' AND planned.apply_values = 1;

    UPDATE current_record SET last_seen_execution_id = @execution_id, last_seen_at_utc = @now_utc
    FROM core.coleta AS current_record INNER JOIN @plan AS planned
      ON planned.coleta_id = current_record.coleta_id
    WHERE planned.application_disposition IN (N'NO_OP', N'STALE_NO_OP');

    UPDATE planned SET coleta_id = current_record.coleta_id
    FROM @plan AS planned INNER JOIN core.coleta AS current_record
      ON current_record.environment_name = @environment_name
     AND current_record.source_instance = @source_instance
     AND current_record.tenant_scope = @tenant_scope
     AND current_record.entity_name = @entity_name AND current_record.source_key = planned.source_key;
    IF EXISTS (SELECT 1 FROM @plan WHERE coleta_id IS NULL)
        THROW 51815, N'A aplicação não resolveu canonical_id de Coletas.', 1;

    INSERT INTO ref.coleta_sequence_code_alias (
        coleta_id, sequence_code_json, observed_execution_id, valid_from_utc, valid_to_utc, provenance
    )
    SELECT planned.coleta_id, typed.sequence_code_json, @execution_id, @now_utc, NULL,
           N'dataexport-6908-v02'
    FROM @plan AS planned INNER JOIN stg.coleta_record AS typed
      ON typed.stage_record_id = planned.stage_record_id
    WHERE typed.sequence_code_presence = N'VALUE'
      AND NOT EXISTS (SELECT 1 FROM ref.coleta_sequence_code_alias AS alias
                      WHERE alias.coleta_id = planned.coleta_id AND alias.valid_to_utc IS NULL);

    INSERT INTO recon.coleta_root_presence_observation (
        execution_id, coleta_id, observed_at_utc, snapshot_completeness, absence_evaluation
    )
    SELECT @execution_id, coleta_id, @now_utc, N'BLOCKED_NO_COMPLETENESS_PROOF', N'NOT_EVALUATED'
    FROM @plan;

    DECLARE @inserted_rows BIGINT = (SELECT COUNT_BIG(*) FROM @plan WHERE application_disposition = N'INSERTED');
    DECLARE @updated_rows BIGINT = (SELECT COUNT_BIG(*) FROM @plan WHERE application_disposition = N'UPDATED');
    DECLARE @noop_rows BIGINT = (SELECT COUNT_BIG(*) FROM @plan WHERE application_disposition IN (N'NO_OP', N'STALE_NO_OP'));
    DECLARE @stale_noop_rows BIGINT = (SELECT COUNT_BIG(*) FROM @plan WHERE application_disposition = N'STALE_NO_OP');
    -- P02R BEGIN RECEIPT: mesma transação dos efeitos tipados, antes de qualquer ack.
    INSERT ctl.runtime_coleta_publication_receipt
    SELECT r.*,LOWER(CONVERT(CHAR(64),HASHBYTES('SHA2_256',CONCAT(
        ctl.fn_runtime_recovery_identity(r.execution_id),N'|',r.candidate_rows,N'|',
        r.inserted_rows,N'|',r.updated_rows,N'|',r.reactivated_rows,N'|',
        r.noop_rows,N'|',r.stale_noop_rows,N'|',
        CONVERT(NVARCHAR(23),r.reconciled_at_utc,126),N'|',CONVERT(NVARCHAR(23),r.published_at_utc,126),N'|',
        CONVERT(NVARCHAR(23),r.incremental_frontier_before_utc,126),N'|',
        CONVERT(NVARCHAR(23),r.incremental_frontier_after_utc,126))),2))
    FROM (SELECT @execution_id AS execution_id,candidate_rows,@inserted_rows AS inserted_rows,
        @updated_rows AS updated_rows,CAST(0 AS BIGINT) AS reactivated_rows,@noop_rows AS noop_rows,
        @stale_noop_rows AS stale_noop_rows,reconciled_at_utc,published_at_utc,
        incremental_frontier_before_utc,incremental_frontier_after_utc FROM @common_result) r;
    -- P02R END RECEIPT
    EXEC recon.usp_capture_runtime_vertical_output @execution_id;
    COMMIT TRANSACTION;
    SELECT @execution_id AS execution_id, (SELECT candidate_rows FROM @common_result) AS candidate_rows,
           @inserted_rows AS inserted_rows, @updated_rows AS updated_rows,
           CAST(0 AS BIGINT) AS reactivated_rows, @noop_rows AS noop_rows,
           @stale_noop_rows AS stale_noop_rows, reconciled_at_utc, published_at_utc,
           incremental_frontier_before_utc, incremental_frontier_after_utc FROM @common_result;
END;
GO

CREATE OR ALTER PROCEDURE core.usp_apply_reconcile_publish_fretes
    @execution_id UNIQUEIDENTIFIER,
    @contract_version NVARCHAR(MAX),
    @contract_fingerprint NVARCHAR(MAX),
    @configuration_version NVARCHAR(MAX),
    @configuration_fingerprint NVARCHAR(MAX)
AS
BEGIN
    -- B54: validate current SQL authority consumption before any effect/read.
    DECLARE @b54_actual NVARCHAR(MAX)=(SELECT LOWER(COALESCE(CONVERT(NVARCHAR(36),@execution_id),N'')) AS [execution],@contract_version AS [contractVersion],@contract_fingerprint AS [contractHash],@configuration_version AS [configurationVersion],@configuration_fingerprint AS [configurationHash] FOR JSON PATH,WITHOUT_ARRAY_WRAPPER,INCLUDE_NULL_VALUES);
    IF ctl.fn_runtime_consumed_scope(@b54_actual,1,NULL)<>1 THROW 52500,N'RUNTIME_DURABLE_CONSUMER_FENCE_REQUIRED',1;

    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    BEGIN TRANSACTION;
    IF NOT EXISTS(
        SELECT 1 FROM ctl.frete_promotion_result WITH(UPDLOCK,HOLDLOCK)
        WHERE execution_id=@execution_id AND validation_state=N'PASSED'
    ) THROW 52140,N'Candidate set de Fretes não está apto.',1;
    DECLARE @environment_name NVARCHAR(32),@source_instance NVARCHAR(128),
            @tenant_scope NVARCHAR(128),@entity_name NVARCHAR(128);
    SELECT @environment_name=partition.environment_name,
           @source_instance=partition.source_instance,@tenant_scope=partition.tenant_scope,
           @entity_name=partition.entity_name
    FROM ctl.execution_attempt attempt WITH(UPDLOCK,HOLDLOCK)
    JOIN ctl.execution_partition partition WITH(HOLDLOCK)
      ON partition.partition_id=attempt.partition_id
    WHERE attempt.execution_id=@execution_id;
    IF @entity_name<>N'fretes' OR UPPER(@source_instance) IN(N'DEFAULT',N'GLOBAL',N'SINGLETON')
       OR UPPER(@tenant_scope) IN(N'DEFAULT',N'GLOBAL',N'SINGLETON')
        THROW 52141,N'Escopo de promoção de Fretes inválido.',1;

    -- A mesma função de decisão: antigo=no-op, igual+mesmo hash=replay,
    -- igual+hash divergente=quarentena fail-closed, novo=promoção.
    IF EXISTS(
        SELECT 1
        FROM stg.execution_candidate candidate
        JOIN stg.frete_record typed_record
          ON typed_record.stage_record_id=candidate.winner_stage_record_id
        JOIN core.frete current_record WITH(UPDLOCK,HOLDLOCK,INDEX(UQ_core_frete_source))
          ON current_record.environment_name=@environment_name
         AND current_record.source_instance=@source_instance
         AND current_record.tenant_scope=@tenant_scope
         AND current_record.entity_name=N'fretes'
         AND current_record.source_key=candidate.source_key
        WHERE candidate.execution_id=@execution_id
          AND current_record.freshness_at_utc=typed_record.freshness_at_utc
          AND current_record.attribute_hash<>typed_record.attribute_hash
    ) THROW 52142,N'EQUAL_FRESHNESS_CONFLICT: Fretes exige quarentena.',1;

    DECLARE @effective TABLE(
        source_key NVARCHAR(256) COLLATE Latin1_General_100_BIN2 NOT NULL PRIMARY KEY,
        stage_record_id BIGINT NOT NULL,
        business_alias_json NVARCHAR(MAX) NOT NULL,
        status_raw NVARCHAR(255) NULL,status_code NVARCHAR(32) NULL,status_label NVARCHAR(64) NULL,
        terminal BIT NOT NULL,cte_finalizations_json NVARCHAR(MAX) NOT NULL,
        financial_json NVARCHAR(MAX) NOT NULL,relation_candidates_json NVARCHAR(MAX) NOT NULL
    );
    INSERT @effective(
        source_key,stage_record_id,business_alias_json,status_raw,status_code,status_label,
        terminal,cte_finalizations_json,financial_json,relation_candidates_json
    )
    SELECT candidate.source_key,typed_record.stage_record_id,
      CASE JSON_VALUE(typed_record.field_presence_json,N'$.corporation_sequence_number')
        WHEN N'ABSENT' THEN COALESCE(current_record.business_alias_json,typed_record.business_alias_json)
        ELSE typed_record.business_alias_json END,
      CASE
        WHEN current_record.terminal=1 AND typed_record.terminal=0 THEN current_record.status_raw
        WHEN JSON_VALUE(typed_record.field_presence_json,N'$.status')=N'ABSENT'
          THEN current_record.status_raw ELSE typed_record.status_raw END,
      CASE
        WHEN current_record.terminal=1 AND typed_record.terminal=0 THEN current_record.status_code
        WHEN JSON_VALUE(typed_record.field_presence_json,N'$.status')=N'ABSENT'
          THEN current_record.status_code ELSE typed_record.status_code END,
      CASE
        WHEN current_record.terminal=1 AND typed_record.terminal=0 THEN current_record.status_label
        WHEN JSON_VALUE(typed_record.field_presence_json,N'$.status')=N'ABSENT'
          THEN current_record.status_label ELSE typed_record.status_label END,
      CASE
        WHEN current_record.terminal=1 THEN CONVERT(BIT,1) ELSE typed_record.terminal END,
      CASE WHEN
          JSON_VALUE(typed_record.field_presence_json,N'$.cte')=N'ABSENT'
          AND JSON_VALUE(typed_record.field_presence_json,N'$.ctes')=N'ABSENT'
          AND JSON_VALUE(typed_record.field_presence_json,N'$.cte_key')=N'ABSENT'
          AND JSON_VALUE(typed_record.field_presence_json,N'$.finalizations')=N'ABSENT'
          AND JSON_VALUE(typed_record.field_presence_json,N'$.finalizacoes')=N'ABSENT'
        THEN COALESCE(current_record.cte_finalizations_json,typed_record.cte_finalizations_json)
        ELSE typed_record.cte_finalizations_json END,
      CASE WHEN
          JSON_VALUE(typed_record.field_presence_json,N'$.reference_number')=N'ABSENT'
          AND JSON_VALUE(typed_record.field_presence_json,N'$.total')=N'ABSENT'
          AND JSON_VALUE(typed_record.field_presence_json,N'$.cte')=N'ABSENT'
          AND JSON_VALUE(typed_record.field_presence_json,N'$.cte_key')=N'ABSENT'
        THEN COALESCE(current_record.financial_json,typed_record.financial_json)
        ELSE typed_record.financial_json END,
      CASE WHEN
          JSON_VALUE(typed_record.field_presence_json,N'$.fit_p_m_pck_sequence_code')=N'ABSENT'
        THEN COALESCE(current_record.relation_candidates_json,typed_record.relation_candidates_json)
        ELSE typed_record.relation_candidates_json END
    FROM stg.execution_candidate candidate
    JOIN stg.frete_record typed_record
      ON typed_record.stage_record_id=candidate.winner_stage_record_id
    LEFT JOIN core.frete current_record WITH(UPDLOCK,HOLDLOCK,INDEX(UQ_core_frete_source))
      ON current_record.environment_name=@environment_name
     AND current_record.source_instance=@source_instance
     AND current_record.tenant_scope=@tenant_scope
     AND current_record.entity_name=N'fretes'
     AND current_record.source_key=candidate.source_key
    WHERE candidate.execution_id=@execution_id;

    INSERT recon.frete_terminal_transition_observation(
        execution_id,frete_id,observed_status_raw,transition_action,observed_at_utc
    )
    SELECT @execution_id,current_record.frete_id,typed_record.status_raw,
           N'TERMINAL_REGRESSION_BLOCKED',SYSUTCDATETIME()
    FROM stg.execution_candidate candidate
    JOIN stg.frete_record typed_record
      ON typed_record.stage_record_id=candidate.winner_stage_record_id
    JOIN core.frete current_record WITH(UPDLOCK,HOLDLOCK,INDEX(UQ_core_frete_source))
      ON current_record.environment_name=@environment_name
     AND current_record.source_instance=@source_instance
     AND current_record.tenant_scope=@tenant_scope
     AND current_record.entity_name=N'fretes'
     AND current_record.source_key=candidate.source_key
    WHERE candidate.execution_id=@execution_id AND current_record.terminal=1
      AND typed_record.terminal=0
      AND JSON_VALUE(typed_record.field_presence_json,N'$.status')=N'VALUE'
      AND NOT EXISTS(
          SELECT 1 FROM recon.frete_terminal_transition_observation audit_record
          WHERE audit_record.execution_id=@execution_id
            AND audit_record.frete_id=current_record.frete_id
      );

    DECLARE @common_result TABLE(
        execution_id UNIQUEIDENTIFIER NOT NULL,candidate_rows BIGINT NOT NULL,
        inserted_rows BIGINT NOT NULL,updated_rows BIGINT NOT NULL,
        reactivated_rows BIGINT NOT NULL,noop_rows BIGINT NOT NULL,
        stale_noop_rows BIGINT NOT NULL,reconciled_at_utc DATETIME2(3) NOT NULL,
        published_at_utc DATETIME2(3) NOT NULL,
        incremental_frontier_before_utc DATETIME2(3) NULL,
        incremental_frontier_after_utc DATETIME2(3) NULL
    );
    INSERT @common_result EXEC core.usp_apply_reconcile_publish_execution
        @execution_id,@contract_version,@contract_fingerprint,
        @configuration_version,@configuration_fingerprint;

    INSERT core.frete(
        record_state_id,environment_name,source_instance,tenant_scope,entity_name,source_key,
        source_key_wire_type,payload_json,field_presence_json,business_alias_json,status_raw,
        status_code,status_label,status_catalog_version,terminal,freshness_evidence_json,
        freshness_at_utc,freshness_origin,service_at_utc,partition_basis,overlap_policy_version,
        cte_finalizations_json,financial_json,relation_candidates_json,attribute_hash,active,
        first_seen_execution_id,last_seen_execution_id,first_seen_at_utc,last_seen_at_utc
    )
    SELECT application.record_state_id,@environment_name,@source_instance,@tenant_scope,
        N'fretes',typed_record.source_key,N'INTEGER',typed_record.payload_json,
        typed_record.field_presence_json,effective.business_alias_json,effective.status_raw,
        effective.status_code,effective.status_label,N'fretes-status-v1',effective.terminal,
        typed_record.freshness_evidence_json,typed_record.freshness_at_utc,
        typed_record.freshness_origin,typed_record.service_at_utc,N'freights.service_at',
        N'fretes-service-at-overlap-v1',effective.cte_finalizations_json,
        effective.financial_json,effective.relation_candidates_json,typed_record.attribute_hash,
        CONVERT(BIT,1),@execution_id,@execution_id,SYSUTCDATETIME(),SYSUTCDATETIME()
    FROM stg.execution_candidate candidate
    JOIN stg.frete_record typed_record
      ON typed_record.stage_record_id=candidate.winner_stage_record_id
    JOIN @effective effective ON effective.source_key=candidate.source_key
    JOIN recon.execution_candidate_application application
      ON application.execution_id=candidate.execution_id
     AND application.source_key=candidate.source_key
    WHERE candidate.execution_id=@execution_id
      AND NOT EXISTS(
          SELECT 1 FROM core.frete current_record WITH(UPDLOCK,HOLDLOCK,INDEX(UQ_core_frete_source))
          WHERE current_record.environment_name=@environment_name
            AND current_record.source_instance=@source_instance
            AND current_record.tenant_scope=@tenant_scope
            AND current_record.entity_name=N'fretes'
            AND current_record.source_key=candidate.source_key
      );

    UPDATE current_record SET
        payload_json=typed_record.payload_json,
        field_presence_json=typed_record.field_presence_json,
        business_alias_json=effective.business_alias_json,status_raw=effective.status_raw,
        status_code=effective.status_code,status_label=effective.status_label,
        terminal=effective.terminal,freshness_evidence_json=typed_record.freshness_evidence_json,
        freshness_at_utc=typed_record.freshness_at_utc,
        freshness_origin=typed_record.freshness_origin,
        service_at_utc=COALESCE(typed_record.service_at_utc,current_record.service_at_utc),
        cte_finalizations_json=effective.cte_finalizations_json,
        financial_json=effective.financial_json,
        relation_candidates_json=effective.relation_candidates_json,
        attribute_hash=typed_record.attribute_hash,last_seen_execution_id=@execution_id,
        last_seen_at_utc=SYSUTCDATETIME()
    FROM core.frete current_record
    JOIN stg.execution_candidate candidate ON candidate.source_key=current_record.source_key
    JOIN stg.frete_record typed_record
      ON typed_record.stage_record_id=candidate.winner_stage_record_id
    JOIN @effective effective ON effective.source_key=candidate.source_key
    WHERE candidate.execution_id=@execution_id
      AND current_record.environment_name=@environment_name
      AND current_record.source_instance=@source_instance
      AND current_record.tenant_scope=@tenant_scope AND current_record.entity_name=N'fretes'
      AND typed_record.freshness_at_utc>current_record.freshness_at_utc;

    UPDATE current_record SET last_seen_execution_id=@execution_id,
        last_seen_at_utc=SYSUTCDATETIME()
    FROM core.frete current_record
    JOIN stg.execution_candidate candidate ON candidate.source_key=current_record.source_key
    JOIN recon.execution_candidate_application application
      ON application.execution_id=candidate.execution_id
     AND application.source_key=candidate.source_key
    WHERE candidate.execution_id=@execution_id
      AND current_record.environment_name=@environment_name
      AND current_record.source_instance=@source_instance
      AND current_record.tenant_scope=@tenant_scope AND current_record.entity_name=N'fretes'
      AND application.application_disposition IN(N'NO_OP',N'STALE_NO_OP');

    INSERT core.frete_performance(
        frete_id,performance_evidence_json,performance_at_utc,performance_origin,
        last_seen_execution_id,last_seen_at_utc
    )
    SELECT current_record.frete_id,performance_record.performance_evidence_json,
        performance_record.performance_at_utc,performance_record.performance_origin,
        @execution_id,SYSUTCDATETIME()
    FROM stg.execution_candidate candidate
    JOIN stg.frete_performance_observation performance_record
      ON performance_record.stage_record_id=candidate.winner_stage_record_id
    JOIN core.frete current_record WITH(UPDLOCK,HOLDLOCK,INDEX(UQ_core_frete_source))
      ON current_record.environment_name=@environment_name
     AND current_record.source_instance=@source_instance
     AND current_record.tenant_scope=@tenant_scope
     AND current_record.entity_name=N'fretes' AND current_record.source_key=candidate.source_key
    WHERE candidate.execution_id=@execution_id
      AND NOT EXISTS(
          SELECT 1 FROM core.frete_performance current_performance
          WHERE current_performance.frete_id=current_record.frete_id
      );

    UPDATE current_performance SET
        performance_evidence_json=performance_record.performance_evidence_json,
        performance_at_utc=performance_record.performance_at_utc,
        performance_origin=performance_record.performance_origin,
        last_seen_execution_id=@execution_id,last_seen_at_utc=SYSUTCDATETIME()
    FROM core.frete_performance current_performance
    JOIN core.frete current_record ON current_record.frete_id=current_performance.frete_id
    JOIN stg.execution_candidate candidate ON candidate.source_key=current_record.source_key
    JOIN stg.frete_record typed_record
      ON typed_record.stage_record_id=candidate.winner_stage_record_id
    JOIN stg.frete_performance_observation performance_record
      ON performance_record.stage_record_id=candidate.winner_stage_record_id
    WHERE candidate.execution_id=@execution_id
      AND current_record.environment_name=@environment_name
      AND current_record.source_instance=@source_instance
      AND current_record.tenant_scope=@tenant_scope AND current_record.entity_name=N'fretes'
      AND typed_record.freshness_at_utc>=current_record.freshness_at_utc
      AND (performance_record.official_presence<>N'ABSENT'
           OR performance_record.fallback_presence<>N'ABSENT');

    INSERT recon.frete_root_presence_observation(
        execution_id,frete_id,observed_at_utc,snapshot_completeness,absence_evaluation
    )
    SELECT @execution_id,current_record.frete_id,SYSUTCDATETIME(),
           N'BLOCKED_NO_COMPLETENESS_PROOF',N'NOT_EVALUATED_PRUNE_DISABLED'
    FROM core.frete current_record
    JOIN stg.execution_candidate candidate ON candidate.source_key=current_record.source_key
    WHERE candidate.execution_id=@execution_id
      AND current_record.environment_name=@environment_name
      AND current_record.source_instance=@source_instance
      AND current_record.tenant_scope=@tenant_scope AND current_record.entity_name=N'fretes'
      AND NOT EXISTS(
          SELECT 1 FROM recon.frete_root_presence_observation observation
          WHERE observation.execution_id=@execution_id
            AND observation.frete_id=current_record.frete_id
      );
    EXEC recon.usp_capture_runtime_vertical_output @execution_id;
    COMMIT TRANSACTION;
    SELECT execution_id,candidate_rows,inserted_rows,updated_rows,reactivated_rows,
           noop_rows,stale_noop_rows,reconciled_at_utc,published_at_utc,
           incremental_frontier_before_utc,incremental_frontier_after_utc
    FROM @common_result;
END;
GO

CREATE OR ALTER PROCEDURE core.usp_apply_reconcile_publish_cotacoes
  @execution_id UNIQUEIDENTIFIER,@contract_version NVARCHAR(MAX),@contract_fingerprint NVARCHAR(MAX),@configuration_version NVARCHAR(MAX),@configuration_fingerprint NVARCHAR(MAX),@reference_release_id BIGINT
AS
BEGIN
    DECLARE @b55_actual NVARCHAR(MAX)=(SELECT LOWER(CONVERT(NVARCHAR(36),@execution_id)) AS [execution],
       N'cotacoes' AS [entity],N'cotacoes' AS [workload],N'BACKFILL' AS [mode] FOR JSON PATH,WITHOUT_ARRAY_WRAPPER);
    IF ctl.fn_runtime_consumed_scope(@b55_actual,1,NULL)<>1 THROW 52840,N'B55_VERTICAL_CONSUMER_FENCE',1;
  SET NOCOUNT ON; SET XACT_ABORT ON; BEGIN TRANSACTION;
  IF ctl.fn_runtime_tariff_valid(@execution_id,@reference_release_id)<>1
    OR NOT EXISTS(SELECT 1 FROM ctl.runtime_cotacao_reference WHERE execution_id=@execution_id AND reference_release_id=@reference_release_id)
    THROW 52842,N'TARIFF_DURABLE_BINDING_REQUIRED',1;
  IF @reference_release_id IS NULL THROW 51910,N'COT-02 exige reference_release_id explícito.',1;
  IF NOT EXISTS(SELECT 1 FROM ctl.cotacao_promotion_result WITH(UPDLOCK,HOLDLOCK) WHERE execution_id=@execution_id AND validation_state=N'PASSED') THROW 51911,N'Candidate set de Cotações não está apto.',1;
  DECLARE @environment_name NVARCHAR(32),@source_instance NVARCHAR(128),@tenant_scope NVARCHAR(128);
  SELECT @environment_name=p.environment_name,@source_instance=p.source_instance,@tenant_scope=p.tenant_scope
  FROM ctl.execution_attempt a WITH(UPDLOCK,HOLDLOCK)
  JOIN ctl.execution_partition p WITH(HOLDLOCK) ON p.partition_id=a.partition_id WHERE a.execution_id=@execution_id;
  IF UPPER(@source_instance) IN(N'DEFAULT',N'GLOBAL',N'SINGLETON')
     OR UPPER(@tenant_scope) IN(N'DEFAULT',N'GLOBAL',N'SINGLETON')
    THROW 51903,N'Cotações exige source_instance e tenant_scope explícitos, sem sentinelas.',1;
  -- Não há current, wildcard ou fallback: a release precisa ser QUOTE_TARIFF, ratificada para
  -- SHADOW e sem revogação no mesmo escopo.
  IF NOT EXISTS(
    SELECT 1 FROM ref.reference_release r WITH(UPDLOCK,HOLDLOCK)
    JOIN ref.reference_release_ratification rat WITH(UPDLOCK,HOLDLOCK)
      ON rat.reference_release_id=r.reference_release_id AND rat.activation_scope=N'SHADOW'
    LEFT JOIN ref.reference_release_revocation rev WITH(UPDLOCK,HOLDLOCK)
      ON rev.reference_release_id=rat.reference_release_id AND rev.activation_scope=rat.activation_scope
    WHERE r.reference_release_id=@reference_release_id AND r.family_code=N'QUOTE_TARIFF'
      AND rev.reference_release_id IS NULL
  ) THROW 51912,N'Release tarifária ausente, não ratificada ou revogada.',1;

  -- ABSENT consulta somente a mesma raiz física escopada; NULL limpa e VALUE aplica,
  -- inclusive zero. Essa decisão precede obrigatoriamente a resolução tarifária.
  DECLARE @effective_candidates TABLE (
    source_key NVARCHAR(256) COLLATE Latin1_General_100_BIN2 NOT NULL PRIMARY KEY,
    stage_record_id BIGINT NOT NULL,
    user_name_normalized NVARCHAR(MAX) NULL,
    total_amount DECIMAL(19,4) NULL,
    origin_uf CHAR(2) COLLATE Latin1_General_100_BIN2 NULL,
    destination_uf CHAR(2) COLLATE Latin1_General_100_BIN2 NULL
  );
  INSERT @effective_candidates(
    source_key,stage_record_id,user_name_normalized,total_amount,origin_uf,destination_uf
  )
  SELECT candidate.source_key,typed.stage_record_id,
    CASE JSON_VALUE(typed.field_presence_json,N'$.qoe_uer_name')
      WHEN N'ABSENT' THEN current_record.user_name_normalized
      WHEN N'NULL' THEN NULL
      WHEN N'VALUE' THEN typed.user_name_normalized END,
    CASE JSON_VALUE(typed.field_presence_json,N'$.qoe_qes_total')
      WHEN N'ABSENT' THEN current_record.total_amount
      WHEN N'NULL' THEN NULL
      WHEN N'VALUE' THEN typed.total_amount END,
    CASE JSON_VALUE(typed.field_presence_json,N'$.qoe_qes_ony_sae_code')
      WHEN N'ABSENT' THEN current_record.origin_uf
      WHEN N'NULL' THEN NULL
      WHEN N'VALUE' THEN typed.origin_uf END,
    CASE JSON_VALUE(typed.field_presence_json,N'$.qoe_qes_diy_sae_code')
      WHEN N'ABSENT' THEN current_record.destination_uf
      WHEN N'NULL' THEN NULL
      WHEN N'VALUE' THEN typed.destination_uf END
  FROM stg.execution_candidate candidate
  JOIN stg.cotacao_record typed ON typed.stage_record_id=candidate.winner_stage_record_id
  LEFT JOIN core.cotacao current_record WITH(UPDLOCK,HOLDLOCK,INDEX(UQ_core_cotacao_source))
    ON current_record.environment_name=@environment_name
   AND current_record.source_instance=@source_instance
   AND current_record.tenant_scope=@tenant_scope
   AND current_record.entity_name=N'cotacoes'
   AND current_record.source_key=candidate.source_key
  WHERE candidate.execution_id=@execution_id;

  DECLARE @tariff_resolution TABLE (
    source_key NVARCHAR(256) COLLATE Latin1_General_100_BIN2 NOT NULL PRIMARY KEY,
    reference_release_id BIGINT NOT NULL, effective_date DATE NOT NULL,
    minimum_amount DECIMAL(19,4) NOT NULL, currency_code CHAR(3) COLLATE Latin1_General_100_BIN2 NOT NULL,
    unit_code NVARCHAR(32) COLLATE Latin1_General_100_BIN2 NOT NULL,
    rounding_mode NVARCHAR(24) COLLATE Latin1_General_100_BIN2 NOT NULL
  );
  INSERT @tariff_resolution(source_key,reference_release_id,effective_date,minimum_amount,currency_code,unit_code,rounding_mode)
  SELECT candidate.source_key,tariff.reference_release_id,typed.freshness_business_date,
         tariff.minimum_amount,tariff.currency_code,tariff.unit_code,tariff.rounding_mode
  FROM stg.execution_candidate candidate
  JOIN @effective_candidates effective ON effective.source_key=candidate.source_key
  JOIN stg.cotacao_record typed ON typed.stage_record_id=effective.stage_record_id
  CROSS APPLY(
    SELECT COUNT_BIG(*) AS matching_rows FROM ref.tarifa_rota_uf WITH(UPDLOCK,HOLDLOCK,INDEX(IX_ref_tarifa_rota_uf_lookup))
    WHERE reference_release_id=@reference_release_id AND origin_uf=effective.origin_uf
      AND destination_uf=effective.destination_uf AND typed.freshness_business_date>=valid_from
      AND typed.freshness_business_date<valid_to_exclusive
  ) matches
  OUTER APPLY(
    SELECT TOP(1) reference_release_id,coverage_state,minimum_amount,currency_code,unit_code,rounding_mode
    FROM ref.tarifa_rota_uf WITH(UPDLOCK,HOLDLOCK,INDEX(IX_ref_tarifa_rota_uf_lookup))
    WHERE reference_release_id=@reference_release_id AND origin_uf=effective.origin_uf
      AND destination_uf=effective.destination_uf AND typed.freshness_business_date>=valid_from
      AND typed.freshness_business_date<valid_to_exclusive
    ORDER BY valid_from
  ) tariff
  WHERE candidate.execution_id=@execution_id AND effective.origin_uf IS NOT NULL AND effective.destination_uf IS NOT NULL
    AND matches.matching_rows=1 AND tariff.coverage_state=N'PRICED'
    AND tariff.minimum_amount>CONVERT(DECIMAL(19,4),0)
    AND tariff.currency_code COLLATE Latin1_General_100_BIN2 LIKE '[A-Z][A-Z][A-Z]'
    AND tariff.unit_code IN(N'PER_SHIPMENT',N'PER_WEIGHT',N'PER_VOLUME')
    AND tariff.rounding_mode IN(N'HALF_UP',N'HALF_EVEN',N'DOWN',N'UP');
  IF (SELECT COUNT_BIG(*) FROM @tariff_resolution)<>(SELECT COUNT_BIG(*) FROM stg.execution_candidate WHERE execution_id=@execution_id)
    THROW 51914,N'Tarifa direcional não coberta, ambígua ou inválida: COT-02 falha fechada.',1;

  IF EXISTS(
    SELECT 1 FROM stg.execution_candidate candidate
    JOIN stg.cotacao_record typed ON typed.stage_record_id=candidate.winner_stage_record_id
    JOIN core.cotacao current_record WITH(UPDLOCK,HOLDLOCK,INDEX(UQ_core_cotacao_source))
      ON current_record.environment_name=@environment_name AND current_record.source_instance=@source_instance
     AND current_record.tenant_scope=@tenant_scope AND current_record.entity_name=N'cotacoes'
     AND current_record.source_key=candidate.source_key
    WHERE candidate.execution_id=@execution_id AND current_record.freshness_at_utc=typed.freshness_at_utc
      AND current_record.attribute_hash<>typed.attribute_hash
  ) THROW 51913,N'Empate de frescor com conteúdo divergente exige quarentena.',1;

  DECLARE @common_result TABLE (
    execution_id UNIQUEIDENTIFIER NOT NULL,candidate_rows BIGINT NOT NULL,inserted_rows BIGINT NOT NULL,
    updated_rows BIGINT NOT NULL,reactivated_rows BIGINT NOT NULL,noop_rows BIGINT NOT NULL,
    stale_noop_rows BIGINT NOT NULL,reconciled_at_utc DATETIME2(3) NOT NULL,published_at_utc DATETIME2(3) NOT NULL,
    incremental_frontier_before_utc DATETIME2(3) NULL,incremental_frontier_after_utc DATETIME2(3) NULL
  );
  INSERT @common_result EXEC core.usp_apply_reconcile_publish_execution
    @execution_id,@contract_version,@contract_fingerprint,@configuration_version,@configuration_fingerprint;
  INSERT core.cotacao(
    record_state_id,environment_name,source_instance,tenant_scope,entity_name,source_key,payload_json,
    field_presence_json,user_name_normalized,freshness_at_utc,freshness_origin,freshness_business_date,
    total_amount,currency_code,origin_uf,destination_uf,tariff_reference_release_id,tariff_effective_date,
    tariff_minimum_amount,tariff_currency_code,tariff_unit_code,tariff_rounding_mode,attribute_hash,active,
    first_seen_execution_id,last_seen_execution_id
  )
  SELECT app.record_state_id,@environment_name,@source_instance,@tenant_scope,N'cotacoes',typed.source_key,
         typed.payload_json,typed.field_presence_json,effective.user_name_normalized,typed.freshness_at_utc,
         typed.freshness_origin,typed.freshness_business_date,effective.total_amount,CAST(NULL AS CHAR(3)),
         effective.origin_uf,effective.destination_uf,tariff.reference_release_id,tariff.effective_date,
         tariff.minimum_amount,tariff.currency_code,tariff.unit_code,tariff.rounding_mode,typed.attribute_hash,
         1,@execution_id,@execution_id
  FROM stg.execution_candidate x JOIN stg.cotacao_record typed ON typed.stage_record_id=x.winner_stage_record_id
  JOIN @effective_candidates effective ON effective.source_key=x.source_key
  JOIN @tariff_resolution tariff ON tariff.source_key=x.source_key
  JOIN recon.execution_candidate_application app ON app.execution_id=x.execution_id AND app.source_key=x.source_key
  WHERE x.execution_id=@execution_id AND NOT EXISTS(
    SELECT 1 FROM core.cotacao current_record WITH(UPDLOCK,HOLDLOCK,INDEX(UQ_core_cotacao_source))
    WHERE current_record.environment_name=@environment_name AND current_record.source_instance=@source_instance
      AND current_record.tenant_scope=@tenant_scope AND current_record.entity_name=N'cotacoes'
      AND current_record.source_key=x.source_key
  );
  UPDATE current_record SET payload_json=typed.payload_json,field_presence_json=typed.field_presence_json,
    user_name_normalized=effective.user_name_normalized,freshness_at_utc=typed.freshness_at_utc,
    freshness_origin=typed.freshness_origin,freshness_business_date=typed.freshness_business_date,
    total_amount=effective.total_amount,currency_code=NULL,origin_uf=effective.origin_uf,
    destination_uf=effective.destination_uf,tariff_reference_release_id=tariff.reference_release_id,
    tariff_effective_date=tariff.effective_date,tariff_minimum_amount=tariff.minimum_amount,
    tariff_currency_code=tariff.currency_code,tariff_unit_code=tariff.unit_code,
    tariff_rounding_mode=tariff.rounding_mode,attribute_hash=typed.attribute_hash,last_seen_execution_id=@execution_id
  FROM core.cotacao current_record JOIN stg.execution_candidate x ON x.source_key=current_record.source_key
  JOIN stg.cotacao_record typed ON typed.stage_record_id=x.winner_stage_record_id
  JOIN @effective_candidates effective ON effective.source_key=x.source_key
  JOIN @tariff_resolution tariff ON tariff.source_key=x.source_key
  WHERE x.execution_id=@execution_id AND current_record.environment_name=@environment_name
    AND current_record.source_instance=@source_instance AND current_record.tenant_scope=@tenant_scope
    AND current_record.entity_name=N'cotacoes' AND typed.freshness_at_utc>current_record.freshness_at_utc;
  UPDATE current_record SET last_seen_execution_id=@execution_id
  FROM core.cotacao current_record JOIN stg.execution_candidate x ON x.source_key=current_record.source_key
  JOIN recon.execution_candidate_application app ON app.execution_id=x.execution_id AND app.source_key=x.source_key
  WHERE x.execution_id=@execution_id AND current_record.environment_name=@environment_name
    AND current_record.source_instance=@source_instance AND current_record.tenant_scope=@tenant_scope
    AND current_record.entity_name=N'cotacoes' AND app.application_disposition IN(N'NO_OP',N'STALE_NO_OP');
  INSERT recon.cotacao_root_presence_observation(execution_id,cotacao_id,observed_at_utc,snapshot_completeness,absence_evaluation)
  SELECT @execution_id,current_record.cotacao_id,SYSUTCDATETIME(),N'BLOCKED_NO_COMPLETENESS_PROOF',N'NOT_EVALUATED'
  FROM core.cotacao current_record JOIN stg.execution_candidate x ON x.source_key=current_record.source_key
  WHERE x.execution_id=@execution_id AND current_record.environment_name=@environment_name
    AND current_record.source_instance=@source_instance AND current_record.tenant_scope=@tenant_scope
    AND current_record.entity_name=N'cotacoes'
    AND NOT EXISTS(SELECT 1 FROM recon.cotacao_root_presence_observation observed
                   WHERE observed.execution_id=@execution_id AND observed.cotacao_id=current_record.cotacao_id);
  EXEC recon.usp_capture_runtime_vertical_output @execution_id;
    COMMIT TRANSACTION;
  SELECT execution_id,candidate_rows,inserted_rows,updated_rows,reactivated_rows,noop_rows,stale_noop_rows,
         reconciled_at_utc,published_at_utc,incremental_frontier_before_utc,incremental_frontier_after_utc
  FROM @common_result;
END;
GO

CREATE OR ALTER PROCEDURE core.usp_apply_reconcile_publish_manifestos
    @execution_id UNIQUEIDENTIFIER,@contract_version NVARCHAR(MAX),@contract_fingerprint NVARCHAR(MAX),
    @configuration_version NVARCHAR(MAX),@configuration_fingerprint NVARCHAR(MAX)
AS
BEGIN
    DECLARE @b55_actual NVARCHAR(MAX)=(SELECT LOWER(CONVERT(NVARCHAR(36),@execution_id)) AS [execution],
       N'manifestos' AS [entity],N'manifestos' AS [workload],N'BACKFILL' AS [mode] FOR JSON PATH,WITHOUT_ARRAY_WRAPPER);
    IF ctl.fn_runtime_consumed_scope(@b55_actual,1,NULL)<>1 THROW 52840,N'B55_VERTICAL_CONSUMER_FENCE',1;
    SET NOCOUNT ON; SET XACT_ABORT ON; BEGIN TRANSACTION;
    IF NOT EXISTS(SELECT 1 FROM ctl.manifesto_promotion_result WITH(UPDLOCK,HOLDLOCK) WHERE execution_id=@execution_id AND validation_state=N'PASSED')
        THROW 52030,N'Candidate set de Manifestos não está apto.',1;
    DECLARE @environment_name NVARCHAR(32),@source_instance NVARCHAR(128),@tenant_scope NVARCHAR(128);
    SELECT @environment_name=p.environment_name,@source_instance=p.source_instance,@tenant_scope=p.tenant_scope
    FROM ctl.execution_attempt a WITH(UPDLOCK,HOLDLOCK) JOIN ctl.execution_partition p WITH(HOLDLOCK) ON p.partition_id=a.partition_id WHERE a.execution_id=@execution_id;
    IF UPPER(@source_instance) IN(N'DEFAULT',N'GLOBAL',N'SINGLETON') OR UPPER(@tenant_scope) IN(N'DEFAULT',N'GLOBAL',N'SINGLETON')
        THROW 52031,N'Manifestos exige escopo explícito.',1;
    IF EXISTS(
        SELECT 1 FROM stg.manifesto_reduced_candidate c JOIN core.manifesto current_record WITH(UPDLOCK,HOLDLOCK,INDEX(UQ_core_manifesto_source))
          ON current_record.environment_name=@environment_name AND current_record.source_instance=@source_instance AND current_record.tenant_scope=@tenant_scope
         AND current_record.entity_name=N'manifestos' AND current_record.source_key=c.source_key
        WHERE c.execution_id=@execution_id AND current_record.freshness_at_utc=c.freshness_at_utc AND current_record.attribute_hash<>c.attribute_hash
    ) THROW 52032,N'EQUAL_FRESHNESS_CONFLICT de Manifesto exige quarantine.',1;
    DECLARE @common_result TABLE(execution_id UNIQUEIDENTIFIER,candidate_rows BIGINT,inserted_rows BIGINT,updated_rows BIGINT,reactivated_rows BIGINT,noop_rows BIGINT,stale_noop_rows BIGINT,reconciled_at_utc DATETIME2(3),published_at_utc DATETIME2(3),incremental_frontier_before_utc DATETIME2(3),incremental_frontier_after_utc DATETIME2(3));
    INSERT @common_result EXEC core.usp_apply_reconcile_publish_execution @execution_id,@contract_version,@contract_fingerprint,@configuration_version,@configuration_fingerprint;
    INSERT core.manifesto(record_state_id,environment_name,source_instance,tenant_scope,entity_name,source_key,root_payload_json,root_presence_json,metric_values_json,competence_json,mdfe_status_presence,mdfe_status_value,freshness_at_utc,freshness_origin,attribute_hash,presence_hash,active,first_seen_execution_id,last_seen_execution_id)
    SELECT app.record_state_id,@environment_name,@source_instance,@tenant_scope,N'manifestos',c.source_key,c.root_payload_json,c.root_presence_json,c.metric_values_json,c.competence_json,c.mdfe_status_presence,c.mdfe_status_value,c.freshness_at_utc,c.freshness_origin,c.attribute_hash,c.presence_hash,1,@execution_id,@execution_id
    FROM stg.manifesto_reduced_candidate c JOIN recon.execution_candidate_application app ON app.execution_id=c.execution_id AND app.source_key=c.source_key
    WHERE c.execution_id=@execution_id AND NOT EXISTS(SELECT 1 FROM core.manifesto m WITH(UPDLOCK,HOLDLOCK,INDEX(UQ_core_manifesto_source)) WHERE m.environment_name=@environment_name AND m.source_instance=@source_instance AND m.tenant_scope=@tenant_scope AND m.entity_name=N'manifestos' AND m.source_key=c.source_key);
    UPDATE current_record SET root_payload_json=c.root_payload_json,root_presence_json=c.root_presence_json,metric_values_json=c.metric_values_json,competence_json=c.competence_json,mdfe_status_presence=c.mdfe_status_presence,mdfe_status_value=c.mdfe_status_value,freshness_at_utc=c.freshness_at_utc,freshness_origin=c.freshness_origin,attribute_hash=c.attribute_hash,presence_hash=c.presence_hash,last_seen_execution_id=@execution_id
    FROM core.manifesto current_record JOIN stg.manifesto_reduced_candidate c ON c.source_key=current_record.source_key
    WHERE c.execution_id=@execution_id AND current_record.environment_name=@environment_name AND current_record.source_instance=@source_instance AND current_record.tenant_scope=@tenant_scope AND current_record.entity_name=N'manifestos' AND c.freshness_at_utc>current_record.freshness_at_utc;
    -- Child observations preserve their own latest cohort; absence does not delete a child.
    IF EXISTS(SELECT 1 FROM stg.manifesto_mdfe_candidate x
      JOIN core.manifesto m ON m.environment_name=@environment_name AND m.source_instance=@source_instance
        AND m.tenant_scope=@tenant_scope AND m.source_key=x.source_key
      JOIN core.manifesto_mdfe c ON c.manifesto_id=m.manifesto_id AND c.mdfe_key=x.mdfe_key
      WHERE x.execution_id=@execution_id AND (c.freshness_at_utc=x.freshness_at_utc OR c.freshness_at_utc IS NULL)
        AND c.mdfe_number<>x.mdfe_number) THROW 52833,N'MDFE_EQUAL_FRESHNESS_CONFLICT',1;
    INSERT core.manifesto_pick(manifesto_id,pick_source_key,first_seen_execution_id,freshness_at_utc)
    SELECT m.manifesto_id,x.pick_source_key,@execution_id,x.freshness_at_utc
    FROM stg.manifesto_pick_candidate x JOIN core.manifesto m ON m.environment_name=@environment_name
      AND m.source_instance=@source_instance AND m.tenant_scope=@tenant_scope AND m.source_key=x.source_key
    WHERE x.execution_id=@execution_id AND NOT EXISTS(SELECT 1 FROM core.manifesto_pick c WITH(UPDLOCK,HOLDLOCK)
      WHERE c.manifesto_id=m.manifesto_id AND c.pick_source_key=x.pick_source_key);
    INSERT core.manifesto_mdfe(manifesto_id,mdfe_key,mdfe_number,first_seen_execution_id,freshness_at_utc)
    SELECT m.manifesto_id,x.mdfe_key,x.mdfe_number,@execution_id,x.freshness_at_utc
    FROM stg.manifesto_mdfe_candidate x JOIN core.manifesto m ON m.environment_name=@environment_name
      AND m.source_instance=@source_instance AND m.tenant_scope=@tenant_scope AND m.source_key=x.source_key
    WHERE x.execution_id=@execution_id AND NOT EXISTS(SELECT 1 FROM core.manifesto_mdfe c WITH(UPDLOCK,HOLDLOCK)
      WHERE c.manifesto_id=m.manifesto_id AND c.mdfe_key=x.mdfe_key);
    UPDATE c SET freshness_at_utc=x.freshness_at_utc
    FROM core.manifesto_pick c JOIN core.manifesto m ON m.manifesto_id=c.manifesto_id
    JOIN stg.manifesto_pick_candidate x ON x.execution_id=@execution_id AND x.source_key=m.source_key AND x.pick_source_key=c.pick_source_key
    WHERE m.environment_name=@environment_name AND m.source_instance=@source_instance AND m.tenant_scope=@tenant_scope
      AND (c.freshness_at_utc IS NULL OR c.freshness_at_utc<x.freshness_at_utc);
    UPDATE c SET mdfe_number=x.mdfe_number,freshness_at_utc=x.freshness_at_utc
    FROM core.manifesto_mdfe c JOIN core.manifesto m ON m.manifesto_id=c.manifesto_id
    JOIN stg.manifesto_mdfe_candidate x ON x.execution_id=@execution_id AND x.source_key=m.source_key AND x.mdfe_key=c.mdfe_key
    WHERE m.environment_name=@environment_name AND m.source_instance=@source_instance AND m.tenant_scope=@tenant_scope
      AND (c.freshness_at_utc IS NULL OR c.freshness_at_utc<x.freshness_at_utc);

    INSERT recon.manifesto_root_presence_observation(execution_id,manifesto_id,observed_at_utc,snapshot_completeness,absence_evaluation)
    SELECT @execution_id,m.manifesto_id,SYSUTCDATETIME(),N'BLOCKED_NO_COMPLETENESS_PROOF',N'NOT_EVALUATED'
    FROM core.manifesto m JOIN stg.manifesto_reduced_candidate c ON c.source_key=m.source_key
    WHERE c.execution_id=@execution_id AND m.environment_name=@environment_name AND m.source_instance=@source_instance AND m.tenant_scope=@tenant_scope AND m.entity_name=N'manifestos'
      AND NOT EXISTS(SELECT 1 FROM recon.manifesto_root_presence_observation x WHERE x.execution_id=@execution_id AND x.manifesto_id=m.manifesto_id);
    EXEC recon.usp_capture_runtime_vertical_output @execution_id;
    COMMIT TRANSACTION;
    SELECT execution_id,candidate_rows,inserted_rows,updated_rows,reactivated_rows,noop_rows,stale_noop_rows,reconciled_at_utc,published_at_utc,incremental_frontier_before_utc,incremental_frontier_after_utc FROM @common_result;
END;
GO

CREATE OR ALTER PROCEDURE core.usp_apply_reconcile_publish_localizacao_cargas
    @execution_id UNIQUEIDENTIFIER,
    @contract_version NVARCHAR(MAX),
    @contract_fingerprint NVARCHAR(MAX),
    @configuration_version NVARCHAR(MAX),
    @configuration_fingerprint NVARCHAR(MAX)
AS
BEGIN
    DECLARE @b55_actual NVARCHAR(MAX)=(SELECT LOWER(CONVERT(NVARCHAR(36),@execution_id)) AS [execution],
       N'localizacao_cargas' AS [entity],N'localizacao_cargas' AS [workload],N'BACKFILL' AS [mode] FOR JSON PATH,WITHOUT_ARRAY_WRAPPER);
    IF ctl.fn_runtime_consumed_scope(@b55_actual,1,NULL)<>1 THROW 52840,N'B55_VERTICAL_CONSUMER_FENCE',1;
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    BEGIN TRANSACTION;
    IF NOT EXISTS(
        SELECT 1 FROM ctl.localizacao_carga_promotion_result WITH(UPDLOCK,HOLDLOCK)
        WHERE execution_id=@execution_id AND validation_state=N'PASSED'
    ) THROW 52220,N'Candidate set tipado de Localização não está apto.',1;
    DECLARE @environment_name NVARCHAR(32),@source_instance NVARCHAR(128),
            @tenant_scope NVARCHAR(128),@entity_name NVARCHAR(128);
    SELECT @environment_name=partition.environment_name,
           @source_instance=partition.source_instance,@tenant_scope=partition.tenant_scope,
           @entity_name=partition.entity_name
    FROM ctl.execution_attempt attempt WITH(UPDLOCK,HOLDLOCK)
    JOIN ctl.execution_partition partition WITH(HOLDLOCK)
      ON partition.partition_id=attempt.partition_id
    WHERE attempt.execution_id=@execution_id;
    IF @entity_name<>N'localizacao_cargas'
       OR UPPER(@source_instance) IN(N'DEFAULT',N'GLOBAL',N'SINGLETON')
       OR UPPER(@tenant_scope) IN(N'DEFAULT',N'GLOBAL',N'SINGLETON')
        THROW 52221,N'Escopo de promoção de Localização inválido.',1;

    IF EXISTS(
        SELECT 1
        FROM stg.execution_candidate candidate
        JOIN stg.localizacao_carga_record typed_record
          ON typed_record.stage_record_id=candidate.winner_stage_record_id
        JOIN core.localizacao_cargas current_record
          WITH(UPDLOCK,HOLDLOCK,INDEX(UQ_core_localizacao_cargas_source))
          ON current_record.environment_name=@environment_name
         AND current_record.source_instance=@source_instance
         AND current_record.tenant_scope=@tenant_scope
         AND current_record.entity_name=N'localizacao_cargas'
         AND current_record.source_key=candidate.source_key
        WHERE candidate.execution_id=@execution_id
          AND current_record.freshness_at_utc=typed_record.freshness_at_utc
          AND current_record.attribute_hash<>typed_record.attribute_hash
    ) THROW 52222,N'EQUAL_FRESHNESS_CONFLICT: Localização exige quarentena.',1;

    DECLARE @effective TABLE(
        source_key NVARCHAR(256) COLLATE Latin1_General_100_BIN2 NOT NULL PRIMARY KEY,
        stage_record_id BIGINT NOT NULL,
        payload_json NVARCHAR(MAX) NULL,field_presence_json NVARCHAR(MAX) NULL,
        invoices_volumes_raw NVARCHAR(MAX) NULL,invoices_volumes_typed INT NULL,
        taxed_weight_raw NVARCHAR(MAX) NULL,taxed_weight_typed DECIMAL(38,9) NULL,
        invoices_value_raw NVARCHAR(MAX) NULL,invoices_value_typed DECIMAL(38,9) NULL,
        total_raw NVARCHAR(MAX) NULL,total_typed DECIMAL(38,9) NULL,
        status_raw NVARCHAR(MAX) NULL,status_normalized NVARCHAR(MAX) NOT NULL,
        status_terminal BIT NOT NULL
    );
    INSERT @effective(
        source_key,stage_record_id,payload_json,field_presence_json,
        invoices_volumes_raw,invoices_volumes_typed,
        taxed_weight_raw,taxed_weight_typed,invoices_value_raw,invoices_value_typed,
        total_raw,total_typed,status_raw,status_normalized,status_terminal
    )
    SELECT candidate.source_key,typed_record.stage_record_id,NULL,NULL,
      CASE typed_record.invoices_volumes_presence WHEN N'ABSENT'
        THEN current_record.invoices_volumes_raw ELSE typed_record.invoices_volumes_raw END,
      CASE typed_record.invoices_volumes_presence WHEN N'ABSENT'
        THEN current_record.invoices_volumes_typed ELSE typed_record.invoices_volumes_typed END,
      CASE typed_record.taxed_weight_presence WHEN N'ABSENT'
        THEN current_record.taxed_weight_raw ELSE typed_record.taxed_weight_raw END,
      CASE typed_record.taxed_weight_presence WHEN N'ABSENT'
        THEN current_record.taxed_weight_typed ELSE typed_record.taxed_weight_typed END,
      CASE typed_record.invoices_value_presence WHEN N'ABSENT'
        THEN current_record.invoices_value_raw ELSE typed_record.invoices_value_raw END,
      CASE typed_record.invoices_value_presence WHEN N'ABSENT'
        THEN current_record.invoices_value_typed ELSE typed_record.invoices_value_typed END,
      CASE typed_record.total_presence WHEN N'ABSENT'
        THEN current_record.total_raw ELSE typed_record.total_raw END,
      CASE typed_record.total_presence WHEN N'ABSENT'
        THEN current_record.total_typed ELSE typed_record.total_typed END,
      CASE typed_record.status_presence WHEN N'ABSENT'
        THEN current_record.status_raw ELSE typed_record.status_raw END,
      CASE typed_record.status_presence WHEN N'ABSENT'
        THEN COALESCE(current_record.status_normalized,typed_record.status_normalized)
        ELSE typed_record.status_normalized END,
      CASE typed_record.status_presence WHEN N'ABSENT'
        THEN COALESCE(current_record.status_terminal,typed_record.status_terminal)
        ELSE typed_record.status_terminal END
    FROM stg.execution_candidate candidate
    JOIN stg.localizacao_carga_record typed_record
      ON typed_record.stage_record_id=candidate.winner_stage_record_id
    LEFT JOIN core.localizacao_cargas current_record
      WITH(UPDLOCK,HOLDLOCK,INDEX(UQ_core_localizacao_cargas_source))
      ON current_record.environment_name=@environment_name
     AND current_record.source_instance=@source_instance
     AND current_record.tenant_scope=@tenant_scope
     AND current_record.entity_name=N'localizacao_cargas'
     AND current_record.source_key=candidate.source_key
    WHERE candidate.execution_id=@execution_id;

    -- Reducer uniforme dos 17 paths: ABSENT conserva o envelope corrente; NULL e VALUE
    -- aplicam o envelope observado. Os dois paths obrigatórios nunca chegam ABSENT em VALID.
    DECLARE @effective_field_catalog TABLE(
        field_ordinal TINYINT NOT NULL PRIMARY KEY,
        field_name NVARCHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL UNIQUE
    );
    INSERT @effective_field_catalog VALUES
        (1,N'corporation_sequence_number'),(2,N'type'),(3,N'service_at'),
        (4,N'invoices_volumes'),(5,N'taxed_weight'),(6,N'invoices_value'),
        (7,N'total'),(8,N'service_type'),(9,N'fit_crn_psn_nickname'),
        (10,N'fit_dpn_delivery_prediction_at'),(11,N'fit_dyn_name'),
        (12,N'fit_dyn_drt_nickname'),(13,N'fit_fsn_name'),(14,N'fit_fln_status'),
        (15,N'fit_fln_cln_nickname'),(16,N'fit_o_n_name'),
        (17,N'fit_o_n_drt_nickname');
    DECLARE @effective_field TABLE(
        source_key NVARCHAR(256) COLLATE Latin1_General_100_BIN2 NOT NULL,
        field_ordinal TINYINT NOT NULL,
        field_name NVARCHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL,
        field_json NVARCHAR(MAX) NOT NULL,
        PRIMARY KEY(source_key,field_ordinal)
    );
    INSERT @effective_field(source_key,field_ordinal,field_name,field_json)
    SELECT candidate.source_key,catalog.field_ordinal,catalog.field_name,
        CASE WHEN JSON_VALUE(typed_field.field_json,N'$.presence')
                       COLLATE Latin1_General_100_BIN2
                       =N'ABSENT' COLLATE Latin1_General_100_BIN2
                   AND current_record.localizacao_carga_id IS NOT NULL
             THEN current_field.field_json ELSE typed_field.field_json END
    FROM stg.execution_candidate candidate
    JOIN stg.localizacao_carga_record typed_record
      ON typed_record.stage_record_id=candidate.winner_stage_record_id
    CROSS JOIN @effective_field_catalog catalog
    CROSS APPLY(SELECT JSON_QUERY(typed_record.field_presence_json,
        CONCAT(N'$.fields.',catalog.field_name)) AS field_json) typed_field
    LEFT JOIN core.localizacao_cargas current_record
      WITH(UPDLOCK,HOLDLOCK,INDEX(UQ_core_localizacao_cargas_source))
      ON current_record.environment_name=@environment_name
     AND current_record.source_instance=@source_instance
     AND current_record.tenant_scope=@tenant_scope
     AND current_record.entity_name=N'localizacao_cargas'
     AND current_record.source_key=candidate.source_key
    OUTER APPLY(SELECT JSON_QUERY(current_record.field_presence_json,
        CONCAT(N'$.fields.',catalog.field_name)) AS field_json) current_field
    WHERE candidate.execution_id=@execution_id;

    ;WITH envelope AS(
        SELECT field.source_key,
            STRING_AGG(CONVERT(NVARCHAR(MAX),CONCAT(
                N'"' COLLATE Latin1_General_100_BIN2,
                STRING_ESCAPE(field.field_name COLLATE Latin1_General_100_BIN2,'json')
                    COLLATE Latin1_General_100_BIN2,
                N'":' COLLATE Latin1_General_100_BIN2,
                field.field_json COLLATE Latin1_General_100_BIN2))
                    COLLATE Latin1_General_100_BIN2,
                N',' COLLATE Latin1_General_100_BIN2)
                WITHIN GROUP(ORDER BY field.field_ordinal) AS fields_json
        FROM @effective_field field GROUP BY field.source_key
    ), payload AS(
        SELECT field.source_key,
            STRING_AGG(CONVERT(NVARCHAR(MAX),CONCAT(
                N'"' COLLATE Latin1_General_100_BIN2,
                STRING_ESCAPE(field.field_name COLLATE Latin1_General_100_BIN2,'json')
                    COLLATE Latin1_General_100_BIN2,
                N'":' COLLATE Latin1_General_100_BIN2,
                CASE raw_property.[type]
                    WHEN 0 THEN N'null' COLLATE Latin1_General_100_BIN2
                    WHEN 1 THEN CONCAT(
                        N'"' COLLATE Latin1_General_100_BIN2,
                        STRING_ESCAPE(raw_property.[value] COLLATE Latin1_General_100_BIN2,'json')
                            COLLATE Latin1_General_100_BIN2,
                        N'"' COLLATE Latin1_General_100_BIN2)
                            COLLATE Latin1_General_100_BIN2
                    WHEN 2 THEN raw_property.[value] COLLATE Latin1_General_100_BIN2
                    WHEN 3 THEN LOWER(raw_property.[value] COLLATE Latin1_General_100_BIN2)
                        COLLATE Latin1_General_100_BIN2
                END COLLATE Latin1_General_100_BIN2))
                    COLLATE Latin1_General_100_BIN2,
                N',' COLLATE Latin1_General_100_BIN2)
                WITHIN GROUP(ORDER BY field.field_ordinal) AS payload_fields_json
        FROM @effective_field field
        CROSS APPLY OPENJSON(field.field_json) raw_property
        WHERE raw_property.[key] COLLATE Latin1_General_100_BIN2
                =N'raw' COLLATE Latin1_General_100_BIN2
          AND JSON_VALUE(field.field_json,N'$.presence')
                COLLATE Latin1_General_100_BIN2
                <>N'ABSENT' COLLATE Latin1_General_100_BIN2
        GROUP BY field.source_key
    )
    UPDATE effective SET
        field_presence_json=CONCAT(
            N'{"fields":{' COLLATE Latin1_General_100_BIN2,
            envelope.fields_json COLLATE Latin1_General_100_BIN2,
            N'},"policy":' COLLATE Latin1_General_100_BIN2,
            JSON_QUERY(typed_record.field_presence_json,N'$.policy')
                COLLATE Latin1_General_100_BIN2,
            N'}' COLLATE Latin1_General_100_BIN2),
        payload_json=CONCAT(
            N'{' COLLATE Latin1_General_100_BIN2,
            payload.payload_fields_json COLLATE Latin1_General_100_BIN2,
            N'}' COLLATE Latin1_General_100_BIN2)
    FROM @effective effective
    JOIN envelope ON envelope.source_key=effective.source_key
    JOIN payload ON payload.source_key=effective.source_key
    JOIN stg.localizacao_carga_record typed_record
      ON typed_record.stage_record_id=effective.stage_record_id;

    DECLARE @common_result TABLE(
        execution_id UNIQUEIDENTIFIER NOT NULL,candidate_rows BIGINT NOT NULL,
        inserted_rows BIGINT NOT NULL,updated_rows BIGINT NOT NULL,
        reactivated_rows BIGINT NOT NULL,noop_rows BIGINT NOT NULL,
        stale_noop_rows BIGINT NOT NULL,reconciled_at_utc DATETIME2(3) NOT NULL,
        published_at_utc DATETIME2(3) NOT NULL,
        incremental_frontier_before_utc DATETIME2(3) NULL,
        incremental_frontier_after_utc DATETIME2(3) NULL
    );
    INSERT @common_result EXEC core.usp_apply_reconcile_publish_execution
        @execution_id,@contract_version,@contract_fingerprint,
        @configuration_version,@configuration_fingerprint;

    INSERT core.localizacao_cargas(
        record_state_id,environment_name,source_instance,tenant_scope,entity_name,source_key,
        source_key_wire_type,payload_json,field_presence_json,service_at_raw,service_at_utc,
        invoices_volumes_raw,invoices_volumes_typed,taxed_weight_raw,taxed_weight_typed,
        invoices_value_raw,invoices_value_typed,total_raw,total_typed,status_raw,
        status_normalized,status_catalog_version,status_terminal,status_branch_nickname,
        status_branch_nickname_presence,status_branch_nickname_provenance,
        freight_candidate_presence,freight_candidate_provenance,freight_candidate_state,
        freshness_at_utc,freshness_origin,partition_basis,overlap_policy_version,
        attribute_hash,active,first_seen_execution_id,last_seen_execution_id,
        first_seen_at_utc,last_seen_at_utc
    )
    SELECT application.record_state_id,@environment_name,@source_instance,@tenant_scope,
        N'localizacao_cargas',typed_record.source_key,N'INTEGER',effective.payload_json,
        effective.field_presence_json,typed_record.service_at_raw,typed_record.service_at_utc,
        effective.invoices_volumes_raw,effective.invoices_volumes_typed,
        effective.taxed_weight_raw,effective.taxed_weight_typed,effective.invoices_value_raw,
        effective.invoices_value_typed,effective.total_raw,effective.total_typed,
        effective.status_raw,effective.status_normalized,N'localizacao-cargas-status-v1',
        effective.status_terminal,NULL,N'ABSENT',N'UNSOURCED_LEGACY',N'VALUE',
        N'LOCAL_ROOT_SOURCE_KEY_ONLY_NO_RELATION',
        N'EXACT_SCOPED_ALIAS_CANDIDATE_UNRESOLVED',typed_record.freshness_at_utc,N'SERVICE_AT',
        N'freights.service_at',N'localizacao-cargas-service-at-overlap-v1',
        typed_record.attribute_hash,CONVERT(BIT,1),@execution_id,@execution_id,
        SYSUTCDATETIME(),SYSUTCDATETIME()
    FROM stg.execution_candidate candidate
    JOIN stg.localizacao_carga_record typed_record
      ON typed_record.stage_record_id=candidate.winner_stage_record_id
    JOIN @effective effective ON effective.source_key=candidate.source_key
    JOIN recon.execution_candidate_application application
      ON application.execution_id=candidate.execution_id
     AND application.source_key=candidate.source_key
    WHERE candidate.execution_id=@execution_id
      AND NOT EXISTS(
          SELECT 1 FROM core.localizacao_cargas current_record
            WITH(UPDLOCK,HOLDLOCK,INDEX(UQ_core_localizacao_cargas_source))
          WHERE current_record.environment_name=@environment_name
            AND current_record.source_instance=@source_instance
            AND current_record.tenant_scope=@tenant_scope
            AND current_record.entity_name=N'localizacao_cargas'
            AND current_record.source_key=candidate.source_key
      );

    UPDATE current_record SET
        payload_json=effective.payload_json,field_presence_json=effective.field_presence_json,
        service_at_raw=typed_record.service_at_raw,service_at_utc=typed_record.service_at_utc,
        invoices_volumes_raw=effective.invoices_volumes_raw,
        invoices_volumes_typed=effective.invoices_volumes_typed,
        taxed_weight_raw=effective.taxed_weight_raw,taxed_weight_typed=effective.taxed_weight_typed,
        invoices_value_raw=effective.invoices_value_raw,
        invoices_value_typed=effective.invoices_value_typed,
        total_raw=effective.total_raw,total_typed=effective.total_typed,
        status_raw=effective.status_raw,status_normalized=effective.status_normalized,
        status_terminal=effective.status_terminal,
        freshness_at_utc=typed_record.freshness_at_utc,attribute_hash=typed_record.attribute_hash,
        last_seen_execution_id=@execution_id,last_seen_at_utc=SYSUTCDATETIME()
    FROM core.localizacao_cargas current_record
    JOIN stg.execution_candidate candidate ON candidate.source_key=current_record.source_key
    JOIN stg.localizacao_carga_record typed_record
      ON typed_record.stage_record_id=candidate.winner_stage_record_id
    JOIN @effective effective ON effective.source_key=candidate.source_key
    WHERE candidate.execution_id=@execution_id
      AND current_record.environment_name=@environment_name
      AND current_record.source_instance=@source_instance
      AND current_record.tenant_scope=@tenant_scope
      AND current_record.entity_name=N'localizacao_cargas'
      AND typed_record.freshness_at_utc>current_record.freshness_at_utc;

    UPDATE current_record SET last_seen_execution_id=@execution_id,last_seen_at_utc=SYSUTCDATETIME()
    FROM core.localizacao_cargas current_record
    JOIN stg.execution_candidate candidate ON candidate.source_key=current_record.source_key
    JOIN recon.execution_candidate_application application
      ON application.execution_id=candidate.execution_id
     AND application.source_key=candidate.source_key
    WHERE candidate.execution_id=@execution_id
      AND current_record.environment_name=@environment_name
      AND current_record.source_instance=@source_instance
      AND current_record.tenant_scope=@tenant_scope
      AND current_record.entity_name=N'localizacao_cargas'
      AND application.application_disposition IN(N'NO_OP',N'STALE_NO_OP');

    INSERT recon.localizacao_carga_root_presence_observation(
        execution_id,localizacao_carga_id,observed_at_utc,
        snapshot_completeness,absence_evaluation
    )
    SELECT @execution_id,current_record.localizacao_carga_id,SYSUTCDATETIME(),
           N'BLOCKED_NO_COMPLETENESS_PROOF',N'NOT_EVALUATED_PRUNE_DISABLED'
    FROM core.localizacao_cargas current_record
    JOIN stg.execution_candidate candidate ON candidate.source_key=current_record.source_key
    WHERE candidate.execution_id=@execution_id
      AND current_record.environment_name=@environment_name
      AND current_record.source_instance=@source_instance
      AND current_record.tenant_scope=@tenant_scope
      AND current_record.entity_name=N'localizacao_cargas'
      AND NOT EXISTS(
          SELECT 1 FROM recon.localizacao_carga_root_presence_observation observation
          WHERE observation.execution_id=@execution_id
            AND observation.localizacao_carga_id=current_record.localizacao_carga_id
      );
    EXEC recon.usp_capture_runtime_vertical_output @execution_id;
    COMMIT TRANSACTION;
    SELECT execution_id,candidate_rows,inserted_rows,updated_rows,reactivated_rows,
           noop_rows,stale_noop_rows,reconciled_at_utc,published_at_utc,
           incremental_frontier_before_utc,incremental_frontier_after_utc
    FROM @common_result;
END;
GO

CREATE OR ALTER PROCEDURE recon.usp_evaluate_execution_data_quality
    @execution_id UNIQUEIDENTIFIER,
    @policy_version NVARCHAR(MAX),
    @policy_fingerprint NVARCHAR(MAX)
AS
BEGIN
    -- B54: validate current SQL authority consumption before any effect/read.
    DECLARE @b54_actual NVARCHAR(MAX)=(SELECT LOWER(COALESCE(CONVERT(NVARCHAR(36),@execution_id),N'')) AS [execution] FOR JSON PATH,WITHOUT_ARRAY_WRAPPER,INCLUDE_NULL_VALUES);
    IF ctl.fn_runtime_consumed_scope(@b54_actual,1,NULL)<>1 THROW 52500,N'RUNTIME_DURABLE_CONSUMER_FENCE_REQUIRED',1;

    SET NOCOUNT ON;
    SET XACT_ABORT ON;

    IF @execution_id IS NULL
        OR NULLIF(LTRIM(RTRIM(@policy_version)), N'') IS NULL
        OR DATALENGTH(@policy_version) <> DATALENGTH(LTRIM(RTRIM(@policy_version)))
        OR DATALENGTH(@policy_version) > 256
        OR LEFT(@policy_version, 1) COLLATE Latin1_General_100_BIN2 NOT LIKE '[A-Za-z0-9]'
        OR @policy_version COLLATE Latin1_General_100_BIN2 LIKE '%[^-A-Za-z0-9._]%'
        OR @policy_fingerprint IS NULL
        OR DATALENGTH(@policy_fingerprint) <> 128
        OR @policy_fingerprint COLLATE Latin1_General_100_BIN2 LIKE '%[^0-9a-f]%'
        THROW 51601, N'A avaliação exige execução e policy canônicas.', 1;

    BEGIN TRANSACTION;

    DECLARE @database_now_utc DATETIME2(3);
    DECLARE @partition_id BIGINT;
    DECLARE @execution_state NVARCHAR(32);
    DECLARE @environment_name NVARCHAR(32);
    DECLARE @source_instance NVARCHAR(128);
    DECLARE @tenant_scope NVARCHAR(128);
    DECLARE @entity_name NVARCHAR(128);
    DECLARE @execution_mode NVARCHAR(16);

    SELECT
        @partition_id = attempt.partition_id,
        @execution_state = attempt.current_state,
        @environment_name = partition.environment_name,
        @source_instance = partition.source_instance,
        @tenant_scope = partition.tenant_scope,
        @entity_name = partition.entity_name,
        @execution_mode = partition.execution_mode
    FROM ctl.execution_attempt AS attempt
    INNER JOIN ctl.execution_partition AS partition
        ON partition.partition_id = attempt.partition_id
    WHERE attempt.execution_id = @execution_id;

    IF @partition_id IS NULL
        THROW 51602, N'A execução de Data Quality não existe.', 1;

    DECLARE @lock_payload NVARCHAR(2000) = CONCAT(
        DATALENGTH(@environment_name), N':', @environment_name, N'|',
        DATALENGTH(@source_instance), N':', @source_instance, N'|',
        DATALENGTH(@tenant_scope), N':', @tenant_scope, N'|',
        DATALENGTH(@entity_name), N':', @entity_name
    );
    DECLARE @lock_resource NVARCHAR(255) = CONCAT(
        N'V2_APPLY_', CONVERT(NVARCHAR(64), HASHBYTES('SHA2_256', @lock_payload), 2)
    );
    DECLARE @application_lock_result INT;
    EXEC @application_lock_result = sys.sp_getapplock
        @Resource = @lock_resource,
        @LockMode = 'Exclusive',
        @LockOwner = 'Transaction',
        @LockTimeout = 10000,
        @DbPrincipal = 'public';
    IF @application_lock_result < 0
        THROW 51603, N'Não foi possível serializar a avaliação de Data Quality.', 1;

    -- Releitura sob o mesmo lock do apply evita um permit separado do candidate set.
    SELECT
        @execution_state = attempt.current_state,
        @partition_id = partition.partition_id,
        @environment_name = partition.environment_name,
        @source_instance = partition.source_instance,
        @tenant_scope = partition.tenant_scope,
        @entity_name = partition.entity_name,
        @execution_mode = partition.execution_mode
    FROM ctl.execution_attempt AS attempt WITH (UPDLOCK, HOLDLOCK)
    INNER JOIN ctl.execution_partition AS partition WITH (UPDLOCK, HOLDLOCK)
        ON partition.partition_id = attempt.partition_id
    WHERE attempt.execution_id = @execution_id;

    IF @execution_state NOT IN (N'PROMOTED', N'PUBLISHED')
        THROW 51604, N'A execução não está promovida para Data Quality.', 1;

    -- Vigência e SLA usam o instante posterior à espera do fence compartilhado com o apply.
    SET @database_now_utc = SYSUTCDATETIME();

    DECLARE @scope_material NVARCHAR(2000) = CONCAT(
        N'dq-scope-v1|',
        DATALENGTH(@environment_name), N':', @environment_name, N'|',
        DATALENGTH(@source_instance), N':', @source_instance, N'|',
        DATALENGTH(@tenant_scope), N':', @tenant_scope, N'|',
        DATALENGTH(@entity_name), N':', @entity_name, N'|',
        DATALENGTH(@execution_mode), N':', @execution_mode
    );
    DECLARE @scope_fingerprint CHAR(64) = LOWER(CONVERT(
        CHAR(64), HASHBYTES('SHA2_256', CONVERT(VARBINARY(MAX), @scope_material)), 2
    ));
    DECLARE @expected_checks SMALLINT;
    DECLARE @quarantine_sla_seconds BIGINT;

    DECLARE @candidate_rows BIGINT;
    DECLARE @promotion_physical_rows BIGINT;
    DECLARE @promotion_distinct_rows BIGINT;
    DECLARE @promotion_duplicate_rows BIGINT;
    DECLARE @promotion_quarantined_roots BIGINT;
    DECLARE @promotion_unidentified BIGINT;
    DECLARE @promotion_quarantined_stage BIGINT;
    DECLARE @promotion_recorded_at_utc DATETIME2(3);

    SELECT
        @candidate_rows = promotion.candidate_rows,
        @promotion_physical_rows = promotion.physical_rows,
        @promotion_distinct_rows = promotion.distinct_root_keys,
        @promotion_duplicate_rows = promotion.duplicate_rows,
        @promotion_quarantined_roots = promotion.quarantined_root_keys,
        @promotion_unidentified = promotion.unidentified_quarantine_rows,
        @promotion_quarantined_stage = promotion.quarantined_stage_rows,
        @promotion_recorded_at_utc = promotion.promoted_at_utc
    FROM ctl.execution_promotion_result AS promotion WITH (UPDLOCK, HOLDLOCK)
    WHERE promotion.execution_id = @execution_id;

    IF @candidate_rows IS NULL
        THROW 51606, N'A evidência do candidate set está ausente.', 1;

    IF EXISTS (
        SELECT 1
        FROM recon.execution_data_quality_evaluation AS evaluation WITH (UPDLOCK, HOLDLOCK)
        WHERE evaluation.execution_id = @execution_id
          AND (
              evaluation.policy_version <> @policy_version COLLATE Latin1_General_100_BIN2
              OR evaluation.policy_fingerprint
                    <> @policy_fingerprint COLLATE Latin1_General_100_BIN2
              OR evaluation.scope_fingerprint <> @scope_fingerprint
              OR evaluation.candidate_rows <> @candidate_rows
              OR evaluation.promotion_recorded_at_utc <> @promotion_recorded_at_utc
              OR evaluation.completed_checks <> evaluation.expected_checks
              OR evaluation.completed_checks <> (
                  SELECT COUNT_BIG(*)
                  FROM recon.execution_data_quality_check_result AS result WITH (HOLDLOCK)
                  WHERE result.execution_id = @execution_id
              )
          )
    )
        THROW 51607, N'O retry de Data Quality diverge da evidência imutável.', 1;

    DECLARE @has_existing_evaluation BIT = CASE WHEN EXISTS (
        SELECT 1 FROM recon.execution_data_quality_evaluation WITH (UPDLOCK, HOLDLOCK)
        WHERE execution_id = @execution_id
    ) THEN 1 ELSE 0 END;

    IF @execution_state = N'PUBLISHED' AND @has_existing_evaluation = 0
        THROW 51616, N'A avaliação retroativa após publicação foi recusada.', 1;

    IF @execution_state = N'PUBLISHED'
    BEGIN
        IF NOT EXISTS (
            SELECT 1
            FROM recon.execution_data_quality_evaluation AS evaluation WITH (HOLDLOCK)
            INNER JOIN ctl.execution_publication_event AS publication WITH (HOLDLOCK)
                ON publication.execution_id = evaluation.execution_id
            WHERE evaluation.execution_id = @execution_id
              AND evaluation.evaluated_at_utc <= publication.published_at_utc
        )
            THROW 51607, N'O retry de Data Quality diverge da evidência imutável.', 1;

        COMMIT TRANSACTION;
        SELECT
            execution_id, policy_version, policy_fingerprint, evaluation_fingerprint,
            expected_checks, completed_checks, passed_checks, failed_checks,
            evaluated_rows, failed_rows, evaluation_state, evaluated_at_utc
        FROM recon.execution_data_quality_evaluation
        WHERE execution_id = @execution_id;
        RETURN;
    END;

    DECLARE @policy_material NVARCHAR(4000);
    DECLARE @policy_effective_from_utc DATETIME2(3);
    SELECT
        @expected_checks = policy.expected_checks,
        @quarantine_sla_seconds = policy.quarantine_sla_seconds,
        @policy_effective_from_utc = policy.effective_from_utc,
        @policy_material = CONCAT(
            N'dq-policy-v1|',
            DATALENGTH(policy.policy_version), N':', policy.policy_version, N'|',
            policy.scope_fingerprint, N'|', policy.expected_checks, N'|',
            policy.quarantine_sla_seconds, N'|',
            DATALENGTH(policy.threshold_owner_role), N':', policy.threshold_owner_role, N'|',
            DATALENGTH(policy.quarantine_sla_owner_role), N':',
                policy.quarantine_sla_owner_role, N'|',
            DATALENGTH(policy.retention_policy_version), N':',
                policy.retention_policy_version, N'|',
            DATALENGTH(policy.retention_owner_role), N':', policy.retention_owner_role, N'|',
            CONVERT(NVARCHAR(33), policy.effective_from_utc, 126), N'|',
            check_1.check_ordinal, N':', check_1.check_code, N':',
                check_1.maximum_failed_rows, N':', check_1.maximum_failure_basis_points, N':',
                DATALENGTH(check_1.threshold_owner_role), N':', check_1.threshold_owner_role, N'|',
            check_2.check_ordinal, N':', check_2.check_code, N':',
                check_2.maximum_failed_rows, N':', check_2.maximum_failure_basis_points, N':',
                DATALENGTH(check_2.threshold_owner_role), N':', check_2.threshold_owner_role, N'|',
            check_3.check_ordinal, N':', check_3.check_code, N':',
                check_3.maximum_failed_rows, N':', check_3.maximum_failure_basis_points, N':',
                DATALENGTH(check_3.threshold_owner_role), N':', check_3.threshold_owner_role, N'|',
            check_4.check_ordinal, N':', check_4.check_code, N':',
                check_4.maximum_failed_rows, N':', check_4.maximum_failure_basis_points, N':',
                DATALENGTH(check_4.threshold_owner_role), N':', check_4.threshold_owner_role
        )
    FROM ctl.data_quality_policy AS policy WITH (UPDLOCK, HOLDLOCK)
    INNER JOIN ctl.data_quality_check_policy AS check_1 WITH (UPDLOCK, HOLDLOCK)
        ON check_1.policy_version = policy.policy_version
       AND check_1.policy_fingerprint = policy.policy_fingerprint
       AND check_1.check_ordinal = 1 AND check_1.check_code = N'COUNT_EQUATION'
    INNER JOIN ctl.data_quality_check_policy AS check_2 WITH (UPDLOCK, HOLDLOCK)
        ON check_2.policy_version = policy.policy_version
       AND check_2.policy_fingerprint = policy.policy_fingerprint
       AND check_2.check_ordinal = 2 AND check_2.check_code = N'PAGE_TERMINALITY'
    INNER JOIN ctl.data_quality_check_policy AS check_3 WITH (UPDLOCK, HOLDLOCK)
        ON check_3.policy_version = policy.policy_version
       AND check_3.policy_fingerprint = policy.policy_fingerprint
       AND check_3.check_ordinal = 3 AND check_3.check_code = N'PROMOTION_RECONCILIATION'
    INNER JOIN ctl.data_quality_check_policy AS check_4 WITH (UPDLOCK, HOLDLOCK)
        ON check_4.policy_version = policy.policy_version
       AND check_4.policy_fingerprint = policy.policy_fingerprint
       AND check_4.check_ordinal = 4 AND check_4.check_code = N'QUARANTINE_SLA'
    WHERE policy.policy_version = @policy_version COLLATE Latin1_General_100_BIN2
      AND policy.policy_fingerprint = @policy_fingerprint COLLATE Latin1_General_100_BIN2
      AND policy.scope_fingerprint = @scope_fingerprint
      AND policy.policy_state = N'RATIFIED'
      AND policy.effective_from_utc <= @database_now_utc
      AND NOT EXISTS (
          SELECT 1
          FROM ctl.data_quality_policy AS newer WITH (UPDLOCK, HOLDLOCK)
          WHERE newer.scope_fingerprint = policy.scope_fingerprint
            AND newer.effective_from_utc <= @database_now_utc
            AND newer.effective_from_utc > policy.effective_from_utc
      );

    DECLARE @calculated_policy_fingerprint CHAR(64) = LOWER(CONVERT(
        CHAR(64), HASHBYTES('SHA2_256', CONVERT(VARBINARY(MAX), @policy_material)), 2
    ));
    IF @expected_checks <> 4
        OR @quarantine_sla_seconds IS NULL
        OR @policy_material IS NULL
        OR @calculated_policy_fingerprint <> @policy_fingerprint
        THROW 51605, N'A policy de Data Quality não está íntegra, vigente e ratificada.', 1;

    IF @has_existing_evaluation = 1
    BEGIN
        COMMIT TRANSACTION;
        SELECT
            execution_id, policy_version, policy_fingerprint, evaluation_fingerprint,
            expected_checks, completed_checks, passed_checks, failed_checks,
            evaluated_rows, failed_rows, evaluation_state, evaluated_at_utc
        FROM recon.execution_data_quality_evaluation
        WHERE execution_id = @execution_id;
        RETURN;
    END;

    DECLARE @count_rows BIGINT;
    DECLARE @count_physical BIGINT;
    DECLARE @count_distinct BIGINT;
    DECLARE @count_duplicate BIGINT;
    DECLARE @count_valid BIGINT;
    DECLARE @count_quarantined BIGINT;
    DECLARE @count_unidentified BIGINT;
    SELECT
        @count_rows = COUNT_BIG(*),
        @count_physical = MAX(physical_rows),
        @count_distinct = MAX(distinct_root_keys),
        @count_duplicate = MAX(duplicate_rows),
        @count_valid = MAX(valid_rows),
        @count_quarantined = MAX(quarantined_root_keys),
        @count_unidentified = MAX(unidentified_quarantine_rows)
    FROM ctl.execution_count WITH (UPDLOCK, HOLDLOCK)
    WHERE execution_id = @execution_id
      AND count_phase = N'STAGING_KERNEL';

    DECLARE @stage_rows BIGINT = (
        SELECT COUNT_BIG(*) FROM stg.execution_record WITH (UPDLOCK, HOLDLOCK)
        WHERE execution_id = @execution_id
    );
    DECLARE @actual_candidate_rows BIGINT = (
        SELECT COUNT_BIG(*) FROM stg.execution_candidate WITH (UPDLOCK, HOLDLOCK)
        WHERE execution_id = @execution_id
    );
    DECLARE @actual_quarantine_rows BIGINT = (
        SELECT COUNT_BIG(*) FROM recon.quarantine_record WITH (UPDLOCK, HOLDLOCK)
        WHERE execution_id = @execution_id
    );

    DECLARE @page_rows BIGINT;
    DECLARE @minimum_page INT;
    DECLARE @maximum_page INT;
    DECLARE @terminal_pages BIGINT;
    DECLARE @terminal_page INT;
    DECLARE @page_physical_rows BIGINT;
    DECLARE @non_terminal_empty_pages BIGINT;
    DECLARE @invalid_terminal_pages BIGINT;
    DECLARE @oversized_root_pages BIGINT;
    ;WITH ranked_page AS (
        SELECT
            page_number, page_attempt, requested_page_size, physical_rows, distinct_root_keys,
            response_bytes, terminal_empty_page, terminal_evidence_kind,
            ROW_NUMBER() OVER (PARTITION BY page_number ORDER BY page_attempt DESC) AS authority_rank
        FROM ctl.execution_page_audit WITH (UPDLOCK, HOLDLOCK)
        WHERE execution_id = @execution_id
    ), authoritative_page AS (
        SELECT page_number, requested_page_size, physical_rows, distinct_root_keys,
               terminal_empty_page, terminal_evidence_kind
        FROM ranked_page WHERE authority_rank = 1
    )
    SELECT
        @page_rows = COUNT_BIG(*),
        @minimum_page = MIN(page_number),
        @maximum_page = MAX(page_number),
        @terminal_pages = COALESCE(SUM(
            CASE WHEN terminal_evidence_kind <> N'NONE' THEN 1 ELSE 0 END
        ), 0),
        @terminal_page = MAX(
            CASE WHEN terminal_evidence_kind <> N'NONE' THEN page_number END
        ),
        @page_physical_rows = COALESCE(SUM(physical_rows), 0),
        @non_terminal_empty_pages = COALESCE(SUM(
            CASE WHEN terminal_evidence_kind = N'NONE' AND physical_rows = 0
                 THEN 1 ELSE 0 END
        ), 0),
        @invalid_terminal_pages = COALESCE(SUM(
            CASE
                WHEN terminal_evidence_kind = N'DATA_EXPORT_EMPTY_PAGE'
                     AND (terminal_empty_page <> 1
                          OR physical_rows <> 0 OR distinct_root_keys <> 0)
                    THEN 1
                WHEN terminal_evidence_kind = N'GRAPHQL_PAGE_INFO'
                     AND (terminal_empty_page <> 0 OR physical_rows = 0)
                    THEN 1
                WHEN terminal_evidence_kind NOT IN (
                    N'NONE', N'DATA_EXPORT_EMPTY_PAGE', N'GRAPHQL_PAGE_INFO'
                ) THEN 1
                ELSE 0
            END
        ), 0),
        @oversized_root_pages = COALESCE(SUM(
            CASE WHEN distinct_root_keys > requested_page_size THEN 1 ELSE 0 END
        ), 0)
    FROM authoritative_page;

    DECLARE @divergent_page_attempts BIGINT = (
        SELECT COUNT_BIG(*)
        FROM (
            SELECT page_number
            FROM ctl.execution_page_audit WITH (UPDLOCK, HOLDLOCK)
            WHERE execution_id = @execution_id
            GROUP BY page_number
            HAVING MIN(requested_page_size) <> MAX(requested_page_size)
                OR MIN(physical_rows) <> MAX(physical_rows)
                OR MIN(distinct_root_keys) <> MAX(distinct_root_keys)
                OR MIN(response_bytes) <> MAX(response_bytes)
                OR MIN(CONVERT(TINYINT, terminal_empty_page))
                    <> MAX(CONVERT(TINYINT, terminal_empty_page))
                OR MIN(terminal_evidence_kind) <> MAX(terminal_evidence_kind)
        ) AS divergent
    );

    DECLARE @overdue_quarantine_rows BIGINT = (
        SELECT COUNT_BIG(*)
        FROM recon.quarantine_record WITH (UPDLOCK, HOLDLOCK)
        WHERE execution_id = @execution_id
          AND quarantined_at_utc < DATEADD(
              SECOND,
              -CONVERT(INT, CASE WHEN @quarantine_sla_seconds > 2147483647
                  THEN 2147483647 ELSE @quarantine_sla_seconds END),
              @database_now_utc
          )
    );

    DECLARE @measurements TABLE (
        check_ordinal SMALLINT NOT NULL PRIMARY KEY,
        check_code NVARCHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL,
        evaluated_rows BIGINT NOT NULL,
        failed_rows BIGINT NOT NULL,
        reason_code NVARCHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL
    );

    INSERT INTO @measurements (
        check_ordinal, check_code, evaluated_rows, failed_rows, reason_code
    ) VALUES
    (
        1, N'COUNT_EQUATION', 1,
        CASE WHEN @count_rows = 1
              AND @count_physical = @count_distinct + @count_duplicate + @count_unidentified
              AND @count_distinct = @count_valid + @count_quarantined
             THEN 0 ELSE 1 END,
        N'COUNT_EQUATION_MISMATCH'
    ),
    (
        2, N'PAGE_TERMINALITY', 1,
        CASE WHEN @page_rows > 0
              AND @minimum_page = 1
              AND @page_rows = @maximum_page
              AND @terminal_pages = 1
              AND @terminal_page = @maximum_page
              AND @non_terminal_empty_pages = 0
              AND @invalid_terminal_pages = 0
              AND @oversized_root_pages = 0
              AND @divergent_page_attempts = 0
              AND (@page_physical_rows = @stage_rows AND @entity_name<>N'manifestos'
                   OR @entity_name=N'manifestos' AND EXISTS(SELECT 1 FROM ctl.manifesto_promotion_result m
                       WHERE m.execution_id=@execution_id AND m.validation_state=N'PASSED'
                       AND m.physical_observation_rows=@page_physical_rows AND m.reduced_root_rows=@stage_rows
                       AND m.quarantined_observation_rows=0
                       AND m.physical_observation_rows=(SELECT COUNT_BIG(*) FROM stg.manifesto_observation WHERE execution_id=@execution_id)))
             THEN 0 ELSE 1 END,
        N'PAGE_TERMINALITY_MISMATCH'
    ),
    (
        3, N'PROMOTION_RECONCILIATION', 1,
        CASE WHEN @count_rows = 1
              AND @promotion_physical_rows = @count_physical
              AND @promotion_distinct_rows = @count_distinct
              AND @promotion_duplicate_rows = @count_duplicate
              AND @candidate_rows = @count_valid
              AND @promotion_quarantined_roots = @count_quarantined
              AND @promotion_unidentified = @count_unidentified
              AND @promotion_physical_rows = @stage_rows
              AND @promotion_distinct_rows
                    = @candidate_rows + @promotion_quarantined_roots
              AND @promotion_physical_rows = @promotion_distinct_rows
                    + @promotion_duplicate_rows + @promotion_unidentified
              AND @candidate_rows = @actual_candidate_rows
              AND @actual_quarantine_rows = @promotion_quarantined_stage
             THEN 0 ELSE 1 END,
        N'PROMOTION_RECONCILIATION_MISMATCH'
    ),
    (
        4, N'QUARANTINE_SLA', @actual_quarantine_rows, @overdue_quarantine_rows,
        N'QUARANTINE_SLA_EXCEEDED'
    );

    DECLARE @results TABLE (
        check_ordinal SMALLINT NOT NULL PRIMARY KEY,
        check_code NVARCHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL,
        evaluated_rows BIGINT NOT NULL,
        failed_rows BIGINT NOT NULL,
        failure_basis_points INT NOT NULL,
        check_state NVARCHAR(16) COLLATE Latin1_General_100_BIN2 NOT NULL,
        reason_code NVARCHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL
    );

    INSERT INTO @results (
        check_ordinal, check_code, evaluated_rows, failed_rows,
        failure_basis_points, check_state, reason_code
    )
    SELECT
        measurement.check_ordinal,
        measurement.check_code,
        measurement.evaluated_rows,
        measurement.failed_rows,
        CASE WHEN measurement.evaluated_rows = 0 THEN 0 ELSE CONVERT(INT, CEILING(
            CONVERT(DECIMAL(38, 10), measurement.failed_rows) * 10000
                / CONVERT(DECIMAL(38, 10), measurement.evaluated_rows)
        )) END,
        CASE WHEN measurement.failed_rows <= policy.maximum_failed_rows
              AND (
                  (measurement.evaluated_rows = 0 AND measurement.failed_rows = 0)
                  OR CONVERT(DECIMAL(38, 0), measurement.failed_rows) * 10000
                        <= CONVERT(DECIMAL(38, 0), measurement.evaluated_rows)
                            * policy.maximum_failure_basis_points
              )
             THEN N'PASSED' ELSE N'FAILED' END,
        CASE WHEN measurement.failed_rows = 0
                  OR (measurement.failed_rows <= policy.maximum_failed_rows
                      AND (
                          (measurement.evaluated_rows = 0 AND measurement.failed_rows = 0)
                          OR CONVERT(DECIMAL(38, 0), measurement.failed_rows) * 10000
                                <= CONVERT(DECIMAL(38, 0), measurement.evaluated_rows)
                                    * policy.maximum_failure_basis_points
                      ))
             THEN N'CHECK_WITHIN_THRESHOLD'
             ELSE measurement.reason_code END
    FROM @measurements AS measurement
    INNER JOIN ctl.data_quality_check_policy AS policy WITH (UPDLOCK, HOLDLOCK)
        ON policy.policy_version = @policy_version COLLATE Latin1_General_100_BIN2
       AND policy.policy_fingerprint = @policy_fingerprint COLLATE Latin1_General_100_BIN2
       AND policy.check_ordinal = measurement.check_ordinal
       AND policy.check_code = measurement.check_code;

    IF (SELECT COUNT_BIG(*) FROM @results) <> @expected_checks
        THROW 51608, N'A execução parcial dos checks foi recusada.', 1;

    DECLARE @completed_checks SMALLINT = CONVERT(SMALLINT, (SELECT COUNT_BIG(*) FROM @results));
    DECLARE @passed_checks SMALLINT = CONVERT(SMALLINT, (
        SELECT COUNT_BIG(*) FROM @results WHERE check_state = N'PASSED'
    ));
    DECLARE @failed_checks SMALLINT = CONVERT(SMALLINT, (
        SELECT COUNT_BIG(*) FROM @results WHERE check_state = N'FAILED'
    ));
    DECLARE @evaluated_rows BIGINT = (SELECT SUM(evaluated_rows) FROM @results);
    DECLARE @failed_rows BIGINT = (SELECT SUM(failed_rows) FROM @results);
    DECLARE @evaluation_state NVARCHAR(16) =
        CASE WHEN @failed_checks = 0 THEN N'PASSED' ELSE N'FAILED' END;

    DECLARE @evaluation_material NVARCHAR(2000) = CONCAT(
        N'dq-evaluation-v1|', @policy_version, N'|', @policy_fingerprint, N'|',
        @scope_fingerprint, N'|',
        CONVERT(NVARCHAR(36), @execution_id), N'|', @candidate_rows, N'|',
        CONVERT(NVARCHAR(33), @promotion_recorded_at_utc, 126), N'|',
        @completed_checks, N'|', @passed_checks, N'|', @failed_checks, N'|',
        @evaluated_rows, N'|', @failed_rows, N'|', @evaluation_state, N'|',
        (SELECT CONCAT(check_code, N':', evaluated_rows, N':', failed_rows, N':', check_state)
         FROM @results WHERE check_ordinal = 1), N'|',
        (SELECT CONCAT(check_code, N':', evaluated_rows, N':', failed_rows, N':', check_state)
         FROM @results WHERE check_ordinal = 2), N'|',
        (SELECT CONCAT(check_code, N':', evaluated_rows, N':', failed_rows, N':', check_state)
         FROM @results WHERE check_ordinal = 3), N'|',
        (SELECT CONCAT(check_code, N':', evaluated_rows, N':', failed_rows, N':', check_state)
         FROM @results WHERE check_ordinal = 4)
    );
    DECLARE @evaluation_fingerprint CHAR(64) = LOWER(CONVERT(
        CHAR(64), HASHBYTES('SHA2_256', CONVERT(VARBINARY(MAX), @evaluation_material)), 2
    ));

    INSERT INTO recon.execution_data_quality_evaluation (
        execution_id, policy_version, policy_fingerprint, scope_fingerprint,
        evaluation_fingerprint,
        expected_checks, completed_checks, passed_checks, failed_checks,
        evaluated_rows, failed_rows, evaluation_state, candidate_rows,
        promotion_recorded_at_utc, evaluated_at_utc
    ) VALUES (
        @execution_id, @policy_version, @policy_fingerprint, @scope_fingerprint,
        @evaluation_fingerprint,
        @expected_checks, @completed_checks, @passed_checks, @failed_checks,
        @evaluated_rows, @failed_rows, @evaluation_state, @candidate_rows,
        @promotion_recorded_at_utc, @database_now_utc
    );

    INSERT INTO recon.execution_data_quality_check_result (
        execution_id, check_ordinal, check_code, evaluated_rows, failed_rows,
        failure_basis_points, check_state, reason_code, evaluated_at_utc
    )
    SELECT
        @execution_id, check_ordinal, check_code, evaluated_rows, failed_rows,
        failure_basis_points, check_state, reason_code, @database_now_utc
    FROM @results;

    IF @@ROWCOUNT <> @expected_checks
        THROW 51609, N'A persistência parcial dos checks foi recusada.', 1;

    COMMIT TRANSACTION;

    SELECT
        execution_id, policy_version, policy_fingerprint, evaluation_fingerprint,
        expected_checks, completed_checks, passed_checks, failed_checks,
        evaluated_rows, failed_rows, evaluation_state, evaluated_at_utc
    FROM recon.execution_data_quality_evaluation
    WHERE execution_id = @execution_id;
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
        OR @entity IS NULL OR @entity COLLATE Latin1_General_100_BIN2 NOT IN(N'coletas',N'fretes',N'manifestos',N'cotacoes',N'localizacao_cargas')
        THROW 52301,N'RUNTIME_RECOVERY_INPUT_INVALID',1;
    IF @entity=N'cotacoes' AND ctl.fn_runtime_tariff_valid(@execution_id,@reference_release_id)<>1
       THROW 52843,N'TARIFF_SCOPE_OR_REFERENCE_REJECTED',1;
    IF @entity<>N'cotacoes' AND @reference_release_id IS NOT NULL THROW 52843,N'TARIFF_ENTITY_MISMATCH',1;
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
        IF @operation=N'SEAL'
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
            EXEC recon.usp_evaluate_execution_data_quality @execution_id,@policy_version,@policy_fingerprint;
            IF @entity=N'coletas' EXEC core.usp_apply_reconcile_publish_coletas @execution_id,@contract_version,
                @contract_fingerprint,@configuration_version,@configuration_fingerprint;
            ELSE IF @entity=N'manifestos' EXEC core.usp_apply_reconcile_publish_manifestos @execution_id,@contract_version,
                @contract_fingerprint,@configuration_version,@configuration_fingerprint;
            ELSE IF @entity=N'cotacoes' EXEC core.usp_apply_reconcile_publish_cotacoes @execution_id,@contract_version,
                @contract_fingerprint,@configuration_version,@configuration_fingerprint,@reference_release_id;
            ELSE IF @entity=N'localizacao_cargas' EXEC core.usp_apply_reconcile_publish_localizacao_cargas @execution_id,@contract_version,
                @contract_fingerprint,@configuration_version,@configuration_fingerprint;
            ELSE EXEC core.usp_apply_reconcile_publish_fretes @execution_id,@contract_version,
                @contract_fingerprint,@configuration_version,@configuration_fingerprint;
            COMMIT TRANSACTION;
            RETURN;
        END;
        SELECT @reason AS reason,@execution_state AS execution_state,@lease_valid AS lease_valid,
            @contract_verified AS contract_verified,@candidates AS candidate_rows,@quality AS quality,@revision AS revision,
            @execution_id AS execution_id,COALESCE(typed.inserted_rows,result.inserted_rows) AS inserted_rows,
            COALESCE(typed.updated_rows,result.updated_rows) AS updated_rows,
            COALESCE(typed.reactivated_rows,result.reactivated_rows) AS reactivated_rows,
            COALESCE(typed.noop_rows,result.noop_rows) AS noop_rows,
            COALESCE(typed.stale_noop_rows,result.stale_noop_rows) AS stale_noop_rows,result.reconciled_at_utc,result.published_at_utc,
            publication.incremental_frontier_before_utc,publication.incremental_frontier_after_utc
        FROM (VALUES(1)) anchor(value)
        LEFT JOIN recon.execution_reconciliation_result result ON result.execution_id=@execution_id AND @reason=N'PUBLISHED'
        LEFT JOIN ctl.execution_publication_event publication ON publication.execution_id=result.execution_id
        LEFT JOIN ctl.runtime_coleta_publication_receipt typed ON typed.execution_id=result.execution_id AND @entity=N'coletas';
        COMMIT TRANSACTION;
    END TRY
    BEGIN CATCH
        IF XACT_STATE()<>0 ROLLBACK TRANSACTION;
        THROW;
    END CATCH;
END;
GO

CREATE OR ALTER PROCEDURE ctl.usp_runtime_status
    @execution_id UNIQUEIDENTIFIER,@operation NVARCHAR(MAX),@expected_identity NVARCHAR(MAX),
    @contract_material NVARCHAR(MAX),@policy_version NVARCHAR(MAX),@policy_fingerprint NVARCHAR(MAX),
    @expected_revision NVARCHAR(MAX),@entity NVARCHAR(MAX),@reference_release_id BIGINT=NULL
AS
BEGIN
    -- B54: validate current SQL authority consumption before any effect/read.
    DECLARE @b54_actual NVARCHAR(MAX)=(SELECT LOWER(COALESCE(CONVERT(NVARCHAR(36),@execution_id),N'')) AS [execution] FOR JSON PATH,WITHOUT_ARRAY_WRAPPER,INCLUDE_NULL_VALUES);
    IF ctl.fn_runtime_consumed_scope(@b54_actual,0,NULL)<>1 THROW 52500,N'RUNTIME_DURABLE_CONSUMER_FENCE_REQUIRED',1;

    SET NOCOUNT ON;
    IF @operation IS NULL OR @operation COLLATE Latin1_General_100_BIN2<>N'READ' OR DATALENGTH(@operation)<>8
        THROW 52416,N'STATUS_CANNOT_MUTATE_RUNTIME',1;
    EXEC ctl.usp_runtime_recovery @execution_id,N'READ',@expected_identity,@contract_material,
        @policy_version,@policy_fingerprint,@expected_revision,@entity,@reference_release_id;
END;
GO

CREATE OR ALTER PROCEDURE ctl.usp_runtime_authorization
    @operation NVARCHAR(MAX), @invocation_id UNIQUEIDENTIFIER, @authority_id UNIQUEIDENTIFIER,
    @action NVARCHAR(MAX), @execution_id UNIQUEIDENTIFIER, @scope_material NVARCHAR(MAX),
    @policy_fingerprint NVARCHAR(MAX), @receipt_id UNIQUEIDENTIFIER=NULL
AS
BEGIN
    SET NOCOUNT ON; SET XACT_ABORT ON;
    IF @operation IS NULL OR @operation COLLATE Latin1_General_100_BIN2 NOT IN(N'AUTHORIZE',N'CONSUME')
        OR DATALENGTH(@operation)<>2*LEN(@operation) OR @invocation_id IS NULL OR @execution_id IS NULL
        OR @authority_id IS NULL OR @action IS NULL OR @action COLLATE Latin1_General_100_BIN2 NOT IN(N'RUN',N'REPLAY',N'FORCE_RUN',N'STATUS')
        OR DATALENGTH(@action)<>2*LEN(@action)
        OR @scope_material IS NULL OR DATALENGTH(@scope_material)>8000 OR ISJSON(@scope_material)<>1
        OR @policy_fingerprint IS NULL OR DATALENGTH(@policy_fingerprint)<>128 OR @policy_fingerprint COLLATE Latin1_General_100_BIN2 LIKE '%[^a-f0-9]%'
        THROW 52411,N'AUTHORIZATION_REQUEST_INVALID',1;
    DECLARE @keys TABLE(name NVARCHAR(32) COLLATE Latin1_General_100_BIN2 PRIMARY KEY,maximum INT NOT NULL);
    INSERT @keys VALUES(N'version',32),(N'invocation',36),(N'action',32),(N'execution',36),(N'environment',32),
        (N'source',128),(N'tenant',128),(N'workload',128),(N'entity',128),(N'mode',16),(N'start',30),(N'endExclusive',30),
        (N'strategy',32),(N'idempotency',200),(N'replayOf',36),(N'cycle',36),(N'planVersion',128),(N'planHash',64),
        (N'contractVersion',128),(N'contractHash',64),(N'configurationVersion',128),(N'configurationHash',64);
    DECLARE @tariff_scope BIT=CASE WHEN JSON_VALUE(@scope_material,'$.version')=N'runtime-scope-tariff-v1' THEN 1 ELSE 0 END;
    IF @tariff_scope=1
    BEGIN
      INSERT @keys VALUES(N'referenceReleaseId',20);
      IF JSON_VALUE(@scope_material,'$.entity')<>N'cotacoes' OR JSON_VALUE(@scope_material,'$.workload')<>N'cotacoes'
         OR JSON_VALUE(@scope_material,'$.mode')<>N'BACKFILL'
         OR TRY_CONVERT(BIGINT,JSON_VALUE(@scope_material,'$.referenceReleaseId')) IS NULL
         OR TRY_CONVERT(BIGINT,JSON_VALUE(@scope_material,'$.referenceReleaseId'))<1
         THROW 52844,N'TARIFF_SCOPE_SCHEMA',1;
    END;
    IF (SELECT COUNT_BIG(*) FROM OPENJSON(@scope_material))<>22+CONVERT(INT,@tariff_scope)
        OR EXISTS(SELECT 1 FROM OPENJSON(@scope_material) j LEFT JOIN @keys k ON k.name=j.[key] COLLATE Latin1_General_100_BIN2
            WHERE k.name IS NULL OR DATALENGTH(k.name)<>DATALENGTH(j.[key]) OR j.type<>1 OR DATALENGTH(j.value)>2*k.maximum
                OR (j.[key]<>N'replayOf' AND DATALENGTH(j.value)=0))
        OR EXISTS(SELECT 1 FROM OPENJSON(@scope_material) GROUP BY [key] COLLATE Latin1_General_100_BIN2 HAVING COUNT_BIG(*)<>1)
        OR JSON_VALUE(@scope_material,'$.version') COLLATE Latin1_General_100_BIN2<>CASE WHEN @tariff_scope=1 THEN N'runtime-scope-tariff-v1' ELSE N'runtime-scope-v1' END
        OR TRY_CONVERT(UNIQUEIDENTIFIER,JSON_VALUE(@scope_material,'$.invocation')) IS NULL
        OR TRY_CONVERT(UNIQUEIDENTIFIER,JSON_VALUE(@scope_material,'$.execution')) IS NULL
        OR ISNULL(TRY_CONVERT(UNIQUEIDENTIFIER,JSON_VALUE(@scope_material,'$.invocation')),'00000000-0000-0000-0000-000000000000')<>@invocation_id
        OR ISNULL(TRY_CONVERT(UNIQUEIDENTIFIER,JSON_VALUE(@scope_material,'$.execution')),'00000000-0000-0000-0000-000000000000')<>@execution_id
        OR JSON_VALUE(@scope_material,'$.action') COLLATE Latin1_General_100_BIN2<>@action
        OR TRY_CONVERT(UNIQUEIDENTIFIER,JSON_VALUE(@scope_material,'$.cycle')) IS NULL
        OR TRY_CONVERT(DATETIME2(3),JSON_VALUE(@scope_material,'$.start')) IS NULL
        OR TRY_CONVERT(DATETIME2(3),JSON_VALUE(@scope_material,'$.endExclusive')) IS NULL
        OR TRY_CONVERT(DATETIME2(3),JSON_VALUE(@scope_material,'$.start'))>=TRY_CONVERT(DATETIME2(3),JSON_VALUE(@scope_material,'$.endExclusive'))
        OR EXISTS(SELECT 1 FROM OPENJSON(@scope_material) WHERE [key] IN(N'planHash',N'contractHash',N'configurationHash')
            AND (DATALENGTH(value)<>128 OR value COLLATE Latin1_General_100_BIN2 LIKE '%[^a-f0-9]%'))
        THROW 52411,N'AUTHORIZATION_SCOPE_INVALID',1;
    DECLARE @now DATETIME2(3)=SYSUTCDATETIME(),@until DATETIME2(3),@reason VARCHAR(40)='AUTHORITY_UNCONFIGURED',
        @sid VARBINARY(85)=SUSER_SID(ORIGINAL_LOGIN()),@audit UNIQUEIDENTIFIER,@mapping BIGINT,@scope_version BIGINT,
        @hash CHAR(64)=LOWER(CONVERT(CHAR(64),HASHBYTES('SHA2_256',@scope_material),2)),@seconds INT=1,
        @environment NVARCHAR(32)=JSON_VALUE(@scope_material,'$.environment'),
        @source NVARCHAR(128)=JSON_VALUE(@scope_material,'$.source'),@tenant NVARCHAR(128)=JSON_VALUE(@scope_material,'$.tenant'),
        @workload NVARCHAR(128)=JSON_VALUE(@scope_material,'$.workload'),@mode NVARCHAR(16)=JSON_VALUE(@scope_material,'$.mode');
    IF @environment IS NULL OR @source IS NULL OR @tenant IS NULL OR @workload IS NULL OR @mode IS NULL
        OR @mode COLLATE Latin1_General_100_BIN2 NOT IN(N'INCREMENTAL',N'BACKFILL',N'BOOTSTRAP',N'REPLAY')
        OR (@action=N'REPLAY' AND @mode<>N'REPLAY') OR (@mode=N'REPLAY' AND @action NOT IN(N'REPLAY',N'STATUS'))
        OR (@mode=N'REPLAY' AND TRY_CONVERT(UNIQUEIDENTIFIER,JSON_VALUE(@scope_material,'$.replayOf')) IS NULL)
        OR (@mode<>N'REPLAY' AND DATALENGTH(JSON_VALUE(@scope_material,'$.replayOf'))<>0)
        THROW 52411,N'AUTHORIZATION_SCOPE_INVALID',1;
    BEGIN TRY
        BEGIN TRANSACTION;
        DECLARE @lock_result INT,@lock_resource NVARCHAR(255)=N'V2_AUTH_'+CONVERT(NVARCHAR(36),@invocation_id);
        EXEC @lock_result=sys.sp_getapplock @Resource=@lock_resource,@LockMode='Exclusive',@LockOwner='Transaction',@LockTimeout=10000;
        IF @lock_result<0 THROW 52412,N'AUTHORIZATION_LOCK_UNAVAILABLE',1;
        SET @now=SYSUTCDATETIME();
        IF EXISTS(SELECT 1 FROM ctl.runtime_authority_configuration WITH(HOLDLOCK) WHERE singleton=1 AND enabled=1
            AND authority_id=@authority_id AND server_name=CONVERT(NVARCHAR(128),SERVERPROPERTY('ServerName')) COLLATE Latin1_General_100_BIN2
            AND database_name=DB_NAME() COLLATE Latin1_General_100_BIN2 AND policy_fingerprint=@policy_fingerprint)
        BEGIN
            SELECT @seconds=capability_seconds FROM ctl.runtime_authority_configuration WITH(HOLDLOCK) WHERE singleton=1;
            SET @reason='IDENTITY_UNMAPPED';
            IF ISNULL(CONVERT(NVARCHAR(40),CONNECTIONPROPERTY('auth_scheme')),N'') NOT IN(N'NTLM',N'KERBEROS') SET @reason='AUTHENTICATION_INVALID';
            ELSE IF @sid IS NULL OR SUSER_SID()<>@sid OR ORIGINAL_LOGIN()<>SUSER_SNAME() SET @reason='CONTEXT_CHANGED';
            ELSE IF IS_SRVROLEMEMBER('sysadmin')=1 OR IS_MEMBER('db_owner')=1
                OR HAS_PERMS_BY_NAME(NULL,NULL,'CONTROL SERVER')=1 OR HAS_PERMS_BY_NAME(DB_NAME(),'DATABASE','CONTROL')=1
                OR HAS_PERMS_BY_NAME(N'ctl.runtime_identity_mapping','OBJECT','SELECT')=1
                OR HAS_PERMS_BY_NAME(DB_NAME(),'DATABASE','ALTER ANY ROLE')=1 SET @reason='PRIVILEGE_EXCESSIVE';
            ELSE IF NOT EXISTS(SELECT 1 FROM sys.server_principals WHERE sid=@sid AND type IN('U','G') AND is_disabled=0)
                SET @reason='IDENTITY_DISABLED_OR_UNVERIFIABLE';
            ELSE
            BEGIN
                SELECT @audit=audit_reference,@mapping=mapping_version,@until=valid_until_utc
                FROM ctl.runtime_identity_mapping WITH(HOLDLOCK) WHERE original_sid=@sid AND revoked=0
                    AND valid_from_utc<=@now AND valid_until_utc>@now
                    AND ((@action=N'STATUS' AND observer=1) OR (@action=N'RUN' AND executor=1)
                        OR (@action=N'REPLAY' AND executor=1 AND replay=1) OR (@action=N'FORCE_RUN' AND executor=1 AND force_run=1));
                IF @audit IS NOT NULL
                BEGIN
                    SET @reason='SCOPE_REJECTED';
                    SELECT @scope_version=scope_version FROM ctl.runtime_identity_scope WITH(HOLDLOCK)
                    WHERE original_sid=@sid AND environment_name=@environment AND source_instance=@source AND tenant_scope=@tenant
                        AND workload=@workload AND mode=@mode AND policy_fingerprint=@policy_fingerprint AND revoked=0;
                    IF @scope_version IS NOT NULL SET @reason='AUTHORIZED';
                END;
            END;
        END;
        IF @until IS NULL OR @until>DATEADD(SECOND,@seconds,@now) SET @until=DATEADD(SECOND,@seconds,@now);
        IF @operation=N'AUTHORIZE'
        BEGIN
            IF EXISTS(SELECT 1 FROM ctl.runtime_authorization_decision WITH(UPDLOCK,HOLDLOCK) WHERE invocation_id=@invocation_id
                AND (authority_id<>@authority_id OR action<>@action OR execution_id<>@execution_id OR scope_hash<>@hash
                    OR scope_material<>@scope_material OR DATALENGTH(scope_material)<>DATALENGTH(@scope_material)
                    OR policy_fingerprint<>@policy_fingerprint OR ISNULL(audit_reference,'00000000-0000-0000-0000-000000000000')
                        <>ISNULL(@audit,'00000000-0000-0000-0000-000000000000')))
                THROW 52413,N'AUTHORIZATION_INVOCATION_CONFLICT',1;
            IF NOT EXISTS(SELECT 1 FROM ctl.runtime_authorization_decision WITH(UPDLOCK,HOLDLOCK) WHERE invocation_id=@invocation_id)
                INSERT ctl.runtime_authorization_decision VALUES(@invocation_id,NEWID(),@authority_id,@action,@execution_id,
                    @scope_material,@hash,@audit,@mapping,@scope_version,@policy_fingerprint,
                    CASE WHEN @reason='AUTHORIZED' THEN 'ALLOW' ELSE 'DENY' END,@reason,@now,@until);
            COMMIT TRANSACTION;
            SELECT receipt_id,decision,reason,audit_reference,mapping_version,scope_version,policy_fingerprint,
                authorized_at_utc,valid_until_utc,scope_hash,CAST(NULL AS BIGINT) fence
            FROM ctl.runtime_authorization_decision WHERE invocation_id=@invocation_id;
            RETURN;
        END;
        IF @reason<>'AUTHORIZED' THROW 52414,N'AUTHORIZATION_CONSUMPTION_REJECTED',1;
        IF NOT EXISTS(SELECT 1 FROM ctl.runtime_authorization_decision WITH(UPDLOCK,HOLDLOCK) WHERE invocation_id=@invocation_id
            AND receipt_id=@receipt_id AND decision='ALLOW' AND authority_id=@authority_id AND action=@action AND execution_id=@execution_id
            AND scope_hash=@hash AND scope_material=@scope_material AND DATALENGTH(scope_material)=DATALENGTH(@scope_material)
            AND mapping_version=@mapping AND scope_version=@scope_version AND audit_reference=@audit
            AND policy_fingerprint=@policy_fingerprint AND authorized_at_utc<=@now AND valid_until_utc>@now)
            THROW 52414,N'AUTHORIZATION_CONSUMPTION_REJECTED',1;
        IF EXISTS(SELECT 1 FROM ctl.runtime_authorization_consumption WITH(UPDLOCK,HOLDLOCK) WHERE invocation_id=@invocation_id)
            THROW 52415,N'AUTHORIZATION_ALREADY_CONSUMED',1;
        -- Intent links the exact existing runtime occurrence. Recovery reads that occurrence;
        -- NOT_FOUND permits only its original idempotent start, never a replacement execution_id.
        INSERT ctl.runtime_authorization_consumption(invocation_id,execution_id,consumed_at_utc) VALUES(@invocation_id,@execution_id,@now);
        COMMIT TRANSACTION;
        SELECT d.receipt_id,d.decision,d.reason,d.audit_reference,d.mapping_version,d.scope_version,d.policy_fingerprint,
            d.authorized_at_utc,d.valid_until_utc,d.scope_hash,c.fence
        FROM ctl.runtime_authorization_decision d JOIN ctl.runtime_authorization_consumption c ON c.invocation_id=d.invocation_id
        WHERE d.invocation_id=@invocation_id;
    END TRY
    BEGIN CATCH
        IF XACT_STATE()<>0 ROLLBACK TRANSACTION;
        THROW;
    END CATCH;
END;
GO