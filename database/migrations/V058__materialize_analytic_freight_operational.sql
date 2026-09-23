-- ADR0050 ANA-02/11: canonical PE/CB, mutable partitions, immutable observations, explicit inputs.
SET ANSI_NULLS ON; SET QUOTED_IDENTIFIER ON;
GO
CREATE FUNCTION core.ufn_analytic_document_key(@value NVARCHAR(1024)) RETURNS NVARCHAR(1024) AS
BEGIN RETURN UPPER(NULLIF(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(LTRIM(RTRIM(@value)),N'.',N''),N'-',N''),N'/',N''),N' ',N''),CHAR(9),N''),N'')); END;
GO
-- Captured ISO text retains nanoseconds; truncate fractional digits, never round across a civil day.
CREATE FUNCTION core.ufn_analytic_iso_time(@raw NVARCHAR(1024)) RETURNS DATETIMEOFFSET(7) AS
BEGIN
 IF @raw IS NULL OR LEN(@raw)>64 OR LEN(@raw)<20 RETURN NULL;
 DECLARE @dot INT=CHARINDEX(N'.',@raw),@suffix INT;
 SET @suffix=CASE WHEN RIGHT(@raw,1)=N'Z' THEN LEN(@raw) WHEN SUBSTRING(@raw,LEN(@raw)-5,1) IN(N'+',N'-') THEN LEN(@raw)-5 ELSE 0 END;
 IF @suffix=0 RETURN NULL;
 IF @dot>0 AND @suffix-@dot-1>7 SET @raw=LEFT(@raw,@dot+7)+SUBSTRING(@raw,@suffix,7);
 RETURN TRY_CONVERT(DATETIMEOFFSET(7),@raw,127);
END;
GO
CREATE TABLE ctl.analytic_lab_materialization_receipt (
 receipt_id UNIQUEIDENTIFIER NOT NULL PRIMARY KEY,run_id UNIQUEIDENTIFIER NOT NULL REFERENCES ctl.analytic_lab_run(run_id),
 kind VARCHAR(8) NOT NULL,reference_revision INT NOT NULL,mode VARCHAR(16) NOT NULL,full_scope BIT NOT NULL,
 window_start DATE NOT NULL,window_end_exclusive DATE NOT NULL,candidates BIGINT NOT NULL,inserts BIGINT NOT NULL,
 updates BIGINT NOT NULL,noops BIGINT NOT NULL,ready BIGINT NOT NULL,blocked BIGINT NOT NULL,recorded_at DATETIME2(7) NOT NULL,
 CONSTRAINT CK_analytic_materialization_receipt CHECK(kind IN('MAT01','MAT02','MAT05') AND reference_revision BETWEEN 1 AND 100000
 AND mode IN('BOOTSTRAP','INCREMENTAL','BACKFILL','REPLAY') AND window_start<window_end_exclusive
 AND candidates=inserts+updates+noops AND candidates=ready+blocked AND inserts>=0 AND updates>=0 AND noops>=0 AND ready>=0 AND blocked>=0)
);
GO
CREATE TRIGGER ctl.trg_analytic_materialization_receipt ON ctl.analytic_lab_materialization_receipt AFTER UPDATE,DELETE
AS BEGIN SET NOCOUNT ON; IF EXISTS(SELECT 1 FROM deleted) THROW 53580,N'ANA_MATERIALIZATION_RECEIPT_IMMUTABLE',1; END;
GO
CREATE TABLE mart.analytic_freight_operational_observation (
 observation_id BIGINT IDENTITY NOT NULL PRIMARY KEY,
 receipt_id UNIQUEIDENTIFIER NOT NULL REFERENCES ctl.analytic_lab_materialization_receipt(receipt_id),
 run_id UNIQUEIDENTIFIER NOT NULL REFERENCES ctl.analytic_lab_run(run_id),
 dependency_id BIGINT NOT NULL REFERENCES core.expansion_lab_dependency(dependency_id),indicator CHAR(2) NOT NULL,
 freight_stage_id BIGINT NOT NULL,
 attribute_id BIGINT NULL,
 term_id BIGINT NULL,
 location_stage_id BIGINT NULL,
 source_key NVARCHAR(256) COLLATE Latin1_General_100_BIN2 NOT NULL,
 reference_revision INT NOT NULL,
 reference_release_id BIGINT NULL,
 reference_date DATE NULL,
 service_date DATE NULL,
 forecast_date DATE NULL,
 completion_date DATE NULL,
 forecast_provenance VARCHAR(32) NOT NULL,
 completion_provenance VARCHAR(32) NOT NULL,
 branch_key VARCHAR(64) COLLATE Latin1_General_100_BIN2 NULL,
 branch_binding_id BIGINT NULL,
 performance_branch_key VARCHAR(64) COLLATE Latin1_General_100_BIN2 NULL,
 performance_binding_id BIGINT NULL,
 performance_branch_provenance VARCHAR(32) NOT NULL,
 payer_document_key NVARCHAR(1024) COLLATE Latin1_General_100_BIN2 NULL,
 document_type NVARCHAR(32) NOT NULL,
 freight_type NVARCHAR(1024) NULL,
 status_raw NVARCHAR(1024) NULL,
 total_value DECIMAL(28,8) NULL,
 currency CHAR(3) NULL,
 unit VARCHAR(16) NULL,
 total_m3 DECIMAL(28,8) NULL,
 real_weight DECIMAL(28,8) NULL,
 taxed_weight DECIMAL(28,8) NULL,
 cubed_weight DECIMAL(28,8) NULL,
 volumes INT NULL,
 volume_provenance VARCHAR(32) NOT NULL,
 performance_days INT NULL,
 performance_status NVARCHAR(24) NOT NULL,
 performance_code TINYINT NOT NULL,
 is_cubed BIT NOT NULL,
 cancelled BIT NOT NULL,
 courtesy BIT NULL,
 source_active BIT NULL,
 has_document BIT NOT NULL,
 excluded_payer BIT NOT NULL,
 branch_document BIT NOT NULL,
 eligible BIT NOT NULL,
 eligible_with_value BIT NOT NULL,
 indicator_valid BIT NOT NULL,
 disposition VARCHAR(40) NOT NULL,
 extracted_at DATETIME2(7) NOT NULL,
 extraction_provenance VARCHAR(24) NOT NULL,
 action VARCHAR(8) NOT NULL,prior_observation_id BIGINT NULL REFERENCES mart.analytic_freight_operational_observation(observation_id),
 old_reference_date DATE NULL,old_branch_key VARCHAR(64) NULL,
 CONSTRAINT UQ_analytic_mat01_observation UNIQUE(receipt_id,dependency_id,indicator),
 CONSTRAINT UQ_analytic_mat01_lineage UNIQUE(run_id,dependency_id,indicator,observation_id),
 CONSTRAINT FK_analytic_mat01_freight FOREIGN KEY(freight_stage_id) REFERENCES stg.frete_record(stage_record_id),
 CONSTRAINT FK_analytic_mat01_attr FOREIGN KEY(attribute_id) REFERENCES stg.analytic_freight_attributes(observation_id),
 CONSTRAINT FK_analytic_mat01_terms FOREIGN KEY(term_id) REFERENCES stg.expansion_lab_freight_terms(term_id),
 CONSTRAINT FK_analytic_mat01_loc FOREIGN KEY(location_stage_id) REFERENCES stg.localizacao_carga_record(stage_record_id),
 CONSTRAINT FK_analytic_mat01_branch FOREIGN KEY(branch_binding_id) REFERENCES ref.analytic_lab_dimension_binding(binding_id),
 CONSTRAINT FK_analytic_mat01_performance_branch FOREIGN KEY(performance_binding_id) REFERENCES ref.analytic_lab_dimension_binding(binding_id),
 CONSTRAINT FK_analytic_mat01_reference FOREIGN KEY(reference_release_id) REFERENCES ref.reference_release(reference_release_id),
 CONSTRAINT CK_analytic_mat01_observation CHECK(indicator IN('PE','CB') AND action IN('INSERT','UPDATE','NOOP') AND performance_code BETWEEN 0 AND 2
 AND (indicator_valid=0 OR(disposition='READY' AND reference_date IS NOT NULL)))
);
CREATE INDEX IX_analytic_mat01_observation_receipt ON mart.analytic_freight_operational_observation(receipt_id,dependency_id,indicator);
GO
CREATE TRIGGER mart.trg_analytic_mat01_observation ON mart.analytic_freight_operational_observation AFTER UPDATE,DELETE
AS BEGIN SET NOCOUNT ON; IF EXISTS(SELECT 1 FROM deleted) THROW 53581,N'ANA_MAT01_OBSERVATION_IMMUTABLE',1; END;
GO
CREATE TABLE mart.analytic_freight_operational (
 run_id UNIQUEIDENTIFIER NOT NULL,dependency_id BIGINT NOT NULL,indicator CHAR(2) NOT NULL,observation_id BIGINT NOT NULL,
 reference_date DATE NULL,branch_key VARCHAR(64) NULL,indicator_valid BIT NOT NULL,
 CONSTRAINT PK_analytic_mat01 PRIMARY KEY(run_id,dependency_id,indicator),
 CONSTRAINT FK_analytic_mat01_current FOREIGN KEY(run_id,dependency_id,indicator,observation_id)
 REFERENCES mart.analytic_freight_operational_observation(run_id,dependency_id,indicator,observation_id)
);
CREATE INDEX IX_analytic_mat01_partition ON mart.analytic_freight_operational(run_id,reference_date,dependency_id,indicator)
 INCLUDE(observation_id,branch_key,indicator_valid);
GO
CREATE VIEW pub.analytic_lab_freight_operational AS
 SELECT o.* FROM mart.analytic_freight_operational c JOIN mart.analytic_freight_operational_observation o ON o.observation_id=c.observation_id
 WHERE c.indicator_valid=1;
GO
CREATE PROCEDURE mart.usp_materialize_analytic_freight @run_id UNIQUEIDENTIFIER,@receipt_id UNIQUEIDENTIFIER,
 @reference_revision INT,@mode VARCHAR(16),@full BIT,@start DATE,@end DATE,@now DATETIME2(7)
AS BEGIN
 SET NOCOUNT ON; SET XACT_ABORT ON;
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
 CASE WHEN JSON_VALUE(location.payload_json,'$.predicted_delivery_at') IS NOT NULL AND dates.location_forecast IS NULL THEN 1 ELSE 0 END invalid_location_forecast,
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
 CONVERT(DATE,core.ufn_analytic_iso_time(JSON_VALUE(location.payload_json,'$.predicted_delivery_at')) AT TIME ZONE @zone) location_forecast,
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
END;
GO
