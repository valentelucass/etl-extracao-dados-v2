-- Explicit laboratory counterpart of the existing runtime tariff scope binding.
SET ANSI_NULLS ON;SET QUOTED_IDENTIFIER ON;
GO
CREATE TABLE ctl.analytic_quote_runtime_reference (
 execution_id UNIQUEIDENTIFIER NOT NULL CONSTRAINT PK_ana_quote_runtime_ref PRIMARY KEY REFERENCES ctl.analytic_quote_capture(execution_id),
 run_id UNIQUEIDENTIFIER NOT NULL REFERENCES ctl.analytic_lab_run(run_id),revision INT NOT NULL,
 reference_release_id BIGINT NOT NULL REFERENCES ref.reference_release(reference_release_id),
 reference_fingerprint CHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL,
 CONSTRAINT CK_ana_quote_runtime_ref CHECK(revision BETWEEN 1 AND 100000)
);
GO
CREATE TRIGGER ctl.trg_analytic_quote_runtime_reference ON ctl.analytic_quote_runtime_reference AFTER UPDATE,DELETE AS
BEGIN SET NOCOUNT ON;THROW 53720,N'ANA_QUOTE_RUNTIME_REFERENCE_IMMUTABLE',1;END;
GO
CREATE PROCEDURE ctl.usp_bind_analytic_quote_runtime_reference @run_id UNIQUEIDENTIFIER,@execution_id UNIQUEIDENTIFIER,@revision INT,@release BIGINT AS
BEGIN
 SET NOCOUNT ON;SET XACT_ABORT OFF;
 IF @@TRANCOUNT=0 OR NOT EXISTS(SELECT 1 FROM ctl.analytic_quote_capture c
 JOIN ctl.execution_attempt e ON e.execution_id=c.execution_id JOIN ctl.execution_partition p ON p.partition_id=e.partition_id
 CROSS APPLY ref.ufn_analytic_quote_tariff(c.run_id,@revision,CONVERT(DATE,p.partition_start_utc)) selection
 WHERE c.run_id=@run_id AND c.execution_id=@execution_id AND c.state='CAPTURING' AND e.current_state=N'EXTRACTING'
 AND p.environment_name=N'LOCAL_SHADOW' AND p.source_instance=N'LOCAL_V2' AND p.tenant_scope=N'LOCAL_V2' AND p.entity_name=N'cotacoes'
 AND selection.reference_release_id=@release) THROW 53721,N'ANA_QUOTE_RUNTIME_REFERENCE_SCOPE',1;
 IF EXISTS(SELECT 1 FROM ctl.analytic_quote_runtime_reference WHERE execution_id=@execution_id
 AND(run_id<>@run_id OR revision<>@revision OR reference_release_id<>@release))
 THROW 53722,N'ANA_QUOTE_RUNTIME_REFERENCE_DIVERGENT',1;
 INSERT ctl.analytic_quote_runtime_reference
 SELECT @execution_id,@run_id,@revision,@release,r.source_fingerprint FROM ref.reference_release r
 WHERE r.reference_release_id=@release AND NOT EXISTS(SELECT 1 FROM ctl.analytic_quote_runtime_reference WHERE execution_id=@execution_id);
END;
GO
ALTER FUNCTION ctl.fn_runtime_tariff_valid(@execution_id UNIQUEIDENTIFIER,@reference_release_id BIGINT)
RETURNS BIT AS
BEGIN
 IF @reference_release_id IS NULL OR @reference_release_id<1 RETURN 0;
 IF NOT EXISTS(SELECT 1 FROM ref.reference_release r
   JOIN ref.reference_release_ratification rat ON rat.reference_release_id=r.reference_release_id AND rat.activation_scope=N'SHADOW'
   WHERE r.reference_release_id=@reference_release_id AND r.family_code=N'QUOTE_TARIFF'
   AND(r.scope_code=N'LOCAL_SHADOW/LOCAL_V2/LOCAL_V2/cotacoes' OR EXISTS(
    SELECT 1 FROM ctl.analytic_quote_runtime_reference local_binding
    JOIN ctl.analytic_quote_capture capture ON capture.execution_id=local_binding.execution_id AND capture.run_id=local_binding.run_id
    JOIN ctl.execution_attempt attempt ON attempt.execution_id=capture.execution_id
    JOIN ctl.execution_partition partition ON partition.partition_id=attempt.partition_id
    CROSS APPLY ref.ufn_analytic_quote_tariff(local_binding.run_id,local_binding.revision,CONVERT(DATE,partition.partition_start_utc)) selection
    WHERE local_binding.execution_id=@execution_id AND local_binding.reference_release_id=r.reference_release_id
    AND selection.reference_release_id=r.reference_release_id AND local_binding.reference_fingerprint=r.source_fingerprint
    AND r.source_kind=N'DETERMINISTIC_LOCAL' AND r.scope_code=CONCAT(N'SYNTHETIC_QUOTE_LAB:',CONVERT(NVARCHAR(36),local_binding.run_id),N':R',local_binding.revision) COLLATE Latin1_General_100_BIN2
    AND partition.environment_name=N'LOCAL_SHADOW' AND partition.source_instance=N'LOCAL_V2' AND partition.tenant_scope=N'LOCAL_V2' AND partition.entity_name=N'cotacoes'))
   AND NOT EXISTS(SELECT 1 FROM ref.reference_release_revocation rev
     WHERE rev.reference_release_id=r.reference_release_id AND rev.activation_scope=N'SHADOW')) RETURN 0;
 IF EXISTS(SELECT 1 FROM ctl.runtime_cotacao_reference b JOIN ref.reference_release r ON r.reference_release_id=b.reference_release_id
   WHERE b.execution_id=@execution_id AND(b.reference_release_id<>@reference_release_id OR b.reference_fingerprint<>r.source_fingerprint)) RETURN 0;
 IF IS_SRVROLEMEMBER(N'sysadmin')=1 OR IS_MEMBER(N'db_owner')=1 RETURN 1;
 DECLARE @actual NVARCHAR(MAX)=CONCAT(N'{"execution":"',LOWER(CONVERT(NVARCHAR(36),@execution_id)),
   N'","entity":"cotacoes","workload":"cotacoes","mode":"BACKFILL","referenceReleaseId":"',
   CONVERT(NVARCHAR(20),@reference_release_id),N'"}');
 RETURN ctl.fn_runtime_consumed_scope(@actual,0,NULL);
END;
GO
