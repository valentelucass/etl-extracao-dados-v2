-- Materializa os campos já calculados pelo contrato de cada relatório. A metadata
-- é comprimida no disco e o otimizador só a descomprime quando a consulta a pede.
DECLARE @sources TABLE (name sysname, schema_name sysname, table_name sysname, read_schema sysname,
                        read_name sysname, date_column sysname);
INSERT @sources VALUES
('dashboard_fato_manifestos','core','dashboard_manifestos_leitura','core','dashboard_manifestos_leitura_v','data_criacao'),
('dashboard_faturas_vigentes','core','dashboard_faturas_leitura','core','dashboard_faturas_leitura_v','data_emissao_fatura'),
('vw_contas_a_pagar_powerbi','pub','dashboard_contas_leitura','pub','vw_contas_a_pagar_powerbi_preparada',N'Emissão'),
('vw_localizacao_cargas_powerbi','pub','dashboard_localizacao_leitura','pub','vw_localizacao_cargas_powerbi_preparada',N'Data do frete');
DECLARE @name sysname,@schema sysname,@table sysname,@read_schema sysname,@view sysname,@date sysname,
        @source nvarchar(260),@target nvarchar(260),@encode nvarchar(max),@decode nvarchar(max),
        @ddl nvarchar(max),@body nvarchar(max)=N'',@replace nvarchar(max)=N'';
DECLARE sources CURSOR LOCAL FAST_FORWARD FOR SELECT * FROM @sources;
OPEN sources; FETCH NEXT FROM sources INTO @name,@schema,@table,@read_schema,@view,@date;
WHILE @@FETCH_STATUS=0
BEGIN
    SET @source=QUOTENAME(@schema)+'.'+QUOTENAME(@name);
    SET @target=N'core.'+QUOTENAME(@table);
    IF NOT EXISTS(SELECT 1 FROM sys.columns WHERE object_id=OBJECT_ID(@source,'V') AND name=N'Metadata'
                  AND system_type_id=231 AND max_length=-1) THROW 52086,'Metadata fora do contrato.',1;
    SELECT @encode=STRING_AGG(CONVERT(nvarchar(max),CASE WHEN name=N'Metadata'
                    THEN N'COMPRESS('+QUOTENAME(name)+N') AS [metadata_gzip]' ELSE QUOTENAME(name) END),N',') WITHIN GROUP(ORDER BY column_id),
           @decode=STRING_AGG(CONVERT(nvarchar(max),CASE WHEN name=N'Metadata'
                    THEN N'CONVERT(nvarchar(max),DECOMPRESS([metadata_gzip])) AS '+QUOTENAME(name) ELSE QUOTENAME(name) END),N',') WITHIN GROUP(ORDER BY column_id)
    FROM sys.columns WHERE object_id=OBJECT_ID(@source);
    SET @ddl=N'SELECT TOP(0) '+@encode+N' INTO '+@target+' FROM '+@source+
             N'; CREATE CLUSTERED INDEX IX_periodo ON '+@target+N'('+QUOTENAME(@date)+N');';
    EXEC sys.sp_executesql @ddl;
    SET @ddl=N'CREATE OR ALTER VIEW '+QUOTENAME(@read_schema)+'.'+QUOTENAME(@view)+N' AS SELECT '+@decode+' FROM '+@target;
    EXEC sys.sp_executesql @ddl;
    SET @body+=N'SELECT '+@encode+N' INTO #'+@table+N' FROM '+@source+N' OPTION(MAXDOP 1,MAX_GRANT_PERCENT=2);'+CHAR(10);
    SET @replace+=N'DELETE FROM '+@target+N'; INSERT '+@target+N' SELECT * FROM #'+@table+N' OPTION(MAXDOP 1);'+CHAR(10);
    FETCH NEXT FROM sources INTO @name,@schema,@table,@read_schema,@view,@date;
END;
CLOSE sources; DEALLOCATE sources;
SET @ddl=N'CREATE OR ALTER PROCEDURE core.usp_dashboard_preparar_leituras_operacionais AS BEGIN
SET NOCOUNT ON;
IF @@TRANCOUNT=0 THROW 52087,''Publicar dentro do lote de relatorios.'',1;
'+@body+@replace+N' END;';
EXEC sys.sp_executesql @ddl;

DECLARE @views TABLE (name sysname,base_name sysname,replacement sysname);
INSERT @views VALUES ('vw_manifestos_powerbi','dashboard_fato_manifestos','dashboard_manifestos_leitura_v'),
 ('vw_fato_manifestos_dash','dashboard_fato_manifestos','dashboard_manifestos_leitura_v'),
 ('vw_faturas_por_cliente_powerbi','dashboard_faturas_vigentes','dashboard_faturas_leitura_v'),
 ('fato_gestao_vista_faturas','dashboard_faturas_vigentes','dashboard_faturas_leitura_v');
DECLARE @base_name sysname,@new_base sysname,@at int;
DECLARE wrappers CURSOR LOCAL FAST_FORWARD FOR SELECT * FROM @views;
OPEN wrappers; FETCH NEXT FROM wrappers INTO @name,@base_name,@new_base;
WHILE @@FETCH_STATUS=0
BEGIN
    SET @ddl=OBJECT_DEFINITION(OBJECT_ID(N'pub.'+QUOTENAME(@name),'V'));
    SET @at=CHARINDEX('AS',@ddl,CHARINDEX(@name,@ddl)+LEN(@name));
    IF @ddl IS NULL OR @at=0 OR CHARINDEX(N'core.'+@base_name,@ddl)=0 THROW 52088,'Origem de relatorio inesperada.',1;
    SET @ddl=N'CREATE OR ALTER VIEW pub.'+QUOTENAME(@name+'_preparada')+N' '+SUBSTRING(@ddl,@at,LEN(@ddl));
    SET @ddl=REPLACE(@ddl,N'core.'+@base_name,N'core.'+@new_base);
    EXEC sys.sp_executesql @ddl;
    FETCH NEXT FROM wrappers INTO @name,@base_name,@new_base;
END;
CLOSE wrappers; DEALLOCATE wrappers;

-- Inclui as quatro leituras na mesma troca atômica dos fatos de frete.
SET @ddl=OBJECT_DEFINITION(OBJECT_ID('core.usp_dashboard_publicar_fatos_fretes','P'));
IF CHARINDEX(N'IF EXISTS(SELECT source_scope',@ddl)=0 THROW 52089,'Coordenador de publicacao inesperado.',1;
SET @at=CHARINDEX(N'AS',@ddl,CHARINDEX(N'usp_dashboard_publicar_fatos_fretes',@ddl)+LEN(N'usp_dashboard_publicar_fatos_fretes'));
SET @ddl=N'CREATE OR ALTER PROCEDURE core.usp_dashboard_publicar_fatos_fretes '+SUBSTRING(@ddl,@at,LEN(@ddl));
SET @ddl=REPLACE(@ddl,N'IF EXISTS(SELECT source_scope',N'EXEC core.usp_dashboard_preparar_leituras_operacionais;
        IF EXISTS(SELECT source_scope');
EXEC sys.sp_executesql @ddl;
