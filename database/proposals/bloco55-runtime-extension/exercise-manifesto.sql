-- Two physical observations on different pages, one root and two children of each kind.
-- Administrative transactional SQL proof only; this is not an official-JAR acceptance.
DECLARE @now DATETIME2(3)=SYSUTCDATETIME(),@execution UNIQUEIDENTIFIER=NEWID(),@cycle UNIQUEIDENTIFIER=NEWID(),
 @contract CHAR(64)=REPLICATE('a',64),@config CHAR(64)=REPLICATE('b',64),@plan CHAR(64)=REPLICATE('c',64);
EXEC ctl.usp_control_plane_start_cycle @cycle,N'bloco55-sql-qualification-v1',@plan,@now;
EXEC ctl.usp_control_plane_start_execution @execution,@cycle,N'LOCAL_SHADOW',N'LOCAL_V2',N'LOCAL_V2',N'manifestos',N'BACKFILL',
 '2032-02-29T03:00:00','2032-03-01T03:00:00',N'DATA_EXPORT_RESTART_FROM_BEGINNING',N'bloco55-synthetic-v1',@contract,
 N'bloco55-sql-qualification-v1',@config,N'bloco55-sql-qualification',NULL,30,@now;
DECLARE @payload NVARCHAR(MAX)=N'{"sequence_code":95055001,"status":"closed","mdfe_status":"authorized","created_at":"2032-02-29T12:00:00Z","km":"0"}',
 @presence NVARCHAR(MAX)=N'{"sequence_code":"VALUE","status":"VALUE","mdfe_status":"VALUE"}',
 @root NVARCHAR(MAX)=N'{"sequence_code":95055001,"status":"closed","mdfe_status":"authorized"}',
 @metrics NVARCHAR(MAX)=N'{"KM":{"presence":"VALUE","value":"0"}}',
 @competence NVARCHAR(MAX)=N'{"presence":"VALUE","canonicalJson":"2032-02-29T12:00:00Z"}';
EXEC stg.usp_stage_manifesto_observation @execution,1,1,N'INTEGER:95055001',@payload,@presence,@root,@metrics,
 N'{"mft_pfs_pck_sequence_code":{"presence":"VALUE","value":95055011}}',N'VALUE',N'authorized',N'INTEGER:95055011',
 N'11111111111111111111111111111111111111111111',N'1','2032-02-29T12:00:00',N'CREATED_AT',@competence,N'VALID',NULL,@now;
EXEC ctl.usp_control_plane_record_page @execution,1,1,2,1,1,512,0,@now,N'NONE';
EXEC stg.usp_stage_manifesto_observation @execution,2,1,N'INTEGER:95055001',@payload,@presence,@root,@metrics,
 N'{"mft_pfs_pck_sequence_code":{"presence":"VALUE","value":95055012}}',N'VALUE',N'authorized',N'INTEGER:95055012',
 N'22222222222222222222222222222222222222222222',N'2','2032-02-29T12:00:00',N'CREATED_AT',@competence,N'VALID',NULL,@now;
EXEC ctl.usp_control_plane_record_page @execution,2,1,2,1,1,512,0,@now,N'NONE';
EXEC ctl.usp_control_plane_record_page @execution,3,1,2,0,0,11,1,@now,N'DATA_EXPORT_EMPTY_PAGE';
DECLARE @policy NVARCHAR(128)=N'bloco55-manifestos-qualification-v1',@policyHash CHAR(64),
 @scope CHAR(64)=LOWER(CONVERT(CHAR(64),HASHBYTES('SHA2_256',N'dq-scope-v1|24:LOCAL_SHADOW|16:LOCAL_V2|16:LOCAL_V2|20:manifestos|16:BACKFILL'),2));
DECLARE @owner NVARCHAR(64)=N'laboratory-owner',@retention NVARCHAR(128)=N'bloco55-preserve-v1';
SET @policyHash=LOWER(CONVERT(CHAR(64),HASHBYTES('SHA2_256',CONCAT(N'dq-policy-v1|',DATALENGTH(@policy),N':',@policy,N'|',@scope,N'|4|3600|',
 DATALENGTH(@owner),N':',@owner,N'|',DATALENGTH(@owner),N':',@owner,N'|',DATALENGTH(@retention),N':',@retention,N'|',DATALENGTH(@owner),N':',@owner,N'|',CONVERT(NVARCHAR(33),@now,126),N'|',
 N'1:COUNT_EQUATION:0:0:',DATALENGTH(@owner),N':',@owner,N'|2:PAGE_TERMINALITY:0:0:',DATALENGTH(@owner),N':',@owner,
 N'|3:PROMOTION_RECONCILIATION:0:0:',DATALENGTH(@owner),N':',@owner,N'|4:QUARANTINE_SLA:0:0:',DATALENGTH(@owner),N':',@owner)),2));
INSERT ctl.data_quality_policy VALUES(@policy,@policyHash,@scope,4,3600,@owner,@owner,@retention,@owner,N'RATIFIED',@now,@now);
INSERT ctl.data_quality_check_policy(policy_version,policy_fingerprint,check_ordinal,check_code,maximum_failed_rows,maximum_failure_basis_points,threshold_owner_role)
VALUES(@policy,@policyHash,1,N'COUNT_EQUATION',0,0,N'laboratory-owner'),(@policy,@policyHash,2,N'PAGE_TERMINALITY',0,0,N'laboratory-owner'),
 (@policy,@policyHash,3,N'PROMOTION_RECONCILIATION',0,0,N'laboratory-owner'),(@policy,@policyHash,4,N'QUARANTINE_SLA',0,0,N'laboratory-owner');
DECLARE @identity NVARCHAR(MAX)=ctl.fn_runtime_recovery_identity(@execution);
EXEC ctl.usp_runtime_recovery @execution,N'SEAL',@identity,N'bloco55-sql-traversal',@policy,@policyHash,NULL,N'manifestos';
EXEC ctl.usp_control_plane_transition_execution @execution,N'EXTRACTING',N'EXTRACTED',N'TRAVERSAL_AUDITED',@now;
EXEC ctl.usp_control_plane_transition_execution @execution,N'EXTRACTED',N'STAGED',N'STAGING_COMPLETED',@now;
EXEC core.usp_prepare_manifesto_candidate_set @execution,N'bloco55-synthetic-v1',@contract,N'bloco55-sql-qualification-v1',@config;
IF (SELECT COUNT_BIG(*) FROM stg.manifesto_reduced_candidate WHERE execution_id=@execution)<>1
 OR (SELECT COUNT_BIG(*) FROM stg.manifesto_pick_candidate WHERE execution_id=@execution)<>2
 OR (SELECT COUNT_BIG(*) FROM stg.manifesto_mdfe_candidate WHERE execution_id=@execution)<>2
 OR NOT EXISTS(SELECT 1 FROM stg.manifesto_reduced_candidate WHERE execution_id=@execution AND JSON_VALUE(root_presence_json,'$.mft_uer_name')=N'ABSENT')
 THROW 52851,N'MANIFESTO_REDUCED_GRAIN_OR_PRESENCE',1;
EXEC recon.usp_evaluate_execution_data_quality @execution,@policy,@policyHash;
EXEC core.usp_apply_reconcile_publish_manifestos @execution,N'bloco55-synthetic-v1',@contract,N'bloco55-sql-qualification-v1',@config;
IF NOT EXISTS(SELECT 1 FROM ctl.execution_publication_event WHERE execution_id=@execution)
 OR (SELECT COUNT_BIG(*) FROM recon.runtime_vertical_output WHERE execution_id=@execution)<>5
 THROW 52851,N'MANIFESTO_PUBLICATION_OUTPUT_MISSING',1;
PRINT N'B55_MANIFESTO_SQL_TWO_PAGES_ONE_ROOT_FOUR_CHILDREN_DQ_PUBLICATION_PASS';
GO
