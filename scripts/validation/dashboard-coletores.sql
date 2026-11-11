SET NOCOUNT ON;
SELECT TOP(0) * INTO #collectors FROM pub.fato_gestao_vista_coletores;
BEGIN TRY
    EXECUTE AS USER=N'usuario_etl';
    INSERT #collectors SELECT * FROM pub.fato_gestao_vista_coletores;
    REVERT;
END TRY
BEGIN CATCH
    IF USER_NAME()=N'usuario_etl' REVERT;
    THROW;
END CATCH;
CREATE UNIQUE CLUSTERED INDEX ix_collectors_grain ON #collectors(data_referencia,filial_key,classificacao);

-- VALIDATION BATCH
DECLARE @orders bigint,@manifests bigint;
SELECT @orders=COUNT_BIG(DISTINCT [N° Ordem])
FROM pub.vw_inventario_powerbi
WHERE [Data/Hora início] IS NOT NULL
  AND [Tipo] COLLATE Latin1_General_CI_AI IN(N'Carregamento',N'Descarregamento',N'Picking',N'Recebimento',N'Retorno');
SELECT @manifests=COUNT_BIG(DISTINCT TRY_CONVERT(bigint,JSON_VALUE(source_envelope,'$.record.sequence_code')))
FROM stg.production_export_latest
WHERE entity_name='MANIFESTOS' AND TRY_CONVERT(datetimeoffset(7),JSON_VALUE(source_envelope,'$.record.created_at')) IS NOT NULL
  AND COALESCE(TRIM(JSON_VALUE(source_envelope,'$.record.mft_man_name')),N'') COLLATE Latin1_General_CI_AI NOT LIKE N'carga fechada%'
  AND COALESCE(TRIM(JSON_VALUE(source_envelope,'$.record.mft_man_name')),N'') COLLATE Latin1_General_CI_AI NOT LIKE N'acerto de motorista%'
  AND COALESCE(TRIM(JSON_VALUE(source_envelope,'$.record.mft_man_name')),N'') COLLATE Latin1_General_CI_AI NOT LIKE N'frete retorno%'
  AND COALESCE(TRIM(JSON_VALUE(source_envelope,'$.record.mft_man_name')),N'') COLLATE Latin1_General_CI_AI NOT LIKE N'viagem vazia%';
IF @orders<1 OR @manifests<1 OR NOT EXISTS(SELECT 1 FROM #collectors)
   OR (SELECT SUM(manifestos_bipados) FROM #collectors)<>@orders
   OR (SELECT SUM(manifestos_emitidos) FROM #collectors)<>@manifests
   OR EXISTS(SELECT 1 FROM #collectors WHERE
       total_manifestos<>manifestos_emitidos+manifestos_descarregamento
       OR manifestos_incompletos>manifestos_bipados
       OR data_referencia_yyyymm<>YEAR(data_referencia)*100+MONTH(data_referencia)
       OR COALESCE(CONVERT(decimal(9,4),CONVERT(decimal(19,4),manifestos_bipados)*100.0/NULLIF(total_manifestos,0)),0)<>pct_utilizacao
       OR snapshot_em IS NULL OR hash_linha IS NULL OR excluido_na_origem<>0
       OR is_linha_valida_indicador<>1 OR classificacao<>N'Geral')
    THROW 52019,'DASHBOARD_COLLECTOR_SOURCE_TOTALS_DIFFER',1;

SELECT COUNT_BIG(*) AS date_branch_groups,
       SUM(c.manifestos_bipados) AS orders_count,
       SUM(c.manifestos_emitidos) AS manifests_issued,
       SUM(c.manifestos_descarregamento) AS manifests_unloading,
       SUM(c.manifestos_incompletos) AS incomplete_orders,
       COUNT_BIG(o.data_referencia) AS overlapping_v1_groups,
       COUNT_BIG(CASE WHEN o.data_referencia IS NOT NULL AND o.manifestos_bipados=c.manifestos_bipados
           AND o.manifestos_emitidos=c.manifestos_emitidos AND o.manifestos_descarregamento=c.manifestos_descarregamento
           AND o.manifestos_incompletos=c.manifestos_incompletos THEN 1 END) AS equal_v1_group_counts
FROM #collectors c
LEFT JOIN ETL_SISTEMA.dbo.fato_gestao_vista_coletores o ON o.data_referencia=c.data_referencia
    AND o.filial_key COLLATE Latin1_General_CI_AI=c.filial_key AND o.classificacao COLLATE DATABASE_DEFAULT=c.classificacao
    AND o.excluido_na_origem=0
FOR JSON PATH, INCLUDE_NULL_VALUES, WITHOUT_ARRAY_WRAPPER;
