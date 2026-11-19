-- O índice segue a mesma ordenação estável da tabela de Faturas.
CREATE INDEX IX_dashboard_faturas_cte_pagina ON core.dashboard_faturas_leitura(data_emissao_cte DESC,unique_id DESC);
SELECT TOP(0) * INTO core.dashboard_dim_filiais FROM pub.vw_dim_filiais;
SELECT TOP(0) * INTO core.dashboard_dim_motoristas FROM pub.vw_dim_motoristas;
SELECT TOP(0) * INTO core.dashboard_dim_veiculos FROM pub.vw_dim_veiculos;
GO
CREATE OR ALTER VIEW pub.vw_dim_filiais_preparada AS SELECT * FROM core.dashboard_dim_filiais;
GO
CREATE OR ALTER VIEW pub.vw_dim_motoristas_preparada AS SELECT * FROM core.dashboard_dim_motoristas;
GO
CREATE OR ALTER VIEW pub.vw_dim_veiculos_preparada AS SELECT * FROM core.dashboard_dim_veiculos;
GO
CREATE OR ALTER PROCEDURE core.usp_dashboard_preparar_dimensoes AS
BEGIN
    SET NOCOUNT ON;
    IF @@TRANCOUNT=0 THROW 52100,'Publicar listas dentro de uma transacao.',1;
    SELECT * INTO #filiais FROM pub.vw_dim_filiais OPTION(MAXDOP 1,MAX_GRANT_PERCENT=2);
    SELECT * INTO #motoristas FROM pub.vw_dim_motoristas OPTION(MAXDOP 1,MAX_GRANT_PERCENT=2);
    SELECT * INTO #veiculos FROM pub.vw_dim_veiculos OPTION(MAXDOP 1,MAX_GRANT_PERCENT=2);
    DELETE FROM core.dashboard_dim_filiais;
    INSERT core.dashboard_dim_filiais SELECT * FROM #filiais;
    DELETE FROM core.dashboard_dim_motoristas;
    INSERT core.dashboard_dim_motoristas SELECT * FROM #motoristas;
    DELETE FROM core.dashboard_dim_veiculos;
    INSERT core.dashboard_dim_veiculos SELECT * FROM #veiculos;
    DELETE FROM ctl.dashboard_leituras_publicadas WHERE versao=49;
    INSERT ctl.dashboard_leituras_publicadas VALUES(49,SYSUTCDATETIME());
END;
GO
DECLARE @ddl nvarchar(max)=OBJECT_DEFINITION(OBJECT_ID('core.usp_dashboard_publicar_fatos_fretes'));
DECLARE @at int=CHARINDEX(N'@audit nvarchar(max)',@ddl);
IF @ddl IS NULL OR @at=0 OR CHARINDEX(N'EXEC core.usp_dashboard_preparar_coletores;',@ddl)=0
    THROW 52101,'Coordenador de publicacao inesperado.',1;
SET @ddl=N'CREATE OR ALTER PROCEDURE core.usp_dashboard_publicar_fatos_fretes '+SUBSTRING(@ddl,@at,LEN(@ddl));
SET @ddl=REPLACE(@ddl,N'EXEC core.usp_dashboard_preparar_coletores;',N'EXEC core.usp_dashboard_preparar_coletores;
        EXEC core.usp_dashboard_preparar_dimensoes;');
EXEC sys.sp_executesql @ddl;