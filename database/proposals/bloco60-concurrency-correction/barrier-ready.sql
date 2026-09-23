:On Error exit
SET NOCOUNT ON;
IF DB_NAME()<>N'ETL_SISTEMA_V2_SHADOW' OR ORIGINAL_LOGIN()<>N'RTR-SVW-002\suporte'
 THROW 52970,N'B60_READINESS_TARGET',1;
DECLARE @pid INT=$(B60BarrierPid),@found INT=0,@tries INT=0;
IF @pid<=0 THROW 52970,N'B60_READINESS_OWN_PID',1;
WHILE @tries<12 AND @found<>1
BEGIN
 SELECT @found=COUNT_BIG(*) FROM sys.dm_tran_locks l
 JOIN sys.dm_exec_sessions s ON s.session_id=l.request_session_id
 WHERE s.host_process_id=@pid AND s.original_login_name=N'RTR-SVW-002\suporte'
 AND s.host_name=N'RTR-SVW-002' AND l.resource_database_id=DB_ID()
 AND l.resource_type=N'APPLICATION' AND l.request_status=N'GRANT'
 AND l.request_mode=N'X' AND l.request_owner_type=N'TRANSACTION';
 IF @found<>1 WAITFOR DELAY '00:00:00.250';
 SET @tries+=1;
END;
IF @found<>1 THROW 52970,N'B60_READINESS_NOT_GRANTED',1;
SELECT @pid barrierPid,@found grantedApplicationLocks,
 (SELECT COUNT_BIG(*) FROM sys.dm_exec_sessions WHERE host_process_id=@pid
 AND original_login_name=N'RTR-SVW-002\suporte' AND host_name=N'RTR-SVW-002') sessions
FOR JSON PATH,WITHOUT_ARRAY_WRAPPER;
