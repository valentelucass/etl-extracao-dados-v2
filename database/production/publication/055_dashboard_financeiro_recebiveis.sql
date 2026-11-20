IF DB_NAME() <> 'ETL_SISTEMA_V2' THROW 52103,'Banco de publicacao incorreto.',1;
IF OBJECT_ID('core.dashboard_financeiro_rateio','U') IS NULL
    CREATE TABLE core.dashboard_financeiro_rateio(
        unique_id nvarchar(220) NOT NULL PRIMARY KEY, data_servico_frete date NULL,
        data_financeira date NULL, baixa_titulo date NULL, cnpj_parcela nvarchar(50) NULL,
        peso_acumulado decimal(19,4) NULL,peso_atual decimal(19,4) NULL,peso_total decimal(19,4) NULL,
        valor_faturado_rateado decimal(19,2) NOT NULL, valor_a_faturar decimal(19,2) NOT NULL,
        documento nvarchar(100) NULL, emissao date NULL);
GO
CREATE OR ALTER PROCEDURE core.usp_dashboard_preparar_rateio_recebiveis AS
BEGIN
    SET NOCOUNT ON;
    IF @@TRANCOUNT=0 THROW 52104,'Publicar o rateio junto com as parcelas.',1;
    ;WITH documentos AS (
        SELECT documento,emissao,cliente_cnpj,CASE WHEN COUNT(*)=COUNT(liquidacao) THEN MAX(liquidacao) END AS liquidacao
        FROM core.dashboard_parcelas_recebiveis GROUP BY documento,emissao,cliente_cnpj
    ), chaves AS (
        SELECT *, COUNT(*) OVER(PARTITION BY documento,emissao) AS clientes FROM documentos
    ), pesos AS (
        SELECT f.unique_id, f.documento_fatura, f.data_base_prazo, f.cliente_cnpj, f.data_emissao_cte_date, f.valor_frete,
               COALESCE(MAX(COALESCE(f.valor_fit_ant,f.valor_fatura)) OVER(PARTITION BY f.cliente_chave,f.documento_fatura,f.data_base_prazo),
                   SUM(COALESCE(f.valor_frete,0)) OVER(PARTITION BY f.cliente_chave,f.documento_fatura,f.data_base_prazo)) nominal,
               SUM(COALESCE(f.valor_frete,0)) OVER(PARTITION BY f.cliente_chave,f.documento_fatura,f.data_base_prazo) soma,
               SUM(COALESCE(f.valor_frete,0)) OVER(PARTITION BY f.cliente_chave,f.documento_fatura,f.data_base_prazo
                   ORDER BY f.unique_id ROWS UNBOUNDED PRECEDING) acumulado,
               COUNT_BIG(*) OVER(PARTITION BY f.cliente_chave,f.documento_fatura,f.data_base_prazo) quantidade,
               ROW_NUMBER() OVER(PARTITION BY f.cliente_chave,f.documento_fatura,f.data_base_prazo ORDER BY f.unique_id) posicao
        FROM pub.fato_gestao_vista_faturas_preparada f WHERE f.excluido_na_origem=0 AND f.documento_fatura IS NOT NULL
    )
    SELECT f.unique_id, CONVERT(date,registro.servico) data_servico_frete, f.data_emissao_cte_date data_financeira,
           c.liquidacao baixa_titulo,c.cliente_cnpj cnpj_parcela,p.acumulado peso_acumulado,p.atual peso_atual,p.total peso_total,
           CONVERT(decimal(19,2), ROUND(f.nominal*p.acumulado/p.total,2)-ROUND(f.nominal*(p.acumulado-p.atual)/p.total,2)) valor_faturado_rateado,
           CONVERT(decimal(19,2),0) valor_a_faturar,f.documento_fatura documento,f.data_base_prazo emissao
    INTO #rateio
    FROM pesos f
    LEFT JOIN pub.vw_faturas_por_cliente_powerbi_preparada o ON o.[ID Único]=f.unique_id
    OUTER APPLY OPENJSON(CASE WHEN ISJSON(o.[Metadata])=1 THEN o.[Metadata] END)
       WITH(servico datetimeoffset '$.service_at') registro
    LEFT JOIN chaves c ON c.documento=f.documento_fatura AND c.emissao=f.data_base_prazo
       AND (c.cliente_cnpj=f.cliente_cnpj OR (f.cliente_cnpj IS NULL AND c.clientes=1))
    CROSS APPLY (VALUES(CONVERT(decimal(19,4),CASE WHEN f.soma=0 THEN f.posicao ELSE f.acumulado END),
                        CONVERT(decimal(19,4),CASE WHEN f.soma=0 THEN 1 ELSE COALESCE(f.valor_frete,0) END),
                        CONVERT(decimal(19,4),CASE WHEN f.soma=0 THEN f.quantidade ELSE f.soma END))) p(acumulado,atual,total)
    OPTION(MAXDOP 1);
    INSERT #rateio
    SELECT f.unique_id, CONVERT(date,registro.servico),COALESCE(f.data_emissao_cte_date,CONVERT(date,registro.servico)),
        NULL,NULL,NULL,NULL,NULL,0,COALESCE(f.valor_frete,0),NULL,NULL
    FROM pub.fato_gestao_vista_faturas_preparada f
    JOIN pub.vw_faturas_por_cliente_powerbi_preparada o ON o.[ID Único]=f.unique_id
    CROSS APPLY OPENJSON(CASE WHEN ISJSON(o.[Metadata])=1 THEN o.[Metadata] END)
       WITH(id bigint '$.id',servico datetimeoffset '$.service_at',cortesia bit '$.courtesy',status_frete nvarchar(64) '$.status') registro
    LEFT JOIN pub.fato_fretes_faturamento_preparada frete ON frete.frete_id=registro.id
    WHERE f.excluido_na_origem=0 AND f.documento_fatura IS NULL AND
      ((frete.excluido_na_origem=0 AND frete.is_elegivel_faturamento=1) OR
       (frete.frete_id IS NULL AND registro.id IS NOT NULL AND registro.servico IS NOT NULL AND registro.cortesia=0
        AND NOT (COALESCE(f.classificacao,N'') COLLATE Latin1_General_CI_AI LIKE N'%bloqueio%' AND
            (f.classificacao COLLATE Latin1_General_CI_AI LIKE N'%anulacao%' OR f.classificacao COLLATE Latin1_General_CI_AI LIKE N'%isolamento%'))
        AND COALESCE(f.status_cte,N'') COLLATE Latin1_General_CI_AI NOT LIKE N'%cancel%'
        AND COALESCE(f.status_cte_result,N'') COLLATE Latin1_General_CI_AI NOT LIKE N'%cancel%'
        AND COALESCE(registro.status_frete,N'') COLLATE Latin1_General_CI_AI NOT LIKE N'cancel%'))
    OPTION(MAXDOP 1);
    DELETE FROM core.dashboard_financeiro_rateio;
    INSERT core.dashboard_financeiro_rateio(unique_id,data_servico_frete,data_financeira,baixa_titulo,cnpj_parcela,
        peso_acumulado,peso_atual,peso_total,valor_faturado_rateado,valor_a_faturar,documento,emissao)
    SELECT unique_id,data_servico_frete,data_financeira,baixa_titulo,cnpj_parcela,peso_acumulado,peso_atual,peso_total,
        valor_faturado_rateado,valor_a_faturar,documento,emissao FROM #rateio;
END;
GO
CREATE OR ALTER VIEW pub.dashboard_financeiro_fretes AS
SELECT f.*,r.data_servico_frete,r.data_financeira,r.baixa_titulo,r.valor_faturado_rateado,r.valor_a_faturar,
       r.cnpj_parcela,r.peso_acumulado,r.peso_atual,r.peso_total
FROM pub.fato_gestao_vista_faturas_preparada f JOIN core.dashboard_financeiro_rateio r ON r.unique_id=f.unique_id
WHERE f.excluido_na_origem=0 AND (r.documento=f.documento_fatura OR (r.documento IS NULL AND f.documento_fatura IS NULL));
