-- ADR0048 / REL-LAB-01..06. Synthetic local laboratory, no operational permits.
SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;
SET XACT_ABORT ON;
GO
ALTER TABLE ctl.execution_audit DROP CONSTRAINT CK_ctl_execution_audit_template;
ALTER TABLE ctl.execution_audit ADD CONSTRAINT CK_ctl_execution_audit_template
    CHECK(template_id IN(6908,6389,6399));
GO
CREATE TABLE ctl.relational_lab_run (
    run_id UNIQUEIDENTIFIER NOT NULL CONSTRAINT PK_relational_lab_run PRIMARY KEY,
    source_instance NVARCHAR(128) COLLATE Latin1_General_100_BIN2 NOT NULL,
    tenant_scope NVARCHAR(128) COLLATE Latin1_General_100_BIN2 NOT NULL,
    contract_version NVARCHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL,
    contract_fingerprint CHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL,
    window_start DATE NOT NULL, window_end DATE NOT NULL,
    maximum_rows INT NOT NULL, maximum_claim INT NOT NULL,
    maximum_attempts INT NOT NULL, lease_seconds INT NOT NULL, retry_seconds INT NOT NULL,
    expansion_days INT NOT NULL, created_at DATETIME2(7) NOT NULL,
    page_size INT NOT NULL, maximum_pages INT NOT NULL,
    CONSTRAINT CK_relational_lab_run_scope CHECK(
        source_instance=N'SYNTHETIC_RELATIONAL_LAB'
        AND tenant_scope=N'SYNTHETIC_RELATIONAL_TENANT'
        AND contract_version=N'synthetic-relational-v1'
        AND contract_fingerprint NOT LIKE '%[^a-f0-9]%' AND LEN(contract_fingerprint)=64),
    CONSTRAINT CK_relational_lab_run_bounds CHECK(window_end>=window_start
        AND DATEDIFF(DAY,window_start,window_end)<=366
        AND maximum_rows BETWEEN 1 AND 100000 AND maximum_claim BETWEEN 1 AND 100
        AND maximum_attempts BETWEEN 1 AND 10 AND lease_seconds BETWEEN 1 AND 300
        AND retry_seconds BETWEEN 1 AND 3600 AND expansion_days BETWEEN 0 AND 31
        AND page_size BETWEEN 1 AND 100 AND maximum_pages BETWEEN 2 AND 10000)
);
GO
CREATE TABLE ctl.relational_lab_contract (
    run_id UNIQUEIDENTIFIER NOT NULL REFERENCES ctl.relational_lab_run(run_id),
    entity_name NVARCHAR(16) COLLATE Latin1_General_100_BIN2 NOT NULL,
    contract_fingerprint CHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL,
    CONSTRAINT PK_relational_lab_contract PRIMARY KEY(run_id,entity_name),
    CONSTRAINT CK_relational_lab_contract CHECK(entity_name IN(N'manifestos',N'coletas',N'fretes')
        AND LEN(contract_fingerprint)=64 AND contract_fingerprint NOT LIKE '%[^a-f0-9]%')
);
GO
CREATE TABLE ctl.relational_lab_capture (
    execution_id UNIQUEIDENTIFIER NOT NULL CONSTRAINT PK_relational_lab_capture PRIMARY KEY
        REFERENCES ctl.execution_attempt(execution_id),
    run_id UNIQUEIDENTIFIER NOT NULL REFERENCES ctl.relational_lab_run(run_id),
    entity_name NVARCHAR(16) COLLATE Latin1_General_100_BIN2 NOT NULL,
    business_date DATE NOT NULL, contract_fingerprint CHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL,
    physical_rows BIGINT NOT NULL, root_rows BIGINT NOT NULL, component_rows BIGINT NOT NULL,
    sealed_at DATETIME2(7) NOT NULL,
    CONSTRAINT CK_relational_lab_capture CHECK(entity_name IN(N'manifestos',N'coletas',N'fretes')
        AND physical_rows>=root_rows AND root_rows>=0 AND component_rows>=0)
);
CREATE INDEX IX_relational_lab_capture_run ON ctl.relational_lab_capture(run_id,entity_name,business_date);
GO
CREATE TABLE stg.relational_lab_root (
    execution_id UNIQUEIDENTIFIER NOT NULL REFERENCES ctl.relational_lab_capture(execution_id),
    source_key NVARCHAR(128) COLLATE Latin1_General_100_BIN2 NOT NULL,
    alias_presence NVARCHAR(8) COLLATE Latin1_General_100_BIN2 NOT NULL,
    alias_key NVARCHAR(128) COLLATE Latin1_General_100_BIN2 NULL,
    epoch_second BIGINT NOT NULL, nano INT NOT NULL,
    status_code NVARCHAR(50) COLLATE Latin1_General_100_BIN2 NULL, terminal BIT NOT NULL,
    CONSTRAINT PK_relational_lab_stage_root PRIMARY KEY(execution_id,source_key),
    CONSTRAINT CK_relational_lab_stage_root CHECK(nano BETWEEN 0 AND 999999999
        AND source_key LIKE N'INTEGER:%'
        AND ((alias_presence=N'VALUE' AND alias_key IS NOT NULL)
            OR (alias_presence IN(N'ABSENT',N'NULL') AND alias_key IS NULL)))
);
GO
CREATE TABLE core.relational_lab_root (
    run_id UNIQUEIDENTIFIER NOT NULL REFERENCES ctl.relational_lab_run(run_id),
    entity_name NVARCHAR(16) COLLATE Latin1_General_100_BIN2 NOT NULL,
    source_key NVARCHAR(128) COLLATE Latin1_General_100_BIN2 NOT NULL,
    execution_id UNIQUEIDENTIFIER NOT NULL,
    alias_presence NVARCHAR(8) COLLATE Latin1_General_100_BIN2 NOT NULL,
    alias_key NVARCHAR(128) COLLATE Latin1_General_100_BIN2 NULL,
    epoch_second BIGINT NOT NULL, nano INT NOT NULL,
    status_code NVARCHAR(50) COLLATE Latin1_General_100_BIN2 NULL, terminal BIT NOT NULL,
    CONSTRAINT PK_relational_lab_root PRIMARY KEY(run_id,entity_name,source_key),
    CONSTRAINT FK_relational_lab_root_capture FOREIGN KEY(execution_id,source_key)
        REFERENCES stg.relational_lab_root(execution_id,source_key),
    CONSTRAINT CK_relational_lab_root CHECK(nano BETWEEN 0 AND 999999999)
);
CREATE INDEX IX_relational_lab_root_alias ON core.relational_lab_root(run_id,entity_name,alias_key);
GO
CREATE TABLE stg.relational_lab_component (
    component_id BIGINT IDENTITY NOT NULL CONSTRAINT PK_relational_lab_component PRIMARY KEY,
    execution_id UNIQUEIDENTIFIER NOT NULL,
    source_key NVARCHAR(128) COLLATE Latin1_General_100_BIN2 NOT NULL,
    component_kind NVARCHAR(8) COLLATE Latin1_General_100_BIN2 NOT NULL,
    presence NVARCHAR(8) COLLATE Latin1_General_100_BIN2 NOT NULL,
    component_key NVARCHAR(128) COLLATE Latin1_General_100_BIN2 NULL,
    validity NVARCHAR(8) COLLATE Latin1_General_100_BIN2 NOT NULL,
    CONSTRAINT FK_relational_lab_component_root FOREIGN KEY(execution_id,source_key)
        REFERENCES stg.relational_lab_root(execution_id,source_key),
    CONSTRAINT CK_relational_lab_component CHECK(component_kind IN(N'PICK',N'ITEM',N'MDFE')
        AND validity IN(N'VALID',N'INVALID')
        AND ((presence=N'VALUE' AND component_key IS NOT NULL)
            OR (presence IN(N'ABSENT',N'NULL') AND component_key IS NULL)))
);
CREATE INDEX IX_relational_lab_component_lookup
    ON stg.relational_lab_component(execution_id,source_key,component_kind,component_key)
    INCLUDE(presence,validity);
GO
CREATE TABLE stg.relational_lab_binding (
    binding_id BIGINT IDENTITY NOT NULL CONSTRAINT PK_relational_lab_binding PRIMARY KEY,
    run_id UNIQUEIDENTIFIER NOT NULL REFERENCES ctl.relational_lab_run(run_id),
    evidence_id NVARCHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL,
    relation_kind CHAR(2) COLLATE Latin1_General_100_BIN2 NOT NULL,
    origin_key NVARCHAR(128) COLLATE Latin1_General_100_BIN2 NOT NULL,
    origin_component NVARCHAR(128) COLLATE Latin1_General_100_BIN2 NOT NULL,
    target_key NVARCHAR(128) COLLATE Latin1_General_100_BIN2 NOT NULL,
    target_component NVARCHAR(128) COLLATE Latin1_General_100_BIN2 NOT NULL,
    target_date DATE NOT NULL, revision INT NOT NULL,
    cardinality NVARCHAR(16) COLLATE Latin1_General_100_BIN2 NOT NULL,
    evidence_version NVARCHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL,
    observed_at DATETIME2(7) NOT NULL,
    CONSTRAINT UQ_relational_lab_binding_evidence UNIQUE(run_id,evidence_id),
    CONSTRAINT CK_relational_lab_binding CHECK(relation_kind IN('MC','CF') AND revision BETWEEN 1 AND 1000
        AND cardinality IN(N'ONE_TO_ONE',N'ONE_TO_MANY',N'MANY_TO_MANY')
        AND evidence_version=N'synthetic-relational-binding-v1'
        AND origin_key LIKE N'INTEGER:%' AND target_key LIKE N'INTEGER:%'
        AND (origin_component LIKE N'INTEGER:%' OR origin_component LIKE N'STRING:%')
        AND (target_component=N'ROOT' OR target_component LIKE N'INTEGER:%' OR target_component LIKE N'STRING:%'))
);
CREATE INDEX IX_relational_lab_binding_origin
    ON stg.relational_lab_binding(run_id,relation_kind,origin_key,origin_component,revision)
    INCLUDE(target_key,target_component,cardinality,target_date);
GO
CREATE TYPE stg.relational_lab_binding_batch AS TABLE (
    evidence_id NVARCHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL PRIMARY KEY,
    relation_kind CHAR(2) COLLATE Latin1_General_100_BIN2 NOT NULL,
    origin_key NVARCHAR(128) COLLATE Latin1_General_100_BIN2 NOT NULL,
    origin_component NVARCHAR(128) COLLATE Latin1_General_100_BIN2 NOT NULL,
    target_key NVARCHAR(128) COLLATE Latin1_General_100_BIN2 NOT NULL,
    target_component NVARCHAR(128) COLLATE Latin1_General_100_BIN2 NOT NULL,
    target_date DATE NOT NULL, revision INT NOT NULL,
    cardinality NVARCHAR(16) COLLATE Latin1_General_100_BIN2 NOT NULL
);
GO
CREATE TABLE recon.relational_lab_receipt (
    receipt_id UNIQUEIDENTIFIER NOT NULL CONSTRAINT PK_relational_lab_receipt PRIMARY KEY,
    run_id UNIQUEIDENTIFIER NOT NULL REFERENCES ctl.relational_lab_run(run_id),
    execution_id UNIQUEIDENTIFIER NULL REFERENCES ctl.relational_lab_capture(execution_id),
    operation NVARCHAR(16) COLLATE Latin1_General_100_BIN2 NOT NULL,
    considered BIGINT NOT NULL, inserted BIGINT NOT NULL, updated BIGINT NOT NULL, noop BIGINT NOT NULL,
    resolved BIGINT NOT NULL, orphaned BIGINT NOT NULL, blocked BIGINT NOT NULL,
    observed_at DATETIME2(7) NOT NULL,
    CONSTRAINT CK_relational_lab_receipt CHECK(operation IN(N'CAPTURE',N'RESOLVE')
        AND considered>=0 AND inserted>=0 AND updated>=0 AND noop>=0
        AND resolved>=0 AND orphaned>=0 AND blocked>=0
        AND (operation<>N'CAPTURE' OR considered=inserted+updated+noop))
);
CREATE INDEX IX_relational_lab_receipt_run ON recon.relational_lab_receipt(run_id,observed_at);
GO
CREATE TABLE core.relational_lab_link (
    link_id BIGINT IDENTITY NOT NULL CONSTRAINT PK_relational_lab_link PRIMARY KEY,
    run_id UNIQUEIDENTIFIER NOT NULL REFERENCES ctl.relational_lab_run(run_id),
    relation_kind CHAR(2) COLLATE Latin1_General_100_BIN2 NOT NULL,
    origin_key NVARCHAR(128) COLLATE Latin1_General_100_BIN2 NOT NULL,
    origin_component NVARCHAR(128) COLLATE Latin1_General_100_BIN2 NOT NULL,
    target_key NVARCHAR(128) COLLATE Latin1_General_100_BIN2 NOT NULL,
    target_component NVARCHAR(128) COLLATE Latin1_General_100_BIN2 NOT NULL,
    revision INT NOT NULL, active BIT NOT NULL,
    binding_id BIGINT NOT NULL REFERENCES stg.relational_lab_binding(binding_id),
    receipt_id UNIQUEIDENTIFIER NOT NULL REFERENCES recon.relational_lab_receipt(receipt_id),
    CONSTRAINT UQ_relational_lab_link UNIQUE NONCLUSTERED
        (run_id,relation_kind,origin_key,origin_component,target_key,target_component,revision)
);
CREATE INDEX IX_relational_lab_link_consumer
    ON core.relational_lab_link(run_id,relation_kind,active,origin_key,target_key);
GO
CREATE TABLE recon.relational_lab_resolution (
    receipt_id UNIQUEIDENTIFIER NOT NULL REFERENCES recon.relational_lab_receipt(receipt_id),
    binding_id BIGINT NOT NULL REFERENCES stg.relational_lab_binding(binding_id),
    disposition NVARCHAR(32) COLLATE Latin1_General_100_BIN2 NOT NULL,
    CONSTRAINT PK_relational_lab_resolution PRIMARY KEY(receipt_id,binding_id),
    CONSTRAINT CK_relational_lab_resolution CHECK(disposition IN
        (N'RESOLVED',N'ORPHAN',N'ORIGIN_ABSENT',N'COMPONENT_ABSENT',N'CONFLICT',N'SUPERSEDED',N'CHAIN_BLOCKED'))
);
GO
CREATE TABLE ctl.relational_lab_backlog (
    backlog_id BIGINT IDENTITY NOT NULL CONSTRAINT PK_relational_lab_backlog PRIMARY KEY,
    run_id UNIQUEIDENTIFIER NOT NULL REFERENCES ctl.relational_lab_run(run_id),
    binding_id BIGINT NOT NULL REFERENCES stg.relational_lab_binding(binding_id),
    state NVARCHAR(16) COLLATE Latin1_General_100_BIN2 NOT NULL,
    cause NVARCHAR(32) COLLATE Latin1_General_100_BIN2 NOT NULL,
    first_seen DATETIME2(7) NOT NULL, last_seen DATETIME2(7) NOT NULL,
    attempts INT NOT NULL, eligible_at DATETIME2(7) NOT NULL,
    owner_id UNIQUEIDENTIFIER NULL, lease_until DATETIME2(7) NULL,
    last_execution_id UNIQUEIDENTIFIER NULL REFERENCES ctl.execution_attempt(execution_id),
    last_receipt_id UNIQUEIDENTIFIER NOT NULL REFERENCES recon.relational_lab_receipt(receipt_id),
    CONSTRAINT UQ_relational_lab_backlog UNIQUE(run_id,binding_id),
    CONSTRAINT CK_relational_lab_backlog CHECK(state IN(N'PENDING',N'CLAIMED',N'DEFERRED',N'RESOLVED',N'QUARANTINED')
        AND attempts>=0 AND last_seen>=first_seen
        AND ((state=N'CLAIMED' AND owner_id IS NOT NULL AND lease_until IS NOT NULL)
            OR (state<>N'CLAIMED' AND owner_id IS NULL AND lease_until IS NULL)))
);
CREATE INDEX IX_relational_lab_backlog_eligible
    ON ctl.relational_lab_backlog(run_id,state,eligible_at,backlog_id)
    INCLUDE(attempts,lease_until,binding_id,first_seen);
GO
CREATE TABLE ctl.relational_lab_attempt (
    backlog_id BIGINT NOT NULL REFERENCES ctl.relational_lab_backlog(backlog_id),
    attempt_number INT NOT NULL, owner_id UNIQUEIDENTIFIER NOT NULL,
    started_at DATETIME2(7) NOT NULL, lease_until DATETIME2(7) NOT NULL,
    finished_at DATETIME2(7) NULL,
    result NVARCHAR(24) COLLATE Latin1_General_100_BIN2 NOT NULL,
    execution_id UNIQUEIDENTIFIER NULL REFERENCES ctl.execution_attempt(execution_id),
    CONSTRAINT PK_relational_lab_attempt PRIMARY KEY(backlog_id,attempt_number),
    CONSTRAINT CK_relational_lab_attempt CHECK(attempt_number>0 AND result IN
        (N'CLAIMED',N'RESOLVED',N'TEMPORARY_FAILURE',N'CONTRACT_FAILURE',N'CONFLICT',N'ABANDONED',N'LEASE_EXPIRED'))
);
GO
CREATE PROCEDURE ctl.usp_relational_lab_lock @run_id UNIQUEIDENTIFIER
AS
BEGIN
    SET NOCOUNT ON;
    IF DB_NAME()<>N'ETL_SISTEMA_V2_SHADOW' OR @@TRANCOUNT=0 OR XACT_STATE()<>1
        THROW 53200,N'REL_LAB_SHARED_TRANSACTION_REQUIRED',1;
    DECLARE @lock INT,@resource NVARCHAR(255)=CONCAT(N'relational-lab:',CONVERT(NVARCHAR(36),@run_id));
    EXEC @lock=sys.sp_getapplock @Resource=@resource,@LockMode='Exclusive',@LockOwner='Transaction',@LockTimeout=1200;
    IF @lock<0 THROW 53201,N'REL_LAB_BUSY',1;
END;
GO
CREATE PROCEDURE stg.usp_relational_lab_bind
    @run_id UNIQUEIDENTIFIER,@rows stg.relational_lab_binding_batch READONLY,@now DATETIME2(7)
AS
BEGIN
    SET NOCOUNT ON;
    EXEC ctl.usp_relational_lab_lock @run_id;
    IF NOT EXISTS(SELECT 1 FROM ctl.relational_lab_run WHERE run_id=@run_id)
        OR (SELECT COUNT_BIG(*) FROM @rows)>100 THROW 53202,N'REL_LAB_BINDING_BOUND',1;
    IF EXISTS(SELECT 1 FROM @rows b JOIN ctl.relational_lab_run r ON r.run_id=@run_id
        WHERE b.target_date<DATEADD(DAY,-r.expansion_days,r.window_start)
            OR b.target_date>DATEADD(DAY,r.expansion_days,r.window_end))
        THROW 53203,N'REL_LAB_BINDING_WINDOW',1;
    IF EXISTS(SELECT evidence_id,relation_kind,origin_key,origin_component,target_key,target_component,target_date,revision,cardinality
        FROM @rows WHERE evidence_id IN(SELECT evidence_id FROM stg.relational_lab_binding WHERE run_id=@run_id)
        EXCEPT SELECT evidence_id,relation_kind,origin_key,origin_component,target_key,target_component,target_date,revision,cardinality
        FROM stg.relational_lab_binding WHERE run_id=@run_id)
        THROW 53204,N'REL_LAB_BINDING_RETRY_DIVERGENT',1;
    INSERT stg.relational_lab_binding(run_id,evidence_id,relation_kind,origin_key,origin_component,
        target_key,target_component,target_date,revision,cardinality,evidence_version,observed_at)
    SELECT @run_id,b.evidence_id,b.relation_kind,b.origin_key,b.origin_component,b.target_key,b.target_component,
        b.target_date,b.revision,b.cardinality,N'synthetic-relational-binding-v1',@now FROM @rows b
    WHERE NOT EXISTS(SELECT 1 FROM stg.relational_lab_binding e WHERE e.run_id=@run_id AND e.evidence_id=b.evidence_id);
END;
GO
