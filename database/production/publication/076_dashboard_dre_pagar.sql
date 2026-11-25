-- O relatório de conferência tem uma linha por título. O DRE também precisa das
-- apropriações por conta e centro que o ESL exporta dentro do mesmo título.
CREATE TABLE core.dashboard_dre_pagar (
    numero bigint NOT NULL, ordem int NOT NULL, documento nvarchar(100), emissao date,
    ano int, mes int, filial nvarchar(255), fornecedor nvarchar(255), centro nvarchar(255),
    conta nvarchar(255), linha_dre varchar(24), valor decimal(19,2), valor_centro decimal(19,2),
    PRIMARY KEY (numero,ordem)
);
CREATE INDEX IX_dashboard_dre_competencia ON core.dashboard_dre_pagar (ano,mes,linha_dre)
    INCLUDE (conta,filial,centro,valor,valor_centro);
GO
CREATE OR ALTER VIEW pub.dashboard_dre_pagar AS
SELECT numero,ordem,documento,emissao,ano,mes,filial,fornecedor,centro,conta,linha_dre,valor,valor_centro
FROM core.dashboard_dre_pagar;
GO
CREATE OR ALTER PROCEDURE core.usp_dashboard_preparar_dre_pagar AS
BEGIN
    SET NOCOUNT ON;
    IF @@TRANCOUNT=0 THROW 52420,'Preparar o DRE na transacao de publicacao.',1;
    DECLARE @mapa TABLE (conta nvarchar(255) COLLATE Latin1_General_100_CI_AI PRIMARY KEY,
        fixa varchar(5), operacional varchar(5), adm varchar(5), comercial varchar(5));
    INSERT @mapa VALUES
        (N'ICMS A RECOLHER','D8',NULL,NULL,NULL),(N'PIS A RECOLHER','D9',NULL,NULL,NULL),
        (N'COFINS A RECOLHER','D9',NULL,NULL,NULL),(N'CONTRIBUIÇÃO PREVIDENCIARIA A RECOLHER','D10',NULL,NULL,NULL),
        (N'ISS A RECOLHER','D11',NULL,NULL,NULL),(N'ABASTECIMENTOS ( TRIVALE E POSTOS )','D36',NULL,NULL,NULL),
        (N'SOFTWARES E LICENÇAS','D130',NULL,NULL,NULL),(N'SOFTWARE E LICENÇAS','D130',NULL,NULL,NULL),
        (N'PLANO DE SAÚDE','D143',NULL,NULL,NULL),
        (N'SALÁRIOS',NULL,'D16','D102','D178'),(N'HORAS EXTRAS',NULL,'D17','D103','D179'),
        (N'AJUDA DE CUSTOS',NULL,'D18','D104','D180'),(N'GRATIFICAÇÕES',NULL,'D19','D105','D181'),
        (N'INCENTIVO DE MOTORISTAS',NULL,'D20','D106','D182'),(N'PRÊMIOS',NULL,'D21','D107','D182'),
        (N'INCENTIVO/PREMIOS',NULL,NULL,NULL,'D182'),(N'PLR',NULL,'D22','D108','D183'),
        (N'INSS - (SENAT, SEST, SAL EDUC,INCRA,SEBRAE,RAT)',NULL,'D23','D109','D184'),
        (N'INSS',NULL,'D23','D109','D184'),(N'FGTS',NULL,'D24','D110','D185'),
        (N'FGTS - MULTA 40%',NULL,'D25','D111','D186'),(N'FÉRIAS',NULL,'D26','D112','D187'),
        (N'RESCISÕES',NULL,'D27','D113','D188'),(N'13º SALÁRIO',NULL,'D28','D114','D189'),
        (N'13º',NULL,'D28','D114','D189'),(N'CESTA BÁSICA',NULL,'D29','D115','D190'),
        (N'VALE ALIMENTAÇÃO',NULL,'D30','D116','D191'),(N'VALE COMBUSTÍVEL',NULL,'D31','D117','D192'),
        (N'VALE TRANSPORTE',NULL,'D32','D118','D193');
    -- Os títulos fora destas famílias continuam com o grão do relatório de Contas a Pagar.
    SELECT TOP(0) * INTO #dre FROM core.dashboard_dre_pagar;
    INSERT #dre
    SELECT p.[Lançamento a Pagar/N°],0,p.[N° Documento],p.[Emissão],p.[Ano de Competência],p.[Mês de Competência],
        p.[Filial],p.[Fornecedor/Nome],p.[Centro de custo/Nome],p.[Conta Contábil/Descrição],NULL,p.[Conta Contábil/Valor],
        CASE WHEN p.[Conta Contábil/Valor]=p.[Valor] THEN p.[Centro de custo/Valor]
             WHEN p.[Centro de custo/Valor]=p.[Valor] THEN p.[Conta Contábil/Valor] END
    FROM core.dashboard_contas_leitura p
    WHERE NOT EXISTS (SELECT 1 FROM @mapa m WHERE m.conta=p.[Conta Contábil/Descrição] COLLATE Latin1_General_100_CI_AI);

    SELECT p.[Lançamento a Pagar/N°] numero,l.occurrence_ordinal ordem,p.[N° Documento] documento,p.[Emissão] emissao,
            p.[Ano de Competência] ano,p.[Mês de Competência] mes,p.[Filial] filial,p.[Fornecedor/Nome] fornecedor,
            TRIM(j.centro) COLLATE Latin1_General_100_CI_AI centro,TRIM(j.obs) COLLATE Latin1_General_100_CI_AI obs,
            TRIM(j.conta) conta,m.fixa,m.operacional,m.adm,m.comercial,
            n.v,n.vc,n.vcc, COUNT_BIG(*) OVER (PARTITION BY j.numero,j.conta) parcelas,
            ROW_NUMBER() OVER (PARTITION BY j.numero,j.conta ORDER BY l.occurrence_ordinal) parcela,
            MIN(n.vc) OVER (PARTITION BY j.numero,j.conta) minimo, MAX(n.vc) OVER (PARTITION BY j.numero,j.conta) maximo
        INTO #fisicas
        FROM stg.production_export_latest l
        CROSS APPLY OPENJSON(l.source_envelope,'$.record') WITH (
            numero bigint '$.ant_ils_sequence_code', conta nvarchar(255) '$.ant_ils_pas_ant_name',
            centro nvarchar(255) '$.ant_ces_acr_name', obs nvarchar(max) '$.ant_ils_comments',
            usuario nvarchar(255) '$.ant_uer_name', total nvarchar(80) '$.value',
            valor_conta nvarchar(80) '$.ant_ils_pas_value', valor_centro nvarchar(80) '$.ant_ces_value'
        ) j
        CROSS APPLY (VALUES (
            TRY_CONVERT(decimal(19,2),NULLIF(TRIM(REPLACE(REPLACE(j.total,'R$',''),',','')),'')),
            TRY_CONVERT(decimal(19,2),NULLIF(TRIM(REPLACE(REPLACE(j.valor_conta,'R$',''),',','')),'')),
            TRY_CONVERT(decimal(19,2),NULLIF(TRIM(REPLACE(REPLACE(j.valor_centro,'R$',''),',','')),''))
        )) n(v,vc,vcc)
        JOIN @mapa m ON m.conta=TRIM(j.conta) COLLATE Latin1_General_100_CI_AI
        JOIN core.dashboard_contas_leitura p ON p.[Lançamento a Pagar/N°]=j.numero AND p.[Emissão]=l.business_date
        WHERE l.entity_name='CONTAS_A_PAGAR'
          AND l.source_scope=(SELECT MIN(source_scope) FROM ctl.production_ingestion_coverage
              WHERE entity_name='CONTAS_A_PAGAR' HAVING COUNT(DISTINCT source_scope)=1)
          AND (m.fixa IS NULL OR m.fixa<>'D36' OR TRIM(j.usuario) COLLATE Latin1_General_100_CI_AI
                IN (N'Elivaldo - Controladoria',N'Elivaldo - Controladoria (MTZ)'))
        OPTION(MAXDOP 1,MAX_GRANT_PERCENT=2);
    ;WITH rateios AS (
        SELECT f.*, CASE WHEN vc=v THEN COALESCE(vcc,CASE WHEN parcelas=1 AND centro IS NULL THEN vc END)
                         WHEN vcc=v THEN vc END intersecao,
            CASE WHEN centro IN (N'ADMINISTRATIVO COMERCIAL',N'VENDEDORES') OR centro LIKE N'% - COMERCIAL - %' THEN comercial
                 WHEN centro IN (N'ADMINISTRATIVO GERAL',N'ADMINISTRATIVO FROTA') OR centro LIKE N'% - ADM' THEN adm
                 WHEN centro=N'VEÍCULOS DE CARGA' OR centro LIKE N'% - TERMINAL - OPERAÇÃO'
                   OR centro LIKE N'% - FROTA - VIAGEM' OR centro LIKE N'% - FROTA - DISTRIBUIÇÃO' OR centro LIKE N'% - FROTA - OFICINA' THEN operacional
                 -- Na folha antiga, o centro identifica só a filial. A folha traz o setor no fim da observação.
                 WHEN centro LIKE N'FILIAL %' AND centro NOT LIKE N'% - %' THEN
                    CASE WHEN obs LIKE N'% - COMERCIAL' THEN comercial
                         WHEN obs LIKE N'% - ADM' THEN adm
                         WHEN obs LIKE N'% - OPER' OR obs LIKE N'% - OPER TIGRE' OR obs LIKE N'% - OPERAÇÃO'
                           OR obs LIKE N'% - OPERAÇÃO TIGRE' OR obs LIKE N'% - TERMINAL OPER' OR obs LIKE N'% - TERMINAL OPERAÇÃO'
                           OR obs LIKE N'% - MOTORISTA DISTR' OR obs LIKE N'% - MOTORISTA DRISTR'
                           OR obs LIKE N'% - MOTIRISTA DISTR' OR obs LIKE N'% - MOTORISTA DE VIAGEM' THEN operacional END
            END departamento
        FROM #fisicas f
    )
    INSERT #dre
    SELECT numero,ordem+1,documento,emissao,ano,mes,filial,fornecedor,centro,conta,
        COALESCE(fixa,CASE WHEN intersecao IS NOT NULL THEN departamento END,'PESSOAL_REVISAR'),
        CASE WHEN intersecao IS NOT NULL THEN intersecao
             WHEN minimo=maximo THEN 0 END,
        intersecao
    FROM rateios OPTION(MAXDOP 1,MAX_GRANT_PERCENT=2);

    -- Preserva o valor da conta se os centros publicados não fecham (inclusive por um centavo).
    -- A diferença fica identificável no título, sem atribuição arbitrária a um centro.
    ;WITH distribuido AS (
        SELECT numero,conta,SUM(valor) total FROM #dre WHERE ordem>0 GROUP BY numero,conta
    )
    INSERT #dre
    SELECT f.numero,-1-f.ordem,f.documento,f.emissao,f.ano,f.mes,f.filial,f.fornecedor,NULL,f.conta,
        COALESCE(f.fixa,'PESSOAL_REVISAR'),f.vc-d.total,NULL
    FROM #fisicas f JOIN distribuido d ON d.numero=f.numero AND d.conta=f.conta
    WHERE f.parcela=1 AND f.minimo=f.maximo AND f.vc<>d.total;

    DELETE FROM core.dashboard_dre_pagar;
    INSERT core.dashboard_dre_pagar SELECT * FROM #dre;
END;
GO
-- Acrescenta a preparação após a publicação dos títulos e antes do mesmo COMMIT.
DECLARE @ddl nvarchar(max)=OBJECT_DEFINITION(OBJECT_ID('core.usp_dashboard_publicar_fatos_fretes'));
DECLARE @at int=CHARINDEX(N'@audit nvarchar(max)',@ddl), @ancora nvarchar(100)=N'EXEC core.usp_dashboard_preparar_leituras_operacionais;';
IF @ddl IS NULL OR @at=0 OR CHARINDEX(@ancora,@ddl)=0 THROW 52421,'Coordenador de publicacao do ETL inesperado.',1;
IF CHARINDEX('EXEC core.usp_dashboard_preparar_dre_pagar;',@ddl)=0
BEGIN
    SET @ddl=N'CREATE OR ALTER PROCEDURE core.usp_dashboard_publicar_fatos_fretes '+SUBSTRING(@ddl,@at,LEN(@ddl));
    SET @ddl=REPLACE(@ddl,@ancora,@ancora+N' EXEC core.usp_dashboard_preparar_dre_pagar;');
    EXEC sys.sp_executesql @ddl;
END;
EXEC core.usp_dashboard_preparar_dre_pagar;
