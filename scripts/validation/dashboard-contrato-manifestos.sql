SET NOCOUNT ON;
SELECT [Tipo de contrato] AS tipo_contrato, [Tipo de contrato key] AS tipo_contrato_key,
       CASE WHEN [Tipo de contrato do motorista] = N'Próprio' THEN N'Frota' ELSE N'Frota + PX' END AS esperado
INTO #contratos_dalga
FROM pub.vw_fato_manifestos_dash_preparada
WHERE TRIM([Proprietário/Nome]) COLLATE Latin1_General_CI_AI = N'DALGA LOGISTICA E TRANSPORTES LTDA'
  AND [Tipo de contrato do veículo] = N'Agregado'
  AND COALESCE(TRIM([Tipo de contrato do motorista]), N'') IN (N'', N'Terceiro', N'Agregado', N'Próprio')
OPTION (MAXDOP 1, MAX_GRANT_PERCENT = 2);

IF EXISTS (SELECT 1 FROM #contratos_dalga
           WHERE tipo_contrato IS DISTINCT FROM esperado OR tipo_contrato_key IS DISTINCT FROM LOWER(esperado))
    THROW 52103, 'DASHBOARD_MANIFESTO_CONTRATO_DALGA_DIVERGENTE', 1;

SELECT COUNT_BIG(CASE WHEN esperado = N'Frota + PX' THEN 1 END) AS frota_px,
       COUNT_BIG(CASE WHEN esperado = N'Frota' THEN 1 END) AS frota
FROM #contratos_dalga FOR JSON PATH, WITHOUT_ARRAY_WRAPPER;
