-- Validador somente leitura do protocolo atômico de aplicação, reconciliação e publicação.
-- Requer V003 e V004 aplicadas no mesmo banco V2.

SET NOCOUNT ON;

DECLARE @failures TABLE (
    category NVARCHAR(40) NOT NULL,
    object_name NVARCHAR(256) NOT NULL,
    detail NVARCHAR(400) NOT NULL
);

DECLARE @expected_objects TABLE (
    schema_name SYSNAME COLLATE DATABASE_DEFAULT NOT NULL,
    object_name SYSNAME COLLATE DATABASE_DEFAULT NOT NULL,
    object_type CHAR(2) COLLATE DATABASE_DEFAULT NOT NULL,
    PRIMARY KEY (schema_name, object_name)
);

INSERT INTO @expected_objects (schema_name, object_name, object_type)
VALUES
    (N'ctl', N'execution_publication_event', N'U'),
    (N'recon', N'execution_candidate_application', N'U'),
    (N'recon', N'execution_reconciliation_result', N'U'),
    (N'core', N'usp_apply_reconcile_publish_execution', N'P');

INSERT INTO @failures (category, object_name, detail)
SELECT
    N'OBJECT',
    CONCAT(expected.schema_name, N'.', expected.object_name),
    N'Objeto obrigatório do protocolo atômico ausente ou com tipo divergente.'
FROM @expected_objects AS expected
LEFT JOIN sys.schemas AS schema_definition
    ON schema_definition.name COLLATE DATABASE_DEFAULT = expected.schema_name
LEFT JOIN sys.objects AS object_definition
    ON object_definition.schema_id = schema_definition.schema_id
   AND object_definition.name COLLATE DATABASE_DEFAULT = expected.object_name
   AND object_definition.type COLLATE DATABASE_DEFAULT = expected.object_type
WHERE object_definition.object_id IS NULL;

DECLARE @expected_constraints TABLE (
    schema_name SYSNAME COLLATE DATABASE_DEFAULT NOT NULL,
    table_name SYSNAME COLLATE DATABASE_DEFAULT NOT NULL,
    constraint_name SYSNAME COLLATE DATABASE_DEFAULT NOT NULL,
    object_type CHAR(2) COLLATE DATABASE_DEFAULT NOT NULL,
    PRIMARY KEY (schema_name, table_name, constraint_name)
);

INSERT INTO @expected_constraints (
    schema_name, table_name, constraint_name, object_type
)
VALUES
    (N'ctl', N'execution_publication_event',
        N'PK_ctl_execution_publication_event', N'PK'),
    (N'ctl', N'execution_publication_event',
        N'FK_ctl_execution_publication_event_execution', N'F'),
    (N'ctl', N'execution_publication_event',
        N'FK_ctl_execution_publication_event_partition', N'F'),
    (N'ctl', N'execution_publication_event',
        N'FK_ctl_execution_publication_event_previous_execution', N'F'),
    (N'ctl', N'execution_publication_event',
        N'FK_ctl_execution_publication_event_watermark_partition', N'F'),
    (N'ctl', N'execution_publication_event',
        N'CK_ctl_execution_publication_event_frontier', N'C'),
    (N'recon', N'execution_candidate_application',
        N'PK_recon_execution_candidate_application', N'PK'),
    (N'recon', N'execution_candidate_application',
        N'UQ_recon_execution_candidate_application_record', N'UQ'),
    (N'recon', N'execution_candidate_application',
        N'FK_recon_execution_candidate_application_record', N'F'),
    (N'recon', N'execution_candidate_application',
        N'CK_recon_execution_candidate_application_disposition', N'C'),
    (N'recon', N'execution_candidate_application',
        N'CK_recon_execution_candidate_application_fingerprints', N'C'),
    (N'recon', N'execution_reconciliation_result',
        N'PK_recon_execution_reconciliation_result', N'PK'),
    (N'recon', N'execution_reconciliation_result',
        N'FK_recon_execution_reconciliation_result_execution', N'F'),
    (N'recon', N'execution_reconciliation_result',
        N'FK_recon_execution_reconciliation_result_candidate_set', N'F'),
    (N'recon', N'execution_reconciliation_result',
        N'CK_recon_execution_reconciliation_result_counts', N'C'),
    (N'recon', N'execution_reconciliation_result',
        N'CK_recon_execution_reconciliation_result_times', N'C');

INSERT INTO @failures (category, object_name, detail)
SELECT
    N'CONSTRAINT',
    CONCAT(
        expected.schema_name, N'.', expected.table_name, N'.', expected.constraint_name
    ),
    N'Constraint obrigatória ausente, desabilitada ou não confiável.'
FROM @expected_constraints AS expected
WHERE NOT EXISTS (
    SELECT 1
    FROM sys.objects AS constraint_definition
    INNER JOIN sys.schemas AS schema_definition
        ON schema_definition.schema_id = constraint_definition.schema_id
    WHERE schema_definition.name COLLATE DATABASE_DEFAULT = expected.schema_name
      AND OBJECT_NAME(constraint_definition.parent_object_id) COLLATE DATABASE_DEFAULT
            = expected.table_name
      AND constraint_definition.name COLLATE DATABASE_DEFAULT = expected.constraint_name
      AND constraint_definition.type COLLATE DATABASE_DEFAULT = expected.object_type
      AND NOT EXISTS (
          SELECT 1
          FROM sys.check_constraints AS check_definition
          WHERE check_definition.object_id = constraint_definition.object_id
            AND (check_definition.is_disabled = 1 OR check_definition.is_not_trusted = 1)
      )
      AND NOT EXISTS (
          SELECT 1
          FROM sys.foreign_keys AS foreign_key_definition
          WHERE foreign_key_definition.object_id = constraint_definition.object_id
            AND (
                foreign_key_definition.is_disabled = 1
                OR foreign_key_definition.is_not_trusted = 1
            )
      )
);

DECLARE @required_columns TABLE (
    schema_name SYSNAME COLLATE DATABASE_DEFAULT NOT NULL,
    table_name SYSNAME COLLATE DATABASE_DEFAULT NOT NULL,
    column_name SYSNAME COLLATE DATABASE_DEFAULT NOT NULL,
    type_name SYSNAME COLLATE DATABASE_DEFAULT NOT NULL,
    is_nullable BIT NOT NULL,
    PRIMARY KEY (schema_name, table_name, column_name)
);

INSERT INTO @required_columns (
    schema_name, table_name, column_name, type_name, is_nullable
)
VALUES
    (N'ctl', N'execution_publication_event', N'execution_id', N'uniqueidentifier', 0),
    (N'ctl', N'execution_publication_event', N'partition_id', N'bigint', 0),
    (N'ctl', N'execution_publication_event',
        N'previous_published_execution_id', N'uniqueidentifier', 1),
    (N'ctl', N'execution_publication_event', N'published_at_utc', N'datetime2', 0),
    (N'ctl', N'execution_publication_event',
        N'incremental_frontier_before_utc', N'datetime2', 1),
    (N'ctl', N'execution_publication_event',
        N'incremental_frontier_after_utc', N'datetime2', 1),
    (N'ctl', N'execution_publication_event', N'watermark_last_partition_id', N'bigint', 1),
    (N'recon', N'execution_candidate_application', N'execution_id', N'uniqueidentifier', 0),
    (N'recon', N'execution_candidate_application', N'source_key', N'nvarchar', 0),
    (N'recon', N'execution_candidate_application', N'record_state_id', N'bigint', 0),
    (N'recon', N'execution_candidate_application',
        N'application_disposition', N'nvarchar', 0),
    (N'recon', N'execution_candidate_application', N'applied_at_utc', N'datetime2', 0),
    (N'recon', N'execution_reconciliation_result', N'execution_id', N'uniqueidentifier', 0),
    (N'recon', N'execution_reconciliation_result', N'candidate_rows', N'bigint', 0),
    (N'recon', N'execution_reconciliation_result', N'inserted_rows', N'bigint', 0),
    (N'recon', N'execution_reconciliation_result', N'updated_rows', N'bigint', 0),
    (N'recon', N'execution_reconciliation_result', N'reactivated_rows', N'bigint', 0),
    (N'recon', N'execution_reconciliation_result', N'noop_rows', N'bigint', 0),
    (N'recon', N'execution_reconciliation_result', N'stale_noop_rows', N'bigint', 0),
    (N'recon', N'execution_reconciliation_result', N'reconciled_at_utc', N'datetime2', 0),
    (N'recon', N'execution_reconciliation_result', N'published_at_utc', N'datetime2', 0),
    (N'core', N'entity_record_state', N'environment_name', N'nvarchar', 0);

INSERT INTO @failures (category, object_name, detail)
SELECT
    N'COLUMN',
    CONCAT(expected.schema_name, N'.', expected.table_name, N'.', expected.column_name),
    N'Coluna obrigatória ausente ou com tipo/nullability divergente.'
FROM @required_columns AS expected
WHERE NOT EXISTS (
    SELECT 1
    FROM sys.columns AS column_definition
    INNER JOIN sys.types AS type_definition
        ON type_definition.user_type_id = column_definition.user_type_id
    WHERE column_definition.object_id = OBJECT_ID(
              CONCAT(expected.schema_name, N'.', expected.table_name), N'U'
          )
      AND column_definition.name COLLATE DATABASE_DEFAULT = expected.column_name
      AND type_definition.name COLLATE DATABASE_DEFAULT = expected.type_name
      AND column_definition.is_nullable = expected.is_nullable
);

INSERT INTO @failures (category, object_name, detail)
SELECT
    N'COLLATION',
    CONCAT(schema_definition.name, N'.', table_definition.name, N'.', column_definition.name),
    N'Identidade textual ou disposition persistida deve usar BIN2 explícita.'
FROM sys.tables AS table_definition
INNER JOIN sys.schemas AS schema_definition
    ON schema_definition.schema_id = table_definition.schema_id
INNER JOIN sys.columns AS column_definition
    ON column_definition.object_id = table_definition.object_id
INNER JOIN sys.types AS type_definition
    ON type_definition.user_type_id = column_definition.user_type_id
WHERE (
        (schema_definition.name = N'core' AND table_definition.name = N'entity_record_state')
        OR (
            schema_definition.name = N'recon'
            AND table_definition.name = N'execution_candidate_application'
        )
      )
  AND type_definition.name = N'nvarchar'
  AND column_definition.collation_name <> N'Latin1_General_100_BIN2';

DECLARE @expected_core_key TABLE (
    key_ordinal INT NOT NULL PRIMARY KEY,
    column_name SYSNAME COLLATE DATABASE_DEFAULT NOT NULL
);

INSERT INTO @expected_core_key (key_ordinal, column_name)
VALUES
    (1, N'environment_name'),
    (2, N'source_instance'),
    (3, N'tenant_scope'),
    (4, N'entity_name'),
    (5, N'source_key');

IF EXISTS (
    SELECT expected.key_ordinal, expected.column_name
    FROM @expected_core_key AS expected
    EXCEPT
    SELECT index_column.key_ordinal, column_definition.name
    FROM sys.indexes AS index_definition
    INNER JOIN sys.index_columns AS index_column
        ON index_column.object_id = index_definition.object_id
       AND index_column.index_id = index_definition.index_id
    INNER JOIN sys.columns AS column_definition
        ON column_definition.object_id = index_column.object_id
       AND column_definition.column_id = index_column.column_id
    WHERE index_definition.object_id = OBJECT_ID(N'core.entity_record_state', N'U')
      AND index_definition.name = N'UQ_core_entity_record_state_source'
      AND index_column.key_ordinal > 0
)
OR EXISTS (
    SELECT index_column.key_ordinal, column_definition.name
    FROM sys.indexes AS index_definition
    INNER JOIN sys.index_columns AS index_column
        ON index_column.object_id = index_definition.object_id
       AND index_column.index_id = index_definition.index_id
    INNER JOIN sys.columns AS column_definition
        ON column_definition.object_id = index_column.object_id
       AND column_definition.column_id = index_column.column_id
    WHERE index_definition.object_id = OBJECT_ID(N'core.entity_record_state', N'U')
      AND index_definition.name = N'UQ_core_entity_record_state_source'
      AND index_column.key_ordinal > 0
    EXCEPT
    SELECT expected.key_ordinal, expected.column_name
    FROM @expected_core_key AS expected
)
    INSERT INTO @failures (category, object_name, detail)
    VALUES (
        N'KEY_GRAIN', N'core.UQ_core_entity_record_state_source',
        N'A identidade de core deve começar pelo ambiente e fechar no source_key.'
    );

DECLARE @application_disposition_definition NVARCHAR(MAX) = (
    SELECT definition
    FROM sys.check_constraints
    WHERE parent_object_id = OBJECT_ID(N'recon.execution_candidate_application', N'U')
      AND name = N'CK_recon_execution_candidate_application_disposition'
);

IF @application_disposition_definition IS NULL
   OR @application_disposition_definition NOT LIKE N'%N''INSERTED''%'
   OR @application_disposition_definition NOT LIKE N'%N''UPDATED''%'
   OR @application_disposition_definition NOT LIKE N'%N''REACTIVATED''%'
   OR @application_disposition_definition NOT LIKE N'%N''NO_OP''%'
   OR @application_disposition_definition NOT LIKE N'%N''STALE_NO_OP''%'
    INSERT INTO @failures (category, object_name, detail)
    VALUES (
        N'CONSTRAINT', N'CK_recon_execution_candidate_application_disposition',
        N'As cinco disposições exclusivas de aplicação não estão fechadas pela constraint.'
    );

DECLARE @reconciliation_count_definition NVARCHAR(MAX) = (
    SELECT definition
    FROM sys.check_constraints
    WHERE parent_object_id = OBJECT_ID(N'recon.execution_reconciliation_result', N'U')
      AND name = N'CK_recon_execution_reconciliation_result_counts'
);

IF @reconciliation_count_definition IS NULL
   OR @reconciliation_count_definition
        NOT LIKE N'%stale_noop_rows%<=%noop_rows%'
   OR @reconciliation_count_definition
        NOT LIKE N'%candidate_rows%=%inserted_rows%+%updated_rows%+%reactivated_rows%+%noop_rows%'
    INSERT INTO @failures (category, object_name, detail)
    VALUES (
        N'CONSTRAINT', N'CK_recon_execution_reconciliation_result_counts',
        N'A equação candidate=insert+update+reactivation+no-op não está fechada.'
    );

DECLARE @publication_frontier_definition NVARCHAR(MAX) = (
    SELECT definition
    FROM sys.check_constraints
    WHERE parent_object_id = OBJECT_ID(N'ctl.execution_publication_event', N'U')
      AND name = N'CK_ctl_execution_publication_event_frontier'
);

IF @publication_frontier_definition IS NULL
   OR @publication_frontier_definition
        NOT LIKE N'%incremental_frontier_after_utc%>=%incremental_frontier_before_utc%'
    INSERT INTO @failures (category, object_name, detail)
    VALUES (
        N'CONSTRAINT', N'CK_ctl_execution_publication_event_frontier',
        N'A evidência de publicação permite regressão da fronteira incremental.'
    );

INSERT INTO @failures (category, object_name, detail)
SELECT
    N'PARAMETER', N'core.usp_apply_reconcile_publish_execution',
    N'A procedure final deve aceitar execution_id e o permit de contrato/configuração.'
WHERE (
        SELECT COUNT_BIG(*)
        FROM sys.parameters
        WHERE object_id = OBJECT_ID(N'core.usp_apply_reconcile_publish_execution', N'P')
      ) <> 5;

DECLARE @expected_apply_parameters TABLE (
    parameter_id INT NOT NULL PRIMARY KEY,
    parameter_name SYSNAME NOT NULL,
    type_name SYSNAME NOT NULL,
    max_length SMALLINT NOT NULL
);
INSERT INTO @expected_apply_parameters (parameter_id, parameter_name, type_name, max_length)
VALUES
    (1, N'@execution_id', N'uniqueidentifier', 16),
    (2, N'@contract_version', N'nvarchar', -1),
    (3, N'@contract_fingerprint', N'nvarchar', -1),
    (4, N'@configuration_version', N'nvarchar', -1),
    (5, N'@configuration_fingerprint', N'nvarchar', -1);

INSERT INTO @failures (category, object_name, detail)
SELECT
    N'PARAMETER',
    CONCAT(N'core.usp_apply_reconcile_publish_execution.', expected.parameter_name),
    N'Nome, ordem, tipo ou largura do permit da publicação diverge.'
FROM @expected_apply_parameters AS expected
WHERE NOT EXISTS (
    SELECT 1
    FROM sys.parameters AS parameter_definition
    INNER JOIN sys.types AS type_definition
        ON type_definition.user_type_id = parameter_definition.user_type_id
    WHERE parameter_definition.object_id = OBJECT_ID(
              N'core.usp_apply_reconcile_publish_execution', N'P'
          )
      AND parameter_definition.parameter_id = expected.parameter_id
      AND parameter_definition.name = expected.parameter_name
      AND type_definition.name = expected.type_name
      AND parameter_definition.max_length = expected.max_length
      AND parameter_definition.is_output = 0
);

DECLARE @expected_result TABLE (
    column_ordinal INT NOT NULL PRIMARY KEY,
    column_name SYSNAME COLLATE DATABASE_DEFAULT NOT NULL,
    system_type_name NVARCHAR(256) COLLATE DATABASE_DEFAULT NOT NULL
);

INSERT INTO @expected_result (column_ordinal, column_name, system_type_name)
VALUES
    (1, N'execution_id', N'uniqueidentifier'),
    (2, N'candidate_rows', N'bigint'),
    (3, N'inserted_rows', N'bigint'),
    (4, N'updated_rows', N'bigint'),
    (5, N'reactivated_rows', N'bigint'),
    (6, N'noop_rows', N'bigint'),
    (7, N'stale_noop_rows', N'bigint'),
    (8, N'reconciled_at_utc', N'datetime2(3)'),
    (9, N'published_at_utc', N'datetime2(3)'),
    (10, N'incremental_frontier_before_utc', N'datetime2(3)'),
    (11, N'incremental_frontier_after_utc', N'datetime2(3)');

DECLARE @actual_result TABLE (
    column_ordinal INT NULL,
    column_name SYSNAME COLLATE DATABASE_DEFAULT NULL,
    system_type_name NVARCHAR(256) COLLATE DATABASE_DEFAULT NULL
);

IF OBJECT_ID(N'core.usp_apply_reconcile_publish_execution', N'P') IS NOT NULL
BEGIN
    INSERT INTO @actual_result (column_ordinal, column_name, system_type_name)
    SELECT column_ordinal, name, system_type_name
    FROM sys.dm_exec_describe_first_result_set_for_object(
        OBJECT_ID(N'core.usp_apply_reconcile_publish_execution', N'P'), 0
    )
    WHERE is_hidden = 0;
END;

INSERT INTO @failures (category, object_name, detail)
SELECT
    N'RESULT_SET',
    CONCAT(N'core.usp_apply_reconcile_publish_execution#', expected.column_ordinal),
    N'Nome, ordem ou tipo da coluna agregada de retorno diverge do contrato JDBC.'
FROM @expected_result AS expected
LEFT JOIN @actual_result AS actual
    ON actual.column_ordinal = expected.column_ordinal
WHERE EXISTS (
        SELECT 1 FROM @actual_result WHERE column_ordinal IS NOT NULL
      )
  AND (
        actual.column_ordinal IS NULL
        OR actual.column_name <> expected.column_name
        OR actual.system_type_name <> expected.system_type_name
      );

INSERT INTO @failures (category, object_name, detail)
SELECT
    N'RESULT_SET', N'core.usp_apply_reconcile_publish_execution',
    N'O retorno contém coluna extra além do contrato agregado O(1).'
WHERE EXISTS (
    SELECT 1 FROM @actual_result WHERE column_ordinal > 11
);

DECLARE @procedure_definition NVARCHAR(MAX) = OBJECT_DEFINITION(
    OBJECT_ID(N'core.usp_apply_reconcile_publish_execution', N'P')
);

DECLARE @required_fragments TABLE (
    category NVARCHAR(40) NOT NULL,
    fragment NVARCHAR(400) NOT NULL,
    detail NVARCHAR(400) NOT NULL,
    PRIMARY KEY (category, fragment)
);

INSERT INTO @required_fragments (category, fragment, detail)
VALUES
    (N'TRANSACTION', N'SET XACT_ABORT ON',
        N'XACT_ABORT deve proteger a unidade recuperável.'),
    (N'TRANSACTION', N'BEGIN TRANSACTION',
        N'A aplicação positiva deve abrir uma única unidade transacional.'),
    (N'TRANSACTION', N'COMMIT TRANSACTION',
        N'A unidade positiva não possui commit explícito.'),
    (N'CLOCK_AUTHORITY', N'SYSUTCDATETIME()',
        N'O relógio técnico deve pertencer ao SQL Server.'),
    (N'CLOCK_AUTHORITY', N'expires_at_utc > SYSUTCDATETIME()',
        N'A lease precisa ser revalidada no último write antes do commit.'),
    (N'CONCURRENCY', N'sys.sp_getapplock',
        N'O namespace não é serializado por application lock.'),
    (N'CONCURRENCY', N'@LockMode = ''Exclusive''',
        N'O application lock precisa ser exclusivo.'),
    (N'CONCURRENCY', N'@LockOwner = ''Transaction''',
        N'O application lock deve ser liberado junto da transação.'),
    (N'CONCURRENCY', N'WITH (UPDLOCK, HOLDLOCK)',
        N'As decisões persistidas não estão protegidas até o commit.'),
    (N'IDEMPOTENCY',
        N'IF @execution_state COLLATE Latin1_General_100_BIN2 = N''PUBLISHED''',
        N'Retry PUBLISHED não possui fast path evidence-bound.'),
    (N'IDEMPOTENCY', N'Execução publicada sem evidência imutável coerente.',
        N'Retry PUBLISHED não falha fechado diante de evidência parcial.'),
    (N'RESULT_SET', N'@execution_id AS execution_id',
        N'O retorno agregado não inicia por execution_id.'),
    (N'RESULT_SET', N'@candidate_rows AS candidate_rows',
        N'O retorno agregado não expõe candidate_rows.'),
    (N'RESULT_SET', N'@inserted_rows AS inserted_rows',
        N'O retorno agregado não expõe inserted_rows.'),
    (N'RESULT_SET', N'@updated_rows AS updated_rows',
        N'O retorno agregado não expõe updated_rows.'),
    (N'RESULT_SET', N'@reactivated_rows AS reactivated_rows',
        N'O retorno agregado não expõe reactivated_rows.'),
    (N'RESULT_SET', N'@noop_rows AS noop_rows',
        N'O retorno agregado não expõe noop_rows.'),
    (N'RESULT_SET', N'@stale_noop_rows AS stale_noop_rows',
        N'O retorno agregado não expõe stale_noop_rows.'),
    (N'RESULT_SET', N'@reconciled_at_utc AS reconciled_at_utc',
        N'O retorno agregado não expõe reconciled_at_utc.'),
    (N'RESULT_SET', N'@published_at_utc AS published_at_utc',
        N'O retorno agregado não expõe published_at_utc.'),
    (N'RESULT_SET',
        N'@incremental_frontier_before_utc AS incremental_frontier_before_utc',
        N'O retorno agregado não expõe a fronteira anterior.'),
    (N'RESULT_SET',
        N'@incremental_frontier_after_utc AS incremental_frontier_after_utc',
        N'O retorno agregado não expõe a fronteira posterior.'),
    (N'APPLY', N'INSERT INTO core.entity_record_state',
        N'Casos INSERTED não são aplicados ao core.'),
    (N'APPLY', N'UPDATE current_record',
        N'Casos UPDATE/REACTIVATION não são aplicados ao core.'),
    (N'APPLY', N'N''INSERTED''', N'Disposição INSERTED ausente.'),
    (N'APPLY', N'N''UPDATED''', N'Disposição UPDATED ausente.'),
    (N'APPLY', N'N''REACTIVATED''', N'Disposição REACTIVATED ausente.'),
    (N'APPLY', N'N''NO_OP''', N'Disposição NO_OP ausente.'),
    (N'APPLY', N'N''STALE_NO_OP''', N'Disposição STALE_NO_OP ausente.'),
    (N'RECONCILIATION', N'INSERT INTO recon.execution_candidate_application',
        N'A evidência por candidato não é persistida.'),
    (N'RECONCILIATION', N'INSERT INTO recon.execution_reconciliation_result',
        N'O fechamento agregado da reconciliação não é persistido.'),
    (N'RECONCILIATION',
        N'@candidate_rows <> @inserted_rows + @updated_rows + @reactivated_rows + @noop_rows',
        N'A equação completa não é verificada antes da publicação.'),
    (N'STATE', N'N''PROMOTED'', N''RECONCILED''',
        N'A transição dedicada para RECONCILED está ausente.'),
    (N'STATE', N'N''RECONCILED'', N''PUBLISHED''',
        N'A transição dedicada para PUBLISHED está ausente.'),
    (N'PUBLICATION', N'INSERT INTO ctl.partition_publication_pointer',
        N'O pointer da partição não participa do commit.'),
    (N'PUBLICATION', N'INSERT INTO ctl.execution_publication_event',
        N'A evidência imutável de publicação não participa do commit.'),
    (N'PUBLICATION', N'ctl.incremental_publication_watermark',
        N'A fronteira incremental não participa do protocolo.'),
    (N'PUBLICATION', N'SET released_at_utc = @published_at_utc',
        N'O lease não é liberado pelo mesmo commit positivo.'),
    (N'QUARANTINE', N'result.quarantined_root_keys = 0',
        N'Candidate set com raiz em quarantine não é recusado.'),
    (N'QUARANTINE', N'result.unidentified_quarantine_rows = 0',
        N'Quarantine sem chave não é recusado pelo protocolo positivo.'),
    (N'QUARANTINE', N'result.quarantined_stage_rows = 0',
        N'Artefatos físicos de quarantine não são recusados.'),
    (N'FRONTIER', N'OPTION (MAXRECURSION 32767)',
        N'O fechamento contíguo não percorre publicações já concluídas.'),
    (N'FRONTIER', N'next_partition.execution_mode = N''INCREMENTAL''',
        N'O watermark não está isolado ao modo incremental.');

INSERT INTO @failures (category, object_name, detail)
SELECT
    required.category,
    N'core.usp_apply_reconcile_publish_execution',
    required.detail
FROM @required_fragments AS required
WHERE @procedure_definition IS NULL
   OR @procedure_definition NOT LIKE N'%' + required.fragment + N'%';

IF @procedure_definition LIKE N'%MERGE%'
    INSERT INTO @failures (category, object_name, detail)
    VALUES (
        N'APPLY', N'core.usp_apply_reconcile_publish_execution',
        N'MERGE não é aceito no kernel concorrente; use INSERT/UPDATE set-based explícitos.'
    );

IF @procedure_definition LIKE N'%EXEC ctl.usp_control_plane_publish_execution%'
    INSERT INTO @failures (category, object_name, detail)
    VALUES (
        N'PUBLICATION', N'core.usp_apply_reconcile_publish_execution',
        N'O stub fail-closed de ctl não pode substituir o protocolo evidence-bound.'
    );

IF CHARINDEX(
       N'THROW 51418',
       @procedure_definition
   ) = 0
   OR CHARINDEX(
          N'IF @persisted_contract_version IS NULL',
          @procedure_definition
      ) = 0
   OR CHARINDEX(
          N'EXEC @application_lock_result = sys.sp_getapplock',
          @procedure_definition
      ) = 0
   OR CHARINDEX(
          N'FROM ctl.execution_attempt AS attempt WITH (UPDLOCK, HOLDLOCK)',
          @procedure_definition
      ) = 0
   OR CHARINDEX(
          N'@persisted_contract_version COLLATE Latin1_General_100_BIN2',
          @procedure_definition
      ) = 0
   OR CHARINDEX(
          N'@persisted_configuration_fingerprint COLLATE Latin1_General_100_BIN2',
          @procedure_definition
      ) = 0
   OR CHARINDEX(N'DATALENGTH(@contract_version)', @procedure_definition) = 0
   OR CHARINDEX(N'SET @contract_version = LTRIM(RTRIM(@contract_version))', @procedure_definition) = 0
   OR CHARINDEX(N'DATALENGTH(@contract_version)', @procedure_definition) >=
      CHARINDEX(N'SET @contract_version = LTRIM(RTRIM(@contract_version))', @procedure_definition)
   OR CHARINDEX(
          N'EXEC @application_lock_result = sys.sp_getapplock',
          @procedure_definition
      ) >= CHARINDEX(
          N'FROM ctl.execution_attempt AS attempt WITH (UPDLOCK, HOLDLOCK)',
          @procedure_definition
      )
   OR CHARINDEX(
          N'FROM ctl.execution_attempt AS attempt WITH (UPDLOCK, HOLDLOCK)',
          @procedure_definition
      ) >= CHARINDEX(N'IF @persisted_contract_version IS NULL', @procedure_definition)
   OR CHARINDEX(N'IF @persisted_contract_version IS NULL', @procedure_definition)
      >= CHARINDEX(N'THROW 51418', @procedure_definition)
   OR CHARINDEX(
          N'THROW 51418',
          @procedure_definition
      ) >= CHARINDEX(
          N'IF @execution_state COLLATE Latin1_General_100_BIN2 = N''PUBLISHED''',
          @procedure_definition
      )
    INSERT INTO @failures (category, object_name, detail)
    VALUES (
        N'CONTRACT_PERMIT', N'core.usp_apply_reconcile_publish_execution',
        N'O permit deve ser validado sob lock antes do retry PUBLISHED e de qualquer mutação.'
    );

IF CHARINDEX(
       N'IF @execution_state COLLATE Latin1_General_100_BIN2 = N''PUBLISHED''',
       @procedure_definition
   ) = 0
   OR CHARINDEX(N'DECLARE @lease_expires_at_utc', @procedure_definition) = 0
   OR CHARINDEX(
          N'IF @execution_state COLLATE Latin1_General_100_BIN2 = N''PUBLISHED''',
          @procedure_definition
      ) >= CHARINDEX(N'DECLARE @lease_expires_at_utc', @procedure_definition)
    INSERT INTO @failures (category, object_name, detail)
    VALUES (
        N'IDEMPOTENCY', N'core.usp_apply_reconcile_publish_execution',
        N'O retry exato PUBLISHED precisa retornar da evidência antes de validar a lease.'
    );

DECLARE @expected_permissions TABLE (
    permission_class TINYINT NOT NULL,
    schema_name SYSNAME COLLATE DATABASE_DEFAULT NOT NULL,
    object_name SYSNAME COLLATE DATABASE_DEFAULT NOT NULL,
    permission_state CHAR(1) COLLATE DATABASE_DEFAULT NOT NULL,
    permission_name NVARCHAR(60) COLLATE DATABASE_DEFAULT NOT NULL,
    PRIMARY KEY (permission_class, schema_name, object_name, permission_name)
);

INSERT INTO @expected_permissions (
    permission_class, schema_name, object_name, permission_state, permission_name
)
VALUES
    (1, N'core', N'usp_prepare_staged_execution', N'G', N'EXECUTE'),
    (1, N'core', N'usp_apply_reconcile_publish_execution', N'G', N'EXECUTE'),
    (3, N'ctl', N'*', N'D', N'SELECT'),
    (3, N'ctl', N'*', N'D', N'INSERT'),
    (3, N'ctl', N'*', N'D', N'UPDATE'),
    (3, N'ctl', N'*', N'D', N'DELETE'),
    (3, N'stg', N'*', N'D', N'SELECT'),
    (3, N'stg', N'*', N'D', N'INSERT'),
    (3, N'stg', N'*', N'D', N'UPDATE'),
    (3, N'stg', N'*', N'D', N'DELETE'),
    (3, N'core', N'*', N'D', N'SELECT'),
    (3, N'core', N'*', N'D', N'INSERT'),
    (3, N'core', N'*', N'D', N'UPDATE'),
    (3, N'core', N'*', N'D', N'DELETE'),
    (3, N'recon', N'*', N'D', N'SELECT'),
    (3, N'recon', N'*', N'D', N'INSERT'),
    (3, N'recon', N'*', N'D', N'UPDATE'),
    (3, N'recon', N'*', N'D', N'DELETE');

INSERT INTO @failures (category, object_name, detail)
SELECT
    N'PERMISSION',
    CONCAT(expected.schema_name, N'.', expected.object_name, N':', expected.permission_name),
    N'Permissão mínima do runtime ausente.'
FROM @expected_permissions AS expected
WHERE NOT EXISTS (
    SELECT 1
    FROM sys.database_permissions AS permission_definition
    INNER JOIN sys.database_principals AS principal_definition
        ON principal_definition.principal_id = permission_definition.grantee_principal_id
    WHERE principal_definition.name COLLATE DATABASE_DEFAULT = N'v2_runtime'
      AND permission_definition.class = expected.permission_class
      AND permission_definition.state COLLATE DATABASE_DEFAULT = expected.permission_state
      AND permission_definition.permission_name COLLATE DATABASE_DEFAULT
            = expected.permission_name
      AND (
          (
              expected.permission_class = 3
              AND permission_definition.major_id = SCHEMA_ID(expected.schema_name)
          )
          OR (
              expected.permission_class = 1
              AND permission_definition.major_id = OBJECT_ID(
                  CONCAT(expected.schema_name, N'.', expected.object_name), N'P'
              )
          )
      )
);

INSERT INTO @failures (category, object_name, detail)
SELECT
    N'PERMISSION',
    CONCAT(schema_definition.name, N'.', object_definition.name),
    N'O runtime recebeu DML/SELECT direto em tabela protegida.'
FROM sys.database_permissions AS permission_definition
INNER JOIN sys.database_principals AS principal_definition
    ON principal_definition.principal_id = permission_definition.grantee_principal_id
INNER JOIN sys.objects AS object_definition
    ON object_definition.object_id = permission_definition.major_id
INNER JOIN sys.schemas AS schema_definition
    ON schema_definition.schema_id = object_definition.schema_id
WHERE principal_definition.name = N'v2_runtime'
  AND permission_definition.class = 1
  AND permission_definition.state IN (N'G', N'W')
  AND permission_definition.permission_name IN (N'SELECT', N'INSERT', N'UPDATE', N'DELETE')
  AND object_definition.type = N'U'
  AND schema_definition.name IN (N'ctl', N'stg', N'core', N'recon');

INSERT INTO @failures (category, object_name, detail)
SELECT
    N'PERMISSION', N'ctl.usp_control_plane_publish_execution',
    N'O runtime não pode receber o bypass fail-closed de publicação.'
WHERE EXISTS (
    SELECT 1
    FROM sys.database_permissions AS permission_definition
    INNER JOIN sys.database_principals AS principal_definition
        ON principal_definition.principal_id = permission_definition.grantee_principal_id
    WHERE principal_definition.name = N'v2_runtime'
      AND permission_definition.class = 1
      AND permission_definition.state IN (N'G', N'W')
      AND permission_definition.permission_name = N'EXECUTE'
      AND permission_definition.major_id = OBJECT_ID(
          N'ctl.usp_control_plane_publish_execution', N'P'
      )
);

IF EXISTS (SELECT 1 FROM @failures)
BEGIN
    SELECT category, object_name, detail
    FROM @failures
    ORDER BY category, object_name, detail;
    THROW 51460, N'Protocolo atômico de publicação V2 divergente do contrato.', 1;
END;

PRINT N'Protocolo atômico de aplicação, reconciliação e publicação validado com sucesso.';
