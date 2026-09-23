-- Persisted bounded intents; receipts are rehydrated within the rollback-only laboratory session.
SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;
GO
CREATE TYPE recon.expansion_lab_plan_batch AS TABLE(
 ordinal INT NOT NULL PRIMARY KEY,partition_date DATE NOT NULL,first_root INT NOT NULL,root_count INT NOT NULL,
 missing_last_freight BIT NOT NULL,source_revision INT NOT NULL,reference_revision INT NOT NULL
);
GO
CREATE TABLE recon.expansion_lab_partition(
 partition_id UNIQUEIDENTIFIER NOT NULL PRIMARY KEY,run_id UNIQUEIDENTIFIER NOT NULL,
 mode VARCHAR(16) NOT NULL,revision INT NOT NULL,ordinal INT NOT NULL,partition_date DATE NOT NULL,
 first_root INT NOT NULL,root_count INT NOT NULL,missing_last_freight BIT NOT NULL,
 source_revision INT NOT NULL,reference_revision INT NOT NULL,replay_revision INT NULL,
 invoice_receipt UNIQUEIDENTIFIER NOT NULL,revenue_receipt UNIQUEIDENTIFIER NOT NULL,
 state VARCHAR(16) NOT NULL DEFAULT 'PENDING',completed_at DATETIME2(7) NULL,
 CONSTRAINT FK_exp_plan_run FOREIGN KEY(run_id) REFERENCES ctl.expansion_lab_run(run_id),
 CONSTRAINT UQ_exp_plan_ordinal UNIQUE(run_id,mode,revision,ordinal),
 CONSTRAINT UQ_exp_plan_date UNIQUE(run_id,mode,revision,partition_date),
 CONSTRAINT CK_exp_plan_scope CHECK(mode IN('BOOTSTRAP','INCREMENTAL','BACKFILL','REPLAY') AND revision BETWEEN 1 AND 1000
 AND ordinal BETWEEN 1 AND 31 AND first_root BETWEEN 1 AND 2048 AND root_count BETWEEN 0 AND 2048
 AND first_root+root_count<=2049 AND source_revision BETWEEN 1 AND 1000 AND reference_revision BETWEEN 1 AND 1000
 AND ((mode='REPLAY' AND replay_revision BETWEEN 1 AND 1000) OR(mode<>'REPLAY' AND replay_revision IS NULL))),
 CONSTRAINT CK_exp_plan_state CHECK(state IN('PENDING','COMPLETE','DEGRADED'))
);
CREATE TABLE recon.expansion_lab_step(
 partition_id UNIQUEIDENTIFIER NOT NULL,entity VARCHAR(8) NOT NULL,execution_id UNIQUEIDENTIFIER NOT NULL,
 original_execution UNIQUEIDENTIFIER NULL,attached BIT NOT NULL DEFAULT 0,
 CONSTRAINT PK_exp_plan_step PRIMARY KEY(partition_id,entity),
 CONSTRAINT UQ_exp_plan_execution UNIQUE(execution_id),
 CONSTRAINT FK_exp_plan_step FOREIGN KEY(partition_id) REFERENCES recon.expansion_lab_partition(partition_id),
 CONSTRAINT CK_exp_plan_entity CHECK(entity IN('CAP','FAT','INV','SIN','FRETE','LOC'))
);
CREATE TABLE recon.expansion_lab_failure(
 failure_id BIGINT IDENTITY PRIMARY KEY,partition_id UNIQUEIDENTIFIER NOT NULL,boundary VARCHAR(32) NOT NULL,
 category VARCHAR(16) NOT NULL,recorded_at DATETIME2(7) NOT NULL,
 CONSTRAINT FK_exp_plan_failure FOREIGN KEY(partition_id) REFERENCES recon.expansion_lab_partition(partition_id),
 CONSTRAINT CK_exp_failure_category CHECK(category IN('SQL','CANCELLED','CONTRACT','INJECTED'))
);
GO
CREATE PROCEDURE recon.usp_plan_expansion_lab @run_id UNIQUEIDENTIFIER,@mode VARCHAR(16),@revision INT,
 @replay_revision INT,@slots recon.expansion_lab_plan_batch READONLY
AS
BEGIN
 SET NOCOUNT ON;SET XACT_ABORT ON;EXEC ctl.usp_expansion_lab_lock @run_id,'PLAN';
 IF @mode NOT IN('BOOTSTRAP','INCREMENTAL','BACKFILL','REPLAY') OR @revision NOT BETWEEN 1 AND 1000
 OR (@mode='REPLAY' AND @replay_revision IS NULL) OR (@mode<>'REPLAY' AND @replay_revision IS NOT NULL)
 OR (SELECT COUNT(*) FROM @slots) NOT BETWEEN 1 AND 31 THROW 53490,N'EXP_PLAN_SCOPE',1;
 IF EXISTS(SELECT 1 FROM @slots s LEFT JOIN ctl.expansion_lab_run r ON r.run_id=@run_id
 WHERE r.run_id IS NULL OR s.partition_date<r.window_start OR s.partition_date>=r.window_end_exclusive
 OR s.ordinal NOT BETWEEN 1 AND 31 OR s.root_count<0 OR s.first_root<1 OR s.first_root+s.root_count>2049
 OR s.source_revision NOT BETWEEN 1 AND 1000 OR s.reference_revision NOT BETWEEN 1 AND 1000)
 THROW 53490,N'EXP_PLAN_SCOPE',1;
 IF EXISTS(SELECT 1 FROM recon.expansion_lab_partition WHERE run_id=@run_id AND mode=@mode AND revision=@revision)
 BEGIN
  IF EXISTS(SELECT ordinal,partition_date,first_root,root_count,missing_last_freight,source_revision,reference_revision FROM @slots
  EXCEPT SELECT ordinal,partition_date,first_root,root_count,missing_last_freight,source_revision,reference_revision
  FROM recon.expansion_lab_partition WHERE run_id=@run_id AND mode=@mode AND revision=@revision)
  OR EXISTS(SELECT ordinal,partition_date,first_root,root_count,missing_last_freight,source_revision,reference_revision
  FROM recon.expansion_lab_partition WHERE run_id=@run_id AND mode=@mode AND revision=@revision
  EXCEPT SELECT ordinal,partition_date,first_root,root_count,missing_last_freight,source_revision,reference_revision FROM @slots)
  OR EXISTS(SELECT 1 FROM recon.expansion_lab_partition WHERE run_id=@run_id AND mode=@mode AND revision=@revision
  AND ISNULL(replay_revision,0)<>ISNULL(@replay_revision,0)) THROW 53491,N'EXP_PLAN_RETRY_DIVERGENT',1;
  RETURN;
 END;
 IF @mode='REPLAY' AND EXISTS(SELECT 1 FROM @slots s WHERE NOT EXISTS(
 SELECT 1 FROM recon.expansion_lab_partition p WHERE p.run_id=@run_id AND p.mode='BOOTSTRAP' AND p.revision=@replay_revision
 AND p.partition_date=s.partition_date AND p.first_root=s.first_root AND p.root_count=s.root_count
 AND p.source_revision=s.source_revision AND p.missing_last_freight=s.missing_last_freight AND p.state='COMPLETE'))
 THROW 53492,N'EXP_PLAN_REPLAY_ORIGINAL_REQUIRED',1;
 INSERT recon.expansion_lab_partition(partition_id,run_id,mode,revision,ordinal,partition_date,first_root,root_count,
 missing_last_freight,source_revision,reference_revision,replay_revision,invoice_receipt,revenue_receipt)
 SELECT NEWID(),@run_id,@mode,@revision,ordinal,partition_date,first_root,root_count,missing_last_freight,
 source_revision,reference_revision,@replay_revision,NEWID(),NEWID() FROM @slots;
 INSERT recon.expansion_lab_step(partition_id,entity,execution_id,original_execution)
 SELECT p.partition_id,e.entity,NEWID(),s.execution_id FROM recon.expansion_lab_partition p
 CROSS JOIN(VALUES('CAP'),('FAT'),('INV'),('SIN'),('FRETE'),('LOC')) e(entity)
 LEFT JOIN recon.expansion_lab_partition o ON @mode='REPLAY' AND o.run_id=p.run_id AND o.mode='BOOTSTRAP'
 AND o.revision=@replay_revision AND o.partition_date=p.partition_date
 LEFT JOIN recon.expansion_lab_step s ON s.partition_id=o.partition_id AND s.entity=e.entity
 WHERE p.run_id=@run_id AND p.mode=@mode AND p.revision=@revision;
END;
GO
CREATE VIEW recon.expansion_lab_step_capture AS
 SELECT execution_id,run_id,vertical entity,partition_start partition_date,mode COLLATE DATABASE_DEFAULT mode,state FROM ctl.expansion_lab_capture
 UNION ALL SELECT c.execution_id,c.run_id,c.entity,c.partition_date,p.execution_mode COLLATE DATABASE_DEFAULT,c.state
 FROM ctl.expansion_lab_dependency_capture c JOIN ctl.execution_attempt a ON a.execution_id=c.execution_id
 JOIN ctl.execution_partition p ON p.partition_id=a.partition_id;
GO
CREATE PROCEDURE recon.usp_attach_expansion_lab_step @partition_id UNIQUEIDENTIFIER,@entity VARCHAR(8)
AS
BEGIN
 SET NOCOUNT ON;SET XACT_ABORT ON;
 DECLARE @run UNIQUEIDENTIFIER=(SELECT run_id FROM recon.expansion_lab_partition WHERE partition_id=@partition_id);
 EXEC ctl.usp_expansion_lab_lock @run,'PLAN';
 IF NOT EXISTS(SELECT 1 FROM recon.expansion_lab_step s JOIN recon.expansion_lab_partition p ON p.partition_id=s.partition_id
 JOIN recon.expansion_lab_step_capture c ON c.execution_id=s.execution_id AND c.run_id=p.run_id AND c.entity=s.entity
 AND c.partition_date=p.partition_date AND c.mode=p.mode AND c.state='COMPLETE'
 WHERE s.partition_id=@partition_id AND s.entity=@entity) THROW 53493,N'EXP_PLAN_CAPTURE_INCOMPLETE',1;
 UPDATE recon.expansion_lab_step SET attached=1 WHERE partition_id=@partition_id AND entity=@entity;
END;
GO
CREATE PROCEDURE recon.usp_complete_expansion_lab_partition @partition_id UNIQUEIDENTIFIER,@now DATETIME2(7)
AS
BEGIN
 SET NOCOUNT ON;SET XACT_ABORT ON;
 DECLARE @run UNIQUEIDENTIFIER=(SELECT run_id FROM recon.expansion_lab_partition WHERE partition_id=@partition_id);
 EXEC ctl.usp_expansion_lab_lock @run,'PLAN';
 IF (SELECT COUNT(*) FROM recon.expansion_lab_step WHERE partition_id=@partition_id AND attached=1)<>6
 THROW 53494,N'EXP_PLAN_STEPS_INCOMPLETE',1;
 IF NOT EXISTS(SELECT 1 FROM recon.expansion_lab_partition p
 JOIN ctl.expansion_lab_materialization_receipt i ON i.receipt_id=p.invoice_receipt AND i.run_id=p.run_id AND i.kind='INVOICE'
 JOIN ctl.expansion_lab_materialization_receipt j ON j.receipt_id=p.revenue_receipt AND j.run_id=p.run_id AND j.kind='REVENUE'
 WHERE p.partition_id=@partition_id AND i.reference_revision=p.reference_revision AND j.reference_revision=p.reference_revision
 AND i.mode=p.mode AND j.mode=p.mode AND i.window_start=p.partition_date AND j.window_start=p.partition_date
 AND i.window_end_exclusive=DATEADD(DAY,1,p.partition_date) AND j.window_end_exclusive=DATEADD(DAY,1,p.partition_date))
 THROW 53495,N'EXP_PLAN_MATERIALIZATIONS_INCOMPLETE',1;
 UPDATE p SET state=CASE WHEN i.blocked+j.blocked>0 OR EXISTS(SELECT 1 FROM core.expansion_lab_link l
 WHERE l.run_id=p.run_id AND l.state IN('MISSING_TARGET','MISSING_SOURCE','CONFLICT')) THEN 'DEGRADED' ELSE 'COMPLETE' END,
 completed_at=@now FROM recon.expansion_lab_partition p
 JOIN ctl.expansion_lab_materialization_receipt i ON i.receipt_id=p.invoice_receipt
 JOIN ctl.expansion_lab_materialization_receipt j ON j.receipt_id=p.revenue_receipt
 WHERE p.partition_id=@partition_id AND p.state='PENDING';
END;
GO
