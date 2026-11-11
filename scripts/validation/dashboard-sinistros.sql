SET NOCOUNT ON;

SELECT TOP(0) * INTO #actual_claims FROM pub.vw_sinistros_powerbi;
BEGIN TRY
    EXECUTE AS USER=N'usuario_etl';
    INSERT #actual_claims SELECT * FROM pub.vw_sinistros_powerbi;
    REVERT;
END TRY
BEGIN CATCH
    IF USER_NAME()=N'usuario_etl' REVERT;
    THROW;
END CATCH;

SELECT JSON_QUERY(l.source_envelope,'$.record') AS record_json, l.business_date
INTO #source_claims
FROM stg.production_export_latest l WHERE l.entity_name='SINISTROS';

DECLARE @rows bigint=(SELECT COUNT_BIG(*) FROM #source_claims);
IF @rows=0 OR (SELECT COUNT_BIG(*) FROM #actual_claims)<>@rows
    OR (SELECT COUNT_BIG(DISTINCT [Identificador Único]) FROM #actual_claims)<>@rows
    THROW 51973, 'DASHBOARD_SINISTROS_SOURCE_GRAIN_MISMATCH',1;

DECLARE @widths TABLE(field_name nvarchar(100) PRIMARY KEY, max_bytes int);
INSERT @widths VALUES
    (N'insurance_claim_location',510),(N'informed_by',510),(N'icm_fis_fit_pyr_nickname',510),
    (N'icm_crn_psn_nickname',510),(N'icm_dvr_iil_name',510),(N'icm_fer_name',510),
    (N'icm_vie_license_plate',40),(N'icm_ttt_ore_description',1000),(N'icm_ttt_ore_code',200),
    (N'icm_ttt_dealing_type',200),(N'icm_ttt_solution_type',200);
IF EXISTS (
    SELECT 1 FROM #source_claims s CROSS APPLY OPENJSON(s.record_json) f
    JOIN @widths w ON w.field_name=f.[key] COLLATE DATABASE_DEFAULT
    WHERE DATALENGTH(f.[value])>w.max_bytes
) THROW 51974,'DASHBOARD_SINISTROS_VALUE_WIDTH_EXCEEDED',1;

-- Compare raw rows including multiplicity, using a separate parse from the view.
SELECT HASHBYTES('SHA2_256',CONVERT(varbinary(max),record_json)) AS hash_value, COUNT_BIG(*) AS occurrences
INTO #source_fingerprints FROM #source_claims
GROUP BY HASHBYTES('SHA2_256',CONVERT(varbinary(max),record_json));
SELECT HASHBYTES('SHA2_256',CONVERT(varbinary(max),[Metadata])) AS hash_value, COUNT_BIG(*) AS occurrences
INTO #actual_fingerprints FROM #actual_claims
GROUP BY HASHBYTES('SHA2_256',CONVERT(varbinary(max),[Metadata]));
IF EXISTS (SELECT * FROM #source_fingerprints EXCEPT SELECT * FROM #actual_fingerprints)
    OR EXISTS (SELECT * FROM #actual_fingerprints EXCEPT SELECT * FROM #source_fingerprints)
    THROW 51975,'DASHBOARD_SINISTROS_METADATA_DIFFERS',1;

IF EXISTS (
    SELECT 1
    FROM #actual_claims a
    CROSS APPLY (VALUES(
        CONVERT(bigint,JSON_VALUE(a.[Metadata],'$.sequence_code')),
        CONVERT(bigint,JSON_VALUE(a.[Metadata],'$.icm_fis_fit_corporation_sequence_number')),
        CONVERT(bigint,JSON_VALUE(a.[Metadata],'$.icm_fis_ioe_number'))
    )) k(claim_number,freight_number,invoice_number)
    WHERE NOT EXISTS (
        SELECT a.[Identificador Único], a.[Nº do Sinistro], a.[Minuta], a.[Nota Fiscal/Número],
               a.[Data abertura], a.[Data ocorrência], a.[Previsão de solução], a.[Data finalização],
               a.[Quantidade de documentos], a.[Volumes], a.[Peso], a.[Valor NF],
               a.[valor a pagar ao cliente], a.[Valor nota de crédito ao cliente],
               a.[Valor a receber do responsável], a.[Valor nota de débito do responsável],
               a.[Valor a receber da seguradora], a.[Resultado final],
               a.[Pessoa/Nome fantasia], a.[Ocorrência/Ocorrência], a.[Usuário/Nome],
               a.[Veículo/Placa], a.[Ocorrência/Descrição], a.[Ocorrência/Código], a.[Tratativa/Data tratativa]
        INTERSECT
        SELECT LOWER(CONVERT(char(64),HASHBYTES('SHA2_256',
                   CONCAT_WS('|',k.claim_number,COALESCE(CONVERT(varchar(20),k.invoice_number),'null'),
                                  COALESCE(CONVERT(varchar(20),k.freight_number),'null'))),2)),
               k.claim_number,k.freight_number,k.invoice_number,
               CONVERT(date,JSON_VALUE(a.[Metadata],'$.opening_at_date')),
               CONVERT(date,JSON_VALUE(a.[Metadata],'$.occurrence_at_date')),
               CONVERT(date,JSON_VALUE(a.[Metadata],'$.expected_solution_date')),
               CONVERT(date,JSON_VALUE(a.[Metadata],'$.finished_at_date')),
               CONVERT(int,JSON_VALUE(a.[Metadata],'$.invoices_count')),
               CONVERT(int,JSON_VALUE(a.[Metadata],'$.invoices_volumes')),
               CONVERT(decimal(18,3),JSON_VALUE(a.[Metadata],'$.invoices_weight')),
               CONVERT(decimal(18,2),JSON_VALUE(a.[Metadata],'$.invoices_value')),
               CONVERT(decimal(18,2),JSON_VALUE(a.[Metadata],'$.customer_debits_subtotal')),
               CONVERT(decimal(18,2),JSON_VALUE(a.[Metadata],'$.customer_credit_entries_subtotal')),
               CONVERT(decimal(18,2),JSON_VALUE(a.[Metadata],'$.responsible_credits_subtotal')),
               CONVERT(decimal(18,2),JSON_VALUE(a.[Metadata],'$.responsible_debit_entries_subtotal')),
               CONVERT(decimal(18,2),JSON_VALUE(a.[Metadata],'$.insurer_credits_subtotal')),
               CONVERT(decimal(18,2),JSON_VALUE(a.[Metadata],'$.insurance_claim_total')),
               JSON_VALUE(a.[Metadata],'$.icm_crn_psn_nickname'),
               JSON_VALUE(a.[Metadata],'$.icm_dvr_iil_name'),
               JSON_VALUE(a.[Metadata],'$.icm_fer_name'),
               JSON_VALUE(a.[Metadata],'$.icm_vie_license_plate'),
               JSON_VALUE(a.[Metadata],'$.icm_ttt_ore_description'),
               JSON_VALUE(a.[Metadata],'$.icm_ttt_ore_code'),
               CONVERT(datetimeoffset(7),JSON_VALUE(a.[Metadata],'$.icm_ttt_treatment_at'))
    )
) THROW 51976,'DASHBOARD_SINISTROS_CONTRACT_VALUES_DIFFER',1;

DECLARE @historical_rows bigint, @historical_identity_matches bigint;
WITH historical AS (
    SELECT TOP(100) [Identificador Único], [Nº do Sinistro], [Nota Fiscal/Número], [Minuta]
    FROM ETL_SISTEMA.dbo.vw_sinistros_powerbi ORDER BY [Data de extracao] DESC, [Identificador Único]
)
SELECT @historical_rows=COUNT_BIG(*),
       @historical_identity_matches=COUNT_BIG(CASE WHEN [Identificador Único]=LOWER(CONVERT(char(64),
           HASHBYTES('SHA2_256', CONCAT_WS('|',[Nº do Sinistro],
               COALESCE(CONVERT(varchar(20),[Nota Fiscal/Número]),'null'),COALESCE(CONVERT(varchar(20),[Minuta]),'null'))),2)) THEN 1 END)
FROM historical;
IF @historical_rows=0 OR @historical_rows<>@historical_identity_matches
    THROW 51977,'DASHBOARD_SINISTROS_V1_IDENTITY_DIFFERS',1;

SELECT @rows AS source_rows, @rows AS unique_claim_note_freight_keys,
       COUNT_BIG(DISTINCT a.[Nº do Sinistro]) AS claim_numbers,
       SUM(a.[valor a pagar ao cliente]) AS customer_debit, SUM(a.[Resultado final]) AS final_total,
       @historical_identity_matches AS historical_v1_ids_verified
FROM #actual_claims a
FOR JSON PATH, INCLUDE_NULL_VALUES, WITHOUT_ARRAY_WRAPPER;
