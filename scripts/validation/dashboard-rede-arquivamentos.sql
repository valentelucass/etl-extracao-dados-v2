-- Executado na transação do instalador. A referência usa diretamente o relatório 11361.
IF ISNULL(CHARINDEX(N'EXEC core.usp_dashboard_preparar_arquivos;',
              OBJECT_DEFINITION(OBJECT_ID('core.usp_dashboard_publicar_fatos_fretes'))),0)=0
    THROW 52204,'O publicador nao atualiza os arquivamentos.',1;

SELECT d.minuta, MIN(c.business_date) AS primeiro_arquivamento INTO #arquivos_referencia
FROM ctl.production_ingestion_coverage c
JOIN stg.production_export_occurrence o ON o.run_id=c.latest_run_id
CROSS APPLY OPENJSON(o.source_envelope,'$.record') WITH
    (minuta bigint '$.fag_fhs_fit_corporation_sequence_number', arquivo nvarchar(100) '$.sequence_code') d
WHERE c.entity_name='COMPROVANTES_ENTREGA' AND d.minuta IS NOT NULL AND NULLIF(TRIM(d.arquivo),'') IS NOT NULL
GROUP BY d.minuta OPTION(MAXDOP 1);
CREATE UNIQUE CLUSTERED INDEX IX_referencia ON #arquivos_referencia(minuta);

EXEC core.usp_dashboard_preparar_arquivos;
IF EXISTS (SELECT latest_run_id FROM ctl.production_ingestion_coverage WHERE entity_name='COMPROVANTES_ENTREGA'
           EXCEPT SELECT captura FROM ctl.dashboard_arquivos_captura)
   OR EXISTS (SELECT captura FROM ctl.dashboard_arquivos_captura EXCEPT
              SELECT latest_run_id FROM ctl.production_ingestion_coverage WHERE entity_name='COMPROVANTES_ENTREGA')
    THROW 52205,'Capturas vigentes divergentes.',1;

-- Uma captura recém-aceita ainda não preparada precisa produzir o mesmo resultado.
-- O savepoint restaura exclusivamente o preparo derivado, incluindo capturas vazias.
SAVE TRANSACTION prova_arquivo_novo;
DELETE ctl.dashboard_arquivos_captura WHERE captura IN (
    SELECT TOP (1) captura FROM core.dashboard_arquivos_minuta GROUP BY captura ORDER BY COUNT_BIG(*) DESC
) OR captura IN (
    SELECT TOP (1) c.captura FROM ctl.dashboard_arquivos_captura c
    WHERE NOT EXISTS (SELECT 1 FROM core.dashboard_arquivos_minuta m WHERE m.captura=c.captura)
);
SELECT minuta,primeiro_arquivamento INTO #antes_do_preparo FROM pub.rede_credenciada_arquivos OPTION(MAXDOP 1);
IF EXISTS (SELECT * FROM #arquivos_referencia EXCEPT SELECT * FROM #antes_do_preparo)
   OR EXISTS (SELECT * FROM #antes_do_preparo EXCEPT SELECT * FROM #arquivos_referencia)
    THROW 52206,'O resultado de uma captura nova diverge do ESL.',1;
ROLLBACK TRANSACTION prova_arquivo_novo;

EXEC core.usp_dashboard_preparar_arquivos;
SELECT minuta,primeiro_arquivamento INTO #apos_preparo FROM pub.rede_credenciada_arquivos OPTION(MAXDOP 1);
IF EXISTS (SELECT * FROM #arquivos_referencia EXCEPT SELECT * FROM #apos_preparo)
   OR EXISTS (SELECT * FROM #apos_preparo EXCEPT SELECT * FROM #arquivos_referencia)
    THROW 52207,'Arquivamentos preparados divergem do ESL.',1;
