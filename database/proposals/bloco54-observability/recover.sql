-- Compensation removes exactly the three new grants. Schema/audit/data remain.
:On Error exit
:r "verify.sql"
GO
BEGIN TRANSACTION;
DECLARE @machine NVARCHAR(128)=CONVERT(NVARCHAR(128),SERVERPROPERTY('MachineName'));
DECLARE @exec SYSNAME=@machine+N'\etl_v2_exec',@view SYSNAME=@machine+N'\etl_v2_view',@sql NVARCHAR(MAX);
SET @sql=N'REVOKE EXECUTE ON OBJECT::ctl.usp_runtime_observation FROM '+QUOTENAME(@exec)+N'; REVOKE EXECUTE ON OBJECT::recon.usp_runtime_raise_alert FROM '+QUOTENAME(@exec)+N'; REVOKE EXECUTE ON OBJECT::ctl.usp_runtime_observation FROM '+QUOTENAME(@view)+N';';
EXEC sys.sp_executesql @sql;
GO
:r "..\..\validation\053_validate_local_runtime_provisioning.sql"
GO
COMMIT TRANSACTION;
PRINT N'B54_ORIGINAL_22_GRANTS_RESTORED_NO_DATA_CLEANUP';