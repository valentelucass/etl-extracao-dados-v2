package br.com.esl.etl.v2.plataforma.persistencia.analitico;

import br.com.esl.etl.v2.plataforma.analitico.FreightSupplementObservation;
import br.com.esl.etl.v2.plataforma.expansao.ExpansionValue;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken;
import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import javax.sql.DataSource;

/**
 * A bounded source-attribute batch, separate from the eleven capture pipelines and all final facts.
 */
public final class JdbcFreightAnalyticAttributes {
    private final DataSource source;
    private static final String SQL =
            "INSERT stg.analytic_freight_attributes(run_id,source_execution,source_key,freight_sta"
                    + "ge_id,base_stage_id,revision,evidence,valid,comparison_bytes,modal_p,modal_w,modal_ra"
                    + "w,modal,modal_issue,tipo_frete_p,tipo_frete_w,tipo_frete_raw,tipo_frete,tipo_frete_is"
                    + "sue,accounting_credit_id_p,accounting_credit_id_w,accounting_credit_id_raw,accounting"
                    + "_credit_id,accounting_credit_id_issue,accounting_credit_installment_id_p,accounting_c"
                    + "redit_installment_id_w,accounting_credit_installment_id_raw,accounting_credit_install"
                    + "ment_id,accounting_credit_installment_id_issue,valor_notas_p,valor_notas_w,valor_nota"
                    + "s_raw,valor_notas,valor_notas_issue,peso_notas_p,peso_notas_w,peso_notas_raw,peso_not"
                    + "as,peso_notas_issue,id_corporacao_p,id_corporacao_w,id_corporacao_raw,id_corporacao,i"
                    + "d_corporacao_issue,id_cidade_destino_p,id_cidade_destino_w,id_cidade_destino_raw,id_c"
                    + "idade_destino,id_cidade_destino_issue,data_previsao_entrega_p,data_previsao_entrega_w"
                    + ",data_previsao_entrega_raw,data_previsao_entrega,data_previsao_entrega_issue,service_"
                    + "date_p,service_date_w,service_date_raw,service_date,service_date_issue,pick_item_id_p"
                    + ",pick_item_id_w,pick_item_id_raw,pick_item_id,pick_item_id_issue,pagador_id_p,pagador"
                    + "_id_w,pagador_id_raw,pagador_id,pagador_id_issue,pagador_nome_p,pagador_nome_w,pagado"
                    + "r_nome_raw,pagador_nome,pagador_nome_issue,remetente_id_p,remetente_id_w,remetente_id"
                    + "_raw,remetente_id,remetente_id_issue,remetente_nome_p,remetente_nome_w,remetente_nome"
                    + "_raw,remetente_nome,remetente_nome_issue,origem_cidade_p,origem_cidade_w,origem_cidad"
                    + "e_raw,origem_cidade,origem_cidade_issue,origem_uf_p,origem_uf_w,origem_uf_raw,origem_"
                    + "uf,origem_uf_issue,destinatario_id_p,destinatario_id_w,destinatario_id_raw,destinatar"
                    + "io_id,destinatario_id_issue,destinatario_nome_p,destinatario_nome_w,destinatario_nome"
                    + "_raw,destinatario_nome,destinatario_nome_issue,destino_cidade_p,destino_cidade_w,dest"
                    + "ino_cidade_raw,destino_cidade,destino_cidade_issue,destino_uf_p,destino_uf_w,destino_"
                    + "uf_raw,destino_uf,destino_uf_issue,filial_nome_p,filial_nome_w,filial_nome_raw,filial"
                    + "_nome,filial_nome_issue,filial_apelido_p,filial_apelido_w,filial_apelido_raw,filial_a"
                    + "pelido,filial_apelido_issue,numero_nota_fiscal_p,numero_nota_fiscal_w,numero_nota_fis"
                    + "cal_raw,numero_nota_fiscal,numero_nota_fiscal_issue,tabela_preco_nome_p,tabela_preco_"
                    + "nome_w,tabela_preco_nome_raw,tabela_preco_nome,tabela_preco_nome_issue,classificacao_"
                    + "nome_p,classificacao_nome_w,classificacao_nome_raw,classificacao_nome,classificacao_n"
                    + "ome_issue,centro_custo_nome_p,centro_custo_nome_w,centro_custo_nome_raw,centro_custo_"
                    + "nome,centro_custo_nome_issue,usuario_nome_p,usuario_nome_w,usuario_nome_raw,usuario_n"
                    + "ome,usuario_nome_issue,invoices_total_volumes_p,invoices_total_volumes_w,invoices_tot"
                    + "al_volumes_raw,invoices_total_volumes,invoices_total_volumes_issue,taxed_weight_p,tax"
                    + "ed_weight_w,taxed_weight_raw,taxed_weight,taxed_weight_issue,real_weight_p,real_weigh"
                    + "t_w,real_weight_raw,real_weight,real_weight_issue,total_cubic_volume_p,total_cubic_vo"
                    + "lume_w,total_cubic_volume_raw,total_cubic_volume,total_cubic_volume_issue,subtotal_p,"
                    + "subtotal_w,subtotal_raw,subtotal,subtotal_issue,chave_cte_p,chave_cte_w,chave_cte_raw"
                    + ",chave_cte,chave_cte_issue,numero_cte_p,numero_cte_w,numero_cte_raw,numero_cte,numero"
                    + "_cte_issue,serie_cte_p,serie_cte_w,serie_cte_raw,serie_cte,serie_cte_issue,cte_id_p,c"
                    + "te_id_w,cte_id_raw,cte_id,cte_id_issue,cte_emission_type_p,cte_emission_type_w,cte_em"
                    + "ission_type_raw,cte_emission_type,cte_emission_type_issue,service_type_p,service_type"
                    + "_w,service_type_raw,service_type,service_type_issue,insurance_enabled_p,insurance_ena"
                    + "bled_w,insurance_enabled_raw,insurance_enabled,insurance_enabled_issue,gris_subtotal_"
                    + "p,gris_subtotal_w,gris_subtotal_raw,gris_subtotal,gris_subtotal_issue,tde_subtotal_p,"
                    + "tde_subtotal_w,tde_subtotal_raw,tde_subtotal,tde_subtotal_issue,modal_cte_p,modal_cte"
                    + "_w,modal_cte_raw,modal_cte,modal_cte_issue,redispatch_subtotal_p,redispatch_subtotal_"
                    + "w,redispatch_subtotal_raw,redispatch_subtotal,redispatch_subtotal_issue,suframa_subto"
                    + "tal_p,suframa_subtotal_w,suframa_subtotal_raw,suframa_subtotal,suframa_subtotal_issue"
                    + ",payment_type_p,payment_type_w,payment_type_raw,payment_type,payment_type_issue,previ"
                    + "ous_document_type_p,previous_document_type_w,previous_document_type_raw,previous_docu"
                    + "ment_type,previous_document_type_issue,products_value_p,products_value_w,products_val"
                    + "ue_raw,products_value,products_value_issue,trt_subtotal_p,trt_subtotal_w,trt_subtotal"
                    + "_raw,trt_subtotal,trt_subtotal_issue,nfse_series_p,nfse_series_w,nfse_series_raw,nfse"
                    + "_series,nfse_series_issue,nfse_number_p,nfse_number_w,nfse_number_raw,nfse_number,nfs"
                    + "e_number_issue,insurance_id_p,insurance_id_w,insurance_id_raw,insurance_id,insurance_"
                    + "id_issue,other_fees_p,other_fees_w,other_fees_raw,other_fees,other_fees_issue,km_p,km"
                    + "_w,km_raw,km,km_issue,payment_accountable_type_p,payment_accountable_type_w,payment_a"
                    + "ccountable_type_raw,payment_accountable_type,payment_accountable_type_issue,insured_v"
                    + "alue_p,insured_value_w,insured_value_raw,insured_value,insured_value_issue,globalized"
                    + "_p,globalized_w,globalized_raw,globalized,globalized_issue,sec_cat_subtotal_p,sec_cat"
                    + "_subtotal_w,sec_cat_subtotal_raw,sec_cat_subtotal,sec_cat_subtotal_issue,globalized_t"
                    + "ype_p,globalized_type_w,globalized_type_raw,globalized_type,globalized_type_issue,pri"
                    + "ce_table_accountable_type_p,price_table_accountable_type_w,price_table_accountable_ty"
                    + "pe_raw,price_table_accountable_type,price_table_accountable_type_issue,insurance_acco"
                    + "untable_type_p,insurance_accountable_type_w,insurance_accountable_type_raw,insurance_"
                    + "accountable_type,insurance_accountable_type_issue,fiscal_calculation_basis_p,fiscal_c"
                    + "alculation_basis_w,fiscal_calculation_basis_raw,fiscal_calculation_basis,fiscal_calcu"
                    + "lation_basis_issue,fiscal_tax_rate_p,fiscal_tax_rate_w,fiscal_tax_rate_raw,fiscal_tax"
                    + "_rate,fiscal_tax_rate_issue,fiscal_pis_rate_p,fiscal_pis_rate_w,fiscal_pis_rate_raw,f"
                    + "iscal_pis_rate,fiscal_pis_rate_issue,fiscal_cofins_rate_p,fiscal_cofins_rate_w,fiscal"
                    + "_cofins_rate_raw,fiscal_cofins_rate,fiscal_cofins_rate_issue,fiscal_has_difal_p,fisca"
                    + "l_has_difal_w,fiscal_has_difal_raw,fiscal_has_difal,fiscal_has_difal_issue,fiscal_dif"
                    + "al_origin_p,fiscal_difal_origin_w,fiscal_difal_origin_raw,fiscal_difal_origin,fiscal_"
                    + "difal_origin_issue,fiscal_difal_destination_p,fiscal_difal_destination_w,fiscal_difal"
                    + "_destination_raw,fiscal_difal_destination,fiscal_difal_destination_issue,fiscal_cst_t"
                    + "ype_p,fiscal_cst_type_w,fiscal_cst_type_raw,fiscal_cst_type,fiscal_cst_type_issue,fis"
                    + "cal_cfop_code_p,fiscal_cfop_code_w,fiscal_cfop_code_raw,fiscal_cfop_code,fiscal_cfop_"
                    + "code_issue,fiscal_tax_value_p,fiscal_tax_value_w,fiscal_tax_value_raw,fiscal_tax_valu"
                    + "e,fiscal_tax_value_issue,fiscal_pis_value_p,fiscal_pis_value_w,fiscal_pis_value_raw,f"
                    + "iscal_pis_value,fiscal_pis_value_issue,fiscal_cofins_value_p,fiscal_cofins_value_w,fi"
                    + "scal_cofins_value_raw,fiscal_cofins_value,fiscal_cofins_value_issue,cubages_cubed_wei"
                    + "ght_p,cubages_cubed_weight_w,cubages_cubed_weight_raw,cubages_cubed_weight,cubages_cu"
                    + "bed_weight_issue,freight_weight_subtotal_p,freight_weight_subtotal_w,freight_weight_s"
                    + "ubtotal_raw,freight_weight_subtotal,freight_weight_subtotal_issue,ad_valorem_subtotal"
                    + "_p,ad_valorem_subtotal_w,ad_valorem_subtotal_raw,ad_valorem_subtotal,ad_valorem_subto"
                    + "tal_issue,toll_subtotal_p,toll_subtotal_w,toll_subtotal_raw,toll_subtotal,toll_subtot"
                    + "al_issue,itr_subtotal_p,itr_subtotal_w,itr_subtotal_raw,itr_subtotal,itr_subtotal_iss"
                    + "ue,pagador_documento_p,pagador_documento_w,pagador_documento_raw,pagador_documento,pa"
                    + "gador_documento_issue,remetente_documento_p,remetente_documento_w,remetente_documento"
                    + "_raw,remetente_documento,remetente_documento_issue,destinatario_documento_p,destinata"
                    + "rio_documento_w,destinatario_documento_raw,destinatario_documento,destinatario_docume"
                    + "nto_issue,filial_cnpj_p,filial_cnpj_w,filial_cnpj_raw,filial_cnpj,filial_cnpj_issue,n"
                    + "fse_integration_id_p,nfse_integration_id_w,nfse_integration_id_raw,nfse_integration_i"
                    + "d,nfse_integration_id_issue,nfse_status_p,nfse_status_w,nfse_status_raw,nfse_status,n"
                    + "fse_status_issue,nfse_issued_at_p,nfse_issued_at_w,nfse_issued_at_raw,nfse_issued_at,"
                    + "nfse_issued_at_issue,nfse_cancelation_reason_p,nfse_cancelation_reason_w,nfse_cancela"
                    + "tion_reason_raw,nfse_cancelation_reason,nfse_cancelation_reason_issue,nfse_pdf_servic"
                    + "e_url_p,nfse_pdf_service_url_w,nfse_pdf_service_url_raw,nfse_pdf_service_url,nfse_pdf"
                    + "_service_url_issue,nfse_corporation_id_p,nfse_corporation_id_w,nfse_corporation_id_ra"
                    + "w,nfse_corporation_id,nfse_corporation_id_issue,nfse_service_description_p,nfse_servi"
                    + "ce_description_w,nfse_service_description_raw,nfse_service_description,nfse_service_d"
                    + "escription_issue,nfse_xml_document_p,nfse_xml_document_w,nfse_xml_document_raw,nfse_x"
                    + "ml_document,nfse_xml_document_issue) SELECT ?,?,?,o.stage_record_id,d.stage_record_id"
                    + ",?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,"
                    + "?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?"
                    + ",?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,"
                    + "?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?"
                    + ",?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,"
                    + "?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?"
                    + ",?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,"
                    + "?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?"
                    + ",?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,"
                    + "?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?"
                    + ",?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,? FROM stg.expansion_lab_dep"
                    + "endency_observation o JOIN ctl.expansion_lab_dependency_capture c ON c.execution_id=o"
                    + ".execution_id AND c.state='COMPLETE' JOIN ctl.analytic_lab_source_group g ON g.expans"
                    + "ion_run=o.run_id JOIN core.expansion_lab_dependency d ON d.run_id=o.run_id AND d.enti"
                    + "ty='FRETE' AND d.source_key=o.source_key AND d.state='VALID' WHERE g.run_id=? AND o.e"
                    + "xecution_id=? AND o.source_key=? AND o.entity='FRETE' AND o.disposition IN('INSERTED'"
                    + ",'UPDATED','NOOP')";

    public JdbcFreightAnalyticAttributes(final DataSource source) {
        this.source = Objects.requireNonNull(source);
    }

    public int captureBatch(
            final UUID run,
            final UUID execution,
            final List<FreightSupplementObservation> rows,
            final CancellationToken cancellation)
            throws SQLException {
        if (rows == null || rows.isEmpty() || rows.size() > 16) {
            throw new IllegalArgumentException("ANA_FREIGHT_ATTRIBUTES_BATCH_BOUND");
        }
        cancellation.throwIfCancellationRequested();
        try (var connection = source.getConnection();
                var sql = connection.prepareStatement(SQL)) {
            sql.setQueryTimeout(15);
            final var before = connection.setSavepoint();
            try {
                for (final var row : rows) {
                    final var attributes = row.attributes();
                    final var comparison = new AnalyticFieldComparison();
                    comparison.text("synthetic-freight-attributes-v1");
                    comparison.value(attributes.modal());
                    comparison.value(attributes.tipoFrete());
                    comparison.value(attributes.accountingCreditId());
                    comparison.value(attributes.accountingCreditInstallmentId());
                    comparison.value(attributes.valorNotas());
                    comparison.value(attributes.pesoNotas());
                    comparison.value(attributes.idCorporacao());
                    comparison.value(attributes.idCidadeDestino());
                    comparison.value(attributes.dataPrevisaoEntrega());
                    comparison.value(attributes.serviceDate());
                    comparison.value(attributes.pickItemId());
                    comparison.value(attributes.pagadorId());
                    comparison.value(attributes.pagadorNome());
                    comparison.value(attributes.remetenteId());
                    comparison.value(attributes.remetenteNome());
                    comparison.value(attributes.origemCidade());
                    comparison.value(attributes.origemUf());
                    comparison.value(attributes.destinatarioId());
                    comparison.value(attributes.destinatarioNome());
                    comparison.value(attributes.destinoCidade());
                    comparison.value(attributes.destinoUf());
                    comparison.value(attributes.filialNome());
                    comparison.value(attributes.filialApelido());
                    comparison.value(attributes.numeroNotaFiscal());
                    comparison.value(attributes.tabelaPrecoNome());
                    comparison.value(attributes.classificacaoNome());
                    comparison.value(attributes.centroCustoNome());
                    comparison.value(attributes.usuarioNome());
                    comparison.value(attributes.invoicesTotalVolumes());
                    comparison.value(attributes.taxedWeight());
                    comparison.value(attributes.realWeight());
                    comparison.value(attributes.totalCubicVolume());
                    comparison.value(attributes.subtotal());
                    comparison.value(attributes.chaveCte());
                    comparison.value(attributes.numeroCte());
                    comparison.value(attributes.serieCte());
                    comparison.value(attributes.cteId());
                    comparison.value(attributes.cteEmissionType());
                    comparison.value(attributes.serviceType());
                    comparison.value(attributes.insuranceEnabled());
                    comparison.value(attributes.grisSubtotal());
                    comparison.value(attributes.tdeSubtotal());
                    comparison.value(attributes.modalCte());
                    comparison.value(attributes.redispatchSubtotal());
                    comparison.value(attributes.suframaSubtotal());
                    comparison.value(attributes.paymentType());
                    comparison.value(attributes.previousDocumentType());
                    comparison.value(attributes.productsValue());
                    comparison.value(attributes.trtSubtotal());
                    comparison.value(attributes.nfseSeries());
                    comparison.value(attributes.nfseNumber());
                    comparison.value(attributes.insuranceId());
                    comparison.value(attributes.otherFees());
                    comparison.value(attributes.km());
                    comparison.value(attributes.paymentAccountableType());
                    comparison.value(attributes.insuredValue());
                    comparison.value(attributes.globalized());
                    comparison.value(attributes.secCatSubtotal());
                    comparison.value(attributes.globalizedType());
                    comparison.value(attributes.priceTableAccountableType());
                    comparison.value(attributes.insuranceAccountableType());
                    comparison.value(attributes.fiscalCalculationBasis());
                    comparison.value(attributes.fiscalTaxRate());
                    comparison.value(attributes.fiscalPisRate());
                    comparison.value(attributes.fiscalCofinsRate());
                    comparison.value(attributes.fiscalHasDifal());
                    comparison.value(attributes.fiscalDifalOrigin());
                    comparison.value(attributes.fiscalDifalDestination());
                    comparison.value(attributes.fiscalCstType());
                    comparison.value(attributes.fiscalCfopCode());
                    comparison.value(attributes.fiscalTaxValue());
                    comparison.value(attributes.fiscalPisValue());
                    comparison.value(attributes.fiscalCofinsValue());
                    comparison.value(attributes.cubagesCubedWeight());
                    comparison.value(attributes.freightWeightSubtotal());
                    comparison.value(attributes.adValoremSubtotal());
                    comparison.value(attributes.tollSubtotal());
                    comparison.value(attributes.itrSubtotal());
                    comparison.value(attributes.pagadorDocumento());
                    comparison.value(attributes.remetenteDocumento());
                    comparison.value(attributes.destinatarioDocumento());
                    comparison.value(attributes.filialCnpj());
                    comparison.value(attributes.nfseIntegrationId());
                    comparison.value(attributes.nfseStatus());
                    comparison.value(attributes.nfseIssuedAt());
                    comparison.value(attributes.nfseCancelationReason());
                    comparison.value(attributes.nfsePdfServiceUrl());
                    comparison.value(attributes.nfseCorporationId());
                    comparison.value(attributes.nfseServiceDescription());
                    comparison.value(attributes.nfseXmlDocument());
                    sql.setString(1, run.toString());
                    sql.setString(2, execution.toString());
                    sql.setString(3, row.sourceKey());
                    sql.setInt(4, row.revision());
                    sql.setString(5, row.evidence());
                    sql.setBoolean(6, attributes.valid());
                    sql.setBytes(7, comparison.bytesLimited(256 * 1024));
                    int index = 8;
                    index = stringValue(sql, index, attributes.modal());
                    index = stringValue(sql, index, attributes.tipoFrete());
                    index = longValue(sql, index, attributes.accountingCreditId());
                    index = longValue(sql, index, attributes.accountingCreditInstallmentId());
                    index = bigdecimalValue(sql, index, attributes.valorNotas());
                    index = bigdecimalValue(sql, index, attributes.pesoNotas());
                    index = longValue(sql, index, attributes.idCorporacao());
                    index = longValue(sql, index, attributes.idCidadeDestino());
                    index = localdateValue(sql, index, attributes.dataPrevisaoEntrega());
                    index = localdateValue(sql, index, attributes.serviceDate());
                    index = longValue(sql, index, attributes.pickItemId());
                    index = longValue(sql, index, attributes.pagadorId());
                    index = stringValue(sql, index, attributes.pagadorNome());
                    index = longValue(sql, index, attributes.remetenteId());
                    index = stringValue(sql, index, attributes.remetenteNome());
                    index = stringValue(sql, index, attributes.origemCidade());
                    index = stringValue(sql, index, attributes.origemUf());
                    index = longValue(sql, index, attributes.destinatarioId());
                    index = stringValue(sql, index, attributes.destinatarioNome());
                    index = stringValue(sql, index, attributes.destinoCidade());
                    index = stringValue(sql, index, attributes.destinoUf());
                    index = stringValue(sql, index, attributes.filialNome());
                    index = stringValue(sql, index, attributes.filialApelido());
                    index = stringValue(sql, index, attributes.numeroNotaFiscal());
                    index = stringValue(sql, index, attributes.tabelaPrecoNome());
                    index = stringValue(sql, index, attributes.classificacaoNome());
                    index = stringValue(sql, index, attributes.centroCustoNome());
                    index = stringValue(sql, index, attributes.usuarioNome());
                    index = integerValue(sql, index, attributes.invoicesTotalVolumes());
                    index = bigdecimalValue(sql, index, attributes.taxedWeight());
                    index = bigdecimalValue(sql, index, attributes.realWeight());
                    index = bigdecimalValue(sql, index, attributes.totalCubicVolume());
                    index = bigdecimalValue(sql, index, attributes.subtotal());
                    index = stringValue(sql, index, attributes.chaveCte());
                    index = integerValue(sql, index, attributes.numeroCte());
                    index = integerValue(sql, index, attributes.serieCte());
                    index = longValue(sql, index, attributes.cteId());
                    index = stringValue(sql, index, attributes.cteEmissionType());
                    index = integerValue(sql, index, attributes.serviceType());
                    index = booleanValue(sql, index, attributes.insuranceEnabled());
                    index = bigdecimalValue(sql, index, attributes.grisSubtotal());
                    index = bigdecimalValue(sql, index, attributes.tdeSubtotal());
                    index = stringValue(sql, index, attributes.modalCte());
                    index = bigdecimalValue(sql, index, attributes.redispatchSubtotal());
                    index = bigdecimalValue(sql, index, attributes.suframaSubtotal());
                    index = stringValue(sql, index, attributes.paymentType());
                    index = stringValue(sql, index, attributes.previousDocumentType());
                    index = bigdecimalValue(sql, index, attributes.productsValue());
                    index = bigdecimalValue(sql, index, attributes.trtSubtotal());
                    index = stringValue(sql, index, attributes.nfseSeries());
                    index = integerValue(sql, index, attributes.nfseNumber());
                    index = longValue(sql, index, attributes.insuranceId());
                    index = bigdecimalValue(sql, index, attributes.otherFees());
                    index = bigdecimalValue(sql, index, attributes.km());
                    index = integerValue(sql, index, attributes.paymentAccountableType());
                    index = bigdecimalValue(sql, index, attributes.insuredValue());
                    index = booleanValue(sql, index, attributes.globalized());
                    index = bigdecimalValue(sql, index, attributes.secCatSubtotal());
                    index = stringValue(sql, index, attributes.globalizedType());
                    index = integerValue(sql, index, attributes.priceTableAccountableType());
                    index = integerValue(sql, index, attributes.insuranceAccountableType());
                    index = bigdecimalValue(sql, index, attributes.fiscalCalculationBasis());
                    index = bigdecimalValue(sql, index, attributes.fiscalTaxRate());
                    index = bigdecimalValue(sql, index, attributes.fiscalPisRate());
                    index = bigdecimalValue(sql, index, attributes.fiscalCofinsRate());
                    index = booleanValue(sql, index, attributes.fiscalHasDifal());
                    index = bigdecimalValue(sql, index, attributes.fiscalDifalOrigin());
                    index = bigdecimalValue(sql, index, attributes.fiscalDifalDestination());
                    index = stringValue(sql, index, attributes.fiscalCstType());
                    index = stringValue(sql, index, attributes.fiscalCfopCode());
                    index = bigdecimalValue(sql, index, attributes.fiscalTaxValue());
                    index = bigdecimalValue(sql, index, attributes.fiscalPisValue());
                    index = bigdecimalValue(sql, index, attributes.fiscalCofinsValue());
                    index = bigdecimalValue(sql, index, attributes.cubagesCubedWeight());
                    index = bigdecimalValue(sql, index, attributes.freightWeightSubtotal());
                    index = bigdecimalValue(sql, index, attributes.adValoremSubtotal());
                    index = bigdecimalValue(sql, index, attributes.tollSubtotal());
                    index = bigdecimalValue(sql, index, attributes.itrSubtotal());
                    index = stringValue(sql, index, attributes.pagadorDocumento());
                    index = stringValue(sql, index, attributes.remetenteDocumento());
                    index = stringValue(sql, index, attributes.destinatarioDocumento());
                    index = stringValue(sql, index, attributes.filialCnpj());
                    index = stringValue(sql, index, attributes.nfseIntegrationId());
                    index = stringValue(sql, index, attributes.nfseStatus());
                    index = localdateValue(sql, index, attributes.nfseIssuedAt());
                    index = stringValue(sql, index, attributes.nfseCancelationReason());
                    index = stringValue(sql, index, attributes.nfsePdfServiceUrl());
                    index = longValue(sql, index, attributes.nfseCorporationId());
                    index = stringValue(sql, index, attributes.nfseServiceDescription());
                    index = stringValue(sql, index, attributes.nfseXmlDocument());
                    sql.setString(index++, run.toString());
                    sql.setString(index++, execution.toString());
                    sql.setString(index, row.sourceKey());
                    sql.addBatch();
                }
                cancellation.throwIfCancellationRequested();
                for (final int changed : sql.executeBatch()) {
                    if (changed != 1) {
                        throw new SQLException("ANA_FREIGHT_ATTRIBUTES_CAPTURE_REQUIRED");
                    }
                }
                return rows.size();
            } catch (final SQLException | RuntimeException failure) {
                try {
                    connection.rollback(before);
                } catch (final SQLException rollback) {
                    failure.addSuppressed(rollback);
                }
                throw failure;
            }
        }
    }

    private static int stringValue(
            final PreparedStatement sql, final int index, final ExpansionValue<String> field)
            throws SQLException {
        sql.setString(index, field.presence().name());
        sql.setString(index + 1, field.wire().name());
        sql.setString(index + 2, field.raw());
        sql.setString(index + 3, field.value());
        sql.setString(index + 4, field.issue());
        return index + 5;
    }

    private static int longValue(
            final PreparedStatement sql, final int index, final ExpansionValue<Long> field)
            throws SQLException {
        sql.setString(index, field.presence().name());
        sql.setString(index + 1, field.wire().name());
        sql.setString(index + 2, field.raw());
        sql.setObject(index + 3, field.value(), java.sql.Types.BIGINT);
        sql.setString(index + 4, field.issue());
        return index + 5;
    }

    private static int bigdecimalValue(
            final PreparedStatement sql, final int index, final ExpansionValue<BigDecimal> field)
            throws SQLException {
        sql.setString(index, field.presence().name());
        sql.setString(index + 1, field.wire().name());
        sql.setString(index + 2, field.raw());
        sql.setBigDecimal(index + 3, field.value());
        sql.setString(index + 4, field.issue());
        return index + 5;
    }

    private static int localdateValue(
            final PreparedStatement sql, final int index, final ExpansionValue<LocalDate> field)
            throws SQLException {
        sql.setString(index, field.presence().name());
        sql.setString(index + 1, field.wire().name());
        sql.setString(index + 2, field.raw());
        sql.setDate(index + 3, field.value() == null ? null : java.sql.Date.valueOf(field.value()));
        sql.setString(index + 4, field.issue());
        return index + 5;
    }

    private static int integerValue(
            final PreparedStatement sql, final int index, final ExpansionValue<Integer> field)
            throws SQLException {
        sql.setString(index, field.presence().name());
        sql.setString(index + 1, field.wire().name());
        sql.setString(index + 2, field.raw());
        sql.setObject(index + 3, field.value(), java.sql.Types.INTEGER);
        sql.setString(index + 4, field.issue());
        return index + 5;
    }

    private static int booleanValue(
            final PreparedStatement sql, final int index, final ExpansionValue<Boolean> field)
            throws SQLException {
        sql.setString(index, field.presence().name());
        sql.setString(index + 1, field.wire().name());
        sql.setString(index + 2, field.raw());
        sql.setObject(index + 3, field.value(), java.sql.Types.BIT);
        sql.setString(index + 4, field.issue());
        return index + 5;
    }
}
