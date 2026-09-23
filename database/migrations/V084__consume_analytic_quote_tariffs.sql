-- Synthetic quote tariff selection reuses the existing governed release/receipt/SHADOW gate.
SET ANSI_NULLS ON;SET QUOTED_IDENTIFIER ON;
GO
CREATE TABLE ctl.analytic_quote_tariff_selection (
 run_id UNIQUEIDENTIFIER NOT NULL REFERENCES ctl.analytic_lab_run(run_id),revision INT NOT NULL,
 valid_from DATE NOT NULL,valid_to_exclusive DATE NOT NULL,reference_release_id BIGINT NOT NULL REFERENCES ref.reference_release(reference_release_id),
 weight_unit VARCHAR(8) NOT NULL,
 CONSTRAINT PK_ana_quote_tariff_selection PRIMARY KEY(run_id,revision,valid_from),
 CONSTRAINT CK_ana_quote_tariff_selection CHECK(revision BETWEEN 1 AND 100000 AND valid_from<valid_to_exclusive AND weight_unit='KG')
);
GO
CREATE TRIGGER ctl.trg_analytic_quote_tariff_selection ON ctl.analytic_quote_tariff_selection AFTER INSERT,UPDATE,DELETE AS
BEGIN
 SET NOCOUNT ON;
 IF EXISTS(SELECT 1 FROM deleted) THROW 53700,N'ANA_QUOTE_TARIFF_SELECTION_IMMUTABLE',1;
 IF EXISTS(SELECT 1 FROM inserted i LEFT JOIN ref.reference_release r ON r.reference_release_id=i.reference_release_id
 LEFT JOIN ref.reference_import_receipt seal ON seal.reference_release_id=r.reference_release_id
 LEFT JOIN ref.reference_release_ratification rat ON rat.reference_release_id=r.reference_release_id AND rat.activation_scope=N'SHADOW'
 WHERE r.family_code<>N'QUOTE_TARIFF' OR r.source_kind<>N'DETERMINISTIC_LOCAL'
 OR r.scope_code<>CONCAT(N'SYNTHETIC_QUOTE_LAB:',CONVERT(NVARCHAR(36),i.run_id),N':R',i.revision) COLLATE Latin1_General_100_BIN2
 OR seal.reference_release_id IS NULL OR rat.reference_release_id IS NULL OR i.valid_from<r.valid_from OR i.valid_to_exclusive>r.valid_to_exclusive
 OR EXISTS(SELECT 1 FROM ref.reference_release_revocation rev WHERE rev.reference_release_id=r.reference_release_id AND rev.activation_scope=N'SHADOW'))
 THROW 53701,N'ANA_QUOTE_TARIFF_SELECTION_SCOPE',1;
 IF EXISTS(SELECT 1 FROM inserted i JOIN ctl.analytic_quote_tariff_selection x WITH(UPDLOCK,HOLDLOCK)
 ON x.run_id=i.run_id AND x.revision=i.revision AND x.valid_from<>i.valid_from
 AND x.valid_from<i.valid_to_exclusive AND i.valid_from<x.valid_to_exclusive) THROW 53702,N'ANA_QUOTE_TARIFF_SELECTION_OVERLAP',1;
END;
GO
CREATE TYPE ref.analytic_quote_tariff_batch AS TABLE (
 origin_uf CHAR(2) COLLATE Latin1_General_100_BIN2 NOT NULL,destination_uf CHAR(2) COLLATE Latin1_General_100_BIN2 NOT NULL,
 amount DECIMAL(19,4) NOT NULL,currency CHAR(3) COLLATE Latin1_General_100_BIN2 NOT NULL,
 unit NVARCHAR(32) COLLATE Latin1_General_100_BIN2 NOT NULL,weight_unit VARCHAR(8) NOT NULL,
 rounding NVARCHAR(24) COLLATE Latin1_General_100_BIN2 NOT NULL,
 PRIMARY KEY(origin_uf,destination_uf)
);
GO
CREATE PROCEDURE ref.usp_import_analytic_quote_tariffs @run_id UNIQUEIDENTIFIER,@revision INT,@start DATE,@end DATE,
 @fingerprint CHAR(64),@fixture_bytes INT,@rows ref.analytic_quote_tariff_batch READONLY AS
BEGIN
 SET NOCOUNT ON;SET XACT_ABORT OFF;
 IF @@TRANCOUNT=0 OR @revision NOT BETWEEN 1 AND 100000 OR @fixture_bytes NOT BETWEEN 1 AND 32768
 OR @start>=@end OR DATEDIFF(day,@start,@end)>3660 OR (SELECT COUNT_BIG(*) FROM @rows) NOT BETWEEN 1 AND 100
 OR @fingerprint LIKE '%[^0-9a-f]%' OR NOT EXISTS(SELECT 1 FROM ctl.analytic_lab_run WHERE run_id=@run_id AND @start>=window_start AND @end<=window_end_exclusive)
 THROW 53703,N'ANA_QUOTE_TARIFF_IMPORT_BOUND',1;
 IF EXISTS(SELECT 1 FROM @rows WHERE amount<=0 OR currency<>'BRL' OR unit<>N'PER_WEIGHT' OR weight_unit<>'KG'
 OR rounding<>N'HALF_UP' OR origin_uf NOT IN('AC','AL','AP','AM','BA','CE','DF','ES','GO','MA','MT','MS','MG','PA','PB','PR','PE','PI','RJ','RN','RS','RO','RR','SC','SP','SE','TO')
 OR destination_uf NOT IN('AC','AL','AP','AM','BA','CE','DF','ES','GO','MA','MT','MS','MG','PA','PB','PR','PE','PI','RJ','RN','RS','RO','RR','SC','SP','SE','TO'))
 THROW 53704,N'ANA_QUOTE_TARIFF_IMPORT_ROW',1;
 EXEC ctl.usp_analytic_lab_lock @run_id,'DIMENSION';
 DECLARE @release BIGINT,@count BIGINT=(SELECT COUNT_BIG(*) FROM @rows),@outcome NVARCHAR(16),
 @scope NVARCHAR(128)=CONCAT(N'SYNTHETIC_QUOTE_LAB:',CONVERT(NVARCHAR(36),@run_id),N':R',@revision),
 @version NVARCHAR(64)=CONCAT(N'analytic-quote-tariff-v1-r',@revision);
 SELECT @release=reference_release_id FROM ctl.analytic_quote_tariff_selection WHERE run_id=@run_id AND revision=@revision AND valid_from=@start;
 IF @release IS NOT NULL
 BEGIN
  IF NOT EXISTS(SELECT 1 FROM ctl.analytic_quote_tariff_selection s JOIN ref.reference_release r ON r.reference_release_id=s.reference_release_id
  JOIN ref.reference_import_receipt receipt ON receipt.reference_release_id=r.reference_release_id
  WHERE s.run_id=@run_id AND s.revision=@revision AND s.valid_from=@start AND s.valid_to_exclusive=@end
  AND r.scope_code=@scope AND r.source_fingerprint=@fingerprint AND r.source_row_count=@count AND receipt.imported_byte_count=@fixture_bytes)
  OR EXISTS(SELECT origin_uf,destination_uf,amount,currency,unit,rounding FROM @rows EXCEPT
  SELECT origin_uf,destination_uf,minimum_amount,currency_code,unit_code,rounding_mode FROM ref.tarifa_rota_uf WHERE reference_release_id=@release)
  OR EXISTS(SELECT origin_uf,destination_uf,minimum_amount,currency_code,unit_code,rounding_mode FROM ref.tarifa_rota_uf WHERE reference_release_id=@release EXCEPT
  SELECT origin_uf,destination_uf,amount,currency,unit,rounding FROM @rows)
  THROW 53705,N'ANA_QUOTE_TARIFF_RETRY_DIVERGENT',1;
  SELECT @release reference_release_id,@count rows_imported;RETURN;
 END;
 IF EXISTS(SELECT 1 FROM ctl.analytic_quote_tariff_selection WHERE run_id=@run_id AND revision=@revision
 AND valid_from<@end AND @start<valid_to_exclusive) THROW 53702,N'ANA_QUOTE_TARIFF_SELECTION_OVERLAP',1;
 EXEC ref.usp_register_reference_release N'QUOTE_TARIFF',@scope,@version,1,N'DETERMINISTIC_LOCAL',N'ANALYTIC_QUOTE_FIXTURE_V1',
 @fingerprint,@count,@start,@end,N'SYNTHETIC_LABORATORY_ONLY',N'SYNTHETIC_QUOTE_AUTHOR',@release OUTPUT,@outcome OUTPUT;
 IF @outcome<>N'CREATED' THROW 53706,N'ANA_QUOTE_TARIFF_ORPHAN_RELEASE',1;
 INSERT ref.tarifa_rota_uf
 SELECT @release,N'QUOTE_TARIFF',origin_uf,destination_uf,N'PRICED',amount,currency,unit,rounding,@start,@end,N'SYNTHETIC_LABORATORY_ONLY' FROM @rows;
 INSERT ref.reference_import_receipt(reference_release_id,contract_version,content_fingerprint,imported_row_count,imported_byte_count,importer_role)
 VALUES(@release,N'governed-references-v1',@fingerprint,@count,@fixture_bytes,N'SYNTHETIC_QUOTE_IMPORTER');
 INSERT ref.reference_release_ratification(reference_release_id,activation_scope,approver_role,approval_evidence_ref,approval_fingerprint)
 VALUES(@release,N'SHADOW',N'SYNTHETIC_QUOTE_APPROVER',N'ANALYTIC_FIXTURE_ONLY_ROLLBACK',@fingerprint);
 INSERT ctl.analytic_quote_tariff_selection VALUES(@run_id,@revision,@start,@end,@release,'KG');
 SELECT @release reference_release_id,@count rows_imported;
END;
GO
CREATE FUNCTION ref.ufn_analytic_quote_tariff(@run_id UNIQUEIDENTIFIER,@revision INT,@date DATE)
RETURNS TABLE AS RETURN (
 SELECT s.reference_release_id,s.valid_from,s.valid_to_exclusive,s.weight_unit
 FROM ctl.analytic_quote_tariff_selection s
 JOIN ref.reference_release r ON r.reference_release_id=s.reference_release_id AND r.family_code=N'QUOTE_TARIFF'
 JOIN ref.reference_release_ratification rat ON rat.reference_release_id=r.reference_release_id AND rat.activation_scope=N'SHADOW'
 WHERE s.run_id=@run_id AND s.revision=@revision AND s.valid_from<=@date AND s.valid_to_exclusive>@date
 AND NOT EXISTS(SELECT 1 FROM ref.reference_release_revocation rev WHERE rev.reference_release_id=s.reference_release_id AND rev.activation_scope=N'SHADOW')
);
GO
