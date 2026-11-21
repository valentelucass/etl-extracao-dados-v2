-- O relatório 11067 pode repetir o cabeçalho financeiro ao detalhar várias minutas.
-- Mantemos os vínculos individuais. A API soma cada cabeçalho e cada frete uma vez.
CREATE OR ALTER VIEW pub.dashboard_rede_credenciada_consolidacoes AS
SELECT l.business_date AS data_emissao,
       l.occurrence_ordinal AS ordem,
       j.numero AS consolidacao,
       NULLIF(TRIM(j.documento_parceiro), N'') AS parceiro_id,
       COALESCE(NULLIF(TRIM(j.parceiro), N''), N'Sem parceiro informado') AS parceiro,
       COALESCE(NULLIF(TRIM(j.situacao), N''), N'Sem situação informada') AS situacao,
       NULLIF(TRIM(j.filial), N'') AS filial_emissora,
       CASE WHEN TRIM(j.filial) NOT LIKE N'%| PARCEIRO%'
                  AND (LEN(TRIM(j.filial)) = 3 OR TRIM(j.filial) LIKE N'___ -%')
            THEN LOWER(LEFT(TRIM(j.filial), 3)) END AS filial_key,
       j.minuta, j.cobrado, j.calculado, j.frete_rodogarcia
FROM stg.production_export_latest AS l
CROSS APPLY OPENJSON(l.source_envelope, '$.record') WITH (
    numero bigint '$.sequence_code',
    documento_parceiro nvarchar(64) '$.ctd_rhr_cpy_document',
    parceiro nvarchar(255) '$.ctd_rhr_cpy_nickname',
    situacao nvarchar(100) '$.bill_status',
    filial nvarchar(255) '$.ctd_fgs_crn_psn_nickname',
    minuta bigint '$.ctd_fgs_corporation_sequence_number',
    cobrado decimal(19,2) '$.charged_value',
    calculado decimal(19,2) '$.subtotal',
    frete_rodogarcia decimal(19,2) '$.ctd_fgs_total'
) AS j
WHERE l.entity_name = 'CONSOLIDACAO_DETALHADA';
