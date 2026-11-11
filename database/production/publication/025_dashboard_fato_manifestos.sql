-- Legacy Manifestos aggregation over V2 reports and the observed freight-to-collection key.
CREATE OR ALTER VIEW core.dashboard_fato_manifestos
AS
WITH coletas_vigentes AS (
    SELECT DISTINCT TRY_CONVERT(bigint,JSON_VALUE(source_envelope,'$.record.sequence_code')) AS sequence_code
    FROM stg.production_export_latest WHERE entity_name='COLETAS'
), fretes_fonte AS (
    SELECT j.*,ROW_NUMBER() OVER(PARTITION BY j.id ORDER BY
        l.stored_utc DESC,l.business_date DESC,l.occurrence_ordinal DESC) AS row_rank
    FROM stg.production_export_latest l
    CROSS APPLY OPENJSON(l.source_envelope,'$.record') WITH(
        id bigint '$.id',
        collection_number bigint '$.fit_p_m_pck_sequence_code',
        freight_total decimal(18,2) '$.total'
    ) j
    WHERE l.entity_name='FRETES' AND j.id IS NOT NULL
), receita_coletas AS (
    SELECT c.sequence_code,SUM(COALESCE(f.freight_total,0)) AS receita_coleta
    FROM coletas_vigentes c JOIN fretes_fonte f ON f.collection_number=c.sequence_code AND f.row_rank=1
    GROUP BY c.sequence_code
),
manifestos_linha AS (
SELECT
    m.*,
    competencia_operacional.data_competencia_operacional,
    proprietario_documento.proprietario_documento,
    tipo_motorista.tipo_motorista
FROM core.dashboard_manifestos_linhas AS m
CROSS APPLY (
    SELECT COALESCE(m.departured_at, m.created_at) AS data_competencia_operacional
) competencia_operacional
OUTER APPLY OPENJSON(CASE WHEN ISJSON(m.metadata) = 1 THEN m.metadata END)
WITH (
    mft_vie_onr_document NVARCHAR(255) '$.mft_vie_onr_document',
    mft_vie_onr_cnpj NVARCHAR(255) '$.mft_vie_onr_cnpj',
    vehicle_owner_document NVARCHAR(255) '$.vehicle_owner_document',
    owner_document NVARCHAR(255) '$.owner_document',
    mft_vie_owner_document NVARCHAR(255) '$.mft_vie_owner_document'
) metadata_proprietario
OUTER APPLY (
    SELECT NULLIF(
        REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(
            COALESCE(
                metadata_proprietario.mft_vie_onr_document,
                metadata_proprietario.mft_vie_onr_cnpj,
                metadata_proprietario.vehicle_owner_document,
                metadata_proprietario.owner_document,
                metadata_proprietario.mft_vie_owner_document
            ),
            '.', ''), '-', ''), '/', ''), ' ', ''), CHAR(9), ''
        ),
        ''
    ) AS proprietario_documento
) proprietario_documento
OUTER APPLY (
    SELECT CASE
        WHEN UPPER(LTRIM(RTRIM(COALESCE(m.vehicle_owner, '')))) COLLATE Latin1_General_CI_AI = N'RODOGARCIA TRANSPORTES RODOVIARIOS LTDA'
        THEN N'Frota Própria'
        WHEN LOWER(LTRIM(RTRIM(COALESCE(m.contract_type, '')))) COLLATE Latin1_General_CI_AI IN (
            N'aggregate',
            N'aggregated',
            N'agregado',
            N'prestador agregado',
            N'exclusive',
            N'exclusivo'
        )
          OR LOWER(LTRIM(RTRIM(COALESCE(m.contract_type, '')))) COLLATE Latin1_General_CI_AI LIKE N'%agreg%'
          OR LOWER(LTRIM(RTRIM(COALESCE(m.contract_type, '')))) COLLATE Latin1_General_CI_AI LIKE N'%exclus%'
        THEN N'Agregado'
        ELSE N'Terceiro / Autônomo'
    END AS tipo_motorista
) tipo_motorista
WHERE COALESCE(m.excluido_na_origem, 0) = 0
  AND (m.vehicle_plate IS NULL OR m.vehicle_plate <> N'ACM0000')),
        agregados AS (
SELECT
    ml.sequence_code,
    ml.sequence_code AS numero_manifesto,
    MAX(ml.identificador_unico) AS identificador_unico,
    MAX(ml.data_competencia_operacional) AS data_criacao,
    CAST(MAX(ml.data_competencia_operacional) AS DATE) AS data_criacao_date,
    CASE
        WHEN MAX(ml.data_competencia_operacional) IS NULL THEN NULL
        ELSE YEAR(CAST(MAX(ml.data_competencia_operacional) AS DATE)) * 100 + MONTH(CAST(MAX(ml.data_competencia_operacional) AS DATE))
    END AS data_criacao_yyyymm,
    CAST(MAX(ml.created_at) AS TIME(0)) AS hora_solicitacao,
    CAST(MAX(ml.created_at) AS TIME(0)) AS hora_criacao,
    MAX(ml.departured_at) AS saida,
    MAX(ml.closed_at) AS fechamento,
    MAX(ml.finished_at) AS chegada,
    CASE
        WHEN MAX(CASE WHEN LOWER(LTRIM(RTRIM(ml.status))) = N'closed' THEN 1 ELSE 0 END) = 1
            THEN N'closed'
        WHEN MAX(CASE WHEN LOWER(LTRIM(RTRIM(ml.status))) = N'in_transit' THEN 1 ELSE 0 END) = 1
            THEN N'in_transit'
        WHEN MAX(CASE WHEN LOWER(LTRIM(RTRIM(ml.status))) = N'pending' THEN 1 ELSE 0 END) = 1
            THEN N'pending'
        ELSE MAX(ml.status)
    END AS status_raw,
    MAX(ml.classification) AS classificacao,
    MAX(ml.branch_nickname) AS filial,
    MAX(ml.branch_nickname) AS filial_emissora,
    NULLIF(LOWER(LTRIM(RTRIM(MAX(ml.branch_nickname)))), N'') AS filial_key,
    MAX(ml.mdfe_number) AS mdfe_number,
    MAX(ml.mdfe_key) AS mdfe_key,
    MAX(ml.mdfe_status) AS mdfe_status_raw,
    MAX(ml.distribution_pole) AS distribution_pole,
    MAX(ml.vehicle_plate) AS placa_veiculo,
    NULLIF(LOWER(LTRIM(RTRIM(MAX(ml.vehicle_plate)))), N'') AS placa_veiculo_key,
    MAX(ml.vehicle_type) AS tipo_veiculo,
    MAX(ml.vehicle_owner) AS proprietario_nome,
    MAX(ml.proprietario_documento) AS proprietario_documento,
    MAX(ml.tipo_motorista) AS tipo_motorista,
    NULLIF(LOWER(LTRIM(RTRIM(MAX(ml.tipo_motorista)))), N'') AS tipo_motorista_key,
    MAX(ml.driver_name) AS motorista,
    NULLIF(LOWER(LTRIM(RTRIM(MAX(ml.driver_name)))), N'') AS motorista_key,
    MAX(ml.vehicle_departure_km) AS km_saida,
    MAX(ml.closing_km) AS km_chegada,
    MAX(ml.traveled_km) AS km_viagem,
    CASE MAX(CAST(ml.manual_km AS INT))
        WHEN 1 THEN N'é manual'
        WHEN 0 THEN N'não é manual'
        ELSE NULL
    END AS km_manual,
    MAX(ml.km) AS km_total,
    MAX(ml.invoices_count) AS qtd_nf,
    MAX(ml.invoices_volumes) AS volumes_nf,
    MAX(ml.invoices_weight) AS peso_nf,
    MAX(ml.total_taxed_weight) AS peso_taxado,
    MAX(ml.total_cubic_volume) AS total_m3,
    MAX(ml.invoices_value) AS valor_nf,
    MAX(ml.manifest_freights_total) AS fretes_total,
    SUM(COALESCE(rc.receita_coleta, 0)) AS coletas_total,
    MAX(COALESCE(ml.manifest_freights_total, 0)) + SUM(COALESCE(rc.receita_coleta, 0)) AS receita_total,
    STRING_AGG(CONVERT(NVARCHAR(MAX), ml.pick_sequence_code), N', ') AS coleta_numeros,
    MAX(ml.contract_number) AS contract_number,
    MAX(ml.contract_type) AS contract_type_vehicle_raw,
    MAX(ml.driver_contract_type) AS contract_type_driver_raw,
    MAX(ml.calculation_type) AS calculation_type_raw,
    MAX(ml.cargo_type) AS cargo_type_raw,
    MAX(ml.daily_subtotal) AS diaria,
    MAX(ml.total_cost) AS custo_total,
    MAX(ml.freight_subtotal) AS valor_frete,
    MAX(ml.fuel_subtotal) AS combustivel,
    MAX(ml.toll_subtotal) AS pedagio,
    MAX(ml.driver_services_total) AS servicos_motorista_total,
    MAX(ml.operational_expenses_total) AS despesa_operacional,
    MAX(ml.inss_value) AS inss_value,
    MAX(ml.sest_senat_value) AS sest_senat_value,
    MAX(ml.ir_value) AS ir_value,
    MAX(ml.paying_total) AS saldo_pagar,
    MAX(ml.uniq_destinations_count) AS uniq_destinations_count,
    CAST(MAX(CAST(ml.generate_mdfe AS INT)) AS BIT) AS gerar_mdfe,
    CAST(MAX(CAST(ml.monitoring_request AS INT)) AS BIT) AS solicitou_monitoramento,
    CASE MAX(CAST(ml.monitoring_request AS INT))
        WHEN 1 THEN N'sim'
        WHEN 0 THEN N'não'
        ELSE NULL
    END AS solicitacao_monitoramento,
    MAX(ml.mobile_read_at) AS leitura_movel_em,
    MAX(ml.delivery_manifest_items_count) AS itens_entrega,
    MAX(ml.transfer_manifest_items_count) AS itens_transferencia,
    MAX(ml.pick_manifest_items_count) AS itens_coleta,
    MAX(ml.dispatch_draft_manifest_items_count) AS itens_despacho_rascunho,
    MAX(ml.consolidation_manifest_items_count) AS itens_consolidacao,
    MAX(ml.reverse_pick_manifest_items_count) AS itens_coleta_reversa,
    MAX(ml.manifest_items_count) AS itens_total,
    MAX(ml.finalized_manifest_items_count) AS itens_finalizados,
    MAX(ml.calculated_pick_count) AS calculado_coleta,
    MAX(ml.calculated_delivery_count) AS calculado_entrega,
    MAX(ml.calculated_dispatch_count) AS calculado_despacho,
    MAX(ml.calculated_consolidation_count) AS calculado_consolidacao,
    MAX(ml.calculated_reverse_pick_count) AS calculado_coleta_reversa,
    MAX(ml.pick_subtotal) AS valor_coletas,
    MAX(ml.delivery_subtotal) AS valor_entregas,
    MAX(ml.dispatch_subtotal) AS despachos,
    MAX(ml.consolidation_subtotal) AS consolidacoes,
    MAX(ml.reverse_pick_subtotal) AS coleta_reversa,
    MAX(ml.advance_subtotal) AS adiantamento,
    MAX(ml.fleet_costs_subtotal) AS custos_frota,
    MAX(ml.additionals_subtotal) AS adicionais,
    MAX(ml.discounts_subtotal) AS descontos,
    MAX(ml.discount_value) AS desconto_valor,
    MAX(ml.adjustment_comments) AS ajuste_comentarios,
    MAX(ml.iks_id) AS iks_id,
    MAX(ml.programacao_sequence_code) AS programacao_sequence_code,
    MAX(ml.programacao_starting_at) AS programacao_inicio,
    MAX(ml.programacao_ending_at) AS programacao_termino,
    MAX(ml.trailer1_license_plate) AS carreta1_placa,
    MAX(ml.trailer1_weight_capacity) AS carreta1_capacidade_peso,
    MAX(ml.trailer2_license_plate) AS carreta2_placa,
    MAX(ml.trailer2_weight_capacity) AS carreta2_capacidade_peso,
    MAX(ml.vehicle_weight_capacity) AS veiculo_capacidade_peso,
    MAX(ml.vehicle_cubic_weight) AS veiculo_peso_cubado,
    (
        COALESCE(MAX(ml.vehicle_weight_capacity), 0)
        + COALESCE(MAX(ml.trailer1_weight_capacity), 0)
        + COALESCE(MAX(ml.trailer2_weight_capacity), 0)
    ) AS capacidade_lotacao_kg,
    MAX(REPLACE(REPLACE(REPLACE(ml.unloading_recipient_names, '[', ''), ']', ''), '"', '')) AS descarregamento_destinatarios,
    MAX(REPLACE(REPLACE(REPLACE(ml.unloading_recipient_names, '[', ''), ']', ''), '"', '')) AS local_descarregamento,
    MAX(REPLACE(REPLACE(REPLACE(ml.delivery_region_names, '[', ''), ']', ''), '"', '')) AS entrega_regioes,
    MAX(ml.programacao_cliente) AS programacao_cliente,
    MAX(ml.programacao_tipo_servico) AS programacao_tipo_servico,
    MAX(ml.creation_user_name) AS usuario_emissor,
    MAX(ml.adjustment_user_name) AS usuario_ajuste,
    MAX(ml.obs_operacional) AS obs_operacional,
    MAX(ml.obs_financeira) AS obs_financeira,
    MAX(ml.metadata) AS metadata,
    MAX(CAST(ml.data_extracao AS DATETIME2(0))) AS data_extracao
FROM manifestos_linha AS ml
LEFT JOIN receita_coletas AS rc
    ON rc.sequence_code = ml.pick_sequence_code
GROUP BY ml.sequence_code
        ),
        origem AS (
SELECT
    a.sequence_code,
    a.numero_manifesto,
    a.identificador_unico,
    a.data_criacao,
    a.data_criacao_date,
    a.data_criacao_yyyymm,
    a.hora_solicitacao,
    a.hora_criacao,
    a.saida,
    a.fechamento,
    a.chegada,
    status_traduzido.status,
    a.status_raw,
    NULLIF(LOWER(LTRIM(RTRIM(status_traduzido.status))), N'') AS status_key,
    a.classificacao,
    CASE
        WHEN LOWER(LTRIM(RTRIM(COALESCE(a.classificacao, N'')))) COLLATE Latin1_General_CI_AI LIKE N'%distribu%' THEN N'distribuicao'
        WHEN LOWER(LTRIM(RTRIM(COALESCE(a.classificacao, N'')))) COLLATE Latin1_General_CI_AI LIKE N'%transfer%' THEN N'transferencia'
        WHEN LOWER(LTRIM(RTRIM(COALESCE(a.classificacao, N'')))) COLLATE Latin1_General_CI_AI LIKE N'%carga%fechada%'
          OR LOWER(LTRIM(RTRIM(COALESCE(tipo_carga.tipo_carga, N'')))) COLLATE Latin1_General_CI_AI LIKE N'%carga%fechada%'
        THEN N'cargaFechada'
        ELSE N'distribuicao'
    END AS classificacao_bucket,
    a.filial,
    a.filial_emissora,
    a.filial_key,
    a.mdfe_number,
    a.mdfe_key,
    mdfe_status.mdfe_status,
    a.distribution_pole,
    a.placa_veiculo,
    a.placa_veiculo_key,
    a.tipo_veiculo,
    a.proprietario_nome,
    a.proprietario_documento,
    a.tipo_motorista,
    a.tipo_motorista_key,
    a.motorista,
    a.motorista_key,
    a.km_saida,
    a.km_chegada,
    a.km_viagem,
    a.km_manual,
    a.km_total,
    a.qtd_nf,
    a.volumes_nf,
    a.peso_nf,
    a.peso_taxado,
    a.total_m3,
    a.valor_nf,
    a.fretes_total,
    a.coletas_total,
    a.receita_total,
    a.coleta_numeros,
    a.contract_number,
    tipo_contrato_veiculo.tipo_contrato_veiculo,
    NULLIF(LOWER(LTRIM(RTRIM(tipo_contrato_veiculo.tipo_contrato_veiculo))), N'') AS tipo_contrato_veiculo_key,
    tipo_contrato_motorista.tipo_contrato_motorista,
    NULLIF(LOWER(LTRIM(RTRIM(tipo_contrato_motorista.tipo_contrato_motorista))), N'') AS tipo_contrato_motorista_key,
    tipo_contrato.tipo_contrato,
    NULLIF(LOWER(LTRIM(RTRIM(tipo_contrato.tipo_contrato))), N'') AS tipo_contrato_key,
    tipo_calculo.tipo_calculo,
    tipo_carga.tipo_carga,
    NULLIF(LOWER(LTRIM(RTRIM(tipo_carga.tipo_carga))), N'') AS tipo_carga_key,
    a.diaria,
    a.custo_total,
    a.valor_frete,
    a.combustivel,
    a.pedagio,
    a.servicos_motorista_total,
    a.despesa_operacional,
    a.inss_value,
    a.sest_senat_value,
    a.ir_value,
    a.saldo_pagar,
    a.uniq_destinations_count,
    a.gerar_mdfe,
    a.solicitou_monitoramento,
    a.solicitacao_monitoramento,
    a.leitura_movel_em,
    a.itens_entrega,
    a.itens_transferencia,
    a.itens_coleta,
    a.itens_despacho_rascunho,
    a.itens_consolidacao,
    a.itens_coleta_reversa,
    a.itens_total,
    a.itens_finalizados,
    a.calculado_coleta,
    a.calculado_entrega,
    a.calculado_despacho,
    a.calculado_consolidacao,
    a.calculado_coleta_reversa,
    a.valor_coletas,
    a.valor_entregas,
    a.despachos,
    a.consolidacoes,
    a.coleta_reversa,
    a.adiantamento,
    a.custos_frota,
    a.adicionais,
    a.descontos,
    a.desconto_valor,
    a.ajuste_comentarios,
    a.iks_id,
    a.programacao_sequence_code,
    a.programacao_inicio,
    a.programacao_termino,
    a.carreta1_placa,
    a.carreta1_capacidade_peso,
    a.carreta2_placa,
    a.carreta2_capacidade_peso,
    a.veiculo_capacidade_peso,
    a.veiculo_peso_cubado,
    a.capacidade_lotacao_kg,
    a.descarregamento_destinatarios,
    a.local_descarregamento,
    a.entrega_regioes,
    a.programacao_cliente,
    a.programacao_tipo_servico,
    a.usuario_emissor,
    a.usuario_ajuste,
    a.obs_operacional,
    a.obs_financeira,
    a.metadata,
    a.data_extracao,
    CAST(0 AS BIT) AS excluido_na_origem,
    CAST(NULL AS DATETIME2(0)) AS data_exclusao_origem,
    (SELECT MAX(CONVERT(datetime2(0),stored_utc)) FROM ctl.production_ingestion_coverage WHERE entity_name='MANIFESTOS') AS ultima_reconciliacao_origem_em,
    (SELECT MAX(CONVERT(datetime2(0),stored_utc)) FROM ctl.production_ingestion_coverage WHERE entity_name='MANIFESTOS') AS snapshot_em,
    CONVERT(BINARY(32), HASHBYTES('SHA2_256', CONCAT_WS(N'|',
        CONVERT(NVARCHAR(30), a.sequence_code),
        COALESCE(a.identificador_unico, N'__NULL__'),
        COALESCE(CONVERT(NVARCHAR(48), a.data_criacao, 127), N'__NULL__'),
        COALESCE(status_traduzido.status, N'__NULL__'),
        COALESCE(a.classificacao, N'__NULL__'),
        COALESCE(a.filial, N'__NULL__'),
        COALESCE(a.placa_veiculo, N'__NULL__'),
        COALESCE(a.tipo_veiculo, N'__NULL__'),
        COALESCE(a.proprietario_nome, N'__NULL__'),
        COALESCE(a.proprietario_documento, N'__NULL__'),
        COALESCE(a.tipo_motorista, N'__NULL__'),
        COALESCE(a.motorista, N'__NULL__'),
        COALESCE(CONVERT(NVARCHAR(40), a.peso_taxado), N'__NULL__'),
        COALESCE(CONVERT(NVARCHAR(40), a.total_m3), N'__NULL__'),
        COALESCE(CONVERT(NVARCHAR(40), a.fretes_total), N'__NULL__'),
        COALESCE(CONVERT(NVARCHAR(40), a.coletas_total), N'__NULL__'),
        COALESCE(CONVERT(NVARCHAR(40), a.receita_total), N'__NULL__'),
        COALESCE(tipo_contrato_veiculo.tipo_contrato_veiculo, N'__NULL__'),
        COALESCE(tipo_contrato_motorista.tipo_contrato_motorista, N'__NULL__'),
        COALESCE(tipo_contrato.tipo_contrato, N'__NULL__'),
        COALESCE(CONVERT(NVARCHAR(40), a.custo_total), N'__NULL__'),
        COALESCE(CONVERT(NVARCHAR(40), a.valor_frete), N'__NULL__'),
        COALESCE(CONVERT(NVARCHAR(40), a.combustivel), N'__NULL__'),
        COALESCE(CONVERT(NVARCHAR(40), a.pedagio), N'__NULL__'),
        COALESCE(CONVERT(NVARCHAR(40), a.saldo_pagar), N'__NULL__'),
        COALESCE(CONVERT(NVARCHAR(40), a.km_total), N'__NULL__'),
        COALESCE(CONVERT(NVARCHAR(40), a.capacidade_lotacao_kg), N'__NULL__'),
        COALESCE(CONVERT(NVARCHAR(20), a.itens_total), N'__NULL__'),
        COALESCE(CONVERT(NVARCHAR(20), a.itens_finalizados), N'__NULL__'),
        COALESCE(a.local_descarregamento, N'__NULL__'),
        COALESCE(CONVERT(NVARCHAR(30), a.data_extracao, 126), N'__NULL__')
    ))) AS hash_linha
FROM agregados AS a
CROSS APPLY (
    SELECT CASE a.status_raw
        WHEN N'closed' THEN N'encerrado'
        WHEN N'in_transit' THEN N'em trânsito'
        WHEN N'pending' THEN N'pendente'
        ELSE a.status_raw
    END AS status
) status_traduzido
CROSS APPLY (
    SELECT CASE a.mdfe_status_raw
        WHEN N'pending' THEN N'pendente'
        WHEN N'closed' THEN N'encerrado'
        WHEN N'issued' THEN N'emitido'
        WHEN N'rejected' THEN N'rejeitado'
        ELSE a.mdfe_status_raw
    END AS mdfe_status
) mdfe_status
CROSS APPLY (
    SELECT CASE a.contract_type_vehicle_raw
        WHEN N'aggregate' THEN N'Agregado'
        WHEN N'driver' THEN N'Motorista'
        ELSE a.contract_type_vehicle_raw
    END AS tipo_contrato_veiculo
) tipo_contrato_veiculo
CROSS APPLY (
    SELECT CASE a.contract_type_driver_raw
        WHEN N'company' THEN N'Próprio'
        WHEN N'aggregate' THEN N'Agregado'
        WHEN N'third_party' THEN N'Terceiro'
        ELSE a.contract_type_driver_raw
    END AS tipo_contrato_motorista
) tipo_contrato_motorista
CROSS APPLY (
    SELECT CASE
        WHEN tipo_contrato_veiculo.tipo_contrato_veiculo = N'Agregado'
         AND a.proprietario_nome COLLATE Latin1_General_CI_AI
             LIKE N'%LM TRANSPORTES INTERESTADUAIS SERVICOS E COMERCIO S.A%'
            THEN N'Frota + PX'
        WHEN tipo_contrato_veiculo.tipo_contrato_veiculo = N'Agregado'
         AND (
                tipo_contrato_motorista.tipo_contrato_motorista IS NULL
             OR LTRIM(RTRIM(tipo_contrato_motorista.tipo_contrato_motorista)) = N''
             )
            THEN N'Terceiro'
        WHEN tipo_contrato_veiculo.tipo_contrato_veiculo = N'Motorista'
         AND (
                tipo_contrato_motorista.tipo_contrato_motorista IS NULL
             OR LTRIM(RTRIM(tipo_contrato_motorista.tipo_contrato_motorista)) = N''
             )
            THEN N'Frota + PX'
        WHEN tipo_contrato_veiculo.tipo_contrato_veiculo = N'Agregado'
         AND tipo_contrato_motorista.tipo_contrato_motorista = N'Agregado'
            THEN N'Agregado'
        WHEN tipo_contrato_veiculo.tipo_contrato_veiculo = N'Agregado'
         AND tipo_contrato_motorista.tipo_contrato_motorista = N'Terceiro'
            THEN N'Terceiro'
        WHEN tipo_contrato_veiculo.tipo_contrato_veiculo = N'Agregado'
         AND tipo_contrato_motorista.tipo_contrato_motorista = N'Próprio'
            THEN N'Frota'
        WHEN tipo_contrato_veiculo.tipo_contrato_veiculo = N'Motorista'
         AND tipo_contrato_motorista.tipo_contrato_motorista = N'Agregado'
            THEN N'Frota + PX'
        WHEN tipo_contrato_veiculo.tipo_contrato_veiculo = N'Motorista'
         AND tipo_contrato_motorista.tipo_contrato_motorista = N'Terceiro'
            THEN N'Frota + PX'
        WHEN tipo_contrato_veiculo.tipo_contrato_veiculo = N'Motorista'
         AND tipo_contrato_motorista.tipo_contrato_motorista = N'Próprio'
            THEN N'Frota'
        ELSE NULL
    END AS tipo_contrato
) tipo_contrato
CROSS APPLY (
    SELECT CASE a.calculation_type_raw
        WHEN N'price_table' THEN N'tabela de preço'
        WHEN N'agreed' THEN N'acordado'
        ELSE a.calculation_type_raw
    END AS tipo_calculo
) tipo_calculo
CROSS APPLY (
    SELECT CASE a.cargo_type_raw
        WHEN N'fractioned' THEN N'carga fracionada'
        WHEN N'closed' THEN N'carga fechada'
        ELSE a.cargo_type_raw
    END AS tipo_carga
) tipo_carga
        )
SELECT
       CONVERT(bigint,o.[sequence_code]) AS [sequence_code],
       CONVERT(bigint,o.[numero_manifesto]) AS [numero_manifesto],
       CONVERT(nvarchar(100),o.[identificador_unico]) AS [identificador_unico],
       CONVERT(datetimeoffset(7),o.[data_criacao]) AS [data_criacao],
       CONVERT(date,o.[data_criacao_date]) AS [data_criacao_date],
       CONVERT(int,o.[data_criacao_yyyymm]) AS [data_criacao_yyyymm],
       CONVERT(time(0),o.[hora_solicitacao]) AS [hora_solicitacao],
       CONVERT(time(0),o.[hora_criacao]) AS [hora_criacao],
       CONVERT(datetimeoffset(7),o.[saida]) AS [saida],
       CONVERT(datetimeoffset(7),o.[fechamento]) AS [fechamento],
       CONVERT(datetimeoffset(7),o.[chegada]) AS [chegada],
       CONVERT(nvarchar(100),o.[status]) AS [status],
       CONVERT(nvarchar(50),o.[status_raw]) AS [status_raw],
       CONVERT(nvarchar(100),o.[status_key]) AS [status_key],
       CONVERT(nvarchar(255),o.[classificacao]) AS [classificacao],
       CONVERT(nvarchar(50),o.[classificacao_bucket]) AS [classificacao_bucket],
       CONVERT(nvarchar(255),o.[filial]) AS [filial],
       CONVERT(nvarchar(255),o.[filial_emissora]) AS [filial_emissora],
       CONVERT(nvarchar(255),o.[filial_key]) AS [filial_key],
       CONVERT(int,o.[mdfe_number]) AS [mdfe_number],
       CONVERT(nvarchar(100),o.[mdfe_key]) AS [mdfe_key],
       CONVERT(nvarchar(100),o.[mdfe_status]) AS [mdfe_status],
       CONVERT(nvarchar(255),o.[distribution_pole]) AS [distribution_pole],
       CONVERT(nvarchar(20),o.[placa_veiculo]) AS [placa_veiculo],
       CONVERT(nvarchar(20),o.[placa_veiculo_key]) AS [placa_veiculo_key],
       CONVERT(nvarchar(255),o.[tipo_veiculo]) AS [tipo_veiculo],
       CONVERT(nvarchar(255),o.[proprietario_nome]) AS [proprietario_nome],
       CONVERT(nvarchar(255),o.[proprietario_documento]) AS [proprietario_documento],
       CONVERT(nvarchar(100),o.[tipo_motorista]) AS [tipo_motorista],
       CONVERT(nvarchar(100),o.[tipo_motorista_key]) AS [tipo_motorista_key],
       CONVERT(nvarchar(255),o.[motorista]) AS [motorista],
       CONVERT(nvarchar(255),o.[motorista_key]) AS [motorista_key],
       CONVERT(int,o.[km_saida]) AS [km_saida],
       CONVERT(int,o.[km_chegada]) AS [km_chegada],
       CONVERT(int,o.[km_viagem]) AS [km_viagem],
       CONVERT(nvarchar(30),o.[km_manual]) AS [km_manual],
       CONVERT(decimal(18,2),o.[km_total]) AS [km_total],
       CONVERT(int,o.[qtd_nf]) AS [qtd_nf],
       CONVERT(int,o.[volumes_nf]) AS [volumes_nf],
       CONVERT(decimal(18,3),o.[peso_nf]) AS [peso_nf],
       CONVERT(decimal(18,3),o.[peso_taxado]) AS [peso_taxado],
       CONVERT(decimal(18,6),o.[total_m3]) AS [total_m3],
       CONVERT(decimal(18,2),o.[valor_nf]) AS [valor_nf],
       CONVERT(decimal(18,2),o.[fretes_total]) AS [fretes_total],
       CONVERT(decimal(18,2),o.[coletas_total]) AS [coletas_total],
       CONVERT(decimal(18,2),o.[receita_total]) AS [receita_total],
       CONVERT(nvarchar(max),o.[coleta_numeros]) AS [coleta_numeros],
       CONVERT(nvarchar(50),o.[contract_number]) AS [contract_number],
       CONVERT(nvarchar(100),o.[tipo_contrato_veiculo]) AS [tipo_contrato_veiculo],
       CONVERT(nvarchar(100),o.[tipo_contrato_veiculo_key]) AS [tipo_contrato_veiculo_key],
       CONVERT(nvarchar(100),o.[tipo_contrato_motorista]) AS [tipo_contrato_motorista],
       CONVERT(nvarchar(100),o.[tipo_contrato_motorista_key]) AS [tipo_contrato_motorista_key],
       CONVERT(nvarchar(100),o.[tipo_contrato]) AS [tipo_contrato],
       CONVERT(nvarchar(100),o.[tipo_contrato_key]) AS [tipo_contrato_key],
       CONVERT(nvarchar(100),o.[tipo_calculo]) AS [tipo_calculo],
       CONVERT(nvarchar(100),o.[tipo_carga]) AS [tipo_carga],
       CONVERT(nvarchar(100),o.[tipo_carga_key]) AS [tipo_carga_key],
       CONVERT(decimal(18,2),o.[diaria]) AS [diaria],
       CONVERT(decimal(18,2),o.[custo_total]) AS [custo_total],
       CONVERT(decimal(18,2),o.[valor_frete]) AS [valor_frete],
       CONVERT(decimal(18,2),o.[combustivel]) AS [combustivel],
       CONVERT(decimal(18,2),o.[pedagio]) AS [pedagio],
       CONVERT(decimal(18,2),o.[servicos_motorista_total]) AS [servicos_motorista_total],
       CONVERT(decimal(18,2),o.[despesa_operacional]) AS [despesa_operacional],
       CONVERT(decimal(18,2),o.[inss_value]) AS [inss_value],
       CONVERT(decimal(18,2),o.[sest_senat_value]) AS [sest_senat_value],
       CONVERT(decimal(18,2),o.[ir_value]) AS [ir_value],
       CONVERT(decimal(18,2),o.[saldo_pagar]) AS [saldo_pagar],
       CONVERT(int,o.[uniq_destinations_count]) AS [uniq_destinations_count],
       CONVERT(bit,o.[gerar_mdfe]) AS [gerar_mdfe],
       CONVERT(bit,o.[solicitou_monitoramento]) AS [solicitou_monitoramento],
       CONVERT(nvarchar(10),o.[solicitacao_monitoramento]) AS [solicitacao_monitoramento],
       CONVERT(datetimeoffset(7),o.[leitura_movel_em]) AS [leitura_movel_em],
       CONVERT(int,o.[itens_entrega]) AS [itens_entrega],
       CONVERT(int,o.[itens_transferencia]) AS [itens_transferencia],
       CONVERT(int,o.[itens_coleta]) AS [itens_coleta],
       CONVERT(int,o.[itens_despacho_rascunho]) AS [itens_despacho_rascunho],
       CONVERT(int,o.[itens_consolidacao]) AS [itens_consolidacao],
       CONVERT(int,o.[itens_coleta_reversa]) AS [itens_coleta_reversa],
       CONVERT(int,o.[itens_total]) AS [itens_total],
       CONVERT(int,o.[itens_finalizados]) AS [itens_finalizados],
       CONVERT(int,o.[calculado_coleta]) AS [calculado_coleta],
       CONVERT(int,o.[calculado_entrega]) AS [calculado_entrega],
       CONVERT(int,o.[calculado_despacho]) AS [calculado_despacho],
       CONVERT(int,o.[calculado_consolidacao]) AS [calculado_consolidacao],
       CONVERT(int,o.[calculado_coleta_reversa]) AS [calculado_coleta_reversa],
       CONVERT(decimal(18,2),o.[valor_coletas]) AS [valor_coletas],
       CONVERT(decimal(18,2),o.[valor_entregas]) AS [valor_entregas],
       CONVERT(decimal(18,2),o.[despachos]) AS [despachos],
       CONVERT(decimal(18,2),o.[consolidacoes]) AS [consolidacoes],
       CONVERT(decimal(18,2),o.[coleta_reversa]) AS [coleta_reversa],
       CONVERT(decimal(18,2),o.[adiantamento]) AS [adiantamento],
       CONVERT(decimal(18,2),o.[custos_frota]) AS [custos_frota],
       CONVERT(decimal(18,2),o.[adicionais]) AS [adicionais],
       CONVERT(decimal(18,2),o.[descontos]) AS [descontos],
       CONVERT(decimal(18,2),o.[desconto_valor]) AS [desconto_valor],
       CONVERT(nvarchar(max),o.[ajuste_comentarios]) AS [ajuste_comentarios],
       CONVERT(nvarchar(100),o.[iks_id]) AS [iks_id],
       CONVERT(nvarchar(50),o.[programacao_sequence_code]) AS [programacao_sequence_code],
       CONVERT(datetimeoffset(7),o.[programacao_inicio]) AS [programacao_inicio],
       CONVERT(datetimeoffset(7),o.[programacao_termino]) AS [programacao_termino],
       CONVERT(nvarchar(20),o.[carreta1_placa]) AS [carreta1_placa],
       CONVERT(decimal(18,2),o.[carreta1_capacidade_peso]) AS [carreta1_capacidade_peso],
       CONVERT(nvarchar(20),o.[carreta2_placa]) AS [carreta2_placa],
       CONVERT(decimal(18,2),o.[carreta2_capacidade_peso]) AS [carreta2_capacidade_peso],
       CONVERT(decimal(18,2),o.[veiculo_capacidade_peso]) AS [veiculo_capacidade_peso],
       CONVERT(decimal(18,2),o.[veiculo_peso_cubado]) AS [veiculo_peso_cubado],
       CONVERT(decimal(18,2),o.[capacidade_lotacao_kg]) AS [capacidade_lotacao_kg],
       CONVERT(nvarchar(max),o.[descarregamento_destinatarios]) AS [descarregamento_destinatarios],
       CONVERT(nvarchar(max),o.[local_descarregamento]) AS [local_descarregamento],
       CONVERT(nvarchar(max),o.[entrega_regioes]) AS [entrega_regioes],
       CONVERT(nvarchar(255),o.[programacao_cliente]) AS [programacao_cliente],
       CONVERT(nvarchar(255),o.[programacao_tipo_servico]) AS [programacao_tipo_servico],
       CONVERT(nvarchar(255),o.[usuario_emissor]) AS [usuario_emissor],
       CONVERT(nvarchar(255),o.[usuario_ajuste]) AS [usuario_ajuste],
       CONVERT(nvarchar(max),o.[obs_operacional]) AS [obs_operacional],
       CONVERT(nvarchar(max),o.[obs_financeira]) AS [obs_financeira],
       CONVERT(nvarchar(max),o.[metadata]) AS [metadata],
       CONVERT(datetime2(0),o.[data_extracao]) AS [data_extracao],
       CONVERT(bit,o.[excluido_na_origem]) AS [excluido_na_origem],
       CONVERT(datetime2(0),o.[data_exclusao_origem]) AS [data_exclusao_origem],
       CONVERT(datetime2(0),o.[ultima_reconciliacao_origem_em]) AS [ultima_reconciliacao_origem_em],
       CONVERT(datetime2(0),o.[snapshot_em]) AS [snapshot_em],
       CONVERT(binary,o.[hash_linha]) AS [hash_linha]
FROM origem o;
