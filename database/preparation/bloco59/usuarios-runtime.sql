-- B59 PREPARACAO_NAO_EXECUTADA. Not a migration and not an authorization to run SQL.
-- Derived from V022; Users SQL current/history remains V007, without algorithm duplication.
-- Qualify physical target/state/authority/grants, migration numbering and rollback separately.
:On Error exit
IF DB_NAME()<>N'ETL_SISTEMA_V2_SHADOW' THROW 52958,N'USERS_DRAFT_SHADOW_TARGET_REQUIRED',1;
GO
CREATE OR ALTER PROCEDURE ctl.usp_runtime_recovery
    @execution_id UNIQUEIDENTIFIER,
    @operation NVARCHAR(MAX),
    @expected_identity NVARCHAR(MAX),
    @contract_material NVARCHAR(MAX),
    @policy_version NVARCHAR(MAX),
    @policy_fingerprint NVARCHAR(MAX),
    @expected_revision NVARCHAR(MAX),
    @entity NVARCHAR(MAX),@reference_release_id BIGINT=NULL
AS
BEGIN
    -- B54: validate current SQL authority consumption before any effect/read.
    DECLARE @b54_actual NVARCHAR(MAX)=(SELECT LOWER(COALESCE(CONVERT(NVARCHAR(36),@execution_id),N'')) AS [execution] FOR JSON PATH,WITHOUT_ARRAY_WRAPPER,INCLUDE_NULL_VALUES);
    IF ctl.fn_runtime_consumed_scope(@b54_actual,CASE WHEN @operation=N'READ' THEN 0 ELSE 1 END,NULL)<>1 THROW 52500,N'RUNTIME_DURABLE_CONSUMER_FENCE_REQUIRED',1;

    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    IF @operation IS NULL OR @operation COLLATE Latin1_General_100_BIN2 NOT IN(N'READ',N'SEAL',N'RESUME')
        OR @expected_identity IS NULL OR DATALENGTH(@expected_identity) NOT BETWEEN 2 AND 8000
        OR @contract_material IS NULL OR DATALENGTH(@contract_material) NOT BETWEEN 2 AND 8000
        OR @policy_version IS NULL OR DATALENGTH(@policy_version) NOT BETWEEN 2 AND 256
        OR @policy_fingerprint IS NULL OR DATALENGTH(@policy_fingerprint)<>128
        OR @policy_fingerprint COLLATE Latin1_General_100_BIN2 LIKE N'%[^0-9a-f]%'
        OR @entity IS NULL OR @entity COLLATE Latin1_General_100_BIN2 NOT IN(N'coletas',N'fretes',N'manifestos',N'cotacoes',N'localizacao_cargas',N'usuarios')
        THROW 52301,N'RUNTIME_RECOVERY_INPUT_INVALID',1;
    IF @entity=N'cotacoes' AND ctl.fn_runtime_tariff_valid(@execution_id,@reference_release_id)<>1
       THROW 52843,N'TARIFF_SCOPE_OR_REFERENCE_REJECTED',1;
    IF @entity<>N'cotacoes' AND @reference_release_id IS NOT NULL THROW 52843,N'TARIFF_ENTITY_MISMATCH',1;
    IF @entity=N'usuarios' AND @contract_material COLLATE Latin1_General_100_BIN2
        NOT LIKE N'runtime-recovery-v1|14:GRAPHQL|44:graphql-users-snapshot|%'
        THROW 52959,N'USERS_RECOVERY_OPERATION_MISMATCH',1;
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
        -- Users has populated local terminality. It never inherits the DE empty-page proof.
        IF @entity=N'usuarios'
            SELECT @terminal=SUM(CASE WHEN terminal_evidence_kind=N'GRAPHQL_PAGE_INFO'
                    AND terminal_empty_page=0 AND physical_rows BETWEEN 1 AND 20 THEN 1 ELSE 0 END),
                @terminal_page=MAX(CASE WHEN terminal_evidence_kind=N'GRAPHQL_PAGE_INFO' THEN page_number END)
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
        IF @entity=N'usuarios'
        BEGIN
            SET @audit_valid=CASE WHEN @pages>=1 AND @rows>0 AND @pages=@unique_pages
                AND @pages=@last_page AND @first_page=1 AND @terminal=1 AND @terminal_page=@last_page
                AND NOT EXISTS(SELECT 1 FROM ctl.execution_page_audit WITH(HOLDLOCK)
                    WHERE execution_id=@execution_id AND (requested_page_size<>20 OR page_attempt<>1
                        OR physical_rows NOT BETWEEN 1 AND 20 OR terminal_empty_page<>0
                        OR terminal_evidence_kind NOT IN(N'NONE',N'GRAPHQL_PAGE_INFO')))
                THEN 1 ELSE 0 END;
            IF @attempt_exists=1 AND (@mode NOT IN(N'BACKFILL',N'REPLAY') OR NOT EXISTS(
                SELECT 1 FROM ctl.execution_attempt a WITH(HOLDLOCK)
                JOIN ctl.source_catalog s WITH(HOLDLOCK) ON s.source_instance=@source_instance
                WHERE a.execution_id=@execution_id AND a.window_strategy=N'FULL' AND s.source_kind=N'GRAPHQL'))
                SET @identity_valid=0;
        END;
        IF @operation=N'SEAL' AND @entity<>N'usuarios'
        BEGIN
            IF @identity_valid=0 OR @execution_state<>N'EXTRACTING' OR @lease_valid=0 OR @audit_valid=0
                OR @rows<>CASE WHEN @entity=N'manifestos' THEN
                   (SELECT COUNT_BIG(*) FROM stg.manifesto_observation WITH(HOLDLOCK) WHERE execution_id=@execution_id)
                   ELSE (SELECT COUNT_BIG(*) FROM stg.execution_record WITH(HOLDLOCK) WHERE execution_id=@execution_id) END
                THROW 52303,N'RUNTIME_RECOVERY_SEAL_GATE_REJECTED',1;
            IF EXISTS(SELECT 1 FROM ctl.runtime_contract_evidence WITH(UPDLOCK,HOLDLOCK)
                WHERE execution_id=@execution_id AND evidence_hash<>@evidence_hash)
                THROW 52304,N'RUNTIME_RECOVERY_EVIDENCE_CONFLICT',1;
            IF NOT EXISTS(SELECT 1 FROM ctl.runtime_contract_evidence WITH(UPDLOCK,HOLDLOCK) WHERE execution_id=@execution_id)
                INSERT ctl.runtime_contract_evidence VALUES(@execution_id,@identity,@contract_material,
                    @policy_version,@policy_fingerprint,@rows,@pages,@audit_hash,@evidence_hash,@now);
            IF @entity=N'cotacoes' AND NOT EXISTS(SELECT 1 FROM ctl.runtime_cotacao_reference WITH(UPDLOCK,HOLDLOCK) WHERE execution_id=@execution_id)
              INSERT ctl.runtime_cotacao_reference(execution_id,reference_release_id,reference_fingerprint,bound_at_utc)
              SELECT @execution_id,@reference_release_id,source_fingerprint,@now FROM ref.reference_release WHERE reference_release_id=@reference_release_id;
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
            OR EXISTS(SELECT 1 FROM ctl.cotacao_promotion_result WITH(HOLDLOCK) WHERE execution_id=@execution_id
              AND @entity=N'cotacoes' AND validation_state=N'PASSED' AND typed_candidate_rows=@candidates)
            OR EXISTS(SELECT 1 FROM ctl.localizacao_carga_promotion_result WITH(HOLDLOCK) WHERE execution_id=@execution_id
              AND @entity=N'localizacao_cargas' AND validation_state=N'PASSED' AND typed_candidate_rows=@candidates)
            OR EXISTS(SELECT 1 FROM ctl.manifesto_promotion_result WITH(HOLDLOCK) WHERE execution_id=@execution_id
              AND @entity=N'manifestos' AND validation_state=N'PASSED' AND reduced_root_rows=@candidates)
            OR EXISTS(SELECT 1 FROM ctl.usuario_promotion_result WITH(HOLDLOCK) WHERE execution_id=@execution_id
              AND @entity=N'usuarios' AND validation_state=N'PASSED' AND typed_candidate_rows=@candidates
              AND generic_candidate_rows=@candidates AND conflicting_root_keys=0 AND generic_quarantine_rows=0)
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
        -- Users seals only after the same occurrence's typed candidate and complete DQ pass.
        IF @operation=N'SEAL' AND @entity=N'usuarios'
        BEGIN
            IF @identity_valid=0 OR @execution_state<>N'PROMOTED' OR @lease_valid=0 OR @audit_valid=0
                OR @typed_valid=0 OR @quality<>N'PASSED' OR @dq_integrity=0
                OR @rows<>(SELECT COUNT_BIG(*) FROM stg.execution_record WITH(HOLDLOCK) WHERE execution_id=@execution_id)
                OR @rows<>(SELECT COUNT_BIG(*) FROM stg.usuario_record WITH(HOLDLOCK) WHERE execution_id=@execution_id)
                OR NOT EXISTS(SELECT 1 FROM recon.execution_data_quality_evaluation e WITH(HOLDLOCK)
                    JOIN ctl.execution_promotion_result p WITH(HOLDLOCK) ON p.execution_id=e.execution_id
                    WHERE e.execution_id=@execution_id AND e.expected_checks=4 AND e.completed_checks=4
                        AND e.passed_checks=4 AND e.failed_checks=0 AND e.failed_rows=0
                        AND e.candidate_rows=@candidates AND e.promotion_recorded_at_utc=p.promoted_at_utc
                        AND e.evaluated_at_utc<=@now)
                THROW 52960,N'USERS_RECOVERY_SEAL_GATE_REJECTED',1;
            IF EXISTS(SELECT 1 FROM ctl.runtime_contract_evidence WITH(UPDLOCK,HOLDLOCK)
                WHERE execution_id=@execution_id AND evidence_hash<>@evidence_hash)
                THROW 52304,N'RUNTIME_RECOVERY_EVIDENCE_CONFLICT',1;
            IF NOT EXISTS(SELECT 1 FROM ctl.runtime_contract_evidence WITH(UPDLOCK,HOLDLOCK) WHERE execution_id=@execution_id)
                INSERT ctl.runtime_contract_evidence VALUES(@execution_id,@identity,@contract_material,
                    @policy_version,@policy_fingerprint,@rows,@pages,@audit_hash,@evidence_hash,@now);
            COMMIT TRANSACTION;
            RETURN;
        END;
        DECLARE @users_receipt_valid BIT=0;
        IF @entity=N'usuarios' AND EXISTS(
            SELECT 1 FROM recon.usuario_reconciliation_result u WITH(HOLDLOCK)
            JOIN recon.usuario_apply_authorization a WITH(HOLDLOCK) ON a.execution_id=u.execution_id
            JOIN recon.execution_reconciliation_result g WITH(HOLDLOCK) ON g.execution_id=u.execution_id
            WHERE u.execution_id=@execution_id AND a.authorization_state=N'APPLIED'
                AND a.candidate_rows=@candidates AND u.candidate_rows=@candidates
                AND u.candidate_rows=g.candidate_rows AND a.applied_at_utc>=a.authorized_at_utc AND a.applied_at_utc<=u.published_at_utc
                AND u.reconciled_at_utc=g.reconciled_at_utc AND u.published_at_utc=g.published_at_utc
                AND u.candidate_rows=(SELECT COUNT_BIG(*) FROM recon.usuario_candidate_application WITH(HOLDLOCK)
                    WHERE execution_id=@execution_id AND result_active=1)
                AND u.inserted_rows=(SELECT COUNT_BIG(*) FROM recon.usuario_candidate_application WITH(HOLDLOCK)
                    WHERE execution_id=@execution_id AND application_disposition=N'INSERTED')
                AND u.updated_rows=(SELECT COUNT_BIG(*) FROM recon.usuario_candidate_application WITH(HOLDLOCK)
                    WHERE execution_id=@execution_id AND application_disposition=N'UPDATED')
                AND u.reactivated_rows=(SELECT COUNT_BIG(*) FROM recon.usuario_candidate_application WITH(HOLDLOCK)
                    WHERE execution_id=@execution_id AND application_disposition=N'REACTIVATED')
                AND u.noop_rows=(SELECT COUNT_BIG(*) FROM recon.usuario_candidate_application WITH(HOLDLOCK)
                    WHERE execution_id=@execution_id AND application_disposition IN(N'NO_OP',N'STALE_NO_OP'))
                AND u.stale_noop_rows=(SELECT COUNT_BIG(*) FROM recon.usuario_candidate_application WITH(HOLDLOCK)
                    WHERE execution_id=@execution_id AND application_disposition=N'STALE_NO_OP')
                AND u.history_rows=(SELECT COUNT_BIG(*) FROM core.usuario_history WITH(HOLDLOCK) WHERE execution_id=@execution_id)
                AND NOT EXISTS(SELECT 1 FROM recon.usuario_candidate_application c WITH(HOLDLOCK)
                    LEFT JOIN core.usuario_history h WITH(HOLDLOCK)
                        ON h.execution_id=c.execution_id AND h.usuario_id=c.usuario_id
                    WHERE c.execution_id=@execution_id AND c.application_disposition IN(N'INSERTED',N'UPDATED',N'REACTIVATED')
                        AND (h.usuario_id IS NULL OR h.change_kind<>c.application_disposition
                            OR h.state_hash<>c.result_state_hash OR h.attribute_hash<>c.result_attribute_hash
                            OR h.observation_order_at_utc<>c.observation_order_at_utc
                            OR h.observation_order_execution_id<>c.observation_order_execution_id)))
            SET @users_receipt_valid=1;
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
            AND (@entity<>N'usuarios' OR @contract_verified=1 AND @users_receipt_valid=1)
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
                ELSE IF @entity=N'manifestos' EXEC core.usp_prepare_manifesto_candidate_set @execution_id,@contract_version,
                    @contract_fingerprint,@configuration_version,@configuration_fingerprint;
                ELSE EXEC core.usp_prepare_staged_execution @execution_id,@contract_version,
                    @contract_fingerprint,@configuration_version,@configuration_fingerprint;
            END;
            -- Entry points existentes revalidam candidates, checks, policy/SLA, lease e publicação.
            -- Sem INSERT EXEC aninhado. O adapter drena resultados antes da leitura de confirmação.
            IF @entity<>N'usuarios' OR @quality<>N'PASSED'
                EXEC recon.usp_evaluate_execution_data_quality @execution_id,@policy_version,@policy_fingerprint;
            IF @entity=N'coletas' EXEC core.usp_apply_reconcile_publish_coletas @execution_id,@contract_version,
                @contract_fingerprint,@configuration_version,@configuration_fingerprint;
            ELSE IF @entity=N'manifestos' EXEC core.usp_apply_reconcile_publish_manifestos @execution_id,@contract_version,
                @contract_fingerprint,@configuration_version,@configuration_fingerprint;
            ELSE IF @entity=N'cotacoes' EXEC core.usp_apply_reconcile_publish_cotacoes @execution_id,@contract_version,
                @contract_fingerprint,@configuration_version,@configuration_fingerprint,@reference_release_id;
            ELSE IF @entity=N'localizacao_cargas' EXEC core.usp_apply_reconcile_publish_localizacao_cargas @execution_id,@contract_version,
                @contract_fingerprint,@configuration_version,@configuration_fingerprint;
            ELSE IF @entity=N'usuarios' EXEC core.usp_apply_reconcile_publish_usuarios @execution_id,@contract_version,
                @contract_fingerprint,@configuration_version,@configuration_fingerprint;
            ELSE EXEC core.usp_apply_reconcile_publish_fretes @execution_id,@contract_version,
                @contract_fingerprint,@configuration_version,@configuration_fingerprint;
            COMMIT TRANSACTION;
            RETURN;
        END;
        SELECT @reason AS reason,@execution_state AS execution_state,@lease_valid AS lease_valid,
            @contract_verified AS contract_verified,@candidates AS candidate_rows,@quality AS quality,@revision AS revision,
            @execution_id AS execution_id,COALESCE(users_receipt.inserted_rows,typed.inserted_rows,result.inserted_rows) AS inserted_rows,
            COALESCE(users_receipt.updated_rows,typed.updated_rows,result.updated_rows) AS updated_rows,
            COALESCE(users_receipt.reactivated_rows,typed.reactivated_rows,result.reactivated_rows) AS reactivated_rows,
            COALESCE(users_receipt.noop_rows,typed.noop_rows,result.noop_rows) AS noop_rows,
            COALESCE(users_receipt.stale_noop_rows,typed.stale_noop_rows,result.stale_noop_rows) AS stale_noop_rows,result.reconciled_at_utc,result.published_at_utc,
            publication.incremental_frontier_before_utc,publication.incremental_frontier_after_utc
        FROM (VALUES(1)) anchor(value)
        LEFT JOIN recon.execution_reconciliation_result result ON result.execution_id=@execution_id AND @reason=N'PUBLISHED'
        LEFT JOIN ctl.execution_publication_event publication ON publication.execution_id=result.execution_id
        LEFT JOIN ctl.runtime_coleta_publication_receipt typed ON typed.execution_id=result.execution_id AND @entity=N'coletas'
        LEFT JOIN recon.usuario_reconciliation_result users_receipt ON users_receipt.execution_id=result.execution_id AND @entity=N'usuarios';
        COMMIT TRANSACTION;
    END TRY
    BEGIN CATCH
        IF XACT_STATE()<>0 ROLLBACK TRANSACTION;
        THROW;
    END CATCH;
END;
GO
