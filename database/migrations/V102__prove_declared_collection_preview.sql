-- Explicit bounded Coletas universe; four actual traversals, existing preview kernel.
-- Declared universes have no local absence application capability. V092 legacy behavior is retained.
SET ANSI_NULLS ON;SET QUOTED_IDENTIFIER ON;
IF DB_NAME()<>N'ETL_SISTEMA_V2_SHADOW' OR CONVERT(NVARCHAR(128),SERVERPROPERTY('MachineName'))<>CONVERT(NVARCHAR(128),HOST_NAME())
 THROW 53840,N'INTEGRAL_MIGRATION_LOCAL_SHADOW_REQUIRED',1;
GO
ALTER TABLE recon.analytic_collection_sweep_cycle ADD declared_universe NVARCHAR(MAX) COLLATE Latin1_General_100_BIN2 NULL;
GO
ALTER TABLE recon.analytic_collection_sweep_cycle ADD CONSTRAINT CK_ana_collection_declared_universe
 CHECK(declared_universe IS NULL OR(ISJSON(declared_universe)=1 AND DATALENGTH(declared_universe)<=16384 AND universe_roots<=32));
GO
CREATE OR ALTER PROCEDURE recon.usp_prepare_analytic_collection_sweep
 @run_id UNIQUEIDENTIFIER,@cycle_id UNIQUEIDENTIFIER,@date DATE,@universe INT,@omit_first BIT,
 @contract CHAR(64),@snapshot CHAR(64),@policy CHAR(64),@scope CHAR(64),@binding CHAR(64),
 @proofs recon.analytic_collection_sweep_proofs READONLY,@declared_universe NVARCHAR(MAX)=NULL AS
BEGIN
 SET NOCOUNT ON;SET XACT_ABORT OFF;
 DECLARE @rel UNIQUEIDENTIFIER,@per INT,@maximum INT,@roots INT=@universe-CONVERT(INT,@omit_first),@pages INT,@now DATETIME2(7)=SYSUTCDATETIME();
 SELECT @rel=g.relational_run,@maximum=a.maximum_rows,@per=r.page_size FROM ctl.analytic_lab_source_group g
 JOIN ctl.analytic_lab_run a ON a.run_id=g.run_id JOIN ctl.relational_lab_run r ON r.run_id=g.relational_run
 WHERE g.run_id=@run_id AND @date>=a.window_start AND @date<a.window_end_exclusive;
 IF @@TRANCOUNT=0 OR @rel IS NULL OR @cycle_id IS NULL OR @date IS NULL OR @universe IS NULL OR @omit_first IS NULL
 OR @contract IS NULL OR @snapshot IS NULL OR @policy IS NULL OR @scope IS NULL OR @binding IS NULL
 OR @universe NOT BETWEEN 2 AND 4096 OR @roots<1 OR @roots*3>@maximum
 OR (SELECT COUNT_BIG(*) FROM @proofs)<>4 OR EXISTS(SELECT 1 FROM @proofs WHERE ordinal NOT BETWEEN 1 AND 4)
 OR @policy LIKE '%[^0-9a-f]%' OR @scope LIKE '%[^0-9a-f]%' OR @binding LIKE '%[^0-9a-f]%'
 THROW 53771,N'ANA_COLLECTION_SWEEP_SCOPE_BOUND',1;
 DECLARE @rows INT=@roots*3;
 CREATE TABLE #declared(source_key NVARCHAR(128) COLLATE Latin1_General_100_BIN2 NOT NULL PRIMARY KEY,physical_rows INT NOT NULL);
 IF @declared_universe IS NOT NULL
 BEGIN
  IF ISJSON(@declared_universe)<>1 OR LEFT(@declared_universe,1)<>N'[' OR DATALENGTH(@declared_universe)>16384
  OR @universe>32 THROW 53860,N'ANA_COLLECTION_DECLARED_UNIVERSE',1;
  IF EXISTS(SELECT 1 FROM OPENJSON(@declared_universe) j WHERE j.type<>5)
  OR EXISTS(SELECT 1 FROM OPENJSON(@declared_universe) j CROSS APPLY OPENJSON(j.value) f WHERE f.[key] NOT IN(N'key',N'rows'))
  OR EXISTS(SELECT 1 FROM OPENJSON(@declared_universe) j WHERE (SELECT COUNT_BIG(*) FROM OPENJSON(j.value))<>2
   OR (SELECT COUNT_BIG(*) FROM OPENJSON(j.value) WHERE [key]=N'key' AND type=1)<>1
   OR (SELECT COUNT_BIG(*) FROM OPENJSON(j.value) WHERE [key]=N'rows' AND type=2)<>1)
  THROW 53860,N'ANA_COLLECTION_DECLARED_UNIVERSE',1;
  INSERT #declared SELECT source_key,physical_rows FROM OPENJSON(@declared_universe)
   WITH(source_key NVARCHAR(128) '$.key',physical_rows INT '$.rows');
  IF (SELECT COUNT_BIG(*) FROM #declared)<>@universe
  OR (SELECT COUNT_BIG(*) FROM #declared WHERE physical_rows=0)<>CONVERT(INT,@omit_first)
  OR EXISTS(SELECT 1 FROM #declared WHERE physical_rows NOT BETWEEN 0 AND 1000
    OR source_key<>CONCAT(N'INTEGER:',TRY_CONVERT(BIGINT,SUBSTRING(source_key,9,128))) COLLATE Latin1_General_100_BIN2
    OR TRY_CONVERT(BIGINT,SUBSTRING(source_key,9,128)) IS NULL OR TRY_CONVERT(BIGINT,SUBSTRING(source_key,9,128))<=0)
  THROW 53860,N'ANA_COLLECTION_DECLARED_UNIVERSE',1;
  SELECT @rows=SUM(physical_rows) FROM #declared;
  IF @rows NOT BETWEEN 1 AND 10000 OR @rows>@maximum THROW 53860,N'ANA_COLLECTION_DECLARED_UNIVERSE',1;
 END;
 SET @pages=(@rows+@per-1)/@per+1;
 IF @declared_universe IS NOT NULL
  SELECT @pages=a.pages_fetched FROM @proofs p JOIN ctl.execution_audit a ON a.execution_id=p.execution_id WHERE p.ordinal=1;
 IF @pages IS NULL OR @pages NOT BETWEEN 2 AND 1000 THROW 53776,N'ANA_COLLECTION_SWEEP_PAGES_INCOMPLETE',1;
 DECLARE @expected CHAR(64)=LOWER(CONVERT(CHAR(64),HASHBYTES('SHA2_256',CONVERT(VARBINARY(MAX),CONCAT(N'synthetic-analytic-collection-snapshot-v1|universe=',@universe,N'|omit=',CONVERT(INT,@omit_first),N'|date=',CONVERT(NVARCHAR(10),@date,23)))),2));
 IF @declared_universe IS NOT NULL
  SET @expected=LOWER(CONVERT(CHAR(64),HASHBYTES('SHA2_256',CONVERT(VARBINARY(MAX),CONCAT(
    N'synthetic-analytic-collection-snapshot-v2|date=',CONVERT(NVARCHAR(10),@date,23),N'|universe=',@declared_universe))),2));
 IF @snapshot<>@expected COLLATE Latin1_General_100_BIN2 THROW 53772,N'ANA_COLLECTION_SWEEP_SNAPSHOT_UNPROVED',1;
 EXEC ctl.usp_analytic_lab_lock @run_id,'DIMENSION';EXEC ctl.usp_relational_lab_lock @rel;
 IF EXISTS(SELECT 1 FROM recon.analytic_collection_sweep_cycle WHERE cycle_id=@cycle_id)
 BEGIN
  IF NOT EXISTS(SELECT 1 FROM recon.analytic_collection_sweep_cycle WHERE cycle_id=@cycle_id AND run_id=@run_id AND business_date=@date AND universe_roots=@universe AND omit_first=@omit_first
  AND contract_fingerprint=@contract AND snapshot_fingerprint=@snapshot AND policy_fingerprint=@policy AND scope_fingerprint=@scope AND binding_fingerprint=@binding
  AND ISNULL(declared_universe,N'')=ISNULL(@declared_universe,N'') COLLATE Latin1_General_100_BIN2)
  OR EXISTS(SELECT ordinal,execution_id FROM @proofs EXCEPT SELECT ordinal,execution_id FROM recon.analytic_collection_sweep_proof WHERE cycle_id=@cycle_id)
  THROW 53773,N'ANA_COLLECTION_SWEEP_RETRY_DIVERGENT',1;
  SELECT receipt_fingerprint,expected_roots,expected_pages FROM recon.analytic_collection_sweep_cycle WHERE cycle_id=@cycle_id;RETURN;
 END;
 IF EXISTS(SELECT 1 FROM @proofs p JOIN recon.analytic_collection_sweep_proof used ON used.execution_id=p.execution_id)
 THROW 53774,N'ANA_COLLECTION_SWEEP_OBSERVATION_REUSED',1;
 IF EXISTS(SELECT 1 FROM @proofs p LEFT JOIN ctl.analytic_collection_preparation prepared ON prepared.execution_id=p.execution_id
 LEFT JOIN ctl.relational_lab_capture c ON c.execution_id=p.execution_id LEFT JOIN ctl.execution_audit audit ON audit.execution_id=p.execution_id
 WHERE prepared.execution_id IS NULL OR prepared.run_id<>@run_id OR prepared.fingerprint<>@contract COLLATE Latin1_General_100_BIN2
 OR c.run_id<>@rel OR c.entity_name<>N'coletas' OR c.business_date<>@date OR c.contract_fingerprint<>@contract COLLATE Latin1_General_100_BIN2
 OR c.root_rows<>@roots OR c.physical_rows<>@rows OR prepared.physical_rows<>@rows OR prepared.roots<>@roots
 OR audit.execution_id IS NULL OR audit.status<>N'COMPLETED' OR audit.template_id<>6908 OR audit.business_window_start<>@date OR audit.business_window_end<>@date
 OR audit.updated_at_window_start IS NOT NULL OR audit.updated_at_window_end IS NOT NULL OR audit.failure_category IS NOT NULL
 OR audit.pages_fetched<>@pages OR audit.records_delivered<>@rows OR audit.terminal_page<>@pages)
 THROW 53775,N'ANA_COLLECTION_SWEEP_CAPTURE_INCOMPLETE',1;
 IF EXISTS(SELECT 1 FROM @proofs p OUTER APPLY(SELECT COUNT_BIG(*) pages,SUM(CONVERT(BIGINT,record_count)) records,MIN(page_number) first_page,MAX(page_number) last_page,
 SUM(CASE WHEN is_terminal=1 AND record_count=0 AND page_number=@pages THEN 1 ELSE 0 END) terminal,
 SUM(CASE WHEN page_number<@pages AND record_count<=0 THEN 1 ELSE 0 END) early_empty FROM ctl.page_audit WHERE execution_id=p.execution_id) a
 WHERE a.pages<>@pages OR a.records<>@rows OR a.first_page<>1 OR a.last_page<>@pages OR a.terminal<>1 OR a.early_empty<>0)
 THROW 53776,N'ANA_COLLECTION_SWEEP_PAGES_INCOMPLETE',1;
 -- This closed synthetic universe is the completeness proof in addition to local terminal evidence.
 IF @declared_universe IS NULL AND EXISTS(SELECT 1 FROM @proofs p OUTER APPLY(SELECT COUNT_BIG(*) roots,
 SUM(CASE WHEN source_key=CONCAT(N'INTEGER:',TRY_CONVERT(BIGINT,SUBSTRING(source_key,9,128))) COLLATE Latin1_General_100_BIN2
 AND TRY_CONVERT(BIGINT,SUBSTRING(source_key,9,128)) BETWEEN 200001+CONVERT(INT,@omit_first) AND 200000+@universe THEN 0 ELSE 1 END) invalid
 FROM stg.relational_lab_root WHERE execution_id=p.execution_id) a WHERE a.roots<>@roots OR a.invalid<>0)
 THROW 53777,N'ANA_COLLECTION_SWEEP_UNIVERSE_MISMATCH',1;
 IF @declared_universe IS NOT NULL
 BEGIN
  IF EXISTS(SELECT p.execution_id,d.source_key FROM @proofs p CROSS JOIN #declared d WHERE d.physical_rows>0
   EXCEPT SELECT p.execution_id,s.source_key FROM @proofs p JOIN stg.relational_lab_root s ON s.execution_id=p.execution_id)
  OR EXISTS(SELECT p.execution_id,s.source_key FROM @proofs p JOIN stg.relational_lab_root s ON s.execution_id=p.execution_id
   EXCEPT SELECT p.execution_id,d.source_key FROM @proofs p CROSS JOIN #declared d WHERE d.physical_rows>0)
  OR EXISTS(SELECT 1 FROM @proofs p CROSS JOIN #declared d OUTER APPLY(
    SELECT COUNT_BIG(*) physical_rows FROM stg.coleta_record r JOIN stg.analytic_collection_attributes a ON a.stage_record_id=r.stage_record_id
    WHERE r.execution_id=p.execution_id AND CONCAT(N'INTEGER:',a.id)=d.source_key COLLATE Latin1_General_100_BIN2) actual
    WHERE actual.physical_rows<>d.physical_rows)
  THROW 53777,N'ANA_COLLECTION_SWEEP_UNIVERSE_MISMATCH',1;
 END;
 DECLARE @receipt CHAR(64)=LOWER(CONVERT(CHAR(64),HASHBYTES('SHA2_256',CONVERT(VARBINARY(MAX),CONCAT(CONVERT(NVARCHAR(36),@cycle_id),N'|',@snapshot,N'|',@binding))),2));
 INSERT recon.analytic_collection_sweep_cycle(cycle_id,run_id,business_date,universe_roots,omit_first,contract_fingerprint,
 snapshot_fingerprint,policy_fingerprint,scope_fingerprint,binding_fingerprint,receipt_fingerprint,expected_roots,expected_pages,sealed_at,declared_universe)
 VALUES(@cycle_id,@run_id,@date,@universe,@omit_first,@contract,@snapshot,@policy,@scope,@binding,@receipt,@roots,@pages,@now,@declared_universe);
 INSERT recon.analytic_collection_sweep_proof SELECT @cycle_id,ordinal,execution_id FROM @proofs;
 SELECT @receipt receipt_fingerprint,@roots expected_roots,@pages expected_pages;
END;
GO
CREATE OR ALTER PROCEDURE recon.usp_apply_analytic_collection_sweep @run_id UNIQUEIDENTIFIER,@cycle_id UNIQUEIDENTIFIER,@receipt CHAR(64),@kernel VARCHAR(64) AS
BEGIN
 SET NOCOUNT ON;SET XACT_ABORT OFF;
 IF EXISTS(SELECT 1 FROM recon.analytic_collection_sweep_cycle WHERE cycle_id=@cycle_id AND declared_universe IS NOT NULL)
  THROW 53861,N'ANA_COLLECTION_DECLARED_PREVIEW_ONLY',1;
 DECLARE @date DATE,@universe INT,@contract CHAR(64),@scope CHAR(64),@policy CHAR(64),@last UNIQUEIDENTIFIER,@rel UNIQUEIDENTIFIER,@now DATETIME2(7)=SYSUTCDATETIME();
 SELECT @date=c.business_date,@universe=c.universe_roots,@contract=c.contract_fingerprint,@scope=c.scope_fingerprint,@policy=c.policy_fingerprint,@rel=g.relational_run
 FROM recon.analytic_collection_sweep_cycle c JOIN ctl.analytic_lab_source_group g ON g.run_id=c.run_id
 WHERE c.cycle_id=@cycle_id AND c.run_id=@run_id AND c.receipt_fingerprint=@receipt COLLATE Latin1_General_100_BIN2;
 IF @@TRANCOUNT=0 OR @rel IS NULL OR @kernel IS NULL OR @kernel<>'PREVIEW_ELIGIBLE_NO_APPLY_CAPABILITY' THROW 53778,N'ANA_COLLECTION_SWEEP_APPLICATION_GATE',1;
 EXEC ctl.usp_analytic_lab_lock @run_id,'DIMENSION';EXEC ctl.usp_relational_lab_lock @rel;
 IF EXISTS(SELECT 1 FROM recon.analytic_collection_sweep_application WHERE cycle_id=@cycle_id)
 BEGIN SELECT candidates,confirmations,reactivated,unchanged FROM recon.analytic_collection_sweep_application WHERE cycle_id=@cycle_id;RETURN;END;
 IF EXISTS(SELECT 1 FROM recon.analytic_collection_sweep_cycle later JOIN recon.analytic_collection_sweep_application applied ON applied.cycle_id=later.cycle_id
 JOIN recon.analytic_collection_sweep_cycle currentCycle ON currentCycle.cycle_id=@cycle_id
 WHERE later.run_id=@run_id AND later.business_date=@date AND later.universe_roots=@universe AND later.cycle_order>currentCycle.cycle_order)
 THROW 53780,N'ANA_COLLECTION_SWEEP_STALE_OBSERVATION',1;
 SELECT @last=execution_id FROM recon.analytic_collection_sweep_proof WHERE cycle_id=@cycle_id AND ordinal=4;
 IF @last IS NULL OR (SELECT COUNT_BIG(*) FROM recon.analytic_collection_sweep_proof WHERE cycle_id=@cycle_id)<>4 THROW 53778,N'ANA_COLLECTION_SWEEP_APPLICATION_GATE',1;
 IF EXISTS(SELECT 1 FROM recon.analytic_collection_absence a JOIN recon.analytic_collection_sweep_cycle old ON old.cycle_id=a.last_cycle
 WHERE a.run_id=@run_id AND a.confirmations>0 AND a.source_key BETWEEN N'INTEGER:200001' AND CONCAT(N'INTEGER:',200000+@universe) AND DATALENGTH(a.source_key)=28
 AND old.business_date=@date AND(old.scope_fingerprint<>@scope COLLATE Latin1_General_100_BIN2 OR old.contract_fingerprint<>@contract COLLATE Latin1_General_100_BIN2 OR old.policy_fingerprint<>@policy COLLATE Latin1_General_100_BIN2))
 THROW 53779,N'ANA_COLLECTION_SWEEP_PREVIOUS_SCOPE_DIVERGENT',1;
 SELECT currentRoot.source_key,COALESCE(a.confirmations,0) old_count,a.last_cycle old_cycle,
 CONVERT(TINYINT,CASE WHEN seen.source_key IS NOT NULL THEN 0 WHEN COALESCE(a.confirmations,0)=0 THEN 1 ELSE 2 END) next_count
 INTO #changes FROM core.analytic_collection_current currentRoot LEFT JOIN recon.analytic_collection_absence a ON a.run_id=currentRoot.run_id AND a.source_key=currentRoot.source_key
 LEFT JOIN stg.relational_lab_root seen ON seen.execution_id=@last AND seen.source_key=currentRoot.source_key
 WHERE currentRoot.run_id=@run_id AND currentRoot.business_date=@date AND currentRoot.source_key BETWEEN N'INTEGER:200001' AND CONCAT(N'INTEGER:',200000+@universe) AND DATALENGTH(currentRoot.source_key)=28;
 INSERT recon.analytic_collection_absence_history SELECT @cycle_id,source_key,old_count,next_count,old_cycle FROM #changes;
 UPDATE a SET confirmations=c.next_count,confirmed=CASE WHEN c.next_count=2 THEN 1 ELSE 0 END,
 absent_since=CASE WHEN c.next_count=0 THEN NULL ELSE COALESCE(a.absent_since,@now) END,
 excluded_at=CASE WHEN c.next_count=2 THEN COALESCE(a.excluded_at,@now) ELSE NULL END,
 reason=CASE WHEN c.next_count=0 THEN NULL ELSE 'SYNTHETIC_COMPLETE_SNAPSHOT_ABSENCE' END,
 first_cycle=CASE WHEN c.next_count=0 THEN NULL ELSE COALESCE(a.first_cycle,@cycle_id) END,
 last_cycle=CASE WHEN c.next_count=0 THEN NULL WHEN c.next_count=1 THEN COALESCE(a.first_cycle,@cycle_id) ELSE @cycle_id END,
 last_reconciled_at=@now,last_capture=@last FROM recon.analytic_collection_absence a JOIN #changes c ON c.source_key=a.source_key WHERE a.run_id=@run_id;
 INSERT recon.analytic_collection_absence(run_id,source_key,absent_since,confirmations,confirmed,excluded_at,reason,first_cycle,last_cycle,last_reconciled_at,last_capture)
 SELECT @run_id,c.source_key,@now,1,0,NULL,'SYNTHETIC_COMPLETE_SNAPSHOT_ABSENCE',@cycle_id,@cycle_id,@now,@last FROM #changes c
 WHERE c.next_count=1 AND NOT EXISTS(SELECT 1 FROM recon.analytic_collection_absence a WHERE a.run_id=@run_id AND a.source_key=c.source_key);
 INSERT recon.analytic_collection_sweep_application SELECT @cycle_id,COALESCE(SUM(CONVERT(BIGINT,CASE WHEN old_count=0 AND next_count=1 THEN 1 ELSE 0 END)),0),
 COALESCE(SUM(CONVERT(BIGINT,CASE WHEN old_count=1 AND next_count=2 THEN 1 ELSE 0 END)),0),COALESCE(SUM(CONVERT(BIGINT,CASE WHEN old_count>0 AND next_count=0 THEN 1 ELSE 0 END)),0),
 COALESCE(SUM(CONVERT(BIGINT,CASE WHEN old_count=next_count THEN 1 ELSE 0 END)),0),@now FROM #changes;
 SELECT candidates,confirmations,reactivated,unchanged FROM recon.analytic_collection_sweep_application WHERE cycle_id=@cycle_id;
END;
GO
