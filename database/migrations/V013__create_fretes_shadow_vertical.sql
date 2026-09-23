-- Fretes 6389 em shadow base (V2-011). Sem pub, sweep, relação ou paridade.
-- V2_046A_NOT_REQUIRED_FOR_BASE_SHADOW;
-- V2_046A/V2_046B_REQUIRED_ONLY_FOR_RELATIONAL/PARITY_GATES.
SET XACT_ABORT ON;
SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;

CREATE TABLE stg.frete_record (
    stage_record_id BIGINT NOT NULL,
    execution_id UNIQUEIDENTIFIER NOT NULL,
    source_key NVARCHAR(256) COLLATE Latin1_General_100_BIN2 NOT NULL,
    source_key_wire_type NVARCHAR(16) COLLATE Latin1_General_100_BIN2 NOT NULL,
    payload_json NVARCHAR(MAX) NOT NULL,
    field_presence_json NVARCHAR(MAX) NOT NULL,
    business_alias_json NVARCHAR(MAX) NOT NULL,
    status_raw NVARCHAR(255) NULL,
    status_code NVARCHAR(32) COLLATE Latin1_General_100_BIN2 NULL,
    status_label NVARCHAR(64) NULL,
    status_catalog_version NVARCHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL,
    terminal BIT NOT NULL,
    freshness_evidence_json NVARCHAR(MAX) NOT NULL,
    freshness_at_utc DATETIME2(3) NOT NULL,
    freshness_origin NVARCHAR(32) COLLATE Latin1_General_100_BIN2 NOT NULL,
    service_at_utc DATETIME2(3) NULL,
    partition_basis NVARCHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL,
    overlap_policy_version NVARCHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL,
    cte_finalizations_json NVARCHAR(MAX) NOT NULL,
    financial_json NVARCHAR(MAX) NOT NULL,
    relation_candidates_json NVARCHAR(MAX) NOT NULL,
    attribute_hash CHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL,
    observed_at_utc DATETIME2(3) NOT NULL,
    CONSTRAINT PK_stg_frete_record PRIMARY KEY CLUSTERED(stage_record_id),
    CONSTRAINT FK_stg_frete_record_stage FOREIGN KEY(stage_record_id,execution_id,source_key)
        REFERENCES stg.execution_record(stage_record_id,execution_id,source_key),
    CONSTRAINT UQ_stg_frete_record_execution_stage UNIQUE(execution_id,stage_record_id),
    CONSTRAINT CK_stg_frete_identity CHECK(
        source_key_wire_type=N'INTEGER'
        AND source_key LIKE N'INTEGER:[1-9]%'
        AND SUBSTRING(source_key,9,256) NOT LIKE N'%[^0-9]%'
        AND TRY_CONVERT(BIGINT,SUBSTRING(source_key,9,256))>CONVERT(BIGINT,0)
        AND source_key=CONCAT(N'INTEGER:',CONVERT(NVARCHAR(20),TRY_CONVERT(BIGINT,SUBSTRING(source_key,9,256))))
    ),
    CONSTRAINT CK_stg_frete_json CHECK(
        ISJSON(payload_json)=1 AND ISJSON(field_presence_json)=1
        AND ISJSON(business_alias_json)=1 AND ISJSON(freshness_evidence_json)=1
        AND ISJSON(cte_finalizations_json)=1 AND ISJSON(financial_json)=1
        AND ISJSON(relation_candidates_json)=1
    ),
    CONSTRAINT CK_stg_frete_status CHECK(
        status_catalog_version=N'fretes-status-v1'
        AND ((status_code IS NULL AND status_label IS NULL AND terminal=0)
          OR (status_code IN(N'finished',N'done') AND status_label=N'finalizado' AND terminal=1)
          OR (status_code IN(N'canceled',N'cancelled') AND status_label=N'cancelada' AND terminal=1))
    ),
    CONSTRAINT CK_stg_frete_freshness CHECK(
        freshness_origin IN(N'CTE_CREATED_AT',N'CTE_ISSUED_AT',N'CRIADO_EM',N'SERVICO_EM')
        AND JSON_VALUE(freshness_evidence_json,N'$.precedence')=
            N'cte_created_at>cte_issued_at>criado_em>servico_em'
        AND JSON_VALUE(freshness_evidence_json,N'$.updated_at')=N'IGNORED_UNVERIFIED'
        AND JSON_VALUE(freshness_evidence_json,N'$.evidenceScope')=
            N'SYNTHETIC_V09_NOT_SOURCE_CONTRACT_EVIDENCE'
    ),
    CONSTRAINT CK_stg_frete_partition CHECK(
        partition_basis=N'freights.service_at'
        AND overlap_policy_version=N'fretes-service-at-overlap-v1'
    ),
    CONSTRAINT CK_stg_frete_hash CHECK(
        LEN(attribute_hash)=64 AND attribute_hash NOT LIKE '%[^0-9A-Fa-f]%'
    )
);
GO
CREATE INDEX IX_stg_frete_execution_source_freshness
    ON stg.frete_record(execution_id,source_key,freshness_at_utc DESC,stage_record_id)
    INCLUDE(attribute_hash,terminal,status_code,service_at_utc);
GO

CREATE TABLE stg.frete_performance_observation (
    stage_record_id BIGINT NOT NULL,
    execution_id UNIQUEIDENTIFIER NOT NULL,
    performance_evidence_json NVARCHAR(MAX) NOT NULL,
    official_presence NVARCHAR(8) COLLATE Latin1_General_100_BIN2 NOT NULL,
    fallback_presence NVARCHAR(8) COLLATE Latin1_General_100_BIN2 NOT NULL,
    performance_at_utc DATETIME2(3) NULL,
    performance_origin NVARCHAR(32) COLLATE Latin1_General_100_BIN2 NULL,
    observed_at_utc DATETIME2(3) NOT NULL,
    CONSTRAINT PK_stg_frete_performance_observation PRIMARY KEY CLUSTERED(stage_record_id),
    CONSTRAINT FK_stg_frete_performance_record FOREIGN KEY(stage_record_id)
        REFERENCES stg.frete_record(stage_record_id),
    CONSTRAINT FK_stg_frete_performance_execution FOREIGN KEY(execution_id)
        REFERENCES ctl.execution_attempt(execution_id),
    CONSTRAINT CK_stg_frete_performance_json CHECK(ISJSON(performance_evidence_json)=1),
    CONSTRAINT CK_stg_frete_performance_presence CHECK(
        official_presence IN(N'ABSENT',N'NULL',N'VALUE')
        AND fallback_presence IN(N'ABSENT',N'NULL',N'VALUE')
        AND ((performance_at_utc IS NULL AND performance_origin IS NULL)
          OR (performance_at_utc IS NOT NULL
              AND performance_origin IN(N'OFFICIAL_6389',N'FINISHED_AT_FALLBACK')))
        AND (official_presence<>N'VALUE' OR performance_origin=N'OFFICIAL_6389')
        AND (official_presence=N'VALUE' OR fallback_presence<>N'VALUE'
             OR performance_origin=N'FINISHED_AT_FALLBACK')
    )
);
GO

CREATE TABLE stg.frete_sidecar_observation (
    stage_record_id BIGINT NOT NULL,
    execution_id UNIQUEIDENTIFIER NOT NULL,
    sidecar_catalog_version NVARCHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL,
    sidecar_json NVARCHAR(MAX) NOT NULL,
    edge_count SMALLINT NOT NULL,
    observed_at_utc DATETIME2(3) NOT NULL,
    CONSTRAINT PK_stg_frete_sidecar_observation PRIMARY KEY CLUSTERED(stage_record_id),
    CONSTRAINT FK_stg_frete_sidecar_record FOREIGN KEY(stage_record_id)
        REFERENCES stg.frete_record(stage_record_id),
    CONSTRAINT FK_stg_frete_sidecar_execution FOREIGN KEY(execution_id)
        REFERENCES ctl.execution_attempt(execution_id),
    CONSTRAINT CK_stg_frete_sidecar_values CHECK(
        sidecar_catalog_version=N'fretes-graphql-sidecar-v1'
        AND ISJSON(sidecar_json)=1 AND edge_count BETWEEN 0 AND 100
        AND JSON_VALUE(sidecar_json,N'$.relationPolicy')=
            N'PRESERVE_ONLY_V2_046B_OWNS_CROSSWALK'
    )
);
GO

CREATE TABLE ctl.frete_promotion_result (
    execution_id UNIQUEIDENTIFIER NOT NULL,
    generic_candidate_rows BIGINT NOT NULL,
    typed_candidate_rows BIGINT NOT NULL,
    conflicting_root_keys BIGINT NOT NULL,
    performance_rows BIGINT NOT NULL,
    sidecar_rows BIGINT NOT NULL,
    validation_state NVARCHAR(16) COLLATE Latin1_General_100_BIN2 NOT NULL,
    validated_at_utc DATETIME2(3) NOT NULL,
    CONSTRAINT PK_ctl_frete_promotion_result PRIMARY KEY CLUSTERED(execution_id),
    CONSTRAINT FK_ctl_frete_promotion_result_generic FOREIGN KEY(execution_id)
        REFERENCES ctl.execution_promotion_result(execution_id),
    CONSTRAINT CK_ctl_frete_promotion_result CHECK(
        generic_candidate_rows>=0 AND typed_candidate_rows>=0
        AND conflicting_root_keys>=0 AND performance_rows>=0 AND sidecar_rows>=0
        AND validation_state IN(N'PASSED',N'BLOCKED')
        AND ((validation_state=N'PASSED'
              AND generic_candidate_rows=typed_candidate_rows
              AND typed_candidate_rows=performance_rows
              AND typed_candidate_rows=sidecar_rows
              AND conflicting_root_keys=0)
          OR validation_state=N'BLOCKED')
    )
);
GO

CREATE TABLE core.frete (
    frete_id BIGINT IDENTITY(1,1) NOT NULL,
    record_state_id BIGINT NOT NULL,
    environment_name NVARCHAR(32) COLLATE Latin1_General_100_BIN2 NOT NULL,
    source_instance NVARCHAR(128) COLLATE Latin1_General_100_BIN2 NOT NULL,
    tenant_scope NVARCHAR(128) COLLATE Latin1_General_100_BIN2 NOT NULL,
    entity_name NVARCHAR(128) COLLATE Latin1_General_100_BIN2 NOT NULL,
    source_key NVARCHAR(256) COLLATE Latin1_General_100_BIN2 NOT NULL,
    source_key_wire_type NVARCHAR(16) COLLATE Latin1_General_100_BIN2 NOT NULL,
    payload_json NVARCHAR(MAX) NOT NULL,
    field_presence_json NVARCHAR(MAX) NOT NULL,
    business_alias_json NVARCHAR(MAX) NOT NULL,
    status_raw NVARCHAR(255) NULL,
    status_code NVARCHAR(32) COLLATE Latin1_General_100_BIN2 NULL,
    status_label NVARCHAR(64) NULL,
    status_catalog_version NVARCHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL,
    terminal BIT NOT NULL,
    freshness_evidence_json NVARCHAR(MAX) NOT NULL,
    freshness_at_utc DATETIME2(3) NOT NULL,
    freshness_origin NVARCHAR(32) COLLATE Latin1_General_100_BIN2 NOT NULL,
    service_at_utc DATETIME2(3) NULL,
    partition_basis NVARCHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL,
    overlap_policy_version NVARCHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL,
    cte_finalizations_json NVARCHAR(MAX) NOT NULL,
    financial_json NVARCHAR(MAX) NOT NULL,
    relation_candidates_json NVARCHAR(MAX) NOT NULL,
    attribute_hash CHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL,
    active BIT NOT NULL,
    first_seen_execution_id UNIQUEIDENTIFIER NOT NULL,
    last_seen_execution_id UNIQUEIDENTIFIER NOT NULL,
    first_seen_at_utc DATETIME2(3) NOT NULL,
    last_seen_at_utc DATETIME2(3) NOT NULL,
    CONSTRAINT PK_core_frete PRIMARY KEY CLUSTERED(frete_id),
    CONSTRAINT UQ_core_frete_record_state UNIQUE(record_state_id),
    CONSTRAINT UQ_core_frete_source UNIQUE(
        environment_name,source_instance,tenant_scope,entity_name,source_key
    ),
    CONSTRAINT FK_core_frete_record_binding FOREIGN KEY(
        record_state_id,environment_name,source_instance,tenant_scope,entity_name,source_key
    ) REFERENCES core.entity_record_state(
        record_state_id,environment_name,source_instance,tenant_scope,entity_name,source_key
    ),
    CONSTRAINT FK_core_frete_first_execution FOREIGN KEY(first_seen_execution_id)
        REFERENCES ctl.execution_attempt(execution_id),
    CONSTRAINT FK_core_frete_last_execution FOREIGN KEY(last_seen_execution_id)
        REFERENCES ctl.execution_attempt(execution_id),
    CONSTRAINT CK_core_frete_identity CHECK(
        entity_name=N'fretes' AND source_key_wire_type=N'INTEGER'
        AND source_key LIKE N'INTEGER:[1-9]%'
        AND SUBSTRING(source_key,9,256) NOT LIKE N'%[^0-9]%'
        AND TRY_CONVERT(BIGINT,SUBSTRING(source_key,9,256))>CONVERT(BIGINT,0)
        AND source_key=CONCAT(N'INTEGER:',CONVERT(NVARCHAR(20),TRY_CONVERT(BIGINT,SUBSTRING(source_key,9,256))))
    ),
    CONSTRAINT CK_core_frete_json CHECK(
        ISJSON(payload_json)=1 AND ISJSON(field_presence_json)=1
        AND ISJSON(business_alias_json)=1 AND ISJSON(freshness_evidence_json)=1
        AND ISJSON(cte_finalizations_json)=1 AND ISJSON(financial_json)=1
        AND ISJSON(relation_candidates_json)=1
    ),
    CONSTRAINT CK_core_frete_active CHECK(active=1),
    CONSTRAINT CK_core_frete_status CHECK(
        status_catalog_version=N'fretes-status-v1'
        AND ((status_code IS NULL AND status_label IS NULL AND terminal=0)
          OR (status_code IN(N'finished',N'done') AND status_label=N'finalizado' AND terminal=1)
          OR (status_code IN(N'canceled',N'cancelled') AND status_label=N'cancelada' AND terminal=1))
    ),
    CONSTRAINT CK_core_frete_partition CHECK(
        partition_basis=N'freights.service_at'
        AND overlap_policy_version=N'fretes-service-at-overlap-v1'
    )
);
GO
CREATE INDEX IX_core_frete_active_service
    ON core.frete(environment_name,source_instance,tenant_scope,service_at_utc,source_key)
    INCLUDE(frete_id,freshness_at_utc,terminal,status_code) WHERE active=1;
GO

CREATE TABLE core.frete_performance (
    frete_id BIGINT NOT NULL,
    performance_evidence_json NVARCHAR(MAX) NOT NULL,
    performance_at_utc DATETIME2(3) NULL,
    performance_origin NVARCHAR(32) COLLATE Latin1_General_100_BIN2 NULL,
    last_seen_execution_id UNIQUEIDENTIFIER NOT NULL,
    last_seen_at_utc DATETIME2(3) NOT NULL,
    CONSTRAINT PK_core_frete_performance PRIMARY KEY CLUSTERED(frete_id),
    CONSTRAINT FK_core_frete_performance_owner FOREIGN KEY(frete_id)
        REFERENCES core.frete(frete_id),
    CONSTRAINT FK_core_frete_performance_execution FOREIGN KEY(last_seen_execution_id)
        REFERENCES ctl.execution_attempt(execution_id),
    CONSTRAINT CK_core_frete_performance_json CHECK(ISJSON(performance_evidence_json)=1),
    CONSTRAINT CK_core_frete_performance_values CHECK(
        (performance_at_utc IS NULL AND performance_origin IS NULL)
        OR (performance_at_utc IS NOT NULL
            AND performance_origin IN(N'OFFICIAL_6389',N'FINISHED_AT_FALLBACK'))
    )
);
GO

CREATE TABLE recon.frete_coleta_relation_candidate (
    execution_id UNIQUEIDENTIFIER NOT NULL,
    stage_record_id BIGINT NOT NULL,
    source_kind NVARCHAR(32) COLLATE Latin1_General_100_BIN2 NOT NULL,
    frete_source_key NVARCHAR(256) COLLATE Latin1_General_100_BIN2 NOT NULL,
    candidate_presence NVARCHAR(8) COLLATE Latin1_General_100_BIN2 NOT NULL,
    candidate_evidence_json NVARCHAR(MAX) NOT NULL,
    relation_state NVARCHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL,
    observed_at_utc DATETIME2(3) NOT NULL,
    CONSTRAINT PK_recon_frete_coleta_relation_candidate
        PRIMARY KEY CLUSTERED(execution_id,stage_record_id,source_kind),
    CONSTRAINT FK_recon_frete_relation_execution FOREIGN KEY(execution_id)
        REFERENCES ctl.execution_attempt(execution_id),
    CONSTRAINT FK_recon_frete_relation_stage FOREIGN KEY(stage_record_id)
        REFERENCES stg.frete_record(stage_record_id),
    CONSTRAINT CK_recon_frete_relation_values CHECK(
        source_kind IN(N'DATA_EXPORT_6389',N'GRAPHQL_TRANSITIONAL')
        AND candidate_presence IN(N'ABSENT',N'NULL',N'VALUE')
        AND ISJSON(candidate_evidence_json)=1
        AND relation_state=N'APPEND_ONLY_UNRESOLVED_V2_046B'
    )
);
GO

CREATE TABLE recon.frete_terminal_transition_observation (
    execution_id UNIQUEIDENTIFIER NOT NULL,
    frete_id BIGINT NOT NULL,
    observed_status_raw NVARCHAR(255) NULL,
    transition_action NVARCHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL,
    observed_at_utc DATETIME2(3) NOT NULL,
    CONSTRAINT PK_recon_frete_terminal_transition PRIMARY KEY CLUSTERED(execution_id,frete_id),
    CONSTRAINT FK_recon_frete_terminal_execution FOREIGN KEY(execution_id)
        REFERENCES ctl.execution_attempt(execution_id),
    CONSTRAINT FK_recon_frete_terminal_owner FOREIGN KEY(frete_id)
        REFERENCES core.frete(frete_id),
    CONSTRAINT CK_recon_frete_terminal_action CHECK(
        transition_action=N'TERMINAL_REGRESSION_BLOCKED'
    )
);
GO

CREATE TABLE recon.frete_root_presence_observation (
    execution_id UNIQUEIDENTIFIER NOT NULL,
    frete_id BIGINT NOT NULL,
    observed_at_utc DATETIME2(3) NOT NULL,
    snapshot_completeness NVARCHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL,
    absence_evaluation NVARCHAR(32) COLLATE Latin1_General_100_BIN2 NOT NULL,
    CONSTRAINT PK_recon_frete_root_presence PRIMARY KEY CLUSTERED(execution_id,frete_id),
    CONSTRAINT FK_recon_frete_presence_execution FOREIGN KEY(execution_id)
        REFERENCES ctl.execution_attempt(execution_id),
    CONSTRAINT FK_recon_frete_presence_owner FOREIGN KEY(frete_id)
        REFERENCES core.frete(frete_id),
    CONSTRAINT CK_recon_frete_presence_values CHECK(
        snapshot_completeness=N'BLOCKED_NO_COMPLETENESS_PROOF'
        AND absence_evaluation=N'NOT_EVALUATED_PRUNE_DISABLED'
    )
);
GO

CREATE OR ALTER PROCEDURE stg.usp_stage_frete_performance
    @execution_id UNIQUEIDENTIFIER,
    @stage_record_id BIGINT,
    @performance_evidence_json NVARCHAR(MAX),
    @performance_at_utc DATETIME2(3),
    @performance_origin NVARCHAR(32),
    @observed_at_utc DATETIME2(3)
AS
BEGIN
    SET NOCOUNT ON;
    IF @execution_id IS NULL OR @stage_record_id IS NULL OR @observed_at_utc IS NULL
       OR ISJSON(@performance_evidence_json)<>1
       OR JSON_VALUE(@performance_evidence_json,N'$.precedence')<>
            N'OFFICIAL_6389_THEN_FINISHED_AT'
       OR COALESCE(JSON_VALUE(@performance_evidence_json,N'$.official.presence'),N'INVALID')
            NOT IN(N'ABSENT',N'NULL',N'VALUE')
       OR COALESCE(JSON_VALUE(@performance_evidence_json,N'$.fallback.presence'),N'INVALID')
            NOT IN(N'ABSENT',N'NULL',N'VALUE')
       OR ((@performance_at_utc IS NULL AND @performance_origin IS NOT NULL)
           OR (@performance_at_utc IS NOT NULL
               AND @performance_origin NOT IN(N'OFFICIAL_6389',N'FINISHED_AT_FALLBACK')))
       OR (JSON_VALUE(@performance_evidence_json,N'$.official.presence')=N'VALUE'
           AND @performance_origin<>N'OFFICIAL_6389')
       OR (JSON_VALUE(@performance_evidence_json,N'$.official.presence')<>N'VALUE'
           AND JSON_VALUE(@performance_evidence_json,N'$.fallback.presence')=N'VALUE'
           AND @performance_origin<>N'FINISHED_AT_FALLBACK')
        THROW 52101,N'Performance 6389 inválida ou sem proveniência.',1;
    SET XACT_ABORT ON;
    IF NOT EXISTS(
        SELECT 1 FROM stg.frete_record WITH(UPDLOCK,HOLDLOCK)
        WHERE stage_record_id=@stage_record_id AND execution_id=@execution_id
    ) THROW 52102,N'Performance 6389 não pertence à raiz tipada.',1;
    IF EXISTS(
        SELECT 1 FROM stg.frete_performance_observation WITH(UPDLOCK,HOLDLOCK)
        WHERE stage_record_id=@stage_record_id
          AND (performance_evidence_json<>@performance_evidence_json
            OR ISNULL(performance_at_utc,'0001-01-01')<>ISNULL(@performance_at_utc,'0001-01-01')
            OR ISNULL(performance_origin,N'')<>ISNULL(@performance_origin,N''))
    ) THROW 52103,N'Retry de performance 6389 divergente.',1;
    IF NOT EXISTS(
        SELECT 1 FROM stg.frete_performance_observation WHERE stage_record_id=@stage_record_id
    )
        INSERT stg.frete_performance_observation(
            stage_record_id,execution_id,performance_evidence_json,official_presence,
            fallback_presence,performance_at_utc,performance_origin,observed_at_utc
        ) VALUES(
            @stage_record_id,@execution_id,@performance_evidence_json,
            JSON_VALUE(@performance_evidence_json,N'$.official.presence'),
            JSON_VALUE(@performance_evidence_json,N'$.fallback.presence'),
            @performance_at_utc,@performance_origin,@observed_at_utc
        );
END;
GO

CREATE OR ALTER PROCEDURE stg.usp_stage_frete_sidecar
    @execution_id UNIQUEIDENTIFIER,
    @stage_record_id BIGINT,
    @sidecar_json NVARCHAR(MAX),
    @observed_at_utc DATETIME2(3)
AS
BEGIN
    SET NOCOUNT ON;
    DECLARE @edge_count BIGINT=CASE WHEN ISJSON(@sidecar_json)=1
        THEN (SELECT COUNT_BIG(*) FROM OPENJSON(@sidecar_json,N'$.edges')) END;
    IF @execution_id IS NULL OR @stage_record_id IS NULL OR @observed_at_utc IS NULL
       OR ISJSON(@sidecar_json)<>1 OR @edge_count NOT BETWEEN 0 AND 100
       OR JSON_VALUE(@sidecar_json,N'$.catalogVersion')<>N'fretes-graphql-sidecar-v1'
       OR JSON_VALUE(@sidecar_json,N'$.relationPolicy')<>
            N'PRESERVE_ONLY_V2_046B_OWNS_CROSSWALK'
       OR COALESCE(JSON_VALUE(@sidecar_json,N'$.edgesPresence'),N'INVALID')
            NOT IN(N'ABSENT',N'NULL',N'VALUE')
       OR NOT EXISTS(SELECT 1 FROM OPENJSON(@sidecar_json)
                     WHERE [key]=N'edges' AND [type]=4)
       OR NOT EXISTS(SELECT 1 FROM OPENJSON(@sidecar_json)
                     WHERE [key]=N'pageInfo' AND [type]=5)
       OR EXISTS(SELECT 1 FROM OPENJSON(@sidecar_json)
                 WHERE [key] NOT IN(N'catalogVersion',N'relationPolicy',N'edgesPresence',N'edges',N'pageInfo'))
       OR EXISTS(
           SELECT 1 FROM OPENJSON(@sidecar_json,N'$.pageInfo') property_value
           WHERE property_value.[key] NOT IN(N'presence',N'hasNextPage',N'endCursor')
       )
       OR COALESCE(JSON_VALUE(@sidecar_json,N'$.pageInfo.hasNextPage.path'),N'')<>
            N'/freight/pageInfo/hasNextPage'
       OR COALESCE(JSON_VALUE(@sidecar_json,N'$.pageInfo.endCursor.path'),N'')<>
            N'/freight/pageInfo/endCursor'
       OR EXISTS(
           SELECT 1 FROM OPENJSON(@sidecar_json,N'$.edges') edge_value
           CROSS APPLY OPENJSON(edge_value.[value]) property_value
           WHERE property_value.[key] NOT IN(
               N'ordinal',N'id',N'accountingCreditId',N'accountingCreditInstallmentId',
               N'referenceNumber',N'cte',N'total',N'corporationSequenceNumber',
               N'pickItemId',N'pickItemResolution'
           )
       )
       OR EXISTS(
           SELECT 1 FROM OPENJSON(@sidecar_json,N'$.edges') edge_value
           WHERE COALESCE(JSON_VALUE(edge_value.[value],N'$.id.path'),N'')<>
                    N'/freight/edges/node/id'
              OR COALESCE(JSON_VALUE(edge_value.[value],N'$.accountingCreditId.path'),N'')<>
                    N'/freight/edges/node/accountingCreditId'
              OR COALESCE(JSON_VALUE(edge_value.[value],N'$.accountingCreditInstallmentId.path'),N'')<>
                    N'/freight/edges/node/accountingCreditInstallmentId'
              OR COALESCE(JSON_VALUE(edge_value.[value],N'$.referenceNumber.path'),N'')<>
                    N'/freight/edges/node/referenceNumber'
              OR COALESCE(JSON_VALUE(edge_value.[value],N'$.cte.key.path'),N'')<>
                    N'/freight/edges/node/cte/key'
              OR COALESCE(JSON_VALUE(edge_value.[value],N'$.total.path'),N'')<>
                    N'/freight/edges/node/total'
              OR COALESCE(JSON_VALUE(edge_value.[value],N'$.corporationSequenceNumber.path'),N'')<>
                    N'/freight/edges/node/corporationSequenceNumber'
              OR COALESCE(JSON_VALUE(edge_value.[value],N'$.pickItemId.path'),N'')<>
                    N'/freight/edges/node/pickItemId'
              OR COALESCE(JSON_VALUE(edge_value.[value],N'$.pickItemResolution'),N'')<>
                    N'UNRESOLVED_CANDIDATE_ONLY'
       )
        THROW 52111,N'Sidecar de Fretes excede limite ou diverge do catálogo.',1;
    SET XACT_ABORT ON;
    IF NOT EXISTS(
        SELECT 1 FROM stg.frete_record WITH(UPDLOCK,HOLDLOCK)
        WHERE stage_record_id=@stage_record_id AND execution_id=@execution_id
    ) THROW 52112,N'Sidecar de Fretes não pertence à raiz tipada.',1;
    IF EXISTS(
        SELECT 1 FROM stg.frete_sidecar_observation WITH(UPDLOCK,HOLDLOCK)
        WHERE stage_record_id=@stage_record_id AND sidecar_json<>@sidecar_json
    ) THROW 52113,N'Retry de sidecar de Fretes divergente.',1;
    IF NOT EXISTS(
        SELECT 1 FROM stg.frete_sidecar_observation WHERE stage_record_id=@stage_record_id
    )
        INSERT stg.frete_sidecar_observation(
            stage_record_id,execution_id,sidecar_catalog_version,sidecar_json,edge_count,
            observed_at_utc
        ) VALUES(
            @stage_record_id,@execution_id,N'fretes-graphql-sidecar-v1',@sidecar_json,
            CONVERT(SMALLINT,@edge_count),@observed_at_utc
        );
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

CREATE OR ALTER TRIGGER ctl.trg_frete_prepare_candidate_set
ON ctl.execution_promotion_result
AFTER INSERT
AS
BEGIN
    SET NOCOUNT ON;
    INSERT ctl.frete_promotion_result(
        execution_id,generic_candidate_rows,typed_candidate_rows,conflicting_root_keys,
        performance_rows,sidecar_rows,validation_state,validated_at_utc
    )
    SELECT inserted.execution_id,inserted.candidate_rows,COALESCE(typed.rows_count,0),
           COALESCE(conflicts.rows_count,0),COALESCE(performance.rows_count,0),
           COALESCE(sidecar.rows_count,0),
           CASE WHEN inserted.candidate_rows=COALESCE(typed.rows_count,0)
                  AND inserted.candidate_rows=COALESCE(performance.rows_count,0)
                  AND inserted.candidate_rows=COALESCE(sidecar.rows_count,0)
                  AND COALESCE(conflicts.rows_count,0)=0
                  AND inserted.quarantined_root_keys=0
                THEN N'PASSED' ELSE N'BLOCKED' END,SYSUTCDATETIME()
    FROM inserted
    JOIN ctl.execution_attempt attempt ON attempt.execution_id=inserted.execution_id
    JOIN ctl.execution_partition partition ON partition.partition_id=attempt.partition_id
    OUTER APPLY(
        SELECT COUNT_BIG(*) rows_count
        FROM stg.execution_candidate candidate
        JOIN stg.frete_record typed_record
          ON typed_record.stage_record_id=candidate.winner_stage_record_id
        WHERE candidate.execution_id=inserted.execution_id
    ) typed
    OUTER APPLY(
        SELECT COUNT_BIG(*) rows_count
        FROM stg.execution_candidate candidate
        JOIN stg.frete_performance_observation performance_record
          ON performance_record.stage_record_id=candidate.winner_stage_record_id
        WHERE candidate.execution_id=inserted.execution_id
    ) performance
    OUTER APPLY(
        SELECT COUNT_BIG(*) rows_count
        FROM stg.execution_candidate candidate
        JOIN stg.frete_sidecar_observation sidecar_record
          ON sidecar_record.stage_record_id=candidate.winner_stage_record_id
        WHERE candidate.execution_id=inserted.execution_id
    ) sidecar
    OUTER APPLY(
        SELECT COUNT_BIG(*) rows_count FROM(
            SELECT source_key
            FROM stg.frete_record
            WHERE execution_id=inserted.execution_id
            GROUP BY source_key,freshness_at_utc
            HAVING COUNT(DISTINCT attribute_hash)>1
        ) divergent
    ) conflicts
    WHERE partition.entity_name=N'fretes';
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

CREATE OR ALTER PROCEDURE core.usp_apply_reconcile_publish_fretes
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

EXEC dbo.usp_publish_v2_procedure_grant N'stg',N'usp_stage_frete_record',N'v2_runtime';
EXEC dbo.usp_publish_v2_procedure_grant N'stg',N'usp_stage_frete_performance',N'v2_runtime';
EXEC dbo.usp_publish_v2_procedure_grant N'stg',N'usp_stage_frete_sidecar',N'v2_runtime';
EXEC dbo.usp_publish_v2_procedure_grant N'core',N'usp_prepare_frete_candidate_set',N'v2_runtime';
EXEC dbo.usp_publish_v2_procedure_grant N'core',N'usp_apply_reconcile_publish_fretes',N'v2_runtime';
DENY INSERT,UPDATE,DELETE,SELECT ON SCHEMA::stg TO v2_runtime;
DENY INSERT,UPDATE,DELETE,SELECT ON SCHEMA::core TO v2_runtime;
DENY INSERT,UPDATE,DELETE,SELECT ON SCHEMA::recon TO v2_runtime;
GO
