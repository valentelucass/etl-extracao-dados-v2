:On Error exit
:r "../bloco60-lease-restante/profile-after.sql"
GO
SET NOCOUNT ON;
IF DB_NAME()<>N'ETL_SISTEMA_V2_SHADOW' OR ORIGINAL_LOGIN()<>N'RTR-SVW-002\suporte' THROW 52970,N'PAIR_RECOVERY_TARGET',1;
DECLARE @e UNIQUEIDENTIFIER='5662390b-0feb-4f2c-a034-fbe384f54ab9',@a UNIQUEIDENTIFIER='727f9ae4-fed1-44a0-b346-8b44b50855b0',@b UNIQUEIDENTIFIER='b5264c4c-ff6a-4840-a47d-c8bcf5e99787';
SELECT (SELECT COUNT_BIG(*) FROM ctl.execution_attempt WHERE execution_id=@e) attempts,
(SELECT current_state FROM ctl.execution_attempt WHERE execution_id=@e) state,
(SELECT COUNT_BIG(*) FROM ctl.runtime_authorization_decision WHERE invocation_id IN(@a,@b)) decisions,
(SELECT COUNT_BIG(*) FROM ctl.runtime_authorization_consumption WHERE invocation_id IN(@a,@b)) consumptions,
(SELECT COUNT_BIG(*) FROM ctl.execution_publication_event WHERE execution_id=@e) publications,
(SELECT COUNT_BIG(*) FROM recon.execution_candidate_application WHERE execution_id=@e) applications,
(SELECT COUNT_BIG(*) FROM core.usuario_history WHERE execution_id=@e) history,
(SELECT COUNT_BIG(*) FROM ctl.runtime_contract_evidence WHERE execution_id=@e) seals,
(SELECT COUNT_BIG(*) FROM sys.dm_exec_sessions WHERE host_process_id IN(24388,30596,30940) AND original_login_name IN(N'RTR-SVW-002\etl_v2_exec',N'RTR-SVW-002\suporte')) ownSessions,
(SELECT COUNT_BIG(*) FROM sys.dm_tran_session_transactions t JOIN sys.dm_exec_sessions s ON s.session_id=t.session_id WHERE s.host_process_id=30940 AND s.original_login_name=N'RTR-SVW-002\suporte') barrierTransactions
FOR JSON PATH,WITHOUT_ARRAY_WRAPPER,INCLUDE_NULL_VALUES;
IF EXISTS(SELECT 1 FROM sys.dm_exec_sessions WHERE host_process_id IN(24388,30596,30940) AND original_login_name IN(N'RTR-SVW-002\etl_v2_exec',N'RTR-SVW-002\suporte')) THROW 52970,N'PAIR_OWN_SESSION_STILL_ALIVE',1;
SELECT N'PAIR_READBACK_RECOVERY_OBSERVED_NO_RETRY_NO_MUTATION';
