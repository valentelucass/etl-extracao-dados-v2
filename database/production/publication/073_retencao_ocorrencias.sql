-- Instalar em uma transação pelo instalador do ETL. A instalação começa sem limpeza ativa.
IF DB_NAME() <> N'ETL_SISTEMA_V2' OR OBJECT_ID(N'pub.customer_service_cobertura_ocorrencias',N'V') IS NULL
    THROW 52300, 'Contrato de ocorrencias do ETL ausente.', 1;
CREATE TABLE ctl.jujuba_retencao_banco (
 id tinyint NOT NULL PRIMARY KEY CHECK(id=1), dias int NULL CHECK(dias>0), revisao rowversion NOT NULL
);
INSERT ctl.jujuba_retencao_banco(id,dias) VALUES (1,NULL);
CREATE TABLE ctl.jujuba_retencao_regra (
 entidade varchar(40) PRIMARY KEY REFERENCES ctl.production_export_template(entity_name),
 modo varchar(8) NOT NULL, dias int NULL,
 CHECK ((modo='own' AND dias IS NOT NULL AND dias>0) OR (modo IN('keep','inherit') AND dias IS NULL))
);
CREATE TABLE ctl.jujuba_retencao_dia (
 entidade varchar(40) NOT NULL, dia date NOT NULL, arquivo uniqueidentifier NOT NULL UNIQUE,
 assinatura char(64) NOT NULL, origem bigint NOT NULL, vigentes bigint NOT NULL, publicadas bigint NOT NULL,
 coberturas int NOT NULL, marcadores int NOT NULL, gravado_utc datetime2(3) NOT NULL DEFAULT SYSUTCDATETIME(),
 PRIMARY KEY(entidade,dia)
);
CREATE TABLE ctl.jujuba_retencao_hold (
 entidade varchar(40) NOT NULL, dia date NOT NULL, motivo nvarchar(240) NOT NULL, PRIMARY KEY(entidade,dia)
);
CREATE TABLE ctl.jujuba_retencao_revisao (
 run_id uniqueidentifier NOT NULL PRIMARY KEY, entidade varchar(40) NOT NULL, dia date NOT NULL,
 detectado_utc datetime2(3) NOT NULL DEFAULT SYSUTCDATETIME()
);
CREATE TABLE ctl.jujuba_retencao_alteracao (
 id bigint IDENTITY PRIMARY KEY, entidade varchar(40) NOT NULL, modo varchar(8) NOT NULL, dias int NULL,
 login_sql sysname NOT NULL DEFAULT ORIGINAL_LOGIN(), gravado_utc datetime2(3) NOT NULL DEFAULT SYSUTCDATETIME()
);
GO
CREATE OR ALTER FUNCTION ctl.ufn_jujuba_retencao_corte(@hoje date,@dias int) RETURNS date
AS BEGIN
 RETURN DATEADD(day,CASE WHEN @dias>DATEDIFF(day,CONVERT(date,'00010101'),@hoje)+1
                    THEN -DATEDIFF(day,CONVERT(date,'00010101'),@hoje) ELSE 1-NULLIF(@dias,0) END,@hoje);
END;
GO
CREATE OR ALTER VIEW ctl.jujuba_retencao_entidades AS
SELECT t.entity_name AS entidade, CONVERT(bit,CASE WHEN t.entity_name='OCORRENCIAS' THEN 1 ELSE 0 END) AS habilitada,
 ISNULL(r.modo,'inherit') AS modo,r.dias,
 CASE WHEN t.entity_name='OCORRENCIAS' THEN CASE COALESCE(r.modo,'inherit') WHEN 'own' THEN r.dias WHEN 'inherit' THEN b.dias END END AS prazo
FROM ctl.production_export_template t CROSS JOIN ctl.jujuba_retencao_banco b
LEFT JOIN ctl.jujuba_retencao_regra r ON r.entidade=t.entity_name;
GO
CREATE OR ALTER PROCEDURE ctl.usp_jujuba_retencao_catalogo AS
BEGIN
 SET NOCOUNT ON;
 SELECT (SELECT CAST(1 AS bit) AS available,b.dias AS defaultDays,CONVERT(char(16),b.revisao,2) AS revision,
  CAST(COALESCE(HAS_PERMS_BY_NAME('ctl.usp_jujuba_retencao_configurar','OBJECT','EXECUTE'),0) AS bit) AS canConfigure,
  JSON_QUERY((SELECT entidade AS name,modo AS mode,dias AS days,habilitada AS supported
   FROM ctl.jujuba_retencao_entidades ORDER BY entidade FOR JSON PATH, INCLUDE_NULL_VALUES)) AS entities
  FROM ctl.jujuba_retencao_banco b FOR JSON PATH, INCLUDE_NULL_VALUES, WITHOUT_ARRAY_WRAPPER) AS payload;
END;
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
  FROM ctl.production_ingestion_run r JOIN alvo a ON a.entidade=r.entity_name WHERE r.run_state='STORED'
  GROUP BY r.entity_name,r.period_start
 ), cobertura AS (
  SELECT c.entity_name AS entidade,c.business_date AS dia,SUM(CONVERT(bigint,r.row_count)) AS vigente
  FROM ctl.production_ingestion_coverage c JOIN alvo a ON a.entidade=c.entity_name JOIN ctl.production_ingestion_run r ON r.run_id=c.latest_run_id
  GROUP BY c.entity_name,c.business_date
 ), dias AS (
  SELECT p.dia,SUM(COALESCE(v.vigente,0)) AS currentRows,SUM(CASE WHEN d.dia IS NULL THEN p.bruto ELSE 0 END) AS sourceRows,
   SUM(ISNULL(d.origem,0)) AS removedRows, CAST(MIN(IIF(d.dia IS NULL,0,1)) AS bit) AS removed,
   CAST(MAX(IIF(h.dia IS NULL,0,1)) AS bit) AS held
  FROM capturas p LEFT JOIN cobertura v ON v.entidade=p.entidade AND v.dia=p.dia
  LEFT JOIN ctl.jujuba_retencao_dia d ON d.entidade=p.entidade AND d.dia=p.dia
  LEFT JOIN ctl.jujuba_retencao_hold h ON h.entidade=p.entidade AND h.dia=p.dia GROUP BY p.dia
 )
 SELECT (SELECT CAST(1 AS bit) AS available,CONVERT(char(10),@hoje,23) AS today,CONVERT(char(10),@corte,23) AS cutoff,
    CONVERT(char(16),b.revisao,2) AS revision,JSON_QUERY((SELECT dia AS day,currentRows,sourceRows,removedRows,removed,held
     FROM dias ORDER BY dia FOR JSON PATH)) AS days FROM ctl.jujuba_retencao_banco b
     FOR JSON PATH,INCLUDE_NULL_VALUES,WITHOUT_ARRAY_WRAPPER) AS payload OPTION(MAXDOP 1);
END;
GO
CREATE OR ALTER PROCEDURE ctl.usp_jujuba_retencao_configurar @entidade nvarchar(128),@modo varchar(8),@dias int,@revisao varchar(16)
AS
BEGIN
 SET NOCOUNT ON; SET XACT_ABORT ON;
 IF @entidade IS NULL OR @entidade NOT IN (N'OCORRENCIAS',N'*') OR @modo IS NULL OR @modo NOT IN ('own','inherit','keep')
    OR (@entidade=N'*' AND @modo='inherit') OR @dias IS NULL OR (@modo='own' AND @dias<1) OR (@modo<>'own' AND @dias<>0)
    THROW 52301,'Entidade ou prazo invalido.',1;
 BEGIN TRY
  BEGIN TRAN;
  DECLARE @lk int;
  EXEC @lk=sys.sp_getapplock @Resource=N'jujuba:retencao:regra',@LockMode='Exclusive',@LockOwner='Transaction',@LockTimeout=2500;
  IF @lk<0 THROW 52302,'Retencao em andamento. Atualize a previa.',1;
  IF NOT EXISTS(SELECT 1 FROM ctl.jujuba_retencao_banco WITH(UPDLOCK,HOLDLOCK) WHERE revisao=TRY_CONVERT(binary(8),@revisao,2))
      THROW 52303,'A regra mudou. Atualize a previa.',1;
  UPDATE ctl.jujuba_retencao_banco SET dias=CASE WHEN @entidade=N'*' THEN NULLIF(@dias,0) ELSE dias END WHERE id=1;
  IF @entidade<>N'*' BEGIN
   UPDATE ctl.jujuba_retencao_regra SET modo=@modo,dias=NULLIF(@dias,0) WHERE entidade=@entidade;
   IF @@ROWCOUNT=0 INSERT ctl.jujuba_retencao_regra(entidade,modo,dias) VALUES (@entidade,@modo,NULLIF(@dias,0));
  END;
  INSERT ctl.jujuba_retencao_alteracao(entidade,modo,dias) VALUES (@entidade,@modo,NULLIF(@dias,0));
  COMMIT;
  SELECT N'{"available":true}' AS payload;
 END TRY BEGIN CATCH IF XACT_STATE()<>0 ROLLBACK; THROW; END CATCH;
END;
GO
-- O planejador omite dias retirados; nunca os anuncia como cobertos.
CREATE OR ALTER PROCEDURE ctl.usp_production_ingestion_plan
 @source_scope char(64),@anchor date,@until date,@entity_name varchar(40)='FATURAS_POR_CLIENTE' AS
BEGIN
 SET NOCOUNT ON;
 IF @anchor IS NULL OR @until IS NULL OR @until<@anchor OR DATEDIFF(day,@anchor,@until)>3660
 THROW 51908,'BOUNDED_RECOVERY_RANGE_REQUIRED',1;
 IF NOT EXISTS(SELECT 1 FROM ctl.production_export_template WHERE entity_name COLLATE Latin1_General_100_BIN2=@entity_name COLLATE Latin1_General_100_BIN2)
 THROW 51924,'CLOSED_RECEIVING_ENTITY_REQUIRED',1;
 DECLARE @corte date=ctl.ufn_jujuba_retencao_corte(CONVERT(date,SYSDATETIMEOFFSET() AT TIME ZONE 'E. South America Standard Time'),
    (SELECT prazo FROM ctl.jujuba_retencao_entidades WHERE entidade=@entity_name));
 ;WITH days AS(SELECT @anchor AS business_date UNION ALL SELECT DATEADD(day,1,business_date) FROM days WHERE business_date<@until)
 SELECT d.business_date,CONVERT(bit,CASE WHEN c.latest_run_id IS NULL THEN 0 ELSE 1 END) AS stored
 FROM days d LEFT JOIN ctl.production_ingestion_coverage c ON c.source_scope=@source_scope AND c.entity_name=@entity_name AND c.business_date=d.business_date
 WHERE (@corte IS NULL OR d.business_date>=@corte)
 AND NOT EXISTS(SELECT 1 FROM ctl.jujuba_retencao_dia a WHERE a.entidade=@entity_name AND a.dia=d.business_date)
 ORDER BY d.business_date OPTION(MAXRECURSION 3661);
END;
GO
CREATE OR ALTER PROCEDURE ctl.usp_production_ingestion_start
 @run_id uniqueidentifier,@source_scope char(64),@business_date date,@request_sha256 char(64),
 @entity_name varchar(40)='FATURAS_POR_CLIENTE' AS
BEGIN
 SET NOCOUNT ON; SET XACT_ABORT ON;
 IF @@TRANCOUNT<>0 THROW 51901,'OUTER_TRANSACTION_REFUSED',1;
 IF @run_id IS NULL OR @business_date IS NULL OR @source_scope IS NULL OR @request_sha256 IS NULL
 OR LEN(@source_scope)<>64 OR LEN(@request_sha256)<>64
 OR @source_scope COLLATE Latin1_General_100_BIN2 LIKE '%[^0-9a-f]%' OR @request_sha256 COLLATE Latin1_General_100_BIN2 LIKE '%[^0-9a-f]%'
 THROW 51902,'EXPLICIT_SCOPE_REQUIRED',1;
 IF NOT EXISTS(SELECT 1 FROM ctl.production_export_template WHERE entity_name COLLATE Latin1_General_100_BIN2=@entity_name COLLATE Latin1_General_100_BIN2)
 THROW 51924,'CLOSED_RECEIVING_ENTITY_REQUIRED',1;
 BEGIN TRY
  BEGIN TRAN;
  IF @entity_name='OCORRENCIAS' BEGIN
   DECLARE @lk int;
   EXEC @lk=sys.sp_getapplock @Resource=N'jujuba:retencao:regra',@LockMode='Shared',@LockOwner='Transaction',@LockTimeout=2200;
   IF @lk<0 THROW 52302,'Retencao em andamento.',1;
   DECLARE @chave_dia nvarchar(100)=N'jujuba:retencao:OCORRENCIAS:'+CONVERT(nvarchar(10),@business_date,23);
   EXEC @lk=sys.sp_getapplock @Resource=@chave_dia,@LockMode='Shared',@LockOwner='Transaction',@LockTimeout=2200;
   IF @lk<0 THROW 52302,'Dia em retirada.',1;
   IF EXISTS(SELECT 1 FROM ctl.jujuba_retencao_dia WHERE entidade=@entity_name AND dia=@business_date)
     OR @business_date<ctl.ufn_jujuba_retencao_corte(CONVERT(date,SYSDATETIMEOFFSET() AT TIME ZONE 'E. South America Standard Time'),
       (SELECT prazo FROM ctl.jujuba_retencao_entidades WHERE entidade=@entity_name))
   BEGIN
    IF NOT EXISTS(SELECT 1 FROM ctl.jujuba_retencao_revisao WITH(UPDLOCK,HOLDLOCK) WHERE run_id=@run_id)
      INSERT ctl.jujuba_retencao_revisao(run_id,entidade,dia) VALUES(@run_id,@entity_name,@business_date);
    COMMIT;
    THROW 52304,'RETENTION_DATE_REQUIRES_REVIEW',1;
   END;
  END;
  INSERT ctl.production_ingestion_run(run_id,source_scope,entity_name,period_start,period_end,run_state,request_sha256)
  VALUES(@run_id,@source_scope,@entity_name,@business_date,@business_date,'RUNNING',@request_sha256);
  COMMIT;
 END TRY BEGIN CATCH IF XACT_STATE()<>0 ROLLBACK; THROW; END CATCH;
END;
GO
CREATE OR ALTER VIEW ctl.jujuba_retencao_elegiveis AS
SELECT DISTINCT r.entity_name AS entidade,r.period_start AS dia
FROM ctl.production_ingestion_run r JOIN ctl.jujuba_retencao_entidades e ON e.entidade=r.entity_name
WHERE e.habilitada=1 AND r.run_state='STORED' AND r.period_start=r.period_end
 AND r.period_start<ctl.ufn_jujuba_retencao_corte(CONVERT(date,SYSDATETIMEOFFSET() AT TIME ZONE 'E. South America Standard Time'),e.prazo)
 AND NOT EXISTS(SELECT 1 FROM ctl.jujuba_retencao_dia a WHERE a.entidade=r.entity_name AND a.dia=r.period_start)
 AND NOT EXISTS(SELECT 1 FROM ctl.jujuba_retencao_hold h WHERE h.entidade=r.entity_name AND h.dia=r.period_start)
 AND NOT EXISTS(SELECT 1 FROM ctl.production_ingestion_run p WHERE p.entity_name=r.entity_name AND p.period_start=r.period_start AND p.run_state='RUNNING');
GO
CREATE OR ALTER PROCEDURE ctl.usp_jujuba_retencao_proxima AS
BEGIN SET NOCOUNT ON; SELECT TOP(1) entidade,dia FROM ctl.jujuba_retencao_elegiveis ORDER BY dia,entidade; END;
GO
-- Linhas canônicas do arquivo. O envelope é texto para preservar cada caractere e cada repetição.
CREATE OR ALTER VIEW ctl.jujuba_retencao_arquivo_origem AS
SELECT r.period_start AS dia,1 AS camada,CONVERT(varchar(64),r.run_id) COLLATE Latin1_General_100_BIN2 AS chave,CONVERT(bigint,0) AS ordinal,
 (SELECT 'captura' AS tipo,r.run_id,r.source_scope,r.entity_name,r.period_start,r.period_end,r.started_utc,
   r.completed_utc,r.run_state,r.request_sha256,r.capture_sha256,r.row_count,r.failure_code
  FOR JSON PATH, INCLUDE_NULL_VALUES, WITHOUT_ARRAY_WRAPPER) AS linha
FROM ctl.production_ingestion_run r WHERE r.entity_name='OCORRENCIAS' AND r.run_state='STORED'
UNION ALL
SELECT r.period_start,2,CONVERT(varchar(64),r.run_id),o.occurrence_ordinal,
 (SELECT 'origem' AS tipo,o.run_id,o.occurrence_ordinal,o.business_date,CONVERT(nvarchar(max),o.source_envelope) AS source_envelope,
   CONVERT(char(64),o.envelope_sha256,2) AS envelope_sha256 FOR JSON PATH, INCLUDE_NULL_VALUES, WITHOUT_ARRAY_WRAPPER)
FROM ctl.production_ingestion_run r JOIN stg.production_export_occurrence o WITH(FORCESEEK) ON o.run_id=r.run_id
WHERE r.entity_name='OCORRENCIAS' AND r.run_state='STORED'
UNION ALL
SELECT c.business_date,3,c.source_scope,0,
 (SELECT 'cobertura' AS tipo,c.source_scope,c.entity_name,c.business_date,c.latest_run_id,c.stored_utc FOR JSON PATH, INCLUDE_NULL_VALUES, WITHOUT_ARRAY_WRAPPER)
FROM ctl.production_ingestion_coverage c WHERE c.entity_name='OCORRENCIAS'
UNION ALL
SELECT p.data_particao,4,p.escopo,0,
 (SELECT 'marcador' AS tipo,p.escopo,p.data_particao,p.captura FOR JSON PATH, INCLUDE_NULL_VALUES, WITHOUT_ARRAY_WRAPPER)
FROM ctl.dashboard_performance_ocorrencias_particao p
UNION ALL
SELECT p.data_particao,5,p.escopo,o.ordem,
 (SELECT 'publicacao' AS tipo,o.escopo,o.data_particao,o.ordem,o.minuta,o.nota_fiscal,o.cte,o.codigo,o.descricao,
   o.ocorrido_em,o.filial,o.status_entrega FOR JSON PATH, INCLUDE_NULL_VALUES, WITHOUT_ARRAY_WRAPPER)
FROM ctl.dashboard_performance_ocorrencias_particao p
JOIN core.dashboard_performance_ocorrencias o WITH(FORCESEEK) ON o.escopo=p.escopo AND o.data_particao=p.data_particao;
GO
CREATE OR ALTER PROCEDURE ctl.usp_jujuba_retencao_exportar @entidade varchar(40),@dia date AS
BEGIN
 SET NOCOUNT ON;
 IF NOT EXISTS(SELECT 1 FROM ctl.jujuba_retencao_elegiveis WHERE entidade=@entidade AND dia=@dia)
  THROW 52305,'Dia fora do escopo de retirada.',1;
 SELECT linha FROM ctl.jujuba_retencao_arquivo_origem WHERE dia=@dia ORDER BY camada,chave,ordinal
 OPTION(MAXDOP 1,MAX_GRANT_PERCENT=1,RECOMPILE);
END;
GO
CREATE OR ALTER PROCEDURE ctl.usp_jujuba_retencao_retirar
 @entidade varchar(40),@dia date,@arquivo uniqueidentifier,@assinatura varchar(64) WITH EXECUTE AS OWNER AS
BEGIN
 SET NOCOUNT ON; SET XACT_ABORT ON; SET DEADLOCK_PRIORITY LOW; SET LOCK_TIMEOUT 3500;
 IF @entidade IS NULL OR @entidade<>'OCORRENCIAS' OR @dia IS NULL OR @arquivo IS NULL OR @assinatura IS NULL
  OR DATALENGTH(@assinatura)<>64 OR @assinatura COLLATE Latin1_General_100_BIN2 LIKE '%[^0-9A-F]%'
  THROW 52305,'Identificacao da retirada invalida.',1;
 BEGIN TRY
  BEGIN TRAN;
  DECLARE @lk int;
  EXEC @lk=sys.sp_getapplock @Resource=N'jujuba:retencao:regra',@LockMode='Shared',@LockOwner='Transaction',@LockTimeout=3500;
  IF @lk<0 THROW 52302,'Regra ocupada. Tente novamente.',1;
  DECLARE @chave_dia nvarchar(100)=N'jujuba:retencao:OCORRENCIAS:'+CONVERT(nvarchar(10),@dia,23);
  EXEC @lk=sys.sp_getapplock @Resource=@chave_dia,@LockMode='Exclusive',@LockOwner='Transaction',@LockTimeout=3500;
  IF @lk<0 THROW 52302,'Dia ocupado. Tente novamente.',1;
  EXEC @lk=sys.sp_getapplock @Resource=N'dashboard:fatos-fretes',@LockMode='Exclusive',@LockOwner='Transaction',@LockTimeout=3500;
  IF @lk<0 THROW 52302,'Publicacao em andamento. Tente novamente.',1;
  IF EXISTS(SELECT 1 FROM ctl.jujuba_retencao_dia WHERE entidade=@entidade AND dia=@dia AND arquivo=@arquivo AND assinatura=@assinatura)
  BEGIN COMMIT; RETURN; END;
  IF EXISTS(SELECT 1 FROM ctl.jujuba_retencao_hold WITH(UPDLOCK,HOLDLOCK) WHERE entidade=@entidade AND dia=@dia)
     OR NOT EXISTS(SELECT 1 FROM ctl.jujuba_retencao_elegiveis WHERE entidade=@entidade AND dia=@dia)
   THROW 52305,'Dia fora do escopo de retirada.',1;

  -- Bloqueia quando uma nova dependência puder alterar o alcance do DELETE.
  DECLARE @alvos TABLE(id int PRIMARY KEY);
  INSERT @alvos VALUES(OBJECT_ID('stg.production_export_occurrence')),(OBJECT_ID('core.dashboard_performance_ocorrencias')),
   (OBJECT_ID('ctl.dashboard_performance_ocorrencias_particao')),(OBJECT_ID('ctl.production_ingestion_coverage'));
  IF EXISTS(SELECT 1 FROM sys.foreign_keys WHERE referenced_object_id IN(SELECT id FROM @alvos) AND delete_referential_action<>0)
   OR EXISTS(SELECT 1 FROM sys.triggers WHERE parent_id IN(SELECT id FROM @alvos) AND is_disabled=0)
   THROW 52306,'As dependencias da publicacao mudaram.',1;

  DECLARE @capturas TABLE(run_id uniqueidentifier PRIMARY KEY,escopo char(64) COLLATE Latin1_General_100_BIN2,esperado bigint);
  INSERT @capturas SELECT run_id,source_scope,row_count FROM ctl.production_ingestion_run
   WHERE entity_name=@entidade AND period_start=@dia AND period_end=@dia AND run_state='STORED';
  DECLARE @particoes TABLE(escopo char(64) COLLATE Latin1_General_100_BIN2 PRIMARY KEY,captura uniqueidentifier,vigentes bigint);
  INSERT @particoes SELECT c.source_scope,c.latest_run_id,r.esperado FROM ctl.production_ingestion_coverage c
    JOIN @capturas r ON r.run_id=c.latest_run_id AND r.escopo=c.source_scope WHERE c.entity_name=@entidade AND c.business_date=@dia;
  IF EXISTS(SELECT 1 FROM ctl.production_ingestion_coverage c JOIN @capturas r ON c.latest_run_id=r.run_id
   WHERE c.entity_name<>@entidade OR c.business_date<>@dia OR c.source_scope<>r.escopo)
   OR EXISTS(SELECT source_scope,latest_run_id FROM ctl.production_ingestion_coverage WHERE entity_name=@entidade AND business_date=@dia
              EXCEPT SELECT escopo,captura FROM @particoes)
   OR EXISTS(SELECT escopo,captura FROM ctl.dashboard_performance_ocorrencias_particao WHERE data_particao=@dia EXCEPT SELECT escopo,captura FROM @particoes)
   OR EXISTS(SELECT escopo,captura FROM @particoes EXCEPT SELECT escopo,captura FROM ctl.dashboard_performance_ocorrencias_particao WHERE data_particao=@dia)
   OR EXISTS(SELECT escopo FROM @capturas EXCEPT SELECT escopo FROM @particoes)
   THROW 52306,'Cobertura e publicacao de ocorrencias divergentes.',1;
  IF EXISTS (SELECT 1 FROM @capturas r OUTER APPLY (
    SELECT COUNT_BIG(*) AS total,MIN(o.business_date) AS primeiro,MAX(o.business_date) AS ultimo
    FROM stg.production_export_occurrence o WITH(FORCESEEK) WHERE o.run_id=r.run_id
   ) o WHERE r.esperado<>o.total OR o.primeiro<>@dia OR o.ultimo<>@dia)
   OR EXISTS (SELECT 1 FROM @particoes p CROSS APPLY (SELECT COUNT_BIG(*) AS total FROM core.dashboard_performance_ocorrencias o WITH(FORCESEEK)
            WHERE o.escopo=p.escopo AND o.data_particao=@dia) o WHERE o.total<>p.vigentes)
   THROW 52306,'Contagem da captura ou do Performance divergente.',1;
  DECLARE @conferido char(64);
  SELECT @conferido=CONVERT(char(64),HASHBYTES('SHA2_256',
   STRING_AGG(CONVERT(varchar(max),CONVERT(char(64),HASHBYTES('SHA2_256',linha),2)),'')
   WITHIN GROUP(ORDER BY camada,chave,ordinal)),2)
  FROM ctl.jujuba_retencao_arquivo_origem WHERE dia=@dia OPTION(MAXDOP 1,MAX_GRANT_PERCENT=1,RECOMPILE);
  IF @conferido IS NULL OR @conferido<>@assinatura THROW 52307,'Arquivo difere do conteudo atual.',1;

  DECLARE @origem bigint=(SELECT SUM(esperado) FROM @capturas),@vigentes bigint=(SELECT SUM(vigentes) FROM @particoes),
   @partes int=(SELECT COUNT(*) FROM @particoes);
  DELETE o FROM @capturas r INNER LOOP JOIN stg.production_export_occurrence o ON o.run_id=r.run_id WHERE o.business_date=@dia OPTION(MAXDOP 1);
  IF ROWCOUNT_BIG()<>@origem THROW 52306,'Retirada incompleta na origem.',1;
  DELETE o FROM @particoes p INNER LOOP JOIN core.dashboard_performance_ocorrencias o ON o.escopo=p.escopo AND o.data_particao=@dia OPTION(MAXDOP 1);
  IF ROWCOUNT_BIG()<>@vigentes THROW 52306,'Retirada incompleta no Performance.',1;
  DELETE p FROM ctl.dashboard_performance_ocorrencias_particao p JOIN @particoes a ON a.escopo=p.escopo AND a.captura=p.captura WHERE p.data_particao=@dia;
  IF @@ROWCOUNT<>@partes THROW 52306,'Marcadores divergentes.',1;
  DELETE c FROM ctl.production_ingestion_coverage c JOIN @particoes a ON a.escopo=c.source_scope AND a.captura=c.latest_run_id WHERE c.entity_name=@entidade AND c.business_date=@dia;
  IF @@ROWCOUNT<>@partes THROW 52306,'Coberturas divergentes.',1;
  INSERT ctl.jujuba_retencao_dia(entidade,dia,arquivo,assinatura,origem,vigentes,publicadas,coberturas,marcadores)
   VALUES(@entidade,@dia,@arquivo,@conferido,@origem,@vigentes,@vigentes,@partes,@partes);
  COMMIT;
 END TRY BEGIN CATCH IF XACT_STATE()<>0 ROLLBACK; THROW; END CATCH;
END;
GO
GRANT EXECUTE ON ctl.usp_jujuba_retencao_catalogo TO v2_lifecycle_reviewer,v2_retention_governor,usuario_etl;
GRANT EXECUTE ON ctl.usp_jujuba_retencao_previa TO v2_lifecycle_reviewer,v2_retention_governor,usuario_etl;
GRANT EXECUTE ON ctl.usp_jujuba_retencao_configurar TO v2_retention_governor;
GRANT EXECUTE ON ctl.usp_jujuba_retencao_proxima TO v2_runtime;
GRANT EXECUTE ON ctl.usp_jujuba_retencao_exportar TO v2_runtime;
GRANT EXECUTE ON ctl.usp_jujuba_retencao_retirar TO v2_runtime;
GO
-- A cópia de recuperação tem sete dias de vida, contados a partir do COMMIT da retirada.
CREATE OR ALTER PROCEDURE ctl.usp_jujuba_retencao_arquivos_vencidos AS
BEGIN
 SET NOCOUNT ON;
 SELECT arquivo,dia FROM ctl.jujuba_retencao_dia
 WHERE entidade='OCORRENCIAS' AND gravado_utc < DATEADD(day,-7,SYSUTCDATETIME()) ORDER BY dia;
END;
GO
GRANT EXECUTE ON ctl.usp_jujuba_retencao_arquivos_vencidos TO v2_runtime;
GO
