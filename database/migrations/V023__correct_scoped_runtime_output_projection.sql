-- B55 forward correction: exact physical Localizacao table and immutable typed output evidence.
SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;
GO
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
 SELECT @execution_id,@entity,'ROOT',c.source_key,N'',
 JSON_MODIFY(c.payload_json,'$._typed',JSON_QUERY((SELECT c.total_amount AS totalAmount,c.user_name_normalized AS userName,
 c.tariff_minimum_amount AS tariffMinimum,c.tariff_currency_code AS currency,c.tariff_unit_code AS unit,c.tariff_rounding_mode AS rounding
 FOR JSON PATH,WITHOUT_ARRAY_WRAPPER,INCLUDE_NULL_VALUES))),c.field_presence_json,c.tariff_reference_release_id,@now
 FROM recon.execution_candidate_application a JOIN core.cotacao c ON c.record_state_id=a.record_state_id WHERE a.execution_id=@execution_id;
 ELSE IF @entity=N'localizacao_cargas'
 INSERT recon.runtime_vertical_output(execution_id,entity_name,row_kind,source_key,child_key,projection_json,presence_json,captured_at_utc)
 SELECT @execution_id,@entity,'ROOT',c.source_key,N'',
 JSON_MODIFY(c.payload_json,'$._typed',JSON_QUERY((SELECT c.invoices_volumes_typed AS volumes,c.status_normalized AS status,
 c.status_terminal AS terminal,c.freight_candidate_state AS freightRelation,c.freight_candidate_provenance AS freightProvenance
 FOR JSON PATH,WITHOUT_ARRAY_WRAPPER,INCLUDE_NULL_VALUES))),c.field_presence_json,@now
 FROM recon.execution_candidate_application a JOIN core.localizacao_cargas c ON c.record_state_id=a.record_state_id WHERE a.execution_id=@execution_id;
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

CREATE INDEX IX_manifesto_pick_candidate_execution ON stg.manifesto_pick_candidate(execution_id,source_key) INCLUDE(pick_source_key,freshness_at_utc);
GO
CREATE INDEX IX_manifesto_mdfe_candidate_execution ON stg.manifesto_mdfe_candidate(execution_id,source_key) INCLUDE(mdfe_key,mdfe_number,freshness_at_utc);
GO