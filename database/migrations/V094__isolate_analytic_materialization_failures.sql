-- ADR0050 ANA-33: expected claim/input refusals preserve independent work in the caller transaction.
SET ANSI_NULLS ON; SET QUOTED_IDENTIFIER ON;
GO
CREATE OR ALTER PROCEDURE mart.usp_materialize_analytic_freight @run_id UNIQUEIDENTIFIER,@receipt_id UNIQUEIDENTIFIER,
 @reference_revision INT,@mode VARCHAR(16),@full BIT,@start DATE,@end DATE,@now DATETIME2(7)
AS BEGIN
 SET NOCOUNT ON; SET XACT_ABORT OFF;

 IF @@TRANCOUNT=0 THROW 53501,N'ANA_TRANSACTION_REQUIRED',1;
 SAVE TRANSACTION ana_mat01;
 BEGIN TRY
 EXEC ctl.usp_analytic_lab_lock @run_id,'MAT01';
 DECLARE @exp UNIQUEIDENTIFIER,@zone SYSNAME,@max INT;
 SELECT @exp=g.expansion_run,@zone=CASE r.zone_id WHEN 'UTC' THEN 'UTC' ELSE 'E. South America Standard Time' END,@max=r.maximum_rows
 FROM ctl.analytic_lab_run r JOIN ctl.analytic_lab_source_group g ON g.run_id=r.run_id
 WHERE r.run_id=@run_id AND @start>=r.window_start AND @end<=r.window_end_exclusive
 AND(@full=0 OR(@start=r.window_start AND @end=r.window_end_exclusive));
 IF @exp IS NULL OR @receipt_id IS NULL OR @now IS NULL OR @start IS NULL OR @end IS NULL OR @start>=@end
 OR ISNULL(@reference_revision,0) NOT BETWEEN 1 AND 100000 OR ISNULL(@mode,'') NOT IN('BOOTSTRAP','INCREMENTAL','BACKFILL','REPLAY')
 OR @full IS NULL THROW 53582,N'ANA_MAT01_SCOPE',1;
 EXEC ctl.usp_expansion_lab_lock @exp,'DEPENDENCY';
 EXEC ctl.usp_expansion_lab_lock @exp,'RELATION';
 EXEC ctl.usp_analytic_lab_lock @run_id,'DIMENSION';
 SELECT d.* INTO #roots FROM core.expansion_lab_dependency d WHERE d.run_id=@exp AND d.entity='FRETE' AND(@full=1
 OR EXISTS(SELECT 1 FROM stg.expansion_lab_dependency_observation o JOIN ctl.expansion_lab_dependency_capture c ON c.execution_id=o.execution_id
 WHERE o.run_id=d.run_id AND o.entity=d.entity AND o.source_key=d.source_key AND c.state='COMPLETE' AND c.partition_date>=@start AND c.partition_date<@end)
 OR EXISTS(SELECT 1 FROM mart.analytic_freight_operational prior WHERE prior.run_id=@run_id AND prior.dependency_id=d.dependency_id
 AND prior.reference_date>=@start AND prior.reference_date<@end));
 IF (SELECT COUNT_BIG(*) FROM #roots)>@max THROW 53583,N'ANA_MAT01_ROW_BOUND',1;
 CREATE UNIQUE CLUSTERED INDEX IX_roots ON #roots(dependency_id);
 SELECT l.target_dependency_id dependency_id,COUNT(DISTINCT loc.dependency_id) location_count,
 CASE WHEN COUNT(DISTINCT loc.dependency_id)=1 THEN MIN(loc.stage_record_id) END stage_record_id,
 MAX(CASE WHEN loc.state<>'VALID' OR l.state<>'RESOLVED' THEN 1 ELSE 0 END) conflict
 INTO #locations FROM core.expansion_lab_link l JOIN stg.expansion_lab_relation b ON b.relation_id=l.relation_id
 JOIN core.expansion_lab_dependency loc ON loc.dependency_id=l.source_dependency_id JOIN #roots d ON d.dependency_id=l.target_dependency_id
 WHERE l.run_id=@exp AND b.kind='LOC_FREIGHT' AND l.state IN('RESOLVED','CONFLICT') GROUP BY l.target_dependency_id;
 SELECT d.dependency_id,d.source_key,d.stage_record_id freight_stage_id,a.observation_id attribute_id,t.term_id,
 loc.stage_record_id location_stage_id,@reference_revision reference_revision,
 dates.service_date,COALESCE(a.data_previsao_entrega,dates.location_forecast) forecast_date,dates.completion_date,
 CONVERT(VARCHAR(32),CASE WHEN a.data_previsao_entrega IS NOT NULL THEN 'FRETE_ATTRIBUTES' WHEN dates.location_forecast IS NOT NULL THEN 'LOCALIZACAO_CAPTURE' ELSE 'MISSING' END) forecast_provenance,
 CONVERT(VARCHAR(32),COALESCE(performance.performance_origin,'MISSING')) completion_provenance,
 core.ufn_analytic_document_key(a.pagador_documento) payer_document_key,
 CONVERT(NVARCHAR(32),CASE WHEN a.cte_id IS NOT NULL OR NULLIF(LTRIM(RTRIM(a.chave_cte)),N'') IS NOT NULL OR a.numero_cte IS NOT NULL OR a.serie_cte IS NOT NULL THEN N'CT-E'
 WHEN a.nfse_number IS NOT NULL OR NULLIF(LTRIM(RTRIM(a.nfse_series)),N'') IS NOT NULL OR NULLIF(LTRIM(RTRIM(a.nfse_xml_document)),N'') IS NOT NULL
 OR NULLIF(LTRIM(RTRIM(a.nfse_integration_id)),N'') IS NOT NULL THEN N'NFS-E' ELSE N'PENDENTE/NAO EMITIDO' END) document_type,
 UPPER(NULLIF(LTRIM(RTRIM(REPLACE(a.tipo_frete,N'Freight::',N''))),N'')) freight_type,
 CONVERT(NVARCHAR(1024),f.status_raw) status_raw,TRY_CONVERT(DECIMAL(28,8),JSON_VALUE(f.financial_json,'$.total.typedDecimal')) total_value,t.currency,t.unit,
 a.total_cubic_volume total_m3,a.real_weight,a.taxed_weight,a.cubages_cubed_weight cubed_weight,
 COALESCE(location.invoices_volumes_typed,a.invoices_total_volumes,0) volumes,
 CONVERT(VARCHAR(32),CASE WHEN location.invoices_volumes_typed IS NOT NULL THEN 'LOCALIZACAO_CAPTURE' WHEN a.invoices_total_volumes IS NOT NULL THEN 'FRETE_ATTRIBUTES' ELSE 'LEGACY_ZERO' END) volume_provenance,
 CONVERT(BIT,CASE WHEN LOWER(LTRIM(RTRIM(f.status_raw))) IN(N'cancelado',N'cancelada',N'canceled',N'cancelled') THEN 1 ELSE 0 END) cancelled,
 t.courtesy,t.active source_active,d.state dependency_state,t.terms_state,a.disposition attribute_state,
 loc.conflict location_conflict,loc.location_count,
 CASE WHEN EXISTS(SELECT 1 FROM OPENJSON(location.payload_json) p WHERE p.[key]='fit_dpn_delivery_prediction_at' AND p.[type] NOT IN(0,1))
 OR(JSON_VALUE(location.payload_json,'$.fit_dpn_delivery_prediction_at') IS NOT NULL AND dates.location_forecast IS NULL) THEN 1 ELSE 0 END invalid_location_forecast,
 CASE WHEN DATALENGTH(f.status_raw)>2048 THEN 1 ELSE 0 END source_text_overflow,
 CASE WHEN JSON_VALUE(f.financial_json,'$.total.typedDecimal') IS NOT NULL AND
 (TRY_CONVERT(DECIMAL(28,8),JSON_VALUE(f.financial_json,'$.total.typedDecimal')) IS NULL
 OR CHARINDEX('e',LOWER(JSON_VALUE(f.financial_json,'$.total.typedDecimal')))>0
 OR(CHARINDEX('.',JSON_VALUE(f.financial_json,'$.total.typedDecimal'))>0 AND
 LEN(JSON_VALUE(f.financial_json,'$.total.typedDecimal'))-CHARINDEX('.',JSON_VALUE(f.financial_json,'$.total.typedDecimal'))>8))
 THEN 1 ELSE 0 END source_amount_precision,
 CASE WHEN location.observed_at_utc>f.observed_at_utc THEN location.observed_at_utc ELSE f.observed_at_utc END extracted_at,
 CONVERT(VARCHAR(24),CASE WHEN location.observed_at_utc>f.observed_at_utc THEN 'LOCALIZACAO_CAPTURE' ELSE 'FRETE_CAPTURE' END) extraction_provenance
 INTO #base FROM #roots d JOIN stg.frete_record f ON f.stage_record_id=d.stage_record_id
 JOIN stg.expansion_lab_dependency_observation clock ON clock.stage_record_id=d.stage_record_id
 LEFT JOIN core.ufn_analytic_freight_attributes(@run_id) a ON a.source_key=d.source_key AND a.base_stage_id=d.stage_record_id
 LEFT JOIN core.ufn_expansion_freight_terms(@exp) t ON t.source_key=d.source_key
 LEFT JOIN #locations loc ON loc.dependency_id=d.dependency_id
 LEFT JOIN stg.localizacao_carga_record location ON location.stage_record_id=loc.stage_record_id
 LEFT JOIN stg.frete_performance_observation performance ON performance.stage_record_id=d.stage_record_id
 CROSS APPLY(SELECT
 CONVERT(DATE,core.ufn_analytic_raster_time(clock.service_second,clock.service_nano,0) AT TIME ZONE @zone) service_date,
 CONVERT(DATE,core.ufn_analytic_iso_time(JSON_VALUE(location.payload_json,'$.fit_dpn_delivery_prediction_at')) AT TIME ZONE @zone) location_forecast,
 CONVERT(DATE,core.ufn_analytic_iso_time(JSON_VALUE(performance.performance_evidence_json,'$.instantUtc')) AT TIME ZONE @zone) completion_date) dates;
 SELECT b.*,i.indicator,i.reference_date,ref.reference_release_id,ref.fiscal_policy,
 branch.entity_key branch_key,branch.binding_id branch_binding_id,
 COALESCE(performance.entity_key,destination.entity_key,branch.entity_key) performance_branch_key,
 COALESCE(performance.binding_id,destination.binding_id,branch.binding_id) performance_binding_id,
 CONVERT(VARCHAR(32),CASE WHEN performance.binding_id IS NOT NULL THEN 'PERFORMANCE_BINDING' WHEN destination.binding_id IS NOT NULL THEN 'DESTINATION_BINDING'
 WHEN branch.binding_id IS NOT NULL THEN 'ISSUER_BINDING' ELSE 'MISSING' END) performance_branch_provenance,
 COALESCE(performance.disposition,destination.disposition,branch.disposition) performance_branch_state,branch.disposition branch_state,
 CONVERT(BIT,CASE WHEN b.document_type IN(N'CT-E',N'NFS-E') THEN 1 ELSE 0 END) has_document,
 CONVERT(BIT,CASE WHEN COALESCE(b.total_m3,0)<>0 THEN 1 ELSE 0 END) is_cubed,
 CONVERT(BIT,CASE WHEN EXISTS(SELECT 1 FROM ref.analytic_lab_exclusion x WHERE x.reference_release_id=ref.reference_release_id
 AND x.kind='PAYER' AND core.ufn_analytic_document_key(x.match_value)=b.payer_document_key) THEN 1 ELSE 0 END) excluded_payer,
 CONVERT(BIT,CASE WHEN EXISTS(SELECT 1 FROM ref.analytic_lab_exclusion x WHERE x.reference_release_id=ref.reference_release_id
 AND x.kind='BRANCH_DOCUMENT' AND core.ufn_analytic_document_key(x.match_value)=b.payer_document_key) THEN 1 ELSE 0 END) branch_document,
 DATEDIFF(day,b.forecast_date,b.completion_date) performance_days
 INTO #bound FROM #base b CROSS APPLY(VALUES(CONVERT(CHAR(2),'PE'),b.forecast_date),(CONVERT(CHAR(2),'CB'),b.service_date)) i(indicator,reference_date)
 OUTER APPLY ref.ufn_analytic_reference(@run_id,@reference_revision,COALESCE(i.reference_date,b.service_date,@start)) ref
 OUTER APPLY(SELECT * FROM ref.ufn_analytic_dimension(@run_id,@reference_revision,COALESCE(i.reference_date,b.service_date,@start)) d WHERE d.entity='FRETE' AND d.source_key=b.source_key AND d.role='BRANCH') branch
 OUTER APPLY(SELECT * FROM ref.ufn_analytic_dimension(@run_id,@reference_revision,COALESCE(i.reference_date,b.service_date,@start)) d WHERE d.entity='FRETE' AND d.source_key=b.source_key AND d.role='DEST_BRANCH') destination
 OUTER APPLY(SELECT * FROM ref.ufn_analytic_dimension(@run_id,@reference_revision,COALESCE(i.reference_date,b.service_date,@start)) d WHERE d.entity='FRETE' AND d.source_key=b.source_key AND d.role='PERFORMANCE_BRANCH') performance;
 SELECT b.*,CONVERT(BIT,CASE WHEN source_active=0 OR courtesy=1 THEN 0
 WHEN has_document=0 AND(branch_document=1 OR COALESCE(total_value,0)<=0.01 OR(freight_type LIKE N'%SUBSTITUTE%' AND LOWER(status_raw) IN(N'pending',N'pendente'))) THEN 0 ELSE 1 END) eligible,
 CONVERT(NVARCHAR(24),CASE WHEN performance_days IS NULL THEN N'EM ABERTO' WHEN performance_days<=0 THEN N'NO PRAZO' ELSE N'FORA DO PRAZO' END) performance_status,
 CONVERT(TINYINT,CASE WHEN performance_days IS NULL THEN 0 WHEN performance_days<=0 THEN 1 ELSE 2 END) performance_code
 INTO #rules FROM #bound b;
 SELECT r.*,CONVERT(BIT,CASE WHEN eligible=1 AND total_value>0.01 THEN 1 ELSE 0 END) eligible_with_value,
 CONVERT(VARCHAR(40),CASE WHEN dependency_state<>'VALID' THEN 'SOURCE_CONFLICT' WHEN attribute_state IS NULL THEN 'ATTRIBUTES_MISSING'
 WHEN attribute_state<>'READY' THEN attribute_state WHEN terms_state IS NULL THEN 'TERMS_MISSING' WHEN terms_state<>'READY' THEN 'TERMS_CONFLICT'
 WHEN location_conflict=1 OR location_count>1 THEN 'LOCATION_CONFLICT' WHEN invalid_location_forecast=1 THEN 'INVALID_LOCATION_FORECAST'
 WHEN source_text_overflow=1 THEN 'SOURCE_TEXT_BOUND' WHEN source_amount_precision=1 THEN 'SOURCE_AMOUNT_PRECISION'
 WHEN reference_release_id IS NULL THEN 'REFERENCE_MISSING' WHEN fiscal_policy<>'CTE_FIRST_REAL_DOCUMENT' THEN 'FISCAL_UNRESOLVED'
 WHEN courtesy IS NULL OR source_active IS NULL OR currency<>'BRL' OR unit<>'MAJOR' THEN 'FINANCIAL_POLICY_MISSING'
 WHEN freight_type IS NULL THEN 'FREIGHT_TYPE_MISSING' WHEN freight_type=N'COMPLEMENTAR' THEN 'COMPLEMENTARY'
 WHEN source_active=0 THEN 'INACTIVE' WHEN courtesy=1 THEN 'COURTESY' WHEN cancelled=1 THEN 'CANCELLED'
 WHEN reference_date IS NULL THEN 'DATE_MISSING' WHEN eligible=0 THEN 'INELIGIBLE'
 WHEN indicator='PE' AND(performance_branch_key IS NULL OR ISNULL(performance_branch_state,'')<>'RESOLVED') THEN 'PERFORMANCE_BRANCH_MISSING'
 WHEN indicator='CB' AND ISNULL(total_value,0)<=0.01 THEN 'VALUE_THRESHOLD' WHEN indicator='CB' AND excluded_payer=1 THEN 'PAYER_EXCLUDED'
 ELSE 'READY' END) disposition INTO #classified FROM #rules r;
 SELECT c.*,CONVERT(BIT,CASE WHEN disposition='READY' THEN 1 ELSE 0 END) indicator_valid INTO #result FROM #classified c;
 IF EXISTS(SELECT 1 FROM ctl.analytic_lab_materialization_receipt WHERE receipt_id=@receipt_id)
 BEGIN
 IF NOT EXISTS(SELECT 1 FROM ctl.analytic_lab_materialization_receipt WHERE receipt_id=@receipt_id AND run_id=@run_id AND kind='MAT01'
 AND reference_revision=@reference_revision AND mode=@mode AND full_scope=@full AND window_start=@start AND window_end_exclusive=@end)
 THROW 53584,N'ANA_MAT01_RETRY_DIVERGENT',1;
 IF EXISTS(SELECT s.dependency_id,s.indicator,s.freight_stage_id,s.attribute_id,s.term_id,s.location_stage_id,CONVERT(VARBINARY(MAX),s.source_key),s.reference_revision,s.reference_release_id,s.reference_date,s.service_date,s.forecast_date,s.completion_date,CONVERT(VARBINARY(MAX),s.forecast_provenance),CONVERT(VARBINARY(MAX),s.completion_provenance),CONVERT(VARBINARY(MAX),s.branch_key),s.branch_binding_id,CONVERT(VARBINARY(MAX),s.performance_branch_key),s.performance_binding_id,CONVERT(VARBINARY(MAX),s.performance_branch_provenance),CONVERT(VARBINARY(MAX),s.payer_document_key),CONVERT(VARBINARY(MAX),s.document_type),CONVERT(VARBINARY(MAX),s.freight_type),CONVERT(VARBINARY(MAX),s.status_raw),s.total_value,CONVERT(VARBINARY(MAX),s.currency),CONVERT(VARBINARY(MAX),s.unit),s.total_m3,s.real_weight,s.taxed_weight,s.cubed_weight,s.volumes,CONVERT(VARBINARY(MAX),s.volume_provenance),s.performance_days,CONVERT(VARBINARY(MAX),s.performance_status),s.performance_code,s.is_cubed,s.cancelled,s.courtesy,s.source_active,s.has_document,s.excluded_payer,s.branch_document,s.eligible,s.eligible_with_value,s.indicator_valid,CONVERT(VARBINARY(MAX),s.disposition),s.extracted_at,CONVERT(VARBINARY(MAX),s.extraction_provenance) FROM #result s EXCEPT
 SELECT t.dependency_id,t.indicator,t.freight_stage_id,t.attribute_id,t.term_id,t.location_stage_id,CONVERT(VARBINARY(MAX),t.source_key),t.reference_revision,t.reference_release_id,t.reference_date,t.service_date,t.forecast_date,t.completion_date,CONVERT(VARBINARY(MAX),t.forecast_provenance),CONVERT(VARBINARY(MAX),t.completion_provenance),CONVERT(VARBINARY(MAX),t.branch_key),t.branch_binding_id,CONVERT(VARBINARY(MAX),t.performance_branch_key),t.performance_binding_id,CONVERT(VARBINARY(MAX),t.performance_branch_provenance),CONVERT(VARBINARY(MAX),t.payer_document_key),CONVERT(VARBINARY(MAX),t.document_type),CONVERT(VARBINARY(MAX),t.freight_type),CONVERT(VARBINARY(MAX),t.status_raw),t.total_value,CONVERT(VARBINARY(MAX),t.currency),CONVERT(VARBINARY(MAX),t.unit),t.total_m3,t.real_weight,t.taxed_weight,t.cubed_weight,t.volumes,CONVERT(VARBINARY(MAX),t.volume_provenance),t.performance_days,CONVERT(VARBINARY(MAX),t.performance_status),t.performance_code,t.is_cubed,t.cancelled,t.courtesy,t.source_active,t.has_document,t.excluded_payer,t.branch_document,t.eligible,t.eligible_with_value,t.indicator_valid,CONVERT(VARBINARY(MAX),t.disposition),t.extracted_at,CONVERT(VARBINARY(MAX),t.extraction_provenance) FROM mart.analytic_freight_operational_observation t WHERE receipt_id=@receipt_id)
 OR EXISTS(SELECT t.dependency_id,t.indicator,t.freight_stage_id,t.attribute_id,t.term_id,t.location_stage_id,CONVERT(VARBINARY(MAX),t.source_key),t.reference_revision,t.reference_release_id,t.reference_date,t.service_date,t.forecast_date,t.completion_date,CONVERT(VARBINARY(MAX),t.forecast_provenance),CONVERT(VARBINARY(MAX),t.completion_provenance),CONVERT(VARBINARY(MAX),t.branch_key),t.branch_binding_id,CONVERT(VARBINARY(MAX),t.performance_branch_key),t.performance_binding_id,CONVERT(VARBINARY(MAX),t.performance_branch_provenance),CONVERT(VARBINARY(MAX),t.payer_document_key),CONVERT(VARBINARY(MAX),t.document_type),CONVERT(VARBINARY(MAX),t.freight_type),CONVERT(VARBINARY(MAX),t.status_raw),t.total_value,CONVERT(VARBINARY(MAX),t.currency),CONVERT(VARBINARY(MAX),t.unit),t.total_m3,t.real_weight,t.taxed_weight,t.cubed_weight,t.volumes,CONVERT(VARBINARY(MAX),t.volume_provenance),t.performance_days,CONVERT(VARBINARY(MAX),t.performance_status),t.performance_code,t.is_cubed,t.cancelled,t.courtesy,t.source_active,t.has_document,t.excluded_payer,t.branch_document,t.eligible,t.eligible_with_value,t.indicator_valid,CONVERT(VARBINARY(MAX),t.disposition),t.extracted_at,CONVERT(VARBINARY(MAX),t.extraction_provenance) FROM mart.analytic_freight_operational_observation t WHERE receipt_id=@receipt_id EXCEPT
 SELECT s.dependency_id,s.indicator,s.freight_stage_id,s.attribute_id,s.term_id,s.location_stage_id,CONVERT(VARBINARY(MAX),s.source_key),s.reference_revision,s.reference_release_id,s.reference_date,s.service_date,s.forecast_date,s.completion_date,CONVERT(VARBINARY(MAX),s.forecast_provenance),CONVERT(VARBINARY(MAX),s.completion_provenance),CONVERT(VARBINARY(MAX),s.branch_key),s.branch_binding_id,CONVERT(VARBINARY(MAX),s.performance_branch_key),s.performance_binding_id,CONVERT(VARBINARY(MAX),s.performance_branch_provenance),CONVERT(VARBINARY(MAX),s.payer_document_key),CONVERT(VARBINARY(MAX),s.document_type),CONVERT(VARBINARY(MAX),s.freight_type),CONVERT(VARBINARY(MAX),s.status_raw),s.total_value,CONVERT(VARBINARY(MAX),s.currency),CONVERT(VARBINARY(MAX),s.unit),s.total_m3,s.real_weight,s.taxed_weight,s.cubed_weight,s.volumes,CONVERT(VARBINARY(MAX),s.volume_provenance),s.performance_days,CONVERT(VARBINARY(MAX),s.performance_status),s.performance_code,s.is_cubed,s.cancelled,s.courtesy,s.source_active,s.has_document,s.excluded_payer,s.branch_document,s.eligible,s.eligible_with_value,s.indicator_valid,CONVERT(VARBINARY(MAX),s.disposition),s.extracted_at,CONVERT(VARBINARY(MAX),s.extraction_provenance) FROM #result s) THROW 53585,N'ANA_MAT01_RETRY_INPUT_CHANGED',1;
 SELECT candidates,inserts,updates,noops,ready,blocked FROM ctl.analytic_lab_materialization_receipt WHERE receipt_id=@receipt_id; RETURN;
 END;
 SELECT s.*,o.observation_id prior_observation_id,o.reference_date old_reference_date,o.branch_key old_branch_key,
 CONVERT(VARCHAR(8),CASE WHEN o.observation_id IS NULL THEN 'INSERT' WHEN EXISTS(SELECT s.freight_stage_id,s.attribute_id,s.term_id,s.location_stage_id,CONVERT(VARBINARY(MAX),s.source_key),s.reference_revision,s.reference_release_id,s.reference_date,s.service_date,s.forecast_date,s.completion_date,CONVERT(VARBINARY(MAX),s.forecast_provenance),CONVERT(VARBINARY(MAX),s.completion_provenance),CONVERT(VARBINARY(MAX),s.branch_key),s.branch_binding_id,CONVERT(VARBINARY(MAX),s.performance_branch_key),s.performance_binding_id,CONVERT(VARBINARY(MAX),s.performance_branch_provenance),CONVERT(VARBINARY(MAX),s.payer_document_key),CONVERT(VARBINARY(MAX),s.document_type),CONVERT(VARBINARY(MAX),s.freight_type),CONVERT(VARBINARY(MAX),s.status_raw),s.total_value,CONVERT(VARBINARY(MAX),s.currency),CONVERT(VARBINARY(MAX),s.unit),s.total_m3,s.real_weight,s.taxed_weight,s.cubed_weight,s.volumes,CONVERT(VARBINARY(MAX),s.volume_provenance),s.performance_days,CONVERT(VARBINARY(MAX),s.performance_status),s.performance_code,s.is_cubed,s.cancelled,s.courtesy,s.source_active,s.has_document,s.excluded_payer,s.branch_document,s.eligible,s.eligible_with_value,s.indicator_valid,CONVERT(VARBINARY(MAX),s.disposition),s.extracted_at,CONVERT(VARBINARY(MAX),s.extraction_provenance) EXCEPT SELECT o.freight_stage_id,o.attribute_id,o.term_id,o.location_stage_id,CONVERT(VARBINARY(MAX),o.source_key),o.reference_revision,o.reference_release_id,o.reference_date,o.service_date,o.forecast_date,o.completion_date,CONVERT(VARBINARY(MAX),o.forecast_provenance),CONVERT(VARBINARY(MAX),o.completion_provenance),CONVERT(VARBINARY(MAX),o.branch_key),o.branch_binding_id,CONVERT(VARBINARY(MAX),o.performance_branch_key),o.performance_binding_id,CONVERT(VARBINARY(MAX),o.performance_branch_provenance),CONVERT(VARBINARY(MAX),o.payer_document_key),CONVERT(VARBINARY(MAX),o.document_type),CONVERT(VARBINARY(MAX),o.freight_type),CONVERT(VARBINARY(MAX),o.status_raw),o.total_value,CONVERT(VARBINARY(MAX),o.currency),CONVERT(VARBINARY(MAX),o.unit),o.total_m3,o.real_weight,o.taxed_weight,o.cubed_weight,o.volumes,CONVERT(VARBINARY(MAX),o.volume_provenance),o.performance_days,CONVERT(VARBINARY(MAX),o.performance_status),o.performance_code,o.is_cubed,o.cancelled,o.courtesy,o.source_active,o.has_document,o.excluded_payer,o.branch_document,o.eligible,o.eligible_with_value,o.indicator_valid,CONVERT(VARBINARY(MAX),o.disposition),o.extracted_at,CONVERT(VARBINARY(MAX),o.extraction_provenance)) THEN 'UPDATE' ELSE 'NOOP' END) action
 INTO #decisions FROM #result s LEFT JOIN mart.analytic_freight_operational c ON c.run_id=@run_id AND c.dependency_id=s.dependency_id AND c.indicator=s.indicator
 LEFT JOIN mart.analytic_freight_operational_observation o ON o.observation_id=c.observation_id;
 INSERT ctl.analytic_lab_materialization_receipt
 SELECT @receipt_id,@run_id,'MAT01',@reference_revision,@mode,@full,@start,@end,COUNT_BIG(*),
 COALESCE(SUM(CONVERT(BIGINT,CASE WHEN action='INSERT' THEN 1 ELSE 0 END)),0),COALESCE(SUM(CONVERT(BIGINT,CASE WHEN action='UPDATE' THEN 1 ELSE 0 END)),0),
 COALESCE(SUM(CONVERT(BIGINT,CASE WHEN action='NOOP' THEN 1 ELSE 0 END)),0),COALESCE(SUM(CONVERT(BIGINT,indicator_valid)),0),
 COALESCE(SUM(CONVERT(BIGINT,CASE WHEN indicator_valid=0 THEN 1 ELSE 0 END)),0),@now FROM #decisions;
 INSERT mart.analytic_freight_operational_observation(receipt_id,run_id,dependency_id,indicator,freight_stage_id,attribute_id,term_id,location_stage_id,source_key,reference_revision,reference_release_id,reference_date,service_date,forecast_date,completion_date,forecast_provenance,completion_provenance,branch_key,branch_binding_id,performance_branch_key,performance_binding_id,performance_branch_provenance,payer_document_key,document_type,freight_type,status_raw,total_value,currency,unit,total_m3,real_weight,taxed_weight,cubed_weight,volumes,volume_provenance,performance_days,performance_status,performance_code,is_cubed,cancelled,courtesy,source_active,has_document,excluded_payer,branch_document,eligible,eligible_with_value,indicator_valid,disposition,extracted_at,extraction_provenance,action,prior_observation_id,old_reference_date,old_branch_key)
 SELECT @receipt_id,@run_id,dependency_id,indicator,freight_stage_id,attribute_id,term_id,location_stage_id,source_key,reference_revision,reference_release_id,reference_date,service_date,forecast_date,completion_date,forecast_provenance,completion_provenance,branch_key,branch_binding_id,performance_branch_key,performance_binding_id,performance_branch_provenance,payer_document_key,document_type,freight_type,status_raw,total_value,currency,unit,total_m3,real_weight,taxed_weight,cubed_weight,volumes,volume_provenance,performance_days,performance_status,performance_code,is_cubed,cancelled,courtesy,source_active,has_document,excluded_payer,branch_document,eligible,eligible_with_value,indicator_valid,disposition,extracted_at,extraction_provenance,action,prior_observation_id,old_reference_date,old_branch_key FROM #decisions;
 INSERT mart.analytic_freight_operational
 SELECT @run_id,dependency_id,indicator,observation_id,reference_date,branch_key,indicator_valid FROM mart.analytic_freight_operational_observation
 WHERE receipt_id=@receipt_id AND action='INSERT';
 UPDATE c SET observation_id=o.observation_id,reference_date=o.reference_date,branch_key=o.branch_key,indicator_valid=o.indicator_valid
 FROM mart.analytic_freight_operational c JOIN mart.analytic_freight_operational_observation o ON o.run_id=c.run_id AND o.dependency_id=c.dependency_id AND o.indicator=c.indicator
 WHERE o.receipt_id=@receipt_id AND o.action='UPDATE';
 SELECT candidates,inserts,updates,noops,ready,blocked FROM ctl.analytic_lab_materialization_receipt WHERE receipt_id=@receipt_id;

 END TRY
 BEGIN CATCH
  IF XACT_STATE()=1 ROLLBACK TRANSACTION ana_mat01;
  THROW;
 END CATCH;
END;
GO
CREATE OR ALTER PROCEDURE mart.usp_materialize_analytic_collectors @run_id UNIQUEIDENTIFIER,@receipt_id UNIQUEIDENTIFIER,
 @reference_revision INT,@mode VARCHAR(16),@full BIT,@start DATE,@end DATE,@now DATETIME2(7)
AS BEGIN SET NOCOUNT ON; SET XACT_ABORT OFF;

 IF @@TRANCOUNT=0 THROW 53501,N'ANA_TRANSACTION_REQUIRED',1;
 SAVE TRANSACTION ana_mat02;
 BEGIN TRY
 EXEC ctl.usp_analytic_lab_lock @run_id,'MAT02';
 DECLARE @exp UNIQUEIDENTIFIER,@rel UNIQUEIDENTIFIER,@zone SYSNAME,@max INT,@run_created DATETIME2(7);
 SELECT @exp=g.expansion_run,@rel=g.relational_run,@zone=CASE r.zone_id WHEN 'UTC' THEN 'UTC' ELSE 'E. South America Standard Time' END,@max=r.maximum_rows,@run_created=r.created_at
 FROM ctl.analytic_lab_run r JOIN ctl.analytic_lab_source_group g ON g.run_id=r.run_id
 WHERE r.run_id=@run_id AND @start>=r.window_start AND @end<=r.window_end_exclusive
 AND(@full=0 OR(@start=r.window_start AND @end=r.window_end_exclusive));
 IF @exp IS NULL OR @receipt_id IS NULL OR @now IS NULL OR @start IS NULL OR @end IS NULL OR @start>=@end
 OR ISNULL(@reference_revision,0) NOT BETWEEN 1 AND 100000 OR ISNULL(@mode,'') NOT IN('BOOTSTRAP','INCREMENTAL','BACKFILL','REPLAY')
 OR @full IS NULL THROW 53621,N'ANA_COLLECTORS_SCOPE',1;
 EXEC ctl.usp_analytic_lab_lock @run_id,'MAT05';
 EXEC ctl.usp_expansion_lab_lock @exp,'RELATION';
 EXEC ctl.usp_relational_lab_lock @rel;
 EXEC ctl.usp_analytic_lab_lock @run_id,'DIMENSION';
 IF NOT EXISTS(SELECT 1 FROM ctl.relational_lab_capture c WHERE c.run_id=@rel AND c.entity_name=N'manifestos')
 OR NOT EXISTS(SELECT 1 FROM ctl.expansion_lab_capture c WHERE c.run_id=@exp AND c.vertical='INV' AND c.state='COMPLETE')
 THROW 53622,N'ANA_COLLECTORS_INPUT_CAPTURE_MISSING',1;
 -- Every selected manifest capture must have completed analytical preparation, including empty captures.
 IF EXISTS(SELECT 1 FROM ctl.relational_lab_capture c WHERE c.run_id=@rel AND c.entity_name=N'manifestos'
 AND(@full=1 OR(c.business_date>=@start AND c.business_date<@end))
 AND NOT EXISTS(SELECT 1 FROM ctl.analytic_manifest_preparation p WHERE p.run_id=@run_id AND p.execution_id=c.execution_id))
 THROW 53623,N'ANA_COLLECTORS_MANIFEST_PREPARATION_MISSING',1;
 SELECT m.*,CONVERT(DATE,m.created_at AT TIME ZONE @zone) business_date INTO #manifestos
 FROM core.analytic_lab_manifest_projection m WHERE m.run_id=@run_id AND(@full=1
 OR EXISTS(SELECT 1 FROM ctl.relational_lab_capture c JOIN stg.manifesto_observation o ON o.execution_id=c.execution_id
 WHERE c.run_id=@rel AND c.entity_name=N'manifestos' AND o.source_key COLLATE Latin1_General_100_BIN2=m.source_key AND c.business_date>=@start AND c.business_date<@end)
 OR EXISTS(SELECT 1 FROM mart.analytic_collector_contribution p WHERE p.run_id=@run_id AND p.entity='MAN' AND p.source_key=m.source_key AND p.reference_date>=@start AND p.reference_date<@end));
 -- INV common root attributes are reduced only inside one current freshness/revision cohort.
 SELECT i.*,DENSE_RANK() OVER(PARTITION BY i.root_id ORDER BY i.fresh_second DESC,i.fresh_nano DESC,i.fresh_inclusive DESC,i.revision DESC) cohort_rank
 INTO #inventory_all FROM core.expansion_lab_current_input i WHERE i.run_id=@exp AND i.vertical='INV';
 SELECT i.root_id,MIN(i.observation_id) observation_id,MAX(CONVERT(INT,i.unresolved_conflict)) source_conflict,
 CASE WHEN COUNT(DISTINCT CONVERT(VARBINARY(8000),CONCAT(f.started_at_p,':',f.started_at_raw)))>1
 OR COUNT(DISTINCT CONVERT(VARBINARY(8000),CONCAT(f.finished_at_p,':',f.finished_at_raw)))>1
 OR COUNT(DISTINCT CONVERT(VARBINARY(8000),CONCAT(f.type_p,':',f.type_raw)))>1
 OR COUNT(DISTINCT CONVERT(VARBINARY(8000),CONCAT(f.cnr_crn_psn_nickname_p,':',f.cnr_crn_psn_nickname_raw)))>1 THEN 1 ELSE 0 END attribute_conflict
 INTO #inventory_cohort FROM #inventory_all i JOIN stg.expansion_lab_inv f ON f.execution_id=i.execution_id AND f.occurrence=i.occurrence
 WHERE i.cohort_rank=1 GROUP BY i.root_id;
 SELECT r.root_id,CONVERT(NVARCHAR(256),CONCAT(r.root_type,':',r.root_key)) COLLATE Latin1_General_100_BIN2 source_key,
 o.execution_id,o.observation_id,c.attribute_conflict,c.source_conflict,r.active,
 f.type,f.finished_at,f.finished_at_p,f.cnr_crn_psn_nickname,
 CONVERT(DATE,core.ufn_analytic_iso_time(f.started_at_raw) AT TIME ZONE @zone) business_date,
 capture.sealed_at extracted_at
 INTO #inventories FROM core.expansion_lab_root r
 LEFT JOIN #inventory_cohort c ON c.root_id=r.root_id
 LEFT JOIN stg.expansion_lab_observation o ON o.observation_id=c.observation_id
 LEFT JOIN stg.expansion_lab_inv f ON f.execution_id=o.execution_id AND f.occurrence=o.occurrence
 LEFT JOIN ctl.expansion_lab_capture capture ON capture.execution_id=o.execution_id
 WHERE r.run_id=@exp AND r.vertical='INV' AND(@full=1 OR EXISTS(SELECT 1 FROM ctl.expansion_lab_capture c2
 JOIN stg.expansion_lab_observation o2 ON o2.execution_id=c2.execution_id WHERE c2.run_id=@exp AND o2.root_type=r.root_type AND o2.root_key=r.root_key AND o2.vertical='INV'
 AND c2.state='COMPLETE' AND c2.partition_start>=@start AND c2.partition_start<@end)
 OR EXISTS(SELECT 1 FROM mart.analytic_collector_contribution p WHERE p.run_id=@run_id AND p.entity='INV' AND p.source_key=CONCAT(r.root_type,':',r.root_key) COLLATE Latin1_General_100_BIN2 AND p.reference_date>=@start AND p.reference_date<@end));
 IF (SELECT COUNT_BIG(*) FROM #manifestos)+(SELECT COUNT_BIG(*) FROM #inventories)>@max THROW 53624,N'ANA_COLLECTORS_ROOT_BOUND',1;
 CREATE TABLE #candidate(entity VARCHAR(8) COLLATE Latin1_General_100_BIN2 NOT NULL,
 source_key NVARCHAR(256) COLLATE Latin1_General_100_BIN2 NOT NULL,
 contribution VARCHAR(12) COLLATE Latin1_General_100_BIN2 NOT NULL,
 source_execution UNIQUEIDENTIFIER NULL,
 manifest_snapshot_id BIGINT NULL,
 inventory_observation_id BIGINT NULL,
 reference_revision INT NOT NULL,
 reference_release_id BIGINT NULL,
 source_state_id BIGINT NULL,
 reference_date DATE NULL,
 branch_key VARCHAR(64) COLLATE Latin1_General_100_BIN2 NULL,
 branch_binding_id BIGINT NULL,
 branch_provenance VARCHAR(32) COLLATE Latin1_General_100_BIN2 NOT NULL,
 fallback_dependency_id BIGINT NULL,
 quantity BIGINT NOT NULL,
 incomplete BIGINT NOT NULL,
 disposition VARCHAR(40) COLLATE Latin1_General_100_BIN2 NOT NULL,
 extracted_at DATETIME2(7) NOT NULL);
 INSERT #candidate
 SELECT 'MAN',m.source_key,k.contribution,m.execution_id,m.snapshot_id,NULL,@reference_revision,selected.reference_release_id,state.state_id,
 m.business_date,branch.entity_key,branch.binding_id,
 CASE k.contribution WHEN 'ISSUED' THEN 'MANIFEST_ISSUER_BINDING' ELSE 'EXPLICIT_UNLOADING_BINDING' END,NULL,
 CASE WHEN reason.disposition='READY' THEN 1 ELSE 0 END,0,reason.disposition,m.extracted_at
 FROM #manifestos m CROSS APPLY(VALUES('ISSUED','BRANCH'),('UNLOADED','UNLOADING_BRANCH')) k(contribution,role)
 LEFT JOIN ref.analytic_lab_manifest_state_current state ON state.run_id=@run_id AND state.source_key=m.source_key
 OUTER APPLY ref.ufn_analytic_reference(@run_id,@reference_revision,COALESCE(m.business_date,@start)) selected
 OUTER APPLY(SELECT * FROM ref.ufn_analytic_dimension(@run_id,@reference_revision,COALESCE(m.business_date,@start)) d
 WHERE d.entity='MAN' AND d.source_key=m.source_key AND d.role=k.role COLLATE Latin1_General_100_BIN2) branch
 CROSS APPLY(SELECT CONVERT(VARCHAR(40),CASE WHEN m.disposition<>'READY' THEN m.disposition WHEN state.state_id IS NULL THEN 'SOURCE_STATE_MISSING'
 WHEN state.active=0 THEN 'SOURCE_INACTIVE' WHEN m.business_date IS NULL THEN 'CREATED_DATE_MISSING'
 WHEN selected.reference_release_id IS NULL THEN 'REFERENCE_MISSING' WHEN selected.branch_policy<>'EXPLICIT_ASSIGNMENT' THEN 'BRANCH_UNRESOLVED'
 WHEN EXISTS(SELECT 1 FROM ref.analytic_lab_exclusion x WHERE x.reference_release_id=selected.reference_release_id AND x.kind='OPERATION_PREFIX'
 AND LEFT(LTRIM(m.mft_man_name),LEN(x.match_value)) COLLATE Latin1_General_100_CI_AI=x.match_value COLLATE Latin1_General_100_CI_AI) THEN 'OPERATION_EXCLUDED'
 WHEN k.contribution='UNLOADED' AND NOT EXISTS(SELECT 1 FROM OPENJSON(COALESCE(m.mft_mte_unloading_recipient_names,N'[]')) j WHERE NULLIF(LTRIM(RTRIM(j.value)),N'') IS NOT NULL) THEN 'NO_UNLOADING_SOURCE'
 WHEN branch.binding_id IS NULL THEN 'BRANCH_BINDING_MISSING' WHEN branch.disposition<>'RESOLVED' THEN branch.disposition ELSE 'READY' END) disposition) reason;
 SELECT i.root_id,COUNT(DISTINCT l.target_dependency_id) targets,
 CASE WHEN COUNT(DISTINCT l.target_dependency_id)=1 THEN MIN(l.target_dependency_id) END target_dependency_id,
 MAX(CASE WHEN l.state<>'RESOLVED' THEN 1 ELSE 0 END) conflict INTO #fallback
 FROM #inventories i JOIN core.expansion_lab_link l ON l.run_id=@exp AND l.root_id=i.root_id
 JOIN stg.expansion_lab_relation b ON b.relation_id=l.relation_id AND b.kind='INV_FREIGHT' AND b.active=1
 WHERE l.state NOT IN('INACTIVE','SUPERSEDED') GROUP BY i.root_id;
 INSERT #candidate
 SELECT 'INV',i.source_key,'SCANNED',i.execution_id,NULL,i.observation_id,@reference_revision,selected.reference_release_id,NULL,
 i.business_date,CASE WHEN direct.binding_id IS NOT NULL THEN direct.entity_key ELSE freight.entity_key END,
 CASE WHEN direct.binding_id IS NOT NULL THEN direct.binding_id ELSE freight.binding_id END,
 CASE WHEN direct.binding_id IS NOT NULL THEN 'INVENTORY_BINDING' ELSE 'EXPLICIT_FREIGHT_FALLBACK' END,
 CASE WHEN direct.binding_id IS NULL THEN links.target_dependency_id END,
 CASE WHEN reason.disposition='READY' THEN 1 ELSE 0 END,
 CASE WHEN reason.disposition='READY' AND i.finished_at IS NULL THEN 1 ELSE 0 END,reason.disposition,COALESCE(i.extracted_at,@run_created)
 FROM #inventories i LEFT JOIN #fallback links ON links.root_id=i.root_id
 LEFT JOIN core.expansion_lab_dependency f ON f.dependency_id=links.target_dependency_id
 OUTER APPLY ref.ufn_analytic_reference(@run_id,@reference_revision,COALESCE(i.business_date,@start)) selected
 OUTER APPLY(SELECT * FROM ref.ufn_analytic_dimension(@run_id,@reference_revision,COALESCE(i.business_date,@start)) d WHERE d.entity='INV' AND d.source_key=i.source_key AND d.role='BRANCH') direct
 OUTER APPLY(SELECT * FROM ref.ufn_analytic_dimension(@run_id,@reference_revision,COALESCE(i.business_date,@start)) d WHERE d.entity='FRETE' AND d.source_key=f.source_key AND d.role='BRANCH') freight
 CROSS APPLY(SELECT CONVERT(VARCHAR(40),CASE WHEN i.active=0 THEN 'SOURCE_INACTIVE' WHEN i.source_conflict=1 OR i.attribute_conflict=1 THEN 'INVENTORY_ROOT_CONFLICT'
 WHEN i.observation_id IS NULL THEN 'INVENTORY_SOURCE_MISSING' WHEN i.business_date IS NULL THEN 'STARTED_DATE_MISSING'
 WHEN selected.reference_release_id IS NULL THEN 'REFERENCE_MISSING' WHEN selected.branch_policy<>'EXPLICIT_ASSIGNMENT' THEN 'BRANCH_UNRESOLVED'
 WHEN i.type IS NULL OR NOT EXISTS(SELECT 1 FROM ref.analytic_lab_label label WHERE label.reference_release_id=selected.reference_release_id
 AND label.category='INV_TYPE' AND label.raw_value=CASE WHEN LEFT(i.type,16)=N'CheckIn::Order::' THEN SUBSTRING(i.type,17,1024) ELSE i.type END COLLATE Latin1_General_100_BIN2
 AND label.raw_value IN(N'Picking',N'Return',N'Receipt',N'Loading',N'Unloading')) THEN 'TYPE_EXCLUDED'
 WHEN direct.binding_id IS NOT NULL AND direct.disposition<>'RESOLVED' THEN direct.disposition
 WHEN direct.binding_id IS NULL AND NULLIF(LTRIM(RTRIM(i.cnr_crn_psn_nickname)),N'') IS NOT NULL THEN 'BRANCH_BINDING_MISSING'
 WHEN direct.binding_id IS NULL AND(COALESCE(links.targets,0)<>1 OR links.conflict=1 OR f.state<>'VALID') THEN 'FREIGHT_FALLBACK_AMBIGUOUS'
 WHEN direct.binding_id IS NULL AND freight.binding_id IS NULL THEN 'FREIGHT_BRANCH_MISSING'
 WHEN direct.binding_id IS NULL AND freight.disposition<>'RESOLVED' THEN freight.disposition ELSE 'READY' END) disposition) reason;
 IF EXISTS(SELECT 1 FROM ctl.analytic_lab_materialization_receipt WHERE receipt_id=@receipt_id)
 BEGIN
 IF NOT EXISTS(SELECT 1 FROM ctl.analytic_lab_materialization_receipt WHERE receipt_id=@receipt_id AND run_id=@run_id AND kind='MAT02'
 AND reference_revision=@reference_revision AND mode=@mode AND full_scope=@full AND window_start=@start AND window_end_exclusive=@end)
 OR EXISTS(SELECT entity,source_key,contribution,source_execution,manifest_snapshot_id,inventory_observation_id,reference_revision,reference_release_id,source_state_id,reference_date,branch_key,branch_binding_id,branch_provenance,fallback_dependency_id,quantity,incomplete,disposition,extracted_at FROM #candidate EXCEPT SELECT entity,source_key,contribution,source_execution,manifest_snapshot_id,inventory_observation_id,reference_revision,reference_release_id,source_state_id,reference_date,branch_key,branch_binding_id,branch_provenance,fallback_dependency_id,quantity,incomplete,disposition,extracted_at FROM mart.analytic_collector_contribution_observation WHERE receipt_id=@receipt_id)
 OR EXISTS(SELECT entity,source_key,contribution,source_execution,manifest_snapshot_id,inventory_observation_id,reference_revision,reference_release_id,source_state_id,reference_date,branch_key,branch_binding_id,branch_provenance,fallback_dependency_id,quantity,incomplete,disposition,extracted_at FROM mart.analytic_collector_contribution_observation WHERE receipt_id=@receipt_id EXCEPT SELECT entity,source_key,contribution,source_execution,manifest_snapshot_id,inventory_observation_id,reference_revision,reference_release_id,source_state_id,reference_date,branch_key,branch_binding_id,branch_provenance,fallback_dependency_id,quantity,incomplete,disposition,extracted_at FROM #candidate)
 THROW 53625,N'ANA_COLLECTORS_RECEIPT_INPUT_CHANGED',1;
 SELECT candidates,inserts,updates,noops,ready,blocked FROM ctl.analytic_lab_materialization_receipt WHERE receipt_id=@receipt_id; RETURN;
 END;
 SELECT n.*,o.observation_id prior_observation_id,o.reference_date old_reference_date,o.branch_key old_branch_key,
 CONVERT(VARCHAR(8),CASE WHEN o.observation_id IS NULL THEN 'INSERT'
 WHEN EXISTS(SELECT n.entity,n.source_key,n.contribution,n.source_execution,n.manifest_snapshot_id,n.inventory_observation_id,n.reference_revision,n.reference_release_id,n.source_state_id,n.reference_date,n.branch_key,n.branch_binding_id,n.branch_provenance,n.fallback_dependency_id,n.quantity,n.incomplete,n.disposition,n.extracted_at EXCEPT SELECT o.entity,o.source_key,o.contribution,o.source_execution,o.manifest_snapshot_id,o.inventory_observation_id,o.reference_revision,o.reference_release_id,o.source_state_id,o.reference_date,o.branch_key,o.branch_binding_id,o.branch_provenance,o.fallback_dependency_id,o.quantity,o.incomplete,o.disposition,o.extracted_at) THEN 'UPDATE' ELSE 'NOOP' END) action
 INTO #decisions FROM #candidate n LEFT JOIN mart.analytic_collector_contribution c ON c.run_id=@run_id AND c.entity=n.entity AND c.source_key=n.source_key AND c.contribution=n.contribution
 LEFT JOIN mart.analytic_collector_contribution_observation o ON o.observation_id=c.observation_id;
 INSERT ctl.analytic_lab_materialization_receipt SELECT @receipt_id,@run_id,'MAT02',@reference_revision,@mode,@full,@start,@end,COUNT_BIG(*),
 COALESCE(SUM(CONVERT(BIGINT,CASE WHEN action='INSERT' THEN 1 ELSE 0 END)),0),COALESCE(SUM(CONVERT(BIGINT,CASE WHEN action='UPDATE' THEN 1 ELSE 0 END)),0),
 COALESCE(SUM(CONVERT(BIGINT,CASE WHEN action='NOOP' THEN 1 ELSE 0 END)),0),COALESCE(SUM(CONVERT(BIGINT,CASE WHEN disposition='READY' THEN 1 ELSE 0 END)),0),
 COALESCE(SUM(CONVERT(BIGINT,CASE WHEN disposition<>'READY' THEN 1 ELSE 0 END)),0),@now FROM #decisions;
 INSERT mart.analytic_collector_contribution_observation(receipt_id,run_id,entity,source_key,contribution,source_execution,manifest_snapshot_id,inventory_observation_id,reference_revision,reference_release_id,source_state_id,reference_date,branch_key,branch_binding_id,branch_provenance,fallback_dependency_id,quantity,incomplete,disposition,extracted_at,action,prior_observation_id,old_reference_date,old_branch_key)
 SELECT @receipt_id,@run_id,entity,source_key,contribution,source_execution,manifest_snapshot_id,inventory_observation_id,reference_revision,reference_release_id,source_state_id,reference_date,branch_key,branch_binding_id,branch_provenance,fallback_dependency_id,quantity,incomplete,disposition,extracted_at,action,prior_observation_id,old_reference_date,old_branch_key FROM #decisions;
 INSERT mart.analytic_collector_contribution SELECT @run_id,entity,source_key,contribution,observation_id,reference_date,branch_key
 FROM mart.analytic_collector_contribution_observation WHERE receipt_id=@receipt_id AND action='INSERT';
 UPDATE c SET observation_id=o.observation_id,reference_date=o.reference_date,branch_key=o.branch_key
 FROM mart.analytic_collector_contribution c JOIN mart.analytic_collector_contribution_observation o
 ON o.run_id=c.run_id AND o.entity=c.entity AND o.source_key=c.source_key AND o.contribution=c.contribution
 WHERE o.receipt_id=@receipt_id AND o.action='UPDATE';
 SELECT reference_date,branch_key INTO #partitions FROM #decisions WHERE reference_date IS NOT NULL AND branch_key IS NOT NULL AND action<>'NOOP'
 UNION SELECT old_reference_date,old_branch_key FROM #decisions WHERE old_reference_date IS NOT NULL AND old_branch_key IS NOT NULL AND action<>'NOOP';
 SELECT p.reference_date,p.branch_key,
 COALESCE(SUM(CASE WHEN o.contribution='ISSUED' THEN o.quantity ELSE 0 END),0) issued,
 COALESCE(SUM(CASE WHEN o.contribution='UNLOADED' THEN o.quantity ELSE 0 END),0) unloaded,
 COALESCE(SUM(CASE WHEN o.contribution='SCANNED' THEN o.quantity ELSE 0 END),0) scanned,
 COALESCE(SUM(o.incomplete),0) incomplete
 INTO #daily FROM #partitions p LEFT JOIN mart.analytic_collector_contribution c ON c.run_id=@run_id AND c.reference_date=p.reference_date AND c.branch_key=p.branch_key
 LEFT JOIN mart.analytic_collector_contribution_observation o ON o.observation_id=c.observation_id GROUP BY p.reference_date,p.branch_key;
 INSERT mart.analytic_collector_daily_observation(run_id,receipt_id,reference_date,branch_key,classification,issued,unloaded,scanned,incomplete,total,percentage,has_inventory,active,reference_revision,reference_release_id)
 SELECT @run_id,@receipt_id,d.reference_date,d.branch_key,N'Geral',d.issued,d.unloaded,d.scanned,d.incomplete,d.issued+d.unloaded,
 CONVERT(DECIMAL(28,8),CASE WHEN d.issued+d.unloaded=0 THEN 0 ELSE CONVERT(DECIMAL(28,8),d.scanned)*100/CONVERT(DECIMAL(28,8),d.issued+d.unloaded) END),
 CASE WHEN d.scanned>0 THEN 1 ELSE 0 END,CASE WHEN d.issued+d.unloaded+d.scanned>0 THEN 1 ELSE 0 END,@reference_revision,s.reference_release_id
 FROM #daily d OUTER APPLY ref.ufn_analytic_reference(@run_id,@reference_revision,d.reference_date) s;
 UPDATE c SET observation_id=o.observation_id FROM mart.analytic_collector_daily c JOIN mart.analytic_collector_daily_observation o
 ON o.run_id=c.run_id AND o.reference_date=c.reference_date AND o.branch_key=c.branch_key WHERE o.receipt_id=@receipt_id;
 INSERT mart.analytic_collector_daily SELECT o.run_id,o.reference_date,o.branch_key,o.observation_id FROM mart.analytic_collector_daily_observation o
 WHERE o.receipt_id=@receipt_id AND NOT EXISTS(SELECT 1 FROM mart.analytic_collector_daily c WHERE c.run_id=o.run_id AND c.reference_date=o.reference_date AND c.branch_key=o.branch_key);
 SELECT candidates,inserts,updates,noops,ready,blocked FROM ctl.analytic_lab_materialization_receipt WHERE receipt_id=@receipt_id;

 END TRY
 BEGIN CATCH
  IF XACT_STATE()=1 ROLLBACK TRANSACTION ana_mat02;
  THROW;
 END CATCH;
END;
GO
CREATE OR ALTER PROCEDURE mart.usp_materialize_analytic_manifests @run_id UNIQUEIDENTIFIER,@receipt_id UNIQUEIDENTIFIER,
 @reference_revision INT,@mode VARCHAR(16),@full BIT,@start DATE,@end DATE,@now DATETIME2(7)
AS BEGIN SET NOCOUNT ON; SET XACT_ABORT OFF;
 -- Shared order with MAT02 avoids a DIMENSION/MAT05 lock inversion.

 IF @@TRANCOUNT=0 THROW 53501,N'ANA_TRANSACTION_REQUIRED',1;
 SAVE TRANSACTION ana_mat05;
 BEGIN TRY
 EXEC ctl.usp_analytic_lab_lock @run_id,'MAT02';
 EXEC ctl.usp_analytic_lab_lock @run_id,'MAT05';
 DECLARE @exp UNIQUEIDENTIFIER,@rel UNIQUEIDENTIFIER,@zone SYSNAME,@max INT;
 SELECT @exp=g.expansion_run,@rel=g.relational_run,@zone=CASE r.zone_id WHEN 'UTC' THEN 'UTC' ELSE 'E. South America Standard Time' END,@max=r.maximum_rows
 FROM ctl.analytic_lab_run r JOIN ctl.analytic_lab_source_group g ON g.run_id=r.run_id
 WHERE r.run_id=@run_id AND @start>=r.window_start AND @end<=r.window_end_exclusive
 AND(@full=0 OR(@start=r.window_start AND @end=r.window_end_exclusive));
 IF @exp IS NULL OR @receipt_id IS NULL OR @now IS NULL OR @start IS NULL OR @end IS NULL OR @start>=@end
 OR ISNULL(@reference_revision,0) NOT BETWEEN 1 AND 100000 OR ISNULL(@mode,'') NOT IN('BOOTSTRAP','INCREMENTAL','BACKFILL','REPLAY')
 OR @full IS NULL THROW 53661,N'ANA_MANIFEST_SCOPE',1;
 EXEC ctl.usp_expansion_lab_lock @exp,'RELATION';
 EXEC ctl.usp_relational_lab_lock @rel;
 EXEC ctl.usp_analytic_lab_lock @run_id,'DIMENSION';
 IF NOT EXISTS(SELECT 1 FROM ctl.relational_lab_capture c WHERE c.run_id=@rel AND c.entity_name=N'manifestos')
 OR EXISTS(SELECT 1 FROM ctl.relational_lab_capture c WHERE c.run_id=@rel AND c.entity_name=N'manifestos'
 AND(@full=1 OR(c.business_date>=@start AND c.business_date<@end))
 AND NOT EXISTS(SELECT 1 FROM ctl.analytic_manifest_preparation p WHERE p.run_id=@run_id AND p.execution_id=c.execution_id))
 THROW 53662,N'ANA_MANIFEST_PREPARATION_REQUIRED',1;
 SELECT m.*,CONVERT(DATE,COALESCE(m.departured_at,m.created_at) AT TIME ZONE @zone) business_date,
 CASE WHEN m.departured_at IS NOT NULL THEN 'DEPARTURED_AT' ELSE 'CREATED_AT_ABSENT_NULL_FALLBACK' END competence_origin
 INTO #roots FROM core.analytic_lab_manifest_projection m WHERE m.run_id=@run_id AND(@full=1
 OR EXISTS(SELECT 1 FROM ctl.relational_lab_capture c JOIN stg.manifesto_observation o ON o.execution_id=c.execution_id
 WHERE c.run_id=@rel AND o.source_key COLLATE Latin1_General_100_BIN2=m.source_key AND c.business_date>=@start AND c.business_date<@end)
 OR EXISTS(SELECT 1 FROM mart.analytic_manifest_current c WHERE c.run_id=@run_id AND c.source_key=m.source_key AND c.reference_date>=@start AND c.reference_date<@end));
 IF(SELECT COUNT_BIG(*) FROM #roots)>@max THROW 53663,N'ANA_MANIFEST_ROW_BOUND',1;
 CREATE UNIQUE CLUSTERED INDEX IX_roots ON #roots(source_key);
 -- Historical links remain auditable but only current child components participate in revenue.
 SELECT DISTINCT m.source_key,CONVERT(NVARCHAR(128),CONCAT(N'INTEGER:',p.[value])) COLLATE Latin1_General_100_BIN2 component_key
 INTO #current_picks FROM #roots m JOIN core.analytic_manifest_snapshot_lineage l ON l.snapshot_id=m.snapshot_id
 JOIN stg.manifesto_observation o ON o.manifesto_observation_id=l.source_observation_id
 CROSS APPLY OPENJSON(o.payload_json) p WHERE p.[key]=N'mft_pfs_pck_sequence_code' AND p.[type]=2;
 SELECT mc.* INTO #current_mc FROM core.relational_lab_link mc JOIN #current_picks p ON p.source_key=mc.origin_key AND p.component_key=mc.origin_component
 WHERE mc.run_id=@rel AND mc.relation_kind='MC' AND mc.active=1;
 SELECT DISTINCT root.source_key,c.component_key INTO #current_items FROM core.relational_lab_root root
 JOIN stg.relational_lab_component c ON c.execution_id=root.execution_id AND c.source_key=root.source_key
 WHERE root.run_id=@rel AND root.entity_name=N'coletas' AND c.component_kind=N'ITEM' AND c.presence=N'VALUE'
 AND EXISTS(SELECT 1 FROM #current_mc mc WHERE mc.target_key=root.source_key);
 SELECT cf.* INTO #current_cf FROM core.relational_lab_link cf JOIN #current_items c ON c.source_key=cf.origin_key AND c.component_key=cf.origin_component
 WHERE cf.run_id=@rel AND cf.relation_kind='CF' AND cf.active=1;

 SELECT m.source_key,b.freight_key,CONVERT(VARCHAR(12),'DIRECT') path_kind,b.binding_id direct_binding_id,
 CONVERT(BIGINT,NULL) mc_link_id,CONVERT(BIGINT,NULL) cf_link_id,CONVERT(BIGINT,NULL) crosswalk_binding_id
 INTO #paths FROM #roots m JOIN core.analytic_lab_freight_relation_current b ON b.run_id=@run_id AND b.kind='DIRECT' AND b.origin_key=m.source_key AND b.active=1
 UNION ALL
 SELECT m.source_key,x.freight_key,'COLLECTION',NULL,mc.link_id,cf.link_id,x.binding_id
 FROM #roots m JOIN #current_mc mc ON mc.run_id=@rel AND mc.relation_kind='MC' AND mc.origin_key=m.source_key AND mc.active=1
 JOIN #current_cf cf ON cf.run_id=@rel AND cf.relation_kind='CF' AND cf.origin_key=mc.target_key AND cf.active=1
 JOIN core.analytic_lab_freight_relation_current x ON x.run_id=@run_id AND x.kind='CROSSWALK' AND x.origin_key=cf.target_key AND x.active=1;
 IF(SELECT COUNT_BIG(*) FROM #paths)>CONVERT(BIGINT,@max)*64 THROW 53663,N'ANA_MANIFEST_PATH_BOUND',1;
 CREATE INDEX IX_paths ON #paths(source_key,freight_key);
 SELECT source_key,freight_key,MAX(CASE WHEN path_kind='DIRECT' THEN 1 ELSE 0 END) direct_path,
 MAX(CASE WHEN path_kind='COLLECTION' THEN 1 ELSE 0 END) collection_path INTO #unique_freights FROM #paths GROUP BY source_key,freight_key;
 SELECT u.*,d.dependency_id,d.stage_record_id freight_stage_id,t.term_id,t.active source_active,t.currency,
 TRY_CONVERT(DECIMAL(28,8),JSON_VALUE(f.financial_json,'$.total.typedDecimal')) amount,
 CONVERT(VARCHAR(40),CASE WHEN d.dependency_id IS NULL OR d.state<>'VALID' THEN 'FREIGHT_MISSING_OR_CONFLICT'
 WHEN t.term_id IS NULL OR t.terms_state<>'READY' THEN 'FREIGHT_TERMS_MISSING_OR_CONFLICT'
 WHEN t.active=0 THEN 'INACTIVE' WHEN t.currency<>'BRL' OR t.unit<>'MAJOR' THEN 'CURRENCY_UNRESOLVED'
 WHEN TRY_CONVERT(DECIMAL(28,8),JSON_VALUE(f.financial_json,'$.total.typedDecimal')) IS NULL THEN 'FREIGHT_AMOUNT_MISSING' ELSE 'READY' END) disposition
 INTO #freights FROM #unique_freights u LEFT JOIN core.expansion_lab_dependency d ON d.run_id=@exp AND d.entity='FRETE' AND d.source_key=u.freight_key
 LEFT JOIN stg.frete_record f ON f.stage_record_id=d.stage_record_id LEFT JOIN core.ufn_expansion_freight_terms(@exp) t ON t.source_key=u.freight_key;
 SELECT source_key,COUNT_BIG(*) unique_freights,SUM(CONVERT(BIGINT,direct_path)) direct_freights,SUM(CONVERT(BIGINT,collection_path)) collection_freights,
 SUM(CONVERT(BIGINT,CASE WHEN direct_path=1 AND collection_path=1 THEN 1 ELSE 0 END)) shared_freights,
 SUM(CASE WHEN direct_path=1 AND disposition='READY' THEN amount ELSE CONVERT(DECIMAL(28,8),0) END) direct_revenue,
 SUM(CASE WHEN collection_path=1 AND disposition='READY' THEN amount ELSE CONVERT(DECIMAL(28,8),0) END) collection_revenue,
 SUM(CASE WHEN direct_path=1 AND collection_path=1 AND disposition='READY' THEN amount ELSE CONVERT(DECIMAL(28,8),0) END) shared_revenue,
 SUM(CASE WHEN disposition='READY' THEN amount ELSE CONVERT(DECIMAL(28,8),0) END) total_revenue,
 SUM(CASE WHEN disposition NOT IN('READY','INACTIVE') THEN 1 ELSE 0 END) blocked
 INTO #revenue FROM #freights GROUP BY source_key;
 SELECT m.*,selected.reference_release_id,selected.branch_policy,state.state_id,state.active source_active,
 branch.entity_key branch_key,branch.binding_id branch_binding_id,branch.disposition branch_disposition,
 tractor.entity_key tractor_key,tractor.binding_id tractor_binding_id,tractor.disposition tractor_disposition,tractor.capacity tractor_capacity,tractor.capacity_unit tractor_unit,
 tractor.raw_document owner_document,tractor.license_plate tractor_plate,
 CASE WHEN trailer1.active=1 THEN trailer1.binding_id END trailer1_binding_id,CASE WHEN trailer1.active=1 THEN trailer1.entity_key END trailer1_key,trailer1.disposition trailer1_disposition,CASE WHEN trailer1.active=1 THEN trailer1.capacity END trailer1_capacity,CASE WHEN trailer1.active=1 THEN trailer1.capacity_unit END trailer1_unit,
 CASE WHEN trailer2.active=1 THEN trailer2.binding_id END trailer2_binding_id,CASE WHEN trailer2.active=1 THEN trailer2.entity_key END trailer2_key,trailer2.disposition trailer2_disposition,CASE WHEN trailer2.active=1 THEN trailer2.capacity END trailer2_capacity,CASE WHEN trailer2.active=1 THEN trailer2.capacity_unit END trailer2_unit,
 driver.binding_id driver_binding_id,driver.disposition driver_disposition,
 fleet.reference_release_id fleet_release_id,fleet.ownership_code,fleet.contract_code,fleet.ownership_provenance,fleet.disposition fleet_disposition,
 seal.composition_id,seal.revision composition_revision,seal.expected_direct_freights,
 COALESCE(r.direct_freights,0) direct_freights,COALESCE(r.collection_freights,0) collection_freights,COALESCE(r.shared_freights,0) shared_freights,COALESCE(r.unique_freights,0) unique_freights,
 TRY_CONVERT(DECIMAL(28,8),COALESCE(r.direct_revenue,0)) direct_revenue,TRY_CONVERT(DECIMAL(28,8),COALESCE(r.collection_revenue,0)) collection_revenue,
 TRY_CONVERT(DECIMAL(28,8),COALESCE(r.shared_revenue,0)) shared_revenue,TRY_CONVERT(DECIMAL(28,8),COALESCE(r.total_revenue,0)) total_revenue,COALESCE(r.blocked,0) blocked_freights
 INTO #bound FROM #roots m
 LEFT JOIN ref.analytic_lab_manifest_state_current state ON state.run_id=@run_id AND state.source_key=m.source_key
 OUTER APPLY ref.ufn_analytic_reference(@run_id,@reference_revision,COALESCE(m.business_date,@start)) selected
 OUTER APPLY(SELECT * FROM ref.ufn_analytic_dimension(@run_id,@reference_revision,COALESCE(m.business_date,@start)) d WHERE d.entity='MAN' AND d.source_key=m.source_key AND d.role='BRANCH') branch
 OUTER APPLY(SELECT * FROM ref.ufn_analytic_dimension(@run_id,@reference_revision,COALESCE(m.business_date,@start)) d WHERE d.entity='MAN' AND d.source_key=m.source_key AND d.role='TRACTOR') tractor
 OUTER APPLY(SELECT * FROM ref.ufn_analytic_dimension(@run_id,@reference_revision,COALESCE(m.business_date,@start)) d WHERE d.entity='MAN' AND d.source_key=m.source_key AND d.role='TRAILER1') trailer1
 OUTER APPLY(SELECT * FROM ref.ufn_analytic_dimension(@run_id,@reference_revision,COALESCE(m.business_date,@start)) d WHERE d.entity='MAN' AND d.source_key=m.source_key AND d.role='TRAILER2') trailer2
 OUTER APPLY(SELECT * FROM ref.ufn_analytic_dimension(@run_id,@reference_revision,COALESCE(m.business_date,@start)) d WHERE d.entity='MAN' AND d.source_key=m.source_key AND d.role='DRIVER') driver
 OUTER APPLY ref.ufn_analytic_fleet(@run_id,@reference_revision,COALESCE(m.business_date,@start),tractor.raw_document,m.mft_vie_onr_name,m.contract_type,m.mft_mdr_contract_type) fleet
 OUTER APPLY(SELECT c.* FROM ctl.analytic_manifest_composition c WHERE c.run_id=@run_id AND c.manifest_key=m.source_key
 AND NOT EXISTS(SELECT 1 FROM ctl.analytic_manifest_composition later WHERE later.run_id=c.run_id AND later.manifest_key=c.manifest_key AND later.revision>c.revision)) seal
 LEFT JOIN #revenue r ON r.source_key=m.source_key;
 SELECT b.*,TRY_CONVERT(DECIMAL(28,8),tractor_capacity+COALESCE(trailer1_capacity,0)+COALESCE(trailer2_capacity,0)) total_capacity,
 CONVERT(VARCHAR(40),CASE WHEN b.disposition<>'READY' THEN b.disposition WHEN state_id IS NULL THEN 'SOURCE_STATE_MISSING' WHEN source_active=0 THEN 'SOURCE_INACTIVE'
 WHEN business_date IS NULL THEN 'COMPETENCE_MISSING' WHEN reference_release_id IS NULL THEN 'REFERENCE_MISSING'
 WHEN branch_policy<>'EXPLICIT_ASSIGNMENT' OR ISNULL(branch_disposition,'MISSING')<>'RESOLVED' THEN 'BRANCH_UNRESOLVED'
 WHEN ISNULL(tractor_disposition,'MISSING')<>'RESOLVED' OR ISNULL(driver_disposition,'MISSING')<>'RESOLVED' THEN 'FLEET_BINDING_MISSING'
 WHEN EXISTS(SELECT 1 FROM ref.analytic_lab_exclusion x WHERE x.reference_release_id=b.reference_release_id AND x.kind='LICENSE_PLATE' AND(x.match_value=b.mft_vie_license_plate OR x.match_value=b.tractor_plate)) THEN 'EXCLUDED_VEHICLE'
 WHEN tractor_key=trailer1_key OR tractor_key=trailer2_key OR trailer1_key=trailer2_key THEN 'FLEET_ROLE_CONFLICT'
 WHEN (b.mft_tl1_license_plate IS NOT NULL OR trailer1_binding_id IS NOT NULL) AND ISNULL(trailer1_disposition,'MISSING')<>'RESOLVED' THEN 'TRAILER1_BINDING_MISSING'
 WHEN (b.mft_tl2_license_plate IS NOT NULL OR trailer2_binding_id IS NOT NULL) AND ISNULL(trailer2_disposition,'MISSING')<>'RESOLVED' THEN 'TRAILER2_BINDING_MISSING'
 WHEN tractor_capacity IS NULL OR tractor_capacity<0 OR tractor_unit<>'KG'
 OR(trailer1_binding_id IS NOT NULL AND(trailer1_capacity IS NULL OR trailer1_capacity<0 OR trailer1_unit<>'KG'))
 OR(trailer2_binding_id IS NOT NULL AND(trailer2_capacity IS NULL OR trailer2_capacity<0 OR trailer2_unit<>'KG')) THEN 'CAPACITY_UNRESOLVED'
 WHEN TRY_CONVERT(DECIMAL(28,8),tractor_capacity+COALESCE(trailer1_capacity,0)+COALESCE(trailer2_capacity,0)) IS NULL THEN 'CAPACITY_OVERFLOW'
 WHEN fleet_disposition<>'RESOLVED' THEN fleet_disposition
 WHEN composition_id IS NULL THEN 'COMPOSITION_MISSING' WHEN expected_direct_freights<>direct_freights
 OR EXISTS(SELECT 1 FROM core.analytic_lab_freight_relation_current rel WHERE rel.run_id=@run_id AND rel.kind='DIRECT' AND rel.origin_key=b.source_key AND rel.revision>b.composition_revision) THEN 'COMPOSITION_OUTDATED'
 WHEN EXISTS(SELECT 1 FROM #current_picks p WHERE p.source_key=b.source_key
 AND NOT EXISTS(SELECT 1 FROM #current_mc mc WHERE mc.origin_key=p.source_key AND mc.origin_component=p.component_key)) THEN 'MC_UNRESOLVED'
 WHEN EXISTS(SELECT 1 FROM #current_mc mc JOIN #current_items c ON c.source_key=mc.target_key WHERE mc.origin_key=b.source_key
 AND NOT EXISTS(SELECT 1 FROM #current_cf cf WHERE cf.origin_key=c.source_key AND cf.origin_component=c.component_key)) THEN 'CF_UNRESOLVED'
 WHEN EXISTS(SELECT 1 FROM #current_mc mc JOIN #current_cf cf ON cf.origin_key=mc.target_key WHERE mc.origin_key=b.source_key
 AND NOT EXISTS(SELECT 1 FROM core.analytic_lab_freight_relation_current x WHERE x.run_id=@run_id AND x.kind='CROSSWALK' AND x.origin_key=cf.target_key AND x.active=1)) THEN 'FREIGHT_CROSSWALK_MISSING'
 WHEN blocked_freights>0 THEN 'FREIGHT_DEPENDENCY_BLOCKED'
 WHEN direct_revenue IS NULL OR collection_revenue IS NULL OR total_revenue IS NULL OR shared_revenue IS NULL THEN 'REVENUE_OVERFLOW'
 WHEN manifest_freights_total IS NULL AND direct_freights>0 THEN 'DIRECT_AGGREGATE_MISSING'
 WHEN COALESCE(manifest_freights_total,0)<>direct_revenue THEN 'DIRECT_AGGREGATE_CONFLICT'
 ELSE 'READY' END) result_disposition INTO #classified FROM #bound b;
 SELECT source_key,snapshot_id,state_id,@reference_revision reference_revision,reference_release_id,fleet_release_id,business_date reference_date,
 CONVERT(VARCHAR(32),competence_origin) competence_provenance,branch_key,branch_binding_id,tractor_binding_id,trailer1_binding_id,trailer2_binding_id,driver_binding_id,
 composition_id,direct_freights,collection_freights,shared_freights,unique_freights,direct_revenue,collection_revenue,shared_revenue,total_revenue,
 CONVERT(VARCHAR(8),'BRL') currency,tractor_capacity,trailer1_capacity,trailer2_capacity,total_capacity,CONVERT(VARCHAR(8),'KG') capacity_unit,
 owner_document,ownership_code,contract_code,ownership_provenance,source_active,result_disposition disposition,
 CONVERT(BIT,CASE WHEN result_disposition='READY' THEN 1 ELSE 0 END) active,extracted_at INTO #result FROM #classified;
 IF EXISTS(SELECT 1 FROM ctl.analytic_lab_materialization_receipt WHERE receipt_id=@receipt_id)
 BEGIN
 IF NOT EXISTS(SELECT 1 FROM ctl.analytic_lab_materialization_receipt WHERE receipt_id=@receipt_id AND run_id=@run_id AND kind='MAT05' AND reference_revision=@reference_revision
 AND mode=@mode AND full_scope=@full AND window_start=@start AND window_end_exclusive=@end) THROW 53664,N'ANA_MANIFEST_RECEIPT_CONFLICT',1;
 IF EXISTS(SELECT CONVERT(VARBINARY(MAX),s.source_key),s.snapshot_id,s.state_id,s.reference_revision,s.reference_release_id,s.fleet_release_id,s.reference_date,CONVERT(VARBINARY(MAX),s.competence_provenance),CONVERT(VARBINARY(MAX),s.branch_key),s.branch_binding_id,s.tractor_binding_id,s.trailer1_binding_id,s.trailer2_binding_id,s.driver_binding_id,s.composition_id,s.direct_freights,s.collection_freights,s.shared_freights,s.unique_freights,s.direct_revenue,s.collection_revenue,s.shared_revenue,s.total_revenue,CONVERT(VARBINARY(MAX),s.currency),s.tractor_capacity,s.trailer1_capacity,s.trailer2_capacity,s.total_capacity,CONVERT(VARBINARY(MAX),s.capacity_unit),CONVERT(VARBINARY(MAX),s.owner_document),CONVERT(VARBINARY(MAX),s.ownership_code),CONVERT(VARBINARY(MAX),s.contract_code),CONVERT(VARBINARY(MAX),s.ownership_provenance),s.source_active,CONVERT(VARBINARY(MAX),s.disposition),s.active,s.extracted_at FROM #result s EXCEPT SELECT CONVERT(VARBINARY(MAX),t.source_key),t.snapshot_id,t.state_id,t.reference_revision,t.reference_release_id,t.fleet_release_id,t.reference_date,CONVERT(VARBINARY(MAX),t.competence_provenance),CONVERT(VARBINARY(MAX),t.branch_key),t.branch_binding_id,t.tractor_binding_id,t.trailer1_binding_id,t.trailer2_binding_id,t.driver_binding_id,t.composition_id,t.direct_freights,t.collection_freights,t.shared_freights,t.unique_freights,t.direct_revenue,t.collection_revenue,t.shared_revenue,t.total_revenue,CONVERT(VARBINARY(MAX),t.currency),t.tractor_capacity,t.trailer1_capacity,t.trailer2_capacity,t.total_capacity,CONVERT(VARBINARY(MAX),t.capacity_unit),CONVERT(VARBINARY(MAX),t.owner_document),CONVERT(VARBINARY(MAX),t.ownership_code),CONVERT(VARBINARY(MAX),t.contract_code),CONVERT(VARBINARY(MAX),t.ownership_provenance),t.source_active,CONVERT(VARBINARY(MAX),t.disposition),t.active,t.extracted_at FROM mart.analytic_manifest_observation t WHERE receipt_id=@receipt_id)
 OR EXISTS(SELECT CONVERT(VARBINARY(MAX),t.source_key),t.snapshot_id,t.state_id,t.reference_revision,t.reference_release_id,t.fleet_release_id,t.reference_date,CONVERT(VARBINARY(MAX),t.competence_provenance),CONVERT(VARBINARY(MAX),t.branch_key),t.branch_binding_id,t.tractor_binding_id,t.trailer1_binding_id,t.trailer2_binding_id,t.driver_binding_id,t.composition_id,t.direct_freights,t.collection_freights,t.shared_freights,t.unique_freights,t.direct_revenue,t.collection_revenue,t.shared_revenue,t.total_revenue,CONVERT(VARBINARY(MAX),t.currency),t.tractor_capacity,t.trailer1_capacity,t.trailer2_capacity,t.total_capacity,CONVERT(VARBINARY(MAX),t.capacity_unit),CONVERT(VARBINARY(MAX),t.owner_document),CONVERT(VARBINARY(MAX),t.ownership_code),CONVERT(VARBINARY(MAX),t.contract_code),CONVERT(VARBINARY(MAX),t.ownership_provenance),t.source_active,CONVERT(VARBINARY(MAX),t.disposition),t.active,t.extracted_at FROM mart.analytic_manifest_observation t WHERE receipt_id=@receipt_id EXCEPT SELECT CONVERT(VARBINARY(MAX),s.source_key),s.snapshot_id,s.state_id,s.reference_revision,s.reference_release_id,s.fleet_release_id,s.reference_date,CONVERT(VARBINARY(MAX),s.competence_provenance),CONVERT(VARBINARY(MAX),s.branch_key),s.branch_binding_id,s.tractor_binding_id,s.trailer1_binding_id,s.trailer2_binding_id,s.driver_binding_id,s.composition_id,s.direct_freights,s.collection_freights,s.shared_freights,s.unique_freights,s.direct_revenue,s.collection_revenue,s.shared_revenue,s.total_revenue,CONVERT(VARBINARY(MAX),s.currency),s.tractor_capacity,s.trailer1_capacity,s.trailer2_capacity,s.total_capacity,CONVERT(VARBINARY(MAX),s.capacity_unit),CONVERT(VARBINARY(MAX),s.owner_document),CONVERT(VARBINARY(MAX),s.ownership_code),CONVERT(VARBINARY(MAX),s.contract_code),CONVERT(VARBINARY(MAX),s.ownership_provenance),s.source_active,CONVERT(VARBINARY(MAX),s.disposition),s.active,s.extracted_at FROM #result s)
 THROW 53665,N'ANA_MANIFEST_RETRY_INPUT_CHANGED',1;
 IF EXISTS(SELECT p.source_key,p.path_kind,p.freight_key,p.direct_binding_id,p.mc_link_id,p.cf_link_id,p.crosswalk_binding_id FROM #paths p EXCEPT
 SELECT o.source_key,p.path_kind,p.freight_key,p.direct_binding_id,p.mc_link_id,p.cf_link_id,p.crosswalk_binding_id FROM mart.analytic_manifest_freight_path p JOIN mart.analytic_manifest_observation o ON o.observation_id=p.observation_id WHERE o.receipt_id=@receipt_id)
 OR EXISTS(SELECT o.source_key,p.path_kind,p.freight_key,p.direct_binding_id,p.mc_link_id,p.cf_link_id,p.crosswalk_binding_id FROM mart.analytic_manifest_freight_path p JOIN mart.analytic_manifest_observation o ON o.observation_id=p.observation_id WHERE o.receipt_id=@receipt_id EXCEPT
 SELECT p.source_key,p.path_kind,p.freight_key,p.direct_binding_id,p.mc_link_id,p.cf_link_id,p.crosswalk_binding_id FROM #paths p)
 OR EXISTS(SELECT source_key,dependency_id,freight_stage_id,term_id,direct_path,collection_path,source_active,amount,disposition FROM #freights WHERE dependency_id IS NOT NULL EXCEPT
 SELECT o.source_key,f.dependency_id,f.freight_stage_id,f.term_id,CONVERT(INT,f.direct_path),CONVERT(INT,f.collection_path),f.source_active,f.amount,f.disposition FROM mart.analytic_manifest_freight f JOIN mart.analytic_manifest_observation o ON o.observation_id=f.observation_id WHERE o.receipt_id=@receipt_id)
 OR EXISTS(SELECT o.source_key,f.dependency_id,f.freight_stage_id,f.term_id,CONVERT(INT,f.direct_path),CONVERT(INT,f.collection_path),f.source_active,f.amount,f.disposition FROM mart.analytic_manifest_freight f JOIN mart.analytic_manifest_observation o ON o.observation_id=f.observation_id WHERE o.receipt_id=@receipt_id EXCEPT
 SELECT source_key,dependency_id,freight_stage_id,term_id,direct_path,collection_path,source_active,amount,disposition FROM #freights WHERE dependency_id IS NOT NULL)
 THROW 53665,N'ANA_MANIFEST_RETRY_LINEAGE_CHANGED',1;
 SELECT candidates,inserts,updates,noops,ready,blocked FROM ctl.analytic_lab_materialization_receipt WHERE receipt_id=@receipt_id; RETURN;
 END;
 SELECT s.*,o.observation_id prior_observation_id,o.reference_date old_reference_date,o.branch_key old_branch_key,
 CONVERT(VARCHAR(8),CASE WHEN o.observation_id IS NULL THEN 'INSERT' WHEN EXISTS(SELECT CONVERT(VARBINARY(MAX),s.source_key),s.state_id,s.reference_revision,s.reference_release_id,s.fleet_release_id,s.reference_date,CONVERT(VARBINARY(MAX),s.competence_provenance),CONVERT(VARBINARY(MAX),s.branch_key),s.branch_binding_id,s.tractor_binding_id,s.trailer1_binding_id,s.trailer2_binding_id,s.driver_binding_id,s.composition_id,s.direct_freights,s.collection_freights,s.shared_freights,s.unique_freights,s.direct_revenue,s.collection_revenue,s.shared_revenue,s.total_revenue,CONVERT(VARBINARY(MAX),s.currency),s.tractor_capacity,s.trailer1_capacity,s.trailer2_capacity,s.total_capacity,CONVERT(VARBINARY(MAX),s.capacity_unit),CONVERT(VARBINARY(MAX),s.owner_document),CONVERT(VARBINARY(MAX),s.ownership_code),CONVERT(VARBINARY(MAX),s.contract_code),CONVERT(VARBINARY(MAX),s.ownership_provenance),s.source_active,CONVERT(VARBINARY(MAX),s.disposition),s.active,sm.[sequence_code],CONVERT(VARBINARY(MAX),sm.[mft_crn_psn_nickname]),sm.[created_at],sm.[departured_at],sm.[closed_at],sm.[finished_at],CONVERT(VARBINARY(MAX),sm.[status]),sm.[mft_mfs_number],CONVERT(VARBINARY(MAX),sm.[mft_mfs_key]),CONVERT(VARBINARY(MAX),sm.[mdfe_status]),CONVERT(VARBINARY(MAX),sm.[mft_ape_name]),CONVERT(VARBINARY(MAX),sm.[mft_man_name]),CONVERT(VARBINARY(MAX),sm.[mft_vie_license_plate]),CONVERT(VARBINARY(MAX),sm.[mft_vie_vee_name]),CONVERT(VARBINARY(MAX),sm.[mft_vie_onr_name]),CONVERT(VARBINARY(MAX),sm.[mft_mdr_iil_name]),sm.[vehicle_departure_km],sm.[closing_km],sm.[traveled_km],sm.[invoices_count],sm.[invoices_volumes],sm.[invoices_weight],sm.[total_taxed_weight],sm.[total_cubic_volume],sm.[invoices_value],sm.[manifest_freights_total],sm.[mft_pfs_pck_sequence_code],CONVERT(VARBINARY(MAX),sm.[mft_cat_cot_number]),sm.[daily_subtotal],sm.[total_cost],sm.[operational_expenses_total],sm.[mft_a_t_inss_value],sm.[mft_a_t_sest_senat_value],sm.[mft_a_t_ir_value],sm.[paying_total],CONVERT(VARBINARY(MAX),sm.[mft_uer_name]),CONVERT(VARBINARY(MAX),sm.[mft_aoe_rer_name]),sm.[advance_subtotal],sm.[toll_subtotal],sm.[fleet_costs_subtotal],CONVERT(VARBINARY(MAX),sm.[mft_s_n_svs_sge_pyr_nickname]),CONVERT(VARBINARY(MAX),sm.[mft_s_n_svs_sge_sse_name]),sm.[mobile_read_at],sm.[km],sm.[manual_km],sm.[generate_mdfe],sm.[monitoring_request],sm.[delivery_manifest_items_count],sm.[transfer_manifest_items_count],sm.[pick_manifest_items_count],sm.[dispatch_draft_manifest_items_count],sm.[consolidation_manifest_items_count],sm.[reverse_pick_manifest_items_count],sm.[manifest_items_count],sm.[finalized_manifest_items_count],sm.[uniq_destinations_count],CONVERT(VARBINARY(MAX),sm.[contract_type]),CONVERT(VARBINARY(MAX),sm.[mft_mdr_contract_type]),CONVERT(VARBINARY(MAX),sm.[calculation_type]),CONVERT(VARBINARY(MAX),sm.[cargo_type]),sm.[calculated_pick_count],sm.[calculated_delivery_count],sm.[calculated_dispatch_count],sm.[calculated_consolidation_count],sm.[calculated_reverse_pick_count],sm.[freight_subtotal],sm.[fuel_subtotal],sm.[pick_subtotal],sm.[delivery_subtotal],sm.[dispatch_subtotal],sm.[consolidation_subtotal],sm.[reverse_pick_subtotal],sm.[additionals_subtotal],sm.[discounts_subtotal],sm.[discount_value],sm.[driver_services_total],CONVERT(VARBINARY(MAX),sm.[mft_aoe_comments]),CONVERT(VARBINARY(MAX),sm.[mft_cat_cot_status]),CONVERT(VARBINARY(MAX),sm.[mft_iks_id]),CONVERT(VARBINARY(MAX),sm.[mft_s_n_sequence_code]),sm.[mft_s_n_starting_at],sm.[mft_s_n_ending_at],CONVERT(VARBINARY(MAX),sm.[mft_tl1_license_plate]),sm.[mft_tl1_weight_capacity],CONVERT(VARBINARY(MAX),sm.[mft_tl2_license_plate]),sm.[mft_tl2_weight_capacity],sm.[mft_vie_weight_capacity],sm.[mft_vie_cubic_weight],CONVERT(VARBINARY(MAX),sm.[operational_comments]),CONVERT(VARBINARY(MAX),sm.[closing_comments]),CONVERT(VARBINARY(MAX),sm.[mft_mte_unloading_recipient_names]),CONVERT(VARBINARY(MAX),sm.[mft_mte_delivery_region_names]) EXCEPT SELECT CONVERT(VARBINARY(MAX),o.source_key),o.state_id,o.reference_revision,o.reference_release_id,o.fleet_release_id,o.reference_date,CONVERT(VARBINARY(MAX),o.competence_provenance),CONVERT(VARBINARY(MAX),o.branch_key),o.branch_binding_id,o.tractor_binding_id,o.trailer1_binding_id,o.trailer2_binding_id,o.driver_binding_id,o.composition_id,o.direct_freights,o.collection_freights,o.shared_freights,o.unique_freights,o.direct_revenue,o.collection_revenue,o.shared_revenue,o.total_revenue,CONVERT(VARBINARY(MAX),o.currency),o.tractor_capacity,o.trailer1_capacity,o.trailer2_capacity,o.total_capacity,CONVERT(VARBINARY(MAX),o.capacity_unit),CONVERT(VARBINARY(MAX),o.owner_document),CONVERT(VARBINARY(MAX),o.ownership_code),CONVERT(VARBINARY(MAX),o.contract_code),CONVERT(VARBINARY(MAX),o.ownership_provenance),o.source_active,CONVERT(VARBINARY(MAX),o.disposition),o.active,om.[sequence_code],CONVERT(VARBINARY(MAX),om.[mft_crn_psn_nickname]),om.[created_at],om.[departured_at],om.[closed_at],om.[finished_at],CONVERT(VARBINARY(MAX),om.[status]),om.[mft_mfs_number],CONVERT(VARBINARY(MAX),om.[mft_mfs_key]),CONVERT(VARBINARY(MAX),om.[mdfe_status]),CONVERT(VARBINARY(MAX),om.[mft_ape_name]),CONVERT(VARBINARY(MAX),om.[mft_man_name]),CONVERT(VARBINARY(MAX),om.[mft_vie_license_plate]),CONVERT(VARBINARY(MAX),om.[mft_vie_vee_name]),CONVERT(VARBINARY(MAX),om.[mft_vie_onr_name]),CONVERT(VARBINARY(MAX),om.[mft_mdr_iil_name]),om.[vehicle_departure_km],om.[closing_km],om.[traveled_km],om.[invoices_count],om.[invoices_volumes],om.[invoices_weight],om.[total_taxed_weight],om.[total_cubic_volume],om.[invoices_value],om.[manifest_freights_total],om.[mft_pfs_pck_sequence_code],CONVERT(VARBINARY(MAX),om.[mft_cat_cot_number]),om.[daily_subtotal],om.[total_cost],om.[operational_expenses_total],om.[mft_a_t_inss_value],om.[mft_a_t_sest_senat_value],om.[mft_a_t_ir_value],om.[paying_total],CONVERT(VARBINARY(MAX),om.[mft_uer_name]),CONVERT(VARBINARY(MAX),om.[mft_aoe_rer_name]),om.[advance_subtotal],om.[toll_subtotal],om.[fleet_costs_subtotal],CONVERT(VARBINARY(MAX),om.[mft_s_n_svs_sge_pyr_nickname]),CONVERT(VARBINARY(MAX),om.[mft_s_n_svs_sge_sse_name]),om.[mobile_read_at],om.[km],om.[manual_km],om.[generate_mdfe],om.[monitoring_request],om.[delivery_manifest_items_count],om.[transfer_manifest_items_count],om.[pick_manifest_items_count],om.[dispatch_draft_manifest_items_count],om.[consolidation_manifest_items_count],om.[reverse_pick_manifest_items_count],om.[manifest_items_count],om.[finalized_manifest_items_count],om.[uniq_destinations_count],CONVERT(VARBINARY(MAX),om.[contract_type]),CONVERT(VARBINARY(MAX),om.[mft_mdr_contract_type]),CONVERT(VARBINARY(MAX),om.[calculation_type]),CONVERT(VARBINARY(MAX),om.[cargo_type]),om.[calculated_pick_count],om.[calculated_delivery_count],om.[calculated_dispatch_count],om.[calculated_consolidation_count],om.[calculated_reverse_pick_count],om.[freight_subtotal],om.[fuel_subtotal],om.[pick_subtotal],om.[delivery_subtotal],om.[dispatch_subtotal],om.[consolidation_subtotal],om.[reverse_pick_subtotal],om.[additionals_subtotal],om.[discounts_subtotal],om.[discount_value],om.[driver_services_total],CONVERT(VARBINARY(MAX),om.[mft_aoe_comments]),CONVERT(VARBINARY(MAX),om.[mft_cat_cot_status]),CONVERT(VARBINARY(MAX),om.[mft_iks_id]),CONVERT(VARBINARY(MAX),om.[mft_s_n_sequence_code]),om.[mft_s_n_starting_at],om.[mft_s_n_ending_at],CONVERT(VARBINARY(MAX),om.[mft_tl1_license_plate]),om.[mft_tl1_weight_capacity],CONVERT(VARBINARY(MAX),om.[mft_tl2_license_plate]),om.[mft_tl2_weight_capacity],om.[mft_vie_weight_capacity],om.[mft_vie_cubic_weight],CONVERT(VARBINARY(MAX),om.[operational_comments]),CONVERT(VARBINARY(MAX),om.[closing_comments]),CONVERT(VARBINARY(MAX),om.[mft_mte_unloading_recipient_names]),CONVERT(VARBINARY(MAX),om.[mft_mte_delivery_region_names])) THEN 'UPDATE' ELSE 'NOOP' END) action
 INTO #decisions FROM #result s JOIN core.analytic_manifest_snapshot sm ON sm.snapshot_id=s.snapshot_id
 LEFT JOIN mart.analytic_manifest_current c ON c.run_id=@run_id AND c.source_key=s.source_key
 LEFT JOIN mart.analytic_manifest_observation o ON o.observation_id=c.observation_id LEFT JOIN core.analytic_manifest_snapshot om ON om.snapshot_id=o.snapshot_id;
 INSERT ctl.analytic_lab_materialization_receipt SELECT @receipt_id,@run_id,'MAT05',@reference_revision,@mode,@full,@start,@end,COUNT_BIG(*),
 COALESCE(SUM(CONVERT(BIGINT,CASE WHEN action='INSERT' THEN 1 ELSE 0 END)),0),COALESCE(SUM(CONVERT(BIGINT,CASE WHEN action='UPDATE' THEN 1 ELSE 0 END)),0),
 COALESCE(SUM(CONVERT(BIGINT,CASE WHEN action='NOOP' THEN 1 ELSE 0 END)),0),COALESCE(SUM(CONVERT(BIGINT,active)),0),COALESCE(SUM(CONVERT(BIGINT,1-active)),0),@now FROM #decisions;
 INSERT mart.analytic_manifest_observation(receipt_id,run_id,source_key,snapshot_id,state_id,reference_revision,reference_release_id,fleet_release_id,reference_date,competence_provenance,branch_key,branch_binding_id,tractor_binding_id,trailer1_binding_id,trailer2_binding_id,driver_binding_id,composition_id,direct_freights,collection_freights,shared_freights,unique_freights,direct_revenue,collection_revenue,shared_revenue,total_revenue,currency,tractor_capacity,trailer1_capacity,trailer2_capacity,total_capacity,capacity_unit,owner_document,ownership_code,contract_code,ownership_provenance,source_active,disposition,active,extracted_at,action,prior_observation_id,old_reference_date,old_branch_key)
 SELECT @receipt_id,@run_id,source_key,snapshot_id,state_id,reference_revision,reference_release_id,fleet_release_id,reference_date,competence_provenance,branch_key,branch_binding_id,tractor_binding_id,trailer1_binding_id,trailer2_binding_id,driver_binding_id,composition_id,direct_freights,collection_freights,shared_freights,unique_freights,direct_revenue,collection_revenue,shared_revenue,total_revenue,currency,tractor_capacity,trailer1_capacity,trailer2_capacity,total_capacity,capacity_unit,owner_document,ownership_code,contract_code,ownership_provenance,source_active,disposition,active,extracted_at,action,prior_observation_id,old_reference_date,old_branch_key FROM #decisions;
 INSERT mart.analytic_manifest_freight SELECT o.observation_id,f.dependency_id,f.freight_stage_id,f.term_id,f.direct_path,f.collection_path,f.source_active,f.amount,f.disposition
 FROM #freights f JOIN mart.analytic_manifest_observation o ON o.receipt_id=@receipt_id AND o.source_key=f.source_key WHERE f.dependency_id IS NOT NULL;
 INSERT mart.analytic_manifest_freight_path SELECT o.observation_id,p.path_kind,p.freight_key,p.direct_binding_id,p.mc_link_id,p.cf_link_id,p.crosswalk_binding_id
 FROM #paths p JOIN mart.analytic_manifest_observation o ON o.receipt_id=@receipt_id AND o.source_key=p.source_key;
 INSERT mart.analytic_manifest_current SELECT @run_id,source_key,observation_id,reference_date,branch_key,active FROM mart.analytic_manifest_observation WHERE receipt_id=@receipt_id AND action='INSERT';
 -- NOOP advances the provenance pointer only; immutable business values remain equal.
 UPDATE c SET observation_id=o.observation_id,reference_date=o.reference_date,branch_key=o.branch_key,active=o.active
 FROM mart.analytic_manifest_current c JOIN mart.analytic_manifest_observation o ON o.run_id=c.run_id AND o.source_key=c.source_key WHERE o.receipt_id=@receipt_id AND o.action IN('UPDATE','NOOP');

 INSERT mart.analytic_manifest_display
 SELECT o.observation_id,picks.numbers,unloading.names,regions.names,
 (SELECT m.snapshot_id,m.execution_id,m.source_rows,m.fresh_second,m.fresh_nano,m.[sequence_code],m.[mft_crn_psn_nickname],m.[created_at],m.[departured_at],m.[closed_at],m.[finished_at],m.[status],m.[mft_mfs_number],m.[mft_mfs_key],m.[mdfe_status],m.[mft_ape_name],m.[mft_man_name],m.[mft_vie_license_plate],m.[mft_vie_vee_name],m.[mft_vie_onr_name],m.[mft_mdr_iil_name],m.[vehicle_departure_km],m.[closing_km],m.[traveled_km],m.[invoices_count],m.[invoices_volumes],m.[invoices_weight],m.[total_taxed_weight],m.[total_cubic_volume],m.[invoices_value],m.[manifest_freights_total],m.[mft_pfs_pck_sequence_code],m.[mft_cat_cot_number],m.[daily_subtotal],m.[total_cost],m.[operational_expenses_total],m.[mft_a_t_inss_value],m.[mft_a_t_sest_senat_value],m.[mft_a_t_ir_value],m.[paying_total],m.[mft_uer_name],m.[mft_aoe_rer_name],m.[advance_subtotal],m.[toll_subtotal],m.[fleet_costs_subtotal],m.[mft_s_n_svs_sge_pyr_nickname],m.[mft_s_n_svs_sge_sse_name],m.[mobile_read_at],m.[km],m.[manual_km],m.[generate_mdfe],m.[monitoring_request],m.[delivery_manifest_items_count],m.[transfer_manifest_items_count],m.[pick_manifest_items_count],m.[dispatch_draft_manifest_items_count],m.[consolidation_manifest_items_count],m.[reverse_pick_manifest_items_count],m.[manifest_items_count],m.[finalized_manifest_items_count],m.[uniq_destinations_count],m.[contract_type],m.[mft_mdr_contract_type],m.[calculation_type],m.[cargo_type],m.[calculated_pick_count],m.[calculated_delivery_count],m.[calculated_dispatch_count],m.[calculated_consolidation_count],m.[calculated_reverse_pick_count],m.[freight_subtotal],m.[fuel_subtotal],m.[pick_subtotal],m.[delivery_subtotal],m.[dispatch_subtotal],m.[consolidation_subtotal],m.[reverse_pick_subtotal],m.[additionals_subtotal],m.[discounts_subtotal],m.[discount_value],m.[driver_services_total],m.[mft_aoe_comments],m.[mft_cat_cot_status],m.[mft_iks_id],m.[mft_s_n_sequence_code],m.[mft_s_n_starting_at],m.[mft_s_n_ending_at],m.[mft_tl1_license_plate],m.[mft_tl1_weight_capacity],m.[mft_tl2_license_plate],m.[mft_tl2_weight_capacity],m.[mft_vie_weight_capacity],m.[mft_vie_cubic_weight],m.[operational_comments],m.[closing_comments],m.[mft_mte_unloading_recipient_names],m.[mft_mte_delivery_region_names]
 FOR JSON PATH,INCLUDE_NULL_VALUES,WITHOUT_ARRAY_WRAPPER)
 FROM mart.analytic_manifest_observation o JOIN core.analytic_manifest_snapshot m ON m.snapshot_id=o.snapshot_id
 OUTER APPLY(SELECT STRING_AGG(CONVERT(NVARCHAR(4000),CASE WHEN c.alias_key LIKE N'INTEGER:%' THEN SUBSTRING(c.alias_key,9,128) ELSE c.alias_key END),N', ') WITHIN GROUP(ORDER BY c.source_key) numbers
 FROM(SELECT DISTINCT c.source_key,c.alias_key FROM #current_mc mc JOIN core.relational_lab_root c ON c.run_id=mc.run_id AND c.entity_name=N'coletas' AND c.source_key=mc.target_key
 WHERE mc.run_id=@rel AND mc.relation_kind='MC' AND mc.origin_key=o.source_key AND mc.active=1) c) picks
 OUTER APPLY(SELECT STRING_AGG(CONVERT(NVARCHAR(4000),j.value),N', ') WITHIN GROUP(ORDER BY CONVERT(INT,j.[key])) names FROM OPENJSON(m.mft_mte_unloading_recipient_names) j) unloading
 OUTER APPLY(SELECT STRING_AGG(CONVERT(NVARCHAR(4000),j.value),N', ') WITHIN GROUP(ORDER BY CONVERT(INT,j.[key])) names FROM OPENJSON(m.mft_mte_delivery_region_names) j) regions
 WHERE o.receipt_id=@receipt_id;
 SELECT candidates,inserts,updates,noops,ready,blocked FROM ctl.analytic_lab_materialization_receipt WHERE receipt_id=@receipt_id;

 END TRY
 BEGIN CATCH
  IF XACT_STATE()=1 ROLLBACK TRANSACTION ana_mat05;
  THROW;
 END CATCH;
END;
GO
