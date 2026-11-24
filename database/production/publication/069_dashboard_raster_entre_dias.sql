IF DB_NAME() <> N'ETL_SISTEMA_V2' THROW 52220,'Banco de publicacao Raster incorreto.',1;
IF OBJECT_ID('core.dashboard_raster_entre_dias','U') IS NULL
    CREATE TABLE core.dashboard_raster_entre_dias (
        source_key bigint NOT NULL PRIMARY KEY,
        record_json nvarchar(max) NOT NULL,
        payload_hash binary(32) NOT NULL,
        observed_utc datetime2(3) NOT NULL,
        conferido_utc datetime2(3) NOT NULL
    );
IF OBJECT_ID('ctl.dashboard_raster_revisao','U') IS NULL
BEGIN
    CREATE TABLE ctl.dashboard_raster_revisao (
        id tinyint NOT NULL PRIMARY KEY CHECK(id=1),
        recebida bigint NOT NULL, preparada bigint NOT NULL
    );
    INSERT ctl.dashboard_raster_revisao VALUES(1,0,0);
END;
GO
CREATE OR ALTER PROCEDURE core.usp_dashboard_filiais_raster AS
BEGIN
    SET NOCOUNT ON;
    -- O cadastro do ponto Raster pode usar documentos diferentes nos dois sentidos.
    SELECT DISTINCT e.cnpj
    FROM core.dashboard_raster_viagens v
    CROSS APPLY (VALUES(CHARINDEX(N' ATE ',v.rota_descricao COLLATE Latin1_General_CI_AI))) s(pos)
    CROSS APPLY (VALUES
        (LEFT(v.rota_descricao,NULLIF(s.pos,0)-1),v.cnpj_cliente_orig),
        (SUBSTRING(v.rota_descricao,s.pos+5,500),v.cnpj_cliente_dest)
    ) e(ponto,cnpj)
    WHERE s.pos>0 AND e.ponto COLLATE Latin1_General_CI_AI LIKE N'%RODOGARCIA%FILIAL%'
      AND LEN(e.cnpj)=14 AND e.cnpj COLLATE Latin1_General_100_BIN2 NOT LIKE N'%[^0-9]%'
    ORDER BY e.cnpj;
END;
GO
CREATE OR ALTER PROCEDURE core.usp_dashboard_receber_raster_entre_dias
    @inicio date,@fim date,@observado datetimeoffset(3),@total int,@registros nvarchar(max)
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    IF @inicio IS NULL OR @fim IS NULL OR @inicio>=@fim OR @observado IS NULL OR
       @total IS NULL OR @total NOT BETWEEN 0 AND 499 OR ISNULL(ISJSON(@registros,ARRAY),0)<>1 OR
       DATALENGTH(@registros)>20971520 OR (SELECT COUNT(*) FROM OPENJSON(@registros))<>@total OR
       EXISTS(SELECT 1 FROM OPENJSON(@registros) WHERE [type]<>5)
        THROW 52221,'Janela Raster incompleta.',1;
    IF EXISTS(SELECT 1 FROM OPENJSON(@registros) r
              GROUP BY TRY_CONVERT(bigint,JSON_VALUE(r.value,'$.CodSolicitacao'))
              HAVING ISNULL(TRY_CONVERT(bigint,JSON_VALUE(r.value,'$.CodSolicitacao')),0)<=0 OR COUNT(*)>1)
       OR EXISTS(SELECT 1 FROM OPENJSON(@registros) r CROSS APPLY OPENJSON(r.value) j
                 WHERE j.[key]='ColetasEntregas' AND j.[type] NOT IN(0,4))
        THROW 52222,'Viagem ou paradas invalidas na janela Raster.',1;
    DECLARE @instante datetime2(3)=CONVERT(datetime2(3),SWITCHOFFSET(@observado,'+00:00'));
    SELECT CONVERT(bigint,JSON_VALUE(r.value,'$.CodSolicitacao')) AS source_key,
           r.value AS record_json,HASHBYTES('SHA2_256',r.value) AS payload_hash
    INTO #confirmadas
    FROM OPENJSON(@registros) r
    CROSS APPLY (VALUES(core.dashboard_raster_time(JSON_VALUE(r.value,'$.DataHoraRealIni')),
                        core.dashboard_raster_time(JSON_VALUE(r.value,'$.DataHoraRealFim')))) t(inicio,fim)
    CROSS APPLY (VALUES(CONVERT(date,t.inicio AT TIME ZONE 'E. South America Standard Time'),
                        CONVERT(date,t.fim AT TIME ZONE 'E. South America Standard Time'))) d(inicio,fim)
    WHERE d.inicio>=@inicio AND d.fim<=@fim AND t.inicio<=t.fim AND (
        d.inicio<d.fim OR EXISTS(SELECT 1 FROM core.dashboard_raster_entre_dias anterior
            WHERE anterior.source_key=TRY_CONVERT(bigint,JSON_VALUE(r.value,'$.CodSolicitacao')))
    );
    CREATE UNIQUE CLUSTERED INDEX ix_confirmadas ON #confirmadas(source_key);
    DECLARE @alteradas int=0,@versao bigint;
    BEGIN TRY
        BEGIN TRANSACTION;
        SELECT @versao=recebida FROM ctl.dashboard_raster_revisao WITH(UPDLOCK,HOLDLOCK) WHERE id=1;
        UPDATE dest SET record_json=src.record_json,payload_hash=src.payload_hash,observed_utc=@instante,conferido_utc=@instante
        FROM core.dashboard_raster_entre_dias dest JOIN #confirmadas src ON src.source_key=dest.source_key
        WHERE dest.conferido_utc<@instante AND (dest.payload_hash<>src.payload_hash OR EXISTS(
            SELECT 1 FROM stg.dashboard_auxiliary_latest d WHERE d.source_name='RASTER' AND d.source_key=dest.source_key
              AND d.observed_utc>=dest.observed_utc AND d.observed_utc<@instante AND HASHBYTES('SHA2_256',d.record_json)<>src.payload_hash));
        SET @alteradas=@@ROWCOUNT;
        UPDATE dest SET conferido_utc=@instante FROM core.dashboard_raster_entre_dias dest
        JOIN #confirmadas src ON src.source_key=dest.source_key WHERE dest.conferido_utc<@instante;
        INSERT core.dashboard_raster_entre_dias(source_key,record_json,payload_hash,observed_utc,conferido_utc)
        SELECT src.source_key,src.record_json,src.payload_hash,@instante,@instante FROM #confirmadas src
        WHERE NOT EXISTS(SELECT 1 FROM core.dashboard_raster_entre_dias dest WHERE dest.source_key=src.source_key);
        SET @alteradas+=@@ROWCOUNT;
        IF @alteradas>0 UPDATE ctl.dashboard_raster_revisao SET recebida=recebida+1 WHERE id=1;
        COMMIT;
        SELECT @alteradas;
    END TRY
    BEGIN CATCH
        IF XACT_STATE()<>0 ROLLBACK;
        THROW;
    END CATCH;
END;
GO
CREATE OR ALTER VIEW core.dashboard_raster_pendente AS
WITH atual AS (
    SELECT COUNT_BIG(*) AS capturas,ISNULL(MAX(capture_id),0) AS ultima
    FROM ctl.dashboard_auxiliary_capture WHERE source_name='RASTER'
)
SELECT a.capturas,a.ultima FROM atual a
WHERE NOT EXISTS(SELECT 1 FROM ctl.dashboard_raster_publicacao p WHERE p.id=1 AND p.capturas=a.capturas AND p.ultima=a.ultima)
   OR EXISTS(SELECT 1 FROM ctl.dashboard_raster_revisao WHERE recebida<>preparada);
GO
CREATE OR ALTER PROCEDURE core.usp_dashboard_preparar_raster AS
BEGIN
    SET NOCOUNT ON;
    IF @@TRANCOUNT=0 THROW 52102,'Preparar Raster dentro da publicacao.',1;
    DECLARE @capturas bigint,@ultima bigint,@revisao bigint;
    SELECT @revisao=recebida FROM ctl.dashboard_raster_revisao WITH(UPDLOCK,HOLDLOCK) WHERE id=1;
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
    UPDATE ctl.dashboard_raster_publicacao SET capturas=@capturas,ultima=@ultima WHERE id=1;
    IF @@ROWCOUNT=0 INSERT ctl.dashboard_raster_publicacao VALUES(1,@capturas,@ultima);
    UPDATE ctl.dashboard_raster_revisao SET preparada=@revisao WHERE id=1;
    UPDATE ctl.dashboard_leituras_publicadas SET publicado_utc=SYSUTCDATETIME() WHERE versao=50;
    IF @@ROWCOUNT=0 INSERT ctl.dashboard_leituras_publicadas VALUES(50,SYSUTCDATETIME());
END;
GO
GRANT EXECUTE ON OBJECT::core.usp_dashboard_filiais_raster TO etl_v2_runtime_prod;
GRANT EXECUTE ON OBJECT::core.usp_dashboard_receber_raster_entre_dias TO etl_v2_runtime_prod;
