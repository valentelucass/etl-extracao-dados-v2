-- Operational installation evidence only. This is not a business publication or readiness gate.
SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;
SET XACT_ABORT ON;
IF DB_NAME() <> N'ETL_SISTEMA_V2'
    THROW 51820, N'INSTALLATION_STATUS_EXACT_TARGET_REQUIRED', 1;
IF OBJECT_ID(N'ctl.production_installation_step', N'U') IS NULL
    OR OBJECT_ID(N'pub.v2_installation_status') IS NOT NULL
    THROW 51821, N'INSTALLATION_STATUS_FOUNDATION_REQUIRED_OR_ALREADY_INSTALLED', 1;
GO
CREATE VIEW pub.v2_installation_status
AS
SELECT step_id, source_name, source_sha256,
    CONVERT(DATETIMEOFFSET(7), SWITCHOFFSET(installed_at, '+00:00')) AS installed_at_utc
FROM ctl.production_installation_step;
GO
