-- Compila SHOWPLAN_XML dos cinco entrypoints V2-023 sem executá-los e reverte o baseline.

:setvar DatabaseName "ETL_SISTEMA_V2_SHADOW"
:On Error exit

IF DB_NAME() <> N'$(DatabaseName)'
    THROW 51660, N'O SHOWPLAN só aceita o banco local V2 de sombra autorizado.', 1;

SET NOCOUNT ON;
SET XACT_ABORT ON;
BEGIN TRANSACTION;

:r "..\transition\001_reset_historical_shadow_for_schema_foundation.sql"
:r "..\baseline\001_schema_foundation_baseline.sql"
:r "021_validate_observability_data_quality.sql"
GO

SET SHOWPLAN_XML ON;
GO

DECLARE @execution UNIQUEIDENTIFIER = '00000000-0000-0000-0000-000000000651';
DECLARE @fingerprint CHAR(64) = REPLICATE('a', 64);
DECLARE @correlation CHAR(64) = REPLICATE('b', 64);
DECLARE @now DATETIME2(3) = '2026-08-31T12:00:00.000';

EXEC recon.usp_evaluate_execution_data_quality
    @execution, N'synthetic-showplan-v1', @fingerprint;
EXEC recon.usp_observe_execution_data_quality @execution, 32;
EXEC recon.usp_record_execution_metric
    @execution, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
    0, 0, 0, 0, 0, 0, 0, 0, NULL, @now;
EXEC recon.usp_raise_observability_alert
    @correlation, 1, N'WARNING', N'SHOWPLAN_PROBE', N'operations-owner', 1, @now;
EXEC ctl.usp_observe_platform_health 3600;
GO

SET SHOWPLAN_XML OFF;
GO

IF @@TRANCOUNT <> 1 OR XACT_STATE() <> 1
    THROW 51661, N'O SHOWPLAN alterou o escopo transacional rollback-only.', 1;
ROLLBACK TRANSACTION;
PRINT N'OBSERVABILITY_DATA_QUALITY_SHOWPLAN_ROLLED_BACK';
