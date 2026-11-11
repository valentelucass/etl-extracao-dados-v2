-- Use the V1 identity: sinistro, nota and minuta, in that order.
CREATE OR ALTER VIEW pub.vw_sinistros_powerbi
AS
WITH source_rows AS (
    SELECT j.*, JSON_QUERY(l.source_envelope, '$.record') AS raw_record,
           CONVERT(datetime2(7), TODATETIMEOFFSET(l.stored_utc, '+00:00')
               AT TIME ZONE 'E. South America Standard Time') AS received_at
    FROM stg.production_export_latest AS l
    CROSS APPLY OPENJSON(l.source_envelope, '$.record') WITH (
        claim_number bigint '$.sequence_code',
        opened date '$.opening_at_date',
        occurred date '$.occurrence_at_date',
        occurrence_clock nvarchar(max) '$.occurrence_at_time',
        expected_solution date '$.expected_solution_date',
        claim_location nvarchar(max) '$.insurance_claim_location',
        informed_by nvarchar(max) '$.informed_by',
        closed date '$.finished_at_date',
        closing_clock nvarchar(max) '$.finished_at_time',
        invoice_count int '$.invoices_count',
        freight_number bigint '$.icm_fis_fit_corporation_sequence_number',
        invoice_number bigint '$.icm_fis_ioe_number',
        volumes int '$.invoices_volumes',
        weight decimal(18,3) '$.invoices_weight',
        invoice_value decimal(18,2) '$.invoices_value',
        payer nvarchar(max) '$.icm_fis_fit_pyr_nickname',
        customer_debit decimal(18,2) '$.customer_debits_subtotal',
        customer_credit decimal(18,2) '$.customer_credit_entries_subtotal',
        responsible_credit decimal(18,2) '$.responsible_credits_subtotal',
        responsible_debit decimal(18,2) '$.responsible_debit_entries_subtotal',
        insurer_credit decimal(18,2) '$.insurer_credits_subtotal',
        total decimal(18,2) '$.insurance_claim_total',
        branch_name nvarchar(max) '$.icm_crn_psn_nickname',
        event_name nvarchar(max) '$.icm_dvr_iil_name',
        user_name nvarchar(max) '$.icm_fer_name',
        plate nvarchar(max) '$.icm_vie_license_plate',
        occurrence_description nvarchar(max) '$.icm_ttt_ore_description',
        occurrence_code nvarchar(max) '$.icm_ttt_ore_code',
        treatment_at datetimeoffset(7) '$.icm_ttt_treatment_at',
        treatment_type nvarchar(max) '$.icm_ttt_dealing_type',
        solution_type nvarchar(max) '$.icm_ttt_solution_type'
    ) AS j
    WHERE l.entity_name='SINISTROS'
      AND (SELECT COUNT_BIG(DISTINCT source_scope)
           FROM ctl.production_ingestion_coverage WHERE entity_name='SINISTROS')=1
), clock_prefixes AS (
    SELECT *,
           CASE WHEN TRIM(occurrence_clock) LIKE '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9][ T][0-9][0-9]:[0-9][0-9]%'
                THEN SUBSTRING(TRIM(occurrence_clock),12,8) ELSE TRIM(occurrence_clock) END AS occurrence_prefix,
           CASE WHEN TRIM(closing_clock) LIKE '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9][ T][0-9][0-9]:[0-9][0-9]%'
                THEN SUBSTRING(TRIM(closing_clock),12,8) ELSE TRIM(closing_clock) END AS closing_prefix
    FROM source_rows
)
SELECT CONVERT(nvarchar(64), LOWER(CONVERT(char(64), HASHBYTES('SHA2_256', CONCAT(
           CONVERT(varchar(20), claim_number), '|',
           COALESCE(CONVERT(varchar(20), invoice_number),'null'), '|',
           COALESCE(CONVERT(varchar(20), freight_number),'null'))), 2))) AS [Identificador Único],
       claim_number AS [Nº do Sinistro],
       opened AS [Data abertura],
       occurred AS [Data ocorrência],
       CONVERT(nvarchar(20), CASE
           WHEN occurrence_prefix LIKE '[0-9][0-9]:[0-9][0-9]:[0-9][0-9]%' THEN LEFT(occurrence_prefix,8)
           WHEN occurrence_prefix LIKE '[0-9][0-9]:[0-9][0-9]%' THEN LEFT(occurrence_prefix,5)
           WHEN occurrence_prefix NOT IN (N'',N'NULL') AND LEN(occurrence_prefix)<=10
             AND occurrence_prefix NOT LIKE '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]' THEN occurrence_prefix END) AS [Hora ocorrência],
       expected_solution AS [Previsão de solução],
       CONVERT(nvarchar(255),claim_location) AS [Local do sinistro],
       CONVERT(nvarchar(255),informed_by) AS [Informado por],
       closed AS [Data finalização],
       CONVERT(nvarchar(20), CASE
           WHEN closing_prefix LIKE '[0-9][0-9]:[0-9][0-9]:[0-9][0-9]%' THEN LEFT(closing_prefix,8)
           WHEN closing_prefix LIKE '[0-9][0-9]:[0-9][0-9]%' THEN LEFT(closing_prefix,5)
           WHEN closing_prefix NOT IN (N'',N'NULL') AND LEN(closing_prefix)<=10
             AND closing_prefix NOT LIKE '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]' THEN closing_prefix END) AS [Hora finalização],
       invoice_count AS [Quantidade de documentos],
       freight_number AS [Minuta],
       invoice_number AS [Nota Fiscal/Número],
       volumes AS [Volumes],
       weight AS [Peso],
       invoice_value AS [Valor NF],
       CONVERT(nvarchar(255),payer) AS [Pagador do frete/Nome fantasia],
       customer_debit AS [valor a pagar ao cliente],
       customer_credit AS [Valor nota de crédito ao cliente],
       responsible_credit AS [Valor a receber do responsável],
       responsible_debit AS [Valor nota de débito do responsável],
       insurer_credit AS [Valor a receber da seguradora],
       total AS [Resultado final],
       CONVERT(nvarchar(255),branch_name) AS [Pessoa/Nome fantasia],
       CONVERT(nvarchar(255),event_name) AS [Ocorrência/Ocorrência],
       CONVERT(nvarchar(255),user_name) AS [Usuário/Nome],
       CONVERT(nvarchar(20),plate) AS [Veículo/Placa],
       CONVERT(nvarchar(500),occurrence_description) AS [Ocorrência/Descrição],
       CONVERT(nvarchar(100),occurrence_code) AS [Ocorrência/Código],
       treatment_at AS [Tratativa/Data tratativa],
       CONVERT(nvarchar(100),CASE treatment_type
           WHEN 'send_to_damages_dealing' THEN N'enviado para tratativa de avarias'
           WHEN 'send_to_incidents_dealing' THEN N'enviado para tratativa de incidentes'
           ELSE treatment_type END) AS [Tratativa/Tipo tratativa],
       CONVERT(nvarchar(100),CASE solution_type
           WHEN 'redelivery_without_freight_charge' THEN N'redespacho sem cobrança de frete'
           ELSE solution_type END) AS [Tratativa/Solução],
       raw_record AS [Metadata],
       received_at AS [Data de extracao]
FROM clock_prefixes
WHERE claim_number IS NOT NULL;
