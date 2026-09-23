CREATE OR ALTER PROCEDURE recon.usp_capture_runtime_vertical_output @execution_id UNIQUEIDENTIFIER
AS
BEGIN
 SET NOCOUNT ON;
 IF @@TRANCOUNT=0 OR NOT EXISTS(SELECT 1 FROM ctl.execution_publication_event WHERE execution_id=@execution_id)
   THROW 52830,N'OUTPUT_REQUIRES_PUBLICATION_TRANSACTION',1;
 IF EXISTS(SELECT 1 FROM recon.runtime_vertical_output WHERE execution_id=@execution_id) RETURN;
 DECLARE @entity NVARCHAR(128),@now DATETIME2(3)=SYSUTCDATETIME();
 SELECT @entity=p.entity_name FROM ctl.execution_attempt a JOIN ctl.execution_partition p ON p.partition_id=a.partition_id
 WHERE a.execution_id=@execution_id AND p.environment_name=N'LOCAL_SHADOW' AND p.source_instance=N'LOCAL_V2' AND p.tenant_scope=N'LOCAL_V2';
 IF @entity IS NULL RETURN;
 DECLARE @actual NVARCHAR(MAX)=(SELECT LOWER(CONVERT(NVARCHAR(36),@execution_id)) AS [execution],@entity AS [entity] FOR JSON PATH,WITHOUT_ARRAY_WRAPPER);
 IF ctl.fn_runtime_consumed_scope(@actual,1,NULL)<>1 THROW 52831,N'OUTPUT_CONSUMPTION_REQUIRED',1;
 IF @entity=N'coletas'
 INSERT recon.runtime_vertical_output(execution_id,entity_name,row_kind,source_key,child_key,projection_json,presence_json,captured_at_utc)
 SELECT @execution_id,@entity,'ROOT',c.source_key,N'',c.payload_json,c.field_presence_json,@now
 FROM recon.execution_candidate_application a JOIN core.coleta c ON c.record_state_id=a.record_state_id WHERE a.execution_id=@execution_id;
 ELSE IF @entity=N'fretes'
 INSERT recon.runtime_vertical_output(execution_id,entity_name,row_kind,source_key,child_key,projection_json,presence_json,captured_at_utc)
 SELECT @execution_id,@entity,'ROOT',c.source_key,N'',c.payload_json,c.field_presence_json,@now
 FROM recon.execution_candidate_application a JOIN core.frete c ON c.record_state_id=a.record_state_id WHERE a.execution_id=@execution_id;
 ELSE IF @entity=N'cotacoes'
 INSERT recon.runtime_vertical_output(execution_id,entity_name,row_kind,source_key,child_key,projection_json,presence_json,reference_release_id,captured_at_utc)
 SELECT @execution_id,@entity,'ROOT',c.source_key,N'',c.payload_json,c.field_presence_json,c.tariff_reference_release_id,@now
 FROM recon.execution_candidate_application a JOIN core.cotacao c ON c.record_state_id=a.record_state_id WHERE a.execution_id=@execution_id;
 ELSE IF @entity=N'localizacao_cargas'
 INSERT recon.runtime_vertical_output(execution_id,entity_name,row_kind,source_key,child_key,projection_json,presence_json,captured_at_utc)
 SELECT @execution_id,@entity,'ROOT',c.source_key,N'',c.payload_json,c.field_presence_json,@now
 FROM recon.execution_candidate_application a JOIN core.localizacao_carga c ON c.record_state_id=a.record_state_id WHERE a.execution_id=@execution_id;
 ELSE IF @entity=N'manifestos'
 BEGIN
 INSERT recon.runtime_vertical_output(execution_id,entity_name,row_kind,source_key,child_key,projection_json,presence_json,captured_at_utc)
 SELECT @execution_id,@entity,'ROOT',c.source_key,N'',
  (SELECT JSON_QUERY(c.root_payload_json) AS root,JSON_QUERY(c.metric_values_json) AS metrics,JSON_QUERY(c.competence_json) AS competence
   FOR JSON PATH,WITHOUT_ARRAY_WRAPPER),c.root_presence_json,@now
 FROM recon.execution_candidate_application a JOIN core.manifesto c ON c.record_state_id=a.record_state_id WHERE a.execution_id=@execution_id;
 INSERT recon.runtime_vertical_output(execution_id,entity_name,row_kind,source_key,child_key,projection_json,presence_json,captured_at_utc)
 SELECT @execution_id,@entity,'PICK',c.source_key,p.pick_source_key,N'{"presence":"VALUE","relation":"UNRESOLVED_V2_046A"}',N'{"key":"VALUE"}',@now
 FROM recon.execution_candidate_application a JOIN core.manifesto c ON c.record_state_id=a.record_state_id
 JOIN core.manifesto_pick p ON p.manifesto_id=c.manifesto_id WHERE a.execution_id=@execution_id;
 INSERT recon.runtime_vertical_output(execution_id,entity_name,row_kind,source_key,child_key,projection_json,presence_json,captured_at_utc)
 SELECT @execution_id,@entity,'MDFE',c.source_key,p.mdfe_key,
   (SELECT p.mdfe_number AS number FOR JSON PATH,WITHOUT_ARRAY_WRAPPER),N'{"key":"VALUE","number":"VALUE"}',@now
 FROM recon.execution_candidate_application a JOIN core.manifesto c ON c.record_state_id=a.record_state_id
 JOIN core.manifesto_mdfe p ON p.manifesto_id=c.manifesto_id WHERE a.execution_id=@execution_id;
 END;
 IF (SELECT COUNT_BIG(*) FROM recon.runtime_vertical_output WHERE execution_id=@execution_id)>512
   THROW 52832,N'OUTPUT_DERIVED_LIMIT',1;
END;
GO
