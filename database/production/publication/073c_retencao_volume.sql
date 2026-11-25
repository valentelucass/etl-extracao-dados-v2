ALTER TABLE ctl.jujuba_retencao_dia ADD bytes_apagados bigint NULL
    CONSTRAINT CK_jujuba_retencao_bytes_apagados CHECK (bytes_apagados >= 0);
GO
-- Mede os valores no tipo original do SQL. Cabeçalhos de captura permanecem no ETL.
-- A mesma expressão mede os dias futuros e os arquivos de recuperação ainda disponíveis.
CREATE OR ALTER FUNCTION ctl.ufn_jujuba_retencao_bytes_linha(@linha nvarchar(max))
RETURNS TABLE AS RETURN (
 SELECT j.tipo, CASE j.tipo
   WHEN 'captura' THEN CONVERT(bigint,0)
   WHEN 'origem' THEN DATALENGTH(j.source_envelope) + DATALENGTH(j.run_id) + DATALENGTH(j.occurrence_ordinal)
       + DATALENGTH(j.business_date) + DATALENGTH(CONVERT(binary(32),j.envelope_sha256,2))
   WHEN 'publicacao' THEN DATALENGTH(j.escopo) + DATALENGTH(j.data_particao) + DATALENGTH(j.ordem)
       + ISNULL(DATALENGTH(j.minuta),0) + ISNULL(DATALENGTH(j.nota_fiscal),0) + ISNULL(DATALENGTH(j.cte),0)
       + ISNULL(DATALENGTH(j.codigo),0) + ISNULL(DATALENGTH(j.descricao),0) + ISNULL(DATALENGTH(j.ocorrido_em),0)
       + ISNULL(DATALENGTH(j.filial),0) + ISNULL(DATALENGTH(j.status_entrega),0)
   WHEN 'cobertura' THEN DATALENGTH(j.source_scope) + DATALENGTH(j.entity_name) + DATALENGTH(j.business_date)
       + DATALENGTH(j.latest_run_id) + DATALENGTH(j.stored_utc)
   WHEN 'marcador' THEN DATALENGTH(j.escopo) + DATALENGTH(j.data_particao) + DATALENGTH(j.captura)
 END AS bytes
 FROM OPENJSON(@linha) WITH (
   tipo varchar(16), run_id uniqueidentifier, occurrence_ordinal int, business_date date,
   source_envelope nvarchar(max), envelope_sha256 char(64), escopo char(64), data_particao date, ordem bigint,
   minuta bigint, nota_fiscal nvarchar(100), cte nvarchar(100), codigo int, descricao nvarchar(255),
   ocorrido_em datetime2(3), filial nvarchar(255), status_entrega nvarchar(100), source_scope char(64),
   entity_name varchar(40), latest_run_id uniqueidentifier, stored_utc datetime2(3), captura uniqueidentifier
 ) AS j
);
GO
-- Acrescenta a medição à mesma leitura que assina as linhas e à mesma transação que as retira.
DECLARE @ddl nvarchar(max)=OBJECT_DEFINITION(OBJECT_ID(N'ctl.usp_jujuba_retencao_retirar','P'));
DECLARE @mudancas TABLE(id int PRIMARY KEY,antes nvarchar(200),depois nvarchar(500));
INSERT @mudancas VALUES
 (1,N'DECLARE @conferido char(64);',N'DECLARE @conferido char(64), @bytes bigint, @invalidas bigint;'),
 (2,N'SELECT @conferido=CONVERT',N'SELECT @bytes=SUM(m.bytes), @invalidas=COUNT_BIG(*)-COUNT_BIG(m.bytes), @conferido=CONVERT'),
 (3,N'FROM ctl.jujuba_retencao_arquivo_origem WHERE dia=@dia OPTION',N'FROM ctl.jujuba_retencao_arquivo_origem CROSS APPLY ctl.ufn_jujuba_retencao_bytes_linha(linha) m WHERE dia=@dia OPTION'),
 (4,N'IF @conferido IS NULL',N'IF @bytes IS NULL OR @invalidas<>0 THROW 52308,''Volume de retirada incompleto.'',1;
  IF @conferido IS NULL'),
 (5,N'publicadas,coberturas,marcadores)',N'publicadas,coberturas,marcadores,bytes_apagados)'),
 (6,N'@vigentes,@partes,@partes);',N'@vigentes,@partes,@partes,@bytes);');
DECLARE @id int,@antes nvarchar(200),@depois nvarchar(500),@pos int;
WHILE EXISTS(SELECT 1 FROM @mudancas)
BEGIN
 SELECT TOP(1) @id=id,@antes=antes,@depois=depois FROM @mudancas ORDER BY id;
 SET @pos=CHARINDEX(@antes,@ddl);
 IF @pos IS NULL OR @pos=0 OR CHARINDEX(@antes,@ddl,@pos+LEN(@antes))>0 THROW 52308,'Definicao da retirada incompativel.',1;
 SET @ddl=STUFF(@ddl,@pos,LEN(@antes),@depois);
 DELETE FROM @mudancas WHERE id=@id;
END;
SET @ddl=N'ALTER '+SUBSTRING(@ddl,CHARINDEX(N'PROCEDURE',@ddl),LEN(@ddl));
EXEC sys.sp_executesql @ddl;
GO
CREATE OR ALTER PROCEDURE ctl.usp_jujuba_retencao_previa @entidade nvarchar(128),@dias int AS
BEGIN
 SET NOCOUNT ON;
 IF @dias IS NULL OR @dias<0 OR (@entidade<>N'*' AND NOT EXISTS(SELECT 1 FROM ctl.jujuba_retencao_entidades WHERE entidade=@entidade))
     THROW 52301,'Entidade ou prazo invalido.',1;
 DECLARE @hoje date=CONVERT(date,SYSDATETIMEOFFSET() AT TIME ZONE 'E. South America Standard Time');
 DECLARE @corte date=ctl.ufn_jujuba_retencao_corte(@hoje,@dias);
 ;WITH alvo AS (
  SELECT entidade FROM ctl.jujuba_retencao_entidades WHERE entidade=@entidade OR (@entidade=N'*' AND habilitada=1 AND modo='inherit')
 ), capturas AS (
  SELECT r.entity_name AS entidade,r.period_start AS dia,SUM(CONVERT(bigint,r.row_count)) AS bruto
  FROM ctl.production_ingestion_run r JOIN alvo a ON a.entidade=r.entity_name WHERE r.run_state='STORED' GROUP BY r.entity_name,r.period_start
 ), cobertura AS (
  SELECT c.entity_name AS entidade,c.business_date AS dia,SUM(CONVERT(bigint,r.row_count)) AS vigente
  FROM ctl.production_ingestion_coverage c JOIN alvo a ON a.entidade=c.entity_name JOIN ctl.production_ingestion_run r ON r.run_id=c.latest_run_id
  GROUP BY c.entity_name,c.business_date
 ), dias AS (
  SELECT p.dia,SUM(COALESCE(v.vigente,0)) AS currentRows,SUM(CASE WHEN d.dia IS NULL THEN p.bruto ELSE 0 END) AS sourceRows,
   SUM(ISNULL(d.origem,0)) AS removedRows,CAST(MIN(IIF(d.dia IS NULL,0,1)) AS bit) AS removed,
   CASE WHEN COUNT_BIG(d.dia)=COUNT_BIG(d.bytes_apagados) THEN CONVERT(varchar(20),SUM(ISNULL(d.bytes_apagados,0))) END AS removedBytes,
   CAST(MAX(IIF(h.dia IS NULL,0,1)) AS bit) AS held
  FROM capturas p LEFT JOIN cobertura v ON v.entidade=p.entidade AND v.dia=p.dia
  LEFT JOIN ctl.jujuba_retencao_dia d ON d.entidade=p.entidade AND d.dia=p.dia
  LEFT JOIN ctl.jujuba_retencao_hold h ON h.entidade=p.entidade AND h.dia=p.dia GROUP BY p.dia
 )
 SELECT (SELECT CAST(1 AS bit) AS available,CONVERT(char(10),@hoje,23) AS today,CONVERT(char(10),@corte,23) AS cutoff,
  CONVERT(char(16),CONVERT(binary(8),b.revisao),2) AS revision,JSON_QUERY((SELECT dia AS day,currentRows,sourceRows,removedRows,removed,held,removedBytes
    FROM dias ORDER BY dia FOR JSON PATH, INCLUDE_NULL_VALUES)) AS days FROM ctl.jujuba_retencao_banco b
  FOR JSON PATH,INCLUDE_NULL_VALUES,WITHOUT_ARRAY_WRAPPER) AS payload OPTION(MAXDOP 1);
END;
GO
