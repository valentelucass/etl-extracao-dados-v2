-- Read-only schema verification of the explicitly installed Coletas laboratory extension.
SET NOCOUNT ON;
IF DB_NAME()<>N'ETL_SISTEMA_V2_SHADOW' THROW 53190,N'EXACT_SHADOW_REQUIRED',1;
DECLARE @expected TABLE(schema_name SYSNAME,object_name SYSNAME,type CHAR(2));
INSERT @expected VALUES
    (N'ctl',N'execution_audit','U'),(N'ctl',N'page_audit','U'),
    (N'ctl',N'usp_audit_execution_started','P'),(N'ctl',N'usp_audit_page_read','P'),
    (N'ctl',N'usp_audit_execution_completed','P'),(N'ctl',N'usp_audit_execution_failed','P'),
    (N'stg',N'coleta_temporal_run','U'),(N'stg',N'coleta_temporal_observation','U'),
    (N'stg',N'coleta_temporal_binding','U'),(N'stg',N'coleta_exact_time','U'),
    (N'stg',N'usp_stage_coleta_temporal','P'),(N'stg',N'usp_complete_coleta_temporal','P'),
    (N'stg',N'usp_bind_coleta_temporal','P'),(N'stg',N'usp_stage_coleta_exact_time','P'),
    (N'stg',N'usp_complete_coleta_temporal_data_export','P'),
    (N'recon',N'vw_coleta_temporal_decision_v2','V'),(N'recon',N'usp_qualify_coleta_temporal_v2','P'),
    (N'core',N'coleta_temporal_laboratory','U'),(N'core',N'usp_consume_coleta_temporal_laboratory','P'),
    (N'recon',N'coleta_temporal_laboratory_application','U');
IF EXISTS(SELECT 1 FROM @expected e LEFT JOIN sys.objects o
    ON o.schema_id=SCHEMA_ID(e.schema_name) AND o.name COLLATE DATABASE_DEFAULT=e.object_name
        AND o.type COLLATE DATABASE_DEFAULT=e.type WHERE o.object_id IS NULL)
    THROW 53190,N'COL_TEMPORAL_SCHEMA_MISSING',1;
IF EXISTS(SELECT 1 FROM sys.columns c WHERE c.object_id IN(OBJECT_ID(N'stg.coleta_exact_time'),OBJECT_ID(N'stg.coleta_temporal_observation'),
    OBJECT_ID(N'core.coleta_temporal_laboratory')) AND ((c.name=N'epoch_second' AND TYPE_NAME(c.user_type_id)<>N'bigint')
        OR (c.name=N'nano' AND TYPE_NAME(c.user_type_id)<>N'int')))
    THROW 53190,N'COL_TEMPORAL_PRECISION_TYPE',1;
IF EXISTS(SELECT 1 FROM sys.check_constraints c JOIN @expected e
    ON c.parent_object_id=OBJECT_ID(e.schema_name+N'.'+e.object_name) WHERE c.is_disabled=1 OR c.is_not_trusted=1)
    OR EXISTS(SELECT 1 FROM sys.foreign_keys c JOIN @expected e
    ON c.parent_object_id=OBJECT_ID(e.schema_name+N'.'+e.object_name) WHERE c.is_disabled=1 OR c.is_not_trusted=1)
    THROW 53190,N'COL_TEMPORAL_UNTRUSTED_CONSTRAINT',1;
IF EXISTS(SELECT 1 FROM sys.database_permissions p JOIN @expected e
    ON p.major_id=OBJECT_ID(e.schema_name+N'.'+e.object_name) WHERE p.state IN('G','W'))
    THROW 53190,N'COL_TEMPORAL_UNEXPECTED_GRANT',1;
IF OBJECT_DEFINITION(OBJECT_ID(N'core.usp_consume_coleta_temporal_laboratory')) NOT LIKE N'%THROW 51428%'
    OR OBJECT_DEFINITION(OBJECT_ID(N'core.usp_consume_coleta_temporal_laboratory')) LIKE N'%INSERT INTO core.coleta (%'
    THROW 53190,N'COL_TEMPORAL_CONSUMER_CONTRACT',1;
SELECT N'COLETAS_TEMPORAL_SCHEMA_VERIFIED' result,COUNT_BIG(*) checked_objects FROM @expected;
