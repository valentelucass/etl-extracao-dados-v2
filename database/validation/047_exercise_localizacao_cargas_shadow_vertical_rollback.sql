-- 047_exercise_localizacao_cargas_shadow_vertical_rollback.sql
-- Exercício sintético V2-028. Toda escrita é revertida; nenhum ID/payload real é usado.
-- QUARANTINE_RAW_PRESERVED significa payload/raw/presença no sidecar, nunca candidate/promoção.
:setvar DatabaseName "ETL_SISTEMA_V2_SHADOW"
:On Error exit

IF DB_NAME()<>N'$(DatabaseName)'
    THROW 52260,N'O exercício de Localização aceita somente ETL_SISTEMA_V2_SHADOW.',1;

SET NOCOUNT ON;
SET XACT_ABORT ON;
BEGIN TRANSACTION;

:r "..\transition\001_reset_historical_shadow_for_schema_foundation.sql"
:r "..\baseline\001_schema_foundation_baseline.sql"
GO
:r "005_validate_progressive_data_gate.sql"
GO
:r "046_validate_localizacao_cargas_shadow_vertical.sql"
GO

DECLARE @now DATETIME2(3)=SYSUTCDATETIME();
DECLARE @source NVARCHAR(128)=N'SYNTHETIC_LOCALIZACAO_8656';
DECLARE @cycle_fingerprint CHAR(64)=REPLICATE('a',64);
EXEC ctl.usp_control_plane_register_source @source,N'DATA_EXPORT',@now;
EXEC ctl.usp_control_plane_start_cycle
    '00000000-0000-0000-0000-000000008656',N'synthetic-localizacao-plan-v1',
    @cycle_fingerprint,@now;
GO

CREATE OR ALTER PROCEDURE #install_localizacao_dq
    @tenant NVARCHAR(128),
    @mode NVARCHAR(16),
    @suffix NVARCHAR(32)
AS
BEGIN
    SET NOCOUNT ON;
    DECLARE @effective DATETIME2(3)=DATEADD(DAY,-1,SYSUTCDATETIME());
    DECLARE @environment NVARCHAR(32)=N'LOCAL_SHADOW';
    DECLARE @source NVARCHAR(128)=N'SYNTHETIC_LOCALIZACAO_8656';
    DECLARE @entity NVARCHAR(128)=N'localizacao_cargas';
    DECLARE @scope CHAR(64)=LOWER(CONVERT(CHAR(64),HASHBYTES(
        'SHA2_256',CONVERT(VARBINARY(MAX),CONCAT(
            N'dq-scope-v1|',DATALENGTH(@environment),N':',@environment,
            N'|',DATALENGTH(@source),N':',@source,
            N'|',DATALENGTH(@tenant),N':',@tenant,
            N'|',DATALENGTH(@entity),N':',@entity,
            N'|',DATALENGTH(@mode),N':',@mode))),2));
    DECLARE @version NVARCHAR(128)=CONCAT(N'synthetic-localizacao-dq-',@suffix);
    DECLARE @fingerprint CHAR(64)=LOWER(CONVERT(CHAR(64),HASHBYTES(
        'SHA2_256',CONVERT(VARBINARY(MAX),CONCAT(
            N'dq-policy-v1|',DATALENGTH(@version),N':',@version,N'|',@scope,
            N'|4|3600|',DATALENGTH(N'quality-owner'),N':quality-owner|',
            DATALENGTH(N'quarantine-owner'),N':quarantine-owner|',
            DATALENGTH(N'synthetic-localizacao-retention-v1'),
            N':synthetic-localizacao-retention-v1|',
            DATALENGTH(N'retention-owner'),N':retention-owner|',
            CONVERT(NVARCHAR(33),@effective,126),
            N'|1:COUNT_EQUATION:0:0:',DATALENGTH(N'quality-owner'),N':quality-owner',
            N'|2:PAGE_TERMINALITY:0:0:',DATALENGTH(N'quality-owner'),N':quality-owner',
            N'|3:PROMOTION_RECONCILIATION:0:0:',DATALENGTH(N'quality-owner'),N':quality-owner',
            N'|4:QUARANTINE_SLA:0:0:',DATALENGTH(N'quality-owner'),N':quality-owner'))),2));
    INSERT ctl.data_quality_policy VALUES(
        @version,@fingerprint,@scope,4,3600,N'quality-owner',N'quarantine-owner',
        N'synthetic-localizacao-retention-v1',N'retention-owner',N'RATIFIED',@effective,
        SYSUTCDATETIME()
    );
    INSERT ctl.data_quality_check_policy VALUES
        (@version,@fingerprint,1,N'COUNT_EQUATION',0,0,N'quality-owner'),
        (@version,@fingerprint,2,N'PAGE_TERMINALITY',0,0,N'quality-owner'),
        (@version,@fingerprint,3,N'PROMOTION_RECONCILIATION',0,0,N'quality-owner'),
        (@version,@fingerprint,4,N'QUARANTINE_SLA',0,0,N'quality-owner');
END;
GO

EXEC #install_localizacao_dq N'SYNTHETIC_LOCALIZACAO_TENANT_A',N'BACKFILL',N'a-backfill';
EXEC #install_localizacao_dq N'SYNTHETIC_LOCALIZACAO_TENANT_A',N'REPLAY',N'a-replay';
EXEC #install_localizacao_dq N'SYNTHETIC_LOCALIZACAO_TENANT_B',N'BACKFILL',N'b-backfill';
GO

-- Fixture builder equivalente ao mapper: root fields/policy, 17 paths e sete metadados.
CREATE OR ALTER PROCEDURE #build_localizacao_presence
    @payload_json NVARCHAR(MAX),
    @presence_json NVARCHAR(MAX) OUTPUT
AS
BEGIN
    SET NOCOUNT ON;
    DECLARE @catalog TABLE(
        ordinal TINYINT PRIMARY KEY,field_name NVARCHAR(64) NOT NULL,
        typed_state NVARCHAR(64) NOT NULL
    );
    INSERT @catalog VALUES
      (1,N'corporation_sequence_number',N'INTEGER_TYPE_TAGGED_SOURCE_KEY'),
      (2,N'type',N'UNVERIFIED_SOURCE_TYPE_PRESERVED_RAW'),
      (3,N'service_at',N'STRICT_TYPED_COLUMN'),
      (4,N'invoices_volumes',N'STRICT_TYPED_COLUMN'),
      (5,N'taxed_weight',N'STRICT_TYPED_COLUMN'),
      (6,N'invoices_value',N'STRICT_TYPED_COLUMN'),(7,N'total',N'STRICT_TYPED_COLUMN'),
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
    DECLARE @fields NVARCHAR(MAX);
    SELECT @fields=STRING_AGG(CONVERT(NVARCHAR(MAX),CONCAT(
        N'"',catalog.field_name,N'":{"path":"/',catalog.field_name,N'","presence":"',
        CASE WHEN actual.[type] IS NULL THEN N'ABSENT'
             WHEN actual.[type]=0 THEN N'NULL' ELSE N'VALUE' END,
        N'","provenance":"DATAEXPORT_8656","parseState":"',
        CASE WHEN actual.[type] IS NULL THEN N'NOT_PRESENT'
             WHEN actual.[type]=0 THEN N'EXPLICIT_NULL'
             ELSE N'PRESERVED_OR_STRICTLY_PARSED_BY_NAMED_FIELD' END,
        N'","typedState":"',catalog.typed_state,N'","raw":',
        CASE actual.[type] WHEN 0 THEN N'null'
             WHEN 1 THEN CONCAT(N'"',STRING_ESCAPE(actual.[value],'json'),N'"')
             WHEN 2 THEN actual.[value] WHEN 3 THEN LOWER(actual.[value]) ELSE N'null' END,
        N',"rawWireLexeme":"',
        CASE WHEN actual.[type] IS NULL OR actual.[type]=0
                THEN N'NOT_APPLICABLE_WITHOUT_VALUE'
             WHEN actual.[type] IN(1,3) THEN N'NOT_APPLICABLE_NON_NUMERIC_TOKEN'
             WHEN catalog.field_name IN(N'invoices_volumes',N'taxed_weight',N'invoices_value',N'total')
                THEN STRING_ESCAPE(actual.[value],'json')
             ELSE N'UNAVAILABLE_AFTER_JSON_TREE_NORMALIZATION' END,N'"}')),N',')
        WITHIN GROUP(ORDER BY catalog.ordinal)
    FROM @catalog catalog
    OUTER APPLY(SELECT MAX(source_value.[value]) AS [value],
                       MAX(source_value.[type]) AS [type]
        FROM OPENJSON(@payload_json) source_value
        WHERE source_value.[key] COLLATE Latin1_General_100_BIN2
            = catalog.field_name COLLATE Latin1_General_100_BIN2) actual;
    SET @presence_json=CONCAT(N'{"fields":{',@fields,
      N'},"policy":{"matrix":"LOCALIZACAO_8656_17_PATHS_V1",',
      N'"freightVolumeFallback":"FREIGHT_VOLUME_FALLBACK_DEFERRED_RELATION_AND_PUBLICATION",',
      N'"statusBranchNickname":{"path":"ABSENT","presence":"ABSENT",',
      N'"provenance":"UNSOURCED_LEGACY"}}}');
END;
GO

CREATE OR ALTER PROCEDURE #run_localizacao
    @execution_id UNIQUEIDENTIFIER,
    @window_start DATETIME2(3),
    @window_end DATETIME2(3),
    @idempotency NVARCHAR(128),
    @mode NVARCHAR(16),
    @replay_of UNIQUEIDENTIFIER,
    @tenant NVARCHAR(128),
    @source_key NVARCHAR(256),
    @payload_json NVARCHAR(MAX),
    @field_presence_json NVARCHAR(MAX),
    @service_at_raw NVARCHAR(MAX),
    @service_at_utc DATETIME2(3),
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
    @status_terminal BIT
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    DECLARE @now DATETIME2(3)=SYSUTCDATETIME();
    DECLARE @contract CHAR(64)=REPLICATE('b',64),@configuration CHAR(64)=REPLICATE('c',64);
    DECLARE @environment NVARCHAR(32)=N'LOCAL_SHADOW',
            @source NVARCHAR(128)=N'SYNTHETIC_LOCALIZACAO_8656',
            @entity NVARCHAR(128)=N'localizacao_cargas';
    IF @field_presence_json IS NULL OR ISJSON(@field_presence_json)<>1
        THROW 52290,N'Fixture de presença equivalente ao mapper é obrigatória.',1;
    DECLARE @scope CHAR(64)=LOWER(CONVERT(CHAR(64),HASHBYTES(
        'SHA2_256',CONVERT(VARBINARY(MAX),CONCAT(
            N'dq-scope-v1|',DATALENGTH(@environment),N':',@environment,
            N'|',DATALENGTH(@source),N':',@source,N'|',DATALENGTH(@tenant),N':',@tenant,
            N'|',DATALENGTH(@entity),N':',@entity,N'|',DATALENGTH(@mode),N':',@mode))),2));
    DECLARE @policy_version NVARCHAR(128),@policy_fingerprint CHAR(64);
    SELECT @policy_version=policy_version,@policy_fingerprint=policy_fingerprint
    FROM ctl.data_quality_policy WHERE scope_fingerprint=@scope;
    EXEC ctl.usp_control_plane_start_execution
        @execution_id,'00000000-0000-0000-0000-000000008656',@environment,@source,
        @tenant,@entity,@mode,@window_start,@window_end,
        N'DATA_EXPORT_RESTART_FROM_BEGINNING',N'dataexport-8656-v1',@contract,
        N'localizacao-cargas-shadow-v1',@configuration,@idempotency,@replay_of,3600,@now;
    EXEC ctl.usp_control_plane_record_page
        @execution_id,1,1,100,1,1,512,0,@now,N'NONE';
    EXEC ctl.usp_control_plane_record_page
        @execution_id,2,1,100,0,0,64,1,@now,N'DATA_EXPORT_EMPTY_PAGE';
    EXEC stg.usp_stage_localizacao_carga_record
        @execution_id,1,1,@source_key,@payload_json,@field_presence_json,
        @service_at_raw,@service_at_utc,N'VALUE',N'VALID',
        @invoices_volumes_raw,@invoices_volumes_typed,@invoices_volumes_presence,
        @invoices_volumes_parse_state,@taxed_weight_raw,@taxed_weight_typed,
        @invoices_value_raw,@invoices_value_typed,@total_raw,@total_typed,
        @status_raw,@status_normalized,@status_terminal,N'UNSOURCED_LEGACY',
        N'VALID',NULL,@now;
    EXEC ctl.usp_control_plane_transition_execution
        @execution_id,N'EXTRACTING',N'EXTRACTED',N'EXTRACTION_OK',@now;
    EXEC ctl.usp_control_plane_transition_execution
        @execution_id,N'EXTRACTED',N'STAGED',N'STAGING_OK',@now;
    EXEC core.usp_prepare_staged_execution
        @execution_id,N'dataexport-8656-v1',@contract,
        N'localizacao-cargas-shadow-v1',@configuration;
    DECLARE @dq TABLE(
        execution_id UNIQUEIDENTIFIER,policy_version NVARCHAR(128),
        policy_fingerprint CHAR(64),evaluation_fingerprint CHAR(64),
        expected_checks SMALLINT,completed_checks SMALLINT,passed_checks SMALLINT,
        failed_checks SMALLINT,evaluated_rows BIGINT,failed_rows BIGINT,
        evaluation_state NVARCHAR(16),evaluated_at_utc DATETIME2(3)
    );
    INSERT @dq EXEC recon.usp_evaluate_execution_data_quality
        @execution_id,@policy_version,@policy_fingerprint;
    IF NOT EXISTS(SELECT 1 FROM @dq WHERE evaluation_state=N'PASSED' AND failed_rows=0)
        THROW 52261,N'Data Quality sintética de Localização não passou.',1;
    EXEC core.usp_apply_reconcile_publish_localizacao_cargas
        @execution_id,N'dataexport-8656-v1',@contract,
        N'localizacao-cargas-shadow-v1',@configuration;
END;
GO

DECLARE @fixture_value_payload NVARCHAR(MAX)=N'{
 "corporation_sequence_number":-8656001,"type":"LOAD","service_at":"2036-04-20T12:00:00",
 "invoices_volumes":0,"taxed_weight":10.250000000,"invoices_value":20.000000000,
 "total":30.000000000,"service_type":"NORMAL","fit_crn_psn_nickname":"BRANCH-A",
 "fit_dpn_delivery_prediction_at":"2036-04-21","fit_dyn_name":"DEST-A",
 "fit_dyn_drt_nickname":"REGION-A","fit_fsn_name":"STEP-A","fit_fln_status":"finished",
 "fit_fln_cln_nickname":"STATUS-A","fit_o_n_name":"ORIGIN-A",
 "fit_o_n_drt_nickname":"ORIGIN-REGION-A"}';
DECLARE @presence_value NVARCHAR(MAX),@presence_absent NVARCHAR(MAX);
EXEC #build_localizacao_presence @fixture_value_payload,@presence_value OUTPUT;
SET @presence_value=JSON_MODIFY(@presence_value,
    N'$.fields.taxed_weight.rawWireLexeme',N'10.250000000');
SET @presence_value=JSON_MODIFY(@presence_value,
    N'$.fields.invoices_value.rawWireLexeme',N'20.000000000');
SET @presence_value=JSON_MODIFY(@presence_value,
    N'$.fields.total.rawWireLexeme',N'30.000000000');
IF JSON_VALUE(@presence_value,N'$.fields.taxed_weight.raw')<>N'10.250000000'
   OR JSON_VALUE(@presence_value,N'$.fields.taxed_weight.rawWireLexeme')<>N'10.250000000'
   OR JSON_VALUE(@presence_value,N'$.fields.invoices_value.raw')<>N'20.000000000'
   OR JSON_VALUE(@presence_value,N'$.fields.invoices_value.rawWireLexeme')<>N'20.000000000'
    THROW 52289,N'Fixture SQL não reproduz raw canônico + rawWireLexeme do mapper Java.',1;
EXEC #build_localizacao_presence
    N'{"corporation_sequence_number":-8656001,"service_at":"2036-04-21T12:00:00-03:00"}',
    @presence_absent OUTPUT;
DECLARE @tenant_a NVARCHAR(128)=N'SYNTHETIC_LOCALIZACAO_TENANT_A';
DECLARE @tenant_b NVARCHAR(128)=N'SYNTHETIC_LOCALIZACAO_TENANT_B';
DECLARE @source_key NVARCHAR(256)=N'INTEGER:-8656001';
DECLARE @seed UNIQUEIDENTIFIER='00000000-0000-0000-0000-000000008660';
DECLARE @replay UNIQUEIDENTIFIER='00000000-0000-0000-0000-000000008661';
DECLARE @stale UNIQUEIDENTIFIER='00000000-0000-0000-0000-000000008662';
DECLARE @newer UNIQUEIDENTIFIER='00000000-0000-0000-0000-000000008663';
DECLARE @other_tenant UNIQUEIDENTIFIER='00000000-0000-0000-0000-000000008664';

-- Barreiras negativas anteriores a qualquer escrita.
DECLARE @caught BIT=0,@negative_now DATETIME2(3)=SYSUTCDATETIME();
DECLARE @locale_payload NVARCHAR(MAX)=JSON_MODIFY(@fixture_value_payload,
    N'$.taxed_weight',N'10,25'),@locale_presence NVARCHAR(MAX);
EXEC #build_localizacao_presence @locale_payload,@locale_presence OUTPUT;
SET XACT_ABORT OFF;
BEGIN TRY
    EXEC stg.usp_stage_localizacao_carga_record
        '00000000-0000-0000-0000-000000008699',1,1,N'INTEGER:01',@fixture_value_payload,@presence_value,
        N'"2036-04-20T12:00:00"','2036-04-20T15:00:00',N'VALUE',N'VALID',
        N'0',0,N'VALUE',N'VALID',N'10.250000000',10.25,N'20.000000000',20,
        N'30.000000000',30,N'finished',N'finished',1,N'UNSOURCED_LEGACY',
        N'VALID',NULL,@negative_now;
END TRY BEGIN CATCH IF ERROR_NUMBER()=52201 SET @caught=1 ELSE THROW; END CATCH;
IF @caught=0 THROW 52262,N'Chave inteira não canônica alcançou Localização.',1;
SET @caught=0;
BEGIN TRY
    EXEC stg.usp_stage_localizacao_carga_record
        '00000000-0000-0000-0000-000000008699',1,101,N'INTEGER:-8656001',@fixture_value_payload,@presence_value,
        N'"2036-04-20T12:00:00"','2036-04-20T15:00:00',N'VALUE',N'VALID',
        N'0',0,N'VALUE',N'VALID',N'10.250000000',10.25,N'20.000000000',20,
        N'30.000000000',30,N'finished',N'finished',1,N'UNSOURCED_LEGACY',
        N'VALID',NULL,@negative_now;
END TRY BEGIN CATCH IF ERROR_NUMBER()=52200 SET @caught=1 ELSE THROW; END CATCH;
IF @caught=0 THROW 52263,N'Microbatch acima de 100 alcançou Localização.',1;
SET @caught=0;
BEGIN TRY
    EXEC stg.usp_stage_localizacao_carga_record
        '00000000-0000-0000-0000-000000008699',1,1,N'INTEGER:-8656001',@locale_payload,@locale_presence,
        N'"2036-04-20T12:00:00"','2036-04-20T15:00:00',N'VALUE',N'VALID',
        N'0',0,N'VALUE',N'VALID',N'"10,25"',10.25,N'20.000000000',20,
        N'30.000000000',30,N'finished',N'finished',1,N'UNSOURCED_LEGACY',
        N'VALID',NULL,@negative_now;
END TRY BEGIN CATCH IF ERROR_NUMBER()=52205 SET @caught=1 ELSE THROW; END CATCH;
IF @caught=0 THROW 52264,N'Locale numérico ambíguo foi aceito.',1;
SET @caught=0;
BEGIN TRY
    EXEC stg.usp_stage_localizacao_carga_record
        '00000000-0000-0000-0000-000000008699',1,1,N'INTEGER:-8656001',@fixture_value_payload,@presence_value,
        N'"2036-04-20T12:00:00"','2036-04-20T15:00:00',N'VALUE',N'VALID',
        N'0',0,N'VALUE',N'VALID',N'10.250000000',10.25,N'20.000000000',20,
        N'30.000000000',30,N'finished',N'finished',1,N'INVENTED_FALLBACK',
        N'VALID',NULL,@negative_now;
END TRY BEGIN CATCH IF ERROR_NUMBER()=52207 SET @caught=1 ELSE THROW; END CATCH;
IF @caught=0 THROW 52265,N'Fallback de status_branch_nickname foi aceito.',1;
SET @caught=0;
DECLARE @extra_payload NVARCHAR(MAX)=JSON_MODIFY(@fixture_value_payload,N'$.extra_field',N'blocked'),
        @extra_presence NVARCHAR(MAX);
EXEC #build_localizacao_presence @extra_payload,@extra_presence OUTPUT;
BEGIN TRY
    EXEC stg.usp_stage_localizacao_carga_record
        '00000000-0000-0000-0000-000000008699',1,1,N'INTEGER:-8656001',
        @extra_payload,@extra_presence,N'"2036-04-20T12:00:00"',
        '2036-04-20T15:00:00',N'VALUE',N'VALID',N'0',0,N'VALUE',N'VALID',
        N'10.250000000',10.25,N'20.000000000',20,N'30.000000000',30,
        N'finished',N'finished',1,N'UNSOURCED_LEGACY',N'VALID',NULL,@negative_now;
END TRY BEGIN CATCH IF ERROR_NUMBER()=52201 SET @caught=1 ELSE THROW; END CATCH;
IF @caught=0 THROW 52278,N'Extra field alcançou o stage 8656.',1;
SET @caught=0;
DECLARE @sequence_payload NVARCHAR(MAX)=JSON_MODIFY(@fixture_value_payload,
        N'$.sequence_number',8656001),@sequence_presence NVARCHAR(MAX);
EXEC #build_localizacao_presence @sequence_payload,@sequence_presence OUTPUT;
BEGIN TRY
    EXEC stg.usp_stage_localizacao_carga_record
        '00000000-0000-0000-0000-000000008699',1,1,N'INTEGER:-8656001',
        @sequence_payload,@sequence_presence,N'"2036-04-20T12:00:00"',
        '2036-04-20T15:00:00',N'VALUE',N'VALID',N'0',0,N'VALUE',N'VALID',
        N'10.250000000',10.25,N'20.000000000',20,N'30.000000000',30,
        N'finished',N'finished',1,N'UNSOURCED_LEGACY',N'VALID',NULL,@negative_now;
END TRY BEGIN CATCH IF ERROR_NUMBER()=52201 SET @caught=1 ELSE THROW; END CATCH;
IF @caught=0 THROW 52279,N'/sequence_number alcançou o stage 8656.',1;
SET @caught=0;
BEGIN TRY
    EXEC stg.usp_stage_localizacao_carga_record
        '00000000-0000-0000-0000-000000008699',1,1,N'INTEGER:8656999',
        @fixture_value_payload,@presence_value,N'"2036-04-20T12:00:00"',
        '2036-04-20T15:00:00',N'VALUE',N'VALID',N'0',0,N'VALUE',N'VALID',
        N'10.250000000',10.25,N'20.000000000',20,N'30.000000000',30,
        N'finished',N'finished',1,N'UNSOURCED_LEGACY',N'VALID',NULL,@negative_now;
END TRY BEGIN CATCH IF ERROR_NUMBER()=52201 SET @caught=1 ELSE THROW; END CATCH;
IF @caught=0 THROW 52280,N'Source key e payload divergentes alcançaram o stage.',1;

-- 2018-11-04 00:30 não existe em America/Sao_Paulo: o gap deve falhar fechado.
DECLARE @gap_payload NVARCHAR(MAX)=JSON_MODIFY(
    @fixture_value_payload,N'$.service_at',N'2018-11-04T00:30:00'),
    @gap_presence NVARCHAR(MAX);
EXEC #build_localizacao_presence @gap_payload,@gap_presence OUTPUT;
SET @caught=0;
BEGIN TRY
    EXEC stg.usp_stage_localizacao_carga_record
        '00000000-0000-0000-0000-000000008699',1,1,N'INTEGER:-8656001',
        @gap_payload,@gap_presence,N'"2018-11-04T00:30:00"',
        '2018-11-04T03:30:00',N'VALUE',N'VALID',N'0',0,N'VALUE',N'VALID',
        N'10.250000000',10.25,N'20.000000000',20,N'30.000000000',30,
        N'finished',N'finished',1,N'UNSOURCED_LEGACY',N'VALID',NULL,@negative_now;
END TRY BEGIN CATCH IF ERROR_NUMBER()=52203 SET @caught=1 ELSE THROW; END CATCH;
IF @caught=0 THROW 52286,N'Gap histórico de America/Sao_Paulo foi aceito.',1;

-- 2018-02-17 23:30 possui dois offsets válidos no fim do horário de verão.
DECLARE @overlap_payload NVARCHAR(MAX)=JSON_MODIFY(
    @fixture_value_payload,N'$.service_at',N'2018-02-17T23:30:00'),
    @overlap_presence NVARCHAR(MAX);
EXEC #build_localizacao_presence @overlap_payload,@overlap_presence OUTPUT;
SET @caught=0;
BEGIN TRY
    EXEC stg.usp_stage_localizacao_carga_record
        '00000000-0000-0000-0000-000000008699',1,1,N'INTEGER:-8656001',
        @overlap_payload,@overlap_presence,N'"2018-02-17T23:30:00"',
        '2018-02-18T01:30:00',N'VALUE',N'VALID',N'0',0,N'VALUE',N'VALID',
        N'10.250000000',10.25,N'20.000000000',20,N'30.000000000',30,
        N'finished',N'finished',1,N'UNSOURCED_LEGACY',N'VALID',NULL,@negative_now;
END TRY BEGIN CATCH IF ERROR_NUMBER()=52203 SET @caught=1 ELSE THROW; END CATCH;
IF @caught=0 THROW 52287,N'Overlap histórico de America/Sao_Paulo foi aceito.',1;

-- Cada scalar tipado deve ser derivado do mesmo raw registrado no envelope fechado.
SET @caught=0;
BEGIN TRY
    EXEC stg.usp_stage_localizacao_carga_record
        '00000000-0000-0000-0000-000000008699',1,1,N'INTEGER:-8656001',
        @fixture_value_payload,@presence_value,N'"2036-04-20T13:00:00-03:00"',
        '2036-04-20T16:00:00',N'VALUE',N'VALID',N'0',0,N'VALUE',N'VALID',
        N'10.250000000',10.25,N'20.000000000',20,N'30.000000000',30,
        N'finished',N'finished',1,N'UNSOURCED_LEGACY',N'VALID',NULL,@negative_now;
END TRY BEGIN CATCH IF ERROR_NUMBER()=52203 SET @caught=1 ELSE THROW; END CATCH;
IF @caught=0 THROW 52282,N'Service_at scalar divergente do envelope foi aceito.',1;
SET @caught=0;
BEGIN TRY
    EXEC stg.usp_stage_localizacao_carga_record
        '00000000-0000-0000-0000-000000008699',1,1,N'INTEGER:-8656001',
        @fixture_value_payload,@presence_value,N'"2036-04-20T12:00:00"',
        '2036-04-20T15:00:00',N'VALUE',N'VALID',N'1',1,N'VALUE',N'VALID',
        N'10.250000000',10.25,N'20.000000000',20,N'30.000000000',30,
        N'finished',N'finished',1,N'UNSOURCED_LEGACY',N'VALID',NULL,@negative_now;
END TRY BEGIN CATCH IF ERROR_NUMBER()=52204 SET @caught=1 ELSE THROW; END CATCH;
IF @caught=0 THROW 52283,N'Invoices_volumes scalar divergente do envelope foi aceito.',1;
SET @caught=0;
BEGIN TRY
    EXEC stg.usp_stage_localizacao_carga_record
        '00000000-0000-0000-0000-000000008699',1,1,N'INTEGER:-8656001',
        @fixture_value_payload,@presence_value,N'"2036-04-20T12:00:00"',
        '2036-04-20T15:00:00',N'VALUE',N'VALID',N'0',0,N'VALUE',N'VALID',
        N'11.250000000',11.25,N'20.000000000',20,N'30.000000000',30,
        N'finished',N'finished',1,N'UNSOURCED_LEGACY',N'VALID',NULL,@negative_now;
END TRY BEGIN CATCH IF ERROR_NUMBER()=52205 SET @caught=1 ELSE THROW; END CATCH;
IF @caught=0 THROW 52284,N'Decimal scalar divergente do envelope foi aceito.',1;
SET @caught=0;
BEGIN TRY
    EXEC stg.usp_stage_localizacao_carga_record
        '00000000-0000-0000-0000-000000008699',1,1,N'INTEGER:-8656001',
        @fixture_value_payload,@presence_value,N'"2036-04-20T12:00:00"',
        '2036-04-20T15:00:00',N'VALUE',N'VALID',N'0',0,N'VALUE',N'VALID',
        N'10.250000000',10.25,N'20.000000000',20,N'30.000000000',30,
        N'open',N'open',0,N'UNSOURCED_LEGACY',N'VALID',NULL,@negative_now;
END TRY BEGIN CATCH IF ERROR_NUMBER()=52206 SET @caught=1 ELSE THROW; END CATCH;
IF @caught=0 THROW 52285,N'Status scalar divergente do envelope foi aceito.',1;
SET XACT_ABORT ON;

EXEC #run_localizacao @seed,'2036-04-20','2036-04-21',N'localizacao-seed',N'BACKFILL',NULL,
    @tenant_a,@source_key,@fixture_value_payload,
    @presence_value,N'"2036-04-20T12:00:00"','2036-04-20T15:00:00',
    N'0',0,N'VALUE',N'VALID',N'10.250000000',10.25,N'20.000000000',20,
    N'30.000000000',30,N'finished',N'finished',1;
IF NOT EXISTS(
    SELECT 1 FROM core.localizacao_cargas
    WHERE tenant_scope=@tenant_a AND source_key=@source_key
      AND invoices_volumes_typed=0 AND status_normalized=N'finished'
      AND status_terminal=1 AND status_branch_nickname IS NULL
      AND status_branch_nickname_provenance=N'UNSOURCED_LEGACY'
      AND freight_candidate_state=N'EXACT_SCOPED_ALIAS_CANDIDATE_UNRESOLVED'
) THROW 52266,N'Seed tipado ou zero real de Localização não foi promovido.',1;

EXEC #run_localizacao @replay,'2036-04-20','2036-04-21',N'localizacao-replay',N'REPLAY',@seed,
    @tenant_a,@source_key,@fixture_value_payload,
    @presence_value,N'"2036-04-20T12:00:00"','2036-04-20T15:00:00',
    N'0',0,N'VALUE',N'VALID',N'10.250000000',10.25,N'20.000000000',20,
    N'30.000000000',30,N'finished',N'finished',1;
IF NOT EXISTS(SELECT 1 FROM recon.execution_candidate_application
    WHERE execution_id=@replay AND application_disposition=N'NO_OP')
    THROW 52267,N'Replay idêntico não virou no-op.',1;

DECLARE @stale_payload NVARCHAR(MAX)=N'{"corporation_sequence_number":-8656001,
 "type":"OLDER","service_at":"2036-04-19T12:00:00-03:00","invoices_volumes":9,
 "taxed_weight":9.000000000,"invoices_value":19.000000000,"total":29.000000000,
 "service_type":"OLDER","fit_crn_psn_nickname":"OLDER","fit_dpn_delivery_prediction_at":"2036-04-19",
 "fit_dyn_name":"OLDER","fit_dyn_drt_nickname":"OLDER","fit_fsn_name":"OLDER",
 "fit_fln_status":"open","fit_fln_cln_nickname":"OLDER","fit_o_n_name":"OLDER",
 "fit_o_n_drt_nickname":"OLDER"}',@stale_presence NVARCHAR(MAX);
EXEC #build_localizacao_presence @stale_payload,@stale_presence OUTPUT;
SET @stale_presence=JSON_MODIFY(@stale_presence,N'$.fields.taxed_weight.rawWireLexeme',N'9.000000000');
SET @stale_presence=JSON_MODIFY(@stale_presence,N'$.fields.invoices_value.rawWireLexeme',N'19.000000000');
SET @stale_presence=JSON_MODIFY(@stale_presence,N'$.fields.total.rawWireLexeme',N'29.000000000');
EXEC #run_localizacao @stale,'2036-04-18','2036-04-19',N'localizacao-stale',N'BACKFILL',NULL,
    @tenant_a,@source_key,@stale_payload,
    @stale_presence,N'"2036-04-19T12:00:00-03:00"','2036-04-19T15:00:00',
    N'9',9,N'VALUE',N'VALID',N'9.000000000',9,N'19.000000000',19,
    N'29.000000000',29,N'open',N'open',0;
IF NOT EXISTS(SELECT 1 FROM recon.execution_candidate_application
    WHERE execution_id=@stale AND application_disposition=N'STALE_NO_OP')
   OR NOT EXISTS(SELECT 1 FROM core.localizacao_cargas
       WHERE tenant_scope=@tenant_a AND source_key=@source_key
         AND JSON_VALUE(payload_json,N'$.type')=N'LOAD' AND invoices_volumes_typed=0)
    THROW 52268,N'Out-of-order regrediu a raiz ou perdeu zero local.',1;

EXEC #run_localizacao @newer,'2036-04-21','2036-04-22',N'localizacao-newer',N'BACKFILL',NULL,
    @tenant_a,@source_key,N'{"corporation_sequence_number":-8656001,
      "service_at":"2036-04-21T12:00:00-03:00"}',
    @presence_absent,N'"2036-04-21T12:00:00-03:00"','2036-04-21T15:00:00',
    NULL,NULL,N'ABSENT',N'NOT_PRESENT',NULL,NULL,NULL,NULL,NULL,NULL,NULL,N'sem_status',0;
IF NOT EXISTS(SELECT 1 FROM core.localizacao_cargas
    WHERE tenant_scope=@tenant_a AND source_key=@source_key
      AND invoices_volumes_typed=0 AND invoices_value_typed=20
      AND status_normalized=N'finished' AND status_terminal=1
      AND JSON_VALUE(payload_json,N'$.type')=N'LOAD'
      AND JSON_VALUE(field_presence_json,N'$.fields.type.presence')=N'VALUE')
    THROW 52269,N'VALUE->ABSENT não preservou os 15 paths opcionais.',1;

DECLARE @tenant_payload NVARCHAR(MAX)=N'{"corporation_sequence_number":-8656001,"type":"TENANT_B",
       "service_at":"2036-04-21T12:00:00-03:00","invoices_volumes":1,
       "taxed_weight":1.000000000,"invoices_value":2.000000000,"total":3.000000000,
       "fit_fln_status":"unknown"}',@tenant_presence NVARCHAR(MAX);
EXEC #build_localizacao_presence @tenant_payload,@tenant_presence OUTPUT;
SET @tenant_presence=JSON_MODIFY(@tenant_presence,N'$.fields.taxed_weight.rawWireLexeme',N'1.000000000');
SET @tenant_presence=JSON_MODIFY(@tenant_presence,N'$.fields.invoices_value.rawWireLexeme',N'2.000000000');
SET @tenant_presence=JSON_MODIFY(@tenant_presence,N'$.fields.total.rawWireLexeme',N'3.000000000');
EXEC #run_localizacao @other_tenant,'2036-04-21','2036-04-22',N'localizacao-other-tenant',
    N'BACKFILL',NULL,@tenant_b,@source_key,@tenant_payload,@tenant_presence,
    N'"2036-04-21T12:00:00-03:00"','2036-04-21T15:00:00',N'1',1,N'VALUE',N'VALID',
    N'1.000000000',1,N'2.000000000',2,N'3.000000000',3,N'unknown',N'unknown',0;
IF (SELECT COUNT_BIG(*) FROM core.localizacao_cargas WHERE source_key=@source_key)<>2
    THROW 52270,N'Isolamento por tenant não criou raízes independentes.',1;

-- Triplet Java -> SQL de alta precisão: payload/evidence canônicos permanecem decimais
-- exatos e raw/rawWire conservam o mesmo léxico que originou o valor tipado DECIMAL(38,9).
DECLARE @precision UNIQUEIDENTIFIER='00000000-0000-0000-0000-000000008668';
DECLARE @precision_payload NVARCHAR(MAX)=N'{"corporation_sequence_number":-8656004,
 "service_at":"2036-04-25T12:00:00-03:00","invoices_volumes":1,
 "taxed_weight":12345678901234567890.123456789,"invoices_value":0.000000001,
 "total":0,"fit_fln_status":"unknown"}',@precision_presence NVARCHAR(MAX);
EXEC #build_localizacao_presence @precision_payload,@precision_presence OUTPUT;
EXEC #run_localizacao @precision,'2036-04-25','2036-04-26',N'localizacao-decimal-precision',
    N'BACKFILL',NULL,@tenant_a,N'INTEGER:-8656004',@precision_payload,@precision_presence,
    N'"2036-04-25T12:00:00-03:00"','2036-04-25T15:00:00',N'1',1,N'VALUE',N'VALID',
    N'12345678901234567890.123456789',12345678901234567890.123456789,
    N'0.000000001',0.000000001,N'0',0,N'unknown',N'unknown',0;
IF NOT EXISTS(SELECT 1 FROM core.localizacao_cargas
    WHERE tenant_scope=@tenant_a AND source_key=N'INTEGER:-8656004'
      AND taxed_weight_typed=12345678901234567890.123456789
      AND invoices_value_typed=0.000000001
      AND JSON_VALUE(field_presence_json,N'$.fields.taxed_weight.raw')=
            N'12345678901234567890.123456789'
      AND JSON_VALUE(field_presence_json,N'$.fields.taxed_weight.rawWireLexeme')=
            N'12345678901234567890.123456789')
    THROW 52291,N'Precisão DECIMAL(38,9) divergiu entre mapper e SQL.',1;

-- Os dois paths obrigatórios ausentes e o overflow numérico são QUARANTINE: envelope fica
-- auditável, nenhum candidate nasce e o core previamente publicado permanece byte-for-byte.
DECLARE @mandatory UNIQUEIDENTIFIER='00000000-0000-0000-0000-000000008667';
DECLARE @missing_key_payload NVARCHAR(MAX)=N'{"service_at":"2036-04-22T12:00:00-03:00"}',
        @missing_key_presence NVARCHAR(MAX),
        @missing_service_payload NVARCHAR(MAX)=N'{"corporation_sequence_number":-8656001}',
        @missing_service_presence NVARCHAR(MAX),
        @overflow_wire NVARCHAR(MAX)=REPLICATE(N'9',5001),
        @overflow_payload NVARCHAR(MAX),@overflow_presence NVARCHAR(MAX),
        @core_payload_before NVARCHAR(MAX),@core_presence_before NVARCHAR(MAX),
        @core_hash_before CHAR(64),@core_freshness_before DATETIME2(3);
SET @overflow_payload=CONCAT(N'{"corporation_sequence_number":-8656002,',
    N'"service_at":"2036-04-22T12:00:00-03:00","taxed_weight":',@overflow_wire,N'}');
EXEC #build_localizacao_presence @missing_key_payload,@missing_key_presence OUTPUT;
EXEC #build_localizacao_presence @missing_service_payload,@missing_service_presence OUTPUT;
EXEC #build_localizacao_presence @overflow_payload,@overflow_presence OUTPUT;
SELECT @core_payload_before=payload_json,@core_presence_before=field_presence_json,
       @core_hash_before=attribute_hash,@core_freshness_before=freshness_at_utc
FROM core.localizacao_cargas WHERE tenant_scope=@tenant_a AND source_key=@source_key;
DECLARE @mandatory_now DATETIME2(3)=SYSUTCDATETIME(),
        @mandatory_contract CHAR(64)=REPLICATE('b',64),
        @mandatory_configuration CHAR(64)=REPLICATE('c',64);
EXEC ctl.usp_control_plane_start_execution @mandatory,
    '00000000-0000-0000-0000-000000008656',N'LOCAL_SHADOW',N'SYNTHETIC_LOCALIZACAO_8656',
    @tenant_a,N'localizacao_cargas',N'BACKFILL','2036-04-22','2036-04-23',
    N'DATA_EXPORT_RESTART_FROM_BEGINNING',N'dataexport-8656-v1',@mandatory_contract,
    N'localizacao-cargas-shadow-v1',@mandatory_configuration,
    N'localizacao-mandatory-absent',NULL,3600,@mandatory_now;
EXEC ctl.usp_control_plane_record_page @mandatory,1,1,100,3,3,12000,0,@mandatory_now,N'NONE';
EXEC ctl.usp_control_plane_record_page @mandatory,2,1,100,0,0,64,1,@mandatory_now,
    N'DATA_EXPORT_EMPTY_PAGE';
EXEC stg.usp_stage_localizacao_carga_record @mandatory,1,1,NULL,
    @missing_key_payload,@missing_key_presence,N'"2036-04-22T12:00:00-03:00"',
    '2036-04-22T15:00:00',N'VALUE',N'VALID',NULL,NULL,N'ABSENT',N'NOT_PRESENT',
    NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,0,NULL,N'QUARANTINE',N'MISSING_INTEGER_KEY',
    @mandatory_now;
EXEC stg.usp_stage_localizacao_carga_record @mandatory,1,2,@source_key,
    @missing_service_payload,@missing_service_presence,NULL,NULL,N'ABSENT',N'NOT_PRESENT',
    NULL,NULL,N'ABSENT',N'NOT_PRESENT',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,0,NULL,
    N'QUARANTINE',N'MISSING_VALID_SERVICE_AT',@mandatory_now;
EXEC stg.usp_stage_localizacao_carga_record @mandatory,1,3,N'INTEGER:-8656002',
    @overflow_payload,@overflow_presence,N'"2036-04-22T12:00:00-03:00"',
    '2036-04-22T15:00:00',N'VALUE',N'VALID',NULL,NULL,N'ABSENT',N'NOT_PRESENT',
    @overflow_wire,NULL,NULL,NULL,NULL,NULL,NULL,NULL,0,N'UNSOURCED_LEGACY',
    N'QUARANTINE',N'INVALID_TAXED_WEIGHT',@mandatory_now;
EXEC ctl.usp_control_plane_transition_execution @mandatory,N'EXTRACTING',N'EXTRACTED',
    N'EXTRACTION_OK',@mandatory_now;
EXEC ctl.usp_control_plane_transition_execution @mandatory,N'EXTRACTED',N'STAGED',
    N'STAGING_OK',@mandatory_now;
EXEC core.usp_prepare_staged_execution @mandatory,N'dataexport-8656-v1',@mandatory_contract,
    N'localizacao-cargas-shadow-v1',@mandatory_configuration;
IF (SELECT COUNT_BIG(*) FROM stg.localizacao_carga_record
    WHERE execution_id=@mandatory AND validation_disposition=N'QUARANTINE'
      AND payload_json IS NOT NULL AND field_presence_json IS NOT NULL)<>3
   OR (SELECT COUNT_BIG(*) FROM recon.quarantine_record WHERE execution_id=@mandatory)<>3
   OR NOT EXISTS(
       SELECT 1 FROM stg.localizacao_carga_record typed_record
       CROSS APPLY OPENJSON(typed_record.payload_json) payload_property
       CROSS APPLY OPENJSON(JSON_QUERY(typed_record.field_presence_json,
           N'$.fields.taxed_weight')) evidence_property
       WHERE typed_record.execution_id=@mandatory
         AND typed_record.quarantine_reason_code=N'INVALID_TAXED_WEIGHT'
         AND typed_record.taxed_weight_raw=@overflow_wire
         AND payload_property.[key]=N'taxed_weight' AND payload_property.[value]=@overflow_wire
         AND evidence_property.[key]=N'rawWireLexeme'
         AND evidence_property.[value]=@overflow_wire)
   OR EXISTS(SELECT 1 FROM stg.execution_candidate WHERE execution_id=@mandatory)
   OR NOT EXISTS(SELECT 1 FROM core.localizacao_cargas
       WHERE tenant_scope=@tenant_a AND source_key=@source_key)
   OR EXISTS(SELECT 1 FROM core.localizacao_cargas
       WHERE tenant_scope=@tenant_a AND source_key=@source_key
         AND (payload_json<>@core_payload_before OR field_presence_json<>@core_presence_before
           OR attribute_hash<>@core_hash_before OR freshness_at_utc<>@core_freshness_before))
     THROW 52281,N'ABSENT obrigatório não preservou quarentena/evidência/core imutável.',1;

-- A ocorrência de quarentena não segue para promoção. Encerre-a explicitamente para
-- provar a liberação da lease antes de reutilizar a mesma partição sintética.
DECLARE @mandatory_terminal_at DATETIME2(3)=SYSUTCDATETIME();
EXEC ctl.usp_control_plane_transition_execution @mandatory,N'PROMOTED',N'FAILED',
    N'SYNTHETIC_QUARANTINE_VALIDATED',@mandatory_terminal_at;
IF EXISTS(SELECT 1 FROM ctl.execution_lease
    WHERE execution_id=@mandatory AND released_at_utc IS NULL)
    THROW 52288,N'Lease da ocorrência sintética de quarentena não foi liberada.',1;

-- Duplicata idêntica entre páginas deduplica; mesmo service_at divergente é quarentena.
DECLARE @duplicate UNIQUEIDENTIFIER='00000000-0000-0000-0000-000000008665';
DECLARE @conflict UNIQUEIDENTIFIER='00000000-0000-0000-0000-000000008666';
DECLARE @run_now DATETIME2(3)=SYSUTCDATETIME(),@contract CHAR(64)=REPLICATE('b',64),
        @configuration CHAR(64)=REPLICATE('c',64);
DECLARE @duplicate_payload NVARCHAR(MAX)=N'{"corporation_sequence_number":8656002,
 "type":"A","service_at":"2036-04-22T12:00:00-03:00","invoices_volumes":0,
 "taxed_weight":1.000000000,"invoices_value":2.000000000,"total":3.000000000,
 "fit_fln_status":"delivered"}',@duplicate_presence NVARCHAR(MAX);
DECLARE @conflict_payload_a NVARCHAR(MAX)=N'{"corporation_sequence_number":8656003,
 "type":"A","service_at":"2036-04-23T12:00:00-03:00","invoices_volumes":0,
 "taxed_weight":1.000000000,"invoices_value":2.000000000,"total":3.000000000,
 "fit_fln_status":"unknown"}',@conflict_presence_a NVARCHAR(MAX);
DECLARE @conflict_payload_b NVARCHAR(MAX)=N'{"corporation_sequence_number":8656003,
 "type":"B","service_at":"2036-04-23T12:00:00-03:00","invoices_volumes":0,
 "taxed_weight":1.000000000,"invoices_value":2.000000000,"total":3.000000000,
 "fit_fln_status":"unknown"}',@conflict_presence_b NVARCHAR(MAX);
EXEC #build_localizacao_presence @duplicate_payload,@duplicate_presence OUTPUT;
EXEC #build_localizacao_presence @conflict_payload_a,@conflict_presence_a OUTPUT;
EXEC #build_localizacao_presence @conflict_payload_b,@conflict_presence_b OUTPUT;
SET @duplicate_presence=JSON_MODIFY(@duplicate_presence,N'$.fields.taxed_weight.rawWireLexeme',N'1.000000000');
SET @duplicate_presence=JSON_MODIFY(@duplicate_presence,N'$.fields.invoices_value.rawWireLexeme',N'2.000000000');
SET @duplicate_presence=JSON_MODIFY(@duplicate_presence,N'$.fields.total.rawWireLexeme',N'3.000000000');
SET @conflict_presence_a=JSON_MODIFY(@conflict_presence_a,N'$.fields.taxed_weight.rawWireLexeme',N'1.000000000');
SET @conflict_presence_a=JSON_MODIFY(@conflict_presence_a,N'$.fields.invoices_value.rawWireLexeme',N'2.000000000');
SET @conflict_presence_a=JSON_MODIFY(@conflict_presence_a,N'$.fields.total.rawWireLexeme',N'3.000000000');
SET @conflict_presence_b=JSON_MODIFY(@conflict_presence_b,N'$.fields.taxed_weight.rawWireLexeme',N'1.000000000');
SET @conflict_presence_b=JSON_MODIFY(@conflict_presence_b,N'$.fields.invoices_value.rawWireLexeme',N'2.000000000');
SET @conflict_presence_b=JSON_MODIFY(@conflict_presence_b,N'$.fields.total.rawWireLexeme',N'3.000000000');
EXEC ctl.usp_control_plane_start_execution @duplicate,
    '00000000-0000-0000-0000-000000008656',N'LOCAL_SHADOW',N'SYNTHETIC_LOCALIZACAO_8656',
    @tenant_a,N'localizacao_cargas',N'BACKFILL','2036-04-22','2036-04-23',
    N'DATA_EXPORT_RESTART_FROM_BEGINNING',N'dataexport-8656-v1',@contract,
    N'localizacao-cargas-shadow-v1',@configuration,N'localizacao-duplicate-pages',NULL,3600,@run_now;
EXEC ctl.usp_control_plane_record_page @duplicate,1,1,100,1,1,512,0,@run_now,N'NONE';
EXEC ctl.usp_control_plane_record_page @duplicate,2,1,100,1,1,512,0,@run_now,N'NONE';
EXEC ctl.usp_control_plane_record_page @duplicate,3,1,100,0,0,64,1,@run_now,N'DATA_EXPORT_EMPTY_PAGE';
EXEC stg.usp_stage_localizacao_carga_record @duplicate,1,1,N'INTEGER:8656002',@duplicate_payload,
    @duplicate_presence,N'"2036-04-22T12:00:00-03:00"','2036-04-22T15:00:00',N'VALUE',N'VALID',
    N'0',0,N'VALUE',N'VALID',N'1.000000000',1,N'2.000000000',2,N'3.000000000',3,
    N'delivered',N'delivered',1,N'UNSOURCED_LEGACY',N'VALID',NULL,@run_now;
EXEC stg.usp_stage_localizacao_carga_record @duplicate,2,1,N'INTEGER:8656002',@duplicate_payload,
    @duplicate_presence,N'"2036-04-22T12:00:00-03:00"','2036-04-22T15:00:00',N'VALUE',N'VALID',
    N'0',0,N'VALUE',N'VALID',N'1.000000000',1,N'2.000000000',2,N'3.000000000',3,
    N'delivered',N'delivered',1,N'UNSOURCED_LEGACY',N'VALID',NULL,@run_now;
EXEC ctl.usp_control_plane_transition_execution @duplicate,N'EXTRACTING',N'EXTRACTED',N'EXTRACTION_OK',@run_now;
EXEC ctl.usp_control_plane_transition_execution @duplicate,N'EXTRACTED',N'STAGED',N'STAGING_OK',@run_now;
EXEC core.usp_prepare_staged_execution @duplicate,N'dataexport-8656-v1',@contract,
    N'localizacao-cargas-shadow-v1',@configuration;
IF NOT EXISTS(SELECT 1 FROM ctl.execution_promotion_result WHERE execution_id=@duplicate
    AND candidate_rows=1 AND duplicate_rows=1)
    THROW 52271,N'Duplicata entre páginas não foi deduplicada.',1;

EXEC ctl.usp_control_plane_start_execution @conflict,
    '00000000-0000-0000-0000-000000008656',N'LOCAL_SHADOW',N'SYNTHETIC_LOCALIZACAO_8656',
    @tenant_a,N'localizacao_cargas',N'BACKFILL','2036-04-23','2036-04-24',
    N'DATA_EXPORT_RESTART_FROM_BEGINNING',N'dataexport-8656-v1',@contract,
    N'localizacao-cargas-shadow-v1',@configuration,N'localizacao-equal-conflict',NULL,3600,@run_now;
EXEC ctl.usp_control_plane_record_page @conflict,1,1,100,1,1,512,0,@run_now,N'NONE';
EXEC ctl.usp_control_plane_record_page @conflict,2,1,100,1,1,512,0,@run_now,N'NONE';
EXEC ctl.usp_control_plane_record_page @conflict,3,1,100,0,0,64,1,@run_now,N'DATA_EXPORT_EMPTY_PAGE';
EXEC stg.usp_stage_localizacao_carga_record @conflict,1,1,N'INTEGER:8656003',@conflict_payload_a,
    @conflict_presence_a,N'"2036-04-23T12:00:00-03:00"','2036-04-23T15:00:00',N'VALUE',N'VALID',
    N'0',0,N'VALUE',N'VALID',N'1.000000000',1,N'2.000000000',2,N'3.000000000',3,
    N'unknown',N'unknown',0,N'UNSOURCED_LEGACY',N'VALID',NULL,@run_now;
EXEC stg.usp_stage_localizacao_carga_record @conflict,2,1,N'INTEGER:8656003',@conflict_payload_b,
    @conflict_presence_b,N'"2036-04-23T12:00:00-03:00"','2036-04-23T15:00:00',N'VALUE',N'VALID',
    N'0',0,N'VALUE',N'VALID',N'1.000000000',1,N'2.000000000',2,N'3.000000000',3,
    N'unknown',N'unknown',0,N'UNSOURCED_LEGACY',N'VALID',NULL,@run_now;
EXEC ctl.usp_control_plane_transition_execution @conflict,N'EXTRACTING',N'EXTRACTED',N'EXTRACTION_OK',@run_now;
EXEC ctl.usp_control_plane_transition_execution @conflict,N'EXTRACTED',N'STAGED',N'STAGING_OK',@run_now;
EXEC core.usp_prepare_staged_execution @conflict,N'dataexport-8656-v1',@contract,
    N'localizacao-cargas-shadow-v1',@configuration;
IF NOT EXISTS(SELECT 1 FROM ctl.localizacao_carga_promotion_result
    WHERE execution_id=@conflict AND validation_state=N'BLOCKED' AND conflicting_root_keys=1)
   OR (SELECT COUNT_BIG(*) FROM recon.quarantine_record WHERE execution_id=@conflict
       AND reason_code=N'EQUAL_FRESHNESS_CONFLICT')<>2
   OR EXISTS(SELECT 1 FROM stg.execution_candidate WHERE execution_id=@conflict)
    THROW 52272,N'Empate divergente não foi quarentenado.',1;

IF EXISTS(SELECT 1 FROM sys.foreign_keys WHERE referenced_object_id=OBJECT_ID(N'core.frete')
    AND parent_object_id IN(OBJECT_ID(N'stg.localizacao_carga_record'),
                            OBJECT_ID(N'core.localizacao_cargas')))
    THROW 52273,N'Relação Localização--Frete foi materializada indevidamente.',1;
IF EXISTS(SELECT 1 FROM sys.objects object_definition JOIN sys.schemas schema_definition
    ON schema_definition.schema_id=object_definition.schema_id
    WHERE schema_definition.name=N'pub' AND object_definition.name LIKE N'%localizacao%')
    THROW 52274,N'Objeto pub de Localização foi criado.',1;
IF EXISTS(SELECT 1 FROM core.localizacao_cargas WHERE active=0)
    THROW 52275,N'Prune/sweep alterou active.',1;

IF XACT_STATE()<>1 OR @@TRANCOUNT<>1
    THROW 52276,N'O escopo rollback-only de Localização foi consumido.',1;
ROLLBACK TRANSACTION;
IF @@TRANCOUNT<>0 THROW 52277,N'O rollback de Localização não encerrou a transação.',1;
PRINT N'Localização V2-028: identidade, tri-state, números, service_at, status, replay, stale, empate e tenants exercitados e revertidos.';
