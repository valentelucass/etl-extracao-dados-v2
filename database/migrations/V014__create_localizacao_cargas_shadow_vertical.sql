-- Localização de Cargas 8656 em shadow base (V2-028). Sem fonte, relação, pub ou sweep.
-- Os limites físicos desta migration são locais e não ampliam o contrato transitório 8656.
SET XACT_ABORT ON;
SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;

CREATE TABLE stg.localizacao_carga_record (
    stage_record_id BIGINT NOT NULL,
    execution_id UNIQUEIDENTIFIER NOT NULL,
    source_key NVARCHAR(256) COLLATE Latin1_General_100_BIN2 NULL,
    source_key_wire_type NVARCHAR(16) COLLATE Latin1_General_100_BIN2 NULL,
    payload_json NVARCHAR(MAX) NOT NULL,
    field_presence_json NVARCHAR(MAX) NOT NULL,
    validation_disposition NVARCHAR(16) COLLATE Latin1_General_100_BIN2 NOT NULL,
    quarantine_reason_code NVARCHAR(64) COLLATE Latin1_General_100_BIN2 NULL,
    service_at_raw NVARCHAR(MAX) NULL,
    service_at_utc DATETIME2(3) NULL,
    service_at_presence NVARCHAR(8) COLLATE Latin1_General_100_BIN2 NULL,
    service_at_parse_state NVARCHAR(16) COLLATE Latin1_General_100_BIN2 NULL,
    invoices_volumes_raw NVARCHAR(MAX) NULL,
    invoices_volumes_typed INT NULL,
    invoices_volumes_presence NVARCHAR(8) COLLATE Latin1_General_100_BIN2 NULL,
    invoices_volumes_parse_state NVARCHAR(16) COLLATE Latin1_General_100_BIN2 NULL,
    taxed_weight_raw NVARCHAR(MAX) NULL,
    taxed_weight_typed DECIMAL(38,9) NULL,
    taxed_weight_presence NVARCHAR(8) COLLATE Latin1_General_100_BIN2 NULL,
    taxed_weight_parse_state NVARCHAR(16) COLLATE Latin1_General_100_BIN2 NULL,
    invoices_value_raw NVARCHAR(MAX) NULL,
    invoices_value_typed DECIMAL(38,9) NULL,
    invoices_value_presence NVARCHAR(8) COLLATE Latin1_General_100_BIN2 NULL,
    invoices_value_parse_state NVARCHAR(16) COLLATE Latin1_General_100_BIN2 NULL,
    total_raw NVARCHAR(MAX) NULL,
    total_typed DECIMAL(38,9) NULL,
    total_presence NVARCHAR(8) COLLATE Latin1_General_100_BIN2 NULL,
    total_parse_state NVARCHAR(16) COLLATE Latin1_General_100_BIN2 NULL,
    status_raw NVARCHAR(MAX) NULL,
    status_normalized NVARCHAR(MAX) COLLATE Latin1_General_100_BIN2 NULL,
    status_presence NVARCHAR(8) COLLATE Latin1_General_100_BIN2 NULL,
    status_catalog_version NVARCHAR(64) COLLATE Latin1_General_100_BIN2 NULL,
    status_terminal BIT NULL,
    status_branch_nickname NVARCHAR(MAX) NULL,
    status_branch_nickname_presence NVARCHAR(8) COLLATE Latin1_General_100_BIN2 NULL,
    status_branch_nickname_provenance NVARCHAR(64) COLLATE Latin1_General_100_BIN2 NULL,
    freight_candidate_presence NVARCHAR(8) COLLATE Latin1_General_100_BIN2 NULL,
    freight_candidate_provenance NVARCHAR(96) COLLATE Latin1_General_100_BIN2 NULL,
    freight_candidate_state NVARCHAR(96) COLLATE Latin1_General_100_BIN2 NULL,
    freshness_at_utc DATETIME2(3) NULL,
    freshness_origin NVARCHAR(32) COLLATE Latin1_General_100_BIN2 NULL,
    partition_basis NVARCHAR(64) COLLATE Latin1_General_100_BIN2 NULL,
    overlap_policy_version NVARCHAR(64) COLLATE Latin1_General_100_BIN2 NULL,
    attribute_hash CHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL,
    observed_at_utc DATETIME2(3) NOT NULL,
    CONSTRAINT PK_stg_localizacao_carga_record PRIMARY KEY CLUSTERED(stage_record_id),
    CONSTRAINT FK_stg_localizacao_carga_record_stage
        FOREIGN KEY(stage_record_id) REFERENCES stg.execution_record(stage_record_id),
    CONSTRAINT UQ_stg_localizacao_carga_execution_stage UNIQUE(execution_id,stage_record_id),
    CONSTRAINT CK_stg_localizacao_carga_disposition CHECK(
        (validation_disposition=N'VALID' AND quarantine_reason_code IS NULL)
        OR (validation_disposition=N'QUARANTINE'
            AND quarantine_reason_code IS NOT NULL
            AND LEN(quarantine_reason_code) BETWEEN 2 AND 64
            AND LEFT(quarantine_reason_code,1) COLLATE Latin1_General_100_BIN2 LIKE N'[A-Z]'
            AND quarantine_reason_code COLLATE Latin1_General_100_BIN2
                NOT LIKE N'%[^A-Z0-9_]%')
    ),
    CONSTRAINT CK_stg_localizacao_carga_identity CHECK(
        (validation_disposition=N'QUARANTINE' AND source_key IS NULL
            AND source_key_wire_type IS NULL)
        OR (source_key_wire_type=N'INTEGER'
            AND LEFT(source_key,8) COLLATE Latin1_General_100_BIN2=N'INTEGER:'
            AND (
                SUBSTRING(source_key,9,248) COLLATE Latin1_General_100_BIN2=N'0'
                OR (SUBSTRING(source_key,9,248) COLLATE Latin1_General_100_BIN2 LIKE N'[1-9]%'
                    AND SUBSTRING(source_key,9,248) COLLATE Latin1_General_100_BIN2 NOT LIKE N'%[^0-9]%')
                OR (SUBSTRING(source_key,9,248) COLLATE Latin1_General_100_BIN2 LIKE N'-[1-9]%'
                    AND SUBSTRING(source_key,10,247) COLLATE Latin1_General_100_BIN2 NOT LIKE N'%[^0-9]%')
            ))
    ),
    CONSTRAINT CK_stg_localizacao_carga_json CHECK(
        ISJSON(payload_json)=1 AND ISJSON(field_presence_json)=1
    ),
    CONSTRAINT CK_stg_localizacao_carga_temporal CHECK(
        validation_disposition=N'QUARANTINE'
        OR (service_at_presence=N'VALUE' AND service_at_parse_state=N'VALID'
            AND service_at_raw IS NOT NULL AND service_at_utc=freshness_at_utc
            AND freshness_origin=N'SERVICE_AT'
            AND partition_basis=N'freights.service_at'
            AND overlap_policy_version=N'localizacao-cargas-service-at-overlap-v1')
    ),
    CONSTRAINT CK_stg_localizacao_carga_numeric CHECK(
        validation_disposition=N'QUARANTINE'
        OR (invoices_volumes_presence IN(N'ABSENT',N'NULL',N'VALUE')
        AND taxed_weight_presence IN(N'ABSENT',N'NULL',N'VALUE')
        AND invoices_value_presence IN(N'ABSENT',N'NULL',N'VALUE')
        AND total_presence IN(N'ABSENT',N'NULL',N'VALUE')
        AND ((invoices_volumes_presence=N'ABSENT' AND invoices_volumes_parse_state=N'NOT_PRESENT'
              AND invoices_volumes_raw IS NULL AND invoices_volumes_typed IS NULL)
          OR (invoices_volumes_presence=N'NULL' AND invoices_volumes_parse_state=N'EXPLICIT_NULL'
              AND invoices_volumes_raw IS NULL AND invoices_volumes_typed IS NULL)
          OR (invoices_volumes_presence=N'VALUE' AND invoices_volumes_parse_state=N'VALID'
              AND invoices_volumes_raw IS NOT NULL AND invoices_volumes_typed>=0))
        AND ((taxed_weight_presence=N'ABSENT' AND taxed_weight_parse_state=N'NOT_PRESENT'
              AND taxed_weight_raw IS NULL AND taxed_weight_typed IS NULL)
          OR (taxed_weight_presence=N'NULL' AND taxed_weight_parse_state=N'EXPLICIT_NULL'
              AND taxed_weight_raw IS NULL AND taxed_weight_typed IS NULL)
          OR (taxed_weight_presence=N'VALUE' AND taxed_weight_parse_state=N'VALID'
              AND taxed_weight_raw IS NOT NULL AND taxed_weight_typed IS NOT NULL))
        AND ((invoices_value_presence=N'ABSENT' AND invoices_value_parse_state=N'NOT_PRESENT'
              AND invoices_value_raw IS NULL AND invoices_value_typed IS NULL)
          OR (invoices_value_presence=N'NULL' AND invoices_value_parse_state=N'EXPLICIT_NULL'
              AND invoices_value_raw IS NULL AND invoices_value_typed IS NULL)
          OR (invoices_value_presence=N'VALUE' AND invoices_value_parse_state=N'VALID'
              AND invoices_value_raw IS NOT NULL AND invoices_value_typed IS NOT NULL))
        AND ((total_presence=N'ABSENT' AND total_parse_state=N'NOT_PRESENT'
              AND total_raw IS NULL AND total_typed IS NULL)
          OR (total_presence=N'NULL' AND total_parse_state=N'EXPLICIT_NULL'
              AND total_raw IS NULL AND total_typed IS NULL)
          OR (total_presence=N'VALUE' AND total_parse_state=N'VALID'
              AND total_raw IS NOT NULL AND total_typed IS NOT NULL)))
    ),
    CONSTRAINT CK_stg_localizacao_carga_status CHECK(
        validation_disposition=N'QUARANTINE'
        OR (status_presence IN(N'ABSENT',N'NULL',N'VALUE')
        AND status_catalog_version=N'localizacao-cargas-status-v1'
        AND ((status_normalized IN(N'finished',N'delivered',N'canceled',N'cancelled')
              AND status_terminal=1)
          OR (status_normalized NOT IN(N'finished',N'delivered',N'canceled',N'cancelled')
              AND status_terminal=0)))
    ),
    CONSTRAINT CK_stg_localizacao_carga_unsourced CHECK(
        validation_disposition=N'QUARANTINE'
        OR (status_branch_nickname IS NULL
            AND status_branch_nickname_presence=N'ABSENT'
            AND status_branch_nickname_provenance=N'UNSOURCED_LEGACY')
    ),
    CONSTRAINT CK_stg_localizacao_carga_freight_candidate CHECK(
        validation_disposition=N'QUARANTINE'
        OR (freight_candidate_presence=N'VALUE'
            AND freight_candidate_provenance=N'LOCAL_ROOT_SOURCE_KEY_ONLY_NO_RELATION'
            AND freight_candidate_state=N'EXACT_SCOPED_ALIAS_CANDIDATE_UNRESOLVED')
    ),
    CONSTRAINT CK_stg_localizacao_carga_hash CHECK(
        LEN(attribute_hash)=64 AND attribute_hash NOT LIKE '%[^0-9A-Fa-f]%'
    )
);
GO

CREATE INDEX IX_stg_localizacao_carga_execution_source_freshness
    ON stg.localizacao_carga_record(execution_id,source_key,freshness_at_utc DESC,stage_record_id)
    INCLUDE(attribute_hash,invoices_volumes_typed,status_terminal,status_normalized);
GO

CREATE TABLE ctl.localizacao_carga_promotion_result (
    execution_id UNIQUEIDENTIFIER NOT NULL,
    generic_candidate_rows BIGINT NOT NULL,
    typed_candidate_rows BIGINT NOT NULL,
    conflicting_root_keys BIGINT NOT NULL,
    validation_state NVARCHAR(16) COLLATE Latin1_General_100_BIN2 NOT NULL,
    validated_at_utc DATETIME2(3) NOT NULL,
    CONSTRAINT PK_ctl_localizacao_carga_promotion_result PRIMARY KEY CLUSTERED(execution_id),
    CONSTRAINT FK_ctl_localizacao_carga_promotion_result_generic FOREIGN KEY(execution_id)
        REFERENCES ctl.execution_promotion_result(execution_id),
    CONSTRAINT CK_ctl_localizacao_carga_promotion_result CHECK(
        generic_candidate_rows>=0 AND typed_candidate_rows>=0 AND conflicting_root_keys>=0
        AND validation_state IN(N'PASSED',N'BLOCKED')
        AND ((validation_state=N'PASSED' AND generic_candidate_rows=typed_candidate_rows
              AND conflicting_root_keys=0) OR validation_state=N'BLOCKED')
    )
);
GO

CREATE TABLE core.localizacao_cargas (
    localizacao_carga_id BIGINT IDENTITY(1,1) NOT NULL,
    record_state_id BIGINT NOT NULL,
    environment_name NVARCHAR(32) COLLATE Latin1_General_100_BIN2 NOT NULL,
    source_instance NVARCHAR(128) COLLATE Latin1_General_100_BIN2 NOT NULL,
    tenant_scope NVARCHAR(128) COLLATE Latin1_General_100_BIN2 NOT NULL,
    entity_name NVARCHAR(128) COLLATE Latin1_General_100_BIN2 NOT NULL,
    source_key NVARCHAR(256) COLLATE Latin1_General_100_BIN2 NOT NULL,
    source_key_wire_type NVARCHAR(16) COLLATE Latin1_General_100_BIN2 NOT NULL,
    payload_json NVARCHAR(MAX) NOT NULL,
    field_presence_json NVARCHAR(MAX) NOT NULL,
    service_at_raw NVARCHAR(MAX) NOT NULL,
    service_at_utc DATETIME2(3) NOT NULL,
    invoices_volumes_raw NVARCHAR(MAX) NULL,
    invoices_volumes_typed INT NULL,
    taxed_weight_raw NVARCHAR(MAX) NULL,
    taxed_weight_typed DECIMAL(38,9) NULL,
    invoices_value_raw NVARCHAR(MAX) NULL,
    invoices_value_typed DECIMAL(38,9) NULL,
    total_raw NVARCHAR(MAX) NULL,
    total_typed DECIMAL(38,9) NULL,
    status_raw NVARCHAR(MAX) NULL,
    status_normalized NVARCHAR(MAX) COLLATE Latin1_General_100_BIN2 NOT NULL,
    status_catalog_version NVARCHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL,
    status_terminal BIT NOT NULL,
    status_branch_nickname NVARCHAR(MAX) NULL,
    status_branch_nickname_presence NVARCHAR(8) COLLATE Latin1_General_100_BIN2 NOT NULL,
    status_branch_nickname_provenance NVARCHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL,
    freight_candidate_presence NVARCHAR(8) COLLATE Latin1_General_100_BIN2 NOT NULL,
    freight_candidate_provenance NVARCHAR(96) COLLATE Latin1_General_100_BIN2 NOT NULL,
    freight_candidate_state NVARCHAR(96) COLLATE Latin1_General_100_BIN2 NOT NULL,
    freshness_at_utc DATETIME2(3) NOT NULL,
    freshness_origin NVARCHAR(32) COLLATE Latin1_General_100_BIN2 NOT NULL,
    partition_basis NVARCHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL,
    overlap_policy_version NVARCHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL,
    attribute_hash CHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL,
    active BIT NOT NULL,
    first_seen_execution_id UNIQUEIDENTIFIER NOT NULL,
    last_seen_execution_id UNIQUEIDENTIFIER NOT NULL,
    first_seen_at_utc DATETIME2(3) NOT NULL,
    last_seen_at_utc DATETIME2(3) NOT NULL,
    CONSTRAINT PK_core_localizacao_cargas PRIMARY KEY CLUSTERED(localizacao_carga_id),
    CONSTRAINT UQ_core_localizacao_cargas_record_state UNIQUE(record_state_id),
    CONSTRAINT UQ_core_localizacao_cargas_source UNIQUE(
        environment_name,source_instance,tenant_scope,entity_name,source_key
    ),
    CONSTRAINT FK_core_localizacao_cargas_record_binding FOREIGN KEY(
        record_state_id,environment_name,source_instance,tenant_scope,entity_name,source_key
    ) REFERENCES core.entity_record_state(
        record_state_id,environment_name,source_instance,tenant_scope,entity_name,source_key
    ),
    CONSTRAINT FK_core_localizacao_cargas_first_execution FOREIGN KEY(first_seen_execution_id)
        REFERENCES ctl.execution_attempt(execution_id),
    CONSTRAINT FK_core_localizacao_cargas_last_execution FOREIGN KEY(last_seen_execution_id)
        REFERENCES ctl.execution_attempt(execution_id),
    CONSTRAINT CK_core_localizacao_cargas_identity CHECK(
        entity_name=N'localizacao_cargas' AND source_key_wire_type=N'INTEGER'
        AND LEFT(source_key,8) COLLATE Latin1_General_100_BIN2=N'INTEGER:'
        AND (
            SUBSTRING(source_key,9,248) COLLATE Latin1_General_100_BIN2=N'0'
            OR (SUBSTRING(source_key,9,248) COLLATE Latin1_General_100_BIN2 LIKE N'[1-9]%'
                AND SUBSTRING(source_key,9,248) COLLATE Latin1_General_100_BIN2 NOT LIKE N'%[^0-9]%')
            OR (SUBSTRING(source_key,9,248) COLLATE Latin1_General_100_BIN2 LIKE N'-[1-9]%'
                AND SUBSTRING(source_key,10,247) COLLATE Latin1_General_100_BIN2 NOT LIKE N'%[^0-9]%')
        )
    ),
    CONSTRAINT CK_core_localizacao_cargas_json CHECK(
        ISJSON(payload_json)=1 AND ISJSON(field_presence_json)=1
    ),
    CONSTRAINT CK_core_localizacao_cargas_status CHECK(
        status_catalog_version=N'localizacao-cargas-status-v1'
        AND ((status_normalized IN(N'finished',N'delivered',N'canceled',N'cancelled')
              AND status_terminal=1)
          OR (status_normalized NOT IN(N'finished',N'delivered',N'canceled',N'cancelled')
              AND status_terminal=0))
    ),
    CONSTRAINT CK_core_localizacao_cargas_unsourced CHECK(
        status_branch_nickname IS NULL
        AND status_branch_nickname_presence=N'ABSENT'
        AND status_branch_nickname_provenance=N'UNSOURCED_LEGACY'
    ),
    CONSTRAINT CK_core_localizacao_cargas_candidate CHECK(
        freight_candidate_presence=N'VALUE'
        AND freight_candidate_provenance=N'LOCAL_ROOT_SOURCE_KEY_ONLY_NO_RELATION'
        AND freight_candidate_state=N'EXACT_SCOPED_ALIAS_CANDIDATE_UNRESOLVED'
    ),
    CONSTRAINT CK_core_localizacao_cargas_partition CHECK(
        freshness_at_utc=service_at_utc AND freshness_origin=N'SERVICE_AT'
        AND partition_basis=N'freights.service_at'
        AND overlap_policy_version=N'localizacao-cargas-service-at-overlap-v1'
        AND active=1
    )
);
GO

CREATE INDEX IX_core_localizacao_cargas_active_service
    ON core.localizacao_cargas(
        environment_name,source_instance,tenant_scope,service_at_utc,source_key
    ) INCLUDE(
        localizacao_carga_id,freshness_at_utc,invoices_volumes_typed,
        status_terminal,status_normalized
    ) WHERE active=1;
GO

CREATE TABLE recon.localizacao_carga_root_presence_observation (
    execution_id UNIQUEIDENTIFIER NOT NULL,
    localizacao_carga_id BIGINT NOT NULL,
    observed_at_utc DATETIME2(3) NOT NULL,
    snapshot_completeness NVARCHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL,
    absence_evaluation NVARCHAR(48) COLLATE Latin1_General_100_BIN2 NOT NULL,
    CONSTRAINT PK_recon_localizacao_carga_root_presence
        PRIMARY KEY CLUSTERED(execution_id,localizacao_carga_id),
    CONSTRAINT FK_recon_localizacao_carga_presence_execution FOREIGN KEY(execution_id)
        REFERENCES ctl.execution_attempt(execution_id),
    CONSTRAINT FK_recon_localizacao_carga_presence_owner FOREIGN KEY(localizacao_carga_id)
        REFERENCES core.localizacao_cargas(localizacao_carga_id),
    CONSTRAINT CK_recon_localizacao_carga_presence CHECK(
        snapshot_completeness=N'BLOCKED_NO_COMPLETENESS_PROOF'
        AND absence_evaluation=N'NOT_EVALUATED_PRUNE_DISABLED'
    )
);
GO

CREATE OR ALTER PROCEDURE stg.usp_stage_localizacao_carga_record
    @execution_id UNIQUEIDENTIFIER,
    @input_batch_number INT,
    @input_record_ordinal INT,
    @source_key NVARCHAR(256),
    @payload_json NVARCHAR(MAX),
    @field_presence_json NVARCHAR(MAX),
    @service_at_raw NVARCHAR(MAX),
    @service_at_utc DATETIME2(3),
    @service_at_presence NVARCHAR(8),
    @service_at_parse_state NVARCHAR(16),
    @invoices_volumes_raw NVARCHAR(MAX),
    @invoices_volumes_typed INT,
    @invoices_volumes_presence NVARCHAR(8),
    @invoices_volumes_parse_state NVARCHAR(16),
    @taxed_weight_raw NVARCHAR(MAX),
    @taxed_weight_typed DECIMAL(38,9),
    @invoices_value_raw NVARCHAR(MAX),
    @invoices_value_typed DECIMAL(38,9),
    @total_raw NVARCHAR(MAX),
    @total_typed DECIMAL(38,9),
    @status_raw NVARCHAR(MAX),
    @status_normalized NVARCHAR(MAX),
    @status_terminal BIT,
    @status_branch_nickname_provenance NVARCHAR(64),
    @validation_disposition NVARCHAR(16),
    @quarantine_reason_code NVARCHAR(128),
    @observed_at_utc DATETIME2(3)
AS
BEGIN
    SET NOCOUNT ON;
    IF @execution_id IS NULL OR @input_batch_number IS NULL OR @input_batch_number<1
       OR @input_record_ordinal IS NULL OR @input_record_ordinal NOT BETWEEN 1 AND 100
       OR @observed_at_utc IS NULL
       OR @validation_disposition NOT IN(N'VALID',N'QUARANTINE')
       OR (@validation_disposition=N'QUARANTINE'
           AND (@quarantine_reason_code IS NULL OR LEN(@quarantine_reason_code) NOT BETWEEN 2 AND 64
             OR LEFT(@quarantine_reason_code,1) COLLATE Latin1_General_100_BIN2 NOT LIKE N'[A-Z]'
             OR @quarantine_reason_code COLLATE Latin1_General_100_BIN2 LIKE N'%[^A-Z0-9_]%'))
       OR (@validation_disposition=N'VALID' AND @quarantine_reason_code IS NOT NULL)
        THROW 52200,N'Envelope físico de Localização 8656 inválido.',1;

    IF @payload_json IS NULL OR @field_presence_json IS NULL
       OR ISJSON(@payload_json)<>1 OR ISJSON(@field_presence_json)<>1
        THROW 52201,N'RAW_PAYLOAD_PRESENCE_REQUIRED: JSON de Localização inválido.',1;

    DECLARE @expected_presence_fields TABLE(
        field_ordinal TINYINT NOT NULL PRIMARY KEY,
        field_name NVARCHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL UNIQUE,
        typed_state NVARCHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL
    );
    INSERT @expected_presence_fields VALUES
        (1,N'corporation_sequence_number',N'INTEGER_TYPE_TAGGED_SOURCE_KEY'),
        (2,N'type',N'UNVERIFIED_SOURCE_TYPE_PRESERVED_RAW'),
        (3,N'service_at',N'STRICT_TYPED_COLUMN'),
        (4,N'invoices_volumes',N'STRICT_TYPED_COLUMN'),
        (5,N'taxed_weight',N'STRICT_TYPED_COLUMN'),
        (6,N'invoices_value',N'STRICT_TYPED_COLUMN'),
        (7,N'total',N'STRICT_TYPED_COLUMN'),
        (8,N'service_type',N'UNVERIFIED_SOURCE_TYPE_PRESERVED_RAW'),
        (9,N'fit_crn_psn_nickname',N'UNVERIFIED_SOURCE_TYPE_PRESERVED_RAW'),
        (10,N'fit_dpn_delivery_prediction_at',N'UNVERIFIED_SOURCE_TYPE_PRESERVED_RAW'),
        (11,N'fit_dyn_name',N'UNVERIFIED_SOURCE_TYPE_PRESERVED_RAW'),
        (12,N'fit_dyn_drt_nickname',N'UNVERIFIED_SOURCE_TYPE_PRESERVED_RAW'),
        (13,N'fit_fsn_name',N'UNVERIFIED_SOURCE_TYPE_PRESERVED_RAW'),
        (14,N'fit_fln_status',N'STRICT_TYPED_COLUMN'),
        (15,N'fit_fln_cln_nickname',N'UNVERIFIED_SOURCE_TYPE_PRESERVED_RAW'),
        (16,N'fit_o_n_name',N'UNVERIFIED_SOURCE_TYPE_PRESERVED_RAW'),
        (17,N'fit_o_n_drt_nickname',N'UNVERIFIED_SOURCE_TYPE_PRESERVED_RAW');

    IF (SELECT COUNT_BIG(*) FROM OPENJSON(@field_presence_json))<>2
       OR (SELECT [type] FROM OPENJSON(@field_presence_json) WHERE [key]=N'fields')<>5
       OR (SELECT [type] FROM OPENJSON(@field_presence_json) WHERE [key]=N'policy')<>5
       OR EXISTS(SELECT 1 FROM OPENJSON(@field_presence_json)
                 WHERE [key] NOT IN(N'fields',N'policy'))
       OR (SELECT COUNT_BIG(*) FROM OPENJSON(@field_presence_json,N'$.fields'))<>17
       OR EXISTS(
            SELECT 1 FROM OPENJSON(@field_presence_json,N'$.fields') actual
            WHERE actual.[key] COLLATE Latin1_General_100_BIN2 NOT IN(
                SELECT field_name FROM @expected_presence_fields)
               OR actual.[type]<>5)
       OR EXISTS(
            SELECT 1 FROM @expected_presence_fields expected
            WHERE JSON_QUERY(@field_presence_json,
                    CONCAT(N'$.fields.',expected.field_name)) IS NULL)
       OR EXISTS(
            SELECT 1
            FROM @expected_presence_fields expected
            CROSS APPLY OPENJSON(JSON_QUERY(@field_presence_json,
                CONCAT(N'$.fields.',expected.field_name))) property
            GROUP BY expected.field_ordinal,expected.field_name
            HAVING COUNT_BIG(*)<>7
               OR SUM(CASE WHEN property.[key] IN(
                    N'path',N'presence',N'provenance',N'parseState',N'typedState',N'raw',
                    N'rawWireLexeme')
                    THEN 0 ELSE 1 END)>0
               OR SUM(CASE WHEN property.[key]=N'path' THEN 1 ELSE 0 END)<>1
               OR SUM(CASE WHEN property.[key]=N'presence' THEN 1 ELSE 0 END)<>1
               OR SUM(CASE WHEN property.[key]=N'provenance' THEN 1 ELSE 0 END)<>1
               OR SUM(CASE WHEN property.[key]=N'parseState' THEN 1 ELSE 0 END)<>1
               OR SUM(CASE WHEN property.[key]=N'typedState' THEN 1 ELSE 0 END)<>1
               OR SUM(CASE WHEN property.[key]=N'raw' THEN 1 ELSE 0 END)<>1
               OR SUM(CASE WHEN property.[key]=N'rawWireLexeme' THEN 1 ELSE 0 END)<>1)
       OR EXISTS(
            SELECT 1
            FROM @expected_presence_fields expected
            CROSS APPLY(SELECT JSON_QUERY(@field_presence_json,
                CONCAT(N'$.fields.',expected.field_name)) AS field_json) envelope
            CROSS APPLY(SELECT
                JSON_VALUE(envelope.field_json,N'$.presence') AS presence_state,
                JSON_VALUE(envelope.field_json,N'$.parseState') AS parse_state,
                (SELECT MAX(property.[value]) FROM OPENJSON(envelope.field_json) property
                 WHERE property.[key]=N'rawWireLexeme') AS raw_wire_lexeme,
                (SELECT MAX(property.[type]) FROM OPENJSON(envelope.field_json) property
                 WHERE property.[key]=N'rawWireLexeme') AS raw_wire_type,
                (SELECT MAX(property.[type]) FROM OPENJSON(envelope.field_json) property
                 WHERE property.[key]=N'raw') AS raw_type) evidence
            WHERE JSON_VALUE(envelope.field_json,N'$.path') COLLATE Latin1_General_100_BIN2
                    <>CONCAT(N'/',expected.field_name)
               OR JSON_VALUE(envelope.field_json,N'$.provenance')
                    COLLATE Latin1_General_100_BIN2<>N'DATAEXPORT_8656'
               OR JSON_VALUE(envelope.field_json,N'$.typedState')
                    COLLATE Latin1_General_100_BIN2<>expected.typed_state
               OR evidence.presence_state COLLATE Latin1_General_100_BIN2
                    NOT IN(N'ABSENT',N'NULL',N'VALUE')
               OR evidence.parse_state COLLATE Latin1_General_100_BIN2<>
                    CASE evidence.presence_state WHEN N'ABSENT' THEN N'NOT_PRESENT'
                         WHEN N'NULL' THEN N'EXPLICIT_NULL'
                         WHEN N'VALUE' THEN N'PRESERVED_OR_STRICTLY_PARSED_BY_NAMED_FIELD'
                    END
               OR evidence.raw_wire_type<>1
               OR (evidence.presence_state IN(N'ABSENT',N'NULL') AND evidence.raw_type<>0)
               OR (evidence.presence_state=N'VALUE' AND evidence.raw_type NOT IN(1,2,3))
               OR (evidence.presence_state IN(N'ABSENT',N'NULL')
                   AND evidence.raw_wire_lexeme<>N'NOT_APPLICABLE_WITHOUT_VALUE')
               OR (evidence.presence_state=N'VALUE' AND evidence.raw_type IN(1,3)
                   AND evidence.raw_wire_lexeme<>N'NOT_APPLICABLE_NON_NUMERIC_TOKEN')
               OR (evidence.presence_state=N'VALUE' AND evidence.raw_type=2
                   AND expected.field_name NOT IN(
                       N'invoices_volumes',N'taxed_weight',N'invoices_value',N'total')
                   AND evidence.raw_wire_lexeme<>N'UNAVAILABLE_AFTER_JSON_TREE_NORMALIZATION')
               OR (evidence.presence_state=N'VALUE' AND evidence.raw_type=2
                   AND expected.field_name IN(
                       N'invoices_volumes',N'taxed_weight',N'invoices_value',N'total')
                   AND (evidence.raw_wire_lexeme IS NULL OR evidence.raw_wire_lexeme=N'')))
       OR (SELECT COUNT_BIG(*) FROM OPENJSON(@field_presence_json,N'$.policy'))<>3
       OR JSON_VALUE(@field_presence_json,N'$.policy.matrix')
            COLLATE Latin1_General_100_BIN2<>N'LOCALIZACAO_8656_17_PATHS_V1'
       OR JSON_VALUE(@field_presence_json,N'$.policy.freightVolumeFallback')
            COLLATE Latin1_General_100_BIN2<>
                N'FREIGHT_VOLUME_FALLBACK_DEFERRED_RELATION_AND_PUBLICATION'
       OR JSON_QUERY(@field_presence_json,N'$.policy.statusBranchNickname') IS NULL
       OR EXISTS(SELECT 1 FROM OPENJSON(@field_presence_json,N'$.policy')
                 WHERE [key] NOT IN(N'matrix',N'freightVolumeFallback',N'statusBranchNickname'))
       OR (SELECT COUNT_BIG(*)
           FROM OPENJSON(@field_presence_json,N'$.policy.statusBranchNickname'))<>3
       OR JSON_VALUE(@field_presence_json,N'$.policy.statusBranchNickname.path')<>N'ABSENT'
       OR JSON_VALUE(@field_presence_json,N'$.policy.statusBranchNickname.presence')<>N'ABSENT'
       OR JSON_VALUE(@field_presence_json,N'$.policy.statusBranchNickname.provenance')
            <>N'UNSOURCED_LEGACY'
       OR EXISTS(SELECT 1
           FROM OPENJSON(@field_presence_json,N'$.policy.statusBranchNickname')
           WHERE [key] NOT IN(N'path',N'presence',N'provenance') OR [type]<>1)
        THROW 52202,N'PRESENCE_SCHEMA_INVALID: envelope fechado dos 17 paths 8656 inválido.',1;

    DECLARE @taxed_weight_presence NVARCHAR(8)=
        JSON_VALUE(@field_presence_json,N'$.fields.taxed_weight.presence');
    DECLARE @invoices_value_presence NVARCHAR(8)=
        JSON_VALUE(@field_presence_json,N'$.fields.invoices_value.presence');
    DECLARE @total_presence NVARCHAR(8)=
        JSON_VALUE(@field_presence_json,N'$.fields.total.presence');
    DECLARE @status_presence NVARCHAR(8)=
        JSON_VALUE(@field_presence_json,N'$.fields.fit_fln_status.presence');

    IF @validation_disposition=N'VALID'
    BEGIN
        IF @source_key IS NULL
           OR LEFT(@source_key,8) COLLATE Latin1_General_100_BIN2<>N'INTEGER:'
           OR NOT (
                SUBSTRING(@source_key,9,248) COLLATE Latin1_General_100_BIN2=N'0'
                OR (SUBSTRING(@source_key,9,248) COLLATE Latin1_General_100_BIN2 LIKE N'[1-9]%'
                    AND SUBSTRING(@source_key,9,248) COLLATE Latin1_General_100_BIN2 NOT LIKE N'%[^0-9]%')
                OR (SUBSTRING(@source_key,9,248) COLLATE Latin1_General_100_BIN2 LIKE N'-[1-9]%'
                    AND SUBSTRING(@source_key,10,247) COLLATE Latin1_General_100_BIN2 NOT LIKE N'%[^0-9]%')
           )
            THROW 52201,N'Identidade INTEGER type-tagged de Localização inválida.',1;

        DECLARE @payload_source_key NVARCHAR(MAX),@payload_source_key_type INT,
                @presence_source_key NVARCHAR(MAX),@presence_source_key_type INT;
        SELECT @payload_source_key=[value],@payload_source_key_type=[type]
        FROM OPENJSON(@payload_json) WHERE [key]=N'corporation_sequence_number';
        SELECT @presence_source_key=[value],@presence_source_key_type=[type]
        FROM OPENJSON(JSON_QUERY(@field_presence_json,
             N'$.fields.corporation_sequence_number')) WHERE [key]=N'raw';
        IF JSON_VALUE(@field_presence_json,
                N'$.fields.corporation_sequence_number.presence')<>N'VALUE'
           OR @payload_source_key_type<>2 OR @presence_source_key_type<>2
           OR CONCAT(N'INTEGER:',@payload_source_key) COLLATE Latin1_General_100_BIN2<>@source_key
           OR @payload_source_key COLLATE Latin1_General_100_BIN2<>@presence_source_key
           OR EXISTS(SELECT 1 FROM OPENJSON(@payload_json) actual
                     WHERE actual.[key] COLLATE Latin1_General_100_BIN2 NOT IN(
                         SELECT field_name FROM @expected_presence_fields))
           OR EXISTS(SELECT [key] FROM OPENJSON(@payload_json)
                     GROUP BY [key] HAVING COUNT_BIG(*)>1)
           OR EXISTS(
                SELECT 1
                FROM @expected_presence_fields expected
                CROSS APPLY(SELECT JSON_QUERY(@field_presence_json,
                    CONCAT(N'$.fields.',expected.field_name)) AS field_json) envelope
                OUTER APPLY(SELECT MAX(actual.[value]) AS [value],MAX(actual.[type]) AS [type]
                    FROM OPENJSON(@payload_json) actual
                    WHERE actual.[key] COLLATE Latin1_General_100_BIN2=expected.field_name) payload
                OUTER APPLY(SELECT MAX(raw_property.[value]) AS [value],
                                   MAX(raw_property.[type]) AS [type]
                    FROM OPENJSON(envelope.field_json) raw_property
                    WHERE raw_property.[key]=N'raw') raw_evidence
                WHERE (JSON_VALUE(envelope.field_json,N'$.presence')=N'ABSENT'
                        AND payload.[type] IS NOT NULL)
                   OR (JSON_VALUE(envelope.field_json,N'$.presence')=N'NULL'
                        AND ISNULL(payload.[type],-1)<>0)
                   OR (JSON_VALUE(envelope.field_json,N'$.presence')=N'VALUE'
                        AND (payload.[type] IS NULL OR payload.[type]<>raw_evidence.[type]
                          OR payload.[value] COLLATE Latin1_General_100_BIN2
                             <>raw_evidence.[value] COLLATE Latin1_General_100_BIN2)))
            THROW 52201,N'SOURCE_KEY_PAYLOAD_DIVERGENT_OR_UNAPPROVED_PATH.',1;

        DECLARE @service_at_envelope_raw_value NVARCHAR(MAX),
                @service_at_envelope_raw_type INT,
                @service_at_scalar_raw_value NVARCHAR(MAX),
                @service_at_scalar_raw_type INT;
        SELECT @service_at_envelope_raw_value=raw_property.[value],
               @service_at_envelope_raw_type=raw_property.[type]
        FROM OPENJSON(JSON_QUERY(@field_presence_json,N'$.fields.service_at')) raw_property
        WHERE raw_property.[key]=N'raw';
        IF @service_at_raw IS NULL
           OR ISJSON(CONCAT(N'[',@service_at_raw,N']'))<>1
            THROW 52203,N'SERVICE_AT_REQUIRED_VALID: raw não é um token JSON íntegro.',1;
        SELECT @service_at_scalar_raw_value=scalar_raw.[value],
               @service_at_scalar_raw_type=scalar_raw.[type]
        FROM OPENJSON(CONCAT(N'[',@service_at_raw,N']')) scalar_raw;
        DECLARE @service_at_has_explicit_offset BIT=CASE
            WHEN RIGHT(@service_at_scalar_raw_value,1) COLLATE Latin1_General_100_BIN2=N'Z'
              OR (LEN(@service_at_scalar_raw_value)>=6
                AND SUBSTRING(@service_at_scalar_raw_value,
                    LEN(@service_at_scalar_raw_value)-5,1) IN(N'+',N'-')
                AND SUBSTRING(@service_at_scalar_raw_value,
                    LEN(@service_at_scalar_raw_value)-2,1)=N':')
            THEN 1 ELSE 0 END;
        DECLARE @service_at_local_value DATETIME2(7)=CASE
            WHEN @service_at_has_explicit_offset=0
            THEN TRY_CONVERT(DATETIME2(7),@service_at_scalar_raw_value,126) END;
        -- Java aceita data civil somente quando America/Sao_Paulo oferece um único offset.
        -- Os candidatos antes/depois da transição detectam tanto gap quanto overlap.
        DECLARE @service_at_offset_before INT=CASE WHEN @service_at_local_value IS NOT NULL
            THEN DATEPART(TZOFFSET,DATEADD(DAY,-1,@service_at_local_value)
                AT TIME ZONE N'E. South America Standard Time') END;
        DECLARE @service_at_offset_after INT=CASE WHEN @service_at_local_value IS NOT NULL
            THEN DATEPART(TZOFFSET,DATEADD(DAY,1,@service_at_local_value)
                AT TIME ZONE N'E. South America Standard Time') END;
        DECLARE @service_at_candidate_before DATETIMEOFFSET(7)=CASE
            WHEN @service_at_local_value IS NOT NULL
            THEN TODATETIMEOFFSET(@service_at_local_value,@service_at_offset_before) END;
        DECLARE @service_at_candidate_after DATETIMEOFFSET(7)=CASE
            WHEN @service_at_local_value IS NOT NULL
            THEN TODATETIMEOFFSET(@service_at_local_value,@service_at_offset_after) END;
        DECLARE @service_at_roundtrip_before DATETIMEOFFSET(7)=CASE
            WHEN @service_at_candidate_before IS NOT NULL THEN
                @service_at_candidate_before AT TIME ZONE N'E. South America Standard Time' END;
        DECLARE @service_at_roundtrip_after DATETIMEOFFSET(7)=CASE
            WHEN @service_at_candidate_after IS NOT NULL THEN
                @service_at_candidate_after AT TIME ZONE N'E. South America Standard Time' END;
        DECLARE @service_at_before_valid BIT=CASE
            WHEN CONVERT(DATETIME2(7),@service_at_roundtrip_before)=@service_at_local_value
             AND DATEPART(TZOFFSET,@service_at_roundtrip_before)=@service_at_offset_before
            THEN 1 ELSE 0 END;
        DECLARE @service_at_after_valid BIT=CASE
            WHEN @service_at_offset_after<>@service_at_offset_before
             AND CONVERT(DATETIME2(7),@service_at_roundtrip_after)=@service_at_local_value
             AND DATEPART(TZOFFSET,@service_at_roundtrip_after)=@service_at_offset_after
            THEN 1 ELSE 0 END;
        DECLARE @service_at_valid_offset_count TINYINT=
            CONVERT(TINYINT,@service_at_before_valid)+CONVERT(TINYINT,@service_at_after_valid);
        DECLARE @service_at_local_zoned DATETIMEOFFSET(7)=CASE
            WHEN @service_at_valid_offset_count=1 AND @service_at_before_valid=1
                THEN @service_at_candidate_before
            WHEN @service_at_valid_offset_count=1 AND @service_at_after_valid=1
                THEN @service_at_candidate_after END;
        DECLARE @service_at_derived_utc DATETIME2(3)=CASE
            WHEN @service_at_has_explicit_offset=1 THEN CONVERT(DATETIME2(3),SWITCHOFFSET(
                TRY_CONVERT(DATETIMEOFFSET(7),@service_at_scalar_raw_value,127),N'+00:00'))
            WHEN @service_at_valid_offset_count=1
            THEN CONVERT(DATETIME2(3),SWITCHOFFSET(@service_at_local_zoned,N'+00:00')) END;
        IF @service_at_presence<>N'VALUE' OR @service_at_parse_state<>N'VALID'
           OR @service_at_utc IS NULL OR @service_at_derived_utc IS NULL
           OR @service_at_scalar_raw_type<>1 OR @service_at_envelope_raw_type<>1
           OR @service_at_scalar_raw_value COLLATE Latin1_General_100_BIN2
                <>@service_at_envelope_raw_value COLLATE Latin1_General_100_BIN2
           OR @service_at_utc<>@service_at_derived_utc
           OR @service_at_presence<>
                JSON_VALUE(@field_presence_json,N'$.fields.service_at.presence')
            THROW 52203,N'SERVICE_AT_REQUIRED_VALID: frescor de Localização inválido.',1;

        IF EXISTS(
            SELECT 1 FROM @expected_presence_fields expected
            CROSS APPLY(SELECT JSON_QUERY(@field_presence_json,
                CONCAT(N'$.fields.',expected.field_name)) AS field_json) envelope
            CROSS APPLY(SELECT
                MAX(CASE WHEN property.[key]=N'raw' THEN property.[type] END) AS raw_type,
                MAX(CASE WHEN property.[key]=N'rawWireLexeme'
                         THEN property.[value] END) AS raw_wire_lexeme
                FROM OPENJSON(envelope.field_json) property) raw_value
            WHERE expected.field_name IN(
                    N'invoices_volumes',N'taxed_weight',N'invoices_value',N'total')
              AND JSON_VALUE(envelope.field_json,N'$.presence')=N'VALUE'
              AND raw_value.raw_type=2
              AND raw_value.raw_wire_lexeme COLLATE Latin1_General_100_BIN2=
                    N'UNAVAILABLE_AFTER_JSON_TREE_NORMALIZATION'
        ) THROW 52205,N'DECIMAL_INVALID: léxico numérico wire indisponível.',1;

        DECLARE @invoices_volumes_envelope_raw_value NVARCHAR(MAX),
                @invoices_volumes_envelope_raw_type INT,
                @invoices_volumes_scalar_raw_value NVARCHAR(MAX),
                @invoices_volumes_scalar_raw_type INT;
        SELECT @invoices_volumes_envelope_raw_value=raw_property.[value],
               @invoices_volumes_envelope_raw_type=raw_property.[type]
        FROM OPENJSON(JSON_QUERY(@field_presence_json,N'$.fields.invoices_volumes')) raw_property
        WHERE raw_property.[key]=N'raw';
        IF @invoices_volumes_raw IS NOT NULL
           AND ISJSON(CONCAT(N'[',@invoices_volumes_raw,N']'))<>1
            THROW 52204,N'INVOICES_VOLUMES_INVALID: raw não é um token JSON íntegro.',1;
        IF @invoices_volumes_raw IS NOT NULL
            SELECT @invoices_volumes_scalar_raw_value=scalar_raw.[value],
                   @invoices_volumes_scalar_raw_type=scalar_raw.[type]
            FROM OPENJSON(CONCAT(N'[',@invoices_volumes_raw,N']')) scalar_raw;
        DECLARE @invoices_volumes_wire_lexeme NVARCHAR(MAX);
        SELECT @invoices_volumes_wire_lexeme=property.[value]
        FROM OPENJSON(JSON_QUERY(@field_presence_json,N'$.fields.invoices_volumes')) property
        WHERE property.[key]=N'rawWireLexeme';
        IF @invoices_volumes_presence<>
                JSON_VALUE(@field_presence_json,N'$.fields.invoices_volumes.presence')
           OR @invoices_volumes_presence NOT IN(N'ABSENT',N'NULL',N'VALUE')
           OR (@invoices_volumes_presence=N'ABSENT'
               AND (@invoices_volumes_parse_state<>N'NOT_PRESENT'
                 OR @invoices_volumes_raw IS NOT NULL OR @invoices_volumes_typed IS NOT NULL))
           OR (@invoices_volumes_presence=N'NULL'
               AND (@invoices_volumes_parse_state<>N'EXPLICIT_NULL'
                 OR @invoices_volumes_raw IS NOT NULL OR @invoices_volumes_typed IS NOT NULL))
           OR (@invoices_volumes_presence=N'VALUE'
               AND (@invoices_volumes_parse_state<>N'VALID'
                  OR @invoices_volumes_raw IS NULL OR @invoices_volumes_typed IS NULL
                  OR @invoices_volumes_typed<0
                  OR @invoices_volumes_scalar_raw_type NOT IN(1,2)
                  OR @invoices_volumes_scalar_raw_type<>@invoices_volumes_envelope_raw_type
                  OR (@invoices_volumes_scalar_raw_type=1
                    AND @invoices_volumes_scalar_raw_value COLLATE Latin1_General_100_BIN2
                       <>@invoices_volumes_envelope_raw_value COLLATE Latin1_General_100_BIN2)
                  OR (@invoices_volumes_scalar_raw_type=2
                    AND (@invoices_volumes_wire_lexeme COLLATE Latin1_General_100_BIN2
                           <>@invoices_volumes_raw COLLATE Latin1_General_100_BIN2
                      OR TRY_CONVERT(INT,@invoices_volumes_envelope_raw_value) IS NULL
                      OR TRY_CONVERT(INT,@invoices_volumes_envelope_raw_value)
                           <>@invoices_volumes_typed))))
            THROW 52204,N'INVOICES_VOLUMES_INVALID: inteiro estrito inválido.',1;

        DECLARE @invoices_volumes_lexeme NVARCHAR(MAX)=CASE
            WHEN @invoices_volumes_wire_lexeme NOT IN(
                    N'NOT_APPLICABLE_NON_NUMERIC_TOKEN',
                    N'UNAVAILABLE_AFTER_JSON_TREE_NORMALIZATION')
              THEN @invoices_volumes_wire_lexeme
            WHEN LEFT(@invoices_volumes_raw,1)=N'"' AND RIGHT(@invoices_volumes_raw,1)=N'"'
              AND DATALENGTH(@invoices_volumes_raw)>=4
              THEN SUBSTRING(@invoices_volumes_raw,2,LEN(@invoices_volumes_raw)-2)
            ELSE @invoices_volumes_raw END;
        IF @invoices_volumes_presence=N'VALUE'
           AND (@invoices_volumes_lexeme=N''
             OR (@invoices_volumes_scalar_raw_type=2
                 AND @invoices_volumes_wire_lexeme COLLATE Latin1_General_100_BIN2
                    <>@invoices_volumes_raw COLLATE Latin1_General_100_BIN2)
             OR @invoices_volumes_lexeme COLLATE Latin1_General_100_BIN2 LIKE N'%[^0-9]%'
             OR TRY_CONVERT(INT,@invoices_volumes_lexeme) IS NULL
             OR TRY_CONVERT(INT,@invoices_volumes_lexeme)<>@invoices_volumes_typed)
            THROW 52204,N'INVOICES_VOLUMES_INVALID: léxico ASCII, overflow ou valor divergente.',1;

        DECLARE @taxed_weight_parse_state NVARCHAR(16)=CASE @taxed_weight_presence
            WHEN N'ABSENT' THEN N'NOT_PRESENT' WHEN N'NULL' THEN N'EXPLICIT_NULL'
            WHEN N'VALUE' THEN N'VALID' ELSE N'INVALID' END;
        DECLARE @invoices_value_parse_state NVARCHAR(16)=CASE @invoices_value_presence
            WHEN N'ABSENT' THEN N'NOT_PRESENT' WHEN N'NULL' THEN N'EXPLICIT_NULL'
            WHEN N'VALUE' THEN N'VALID' ELSE N'INVALID' END;
        DECLARE @total_parse_state NVARCHAR(16)=CASE @total_presence
            WHEN N'ABSENT' THEN N'NOT_PRESENT' WHEN N'NULL' THEN N'EXPLICIT_NULL'
            WHEN N'VALUE' THEN N'VALID' ELSE N'INVALID' END;
        DECLARE @decimal_binding TABLE(
            field_name NVARCHAR(32) COLLATE Latin1_General_100_BIN2 NOT NULL PRIMARY KEY,
            presence NVARCHAR(8) COLLATE Latin1_General_100_BIN2 NOT NULL,
            scalar_raw NVARCHAR(MAX) NULL,
            typed_value DECIMAL(38,9) NULL,
            scalar_raw_value NVARCHAR(MAX) NULL,
            scalar_raw_type INT NULL,
            envelope_raw_value NVARCHAR(MAX) NULL,
            envelope_raw_type INT NULL,
            raw_wire_lexeme NVARCHAR(MAX) NULL
        );
        INSERT @decimal_binding(field_name,presence,scalar_raw,typed_value) VALUES
            (N'taxed_weight',@taxed_weight_presence,@taxed_weight_raw,@taxed_weight_typed),
            (N'invoices_value',@invoices_value_presence,@invoices_value_raw,@invoices_value_typed),
            (N'total',@total_presence,@total_raw,@total_typed);
        IF EXISTS(SELECT 1 FROM @decimal_binding binding
                  WHERE binding.scalar_raw IS NOT NULL
                    AND ISJSON(CONCAT(N'[',binding.scalar_raw,N']'))<>1)
            THROW 52205,N'DECIMAL_INVALID: raw não é um token JSON íntegro.',1;
        UPDATE binding SET
            envelope_raw_value=evidence.raw_value,
            envelope_raw_type=evidence.raw_type,
            raw_wire_lexeme=evidence.raw_wire_lexeme
        FROM @decimal_binding binding
        CROSS APPLY(
            SELECT
                MAX(CASE WHEN property.[key]=N'raw' THEN property.[value] END) AS raw_value,
                MAX(CASE WHEN property.[key]=N'raw' THEN property.[type] END) AS raw_type,
                MAX(CASE WHEN property.[key]=N'rawWireLexeme'
                         THEN property.[value] END) AS raw_wire_lexeme
            FROM OPENJSON(JSON_QUERY(@field_presence_json,
                CONCAT(N'$.fields.',binding.field_name))) property
        ) evidence;
        UPDATE binding SET
            scalar_raw_value=scalar_raw.[value],scalar_raw_type=scalar_raw.[type]
        FROM @decimal_binding binding
        CROSS APPLY OPENJSON(CONCAT(N'[',binding.scalar_raw,N']')) scalar_raw
        WHERE binding.scalar_raw IS NOT NULL;
        IF EXISTS(
            SELECT 1 FROM @decimal_binding binding
            WHERE (binding.presence IN(N'ABSENT',N'NULL')
                    AND (binding.scalar_raw IS NOT NULL OR binding.typed_value IS NOT NULL))
                OR (binding.presence=N'VALUE'
                    AND (binding.scalar_raw IS NULL OR binding.typed_value IS NULL
                      OR binding.scalar_raw_type NOT IN(1,2)
                      OR binding.scalar_raw_type<>binding.envelope_raw_type
                      OR (binding.scalar_raw_type=1
                        AND binding.scalar_raw_value COLLATE Latin1_General_100_BIN2
                           <>binding.envelope_raw_value COLLATE Latin1_General_100_BIN2)
                      OR (binding.scalar_raw_type=2
                        AND (binding.raw_wire_lexeme COLLATE Latin1_General_100_BIN2
                              <>binding.scalar_raw COLLATE Latin1_General_100_BIN2
                          OR TRY_CONVERT(DECIMAL(38,9),binding.envelope_raw_value) IS NULL
                          OR TRY_CONVERT(DECIMAL(38,9),binding.envelope_raw_value)
                              <>binding.typed_value))))
        )
            THROW 52205,N'DECIMAL_INVALID: presença, raw ou typed divergentes.',1;

        DECLARE @taxed_weight_wire_lexeme NVARCHAR(MAX),
                @invoices_value_wire_lexeme NVARCHAR(MAX),
                @total_wire_lexeme NVARCHAR(MAX);
        SELECT @taxed_weight_wire_lexeme=raw_wire_lexeme
        FROM @decimal_binding WHERE field_name=N'taxed_weight';
        SELECT @invoices_value_wire_lexeme=raw_wire_lexeme
        FROM @decimal_binding WHERE field_name=N'invoices_value';
        SELECT @total_wire_lexeme=raw_wire_lexeme
        FROM @decimal_binding WHERE field_name=N'total';
        DECLARE @taxed_weight_lexeme NVARCHAR(MAX)=CASE
            WHEN @taxed_weight_wire_lexeme NOT IN(
                    N'NOT_APPLICABLE_NON_NUMERIC_TOKEN',
                    N'UNAVAILABLE_AFTER_JSON_TREE_NORMALIZATION')
              THEN @taxed_weight_wire_lexeme
            WHEN LEFT(@taxed_weight_raw,1)=N'"' AND RIGHT(@taxed_weight_raw,1)=N'"'
              AND DATALENGTH(@taxed_weight_raw)>=4
              THEN SUBSTRING(@taxed_weight_raw,2,LEN(@taxed_weight_raw)-2)
            ELSE @taxed_weight_raw END;
        DECLARE @invoices_value_lexeme NVARCHAR(MAX)=CASE
            WHEN @invoices_value_wire_lexeme NOT IN(
                    N'NOT_APPLICABLE_NON_NUMERIC_TOKEN',
                    N'UNAVAILABLE_AFTER_JSON_TREE_NORMALIZATION')
              THEN @invoices_value_wire_lexeme
            WHEN LEFT(@invoices_value_raw,1)=N'"' AND RIGHT(@invoices_value_raw,1)=N'"'
              AND DATALENGTH(@invoices_value_raw)>=4
              THEN SUBSTRING(@invoices_value_raw,2,LEN(@invoices_value_raw)-2)
            ELSE @invoices_value_raw END;
        DECLARE @total_lexeme NVARCHAR(MAX)=CASE
            WHEN @total_wire_lexeme NOT IN(
                    N'NOT_APPLICABLE_NON_NUMERIC_TOKEN',
                    N'UNAVAILABLE_AFTER_JSON_TREE_NORMALIZATION')
              THEN @total_wire_lexeme
            WHEN LEFT(@total_raw,1)=N'"' AND RIGHT(@total_raw,1)=N'"'
              AND DATALENGTH(@total_raw)>=4
              THEN SUBSTRING(@total_raw,2,LEN(@total_raw)-2)
            ELSE @total_raw END;
        DECLARE @decimal_lexemes TABLE(
            field_name NVARCHAR(32) COLLATE Latin1_General_100_BIN2 NOT NULL,
            presence NVARCHAR(8) COLLATE Latin1_General_100_BIN2 NOT NULL,
            lexeme NVARCHAR(MAX) NULL,
            typed_value DECIMAL(38,9) NULL
        );
        INSERT @decimal_lexemes VALUES
            (N'taxed_weight',@taxed_weight_presence,@taxed_weight_lexeme,@taxed_weight_typed),
            (N'invoices_value',@invoices_value_presence,@invoices_value_lexeme,@invoices_value_typed),
            (N'total',@total_presence,@total_lexeme,@total_typed);
        IF EXISTS(
            SELECT 1 FROM @decimal_lexemes value
            CROSS APPLY(SELECT CASE WHEN LEFT(value.lexeme,1)=N'-'
                                    THEN SUBSTRING(value.lexeme,2,LEN(value.lexeme)-1)
                                    ELSE value.lexeme END AS unsigned_lexeme) unsigned_value
            CROSS APPLY(SELECT CHARINDEX(N'.',unsigned_value.unsigned_lexeme) AS dot_position,
                               LEN(unsigned_value.unsigned_lexeme)
                                 -LEN(REPLACE(unsigned_value.unsigned_lexeme,N'.',N'')) AS dot_count)
                punctuation
            WHERE value.presence=N'VALUE'
              AND (value.lexeme IS NULL OR value.lexeme=N''
                OR value.lexeme COLLATE Latin1_General_100_BIN2 LIKE N'%[^0-9.-]%'
                OR value.lexeme COLLATE Latin1_General_100_BIN2 LIKE N'%-%-%'
                OR CHARINDEX(N'-',value.lexeme)>1
                OR unsigned_value.unsigned_lexeme=N''
                OR punctuation.dot_count>1 OR punctuation.dot_position=1
                OR (punctuation.dot_position>0
                    AND punctuation.dot_position=LEN(unsigned_value.unsigned_lexeme))
                OR (punctuation.dot_position>0
                    AND LEN(unsigned_value.unsigned_lexeme)-punctuation.dot_position>9)
                OR LEN(REPLACE(unsigned_value.unsigned_lexeme,N'.',N''))>38
                OR TRY_CONVERT(DECIMAL(38,9),value.lexeme) IS NULL
                OR TRY_CONVERT(DECIMAL(38,9),value.lexeme)<>value.typed_value)
        ) THROW 52205,N'DECIMAL_INVALID: léxico ASCII, precisão, escala ou typed divergente.',1;

        DECLARE @status_envelope_raw_value NVARCHAR(MAX),@status_envelope_raw_type INT;
        SELECT @status_envelope_raw_value=raw_property.[value],
               @status_envelope_raw_type=raw_property.[type]
        FROM OPENJSON(JSON_QUERY(@field_presence_json,N'$.fields.fit_fln_status')) raw_property
        WHERE raw_property.[key]=N'raw';
        IF @status_normalized IS NULL OR @status_terminal IS NULL
           OR (@status_presence IN(N'ABSENT',N'NULL')
              AND (@status_raw IS NOT NULL OR @status_normalized<>N'sem_status'))
           OR (@status_presence=N'VALUE'
              AND (@status_raw IS NULL OR @status_envelope_raw_type<>1
                OR @status_raw COLLATE Latin1_General_100_BIN2
                   <>@status_envelope_raw_value COLLATE Latin1_General_100_BIN2))
           OR (@status_presence=N'VALUE'
              AND @status_normalized COLLATE Latin1_General_100_BIN2<>
                  CASE WHEN LTRIM(RTRIM(@status_raw))=N'' THEN N'sem_status'
                       ELSE LOWER(LTRIM(RTRIM(@status_raw))) END COLLATE Latin1_General_100_BIN2)
           OR (@status_normalized IN(N'finished',N'delivered',N'canceled',N'cancelled')
               AND @status_terminal<>1)
           OR (@status_normalized NOT IN(N'finished',N'delivered',N'canceled',N'cancelled')
               AND @status_terminal<>0)
            THROW 52206,N'STATUS_INVALID: status de Localização diverge do catálogo exato.',1;

        IF @status_branch_nickname_provenance<>N'UNSOURCED_LEGACY'
            THROW 52207,N'STATUS_BRANCH_NICKNAME deve permanecer UNSOURCED_LEGACY.',1;
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
    IF @entity_name COLLATE Latin1_General_100_BIN2<>N'localizacao_cargas'
        THROW 52208,N'A execução não pertence a Localização de Cargas.',1;
    IF UPPER(@source_instance) IN(N'DEFAULT',N'GLOBAL',N'SINGLETON')
       OR UPPER(@tenant_scope) IN(N'DEFAULT',N'GLOBAL',N'SINGLETON')
        THROW 52209,N'Localização exige source_instance/tenant_scope explícitos.',1;

    -- Mesmo QUARANTINE recebe fingerprints e sidecar imutável: o kernel genérico não
    -- carrega payload/raw/presença e, isoladamente, seria evidência insuficiente.
    DECLARE @attribute_hash CHAR(64)=
        LOWER(CONVERT(CHAR(64),HASHBYTES('SHA2_256',CONVERT(VARBINARY(MAX),CONCAT(
            N'localizacao-cargas-envelope-v2|',COALESCE(@source_key,N'<NULL>'),N'|',
            @payload_json,N'|',@field_presence_json,N'|',@validation_disposition,N'|',
            COALESCE(@quarantine_reason_code,N'<NULL>'),N'|',
            @service_at_raw,N'|',CONVERT(NVARCHAR(33),@service_at_utc,126),N'|',
            COALESCE(@invoices_volumes_raw,N'<ABSENT_OR_NULL>'),N'|',
            COALESCE(CONVERT(NVARCHAR(32),@invoices_volumes_typed),N'<NULL>'),N'|',
            COALESCE(@taxed_weight_raw,N'<ABSENT_OR_NULL>'),N'|',
            COALESCE(CONVERT(NVARCHAR(64),@taxed_weight_typed),N'<NULL>'),N'|',
            COALESCE(@invoices_value_raw,N'<ABSENT_OR_NULL>'),N'|',
            COALESCE(CONVERT(NVARCHAR(64),@invoices_value_typed),N'<NULL>'),N'|',
            COALESCE(@total_raw,N'<ABSENT_OR_NULL>'),N'|',
            COALESCE(CONVERT(NVARCHAR(64),@total_typed),N'<NULL>'),N'|',
            COALESCE(@status_raw,N'<NULL>'),N'|',COALESCE(@status_normalized,N'<NULL>'),N'|',
            COALESCE(CONVERT(NVARCHAR(1),@status_terminal),N'<NULL>'),N'|',
            COALESCE(@status_branch_nickname_provenance,N'<NULL>')))),2));
    DECLARE @presence_hash CHAR(64)=
        LOWER(CONVERT(CHAR(64),HASHBYTES('SHA2_256',CONVERT(VARBINARY(MAX),
            CONCAT(N'localizacao-cargas-presence-v2|',@field_presence_json))),2));
    EXEC stg.usp_stage_record
        @execution_id,@input_batch_number,@input_record_ordinal,@source_key,
        N'localizacao-cargas-envelope-v2',@attribute_hash,
        N'localizacao-cargas-presence-v2',
        @presence_hash,@service_at_utc,@validation_disposition,@quarantine_reason_code,
        @observed_at_utc;

    DECLARE @stage_record_id BIGINT;
    SELECT @stage_record_id=stage_record_id
    FROM stg.execution_record WITH(UPDLOCK,HOLDLOCK)
    WHERE execution_id=@execution_id AND input_batch_number=@input_batch_number
      AND input_record_ordinal=@input_record_ordinal;
    IF EXISTS(
        SELECT 1 FROM stg.localizacao_carga_record WITH(UPDLOCK,HOLDLOCK)
        WHERE stage_record_id=@stage_record_id AND attribute_hash<>@attribute_hash
    ) THROW 52210,N'Retry de Localização possui conteúdo divergente.',1;
    IF NOT EXISTS(SELECT 1 FROM stg.localizacao_carga_record WHERE stage_record_id=@stage_record_id)
        INSERT stg.localizacao_carga_record(
            stage_record_id,execution_id,source_key,source_key_wire_type,payload_json,
            field_presence_json,validation_disposition,quarantine_reason_code,
            service_at_raw,service_at_utc,service_at_presence,
            service_at_parse_state,invoices_volumes_raw,invoices_volumes_typed,
            invoices_volumes_presence,invoices_volumes_parse_state,taxed_weight_raw,
            taxed_weight_typed,taxed_weight_presence,taxed_weight_parse_state,
            invoices_value_raw,invoices_value_typed,invoices_value_presence,
            invoices_value_parse_state,total_raw,total_typed,total_presence,total_parse_state,
            status_raw,status_normalized,status_presence,status_catalog_version,status_terminal,
            status_branch_nickname,status_branch_nickname_presence,
            status_branch_nickname_provenance,freight_candidate_presence,
            freight_candidate_provenance,freight_candidate_state,freshness_at_utc,
            freshness_origin,partition_basis,overlap_policy_version,attribute_hash,observed_at_utc
        ) VALUES(
            @stage_record_id,@execution_id,@source_key,
            CASE WHEN @source_key IS NULL THEN NULL ELSE N'INTEGER' END,@payload_json,
            @field_presence_json,@validation_disposition,@quarantine_reason_code,
            @service_at_raw,@service_at_utc,@service_at_presence,
            @service_at_parse_state,@invoices_volumes_raw,@invoices_volumes_typed,
            @invoices_volumes_presence,@invoices_volumes_parse_state,@taxed_weight_raw,
            @taxed_weight_typed,@taxed_weight_presence,@taxed_weight_parse_state,
            @invoices_value_raw,@invoices_value_typed,@invoices_value_presence,
            @invoices_value_parse_state,@total_raw,@total_typed,@total_presence,@total_parse_state,
            @status_raw,@status_normalized,@status_presence,
            CASE WHEN @validation_disposition=N'VALID'
                 THEN N'localizacao-cargas-status-v1' END,
            @status_terminal,NULL,
            CASE WHEN @validation_disposition=N'VALID' THEN N'ABSENT' END,
            CASE WHEN @validation_disposition=N'VALID' THEN N'UNSOURCED_LEGACY' END,
            CASE WHEN @validation_disposition=N'VALID' THEN N'VALUE' END,
            CASE WHEN @validation_disposition=N'VALID'
                 THEN N'LOCAL_ROOT_SOURCE_KEY_ONLY_NO_RELATION' END,
            CASE WHEN @validation_disposition=N'VALID'
                 THEN N'EXACT_SCOPED_ALIAS_CANDIDATE_UNRESOLVED' END,
            CASE WHEN @validation_disposition=N'VALID' THEN @service_at_utc END,
            CASE WHEN @validation_disposition=N'VALID' THEN N'SERVICE_AT' END,
            CASE WHEN @validation_disposition=N'VALID' THEN N'freights.service_at' END,
            CASE WHEN @validation_disposition=N'VALID'
                 THEN N'localizacao-cargas-service-at-overlap-v1' END,
            @attribute_hash,@observed_at_utc
        );
    IF @validation_disposition=N'QUARANTINE'
    BEGIN
        COMMIT TRANSACTION;
        RETURN;
    END;
    COMMIT TRANSACTION;
END;
GO

CREATE OR ALTER TRIGGER ctl.trg_localizacao_carga_prepare_candidate_set
ON ctl.execution_promotion_result
AFTER INSERT
AS
BEGIN
    SET NOCOUNT ON;
    INSERT ctl.localizacao_carga_promotion_result(
        execution_id,generic_candidate_rows,typed_candidate_rows,conflicting_root_keys,
        validation_state,validated_at_utc
    )
    SELECT inserted.execution_id,inserted.candidate_rows,COALESCE(typed.rows_count,0),
           COALESCE(conflicts.rows_count,0),
           CASE WHEN inserted.candidate_rows=COALESCE(typed.rows_count,0)
                  AND COALESCE(conflicts.rows_count,0)=0
                  AND inserted.quarantined_root_keys=0
                THEN N'PASSED' ELSE N'BLOCKED' END,SYSUTCDATETIME()
    FROM inserted
    JOIN ctl.execution_attempt attempt ON attempt.execution_id=inserted.execution_id
    JOIN ctl.execution_partition partition ON partition.partition_id=attempt.partition_id
    OUTER APPLY(
        SELECT COUNT_BIG(*) rows_count
        FROM stg.execution_candidate candidate
        JOIN stg.localizacao_carga_record typed_record
          ON typed_record.stage_record_id=candidate.winner_stage_record_id
        WHERE candidate.execution_id=inserted.execution_id
    ) typed
    OUTER APPLY(
        SELECT COUNT_BIG(*) rows_count FROM(
            SELECT source_key
            FROM stg.localizacao_carga_record
            WHERE execution_id=inserted.execution_id
            GROUP BY source_key,freshness_at_utc
            HAVING COUNT(DISTINCT attribute_hash)>1
        ) divergent
    ) conflicts
    WHERE partition.entity_name=N'localizacao_cargas';
END;
GO

CREATE OR ALTER PROCEDURE core.usp_apply_reconcile_publish_localizacao_cargas
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
    IF NOT EXISTS(
        SELECT 1 FROM ctl.localizacao_carga_promotion_result WITH(UPDLOCK,HOLDLOCK)
        WHERE execution_id=@execution_id AND validation_state=N'PASSED'
    ) THROW 52220,N'Candidate set tipado de Localização não está apto.',1;
    DECLARE @environment_name NVARCHAR(32),@source_instance NVARCHAR(128),
            @tenant_scope NVARCHAR(128),@entity_name NVARCHAR(128);
    SELECT @environment_name=partition.environment_name,
           @source_instance=partition.source_instance,@tenant_scope=partition.tenant_scope,
           @entity_name=partition.entity_name
    FROM ctl.execution_attempt attempt WITH(UPDLOCK,HOLDLOCK)
    JOIN ctl.execution_partition partition WITH(HOLDLOCK)
      ON partition.partition_id=attempt.partition_id
    WHERE attempt.execution_id=@execution_id;
    IF @entity_name<>N'localizacao_cargas'
       OR UPPER(@source_instance) IN(N'DEFAULT',N'GLOBAL',N'SINGLETON')
       OR UPPER(@tenant_scope) IN(N'DEFAULT',N'GLOBAL',N'SINGLETON')
        THROW 52221,N'Escopo de promoção de Localização inválido.',1;

    IF EXISTS(
        SELECT 1
        FROM stg.execution_candidate candidate
        JOIN stg.localizacao_carga_record typed_record
          ON typed_record.stage_record_id=candidate.winner_stage_record_id
        JOIN core.localizacao_cargas current_record
          WITH(UPDLOCK,HOLDLOCK,INDEX(UQ_core_localizacao_cargas_source))
          ON current_record.environment_name=@environment_name
         AND current_record.source_instance=@source_instance
         AND current_record.tenant_scope=@tenant_scope
         AND current_record.entity_name=N'localizacao_cargas'
         AND current_record.source_key=candidate.source_key
        WHERE candidate.execution_id=@execution_id
          AND current_record.freshness_at_utc=typed_record.freshness_at_utc
          AND current_record.attribute_hash<>typed_record.attribute_hash
    ) THROW 52222,N'EQUAL_FRESHNESS_CONFLICT: Localização exige quarentena.',1;

    DECLARE @effective TABLE(
        source_key NVARCHAR(256) COLLATE Latin1_General_100_BIN2 NOT NULL PRIMARY KEY,
        stage_record_id BIGINT NOT NULL,
        payload_json NVARCHAR(MAX) NULL,field_presence_json NVARCHAR(MAX) NULL,
        invoices_volumes_raw NVARCHAR(MAX) NULL,invoices_volumes_typed INT NULL,
        taxed_weight_raw NVARCHAR(MAX) NULL,taxed_weight_typed DECIMAL(38,9) NULL,
        invoices_value_raw NVARCHAR(MAX) NULL,invoices_value_typed DECIMAL(38,9) NULL,
        total_raw NVARCHAR(MAX) NULL,total_typed DECIMAL(38,9) NULL,
        status_raw NVARCHAR(MAX) NULL,status_normalized NVARCHAR(MAX) NOT NULL,
        status_terminal BIT NOT NULL
    );
    INSERT @effective(
        source_key,stage_record_id,payload_json,field_presence_json,
        invoices_volumes_raw,invoices_volumes_typed,
        taxed_weight_raw,taxed_weight_typed,invoices_value_raw,invoices_value_typed,
        total_raw,total_typed,status_raw,status_normalized,status_terminal
    )
    SELECT candidate.source_key,typed_record.stage_record_id,NULL,NULL,
      CASE typed_record.invoices_volumes_presence WHEN N'ABSENT'
        THEN current_record.invoices_volumes_raw ELSE typed_record.invoices_volumes_raw END,
      CASE typed_record.invoices_volumes_presence WHEN N'ABSENT'
        THEN current_record.invoices_volumes_typed ELSE typed_record.invoices_volumes_typed END,
      CASE typed_record.taxed_weight_presence WHEN N'ABSENT'
        THEN current_record.taxed_weight_raw ELSE typed_record.taxed_weight_raw END,
      CASE typed_record.taxed_weight_presence WHEN N'ABSENT'
        THEN current_record.taxed_weight_typed ELSE typed_record.taxed_weight_typed END,
      CASE typed_record.invoices_value_presence WHEN N'ABSENT'
        THEN current_record.invoices_value_raw ELSE typed_record.invoices_value_raw END,
      CASE typed_record.invoices_value_presence WHEN N'ABSENT'
        THEN current_record.invoices_value_typed ELSE typed_record.invoices_value_typed END,
      CASE typed_record.total_presence WHEN N'ABSENT'
        THEN current_record.total_raw ELSE typed_record.total_raw END,
      CASE typed_record.total_presence WHEN N'ABSENT'
        THEN current_record.total_typed ELSE typed_record.total_typed END,
      CASE typed_record.status_presence WHEN N'ABSENT'
        THEN current_record.status_raw ELSE typed_record.status_raw END,
      CASE typed_record.status_presence WHEN N'ABSENT'
        THEN COALESCE(current_record.status_normalized,typed_record.status_normalized)
        ELSE typed_record.status_normalized END,
      CASE typed_record.status_presence WHEN N'ABSENT'
        THEN COALESCE(current_record.status_terminal,typed_record.status_terminal)
        ELSE typed_record.status_terminal END
    FROM stg.execution_candidate candidate
    JOIN stg.localizacao_carga_record typed_record
      ON typed_record.stage_record_id=candidate.winner_stage_record_id
    LEFT JOIN core.localizacao_cargas current_record
      WITH(UPDLOCK,HOLDLOCK,INDEX(UQ_core_localizacao_cargas_source))
      ON current_record.environment_name=@environment_name
     AND current_record.source_instance=@source_instance
     AND current_record.tenant_scope=@tenant_scope
     AND current_record.entity_name=N'localizacao_cargas'
     AND current_record.source_key=candidate.source_key
    WHERE candidate.execution_id=@execution_id;

    -- Reducer uniforme dos 17 paths: ABSENT conserva o envelope corrente; NULL e VALUE
    -- aplicam o envelope observado. Os dois paths obrigatórios nunca chegam ABSENT em VALID.
    DECLARE @effective_field_catalog TABLE(
        field_ordinal TINYINT NOT NULL PRIMARY KEY,
        field_name NVARCHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL UNIQUE
    );
    INSERT @effective_field_catalog VALUES
        (1,N'corporation_sequence_number'),(2,N'type'),(3,N'service_at'),
        (4,N'invoices_volumes'),(5,N'taxed_weight'),(6,N'invoices_value'),
        (7,N'total'),(8,N'service_type'),(9,N'fit_crn_psn_nickname'),
        (10,N'fit_dpn_delivery_prediction_at'),(11,N'fit_dyn_name'),
        (12,N'fit_dyn_drt_nickname'),(13,N'fit_fsn_name'),(14,N'fit_fln_status'),
        (15,N'fit_fln_cln_nickname'),(16,N'fit_o_n_name'),
        (17,N'fit_o_n_drt_nickname');
    DECLARE @effective_field TABLE(
        source_key NVARCHAR(256) COLLATE Latin1_General_100_BIN2 NOT NULL,
        field_ordinal TINYINT NOT NULL,
        field_name NVARCHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL,
        field_json NVARCHAR(MAX) NOT NULL,
        PRIMARY KEY(source_key,field_ordinal)
    );
    INSERT @effective_field(source_key,field_ordinal,field_name,field_json)
    SELECT candidate.source_key,catalog.field_ordinal,catalog.field_name,
        CASE WHEN JSON_VALUE(typed_field.field_json,N'$.presence')
                       COLLATE Latin1_General_100_BIN2
                       =N'ABSENT' COLLATE Latin1_General_100_BIN2
                   AND current_record.localizacao_carga_id IS NOT NULL
             THEN current_field.field_json ELSE typed_field.field_json END
    FROM stg.execution_candidate candidate
    JOIN stg.localizacao_carga_record typed_record
      ON typed_record.stage_record_id=candidate.winner_stage_record_id
    CROSS JOIN @effective_field_catalog catalog
    CROSS APPLY(SELECT JSON_QUERY(typed_record.field_presence_json,
        CONCAT(N'$.fields.',catalog.field_name)) AS field_json) typed_field
    LEFT JOIN core.localizacao_cargas current_record
      WITH(UPDLOCK,HOLDLOCK,INDEX(UQ_core_localizacao_cargas_source))
      ON current_record.environment_name=@environment_name
     AND current_record.source_instance=@source_instance
     AND current_record.tenant_scope=@tenant_scope
     AND current_record.entity_name=N'localizacao_cargas'
     AND current_record.source_key=candidate.source_key
    OUTER APPLY(SELECT JSON_QUERY(current_record.field_presence_json,
        CONCAT(N'$.fields.',catalog.field_name)) AS field_json) current_field
    WHERE candidate.execution_id=@execution_id;

    ;WITH envelope AS(
        SELECT field.source_key,
            STRING_AGG(CONVERT(NVARCHAR(MAX),CONCAT(
                N'"' COLLATE Latin1_General_100_BIN2,
                STRING_ESCAPE(field.field_name COLLATE Latin1_General_100_BIN2,'json')
                    COLLATE Latin1_General_100_BIN2,
                N'":' COLLATE Latin1_General_100_BIN2,
                field.field_json COLLATE Latin1_General_100_BIN2))
                    COLLATE Latin1_General_100_BIN2,
                N',' COLLATE Latin1_General_100_BIN2)
                WITHIN GROUP(ORDER BY field.field_ordinal) AS fields_json
        FROM @effective_field field GROUP BY field.source_key
    ), payload AS(
        SELECT field.source_key,
            STRING_AGG(CONVERT(NVARCHAR(MAX),CONCAT(
                N'"' COLLATE Latin1_General_100_BIN2,
                STRING_ESCAPE(field.field_name COLLATE Latin1_General_100_BIN2,'json')
                    COLLATE Latin1_General_100_BIN2,
                N'":' COLLATE Latin1_General_100_BIN2,
                CASE raw_property.[type]
                    WHEN 0 THEN N'null' COLLATE Latin1_General_100_BIN2
                    WHEN 1 THEN CONCAT(
                        N'"' COLLATE Latin1_General_100_BIN2,
                        STRING_ESCAPE(raw_property.[value] COLLATE Latin1_General_100_BIN2,'json')
                            COLLATE Latin1_General_100_BIN2,
                        N'"' COLLATE Latin1_General_100_BIN2)
                            COLLATE Latin1_General_100_BIN2
                    WHEN 2 THEN raw_property.[value] COLLATE Latin1_General_100_BIN2
                    WHEN 3 THEN LOWER(raw_property.[value] COLLATE Latin1_General_100_BIN2)
                        COLLATE Latin1_General_100_BIN2
                END COLLATE Latin1_General_100_BIN2))
                    COLLATE Latin1_General_100_BIN2,
                N',' COLLATE Latin1_General_100_BIN2)
                WITHIN GROUP(ORDER BY field.field_ordinal) AS payload_fields_json
        FROM @effective_field field
        CROSS APPLY OPENJSON(field.field_json) raw_property
        WHERE raw_property.[key] COLLATE Latin1_General_100_BIN2
                =N'raw' COLLATE Latin1_General_100_BIN2
          AND JSON_VALUE(field.field_json,N'$.presence')
                COLLATE Latin1_General_100_BIN2
                <>N'ABSENT' COLLATE Latin1_General_100_BIN2
        GROUP BY field.source_key
    )
    UPDATE effective SET
        field_presence_json=CONCAT(
            N'{"fields":{' COLLATE Latin1_General_100_BIN2,
            envelope.fields_json COLLATE Latin1_General_100_BIN2,
            N'},"policy":' COLLATE Latin1_General_100_BIN2,
            JSON_QUERY(typed_record.field_presence_json,N'$.policy')
                COLLATE Latin1_General_100_BIN2,
            N'}' COLLATE Latin1_General_100_BIN2),
        payload_json=CONCAT(
            N'{' COLLATE Latin1_General_100_BIN2,
            payload.payload_fields_json COLLATE Latin1_General_100_BIN2,
            N'}' COLLATE Latin1_General_100_BIN2)
    FROM @effective effective
    JOIN envelope ON envelope.source_key=effective.source_key
    JOIN payload ON payload.source_key=effective.source_key
    JOIN stg.localizacao_carga_record typed_record
      ON typed_record.stage_record_id=effective.stage_record_id;

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

    INSERT core.localizacao_cargas(
        record_state_id,environment_name,source_instance,tenant_scope,entity_name,source_key,
        source_key_wire_type,payload_json,field_presence_json,service_at_raw,service_at_utc,
        invoices_volumes_raw,invoices_volumes_typed,taxed_weight_raw,taxed_weight_typed,
        invoices_value_raw,invoices_value_typed,total_raw,total_typed,status_raw,
        status_normalized,status_catalog_version,status_terminal,status_branch_nickname,
        status_branch_nickname_presence,status_branch_nickname_provenance,
        freight_candidate_presence,freight_candidate_provenance,freight_candidate_state,
        freshness_at_utc,freshness_origin,partition_basis,overlap_policy_version,
        attribute_hash,active,first_seen_execution_id,last_seen_execution_id,
        first_seen_at_utc,last_seen_at_utc
    )
    SELECT application.record_state_id,@environment_name,@source_instance,@tenant_scope,
        N'localizacao_cargas',typed_record.source_key,N'INTEGER',effective.payload_json,
        effective.field_presence_json,typed_record.service_at_raw,typed_record.service_at_utc,
        effective.invoices_volumes_raw,effective.invoices_volumes_typed,
        effective.taxed_weight_raw,effective.taxed_weight_typed,effective.invoices_value_raw,
        effective.invoices_value_typed,effective.total_raw,effective.total_typed,
        effective.status_raw,effective.status_normalized,N'localizacao-cargas-status-v1',
        effective.status_terminal,NULL,N'ABSENT',N'UNSOURCED_LEGACY',N'VALUE',
        N'LOCAL_ROOT_SOURCE_KEY_ONLY_NO_RELATION',
        N'EXACT_SCOPED_ALIAS_CANDIDATE_UNRESOLVED',typed_record.freshness_at_utc,N'SERVICE_AT',
        N'freights.service_at',N'localizacao-cargas-service-at-overlap-v1',
        typed_record.attribute_hash,CONVERT(BIT,1),@execution_id,@execution_id,
        SYSUTCDATETIME(),SYSUTCDATETIME()
    FROM stg.execution_candidate candidate
    JOIN stg.localizacao_carga_record typed_record
      ON typed_record.stage_record_id=candidate.winner_stage_record_id
    JOIN @effective effective ON effective.source_key=candidate.source_key
    JOIN recon.execution_candidate_application application
      ON application.execution_id=candidate.execution_id
     AND application.source_key=candidate.source_key
    WHERE candidate.execution_id=@execution_id
      AND NOT EXISTS(
          SELECT 1 FROM core.localizacao_cargas current_record
            WITH(UPDLOCK,HOLDLOCK,INDEX(UQ_core_localizacao_cargas_source))
          WHERE current_record.environment_name=@environment_name
            AND current_record.source_instance=@source_instance
            AND current_record.tenant_scope=@tenant_scope
            AND current_record.entity_name=N'localizacao_cargas'
            AND current_record.source_key=candidate.source_key
      );

    UPDATE current_record SET
        payload_json=effective.payload_json,field_presence_json=effective.field_presence_json,
        service_at_raw=typed_record.service_at_raw,service_at_utc=typed_record.service_at_utc,
        invoices_volumes_raw=effective.invoices_volumes_raw,
        invoices_volumes_typed=effective.invoices_volumes_typed,
        taxed_weight_raw=effective.taxed_weight_raw,taxed_weight_typed=effective.taxed_weight_typed,
        invoices_value_raw=effective.invoices_value_raw,
        invoices_value_typed=effective.invoices_value_typed,
        total_raw=effective.total_raw,total_typed=effective.total_typed,
        status_raw=effective.status_raw,status_normalized=effective.status_normalized,
        status_terminal=effective.status_terminal,
        freshness_at_utc=typed_record.freshness_at_utc,attribute_hash=typed_record.attribute_hash,
        last_seen_execution_id=@execution_id,last_seen_at_utc=SYSUTCDATETIME()
    FROM core.localizacao_cargas current_record
    JOIN stg.execution_candidate candidate ON candidate.source_key=current_record.source_key
    JOIN stg.localizacao_carga_record typed_record
      ON typed_record.stage_record_id=candidate.winner_stage_record_id
    JOIN @effective effective ON effective.source_key=candidate.source_key
    WHERE candidate.execution_id=@execution_id
      AND current_record.environment_name=@environment_name
      AND current_record.source_instance=@source_instance
      AND current_record.tenant_scope=@tenant_scope
      AND current_record.entity_name=N'localizacao_cargas'
      AND typed_record.freshness_at_utc>current_record.freshness_at_utc;

    UPDATE current_record SET last_seen_execution_id=@execution_id,last_seen_at_utc=SYSUTCDATETIME()
    FROM core.localizacao_cargas current_record
    JOIN stg.execution_candidate candidate ON candidate.source_key=current_record.source_key
    JOIN recon.execution_candidate_application application
      ON application.execution_id=candidate.execution_id
     AND application.source_key=candidate.source_key
    WHERE candidate.execution_id=@execution_id
      AND current_record.environment_name=@environment_name
      AND current_record.source_instance=@source_instance
      AND current_record.tenant_scope=@tenant_scope
      AND current_record.entity_name=N'localizacao_cargas'
      AND application.application_disposition IN(N'NO_OP',N'STALE_NO_OP');

    INSERT recon.localizacao_carga_root_presence_observation(
        execution_id,localizacao_carga_id,observed_at_utc,
        snapshot_completeness,absence_evaluation
    )
    SELECT @execution_id,current_record.localizacao_carga_id,SYSUTCDATETIME(),
           N'BLOCKED_NO_COMPLETENESS_PROOF',N'NOT_EVALUATED_PRUNE_DISABLED'
    FROM core.localizacao_cargas current_record
    JOIN stg.execution_candidate candidate ON candidate.source_key=current_record.source_key
    WHERE candidate.execution_id=@execution_id
      AND current_record.environment_name=@environment_name
      AND current_record.source_instance=@source_instance
      AND current_record.tenant_scope=@tenant_scope
      AND current_record.entity_name=N'localizacao_cargas'
      AND NOT EXISTS(
          SELECT 1 FROM recon.localizacao_carga_root_presence_observation observation
          WHERE observation.execution_id=@execution_id
            AND observation.localizacao_carga_id=current_record.localizacao_carga_id
      );
    COMMIT TRANSACTION;
    SELECT execution_id,candidate_rows,inserted_rows,updated_rows,reactivated_rows,
           noop_rows,stale_noop_rows,reconciled_at_utc,published_at_utc,
           incremental_frontier_before_utc,incremental_frontier_after_utc
    FROM @common_result;
END;
GO

EXEC dbo.usp_publish_v2_procedure_grant
    N'stg',N'usp_stage_localizacao_carga_record',N'v2_runtime';
EXEC dbo.usp_publish_v2_procedure_grant
    N'core',N'usp_apply_reconcile_publish_localizacao_cargas',N'v2_runtime';
DENY INSERT,UPDATE,DELETE,SELECT ON SCHEMA::stg TO v2_runtime;
DENY INSERT,UPDATE,DELETE,SELECT ON SCHEMA::core TO v2_runtime;
DENY INSERT,UPDATE,DELETE,SELECT ON SCHEMA::recon TO v2_runtime;
GO
