-- Reuse the V2-035a OWNED_FLEET tables; only the scoped laboratory import and selection are new.
SET ANSI_NULLS ON; SET QUOTED_IDENTIFIER ON;
GO
CREATE TABLE ctl.analytic_lab_fleet_selection(
 run_id UNIQUEIDENTIFIER NOT NULL REFERENCES ctl.analytic_lab_run(run_id),revision INT NOT NULL,
 valid_from DATE NOT NULL,valid_to_exclusive DATE NOT NULL,reference_release_id BIGINT NOT NULL REFERENCES ref.reference_release(reference_release_id),
 CONSTRAINT PK_analytic_fleet_selection PRIMARY KEY(run_id,revision,valid_from),
 CONSTRAINT CK_analytic_fleet_selection CHECK(revision BETWEEN 1 AND 100000 AND valid_from<valid_to_exclusive)
);
GO
CREATE TRIGGER ctl.trg_analytic_fleet_selection ON ctl.analytic_lab_fleet_selection AFTER INSERT,UPDATE,DELETE
AS BEGIN SET NOCOUNT ON;
 IF EXISTS(SELECT 1 FROM deleted) THROW 53650,N'ANA_FLEET_SELECTION_IMMUTABLE',1;
 IF EXISTS(SELECT 1 FROM inserted i JOIN ctl.analytic_lab_run run ON run.run_id=i.run_id JOIN ref.reference_release r ON r.reference_release_id=i.reference_release_id
 WHERE i.valid_from<run.window_start OR i.valid_to_exclusive>run.window_end_exclusive OR r.family_code<>N'OWNED_FLEET'
 OR r.scope_code<>CONCAT(N'SYNTHETIC_ANALYTIC_LAB:',CONVERT(NVARCHAR(36),i.run_id)) OR r.valid_from<>i.valid_from OR r.valid_to_exclusive<>i.valid_to_exclusive
 OR NOT EXISTS(SELECT 1 FROM ref.reference_import_receipt receipt WHERE receipt.reference_release_id=r.reference_release_id)) THROW 53651,N'ANA_FLEET_SELECTION_SCOPE_SEAL',1;
 IF EXISTS(SELECT 1 FROM inserted i JOIN ctl.analytic_lab_fleet_selection other WITH(UPDLOCK,HOLDLOCK)
 ON other.run_id=i.run_id AND other.revision=i.revision AND other.valid_from<>i.valid_from
 AND other.valid_from<i.valid_to_exclusive AND i.valid_from<other.valid_to_exclusive) THROW 53652,N'ANA_FLEET_SELECTION_OVERLAP',1;
END;
GO
CREATE TYPE ref.analytic_fleet_document_batch AS TABLE(document_token CHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL PRIMARY KEY,reason_code NVARCHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL);
GO
CREATE TYPE ref.analytic_fleet_alias_batch AS TABLE(
 classification_scope NVARCHAR(32) COLLATE Latin1_General_100_BIN2 NOT NULL,alias_scope NVARCHAR(32) COLLATE Latin1_General_100_BIN2 NOT NULL,
 normalized_alias NVARCHAR(128) COLLATE Latin1_General_100_BIN2 NOT NULL,contract_class NVARCHAR(32) COLLATE Latin1_General_100_BIN2 NOT NULL,
 reason_code NVARCHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL,PRIMARY KEY(classification_scope,alias_scope,normalized_alias));
GO
CREATE TYPE ref.analytic_fleet_matrix_batch AS TABLE(
 vehicle_contract_class NVARCHAR(32) COLLATE Latin1_General_100_BIN2 NOT NULL,driver_contract_class NVARCHAR(32) COLLATE Latin1_General_100_BIN2 NOT NULL,
 classification_code NVARCHAR(32) COLLATE Latin1_General_100_BIN2 NOT NULL,reason_code NVARCHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL,
 PRIMARY KEY(vehicle_contract_class,driver_contract_class));
GO
CREATE TYPE ref.analytic_fleet_exception_batch AS TABLE(
 classification_scope NVARCHAR(32) COLLATE Latin1_General_100_BIN2 NOT NULL,subject_token CHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL,
 required_vehicle_contract_class NVARCHAR(32) COLLATE Latin1_General_100_BIN2 NULL,classification_code NVARCHAR(32) COLLATE Latin1_General_100_BIN2 NOT NULL,
 priority SMALLINT NOT NULL,reason_code NVARCHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL,PRIMARY KEY(classification_scope,subject_token));
GO
CREATE PROCEDURE ref.usp_import_analytic_fleet @run_id UNIQUEIDENTIFIER,@revision INT,@start DATE,@end DATE,@fingerprint CHAR(64),
 @documents ref.analytic_fleet_document_batch READONLY,@aliases ref.analytic_fleet_alias_batch READONLY,
 @matrix ref.analytic_fleet_matrix_batch READONLY,@exceptions ref.analytic_fleet_exception_batch READONLY,@now DATETIME2(7),@fixture_bytes INT
AS BEGIN SET NOCOUNT ON; SET XACT_ABORT ON;
 EXEC ctl.usp_analytic_lab_lock @run_id,'DIMENSION';
 IF ISNULL(@revision,0) NOT BETWEEN 1 AND 100000 OR @start IS NULL OR @end IS NULL OR @start>=@end OR @now IS NULL OR ISNULL(@fixture_bytes,0) NOT BETWEEN 1 AND 32768
 OR (SELECT COUNT_BIG(*) FROM @documents)>100 OR (SELECT COUNT_BIG(*) FROM @aliases)>100
 OR (SELECT COUNT_BIG(*) FROM @matrix)>100 OR (SELECT COUNT_BIG(*) FROM @exceptions)>100
 OR NOT EXISTS(SELECT 1 FROM ctl.analytic_lab_run WHERE run_id=@run_id AND @start>=window_start AND @end<=window_end_exclusive)
 THROW 53653,N'ANA_FLEET_REFERENCE_BOUND',1;
 DECLARE @id BIGINT,@outcome NVARCHAR(16),@scope NVARCHAR(128)=CONCAT(N'SYNTHETIC_ANALYTIC_LAB:',CONVERT(NVARCHAR(36),@run_id)),
 @version NVARCHAR(64)=CONCAT(N'analytic-fleet-references-v1-r',@revision),
 @count BIGINT=(SELECT COUNT_BIG(*) FROM @documents)+(SELECT COUNT_BIG(*) FROM @aliases)+(SELECT COUNT_BIG(*) FROM @matrix)+(SELECT COUNT_BIG(*) FROM @exceptions);
 EXEC ref.usp_register_reference_release N'OWNED_FLEET',@scope,@version,1,N'DETERMINISTIC_LOCAL',N'ANALYTIC_FLEET_FIXTURE_V1',
 @fingerprint,@count,@start,@end,N'SYNTHETIC_ONLY',N'LABORATORY_AUTHOR',@id OUTPUT,@outcome OUTPUT;
 IF @outcome=N'CREATED'
 BEGIN
 INSERT ref.frota_propria_documento SELECT @id,N'OWNED_FLEET',N'DRIVER_OWNERSHIP',document_token,N'sha256-utf16le-v1',@start,@end,reason_code FROM @documents;
 INSERT ref.classificacao_frota_alias SELECT @id,N'OWNED_FLEET',classification_scope,alias_scope,normalized_alias,N'ascii-trim-upper-v1',contract_class,@start,@end,reason_code FROM @aliases;
 INSERT ref.classificacao_frota_matriz SELECT @id,N'OWNED_FLEET',N'VEHICLE_DRIVER_CONTRACT',vehicle_contract_class,driver_contract_class,classification_code,@start,@end,reason_code FROM @matrix;
 INSERT ref.classificacao_frota_excecao_token SELECT @id,N'OWNED_FLEET',classification_scope,N'OWNER_NAME_TOKEN',subject_token,N'sha256-utf16le-v1',required_vehicle_contract_class,classification_code,priority,@start,@end,reason_code FROM @exceptions;
 INSERT ref.reference_import_receipt VALUES(@id,N'governed-references-v1',@fingerprint,@count,@fixture_bytes,N'LABORATORY_IMPORTER',SYSUTCDATETIME());
 END;
 IF EXISTS(SELECT * FROM @documents EXCEPT SELECT document_token,reason_code FROM ref.frota_propria_documento WHERE reference_release_id=@id)
 OR EXISTS(SELECT document_token,reason_code FROM ref.frota_propria_documento WHERE reference_release_id=@id EXCEPT SELECT * FROM @documents)
 OR EXISTS(SELECT * FROM @aliases EXCEPT SELECT classification_scope,alias_scope,normalized_alias,contract_class,reason_code FROM ref.classificacao_frota_alias WHERE reference_release_id=@id)
 OR EXISTS(SELECT classification_scope,alias_scope,normalized_alias,contract_class,reason_code FROM ref.classificacao_frota_alias WHERE reference_release_id=@id EXCEPT SELECT * FROM @aliases)
 OR EXISTS(SELECT * FROM @matrix EXCEPT SELECT vehicle_contract_class,driver_contract_class,classification_code,reason_code FROM ref.classificacao_frota_matriz WHERE reference_release_id=@id)
 OR EXISTS(SELECT vehicle_contract_class,driver_contract_class,classification_code,reason_code FROM ref.classificacao_frota_matriz WHERE reference_release_id=@id EXCEPT SELECT * FROM @matrix)
 OR EXISTS(SELECT * FROM @exceptions EXCEPT SELECT classification_scope,subject_token,required_vehicle_contract_class,classification_code,priority,reason_code FROM ref.classificacao_frota_excecao_token WHERE reference_release_id=@id)
 OR EXISTS(SELECT classification_scope,subject_token,required_vehicle_contract_class,classification_code,priority,reason_code FROM ref.classificacao_frota_excecao_token WHERE reference_release_id=@id EXCEPT SELECT * FROM @exceptions)
 THROW 53654,N'ANA_FLEET_REFERENCE_REPLAY_DIVERGENT',1;
 IF EXISTS(SELECT 1 FROM ctl.analytic_lab_fleet_selection WHERE run_id=@run_id AND revision=@revision AND valid_from=@start
 AND(reference_release_id<>@id OR valid_to_exclusive<>@end)) THROW 53655,N'ANA_FLEET_SELECTION_REPLAY_CONFLICT',1;
 INSERT ctl.analytic_lab_fleet_selection SELECT @run_id,@revision,@start,@end,@id
 WHERE NOT EXISTS(SELECT 1 FROM ctl.analytic_lab_fleet_selection WHERE run_id=@run_id AND revision=@revision AND valid_from=@start);
 SELECT @id reference_release_id,@count registered_rows;
END;
GO
CREATE FUNCTION ref.ufn_analytic_fleet(@run_id UNIQUEIDENTIFIER,@revision INT,@date DATE,
 @owner_document NVARCHAR(256),@owner_name NVARCHAR(256),@vehicle_contract NVARCHAR(128),@driver_contract NVARCHAR(128))
RETURNS TABLE AS RETURN(
 SELECT selected.reference_release_id,
 CONVERT(VARCHAR(32),CASE WHEN own.document_token IS NOT NULL OR owner_exception.classification_code=N'FLEET' THEN 'FLEET' ELSE ownership.contract_class END) ownership_code,
 COALESCE(contract_exception.classification_code,matrix.classification_code) contract_code,
 CONVERT(VARCHAR(40),CASE WHEN selected.reference_release_id IS NULL THEN 'FLEET_REFERENCE_MISSING'
 WHEN own.document_token IS NULL AND owner_exception.classification_code IS NULL AND ownership.contract_class IS NULL THEN 'OWNERSHIP_UNRESOLVED'
 WHEN contract_exception.classification_code IS NULL AND matrix.classification_code IS NULL THEN 'CONTRACT_UNRESOLVED' ELSE 'RESOLVED' END) disposition,
 CONVERT(VARCHAR(24),CASE WHEN own.document_token IS NOT NULL THEN 'DOCUMENT_REFERENCE' WHEN owner_exception.classification_code IS NOT NULL THEN 'OWNER_NAME_REFERENCE'
 WHEN ownership.contract_class IS NOT NULL THEN 'CONTRACT_REFERENCE' ELSE 'UNRESOLVED' END) ownership_provenance,
 vehicle.contract_class vehicle_contract_class,driver.contract_class driver_contract_class
 FROM(SELECT 1 seed) seed
 OUTER APPLY(SELECT reference_release_id FROM ctl.analytic_lab_fleet_selection WHERE run_id=@run_id AND revision=@revision AND valid_from<=@date AND valid_to_exclusive>@date) selected
 CROSS APPLY(SELECT LOWER(CONVERT(CHAR(64),HASHBYTES('SHA2_256',CONVERT(VARBINARY(MAX),core.ufn_analytic_document_key(@owner_document))),2)) document_token,
 CASE WHEN @owner_name COLLATE Latin1_General_100_BIN2 NOT LIKE N'%[^ -~]%' THEN LOWER(CONVERT(CHAR(64),HASHBYTES('SHA2_256',CONVERT(VARBINARY(MAX),UPPER(LTRIM(RTRIM(@owner_name))))),2)) END owner_token,
 UPPER(LTRIM(RTRIM(@vehicle_contract))) vehicle_alias,COALESCE(NULLIF(UPPER(LTRIM(RTRIM(@driver_contract))),N''),N'__NULL__') driver_alias) normalized
 LEFT JOIN ref.frota_propria_documento own ON own.reference_release_id=selected.reference_release_id AND own.document_token=normalized.document_token COLLATE Latin1_General_100_BIN2
 AND own.classification_scope=N'DRIVER_OWNERSHIP' AND own.token_scheme_version=N'sha256-utf16le-v1' AND own.valid_from<=@date AND own.valid_to_exclusive>@date
 LEFT JOIN ref.classificacao_frota_alias ownership ON ownership.reference_release_id=selected.reference_release_id AND ownership.classification_scope=N'DRIVER_OWNERSHIP'
 AND ownership.alias_scope=N'DRIVER_OWNERSHIP_CONTRACT' AND ownership.normalization_version=N'ascii-trim-upper-v1' AND ownership.normalized_alias=normalized.vehicle_alias COLLATE Latin1_General_100_BIN2 AND ownership.valid_from<=@date AND ownership.valid_to_exclusive>@date
 LEFT JOIN ref.classificacao_frota_alias vehicle ON vehicle.reference_release_id=selected.reference_release_id AND vehicle.classification_scope=N'VEHICLE_DRIVER_CONTRACT'
 AND vehicle.alias_scope=N'VEHICLE_CONTRACT' AND vehicle.normalization_version=N'ascii-trim-upper-v1' AND vehicle.normalized_alias=normalized.vehicle_alias COLLATE Latin1_General_100_BIN2 AND vehicle.valid_from<=@date AND vehicle.valid_to_exclusive>@date
 LEFT JOIN ref.classificacao_frota_alias driver ON driver.reference_release_id=selected.reference_release_id AND driver.classification_scope=N'VEHICLE_DRIVER_CONTRACT'
 AND driver.alias_scope=N'DRIVER_CONTRACT' AND driver.normalization_version=N'ascii-trim-upper-v1' AND driver.normalized_alias=normalized.driver_alias COLLATE Latin1_General_100_BIN2 AND driver.valid_from<=@date AND driver.valid_to_exclusive>@date
 LEFT JOIN ref.classificacao_frota_matriz matrix ON matrix.reference_release_id=selected.reference_release_id AND matrix.classification_scope=N'VEHICLE_DRIVER_CONTRACT'
 AND matrix.vehicle_contract_class=vehicle.contract_class AND matrix.driver_contract_class=driver.contract_class AND matrix.valid_from<=@date AND matrix.valid_to_exclusive>@date
 LEFT JOIN ref.classificacao_frota_excecao_token owner_exception ON owner_exception.reference_release_id=selected.reference_release_id AND owner_exception.classification_scope=N'DRIVER_OWNERSHIP'
 AND owner_exception.subject_token=normalized.owner_token COLLATE Latin1_General_100_BIN2 AND owner_exception.exception_scope=N'OWNER_NAME_TOKEN' AND owner_exception.token_scheme_version=N'sha256-utf16le-v1' AND owner_exception.valid_from<=@date AND owner_exception.valid_to_exclusive>@date
 LEFT JOIN ref.classificacao_frota_excecao_token contract_exception ON contract_exception.reference_release_id=selected.reference_release_id AND contract_exception.classification_scope=N'VEHICLE_DRIVER_CONTRACT'
 AND contract_exception.subject_token=normalized.owner_token COLLATE Latin1_General_100_BIN2 AND contract_exception.required_vehicle_contract_class=vehicle.contract_class
 AND contract_exception.exception_scope=N'OWNER_NAME_TOKEN' AND contract_exception.token_scheme_version=N'sha256-utf16le-v1' AND contract_exception.valid_from<=@date AND contract_exception.valid_to_exclusive>@date
);
GO
