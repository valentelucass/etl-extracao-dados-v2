SET XACT_ABORT ON;
IF DB_NAME() <> N'ETL_SISTEMA_V2' THROW 52150, 'Banco de publicação incorreto.', 1;
IF OBJECT_ID('core.dashboard_rede_agentes_coleta', 'U') IS NULL
BEGIN
    CREATE TABLE core.dashboard_rede_agentes_coleta (
        coleta_id bigint NOT NULL PRIMARY KEY, solicitacao date NOT NULL,
        tem_agente bit NOT NULL, documento varchar(32) NULL
    );
    CREATE INDEX IX_rede_agentes_solicitacao ON core.dashboard_rede_agentes_coleta(solicitacao);
    CREATE TABLE ctl.dashboard_rede_agentes_dias (
        dia date NOT NULL PRIMARY KEY, conferido_utc datetime2 NOT NULL
    );
END;
GO
CREATE OR ALTER VIEW pub.rede_agentes_coleta AS
SELECT coleta_id, tem_agente, documento FROM core.dashboard_rede_agentes_coleta;
GO
CREATE OR ALTER PROCEDURE core.usp_rede_dias_agentes_coleta AS
SELECT c.business_date AS dia
FROM ctl.production_ingestion_coverage c
LEFT JOIN ctl.dashboard_rede_agentes_dias d ON d.dia = c.business_date
WHERE c.entity_name = 'COLETAS'
GROUP BY c.business_date, d.conferido_utc
HAVING d.conferido_utc IS NULL OR MAX(c.stored_utc) > d.conferido_utc
    OR d.conferido_utc < DATEADD(day,-7,SYSUTCDATETIME())
ORDER BY dia;
GO
CREATE OR ALTER PROCEDURE core.usp_rede_publicar_agentes_coleta
    @inicio date, @fim date, @total int, @linhas nvarchar(max)
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    IF @inicio IS NULL OR @fim IS NULL OR DATEDIFF(day,@inicio,@fim) NOT BETWEEN 0 AND 6
        OR @total IS NULL OR @total < 0 OR ISNULL(ISJSON(@linhas),0) = 0
        THROW 52151, 'Período ou conjunto de coletas inválido.', 1;
    DECLARE @fonte TABLE (id bigint PRIMARY KEY, dia date NOT NULL, agente bit NOT NULL, documento varchar(32));
    INSERT @fonte SELECT id, dia, agente, documento FROM OPENJSON(@linhas)
        WITH(id bigint, dia date, agente bit, documento varchar(32));
    IF (SELECT COUNT(*) FROM @fonte) <> @total OR EXISTS
        (SELECT 1 FROM @fonte WHERE dia NOT BETWEEN @inicio AND @fim OR (agente = 0 AND documento IS NOT NULL))
        THROW 52152, 'Coletas divergentes do total do relatório.', 1;
    BEGIN TRANSACTION;
    UPDATE a SET solicitacao = f.dia, tem_agente = f.agente, documento = f.documento
    FROM core.dashboard_rede_agentes_coleta a JOIN @fonte f ON f.id = a.coleta_id;
    INSERT core.dashboard_rede_agentes_coleta(coleta_id,solicitacao,tem_agente,documento)
    SELECT id,dia,agente,documento FROM @fonte f
    WHERE NOT EXISTS (SELECT 1 FROM core.dashboard_rede_agentes_coleta a WHERE a.coleta_id=f.id);
    DELETE a FROM core.dashboard_rede_agentes_coleta a WHERE solicitacao BETWEEN @inicio AND @fim
        AND NOT EXISTS (SELECT 1 FROM @fonte f WHERE f.id = a.coleta_id);
    DELETE FROM ctl.dashboard_rede_agentes_dias WHERE dia BETWEEN @inicio AND @fim;
    INSERT ctl.dashboard_rede_agentes_dias(dia,conferido_utc)
    SELECT DISTINCT business_date,SYSUTCDATETIME() FROM ctl.production_ingestion_coverage
    WHERE entity_name='COLETAS' AND business_date BETWEEN @inicio AND @fim;
    COMMIT;
END;
GO
GRANT EXECUTE ON OBJECT::core.usp_rede_publicar_agentes_coleta TO etl_v2_runtime_prod;
GRANT EXECUTE ON OBJECT::core.usp_rede_dias_agentes_coleta TO etl_v2_runtime_prod;
