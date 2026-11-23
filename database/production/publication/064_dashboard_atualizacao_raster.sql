IF DB_NAME() <> 'ETL_SISTEMA_V2' THROW 52160,'Base de recebimento Raster incorreta.',1;
IF OBJECT_ID('ctl.dashboard_raster_dias','U') IS NULL
    CREATE TABLE ctl.dashboard_raster_dias(
        dia date NOT NULL PRIMARY KEY,
        conferido_utc datetime2(3) NOT NULL,
        viagens int NOT NULL CHECK(viagens BETWEEN 0 AND 499)
    );
GO
CREATE OR ALTER PROCEDURE core.usp_dashboard_dias_raster AS
BEGIN
    SET NOCOUNT ON;
    DECLARE @hoje date=CONVERT(date,SYSDATETIMEOFFSET() AT TIME ZONE 'E. South America Standard Time');
    DECLARE @primeiro date=(SELECT MIN(business_date) FROM ctl.dashboard_auxiliary_capture WHERE source_name='RASTER');
    SET @primeiro=DATEFROMPARTS(YEAR(ISNULL(@primeiro,@hoje)),MONTH(ISNULL(@primeiro,@hoje)),1);
    ;WITH dias AS (
        SELECT @primeiro AS dia
        UNION ALL SELECT DATEADD(day,1,dia) FROM dias WHERE dia<@hoje
    )
    SELECT TOP (16) d.dia FROM dias d
    LEFT JOIN ctl.dashboard_raster_dias c ON c.dia=d.dia
    WHERE d.dia>=DATEADD(day,-1,@hoje)
       OR (c.dia IS NULL AND NOT EXISTS(SELECT 1 FROM ctl.dashboard_auxiliary_capture a
                                       WHERE a.source_name='RASTER' AND a.business_date=d.dia))
       OR CONVERT(date,(c.conferido_utc AT TIME ZONE 'UTC') AT TIME ZONE 'E. South America Standard Time')<=d.dia
    ORDER BY CASE WHEN d.dia>=DATEADD(day,-1,@hoje) THEN 0 ELSE 1 END, d.dia
    OPTION(MAXRECURSION 0);
END;
GO
CREATE OR ALTER PROCEDURE core.usp_dashboard_receber_dia_raster
    @dia date, @observado datetimeoffset(3), @total int, @registros nvarchar(max)
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    IF @dia IS NULL OR @observado IS NULL OR @total IS NULL OR @total NOT BETWEEN 0 AND 499
       OR ISNULL(ISJSON(@registros,ARRAY),0)<>1 OR (SELECT COUNT(*) FROM OPENJSON(@registros))<>@total
        THROW 52161,'A captura Raster esta incompleta.',1;
    DECLARE @instante datetime2(3)=CONVERT(datetime2(3),SWITCHOFFSET(@observado,'+00:00'));
    DECLARE @novoHash binary(32)=HASHBYTES('SHA2_256',@registros), @ultimoHash binary(32), @ultimaCaptura datetime2(3), @conferido datetime2(3);
    BEGIN TRY
        BEGIN TRANSACTION;
        SELECT @conferido=conferido_utc FROM ctl.dashboard_raster_dias WITH(UPDLOCK,HOLDLOCK) WHERE dia=@dia;
        SELECT TOP (1) @ultimoHash=payload_hash,@ultimaCaptura=observed_utc
        FROM ctl.dashboard_auxiliary_capture WITH(UPDLOCK,HOLDLOCK)
        WHERE source_name='RASTER' AND business_date=@dia ORDER BY observed_utc DESC,capture_id DESC;
        IF @instante>=ISNULL(@ultimaCaptura,@instante) AND @instante>=ISNULL(@conferido,@instante)
        BEGIN
            IF @total=0 AND @ultimaCaptura IS NOT NULL
                THROW 52162,'O Raster devolveu um dia vazio que ja continha viagens.',1;
            IF @total>0 AND (@ultimoHash IS NULL OR @novoHash<>@ultimoHash)
                EXEC ctl.usp_store_dashboard_auxiliary 'RASTER',@dia,@observado,@total,@registros;
            IF @conferido IS NULL
                INSERT ctl.dashboard_raster_dias(dia,conferido_utc,viagens) VALUES(@dia,@instante,@total);
            ELSE UPDATE ctl.dashboard_raster_dias SET conferido_utc=@instante,viagens=@total WHERE dia=@dia;
        END;
        COMMIT;
    END TRY
    BEGIN CATCH
        IF XACT_STATE()<>0 ROLLBACK;
        THROW;
    END CATCH;
END;
GO
CREATE OR ALTER PROCEDURE core.usp_dashboard_atualizar_leitura_raster AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    BEGIN TRY
        BEGIN TRANSACTION;
        EXEC core.usp_dashboard_preparar_raster;
        COMMIT;
    END TRY
    BEGIN CATCH
        IF XACT_STATE()<>0 ROLLBACK;
        THROW;
    END CATCH;
END;
GO
GRANT EXECUTE ON OBJECT::core.usp_dashboard_dias_raster TO etl_v2_runtime_prod;
GRANT EXECUTE ON OBJECT::core.usp_dashboard_receber_dia_raster TO etl_v2_runtime_prod;
GRANT EXECUTE ON OBJECT::core.usp_dashboard_atualizar_leitura_raster TO etl_v2_runtime_prod;
