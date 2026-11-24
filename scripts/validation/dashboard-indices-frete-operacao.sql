-- Confere os números efetivamente armazenados no índice contra a metadata pública.
IF EXISTS (
    SELECT [ID],[Nº Minuta],manifesto_entrega_consulta FROM core.dashboard_detalhe_fretes_leitura WITH (INDEX(IX_detalhe_minuta_operacao))
    EXCEPT SELECT [ID],[Nº Minuta],TRY_CONVERT(bigint,JSON_VALUE([Metadata],'$.fit_fte_l_y_mft_sequence_code')) FROM pub.vw_fretes_powerbi_pronta
) THROW 52212,'Manifesto de entrega divergente.',1;

-- O preparo incremental compara o mesmo conjunto de campos que grava. Verifica
-- também a forma de INSERT usada na recarga completa, sem gravar dados de teste.
DECLARE @publicados nvarchar(max), @originais nvarchar(max);
SELECT @publicados=STRING_AGG(CONVERT(nvarchar(max),QUOTENAME(name)),',') WITHIN GROUP(ORDER BY column_id),
       @originais=STRING_AGG(CONVERT(nvarchar(max),'a.'+QUOTENAME(name)),',') WITHIN GROUP(ORDER BY column_id)
FROM sys.columns WHERE object_id=OBJECT_ID('core.dashboard_detalhe_fretes_leitura') AND is_computed=0;
DECLARE @modulo nvarchar(max)=OBJECT_DEFINITION(OBJECT_ID('core.usp_dashboard_detalhe_incremental'));
IF @modulo IS NULL OR CHARINDEX('SELECT TOP(0) '+@publicados+' INTO #detalhe',@modulo)=0
    OR CHARINDEX('(SELECT '+@originais+' FOR JSON PATH',@modulo)=0
    THROW 52213,'A assinatura de publicacao inclui campos de consulta.',1;
DECLARE @ensaio nvarchar(max)=N'SELECT TOP(0) '+@publicados+N' INTO #amostra FROM core.dashboard_detalhe_fretes_leitura;
    INSERT core.dashboard_detalhe_fretes_leitura SELECT * FROM #amostra;';
EXEC sys.sp_executesql @ensaio;
