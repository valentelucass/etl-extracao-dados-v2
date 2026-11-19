-- O detalhe conserva as colunas da consulta publicada e usa os vínculos já conferidos.
DECLARE @definition nvarchar(max)=OBJECT_DEFINITION(OBJECT_ID(N'pub.vw_fretes_powerbi',N'V'));
IF @definition IS NULL OR CHARINDEX(N'FROM core.dashboard_fretes_publicacao f',@definition)=0
    THROW 52084,'A definicao de detalhe de fretes diverge da publicacao esperada.',1;
DECLARE @body int=CHARINDEX(N'AS',@definition,CHARINDEX(N'vw_fretes_powerbi',@definition)+LEN(N'vw_fretes_powerbi'));
IF @body=0 THROW 52085,'Cabecalho ausente no detalhe de fretes.',1;
SET @definition=N'CREATE OR ALTER VIEW pub.vw_fretes_powerbi_preparada '+SUBSTRING(@definition,@body,LEN(@definition));
SET @definition=REPLACE(@definition,N'FROM core.dashboard_fretes_publicacao f',N'FROM core.dashboard_fretes_publicacao_preparada f');
EXEC sys.sp_executesql @definition;
