-- Runs inside the schema qualification transaction; caller always rolls back.
IF @@TRANCOUNT<>1 OR XACT_STATE()<>1 THROW 53933,N'P03_ROLLBACK_OWNER_REQUIRED',1;
DECLARE @run UNIQUEIDENTIFIER=NEWID(),@receipt UNIQUEIDENTIFIER,@now DATETIME2(7)=SYSUTCDATETIME();
INSERT ctl.relational_lab_run VALUES(@run,N'SYNTHETIC_RELATIONAL_LAB',N'SYNTHETIC_RELATIONAL_TENANT',
 N'synthetic-relational-v1',REPLICATE('a',64),'20360401','20360401',100,10,3,30,10,0,@now,10,20);
DECLARE @rows stg.relational_lab_binding_batch;
INSERT @rows VALUES('old-a','MC','INTEGER:1','STRING:a','INTEGER:10','ROOT','20360401',1,'ONE_TO_ONE'),
 ('other-origin','MC','INTEGER:2','STRING:c','INTEGER:20','ROOT','20360401',1,'ONE_TO_ONE');
EXEC stg.usp_relational_lab_bind @run,@rows,@now;
EXEC stg.usp_declare_relational_mc_sets @run,1,@now;
DECLARE @later stg.relational_lab_binding_batch;
INSERT @later VALUES('new-b','MC','INTEGER:1','STRING:b','INTEGER:11','ROOT','20360401',2,'ONE_TO_ONE');
EXEC stg.usp_relational_lab_bind @run,@later,@now;
SET @receipt=NEWID(); EXEC core.usp_resolve_relational_laboratory @run,@receipt,@now;
IF EXISTS(SELECT 1 FROM recon.relational_lab_resolution WHERE receipt_id=@receipt AND disposition='SUPERSEDED')
 THROW 53934,N'PARTIAL_BIND_MUST_NOT_RETIRE',1;
EXEC stg.usp_declare_relational_mc_sets @run,2,@now;
EXEC stg.usp_declare_relational_mc_sets @run,2,@now;
IF (SELECT COUNT(*) FROM stg.relational_lab_mc_set WHERE run_id=@run)<>3 THROW 53934,N'SET_REPLAY_NOT_IDEMPOTENT',1;
SET @receipt=NEWID(); EXEC core.usp_resolve_relational_laboratory @run,@receipt,@now;
IF (SELECT COUNT(*) FROM recon.relational_lab_resolution WHERE receipt_id=@receipt AND disposition='SUPERSEDED')<>1
 THROW 53934,N'EXPLICIT_SET_MUST_RETIRE_ONLY_INCLUDED_ORIGIN',1;
IF (SELECT COUNT(*) FROM stg.relational_lab_binding WHERE run_id=@run)<>3 THROW 53934,N'BINDING_HISTORY_LOST',1;
DECLARE @conflict stg.relational_lab_binding_batch;
INSERT @conflict VALUES('conflict','MC','INTEGER:1','STRING:b','INTEGER:12','ROOT','20360401',2,'ONE_TO_ONE');
EXEC stg.usp_relational_lab_bind @run,@conflict,@now;
SET @receipt=NEWID(); EXEC core.usp_resolve_relational_laboratory @run,@receipt,@now;
IF (SELECT COUNT(*) FROM recon.relational_lab_resolution WHERE receipt_id=@receipt AND disposition='CONFLICT')<>2
 THROW 53934,N'CONTEMPORARY_CONFLICT_MUST_REMAIN',1;
PRINT N'P03_MC_COUNTERPROOFS_PASS';
GO
