SET ANSI_NULLS ON; SET QUOTED_IDENTIFIER ON;
IF DB_NAME()<>N'ETL_SISTEMA_V2_SHADOW' THROW 53930,N'P03_EXACT_SHADOW_REQUIRED',1;
GO
-- ADR0052 / SEQ-MC-01: explicit complete sets per included origin, never window absence.
CREATE TABLE stg.relational_lab_mc_set (
 run_id UNIQUEIDENTIFIER NOT NULL CONSTRAINT FK_relational_mc_set_run REFERENCES ctl.relational_lab_run(run_id),
 origin_key NVARCHAR(128) COLLATE Latin1_General_100_BIN2 NOT NULL,
 revision INT NOT NULL, declared_at DATETIME2(7) NOT NULL,
 CONSTRAINT PK_relational_lab_mc_set PRIMARY KEY(run_id,origin_key,revision),
 CONSTRAINT CK_relational_lab_mc_set CHECK(revision BETWEEN 1 AND 1000 AND origin_key LIKE N'INTEGER:%')
);
GO
CREATE PROCEDURE stg.usp_declare_relational_mc_sets
 @run_id UNIQUEIDENTIFIER,@revision INT,@now DATETIME2(7)
AS
BEGIN
 SET NOCOUNT ON;
 EXEC ctl.usp_relational_lab_lock @run_id;
 IF @revision NOT BETWEEN 1 AND 1000 OR @revision IS NULL OR @now IS NULL
 OR NOT EXISTS(SELECT 1 FROM stg.relational_lab_binding WHERE run_id=@run_id AND relation_kind='MC' AND revision=@revision)
 THROW 53931,N'MC_EXPLICIT_NONEMPTY_SET_REQUIRED',1;
 -- Caller explicitly attests completeness for each included origin after all batches.
 -- Other origins and ordinary bind/capture calls do not acquire this assertion.
 INSERT stg.relational_lab_mc_set(run_id,origin_key,revision,declared_at)
 SELECT DISTINCT @run_id,b.origin_key,@revision,@now FROM stg.relational_lab_binding b
 WHERE b.run_id=@run_id AND b.relation_kind='MC' AND b.revision=@revision
 AND NOT EXISTS(SELECT 1 FROM stg.relational_lab_mc_set s WHERE s.run_id=b.run_id AND s.origin_key=b.origin_key AND s.revision=@revision);
END;
GO
CREATE OR ALTER PROCEDURE core.usp_resolve_relational_laboratory
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
        CASE WHEN relation_kind='MC' AND revision<COALESCE((SELECT MAX(s.revision)
            FROM stg.relational_lab_mc_set s WHERE s.run_id=@run_id AND s.origin_key=stg.relational_lab_binding.origin_key),0)
            THEN CONVERT(BIGINT,2)
            ELSE DENSE_RANK() OVER(PARTITION BY relation_kind,origin_key,origin_component ORDER BY revision DESC) END revision_rank
    INTO #bindings FROM stg.relational_lab_binding WHERE run_id=@run_id
    GROUP BY relation_kind,origin_key,origin_component,target_key,target_component,target_date,revision,cardinality;
    CREATE INDEX IX_bindings_origin ON #bindings(relation_kind,origin_key,origin_component,revision_rank);
    SELECT b.*,CONVERT(NVARCHAR(32),CASE
        WHEN revision_rank<>1 THEN N'SUPERSEDED'
        WHEN EXISTS(SELECT 1 FROM #bindings x WHERE x.revision_rank=1 AND x.relation_kind=b.relation_kind
            AND x.origin_key=b.origin_key AND x.origin_component=b.origin_component
            AND x.target_key=b.target_key AND x.target_component=b.target_component AND x.target_date<>b.target_date) THEN N'CONFLICT'
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
            AND r.entity_name=CASE b.relation_kind WHEN 'MC' THEN N'coletas' ELSE N'fretes' END AND r.source_key=b.target_key
            AND EXISTS(SELECT 1 FROM stg.relational_lab_root observed JOIN ctl.relational_lab_capture captured
                ON captured.execution_id=observed.execution_id WHERE captured.run_id=@run_id
                AND captured.entity_name=r.entity_name AND captured.business_date=b.target_date
                AND observed.source_key=b.target_key)) THEN N'ORPHAN'
        WHEN b.relation_kind='MC' AND b.target_component<>N'ROOT' THEN N'CONFLICT'
        WHEN b.relation_kind='CF' AND NOT EXISTS(SELECT 1 FROM recon.vw_relational_lab_components c WHERE c.run_id=@run_id
            AND c.entity_name=N'fretes' AND c.source_key=b.target_key AND c.component_kind=N'ITEM'
            AND c.component_key=b.target_component AND c.validity=N'VALID'
            AND EXISTS(SELECT 1 FROM stg.relational_lab_component observed JOIN ctl.relational_lab_capture captured
                ON captured.execution_id=observed.execution_id WHERE captured.run_id=@run_id
                AND captured.entity_name=N'fretes' AND captured.business_date=b.target_date
                AND observed.source_key=b.target_key AND observed.component_key=b.target_component AND observed.validity=N'VALID')) THEN N'COMPONENT_ABSENT'
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
            AND d.origin_component=l.origin_component AND (d.revision>l.revision OR (d.binding_id=l.binding_id AND d.disposition=N'SUPERSEDED')));
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

