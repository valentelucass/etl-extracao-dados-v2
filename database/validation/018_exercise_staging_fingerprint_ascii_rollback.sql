-- Exercício negativo, sintético e rollback-only da gramática ASCII dos fingerprints V003-V005.

:setvar DatabaseName "ETL_SISTEMA_V2_SHADOW"
:On Error exit

IF DB_NAME() <> N'$(DatabaseName)'
BEGIN
    THROW 51660, N'O exercício só aceita o banco local V2 de sombra autorizado.', 1;
END;

SET NOCOUNT ON;
SET XACT_ABORT ON;
BEGIN TRANSACTION;

:r "..\transition\001_reset_historical_shadow_for_schema_foundation.sql"
:r "..\baseline\001_schema_foundation_baseline.sql"
:r "012_validate_staging_lifecycle.sql"
GO

DECLARE @non_ascii_fingerprint NVARCHAR(64) =
    REPLICATE(N'a', 62) + NCHAR(0xFF11) + NCHAR(0xFF21);
DECLARE @cycle_started_at_utc DATETIME2(3) = SYSUTCDATETIME();
DECLARE @cycle_error INT = NULL;
BEGIN TRY
    EXEC ctl.usp_control_plane_start_cycle
        '00000000-0000-0000-0000-000000000579', N'ascii-cycle-v1',
        @non_ascii_fingerprint, @cycle_started_at_utc;
END TRY
BEGIN CATCH
    SET @cycle_error = ERROR_NUMBER();
END CATCH;

IF XACT_STATE() <> 0
    ROLLBACK TRANSACTION;
IF @cycle_error IS NULL OR @cycle_error <> 51302
    THROW 51661, N'Control plane aceitou homoglyph Unicode como fingerprint ASCII.', 1;
IF XACT_STATE() <> 0 OR @@TRANCOUNT <> 0
    THROW 51662, N'O rollback do fingerprint V003 não encerrou o escopo sintético.', 1;
GO

BEGIN TRANSACTION;
:r "..\transition\001_reset_historical_shadow_for_schema_foundation.sql"
:r "..\baseline\001_schema_foundation_baseline.sql"
GO

DECLARE @non_ascii_fingerprint NVARCHAR(64) =
    REPLICATE(N'a', 62) + NCHAR(0xFF11) + NCHAR(0xFF21);
DECLARE @ascii_fingerprint NVARCHAR(64) = REPLICATE(N'b', 64);
DECLARE @source_freshness_at_utc DATETIME2(3) = SYSUTCDATETIME();
DECLARE @staging_error INT = NULL;
BEGIN TRY
    EXEC stg.usp_stage_record
        '00000000-0000-0000-0000-000000000580', 1, 1, N'ascii-key', N'row-v1',
        @non_ascii_fingerprint, N'presence-v1', @ascii_fingerprint, NULL,
        N'VALID', NULL, @source_freshness_at_utc;
END TRY
BEGIN CATCH
    SET @staging_error = ERROR_NUMBER();
END CATCH;

IF XACT_STATE() <> 0
    ROLLBACK TRANSACTION;
IF @staging_error IS NULL OR @staging_error <> 51400
    THROW 51663, N'Kernel de staging aceitou homoglyph Unicode como fingerprint ASCII.', 1;
IF XACT_STATE() <> 0 OR @@TRANCOUNT <> 0
    THROW 51664, N'O rollback do fingerprint V004 não encerrou o escopo sintético.', 1;
GO

BEGIN TRANSACTION;
:r "..\transition\001_reset_historical_shadow_for_schema_foundation.sql"
:r "..\baseline\001_schema_foundation_baseline.sql"
GO

DECLARE @non_ascii_fingerprint NVARCHAR(64) =
    REPLICATE(N'a', 62) + NCHAR(0xFF11) + NCHAR(0xFF21);
DECLARE @owner_evidence_fingerprint NVARCHAR(64) = REPLICATE(N'b', 64);
DECLARE @compliance_evidence_fingerprint NVARCHAR(64) = REPLICATE(N'c', 64);
DECLARE @fingerprint_error INT = NULL;
BEGIN TRY
    EXEC ctl.usp_approve_staging_retention_policy
        '00000000-0000-0000-0000-000000000580',
        N'LOCAL_SHADOW', N'SYNTHETIC_ASCII_SOURCE', N'SYNTHETIC_TENANT',
        N'SYNTHETIC_ENTITY', N'NON_PUBLISHED_TERMINAL', N'ascii-policy-v1',
        @non_ascii_fingerprint, 1, @owner_evidence_fingerprint, N'data-owner',
        @compliance_evidence_fingerprint, N'compliance';
END TRY
BEGIN CATCH
    SET @fingerprint_error = ERROR_NUMBER();
END CATCH;

IF XACT_STATE() <> 0
    ROLLBACK TRANSACTION;
IF @fingerprint_error IS NULL OR @fingerprint_error <> 51503
    THROW 51665, N'Lifecycle aceitou homoglyph Unicode como fingerprint ASCII.', 1;
IF XACT_STATE() <> 0 OR @@TRANCOUNT <> 0
    THROW 51666, N'O rollback do fingerprint V005 não encerrou o escopo sintético.', 1;

PRINT N'Homoglyphs Unicode bloqueados em V003, V004 e V005; estados revertidos.';
