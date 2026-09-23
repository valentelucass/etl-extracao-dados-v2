-- ADR0049. Explicit synthetic laboratory only; no grants, permits or operational promotion.
SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;
SET XACT_ABORT ON;
GO
ALTER TABLE ctl.execution_audit DROP CONSTRAINT CK_ctl_execution_audit_template;
ALTER TABLE ctl.execution_audit ADD CONSTRAINT CK_ctl_execution_audit_template
 CHECK(template_id IN(6908,6389,6399,6906,8656,8636,4924,10633,6392));
GO
CREATE TABLE ctl.expansion_lab_run (
 run_id UNIQUEIDENTIFIER NOT NULL PRIMARY KEY,
 source_instance NVARCHAR(128) COLLATE Latin1_General_100_BIN2 NOT NULL,
 tenant_scope NVARCHAR(128) COLLATE Latin1_General_100_BIN2 NOT NULL,
 contract_version VARCHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL,
 window_start DATE NOT NULL, window_end_exclusive DATE NOT NULL, business_date DATE NOT NULL,
 page_size INT NOT NULL, maximum_pages INT NOT NULL, maximum_rows INT NOT NULL,
 fiscal_policy VARCHAR(32) NOT NULL, created_at DATETIME2(7) NOT NULL,
 CONSTRAINT CK_expansion_run_scope CHECK(source_instance=N'SYNTHETIC_EXPANSION_LAB'
 AND tenant_scope=N'SYNTHETIC_TENANT' AND contract_version='expansion-synthetic-v1'),
 CONSTRAINT CK_expansion_run_bounds CHECK(window_start<window_end_exclusive AND DATEDIFF(day,window_start,window_end_exclusive)<=31
 AND page_size BETWEEN 1 AND 100 AND maximum_pages BETWEEN 1 AND 10000 AND maximum_rows BETWEEN 1 AND 100000),
 CONSTRAINT CK_expansion_run_fiscal CHECK(fiscal_policy IN('UNRESOLVED','SYNTHETIC_CTE','SYNTHETIC_NFSE'))
);
GO
CREATE TABLE ctl.expansion_lab_capture (
 execution_id UNIQUEIDENTIFIER NOT NULL PRIMARY KEY, run_id UNIQUEIDENTIFIER NOT NULL,
 template_id INT NOT NULL, vertical CHAR(3) NOT NULL, contract_fingerprint CHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL,
 mode VARCHAR(16) NOT NULL, partition_start DATE NOT NULL, partition_end_exclusive DATE NOT NULL,
 replay_of UNIQUEIDENTIFIER NULL, state VARCHAR(16) NOT NULL, physical_rows BIGINT NULL,
 created_at DATETIME2(7) NOT NULL, sealed_at DATETIME2(7) NULL,
 CONSTRAINT FK_expansion_capture_run FOREIGN KEY(run_id) REFERENCES ctl.expansion_lab_run(run_id),
 CONSTRAINT UQ_expansion_capture_scope UNIQUE(execution_id,run_id,vertical),
 CONSTRAINT CK_expansion_capture_template CHECK((template_id=8636 AND vertical='CAP') OR (template_id=4924 AND vertical='FAT')
 OR (template_id=10633 AND vertical='INV') OR(template_id=6392 AND vertical='SIN')),
 CONSTRAINT CK_expansion_capture_state CHECK(state IN('CAPTURING','COMPLETE','INCOMPLETE','CANCELLED','FAILED')),
 CONSTRAINT CK_expansion_capture_mode CHECK(mode IN('BOOTSTRAP','INCREMENTAL','BACKFILL','REPLAY')
 AND ((mode='REPLAY' AND replay_of IS NOT NULL) OR (mode<>'REPLAY' AND replay_of IS NULL))),
 CONSTRAINT CK_expansion_capture_seal CHECK((state='COMPLETE' AND sealed_at IS NOT NULL AND physical_rows>=0) OR (state<>'COMPLETE' AND sealed_at IS NULL))
);
CREATE INDEX IX_expansion_capture_partition ON ctl.expansion_lab_capture(run_id,vertical,partition_start,state);
GO
CREATE TABLE stg.expansion_lab_observation (
 observation_id BIGINT IDENTITY NOT NULL PRIMARY KEY, execution_id UNIQUEIDENTIFIER NOT NULL,
 run_id UNIQUEIDENTIFIER NOT NULL, vertical CHAR(3) NOT NULL, occurrence BIGINT NOT NULL,
 root_type VARCHAR(8) NULL, root_key NVARCHAR(64) COLLATE Latin1_General_100_BIN2 NULL,
 part_type VARCHAR(8) NULL, part_key NVARCHAR(64) COLLATE Latin1_General_100_BIN2 NULL,
 component_type VARCHAR(8) NULL, component_key NVARCHAR(64) COLLATE Latin1_General_100_BIN2 NULL,
 revision INT NULL, binding_evidence VARCHAR(80) NULL, currency CHAR(3) NULL, unit VARCHAR(16) NULL,
 additive_allocation BIT NULL, active BIT NULL, reactivation BIT NULL,
 fresh_second BIGINT NULL, fresh_nano INT NULL, fresh_inclusive TINYINT NULL, valid BIT NOT NULL, proof_attached BIT NOT NULL,
 root_amount DECIMAL(28,8) NULL, part_amount DECIMAL(28,8) NULL, allocation_amount DECIMAL(28,8) NULL,
 business_date DATE NULL, comparison_bytes VARBINARY(MAX) NOT NULL, disposition VARCHAR(40) NOT NULL DEFAULT 'RAW',
 CONSTRAINT FK_expansion_observation_capture FOREIGN KEY(execution_id,run_id,vertical)
 REFERENCES ctl.expansion_lab_capture(execution_id,run_id,vertical),
 CONSTRAINT UQ_expansion_observation_address UNIQUE(execution_id,occurrence),
 CONSTRAINT CK_expansion_observation_bounds CHECK(occurrence BETWEEN 1 AND 100000 AND DATALENGTH(comparison_bytes)<=131072
 AND (fresh_nano IS NULL OR fresh_nano BETWEEN 0 AND 999999999) AND (fresh_inclusive IS NULL OR fresh_inclusive IN(0,1))),
 CONSTRAINT CK_expansion_binding CHECK((root_key IS NULL AND part_key IS NULL AND component_key IS NULL AND revision IS NULL) OR
 (root_key IS NOT NULL AND part_key IS NOT NULL AND component_key IS NOT NULL AND revision BETWEEN 1 AND 1000
 AND root_type IN('INTEGER','STRING') AND part_type IN('INTEGER','STRING') AND component_type IN('INTEGER','STRING')
 AND DATALENGTH(root_key)=DATALENGTH(LTRIM(RTRIM(root_key))) AND LEN(root_key)>0
 AND DATALENGTH(part_key)=DATALENGTH(LTRIM(RTRIM(part_key))) AND LEN(part_key)>0
 AND DATALENGTH(component_key)=DATALENGTH(LTRIM(RTRIM(component_key))) AND LEN(component_key)>0
 AND binding_evidence LIKE 'synthetic-%' AND currency NOT LIKE '%[^A-Z]%' AND unit='MAJOR'))
);
CREATE INDEX IX_expansion_observation_dedupe ON stg.expansion_lab_observation(run_id,vertical,root_type,root_key,part_type,part_key,component_type,component_key,fresh_second DESC,fresh_nano DESC,fresh_inclusive DESC,revision DESC)
 INCLUDE(execution_id,valid,active,root_amount,part_amount,allocation_amount,business_date);
GO
CREATE TABLE stg.expansion_lab_cap (
 execution_id UNIQUEIDENTIFIER NOT NULL, occurrence BIGINT NOT NULL,
 [ant_aln_name_p] VARCHAR(6) NOT NULL, [ant_aln_name_w] VARCHAR(8) NOT NULL, [ant_aln_name_raw] NVARCHAR(4000) NULL, [ant_aln_name] NVARCHAR(1024) NULL,
 [ant_ces_acr_name_p] VARCHAR(6) NOT NULL, [ant_ces_acr_name_w] VARCHAR(8) NOT NULL, [ant_ces_acr_name_raw] NVARCHAR(4000) NULL, [ant_ces_acr_name] NVARCHAR(1024) NULL,
 [ant_ces_value_p] VARCHAR(6) NOT NULL, [ant_ces_value_w] VARCHAR(8) NOT NULL, [ant_ces_value_raw] NVARCHAR(4000) NULL, [ant_ces_value] DECIMAL(28,8) NULL,
 [ant_crn_psn_nickname_p] VARCHAR(6) NOT NULL, [ant_crn_psn_nickname_w] VARCHAR(8) NOT NULL, [ant_crn_psn_nickname_raw] NVARCHAR(4000) NULL, [ant_crn_psn_nickname] NVARCHAR(1024) NULL,
 [ant_ils_atn_liquidation_date_p] VARCHAR(6) NOT NULL, [ant_ils_atn_liquidation_date_w] VARCHAR(8) NOT NULL, [ant_ils_atn_liquidation_date_raw] NVARCHAR(4000) NULL, [ant_ils_atn_liquidation_date] DATE NULL,
 [ant_ils_atn_reconciled_p] VARCHAR(6) NOT NULL, [ant_ils_atn_reconciled_w] VARCHAR(8) NOT NULL, [ant_ils_atn_reconciled_raw] NVARCHAR(4000) NULL, [ant_ils_atn_reconciled] BIT NULL,
 [ant_ils_atn_transaction_date_p] VARCHAR(6) NOT NULL, [ant_ils_atn_transaction_date_w] VARCHAR(8) NOT NULL, [ant_ils_atn_transaction_date_raw] NVARCHAR(4000) NULL, [ant_ils_atn_transaction_date] DATE NULL,
 [ant_ils_comments_p] VARCHAR(6) NOT NULL, [ant_ils_comments_w] VARCHAR(8) NOT NULL, [ant_ils_comments_raw] NVARCHAR(4000) NULL, [ant_ils_comments] NVARCHAR(1024) NULL,
 [ant_ils_expense_description_p] VARCHAR(6) NOT NULL, [ant_ils_expense_description_w] VARCHAR(8) NOT NULL, [ant_ils_expense_description_raw] NVARCHAR(4000) NULL, [ant_ils_expense_description] NVARCHAR(1024) NULL,
 [ant_ils_pas_ant_classification_p] VARCHAR(6) NOT NULL, [ant_ils_pas_ant_classification_w] VARCHAR(8) NOT NULL, [ant_ils_pas_ant_classification_raw] NVARCHAR(4000) NULL, [ant_ils_pas_ant_classification] NVARCHAR(1024) NULL,
 [ant_ils_pas_ant_name_p] VARCHAR(6) NOT NULL, [ant_ils_pas_ant_name_w] VARCHAR(8) NOT NULL, [ant_ils_pas_ant_name_raw] NVARCHAR(4000) NULL, [ant_ils_pas_ant_name] NVARCHAR(1024) NULL,
 [ant_ils_pas_value_p] VARCHAR(6) NOT NULL, [ant_ils_pas_value_w] VARCHAR(8) NOT NULL, [ant_ils_pas_value_raw] NVARCHAR(4000) NULL, [ant_ils_pas_value] DECIMAL(28,8) NULL,
 [ant_ils_sequence_code_p] VARCHAR(6) NOT NULL, [ant_ils_sequence_code_w] VARCHAR(8) NOT NULL, [ant_ils_sequence_code_raw] NVARCHAR(4000) NULL, [ant_ils_sequence_code] VARCHAR(40) NULL,
 [ant_rir_name_p] VARCHAR(6) NOT NULL, [ant_rir_name_w] VARCHAR(8) NOT NULL, [ant_rir_name_raw] NVARCHAR(4000) NULL, [ant_rir_name] NVARCHAR(1024) NULL,
 [ant_uer_name_p] VARCHAR(6) NOT NULL, [ant_uer_name_w] VARCHAR(8) NOT NULL, [ant_uer_name_raw] NVARCHAR(4000) NULL, [ant_uer_name] NVARCHAR(1024) NULL,
 [comments_p] VARCHAR(6) NOT NULL, [comments_w] VARCHAR(8) NOT NULL, [comments_raw] NVARCHAR(4000) NULL, [comments] NVARCHAR(1024) NULL,
 [competence_month_p] VARCHAR(6) NOT NULL, [competence_month_w] VARCHAR(8) NOT NULL, [competence_month_raw] NVARCHAR(4000) NULL, [competence_month] VARCHAR(40) NULL,
 [competence_year_p] VARCHAR(6) NOT NULL, [competence_year_w] VARCHAR(8) NOT NULL, [competence_year_raw] NVARCHAR(4000) NULL, [competence_year] VARCHAR(40) NULL,
 [created_at_p] VARCHAR(6) NOT NULL, [created_at_w] VARCHAR(8) NOT NULL, [created_at_raw] NVARCHAR(4000) NULL, [created_at] BIGINT NULL,
 [created_at_nano] INT NULL,
 [discount_value_p] VARCHAR(6) NOT NULL, [discount_value_w] VARCHAR(8) NOT NULL, [discount_value_raw] NVARCHAR(4000) NULL, [discount_value] DECIMAL(28,8) NULL,
 [document_p] VARCHAR(6) NOT NULL, [document_w] VARCHAR(8) NOT NULL, [document_raw] NVARCHAR(4000) NULL, [document] NVARCHAR(1024) NULL,
 [interest_value_p] VARCHAR(6) NOT NULL, [interest_value_w] VARCHAR(8) NOT NULL, [interest_value_raw] NVARCHAR(4000) NULL, [interest_value] DECIMAL(28,8) NULL,
 [issue_date_p] VARCHAR(6) NOT NULL, [issue_date_w] VARCHAR(8) NOT NULL, [issue_date_raw] NVARCHAR(4000) NULL, [issue_date] DATE NULL,
 [paid_p] VARCHAR(6) NOT NULL, [paid_w] VARCHAR(8) NOT NULL, [paid_raw] NVARCHAR(4000) NULL, [paid] BIT NULL,
 [paid_value_p] VARCHAR(6) NOT NULL, [paid_value_w] VARCHAR(8) NOT NULL, [paid_value_raw] NVARCHAR(4000) NULL, [paid_value] DECIMAL(28,8) NULL,
 [type_p] VARCHAR(6) NOT NULL, [type_w] VARCHAR(8) NOT NULL, [type_raw] NVARCHAR(4000) NULL, [type] NVARCHAR(1024) NULL,
 [value_p] VARCHAR(6) NOT NULL, [value_w] VARCHAR(8) NOT NULL, [value_raw] NVARCHAR(4000) NULL, [value] DECIMAL(28,8) NULL,
 [value_to_pay_p] VARCHAR(6) NOT NULL, [value_to_pay_w] VARCHAR(8) NOT NULL, [value_to_pay_raw] NVARCHAR(4000) NULL, [value_to_pay] DECIMAL(28,8) NULL,
 CONSTRAINT PK_expansion_CAP PRIMARY KEY(execution_id,occurrence),
 CONSTRAINT FK_expansion_CAP FOREIGN KEY(execution_id,occurrence) REFERENCES stg.expansion_lab_observation(execution_id,occurrence)
);
GO
CREATE TABLE stg.expansion_lab_fat (
 execution_id UNIQUEIDENTIFIER NOT NULL, occurrence BIGINT NOT NULL,
 [comments_p] VARCHAR(6) NOT NULL, [comments_w] VARCHAR(8) NOT NULL, [comments_raw] NVARCHAR(4000) NULL, [comments] NVARCHAR(1024) NULL,
 [corporation_sequence_number_p] VARCHAR(6) NOT NULL, [corporation_sequence_number_w] VARCHAR(8) NOT NULL, [corporation_sequence_number_raw] NVARCHAR(4000) NULL, [corporation_sequence_number] VARCHAR(40) NULL,
 [courtesy_p] VARCHAR(6) NOT NULL, [courtesy_w] VARCHAR(8) NOT NULL, [courtesy_raw] NVARCHAR(4000) NULL, [courtesy] BIT NULL,
 [emission_type_p] VARCHAR(6) NOT NULL, [emission_type_w] VARCHAR(8) NOT NULL, [emission_type_raw] NVARCHAR(4000) NULL, [emission_type] NVARCHAR(1024) NULL,
 [finished_at_p] VARCHAR(6) NOT NULL, [finished_at_w] VARCHAR(8) NOT NULL, [finished_at_raw] NVARCHAR(4000) NULL, [finished_at] BIGINT NULL,
 [finished_at_nano] INT NULL,
 [fit_ant_ant_name_p] VARCHAR(6) NOT NULL, [fit_ant_ant_name_w] VARCHAR(8) NOT NULL, [fit_ant_ant_name_raw] NVARCHAR(4000) NULL, [fit_ant_ant_name] NVARCHAR(1024) NULL,
 [fit_ant_discount_value_p] VARCHAR(6) NOT NULL, [fit_ant_discount_value_w] VARCHAR(8) NOT NULL, [fit_ant_discount_value_raw] NVARCHAR(4000) NULL, [fit_ant_discount_value] DECIMAL(28,8) NULL,
 [fit_ant_document_p] VARCHAR(6) NOT NULL, [fit_ant_document_w] VARCHAR(8) NOT NULL, [fit_ant_document_raw] NVARCHAR(4000) NULL, [fit_ant_document] NVARCHAR(1024) NULL,
 [fit_ant_ils_atn_transaction_date_p] VARCHAR(6) NOT NULL, [fit_ant_ils_atn_transaction_date_w] VARCHAR(8) NOT NULL, [fit_ant_ils_atn_transaction_date_raw] NVARCHAR(4000) NULL, [fit_ant_ils_atn_transaction_date] DATE NULL,
 [fit_ant_ils_due_date_p] VARCHAR(6) NOT NULL, [fit_ant_ils_due_date_w] VARCHAR(8) NOT NULL, [fit_ant_ils_due_date_raw] NVARCHAR(4000) NULL, [fit_ant_ils_due_date] DATE NULL,
 [fit_ant_ils_original_due_date_p] VARCHAR(6) NOT NULL, [fit_ant_ils_original_due_date_w] VARCHAR(8) NOT NULL, [fit_ant_ils_original_due_date_raw] NVARCHAR(4000) NULL, [fit_ant_ils_original_due_date] DATE NULL,
 [fit_ant_interest_value_p] VARCHAR(6) NOT NULL, [fit_ant_interest_value_w] VARCHAR(8) NOT NULL, [fit_ant_interest_value_raw] NVARCHAR(4000) NULL, [fit_ant_interest_value] DECIMAL(28,8) NULL,
 [fit_ant_issue_date_p] VARCHAR(6) NOT NULL, [fit_ant_issue_date_w] VARCHAR(8) NOT NULL, [fit_ant_issue_date_raw] NVARCHAR(4000) NULL, [fit_ant_issue_date] DATE NULL,
 [fit_ant_tat_account_number_p] VARCHAR(6) NOT NULL, [fit_ant_tat_account_number_w] VARCHAR(8) NOT NULL, [fit_ant_tat_account_number_raw] NVARCHAR(4000) NULL, [fit_ant_tat_account_number] NVARCHAR(1024) NULL,
 [fit_ant_tat_agency_number_p] VARCHAR(6) NOT NULL, [fit_ant_tat_agency_number_w] VARCHAR(8) NOT NULL, [fit_ant_tat_agency_number_raw] NVARCHAR(4000) NULL, [fit_ant_tat_agency_number] NVARCHAR(1024) NULL,
 [fit_ant_tat_bnk_name_p] VARCHAR(6) NOT NULL, [fit_ant_tat_bnk_name_w] VARCHAR(8) NOT NULL, [fit_ant_tat_bnk_name_raw] NVARCHAR(4000) NULL, [fit_ant_tat_bnk_name] NVARCHAR(1024) NULL,
 [fit_ant_tat_bro_description_p] VARCHAR(6) NOT NULL, [fit_ant_tat_bro_description_w] VARCHAR(8) NOT NULL, [fit_ant_tat_bro_description_raw] NVARCHAR(4000) NULL, [fit_ant_tat_bro_description] NVARCHAR(1024) NULL,
 [fit_ant_tat_custom_instruction_p] VARCHAR(6) NOT NULL, [fit_ant_tat_custom_instruction_w] VARCHAR(8) NOT NULL, [fit_ant_tat_custom_instruction_raw] NVARCHAR(4000) NULL, [fit_ant_tat_custom_instruction] NVARCHAR(1024) NULL,
 [fit_ant_value_p] VARCHAR(6) NOT NULL, [fit_ant_value_w] VARCHAR(8) NOT NULL, [fit_ant_value_raw] NVARCHAR(4000) NULL, [fit_ant_value] DECIMAL(28,8) NULL,
 [fit_crn_psn_nickname_p] VARCHAR(6) NOT NULL, [fit_crn_psn_nickname_w] VARCHAR(8) NOT NULL, [fit_crn_psn_nickname_raw] NVARCHAR(4000) NULL, [fit_crn_psn_nickname] NVARCHAR(1024) NULL,
 [fit_d_t_created_at_p] VARCHAR(6) NOT NULL, [fit_d_t_created_at_w] VARCHAR(8) NOT NULL, [fit_d_t_created_at_raw] NVARCHAR(4000) NULL, [fit_d_t_created_at] BIGINT NULL,
 [fit_d_t_created_at_nano] INT NULL,
 [fit_diy_sae_name_p] VARCHAR(6) NOT NULL, [fit_diy_sae_name_w] VARCHAR(8) NOT NULL, [fit_diy_sae_name_raw] NVARCHAR(4000) NULL, [fit_diy_sae_name] NVARCHAR(1024) NULL,
 [fit_dyn_drt_nickname_p] VARCHAR(6) NOT NULL, [fit_dyn_drt_nickname_w] VARCHAR(8) NOT NULL, [fit_dyn_drt_nickname_raw] NVARCHAR(4000) NULL, [fit_dyn_drt_nickname] NVARCHAR(1024) NULL,
 [fit_fhe_cte_issued_at_p] VARCHAR(6) NOT NULL, [fit_fhe_cte_issued_at_w] VARCHAR(8) NOT NULL, [fit_fhe_cte_issued_at_raw] NVARCHAR(4000) NULL, [fit_fhe_cte_issued_at] BIGINT NULL,
 [fit_fhe_cte_issued_at_nano] INT NULL,
 [fit_fhe_cte_key_p] VARCHAR(6) NOT NULL, [fit_fhe_cte_key_w] VARCHAR(8) NOT NULL, [fit_fhe_cte_key_raw] NVARCHAR(4000) NULL, [fit_fhe_cte_key] NVARCHAR(1024) NULL,
 [fit_fhe_cte_number_p] VARCHAR(6) NOT NULL, [fit_fhe_cte_number_w] VARCHAR(8) NOT NULL, [fit_fhe_cte_number_raw] NVARCHAR(4000) NULL, [fit_fhe_cte_number] VARCHAR(40) NULL,
 [fit_fhe_cte_status_p] VARCHAR(6) NOT NULL, [fit_fhe_cte_status_w] VARCHAR(8) NOT NULL, [fit_fhe_cte_status_raw] NVARCHAR(4000) NULL, [fit_fhe_cte_status] NVARCHAR(1024) NULL,
 [fit_fhe_cte_status_result_p] VARCHAR(6) NOT NULL, [fit_fhe_cte_status_result_w] VARCHAR(8) NOT NULL, [fit_fhe_cte_status_result_raw] NVARCHAR(4000) NULL, [fit_fhe_cte_status_result] NVARCHAR(1024) NULL,
 [fit_fsn_name_p] VARCHAR(6) NOT NULL, [fit_fsn_name_w] VARCHAR(8) NOT NULL, [fit_fsn_name_raw] NVARCHAR(4000) NULL, [fit_fsn_name] NVARCHAR(1024) NULL,
 [fit_fte_foe_ore_description_p] VARCHAR(6) NOT NULL, [fit_fte_foe_ore_description_w] VARCHAR(8) NOT NULL, [fit_fte_foe_ore_description_raw] NVARCHAR(4000) NULL, [fit_fte_foe_ore_description] NVARCHAR(1024) NULL,
 [fit_fte_has_delivery_receipt_p] VARCHAR(6) NOT NULL, [fit_fte_has_delivery_receipt_w] VARCHAR(8) NOT NULL, [fit_fte_has_delivery_receipt_raw] NVARCHAR(4000) NULL, [fit_fte_has_delivery_receipt] BIT NULL,
 [fit_fte_invoices_order_number_p] VARCHAR(6) NOT NULL, [fit_fte_invoices_order_number_w] VARCHAR(8) NOT NULL, [fit_fte_invoices_order_number_raw] NVARCHAR(4000) NULL, [fit_fte_invoices_order_number] SMALLINT NULL,
 [fit_nse_number_p] VARCHAR(6) NOT NULL, [fit_nse_number_w] VARCHAR(8) NOT NULL, [fit_nse_number_raw] NVARCHAR(4000) NULL, [fit_nse_number] VARCHAR(40) NULL,
 [fit_pyr_cor_billing_cycle_p] VARCHAR(6) NOT NULL, [fit_pyr_cor_billing_cycle_w] VARCHAR(8) NOT NULL, [fit_pyr_cor_billing_cycle_raw] NVARCHAR(4000) NULL, [fit_pyr_cor_billing_cycle] NVARCHAR(1024) NULL,
 [fit_pyr_cor_billing_due_in_days_p] VARCHAR(6) NOT NULL, [fit_pyr_cor_billing_due_in_days_w] VARCHAR(8) NOT NULL, [fit_pyr_cor_billing_due_in_days_raw] NVARCHAR(4000) NULL, [fit_pyr_cor_billing_due_in_days] VARCHAR(40) NULL,
 [fit_pyr_document_p] VARCHAR(6) NOT NULL, [fit_pyr_document_w] VARCHAR(8) NOT NULL, [fit_pyr_document_raw] NVARCHAR(4000) NULL, [fit_pyr_document] NVARCHAR(1024) NULL,
 [fit_pyr_name_p] VARCHAR(6) NOT NULL, [fit_pyr_name_w] VARCHAR(8) NOT NULL, [fit_pyr_name_raw] NVARCHAR(4000) NULL, [fit_pyr_name] NVARCHAR(1024) NULL,
 [fit_rpt_document_p] VARCHAR(6) NOT NULL, [fit_rpt_document_w] VARCHAR(8) NOT NULL, [fit_rpt_document_raw] NVARCHAR(4000) NULL, [fit_rpt_document] NVARCHAR(1024) NULL,
 [fit_rpt_name_p] VARCHAR(6) NOT NULL, [fit_rpt_name_w] VARCHAR(8) NOT NULL, [fit_rpt_name_raw] NVARCHAR(4000) NULL, [fit_rpt_name] NVARCHAR(1024) NULL,
 [fit_sdr_document_p] VARCHAR(6) NOT NULL, [fit_sdr_document_w] VARCHAR(8) NOT NULL, [fit_sdr_document_raw] NVARCHAR(4000) NULL, [fit_sdr_document] NVARCHAR(1024) NULL,
 [fit_sdr_name_p] VARCHAR(6) NOT NULL, [fit_sdr_name_w] VARCHAR(8) NOT NULL, [fit_sdr_name_raw] NVARCHAR(4000) NULL, [fit_sdr_name] NVARCHAR(1024) NULL,
 [fit_sps_slr_psn_name_p] VARCHAR(6) NOT NULL, [fit_sps_slr_psn_name_w] VARCHAR(8) NOT NULL, [fit_sps_slr_psn_name_raw] NVARCHAR(4000) NULL, [fit_sps_slr_psn_name] NVARCHAR(1024) NULL,
 [id_p] VARCHAR(6) NOT NULL, [id_w] VARCHAR(8) NOT NULL, [id_raw] NVARCHAR(4000) NULL, [id] VARCHAR(40) NULL,
 [invoices_mapping_p] VARCHAR(6) NOT NULL, [invoices_mapping_w] VARCHAR(8) NOT NULL, [invoices_mapping_raw] NVARCHAR(4000) NULL, [invoices_mapping] SMALLINT NULL,
 [nfse_number_p] VARCHAR(6) NOT NULL, [nfse_number_w] VARCHAR(8) NOT NULL, [nfse_number_raw] NVARCHAR(4000) NULL, [nfse_number] NVARCHAR(128) NULL,
 [payment_type_p] VARCHAR(6) NOT NULL, [payment_type_w] VARCHAR(8) NOT NULL, [payment_type_raw] NVARCHAR(4000) NULL, [payment_type] NVARCHAR(1024) NULL,
 [reference_number_p] VARCHAR(6) NOT NULL, [reference_number_w] VARCHAR(8) NOT NULL, [reference_number_raw] NVARCHAR(4000) NULL, [reference_number] NVARCHAR(1024) NULL,
 [service_at_p] VARCHAR(6) NOT NULL, [service_at_w] VARCHAR(8) NOT NULL, [service_at_raw] NVARCHAR(4000) NULL, [service_at] BIGINT NULL,
 [service_at_nano] INT NULL,
 [service_type_p] VARCHAR(6) NOT NULL, [service_type_w] VARCHAR(8) NOT NULL, [service_type_raw] NVARCHAR(4000) NULL, [service_type] NVARCHAR(1024) NULL,
 [status_p] VARCHAR(6) NOT NULL, [status_w] VARCHAR(8) NOT NULL, [status_raw] NVARCHAR(4000) NULL, [status] NVARCHAR(1024) NULL,
 [third_party_ctes_value_p] VARCHAR(6) NOT NULL, [third_party_ctes_value_w] VARCHAR(8) NOT NULL, [third_party_ctes_value_raw] NVARCHAR(4000) NULL, [third_party_ctes_value] DECIMAL(28,8) NULL,
 [total_p] VARCHAR(6) NOT NULL, [total_w] VARCHAR(8) NOT NULL, [total_raw] NVARCHAR(4000) NULL, [total] DECIMAL(28,8) NULL,
 [type_p] VARCHAR(6) NOT NULL, [type_w] VARCHAR(8) NOT NULL, [type_raw] NVARCHAR(4000) NULL, [type] NVARCHAR(1024) NULL,
 CONSTRAINT PK_expansion_FAT PRIMARY KEY(execution_id,occurrence),
 CONSTRAINT FK_expansion_FAT FOREIGN KEY(execution_id,occurrence) REFERENCES stg.expansion_lab_observation(execution_id,occurrence)
);
GO
CREATE TABLE stg.expansion_lab_inv (
 execution_id UNIQUEIDENTIFIER NOT NULL, occurrence BIGINT NOT NULL,
 [cnr_c_s_fit_corporation_sequence_number_p] VARCHAR(6) NOT NULL, [cnr_c_s_fit_corporation_sequence_number_w] VARCHAR(8) NOT NULL, [cnr_c_s_fit_corporation_sequence_number_raw] NVARCHAR(4000) NULL, [cnr_c_s_fit_corporation_sequence_number] VARCHAR(40) NULL,
 [cnr_c_s_fit_dpn_delivery_prediction_at_p] VARCHAR(6) NOT NULL, [cnr_c_s_fit_dpn_delivery_prediction_at_w] VARCHAR(8) NOT NULL, [cnr_c_s_fit_dpn_delivery_prediction_at_raw] NVARCHAR(4000) NULL, [cnr_c_s_fit_dpn_delivery_prediction_at] BIGINT NULL,
 [cnr_c_s_fit_dpn_delivery_prediction_at_nano] INT NULL,
 [cnr_c_s_fit_dpn_performance_finished_at_p] VARCHAR(6) NOT NULL, [cnr_c_s_fit_dpn_performance_finished_at_w] VARCHAR(8) NOT NULL, [cnr_c_s_fit_dpn_performance_finished_at_raw] NVARCHAR(4000) NULL, [cnr_c_s_fit_dpn_performance_finished_at] BIGINT NULL,
 [cnr_c_s_fit_dpn_performance_finished_at_nano] INT NULL,
 [cnr_c_s_fit_dyn_drt_nickname_p] VARCHAR(6) NOT NULL, [cnr_c_s_fit_dyn_drt_nickname_w] VARCHAR(8) NOT NULL, [cnr_c_s_fit_dyn_drt_nickname_raw] NVARCHAR(4000) NULL, [cnr_c_s_fit_dyn_drt_nickname] NVARCHAR(1024) NULL,
 [cnr_c_s_fit_dyn_name_p] VARCHAR(6) NOT NULL, [cnr_c_s_fit_dyn_name_w] VARCHAR(8) NOT NULL, [cnr_c_s_fit_dyn_name_raw] NVARCHAR(4000) NULL, [cnr_c_s_fit_dyn_name] NVARCHAR(1024) NULL,
 [cnr_c_s_fit_fte_lce_occurrence_at_p] VARCHAR(6) NOT NULL, [cnr_c_s_fit_fte_lce_occurrence_at_w] VARCHAR(8) NOT NULL, [cnr_c_s_fit_fte_lce_occurrence_at_raw] NVARCHAR(4000) NULL, [cnr_c_s_fit_fte_lce_occurrence_at] BIGINT NULL,
 [cnr_c_s_fit_fte_lce_occurrence_at_nano] INT NULL,
 [cnr_c_s_fit_fte_lce_ore_description_p] VARCHAR(6) NOT NULL, [cnr_c_s_fit_fte_lce_ore_description_w] VARCHAR(8) NOT NULL, [cnr_c_s_fit_fte_lce_ore_description_raw] NVARCHAR(4000) NULL, [cnr_c_s_fit_fte_lce_ore_description] NVARCHAR(1024) NULL,
 [cnr_c_s_fit_invoices_mapping_p] VARCHAR(6) NOT NULL, [cnr_c_s_fit_invoices_mapping_w] VARCHAR(8) NOT NULL, [cnr_c_s_fit_invoices_mapping_raw] NVARCHAR(4000) NULL, [cnr_c_s_fit_invoices_mapping] SMALLINT NULL,
 [cnr_c_s_fit_invoices_value_p] VARCHAR(6) NOT NULL, [cnr_c_s_fit_invoices_value_w] VARCHAR(8) NOT NULL, [cnr_c_s_fit_invoices_value_raw] NVARCHAR(4000) NULL, [cnr_c_s_fit_invoices_value] DECIMAL(28,8) NULL,
 [cnr_c_s_fit_invoices_volumes_p] VARCHAR(6) NOT NULL, [cnr_c_s_fit_invoices_volumes_w] VARCHAR(8) NOT NULL, [cnr_c_s_fit_invoices_volumes_raw] NVARCHAR(4000) NULL, [cnr_c_s_fit_invoices_volumes] VARCHAR(40) NULL,
 [cnr_c_s_fit_pyr_nickname_p] VARCHAR(6) NOT NULL, [cnr_c_s_fit_pyr_nickname_w] VARCHAR(8) NOT NULL, [cnr_c_s_fit_pyr_nickname_raw] NVARCHAR(4000) NULL, [cnr_c_s_fit_pyr_nickname] NVARCHAR(1024) NULL,
 [cnr_c_s_fit_real_weight_p] VARCHAR(6) NOT NULL, [cnr_c_s_fit_real_weight_w] VARCHAR(8) NOT NULL, [cnr_c_s_fit_real_weight_raw] NVARCHAR(4000) NULL, [cnr_c_s_fit_real_weight] DECIMAL(28,8) NULL,
 [cnr_c_s_fit_rpt_ads_cty_name_p] VARCHAR(6) NOT NULL, [cnr_c_s_fit_rpt_ads_cty_name_w] VARCHAR(8) NOT NULL, [cnr_c_s_fit_rpt_ads_cty_name_raw] NVARCHAR(4000) NULL, [cnr_c_s_fit_rpt_ads_cty_name] NVARCHAR(1024) NULL,
 [cnr_c_s_fit_rpt_nickname_p] VARCHAR(6) NOT NULL, [cnr_c_s_fit_rpt_nickname_w] VARCHAR(8) NOT NULL, [cnr_c_s_fit_rpt_nickname_raw] NVARCHAR(4000) NULL, [cnr_c_s_fit_rpt_nickname] NVARCHAR(1024) NULL,
 [cnr_c_s_fit_sdr_ads_cty_name_p] VARCHAR(6) NOT NULL, [cnr_c_s_fit_sdr_ads_cty_name_w] VARCHAR(8) NOT NULL, [cnr_c_s_fit_sdr_ads_cty_name_raw] NVARCHAR(4000) NULL, [cnr_c_s_fit_sdr_ads_cty_name] NVARCHAR(1024) NULL,
 [cnr_c_s_fit_sdr_nickname_p] VARCHAR(6) NOT NULL, [cnr_c_s_fit_sdr_nickname_w] VARCHAR(8) NOT NULL, [cnr_c_s_fit_sdr_nickname_raw] NVARCHAR(4000) NULL, [cnr_c_s_fit_sdr_nickname] NVARCHAR(1024) NULL,
 [cnr_c_s_fit_taxed_weight_p] VARCHAR(6) NOT NULL, [cnr_c_s_fit_taxed_weight_w] VARCHAR(8) NOT NULL, [cnr_c_s_fit_taxed_weight_raw] NVARCHAR(4000) NULL, [cnr_c_s_fit_taxed_weight] DECIMAL(28,8) NULL,
 [cnr_c_s_fit_total_cubic_volume_p] VARCHAR(6) NOT NULL, [cnr_c_s_fit_total_cubic_volume_w] VARCHAR(8) NOT NULL, [cnr_c_s_fit_total_cubic_volume_raw] NVARCHAR(4000) NULL, [cnr_c_s_fit_total_cubic_volume] DECIMAL(28,8) NULL,
 [cnr_c_s_read_volumes_p] VARCHAR(6) NOT NULL, [cnr_c_s_read_volumes_w] VARCHAR(8) NOT NULL, [cnr_c_s_read_volumes_raw] NVARCHAR(4000) NULL, [cnr_c_s_read_volumes] VARCHAR(40) NULL,
 [cnr_cis_eoe_psn_name_p] VARCHAR(6) NOT NULL, [cnr_cis_eoe_psn_name_w] VARCHAR(8) NOT NULL, [cnr_cis_eoe_psn_name_raw] NVARCHAR(4000) NULL, [cnr_cis_eoe_psn_name] NVARCHAR(1024) NULL,
 [cnr_crn_psn_nickname_p] VARCHAR(6) NOT NULL, [cnr_crn_psn_nickname_w] VARCHAR(8) NOT NULL, [cnr_crn_psn_nickname_raw] NVARCHAR(4000) NULL, [cnr_crn_psn_nickname] NVARCHAR(1024) NULL,
 [finished_at_p] VARCHAR(6) NOT NULL, [finished_at_w] VARCHAR(8) NOT NULL, [finished_at_raw] NVARCHAR(4000) NULL, [finished_at] BIGINT NULL,
 [finished_at_nano] INT NULL,
 [sequence_code_p] VARCHAR(6) NOT NULL, [sequence_code_w] VARCHAR(8) NOT NULL, [sequence_code_raw] NVARCHAR(4000) NULL, [sequence_code] VARCHAR(40) NULL,
 [started_at_p] VARCHAR(6) NOT NULL, [started_at_w] VARCHAR(8) NOT NULL, [started_at_raw] NVARCHAR(4000) NULL, [started_at] BIGINT NULL,
 [started_at_nano] INT NULL,
 [status_p] VARCHAR(6) NOT NULL, [status_w] VARCHAR(8) NOT NULL, [status_raw] NVARCHAR(4000) NULL, [status] NVARCHAR(1024) NULL,
 [type_p] VARCHAR(6) NOT NULL, [type_w] VARCHAR(8) NOT NULL, [type_raw] NVARCHAR(4000) NULL, [type] NVARCHAR(1024) NULL,
 CONSTRAINT PK_expansion_INV PRIMARY KEY(execution_id,occurrence),
 CONSTRAINT FK_expansion_INV FOREIGN KEY(execution_id,occurrence) REFERENCES stg.expansion_lab_observation(execution_id,occurrence)
);
GO
CREATE TABLE stg.expansion_lab_sin (
 execution_id UNIQUEIDENTIFIER NOT NULL, occurrence BIGINT NOT NULL,
 [bo_number_p] VARCHAR(6) NOT NULL, [bo_number_w] VARCHAR(8) NOT NULL, [bo_number_raw] NVARCHAR(4000) NULL, [bo_number] NVARCHAR(1024) NULL,
 [brokers_case_number_p] VARCHAR(6) NOT NULL, [brokers_case_number_w] VARCHAR(8) NOT NULL, [brokers_case_number_raw] NVARCHAR(4000) NULL, [brokers_case_number] NVARCHAR(1024) NULL,
 [customer_communication_contact_name_p] VARCHAR(6) NOT NULL, [customer_communication_contact_name_w] VARCHAR(8) NOT NULL, [customer_communication_contact_name_raw] NVARCHAR(4000) NULL, [customer_communication_contact_name] NVARCHAR(1024) NULL,
 [customer_communication_date_p] VARCHAR(6) NOT NULL, [customer_communication_date_w] VARCHAR(8) NOT NULL, [customer_communication_date_raw] NVARCHAR(4000) NULL, [customer_communication_date] DATE NULL,
 [customer_communication_time_p] VARCHAR(6) NOT NULL, [customer_communication_time_w] VARCHAR(8) NOT NULL, [customer_communication_time_raw] NVARCHAR(4000) NULL, [customer_communication_time] NVARCHAR(1024) NULL,
 [customer_credit_entries_subtotal_p] VARCHAR(6) NOT NULL, [customer_credit_entries_subtotal_w] VARCHAR(8) NOT NULL, [customer_credit_entries_subtotal_raw] NVARCHAR(4000) NULL, [customer_credit_entries_subtotal] DECIMAL(28,8) NULL,
 [customer_debits_subtotal_p] VARCHAR(6) NOT NULL, [customer_debits_subtotal_w] VARCHAR(8) NOT NULL, [customer_debits_subtotal_raw] NVARCHAR(4000) NULL, [customer_debits_subtotal] DECIMAL(28,8) NULL,
 [expected_solution_date_p] VARCHAR(6) NOT NULL, [expected_solution_date_w] VARCHAR(8) NOT NULL, [expected_solution_date_raw] NVARCHAR(4000) NULL, [expected_solution_date] DATE NULL,
 [finished_at_date_p] VARCHAR(6) NOT NULL, [finished_at_date_w] VARCHAR(8) NOT NULL, [finished_at_date_raw] NVARCHAR(4000) NULL, [finished_at_date] DATE NULL,
 [finished_at_time_p] VARCHAR(6) NOT NULL, [finished_at_time_w] VARCHAR(8) NOT NULL, [finished_at_time_raw] NVARCHAR(4000) NULL, [finished_at_time] BIGINT NULL,
 [finished_commentary_p] VARCHAR(6) NOT NULL, [finished_commentary_w] VARCHAR(8) NOT NULL, [finished_commentary_raw] NVARCHAR(4000) NULL, [finished_commentary] NVARCHAR(1024) NULL,
 [icm_crn_psn_nickname_p] VARCHAR(6) NOT NULL, [icm_crn_psn_nickname_w] VARCHAR(8) NOT NULL, [icm_crn_psn_nickname_raw] NVARCHAR(4000) NULL, [icm_crn_psn_nickname] NVARCHAR(1024) NULL,
 [icm_dvr_iil_name_p] VARCHAR(6) NOT NULL, [icm_dvr_iil_name_w] VARCHAR(8) NOT NULL, [icm_dvr_iil_name_raw] NVARCHAR(4000) NULL, [icm_dvr_iil_name] NVARCHAR(1024) NULL,
 [icm_fer_name_p] VARCHAR(6) NOT NULL, [icm_fer_name_w] VARCHAR(8) NOT NULL, [icm_fer_name_raw] NVARCHAR(4000) NULL, [icm_fer_name] NVARCHAR(1024) NULL,
 [icm_fis_fit_corporation_sequence_number_p] VARCHAR(6) NOT NULL, [icm_fis_fit_corporation_sequence_number_w] VARCHAR(8) NOT NULL, [icm_fis_fit_corporation_sequence_number_raw] NVARCHAR(4000) NULL, [icm_fis_fit_corporation_sequence_number] VARCHAR(40) NULL,
 [icm_fis_fit_pyr_nickname_p] VARCHAR(6) NOT NULL, [icm_fis_fit_pyr_nickname_w] VARCHAR(8) NOT NULL, [icm_fis_fit_pyr_nickname_raw] NVARCHAR(4000) NULL, [icm_fis_fit_pyr_nickname] NVARCHAR(1024) NULL,
 [icm_fis_ioe_number_p] VARCHAR(6) NOT NULL, [icm_fis_ioe_number_w] VARCHAR(8) NOT NULL, [icm_fis_ioe_number_raw] NVARCHAR(4000) NULL, [icm_fis_ioe_number] NVARCHAR(128) NULL,
 [icm_ttt_dealing_type_p] VARCHAR(6) NOT NULL, [icm_ttt_dealing_type_w] VARCHAR(8) NOT NULL, [icm_ttt_dealing_type_raw] NVARCHAR(4000) NULL, [icm_ttt_dealing_type] NVARCHAR(1024) NULL,
 [icm_ttt_ore_code_p] VARCHAR(6) NOT NULL, [icm_ttt_ore_code_w] VARCHAR(8) NOT NULL, [icm_ttt_ore_code_raw] NVARCHAR(4000) NULL, [icm_ttt_ore_code] VARCHAR(40) NULL,
 [icm_ttt_ore_description_p] VARCHAR(6) NOT NULL, [icm_ttt_ore_description_w] VARCHAR(8) NOT NULL, [icm_ttt_ore_description_raw] NVARCHAR(4000) NULL, [icm_ttt_ore_description] NVARCHAR(1024) NULL,
 [icm_ttt_solution_type_p] VARCHAR(6) NOT NULL, [icm_ttt_solution_type_w] VARCHAR(8) NOT NULL, [icm_ttt_solution_type_raw] NVARCHAR(4000) NULL, [icm_ttt_solution_type] NVARCHAR(1024) NULL,
 [icm_ttt_treatment_at_p] VARCHAR(6) NOT NULL, [icm_ttt_treatment_at_w] VARCHAR(8) NOT NULL, [icm_ttt_treatment_at_raw] NVARCHAR(4000) NULL, [icm_ttt_treatment_at] BIGINT NULL,
 [icm_ttt_treatment_at_nano] INT NULL,
 [icm_vie_license_plate_p] VARCHAR(6) NOT NULL, [icm_vie_license_plate_w] VARCHAR(8) NOT NULL, [icm_vie_license_plate_raw] NVARCHAR(4000) NULL, [icm_vie_license_plate] NVARCHAR(1024) NULL,
 [informed_by_p] VARCHAR(6) NOT NULL, [informed_by_w] VARCHAR(8) NOT NULL, [informed_by_raw] NVARCHAR(4000) NULL, [informed_by] NVARCHAR(1024) NULL,
 [insurance_claim_commentary_p] VARCHAR(6) NOT NULL, [insurance_claim_commentary_w] VARCHAR(8) NOT NULL, [insurance_claim_commentary_raw] NVARCHAR(4000) NULL, [insurance_claim_commentary] NVARCHAR(1024) NULL,
 [insurance_claim_location_p] VARCHAR(6) NOT NULL, [insurance_claim_location_w] VARCHAR(8) NOT NULL, [insurance_claim_location_raw] NVARCHAR(4000) NULL, [insurance_claim_location] NVARCHAR(1024) NULL,
 [insurance_claim_total_p] VARCHAR(6) NOT NULL, [insurance_claim_total_w] VARCHAR(8) NOT NULL, [insurance_claim_total_raw] NVARCHAR(4000) NULL, [insurance_claim_total] DECIMAL(28,8) NULL,
 [insurer_credits_subtotal_p] VARCHAR(6) NOT NULL, [insurer_credits_subtotal_w] VARCHAR(8) NOT NULL, [insurer_credits_subtotal_raw] NVARCHAR(4000) NULL, [insurer_credits_subtotal] DECIMAL(28,8) NULL,
 [internal_description_p] VARCHAR(6) NOT NULL, [internal_description_w] VARCHAR(8) NOT NULL, [internal_description_raw] NVARCHAR(4000) NULL, [internal_description] NVARCHAR(1024) NULL,
 [invoices_count_p] VARCHAR(6) NOT NULL, [invoices_count_w] VARCHAR(8) NOT NULL, [invoices_count_raw] NVARCHAR(4000) NULL, [invoices_count] VARCHAR(40) NULL,
 [invoices_value_p] VARCHAR(6) NOT NULL, [invoices_value_w] VARCHAR(8) NOT NULL, [invoices_value_raw] NVARCHAR(4000) NULL, [invoices_value] DECIMAL(28,8) NULL,
 [invoices_volumes_p] VARCHAR(6) NOT NULL, [invoices_volumes_w] VARCHAR(8) NOT NULL, [invoices_volumes_raw] NVARCHAR(4000) NULL, [invoices_volumes] VARCHAR(40) NULL,
 [invoices_weight_p] VARCHAR(6) NOT NULL, [invoices_weight_w] VARCHAR(8) NOT NULL, [invoices_weight_raw] NVARCHAR(4000) NULL, [invoices_weight] DECIMAL(28,8) NULL,
 [list_of_claimed_products_p] VARCHAR(6) NOT NULL, [list_of_claimed_products_w] VARCHAR(8) NOT NULL, [list_of_claimed_products_raw] NVARCHAR(4000) NULL, [list_of_claimed_products] NVARCHAR(1024) NULL,
 [occurrence_at_date_p] VARCHAR(6) NOT NULL, [occurrence_at_date_w] VARCHAR(8) NOT NULL, [occurrence_at_date_raw] NVARCHAR(4000) NULL, [occurrence_at_date] DATE NULL,
 [occurrence_at_time_p] VARCHAR(6) NOT NULL, [occurrence_at_time_w] VARCHAR(8) NOT NULL, [occurrence_at_time_raw] NVARCHAR(4000) NULL, [occurrence_at_time] BIGINT NULL,
 [opening_at_date_p] VARCHAR(6) NOT NULL, [opening_at_date_w] VARCHAR(8) NOT NULL, [opening_at_date_raw] NVARCHAR(4000) NULL, [opening_at_date] DATE NULL,
 [policy_number_p] VARCHAR(6) NOT NULL, [policy_number_w] VARCHAR(8) NOT NULL, [policy_number_raw] NVARCHAR(4000) NULL, [policy_number] NVARCHAR(1024) NULL,
 [rcfdc_p] VARCHAR(6) NOT NULL, [rcfdc_w] VARCHAR(8) NOT NULL, [rcfdc_raw] NVARCHAR(4000) NULL, [rcfdc] SMALLINT NULL,
 [rctac_p] VARCHAR(6) NOT NULL, [rctac_w] VARCHAR(8) NOT NULL, [rctac_raw] NVARCHAR(4000) NULL, [rctac] SMALLINT NULL,
 [rctrc_p] VARCHAR(6) NOT NULL, [rctrc_w] VARCHAR(8) NOT NULL, [rctrc_raw] NVARCHAR(4000) NULL, [rctrc] SMALLINT NULL,
 [responsible_credits_subtotal_p] VARCHAR(6) NOT NULL, [responsible_credits_subtotal_w] VARCHAR(8) NOT NULL, [responsible_credits_subtotal_raw] NVARCHAR(4000) NULL, [responsible_credits_subtotal] DECIMAL(28,8) NULL,
 [responsible_debit_entries_subtotal_p] VARCHAR(6) NOT NULL, [responsible_debit_entries_subtotal_w] VARCHAR(8) NOT NULL, [responsible_debit_entries_subtotal_raw] NVARCHAR(4000) NULL, [responsible_debit_entries_subtotal] DECIMAL(28,8) NULL,
 [sequence_code_p] VARCHAR(6) NOT NULL, [sequence_code_w] VARCHAR(8) NOT NULL, [sequence_code_raw] NVARCHAR(4000) NULL, [sequence_code] VARCHAR(40) NULL,
 CONSTRAINT PK_expansion_SIN PRIMARY KEY(execution_id,occurrence),
 CONSTRAINT FK_expansion_SIN FOREIGN KEY(execution_id,occurrence) REFERENCES stg.expansion_lab_observation(execution_id,occurrence)
);
GO
CREATE TABLE stg.expansion_lab_array_item (
 execution_id UNIQUEIDENTIFIER NOT NULL, occurrence BIGINT NOT NULL, field_name VARCHAR(80) NOT NULL,
 physical_position INT NOT NULL, text_value NVARCHAR(256) COLLATE Latin1_General_100_BIN2 NOT NULL,
 CONSTRAINT PK_expansion_array_item PRIMARY KEY(execution_id,occurrence,field_name,physical_position),
 CONSTRAINT FK_expansion_array_item FOREIGN KEY(execution_id,occurrence) REFERENCES stg.expansion_lab_observation(execution_id,occurrence),
 CONSTRAINT CK_expansion_array_bound CHECK(physical_position BETWEEN 0 AND 31
 AND field_name IN('invoices_mapping','fit_fte_invoices_order_number','cnr_c_s_fit_invoices_mapping'))
);
GO
CREATE TABLE core.expansion_lab_root (
 root_id BIGINT IDENTITY NOT NULL PRIMARY KEY, run_id UNIQUEIDENTIFIER NOT NULL, vertical CHAR(3) NOT NULL,
 root_type VARCHAR(8) NOT NULL, root_key NVARCHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL,
 currency CHAR(3) NOT NULL, unit VARCHAR(16) NOT NULL, amount DECIMAL(28,8) NULL,
 amount_state VARCHAR(16) NOT NULL DEFAULT 'UNRESOLVED', proof_attached BIT NOT NULL DEFAULT 0,
 active BIT NOT NULL DEFAULT 1,
 CONSTRAINT FK_expansion_root_run FOREIGN KEY(run_id) REFERENCES ctl.expansion_lab_run(run_id),
 CONSTRAINT UQ_expansion_root UNIQUE(run_id,vertical,root_type,root_key),
 CONSTRAINT CK_expansion_root_key CHECK(root_type IN('INTEGER','STRING') AND LEN(root_key)>0 AND DATALENGTH(root_key)=DATALENGTH(LTRIM(RTRIM(root_key))))
);
CREATE TABLE core.expansion_lab_component (
 component_id BIGINT IDENTITY NOT NULL PRIMARY KEY, root_id BIGINT NOT NULL,
 part_type VARCHAR(8) NOT NULL, part_key NVARCHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL,
 component_type VARCHAR(8) NOT NULL, component_key NVARCHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL,
 observation_id BIGINT NOT NULL, active BIT NOT NULL, proof_attached BIT NOT NULL,
 CONSTRAINT FK_expansion_component_root FOREIGN KEY(root_id) REFERENCES core.expansion_lab_root(root_id),
 CONSTRAINT FK_expansion_component_observation FOREIGN KEY(observation_id) REFERENCES stg.expansion_lab_observation(observation_id),
 CONSTRAINT UQ_expansion_component UNIQUE(root_id,part_type,part_key,component_type,component_key)
);
CREATE INDEX IX_expansion_component_observation ON core.expansion_lab_component(observation_id) INCLUDE(root_id,active);
CREATE TABLE core.expansion_lab_part (
 root_id BIGINT NOT NULL, part_type VARCHAR(8) NOT NULL, part_key NVARCHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL,
 amount DECIMAL(28,8) NULL, amount_state VARCHAR(16) NOT NULL,
 CONSTRAINT PK_expansion_part PRIMARY KEY(root_id,part_type,part_key),
 CONSTRAINT FK_expansion_part_root FOREIGN KEY(root_id) REFERENCES core.expansion_lab_root(root_id)
);
CREATE TABLE core.expansion_lab_history (
 history_id BIGINT IDENTITY NOT NULL PRIMARY KEY, component_id BIGINT NOT NULL,
 old_observation_id BIGINT NULL, observation_id BIGINT NOT NULL, execution_id UNIQUEIDENTIFIER NOT NULL,
 action VARCHAR(16) NOT NULL, recorded_at DATETIME2(7) NOT NULL,
 CONSTRAINT FK_expansion_history_component FOREIGN KEY(component_id) REFERENCES core.expansion_lab_component(component_id),
 CONSTRAINT UQ_expansion_history UNIQUE(component_id,observation_id)
);
CREATE TABLE ctl.expansion_lab_apply_receipt (
 execution_id UNIQUEIDENTIFIER NOT NULL PRIMARY KEY, run_id UNIQUEIDENTIFIER NOT NULL,
 observations BIGINT NOT NULL,inserts BIGINT NOT NULL, updates BIGINT NOT NULL,noops BIGINT NOT NULL,
 stale BIGINT NOT NULL, quarantine BIGINT NOT NULL, unbound BIGINT NOT NULL,duplicates BIGINT NOT NULL,
 recorded_at DATETIME2(7) NOT NULL,
 CONSTRAINT FK_expansion_apply_capture FOREIGN KEY(execution_id) REFERENCES ctl.expansion_lab_capture(execution_id),
 CONSTRAINT CK_expansion_apply_equation CHECK(observations=inserts+updates+noops+stale+quarantine+unbound+duplicates)
);
GO
CREATE PROCEDURE ctl.usp_seal_expansion_lab_capture @run_id UNIQUEIDENTIFIER,@execution_id UNIQUEIDENTIFIER,@now DATETIME2(7)
AS
BEGIN
 SET NOCOUNT ON; SET XACT_ABORT ON;
 IF @@TRANCOUNT=0 THROW 53300,N'EXP_TRANSACTION_REQUIRED',1;
 IF NOT EXISTS(SELECT 1 FROM ctl.expansion_lab_capture c JOIN ctl.execution_audit a ON a.execution_id=c.execution_id
 JOIN ctl.expansion_lab_run r ON r.run_id=c.run_id WHERE c.run_id=@run_id AND c.execution_id=@execution_id
 AND c.state='CAPTURING' AND a.status=N'COMPLETED' AND a.terminal_page IS NOT NULL
 AND a.business_window_start=c.partition_start AND DATEADD(day,1,a.business_window_end)=c.partition_end_exclusive
 AND a.records_delivered=(SELECT COUNT_BIG(*) FROM stg.expansion_lab_observation o WHERE o.execution_id=@execution_id)
 AND a.records_delivered<=r.maximum_rows AND a.pages_fetched<=r.maximum_pages)
 THROW 53301,N'EXP_COMPLETE_CAPTURE_REQUIRED',1;
 UPDATE ctl.expansion_lab_capture SET state='COMPLETE',sealed_at=@now,physical_rows=
 (SELECT COUNT_BIG(*) FROM stg.expansion_lab_observation WHERE execution_id=@execution_id) WHERE execution_id=@execution_id;
END;
GO
