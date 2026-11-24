-- The JSON expression is the one used by the delivery queries. Keep only its small
-- numeric result in the index. The public view still returns the original metadata.
IF @@TRANCOUNT=0 THROW 52210,'Instalar dentro da transacao de publicacao.',1;
DECLARE @campos nvarchar(max), @camposAtual nvarchar(max);
SELECT @campos=STRING_AGG(CONVERT(nvarchar(max),QUOTENAME(name)),',') WITHIN GROUP(ORDER BY column_id),
       @camposAtual=STRING_AGG(CONVERT(nvarchar(max),'a.'+QUOTENAME(name)),',') WITHIN GROUP(ORDER BY column_id)
FROM sys.columns WHERE object_id=OBJECT_ID('core.dashboard_detalhe_fretes_leitura') AND is_computed=0;
DECLARE @modulo nvarchar(max)=OBJECT_DEFINITION(OBJECT_ID('core.usp_dashboard_detalhe_incremental'));
DECLARE @cab int=CHARINDEX(N'@recalculados int',@modulo);
DECLARE @forma nvarchar(140)=N'SELECT TOP(0) * INTO #detalhe FROM core.dashboard_detalhe_fretes_leitura';
DECLARE @assinatura nvarchar(140)=N'(SELECT a.* FOR JSON PATH,WITHOUT_ARRAY_WRAPPER,INCLUDE_NULL_VALUES)';
IF ISNULL(@cab,0)=0 OR CHARINDEX(@forma,@modulo)=0 OR CHARINDEX(@assinatura,@modulo)=0
    THROW 52211,'Contrato do detalhe de fretes divergente.',1;
SET @modulo=N'ALTER PROCEDURE core.usp_dashboard_detalhe_incremental '+SUBSTRING(@modulo,@cab,LEN(@modulo));
SET @modulo=REPLACE(@modulo,@forma,N'SELECT TOP(0) '+@campos+N' INTO #detalhe FROM core.dashboard_detalhe_fretes_leitura');
SET @modulo=REPLACE(@modulo,@assinatura,N'(SELECT '+@camposAtual+N' FOR JSON PATH,WITHOUT_ARRAY_WRAPPER,INCLUDE_NULL_VALUES)');
EXEC sys.sp_executesql @modulo;
GO
ALTER TABLE core.dashboard_detalhe_fretes_leitura ADD manifesto_entrega_consulta AS
 TRY_CONVERT(bigint,JSON_VALUE(CONVERT(nvarchar(max),DECOMPRESS(metadata_gzip)),'$.fit_fte_l_y_mft_sequence_code'));
GO
CREATE INDEX IX_detalhe_minuta_operacao ON core.dashboard_detalhe_fretes_leitura([Nº Minuta])
INCLUDE (manifesto_entrega_consulta,[Tipo Frete],[CT-e Emissão],[Previsão de Entrega],[Data de Finalização],
    [Finalização da Performance],[Performance Diferença de Dias],[Data de extracao],[Comprovante Anexado],[Status],
    [Pagador],[Pagador Doc],[Responsável pela Região de Destino],[Responsável Região Destino Key])
WITH(DATA_COMPRESSION=PAGE,MAXDOP=1);
CREATE INDEX IX_detalhe_pagador_operacao ON core.dashboard_detalhe_fretes_leitura([Pagador Doc],[Nº Minuta])
INCLUDE ([Tipo Frete],[CT-e Emissão],[Previsão de Entrega],[Data de Finalização],[Finalização da Performance],
    [Performance Diferença de Dias],[Data de extracao],[Comprovante Anexado],[Status],[Pagador],
    [Responsável Região Destino Key],[Valor Total do Serviço],[Kg Real],[Valor NF])
WITH(DATA_COMPRESSION=PAGE,MAXDOP=1);
