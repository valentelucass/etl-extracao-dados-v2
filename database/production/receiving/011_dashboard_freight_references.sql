CREATE TABLE ref.dashboard_frete_filial_documento (
    filial_id bigint NOT NULL PRIMARY KEY,
    documento nvarchar(50) NOT NULL
);

CREATE TABLE ref.dashboard_frete_filial_nome (
    filial_nome_key nvarchar(255) COLLATE Latin1_General_CI_AS NOT NULL PRIMARY KEY,
    filial_id bigint NOT NULL REFERENCES ref.dashboard_frete_filial_documento(filial_id)
);

CREATE TABLE ref.dashboard_faturamento_atribuicao_filial (
    pagador_documento_key nvarchar(50) COLLATE Latin1_General_CI_AS NOT NULL PRIMARY KEY,
    filial_destino_nome nvarchar(255) NULL,
    filial_destino_key nvarchar(255) NULL
);

CREATE TABLE ref.dashboard_frete_cidade_destino (
    cidade_key nvarchar(255) COLLATE Latin1_General_CI_AS NOT NULL,
    uf nvarchar(10) COLLATE Latin1_General_CI_AS NOT NULL,
    cidade_id bigint NOT NULL,
    PRIMARY KEY (cidade_key,uf)
);

IF EXISTS (
    SELECT id_corporacao
    FROM ETL_SISTEMA.dbo.fretes
    WHERE id_corporacao IS NOT NULL AND COALESCE(excluido_na_origem,0)=0
    GROUP BY id_corporacao
    HAVING COUNT(DISTINCT NULLIF(TRIM(filial_cnpj),N''))>1
)
    THROW 52116,'DASHBOARD_FREIGHT_BRANCH_DOCUMENT_CONFLICT',1;

INSERT ref.dashboard_frete_filial_documento(filial_id,documento)
SELECT id_corporacao,MAX(NULLIF(TRIM(filial_cnpj),N''))
FROM ETL_SISTEMA.dbo.fretes
WHERE id_corporacao IS NOT NULL AND COALESCE(excluido_na_origem,0)=0
GROUP BY id_corporacao
HAVING MAX(NULLIF(TRIM(filial_cnpj),N'')) IS NOT NULL;

IF EXISTS (
    SELECT LOWER(TRIM(filial_nome))
    FROM ETL_SISTEMA.dbo.fretes
    WHERE id_corporacao IS NOT NULL AND NULLIF(TRIM(filial_nome),N'') IS NOT NULL
      AND COALESCE(excluido_na_origem,0)=0
    GROUP BY LOWER(TRIM(filial_nome))
    HAVING COUNT(DISTINCT id_corporacao)>1
)
    THROW 52117,'DASHBOARD_FREIGHT_BRANCH_NAME_CONFLICT',1;

INSERT ref.dashboard_frete_filial_nome(filial_nome_key,filial_id)
SELECT LOWER(TRIM(f.filial_nome)),MIN(f.id_corporacao)
FROM ETL_SISTEMA.dbo.fretes f
JOIN ref.dashboard_frete_filial_documento b ON b.filial_id=f.id_corporacao
WHERE NULLIF(TRIM(f.filial_nome),N'') IS NOT NULL AND COALESCE(f.excluido_na_origem,0)=0
GROUP BY LOWER(TRIM(f.filial_nome));

INSERT ref.dashboard_faturamento_atribuicao_filial(pagador_documento_key,filial_destino_nome,filial_destino_key)
SELECT pagador_documento_key,filial_destino_nome,filial_destino_key
FROM ETL_SISTEMA.dbo.regras_atribuicao_filial
WHERE ativo=1;

INSERT ref.dashboard_frete_cidade_destino(cidade_key,uf,cidade_id)
SELECT LOWER(TRIM(destino_cidade)),UPPER(TRIM(destino_uf)),MIN(id_cidade_destino)
FROM ETL_SISTEMA.dbo.fretes
WHERE id_cidade_destino IS NOT NULL AND NULLIF(TRIM(destino_cidade),N'') IS NOT NULL
    AND NULLIF(TRIM(destino_uf),N'') IS NOT NULL
GROUP BY LOWER(TRIM(destino_cidade)),UPPER(TRIM(destino_uf))
HAVING COUNT(DISTINCT id_cidade_destino)=1;
