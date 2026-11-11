-- The document identity and freshness rules are the ones used by the V1 mapper.
CREATE OR ALTER VIEW core.dashboard_faturas_vigentes
AS
WITH raw_rows AS (
    SELECT j.*, l.stored_utc, l.business_date, l.occurrence_ordinal,
           JSON_QUERY(l.source_envelope,'$.record') AS record_json
    FROM stg.production_export_latest l
    CROSS APPLY OPENJSON(l.source_envelope,'$.record') WITH (
        nfse_number bigint '$.fit_nse_number',
        nfse_raw nvarchar(max) '$.nfse_number',
        cte_number bigint '$.fit_fhe_cte_number',
        cte_key nvarchar(max) '$.fit_fhe_cte_key',
        cte_issued_at datetimeoffset(7) '$.fit_fhe_cte_issued_at',
        cte_status nvarchar(max) '$.fit_fhe_cte_status',
        cte_result nvarchar(max) '$.fit_fhe_cte_status_result',
        invoice_document nvarchar(max) '$.fit_ant_document',
        invoice_issued nvarchar(max) '$.fit_ant_issue_date',
        invoice_due date '$.fit_ant_ils_due_date',
        invoice_paid date '$.fit_ant_ils_atn_transaction_date',
        invoice_original_due date '$.fit_ant_ils_original_due_date',
        billing_id nvarchar(max) '$.billingId',
        branch_name nvarchar(max) '$.fit_crn_psn_nickname',
        state_name nvarchar(max) '$.fit_diy_sae_name',
        freight_type nvarchar(max) '$.type',
        classification nvarchar(max) '$.fit_fsn_name',
        payer_name nvarchar(max) '$.fit_pyr_name',
        payer_doc nvarchar(max) '$.fit_pyr_document',
        sender_name nvarchar(max) '$.fit_rpt_name',
        sender_doc nvarchar(max) '$.fit_rpt_document',
        recipient_name nvarchar(max) '$.fit_sdr_name',
        recipient_doc nvarchar(max) '$.fit_sdr_document',
        seller_name nvarchar(max) '$.fit_sps_slr_psn_name',
        freight_raw nvarchar(max) '$.total',
        invoice_raw nvarchar(max) '$.fit_ant_value',
        third_party decimal(18,2) '$.third_party_ctes_value',
        invoices nvarchar(max) '$.invoices_mapping' AS JSON,
        orders nvarchar(max) '$.fit_fte_invoices_order_number' AS JSON
    ) j
    WHERE l.entity_name='FATURAS_POR_CLIENTE'
      AND (SELECT COUNT_BIG(DISTINCT source_scope) FROM ctl.production_ingestion_coverage
           WHERE entity_name='FATURAS_POR_CLIENTE')=1
), normalized AS (
    SELECT j.*,
           COALESCE(j.nfse_number,TRY_CONVERT(bigint,NULLIF(TRIM(j.nfse_raw),N''))) AS effective_nfse,
           TRY_CONVERT(date,NULLIF(TRIM(j.invoice_issued),N'')) AS invoice_issued_date,
           TRY_CONVERT(decimal(18,2),NULLIF(TRIM(REPLACE(j.freight_raw,N',',N'')),N'')) AS freight_amount,
           TRY_CONVERT(decimal(18,2),NULLIF(TRIM(REPLACE(j.invoice_raw,N',',N'')),N'')) AS invoice_amount,
           notes.canonical AS canonical_invoices, REPLACE(NULLIF(NULLIF(notes.canonical,N'<null>'),N'<empty>'),N',',N', ') AS notes_display,
           orders.canonical AS canonical_orders, REPLACE(NULLIF(NULLIF(orders.canonical,N'<null>'),N'<empty>'),N',',N', ') AS orders_display,
           REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(TRIM(j.payer_doc),
               N'.',N''),N'-',N''),N'/',N''),N' ',N''),NCHAR(9),N''),NCHAR(160),N'') AS payer_digits
    FROM raw_rows j
    CROSS APPLY (
        SELECT CASE WHEN NOT EXISTS (SELECT 1 FROM OPENJSON(j.invoices)) THEN N'<null>'
               ELSE COALESCE(STUFF((
                   SELECT N','+TRIM(i.value)
                   FROM OPENJSON(j.invoices) i
                   WHERE TRIM(i.value) COLLATE Latin1_General_100_BIN2 NOT IN (N'',N'<null>',N'<empty>')
                   ORDER BY TRIM(i.value) COLLATE Latin1_General_100_BIN2
                   FOR XML PATH(''),TYPE
               ).value('text()[1]','nvarchar(max)'),1,1,N''),N'<empty>') END AS canonical
    ) notes
    CROSS APPLY (
        SELECT CASE WHEN NOT EXISTS (SELECT 1 FROM OPENJSON(j.orders)) THEN N'<null>'
               ELSE COALESCE(STUFF((
                   SELECT N','+TRIM(i.value)
                   FROM OPENJSON(j.orders) i
                   WHERE TRIM(i.value) COLLATE Latin1_General_100_BIN2 NOT IN (N'',N'<null>',N'<empty>')
                   ORDER BY TRIM(i.value) COLLATE Latin1_General_100_BIN2
                   FOR XML PATH(''),TYPE
               ).value('text()[1]','nvarchar(max)'),1,1,N''),N'<empty>') END AS canonical
    ) orders
), identified AS (
    SELECT n.*,
           CONVERT(nvarchar(100), CONCAT(N'FPC-HASH-',LOWER(CONVERT(char(64),HASHBYTES('SHA2_256',
               CONVERT(varchar(max), k.identity_text COLLATE Latin1_General_100_BIN2_UTF8)),2)))) AS unique_id,
           CASE WHEN n.effective_nfse IS NULL AND (n.cte_number IS NOT NULL OR NULLIF(TRIM(n.cte_key),N'') IS NOT NULL)
                THEN n.cte_issued_at END AS effective_cte_date
    FROM normalized n
    CROSS APPLY (VALUES (
        CONCAT(N'pagadorDocumento=',CASE WHEN n.payer_doc IS NULL THEN N'<null>' ELSE COALESCE(NULLIF(TRIM(n.payer_doc),N''),N'<empty>') END,N'|'),
        CONCAT(N'remetenteDocumento=',CASE WHEN n.sender_doc IS NULL THEN N'<null>' ELSE COALESCE(NULLIF(TRIM(n.sender_doc),N''),N'<empty>') END,N'|'),
        CONCAT(N'destinatarioDocumento=',CASE WHEN n.recipient_doc IS NULL THEN N'<null>' ELSE COALESCE(NULLIF(TRIM(n.recipient_doc),N''),N'<empty>') END,N'|')
    )) docs(payer,sender,recipient)
    CROSS APPLY (VALUES (
        CASE
            WHEN n.effective_nfse IS NOT NULL THEN CONCAT(N'identitySource=nfse|nfseNumber=',n.effective_nfse,N'|',docs.payer,docs.sender,docs.recipient)
            WHEN n.cte_number IS NOT NULL THEN CONCAT(N'identitySource=cte|cteNumber=',n.cte_number,N'|',docs.payer,docs.sender,docs.recipient)
            WHEN NULLIF(TRIM(n.invoice_document),N'') IS NOT NULL THEN CONCAT(
                N'identitySource=fatura|document=',TRIM(n.invoice_document),N'|issueDate=',
                CASE WHEN n.invoice_issued IS NULL THEN N'<null>' ELSE COALESCE(NULLIF(TRIM(n.invoice_issued),N''),N'<empty>') END,N'|',docs.payer,docs.recipient)
            WHEN NULLIF(TRIM(n.billing_id),N'') IS NOT NULL THEN CONCAT(N'identitySource=billing|billingId=',TRIM(n.billing_id),N'|',docs.payer,docs.recipient)
            ELSE CONCAT(N'identitySource=fallback|',docs.payer,docs.sender,docs.recipient,
                N'notasFiscais=',n.canonical_invoices,N'|pedidosCliente=',n.canonical_orders,
                N'|valorFrete=',CASE WHEN n.freight_raw IS NULL THEN N'<null>' ELSE COALESCE(NULLIF(TRIM(n.freight_raw),N''),N'<empty>') END,
                N'|valorFatura=',CASE WHEN n.invoice_raw IS NULL THEN N'<null>' ELSE COALESCE(NULLIF(TRIM(n.invoice_raw),N''),N'<empty>') END,N'|')
        END
    )) k(identity_text)
), ranked AS (
    SELECT i.*, ROW_NUMBER() OVER (PARTITION BY i.unique_id ORDER BY
        GREATEST(
            CONVERT(datetime2(3),i.effective_cte_date),
            DATEADD(millisecond,-1,DATEADD(day,1,CONVERT(datetime2(3),i.invoice_issued_date))),
            DATEADD(millisecond,-1,DATEADD(day,1,CONVERT(datetime2(3),i.invoice_due))),
            DATEADD(millisecond,-1,DATEADD(day,1,CONVERT(datetime2(3),i.invoice_paid))),
            DATEADD(millisecond,-1,DATEADD(day,1,CONVERT(datetime2(3),i.invoice_original_due)))
        ) DESC,i.stored_utc DESC,i.business_date DESC,i.occurrence_ordinal DESC) AS row_rank
    FROM identified i
)
SELECT unique_id,
       CASE WHEN effective_nfse IS NULL THEN cte_number END AS numero_cte,
       CASE WHEN effective_nfse IS NULL THEN cte_key END AS chave_cte,
       effective_nfse AS numero_nfse,
       CONVERT(nvarchar(50),NULL) AS serie_nfse,
       effective_cte_date AS data_emissao_cte,
       CASE WHEN effective_nfse IS NULL AND (cte_number IS NOT NULL OR NULLIF(TRIM(cte_key),N'') IS NOT NULL)
            THEN CASE LOWER(TRIM(cte_status))
                WHEN N'authorized' THEN N'Autorizado' WHEN N'cancelled' THEN N'Cancelado'
                WHEN N'denied' THEN N'Negado' WHEN N'pending' THEN N'Pendente' ELSE NULLIF(cte_status,N'') END END AS status_cte,
       CASE WHEN effective_nfse IS NULL AND (cte_number IS NOT NULL OR NULLIF(TRIM(cte_key),N'') IS NOT NULL)
            THEN cte_result END AS status_cte_result,
       invoice_document AS numero_fatura,
       invoice_issued_date AS data_emissao_fatura,
       invoice_due AS data_vencimento_fatura,
       invoice_paid AS data_baixa_fatura,
       invoice_original_due AS fit_ant_ils_original_due_date,
       invoice_document AS fit_ant_document,
       invoice_issued_date AS fit_ant_issue_date,
       invoice_amount AS fit_ant_value,
       invoice_amount AS valor_fatura,
       freight_amount AS valor_frete,
       third_party AS third_party_ctes_value,
       branch_name AS filial,
       state_name AS estado,
       NULLIF(TRIM(REPLACE(freight_type,N'Freight::',N'')),N'') AS tipo_frete,
       classification AS classificacao,
       payer_name AS pagador_nome,
       payer_doc AS pagador_documento,
       CASE WHEN LEN(payer_digits)=14 AND payer_digits NOT LIKE N'%[^0-9]%' THEN payer_digits END AS cliente_cnpj,
       sender_name AS remetente_nome,
       sender_doc AS remetente_documento,
       recipient_name AS destinatario_nome,
       recipient_doc AS destinatario_documento,
       seller_name AS vendedor_nome,
       notes_display AS notas_fiscais,
       orders_display AS pedidos_cliente,
       record_json AS metadata,
       CONVERT(datetime2(7),TODATETIMEOFFSET(stored_utc,'+00:00') AT TIME ZONE 'E. South America Standard Time') AS data_extracao,
       stored_utc AS snapshot_utc
FROM ranked WHERE row_rank=1;
