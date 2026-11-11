-- The Data Export has no status branch. The V1 column is null for this template;
-- the dashboard derives its operational branch from location and issuing branch.
CREATE OR ALTER VIEW pub.vw_localizacao_cargas_powerbi
AS
WITH locations AS (
    SELECT j.*, JSON_QUERY(l.source_envelope,'$.record') AS record_json,
           CONVERT(datetime2(7), TODATETIMEOFFSET(l.stored_utc,'+00:00')
                AT TIME ZONE 'E. South America Standard Time') AS received_at,
           COALESCE(NULLIF(LOWER(TRIM(j.status_raw)),N''),N'sem_status') AS status_normalized
    FROM stg.production_export_latest l
    CROSS APPLY OPENJSON(l.source_envelope,'$.record') WITH (
        freight_number bigint '$.corporation_sequence_number',
        freight_type nvarchar(max) '$.type',
        service_at datetimeoffset(7) '$.service_at',
        volumes int '$.invoices_volumes',
        taxed_weight nvarchar(max) '$.taxed_weight',
        invoice_value nvarchar(max) '$.invoices_value',
        total decimal(18,2) '$.total',
        service_type nvarchar(max) '$.service_type',
        issuing_branch nvarchar(max) '$.fit_crn_psn_nickname',
        delivery_forecast datetimeoffset(7) '$.fit_dpn_delivery_prediction_at',
        destination_region nvarchar(max) '$.fit_dyn_name',
        destination_branch nvarchar(max) '$.fit_dyn_drt_nickname',
        classification nvarchar(max) '$.fit_fsn_name',
        status_raw nvarchar(max) '$.fit_fln_status',
        origin_region nvarchar(max) '$.fit_o_n_name',
        origin_branch nvarchar(max) '$.fit_o_n_drt_nickname',
        current_location nvarchar(max) '$.fit_fln_cln_nickname'
    ) j
    WHERE l.entity_name='LOCALIZACAO_CARGAS'
      AND (SELECT COUNT_BIG(DISTINCT source_scope)
           FROM ctl.production_ingestion_coverage WHERE entity_name='LOCALIZACAO_CARGAS')=1
), destination_aliases AS (
    SELECT code, CONCAT(code,N' - RODOGARCIA TRANSPORTES RODOVIARIOS LTDA') AS responsible
    FROM (VALUES (N'AGU'),(N'CAS'),(N'CPQ'),(N'CWB'),(N'NHB'),(N'REC'),(N'RJR'),(N'SPO')) alias_codes(code)
)
SELECT CONVERT(time(0),x.service_at) AS [Hora (Solicitacao)],
       x.freight_number AS [N° Minuta],
       CONVERT(nvarchar(4000),REPLACE(x.freight_type,N'Freight::',N'')) AS [Tipo],
       x.service_at AS [Data do frete],
       x.volumes AS [Volumes],
       CONVERT(nvarchar(50),x.taxed_weight) AS [Peso Taxado],
       TRY_CONVERT(decimal(18,3),NULLIF(TRIM(REPLACE(x.taxed_weight,N',',N'')),N'')) AS [Peso Taxado Decimal],
       CONVERT(nvarchar(50),x.invoice_value) AS [Valor NF],
       TRY_CONVERT(decimal(18,2),NULLIF(TRIM(REPLACE(x.invoice_value,N',',N'')),N'')) AS [Valor NF Decimal],
       x.total AS [Valor Frete],
       CONVERT(nvarchar(50),x.service_type) AS [Tipo Serviço],
       CONVERT(nvarchar(255),x.issuing_branch) AS [Filial Emissora],
       x.delivery_forecast AS [Previsão Entrega/Previsão de entrega],
       CONVERT(nvarchar(255),x.destination_region) AS [Região Destino],
       CONVERT(nvarchar(255),x.destination_branch) AS [Filial Destino],
       CONVERT(nvarchar(255),x.destination_branch) AS [Responsável pela Região de Destino],
       CONVERT(nvarchar(32),COALESCE(a.code,N'SEM_MAP')) AS [Sigla Responsável Região Destino],
       CONVERT(nvarchar(255),x.classification) AS [Classificação],
       CONVERT(nvarchar(50),CASE x.status_raw
           WHEN N'pending' THEN N'Pendente' WHEN N'delivering' THEN N'Em entrega'
           WHEN N'in_warehouse' THEN N'Em armazém' WHEN N'in_transfer' THEN N'Em transferência'
           WHEN N'manifested' THEN N'Manifestado' WHEN N'finished' THEN N'Finalizado'
           WHEN N'delivered' THEN N'Entregue' WHEN N'canceled' THEN N'Cancelado'
           WHEN N'cancelled' THEN N'Cancelado' ELSE x.status_raw END) AS [Status Carga],
       CONVERT(nvarchar(50),x.status_normalized) AS [Status Normalizado],
       CASE WHEN x.status_normalized IN (N'finished',N'delivered',N'canceled',N'cancelled',N'finalizado',N'entregue',N'cancelado')
           THEN 1 ELSE 0 END AS [Status Terminal],
       CASE WHEN x.status_normalized IN (N'canceled',N'cancelled',N'cancelado') THEN 1 ELSE 0 END AS [Cancelado Flag],
       CONVERT(nvarchar(255),NULL) AS [Filial Atual],
       CONVERT(nvarchar(255),x.origin_region) AS [Região Origem],
       CONVERT(nvarchar(255),x.origin_branch) AS [Filial Origem],
       CONVERT(nvarchar(255),x.current_location) AS [Localização Atual],
       CONVERT(char(64),LOWER(CONVERT(char(64),HASHBYTES('SHA2_256',CONVERT(varbinary(max),x.record_json)),2))) AS [Hash Localização],
       x.record_json AS [Metadata],
       x.received_at AS [Data de extracao]
FROM locations x
LEFT JOIN destination_aliases a ON a.responsible COLLATE Latin1_General_CI_AS=x.destination_branch COLLATE Latin1_General_CI_AS;
