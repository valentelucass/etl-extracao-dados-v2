SET NOCOUNT ON;
IF (SELECT COUNT(*) FROM ref.dashboard_frete_regras_documento WHERE rule_type='BRANCH')<>11
   OR (SELECT COUNT(*) FROM ref.dashboard_frete_regras_documento WHERE rule_type='CUBAGE')<>40
    THROW 52048,'DASHBOARD_FREIGHT_RULES_NOT_BOUND',1;

SELECT TOP(0) * INTO #indicators FROM pub.fato_gestao_vista_fretes;
BEGIN TRY
    EXECUTE AS USER=N'usuario_etl';
    INSERT #indicators SELECT * FROM pub.fato_gestao_vista_fretes;
    REVERT;
END TRY
BEGIN CATCH
    IF USER_NAME()=N'usuario_etl' REVERT;
    THROW;
END CATCH;

CREATE UNIQUE CLUSTERED INDEX ix_indicators ON #indicators(indicador_codigo,numero_minuta);

-- VALIDATION BATCH
IF NOT EXISTS(SELECT 1 FROM #indicators WHERE indicador_codigo='PE' AND is_linha_valida_indicador=1)
   OR NOT EXISTS(SELECT 1 FROM #indicators WHERE indicador_codigo='CB' AND is_linha_valida_indicador=1)
   OR EXISTS(SELECT 1 FROM #indicators i WHERE i.hash_linha IS NULL OR i.frete_id_origem IS NULL OR
        i.numero_minuta IS NULL OR i.data_referencia IS NULL OR
        i.indicador_codigo NOT IN ('PE','CB') OR
        (i.indicador_codigo='PE' AND i.data_referencia<>i.data_previsao_entrega) OR
        (i.indicador_codigo='CB' AND i.data_referencia<>i.data_frete_date) OR
        (i.is_linha_valida_indicador=1 AND (i.is_cancelado=1 OR i.is_cortesia=1 OR
            (i.indicador_codigo='CB' AND (i.is_pagador_excluido_cubagem=1 OR COALESCE(i.valor_total,0)<=0.01)) OR
            (i.indicador_codigo='PE' AND i.filial_performance_key IS NULL))))
    THROW 52049,'DASHBOARD_FREIGHT_INDICATOR_INVARIANT_FAILED',1;

IF EXISTS(SELECT 1 FROM sys.sql_expression_dependencies WHERE referencing_id IN (
    OBJECT_ID('core.dashboard_fretes_linhas','V'),OBJECT_ID('pub.fato_gestao_vista_fretes','V'))
    AND (referenced_database_name IS NOT NULL OR referenced_server_name IS NOT NULL))
    THROW 52050,'DASHBOARD_FREIGHT_INDICATOR_CROSS_DATABASE_DEPENDENCY',1;

SELECT COUNT_BIG(*) AS freightIndicators,
       COUNT_BIG(CASE WHEN indicador_codigo='PE' THEN 1 END) AS deliveryPerformanceRows,
       COUNT_BIG(CASE WHEN indicador_codigo='CB' THEN 1 END) AS cubageRows,
       COUNT_BIG(CASE WHEN indicador_codigo='PE' AND is_linha_valida_indicador=1 THEN 1 END) AS eligiblePerformanceRows,
       COUNT_BIG(CASE WHEN indicador_codigo='CB' AND is_linha_valida_indicador=1 THEN 1 END) AS eligibleCubageRows,
       COUNT_BIG(CASE WHEN is_pagador_excluido_cubagem=1 THEN 1 END) AS cubageExcludedByPayer
FROM #indicators
FOR JSON PATH,WITHOUT_ARRAY_WRAPPER;
