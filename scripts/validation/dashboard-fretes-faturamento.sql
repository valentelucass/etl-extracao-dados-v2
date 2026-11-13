SET NOCOUNT ON;

DECLARE @freights bigint,@distinct bigint,@eligible bigint,@invalidAmount bigint,@documents bigint,@detail bigint;
BEGIN TRY
    EXECUTE AS USER=N'usuario_etl';
    SELECT @freights=COUNT_BIG(*),@distinct=COUNT_BIG(DISTINCT frete_id),
           @eligible=COUNT_BIG(CASE WHEN is_elegivel_faturamento=1 THEN 1 END),
           @invalidAmount=COUNT_BIG(CASE WHEN is_elegivel_faturamento=0 AND (valor_frete<>0 OR receita_bruta<>0) THEN 1 END),
           @documents=COUNT_BIG(filial_cnpj)
    FROM pub.fato_fretes_faturamento OPTION (MAXDOP 2);
    SELECT @detail=COUNT_BIG(*) FROM pub.vw_fretes_powerbi OPTION (MAXDOP 2);
    REVERT;
END TRY
BEGIN CATCH
    IF USER_NAME()=N'usuario_etl' REVERT;
    THROW;
END CATCH;

IF @freights=0 OR @freights<>@distinct OR @eligible=0 OR @invalidAmount<>0 OR @documents=0 OR @detail=0
    THROW 52118,'DASHBOARD_FREIGHT_PUBLICATION_INVALID',1;

IF EXISTS (SELECT 1 FROM sys.sql_expression_dependencies WHERE referencing_id IN (
    OBJECT_ID('core.dashboard_fretes_publicacao','V'),
    OBJECT_ID('pub.vw_fretes_powerbi','V'),OBJECT_ID('pub.fato_fretes_faturamento','V'))
    AND (referenced_database_name IS NOT NULL OR referenced_server_name IS NOT NULL))
    THROW 52119,'DASHBOARD_FREIGHT_PUBLICATION_EXTERNAL_DEPENDENCY',1;

SELECT @freights AS billingRows,@distinct AS uniqueFreightIds,@eligible AS eligibleRows,
       @detail AS operationalRows,@documents AS withIssuingBranchDocument
FOR JSON PATH,WITHOUT_ARRAY_WRAPPER;
