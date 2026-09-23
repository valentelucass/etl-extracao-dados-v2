-- FRE-02/FRE-03 regression against the installed procedures, with rollback only.
-- sqlcmd variable TestCase; local sysadmin/db_owner synthetic validation path, no authority changes.
:On Error exit
SET NOCOUNT ON;
SET XACT_ABORT ON;
SET LOCK_TIMEOUT 5000;
IF DB_NAME()<>N'ETL_SISTEMA_V2_SHADOW' OR CONNECTIONPROPERTY('auth_scheme') NOT IN(N'NTLM',N'KERBEROS')
    THROW 52990,N'LOCAL_SHADOW_REQUIRED',1;
BEGIN TRANSACTION;
GO
DECLARE @now DATETIME2(3)=SYSUTCDATETIME();
DECLARE @source NVARCHAR(128)=N'SYNTHETIC_FRETE_TERMINAL_V099';
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
    DECLARE @source NVARCHAR(128)=N'SYNTHETIC_FRETE_TERMINAL_V099';
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
            @source NVARCHAR(128)=N'SYNTHETIC_FRETE_TERMINAL_V099',@entity NVARCHAR(128)=N'fretes';
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

DECLARE @case NVARCHAR(32)=N'$(TestCase)';
IF @case NOT IN(N'done',N'finished',N'canceled',N'cancelled',N'equal',N'issued',N'null_cte',N'null_date',N'older_cte',N'nonterminal',N'fallback_current',N'other_tenant',N'conflict',N'newer',N'fallback_performance',N'partial_finalization')
    THROW 52990,N'UNKNOWN_SYNTHETIC_CASE',1;
DECLARE @seed UNIQUEIDENTIFIER=NEWID(),@next UNIQUEIDENTIFIER=NEWID();
DECLARE @tenant NVARCHAR(128)=N'SYNTHETIC_FRETES_TENANT_A';
DECLARE @key NVARCHAR(256)=N'INTEGER:6389001';
DECLARE @current_at DATETIME2(3)='2036-03-20T12:00:06',@observed_at DATETIME2(3)='2036-03-20T12:00:00';
DECLARE @current_origin NVARCHAR(32)=CASE @case WHEN N'issued' THEN N'CTE_ISSUED_AT' WHEN N'fallback_current' THEN N'CRIADO_EM' ELSE N'CTE_CREATED_AT' END;
DECLARE @candidate_origin NVARCHAR(32)=N'CRIADO_EM';
DECLARE @status NVARCHAR(32)=CASE WHEN @case IN(N'finished',N'canceled',N'cancelled') THEN @case WHEN @case=N'nonterminal' THEN N'pending' ELSE N'done' END;
DECLARE @terminal BIT=CASE WHEN @case=N'nonterminal' THEN 0 ELSE 1 END;
DECLARE @label NVARCHAR(64)=CASE WHEN @status IN(N'canceled',N'cancelled') THEN N'cancelada' WHEN @terminal=1 THEN N'finalizado' END;
DECLARE @code NVARCHAR(32)=CASE WHEN @terminal=1 THEN @status END;
DECLARE @old_payload NVARCHAR(MAX)=N'{"id":6389001,"status":"pending","synthetic_fixture":true}';
DECLARE @new_payload NVARCHAR(MAX)=JSON_MODIFY(N'{"id":6389001,"synthetic_fixture":true}',N'$.status',@status);
IF @case=N'issued' SET @presence_value=JSON_MODIFY(JSON_MODIFY(@presence_value,N'$.cte_created_at',N'ABSENT'),N'$.cte_issued_at',N'VALUE');
IF @case=N'fallback_current' SET @presence_value=JSON_MODIFY(JSON_MODIFY(@presence_value,N'$.cte_created_at',N'ABSENT'),N'$.criado_em',N'VALUE');
IF @case IN(N'issued',N'fallback_current') BEGIN
    SET @fresh_seed=JSON_MODIFY(@fresh_seed,N'$.cte_created_at.presence',N'ABSENT');
    SET @fresh_seed=JSON_MODIFY(@fresh_seed,CONCAT(N'$.',LOWER(@current_origin)),
        JSON_QUERY(N'{"presence":"VALUE","parseState":"VALID"}'));
END;
EXEC #run_frete @seed,'2036-03-20','2036-03-21',N'fretes-terminal-seed',N'BACKFILL',NULL,
    @tenant,@key,@old_payload,@presence_value,@alias_seed,N'pending',NULL,NULL,0,
    @fresh_seed,@current_at,@current_origin,'2036-03-17T12:00:00',
    @performance_seed,'2036-03-19T18:42:10',N'OFFICIAL_6389',@cte_seed,@financial_seed,@relation_seed,@sidecar_seed;
SET @presence_absent=JSON_MODIFY(JSON_MODIFY(@presence_absent,N'$.cte_created_at',N'ABSENT'),N'$.criado_em',N'VALUE');
IF @case=N'null_cte' SET @presence_absent=JSON_MODIFY(@presence_absent,N'$.cte',N'NULL');
IF @case=N'null_date' SET @presence_absent=JSON_MODIFY(@presence_absent,N'$.cte_created_at',N'NULL');
IF @case IN(N'older_cte',N'conflict') BEGIN
    SET @presence_absent=JSON_MODIFY(@presence_absent,N'$.cte_created_at',N'VALUE');
    SET @candidate_origin=N'CTE_CREATED_AT';
END;
IF @case IN(N'equal',N'conflict') SET @observed_at=@current_at;
IF @case=N'newer' SET @observed_at=DATEADD(SECOND,6,@current_at);
IF @case=N'other_tenant' SET @tenant=N'SYNTHETIC_FRETES_TENANT_B';
DECLARE @next_performance DATETIME2(3)=NULL,@next_performance_origin NVARCHAR(32)=NULL;
IF @case=N'partial_finalization' BEGIN
    SET @presence_absent=JSON_MODIFY(@presence_absent,N'$.finalizations',N'VALUE');
    SET @new_payload=JSON_MODIFY(@new_payload,N'$.finalizations',JSON_QUERY(N'[]'));
    SET @cte_absent=JSON_MODIFY(@cte_absent,N'$.finalizations',JSON_QUERY(N'{"presence":"VALUE","raw":[]}'));
END;
IF @case=N'fallback_performance' BEGIN
    SET @next_performance='2036-03-20T18:50:00';
    SET @next_performance_origin=N'FINISHED_AT_FALLBACK';
    SET @presence_absent=JSON_MODIFY(@presence_absent,N'$.finished_at',N'VALUE');
    SET @new_payload=JSON_MODIFY(@new_payload,N'$.finished_at',N'2036-03-20T18:50:00Z');
    SET @performance_absent=JSON_MODIFY(JSON_MODIFY(JSON_MODIFY(@performance_absent,
        N'$.fallback.presence',N'VALUE'),N'$.selectedOrigin',N'FINISHED_AT_FALLBACK'),N'$.parseState',N'VALID');
END;
DECLARE @observed_json NVARCHAR(MAX)=JSON_MODIFY(JSON_MODIFY(@fresh_seed,N'$.cte_created_at.presence',
    JSON_VALUE(@presence_absent,N'$.cte_created_at')),N'$.cte_issued_at.presence',N'ABSENT');
SET @observed_json=JSON_MODIFY(@observed_json,CONCAT(N'$.',LOWER(@candidate_origin)),
    JSON_QUERY(N'{"presence":"VALUE","parseState":"VALID"}'));
BEGIN TRY
    EXEC #run_frete @next,'2036-03-21','2036-03-22',N'fretes-terminal-next',N'BACKFILL',NULL,
        @tenant,@key,@new_payload,@presence_absent,@alias_absent,@status,@code,@label,@terminal,
        @observed_json,@observed_at,@candidate_origin,'2036-03-17T12:00:00',
        @performance_absent,@next_performance,@next_performance_origin,@cte_absent,@financial_absent,@relation_absent,@sidecar_absent;
    IF @case=N'conflict' THROW 52991,N'EQUAL_CONFLICT_ACCEPTED',1;
    DECLARE @promoted BIT=CASE WHEN @case IN(N'done',N'finished',N'canceled',N'cancelled',N'equal',N'issued',N'newer',N'fallback_performance',N'partial_finalization') THEN 1 ELSE 0 END;
    DECLARE @expected NVARCHAR(24)=CASE WHEN @case=N'other_tenant' THEN N'INSERTED' WHEN @promoted=1 THEN N'UPDATED' ELSE N'STALE_NO_OP' END;
    IF NOT EXISTS(SELECT 1 FROM recon.execution_candidate_application WHERE execution_id=@next AND application_disposition=@expected)
        THROW 52992,N'DISPOSITION_MISMATCH',1;
    IF @case<>N'other_tenant' AND NOT EXISTS(
        SELECT 1 FROM core.frete f JOIN core.entity_record_state g
          ON g.environment_name=f.environment_name AND g.source_instance=f.source_instance
         AND g.tenant_scope=f.tenant_scope AND g.entity_name=f.entity_name AND g.source_key=f.source_key
        JOIN recon.execution_candidate_application a ON a.execution_id=@next AND a.source_key=f.source_key
        WHERE f.tenant_scope=@tenant AND f.source_key=@key AND f.terminal=@promoted
          AND f.freshness_at_utc=CASE WHEN @case=N'newer' THEN @observed_at ELSE @current_at END
          AND g.source_freshness_at_utc=f.freshness_at_utc AND a.result_source_freshness_at_utc=f.freshness_at_utc
          AND f.cte_finalizations_json LIKE N'%SEED_CTE%'
          AND f.financial_json LIKE N'%SEED_FINANCIAL%'
    ) THROW 52993,N'EFFECTIVE_CORE_OR_RECEIPT_MISMATCH',1;
    IF @promoted=1 AND @case<>N'newer' AND NOT EXISTS(
        SELECT 1 FROM core.frete WHERE tenant_scope=@tenant AND source_key=@key
          AND freshness_origin=@current_origin
          AND JSON_VALUE(freshness_evidence_json,N'$.terminalTransition.policy')=N'FRE-03_ABSENT_CTE_PRESERVED'
          AND status_code=@code
    ) THROW 52994,N'PRESERVED_PROVENANCE_MISSING',1;
    IF NOT EXISTS(SELECT 1 FROM stg.frete_record WHERE execution_id=@next
        AND freshness_at_utc=@observed_at AND freshness_origin=@candidate_origin
        AND payload_json=@new_payload AND field_presence_json=@presence_absent)
        THROW 52995,N'OBSERVED_STAGING_MUTATED',1;
    IF @case<>N'other_tenant' AND NOT EXISTS(
        SELECT 1 FROM core.frete f JOIN core.frete_performance p ON p.frete_id=f.frete_id
        WHERE f.tenant_scope=@tenant AND f.source_key=@key AND p.performance_origin=N'OFFICIAL_6389'
          AND p.performance_at_utc='2036-03-19T18:42:10')
        THROW 52996,N'KNOWN_OFFICIAL_PERFORMANCE_LOST',1;
    IF @case=N'other_tenant' AND NOT EXISTS(SELECT 1 FROM core.frete WHERE tenant_scope=N'SYNTHETIC_FRETES_TENANT_A' AND source_key=@key AND terminal=0)
        THROW 52997,N'TENANT_ISOLATION_LOST',1;
    -- Repeat the promotion only: the same durable receipt must be returned unchanged.
    DECLARE @receipts BIGINT=(SELECT COUNT_BIG(*) FROM recon.execution_candidate_application WHERE execution_id=@next);
    EXEC core.usp_apply_reconcile_publish_fretes @next,N'dataexport-6389-v1',
        N'bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb',N'fretes-shadow-v1',
        N'cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc';
    IF @receipts<>(SELECT COUNT_BIG(*) FROM recon.execution_candidate_application WHERE execution_id=@next)
        THROW 52998,N'REPLAY_DUPLICATED_RECEIPT',1;
    ROLLBACK TRANSACTION;
    SELECT N'PASS' AS result,@case AS synthetic_case,@expected AS disposition,N'ROLLED_BACK' AS effects;
END TRY
BEGIN CATCH
    DECLARE @error INT=ERROR_NUMBER();
    IF XACT_STATE()<>0 ROLLBACK TRANSACTION;
    IF @case=N'conflict' AND @error=52142
        SELECT N'PASS' AS result,@case AS synthetic_case,N'EQUAL_FRESHNESS_CONFLICT' AS disposition,N'ROLLED_BACK' AS effects;
    ELSE THROW;
END CATCH;
GO
