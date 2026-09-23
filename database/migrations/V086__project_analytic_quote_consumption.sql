-- PUB03: actual effective snapshots, explicit branch and currently valid governed tariff.
SET ANSI_NULLS ON;SET QUOTED_IDENTIFIER ON;
GO
CREATE VIEW core.analytic_lab_quote_projection AS
 SELECT TRY_CONVERT(BIGINT,f.sequence_code) AS [N° Cotação],
 core.ufn_analytic_raster_time(f.[requested_at],f.[requested_at_nano],0) AT TIME ZONE zone.name AS [Data Cotação],
 CONVERT(TIME(7),core.ufn_analytic_raster_time(f.[requested_at],f.[requested_at_nano],0) AT TIME ZONE zone.name) AS [Hora (Solicitacao)],
 pointer.latest_extracted_at AS [Data de extracao],
 branch.normalized_name AS [Filial],
 branch.normalized_name AS [Unidade],
 branch.normalized_name AS [Unidade_Origem],
 f.requester_name AS [Solicitante],
 f.qoe_uer_name AS [Usuário],
 f.user_normalized AS [Usuario Key],
 f.qoe_uer_name AS [Setor_Usuario],
 f.qoe_crn_psn_name AS [Empresa],
 f.qoe_cor_name AS [Cliente Pagador],
 f.qoe_cor_document AS [CNPJ/CPF Cliente],
 f.qoe_cor_nickname AS [Pagador/Nome fantasia],
 f.qoe_cor_name AS [Cliente Grupo],
 COALESCE(f.qoe_cor_nickname,f.qoe_cor_name) AS [Cliente],
 f.qoe_qes_ony_name AS [Cidade Origem],
 f.qoe_qes_ony_sae_code AS [UF Origem],
 f.qoe_qes_origin_postal_code AS [CEP Origem],
 CONCAT(f.qoe_qes_ony_name,N' - ',f.qoe_qes_ony_sae_code) AS [Origem],
 f.qoe_qes_diy_name AS [Cidade Destino],
 f.qoe_qes_diy_sae_code AS [UF Destino],
 f.qoe_qes_destination_postal_code AS [CEP Destino],
 CONCAT(f.qoe_qes_diy_name,N' - ',f.qoe_qes_diy_sae_code) AS [Destino],
 CONCAT(f.qoe_qes_ony_name,N' - ',f.qoe_qes_ony_sae_code,N' x ',f.qoe_qes_diy_name,N' - ',f.qoe_qes_diy_sae_code) AS [Trecho],
 TRY_CONVERT(BIGINT,f.qoe_qes_invoices_volumes) AS [Volume],
 f.qoe_qes_real_weight AS [Peso real],
 f.qoe_qes_taxed_weight AS [Peso taxado],
 f.qoe_qes_invoices_value AS [Valor NF],
 f.qoe_qes_total AS [Valor frete],
 CONVERT(DECIMAL(28,8),f.tariff_minimum) AS [Min. Frete/KG],
 f.qoe_qes_cre_name AS [Tabela],
 f.qoe_qes_fon_name AS [Tipo de operação],
 (SELECT f.source_execution,f.stage_record_id,f.snapshot_id,f.previous_snapshot,a.[requested_at_p] [requested_at.presence],a.[requested_at_w] [requested_at.wire],a.[requested_at_raw] [requested_at.raw],a.[sequence_code_p] [sequence_code.presence],a.[sequence_code_w] [sequence_code.wire],a.[sequence_code_raw] [sequence_code.raw],a.[qoe_qes_fon_name_p] [qoe_qes_fon_name.presence],a.[qoe_qes_fon_name_w] [qoe_qes_fon_name.wire],a.[qoe_qes_fon_name_raw] [qoe_qes_fon_name.raw],a.[qoe_cor_document_p] [qoe_cor_document.presence],a.[qoe_cor_document_w] [qoe_cor_document.wire],a.[qoe_cor_document_raw] [qoe_cor_document.raw],a.[qoe_cor_name_p] [qoe_cor_name.presence],a.[qoe_cor_name_w] [qoe_cor_name.wire],a.[qoe_cor_name_raw] [qoe_cor_name.raw],a.[qoe_qes_ony_name_p] [qoe_qes_ony_name.presence],a.[qoe_qes_ony_name_w] [qoe_qes_ony_name.wire],a.[qoe_qes_ony_name_raw] [qoe_qes_ony_name.raw],a.[qoe_qes_ony_sae_code_p] [qoe_qes_ony_sae_code.presence],a.[qoe_qes_ony_sae_code_w] [qoe_qes_ony_sae_code.wire],a.[qoe_qes_ony_sae_code_raw] [qoe_qes_ony_sae_code.raw],a.[qoe_qes_diy_name_p] [qoe_qes_diy_name.presence],a.[qoe_qes_diy_name_w] [qoe_qes_diy_name.wire],a.[qoe_qes_diy_name_raw] [qoe_qes_diy_name.raw],a.[qoe_qes_diy_sae_code_p] [qoe_qes_diy_sae_code.presence],a.[qoe_qes_diy_sae_code_w] [qoe_qes_diy_sae_code.wire],a.[qoe_qes_diy_sae_code_raw] [qoe_qes_diy_sae_code.raw],a.[qoe_qes_cre_name_p] [qoe_qes_cre_name.presence],a.[qoe_qes_cre_name_w] [qoe_qes_cre_name.wire],a.[qoe_qes_cre_name_raw] [qoe_qes_cre_name.raw],a.[qoe_qes_invoices_volumes_p] [qoe_qes_invoices_volumes.presence],a.[qoe_qes_invoices_volumes_w] [qoe_qes_invoices_volumes.wire],a.[qoe_qes_invoices_volumes_raw] [qoe_qes_invoices_volumes.raw],a.[qoe_qes_taxed_weight_p] [qoe_qes_taxed_weight.presence],a.[qoe_qes_taxed_weight_w] [qoe_qes_taxed_weight.wire],a.[qoe_qes_taxed_weight_raw] [qoe_qes_taxed_weight.raw],a.[qoe_qes_invoices_value_p] [qoe_qes_invoices_value.presence],a.[qoe_qes_invoices_value_w] [qoe_qes_invoices_value.wire],a.[qoe_qes_invoices_value_raw] [qoe_qes_invoices_value.raw],a.[qoe_qes_total_p] [qoe_qes_total.presence],a.[qoe_qes_total_w] [qoe_qes_total.wire],a.[qoe_qes_total_raw] [qoe_qes_total.raw],a.[qoe_qes_fit_fhe_cte_issued_at_p] [qoe_qes_fit_fhe_cte_issued_at.presence],a.[qoe_qes_fit_fhe_cte_issued_at_w] [qoe_qes_fit_fhe_cte_issued_at.wire],a.[qoe_qes_fit_fhe_cte_issued_at_raw] [qoe_qes_fit_fhe_cte_issued_at.raw],a.[qoe_qes_fit_nse_issued_at_p] [qoe_qes_fit_nse_issued_at.presence],a.[qoe_qes_fit_nse_issued_at_w] [qoe_qes_fit_nse_issued_at.wire],a.[qoe_qes_fit_nse_issued_at_raw] [qoe_qes_fit_nse_issued_at.raw],a.[qoe_uer_name_p] [qoe_uer_name.presence],a.[qoe_uer_name_w] [qoe_uer_name.wire],a.[qoe_uer_name_raw] [qoe_uer_name.raw],a.[qoe_crn_psn_nickname_p] [qoe_crn_psn_nickname.presence],a.[qoe_crn_psn_nickname_w] [qoe_crn_psn_nickname.wire],a.[qoe_crn_psn_nickname_raw] [qoe_crn_psn_nickname.raw],a.[qoe_qes_sdr_document_p] [qoe_qes_sdr_document.presence],a.[qoe_qes_sdr_document_w] [qoe_qes_sdr_document.wire],a.[qoe_qes_sdr_document_raw] [qoe_qes_sdr_document.raw],a.[qoe_qes_sdr_nickname_p] [qoe_qes_sdr_nickname.presence],a.[qoe_qes_sdr_nickname_w] [qoe_qes_sdr_nickname.wire],a.[qoe_qes_sdr_nickname_raw] [qoe_qes_sdr_nickname.raw],a.[qoe_qes_rpt_document_p] [qoe_qes_rpt_document.presence],a.[qoe_qes_rpt_document_w] [qoe_qes_rpt_document.wire],a.[qoe_qes_rpt_document_raw] [qoe_qes_rpt_document.raw],a.[qoe_qes_rpt_nickname_p] [qoe_qes_rpt_nickname.presence],a.[qoe_qes_rpt_nickname_w] [qoe_qes_rpt_nickname.wire],a.[qoe_qes_rpt_nickname_raw] [qoe_qes_rpt_nickname.raw],a.[qoe_qes_origin_postal_code_p] [qoe_qes_origin_postal_code.presence],a.[qoe_qes_origin_postal_code_w] [qoe_qes_origin_postal_code.wire],a.[qoe_qes_origin_postal_code_raw] [qoe_qes_origin_postal_code.raw],a.[qoe_qes_destination_postal_code_p] [qoe_qes_destination_postal_code.presence],a.[qoe_qes_destination_postal_code_w] [qoe_qes_destination_postal_code.wire],a.[qoe_qes_destination_postal_code_raw] [qoe_qes_destination_postal_code.raw],a.[qoe_qes_real_weight_p] [qoe_qes_real_weight.presence],a.[qoe_qes_real_weight_w] [qoe_qes_real_weight.wire],a.[qoe_qes_real_weight_raw] [qoe_qes_real_weight.raw],a.[qoe_qes_disapprove_comments_p] [qoe_qes_disapprove_comments.presence],a.[qoe_qes_disapprove_comments_w] [qoe_qes_disapprove_comments.wire],a.[qoe_qes_disapprove_comments_raw] [qoe_qes_disapprove_comments.raw],a.[qoe_qes_freight_comments_p] [qoe_qes_freight_comments.presence],a.[qoe_qes_freight_comments_w] [qoe_qes_freight_comments.wire],a.[qoe_qes_freight_comments_raw] [qoe_qes_freight_comments.raw],a.[qoe_qes_fit_fdt_subtotal_p] [qoe_qes_fit_fdt_subtotal.presence],a.[qoe_qes_fit_fdt_subtotal_w] [qoe_qes_fit_fdt_subtotal.wire],a.[qoe_qes_fit_fdt_subtotal_raw] [qoe_qes_fit_fdt_subtotal.raw],a.[requester_name_p] [requester_name.presence],a.[requester_name_w] [requester_name.wire],a.[requester_name_raw] [requester_name.raw],a.[qoe_qes_itr_subtotal_p] [qoe_qes_itr_subtotal.presence],a.[qoe_qes_itr_subtotal_w] [qoe_qes_itr_subtotal.wire],a.[qoe_qes_itr_subtotal_raw] [qoe_qes_itr_subtotal.raw],a.[qoe_qes_tde_subtotal_p] [qoe_qes_tde_subtotal.presence],a.[qoe_qes_tde_subtotal_w] [qoe_qes_tde_subtotal.wire],a.[qoe_qes_tde_subtotal_raw] [qoe_qes_tde_subtotal.raw],a.[qoe_qes_collect_subtotal_p] [qoe_qes_collect_subtotal.presence],a.[qoe_qes_collect_subtotal_w] [qoe_qes_collect_subtotal.wire],a.[qoe_qes_collect_subtotal_raw] [qoe_qes_collect_subtotal.raw],a.[qoe_qes_delivery_subtotal_p] [qoe_qes_delivery_subtotal.presence],a.[qoe_qes_delivery_subtotal_w] [qoe_qes_delivery_subtotal.wire],a.[qoe_qes_delivery_subtotal_raw] [qoe_qes_delivery_subtotal.raw],a.[qoe_qes_other_fees_p] [qoe_qes_other_fees.presence],a.[qoe_qes_other_fees_w] [qoe_qes_other_fees.wire],a.[qoe_qes_other_fees_raw] [qoe_qes_other_fees.raw],a.[qoe_crn_psn_name_p] [qoe_crn_psn_name.presence],a.[qoe_crn_psn_name_w] [qoe_crn_psn_name.wire],a.[qoe_crn_psn_name_raw] [qoe_crn_psn_name.raw],a.[qoe_cor_nickname_p] [qoe_cor_nickname.presence],a.[qoe_cor_nickname_w] [qoe_cor_nickname.wire],a.[qoe_cor_nickname_raw] [qoe_cor_nickname.raw] FOR JSON PATH,INCLUDE_NULL_VALUES,WITHOUT_ARRAY_WRAPPER) AS [Metadata],
 CASE WHEN f.qoe_qes_fit_fhe_cte_issued_at IS NOT NULL OR f.qoe_qes_fit_nse_issued_at IS NOT NULL THEN N'Convertida' WHEN LEN(f.qoe_qes_disapprove_comments)>0 THEN N'Reprovada' ELSE N'Pendente' END COLLATE Latin1_General_100_BIN2 AS [Status Conversão],
 CASE WHEN f.qoe_qes_fit_fhe_cte_issued_at IS NOT NULL OR f.qoe_qes_fit_nse_issued_at IS NOT NULL THEN N'Convertida' WHEN LEN(f.qoe_qes_disapprove_comments)>0 THEN N'Reprovada' ELSE N'Pendente' END COLLATE Latin1_General_100_BIN2 AS [Status_Sistema],
 CASE WHEN f.qoe_qes_fit_fhe_cte_issued_at IS NOT NULL THEN N'Emitido' ELSE N'Pendente' END COLLATE Latin1_General_100_BIN2 AS [Status_Sistema_CTe],
 CASE WHEN f.qoe_qes_fit_nse_issued_at IS NOT NULL THEN N'Emitida' ELSE N'Pendente' END COLLATE Latin1_General_100_BIN2 AS [Status_Sistema_NFSe],
 CASE WHEN f.qoe_qes_fit_fhe_cte_issued_at IS NOT NULL THEN N'Sim' ELSE N'Não' END COLLATE Latin1_General_100_BIN2 AS [Refino_CTe],
 f.qoe_qes_disapprove_comments AS [Motivo Perda],
 f.qoe_qes_freight_comments AS [Observações para o frete],
 core.ufn_analytic_raster_time(f.[qoe_qes_fit_fhe_cte_issued_at],f.[qoe_qes_fit_fhe_cte_issued_at_nano],0) AT TIME ZONE zone.name AS [CT-e/Data de emissão],
 core.ufn_analytic_raster_time(f.[qoe_qes_fit_nse_issued_at],f.[qoe_qes_fit_nse_issued_at_nano],0) AT TIME ZONE zone.name AS [Nfse/Data de emissão],
 f.qoe_qes_sdr_document AS [Remetente/CNPJ],
 f.qoe_qes_sdr_nickname AS [Remetente/Nome fantasia],
 f.qoe_qes_rpt_document AS [Destinatário/CNPJ],
 f.qoe_qes_rpt_nickname AS [Destinatário/Nome fantasia],
 f.qoe_qes_fit_fdt_subtotal AS [Descontos/Subtotal parcelas],
 f.qoe_qes_itr_subtotal AS [Trechos/ITR],
 f.qoe_qes_tde_subtotal AS [Trechos/TDE],
 f.qoe_qes_collect_subtotal AS [Trechos/Coleta],
 f.qoe_qes_delivery_subtotal AS [Trechos/Entrega],
 f.qoe_qes_other_fees AS [Trechos/Outros valores],
 pointer.run_id,pointer.source_key,f.snapshot_id,f.source_execution,pointer.last_observation,f.stage_record_id,
 f.business_date,selection.revision reference_revision,selection.reference_release_id,
 branch.binding_id branch_binding_id,f.qoe_crn_psn_nickname branch_raw,f.tariff_release,f.tariff_currency,f.tariff_unit,f.tariff_rounding,
 CONVERT(VARCHAR(40),CASE WHEN selection.reference_release_id IS NULL THEN 'REFERENCE_MISSING'
 WHEN ISNULL(branch.disposition,'MISSING')<>'RESOLVED' THEN 'BRANCH_UNRESOLVED'
 WHEN tariff.reference_release_id IS NULL THEN 'TARIFF_RELEASE_UNRESOLVED' ELSE 'READY' END) disposition
 FROM core.analytic_quote_current pointer JOIN core.analytic_quote_snapshot f ON f.snapshot_id=pointer.snapshot_id
 JOIN stg.analytic_quote_attributes a ON a.stage_record_id=f.stage_record_id
 JOIN ctl.analytic_lab_run run ON run.run_id=pointer.run_id
 CROSS APPLY(SELECT CASE run.zone_id WHEN 'UTC' THEN 'UTC' ELSE 'E. South America Standard Time' END name) zone
 LEFT JOIN ctl.analytic_lab_reference_selection selection ON selection.run_id=pointer.run_id
 AND selection.valid_from<=f.business_date AND selection.valid_to_exclusive>f.business_date
 OUTER APPLY(SELECT * FROM ref.ufn_analytic_dimension(pointer.run_id,selection.revision,f.business_date) d
 WHERE d.entity='COT' AND d.source_key=pointer.source_key AND d.role='BRANCH') branch
 OUTER APPLY(SELECT * FROM ref.ufn_analytic_quote_tariff(pointer.run_id,selection.revision,f.business_date) t
 WHERE t.reference_release_id=f.tariff_release) tariff;
GO
CREATE VIEW pub.analytic_lab_sql_05 AS SELECT * FROM core.analytic_lab_quote_projection WHERE disposition='READY';
GO
