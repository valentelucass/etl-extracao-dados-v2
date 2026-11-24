-- Compara o resultado publicado com o valor bruto de cada registro, incluindo nulos.
-- As duas leituras vêm da mesma linha durante uma única consulta.
SET NOCOUNT ON;
SELECT [ID], [Comprovante de Entrega] AS publicada,
       JSON_VALUE([Metadata],'$.fit_d_t_created_at') AS original
INTO #conferencia_comprovante
FROM pub.vw_fretes_powerbi_pronta OPTION(MAXDOP 1);
IF EXISTS (SELECT 1 FROM #conferencia_comprovante
    WHERE publicada IS DISTINCT FROM TRY_CONVERT(datetimeoffset(7),NULLIF(TRIM(original),N''),127))
    THROW 52283,'Data publicada do comprovante difere do frete vigente.',1;
SELECT COUNT_BIG(*) AS fretes,COUNT_BIG(publicada) AS com_data FROM #conferencia_comprovante;

-- Exercita anexação posterior e remoção no mesmo frete com a definição instalada.
DECLARE @definicao nvarchar(max)=(SELECT definition FROM sys.computed_columns WHERE
    object_id=OBJECT_ID('core.dashboard_detalhe_fretes_leitura') AND name='data_comprovante_entrega' AND is_persisted=1);
IF @definicao IS NULL THROW 52284,'Coluna persistida ausente.',1;
DECLARE @teste nvarchar(max)=N'CREATE TABLE #amostra (id int PRIMARY KEY, metadata_gzip varbinary(max),
    data_comprovante_entrega AS '+@definicao+N' PERSISTED);
    INSERT #amostra(id,metadata_gzip) VALUES
        (1,COMPRESS(N''{}'')), (2,COMPRESS(N''{"fit_d_t_created_at":"   "}'')),
        (3,COMPRESS(N''{"fit_d_t_created_at":"data inválida"}'')),(4,NULL);
    IF EXISTS (SELECT 1 FROM #amostra WHERE data_comprovante_entrega IS NOT NULL)
        THROW 52285,''Ausência de data válida interpretada como anexo.'',1;
    UPDATE #amostra SET metadata_gzip=COMPRESS(N''{"fit_d_t_created_at":"2026-11-24T09:12:34.123-03:00"}'') WHERE id=1;
    IF NOT EXISTS (SELECT 1 FROM #amostra WHERE id=1 AND data_comprovante_entrega=CONVERT(datetimeoffset(7),''2026-11-24T12:12:34.123Z'',127))
        THROW 52286,''Data anexada posteriormente não foi atualizada.'',1;
    UPDATE #amostra SET metadata_gzip=COMPRESS(N''{"fit_d_t_created_at":null}'') WHERE id=1;
    IF EXISTS (SELECT 1 FROM #amostra WHERE id=1 AND data_comprovante_entrega IS NOT NULL)
        THROW 52287,''Remoção do comprovante não atualizou o registro.'',1;';
EXEC sys.sp_executesql @teste;
DROP TABLE #conferencia_comprovante;
