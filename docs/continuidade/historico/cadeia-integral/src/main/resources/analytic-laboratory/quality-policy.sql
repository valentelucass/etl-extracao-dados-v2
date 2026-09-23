SET NOCOUNT ON; SET XACT_ABORT ON;
DECLARE @run UNIQUEIDENTIFIER=?,@entity NVARCHAR(128)=?,@mode NVARCHAR(16)=?;
IF @@TRANCOUNT=0 OR DB_NAME()<>N'ETL_SISTEMA_V2_SHADOW'
 OR NOT EXISTS(SELECT 1 FROM ctl.analytic_lab_run WHERE run_id=@run)
 OR @entity NOT IN(N'usuarios',N'cotacoes') OR @mode NOT IN(N'BOOTSTRAP',N'INCREMENTAL',N'BACKFILL',N'REPLAY')
 OR (@entity=N'usuarios' AND @mode NOT IN(N'BACKFILL',N'REPLAY'))
 THROW 53571,N'ANA_QUALITY_SCOPE',1;
DECLARE @version NVARCHAR(128)=CONCAT(N'analytic-',@entity,N'-',LOWER(@mode),N'-',CONVERT(VARCHAR(36),@run));
IF EXISTS(SELECT 1 FROM ctl.data_quality_policy WHERE policy_version=@version)
BEGIN SELECT policy_version,policy_fingerprint FROM ctl.data_quality_policy WHERE policy_version=@version; RETURN; END;
DECLARE @scope CHAR(64)=LOWER(CONVERT(CHAR(64),HASHBYTES('SHA2_256',CONVERT(VARBINARY(MAX),CONCAT(
 N'dq-scope-v1|24:LOCAL_SHADOW|16:LOCAL_V2|16:LOCAL_V2|',DATALENGTH(@entity),N':',@entity,N'|',DATALENGTH(@mode),N':',@mode))),2));
DECLARE @now DATETIME2(3)=SYSUTCDATETIME(),@owner NVARCHAR(64)=N'analytic-laboratory-owner',
 @retention NVARCHAR(128)=N'analytic-rollback-only-v1',@fingerprint CHAR(64);
SET @fingerprint=LOWER(CONVERT(CHAR(64),HASHBYTES('SHA2_256',CONVERT(VARBINARY(MAX),CONCAT(
 N'dq-policy-v1|',DATALENGTH(@version),N':',@version,N'|',@scope,N'|4|3600|',
 DATALENGTH(@owner),N':',@owner,N'|',DATALENGTH(@owner),N':',@owner,N'|',
 DATALENGTH(@retention),N':',@retention,N'|',DATALENGTH(@owner),N':',@owner,N'|',CONVERT(NVARCHAR(33),@now,126),N'|',
 N'1:COUNT_EQUATION:0:0:',DATALENGTH(@owner),N':',@owner,N'|',
 N'2:PAGE_TERMINALITY:0:0:',DATALENGTH(@owner),N':',@owner,N'|',
 N'3:PROMOTION_RECONCILIATION:0:0:',DATALENGTH(@owner),N':',@owner,N'|',
 N'4:QUARANTINE_SLA:0:0:',DATALENGTH(@owner),N':',@owner))),2));
INSERT ctl.data_quality_policy VALUES(@version,@fingerprint,@scope,4,3600,@owner,@owner,@retention,@owner,N'RATIFIED',@now,@now);
INSERT ctl.data_quality_check_policy
 SELECT @version,@fingerprint,ordinal,code,0,0,@owner
 FROM(VALUES(1,N'COUNT_EQUATION'),(2,N'PAGE_TERMINALITY'),(3,N'PROMOTION_RECONCILIATION'),(4,N'QUARANTINE_SLA')) v(ordinal,code);
SELECT @version,@fingerprint;
