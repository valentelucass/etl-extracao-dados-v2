SET ANSI_NULLS ON; SET QUOTED_IDENTIFIER ON;
IF DB_NAME()<>N'ETL_SISTEMA_V2_SHADOW' THROW 53930,N'P03_EXACT_SHADOW_REQUIRED',1;
GO
-- ADR0052 / SEQ-REF-01: reference change is audited independently of source freshness.
CREATE TABLE recon.cotacao_reference_revision (
 execution_id UNIQUEIDENTIFIER NOT NULL CONSTRAINT FK_quote_reference_execution REFERENCES ctl.execution_attempt(execution_id),
 cotacao_id BIGINT NOT NULL CONSTRAINT FK_quote_reference_quote REFERENCES core.cotacao(cotacao_id),
 previous_release BIGINT NOT NULL CONSTRAINT FK_quote_reference_previous REFERENCES ref.reference_release(reference_release_id),
 reference_release BIGINT NOT NULL CONSTRAINT FK_quote_reference_release REFERENCES ref.reference_release(reference_release_id),
 source_freshness DATETIME2(3) NOT NULL, source_hash CHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL,
 CONSTRAINT PK_cotacao_reference_revision PRIMARY KEY(execution_id,cotacao_id),
 CONSTRAINT CK_cotacao_reference_revision CHECK(previous_release<>reference_release)
);
GO
CREATE TRIGGER recon.trg_cotacao_reference_revision ON recon.cotacao_reference_revision AFTER UPDATE,DELETE AS
BEGIN THROW 53932,N'QUOTE_REFERENCE_HISTORY_IMMUTABLE',1; END;
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
  -- Only an equal, verified source observation can revise its independent tariff reference.
  INSERT recon.cotacao_reference_revision(execution_id,cotacao_id,previous_release,reference_release,source_freshness,source_hash)
  SELECT @execution_id,c.cotacao_id,c.tariff_reference_release_id,t.reference_release_id,c.freshness_at_utc,c.attribute_hash
  FROM core.cotacao c JOIN stg.execution_candidate x ON x.source_key=c.source_key
  JOIN stg.cotacao_record typed ON typed.stage_record_id=x.winner_stage_record_id
  JOIN @tariff_resolution t ON t.source_key=x.source_key
  WHERE x.execution_id=@execution_id AND c.environment_name=@environment_name AND c.source_instance=@source_instance
  AND c.tenant_scope=@tenant_scope AND c.entity_name=N'cotacoes'
  AND typed.freshness_at_utc=c.freshness_at_utc AND typed.attribute_hash=c.attribute_hash
  AND c.tariff_reference_release_id<>t.reference_release_id;
  UPDATE c SET tariff_reference_release_id=t.reference_release_id,tariff_effective_date=t.effective_date,
    tariff_minimum_amount=t.minimum_amount,tariff_currency_code=t.currency_code,
    tariff_unit_code=t.unit_code,tariff_rounding_mode=t.rounding_mode
  FROM core.cotacao c JOIN recon.cotacao_reference_revision r ON r.cotacao_id=c.cotacao_id AND r.execution_id=@execution_id
  JOIN stg.execution_candidate x ON x.execution_id=@execution_id AND x.source_key=c.source_key
  JOIN @tariff_resolution t ON t.source_key=x.source_key;
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
 WHERE candidate.execution_id=@execution_id AND(previous.snapshot_id IS NULL OR typed.freshness_at_utc>previous.freshness_at_utc
 OR (typed.freshness_at_utc=previous.freshness_at_utc
 AND current_record.attribute_hash=previous.attribute_hash
 AND current_record.tariff_reference_release_id<>previous.tariff_release))
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
