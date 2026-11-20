-- Executar logo após uma publicação. A conferência só grava tabelas temporárias da sessão.
SET NOCOUNT ON;
SET DEADLOCK_PRIORITY LOW;
SET LOCK_TIMEOUT 2500;

DECLARE @recibo datetime2(7) = (SELECT publicado_utc FROM ctl.dashboard_leituras_publicadas WHERE versao = 46);
DECLARE @comeco datetime2(7) = SYSUTCDATETIME();
DECLARE @destino int = OBJECT_ID(N'core.dashboard_detalhe_fretes_leitura', N'U');
IF @recibo IS NULL OR (SELECT COUNT(*) FROM sys.columns WHERE object_id = @destino) <> 123
    THROW 52170, 'Contrato ou recibo do detalhe de fretes incompleto.', 1;

SELECT TOP (0) * INTO #referencia FROM core.dashboard_detalhe_fretes_leitura;
DECLARE @colunas nvarchar(max), @origem nvarchar(max);
SELECT @colunas = STRING_AGG(CONVERT(nvarchar(max), QUOTENAME(name)), N',') WITHIN GROUP (ORDER BY column_id),
       @origem = STRING_AGG(CONVERT(nvarchar(max), CASE name WHEN N'metadata_gzip' THEN N'COMPRESS([Metadata])' ELSE QUOTENAME(name) END), N',') WITHIN GROUP (ORDER BY column_id)
FROM sys.columns WHERE object_id = @destino;
DECLARE @consulta nvarchar(max) = N'INSERT #referencia (' + @colunas + N') SELECT ' + @origem
    + N' FROM core.dashboard_detalhe_fretes_entrada OPTION (MAXDOP 1, MAX_GRANT_PERCENT = 2);';
EXEC sys.sp_executesql @consulta;

DECLARE @originais bigint, @publicadas bigint, @diferencas bigint;
-- A frequência por assinatura detecta tanto campos alterados quanto linhas repetidas ou ausentes.
WITH original AS (
    SELECT h.assinatura, COUNT_BIG(*) AS quantidade
    FROM #referencia r
    CROSS APPLY (SELECT HASHBYTES('SHA2_256', (SELECT r.* FOR JSON PATH, WITHOUT_ARRAY_WRAPPER, INCLUDE_NULL_VALUES)) AS assinatura) h
    GROUP BY h.assinatura
), publicado AS (
    SELECT h.assinatura, COUNT_BIG(*) AS quantidade
    FROM core.dashboard_detalhe_fretes_leitura p
    CROSS APPLY (SELECT HASHBYTES('SHA2_256', (SELECT p.* FOR JSON PATH, WITHOUT_ARRAY_WRAPPER, INCLUDE_NULL_VALUES)) AS assinatura) h
    GROUP BY h.assinatura
)
SELECT @originais = COALESCE(SUM(o.quantidade), 0), @publicadas = COALESCE(SUM(p.quantidade), 0),
       @diferencas = COUNT_BIG(CASE WHEN o.quantidade IS DISTINCT FROM p.quantidade THEN 1 END)
FROM original o FULL JOIN publicado p ON p.assinatura = o.assinatura
OPTION (MAXDOP 1, MAX_GRANT_PERCENT = 2);

IF @recibo IS DISTINCT FROM (SELECT publicado_utc FROM ctl.dashboard_leituras_publicadas WHERE versao = 46)
    THROW 52171, 'O recibo mudou durante a leitura. Repita após o lote.', 1;
SELECT @originais AS original, @publicadas AS publicado, @diferencas AS grupos_diferentes, 123 AS colunas,
       DATEDIFF_BIG(millisecond, @comeco, SYSUTCDATETIME()) AS duracao_ms, @recibo AS publicado_utc;
IF @diferencas <> 0 THROW 52172, 'A publicação difere da consulta integral nas fontes atuais.', 1;
