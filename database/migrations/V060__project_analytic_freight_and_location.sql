-- SQL-02/07 local projections, captured observations and governed labels. No cross-database aliases.
SET ANSI_NULLS ON; SET QUOTED_IDENTIFIER ON;
GO
CREATE VIEW core.analytic_lab_location_projection AS
 SELECT g.run_id,d.dependency_id,d.source_key,d.stage_record_id,capture.partition_date business_date,d.state,
 TRY_CONVERT(BIGINT,SUBSTRING(d.source_key,9,256)) sequence_number,
 CONVERT(NVARCHAR(1024),JSON_VALUE(l.payload_json,'$.type')) type,
 core.ufn_analytic_raster_time(t.service_second,t.service_nano,0) AT TIME ZONE zone.name service_at,
 l.invoices_volumes_typed invoices_volumes,l.taxed_weight_raw taxed_weight,l.taxed_weight_typed taxed_weight_decimal,
 l.invoices_value_raw invoices_value,l.invoices_value_typed invoices_value_decimal,l.total_typed total_value,
 CONVERT(NVARCHAR(1024),JSON_VALUE(l.payload_json,'$.service_type')) service_type,
 CONVERT(NVARCHAR(1024),JSON_VALUE(l.payload_json,'$.fit_crn_psn_nickname')) branch_nickname,
 core.ufn_analytic_iso_time(JSON_VALUE(l.payload_json,'$.fit_dpn_delivery_prediction_at')) AT TIME ZONE zone.name predicted_delivery_at,
 CONVERT(NVARCHAR(1024),JSON_VALUE(l.payload_json,'$.fit_dyn_name')) destination_location_name,
 CONVERT(NVARCHAR(1024),JSON_VALUE(l.payload_json,'$.fit_dyn_drt_nickname')) destination_branch_nickname,
 CONVERT(NVARCHAR(1024),JSON_VALUE(l.payload_json,'$.fit_fsn_name')) classification,l.status_raw COLLATE Latin1_General_100_BIN2 status,l.status_normalized,
 l.status_branch_nickname,l.status_branch_nickname_provenance,
 CONVERT(NVARCHAR(1024),JSON_VALUE(l.payload_json,'$.fit_o_n_name')) origin_location_name,
 CONVERT(NVARCHAR(1024),JSON_VALUE(l.payload_json,'$.fit_o_n_drt_nickname')) origin_branch_nickname,
 CONVERT(NVARCHAR(1024),JSON_VALUE(l.payload_json,'$.fit_fln_cln_nickname')) fit_fln_cln_nickname,
 l.attribute_hash localizacao_hash,l.payload_json metadata,l.observed_at_utc data_extracao,
 CONVERT(VARCHAR(32),CASE WHEN d.state<>'VALID' THEN 'SOURCE_CONFLICT'
 WHEN EXISTS(SELECT 1 FROM OPENJSON(l.payload_json) j WHERE j.[key] IN('type','service_type','fit_crn_psn_nickname',
 'fit_dpn_delivery_prediction_at','fit_dyn_name','fit_dyn_drt_nickname','fit_fsn_name','fit_o_n_name','fit_o_n_drt_nickname','fit_fln_cln_nickname')
 AND(j.[type] NOT IN(0,1) OR DATALENGTH(j.[value])>2048)) THEN 'SOURCE_ATTRIBUTE_INVALID'
 WHEN JSON_VALUE(l.payload_json,'$.fit_dpn_delivery_prediction_at') IS NOT NULL
 AND core.ufn_analytic_iso_time(JSON_VALUE(l.payload_json,'$.fit_dpn_delivery_prediction_at')) IS NULL THEN 'SOURCE_DATE_INVALID'
 ELSE 'READY' END) disposition
 FROM ctl.analytic_lab_source_group g JOIN ctl.analytic_lab_run run ON run.run_id=g.run_id
 JOIN core.expansion_lab_dependency d ON d.run_id=g.expansion_run AND d.entity='LOC'
 JOIN stg.localizacao_carga_record l ON l.stage_record_id=d.stage_record_id
 JOIN stg.expansion_lab_dependency_observation t ON t.stage_record_id=d.stage_record_id
 JOIN ctl.expansion_lab_dependency_capture capture ON capture.execution_id=t.execution_id AND capture.state='COMPLETE'
 CROSS APPLY(SELECT CASE run.zone_id WHEN 'UTC' THEN 'UTC' ELSE 'E. South America Standard Time' END name) zone;
GO
CREATE VIEW core.analytic_lab_freight_projection AS
 SELECT o.run_id,o.dependency_id,o.observation_id prepared_observation_id,
 a.modal,
 a.tipo_frete,
 a.accounting_credit_id,
 a.accounting_credit_installment_id,
 a.valor_notas,
 a.peso_notas,
 a.id_corporacao,
 a.id_cidade_destino,
 a.data_previsao_entrega,
 a.service_date,
 a.pick_item_id,
 a.pagador_id,
 a.pagador_nome,
 a.remetente_id,
 a.remetente_nome,
 a.origem_cidade,
 a.origem_uf,
 a.destinatario_id,
 a.destinatario_nome,
 a.destino_cidade,
 a.destino_uf,
 a.filial_nome,
 a.filial_apelido,
 a.numero_nota_fiscal,
 a.tabela_preco_nome,
 a.classificacao_nome,
 a.centro_custo_nome,
 a.usuario_nome,
 a.invoices_total_volumes,
 a.taxed_weight,
 a.real_weight,
 a.total_cubic_volume,
 a.subtotal,
 a.chave_cte,
 a.numero_cte,
 a.serie_cte,
 a.cte_id,
 a.cte_emission_type,
 a.service_type,
 a.insurance_enabled,
 a.gris_subtotal,
 a.tde_subtotal,
 a.modal_cte,
 a.redispatch_subtotal,
 a.suframa_subtotal,
 a.payment_type,
 a.previous_document_type,
 a.products_value,
 a.trt_subtotal,
 a.nfse_series,
 a.nfse_number,
 a.insurance_id,
 a.other_fees,
 a.km,
 a.payment_accountable_type,
 a.insured_value,
 a.globalized,
 a.sec_cat_subtotal,
 a.globalized_type,
 a.price_table_accountable_type,
 a.insurance_accountable_type,
 a.fiscal_calculation_basis,
 a.fiscal_tax_rate,
 a.fiscal_pis_rate,
 a.fiscal_cofins_rate,
 a.fiscal_has_difal,
 a.fiscal_difal_origin,
 a.fiscal_difal_destination,
 a.fiscal_cst_type,
 a.fiscal_cfop_code,
 a.fiscal_tax_value,
 a.fiscal_pis_value,
 a.fiscal_cofins_value,
 a.cubages_cubed_weight,
 a.freight_weight_subtotal,
 a.ad_valorem_subtotal,
 a.toll_subtotal,
 a.itr_subtotal,
 a.pagador_documento,
 a.remetente_documento,
 a.destinatario_documento,
 a.filial_cnpj,
 a.nfse_integration_id,
 a.nfse_status,
 a.nfse_issued_at,
 a.nfse_cancelation_reason,
 a.nfse_pdf_service_url,
 a.nfse_corporation_id,
 a.nfse_service_description,
 a.nfse_xml_document,
 TRY_CONVERT(BIGINT,SUBSTRING(o.source_key,9,256)) id,
 core.ufn_analytic_raster_time(t.service_second,t.service_nano,0) AT TIME ZONE zone.name servico_em,
 core.ufn_analytic_iso_time(JSON_VALUE(f.payload_json,'$.criado_em')) AT TIME ZONE zone.name criado_em,
 f.status_raw status,o.total_value valor_total,
 TRY_CONVERT(BIGINT,JSON_VALUE(f.payload_json,'$.corporation_sequence_number')) corporation_sequence_number,
 CONVERT(NVARCHAR(1024),JSON_VALUE(f.payload_json,'$.reference_number')) reference_number,
 core.ufn_analytic_iso_time(JSON_VALUE(f.payload_json,'$.finished_at')) AT TIME ZONE zone.name finished_at,
 core.ufn_analytic_iso_time(JSON_VALUE(f.payload_json,'$.fit_dpn_performance_finished_at')) AT TIME ZONE zone.name fit_dpn_performance_finished_at,
 core.ufn_analytic_iso_time(JSON_VALUE(f.payload_json,'$.cte_created_at')) AT TIME ZONE zone.name cte_created_at,
 core.ufn_analytic_iso_time(JSON_VALUE(f.payload_json,'$.cte_issued_at')) AT TIME ZONE zone.name cte_issued_at,
 terms.billing_reference_date data_referencia_faturamento,terms.eligible is_elegivel_faturamento,terms.courtesy cortesia,
 f.payload_json metadata,CONVERT(BIT,CASE WHEN o.source_active=0 THEN 1 ELSE 0 END) excluido_na_origem,o.extracted_at data_extracao
 FROM mart.analytic_freight_operational current_fact JOIN mart.analytic_freight_operational_observation o ON o.observation_id=current_fact.observation_id
 JOIN ctl.analytic_lab_run run ON run.run_id=o.run_id JOIN stg.frete_record f ON f.stage_record_id=o.freight_stage_id
 JOIN stg.expansion_lab_dependency_observation t ON t.stage_record_id=o.freight_stage_id
 JOIN stg.analytic_freight_attributes a ON a.observation_id=o.attribute_id
 LEFT JOIN stg.expansion_lab_freight_terms terms ON terms.term_id=o.term_id
 CROSS APPLY(SELECT CASE run.zone_id WHEN 'UTC' THEN 'UTC' ELSE 'E. South America Standard Time' END name) zone
 WHERE current_fact.indicator='CB' AND a.valid=1;
GO
CREATE VIEW pub.analytic_lab_sql_02 AS SELECT
 CAST(f.servico_em AS TIME(0)) AS [Hora (Solicitacao)],
 f.id AS [ID],
 f.corporation_sequence_number AS [Nº Minuta],
 f.chave_cte AS [Chave CT-e],
 f.numero_cte AS [Nº CT-e],
 f.serie_cte AS [Série],
 f.cte_issued_at AS [CT-e Emissão],
 f.data_referencia_faturamento AS data_referencia_faturamento,
 f.cte_emission_type AS [CT-e Tipo Emissão],
 f.cte_id AS [CT-e ID],
 f.cte_created_at AS [CT-e Criado em],
 CASE 
        WHEN f.cte_id IS NOT NULL
          OR NULLIF(LTRIM(RTRIM(f.chave_cte)), '') IS NOT NULL
          OR f.numero_cte IS NOT NULL
          OR f.serie_cte IS NOT NULL THEN 'CT-e'
        WHEN f.nfse_number IS NOT NULL
          OR NULLIF(LTRIM(RTRIM(f.nfse_series)), '') IS NOT NULL
          OR NULLIF(LTRIM(RTRIM(f.nfse_xml_document)), '') IS NOT NULL
          OR NULLIF(LTRIM(RTRIM(f.nfse_integration_id)), '') IS NOT NULL THEN 'NFS-e'
        ELSE 'Pendente/Não Emitido'
    END AS [Documento Oficial/Tipo],
 CASE 
        WHEN f.cte_id IS NOT NULL
          OR NULLIF(LTRIM(RTRIM(f.chave_cte)), '') IS NOT NULL THEN f.chave_cte
        ELSE NULL
    END AS [Documento Oficial/Chave],
 CASE 
        WHEN f.cte_id IS NOT NULL
          OR NULLIF(LTRIM(RTRIM(f.chave_cte)), '') IS NOT NULL
          OR f.numero_cte IS NOT NULL
          OR f.serie_cte IS NOT NULL THEN CONVERT(NVARCHAR(50), f.numero_cte)
        WHEN f.nfse_number IS NOT NULL
          OR NULLIF(LTRIM(RTRIM(f.nfse_series)), '') IS NOT NULL
          OR NULLIF(LTRIM(RTRIM(f.nfse_xml_document)), '') IS NOT NULL
          OR NULLIF(LTRIM(RTRIM(f.nfse_integration_id)), '') IS NOT NULL THEN CONVERT(NVARCHAR(50), f.nfse_number)
        ELSE NULL
    END AS [Documento Oficial/Número],
 CASE 
        WHEN f.cte_id IS NOT NULL
          OR NULLIF(LTRIM(RTRIM(f.chave_cte)), '') IS NOT NULL
          OR f.numero_cte IS NOT NULL
          OR f.serie_cte IS NOT NULL THEN CONVERT(NVARCHAR(50), f.serie_cte)
        WHEN f.nfse_number IS NOT NULL
          OR NULLIF(LTRIM(RTRIM(f.nfse_series)), '') IS NOT NULL
          OR NULLIF(LTRIM(RTRIM(f.nfse_xml_document)), '') IS NOT NULL
          OR NULLIF(LTRIM(RTRIM(f.nfse_integration_id)), '') IS NOT NULL THEN f.nfse_series
        ELSE NULL
    END AS [Documento Oficial/Série],
 CASE 
        WHEN f.cte_id IS NOT NULL
          OR NULLIF(LTRIM(RTRIM(f.chave_cte)), '') IS NOT NULL
          OR f.numero_cte IS NOT NULL
          OR f.serie_cte IS NOT NULL THEN NULL
        WHEN f.nfse_number IS NOT NULL
          OR NULLIF(LTRIM(RTRIM(f.nfse_series)), '') IS NOT NULL
          OR NULLIF(LTRIM(RTRIM(f.nfse_xml_document)), '') IS NOT NULL
          OR NULLIF(LTRIM(RTRIM(f.nfse_integration_id)), '') IS NOT NULL THEN f.nfse_xml_document
        ELSE NULL
    END AS [Documento Oficial/XML],
 f.servico_em AS [Data frete],
 f.criado_em AS [Criado em],
 f.valor_total AS [Valor Total do Serviço],
 f.valor_notas AS [Valor NF],
 f.peso_notas AS [Kg NF],
 f.subtotal AS [Valor Frete],
 o.volumes AS [Volumes],
 f.taxed_weight AS [Kg Taxado],
 f.taxed_weight AS [Peso Taxado],
 f.real_weight AS [Kg Real],
 f.real_weight AS [Peso Real],
 f.cubages_cubed_weight AS [Kg Cubado],
 f.cubages_cubed_weight AS [Peso Cubado],
 f.total_cubic_volume AS [M3],
 f.total_cubic_volume AS [Total M3],
 f.pagador_nome AS [Pagador],
 f.pagador_documento AS [Pagador Doc],
 f.pagador_id AS [Pagador ID],
 f.remetente_nome AS [Remetente],
 f.remetente_documento AS [Remetente Doc],
 f.remetente_id AS [Remetente ID],
 f.origem_cidade AS [Origem],
 f.origem_uf AS [UF Origem],
 f.destinatario_nome AS [Destinatario],
 f.destinatario_documento AS [Destinatario Doc],
 f.destinatario_id AS [Destinatario ID],
 f.destino_cidade AS [Destino],
 f.destino_cidade AS [Cidade Destino],
 f.destino_uf AS [UF Destino],
 COALESCE(NULLIF(LTRIM(RTRIM(lc.destination_location_name)),N'') COLLATE Latin1_General_100_BIN2,f.destino_uf,N'SEM_REGIAO') AS [Região Destino],
 f.filial_nome AS [Filial],
 f.filial_nome AS [Filial Emissora],
 registry.normalized_name AS [Responsável pela Região de Destino],
 pe.performance_branch_key AS [Responsável Região Destino Key],
 f.filial_apelido AS [Filial Apelido],
 f.filial_cnpj AS [Filial CNPJ],
 f.tabela_preco_nome AS [Tabela de Preço],
 f.classificacao_nome AS [Classificação],
 f.centro_custo_nome AS [Centro de Custo],
 f.usuario_nome AS [Usuário],
 f.numero_nota_fiscal AS [NF],
 f.reference_number AS [Referência],
 f.id_corporacao AS [Corp ID],
 f.id_cidade_destino AS [Cidade Destino ID],
 o.forecast_date AS [Previsão de Entrega],
 f.finished_at AS [Data de Finalização],
 o.completion_date AS [Finalização da Performance],
 o.performance_days AS [Performance Diferença de Dias],
 CASE
        WHEN o.performance_days IS NULL THEN NULL
        WHEN o.performance_days <= 0 THEN 'NO PRAZO'
        ELSE 'FORA DO PRAZO'
    END AS [Performance Status],
 CASE
        WHEN o.performance_days IS NULL THEN NULL
        WHEN o.performance_days = 0 THEN 'NO PRAZO'
        WHEN o.performance_days = 1 THEN '1 DIA DE ATRASO'
        WHEN o.performance_days = 2 THEN '2 DIAS DE ATRASO'
        WHEN o.performance_days = 3 THEN '3 DIAS DE ATRASO'
        WHEN o.performance_days > 3 THEN 'ACIMA DE 3 DIAS DE ATRASO'
        WHEN o.performance_days = -1 THEN '1 DIA ANTES'
        WHEN o.performance_days = -2 THEN '2 DIAS ANTES'
        WHEN o.performance_days = -3 THEN '3 DIAS ANTES'
        ELSE 'ACIMA DE 3 DIAS ANTES'
    END AS [Performance Status Dif de Dias],
 CASE
        WHEN o.performance_days IS NULL THEN NULL
        WHEN o.performance_days = 0 THEN 'NO PRAZO'
        WHEN o.performance_days = 1 THEN '1 DIA DE ATRASO'
        WHEN o.performance_days = 2 THEN '2 DIAS DE ATRASO'
        WHEN o.performance_days = 3 THEN '3 DIAS DE ATRASO'
        WHEN o.performance_days > 3 THEN 'ACIMA DE 3 DIAS DE ATRASO'
        WHEN o.performance_days = -1 THEN '1 DIA ANTES'
        WHEN o.performance_days = -2 THEN '2 DIAS ANTES'
        WHEN o.performance_days = -3 THEN '3 DIAS ANTES'
        ELSE 'ACIMA DE 3 DIAS ANTES'
    END AS [Performance Status Dif de Dias Oficial],
 CASE WHEN EXISTS(SELECT 1 FROM core.expansion_lab_link link JOIN stg.expansion_lab_relation binding ON binding.relation_id=link.relation_id JOIN core.expansion_lab_current_input inv ON inv.run_id=link.run_id AND inv.component_id=link.component_id WHERE link.run_id=g.expansion_run AND link.target_dependency_id=o.dependency_id AND link.state='RESOLVED' AND binding.kind='INV_FREIGHT' AND inv.vertical='INV' AND inv.proof_attached=1 AND inv.unresolved_conflict=0) THEN N'Sim' ELSE N'Não' END AS [Comprovante Anexado],
 f.modal AS [Modal],
 COALESCE(label.label,f.status COLLATE Latin1_General_100_BIN2) AS [Status],
 CASE WHEN f.cortesia = 1 THEN 'Sim'
         WHEN f.cortesia = 0 THEN 'Não'
         ELSE NULL
    END AS [Cortesia],
 f.cortesia AS [Cortesia Flag],
 f.is_elegivel_faturamento AS is_elegivel_faturamento,
 REPLACE(f.tipo_frete, 'Freight::', '') AS [Tipo Frete],
 f.service_type AS [Service Type],
 CASE WHEN f.insurance_enabled = 1 THEN 'Com seguro'
         WHEN f.insurance_enabled = 0 THEN 'Sem seguro'
         ELSE NULL
    END AS [Seguro Habilitado],
 f.gris_subtotal AS [GRIS],
 f.tde_subtotal AS [TDE],
 f.freight_weight_subtotal AS [Frete Peso],
 f.ad_valorem_subtotal AS [Ad Valorem],
 f.toll_subtotal AS [Pedágio],
 f.itr_subtotal AS [ITR],
 f.modal_cte AS [Modal CT-e],
 f.redispatch_subtotal AS [Redispatch],
 f.suframa_subtotal AS [SUFRAMA],
 CASE f.payment_type
        WHEN 'bill' THEN 'cobrança'
        WHEN 'cash' THEN 'dinheiro'
        ELSE f.payment_type
    END AS [Tipo Pagamento],
 CASE f.previous_document_type
        WHEN 'electronic' THEN 'eletrônico'
        ELSE f.previous_document_type
    END AS [Doc Anterior],
 f.products_value AS [Valor Produtos],
 f.trt_subtotal AS [TRT],
 f.fiscal_cst_type AS [ICMS CST],
 f.fiscal_cfop_code AS [CFOP],
 f.fiscal_tax_value AS [Valor ICMS],
 f.fiscal_pis_value AS [Valor PIS],
 f.fiscal_cofins_value AS [Valor COFINS],
 f.fiscal_calculation_basis AS [Base de Cálculo ICMS],
 f.fiscal_tax_rate AS [Alíquota ICMS %],
 f.fiscal_pis_rate AS [Alíquota PIS %],
 f.fiscal_cofins_rate AS [Alíquota COFINS %],
 CASE WHEN f.fiscal_has_difal = 1 THEN 'possui'
         WHEN f.fiscal_has_difal = 0 THEN 'não possui'
         ELSE NULL
    END AS [Possui DIFAL],
 f.fiscal_difal_origin AS [DIFAL Origem],
 f.fiscal_difal_destination AS [DIFAL Destino],
 f.nfse_series AS [Série NFS-e],
 f.nfse_number AS [Nº NFS-e],
 f.nfse_integration_id AS [NFS-e/ID Integração],
 f.nfse_status AS [NFS-e/Status],
 f.nfse_issued_at AS [NFS-e/Emissão],
 f.nfse_cancelation_reason AS [NFS-e/Cancelamento/Motivo],
 f.nfse_pdf_service_url AS [NFS-e/PDF],
 f.nfse_corporation_id AS [NFS-e/Filial ID],
 f.nfse_service_description AS [NFS-e/Serviço/Descrição],
 f.nfse_xml_document AS [NFS-e/XML],
 f.insurance_id AS [Seguro ID],
 f.other_fees AS [Outras Tarifas],
 f.km AS [KM],
 f.payment_accountable_type AS [Tipo Contábil Pagamento],
 f.insured_value AS [Valor Segurado],
 CASE WHEN f.globalized = 1 THEN 'verdadeiro'
         WHEN f.globalized = 0 THEN 'falso'
         ELSE NULL
    END AS [Globalizado],
 f.sec_cat_subtotal AS [SEC/CAT],
 CASE f.globalized_type
        WHEN 'none' THEN 'nenhum'
        ELSE f.globalized_type
    END AS [Tipo Globalizado],
 f.price_table_accountable_type AS [Tipo Contábil Tabela],
 f.insurance_accountable_type AS [Tipo Contábil Seguro],
 f.metadata AS [Metadata],
 f.data_extracao AS [Data de extracao],
 o.run_id,o.dependency_id,o.source_key,o.reference_revision,o.service_date business_date,o.disposition,
 o.receipt_id,o.freight_stage_id,o.attribute_id,o.term_id,o.location_stage_id,o.reference_release_id,
 o.volume_provenance,o.forecast_provenance,o.completion_provenance,o.performance_branch_provenance,o.extraction_provenance
 FROM core.analytic_lab_freight_projection f JOIN mart.analytic_freight_operational_observation o ON o.observation_id=f.prepared_observation_id
 JOIN ctl.analytic_lab_source_group g ON g.run_id=o.run_id
 LEFT JOIN stg.localizacao_carga_record lc_record ON lc_record.stage_record_id=o.location_stage_id
 OUTER APPLY(SELECT CONVERT(NVARCHAR(1024),JSON_VALUE(lc_record.payload_json,'$.fit_dyn_name')) destination_location_name) lc
 JOIN mart.analytic_freight_operational pe_current ON pe_current.run_id=o.run_id AND pe_current.dependency_id=o.dependency_id AND pe_current.indicator='PE'
 JOIN mart.analytic_freight_operational_observation pe ON pe.observation_id=pe_current.observation_id
 LEFT JOIN ref.analytic_lab_registry registry ON registry.reference_release_id=pe.reference_release_id AND registry.dimension_kind='FILIAL'
 AND registry.entity_key=pe.performance_branch_key AND registry.active=1 AND registry.valid_from<=COALESCE(o.forecast_date,o.service_date)
 AND registry.valid_to_exclusive>COALESCE(o.forecast_date,o.service_date)
 LEFT JOIN ref.analytic_lab_label label ON label.reference_release_id=o.reference_release_id AND label.category='FRE_STATUS' AND label.raw_value=f.status COLLATE Latin1_General_100_BIN2
 WHERE o.source_active=1 AND o.freight_type<>N'COMPLEMENTAR'
 AND o.disposition NOT IN('SOURCE_CONFLICT','ATTRIBUTE_CONFLICT','INVALID_ATTRIBUTES','TERMS_CONFLICT','LOCATION_CONFLICT','SOURCE_AMOUNT_PRECISION');
GO
CREATE VIEW pub.analytic_lab_sql_07 AS SELECT
 CAST(lc.service_at AS TIME(0)) AS [Hora (Solicitacao)],
 lc.sequence_number AS [N° Minuta],
 REPLACE(lc.type, 'Freight::', '') AS [Tipo],
 lc.service_at AS [Data do frete],
 lc.invoices_volumes AS [Volumes],
 lc.taxed_weight AS [Peso Taxado],
 lc.taxed_weight_decimal AS [Peso Taxado Decimal],
 lc.invoices_value AS [Valor NF],
 lc.invoices_value_decimal AS [Valor NF Decimal],
 lc.total_value AS [Valor Frete],
 lc.service_type AS [Tipo Serviço],
 lc.branch_nickname AS [Filial Emissora],
 lc.predicted_delivery_at AS [Previsão Entrega/Previsão de entrega],
 lc.destination_location_name AS [Região Destino],
 lc.destination_branch_nickname AS [Filial Destino],
 lc.destination_branch_nickname AS [Responsável pela Região de Destino],
 COALESCE(regiao_alias.label,N'SEM_MAP') AS [Sigla Responsável Região Destino],
 lc.classification AS [Classificação],
 COALESCE(status_label.label,lc.status COLLATE Latin1_General_100_BIN2) AS [Status Carga],
 COALESCE(lc.status_normalized, NULLIF(LOWER(LTRIM(RTRIM(lc.status))), ''), N'sem_status') AS [Status Normalizado],
 CASE WHEN COALESCE(lc.status_normalized, LOWER(LTRIM(RTRIM(lc.status)))) IN (N'finished', N'delivered', N'canceled', N'cancelled', N'finalizado', N'entregue', N'cancelado') THEN 1 ELSE 0 END AS [Status Terminal],
 CASE WHEN COALESCE(lc.status_normalized, LOWER(LTRIM(RTRIM(lc.status)))) IN (N'canceled', N'cancelled', N'cancelado') THEN 1 ELSE 0 END AS [Cancelado Flag],
 lc.status_branch_nickname AS [Filial Atual],
 lc.origin_location_name AS [Região Origem],
 lc.origin_branch_nickname AS [Filial Origem],
 lc.fit_fln_cln_nickname AS [Localização Atual],
 lc.localizacao_hash AS [Hash Localização],
 lc.metadata AS [Metadata],
 lc.data_extracao AS [Data de extracao],
 lc.run_id,lc.dependency_id,lc.source_key,lc.business_date,selection.revision reference_revision,lc.stage_record_id,lc.disposition,
 selection.reference_release_id,lc.status_branch_nickname_provenance,
 CONVERT(VARCHAR(24),CASE WHEN regiao_alias.label IS NULL THEN 'UNMAPPED' ELSE 'GOVERNED_ALIAS' END) region_provenance
 FROM core.analytic_lab_location_projection lc
 JOIN ctl.analytic_lab_reference_selection selection ON selection.run_id=lc.run_id AND selection.valid_from<=lc.business_date AND selection.valid_to_exclusive>lc.business_date
 LEFT JOIN ref.analytic_lab_label status_label ON status_label.reference_release_id=selection.reference_release_id AND status_label.category='LOC_STATUS' AND status_label.raw_value=lc.status COLLATE Latin1_General_100_BIN2
 LEFT JOIN ref.analytic_lab_label regiao_alias ON regiao_alias.reference_release_id=selection.reference_release_id AND regiao_alias.category='REGION_ALIAS'
 AND regiao_alias.raw_value=UPPER(LTRIM(RTRIM(lc.destination_branch_nickname))) COLLATE Latin1_General_100_BIN2
 WHERE lc.disposition='READY';
GO
