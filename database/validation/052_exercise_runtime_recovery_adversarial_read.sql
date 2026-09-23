-- Read-only against admitted Block 53 synthetic occurrences. Table variables are local assertions.
:On Error exit
SET NOCOUNT ON;SET XACT_ABORT ON;
IF DB_NAME()<>N'ETL_SISTEMA_V2_SHADOW' THROW 52450,N'LOCAL_TARGET_REQUIRED',1;
DECLARE @before BIGINT=(SELECT COUNT_BIG(*) FROM ctl.execution_publication_event),@passes INT=0;
DECLARE @snapshot TABLE(reason NVARCHAR(40),execution_state NVARCHAR(32),lease_valid BIT,
 contract_verified BIT,candidate_rows BIGINT,quality NVARCHAR(16),revision CHAR(64),
 execution_id UNIQUEIDENTIFIER,inserted_rows BIGINT,updated_rows BIGINT,reactivated_rows BIGINT,
 noop_rows BIGINT,stale_noop_rows BIGINT,reconciled_at_utc DATETIME2(3),published_at_utc DATETIME2(3),
 incremental_frontier_before_utc DATETIME2(3),incremental_frontier_after_utc DATETIME2(3));
DECLARE @vertical INT=0;
WHILE @vertical<2
BEGIN
 DECLARE @entity NVARCHAR(16)=CASE WHEN @vertical=0 THEN N'coletas' ELSE N'fretes' END,
  @execution UNIQUEIDENTIFIER,@identity NVARCHAR(MAX),@material NVARCHAR(MAX),@policy NVARCHAR(128),@hash CHAR(64);
 SELECT TOP(1) @execution=e.execution_id,@identity=e.identity_material,@material=e.contract_material,
  @policy=e.policy_version,@hash=e.policy_fingerprint
 FROM ctl.runtime_contract_evidence e JOIN ctl.execution_attempt a ON a.execution_id=e.execution_id
 JOIN ctl.execution_partition p ON p.partition_id=a.partition_id
 WHERE p.source_instance LIKE N'SYNTHETIC_B53[_]%' AND p.entity_name=@entity AND a.current_state=N'PUBLISHED'
  AND (@entity=N'fretes' OR EXISTS(SELECT 1 FROM ctl.runtime_coleta_publication_receipt WHERE execution_id=e.execution_id))
 ORDER BY e.sealed_at_utc,e.execution_id;
 IF @execution IS NULL THROW 52450,N'COMMITTED_SYNTHETIC_OCCURRENCE_REQUIRED',1;
 DECLARE @ordinal INT=2;
 WHILE @ordinal<=19
 BEGIN
  DECLARE @token NVARCHAR(4000),@mutated NVARCHAR(MAX);
  SELECT @token=value FROM STRING_SPLIT(@identity,N'|',1) WHERE ordinal=@ordinal;
  IF @token IS NULL THROW 52450,N'IDENTITY_FIELD_REQUIRED',1;
  SET @mutated=REPLACE(@identity,@token+N'|',@token+N'x|');
  DELETE FROM @snapshot;
  INSERT @snapshot EXEC ctl.usp_runtime_recovery @execution,N'READ',@mutated,@material,@policy,@hash,N'',@entity;
  IF (SELECT COUNT_BIG(*) FROM @snapshot WHERE reason=N'INCONSISTENT' AND published_at_utc IS NULL)<>1
   THROW 52450,N'IDENTITY_CONTRADICTION_OPENED_RECOVERY',1;
  SET @passes+=1;SET @ordinal+=1;
 END;
 DELETE FROM @snapshot;
 SET @mutated=@material+N'contradiction';
 INSERT @snapshot EXEC ctl.usp_runtime_recovery @execution,N'READ',@identity,@mutated,@policy,@hash,N'',@entity;
 IF (SELECT COUNT_BIG(*) FROM @snapshot WHERE reason=N'INCONSISTENT')<>1 THROW 52450,N'CONTRACT_CONTRADICTION_OPENED_RECOVERY',1;
 SET @passes+=1;
 DELETE FROM @snapshot;
 DECLARE @missing NVARCHAR(128)=N'SYNTHETIC_ABSENT_POLICY';
 INSERT @snapshot EXEC ctl.usp_runtime_recovery @execution,N'READ',@identity,@material,@missing,@hash,N'',@entity;
 IF EXISTS(SELECT 1 FROM @snapshot WHERE reason IN(N'PUBLISHED',N'ELIGIBLE')) THROW 52450,N'POLICY_CONTRADICTION_OPENED_RECOVERY',1;
 SET @passes+=1;
 SET @vertical+=1;
END;
-- Terminal attempts must remain terminal; recovery must not claim a new lease.
DECLARE @failed UNIQUEIDENTIFIER,@failedIdentity NVARCHAR(MAX);
SELECT TOP(1) @failed=a.execution_id FROM ctl.execution_attempt a JOIN ctl.execution_partition p ON p.partition_id=a.partition_id
 WHERE p.source_instance LIKE N'SYNTHETIC_B53[_]%' AND p.entity_name=N'coletas' AND a.current_state=N'FAILED' ORDER BY a.execution_id;
IF @failed IS NULL THROW 52450,N'TERMINAL_FIXTURE_REQUIRED',1;
SET @failedIdentity=ctl.fn_runtime_recovery_identity(@failed);
DELETE FROM @snapshot;
DECLARE @dummyHash CHAR(64)=REPLICATE('a',64);
INSERT @snapshot EXEC ctl.usp_runtime_recovery @failed,N'READ',@failedIdentity,N'synthetic-no-seal',N'synthetic-no-policy',@dummyHash,N'',N'coletas';
IF (SELECT COUNT_BIG(*) FROM @snapshot WHERE reason=N'TERMINAL' AND lease_valid=0)<>1 THROW 52450,N'TERMINAL_RECOVERY_CHANGED',1;
IF NOT EXISTS(SELECT 1 FROM ctl.runtime_coleta_publication_receipt t JOIN recon.execution_reconciliation_result g ON g.execution_id=t.execution_id
 WHERE t.updated_rows=3 AND g.updated_rows=0 AND g.stale_noop_rows=3)
 THROW 52450,N'COL03_TYPED_GENERIC_DIVERGENCE_REQUIRED',1;
IF @before<>(SELECT COUNT_BIG(*) FROM ctl.execution_publication_event) THROW 52450,N'READ_DUPLICATED_PUBLICATION',1;
PRINT CONCAT(N'ADVERSARIAL_PHYSICAL_READ_PASS identity_contract_policy_cases=',@passes,N'; terminal=1; COL03_divergence=1; publication_unchanged=1');
