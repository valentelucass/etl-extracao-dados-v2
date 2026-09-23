-- Exercício local sintético de V2-026. Toda escrita ocorre em transação revertida.
:setvar DatabaseName "ETL_SISTEMA_V2_SHADOW"
:On Error exit

IF DB_NAME() <> N'$(DatabaseName)'
    THROW 52081,N'O exercício de Manifestos aceita somente ETL_SISTEMA_V2_SHADOW.',1;

SET NOCOUNT ON;
SET XACT_ABORT ON;
BEGIN TRANSACTION;

:r "..\transition\001_reset_historical_shadow_for_schema_foundation.sql"
:r "..\baseline\001_schema_foundation_baseline.sql"
GO
:r "005_validate_progressive_data_gate.sql"
GO
:r "042_validate_manifestos_shadow_vertical.sql"
GO

DECLARE @now DATETIME2(3)=SYSUTCDATETIME(),@source NVARCHAR(128)=N'SYNTHETIC_MANIFESTOS_6399',@tenant NVARCHAR(128)=N'SYNTHETIC_MANIFESTOS_TENANT',@contract CHAR(64)=REPLICATE('a',64),@configuration CHAR(64)=REPLICATE('b',64),@plan CHAR(64)=REPLICATE('c',64);
EXEC ctl.usp_control_plane_register_source @source,N'DATA_EXPORT',@now;
EXEC ctl.usp_control_plane_start_cycle '00000000-0000-0000-0000-000000006399',N'synthetic-manifestos-plan-v1',@plan,@now;
DECLARE @execution UNIQUEIDENTIFIER='00000000-0000-0000-0000-000000006400';
EXEC ctl.usp_control_plane_start_execution @execution,'00000000-0000-0000-0000-000000006399',N'LOCAL_SHADOW',@source,@tenant,N'manifestos',N'BACKFILL','2036-01-01T00:00:00','2036-01-01T01:00:00',N'DATA_EXPORT_RESTART_FROM_BEGINNING',N'dataexport-6399-v1',@contract,N'manifestos-shadow-v1',@configuration,N'manifestos-initial',NULL,3600,@now;
EXEC ctl.usp_control_plane_record_page @execution,1,1,100,2,1,1024,0,@now,N'NONE';
EXEC ctl.usp_control_plane_record_page @execution,2,1,100,0,0,64,1,@now,N'DATA_EXPORT_EMPTY_PAGE';

DECLARE @observation_payload_json NVARCHAR(MAX)=N'{"sequence_code":6399,"status":"pending","mdfe_status":"authorized"}',@observation_presence_json NVARCHAR(MAX)=N'{"sequence_code":"VALUE","status":"VALUE","mdfe_status":"VALUE"}',@observation_root_json NVARCHAR(MAX)=N'{"sequence_code":6399,"status":"pending","mdfe_status":"authorized"}',@observation_metrics_json NVARCHAR(MAX)=N'{"km":"0","totalCost":"10"}',@observation_competence_json NVARCHAR(MAX)=N'{"sourcePath":"departured_at","raw":"2036-01-01T09:00:00-03:00","instantUtc":"2036-01-01T12:00:00Z"}';
IF ISJSON(@observation_payload_json)<>1 OR ISJSON(@observation_presence_json)<>1 OR ISJSON(@observation_root_json)<>1 OR ISJSON(@observation_metrics_json)<>1 OR ISJSON(@observation_competence_json)<>1 THROW 52086,N'Envelope sintético de Manifestos inválido.',1;
EXEC stg.usp_stage_manifesto_observation @execution_id=@execution,@input_batch_number=1,@input_record_ordinal=1,@source_key=N'INTEGER:6399',@payload_json=@observation_payload_json,@field_presence_json=@observation_presence_json,@root_fields_json=@observation_root_json,@metric_values_json=@observation_metrics_json,@relation_candidates_json=N'{"mft_pfs_pck_sequence_code":{"presence":"VALUE","value":11}}',@mdfe_status_presence=N'VALUE',@mdfe_status_value=N'authorized',@pick_source_key=N'INTEGER:11',@mdfe_key=N'12345678901234567890123456789012345678901234',@mdfe_number=N'1',@freshness_at_utc='2036-01-01T10:00:00',@freshness_origin=N'FINISHED_AT',@competence_json=@observation_competence_json,@validation_disposition=N'VALID',@quarantine_reason_code=NULL,@observed_at_utc=@now;
EXEC stg.usp_stage_manifesto_observation @execution_id=@execution,@input_batch_number=1,@input_record_ordinal=2,@source_key=N'INTEGER:6399',@payload_json=@observation_payload_json,@field_presence_json=@observation_presence_json,@root_fields_json=@observation_root_json,@metric_values_json=@observation_metrics_json,@relation_candidates_json=N'{"mft_pfs_pck_sequence_code":{"presence":"VALUE","value":12}}',@mdfe_status_presence=N'VALUE',@mdfe_status_value=N'authorized',@pick_source_key=N'INTEGER:12',@mdfe_key=N'22345678901234567890123456789012345678901234',@mdfe_number=N'2',@freshness_at_utc='2036-01-01T10:00:00',@freshness_origin=N'FINISHED_AT',@competence_json=@observation_competence_json,@validation_disposition=N'VALID',@quarantine_reason_code=NULL,@observed_at_utc=@now;
-- Replays físicos não duplicam observação nem candidato.
EXEC stg.usp_stage_manifesto_observation @execution_id=@execution,@input_batch_number=1,@input_record_ordinal=1,@source_key=N'INTEGER:6399',@payload_json=@observation_payload_json,@field_presence_json=@observation_presence_json,@root_fields_json=@observation_root_json,@metric_values_json=@observation_metrics_json,@relation_candidates_json=N'{"mft_pfs_pck_sequence_code":{"presence":"VALUE","value":11}}',@mdfe_status_presence=N'VALUE',@mdfe_status_value=N'authorized',@pick_source_key=N'INTEGER:11',@mdfe_key=N'12345678901234567890123456789012345678901234',@mdfe_number=N'1',@freshness_at_utc='2036-01-01T10:00:00',@freshness_origin=N'FINISHED_AT',@competence_json=@observation_competence_json,@validation_disposition=N'VALID',@quarantine_reason_code=NULL,@observed_at_utc=@now;
EXEC stg.usp_stage_manifesto_reduced_candidate @execution,10,1,N'INTEGER:6399',N'{"sequence_code":6399,"status":"pending","mdfe_status":"authorized"}',N'{"sequence_code":"VALUE","status":"VALUE","mdfe_status":"VALUE"}',N'{"km":"0","totalCost":"10","manifestFreightsTotal":"2","totalTaxedWeight":"3","vehicleWeightCapacity":"4","manifestItemsCount":"5","finalizedManifestItemsCount":"6"}',N'{"sourcePath":"departured_at","raw":"2036-01-01T09:00:00-03:00","instantUtc":"2036-01-01T12:00:00Z"}',N'VALUE',N'authorized','2036-01-01T10:00:00',N'FINISHED_AT',N'[{"sourceKey":"INTEGER:11","presence":"VALUE"},{"sourceKey":"INTEGER:12","presence":"VALUE"}]',N'[{"key":"12345678901234567890123456789012345678901234","number":"1"},{"key":"22345678901234567890123456789012345678901234","number":"2"}]',@now;
EXEC stg.usp_stage_manifesto_reduced_candidate @execution,10,1,N'INTEGER:6399',N'{"sequence_code":6399,"status":"pending","mdfe_status":"authorized"}',N'{"sequence_code":"VALUE","status":"VALUE","mdfe_status":"VALUE"}',N'{"km":"0","totalCost":"10","manifestFreightsTotal":"2","totalTaxedWeight":"3","vehicleWeightCapacity":"4","manifestItemsCount":"5","finalizedManifestItemsCount":"6"}',N'{"sourcePath":"departured_at","raw":"2036-01-01T09:00:00-03:00","instantUtc":"2036-01-01T12:00:00Z"}',N'VALUE',N'authorized','2036-01-01T10:00:00',N'FINISHED_AT',N'[{"sourceKey":"INTEGER:11","presence":"VALUE"},{"sourceKey":"INTEGER:12","presence":"VALUE"}]',N'[{"key":"12345678901234567890123456789012345678901234","number":"1"},{"key":"22345678901234567890123456789012345678901234","number":"2"}]',@now;

IF (SELECT COUNT_BIG(*) FROM stg.manifesto_observation WHERE execution_id=@execution)<>2 OR (SELECT COUNT_BIG(*) FROM recon.manifesto_coleta_relation_candidate WHERE execution_id=@execution)<>2 OR (SELECT COUNT_BIG(*) FROM stg.manifesto_pick_candidate WHERE execution_id=@execution)<>2 OR (SELECT COUNT_BIG(*) FROM stg.manifesto_mdfe_candidate WHERE execution_id=@execution)<>2
    THROW 52082,N'Expansões, replay ou candidato relacional não preservaram o grão.',1;
EXEC ctl.usp_control_plane_transition_execution @execution,N'EXTRACTING',N'EXTRACTED',N'EXTRACTION_OK',@now;
EXEC ctl.usp_control_plane_transition_execution @execution,N'EXTRACTED',N'STAGED',N'STAGING_OK',@now;
EXEC core.usp_prepare_manifesto_candidate_set @execution,N'dataexport-6399-v1',@contract,N'manifestos-shadow-v1',@configuration;
IF NOT EXISTS(SELECT 1 FROM ctl.manifesto_promotion_result WHERE execution_id=@execution AND validation_state=N'PASSED' AND physical_observation_rows=2 AND reduced_root_rows=1)
    THROW 52084,N'Candidate set reduzido não foi preparado.',1;
IF EXISTS(SELECT 1 FROM core.manifesto) OR EXISTS(SELECT 1 FROM core.manifesto_pick) OR EXISTS(SELECT 1 FROM core.manifesto_mdfe)
    THROW 52085,N'Preparação não pode promover, deletar ou materializar raiz/filho.',1;
BEGIN TRY
    DECLARE @overflow NVARCHAR(MAX)=N'{"operational_comments":"' + REPLICATE(CONVERT(NVARCHAR(MAX),N'x'),4001) + N'"}';
    EXEC stg.usp_stage_manifesto_observation @execution,1,3,N'INTEGER:6400',N'{"sequence_code":6400}',N'{"sequence_code":"VALUE"}',@overflow,N'{}',N'{"mft_pfs_pck_sequence_code":{"presence":"ABSENT"}}',N'ABSENT',NULL,NULL,NULL,NULL,'2036-01-01T10:00:00',N'FINISHED_AT',N'{}',N'VALID',NULL,@now;
    THROW 52083,N'Overflow textual não foi rejeitado.',1;
END TRY
BEGIN CATCH
    IF ERROR_NUMBER()<>52001 THROW;
END CATCH;
ROLLBACK TRANSACTION;
PRINT N'Manifestos V2-026: staging físico, candidatos MAN-01/02/04/07, replay, Unicode/overflow, ownership e ausência foram exercitados e revertidos.';
