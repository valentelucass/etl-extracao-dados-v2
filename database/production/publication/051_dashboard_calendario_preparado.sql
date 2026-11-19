SELECT TOP (0) * INTO core.dashboard_calendario FROM pub.dim_calendario;
CREATE UNIQUE CLUSTERED INDEX IX_dashboard_calendario_data ON core.dashboard_calendario(data);
CREATE TABLE ctl.dashboard_calendario_publicacao (
    id tinyint PRIMARY KEY CHECK (id = 1),
    assinatura binary(32) NOT NULL
);
GO
CREATE OR ALTER PROCEDURE core.usp_dashboard_preparar_calendario
WITH EXECUTE AS OWNER
AS
BEGIN
    SET NOCOUNT ON;
    IF @@TRANCOUNT = 0 THROW 52104,'Preparar o calendario dentro da publicacao.',1;
    DECLARE @vista nvarchar(max) = OBJECT_DEFINITION(OBJECT_ID('pub.dim_calendario'));
    DECLARE @funcao nvarchar(max) = OBJECT_DEFINITION(OBJECT_ID('ref.ufn_calendar_seed_candidate_v1'));
    DECLARE @instalacao nvarchar(64) = (
        SELECT CONVERT(nvarchar(64),installed_at,127) FROM ctl.production_installation_step
        WHERE step_id = '0774-dashboard-dim-calendario'
    );
    IF @vista IS NULL OR @funcao IS NULL OR @instalacao IS NULL
        THROW 52105,'A origem do calendario precisa estar instalada.',1;
    DECLARE @assinatura binary(32) = HASHBYTES('SHA2_256',CONCAT(@vista,N'|',@funcao,N'|',@instalacao));
    IF EXISTS (SELECT 1 FROM ctl.dashboard_calendario_publicacao WITH (UPDLOCK,HOLDLOCK)
               WHERE id=1 AND assinatura=@assinatura) RETURN;
    SELECT * INTO #calendario FROM pub.dim_calendario;
    IF NOT EXISTS (SELECT 1 FROM #calendario)
        THROW 52106,'Calendario de origem vazio.',1;
    DELETE FROM core.dashboard_calendario;
    INSERT core.dashboard_calendario SELECT * FROM #calendario;
    UPDATE ctl.dashboard_calendario_publicacao SET assinatura=@assinatura WHERE id=1;
    IF @@ROWCOUNT=0 INSERT ctl.dashboard_calendario_publicacao(id,assinatura) VALUES(1,@assinatura);
END;
GO
CREATE OR ALTER VIEW pub.dim_calendario_preparada AS SELECT * FROM core.dashboard_calendario;
GO
EXEC core.usp_dashboard_preparar_calendario;
DECLARE @ddl nvarchar(max)=OBJECT_DEFINITION(OBJECT_ID('core.usp_dashboard_publicar_fatos_fretes'));
DECLARE @at int=CHARINDEX(N'@audit nvarchar(max)',@ddl);
IF @ddl IS NULL OR @at=0 OR CHARINDEX(N'EXEC core.usp_dashboard_preparar_raster;',@ddl)=0
    THROW 52107,'Coordenador de publicacao inesperado.',1;
SET @ddl=N'CREATE OR ALTER PROCEDURE core.usp_dashboard_publicar_fatos_fretes '+SUBSTRING(@ddl,@at,LEN(@ddl));
SET @ddl=REPLACE(@ddl,N'EXEC core.usp_dashboard_preparar_raster;',N'EXEC core.usp_dashboard_preparar_raster;
        EXEC core.usp_dashboard_preparar_calendario;');
EXEC sys.sp_executesql @ddl;
