SET XACT_ABORT ON;
IF DB_NAME() <> 'ETL_SISTEMA_V2' THROW 52100,'Banco de recebimento incorreto.',1;
IF OBJECT_ID('core.dashboard_parcelas_recebiveis','U') IS NULL
BEGIN
    CREATE TABLE core.dashboard_parcelas_recebiveis (
        parcela_id bigint NOT NULL PRIMARY KEY,
        documento nvarchar(100) NULL,
        cliente_cnpj nvarchar(50) NULL,
        emissao date NOT NULL,
        vencimento date NULL,
        liquidacao date NULL,
        valor decimal(19,2) NOT NULL
    );
    CREATE INDEX IX_parcelas_recebiveis_fatura ON core.dashboard_parcelas_recebiveis(documento,emissao,cliente_cnpj)
        INCLUDE(vencimento,liquidacao,valor);
END;
GO
CREATE OR ALTER VIEW pub.dashboard_parcelas_recebiveis AS
SELECT parcela_id,documento,cliente_cnpj,emissao,vencimento,liquidacao,valor FROM core.dashboard_parcelas_recebiveis;
GO
CREATE OR ALTER PROCEDURE core.usp_dashboard_inicio_recebiveis AS
SELECT MIN(COALESCE(fit_ant_issue_date,data_emissao_fatura)) FROM core.dashboard_faturas_vigentes
WHERE NULLIF(TRIM(fit_ant_document),'') IS NOT NULL;
GO
CREATE OR ALTER PROCEDURE core.usp_dashboard_publicar_recebiveis
    @inicio date, @fim date, @quantidade int, @valor decimal(19,2)
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    IF APPLOCK_MODE('public','dashboard_parcelas_recebiveis','Session') <> 'Exclusive'
        THROW 52101,'A coleta requer uma sessao exclusiva.',1;
    IF @inicio IS NULL OR @fim < @inicio OR @quantidade < 1 OR @valor IS NULL
        OR (SELECT COUNT_BIG(*) FROM #parcelas_esl) <> @quantidade
        OR (SELECT SUM(valor) FROM #parcelas_esl) <> @valor
        OR EXISTS(SELECT 1 FROM #parcelas_esl WHERE emissao NOT BETWEEN @inicio AND @fim)
        THROW 52102,'Parcelas incompletas na conciliacao com o ESL.',1;
    BEGIN TRAN;
    UPDATE p SET documento=s.documento, cliente_cnpj=s.cliente_cnpj, emissao=s.emissao,
       vencimento=s.vencimento,liquidacao=s.liquidacao,valor=s.valor
    FROM core.dashboard_parcelas_recebiveis p JOIN #parcelas_esl s ON p.parcela_id=s.parcela_id
    WHERE EXISTS (SELECT s.documento,s.cliente_cnpj,s.emissao,s.vencimento,s.liquidacao,s.valor
                  EXCEPT SELECT p.documento,p.cliente_cnpj,p.emissao,p.vencimento,p.liquidacao,p.valor);
    INSERT core.dashboard_parcelas_recebiveis(parcela_id,documento,cliente_cnpj,emissao,vencimento,liquidacao,valor)
    SELECT s.parcela_id,s.documento,s.cliente_cnpj,s.emissao,s.vencimento,s.liquidacao,s.valor FROM #parcelas_esl s
    WHERE NOT EXISTS(SELECT 1 FROM core.dashboard_parcelas_recebiveis p WHERE p.parcela_id=s.parcela_id);
    DELETE p FROM core.dashboard_parcelas_recebiveis p WHERE p.emissao BETWEEN @inicio AND @fim
        AND NOT EXISTS(SELECT 1 FROM #parcelas_esl s WHERE s.parcela_id=p.parcela_id);
    EXEC core.usp_dashboard_preparar_rateio_recebiveis;
    COMMIT;
END;
GO
GRANT EXECUTE ON OBJECT::core.usp_dashboard_publicar_recebiveis TO etl_v2_runtime_prod;
GRANT EXECUTE ON OBJECT::core.usp_dashboard_inicio_recebiveis TO etl_v2_runtime_prod;
