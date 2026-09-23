-- B54: durable consumer fence. No new grants and no caller-asserted session context.
SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;
SET XACT_ABORT ON;
GO
CREATE FUNCTION ctl.fn_runtime_consumed_scope(@actual NVARCHAR(MAX), @write BIT,
    @namespace CHAR(64)=NULL) RETURNS BIT
AS
BEGIN
    -- Database administrators already own the catalog and synthetic migration validations.
    -- They remain forbidden by the runtime authority; this is not an application credential.
    IF IS_SRVROLEMEMBER(N'sysadmin')=1 OR IS_MEMBER(N'db_owner')=1 RETURN 1;
    IF ISJSON(@actual)<>1 OR DATALENGTH(@actual)>8000 RETURN 0;
    IF ORIGINAL_LOGIN()<>SUSER_SNAME() OR SUSER_SID(ORIGINAL_LOGIN())<>SUSER_SID() RETURN 0;
    IF CONNECTIONPROPERTY('auth_scheme') NOT IN(N'NTLM',N'KERBEROS') RETURN 0;
    IF EXISTS(
        SELECT 1 FROM ctl.runtime_authorization_decision d
        JOIN ctl.runtime_authorization_consumption c ON c.invocation_id=d.invocation_id
        JOIN ctl.runtime_identity_mapping m ON m.audit_reference=d.audit_reference
        JOIN ctl.runtime_authority_configuration a ON a.singleton=1 AND a.enabled=1
        JOIN ctl.runtime_identity_scope s ON s.original_sid=m.original_sid
            AND s.environment_name=JSON_VALUE(d.scope_material,'$.environment')
            AND s.source_instance=JSON_VALUE(d.scope_material,'$.source')
            AND s.tenant_scope=JSON_VALUE(d.scope_material,'$.tenant')
            AND s.workload=JSON_VALUE(d.scope_material,'$.workload')
            AND s.mode=JSON_VALUE(d.scope_material,'$.mode')
        WHERE m.original_sid=SUSER_SID(ORIGINAL_LOGIN()) AND m.revoked=0 AND s.revoked=0
            AND m.mapping_version=d.mapping_version AND s.scope_version=d.scope_version
            AND m.valid_from_utc<=SYSUTCDATETIME() AND m.valid_until_utc>SYSUTCDATETIME()
            AND d.authorized_at_utc<=SYSUTCDATETIME() AND d.valid_until_utc>SYSUTCDATETIME()
            AND d.authority_id=a.authority_id AND d.policy_fingerprint=a.policy_fingerprint
            AND s.policy_fingerprint=a.policy_fingerprint AND d.decision='ALLOW'
            AND (d.action=N'STATUS' AND @write=0 AND m.observer=1
                OR d.action=N'RUN' AND m.executor=1
                OR d.action=N'REPLAY' AND m.executor=1 AND m.replay=1
                OR d.action=N'FORCE_RUN' AND m.executor=1 AND m.force_run=1)
            AND (@namespace IS NULL OR @namespace=LOWER(CONVERT(CHAR(64),HASHBYTES('SHA2_256',
                CONCAT(JSON_VALUE(d.scope_material,'$.environment'),N'|',JSON_VALUE(d.scope_material,'$.source'),
                N'|',JSON_VALUE(d.scope_material,'$.tenant'),N'|',JSON_VALUE(d.scope_material,'$.workload'),
                N'|',JSON_VALUE(d.scope_material,'$.mode'))),2)))
            AND NOT EXISTS(SELECT 1 FROM OPENJSON(@actual) x LEFT JOIN OPENJSON(d.scope_material) y
                ON x.[key] COLLATE Latin1_General_100_BIN2=y.[key] COLLATE Latin1_General_100_BIN2
                AND DATALENGTH(x.[key])=DATALENGTH(y.[key])
                WHERE y.[key] IS NULL OR x.type<>1 OR y.type<>1
                    OR (x.[key] IN(N'start',N'endExclusive') AND
                        (TRY_CONVERT(DATETIME2(3),x.value) IS NULL
                         OR TRY_CONVERT(DATETIME2(3),x.value)<>TRY_CONVERT(DATETIME2(3),y.value)))
                    OR (x.[key] NOT IN(N'start',N'endExclusive') AND
                        (DATALENGTH(x.value)<>DATALENGTH(y.value)
                         OR x.value COLLATE Latin1_General_100_BIN2<>y.value COLLATE Latin1_General_100_BIN2))))
        RETURN 1;
    RETURN 0;
END;
GO
CREATE OR ALTER PROCEDURE core.usp_apply_reconcile_publish_coletas
    @execution_id UNIQUEIDENTIFIER,
    @contract_version NVARCHAR(MAX),
    @contract_fingerprint NVARCHAR(MAX),
    @configuration_version NVARCHAR(MAX),
    @configuration_fingerprint NVARCHAR(MAX)
AS
BEGIN
    -- B54: validate current SQL authority consumption before any effect/read.
    DECLARE @b54_actual NVARCHAR(MAX)=(SELECT LOWER(COALESCE(CONVERT(NVARCHAR(36),@execution_id),N'')) AS [execution],@contract_version AS [contractVersion],@contract_fingerprint AS [contractHash],@configuration_version AS [configurationVersion],@configuration_fingerprint AS [configurationHash] FOR JSON PATH,WITHOUT_ARRAY_WRAPPER,INCLUDE_NULL_VALUES);
    IF ctl.fn_runtime_consumed_scope(@b54_actual,1,NULL)<>1 THROW 52500,N'RUNTIME_DURABLE_CONSUMER_FENCE_REQUIRED',1;

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

CREATE OR ALTER PROCEDURE core.usp_apply_reconcile_publish_fretes
    @execution_id UNIQUEIDENTIFIER,
    @contract_version NVARCHAR(MAX),
    @contract_fingerprint NVARCHAR(MAX),
    @configuration_version NVARCHAR(MAX),
    @configuration_fingerprint NVARCHAR(MAX)
AS
BEGIN
    -- B54: validate current SQL authority consumption before any effect/read.
    DECLARE @b54_actual NVARCHAR(MAX)=(SELECT LOWER(COALESCE(CONVERT(NVARCHAR(36),@execution_id),N'')) AS [execution],@contract_version AS [contractVersion],@contract_fingerprint AS [contractHash],@configuration_version AS [configurationVersion],@configuration_fingerprint AS [configurationHash] FOR JSON PATH,WITHOUT_ARRAY_WRAPPER,INCLUDE_NULL_VALUES);
    IF ctl.fn_runtime_consumed_scope(@b54_actual,1,NULL)<>1 THROW 52500,N'RUNTIME_DURABLE_CONSUMER_FENCE_REQUIRED',1;

    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    BEGIN TRANSACTION;
    IF NOT EXISTS(
        SELECT 1 FROM ctl.frete_promotion_result WITH(UPDLOCK,HOLDLOCK)
        WHERE execution_id=@execution_id AND validation_state=N'PASSED'
    ) THROW 52140,N'Candidate set de Fretes não está apto.',1;
    DECLARE @environment_name NVARCHAR(32),@source_instance NVARCHAR(128),
            @tenant_scope NVARCHAR(128),@entity_name NVARCHAR(128);
    SELECT @environment_name=partition.environment_name,
           @source_instance=partition.source_instance,@tenant_scope=partition.tenant_scope,
           @entity_name=partition.entity_name
    FROM ctl.execution_attempt attempt WITH(UPDLOCK,HOLDLOCK)
    JOIN ctl.execution_partition partition WITH(HOLDLOCK)
      ON partition.partition_id=attempt.partition_id
    WHERE attempt.execution_id=@execution_id;
    IF @entity_name<>N'fretes' OR UPPER(@source_instance) IN(N'DEFAULT',N'GLOBAL',N'SINGLETON')
       OR UPPER(@tenant_scope) IN(N'DEFAULT',N'GLOBAL',N'SINGLETON')
        THROW 52141,N'Escopo de promoção de Fretes inválido.',1;

    -- A mesma função de decisão: antigo=no-op, igual+mesmo hash=replay,
    -- igual+hash divergente=quarentena fail-closed, novo=promoção.
    IF EXISTS(
        SELECT 1
        FROM stg.execution_candidate candidate
        JOIN stg.frete_record typed_record
          ON typed_record.stage_record_id=candidate.winner_stage_record_id
        JOIN core.frete current_record WITH(UPDLOCK,HOLDLOCK,INDEX(UQ_core_frete_source))
          ON current_record.environment_name=@environment_name
         AND current_record.source_instance=@source_instance
         AND current_record.tenant_scope=@tenant_scope
         AND current_record.entity_name=N'fretes'
         AND current_record.source_key=candidate.source_key
        WHERE candidate.execution_id=@execution_id
          AND current_record.freshness_at_utc=typed_record.freshness_at_utc
          AND current_record.attribute_hash<>typed_record.attribute_hash
    ) THROW 52142,N'EQUAL_FRESHNESS_CONFLICT: Fretes exige quarentena.',1;

    DECLARE @effective TABLE(
        source_key NVARCHAR(256) COLLATE Latin1_General_100_BIN2 NOT NULL PRIMARY KEY,
        stage_record_id BIGINT NOT NULL,
        business_alias_json NVARCHAR(MAX) NOT NULL,
        status_raw NVARCHAR(255) NULL,status_code NVARCHAR(32) NULL,status_label NVARCHAR(64) NULL,
        terminal BIT NOT NULL,cte_finalizations_json NVARCHAR(MAX) NOT NULL,
        financial_json NVARCHAR(MAX) NOT NULL,relation_candidates_json NVARCHAR(MAX) NOT NULL
    );
    INSERT @effective(
        source_key,stage_record_id,business_alias_json,status_raw,status_code,status_label,
        terminal,cte_finalizations_json,financial_json,relation_candidates_json
    )
    SELECT candidate.source_key,typed_record.stage_record_id,
      CASE JSON_VALUE(typed_record.field_presence_json,N'$.corporation_sequence_number')
        WHEN N'ABSENT' THEN COALESCE(current_record.business_alias_json,typed_record.business_alias_json)
        ELSE typed_record.business_alias_json END,
      CASE
        WHEN current_record.terminal=1 AND typed_record.terminal=0 THEN current_record.status_raw
        WHEN JSON_VALUE(typed_record.field_presence_json,N'$.status')=N'ABSENT'
          THEN current_record.status_raw ELSE typed_record.status_raw END,
      CASE
        WHEN current_record.terminal=1 AND typed_record.terminal=0 THEN current_record.status_code
        WHEN JSON_VALUE(typed_record.field_presence_json,N'$.status')=N'ABSENT'
          THEN current_record.status_code ELSE typed_record.status_code END,
      CASE
        WHEN current_record.terminal=1 AND typed_record.terminal=0 THEN current_record.status_label
        WHEN JSON_VALUE(typed_record.field_presence_json,N'$.status')=N'ABSENT'
          THEN current_record.status_label ELSE typed_record.status_label END,
      CASE
        WHEN current_record.terminal=1 THEN CONVERT(BIT,1) ELSE typed_record.terminal END,
      CASE WHEN
          JSON_VALUE(typed_record.field_presence_json,N'$.cte')=N'ABSENT'
          AND JSON_VALUE(typed_record.field_presence_json,N'$.ctes')=N'ABSENT'
          AND JSON_VALUE(typed_record.field_presence_json,N'$.cte_key')=N'ABSENT'
          AND JSON_VALUE(typed_record.field_presence_json,N'$.finalizations')=N'ABSENT'
          AND JSON_VALUE(typed_record.field_presence_json,N'$.finalizacoes')=N'ABSENT'
        THEN COALESCE(current_record.cte_finalizations_json,typed_record.cte_finalizations_json)
        ELSE typed_record.cte_finalizations_json END,
      CASE WHEN
          JSON_VALUE(typed_record.field_presence_json,N'$.reference_number')=N'ABSENT'
          AND JSON_VALUE(typed_record.field_presence_json,N'$.total')=N'ABSENT'
          AND JSON_VALUE(typed_record.field_presence_json,N'$.cte')=N'ABSENT'
          AND JSON_VALUE(typed_record.field_presence_json,N'$.cte_key')=N'ABSENT'
        THEN COALESCE(current_record.financial_json,typed_record.financial_json)
        ELSE typed_record.financial_json END,
      CASE WHEN
          JSON_VALUE(typed_record.field_presence_json,N'$.fit_p_m_pck_sequence_code')=N'ABSENT'
        THEN COALESCE(current_record.relation_candidates_json,typed_record.relation_candidates_json)
        ELSE typed_record.relation_candidates_json END
    FROM stg.execution_candidate candidate
    JOIN stg.frete_record typed_record
      ON typed_record.stage_record_id=candidate.winner_stage_record_id
    LEFT JOIN core.frete current_record WITH(UPDLOCK,HOLDLOCK,INDEX(UQ_core_frete_source))
      ON current_record.environment_name=@environment_name
     AND current_record.source_instance=@source_instance
     AND current_record.tenant_scope=@tenant_scope
     AND current_record.entity_name=N'fretes'
     AND current_record.source_key=candidate.source_key
    WHERE candidate.execution_id=@execution_id;

    INSERT recon.frete_terminal_transition_observation(
        execution_id,frete_id,observed_status_raw,transition_action,observed_at_utc
    )
    SELECT @execution_id,current_record.frete_id,typed_record.status_raw,
           N'TERMINAL_REGRESSION_BLOCKED',SYSUTCDATETIME()
    FROM stg.execution_candidate candidate
    JOIN stg.frete_record typed_record
      ON typed_record.stage_record_id=candidate.winner_stage_record_id
    JOIN core.frete current_record WITH(UPDLOCK,HOLDLOCK,INDEX(UQ_core_frete_source))
      ON current_record.environment_name=@environment_name
     AND current_record.source_instance=@source_instance
     AND current_record.tenant_scope=@tenant_scope
     AND current_record.entity_name=N'fretes'
     AND current_record.source_key=candidate.source_key
    WHERE candidate.execution_id=@execution_id AND current_record.terminal=1
      AND typed_record.terminal=0
      AND JSON_VALUE(typed_record.field_presence_json,N'$.status')=N'VALUE'
      AND NOT EXISTS(
          SELECT 1 FROM recon.frete_terminal_transition_observation audit_record
          WHERE audit_record.execution_id=@execution_id
            AND audit_record.frete_id=current_record.frete_id
      );

    DECLARE @common_result TABLE(
        execution_id UNIQUEIDENTIFIER NOT NULL,candidate_rows BIGINT NOT NULL,
        inserted_rows BIGINT NOT NULL,updated_rows BIGINT NOT NULL,
        reactivated_rows BIGINT NOT NULL,noop_rows BIGINT NOT NULL,
        stale_noop_rows BIGINT NOT NULL,reconciled_at_utc DATETIME2(3) NOT NULL,
        published_at_utc DATETIME2(3) NOT NULL,
        incremental_frontier_before_utc DATETIME2(3) NULL,
        incremental_frontier_after_utc DATETIME2(3) NULL
    );
    INSERT @common_result EXEC core.usp_apply_reconcile_publish_execution
        @execution_id,@contract_version,@contract_fingerprint,
        @configuration_version,@configuration_fingerprint;

    INSERT core.frete(
        record_state_id,environment_name,source_instance,tenant_scope,entity_name,source_key,
        source_key_wire_type,payload_json,field_presence_json,business_alias_json,status_raw,
        status_code,status_label,status_catalog_version,terminal,freshness_evidence_json,
        freshness_at_utc,freshness_origin,service_at_utc,partition_basis,overlap_policy_version,
        cte_finalizations_json,financial_json,relation_candidates_json,attribute_hash,active,
        first_seen_execution_id,last_seen_execution_id,first_seen_at_utc,last_seen_at_utc
    )
    SELECT application.record_state_id,@environment_name,@source_instance,@tenant_scope,
        N'fretes',typed_record.source_key,N'INTEGER',typed_record.payload_json,
        typed_record.field_presence_json,effective.business_alias_json,effective.status_raw,
        effective.status_code,effective.status_label,N'fretes-status-v1',effective.terminal,
        typed_record.freshness_evidence_json,typed_record.freshness_at_utc,
        typed_record.freshness_origin,typed_record.service_at_utc,N'freights.service_at',
        N'fretes-service-at-overlap-v1',effective.cte_finalizations_json,
        effective.financial_json,effective.relation_candidates_json,typed_record.attribute_hash,
        CONVERT(BIT,1),@execution_id,@execution_id,SYSUTCDATETIME(),SYSUTCDATETIME()
    FROM stg.execution_candidate candidate
    JOIN stg.frete_record typed_record
      ON typed_record.stage_record_id=candidate.winner_stage_record_id
    JOIN @effective effective ON effective.source_key=candidate.source_key
    JOIN recon.execution_candidate_application application
      ON application.execution_id=candidate.execution_id
     AND application.source_key=candidate.source_key
    WHERE candidate.execution_id=@execution_id
      AND NOT EXISTS(
          SELECT 1 FROM core.frete current_record WITH(UPDLOCK,HOLDLOCK,INDEX(UQ_core_frete_source))
          WHERE current_record.environment_name=@environment_name
            AND current_record.source_instance=@source_instance
            AND current_record.tenant_scope=@tenant_scope
            AND current_record.entity_name=N'fretes'
            AND current_record.source_key=candidate.source_key
      );

    UPDATE current_record SET
        payload_json=typed_record.payload_json,
        field_presence_json=typed_record.field_presence_json,
        business_alias_json=effective.business_alias_json,status_raw=effective.status_raw,
        status_code=effective.status_code,status_label=effective.status_label,
        terminal=effective.terminal,freshness_evidence_json=typed_record.freshness_evidence_json,
        freshness_at_utc=typed_record.freshness_at_utc,
        freshness_origin=typed_record.freshness_origin,
        service_at_utc=COALESCE(typed_record.service_at_utc,current_record.service_at_utc),
        cte_finalizations_json=effective.cte_finalizations_json,
        financial_json=effective.financial_json,
        relation_candidates_json=effective.relation_candidates_json,
        attribute_hash=typed_record.attribute_hash,last_seen_execution_id=@execution_id,
        last_seen_at_utc=SYSUTCDATETIME()
    FROM core.frete current_record
    JOIN stg.execution_candidate candidate ON candidate.source_key=current_record.source_key
    JOIN stg.frete_record typed_record
      ON typed_record.stage_record_id=candidate.winner_stage_record_id
    JOIN @effective effective ON effective.source_key=candidate.source_key
    WHERE candidate.execution_id=@execution_id
      AND current_record.environment_name=@environment_name
      AND current_record.source_instance=@source_instance
      AND current_record.tenant_scope=@tenant_scope AND current_record.entity_name=N'fretes'
      AND typed_record.freshness_at_utc>current_record.freshness_at_utc;

    UPDATE current_record SET last_seen_execution_id=@execution_id,
        last_seen_at_utc=SYSUTCDATETIME()
    FROM core.frete current_record
    JOIN stg.execution_candidate candidate ON candidate.source_key=current_record.source_key
    JOIN recon.execution_candidate_application application
      ON application.execution_id=candidate.execution_id
     AND application.source_key=candidate.source_key
    WHERE candidate.execution_id=@execution_id
      AND current_record.environment_name=@environment_name
      AND current_record.source_instance=@source_instance
      AND current_record.tenant_scope=@tenant_scope AND current_record.entity_name=N'fretes'
      AND application.application_disposition IN(N'NO_OP',N'STALE_NO_OP');

    INSERT core.frete_performance(
        frete_id,performance_evidence_json,performance_at_utc,performance_origin,
        last_seen_execution_id,last_seen_at_utc
    )
    SELECT current_record.frete_id,performance_record.performance_evidence_json,
        performance_record.performance_at_utc,performance_record.performance_origin,
        @execution_id,SYSUTCDATETIME()
    FROM stg.execution_candidate candidate
    JOIN stg.frete_performance_observation performance_record
      ON performance_record.stage_record_id=candidate.winner_stage_record_id
    JOIN core.frete current_record WITH(UPDLOCK,HOLDLOCK,INDEX(UQ_core_frete_source))
      ON current_record.environment_name=@environment_name
     AND current_record.source_instance=@source_instance
     AND current_record.tenant_scope=@tenant_scope
     AND current_record.entity_name=N'fretes' AND current_record.source_key=candidate.source_key
    WHERE candidate.execution_id=@execution_id
      AND NOT EXISTS(
          SELECT 1 FROM core.frete_performance current_performance
          WHERE current_performance.frete_id=current_record.frete_id
      );

    UPDATE current_performance SET
        performance_evidence_json=performance_record.performance_evidence_json,
        performance_at_utc=performance_record.performance_at_utc,
        performance_origin=performance_record.performance_origin,
        last_seen_execution_id=@execution_id,last_seen_at_utc=SYSUTCDATETIME()
    FROM core.frete_performance current_performance
    JOIN core.frete current_record ON current_record.frete_id=current_performance.frete_id
    JOIN stg.execution_candidate candidate ON candidate.source_key=current_record.source_key
    JOIN stg.frete_record typed_record
      ON typed_record.stage_record_id=candidate.winner_stage_record_id
    JOIN stg.frete_performance_observation performance_record
      ON performance_record.stage_record_id=candidate.winner_stage_record_id
    WHERE candidate.execution_id=@execution_id
      AND current_record.environment_name=@environment_name
      AND current_record.source_instance=@source_instance
      AND current_record.tenant_scope=@tenant_scope AND current_record.entity_name=N'fretes'
      AND typed_record.freshness_at_utc>=current_record.freshness_at_utc
      AND (performance_record.official_presence<>N'ABSENT'
           OR performance_record.fallback_presence<>N'ABSENT');

    INSERT recon.frete_root_presence_observation(
        execution_id,frete_id,observed_at_utc,snapshot_completeness,absence_evaluation
    )
    SELECT @execution_id,current_record.frete_id,SYSUTCDATETIME(),
           N'BLOCKED_NO_COMPLETENESS_PROOF',N'NOT_EVALUATED_PRUNE_DISABLED'
    FROM core.frete current_record
    JOIN stg.execution_candidate candidate ON candidate.source_key=current_record.source_key
    WHERE candidate.execution_id=@execution_id
      AND current_record.environment_name=@environment_name
      AND current_record.source_instance=@source_instance
      AND current_record.tenant_scope=@tenant_scope AND current_record.entity_name=N'fretes'
      AND NOT EXISTS(
          SELECT 1 FROM recon.frete_root_presence_observation observation
          WHERE observation.execution_id=@execution_id
            AND observation.frete_id=current_record.frete_id
      );
    COMMIT TRANSACTION;
    SELECT execution_id,candidate_rows,inserted_rows,updated_rows,reactivated_rows,
           noop_rows,stale_noop_rows,reconciled_at_utc,published_at_utc,
           incremental_frontier_before_utc,incremental_frontier_after_utc
    FROM @common_result;
END;
GO

CREATE OR ALTER PROCEDURE core.usp_prepare_frete_candidate_set
    @execution_id UNIQUEIDENTIFIER,
    @contract_version NVARCHAR(MAX),
    @contract_fingerprint NVARCHAR(MAX),
    @configuration_version NVARCHAR(MAX),
    @configuration_fingerprint NVARCHAR(MAX)
AS
BEGIN
    -- B54: validate current SQL authority consumption before any effect/read.
    DECLARE @b54_actual NVARCHAR(MAX)=(SELECT LOWER(COALESCE(CONVERT(NVARCHAR(36),@execution_id),N'')) AS [execution],@contract_version AS [contractVersion],@contract_fingerprint AS [contractHash],@configuration_version AS [configurationVersion],@configuration_fingerprint AS [configurationHash] FOR JSON PATH,WITHOUT_ARRAY_WRAPPER,INCLUDE_NULL_VALUES);
    IF ctl.fn_runtime_consumed_scope(@b54_actual,1,NULL)<>1 THROW 52500,N'RUNTIME_DURABLE_CONSUMER_FENCE_REQUIRED',1;

    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    DECLARE @entity_name NVARCHAR(128);
    SELECT @entity_name=partition.entity_name
    FROM ctl.execution_attempt attempt
    JOIN ctl.execution_partition partition ON partition.partition_id=attempt.partition_id
    WHERE attempt.execution_id=@execution_id;
    IF @entity_name COLLATE Latin1_General_100_BIN2<>N'fretes'
        THROW 52130,N'A preparação tipada aceita somente Fretes.',1;
    EXEC core.usp_prepare_staged_execution @execution_id,@contract_version,
        @contract_fingerprint,@configuration_version,@configuration_fingerprint;
    IF NOT EXISTS(
        SELECT 1 FROM ctl.frete_promotion_result
        WHERE execution_id=@execution_id AND validation_state=N'PASSED'
    ) THROW 52131,N'Candidate set tipado de Fretes foi bloqueado.',1;
END;
GO

CREATE OR ALTER PROCEDURE core.usp_prepare_staged_execution
    @execution_id UNIQUEIDENTIFIER,
    @contract_version NVARCHAR(MAX),
    @contract_fingerprint NVARCHAR(MAX),
    @configuration_version NVARCHAR(MAX),
    @configuration_fingerprint NVARCHAR(MAX)
AS
BEGIN
    -- B54: validate current SQL authority consumption before any effect/read.
    DECLARE @b54_actual NVARCHAR(MAX)=(SELECT LOWER(COALESCE(CONVERT(NVARCHAR(36),@execution_id),N'')) AS [execution],@contract_version AS [contractVersion],@contract_fingerprint AS [contractHash],@configuration_version AS [configurationVersion],@configuration_fingerprint AS [configurationHash] FOR JSON PATH,WITHOUT_ARRAY_WRAPPER,INCLUDE_NULL_VALUES);
    IF ctl.fn_runtime_consumed_scope(@b54_actual,1,NULL)<>1 THROW 52500,N'RUNTIME_DURABLE_CONSUMER_FENCE_REQUIRED',1;

    SET NOCOUNT ON;
    SET XACT_ABORT ON;

    IF DATALENGTH(@contract_version) > 256
        OR DATALENGTH(@contract_fingerprint) > 128
        OR DATALENGTH(@configuration_version) > 256
        OR DATALENGTH(@configuration_fingerprint) > 128
        THROW 51410, N'Preparação de candidate set inválida.', 1;

    SET @contract_version = LTRIM(RTRIM(@contract_version));
    SET @contract_fingerprint = LOWER(@contract_fingerprint);
    SET @configuration_version = LTRIM(RTRIM(@configuration_version));
    SET @configuration_fingerprint = LOWER(@configuration_fingerprint);

    IF @execution_id IS NULL
        OR NULLIF(@contract_version, N'') IS NULL
        OR @contract_fingerprint IS NULL
        OR LEN(@contract_fingerprint) <> 64
        OR @contract_fingerprint COLLATE Latin1_General_100_BIN2 LIKE '%[^0-9a-f]%'
        OR NULLIF(@configuration_version, N'') IS NULL
        OR @configuration_fingerprint IS NULL
        OR LEN(@configuration_fingerprint) <> 64
        OR @configuration_fingerprint COLLATE Latin1_General_100_BIN2 LIKE '%[^0-9a-f]%'
        THROW 51410, N'Preparação de candidate set inválida.', 1;

    BEGIN TRANSACTION;

    DECLARE @execution_state NVARCHAR(32);
    DECLARE @partition_id BIGINT;
    DECLARE @next_transition_sequence INT;
    DECLARE @persisted_contract_version NVARCHAR(128);
    DECLARE @persisted_contract_fingerprint CHAR(64);
    DECLARE @persisted_configuration_version NVARCHAR(128);
    DECLARE @persisted_configuration_fingerprint CHAR(64);
    SELECT
        @execution_state = attempt.current_state,
        @partition_id = attempt.partition_id,
        @next_transition_sequence = attempt.next_transition_sequence,
        @persisted_contract_version = attempt.contract_version,
        @persisted_contract_fingerprint = attempt.contract_fingerprint,
        @persisted_configuration_version = attempt.configuration_version,
        @persisted_configuration_fingerprint = attempt.configuration_fingerprint
    FROM ctl.execution_attempt AS attempt WITH (UPDLOCK, HOLDLOCK)
    WHERE attempt.execution_id = @execution_id;

    IF @partition_id IS NULL
        THROW 51411, N'A execução não existe para promoção.', 1;

    IF @persisted_contract_version IS NULL
        OR @persisted_contract_fingerprint IS NULL
        OR @persisted_configuration_version IS NULL
        OR @persisted_configuration_fingerprint IS NULL
        OR @persisted_contract_version COLLATE Latin1_General_100_BIN2
            <> @contract_version COLLATE Latin1_General_100_BIN2
        OR @persisted_contract_fingerprint COLLATE Latin1_General_100_BIN2
            <> @contract_fingerprint COLLATE Latin1_General_100_BIN2
        OR @persisted_configuration_version COLLATE Latin1_General_100_BIN2
            <> @configuration_version COLLATE Latin1_General_100_BIN2
        OR @persisted_configuration_fingerprint COLLATE Latin1_General_100_BIN2
            <> @configuration_fingerprint COLLATE Latin1_General_100_BIN2
        THROW 51418, N'A autorização de contrato diverge da ocorrência.', 1;

    IF @execution_state COLLATE Latin1_General_100_BIN2 = N'PROMOTED'
    BEGIN
        IF NOT EXISTS (
            SELECT 1
            FROM ctl.execution_promotion_result AS result WITH (UPDLOCK, HOLDLOCK)
            WHERE result.execution_id = @execution_id
              AND result.physical_rows = (
                  SELECT COUNT_BIG(*) FROM stg.execution_record WITH (UPDLOCK, HOLDLOCK)
                  WHERE execution_id = @execution_id
              )
              AND result.candidate_rows = (
                  SELECT COUNT_BIG(*) FROM stg.execution_candidate WITH (UPDLOCK, HOLDLOCK)
                  WHERE execution_id = @execution_id
              )
              AND result.quarantined_stage_rows = (
                  SELECT COUNT_BIG(*) FROM recon.quarantine_record WITH (UPDLOCK, HOLDLOCK)
                  WHERE execution_id = @execution_id
              )
              AND result.unidentified_quarantine_rows = (
                  SELECT COUNT_BIG(*) FROM recon.quarantine_record WITH (UPDLOCK, HOLDLOCK)
                  WHERE execution_id = @execution_id AND source_key IS NULL
              )
              AND result.quarantined_root_keys = (
                  SELECT COUNT_BIG(DISTINCT source_key)
                  FROM recon.quarantine_record WITH (UPDLOCK, HOLDLOCK)
                  WHERE execution_id = @execution_id AND source_key IS NOT NULL
              )
              AND EXISTS (
                  SELECT 1
                  FROM ctl.execution_count AS count_audit WITH (UPDLOCK, HOLDLOCK)
                  WHERE count_audit.execution_id = result.execution_id
                    AND count_audit.count_phase = N'STAGING_KERNEL'
                    AND count_audit.physical_rows = result.physical_rows
                    AND count_audit.distinct_root_keys = result.distinct_root_keys
                    AND count_audit.duplicate_rows = result.duplicate_rows
                    AND count_audit.valid_rows = result.candidate_rows
                    AND count_audit.quarantined_root_keys = result.quarantined_root_keys
                    AND count_audit.unidentified_quarantine_rows =
                        result.unidentified_quarantine_rows
              )
              AND NOT EXISTS (
                  SELECT 1
                  FROM stg.execution_candidate AS candidate WITH (UPDLOCK, HOLDLOCK)
                  INNER JOIN stg.execution_record AS staged WITH (UPDLOCK, HOLDLOCK)
                      ON staged.stage_record_id = candidate.winner_stage_record_id
                  WHERE candidate.execution_id = @execution_id
                    AND (
                        staged.execution_id <> candidate.execution_id
                        OR staged.validation_disposition <> N'VALID'
                        OR staged.source_key <> candidate.source_key
                        OR staged.row_fingerprint_version <> candidate.row_fingerprint_version
                        OR staged.source_row_hash <> candidate.source_row_hash
                        OR staged.presence_fingerprint_version <>
                            candidate.presence_fingerprint_version
                        OR staged.presence_fingerprint <> candidate.presence_fingerprint
                        OR staged.source_freshness_at_utc <> candidate.source_freshness_at_utc
                        OR (
                            staged.source_freshness_at_utc IS NULL
                            AND candidate.source_freshness_at_utc IS NOT NULL
                        )
                        OR (
                            staged.source_freshness_at_utc IS NOT NULL
                            AND candidate.source_freshness_at_utc IS NULL
                        )
                    )
              )
              AND NOT EXISTS (
                  SELECT 1
                  FROM recon.quarantine_record AS quarantine WITH (UPDLOCK, HOLDLOCK)
                  INNER JOIN stg.execution_record AS staged WITH (UPDLOCK, HOLDLOCK)
                      ON staged.stage_record_id = quarantine.stage_record_id
                  WHERE quarantine.execution_id = @execution_id
                    AND (
                        staged.execution_id <> quarantine.execution_id
                        OR (
                            staged.validation_disposition = N'QUARANTINE'
                            AND quarantine.reason_code <> staged.quarantine_reason_code
                        )
                        OR (
                            staged.validation_disposition = N'VALID'
                            AND quarantine.reason_code NOT IN (
                                N'EQUAL_FRESHNESS_CONFLICT',
                                N'UNKNOWN_FRESHNESS_CONFLICT',
                                N'KEY_HAS_QUARANTINED_ROW'
                            )
                        )
                        OR (staged.source_key <> quarantine.source_key)
                        OR (staged.source_key IS NULL AND quarantine.source_key IS NOT NULL)
                        OR (staged.source_key IS NOT NULL AND quarantine.source_key IS NULL)
                        OR (staged.row_fingerprint_version <>
                            quarantine.row_fingerprint_version)
                        OR (
                            staged.row_fingerprint_version IS NULL
                            AND quarantine.row_fingerprint_version IS NOT NULL
                        )
                        OR (
                            staged.row_fingerprint_version IS NOT NULL
                            AND quarantine.row_fingerprint_version IS NULL
                        )
                        OR (staged.source_row_hash <> quarantine.source_row_hash)
                        OR (staged.source_row_hash IS NULL AND quarantine.source_row_hash IS NOT NULL)
                        OR (staged.source_row_hash IS NOT NULL AND quarantine.source_row_hash IS NULL)
                        OR (staged.presence_fingerprint_version <>
                            quarantine.presence_fingerprint_version)
                        OR (
                            staged.presence_fingerprint_version IS NULL
                            AND quarantine.presence_fingerprint_version IS NOT NULL
                        )
                        OR (
                            staged.presence_fingerprint_version IS NOT NULL
                            AND quarantine.presence_fingerprint_version IS NULL
                        )
                        OR (staged.presence_fingerprint <> quarantine.presence_fingerprint)
                        OR (
                            staged.presence_fingerprint IS NULL
                            AND quarantine.presence_fingerprint IS NOT NULL
                        )
                        OR (
                            staged.presence_fingerprint IS NOT NULL
                            AND quarantine.presence_fingerprint IS NULL
                        )
                    )
              )
              AND EXISTS (
                  SELECT 1
                  FROM ctl.execution_state_event AS promotion_event WITH (UPDLOCK, HOLDLOCK)
                  WHERE promotion_event.execution_id = @execution_id
                    AND promotion_event.transition_sequence = @next_transition_sequence - 1
                    AND promotion_event.previous_state = N'STAGED'
                    AND promotion_event.next_state = N'PROMOTED'
                    AND promotion_event.reason_code = N'CANDIDATE_SET_PREPARED'
              )
              AND EXISTS (
                  SELECT 1
                  FROM ctl.execution_partition AS promotion_partition WITH (UPDLOCK, HOLDLOCK)
                  WHERE promotion_partition.partition_id = @partition_id
                    AND promotion_partition.current_execution_id = @execution_id
                    AND promotion_partition.current_state = N'PROMOTED'
              )
        )
            THROW 51412, N'Execução promovida sem candidate set e auditoria coerentes.', 1;
        COMMIT TRANSACTION;
        RETURN;
    END;

    IF @execution_state COLLATE Latin1_General_100_BIN2 <> N'STAGED'
        THROW 51413, N'A execução não está pronta para preparar o candidate set.', 1;

    DECLARE @lease_expires_at_utc DATETIME2(3);
    DECLARE @lease_released_at_utc DATETIME2(3);
    SELECT
        @lease_expires_at_utc = lease.expires_at_utc,
        @lease_released_at_utc = lease.released_at_utc
    FROM ctl.execution_partition AS partition WITH (UPDLOCK, HOLDLOCK)
    INNER JOIN ctl.execution_lease AS lease WITH (UPDLOCK, HOLDLOCK)
        ON lease.partition_id = partition.partition_id
       AND lease.execution_id = @execution_id
    WHERE partition.partition_id = @partition_id
      AND partition.current_execution_id = @execution_id;

    DECLARE @database_now_utc DATETIME2(3) = SYSUTCDATETIME();

    IF @lease_expires_at_utc IS NULL
        OR @lease_released_at_utc IS NOT NULL
        OR @lease_expires_at_utc <= @database_now_utc
        THROW 51411, N'A execução não possui lease corrente e ativa para promoção.', 1;

    IF NOT EXISTS (SELECT 1 FROM stg.execution_record WHERE execution_id = @execution_id)
        THROW 51414, N'Não há registros de staging para promover.', 1;

    DECLARE @physical_rows BIGINT;
    DECLARE @candidate_rows BIGINT;
    DECLARE @duplicate_rows BIGINT;
    DECLARE @quarantined_root_keys BIGINT;
    DECLARE @unidentified_quarantine_rows BIGINT;
    DECLARE @quarantined_stage_rows BIGINT;
    DECLARE @distinct_rows BIGINT;

    SELECT @physical_rows = COUNT_BIG(*)
    FROM stg.execution_record WITH (UPDLOCK, HOLDLOCK)
    WHERE execution_id = @execution_id;

    DECLARE @quarantined_keys TABLE (
        source_key NVARCHAR(256) COLLATE Latin1_General_100_BIN2 NOT NULL PRIMARY KEY,
        reason_code NVARCHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL
    );

    ;WITH maximum_freshness AS (
        SELECT source_key, MAX(source_freshness_at_utc) AS maximum_freshness_at_utc
        FROM stg.execution_record
        WHERE execution_id = @execution_id AND validation_disposition = N'VALID'
        GROUP BY source_key
    ), top_signatures AS (
        SELECT DISTINCT
            staged.source_key,
            maximum.maximum_freshness_at_utc,
            staged.row_fingerprint_version,
            staged.source_row_hash,
            staged.presence_fingerprint_version,
            staged.presence_fingerprint
        FROM stg.execution_record AS staged
        INNER JOIN maximum_freshness AS maximum ON maximum.source_key = staged.source_key
        WHERE staged.execution_id = @execution_id
          AND staged.validation_disposition = N'VALID'
          AND (
              staged.source_freshness_at_utc = maximum.maximum_freshness_at_utc
              OR (staged.source_freshness_at_utc IS NULL AND maximum.maximum_freshness_at_utc IS NULL)
          )
    )
    INSERT INTO @quarantined_keys (source_key, reason_code)
    SELECT
        source_key,
        CASE WHEN maximum_freshness_at_utc IS NULL
            THEN N'UNKNOWN_FRESHNESS_CONFLICT'
            ELSE N'EQUAL_FRESHNESS_CONFLICT'
        END
    FROM top_signatures
    GROUP BY source_key, maximum_freshness_at_utc
    HAVING COUNT_BIG(*) > 1;

    INSERT INTO @quarantined_keys (source_key, reason_code)
    SELECT DISTINCT staged.source_key, N'KEY_HAS_QUARANTINED_ROW'
    FROM stg.execution_record AS staged
    WHERE staged.execution_id = @execution_id
      AND staged.validation_disposition = N'QUARANTINE'
      AND staged.source_key IS NOT NULL
      AND NOT EXISTS (
          SELECT 1
          FROM @quarantined_keys AS quarantined
          WHERE quarantined.source_key = staged.source_key
      );

    DECLARE @ranked TABLE (
        stage_record_id BIGINT NOT NULL PRIMARY KEY,
        source_key NVARCHAR(256) COLLATE Latin1_General_100_BIN2 NOT NULL,
        row_fingerprint_version NVARCHAR(128) COLLATE Latin1_General_100_BIN2 NOT NULL,
        source_row_hash CHAR(64) NOT NULL,
        presence_fingerprint_version NVARCHAR(128) COLLATE Latin1_General_100_BIN2 NOT NULL,
        presence_fingerprint CHAR(64) NOT NULL,
        source_freshness_at_utc DATETIME2(3) NULL,
        dedupe_rank BIGINT NOT NULL
    );

    INSERT INTO @ranked (
        stage_record_id, source_key, row_fingerprint_version, source_row_hash,
        presence_fingerprint_version, presence_fingerprint, source_freshness_at_utc, dedupe_rank
    )
    SELECT
        staged.stage_record_id,
        staged.source_key,
        staged.row_fingerprint_version,
        staged.source_row_hash,
        staged.presence_fingerprint_version,
        staged.presence_fingerprint,
        staged.source_freshness_at_utc,
        ROW_NUMBER() OVER (
            PARTITION BY staged.source_key
            ORDER BY
                CASE WHEN staged.source_freshness_at_utc IS NULL THEN 1 ELSE 0 END,
                staged.source_freshness_at_utc DESC,
                staged.input_batch_number DESC,
                staged.input_record_ordinal DESC
        )
    FROM stg.execution_record AS staged WITH (UPDLOCK, HOLDLOCK)
    WHERE staged.execution_id = @execution_id
      AND staged.validation_disposition = N'VALID'
      AND NOT EXISTS (
          SELECT 1 FROM @quarantined_keys AS quarantined
          WHERE quarantined.source_key = staged.source_key
      );

    INSERT INTO recon.quarantine_record (
        execution_id, stage_record_id, reason_code, source_key,
        row_fingerprint_version, source_row_hash, presence_fingerprint_version,
        presence_fingerprint, quarantined_at_utc
    )
    SELECT
        staged.execution_id,
        staged.stage_record_id,
        CASE WHEN staged.validation_disposition = N'QUARANTINE'
            THEN staged.quarantine_reason_code ELSE quarantined.reason_code END,
        staged.source_key,
        staged.row_fingerprint_version,
        staged.source_row_hash,
        staged.presence_fingerprint_version,
        staged.presence_fingerprint,
        @database_now_utc
    FROM stg.execution_record AS staged
    LEFT JOIN @quarantined_keys AS quarantined ON quarantined.source_key = staged.source_key
    WHERE staged.execution_id = @execution_id
      AND (staged.validation_disposition = N'QUARANTINE' OR quarantined.source_key IS NOT NULL);

    INSERT INTO stg.execution_candidate (
        execution_id, source_key, winner_stage_record_id, row_fingerprint_version,
        source_row_hash, presence_fingerprint_version, presence_fingerprint,
        source_freshness_at_utc, prepared_at_utc
    )
    SELECT
        @execution_id, winner.source_key, winner.stage_record_id, winner.row_fingerprint_version,
        winner.source_row_hash, winner.presence_fingerprint_version, winner.presence_fingerprint,
        winner.source_freshness_at_utc, @database_now_utc
    FROM @ranked AS winner
    WHERE winner.dedupe_rank = 1;

    SELECT @candidate_rows = COUNT_BIG(*)
    FROM stg.execution_candidate WHERE execution_id = @execution_id;

    SELECT @quarantined_root_keys = COUNT_BIG(*) FROM @quarantined_keys;

    SELECT @unidentified_quarantine_rows = COUNT_BIG(*)
    FROM stg.execution_record
    WHERE execution_id = @execution_id AND source_key IS NULL;

    SELECT @quarantined_stage_rows = COUNT_BIG(*)
    FROM recon.quarantine_record WHERE execution_id = @execution_id;

    SET @distinct_rows = @candidate_rows + @quarantined_root_keys;
    SET @duplicate_rows = @physical_rows - @distinct_rows - @unidentified_quarantine_rows;

    IF @duplicate_rows < 0
        OR @physical_rows <> @distinct_rows + @duplicate_rows + @unidentified_quarantine_rows
        OR @distinct_rows <> @candidate_rows + @quarantined_root_keys
        OR @quarantined_stage_rows < @quarantined_root_keys + @unidentified_quarantine_rows
        THROW 51415, N'Equações canônicas do candidate set são inconsistentes.', 1;

    -- STAGING_KERNEL é namespace interno: a API runtime de contagens o rejeita.  Este INSERT
    -- permanece dentro da mesma transação que já travou attempt, partition, lease e staging.
    INSERT INTO ctl.execution_count (
        execution_id, count_phase, physical_rows, distinct_root_keys, duplicate_rows,
        valid_rows, quarantined_root_keys, unidentified_quarantine_rows, recorded_at_utc
    ) VALUES (
        @execution_id, N'STAGING_KERNEL', @physical_rows, @distinct_rows, @duplicate_rows,
        @candidate_rows, @quarantined_root_keys, @unidentified_quarantine_rows, @database_now_utc
    );

    INSERT INTO ctl.execution_promotion_result (
        execution_id, physical_rows, distinct_root_keys, candidate_rows,
        duplicate_rows, quarantined_root_keys, unidentified_quarantine_rows,
        quarantined_stage_rows, promoted_at_utc
    ) VALUES (
        @execution_id, @physical_rows, @distinct_rows, @candidate_rows,
        @duplicate_rows, @quarantined_root_keys, @unidentified_quarantine_rows,
        @quarantined_stage_rows, @database_now_utc
    );

    DECLARE @allocated_transition TABLE (transition_sequence INT NOT NULL);
    UPDATE ctl.execution_attempt
    SET current_state = N'PROMOTED',
        next_transition_sequence = next_transition_sequence + 1
    OUTPUT deleted.next_transition_sequence
        INTO @allocated_transition (transition_sequence)
    WHERE execution_id = @execution_id AND current_state = N'STAGED';

    IF @@ROWCOUNT <> 1
        THROW 51416, N'O estado mudou durante a preparação do candidate set.', 1;

    DECLARE @transition_sequence INT;
    SELECT @transition_sequence = transition_sequence FROM @allocated_transition;
    IF @transition_sequence IS NULL
        THROW 51416, N'Não foi possível alocar transition_sequence atomicamente.', 1;

    INSERT INTO ctl.execution_state_event (
        execution_id, transition_sequence, previous_state, next_state, reason_code, transitioned_at_utc
    ) VALUES (
        @execution_id, @transition_sequence, N'STAGED', N'PROMOTED',
        N'CANDIDATE_SET_PREPARED', @database_now_utc
    );

    UPDATE ctl.execution_partition
    SET current_state = N'PROMOTED'
    WHERE partition_id = @partition_id AND current_execution_id = @execution_id;

    IF @@ROWCOUNT <> 1
        THROW 51417, N'A execução deixou de ser corrente durante a promoção.', 1;

    COMMIT TRANSACTION;
END;
GO

CREATE OR ALTER PROCEDURE ctl.usp_control_plane_heartbeat_lease
    @execution_id UNIQUEIDENTIFIER,
    @heartbeat_at_utc DATETIME2(3),
    @lease_seconds INT,
    @expires_at_utc DATETIME2(3)
AS
BEGIN
    -- B54: validate current SQL authority consumption before any effect/read.
    DECLARE @b54_actual NVARCHAR(MAX)=(SELECT LOWER(COALESCE(CONVERT(NVARCHAR(36),@execution_id),N'')) AS [execution] FOR JSON PATH,WITHOUT_ARRAY_WRAPPER,INCLUDE_NULL_VALUES);
    IF ctl.fn_runtime_consumed_scope(@b54_actual,1,NULL)<>1 THROW 52500,N'RUNTIME_DURABLE_CONSUMER_FENCE_REQUIRED',1;

    SET NOCOUNT ON;
    SET XACT_ABORT ON;

    IF @execution_id IS NULL
        OR @heartbeat_at_utc IS NULL
        OR @lease_seconds NOT BETWEEN 1 AND 86400
        OR @expires_at_utc <> DATEADD(SECOND, @lease_seconds, @heartbeat_at_utc)
        THROW 51311, N'Heartbeat de lease inválido.', 1;

    BEGIN TRANSACTION;

    DECLARE @partition_id BIGINT;
    DECLARE @lease_expires_at_utc DATETIME2(3);
    DECLARE @lease_released_at_utc DATETIME2(3);
    SELECT
        @partition_id = attempt.partition_id,
        @lease_expires_at_utc = lease.expires_at_utc,
        @lease_released_at_utc = lease.released_at_utc
    FROM ctl.execution_attempt AS attempt WITH (UPDLOCK, HOLDLOCK)
    INNER JOIN ctl.execution_partition AS partition WITH (UPDLOCK, HOLDLOCK)
        ON partition.partition_id = attempt.partition_id
       AND partition.current_execution_id = attempt.execution_id
    INNER JOIN ctl.execution_lease AS lease WITH (UPDLOCK, HOLDLOCK)
        ON lease.partition_id = partition.partition_id
       AND lease.execution_id = attempt.execution_id
    WHERE attempt.execution_id = @execution_id
      AND attempt.current_state IN (N'EXTRACTING', N'EXTRACTED', N'STAGED', N'PROMOTED');

    DECLARE @database_now_utc DATETIME2(3) = SYSUTCDATETIME();

    IF @partition_id IS NULL
        OR @lease_released_at_utc IS NOT NULL
        OR @lease_expires_at_utc <= @database_now_utc
        THROW 51312, N'Lease corrente e ativa não encontrada para heartbeat.', 1;

    UPDATE ctl.execution_lease
    SET heartbeat_at_utc = @database_now_utc,
        expires_at_utc = DATEADD(SECOND, @lease_seconds, @database_now_utc)
    WHERE partition_id = @partition_id
      AND execution_id = @execution_id
      AND released_at_utc IS NULL;

    IF @@ROWCOUNT <> 1
        THROW 51312, N'Lease ativa não encontrada para heartbeat.', 1;

    COMMIT TRANSACTION;
END;
GO

CREATE OR ALTER PROCEDURE ctl.usp_control_plane_record_counts
    @execution_id UNIQUEIDENTIFIER,
    @count_phase NVARCHAR(MAX),
    @physical_rows BIGINT,
    @distinct_root_keys BIGINT,
    @duplicate_rows BIGINT,
    @valid_rows BIGINT,
    @quarantined_root_keys BIGINT,
    @recorded_at_utc DATETIME2(3),
    @unidentified_quarantine_rows BIGINT = 0
AS
BEGIN
    -- B54: validate current SQL authority consumption before any effect/read.
    DECLARE @b54_actual NVARCHAR(MAX)=(SELECT LOWER(COALESCE(CONVERT(NVARCHAR(36),@execution_id),N'')) AS [execution] FOR JSON PATH,WITHOUT_ARRAY_WRAPPER,INCLUDE_NULL_VALUES);
    IF ctl.fn_runtime_consumed_scope(@b54_actual,1,NULL)<>1 THROW 52500,N'RUNTIME_DURABLE_CONSUMER_FENCE_REQUIRED',1;

    SET NOCOUNT ON;
    SET XACT_ABORT ON;

    IF DATALENGTH(@count_phase) > 128
        THROW 51315, N'Fase da contagem excede o limite textual.', 1;

    SET @count_phase = LTRIM(RTRIM(@count_phase));

    IF @execution_id IS NULL
        OR NULLIF(LTRIM(RTRIM(@count_phase)), N'') IS NULL
        OR @count_phase COLLATE Latin1_General_100_BIN2 = N'STAGING_KERNEL'
        OR @physical_rows IS NULL
        OR @physical_rows < 0
        OR @distinct_root_keys IS NULL
        OR @distinct_root_keys < 0
        OR @duplicate_rows IS NULL
        OR @duplicate_rows < 0
        OR @valid_rows IS NULL
        OR @valid_rows < 0
        OR @quarantined_root_keys IS NULL
        OR @quarantined_root_keys < 0
        OR @unidentified_quarantine_rows IS NULL
        OR @unidentified_quarantine_rows < 0
        OR @physical_rows <> @distinct_root_keys + @duplicate_rows + @unidentified_quarantine_rows
        OR @distinct_root_keys <> @valid_rows + @quarantined_root_keys
        OR @recorded_at_utc IS NULL
        THROW 51315, N'Equação de contagens inválida.', 1;

    BEGIN TRANSACTION;

    DECLARE @partition_id BIGINT;
    DECLARE @execution_state NVARCHAR(32);
    SELECT
        @partition_id = partition_id,
        @execution_state = current_state
    FROM ctl.execution_attempt WITH (UPDLOCK, HOLDLOCK)
    WHERE execution_id = @execution_id;

    IF @partition_id IS NULL
        THROW 51316, N'Execução inexistente para contagens.', 1;

    DECLARE @existing_count_id BIGINT;
    DECLARE @existing_physical_rows BIGINT;
    DECLARE @existing_distinct_root_keys BIGINT;
    DECLARE @existing_duplicate_rows BIGINT;
    DECLARE @existing_valid_rows BIGINT;
    DECLARE @existing_quarantined_root_keys BIGINT;
    DECLARE @existing_unidentified_quarantine_rows BIGINT;
    SELECT
        @existing_count_id = count_id,
        @existing_physical_rows = physical_rows,
        @existing_distinct_root_keys = distinct_root_keys,
        @existing_duplicate_rows = duplicate_rows,
        @existing_valid_rows = valid_rows,
        @existing_quarantined_root_keys = quarantined_root_keys,
        @existing_unidentified_quarantine_rows = unidentified_quarantine_rows
    FROM ctl.execution_count WITH (UPDLOCK, HOLDLOCK)
    WHERE execution_id = @execution_id
      AND count_phase = @count_phase;

    IF @existing_count_id IS NOT NULL
    BEGIN
        IF @existing_physical_rows <> @physical_rows
            OR @existing_distinct_root_keys <> @distinct_root_keys
            OR @existing_duplicate_rows <> @duplicate_rows
            OR @existing_valid_rows <> @valid_rows
            OR @existing_quarantined_root_keys <> @quarantined_root_keys
            OR @existing_unidentified_quarantine_rows <> @unidentified_quarantine_rows
            THROW 51335, N'Contagem já existe com conteúdo imutável divergente.', 1;

        COMMIT TRANSACTION;
        RETURN;
    END;

    DECLARE @lease_expires_at_utc DATETIME2(3);
    DECLARE @lease_released_at_utc DATETIME2(3);
    SELECT
        @lease_expires_at_utc = lease.expires_at_utc,
        @lease_released_at_utc = lease.released_at_utc
    FROM ctl.execution_partition AS partition WITH (UPDLOCK, HOLDLOCK)
        INNER JOIN ctl.execution_lease AS lease WITH (UPDLOCK, HOLDLOCK)
            ON lease.partition_id = partition.partition_id
           AND lease.execution_id = @execution_id
    WHERE partition.partition_id = @partition_id
      AND partition.current_execution_id = @execution_id;

    DECLARE @database_now_utc DATETIME2(3) = SYSUTCDATETIME();

    IF @execution_state COLLATE Latin1_General_100_BIN2
            NOT IN (N'EXTRACTING', N'EXTRACTED', N'STAGED', N'PROMOTED')
        OR @lease_expires_at_utc IS NULL
        OR @lease_released_at_utc IS NOT NULL
        OR @lease_expires_at_utc <= @database_now_utc
        THROW 51316, N'Contagens exigem execução corrente, não terminal e com lease ativa.', 1;

    INSERT INTO ctl.execution_count (
        execution_id, count_phase, physical_rows, distinct_root_keys, duplicate_rows,
        valid_rows, quarantined_root_keys, unidentified_quarantine_rows, recorded_at_utc
    ) VALUES (
        @execution_id, @count_phase, @physical_rows, @distinct_root_keys, @duplicate_rows,
        @valid_rows, @quarantined_root_keys, @unidentified_quarantine_rows, @database_now_utc
    );

    COMMIT TRANSACTION;
END;
GO

CREATE OR ALTER PROCEDURE ctl.usp_control_plane_record_page
    @execution_id UNIQUEIDENTIFIER,
    @page_number INT,
    @page_attempt INT,
    @requested_page_size INT,
    @physical_rows BIGINT,
    @distinct_root_keys BIGINT,
    @response_bytes BIGINT,
    @terminal_empty_page BIT,
    @read_at_utc DATETIME2(3),
    @terminal_evidence_kind NVARCHAR(MAX) = NULL
AS
BEGIN
    -- B54: validate current SQL authority consumption before any effect/read.
    DECLARE @b54_actual NVARCHAR(MAX)=(SELECT LOWER(COALESCE(CONVERT(NVARCHAR(36),@execution_id),N'')) AS [execution] FOR JSON PATH,WITHOUT_ARRAY_WRAPPER,INCLUDE_NULL_VALUES);
    IF ctl.fn_runtime_consumed_scope(@b54_actual,1,NULL)<>1 THROW 52500,N'RUNTIME_DURABLE_CONSUMER_FENCE_REQUIRED',1;

    SET NOCOUNT ON;
    SET XACT_ABORT ON;

    IF DATALENGTH(@terminal_evidence_kind) > 64
        THROW 51313, N'Auditoria de página inválida.', 1;

    SET @terminal_evidence_kind = NULLIF(LTRIM(RTRIM(@terminal_evidence_kind)), N'');
    SET @terminal_evidence_kind = COALESCE(
        @terminal_evidence_kind,
        CASE WHEN @terminal_empty_page = 1
             THEN N'DATA_EXPORT_EMPTY_PAGE' ELSE N'NONE' END
    );

    IF @execution_id IS NULL
        OR @page_number IS NULL
        OR @page_number < 1
        OR @page_attempt IS NULL
        OR @page_attempt < 1
        OR @requested_page_size IS NULL
        OR @requested_page_size < 1
        OR @physical_rows IS NULL
        OR @physical_rows < 0
        OR @distinct_root_keys IS NULL
        OR @distinct_root_keys < 0
        OR @distinct_root_keys > @physical_rows
        OR @response_bytes IS NULL
        OR @response_bytes < 0
        OR @terminal_empty_page IS NULL
        OR @terminal_evidence_kind COLLATE Latin1_General_100_BIN2 NOT IN (
            N'NONE', N'DATA_EXPORT_EMPTY_PAGE', N'GRAPHQL_PAGE_INFO'
        )
        OR (
            @terminal_evidence_kind COLLATE Latin1_General_100_BIN2 = N'NONE'
            AND @terminal_empty_page <> 0
        )
        OR (
            @terminal_evidence_kind COLLATE Latin1_General_100_BIN2 = N'DATA_EXPORT_EMPTY_PAGE'
            AND (@terminal_empty_page <> 1 OR @physical_rows <> 0)
        )
        OR (
            @terminal_evidence_kind COLLATE Latin1_General_100_BIN2 = N'GRAPHQL_PAGE_INFO'
            AND (@terminal_empty_page <> 0 OR @physical_rows = 0)
        )
        OR @read_at_utc IS NULL
        THROW 51313, N'Auditoria de página inválida.', 1;

    BEGIN TRANSACTION;

    DECLARE @partition_id BIGINT;
    DECLARE @execution_state NVARCHAR(32);
    SELECT
        @partition_id = partition_id,
        @execution_state = current_state
    FROM ctl.execution_attempt WITH (UPDLOCK, HOLDLOCK)
    WHERE execution_id = @execution_id;

    IF @partition_id IS NULL
        THROW 51314, N'Execução inexistente para auditoria de página.', 1;

    DECLARE @existing_page_audit_id BIGINT;
    DECLARE @existing_requested_page_size INT;
    DECLARE @existing_physical_rows BIGINT;
    DECLARE @existing_distinct_root_keys BIGINT;
    DECLARE @existing_response_bytes BIGINT;
    DECLARE @existing_terminal_empty_page BIT;
    DECLARE @existing_terminal_evidence_kind NVARCHAR(32);
    SELECT
        @existing_page_audit_id = page_audit_id,
        @existing_requested_page_size = requested_page_size,
        @existing_physical_rows = physical_rows,
        @existing_distinct_root_keys = distinct_root_keys,
        @existing_response_bytes = response_bytes,
        @existing_terminal_empty_page = terminal_empty_page,
        @existing_terminal_evidence_kind = terminal_evidence_kind
    FROM ctl.execution_page_audit WITH (UPDLOCK, HOLDLOCK)
    WHERE execution_id = @execution_id
      AND page_number = @page_number
      AND page_attempt = @page_attempt;

    IF @existing_page_audit_id IS NOT NULL
    BEGIN
        IF @existing_requested_page_size <> @requested_page_size
            OR @existing_physical_rows <> @physical_rows
            OR @existing_distinct_root_keys <> @distinct_root_keys
            OR @existing_response_bytes <> @response_bytes
            OR @existing_terminal_empty_page <> @terminal_empty_page
            OR @existing_terminal_evidence_kind COLLATE Latin1_General_100_BIN2
                <> @terminal_evidence_kind COLLATE Latin1_General_100_BIN2
            THROW 51334, N'Auditoria de página já existe com conteúdo imutável divergente.', 1;

        COMMIT TRANSACTION;
        RETURN;
    END;

    DECLARE @lease_expires_at_utc DATETIME2(3);
    DECLARE @lease_released_at_utc DATETIME2(3);
    SELECT
        @lease_expires_at_utc = lease.expires_at_utc,
        @lease_released_at_utc = lease.released_at_utc
    FROM ctl.execution_partition AS partition WITH (UPDLOCK, HOLDLOCK)
        INNER JOIN ctl.execution_lease AS lease WITH (UPDLOCK, HOLDLOCK)
            ON lease.partition_id = partition.partition_id
           AND lease.execution_id = @execution_id
    WHERE partition.partition_id = @partition_id
      AND partition.current_execution_id = @execution_id;

    DECLARE @database_now_utc DATETIME2(3) = SYSUTCDATETIME();

    IF @execution_state COLLATE Latin1_General_100_BIN2 <> N'EXTRACTING'
        OR @lease_expires_at_utc IS NULL
        OR @lease_released_at_utc IS NOT NULL
        OR @lease_expires_at_utc <= @database_now_utc
        THROW 51314, N'Auditoria de página exige execução corrente, EXTRACTING e com lease ativa.', 1;

    INSERT INTO ctl.execution_page_audit (
        execution_id, page_number, page_attempt, requested_page_size, physical_rows,
        distinct_root_keys, response_bytes, terminal_empty_page, terminal_evidence_kind,
        read_at_utc
    ) VALUES (
        @execution_id, @page_number, @page_attempt, @requested_page_size, @physical_rows,
        @distinct_root_keys, @response_bytes, @terminal_empty_page, @terminal_evidence_kind,
        @database_now_utc
    );

    COMMIT TRANSACTION;
END;
GO

CREATE OR ALTER PROCEDURE ctl.usp_control_plane_register_incremental_frontier
    @environment_name NVARCHAR(MAX),
    @source_instance NVARCHAR(MAX),
    @tenant_scope NVARCHAR(MAX),
    @entity_name NVARCHAR(MAX),
    @initial_contiguous_end_utc DATETIME2(3),
    @registered_at_utc DATETIME2(3)
AS
BEGIN
    -- B54: validate current SQL authority consumption before any effect/read.
    DECLARE @b54_actual NVARCHAR(MAX)=(SELECT @environment_name AS [environment],@source_instance AS [source],@tenant_scope AS [tenant],@entity_name AS [entity] FOR JSON PATH,WITHOUT_ARRAY_WRAPPER,INCLUDE_NULL_VALUES);
    IF ctl.fn_runtime_consumed_scope(@b54_actual,1,NULL)<>1 THROW 52500,N'RUNTIME_DURABLE_CONSUMER_FENCE_REQUIRED',1;

    SET NOCOUNT ON;
    SET XACT_ABORT ON;

    IF DATALENGTH(@environment_name) > 64
        OR DATALENGTH(@source_instance) > 256
        OR DATALENGTH(@tenant_scope) > 256
        OR DATALENGTH(@entity_name) > 256
        THROW 51321, N'Fronteira incremental excede o limite textual.', 1;

    SET @environment_name = LTRIM(RTRIM(@environment_name));
    SET @source_instance = LTRIM(RTRIM(@source_instance));
    SET @tenant_scope = LTRIM(RTRIM(@tenant_scope));
    SET @entity_name = LTRIM(RTRIM(@entity_name));

    IF NULLIF(LTRIM(RTRIM(@environment_name)), N'') IS NULL
        OR NULLIF(LTRIM(RTRIM(@source_instance)), N'') IS NULL
        OR NULLIF(LTRIM(RTRIM(@tenant_scope)), N'') IS NULL
        OR NULLIF(LTRIM(RTRIM(@entity_name)), N'') IS NULL
        OR @initial_contiguous_end_utc IS NULL
        OR @registered_at_utc IS NULL
        THROW 51321, N'Fronteira incremental inválida.', 1;

    BEGIN TRANSACTION;

    IF NOT EXISTS (
        SELECT 1 FROM ctl.source_catalog WHERE source_instance = @source_instance AND active = 1
    )
        THROW 51322, N'Fonte inativa não pode ter watermark operacional.', 1;

    DECLARE @existing_end DATETIME2(3);
    SELECT @existing_end = contiguous_partition_end_utc
    FROM ctl.incremental_publication_watermark WITH (UPDLOCK, HOLDLOCK)
    WHERE environment_name = @environment_name
      AND source_instance = @source_instance
      AND tenant_scope = @tenant_scope
      AND entity_name = @entity_name;

    DECLARE @database_now_utc DATETIME2(3) = SYSUTCDATETIME();

    IF @existing_end IS NULL
    BEGIN
        INSERT INTO ctl.incremental_publication_watermark (
            environment_name, source_instance, tenant_scope, entity_name,
            contiguous_partition_end_utc, registered_at_utc
        ) VALUES (
            @environment_name, @source_instance, @tenant_scope, @entity_name,
            @initial_contiguous_end_utc, @database_now_utc
        );
    END
    ELSE IF @existing_end <> @initial_contiguous_end_utc
    BEGIN
        THROW 51323, N'Fronteira incremental já existe e é imutável.', 1;
    END;

    COMMIT TRANSACTION;
END;
GO

CREATE OR ALTER PROCEDURE ctl.usp_control_plane_register_source
    @source_instance NVARCHAR(MAX),
    @source_kind NVARCHAR(MAX),
    @registered_at_utc DATETIME2(3)
AS
BEGIN
    -- B54: validate current SQL authority consumption before any effect/read.
    DECLARE @b54_actual NVARCHAR(MAX)=(SELECT @source_instance AS [source] FOR JSON PATH,WITHOUT_ARRAY_WRAPPER,INCLUDE_NULL_VALUES);
    IF ctl.fn_runtime_consumed_scope(@b54_actual,1,NULL)<>1 THROW 52500,N'RUNTIME_DURABLE_CONSUMER_FENCE_REQUIRED',1;

    SET NOCOUNT ON;
    SET XACT_ABORT ON;

    IF DATALENGTH(@source_instance) > 256 OR DATALENGTH(@source_kind) > 128
        THROW 51300, N'Catálogo de fonte excede o limite textual.', 1;

    SET @source_instance = LTRIM(RTRIM(@source_instance));
    SET @source_kind = LTRIM(RTRIM(@source_kind));

    IF NULLIF(LTRIM(RTRIM(@source_instance)), N'') IS NULL
        OR NULLIF(LTRIM(RTRIM(@source_kind)), N'') IS NULL
        OR @registered_at_utc IS NULL
        THROW 51300, N'Catálogo de fonte inválido.', 1;

    BEGIN TRANSACTION;

    DECLARE @existing_source_kind NVARCHAR(64);
    SELECT @existing_source_kind = source_kind
    FROM ctl.source_catalog WITH (UPDLOCK, HOLDLOCK)
    WHERE source_instance = @source_instance;

    -- registered_at_utc is a technical persistence timestamp.  Capture it only
    -- after the catalog key has been locked; the caller value is compatibility metadata.
    DECLARE @database_now_utc DATETIME2(3) = SYSUTCDATETIME();

    IF @existing_source_kind IS NULL
    BEGIN
        INSERT INTO ctl.source_catalog (source_instance, source_kind, active, registered_at_utc)
        VALUES (@source_instance, @source_kind, 1, @database_now_utc);
    END
    ELSE IF @existing_source_kind COLLATE Latin1_General_100_BIN2
            <> @source_kind COLLATE Latin1_General_100_BIN2
    BEGIN
        THROW 51301, N'Instância de fonte já pertence a outro catálogo.', 1;
    END;

    COMMIT TRANSACTION;
END;
GO

CREATE OR ALTER PROCEDURE ctl.usp_control_plane_start_cycle
    @cycle_id UNIQUEIDENTIFIER,
    @plan_version NVARCHAR(MAX),
    @plan_fingerprint NVARCHAR(MAX),
    @planned_at_utc DATETIME2(3)
AS
BEGIN
    -- B54: validate current SQL authority consumption before any effect/read.
    DECLARE @b54_actual NVARCHAR(MAX)=(SELECT LOWER(COALESCE(CONVERT(NVARCHAR(36),@cycle_id),N'')) AS [cycle],@plan_version AS [planVersion],@plan_fingerprint AS [planHash] FOR JSON PATH,WITHOUT_ARRAY_WRAPPER,INCLUDE_NULL_VALUES);
    IF ctl.fn_runtime_consumed_scope(@b54_actual,1,NULL)<>1 THROW 52500,N'RUNTIME_DURABLE_CONSUMER_FENCE_REQUIRED',1;

    SET NOCOUNT ON;
    SET XACT_ABORT ON;

    IF DATALENGTH(@plan_version) > 256 OR DATALENGTH(@plan_fingerprint) > 128
        THROW 51302, N'Ciclo de execução excede o limite textual.', 1;

    SET @plan_version = LTRIM(RTRIM(@plan_version));
    SET @plan_fingerprint = LOWER(@plan_fingerprint);

    IF @cycle_id IS NULL
        OR @planned_at_utc IS NULL
        OR NULLIF(LTRIM(RTRIM(@plan_version)), N'') IS NULL
        OR @plan_fingerprint IS NULL
        OR LEN(@plan_fingerprint) <> 64
        OR @plan_fingerprint COLLATE Latin1_General_100_BIN2 LIKE '%[^0-9A-Fa-f]%'
        THROW 51302, N'Ciclo de execução inválido.', 1;

    BEGIN TRANSACTION;

    DECLARE @existing_version NVARCHAR(128);
    DECLARE @existing_fingerprint CHAR(64);
    SELECT
        @existing_version = plan_version,
        @existing_fingerprint = plan_fingerprint
    FROM ctl.execution_cycle WITH (UPDLOCK, HOLDLOCK)
    WHERE cycle_id = @cycle_id;

    -- planned_at_utc is a technical persistence timestamp.  Capture it only
    -- after the cycle key has been locked; retries do not compare caller clocks.
    DECLARE @database_now_utc DATETIME2(3) = SYSUTCDATETIME();

    IF @existing_fingerprint IS NULL
    BEGIN
        INSERT INTO ctl.execution_cycle (cycle_id, plan_version, plan_fingerprint, planned_at_utc)
        VALUES (@cycle_id, @plan_version, LOWER(@plan_fingerprint), @database_now_utc);
    END
    ELSE IF @existing_version COLLATE Latin1_General_100_BIN2
            <> @plan_version COLLATE Latin1_General_100_BIN2
        OR @existing_fingerprint <> @plan_fingerprint
    BEGIN
        THROW 51303, N'O ciclo não pode ser regravado com outro plano.', 1;
    END;

    COMMIT TRANSACTION;
END;
GO

CREATE OR ALTER PROCEDURE ctl.usp_control_plane_start_execution
    @execution_id UNIQUEIDENTIFIER,
    @cycle_id UNIQUEIDENTIFIER,
    @environment_name NVARCHAR(MAX),
    @source_instance NVARCHAR(MAX),
    @tenant_scope NVARCHAR(MAX),
    @entity_name NVARCHAR(MAX),
    @execution_mode NVARCHAR(MAX),
    @partition_start_utc DATETIME2(3),
    @partition_end_exclusive_utc DATETIME2(3),
    @window_strategy NVARCHAR(MAX),
    @contract_version NVARCHAR(MAX),
    @contract_fingerprint NVARCHAR(MAX),
    @configuration_version NVARCHAR(MAX),
    @configuration_fingerprint NVARCHAR(MAX),
    @idempotency_key NVARCHAR(MAX),
    @replay_of_execution_id UNIQUEIDENTIFIER = NULL,
    @lease_seconds INT,
    @started_at_utc DATETIME2(3)
AS
BEGIN
    -- B54: validate current SQL authority consumption before any effect/read.
    DECLARE @b54_actual NVARCHAR(MAX)=(SELECT LOWER(COALESCE(CONVERT(NVARCHAR(36),@execution_id),N'')) AS [execution],LOWER(COALESCE(CONVERT(NVARCHAR(36),@cycle_id),N'')) AS [cycle],@environment_name AS [environment],@source_instance AS [source],@tenant_scope AS [tenant],@entity_name AS [entity],@execution_mode AS [mode],CONVERT(NVARCHAR(33),@partition_start_utc,126) AS [start],CONVERT(NVARCHAR(33),@partition_end_exclusive_utc,126) AS [endExclusive],@window_strategy AS [strategy],@contract_version AS [contractVersion],@contract_fingerprint AS [contractHash],@configuration_version AS [configurationVersion],@configuration_fingerprint AS [configurationHash],@idempotency_key AS [idempotency],LOWER(COALESCE(CONVERT(NVARCHAR(36),@replay_of_execution_id),N'')) AS [replayOf] FOR JSON PATH,WITHOUT_ARRAY_WRAPPER,INCLUDE_NULL_VALUES);
    IF ctl.fn_runtime_consumed_scope(@b54_actual,1,NULL)<>1 THROW 52500,N'RUNTIME_DURABLE_CONSUMER_FENCE_REQUIRED',1;

    SET NOCOUNT ON;
    SET XACT_ABORT ON;

    IF DATALENGTH(@environment_name) > 64
        OR DATALENGTH(@source_instance) > 256
        OR DATALENGTH(@tenant_scope) > 256
        OR DATALENGTH(@entity_name) > 256
        OR DATALENGTH(@execution_mode) > 32
        OR DATALENGTH(@window_strategy) > 128
        OR DATALENGTH(@contract_version) > 256
        OR DATALENGTH(@contract_fingerprint) > 128
        OR DATALENGTH(@configuration_version) > 256
        OR DATALENGTH(@configuration_fingerprint) > 128
        OR DATALENGTH(@idempotency_key) > 256
        THROW 51304, N'Início de execução excede o limite textual.', 1;

    SET @environment_name = LTRIM(RTRIM(@environment_name));
    SET @source_instance = LTRIM(RTRIM(@source_instance));
    SET @tenant_scope = LTRIM(RTRIM(@tenant_scope));
    SET @entity_name = LTRIM(RTRIM(@entity_name));
    SET @execution_mode = LTRIM(RTRIM(@execution_mode));
    SET @window_strategy = LTRIM(RTRIM(@window_strategy));
    SET @contract_version = LTRIM(RTRIM(@contract_version));
    SET @contract_fingerprint = LOWER(@contract_fingerprint);
    SET @configuration_version = LTRIM(RTRIM(@configuration_version));
    SET @configuration_fingerprint = LOWER(@configuration_fingerprint);
    SET @idempotency_key = LTRIM(RTRIM(@idempotency_key));

    DECLARE @database_now_utc DATETIME2(3) = SYSUTCDATETIME();

    IF @execution_id IS NULL
        OR @cycle_id IS NULL
        OR NULLIF(LTRIM(RTRIM(@environment_name)), N'') IS NULL
        OR NULLIF(LTRIM(RTRIM(@source_instance)), N'') IS NULL
        OR NULLIF(LTRIM(RTRIM(@tenant_scope)), N'') IS NULL
        OR NULLIF(LTRIM(RTRIM(@entity_name)), N'') IS NULL
        OR @execution_mode COLLATE Latin1_General_100_BIN2
            NOT IN (N'INCREMENTAL', N'BOOTSTRAP', N'BACKFILL', N'REPLAY', N'SWEEP')
        OR @partition_start_utc IS NULL
        OR @partition_end_exclusive_utc IS NULL
        OR @partition_start_utc >= @partition_end_exclusive_utc
        OR NULLIF(LTRIM(RTRIM(@window_strategy)), N'') IS NULL
        OR NULLIF(LTRIM(RTRIM(@contract_version)), N'') IS NULL
        OR @contract_fingerprint IS NULL
        OR LEN(@contract_fingerprint) <> 64
        OR @contract_fingerprint COLLATE Latin1_General_100_BIN2 LIKE '%[^0-9A-Fa-f]%'
        OR NULLIF(LTRIM(RTRIM(@configuration_version)), N'') IS NULL
        OR @configuration_fingerprint IS NULL
        OR LEN(@configuration_fingerprint) <> 64
        OR @configuration_fingerprint COLLATE Latin1_General_100_BIN2 LIKE '%[^0-9A-Fa-f]%'
        OR NULLIF(LTRIM(RTRIM(@idempotency_key)), N'') IS NULL
        OR @lease_seconds NOT BETWEEN 1 AND 86400
        OR @started_at_utc IS NULL
        OR (@execution_mode COLLATE Latin1_General_100_BIN2 = N'REPLAY'
            AND @replay_of_execution_id IS NULL)
        OR (@execution_mode COLLATE Latin1_General_100_BIN2 <> N'REPLAY'
            AND @replay_of_execution_id IS NOT NULL)
        OR @replay_of_execution_id = @execution_id
        THROW 51304, N'Início de execução inválido.', 1;

    BEGIN TRANSACTION;

    DECLARE @existing_execution_id UNIQUEIDENTIFIER;
    SELECT @existing_execution_id = execution_id
    FROM ctl.execution_attempt WITH (UPDLOCK, HOLDLOCK)
    WHERE execution_id = @execution_id;

    IF @existing_execution_id IS NOT NULL
    BEGIN
        IF NOT EXISTS (
            SELECT 1
            FROM ctl.execution_attempt AS attempt
            INNER JOIN ctl.execution_partition AS partition ON partition.partition_id = attempt.partition_id
            WHERE attempt.execution_id = @execution_id
              AND attempt.cycle_id = @cycle_id
              AND partition.environment_name = @environment_name
              AND partition.source_instance = @source_instance
              AND partition.tenant_scope = @tenant_scope
              AND partition.entity_name = @entity_name
              AND partition.execution_mode = @execution_mode
              AND partition.partition_start_utc = @partition_start_utc
              AND partition.partition_end_exclusive_utc = @partition_end_exclusive_utc
              AND attempt.window_strategy = @window_strategy
              AND attempt.contract_version = @contract_version
              AND attempt.contract_fingerprint = @contract_fingerprint
              AND attempt.configuration_version = @configuration_version
              AND attempt.configuration_fingerprint = @configuration_fingerprint
              AND attempt.idempotency_key = @idempotency_key
              AND (
                    attempt.replay_of_execution_id = @replay_of_execution_id
                    OR (attempt.replay_of_execution_id IS NULL AND @replay_of_execution_id IS NULL)
              )
        )
            THROW 51305, N'Execution_id já existe com ocorrência imutável divergente.', 1;

        COMMIT TRANSACTION;
        RETURN;
    END;

    SET @database_now_utc = SYSUTCDATETIME();
    IF ABS(DATEDIFF_BIG(SECOND, @started_at_utc, @database_now_utc)) > 300
        THROW 51304, N'Início de execução fora do limite de skew permitido.', 1;

    IF EXISTS (
        SELECT 1
        FROM ctl.execution_attempt WITH (UPDLOCK, HOLDLOCK)
        WHERE idempotency_key = @idempotency_key
    )
        THROW 51306, N'Chave de idempotência já pertence a outra ocorrência.', 1;

    IF NOT EXISTS (SELECT 1 FROM ctl.execution_cycle WHERE cycle_id = @cycle_id)
        THROW 51307, N'Ciclo de execução inexistente.', 1;

    IF NOT EXISTS (
        SELECT 1 FROM ctl.source_catalog
        WHERE source_instance = @source_instance AND active = 1
    )
        THROW 51308, N'Instância de fonte não está ativa no catálogo.', 1;

    -- REPLAY ocupa uma partição própria; a origem deve ser uma execução anterior não-REPLAY
    -- no mesmo namespace (ambiente/fonte/tenant/entidade) e na mesma janela canônica.
    IF @execution_mode COLLATE Latin1_General_100_BIN2 = N'REPLAY'
       AND NOT EXISTS (
            SELECT 1
            FROM ctl.execution_attempt AS replay_origin WITH (UPDLOCK, HOLDLOCK)
            INNER JOIN ctl.execution_partition AS origin_partition WITH (UPDLOCK, HOLDLOCK)
                ON origin_partition.partition_id = replay_origin.partition_id
            WHERE replay_origin.execution_id = @replay_of_execution_id
              AND origin_partition.environment_name = @environment_name
              AND origin_partition.source_instance = @source_instance
              AND origin_partition.tenant_scope = @tenant_scope
              AND origin_partition.entity_name = @entity_name
              AND origin_partition.execution_mode IN (
                  N'INCREMENTAL', N'BOOTSTRAP', N'BACKFILL', N'SWEEP'
              )
              AND origin_partition.partition_start_utc = @partition_start_utc
              AND origin_partition.partition_end_exclusive_utc = @partition_end_exclusive_utc
       )
        THROW 51333, N'A origem do replay não existe antes da nova tentativa ou diverge do namespace e janela.', 1;

    DECLARE @partition_id BIGINT;
    SELECT @partition_id = partition_id
    FROM ctl.execution_partition WITH (UPDLOCK, HOLDLOCK)
    WHERE environment_name = @environment_name
      AND source_instance = @source_instance
      AND tenant_scope = @tenant_scope
      AND entity_name = @entity_name
      AND execution_mode = @execution_mode
      AND partition_start_utc = @partition_start_utc
      AND partition_end_exclusive_utc = @partition_end_exclusive_utc;

    SET @database_now_utc = SYSUTCDATETIME();

    IF @partition_id IS NULL
    BEGIN
        INSERT INTO ctl.execution_partition (
            environment_name, source_instance, tenant_scope, entity_name, execution_mode,
            partition_start_utc, partition_end_exclusive_utc, created_at_utc
        ) VALUES (
            @environment_name, @source_instance, @tenant_scope, @entity_name, @execution_mode,
            @partition_start_utc, @partition_end_exclusive_utc, @database_now_utc
        );
        SET @partition_id = CONVERT(BIGINT, SCOPE_IDENTITY());
    END;

    DECLARE @existing_lease_expires_at_utc DATETIME2(3);
    SELECT @existing_lease_expires_at_utc = expires_at_utc
    FROM ctl.execution_lease WITH (UPDLOCK, HOLDLOCK)
    WHERE partition_id = @partition_id
      AND released_at_utc IS NULL;

    -- Capture o relógio depois de adquirir os locks usados para decidir a lease.
    SET @database_now_utc = SYSUTCDATETIME();

    IF @existing_lease_expires_at_utc > @database_now_utc
        THROW 51309, N'Já existe lease ativa para a partição semântica.', 1;

    IF @existing_lease_expires_at_utc <= @database_now_utc
        THROW 51310, N'Lease expirada exige recuperação determinística antes de nova execução.', 1;

    DECLARE @allocated_attempt TABLE (attempt_number INT NOT NULL);
    UPDATE ctl.execution_partition
    SET next_attempt_number = next_attempt_number + 1
    OUTPUT deleted.next_attempt_number INTO @allocated_attempt (attempt_number)
    WHERE partition_id = @partition_id;

    DECLARE @attempt_number INT;
    SELECT @attempt_number = attempt_number FROM @allocated_attempt;
    IF @attempt_number IS NULL
        THROW 51331, N'Não foi possível alocar attempt_number atomicamente.', 1;

    INSERT INTO ctl.execution_attempt (
        execution_id, partition_id, cycle_id, attempt_number, window_strategy,
        contract_version, contract_fingerprint, configuration_version, configuration_fingerprint,
        idempotency_key, replay_of_execution_id, current_state, next_transition_sequence,
        started_at_utc
    ) VALUES (
        @execution_id, @partition_id, @cycle_id, @attempt_number, @window_strategy,
        @contract_version, @contract_fingerprint, @configuration_version, @configuration_fingerprint,
        @idempotency_key, @replay_of_execution_id, N'EXTRACTING', 3, @database_now_utc
    );

    INSERT INTO ctl.execution_state_event (
        execution_id, transition_sequence, previous_state, next_state, reason_code, transitioned_at_utc
    ) VALUES
        (@execution_id, 1, NULL, N'PLANNED', N'EXECUTION_PLANNED', @database_now_utc),
        (@execution_id, 2, N'PLANNED', N'EXTRACTING', N'LEASE_ACQUIRED', @database_now_utc);

    INSERT INTO ctl.execution_lease (
        lease_id, partition_id, execution_id, acquired_at_utc, heartbeat_at_utc, expires_at_utc
    ) VALUES (
        NEWID(), @partition_id, @execution_id, @database_now_utc, @database_now_utc,
        DATEADD(SECOND, @lease_seconds, @database_now_utc)
    );

    UPDATE ctl.execution_partition
    SET current_execution_id = @execution_id,
        current_state = N'EXTRACTING'
    WHERE partition_id = @partition_id;

    COMMIT TRANSACTION;
END;
GO

CREATE OR ALTER PROCEDURE ctl.usp_control_plane_transition_execution
    @execution_id UNIQUEIDENTIFIER,
    @expected_current_state NVARCHAR(MAX),
    @next_state NVARCHAR(MAX),
    @reason_code NVARCHAR(MAX),
    @transitioned_at_utc DATETIME2(3)
AS
BEGIN
    -- B54: validate current SQL authority consumption before any effect/read.
    DECLARE @b54_actual NVARCHAR(MAX)=(SELECT LOWER(COALESCE(CONVERT(NVARCHAR(36),@execution_id),N'')) AS [execution] FOR JSON PATH,WITHOUT_ARRAY_WRAPPER,INCLUDE_NULL_VALUES);
    IF ctl.fn_runtime_consumed_scope(@b54_actual,1,NULL)<>1 THROW 52500,N'RUNTIME_DURABLE_CONSUMER_FENCE_REQUIRED',1;

    SET NOCOUNT ON;
    SET XACT_ABORT ON;

    IF DATALENGTH(@expected_current_state) > 64
        OR DATALENGTH(@next_state) > 64
        OR DATALENGTH(@reason_code) > 128
        THROW 51317, N'Transição excede o limite textual.', 1;

    SET @expected_current_state = LTRIM(RTRIM(@expected_current_state));
    SET @next_state = LTRIM(RTRIM(@next_state));
    SET @reason_code = LTRIM(RTRIM(@reason_code));

    IF @execution_id IS NULL
        OR @expected_current_state IS NULL
        OR @next_state IS NULL
        OR NULLIF(LTRIM(RTRIM(@reason_code)), N'') IS NULL
        OR LEFT(@reason_code, 1) COLLATE Latin1_General_100_BIN2 NOT LIKE '[A-Z]'
        OR @reason_code COLLATE Latin1_General_100_BIN2 LIKE '%[^A-Z0-9_]%'
        OR LEN(@reason_code) NOT BETWEEN 2 AND 64
        OR @transitioned_at_utc IS NULL
        OR @next_state COLLATE Latin1_General_100_BIN2
            IN (N'PROMOTED', N'RECONCILED', N'PUBLISHED')
        THROW 51317, N'Transição de execução inválida.', 1;

    BEGIN TRANSACTION;

    DECLARE @partition_id BIGINT;
    DECLARE @current_state NVARCHAR(32);
    DECLARE @next_transition_sequence INT;
    SELECT
        @partition_id = partition_id,
        @current_state = current_state,
        @next_transition_sequence = next_transition_sequence
    FROM ctl.execution_attempt WITH (UPDLOCK, HOLDLOCK)
    WHERE execution_id = @execution_id;

    IF @partition_id IS NULL
        THROW 51318, N'Execução inexistente para transição.', 1;

    DECLARE @event_sequence_to_lock INT = CASE
        WHEN @current_state COLLATE Latin1_General_100_BIN2
                = @next_state COLLATE Latin1_General_100_BIN2
            THEN @next_transition_sequence - 1
        ELSE @next_transition_sequence
    END;
    DECLARE @existing_event_id BIGINT;
    DECLARE @existing_event_previous_state NVARCHAR(32);
    DECLARE @existing_event_next_state NVARCHAR(32);
    DECLARE @existing_event_reason_code NVARCHAR(64);
    SELECT
        @existing_event_id = state_event_id,
        @existing_event_previous_state = previous_state,
        @existing_event_next_state = next_state,
        @existing_event_reason_code = reason_code
    FROM ctl.execution_state_event WITH (UPDLOCK, HOLDLOCK)
    WHERE execution_id = @execution_id
      AND transition_sequence = @event_sequence_to_lock;

    IF @current_state COLLATE Latin1_General_100_BIN2
            = @next_state COLLATE Latin1_General_100_BIN2
    BEGIN
        IF @existing_event_id IS NOT NULL
            AND @existing_event_previous_state COLLATE Latin1_General_100_BIN2
                = @expected_current_state COLLATE Latin1_General_100_BIN2
            AND @existing_event_next_state COLLATE Latin1_General_100_BIN2
                = @next_state COLLATE Latin1_General_100_BIN2
            AND @existing_event_reason_code COLLATE Latin1_General_100_BIN2
                = @reason_code COLLATE Latin1_General_100_BIN2
        BEGIN
            COMMIT TRANSACTION;
            RETURN;
        END;

        THROW 51319, N'Estado atual já foi alcançado por outra transição imutável.', 1;
    END;

    IF @existing_event_id IS NOT NULL
        THROW 51319, N'O slot imutável da próxima transição já está ocupado.', 1;

    IF @current_state COLLATE Latin1_General_100_BIN2
            <> @expected_current_state COLLATE Latin1_General_100_BIN2
        THROW 51319, N'Estado atual diverge da transição esperada.', 1;

    DECLARE @lease_expires_at_utc DATETIME2(3);
    DECLARE @lease_released_at_utc DATETIME2(3);
    SELECT
        @lease_expires_at_utc = lease.expires_at_utc,
        @lease_released_at_utc = lease.released_at_utc
    FROM ctl.execution_partition AS partition WITH (UPDLOCK, HOLDLOCK)
        INNER JOIN ctl.execution_lease AS lease WITH (UPDLOCK, HOLDLOCK)
            ON lease.partition_id = partition.partition_id
           AND lease.execution_id = @execution_id
    WHERE partition.partition_id = @partition_id
      AND partition.current_execution_id = @execution_id;

    DECLARE @database_now_utc DATETIME2(3) = SYSUTCDATETIME();

    IF @lease_expires_at_utc IS NULL
        OR @lease_released_at_utc IS NOT NULL
        OR @lease_expires_at_utc <= @database_now_utc
        THROW 51330, N'Execução sem lease corrente e ativa para transição.', 1;

    IF NOT (
        (@current_state COLLATE Latin1_General_100_BIN2 = N'PLANNED'
            AND @next_state COLLATE Latin1_General_100_BIN2
                IN (N'EXTRACTING', N'BLOCKED', N'SKIPPED', N'NOT_APPLICABLE', N'FAILED', N'CANCELLED'))
        OR (@current_state COLLATE Latin1_General_100_BIN2 = N'EXTRACTING'
            AND @next_state COLLATE Latin1_General_100_BIN2
                IN (N'EXTRACTED', N'BLOCKED', N'FAILED', N'CANCELLED', N'DEGRADED'))
        OR (@current_state COLLATE Latin1_General_100_BIN2 = N'EXTRACTED'
            AND @next_state COLLATE Latin1_General_100_BIN2
                IN (N'STAGED', N'BLOCKED', N'FAILED', N'CANCELLED', N'DEGRADED'))
        OR (@current_state COLLATE Latin1_General_100_BIN2 = N'STAGED'
            AND @next_state COLLATE Latin1_General_100_BIN2
                IN (N'BLOCKED', N'FAILED', N'CANCELLED', N'DEGRADED'))
        OR (@current_state COLLATE Latin1_General_100_BIN2 = N'PROMOTED'
            AND @next_state COLLATE Latin1_General_100_BIN2
                IN (N'BLOCKED', N'FAILED', N'CANCELLED', N'DEGRADED'))
        OR (@current_state COLLATE Latin1_General_100_BIN2 = N'RECONCILED'
            AND @next_state COLLATE Latin1_General_100_BIN2
                IN (N'BLOCKED', N'FAILED', N'CANCELLED', N'DEGRADED'))
    )
        THROW 51320, N'Transição de estado não permitida.', 1;

    DECLARE @allocated_transition TABLE (transition_sequence INT NOT NULL);
    UPDATE ctl.execution_attempt
    SET current_state = @next_state,
        terminal_at_utc = CASE WHEN @next_state COLLATE Latin1_General_100_BIN2
                IN (N'BLOCKED', N'SKIPPED', N'NOT_APPLICABLE', N'FAILED', N'CANCELLED', N'DEGRADED')
                               THEN @database_now_utc ELSE NULL END,
        next_transition_sequence = next_transition_sequence + 1
    OUTPUT deleted.next_transition_sequence
        INTO @allocated_transition (transition_sequence)
    WHERE execution_id = @execution_id
      AND current_state = @current_state;

    DECLARE @sequence INT;
    SELECT @sequence = transition_sequence FROM @allocated_transition;
    IF @sequence IS NULL
        THROW 51332, N'Não foi possível alocar transition_sequence atomicamente.', 1;

    INSERT INTO ctl.execution_state_event (
        execution_id, transition_sequence, previous_state, next_state, reason_code, transitioned_at_utc
    ) VALUES (
        @execution_id, @sequence, @current_state, @next_state, @reason_code, @database_now_utc
    );

    UPDATE ctl.execution_partition
    SET current_execution_id = @execution_id,
        current_state = @next_state
    WHERE partition_id = @partition_id;

    IF @next_state COLLATE Latin1_General_100_BIN2
            IN (N'BLOCKED', N'SKIPPED', N'NOT_APPLICABLE', N'FAILED', N'CANCELLED', N'DEGRADED')
    BEGIN
        UPDATE ctl.execution_lease
        SET released_at_utc = @database_now_utc
        WHERE execution_id = @execution_id AND released_at_utc IS NULL;
    END;

    COMMIT TRANSACTION;
END;
GO

CREATE OR ALTER PROCEDURE ctl.usp_runtime_recovery
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

CREATE OR ALTER PROCEDURE ctl.usp_runtime_status
    @execution_id UNIQUEIDENTIFIER,@operation NVARCHAR(MAX),@expected_identity NVARCHAR(MAX),
    @contract_material NVARCHAR(MAX),@policy_version NVARCHAR(MAX),@policy_fingerprint NVARCHAR(MAX),
    @expected_revision NVARCHAR(MAX),@entity NVARCHAR(MAX)
AS
BEGIN
    -- B54: validate current SQL authority consumption before any effect/read.
    DECLARE @b54_actual NVARCHAR(MAX)=(SELECT LOWER(COALESCE(CONVERT(NVARCHAR(36),@execution_id),N'')) AS [execution] FOR JSON PATH,WITHOUT_ARRAY_WRAPPER,INCLUDE_NULL_VALUES);
    IF ctl.fn_runtime_consumed_scope(@b54_actual,0,NULL)<>1 THROW 52500,N'RUNTIME_DURABLE_CONSUMER_FENCE_REQUIRED',1;

    SET NOCOUNT ON;
    IF @operation IS NULL OR @operation COLLATE Latin1_General_100_BIN2<>N'READ' OR DATALENGTH(@operation)<>8
        THROW 52416,N'STATUS_CANNOT_MUTATE_RUNTIME',1;
    EXEC ctl.usp_runtime_recovery @execution_id,N'READ',@expected_identity,@contract_material,
        @policy_version,@policy_fingerprint,@expected_revision,@entity;
END;
GO

CREATE OR ALTER PROCEDURE ctl.usp_runtime_temporal_gaps @namespace_fingerprint CHAR(64),@maximum INT,@after_utc DATETIME2(3)
AS
BEGIN
    -- B54: validate current SQL authority consumption before any effect/read.
    DECLARE @b54_actual NVARCHAR(MAX)=N'{}';
    IF ctl.fn_runtime_consumed_scope(@b54_actual,0,@namespace_fingerprint)<>1 THROW 52500,N'RUNTIME_DURABLE_CONSUMER_FENCE_REQUIRED',1;

    SET NOCOUNT ON;
    IF @maximum NOT BETWEEN 1 AND 64 OR @namespace_fingerprint IS NULL OR @after_utc IS NULL
        THROW 52424,N'TEMPORAL_RECONCILIATION_LIMIT',1;
    SELECT TOP(@maximum) w.plan_id,w.ordinal,w.execution_id,w.partition_start_utc,w.partition_end_exclusive_utc,
        CASE WHEN p.execution_id IS NOT NULL THEN N'PUBLISHED' WHEN a.execution_id IS NULL THEN N'NOT_STARTED'
            ELSE a.current_state END state,w.policy_version,w.policy_fingerprint
    FROM ctl.runtime_temporal_window w LEFT JOIN ctl.execution_attempt a ON a.execution_id=w.execution_id
    LEFT JOIN ctl.execution_publication_event p ON p.execution_id=w.execution_id
    WHERE w.namespace_fingerprint=@namespace_fingerprint AND w.partition_end_exclusive_utc>@after_utc
    ORDER BY w.partition_start_utc,w.partition_end_exclusive_utc;
END;
GO

CREATE OR ALTER PROCEDURE ctl.usp_runtime_temporal_plan
    @plan_id UNIQUEIDENTIFIER,@namespace_fingerprint NVARCHAR(MAX),@policy_version NVARCHAR(MAX),
    @policy_fingerprint NVARCHAR(MAX),@policy_material NVARCHAR(MAX),@windows NVARCHAR(MAX)
AS
BEGIN
    -- B54: validate current SQL authority consumption before any effect/read.
    DECLARE @b54_actual NVARCHAR(MAX)=(SELECT LOWER(CONVERT(NVARCHAR(36),@plan_id)) AS [cycle],@policy_version AS [contractVersion],@policy_fingerprint AS [contractHash],JSON_VALUE(@windows,'$[0].execution') AS [execution] FOR JSON PATH,WITHOUT_ARRAY_WRAPPER,INCLUDE_NULL_VALUES);
    IF IS_SRVROLEMEMBER(N'sysadmin')<>1 AND IS_MEMBER(N'db_owner')<>1 AND (ISJSON(@windows)<>1 OR (SELECT COUNT(*) FROM OPENJSON(@windows))<>1) THROW 52500,N'RUNTIME_TEMPORAL_SINGLE_AUTHORIZED_WINDOW_REQUIRED',1;
    IF ctl.fn_runtime_consumed_scope(@b54_actual,1,@namespace_fingerprint)<>1 THROW 52500,N'RUNTIME_DURABLE_CONSUMER_FENCE_REQUIRED',1;

    SET NOCOUNT ON;SET XACT_ABORT ON;
    IF @plan_id IS NULL OR @policy_material IS NULL OR DATALENGTH(@policy_material)>8000 OR ISJSON(@policy_material)<>1
        OR @windows IS NULL OR DATALENGTH(@windows)>65536 OR ISJSON(@windows)<>1 OR LEFT(LTRIM(@windows),1)<>N'['
        OR @policy_version IS NULL OR DATALENGTH(@policy_version) NOT BETWEEN 2 AND 256
        OR @policy_fingerprint IS NULL OR DATALENGTH(@policy_fingerprint)<>128
        OR @policy_fingerprint<>LOWER(CONVERT(CHAR(64),HASHBYTES('SHA2_256',@policy_material),2))
        OR @namespace_fingerprint IS NULL OR DATALENGTH(@namespace_fingerprint)<>128 OR @namespace_fingerprint COLLATE Latin1_General_100_BIN2 LIKE '%[^a-f0-9]%'
        THROW 52421,N'TEMPORAL_PLAN_INVALID',1;
    IF (SELECT COUNT_BIG(*) FROM OPENJSON(@policy_material))<>15
        OR EXISTS(SELECT 1 FROM OPENJSON(@policy_material) GROUP BY [key] COLLATE Latin1_General_100_BIN2 HAVING COUNT_BIG(*)<>1)
        OR EXISTS(SELECT 1 FROM OPENJSON(@policy_material) WHERE [key] COLLATE Latin1_General_100_BIN2 NOT IN
            (N'version',N'zone',N'mode',N'strategy',N'cadence',N'boundary',N'lookback',N'stabilization',N'sla',N'deadline',
             N'concurrency',N'maximumBacklog',N'maximumReconciliation',N'maximumDegraded',N'blackouts'))
        OR EXISTS(SELECT 1 FROM OPENJSON(@policy_material) WHERE
            ([key] IN(N'concurrency',N'maximumBacklog',N'maximumReconciliation',N'maximumDegraded') AND type<>2)
            OR ([key]=N'blackouts' AND type<>4)
            OR ([key] NOT IN(N'concurrency',N'maximumBacklog',N'maximumReconciliation',N'maximumDegraded',N'blackouts')
                AND (type<>1 OR DATALENGTH(value)=0)))
        OR JSON_VALUE(@policy_material,'$.version') COLLATE Latin1_General_100_BIN2<>@policy_version
        OR ISNULL(TRY_CONVERT(INT,JSON_VALUE(@policy_material,'$.concurrency')),0) NOT BETWEEN 1 AND 4
        OR ISNULL(TRY_CONVERT(INT,JSON_VALUE(@policy_material,'$.maximumBacklog')),0) NOT BETWEEN 1 AND 64
        OR ISNULL(TRY_CONVERT(INT,JSON_VALUE(@policy_material,'$.maximumReconciliation')),0) NOT BETWEEN 1 AND 64
        OR ISNULL(TRY_CONVERT(INT,JSON_VALUE(@policy_material,'$.maximumDegraded')),-1) NOT BETWEEN 0 AND 64
        OR TRY_CONVERT(INT,JSON_VALUE(@policy_material,'$.maximumDegraded'))>TRY_CONVERT(INT,JSON_VALUE(@policy_material,'$.maximumReconciliation'))
        OR JSON_VALUE(@policy_material,'$.strategy') COLLATE Latin1_General_100_BIN2<>N'INTERVAL'
        OR JSON_VALUE(@policy_material,'$.cadence') COLLATE Latin1_General_100_BIN2 NOT IN(N'CIVIL_DAY',N'CIVIL_MONTH')
        OR JSON_VALUE(@policy_material,'$.mode') COLLATE Latin1_General_100_BIN2 NOT IN(N'INCREMENTAL',N'BACKFILL',N'BOOTSTRAP',N'REPLAY')
        OR JSON_QUERY(@policy_material,'$.blackouts') IS NULL
        THROW 52421,N'TEMPORAL_POLICY_SCHEMA_INVALID',1;
    DECLARE @bounded TABLE(ordinal INT NOT NULL PRIMARY KEY,execution_id UNIQUEIDENTIFIER NOT NULL UNIQUE,
        start_utc DATETIME2(3) NOT NULL,end_utc DATETIME2(3) NOT NULL,extraction_utc DATETIME2(3) NOT NULL,
        due_utc DATETIME2(3) NOT NULL,deadline_utc DATETIME2(3) NOT NULL);
    IF (SELECT COUNT_BIG(*) FROM OPENJSON(@windows)) NOT BETWEEN 1 AND 64 THROW 52421,N'TEMPORAL_WINDOW_LIMIT',1;
    IF (SELECT COUNT_BIG(*) FROM OPENJSON(@windows))>TRY_CONVERT(INT,JSON_VALUE(@policy_material,'$.maximumBacklog'))
        OR EXISTS(SELECT 1 FROM OPENJSON(@windows) j WHERE j.type<>5 OR (SELECT COUNT_BIG(*) FROM OPENJSON(j.value))<>6)
        OR EXISTS(SELECT 1 FROM OPENJSON(@windows) j CROSS APPLY OPENJSON(j.value) v
            WHERE v.type<>1 OR v.[key] COLLATE Latin1_General_100_BIN2 NOT IN(N'execution',N'start',N'endExclusive',N'extractionStart',N'due',N'deadline'))
        OR EXISTS(SELECT 1 FROM OPENJSON(@windows) j CROSS APPLY OPENJSON(j.value) v GROUP BY j.[key],v.[key] COLLATE Latin1_General_100_BIN2 HAVING COUNT_BIG(*)<>1)
        THROW 52421,N'TEMPORAL_WINDOW_SCHEMA_INVALID',1;
    INSERT @bounded SELECT CONVERT(INT,j.[key])+1,v.execution_id,v.start_utc,v.end_utc,v.extraction_utc,v.due_utc,v.deadline_utc
    FROM OPENJSON(@windows) j CROSS APPLY OPENJSON(j.value) WITH(execution_id UNIQUEIDENTIFIER '$.execution',
        start_utc DATETIME2(3) '$.start',end_utc DATETIME2(3) '$.endExclusive',extraction_utc DATETIME2(3) '$.extractionStart',
        due_utc DATETIME2(3) '$.due',deadline_utc DATETIME2(3) '$.deadline') v;
    IF EXISTS(SELECT 1 FROM @bounded WHERE start_utc>=end_utc OR extraction_utc>start_utc OR due_utc>=deadline_utc)
        OR EXISTS(SELECT 1 FROM @bounded a JOIN @bounded b ON b.ordinal=a.ordinal+1 WHERE a.end_utc<>b.start_utc)
        THROW 52421,N'TEMPORAL_WINDOW_NOT_CONTIGUOUS',1;
    BEGIN TRY
        BEGIN TRANSACTION;
        DECLARE @lock INT,@resource NVARCHAR(255)=N'V2_TEMPORAL_'+@namespace_fingerprint;
        EXEC @lock=sys.sp_getapplock @Resource=@resource,@LockMode='Exclusive',@LockOwner='Transaction',@LockTimeout=10000;
        IF @lock<0 THROW 52422,N'TEMPORAL_PLAN_LOCK',1;
        IF EXISTS(SELECT 1 FROM ctl.runtime_temporal_policy WITH(UPDLOCK,HOLDLOCK) WHERE policy_version=@policy_version
            AND policy_fingerprint=@policy_fingerprint AND (policy_material<>@policy_material OR DATALENGTH(policy_material)<>DATALENGTH(@policy_material)))
            THROW 52423,N'TEMPORAL_POLICY_CONFLICT',1;
        IF NOT EXISTS(SELECT 1 FROM ctl.runtime_temporal_policy WITH(UPDLOCK,HOLDLOCK) WHERE policy_version=@policy_version AND policy_fingerprint=@policy_fingerprint)
            INSERT ctl.runtime_temporal_policy VALUES(@policy_version,@policy_fingerprint,@policy_material,SYSUTCDATETIME());
        IF EXISTS(SELECT 1 FROM ctl.runtime_temporal_window WITH(UPDLOCK,HOLDLOCK) WHERE plan_id=@plan_id)
        BEGIN
            IF (SELECT COUNT_BIG(*) FROM ctl.runtime_temporal_window WHERE plan_id=@plan_id)<>(SELECT COUNT_BIG(*) FROM @bounded)
                OR EXISTS(SELECT 1 FROM @bounded b LEFT JOIN ctl.runtime_temporal_window w ON w.plan_id=@plan_id AND w.ordinal=b.ordinal
                    WHERE w.execution_id IS NULL OR w.execution_id<>b.execution_id OR w.namespace_fingerprint<>@namespace_fingerprint
                        OR w.policy_version<>@policy_version OR w.policy_fingerprint<>@policy_fingerprint OR w.partition_start_utc<>b.start_utc
                        OR w.partition_end_exclusive_utc<>b.end_utc OR w.extraction_start_utc<>b.extraction_utc OR w.due_at_utc<>b.due_utc OR w.deadline_at_utc<>b.deadline_utc)
                THROW 52423,N'TEMPORAL_PLAN_CONFLICT',1;
        END
        ELSE INSERT ctl.runtime_temporal_window SELECT @plan_id,ordinal,execution_id,@namespace_fingerprint,@policy_version,@policy_fingerprint,
            start_utc,end_utc,extraction_utc,due_utc,deadline_utc FROM @bounded;
        COMMIT TRANSACTION;
        SELECT COUNT_BIG(*) persisted_windows FROM ctl.runtime_temporal_window WHERE plan_id=@plan_id;
    END TRY BEGIN CATCH IF XACT_STATE()<>0 ROLLBACK TRANSACTION;THROW;END CATCH;
END;
GO
CREATE OR ALTER PROCEDURE recon.usp_evaluate_execution_data_quality
    @execution_id UNIQUEIDENTIFIER,
    @policy_version NVARCHAR(MAX),
    @policy_fingerprint NVARCHAR(MAX)
AS
BEGIN
    -- B54: validate current SQL authority consumption before any effect/read.
    DECLARE @b54_actual NVARCHAR(MAX)=(SELECT LOWER(COALESCE(CONVERT(NVARCHAR(36),@execution_id),N'')) AS [execution] FOR JSON PATH,WITHOUT_ARRAY_WRAPPER,INCLUDE_NULL_VALUES);
    IF ctl.fn_runtime_consumed_scope(@b54_actual,1,NULL)<>1 THROW 52500,N'RUNTIME_DURABLE_CONSUMER_FENCE_REQUIRED',1;

    SET NOCOUNT ON;
    SET XACT_ABORT ON;

    IF @execution_id IS NULL
        OR NULLIF(LTRIM(RTRIM(@policy_version)), N'') IS NULL
        OR DATALENGTH(@policy_version) <> DATALENGTH(LTRIM(RTRIM(@policy_version)))
        OR DATALENGTH(@policy_version) > 256
        OR LEFT(@policy_version, 1) COLLATE Latin1_General_100_BIN2 NOT LIKE '[A-Za-z0-9]'
        OR @policy_version COLLATE Latin1_General_100_BIN2 LIKE '%[^-A-Za-z0-9._]%'
        OR @policy_fingerprint IS NULL
        OR DATALENGTH(@policy_fingerprint) <> 128
        OR @policy_fingerprint COLLATE Latin1_General_100_BIN2 LIKE '%[^0-9a-f]%'
        THROW 51601, N'A avaliação exige execução e policy canônicas.', 1;

    BEGIN TRANSACTION;

    DECLARE @database_now_utc DATETIME2(3);
    DECLARE @partition_id BIGINT;
    DECLARE @execution_state NVARCHAR(32);
    DECLARE @environment_name NVARCHAR(32);
    DECLARE @source_instance NVARCHAR(128);
    DECLARE @tenant_scope NVARCHAR(128);
    DECLARE @entity_name NVARCHAR(128);
    DECLARE @execution_mode NVARCHAR(16);

    SELECT
        @partition_id = attempt.partition_id,
        @execution_state = attempt.current_state,
        @environment_name = partition.environment_name,
        @source_instance = partition.source_instance,
        @tenant_scope = partition.tenant_scope,
        @entity_name = partition.entity_name,
        @execution_mode = partition.execution_mode
    FROM ctl.execution_attempt AS attempt
    INNER JOIN ctl.execution_partition AS partition
        ON partition.partition_id = attempt.partition_id
    WHERE attempt.execution_id = @execution_id;

    IF @partition_id IS NULL
        THROW 51602, N'A execução de Data Quality não existe.', 1;

    DECLARE @lock_payload NVARCHAR(2000) = CONCAT(
        DATALENGTH(@environment_name), N':', @environment_name, N'|',
        DATALENGTH(@source_instance), N':', @source_instance, N'|',
        DATALENGTH(@tenant_scope), N':', @tenant_scope, N'|',
        DATALENGTH(@entity_name), N':', @entity_name
    );
    DECLARE @lock_resource NVARCHAR(255) = CONCAT(
        N'V2_APPLY_', CONVERT(NVARCHAR(64), HASHBYTES('SHA2_256', @lock_payload), 2)
    );
    DECLARE @application_lock_result INT;
    EXEC @application_lock_result = sys.sp_getapplock
        @Resource = @lock_resource,
        @LockMode = 'Exclusive',
        @LockOwner = 'Transaction',
        @LockTimeout = 10000,
        @DbPrincipal = 'public';
    IF @application_lock_result < 0
        THROW 51603, N'Não foi possível serializar a avaliação de Data Quality.', 1;

    -- Releitura sob o mesmo lock do apply evita um permit separado do candidate set.
    SELECT
        @execution_state = attempt.current_state,
        @partition_id = partition.partition_id,
        @environment_name = partition.environment_name,
        @source_instance = partition.source_instance,
        @tenant_scope = partition.tenant_scope,
        @entity_name = partition.entity_name,
        @execution_mode = partition.execution_mode
    FROM ctl.execution_attempt AS attempt WITH (UPDLOCK, HOLDLOCK)
    INNER JOIN ctl.execution_partition AS partition WITH (UPDLOCK, HOLDLOCK)
        ON partition.partition_id = attempt.partition_id
    WHERE attempt.execution_id = @execution_id;

    IF @execution_state NOT IN (N'PROMOTED', N'PUBLISHED')
        THROW 51604, N'A execução não está promovida para Data Quality.', 1;

    -- Vigência e SLA usam o instante posterior à espera do fence compartilhado com o apply.
    SET @database_now_utc = SYSUTCDATETIME();

    DECLARE @scope_material NVARCHAR(2000) = CONCAT(
        N'dq-scope-v1|',
        DATALENGTH(@environment_name), N':', @environment_name, N'|',
        DATALENGTH(@source_instance), N':', @source_instance, N'|',
        DATALENGTH(@tenant_scope), N':', @tenant_scope, N'|',
        DATALENGTH(@entity_name), N':', @entity_name, N'|',
        DATALENGTH(@execution_mode), N':', @execution_mode
    );
    DECLARE @scope_fingerprint CHAR(64) = LOWER(CONVERT(
        CHAR(64), HASHBYTES('SHA2_256', CONVERT(VARBINARY(MAX), @scope_material)), 2
    ));
    DECLARE @expected_checks SMALLINT;
    DECLARE @quarantine_sla_seconds BIGINT;

    DECLARE @candidate_rows BIGINT;
    DECLARE @promotion_physical_rows BIGINT;
    DECLARE @promotion_distinct_rows BIGINT;
    DECLARE @promotion_duplicate_rows BIGINT;
    DECLARE @promotion_quarantined_roots BIGINT;
    DECLARE @promotion_unidentified BIGINT;
    DECLARE @promotion_quarantined_stage BIGINT;
    DECLARE @promotion_recorded_at_utc DATETIME2(3);

    SELECT
        @candidate_rows = promotion.candidate_rows,
        @promotion_physical_rows = promotion.physical_rows,
        @promotion_distinct_rows = promotion.distinct_root_keys,
        @promotion_duplicate_rows = promotion.duplicate_rows,
        @promotion_quarantined_roots = promotion.quarantined_root_keys,
        @promotion_unidentified = promotion.unidentified_quarantine_rows,
        @promotion_quarantined_stage = promotion.quarantined_stage_rows,
        @promotion_recorded_at_utc = promotion.promoted_at_utc
    FROM ctl.execution_promotion_result AS promotion WITH (UPDLOCK, HOLDLOCK)
    WHERE promotion.execution_id = @execution_id;

    IF @candidate_rows IS NULL
        THROW 51606, N'A evidência do candidate set está ausente.', 1;

    IF EXISTS (
        SELECT 1
        FROM recon.execution_data_quality_evaluation AS evaluation WITH (UPDLOCK, HOLDLOCK)
        WHERE evaluation.execution_id = @execution_id
          AND (
              evaluation.policy_version <> @policy_version COLLATE Latin1_General_100_BIN2
              OR evaluation.policy_fingerprint
                    <> @policy_fingerprint COLLATE Latin1_General_100_BIN2
              OR evaluation.scope_fingerprint <> @scope_fingerprint
              OR evaluation.candidate_rows <> @candidate_rows
              OR evaluation.promotion_recorded_at_utc <> @promotion_recorded_at_utc
              OR evaluation.completed_checks <> evaluation.expected_checks
              OR evaluation.completed_checks <> (
                  SELECT COUNT_BIG(*)
                  FROM recon.execution_data_quality_check_result AS result WITH (HOLDLOCK)
                  WHERE result.execution_id = @execution_id
              )
          )
    )
        THROW 51607, N'O retry de Data Quality diverge da evidência imutável.', 1;

    DECLARE @has_existing_evaluation BIT = CASE WHEN EXISTS (
        SELECT 1 FROM recon.execution_data_quality_evaluation WITH (UPDLOCK, HOLDLOCK)
        WHERE execution_id = @execution_id
    ) THEN 1 ELSE 0 END;

    IF @execution_state = N'PUBLISHED' AND @has_existing_evaluation = 0
        THROW 51616, N'A avaliação retroativa após publicação foi recusada.', 1;

    IF @execution_state = N'PUBLISHED'
    BEGIN
        IF NOT EXISTS (
            SELECT 1
            FROM recon.execution_data_quality_evaluation AS evaluation WITH (HOLDLOCK)
            INNER JOIN ctl.execution_publication_event AS publication WITH (HOLDLOCK)
                ON publication.execution_id = evaluation.execution_id
            WHERE evaluation.execution_id = @execution_id
              AND evaluation.evaluated_at_utc <= publication.published_at_utc
        )
            THROW 51607, N'O retry de Data Quality diverge da evidência imutável.', 1;

        COMMIT TRANSACTION;
        SELECT
            execution_id, policy_version, policy_fingerprint, evaluation_fingerprint,
            expected_checks, completed_checks, passed_checks, failed_checks,
            evaluated_rows, failed_rows, evaluation_state, evaluated_at_utc
        FROM recon.execution_data_quality_evaluation
        WHERE execution_id = @execution_id;
        RETURN;
    END;

    DECLARE @policy_material NVARCHAR(4000);
    DECLARE @policy_effective_from_utc DATETIME2(3);
    SELECT
        @expected_checks = policy.expected_checks,
        @quarantine_sla_seconds = policy.quarantine_sla_seconds,
        @policy_effective_from_utc = policy.effective_from_utc,
        @policy_material = CONCAT(
            N'dq-policy-v1|',
            DATALENGTH(policy.policy_version), N':', policy.policy_version, N'|',
            policy.scope_fingerprint, N'|', policy.expected_checks, N'|',
            policy.quarantine_sla_seconds, N'|',
            DATALENGTH(policy.threshold_owner_role), N':', policy.threshold_owner_role, N'|',
            DATALENGTH(policy.quarantine_sla_owner_role), N':',
                policy.quarantine_sla_owner_role, N'|',
            DATALENGTH(policy.retention_policy_version), N':',
                policy.retention_policy_version, N'|',
            DATALENGTH(policy.retention_owner_role), N':', policy.retention_owner_role, N'|',
            CONVERT(NVARCHAR(33), policy.effective_from_utc, 126), N'|',
            check_1.check_ordinal, N':', check_1.check_code, N':',
                check_1.maximum_failed_rows, N':', check_1.maximum_failure_basis_points, N':',
                DATALENGTH(check_1.threshold_owner_role), N':', check_1.threshold_owner_role, N'|',
            check_2.check_ordinal, N':', check_2.check_code, N':',
                check_2.maximum_failed_rows, N':', check_2.maximum_failure_basis_points, N':',
                DATALENGTH(check_2.threshold_owner_role), N':', check_2.threshold_owner_role, N'|',
            check_3.check_ordinal, N':', check_3.check_code, N':',
                check_3.maximum_failed_rows, N':', check_3.maximum_failure_basis_points, N':',
                DATALENGTH(check_3.threshold_owner_role), N':', check_3.threshold_owner_role, N'|',
            check_4.check_ordinal, N':', check_4.check_code, N':',
                check_4.maximum_failed_rows, N':', check_4.maximum_failure_basis_points, N':',
                DATALENGTH(check_4.threshold_owner_role), N':', check_4.threshold_owner_role
        )
    FROM ctl.data_quality_policy AS policy WITH (UPDLOCK, HOLDLOCK)
    INNER JOIN ctl.data_quality_check_policy AS check_1 WITH (UPDLOCK, HOLDLOCK)
        ON check_1.policy_version = policy.policy_version
       AND check_1.policy_fingerprint = policy.policy_fingerprint
       AND check_1.check_ordinal = 1 AND check_1.check_code = N'COUNT_EQUATION'
    INNER JOIN ctl.data_quality_check_policy AS check_2 WITH (UPDLOCK, HOLDLOCK)
        ON check_2.policy_version = policy.policy_version
       AND check_2.policy_fingerprint = policy.policy_fingerprint
       AND check_2.check_ordinal = 2 AND check_2.check_code = N'PAGE_TERMINALITY'
    INNER JOIN ctl.data_quality_check_policy AS check_3 WITH (UPDLOCK, HOLDLOCK)
        ON check_3.policy_version = policy.policy_version
       AND check_3.policy_fingerprint = policy.policy_fingerprint
       AND check_3.check_ordinal = 3 AND check_3.check_code = N'PROMOTION_RECONCILIATION'
    INNER JOIN ctl.data_quality_check_policy AS check_4 WITH (UPDLOCK, HOLDLOCK)
        ON check_4.policy_version = policy.policy_version
       AND check_4.policy_fingerprint = policy.policy_fingerprint
       AND check_4.check_ordinal = 4 AND check_4.check_code = N'QUARANTINE_SLA'
    WHERE policy.policy_version = @policy_version COLLATE Latin1_General_100_BIN2
      AND policy.policy_fingerprint = @policy_fingerprint COLLATE Latin1_General_100_BIN2
      AND policy.scope_fingerprint = @scope_fingerprint
      AND policy.policy_state = N'RATIFIED'
      AND policy.effective_from_utc <= @database_now_utc
      AND NOT EXISTS (
          SELECT 1
          FROM ctl.data_quality_policy AS newer WITH (UPDLOCK, HOLDLOCK)
          WHERE newer.scope_fingerprint = policy.scope_fingerprint
            AND newer.effective_from_utc <= @database_now_utc
            AND newer.effective_from_utc > policy.effective_from_utc
      );

    DECLARE @calculated_policy_fingerprint CHAR(64) = LOWER(CONVERT(
        CHAR(64), HASHBYTES('SHA2_256', CONVERT(VARBINARY(MAX), @policy_material)), 2
    ));
    IF @expected_checks <> 4
        OR @quarantine_sla_seconds IS NULL
        OR @policy_material IS NULL
        OR @calculated_policy_fingerprint <> @policy_fingerprint
        THROW 51605, N'A policy de Data Quality não está íntegra, vigente e ratificada.', 1;

    IF @has_existing_evaluation = 1
    BEGIN
        COMMIT TRANSACTION;
        SELECT
            execution_id, policy_version, policy_fingerprint, evaluation_fingerprint,
            expected_checks, completed_checks, passed_checks, failed_checks,
            evaluated_rows, failed_rows, evaluation_state, evaluated_at_utc
        FROM recon.execution_data_quality_evaluation
        WHERE execution_id = @execution_id;
        RETURN;
    END;

    DECLARE @count_rows BIGINT;
    DECLARE @count_physical BIGINT;
    DECLARE @count_distinct BIGINT;
    DECLARE @count_duplicate BIGINT;
    DECLARE @count_valid BIGINT;
    DECLARE @count_quarantined BIGINT;
    DECLARE @count_unidentified BIGINT;
    SELECT
        @count_rows = COUNT_BIG(*),
        @count_physical = MAX(physical_rows),
        @count_distinct = MAX(distinct_root_keys),
        @count_duplicate = MAX(duplicate_rows),
        @count_valid = MAX(valid_rows),
        @count_quarantined = MAX(quarantined_root_keys),
        @count_unidentified = MAX(unidentified_quarantine_rows)
    FROM ctl.execution_count WITH (UPDLOCK, HOLDLOCK)
    WHERE execution_id = @execution_id
      AND count_phase = N'STAGING_KERNEL';

    DECLARE @stage_rows BIGINT = (
        SELECT COUNT_BIG(*) FROM stg.execution_record WITH (UPDLOCK, HOLDLOCK)
        WHERE execution_id = @execution_id
    );
    DECLARE @actual_candidate_rows BIGINT = (
        SELECT COUNT_BIG(*) FROM stg.execution_candidate WITH (UPDLOCK, HOLDLOCK)
        WHERE execution_id = @execution_id
    );
    DECLARE @actual_quarantine_rows BIGINT = (
        SELECT COUNT_BIG(*) FROM recon.quarantine_record WITH (UPDLOCK, HOLDLOCK)
        WHERE execution_id = @execution_id
    );

    DECLARE @page_rows BIGINT;
    DECLARE @minimum_page INT;
    DECLARE @maximum_page INT;
    DECLARE @terminal_pages BIGINT;
    DECLARE @terminal_page INT;
    DECLARE @page_physical_rows BIGINT;
    DECLARE @non_terminal_empty_pages BIGINT;
    DECLARE @invalid_terminal_pages BIGINT;
    DECLARE @oversized_root_pages BIGINT;
    ;WITH ranked_page AS (
        SELECT
            page_number, page_attempt, requested_page_size, physical_rows, distinct_root_keys,
            response_bytes, terminal_empty_page, terminal_evidence_kind,
            ROW_NUMBER() OVER (PARTITION BY page_number ORDER BY page_attempt DESC) AS authority_rank
        FROM ctl.execution_page_audit WITH (UPDLOCK, HOLDLOCK)
        WHERE execution_id = @execution_id
    ), authoritative_page AS (
        SELECT page_number, requested_page_size, physical_rows, distinct_root_keys,
               terminal_empty_page, terminal_evidence_kind
        FROM ranked_page WHERE authority_rank = 1
    )
    SELECT
        @page_rows = COUNT_BIG(*),
        @minimum_page = MIN(page_number),
        @maximum_page = MAX(page_number),
        @terminal_pages = COALESCE(SUM(
            CASE WHEN terminal_evidence_kind <> N'NONE' THEN 1 ELSE 0 END
        ), 0),
        @terminal_page = MAX(
            CASE WHEN terminal_evidence_kind <> N'NONE' THEN page_number END
        ),
        @page_physical_rows = COALESCE(SUM(physical_rows), 0),
        @non_terminal_empty_pages = COALESCE(SUM(
            CASE WHEN terminal_evidence_kind = N'NONE' AND physical_rows = 0
                 THEN 1 ELSE 0 END
        ), 0),
        @invalid_terminal_pages = COALESCE(SUM(
            CASE
                WHEN terminal_evidence_kind = N'DATA_EXPORT_EMPTY_PAGE'
                     AND (terminal_empty_page <> 1
                          OR physical_rows <> 0 OR distinct_root_keys <> 0)
                    THEN 1
                WHEN terminal_evidence_kind = N'GRAPHQL_PAGE_INFO'
                     AND (terminal_empty_page <> 0 OR physical_rows = 0)
                    THEN 1
                WHEN terminal_evidence_kind NOT IN (
                    N'NONE', N'DATA_EXPORT_EMPTY_PAGE', N'GRAPHQL_PAGE_INFO'
                ) THEN 1
                ELSE 0
            END
        ), 0),
        @oversized_root_pages = COALESCE(SUM(
            CASE WHEN distinct_root_keys > requested_page_size THEN 1 ELSE 0 END
        ), 0)
    FROM authoritative_page;

    DECLARE @divergent_page_attempts BIGINT = (
        SELECT COUNT_BIG(*)
        FROM (
            SELECT page_number
            FROM ctl.execution_page_audit WITH (UPDLOCK, HOLDLOCK)
            WHERE execution_id = @execution_id
            GROUP BY page_number
            HAVING MIN(requested_page_size) <> MAX(requested_page_size)
                OR MIN(physical_rows) <> MAX(physical_rows)
                OR MIN(distinct_root_keys) <> MAX(distinct_root_keys)
                OR MIN(response_bytes) <> MAX(response_bytes)
                OR MIN(CONVERT(TINYINT, terminal_empty_page))
                    <> MAX(CONVERT(TINYINT, terminal_empty_page))
                OR MIN(terminal_evidence_kind) <> MAX(terminal_evidence_kind)
        ) AS divergent
    );

    DECLARE @overdue_quarantine_rows BIGINT = (
        SELECT COUNT_BIG(*)
        FROM recon.quarantine_record WITH (UPDLOCK, HOLDLOCK)
        WHERE execution_id = @execution_id
          AND quarantined_at_utc < DATEADD(
              SECOND,
              -CONVERT(INT, CASE WHEN @quarantine_sla_seconds > 2147483647
                  THEN 2147483647 ELSE @quarantine_sla_seconds END),
              @database_now_utc
          )
    );

    DECLARE @measurements TABLE (
        check_ordinal SMALLINT NOT NULL PRIMARY KEY,
        check_code NVARCHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL,
        evaluated_rows BIGINT NOT NULL,
        failed_rows BIGINT NOT NULL,
        reason_code NVARCHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL
    );

    INSERT INTO @measurements (
        check_ordinal, check_code, evaluated_rows, failed_rows, reason_code
    ) VALUES
    (
        1, N'COUNT_EQUATION', 1,
        CASE WHEN @count_rows = 1
              AND @count_physical = @count_distinct + @count_duplicate + @count_unidentified
              AND @count_distinct = @count_valid + @count_quarantined
             THEN 0 ELSE 1 END,
        N'COUNT_EQUATION_MISMATCH'
    ),
    (
        2, N'PAGE_TERMINALITY', 1,
        CASE WHEN @page_rows > 0
              AND @minimum_page = 1
              AND @page_rows = @maximum_page
              AND @terminal_pages = 1
              AND @terminal_page = @maximum_page
              AND @non_terminal_empty_pages = 0
              AND @invalid_terminal_pages = 0
              AND @oversized_root_pages = 0
              AND @divergent_page_attempts = 0
              AND @page_physical_rows = @stage_rows
             THEN 0 ELSE 1 END,
        N'PAGE_TERMINALITY_MISMATCH'
    ),
    (
        3, N'PROMOTION_RECONCILIATION', 1,
        CASE WHEN @count_rows = 1
              AND @promotion_physical_rows = @count_physical
              AND @promotion_distinct_rows = @count_distinct
              AND @promotion_duplicate_rows = @count_duplicate
              AND @candidate_rows = @count_valid
              AND @promotion_quarantined_roots = @count_quarantined
              AND @promotion_unidentified = @count_unidentified
              AND @promotion_physical_rows = @stage_rows
              AND @promotion_distinct_rows
                    = @candidate_rows + @promotion_quarantined_roots
              AND @promotion_physical_rows = @promotion_distinct_rows
                    + @promotion_duplicate_rows + @promotion_unidentified
              AND @candidate_rows = @actual_candidate_rows
              AND @actual_quarantine_rows = @promotion_quarantined_stage
             THEN 0 ELSE 1 END,
        N'PROMOTION_RECONCILIATION_MISMATCH'
    ),
    (
        4, N'QUARANTINE_SLA', @actual_quarantine_rows, @overdue_quarantine_rows,
        N'QUARANTINE_SLA_EXCEEDED'
    );

    DECLARE @results TABLE (
        check_ordinal SMALLINT NOT NULL PRIMARY KEY,
        check_code NVARCHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL,
        evaluated_rows BIGINT NOT NULL,
        failed_rows BIGINT NOT NULL,
        failure_basis_points INT NOT NULL,
        check_state NVARCHAR(16) COLLATE Latin1_General_100_BIN2 NOT NULL,
        reason_code NVARCHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL
    );

    INSERT INTO @results (
        check_ordinal, check_code, evaluated_rows, failed_rows,
        failure_basis_points, check_state, reason_code
    )
    SELECT
        measurement.check_ordinal,
        measurement.check_code,
        measurement.evaluated_rows,
        measurement.failed_rows,
        CASE WHEN measurement.evaluated_rows = 0 THEN 0 ELSE CONVERT(INT, CEILING(
            CONVERT(DECIMAL(38, 10), measurement.failed_rows) * 10000
                / CONVERT(DECIMAL(38, 10), measurement.evaluated_rows)
        )) END,
        CASE WHEN measurement.failed_rows <= policy.maximum_failed_rows
              AND (
                  (measurement.evaluated_rows = 0 AND measurement.failed_rows = 0)
                  OR CONVERT(DECIMAL(38, 0), measurement.failed_rows) * 10000
                        <= CONVERT(DECIMAL(38, 0), measurement.evaluated_rows)
                            * policy.maximum_failure_basis_points
              )
             THEN N'PASSED' ELSE N'FAILED' END,
        CASE WHEN measurement.failed_rows = 0
                  OR (measurement.failed_rows <= policy.maximum_failed_rows
                      AND (
                          (measurement.evaluated_rows = 0 AND measurement.failed_rows = 0)
                          OR CONVERT(DECIMAL(38, 0), measurement.failed_rows) * 10000
                                <= CONVERT(DECIMAL(38, 0), measurement.evaluated_rows)
                                    * policy.maximum_failure_basis_points
                      ))
             THEN N'CHECK_WITHIN_THRESHOLD'
             ELSE measurement.reason_code END
    FROM @measurements AS measurement
    INNER JOIN ctl.data_quality_check_policy AS policy WITH (UPDLOCK, HOLDLOCK)
        ON policy.policy_version = @policy_version COLLATE Latin1_General_100_BIN2
       AND policy.policy_fingerprint = @policy_fingerprint COLLATE Latin1_General_100_BIN2
       AND policy.check_ordinal = measurement.check_ordinal
       AND policy.check_code = measurement.check_code;

    IF (SELECT COUNT_BIG(*) FROM @results) <> @expected_checks
        THROW 51608, N'A execução parcial dos checks foi recusada.', 1;

    DECLARE @completed_checks SMALLINT = CONVERT(SMALLINT, (SELECT COUNT_BIG(*) FROM @results));
    DECLARE @passed_checks SMALLINT = CONVERT(SMALLINT, (
        SELECT COUNT_BIG(*) FROM @results WHERE check_state = N'PASSED'
    ));
    DECLARE @failed_checks SMALLINT = CONVERT(SMALLINT, (
        SELECT COUNT_BIG(*) FROM @results WHERE check_state = N'FAILED'
    ));
    DECLARE @evaluated_rows BIGINT = (SELECT SUM(evaluated_rows) FROM @results);
    DECLARE @failed_rows BIGINT = (SELECT SUM(failed_rows) FROM @results);
    DECLARE @evaluation_state NVARCHAR(16) =
        CASE WHEN @failed_checks = 0 THEN N'PASSED' ELSE N'FAILED' END;

    DECLARE @evaluation_material NVARCHAR(2000) = CONCAT(
        N'dq-evaluation-v1|', @policy_version, N'|', @policy_fingerprint, N'|',
        @scope_fingerprint, N'|',
        CONVERT(NVARCHAR(36), @execution_id), N'|', @candidate_rows, N'|',
        CONVERT(NVARCHAR(33), @promotion_recorded_at_utc, 126), N'|',
        @completed_checks, N'|', @passed_checks, N'|', @failed_checks, N'|',
        @evaluated_rows, N'|', @failed_rows, N'|', @evaluation_state, N'|',
        (SELECT CONCAT(check_code, N':', evaluated_rows, N':', failed_rows, N':', check_state)
         FROM @results WHERE check_ordinal = 1), N'|',
        (SELECT CONCAT(check_code, N':', evaluated_rows, N':', failed_rows, N':', check_state)
         FROM @results WHERE check_ordinal = 2), N'|',
        (SELECT CONCAT(check_code, N':', evaluated_rows, N':', failed_rows, N':', check_state)
         FROM @results WHERE check_ordinal = 3), N'|',
        (SELECT CONCAT(check_code, N':', evaluated_rows, N':', failed_rows, N':', check_state)
         FROM @results WHERE check_ordinal = 4)
    );
    DECLARE @evaluation_fingerprint CHAR(64) = LOWER(CONVERT(
        CHAR(64), HASHBYTES('SHA2_256', CONVERT(VARBINARY(MAX), @evaluation_material)), 2
    ));

    INSERT INTO recon.execution_data_quality_evaluation (
        execution_id, policy_version, policy_fingerprint, scope_fingerprint,
        evaluation_fingerprint,
        expected_checks, completed_checks, passed_checks, failed_checks,
        evaluated_rows, failed_rows, evaluation_state, candidate_rows,
        promotion_recorded_at_utc, evaluated_at_utc
    ) VALUES (
        @execution_id, @policy_version, @policy_fingerprint, @scope_fingerprint,
        @evaluation_fingerprint,
        @expected_checks, @completed_checks, @passed_checks, @failed_checks,
        @evaluated_rows, @failed_rows, @evaluation_state, @candidate_rows,
        @promotion_recorded_at_utc, @database_now_utc
    );

    INSERT INTO recon.execution_data_quality_check_result (
        execution_id, check_ordinal, check_code, evaluated_rows, failed_rows,
        failure_basis_points, check_state, reason_code, evaluated_at_utc
    )
    SELECT
        @execution_id, check_ordinal, check_code, evaluated_rows, failed_rows,
        failure_basis_points, check_state, reason_code, @database_now_utc
    FROM @results;

    IF @@ROWCOUNT <> @expected_checks
        THROW 51609, N'A persistência parcial dos checks foi recusada.', 1;

    COMMIT TRANSACTION;

    SELECT
        execution_id, policy_version, policy_fingerprint, evaluation_fingerprint,
        expected_checks, completed_checks, passed_checks, failed_checks,
        evaluated_rows, failed_rows, evaluation_state, evaluated_at_utc
    FROM recon.execution_data_quality_evaluation
    WHERE execution_id = @execution_id;
END;
GO

CREATE OR ALTER PROCEDURE stg.usp_stage_coleta_record
    @execution_id UNIQUEIDENTIFIER,
    @input_batch_number INT,
    @input_record_ordinal INT,
    @source_key NVARCHAR(MAX),
    @source_key_wire_type NVARCHAR(MAX),
    @sequence_code_presence NVARCHAR(MAX),
    @sequence_code_json NVARCHAR(MAX),
    @payload_json NVARCHAR(MAX),
    @field_presence_json NVARCHAR(MAX),
    @relation_candidates_json NVARCHAR(MAX),
    @status_raw NVARCHAR(MAX),
    @status_code NVARCHAR(MAX),
    @status_label NVARCHAR(MAX),
    @status_catalog_version NVARCHAR(MAX),
    @terminal BIT,
    @occurrence_action NVARCHAR(MAX),
    @attempt_count SMALLINT,
    @freshness_raw NVARCHAR(MAX),
    @freshness_at_utc DATETIME2(3),
    @freshness_origin NVARCHAR(MAX),
    @validation_disposition NVARCHAR(MAX),
    @quarantine_reason_code NVARCHAR(MAX),
    @observed_at_utc DATETIME2(3)
AS
BEGIN
    -- B54: validate current SQL authority consumption before any effect/read.
    DECLARE @b54_actual NVARCHAR(MAX)=(SELECT LOWER(COALESCE(CONVERT(NVARCHAR(36),@execution_id),N'')) AS [execution] FOR JSON PATH,WITHOUT_ARRAY_WRAPPER,INCLUDE_NULL_VALUES);
    IF ctl.fn_runtime_consumed_scope(@b54_actual,1,NULL)<>1 THROW 52500,N'RUNTIME_DURABLE_CONSUMER_FENCE_REQUIRED',1;

    SET NOCOUNT ON;
    SET XACT_ABORT ON;

    IF @input_record_ordinal IS NULL OR @input_record_ordinal NOT BETWEEN 1 AND 100
        THROW 51800, N'Ordinal vertical de Coletas deve estar entre 1 e 100.', 1;
    IF DATALENGTH(@source_key) > 512 OR DATALENGTH(@source_key_wire_type) > 32
        OR DATALENGTH(@sequence_code_presence) > 16 OR DATALENGTH(@sequence_code_json) > 512
        OR DATALENGTH(@payload_json) > 20971520 OR DATALENGTH(@field_presence_json) > 20971520
        OR DATALENGTH(@relation_candidates_json) > 20971520 OR DATALENGTH(@status_raw) > 510
        OR DATALENGTH(@status_code) > 64 OR DATALENGTH(@status_label) > 128
        OR DATALENGTH(@status_catalog_version) > 128 OR DATALENGTH(@occurrence_action) > 4096
        OR DATALENGTH(@freshness_raw) > 510 OR DATALENGTH(@freshness_origin) > 64
        OR DATALENGTH(@validation_disposition) > 32 OR DATALENGTH(@quarantine_reason_code) > 128
        THROW 51800, N'Registro tipado de Coletas excede o limite textual.', 1;

    SET @source_key_wire_type = NULLIF(LTRIM(RTRIM(@source_key_wire_type)), N'');
    SET @sequence_code_presence = NULLIF(LTRIM(RTRIM(@sequence_code_presence)), N'');
    SET @status_code = NULLIF(LTRIM(RTRIM(@status_code)), N'');
    SET @status_catalog_version = NULLIF(LTRIM(RTRIM(@status_catalog_version)), N'');
    SET @freshness_origin = NULLIF(LTRIM(RTRIM(@freshness_origin)), N'');
    SET @validation_disposition = NULLIF(LTRIM(RTRIM(@validation_disposition)), N'');
    SET @quarantine_reason_code = NULLIF(LTRIM(RTRIM(@quarantine_reason_code)), N'');

    IF @source_key_wire_type COLLATE Latin1_General_100_BIN2 <> N'INTEGER'
       OR LEFT(@source_key, 8) COLLATE Latin1_General_100_BIN2 <> N'INTEGER:'
       OR SUBSTRING(@source_key, 9, 256) COLLATE Latin1_General_100_BIN2 LIKE N'%[^0-9-]%'
       OR @source_key = N'INTEGER:' OR @source_key = N'INTEGER:-'
        THROW 51800, N'Source key tipada de Coletas é inválida.', 1;

    IF @validation_disposition COLLATE Latin1_General_100_BIN2 NOT IN (N'VALID', N'QUARANTINE')
       OR @observed_at_utc IS NULL
       OR (@validation_disposition = N'QUARANTINE' AND @quarantine_reason_code IS NULL)
       OR (@validation_disposition = N'VALID' AND (
            @source_key IS NULL OR @sequence_code_presence NOT IN (N'ABSENT', N'NULL', N'VALUE')
            OR (@sequence_code_presence = N'VALUE' AND ISJSON(@sequence_code_json) <> 1)
            OR (@sequence_code_presence IN (N'ABSENT', N'NULL') AND @sequence_code_json IS NOT NULL)
            OR ISJSON(@payload_json) <> 1 OR ISJSON(@field_presence_json) <> 1
            OR ISJSON(@relation_candidates_json) <> 1
            OR @status_catalog_version <> N'coletas-status-v1'
            OR @terminal IS NULL OR @attempt_count <> CASE WHEN @terminal = 1 THEN 1 ELSE 0 END
            OR @freshness_origin NOT IN (
                N'STATUS_UPDATED_AT', N'FINISH_DATE', N'SERVICE_DATE', N'REQUEST_DATE', N'UNAVAILABLE'
            )
            OR ((@freshness_origin = N'UNAVAILABLE' AND @freshness_at_utc IS NOT NULL)
                OR (@freshness_origin <> N'UNAVAILABLE' AND @freshness_at_utc IS NULL))
            OR @quarantine_reason_code IS NOT NULL
       ))
        THROW 51800, N'Registro tipado de Coletas é inválido.', 1;

    IF @validation_disposition = N'VALID' AND NOT (
        (@status_code IS NULL AND @status_label IS NULL AND @terminal = 0)
        OR (@status_code = N'pending' AND @status_label = N'Pendente' AND @terminal = 0)
        OR (@status_code = N'treatment' AND @status_label = N'Em tratativa' AND @terminal = 0)
        OR (@status_code = N'manifested' AND @status_label = N'Manifestada' AND @terminal = 0)
        OR (@status_code = N'in_transit' AND @status_label = N'Em trânsito' AND @terminal = 0)
        OR (@status_code = N'draft' AND @status_label = N'Rascunho' AND @terminal = 0)
        OR (@status_code = N'finished' AND @status_label = N'Finalizada' AND @terminal = 1)
        OR (@status_code = N'done' AND @status_label = N'Coletada' AND @terminal = 1)
        OR (@status_code IN (N'canceled', N'cancelled') AND @status_label = N'Cancelada' AND @terminal = 1)
    )
        THROW 51800, N'Catálogo de status de Coletas diverge.', 1;

    DECLARE @identity_row_hash CHAR(64) = NULL;
    DECLARE @identity_presence_hash CHAR(64) = NULL;
    DECLARE @attribute_hash CHAR(64) = NULL;
    DECLARE @presence_hash CHAR(64) = NULL;
    IF @validation_disposition = N'VALID'
    BEGIN
        SET @identity_row_hash = LOWER(CONVERT(CHAR(64), HASHBYTES(
            'SHA2_256', CONVERT(VARBINARY(MAX), CONCAT(N'coletas-envelope-v1|', @payload_json))
        ), 2));
        SET @identity_presence_hash = LOWER(CONVERT(CHAR(64), HASHBYTES(
            'SHA2_256', CONVERT(VARBINARY(MAX), CONCAT(N'coletas-presence-v1|', @field_presence_json))
        ), 2));
        SET @attribute_hash = LOWER(CONVERT(CHAR(64), HASHBYTES(
            'SHA2_256', CONVERT(VARBINARY(MAX), CONCAT(
                N'coletas-attributes-v1|', @sequence_code_presence, N'|',
                COALESCE(@sequence_code_json, N'<null>'), N'|', COALESCE(@status_code, N'<unknown>'),
                N'|', CONVERT(NVARCHAR(1), @terminal), N'|', COALESCE(@freshness_origin, N'UNAVAILABLE'),
                N'|', COALESCE(CONVERT(NVARCHAR(33), @freshness_at_utc, 126), N'<null>')
            ))
        ), 2));
        SET @presence_hash = @identity_presence_hash;
    END;

    BEGIN TRANSACTION;
    DECLARE @entity_name NVARCHAR(128);
    SELECT @entity_name = partition.entity_name
    FROM ctl.execution_attempt AS attempt WITH (UPDLOCK, HOLDLOCK)
    INNER JOIN ctl.execution_partition AS partition WITH (HOLDLOCK)
        ON partition.partition_id = attempt.partition_id
    WHERE attempt.execution_id = @execution_id;
    IF @entity_name IS NULL OR @entity_name COLLATE Latin1_General_100_BIN2 <> N'coletas'
        THROW 51801, N'A execução não pertence à vertical de Coletas.', 1;

    DECLARE @preexisting_stage_record_id BIGINT;
    SELECT @preexisting_stage_record_id = stage_record_id
    FROM stg.execution_record WITH (UPDLOCK, HOLDLOCK)
    WHERE execution_id = @execution_id AND input_batch_number = @input_batch_number
      AND input_record_ordinal = @input_record_ordinal;
    IF @validation_disposition = N'VALID' AND @preexisting_stage_record_id IS NOT NULL
       AND NOT EXISTS (SELECT 1 FROM stg.coleta_record WITH (UPDLOCK, HOLDLOCK)
                       WHERE stage_record_id = @preexisting_stage_record_id)
        THROW 51803, N'Registro genérico preexistente não aceita sidecar tardio.', 1;

    DECLARE @identity_row_fingerprint_version NVARCHAR(128) =
        CASE WHEN @validation_disposition = N'VALID' THEN N'coletas-envelope-v1' END;
    DECLARE @identity_presence_fingerprint_version NVARCHAR(128) =
        CASE WHEN @validation_disposition = N'VALID' THEN N'coletas-presence-v1' END;

    EXEC stg.usp_stage_record
        @execution_id = @execution_id, @input_batch_number = @input_batch_number,
        @input_record_ordinal = @input_record_ordinal, @source_key = @source_key,
        @row_fingerprint_version = @identity_row_fingerprint_version,
        @source_row_hash = @identity_row_hash,
        @presence_fingerprint_version = @identity_presence_fingerprint_version,
        @presence_fingerprint = @identity_presence_hash,
        @source_freshness_at_utc = @freshness_at_utc,
        @validation_disposition = @validation_disposition,
        @quarantine_reason_code = @quarantine_reason_code, @staged_at_utc = @observed_at_utc;

    IF @validation_disposition = N'QUARANTINE'
    BEGIN
        COMMIT TRANSACTION;
        RETURN;
    END;

    DECLARE @stage_record_id BIGINT;
    SELECT @stage_record_id = stage_record_id
    FROM stg.execution_record WITH (UPDLOCK, HOLDLOCK)
    WHERE execution_id = @execution_id AND input_batch_number = @input_batch_number
      AND input_record_ordinal = @input_record_ordinal;
    IF EXISTS (SELECT 1 FROM stg.coleta_record WITH (UPDLOCK, HOLDLOCK)
               WHERE stage_record_id = @stage_record_id AND (
                   attribute_hash <> @attribute_hash OR presence_hash <> @presence_hash
                   OR payload_json <> @payload_json OR field_presence_json <> @field_presence_json
                   OR relation_candidates_json <> @relation_candidates_json
               ))
        THROW 51802, N'Retry tipado de Coletas possui conteúdo divergente.', 1;
    IF NOT EXISTS (SELECT 1 FROM stg.coleta_record WITH (UPDLOCK, HOLDLOCK)
                   WHERE stage_record_id = @stage_record_id)
        INSERT INTO stg.coleta_record (
            stage_record_id, execution_id, source_key, source_key_wire_type,
            sequence_code_presence, sequence_code_json, payload_json, field_presence_json,
            relation_candidates_json, status_raw, status_code, status_label, status_catalog_version,
            terminal, occurrence_action, attempt_count, freshness_raw, freshness_at_utc,
            freshness_origin, attribute_fingerprint_version, attribute_hash,
            presence_fingerprint_version, presence_hash
        ) VALUES (
            @stage_record_id, @execution_id, @source_key, @source_key_wire_type,
            @sequence_code_presence, @sequence_code_json, @payload_json, @field_presence_json,
            @relation_candidates_json, @status_raw, @status_code, @status_label,
            @status_catalog_version, @terminal, @occurrence_action, @attempt_count, @freshness_raw,
            @freshness_at_utc, @freshness_origin, N'coletas-attributes-v1', @attribute_hash,
            N'coletas-presence-v1', @presence_hash
        );
    COMMIT TRANSACTION;
END;
GO

CREATE OR ALTER PROCEDURE stg.usp_stage_frete_record
    @execution_id UNIQUEIDENTIFIER,
    @input_batch_number INT,
    @input_record_ordinal INT,
    @source_key NVARCHAR(256),
    @payload_json NVARCHAR(MAX),
    @field_presence_json NVARCHAR(MAX),
    @business_alias_json NVARCHAR(MAX),
    @status_raw NVARCHAR(255),
    @status_code NVARCHAR(32),
    @status_label NVARCHAR(64),
    @terminal BIT,
    @freshness_evidence_json NVARCHAR(MAX),
    @freshness_at_utc DATETIME2(3),
    @freshness_origin NVARCHAR(32),
    @service_at_utc DATETIME2(3),
    @performance_evidence_json NVARCHAR(MAX),
    @performance_at_utc DATETIME2(3),
    @performance_origin NVARCHAR(32),
    @cte_finalizations_json NVARCHAR(MAX),
    @financial_json NVARCHAR(MAX),
    @relation_candidates_json NVARCHAR(MAX),
    @sidecar_json NVARCHAR(MAX),
    @validation_disposition NVARCHAR(16),
    @quarantine_reason_code NVARCHAR(64),
    @observed_at_utc DATETIME2(3)
AS
BEGIN
    -- B54: validate current SQL authority consumption before any effect/read.
    DECLARE @b54_actual NVARCHAR(MAX)=(SELECT LOWER(COALESCE(CONVERT(NVARCHAR(36),@execution_id),N'')) AS [execution] FOR JSON PATH,WITHOUT_ARRAY_WRAPPER,INCLUDE_NULL_VALUES);
    IF ctl.fn_runtime_consumed_scope(@b54_actual,1,NULL)<>1 THROW 52500,N'RUNTIME_DURABLE_CONSUMER_FENCE_REQUIRED',1;

    SET NOCOUNT ON;
    IF @execution_id IS NULL OR @input_batch_number IS NULL OR @input_batch_number<1
       OR @input_record_ordinal IS NULL OR @input_record_ordinal NOT BETWEEN 1 AND 100
       OR @observed_at_utc IS NULL
       OR @validation_disposition NOT IN(N'VALID',N'QUARANTINE')
       OR (@validation_disposition=N'QUARANTINE'
           AND (@quarantine_reason_code IS NULL
             OR @quarantine_reason_code LIKE N'%[^A-Z0-9_]%' OR LEN(@quarantine_reason_code)<2))
       OR (@validation_disposition=N'VALID' AND @quarantine_reason_code IS NOT NULL)
        THROW 52120,N'Envelope físico de Fretes inválido.',1;

    IF @validation_disposition=N'VALID'
    BEGIN
        IF @source_key IS NULL OR @source_key COLLATE Latin1_General_100_BIN2 NOT LIKE N'INTEGER:[1-9]%'
           OR SUBSTRING(@source_key,9,256) LIKE N'%[^0-9]%'
           OR TRY_CONVERT(BIGINT,SUBSTRING(@source_key,9,256))<=CONVERT(BIGINT,0)
           OR @source_key COLLATE Latin1_General_100_BIN2<>
                CONCAT(N'INTEGER:',CONVERT(NVARCHAR(20),TRY_CONVERT(BIGINT,SUBSTRING(@source_key,9,256))))
           OR ISJSON(@payload_json)<>1 OR ISJSON(@field_presence_json)<>1
           OR ISJSON(@business_alias_json)<>1 OR ISJSON(@freshness_evidence_json)<>1
           OR ISJSON(@performance_evidence_json)<>1 OR ISJSON(@cte_finalizations_json)<>1
           OR ISJSON(@financial_json)<>1 OR ISJSON(@relation_candidates_json)<>1
           OR ISJSON(@sidecar_json)<>1
            THROW 52121,N'Identidade, tipo ou JSON de Fretes inválido.',1;
        IF (SELECT COUNT_BIG(*) FROM OPENJSON(@field_presence_json))<>18
           OR JSON_VALUE(@field_presence_json,N'$.id')<>N'VALUE'
           OR JSON_VALUE(@field_presence_json,N'$.updated_at') IS NULL
           OR JSON_VALUE(@field_presence_json,N'$.reference_number') IS NULL
           OR JSON_VALUE(@field_presence_json,N'$.fit_p_m_pck_sequence_code') IS NULL
           OR JSON_VALUE(@field_presence_json,N'$.corporation_sequence_number') IS NULL
           OR JSON_VALUE(@field_presence_json,N'$.finished_at') IS NULL
           OR JSON_VALUE(@field_presence_json,N'$.fit_dpn_performance_finished_at') IS NULL
           OR JSON_VALUE(@field_presence_json,N'$.status') IS NULL
           OR JSON_VALUE(@field_presence_json,N'$.cte_created_at') IS NULL
           OR JSON_VALUE(@field_presence_json,N'$.cte_issued_at') IS NULL
           OR JSON_VALUE(@field_presence_json,N'$.criado_em') IS NULL
           OR JSON_VALUE(@field_presence_json,N'$.servico_em') IS NULL
           OR JSON_VALUE(@field_presence_json,N'$.cte') IS NULL
           OR JSON_VALUE(@field_presence_json,N'$.ctes') IS NULL
           OR JSON_VALUE(@field_presence_json,N'$.cte_key') IS NULL
           OR JSON_VALUE(@field_presence_json,N'$.finalizations') IS NULL
           OR JSON_VALUE(@field_presence_json,N'$.finalizacoes') IS NULL
           OR JSON_VALUE(@field_presence_json,N'$.total') IS NULL
           OR EXISTS(
               SELECT 1 FROM OPENJSON(@field_presence_json)
               WHERE [value] COLLATE Latin1_General_100_BIN2 NOT IN(N'ABSENT',N'NULL',N'VALUE')
           ) THROW 52122,N'Presença tri-state de Fretes inválida.',1;
        DECLARE @expected_freshness_origin NVARCHAR(32)=
            CASE
              WHEN JSON_VALUE(@freshness_evidence_json,N'$.cte_created_at.presence')=N'VALUE'
                THEN N'CTE_CREATED_AT'
              WHEN JSON_VALUE(@freshness_evidence_json,N'$.cte_issued_at.presence')=N'VALUE'
                THEN N'CTE_ISSUED_AT'
              WHEN JSON_VALUE(@freshness_evidence_json,N'$.criado_em.presence')=N'VALUE'
                THEN N'CRIADO_EM'
              WHEN JSON_VALUE(@freshness_evidence_json,N'$.servico_em.presence')=N'VALUE'
                THEN N'SERVICO_EM'
            END;
        IF @freshness_at_utc IS NULL OR @expected_freshness_origin IS NULL
           OR @freshness_origin<>@expected_freshness_origin
           OR JSON_VALUE(@freshness_evidence_json,
                CONCAT(N'$.',LOWER(@expected_freshness_origin),N'.parseState'))<>N'VALID'
           OR JSON_VALUE(@freshness_evidence_json,N'$.updated_at')<>N'IGNORED_UNVERIFIED'
           OR JSON_VALUE(@freshness_evidence_json,N'$.evidenceScope')<>
                N'SYNTHETIC_V09_NOT_SOURCE_CONTRACT_EVIDENCE'
            THROW 52123,N'Precedência temporal de Fretes inválida.',1;
        IF (@status_code IS NULL AND (@status_label IS NOT NULL OR @terminal<>0))
           OR (@status_code IN(N'finished',N'done') AND (@status_label<>N'finalizado' OR @terminal<>1))
           OR (@status_code IN(N'canceled',N'cancelled') AND (@status_label<>N'cancelada' OR @terminal<>1))
           OR (@status_code IS NOT NULL AND @status_code NOT IN(N'finished',N'done',N'canceled',N'cancelled'))
            THROW 52124,N'Terminalidade de Fretes diverge do catálogo exato.',1;
        IF JSON_VALUE(@cte_finalizations_json,N'$.evidenceScope')<>
                N'SYNTHETIC_V09_NOT_SOURCE_CONTRACT_EVIDENCE'
           OR JSON_VALUE(@cte_finalizations_json,N'$.status.provenance')<>N'PRESERVED'
           OR JSON_VALUE(@financial_json,N'$.evidenceScope')<>
                N'SYNTHETIC_V09_NOT_SOURCE_CONTRACT_EVIDENCE'
           OR JSON_VALUE(@financial_json,N'$.currency')<>N'UNRESOLVED_NO_INFERENCE'
           OR JSON_VALUE(@financial_json,N'$.unit')<>N'UNRESOLVED_NO_INFERENCE'
           OR JSON_VALUE(@financial_json,N'$.arithmetic')<>N'FORBIDDEN'
           OR JSON_VALUE(@relation_candidates_json,N'$.policy')<>
                N'UNRESOLVED_RELATION_CANDIDATES_V2_046B'
           OR JSON_VALUE(@relation_candidates_json,N'$.evidenceScope')<>
                N'CONTRACTED_DATAEXPORT_6389_PATHS_ONLY'
            THROW 52125,N'Envelope protegido de Fretes perdeu proveniência ou inferiu regra.',1;
    END;

    SET XACT_ABORT ON;
    BEGIN TRANSACTION;
    DECLARE @entity_name NVARCHAR(128),@source_instance NVARCHAR(128),@tenant_scope NVARCHAR(128);
    SELECT @entity_name=partition.entity_name,@source_instance=partition.source_instance,
           @tenant_scope=partition.tenant_scope
    FROM ctl.execution_attempt attempt WITH(UPDLOCK,HOLDLOCK)
    JOIN ctl.execution_partition partition WITH(HOLDLOCK)
      ON partition.partition_id=attempt.partition_id
    WHERE attempt.execution_id=@execution_id;
    IF @entity_name COLLATE Latin1_General_100_BIN2<>N'fretes'
        THROW 52126,N'A execução não pertence a Fretes.',1;
    IF UPPER(@source_instance) IN(N'DEFAULT',N'GLOBAL',N'SINGLETON')
       OR UPPER(@tenant_scope) IN(N'DEFAULT',N'GLOBAL',N'SINGLETON')
        THROW 52127,N'Fretes exige source_instance/tenant_scope explícitos.',1;

    DECLARE @attribute_hash CHAR(64)=CASE WHEN @validation_disposition=N'VALID' THEN
        LOWER(CONVERT(CHAR(64),HASHBYTES('SHA2_256',CONVERT(VARBINARY(MAX),
            CONCAT(N'fretes-envelope-v1|',@payload_json,N'|',@field_presence_json,N'|',
              @business_alias_json,N'|',@freshness_evidence_json,N'|',
              @performance_evidence_json,N'|',@cte_finalizations_json,N'|',
              @financial_json,N'|',@relation_candidates_json))),2)) END;
    DECLARE @presence_hash CHAR(64)=CASE WHEN @validation_disposition=N'VALID' THEN
        LOWER(CONVERT(CHAR(64),HASHBYTES('SHA2_256',CONVERT(VARBINARY(MAX),
            CONCAT(N'fretes-presence-v1|',@field_presence_json))),2)) END;
    DECLARE @row_fingerprint_version NVARCHAR(128)=CASE
        WHEN @validation_disposition=N'VALID' THEN N'fretes-envelope-v1' END;
    DECLARE @presence_fingerprint_version NVARCHAR(128)=CASE
        WHEN @validation_disposition=N'VALID' THEN N'fretes-presence-v1' END;
    EXEC stg.usp_stage_record
        @execution_id,@input_batch_number,@input_record_ordinal,@source_key,
        @row_fingerprint_version,@attribute_hash,@presence_fingerprint_version,@presence_hash,
        @freshness_at_utc,@validation_disposition,@quarantine_reason_code,@observed_at_utc;
    IF @validation_disposition=N'QUARANTINE'
    BEGIN
        COMMIT TRANSACTION;
        RETURN;
    END;

    DECLARE @stage_record_id BIGINT;
    SELECT @stage_record_id=stage_record_id
    FROM stg.execution_record WITH(UPDLOCK,HOLDLOCK)
    WHERE execution_id=@execution_id AND input_batch_number=@input_batch_number
      AND input_record_ordinal=@input_record_ordinal;
    IF EXISTS(
        SELECT 1 FROM stg.frete_record WITH(UPDLOCK,HOLDLOCK)
        WHERE stage_record_id=@stage_record_id AND attribute_hash<>@attribute_hash
    ) THROW 52128,N'Retry de Fretes possui conteúdo divergente.',1;
    IF NOT EXISTS(SELECT 1 FROM stg.frete_record WHERE stage_record_id=@stage_record_id)
        INSERT stg.frete_record(
            stage_record_id,execution_id,source_key,source_key_wire_type,payload_json,
            field_presence_json,business_alias_json,status_raw,status_code,status_label,
            status_catalog_version,terminal,freshness_evidence_json,freshness_at_utc,
            freshness_origin,service_at_utc,partition_basis,overlap_policy_version,
            cte_finalizations_json,financial_json,relation_candidates_json,attribute_hash,
            observed_at_utc
        ) VALUES(
            @stage_record_id,@execution_id,@source_key,N'INTEGER',@payload_json,
            @field_presence_json,@business_alias_json,@status_raw,@status_code,@status_label,
            N'fretes-status-v1',@terminal,@freshness_evidence_json,@freshness_at_utc,
            @freshness_origin,@service_at_utc,N'freights.service_at',
            N'fretes-service-at-overlap-v1',@cte_finalizations_json,@financial_json,
            @relation_candidates_json,@attribute_hash,@observed_at_utc
        );

    EXEC stg.usp_stage_frete_performance @execution_id,@stage_record_id,
        @performance_evidence_json,@performance_at_utc,@performance_origin,@observed_at_utc;
    EXEC stg.usp_stage_frete_sidecar @execution_id,@stage_record_id,@sidecar_json,@observed_at_utc;

    DECLARE @root_candidate_presence NVARCHAR(8)=CASE
        WHEN JSON_VALUE(@relation_candidates_json,
                N'$.fit_p_m_pck_sequence_code.presence')=N'VALUE' THEN N'VALUE'
        WHEN JSON_VALUE(@relation_candidates_json,
                N'$.fit_p_m_pck_sequence_code.presence')=N'NULL' THEN N'NULL'
        ELSE N'ABSENT' END;
    IF NOT EXISTS(
        SELECT 1 FROM recon.frete_coleta_relation_candidate
        WHERE execution_id=@execution_id AND stage_record_id=@stage_record_id
          AND source_kind=N'DATA_EXPORT_6389'
    )
        INSERT recon.frete_coleta_relation_candidate(
            execution_id,stage_record_id,source_kind,frete_source_key,candidate_presence,
            candidate_evidence_json,relation_state,observed_at_utc
        ) VALUES(
            @execution_id,@stage_record_id,N'DATA_EXPORT_6389',@source_key,
            @root_candidate_presence,@relation_candidates_json,
            N'APPEND_ONLY_UNRESOLVED_V2_046B',@observed_at_utc
        );
    DECLARE @sidecar_candidate_presence NVARCHAR(8)=CASE
        WHEN EXISTS(SELECT 1 FROM OPENJSON(@sidecar_json,N'$.edges')
                    WHERE JSON_VALUE([value],N'$.pickItemId.presence')=N'VALUE') THEN N'VALUE'
        WHEN EXISTS(SELECT 1 FROM OPENJSON(@sidecar_json,N'$.edges')
                    WHERE JSON_VALUE([value],N'$.pickItemId.presence')=N'NULL') THEN N'NULL'
        ELSE N'ABSENT' END;
    IF NOT EXISTS(
        SELECT 1 FROM recon.frete_coleta_relation_candidate
        WHERE execution_id=@execution_id AND stage_record_id=@stage_record_id
          AND source_kind=N'GRAPHQL_TRANSITIONAL'
    )
        INSERT recon.frete_coleta_relation_candidate(
            execution_id,stage_record_id,source_kind,frete_source_key,candidate_presence,
            candidate_evidence_json,relation_state,observed_at_utc
        ) VALUES(
            @execution_id,@stage_record_id,N'GRAPHQL_TRANSITIONAL',@source_key,
            @sidecar_candidate_presence,@sidecar_json,
            N'APPEND_ONLY_UNRESOLVED_V2_046B',@observed_at_utc
        );
    COMMIT TRANSACTION;
END;
GO


CREATE OR ALTER PROCEDURE ctl.usp_runtime_authorization
    @operation NVARCHAR(MAX), @invocation_id UNIQUEIDENTIFIER, @authority_id UNIQUEIDENTIFIER,
    @action NVARCHAR(MAX), @execution_id UNIQUEIDENTIFIER, @scope_material NVARCHAR(MAX),
    @policy_fingerprint NVARCHAR(MAX), @receipt_id UNIQUEIDENTIFIER=NULL
AS
BEGIN
    SET NOCOUNT ON; SET XACT_ABORT ON;
    IF @operation IS NULL OR @operation COLLATE Latin1_General_100_BIN2 NOT IN(N'AUTHORIZE',N'CONSUME')
        OR DATALENGTH(@operation)<>2*LEN(@operation) OR @invocation_id IS NULL OR @execution_id IS NULL
        OR @authority_id IS NULL OR @action IS NULL OR @action COLLATE Latin1_General_100_BIN2 NOT IN(N'RUN',N'REPLAY',N'FORCE_RUN',N'STATUS')
        OR DATALENGTH(@action)<>2*LEN(@action)
        OR @scope_material IS NULL OR DATALENGTH(@scope_material)>8000 OR ISJSON(@scope_material)<>1
        OR @policy_fingerprint IS NULL OR DATALENGTH(@policy_fingerprint)<>128 OR @policy_fingerprint COLLATE Latin1_General_100_BIN2 LIKE '%[^a-f0-9]%'
        THROW 52411,N'AUTHORIZATION_REQUEST_INVALID',1;
    DECLARE @keys TABLE(name NVARCHAR(32) COLLATE Latin1_General_100_BIN2 PRIMARY KEY,maximum INT NOT NULL);
    INSERT @keys VALUES(N'version',32),(N'invocation',36),(N'action',32),(N'execution',36),(N'environment',32),
        (N'source',128),(N'tenant',128),(N'workload',128),(N'entity',128),(N'mode',16),(N'start',30),(N'endExclusive',30),
        (N'strategy',32),(N'idempotency',200),(N'replayOf',36),(N'cycle',36),(N'planVersion',128),(N'planHash',64),
        (N'contractVersion',128),(N'contractHash',64),(N'configurationVersion',128),(N'configurationHash',64);
    IF (SELECT COUNT_BIG(*) FROM OPENJSON(@scope_material))<>22
        OR EXISTS(SELECT 1 FROM OPENJSON(@scope_material) j LEFT JOIN @keys k ON k.name=j.[key] COLLATE Latin1_General_100_BIN2
            WHERE k.name IS NULL OR DATALENGTH(k.name)<>DATALENGTH(j.[key]) OR j.type<>1 OR DATALENGTH(j.value)>2*k.maximum
                OR (j.[key]<>N'replayOf' AND DATALENGTH(j.value)=0))
        OR EXISTS(SELECT 1 FROM OPENJSON(@scope_material) GROUP BY [key] COLLATE Latin1_General_100_BIN2 HAVING COUNT_BIG(*)<>1)
        OR JSON_VALUE(@scope_material,'$.version') COLLATE Latin1_General_100_BIN2<>N'runtime-scope-v1'
        OR TRY_CONVERT(UNIQUEIDENTIFIER,JSON_VALUE(@scope_material,'$.invocation')) IS NULL
        OR TRY_CONVERT(UNIQUEIDENTIFIER,JSON_VALUE(@scope_material,'$.execution')) IS NULL
        OR ISNULL(TRY_CONVERT(UNIQUEIDENTIFIER,JSON_VALUE(@scope_material,'$.invocation')),'00000000-0000-0000-0000-000000000000')<>@invocation_id
        OR ISNULL(TRY_CONVERT(UNIQUEIDENTIFIER,JSON_VALUE(@scope_material,'$.execution')),'00000000-0000-0000-0000-000000000000')<>@execution_id
        OR JSON_VALUE(@scope_material,'$.action') COLLATE Latin1_General_100_BIN2<>@action
        OR TRY_CONVERT(UNIQUEIDENTIFIER,JSON_VALUE(@scope_material,'$.cycle')) IS NULL
        OR TRY_CONVERT(DATETIME2(3),JSON_VALUE(@scope_material,'$.start')) IS NULL
        OR TRY_CONVERT(DATETIME2(3),JSON_VALUE(@scope_material,'$.endExclusive')) IS NULL
        OR TRY_CONVERT(DATETIME2(3),JSON_VALUE(@scope_material,'$.start'))>=TRY_CONVERT(DATETIME2(3),JSON_VALUE(@scope_material,'$.endExclusive'))
        OR EXISTS(SELECT 1 FROM OPENJSON(@scope_material) WHERE [key] IN(N'planHash',N'contractHash',N'configurationHash')
            AND (DATALENGTH(value)<>128 OR value COLLATE Latin1_General_100_BIN2 LIKE '%[^a-f0-9]%'))
        THROW 52411,N'AUTHORIZATION_SCOPE_INVALID',1;
    DECLARE @now DATETIME2(3)=SYSUTCDATETIME(),@until DATETIME2(3),@reason VARCHAR(40)='AUTHORITY_UNCONFIGURED',
        @sid VARBINARY(85)=SUSER_SID(ORIGINAL_LOGIN()),@audit UNIQUEIDENTIFIER,@mapping BIGINT,@scope_version BIGINT,
        @hash CHAR(64)=LOWER(CONVERT(CHAR(64),HASHBYTES('SHA2_256',@scope_material),2)),@seconds INT=1,
        @environment NVARCHAR(32)=JSON_VALUE(@scope_material,'$.environment'),
        @source NVARCHAR(128)=JSON_VALUE(@scope_material,'$.source'),@tenant NVARCHAR(128)=JSON_VALUE(@scope_material,'$.tenant'),
        @workload NVARCHAR(128)=JSON_VALUE(@scope_material,'$.workload'),@mode NVARCHAR(16)=JSON_VALUE(@scope_material,'$.mode');
    IF @environment IS NULL OR @source IS NULL OR @tenant IS NULL OR @workload IS NULL OR @mode IS NULL
        OR @mode COLLATE Latin1_General_100_BIN2 NOT IN(N'INCREMENTAL',N'BACKFILL',N'BOOTSTRAP',N'REPLAY')
        OR (@action=N'REPLAY' AND @mode<>N'REPLAY') OR (@mode=N'REPLAY' AND @action NOT IN(N'REPLAY',N'STATUS'))
        OR (@mode=N'REPLAY' AND TRY_CONVERT(UNIQUEIDENTIFIER,JSON_VALUE(@scope_material,'$.replayOf')) IS NULL)
        OR (@mode<>N'REPLAY' AND DATALENGTH(JSON_VALUE(@scope_material,'$.replayOf'))<>0)
        THROW 52411,N'AUTHORIZATION_SCOPE_INVALID',1;
    BEGIN TRY
        BEGIN TRANSACTION;
        DECLARE @lock_result INT,@lock_resource NVARCHAR(255)=N'V2_AUTH_'+CONVERT(NVARCHAR(36),@invocation_id);
        EXEC @lock_result=sys.sp_getapplock @Resource=@lock_resource,@LockMode='Exclusive',@LockOwner='Transaction',@LockTimeout=10000;
        IF @lock_result<0 THROW 52412,N'AUTHORIZATION_LOCK_UNAVAILABLE',1;
        SET @now=SYSUTCDATETIME();
        IF EXISTS(SELECT 1 FROM ctl.runtime_authority_configuration WITH(HOLDLOCK) WHERE singleton=1 AND enabled=1
            AND authority_id=@authority_id AND server_name=CONVERT(NVARCHAR(128),SERVERPROPERTY('ServerName')) COLLATE Latin1_General_100_BIN2
            AND database_name=DB_NAME() COLLATE Latin1_General_100_BIN2 AND policy_fingerprint=@policy_fingerprint)
        BEGIN
            SELECT @seconds=capability_seconds FROM ctl.runtime_authority_configuration WITH(HOLDLOCK) WHERE singleton=1;
            SET @reason='IDENTITY_UNMAPPED';
            IF ISNULL(CONVERT(NVARCHAR(40),CONNECTIONPROPERTY('auth_scheme')),N'') NOT IN(N'NTLM',N'KERBEROS') SET @reason='AUTHENTICATION_INVALID';
            ELSE IF @sid IS NULL OR SUSER_SID()<>@sid OR ORIGINAL_LOGIN()<>SUSER_SNAME() SET @reason='CONTEXT_CHANGED';
            ELSE IF IS_SRVROLEMEMBER('sysadmin')=1 OR IS_MEMBER('db_owner')=1
                OR HAS_PERMS_BY_NAME(NULL,NULL,'CONTROL SERVER')=1 OR HAS_PERMS_BY_NAME(DB_NAME(),'DATABASE','CONTROL')=1
                OR HAS_PERMS_BY_NAME(N'ctl.runtime_identity_mapping','OBJECT','SELECT')=1
                OR HAS_PERMS_BY_NAME(DB_NAME(),'DATABASE','ALTER ANY ROLE')=1 SET @reason='PRIVILEGE_EXCESSIVE';
            ELSE IF NOT EXISTS(SELECT 1 FROM sys.server_principals WHERE sid=@sid AND type IN('U','G') AND is_disabled=0)
                SET @reason='IDENTITY_DISABLED_OR_UNVERIFIABLE';
            ELSE
            BEGIN
                SELECT @audit=audit_reference,@mapping=mapping_version,@until=valid_until_utc
                FROM ctl.runtime_identity_mapping WITH(HOLDLOCK) WHERE original_sid=@sid AND revoked=0
                    AND valid_from_utc<=@now AND valid_until_utc>@now
                    AND ((@action=N'STATUS' AND observer=1) OR (@action=N'RUN' AND executor=1)
                        OR (@action=N'REPLAY' AND executor=1 AND replay=1) OR (@action=N'FORCE_RUN' AND executor=1 AND force_run=1));
                IF @audit IS NOT NULL
                BEGIN
                    SET @reason='SCOPE_REJECTED';
                    SELECT @scope_version=scope_version FROM ctl.runtime_identity_scope WITH(HOLDLOCK)
                    WHERE original_sid=@sid AND environment_name=@environment AND source_instance=@source AND tenant_scope=@tenant
                        AND workload=@workload AND mode=@mode AND policy_fingerprint=@policy_fingerprint AND revoked=0;
                    IF @scope_version IS NOT NULL SET @reason='AUTHORIZED';
                END;
            END;
        END;
        IF @until IS NULL OR @until>DATEADD(SECOND,@seconds,@now) SET @until=DATEADD(SECOND,@seconds,@now);
        IF @operation=N'AUTHORIZE'
        BEGIN
            IF EXISTS(SELECT 1 FROM ctl.runtime_authorization_decision WITH(UPDLOCK,HOLDLOCK) WHERE invocation_id=@invocation_id
                AND (authority_id<>@authority_id OR action<>@action OR execution_id<>@execution_id OR scope_hash<>@hash
                    OR scope_material<>@scope_material OR DATALENGTH(scope_material)<>DATALENGTH(@scope_material)
                    OR policy_fingerprint<>@policy_fingerprint OR ISNULL(audit_reference,'00000000-0000-0000-0000-000000000000')
                        <>ISNULL(@audit,'00000000-0000-0000-0000-000000000000')))
                THROW 52413,N'AUTHORIZATION_INVOCATION_CONFLICT',1;
            IF NOT EXISTS(SELECT 1 FROM ctl.runtime_authorization_decision WITH(UPDLOCK,HOLDLOCK) WHERE invocation_id=@invocation_id)
                INSERT ctl.runtime_authorization_decision VALUES(@invocation_id,NEWID(),@authority_id,@action,@execution_id,
                    @scope_material,@hash,@audit,@mapping,@scope_version,@policy_fingerprint,
                    CASE WHEN @reason='AUTHORIZED' THEN 'ALLOW' ELSE 'DENY' END,@reason,@now,@until);
            COMMIT TRANSACTION;
            SELECT receipt_id,decision,reason,audit_reference,mapping_version,scope_version,policy_fingerprint,
                authorized_at_utc,valid_until_utc,scope_hash,CAST(NULL AS BIGINT) fence
            FROM ctl.runtime_authorization_decision WHERE invocation_id=@invocation_id;
            RETURN;
        END;
        IF @reason<>'AUTHORIZED' THROW 52414,N'AUTHORIZATION_CONSUMPTION_REJECTED',1;
        IF NOT EXISTS(SELECT 1 FROM ctl.runtime_authorization_decision WITH(UPDLOCK,HOLDLOCK) WHERE invocation_id=@invocation_id
            AND receipt_id=@receipt_id AND decision='ALLOW' AND authority_id=@authority_id AND action=@action AND execution_id=@execution_id
            AND scope_hash=@hash AND scope_material=@scope_material AND DATALENGTH(scope_material)=DATALENGTH(@scope_material)
            AND mapping_version=@mapping AND scope_version=@scope_version AND audit_reference=@audit
            AND policy_fingerprint=@policy_fingerprint AND authorized_at_utc<=@now AND valid_until_utc>@now)
            THROW 52414,N'AUTHORIZATION_CONSUMPTION_REJECTED',1;
        IF EXISTS(SELECT 1 FROM ctl.runtime_authorization_consumption WITH(UPDLOCK,HOLDLOCK) WHERE invocation_id=@invocation_id)
            THROW 52415,N'AUTHORIZATION_ALREADY_CONSUMED',1;
        -- Intent links the exact existing runtime occurrence. Recovery reads that occurrence;
        -- NOT_FOUND permits only its original idempotent start, never a replacement execution_id.
        INSERT ctl.runtime_authorization_consumption(invocation_id,execution_id,consumed_at_utc) VALUES(@invocation_id,@execution_id,@now);
        COMMIT TRANSACTION;
        SELECT d.receipt_id,d.decision,d.reason,d.audit_reference,d.mapping_version,d.scope_version,d.policy_fingerprint,
            d.authorized_at_utc,d.valid_until_utc,d.scope_hash,c.fence
        FROM ctl.runtime_authorization_decision d JOIN ctl.runtime_authorization_consumption c ON c.invocation_id=d.invocation_id
        WHERE d.invocation_id=@invocation_id;
    END TRY
    BEGIN CATCH
        IF XACT_STATE()<>0 ROLLBACK TRANSACTION;
        THROW;
    END CATCH;
END;
GO