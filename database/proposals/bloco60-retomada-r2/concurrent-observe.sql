:On Error exit
SET NOCOUNT ON;
IF DB_NAME()<>N'ETL_SISTEMA_V2_SHADOW' THROW 52970,N'B60_CONCURRENCY_TARGET',1;
DECLARE @found INT=0,@tries INT=0,@first INT=$(B60FirstPid),@second INT=$(B60SecondPid),@barrier INT=$(B60BarrierPid);
IF @first<=0 OR @second<=0 OR @first=@second OR @barrier<=0 THROW 52970,N'B60_OWN_DISTINCT_PIDS',1;
WHILE @tries<12 AND @found<2
BEGIN
 SELECT @found=COUNT(DISTINCT s.host_process_id)
 FROM sys.dm_exec_requests r JOIN sys.dm_exec_sessions s ON s.session_id=r.session_id
 WHERE s.original_login_name=N'RTR-SVW-002\etl_v2_exec' AND s.host_name=N'RTR-SVW-002'
 AND s.host_process_id IN(@first,@second) AND r.database_id=DB_ID()
 AND r.blocking_session_id IN(SELECT session_id FROM sys.dm_exec_sessions WHERE host_process_id=@barrier AND original_login_name=N'RTR-SVW-002\suporte')
 AND r.wait_type LIKE N'LCK_M_%';
 IF @found<2 WAITFOR DELAY '00:00:00.250';
 SET @tries+=1;
END;
IF @found<>2 THROW 52970,N'B60_TWO_REAL_WAITING_JVMS_REQUIRED',1;
SELECT N'B60_CONCURRENT_DISTINCT_WINDOWS_JVMS_WAITING=2';
