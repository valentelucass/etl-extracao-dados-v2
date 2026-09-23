-- Only B60-owned UUIDs from the frozen request manifest may fill these sqlcmd variables.
-- Does not modify persisted receipts. Corrupt claims are sent to the real SQL reader.
:On Error exit
SET NOCOUNT ON; SET XACT_ABORT ON;
IF DB_NAME()<>N'ETL_SISTEMA_V2_SHADOW' THROW 52972,N'B60_TARGET_REQUIRED',1;
DECLARE @execution UNIQUEIDENTIFIER='$(B60Published)',@other UNIQUEIDENTIFIER='$(B60Other)',
 @prepared UNIQUEIDENTIFIER='$(B60Prepared)',@identity NVARCHAR(MAX),@contract NVARCHAR(MAX),@policy NVARCHAR(128),@hash CHAR(64);
DECLARE @before BIGINT=(SELECT COUNT_BIG(*) FROM ctl.execution_publication_event),@checks INT=0;
SELECT @identity=identity_material,@contract=contract_material,@policy=policy_version,@hash=policy_fingerprint
 FROM ctl.runtime_contract_evidence WHERE execution_id=@execution;
IF @identity IS NULL OR @policy NOT LIKE N'bloco60-usuarios-%' THROW 52972,N'B60_OWN_PUBLISHED_RECEIPT_REQUIRED',1;
DECLARE @snapshot TABLE(reason NVARCHAR(40),execution_state NVARCHAR(32),lease_valid BIT,
 contract_verified BIT,candidate_rows BIGINT,quality NVARCHAR(16),revision CHAR(64),
 execution_id UNIQUEIDENTIFIER,inserted_rows BIGINT,updated_rows BIGINT,reactivated_rows BIGINT,
 noop_rows BIGINT,stale_noop_rows BIGINT,reconciled_at_utc DATETIME2(3),published_at_utc DATETIME2(3),
 incremental_frontier_before_utc DATETIME2(3),incremental_frontier_after_utc DATETIME2(3));
INSERT @snapshot EXEC ctl.usp_runtime_recovery @execution,N'READ',@identity,@contract,@policy,@hash,N'',N'usuarios';
IF (SELECT COUNT_BIG(*) FROM @snapshot WHERE reason=N'PUBLISHED' AND contract_verified=1 AND quality=N'PASSED' AND published_at_utc IS NOT NULL)<>1
 THROW 52972,N'B60_REAL_TYPED_RECEIPT_REQUIRED',1;
SET @checks+=1;
-- Clearing a local assertion variable never deletes persisted business/audit rows.
DELETE FROM @snapshot;
DECLARE @wrong NVARCHAR(MAX)=@identity+N'B60_CORRUPT';
INSERT @snapshot EXEC ctl.usp_runtime_recovery @execution,N'READ',@wrong,@contract,@policy,@hash,N'',N'usuarios';
IF EXISTS(SELECT 1 FROM @snapshot WHERE reason<>N'INCONSISTENT' OR published_at_utc IS NOT NULL) THROW 52972,N'B60_IDENTITY_CONTRADICTION',1;
SET @checks+=1;
DELETE FROM @snapshot;
SET @wrong=@contract+N'B60_CORRUPT';
INSERT @snapshot EXEC ctl.usp_runtime_recovery @execution,N'READ',@identity,@wrong,@policy,@hash,N'',N'usuarios';
IF EXISTS(SELECT 1 FROM @snapshot WHERE reason<>N'INCONSISTENT' OR published_at_utc IS NOT NULL) THROW 52972,N'B60_CONTRACT_CONTRADICTION',1;
SET @checks+=1;
DELETE FROM @snapshot;
INSERT @snapshot EXEC ctl.usp_runtime_recovery @other,N'READ',@identity,@contract,@policy,@hash,N'',N'usuarios';
IF EXISTS(SELECT 1 FROM @snapshot WHERE reason IN(N'PUBLISHED',N'ELIGIBLE') OR published_at_utc IS NOT NULL) THROW 52972,N'B60_FOREIGN_RECEIPT',1;
SET @checks+=1;
DELETE FROM @snapshot;
SET @wrong=ctl.fn_runtime_recovery_identity(@prepared);
IF @wrong IS NULL OR EXISTS(SELECT 1 FROM ctl.runtime_contract_evidence WHERE execution_id=@prepared)
 OR NOT EXISTS(SELECT 1 FROM ctl.execution_promotion_result WHERE execution_id=@prepared)
 THROW 52972,N'B60_PREPARE_COMMIT_WITHOUT_SEAL_REQUIRED',1;
INSERT @snapshot EXEC ctl.usp_runtime_recovery @prepared,N'READ',@wrong,@contract,@policy,@hash,N'',N'usuarios';
IF EXISTS(SELECT 1 FROM @snapshot WHERE contract_verified<>0 OR reason IN(N'PUBLISHED',N'ELIGIBLE')) THROW 52972,N'B60_MISSING_SEAL_NOT_ABSENT_COMMIT',1;
SET @checks+=1;
DECLARE @caught INT=0;
BEGIN TRY
 EXEC stg.usp_stage_usuario_record @execution,1,1,NULL,NULL,NULL,NULL,NULL,NULL,NULL;
END TRY BEGIN CATCH IF ERROR_NUMBER()=52966 SET @caught=1; ELSE THROW; END CATCH;
IF @caught<>1 THROW 52972,N'B60_NULL_STAGE_ACCEPTED',1;
SET @checks+=1;
SET @caught=0;
BEGIN TRY
 DECLARE @now DATETIME2(3)=SYSUTCDATETIME();
 EXEC ctl.usp_control_plane_register_source N'LOCAL_V2',N'UNREGISTERED_B60',@now;
END TRY BEGIN CATCH IF ERROR_NUMBER()=51301 SET @caught=1; ELSE THROW; END CATCH;
IF @caught<>1 THROW 52972,N'B60_UNREGISTERED_PROTOCOL_ACCEPTED',1;
SET @checks+=1;
IF @before<>(SELECT COUNT_BIG(*) FROM ctl.execution_publication_event)
 OR EXISTS(SELECT 1 FROM ctl.source_protocol_binding WHERE source_instance=N'LOCAL_V2' AND source_kind=N'UNREGISTERED_B60')
 THROW 52972,N'B60_ADVERSARIAL_UNEXPECTED_EFFECT',1;
SELECT CONCAT(N'B60_ADVERSARIAL_SQL_READ_PASS checks=',@checks);
