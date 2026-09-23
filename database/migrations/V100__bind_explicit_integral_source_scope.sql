-- Integral local source scope: synthetic namespaces, exact run association, unchanged business rules.
SET ANSI_NULLS ON; SET QUOTED_IDENTIFIER ON;
IF DB_NAME()<>N'ETL_SISTEMA_V2_SHADOW' OR CONVERT(NVARCHAR(128),SERVERPROPERTY('MachineName'))<>CONVERT(NVARCHAR(128),HOST_NAME())
 THROW 53840,N'INTEGRAL_MIGRATION_LOCAL_SHADOW_REQUIRED',1;
GO
CREATE FUNCTION ctl.fn_local_synthetic_scope(@value NVARCHAR(128)) RETURNS BIT WITH SCHEMABINDING AS
BEGIN
 RETURN CASE WHEN @value COLLATE Latin1_General_100_BIN2 LIKE N'SYNTHETIC[_]%'
 AND LEN(@value) BETWEEN 11 AND 40 AND DATALENGTH(@value)=LEN(@value)*2
 AND @value COLLATE Latin1_General_100_BIN2 NOT LIKE N'%[^A-Z0-9_]%' THEN 1 ELSE 0 END;
END;
GO
CREATE FUNCTION ctl.fn_analytic_source_scope(@run UNIQUEIDENTIFIER,@source NVARCHAR(128),@tenant NVARCHAR(128)) RETURNS BIT AS
BEGIN
 RETURN CASE WHEN EXISTS(SELECT 1 FROM ctl.analytic_lab_run r WHERE r.run_id=@run
 AND ((r.source_instance COLLATE Latin1_General_100_BIN2=@source AND r.tenant_scope COLLATE Latin1_General_100_BIN2=@tenant)
 OR (r.source_instance='SYNTHETIC_ANALYTIC_LAB' AND r.tenant_scope='SYNTHETIC_ANALYTIC_TENANT'
 AND @source COLLATE Latin1_General_100_BIN2=N'LOCAL_V2' AND @tenant COLLATE Latin1_General_100_BIN2=N'LOCAL_V2')))
 THEN 1 ELSE 0 END;
END;
GO
ALTER TABLE ctl.relational_lab_run DROP CONSTRAINT CK_relational_lab_run_scope;
ALTER TABLE ctl.relational_lab_run WITH CHECK ADD CONSTRAINT CK_relational_lab_run_scope CHECK(
 ctl.fn_local_synthetic_scope(source_instance)=1 AND ctl.fn_local_synthetic_scope(tenant_scope)=1
 AND contract_version=N'synthetic-relational-v1' AND contract_fingerprint NOT LIKE '%[^a-f0-9]%' AND LEN(contract_fingerprint)=64);
ALTER TABLE ctl.expansion_lab_run DROP CONSTRAINT CK_expansion_run_scope;
ALTER TABLE ctl.expansion_lab_run WITH CHECK ADD CONSTRAINT CK_expansion_run_scope CHECK(
 ctl.fn_local_synthetic_scope(source_instance)=1 AND ctl.fn_local_synthetic_scope(tenant_scope)=1 AND contract_version='expansion-synthetic-v1');
ALTER TABLE ctl.analytic_lab_run DROP CONSTRAINT CK_analytic_run;
ALTER TABLE ctl.analytic_lab_run WITH CHECK ADD CONSTRAINT CK_analytic_run CHECK(
 ctl.fn_local_synthetic_scope(source_instance)=1 AND ctl.fn_local_synthetic_scope(tenant_scope)=1
 AND contract_version='synthetic-analytic-v1' AND window_start<window_end_exclusive
 AND DATEDIFF(DAY,window_start,window_end_exclusive)<=3660 AND maximum_rows BETWEEN 1 AND 100000
 AND maximum_pages BETWEEN 1 AND 10000 AND zone_id IN('America/Sao_Paulo','UTC'));
ALTER TABLE ctl.analytic_raster_capture DROP CONSTRAINT CK_analytic_raster_capture;
ALTER TABLE ctl.analytic_raster_capture WITH CHECK ADD CONSTRAINT CK_analytic_raster_capture CHECK(
 start_date<end_exclusive AND mode IN('BOOTSTRAP','INCREMENTAL','BACKFILL','REPLAY')
 AND state IN('CAPTURING','COMPLETE','APPLIED','DEGRADED') AND ctl.fn_local_synthetic_scope(source_instance)=1
 AND ctl.fn_local_synthetic_scope(tenant_scope)=1 AND contract_version='synthetic-analytic-v1');
ALTER TABLE ctl.analytic_raster_terminal_window DROP CONSTRAINT CK_analytic_raster_terminal;
ALTER TABLE ctl.analytic_raster_terminal_window WITH CHECK ADD CONSTRAINT CK_analytic_raster_terminal CHECK(
 ordinal BETWEEN 1 AND 10000 AND start_date<end_exclusive AND expected_roots BETWEEN 0 AND 499 AND expected_stops BETWEEN 0 AND 100000
 AND receipt LIKE 'synthetic-%' AND DATALENGTH(receipt) BETWEEN 11 AND 64
 AND receipt COLLATE Latin1_General_100_BIN2 NOT LIKE '%[^a-zA-Z0-9-]%'
 AND ctl.fn_local_synthetic_scope(source_instance)=1 AND ctl.fn_local_synthetic_scope(tenant_scope)=1 AND contract_version='synthetic-analytic-v1');
ALTER TABLE ctl.analytic_lab_freight_contract DROP CONSTRAINT CK_analytic_freight_contract;
ALTER TABLE ctl.analytic_lab_freight_contract WITH CHECK ADD CONSTRAINT CK_analytic_freight_contract CHECK(
 contract_version IN('analytic-freight-performance-v1','integral-freight-pages-v1')
 AND LEN(contract_fingerprint)=64 AND contract_fingerprint NOT LIKE '%[^0-9a-f]%' AND evidence='synthetic-freight-contract-v1');
GO
CREATE TRIGGER ctl.trg_integral_source_group ON ctl.analytic_lab_source_group AFTER INSERT,UPDATE AS
BEGIN
 SET NOCOUNT ON;
 IF EXISTS(SELECT 1 FROM inserted g JOIN ctl.analytic_lab_run a ON a.run_id=g.run_id
 JOIN ctl.expansion_lab_run e ON e.run_id=g.expansion_run JOIN ctl.relational_lab_run r ON r.run_id=g.relational_run
 WHERE (a.source_instance<>'SYNTHETIC_ANALYTIC_LAB' OR a.tenant_scope<>'SYNTHETIC_ANALYTIC_TENANT')
 AND (a.source_instance COLLATE Latin1_General_100_BIN2<>e.source_instance OR a.tenant_scope COLLATE Latin1_General_100_BIN2<>e.tenant_scope
 OR a.source_instance COLLATE Latin1_General_100_BIN2<>r.source_instance OR a.tenant_scope COLLATE Latin1_General_100_BIN2<>r.tenant_scope
 OR a.window_start<>e.window_start OR a.window_end_exclusive<>e.window_end_exclusive
 OR a.window_start<>r.window_start OR a.window_end_exclusive<>DATEADD(DAY,1,r.window_end)))
 THROW 53841,N'INTEGRAL_SOURCE_GROUP_MISMATCH',1;
END;
GO
ALTER TRIGGER ctl.trg_analytic_execution_source ON ctl.analytic_lab_execution_source AFTER INSERT,UPDATE,DELETE
AS BEGIN SET NOCOUNT ON;
 IF EXISTS(SELECT 1 FROM deleted) THROW 53562,N'ANA_EXECUTION_SOURCE_IMMUTABLE',1;
 IF EXISTS(SELECT 1 FROM inserted i JOIN ctl.execution_attempt e ON e.execution_id=i.execution_id
 JOIN ctl.execution_partition p ON p.partition_id=e.partition_id JOIN ctl.analytic_lab_run r ON r.run_id=i.run_id
 WHERE p.environment_name<>N'LOCAL_SHADOW' OR ctl.fn_analytic_source_scope(i.run_id,p.source_instance,p.tenant_scope)<>1
 OR p.entity_name<>CASE i.entity WHEN 'USUARIO' THEN N'usuarios' ELSE N'cotacoes' END
 OR e.current_state<>N'PUBLISHED' OR CONVERT(DATE,p.partition_start_utc)<r.window_start
 OR CONVERT(DATE,p.partition_start_utc)>=r.window_end_exclusive)
 THROW 53563,N'ANA_EXECUTION_SOURCE_SCOPE_OR_STATE',1;
END;
GO
ALTER PROCEDURE ctl.usp_begin_analytic_quote_capture @run_id UNIQUEIDENTIFIER,@execution_id UNIQUEIDENTIFIER,@fingerprint CHAR(64) AS
BEGIN
 SET NOCOUNT ON;SET XACT_ABORT OFF;
 IF @@TRANCOUNT=0 OR NOT EXISTS(SELECT 1 FROM ctl.analytic_lab_run r JOIN ctl.execution_attempt e ON e.execution_id=@execution_id
 JOIN ctl.execution_partition p ON p.partition_id=e.partition_id WHERE r.run_id=@run_id AND e.current_state=N'EXTRACTING'
 AND p.environment_name=N'LOCAL_SHADOW' AND ctl.fn_analytic_source_scope(@run_id,p.source_instance,p.tenant_scope)=1 AND p.entity_name=N'cotacoes'
 AND e.contract_fingerprint=@fingerprint AND CONVERT(DATE,p.partition_start_utc)>=r.window_start
 AND CONVERT(DATE,p.partition_start_utc)<r.window_end_exclusive) THROW 53691,N'ANA_QUOTE_CAPTURE_SCOPE',1;
 IF EXISTS(SELECT 1 FROM ctl.analytic_quote_capture WHERE execution_id=@execution_id
 AND(run_id<>@run_id OR contract_fingerprint<>@fingerprint OR state<>'CAPTURING')) THROW 53692,N'ANA_QUOTE_CAPTURE_RETRY',1;
 IF NOT EXISTS(SELECT 1 FROM ctl.analytic_quote_capture WHERE execution_id=@execution_id)
 INSERT ctl.analytic_quote_capture(execution_id,run_id,contract_fingerprint,state,started_at)
 VALUES(@execution_id,@run_id,@fingerprint,'CAPTURING',SYSUTCDATETIME());
END;
GO
ALTER PROCEDURE ctl.usp_bind_analytic_quote_runtime_reference @run_id UNIQUEIDENTIFIER,@execution_id UNIQUEIDENTIFIER,@revision INT,@release BIGINT AS
BEGIN
 SET NOCOUNT ON;SET XACT_ABORT OFF;
 IF @@TRANCOUNT=0 OR NOT EXISTS(SELECT 1 FROM ctl.analytic_quote_capture c
 JOIN ctl.execution_attempt e ON e.execution_id=c.execution_id JOIN ctl.execution_partition p ON p.partition_id=e.partition_id
 CROSS APPLY ref.ufn_analytic_quote_tariff(c.run_id,@revision,CONVERT(DATE,p.partition_start_utc)) selection
 WHERE c.run_id=@run_id AND c.execution_id=@execution_id AND c.state='CAPTURING' AND e.current_state=N'EXTRACTING'
 AND p.environment_name=N'LOCAL_SHADOW' AND ctl.fn_analytic_source_scope(@run_id,p.source_instance,p.tenant_scope)=1 AND p.entity_name=N'cotacoes'
 AND selection.reference_release_id=@release) THROW 53721,N'ANA_QUOTE_RUNTIME_REFERENCE_SCOPE',1;
 IF EXISTS(SELECT 1 FROM ctl.analytic_quote_runtime_reference WHERE execution_id=@execution_id
 AND(run_id<>@run_id OR revision<>@revision OR reference_release_id<>@release))
 THROW 53722,N'ANA_QUOTE_RUNTIME_REFERENCE_DIVERGENT',1;
 INSERT ctl.analytic_quote_runtime_reference
 SELECT @execution_id,@run_id,@revision,@release,r.source_fingerprint FROM ref.reference_release r
 WHERE r.reference_release_id=@release AND NOT EXISTS(SELECT 1 FROM ctl.analytic_quote_runtime_reference WHERE execution_id=@execution_id);
END;
GO
ALTER FUNCTION ctl.fn_runtime_tariff_valid(@execution_id UNIQUEIDENTIFIER,@reference_release_id BIGINT)
RETURNS BIT AS
BEGIN
 IF @reference_release_id IS NULL OR @reference_release_id<1 RETURN 0;
 IF NOT EXISTS(SELECT 1 FROM ref.reference_release r
   JOIN ref.reference_release_ratification rat ON rat.reference_release_id=r.reference_release_id AND rat.activation_scope=N'SHADOW'
   WHERE r.reference_release_id=@reference_release_id AND r.family_code=N'QUOTE_TARIFF'
   AND(r.scope_code=N'LOCAL_SHADOW/LOCAL_V2/LOCAL_V2/cotacoes' OR EXISTS(
    SELECT 1 FROM ctl.analytic_quote_runtime_reference local_binding
    JOIN ctl.analytic_quote_capture capture ON capture.execution_id=local_binding.execution_id AND capture.run_id=local_binding.run_id
    JOIN ctl.execution_attempt attempt ON attempt.execution_id=capture.execution_id
    JOIN ctl.execution_partition partition ON partition.partition_id=attempt.partition_id
    CROSS APPLY ref.ufn_analytic_quote_tariff(local_binding.run_id,local_binding.revision,CONVERT(DATE,partition.partition_start_utc)) selection
    WHERE local_binding.execution_id=@execution_id AND local_binding.reference_release_id=r.reference_release_id
    AND selection.reference_release_id=r.reference_release_id AND local_binding.reference_fingerprint=r.source_fingerprint
    AND r.source_kind=N'DETERMINISTIC_LOCAL' AND r.scope_code=CONCAT(N'SYNTHETIC_QUOTE_LAB:',CONVERT(NVARCHAR(36),local_binding.run_id),N':R',local_binding.revision) COLLATE Latin1_General_100_BIN2
    AND partition.environment_name=N'LOCAL_SHADOW' AND ctl.fn_analytic_source_scope(local_binding.run_id,partition.source_instance,partition.tenant_scope)=1 AND partition.entity_name=N'cotacoes'))
   AND NOT EXISTS(SELECT 1 FROM ref.reference_release_revocation rev
     WHERE rev.reference_release_id=r.reference_release_id AND rev.activation_scope=N'SHADOW')) RETURN 0;
 IF EXISTS(SELECT 1 FROM ctl.runtime_cotacao_reference b JOIN ref.reference_release r ON r.reference_release_id=b.reference_release_id
   WHERE b.execution_id=@execution_id AND(b.reference_release_id<>@reference_release_id OR b.reference_fingerprint<>r.source_fingerprint)) RETURN 0;
 IF IS_SRVROLEMEMBER(N'sysadmin')=1 OR IS_MEMBER(N'db_owner')=1 RETURN 1;
 DECLARE @actual NVARCHAR(MAX)=CONCAT(N'{"execution":"',LOWER(CONVERT(NVARCHAR(36),@execution_id)),
   N'","entity":"cotacoes","workload":"cotacoes","mode":"BACKFILL","referenceReleaseId":"',
   CONVERT(NVARCHAR(20),@reference_release_id),N'"}');
 RETURN ctl.fn_runtime_consumed_scope(@actual,0,NULL);
END;
GO
ALTER PROCEDURE core.usp_preflight_analytic_quotes @run_id UNIQUEIDENTIFIER,@execution_id UNIQUEIDENTIFIER,@revision INT,@release BIGINT AS
BEGIN
 SET NOCOUNT ON;SET XACT_ABORT OFF;
 DECLARE @integral_source NVARCHAR(128),@integral_tenant NVARCHAR(128);
 SELECT @integral_source=p.source_instance,@integral_tenant=p.tenant_scope FROM ctl.execution_attempt e
 JOIN ctl.execution_partition p ON p.partition_id=e.partition_id WHERE e.execution_id=@execution_id
 AND p.environment_name=N'LOCAL_SHADOW' AND ctl.fn_analytic_source_scope(@run_id,p.source_instance,p.tenant_scope)=1;
 IF @integral_source IS NULL THROW 53842,N'INTEGRAL_QUOTE_SOURCE_SCOPE',1;
 IF @@TRANCOUNT=0 OR NOT EXISTS(SELECT 1 FROM ctl.analytic_quote_capture c JOIN ctl.execution_attempt e ON e.execution_id=c.execution_id
 WHERE c.run_id=@run_id AND c.execution_id=@execution_id AND c.state='CAPTURING' AND e.current_state=N'PROMOTED')
 THROW 53712,N'ANA_QUOTE_PREFLIGHT_STATE',1;
 EXEC ctl.usp_analytic_lab_lock @run_id,'DIMENSION';
 IF (SELECT COUNT_BIG(*) FROM stg.execution_record WHERE execution_id=@execution_id)<>
 (SELECT COUNT_BIG(*) FROM stg.analytic_quote_attributes WHERE execution_id=@execution_id)
 THROW 53713,N'ANA_QUOTE_ATTRIBUTE_CAPTURE_INCOMPLETE',1;
 IF EXISTS(SELECT 1 FROM stg.execution_candidate candidate
 JOIN stg.cotacao_record typed ON typed.stage_record_id=candidate.winner_stage_record_id
 JOIN stg.analytic_quote_attributes a ON a.stage_record_id=typed.stage_record_id
 LEFT JOIN core.analytic_quote_current pointer ON pointer.run_id=@run_id AND pointer.source_key=candidate.source_key
 LEFT JOIN core.analytic_quote_snapshot previous ON previous.snapshot_id=pointer.snapshot_id
 LEFT JOIN core.cotacao current_record WITH(UPDLOCK,HOLDLOCK) ON current_record.environment_name=N'LOCAL_SHADOW'
 AND current_record.source_instance=@integral_source AND current_record.tenant_scope=@integral_tenant
 AND current_record.entity_name=N'cotacoes' AND current_record.source_key=candidate.source_key WHERE candidate.execution_id=@execution_id AND current_record.cotacao_id IS NOT NULL
 AND(previous.snapshot_id IS NULL OR current_record.attribute_hash<>previous.attribute_hash OR current_record.freshness_at_utc<>previous.freshness_at_utc))
 THROW 53714,N'ANA_QUOTE_FOREIGN_CURRENT_CONTEXT',1;
 IF EXISTS(SELECT 1 FROM stg.execution_candidate candidate
 JOIN stg.cotacao_record typed ON typed.stage_record_id=candidate.winner_stage_record_id
 JOIN stg.analytic_quote_attributes a ON a.stage_record_id=typed.stage_record_id
 LEFT JOIN core.analytic_quote_current pointer ON pointer.run_id=@run_id AND pointer.source_key=candidate.source_key
 LEFT JOIN core.analytic_quote_snapshot previous ON previous.snapshot_id=pointer.snapshot_id
 LEFT JOIN core.cotacao current_record WITH(UPDLOCK,HOLDLOCK) ON current_record.environment_name=N'LOCAL_SHADOW'
 AND current_record.source_instance=@integral_source AND current_record.tenant_scope=@integral_tenant
 AND current_record.entity_name=N'cotacoes' AND current_record.source_key=candidate.source_key WHERE candidate.execution_id=@execution_id AND previous.snapshot_id IS NOT NULL
 AND typed.freshness_at_utc=previous.freshness_at_utc AND EXISTS(SELECT CASE WHEN a.[requested_at_p]='ABSENT' THEN previous.[requested_at] ELSE a.[requested_at] END,CASE WHEN a.[requested_at_p]='ABSENT' THEN previous.[requested_at_nano] ELSE a.[requested_at_nano] END,CASE WHEN a.[sequence_code_p]='ABSENT' THEN previous.[sequence_code] ELSE a.[sequence_code] END,CASE WHEN a.[qoe_qes_fon_name_p]='ABSENT' THEN previous.[qoe_qes_fon_name] ELSE a.[qoe_qes_fon_name] END,CASE WHEN a.[qoe_cor_document_p]='ABSENT' THEN previous.[qoe_cor_document] ELSE a.[qoe_cor_document] END,CASE WHEN a.[qoe_cor_name_p]='ABSENT' THEN previous.[qoe_cor_name] ELSE a.[qoe_cor_name] END,CASE WHEN a.[qoe_qes_ony_name_p]='ABSENT' THEN previous.[qoe_qes_ony_name] ELSE a.[qoe_qes_ony_name] END,CASE WHEN a.[qoe_qes_ony_sae_code_p]='ABSENT' THEN previous.[qoe_qes_ony_sae_code] ELSE a.[qoe_qes_ony_sae_code] END,CASE WHEN a.[qoe_qes_diy_name_p]='ABSENT' THEN previous.[qoe_qes_diy_name] ELSE a.[qoe_qes_diy_name] END,CASE WHEN a.[qoe_qes_diy_sae_code_p]='ABSENT' THEN previous.[qoe_qes_diy_sae_code] ELSE a.[qoe_qes_diy_sae_code] END,CASE WHEN a.[qoe_qes_cre_name_p]='ABSENT' THEN previous.[qoe_qes_cre_name] ELSE a.[qoe_qes_cre_name] END,CASE WHEN a.[qoe_qes_invoices_volumes_p]='ABSENT' THEN previous.[qoe_qes_invoices_volumes] ELSE a.[qoe_qes_invoices_volumes] END,CASE WHEN a.[qoe_qes_taxed_weight_p]='ABSENT' THEN previous.[qoe_qes_taxed_weight] ELSE a.[qoe_qes_taxed_weight] END,CASE WHEN a.[qoe_qes_invoices_value_p]='ABSENT' THEN previous.[qoe_qes_invoices_value] ELSE a.[qoe_qes_invoices_value] END,CASE WHEN a.[qoe_qes_total_p]='ABSENT' THEN previous.[qoe_qes_total] ELSE a.[qoe_qes_total] END,CASE WHEN a.[qoe_qes_fit_fhe_cte_issued_at_p]='ABSENT' THEN previous.[qoe_qes_fit_fhe_cte_issued_at] ELSE a.[qoe_qes_fit_fhe_cte_issued_at] END,CASE WHEN a.[qoe_qes_fit_fhe_cte_issued_at_p]='ABSENT' THEN previous.[qoe_qes_fit_fhe_cte_issued_at_nano] ELSE a.[qoe_qes_fit_fhe_cte_issued_at_nano] END,CASE WHEN a.[qoe_qes_fit_nse_issued_at_p]='ABSENT' THEN previous.[qoe_qes_fit_nse_issued_at] ELSE a.[qoe_qes_fit_nse_issued_at] END,CASE WHEN a.[qoe_qes_fit_nse_issued_at_p]='ABSENT' THEN previous.[qoe_qes_fit_nse_issued_at_nano] ELSE a.[qoe_qes_fit_nse_issued_at_nano] END,CASE WHEN a.[qoe_uer_name_p]='ABSENT' THEN previous.[qoe_uer_name] ELSE a.[qoe_uer_name] END,CASE WHEN a.[qoe_crn_psn_nickname_p]='ABSENT' THEN previous.[qoe_crn_psn_nickname] ELSE a.[qoe_crn_psn_nickname] END,CASE WHEN a.[qoe_qes_sdr_document_p]='ABSENT' THEN previous.[qoe_qes_sdr_document] ELSE a.[qoe_qes_sdr_document] END,CASE WHEN a.[qoe_qes_sdr_nickname_p]='ABSENT' THEN previous.[qoe_qes_sdr_nickname] ELSE a.[qoe_qes_sdr_nickname] END,CASE WHEN a.[qoe_qes_rpt_document_p]='ABSENT' THEN previous.[qoe_qes_rpt_document] ELSE a.[qoe_qes_rpt_document] END,CASE WHEN a.[qoe_qes_rpt_nickname_p]='ABSENT' THEN previous.[qoe_qes_rpt_nickname] ELSE a.[qoe_qes_rpt_nickname] END,CASE WHEN a.[qoe_qes_origin_postal_code_p]='ABSENT' THEN previous.[qoe_qes_origin_postal_code] ELSE a.[qoe_qes_origin_postal_code] END,CASE WHEN a.[qoe_qes_destination_postal_code_p]='ABSENT' THEN previous.[qoe_qes_destination_postal_code] ELSE a.[qoe_qes_destination_postal_code] END,CASE WHEN a.[qoe_qes_real_weight_p]='ABSENT' THEN previous.[qoe_qes_real_weight] ELSE a.[qoe_qes_real_weight] END,CASE WHEN a.[qoe_qes_disapprove_comments_p]='ABSENT' THEN previous.[qoe_qes_disapprove_comments] ELSE a.[qoe_qes_disapprove_comments] END,CASE WHEN a.[qoe_qes_freight_comments_p]='ABSENT' THEN previous.[qoe_qes_freight_comments] ELSE a.[qoe_qes_freight_comments] END,CASE WHEN a.[qoe_qes_fit_fdt_subtotal_p]='ABSENT' THEN previous.[qoe_qes_fit_fdt_subtotal] ELSE a.[qoe_qes_fit_fdt_subtotal] END,CASE WHEN a.[requester_name_p]='ABSENT' THEN previous.[requester_name] ELSE a.[requester_name] END,CASE WHEN a.[qoe_qes_itr_subtotal_p]='ABSENT' THEN previous.[qoe_qes_itr_subtotal] ELSE a.[qoe_qes_itr_subtotal] END,CASE WHEN a.[qoe_qes_tde_subtotal_p]='ABSENT' THEN previous.[qoe_qes_tde_subtotal] ELSE a.[qoe_qes_tde_subtotal] END,CASE WHEN a.[qoe_qes_collect_subtotal_p]='ABSENT' THEN previous.[qoe_qes_collect_subtotal] ELSE a.[qoe_qes_collect_subtotal] END,CASE WHEN a.[qoe_qes_delivery_subtotal_p]='ABSENT' THEN previous.[qoe_qes_delivery_subtotal] ELSE a.[qoe_qes_delivery_subtotal] END,CASE WHEN a.[qoe_qes_other_fees_p]='ABSENT' THEN previous.[qoe_qes_other_fees] ELSE a.[qoe_qes_other_fees] END,CASE WHEN a.[qoe_crn_psn_name_p]='ABSENT' THEN previous.[qoe_crn_psn_name] ELSE a.[qoe_crn_psn_name] END,CASE WHEN a.[qoe_cor_nickname_p]='ABSENT' THEN previous.[qoe_cor_nickname] ELSE a.[qoe_cor_nickname] END EXCEPT SELECT previous.[requested_at],previous.[requested_at_nano],previous.[sequence_code],previous.[qoe_qes_fon_name],previous.[qoe_cor_document],previous.[qoe_cor_name],previous.[qoe_qes_ony_name],previous.[qoe_qes_ony_sae_code],previous.[qoe_qes_diy_name],previous.[qoe_qes_diy_sae_code],previous.[qoe_qes_cre_name],previous.[qoe_qes_invoices_volumes],previous.[qoe_qes_taxed_weight],previous.[qoe_qes_invoices_value],previous.[qoe_qes_total],previous.[qoe_qes_fit_fhe_cte_issued_at],previous.[qoe_qes_fit_fhe_cte_issued_at_nano],previous.[qoe_qes_fit_nse_issued_at],previous.[qoe_qes_fit_nse_issued_at_nano],previous.[qoe_uer_name],previous.[qoe_crn_psn_nickname],previous.[qoe_qes_sdr_document],previous.[qoe_qes_sdr_nickname],previous.[qoe_qes_rpt_document],previous.[qoe_qes_rpt_nickname],previous.[qoe_qes_origin_postal_code],previous.[qoe_qes_destination_postal_code],previous.[qoe_qes_real_weight],previous.[qoe_qes_disapprove_comments],previous.[qoe_qes_freight_comments],previous.[qoe_qes_fit_fdt_subtotal],previous.[requester_name],previous.[qoe_qes_itr_subtotal],previous.[qoe_qes_tde_subtotal],previous.[qoe_qes_collect_subtotal],previous.[qoe_qes_delivery_subtotal],previous.[qoe_qes_other_fees],previous.[qoe_crn_psn_name],previous.[qoe_cor_nickname]))
 THROW 53715,N'ANA_QUOTE_EXACT_CLOCK_CONFLICT',1;
 IF EXISTS(SELECT 1 FROM stg.execution_candidate candidate
 JOIN stg.cotacao_record typed ON typed.stage_record_id=candidate.winner_stage_record_id
 JOIN stg.analytic_quote_attributes a ON a.stage_record_id=typed.stage_record_id
 LEFT JOIN core.analytic_quote_current pointer ON pointer.run_id=@run_id AND pointer.source_key=candidate.source_key
 LEFT JOIN core.analytic_quote_snapshot previous ON previous.snapshot_id=pointer.snapshot_id
 LEFT JOIN core.cotacao current_record WITH(UPDLOCK,HOLDLOCK) ON current_record.environment_name=N'LOCAL_SHADOW'
 AND current_record.source_instance=@integral_source AND current_record.tenant_scope=@integral_tenant
 AND current_record.entity_name=N'cotacoes' AND current_record.source_key=candidate.source_key
 OUTER APPLY(SELECT COUNT_BIG(*) matches FROM ref.ufn_analytic_quote_tariff(@run_id,@revision,typed.freshness_business_date) selection
 JOIN ref.tarifa_rota_uf rate ON rate.reference_release_id=selection.reference_release_id
 AND rate.origin_uf=CASE WHEN a.qoe_qes_ony_sae_code_p='ABSENT' THEN previous.qoe_qes_ony_sae_code ELSE a.qoe_qes_ony_sae_code END
 AND rate.destination_uf=CASE WHEN a.qoe_qes_diy_sae_code_p='ABSENT' THEN previous.qoe_qes_diy_sae_code ELSE a.qoe_qes_diy_sae_code END
 AND typed.freshness_business_date>=rate.valid_from AND typed.freshness_business_date<rate.valid_to_exclusive
 WHERE selection.reference_release_id=@release AND rate.coverage_state=N'PRICED') tariff
 WHERE candidate.execution_id=@execution_id AND tariff.matches<>1)
 THROW 53716,N'ANA_QUOTE_TARIFF_UNRESOLVED',1;
END;
GO
ALTER PROCEDURE core.usp_prepare_analytic_quotes @run_id UNIQUEIDENTIFIER,@execution_id UNIQUEIDENTIFIER AS
BEGIN
 SET NOCOUNT ON;SET XACT_ABORT OFF;
 DECLARE @integral_source NVARCHAR(128),@integral_tenant NVARCHAR(128);
 SELECT @integral_source=p.source_instance,@integral_tenant=p.tenant_scope FROM ctl.execution_attempt e
 JOIN ctl.execution_partition p ON p.partition_id=e.partition_id WHERE e.execution_id=@execution_id
 AND p.environment_name=N'LOCAL_SHADOW' AND ctl.fn_analytic_source_scope(@run_id,p.source_instance,p.tenant_scope)=1;
 IF @integral_source IS NULL THROW 53842,N'INTEGRAL_QUOTE_SOURCE_SCOPE',1;
 IF @@TRANCOUNT=0 OR NOT EXISTS(SELECT 1 FROM ctl.analytic_quote_capture c JOIN ctl.execution_attempt e ON e.execution_id=c.execution_id
 WHERE c.run_id=@run_id AND c.execution_id=@execution_id AND e.current_state=N'PUBLISHED')
 THROW 53717,N'ANA_QUOTE_PUBLICATION_REQUIRED',1;
 EXEC ctl.usp_analytic_lab_lock @run_id,'DIMENSION';
 IF EXISTS(SELECT 1 FROM ctl.analytic_quote_capture WHERE execution_id=@execution_id AND state='PUBLISHED')
 BEGIN SELECT physical_rows,roots,inserts,updates,noops,stale,blocked FROM ctl.analytic_quote_capture WHERE execution_id=@execution_id;RETURN;END;
 IF (SELECT COUNT_BIG(*) FROM stg.execution_candidate WHERE execution_id=@execution_id)<>
 (SELECT COUNT_BIG(*) FROM stg.execution_candidate candidate
 JOIN stg.cotacao_record typed ON typed.stage_record_id=candidate.winner_stage_record_id
 JOIN stg.analytic_quote_attributes a ON a.stage_record_id=typed.stage_record_id
 LEFT JOIN core.analytic_quote_current pointer ON pointer.run_id=@run_id AND pointer.source_key=candidate.source_key
 LEFT JOIN core.analytic_quote_snapshot previous ON previous.snapshot_id=pointer.snapshot_id
 LEFT JOIN core.cotacao current_record WITH(UPDLOCK,HOLDLOCK) ON current_record.environment_name=N'LOCAL_SHADOW'
 AND current_record.source_instance=@integral_source AND current_record.tenant_scope=@integral_tenant
 AND current_record.entity_name=N'cotacoes' AND current_record.source_key=candidate.source_key JOIN recon.execution_candidate_application app ON app.execution_id=candidate.execution_id AND app.source_key=candidate.source_key
 WHERE candidate.execution_id=@execution_id AND current_record.cotacao_id IS NOT NULL)
 THROW 53718,N'ANA_QUOTE_PUBLICATION_RECONCILIATION',1;
 INSERT core.analytic_quote_snapshot(run_id,source_key,source_execution,stage_record_id,previous_snapshot,cotacao_id,attribute_hash,
 freshness_at_utc,business_date,user_normalized,tariff_release,tariff_minimum,tariff_currency,tariff_unit,tariff_rounding,extracted_at,
 [requested_at_p],[requested_at_w],[requested_at_raw],[requested_at],[requested_at_nano],[sequence_code_p],[sequence_code_w],[sequence_code_raw],[sequence_code],[qoe_qes_fon_name_p],[qoe_qes_fon_name_w],[qoe_qes_fon_name_raw],[qoe_qes_fon_name],[qoe_cor_document_p],[qoe_cor_document_w],[qoe_cor_document_raw],[qoe_cor_document],[qoe_cor_name_p],[qoe_cor_name_w],[qoe_cor_name_raw],[qoe_cor_name],[qoe_qes_ony_name_p],[qoe_qes_ony_name_w],[qoe_qes_ony_name_raw],[qoe_qes_ony_name],[qoe_qes_ony_sae_code_p],[qoe_qes_ony_sae_code_w],[qoe_qes_ony_sae_code_raw],[qoe_qes_ony_sae_code],[qoe_qes_diy_name_p],[qoe_qes_diy_name_w],[qoe_qes_diy_name_raw],[qoe_qes_diy_name],[qoe_qes_diy_sae_code_p],[qoe_qes_diy_sae_code_w],[qoe_qes_diy_sae_code_raw],[qoe_qes_diy_sae_code],[qoe_qes_cre_name_p],[qoe_qes_cre_name_w],[qoe_qes_cre_name_raw],[qoe_qes_cre_name],[qoe_qes_invoices_volumes_p],[qoe_qes_invoices_volumes_w],[qoe_qes_invoices_volumes_raw],[qoe_qes_invoices_volumes],[qoe_qes_taxed_weight_p],[qoe_qes_taxed_weight_w],[qoe_qes_taxed_weight_raw],[qoe_qes_taxed_weight],[qoe_qes_invoices_value_p],[qoe_qes_invoices_value_w],[qoe_qes_invoices_value_raw],[qoe_qes_invoices_value],[qoe_qes_total_p],[qoe_qes_total_w],[qoe_qes_total_raw],[qoe_qes_total],[qoe_qes_fit_fhe_cte_issued_at_p],[qoe_qes_fit_fhe_cte_issued_at_w],[qoe_qes_fit_fhe_cte_issued_at_raw],[qoe_qes_fit_fhe_cte_issued_at],[qoe_qes_fit_fhe_cte_issued_at_nano],[qoe_qes_fit_nse_issued_at_p],[qoe_qes_fit_nse_issued_at_w],[qoe_qes_fit_nse_issued_at_raw],[qoe_qes_fit_nse_issued_at],[qoe_qes_fit_nse_issued_at_nano],[qoe_uer_name_p],[qoe_uer_name_w],[qoe_uer_name_raw],[qoe_uer_name],[qoe_crn_psn_nickname_p],[qoe_crn_psn_nickname_w],[qoe_crn_psn_nickname_raw],[qoe_crn_psn_nickname],[qoe_qes_sdr_document_p],[qoe_qes_sdr_document_w],[qoe_qes_sdr_document_raw],[qoe_qes_sdr_document],[qoe_qes_sdr_nickname_p],[qoe_qes_sdr_nickname_w],[qoe_qes_sdr_nickname_raw],[qoe_qes_sdr_nickname],[qoe_qes_rpt_document_p],[qoe_qes_rpt_document_w],[qoe_qes_rpt_document_raw],[qoe_qes_rpt_document],[qoe_qes_rpt_nickname_p],[qoe_qes_rpt_nickname_w],[qoe_qes_rpt_nickname_raw],[qoe_qes_rpt_nickname],[qoe_qes_origin_postal_code_p],[qoe_qes_origin_postal_code_w],[qoe_qes_origin_postal_code_raw],[qoe_qes_origin_postal_code],[qoe_qes_destination_postal_code_p],[qoe_qes_destination_postal_code_w],[qoe_qes_destination_postal_code_raw],[qoe_qes_destination_postal_code],[qoe_qes_real_weight_p],[qoe_qes_real_weight_w],[qoe_qes_real_weight_raw],[qoe_qes_real_weight],[qoe_qes_disapprove_comments_p],[qoe_qes_disapprove_comments_w],[qoe_qes_disapprove_comments_raw],[qoe_qes_disapprove_comments],[qoe_qes_freight_comments_p],[qoe_qes_freight_comments_w],[qoe_qes_freight_comments_raw],[qoe_qes_freight_comments],[qoe_qes_fit_fdt_subtotal_p],[qoe_qes_fit_fdt_subtotal_w],[qoe_qes_fit_fdt_subtotal_raw],[qoe_qes_fit_fdt_subtotal],[requester_name_p],[requester_name_w],[requester_name_raw],[requester_name],[qoe_qes_itr_subtotal_p],[qoe_qes_itr_subtotal_w],[qoe_qes_itr_subtotal_raw],[qoe_qes_itr_subtotal],[qoe_qes_tde_subtotal_p],[qoe_qes_tde_subtotal_w],[qoe_qes_tde_subtotal_raw],[qoe_qes_tde_subtotal],[qoe_qes_collect_subtotal_p],[qoe_qes_collect_subtotal_w],[qoe_qes_collect_subtotal_raw],[qoe_qes_collect_subtotal],[qoe_qes_delivery_subtotal_p],[qoe_qes_delivery_subtotal_w],[qoe_qes_delivery_subtotal_raw],[qoe_qes_delivery_subtotal],[qoe_qes_other_fees_p],[qoe_qes_other_fees_w],[qoe_qes_other_fees_raw],[qoe_qes_other_fees],[qoe_crn_psn_name_p],[qoe_crn_psn_name_w],[qoe_crn_psn_name_raw],[qoe_crn_psn_name],[qoe_cor_nickname_p],[qoe_cor_nickname_w],[qoe_cor_nickname_raw],[qoe_cor_nickname])
 SELECT @run_id,candidate.source_key,@execution_id,a.stage_record_id,previous.snapshot_id,current_record.cotacao_id,current_record.attribute_hash,
 current_record.freshness_at_utc,current_record.freshness_business_date,current_record.user_name_normalized,current_record.tariff_reference_release_id,
 current_record.tariff_minimum_amount,current_record.tariff_currency_code,current_record.tariff_unit_code,current_record.tariff_rounding_mode,
 observed.staged_at_utc,CASE WHEN a.[requested_at_p]='ABSENT' AND previous.snapshot_id IS NOT NULL THEN previous.[requested_at_p] ELSE a.[requested_at_p] END,
CASE WHEN a.[requested_at_p]='ABSENT' AND previous.snapshot_id IS NOT NULL THEN previous.[requested_at_w] ELSE a.[requested_at_w] END,
CASE WHEN a.[requested_at_p]='ABSENT' AND previous.snapshot_id IS NOT NULL THEN previous.[requested_at_raw] ELSE a.[requested_at_raw] END,
CASE WHEN a.[requested_at_p]='ABSENT' AND previous.snapshot_id IS NOT NULL THEN previous.[requested_at] ELSE a.[requested_at] END,
CASE WHEN a.[requested_at_p]='ABSENT' AND previous.snapshot_id IS NOT NULL THEN previous.[requested_at_nano] ELSE a.[requested_at_nano] END,
CASE WHEN a.[sequence_code_p]='ABSENT' AND previous.snapshot_id IS NOT NULL THEN previous.[sequence_code_p] ELSE a.[sequence_code_p] END,
CASE WHEN a.[sequence_code_p]='ABSENT' AND previous.snapshot_id IS NOT NULL THEN previous.[sequence_code_w] ELSE a.[sequence_code_w] END,
CASE WHEN a.[sequence_code_p]='ABSENT' AND previous.snapshot_id IS NOT NULL THEN previous.[sequence_code_raw] ELSE a.[sequence_code_raw] END,
CASE WHEN a.[sequence_code_p]='ABSENT' AND previous.snapshot_id IS NOT NULL THEN previous.[sequence_code] ELSE a.[sequence_code] END,
CASE WHEN a.[qoe_qes_fon_name_p]='ABSENT' AND previous.snapshot_id IS NOT NULL THEN previous.[qoe_qes_fon_name_p] ELSE a.[qoe_qes_fon_name_p] END,
CASE WHEN a.[qoe_qes_fon_name_p]='ABSENT' AND previous.snapshot_id IS NOT NULL THEN previous.[qoe_qes_fon_name_w] ELSE a.[qoe_qes_fon_name_w] END,
CASE WHEN a.[qoe_qes_fon_name_p]='ABSENT' AND previous.snapshot_id IS NOT NULL THEN previous.[qoe_qes_fon_name_raw] ELSE a.[qoe_qes_fon_name_raw] END,
CASE WHEN a.[qoe_qes_fon_name_p]='ABSENT' AND previous.snapshot_id IS NOT NULL THEN previous.[qoe_qes_fon_name] ELSE a.[qoe_qes_fon_name] END,
CASE WHEN a.[qoe_cor_document_p]='ABSENT' AND previous.snapshot_id IS NOT NULL THEN previous.[qoe_cor_document_p] ELSE a.[qoe_cor_document_p] END,
CASE WHEN a.[qoe_cor_document_p]='ABSENT' AND previous.snapshot_id IS NOT NULL THEN previous.[qoe_cor_document_w] ELSE a.[qoe_cor_document_w] END,
CASE WHEN a.[qoe_cor_document_p]='ABSENT' AND previous.snapshot_id IS NOT NULL THEN previous.[qoe_cor_document_raw] ELSE a.[qoe_cor_document_raw] END,
CASE WHEN a.[qoe_cor_document_p]='ABSENT' AND previous.snapshot_id IS NOT NULL THEN previous.[qoe_cor_document] ELSE a.[qoe_cor_document] END,
CASE WHEN a.[qoe_cor_name_p]='ABSENT' AND previous.snapshot_id IS NOT NULL THEN previous.[qoe_cor_name_p] ELSE a.[qoe_cor_name_p] END,
CASE WHEN a.[qoe_cor_name_p]='ABSENT' AND previous.snapshot_id IS NOT NULL THEN previous.[qoe_cor_name_w] ELSE a.[qoe_cor_name_w] END,
CASE WHEN a.[qoe_cor_name_p]='ABSENT' AND previous.snapshot_id IS NOT NULL THEN previous.[qoe_cor_name_raw] ELSE a.[qoe_cor_name_raw] END,
CASE WHEN a.[qoe_cor_name_p]='ABSENT' AND previous.snapshot_id IS NOT NULL THEN previous.[qoe_cor_name] ELSE a.[qoe_cor_name] END,
CASE WHEN a.[qoe_qes_ony_name_p]='ABSENT' AND previous.snapshot_id IS NOT NULL THEN previous.[qoe_qes_ony_name_p] ELSE a.[qoe_qes_ony_name_p] END,
CASE WHEN a.[qoe_qes_ony_name_p]='ABSENT' AND previous.snapshot_id IS NOT NULL THEN previous.[qoe_qes_ony_name_w] ELSE a.[qoe_qes_ony_name_w] END,
CASE WHEN a.[qoe_qes_ony_name_p]='ABSENT' AND previous.snapshot_id IS NOT NULL THEN previous.[qoe_qes_ony_name_raw] ELSE a.[qoe_qes_ony_name_raw] END,
CASE WHEN a.[qoe_qes_ony_name_p]='ABSENT' AND previous.snapshot_id IS NOT NULL THEN previous.[qoe_qes_ony_name] ELSE a.[qoe_qes_ony_name] END,
CASE WHEN a.[qoe_qes_ony_sae_code_p]='ABSENT' AND previous.snapshot_id IS NOT NULL THEN previous.[qoe_qes_ony_sae_code_p] ELSE a.[qoe_qes_ony_sae_code_p] END,
CASE WHEN a.[qoe_qes_ony_sae_code_p]='ABSENT' AND previous.snapshot_id IS NOT NULL THEN previous.[qoe_qes_ony_sae_code_w] ELSE a.[qoe_qes_ony_sae_code_w] END,
CASE WHEN a.[qoe_qes_ony_sae_code_p]='ABSENT' AND previous.snapshot_id IS NOT NULL THEN previous.[qoe_qes_ony_sae_code_raw] ELSE a.[qoe_qes_ony_sae_code_raw] END,
CASE WHEN a.[qoe_qes_ony_sae_code_p]='ABSENT' AND previous.snapshot_id IS NOT NULL THEN previous.[qoe_qes_ony_sae_code] ELSE a.[qoe_qes_ony_sae_code] END,
CASE WHEN a.[qoe_qes_diy_name_p]='ABSENT' AND previous.snapshot_id IS NOT NULL THEN previous.[qoe_qes_diy_name_p] ELSE a.[qoe_qes_diy_name_p] END,
CASE WHEN a.[qoe_qes_diy_name_p]='ABSENT' AND previous.snapshot_id IS NOT NULL THEN previous.[qoe_qes_diy_name_w] ELSE a.[qoe_qes_diy_name_w] END,
CASE WHEN a.[qoe_qes_diy_name_p]='ABSENT' AND previous.snapshot_id IS NOT NULL THEN previous.[qoe_qes_diy_name_raw] ELSE a.[qoe_qes_diy_name_raw] END,
CASE WHEN a.[qoe_qes_diy_name_p]='ABSENT' AND previous.snapshot_id IS NOT NULL THEN previous.[qoe_qes_diy_name] ELSE a.[qoe_qes_diy_name] END,
CASE WHEN a.[qoe_qes_diy_sae_code_p]='ABSENT' AND previous.snapshot_id IS NOT NULL THEN previous.[qoe_qes_diy_sae_code_p] ELSE a.[qoe_qes_diy_sae_code_p] END,
CASE WHEN a.[qoe_qes_diy_sae_code_p]='ABSENT' AND previous.snapshot_id IS NOT NULL THEN previous.[qoe_qes_diy_sae_code_w] ELSE a.[qoe_qes_diy_sae_code_w] END,
CASE WHEN a.[qoe_qes_diy_sae_code_p]='ABSENT' AND previous.snapshot_id IS NOT NULL THEN previous.[qoe_qes_diy_sae_code_raw] ELSE a.[qoe_qes_diy_sae_code_raw] END,
CASE WHEN a.[qoe_qes_diy_sae_code_p]='ABSENT' AND previous.snapshot_id IS NOT NULL THEN previous.[qoe_qes_diy_sae_code] ELSE a.[qoe_qes_diy_sae_code] END,
CASE WHEN a.[qoe_qes_cre_name_p]='ABSENT' AND previous.snapshot_id IS NOT NULL THEN previous.[qoe_qes_cre_name_p] ELSE a.[qoe_qes_cre_name_p] END,
CASE WHEN a.[qoe_qes_cre_name_p]='ABSENT' AND previous.snapshot_id IS NOT NULL THEN previous.[qoe_qes_cre_name_w] ELSE a.[qoe_qes_cre_name_w] END,
CASE WHEN a.[qoe_qes_cre_name_p]='ABSENT' AND previous.snapshot_id IS NOT NULL THEN previous.[qoe_qes_cre_name_raw] ELSE a.[qoe_qes_cre_name_raw] END,
CASE WHEN a.[qoe_qes_cre_name_p]='ABSENT' AND previous.snapshot_id IS NOT NULL THEN previous.[qoe_qes_cre_name] ELSE a.[qoe_qes_cre_name] END,
CASE WHEN a.[qoe_qes_invoices_volumes_p]='ABSENT' AND previous.snapshot_id IS NOT NULL THEN previous.[qoe_qes_invoices_volumes_p] ELSE a.[qoe_qes_invoices_volumes_p] END,
CASE WHEN a.[qoe_qes_invoices_volumes_p]='ABSENT' AND previous.snapshot_id IS NOT NULL THEN previous.[qoe_qes_invoices_volumes_w] ELSE a.[qoe_qes_invoices_volumes_w] END,
CASE WHEN a.[qoe_qes_invoices_volumes_p]='ABSENT' AND previous.snapshot_id IS NOT NULL THEN previous.[qoe_qes_invoices_volumes_raw] ELSE a.[qoe_qes_invoices_volumes_raw] END,
CASE WHEN a.[qoe_qes_invoices_volumes_p]='ABSENT' AND previous.snapshot_id IS NOT NULL THEN previous.[qoe_qes_invoices_volumes] ELSE a.[qoe_qes_invoices_volumes] END,
CASE WHEN a.[qoe_qes_taxed_weight_p]='ABSENT' AND previous.snapshot_id IS NOT NULL THEN previous.[qoe_qes_taxed_weight_p] ELSE a.[qoe_qes_taxed_weight_p] END,
CASE WHEN a.[qoe_qes_taxed_weight_p]='ABSENT' AND previous.snapshot_id IS NOT NULL THEN previous.[qoe_qes_taxed_weight_w] ELSE a.[qoe_qes_taxed_weight_w] END,
CASE WHEN a.[qoe_qes_taxed_weight_p]='ABSENT' AND previous.snapshot_id IS NOT NULL THEN previous.[qoe_qes_taxed_weight_raw] ELSE a.[qoe_qes_taxed_weight_raw] END,
CASE WHEN a.[qoe_qes_taxed_weight_p]='ABSENT' AND previous.snapshot_id IS NOT NULL THEN previous.[qoe_qes_taxed_weight] ELSE a.[qoe_qes_taxed_weight] END,
CASE WHEN a.[qoe_qes_invoices_value_p]='ABSENT' AND previous.snapshot_id IS NOT NULL THEN previous.[qoe_qes_invoices_value_p] ELSE a.[qoe_qes_invoices_value_p] END,
CASE WHEN a.[qoe_qes_invoices_value_p]='ABSENT' AND previous.snapshot_id IS NOT NULL THEN previous.[qoe_qes_invoices_value_w] ELSE a.[qoe_qes_invoices_value_w] END,
CASE WHEN a.[qoe_qes_invoices_value_p]='ABSENT' AND previous.snapshot_id IS NOT NULL THEN previous.[qoe_qes_invoices_value_raw] ELSE a.[qoe_qes_invoices_value_raw] END,
CASE WHEN a.[qoe_qes_invoices_value_p]='ABSENT' AND previous.snapshot_id IS NOT NULL THEN previous.[qoe_qes_invoices_value] ELSE a.[qoe_qes_invoices_value] END,
CASE WHEN a.[qoe_qes_total_p]='ABSENT' AND previous.snapshot_id IS NOT NULL THEN previous.[qoe_qes_total_p] ELSE a.[qoe_qes_total_p] END,
CASE WHEN a.[qoe_qes_total_p]='ABSENT' AND previous.snapshot_id IS NOT NULL THEN previous.[qoe_qes_total_w] ELSE a.[qoe_qes_total_w] END,
CASE WHEN a.[qoe_qes_total_p]='ABSENT' AND previous.snapshot_id IS NOT NULL THEN previous.[qoe_qes_total_raw] ELSE a.[qoe_qes_total_raw] END,
CASE WHEN a.[qoe_qes_total_p]='ABSENT' AND previous.snapshot_id IS NOT NULL THEN previous.[qoe_qes_total] ELSE a.[qoe_qes_total] END,
CASE WHEN a.[qoe_qes_fit_fhe_cte_issued_at_p]='ABSENT' AND previous.snapshot_id IS NOT NULL THEN previous.[qoe_qes_fit_fhe_cte_issued_at_p] ELSE a.[qoe_qes_fit_fhe_cte_issued_at_p] END,
CASE WHEN a.[qoe_qes_fit_fhe_cte_issued_at_p]='ABSENT' AND previous.snapshot_id IS NOT NULL THEN previous.[qoe_qes_fit_fhe_cte_issued_at_w] ELSE a.[qoe_qes_fit_fhe_cte_issued_at_w] END,
CASE WHEN a.[qoe_qes_fit_fhe_cte_issued_at_p]='ABSENT' AND previous.snapshot_id IS NOT NULL THEN previous.[qoe_qes_fit_fhe_cte_issued_at_raw] ELSE a.[qoe_qes_fit_fhe_cte_issued_at_raw] END,
CASE WHEN a.[qoe_qes_fit_fhe_cte_issued_at_p]='ABSENT' AND previous.snapshot_id IS NOT NULL THEN previous.[qoe_qes_fit_fhe_cte_issued_at] ELSE a.[qoe_qes_fit_fhe_cte_issued_at] END,
CASE WHEN a.[qoe_qes_fit_fhe_cte_issued_at_p]='ABSENT' AND previous.snapshot_id IS NOT NULL THEN previous.[qoe_qes_fit_fhe_cte_issued_at_nano] ELSE a.[qoe_qes_fit_fhe_cte_issued_at_nano] END,
CASE WHEN a.[qoe_qes_fit_nse_issued_at_p]='ABSENT' AND previous.snapshot_id IS NOT NULL THEN previous.[qoe_qes_fit_nse_issued_at_p] ELSE a.[qoe_qes_fit_nse_issued_at_p] END,
CASE WHEN a.[qoe_qes_fit_nse_issued_at_p]='ABSENT' AND previous.snapshot_id IS NOT NULL THEN previous.[qoe_qes_fit_nse_issued_at_w] ELSE a.[qoe_qes_fit_nse_issued_at_w] END,
CASE WHEN a.[qoe_qes_fit_nse_issued_at_p]='ABSENT' AND previous.snapshot_id IS NOT NULL THEN previous.[qoe_qes_fit_nse_issued_at_raw] ELSE a.[qoe_qes_fit_nse_issued_at_raw] END,
CASE WHEN a.[qoe_qes_fit_nse_issued_at_p]='ABSENT' AND previous.snapshot_id IS NOT NULL THEN previous.[qoe_qes_fit_nse_issued_at] ELSE a.[qoe_qes_fit_nse_issued_at] END,
CASE WHEN a.[qoe_qes_fit_nse_issued_at_p]='ABSENT' AND previous.snapshot_id IS NOT NULL THEN previous.[qoe_qes_fit_nse_issued_at_nano] ELSE a.[qoe_qes_fit_nse_issued_at_nano] END,
CASE WHEN a.[qoe_uer_name_p]='ABSENT' AND previous.snapshot_id IS NOT NULL THEN previous.[qoe_uer_name_p] ELSE a.[qoe_uer_name_p] END,
CASE WHEN a.[qoe_uer_name_p]='ABSENT' AND previous.snapshot_id IS NOT NULL THEN previous.[qoe_uer_name_w] ELSE a.[qoe_uer_name_w] END,
CASE WHEN a.[qoe_uer_name_p]='ABSENT' AND previous.snapshot_id IS NOT NULL THEN previous.[qoe_uer_name_raw] ELSE a.[qoe_uer_name_raw] END,
CASE WHEN a.[qoe_uer_name_p]='ABSENT' AND previous.snapshot_id IS NOT NULL THEN previous.[qoe_uer_name] ELSE a.[qoe_uer_name] END,
CASE WHEN a.[qoe_crn_psn_nickname_p]='ABSENT' AND previous.snapshot_id IS NOT NULL THEN previous.[qoe_crn_psn_nickname_p] ELSE a.[qoe_crn_psn_nickname_p] END,
CASE WHEN a.[qoe_crn_psn_nickname_p]='ABSENT' AND previous.snapshot_id IS NOT NULL THEN previous.[qoe_crn_psn_nickname_w] ELSE a.[qoe_crn_psn_nickname_w] END,
CASE WHEN a.[qoe_crn_psn_nickname_p]='ABSENT' AND previous.snapshot_id IS NOT NULL THEN previous.[qoe_crn_psn_nickname_raw] ELSE a.[qoe_crn_psn_nickname_raw] END,
CASE WHEN a.[qoe_crn_psn_nickname_p]='ABSENT' AND previous.snapshot_id IS NOT NULL THEN previous.[qoe_crn_psn_nickname] ELSE a.[qoe_crn_psn_nickname] END,
CASE WHEN a.[qoe_qes_sdr_document_p]='ABSENT' AND previous.snapshot_id IS NOT NULL THEN previous.[qoe_qes_sdr_document_p] ELSE a.[qoe_qes_sdr_document_p] END,
CASE WHEN a.[qoe_qes_sdr_document_p]='ABSENT' AND previous.snapshot_id IS NOT NULL THEN previous.[qoe_qes_sdr_document_w] ELSE a.[qoe_qes_sdr_document_w] END,
CASE WHEN a.[qoe_qes_sdr_document_p]='ABSENT' AND previous.snapshot_id IS NOT NULL THEN previous.[qoe_qes_sdr_document_raw] ELSE a.[qoe_qes_sdr_document_raw] END,
CASE WHEN a.[qoe_qes_sdr_document_p]='ABSENT' AND previous.snapshot_id IS NOT NULL THEN previous.[qoe_qes_sdr_document] ELSE a.[qoe_qes_sdr_document] END,
CASE WHEN a.[qoe_qes_sdr_nickname_p]='ABSENT' AND previous.snapshot_id IS NOT NULL THEN previous.[qoe_qes_sdr_nickname_p] ELSE a.[qoe_qes_sdr_nickname_p] END,
CASE WHEN a.[qoe_qes_sdr_nickname_p]='ABSENT' AND previous.snapshot_id IS NOT NULL THEN previous.[qoe_qes_sdr_nickname_w] ELSE a.[qoe_qes_sdr_nickname_w] END,
CASE WHEN a.[qoe_qes_sdr_nickname_p]='ABSENT' AND previous.snapshot_id IS NOT NULL THEN previous.[qoe_qes_sdr_nickname_raw] ELSE a.[qoe_qes_sdr_nickname_raw] END,
CASE WHEN a.[qoe_qes_sdr_nickname_p]='ABSENT' AND previous.snapshot_id IS NOT NULL THEN previous.[qoe_qes_sdr_nickname] ELSE a.[qoe_qes_sdr_nickname] END,
CASE WHEN a.[qoe_qes_rpt_document_p]='ABSENT' AND previous.snapshot_id IS NOT NULL THEN previous.[qoe_qes_rpt_document_p] ELSE a.[qoe_qes_rpt_document_p] END,
CASE WHEN a.[qoe_qes_rpt_document_p]='ABSENT' AND previous.snapshot_id IS NOT NULL THEN previous.[qoe_qes_rpt_document_w] ELSE a.[qoe_qes_rpt_document_w] END,
CASE WHEN a.[qoe_qes_rpt_document_p]='ABSENT' AND previous.snapshot_id IS NOT NULL THEN previous.[qoe_qes_rpt_document_raw] ELSE a.[qoe_qes_rpt_document_raw] END,
CASE WHEN a.[qoe_qes_rpt_document_p]='ABSENT' AND previous.snapshot_id IS NOT NULL THEN previous.[qoe_qes_rpt_document] ELSE a.[qoe_qes_rpt_document] END,
CASE WHEN a.[qoe_qes_rpt_nickname_p]='ABSENT' AND previous.snapshot_id IS NOT NULL THEN previous.[qoe_qes_rpt_nickname_p] ELSE a.[qoe_qes_rpt_nickname_p] END,
CASE WHEN a.[qoe_qes_rpt_nickname_p]='ABSENT' AND previous.snapshot_id IS NOT NULL THEN previous.[qoe_qes_rpt_nickname_w] ELSE a.[qoe_qes_rpt_nickname_w] END,
CASE WHEN a.[qoe_qes_rpt_nickname_p]='ABSENT' AND previous.snapshot_id IS NOT NULL THEN previous.[qoe_qes_rpt_nickname_raw] ELSE a.[qoe_qes_rpt_nickname_raw] END,
CASE WHEN a.[qoe_qes_rpt_nickname_p]='ABSENT' AND previous.snapshot_id IS NOT NULL THEN previous.[qoe_qes_rpt_nickname] ELSE a.[qoe_qes_rpt_nickname] END,
CASE WHEN a.[qoe_qes_origin_postal_code_p]='ABSENT' AND previous.snapshot_id IS NOT NULL THEN previous.[qoe_qes_origin_postal_code_p] ELSE a.[qoe_qes_origin_postal_code_p] END,
CASE WHEN a.[qoe_qes_origin_postal_code_p]='ABSENT' AND previous.snapshot_id IS NOT NULL THEN previous.[qoe_qes_origin_postal_code_w] ELSE a.[qoe_qes_origin_postal_code_w] END,
CASE WHEN a.[qoe_qes_origin_postal_code_p]='ABSENT' AND previous.snapshot_id IS NOT NULL THEN previous.[qoe_qes_origin_postal_code_raw] ELSE a.[qoe_qes_origin_postal_code_raw] END,
CASE WHEN a.[qoe_qes_origin_postal_code_p]='ABSENT' AND previous.snapshot_id IS NOT NULL THEN previous.[qoe_qes_origin_postal_code] ELSE a.[qoe_qes_origin_postal_code] END,
CASE WHEN a.[qoe_qes_destination_postal_code_p]='ABSENT' AND previous.snapshot_id IS NOT NULL THEN previous.[qoe_qes_destination_postal_code_p] ELSE a.[qoe_qes_destination_postal_code_p] END,
CASE WHEN a.[qoe_qes_destination_postal_code_p]='ABSENT' AND previous.snapshot_id IS NOT NULL THEN previous.[qoe_qes_destination_postal_code_w] ELSE a.[qoe_qes_destination_postal_code_w] END,
CASE WHEN a.[qoe_qes_destination_postal_code_p]='ABSENT' AND previous.snapshot_id IS NOT NULL THEN previous.[qoe_qes_destination_postal_code_raw] ELSE a.[qoe_qes_destination_postal_code_raw] END,
CASE WHEN a.[qoe_qes_destination_postal_code_p]='ABSENT' AND previous.snapshot_id IS NOT NULL THEN previous.[qoe_qes_destination_postal_code] ELSE a.[qoe_qes_destination_postal_code] END,
CASE WHEN a.[qoe_qes_real_weight_p]='ABSENT' AND previous.snapshot_id IS NOT NULL THEN previous.[qoe_qes_real_weight_p] ELSE a.[qoe_qes_real_weight_p] END,
CASE WHEN a.[qoe_qes_real_weight_p]='ABSENT' AND previous.snapshot_id IS NOT NULL THEN previous.[qoe_qes_real_weight_w] ELSE a.[qoe_qes_real_weight_w] END,
CASE WHEN a.[qoe_qes_real_weight_p]='ABSENT' AND previous.snapshot_id IS NOT NULL THEN previous.[qoe_qes_real_weight_raw] ELSE a.[qoe_qes_real_weight_raw] END,
CASE WHEN a.[qoe_qes_real_weight_p]='ABSENT' AND previous.snapshot_id IS NOT NULL THEN previous.[qoe_qes_real_weight] ELSE a.[qoe_qes_real_weight] END,
CASE WHEN a.[qoe_qes_disapprove_comments_p]='ABSENT' AND previous.snapshot_id IS NOT NULL THEN previous.[qoe_qes_disapprove_comments_p] ELSE a.[qoe_qes_disapprove_comments_p] END,
CASE WHEN a.[qoe_qes_disapprove_comments_p]='ABSENT' AND previous.snapshot_id IS NOT NULL THEN previous.[qoe_qes_disapprove_comments_w] ELSE a.[qoe_qes_disapprove_comments_w] END,
CASE WHEN a.[qoe_qes_disapprove_comments_p]='ABSENT' AND previous.snapshot_id IS NOT NULL THEN previous.[qoe_qes_disapprove_comments_raw] ELSE a.[qoe_qes_disapprove_comments_raw] END,
CASE WHEN a.[qoe_qes_disapprove_comments_p]='ABSENT' AND previous.snapshot_id IS NOT NULL THEN previous.[qoe_qes_disapprove_comments] ELSE a.[qoe_qes_disapprove_comments] END,
CASE WHEN a.[qoe_qes_freight_comments_p]='ABSENT' AND previous.snapshot_id IS NOT NULL THEN previous.[qoe_qes_freight_comments_p] ELSE a.[qoe_qes_freight_comments_p] END,
CASE WHEN a.[qoe_qes_freight_comments_p]='ABSENT' AND previous.snapshot_id IS NOT NULL THEN previous.[qoe_qes_freight_comments_w] ELSE a.[qoe_qes_freight_comments_w] END,
CASE WHEN a.[qoe_qes_freight_comments_p]='ABSENT' AND previous.snapshot_id IS NOT NULL THEN previous.[qoe_qes_freight_comments_raw] ELSE a.[qoe_qes_freight_comments_raw] END,
CASE WHEN a.[qoe_qes_freight_comments_p]='ABSENT' AND previous.snapshot_id IS NOT NULL THEN previous.[qoe_qes_freight_comments] ELSE a.[qoe_qes_freight_comments] END,
CASE WHEN a.[qoe_qes_fit_fdt_subtotal_p]='ABSENT' AND previous.snapshot_id IS NOT NULL THEN previous.[qoe_qes_fit_fdt_subtotal_p] ELSE a.[qoe_qes_fit_fdt_subtotal_p] END,
CASE WHEN a.[qoe_qes_fit_fdt_subtotal_p]='ABSENT' AND previous.snapshot_id IS NOT NULL THEN previous.[qoe_qes_fit_fdt_subtotal_w] ELSE a.[qoe_qes_fit_fdt_subtotal_w] END,
CASE WHEN a.[qoe_qes_fit_fdt_subtotal_p]='ABSENT' AND previous.snapshot_id IS NOT NULL THEN previous.[qoe_qes_fit_fdt_subtotal_raw] ELSE a.[qoe_qes_fit_fdt_subtotal_raw] END,
CASE WHEN a.[qoe_qes_fit_fdt_subtotal_p]='ABSENT' AND previous.snapshot_id IS NOT NULL THEN previous.[qoe_qes_fit_fdt_subtotal] ELSE a.[qoe_qes_fit_fdt_subtotal] END,
CASE WHEN a.[requester_name_p]='ABSENT' AND previous.snapshot_id IS NOT NULL THEN previous.[requester_name_p] ELSE a.[requester_name_p] END,
CASE WHEN a.[requester_name_p]='ABSENT' AND previous.snapshot_id IS NOT NULL THEN previous.[requester_name_w] ELSE a.[requester_name_w] END,
CASE WHEN a.[requester_name_p]='ABSENT' AND previous.snapshot_id IS NOT NULL THEN previous.[requester_name_raw] ELSE a.[requester_name_raw] END,
CASE WHEN a.[requester_name_p]='ABSENT' AND previous.snapshot_id IS NOT NULL THEN previous.[requester_name] ELSE a.[requester_name] END,
CASE WHEN a.[qoe_qes_itr_subtotal_p]='ABSENT' AND previous.snapshot_id IS NOT NULL THEN previous.[qoe_qes_itr_subtotal_p] ELSE a.[qoe_qes_itr_subtotal_p] END,
CASE WHEN a.[qoe_qes_itr_subtotal_p]='ABSENT' AND previous.snapshot_id IS NOT NULL THEN previous.[qoe_qes_itr_subtotal_w] ELSE a.[qoe_qes_itr_subtotal_w] END,
CASE WHEN a.[qoe_qes_itr_subtotal_p]='ABSENT' AND previous.snapshot_id IS NOT NULL THEN previous.[qoe_qes_itr_subtotal_raw] ELSE a.[qoe_qes_itr_subtotal_raw] END,
CASE WHEN a.[qoe_qes_itr_subtotal_p]='ABSENT' AND previous.snapshot_id IS NOT NULL THEN previous.[qoe_qes_itr_subtotal] ELSE a.[qoe_qes_itr_subtotal] END,
CASE WHEN a.[qoe_qes_tde_subtotal_p]='ABSENT' AND previous.snapshot_id IS NOT NULL THEN previous.[qoe_qes_tde_subtotal_p] ELSE a.[qoe_qes_tde_subtotal_p] END,
CASE WHEN a.[qoe_qes_tde_subtotal_p]='ABSENT' AND previous.snapshot_id IS NOT NULL THEN previous.[qoe_qes_tde_subtotal_w] ELSE a.[qoe_qes_tde_subtotal_w] END,
CASE WHEN a.[qoe_qes_tde_subtotal_p]='ABSENT' AND previous.snapshot_id IS NOT NULL THEN previous.[qoe_qes_tde_subtotal_raw] ELSE a.[qoe_qes_tde_subtotal_raw] END,
CASE WHEN a.[qoe_qes_tde_subtotal_p]='ABSENT' AND previous.snapshot_id IS NOT NULL THEN previous.[qoe_qes_tde_subtotal] ELSE a.[qoe_qes_tde_subtotal] END,
CASE WHEN a.[qoe_qes_collect_subtotal_p]='ABSENT' AND previous.snapshot_id IS NOT NULL THEN previous.[qoe_qes_collect_subtotal_p] ELSE a.[qoe_qes_collect_subtotal_p] END,
CASE WHEN a.[qoe_qes_collect_subtotal_p]='ABSENT' AND previous.snapshot_id IS NOT NULL THEN previous.[qoe_qes_collect_subtotal_w] ELSE a.[qoe_qes_collect_subtotal_w] END,
CASE WHEN a.[qoe_qes_collect_subtotal_p]='ABSENT' AND previous.snapshot_id IS NOT NULL THEN previous.[qoe_qes_collect_subtotal_raw] ELSE a.[qoe_qes_collect_subtotal_raw] END,
CASE WHEN a.[qoe_qes_collect_subtotal_p]='ABSENT' AND previous.snapshot_id IS NOT NULL THEN previous.[qoe_qes_collect_subtotal] ELSE a.[qoe_qes_collect_subtotal] END,
CASE WHEN a.[qoe_qes_delivery_subtotal_p]='ABSENT' AND previous.snapshot_id IS NOT NULL THEN previous.[qoe_qes_delivery_subtotal_p] ELSE a.[qoe_qes_delivery_subtotal_p] END,
CASE WHEN a.[qoe_qes_delivery_subtotal_p]='ABSENT' AND previous.snapshot_id IS NOT NULL THEN previous.[qoe_qes_delivery_subtotal_w] ELSE a.[qoe_qes_delivery_subtotal_w] END,
CASE WHEN a.[qoe_qes_delivery_subtotal_p]='ABSENT' AND previous.snapshot_id IS NOT NULL THEN previous.[qoe_qes_delivery_subtotal_raw] ELSE a.[qoe_qes_delivery_subtotal_raw] END,
CASE WHEN a.[qoe_qes_delivery_subtotal_p]='ABSENT' AND previous.snapshot_id IS NOT NULL THEN previous.[qoe_qes_delivery_subtotal] ELSE a.[qoe_qes_delivery_subtotal] END,
CASE WHEN a.[qoe_qes_other_fees_p]='ABSENT' AND previous.snapshot_id IS NOT NULL THEN previous.[qoe_qes_other_fees_p] ELSE a.[qoe_qes_other_fees_p] END,
CASE WHEN a.[qoe_qes_other_fees_p]='ABSENT' AND previous.snapshot_id IS NOT NULL THEN previous.[qoe_qes_other_fees_w] ELSE a.[qoe_qes_other_fees_w] END,
CASE WHEN a.[qoe_qes_other_fees_p]='ABSENT' AND previous.snapshot_id IS NOT NULL THEN previous.[qoe_qes_other_fees_raw] ELSE a.[qoe_qes_other_fees_raw] END,
CASE WHEN a.[qoe_qes_other_fees_p]='ABSENT' AND previous.snapshot_id IS NOT NULL THEN previous.[qoe_qes_other_fees] ELSE a.[qoe_qes_other_fees] END,
CASE WHEN a.[qoe_crn_psn_name_p]='ABSENT' AND previous.snapshot_id IS NOT NULL THEN previous.[qoe_crn_psn_name_p] ELSE a.[qoe_crn_psn_name_p] END,
CASE WHEN a.[qoe_crn_psn_name_p]='ABSENT' AND previous.snapshot_id IS NOT NULL THEN previous.[qoe_crn_psn_name_w] ELSE a.[qoe_crn_psn_name_w] END,
CASE WHEN a.[qoe_crn_psn_name_p]='ABSENT' AND previous.snapshot_id IS NOT NULL THEN previous.[qoe_crn_psn_name_raw] ELSE a.[qoe_crn_psn_name_raw] END,
CASE WHEN a.[qoe_crn_psn_name_p]='ABSENT' AND previous.snapshot_id IS NOT NULL THEN previous.[qoe_crn_psn_name] ELSE a.[qoe_crn_psn_name] END,
CASE WHEN a.[qoe_cor_nickname_p]='ABSENT' AND previous.snapshot_id IS NOT NULL THEN previous.[qoe_cor_nickname_p] ELSE a.[qoe_cor_nickname_p] END,
CASE WHEN a.[qoe_cor_nickname_p]='ABSENT' AND previous.snapshot_id IS NOT NULL THEN previous.[qoe_cor_nickname_w] ELSE a.[qoe_cor_nickname_w] END,
CASE WHEN a.[qoe_cor_nickname_p]='ABSENT' AND previous.snapshot_id IS NOT NULL THEN previous.[qoe_cor_nickname_raw] ELSE a.[qoe_cor_nickname_raw] END,
CASE WHEN a.[qoe_cor_nickname_p]='ABSENT' AND previous.snapshot_id IS NOT NULL THEN previous.[qoe_cor_nickname] ELSE a.[qoe_cor_nickname] END
 FROM stg.execution_candidate candidate
 JOIN stg.cotacao_record typed ON typed.stage_record_id=candidate.winner_stage_record_id
 JOIN stg.analytic_quote_attributes a ON a.stage_record_id=typed.stage_record_id
 LEFT JOIN core.analytic_quote_current pointer ON pointer.run_id=@run_id AND pointer.source_key=candidate.source_key
 LEFT JOIN core.analytic_quote_snapshot previous ON previous.snapshot_id=pointer.snapshot_id
 LEFT JOIN core.cotacao current_record WITH(UPDLOCK,HOLDLOCK) ON current_record.environment_name=N'LOCAL_SHADOW'
 AND current_record.source_instance=@integral_source AND current_record.tenant_scope=@integral_tenant
 AND current_record.entity_name=N'cotacoes' AND current_record.source_key=candidate.source_key JOIN stg.execution_record observed ON observed.stage_record_id=typed.stage_record_id
 WHERE candidate.execution_id=@execution_id AND(previous.snapshot_id IS NULL OR typed.freshness_at_utc>previous.freshness_at_utc)
 AND current_record.freshness_at_utc=typed.freshness_at_utc;
 UPDATE pointer SET snapshot_id=s.snapshot_id,business_date=s.business_date
 FROM core.analytic_quote_current pointer JOIN core.analytic_quote_snapshot s ON s.run_id=pointer.run_id AND s.source_key=pointer.source_key
 WHERE s.run_id=@run_id AND s.source_execution=@execution_id;
 INSERT core.analytic_quote_current(run_id,source_key,snapshot_id,last_observation,business_date,latest_extracted_at)
 SELECT s.run_id,s.source_key,s.snapshot_id,@execution_id,s.business_date,s.extracted_at FROM core.analytic_quote_snapshot s
 WHERE s.run_id=@run_id AND s.source_execution=@execution_id AND NOT EXISTS(SELECT 1 FROM core.analytic_quote_current p WHERE p.run_id=s.run_id AND p.source_key=s.source_key);
 INSERT recon.analytic_quote_observation
 SELECT @execution_id,candidate.source_key,typed.stage_record_id,pointer.snapshot_id,
 CASE WHEN previous.source_execution=@execution_id THEN CASE WHEN previous.previous_snapshot IS NULL THEN 'INSERTED' ELSE 'UPDATED' END
 WHEN typed.freshness_at_utc<previous.freshness_at_utc THEN 'STALE_NO_OP' ELSE 'NO_OP' END
 FROM stg.execution_candidate candidate
 JOIN stg.cotacao_record typed ON typed.stage_record_id=candidate.winner_stage_record_id
 JOIN stg.analytic_quote_attributes a ON a.stage_record_id=typed.stage_record_id
 LEFT JOIN core.analytic_quote_current pointer ON pointer.run_id=@run_id AND pointer.source_key=candidate.source_key
 LEFT JOIN core.analytic_quote_snapshot previous ON previous.snapshot_id=pointer.snapshot_id
 LEFT JOIN core.cotacao current_record WITH(UPDLOCK,HOLDLOCK) ON current_record.environment_name=N'LOCAL_SHADOW'
 AND current_record.source_instance=@integral_source AND current_record.tenant_scope=@integral_tenant
 AND current_record.entity_name=N'cotacoes' AND current_record.source_key=candidate.source_key WHERE candidate.execution_id=@execution_id;
 UPDATE pointer SET last_observation=@execution_id,latest_extracted_at=CASE WHEN observed.staged_at_utc>pointer.latest_extracted_at
 THEN observed.staged_at_utc ELSE pointer.latest_extracted_at END
 FROM core.analytic_quote_current pointer JOIN recon.analytic_quote_observation o ON o.source_key=pointer.source_key AND o.execution_id=@execution_id
 JOIN stg.execution_record observed ON observed.stage_record_id=o.stage_record_id WHERE pointer.run_id=@run_id;
 UPDATE ctl.analytic_quote_capture SET state='PUBLISHED',finished_at=SYSUTCDATETIME(),
 physical_rows=(SELECT COUNT_BIG(*) FROM stg.analytic_quote_attributes WHERE execution_id=@execution_id),
 roots=(SELECT COUNT_BIG(*) FROM recon.analytic_quote_observation WHERE execution_id=@execution_id),
 inserts=(SELECT COUNT_BIG(*) FROM recon.analytic_quote_observation WHERE execution_id=@execution_id AND application='INSERTED'),
 updates=(SELECT COUNT_BIG(*) FROM recon.analytic_quote_observation WHERE execution_id=@execution_id AND application='UPDATED'),
 noops=(SELECT COUNT_BIG(*) FROM recon.analytic_quote_observation WHERE execution_id=@execution_id AND application='NO_OP'),
 stale=(SELECT COUNT_BIG(*) FROM recon.analytic_quote_observation WHERE execution_id=@execution_id AND application='STALE_NO_OP'),blocked=0
 WHERE execution_id=@execution_id;
 SELECT physical_rows,roots,inserts,updates,noops,stale,blocked FROM ctl.analytic_quote_capture WHERE execution_id=@execution_id;
END;
GO
ALTER PROCEDURE recon.usp_capture_runtime_vertical_output @execution_id UNIQUEIDENTIFIER
AS
BEGIN
 SET NOCOUNT ON;
 IF @@TRANCOUNT=0 OR NOT EXISTS(SELECT 1 FROM ctl.execution_publication_event WHERE execution_id=@execution_id)
   THROW 52830,N'OUTPUT_REQUIRES_PUBLICATION_TRANSACTION',1;
 IF EXISTS(SELECT 1 FROM recon.runtime_vertical_output WHERE execution_id=@execution_id) RETURN;
 DECLARE @entity NVARCHAR(128),@now DATETIME2(3)=SYSUTCDATETIME();
 SELECT @entity=p.entity_name FROM ctl.execution_attempt a JOIN ctl.execution_partition p ON p.partition_id=a.partition_id
 WHERE a.execution_id=@execution_id AND p.environment_name=N'LOCAL_SHADOW' AND ((p.source_instance=N'LOCAL_V2' AND p.tenant_scope=N'LOCAL_V2') OR EXISTS(
 SELECT 1 FROM ctl.analytic_quote_capture local_capture WHERE local_capture.execution_id=a.execution_id
 AND ctl.fn_analytic_source_scope(local_capture.run_id,p.source_instance,p.tenant_scope)=1));
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
 SELECT @execution_id,@entity,'ROOT',c.source_key,N'',
 JSON_MODIFY(c.payload_json,'$._typed',JSON_QUERY((SELECT c.total_amount AS totalAmount,c.user_name_normalized AS userName,
 c.tariff_minimum_amount AS tariffMinimum,c.tariff_currency_code AS currency,c.tariff_unit_code AS unit,c.tariff_rounding_mode AS rounding
 FOR JSON PATH,WITHOUT_ARRAY_WRAPPER,INCLUDE_NULL_VALUES))),c.field_presence_json,c.tariff_reference_release_id,@now
 FROM recon.execution_candidate_application a JOIN core.cotacao c ON c.record_state_id=a.record_state_id WHERE a.execution_id=@execution_id;
 ELSE IF @entity=N'localizacao_cargas'
 INSERT recon.runtime_vertical_output(execution_id,entity_name,row_kind,source_key,child_key,projection_json,presence_json,captured_at_utc)
 SELECT @execution_id,@entity,'ROOT',c.source_key,N'',
 JSON_MODIFY(c.payload_json,'$._typed',JSON_QUERY((SELECT c.invoices_volumes_typed AS volumes,c.status_normalized AS status,
 c.status_terminal AS terminal,c.freight_candidate_state AS freightRelation,c.freight_candidate_provenance AS freightProvenance
 FOR JSON PATH,WITHOUT_ARRAY_WRAPPER,INCLUDE_NULL_VALUES))),c.field_presence_json,@now
 FROM recon.execution_candidate_application a JOIN core.localizacao_cargas c ON c.record_state_id=a.record_state_id WHERE a.execution_id=@execution_id;
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
