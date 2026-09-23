-- ADR0048: explicit evidence, cardinality, set-based links and persisted hydration backlog.
SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;
SET XACT_ABORT ON;
GO
CREATE VIEW recon.vw_relational_lab_components AS
SELECT DISTINCT c.run_id,c.entity_name,s.source_key,s.component_kind,s.presence,s.component_key,s.validity
FROM stg.relational_lab_component s JOIN ctl.relational_lab_capture c ON c.execution_id=s.execution_id;
GO
CREATE PROCEDURE core.usp_resolve_relational_laboratory
    @run_id UNIQUEIDENTIFIER,@receipt_id UNIQUEIDENTIFIER,@now DATETIME2(7)
AS
BEGIN
    SET NOCOUNT ON;
    EXEC ctl.usp_relational_lab_lock @run_id;
    IF NOT EXISTS(SELECT 1 FROM ctl.relational_lab_run WHERE run_id=@run_id)
        THROW 53220,N'REL_LAB_RUN_REQUIRED',1;
    IF EXISTS(SELECT 1 FROM recon.relational_lab_receipt WHERE receipt_id=@receipt_id)
    BEGIN
        IF NOT EXISTS(SELECT 1 FROM recon.relational_lab_receipt WHERE receipt_id=@receipt_id AND run_id=@run_id AND operation=N'RESOLVE')
            THROW 53221,N'REL_LAB_RECEIPT_CONFLICT',1;
        SELECT considered,inserted,updated,noop,resolved,orphaned,blocked
            FROM recon.relational_lab_receipt WHERE receipt_id=@receipt_id;
        RETURN;
    END;
    -- MIN(binding_id) points to an equivalent evidence row, never selects a different identity.
    SELECT MIN(binding_id) binding_id,relation_kind,origin_key,origin_component,target_key,target_component,target_date,revision,cardinality,
        DENSE_RANK() OVER(PARTITION BY relation_kind,origin_key,origin_component ORDER BY revision DESC) revision_rank
    INTO #bindings FROM stg.relational_lab_binding WHERE run_id=@run_id
    GROUP BY relation_kind,origin_key,origin_component,target_key,target_component,target_date,revision,cardinality;
    CREATE INDEX IX_bindings_origin ON #bindings(relation_kind,origin_key,origin_component,revision_rank);
    SELECT b.*,CONVERT(NVARCHAR(32),CASE
        WHEN revision_rank<>1 THEN N'SUPERSEDED'
        WHEN EXISTS(SELECT 1 FROM #bindings x WHERE x.revision_rank=1 AND x.relation_kind=b.relation_kind
            AND x.origin_key=b.origin_key AND x.origin_component=b.origin_component AND x.cardinality<>b.cardinality) THEN N'CONFLICT'
        WHEN b.cardinality=N'ONE_TO_ONE' AND EXISTS(SELECT 1 FROM #bindings x WHERE x.revision_rank=1 AND x.relation_kind=b.relation_kind
            AND x.origin_key=b.origin_key AND x.origin_component=b.origin_component
            AND (x.target_key<>b.target_key OR x.target_component<>b.target_component OR x.target_date<>b.target_date)) THEN N'CONFLICT'
        WHEN b.cardinality<>N'MANY_TO_MANY' AND EXISTS(SELECT 1 FROM #bindings x WHERE x.revision_rank=1 AND x.relation_kind=b.relation_kind
            AND x.target_key=b.target_key AND x.target_component=b.target_component
            AND (x.origin_key<>b.origin_key OR x.origin_component<>b.origin_component)) THEN N'CONFLICT'
        WHEN NOT EXISTS(SELECT 1 FROM core.relational_lab_root r WHERE r.run_id=@run_id
            AND r.entity_name=CASE b.relation_kind WHEN 'MC' THEN N'manifestos' ELSE N'coletas' END AND r.source_key=b.origin_key) THEN N'ORIGIN_ABSENT'
        WHEN NOT EXISTS(SELECT 1 FROM recon.vw_relational_lab_components c WHERE c.run_id=@run_id
            AND c.entity_name=CASE b.relation_kind WHEN 'MC' THEN N'manifestos' ELSE N'coletas' END AND c.source_key=b.origin_key
            AND c.component_kind=CASE b.relation_kind WHEN 'MC' THEN N'PICK' ELSE N'ITEM' END
            AND c.component_key=b.origin_component AND c.validity=N'VALID') THEN N'COMPONENT_ABSENT'
        WHEN NOT EXISTS(SELECT 1 FROM core.relational_lab_root r WHERE r.run_id=@run_id
            AND r.entity_name=CASE b.relation_kind WHEN 'MC' THEN N'coletas' ELSE N'fretes' END AND r.source_key=b.target_key) THEN N'ORPHAN'
        WHEN b.relation_kind='MC' AND b.target_component<>N'ROOT' THEN N'CONFLICT'
        WHEN b.relation_kind='CF' AND NOT EXISTS(SELECT 1 FROM recon.vw_relational_lab_components c WHERE c.run_id=@run_id
            AND c.entity_name=N'fretes' AND c.source_key=b.target_key AND c.component_kind=N'ITEM'
            AND c.component_key=b.target_component AND c.validity=N'VALID') THEN N'COMPONENT_ABSENT'
        ELSE N'RESOLVED' END) disposition
    INTO #decisions FROM #bindings b;
    UPDATE d SET disposition=N'CHAIN_BLOCKED' FROM #decisions d WHERE relation_kind='CF' AND disposition=N'RESOLVED'
        AND NOT EXISTS(SELECT 1 FROM #decisions m WHERE m.relation_kind='MC' AND m.target_key=d.origin_key AND m.disposition=N'RESOLVED');
    DECLARE @inserted BIGINT=0,@updated BIGINT=0,@noop BIGINT=0;
    SELECT @inserted=COUNT_BIG(*) FROM #decisions d WHERE disposition=N'RESOLVED'
        AND NOT EXISTS(SELECT 1 FROM core.relational_lab_link l WHERE l.run_id=@run_id AND l.relation_kind=d.relation_kind
            AND l.origin_key=d.origin_key AND l.origin_component=d.origin_component AND l.target_key=d.target_key
            AND l.target_component=d.target_component AND l.revision=d.revision);
    SELECT @updated=COUNT_BIG(*) FROM core.relational_lab_link l WHERE l.run_id=@run_id AND l.active=1
        AND EXISTS(SELECT 1 FROM #decisions d WHERE d.relation_kind=l.relation_kind AND d.origin_key=l.origin_key
            AND d.origin_component=l.origin_component AND d.revision>l.revision);
    SELECT @noop=COUNT_BIG(*)-@inserted FROM #decisions WHERE disposition=N'RESOLVED';
    INSERT recon.relational_lab_receipt
    SELECT @receipt_id,@run_id,NULL,N'RESOLVE',COUNT_BIG(*),@inserted,@updated,@noop,
        COALESCE(SUM(CONVERT(BIGINT,CASE WHEN disposition=N'RESOLVED' THEN 1 ELSE 0 END)),0),
        COALESCE(SUM(CONVERT(BIGINT,CASE WHEN disposition=N'ORPHAN' THEN 1 ELSE 0 END)),0),
        COALESCE(SUM(CONVERT(BIGINT,CASE WHEN disposition NOT IN(N'RESOLVED',N'ORPHAN',N'SUPERSEDED') THEN 1 ELSE 0 END)),0),@now
    FROM #decisions;
    INSERT recon.relational_lab_resolution SELECT @receipt_id,binding_id,disposition FROM #decisions;
    UPDATE l SET active=0,receipt_id=@receipt_id FROM core.relational_lab_link l WHERE l.run_id=@run_id AND l.active=1
        AND EXISTS(SELECT 1 FROM #decisions d WHERE d.relation_kind=l.relation_kind AND d.origin_key=l.origin_key
            AND d.origin_component=l.origin_component AND (d.revision>l.revision OR (d.revision=l.revision AND d.disposition<>N'RESOLVED')));
    INSERT core.relational_lab_link(run_id,relation_kind,origin_key,origin_component,target_key,target_component,revision,active,binding_id,receipt_id)
    SELECT @run_id,relation_kind,origin_key,origin_component,target_key,target_component,revision,1,binding_id,@receipt_id
    FROM #decisions d WHERE disposition=N'RESOLVED'
        AND NOT EXISTS(SELECT 1 FROM core.relational_lab_link l WHERE l.run_id=@run_id AND l.relation_kind=d.relation_kind
            AND l.origin_key=d.origin_key AND l.origin_component=d.origin_component AND l.target_key=d.target_key
            AND l.target_component=d.target_component AND l.revision=d.revision);
    UPDATE l SET active=1,receipt_id=@receipt_id FROM core.relational_lab_link l JOIN #decisions d
        ON l.binding_id=d.binding_id WHERE l.run_id=@run_id AND l.active=0 AND d.disposition=N'RESOLVED';
    INSERT ctl.relational_lab_backlog(run_id,binding_id,state,cause,first_seen,last_seen,attempts,eligible_at,last_receipt_id)
    SELECT @run_id,binding_id,CASE WHEN disposition=N'ORPHAN' THEN N'PENDING' ELSE N'QUARANTINED' END,
        disposition,@now,@now,0,@now,@receipt_id FROM #decisions d
    WHERE disposition NOT IN(N'RESOLVED',N'SUPERSEDED')
        AND NOT EXISTS(SELECT 1 FROM ctl.relational_lab_backlog b WHERE b.run_id=@run_id AND b.binding_id=d.binding_id);
    UPDATE b SET cause=d.disposition,last_seen=@now,last_receipt_id=@receipt_id,
        state=CASE WHEN b.state=N'CLAIMED' THEN N'CLAIMED'
            WHEN d.disposition IN(N'RESOLVED',N'SUPERSEDED') THEN N'RESOLVED'
            WHEN d.disposition<>N'ORPHAN' THEN N'QUARANTINED'
            WHEN b.state=N'RESOLVED' AND b.attempts<r.maximum_attempts THEN N'PENDING' ELSE b.state END
    FROM ctl.relational_lab_backlog b JOIN #decisions d ON d.binding_id=b.binding_id
        JOIN ctl.relational_lab_run r ON r.run_id=b.run_id WHERE b.run_id=@run_id;
    SELECT considered,inserted,updated,noop,resolved,orphaned,blocked FROM recon.relational_lab_receipt WHERE receipt_id=@receipt_id;
END;
GO
CREATE PROCEDURE ctl.usp_claim_relational_lab_backlog
    @run_id UNIQUEIDENTIFIER,@owner_id UNIQUEIDENTIFIER,@limit INT,@now DATETIME2(7)
AS
BEGIN
    SET NOCOUNT ON;
    EXEC ctl.usp_relational_lab_lock @run_id;
    DECLARE @maximum INT,@attempts INT,@lease INT;
    SELECT @maximum=maximum_claim,@attempts=maximum_attempts,@lease=lease_seconds FROM ctl.relational_lab_run WHERE run_id=@run_id;
    IF @maximum IS NULL OR @limit IS NULL OR @limit NOT BETWEEN 1 AND @maximum OR @owner_id IS NULL
        THROW 53230,N'REL_LAB_CLAIM_BOUND',1;
    UPDATE a SET finished_at=@now,result=N'LEASE_EXPIRED' FROM ctl.relational_lab_attempt a
        JOIN ctl.relational_lab_backlog b ON b.backlog_id=a.backlog_id AND b.attempts=a.attempt_number
        WHERE b.run_id=@run_id AND b.state=N'CLAIMED' AND b.lease_until<=@now AND a.result=N'CLAIMED';
    UPDATE ctl.relational_lab_backlog SET state=CASE WHEN attempts>=@attempts THEN N'QUARANTINED' ELSE N'PENDING' END,
        cause=N'LEASE_EXPIRED',owner_id=NULL,lease_until=NULL,eligible_at=@now
        WHERE run_id=@run_id AND state=N'CLAIMED' AND lease_until<=@now;
    DECLARE @claimed TABLE(backlog_id BIGINT PRIMARY KEY,binding_id BIGINT,attempt_number INT);
    ;WITH bounded AS(SELECT TOP(@limit) * FROM ctl.relational_lab_backlog WITH(UPDLOCK,READPAST,ROWLOCK)
        WHERE run_id=@run_id AND state IN(N'PENDING',N'DEFERRED') AND eligible_at<=@now AND attempts<@attempts
        ORDER BY eligible_at,backlog_id)
    UPDATE bounded SET state=N'CLAIMED',owner_id=@owner_id,lease_until=DATEADD(SECOND,@lease,@now),attempts=attempts+1
        OUTPUT inserted.backlog_id,inserted.binding_id,inserted.attempts INTO @claimed;
    INSERT ctl.relational_lab_attempt(backlog_id,attempt_number,owner_id,started_at,lease_until,result)
        SELECT backlog_id,attempt_number,@owner_id,@now,DATEADD(SECOND,@lease,@now),N'CLAIMED' FROM @claimed;
    SELECT c.backlog_id,c.attempt_number,b.relation_kind,b.target_key,b.target_component,b.target_date,
        DATEADD(SECOND,@lease,@now) lease_until FROM @claimed c JOIN stg.relational_lab_binding b ON b.binding_id=c.binding_id
        ORDER BY c.backlog_id;
END;
GO
CREATE PROCEDURE ctl.usp_finish_relational_lab_attempt
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
CREATE PROCEDURE recon.usp_relational_lab_status @run_id UNIQUEIDENTIFIER,@now DATETIME2(7)
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
        (SELECT COUNT_BIG(*) FROM recon.relational_lab_receipt WHERE run_id=@run_id) receipts;
END;
GO
