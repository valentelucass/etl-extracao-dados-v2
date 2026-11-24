-- A atribuição é comercial. A filial fiscal do CT-e e a região de destino continuam na origem.
DECLARE @pagador nvarchar(50)=N'60642774001209',
        @filial nvarchar(255)=N'SPO - RODOGARCIA TRANSPORTES RODOVIARIOS LTDA';
IF NOT EXISTS (
    SELECT 1 FROM ref.dashboard_frete_filial_nome n
    JOIN ref.dashboard_frete_filial_documento d ON d.filial_id=n.filial_id
    WHERE n.filial_nome_key=LOWER(@filial) AND d.documento=N'60960473000243'
) THROW 52271,'Filial de faturamento SPO desconhecida.',1;

UPDATE ref.dashboard_faturamento_atribuicao_filial
SET filial_destino_nome=@filial, filial_destino_key=LOWER(@filial)
WHERE pagador_documento_key=@pagador;
IF @@ROWCOUNT=0
    INSERT ref.dashboard_faturamento_atribuicao_filial(pagador_documento_key,filial_destino_nome,filial_destino_key)
    VALUES(@pagador,@filial,LOWER(@filial));

-- A publicação já consulta a atribuição pelo pagador para o nome e a chave.
-- Complete ID, apelido e CNPJ na mesma projeção, inclusive nas próximas cargas.
DECLARE @ddl nvarchar(max)=OBJECT_DEFINITION(OBJECT_ID(N'pub.fato_fretes_faturamento',N'V'));
IF @ddl IS NULL THROW 52272,'Fonte de faturamento ausente.',1;
IF CHARINDEX(N'filial_vedacit',@ddl)=0
BEGIN
    IF CHARINDEX(N'resolved_branch_id AS [filial_id]',@ddl)=0
       OR CHARINDEX(N'NULLIF(TRIM(branch_name),N'''') AS [filial_apelido]',@ddl)=0
       OR CHARINDEX(N'branch_document AS [filial_cnpj]',@ddl)=0
       OR CHARINDEX(N'JOIN pub.dim_calendario cal ON',@ddl)=0
        THROW 52273,'Contrato do faturamento mudou. Verifique a origem antes de publicar.',1;
    SET @ddl=N'ALTER '+SUBSTRING(@ddl,CHARINDEX(N'VIEW ',UPPER(@ddl)),LEN(@ddl));
    SET @ddl=REPLACE(@ddl,N'resolved_branch_id AS [filial_id]',
        N'COALESCE(filial_vedacit.filial_id,f.resolved_branch_id) AS [filial_id]');
    SET @ddl=REPLACE(@ddl,N'NULLIF(TRIM(branch_name),N'''') AS [filial_apelido]',
        N'CASE WHEN filial_vedacit.filial_id IS NULL THEN NULLIF(TRIM(f.branch_name),N'''') ELSE f.billing_branch END AS [filial_apelido]');
    SET @ddl=REPLACE(@ddl,N'branch_document AS [filial_cnpj]',
        N'COALESCE(doc_vedacit.documento,f.branch_document) AS [filial_cnpj]');
    SET @ddl=REPLACE(@ddl,N'JOIN pub.dim_calendario cal ON',
        N'LEFT JOIN ref.dashboard_frete_filial_nome filial_vedacit
           ON f.payer_key=N''60642774001209'' AND filial_vedacit.filial_nome_key=f.billing_branch_key COLLATE Latin1_General_CI_AS
          LEFT JOIN ref.dashboard_frete_filial_documento doc_vedacit ON doc_vedacit.filial_id=filial_vedacit.filial_id
          JOIN pub.dim_calendario cal ON');
    EXEC sys.sp_executesql @ddl;
END;

-- Reprocessa a dimensão das linhas já publicadas sem incluir fretes de uma carga ainda aberta.
SELECT f.frete_id,f.filial_id,f.filial_nome,f.filial_key,f.filial_apelido,f.filial_cnpj,f.hash_linha
INTO #origem_vedacit FROM pub.fato_fretes_faturamento f WHERE f.pagador_documento_key=@pagador
OPTION (MAXDOP 1,MAX_GRANT_PERCENT=2);
CREATE UNIQUE CLUSTERED INDEX IX_origem_vedacit ON #origem_vedacit(frete_id);
IF EXISTS (
    SELECT 1 FROM core.dashboard_faturamento_leitura atual
    LEFT JOIN #origem_vedacit f ON f.frete_id=atual.frete_id
    WHERE atual.pagador_documento_key=@pagador
      AND (f.frete_id IS NULL OR f.filial_nome<>@filial OR f.filial_cnpj<>N'60960473000243'
            OR f.filial_id IS NULL OR f.filial_cnpj IS NULL OR f.hash_linha IS NULL)
) THROW 52274,'A origem nao confirma a atribuicao para todos os fretes publicados.',1;
UPDATE atual SET filial_id=f.filial_id,filial_nome=f.filial_nome,filial_key=f.filial_key,
                 filial_apelido=f.filial_apelido,filial_cnpj=f.filial_cnpj,hash_linha=f.hash_linha
FROM core.dashboard_faturamento_leitura atual
JOIN #origem_vedacit f ON f.frete_id=atual.frete_id
WHERE atual.pagador_documento_key=@pagador AND EXISTS (
    SELECT atual.filial_id,atual.filial_nome COLLATE DATABASE_DEFAULT,atual.filial_key COLLATE DATABASE_DEFAULT,
           atual.filial_apelido COLLATE DATABASE_DEFAULT,atual.filial_cnpj COLLATE DATABASE_DEFAULT,atual.hash_linha
    EXCEPT SELECT f.filial_id,f.filial_nome COLLATE DATABASE_DEFAULT,f.filial_key COLLATE DATABASE_DEFAULT,
           f.filial_apelido COLLATE DATABASE_DEFAULT,f.filial_cnpj COLLATE DATABASE_DEFAULT,f.hash_linha
);
DROP TABLE #origem_vedacit;
