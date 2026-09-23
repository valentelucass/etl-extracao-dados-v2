-- SQL01/06: captured components and existing financial policies; no root SUM in display.
SET ANSI_NULLS ON;SET QUOTED_IDENTIFIER ON;
GO
CREATE TABLE stg.analytic_lab_fiscal_attribute (
 attribute_id BIGINT IDENTITY NOT NULL CONSTRAINT PK_ana_fiscal_attr PRIMARY KEY,
 run_id UNIQUEIDENTIFIER NOT NULL,component_id BIGINT NOT NULL,source_execution UNIQUEIDENTIFIER NOT NULL,
 revision INT NOT NULL,presence VARCHAR(5) NOT NULL,nfse_series NVARCHAR(50) COLLATE Latin1_General_100_BIN2 NULL,
 evidence VARCHAR(64) NOT NULL,
 CONSTRAINT UQ_ana_fiscal_attr UNIQUE(run_id,component_id,source_execution,revision),
 CONSTRAINT FK_ana_fiscal_attr_run FOREIGN KEY(run_id) REFERENCES ctl.analytic_lab_run(run_id),
 CONSTRAINT FK_ana_fiscal_attr_component FOREIGN KEY(component_id) REFERENCES core.expansion_lab_component(component_id),
 CONSTRAINT FK_ana_fiscal_attr_execution FOREIGN KEY(source_execution) REFERENCES ctl.expansion_lab_capture(execution_id),
 CONSTRAINT CK_ana_fiscal_attr CHECK(revision BETWEEN 1 AND 100000 AND evidence='synthetic-fiscal-attribute-v1'
 AND((presence='NULL' AND nfse_series IS NULL) OR(presence='VALUE' AND NULLIF(LTRIM(RTRIM(nfse_series)),N'') IS NOT NULL)))
);
GO
CREATE TRIGGER stg.trg_analytic_fiscal_attribute_immutable ON stg.analytic_lab_fiscal_attribute AFTER UPDATE,DELETE AS
BEGIN SET NOCOUNT ON;IF EXISTS(SELECT 1 FROM deleted) THROW 53680,N'ANA_FISCAL_ATTRIBUTE_IMMUTABLE',1;END;
GO
CREATE TYPE stg.analytic_lab_fiscal_attribute_batch AS TABLE (
 component_id BIGINT NOT NULL,source_execution UNIQUEIDENTIFIER NOT NULL,revision INT NOT NULL,
 presence VARCHAR(5) NOT NULL,nfse_series NVARCHAR(50) COLLATE Latin1_General_100_BIN2 NULL,evidence VARCHAR(64) NOT NULL,
 PRIMARY KEY(component_id)
);
GO
CREATE PROCEDURE ref.usp_bind_analytic_fiscal_attributes @run_id UNIQUEIDENTIFIER,@batch stg.analytic_lab_fiscal_attribute_batch READONLY AS
BEGIN
 SET NOCOUNT ON;SET XACT_ABORT OFF;
 IF @@TRANCOUNT=0 OR NOT EXISTS(SELECT 1 FROM ctl.analytic_lab_run WHERE run_id=@run_id)
 OR (SELECT COUNT_BIG(*) FROM @batch) NOT BETWEEN 1 AND 64 THROW 53681,N'ANA_FISCAL_ATTRIBUTE_SCOPE',1;
 DECLARE @lock INT,@resource NVARCHAR(255)=CONCAT(N'ANA_FISCAL_ATTRIBUTE:',CONVERT(NVARCHAR(36),@run_id));
 EXEC @lock=sys.sp_getapplock @Resource=@resource,@LockMode='Exclusive',@LockOwner='Transaction',@LockTimeout=1000;
 IF @lock<0 THROW 53682,N'ANA_FISCAL_ATTRIBUTE_BUSY',1;
 IF EXISTS(SELECT 1 FROM @batch b WHERE b.revision NOT BETWEEN 1 AND 100000 OR b.evidence<>'synthetic-fiscal-attribute-v1'
 OR b.presence NOT IN('NULL','VALUE') OR(b.presence='NULL' AND b.nfse_series IS NOT NULL)
 OR(b.presence='VALUE' AND NULLIF(LTRIM(RTRIM(b.nfse_series)),N'') IS NULL)
 OR NOT EXISTS(SELECT 1 FROM ctl.analytic_lab_source_group g JOIN core.expansion_lab_current_input i ON i.run_id=g.expansion_run
 WHERE g.run_id=@run_id AND i.vertical='FAT' AND i.component_id=b.component_id AND i.execution_id=b.source_execution AND i.unresolved_conflict=0))
 THROW 53683,N'ANA_FISCAL_ATTRIBUTE_SOURCE',1;
 IF EXISTS(SELECT 1 FROM @batch b JOIN stg.analytic_lab_fiscal_attribute a ON a.run_id=@run_id AND a.component_id=b.component_id AND a.source_execution=b.source_execution
 WHERE a.revision>b.revision OR(a.revision=b.revision AND EXISTS(SELECT b.presence,b.nfse_series,b.evidence EXCEPT SELECT a.presence,a.nfse_series,a.evidence)))
 THROW 53684,N'ANA_FISCAL_ATTRIBUTE_REVISION',1;
 INSERT stg.analytic_lab_fiscal_attribute(run_id,component_id,source_execution,revision,presence,nfse_series,evidence)
 SELECT @run_id,b.* FROM @batch b WHERE NOT EXISTS(SELECT 1 FROM stg.analytic_lab_fiscal_attribute a
 WHERE a.run_id=@run_id AND a.component_id=b.component_id AND a.source_execution=b.source_execution AND a.revision=b.revision);
 SELECT COUNT_BIG(*) accepted FROM @batch;
END;
GO
CREATE VIEW core.analytic_lab_faturas_projection AS SELECT
 CONVERT(TIME(7),core.ufn_analytic_raster_time(f.[fit_fhe_cte_issued_at],f.[fit_fhe_cte_issued_at_nano],0) AT TIME ZONE zone.name) AS [Hora (Solicitacao)],
 CONVERT(NVARCHAR(320),CONCAT(CONVERT(NVARCHAR(36),g.run_id),N'/',CONCAT(i.root_type COLLATE Latin1_General_100_BIN2,N':',i.root_key),N'/',i.part_type COLLATE Latin1_General_100_BIN2,N':',i.part_key,N'/',i.component_type COLLATE Latin1_General_100_BIN2,N':',i.component_key)) AS [ID Único],
 branch.normalized_name AS [Filial],
 f.[fit_diy_sae_name] AS [Estado],
 f.[fit_fhe_cte_number] AS [CT-e/Número],
 policy.official_number AS [Número do Documento],
 f.[fit_fhe_cte_key] AS [CT-e/Chave],
 core.ufn_analytic_raster_time(f.[fit_fhe_cte_issued_at],f.[fit_fhe_cte_issued_at_nano],0) AT TIME ZONE zone.name AS [CT-e/Data de emissão],
 f.[total] AS [Frete/Valor dos CT-es],
 f.[third_party_ctes_value] AS [Terceiros/Valor CT-es],
 COALESCE((SELECT l.label COLLATE Latin1_General_100_BIN2 FROM ref.analytic_lab_label l WHERE l.reference_release_id=selection.reference_release_id AND l.category='FAT_CTE' AND l.raw_value=f.fit_fhe_cte_status COLLATE Latin1_General_100_BIN2),f.fit_fhe_cte_status COLLATE Latin1_General_100_BIN2) AS [CT-e/Status],
 f.[fit_fhe_cte_status_result] AS [CT-e/Resultado],
 policy.freight_type AS [Tipo],
 f.[fit_fsn_name] AS [Classificação],
 f.[fit_pyr_name] AS [Pagador do frete/Nome],
 f.[fit_pyr_document] AS [Pagador do frete/Documento],
 CASE WHEN policy.client_provenance='PAYER_DOCUMENT_14_DIGITS' THEN SUBSTRING(policy.client_key,6,14) END AS [Cliente/CNPJ],
 f.[fit_rpt_name] AS [Remetente/Nome],
 f.[fit_rpt_document] AS [Remetente/Documento],
 f.[fit_sdr_name] AS [Destinatário/Nome],
 f.[fit_sdr_document] AS [Destinatário/Documento],
 f.[fit_sps_slr_psn_name] AS [Vendedor/Nome],
 COALESCE(f.fit_nse_number,f.nfse_number) COLLATE Latin1_General_100_BIN2 AS [NFS-e/Número],
 supplement.nfse_series AS [NFS-e/Série],
 COALESCE(f.fit_nse_number,f.nfse_number) COLLATE Latin1_General_100_BIN2 AS [fit_nse_number],
 COALESCE(f.fit_nse_number,f.nfse_number) COLLATE Latin1_General_100_BIN2 AS [N° NFS-e],
 f.[fit_ant_tat_bro_description] AS [Carteira/Descrição],
 f.[fit_ant_tat_custom_instruction] AS [Instrução Customizada],
 CONVERT(NVARCHAR(32),CASE policy.has_invoice WHEN 1 THEN N'Faturado' ELSE N'Aguardando Faturamento' END) AS [Status do Processo],
 f.[fit_ant_document] AS [Fatura/N° Documento],
 f.[fit_ant_issue_date] AS [Fatura/Emissão],
 f.[fit_ant_value] AS [Fatura/Valor],
 f.[fit_ant_value] AS [Fatura/Valor Total],
 f.[fit_ant_document] AS [Fatura/Número],
 f.[fit_ant_issue_date] AS [Fatura/Emissão Fatura],
 f.[fit_ant_ils_due_date] AS [Parcelas/Vencimento],
 f.[fit_ant_ils_atn_transaction_date] AS [Fatura/Baixa],
 f.[fit_ant_ils_original_due_date] AS [Fatura/Data Vencimento Original],
 notes.display_names AS [Notas Fiscais],
 orders.display_names AS [Pedidos/Cliente],
 (SELECT i.execution_id,i.occurrence,i.observation_id,i.revision,f.[comments_p] AS [comments.presence],f.[comments_w] AS [comments.wire],f.[comments_raw] AS [comments.raw],f.[corporation_sequence_number_p] AS [corporation_sequence_number.presence],f.[corporation_sequence_number_w] AS [corporation_sequence_number.wire],f.[corporation_sequence_number_raw] AS [corporation_sequence_number.raw],f.[courtesy_p] AS [courtesy.presence],f.[courtesy_w] AS [courtesy.wire],f.[courtesy_raw] AS [courtesy.raw],f.[emission_type_p] AS [emission_type.presence],f.[emission_type_w] AS [emission_type.wire],f.[emission_type_raw] AS [emission_type.raw],f.[finished_at_p] AS [finished_at.presence],f.[finished_at_w] AS [finished_at.wire],f.[finished_at_raw] AS [finished_at.raw],f.[fit_ant_ant_name_p] AS [fit_ant_ant_name.presence],f.[fit_ant_ant_name_w] AS [fit_ant_ant_name.wire],f.[fit_ant_ant_name_raw] AS [fit_ant_ant_name.raw],f.[fit_ant_discount_value_p] AS [fit_ant_discount_value.presence],f.[fit_ant_discount_value_w] AS [fit_ant_discount_value.wire],f.[fit_ant_discount_value_raw] AS [fit_ant_discount_value.raw],f.[fit_ant_document_p] AS [fit_ant_document.presence],f.[fit_ant_document_w] AS [fit_ant_document.wire],f.[fit_ant_document_raw] AS [fit_ant_document.raw],f.[fit_ant_ils_atn_transaction_date_p] AS [fit_ant_ils_atn_transaction_date.presence],f.[fit_ant_ils_atn_transaction_date_w] AS [fit_ant_ils_atn_transaction_date.wire],f.[fit_ant_ils_atn_transaction_date_raw] AS [fit_ant_ils_atn_transaction_date.raw],f.[fit_ant_ils_due_date_p] AS [fit_ant_ils_due_date.presence],f.[fit_ant_ils_due_date_w] AS [fit_ant_ils_due_date.wire],f.[fit_ant_ils_due_date_raw] AS [fit_ant_ils_due_date.raw],f.[fit_ant_ils_original_due_date_p] AS [fit_ant_ils_original_due_date.presence],f.[fit_ant_ils_original_due_date_w] AS [fit_ant_ils_original_due_date.wire],f.[fit_ant_ils_original_due_date_raw] AS [fit_ant_ils_original_due_date.raw],f.[fit_ant_interest_value_p] AS [fit_ant_interest_value.presence],f.[fit_ant_interest_value_w] AS [fit_ant_interest_value.wire],f.[fit_ant_interest_value_raw] AS [fit_ant_interest_value.raw],f.[fit_ant_issue_date_p] AS [fit_ant_issue_date.presence],f.[fit_ant_issue_date_w] AS [fit_ant_issue_date.wire],f.[fit_ant_issue_date_raw] AS [fit_ant_issue_date.raw],f.[fit_ant_tat_account_number_p] AS [fit_ant_tat_account_number.presence],f.[fit_ant_tat_account_number_w] AS [fit_ant_tat_account_number.wire],f.[fit_ant_tat_account_number_raw] AS [fit_ant_tat_account_number.raw],f.[fit_ant_tat_agency_number_p] AS [fit_ant_tat_agency_number.presence],f.[fit_ant_tat_agency_number_w] AS [fit_ant_tat_agency_number.wire],f.[fit_ant_tat_agency_number_raw] AS [fit_ant_tat_agency_number.raw],f.[fit_ant_tat_bnk_name_p] AS [fit_ant_tat_bnk_name.presence],f.[fit_ant_tat_bnk_name_w] AS [fit_ant_tat_bnk_name.wire],f.[fit_ant_tat_bnk_name_raw] AS [fit_ant_tat_bnk_name.raw],f.[fit_ant_tat_bro_description_p] AS [fit_ant_tat_bro_description.presence],f.[fit_ant_tat_bro_description_w] AS [fit_ant_tat_bro_description.wire],f.[fit_ant_tat_bro_description_raw] AS [fit_ant_tat_bro_description.raw],f.[fit_ant_tat_custom_instruction_p] AS [fit_ant_tat_custom_instruction.presence],f.[fit_ant_tat_custom_instruction_w] AS [fit_ant_tat_custom_instruction.wire],f.[fit_ant_tat_custom_instruction_raw] AS [fit_ant_tat_custom_instruction.raw],f.[fit_ant_value_p] AS [fit_ant_value.presence],f.[fit_ant_value_w] AS [fit_ant_value.wire],f.[fit_ant_value_raw] AS [fit_ant_value.raw],f.[fit_crn_psn_nickname_p] AS [fit_crn_psn_nickname.presence],f.[fit_crn_psn_nickname_w] AS [fit_crn_psn_nickname.wire],f.[fit_crn_psn_nickname_raw] AS [fit_crn_psn_nickname.raw],f.[fit_d_t_created_at_p] AS [fit_d_t_created_at.presence],f.[fit_d_t_created_at_w] AS [fit_d_t_created_at.wire],f.[fit_d_t_created_at_raw] AS [fit_d_t_created_at.raw],f.[fit_diy_sae_name_p] AS [fit_diy_sae_name.presence],f.[fit_diy_sae_name_w] AS [fit_diy_sae_name.wire],f.[fit_diy_sae_name_raw] AS [fit_diy_sae_name.raw],f.[fit_dyn_drt_nickname_p] AS [fit_dyn_drt_nickname.presence],f.[fit_dyn_drt_nickname_w] AS [fit_dyn_drt_nickname.wire],f.[fit_dyn_drt_nickname_raw] AS [fit_dyn_drt_nickname.raw],f.[fit_fhe_cte_issued_at_p] AS [fit_fhe_cte_issued_at.presence],f.[fit_fhe_cte_issued_at_w] AS [fit_fhe_cte_issued_at.wire],f.[fit_fhe_cte_issued_at_raw] AS [fit_fhe_cte_issued_at.raw],f.[fit_fhe_cte_key_p] AS [fit_fhe_cte_key.presence],f.[fit_fhe_cte_key_w] AS [fit_fhe_cte_key.wire],f.[fit_fhe_cte_key_raw] AS [fit_fhe_cte_key.raw],f.[fit_fhe_cte_number_p] AS [fit_fhe_cte_number.presence],f.[fit_fhe_cte_number_w] AS [fit_fhe_cte_number.wire],f.[fit_fhe_cte_number_raw] AS [fit_fhe_cte_number.raw],f.[fit_fhe_cte_status_p] AS [fit_fhe_cte_status.presence],f.[fit_fhe_cte_status_w] AS [fit_fhe_cte_status.wire],f.[fit_fhe_cte_status_raw] AS [fit_fhe_cte_status.raw],f.[fit_fhe_cte_status_result_p] AS [fit_fhe_cte_status_result.presence],f.[fit_fhe_cte_status_result_w] AS [fit_fhe_cte_status_result.wire],f.[fit_fhe_cte_status_result_raw] AS [fit_fhe_cte_status_result.raw],f.[fit_fsn_name_p] AS [fit_fsn_name.presence],f.[fit_fsn_name_w] AS [fit_fsn_name.wire],f.[fit_fsn_name_raw] AS [fit_fsn_name.raw],f.[fit_fte_foe_ore_description_p] AS [fit_fte_foe_ore_description.presence],f.[fit_fte_foe_ore_description_w] AS [fit_fte_foe_ore_description.wire],f.[fit_fte_foe_ore_description_raw] AS [fit_fte_foe_ore_description.raw],f.[fit_fte_has_delivery_receipt_p] AS [fit_fte_has_delivery_receipt.presence],f.[fit_fte_has_delivery_receipt_w] AS [fit_fte_has_delivery_receipt.wire],f.[fit_fte_has_delivery_receipt_raw] AS [fit_fte_has_delivery_receipt.raw],f.[fit_fte_invoices_order_number_p] AS [fit_fte_invoices_order_number.presence],f.[fit_fte_invoices_order_number_w] AS [fit_fte_invoices_order_number.wire],f.[fit_fte_invoices_order_number_raw] AS [fit_fte_invoices_order_number.raw],f.[fit_nse_number_p] AS [fit_nse_number.presence],f.[fit_nse_number_w] AS [fit_nse_number.wire],f.[fit_nse_number_raw] AS [fit_nse_number.raw],f.[fit_pyr_cor_billing_cycle_p] AS [fit_pyr_cor_billing_cycle.presence],f.[fit_pyr_cor_billing_cycle_w] AS [fit_pyr_cor_billing_cycle.wire],f.[fit_pyr_cor_billing_cycle_raw] AS [fit_pyr_cor_billing_cycle.raw],f.[fit_pyr_cor_billing_due_in_days_p] AS [fit_pyr_cor_billing_due_in_days.presence],f.[fit_pyr_cor_billing_due_in_days_w] AS [fit_pyr_cor_billing_due_in_days.wire],f.[fit_pyr_cor_billing_due_in_days_raw] AS [fit_pyr_cor_billing_due_in_days.raw],f.[fit_pyr_document_p] AS [fit_pyr_document.presence],f.[fit_pyr_document_w] AS [fit_pyr_document.wire],f.[fit_pyr_document_raw] AS [fit_pyr_document.raw],f.[fit_pyr_name_p] AS [fit_pyr_name.presence],f.[fit_pyr_name_w] AS [fit_pyr_name.wire],f.[fit_pyr_name_raw] AS [fit_pyr_name.raw],f.[fit_rpt_document_p] AS [fit_rpt_document.presence],f.[fit_rpt_document_w] AS [fit_rpt_document.wire],f.[fit_rpt_document_raw] AS [fit_rpt_document.raw],f.[fit_rpt_name_p] AS [fit_rpt_name.presence],f.[fit_rpt_name_w] AS [fit_rpt_name.wire],f.[fit_rpt_name_raw] AS [fit_rpt_name.raw],f.[fit_sdr_document_p] AS [fit_sdr_document.presence],f.[fit_sdr_document_w] AS [fit_sdr_document.wire],f.[fit_sdr_document_raw] AS [fit_sdr_document.raw],f.[fit_sdr_name_p] AS [fit_sdr_name.presence],f.[fit_sdr_name_w] AS [fit_sdr_name.wire],f.[fit_sdr_name_raw] AS [fit_sdr_name.raw],f.[fit_sps_slr_psn_name_p] AS [fit_sps_slr_psn_name.presence],f.[fit_sps_slr_psn_name_w] AS [fit_sps_slr_psn_name.wire],f.[fit_sps_slr_psn_name_raw] AS [fit_sps_slr_psn_name.raw],f.[id_p] AS [id.presence],f.[id_w] AS [id.wire],f.[id_raw] AS [id.raw],f.[invoices_mapping_p] AS [invoices_mapping.presence],f.[invoices_mapping_w] AS [invoices_mapping.wire],f.[invoices_mapping_raw] AS [invoices_mapping.raw],f.[nfse_number_p] AS [nfse_number.presence],f.[nfse_number_w] AS [nfse_number.wire],f.[nfse_number_raw] AS [nfse_number.raw],f.[payment_type_p] AS [payment_type.presence],f.[payment_type_w] AS [payment_type.wire],f.[payment_type_raw] AS [payment_type.raw],f.[reference_number_p] AS [reference_number.presence],f.[reference_number_w] AS [reference_number.wire],f.[reference_number_raw] AS [reference_number.raw],f.[service_at_p] AS [service_at.presence],f.[service_at_w] AS [service_at.wire],f.[service_at_raw] AS [service_at.raw],f.[service_type_p] AS [service_type.presence],f.[service_type_w] AS [service_type.wire],f.[service_type_raw] AS [service_type.raw],f.[status_p] AS [status.presence],f.[status_w] AS [status.wire],f.[status_raw] AS [status.raw],f.[third_party_ctes_value_p] AS [third_party_ctes_value.presence],f.[third_party_ctes_value_w] AS [third_party_ctes_value.wire],f.[third_party_ctes_value_raw] AS [third_party_ctes_value.raw],f.[total_p] AS [total.presence],f.[total_w] AS [total.wire],f.[total_raw] AS [total.raw],f.[type_p] AS [type.presence],f.[type_w] AS [type.wire],f.[type_raw] AS [type.raw] FOR JSON PATH,INCLUDE_NULL_VALUES,WITHOUT_ARRAY_WRAPPER) AS [Metadata],
 capture.sealed_at AS [Data da Última Atualização],
 g.run_id,CONVERT(NVARCHAR(256),CONCAT(i.root_type COLLATE Latin1_General_100_BIN2,N':',i.root_key)) source_key,i.root_id,i.component_id,i.execution_id,i.occurrence,i.observation_id,
 dates.business_date,selection.revision reference_revision,selection.reference_release_id,branch.binding_id branch_binding_id,
 i.currency,i.unit,i.root_amount,i.part_amount,i.allocation_amount,
 policy.has_invoice,policy.fiscal_state,policy.client_provenance,er.fiscal_policy,supplement.attribute_id fiscal_attribute_id,supplement.presence nfse_series_presence,
 CONVERT(VARCHAR(32),CASE WHEN supplement.attribute_id IS NULL THEN 'UNRESOLVED_UNSOURCED' ELSE 'EXPLICIT_SYNTHETIC_BINDING' END) nfse_series_provenance,
 CONVERT(VARCHAR(40),CASE WHEN i.unresolved_conflict=1 THEN 'SOURCE_CONFLICT' WHEN dates.business_date IS NULL THEN 'BUSINESS_DATE_MISSING'
 WHEN selection.reference_release_id IS NULL THEN 'REFERENCE_MISSING' WHEN ISNULL(branch.disposition,'MISSING')<>'RESOLVED' THEN 'BRANCH_UNRESOLVED'
 WHEN policy.fiscal_state<>'RESOLVED' THEN 'FISCAL_UNRESOLVED' WHEN COALESCE(f.fit_nse_number,f.nfse_number) IS NOT NULL AND supplement.attribute_id IS NULL THEN 'NFSE_SERIES_UNRESOLVED'
 ELSE 'READY' END) disposition
 FROM ctl.analytic_lab_source_group g JOIN ctl.analytic_lab_run run ON run.run_id=g.run_id
 JOIN ctl.expansion_lab_run er ON er.run_id=g.expansion_run
 JOIN core.expansion_lab_current_input i ON i.run_id=g.expansion_run AND i.vertical='FAT'
 JOIN stg.expansion_lab_fat f ON f.execution_id=i.execution_id AND f.occurrence=i.occurrence
 JOIN ctl.expansion_lab_capture capture ON capture.execution_id=i.execution_id
 CROSS APPLY(SELECT CASE run.zone_id WHEN 'UTC' THEN 'UTC' ELSE 'E. South America Standard Time' END name) zone
 CROSS APPLY(SELECT COALESCE(f.fit_ant_issue_date,CONVERT(DATE,core.ufn_analytic_raster_time(f.[fit_fhe_cte_issued_at],f.[fit_fhe_cte_issued_at_nano],0) AT TIME ZONE zone.name)) business_date) dates
 LEFT JOIN ctl.analytic_lab_reference_selection selection ON selection.run_id=g.run_id AND selection.valid_from<=dates.business_date AND selection.valid_to_exclusive>dates.business_date
 CROSS APPLY(SELECT p.* FROM pub.ufn_expansion_fat(g.expansion_run,selection.revision) p WHERE p.component_id=i.component_id) policy
 OUTER APPLY(SELECT * FROM ref.ufn_analytic_dimension(g.run_id,selection.revision,dates.business_date) d WHERE d.entity='FAT' AND d.source_key=CONCAT(i.root_type COLLATE Latin1_General_100_BIN2,N':',i.root_key) COLLATE Latin1_General_100_BIN2 AND d.role='BRANCH') branch
 OUTER APPLY(SELECT a.* FROM stg.analytic_lab_fiscal_attribute a WHERE a.run_id=g.run_id AND a.component_id=i.component_id AND a.source_execution=i.execution_id
 AND NOT EXISTS(SELECT 1 FROM stg.analytic_lab_fiscal_attribute newer WHERE newer.run_id=a.run_id AND newer.component_id=a.component_id AND newer.source_execution=a.source_execution AND newer.revision>a.revision)) supplement
 OUTER APPLY(SELECT STRING_AGG(CONVERT(NVARCHAR(4000),a.text_value),N', ') WITHIN GROUP(ORDER BY a.physical_position) display_names FROM stg.expansion_lab_array_item a WHERE a.execution_id=i.execution_id AND a.occurrence=i.occurrence AND a.field_name='invoices_mapping') notes
 OUTER APPLY(SELECT STRING_AGG(CONVERT(NVARCHAR(4000),a.text_value),N', ') WITHIN GROUP(ORDER BY a.physical_position) display_names FROM stg.expansion_lab_array_item a WHERE a.execution_id=i.execution_id AND a.occurrence=i.occurrence AND a.field_name='fit_fte_invoices_order_number') orders;
GO
CREATE VIEW pub.analytic_lab_sql_01 AS SELECT * FROM core.analytic_lab_faturas_projection WHERE disposition='READY';
GO
CREATE VIEW core.analytic_lab_contas_a_pagar_projection AS SELECT
 CONVERT(TIME(7),core.ufn_analytic_raster_time(f.[created_at],f.[created_at_nano],0) AT TIME ZONE zone.name) AS [Hora (Solicitacao)],
 f.[ant_ils_sequence_code] AS [Lançamento a Pagar/N°],
 f.[document] AS [N° Documento],
 f.[issue_date] AS [Emissão],
 COALESCE(policy.type_label COLLATE Latin1_General_100_BIN2,policy.type_token COLLATE Latin1_General_100_BIN2) AS [Tipo],
 f.[value] AS [Valor],
 f.[interest_value] AS [Juros],
 f.[discount_value] AS [Desconto],
 f.[value_to_pay] AS [Valor a pagar],
 CONVERT(NVARCHAR(3),CASE f.paid WHEN 1 THEN N'Sim' ELSE N'Não' END) AS [Pago],
 f.[paid_value] AS [Valor pago],
 f.[ant_rir_name] AS [Fornecedor/Nome],
 branch.normalized_name AS [Filial],
 COALESCE(policy.classification_label COLLATE Latin1_General_100_BIN2,f.ant_ils_pas_ant_classification COLLATE Latin1_General_100_BIN2) AS [Conta Contábil/Classificação],
 account.normalized_name AS [Conta Contábil/Descrição],
 f.[ant_ils_pas_value] AS [Conta Contábil/Valor],
 f.[ant_ces_acr_name] AS [Centro de custo/Nome],
 f.[ant_ces_value] AS [Centro de custo/Valor],
 f.[ant_aln_name] AS [Área de Lançamento],
 f.[competence_month] AS [Mês de Competência],
 f.[competence_year] AS [Ano de Competência],
 core.ufn_analytic_raster_time(f.[created_at],f.[created_at_nano],0) AT TIME ZONE zone.name AS [Data criação],
 f.[ant_ils_comments] AS [Observações],
 f.[ant_ils_expense_description] AS [Descrição da despesa],
 f.[ant_ils_atn_liquidation_date] AS [Baixa/Data liquidação],
 f.[ant_ils_atn_transaction_date] AS [Data transação],
 f.[ant_uer_name] AS [Usuário/Nome],
 policy.payment_state AS [Status Pagamento],
 CONVERT(NVARCHAR(20),CASE f.ant_ils_atn_reconciled WHEN 1 THEN N'Conciliado' WHEN 0 THEN N'Não conciliado' END) AS [Conciliado],
 (SELECT i.execution_id,i.occurrence,i.observation_id,i.revision,f.[ant_aln_name_p] AS [ant_aln_name.presence],f.[ant_aln_name_w] AS [ant_aln_name.wire],f.[ant_aln_name_raw] AS [ant_aln_name.raw],f.[ant_ces_acr_name_p] AS [ant_ces_acr_name.presence],f.[ant_ces_acr_name_w] AS [ant_ces_acr_name.wire],f.[ant_ces_acr_name_raw] AS [ant_ces_acr_name.raw],f.[ant_ces_value_p] AS [ant_ces_value.presence],f.[ant_ces_value_w] AS [ant_ces_value.wire],f.[ant_ces_value_raw] AS [ant_ces_value.raw],f.[ant_crn_psn_nickname_p] AS [ant_crn_psn_nickname.presence],f.[ant_crn_psn_nickname_w] AS [ant_crn_psn_nickname.wire],f.[ant_crn_psn_nickname_raw] AS [ant_crn_psn_nickname.raw],f.[ant_ils_atn_liquidation_date_p] AS [ant_ils_atn_liquidation_date.presence],f.[ant_ils_atn_liquidation_date_w] AS [ant_ils_atn_liquidation_date.wire],f.[ant_ils_atn_liquidation_date_raw] AS [ant_ils_atn_liquidation_date.raw],f.[ant_ils_atn_reconciled_p] AS [ant_ils_atn_reconciled.presence],f.[ant_ils_atn_reconciled_w] AS [ant_ils_atn_reconciled.wire],f.[ant_ils_atn_reconciled_raw] AS [ant_ils_atn_reconciled.raw],f.[ant_ils_atn_transaction_date_p] AS [ant_ils_atn_transaction_date.presence],f.[ant_ils_atn_transaction_date_w] AS [ant_ils_atn_transaction_date.wire],f.[ant_ils_atn_transaction_date_raw] AS [ant_ils_atn_transaction_date.raw],f.[ant_ils_comments_p] AS [ant_ils_comments.presence],f.[ant_ils_comments_w] AS [ant_ils_comments.wire],f.[ant_ils_comments_raw] AS [ant_ils_comments.raw],f.[ant_ils_expense_description_p] AS [ant_ils_expense_description.presence],f.[ant_ils_expense_description_w] AS [ant_ils_expense_description.wire],f.[ant_ils_expense_description_raw] AS [ant_ils_expense_description.raw],f.[ant_ils_pas_ant_classification_p] AS [ant_ils_pas_ant_classification.presence],f.[ant_ils_pas_ant_classification_w] AS [ant_ils_pas_ant_classification.wire],f.[ant_ils_pas_ant_classification_raw] AS [ant_ils_pas_ant_classification.raw],f.[ant_ils_pas_ant_name_p] AS [ant_ils_pas_ant_name.presence],f.[ant_ils_pas_ant_name_w] AS [ant_ils_pas_ant_name.wire],f.[ant_ils_pas_ant_name_raw] AS [ant_ils_pas_ant_name.raw],f.[ant_ils_pas_value_p] AS [ant_ils_pas_value.presence],f.[ant_ils_pas_value_w] AS [ant_ils_pas_value.wire],f.[ant_ils_pas_value_raw] AS [ant_ils_pas_value.raw],f.[ant_ils_sequence_code_p] AS [ant_ils_sequence_code.presence],f.[ant_ils_sequence_code_w] AS [ant_ils_sequence_code.wire],f.[ant_ils_sequence_code_raw] AS [ant_ils_sequence_code.raw],f.[ant_rir_name_p] AS [ant_rir_name.presence],f.[ant_rir_name_w] AS [ant_rir_name.wire],f.[ant_rir_name_raw] AS [ant_rir_name.raw],f.[ant_uer_name_p] AS [ant_uer_name.presence],f.[ant_uer_name_w] AS [ant_uer_name.wire],f.[ant_uer_name_raw] AS [ant_uer_name.raw],f.[comments_p] AS [comments.presence],f.[comments_w] AS [comments.wire],f.[comments_raw] AS [comments.raw],f.[competence_month_p] AS [competence_month.presence],f.[competence_month_w] AS [competence_month.wire],f.[competence_month_raw] AS [competence_month.raw],f.[competence_year_p] AS [competence_year.presence],f.[competence_year_w] AS [competence_year.wire],f.[competence_year_raw] AS [competence_year.raw],f.[created_at_p] AS [created_at.presence],f.[created_at_w] AS [created_at.wire],f.[created_at_raw] AS [created_at.raw],f.[discount_value_p] AS [discount_value.presence],f.[discount_value_w] AS [discount_value.wire],f.[discount_value_raw] AS [discount_value.raw],f.[document_p] AS [document.presence],f.[document_w] AS [document.wire],f.[document_raw] AS [document.raw],f.[interest_value_p] AS [interest_value.presence],f.[interest_value_w] AS [interest_value.wire],f.[interest_value_raw] AS [interest_value.raw],f.[issue_date_p] AS [issue_date.presence],f.[issue_date_w] AS [issue_date.wire],f.[issue_date_raw] AS [issue_date.raw],f.[paid_p] AS [paid.presence],f.[paid_w] AS [paid.wire],f.[paid_raw] AS [paid.raw],f.[paid_value_p] AS [paid_value.presence],f.[paid_value_w] AS [paid_value.wire],f.[paid_value_raw] AS [paid_value.raw],f.[type_p] AS [type.presence],f.[type_w] AS [type.wire],f.[type_raw] AS [type.raw],f.[value_p] AS [value.presence],f.[value_w] AS [value.wire],f.[value_raw] AS [value.raw],f.[value_to_pay_p] AS [value_to_pay.presence],f.[value_to_pay_w] AS [value_to_pay.wire],f.[value_to_pay_raw] AS [value_to_pay.raw] FOR JSON PATH,INCLUDE_NULL_VALUES,WITHOUT_ARRAY_WRAPPER) AS [Metadata],
 capture.sealed_at AS [Data de extracao],
 g.run_id,CONVERT(NVARCHAR(256),CONCAT(i.root_type COLLATE Latin1_General_100_BIN2,N':',i.root_key)) source_key,i.root_id,i.component_id,i.execution_id,i.occurrence,i.observation_id,
 dates.business_date,selection.revision reference_revision,selection.reference_release_id,branch.binding_id branch_binding_id,
 i.currency,i.unit,i.root_amount,i.part_amount,i.allocation_amount,
 policy.label_release,account.binding_id account_binding_id,f.ant_ils_pas_ant_name account_name_raw,
 CONVERT(VARCHAR(40),CASE WHEN i.unresolved_conflict=1 THEN 'SOURCE_CONFLICT' WHEN dates.business_date IS NULL THEN 'BUSINESS_DATE_MISSING'
 WHEN selection.reference_release_id IS NULL THEN 'REFERENCE_MISSING' WHEN ISNULL(branch.disposition,'MISSING')<>'RESOLVED' THEN 'BRANCH_UNRESOLVED'
 WHEN policy.label_release IS NULL THEN 'FINANCIAL_LABELS_MISSING' WHEN ISNULL(account.disposition,'MISSING')<>'RESOLVED' THEN 'ACCOUNT_UNRESOLVED'
 ELSE 'READY' END) disposition
 FROM ctl.analytic_lab_source_group g JOIN ctl.analytic_lab_run run ON run.run_id=g.run_id
 JOIN ctl.expansion_lab_run er ON er.run_id=g.expansion_run
 JOIN core.expansion_lab_current_input i ON i.run_id=g.expansion_run AND i.vertical='CAP'
 JOIN stg.expansion_lab_cap f ON f.execution_id=i.execution_id AND f.occurrence=i.occurrence
 JOIN ctl.expansion_lab_capture capture ON capture.execution_id=i.execution_id
 CROSS APPLY(SELECT CASE run.zone_id WHEN 'UTC' THEN 'UTC' ELSE 'E. South America Standard Time' END name) zone
 CROSS APPLY(SELECT f.issue_date business_date) dates
 LEFT JOIN ctl.analytic_lab_reference_selection selection ON selection.run_id=g.run_id AND selection.valid_from<=dates.business_date AND selection.valid_to_exclusive>dates.business_date
 CROSS APPLY(SELECT p.* FROM pub.ufn_expansion_cap(g.expansion_run,selection.revision) p WHERE p.component_id=i.component_id) policy
 OUTER APPLY(SELECT * FROM ref.ufn_analytic_dimension(g.run_id,selection.revision,dates.business_date) d WHERE d.entity='CAP' AND d.source_key=CONCAT(i.root_type COLLATE Latin1_General_100_BIN2,N':',i.root_key) COLLATE Latin1_General_100_BIN2 AND d.role='BRANCH') branch
 OUTER APPLY(SELECT * FROM ref.ufn_analytic_dimension(g.run_id,selection.revision,dates.business_date) d WHERE d.entity='CAP' AND d.source_key=CONCAT(i.root_type COLLATE Latin1_General_100_BIN2,N':',i.root_key) COLLATE Latin1_General_100_BIN2 AND d.role='ACCOUNT') account;
GO
CREATE VIEW pub.analytic_lab_sql_06 AS SELECT * FROM core.analytic_lab_contas_a_pagar_projection WHERE disposition='READY';
GO
