-- Analytical snapshots follow effective publication and preserve all typed input attributes.
SET ANSI_NULLS ON;SET QUOTED_IDENTIFIER ON;
GO
CREATE TABLE core.analytic_quote_snapshot (
 snapshot_id BIGINT IDENTITY NOT NULL CONSTRAINT PK_ana_quote_snapshot PRIMARY KEY,
 run_id UNIQUEIDENTIFIER NOT NULL REFERENCES ctl.analytic_lab_run(run_id),source_key NVARCHAR(256) COLLATE Latin1_General_100_BIN2 NOT NULL,
 source_execution UNIQUEIDENTIFIER NOT NULL REFERENCES ctl.analytic_quote_capture(execution_id),
 stage_record_id BIGINT NOT NULL REFERENCES stg.analytic_quote_attributes(stage_record_id),
 previous_snapshot BIGINT NULL REFERENCES core.analytic_quote_snapshot(snapshot_id),
 cotacao_id BIGINT NOT NULL REFERENCES core.cotacao(cotacao_id),attribute_hash CHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL,
 freshness_at_utc DATETIME2(3) NOT NULL,business_date DATE NOT NULL,user_normalized NVARCHAR(1024) COLLATE Latin1_General_100_BIN2 NULL,
 tariff_release BIGINT NOT NULL REFERENCES ref.reference_release(reference_release_id),tariff_minimum DECIMAL(19,4) NOT NULL,
 tariff_currency CHAR(3) COLLATE Latin1_General_100_BIN2 NOT NULL,tariff_unit NVARCHAR(32) COLLATE Latin1_General_100_BIN2 NOT NULL,
 tariff_rounding NVARCHAR(24) COLLATE Latin1_General_100_BIN2 NOT NULL,extracted_at DATETIME2(7) NOT NULL,
 [requested_at_p] VARCHAR(6) NULL,[requested_at_w] VARCHAR(8) NULL,[requested_at_raw] NVARCHAR(4000) COLLATE Latin1_General_100_BIN2 NULL,[requested_at] BIGINT NULL,[requested_at_nano] INT NULL,
 [sequence_code_p] VARCHAR(6) NULL,[sequence_code_w] VARCHAR(8) NULL,[sequence_code_raw] NVARCHAR(4000) COLLATE Latin1_General_100_BIN2 NULL,[sequence_code] VARCHAR(40) COLLATE Latin1_General_100_BIN2 NULL,
 [qoe_qes_fon_name_p] VARCHAR(6) NULL,[qoe_qes_fon_name_w] VARCHAR(8) NULL,[qoe_qes_fon_name_raw] NVARCHAR(4000) COLLATE Latin1_General_100_BIN2 NULL,[qoe_qes_fon_name] NVARCHAR(1024) COLLATE Latin1_General_100_BIN2 NULL,
 [qoe_cor_document_p] VARCHAR(6) NULL,[qoe_cor_document_w] VARCHAR(8) NULL,[qoe_cor_document_raw] NVARCHAR(4000) COLLATE Latin1_General_100_BIN2 NULL,[qoe_cor_document] NVARCHAR(1024) COLLATE Latin1_General_100_BIN2 NULL,
 [qoe_cor_name_p] VARCHAR(6) NULL,[qoe_cor_name_w] VARCHAR(8) NULL,[qoe_cor_name_raw] NVARCHAR(4000) COLLATE Latin1_General_100_BIN2 NULL,[qoe_cor_name] NVARCHAR(1024) COLLATE Latin1_General_100_BIN2 NULL,
 [qoe_qes_ony_name_p] VARCHAR(6) NULL,[qoe_qes_ony_name_w] VARCHAR(8) NULL,[qoe_qes_ony_name_raw] NVARCHAR(4000) COLLATE Latin1_General_100_BIN2 NULL,[qoe_qes_ony_name] NVARCHAR(1024) COLLATE Latin1_General_100_BIN2 NULL,
 [qoe_qes_ony_sae_code_p] VARCHAR(6) NULL,[qoe_qes_ony_sae_code_w] VARCHAR(8) NULL,[qoe_qes_ony_sae_code_raw] NVARCHAR(4000) COLLATE Latin1_General_100_BIN2 NULL,[qoe_qes_ony_sae_code] NVARCHAR(1024) COLLATE Latin1_General_100_BIN2 NULL,
 [qoe_qes_diy_name_p] VARCHAR(6) NULL,[qoe_qes_diy_name_w] VARCHAR(8) NULL,[qoe_qes_diy_name_raw] NVARCHAR(4000) COLLATE Latin1_General_100_BIN2 NULL,[qoe_qes_diy_name] NVARCHAR(1024) COLLATE Latin1_General_100_BIN2 NULL,
 [qoe_qes_diy_sae_code_p] VARCHAR(6) NULL,[qoe_qes_diy_sae_code_w] VARCHAR(8) NULL,[qoe_qes_diy_sae_code_raw] NVARCHAR(4000) COLLATE Latin1_General_100_BIN2 NULL,[qoe_qes_diy_sae_code] NVARCHAR(1024) COLLATE Latin1_General_100_BIN2 NULL,
 [qoe_qes_cre_name_p] VARCHAR(6) NULL,[qoe_qes_cre_name_w] VARCHAR(8) NULL,[qoe_qes_cre_name_raw] NVARCHAR(4000) COLLATE Latin1_General_100_BIN2 NULL,[qoe_qes_cre_name] NVARCHAR(1024) COLLATE Latin1_General_100_BIN2 NULL,
 [qoe_qes_invoices_volumes_p] VARCHAR(6) NULL,[qoe_qes_invoices_volumes_w] VARCHAR(8) NULL,[qoe_qes_invoices_volumes_raw] NVARCHAR(4000) COLLATE Latin1_General_100_BIN2 NULL,[qoe_qes_invoices_volumes] VARCHAR(40) COLLATE Latin1_General_100_BIN2 NULL,
 [qoe_qes_taxed_weight_p] VARCHAR(6) NULL,[qoe_qes_taxed_weight_w] VARCHAR(8) NULL,[qoe_qes_taxed_weight_raw] NVARCHAR(4000) COLLATE Latin1_General_100_BIN2 NULL,[qoe_qes_taxed_weight] DECIMAL(28,8) NULL,
 [qoe_qes_invoices_value_p] VARCHAR(6) NULL,[qoe_qes_invoices_value_w] VARCHAR(8) NULL,[qoe_qes_invoices_value_raw] NVARCHAR(4000) COLLATE Latin1_General_100_BIN2 NULL,[qoe_qes_invoices_value] DECIMAL(28,8) NULL,
 [qoe_qes_total_p] VARCHAR(6) NULL,[qoe_qes_total_w] VARCHAR(8) NULL,[qoe_qes_total_raw] NVARCHAR(4000) COLLATE Latin1_General_100_BIN2 NULL,[qoe_qes_total] DECIMAL(28,8) NULL,
 [qoe_qes_fit_fhe_cte_issued_at_p] VARCHAR(6) NULL,[qoe_qes_fit_fhe_cte_issued_at_w] VARCHAR(8) NULL,[qoe_qes_fit_fhe_cte_issued_at_raw] NVARCHAR(4000) COLLATE Latin1_General_100_BIN2 NULL,[qoe_qes_fit_fhe_cte_issued_at] BIGINT NULL,[qoe_qes_fit_fhe_cte_issued_at_nano] INT NULL,
 [qoe_qes_fit_nse_issued_at_p] VARCHAR(6) NULL,[qoe_qes_fit_nse_issued_at_w] VARCHAR(8) NULL,[qoe_qes_fit_nse_issued_at_raw] NVARCHAR(4000) COLLATE Latin1_General_100_BIN2 NULL,[qoe_qes_fit_nse_issued_at] BIGINT NULL,[qoe_qes_fit_nse_issued_at_nano] INT NULL,
 [qoe_uer_name_p] VARCHAR(6) NULL,[qoe_uer_name_w] VARCHAR(8) NULL,[qoe_uer_name_raw] NVARCHAR(4000) COLLATE Latin1_General_100_BIN2 NULL,[qoe_uer_name] NVARCHAR(1024) COLLATE Latin1_General_100_BIN2 NULL,
 [qoe_crn_psn_nickname_p] VARCHAR(6) NULL,[qoe_crn_psn_nickname_w] VARCHAR(8) NULL,[qoe_crn_psn_nickname_raw] NVARCHAR(4000) COLLATE Latin1_General_100_BIN2 NULL,[qoe_crn_psn_nickname] NVARCHAR(1024) COLLATE Latin1_General_100_BIN2 NULL,
 [qoe_qes_sdr_document_p] VARCHAR(6) NULL,[qoe_qes_sdr_document_w] VARCHAR(8) NULL,[qoe_qes_sdr_document_raw] NVARCHAR(4000) COLLATE Latin1_General_100_BIN2 NULL,[qoe_qes_sdr_document] NVARCHAR(1024) COLLATE Latin1_General_100_BIN2 NULL,
 [qoe_qes_sdr_nickname_p] VARCHAR(6) NULL,[qoe_qes_sdr_nickname_w] VARCHAR(8) NULL,[qoe_qes_sdr_nickname_raw] NVARCHAR(4000) COLLATE Latin1_General_100_BIN2 NULL,[qoe_qes_sdr_nickname] NVARCHAR(1024) COLLATE Latin1_General_100_BIN2 NULL,
 [qoe_qes_rpt_document_p] VARCHAR(6) NULL,[qoe_qes_rpt_document_w] VARCHAR(8) NULL,[qoe_qes_rpt_document_raw] NVARCHAR(4000) COLLATE Latin1_General_100_BIN2 NULL,[qoe_qes_rpt_document] NVARCHAR(1024) COLLATE Latin1_General_100_BIN2 NULL,
 [qoe_qes_rpt_nickname_p] VARCHAR(6) NULL,[qoe_qes_rpt_nickname_w] VARCHAR(8) NULL,[qoe_qes_rpt_nickname_raw] NVARCHAR(4000) COLLATE Latin1_General_100_BIN2 NULL,[qoe_qes_rpt_nickname] NVARCHAR(1024) COLLATE Latin1_General_100_BIN2 NULL,
 [qoe_qes_origin_postal_code_p] VARCHAR(6) NULL,[qoe_qes_origin_postal_code_w] VARCHAR(8) NULL,[qoe_qes_origin_postal_code_raw] NVARCHAR(4000) COLLATE Latin1_General_100_BIN2 NULL,[qoe_qes_origin_postal_code] NVARCHAR(1024) COLLATE Latin1_General_100_BIN2 NULL,
 [qoe_qes_destination_postal_code_p] VARCHAR(6) NULL,[qoe_qes_destination_postal_code_w] VARCHAR(8) NULL,[qoe_qes_destination_postal_code_raw] NVARCHAR(4000) COLLATE Latin1_General_100_BIN2 NULL,[qoe_qes_destination_postal_code] NVARCHAR(1024) COLLATE Latin1_General_100_BIN2 NULL,
 [qoe_qes_real_weight_p] VARCHAR(6) NULL,[qoe_qes_real_weight_w] VARCHAR(8) NULL,[qoe_qes_real_weight_raw] NVARCHAR(4000) COLLATE Latin1_General_100_BIN2 NULL,[qoe_qes_real_weight] DECIMAL(28,8) NULL,
 [qoe_qes_disapprove_comments_p] VARCHAR(6) NULL,[qoe_qes_disapprove_comments_w] VARCHAR(8) NULL,[qoe_qes_disapprove_comments_raw] NVARCHAR(4000) COLLATE Latin1_General_100_BIN2 NULL,[qoe_qes_disapprove_comments] NVARCHAR(1024) COLLATE Latin1_General_100_BIN2 NULL,
 [qoe_qes_freight_comments_p] VARCHAR(6) NULL,[qoe_qes_freight_comments_w] VARCHAR(8) NULL,[qoe_qes_freight_comments_raw] NVARCHAR(4000) COLLATE Latin1_General_100_BIN2 NULL,[qoe_qes_freight_comments] NVARCHAR(1024) COLLATE Latin1_General_100_BIN2 NULL,
 [qoe_qes_fit_fdt_subtotal_p] VARCHAR(6) NULL,[qoe_qes_fit_fdt_subtotal_w] VARCHAR(8) NULL,[qoe_qes_fit_fdt_subtotal_raw] NVARCHAR(4000) COLLATE Latin1_General_100_BIN2 NULL,[qoe_qes_fit_fdt_subtotal] DECIMAL(28,8) NULL,
 [requester_name_p] VARCHAR(6) NULL,[requester_name_w] VARCHAR(8) NULL,[requester_name_raw] NVARCHAR(4000) COLLATE Latin1_General_100_BIN2 NULL,[requester_name] NVARCHAR(1024) COLLATE Latin1_General_100_BIN2 NULL,
 [qoe_qes_itr_subtotal_p] VARCHAR(6) NULL,[qoe_qes_itr_subtotal_w] VARCHAR(8) NULL,[qoe_qes_itr_subtotal_raw] NVARCHAR(4000) COLLATE Latin1_General_100_BIN2 NULL,[qoe_qes_itr_subtotal] DECIMAL(28,8) NULL,
 [qoe_qes_tde_subtotal_p] VARCHAR(6) NULL,[qoe_qes_tde_subtotal_w] VARCHAR(8) NULL,[qoe_qes_tde_subtotal_raw] NVARCHAR(4000) COLLATE Latin1_General_100_BIN2 NULL,[qoe_qes_tde_subtotal] DECIMAL(28,8) NULL,
 [qoe_qes_collect_subtotal_p] VARCHAR(6) NULL,[qoe_qes_collect_subtotal_w] VARCHAR(8) NULL,[qoe_qes_collect_subtotal_raw] NVARCHAR(4000) COLLATE Latin1_General_100_BIN2 NULL,[qoe_qes_collect_subtotal] DECIMAL(28,8) NULL,
 [qoe_qes_delivery_subtotal_p] VARCHAR(6) NULL,[qoe_qes_delivery_subtotal_w] VARCHAR(8) NULL,[qoe_qes_delivery_subtotal_raw] NVARCHAR(4000) COLLATE Latin1_General_100_BIN2 NULL,[qoe_qes_delivery_subtotal] DECIMAL(28,8) NULL,
 [qoe_qes_other_fees_p] VARCHAR(6) NULL,[qoe_qes_other_fees_w] VARCHAR(8) NULL,[qoe_qes_other_fees_raw] NVARCHAR(4000) COLLATE Latin1_General_100_BIN2 NULL,[qoe_qes_other_fees] DECIMAL(28,8) NULL,
 [qoe_crn_psn_name_p] VARCHAR(6) NULL,[qoe_crn_psn_name_w] VARCHAR(8) NULL,[qoe_crn_psn_name_raw] NVARCHAR(4000) COLLATE Latin1_General_100_BIN2 NULL,[qoe_crn_psn_name] NVARCHAR(1024) COLLATE Latin1_General_100_BIN2 NULL,
 [qoe_cor_nickname_p] VARCHAR(6) NULL,[qoe_cor_nickname_w] VARCHAR(8) NULL,[qoe_cor_nickname_raw] NVARCHAR(4000) COLLATE Latin1_General_100_BIN2 NULL,[qoe_cor_nickname] NVARCHAR(1024) COLLATE Latin1_General_100_BIN2 NULL,
 CONSTRAINT UQ_ana_quote_snapshot_execution UNIQUE(run_id,source_key,source_execution)
);
GO
CREATE TRIGGER core.trg_analytic_quote_snapshot ON core.analytic_quote_snapshot AFTER UPDATE,DELETE AS
BEGIN SET NOCOUNT ON;THROW 53710,N'ANA_QUOTE_SNAPSHOT_IMMUTABLE',1;END;
GO
CREATE TABLE core.analytic_quote_current (
 run_id UNIQUEIDENTIFIER NOT NULL REFERENCES ctl.analytic_lab_run(run_id),source_key NVARCHAR(256) COLLATE Latin1_General_100_BIN2 NOT NULL,
 snapshot_id BIGINT NOT NULL REFERENCES core.analytic_quote_snapshot(snapshot_id),
 last_observation UNIQUEIDENTIFIER NOT NULL REFERENCES ctl.analytic_quote_capture(execution_id),
 business_date DATE NOT NULL,latest_extracted_at DATETIME2(7) NOT NULL,
 CONSTRAINT PK_ana_quote_current PRIMARY KEY(run_id,source_key)
);
CREATE INDEX IX_ana_quote_current_window ON core.analytic_quote_current(run_id,business_date,source_key) INCLUDE(snapshot_id,last_observation,latest_extracted_at);
GO
CREATE TABLE recon.analytic_quote_observation (
 execution_id UNIQUEIDENTIFIER NOT NULL REFERENCES ctl.analytic_quote_capture(execution_id),source_key NVARCHAR(256) COLLATE Latin1_General_100_BIN2 NOT NULL,
 stage_record_id BIGINT NOT NULL REFERENCES stg.analytic_quote_attributes(stage_record_id),
 snapshot_id BIGINT NOT NULL REFERENCES core.analytic_quote_snapshot(snapshot_id),application VARCHAR(16) NOT NULL,
 CONSTRAINT PK_ana_quote_observation PRIMARY KEY(execution_id,source_key),
 CONSTRAINT CK_ana_quote_observation CHECK(application IN('INSERTED','UPDATED','NO_OP','STALE_NO_OP'))
);
GO
CREATE TRIGGER recon.trg_analytic_quote_observation ON recon.analytic_quote_observation AFTER UPDATE,DELETE AS
BEGIN SET NOCOUNT ON;THROW 53711,N'ANA_QUOTE_OBSERVATION_IMMUTABLE',1;END;
GO
CREATE PROCEDURE core.usp_preflight_analytic_quotes @run_id UNIQUEIDENTIFIER,@execution_id UNIQUEIDENTIFIER,@revision INT,@release BIGINT AS
BEGIN
 SET NOCOUNT ON;SET XACT_ABORT OFF;
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
 AND current_record.source_instance=N'LOCAL_V2' AND current_record.tenant_scope=N'LOCAL_V2'
 AND current_record.entity_name=N'cotacoes' AND current_record.source_key=candidate.source_key WHERE candidate.execution_id=@execution_id AND current_record.cotacao_id IS NOT NULL
 AND(previous.snapshot_id IS NULL OR current_record.attribute_hash<>previous.attribute_hash OR current_record.freshness_at_utc<>previous.freshness_at_utc))
 THROW 53714,N'ANA_QUOTE_FOREIGN_CURRENT_CONTEXT',1;
 IF EXISTS(SELECT 1 FROM stg.execution_candidate candidate
 JOIN stg.cotacao_record typed ON typed.stage_record_id=candidate.winner_stage_record_id
 JOIN stg.analytic_quote_attributes a ON a.stage_record_id=typed.stage_record_id
 LEFT JOIN core.analytic_quote_current pointer ON pointer.run_id=@run_id AND pointer.source_key=candidate.source_key
 LEFT JOIN core.analytic_quote_snapshot previous ON previous.snapshot_id=pointer.snapshot_id
 LEFT JOIN core.cotacao current_record WITH(UPDLOCK,HOLDLOCK) ON current_record.environment_name=N'LOCAL_SHADOW'
 AND current_record.source_instance=N'LOCAL_V2' AND current_record.tenant_scope=N'LOCAL_V2'
 AND current_record.entity_name=N'cotacoes' AND current_record.source_key=candidate.source_key WHERE candidate.execution_id=@execution_id AND previous.snapshot_id IS NOT NULL
 AND typed.freshness_at_utc=previous.freshness_at_utc AND EXISTS(SELECT CASE WHEN a.[requested_at_p]='ABSENT' THEN previous.[requested_at] ELSE a.[requested_at] END,CASE WHEN a.[requested_at_p]='ABSENT' THEN previous.[requested_at_nano] ELSE a.[requested_at_nano] END,CASE WHEN a.[sequence_code_p]='ABSENT' THEN previous.[sequence_code] ELSE a.[sequence_code] END,CASE WHEN a.[qoe_qes_fon_name_p]='ABSENT' THEN previous.[qoe_qes_fon_name] ELSE a.[qoe_qes_fon_name] END,CASE WHEN a.[qoe_cor_document_p]='ABSENT' THEN previous.[qoe_cor_document] ELSE a.[qoe_cor_document] END,CASE WHEN a.[qoe_cor_name_p]='ABSENT' THEN previous.[qoe_cor_name] ELSE a.[qoe_cor_name] END,CASE WHEN a.[qoe_qes_ony_name_p]='ABSENT' THEN previous.[qoe_qes_ony_name] ELSE a.[qoe_qes_ony_name] END,CASE WHEN a.[qoe_qes_ony_sae_code_p]='ABSENT' THEN previous.[qoe_qes_ony_sae_code] ELSE a.[qoe_qes_ony_sae_code] END,CASE WHEN a.[qoe_qes_diy_name_p]='ABSENT' THEN previous.[qoe_qes_diy_name] ELSE a.[qoe_qes_diy_name] END,CASE WHEN a.[qoe_qes_diy_sae_code_p]='ABSENT' THEN previous.[qoe_qes_diy_sae_code] ELSE a.[qoe_qes_diy_sae_code] END,CASE WHEN a.[qoe_qes_cre_name_p]='ABSENT' THEN previous.[qoe_qes_cre_name] ELSE a.[qoe_qes_cre_name] END,CASE WHEN a.[qoe_qes_invoices_volumes_p]='ABSENT' THEN previous.[qoe_qes_invoices_volumes] ELSE a.[qoe_qes_invoices_volumes] END,CASE WHEN a.[qoe_qes_taxed_weight_p]='ABSENT' THEN previous.[qoe_qes_taxed_weight] ELSE a.[qoe_qes_taxed_weight] END,CASE WHEN a.[qoe_qes_invoices_value_p]='ABSENT' THEN previous.[qoe_qes_invoices_value] ELSE a.[qoe_qes_invoices_value] END,CASE WHEN a.[qoe_qes_total_p]='ABSENT' THEN previous.[qoe_qes_total] ELSE a.[qoe_qes_total] END,CASE WHEN a.[qoe_qes_fit_fhe_cte_issued_at_p]='ABSENT' THEN previous.[qoe_qes_fit_fhe_cte_issued_at] ELSE a.[qoe_qes_fit_fhe_cte_issued_at] END,CASE WHEN a.[qoe_qes_fit_fhe_cte_issued_at_p]='ABSENT' THEN previous.[qoe_qes_fit_fhe_cte_issued_at_nano] ELSE a.[qoe_qes_fit_fhe_cte_issued_at_nano] END,CASE WHEN a.[qoe_qes_fit_nse_issued_at_p]='ABSENT' THEN previous.[qoe_qes_fit_nse_issued_at] ELSE a.[qoe_qes_fit_nse_issued_at] END,CASE WHEN a.[qoe_qes_fit_nse_issued_at_p]='ABSENT' THEN previous.[qoe_qes_fit_nse_issued_at_nano] ELSE a.[qoe_qes_fit_nse_issued_at_nano] END,CASE WHEN a.[qoe_uer_name_p]='ABSENT' THEN previous.[qoe_uer_name] ELSE a.[qoe_uer_name] END,CASE WHEN a.[qoe_crn_psn_nickname_p]='ABSENT' THEN previous.[qoe_crn_psn_nickname] ELSE a.[qoe_crn_psn_nickname] END,CASE WHEN a.[qoe_qes_sdr_document_p]='ABSENT' THEN previous.[qoe_qes_sdr_document] ELSE a.[qoe_qes_sdr_document] END,CASE WHEN a.[qoe_qes_sdr_nickname_p]='ABSENT' THEN previous.[qoe_qes_sdr_nickname] ELSE a.[qoe_qes_sdr_nickname] END,CASE WHEN a.[qoe_qes_rpt_document_p]='ABSENT' THEN previous.[qoe_qes_rpt_document] ELSE a.[qoe_qes_rpt_document] END,CASE WHEN a.[qoe_qes_rpt_nickname_p]='ABSENT' THEN previous.[qoe_qes_rpt_nickname] ELSE a.[qoe_qes_rpt_nickname] END,CASE WHEN a.[qoe_qes_origin_postal_code_p]='ABSENT' THEN previous.[qoe_qes_origin_postal_code] ELSE a.[qoe_qes_origin_postal_code] END,CASE WHEN a.[qoe_qes_destination_postal_code_p]='ABSENT' THEN previous.[qoe_qes_destination_postal_code] ELSE a.[qoe_qes_destination_postal_code] END,CASE WHEN a.[qoe_qes_real_weight_p]='ABSENT' THEN previous.[qoe_qes_real_weight] ELSE a.[qoe_qes_real_weight] END,CASE WHEN a.[qoe_qes_disapprove_comments_p]='ABSENT' THEN previous.[qoe_qes_disapprove_comments] ELSE a.[qoe_qes_disapprove_comments] END,CASE WHEN a.[qoe_qes_freight_comments_p]='ABSENT' THEN previous.[qoe_qes_freight_comments] ELSE a.[qoe_qes_freight_comments] END,CASE WHEN a.[qoe_qes_fit_fdt_subtotal_p]='ABSENT' THEN previous.[qoe_qes_fit_fdt_subtotal] ELSE a.[qoe_qes_fit_fdt_subtotal] END,CASE WHEN a.[requester_name_p]='ABSENT' THEN previous.[requester_name] ELSE a.[requester_name] END,CASE WHEN a.[qoe_qes_itr_subtotal_p]='ABSENT' THEN previous.[qoe_qes_itr_subtotal] ELSE a.[qoe_qes_itr_subtotal] END,CASE WHEN a.[qoe_qes_tde_subtotal_p]='ABSENT' THEN previous.[qoe_qes_tde_subtotal] ELSE a.[qoe_qes_tde_subtotal] END,CASE WHEN a.[qoe_qes_collect_subtotal_p]='ABSENT' THEN previous.[qoe_qes_collect_subtotal] ELSE a.[qoe_qes_collect_subtotal] END,CASE WHEN a.[qoe_qes_delivery_subtotal_p]='ABSENT' THEN previous.[qoe_qes_delivery_subtotal] ELSE a.[qoe_qes_delivery_subtotal] END,CASE WHEN a.[qoe_qes_other_fees_p]='ABSENT' THEN previous.[qoe_qes_other_fees] ELSE a.[qoe_qes_other_fees] END,CASE WHEN a.[qoe_crn_psn_name_p]='ABSENT' THEN previous.[qoe_crn_psn_name] ELSE a.[qoe_crn_psn_name] END,CASE WHEN a.[qoe_cor_nickname_p]='ABSENT' THEN previous.[qoe_cor_nickname] ELSE a.[qoe_cor_nickname] END EXCEPT SELECT previous.[requested_at],previous.[requested_at_nano],previous.[sequence_code],previous.[qoe_qes_fon_name],previous.[qoe_cor_document],previous.[qoe_cor_name],previous.[qoe_qes_ony_name],previous.[qoe_qes_ony_sae_code],previous.[qoe_qes_diy_name],previous.[qoe_qes_diy_sae_code],previous.[qoe_qes_cre_name],previous.[qoe_qes_invoices_volumes],previous.[qoe_qes_taxed_weight],previous.[qoe_qes_invoices_value],previous.[qoe_qes_total],previous.[qoe_qes_fit_fhe_cte_issued_at],previous.[qoe_qes_fit_fhe_cte_issued_at_nano],previous.[qoe_qes_fit_nse_issued_at],previous.[qoe_qes_fit_nse_issued_at_nano],previous.[qoe_uer_name],previous.[qoe_crn_psn_nickname],previous.[qoe_qes_sdr_document],previous.[qoe_qes_sdr_nickname],previous.[qoe_qes_rpt_document],previous.[qoe_qes_rpt_nickname],previous.[qoe_qes_origin_postal_code],previous.[qoe_qes_destination_postal_code],previous.[qoe_qes_real_weight],previous.[qoe_qes_disapprove_comments],previous.[qoe_qes_freight_comments],previous.[qoe_qes_fit_fdt_subtotal],previous.[requester_name],previous.[qoe_qes_itr_subtotal],previous.[qoe_qes_tde_subtotal],previous.[qoe_qes_collect_subtotal],previous.[qoe_qes_delivery_subtotal],previous.[qoe_qes_other_fees],previous.[qoe_crn_psn_name],previous.[qoe_cor_nickname]))
 THROW 53715,N'ANA_QUOTE_EXACT_CLOCK_CONFLICT',1;
 IF EXISTS(SELECT 1 FROM stg.execution_candidate candidate
 JOIN stg.cotacao_record typed ON typed.stage_record_id=candidate.winner_stage_record_id
 JOIN stg.analytic_quote_attributes a ON a.stage_record_id=typed.stage_record_id
 LEFT JOIN core.analytic_quote_current pointer ON pointer.run_id=@run_id AND pointer.source_key=candidate.source_key
 LEFT JOIN core.analytic_quote_snapshot previous ON previous.snapshot_id=pointer.snapshot_id
 LEFT JOIN core.cotacao current_record WITH(UPDLOCK,HOLDLOCK) ON current_record.environment_name=N'LOCAL_SHADOW'
 AND current_record.source_instance=N'LOCAL_V2' AND current_record.tenant_scope=N'LOCAL_V2'
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
CREATE PROCEDURE core.usp_prepare_analytic_quotes @run_id UNIQUEIDENTIFIER,@execution_id UNIQUEIDENTIFIER AS
BEGIN
 SET NOCOUNT ON;SET XACT_ABORT OFF;
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
 AND current_record.source_instance=N'LOCAL_V2' AND current_record.tenant_scope=N'LOCAL_V2'
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
 AND current_record.source_instance=N'LOCAL_V2' AND current_record.tenant_scope=N'LOCAL_V2'
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
 AND current_record.source_instance=N'LOCAL_V2' AND current_record.tenant_scope=N'LOCAL_V2'
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
ALTER VIEW core.analytic_lab_source_current AS
 SELECT g.run_id,CONVERT(VARCHAR(8),d.entity) COLLATE Latin1_General_100_BIN2 entity,d.source_key,
 o.execution_id,c.partition_date business_date,CONVERT(BIT,CASE WHEN d.state='VALID' THEN 1 ELSE 0 END) usable
 FROM ctl.analytic_lab_source_group g JOIN core.expansion_lab_dependency d ON d.run_id=g.expansion_run
 JOIN stg.expansion_lab_dependency_observation o ON o.stage_record_id=d.stage_record_id
 JOIN ctl.expansion_lab_dependency_capture c ON c.execution_id=o.execution_id AND c.state='COMPLETE'
 UNION ALL
 SELECT g.run_id,CONVERT(VARCHAR(8),CASE r.entity_name WHEN N'manifestos' THEN 'MAN' WHEN N'coletas' THEN 'COL' ELSE 'REL_FRT' END),
 r.source_key,r.execution_id,c.business_date,CONVERT(BIT,1)
 FROM ctl.analytic_lab_source_group g JOIN core.relational_lab_root r ON r.run_id=g.relational_run
 JOIN ctl.relational_lab_capture c ON c.execution_id=r.execution_id
 UNION ALL
 SELECT DISTINCT g.run_id,CONVERT(VARCHAR(8),i.vertical),CONVERT(NVARCHAR(256),CONCAT(i.root_type,':',i.root_key)) COLLATE Latin1_General_100_BIN2,
 i.execution_id,i.partition_start,CONVERT(BIT,1)
 FROM ctl.analytic_lab_source_group g JOIN core.expansion_lab_current_input i ON i.run_id=g.expansion_run
 WHERE i.unresolved_conflict=0
 UNION ALL
 SELECT a.run_id,a.entity,u.source_key,u.last_seen_execution_id,CONVERT(DATE,u.last_seen_at_utc),u.active
 FROM ctl.analytic_lab_execution_source a JOIN core.usuario u ON u.last_seen_execution_id=a.execution_id
 WHERE a.entity='USUARIO'
 UNION ALL
 SELECT t.run_id,CONVERT(VARCHAR(8),'RAS'),CONVERT(NVARCHAR(256),t.trip_key),o.capture_id,
 c.start_date,t.active FROM core.analytic_raster_trip t JOIN stg.analytic_raster_trip o ON o.observation_id=t.observation_id
 JOIN ctl.analytic_raster_capture c ON c.capture_id=o.capture_id
 UNION ALL
 SELECT c.run_id,CONVERT(VARCHAR(8),'COT'),c.source_key,c.last_observation,c.business_date,CONVERT(BIT,1)
 FROM core.analytic_quote_current c JOIN ctl.analytic_quote_capture capture ON capture.execution_id=c.last_observation AND capture.state='PUBLISHED';
GO
