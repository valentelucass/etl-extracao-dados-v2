-- Comprovante de Entrega do relatório ESL 6389 (coluna 111), no frete vigente.
IF @@TRANCOUNT=0 THROW 52280,'Instalar dentro da transação de publicação.',1;
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID('core.dashboard_detalhe_fretes_leitura')
    AND name='IX_dashboard_entregas_previsao' AND is_disabled=0)
    THROW 52281,'Índice de previsão ausente.',1;

ALTER TABLE core.dashboard_detalhe_fretes_leitura ADD data_comprovante_entrega AS
    TRY_CONVERT(datetimeoffset(7),NULLIF(TRIM(JSON_VALUE(CONVERT(nvarchar(max),DECOMPRESS(metadata_gzip)),
        '$.fit_d_t_created_at')),N''),127) PERSISTED;
GO
-- Conserva as chaves e as inclusões do índice de previsão usado pelos demais relatórios.
CREATE INDEX IX_dashboard_entregas_previsao
ON core.dashboard_detalhe_fretes_leitura([Previsão de Entrega],[Nº Minuta])
INCLUDE([Data de Finalização],[Finalização da Performance],[Data de extracao],[Pagador],
    [Responsável pela Região de Destino],[Responsável Região Destino Key],[Filial Emissora],[Filial],
    [Região Destino],[UF Destino],[Cidade Destino],[Destino],[Kg Taxado],[Valor NF],[Status],
    [Comprovante Anexado],[Performance Diferença de Dias],[Tipo Frete],[CT-e Emissão],[NF],data_comprovante_entrega)
WITH(DROP_EXISTING=ON,DATA_COMPRESSION=PAGE,MAXDOP=1);
GO
-- Mantém as colunas públicas em sua ordem original, inclusive Metadata.
DECLARE @nome sysname=N'pub.vw_fretes_powerbi_pronta',
        @origem nvarchar(160)=N' FROM core.dashboard_detalhe_fretes_leitura',
        @ddl nvarchar(max)=OBJECT_DEFINITION(OBJECT_ID('pub.vw_fretes_powerbi_pronta','V'));
DECLARE @inicio int=CHARINDEX(N' AS SELECT ',@ddl), @fim int=CHARINDEX(@origem,@ddl);
IF @ddl IS NULL OR @inicio=0 OR @fim<=@inicio OR CHARINDEX(@origem,@ddl,@fim+1)>0
    OR EXISTS(SELECT 1 FROM sys.columns WHERE object_id=OBJECT_ID(@nome) AND name=N'Comprovante de Entrega')
    THROW 52282,'Definição da leitura pública de fretes divergente.',1;
SET @ddl=N'ALTER VIEW '+@nome+SUBSTRING(@ddl,@inicio,@fim-@inicio)
    +N',data_comprovante_entrega AS [Comprovante de Entrega]'+SUBSTRING(@ddl,@fim,LEN(@ddl));
EXEC sys.sp_executesql @ddl;
