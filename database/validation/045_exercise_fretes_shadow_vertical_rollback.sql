-- Exercício sintético V2-011. Toda escrita é revertida; nenhum ID/payload de negócio é usado.
:setvar DatabaseName "ETL_SISTEMA_V2_SHADOW"
:On Error exit

IF DB_NAME()<>N'$(DatabaseName)'
    THROW 52160,N'O exercício de Fretes aceita somente ETL_SISTEMA_V2_SHADOW.',1;

SET NOCOUNT ON;
SET XACT_ABORT ON;
BEGIN TRANSACTION;

:r "..\transition\001_reset_historical_shadow_for_schema_foundation.sql"
:r "..\baseline\001_schema_foundation_baseline.sql"
GO
:r "005_validate_progressive_data_gate.sql"
GO
:r "044_validate_fretes_shadow_vertical.sql"
GO

DECLARE @now DATETIME2(3)=SYSUTCDATETIME();
DECLARE @source NVARCHAR(128)=N'SYNTHETIC_FRETES_6389';
DECLARE @cycle_fingerprint CHAR(64)=REPLICATE('a',64);
EXEC ctl.usp_control_plane_register_source @source,N'DATA_EXPORT',@now;
EXEC ctl.usp_control_plane_start_cycle
    '00000000-0000-0000-0000-000000006389',N'synthetic-fretes-plan-v1',
    @cycle_fingerprint,@now;
GO

CREATE OR ALTER PROCEDURE #install_frete_dq
    @tenant NVARCHAR(128),
    @mode NVARCHAR(16),
    @suffix NVARCHAR(32)
AS
BEGIN
    SET NOCOUNT ON;
    DECLARE @effective DATETIME2(3)=DATEADD(DAY,-1,SYSUTCDATETIME());
    DECLARE @environment NVARCHAR(32)=N'LOCAL_SHADOW';
    DECLARE @source NVARCHAR(128)=N'SYNTHETIC_FRETES_6389';
    DECLARE @entity NVARCHAR(128)=N'fretes';
    DECLARE @scope CHAR(64)=LOWER(CONVERT(CHAR(64),HASHBYTES(
        'SHA2_256',CONVERT(VARBINARY(MAX),CONCAT(
            N'dq-scope-v1|',DATALENGTH(@environment),N':',@environment,
            N'|',DATALENGTH(@source),N':',@source,
            N'|',DATALENGTH(@tenant),N':',@tenant,
            N'|',DATALENGTH(@entity),N':',@entity,
            N'|',DATALENGTH(@mode),N':',@mode))),2));
    DECLARE @version NVARCHAR(128)=CONCAT(N'synthetic-fretes-dq-',@suffix);
    DECLARE @fingerprint CHAR(64)=LOWER(CONVERT(CHAR(64),HASHBYTES(
        'SHA2_256',CONVERT(VARBINARY(MAX),CONCAT(
            N'dq-policy-v1|',DATALENGTH(@version),N':',@version,N'|',@scope,
            N'|4|3600|',DATALENGTH(N'quality-owner'),N':quality-owner|',
            DATALENGTH(N'quarantine-owner'),N':quarantine-owner|',
            DATALENGTH(N'synthetic-fretes-retention-v1'),
            N':synthetic-fretes-retention-v1|',
            DATALENGTH(N'retention-owner'),N':retention-owner|',
            CONVERT(NVARCHAR(33),@effective,126),
            N'|1:COUNT_EQUATION:0:0:',DATALENGTH(N'quality-owner'),N':quality-owner',
            N'|2:PAGE_TERMINALITY:0:0:',DATALENGTH(N'quality-owner'),N':quality-owner',
            N'|3:PROMOTION_RECONCILIATION:0:0:',DATALENGTH(N'quality-owner'),N':quality-owner',
            N'|4:QUARANTINE_SLA:0:0:',DATALENGTH(N'quality-owner'),N':quality-owner'))),2));
    INSERT ctl.data_quality_policy VALUES(
        @version,@fingerprint,@scope,4,3600,N'quality-owner',N'quarantine-owner',
        N'synthetic-fretes-retention-v1',N'retention-owner',N'RATIFIED',@effective,
        SYSUTCDATETIME()
    );
    INSERT ctl.data_quality_check_policy VALUES
        (@version,@fingerprint,1,N'COUNT_EQUATION',0,0,N'quality-owner'),
        (@version,@fingerprint,2,N'PAGE_TERMINALITY',0,0,N'quality-owner'),
        (@version,@fingerprint,3,N'PROMOTION_RECONCILIATION',0,0,N'quality-owner'),
        (@version,@fingerprint,4,N'QUARANTINE_SLA',0,0,N'quality-owner');
END;
GO

EXEC #install_frete_dq N'SYNTHETIC_FRETES_TENANT_A',N'BACKFILL',N'a-backfill';
EXEC #install_frete_dq N'SYNTHETIC_FRETES_TENANT_A',N'REPLAY',N'a-replay';
EXEC #install_frete_dq N'SYNTHETIC_FRETES_TENANT_B',N'BACKFILL',N'b-backfill';
GO

CREATE OR ALTER PROCEDURE #run_frete
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
    @sidecar_json NVARCHAR(MAX)
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    DECLARE @now DATETIME2(3)=SYSUTCDATETIME();
    DECLARE @contract CHAR(64)=REPLICATE('b',64),@configuration CHAR(64)=REPLICATE('c',64);
    DECLARE @environment NVARCHAR(32)=N'LOCAL_SHADOW',
            @source NVARCHAR(128)=N'SYNTHETIC_FRETES_6389',@entity NVARCHAR(128)=N'fretes';
    DECLARE @scope CHAR(64)=LOWER(CONVERT(CHAR(64),HASHBYTES(
        'SHA2_256',CONVERT(VARBINARY(MAX),CONCAT(
            N'dq-scope-v1|',DATALENGTH(@environment),N':',@environment,
            N'|',DATALENGTH(@source),N':',@source,N'|',DATALENGTH(@tenant),N':',@tenant,
            N'|',DATALENGTH(@entity),N':',@entity,N'|',DATALENGTH(@mode),N':',@mode))),2));
    DECLARE @policy_version NVARCHAR(128),@policy_fingerprint CHAR(64);
    SELECT @policy_version=policy_version,@policy_fingerprint=policy_fingerprint
    FROM ctl.data_quality_policy WHERE scope_fingerprint=@scope;
    EXEC ctl.usp_control_plane_start_execution
        @execution_id,'00000000-0000-0000-0000-000000006389',@environment,@source,
        @tenant,@entity,@mode,@window_start,@window_end,
        N'DATA_EXPORT_RESTART_FROM_BEGINNING',N'dataexport-6389-v1',@contract,
        N'fretes-shadow-v1',@configuration,@idempotency,@replay_of,3600,@now;
    EXEC ctl.usp_control_plane_record_page
        @execution_id,1,1,100,1,1,512,0,@now,N'NONE';
    EXEC ctl.usp_control_plane_record_page
        @execution_id,2,1,100,0,0,64,1,@now,N'DATA_EXPORT_EMPTY_PAGE';
    EXEC stg.usp_stage_frete_record
        @execution_id,1,1,@source_key,@payload_json,@field_presence_json,
        @business_alias_json,@status_raw,@status_code,@status_label,@terminal,
        @freshness_evidence_json,@freshness_at_utc,@freshness_origin,@service_at_utc,
        @performance_evidence_json,@performance_at_utc,@performance_origin,
        @cte_finalizations_json,@financial_json,@relation_candidates_json,@sidecar_json,
        N'VALID',NULL,@now;
    EXEC ctl.usp_control_plane_transition_execution
        @execution_id,N'EXTRACTING',N'EXTRACTED',N'EXTRACTION_OK',@now;
    EXEC ctl.usp_control_plane_transition_execution
        @execution_id,N'EXTRACTED',N'STAGED',N'STAGING_OK',@now;
    EXEC core.usp_prepare_frete_candidate_set
        @execution_id,N'dataexport-6389-v1',@contract,N'fretes-shadow-v1',@configuration;
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
        THROW 52161,N'Data Quality sintética de Fretes não passou.',1;
    EXEC core.usp_apply_reconcile_publish_fretes
        @execution_id,N'dataexport-6389-v1',@contract,N'fretes-shadow-v1',@configuration;
END;
GO

DECLARE @presence_value NVARCHAR(MAX)=N'{
 "id":"VALUE","updated_at":"VALUE","reference_number":"VALUE",
 "fit_p_m_pck_sequence_code":"VALUE","corporation_sequence_number":"VALUE",
 "finished_at":"VALUE","fit_dpn_performance_finished_at":"VALUE","status":"VALUE",
 "cte_created_at":"VALUE","cte_issued_at":"ABSENT","criado_em":"ABSENT",
 "servico_em":"VALUE","cte":"VALUE","ctes":"ABSENT","cte_key":"ABSENT",
 "finalizations":"VALUE","finalizacoes":"ABSENT","total":"VALUE"}';
DECLARE @presence_absent NVARCHAR(MAX)=N'{
 "id":"VALUE","updated_at":"ABSENT","reference_number":"ABSENT",
 "fit_p_m_pck_sequence_code":"ABSENT","corporation_sequence_number":"ABSENT",
 "finished_at":"ABSENT","fit_dpn_performance_finished_at":"ABSENT","status":"VALUE",
 "cte_created_at":"VALUE","cte_issued_at":"ABSENT","criado_em":"ABSENT",
 "servico_em":"VALUE","cte":"ABSENT","ctes":"ABSENT","cte_key":"ABSENT",
 "finalizations":"ABSENT","finalizacoes":"ABSENT","total":"ABSENT"}';
DECLARE @alias_seed NVARCHAR(MAX)=N'{"path":"/corporation_sequence_number","presence":"VALUE","typedInteger":"6389009","role":"VERSIONED_NON_TECHNICAL_ALIAS_NEVER_IDENTITY","marker":"SEED_ALIAS"}';
DECLARE @alias_absent NVARCHAR(MAX)=N'{"path":"/corporation_sequence_number","presence":"ABSENT","role":"VERSIONED_NON_TECHNICAL_ALIAS_NEVER_IDENTITY"}';
DECLARE @fresh_seed NVARCHAR(MAX)=N'{"precedence":"cte_created_at>cte_issued_at>criado_em>servico_em","evidenceScope":"SYNTHETIC_V09_NOT_SOURCE_CONTRACT_EVIDENCE","updated_at":"IGNORED_UNVERIFIED","cte_created_at":{"presence":"VALUE","parseState":"VALID"},"cte_issued_at":{"presence":"ABSENT","parseState":"NOT_PRESENT"},"criado_em":{"presence":"ABSENT","parseState":"NOT_PRESENT"},"servico_em":{"presence":"VALUE","parseState":"VALID"}}';
DECLARE @performance_seed NVARCHAR(MAX)=N'{"precedence":"OFFICIAL_6389_THEN_FINISHED_AT","official":{"presence":"VALUE","provenance":"PERFORMANCE_6389"},"fallback":{"presence":"VALUE","provenance":"DECLARED_FALLBACK"},"selectedOrigin":"OFFICIAL_6389","parseState":"VALID","marker":"SEED_PERFORMANCE"}';
DECLARE @performance_absent NVARCHAR(MAX)=N'{"precedence":"OFFICIAL_6389_THEN_FINISHED_AT","official":{"presence":"ABSENT","provenance":"PERFORMANCE_6389"},"fallback":{"presence":"ABSENT","provenance":"DECLARED_FALLBACK"},"selectedOrigin":"NONE","parseState":"NOT_PRESENT"}';
DECLARE @cte_seed NVARCHAR(MAX)=N'{"policy":"CTE_FINALIZATIONS","evidenceScope":"SYNTHETIC_V09_NOT_SOURCE_CONTRACT_EVIDENCE","status":{"presence":"VALUE","provenance":"PRESERVED"},"marker":"SEED_CTE"}';
DECLARE @cte_absent NVARCHAR(MAX)=N'{"policy":"CTE_FINALIZATIONS","evidenceScope":"SYNTHETIC_V09_NOT_SOURCE_CONTRACT_EVIDENCE","status":{"presence":"VALUE","provenance":"PRESERVED"}}';
DECLARE @financial_seed NVARCHAR(MAX)=N'{"evidenceScope":"SYNTHETIC_V09_NOT_SOURCE_CONTRACT_EVIDENCE","currency":"UNRESOLVED_NO_INFERENCE","unit":"UNRESOLVED_NO_INFERENCE","arithmetic":"FORBIDDEN","marker":"SEED_FINANCIAL","reference_number":{"presence":"VALUE","raw":"SYNTHETIC"},"total":{"presence":"VALUE","typedDecimal":"10.2500"}}';
DECLARE @financial_absent NVARCHAR(MAX)=N'{"evidenceScope":"SYNTHETIC_V09_NOT_SOURCE_CONTRACT_EVIDENCE","currency":"UNRESOLVED_NO_INFERENCE","unit":"UNRESOLVED_NO_INFERENCE","arithmetic":"FORBIDDEN"}';
DECLARE @relation_seed NVARCHAR(MAX)=N'{"policy":"UNRESOLVED_RELATION_CANDIDATES_V2_046B","evidenceScope":"CONTRACTED_DATAEXPORT_6389_PATHS_ONLY","fit_p_m_pck_sequence_code":{"presence":"VALUE","raw":11,"provenance":"DATA_EXPORT_6389"},"marker":"SEED_CANDIDATE"}';
DECLARE @relation_absent NVARCHAR(MAX)=N'{"policy":"UNRESOLVED_RELATION_CANDIDATES_V2_046B","evidenceScope":"CONTRACTED_DATAEXPORT_6389_PATHS_ONLY","fit_p_m_pck_sequence_code":{"presence":"ABSENT","provenance":"DATA_EXPORT_6389"}}';
DECLARE @sidecar_seed NVARCHAR(MAX)=N'{"catalogVersion":"fretes-graphql-sidecar-v1","relationPolicy":"PRESERVE_ONLY_V2_046B_OWNS_CROSSWALK","edgesPresence":"VALUE","edges":[{"id":{"path":"/freight/edges/node/id","presence":"VALUE","raw":6389},"accountingCreditId":{"path":"/freight/edges/node/accountingCreditId","presence":"VALUE","raw":1},"accountingCreditInstallmentId":{"path":"/freight/edges/node/accountingCreditInstallmentId","presence":"VALUE","raw":2},"referenceNumber":{"path":"/freight/edges/node/referenceNumber","presence":"VALUE","raw":"SYNTHETIC"},"cte":{"key":{"path":"/freight/edges/node/cte/key","presence":"VALUE","raw":"SYNTHETIC"}},"total":{"path":"/freight/edges/node/total","presence":"VALUE","raw":"10.2500"},"corporationSequenceNumber":{"path":"/freight/edges/node/corporationSequenceNumber","presence":"VALUE","raw":6389009},"pickItemId":{"path":"/freight/edges/node/pickItemId","presence":"VALUE","raw":11},"pickItemResolution":"UNRESOLVED_CANDIDATE_ONLY"}],"pageInfo":{"presence":"VALUE","hasNextPage":{"path":"/freight/pageInfo/hasNextPage","presence":"VALUE","raw":false},"endCursor":{"path":"/freight/pageInfo/endCursor","presence":"VALUE","raw":"SYNTHETIC"}}}';
DECLARE @sidecar_absent NVARCHAR(MAX)=N'{"catalogVersion":"fretes-graphql-sidecar-v1","relationPolicy":"PRESERVE_ONLY_V2_046B_OWNS_CROSSWALK","edgesPresence":"ABSENT","edges":[],"pageInfo":{"presence":"ABSENT","hasNextPage":{"path":"/freight/pageInfo/hasNextPage","presence":"ABSENT"},"endCursor":{"path":"/freight/pageInfo/endCursor","presence":"ABSENT"}}}';
DECLARE @tenant_a NVARCHAR(128)=N'SYNTHETIC_FRETES_TENANT_A';
DECLARE @tenant_b NVARCHAR(128)=N'SYNTHETIC_FRETES_TENANT_B';
DECLARE @source_key NVARCHAR(256)=N'INTEGER:6389001';
DECLARE @seed UNIQUEIDENTIFIER='00000000-0000-0000-0000-000000006390';
DECLARE @replay UNIQUEIDENTIFIER='00000000-0000-0000-0000-000000006391';
DECLARE @stale UNIQUEIDENTIFIER='00000000-0000-0000-0000-000000006392';
DECLARE @terminal_guard UNIQUEIDENTIFIER='00000000-0000-0000-0000-000000006393';
DECLARE @other_tenant UNIQUEIDENTIFIER='00000000-0000-0000-0000-000000006394';

-- Barreiras negativas ocorrem antes de qualquer escrita.
DECLARE @caught BIT=0;
DECLARE @negative_now DATETIME2(3)=SYSUTCDATETIME();
SET XACT_ABORT OFF;
BEGIN TRY
    EXEC stg.usp_stage_frete_record
        '00000000-0000-0000-0000-000000006399',1,1,N'INTEGER:01',N'{}',@presence_value,
        @alias_seed,N'done',N'done',N'finalizado',1,@fresh_seed,'2036-03-20T12:00:00',
        N'CTE_CREATED_AT','2036-03-17T12:00:00',@performance_seed,'2036-03-19T18:42:10',
        N'OFFICIAL_6389',@cte_seed,@financial_seed,@relation_seed,@sidecar_seed,N'VALID',NULL,
        @negative_now;
END TRY BEGIN CATCH IF ERROR_NUMBER()=52121 SET @caught=1 ELSE THROW; END CATCH;
IF @caught=0 THROW 52162,N'Chave não canônica alcançou Fretes.',1;
SET @caught=0;
BEGIN TRY
    EXEC stg.usp_stage_frete_record
        '00000000-0000-0000-0000-000000006399',1,101,N'INTEGER:1',N'{}',@presence_value,
        @alias_seed,N'done',N'done',N'finalizado',1,@fresh_seed,'2036-03-20T12:00:00',
        N'CTE_CREATED_AT','2036-03-17T12:00:00',@performance_seed,'2036-03-19T18:42:10',
        N'OFFICIAL_6389',@cte_seed,@financial_seed,@relation_seed,@sidecar_seed,N'VALID',NULL,
        @negative_now;
END TRY BEGIN CATCH IF ERROR_NUMBER()=52120 SET @caught=1 ELSE THROW; END CATCH;
IF @caught=0 THROW 52163,N'Página acima de 100 alcançou Fretes.',1;
SET XACT_ABORT ON;

EXEC #run_frete @seed,'2036-03-20','2036-03-21',N'fretes-seed',N'BACKFILL',NULL,
    @tenant_a,@source_key,N'{"id":6389001,"state":"SEED"}',@presence_value,@alias_seed,
    N'done',N'done',N'finalizado',1,@fresh_seed,'2036-03-20T12:00:00',N'CTE_CREATED_AT',
    '2036-03-17T12:00:00',@performance_seed,'2036-03-19T18:42:10',N'OFFICIAL_6389',
    @cte_seed,@financial_seed,@relation_seed,@sidecar_seed;
IF NOT EXISTS(
    SELECT 1 FROM core.frete root_record
    JOIN core.frete_performance performance_record ON performance_record.frete_id=root_record.frete_id
    WHERE root_record.tenant_scope=@tenant_a AND root_record.source_key=@source_key
      AND root_record.terminal=1 AND root_record.status_code=N'done'
      AND root_record.business_alias_json LIKE N'%SEED_ALIAS%'
      AND root_record.financial_json LIKE N'%SEED_FINANCIAL%'
      AND root_record.relation_candidates_json LIKE N'%SEED_CANDIDATE%'
      AND performance_record.performance_origin=N'OFFICIAL_6389'
) THROW 52164,N'Seed tipado de Fretes não foi promovido.',1;
IF (SELECT COUNT_BIG(*) FROM recon.frete_coleta_relation_candidate
    WHERE execution_id=@seed)<>2
    THROW 52165,N'Candidatos raiz/sidecar não foram preservados separadamente.',1;

EXEC #run_frete @replay,'2036-03-20','2036-03-21',N'fretes-replay',N'REPLAY',@seed,
    @tenant_a,@source_key,N'{"id":6389001,"state":"SEED"}',@presence_value,@alias_seed,
    N'done',N'done',N'finalizado',1,@fresh_seed,'2036-03-20T12:00:00',N'CTE_CREATED_AT',
    '2036-03-17T12:00:00',@performance_seed,'2036-03-19T18:42:10',N'OFFICIAL_6389',
    @cte_seed,@financial_seed,@relation_seed,@sidecar_seed;
IF NOT EXISTS(SELECT 1 FROM recon.execution_candidate_application
    WHERE execution_id=@replay AND application_disposition=N'NO_OP')
    THROW 52166,N'Replay idêntico não virou no-op.',1;

EXEC #run_frete @stale,'2036-03-18','2036-03-19',N'fretes-stale',N'BACKFILL',NULL,
    @tenant_a,@source_key,N'{"id":6389001,"state":"OLDER"}',@presence_value,@alias_seed,
    N'finished',N'finished',N'finalizado',1,@fresh_seed,'2036-03-19T12:00:00',N'CTE_CREATED_AT',
    '2036-03-16T12:00:00',@performance_seed,'2036-03-18T18:42:10',N'OFFICIAL_6389',
    @cte_seed,@financial_seed,@relation_seed,@sidecar_seed;
IF NOT EXISTS(SELECT 1 FROM recon.execution_candidate_application
    WHERE execution_id=@stale AND application_disposition=N'STALE_NO_OP')
   OR NOT EXISTS(SELECT 1 FROM core.frete WHERE tenant_scope=@tenant_a
       AND source_key=@source_key AND payload_json LIKE N'%SEED%')
    THROW 52167,N'Out-of-order regrediu a raiz.',1;

EXEC #run_frete @terminal_guard,'2036-03-21','2036-03-22',N'fretes-terminal-guard',
    N'BACKFILL',NULL,@tenant_a,@source_key,N'{"id":6389001,"status":"open"}',
    @presence_absent,@alias_absent,N'open',NULL,NULL,0,@fresh_seed,
    '2036-03-21T12:00:00',N'CTE_CREATED_AT','2036-03-18T12:00:00',
    @performance_absent,NULL,NULL,@cte_absent,@financial_absent,@relation_absent,@sidecar_absent;
IF NOT EXISTS(SELECT 1 FROM core.frete WHERE tenant_scope=@tenant_a AND source_key=@source_key
      AND terminal=1 AND status_code=N'done' AND business_alias_json LIKE N'%SEED_ALIAS%'
      AND financial_json LIKE N'%SEED_FINANCIAL%'
      AND relation_candidates_json LIKE N'%SEED_CANDIDATE%')
   OR NOT EXISTS(SELECT 1 FROM recon.frete_terminal_transition_observation
      WHERE execution_id=@terminal_guard AND transition_action=N'TERMINAL_REGRESSION_BLOCKED')
    THROW 52168,N'Terminalidade ou ABSENT regrediu atributos conhecidos.',1;

EXEC #run_frete @other_tenant,'2036-03-21','2036-03-22',N'fretes-other-tenant',
    N'BACKFILL',NULL,@tenant_b,@source_key,N'{"id":6389001,"state":"TENANT_B"}',
    @presence_value,@alias_seed,N'done',N'done',N'finalizado',1,@fresh_seed,
    '2036-03-21T12:00:00',N'CTE_CREATED_AT','2036-03-18T12:00:00',
    @performance_seed,'2036-03-20T18:42:10',N'OFFICIAL_6389',@cte_seed,
    @financial_seed,@relation_seed,@sidecar_seed;
IF (SELECT COUNT_BIG(*) FROM core.frete WHERE source_key=@source_key)<>2
    THROW 52169,N'Isolamento por tenant não criou raízes independentes.',1;

-- Duplicata idêntica entre páginas deduplica; empate divergente vai à quarentena.
DECLARE @duplicate UNIQUEIDENTIFIER='00000000-0000-0000-0000-000000006395';
DECLARE @conflict UNIQUEIDENTIFIER='00000000-0000-0000-0000-000000006396';
DECLARE @run_now DATETIME2(3)=SYSUTCDATETIME(),@contract CHAR(64)=REPLICATE('b',64),
        @configuration CHAR(64)=REPLICATE('c',64);
EXEC ctl.usp_control_plane_start_execution @duplicate,
    '00000000-0000-0000-0000-000000006389',N'LOCAL_SHADOW',N'SYNTHETIC_FRETES_6389',
    @tenant_a,N'fretes',N'BACKFILL','2036-03-22','2036-03-23',
    N'DATA_EXPORT_RESTART_FROM_BEGINNING',N'dataexport-6389-v1',@contract,
    N'fretes-shadow-v1',@configuration,N'fretes-duplicate-pages',NULL,3600,@run_now;
EXEC ctl.usp_control_plane_record_page @duplicate,1,1,100,1,1,512,0,@run_now,N'NONE';
EXEC ctl.usp_control_plane_record_page @duplicate,2,1,100,1,1,512,0,@run_now,N'NONE';
EXEC ctl.usp_control_plane_record_page @duplicate,3,1,100,0,0,64,1,@run_now,N'DATA_EXPORT_EMPTY_PAGE';
EXEC stg.usp_stage_frete_record @duplicate,1,1,N'INTEGER:6389002',N'{"id":6389002}',
    @presence_value,@alias_seed,N'done',N'done',N'finalizado',1,@fresh_seed,
    '2036-03-22T12:00:00',N'CTE_CREATED_AT','2036-03-22T12:00:00',@performance_seed,
    '2036-03-22T13:00:00',N'OFFICIAL_6389',@cte_seed,@financial_seed,@relation_seed,
    @sidecar_seed,N'VALID',NULL,@run_now;
EXEC stg.usp_stage_frete_record @duplicate,2,1,N'INTEGER:6389002',N'{"id":6389002}',
    @presence_value,@alias_seed,N'done',N'done',N'finalizado',1,@fresh_seed,
    '2036-03-22T12:00:00',N'CTE_CREATED_AT','2036-03-22T12:00:00',@performance_seed,
    '2036-03-22T13:00:00',N'OFFICIAL_6389',@cte_seed,@financial_seed,@relation_seed,
    @sidecar_seed,N'VALID',NULL,@run_now;
EXEC ctl.usp_control_plane_transition_execution @duplicate,N'EXTRACTING',N'EXTRACTED',N'EXTRACTION_OK',@run_now;
EXEC ctl.usp_control_plane_transition_execution @duplicate,N'EXTRACTED',N'STAGED',N'STAGING_OK',@run_now;
EXEC core.usp_prepare_frete_candidate_set @duplicate,N'dataexport-6389-v1',@contract,
    N'fretes-shadow-v1',@configuration;
IF NOT EXISTS(SELECT 1 FROM ctl.execution_promotion_result WHERE execution_id=@duplicate
    AND candidate_rows=1 AND duplicate_rows=1)
    THROW 52170,N'Duplicata entre páginas não foi deduplicada.',1;

EXEC ctl.usp_control_plane_start_execution @conflict,
    '00000000-0000-0000-0000-000000006389',N'LOCAL_SHADOW',N'SYNTHETIC_FRETES_6389',
    @tenant_a,N'fretes',N'BACKFILL','2036-03-23','2036-03-24',
    N'DATA_EXPORT_RESTART_FROM_BEGINNING',N'dataexport-6389-v1',@contract,
    N'fretes-shadow-v1',@configuration,N'fretes-equal-conflict',NULL,3600,@run_now;
EXEC ctl.usp_control_plane_record_page @conflict,1,1,100,1,1,512,0,@run_now,N'NONE';
EXEC ctl.usp_control_plane_record_page @conflict,2,1,100,1,1,512,0,@run_now,N'NONE';
EXEC ctl.usp_control_plane_record_page @conflict,3,1,100,0,0,64,1,@run_now,N'DATA_EXPORT_EMPTY_PAGE';
EXEC stg.usp_stage_frete_record @conflict,1,1,N'INTEGER:6389003',N'{"id":6389003,"v":"A"}',
    @presence_value,@alias_seed,N'done',N'done',N'finalizado',1,@fresh_seed,
    '2036-03-23T12:00:00',N'CTE_CREATED_AT','2036-03-23T12:00:00',@performance_seed,
    '2036-03-23T13:00:00',N'OFFICIAL_6389',@cte_seed,@financial_seed,@relation_seed,
    @sidecar_seed,N'VALID',NULL,@run_now;
EXEC stg.usp_stage_frete_record @conflict,2,1,N'INTEGER:6389003',N'{"id":6389003,"v":"B"}',
    @presence_value,@alias_seed,N'done',N'done',N'finalizado',1,@fresh_seed,
    '2036-03-23T12:00:00',N'CTE_CREATED_AT','2036-03-23T12:00:00',@performance_seed,
    '2036-03-23T13:00:00',N'OFFICIAL_6389',@cte_seed,@financial_seed,@relation_seed,
    @sidecar_seed,N'VALID',NULL,@run_now;
EXEC ctl.usp_control_plane_transition_execution @conflict,N'EXTRACTING',N'EXTRACTED',N'EXTRACTION_OK',@run_now;
EXEC ctl.usp_control_plane_transition_execution @conflict,N'EXTRACTED',N'STAGED',N'STAGING_OK',@run_now;
EXEC core.usp_prepare_staged_execution @conflict,N'dataexport-6389-v1',@contract,
    N'fretes-shadow-v1',@configuration;
IF NOT EXISTS(SELECT 1 FROM ctl.frete_promotion_result WHERE execution_id=@conflict
    AND validation_state=N'BLOCKED' AND conflicting_root_keys=1)
   OR (SELECT COUNT_BIG(*) FROM recon.quarantine_record WHERE execution_id=@conflict
       AND reason_code=N'EQUAL_FRESHNESS_CONFLICT')<>2
   OR EXISTS(SELECT 1 FROM stg.execution_candidate WHERE execution_id=@conflict)
    THROW 52171,N'Empate divergente não foi quarentenado.',1;

-- Sidecar ilimitado é bloqueado mesmo que exista raiz tipada.
DECLARE @too_many NVARCHAR(MAX)=N'{"catalogVersion":"fretes-graphql-sidecar-v1","relationPolicy":"PRESERVE_ONLY_V2_046B_OWNS_CROSSWALK","edgesPresence":"VALUE","edges":[';
DECLARE @counter INT=1;
WHILE @counter<=101
BEGIN
    SET @too_many=CONCAT(@too_many,CASE WHEN @counter>1 THEN N',' ELSE N'' END,N'{}');
    SET @counter+=1;
END;
SET @too_many+=N'],"pageInfo":{"presence":"ABSENT"}}';
DECLARE @seed_stage BIGINT=(SELECT MIN(stage_record_id) FROM stg.frete_record WHERE execution_id=@seed);
SET @caught=0;
SET XACT_ABORT OFF;
BEGIN TRY EXEC stg.usp_stage_frete_sidecar @seed,@seed_stage,@too_many,@run_now;
END TRY BEGIN CATCH IF ERROR_NUMBER()=52111 SET @caught=1 ELSE THROW; END CATCH;
IF @caught=0 THROW 52172,N'Sidecar acima de 100 foi aceito.',1;
SET XACT_ABORT ON;

IF EXISTS(SELECT 1 FROM sys.foreign_keys WHERE referenced_object_id=OBJECT_ID(N'core.coleta')
    AND parent_object_id IN(OBJECT_ID(N'core.frete'),OBJECT_ID(N'recon.frete_coleta_relation_candidate')))
    THROW 52173,N'Relação Coleta--Frete foi materializada indevidamente.',1;
IF EXISTS(SELECT 1 FROM sys.objects object_definition JOIN sys.schemas schema_definition
    ON schema_definition.schema_id=object_definition.schema_id
    WHERE schema_definition.name=N'pub' AND object_definition.name LIKE N'%frete%')
    THROW 52174,N'Objeto pub de Fretes foi criado.',1;
IF EXISTS(SELECT 1 FROM core.frete WHERE active=0)
    THROW 52175,N'Prune/sweep alterou active.',1;

IF XACT_STATE()<>1 OR @@TRANCOUNT<>1
    THROW 52176,N'O escopo rollback-only de Fretes foi consumido.',1;
ROLLBACK TRANSACTION;
IF @@TRANCOUNT<>0 THROW 52177,N'O rollback de Fretes não encerrou a transação.',1;
PRINT N'Fretes V2-011: identidade, tri-state, frescor, performance, terminalidade, replay, stale, empate, sidecar, candidatos e tenants exercitados e revertidos.';
