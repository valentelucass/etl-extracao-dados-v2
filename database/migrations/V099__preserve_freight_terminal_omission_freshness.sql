SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;
GO

-- FRE-02/FRE-03. A terminal observation may omit the CT-e that supplied stored freshness.
-- Preserve the effective CT-e freshness only for that omission, within the complete namespace.
-- NULL, another entity, missing current state and ordinary stale observations remain excluded.
CREATE OR ALTER FUNCTION core.ufn_frete_terminal_omission_candidates(
    @execution_id UNIQUEIDENTIFIER
) RETURNS TABLE
AS RETURN (
    SELECT candidate.source_key
    FROM stg.execution_candidate candidate
    JOIN stg.frete_record typed_record
      ON typed_record.stage_record_id=candidate.winner_stage_record_id
     AND typed_record.execution_id=candidate.execution_id
    JOIN ctl.execution_attempt attempt ON attempt.execution_id=candidate.execution_id
    JOIN ctl.execution_partition partition ON partition.partition_id=attempt.partition_id
    JOIN core.frete current_record WITH(UPDLOCK,HOLDLOCK)
      ON current_record.environment_name=partition.environment_name
     AND current_record.source_instance=partition.source_instance
     AND current_record.tenant_scope=partition.tenant_scope
     AND current_record.entity_name=partition.entity_name
     AND current_record.source_key=candidate.source_key
    JOIN core.entity_record_state generic_record WITH(UPDLOCK,HOLDLOCK)
      ON generic_record.environment_name=partition.environment_name
     AND generic_record.source_instance=partition.source_instance
     AND generic_record.tenant_scope=partition.tenant_scope
     AND generic_record.entity_name=partition.entity_name
     AND generic_record.source_key=candidate.source_key
     AND generic_record.source_freshness_at_utc=current_record.freshness_at_utc
    WHERE candidate.execution_id=@execution_id AND partition.entity_name=N'fretes'
      AND current_record.terminal=0 AND typed_record.terminal=1
      AND JSON_VALUE(typed_record.field_presence_json,N'$.status')=N'VALUE'
      AND current_record.freshness_origin IN(N'CTE_CREATED_AT',N'CTE_ISSUED_AT')
      AND typed_record.freshness_origin IN(N'CRIADO_EM',N'SERVICO_EM')
      AND typed_record.freshness_at_utc<=current_record.freshness_at_utc
      AND candidate.source_freshness_at_utc=typed_record.freshness_at_utc
      AND JSON_VALUE(typed_record.field_presence_json,N'$.cte_created_at')=N'ABSENT'
      AND JSON_VALUE(typed_record.field_presence_json,N'$.cte_issued_at')=N'ABSENT'
      AND JSON_VALUE(typed_record.field_presence_json,N'$.cte')=N'ABSENT'
      AND JSON_VALUE(typed_record.field_presence_json,N'$.ctes')=N'ABSENT'
      AND JSON_VALUE(typed_record.field_presence_json,N'$.cte_key')=N'ABSENT'
);
GO

CREATE OR ALTER PROCEDURE core.usp_apply_reconcile_publish_execution
    @execution_id UNIQUEIDENTIFIER,
    @contract_version NVARCHAR(MAX),
    @contract_fingerprint NVARCHAR(MAX),
    @configuration_version NVARCHAR(MAX),
    @configuration_fingerprint NVARCHAR(MAX)
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;

    IF DATALENGTH(@contract_version) > 256
        OR DATALENGTH(@contract_fingerprint) > 128
        OR DATALENGTH(@configuration_version) > 256
        OR DATALENGTH(@configuration_fingerprint) > 128
        THROW 51420, N'Aplicação reconciliada inválida.', 1;

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
        THROW 51420, N'Aplicação reconciliada inválida.', 1;

    BEGIN TRANSACTION;

    DECLARE @partition_id BIGINT;
    DECLARE @environment_name NVARCHAR(32);
    DECLARE @source_instance NVARCHAR(128);
    DECLARE @tenant_scope NVARCHAR(128);
    DECLARE @entity_name NVARCHAR(128);
    DECLARE @execution_mode NVARCHAR(16);

    SELECT
        @partition_id = attempt.partition_id,
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
        THROW 51421, N'A execução não existe para aplicação reconciliada.', 1;

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
        THROW 51422, N'Não foi possível serializar a aplicação do namespace.', 1;

    DECLARE @execution_state NVARCHAR(32);
    DECLARE @partition_state NVARCHAR(32);
    DECLARE @current_execution_id UNIQUEIDENTIFIER;
    DECLARE @next_transition_sequence INT;
    DECLARE @persisted_contract_version NVARCHAR(128);
    DECLARE @persisted_contract_fingerprint CHAR(64);
    DECLARE @persisted_configuration_version NVARCHAR(128);
    DECLARE @persisted_configuration_fingerprint CHAR(64);

    SELECT
        @execution_state = attempt.current_state,
        @next_transition_sequence = attempt.next_transition_sequence,
        @partition_state = partition.current_state,
        @current_execution_id = partition.current_execution_id,
        @persisted_contract_version = attempt.contract_version,
        @persisted_contract_fingerprint = attempt.contract_fingerprint,
        @persisted_configuration_version = attempt.configuration_version,
        @persisted_configuration_fingerprint = attempt.configuration_fingerprint,
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

    -- Retry depois de commit incerto: a resposta vem apenas da evidência imutável da execução.
    -- O core e o pointer podem legitimamente ter sido atualizados por uma execução posterior.
    IF @execution_state COLLATE Latin1_General_100_BIN2 = N'PUBLISHED'
    BEGIN
        IF NOT EXISTS (
            SELECT 1
            FROM recon.execution_reconciliation_result AS result WITH (UPDLOCK, HOLDLOCK)
            INNER JOIN ctl.execution_promotion_result AS candidate_set WITH (UPDLOCK, HOLDLOCK)
                ON candidate_set.execution_id = result.execution_id
            INNER JOIN ctl.execution_publication_event AS publication WITH (UPDLOCK, HOLDLOCK)
                ON publication.execution_id = result.execution_id
            WHERE result.execution_id = @execution_id
              AND publication.partition_id = @partition_id
              AND candidate_set.candidate_rows = result.candidate_rows
              AND candidate_set.quarantined_stage_rows = 0
              AND result.candidate_rows = (
                  SELECT COUNT_BIG(*)
                  FROM recon.execution_candidate_application WITH (UPDLOCK, HOLDLOCK)
                  WHERE execution_id = @execution_id
              )
              AND result.inserted_rows = (
                  SELECT COUNT_BIG(*)
                  FROM recon.execution_candidate_application WITH (UPDLOCK, HOLDLOCK)
                  WHERE execution_id = @execution_id
                    AND application_disposition = N'INSERTED'
              )
              AND result.updated_rows = (
                  SELECT COUNT_BIG(*)
                  FROM recon.execution_candidate_application WITH (UPDLOCK, HOLDLOCK)
                  WHERE execution_id = @execution_id
                    AND application_disposition = N'UPDATED'
              )
              AND result.reactivated_rows = (
                  SELECT COUNT_BIG(*)
                  FROM recon.execution_candidate_application WITH (UPDLOCK, HOLDLOCK)
                  WHERE execution_id = @execution_id
                    AND application_disposition = N'REACTIVATED'
              )
              AND result.noop_rows = (
                  SELECT COUNT_BIG(*)
                  FROM recon.execution_candidate_application WITH (UPDLOCK, HOLDLOCK)
                  WHERE execution_id = @execution_id
                    AND application_disposition IN (N'NO_OP', N'STALE_NO_OP')
              )
              AND result.stale_noop_rows = (
                  SELECT COUNT_BIG(*)
                  FROM recon.execution_candidate_application WITH (UPDLOCK, HOLDLOCK)
                  WHERE execution_id = @execution_id
                    AND application_disposition = N'STALE_NO_OP'
              )
              AND EXISTS (
                  SELECT 1
                  FROM ctl.execution_state_event AS event WITH (UPDLOCK, HOLDLOCK)
                  WHERE event.execution_id = @execution_id
                    AND event.transition_sequence = @next_transition_sequence - 2
                    AND event.previous_state = N'PROMOTED'
                    AND event.next_state = N'RECONCILED'
                    AND event.reason_code = N'CANDIDATE_SET_RECONCILED'
              )
              AND EXISTS (
                  SELECT 1
                  FROM ctl.execution_state_event AS event WITH (UPDLOCK, HOLDLOCK)
                  WHERE event.execution_id = @execution_id
                    AND event.transition_sequence = @next_transition_sequence - 1
                    AND event.previous_state = N'RECONCILED'
                    AND event.next_state = N'PUBLISHED'
                    AND event.reason_code = N'RECONCILIATION_PUBLISHED'
              )
              AND EXISTS (
                  SELECT 1
                  FROM ctl.execution_lease AS lease WITH (UPDLOCK, HOLDLOCK)
                  WHERE lease.execution_id = @execution_id
                    AND lease.partition_id = @partition_id
                    AND lease.released_at_utc IS NOT NULL
              )
        )
            THROW 51423, N'Execução publicada sem evidência imutável coerente.', 1;

        COMMIT TRANSACTION;

        SELECT
            result.execution_id,
            result.candidate_rows,
            result.inserted_rows,
            result.updated_rows,
            result.reactivated_rows,
            result.noop_rows,
            result.stale_noop_rows,
            result.reconciled_at_utc,
            result.published_at_utc,
            publication.incremental_frontier_before_utc,
            publication.incremental_frontier_after_utc
        FROM recon.execution_reconciliation_result AS result
        INNER JOIN ctl.execution_publication_event AS publication
            ON publication.execution_id = result.execution_id
        WHERE result.execution_id = @execution_id;
        RETURN;
    END;

    IF @execution_state COLLATE Latin1_General_100_BIN2 <> N'PROMOTED'
        OR @partition_state COLLATE Latin1_General_100_BIN2 <> N'PROMOTED'
        OR @current_execution_id <> @execution_id
        THROW 51424, N'A execução não está promovida e corrente para aplicação.', 1;

    IF EXISTS (
        SELECT 1 FROM recon.execution_candidate_application WITH (UPDLOCK, HOLDLOCK)
        WHERE execution_id = @execution_id
    ) OR EXISTS (
        SELECT 1 FROM recon.execution_reconciliation_result WITH (UPDLOCK, HOLDLOCK)
        WHERE execution_id = @execution_id
    ) OR EXISTS (
        SELECT 1 FROM ctl.execution_publication_event WITH (UPDLOCK, HOLDLOCK)
        WHERE execution_id = @execution_id
    ) OR EXISTS (
        SELECT 1 FROM ctl.partition_publication_pointer WITH (UPDLOCK, HOLDLOCK)
        WHERE published_execution_id = @execution_id
    )
        THROW 51425, N'Aplicação parcial pré-existente foi recusada.', 1;

    DECLARE @database_now_utc DATETIME2(3) = SYSUTCDATETIME();
    DECLARE @lease_expires_at_utc DATETIME2(3);
    DECLARE @lease_released_at_utc DATETIME2(3);

    SELECT
        @lease_expires_at_utc = lease.expires_at_utc,
        @lease_released_at_utc = lease.released_at_utc
    FROM ctl.execution_lease AS lease WITH (UPDLOCK, HOLDLOCK)
    WHERE lease.partition_id = @partition_id
      AND lease.execution_id = @execution_id;

    IF @lease_expires_at_utc IS NULL
        OR @lease_released_at_utc IS NOT NULL
        OR @lease_expires_at_utc <= @database_now_utc
        THROW 51426, N'A execução não possui lease corrente e ativa para aplicação.', 1;

    DECLARE @candidate_rows BIGINT;
    SELECT @candidate_rows = result.candidate_rows
    FROM ctl.execution_promotion_result AS result WITH (UPDLOCK, HOLDLOCK)
    WHERE result.execution_id = @execution_id
      AND result.quarantined_root_keys = 0
      AND result.unidentified_quarantine_rows = 0
      AND result.quarantined_stage_rows = 0
      AND result.candidate_rows = (
          SELECT COUNT_BIG(*)
          FROM stg.execution_candidate WITH (UPDLOCK, HOLDLOCK)
          WHERE execution_id = @execution_id
      )
      AND result.physical_rows = result.distinct_root_keys + result.duplicate_rows
      AND result.distinct_root_keys = result.candidate_rows;

    IF @candidate_rows IS NULL
        THROW 51427, N'Candidate set ausente, divergente ou com quarantine.', 1;

    -- FRE-02/FRE-03: raw staging remains immutable; only effective freshness is preserved.
    DECLARE @terminal_omission TABLE (
        source_key NVARCHAR(256) COLLATE Latin1_General_100_BIN2 NOT NULL PRIMARY KEY
    );
    INSERT @terminal_omission SELECT source_key
    FROM core.ufn_frete_terminal_omission_candidates(@execution_id);

    IF EXISTS (
        SELECT 1
        FROM stg.execution_candidate AS candidate WITH (UPDLOCK, HOLDLOCK)
        INNER JOIN core.entity_record_state AS current_record WITH (
            UPDLOCK, HOLDLOCK, INDEX(UQ_core_entity_record_state_source)
        )
            ON current_record.environment_name = @environment_name
           AND current_record.source_instance = @source_instance
           AND current_record.tenant_scope = @tenant_scope
           AND current_record.entity_name = @entity_name
           AND current_record.source_key = candidate.source_key
        WHERE candidate.execution_id = @execution_id
          AND NOT EXISTS(SELECT 1 FROM @terminal_omission omission
                WHERE omission.source_key=candidate.source_key)
          AND NOT (
              current_record.row_fingerprint_version = candidate.row_fingerprint_version
              AND current_record.source_row_hash = candidate.source_row_hash
              AND current_record.presence_fingerprint_version =
                  candidate.presence_fingerprint_version
              AND current_record.presence_fingerprint = candidate.presence_fingerprint
              AND (
                  current_record.source_freshness_at_utc = candidate.source_freshness_at_utc
                  OR (
                      current_record.source_freshness_at_utc IS NULL
                      AND candidate.source_freshness_at_utc IS NULL
                  )
              )
          )
          AND NOT (
              candidate.source_freshness_at_utc IS NOT NULL
              AND (
                  current_record.source_freshness_at_utc IS NULL
                  OR candidate.source_freshness_at_utc > current_record.source_freshness_at_utc
              )
          )
          AND NOT (
              current_record.source_freshness_at_utc IS NOT NULL
              AND (
                  candidate.source_freshness_at_utc IS NULL
                  OR candidate.source_freshness_at_utc < current_record.source_freshness_at_utc
              )
          )
    )
        THROW 51428, N'Frescor igual ou desconhecido possui conteúdo divergente.', 1;

    DECLARE @application_plan TABLE (
        source_key NVARCHAR(256) COLLATE Latin1_General_100_BIN2 NOT NULL PRIMARY KEY,
        record_state_id BIGINT NULL,
        application_disposition NVARCHAR(24) COLLATE Latin1_General_100_BIN2 NOT NULL,
        apply_candidate_values BIT NOT NULL
    );

    INSERT INTO @application_plan (
        source_key, record_state_id, application_disposition, apply_candidate_values
    )
    SELECT
        candidate.source_key,
        current_record.record_state_id,
        CASE
            WHEN current_record.record_state_id IS NULL THEN N'INSERTED'
            WHEN EXISTS(SELECT 1 FROM @terminal_omission omission
                WHERE omission.source_key=candidate.source_key)
                THEN CASE WHEN current_record.active = 0 THEN N'REACTIVATED' ELSE N'UPDATED' END
            WHEN current_record.row_fingerprint_version = candidate.row_fingerprint_version
             AND current_record.source_row_hash = candidate.source_row_hash
             AND current_record.presence_fingerprint_version = candidate.presence_fingerprint_version
             AND current_record.presence_fingerprint = candidate.presence_fingerprint
             AND (
                 current_record.source_freshness_at_utc = candidate.source_freshness_at_utc
                 OR (
                     current_record.source_freshness_at_utc IS NULL
                     AND candidate.source_freshness_at_utc IS NULL
                 )
             )
                THEN CASE WHEN current_record.active = 0 THEN N'REACTIVATED' ELSE N'NO_OP' END
            WHEN candidate.source_freshness_at_utc IS NOT NULL
             AND (
                 current_record.source_freshness_at_utc IS NULL
                 OR candidate.source_freshness_at_utc > current_record.source_freshness_at_utc
             )
                THEN CASE WHEN current_record.active = 0 THEN N'REACTIVATED' ELSE N'UPDATED' END
            ELSE CASE WHEN current_record.active = 0 THEN N'REACTIVATED' ELSE N'STALE_NO_OP' END
        END,
        CASE
            WHEN current_record.record_state_id IS NULL THEN 1
            WHEN EXISTS(SELECT 1 FROM @terminal_omission omission
                WHERE omission.source_key=candidate.source_key) THEN 1
            WHEN candidate.source_freshness_at_utc IS NOT NULL
             AND (
                 current_record.source_freshness_at_utc IS NULL
                 OR candidate.source_freshness_at_utc > current_record.source_freshness_at_utc
             ) THEN 1
            ELSE 0
        END
    FROM stg.execution_candidate AS candidate WITH (UPDLOCK, HOLDLOCK)
    LEFT JOIN core.entity_record_state AS current_record WITH (
        UPDLOCK, HOLDLOCK, INDEX(UQ_core_entity_record_state_source)
    )
        ON current_record.environment_name = @environment_name
       AND current_record.source_instance = @source_instance
       AND current_record.tenant_scope = @tenant_scope
       AND current_record.entity_name = @entity_name
       AND current_record.source_key = candidate.source_key
    WHERE candidate.execution_id = @execution_id;

    IF (SELECT COUNT_BIG(*) FROM @application_plan) <> @candidate_rows
        THROW 51429, N'O plano de aplicação não cobre todo o candidate set.', 1;

    INSERT INTO core.entity_record_state (
        environment_name, source_instance, tenant_scope, entity_name, source_key,
        row_fingerprint_version, source_row_hash, presence_fingerprint_version,
        presence_fingerprint, source_freshness_at_utc, active,
        first_promoted_execution_id, last_promoted_execution_id,
        first_promoted_at_utc, last_promoted_at_utc
    )
    SELECT
        @environment_name, @source_instance, @tenant_scope, @entity_name, candidate.source_key,
        candidate.row_fingerprint_version, candidate.source_row_hash,
        candidate.presence_fingerprint_version, candidate.presence_fingerprint,
        candidate.source_freshness_at_utc, 1,
        @execution_id, @execution_id, @database_now_utc, @database_now_utc
    FROM @application_plan AS application
    INNER JOIN stg.execution_candidate AS candidate
        ON candidate.execution_id = @execution_id
       AND candidate.source_key = application.source_key
    WHERE application.application_disposition = N'INSERTED';

    UPDATE current_record
    SET row_fingerprint_version = CASE WHEN application.apply_candidate_values = 1
            THEN candidate.row_fingerprint_version ELSE current_record.row_fingerprint_version END,
        source_row_hash = CASE WHEN application.apply_candidate_values = 1
            THEN candidate.source_row_hash ELSE current_record.source_row_hash END,
        presence_fingerprint_version = CASE WHEN application.apply_candidate_values = 1
            THEN candidate.presence_fingerprint_version
            ELSE current_record.presence_fingerprint_version END,
        presence_fingerprint = CASE WHEN application.apply_candidate_values = 1
            THEN candidate.presence_fingerprint ELSE current_record.presence_fingerprint END,
        source_freshness_at_utc = CASE WHEN EXISTS(SELECT 1 FROM @terminal_omission omission
                WHERE omission.source_key=candidate.source_key)
            THEN current_record.source_freshness_at_utc
            WHEN application.apply_candidate_values = 1
            THEN candidate.source_freshness_at_utc ELSE current_record.source_freshness_at_utc END,
        active = 1,
        last_promoted_execution_id = @execution_id,
        last_promoted_at_utc = @database_now_utc
    FROM core.entity_record_state AS current_record
    INNER JOIN @application_plan AS application
        ON application.record_state_id = current_record.record_state_id
    INNER JOIN stg.execution_candidate AS candidate
        ON candidate.execution_id = @execution_id
       AND candidate.source_key = application.source_key
    WHERE application.application_disposition IN (N'UPDATED', N'REACTIVATED');

    UPDATE application
    SET record_state_id = current_record.record_state_id
    FROM @application_plan AS application
    INNER JOIN core.entity_record_state AS current_record
        ON current_record.environment_name = @environment_name
       AND current_record.source_instance = @source_instance
       AND current_record.tenant_scope = @tenant_scope
       AND current_record.entity_name = @entity_name
       AND current_record.source_key = application.source_key;

    IF EXISTS (SELECT 1 FROM @application_plan WHERE record_state_id IS NULL)
        THROW 51430, N'A aplicação não produziu identidade técnica para todo candidato.', 1;

    INSERT INTO recon.execution_candidate_application (
        execution_id, source_key, record_state_id, application_disposition,
        result_row_fingerprint_version, result_source_row_hash,
        result_presence_fingerprint_version, result_presence_fingerprint,
        result_source_freshness_at_utc, applied_at_utc
    )
    SELECT
        @execution_id, application.source_key, application.record_state_id,
        application.application_disposition,
        current_record.row_fingerprint_version, current_record.source_row_hash,
        current_record.presence_fingerprint_version, current_record.presence_fingerprint,
        current_record.source_freshness_at_utc, @database_now_utc
    FROM @application_plan AS application
    INNER JOIN core.entity_record_state AS current_record
        ON current_record.record_state_id = application.record_state_id;

    DECLARE @inserted_rows BIGINT = (
        SELECT COUNT_BIG(*) FROM @application_plan WHERE application_disposition = N'INSERTED'
    );
    DECLARE @updated_rows BIGINT = (
        SELECT COUNT_BIG(*) FROM @application_plan WHERE application_disposition = N'UPDATED'
    );
    DECLARE @reactivated_rows BIGINT = (
        SELECT COUNT_BIG(*) FROM @application_plan WHERE application_disposition = N'REACTIVATED'
    );
    DECLARE @noop_rows BIGINT = (
        SELECT COUNT_BIG(*) FROM @application_plan
        WHERE application_disposition IN (N'NO_OP', N'STALE_NO_OP')
    );
    DECLARE @stale_noop_rows BIGINT = (
        SELECT COUNT_BIG(*) FROM @application_plan WHERE application_disposition = N'STALE_NO_OP'
    );
    DECLARE @reconciled_at_utc DATETIME2(3) = SYSUTCDATETIME();
    DECLARE @published_at_utc DATETIME2(3) = SYSUTCDATETIME();

    IF @candidate_rows <> @inserted_rows + @updated_rows + @reactivated_rows + @noop_rows
        THROW 51431, N'As contagens da aplicação não reconciliam o candidate set.', 1;

    IF @lease_expires_at_utc <= @published_at_utc
        THROW 51432, N'O lease expirou antes do commit de publicação.', 1;

    INSERT INTO recon.execution_reconciliation_result (
        execution_id, candidate_rows, inserted_rows, updated_rows, reactivated_rows,
        noop_rows, stale_noop_rows, reconciled_at_utc, published_at_utc
    ) VALUES (
        @execution_id, @candidate_rows, @inserted_rows, @updated_rows, @reactivated_rows,
        @noop_rows, @stale_noop_rows, @reconciled_at_utc, @published_at_utc
    );

    UPDATE ctl.execution_attempt
    SET current_state = N'RECONCILED',
        next_transition_sequence = next_transition_sequence + 1
    WHERE execution_id = @execution_id
      AND current_state = N'PROMOTED'
      AND next_transition_sequence = @next_transition_sequence;

    IF @@ROWCOUNT <> 1
        THROW 51433, N'O estado mudou antes da reconciliação.', 1;

    INSERT INTO ctl.execution_state_event (
        execution_id, transition_sequence, previous_state, next_state,
        reason_code, transitioned_at_utc
    ) VALUES (
        @execution_id, @next_transition_sequence, N'PROMOTED', N'RECONCILED',
        N'CANDIDATE_SET_RECONCILED', @reconciled_at_utc
    );

    UPDATE ctl.execution_partition
    SET current_state = N'RECONCILED'
    WHERE partition_id = @partition_id
      AND current_execution_id = @execution_id
      AND current_state = N'PROMOTED';

    IF @@ROWCOUNT <> 1
        THROW 51434, N'A partição deixou de ser corrente durante a reconciliação.', 1;

    UPDATE ctl.execution_attempt
    SET current_state = N'PUBLISHED',
        terminal_at_utc = @published_at_utc,
        next_transition_sequence = next_transition_sequence + 1
    WHERE execution_id = @execution_id
      AND current_state = N'RECONCILED'
      AND next_transition_sequence = @next_transition_sequence + 1;

    IF @@ROWCOUNT <> 1
        THROW 51435, N'O estado mudou antes da publicação.', 1;

    INSERT INTO ctl.execution_state_event (
        execution_id, transition_sequence, previous_state, next_state,
        reason_code, transitioned_at_utc
    ) VALUES (
        @execution_id, @next_transition_sequence + 1, N'RECONCILED', N'PUBLISHED',
        N'RECONCILIATION_PUBLISHED', @published_at_utc
    );

    UPDATE ctl.execution_partition
    SET current_state = N'PUBLISHED'
    WHERE partition_id = @partition_id
      AND current_execution_id = @execution_id
      AND current_state = N'RECONCILED';

    IF @@ROWCOUNT <> 1
        THROW 51436, N'A partição deixou de ser corrente durante a publicação.', 1;

    DECLARE @previous_published_execution_id UNIQUEIDENTIFIER;
    SELECT @previous_published_execution_id = pointer.published_execution_id
    FROM ctl.partition_publication_pointer AS pointer WITH (UPDLOCK, HOLDLOCK)
    WHERE pointer.partition_id = @partition_id;

    IF @previous_published_execution_id IS NULL
    BEGIN
        INSERT INTO ctl.partition_publication_pointer (
            partition_id, published_execution_id, published_at_utc
        ) VALUES (
            @partition_id, @execution_id, @published_at_utc
        );
    END
    ELSE
    BEGIN
        UPDATE ctl.partition_publication_pointer
        SET published_execution_id = @execution_id,
            published_at_utc = @published_at_utc
        WHERE partition_id = @partition_id
          AND published_execution_id = @previous_published_execution_id;

        IF @@ROWCOUNT <> 1
            THROW 51437, N'O pointer de publicação mudou durante o commit.', 1;
    END;

    DECLARE @incremental_frontier_before_utc DATETIME2(3) = NULL;
    DECLARE @incremental_frontier_after_utc DATETIME2(3) = NULL;
    DECLARE @watermark_last_partition_before BIGINT = NULL;
    DECLARE @watermark_last_partition_after BIGINT = NULL;

    IF @execution_mode COLLATE Latin1_General_100_BIN2 = N'INCREMENTAL'
    BEGIN
        SELECT
            @incremental_frontier_before_utc = watermark.contiguous_partition_end_utc,
            @watermark_last_partition_before = watermark.last_partition_id
        FROM ctl.incremental_publication_watermark AS watermark WITH (UPDLOCK, HOLDLOCK)
        WHERE watermark.environment_name = @environment_name
          AND watermark.source_instance = @source_instance
          AND watermark.tenant_scope = @tenant_scope
          AND watermark.entity_name = @entity_name;

        IF @incremental_frontier_before_utc IS NULL
            THROW 51438, N'Publicação incremental exige fronteira previamente registrada.', 1;

        ;WITH contiguous_publications AS (
            SELECT
                CAST(NULL AS BIGINT) AS partition_id,
                @incremental_frontier_before_utc AS contiguous_end_utc,
                CAST(0 AS INT) AS traversal_depth
            UNION ALL
            SELECT
                next_partition.partition_id,
                next_partition.partition_end_exclusive_utc,
                contiguous.traversal_depth + 1
            FROM contiguous_publications AS contiguous
            INNER JOIN ctl.execution_partition AS next_partition
                ON next_partition.environment_name = @environment_name
               AND next_partition.source_instance = @source_instance
               AND next_partition.tenant_scope = @tenant_scope
               AND next_partition.entity_name = @entity_name
               AND next_partition.execution_mode = N'INCREMENTAL'
               AND next_partition.partition_start_utc = contiguous.contiguous_end_utc
               AND next_partition.partition_end_exclusive_utc > contiguous.contiguous_end_utc
            INNER JOIN ctl.partition_publication_pointer AS next_pointer
                ON next_pointer.partition_id = next_partition.partition_id
            INNER JOIN ctl.execution_attempt AS published_attempt
                ON published_attempt.partition_id = next_partition.partition_id
               AND published_attempt.execution_id = next_pointer.published_execution_id
               AND published_attempt.current_state = N'PUBLISHED'
        )
        SELECT TOP (1)
            @incremental_frontier_after_utc = contiguous_end_utc,
            @watermark_last_partition_after =
                COALESCE(partition_id, @watermark_last_partition_before)
        FROM contiguous_publications
        ORDER BY contiguous_end_utc DESC, partition_id DESC
        OPTION (MAXRECURSION 32767);

        IF @incremental_frontier_after_utc > @incremental_frontier_before_utc
        BEGIN
            UPDATE ctl.incremental_publication_watermark
            SET contiguous_partition_end_utc = @incremental_frontier_after_utc,
                last_partition_id = @watermark_last_partition_after,
                advanced_at_utc = @published_at_utc
            WHERE environment_name = @environment_name
              AND source_instance = @source_instance
              AND tenant_scope = @tenant_scope
              AND entity_name = @entity_name
              AND contiguous_partition_end_utc = @incremental_frontier_before_utc;

            IF @@ROWCOUNT <> 1
                THROW 51439, N'A fronteira incremental mudou durante o commit.', 1;
        END;
    END;

    INSERT INTO ctl.execution_publication_event (
        execution_id, partition_id, previous_published_execution_id, published_at_utc,
        incremental_frontier_before_utc, incremental_frontier_after_utc,
        watermark_last_partition_id
    ) VALUES (
        @execution_id, @partition_id, @previous_published_execution_id, @published_at_utc,
        @incremental_frontier_before_utc, @incremental_frontier_after_utc,
        @watermark_last_partition_after
    );

    UPDATE ctl.execution_lease
    SET released_at_utc = @published_at_utc
    WHERE partition_id = @partition_id
      AND execution_id = @execution_id
      AND released_at_utc IS NULL
      AND expires_at_utc > SYSUTCDATETIME();

    IF @@ROWCOUNT <> 1
        THROW 51440, N'O lease expirou ou deixou de estar ativo durante a publicação.', 1;

    COMMIT TRANSACTION;

    SELECT
        @execution_id AS execution_id,
        @candidate_rows AS candidate_rows,
        @inserted_rows AS inserted_rows,
        @updated_rows AS updated_rows,
        @reactivated_rows AS reactivated_rows,
        @noop_rows AS noop_rows,
        @stale_noop_rows AS stale_noop_rows,
        @reconciled_at_utc AS reconciled_at_utc,
        @published_at_utc AS published_at_utc,
        @incremental_frontier_before_utc AS incremental_frontier_before_utc,
        @incremental_frontier_after_utc AS incremental_frontier_after_utc;
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

    -- FRE-02/FRE-03: raw staging remains immutable; only effective freshness is preserved.
    DECLARE @terminal_omission TABLE (
        source_key NVARCHAR(256) COLLATE Latin1_General_100_BIN2 NOT NULL PRIMARY KEY
    );
    INSERT @terminal_omission SELECT source_key
    FROM core.ufn_frete_terminal_omission_candidates(@execution_id);

    -- Regra normal: antigo=no-op, igual+mesmo hash=replay,
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
          AND NOT EXISTS(SELECT 1 FROM @terminal_omission omission
                WHERE omission.source_key=candidate.source_key)
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
      -- A terminal omission accepts the status without replacing known CT-e/finalizations
      -- from a partial observation whose raw freshness did not advance.
      CASE WHEN EXISTS(SELECT 1 FROM @terminal_omission omission
                WHERE omission.source_key=candidate.source_key)
        OR (
          JSON_VALUE(typed_record.field_presence_json,N'$.cte')=N'ABSENT'
          AND JSON_VALUE(typed_record.field_presence_json,N'$.ctes')=N'ABSENT'
          AND JSON_VALUE(typed_record.field_presence_json,N'$.cte_key')=N'ABSENT'
          AND JSON_VALUE(typed_record.field_presence_json,N'$.finalizations')=N'ABSENT'
          AND JSON_VALUE(typed_record.field_presence_json,N'$.finalizacoes')=N'ABSENT')
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
        terminal=effective.terminal,
        freshness_evidence_json=CASE WHEN EXISTS(SELECT 1 FROM @terminal_omission omission
                WHERE omission.source_key=candidate.source_key)
          THEN JSON_MODIFY(current_record.freshness_evidence_json,N'$.terminalTransition',
            JSON_QUERY((SELECT N'FRE-03_ABSENT_CTE_PRESERVED' AS [policy],
              CONVERT(NVARCHAR(36),@execution_id) AS [executionId],
              typed_record.freshness_at_utc AS [observedFreshnessAtUtc],
              typed_record.freshness_origin AS [observedFreshnessOrigin]
              FOR JSON PATH,WITHOUT_ARRAY_WRAPPER)))
          ELSE typed_record.freshness_evidence_json END,
        freshness_at_utc=CASE WHEN EXISTS(SELECT 1 FROM @terminal_omission omission
                WHERE omission.source_key=candidate.source_key)
          THEN current_record.freshness_at_utc ELSE typed_record.freshness_at_utc END,
        freshness_origin=CASE WHEN EXISTS(SELECT 1 FROM @terminal_omission omission
                WHERE omission.source_key=candidate.source_key)
          THEN current_record.freshness_origin ELSE typed_record.freshness_origin END,
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
      AND (typed_record.freshness_at_utc>current_record.freshness_at_utc
           OR EXISTS(SELECT 1 FROM @terminal_omission omission
                WHERE omission.source_key=candidate.source_key));

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
    EXEC recon.usp_capture_runtime_vertical_output @execution_id;
    COMMIT TRANSACTION;
    SELECT execution_id,candidate_rows,inserted_rows,updated_rows,reactivated_rows,
           noop_rows,stale_noop_rows,reconciled_at_utc,published_at_utc,
           incremental_frontier_before_utc,incremental_frontier_after_utc
    FROM @common_result;
END;
GO
