-- Uma revisão que atingiu o limite do lote continua pendente no checkpoint.
-- O SQL conhece o motivo gravado pelo recebedor para o próprio run_id.
IF DB_NAME() <> N'ETL_SISTEMA_V2' THROW 52020,'REFRESH_AUDIT_PRODUCTION_DATABASE_REQUIRED',1;
DECLARE @proc nvarchar(max) = OBJECT_DEFINITION(OBJECT_ID(N'ctl.usp_store_dashboard_refresh_audit',N'P'));
DECLARE @old nvarchar(200) = N'(r.run_state=''FAILED'' AND (@passed=1 OR i.reported_rows IS NOT NULL))';
IF @proc IS NULL OR CHARINDEX(@old,@proc)=0
   OR CHARINDEX(N'CREATE PROCEDURE ctl.usp_store_dashboard_refresh_audit',@proc)=0
    THROW 52022,'Definicao de auditoria anterior inesperada.',1;
SET @proc = REPLACE(@proc, N'CREATE PROCEDURE ctl.usp_store_dashboard_refresh_audit',
                           N'CREATE OR ALTER PROCEDURE ctl.usp_store_dashboard_refresh_audit');
SET @proc = REPLACE(@proc, @old, N'(r.run_state=''FAILED'' AND (i.reported_rows IS NOT NULL
              OR (@passed=1 AND ISNULL(r.failure_code,'''')<>''REFRESH_BATCH_DEFERRED'')))');
EXEC sys.sp_executesql @proc;
