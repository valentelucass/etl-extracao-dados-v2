-- Active, fully captured rows only. Use the V2 receipt time as the snapshot.
CREATE OR ALTER VIEW pub.fato_gestao_vista_faturas
AS
WITH normalized AS (
    SELECT f.*,
           CASE WHEN LOWER(NULLIF(TRIM(f.fit_ant_document),N'')) NOT IN (N'faturado',N'aguardando faturamento')
                THEN NULLIF(TRIM(f.fit_ant_document),N'') END AS documento_limpo,
           UPPER(NULLIF(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(
               TRIM(f.pagador_documento),N'.',N''),N'-',N''),N'/',N''),N' ',N''),NCHAR(9),N''),NCHAR(160),N''),N'')) AS pagador_key,
           COALESCE(f.fit_ant_issue_date,f.data_emissao_fatura,CONVERT(date,f.data_emissao_cte)) AS referencia_mensal,
           CONVERT(datetime2(0),f.snapshot_utc) AS snapshot
    FROM core.dashboard_faturas_vigentes f
)
SELECT f.unique_id,
       CONVERT(nvarchar(220),N'linha|'+f.unique_id) AS chave_normalizacao,
       CONVERT(nvarchar(100),f.documento_limpo) AS documento_fatura,
       CONVERT(nvarchar(50),NULLIF(TRIM(f.numero_fatura),N'')) AS numero_fatura,
       CONVERT(nvarchar(50),COALESCE(f.numero_cte,f.numero_nfse)) AS numero_documento,
       f.numero_cte,
       CONVERT(nvarchar(100),NULLIF(TRIM(f.chave_cte),N'')) AS chave_cte,
       f.numero_nfse,
       f.serie_nfse,
       f.data_emissao_cte,
       CONVERT(date,f.data_emissao_cte) AS data_emissao_cte_date,
       f.data_emissao_fatura,
       YEAR(f.data_emissao_fatura)*100+MONTH(f.data_emissao_fatura) AS data_emissao_fatura_yyyymm,
       f.data_vencimento_fatura,
       f.data_baixa_fatura,
       COALESCE(f.fit_ant_issue_date,f.data_emissao_fatura) AS data_base_prazo,
       f.referencia_mensal AS data_referencia_mensal,
       YEAR(f.referencia_mensal)*100+MONTH(f.referencia_mensal) AS data_referencia_mensal_yyyymm,
       f.fit_ant_ils_original_due_date,
       CONVERT(nvarchar(255),NULLIF(TRIM(f.filial),N'')) AS filial,
       CONVERT(nvarchar(255),NULLIF(LOWER(TRIM(f.filial)),N'')) AS filial_key,
       CONVERT(nvarchar(50),NULLIF(TRIM(f.estado),N'')) AS estado,
       CONVERT(nvarchar(255),NULLIF(TRIM(f.pagador_nome),N'')) AS pagador_nome,
       CONVERT(nvarchar(50),NULLIF(TRIM(f.pagador_documento),N'')) AS pagador_documento,
       CONVERT(nvarchar(50),f.pagador_key) AS pagador_documento_key,
       CONVERT(nvarchar(255),COALESCE(NULLIF(TRIM(f.pagador_nome),N''),f.cliente_cnpj)) AS cliente_nome,
       CONVERT(nvarchar(50),f.cliente_cnpj) AS cliente_cnpj,
       CONVERT(nvarchar(50),f.cliente_cnpj) AS cliente_cnpj_key,
       CONVERT(nvarchar(320),CASE WHEN f.cliente_cnpj IS NOT NULL THEN N'cnpj:'+f.cliente_cnpj
           WHEN NULLIF(TRIM(f.pagador_nome),N'') IS NOT NULL THEN N'nome:'+LOWER(TRIM(f.pagador_nome)) END) AS cliente_chave,
       CONVERT(nvarchar(255),NULLIF(TRIM(f.remetente_nome),N'')) AS remetente_nome,
       CONVERT(nvarchar(50),NULLIF(TRIM(f.remetente_documento),N'')) AS remetente_documento,
       CONVERT(nvarchar(255),NULLIF(TRIM(f.destinatario_nome),N'')) AS destinatario_nome,
       CONVERT(nvarchar(50),NULLIF(TRIM(f.destinatario_documento),N'')) AS destinatario_documento,
       CONVERT(nvarchar(255),NULLIF(TRIM(f.vendedor_nome),N'')) AS vendedor_nome,
       CONVERT(nvarchar(50),CASE WHEN f.documento_limpo IS NOT NULL THEN N'Faturado' ELSE N'Aguardando Faturamento' END) AS status_processo,
       CONVERT(nvarchar(50),CASE WHEN f.documento_limpo IS NULL THEN N'sem_fatura'
            WHEN f.data_baixa_fatura IS NOT NULL THEN N'baixado'
            WHEN f.data_vencimento_fatura IS NULL THEN N'sem_vencimento'
            WHEN f.data_vencimento_fatura<CONVERT(date,f.snapshot) THEN N'vencido' ELSE N'a_vencer' END) AS status_pagamento,
       CONVERT(nvarchar(255),NULLIF(TRIM(f.status_cte),N'')) AS status_cte,
       CONVERT(nvarchar(4000),NULLIF(TRIM(f.status_cte_result),N'')) AS status_cte_result,
       CONVERT(nvarchar(100),NULLIF(TRIM(f.tipo_frete),N'')) AS tipo_frete,
       CONVERT(nvarchar(100),NULLIF(TRIM(f.classificacao),N'')) AS classificacao,
       CONVERT(decimal(19,4),f.valor_frete) AS valor_frete,
       CONVERT(decimal(19,4),f.valor_fatura) AS valor_fatura,
       CONVERT(decimal(19,4),f.fit_ant_value) AS valor_fit_ant,
       CONVERT(decimal(19,4),f.third_party_ctes_value) AS third_party_ctes_value,
       COALESCE(CONVERT(decimal(19,4),f.fit_ant_value),CONVERT(decimal(19,4),f.valor_fatura),
                CONVERT(decimal(19,4),f.valor_frete),CONVERT(decimal(19,4),0)) AS valor_operacional,
       CONVERT(nvarchar(4000),NULLIF(TRIM(f.notas_fiscais),N'')) AS notas_fiscais,
       CONVERT(nvarchar(4000),NULLIF(TRIM(f.pedidos_cliente),N'')) AS pedidos_cliente,
       CONVERT(nvarchar(4000),NULLIF(TRIM(f.metadata),N'')) AS metadata,
       CONVERT(bit,0) AS excluido_na_origem,
       CONVERT(datetime2(0),NULL) AS data_exclusao_origem,
       f.snapshot AS ultima_reconciliacao_origem_em,
       CONVERT(datetime2(0),f.data_extracao) AS data_extracao_origem,
       f.snapshot AS snapshot_em,
       CONVERT(binary(32),HASHBYTES('SHA2_256',CONVERT(varbinary(max),CONCAT(
           f.unique_id,N'|',f.metadata,N'|',CONVERT(nvarchar(30),f.snapshot,126))))) AS hash_linha
FROM normalized f;
