-- O comprovante é um indicador de existência por minuta. Agrupar o inventário
-- uma vez evita varrê-lo para cada frete ao montar o detalhe de Performance.
DECLARE @ddl nvarchar(max)=OBJECT_DEFINITION(OBJECT_ID('pub.vw_fretes_powerbi_preparada','V'));
DECLARE @exists nvarchar(max)=N'EXISTS (SELECT 1 FROM pub.vw_inventario_powerbi p WHERE p.[Nº Minuta]=f.corporation_sequence_number AND p.[Comprovante Anexado]=N''Sim'')';
IF @ddl IS NULL OR CHARINDEX(@exists,@ddl)=0 OR CHARINDEX('FROM core.dashboard_fretes_publicacao_preparada f',@ddl)=0
    THROW 52090,'Contrato de comprovantes divergente.',1;
DECLARE @at int=CHARINDEX('AS',@ddl,CHARINDEX('vw_fretes_powerbi_preparada',@ddl)+LEN('vw_fretes_powerbi_preparada'));
SET @ddl=N'CREATE OR ALTER VIEW core.dashboard_detalhe_fretes_entrada '+SUBSTRING(@ddl,@at,LEN(@ddl));
SET @ddl=REPLACE(@ddl,@exists,N'comprovantes.minuta IS NOT NULL');
SET @ddl=REPLACE(@ddl,'FROM core.dashboard_fretes_publicacao_preparada f',N'FROM core.dashboard_fretes_publicacao_preparada f
LEFT HASH JOIN (SELECT DISTINCT [Nº Minuta] AS minuta FROM pub.vw_inventario_powerbi WHERE [Comprovante Anexado]=N''Sim'') comprovantes
    ON comprovantes.minuta=f.corporation_sequence_number');
EXEC sys.sp_executesql @ddl;
DECLARE @encode nvarchar(max),@decode nvarchar(max);
SELECT @encode=STRING_AGG(CONVERT(nvarchar(max),CASE WHEN name=N'Metadata' THEN N'COMPRESS([Metadata]) AS metadata_gzip' ELSE QUOTENAME(name) END),N',') WITHIN GROUP(ORDER BY column_id),
       @decode=STRING_AGG(CONVERT(nvarchar(max),CASE WHEN name=N'Metadata' THEN N'CONVERT(nvarchar(max),DECOMPRESS(metadata_gzip)) AS [Metadata]' ELSE QUOTENAME(name) END),N',') WITHIN GROUP(ORDER BY column_id)
FROM sys.columns WHERE object_id=OBJECT_ID('core.dashboard_detalhe_fretes_entrada');
SET @ddl=N'SELECT TOP(0) '+@encode+N' INTO core.dashboard_detalhe_fretes_leitura FROM core.dashboard_detalhe_fretes_entrada;
CREATE CLUSTERED INDEX IX_dashboard_detalhe_periodo ON core.dashboard_detalhe_fretes_leitura([Data frete],[ID]);';
EXEC sys.sp_executesql @ddl;
SET @ddl=N'CREATE OR ALTER VIEW pub.vw_fretes_powerbi_pronta AS SELECT '+@decode+N' FROM core.dashboard_detalhe_fretes_leitura';
EXEC sys.sp_executesql @ddl;
SET @ddl=OBJECT_DEFINITION(OBJECT_ID('core.usp_dashboard_preparar_leituras_operacionais','P'));
SET @at=CHARINDEX('AS',@ddl,CHARINDEX('usp_dashboard_preparar_leituras_operacionais',@ddl)+LEN('usp_dashboard_preparar_leituras_operacionais'));
IF @ddl IS NULL OR @at=0 OR CHARINDEX(N'DELETE FROM core.',@ddl)=0 THROW 52091,'Publicador ausente.',1;
SET @ddl=N'CREATE OR ALTER PROCEDURE core.usp_dashboard_preparar_leituras_operacionais '+SUBSTRING(@ddl,@at,LEN(@ddl));
SET @ddl=STUFF(@ddl,CHARINDEX(N'DELETE FROM core.',@ddl),0,
    N'SELECT '+@encode+N' INTO #detalhe FROM core.dashboard_detalhe_fretes_entrada OPTION(MAXDOP 1,MAX_GRANT_PERCENT=2);
DELETE FROM core.dashboard_detalhe_fretes_leitura;
INSERT core.dashboard_detalhe_fretes_leitura SELECT * FROM #detalhe OPTION(MAXDOP 1);
');
EXEC sys.sp_executesql @ddl;
