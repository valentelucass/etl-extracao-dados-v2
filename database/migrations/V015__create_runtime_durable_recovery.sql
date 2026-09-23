-- P02R: preparado, não aplicado. Nenhum GRANT ou membership.
SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;
SET XACT_ABORT ON;
GO
CREATE FUNCTION ctl.fn_runtime_recovery_field(@value NVARCHAR(MAX))
RETURNS NVARCHAR(MAX)
AS
BEGIN
    RETURN CONCAT(CONVERT(NVARCHAR(20),DATALENGTH(@value)),N':',@value,N'|');
END;
GO
CREATE FUNCTION ctl.fn_runtime_recovery_identity(@execution_id UNIQUEIDENTIFIER)
RETURNS NVARCHAR(MAX)
AS
BEGIN
    DECLARE @identity NVARCHAR(MAX);
    SELECT @identity=CONCAT(CAST(N'runtime-recovery-v1|' AS NVARCHAR(MAX)),
        ctl.fn_runtime_recovery_field(LOWER(CONVERT(NVARCHAR(36),a.execution_id))),
        ctl.fn_runtime_recovery_field(LOWER(CONVERT(NVARCHAR(36),a.cycle_id))),
        ctl.fn_runtime_recovery_field(c.plan_version),
        ctl.fn_runtime_recovery_field(CONVERT(NVARCHAR(64),c.plan_fingerprint)),
        ctl.fn_runtime_recovery_field(p.environment_name),
        ctl.fn_runtime_recovery_field(p.source_instance),
        ctl.fn_runtime_recovery_field(p.tenant_scope),
        ctl.fn_runtime_recovery_field(p.entity_name),
        ctl.fn_runtime_recovery_field(p.execution_mode),
        ctl.fn_runtime_recovery_field(CONCAT(CONVERT(NVARCHAR(19),p.partition_start_utc,126),N'.',RIGHT(N'000'+CONVERT(NVARCHAR(3),DATEPART(MILLISECOND,p.partition_start_utc)),3))),
        ctl.fn_runtime_recovery_field(CONCAT(CONVERT(NVARCHAR(19),p.partition_end_exclusive_utc,126),N'.',RIGHT(N'000'+CONVERT(NVARCHAR(3),DATEPART(MILLISECOND,p.partition_end_exclusive_utc)),3))),
        ctl.fn_runtime_recovery_field(a.window_strategy),
        ctl.fn_runtime_recovery_field(a.idempotency_key),
        ctl.fn_runtime_recovery_field(COALESCE(LOWER(CONVERT(NVARCHAR(36),a.replay_of_execution_id)),N'')),
        ctl.fn_runtime_recovery_field(a.contract_version),
        ctl.fn_runtime_recovery_field(LOWER(CONVERT(NVARCHAR(64),a.contract_fingerprint))),
        ctl.fn_runtime_recovery_field(a.configuration_version),
        ctl.fn_runtime_recovery_field(LOWER(CONVERT(NVARCHAR(64),a.configuration_fingerprint))))
    FROM ctl.execution_attempt a
    JOIN ctl.execution_partition p ON p.partition_id=a.partition_id
    JOIN ctl.execution_cycle c ON c.cycle_id=a.cycle_id
    WHERE a.execution_id=@execution_id;
    RETURN @identity;
END;
GO
CREATE TABLE ctl.runtime_contract_evidence (
    execution_id UNIQUEIDENTIFIER NOT NULL,
    identity_material NVARCHAR(4000) COLLATE Latin1_General_100_BIN2 NOT NULL,
    contract_material NVARCHAR(4000) COLLATE Latin1_General_100_BIN2 NOT NULL,
    policy_version NVARCHAR(128) COLLATE Latin1_General_100_BIN2 NOT NULL,
    policy_fingerprint CHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL,
    audited_rows BIGINT NOT NULL,
    audited_pages BIGINT NOT NULL,
    audit_hash CHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL,
    evidence_hash CHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL,
    sealed_at_utc DATETIME2(3) NOT NULL,
    CONSTRAINT PK_ctl_runtime_contract_evidence PRIMARY KEY (execution_id),
    CONSTRAINT FK_ctl_runtime_contract_evidence_attempt FOREIGN KEY (execution_id)
        REFERENCES ctl.execution_attempt(execution_id),
    CONSTRAINT CK_ctl_runtime_contract_evidence_counts CHECK (audited_rows>0 AND audited_pages>=2),
    CONSTRAINT CK_ctl_runtime_contract_evidence_hash CHECK (
        evidence_hash NOT LIKE '%[^0-9a-f]%' AND policy_fingerprint NOT LIKE '%[^0-9a-f]%')
);
GO
CREATE TRIGGER ctl.trg_runtime_contract_evidence_immutable
ON ctl.runtime_contract_evidence AFTER UPDATE, DELETE
AS
BEGIN
    SET NOCOUNT ON;
    IF EXISTS(SELECT 1 FROM deleted) THROW 52300,N'RUNTIME_RECOVERY_EVIDENCE_IMMUTABLE',1;
END;
GO
-- Recibo tipado de Coletas: COL-03 pode divergir das disposições genéricas.
CREATE TABLE ctl.runtime_coleta_publication_receipt (
    execution_id UNIQUEIDENTIFIER NOT NULL,
    candidate_rows BIGINT NOT NULL,inserted_rows BIGINT NOT NULL,updated_rows BIGINT NOT NULL,
    reactivated_rows BIGINT NOT NULL,noop_rows BIGINT NOT NULL,stale_noop_rows BIGINT NOT NULL,
    reconciled_at_utc DATETIME2(3) NOT NULL,published_at_utc DATETIME2(3) NOT NULL,
    incremental_frontier_before_utc DATETIME2(3) NULL,incremental_frontier_after_utc DATETIME2(3) NULL,
    receipt_hash CHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL,
    CONSTRAINT PK_ctl_runtime_coleta_publication_receipt PRIMARY KEY(execution_id),
    CONSTRAINT FK_ctl_runtime_coleta_publication_receipt FOREIGN KEY(execution_id)
        REFERENCES ctl.execution_publication_event(execution_id),
    CONSTRAINT CK_ctl_runtime_coleta_publication_receipt CHECK (
        candidate_rows>=0 AND inserted_rows>=0 AND updated_rows>=0 AND reactivated_rows=0
        AND noop_rows>=0 AND stale_noop_rows BETWEEN 0 AND noop_rows
        AND candidate_rows=inserted_rows+updated_rows+reactivated_rows+noop_rows
        AND published_at_utc>=reconciled_at_utc AND receipt_hash NOT LIKE '%[^0-9a-f]%'
        AND ((incremental_frontier_before_utc IS NULL AND incremental_frontier_after_utc IS NULL)
            OR (incremental_frontier_before_utc IS NOT NULL AND incremental_frontier_after_utc IS NOT NULL AND incremental_frontier_after_utc>=incremental_frontier_before_utc)))
);
GO
CREATE FUNCTION ctl.fn_runtime_coleta_publication_hash(@execution_id UNIQUEIDENTIFIER)
RETURNS CHAR(64)
AS
BEGIN
    DECLARE @hash CHAR(64);
    SELECT @hash=LOWER(CONVERT(CHAR(64),HASHBYTES('SHA2_256',CONCAT(
        ctl.fn_runtime_recovery_identity(r.execution_id),N'|',r.candidate_rows,N'|',
        r.inserted_rows,N'|',r.updated_rows,N'|',r.reactivated_rows,N'|',
        r.noop_rows,N'|',r.stale_noop_rows,N'|',
        CONVERT(NVARCHAR(23),r.reconciled_at_utc,126),N'|',CONVERT(NVARCHAR(23),r.published_at_utc,126),N'|',
        CONVERT(NVARCHAR(23),r.incremental_frontier_before_utc,126),N'|',
        CONVERT(NVARCHAR(23),r.incremental_frontier_after_utc,126))),2))
    FROM ctl.runtime_coleta_publication_receipt r WHERE r.execution_id=@execution_id;
    RETURN @hash;
END;
GO
CREATE TRIGGER ctl.trg_runtime_coleta_receipt_immutable
ON ctl.runtime_coleta_publication_receipt AFTER INSERT,UPDATE,DELETE
AS
BEGIN
    SET NOCOUNT ON;
    IF EXISTS(SELECT 1 FROM deleted) THROW 52310,N'RUNTIME_COLETA_RECEIPT_IMMUTABLE',1;
    IF EXISTS(SELECT 1 FROM inserted i WHERE i.receipt_hash<>ctl.fn_runtime_coleta_publication_hash(i.execution_id))
        THROW 52311,N'RUNTIME_COLETA_RECEIPT_INVALID',1;
END;
GO
CREATE PROCEDURE ctl.usp_runtime_recovery
    @execution_id UNIQUEIDENTIFIER,
    @operation NVARCHAR(MAX),
    @expected_identity NVARCHAR(MAX),
    @contract_material NVARCHAR(MAX),
    @policy_version NVARCHAR(MAX),
    @policy_fingerprint NVARCHAR(MAX),
    @expected_revision NVARCHAR(MAX),
    @entity NVARCHAR(MAX)
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    IF @operation IS NULL OR @operation COLLATE Latin1_General_100_BIN2 NOT IN(N'READ',N'SEAL',N'RESUME')
        OR @expected_identity IS NULL OR DATALENGTH(@expected_identity) NOT BETWEEN 2 AND 8000
        OR @contract_material IS NULL OR DATALENGTH(@contract_material) NOT BETWEEN 2 AND 8000
        OR @policy_version IS NULL OR DATALENGTH(@policy_version) NOT BETWEEN 2 AND 256
        OR @policy_fingerprint IS NULL OR DATALENGTH(@policy_fingerprint)<>128
        OR @policy_fingerprint COLLATE Latin1_General_100_BIN2 LIKE N'%[^0-9a-f]%'
        OR @entity IS NULL OR @entity COLLATE Latin1_General_100_BIN2 NOT IN(N'coletas',N'fretes')
        THROW 52301,N'RUNTIME_RECOVERY_INPUT_INVALID',1;
    BEGIN TRY
        BEGIN TRANSACTION;
        DECLARE @environment_name NVARCHAR(32),@source_instance NVARCHAR(128),
            @tenant_scope NVARCHAR(128),@entity_name NVARCHAR(128),@partition_id BIGINT;
        SELECT @environment_name=p.environment_name,@source_instance=p.source_instance,
            @tenant_scope=p.tenant_scope,@entity_name=p.entity_name,@partition_id=p.partition_id
        FROM ctl.execution_attempt a JOIN ctl.execution_partition p ON p.partition_id=a.partition_id
        WHERE a.execution_id=@execution_id;
        DECLARE @lock_payload NVARCHAR(1800)=CONCAT(
            DATALENGTH(@environment_name),N':',@environment_name,N'|',
            DATALENGTH(@source_instance),N':',@source_instance,N'|',
            DATALENGTH(@tenant_scope),N':',@tenant_scope,N'|',
            DATALENGTH(@entity_name),N':',@entity_name);
        DECLARE @lock_resource NVARCHAR(255)=N'V2_APPLY_'+CONVERT(NVARCHAR(64),HASHBYTES('SHA2_256',@lock_payload),2);
        DECLARE @lock_result INT;
        EXEC @lock_result=sys.sp_getapplock @Resource=@lock_resource,@LockMode=N'Exclusive',
            @LockOwner=N'Transaction',@LockTimeout=10000,@DbPrincipal=N'public';
        IF @lock_result<0 THROW 52302,N'RUNTIME_RECOVERY_LOCK_UNAVAILABLE',1;
        DECLARE @execution_state NVARCHAR(32),@current_execution UNIQUEIDENTIFIER,@partition_state NVARCHAR(32),
            @next_transition_sequence INT,@contract_version NVARCHAR(128),@contract_fingerprint CHAR(64),
            @configuration_version NVARCHAR(128),@configuration_fingerprint CHAR(64),@mode NVARCHAR(16);
        SELECT @execution_state=a.current_state,@next_transition_sequence=a.next_transition_sequence,
            @contract_version=a.contract_version,@contract_fingerprint=a.contract_fingerprint,
            @configuration_version=a.configuration_version,@configuration_fingerprint=a.configuration_fingerprint,
            @current_execution=p.current_execution_id,@partition_state=p.current_state,@mode=p.execution_mode
        FROM ctl.execution_attempt a WITH(UPDLOCK,HOLDLOCK)
        JOIN ctl.execution_partition p WITH(UPDLOCK,HOLDLOCK) ON p.partition_id=a.partition_id
        JOIN ctl.execution_cycle c WITH(HOLDLOCK) ON c.cycle_id=a.cycle_id
        WHERE a.execution_id=@execution_id;
        DECLARE @attempt_exists BIT=CASE WHEN EXISTS(SELECT 1 FROM ctl.execution_attempt WITH(HOLDLOCK)
            WHERE execution_id=@execution_id) THEN 1 ELSE 0 END;
        DECLARE @identity NVARCHAR(MAX)=ctl.fn_runtime_recovery_identity(@execution_id);
        DECLARE @now DATETIME2(3)=SYSUTCDATETIME(),@lease_id UNIQUEIDENTIFIER,
            @expires DATETIME2(3),@released DATETIME2(3),@lease_valid BIT=0;
        SELECT @lease_id=lease_id,@expires=expires_at_utc,@released=released_at_utc
        FROM ctl.execution_lease WITH(UPDLOCK,HOLDLOCK)
        WHERE execution_id=@execution_id AND partition_id=@partition_id;
        IF @released IS NULL AND @expires>@now AND @current_execution=@execution_id
            AND @partition_state=@execution_state SET @lease_valid=1;
        DECLARE @unique_pages INT,@pages BIGINT,@rows BIGINT,@last_page INT,@first_page INT,@terminal INT,@terminal_page INT;
        SELECT @unique_pages=COUNT(DISTINCT page_number),@pages=COUNT_BIG(*),@rows=COALESCE(SUM(physical_rows),0),
            @last_page=MAX(page_number),@first_page=MIN(page_number),
            @terminal=SUM(CASE WHEN terminal_evidence_kind=N'DATA_EXPORT_EMPTY_PAGE' AND terminal_empty_page=1
                AND physical_rows=0 THEN 1 ELSE 0 END),
            @terminal_page=MAX(CASE WHEN terminal_evidence_kind=N'DATA_EXPORT_EMPTY_PAGE' THEN page_number END)
        FROM ctl.execution_page_audit WITH(HOLDLOCK) WHERE execution_id=@execution_id;
        DECLARE @audit_hash CHAR(64);
        SELECT @audit_hash=LOWER(CONVERT(CHAR(64),HASHBYTES('SHA2_256',STRING_AGG(
            CAST(CONCAT(page_number,N':',page_attempt,N':',requested_page_size,N':',physical_rows,N':',
                distinct_root_keys,N':',response_bytes,N':',terminal_empty_page,N':',terminal_evidence_kind,
                N':',CONVERT(NVARCHAR(23),read_at_utc,126)) AS NVARCHAR(MAX)),N'|')
                WITHIN GROUP(ORDER BY page_number,page_attempt)),2))
        FROM ctl.execution_page_audit WITH(HOLDLOCK) WHERE execution_id=@execution_id;
        DECLARE @evidence_hash CHAR(64)=LOWER(CONVERT(CHAR(64),HASHBYTES('SHA2_256',CONCAT(
            @identity,@audit_hash,ctl.fn_runtime_recovery_field(@contract_material),
            ctl.fn_runtime_recovery_field(@policy_version),ctl.fn_runtime_recovery_field(@policy_fingerprint),
            ctl.fn_runtime_recovery_field(CONVERT(NVARCHAR(20),@rows)),
            ctl.fn_runtime_recovery_field(CONVERT(NVARCHAR(20),@pages)))),2));
        DECLARE @identity_valid BIT=CASE WHEN @identity COLLATE Latin1_General_100_BIN2=@expected_identity COLLATE Latin1_General_100_BIN2
            AND DATALENGTH(@identity)=DATALENGTH(@expected_identity) AND @entity_name=@entity THEN 1 ELSE 0 END;
        DECLARE @audit_valid BIT=CASE WHEN @pages>=2 AND @rows>0 AND @pages=@unique_pages AND @pages=@last_page AND @first_page=1
            AND @terminal=1 AND @terminal_page=@last_page THEN 1 ELSE 0 END;
        IF @operation=N'SEAL'
        BEGIN
            IF @identity_valid=0 OR @execution_state<>N'EXTRACTING' OR @lease_valid=0 OR @audit_valid=0
                OR @rows<>(SELECT COUNT_BIG(*) FROM stg.execution_record WITH(HOLDLOCK) WHERE execution_id=@execution_id)
                THROW 52303,N'RUNTIME_RECOVERY_SEAL_GATE_REJECTED',1;
            IF EXISTS(SELECT 1 FROM ctl.runtime_contract_evidence WITH(UPDLOCK,HOLDLOCK)
                WHERE execution_id=@execution_id AND evidence_hash<>@evidence_hash)
                THROW 52304,N'RUNTIME_RECOVERY_EVIDENCE_CONFLICT',1;
            IF NOT EXISTS(SELECT 1 FROM ctl.runtime_contract_evidence WITH(UPDLOCK,HOLDLOCK) WHERE execution_id=@execution_id)
                INSERT ctl.runtime_contract_evidence VALUES(@execution_id,@identity,@contract_material,
                    @policy_version,@policy_fingerprint,@rows,@pages,@audit_hash,@evidence_hash,@now);
            COMMIT TRANSACTION;
            RETURN;
        END;
        DECLARE @contract_verified BIT=0,@stored_hash CHAR(64);
        SELECT @stored_hash=evidence_hash,@contract_verified=CASE WHEN
            evidence_hash=@evidence_hash AND audit_hash=@audit_hash AND identity_material=@identity
            AND DATALENGTH(identity_material)=DATALENGTH(@identity)
            AND contract_material=@contract_material AND DATALENGTH(contract_material)=DATALENGTH(@contract_material)
            AND policy_version=@policy_version AND policy_fingerprint=@policy_fingerprint
            AND audited_rows=@rows AND audited_pages=@pages AND @audit_valid=1 THEN 1 ELSE 0 END
        FROM ctl.runtime_contract_evidence WITH(HOLDLOCK) WHERE execution_id=@execution_id;
        DECLARE @candidates BIGINT=0,@quality NVARCHAR(16)=N'ABSENT',@evaluation_hash CHAR(64),@typed_valid BIT=0;
        SELECT @candidates=candidate_rows FROM ctl.execution_promotion_result WITH(HOLDLOCK) WHERE execution_id=@execution_id;
        IF EXISTS(SELECT 1 FROM ctl.coleta_promotion_result WITH(HOLDLOCK) WHERE execution_id=@execution_id
            AND @entity=N'coletas' AND validation_state=N'PASSED' AND typed_candidate_rows=@candidates)
            OR EXISTS(SELECT 1 FROM ctl.frete_promotion_result WITH(HOLDLOCK) WHERE execution_id=@execution_id
            AND @entity=N'fretes' AND validation_state=N'PASSED' AND typed_candidate_rows=@candidates)
            SET @typed_valid=1;
        SELECT @quality=evaluation_state,@evaluation_hash=evaluation_fingerprint
        FROM recon.execution_data_quality_evaluation WITH(HOLDLOCK) WHERE execution_id=@execution_id;
        IF EXISTS(SELECT 1 FROM recon.execution_data_quality_evaluation WITH(HOLDLOCK) WHERE execution_id=@execution_id
            AND (policy_version<>@policy_version OR policy_fingerprint<>@policy_fingerprint)) SET @quality=N'OBSOLETE';
        IF @execution_state<>N'PUBLISHED' AND (NOT EXISTS(
            SELECT 1 FROM ctl.data_quality_policy WITH(HOLDLOCK) WHERE policy_version=@policy_version
                AND policy_fingerprint=@policy_fingerprint AND policy_state=N'RATIFIED' AND effective_from_utc<=@now)
            OR EXISTS(SELECT 1 FROM ctl.data_quality_policy p WITH(HOLDLOCK)
                JOIN ctl.data_quality_policy newer WITH(HOLDLOCK) ON newer.scope_fingerprint=p.scope_fingerprint
                WHERE p.policy_version=@policy_version AND p.policy_fingerprint=@policy_fingerprint
                AND newer.policy_state=N'RATIFIED' AND newer.effective_from_utc>p.effective_from_utc
                AND newer.effective_from_utc<=@now)) SET @quality=N'OBSOLETE';
        DECLARE @dq_integrity BIT=1;
        DECLARE @scope CHAR(64)=LOWER(CONVERT(CHAR(64),HASHBYTES('SHA2_256',CONCAT(
            N'dq-scope-v1|',DATALENGTH(@environment_name),N':',@environment_name,
            N'|',DATALENGTH(@source_instance),N':',@source_instance,
            N'|',DATALENGTH(@tenant_scope),N':',@tenant_scope,
            N'|',DATALENGTH(@entity_name),N':',@entity_name,
            N'|',DATALENGTH(@mode),N':',@mode)),2));
        IF EXISTS(SELECT 1 FROM recon.execution_data_quality_evaluation e WITH(HOLDLOCK)
            WHERE e.execution_id=@execution_id AND (e.scope_fingerprint<>@scope OR e.evaluation_fingerprint<>
                LOWER(CONVERT(CHAR(64),HASHBYTES('SHA2_256',CONCAT(
                    N'dq-evaluation-v1|',e.policy_version,N'|',e.policy_fingerprint,N'|',e.scope_fingerprint,N'|',
                    CONVERT(NVARCHAR(36),e.execution_id),N'|',e.candidate_rows,N'|',
                    CONVERT(NVARCHAR(33),e.promotion_recorded_at_utc,126),N'|',
                    e.completed_checks,N'|',e.passed_checks,N'|',e.failed_checks,N'|',
                    e.evaluated_rows,N'|',e.failed_rows,N'|',e.evaluation_state,N'|',
                    (SELECT CONCAT(check_code,N':',evaluated_rows,N':',failed_rows,N':',check_state)
                     FROM recon.execution_data_quality_check_result WITH(HOLDLOCK)
                     WHERE execution_id=@execution_id AND check_ordinal=1),N'|',
                    (SELECT CONCAT(check_code,N':',evaluated_rows,N':',failed_rows,N':',check_state)
                     FROM recon.execution_data_quality_check_result WITH(HOLDLOCK)
                     WHERE execution_id=@execution_id AND check_ordinal=2),N'|',
                    (SELECT CONCAT(check_code,N':',evaluated_rows,N':',failed_rows,N':',check_state)
                     FROM recon.execution_data_quality_check_result WITH(HOLDLOCK)
                     WHERE execution_id=@execution_id AND check_ordinal=3),N'|',
                    (SELECT CONCAT(check_code,N':',evaluated_rows,N':',failed_rows,N':',check_state)
                     FROM recon.execution_data_quality_check_result WITH(HOLDLOCK)
                     WHERE execution_id=@execution_id AND check_ordinal=4))),2)))) SET @dq_integrity=0;
        IF EXISTS(SELECT 1 FROM recon.execution_data_quality_evaluation e WITH(HOLDLOCK)
            WHERE e.execution_id=@execution_id AND (e.expected_checks<>4 OR e.completed_checks<>4
                OR (SELECT COUNT_BIG(*) FROM recon.execution_data_quality_check_result r WITH(HOLDLOCK)
                    JOIN ctl.data_quality_check_policy p WITH(HOLDLOCK) ON p.policy_version=e.policy_version
                        AND p.policy_fingerprint=e.policy_fingerprint AND p.check_ordinal=r.check_ordinal
                        AND p.check_code=r.check_code WHERE r.execution_id=e.execution_id)<>4)) SET @dq_integrity=0;
        DECLARE @reason NVARCHAR(40)=N'INCONSISTENT';
        IF @attempt_exists=0 SET @reason=N'NOT_FOUND';
        ELSE IF @execution_state IS NULL SET @reason=N'INCONSISTENT';
        ELSE IF @identity_valid=0 OR @dq_integrity=0 SET @reason=N'INCONSISTENT';
        ELSE IF @execution_state=N'PUBLISHED' AND @entity=N'coletas' AND NOT EXISTS(
            SELECT 1 FROM ctl.runtime_coleta_publication_receipt WITH(HOLDLOCK) WHERE execution_id=@execution_id)
            SET @reason=N'EVIDENCE_MISSING';
        ELSE IF @execution_state=N'PUBLISHED'
        BEGIN
            IF @typed_valid=1 AND @quality=N'PASSED' AND (@stored_hash IS NULL OR @contract_verified=1)
            AND (@entity<>N'coletas' OR EXISTS(SELECT 1 FROM ctl.runtime_coleta_publication_receipt r WITH(HOLDLOCK)
                JOIN recon.execution_reconciliation_result g WITH(HOLDLOCK) ON g.execution_id=r.execution_id
                JOIN ctl.execution_publication_event p WITH(HOLDLOCK) ON p.execution_id=r.execution_id
                WHERE r.execution_id=@execution_id AND r.receipt_hash=ctl.fn_runtime_coleta_publication_hash(@execution_id)
                    AND r.candidate_rows=g.candidate_rows AND r.reconciled_at_utc=g.reconciled_at_utc
                    AND r.published_at_utc=g.published_at_utc
                    AND (r.incremental_frontier_before_utc=p.incremental_frontier_before_utc
                        OR r.incremental_frontier_before_utc IS NULL AND p.incremental_frontier_before_utc IS NULL)
                    AND (r.incremental_frontier_after_utc=p.incremental_frontier_after_utc
                        OR r.incremental_frontier_after_utc IS NULL AND p.incremental_frontier_after_utc IS NULL))) AND EXISTS(
                SELECT 1 FROM recon.execution_reconciliation_result result WITH(HOLDLOCK)
                JOIN ctl.execution_publication_event publication WITH(HOLDLOCK) ON publication.execution_id=result.execution_id
                JOIN ctl.execution_promotion_result candidate_set WITH(HOLDLOCK) ON candidate_set.execution_id=result.execution_id
                WHERE result.execution_id=@execution_id AND publication.partition_id=@partition_id
                AND result.candidate_rows=candidate_set.candidate_rows AND candidate_set.quarantined_stage_rows=0
                AND result.published_at_utc=publication.published_at_utc
                AND result.reconciled_at_utc<=result.published_at_utc AND @released IS NOT NULL
                AND ((@mode=N'INCREMENTAL' AND publication.incremental_frontier_before_utc IS NOT NULL
                    AND publication.incremental_frontier_after_utc>=publication.incremental_frontier_before_utc)
                    OR (@mode<>N'INCREMENTAL' AND publication.incremental_frontier_before_utc IS NULL
                    AND publication.incremental_frontier_after_utc IS NULL))
                AND result.candidate_rows=(SELECT COUNT_BIG(*) FROM recon.execution_candidate_application WITH(HOLDLOCK) WHERE execution_id=@execution_id AND 1=1)
                AND result.inserted_rows=(SELECT COUNT_BIG(*) FROM recon.execution_candidate_application WITH(HOLDLOCK) WHERE execution_id=@execution_id AND application_disposition=N'INSERTED')
                AND result.updated_rows=(SELECT COUNT_BIG(*) FROM recon.execution_candidate_application WITH(HOLDLOCK) WHERE execution_id=@execution_id AND application_disposition=N'UPDATED')
                AND result.reactivated_rows=(SELECT COUNT_BIG(*) FROM recon.execution_candidate_application WITH(HOLDLOCK) WHERE execution_id=@execution_id AND application_disposition=N'REACTIVATED')
                AND result.noop_rows=(SELECT COUNT_BIG(*) FROM recon.execution_candidate_application WITH(HOLDLOCK) WHERE execution_id=@execution_id AND application_disposition IN(N'NO_OP',N'STALE_NO_OP'))
                AND result.stale_noop_rows=(SELECT COUNT_BIG(*) FROM recon.execution_candidate_application WITH(HOLDLOCK) WHERE execution_id=@execution_id AND application_disposition=N'STALE_NO_OP')
                AND EXISTS(SELECT 1 FROM ctl.execution_state_event WITH(HOLDLOCK) WHERE execution_id=@execution_id
                    AND transition_sequence=@next_transition_sequence-2 AND previous_state=N'PROMOTED'
                    AND next_state=N'RECONCILED' AND reason_code=N'CANDIDATE_SET_RECONCILED')
                AND EXISTS(SELECT 1 FROM ctl.execution_state_event WITH(HOLDLOCK) WHERE execution_id=@execution_id
                    AND transition_sequence=@next_transition_sequence-1 AND previous_state=N'RECONCILED'
                    AND next_state=N'PUBLISHED' AND reason_code=N'RECONCILIATION_PUBLISHED')
                AND EXISTS(SELECT 1 FROM recon.execution_data_quality_evaluation e WITH(HOLDLOCK)
                    WHERE e.execution_id=@execution_id AND e.expected_checks=4 AND e.completed_checks=4
                    AND e.passed_checks=4 AND e.failed_checks=0 AND e.evaluated_at_utc<=publication.published_at_utc
                    AND e.candidate_rows=result.candidate_rows AND e.promotion_recorded_at_utc=candidate_set.promoted_at_utc
                    AND (SELECT COUNT_BIG(*) FROM recon.execution_data_quality_check_result r WITH(HOLDLOCK)
                        WHERE r.execution_id=@execution_id AND r.check_state=N'PASSED')=4)
            ) SET @reason=N'PUBLISHED';
        END
        ELSE IF EXISTS(SELECT 1 FROM ctl.execution_publication_event WITH(HOLDLOCK) WHERE execution_id=@execution_id)
            OR EXISTS(SELECT 1 FROM recon.execution_reconciliation_result WITH(HOLDLOCK) WHERE execution_id=@execution_id)
            SET @reason=N'INCONSISTENT';
        ELSE IF @execution_state IN(N'FAILED',N'CANCELLED',N'BLOCKED',N'SKIPPED',N'NOT_APPLICABLE',N'DEGRADED') SET @reason=N'TERMINAL';
        ELSE IF @stored_hash IS NOT NULL AND @contract_verified=0 SET @reason=N'INCONSISTENT';
        ELSE IF @lease_valid=0 SET @reason=N'LEASE_LOST';
        ELSE IF @contract_verified=0 SET @reason=CASE WHEN @execution_state=N'EXTRACTING' AND @rows=0
            THEN N'IN_PROGRESS' WHEN @execution_state=N'EXTRACTING' THEN N'PARTIAL_EXTRACTION' ELSE N'EVIDENCE_MISSING' END;
        ELSE IF @quality=N'FAILED' SET @reason=N'DQ_FAILED';
        ELSE IF @quality=N'OBSOLETE' SET @reason=N'DQ_OBSOLETE';
        ELSE IF @execution_state=N'PROMOTED' AND @typed_valid=0 SET @reason=N'INCONSISTENT';
        ELSE IF @execution_state IN(N'EXTRACTING',N'EXTRACTED',N'STAGED',N'PROMOTED') SET @reason=N'ELIGIBLE';
        DECLARE @revision CHAR(64)=LOWER(CONVERT(CHAR(64),HASHBYTES('SHA2_256',CONCAT(
            @identity,N'|',@execution_state,N'|',@next_transition_sequence,N'|',@lease_id,N'|',
            CONVERT(NVARCHAR(23),@expires,126),N'|',@stored_hash,N'|',@evaluation_hash,N'|',@candidates)),2));
        IF @operation=N'RESUME'
        BEGIN
            IF @expected_revision IS NULL OR @expected_revision COLLATE Latin1_General_100_BIN2<>@revision
                OR DATALENGTH(@expected_revision)<>128 THROW 52305,N'RUNTIME_RECOVERY_STATE_CHANGED',1;
            IF @reason<>N'ELIGIBLE' THROW 52306,N'RUNTIME_RECOVERY_CONTINUATION_REJECTED',1;
            IF @execution_state=N'EXTRACTING'
            BEGIN
                EXEC ctl.usp_control_plane_transition_execution @execution_id,N'EXTRACTING',N'EXTRACTED',N'TRAVERSAL_AUDITED',@now;
                SET @execution_state=N'EXTRACTED';
            END;
            IF @execution_state=N'EXTRACTED'
            BEGIN
                EXEC ctl.usp_control_plane_transition_execution @execution_id,N'EXTRACTED',N'STAGED',N'STAGING_COMPLETED',@now;
                SET @execution_state=N'STAGED';
            END;
            IF @execution_state=N'STAGED'
            BEGIN
                IF @entity=N'fretes' EXEC core.usp_prepare_frete_candidate_set @execution_id,@contract_version,
                    @contract_fingerprint,@configuration_version,@configuration_fingerprint;
                ELSE EXEC core.usp_prepare_staged_execution @execution_id,@contract_version,
                    @contract_fingerprint,@configuration_version,@configuration_fingerprint;
            END;
            -- Entry points existentes revalidam candidates, checks, policy/SLA, lease e publicação.
            -- Sem INSERT EXEC aninhado. O adapter drena resultados antes da leitura de confirmação.
            EXEC recon.usp_evaluate_execution_data_quality @execution_id,@policy_version,@policy_fingerprint;
            IF @entity=N'coletas' EXEC core.usp_apply_reconcile_publish_coletas @execution_id,@contract_version,
                @contract_fingerprint,@configuration_version,@configuration_fingerprint;
            ELSE EXEC core.usp_apply_reconcile_publish_fretes @execution_id,@contract_version,
                @contract_fingerprint,@configuration_version,@configuration_fingerprint;
            COMMIT TRANSACTION;
            RETURN;
        END;
        SELECT @reason AS reason,@execution_state AS execution_state,@lease_valid AS lease_valid,
            @contract_verified AS contract_verified,@candidates AS candidate_rows,@quality AS quality,@revision AS revision,
            @execution_id AS execution_id,COALESCE(typed.inserted_rows,result.inserted_rows) AS inserted_rows,
            COALESCE(typed.updated_rows,result.updated_rows) AS updated_rows,
            COALESCE(typed.reactivated_rows,result.reactivated_rows) AS reactivated_rows,
            COALESCE(typed.noop_rows,result.noop_rows) AS noop_rows,
            COALESCE(typed.stale_noop_rows,result.stale_noop_rows) AS stale_noop_rows,result.reconciled_at_utc,result.published_at_utc,
            publication.incremental_frontier_before_utc,publication.incremental_frontier_after_utc
        FROM (VALUES(1)) anchor(value)
        LEFT JOIN recon.execution_reconciliation_result result ON result.execution_id=@execution_id AND @reason=N'PUBLISHED'
        LEFT JOIN ctl.execution_publication_event publication ON publication.execution_id=result.execution_id
        LEFT JOIN ctl.runtime_coleta_publication_receipt typed ON typed.execution_id=result.execution_id AND @entity=N'coletas';
        COMMIT TRANSACTION;
    END TRY
    BEGIN CATCH
        IF XACT_STATE()<>0 ROLLBACK TRANSACTION;
        THROW;
    END CATCH;
END;
GO

-- Evolução aditiva do entrypoint V010; regras COL-03 e SQL tipado preservados.
CREATE OR ALTER PROCEDURE core.usp_apply_reconcile_publish_coletas
    @execution_id UNIQUEIDENTIFIER,
    @contract_version NVARCHAR(MAX),
    @contract_fingerprint NVARCHAR(MAX),
    @configuration_version NVARCHAR(MAX),
    @configuration_fingerprint NVARCHAR(MAX)
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    BEGIN TRANSACTION;

    DECLARE @environment_name NVARCHAR(32), @source_instance NVARCHAR(128),
            @tenant_scope NVARCHAR(128), @entity_name NVARCHAR(128);
    -- P02R BEGIN LOCK: mesma ordem application lock -> row fence do recovery/kernel.
    SELECT @environment_name=p.environment_name,@source_instance=p.source_instance,
        @tenant_scope=p.tenant_scope,@entity_name=p.entity_name
    FROM ctl.execution_attempt a JOIN ctl.execution_partition p ON p.partition_id=a.partition_id
    WHERE a.execution_id=@execution_id;
    DECLARE @runtime_lock_payload NVARCHAR(2000)=CONCAT(DATALENGTH(@environment_name),N':',@environment_name,N'|',
        DATALENGTH(@source_instance),N':',@source_instance,N'|',DATALENGTH(@tenant_scope),N':',@tenant_scope,N'|',
        DATALENGTH(@entity_name),N':',@entity_name);
    DECLARE @runtime_lock_resource NVARCHAR(255)=N'V2_APPLY_'+CONVERT(NVARCHAR(64),HASHBYTES('SHA2_256',@runtime_lock_payload),2);
    DECLARE @runtime_lock INT;
    EXEC @runtime_lock=sys.sp_getapplock @Resource=@runtime_lock_resource,@LockMode=N'Exclusive',
        @LockOwner=N'Transaction',@LockTimeout=10000,@DbPrincipal=N'public';
    IF @runtime_lock<0 THROW 52312,N'RUNTIME_COLETA_LOCK_UNAVAILABLE',1;
    -- P02R END LOCK

    SELECT @environment_name = partition.environment_name,
           @source_instance = partition.source_instance,
           @tenant_scope = partition.tenant_scope,
           @entity_name = partition.entity_name
    FROM ctl.execution_attempt AS attempt WITH (UPDLOCK, HOLDLOCK)
    INNER JOIN ctl.execution_partition AS partition WITH (UPDLOCK, HOLDLOCK)
        ON partition.partition_id = attempt.partition_id
    WHERE attempt.execution_id = @execution_id;
    IF @entity_name COLLATE Latin1_General_100_BIN2 <> N'coletas'
        THROW 51811, N'A aplicação não pertence à vertical de Coletas.', 1;
    IF NOT EXISTS (SELECT 1 FROM ctl.coleta_promotion_result WITH (UPDLOCK, HOLDLOCK)
                   WHERE execution_id = @execution_id AND validation_state = N'PASSED')
        THROW 51812, N'Candidate set tipado de Coletas não está apto.', 1;
    -- P02R BEGIN READBACK: nunca recompõe COL-03 a partir do current alterado.
    IF EXISTS(SELECT 1 FROM ctl.execution_attempt WHERE execution_id=@execution_id AND current_state=N'PUBLISHED')
    BEGIN
        IF NOT EXISTS(SELECT 1 FROM ctl.runtime_coleta_publication_receipt WITH(HOLDLOCK) WHERE execution_id=@execution_id
            AND receipt_hash=ctl.fn_runtime_coleta_publication_hash(@execution_id))
            THROW 52313,N'RUNTIME_COLETA_HISTORICAL_RECEIPT_REQUIRED',1;
        DECLARE @prior_generic TABLE(execution_id UNIQUEIDENTIFIER,candidate_rows BIGINT,inserted_rows BIGINT,
            updated_rows BIGINT,reactivated_rows BIGINT,noop_rows BIGINT,stale_noop_rows BIGINT,
            reconciled_at_utc DATETIME2(3),published_at_utc DATETIME2(3),
            incremental_frontier_before_utc DATETIME2(3),incremental_frontier_after_utc DATETIME2(3));
        INSERT @prior_generic EXEC core.usp_apply_reconcile_publish_execution @execution_id,@contract_version,
            @contract_fingerprint,@configuration_version,@configuration_fingerprint;
        COMMIT TRANSACTION;
        SELECT r.execution_id,r.candidate_rows,r.inserted_rows,r.updated_rows,r.reactivated_rows,
            r.noop_rows,r.stale_noop_rows,r.reconciled_at_utc,r.published_at_utc,
            r.incremental_frontier_before_utc,r.incremental_frontier_after_utc
        FROM ctl.runtime_coleta_publication_receipt r WHERE r.execution_id=@execution_id;
        RETURN;
    END;
    -- P02R END READBACK
    IF EXISTS (
        SELECT 1 FROM stg.execution_candidate AS candidate
        INNER JOIN stg.coleta_record AS typed ON typed.stage_record_id = candidate.winner_stage_record_id
        INNER JOIN core.coleta AS current_record WITH (UPDLOCK, HOLDLOCK, INDEX(UQ_core_coleta_source))
          ON current_record.environment_name = @environment_name
         AND current_record.source_instance = @source_instance
         AND current_record.tenant_scope = @tenant_scope
         AND current_record.entity_name = @entity_name
         AND current_record.source_key = typed.source_key
        WHERE candidate.execution_id = @execution_id AND typed.sequence_code_presence = N'VALUE'
          AND current_record.sequence_code_presence = N'VALUE'
          AND current_record.sequence_code_json <> typed.sequence_code_json
    )
        THROW 51813, N'Alteração de alias exige evidência aprovada.', 1;

    DECLARE @common_result TABLE (
        execution_id UNIQUEIDENTIFIER NOT NULL, candidate_rows BIGINT NOT NULL,
        inserted_rows BIGINT NOT NULL, updated_rows BIGINT NOT NULL, reactivated_rows BIGINT NOT NULL,
        noop_rows BIGINT NOT NULL, stale_noop_rows BIGINT NOT NULL,
        reconciled_at_utc DATETIME2(3) NOT NULL, published_at_utc DATETIME2(3) NOT NULL,
        incremental_frontier_before_utc DATETIME2(3) NULL, incremental_frontier_after_utc DATETIME2(3) NULL
    );
    INSERT INTO @common_result
    EXEC core.usp_apply_reconcile_publish_execution @execution_id, @contract_version,
         @contract_fingerprint, @configuration_version, @configuration_fingerprint;

    DECLARE @now_utc DATETIME2(3) = SYSUTCDATETIME();
    DECLARE @plan TABLE (
        source_key NVARCHAR(256) COLLATE Latin1_General_100_BIN2 NOT NULL PRIMARY KEY,
        record_state_id BIGINT NOT NULL, coleta_id BIGINT NULL, stage_record_id BIGINT NOT NULL,
        application_disposition NVARCHAR(24) COLLATE Latin1_General_100_BIN2 NOT NULL,
        apply_values BIT NOT NULL
    );
    INSERT INTO @plan (source_key, record_state_id, coleta_id, stage_record_id,
                       application_disposition, apply_values)
    SELECT candidate.source_key, generic_application.record_state_id, current_record.coleta_id,
           typed.stage_record_id,
           CASE WHEN current_record.coleta_id IS NULL THEN N'INSERTED'
                WHEN current_record.terminal = 0 AND typed.terminal = 1 THEN N'UPDATED'
                WHEN generic_application.application_disposition = N'STALE_NO_OP' THEN N'STALE_NO_OP'
                WHEN current_record.terminal = 1 AND typed.terminal = 0 THEN N'NO_OP'
                WHEN current_record.attribute_hash = typed.attribute_hash THEN N'NO_OP'
                ELSE N'UPDATED' END,
           CASE WHEN current_record.coleta_id IS NULL THEN 1
                WHEN current_record.terminal = 0 AND typed.terminal = 1 THEN 1
                WHEN generic_application.application_disposition = N'STALE_NO_OP' THEN 0
                WHEN current_record.terminal = 1 AND typed.terminal = 0 THEN 0
                WHEN current_record.attribute_hash = typed.attribute_hash THEN 0 ELSE 1 END
    FROM stg.execution_candidate AS candidate
    INNER JOIN stg.coleta_record AS typed ON typed.stage_record_id = candidate.winner_stage_record_id
    INNER JOIN recon.execution_candidate_application AS generic_application
      ON generic_application.execution_id = candidate.execution_id
     AND generic_application.source_key = candidate.source_key
    LEFT JOIN core.coleta AS current_record WITH (UPDLOCK, HOLDLOCK, INDEX(UQ_core_coleta_source))
      ON current_record.environment_name = @environment_name
     AND current_record.source_instance = @source_instance
     AND current_record.tenant_scope = @tenant_scope
     AND current_record.entity_name = @entity_name
     AND current_record.source_key = candidate.source_key
    WHERE candidate.execution_id = @execution_id;
    IF (SELECT COUNT_BIG(*) FROM @plan) <> (SELECT candidate_rows FROM @common_result)
        THROW 51814, N'O plano tipado não cobre todo o candidate set de Coletas.', 1;

    INSERT INTO core.coleta (
        record_state_id, environment_name, source_instance, tenant_scope, entity_name, source_key,
        source_key_wire_type, sequence_code_presence, sequence_code_json, payload_json,
        field_presence_json, relation_candidates_json, status_raw, status_code, status_label,
        status_catalog_version, terminal, occurrence_action, attempt_count, freshness_raw,
        freshness_at_utc, freshness_origin, attribute_fingerprint_version, attribute_hash,
        state_fingerprint_version, state_hash, active, first_seen_execution_id, last_seen_execution_id,
        last_changed_execution_id, first_seen_at_utc, last_seen_at_utc, last_changed_at_utc
    )
    SELECT planned.record_state_id, @environment_name, @source_instance, @tenant_scope, @entity_name,
           typed.source_key, typed.source_key_wire_type, typed.sequence_code_presence,
           typed.sequence_code_json, typed.payload_json, typed.field_presence_json,
           typed.relation_candidates_json, typed.status_raw, typed.status_code, typed.status_label,
           typed.status_catalog_version, typed.terminal, typed.occurrence_action, typed.attempt_count,
           typed.freshness_raw, typed.freshness_at_utc, typed.freshness_origin,
           typed.attribute_fingerprint_version, typed.attribute_hash, N'coletas-state-v1',
            LOWER(CONVERT(CHAR(64), HASHBYTES('SHA2_256', CONVERT(VARBINARY(MAX), CONCAT(
                N'coletas-state-v1|', typed.attribute_hash, N'|active=1'
            ))), 2)), 1, @execution_id, @execution_id, @execution_id, @now_utc, @now_utc, @now_utc
    FROM @plan AS planned INNER JOIN stg.coleta_record AS typed
      ON typed.stage_record_id = planned.stage_record_id
    WHERE planned.application_disposition = N'INSERTED';

    UPDATE current_record
    SET sequence_code_presence = CASE WHEN typed.sequence_code_presence = N'ABSENT'
                                      THEN current_record.sequence_code_presence
                                      ELSE typed.sequence_code_presence END,
        sequence_code_json = CASE WHEN typed.sequence_code_presence = N'ABSENT'
                                  THEN current_record.sequence_code_json
                                  ELSE typed.sequence_code_json END,
        payload_json = typed.payload_json,
        field_presence_json = typed.field_presence_json, relation_candidates_json = typed.relation_candidates_json,
        status_raw = CASE WHEN JSON_VALUE(typed.field_presence_json, N'$.status') = N'ABSENT'
                          THEN current_record.status_raw ELSE typed.status_raw END,
        status_code = CASE WHEN JSON_VALUE(typed.field_presence_json, N'$.status') = N'ABSENT'
                           THEN current_record.status_code ELSE typed.status_code END,
        status_label = CASE WHEN JSON_VALUE(typed.field_presence_json, N'$.status') = N'ABSENT'
                            THEN current_record.status_label ELSE typed.status_label END,
        status_catalog_version = CASE WHEN JSON_VALUE(typed.field_presence_json, N'$.status') = N'ABSENT'
                                      THEN current_record.status_catalog_version
                                      ELSE typed.status_catalog_version END,
        terminal = CASE WHEN JSON_VALUE(typed.field_presence_json, N'$.status') = N'ABSENT'
                        THEN current_record.terminal ELSE typed.terminal END,
        occurrence_action = CASE WHEN JSON_VALUE(typed.field_presence_json, N'$.status') = N'ABSENT'
                                 THEN current_record.occurrence_action ELSE typed.occurrence_action END,
        attempt_count = CASE WHEN JSON_VALUE(typed.field_presence_json, N'$.status') = N'ABSENT'
                             THEN current_record.attempt_count ELSE typed.attempt_count END,
        freshness_raw = COALESCE(typed.freshness_raw, current_record.freshness_raw),
        freshness_at_utc = CASE WHEN typed.freshness_origin = N'UNAVAILABLE'
                                THEN current_record.freshness_at_utc ELSE typed.freshness_at_utc END,
        freshness_origin = CASE WHEN typed.freshness_origin = N'UNAVAILABLE'
                                THEN current_record.freshness_origin ELSE typed.freshness_origin END,
        attribute_fingerprint_version = typed.attribute_fingerprint_version,
        attribute_hash = typed.attribute_hash,
        state_hash = LOWER(CONVERT(CHAR(64), HASHBYTES('SHA2_256', CONVERT(VARBINARY(MAX), CONCAT(
            N'coletas-state-v1|', typed.attribute_hash, N'|active=1'
        ))), 2)), last_seen_execution_id = @execution_id, last_changed_execution_id = @execution_id,
        last_seen_at_utc = @now_utc, last_changed_at_utc = @now_utc
    FROM core.coleta AS current_record INNER JOIN @plan AS planned
      ON planned.coleta_id = current_record.coleta_id
    INNER JOIN stg.coleta_record AS typed ON typed.stage_record_id = planned.stage_record_id
    WHERE planned.application_disposition = N'UPDATED' AND planned.apply_values = 1;

    UPDATE current_record SET last_seen_execution_id = @execution_id, last_seen_at_utc = @now_utc
    FROM core.coleta AS current_record INNER JOIN @plan AS planned
      ON planned.coleta_id = current_record.coleta_id
    WHERE planned.application_disposition IN (N'NO_OP', N'STALE_NO_OP');

    UPDATE planned SET coleta_id = current_record.coleta_id
    FROM @plan AS planned INNER JOIN core.coleta AS current_record
      ON current_record.environment_name = @environment_name
     AND current_record.source_instance = @source_instance
     AND current_record.tenant_scope = @tenant_scope
     AND current_record.entity_name = @entity_name AND current_record.source_key = planned.source_key;
    IF EXISTS (SELECT 1 FROM @plan WHERE coleta_id IS NULL)
        THROW 51815, N'A aplicação não resolveu canonical_id de Coletas.', 1;

    INSERT INTO ref.coleta_sequence_code_alias (
        coleta_id, sequence_code_json, observed_execution_id, valid_from_utc, valid_to_utc, provenance
    )
    SELECT planned.coleta_id, typed.sequence_code_json, @execution_id, @now_utc, NULL,
           N'dataexport-6908-v02'
    FROM @plan AS planned INNER JOIN stg.coleta_record AS typed
      ON typed.stage_record_id = planned.stage_record_id
    WHERE typed.sequence_code_presence = N'VALUE'
      AND NOT EXISTS (SELECT 1 FROM ref.coleta_sequence_code_alias AS alias
                      WHERE alias.coleta_id = planned.coleta_id AND alias.valid_to_utc IS NULL);

    INSERT INTO recon.coleta_root_presence_observation (
        execution_id, coleta_id, observed_at_utc, snapshot_completeness, absence_evaluation
    )
    SELECT @execution_id, coleta_id, @now_utc, N'BLOCKED_NO_COMPLETENESS_PROOF', N'NOT_EVALUATED'
    FROM @plan;

    DECLARE @inserted_rows BIGINT = (SELECT COUNT_BIG(*) FROM @plan WHERE application_disposition = N'INSERTED');
    DECLARE @updated_rows BIGINT = (SELECT COUNT_BIG(*) FROM @plan WHERE application_disposition = N'UPDATED');
    DECLARE @noop_rows BIGINT = (SELECT COUNT_BIG(*) FROM @plan WHERE application_disposition IN (N'NO_OP', N'STALE_NO_OP'));
    DECLARE @stale_noop_rows BIGINT = (SELECT COUNT_BIG(*) FROM @plan WHERE application_disposition = N'STALE_NO_OP');
    -- P02R BEGIN RECEIPT: mesma transação dos efeitos tipados, antes de qualquer ack.
    INSERT ctl.runtime_coleta_publication_receipt
    SELECT r.*,LOWER(CONVERT(CHAR(64),HASHBYTES('SHA2_256',CONCAT(
        ctl.fn_runtime_recovery_identity(r.execution_id),N'|',r.candidate_rows,N'|',
        r.inserted_rows,N'|',r.updated_rows,N'|',r.reactivated_rows,N'|',
        r.noop_rows,N'|',r.stale_noop_rows,N'|',
        CONVERT(NVARCHAR(23),r.reconciled_at_utc,126),N'|',CONVERT(NVARCHAR(23),r.published_at_utc,126),N'|',
        CONVERT(NVARCHAR(23),r.incremental_frontier_before_utc,126),N'|',
        CONVERT(NVARCHAR(23),r.incremental_frontier_after_utc,126))),2))
    FROM (SELECT @execution_id AS execution_id,candidate_rows,@inserted_rows AS inserted_rows,
        @updated_rows AS updated_rows,CAST(0 AS BIGINT) AS reactivated_rows,@noop_rows AS noop_rows,
        @stale_noop_rows AS stale_noop_rows,reconciled_at_utc,published_at_utc,
        incremental_frontier_before_utc,incremental_frontier_after_utc FROM @common_result) r;
    -- P02R END RECEIPT
    COMMIT TRANSACTION;
    SELECT @execution_id AS execution_id, (SELECT candidate_rows FROM @common_result) AS candidate_rows,
           @inserted_rows AS inserted_rows, @updated_rows AS updated_rows,
           CAST(0 AS BIGINT) AS reactivated_rows, @noop_rows AS noop_rows,
           @stale_noop_rows AS stale_noop_rows, reconciled_at_utc, published_at_utc,
           incremental_frontier_before_utc, incremental_frontier_after_utc FROM @common_result;
END;
GO
