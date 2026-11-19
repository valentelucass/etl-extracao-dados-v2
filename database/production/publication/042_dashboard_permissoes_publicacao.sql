IF DB_NAME() <> N'ETL_SISTEMA_V2' OR DATABASE_PRINCIPAL_ID(N'etl_v2_runtime_prod') IS NULL
    THROW 52083,'Destino ou identidade de publicacao incorretos.',1;
GRANT SELECT ON OBJECT::pub.vw_dashboard_particoes_pendentes TO etl_v2_runtime_prod;
GRANT EXECUTE ON OBJECT::core.usp_dashboard_preparar_coletas TO etl_v2_runtime_prod;
GRANT EXECUTE ON OBJECT::core.usp_dashboard_preparar_fretes_vigentes TO etl_v2_runtime_prod;
GRANT EXECUTE ON OBJECT::core.usp_dashboard_preparar_fretes_vinculos TO etl_v2_runtime_prod;
GRANT EXECUTE ON OBJECT::core.usp_dashboard_preparar_cotacoes TO etl_v2_runtime_prod;
GRANT EXECUTE ON OBJECT::core.usp_dashboard_publicar_fatos_fretes TO etl_v2_runtime_prod;