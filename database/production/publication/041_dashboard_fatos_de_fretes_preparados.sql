-- As duas consultas de origem continuam sendo o contrato de dados.
SELECT TOP (0) * INTO core.dashboard_faturamento_leitura FROM pub.fato_fretes_faturamento;
SELECT TOP (0) * INTO core.dashboard_gestao_fretes_leitura FROM pub.fato_gestao_vista_fretes;
CREATE CLUSTERED INDEX IX_dashboard_faturamento_leitura
    ON core.dashboard_faturamento_leitura(data_referencia_faturamento,frete_id);
CREATE CLUSTERED INDEX IX_dashboard_gestao_fretes_leitura
    ON core.dashboard_gestao_fretes_leitura(data_frete,numero_minuta);
CREATE TABLE ctl.dashboard_fretes_publicados (
    publicacao tinyint NOT NULL PRIMARY KEY CHECK(publicacao=1),
    concluida_utc datetime2(7) NOT NULL,
    linhas_faturamento bigint NOT NULL,
    linhas_gestao bigint NOT NULL
);
GO
CREATE OR ALTER VIEW pub.fato_fretes_faturamento_preparada AS
SELECT * FROM core.dashboard_faturamento_leitura;
GO
CREATE OR ALTER VIEW pub.fato_gestao_vista_fretes_preparada AS
SELECT * FROM core.dashboard_gestao_fretes_leitura;
GO
CREATE OR ALTER PROCEDURE core.usp_dashboard_publicar_fatos_fretes AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    IF @@TRANCOUNT<>0 THROW 52080,'Publicacao exige uma conexao sem transacao externa.',1;
    BEGIN TRY
        BEGIN TRANSACTION;
        DECLARE @lock int;
        EXEC @lock=sys.sp_getapplock @Resource=N'dashboard:fatos-fretes',@LockMode='Exclusive',
             @LockOwner='Transaction',@LockTimeout=5000;
        IF @lock<0 THROW 52081,'Publicacao de fretes em andamento.',1;
        SELECT source_scope,entity_name,business_date,latest_run_id INTO #cobertura
        FROM ctl.production_ingestion_coverage;

        SELECT * INTO #faturamento FROM pub.fato_fretes_faturamento
        OPTION(MAXDOP 1, MAX_GRANT_PERCENT=2);
        SELECT * INTO #gestao FROM pub.fato_gestao_vista_fretes
        OPTION(MAXDOP 1, MAX_GRANT_PERCENT=2);
        IF EXISTS(SELECT source_scope,entity_name,business_date,latest_run_id FROM ctl.production_ingestion_coverage WITH(HOLDLOCK)
                  EXCEPT SELECT source_scope,entity_name,business_date,latest_run_id FROM #cobertura)
        OR EXISTS(SELECT source_scope,entity_name,business_date,latest_run_id FROM #cobertura
                  EXCEPT SELECT source_scope,entity_name,business_date,latest_run_id FROM ctl.production_ingestion_coverage WITH(HOLDLOCK))
            THROW 52082,'Cobertura alterada durante a publicacao.',1;

        DELETE FROM core.dashboard_faturamento_leitura;
        INSERT core.dashboard_faturamento_leitura SELECT * FROM #faturamento;
        DECLARE @faturamento bigint=@@ROWCOUNT;
        DELETE FROM core.dashboard_gestao_fretes_leitura;
        INSERT core.dashboard_gestao_fretes_leitura SELECT * FROM #gestao;
        DECLARE @gestao bigint=@@ROWCOUNT;
        DELETE FROM ctl.dashboard_fretes_publicados WHERE publicacao=1;
        INSERT ctl.dashboard_fretes_publicados VALUES(1,SYSUTCDATETIME(),@faturamento,@gestao);
        COMMIT;
    END TRY
    BEGIN CATCH
        IF @@TRANCOUNT>0 ROLLBACK;
        THROW;
    END CATCH;
END;
