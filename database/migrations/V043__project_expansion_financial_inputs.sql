-- Four bounded-query inputs. Component grain preserves observed expansion; root totals are non-additive attributes.
SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;
GO
CREATE VIEW core.expansion_lab_current_input AS
 SELECT r.run_id,r.root_id,r.vertical,r.root_type,r.root_key,r.currency,r.unit,r.amount root_amount,r.amount_state,
 c.component_id,c.part_type,c.part_key,c.component_type,c.component_key,c.proof_attached,
 o.execution_id,o.occurrence,o.observation_id,o.revision,o.fresh_second,o.fresh_nano,o.fresh_inclusive,o.part_amount,o.allocation_amount,o.additive_allocation,
 capture.partition_start,
 CONVERT(BIT,CASE WHEN EXISTS(SELECT 1 FROM stg.expansion_lab_observation q JOIN ctl.expansion_lab_capture qc ON qc.execution_id=q.execution_id
 WHERE q.run_id=r.run_id AND q.vertical=r.vertical AND q.root_type=r.root_type AND q.root_key=r.root_key
 AND q.part_type=c.part_type AND q.part_key=c.part_key AND q.component_type=c.component_type AND q.component_key=c.component_key
 AND q.disposition='QUARANTINE' AND qc.state='COMPLETE'
 AND(q.fresh_second>o.fresh_second OR(q.fresh_second=o.fresh_second AND q.fresh_nano>o.fresh_nano)
 OR(q.fresh_second=o.fresh_second AND q.fresh_nano=o.fresh_nano AND q.fresh_inclusive>o.fresh_inclusive)
 OR(q.fresh_second=o.fresh_second AND q.fresh_nano=o.fresh_nano AND q.fresh_inclusive=o.fresh_inclusive AND q.revision>=o.revision))) THEN 1 ELSE 0 END) unresolved_conflict
 FROM core.expansion_lab_root r JOIN core.expansion_lab_component c ON c.root_id=r.root_id
 JOIN stg.expansion_lab_observation o ON o.observation_id=c.observation_id
 JOIN ctl.expansion_lab_capture capture ON capture.execution_id=o.execution_id
 WHERE r.active=1 AND c.active=1 AND capture.state='COMPLETE';
GO
CREATE FUNCTION pub.ufn_expansion_cap(@run_id UNIQUEIDENTIFIER,@reference_revision INT)
RETURNS TABLE AS RETURN (
 SELECT i.*,f.ant_ils_sequence_code installment_candidate,f.ant_ils_sequence_code_w installment_wire,
 f.paid,f.ant_ils_atn_reconciled reconciled,
 CASE WHEN f.paid=1 THEN 'PAGO' ELSE 'ABERTO' END payment_state,
 CASE WHEN f.paid=1 THEN N'Sim' ELSE N'Não' END paid_label,
 CASE WHEN f.ant_ils_atn_reconciled=1 THEN N'Conciliado' ELSE N'Não conciliado' END reconciliation_label,
 f.type type_raw,t.type_token,tl.label type_label,f.ant_ils_pas_ant_classification classification_raw,cl.label classification_label,
 labels.reference_release_id label_release,
 f.issue_date,f.created_at,f.created_at_nano,f.ant_ils_atn_transaction_date transaction_date,f.ant_ils_atn_liquidation_date liquidation_date,
 f.value_to_pay,f.paid_value,f.interest_value,f.discount_value,f.competence_month,f.competence_year
 FROM core.expansion_lab_current_input i JOIN stg.expansion_lab_cap f ON f.execution_id=i.execution_id AND f.occurrence=i.occurrence
 CROSS APPLY(SELECT CASE WHEN CHARINDEX(N'::',REVERSE(f.type))>0 THEN RIGHT(f.type,CHARINDEX(N'::',REVERSE(f.type))-1) ELSE f.type END type_token) t
 OUTER APPLY ref.ufn_expansion_reference(i.run_id,@reference_revision,'LABELS',i.partition_start) labels
 LEFT JOIN ref.expansion_lab_label tl ON tl.reference_release_id=labels.reference_release_id AND tl.category='CAP_TYPE' AND tl.raw_value=t.type_token COLLATE Latin1_General_100_BIN2
 LEFT JOIN ref.expansion_lab_label cl ON cl.reference_release_id=labels.reference_release_id AND cl.category='CAP_CLASS' AND cl.raw_value=f.ant_ils_pas_ant_classification COLLATE Latin1_General_100_BIN2
 WHERE i.run_id=@run_id AND i.vertical='CAP'
);
GO
CREATE FUNCTION pub.ufn_expansion_fat(@run_id UNIQUEIDENTIFIER,@reference_revision INT)
RETURNS TABLE AS RETURN (
 SELECT i.*,f.id line_candidate,f.id_w line_wire,f.fit_ant_document document_raw,
 CONVERT(BIT,CASE WHEN NULLIF(LTRIM(RTRIM(f.fit_ant_document)),N'') IS NOT NULL
 AND LOWER(LTRIM(RTRIM(f.fit_ant_document))) COLLATE Latin1_General_100_BIN2 NOT IN(N'faturado',N'aguardando faturamento') THEN 1 ELSE 0 END) has_invoice,
 f.fit_fhe_cte_number cte_number,f.fit_nse_number nfse_number,f.nfse_number nfse_alias,
 CASE WHEN f.fit_nse_number IS NOT NULL AND f.nfse_number IS NOT NULL AND f.fit_nse_number<>f.nfse_number COLLATE Latin1_General_100_BIN2 THEN 'ALIAS_CONFLICT'
 WHEN f.fit_fhe_cte_number IS NOT NULL AND n.nfse IS NOT NULL AND r.fiscal_policy='UNRESOLVED' THEN 'UNRESOLVED'
 ELSE 'RESOLVED' END fiscal_state,
 CASE WHEN f.fit_nse_number IS NOT NULL AND f.nfse_number IS NOT NULL AND f.fit_nse_number<>f.nfse_number COLLATE Latin1_General_100_BIN2 THEN NULL
 WHEN f.fit_fhe_cte_number IS NOT NULL AND n.nfse IS NOT NULL THEN CASE r.fiscal_policy WHEN 'SYNTHETIC_CTE' THEN f.fit_fhe_cte_number WHEN 'SYNTHETIC_NFSE' THEN n.nfse END
 ELSE COALESCE(f.fit_fhe_cte_number,n.nfse) END official_number,
 CONVERT(VARCHAR(32),'ABSENT_UNSOURCED_LEGACY') nfse_series_state,
 f.fit_fhe_cte_status cte_status_raw,f.fit_fhe_cte_status_result cte_status_result,l.label cte_status_label,labels.reference_release_id label_release,
 f.fit_ant_issue_date issue_date,f.fit_ant_ils_due_date due_date,f.fit_ant_ils_atn_transaction_date paid_date,
 f.fit_fhe_cte_issued_at cte_issue_second,f.fit_fhe_cte_issued_at_nano cte_issue_nano,
 f.fit_ant_value title_value,f.total freight_value,
 CASE WHEN LEN(doc.normalized)=14 AND doc.normalized NOT LIKE N'%[^0-9]%' COLLATE Latin1_General_100_BIN2 THEN CONCAT(N'cnpj:',doc.normalized)
 WHEN NULLIF(LTRIM(RTRIM(f.fit_pyr_name)),N'') IS NOT NULL THEN CONCAT(N'nome:',LTRIM(RTRIM(f.fit_pyr_name))) END client_key,
 CASE WHEN LEN(doc.normalized)=14 AND doc.normalized NOT LIKE N'%[^0-9]%' COLLATE Latin1_General_100_BIN2 THEN 'PAYER_DOCUMENT_14_DIGITS'
 WHEN NULLIF(LTRIM(RTRIM(f.fit_pyr_name)),N'') IS NOT NULL THEN 'PAYER_NAME_DISPLAY_ONLY' ELSE 'MISSING_CLIENT' END client_provenance,
 f.fit_pyr_document payer_document_raw,f.fit_pyr_name payer_name_raw,f.type freight_type_raw,LTRIM(RTRIM(REPLACE(f.type,N'Freight::',N''))) freight_type,
 f.courtesy,f.status status_raw,f.fit_fsn_name classification_raw,f.service_at service_second,f.service_at_nano service_nano
 FROM core.expansion_lab_current_input i JOIN stg.expansion_lab_fat f ON f.execution_id=i.execution_id AND f.occurrence=i.occurrence
 JOIN ctl.expansion_lab_run r ON r.run_id=i.run_id
 CROSS APPLY(SELECT COALESCE(f.fit_nse_number,f.nfse_number) nfse) n
 CROSS APPLY(SELECT REPLACE(REPLACE(REPLACE(REPLACE(LTRIM(RTRIM(f.fit_pyr_document)),N'.',N''),N'/',N''),N'-',N''),N' ',N'') normalized) doc
 OUTER APPLY ref.ufn_expansion_reference(i.run_id,@reference_revision,'LABELS',i.partition_start) labels
 LEFT JOIN ref.expansion_lab_label l ON l.reference_release_id=labels.reference_release_id AND l.category='FAT_CTE' AND l.raw_value=f.fit_fhe_cte_status COLLATE Latin1_General_100_BIN2
 WHERE i.run_id=@run_id AND i.vertical='FAT'
);
GO
CREATE FUNCTION pub.ufn_expansion_inv(@run_id UNIQUEIDENTIFIER)
RETURNS TABLE AS RETURN (
 SELECT i.*,f.sequence_code sequence_candidate,f.sequence_code_w sequence_wire,f.cnr_c_s_fit_corporation_sequence_number freight_candidate,
 f.cnr_c_s_fit_invoices_value invoices_value,f.cnr_c_s_fit_invoices_volumes volumes,f.cnr_c_s_fit_invoices_volumes_p volumes_presence,
 f.cnr_c_s_fit_real_weight real_weight,f.cnr_c_s_fit_taxed_weight taxed_weight,f.cnr_c_s_fit_total_cubic_volume cubic_volume,
 f.cnr_c_s_read_volumes read_volumes,f.cnr_c_s_fit_invoices_mapping mapping_count,f.cnr_c_s_fit_invoices_mapping_p mapping_presence,
 f.status status_raw,f.type type_raw,f.cnr_c_s_fit_fte_lce_ore_description occurrence_description
 FROM core.expansion_lab_current_input i JOIN stg.expansion_lab_inv f ON f.execution_id=i.execution_id AND f.occurrence=i.occurrence
 WHERE i.run_id=@run_id AND i.vertical='INV'
);
GO
CREATE FUNCTION pub.ufn_expansion_sin(@run_id UNIQUEIDENTIFIER)
RETURNS TABLE AS RETURN (
 SELECT i.*,f.sequence_code sequence_candidate,f.sequence_code_w sequence_wire,
 f.icm_fis_fit_corporation_sequence_number freight_candidate,f.icm_fis_ioe_number occurrence_candidate,
 f.insurance_claim_total,f.invoices_value,f.invoices_volumes,f.invoices_weight,f.invoices_count,
 f.customer_credit_entries_subtotal,f.customer_debits_subtotal,f.insurer_credits_subtotal,f.responsible_credits_subtotal,f.responsible_debit_entries_subtotal,
 f.opening_at_date,f.occurrence_at_date,f.occurrence_at_time occurrence_time_nano,f.occurrence_at_time_raw,
 f.finished_at_date,f.finished_at_time finished_time_nano,f.finished_at_time_raw,
 f.icm_ttt_treatment_at treatment_second,f.icm_ttt_treatment_at_nano treatment_nano,f.icm_ttt_dealing_type,f.icm_ttt_solution_type
 FROM core.expansion_lab_current_input i JOIN stg.expansion_lab_sin f ON f.execution_id=i.execution_id AND f.occurrence=i.occurrence
 WHERE i.run_id=@run_id AND i.vertical='SIN'
);
GO
