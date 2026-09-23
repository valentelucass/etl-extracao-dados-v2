-- Read-only final structure and aggregate snapshot. Compare preexisting counts externally; never assume global zero.
SET NOCOUNT ON;
IF DB_NAME()<>N'ETL_SISTEMA_V2_SHADOW' OR CONNECTIONPROPERTY('auth_scheme') NOT IN(N'NTLM',N'KERBEROS')
 THROW 53590,N'EXP_VALIDATION_TARGET',1;
IF EXISTS(SELECT required.name FROM(VALUES
 (N'ctl.expansion_lab_run'),(N'ctl.expansion_lab_capture'),(N'ctl.expansion_lab_contract'),
 (N'stg.expansion_lab_observation'),(N'stg.expansion_lab_cap'),(N'stg.expansion_lab_fat'),
 (N'stg.expansion_lab_inv'),(N'stg.expansion_lab_sin'),(N'stg.expansion_lab_array_item'),
 (N'core.expansion_lab_root'),(N'core.expansion_lab_component'),(N'core.expansion_lab_part'),
 (N'core.expansion_lab_history'),(N'ctl.expansion_lab_apply_receipt'),
 (N'ctl.expansion_lab_dependency_capture'),(N'stg.expansion_lab_dependency_time'),
 (N'stg.expansion_lab_dependency_observation'),(N'core.expansion_lab_dependency'),
 (N'core.expansion_lab_dependency_history'),(N'stg.expansion_lab_relation'),
 (N'core.expansion_lab_link'),(N'ctl.expansion_lab_queue'),(N'ctl.expansion_lab_queue_attempt'),
 (N'ref.expansion_lab_label'),(N'ctl.expansion_lab_reference_selection'),
 (N'ctl.expansion_lab_materialization_receipt'),(N'mart.expansion_lab_invoice'),
 (N'mart.expansion_lab_invoice_history'),(N'mart.expansion_lab_invoice_lineage'),
 (N'stg.expansion_lab_freight_terms'),(N'mart.expansion_lab_revenue'),
 (N'mart.expansion_lab_revenue_history'),(N'mart.expansion_lab_revenue_lineage'),
 (N'recon.expansion_lab_partition'),(N'recon.expansion_lab_step'),(N'recon.expansion_lab_failure')
 ) required(name) WHERE OBJECT_ID(required.name,N'U') IS NULL)
 THROW 53591,N'EXP_VALIDATION_TABLE_REQUIRED',1;
IF EXISTS(SELECT required.name FROM(VALUES
 (N'core.usp_apply_expansion_laboratory'),(N'ctl.usp_seal_expansion_lab_capture'),
 (N'core.usp_apply_expansion_dependencies'),(N'ctl.usp_expansion_lab_lock'),
 (N'stg.usp_bind_expansion_relations'),(N'core.usp_resolve_expansion_relations'),
 (N'ctl.usp_claim_expansion_queue'),(N'ctl.usp_finish_expansion_queue'),
 (N'ref.usp_import_expansion_references'),(N'mart.usp_materialize_expansion_invoices'),
 (N'mart.usp_materialize_expansion_revenue'),(N'recon.usp_plan_expansion_lab'),
 (N'recon.usp_attach_expansion_lab_step'),(N'recon.usp_complete_expansion_lab_partition')
 ) required(name) WHERE OBJECT_ID(required.name,N'P') IS NULL)
 THROW 53592,N'EXP_VALIDATION_PROCEDURE_REQUIRED',1;
IF EXISTS(SELECT required.name FROM(VALUES(N'pub.ufn_expansion_cap'),(N'pub.ufn_expansion_fat'),
 (N'pub.ufn_expansion_inv'),(N'pub.ufn_expansion_sin'),(N'core.ufn_expansion_freight_terms'),
 (N'ref.ufn_expansion_reference')) required(name) WHERE OBJECT_ID(required.name,N'IF') IS NULL)
 THROW 53593,N'EXP_VALIDATION_QUERY_REQUIRED',1;
IF EXISTS(SELECT 1 FROM sys.check_constraints c JOIN sys.tables t ON t.object_id=c.parent_object_id
 WHERE t.name LIKE N'%expansion_lab%' AND(c.is_disabled=1 OR c.is_not_trusted=1))
 OR EXISTS(SELECT 1 FROM sys.foreign_keys c JOIN sys.tables t ON t.object_id=c.parent_object_id
 WHERE t.name LIKE N'%expansion_lab%' AND(c.is_disabled=1 OR c.is_not_trusted=1))
 OR EXISTS(SELECT 1 FROM sys.triggers WHERE name LIKE N'%expansion%' AND is_disabled=1)
 THROW 53594,N'EXP_VALIDATION_CONSTRAINT_DISABLED',1;
IF(SELECT COUNT_BIG(*) FROM sys.columns c JOIN sys.tables t ON t.object_id=c.object_id
 WHERE SCHEMA_NAME(t.schema_id)=N'stg' AND t.name IN(N'expansion_lab_cap',N'expansion_lab_fat',N'expansion_lab_inv',N'expansion_lab_sin')
 AND c.name LIKE N'%[_]p')<>151 THROW 53595,N'EXP_VALIDATION_FIELD_PRESENCE_COUNT',1;
IF COL_LENGTH(N'stg.expansion_lab_observation',N'fresh_second')<>8
 OR COL_LENGTH(N'stg.expansion_lab_observation',N'fresh_nano')<>4
 OR COL_LENGTH(N'stg.expansion_lab_sin',N'occurrence_at_time')<>8
 OR COL_LENGTH(N'stg.expansion_lab_array_item',N'text_value')<>512
 THROW 53595,N'EXP_VALIDATION_EXACT_TYPES',1;
IF EXISTS(SELECT required.name FROM(VALUES(N'rcfdc'),(N'rctac'),(N'rctrc')) required(name)
 WHERE NOT EXISTS(SELECT 1 FROM sys.check_constraints
 WHERE parent_object_id=OBJECT_ID(N'stg.expansion_lab_array_item')
 AND name=N'CK_expansion_array_bound' AND definition LIKE N'%'+required.name+N'%'))
 THROW 53595,N'EXP_VALIDATION_SINISTRO_ARRAYS_REQUIRED',1;
IF OBJECT_DEFINITION(OBJECT_ID(N'core.usp_apply_expansion_laboratory')) NOT LIKE N'%MIN(CASE WHEN c.active=1 THEN o.root_amount END)%'
 OR OBJECT_DEFINITION(OBJECT_ID(N'mart.usp_materialize_expansion_revenue')) NOT LIKE N'%CONVERT(VARBINARY(256),s.branch_code)%'
 OR OBJECT_DEFINITION(OBJECT_ID(N'ctl.usp_claim_expansion_queue')) NOT LIKE N'%UPDLOCK,READPAST,ROWLOCK%'
 THROW 53596,N'EXP_VALIDATION_FINAL_REVISION_REQUIRED',1;
SELECT N'EXPANSION_SCHEMA_VALIDATED_AGGREGATES_FOLLOW' result;
SELECT SCHEMA_NAME(t.schema_id)+N'.'+t.name table_name,SUM(p.rows) aggregate_rows
FROM sys.tables t JOIN sys.partitions p ON p.object_id=t.object_id AND p.index_id IN(0,1)
GROUP BY t.schema_id,t.name ORDER BY 1;
