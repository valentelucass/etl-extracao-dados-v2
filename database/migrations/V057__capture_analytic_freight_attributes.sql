-- ANA-09: raw typed synthetic source attributes, bound to a captured6389 root; no final fact fixture.
SET ANSI_NULLS ON; SET QUOTED_IDENTIFIER ON;
GO
CREATE TABLE stg.analytic_freight_attributes (
 observation_id BIGINT IDENTITY NOT NULL PRIMARY KEY,run_id UNIQUEIDENTIFIER NOT NULL REFERENCES ctl.analytic_lab_run(run_id),
 source_execution UNIQUEIDENTIFIER NOT NULL REFERENCES ctl.execution_attempt(execution_id),
 source_key NVARCHAR(256) COLLATE Latin1_General_100_BIN2 NOT NULL,
 freight_stage_id BIGINT NOT NULL REFERENCES stg.frete_record(stage_record_id),
 base_stage_id BIGINT NOT NULL REFERENCES stg.frete_record(stage_record_id),revision INT NOT NULL,
 evidence VARCHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL,valid BIT NOT NULL,comparison_bytes VARBINARY(MAX) NOT NULL,
 modal_p VARCHAR(8) COLLATE Latin1_General_100_BIN2 NOT NULL,
 modal_w VARCHAR(8) COLLATE Latin1_General_100_BIN2 NOT NULL,
 modal_raw NVARCHAR(4000) COLLATE Latin1_General_100_BIN2 NULL,
 modal NVARCHAR(50) COLLATE Latin1_General_100_BIN2 NULL,
 modal_issue VARCHAR(64) COLLATE Latin1_General_100_BIN2 NULL,
 tipo_frete_p VARCHAR(8) COLLATE Latin1_General_100_BIN2 NOT NULL,
 tipo_frete_w VARCHAR(8) COLLATE Latin1_General_100_BIN2 NOT NULL,
 tipo_frete_raw NVARCHAR(4000) COLLATE Latin1_General_100_BIN2 NULL,
 tipo_frete NVARCHAR(100) COLLATE Latin1_General_100_BIN2 NULL,
 tipo_frete_issue VARCHAR(64) COLLATE Latin1_General_100_BIN2 NULL,
 accounting_credit_id_p VARCHAR(8) COLLATE Latin1_General_100_BIN2 NOT NULL,
 accounting_credit_id_w VARCHAR(8) COLLATE Latin1_General_100_BIN2 NOT NULL,
 accounting_credit_id_raw NVARCHAR(4000) COLLATE Latin1_General_100_BIN2 NULL,
 accounting_credit_id BIGINT NULL,
 accounting_credit_id_issue VARCHAR(64) COLLATE Latin1_General_100_BIN2 NULL,
 accounting_credit_installment_id_p VARCHAR(8) COLLATE Latin1_General_100_BIN2 NOT NULL,
 accounting_credit_installment_id_w VARCHAR(8) COLLATE Latin1_General_100_BIN2 NOT NULL,
 accounting_credit_installment_id_raw NVARCHAR(4000) COLLATE Latin1_General_100_BIN2 NULL,
 accounting_credit_installment_id BIGINT NULL,
 accounting_credit_installment_id_issue VARCHAR(64) COLLATE Latin1_General_100_BIN2 NULL,
 valor_notas_p VARCHAR(8) COLLATE Latin1_General_100_BIN2 NOT NULL,
 valor_notas_w VARCHAR(8) COLLATE Latin1_General_100_BIN2 NOT NULL,
 valor_notas_raw NVARCHAR(4000) COLLATE Latin1_General_100_BIN2 NULL,
 valor_notas DECIMAL(28,8) NULL,
 valor_notas_issue VARCHAR(64) COLLATE Latin1_General_100_BIN2 NULL,
 peso_notas_p VARCHAR(8) COLLATE Latin1_General_100_BIN2 NOT NULL,
 peso_notas_w VARCHAR(8) COLLATE Latin1_General_100_BIN2 NOT NULL,
 peso_notas_raw NVARCHAR(4000) COLLATE Latin1_General_100_BIN2 NULL,
 peso_notas DECIMAL(28,8) NULL,
 peso_notas_issue VARCHAR(64) COLLATE Latin1_General_100_BIN2 NULL,
 id_corporacao_p VARCHAR(8) COLLATE Latin1_General_100_BIN2 NOT NULL,
 id_corporacao_w VARCHAR(8) COLLATE Latin1_General_100_BIN2 NOT NULL,
 id_corporacao_raw NVARCHAR(4000) COLLATE Latin1_General_100_BIN2 NULL,
 id_corporacao BIGINT NULL,
 id_corporacao_issue VARCHAR(64) COLLATE Latin1_General_100_BIN2 NULL,
 id_cidade_destino_p VARCHAR(8) COLLATE Latin1_General_100_BIN2 NOT NULL,
 id_cidade_destino_w VARCHAR(8) COLLATE Latin1_General_100_BIN2 NOT NULL,
 id_cidade_destino_raw NVARCHAR(4000) COLLATE Latin1_General_100_BIN2 NULL,
 id_cidade_destino BIGINT NULL,
 id_cidade_destino_issue VARCHAR(64) COLLATE Latin1_General_100_BIN2 NULL,
 data_previsao_entrega_p VARCHAR(8) COLLATE Latin1_General_100_BIN2 NOT NULL,
 data_previsao_entrega_w VARCHAR(8) COLLATE Latin1_General_100_BIN2 NOT NULL,
 data_previsao_entrega_raw NVARCHAR(4000) COLLATE Latin1_General_100_BIN2 NULL,
 data_previsao_entrega DATE NULL,
 data_previsao_entrega_issue VARCHAR(64) COLLATE Latin1_General_100_BIN2 NULL,
 service_date_p VARCHAR(8) COLLATE Latin1_General_100_BIN2 NOT NULL,
 service_date_w VARCHAR(8) COLLATE Latin1_General_100_BIN2 NOT NULL,
 service_date_raw NVARCHAR(4000) COLLATE Latin1_General_100_BIN2 NULL,
 service_date DATE NULL,
 service_date_issue VARCHAR(64) COLLATE Latin1_General_100_BIN2 NULL,
 pick_item_id_p VARCHAR(8) COLLATE Latin1_General_100_BIN2 NOT NULL,
 pick_item_id_w VARCHAR(8) COLLATE Latin1_General_100_BIN2 NOT NULL,
 pick_item_id_raw NVARCHAR(4000) COLLATE Latin1_General_100_BIN2 NULL,
 pick_item_id BIGINT NULL,
 pick_item_id_issue VARCHAR(64) COLLATE Latin1_General_100_BIN2 NULL,
 pagador_id_p VARCHAR(8) COLLATE Latin1_General_100_BIN2 NOT NULL,
 pagador_id_w VARCHAR(8) COLLATE Latin1_General_100_BIN2 NOT NULL,
 pagador_id_raw NVARCHAR(4000) COLLATE Latin1_General_100_BIN2 NULL,
 pagador_id BIGINT NULL,
 pagador_id_issue VARCHAR(64) COLLATE Latin1_General_100_BIN2 NULL,
 pagador_nome_p VARCHAR(8) COLLATE Latin1_General_100_BIN2 NOT NULL,
 pagador_nome_w VARCHAR(8) COLLATE Latin1_General_100_BIN2 NOT NULL,
 pagador_nome_raw NVARCHAR(4000) COLLATE Latin1_General_100_BIN2 NULL,
 pagador_nome NVARCHAR(255) COLLATE Latin1_General_100_BIN2 NULL,
 pagador_nome_issue VARCHAR(64) COLLATE Latin1_General_100_BIN2 NULL,
 remetente_id_p VARCHAR(8) COLLATE Latin1_General_100_BIN2 NOT NULL,
 remetente_id_w VARCHAR(8) COLLATE Latin1_General_100_BIN2 NOT NULL,
 remetente_id_raw NVARCHAR(4000) COLLATE Latin1_General_100_BIN2 NULL,
 remetente_id BIGINT NULL,
 remetente_id_issue VARCHAR(64) COLLATE Latin1_General_100_BIN2 NULL,
 remetente_nome_p VARCHAR(8) COLLATE Latin1_General_100_BIN2 NOT NULL,
 remetente_nome_w VARCHAR(8) COLLATE Latin1_General_100_BIN2 NOT NULL,
 remetente_nome_raw NVARCHAR(4000) COLLATE Latin1_General_100_BIN2 NULL,
 remetente_nome NVARCHAR(255) COLLATE Latin1_General_100_BIN2 NULL,
 remetente_nome_issue VARCHAR(64) COLLATE Latin1_General_100_BIN2 NULL,
 origem_cidade_p VARCHAR(8) COLLATE Latin1_General_100_BIN2 NOT NULL,
 origem_cidade_w VARCHAR(8) COLLATE Latin1_General_100_BIN2 NOT NULL,
 origem_cidade_raw NVARCHAR(4000) COLLATE Latin1_General_100_BIN2 NULL,
 origem_cidade NVARCHAR(255) COLLATE Latin1_General_100_BIN2 NULL,
 origem_cidade_issue VARCHAR(64) COLLATE Latin1_General_100_BIN2 NULL,
 origem_uf_p VARCHAR(8) COLLATE Latin1_General_100_BIN2 NOT NULL,
 origem_uf_w VARCHAR(8) COLLATE Latin1_General_100_BIN2 NOT NULL,
 origem_uf_raw NVARCHAR(4000) COLLATE Latin1_General_100_BIN2 NULL,
 origem_uf NVARCHAR(10) COLLATE Latin1_General_100_BIN2 NULL,
 origem_uf_issue VARCHAR(64) COLLATE Latin1_General_100_BIN2 NULL,
 destinatario_id_p VARCHAR(8) COLLATE Latin1_General_100_BIN2 NOT NULL,
 destinatario_id_w VARCHAR(8) COLLATE Latin1_General_100_BIN2 NOT NULL,
 destinatario_id_raw NVARCHAR(4000) COLLATE Latin1_General_100_BIN2 NULL,
 destinatario_id BIGINT NULL,
 destinatario_id_issue VARCHAR(64) COLLATE Latin1_General_100_BIN2 NULL,
 destinatario_nome_p VARCHAR(8) COLLATE Latin1_General_100_BIN2 NOT NULL,
 destinatario_nome_w VARCHAR(8) COLLATE Latin1_General_100_BIN2 NOT NULL,
 destinatario_nome_raw NVARCHAR(4000) COLLATE Latin1_General_100_BIN2 NULL,
 destinatario_nome NVARCHAR(255) COLLATE Latin1_General_100_BIN2 NULL,
 destinatario_nome_issue VARCHAR(64) COLLATE Latin1_General_100_BIN2 NULL,
 destino_cidade_p VARCHAR(8) COLLATE Latin1_General_100_BIN2 NOT NULL,
 destino_cidade_w VARCHAR(8) COLLATE Latin1_General_100_BIN2 NOT NULL,
 destino_cidade_raw NVARCHAR(4000) COLLATE Latin1_General_100_BIN2 NULL,
 destino_cidade NVARCHAR(255) COLLATE Latin1_General_100_BIN2 NULL,
 destino_cidade_issue VARCHAR(64) COLLATE Latin1_General_100_BIN2 NULL,
 destino_uf_p VARCHAR(8) COLLATE Latin1_General_100_BIN2 NOT NULL,
 destino_uf_w VARCHAR(8) COLLATE Latin1_General_100_BIN2 NOT NULL,
 destino_uf_raw NVARCHAR(4000) COLLATE Latin1_General_100_BIN2 NULL,
 destino_uf NVARCHAR(10) COLLATE Latin1_General_100_BIN2 NULL,
 destino_uf_issue VARCHAR(64) COLLATE Latin1_General_100_BIN2 NULL,
 filial_nome_p VARCHAR(8) COLLATE Latin1_General_100_BIN2 NOT NULL,
 filial_nome_w VARCHAR(8) COLLATE Latin1_General_100_BIN2 NOT NULL,
 filial_nome_raw NVARCHAR(4000) COLLATE Latin1_General_100_BIN2 NULL,
 filial_nome NVARCHAR(255) COLLATE Latin1_General_100_BIN2 NULL,
 filial_nome_issue VARCHAR(64) COLLATE Latin1_General_100_BIN2 NULL,
 filial_apelido_p VARCHAR(8) COLLATE Latin1_General_100_BIN2 NOT NULL,
 filial_apelido_w VARCHAR(8) COLLATE Latin1_General_100_BIN2 NOT NULL,
 filial_apelido_raw NVARCHAR(4000) COLLATE Latin1_General_100_BIN2 NULL,
 filial_apelido NVARCHAR(255) COLLATE Latin1_General_100_BIN2 NULL,
 filial_apelido_issue VARCHAR(64) COLLATE Latin1_General_100_BIN2 NULL,
 numero_nota_fiscal_p VARCHAR(8) COLLATE Latin1_General_100_BIN2 NOT NULL,
 numero_nota_fiscal_w VARCHAR(8) COLLATE Latin1_General_100_BIN2 NOT NULL,
 numero_nota_fiscal_raw NVARCHAR(4000) COLLATE Latin1_General_100_BIN2 NULL,
 numero_nota_fiscal NVARCHAR(1024) COLLATE Latin1_General_100_BIN2 NULL,
 numero_nota_fiscal_issue VARCHAR(64) COLLATE Latin1_General_100_BIN2 NULL,
 tabela_preco_nome_p VARCHAR(8) COLLATE Latin1_General_100_BIN2 NOT NULL,
 tabela_preco_nome_w VARCHAR(8) COLLATE Latin1_General_100_BIN2 NOT NULL,
 tabela_preco_nome_raw NVARCHAR(4000) COLLATE Latin1_General_100_BIN2 NULL,
 tabela_preco_nome NVARCHAR(255) COLLATE Latin1_General_100_BIN2 NULL,
 tabela_preco_nome_issue VARCHAR(64) COLLATE Latin1_General_100_BIN2 NULL,
 classificacao_nome_p VARCHAR(8) COLLATE Latin1_General_100_BIN2 NOT NULL,
 classificacao_nome_w VARCHAR(8) COLLATE Latin1_General_100_BIN2 NOT NULL,
 classificacao_nome_raw NVARCHAR(4000) COLLATE Latin1_General_100_BIN2 NULL,
 classificacao_nome NVARCHAR(255) COLLATE Latin1_General_100_BIN2 NULL,
 classificacao_nome_issue VARCHAR(64) COLLATE Latin1_General_100_BIN2 NULL,
 centro_custo_nome_p VARCHAR(8) COLLATE Latin1_General_100_BIN2 NOT NULL,
 centro_custo_nome_w VARCHAR(8) COLLATE Latin1_General_100_BIN2 NOT NULL,
 centro_custo_nome_raw NVARCHAR(4000) COLLATE Latin1_General_100_BIN2 NULL,
 centro_custo_nome NVARCHAR(255) COLLATE Latin1_General_100_BIN2 NULL,
 centro_custo_nome_issue VARCHAR(64) COLLATE Latin1_General_100_BIN2 NULL,
 usuario_nome_p VARCHAR(8) COLLATE Latin1_General_100_BIN2 NOT NULL,
 usuario_nome_w VARCHAR(8) COLLATE Latin1_General_100_BIN2 NOT NULL,
 usuario_nome_raw NVARCHAR(4000) COLLATE Latin1_General_100_BIN2 NULL,
 usuario_nome NVARCHAR(255) COLLATE Latin1_General_100_BIN2 NULL,
 usuario_nome_issue VARCHAR(64) COLLATE Latin1_General_100_BIN2 NULL,
 invoices_total_volumes_p VARCHAR(8) COLLATE Latin1_General_100_BIN2 NOT NULL,
 invoices_total_volumes_w VARCHAR(8) COLLATE Latin1_General_100_BIN2 NOT NULL,
 invoices_total_volumes_raw NVARCHAR(4000) COLLATE Latin1_General_100_BIN2 NULL,
 invoices_total_volumes INT NULL,
 invoices_total_volumes_issue VARCHAR(64) COLLATE Latin1_General_100_BIN2 NULL,
 taxed_weight_p VARCHAR(8) COLLATE Latin1_General_100_BIN2 NOT NULL,
 taxed_weight_w VARCHAR(8) COLLATE Latin1_General_100_BIN2 NOT NULL,
 taxed_weight_raw NVARCHAR(4000) COLLATE Latin1_General_100_BIN2 NULL,
 taxed_weight DECIMAL(28,8) NULL,
 taxed_weight_issue VARCHAR(64) COLLATE Latin1_General_100_BIN2 NULL,
 real_weight_p VARCHAR(8) COLLATE Latin1_General_100_BIN2 NOT NULL,
 real_weight_w VARCHAR(8) COLLATE Latin1_General_100_BIN2 NOT NULL,
 real_weight_raw NVARCHAR(4000) COLLATE Latin1_General_100_BIN2 NULL,
 real_weight DECIMAL(28,8) NULL,
 real_weight_issue VARCHAR(64) COLLATE Latin1_General_100_BIN2 NULL,
 total_cubic_volume_p VARCHAR(8) COLLATE Latin1_General_100_BIN2 NOT NULL,
 total_cubic_volume_w VARCHAR(8) COLLATE Latin1_General_100_BIN2 NOT NULL,
 total_cubic_volume_raw NVARCHAR(4000) COLLATE Latin1_General_100_BIN2 NULL,
 total_cubic_volume DECIMAL(28,8) NULL,
 total_cubic_volume_issue VARCHAR(64) COLLATE Latin1_General_100_BIN2 NULL,
 subtotal_p VARCHAR(8) COLLATE Latin1_General_100_BIN2 NOT NULL,
 subtotal_w VARCHAR(8) COLLATE Latin1_General_100_BIN2 NOT NULL,
 subtotal_raw NVARCHAR(4000) COLLATE Latin1_General_100_BIN2 NULL,
 subtotal DECIMAL(28,8) NULL,
 subtotal_issue VARCHAR(64) COLLATE Latin1_General_100_BIN2 NULL,
 chave_cte_p VARCHAR(8) COLLATE Latin1_General_100_BIN2 NOT NULL,
 chave_cte_w VARCHAR(8) COLLATE Latin1_General_100_BIN2 NOT NULL,
 chave_cte_raw NVARCHAR(4000) COLLATE Latin1_General_100_BIN2 NULL,
 chave_cte NVARCHAR(100) COLLATE Latin1_General_100_BIN2 NULL,
 chave_cte_issue VARCHAR(64) COLLATE Latin1_General_100_BIN2 NULL,
 numero_cte_p VARCHAR(8) COLLATE Latin1_General_100_BIN2 NOT NULL,
 numero_cte_w VARCHAR(8) COLLATE Latin1_General_100_BIN2 NOT NULL,
 numero_cte_raw NVARCHAR(4000) COLLATE Latin1_General_100_BIN2 NULL,
 numero_cte INT NULL,
 numero_cte_issue VARCHAR(64) COLLATE Latin1_General_100_BIN2 NULL,
 serie_cte_p VARCHAR(8) COLLATE Latin1_General_100_BIN2 NOT NULL,
 serie_cte_w VARCHAR(8) COLLATE Latin1_General_100_BIN2 NOT NULL,
 serie_cte_raw NVARCHAR(4000) COLLATE Latin1_General_100_BIN2 NULL,
 serie_cte INT NULL,
 serie_cte_issue VARCHAR(64) COLLATE Latin1_General_100_BIN2 NULL,
 cte_id_p VARCHAR(8) COLLATE Latin1_General_100_BIN2 NOT NULL,
 cte_id_w VARCHAR(8) COLLATE Latin1_General_100_BIN2 NOT NULL,
 cte_id_raw NVARCHAR(4000) COLLATE Latin1_General_100_BIN2 NULL,
 cte_id BIGINT NULL,
 cte_id_issue VARCHAR(64) COLLATE Latin1_General_100_BIN2 NULL,
 cte_emission_type_p VARCHAR(8) COLLATE Latin1_General_100_BIN2 NOT NULL,
 cte_emission_type_w VARCHAR(8) COLLATE Latin1_General_100_BIN2 NOT NULL,
 cte_emission_type_raw NVARCHAR(4000) COLLATE Latin1_General_100_BIN2 NULL,
 cte_emission_type NVARCHAR(50) COLLATE Latin1_General_100_BIN2 NULL,
 cte_emission_type_issue VARCHAR(64) COLLATE Latin1_General_100_BIN2 NULL,
 service_type_p VARCHAR(8) COLLATE Latin1_General_100_BIN2 NOT NULL,
 service_type_w VARCHAR(8) COLLATE Latin1_General_100_BIN2 NOT NULL,
 service_type_raw NVARCHAR(4000) COLLATE Latin1_General_100_BIN2 NULL,
 service_type INT NULL,
 service_type_issue VARCHAR(64) COLLATE Latin1_General_100_BIN2 NULL,
 insurance_enabled_p VARCHAR(8) COLLATE Latin1_General_100_BIN2 NOT NULL,
 insurance_enabled_w VARCHAR(8) COLLATE Latin1_General_100_BIN2 NOT NULL,
 insurance_enabled_raw NVARCHAR(4000) COLLATE Latin1_General_100_BIN2 NULL,
 insurance_enabled BIT NULL,
 insurance_enabled_issue VARCHAR(64) COLLATE Latin1_General_100_BIN2 NULL,
 gris_subtotal_p VARCHAR(8) COLLATE Latin1_General_100_BIN2 NOT NULL,
 gris_subtotal_w VARCHAR(8) COLLATE Latin1_General_100_BIN2 NOT NULL,
 gris_subtotal_raw NVARCHAR(4000) COLLATE Latin1_General_100_BIN2 NULL,
 gris_subtotal DECIMAL(28,8) NULL,
 gris_subtotal_issue VARCHAR(64) COLLATE Latin1_General_100_BIN2 NULL,
 tde_subtotal_p VARCHAR(8) COLLATE Latin1_General_100_BIN2 NOT NULL,
 tde_subtotal_w VARCHAR(8) COLLATE Latin1_General_100_BIN2 NOT NULL,
 tde_subtotal_raw NVARCHAR(4000) COLLATE Latin1_General_100_BIN2 NULL,
 tde_subtotal DECIMAL(28,8) NULL,
 tde_subtotal_issue VARCHAR(64) COLLATE Latin1_General_100_BIN2 NULL,
 modal_cte_p VARCHAR(8) COLLATE Latin1_General_100_BIN2 NOT NULL,
 modal_cte_w VARCHAR(8) COLLATE Latin1_General_100_BIN2 NOT NULL,
 modal_cte_raw NVARCHAR(4000) COLLATE Latin1_General_100_BIN2 NULL,
 modal_cte NVARCHAR(50) COLLATE Latin1_General_100_BIN2 NULL,
 modal_cte_issue VARCHAR(64) COLLATE Latin1_General_100_BIN2 NULL,
 redispatch_subtotal_p VARCHAR(8) COLLATE Latin1_General_100_BIN2 NOT NULL,
 redispatch_subtotal_w VARCHAR(8) COLLATE Latin1_General_100_BIN2 NOT NULL,
 redispatch_subtotal_raw NVARCHAR(4000) COLLATE Latin1_General_100_BIN2 NULL,
 redispatch_subtotal DECIMAL(28,8) NULL,
 redispatch_subtotal_issue VARCHAR(64) COLLATE Latin1_General_100_BIN2 NULL,
 suframa_subtotal_p VARCHAR(8) COLLATE Latin1_General_100_BIN2 NOT NULL,
 suframa_subtotal_w VARCHAR(8) COLLATE Latin1_General_100_BIN2 NOT NULL,
 suframa_subtotal_raw NVARCHAR(4000) COLLATE Latin1_General_100_BIN2 NULL,
 suframa_subtotal DECIMAL(28,8) NULL,
 suframa_subtotal_issue VARCHAR(64) COLLATE Latin1_General_100_BIN2 NULL,
 payment_type_p VARCHAR(8) COLLATE Latin1_General_100_BIN2 NOT NULL,
 payment_type_w VARCHAR(8) COLLATE Latin1_General_100_BIN2 NOT NULL,
 payment_type_raw NVARCHAR(4000) COLLATE Latin1_General_100_BIN2 NULL,
 payment_type NVARCHAR(50) COLLATE Latin1_General_100_BIN2 NULL,
 payment_type_issue VARCHAR(64) COLLATE Latin1_General_100_BIN2 NULL,
 previous_document_type_p VARCHAR(8) COLLATE Latin1_General_100_BIN2 NOT NULL,
 previous_document_type_w VARCHAR(8) COLLATE Latin1_General_100_BIN2 NOT NULL,
 previous_document_type_raw NVARCHAR(4000) COLLATE Latin1_General_100_BIN2 NULL,
 previous_document_type NVARCHAR(50) COLLATE Latin1_General_100_BIN2 NULL,
 previous_document_type_issue VARCHAR(64) COLLATE Latin1_General_100_BIN2 NULL,
 products_value_p VARCHAR(8) COLLATE Latin1_General_100_BIN2 NOT NULL,
 products_value_w VARCHAR(8) COLLATE Latin1_General_100_BIN2 NOT NULL,
 products_value_raw NVARCHAR(4000) COLLATE Latin1_General_100_BIN2 NULL,
 products_value DECIMAL(28,8) NULL,
 products_value_issue VARCHAR(64) COLLATE Latin1_General_100_BIN2 NULL,
 trt_subtotal_p VARCHAR(8) COLLATE Latin1_General_100_BIN2 NOT NULL,
 trt_subtotal_w VARCHAR(8) COLLATE Latin1_General_100_BIN2 NOT NULL,
 trt_subtotal_raw NVARCHAR(4000) COLLATE Latin1_General_100_BIN2 NULL,
 trt_subtotal DECIMAL(28,8) NULL,
 trt_subtotal_issue VARCHAR(64) COLLATE Latin1_General_100_BIN2 NULL,
 nfse_series_p VARCHAR(8) COLLATE Latin1_General_100_BIN2 NOT NULL,
 nfse_series_w VARCHAR(8) COLLATE Latin1_General_100_BIN2 NOT NULL,
 nfse_series_raw NVARCHAR(4000) COLLATE Latin1_General_100_BIN2 NULL,
 nfse_series NVARCHAR(50) COLLATE Latin1_General_100_BIN2 NULL,
 nfse_series_issue VARCHAR(64) COLLATE Latin1_General_100_BIN2 NULL,
 nfse_number_p VARCHAR(8) COLLATE Latin1_General_100_BIN2 NOT NULL,
 nfse_number_w VARCHAR(8) COLLATE Latin1_General_100_BIN2 NOT NULL,
 nfse_number_raw NVARCHAR(4000) COLLATE Latin1_General_100_BIN2 NULL,
 nfse_number INT NULL,
 nfse_number_issue VARCHAR(64) COLLATE Latin1_General_100_BIN2 NULL,
 insurance_id_p VARCHAR(8) COLLATE Latin1_General_100_BIN2 NOT NULL,
 insurance_id_w VARCHAR(8) COLLATE Latin1_General_100_BIN2 NOT NULL,
 insurance_id_raw NVARCHAR(4000) COLLATE Latin1_General_100_BIN2 NULL,
 insurance_id BIGINT NULL,
 insurance_id_issue VARCHAR(64) COLLATE Latin1_General_100_BIN2 NULL,
 other_fees_p VARCHAR(8) COLLATE Latin1_General_100_BIN2 NOT NULL,
 other_fees_w VARCHAR(8) COLLATE Latin1_General_100_BIN2 NOT NULL,
 other_fees_raw NVARCHAR(4000) COLLATE Latin1_General_100_BIN2 NULL,
 other_fees DECIMAL(28,8) NULL,
 other_fees_issue VARCHAR(64) COLLATE Latin1_General_100_BIN2 NULL,
 km_p VARCHAR(8) COLLATE Latin1_General_100_BIN2 NOT NULL,
 km_w VARCHAR(8) COLLATE Latin1_General_100_BIN2 NOT NULL,
 km_raw NVARCHAR(4000) COLLATE Latin1_General_100_BIN2 NULL,
 km DECIMAL(28,8) NULL,
 km_issue VARCHAR(64) COLLATE Latin1_General_100_BIN2 NULL,
 payment_accountable_type_p VARCHAR(8) COLLATE Latin1_General_100_BIN2 NOT NULL,
 payment_accountable_type_w VARCHAR(8) COLLATE Latin1_General_100_BIN2 NOT NULL,
 payment_accountable_type_raw NVARCHAR(4000) COLLATE Latin1_General_100_BIN2 NULL,
 payment_accountable_type INT NULL,
 payment_accountable_type_issue VARCHAR(64) COLLATE Latin1_General_100_BIN2 NULL,
 insured_value_p VARCHAR(8) COLLATE Latin1_General_100_BIN2 NOT NULL,
 insured_value_w VARCHAR(8) COLLATE Latin1_General_100_BIN2 NOT NULL,
 insured_value_raw NVARCHAR(4000) COLLATE Latin1_General_100_BIN2 NULL,
 insured_value DECIMAL(28,8) NULL,
 insured_value_issue VARCHAR(64) COLLATE Latin1_General_100_BIN2 NULL,
 globalized_p VARCHAR(8) COLLATE Latin1_General_100_BIN2 NOT NULL,
 globalized_w VARCHAR(8) COLLATE Latin1_General_100_BIN2 NOT NULL,
 globalized_raw NVARCHAR(4000) COLLATE Latin1_General_100_BIN2 NULL,
 globalized BIT NULL,
 globalized_issue VARCHAR(64) COLLATE Latin1_General_100_BIN2 NULL,
 sec_cat_subtotal_p VARCHAR(8) COLLATE Latin1_General_100_BIN2 NOT NULL,
 sec_cat_subtotal_w VARCHAR(8) COLLATE Latin1_General_100_BIN2 NOT NULL,
 sec_cat_subtotal_raw NVARCHAR(4000) COLLATE Latin1_General_100_BIN2 NULL,
 sec_cat_subtotal DECIMAL(28,8) NULL,
 sec_cat_subtotal_issue VARCHAR(64) COLLATE Latin1_General_100_BIN2 NULL,
 globalized_type_p VARCHAR(8) COLLATE Latin1_General_100_BIN2 NOT NULL,
 globalized_type_w VARCHAR(8) COLLATE Latin1_General_100_BIN2 NOT NULL,
 globalized_type_raw NVARCHAR(4000) COLLATE Latin1_General_100_BIN2 NULL,
 globalized_type NVARCHAR(50) COLLATE Latin1_General_100_BIN2 NULL,
 globalized_type_issue VARCHAR(64) COLLATE Latin1_General_100_BIN2 NULL,
 price_table_accountable_type_p VARCHAR(8) COLLATE Latin1_General_100_BIN2 NOT NULL,
 price_table_accountable_type_w VARCHAR(8) COLLATE Latin1_General_100_BIN2 NOT NULL,
 price_table_accountable_type_raw NVARCHAR(4000) COLLATE Latin1_General_100_BIN2 NULL,
 price_table_accountable_type INT NULL,
 price_table_accountable_type_issue VARCHAR(64) COLLATE Latin1_General_100_BIN2 NULL,
 insurance_accountable_type_p VARCHAR(8) COLLATE Latin1_General_100_BIN2 NOT NULL,
 insurance_accountable_type_w VARCHAR(8) COLLATE Latin1_General_100_BIN2 NOT NULL,
 insurance_accountable_type_raw NVARCHAR(4000) COLLATE Latin1_General_100_BIN2 NULL,
 insurance_accountable_type INT NULL,
 insurance_accountable_type_issue VARCHAR(64) COLLATE Latin1_General_100_BIN2 NULL,
 fiscal_calculation_basis_p VARCHAR(8) COLLATE Latin1_General_100_BIN2 NOT NULL,
 fiscal_calculation_basis_w VARCHAR(8) COLLATE Latin1_General_100_BIN2 NOT NULL,
 fiscal_calculation_basis_raw NVARCHAR(4000) COLLATE Latin1_General_100_BIN2 NULL,
 fiscal_calculation_basis DECIMAL(28,8) NULL,
 fiscal_calculation_basis_issue VARCHAR(64) COLLATE Latin1_General_100_BIN2 NULL,
 fiscal_tax_rate_p VARCHAR(8) COLLATE Latin1_General_100_BIN2 NOT NULL,
 fiscal_tax_rate_w VARCHAR(8) COLLATE Latin1_General_100_BIN2 NOT NULL,
 fiscal_tax_rate_raw NVARCHAR(4000) COLLATE Latin1_General_100_BIN2 NULL,
 fiscal_tax_rate DECIMAL(28,8) NULL,
 fiscal_tax_rate_issue VARCHAR(64) COLLATE Latin1_General_100_BIN2 NULL,
 fiscal_pis_rate_p VARCHAR(8) COLLATE Latin1_General_100_BIN2 NOT NULL,
 fiscal_pis_rate_w VARCHAR(8) COLLATE Latin1_General_100_BIN2 NOT NULL,
 fiscal_pis_rate_raw NVARCHAR(4000) COLLATE Latin1_General_100_BIN2 NULL,
 fiscal_pis_rate DECIMAL(28,8) NULL,
 fiscal_pis_rate_issue VARCHAR(64) COLLATE Latin1_General_100_BIN2 NULL,
 fiscal_cofins_rate_p VARCHAR(8) COLLATE Latin1_General_100_BIN2 NOT NULL,
 fiscal_cofins_rate_w VARCHAR(8) COLLATE Latin1_General_100_BIN2 NOT NULL,
 fiscal_cofins_rate_raw NVARCHAR(4000) COLLATE Latin1_General_100_BIN2 NULL,
 fiscal_cofins_rate DECIMAL(28,8) NULL,
 fiscal_cofins_rate_issue VARCHAR(64) COLLATE Latin1_General_100_BIN2 NULL,
 fiscal_has_difal_p VARCHAR(8) COLLATE Latin1_General_100_BIN2 NOT NULL,
 fiscal_has_difal_w VARCHAR(8) COLLATE Latin1_General_100_BIN2 NOT NULL,
 fiscal_has_difal_raw NVARCHAR(4000) COLLATE Latin1_General_100_BIN2 NULL,
 fiscal_has_difal BIT NULL,
 fiscal_has_difal_issue VARCHAR(64) COLLATE Latin1_General_100_BIN2 NULL,
 fiscal_difal_origin_p VARCHAR(8) COLLATE Latin1_General_100_BIN2 NOT NULL,
 fiscal_difal_origin_w VARCHAR(8) COLLATE Latin1_General_100_BIN2 NOT NULL,
 fiscal_difal_origin_raw NVARCHAR(4000) COLLATE Latin1_General_100_BIN2 NULL,
 fiscal_difal_origin DECIMAL(28,8) NULL,
 fiscal_difal_origin_issue VARCHAR(64) COLLATE Latin1_General_100_BIN2 NULL,
 fiscal_difal_destination_p VARCHAR(8) COLLATE Latin1_General_100_BIN2 NOT NULL,
 fiscal_difal_destination_w VARCHAR(8) COLLATE Latin1_General_100_BIN2 NOT NULL,
 fiscal_difal_destination_raw NVARCHAR(4000) COLLATE Latin1_General_100_BIN2 NULL,
 fiscal_difal_destination DECIMAL(28,8) NULL,
 fiscal_difal_destination_issue VARCHAR(64) COLLATE Latin1_General_100_BIN2 NULL,
 fiscal_cst_type_p VARCHAR(8) COLLATE Latin1_General_100_BIN2 NOT NULL,
 fiscal_cst_type_w VARCHAR(8) COLLATE Latin1_General_100_BIN2 NOT NULL,
 fiscal_cst_type_raw NVARCHAR(4000) COLLATE Latin1_General_100_BIN2 NULL,
 fiscal_cst_type NVARCHAR(10) COLLATE Latin1_General_100_BIN2 NULL,
 fiscal_cst_type_issue VARCHAR(64) COLLATE Latin1_General_100_BIN2 NULL,
 fiscal_cfop_code_p VARCHAR(8) COLLATE Latin1_General_100_BIN2 NOT NULL,
 fiscal_cfop_code_w VARCHAR(8) COLLATE Latin1_General_100_BIN2 NOT NULL,
 fiscal_cfop_code_raw NVARCHAR(4000) COLLATE Latin1_General_100_BIN2 NULL,
 fiscal_cfop_code NVARCHAR(10) COLLATE Latin1_General_100_BIN2 NULL,
 fiscal_cfop_code_issue VARCHAR(64) COLLATE Latin1_General_100_BIN2 NULL,
 fiscal_tax_value_p VARCHAR(8) COLLATE Latin1_General_100_BIN2 NOT NULL,
 fiscal_tax_value_w VARCHAR(8) COLLATE Latin1_General_100_BIN2 NOT NULL,
 fiscal_tax_value_raw NVARCHAR(4000) COLLATE Latin1_General_100_BIN2 NULL,
 fiscal_tax_value DECIMAL(28,8) NULL,
 fiscal_tax_value_issue VARCHAR(64) COLLATE Latin1_General_100_BIN2 NULL,
 fiscal_pis_value_p VARCHAR(8) COLLATE Latin1_General_100_BIN2 NOT NULL,
 fiscal_pis_value_w VARCHAR(8) COLLATE Latin1_General_100_BIN2 NOT NULL,
 fiscal_pis_value_raw NVARCHAR(4000) COLLATE Latin1_General_100_BIN2 NULL,
 fiscal_pis_value DECIMAL(28,8) NULL,
 fiscal_pis_value_issue VARCHAR(64) COLLATE Latin1_General_100_BIN2 NULL,
 fiscal_cofins_value_p VARCHAR(8) COLLATE Latin1_General_100_BIN2 NOT NULL,
 fiscal_cofins_value_w VARCHAR(8) COLLATE Latin1_General_100_BIN2 NOT NULL,
 fiscal_cofins_value_raw NVARCHAR(4000) COLLATE Latin1_General_100_BIN2 NULL,
 fiscal_cofins_value DECIMAL(28,8) NULL,
 fiscal_cofins_value_issue VARCHAR(64) COLLATE Latin1_General_100_BIN2 NULL,
 cubages_cubed_weight_p VARCHAR(8) COLLATE Latin1_General_100_BIN2 NOT NULL,
 cubages_cubed_weight_w VARCHAR(8) COLLATE Latin1_General_100_BIN2 NOT NULL,
 cubages_cubed_weight_raw NVARCHAR(4000) COLLATE Latin1_General_100_BIN2 NULL,
 cubages_cubed_weight DECIMAL(28,8) NULL,
 cubages_cubed_weight_issue VARCHAR(64) COLLATE Latin1_General_100_BIN2 NULL,
 freight_weight_subtotal_p VARCHAR(8) COLLATE Latin1_General_100_BIN2 NOT NULL,
 freight_weight_subtotal_w VARCHAR(8) COLLATE Latin1_General_100_BIN2 NOT NULL,
 freight_weight_subtotal_raw NVARCHAR(4000) COLLATE Latin1_General_100_BIN2 NULL,
 freight_weight_subtotal DECIMAL(28,8) NULL,
 freight_weight_subtotal_issue VARCHAR(64) COLLATE Latin1_General_100_BIN2 NULL,
 ad_valorem_subtotal_p VARCHAR(8) COLLATE Latin1_General_100_BIN2 NOT NULL,
 ad_valorem_subtotal_w VARCHAR(8) COLLATE Latin1_General_100_BIN2 NOT NULL,
 ad_valorem_subtotal_raw NVARCHAR(4000) COLLATE Latin1_General_100_BIN2 NULL,
 ad_valorem_subtotal DECIMAL(28,8) NULL,
 ad_valorem_subtotal_issue VARCHAR(64) COLLATE Latin1_General_100_BIN2 NULL,
 toll_subtotal_p VARCHAR(8) COLLATE Latin1_General_100_BIN2 NOT NULL,
 toll_subtotal_w VARCHAR(8) COLLATE Latin1_General_100_BIN2 NOT NULL,
 toll_subtotal_raw NVARCHAR(4000) COLLATE Latin1_General_100_BIN2 NULL,
 toll_subtotal DECIMAL(28,8) NULL,
 toll_subtotal_issue VARCHAR(64) COLLATE Latin1_General_100_BIN2 NULL,
 itr_subtotal_p VARCHAR(8) COLLATE Latin1_General_100_BIN2 NOT NULL,
 itr_subtotal_w VARCHAR(8) COLLATE Latin1_General_100_BIN2 NOT NULL,
 itr_subtotal_raw NVARCHAR(4000) COLLATE Latin1_General_100_BIN2 NULL,
 itr_subtotal DECIMAL(28,8) NULL,
 itr_subtotal_issue VARCHAR(64) COLLATE Latin1_General_100_BIN2 NULL,
 pagador_documento_p VARCHAR(8) COLLATE Latin1_General_100_BIN2 NOT NULL,
 pagador_documento_w VARCHAR(8) COLLATE Latin1_General_100_BIN2 NOT NULL,
 pagador_documento_raw NVARCHAR(4000) COLLATE Latin1_General_100_BIN2 NULL,
 pagador_documento NVARCHAR(50) COLLATE Latin1_General_100_BIN2 NULL,
 pagador_documento_issue VARCHAR(64) COLLATE Latin1_General_100_BIN2 NULL,
 remetente_documento_p VARCHAR(8) COLLATE Latin1_General_100_BIN2 NOT NULL,
 remetente_documento_w VARCHAR(8) COLLATE Latin1_General_100_BIN2 NOT NULL,
 remetente_documento_raw NVARCHAR(4000) COLLATE Latin1_General_100_BIN2 NULL,
 remetente_documento NVARCHAR(50) COLLATE Latin1_General_100_BIN2 NULL,
 remetente_documento_issue VARCHAR(64) COLLATE Latin1_General_100_BIN2 NULL,
 destinatario_documento_p VARCHAR(8) COLLATE Latin1_General_100_BIN2 NOT NULL,
 destinatario_documento_w VARCHAR(8) COLLATE Latin1_General_100_BIN2 NOT NULL,
 destinatario_documento_raw NVARCHAR(4000) COLLATE Latin1_General_100_BIN2 NULL,
 destinatario_documento NVARCHAR(50) COLLATE Latin1_General_100_BIN2 NULL,
 destinatario_documento_issue VARCHAR(64) COLLATE Latin1_General_100_BIN2 NULL,
 filial_cnpj_p VARCHAR(8) COLLATE Latin1_General_100_BIN2 NOT NULL,
 filial_cnpj_w VARCHAR(8) COLLATE Latin1_General_100_BIN2 NOT NULL,
 filial_cnpj_raw NVARCHAR(4000) COLLATE Latin1_General_100_BIN2 NULL,
 filial_cnpj NVARCHAR(50) COLLATE Latin1_General_100_BIN2 NULL,
 filial_cnpj_issue VARCHAR(64) COLLATE Latin1_General_100_BIN2 NULL,
 nfse_integration_id_p VARCHAR(8) COLLATE Latin1_General_100_BIN2 NOT NULL,
 nfse_integration_id_w VARCHAR(8) COLLATE Latin1_General_100_BIN2 NOT NULL,
 nfse_integration_id_raw NVARCHAR(4000) COLLATE Latin1_General_100_BIN2 NULL,
 nfse_integration_id NVARCHAR(50) COLLATE Latin1_General_100_BIN2 NULL,
 nfse_integration_id_issue VARCHAR(64) COLLATE Latin1_General_100_BIN2 NULL,
 nfse_status_p VARCHAR(8) COLLATE Latin1_General_100_BIN2 NOT NULL,
 nfse_status_w VARCHAR(8) COLLATE Latin1_General_100_BIN2 NOT NULL,
 nfse_status_raw NVARCHAR(4000) COLLATE Latin1_General_100_BIN2 NULL,
 nfse_status NVARCHAR(50) COLLATE Latin1_General_100_BIN2 NULL,
 nfse_status_issue VARCHAR(64) COLLATE Latin1_General_100_BIN2 NULL,
 nfse_issued_at_p VARCHAR(8) COLLATE Latin1_General_100_BIN2 NOT NULL,
 nfse_issued_at_w VARCHAR(8) COLLATE Latin1_General_100_BIN2 NOT NULL,
 nfse_issued_at_raw NVARCHAR(4000) COLLATE Latin1_General_100_BIN2 NULL,
 nfse_issued_at DATE NULL,
 nfse_issued_at_issue VARCHAR(64) COLLATE Latin1_General_100_BIN2 NULL,
 nfse_cancelation_reason_p VARCHAR(8) COLLATE Latin1_General_100_BIN2 NOT NULL,
 nfse_cancelation_reason_w VARCHAR(8) COLLATE Latin1_General_100_BIN2 NOT NULL,
 nfse_cancelation_reason_raw NVARCHAR(4000) COLLATE Latin1_General_100_BIN2 NULL,
 nfse_cancelation_reason NVARCHAR(255) COLLATE Latin1_General_100_BIN2 NULL,
 nfse_cancelation_reason_issue VARCHAR(64) COLLATE Latin1_General_100_BIN2 NULL,
 nfse_pdf_service_url_p VARCHAR(8) COLLATE Latin1_General_100_BIN2 NOT NULL,
 nfse_pdf_service_url_w VARCHAR(8) COLLATE Latin1_General_100_BIN2 NOT NULL,
 nfse_pdf_service_url_raw NVARCHAR(4000) COLLATE Latin1_General_100_BIN2 NULL,
 nfse_pdf_service_url NVARCHAR(1000) COLLATE Latin1_General_100_BIN2 NULL,
 nfse_pdf_service_url_issue VARCHAR(64) COLLATE Latin1_General_100_BIN2 NULL,
 nfse_corporation_id_p VARCHAR(8) COLLATE Latin1_General_100_BIN2 NOT NULL,
 nfse_corporation_id_w VARCHAR(8) COLLATE Latin1_General_100_BIN2 NOT NULL,
 nfse_corporation_id_raw NVARCHAR(4000) COLLATE Latin1_General_100_BIN2 NULL,
 nfse_corporation_id BIGINT NULL,
 nfse_corporation_id_issue VARCHAR(64) COLLATE Latin1_General_100_BIN2 NULL,
 nfse_service_description_p VARCHAR(8) COLLATE Latin1_General_100_BIN2 NOT NULL,
 nfse_service_description_w VARCHAR(8) COLLATE Latin1_General_100_BIN2 NOT NULL,
 nfse_service_description_raw NVARCHAR(4000) COLLATE Latin1_General_100_BIN2 NULL,
 nfse_service_description NVARCHAR(500) COLLATE Latin1_General_100_BIN2 NULL,
 nfse_service_description_issue VARCHAR(64) COLLATE Latin1_General_100_BIN2 NULL,
 nfse_xml_document_p VARCHAR(8) COLLATE Latin1_General_100_BIN2 NOT NULL,
 nfse_xml_document_w VARCHAR(8) COLLATE Latin1_General_100_BIN2 NOT NULL,
 nfse_xml_document_raw NVARCHAR(4000) COLLATE Latin1_General_100_BIN2 NULL,
 nfse_xml_document NVARCHAR(1024) COLLATE Latin1_General_100_BIN2 NULL,
 nfse_xml_document_issue VARCHAR(64) COLLATE Latin1_General_100_BIN2 NULL,
 CONSTRAINT CK_analytic_freight_attributes CHECK(revision BETWEEN 1 AND 100000 AND evidence LIKE 'synthetic-%'
 AND DATALENGTH(comparison_bytes) BETWEEN 1 AND 262144),
 CONSTRAINT CK_analytic_fa_modal CHECK(modal_p IN('ABSENT','NULL','VALUE')
 AND modal_w IN('ABSENT','NULL','STRING','INTEGER','NUMBER','BOOLEAN','ARRAY','OBJECT')
 AND ((modal_p='ABSENT' AND modal_w='ABSENT') OR(modal_p='NULL' AND modal_w='NULL')
 OR(modal_p='VALUE' AND modal_w NOT IN('ABSENT','NULL')))
 AND ((modal_p<>'VALUE' AND modal_raw IS NULL AND modal IS NULL AND modal_issue IS NULL)
 OR(modal_p='VALUE' AND modal_raw IS NOT NULL AND ((modal IS NULL AND modal_issue IS NOT NULL)
 OR(modal IS NOT NULL AND modal_issue IS NULL))))),
 CONSTRAINT CK_analytic_fa_tipo_frete CHECK(tipo_frete_p IN('ABSENT','NULL','VALUE')
 AND tipo_frete_w IN('ABSENT','NULL','STRING','INTEGER','NUMBER','BOOLEAN','ARRAY','OBJECT')
 AND ((tipo_frete_p='ABSENT' AND tipo_frete_w='ABSENT') OR(tipo_frete_p='NULL' AND tipo_frete_w='NULL')
 OR(tipo_frete_p='VALUE' AND tipo_frete_w NOT IN('ABSENT','NULL')))
 AND ((tipo_frete_p<>'VALUE' AND tipo_frete_raw IS NULL AND tipo_frete IS NULL AND tipo_frete_issue IS NULL)
 OR(tipo_frete_p='VALUE' AND tipo_frete_raw IS NOT NULL AND ((tipo_frete IS NULL AND tipo_frete_issue IS NOT NULL)
 OR(tipo_frete IS NOT NULL AND tipo_frete_issue IS NULL))))),
 CONSTRAINT CK_analytic_fa_accounting_credit_id CHECK(accounting_credit_id_p IN('ABSENT','NULL','VALUE')
 AND accounting_credit_id_w IN('ABSENT','NULL','STRING','INTEGER','NUMBER','BOOLEAN','ARRAY','OBJECT')
 AND ((accounting_credit_id_p='ABSENT' AND accounting_credit_id_w='ABSENT') OR(accounting_credit_id_p='NULL' AND accounting_credit_id_w='NULL')
 OR(accounting_credit_id_p='VALUE' AND accounting_credit_id_w NOT IN('ABSENT','NULL')))
 AND ((accounting_credit_id_p<>'VALUE' AND accounting_credit_id_raw IS NULL AND accounting_credit_id IS NULL AND accounting_credit_id_issue IS NULL)
 OR(accounting_credit_id_p='VALUE' AND accounting_credit_id_raw IS NOT NULL AND ((accounting_credit_id IS NULL AND accounting_credit_id_issue IS NOT NULL)
 OR(accounting_credit_id IS NOT NULL AND accounting_credit_id_issue IS NULL))))),
 CONSTRAINT CK_analytic_fa_accounting_credit_installment_id CHECK(accounting_credit_installment_id_p IN('ABSENT','NULL','VALUE')
 AND accounting_credit_installment_id_w IN('ABSENT','NULL','STRING','INTEGER','NUMBER','BOOLEAN','ARRAY','OBJECT')
 AND ((accounting_credit_installment_id_p='ABSENT' AND accounting_credit_installment_id_w='ABSENT') OR(accounting_credit_installment_id_p='NULL' AND accounting_credit_installment_id_w='NULL')
 OR(accounting_credit_installment_id_p='VALUE' AND accounting_credit_installment_id_w NOT IN('ABSENT','NULL')))
 AND ((accounting_credit_installment_id_p<>'VALUE' AND accounting_credit_installment_id_raw IS NULL AND accounting_credit_installment_id IS NULL AND accounting_credit_installment_id_issue IS NULL)
 OR(accounting_credit_installment_id_p='VALUE' AND accounting_credit_installment_id_raw IS NOT NULL AND ((accounting_credit_installment_id IS NULL AND accounting_credit_installment_id_issue IS NOT NULL)
 OR(accounting_credit_installment_id IS NOT NULL AND accounting_credit_installment_id_issue IS NULL))))),
 CONSTRAINT CK_analytic_fa_valor_notas CHECK(valor_notas_p IN('ABSENT','NULL','VALUE')
 AND valor_notas_w IN('ABSENT','NULL','STRING','INTEGER','NUMBER','BOOLEAN','ARRAY','OBJECT')
 AND ((valor_notas_p='ABSENT' AND valor_notas_w='ABSENT') OR(valor_notas_p='NULL' AND valor_notas_w='NULL')
 OR(valor_notas_p='VALUE' AND valor_notas_w NOT IN('ABSENT','NULL')))
 AND ((valor_notas_p<>'VALUE' AND valor_notas_raw IS NULL AND valor_notas IS NULL AND valor_notas_issue IS NULL)
 OR(valor_notas_p='VALUE' AND valor_notas_raw IS NOT NULL AND ((valor_notas IS NULL AND valor_notas_issue IS NOT NULL)
 OR(valor_notas IS NOT NULL AND valor_notas_issue IS NULL))))),
 CONSTRAINT CK_analytic_fa_peso_notas CHECK(peso_notas_p IN('ABSENT','NULL','VALUE')
 AND peso_notas_w IN('ABSENT','NULL','STRING','INTEGER','NUMBER','BOOLEAN','ARRAY','OBJECT')
 AND ((peso_notas_p='ABSENT' AND peso_notas_w='ABSENT') OR(peso_notas_p='NULL' AND peso_notas_w='NULL')
 OR(peso_notas_p='VALUE' AND peso_notas_w NOT IN('ABSENT','NULL')))
 AND ((peso_notas_p<>'VALUE' AND peso_notas_raw IS NULL AND peso_notas IS NULL AND peso_notas_issue IS NULL)
 OR(peso_notas_p='VALUE' AND peso_notas_raw IS NOT NULL AND ((peso_notas IS NULL AND peso_notas_issue IS NOT NULL)
 OR(peso_notas IS NOT NULL AND peso_notas_issue IS NULL))))),
 CONSTRAINT CK_analytic_fa_id_corporacao CHECK(id_corporacao_p IN('ABSENT','NULL','VALUE')
 AND id_corporacao_w IN('ABSENT','NULL','STRING','INTEGER','NUMBER','BOOLEAN','ARRAY','OBJECT')
 AND ((id_corporacao_p='ABSENT' AND id_corporacao_w='ABSENT') OR(id_corporacao_p='NULL' AND id_corporacao_w='NULL')
 OR(id_corporacao_p='VALUE' AND id_corporacao_w NOT IN('ABSENT','NULL')))
 AND ((id_corporacao_p<>'VALUE' AND id_corporacao_raw IS NULL AND id_corporacao IS NULL AND id_corporacao_issue IS NULL)
 OR(id_corporacao_p='VALUE' AND id_corporacao_raw IS NOT NULL AND ((id_corporacao IS NULL AND id_corporacao_issue IS NOT NULL)
 OR(id_corporacao IS NOT NULL AND id_corporacao_issue IS NULL))))),
 CONSTRAINT CK_analytic_fa_id_cidade_destino CHECK(id_cidade_destino_p IN('ABSENT','NULL','VALUE')
 AND id_cidade_destino_w IN('ABSENT','NULL','STRING','INTEGER','NUMBER','BOOLEAN','ARRAY','OBJECT')
 AND ((id_cidade_destino_p='ABSENT' AND id_cidade_destino_w='ABSENT') OR(id_cidade_destino_p='NULL' AND id_cidade_destino_w='NULL')
 OR(id_cidade_destino_p='VALUE' AND id_cidade_destino_w NOT IN('ABSENT','NULL')))
 AND ((id_cidade_destino_p<>'VALUE' AND id_cidade_destino_raw IS NULL AND id_cidade_destino IS NULL AND id_cidade_destino_issue IS NULL)
 OR(id_cidade_destino_p='VALUE' AND id_cidade_destino_raw IS NOT NULL AND ((id_cidade_destino IS NULL AND id_cidade_destino_issue IS NOT NULL)
 OR(id_cidade_destino IS NOT NULL AND id_cidade_destino_issue IS NULL))))),
 CONSTRAINT CK_analytic_fa_data_previsao_entrega CHECK(data_previsao_entrega_p IN('ABSENT','NULL','VALUE')
 AND data_previsao_entrega_w IN('ABSENT','NULL','STRING','INTEGER','NUMBER','BOOLEAN','ARRAY','OBJECT')
 AND ((data_previsao_entrega_p='ABSENT' AND data_previsao_entrega_w='ABSENT') OR(data_previsao_entrega_p='NULL' AND data_previsao_entrega_w='NULL')
 OR(data_previsao_entrega_p='VALUE' AND data_previsao_entrega_w NOT IN('ABSENT','NULL')))
 AND ((data_previsao_entrega_p<>'VALUE' AND data_previsao_entrega_raw IS NULL AND data_previsao_entrega IS NULL AND data_previsao_entrega_issue IS NULL)
 OR(data_previsao_entrega_p='VALUE' AND data_previsao_entrega_raw IS NOT NULL AND ((data_previsao_entrega IS NULL AND data_previsao_entrega_issue IS NOT NULL)
 OR(data_previsao_entrega IS NOT NULL AND data_previsao_entrega_issue IS NULL))))),
 CONSTRAINT CK_analytic_fa_service_date CHECK(service_date_p IN('ABSENT','NULL','VALUE')
 AND service_date_w IN('ABSENT','NULL','STRING','INTEGER','NUMBER','BOOLEAN','ARRAY','OBJECT')
 AND ((service_date_p='ABSENT' AND service_date_w='ABSENT') OR(service_date_p='NULL' AND service_date_w='NULL')
 OR(service_date_p='VALUE' AND service_date_w NOT IN('ABSENT','NULL')))
 AND ((service_date_p<>'VALUE' AND service_date_raw IS NULL AND service_date IS NULL AND service_date_issue IS NULL)
 OR(service_date_p='VALUE' AND service_date_raw IS NOT NULL AND ((service_date IS NULL AND service_date_issue IS NOT NULL)
 OR(service_date IS NOT NULL AND service_date_issue IS NULL))))),
 CONSTRAINT CK_analytic_fa_pick_item_id CHECK(pick_item_id_p IN('ABSENT','NULL','VALUE')
 AND pick_item_id_w IN('ABSENT','NULL','STRING','INTEGER','NUMBER','BOOLEAN','ARRAY','OBJECT')
 AND ((pick_item_id_p='ABSENT' AND pick_item_id_w='ABSENT') OR(pick_item_id_p='NULL' AND pick_item_id_w='NULL')
 OR(pick_item_id_p='VALUE' AND pick_item_id_w NOT IN('ABSENT','NULL')))
 AND ((pick_item_id_p<>'VALUE' AND pick_item_id_raw IS NULL AND pick_item_id IS NULL AND pick_item_id_issue IS NULL)
 OR(pick_item_id_p='VALUE' AND pick_item_id_raw IS NOT NULL AND ((pick_item_id IS NULL AND pick_item_id_issue IS NOT NULL)
 OR(pick_item_id IS NOT NULL AND pick_item_id_issue IS NULL))))),
 CONSTRAINT CK_analytic_fa_pagador_id CHECK(pagador_id_p IN('ABSENT','NULL','VALUE')
 AND pagador_id_w IN('ABSENT','NULL','STRING','INTEGER','NUMBER','BOOLEAN','ARRAY','OBJECT')
 AND ((pagador_id_p='ABSENT' AND pagador_id_w='ABSENT') OR(pagador_id_p='NULL' AND pagador_id_w='NULL')
 OR(pagador_id_p='VALUE' AND pagador_id_w NOT IN('ABSENT','NULL')))
 AND ((pagador_id_p<>'VALUE' AND pagador_id_raw IS NULL AND pagador_id IS NULL AND pagador_id_issue IS NULL)
 OR(pagador_id_p='VALUE' AND pagador_id_raw IS NOT NULL AND ((pagador_id IS NULL AND pagador_id_issue IS NOT NULL)
 OR(pagador_id IS NOT NULL AND pagador_id_issue IS NULL))))),
 CONSTRAINT CK_analytic_fa_pagador_nome CHECK(pagador_nome_p IN('ABSENT','NULL','VALUE')
 AND pagador_nome_w IN('ABSENT','NULL','STRING','INTEGER','NUMBER','BOOLEAN','ARRAY','OBJECT')
 AND ((pagador_nome_p='ABSENT' AND pagador_nome_w='ABSENT') OR(pagador_nome_p='NULL' AND pagador_nome_w='NULL')
 OR(pagador_nome_p='VALUE' AND pagador_nome_w NOT IN('ABSENT','NULL')))
 AND ((pagador_nome_p<>'VALUE' AND pagador_nome_raw IS NULL AND pagador_nome IS NULL AND pagador_nome_issue IS NULL)
 OR(pagador_nome_p='VALUE' AND pagador_nome_raw IS NOT NULL AND ((pagador_nome IS NULL AND pagador_nome_issue IS NOT NULL)
 OR(pagador_nome IS NOT NULL AND pagador_nome_issue IS NULL))))),
 CONSTRAINT CK_analytic_fa_remetente_id CHECK(remetente_id_p IN('ABSENT','NULL','VALUE')
 AND remetente_id_w IN('ABSENT','NULL','STRING','INTEGER','NUMBER','BOOLEAN','ARRAY','OBJECT')
 AND ((remetente_id_p='ABSENT' AND remetente_id_w='ABSENT') OR(remetente_id_p='NULL' AND remetente_id_w='NULL')
 OR(remetente_id_p='VALUE' AND remetente_id_w NOT IN('ABSENT','NULL')))
 AND ((remetente_id_p<>'VALUE' AND remetente_id_raw IS NULL AND remetente_id IS NULL AND remetente_id_issue IS NULL)
 OR(remetente_id_p='VALUE' AND remetente_id_raw IS NOT NULL AND ((remetente_id IS NULL AND remetente_id_issue IS NOT NULL)
 OR(remetente_id IS NOT NULL AND remetente_id_issue IS NULL))))),
 CONSTRAINT CK_analytic_fa_remetente_nome CHECK(remetente_nome_p IN('ABSENT','NULL','VALUE')
 AND remetente_nome_w IN('ABSENT','NULL','STRING','INTEGER','NUMBER','BOOLEAN','ARRAY','OBJECT')
 AND ((remetente_nome_p='ABSENT' AND remetente_nome_w='ABSENT') OR(remetente_nome_p='NULL' AND remetente_nome_w='NULL')
 OR(remetente_nome_p='VALUE' AND remetente_nome_w NOT IN('ABSENT','NULL')))
 AND ((remetente_nome_p<>'VALUE' AND remetente_nome_raw IS NULL AND remetente_nome IS NULL AND remetente_nome_issue IS NULL)
 OR(remetente_nome_p='VALUE' AND remetente_nome_raw IS NOT NULL AND ((remetente_nome IS NULL AND remetente_nome_issue IS NOT NULL)
 OR(remetente_nome IS NOT NULL AND remetente_nome_issue IS NULL))))),
 CONSTRAINT CK_analytic_fa_origem_cidade CHECK(origem_cidade_p IN('ABSENT','NULL','VALUE')
 AND origem_cidade_w IN('ABSENT','NULL','STRING','INTEGER','NUMBER','BOOLEAN','ARRAY','OBJECT')
 AND ((origem_cidade_p='ABSENT' AND origem_cidade_w='ABSENT') OR(origem_cidade_p='NULL' AND origem_cidade_w='NULL')
 OR(origem_cidade_p='VALUE' AND origem_cidade_w NOT IN('ABSENT','NULL')))
 AND ((origem_cidade_p<>'VALUE' AND origem_cidade_raw IS NULL AND origem_cidade IS NULL AND origem_cidade_issue IS NULL)
 OR(origem_cidade_p='VALUE' AND origem_cidade_raw IS NOT NULL AND ((origem_cidade IS NULL AND origem_cidade_issue IS NOT NULL)
 OR(origem_cidade IS NOT NULL AND origem_cidade_issue IS NULL))))),
 CONSTRAINT CK_analytic_fa_origem_uf CHECK(origem_uf_p IN('ABSENT','NULL','VALUE')
 AND origem_uf_w IN('ABSENT','NULL','STRING','INTEGER','NUMBER','BOOLEAN','ARRAY','OBJECT')
 AND ((origem_uf_p='ABSENT' AND origem_uf_w='ABSENT') OR(origem_uf_p='NULL' AND origem_uf_w='NULL')
 OR(origem_uf_p='VALUE' AND origem_uf_w NOT IN('ABSENT','NULL')))
 AND ((origem_uf_p<>'VALUE' AND origem_uf_raw IS NULL AND origem_uf IS NULL AND origem_uf_issue IS NULL)
 OR(origem_uf_p='VALUE' AND origem_uf_raw IS NOT NULL AND ((origem_uf IS NULL AND origem_uf_issue IS NOT NULL)
 OR(origem_uf IS NOT NULL AND origem_uf_issue IS NULL))))),
 CONSTRAINT CK_analytic_fa_destinatario_id CHECK(destinatario_id_p IN('ABSENT','NULL','VALUE')
 AND destinatario_id_w IN('ABSENT','NULL','STRING','INTEGER','NUMBER','BOOLEAN','ARRAY','OBJECT')
 AND ((destinatario_id_p='ABSENT' AND destinatario_id_w='ABSENT') OR(destinatario_id_p='NULL' AND destinatario_id_w='NULL')
 OR(destinatario_id_p='VALUE' AND destinatario_id_w NOT IN('ABSENT','NULL')))
 AND ((destinatario_id_p<>'VALUE' AND destinatario_id_raw IS NULL AND destinatario_id IS NULL AND destinatario_id_issue IS NULL)
 OR(destinatario_id_p='VALUE' AND destinatario_id_raw IS NOT NULL AND ((destinatario_id IS NULL AND destinatario_id_issue IS NOT NULL)
 OR(destinatario_id IS NOT NULL AND destinatario_id_issue IS NULL))))),
 CONSTRAINT CK_analytic_fa_destinatario_nome CHECK(destinatario_nome_p IN('ABSENT','NULL','VALUE')
 AND destinatario_nome_w IN('ABSENT','NULL','STRING','INTEGER','NUMBER','BOOLEAN','ARRAY','OBJECT')
 AND ((destinatario_nome_p='ABSENT' AND destinatario_nome_w='ABSENT') OR(destinatario_nome_p='NULL' AND destinatario_nome_w='NULL')
 OR(destinatario_nome_p='VALUE' AND destinatario_nome_w NOT IN('ABSENT','NULL')))
 AND ((destinatario_nome_p<>'VALUE' AND destinatario_nome_raw IS NULL AND destinatario_nome IS NULL AND destinatario_nome_issue IS NULL)
 OR(destinatario_nome_p='VALUE' AND destinatario_nome_raw IS NOT NULL AND ((destinatario_nome IS NULL AND destinatario_nome_issue IS NOT NULL)
 OR(destinatario_nome IS NOT NULL AND destinatario_nome_issue IS NULL))))),
 CONSTRAINT CK_analytic_fa_destino_cidade CHECK(destino_cidade_p IN('ABSENT','NULL','VALUE')
 AND destino_cidade_w IN('ABSENT','NULL','STRING','INTEGER','NUMBER','BOOLEAN','ARRAY','OBJECT')
 AND ((destino_cidade_p='ABSENT' AND destino_cidade_w='ABSENT') OR(destino_cidade_p='NULL' AND destino_cidade_w='NULL')
 OR(destino_cidade_p='VALUE' AND destino_cidade_w NOT IN('ABSENT','NULL')))
 AND ((destino_cidade_p<>'VALUE' AND destino_cidade_raw IS NULL AND destino_cidade IS NULL AND destino_cidade_issue IS NULL)
 OR(destino_cidade_p='VALUE' AND destino_cidade_raw IS NOT NULL AND ((destino_cidade IS NULL AND destino_cidade_issue IS NOT NULL)
 OR(destino_cidade IS NOT NULL AND destino_cidade_issue IS NULL))))),
 CONSTRAINT CK_analytic_fa_destino_uf CHECK(destino_uf_p IN('ABSENT','NULL','VALUE')
 AND destino_uf_w IN('ABSENT','NULL','STRING','INTEGER','NUMBER','BOOLEAN','ARRAY','OBJECT')
 AND ((destino_uf_p='ABSENT' AND destino_uf_w='ABSENT') OR(destino_uf_p='NULL' AND destino_uf_w='NULL')
 OR(destino_uf_p='VALUE' AND destino_uf_w NOT IN('ABSENT','NULL')))
 AND ((destino_uf_p<>'VALUE' AND destino_uf_raw IS NULL AND destino_uf IS NULL AND destino_uf_issue IS NULL)
 OR(destino_uf_p='VALUE' AND destino_uf_raw IS NOT NULL AND ((destino_uf IS NULL AND destino_uf_issue IS NOT NULL)
 OR(destino_uf IS NOT NULL AND destino_uf_issue IS NULL))))),
 CONSTRAINT CK_analytic_fa_filial_nome CHECK(filial_nome_p IN('ABSENT','NULL','VALUE')
 AND filial_nome_w IN('ABSENT','NULL','STRING','INTEGER','NUMBER','BOOLEAN','ARRAY','OBJECT')
 AND ((filial_nome_p='ABSENT' AND filial_nome_w='ABSENT') OR(filial_nome_p='NULL' AND filial_nome_w='NULL')
 OR(filial_nome_p='VALUE' AND filial_nome_w NOT IN('ABSENT','NULL')))
 AND ((filial_nome_p<>'VALUE' AND filial_nome_raw IS NULL AND filial_nome IS NULL AND filial_nome_issue IS NULL)
 OR(filial_nome_p='VALUE' AND filial_nome_raw IS NOT NULL AND ((filial_nome IS NULL AND filial_nome_issue IS NOT NULL)
 OR(filial_nome IS NOT NULL AND filial_nome_issue IS NULL))))),
 CONSTRAINT CK_analytic_fa_filial_apelido CHECK(filial_apelido_p IN('ABSENT','NULL','VALUE')
 AND filial_apelido_w IN('ABSENT','NULL','STRING','INTEGER','NUMBER','BOOLEAN','ARRAY','OBJECT')
 AND ((filial_apelido_p='ABSENT' AND filial_apelido_w='ABSENT') OR(filial_apelido_p='NULL' AND filial_apelido_w='NULL')
 OR(filial_apelido_p='VALUE' AND filial_apelido_w NOT IN('ABSENT','NULL')))
 AND ((filial_apelido_p<>'VALUE' AND filial_apelido_raw IS NULL AND filial_apelido IS NULL AND filial_apelido_issue IS NULL)
 OR(filial_apelido_p='VALUE' AND filial_apelido_raw IS NOT NULL AND ((filial_apelido IS NULL AND filial_apelido_issue IS NOT NULL)
 OR(filial_apelido IS NOT NULL AND filial_apelido_issue IS NULL))))),
 CONSTRAINT CK_analytic_fa_numero_nota_fiscal CHECK(numero_nota_fiscal_p IN('ABSENT','NULL','VALUE')
 AND numero_nota_fiscal_w IN('ABSENT','NULL','STRING','INTEGER','NUMBER','BOOLEAN','ARRAY','OBJECT')
 AND ((numero_nota_fiscal_p='ABSENT' AND numero_nota_fiscal_w='ABSENT') OR(numero_nota_fiscal_p='NULL' AND numero_nota_fiscal_w='NULL')
 OR(numero_nota_fiscal_p='VALUE' AND numero_nota_fiscal_w NOT IN('ABSENT','NULL')))
 AND ((numero_nota_fiscal_p<>'VALUE' AND numero_nota_fiscal_raw IS NULL AND numero_nota_fiscal IS NULL AND numero_nota_fiscal_issue IS NULL)
 OR(numero_nota_fiscal_p='VALUE' AND numero_nota_fiscal_raw IS NOT NULL AND ((numero_nota_fiscal IS NULL AND numero_nota_fiscal_issue IS NOT NULL)
 OR(numero_nota_fiscal IS NOT NULL AND numero_nota_fiscal_issue IS NULL))))),
 CONSTRAINT CK_analytic_fa_tabela_preco_nome CHECK(tabela_preco_nome_p IN('ABSENT','NULL','VALUE')
 AND tabela_preco_nome_w IN('ABSENT','NULL','STRING','INTEGER','NUMBER','BOOLEAN','ARRAY','OBJECT')
 AND ((tabela_preco_nome_p='ABSENT' AND tabela_preco_nome_w='ABSENT') OR(tabela_preco_nome_p='NULL' AND tabela_preco_nome_w='NULL')
 OR(tabela_preco_nome_p='VALUE' AND tabela_preco_nome_w NOT IN('ABSENT','NULL')))
 AND ((tabela_preco_nome_p<>'VALUE' AND tabela_preco_nome_raw IS NULL AND tabela_preco_nome IS NULL AND tabela_preco_nome_issue IS NULL)
 OR(tabela_preco_nome_p='VALUE' AND tabela_preco_nome_raw IS NOT NULL AND ((tabela_preco_nome IS NULL AND tabela_preco_nome_issue IS NOT NULL)
 OR(tabela_preco_nome IS NOT NULL AND tabela_preco_nome_issue IS NULL))))),
 CONSTRAINT CK_analytic_fa_classificacao_nome CHECK(classificacao_nome_p IN('ABSENT','NULL','VALUE')
 AND classificacao_nome_w IN('ABSENT','NULL','STRING','INTEGER','NUMBER','BOOLEAN','ARRAY','OBJECT')
 AND ((classificacao_nome_p='ABSENT' AND classificacao_nome_w='ABSENT') OR(classificacao_nome_p='NULL' AND classificacao_nome_w='NULL')
 OR(classificacao_nome_p='VALUE' AND classificacao_nome_w NOT IN('ABSENT','NULL')))
 AND ((classificacao_nome_p<>'VALUE' AND classificacao_nome_raw IS NULL AND classificacao_nome IS NULL AND classificacao_nome_issue IS NULL)
 OR(classificacao_nome_p='VALUE' AND classificacao_nome_raw IS NOT NULL AND ((classificacao_nome IS NULL AND classificacao_nome_issue IS NOT NULL)
 OR(classificacao_nome IS NOT NULL AND classificacao_nome_issue IS NULL))))),
 CONSTRAINT CK_analytic_fa_centro_custo_nome CHECK(centro_custo_nome_p IN('ABSENT','NULL','VALUE')
 AND centro_custo_nome_w IN('ABSENT','NULL','STRING','INTEGER','NUMBER','BOOLEAN','ARRAY','OBJECT')
 AND ((centro_custo_nome_p='ABSENT' AND centro_custo_nome_w='ABSENT') OR(centro_custo_nome_p='NULL' AND centro_custo_nome_w='NULL')
 OR(centro_custo_nome_p='VALUE' AND centro_custo_nome_w NOT IN('ABSENT','NULL')))
 AND ((centro_custo_nome_p<>'VALUE' AND centro_custo_nome_raw IS NULL AND centro_custo_nome IS NULL AND centro_custo_nome_issue IS NULL)
 OR(centro_custo_nome_p='VALUE' AND centro_custo_nome_raw IS NOT NULL AND ((centro_custo_nome IS NULL AND centro_custo_nome_issue IS NOT NULL)
 OR(centro_custo_nome IS NOT NULL AND centro_custo_nome_issue IS NULL))))),
 CONSTRAINT CK_analytic_fa_usuario_nome CHECK(usuario_nome_p IN('ABSENT','NULL','VALUE')
 AND usuario_nome_w IN('ABSENT','NULL','STRING','INTEGER','NUMBER','BOOLEAN','ARRAY','OBJECT')
 AND ((usuario_nome_p='ABSENT' AND usuario_nome_w='ABSENT') OR(usuario_nome_p='NULL' AND usuario_nome_w='NULL')
 OR(usuario_nome_p='VALUE' AND usuario_nome_w NOT IN('ABSENT','NULL')))
 AND ((usuario_nome_p<>'VALUE' AND usuario_nome_raw IS NULL AND usuario_nome IS NULL AND usuario_nome_issue IS NULL)
 OR(usuario_nome_p='VALUE' AND usuario_nome_raw IS NOT NULL AND ((usuario_nome IS NULL AND usuario_nome_issue IS NOT NULL)
 OR(usuario_nome IS NOT NULL AND usuario_nome_issue IS NULL))))),
 CONSTRAINT CK_analytic_fa_invoices_total_volumes CHECK(invoices_total_volumes_p IN('ABSENT','NULL','VALUE')
 AND invoices_total_volumes_w IN('ABSENT','NULL','STRING','INTEGER','NUMBER','BOOLEAN','ARRAY','OBJECT')
 AND ((invoices_total_volumes_p='ABSENT' AND invoices_total_volumes_w='ABSENT') OR(invoices_total_volumes_p='NULL' AND invoices_total_volumes_w='NULL')
 OR(invoices_total_volumes_p='VALUE' AND invoices_total_volumes_w NOT IN('ABSENT','NULL')))
 AND ((invoices_total_volumes_p<>'VALUE' AND invoices_total_volumes_raw IS NULL AND invoices_total_volumes IS NULL AND invoices_total_volumes_issue IS NULL)
 OR(invoices_total_volumes_p='VALUE' AND invoices_total_volumes_raw IS NOT NULL AND ((invoices_total_volumes IS NULL AND invoices_total_volumes_issue IS NOT NULL)
 OR(invoices_total_volumes IS NOT NULL AND invoices_total_volumes_issue IS NULL))))),
 CONSTRAINT CK_analytic_fa_taxed_weight CHECK(taxed_weight_p IN('ABSENT','NULL','VALUE')
 AND taxed_weight_w IN('ABSENT','NULL','STRING','INTEGER','NUMBER','BOOLEAN','ARRAY','OBJECT')
 AND ((taxed_weight_p='ABSENT' AND taxed_weight_w='ABSENT') OR(taxed_weight_p='NULL' AND taxed_weight_w='NULL')
 OR(taxed_weight_p='VALUE' AND taxed_weight_w NOT IN('ABSENT','NULL')))
 AND ((taxed_weight_p<>'VALUE' AND taxed_weight_raw IS NULL AND taxed_weight IS NULL AND taxed_weight_issue IS NULL)
 OR(taxed_weight_p='VALUE' AND taxed_weight_raw IS NOT NULL AND ((taxed_weight IS NULL AND taxed_weight_issue IS NOT NULL)
 OR(taxed_weight IS NOT NULL AND taxed_weight_issue IS NULL))))),
 CONSTRAINT CK_analytic_fa_real_weight CHECK(real_weight_p IN('ABSENT','NULL','VALUE')
 AND real_weight_w IN('ABSENT','NULL','STRING','INTEGER','NUMBER','BOOLEAN','ARRAY','OBJECT')
 AND ((real_weight_p='ABSENT' AND real_weight_w='ABSENT') OR(real_weight_p='NULL' AND real_weight_w='NULL')
 OR(real_weight_p='VALUE' AND real_weight_w NOT IN('ABSENT','NULL')))
 AND ((real_weight_p<>'VALUE' AND real_weight_raw IS NULL AND real_weight IS NULL AND real_weight_issue IS NULL)
 OR(real_weight_p='VALUE' AND real_weight_raw IS NOT NULL AND ((real_weight IS NULL AND real_weight_issue IS NOT NULL)
 OR(real_weight IS NOT NULL AND real_weight_issue IS NULL))))),
 CONSTRAINT CK_analytic_fa_total_cubic_volume CHECK(total_cubic_volume_p IN('ABSENT','NULL','VALUE')
 AND total_cubic_volume_w IN('ABSENT','NULL','STRING','INTEGER','NUMBER','BOOLEAN','ARRAY','OBJECT')
 AND ((total_cubic_volume_p='ABSENT' AND total_cubic_volume_w='ABSENT') OR(total_cubic_volume_p='NULL' AND total_cubic_volume_w='NULL')
 OR(total_cubic_volume_p='VALUE' AND total_cubic_volume_w NOT IN('ABSENT','NULL')))
 AND ((total_cubic_volume_p<>'VALUE' AND total_cubic_volume_raw IS NULL AND total_cubic_volume IS NULL AND total_cubic_volume_issue IS NULL)
 OR(total_cubic_volume_p='VALUE' AND total_cubic_volume_raw IS NOT NULL AND ((total_cubic_volume IS NULL AND total_cubic_volume_issue IS NOT NULL)
 OR(total_cubic_volume IS NOT NULL AND total_cubic_volume_issue IS NULL))))),
 CONSTRAINT CK_analytic_fa_subtotal CHECK(subtotal_p IN('ABSENT','NULL','VALUE')
 AND subtotal_w IN('ABSENT','NULL','STRING','INTEGER','NUMBER','BOOLEAN','ARRAY','OBJECT')
 AND ((subtotal_p='ABSENT' AND subtotal_w='ABSENT') OR(subtotal_p='NULL' AND subtotal_w='NULL')
 OR(subtotal_p='VALUE' AND subtotal_w NOT IN('ABSENT','NULL')))
 AND ((subtotal_p<>'VALUE' AND subtotal_raw IS NULL AND subtotal IS NULL AND subtotal_issue IS NULL)
 OR(subtotal_p='VALUE' AND subtotal_raw IS NOT NULL AND ((subtotal IS NULL AND subtotal_issue IS NOT NULL)
 OR(subtotal IS NOT NULL AND subtotal_issue IS NULL))))),
 CONSTRAINT CK_analytic_fa_chave_cte CHECK(chave_cte_p IN('ABSENT','NULL','VALUE')
 AND chave_cte_w IN('ABSENT','NULL','STRING','INTEGER','NUMBER','BOOLEAN','ARRAY','OBJECT')
 AND ((chave_cte_p='ABSENT' AND chave_cte_w='ABSENT') OR(chave_cte_p='NULL' AND chave_cte_w='NULL')
 OR(chave_cte_p='VALUE' AND chave_cte_w NOT IN('ABSENT','NULL')))
 AND ((chave_cte_p<>'VALUE' AND chave_cte_raw IS NULL AND chave_cte IS NULL AND chave_cte_issue IS NULL)
 OR(chave_cte_p='VALUE' AND chave_cte_raw IS NOT NULL AND ((chave_cte IS NULL AND chave_cte_issue IS NOT NULL)
 OR(chave_cte IS NOT NULL AND chave_cte_issue IS NULL))))),
 CONSTRAINT CK_analytic_fa_numero_cte CHECK(numero_cte_p IN('ABSENT','NULL','VALUE')
 AND numero_cte_w IN('ABSENT','NULL','STRING','INTEGER','NUMBER','BOOLEAN','ARRAY','OBJECT')
 AND ((numero_cte_p='ABSENT' AND numero_cte_w='ABSENT') OR(numero_cte_p='NULL' AND numero_cte_w='NULL')
 OR(numero_cte_p='VALUE' AND numero_cte_w NOT IN('ABSENT','NULL')))
 AND ((numero_cte_p<>'VALUE' AND numero_cte_raw IS NULL AND numero_cte IS NULL AND numero_cte_issue IS NULL)
 OR(numero_cte_p='VALUE' AND numero_cte_raw IS NOT NULL AND ((numero_cte IS NULL AND numero_cte_issue IS NOT NULL)
 OR(numero_cte IS NOT NULL AND numero_cte_issue IS NULL))))),
 CONSTRAINT CK_analytic_fa_serie_cte CHECK(serie_cte_p IN('ABSENT','NULL','VALUE')
 AND serie_cte_w IN('ABSENT','NULL','STRING','INTEGER','NUMBER','BOOLEAN','ARRAY','OBJECT')
 AND ((serie_cte_p='ABSENT' AND serie_cte_w='ABSENT') OR(serie_cte_p='NULL' AND serie_cte_w='NULL')
 OR(serie_cte_p='VALUE' AND serie_cte_w NOT IN('ABSENT','NULL')))
 AND ((serie_cte_p<>'VALUE' AND serie_cte_raw IS NULL AND serie_cte IS NULL AND serie_cte_issue IS NULL)
 OR(serie_cte_p='VALUE' AND serie_cte_raw IS NOT NULL AND ((serie_cte IS NULL AND serie_cte_issue IS NOT NULL)
 OR(serie_cte IS NOT NULL AND serie_cte_issue IS NULL))))),
 CONSTRAINT CK_analytic_fa_cte_id CHECK(cte_id_p IN('ABSENT','NULL','VALUE')
 AND cte_id_w IN('ABSENT','NULL','STRING','INTEGER','NUMBER','BOOLEAN','ARRAY','OBJECT')
 AND ((cte_id_p='ABSENT' AND cte_id_w='ABSENT') OR(cte_id_p='NULL' AND cte_id_w='NULL')
 OR(cte_id_p='VALUE' AND cte_id_w NOT IN('ABSENT','NULL')))
 AND ((cte_id_p<>'VALUE' AND cte_id_raw IS NULL AND cte_id IS NULL AND cte_id_issue IS NULL)
 OR(cte_id_p='VALUE' AND cte_id_raw IS NOT NULL AND ((cte_id IS NULL AND cte_id_issue IS NOT NULL)
 OR(cte_id IS NOT NULL AND cte_id_issue IS NULL))))),
 CONSTRAINT CK_analytic_fa_cte_emission_type CHECK(cte_emission_type_p IN('ABSENT','NULL','VALUE')
 AND cte_emission_type_w IN('ABSENT','NULL','STRING','INTEGER','NUMBER','BOOLEAN','ARRAY','OBJECT')
 AND ((cte_emission_type_p='ABSENT' AND cte_emission_type_w='ABSENT') OR(cte_emission_type_p='NULL' AND cte_emission_type_w='NULL')
 OR(cte_emission_type_p='VALUE' AND cte_emission_type_w NOT IN('ABSENT','NULL')))
 AND ((cte_emission_type_p<>'VALUE' AND cte_emission_type_raw IS NULL AND cte_emission_type IS NULL AND cte_emission_type_issue IS NULL)
 OR(cte_emission_type_p='VALUE' AND cte_emission_type_raw IS NOT NULL AND ((cte_emission_type IS NULL AND cte_emission_type_issue IS NOT NULL)
 OR(cte_emission_type IS NOT NULL AND cte_emission_type_issue IS NULL))))),
 CONSTRAINT CK_analytic_fa_service_type CHECK(service_type_p IN('ABSENT','NULL','VALUE')
 AND service_type_w IN('ABSENT','NULL','STRING','INTEGER','NUMBER','BOOLEAN','ARRAY','OBJECT')
 AND ((service_type_p='ABSENT' AND service_type_w='ABSENT') OR(service_type_p='NULL' AND service_type_w='NULL')
 OR(service_type_p='VALUE' AND service_type_w NOT IN('ABSENT','NULL')))
 AND ((service_type_p<>'VALUE' AND service_type_raw IS NULL AND service_type IS NULL AND service_type_issue IS NULL)
 OR(service_type_p='VALUE' AND service_type_raw IS NOT NULL AND ((service_type IS NULL AND service_type_issue IS NOT NULL)
 OR(service_type IS NOT NULL AND service_type_issue IS NULL))))),
 CONSTRAINT CK_analytic_fa_insurance_enabled CHECK(insurance_enabled_p IN('ABSENT','NULL','VALUE')
 AND insurance_enabled_w IN('ABSENT','NULL','STRING','INTEGER','NUMBER','BOOLEAN','ARRAY','OBJECT')
 AND ((insurance_enabled_p='ABSENT' AND insurance_enabled_w='ABSENT') OR(insurance_enabled_p='NULL' AND insurance_enabled_w='NULL')
 OR(insurance_enabled_p='VALUE' AND insurance_enabled_w NOT IN('ABSENT','NULL')))
 AND ((insurance_enabled_p<>'VALUE' AND insurance_enabled_raw IS NULL AND insurance_enabled IS NULL AND insurance_enabled_issue IS NULL)
 OR(insurance_enabled_p='VALUE' AND insurance_enabled_raw IS NOT NULL AND ((insurance_enabled IS NULL AND insurance_enabled_issue IS NOT NULL)
 OR(insurance_enabled IS NOT NULL AND insurance_enabled_issue IS NULL))))),
 CONSTRAINT CK_analytic_fa_gris_subtotal CHECK(gris_subtotal_p IN('ABSENT','NULL','VALUE')
 AND gris_subtotal_w IN('ABSENT','NULL','STRING','INTEGER','NUMBER','BOOLEAN','ARRAY','OBJECT')
 AND ((gris_subtotal_p='ABSENT' AND gris_subtotal_w='ABSENT') OR(gris_subtotal_p='NULL' AND gris_subtotal_w='NULL')
 OR(gris_subtotal_p='VALUE' AND gris_subtotal_w NOT IN('ABSENT','NULL')))
 AND ((gris_subtotal_p<>'VALUE' AND gris_subtotal_raw IS NULL AND gris_subtotal IS NULL AND gris_subtotal_issue IS NULL)
 OR(gris_subtotal_p='VALUE' AND gris_subtotal_raw IS NOT NULL AND ((gris_subtotal IS NULL AND gris_subtotal_issue IS NOT NULL)
 OR(gris_subtotal IS NOT NULL AND gris_subtotal_issue IS NULL))))),
 CONSTRAINT CK_analytic_fa_tde_subtotal CHECK(tde_subtotal_p IN('ABSENT','NULL','VALUE')
 AND tde_subtotal_w IN('ABSENT','NULL','STRING','INTEGER','NUMBER','BOOLEAN','ARRAY','OBJECT')
 AND ((tde_subtotal_p='ABSENT' AND tde_subtotal_w='ABSENT') OR(tde_subtotal_p='NULL' AND tde_subtotal_w='NULL')
 OR(tde_subtotal_p='VALUE' AND tde_subtotal_w NOT IN('ABSENT','NULL')))
 AND ((tde_subtotal_p<>'VALUE' AND tde_subtotal_raw IS NULL AND tde_subtotal IS NULL AND tde_subtotal_issue IS NULL)
 OR(tde_subtotal_p='VALUE' AND tde_subtotal_raw IS NOT NULL AND ((tde_subtotal IS NULL AND tde_subtotal_issue IS NOT NULL)
 OR(tde_subtotal IS NOT NULL AND tde_subtotal_issue IS NULL))))),
 CONSTRAINT CK_analytic_fa_modal_cte CHECK(modal_cte_p IN('ABSENT','NULL','VALUE')
 AND modal_cte_w IN('ABSENT','NULL','STRING','INTEGER','NUMBER','BOOLEAN','ARRAY','OBJECT')
 AND ((modal_cte_p='ABSENT' AND modal_cte_w='ABSENT') OR(modal_cte_p='NULL' AND modal_cte_w='NULL')
 OR(modal_cte_p='VALUE' AND modal_cte_w NOT IN('ABSENT','NULL')))
 AND ((modal_cte_p<>'VALUE' AND modal_cte_raw IS NULL AND modal_cte IS NULL AND modal_cte_issue IS NULL)
 OR(modal_cte_p='VALUE' AND modal_cte_raw IS NOT NULL AND ((modal_cte IS NULL AND modal_cte_issue IS NOT NULL)
 OR(modal_cte IS NOT NULL AND modal_cte_issue IS NULL))))),
 CONSTRAINT CK_analytic_fa_redispatch_subtotal CHECK(redispatch_subtotal_p IN('ABSENT','NULL','VALUE')
 AND redispatch_subtotal_w IN('ABSENT','NULL','STRING','INTEGER','NUMBER','BOOLEAN','ARRAY','OBJECT')
 AND ((redispatch_subtotal_p='ABSENT' AND redispatch_subtotal_w='ABSENT') OR(redispatch_subtotal_p='NULL' AND redispatch_subtotal_w='NULL')
 OR(redispatch_subtotal_p='VALUE' AND redispatch_subtotal_w NOT IN('ABSENT','NULL')))
 AND ((redispatch_subtotal_p<>'VALUE' AND redispatch_subtotal_raw IS NULL AND redispatch_subtotal IS NULL AND redispatch_subtotal_issue IS NULL)
 OR(redispatch_subtotal_p='VALUE' AND redispatch_subtotal_raw IS NOT NULL AND ((redispatch_subtotal IS NULL AND redispatch_subtotal_issue IS NOT NULL)
 OR(redispatch_subtotal IS NOT NULL AND redispatch_subtotal_issue IS NULL))))),
 CONSTRAINT CK_analytic_fa_suframa_subtotal CHECK(suframa_subtotal_p IN('ABSENT','NULL','VALUE')
 AND suframa_subtotal_w IN('ABSENT','NULL','STRING','INTEGER','NUMBER','BOOLEAN','ARRAY','OBJECT')
 AND ((suframa_subtotal_p='ABSENT' AND suframa_subtotal_w='ABSENT') OR(suframa_subtotal_p='NULL' AND suframa_subtotal_w='NULL')
 OR(suframa_subtotal_p='VALUE' AND suframa_subtotal_w NOT IN('ABSENT','NULL')))
 AND ((suframa_subtotal_p<>'VALUE' AND suframa_subtotal_raw IS NULL AND suframa_subtotal IS NULL AND suframa_subtotal_issue IS NULL)
 OR(suframa_subtotal_p='VALUE' AND suframa_subtotal_raw IS NOT NULL AND ((suframa_subtotal IS NULL AND suframa_subtotal_issue IS NOT NULL)
 OR(suframa_subtotal IS NOT NULL AND suframa_subtotal_issue IS NULL))))),
 CONSTRAINT CK_analytic_fa_payment_type CHECK(payment_type_p IN('ABSENT','NULL','VALUE')
 AND payment_type_w IN('ABSENT','NULL','STRING','INTEGER','NUMBER','BOOLEAN','ARRAY','OBJECT')
 AND ((payment_type_p='ABSENT' AND payment_type_w='ABSENT') OR(payment_type_p='NULL' AND payment_type_w='NULL')
 OR(payment_type_p='VALUE' AND payment_type_w NOT IN('ABSENT','NULL')))
 AND ((payment_type_p<>'VALUE' AND payment_type_raw IS NULL AND payment_type IS NULL AND payment_type_issue IS NULL)
 OR(payment_type_p='VALUE' AND payment_type_raw IS NOT NULL AND ((payment_type IS NULL AND payment_type_issue IS NOT NULL)
 OR(payment_type IS NOT NULL AND payment_type_issue IS NULL))))),
 CONSTRAINT CK_analytic_fa_previous_document_type CHECK(previous_document_type_p IN('ABSENT','NULL','VALUE')
 AND previous_document_type_w IN('ABSENT','NULL','STRING','INTEGER','NUMBER','BOOLEAN','ARRAY','OBJECT')
 AND ((previous_document_type_p='ABSENT' AND previous_document_type_w='ABSENT') OR(previous_document_type_p='NULL' AND previous_document_type_w='NULL')
 OR(previous_document_type_p='VALUE' AND previous_document_type_w NOT IN('ABSENT','NULL')))
 AND ((previous_document_type_p<>'VALUE' AND previous_document_type_raw IS NULL AND previous_document_type IS NULL AND previous_document_type_issue IS NULL)
 OR(previous_document_type_p='VALUE' AND previous_document_type_raw IS NOT NULL AND ((previous_document_type IS NULL AND previous_document_type_issue IS NOT NULL)
 OR(previous_document_type IS NOT NULL AND previous_document_type_issue IS NULL))))),
 CONSTRAINT CK_analytic_fa_products_value CHECK(products_value_p IN('ABSENT','NULL','VALUE')
 AND products_value_w IN('ABSENT','NULL','STRING','INTEGER','NUMBER','BOOLEAN','ARRAY','OBJECT')
 AND ((products_value_p='ABSENT' AND products_value_w='ABSENT') OR(products_value_p='NULL' AND products_value_w='NULL')
 OR(products_value_p='VALUE' AND products_value_w NOT IN('ABSENT','NULL')))
 AND ((products_value_p<>'VALUE' AND products_value_raw IS NULL AND products_value IS NULL AND products_value_issue IS NULL)
 OR(products_value_p='VALUE' AND products_value_raw IS NOT NULL AND ((products_value IS NULL AND products_value_issue IS NOT NULL)
 OR(products_value IS NOT NULL AND products_value_issue IS NULL))))),
 CONSTRAINT CK_analytic_fa_trt_subtotal CHECK(trt_subtotal_p IN('ABSENT','NULL','VALUE')
 AND trt_subtotal_w IN('ABSENT','NULL','STRING','INTEGER','NUMBER','BOOLEAN','ARRAY','OBJECT')
 AND ((trt_subtotal_p='ABSENT' AND trt_subtotal_w='ABSENT') OR(trt_subtotal_p='NULL' AND trt_subtotal_w='NULL')
 OR(trt_subtotal_p='VALUE' AND trt_subtotal_w NOT IN('ABSENT','NULL')))
 AND ((trt_subtotal_p<>'VALUE' AND trt_subtotal_raw IS NULL AND trt_subtotal IS NULL AND trt_subtotal_issue IS NULL)
 OR(trt_subtotal_p='VALUE' AND trt_subtotal_raw IS NOT NULL AND ((trt_subtotal IS NULL AND trt_subtotal_issue IS NOT NULL)
 OR(trt_subtotal IS NOT NULL AND trt_subtotal_issue IS NULL))))),
 CONSTRAINT CK_analytic_fa_nfse_series CHECK(nfse_series_p IN('ABSENT','NULL','VALUE')
 AND nfse_series_w IN('ABSENT','NULL','STRING','INTEGER','NUMBER','BOOLEAN','ARRAY','OBJECT')
 AND ((nfse_series_p='ABSENT' AND nfse_series_w='ABSENT') OR(nfse_series_p='NULL' AND nfse_series_w='NULL')
 OR(nfse_series_p='VALUE' AND nfse_series_w NOT IN('ABSENT','NULL')))
 AND ((nfse_series_p<>'VALUE' AND nfse_series_raw IS NULL AND nfse_series IS NULL AND nfse_series_issue IS NULL)
 OR(nfse_series_p='VALUE' AND nfse_series_raw IS NOT NULL AND ((nfse_series IS NULL AND nfse_series_issue IS NOT NULL)
 OR(nfse_series IS NOT NULL AND nfse_series_issue IS NULL))))),
 CONSTRAINT CK_analytic_fa_nfse_number CHECK(nfse_number_p IN('ABSENT','NULL','VALUE')
 AND nfse_number_w IN('ABSENT','NULL','STRING','INTEGER','NUMBER','BOOLEAN','ARRAY','OBJECT')
 AND ((nfse_number_p='ABSENT' AND nfse_number_w='ABSENT') OR(nfse_number_p='NULL' AND nfse_number_w='NULL')
 OR(nfse_number_p='VALUE' AND nfse_number_w NOT IN('ABSENT','NULL')))
 AND ((nfse_number_p<>'VALUE' AND nfse_number_raw IS NULL AND nfse_number IS NULL AND nfse_number_issue IS NULL)
 OR(nfse_number_p='VALUE' AND nfse_number_raw IS NOT NULL AND ((nfse_number IS NULL AND nfse_number_issue IS NOT NULL)
 OR(nfse_number IS NOT NULL AND nfse_number_issue IS NULL))))),
 CONSTRAINT CK_analytic_fa_insurance_id CHECK(insurance_id_p IN('ABSENT','NULL','VALUE')
 AND insurance_id_w IN('ABSENT','NULL','STRING','INTEGER','NUMBER','BOOLEAN','ARRAY','OBJECT')
 AND ((insurance_id_p='ABSENT' AND insurance_id_w='ABSENT') OR(insurance_id_p='NULL' AND insurance_id_w='NULL')
 OR(insurance_id_p='VALUE' AND insurance_id_w NOT IN('ABSENT','NULL')))
 AND ((insurance_id_p<>'VALUE' AND insurance_id_raw IS NULL AND insurance_id IS NULL AND insurance_id_issue IS NULL)
 OR(insurance_id_p='VALUE' AND insurance_id_raw IS NOT NULL AND ((insurance_id IS NULL AND insurance_id_issue IS NOT NULL)
 OR(insurance_id IS NOT NULL AND insurance_id_issue IS NULL))))),
 CONSTRAINT CK_analytic_fa_other_fees CHECK(other_fees_p IN('ABSENT','NULL','VALUE')
 AND other_fees_w IN('ABSENT','NULL','STRING','INTEGER','NUMBER','BOOLEAN','ARRAY','OBJECT')
 AND ((other_fees_p='ABSENT' AND other_fees_w='ABSENT') OR(other_fees_p='NULL' AND other_fees_w='NULL')
 OR(other_fees_p='VALUE' AND other_fees_w NOT IN('ABSENT','NULL')))
 AND ((other_fees_p<>'VALUE' AND other_fees_raw IS NULL AND other_fees IS NULL AND other_fees_issue IS NULL)
 OR(other_fees_p='VALUE' AND other_fees_raw IS NOT NULL AND ((other_fees IS NULL AND other_fees_issue IS NOT NULL)
 OR(other_fees IS NOT NULL AND other_fees_issue IS NULL))))),
 CONSTRAINT CK_analytic_fa_km CHECK(km_p IN('ABSENT','NULL','VALUE')
 AND km_w IN('ABSENT','NULL','STRING','INTEGER','NUMBER','BOOLEAN','ARRAY','OBJECT')
 AND ((km_p='ABSENT' AND km_w='ABSENT') OR(km_p='NULL' AND km_w='NULL')
 OR(km_p='VALUE' AND km_w NOT IN('ABSENT','NULL')))
 AND ((km_p<>'VALUE' AND km_raw IS NULL AND km IS NULL AND km_issue IS NULL)
 OR(km_p='VALUE' AND km_raw IS NOT NULL AND ((km IS NULL AND km_issue IS NOT NULL)
 OR(km IS NOT NULL AND km_issue IS NULL))))),
 CONSTRAINT CK_analytic_fa_payment_accountable_type CHECK(payment_accountable_type_p IN('ABSENT','NULL','VALUE')
 AND payment_accountable_type_w IN('ABSENT','NULL','STRING','INTEGER','NUMBER','BOOLEAN','ARRAY','OBJECT')
 AND ((payment_accountable_type_p='ABSENT' AND payment_accountable_type_w='ABSENT') OR(payment_accountable_type_p='NULL' AND payment_accountable_type_w='NULL')
 OR(payment_accountable_type_p='VALUE' AND payment_accountable_type_w NOT IN('ABSENT','NULL')))
 AND ((payment_accountable_type_p<>'VALUE' AND payment_accountable_type_raw IS NULL AND payment_accountable_type IS NULL AND payment_accountable_type_issue IS NULL)
 OR(payment_accountable_type_p='VALUE' AND payment_accountable_type_raw IS NOT NULL AND ((payment_accountable_type IS NULL AND payment_accountable_type_issue IS NOT NULL)
 OR(payment_accountable_type IS NOT NULL AND payment_accountable_type_issue IS NULL))))),
 CONSTRAINT CK_analytic_fa_insured_value CHECK(insured_value_p IN('ABSENT','NULL','VALUE')
 AND insured_value_w IN('ABSENT','NULL','STRING','INTEGER','NUMBER','BOOLEAN','ARRAY','OBJECT')
 AND ((insured_value_p='ABSENT' AND insured_value_w='ABSENT') OR(insured_value_p='NULL' AND insured_value_w='NULL')
 OR(insured_value_p='VALUE' AND insured_value_w NOT IN('ABSENT','NULL')))
 AND ((insured_value_p<>'VALUE' AND insured_value_raw IS NULL AND insured_value IS NULL AND insured_value_issue IS NULL)
 OR(insured_value_p='VALUE' AND insured_value_raw IS NOT NULL AND ((insured_value IS NULL AND insured_value_issue IS NOT NULL)
 OR(insured_value IS NOT NULL AND insured_value_issue IS NULL))))),
 CONSTRAINT CK_analytic_fa_globalized CHECK(globalized_p IN('ABSENT','NULL','VALUE')
 AND globalized_w IN('ABSENT','NULL','STRING','INTEGER','NUMBER','BOOLEAN','ARRAY','OBJECT')
 AND ((globalized_p='ABSENT' AND globalized_w='ABSENT') OR(globalized_p='NULL' AND globalized_w='NULL')
 OR(globalized_p='VALUE' AND globalized_w NOT IN('ABSENT','NULL')))
 AND ((globalized_p<>'VALUE' AND globalized_raw IS NULL AND globalized IS NULL AND globalized_issue IS NULL)
 OR(globalized_p='VALUE' AND globalized_raw IS NOT NULL AND ((globalized IS NULL AND globalized_issue IS NOT NULL)
 OR(globalized IS NOT NULL AND globalized_issue IS NULL))))),
 CONSTRAINT CK_analytic_fa_sec_cat_subtotal CHECK(sec_cat_subtotal_p IN('ABSENT','NULL','VALUE')
 AND sec_cat_subtotal_w IN('ABSENT','NULL','STRING','INTEGER','NUMBER','BOOLEAN','ARRAY','OBJECT')
 AND ((sec_cat_subtotal_p='ABSENT' AND sec_cat_subtotal_w='ABSENT') OR(sec_cat_subtotal_p='NULL' AND sec_cat_subtotal_w='NULL')
 OR(sec_cat_subtotal_p='VALUE' AND sec_cat_subtotal_w NOT IN('ABSENT','NULL')))
 AND ((sec_cat_subtotal_p<>'VALUE' AND sec_cat_subtotal_raw IS NULL AND sec_cat_subtotal IS NULL AND sec_cat_subtotal_issue IS NULL)
 OR(sec_cat_subtotal_p='VALUE' AND sec_cat_subtotal_raw IS NOT NULL AND ((sec_cat_subtotal IS NULL AND sec_cat_subtotal_issue IS NOT NULL)
 OR(sec_cat_subtotal IS NOT NULL AND sec_cat_subtotal_issue IS NULL))))),
 CONSTRAINT CK_analytic_fa_globalized_type CHECK(globalized_type_p IN('ABSENT','NULL','VALUE')
 AND globalized_type_w IN('ABSENT','NULL','STRING','INTEGER','NUMBER','BOOLEAN','ARRAY','OBJECT')
 AND ((globalized_type_p='ABSENT' AND globalized_type_w='ABSENT') OR(globalized_type_p='NULL' AND globalized_type_w='NULL')
 OR(globalized_type_p='VALUE' AND globalized_type_w NOT IN('ABSENT','NULL')))
 AND ((globalized_type_p<>'VALUE' AND globalized_type_raw IS NULL AND globalized_type IS NULL AND globalized_type_issue IS NULL)
 OR(globalized_type_p='VALUE' AND globalized_type_raw IS NOT NULL AND ((globalized_type IS NULL AND globalized_type_issue IS NOT NULL)
 OR(globalized_type IS NOT NULL AND globalized_type_issue IS NULL))))),
 CONSTRAINT CK_analytic_fa_price_table_accountable_type CHECK(price_table_accountable_type_p IN('ABSENT','NULL','VALUE')
 AND price_table_accountable_type_w IN('ABSENT','NULL','STRING','INTEGER','NUMBER','BOOLEAN','ARRAY','OBJECT')
 AND ((price_table_accountable_type_p='ABSENT' AND price_table_accountable_type_w='ABSENT') OR(price_table_accountable_type_p='NULL' AND price_table_accountable_type_w='NULL')
 OR(price_table_accountable_type_p='VALUE' AND price_table_accountable_type_w NOT IN('ABSENT','NULL')))
 AND ((price_table_accountable_type_p<>'VALUE' AND price_table_accountable_type_raw IS NULL AND price_table_accountable_type IS NULL AND price_table_accountable_type_issue IS NULL)
 OR(price_table_accountable_type_p='VALUE' AND price_table_accountable_type_raw IS NOT NULL AND ((price_table_accountable_type IS NULL AND price_table_accountable_type_issue IS NOT NULL)
 OR(price_table_accountable_type IS NOT NULL AND price_table_accountable_type_issue IS NULL))))),
 CONSTRAINT CK_analytic_fa_insurance_accountable_type CHECK(insurance_accountable_type_p IN('ABSENT','NULL','VALUE')
 AND insurance_accountable_type_w IN('ABSENT','NULL','STRING','INTEGER','NUMBER','BOOLEAN','ARRAY','OBJECT')
 AND ((insurance_accountable_type_p='ABSENT' AND insurance_accountable_type_w='ABSENT') OR(insurance_accountable_type_p='NULL' AND insurance_accountable_type_w='NULL')
 OR(insurance_accountable_type_p='VALUE' AND insurance_accountable_type_w NOT IN('ABSENT','NULL')))
 AND ((insurance_accountable_type_p<>'VALUE' AND insurance_accountable_type_raw IS NULL AND insurance_accountable_type IS NULL AND insurance_accountable_type_issue IS NULL)
 OR(insurance_accountable_type_p='VALUE' AND insurance_accountable_type_raw IS NOT NULL AND ((insurance_accountable_type IS NULL AND insurance_accountable_type_issue IS NOT NULL)
 OR(insurance_accountable_type IS NOT NULL AND insurance_accountable_type_issue IS NULL))))),
 CONSTRAINT CK_analytic_fa_fiscal_calculation_basis CHECK(fiscal_calculation_basis_p IN('ABSENT','NULL','VALUE')
 AND fiscal_calculation_basis_w IN('ABSENT','NULL','STRING','INTEGER','NUMBER','BOOLEAN','ARRAY','OBJECT')
 AND ((fiscal_calculation_basis_p='ABSENT' AND fiscal_calculation_basis_w='ABSENT') OR(fiscal_calculation_basis_p='NULL' AND fiscal_calculation_basis_w='NULL')
 OR(fiscal_calculation_basis_p='VALUE' AND fiscal_calculation_basis_w NOT IN('ABSENT','NULL')))
 AND ((fiscal_calculation_basis_p<>'VALUE' AND fiscal_calculation_basis_raw IS NULL AND fiscal_calculation_basis IS NULL AND fiscal_calculation_basis_issue IS NULL)
 OR(fiscal_calculation_basis_p='VALUE' AND fiscal_calculation_basis_raw IS NOT NULL AND ((fiscal_calculation_basis IS NULL AND fiscal_calculation_basis_issue IS NOT NULL)
 OR(fiscal_calculation_basis IS NOT NULL AND fiscal_calculation_basis_issue IS NULL))))),
 CONSTRAINT CK_analytic_fa_fiscal_tax_rate CHECK(fiscal_tax_rate_p IN('ABSENT','NULL','VALUE')
 AND fiscal_tax_rate_w IN('ABSENT','NULL','STRING','INTEGER','NUMBER','BOOLEAN','ARRAY','OBJECT')
 AND ((fiscal_tax_rate_p='ABSENT' AND fiscal_tax_rate_w='ABSENT') OR(fiscal_tax_rate_p='NULL' AND fiscal_tax_rate_w='NULL')
 OR(fiscal_tax_rate_p='VALUE' AND fiscal_tax_rate_w NOT IN('ABSENT','NULL')))
 AND ((fiscal_tax_rate_p<>'VALUE' AND fiscal_tax_rate_raw IS NULL AND fiscal_tax_rate IS NULL AND fiscal_tax_rate_issue IS NULL)
 OR(fiscal_tax_rate_p='VALUE' AND fiscal_tax_rate_raw IS NOT NULL AND ((fiscal_tax_rate IS NULL AND fiscal_tax_rate_issue IS NOT NULL)
 OR(fiscal_tax_rate IS NOT NULL AND fiscal_tax_rate_issue IS NULL))))),
 CONSTRAINT CK_analytic_fa_fiscal_pis_rate CHECK(fiscal_pis_rate_p IN('ABSENT','NULL','VALUE')
 AND fiscal_pis_rate_w IN('ABSENT','NULL','STRING','INTEGER','NUMBER','BOOLEAN','ARRAY','OBJECT')
 AND ((fiscal_pis_rate_p='ABSENT' AND fiscal_pis_rate_w='ABSENT') OR(fiscal_pis_rate_p='NULL' AND fiscal_pis_rate_w='NULL')
 OR(fiscal_pis_rate_p='VALUE' AND fiscal_pis_rate_w NOT IN('ABSENT','NULL')))
 AND ((fiscal_pis_rate_p<>'VALUE' AND fiscal_pis_rate_raw IS NULL AND fiscal_pis_rate IS NULL AND fiscal_pis_rate_issue IS NULL)
 OR(fiscal_pis_rate_p='VALUE' AND fiscal_pis_rate_raw IS NOT NULL AND ((fiscal_pis_rate IS NULL AND fiscal_pis_rate_issue IS NOT NULL)
 OR(fiscal_pis_rate IS NOT NULL AND fiscal_pis_rate_issue IS NULL))))),
 CONSTRAINT CK_analytic_fa_fiscal_cofins_rate CHECK(fiscal_cofins_rate_p IN('ABSENT','NULL','VALUE')
 AND fiscal_cofins_rate_w IN('ABSENT','NULL','STRING','INTEGER','NUMBER','BOOLEAN','ARRAY','OBJECT')
 AND ((fiscal_cofins_rate_p='ABSENT' AND fiscal_cofins_rate_w='ABSENT') OR(fiscal_cofins_rate_p='NULL' AND fiscal_cofins_rate_w='NULL')
 OR(fiscal_cofins_rate_p='VALUE' AND fiscal_cofins_rate_w NOT IN('ABSENT','NULL')))
 AND ((fiscal_cofins_rate_p<>'VALUE' AND fiscal_cofins_rate_raw IS NULL AND fiscal_cofins_rate IS NULL AND fiscal_cofins_rate_issue IS NULL)
 OR(fiscal_cofins_rate_p='VALUE' AND fiscal_cofins_rate_raw IS NOT NULL AND ((fiscal_cofins_rate IS NULL AND fiscal_cofins_rate_issue IS NOT NULL)
 OR(fiscal_cofins_rate IS NOT NULL AND fiscal_cofins_rate_issue IS NULL))))),
 CONSTRAINT CK_analytic_fa_fiscal_has_difal CHECK(fiscal_has_difal_p IN('ABSENT','NULL','VALUE')
 AND fiscal_has_difal_w IN('ABSENT','NULL','STRING','INTEGER','NUMBER','BOOLEAN','ARRAY','OBJECT')
 AND ((fiscal_has_difal_p='ABSENT' AND fiscal_has_difal_w='ABSENT') OR(fiscal_has_difal_p='NULL' AND fiscal_has_difal_w='NULL')
 OR(fiscal_has_difal_p='VALUE' AND fiscal_has_difal_w NOT IN('ABSENT','NULL')))
 AND ((fiscal_has_difal_p<>'VALUE' AND fiscal_has_difal_raw IS NULL AND fiscal_has_difal IS NULL AND fiscal_has_difal_issue IS NULL)
 OR(fiscal_has_difal_p='VALUE' AND fiscal_has_difal_raw IS NOT NULL AND ((fiscal_has_difal IS NULL AND fiscal_has_difal_issue IS NOT NULL)
 OR(fiscal_has_difal IS NOT NULL AND fiscal_has_difal_issue IS NULL))))),
 CONSTRAINT CK_analytic_fa_fiscal_difal_origin CHECK(fiscal_difal_origin_p IN('ABSENT','NULL','VALUE')
 AND fiscal_difal_origin_w IN('ABSENT','NULL','STRING','INTEGER','NUMBER','BOOLEAN','ARRAY','OBJECT')
 AND ((fiscal_difal_origin_p='ABSENT' AND fiscal_difal_origin_w='ABSENT') OR(fiscal_difal_origin_p='NULL' AND fiscal_difal_origin_w='NULL')
 OR(fiscal_difal_origin_p='VALUE' AND fiscal_difal_origin_w NOT IN('ABSENT','NULL')))
 AND ((fiscal_difal_origin_p<>'VALUE' AND fiscal_difal_origin_raw IS NULL AND fiscal_difal_origin IS NULL AND fiscal_difal_origin_issue IS NULL)
 OR(fiscal_difal_origin_p='VALUE' AND fiscal_difal_origin_raw IS NOT NULL AND ((fiscal_difal_origin IS NULL AND fiscal_difal_origin_issue IS NOT NULL)
 OR(fiscal_difal_origin IS NOT NULL AND fiscal_difal_origin_issue IS NULL))))),
 CONSTRAINT CK_analytic_fa_fiscal_difal_destination CHECK(fiscal_difal_destination_p IN('ABSENT','NULL','VALUE')
 AND fiscal_difal_destination_w IN('ABSENT','NULL','STRING','INTEGER','NUMBER','BOOLEAN','ARRAY','OBJECT')
 AND ((fiscal_difal_destination_p='ABSENT' AND fiscal_difal_destination_w='ABSENT') OR(fiscal_difal_destination_p='NULL' AND fiscal_difal_destination_w='NULL')
 OR(fiscal_difal_destination_p='VALUE' AND fiscal_difal_destination_w NOT IN('ABSENT','NULL')))
 AND ((fiscal_difal_destination_p<>'VALUE' AND fiscal_difal_destination_raw IS NULL AND fiscal_difal_destination IS NULL AND fiscal_difal_destination_issue IS NULL)
 OR(fiscal_difal_destination_p='VALUE' AND fiscal_difal_destination_raw IS NOT NULL AND ((fiscal_difal_destination IS NULL AND fiscal_difal_destination_issue IS NOT NULL)
 OR(fiscal_difal_destination IS NOT NULL AND fiscal_difal_destination_issue IS NULL))))),
 CONSTRAINT CK_analytic_fa_fiscal_cst_type CHECK(fiscal_cst_type_p IN('ABSENT','NULL','VALUE')
 AND fiscal_cst_type_w IN('ABSENT','NULL','STRING','INTEGER','NUMBER','BOOLEAN','ARRAY','OBJECT')
 AND ((fiscal_cst_type_p='ABSENT' AND fiscal_cst_type_w='ABSENT') OR(fiscal_cst_type_p='NULL' AND fiscal_cst_type_w='NULL')
 OR(fiscal_cst_type_p='VALUE' AND fiscal_cst_type_w NOT IN('ABSENT','NULL')))
 AND ((fiscal_cst_type_p<>'VALUE' AND fiscal_cst_type_raw IS NULL AND fiscal_cst_type IS NULL AND fiscal_cst_type_issue IS NULL)
 OR(fiscal_cst_type_p='VALUE' AND fiscal_cst_type_raw IS NOT NULL AND ((fiscal_cst_type IS NULL AND fiscal_cst_type_issue IS NOT NULL)
 OR(fiscal_cst_type IS NOT NULL AND fiscal_cst_type_issue IS NULL))))),
 CONSTRAINT CK_analytic_fa_fiscal_cfop_code CHECK(fiscal_cfop_code_p IN('ABSENT','NULL','VALUE')
 AND fiscal_cfop_code_w IN('ABSENT','NULL','STRING','INTEGER','NUMBER','BOOLEAN','ARRAY','OBJECT')
 AND ((fiscal_cfop_code_p='ABSENT' AND fiscal_cfop_code_w='ABSENT') OR(fiscal_cfop_code_p='NULL' AND fiscal_cfop_code_w='NULL')
 OR(fiscal_cfop_code_p='VALUE' AND fiscal_cfop_code_w NOT IN('ABSENT','NULL')))
 AND ((fiscal_cfop_code_p<>'VALUE' AND fiscal_cfop_code_raw IS NULL AND fiscal_cfop_code IS NULL AND fiscal_cfop_code_issue IS NULL)
 OR(fiscal_cfop_code_p='VALUE' AND fiscal_cfop_code_raw IS NOT NULL AND ((fiscal_cfop_code IS NULL AND fiscal_cfop_code_issue IS NOT NULL)
 OR(fiscal_cfop_code IS NOT NULL AND fiscal_cfop_code_issue IS NULL))))),
 CONSTRAINT CK_analytic_fa_fiscal_tax_value CHECK(fiscal_tax_value_p IN('ABSENT','NULL','VALUE')
 AND fiscal_tax_value_w IN('ABSENT','NULL','STRING','INTEGER','NUMBER','BOOLEAN','ARRAY','OBJECT')
 AND ((fiscal_tax_value_p='ABSENT' AND fiscal_tax_value_w='ABSENT') OR(fiscal_tax_value_p='NULL' AND fiscal_tax_value_w='NULL')
 OR(fiscal_tax_value_p='VALUE' AND fiscal_tax_value_w NOT IN('ABSENT','NULL')))
 AND ((fiscal_tax_value_p<>'VALUE' AND fiscal_tax_value_raw IS NULL AND fiscal_tax_value IS NULL AND fiscal_tax_value_issue IS NULL)
 OR(fiscal_tax_value_p='VALUE' AND fiscal_tax_value_raw IS NOT NULL AND ((fiscal_tax_value IS NULL AND fiscal_tax_value_issue IS NOT NULL)
 OR(fiscal_tax_value IS NOT NULL AND fiscal_tax_value_issue IS NULL))))),
 CONSTRAINT CK_analytic_fa_fiscal_pis_value CHECK(fiscal_pis_value_p IN('ABSENT','NULL','VALUE')
 AND fiscal_pis_value_w IN('ABSENT','NULL','STRING','INTEGER','NUMBER','BOOLEAN','ARRAY','OBJECT')
 AND ((fiscal_pis_value_p='ABSENT' AND fiscal_pis_value_w='ABSENT') OR(fiscal_pis_value_p='NULL' AND fiscal_pis_value_w='NULL')
 OR(fiscal_pis_value_p='VALUE' AND fiscal_pis_value_w NOT IN('ABSENT','NULL')))
 AND ((fiscal_pis_value_p<>'VALUE' AND fiscal_pis_value_raw IS NULL AND fiscal_pis_value IS NULL AND fiscal_pis_value_issue IS NULL)
 OR(fiscal_pis_value_p='VALUE' AND fiscal_pis_value_raw IS NOT NULL AND ((fiscal_pis_value IS NULL AND fiscal_pis_value_issue IS NOT NULL)
 OR(fiscal_pis_value IS NOT NULL AND fiscal_pis_value_issue IS NULL))))),
 CONSTRAINT CK_analytic_fa_fiscal_cofins_value CHECK(fiscal_cofins_value_p IN('ABSENT','NULL','VALUE')
 AND fiscal_cofins_value_w IN('ABSENT','NULL','STRING','INTEGER','NUMBER','BOOLEAN','ARRAY','OBJECT')
 AND ((fiscal_cofins_value_p='ABSENT' AND fiscal_cofins_value_w='ABSENT') OR(fiscal_cofins_value_p='NULL' AND fiscal_cofins_value_w='NULL')
 OR(fiscal_cofins_value_p='VALUE' AND fiscal_cofins_value_w NOT IN('ABSENT','NULL')))
 AND ((fiscal_cofins_value_p<>'VALUE' AND fiscal_cofins_value_raw IS NULL AND fiscal_cofins_value IS NULL AND fiscal_cofins_value_issue IS NULL)
 OR(fiscal_cofins_value_p='VALUE' AND fiscal_cofins_value_raw IS NOT NULL AND ((fiscal_cofins_value IS NULL AND fiscal_cofins_value_issue IS NOT NULL)
 OR(fiscal_cofins_value IS NOT NULL AND fiscal_cofins_value_issue IS NULL))))),
 CONSTRAINT CK_analytic_fa_cubages_cubed_weight CHECK(cubages_cubed_weight_p IN('ABSENT','NULL','VALUE')
 AND cubages_cubed_weight_w IN('ABSENT','NULL','STRING','INTEGER','NUMBER','BOOLEAN','ARRAY','OBJECT')
 AND ((cubages_cubed_weight_p='ABSENT' AND cubages_cubed_weight_w='ABSENT') OR(cubages_cubed_weight_p='NULL' AND cubages_cubed_weight_w='NULL')
 OR(cubages_cubed_weight_p='VALUE' AND cubages_cubed_weight_w NOT IN('ABSENT','NULL')))
 AND ((cubages_cubed_weight_p<>'VALUE' AND cubages_cubed_weight_raw IS NULL AND cubages_cubed_weight IS NULL AND cubages_cubed_weight_issue IS NULL)
 OR(cubages_cubed_weight_p='VALUE' AND cubages_cubed_weight_raw IS NOT NULL AND ((cubages_cubed_weight IS NULL AND cubages_cubed_weight_issue IS NOT NULL)
 OR(cubages_cubed_weight IS NOT NULL AND cubages_cubed_weight_issue IS NULL))))),
 CONSTRAINT CK_analytic_fa_freight_weight_subtotal CHECK(freight_weight_subtotal_p IN('ABSENT','NULL','VALUE')
 AND freight_weight_subtotal_w IN('ABSENT','NULL','STRING','INTEGER','NUMBER','BOOLEAN','ARRAY','OBJECT')
 AND ((freight_weight_subtotal_p='ABSENT' AND freight_weight_subtotal_w='ABSENT') OR(freight_weight_subtotal_p='NULL' AND freight_weight_subtotal_w='NULL')
 OR(freight_weight_subtotal_p='VALUE' AND freight_weight_subtotal_w NOT IN('ABSENT','NULL')))
 AND ((freight_weight_subtotal_p<>'VALUE' AND freight_weight_subtotal_raw IS NULL AND freight_weight_subtotal IS NULL AND freight_weight_subtotal_issue IS NULL)
 OR(freight_weight_subtotal_p='VALUE' AND freight_weight_subtotal_raw IS NOT NULL AND ((freight_weight_subtotal IS NULL AND freight_weight_subtotal_issue IS NOT NULL)
 OR(freight_weight_subtotal IS NOT NULL AND freight_weight_subtotal_issue IS NULL))))),
 CONSTRAINT CK_analytic_fa_ad_valorem_subtotal CHECK(ad_valorem_subtotal_p IN('ABSENT','NULL','VALUE')
 AND ad_valorem_subtotal_w IN('ABSENT','NULL','STRING','INTEGER','NUMBER','BOOLEAN','ARRAY','OBJECT')
 AND ((ad_valorem_subtotal_p='ABSENT' AND ad_valorem_subtotal_w='ABSENT') OR(ad_valorem_subtotal_p='NULL' AND ad_valorem_subtotal_w='NULL')
 OR(ad_valorem_subtotal_p='VALUE' AND ad_valorem_subtotal_w NOT IN('ABSENT','NULL')))
 AND ((ad_valorem_subtotal_p<>'VALUE' AND ad_valorem_subtotal_raw IS NULL AND ad_valorem_subtotal IS NULL AND ad_valorem_subtotal_issue IS NULL)
 OR(ad_valorem_subtotal_p='VALUE' AND ad_valorem_subtotal_raw IS NOT NULL AND ((ad_valorem_subtotal IS NULL AND ad_valorem_subtotal_issue IS NOT NULL)
 OR(ad_valorem_subtotal IS NOT NULL AND ad_valorem_subtotal_issue IS NULL))))),
 CONSTRAINT CK_analytic_fa_toll_subtotal CHECK(toll_subtotal_p IN('ABSENT','NULL','VALUE')
 AND toll_subtotal_w IN('ABSENT','NULL','STRING','INTEGER','NUMBER','BOOLEAN','ARRAY','OBJECT')
 AND ((toll_subtotal_p='ABSENT' AND toll_subtotal_w='ABSENT') OR(toll_subtotal_p='NULL' AND toll_subtotal_w='NULL')
 OR(toll_subtotal_p='VALUE' AND toll_subtotal_w NOT IN('ABSENT','NULL')))
 AND ((toll_subtotal_p<>'VALUE' AND toll_subtotal_raw IS NULL AND toll_subtotal IS NULL AND toll_subtotal_issue IS NULL)
 OR(toll_subtotal_p='VALUE' AND toll_subtotal_raw IS NOT NULL AND ((toll_subtotal IS NULL AND toll_subtotal_issue IS NOT NULL)
 OR(toll_subtotal IS NOT NULL AND toll_subtotal_issue IS NULL))))),
 CONSTRAINT CK_analytic_fa_itr_subtotal CHECK(itr_subtotal_p IN('ABSENT','NULL','VALUE')
 AND itr_subtotal_w IN('ABSENT','NULL','STRING','INTEGER','NUMBER','BOOLEAN','ARRAY','OBJECT')
 AND ((itr_subtotal_p='ABSENT' AND itr_subtotal_w='ABSENT') OR(itr_subtotal_p='NULL' AND itr_subtotal_w='NULL')
 OR(itr_subtotal_p='VALUE' AND itr_subtotal_w NOT IN('ABSENT','NULL')))
 AND ((itr_subtotal_p<>'VALUE' AND itr_subtotal_raw IS NULL AND itr_subtotal IS NULL AND itr_subtotal_issue IS NULL)
 OR(itr_subtotal_p='VALUE' AND itr_subtotal_raw IS NOT NULL AND ((itr_subtotal IS NULL AND itr_subtotal_issue IS NOT NULL)
 OR(itr_subtotal IS NOT NULL AND itr_subtotal_issue IS NULL))))),
 CONSTRAINT CK_analytic_fa_pagador_documento CHECK(pagador_documento_p IN('ABSENT','NULL','VALUE')
 AND pagador_documento_w IN('ABSENT','NULL','STRING','INTEGER','NUMBER','BOOLEAN','ARRAY','OBJECT')
 AND ((pagador_documento_p='ABSENT' AND pagador_documento_w='ABSENT') OR(pagador_documento_p='NULL' AND pagador_documento_w='NULL')
 OR(pagador_documento_p='VALUE' AND pagador_documento_w NOT IN('ABSENT','NULL')))
 AND ((pagador_documento_p<>'VALUE' AND pagador_documento_raw IS NULL AND pagador_documento IS NULL AND pagador_documento_issue IS NULL)
 OR(pagador_documento_p='VALUE' AND pagador_documento_raw IS NOT NULL AND ((pagador_documento IS NULL AND pagador_documento_issue IS NOT NULL)
 OR(pagador_documento IS NOT NULL AND pagador_documento_issue IS NULL))))),
 CONSTRAINT CK_analytic_fa_remetente_documento CHECK(remetente_documento_p IN('ABSENT','NULL','VALUE')
 AND remetente_documento_w IN('ABSENT','NULL','STRING','INTEGER','NUMBER','BOOLEAN','ARRAY','OBJECT')
 AND ((remetente_documento_p='ABSENT' AND remetente_documento_w='ABSENT') OR(remetente_documento_p='NULL' AND remetente_documento_w='NULL')
 OR(remetente_documento_p='VALUE' AND remetente_documento_w NOT IN('ABSENT','NULL')))
 AND ((remetente_documento_p<>'VALUE' AND remetente_documento_raw IS NULL AND remetente_documento IS NULL AND remetente_documento_issue IS NULL)
 OR(remetente_documento_p='VALUE' AND remetente_documento_raw IS NOT NULL AND ((remetente_documento IS NULL AND remetente_documento_issue IS NOT NULL)
 OR(remetente_documento IS NOT NULL AND remetente_documento_issue IS NULL))))),
 CONSTRAINT CK_analytic_fa_destinatario_documento CHECK(destinatario_documento_p IN('ABSENT','NULL','VALUE')
 AND destinatario_documento_w IN('ABSENT','NULL','STRING','INTEGER','NUMBER','BOOLEAN','ARRAY','OBJECT')
 AND ((destinatario_documento_p='ABSENT' AND destinatario_documento_w='ABSENT') OR(destinatario_documento_p='NULL' AND destinatario_documento_w='NULL')
 OR(destinatario_documento_p='VALUE' AND destinatario_documento_w NOT IN('ABSENT','NULL')))
 AND ((destinatario_documento_p<>'VALUE' AND destinatario_documento_raw IS NULL AND destinatario_documento IS NULL AND destinatario_documento_issue IS NULL)
 OR(destinatario_documento_p='VALUE' AND destinatario_documento_raw IS NOT NULL AND ((destinatario_documento IS NULL AND destinatario_documento_issue IS NOT NULL)
 OR(destinatario_documento IS NOT NULL AND destinatario_documento_issue IS NULL))))),
 CONSTRAINT CK_analytic_fa_filial_cnpj CHECK(filial_cnpj_p IN('ABSENT','NULL','VALUE')
 AND filial_cnpj_w IN('ABSENT','NULL','STRING','INTEGER','NUMBER','BOOLEAN','ARRAY','OBJECT')
 AND ((filial_cnpj_p='ABSENT' AND filial_cnpj_w='ABSENT') OR(filial_cnpj_p='NULL' AND filial_cnpj_w='NULL')
 OR(filial_cnpj_p='VALUE' AND filial_cnpj_w NOT IN('ABSENT','NULL')))
 AND ((filial_cnpj_p<>'VALUE' AND filial_cnpj_raw IS NULL AND filial_cnpj IS NULL AND filial_cnpj_issue IS NULL)
 OR(filial_cnpj_p='VALUE' AND filial_cnpj_raw IS NOT NULL AND ((filial_cnpj IS NULL AND filial_cnpj_issue IS NOT NULL)
 OR(filial_cnpj IS NOT NULL AND filial_cnpj_issue IS NULL))))),
 CONSTRAINT CK_analytic_fa_nfse_integration_id CHECK(nfse_integration_id_p IN('ABSENT','NULL','VALUE')
 AND nfse_integration_id_w IN('ABSENT','NULL','STRING','INTEGER','NUMBER','BOOLEAN','ARRAY','OBJECT')
 AND ((nfse_integration_id_p='ABSENT' AND nfse_integration_id_w='ABSENT') OR(nfse_integration_id_p='NULL' AND nfse_integration_id_w='NULL')
 OR(nfse_integration_id_p='VALUE' AND nfse_integration_id_w NOT IN('ABSENT','NULL')))
 AND ((nfse_integration_id_p<>'VALUE' AND nfse_integration_id_raw IS NULL AND nfse_integration_id IS NULL AND nfse_integration_id_issue IS NULL)
 OR(nfse_integration_id_p='VALUE' AND nfse_integration_id_raw IS NOT NULL AND ((nfse_integration_id IS NULL AND nfse_integration_id_issue IS NOT NULL)
 OR(nfse_integration_id IS NOT NULL AND nfse_integration_id_issue IS NULL))))),
 CONSTRAINT CK_analytic_fa_nfse_status CHECK(nfse_status_p IN('ABSENT','NULL','VALUE')
 AND nfse_status_w IN('ABSENT','NULL','STRING','INTEGER','NUMBER','BOOLEAN','ARRAY','OBJECT')
 AND ((nfse_status_p='ABSENT' AND nfse_status_w='ABSENT') OR(nfse_status_p='NULL' AND nfse_status_w='NULL')
 OR(nfse_status_p='VALUE' AND nfse_status_w NOT IN('ABSENT','NULL')))
 AND ((nfse_status_p<>'VALUE' AND nfse_status_raw IS NULL AND nfse_status IS NULL AND nfse_status_issue IS NULL)
 OR(nfse_status_p='VALUE' AND nfse_status_raw IS NOT NULL AND ((nfse_status IS NULL AND nfse_status_issue IS NOT NULL)
 OR(nfse_status IS NOT NULL AND nfse_status_issue IS NULL))))),
 CONSTRAINT CK_analytic_fa_nfse_issued_at CHECK(nfse_issued_at_p IN('ABSENT','NULL','VALUE')
 AND nfse_issued_at_w IN('ABSENT','NULL','STRING','INTEGER','NUMBER','BOOLEAN','ARRAY','OBJECT')
 AND ((nfse_issued_at_p='ABSENT' AND nfse_issued_at_w='ABSENT') OR(nfse_issued_at_p='NULL' AND nfse_issued_at_w='NULL')
 OR(nfse_issued_at_p='VALUE' AND nfse_issued_at_w NOT IN('ABSENT','NULL')))
 AND ((nfse_issued_at_p<>'VALUE' AND nfse_issued_at_raw IS NULL AND nfse_issued_at IS NULL AND nfse_issued_at_issue IS NULL)
 OR(nfse_issued_at_p='VALUE' AND nfse_issued_at_raw IS NOT NULL AND ((nfse_issued_at IS NULL AND nfse_issued_at_issue IS NOT NULL)
 OR(nfse_issued_at IS NOT NULL AND nfse_issued_at_issue IS NULL))))),
 CONSTRAINT CK_analytic_fa_nfse_cancelation_reason CHECK(nfse_cancelation_reason_p IN('ABSENT','NULL','VALUE')
 AND nfse_cancelation_reason_w IN('ABSENT','NULL','STRING','INTEGER','NUMBER','BOOLEAN','ARRAY','OBJECT')
 AND ((nfse_cancelation_reason_p='ABSENT' AND nfse_cancelation_reason_w='ABSENT') OR(nfse_cancelation_reason_p='NULL' AND nfse_cancelation_reason_w='NULL')
 OR(nfse_cancelation_reason_p='VALUE' AND nfse_cancelation_reason_w NOT IN('ABSENT','NULL')))
 AND ((nfse_cancelation_reason_p<>'VALUE' AND nfse_cancelation_reason_raw IS NULL AND nfse_cancelation_reason IS NULL AND nfse_cancelation_reason_issue IS NULL)
 OR(nfse_cancelation_reason_p='VALUE' AND nfse_cancelation_reason_raw IS NOT NULL AND ((nfse_cancelation_reason IS NULL AND nfse_cancelation_reason_issue IS NOT NULL)
 OR(nfse_cancelation_reason IS NOT NULL AND nfse_cancelation_reason_issue IS NULL))))),
 CONSTRAINT CK_analytic_fa_nfse_pdf_service_url CHECK(nfse_pdf_service_url_p IN('ABSENT','NULL','VALUE')
 AND nfse_pdf_service_url_w IN('ABSENT','NULL','STRING','INTEGER','NUMBER','BOOLEAN','ARRAY','OBJECT')
 AND ((nfse_pdf_service_url_p='ABSENT' AND nfse_pdf_service_url_w='ABSENT') OR(nfse_pdf_service_url_p='NULL' AND nfse_pdf_service_url_w='NULL')
 OR(nfse_pdf_service_url_p='VALUE' AND nfse_pdf_service_url_w NOT IN('ABSENT','NULL')))
 AND ((nfse_pdf_service_url_p<>'VALUE' AND nfse_pdf_service_url_raw IS NULL AND nfse_pdf_service_url IS NULL AND nfse_pdf_service_url_issue IS NULL)
 OR(nfse_pdf_service_url_p='VALUE' AND nfse_pdf_service_url_raw IS NOT NULL AND ((nfse_pdf_service_url IS NULL AND nfse_pdf_service_url_issue IS NOT NULL)
 OR(nfse_pdf_service_url IS NOT NULL AND nfse_pdf_service_url_issue IS NULL))))),
 CONSTRAINT CK_analytic_fa_nfse_corporation_id CHECK(nfse_corporation_id_p IN('ABSENT','NULL','VALUE')
 AND nfse_corporation_id_w IN('ABSENT','NULL','STRING','INTEGER','NUMBER','BOOLEAN','ARRAY','OBJECT')
 AND ((nfse_corporation_id_p='ABSENT' AND nfse_corporation_id_w='ABSENT') OR(nfse_corporation_id_p='NULL' AND nfse_corporation_id_w='NULL')
 OR(nfse_corporation_id_p='VALUE' AND nfse_corporation_id_w NOT IN('ABSENT','NULL')))
 AND ((nfse_corporation_id_p<>'VALUE' AND nfse_corporation_id_raw IS NULL AND nfse_corporation_id IS NULL AND nfse_corporation_id_issue IS NULL)
 OR(nfse_corporation_id_p='VALUE' AND nfse_corporation_id_raw IS NOT NULL AND ((nfse_corporation_id IS NULL AND nfse_corporation_id_issue IS NOT NULL)
 OR(nfse_corporation_id IS NOT NULL AND nfse_corporation_id_issue IS NULL))))),
 CONSTRAINT CK_analytic_fa_nfse_service_description CHECK(nfse_service_description_p IN('ABSENT','NULL','VALUE')
 AND nfse_service_description_w IN('ABSENT','NULL','STRING','INTEGER','NUMBER','BOOLEAN','ARRAY','OBJECT')
 AND ((nfse_service_description_p='ABSENT' AND nfse_service_description_w='ABSENT') OR(nfse_service_description_p='NULL' AND nfse_service_description_w='NULL')
 OR(nfse_service_description_p='VALUE' AND nfse_service_description_w NOT IN('ABSENT','NULL')))
 AND ((nfse_service_description_p<>'VALUE' AND nfse_service_description_raw IS NULL AND nfse_service_description IS NULL AND nfse_service_description_issue IS NULL)
 OR(nfse_service_description_p='VALUE' AND nfse_service_description_raw IS NOT NULL AND ((nfse_service_description IS NULL AND nfse_service_description_issue IS NOT NULL)
 OR(nfse_service_description IS NOT NULL AND nfse_service_description_issue IS NULL))))),
 CONSTRAINT CK_analytic_fa_nfse_xml_document CHECK(nfse_xml_document_p IN('ABSENT','NULL','VALUE')
 AND nfse_xml_document_w IN('ABSENT','NULL','STRING','INTEGER','NUMBER','BOOLEAN','ARRAY','OBJECT')
 AND ((nfse_xml_document_p='ABSENT' AND nfse_xml_document_w='ABSENT') OR(nfse_xml_document_p='NULL' AND nfse_xml_document_w='NULL')
 OR(nfse_xml_document_p='VALUE' AND nfse_xml_document_w NOT IN('ABSENT','NULL')))
 AND ((nfse_xml_document_p<>'VALUE' AND nfse_xml_document_raw IS NULL AND nfse_xml_document IS NULL AND nfse_xml_document_issue IS NULL)
 OR(nfse_xml_document_p='VALUE' AND nfse_xml_document_raw IS NOT NULL AND ((nfse_xml_document IS NULL AND nfse_xml_document_issue IS NOT NULL)
 OR(nfse_xml_document IS NOT NULL AND nfse_xml_document_issue IS NULL)))))
);
CREATE INDEX IX_analytic_freight_attributes ON stg.analytic_freight_attributes(run_id,source_key,revision DESC,freight_stage_id)
 INCLUDE(valid,source_execution);
GO
CREATE TRIGGER stg.trg_analytic_freight_attributes ON stg.analytic_freight_attributes AFTER INSERT,UPDATE,DELETE
AS BEGIN SET NOCOUNT ON;
 IF EXISTS(SELECT 1 FROM deleted) THROW 53572,N'ANA_FREIGHT_ATTRIBUTES_IMMUTABLE',1;
 IF EXISTS(SELECT 1 FROM inserted i WHERE i.valid<>CASE WHEN i.modal_issue IS NOT NULL
 OR i.tipo_frete_issue IS NOT NULL
 OR i.accounting_credit_id_issue IS NOT NULL
 OR i.accounting_credit_installment_id_issue IS NOT NULL
 OR i.valor_notas_issue IS NOT NULL
 OR i.peso_notas_issue IS NOT NULL
 OR i.id_corporacao_issue IS NOT NULL
 OR i.id_cidade_destino_issue IS NOT NULL
 OR i.data_previsao_entrega_issue IS NOT NULL
 OR i.service_date_issue IS NOT NULL
 OR i.pick_item_id_issue IS NOT NULL
 OR i.pagador_id_issue IS NOT NULL
 OR i.pagador_nome_issue IS NOT NULL
 OR i.remetente_id_issue IS NOT NULL
 OR i.remetente_nome_issue IS NOT NULL
 OR i.origem_cidade_issue IS NOT NULL
 OR i.origem_uf_issue IS NOT NULL
 OR i.destinatario_id_issue IS NOT NULL
 OR i.destinatario_nome_issue IS NOT NULL
 OR i.destino_cidade_issue IS NOT NULL
 OR i.destino_uf_issue IS NOT NULL
 OR i.filial_nome_issue IS NOT NULL
 OR i.filial_apelido_issue IS NOT NULL
 OR i.numero_nota_fiscal_issue IS NOT NULL
 OR i.tabela_preco_nome_issue IS NOT NULL
 OR i.classificacao_nome_issue IS NOT NULL
 OR i.centro_custo_nome_issue IS NOT NULL
 OR i.usuario_nome_issue IS NOT NULL
 OR i.invoices_total_volumes_issue IS NOT NULL
 OR i.taxed_weight_issue IS NOT NULL
 OR i.real_weight_issue IS NOT NULL
 OR i.total_cubic_volume_issue IS NOT NULL
 OR i.subtotal_issue IS NOT NULL
 OR i.chave_cte_issue IS NOT NULL
 OR i.numero_cte_issue IS NOT NULL
 OR i.serie_cte_issue IS NOT NULL
 OR i.cte_id_issue IS NOT NULL
 OR i.cte_emission_type_issue IS NOT NULL
 OR i.service_type_issue IS NOT NULL
 OR i.insurance_enabled_issue IS NOT NULL
 OR i.gris_subtotal_issue IS NOT NULL
 OR i.tde_subtotal_issue IS NOT NULL
 OR i.modal_cte_issue IS NOT NULL
 OR i.redispatch_subtotal_issue IS NOT NULL
 OR i.suframa_subtotal_issue IS NOT NULL
 OR i.payment_type_issue IS NOT NULL
 OR i.previous_document_type_issue IS NOT NULL
 OR i.products_value_issue IS NOT NULL
 OR i.trt_subtotal_issue IS NOT NULL
 OR i.nfse_series_issue IS NOT NULL
 OR i.nfse_number_issue IS NOT NULL
 OR i.insurance_id_issue IS NOT NULL
 OR i.other_fees_issue IS NOT NULL
 OR i.km_issue IS NOT NULL
 OR i.payment_accountable_type_issue IS NOT NULL
 OR i.insured_value_issue IS NOT NULL
 OR i.globalized_issue IS NOT NULL
 OR i.sec_cat_subtotal_issue IS NOT NULL
 OR i.globalized_type_issue IS NOT NULL
 OR i.price_table_accountable_type_issue IS NOT NULL
 OR i.insurance_accountable_type_issue IS NOT NULL
 OR i.fiscal_calculation_basis_issue IS NOT NULL
 OR i.fiscal_tax_rate_issue IS NOT NULL
 OR i.fiscal_pis_rate_issue IS NOT NULL
 OR i.fiscal_cofins_rate_issue IS NOT NULL
 OR i.fiscal_has_difal_issue IS NOT NULL
 OR i.fiscal_difal_origin_issue IS NOT NULL
 OR i.fiscal_difal_destination_issue IS NOT NULL
 OR i.fiscal_cst_type_issue IS NOT NULL
 OR i.fiscal_cfop_code_issue IS NOT NULL
 OR i.fiscal_tax_value_issue IS NOT NULL
 OR i.fiscal_pis_value_issue IS NOT NULL
 OR i.fiscal_cofins_value_issue IS NOT NULL
 OR i.cubages_cubed_weight_issue IS NOT NULL
 OR i.freight_weight_subtotal_issue IS NOT NULL
 OR i.ad_valorem_subtotal_issue IS NOT NULL
 OR i.toll_subtotal_issue IS NOT NULL
 OR i.itr_subtotal_issue IS NOT NULL
 OR i.pagador_documento_issue IS NOT NULL
 OR i.remetente_documento_issue IS NOT NULL
 OR i.destinatario_documento_issue IS NOT NULL
 OR i.filial_cnpj_issue IS NOT NULL
 OR i.nfse_integration_id_issue IS NOT NULL
 OR i.nfse_status_issue IS NOT NULL
 OR i.nfse_issued_at_issue IS NOT NULL
 OR i.nfse_cancelation_reason_issue IS NOT NULL
 OR i.nfse_pdf_service_url_issue IS NOT NULL
 OR i.nfse_corporation_id_issue IS NOT NULL
 OR i.nfse_service_description_issue IS NOT NULL
 OR i.nfse_xml_document_issue IS NOT NULL THEN 0 ELSE 1 END)
 THROW 53574,N'ANA_FREIGHT_ATTRIBUTE_VALIDITY',1;
 IF EXISTS(SELECT 1 FROM inserted i WHERE NOT EXISTS(SELECT 1 FROM ctl.analytic_lab_source_group g
 JOIN stg.expansion_lab_dependency_observation o ON o.run_id=g.expansion_run AND o.entity='FRETE'
 JOIN ctl.expansion_lab_dependency_capture c ON c.execution_id=o.execution_id AND c.state='COMPLETE'
 JOIN core.expansion_lab_dependency current_root ON current_root.run_id=g.expansion_run AND current_root.entity='FRETE'
 AND current_root.source_key=i.source_key AND current_root.stage_record_id=i.base_stage_id AND current_root.state='VALID'
 JOIN stg.frete_record original ON original.stage_record_id=i.freight_stage_id
 JOIN stg.frete_record canonical ON canonical.stage_record_id=i.base_stage_id
 WHERE g.run_id=i.run_id AND o.stage_record_id=i.freight_stage_id AND o.source_key=i.source_key
 AND o.execution_id=i.source_execution AND o.disposition IN('NOOP','INSERTED','UPDATED')
 AND DATALENGTH(original.payload_json)=DATALENGTH(canonical.payload_json)
 AND CONVERT(VARBINARY(MAX),original.payload_json)=CONVERT(VARBINARY(MAX),canonical.payload_json)))
 THROW 53573,N'ANA_FREIGHT_ATTRIBUTE_CAPTURE_BINDING',1;
END;
GO
CREATE FUNCTION core.ufn_analytic_freight_attributes(@run_id UNIQUEIDENTIFIER)
RETURNS TABLE AS RETURN (
 WITH ranked AS(SELECT a.*,DENSE_RANK() OVER(PARTITION BY source_key,base_stage_id ORDER BY revision DESC) freshness_rank
 FROM stg.analytic_freight_attributes a WHERE run_id=@run_id),
 pointers AS(SELECT source_key,base_stage_id,MIN(observation_id) observation_id,
 CONVERT(BIT,CASE WHEN MIN(CONVERT(INT,valid))=0 THEN 1 ELSE 0 END) invalid
 FROM ranked WHERE freshness_rank=1 GROUP BY source_key,base_stage_id)
 SELECT a.*,CONVERT(VARCHAR(24),CASE WHEN p.invalid=1 THEN 'INVALID_ATTRIBUTES'
 WHEN EXISTS(SELECT 1 FROM ranked other WHERE other.source_key=a.source_key AND other.base_stage_id=a.base_stage_id
 AND other.freshness_rank=1 AND other.comparison_bytes<>a.comparison_bytes) THEN 'ATTRIBUTE_CONFLICT' ELSE 'READY' END) disposition
 FROM pointers p JOIN stg.analytic_freight_attributes a ON a.observation_id=p.observation_id
);
GO
