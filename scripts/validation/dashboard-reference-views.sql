SET NOCOUNT ON;

-- Compare calendar rules over the entire legacy interval. The installation time
-- belongs to the V2 release, so it is checked for presence instead of equality.
SELECT data, data_key, ano, mes, dia, trimestre, semana_iso, dia_semana_iso,
       nome_dia_semana COLLATE Latin1_General_100_BIN2 AS nome_dia_semana, is_fim_semana,
       is_feriado_nacional, nome_feriado_nacional COLLATE Latin1_General_100_BIN2 AS nome_feriado_nacional,
       is_ponto_facultativo, nome_ponto_facultativo COLLATE Latin1_General_100_BIN2 AS nome_ponto_facultativo,
       is_dia_util, data_referencia_faturamento, data_referencia_faturamento_key, ano_mes
INTO #legacy_calendar FROM ETL_SISTEMA.dbo.dim_calendario;
SELECT TOP(0) * INTO #actual_calendar FROM pub.dim_calendario;
SELECT TOP(0) * INTO #actual_branches FROM pub.vw_dim_filiais;
SELECT TOP(0) * INTO #actual_drivers FROM pub.vw_dim_motoristas;
SELECT TOP(0) * INTO #actual_vehicles FROM pub.vw_dim_veiculos;
SELECT TOP(0) * INTO #actual_accounts FROM pub.vw_dim_planocontas;
BEGIN TRY
    EXECUTE AS USER=N'usuario_etl';
    INSERT #actual_calendar SELECT * FROM pub.dim_calendario;
    INSERT #actual_branches SELECT * FROM pub.vw_dim_filiais;
    INSERT #actual_drivers SELECT * FROM pub.vw_dim_motoristas;
    INSERT #actual_vehicles SELECT * FROM pub.vw_dim_veiculos;
    INSERT #actual_accounts SELECT * FROM pub.vw_dim_planocontas;
    REVERT;
END TRY
BEGIN CATCH
    IF USER_NAME()=N'usuario_etl' REVERT;
    THROW;
END CATCH;

SELECT data, data_key, ano, mes, dia, trimestre, semana_iso, dia_semana_iso,
       nome_dia_semana COLLATE Latin1_General_100_BIN2 AS nome_dia_semana, is_fim_semana,
       is_feriado_nacional, nome_feriado_nacional COLLATE Latin1_General_100_BIN2 AS nome_feriado_nacional,
       is_ponto_facultativo, nome_ponto_facultativo COLLATE Latin1_General_100_BIN2 AS nome_ponto_facultativo,
       is_dia_util, data_referencia_faturamento, data_referencia_faturamento_key, ano_mes
INTO #candidate_calendar FROM #actual_calendar;

IF (SELECT COUNT_BIG(*) FROM #legacy_calendar) <> (SELECT COUNT_BIG(*) FROM #candidate_calendar)
   OR (SELECT COUNT_BIG(DISTINCT data) FROM #candidate_calendar) <>
      DATEDIFF(DAY, CONVERT(date,'20191201'), CONVERT(date,'20330101'))
   OR EXISTS(SELECT * FROM #legacy_calendar EXCEPT SELECT * FROM #candidate_calendar)
   OR EXISTS(SELECT * FROM #candidate_calendar EXCEPT SELECT * FROM #legacy_calendar)
   OR EXISTS(SELECT 1 FROM #actual_calendar WHERE atualizado_em IS NULL)
    THROW 51969, 'DASHBOARD_CALENDAR_RULES_DIFFER', 1;

SELECT l.entity_name, JSON_QUERY(l.source_envelope, '$.record') AS record_json
INTO #reference_records
FROM stg.production_export_latest AS l
WHERE l.entity_name IN ('FRETES','MANIFESTOS','CONTAS_A_PAGAR','FATURAS_POR_CLIENTE');

-- The comparison never silently accepts truncation in the V1 widths.
DECLARE @widths TABLE (field_name nvarchar(100) PRIMARY KEY, max_bytes int);
INSERT @widths VALUES
    (N'fit_crn_psn_nickname',510), (N'mft_crn_psn_nickname',510), (N'ant_crn_psn_nickname',510),
    (N'mft_mdr_iil_name',510), (N'mft_vie_license_plate',20),
    (N'mft_vie_vee_name',510), (N'mft_vie_onr_name',510),
    (N'ant_ils_pas_ant_name',510), (N'ant_ils_pas_ant_classification',200);
IF EXISTS (
    SELECT 1 FROM #reference_records AS r
    CROSS APPLY OPENJSON(r.record_json) AS f
    JOIN @widths AS w ON w.field_name = f.[key] COLLATE DATABASE_DEFAULT
    WHERE f.[type] NOT IN (0,1) OR DATALENGTH(f.[value]) > w.max_bytes
) THROW 51970, 'DASHBOARD_REFERENCE_VALUE_TYPE_OR_WIDTH', 1;

WITH branch_names AS (
    SELECT JSON_VALUE(record_json,'$.fit_crn_psn_nickname') AS branch_name
    FROM #reference_records WHERE entity_name IN ('FRETES','FATURAS_POR_CLIENTE')
    UNION ALL SELECT JSON_VALUE(record_json,'$.mft_crn_psn_nickname')
    FROM #reference_records WHERE entity_name='MANIFESTOS'
    UNION ALL SELECT JSON_VALUE(record_json,'$.ant_crn_psn_nickname')
    FROM #reference_records WHERE entity_name='CONTAS_A_PAGAR'
)
SELECT DISTINCT LTRIM(RTRIM(branch_name)) COLLATE Latin1_General_CI_AS AS NomeFilial,
       CONVERT(time(0),'00:00:00') AS clock_value
INTO #expected_branches
FROM branch_names WHERE LTRIM(RTRIM(branch_name))<>N'';

SELECT JSON_VALUE(record_json,'$.mft_mdr_iil_name') COLLATE Latin1_General_CI_AS AS driver_name,
       JSON_VALUE(record_json,'$.mft_crn_psn_nickname') COLLATE Latin1_General_CI_AS AS branch_name,
       JSON_VALUE(record_json,'$.mft_vie_license_plate') COLLATE Latin1_General_CI_AS AS plate,
       JSON_VALUE(record_json,'$.mft_vie_vee_name') COLLATE Latin1_General_CI_AS AS vehicle_type,
       JSON_VALUE(record_json,'$.mft_vie_onr_name') COLLATE Latin1_General_CI_AS AS vehicle_owner
INTO #manifests
FROM #reference_records WHERE entity_name='MANIFESTOS';

SELECT DISTINCT UPPER(LTRIM(RTRIM(driver_name))) AS NomeMotorista, branch_name AS Filial
INTO #expected_drivers
FROM #manifests
WHERE LTRIM(RTRIM(driver_name))<>N'' AND driver_name NOT LIKE N'%MOTORISTA%';

SELECT UPPER(LTRIM(RTRIM(plate))) AS Placa, MAX(UPPER(LTRIM(RTRIM(vehicle_type)))) AS TipoVeiculo,
       MAX(UPPER(LTRIM(RTRIM(vehicle_owner)))) AS Proprietario, branch_name AS Filial
INTO #expected_vehicles
FROM #manifests
WHERE LTRIM(RTRIM(plate))<>N''
GROUP BY UPPER(LTRIM(RTRIM(plate))), branch_name;

WITH accounts AS (
    SELECT JSON_VALUE(record_json,'$.ant_ils_pas_ant_name') COLLATE Latin1_General_CI_AS AS description,
           JSON_VALUE(record_json,'$.ant_ils_pas_ant_classification') COLLATE Latin1_General_CI_AS AS classification
    FROM #reference_records WHERE entity_name='CONTAS_A_PAGAR'
)
SELECT UPPER(LTRIM(RTRIM(description))) AS Descricao,
       COALESCE(MAX(classification),N'OUTROS / NÃO CLASSIFICADO') AS Classificacao,
       CONVERT(time(0),'00:00:00') AS clock_value
INTO #expected_accounts
FROM accounts WHERE LTRIM(RTRIM(description))<>N''
GROUP BY UPPER(LTRIM(RTRIM(description)));

IF EXISTS(SELECT * FROM #expected_branches EXCEPT SELECT * FROM #actual_branches)
   OR EXISTS(SELECT * FROM #actual_branches EXCEPT SELECT * FROM #expected_branches)
   OR EXISTS(SELECT * FROM #expected_drivers EXCEPT SELECT * FROM #actual_drivers)
   OR EXISTS(SELECT * FROM #actual_drivers EXCEPT SELECT * FROM #expected_drivers)
   OR EXISTS(SELECT * FROM #expected_vehicles EXCEPT SELECT * FROM #actual_vehicles)
   OR EXISTS(SELECT * FROM #actual_vehicles EXCEPT SELECT * FROM #expected_vehicles)
   OR EXISTS(SELECT * FROM #expected_accounts EXCEPT SELECT * FROM #actual_accounts)
   OR EXISTS(SELECT * FROM #actual_accounts EXCEPT SELECT * FROM #expected_accounts)
    THROW 51971, 'DASHBOARD_REFERENCE_VALUES_DIFFER', 1;

DECLARE @calendar bigint=(SELECT COUNT_BIG(*) FROM #actual_calendar),
        @branches bigint=(SELECT COUNT_BIG(*) FROM #actual_branches),
        @drivers bigint=(SELECT COUNT_BIG(*) FROM #actual_drivers),
        @vehicles bigint=(SELECT COUNT_BIG(*) FROM #actual_vehicles),
        @accounts bigint=(SELECT COUNT_BIG(*) FROM #actual_accounts);

IF @calendar<>(SELECT COUNT_BIG(*) FROM #candidate_calendar)
   OR @branches<>(SELECT COUNT_BIG(*) FROM #expected_branches)
   OR @drivers<>(SELECT COUNT_BIG(*) FROM #expected_drivers)
   OR @vehicles<>(SELECT COUNT_BIG(*) FROM #expected_vehicles)
   OR @accounts<>(SELECT COUNT_BIG(*) FROM #expected_accounts)
   OR @branches=0 OR @drivers=0 OR @vehicles=0 OR @accounts=0
    THROW 51972,'DASHBOARD_REFERENCE_CONSUMER_COUNT_MISMATCH',1;

SELECT @calendar AS calendar_rows, @branches AS branches, @drivers AS drivers, @vehicles AS vehicles,
       @accounts AS accounts, (SELECT COUNT_BIG(*) FROM #reference_records) AS source_rows,
       (SELECT COUNT_BIG(*) FROM #manifests WHERE driver_name LIKE N'%MOTORISTA%') AS generic_driver_rows_excluded
FOR JSON PATH, WITHOUT_ARRAY_WRAPPER;
