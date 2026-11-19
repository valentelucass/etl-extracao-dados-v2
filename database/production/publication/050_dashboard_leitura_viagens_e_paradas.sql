SELECT TOP (0) * INTO core.dashboard_raster_viagens FROM pub.raster_viagens;
SELECT TOP (0) * INTO core.dashboard_raster_paradas FROM pub.raster_viagem_paradas;
CREATE INDEX IX_raster_viagens_prev ON core.dashboard_raster_viagens(data_hora_prev_ini,cod_solicitacao);
CREATE INDEX IX_raster_viagens_real ON core.dashboard_raster_viagens(data_hora_real_ini,cod_solicitacao);
CREATE INDEX IX_raster_paradas_solicitacao ON core.dashboard_raster_paradas(cod_solicitacao) INCLUDE(data_extracao);
CREATE TABLE ctl.dashboard_raster_publicacao(id tinyint PRIMARY KEY CHECK(id=1),capturas bigint NOT NULL,ultima bigint NOT NULL);
GO
CREATE OR ALTER VIEW core.dashboard_raster_pendente AS
SELECT COUNT_BIG(*) AS capturas, ISNULL(MAX(capture_id),0) AS ultima
FROM ctl.dashboard_auxiliary_capture WHERE source_name='RASTER'
EXCEPT SELECT capturas,ultima FROM ctl.dashboard_raster_publicacao WHERE id=1;
GO
CREATE OR ALTER PROCEDURE core.usp_dashboard_preparar_raster AS
BEGIN
    SET NOCOUNT ON;
    IF @@TRANCOUNT=0 THROW 52102,'Preparar Raster dentro da publicacao.',1;
    DECLARE @capturas bigint,@ultima bigint;
    SELECT @capturas=capturas,@ultima=ultima FROM core.dashboard_raster_pendente;
    IF @capturas IS NULL RETURN;
    SELECT * INTO #viagens FROM pub.raster_viagens OPTION(MAXDOP 1);
    SELECT * INTO #paradas FROM pub.raster_viagem_paradas OPTION(MAXDOP 1);
    IF EXISTS(SELECT COUNT_BIG(*),ISNULL(MAX(capture_id),0) FROM ctl.dashboard_auxiliary_capture WITH(HOLDLOCK)
              WHERE source_name='RASTER' EXCEPT SELECT @capturas,@ultima) RETURN;
    DELETE FROM core.dashboard_raster_viagens;
    INSERT core.dashboard_raster_viagens SELECT * FROM #viagens;
    DELETE FROM core.dashboard_raster_paradas;
    INSERT core.dashboard_raster_paradas SELECT * FROM #paradas;
    DELETE FROM ctl.dashboard_raster_publicacao WHERE id=1;
    INSERT ctl.dashboard_raster_publicacao VALUES(1,@capturas,@ultima);
    DELETE FROM ctl.dashboard_leituras_publicadas WHERE versao=50;
    INSERT ctl.dashboard_leituras_publicadas VALUES(50,SYSUTCDATETIME());
END;
GO
-- Entre a chegada de uma captura independente e a atualização do resumo,
-- a leitura usa imediatamente as viagens e paradas da captura mais recente.
CREATE OR ALTER VIEW pub.raster_viagens_preparada AS
SELECT v.* FROM core.dashboard_raster_viagens v WHERE NOT EXISTS(SELECT 1 FROM core.dashboard_raster_pendente)
UNION ALL SELECT v.* FROM pub.raster_viagens v WHERE EXISTS(SELECT 1 FROM core.dashboard_raster_pendente);
GO
CREATE OR ALTER VIEW pub.raster_viagem_paradas_preparada AS
SELECT p.* FROM core.dashboard_raster_paradas p WHERE NOT EXISTS(SELECT 1 FROM core.dashboard_raster_pendente)
UNION ALL SELECT p.* FROM pub.raster_viagem_paradas p WHERE EXISTS(SELECT 1 FROM core.dashboard_raster_pendente);
GO
DECLARE @ddl nvarchar(max)=OBJECT_DEFINITION(OBJECT_ID('core.usp_dashboard_publicar_fatos_fretes'));
DECLARE @at int=CHARINDEX(N'@audit nvarchar(max)',@ddl);
IF @ddl IS NULL OR @at=0 OR CHARINDEX(N'EXEC core.usp_dashboard_preparar_dimensoes;',@ddl)=0
    THROW 52103,'Coordenador de publicacao inesperado.',1;
SET @ddl=N'CREATE OR ALTER PROCEDURE core.usp_dashboard_publicar_fatos_fretes '+SUBSTRING(@ddl,@at,LEN(@ddl));
SET @ddl=REPLACE(@ddl,N'EXEC core.usp_dashboard_preparar_dimensoes;',N'EXEC core.usp_dashboard_preparar_dimensoes;
        EXEC core.usp_dashboard_preparar_raster;');
EXEC sys.sp_executesql @ddl;