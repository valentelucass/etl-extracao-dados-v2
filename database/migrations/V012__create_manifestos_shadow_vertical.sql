-- Manifestos 6399 em sombra (V2-026). Sem rede, pub, sweep, relação Manifesto--Coleta ou defaults.
SET XACT_ABORT ON;

CREATE TABLE stg.manifesto_observation (
    manifesto_observation_id BIGINT IDENTITY(1,1) NOT NULL,
    execution_id UNIQUEIDENTIFIER NOT NULL,
    input_batch_number INT NOT NULL,
    input_record_ordinal INT NOT NULL,
    source_key NVARCHAR(256) COLLATE Latin1_General_100_BIN2 NULL,
    validation_disposition NVARCHAR(16) COLLATE Latin1_General_100_BIN2 NOT NULL,
    quarantine_reason_code NVARCHAR(64) COLLATE Latin1_General_100_BIN2 NULL,
    payload_json NVARCHAR(MAX) NOT NULL,
    field_presence_json NVARCHAR(MAX) NOT NULL,
    root_fields_json NVARCHAR(MAX) NULL,
    metric_values_json NVARCHAR(MAX) NULL,
    relation_candidates_json NVARCHAR(MAX) NULL,
    mdfe_status_presence NVARCHAR(8) COLLATE Latin1_General_100_BIN2 NULL,
    mdfe_status_value NVARCHAR(50) NULL,
    pick_source_key NVARCHAR(256) COLLATE Latin1_General_100_BIN2 NULL,
    mdfe_key CHAR(44) COLLATE Latin1_General_100_BIN2 NULL,
    mdfe_number NVARCHAR(128) COLLATE Latin1_General_100_BIN2 NULL,
    freshness_at_utc DATETIME2(3) NULL,
    freshness_origin NVARCHAR(32) COLLATE Latin1_General_100_BIN2 NULL,
    competence_json NVARCHAR(MAX) NULL,
    observed_at_utc DATETIME2(3) NOT NULL,
    CONSTRAINT PK_stg_manifesto_observation PRIMARY KEY CLUSTERED (manifesto_observation_id),
    CONSTRAINT UQ_stg_manifesto_observation_batch UNIQUE (execution_id,input_batch_number,input_record_ordinal),
    CONSTRAINT FK_stg_manifesto_observation_execution FOREIGN KEY (execution_id)
        REFERENCES ctl.execution_attempt(execution_id),
    CONSTRAINT CK_stg_manifesto_observation_json CHECK (
        ISJSON(payload_json)=1 AND ISJSON(field_presence_json)=1
        AND (root_fields_json IS NULL OR ISJSON(root_fields_json)=1)
        AND (metric_values_json IS NULL OR ISJSON(metric_values_json)=1)
        AND (relation_candidates_json IS NULL OR ISJSON(relation_candidates_json)=1)
        AND (competence_json IS NULL OR ISJSON(competence_json)=1)
    ),
    CONSTRAINT CK_stg_manifesto_observation_disposition CHECK (
        validation_disposition IN(N'VALID',N'QUARANTINE')
        AND ((validation_disposition=N'VALID' AND source_key LIKE N'INTEGER:%'
              AND source_key LIKE N'INTEGER:[1-9]%' AND SUBSTRING(source_key,9,256) NOT LIKE N'%[^0-9]%'
              AND root_fields_json IS NOT NULL AND metric_values_json IS NOT NULL
              AND relation_candidates_json IS NOT NULL AND freshness_at_utc IS NOT NULL
              AND freshness_origin IN(N'FINISHED_AT',N'CLOSED_AT',N'DEPARTURED_AT',N'CREATED_AT')
              AND quarantine_reason_code IS NULL)
             OR (validation_disposition=N'QUARANTINE' AND quarantine_reason_code IS NOT NULL))
    ),
    CONSTRAINT CK_stg_manifesto_observation_children CHECK (
        (pick_source_key IS NULL OR (pick_source_key LIKE N'INTEGER:[1-9]%'
            AND SUBSTRING(pick_source_key,9,256) NOT LIKE N'%[^0-9]%'))
        AND ((mdfe_key IS NULL AND mdfe_number IS NULL)
             OR (mdfe_key NOT LIKE '%[^0-9]%' AND mdfe_number LIKE N'[1-9]%' AND mdfe_number NOT LIKE N'%[^0-9]%'))
        AND (mdfe_status_presence IS NULL OR mdfe_status_presence IN(N'ABSENT',N'NULL',N'VALUE'))
        AND ((mdfe_status_presence=N'VALUE' AND mdfe_status_value IS NOT NULL)
             OR (mdfe_status_presence IN(N'ABSENT',N'NULL') AND mdfe_status_value IS NULL)
             OR mdfe_status_presence IS NULL)
    )
);
GO
CREATE INDEX IX_stg_manifesto_observation_execution_root
    ON stg.manifesto_observation(execution_id,source_key,freshness_at_utc,manifesto_observation_id);
GO

CREATE TABLE stg.manifesto_reduced_candidate (
    manifesto_candidate_id BIGINT IDENTITY(1,1) NOT NULL,
    execution_id UNIQUEIDENTIFIER NOT NULL,
    source_key NVARCHAR(256) COLLATE Latin1_General_100_BIN2 NOT NULL,
    stage_record_id BIGINT NOT NULL,
    root_payload_json NVARCHAR(MAX) NOT NULL,
    root_presence_json NVARCHAR(MAX) NOT NULL,
    metric_values_json NVARCHAR(MAX) NOT NULL,
    competence_json NVARCHAR(MAX) NOT NULL,
    mdfe_status_presence NVARCHAR(8) COLLATE Latin1_General_100_BIN2 NOT NULL,
    mdfe_status_value NVARCHAR(50) NULL,
    freshness_at_utc DATETIME2(3) NOT NULL,
    freshness_origin NVARCHAR(32) COLLATE Latin1_General_100_BIN2 NOT NULL,
    attribute_hash CHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL,
    presence_hash CHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL,
    reduced_at_utc DATETIME2(3) NOT NULL,
    CONSTRAINT PK_stg_manifesto_reduced_candidate PRIMARY KEY CLUSTERED(manifesto_candidate_id),
    CONSTRAINT UQ_stg_manifesto_reduced_candidate_source UNIQUE(execution_id,source_key),
    CONSTRAINT UQ_stg_manifesto_reduced_stage UNIQUE(stage_record_id),
    CONSTRAINT FK_stg_manifesto_reduced_candidate_execution FOREIGN KEY(execution_id)
        REFERENCES ctl.execution_attempt(execution_id),
    CONSTRAINT FK_stg_manifesto_reduced_candidate_stage FOREIGN KEY(stage_record_id)
        REFERENCES stg.execution_record(stage_record_id),
    CONSTRAINT CK_stg_manifesto_reduced_candidate_values CHECK (
        source_key LIKE N'INTEGER:[1-9]%' AND SUBSTRING(source_key,9,256) NOT LIKE N'%[^0-9]%'
        AND ISJSON(root_payload_json)=1 AND ISJSON(root_presence_json)=1
        AND ISJSON(metric_values_json)=1 AND ISJSON(competence_json)=1
        AND mdfe_status_presence IN(N'ABSENT',N'NULL',N'VALUE')
        AND ((mdfe_status_presence=N'VALUE' AND mdfe_status_value IS NOT NULL)
             OR (mdfe_status_presence IN(N'ABSENT',N'NULL') AND mdfe_status_value IS NULL))
        AND freshness_origin IN(N'FINISHED_AT',N'CLOSED_AT',N'DEPARTURED_AT',N'CREATED_AT')
        AND attribute_hash NOT LIKE '%[^0-9A-Fa-f]%' AND presence_hash NOT LIKE '%[^0-9A-Fa-f]%'
    )
);
GO

CREATE TABLE stg.manifesto_pick_candidate (
    manifesto_candidate_id BIGINT NOT NULL,
    execution_id UNIQUEIDENTIFIER NOT NULL,
    source_key NVARCHAR(256) COLLATE Latin1_General_100_BIN2 NOT NULL,
    pick_source_key NVARCHAR(256) COLLATE Latin1_General_100_BIN2 NOT NULL,
    candidate_presence NVARCHAR(8) COLLATE Latin1_General_100_BIN2 NOT NULL,
    provenance_json NVARCHAR(MAX) NOT NULL,
    CONSTRAINT PK_stg_manifesto_pick_candidate PRIMARY KEY CLUSTERED(manifesto_candidate_id,pick_source_key),
    CONSTRAINT FK_stg_manifesto_pick_candidate_root FOREIGN KEY(manifesto_candidate_id)
        REFERENCES stg.manifesto_reduced_candidate(manifesto_candidate_id),
    CONSTRAINT CK_stg_manifesto_pick_candidate_values CHECK (
        pick_source_key LIKE N'INTEGER:[1-9]%' AND SUBSTRING(pick_source_key,9,256) NOT LIKE N'%[^0-9]%'
        AND candidate_presence=N'VALUE' AND ISJSON(provenance_json)=1
    )
);
GO

CREATE TABLE stg.manifesto_mdfe_candidate (
    manifesto_candidate_id BIGINT NOT NULL,
    execution_id UNIQUEIDENTIFIER NOT NULL,
    source_key NVARCHAR(256) COLLATE Latin1_General_100_BIN2 NOT NULL,
    mdfe_key CHAR(44) COLLATE Latin1_General_100_BIN2 NOT NULL,
    mdfe_number NVARCHAR(128) COLLATE Latin1_General_100_BIN2 NOT NULL,
    provenance_json NVARCHAR(MAX) NOT NULL,
    CONSTRAINT PK_stg_manifesto_mdfe_candidate PRIMARY KEY CLUSTERED(manifesto_candidate_id,mdfe_key),
    CONSTRAINT FK_stg_manifesto_mdfe_candidate_root FOREIGN KEY(manifesto_candidate_id)
        REFERENCES stg.manifesto_reduced_candidate(manifesto_candidate_id),
    CONSTRAINT CK_stg_manifesto_mdfe_candidate_values CHECK (
        mdfe_key NOT LIKE '%[^0-9]%' AND mdfe_number LIKE N'[1-9]%' AND mdfe_number NOT LIKE N'%[^0-9]%'
        AND ISJSON(provenance_json)=1
    )
);
GO

CREATE TABLE ctl.manifesto_promotion_result (
    execution_id UNIQUEIDENTIFIER NOT NULL,
    physical_observation_rows BIGINT NOT NULL,
    reduced_root_rows BIGINT NOT NULL,
    quarantined_observation_rows BIGINT NOT NULL,
    validation_state NVARCHAR(16) COLLATE Latin1_General_100_BIN2 NOT NULL,
    validated_at_utc DATETIME2(3) NOT NULL,
    CONSTRAINT PK_ctl_manifesto_promotion_result PRIMARY KEY CLUSTERED(execution_id),
    CONSTRAINT FK_ctl_manifesto_promotion_result_generic FOREIGN KEY(execution_id)
        REFERENCES ctl.execution_promotion_result(execution_id),
    CONSTRAINT CK_ctl_manifesto_promotion_result_values CHECK (
        physical_observation_rows>=reduced_root_rows AND quarantined_observation_rows>=0
        AND validation_state IN(N'PASSED',N'BLOCKED')
    )
);
GO

CREATE TABLE core.manifesto (
    manifesto_id BIGINT IDENTITY(1,1) NOT NULL,
    record_state_id BIGINT NOT NULL,
    environment_name NVARCHAR(32) COLLATE Latin1_General_100_BIN2 NOT NULL,
    source_instance NVARCHAR(128) COLLATE Latin1_General_100_BIN2 NOT NULL,
    tenant_scope NVARCHAR(128) COLLATE Latin1_General_100_BIN2 NOT NULL,
    entity_name NVARCHAR(128) COLLATE Latin1_General_100_BIN2 NOT NULL,
    source_key NVARCHAR(256) COLLATE Latin1_General_100_BIN2 NOT NULL,
    root_payload_json NVARCHAR(MAX) NOT NULL,
    root_presence_json NVARCHAR(MAX) NOT NULL,
    metric_values_json NVARCHAR(MAX) NOT NULL,
    competence_json NVARCHAR(MAX) NOT NULL,
    mdfe_status_presence NVARCHAR(8) COLLATE Latin1_General_100_BIN2 NOT NULL,
    mdfe_status_value NVARCHAR(50) NULL,
    freshness_at_utc DATETIME2(3) NOT NULL,
    freshness_origin NVARCHAR(32) COLLATE Latin1_General_100_BIN2 NOT NULL,
    attribute_hash CHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL,
    presence_hash CHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL,
    active BIT NOT NULL,
    first_seen_execution_id UNIQUEIDENTIFIER NOT NULL,
    last_seen_execution_id UNIQUEIDENTIFIER NOT NULL,
    CONSTRAINT PK_core_manifesto PRIMARY KEY CLUSTERED(manifesto_id),
    CONSTRAINT UQ_core_manifesto_state UNIQUE(record_state_id),
    CONSTRAINT UQ_core_manifesto_source UNIQUE(environment_name,source_instance,tenant_scope,entity_name,source_key),
    CONSTRAINT FK_core_manifesto_state FOREIGN KEY(record_state_id,environment_name,source_instance,tenant_scope,entity_name,source_key)
        REFERENCES core.entity_record_state(record_state_id,environment_name,source_instance,tenant_scope,entity_name,source_key),
    CONSTRAINT CK_core_manifesto_values CHECK (
        entity_name=N'manifestos' AND active=1 AND source_key LIKE N'INTEGER:%'
        AND source_key LIKE N'INTEGER:[1-9]%' AND SUBSTRING(source_key,9,256) NOT LIKE N'%[^0-9]%'
        AND ISJSON(root_payload_json)=1 AND ISJSON(root_presence_json)=1
        AND ISJSON(metric_values_json)=1 AND ISJSON(competence_json)=1
        AND mdfe_status_presence IN(N'ABSENT',N'NULL',N'VALUE')
        AND ((mdfe_status_presence=N'VALUE' AND mdfe_status_value IS NOT NULL)
             OR (mdfe_status_presence IN(N'ABSENT',N'NULL') AND mdfe_status_value IS NULL))
        AND freshness_origin IN(N'FINISHED_AT',N'CLOSED_AT',N'DEPARTURED_AT',N'CREATED_AT')
    )
);
GO
CREATE INDEX IX_core_manifesto_active_freshness
    ON core.manifesto(environment_name,source_instance,tenant_scope,freshness_at_utc)
    INCLUDE(manifesto_id,source_key) WHERE active=1;
GO

CREATE TABLE core.manifesto_pick (
    manifesto_pick_id BIGINT IDENTITY(1,1) NOT NULL,
    manifesto_id BIGINT NOT NULL,
    pick_source_key NVARCHAR(256) COLLATE Latin1_General_100_BIN2 NOT NULL,
    first_seen_execution_id UNIQUEIDENTIFIER NOT NULL,
    CONSTRAINT PK_core_manifesto_pick PRIMARY KEY CLUSTERED(manifesto_pick_id),
    CONSTRAINT UQ_core_manifesto_pick UNIQUE(manifesto_id,pick_source_key),
    CONSTRAINT FK_core_manifesto_pick_owner FOREIGN KEY(manifesto_id) REFERENCES core.manifesto(manifesto_id),
    CONSTRAINT CK_core_manifesto_pick_values CHECK (
        pick_source_key LIKE N'INTEGER:[1-9]%' AND SUBSTRING(pick_source_key,9,256) NOT LIKE N'%[^0-9]%'
    )
);
GO

CREATE TABLE core.manifesto_mdfe (
    manifesto_mdfe_id BIGINT IDENTITY(1,1) NOT NULL,
    manifesto_id BIGINT NOT NULL,
    mdfe_key CHAR(44) COLLATE Latin1_General_100_BIN2 NOT NULL,
    mdfe_number NVARCHAR(128) COLLATE Latin1_General_100_BIN2 NOT NULL,
    first_seen_execution_id UNIQUEIDENTIFIER NOT NULL,
    CONSTRAINT PK_core_manifesto_mdfe PRIMARY KEY CLUSTERED(manifesto_mdfe_id),
    CONSTRAINT UQ_core_manifesto_mdfe UNIQUE(manifesto_id,mdfe_key),
    CONSTRAINT FK_core_manifesto_mdfe_owner FOREIGN KEY(manifesto_id) REFERENCES core.manifesto(manifesto_id),
    CONSTRAINT CK_core_manifesto_mdfe_values CHECK (
        mdfe_key NOT LIKE '%[^0-9]%' AND mdfe_number LIKE N'[1-9]%' AND mdfe_number NOT LIKE N'%[^0-9]%'
    )
);
GO

-- Evidência append-only do possível elo; não há lookup, FK ou cardinalidade contra Coletas.
CREATE TABLE recon.manifesto_coleta_relation_candidate (
    manifesto_coleta_relation_candidate_id BIGINT IDENTITY(1,1) NOT NULL,
    execution_id UNIQUEIDENTIFIER NOT NULL,
    manifesto_observation_id BIGINT NULL,
    root_source_key NVARCHAR(256) COLLATE Latin1_General_100_BIN2 NULL,
    pick_source_key NVARCHAR(256) COLLATE Latin1_General_100_BIN2 NULL,
    candidate_presence NVARCHAR(8) COLLATE Latin1_General_100_BIN2 NOT NULL,
    provenance_json NVARCHAR(MAX) NOT NULL,
    observed_at_utc DATETIME2(3) NOT NULL,
    CONSTRAINT PK_recon_manifesto_coleta_relation_candidate PRIMARY KEY CLUSTERED(manifesto_coleta_relation_candidate_id),
    CONSTRAINT UQ_recon_manifesto_coleta_relation_candidate_observation UNIQUE(execution_id,manifesto_observation_id),
    CONSTRAINT CK_recon_manifesto_coleta_relation_candidate_values CHECK (
        candidate_presence IN(N'ABSENT',N'NULL',N'VALUE') AND ISJSON(provenance_json)=1
        AND ((candidate_presence=N'VALUE' AND pick_source_key LIKE N'INTEGER:%')
             OR (candidate_presence IN(N'ABSENT',N'NULL') AND pick_source_key IS NULL))
    )
);
GO

CREATE TABLE recon.manifesto_root_presence_observation (
    execution_id UNIQUEIDENTIFIER NOT NULL,
    manifesto_id BIGINT NOT NULL,
    observed_at_utc DATETIME2(3) NOT NULL,
    snapshot_completeness NVARCHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL,
    absence_evaluation NVARCHAR(32) COLLATE Latin1_General_100_BIN2 NOT NULL,
    CONSTRAINT PK_recon_manifesto_root_presence PRIMARY KEY CLUSTERED(execution_id,manifesto_id),
    CONSTRAINT CK_recon_manifesto_root_presence_values CHECK (
        snapshot_completeness=N'BLOCKED_NO_COMPLETENESS_PROOF' AND absence_evaluation=N'NOT_EVALUATED'
    )
);
GO

CREATE OR ALTER PROCEDURE stg.usp_stage_manifesto_observation
    @execution_id UNIQUEIDENTIFIER,@input_batch_number INT,@input_record_ordinal INT,
    @source_key NVARCHAR(MAX),@payload_json NVARCHAR(MAX),@field_presence_json NVARCHAR(MAX),
    @root_fields_json NVARCHAR(MAX),@metric_values_json NVARCHAR(MAX),@relation_candidates_json NVARCHAR(MAX),
    @mdfe_status_presence NVARCHAR(MAX),@mdfe_status_value NVARCHAR(MAX),@pick_source_key NVARCHAR(MAX),
    @mdfe_key NVARCHAR(MAX),@mdfe_number NVARCHAR(MAX),@freshness_at_utc DATETIME2(3),
    @freshness_origin NVARCHAR(MAX),@competence_json NVARCHAR(MAX),@validation_disposition NVARCHAR(MAX),
    @quarantine_reason_code NVARCHAR(MAX),@observed_at_utc DATETIME2(3)
AS
BEGIN
    SET NOCOUNT ON; SET XACT_ABORT ON;
    IF @input_record_ordinal NOT BETWEEN 1 AND 100 OR @observed_at_utc IS NULL
       OR DATALENGTH(@source_key)>512 OR DATALENGTH(@mdfe_status_value)>100
       OR DATALENGTH(@pick_source_key)>512 OR DATALENGTH(@mdfe_key)>88 OR DATALENGTH(@mdfe_number)>256
       OR DATALENGTH(@validation_disposition)>32 OR DATALENGTH(@quarantine_reason_code)>128
        THROW 52000,N'Observação física de Manifestos excede limite ou ordinal.',1;
    SET @validation_disposition=LTRIM(RTRIM(@validation_disposition));
    SET @quarantine_reason_code=LTRIM(RTRIM(@quarantine_reason_code));
    SET @mdfe_status_presence=LTRIM(RTRIM(@mdfe_status_presence));
    SET @freshness_origin=LTRIM(RTRIM(@freshness_origin));
    IF @validation_disposition NOT IN(N'VALID',N'QUARANTINE')
        THROW 52004,N'Observação física de Manifestos possui disposition inválida.',1;
    IF @validation_disposition=N'VALID' AND @quarantine_reason_code IS NOT NULL
        THROW 52005,N'Observação válida de Manifestos não pode receber razão de quarentena.',1;
    IF @validation_disposition=N'VALID' AND ISJSON(@payload_json)<>1
        THROW 52051,N'Observação válida de Manifestos não contém payload JSON canônico.',1;
    IF @validation_disposition=N'VALID' AND ISJSON(@field_presence_json)<>1
        THROW 52052,N'Observação válida de Manifestos não contém presenças JSON canônicas.',1;
    IF @validation_disposition=N'VALID' AND ISJSON(@root_fields_json)<>1
        THROW 52053,N'Observação válida de Manifestos não contém raiz JSON canônica.',1;
    IF @validation_disposition=N'VALID' AND ISJSON(@metric_values_json)<>1
        THROW 52054,N'Observação válida de Manifestos não contém métricas JSON canônicas.',1;
    IF @validation_disposition=N'VALID' AND ISJSON(@relation_candidates_json)<>1
        THROW 52055,N'Observação válida de Manifestos não contém candidatos relacionais JSON canônicos.',1;
    IF @validation_disposition=N'VALID' AND (@freshness_at_utc IS NULL
       OR @freshness_origin NOT IN(N'FINISHED_AT',N'CLOSED_AT',N'DEPARTURED_AT',N'CREATED_AT'))
        THROW 52008,N'Observação válida de Manifestos não contém freshness canônico.',1;
    IF @validation_disposition=N'QUARANTINE' AND @quarantine_reason_code IS NULL
        THROW 52006,N'Quarentena física de Manifestos requer razão preservada.',1;
    IF @validation_disposition=N'VALID' AND (
        @source_key NOT LIKE N'INTEGER:[1-9]%' OR SUBSTRING(@source_key,9,256) LIKE N'%[^0-9]%'
        OR @pick_source_key IS NOT NULL AND (@pick_source_key NOT LIKE N'INTEGER:[1-9]%' OR SUBSTRING(@pick_source_key,9,256) LIKE N'%[^0-9]%')
        OR ((@mdfe_key IS NULL AND @mdfe_number IS NOT NULL) OR (@mdfe_key IS NOT NULL AND @mdfe_number IS NULL))
        OR (@mdfe_key IS NOT NULL AND (@mdfe_key LIKE N'%[^0-9]%' OR LEN(@mdfe_key)<>44 OR @mdfe_number NOT LIKE N'[1-9]%' OR @mdfe_number LIKE N'%[^0-9]%'))
        OR @mdfe_status_presence NOT IN(N'ABSENT',N'NULL',N'VALUE')
        OR (@mdfe_status_presence=N'VALUE' AND @mdfe_status_value IS NULL)
        OR (@mdfe_status_presence IN(N'ABSENT',N'NULL') AND @mdfe_status_value IS NOT NULL)
        OR EXISTS(
            SELECT 1 FROM (VALUES
                (N'status',50),(N'mdfe_status',50),(N'mft_ape_name',255),(N'mft_man_name',255),
                (N'mft_vie_license_plate',10),(N'mft_vie_vee_name',255),(N'mft_vie_onr_name',255),
                (N'mft_mdr_iil_name',255),(N'mft_crn_psn_nickname',255),(N'mft_cat_cot_number',50),
                (N'contract_type',50),(N'mft_mdr_contract_type',50),(N'calculation_type',50),(N'cargo_type',255),
                (N'mft_uer_name',255),(N'mft_aoe_rer_name',255),(N'mft_aoe_comments',4000),(N'mft_cat_cot_status',50),
                (N'mft_iks_id',100),(N'mft_s_n_sequence_code',50),(N'mft_tl1_license_plate',10),
                (N'mft_tl2_license_plate',10),(N'operational_comments',4000),(N'closing_comments',4000),
                (N'mft_s_n_svs_sge_pyr_nickname',255),(N'mft_s_n_svs_sge_sse_name',255)
            ) AS limits(field_name,maximum_utf16_units)
            JOIN OPENJSON(@root_fields_json) AS text_value ON text_value.[key]=limits.field_name
            WHERE text_value.[type]=1 AND DATALENGTH(text_value.[value])>limits.maximum_utf16_units*2
        )
    ) THROW 52001,N'Observação física de Manifestos falha fechada por chave, filho ou texto.',1;
    BEGIN TRANSACTION;
    DECLARE @entity_name NVARCHAR(128),@source_instance NVARCHAR(128),@tenant_scope NVARCHAR(128);
    SELECT @entity_name=p.entity_name,@source_instance=p.source_instance,@tenant_scope=p.tenant_scope
    FROM ctl.execution_attempt a WITH(UPDLOCK,HOLDLOCK)
    JOIN ctl.execution_partition p WITH(HOLDLOCK) ON p.partition_id=a.partition_id WHERE a.execution_id=@execution_id;
    IF @entity_name<>N'manifestos' OR UPPER(@source_instance) IN(N'DEFAULT',N'GLOBAL',N'SINGLETON')
       OR UPPER(@tenant_scope) IN(N'DEFAULT',N'GLOBAL',N'SINGLETON') THROW 52002,N'Escopo de Manifestos inválido.',1;
    IF EXISTS(SELECT 1 FROM stg.manifesto_observation WITH(UPDLOCK,HOLDLOCK)
              WHERE execution_id=@execution_id AND input_batch_number=@input_batch_number AND input_record_ordinal=@input_record_ordinal
                AND (payload_json<>@payload_json OR field_presence_json<>@field_presence_json OR validation_disposition<>@validation_disposition
                     OR ISNULL(source_key,N'<null>')<>ISNULL(@source_key,N'<null>')))
        THROW 52003,N'Retry de observação física possui conteúdo divergente.',1;
    IF NOT EXISTS(SELECT 1 FROM stg.manifesto_observation WHERE execution_id=@execution_id AND input_batch_number=@input_batch_number AND input_record_ordinal=@input_record_ordinal)
    BEGIN
        INSERT stg.manifesto_observation(execution_id,input_batch_number,input_record_ordinal,source_key,validation_disposition,quarantine_reason_code,payload_json,field_presence_json,root_fields_json,metric_values_json,relation_candidates_json,mdfe_status_presence,mdfe_status_value,pick_source_key,mdfe_key,mdfe_number,freshness_at_utc,freshness_origin,competence_json,observed_at_utc)
        VALUES(@execution_id,@input_batch_number,@input_record_ordinal,@source_key,@validation_disposition,@quarantine_reason_code,@payload_json,@field_presence_json,@root_fields_json,@metric_values_json,@relation_candidates_json,@mdfe_status_presence,@mdfe_status_value,@pick_source_key,@mdfe_key,@mdfe_number,@freshness_at_utc,@freshness_origin,@competence_json,@observed_at_utc);
        DECLARE @observation_id BIGINT=CONVERT(BIGINT,SCOPE_IDENTITY());
        INSERT recon.manifesto_coleta_relation_candidate(execution_id,manifesto_observation_id,root_source_key,pick_source_key,candidate_presence,provenance_json,observed_at_utc)
        SELECT @execution_id,@observation_id,@source_key,@pick_source_key,
               COALESCE(JSON_VALUE(@relation_candidates_json,N'$.mft_pfs_pck_sequence_code.presence'),N'ABSENT'),
               @relation_candidates_json,@observed_at_utc;
    END;
    COMMIT TRANSACTION;
END;
GO

CREATE OR ALTER PROCEDURE stg.usp_stage_manifesto_reduced_candidate
    @execution_id UNIQUEIDENTIFIER,@input_batch_number INT,@input_record_ordinal INT,@source_key NVARCHAR(MAX),
    @root_payload_json NVARCHAR(MAX),@root_presence_json NVARCHAR(MAX),@metric_values_json NVARCHAR(MAX),@competence_json NVARCHAR(MAX),
    @mdfe_status_presence NVARCHAR(MAX),@mdfe_status_value NVARCHAR(MAX),@freshness_at_utc DATETIME2(3),@freshness_origin NVARCHAR(MAX),
    @pick_candidates_json NVARCHAR(MAX),@mdfe_candidates_json NVARCHAR(MAX),@observed_at_utc DATETIME2(3)
AS
BEGIN
    SET NOCOUNT ON; SET XACT_ABORT ON;
    IF @input_record_ordinal NOT BETWEEN 1 AND 10000 OR @observed_at_utc IS NULL OR DATALENGTH(@source_key)>512
       OR DATALENGTH(@mdfe_status_value)>100 OR ISJSON(@root_payload_json)<>1 OR ISJSON(@root_presence_json)<>1
       OR ISJSON(@metric_values_json)<>1 OR ISJSON(@competence_json)<>1 OR ISJSON(@pick_candidates_json)<>1 OR ISJSON(@mdfe_candidates_json)<>1
        THROW 52010,N'Candidato reduzido de Manifestos inválido.',1;
    SET @mdfe_status_presence=LTRIM(RTRIM(@mdfe_status_presence)); SET @freshness_origin=LTRIM(RTRIM(@freshness_origin));
    IF @source_key NOT LIKE N'INTEGER:[1-9]%' OR SUBSTRING(@source_key,9,256) LIKE N'%[^0-9]%'
       OR @freshness_at_utc IS NULL OR @freshness_origin NOT IN(N'FINISHED_AT',N'CLOSED_AT',N'DEPARTURED_AT',N'CREATED_AT')
       OR @mdfe_status_presence NOT IN(N'ABSENT',N'NULL',N'VALUE')
       OR (@mdfe_status_presence=N'VALUE' AND @mdfe_status_value IS NULL)
       OR (@mdfe_status_presence IN(N'ABSENT',N'NULL') AND @mdfe_status_value IS NOT NULL)
       OR EXISTS(SELECT 1 FROM OPENJSON(@pick_candidates_json) WITH(source_key NVARCHAR(256) N'$.sourceKey',presence NVARCHAR(8) N'$.presence') WHERE source_key NOT LIKE N'INTEGER:[1-9]%' OR SUBSTRING(source_key,9,256) LIKE N'%[^0-9]%' OR presence<>N'VALUE')
       OR EXISTS(SELECT 1 FROM OPENJSON(@mdfe_candidates_json) WITH(mdfe_key NVARCHAR(44) N'$.key',mdfe_number NVARCHAR(128) N'$.number') WHERE mdfe_key LIKE N'%[^0-9]%' OR LEN(mdfe_key)<>44 OR mdfe_number NOT LIKE N'[1-9]%' OR mdfe_number LIKE N'%[^0-9]%')
        THROW 52010,N'Candidato reduzido de Manifestos falha fechado.',1;
    DECLARE @attribute_hash CHAR(64)=LOWER(CONVERT(CHAR(64),HASHBYTES('SHA2_256',CONVERT(VARBINARY(MAX),CONCAT(N'manifestos-root-v1|',@root_payload_json,N'|',@metric_values_json,N'|',@competence_json,N'|',@mdfe_status_presence,N'|',COALESCE(@mdfe_status_value,N'<null>')))),2));
    DECLARE @presence_hash CHAR(64)=LOWER(CONVERT(CHAR(64),HASHBYTES('SHA2_256',CONVERT(VARBINARY(MAX),CONCAT(N'manifestos-presence-v1|',@root_presence_json,N'|',@mdfe_status_presence))),2));
    BEGIN TRANSACTION;
    EXEC stg.usp_stage_record @execution_id,@input_batch_number,@input_record_ordinal,@source_key,
        N'manifestos-root-v1',@attribute_hash,N'manifestos-presence-v1',@presence_hash,@freshness_at_utc,N'VALID',NULL,@observed_at_utc;
    DECLARE @stage_record_id BIGINT;
    SELECT @stage_record_id=stage_record_id FROM stg.execution_record WITH(UPDLOCK,HOLDLOCK)
    WHERE execution_id=@execution_id AND input_batch_number=@input_batch_number AND input_record_ordinal=@input_record_ordinal;
    IF EXISTS(SELECT 1 FROM stg.manifesto_reduced_candidate WITH(UPDLOCK,HOLDLOCK) WHERE execution_id=@execution_id AND source_key=@source_key AND (attribute_hash<>@attribute_hash OR presence_hash<>@presence_hash))
        THROW 52011,N'Retry de candidato reduzido diverge.',1;
    DECLARE @manifesto_candidate_id BIGINT;
    IF NOT EXISTS(SELECT 1 FROM stg.manifesto_reduced_candidate WHERE execution_id=@execution_id AND source_key=@source_key)
    BEGIN
        INSERT stg.manifesto_reduced_candidate(execution_id,source_key,stage_record_id,root_payload_json,root_presence_json,metric_values_json,competence_json,mdfe_status_presence,mdfe_status_value,freshness_at_utc,freshness_origin,attribute_hash,presence_hash,reduced_at_utc)
        VALUES(@execution_id,@source_key,@stage_record_id,@root_payload_json,@root_presence_json,@metric_values_json,@competence_json,@mdfe_status_presence,@mdfe_status_value,@freshness_at_utc,@freshness_origin,@attribute_hash,@presence_hash,@observed_at_utc);
        SELECT @manifesto_candidate_id=manifesto_candidate_id FROM stg.manifesto_reduced_candidate
        WHERE execution_id=@execution_id AND source_key=@source_key;
        INSERT stg.manifesto_pick_candidate(manifesto_candidate_id,execution_id,source_key,pick_source_key,candidate_presence,provenance_json)
        SELECT @manifesto_candidate_id,@execution_id,@source_key,source_key,N'VALUE',N'{"reducer":"MAN-01","relation":"UNRESOLVED_V2_046A"}'
        FROM OPENJSON(@pick_candidates_json) WITH(source_key NVARCHAR(256) N'$.sourceKey');
        INSERT stg.manifesto_mdfe_candidate(manifesto_candidate_id,execution_id,source_key,mdfe_key,mdfe_number,provenance_json)
        SELECT @manifesto_candidate_id,@execution_id,@source_key,mdfe_key,mdfe_number,N'{"reducer":"MAN-02","pair":"SAME_PHYSICAL_OBSERVATION"}'
        FROM OPENJSON(@mdfe_candidates_json) WITH(mdfe_key CHAR(44) N'$.key',mdfe_number NVARCHAR(128) N'$.number');
    END;
    COMMIT TRANSACTION;
END;
GO

CREATE OR ALTER PROCEDURE core.usp_prepare_manifesto_candidate_set
    @execution_id UNIQUEIDENTIFIER,@contract_version NVARCHAR(MAX),@contract_fingerprint NVARCHAR(MAX),
    @configuration_version NVARCHAR(MAX),@configuration_fingerprint NVARCHAR(MAX)
AS
BEGIN
    SET NOCOUNT ON; SET XACT_ABORT ON;
    IF EXISTS(
        SELECT 1 FROM stg.manifesto_observation o
        WHERE o.execution_id=@execution_id AND o.validation_disposition=N'VALID'
          AND NOT EXISTS(SELECT 1 FROM stg.manifesto_reduced_candidate c WHERE c.execution_id=o.execution_id AND c.source_key=o.source_key)
    ) THROW 52020,N'Uma observação física válida não possui candidato reduzido MAN-01/MAN-04.',1;
    IF EXISTS(
        SELECT 1 FROM stg.manifesto_observation q JOIN stg.manifesto_reduced_candidate c
          ON c.execution_id=q.execution_id AND c.source_key=q.source_key
        WHERE q.execution_id=@execution_id AND q.validation_disposition=N'QUARANTINE' AND q.source_key IS NOT NULL
    ) THROW 52021,N'Raiz com observação em quarantine não pode promover.',1;
    EXEC core.usp_prepare_staged_execution @execution_id,@contract_version,@contract_fingerprint,@configuration_version,@configuration_fingerprint;
    INSERT ctl.manifesto_promotion_result(execution_id,physical_observation_rows,reduced_root_rows,quarantined_observation_rows,validation_state,validated_at_utc)
    SELECT @execution_id,
        (SELECT COUNT_BIG(*) FROM stg.manifesto_observation WHERE execution_id=@execution_id),
        (SELECT COUNT_BIG(*) FROM stg.manifesto_reduced_candidate WHERE execution_id=@execution_id),
        (SELECT COUNT_BIG(*) FROM stg.manifesto_observation WHERE execution_id=@execution_id AND validation_disposition=N'QUARANTINE'),
        CASE WHEN (SELECT COUNT_BIG(*) FROM stg.execution_candidate WHERE execution_id=@execution_id)=(SELECT COUNT_BIG(*) FROM stg.manifesto_reduced_candidate WHERE execution_id=@execution_id) THEN N'PASSED' ELSE N'BLOCKED' END,
        SYSUTCDATETIME()
    WHERE NOT EXISTS(SELECT 1 FROM ctl.manifesto_promotion_result WHERE execution_id=@execution_id);
END;
GO

CREATE OR ALTER PROCEDURE core.usp_apply_reconcile_publish_manifestos
    @execution_id UNIQUEIDENTIFIER,@contract_version NVARCHAR(MAX),@contract_fingerprint NVARCHAR(MAX),
    @configuration_version NVARCHAR(MAX),@configuration_fingerprint NVARCHAR(MAX)
AS
BEGIN
    SET NOCOUNT ON; SET XACT_ABORT ON; BEGIN TRANSACTION;
    IF NOT EXISTS(SELECT 1 FROM ctl.manifesto_promotion_result WITH(UPDLOCK,HOLDLOCK) WHERE execution_id=@execution_id AND validation_state=N'PASSED')
        THROW 52030,N'Candidate set de Manifestos não está apto.',1;
    DECLARE @environment_name NVARCHAR(32),@source_instance NVARCHAR(128),@tenant_scope NVARCHAR(128);
    SELECT @environment_name=p.environment_name,@source_instance=p.source_instance,@tenant_scope=p.tenant_scope
    FROM ctl.execution_attempt a WITH(UPDLOCK,HOLDLOCK) JOIN ctl.execution_partition p WITH(HOLDLOCK) ON p.partition_id=a.partition_id WHERE a.execution_id=@execution_id;
    IF UPPER(@source_instance) IN(N'DEFAULT',N'GLOBAL',N'SINGLETON') OR UPPER(@tenant_scope) IN(N'DEFAULT',N'GLOBAL',N'SINGLETON')
        THROW 52031,N'Manifestos exige escopo explícito.',1;
    IF EXISTS(
        SELECT 1 FROM stg.manifesto_reduced_candidate c JOIN core.manifesto current_record WITH(UPDLOCK,HOLDLOCK,INDEX(UQ_core_manifesto_source))
          ON current_record.environment_name=@environment_name AND current_record.source_instance=@source_instance AND current_record.tenant_scope=@tenant_scope
         AND current_record.entity_name=N'manifestos' AND current_record.source_key=c.source_key
        WHERE c.execution_id=@execution_id AND current_record.freshness_at_utc=c.freshness_at_utc AND current_record.attribute_hash<>c.attribute_hash
    ) THROW 52032,N'EQUAL_FRESHNESS_CONFLICT de Manifesto exige quarantine.',1;
    DECLARE @common_result TABLE(execution_id UNIQUEIDENTIFIER,candidate_rows BIGINT,inserted_rows BIGINT,updated_rows BIGINT,reactivated_rows BIGINT,noop_rows BIGINT,stale_noop_rows BIGINT,reconciled_at_utc DATETIME2(3),published_at_utc DATETIME2(3),incremental_frontier_before_utc DATETIME2(3),incremental_frontier_after_utc DATETIME2(3));
    INSERT @common_result EXEC core.usp_apply_reconcile_publish_execution @execution_id,@contract_version,@contract_fingerprint,@configuration_version,@configuration_fingerprint;
    INSERT core.manifesto(record_state_id,environment_name,source_instance,tenant_scope,entity_name,source_key,root_payload_json,root_presence_json,metric_values_json,competence_json,mdfe_status_presence,mdfe_status_value,freshness_at_utc,freshness_origin,attribute_hash,presence_hash,active,first_seen_execution_id,last_seen_execution_id)
    SELECT app.record_state_id,@environment_name,@source_instance,@tenant_scope,N'manifestos',c.source_key,c.root_payload_json,c.root_presence_json,c.metric_values_json,c.competence_json,c.mdfe_status_presence,c.mdfe_status_value,c.freshness_at_utc,c.freshness_origin,c.attribute_hash,c.presence_hash,1,@execution_id,@execution_id
    FROM stg.manifesto_reduced_candidate c JOIN recon.execution_candidate_application app ON app.execution_id=c.execution_id AND app.source_key=c.source_key
    WHERE c.execution_id=@execution_id AND NOT EXISTS(SELECT 1 FROM core.manifesto m WITH(UPDLOCK,HOLDLOCK,INDEX(UQ_core_manifesto_source)) WHERE m.environment_name=@environment_name AND m.source_instance=@source_instance AND m.tenant_scope=@tenant_scope AND m.entity_name=N'manifestos' AND m.source_key=c.source_key);
    UPDATE current_record SET root_payload_json=c.root_payload_json,root_presence_json=c.root_presence_json,metric_values_json=c.metric_values_json,competence_json=c.competence_json,mdfe_status_presence=c.mdfe_status_presence,mdfe_status_value=c.mdfe_status_value,freshness_at_utc=c.freshness_at_utc,freshness_origin=c.freshness_origin,attribute_hash=c.attribute_hash,presence_hash=c.presence_hash,last_seen_execution_id=@execution_id
    FROM core.manifesto current_record JOIN stg.manifesto_reduced_candidate c ON c.source_key=current_record.source_key
    WHERE c.execution_id=@execution_id AND current_record.environment_name=@environment_name AND current_record.source_instance=@source_instance AND current_record.tenant_scope=@tenant_scope AND current_record.entity_name=N'manifestos' AND c.freshness_at_utc>current_record.freshness_at_utc;
    INSERT core.manifesto_pick(manifesto_id,pick_source_key,first_seen_execution_id)
    SELECT m.manifesto_id,p.pick_source_key,@execution_id FROM stg.manifesto_pick_candidate p JOIN core.manifesto m ON m.environment_name=@environment_name AND m.source_instance=@source_instance AND m.tenant_scope=@tenant_scope AND m.entity_name=N'manifestos' AND m.source_key=p.source_key
    JOIN recon.execution_candidate_application app ON app.execution_id=p.execution_id AND app.source_key=p.source_key
    WHERE p.execution_id=@execution_id AND app.application_disposition<>N'STALE_NO_OP' AND NOT EXISTS(SELECT 1 FROM core.manifesto_pick x WITH(UPDLOCK,HOLDLOCK,INDEX(UQ_core_manifesto_pick)) WHERE x.manifesto_id=m.manifesto_id AND x.pick_source_key=p.pick_source_key);
    INSERT core.manifesto_mdfe(manifesto_id,mdfe_key,mdfe_number,first_seen_execution_id)
    SELECT m.manifesto_id,c.mdfe_key,c.mdfe_number,@execution_id FROM stg.manifesto_mdfe_candidate c JOIN core.manifesto m ON m.environment_name=@environment_name AND m.source_instance=@source_instance AND m.tenant_scope=@tenant_scope AND m.entity_name=N'manifestos' AND m.source_key=c.source_key
    JOIN recon.execution_candidate_application app ON app.execution_id=c.execution_id AND app.source_key=c.source_key
    WHERE c.execution_id=@execution_id AND app.application_disposition<>N'STALE_NO_OP' AND NOT EXISTS(SELECT 1 FROM core.manifesto_mdfe x WITH(UPDLOCK,HOLDLOCK,INDEX(UQ_core_manifesto_mdfe)) WHERE x.manifesto_id=m.manifesto_id AND x.mdfe_key=c.mdfe_key);
    INSERT recon.manifesto_root_presence_observation(execution_id,manifesto_id,observed_at_utc,snapshot_completeness,absence_evaluation)
    SELECT @execution_id,m.manifesto_id,SYSUTCDATETIME(),N'BLOCKED_NO_COMPLETENESS_PROOF',N'NOT_EVALUATED'
    FROM core.manifesto m JOIN stg.manifesto_reduced_candidate c ON c.source_key=m.source_key
    WHERE c.execution_id=@execution_id AND m.environment_name=@environment_name AND m.source_instance=@source_instance AND m.tenant_scope=@tenant_scope AND m.entity_name=N'manifestos'
      AND NOT EXISTS(SELECT 1 FROM recon.manifesto_root_presence_observation x WHERE x.execution_id=@execution_id AND x.manifesto_id=m.manifesto_id);
    COMMIT TRANSACTION;
    SELECT execution_id,candidate_rows,inserted_rows,updated_rows,reactivated_rows,noop_rows,stale_noop_rows,reconciled_at_utc,published_at_utc,incremental_frontier_before_utc,incremental_frontier_after_utc FROM @common_result;
END;
GO

EXEC dbo.usp_publish_v2_procedure_grant N'stg',N'usp_stage_manifesto_observation',N'v2_runtime';
EXEC dbo.usp_publish_v2_procedure_grant N'stg',N'usp_stage_manifesto_reduced_candidate',N'v2_runtime';
EXEC dbo.usp_publish_v2_procedure_grant N'core',N'usp_prepare_manifesto_candidate_set',N'v2_runtime';
EXEC dbo.usp_publish_v2_procedure_grant N'core',N'usp_apply_reconcile_publish_manifestos',N'v2_runtime';
DENY INSERT,UPDATE,DELETE,SELECT ON SCHEMA::stg TO v2_runtime;
DENY INSERT,UPDATE,DELETE,SELECT ON SCHEMA::core TO v2_runtime;
DENY INSERT,UPDATE,DELETE,SELECT ON SCHEMA::recon TO v2_runtime;
GO
