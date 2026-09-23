-- Add one consumed synthetic label family; keep the eight existing reference families intact.
SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;
GO
ALTER TABLE ref.reference_release DROP CONSTRAINT CK_ref_reference_release_family;
ALTER TABLE ref.reference_release ADD CONSTRAINT CK_ref_reference_release_family CHECK (
 family_code IN(N'CALENDAR',N'PICK_STATUS',N'BRANCH_OPERATIONS',N'OWNED_FLEET',N'BRANCH_ATTRIBUTION',N'CUBAGE_EXCLUSION',N'LOGISTICS_REGION',N'QUOTE_TARIFF')
 OR(family_code=N'EXPANSION_LABELS' AND source_kind=N'DETERMINISTIC_LOCAL' AND scope_code LIKE N'SYNTHETIC_EXPANSION_LAB:%')
);
CREATE TABLE ref.expansion_lab_label (
 reference_release_id BIGINT NOT NULL,family_code NVARCHAR(32) COLLATE Latin1_General_100_BIN2 NOT NULL,
 category VARCHAR(16) COLLATE Latin1_General_100_BIN2 NOT NULL,raw_value NVARCHAR(128) COLLATE Latin1_General_100_BIN2 NOT NULL,
 label NVARCHAR(128) NOT NULL,
 CONSTRAINT PK_exp_label PRIMARY KEY(reference_release_id,category,raw_value),
 CONSTRAINT FK_exp_label_release FOREIGN KEY(reference_release_id,family_code) REFERENCES ref.reference_release(reference_release_id,family_code),
 CONSTRAINT CK_exp_label CHECK(family_code=N'EXPANSION_LABELS' AND category IN('CAP_TYPE','CAP_CLASS','FAT_CTE')
 AND LEN(raw_value)>0 AND DATALENGTH(raw_value)=DATALENGTH(LTRIM(RTRIM(raw_value))) AND LEN(label)>0)
);
GO
CREATE TRIGGER ref.trg_expansion_label_immutable ON ref.expansion_lab_label AFTER INSERT,UPDATE,DELETE
AS
BEGIN
 SET NOCOUNT ON;
 IF EXISTS(SELECT 1 FROM deleted) THROW 53430,N'EXP_LABEL_IMMUTABLE',1;
 IF EXISTS(SELECT 1 FROM inserted i JOIN ref.reference_import_receipt r ON r.reference_release_id=i.reference_release_id)
 THROW 53431,N'EXP_LABEL_RELEASE_SEALED',1;
END;
GO
CREATE TYPE ref.expansion_lab_label_batch AS TABLE (
 category VARCHAR(16) COLLATE Latin1_General_100_BIN2 NOT NULL,raw_value NVARCHAR(128) COLLATE Latin1_General_100_BIN2 NOT NULL,
 label NVARCHAR(128) NOT NULL,PRIMARY KEY(category,raw_value)
);
GO
CREATE TABLE ctl.expansion_lab_reference_selection (
 run_id UNIQUEIDENTIFIER NOT NULL,revision INT NOT NULL,purpose VARCHAR(16) NOT NULL,
 valid_from DATE NOT NULL,valid_to_exclusive DATE NOT NULL,reference_release_id BIGINT NOT NULL,
 CONSTRAINT PK_exp_ref_selection PRIMARY KEY(run_id,revision,purpose,valid_from),
 CONSTRAINT FK_exp_ref_selection_run FOREIGN KEY(run_id) REFERENCES ctl.expansion_lab_run(run_id),
 CONSTRAINT FK_exp_ref_selection_release FOREIGN KEY(reference_release_id) REFERENCES ref.reference_release(reference_release_id),
 CONSTRAINT CK_exp_ref_selection CHECK(revision BETWEEN 1 AND 1000 AND purpose IN('LABELS','CALENDAR','BRANCH','PAYER') AND valid_from<valid_to_exclusive)
);
GO
CREATE TRIGGER ctl.trg_expansion_reference_selection_guard ON ctl.expansion_lab_reference_selection AFTER INSERT,UPDATE,DELETE
AS
BEGIN
 SET NOCOUNT ON;
 IF EXISTS(SELECT 1 FROM deleted) THROW 53432,N'EXP_SELECTION_IMMUTABLE',1;
 IF EXISTS(SELECT 1 FROM inserted i LEFT JOIN ref.reference_release r ON r.reference_release_id=i.reference_release_id
 LEFT JOIN ref.reference_import_receipt seal ON seal.reference_release_id=r.reference_release_id
 WHERE seal.reference_release_id IS NULL OR r.scope_code<>CONCAT(N'SYNTHETIC_EXPANSION_LAB:',CONVERT(NVARCHAR(36),i.run_id)) COLLATE Latin1_General_100_BIN2
 OR r.source_kind<>N'DETERMINISTIC_LOCAL' OR i.valid_from<r.valid_from OR i.valid_to_exclusive>r.valid_to_exclusive
 OR r.family_code<>CASE i.purpose WHEN 'LABELS' THEN N'EXPANSION_LABELS' WHEN 'CALENDAR' THEN N'CALENDAR' WHEN 'BRANCH' THEN N'BRANCH_OPERATIONS' ELSE N'BRANCH_ATTRIBUTION' END)
 THROW 53433,N'EXP_SELECTION_SCOPE_OR_SEAL',1;
 IF EXISTS(SELECT 1 FROM inserted i JOIN ctl.expansion_lab_reference_selection x WITH(UPDLOCK,HOLDLOCK)
 ON x.run_id=i.run_id AND x.revision=i.revision AND x.purpose=i.purpose AND x.valid_from<>i.valid_from
 AND x.valid_from<i.valid_to_exclusive AND i.valid_from<x.valid_to_exclusive) THROW 53434,N'EXP_SELECTION_OVERLAP',1;
END;
GO
CREATE FUNCTION ref.ufn_expansion_reference(@run_id UNIQUEIDENTIFIER,@revision INT,@purpose VARCHAR(16),@date DATE)
RETURNS TABLE AS RETURN (
 SELECT reference_release_id,valid_from,valid_to_exclusive FROM ctl.expansion_lab_reference_selection
 WHERE run_id=@run_id AND revision=@revision AND purpose=@purpose AND valid_from<=@date AND valid_to_exclusive>@date
);
GO
CREATE PROCEDURE ref.usp_import_expansion_references @run_id UNIQUEIDENTIFIER,@revision INT,@start DATE,@end DATE,
 @fingerprint CHAR(64),@labels ref.expansion_lab_label_batch READONLY,@branch_code NVARCHAR(32),@branch_label NVARCHAR(128),
 @payer_token CHAR(64),@now DATETIME2(7)
AS
BEGIN
 SET NOCOUNT ON;SET XACT_ABORT ON;
 EXEC ctl.usp_expansion_lab_lock @run_id,'MATERIALIZE';
 IF @revision NOT BETWEEN 1 AND 1000 OR DATEDIFF(day,@start,@end) NOT BETWEEN 1 AND 62 OR (SELECT COUNT_BIG(*) FROM @labels) NOT BETWEEN 1 AND 100
 OR NOT EXISTS(SELECT 1 FROM ctl.expansion_lab_run WHERE run_id=@run_id AND @start>=DATEADD(day,-31,window_start) AND @end<=DATEADD(day,31,window_end_exclusive))
 THROW 53435,N'EXP_REF_BOUND',1;
 DECLARE @scope NVARCHAR(128)=CONCAT(N'SYNTHETIC_EXPANSION_LAB:',CONVERT(NVARCHAR(36),@run_id)),
 @version NVARCHAR(64)=CONCAT(N'expansion-references-v1-r',@revision),@outcome NVARCHAR(16),@label_id BIGINT,@calendar_id BIGINT,@branch_id BIGINT,@payer_id BIGINT;
 DECLARE @label_count BIGINT=(SELECT COUNT_BIG(*) FROM @labels),@calendar_count BIGINT,@calendar_start DATE;
 SELECT * INTO #calendar FROM ref.ufn_calendar_seed_candidate_v1(@start,@end);
 SELECT @calendar_count=COUNT_BIG(*),@calendar_start=MIN(calendar_date) FROM #calendar;
 IF @calendar_count=0 THROW 53436,N'EXP_CALENDAR_EMPTY',1;
 EXEC ref.usp_register_reference_release N'EXPANSION_LABELS',@scope,@version,1,N'DETERMINISTIC_LOCAL',N'EXPANSION_FIXTURE_V1',@fingerprint,@label_count,@start,@end,N'SYNTHETIC_ONLY',N'LABORATORY_AUTHOR',@label_id OUTPUT,@outcome OUTPUT;
 IF @outcome=N'CREATED'
 BEGIN
  INSERT ref.expansion_lab_label SELECT @label_id,N'EXPANSION_LABELS',* FROM @labels;
  INSERT ref.reference_import_receipt VALUES(@label_id,N'governed-references-v1',@fingerprint,@label_count,1,N'LABORATORY_IMPORTER',@now);
 END;
 EXEC ref.usp_register_reference_release N'CALENDAR',@scope,@version,1,N'DETERMINISTIC_LOCAL',N'EXPANSION_CALENDAR_V1',@fingerprint,@calendar_count,@calendar_start,@end,N'SYNTHETIC_ONLY',N'LABORATORY_AUTHOR',@calendar_id OUTPUT,@outcome OUTPUT;
 IF @outcome=N'CREATED'
 BEGIN
  INSERT ref.calendario SELECT @calendar_id,N'CALENDAR',* FROM #calendar;
  INSERT ref.reference_import_receipt VALUES(@calendar_id,N'governed-references-v1',@fingerprint,@calendar_count,1,N'LABORATORY_IMPORTER',@now);
 END;
 EXEC ref.usp_register_reference_release N'BRANCH_OPERATIONS',@scope,@version,1,N'DETERMINISTIC_LOCAL',N'EXPANSION_BRANCH_V1',@fingerprint,1,@start,@end,N'SYNTHETIC_ONLY',N'LABORATORY_AUTHOR',@branch_id OUTPUT,@outcome OUTPUT;
 IF @outcome=N'CREATED'
 BEGIN
  INSERT ref.filial_operacional VALUES(@branch_id,N'BRANCH_OPERATIONS',@branch_code,@branch_label,N'SYNTHETIC_ONLY');
  INSERT ref.reference_import_receipt VALUES(@branch_id,N'governed-references-v1',@fingerprint,1,1,N'LABORATORY_IMPORTER',@now);
 END;
 EXEC ref.usp_register_reference_release N'BRANCH_ATTRIBUTION',@scope,@version,1,N'DETERMINISTIC_LOCAL',N'EXPANSION_PAYER_V1',@fingerprint,1,@start,@end,N'SYNTHETIC_ONLY',N'LABORATORY_AUTHOR',@payer_id OUTPUT,@outcome OUTPUT;
 IF @outcome=N'CREATED'
 BEGIN
  INSERT ref.atribuicao_filial VALUES(@payer_id,N'BRANCH_ATTRIBUTION',@payer_token,N'synthetic-expansion-token-v1',@branch_id,@branch_code,@start,@end,N'SYNTHETIC_ONLY');
  INSERT ref.reference_import_receipt VALUES(@payer_id,N'governed-references-v1',@fingerprint,1,1,N'LABORATORY_IMPORTER',@now);
 END;
 IF NOT EXISTS(SELECT 1 FROM ref.filial_operacional WHERE reference_release_id=@branch_id AND branch_code=@branch_code AND branch_label=@branch_label)
 OR NOT EXISTS(SELECT 1 FROM ref.atribuicao_filial WHERE reference_release_id=@payer_id AND payer_document_token=@payer_token AND branch_reference_release_id=@branch_id AND branch_code=@branch_code)
 THROW 53437,N'EXP_REF_CONTENT_DIVERGENT',1;
 IF EXISTS(SELECT category,raw_value,label FROM ref.expansion_lab_label WHERE reference_release_id=@label_id EXCEPT SELECT category,raw_value,label FROM @labels)
 OR EXISTS(SELECT category,raw_value,label FROM @labels EXCEPT SELECT category,raw_value,label FROM ref.expansion_lab_label WHERE reference_release_id=@label_id)
 THROW 53437,N'EXP_REF_CONTENT_DIVERGENT',1;
 INSERT ctl.expansion_lab_reference_selection
 SELECT @run_id,@revision,purpose,@start,@end,release_id FROM (VALUES('LABELS',@label_id),('CALENDAR',@calendar_id),('BRANCH',@branch_id),('PAYER',@payer_id)) x(purpose,release_id)
 WHERE NOT EXISTS(SELECT 1 FROM ctl.expansion_lab_reference_selection s WHERE s.run_id=@run_id AND s.revision=@revision AND s.purpose=x.purpose AND s.valid_from=@start);
 SELECT @label_id label_release,@calendar_id calendar_release,@branch_id branch_release,@payer_id payer_release;
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

