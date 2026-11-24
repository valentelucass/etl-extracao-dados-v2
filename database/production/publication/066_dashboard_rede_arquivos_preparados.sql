-- O relatório 11361 corrige um dia substituindo a captura inteira. A captura é imutável.
CREATE TABLE ctl.dashboard_arquivos_captura (
    captura uniqueidentifier NOT NULL PRIMARY KEY
);
CREATE TABLE core.dashboard_arquivos_minuta (
    captura uniqueidentifier NOT NULL REFERENCES ctl.dashboard_arquivos_captura(captura) ON DELETE CASCADE,
    minuta bigint NOT NULL,
    PRIMARY KEY (captura, minuta) WITH (DATA_COMPRESSION=PAGE)
);
GO
CREATE OR ALTER FUNCTION core.dashboard_minutas_arquivadas(@captura uniqueidentifier)
RETURNS TABLE AS RETURN
    SELECT DISTINCT d.minuta
    FROM stg.production_export_occurrence o
    CROSS APPLY OPENJSON(o.source_envelope, '$.record') WITH (
        minuta bigint '$.fag_fhs_fit_corporation_sequence_number',
        arquivo nvarchar(100) '$.sequence_code'
    ) d
    WHERE o.run_id=@captura AND d.minuta IS NOT NULL AND NULLIF(TRIM(d.arquivo),'') IS NOT NULL;
GO
CREATE OR ALTER PROCEDURE core.usp_dashboard_preparar_arquivos AS
BEGIN
    SET NOCOUNT ON;
    IF @@TRANCOUNT=0 THROW 52201,'Preparar na transacao de publicacao.',1;

    SELECT DISTINCT latest_run_id AS captura INTO #arquivos_vigentes
    FROM ctl.production_ingestion_coverage WHERE entity_name='COMPROVANTES_ENTREGA';
    CREATE UNIQUE CLUSTERED INDEX IX_vigentes ON #arquivos_vigentes(captura);

    DECLARE @novas TABLE (captura uniqueidentifier PRIMARY KEY);
    INSERT ctl.dashboard_arquivos_captura(captura) OUTPUT inserted.captura INTO @novas
    SELECT v.captura FROM #arquivos_vigentes v
    WHERE NOT EXISTS (SELECT 1 FROM ctl.dashboard_arquivos_captura p WHERE p.captura=v.captura);

    INSERT core.dashboard_arquivos_minuta(captura,minuta)
    SELECT n.captura,f.minuta FROM @novas n CROSS APPLY core.dashboard_minutas_arquivadas(n.captura) f
    OPTION (MAXDOP 1, MAX_GRANT_PERCENT=2);

    DELETE p FROM ctl.dashboard_arquivos_captura p
    WHERE NOT EXISTS (SELECT 1 FROM #arquivos_vigentes v WHERE v.captura=p.captura);
END;
GO
-- A consulta considera imediatamente inclusive as capturas recebidas depois da última publicação.
CREATE OR ALTER VIEW pub.rede_credenciada_arquivos AS
SELECT a.minuta, MIN(a.dia) AS primeiro_arquivamento
FROM (
    SELECT p.minuta,c.business_date AS dia
    FROM ctl.production_ingestion_coverage c
    JOIN core.dashboard_arquivos_minuta p ON p.captura=c.latest_run_id
    WHERE c.entity_name='COMPROVANTES_ENTREGA'
    UNION ALL
    SELECT f.minuta,c.business_date
    FROM ctl.production_ingestion_coverage c
    CROSS APPLY core.dashboard_minutas_arquivadas(c.latest_run_id) f
    WHERE c.entity_name='COMPROVANTES_ENTREGA'
      AND NOT EXISTS (SELECT 1 FROM ctl.dashboard_arquivos_captura p WHERE p.captura=c.latest_run_id)
) a
GROUP BY a.minuta;
GO
DECLARE @nome sysname=N'core.usp_dashboard_publicar_fatos_fretes';
DECLARE @ddl nvarchar(max)=OBJECT_DEFINITION(OBJECT_ID(@nome, 'P'));
DECLARE @param int=CHARINDEX(N'@audit nvarchar(max)',@ddl);
DECLARE @origem nvarchar(150)=N'EXEC core.usp_dashboard_preparar_ocorrencias;';
IF ISNULL(@param,0)=0 OR CHARINDEX(@origem,@ddl)=0
   OR CHARINDEX(@origem,@ddl,CHARINDEX(@origem,@ddl)+1)<>0
   OR CHARINDEX(N'usp_dashboard_preparar_arquivos',@ddl)<>0
    THROW 52202,'Definicao do publicador incompativel.',1;
SET @ddl=N'CREATE OR ALTER PROCEDURE '+@nome+N' '+SUBSTRING(@ddl,@param,LEN(@ddl));
SET @ddl=REPLACE(@ddl,@origem,@origem+CHAR(10)+N'        EXEC core.usp_dashboard_preparar_arquivos;');
EXEC sys.sp_executesql @ddl;
