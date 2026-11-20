-- O instalador executa os lotes sob a mesma transação e o mesmo bloqueio do publicador.
IF @@TRANCOUNT=0 THROW 52169,'Instalar o detalhe dentro de uma transação.',1;
CREATE TABLE ctl.dashboard_detalhe_frete_estado (
 id bigint NOT NULL PRIMARY KEY,
 minuta bigint NULL,
 origem binary(32) NOT NULL,
 saida binary(32) NULL
);
CREATE TABLE ctl.dashboard_detalhe_instalacao (
 revisao int NOT NULL PRIMARY KEY CHECK(revisao=58),
 modulo_anterior nvarchar(max) NOT NULL,
 assinatura_instalada binary(32) NOT NULL,
 pacote binary(32) NOT NULL,
 instalada_utc datetime2(7) NOT NULL
);
GO
-- O publicador pode conferir o contrato sem receber acesso às definições das views.
CREATE OR ALTER FUNCTION core.fn_dashboard_detalhe_compativel()
RETURNS bit WITH EXECUTE AS OWNER
AS
BEGIN
 DECLARE @obj int=OBJECT_ID(N'core.dashboard_detalhe_fretes_leitura',N'U');
 IF ISNULL(HASHBYTES('SHA2_256',REPLACE(OBJECT_DEFINITION(OBJECT_ID(N'core.dashboard_detalhe_fretes_entrada')),CHAR(13),N'')),0x)<>0x1e24a2f5169af46589cc348786f0024c58fb637f7d6c65c0776b5dbc3ad89c8e OR
       ISNULL(HASHBYTES('SHA2_256',REPLACE(OBJECT_DEFINITION(OBJECT_ID(N'pub.vw_inventario_powerbi')),CHAR(13),N'')),0x)<>0xbf103b3f69ff3b10afcb8c8934d22c133a916cb6d36ddb2be46bba3e20a50f01 OR
       ISNULL(HASHBYTES('SHA2_256',REPLACE(OBJECT_DEFINITION(OBJECT_ID(N'core.dashboard_fretes_vigentes_preparada')),CHAR(13),N'')),0x)<>0xa7c42e45b5aa7a888d1d0cee2eaec2ef88c8de18740c6df66a877c5e0f96108b OR
       ISNULL(HASHBYTES('SHA2_256',REPLACE(OBJECT_DEFINITION(OBJECT_ID(N'core.dashboard_fretes_publicacao_preparada')),CHAR(13),N'')),0x)<>0x7cc734edddf3e503ff793f58ebbfcf311a58d54267db98fb1e2db9c3d5ca13c4 OR
    EXISTS(SELECT 1 FROM sys.triggers WHERE parent_id=@obj AND is_disabled=0)
    OR EXISTS(SELECT 1 FROM sys.change_tracking_tables WHERE object_id=@obj)
    OR EXISTS(SELECT 1 FROM sys.tables WHERE object_id=@obj AND (is_tracked_by_cdc=1 OR temporal_type<>0)) RETURN 0;
 RETURN 1;
END;
GO
CREATE OR ALTER PROCEDURE core.usp_dashboard_detalhe_incremental @recalculados int=NULL OUTPUT,@caminho varchar(24)=NULL OUTPUT AS
BEGIN
 SET NOCOUNT ON;
 IF @@TRANCOUNT=0 THROW 52165,'O detalhe exige a transação da publicação.',1;
 DECLARE @completo bit=0,@ids int=0;
 SET @caminho='incremental';
 SELECT TOP(0) * INTO #detalhe FROM core.dashboard_detalhe_fretes_leitura;
 IF core.fn_dashboard_detalhe_compativel()=0
 BEGIN
    SET @completo=1;
    SET @caminho='contrato';
 END;
 IF @completo=0
 BEGIN
    -- As quatro pequenas tabelas de referência são as mesmas na assinatura e no cálculo.
    SELECT * INTO #ref_atribuicao FROM ref.dashboard_faturamento_atribuicao_filial;
    SELECT * INTO #ref_cidade FROM ref.dashboard_frete_cidade_destino;
    SELECT * INTO #ref_documento FROM ref.dashboard_frete_filial_documento;
    SELECT * INTO #ref_nome FROM ref.dashboard_frete_filial_nome;
    DECLARE @referencias binary(32)=HASHBYTES('SHA2_256',HASHBYTES('SHA2_256',(SELECT * FROM #ref_atribuicao ORDER BY [pagador_documento_key] ASC FOR JSON PATH,INCLUDE_NULL_VALUES))+HASHBYTES('SHA2_256',(SELECT * FROM #ref_cidade ORDER BY [cidade_key] ASC,[uf] ASC FOR JSON PATH,INCLUDE_NULL_VALUES))+HASHBYTES('SHA2_256',(SELECT * FROM #ref_documento ORDER BY [filial_id] ASC FOR JSON PATH,INCLUDE_NULL_VALUES))+HASHBYTES('SHA2_256',(SELECT * FROM #ref_nome ORDER BY [filial_nome_key] ASC FOR JSON PATH,INCLUDE_NULL_VALUES)));
    SELECT DISTINCT [Nº Minuta] AS minuta INTO #comprovantes FROM pub.vw_inventario_powerbi
    WHERE [Comprovante Anexado]=N'Sim' AND [Nº Minuta] IS NOT NULL OPTION(MAXDOP 1,MAX_GRANT_PERCENT=2);
    CREATE UNIQUE CLUSTERED INDEX IX_minuta ON #comprovantes(minuta);
    -- Conserva a ordenação usada pelo relatório para as duas formas de localizar uma fatura.
    ;WITH classificadas AS (SELECT s.*,h.assinatura,
      ROW_NUMBER() OVER(PARTITION BY source_scope,rank_id ORDER BY stored_utc DESC,business_date DESC,occurrence_ordinal) rn,
      ROW_NUMBER() OVER(PARTITION BY source_scope,rank_cte_key ORDER BY cte_priority,stored_utc DESC,business_date DESC,occurrence_ordinal) cte_rn,
      COUNT_BIG(*) OVER(PARTITION BY source_scope,rank_id,stored_utc,business_date,occurrence_ordinal) AS empate_id,
      COUNT_BIG(*) OVER(PARTITION BY source_scope,rank_cte_key,cte_priority,stored_utc,business_date,occurrence_ordinal) AS empate_cte
    FROM core.dashboard_freight_support s
    CROSS APPLY(SELECT HASHBYTES('SHA2_256',(SELECT s.* FOR JSON PATH,WITHOUT_ARRAY_WRAPPER,INCLUDE_NULL_VALUES)) AS assinatura) h
    WHERE entity_name='FATURAS_POR_CLIENTE' AND id IS NOT NULL)
    SELECT * INTO #faturas FROM classificadas WHERE rn=1 OR cte_rn=1 OPTION(MAXDOP 1,MAX_GRANT_PERCENT=2);
    CREATE INDEX IX_fatura_id ON #faturas(source_scope,id,rn);
    CREATE INDEX IX_fatura_cte ON #faturas(source_scope,cte_key,cte_rn);
    ;WITH classificadas AS (SELECT s.*,h.assinatura,
      ROW_NUMBER() OVER(PARTITION BY source_scope,rank_minute ORDER BY stored_utc DESC,rank_service_at DESC,business_date DESC,occurrence_ordinal) rn,
      COUNT_BIG(*) OVER(PARTITION BY source_scope,rank_minute,stored_utc,rank_service_at,business_date,occurrence_ordinal) AS empate
    FROM core.dashboard_freight_support s
    CROSS APPLY(SELECT HASHBYTES('SHA2_256',(SELECT s.* FOR JSON PATH,WITHOUT_ARRAY_WRAPPER,INCLUDE_NULL_VALUES)) AS assinatura) h
    WHERE entity_name='LOCALIZACAO_CARGAS' AND minute IS NOT NULL)
    SELECT * INTO #locais FROM classificadas WHERE rn=1 OPTION(MAXDOP 1,MAX_GRANT_PERCENT=2);
    CREATE INDEX IX_local ON #locais(source_scope,minute);
    IF EXISTS(SELECT 1 FROM #faturas WHERE (rn=1 AND empate_id>1) OR (cte_rn=1 AND empate_cte>1)) OR EXISTS(SELECT 1 FROM #locais WHERE empate>1)
       BEGIN SET @completo=1; SET @caminho='empate'; END;
    SELECT l.freight_id,l.source_scope,l.stored_utc,l.business_date,l.run_id,l.occurrence_ordinal,
      j.id,j.minuta,j.cte,HASHBYTES('SHA2_256',CONVERT(varbinary(max),l.source_envelope)) AS bruto
    INTO #fontes FROM core.dashboard_fretes_vigentes_preparada l
    OUTER APPLY OPENJSON(l.source_envelope,'$.record') WITH(id bigint,minuta bigint '$.corporation_sequence_number',cte nvarchar(100) '$.fit_fhe_cte_key') j
    OPTION(MAXDOP 1,MAX_GRANT_PERCENT=2);
    IF EXISTS(SELECT 1 FROM #fontes WHERE id IS NULL OR id IS DISTINCT FROM freight_id) OR EXISTS(SELECT 1 FROM #fontes GROUP BY id HAVING COUNT_BIG(*)>1)
       BEGIN SET @completo=1; SET @caminho='identidade'; END;
    SELECT f.id,f.minuta,HASHBYTES('SHA2_256',(SELECT f.bruto,f.run_id,f.occurrence_ordinal,f.source_scope,f.business_date,f.stored_utc,
       i.assinatura AS fatura,c.assinatura AS cte,l.assinatura AS local,p.minuta AS comprovante,@referencias AS referencias
       FOR JSON PATH,WITHOUT_ARRAY_WRAPPER,INCLUDE_NULL_VALUES)) AS origem
    INTO #estado FROM #fontes f
    LEFT JOIN #faturas i ON i.source_scope=f.source_scope AND i.id=f.id AND i.rn=1
    LEFT JOIN #faturas c ON c.source_scope=f.source_scope AND c.cte_key=f.cte AND c.cte_rn=1
    LEFT JOIN #locais l ON l.source_scope=f.source_scope AND l.minute=f.minuta
    LEFT JOIN #comprovantes p ON p.minuta=f.minuta OPTION(MAXDOP 1,MAX_GRANT_PERCENT=2);
    IF EXISTS(SELECT 1 FROM #estado GROUP BY id HAVING COUNT_BIG(*)>1) BEGIN SET @completo=1; SET @caminho='vinculo'; END;
    -- Inclui reparos feitos diretamente na tabela de leitura, inclusive exclusões.
    SELECT a.ID,HASHBYTES('SHA2_256',(SELECT a.* FOR JSON PATH,WITHOUT_ARRAY_WRAPPER,INCLUDE_NULL_VALUES)) AS saida
    INTO #publicado FROM core.dashboard_detalhe_fretes_leitura a OPTION(MAXDOP 1,MAX_GRANT_PERCENT=2);
    IF EXISTS(SELECT 1 FROM #publicado GROUP BY ID HAVING ID IS NULL OR COUNT_BIG(*)>1) BEGIN SET @completo=1; SET @caminho='publicado'; END;
    IF @completo=0
    BEGIN
       CREATE UNIQUE CLUSTERED INDEX IX_estado ON #estado(id);
       CREATE UNIQUE CLUSTERED INDEX IX_publicado ON #publicado(id);
       SELECT COALESCE(e.id,h.id,p.id) AS id,e.minuta AS nova,h.minuta AS antiga
       INTO #mudancas FROM #estado e FULL JOIN ctl.dashboard_detalhe_frete_estado h ON e.id=h.id
       FULL JOIN #publicado p ON p.id=COALESCE(e.id,h.id)
       WHERE e.id IS NULL OR h.id IS NULL OR e.origem IS DISTINCT FROM h.origem OR h.saida IS DISTINCT FROM p.saida
       OPTION(MAXDOP 1,RECOMPILE);
       SELECT id INTO #afetados FROM #mudancas
       UNION SELECT e.id FROM #estado e JOIN (SELECT nova AS minuta FROM #mudancas UNION SELECT antiga FROM #mudancas) m
          ON e.minuta IS NOT DISTINCT FROM m.minuta OPTION(MAXDOP 1,RECOMPILE);
       SET @ids=@@ROWCOUNT;
       SET @recalculados=@ids;
       IF @ids=0 RETURN;
       CREATE UNIQUE CLUSTERED INDEX IX_afetados ON #afetados(id);
       SELECT f.freight_id,f.source_scope,f.stored_utc,f.business_date,f.run_id,f.occurrence_ordinal,o.source_envelope
       INTO #fonte_alterada FROM #fontes f JOIN #afetados a ON a.id=f.id
       JOIN stg.production_export_occurrence o ON o.run_id=f.run_id AND o.occurrence_ordinal=f.occurrence_ordinal
       WHERE f.bruto=HASHBYTES('SHA2_256',CONVERT(varbinary(max),o.source_envelope)) OPTION(MAXDOP 1,RECOMPILE);
       IF @@ROWCOUNT<>(SELECT COUNT_BIG(*) FROM #fontes f JOIN #afetados a ON a.id=f.id)
          THROW 52166,'Fonte do frete mudou durante a leitura. Repetir o lote.',1;
       EXEC core.usp_dashboard_detalhe_calcular_selecao;
       IF EXISTS(SELECT 1 FROM #detalhe GROUP BY ID HAVING ID IS NULL OR COUNT_BIG(*)>1) OR EXISTS(SELECT 1 FROM #detalhe d WHERE NOT EXISTS(SELECT 1 FROM #afetados a WHERE a.id=d.ID))
          BEGIN SET @completo=1; SET @caminho='repeticoes'; END;
       ELSE
       BEGIN
          SELECT r.ID,HASHBYTES('SHA2_256',(SELECT r.* FOR JSON PATH,WITHOUT_ARRAY_WRAPPER,INCLUDE_NULL_VALUES)) AS saida
          INTO #prevista FROM #detalhe r OPTION(MAXDOP 1,RECOMPILE);
          CREATE UNIQUE CLUSTERED INDEX IX_prevista ON #prevista(ID);
          SELECT a.id INTO #gravar FROM #afetados a LEFT JOIN #publicado p ON p.ID=a.id
          LEFT JOIN #prevista n ON n.ID=a.id WHERE n.saida IS DISTINCT FROM p.saida OPTION(MAXDOP 1,RECOMPILE);
          IF @@ROWCOUNT>0
          BEGIN
             CREATE UNIQUE CLUSTERED INDEX IX_gravar ON #gravar(id);
             DELETE d FROM core.dashboard_detalhe_fretes_leitura d JOIN #gravar g ON g.id=d.ID OPTION(MAXDOP 1,RECOMPILE);
             INSERT core.dashboard_detalhe_fretes_leitura SELECT d.* FROM #detalhe d JOIN #gravar g ON g.id=d.ID
             OPTION(MAXDOP 1,MAX_GRANT_PERCENT=32,RECOMPILE);
          END;
          DELETE h FROM ctl.dashboard_detalhe_frete_estado h JOIN #afetados a ON a.id=h.id;
          INSERT ctl.dashboard_detalhe_frete_estado(id,minuta,origem,saida)
          SELECT e.id,e.minuta,e.origem,d.saida FROM #estado e JOIN #afetados a ON a.id=e.id
          LEFT JOIN #prevista d ON e.id=d.ID OPTION(MAXDOP 1,RECOMPILE);
       END;
    END;
 END;
 IF @completo=1
 BEGIN
    DELETE FROM ctl.dashboard_detalhe_frete_estado;
    TRUNCATE TABLE #detalhe;
    EXEC core.usp_dashboard_detalhe_calcular_integral @recalculados OUTPUT;
    DELETE FROM core.dashboard_detalhe_fretes_leitura;
    INSERT core.dashboard_detalhe_fretes_leitura SELECT * FROM #detalhe OPTION(MAXDOP 1,MAX_GRANT_PERCENT=32);
 END;
END;
