-- Explicit lateral source lifecycle; provider 6399 does not declare an exclusion flag.
SET ANSI_NULLS ON; SET QUOTED_IDENTIFIER ON;
GO
CREATE TABLE ref.analytic_lab_manifest_state(
 state_id BIGINT IDENTITY NOT NULL PRIMARY KEY,run_id UNIQUEIDENTIFIER NOT NULL,
 source_key NVARCHAR(256) COLLATE Latin1_General_100_BIN2 NOT NULL,
 source_execution UNIQUEIDENTIFIER NOT NULL,revision INT NOT NULL,active BIT NOT NULL,
 reactivate BIT NOT NULL,evidence VARCHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL,
 CONSTRAINT FK_analytic_manifest_state FOREIGN KEY(run_id,source_execution,source_key)
 REFERENCES core.analytic_manifest_snapshot(run_id,execution_id,source_key),
 CONSTRAINT UQ_analytic_manifest_state UNIQUE(run_id,source_key,revision),
 CONSTRAINT CK_analytic_manifest_state CHECK(revision BETWEEN 1 AND 100000 AND evidence='synthetic-manifest-state-v1'
 AND (reactivate=0 OR active=1) AND stg.fn_relational_lab_key_valid(source_key)=1)
);
GO
CREATE TRIGGER ref.trg_analytic_manifest_state ON ref.analytic_lab_manifest_state AFTER UPDATE,DELETE
AS BEGIN SET NOCOUNT ON; IF EXISTS(SELECT 1 FROM deleted) THROW 53610,N'ANA_MANIFEST_STATE_IMMUTABLE',1; END;
GO
CREATE TYPE ref.analytic_lab_manifest_state_batch AS TABLE(
 source_key NVARCHAR(256) COLLATE Latin1_General_100_BIN2 NOT NULL,source_execution UNIQUEIDENTIFIER NOT NULL,
 revision INT NOT NULL,active BIT NOT NULL,reactivate BIT NOT NULL,evidence VARCHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL,
 PRIMARY KEY(source_key)
);
GO
CREATE PROCEDURE ref.usp_bind_analytic_manifest_state @run_id UNIQUEIDENTIFIER,@rows ref.analytic_lab_manifest_state_batch READONLY
AS BEGIN SET NOCOUNT ON; SET XACT_ABORT ON;
 EXEC ctl.usp_analytic_lab_lock @run_id,'DIMENSION';
 IF (SELECT COUNT_BIG(*) FROM @rows) NOT BETWEEN 1 AND 64 THROW 53611,N'ANA_MANIFEST_STATE_BATCH_BOUND',1;
 IF EXISTS(SELECT 1 FROM @rows r WHERE NOT EXISTS(SELECT 1 FROM core.analytic_manifest_current c JOIN core.analytic_manifest_snapshot s ON s.snapshot_id=c.snapshot_id
 WHERE c.run_id=@run_id AND c.source_key=r.source_key AND s.execution_id=r.source_execution)) THROW 53612,N'ANA_MANIFEST_STATE_CURRENT_CAPTURE',1;
 IF EXISTS(SELECT 1 FROM @rows r JOIN ref.analytic_lab_manifest_state s ON s.run_id=@run_id AND s.source_key=r.source_key AND s.revision=r.revision
 WHERE s.source_execution<>r.source_execution OR s.active<>r.active OR s.reactivate<>r.reactivate OR s.evidence<>r.evidence)
 THROW 53613,N'ANA_MANIFEST_STATE_REVISION_CONFLICT',1;
 IF EXISTS(SELECT 1 FROM @rows r JOIN ref.analytic_lab_manifest_state s ON s.run_id=@run_id AND s.source_key=r.source_key AND s.revision>r.revision)
 THROW 53614,N'ANA_MANIFEST_STATE_STALE_REVISION',1;
 IF EXISTS(SELECT 1 FROM @rows r WHERE r.active=1 AND r.reactivate=0 AND EXISTS(
 SELECT 1 FROM ref.analytic_lab_manifest_state s WHERE s.run_id=@run_id AND s.source_key=r.source_key AND s.revision<r.revision AND s.active=0
 AND NOT EXISTS(SELECT 1 FROM ref.analytic_lab_manifest_state later WHERE later.run_id=s.run_id AND later.source_key=s.source_key AND later.revision>s.revision)))
 THROW 53615,N'ANA_MANIFEST_REACTIVATION_REQUIRED',1;
 INSERT ref.analytic_lab_manifest_state(run_id,source_key,source_execution,revision,active,reactivate,evidence)
 SELECT @run_id,r.* FROM @rows r WHERE NOT EXISTS(SELECT 1 FROM ref.analytic_lab_manifest_state s WHERE s.run_id=@run_id AND s.source_key=r.source_key AND s.revision=r.revision);
 SELECT COUNT_BIG(*) FROM @rows;
END;
GO
CREATE VIEW ref.analytic_lab_manifest_state_current AS
 SELECT s.* FROM ref.analytic_lab_manifest_state s WHERE NOT EXISTS(
 SELECT 1 FROM ref.analytic_lab_manifest_state later WHERE later.run_id=s.run_id AND later.source_key=s.source_key AND later.revision>s.revision);
GO
