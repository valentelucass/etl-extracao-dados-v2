-- ADR0048 / REL-LAB-08: absence of captures is not a completed empty capture.
SET XACT_ABORT ON;
GO
CREATE OR ALTER PROCEDURE ctl.usp_finish_relational_lab_attempt
    @run_id UNIQUEIDENTIFIER,@backlog_id BIGINT,@owner_id UNIQUEIDENTIFIER,@result NVARCHAR(24),
    @execution_id UNIQUEIDENTIFIER,@now DATETIME2(7)
AS
BEGIN
    SET NOCOUNT ON;
    EXEC ctl.usp_relational_lab_lock @run_id;
    IF @result IS NULL OR @result NOT IN(N'RESOLVED',N'TEMPORARY_FAILURE',N'CONTRACT_FAILURE',N'CONFLICT',N'ABANDONED')
        THROW 53231,N'REL_LAB_ATTEMPT_RESULT',1;
    IF NOT EXISTS(SELECT 1 FROM ctl.relational_lab_backlog WHERE run_id=@run_id AND backlog_id=@backlog_id
        AND state=N'CLAIMED' AND owner_id=@owner_id AND lease_until>@now)
        THROW 53232,N'REL_LAB_CLAIM_OWNER_OR_LEASE',1;
    IF @result=N'RESOLVED' AND NOT EXISTS(SELECT 1 FROM ctl.relational_lab_backlog
        WHERE backlog_id=@backlog_id AND cause=N'RESOLVED') THROW 53233,N'REL_LAB_RESOLUTION_RECEIPT_REQUIRED',1;
    IF @result=N'RESOLVED' AND NOT EXISTS(SELECT 1 FROM ctl.relational_lab_backlog backlog
        JOIN stg.relational_lab_binding binding ON binding.binding_id=backlog.binding_id
        JOIN ctl.relational_lab_capture captured ON captured.execution_id=@execution_id AND captured.run_id=@run_id
            AND captured.business_date=binding.target_date
            AND captured.entity_name=CASE binding.relation_kind WHEN 'MC' THEN N'coletas' ELSE N'fretes' END
        JOIN stg.relational_lab_root observed ON observed.execution_id=captured.execution_id AND observed.source_key=binding.target_key
        WHERE backlog.run_id=@run_id AND backlog.backlog_id=@backlog_id)
        THROW 53234,N'REL_LAB_ATTEMPT_TARGET_CAPTURE_REQUIRED',1;
    IF @execution_id IS NOT NULL AND NOT EXISTS(SELECT 1 FROM ctl.relational_lab_capture
        WHERE execution_id=@execution_id AND run_id=@run_id) THROW 53234,N'REL_LAB_ATTEMPT_EXECUTION',1;
    UPDATE a SET finished_at=@now,result=@result,execution_id=@execution_id FROM ctl.relational_lab_attempt a
        JOIN ctl.relational_lab_backlog b ON b.backlog_id=a.backlog_id AND b.attempts=a.attempt_number
        WHERE b.backlog_id=@backlog_id AND a.owner_id=@owner_id;
    UPDATE b SET state=CASE WHEN @result=N'RESOLVED' THEN N'RESOLVED'
            WHEN @result IN(N'CONTRACT_FAILURE',N'CONFLICT') OR attempts>=r.maximum_attempts THEN N'QUARANTINED' ELSE N'DEFERRED' END,
        cause=@result,owner_id=NULL,lease_until=NULL,eligible_at=DATEADD(SECOND,r.retry_seconds,@now),last_execution_id=@execution_id
    FROM ctl.relational_lab_backlog b JOIN ctl.relational_lab_run r ON r.run_id=b.run_id WHERE b.backlog_id=@backlog_id;
END;
GO
CREATE OR ALTER PROCEDURE recon.usp_relational_lab_status @run_id UNIQUEIDENTIFIER,@now DATETIME2(7)
AS
BEGIN
    SET NOCOUNT ON;
    IF NOT EXISTS(SELECT 1 FROM ctl.relational_lab_run WHERE run_id=@run_id) THROW 53220,N'REL_LAB_RUN_REQUIRED',1;
    SELECT
        (SELECT COUNT_BIG(*) FROM core.relational_lab_root WHERE run_id=@run_id AND entity_name=N'manifestos') manifestos,
        (SELECT COUNT_BIG(*) FROM core.relational_lab_root WHERE run_id=@run_id AND entity_name=N'coletas') coletas,
        (SELECT COUNT_BIG(*) FROM core.relational_lab_root WHERE run_id=@run_id AND entity_name=N'fretes') fretes,
        (SELECT COUNT_BIG(*) FROM core.relational_lab_link WHERE run_id=@run_id AND active=1 AND relation_kind='MC') manifesto_coletas,
        (SELECT COUNT_BIG(*) FROM core.relational_lab_link WHERE run_id=@run_id AND active=1 AND relation_kind='CF') coleta_fretes,
        (SELECT COUNT_BIG(*) FROM ctl.relational_lab_backlog WHERE run_id=@run_id AND state IN(N'PENDING',N'CLAIMED',N'DEFERRED')) pending,
        (SELECT COUNT_BIG(*) FROM ctl.relational_lab_backlog WHERE run_id=@run_id AND state=N'QUARANTINED') quarantined,
        (SELECT COALESCE(DATEDIFF_BIG(SECOND,MIN(first_seen),@now),0) FROM ctl.relational_lab_backlog
            WHERE run_id=@run_id AND state IN(N'PENDING',N'CLAIMED',N'DEFERRED')) oldest_pending_seconds,
        (SELECT COUNT_BIG(*) FROM recon.vw_relational_lab_components c WHERE c.run_id=@run_id AND c.component_kind IN(N'PICK',N'ITEM')
            AND c.entity_name IN(N'manifestos',N'coletas') AND (c.validity<>N'VALID' OR NOT EXISTS
                (SELECT 1 FROM stg.relational_lab_binding b WHERE b.run_id=c.run_id
                    AND b.relation_kind=CASE c.entity_name WHEN N'manifestos' THEN 'MC' ELSE 'CF' END
                    AND b.origin_key=c.source_key AND b.origin_component=c.component_key))) unbound_candidates,
        (SELECT COUNT_BIG(*) FROM ctl.relational_lab_capture WHERE run_id=@run_id) captures,
        (SELECT COALESCE(SUM(physical_rows),0) FROM ctl.relational_lab_capture WHERE run_id=@run_id) physical_rows,
        (SELECT COUNT_BIG(*) FROM recon.relational_lab_receipt WHERE run_id=@run_id) receipts,
        (SELECT COUNT(DISTINCT entity_name) FROM ctl.relational_lab_capture WHERE run_id=@run_id) captured_entities;
END;
GO

CREATE OR ALTER PROCEDURE recon.usp_complete_relational_lab_partition
    @run_id UNIQUEIDENTIFIER,@mode NVARCHAR(16),@ordinal INT,@receipt_id UNIQUEIDENTIFIER
AS
BEGIN
    SET NOCOUNT ON;
    EXEC ctl.usp_relational_lab_lock @run_id;
    IF NOT EXISTS(SELECT 1 FROM recon.relational_lab_partition WHERE run_id=@run_id AND execution_mode=@mode AND ordinal=@ordinal
        AND manifesto_execution IS NOT NULL AND coleta_execution IS NOT NULL AND frete_execution IS NOT NULL)
        OR NOT EXISTS(SELECT 1 FROM recon.relational_lab_receipt WHERE run_id=@run_id AND receipt_id=@receipt_id
            AND operation=N'RESOLVE' AND orphaned=0 AND blocked=0)
        THROW 53246,N'REL_LAB_PARTITION_NOT_RECONCILED',1;
    IF EXISTS(SELECT MIN(binding_id) FROM stg.relational_lab_binding WHERE run_id=@run_id
        GROUP BY relation_kind,origin_key,origin_component,target_key,target_component,target_date,revision,cardinality
        EXCEPT SELECT binding_id FROM recon.relational_lab_resolution WHERE receipt_id=@receipt_id)
        OR EXISTS(SELECT 1 FROM ctl.relational_lab_backlog WHERE run_id=@run_id AND state<>N'RESOLVED')
        OR EXISTS(SELECT 1 FROM recon.vw_relational_lab_components c WHERE c.run_id=@run_id
            AND c.component_kind IN(N'PICK',N'ITEM') AND c.entity_name IN(N'manifestos',N'coletas')
            AND (c.validity<>N'VALID' OR NOT EXISTS(SELECT 1 FROM stg.relational_lab_binding b
                WHERE b.run_id=c.run_id AND b.relation_kind=CASE c.entity_name WHEN N'manifestos' THEN 'MC' ELSE 'CF' END
                    AND b.origin_key=c.source_key AND b.origin_component=c.component_key)))
        THROW 53253,N'REL_LAB_RECONCILIATION_INCOMPLETE',1;
    UPDATE recon.relational_lab_partition SET completion_receipt=@receipt_id
        WHERE run_id=@run_id AND execution_mode=@mode AND ordinal=@ordinal AND completion_receipt IS NULL;
END;
GO
