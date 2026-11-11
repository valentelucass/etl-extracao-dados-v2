-- Compatibility projection for the existing dashboard. Counts are per occurrence in
-- the last complete daily report; sequence_code is a quote number, not a row key.
CREATE OR ALTER VIEW pub.vw_cotacoes_powerbi
AS
WITH source_guard AS (
    SELECT COUNT_BIG(DISTINCT source_scope) AS sources
    FROM ctl.production_ingestion_coverage
    WHERE entity_name = 'COTACOES'
), quotes AS (
    SELECT
        CONVERT(bigint, j.sequence_code) AS numero,
        CONVERT(datetimeoffset(7), j.requested_at) AS solicitada,
        CONVERT(datetime2(7), TODATETIMEOFFSET(l.stored_utc, '+00:00')
            AT TIME ZONE 'E. South America Standard Time') AS recebida,
        CONVERT(nvarchar(255), j.branch) AS filial,
        CONVERT(nvarchar(255), j.requester) AS solicitante,
        CONVERT(nvarchar(255), j.user_name) AS usuario,
        CONVERT(nvarchar(255), j.company_name) AS empresa,
        CONVERT(nvarchar(255), j.customer_name) AS pagador,
        CONVERT(nvarchar(14), j.customer_document) AS pagador_documento,
        CONVERT(nvarchar(255), j.customer_nickname) AS pagador_fantasia,
        CONVERT(nvarchar(100), j.origin_city) AS origem_cidade,
        CONVERT(nvarchar(2), j.origin_uf) AS origem_uf,
        CONVERT(nvarchar(10), j.origin_postal) AS origem_cep,
        CONVERT(nvarchar(100), j.destination_city) AS destino_cidade,
        CONVERT(nvarchar(2), j.destination_uf) AS destino_uf,
        CONVERT(nvarchar(10), j.destination_postal) AS destino_cep,
        CONVERT(int, j.volumes) AS volumes,
        CONVERT(nvarchar(50), j.real_weight) AS peso_real,
        CONVERT(decimal(18,3), j.taxed_weight) AS peso_taxado,
        CONVERT(decimal(18,2), j.invoices_value) AS valor_nf,
        CONVERT(decimal(18,2), j.freight_total) AS valor_frete,
        CONVERT(nvarchar(255), j.price_table) AS tabela,
        CONVERT(nvarchar(100), j.operation_type) AS operacao,
        JSON_QUERY(l.source_envelope, '$.record') AS metadata,
        j.disapprove_comments AS motivo_perda,
        j.freight_comments AS observacoes,
        CONVERT(datetimeoffset(7), j.cte_issued_at) AS cte_emissao,
        CASE WHEN RIGHT(j.nfse_issued_at, 1) = 'Z'
               OR RIGHT(j.nfse_issued_at, 6) LIKE '[+-][0-9][0-9]:[0-9][0-9]'
             THEN CONVERT(datetimeoffset(7), j.nfse_issued_at)
             ELSE CONVERT(datetime2(7), j.nfse_issued_at, 126) AT TIME ZONE 'E. South America Standard Time'
        END AS nfse_emissao,
        CONVERT(nvarchar(14), j.sender_document) AS remetente_documento,
        CONVERT(nvarchar(255), j.sender_nickname) AS remetente_fantasia,
        CONVERT(nvarchar(14), j.receiver_document) AS destinatario_documento,
        CONVERT(nvarchar(255), j.receiver_nickname) AS destinatario_fantasia,
        CONVERT(decimal(18,6), j.discount_subtotal) AS descontos,
        CONVERT(decimal(18,6), j.itr_subtotal) AS itr,
        CONVERT(decimal(18,6), j.tde_subtotal) AS tde,
        CONVERT(decimal(18,6), j.collect_subtotal) AS coleta,
        CONVERT(decimal(18,6), j.delivery_subtotal) AS entrega,
        CONVERT(decimal(18,6), j.other_fees) AS outros
    FROM stg.production_export_latest l
    CROSS JOIN source_guard g
    CROSS APPLY OPENJSON(l.source_envelope, '$.record') WITH (
        sequence_code nvarchar(max) '$.sequence_code',
        requested_at nvarchar(max) '$.requested_at',
        branch nvarchar(max) '$.qoe_crn_psn_nickname',
        requester nvarchar(max) '$.requester_name',
        user_name nvarchar(max) '$.qoe_uer_name',
        company_name nvarchar(max) '$.qoe_crn_psn_name',
        customer_name nvarchar(max) '$.qoe_cor_name',
        customer_document nvarchar(max) '$.qoe_cor_document',
        customer_nickname nvarchar(max) '$.qoe_cor_nickname',
        origin_city nvarchar(max) '$.qoe_qes_ony_name',
        origin_uf nvarchar(max) '$.qoe_qes_ony_sae_code',
        origin_postal nvarchar(max) '$.qoe_qes_origin_postal_code',
        destination_city nvarchar(max) '$.qoe_qes_diy_name',
        destination_uf nvarchar(max) '$.qoe_qes_diy_sae_code',
        destination_postal nvarchar(max) '$.qoe_qes_destination_postal_code',
        volumes nvarchar(max) '$.qoe_qes_invoices_volumes',
        real_weight nvarchar(max) '$.qoe_qes_real_weight',
        taxed_weight nvarchar(max) '$.qoe_qes_taxed_weight',
        invoices_value nvarchar(max) '$.qoe_qes_invoices_value',
        freight_total nvarchar(max) '$.qoe_qes_total',
        price_table nvarchar(max) '$.qoe_qes_cre_name',
        operation_type nvarchar(max) '$.qoe_qes_fon_name',
        disapprove_comments nvarchar(max) '$.qoe_qes_disapprove_comments',
        freight_comments nvarchar(max) '$.qoe_qes_freight_comments',
        cte_issued_at nvarchar(max) '$.qoe_qes_fit_fhe_cte_issued_at',
        nfse_issued_at nvarchar(max) '$.qoe_qes_fit_nse_issued_at',
        sender_document nvarchar(max) '$.qoe_qes_sdr_document',
        sender_nickname nvarchar(max) '$.qoe_qes_sdr_nickname',
        receiver_document nvarchar(max) '$.qoe_qes_rpt_document',
        receiver_nickname nvarchar(max) '$.qoe_qes_rpt_nickname',
        discount_subtotal nvarchar(max) '$.qoe_qes_fit_fdt_subtotal',
        itr_subtotal nvarchar(max) '$.qoe_qes_itr_subtotal',
        tde_subtotal nvarchar(max) '$.qoe_qes_tde_subtotal',
        collect_subtotal nvarchar(max) '$.qoe_qes_collect_subtotal',
        delivery_subtotal nvarchar(max) '$.qoe_qes_delivery_subtotal',
        other_fees nvarchar(max) '$.qoe_qes_other_fees'
    ) j
    WHERE l.entity_name = 'COTACOES' AND g.sources = 1
), tariffs AS (
    -- Matrix from the installed V1 view, including its asymmetric routes.
    SELECT origin, destination, amount
    FROM (VALUES
        ('SP','SP',0.66), ('SP','RJ',1.21), ('SP','PR',0.74), ('SP','RS',1.70), ('SP','PE',1.54),
        ('RJ','SP',1.21), ('RJ','RJ',0.66), ('RJ','PR',0.98), ('RJ','RS',2.08), ('RJ','PE',1.95),
        ('PR','SP',0.74), ('PR','RJ',0.88), ('PR','PR',0.55), ('PR','RS',0.98), ('PR','PE',1.83),
        ('RS','SP',1.70), ('RS','RJ',1.95), ('RS','PR',1.46), ('RS','RS',0.98), ('RS','PE',2.20),
        ('PE','SP',1.46), ('PE','RJ',2.20), ('PE','PR',2.20), ('PE','RS',2.32), ('PE','PE',0.55)
    ) v(origin, destination, amount)
)
SELECT
    q.numero AS [N° Cotação],
    q.solicitada AS [Data Cotação],
    CONVERT(time(0), q.solicitada) AS [Hora (Solicitacao)],
    q.recebida AS [Data de extracao],
    q.filial AS [Filial],
    q.filial AS [Unidade],
    q.filial AS [Unidade_Origem],
    q.solicitante AS [Solicitante],
    q.usuario AS [Usuário],
    NULLIF(LOWER(TRIM(q.usuario)), N'') AS [Usuario Key],
    q.usuario AS [Setor_Usuario],
    q.empresa AS [Empresa],
    q.pagador AS [Cliente Pagador],
    q.pagador_documento AS [CNPJ/CPF Cliente],
    q.pagador_fantasia AS [Pagador/Nome fantasia],
    q.pagador AS [Cliente Grupo],
    COALESCE(q.pagador_fantasia, q.pagador) AS [Cliente],
    q.origem_cidade AS [Cidade Origem],
    q.origem_uf AS [UF Origem],
    q.origem_cep AS [CEP Origem],
    CONVERT(nvarchar(105), CONCAT(q.origem_cidade, N' - ', q.origem_uf)) AS [Origem],
    q.destino_cidade AS [Cidade Destino],
    q.destino_uf AS [UF Destino],
    q.destino_cep AS [CEP Destino],
    CONVERT(nvarchar(105), CONCAT(q.destino_cidade, N' - ', q.destino_uf)) AS [Destino],
    CONVERT(nvarchar(213), CONCAT(q.origem_cidade, N' - ', q.origem_uf, N' x ', q.destino_cidade, N' - ', q.destino_uf)) AS [Trecho],
    q.volumes AS [Volume],
    q.peso_real AS [Peso real],
    q.peso_taxado AS [Peso taxado],
    q.valor_nf AS [Valor NF],
    q.valor_frete AS [Valor frete],
    CONVERT(decimal(3,2), COALESCE(t.amount, 0.00)) AS [Min. Frete/KG],
    q.tabela AS [Tabela],
    q.operacao AS [Tipo de operação],
    q.metadata AS [Metadata],
    status.conversao AS [Status Conversão],
    status.conversao AS [Status_Sistema],
    CASE WHEN q.cte_emissao IS NOT NULL THEN 'Emitido' ELSE 'Pendente' END AS [Status_Sistema_CTe],
    CASE WHEN q.nfse_emissao IS NOT NULL THEN 'Emitida' ELSE 'Pendente' END AS [Status_Sistema_NFSe],
    CASE WHEN q.cte_emissao IS NOT NULL THEN 'Sim' ELSE 'Não' END AS [Refino_CTe],
    q.motivo_perda AS [Motivo Perda],
    q.observacoes AS [Observações para o frete],
    q.cte_emissao AS [CT-e/Data de emissão],
    q.nfse_emissao AS [Nfse/Data de emissão],
    q.remetente_documento AS [Remetente/CNPJ],
    q.remetente_fantasia AS [Remetente/Nome fantasia],
    q.destinatario_documento AS [Destinatário/CNPJ],
    q.destinatario_fantasia AS [Destinatário/Nome fantasia],
    q.descontos AS [Descontos/Subtotal parcelas],
    q.itr AS [Trechos/ITR],
    q.tde AS [Trechos/TDE],
    q.coleta AS [Trechos/Coleta],
    q.entrega AS [Trechos/Entrega],
    q.outros AS [Trechos/Outros valores]
FROM quotes q
LEFT JOIN tariffs t ON t.origin = q.origem_uf AND t.destination = q.destino_uf
CROSS APPLY (VALUES (CASE WHEN q.cte_emissao IS NOT NULL OR q.nfse_emissao IS NOT NULL THEN 'Convertida'
    WHEN LEN(q.motivo_perda) > 0 THEN 'Reprovada' ELSE 'Pendente' END)) status(conversao);
