-- B55 additive technical evidence. No principal, grant, reference content or production output.
SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;
SET XACT_ABORT ON;
GO
CREATE TABLE ctl.runtime_cotacao_reference (
 execution_id UNIQUEIDENTIFIER NOT NULL CONSTRAINT PK_runtime_cotacao_reference PRIMARY KEY,
 reference_release_id BIGINT NOT NULL,
 reference_fingerprint CHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL,
 bound_at_utc DATETIME2(3) NOT NULL,
 CONSTRAINT FK_runtime_cotacao_reference_attempt FOREIGN KEY(execution_id) REFERENCES ctl.execution_attempt(execution_id),
 CONSTRAINT FK_runtime_cotacao_reference_release FOREIGN KEY(reference_release_id) REFERENCES ref.reference_release(reference_release_id)
);
GO
CREATE TABLE recon.runtime_vertical_output (
 output_id BIGINT IDENTITY NOT NULL CONSTRAINT PK_runtime_vertical_output PRIMARY KEY,
 execution_id UNIQUEIDENTIFIER NOT NULL,
 entity_name NVARCHAR(128) COLLATE Latin1_General_100_BIN2 NOT NULL,
 row_kind VARCHAR(8) COLLATE Latin1_General_100_BIN2 NOT NULL,
 source_key NVARCHAR(256) COLLATE Latin1_General_100_BIN2 NOT NULL,
 child_key NVARCHAR(256) COLLATE Latin1_General_100_BIN2 NOT NULL,
 projection_json NVARCHAR(MAX) NOT NULL,
 presence_json NVARCHAR(MAX) NOT NULL,
 reference_release_id BIGINT NULL,
 captured_at_utc DATETIME2(3) NOT NULL,
 CONSTRAINT UQ_runtime_vertical_output UNIQUE NONCLUSTERED(execution_id,row_kind,source_key,child_key),
 CONSTRAINT FK_runtime_vertical_output_publication FOREIGN KEY(execution_id) REFERENCES ctl.execution_attempt(execution_id),
 CONSTRAINT CK_runtime_vertical_output_kind CHECK(row_kind IN('ROOT','PICK','MDFE')),
 CONSTRAINT CK_runtime_vertical_output_json CHECK(ISJSON(projection_json)=1 AND ISJSON(presence_json)=1)
);
GO
ALTER TABLE core.manifesto_pick ADD freshness_at_utc DATETIME2(3) NULL;
ALTER TABLE core.manifesto_mdfe ADD freshness_at_utc DATETIME2(3) NULL;
ALTER TABLE stg.manifesto_pick_candidate ADD freshness_at_utc DATETIME2(3) NULL;
ALTER TABLE stg.manifesto_mdfe_candidate ADD freshness_at_utc DATETIME2(3) NULL;
GO
CREATE TRIGGER ctl.trg_runtime_cotacao_reference_immutable ON ctl.runtime_cotacao_reference INSTEAD OF UPDATE,DELETE AS
BEGIN THROW 52801,N'RUNTIME_REFERENCE_IMMUTABLE',1; END;
GO
CREATE TRIGGER recon.trg_runtime_vertical_output_immutable ON recon.runtime_vertical_output INSTEAD OF UPDATE,DELETE AS
BEGIN THROW 52802,N'RUNTIME_OUTPUT_IMMUTABLE',1; END;
GO
CREATE OR ALTER FUNCTION ctl.fn_runtime_tariff_valid(@execution_id UNIQUEIDENTIFIER,@reference_release_id BIGINT)
RETURNS BIT AS
BEGIN
 IF @reference_release_id IS NULL OR @reference_release_id<1 RETURN 0;
 IF NOT EXISTS(SELECT 1 FROM ref.reference_release r
   JOIN ref.reference_release_ratification rat ON rat.reference_release_id=r.reference_release_id AND rat.activation_scope=N'SHADOW'
   WHERE r.reference_release_id=@reference_release_id AND r.family_code=N'QUOTE_TARIFF'
   AND r.scope_code=N'LOCAL_SHADOW/LOCAL_V2/LOCAL_V2/cotacoes'
   AND NOT EXISTS(SELECT 1 FROM ref.reference_release_revocation rev
     WHERE rev.reference_release_id=r.reference_release_id AND rev.activation_scope=N'SHADOW')) RETURN 0;
 IF EXISTS(SELECT 1 FROM ctl.runtime_cotacao_reference b JOIN ref.reference_release r ON r.reference_release_id=b.reference_release_id
   WHERE b.execution_id=@execution_id AND (b.reference_release_id<>@reference_release_id OR b.reference_fingerprint<>r.source_fingerprint)) RETURN 0;
 IF IS_SRVROLEMEMBER(N'sysadmin')=1 OR IS_MEMBER(N'db_owner')=1 RETURN 1;
 DECLARE @actual NVARCHAR(MAX)=CONCAT(N'{"execution":"',LOWER(CONVERT(NVARCHAR(36),@execution_id)),
   N'","entity":"cotacoes","workload":"cotacoes","mode":"BACKFILL","referenceReleaseId":"',
   CONVERT(NVARCHAR(20),@reference_release_id),N'"}');
 RETURN ctl.fn_runtime_consumed_scope(@actual,0,NULL);
END;
GO
