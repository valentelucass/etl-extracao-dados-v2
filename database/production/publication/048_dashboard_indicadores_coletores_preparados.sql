SELECT TOP (0) * INTO core.dashboard_coletores_leitura FROM pub.fato_gestao_vista_coletores;
CREATE CLUSTERED INDEX IX_dashboard_coletores_data ON core.dashboard_coletores_leitura(data_referencia);
GO
CREATE OR ALTER VIEW pub.fato_gestao_vista_coletores_preparada AS
SELECT * FROM core.dashboard_coletores_leitura;
GO
CREATE OR ALTER PROCEDURE core.usp_dashboard_preparar_coletores AS
BEGIN
    SET NOCOUNT ON;
    IF @@TRANCOUNT=0 THROW 52096,'Preparar coletores na transacao da publicacao.',1;
    SELECT * INTO #coletores FROM pub.fato_gestao_vista_coletores OPTION(MAXDOP 1,MAX_GRANT_PERCENT=2);
    DELETE FROM core.dashboard_coletores_leitura;
    INSERT core.dashboard_coletores_leitura SELECT * FROM #coletores;
    DELETE FROM ctl.dashboard_leituras_publicadas WHERE versao=48;
    INSERT ctl.dashboard_leituras_publicadas VALUES(48,SYSUTCDATETIME());
END;
GO
DECLARE @ddl nvarchar(max)=OBJECT_DEFINITION(OBJECT_ID('core.usp_dashboard_publicar_fatos_fretes'));
DECLARE @at int=CHARINDEX(N'@audit nvarchar(max)',@ddl);
IF @ddl IS NULL OR @at=0 OR CHARINDEX('EXEC core.usp_dashboard_preparar_leituras_operacionais;',@ddl)=0
    THROW 52097,'Publicador de leituras e auditoria ausente.',1;
SET @ddl=N'CREATE OR ALTER PROCEDURE core.usp_dashboard_publicar_fatos_fretes '+SUBSTRING(@ddl,@at,LEN(@ddl));
SET @ddl=REPLACE(@ddl,'EXEC core.usp_dashboard_preparar_leituras_operacionais;',
 N'EXEC core.usp_dashboard_preparar_leituras_operacionais;
        EXEC core.usp_dashboard_preparar_coletores;');
EXEC sys.sp_executesql @ddl;