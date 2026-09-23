-- ANA-12: explicit analytic extension alongside the immutable baseline contract; every original completeness fence remains.
SET ANSI_NULLS ON; SET QUOTED_IDENTIFIER ON;
GO
CREATE TABLE ctl.analytic_lab_freight_contract (
 run_id UNIQUEIDENTIFIER NOT NULL PRIMARY KEY REFERENCES ctl.analytic_lab_source_group(run_id),
 contract_version VARCHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL,
 contract_fingerprint CHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL,
 evidence VARCHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL,
 CONSTRAINT CK_analytic_freight_contract CHECK(contract_version='analytic-freight-performance-v1'
 AND LEN(contract_fingerprint)=64 AND contract_fingerprint NOT LIKE '%[^0-9a-f]%' AND evidence='synthetic-freight-contract-v1')
);
GO
CREATE TRIGGER ctl.trg_analytic_freight_contract ON ctl.analytic_lab_freight_contract AFTER UPDATE,DELETE
AS BEGIN SET NOCOUNT ON; IF EXISTS(SELECT 1 FROM deleted) THROW 53586,N'ANA_FREIGHT_CONTRACT_IMMUTABLE',1; END;
GO
ALTER PROCEDURE ctl.usp_assert_expansion_capture_scope @run_id UNIQUEIDENTIFIER,@execution_id UNIQUEIDENTIFIER,@entity VARCHAR(8)
AS
BEGIN
 SET NOCOUNT ON;
 IF @@TRANCOUNT=0 THROW 53440,N'EXP_TRANSACTION_REQUIRED',1;
 IF NOT EXISTS(SELECT 1 FROM ctl.expansion_lab_run r JOIN ctl.expansion_lab_contract expected ON expected.run_id=r.run_id AND expected.entity=@entity
 JOIN ctl.execution_attempt e ON e.execution_id=@execution_id AND
 ((e.contract_version=expected.contract_version AND e.contract_fingerprint COLLATE Latin1_General_100_BIN2=expected.contract_fingerprint)
 OR(@entity='FRETE' AND EXISTS(SELECT 1 FROM ctl.analytic_lab_freight_contract extension
 JOIN ctl.analytic_lab_source_group declared ON declared.run_id=extension.run_id AND declared.expansion_run=r.run_id
 WHERE extension.contract_version=e.contract_version AND extension.contract_fingerprint=e.contract_fingerprint COLLATE Latin1_General_100_BIN2)))
 JOIN ctl.execution_partition p ON p.partition_id=e.partition_id
 JOIN ctl.execution_source_protocol source ON source.execution_id=e.execution_id AND source.source_kind=N'DATA_EXPORT'
 JOIN ctl.execution_audit a ON a.execution_id=e.execution_id
 WHERE r.run_id=@run_id AND p.environment_name=N'LOCAL_SHADOW' AND p.source_instance=r.source_instance AND p.tenant_scope=r.tenant_scope
 AND p.entity_name=CASE @entity WHEN 'FRETE' THEN N'fretes' WHEN 'LOC' THEN N'localizacao_cargas' ELSE CONVERT(NVARCHAR(8),@entity) END COLLATE Latin1_General_100_BIN2
 AND p.execution_mode IN(N'BOOTSTRAP',N'INCREMENTAL',N'BACKFILL',N'REPLAY')
 AND e.current_state IN(N'EXTRACTING',N'STAGED',N'DEGRADED')
 AND a.status=N'COMPLETED' AND a.business_window_start=a.business_window_end
 AND a.business_window_start>=r.window_start AND a.business_window_start<r.window_end_exclusive
 AND p.partition_start_utc=CONVERT(DATETIME2(3),CONVERT(DATETIME2,a.business_window_start) AT TIME ZONE 'E. South America Standard Time' AT TIME ZONE 'UTC')
 AND p.partition_end_exclusive_utc=CONVERT(DATETIME2(3),CONVERT(DATETIME2,DATEADD(day,1,a.business_window_start)) AT TIME ZONE 'E. South America Standard Time' AT TIME ZONE 'UTC')
 AND a.template_id=CASE @entity WHEN 'CAP' THEN 8636 WHEN 'FAT' THEN 4924 WHEN 'INV' THEN 10633 WHEN 'SIN' THEN 6392 WHEN 'FRETE' THEN 6389 ELSE 8656 END
 AND a.pages_fetched=a.terminal_page AND a.pages_fetched<=r.maximum_pages AND a.records_delivered<=r.maximum_rows
 AND a.pages_fetched=(SELECT COUNT_BIG(*) FROM ctl.page_audit WHERE execution_id=@execution_id)
 AND a.records_delivered=(SELECT COALESCE(SUM(CONVERT(BIGINT,record_count)),0) FROM ctl.page_audit WHERE execution_id=@execution_id)
 AND EXISTS(SELECT 1 FROM ctl.page_audit WHERE execution_id=@execution_id AND page_number=a.terminal_page AND record_count=0 AND is_terminal=1)
 AND ((p.execution_mode<>N'REPLAY' AND e.replay_of_execution_id IS NULL) OR(p.execution_mode=N'REPLAY' AND
 (EXISTS(SELECT 1 FROM ctl.expansion_lab_capture prior WHERE prior.run_id=r.run_id AND prior.execution_id=e.replay_of_execution_id AND prior.vertical=@entity AND prior.state='COMPLETE')
 OR EXISTS(SELECT 1 FROM ctl.expansion_lab_dependency_capture prior WHERE prior.run_id=r.run_id AND prior.execution_id=e.replay_of_execution_id AND prior.entity=@entity AND prior.state='COMPLETE')))))
 THROW 53441,N'EXP_CAPTURE_SCOPE_OR_COMPLETENESS',1;
END;
GO
