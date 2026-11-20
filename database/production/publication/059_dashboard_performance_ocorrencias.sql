-- A chave do relatório é a posição dentro de uma captura, não a nota:
-- um frete pode registrar a mesma ocorrência para várias notas, ou repetir uma etapa.
CREATE TABLE core.dashboard_performance_ocorrencias (
    escopo char(64) COLLATE Latin1_General_100_BIN2 NOT NULL,
    data_particao date NOT NULL,
    ordem bigint NOT NULL,
    minuta bigint NULL,
    nota_fiscal nvarchar(100) NULL,
    cte nvarchar(100) NULL,
    codigo int NULL,
    descricao nvarchar(255) NULL,
    ocorrido_em datetime2(3) NULL,
    filial nvarchar(255) NULL,
    status_entrega nvarchar(100) NULL,
    CONSTRAINT PK_dashboard_performance_ocorrencias PRIMARY KEY CLUSTERED (escopo, data_particao, ordem)
);
CREATE INDEX IX_performance_ocorrencias_minuta_nota
    ON core.dashboard_performance_ocorrencias(minuta, nota_fiscal, ocorrido_em DESC)
    INCLUDE (codigo, descricao, filial, status_entrega);
CREATE INDEX IX_performance_ocorrencias_etapa
    ON core.dashboard_performance_ocorrencias(codigo, ocorrido_em, minuta)
    INCLUDE (filial, nota_fiscal);
CREATE INDEX IX_performance_ocorrencias_minuta_tempo
    ON core.dashboard_performance_ocorrencias(minuta, ocorrido_em DESC)
    INCLUDE (codigo, descricao, filial, nota_fiscal, status_entrega);
CREATE TABLE ctl.dashboard_performance_ocorrencias_particao (
    escopo char(64) COLLATE Latin1_General_100_BIN2 NOT NULL,
    data_particao date NOT NULL,
    captura uniqueidentifier NOT NULL,
    CONSTRAINT PK_dashboard_performance_ocorrencias_particao PRIMARY KEY (escopo, data_particao)
);
GO
CREATE OR ALTER VIEW pub.dashboard_performance_ocorrencias AS
SELECT minuta, nota_fiscal, cte, codigo, descricao, ocorrido_em, filial, status_entrega,
       data_particao, ordem, escopo
FROM core.dashboard_performance_ocorrencias;
GO
CREATE OR ALTER PROCEDURE core.usp_dashboard_preparar_ocorrencias AS
BEGIN
    SET NOCOUNT ON;
    IF @@TRANCOUNT = 0 THROW 52173, 'Preparar dentro da transacao de publicacao.', 1;

    -- Capturas são imutáveis. O mesmo identificador alimenta as linhas e a marca
    -- de conclusão, mesmo se a integração receber uma nova versão durante a leitura.
    SELECT COALESCE(c.source_scope, p.escopo) AS escopo,
           COALESCE(c.business_date, p.data_particao) AS dia,
           c.latest_run_id AS captura
    INTO #particoes_ocorrencia
    FROM (SELECT source_scope, business_date, latest_run_id
          FROM ctl.production_ingestion_coverage WHERE entity_name = 'OCORRENCIAS') AS c
    FULL JOIN ctl.dashboard_performance_ocorrencias_particao AS p
        ON p.escopo = c.source_scope AND p.data_particao = c.business_date
    WHERE c.latest_run_id IS DISTINCT FROM p.captura;
    IF @@ROWCOUNT = 0 RETURN;
    CREATE UNIQUE CLUSTERED INDEX IX_particoes ON #particoes_ocorrencia(escopo, dia);

    DELETE o FROM core.dashboard_performance_ocorrencias AS o
    JOIN #particoes_ocorrencia AS p ON p.escopo = o.escopo AND p.dia = o.data_particao;

    INSERT core.dashboard_performance_ocorrencias
        (escopo, data_particao, ordem, minuta, nota_fiscal, cte, codigo, descricao, ocorrido_em, filial, status_entrega)
    SELECT p.escopo, p.dia, o.occurrence_ordinal, j.minuta,
           NULLIF(TRIM(j.nf), N''), NULLIF(TRIM(j.cte), N''), j.codigo,
           NULLIF(TRIM(j.descricao), N''),
           CONVERT(datetime2(3), j.ocorrido AT TIME ZONE 'E. South America Standard Time'),
           NULLIF(TRIM(j.filial_registro), N''), NULLIF(TRIM(j.status_entrega), N'')
    FROM #particoes_ocorrencia AS p
    JOIN stg.production_export_occurrence AS o ON o.run_id = p.captura
    CROSS APPLY OPENJSON(o.source_envelope, '$.record') WITH (
        minuta bigint '$.ice_f_e_fit_corporation_sequence_number',
        nf nvarchar(max) '$.ice_f_e_ioe_number',
        cte nvarchar(max) '$.ice_fit_fhe_cte_number',
        codigo int '$.ice_ore_code',
        descricao nvarchar(max) '$.ice_ore_description',
        ocorrido datetimeoffset(3) '$.occurrence_at',
        filial_registro nvarchar(max) '$.ice_uer_crn_psn_nickname',
        status_entrega nvarchar(max) '$.delivery_status'
    ) AS j
    OPTION (MAXDOP 1, MAX_GRANT_PERCENT = 2);

    DECLARE @inseridas bigint = ROWCOUNT_BIG();
    IF @inseridas <> (SELECT COALESCE(SUM(CONVERT(bigint, r.row_count)), 0)
                      FROM #particoes_ocorrencia AS p
                      JOIN ctl.production_ingestion_run AS r ON r.run_id = p.captura)
        THROW 52174, 'A quantidade de ocorrencias difere das capturas do ESL.', 1;

    DELETE a FROM ctl.dashboard_performance_ocorrencias_particao AS a
    JOIN #particoes_ocorrencia AS p ON p.escopo = a.escopo AND p.dia = a.data_particao;
    INSERT ctl.dashboard_performance_ocorrencias_particao(escopo, data_particao, captura)
    SELECT escopo, dia, captura FROM #particoes_ocorrencia WHERE captura IS NOT NULL;
END;
GO
-- Inclui somente o novo preparo. O coordenador já controla a transação e o bloqueio compartilhado.
DECLARE @nome sysname = N'core.usp_dashboard_publicar_fatos_fretes';
DECLARE @ddl nvarchar(max) = OBJECT_DEFINITION(OBJECT_ID(@nome, 'P'));
DECLARE @param int = CHARINDEX(N'@audit nvarchar(max)', @ddl);
DECLARE @ancora nvarchar(200) = N'EXEC core.usp_dashboard_preparar_calendario;';
IF @param IS NULL OR @param = 0 OR CHARINDEX(@ancora, @ddl) = 0
   OR CHARINDEX(@ancora, @ddl, CHARINDEX(@ancora, @ddl) + 1) <> 0
   OR CHARINDEX(N'usp_dashboard_preparar_ocorrencias', @ddl) <> 0
    THROW 52175, 'Definicao do publicador incompativel.', 1;
SET @ddl = N'CREATE OR ALTER PROCEDURE ' + @nome + N' ' + SUBSTRING(@ddl, @param, LEN(@ddl));
SET @ddl = REPLACE(@ddl, @ancora, @ancora + CHAR(10) + N'        EXEC core.usp_dashboard_preparar_ocorrencias;');
EXEC sys.sp_executesql @ddl;

