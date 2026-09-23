-- ADR0050 ANA-34: a bounded set of composition declarations uses the existing immutable seal.
SET ANSI_NULLS ON; SET QUOTED_IDENTIFIER ON;
GO
CREATE TYPE ctl.analytic_manifest_composition_batch AS TABLE(
 manifest_key NVARCHAR(128) COLLATE Latin1_General_100_BIN2 NOT NULL,
 manifest_execution UNIQUEIDENTIFIER NOT NULL,revision INT NOT NULL,expected_direct_freights INT NOT NULL,
 PRIMARY KEY(manifest_key,revision)
);
GO
CREATE PROCEDURE ctl.usp_seal_analytic_manifest_compositions @run_id UNIQUEIDENTIFIER,
 @rows ctl.analytic_manifest_composition_batch READONLY
AS BEGIN
 SET NOCOUNT ON; SET XACT_ABORT OFF;
 IF @@TRANCOUNT=0 THROW 53501,N'ANA_TRANSACTION_REQUIRED',1;
 SAVE TRANSACTION ana_composition_batch;
 BEGIN TRY
  EXEC ctl.usp_analytic_lab_lock @run_id,'PLAN';
  IF (SELECT COUNT_BIG(*) FROM @rows) NOT BETWEEN 1 AND 64
  OR EXISTS(SELECT 1 FROM @rows WHERE revision NOT BETWEEN 1 AND 100000 OR expected_direct_freights NOT BETWEEN 0 AND 100000)
  THROW 53810,N'ANA_COMPOSITION_BATCH_BOUND',1;
  IF EXISTS(SELECT 1 FROM @rows i JOIN ctl.analytic_manifest_composition c
   ON c.run_id=@run_id AND c.manifest_key=i.manifest_key AND c.revision=i.revision
   WHERE c.manifest_execution<>i.manifest_execution OR c.expected_direct_freights<>i.expected_direct_freights)
  THROW 53649,N'ANA_COMPOSITION_REVISION_CONFLICT',1;
  IF EXISTS(SELECT 1 FROM @rows i WHERE NOT EXISTS(
   SELECT 1 FROM ctl.analytic_lab_source_group g JOIN ctl.relational_lab_capture c ON c.run_id=g.relational_run
   JOIN stg.manifesto_observation o ON o.execution_id=c.execution_id
   WHERE g.run_id=@run_id AND c.execution_id=i.manifest_execution AND c.entity_name=N'manifestos'
   AND o.source_key COLLATE Latin1_General_100_BIN2=i.manifest_key)
   OR i.expected_direct_freights<>(SELECT COUNT_BIG(*) FROM core.analytic_lab_freight_relation_current b
    WHERE b.run_id=@run_id AND b.kind='DIRECT' AND b.origin_key=i.manifest_key AND b.active=1)
   OR EXISTS(SELECT 1 FROM core.analytic_lab_freight_relation_current b
    WHERE b.run_id=@run_id AND b.kind='DIRECT' AND b.origin_key=i.manifest_key AND b.revision>i.revision))
  THROW 53646,N'ANA_MANIFEST_COMPOSITION_COUNT_SCOPE',1;
  INSERT ctl.analytic_manifest_composition(run_id,manifest_key,manifest_execution,revision,expected_direct_freights,complete,evidence)
  SELECT @run_id,i.manifest_key,i.manifest_execution,i.revision,i.expected_direct_freights,1,'synthetic-manifest-composition-v1'
  FROM @rows i WHERE NOT EXISTS(SELECT 1 FROM ctl.analytic_manifest_composition c
   WHERE c.run_id=@run_id AND c.manifest_key=i.manifest_key AND c.revision=i.revision);
  SELECT COUNT_BIG(*) FROM @rows;
 END TRY
 BEGIN CATCH
  IF XACT_STATE()=1 ROLLBACK TRANSACTION ana_composition_batch;
  THROW;
 END CATCH;
END;
GO
