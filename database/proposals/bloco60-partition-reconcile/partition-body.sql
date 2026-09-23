DECLARE @partition BIGINT,@oldAttempt INT,@newAttempt INT;
SELECT @partition=partition_id,@oldAttempt=attempt_number FROM ctl.execution_attempt WHERE execution_id=@old AND current_state=N'CANCELLED';
SELECT @newAttempt=attempt_number FROM ctl.execution_attempt WHERE execution_id=@new AND current_state=N'CANCELLED' AND partition_id=@partition;
IF @partition IS NULL OR @oldAttempt IS NULL OR @newAttempt<>@oldAttempt+1 THROW 52970,N'B60_EXACT_CANCEL_PARTITION_REQUIRED',1;
IF EXISTS(SELECT 1 FROM ctl.execution_lease WHERE execution_id IN(@old,@new) AND released_at_utc IS NULL)
 OR EXISTS(SELECT 1 FROM ctl.execution_publication_event WHERE execution_id IN(@old,@new))
 OR EXISTS(SELECT 1 FROM recon.execution_candidate_application WHERE execution_id IN(@old,@new)) THROW 52970,N'B60_CANCEL_RECONCILIATION_EFFECT',1;
DECLARE @actual CHAR(64),@reconstructed CHAR(64);
SELECT @actual=LOWER(CONVERT(CHAR(64),HASHBYTES('SHA2_256',(SELECT p.* FOR JSON PATH,WITHOUT_ARRAY_WRAPPER,INCLUDE_NULL_VALUES)),2)),
 @reconstructed=LOWER(CONVERT(CHAR(64),HASHBYTES('SHA2_256',(SELECT p.partition_id,p.environment_name,p.source_instance,p.tenant_scope,p.entity_name,p.execution_mode,
 p.partition_start_utc,p.partition_end_exclusive_utc,@old current_execution_id,CONVERT(NVARCHAR(32),N'CANCELLED') current_state,p.next_attempt_number-1 next_attempt_number,p.created_at_utc
 FOR JSON PATH,WITHOUT_ARRAY_WRAPPER,INCLUDE_NULL_VALUES)),2))
 FROM ctl.execution_partition p WHERE p.partition_id=@partition AND p.current_execution_id=@new AND p.current_state=N'CANCELLED' AND p.next_attempt_number=@newAttempt+1;
IF @actual IS NULL OR @reconstructed IS NULL OR @actual<>@after OR @reconstructed<>@before THROW 52970,N'B60_PARTITION_RECONSTRUCTION_MISMATCH',1;
SELECT @actual actualHash,@reconstructed reconstructedBeforeHash,1 exactPartition,2 retainedCancelledAttempts,0 openLeases,0 publications,0 applications FOR JSON PATH,WITHOUT_ARRAY_WRAPPER;
SELECT N'B60_EXACT_OWN_CANCEL_PARTITION_UPDATE_RECONCILED_NO_HISTORY_LOSS';
