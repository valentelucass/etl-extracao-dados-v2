CREATE TABLE ctl.dashboard_leituras_publicadas(
    versao int NOT NULL PRIMARY KEY,
    publicado_utc datetime2(7) NOT NULL
);
DECLARE @ddl nvarchar(max)=OBJECT_DEFINITION(OBJECT_ID('core.usp_dashboard_preparar_leituras_operacionais','P'));
DECLARE @at int=CHARINDEX(N'AS',@ddl,CHARINDEX(N'usp_dashboard_preparar_leituras_operacionais',@ddl)+LEN(N'usp_dashboard_preparar_leituras_operacionais'));
IF @ddl IS NULL OR CHARINDEX(N'INSERT core.dashboard_detalhe_fretes_leitura',@ddl)=0 OR @at=0
    THROW 52092,'Preparo dos relatorios incompleto.',1;
SET @ddl=N'CREATE OR ALTER PROCEDURE core.usp_dashboard_preparar_leituras_operacionais '+SUBSTRING(@ddl,@at,LEN(@ddl));
SET @at=LEN(RTRIM(@ddl))-3;
IF RIGHT(RTRIM(@ddl),4)<>N'END;' THROW 52093,'Final do publicador inesperado.',1;
SET @ddl=STUFF(@ddl,@at,0,N'
DELETE FROM ctl.dashboard_leituras_publicadas WHERE versao=46;
INSERT ctl.dashboard_leituras_publicadas VALUES(46,SYSUTCDATETIME());
');
EXEC sys.sp_executesql @ddl;
