-- Compila SHOWPLAN_XML do lifecycle V2-045a sem executar seus entrypoints e reverte o baseline.

:setvar DatabaseName "ETL_SISTEMA_V2_SHADOW"
:On Error exit

IF DB_NAME() <> N'$(DatabaseName)'
BEGIN
    THROW 51650, N'O SHOWPLAN só aceita o banco local V2 de sombra autorizado.', 1;
END;

SET NOCOUNT ON;
SET XACT_ABORT ON;
BEGIN TRANSACTION;

:r "..\transition\001_reset_historical_shadow_for_schema_foundation.sql"
:r "..\baseline\001_schema_foundation_baseline.sql"
:r "012_validate_staging_lifecycle.sql"
GO

SET SHOWPLAN_XML ON;
GO

DECLARE @policy UNIQUEIDENTIFIER = '00000000-0000-0000-0000-000000000570';
DECLARE @execution UNIQUEIDENTIFIER = '00000000-0000-0000-0000-000000000571';
DECLARE @hold UNIQUEIDENTIFIER = '00000000-0000-0000-0000-000000000572';
DECLARE @plan UNIQUEIDENTIFIER = '00000000-0000-0000-0000-000000000573';
DECLARE @restore UNIQUEIDENTIFIER = '00000000-0000-0000-0000-000000000574';
DECLARE @fingerprint CHAR(64) = REPLICATE('a', 64);
DECLARE @other_fingerprint CHAR(64) = REPLICATE('b', 64);
DECLARE @content_root BINARY(32);
DECLARE @now DATETIME2(3) = '2026-08-31T12:00:00.000';

EXEC ctl.usp_approve_staging_retention_policy
    @policy, N'LOCAL_SHADOW', N'SYNTHETIC_SHOWPLAN_SOURCE', N'SYNTHETIC_TENANT',
    N'SYNTHETIC_ENTITY', N'NON_PUBLISHED_TERMINAL', N'showplan-v1', @fingerprint, 1,
    @fingerprint, N'data-owner', @other_fingerprint, N'compliance';
EXEC ctl.usp_revoke_staging_retention_policy
    @policy, N'showplan-v1', @fingerprint, @other_fingerprint, N'compliance';
EXEC ctl.usp_place_staging_legal_hold
    @hold, @execution, N'LEGAL_REVIEW', @fingerprint, N'compliance';
EXEC ctl.usp_release_staging_legal_hold
    @hold, @other_fingerprint, N'compliance';

EXEC stg.usp_plan_staging_lifecycle_at
    @plan, @policy, N'showplan-v1', @fingerprint, N'LOCAL_SHADOW',
    N'SYNTHETIC_SHOWPLAN_SOURCE', N'SYNTHETIC_TENANT', N'SYNTHETIC_ENTITY',
    NULL, NULL, 10, 20, 100, 100, 100, 200, 1000, 1048576, @now;
EXEC stg.usp_plan_staging_lifecycle
    @plan, @policy, N'showplan-v1', @fingerprint, N'LOCAL_SHADOW',
    N'SYNTHETIC_SHOWPLAN_SOURCE', N'SYNTHETIC_TENANT', N'SYNTHETIC_ENTITY',
    NULL, NULL, 10, 20, 100, 100, 100, 200, 1000, 1048576;
EXEC recon.usp_compute_live_staging_content_root_internal
    @plan, 200, @content_root OUTPUT;
EXEC recon.usp_compute_archive_content_root_internal
    @plan, 200, @content_root OUTPUT;
EXEC recon.usp_verify_staging_archive_internal @plan;
EXEC recon.usp_verify_staging_restore_internal @restore;
EXEC stg.usp_archive_staging_lifecycle @plan, N'showplan-v1', @fingerprint;
EXEC stg.usp_purge_staging_lifecycle
    @plan, N'showplan-v1', @fingerprint, N'archive-manifest-v3', @other_fingerprint;
EXEC recon.usp_restore_staging_archive
    @restore, @plan, @execution, N'archive-manifest-v3', @other_fingerprint,
    100, 100, N'SHOWPLAN_VALIDATION';
GO

SET SHOWPLAN_XML OFF;
GO

IF @@TRANCOUNT <> 1 OR XACT_STATE() <> 1
    THROW 51651, N'O SHOWPLAN alterou o escopo transacional rollback-only.', 1;
ROLLBACK TRANSACTION;
PRINT N'STAGING_LIFECYCLE_SHOWPLAN_ROLLED_BACK';
