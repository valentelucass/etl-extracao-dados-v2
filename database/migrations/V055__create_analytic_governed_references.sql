-- ANA-04: consumed laboratory references extend the existing V2-035a release registry.
SET ANSI_NULLS ON; SET QUOTED_IDENTIFIER ON;
GO
ALTER TABLE ref.reference_release DROP CONSTRAINT CK_ref_reference_release_family;
ALTER TABLE ref.reference_release ADD CONSTRAINT CK_ref_reference_release_family CHECK (
 family_code IN(N'CALENDAR',N'PICK_STATUS',N'BRANCH_OPERATIONS',N'OWNED_FLEET',N'BRANCH_ATTRIBUTION',N'CUBAGE_EXCLUSION',N'LOGISTICS_REGION',N'QUOTE_TARIFF')
 OR(family_code=N'EXPANSION_LABELS' AND source_kind=N'DETERMINISTIC_LOCAL' AND scope_code LIKE N'SYNTHETIC_EXPANSION_LAB:%')
 OR(family_code=N'ANALYTIC_RULES' AND source_kind=N'DETERMINISTIC_LOCAL' AND scope_code LIKE N'SYNTHETIC_ANALYTIC_LAB:%')
);
CREATE TABLE ref.analytic_lab_label (
 reference_release_id BIGINT NOT NULL REFERENCES ref.reference_release(reference_release_id),
 category VARCHAR(32) COLLATE Latin1_General_100_BIN2 NOT NULL,
 raw_value NVARCHAR(128) COLLATE Latin1_General_100_BIN2 NOT NULL,label NVARCHAR(128) COLLATE Latin1_General_100_BIN2 NOT NULL,
 CONSTRAINT PK_analytic_label PRIMARY KEY(reference_release_id,category,raw_value)
);
CREATE TABLE ref.analytic_lab_registry (
 reference_release_id BIGINT NOT NULL REFERENCES ref.reference_release(reference_release_id),
 dimension_kind VARCHAR(16) COLLATE Latin1_General_100_BIN2 NOT NULL,
 entity_key VARCHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL,
 raw_name NVARCHAR(256) COLLATE Latin1_General_100_BIN2 NOT NULL,normalized_name AS UPPER(LTRIM(RTRIM(raw_name))) PERSISTED,
 normalization_version VARCHAR(32) COLLATE Latin1_General_100_BIN2 NOT NULL,raw_document NVARCHAR(64) COLLATE Latin1_General_100_BIN2 NULL,
 license_plate NVARCHAR(32) COLLATE Latin1_General_100_BIN2 NULL,branch_key VARCHAR(64) COLLATE Latin1_General_100_BIN2 NULL,
 capacity DECIMAL(28,8) NULL,capacity_unit VARCHAR(8) COLLATE Latin1_General_100_BIN2 NULL,classification NVARCHAR(128) COLLATE Latin1_General_100_BIN2 NULL,
 valid_from DATE NOT NULL,valid_to_exclusive DATE NOT NULL,active BIT NOT NULL,source_path NVARCHAR(256) COLLATE Latin1_General_100_BIN2 NOT NULL, vehicle_type NVARCHAR(64) COLLATE Latin1_General_100_BIN2 NULL, vehicle_owner NVARCHAR(256) COLLATE Latin1_General_100_BIN2 NULL,
 CONSTRAINT PK_analytic_registry PRIMARY KEY(reference_release_id,dimension_kind,entity_key,valid_from),
 CONSTRAINT CK_analytic_registry CHECK(dimension_kind IN('FILIAL','CLIENTE','VEICULO','MOTORISTA','PLANOCONTAS','USUARIO')
 AND entity_key LIKE 'synthetic-%' AND DATALENGTH(entity_key)=DATALENGTH(RTRIM(entity_key))
 AND normalization_version='TRIM_UPPER_V1' AND LEN(LTRIM(RTRIM(raw_name)))>0
 AND valid_from<valid_to_exclusive AND (capacity IS NULL OR capacity>=0)
 AND ((capacity IS NULL AND capacity_unit IS NULL) OR(capacity IS NOT NULL AND capacity_unit='KG')))
);
CREATE INDEX IX_analytic_registry_lookup ON ref.analytic_lab_registry(reference_release_id,dimension_kind,entity_key,valid_to_exclusive)
 INCLUDE(raw_name,normalized_name,branch_key,capacity,capacity_unit,active);
CREATE TABLE ref.analytic_lab_exclusion (
 reference_release_id BIGINT NOT NULL REFERENCES ref.reference_release(reference_release_id),
 kind VARCHAR(24) COLLATE Latin1_General_100_BIN2 NOT NULL,
 match_value NVARCHAR(128) COLLATE Latin1_General_100_BIN2 NOT NULL,
 CONSTRAINT PK_analytic_exclusion PRIMARY KEY(reference_release_id,kind,match_value),
 CONSTRAINT CK_analytic_exclusion CHECK(kind IN('PAYER','BRANCH_DOCUMENT','OPERATION_PREFIX','LICENSE_PLATE','GENERIC_DRIVER'))
);
CREATE TYPE ref.analytic_lab_label_batch AS TABLE (
 category VARCHAR(32) COLLATE Latin1_General_100_BIN2 NOT NULL,raw_value NVARCHAR(128) COLLATE Latin1_General_100_BIN2 NOT NULL,label NVARCHAR(128) COLLATE Latin1_General_100_BIN2 NOT NULL,
 PRIMARY KEY(category,raw_value)
);
CREATE TYPE ref.analytic_lab_registry_batch AS TABLE (
 dimension_kind VARCHAR(16) COLLATE Latin1_General_100_BIN2 NOT NULL,entity_key VARCHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL,raw_name NVARCHAR(256) COLLATE Latin1_General_100_BIN2 NOT NULL,
 raw_document NVARCHAR(64) COLLATE Latin1_General_100_BIN2 NULL,license_plate NVARCHAR(32) COLLATE Latin1_General_100_BIN2 NULL,branch_key VARCHAR(64) COLLATE Latin1_General_100_BIN2 NULL,
 capacity DECIMAL(28,8) NULL,capacity_unit VARCHAR(8) COLLATE Latin1_General_100_BIN2 NULL,classification NVARCHAR(128) COLLATE Latin1_General_100_BIN2 NULL,
 valid_from DATE NOT NULL,valid_to_exclusive DATE NOT NULL,active BIT NOT NULL,source_path NVARCHAR(256) COLLATE Latin1_General_100_BIN2 NOT NULL, vehicle_type NVARCHAR(64) COLLATE Latin1_General_100_BIN2 NULL, vehicle_owner NVARCHAR(256) COLLATE Latin1_General_100_BIN2 NULL,
 PRIMARY KEY(dimension_kind,entity_key,valid_from)
);
CREATE TYPE ref.analytic_lab_exclusion_batch AS TABLE (
 kind VARCHAR(24) COLLATE Latin1_General_100_BIN2 NOT NULL,match_value NVARCHAR(128) COLLATE Latin1_General_100_BIN2 NOT NULL,PRIMARY KEY(kind,match_value)
);
CREATE TABLE ctl.analytic_lab_reference_selection (
 run_id UNIQUEIDENTIFIER NOT NULL REFERENCES ctl.analytic_lab_run(run_id),revision INT NOT NULL,
 valid_from DATE NOT NULL,valid_to_exclusive DATE NOT NULL,reference_release_id BIGINT NOT NULL REFERENCES ref.reference_release(reference_release_id),
 fiscal_policy VARCHAR(24) COLLATE Latin1_General_100_BIN2 NOT NULL,branch_policy VARCHAR(24) COLLATE Latin1_General_100_BIN2 NOT NULL,driver_policy VARCHAR(24) COLLATE Latin1_General_100_BIN2 NOT NULL,
 CONSTRAINT PK_analytic_reference_selection PRIMARY KEY(run_id,revision,valid_from),
 CONSTRAINT CK_analytic_reference_selection CHECK(revision BETWEEN 1 AND 100000 AND valid_from<valid_to_exclusive
 AND fiscal_policy IN('CTE_FIRST_REAL_DOCUMENT','UNRESOLVED') AND branch_policy IN('EXPLICIT_ASSIGNMENT','UNRESOLVED')
 AND driver_policy IN('INCLUDE_ALL_BOUND','EXCLUDE_GENERIC','UNRESOLVED'))
);
GO
CREATE TRIGGER ref.trg_analytic_registry_guard ON ref.analytic_lab_registry AFTER INSERT,UPDATE,DELETE
AS BEGIN SET NOCOUNT ON;
 IF EXISTS(SELECT 1 FROM deleted) THROW 53550,N'ANA_REGISTRY_IMMUTABLE',1;
 IF EXISTS(SELECT 1 FROM inserted i JOIN ref.reference_import_receipt r ON r.reference_release_id=i.reference_release_id)
 THROW 53551,N'ANA_REFERENCE_SEALED',1;
 IF EXISTS(SELECT 1 FROM inserted i JOIN ref.analytic_lab_registry x WITH(UPDLOCK,HOLDLOCK)
 ON x.reference_release_id=i.reference_release_id AND x.dimension_kind=i.dimension_kind AND x.entity_key=i.entity_key
 AND x.valid_from<>i.valid_from AND x.valid_from<i.valid_to_exclusive AND i.valid_from<x.valid_to_exclusive)
 THROW 53552,N'ANA_REGISTRY_VALIDITY_OVERLAP',1;
 IF EXISTS(SELECT 1 FROM inserted i JOIN ref.reference_release r ON r.reference_release_id=i.reference_release_id
 WHERE r.family_code<>'ANALYTIC_RULES' OR i.valid_from<r.valid_from OR i.valid_to_exclusive>r.valid_to_exclusive)
 THROW 53553,N'ANA_REGISTRY_RELEASE_SCOPE',1;
END;
GO
CREATE TRIGGER ref.trg_analytic_label_guard ON ref.analytic_lab_label AFTER INSERT,UPDATE,DELETE
AS BEGIN SET NOCOUNT ON;
 IF EXISTS(SELECT 1 FROM deleted) THROW 53554,N'ANA_LABEL_IMMUTABLE',1;
 IF EXISTS(SELECT 1 FROM inserted i JOIN ref.reference_import_receipt r ON r.reference_release_id=i.reference_release_id)
 THROW 53551,N'ANA_REFERENCE_SEALED',1;
END;
GO
CREATE TRIGGER ref.trg_analytic_exclusion_guard ON ref.analytic_lab_exclusion AFTER INSERT,UPDATE,DELETE
AS BEGIN SET NOCOUNT ON;
 IF EXISTS(SELECT 1 FROM deleted) THROW 53555,N'ANA_EXCLUSION_IMMUTABLE',1;
 IF EXISTS(SELECT 1 FROM inserted i JOIN ref.reference_import_receipt r ON r.reference_release_id=i.reference_release_id)
 THROW 53551,N'ANA_REFERENCE_SEALED',1;
END;
GO
CREATE TRIGGER ctl.trg_analytic_reference_selection_guard ON ctl.analytic_lab_reference_selection AFTER INSERT,UPDATE,DELETE
AS BEGIN SET NOCOUNT ON;
 IF EXISTS(SELECT 1 FROM deleted) THROW 53556,N'ANA_SELECTION_IMMUTABLE',1;
 IF EXISTS(SELECT 1 FROM inserted i LEFT JOIN ref.reference_release r ON r.reference_release_id=i.reference_release_id
 LEFT JOIN ref.reference_import_receipt s ON s.reference_release_id=r.reference_release_id
 WHERE s.reference_release_id IS NULL OR r.scope_code<>CONCAT(N'SYNTHETIC_ANALYTIC_LAB:',CONVERT(NVARCHAR(36),i.run_id)) COLLATE Latin1_General_100_BIN2
 OR r.family_code<>'ANALYTIC_RULES' OR i.valid_from<r.valid_from OR i.valid_to_exclusive>r.valid_to_exclusive)
 THROW 53557,N'ANA_SELECTION_SCOPE_SEAL',1;
 IF EXISTS(SELECT 1 FROM inserted i JOIN ctl.analytic_lab_reference_selection x WITH(UPDLOCK,HOLDLOCK)
 ON x.run_id=i.run_id AND x.revision=i.revision AND x.valid_from<>i.valid_from
 AND x.valid_from<i.valid_to_exclusive AND i.valid_from<x.valid_to_exclusive)
 THROW 53558,N'ANA_SELECTION_OVERLAP',1;
END;
GO
CREATE FUNCTION ref.ufn_analytic_reference(@run_id UNIQUEIDENTIFIER,@revision INT,@date DATE)
RETURNS TABLE AS RETURN (
 SELECT reference_release_id,fiscal_policy,branch_policy,driver_policy FROM ctl.analytic_lab_reference_selection
 WHERE run_id=@run_id AND revision=@revision AND valid_from<=@date AND valid_to_exclusive>@date
);
GO
CREATE PROCEDURE ref.usp_import_analytic_references @run_id UNIQUEIDENTIFIER,@revision INT,@start DATE,@end DATE,
 @fingerprint CHAR(64),@labels ref.analytic_lab_label_batch READONLY,@registry ref.analytic_lab_registry_batch READONLY,
 @exclusions ref.analytic_lab_exclusion_batch READONLY,@fiscal VARCHAR(24),@branch VARCHAR(24),@driver VARCHAR(24),@now DATETIME2(7)
AS BEGIN SET NOCOUNT ON;SET XACT_ABORT ON;
 EXEC ctl.usp_analytic_lab_lock @run_id,'DIMENSION';
 IF @revision NOT BETWEEN 1 AND 100000 OR @start>=@end OR DATEDIFF(DAY,@start,@end)>3660
 OR (SELECT COUNT_BIG(*) FROM @labels)>100 OR (SELECT COUNT_BIG(*) FROM @registry)>100
 OR (SELECT COUNT_BIG(*) FROM @exclusions)>100 OR NOT EXISTS(SELECT 1 FROM ctl.analytic_lab_run WHERE run_id=@run_id
 AND @start>=window_start AND @end<=window_end_exclusive) THROW 53559,N'ANA_REFERENCE_BOUND',1;
 DECLARE @count BIGINT=(SELECT COUNT_BIG(*) FROM @labels)+(SELECT COUNT_BIG(*) FROM @registry)+(SELECT COUNT_BIG(*) FROM @exclusions),
 @id BIGINT,@outcome NVARCHAR(16),@scope NVARCHAR(128)=CONCAT(N'SYNTHETIC_ANALYTIC_LAB:',CONVERT(NVARCHAR(36),@run_id)),
 @version NVARCHAR(64)=CONCAT(N'analytic-references-v1-r',@revision);
 EXEC ref.usp_register_reference_release N'ANALYTIC_RULES',@scope,@version,1,N'DETERMINISTIC_LOCAL',N'ANALYTIC_FIXTURE_V1',
 @fingerprint,@count,@start,@end,N'SYNTHETIC_ONLY',N'LABORATORY_AUTHOR',@id OUTPUT,@outcome OUTPUT;
 IF @outcome=N'CREATED'
 BEGIN
 INSERT ref.analytic_lab_label SELECT @id,* FROM @labels;
 INSERT ref.analytic_lab_registry(reference_release_id,dimension_kind,entity_key,raw_name,normalization_version,raw_document,
 license_plate,branch_key,capacity,capacity_unit,classification,valid_from,valid_to_exclusive,active,source_path,vehicle_type,vehicle_owner)
 SELECT @id,dimension_kind,entity_key,raw_name,'TRIM_UPPER_V1',raw_document,license_plate,branch_key,capacity,capacity_unit,
 classification,valid_from,valid_to_exclusive,active,source_path,vehicle_type,vehicle_owner FROM @registry;
 INSERT ref.analytic_lab_exclusion SELECT @id,* FROM @exclusions;
 INSERT ref.reference_import_receipt VALUES(@id,N'governed-references-v1',@fingerprint,@count,1,N'LABORATORY_IMPORTER',@now);
 END;
 IF EXISTS(SELECT category,raw_value,label FROM ref.analytic_lab_label WHERE reference_release_id=@id EXCEPT SELECT * FROM @labels)
 OR EXISTS(SELECT * FROM @labels EXCEPT SELECT category,raw_value,label FROM ref.analytic_lab_label WHERE reference_release_id=@id)
 OR EXISTS(SELECT kind,match_value FROM ref.analytic_lab_exclusion WHERE reference_release_id=@id EXCEPT SELECT * FROM @exclusions)
 OR EXISTS(SELECT * FROM @exclusions EXCEPT SELECT kind,match_value FROM ref.analytic_lab_exclusion WHERE reference_release_id=@id)
 THROW 53560,N'ANA_REFERENCE_REPLAY_DIVERGENT',1;
 IF EXISTS(SELECT dimension_kind,entity_key,raw_name,raw_document,license_plate,branch_key,capacity,capacity_unit,classification,
 valid_from,valid_to_exclusive,active,source_path,vehicle_type,vehicle_owner FROM ref.analytic_lab_registry WHERE reference_release_id=@id EXCEPT SELECT * FROM @registry)
 OR EXISTS(SELECT * FROM @registry EXCEPT SELECT dimension_kind,entity_key,raw_name,raw_document,license_plate,branch_key,capacity,
 capacity_unit,classification,valid_from,valid_to_exclusive,active,source_path,vehicle_type,vehicle_owner FROM ref.analytic_lab_registry WHERE reference_release_id=@id)
 THROW 53560,N'ANA_REFERENCE_REPLAY_DIVERGENT',1;
 IF EXISTS(SELECT 1 FROM ctl.analytic_lab_reference_selection WHERE run_id=@run_id AND revision=@revision AND valid_from=@start
 AND(reference_release_id<>@id OR valid_to_exclusive<>@end OR fiscal_policy<>@fiscal OR branch_policy<>@branch OR driver_policy<>@driver))
 THROW 53561,N'ANA_POLICY_REPLAY_DIVERGENT',1;
 INSERT ctl.analytic_lab_reference_selection SELECT @run_id,@revision,@start,@end,@id,@fiscal,@branch,@driver
 WHERE NOT EXISTS(SELECT 1 FROM ctl.analytic_lab_reference_selection WHERE run_id=@run_id AND revision=@revision AND valid_from=@start);
 SELECT @id reference_release_id,@count registered_rows;
END;
GO
ALTER TRIGGER ref.trg_reference_import_receipt_guard
ON ref.reference_import_receipt
AFTER INSERT
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;

    IF (SELECT COUNT_BIG(*) FROM inserted) <> 1
        THROW 51942, N'Um recibo deve selar exatamente uma release por transação.', 1;

    DECLARE @release_id BIGINT;
    DECLARE @family NVARCHAR(32);
    DECLARE @expected_fingerprint CHAR(64);
    DECLARE @expected_rows BIGINT;
    DECLARE @release_recorded_at DATETIME2(3);
    SELECT @release_id = receipt.reference_release_id,
           @family = release_definition.family_code,
           @expected_fingerprint = release_definition.source_fingerprint,
           @expected_rows = release_definition.source_row_count,
           @release_recorded_at = release_definition.recorded_at_utc
    FROM inserted AS receipt
    INNER JOIN ref.reference_release AS release_definition WITH (UPDLOCK, HOLDLOCK)
        ON release_definition.reference_release_id = receipt.reference_release_id;

    DECLARE @actual_rows BIGINT = CASE @family
        WHEN N'CALENDAR' THEN
            (SELECT COUNT_BIG(*) FROM ref.calendario WHERE reference_release_id = @release_id)
        WHEN N'PICK_STATUS' THEN
            (SELECT COUNT_BIG(*) FROM ref.status_coleta WHERE reference_release_id = @release_id)
        WHEN N'BRANCH_OPERATIONS' THEN
            (SELECT COUNT_BIG(*) FROM ref.filial_operacional WHERE reference_release_id = @release_id)
            + (SELECT COUNT_BIG(*) FROM ref.regiao_destino_alias WHERE reference_release_id = @release_id)
            + (SELECT COUNT_BIG(*) FROM ref.filial_operacional_documento WHERE reference_release_id = @release_id)
        WHEN N'OWNED_FLEET' THEN
            (SELECT COUNT_BIG(*) FROM ref.frota_propria_documento WHERE reference_release_id = @release_id)
            + (SELECT COUNT_BIG(*) FROM ref.classificacao_frota_alias WHERE reference_release_id = @release_id)
            + (SELECT COUNT_BIG(*) FROM ref.classificacao_frota_matriz WHERE reference_release_id = @release_id)
            + (SELECT COUNT_BIG(*) FROM ref.classificacao_frota_excecao_token WHERE reference_release_id = @release_id)
        WHEN N'BRANCH_ATTRIBUTION' THEN
            (SELECT COUNT_BIG(*) FROM ref.atribuicao_filial WHERE reference_release_id = @release_id)
        WHEN N'CUBAGE_EXCLUSION' THEN
            (SELECT COUNT_BIG(*) FROM ref.pagador_exclusao_cubagem WHERE reference_release_id = @release_id)
        WHEN N'LOGISTICS_REGION' THEN
            (SELECT COUNT_BIG(*) FROM ref.regiao_logistica_cep WHERE reference_release_id = @release_id)
            + (SELECT COUNT_BIG(*) FROM ref.regiao_logistica_cidade_uf WHERE reference_release_id = @release_id)
        WHEN N'QUOTE_TARIFF' THEN
            (SELECT COUNT_BIG(*) FROM ref.tarifa_rota_uf WHERE reference_release_id = @release_id)
        WHEN N'EXPANSION_LABELS' THEN
            (SELECT COUNT_BIG(*) FROM ref.expansion_lab_label WHERE reference_release_id=@release_id)
        WHEN N'ANALYTIC_RULES' THEN
            (SELECT COUNT_BIG(*) FROM ref.analytic_lab_label WHERE reference_release_id=@release_id)
            + (SELECT COUNT_BIG(*) FROM ref.analytic_lab_registry WHERE reference_release_id=@release_id)
            + (SELECT COUNT_BIG(*) FROM ref.analytic_lab_exclusion WHERE reference_release_id=@release_id)
        ELSE -1
    END;

    IF EXISTS (
        SELECT 1 FROM inserted
        WHERE content_fingerprint <> @expected_fingerprint
           OR imported_row_count <> @expected_rows
           OR imported_row_count <> @actual_rows
           OR recorded_at_utc < @release_recorded_at
    )
        THROW 51943, N'Recibo não confere com envelope, conteúdo físico ou cronologia.', 1;
END;
GO

