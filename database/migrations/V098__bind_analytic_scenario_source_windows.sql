-- ANA-37: source receipt windows are distinct from the full three-day materialization scope.
SET ANSI_NULLS ON;SET QUOTED_IDENTIFIER ON;
GO
ALTER PROCEDURE ctl.usp_begin_analytic_scenario @run UNIQUEIDENTIFIER,@mode VARCHAR(16),@revision INT,
 @start DATE,@end DATE,@replay UNIQUEIDENTIFIER,@refs INT,@roots INT,@page INT,@correction BIT
AS BEGIN
 SET NOCOUNT ON;SET XACT_ABORT OFF;EXEC ctl.usp_analytic_lab_lock @run,'PLAN';
 IF @run IS NULL OR @mode IS NULL OR @revision IS NULL OR @roots IS NULL OR @page IS NULL OR @refs IS NULL OR @mode NOT IN('BOOTSTRAP','INCREMENTAL','BACKFILL','REPLAY') OR @revision NOT BETWEEN 1 AND 1000
 OR @roots NOT BETWEEN 2 AND 480 OR @page NOT BETWEEN 1 AND 16 OR @refs NOT BETWEEN 1 AND 100000
 OR @start IS NULL OR @end IS NULL OR @start>=@end OR @correction IS NULL
 OR NOT EXISTS(SELECT 1 FROM ctl.analytic_lab_run WHERE run_id=@run AND @start>=window_start AND @end<=window_end_exclusive)
 OR (@mode='REPLAY' AND (@replay IS NULL OR NOT EXISTS(SELECT 1 FROM ctl.analytic_scenario_cycle WHERE cycle_id=@replay AND run_id=@run AND state='COMPLETE')))
 OR (@mode<>'REPLAY' AND @replay IS NOT NULL) THROW 53821,N'ANA_SCENARIO_SCOPE',1;
 IF EXISTS(SELECT 1 FROM ctl.analytic_scenario_cycle WHERE run_id=@run AND mode=@mode AND revision=@revision
 AND (source_start<>@start OR source_end_exclusive<>@end OR reference_revision<>@refs OR roots<>@roots OR page_size<>@page OR correction<>@correction
 OR ISNULL(replay_of,'00000000-0000-0000-0000-000000000000')<>ISNULL(@replay,'00000000-0000-0000-0000-000000000000')))
 THROW 53822,N'ANA_SCENARIO_RETRY_DIVERGENT',1;
 IF NOT EXISTS(SELECT 1 FROM ctl.analytic_scenario_cycle WHERE run_id=@run AND mode=@mode AND revision=@revision)
 BEGIN
  DECLARE @cycle UNIQUEIDENTIFIER=NEWID();
  INSERT ctl.analytic_scenario_cycle(cycle_id,run_id,mode,revision,source_start,source_end_exclusive,replay_of,reference_revision,roots,page_size,correction,mat01,mat02,mat03,mat04,mat05)
  VALUES(@cycle,@run,@mode,@revision,@start,@end,@replay,@refs,@roots,@page,@correction,NEWID(),NEWID(),NEWID(),NEWID(),NEWID());
  INSERT ctl.analytic_scenario_step(cycle_id,entity)
  SELECT @cycle,entity FROM(VALUES('CAP'),('FAT'),('INV'),('SIN'),('FRETE'),('LOC'),('MAN'),('COL'),('COT'),('USUARIO'),('RASTER'))e(entity);
  IF NOT EXISTS(SELECT 1 FROM ctl.analytic_scenario_frontier WHERE run_id=@run)
  INSERT ctl.analytic_scenario_frontier(run_id,next_date) SELECT run_id,window_start FROM ctl.analytic_lab_run WHERE run_id=@run;
 END;
 SELECT cycle_id,mat01,mat02,mat03,mat04,mat05,state FROM ctl.analytic_scenario_cycle WHERE run_id=@run AND mode=@mode AND revision=@revision;
END;
GO
ALTER VIEW core.analytic_scenario_source_receipt AS
 SELECT g.run_id,CONVERT(VARCHAR(8),c.vertical) COLLATE Latin1_General_100_BIN2 entity,c.execution_id,
 CONVERT(BIT,CASE WHEN c.state='COMPLETE' AND a.quarantine=0 AND a.unbound=0 THEN 1 ELSE 0 END) valid,
 CONVERT(VARCHAR(16),c.mode) COLLATE Latin1_General_100_BIN2 mode,c.partition_start source_start,c.partition_end_exclusive source_end_exclusive
 FROM ctl.analytic_lab_source_group g JOIN ctl.expansion_lab_capture c ON c.run_id=g.expansion_run
 LEFT JOIN ctl.expansion_lab_apply_receipt a ON a.execution_id=c.execution_id
 UNION ALL SELECT g.run_id,c.entity,c.execution_id,CONVERT(BIT,CASE WHEN c.state='COMPLETE' AND c.quarantine=0 THEN 1 ELSE 0 END),p.execution_mode,c.partition_date,DATEADD(DAY,1,c.partition_date)
 FROM ctl.analytic_lab_source_group g JOIN ctl.expansion_lab_dependency_capture c ON c.run_id=g.expansion_run
 JOIN ctl.execution_attempt e ON e.execution_id=c.execution_id JOIN ctl.execution_partition p ON p.partition_id=e.partition_id
 UNION ALL SELECT p.run_id,'MAN',p.execution_id,CONVERT(BIT,CASE WHEN p.blocked_roots=0 THEN 1 ELSE 0 END),part.execution_mode,CONVERT(DATE,part.partition_start_utc),CONVERT(DATE,part.partition_end_exclusive_utc)
 FROM ctl.analytic_manifest_preparation p
 JOIN ctl.execution_attempt e ON e.execution_id=p.execution_id JOIN ctl.execution_partition part ON part.partition_id=e.partition_id
 UNION ALL SELECT p.run_id,'COL',p.execution_id,CONVERT(BIT,1),part.execution_mode,CONVERT(DATE,part.partition_start_utc),CONVERT(DATE,part.partition_end_exclusive_utc) FROM ctl.analytic_collection_preparation p
 JOIN ctl.execution_attempt e ON e.execution_id=p.execution_id JOIN ctl.execution_partition part ON part.partition_id=e.partition_id
 UNION ALL SELECT s.run_id,s.entity,s.execution_id,CONVERT(BIT,CASE WHEN e.current_state='PUBLISHED' THEN 1 ELSE 0 END),p.execution_mode,CONVERT(DATE,p.partition_start_utc),CONVERT(DATE,p.partition_end_exclusive_utc)
 FROM ctl.analytic_lab_execution_source s JOIN ctl.execution_attempt e ON e.execution_id=s.execution_id
 JOIN ctl.execution_partition p ON p.partition_id=e.partition_id
 UNION ALL SELECT r.run_id,'RASTER',r.capture_id,CONVERT(BIT,CASE WHEN r.state='APPLIED' AND r.quarantine=0 AND r.unbound=0
 AND EXISTS(SELECT 1 FROM ctl.analytic_raster_terminal_window w WHERE w.capture_id=r.capture_id) THEN 1 ELSE 0 END),r.mode,r.start_date,r.end_exclusive
 FROM ctl.analytic_raster_capture r;
GO
ALTER PROCEDURE ctl.usp_complete_analytic_scenario @cycle UNIQUEIDENTIFIER,@sources ctl.analytic_scenario_source_batch READONLY,@failure VARCHAR(40)=NULL
AS BEGIN
 SET NOCOUNT ON;SET XACT_ABORT OFF;
 DECLARE @run UNIQUEIDENTIFIER,@exp UNIQUEIDENTIFIER,@refs INT,@mode VARCHAR(16),@start DATE,@end DATE,@state VARCHAR(16);
 SELECT @run=c.run_id,@exp=g.expansion_run,@refs=c.reference_revision,@mode=c.mode,@start=c.source_start,@end=c.source_end_exclusive,@state=c.state
 FROM ctl.analytic_scenario_cycle c JOIN ctl.analytic_lab_source_group g ON g.run_id=c.run_id WHERE c.cycle_id=@cycle;
 IF @run IS NULL THROW 53821,N'ANA_SCENARIO_SCOPE',1;
 EXEC ctl.usp_analytic_lab_lock @run,'PLAN';
 IF (SELECT COUNT(*) FROM @sources)<>11 OR EXISTS(SELECT entity FROM @sources EXCEPT SELECT entity FROM ctl.analytic_scenario_step WHERE cycle_id=@cycle)
 OR (@failure IS NOT NULL AND @failure NOT IN('RASTER_INCOMPLETE','MANIFEST_FLEET_MISSING','FINANCIAL_REFERENCE_MISSING','COLLECTION_SNAPSHOT_INVALID'))
 THROW 53823,N'ANA_SCENARIO_SOURCES',1;
 IF @state<>'PENDING'
 BEGIN
  IF EXISTS(SELECT entity,execution_id FROM @sources EXCEPT SELECT entity,execution_id FROM ctl.analytic_scenario_step WHERE cycle_id=@cycle)
  OR (@failure IS NOT NULL AND @failure<>ISNULL((SELECT failure FROM ctl.analytic_scenario_cycle WHERE cycle_id=@cycle),''))
  THROW 53822,N'ANA_SCENARIO_RETRY_DIVERGENT',1;
  SELECT state,failure,(SELECT next_date FROM ctl.analytic_scenario_frontier WHERE run_id=@run) next_date FROM ctl.analytic_scenario_cycle WHERE cycle_id=@cycle;RETURN;
 END;
 SAVE TRANSACTION ana_scenario_seal;
 BEGIN TRY
  UPDATE s SET execution_id=i.execution_id FROM ctl.analytic_scenario_step s JOIN @sources i ON i.entity=s.entity WHERE s.cycle_id=@cycle;
  IF @failure IS NULL AND EXISTS(SELECT 1 FROM @sources s LEFT JOIN core.analytic_scenario_source_receipt r
  ON r.run_id=@run AND r.entity=s.entity AND r.execution_id=s.execution_id
  WHERE ISNULL(r.valid,0)=0 OR r.mode<>CASE WHEN s.entity='USUARIO' AND @mode<>'REPLAY' THEN 'BACKFILL' ELSE @mode END
  OR r.source_start IS NULL OR r.source_end_exclusive IS NULL
  OR (s.entity<>'RASTER' AND (r.source_start<>@start OR r.source_end_exclusive<>@end))
  OR (s.entity='RASTER' AND (r.source_start>@start OR r.source_end_exclusive<@end)))
  SET @failure='SOURCE_UNPROVEN';
  IF @failure IS NULL AND @mode='INCREMENTAL' AND NOT EXISTS(
   SELECT 1 FROM ctl.analytic_scenario_frontier WHERE run_id=@run AND next_date=@start)
   SET @failure='SOURCE_UNPROVEN';
  IF @failure IS NULL AND (
   (SELECT COUNT(*) FROM ctl.analytic_scenario_cycle c JOIN ctl.analytic_lab_materialization_receipt r ON r.run_id=c.run_id
    AND ((r.kind='MAT01' AND r.receipt_id=c.mat01) OR(r.kind='MAT02' AND r.receipt_id=c.mat02) OR(r.kind='MAT05' AND r.receipt_id=c.mat05))
    JOIN ctl.analytic_lab_run a ON a.run_id=c.run_id WHERE c.cycle_id=@cycle AND r.reference_revision=@refs AND r.mode=@mode AND r.full_scope=1
    AND r.window_start=a.window_start AND r.window_end_exclusive=a.window_end_exclusive AND r.blocked=0 AND r.ready>0)<>3
   OR (SELECT COUNT(*) FROM ctl.analytic_scenario_cycle c JOIN ctl.expansion_lab_materialization_receipt r ON r.run_id=@exp
    AND ((r.kind='REVENUE' AND r.receipt_id=c.mat03) OR(r.kind='INVOICE' AND r.receipt_id=c.mat04))
    JOIN ctl.analytic_lab_run a ON a.run_id=c.run_id WHERE c.cycle_id=@cycle AND r.mode=@mode AND r.full_scope=1
    AND r.window_start=a.window_start AND r.window_end_exclusive=a.window_end_exclusive AND r.blocked=0 AND r.ready>0)<>2)
   SET @failure='FACT_BLOCKED';
  INSERT ctl.analytic_scenario_query_receipt SELECT @cycle,1,42,COUNT_BIG(*) FROM pub.analytic_lab_sql_01 WHERE run_id=@run AND reference_revision=@refs;
 INSERT ctl.analytic_scenario_query_receipt SELECT @cycle,2,123,COUNT_BIG(*) FROM pub.analytic_lab_sql_02 WHERE run_id=@run AND reference_revision=@refs;
 INSERT ctl.analytic_scenario_query_receipt SELECT @cycle,3,41,COUNT_BIG(*) FROM pub.analytic_lab_sql_03 WHERE run_id=@run AND reference_revision=@refs;
 INSERT ctl.analytic_scenario_query_receipt SELECT @cycle,4,13,COUNT_BIG(*) FROM pub.analytic_lab_sql_04 WHERE run_id=@run AND reference_revision=@refs;
 INSERT ctl.analytic_scenario_query_receipt SELECT @cycle,5,54,COUNT_BIG(*) FROM pub.analytic_lab_sql_05 WHERE run_id=@run AND reference_revision=@refs;
 INSERT ctl.analytic_scenario_query_receipt SELECT @cycle,6,31,COUNT_BIG(*) FROM pub.analytic_lab_sql_06 WHERE run_id=@run AND reference_revision=@refs;
 INSERT ctl.analytic_scenario_query_receipt SELECT @cycle,7,29,COUNT_BIG(*) FROM pub.analytic_lab_sql_07 WHERE run_id=@run AND reference_revision=@refs;
 INSERT ctl.analytic_scenario_query_receipt SELECT @cycle,8,105,COUNT_BIG(*) FROM pub.analytic_lab_sql_08 WHERE run_id=@run AND reference_revision=@refs;
 INSERT ctl.analytic_scenario_query_receipt SELECT @cycle,9,106,COUNT_BIG(*) FROM pub.analytic_lab_sql_09 WHERE run_id=@run AND reference_revision=@refs;
 INSERT ctl.analytic_scenario_query_receipt SELECT @cycle,10,9,COUNT_BIG(*) FROM pub.analytic_lab_sql_10 WHERE run_id=@run;
 INSERT ctl.analytic_scenario_query_receipt SELECT @cycle,11,34,COUNT_BIG(*) FROM pub.analytic_lab_sql_11 WHERE run_id=@run AND reference_revision=@refs;
 INSERT ctl.analytic_scenario_query_receipt SELECT @cycle,12,34,COUNT_BIG(*) FROM pub.analytic_lab_sql_12 WHERE run_id=@run AND reference_revision=@refs;
 INSERT ctl.analytic_scenario_query_receipt SELECT @cycle,13,37,COUNT_BIG(*) FROM pub.analytic_lab_sql_13 WHERE run_id=@run;
 INSERT ctl.analytic_scenario_query_receipt SELECT @cycle,14,2,COUNT_BIG(*) FROM pub.analytic_lab_sql_14 WHERE run_id=@run AND reference_revision=@refs;
 INSERT ctl.analytic_scenario_query_receipt SELECT @cycle,15,1,COUNT_BIG(*) FROM pub.analytic_lab_sql_15 WHERE run_id=@run AND reference_revision=@refs;
 INSERT ctl.analytic_scenario_query_receipt SELECT @cycle,16,4,COUNT_BIG(*) FROM pub.analytic_lab_sql_16 WHERE run_id=@run AND reference_revision=@refs;
 INSERT ctl.analytic_scenario_query_receipt SELECT @cycle,17,2,COUNT_BIG(*) FROM pub.analytic_lab_sql_17 WHERE run_id=@run AND reference_revision=@refs;
 INSERT ctl.analytic_scenario_query_receipt SELECT @cycle,18,3,COUNT_BIG(*) FROM pub.analytic_lab_sql_18 WHERE run_id=@run AND reference_revision=@refs;
 INSERT ctl.analytic_scenario_query_receipt SELECT @cycle,19,3,COUNT_BIG(*) FROM pub.analytic_lab_sql_19 WHERE run_id=@run;
  IF @failure IS NULL AND EXISTS(SELECT 1 FROM ctl.analytic_scenario_query_receipt WHERE cycle_id=@cycle AND query_number<>4 AND rows=0)
  SET @failure='QUERY_BLOCKED';
  UPDATE ctl.analytic_scenario_cycle SET state=CASE WHEN @failure IS NULL THEN 'COMPLETE' ELSE 'DEGRADED' END,failure=@failure,completed_at=SYSUTCDATETIME() WHERE cycle_id=@cycle;
  IF @failure IS NULL AND @mode='INCREMENTAL'
  UPDATE ctl.analytic_scenario_frontier SET next_date=@end,last_cycle=@cycle WHERE run_id=@run AND next_date=@start;
  SELECT state,failure,(SELECT next_date FROM ctl.analytic_scenario_frontier WHERE run_id=@run) next_date FROM ctl.analytic_scenario_cycle WHERE cycle_id=@cycle;
 END TRY BEGIN CATCH IF XACT_STATE()=1 ROLLBACK TRANSACTION ana_scenario_seal;THROW;END CATCH;
END;
GO
