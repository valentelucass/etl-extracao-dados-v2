:On Error exit
SET NOCOUNT ON;
IF DB_NAME()<>N'ETL_SISTEMA_V2_SHADOW' OR ORIGINAL_LOGIN()<>N'RTR-SVW-002\suporte'
 THROW 52970,N'B60_CONCURRENCY_TARGET',1;
DECLARE @found INT=0,@tries INT=0,@first INT=$(B60FirstPid),@second INT=$(B60SecondPid),@barrier INT=$(B60BarrierPid);
IF @first<=0 OR @second<=0 OR @first=@second OR @barrier<=0 OR @barrier IN(@first,@second)
 THROW 52970,N'B60_OWN_DISTINCT_PIDS',1;
-- The barrier holds for eight seconds. Observe for seven; JVM and SQL deadlines remain unchanged.
-- Queue order can make the second waiter name the first waiter as its direct blocker.
-- Compare every resource-group column, including two NULL lock partitions, as the same resource.
DECLARE @samples TABLE(sample INT,observed_utc DATETIME2(3),host_process_id INT,session_id INT,
 wait_type NVARCHAR(60),blocking_session_id INT,matching_barrier_lock BIT);
WHILE @tries<28 AND @found<2
BEGIN
 SET @tries+=1;
 INSERT @samples
 SELECT @tries,SYSUTCDATETIME(),s.host_process_id,s.session_id,r.wait_type,r.blocking_session_id,
 CONVERT(BIT,CASE WHEN EXISTS(
  SELECT 1 FROM sys.dm_tran_locks w
  JOIN sys.dm_tran_locks b ON EXISTS(
   SELECT b.resource_type,b.resource_subtype,b.resource_database_id,b.resource_description,
    b.resource_associated_entity_id,b.resource_lock_partition
   INTERSECT
   SELECT w.resource_type,w.resource_subtype,w.resource_database_id,w.resource_description,
    w.resource_associated_entity_id,w.resource_lock_partition)
  JOIN sys.dm_exec_sessions bs ON bs.session_id=b.request_session_id
  WHERE w.request_session_id=s.session_id AND w.resource_type=N'APPLICATION'
   AND w.resource_database_id=DB_ID() AND w.request_status=N'WAIT' AND w.request_mode=N'X'
   AND w.request_owner_type=N'TRANSACTION' AND b.request_status=N'GRANT' AND b.request_mode=N'X'
   AND b.request_owner_type=N'TRANSACTION' AND bs.host_process_id=@barrier
   AND bs.original_login_name=N'RTR-SVW-002\suporte' AND bs.host_name=N'RTR-SVW-002'
 ) THEN 1 ELSE 0 END)
 FROM sys.dm_exec_sessions s JOIN sys.dm_exec_requests r ON r.session_id=s.session_id
 WHERE s.original_login_name=N'RTR-SVW-002\etl_v2_exec' AND s.host_name=N'RTR-SVW-002'
  AND s.host_process_id IN(@first,@second) AND r.database_id=DB_ID();
 SELECT @found=COUNT(DISTINCT host_process_id) FROM @samples
 WHERE sample=@tries AND matching_barrier_lock=1 AND wait_type LIKE N'LCK_M_%';
 -- One compact technical sample per poll, including empty polls. No query text or domain values.
 SELECT @tries sample,SYSUTCDATETIME() observedUtc,@found simultaneousOwnWaiters,
 JSON_QUERY((SELECT host_process_id processId,session_id sessionId,wait_type waitType,
 blocking_session_id blockingSessionId,matching_barrier_lock matchingBarrierLock
 FROM @samples WHERE sample=@tries FOR JSON PATH,INCLUDE_NULL_VALUES)) sessions
 FOR JSON PATH,WITHOUT_ARRAY_WRAPPER;
 IF @found<2 WAITFOR DELAY '00:00:00.250';
END;
IF @found<>2 THROW 52970,N'B60_TWO_REAL_WAITING_JVMS_REQUIRED',1;
SELECT N'B60_CONCURRENT_DISTINCT_WINDOWS_JVMS_WAITING=2';
