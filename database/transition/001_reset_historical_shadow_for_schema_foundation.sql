-- Transição local única da prova V001-V003 para a fundação Flyway V2.
-- O script é fail-closed: aceita somente o alvo local histórico vazio, sem objetos de domínio.
-- Ele pode participar de uma transação externa; nesse caso, não confirma nem reverte a transação.

SET XACT_ABORT ON;
SET NOCOUNT ON;

DECLARE @owns_transaction BIT = 0;

IF DB_NAME() <> N'ETL_SISTEMA_V2_SHADOW'
BEGIN
    THROW 51210, N'A transição aceita somente o banco local ETL_SISTEMA_V2_SHADOW.', 1;
END;

IF OBJECT_ID(N'dbo.flyway_schema_history', N'U') IS NOT NULL
BEGIN
    THROW 51211, N'O alvo já possui histórico Flyway e não é a prova histórica esperada.', 1;
END;

IF SCHEMA_ID(N'ctl') IS NULL
   OR SCHEMA_ID(N'stg') IS NULL
   OR SCHEMA_ID(N'shadow') IS NULL
   OR SCHEMA_ID(N'recon') IS NULL
   OR SCHEMA_ID(N'core') IS NOT NULL
   OR SCHEMA_ID(N'ref') IS NOT NULL
   OR SCHEMA_ID(N'mart') IS NOT NULL
   OR SCHEMA_ID(N'pub') IS NOT NULL
BEGIN
    THROW 51212, N'O alvo não possui exatamente a topologia histórica esperada.', 1;
END;

IF EXISTS (
    SELECT 1
    FROM sys.objects AS object_definition
    INNER JOIN sys.schemas AS schema_definition
        ON schema_definition.schema_id = object_definition.schema_id
    WHERE object_definition.is_ms_shipped = 0
      AND object_definition.type IN (N'U', N'P', N'V', N'FN', N'IF', N'TF', N'TR')
      AND NOT (
          schema_definition.name = N'ctl'
          AND object_definition.name IN (
              N'execution_audit',
              N'page_audit',
              N'source_watermark',
              N'usp_audit_execution_started',
              N'usp_audit_page_read',
              N'usp_audit_execution_completed',
              N'usp_audit_execution_failed'
          )
      )
)
BEGIN
    THROW 51213, N'O alvo contém objeto não pertencente à prova histórica e não será alterado.', 1;
END;

IF EXISTS (
    SELECT 1
    FROM sys.database_principals
    WHERE type = N'R'
      AND name LIKE N'v2[_]%'
      AND name <> N'v2_shadow_runtime'
)
BEGIN
    THROW 51214, N'O alvo contém role V2 não pertencente à prova histórica.', 1;
END;

IF (SELECT COUNT_BIG(*) FROM ctl.execution_audit) <> 0
   OR (SELECT COUNT_BIG(*) FROM ctl.page_audit) <> 0
   OR (SELECT COUNT_BIG(*) FROM ctl.source_watermark) <> 0
BEGIN
    THROW 51215, N'A prova histórica contém dados e não pode ser reconstruída automaticamente.', 1;
END;

BEGIN TRY
    IF @@TRANCOUNT = 0
    BEGIN
        BEGIN TRANSACTION;
        SET @owns_transaction = 1;
    END;

    DROP PROCEDURE ctl.usp_audit_execution_completed;
    DROP PROCEDURE ctl.usp_audit_execution_failed;
    DROP PROCEDURE ctl.usp_audit_execution_started;
    DROP PROCEDURE ctl.usp_audit_page_read;

    DROP TABLE ctl.page_audit;
    DROP TABLE ctl.source_watermark;
    DROP TABLE ctl.execution_audit;

    DROP ROLE v2_shadow_runtime;
    DROP SCHEMA shadow;

    IF @owns_transaction = 1
    BEGIN
        COMMIT TRANSACTION;
    END;
END TRY
BEGIN CATCH
    IF @owns_transaction = 1 AND XACT_STATE() <> 0
    BEGIN
        ROLLBACK TRANSACTION;
    END;
    THROW;
END CATCH;
