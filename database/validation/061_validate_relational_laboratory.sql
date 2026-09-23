-- Read-only structural validation, executed only after rollback-only Java/JAR sessions finish.
SET NOCOUNT ON;
IF DB_NAME()<>N'ETL_SISTEMA_V2_SHADOW' OR CONNECTIONPROPERTY('auth_scheme') NOT IN(N'NTLM',N'KERBEROS')
    THROW 53290,N'REL_VALIDATION_TARGET',1;
IF EXISTS(SELECT required.name FROM (VALUES
    (N'ctl.relational_lab_run'),(N'ctl.relational_lab_contract'),(N'ctl.relational_lab_capture'),
    (N'stg.relational_lab_root'),(N'core.relational_lab_root'),(N'stg.relational_lab_component'),
    (N'stg.relational_lab_binding'),(N'core.relational_lab_link'),(N'ctl.relational_lab_backlog'),
    (N'ctl.relational_lab_attempt'),(N'recon.relational_lab_receipt'),(N'recon.relational_lab_resolution'),
    (N'recon.relational_lab_partition'),(N'stg.relational_lab_mdfe')) required(name)
    WHERE OBJECT_ID(required.name,N'U') IS NULL)
    THROW 53291,N'REL_VALIDATION_TABLE_REQUIRED',1;
IF EXISTS(SELECT required.name FROM (VALUES
    (N'ctl.usp_relational_lab_lock'),(N'stg.usp_relational_lab_bind'),(N'stg.usp_capture_relational_laboratory'),
    (N'core.usp_resolve_relational_laboratory'),(N'ctl.usp_claim_relational_lab_backlog'),
    (N'ctl.usp_finish_relational_lab_attempt'),(N'recon.usp_relational_lab_status'),
    (N'recon.usp_plan_relational_lab_partitions'),(N'recon.usp_attach_relational_lab_capture'),
    (N'recon.usp_complete_relational_lab_partition'),(N'recon.usp_relational_lab_partition_status')) required(name)
    WHERE OBJECT_ID(required.name,N'P') IS NULL)
    THROW 53292,N'REL_VALIDATION_PROCEDURE_REQUIRED',1;
IF EXISTS(SELECT 1 FROM sys.check_constraints c JOIN sys.tables t ON t.object_id=c.parent_object_id
    WHERE t.name LIKE N'%relational_lab%' AND (c.is_disabled=1 OR c.is_not_trusted=1))
    OR EXISTS(SELECT 1 FROM sys.foreign_keys f JOIN sys.tables t ON t.object_id=f.parent_object_id
    WHERE t.name LIKE N'%relational_lab%' AND (f.is_disabled=1 OR f.is_not_trusted=1))
    THROW 53293,N'REL_VALIDATION_CONSTRAINT_DISABLED',1;
IF EXISTS(SELECT required.name FROM (VALUES(N'IX_relational_lab_capture_run'),(N'IX_relational_lab_component_lookup'),
    (N'IX_relational_lab_binding_origin'),(N'IX_relational_lab_link_consumer'),(N'IX_relational_lab_backlog_eligible')) required(name)
    WHERE NOT EXISTS(SELECT 1 FROM sys.indexes i WHERE i.name=required.name AND i.is_disabled=0))
    THROW 53294,N'REL_VALIDATION_INDEX_REQUIRED',1;
IF COL_LENGTH(N'core.relational_lab_root',N'epoch_second')<>8 OR COL_LENGTH(N'core.relational_lab_root',N'nano')<>4
    OR OBJECT_ID(N'stg.coleta_exact_time',N'U') IS NULL
    OR COL_LENGTH(N'stg.relational_lab_mdfe',N'mdfe_number')<>256
    THROW 53295,N'REL_VALIDATION_EXACT_TIME_REQUIRED',1;
IF OBJECT_ID(N'stg.fn_relational_lab_key_valid',N'FN') IS NULL
    OR stg.fn_relational_lab_key_valid(N'STRING:item ')<>0
    OR stg.fn_relational_lab_key_valid(N'INTEGER:0')<>1
    OR stg.fn_relational_lab_key_valid(N'INTEGER:01')<>0
    THROW 53295,N'REL_VALIDATION_KEY_ENCODING_REQUIRED',1;
IF OBJECT_DEFINITION(OBJECT_ID(N'stg.usp_capture_relational_laboratory')) NOT LIKE N'%THROW 51428%'
    OR OBJECT_DEFINITION(OBJECT_ID(N'core.usp_resolve_relational_laboratory')) LIKE N'%alias_key%'
    OR OBJECT_DEFINITION(OBJECT_ID(N'core.usp_resolve_relational_laboratory')) NOT LIKE N'%captured.business_date=b.target_date%'
    OR OBJECT_DEFINITION(OBJECT_ID(N'ctl.usp_claim_relational_lab_backlog')) NOT LIKE N'%UPDLOCK,READPAST,ROWLOCK%'
    OR OBJECT_DEFINITION(OBJECT_ID(N'recon.usp_relational_lab_status')) NOT LIKE N'%captured_entities%'
    THROW 53296,N'REL_VALIDATION_FINAL_PROCEDURE_REVISION',1;
IF EXISTS(SELECT 1 FROM sys.tables t JOIN sys.partitions p ON p.object_id=t.object_id AND p.index_id IN(0,1)
    WHERE t.name LIKE N'%relational_lab%' AND p.rows<>0)
    THROW 53297,N'REL_VALIDATION_SYNTHETIC_RESIDUE',1;
SELECT N'RELATIONAL_SCHEMA_VALIDATED_NO_SYNTHETIC_RESIDUE' result;
SELECT SCHEMA_NAME(t.schema_id)+N'.'+t.name table_name,SUM(p.rows) aggregate_rows
FROM sys.tables t JOIN sys.partitions p ON p.object_id=t.object_id AND p.index_id IN(0,1)
GROUP BY t.schema_id,t.name ORDER BY 1;
