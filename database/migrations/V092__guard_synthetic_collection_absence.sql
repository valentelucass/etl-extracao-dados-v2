-- Local opt-in application only. Four actual traversals support each independent observation.
-- Operational completeness, watermark and source Sweep gates are not changed.
SET ANSI_NULLS ON;SET QUOTED_IDENTIFIER ON;
GO
ALTER TABLE recon.analytic_collection_absence DROP CONSTRAINT CK_ana_collection_absence;
ALTER TABLE recon.analytic_collection_absence ALTER COLUMN absent_since DATETIME2(7) NULL;
ALTER TABLE recon.analytic_collection_absence ALTER COLUMN reason VARCHAR(64) COLLATE Latin1_General_100_BIN2 NULL;
ALTER TABLE recon.analytic_collection_absence ALTER COLUMN first_cycle UNIQUEIDENTIFIER NULL;
ALTER TABLE recon.analytic_collection_absence ALTER COLUMN last_cycle UNIQUEIDENTIFIER NULL;
ALTER TABLE recon.analytic_collection_absence ADD CONSTRAINT CK_ana_collection_absence CHECK(
 (confirmations=0 AND confirmed=0 AND absent_since IS NULL AND excluded_at IS NULL AND reason IS NULL AND first_cycle IS NULL AND last_cycle IS NULL)
 OR (reason='SYNTHETIC_COMPLETE_SNAPSHOT_ABSENCE' AND absent_since IS NOT NULL AND first_cycle IS NOT NULL AND last_cycle IS NOT NULL AND
 ((confirmations=1 AND confirmed=0 AND excluded_at IS NULL AND first_cycle=last_cycle) OR(confirmations=2 AND confirmed=1 AND excluded_at IS NOT NULL AND first_cycle<>last_cycle))));
GO
CREATE TABLE recon.analytic_collection_sweep_cycle (
 cycle_order BIGINT IDENTITY NOT NULL CONSTRAINT UQ_ana_collection_sweep_order UNIQUE,
 cycle_id UNIQUEIDENTIFIER NOT NULL CONSTRAINT PK_ana_collection_sweep_cycle PRIMARY KEY,
 run_id UNIQUEIDENTIFIER NOT NULL REFERENCES ctl.analytic_lab_run(run_id),business_date DATE NOT NULL,universe_roots INT NOT NULL,omit_first BIT NOT NULL,
 contract_fingerprint CHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL,snapshot_fingerprint CHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL,
 policy_fingerprint CHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL,scope_fingerprint CHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL,
 binding_fingerprint CHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL,receipt_fingerprint CHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL,
 expected_roots INT NOT NULL,expected_pages INT NOT NULL,sealed_at DATETIME2(7) NOT NULL,
 CONSTRAINT CK_ana_collection_sweep_cycle CHECK(universe_roots BETWEEN 2 AND 4096 AND expected_roots=universe_roots-CONVERT(INT,omit_first) AND expected_pages>1)
);
CREATE TABLE recon.analytic_collection_sweep_proof (
 cycle_id UNIQUEIDENTIFIER NOT NULL REFERENCES recon.analytic_collection_sweep_cycle(cycle_id),ordinal TINYINT NOT NULL,
 execution_id UNIQUEIDENTIFIER NOT NULL REFERENCES ctl.analytic_collection_preparation(execution_id),
 CONSTRAINT PK_ana_collection_sweep_proof PRIMARY KEY(cycle_id,ordinal),
 CONSTRAINT UQ_ana_collection_sweep_proof UNIQUE(execution_id),CONSTRAINT CK_ana_collection_sweep_proof CHECK(ordinal BETWEEN 1 AND 4)
);
CREATE TABLE recon.analytic_collection_sweep_application (
 cycle_id UNIQUEIDENTIFIER NOT NULL CONSTRAINT PK_ana_collection_sweep_application PRIMARY KEY REFERENCES recon.analytic_collection_sweep_cycle(cycle_id),
 candidates BIGINT NOT NULL,confirmations BIGINT NOT NULL,reactivated BIGINT NOT NULL,unchanged BIGINT NOT NULL,applied_at DATETIME2(7) NOT NULL,
 CONSTRAINT CK_ana_collection_sweep_application CHECK(candidates>=0 AND confirmations>=0 AND reactivated>=0 AND unchanged>=0)
);
CREATE TABLE recon.analytic_collection_absence_history (
 cycle_id UNIQUEIDENTIFIER NOT NULL REFERENCES recon.analytic_collection_sweep_cycle(cycle_id),source_key NVARCHAR(128) COLLATE Latin1_General_100_BIN2 NOT NULL,
 previous_confirmations TINYINT NOT NULL,next_confirmations TINYINT NOT NULL,previous_cycle UNIQUEIDENTIFIER NULL,
 CONSTRAINT PK_ana_collection_absence_history PRIMARY KEY(cycle_id,source_key),
 CONSTRAINT CK_ana_collection_absence_history CHECK(previous_confirmations BETWEEN 0 AND 2 AND next_confirmations BETWEEN 0 AND 2)
);
GO
CREATE TRIGGER recon.trg_ana_collection_sweep_cycle ON recon.analytic_collection_sweep_cycle AFTER UPDATE,DELETE AS
BEGIN SET NOCOUNT ON;THROW 53770,N'ANA_COLLECTION_SWEEP_IMMUTABLE',1;END;
GO
CREATE TRIGGER recon.trg_ana_collection_sweep_proof ON recon.analytic_collection_sweep_proof AFTER UPDATE,DELETE AS
BEGIN SET NOCOUNT ON;THROW 53770,N'ANA_COLLECTION_SWEEP_IMMUTABLE',1;END;
GO
CREATE TRIGGER recon.trg_ana_collection_sweep_application ON recon.analytic_collection_sweep_application AFTER UPDATE,DELETE AS
BEGIN SET NOCOUNT ON;THROW 53770,N'ANA_COLLECTION_SWEEP_IMMUTABLE',1;END;
GO
CREATE TRIGGER recon.trg_ana_collection_absence_history ON recon.analytic_collection_absence_history AFTER UPDATE,DELETE AS
BEGIN SET NOCOUNT ON;THROW 53770,N'ANA_COLLECTION_SWEEP_IMMUTABLE',1;END;
GO
CREATE TYPE recon.analytic_collection_sweep_proofs AS TABLE(ordinal TINYINT NOT NULL PRIMARY KEY,execution_id UNIQUEIDENTIFIER NOT NULL UNIQUE);
GO
CREATE PROCEDURE recon.usp_prepare_analytic_collection_sweep
 @run_id UNIQUEIDENTIFIER,@cycle_id UNIQUEIDENTIFIER,@date DATE,@universe INT,@omit_first BIT,
 @contract CHAR(64),@snapshot CHAR(64),@policy CHAR(64),@scope CHAR(64),@binding CHAR(64),
 @proofs recon.analytic_collection_sweep_proofs READONLY AS
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
 SET @pages=(@roots*3+@per-1)/@per+1;
 DECLARE @expected CHAR(64)=LOWER(CONVERT(CHAR(64),HASHBYTES('SHA2_256',CONVERT(VARBINARY(MAX),CONCAT(N'synthetic-analytic-collection-snapshot-v1|universe=',@universe,N'|omit=',CONVERT(INT,@omit_first),N'|date=',CONVERT(NVARCHAR(10),@date,23)))),2));
 IF @snapshot<>@expected COLLATE Latin1_General_100_BIN2 THROW 53772,N'ANA_COLLECTION_SWEEP_SNAPSHOT_UNPROVED',1;
 EXEC ctl.usp_analytic_lab_lock @run_id,'DIMENSION';EXEC ctl.usp_relational_lab_lock @rel;
 IF EXISTS(SELECT 1 FROM recon.analytic_collection_sweep_cycle WHERE cycle_id=@cycle_id)
 BEGIN
  IF NOT EXISTS(SELECT 1 FROM recon.analytic_collection_sweep_cycle WHERE cycle_id=@cycle_id AND run_id=@run_id AND business_date=@date AND universe_roots=@universe AND omit_first=@omit_first
  AND contract_fingerprint=@contract AND snapshot_fingerprint=@snapshot AND policy_fingerprint=@policy AND scope_fingerprint=@scope AND binding_fingerprint=@binding)
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
 OR c.root_rows<>@roots OR c.physical_rows<>@roots*3 OR prepared.physical_rows<>@roots*3 OR prepared.roots<>@roots
 OR audit.execution_id IS NULL OR audit.status<>N'COMPLETED' OR audit.template_id<>6908 OR audit.business_window_start<>@date OR audit.business_window_end<>@date
 OR audit.updated_at_window_start IS NOT NULL OR audit.updated_at_window_end IS NOT NULL OR audit.failure_category IS NOT NULL
 OR audit.pages_fetched<>@pages OR audit.records_delivered<>@roots*3 OR audit.terminal_page<>@pages)
 THROW 53775,N'ANA_COLLECTION_SWEEP_CAPTURE_INCOMPLETE',1;
 IF EXISTS(SELECT 1 FROM @proofs p OUTER APPLY(SELECT COUNT_BIG(*) pages,SUM(CONVERT(BIGINT,record_count)) records,MIN(page_number) first_page,MAX(page_number) last_page,
 SUM(CASE WHEN is_terminal=1 AND record_count=0 AND page_number=@pages THEN 1 ELSE 0 END) terminal,
 SUM(CASE WHEN page_number<@pages AND record_count<=0 THEN 1 ELSE 0 END) early_empty FROM ctl.page_audit WHERE execution_id=p.execution_id) a
 WHERE a.pages<>@pages OR a.records<>@roots*3 OR a.first_page<>1 OR a.last_page<>@pages OR a.terminal<>1 OR a.early_empty<>0)
 THROW 53776,N'ANA_COLLECTION_SWEEP_PAGES_INCOMPLETE',1;
 -- This closed synthetic universe is the completeness proof in addition to local terminal evidence.
 IF EXISTS(SELECT 1 FROM @proofs p OUTER APPLY(SELECT COUNT_BIG(*) roots,
 SUM(CASE WHEN source_key=CONCAT(N'INTEGER:',TRY_CONVERT(BIGINT,SUBSTRING(source_key,9,128))) COLLATE Latin1_General_100_BIN2
 AND TRY_CONVERT(BIGINT,SUBSTRING(source_key,9,128)) BETWEEN 200001+CONVERT(INT,@omit_first) AND 200000+@universe THEN 0 ELSE 1 END) invalid
 FROM stg.relational_lab_root WHERE execution_id=p.execution_id) a WHERE a.roots<>@roots OR a.invalid<>0)
 THROW 53777,N'ANA_COLLECTION_SWEEP_UNIVERSE_MISMATCH',1;
 DECLARE @receipt CHAR(64)=LOWER(CONVERT(CHAR(64),HASHBYTES('SHA2_256',CONVERT(VARBINARY(MAX),CONCAT(CONVERT(NVARCHAR(36),@cycle_id),N'|',@snapshot,N'|',@binding))),2));
 INSERT recon.analytic_collection_sweep_cycle VALUES(@cycle_id,@run_id,@date,@universe,@omit_first,@contract,@snapshot,@policy,@scope,@binding,@receipt,@roots,@pages,@now);
 INSERT recon.analytic_collection_sweep_proof SELECT @cycle_id,ordinal,execution_id FROM @proofs;
 SELECT @receipt receipt_fingerprint,@roots expected_roots,@pages expected_pages;
END;
GO
CREATE PROCEDURE recon.usp_apply_analytic_collection_sweep @run_id UNIQUEIDENTIFIER,@cycle_id UNIQUEIDENTIFIER,@receipt CHAR(64),@kernel VARCHAR(64) AS
BEGIN
 SET NOCOUNT ON;SET XACT_ABORT OFF;
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
