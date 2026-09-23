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
