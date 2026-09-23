-- Explicit logistics region release. CEP precedes normalized city/UF; no live reference import.
SET ANSI_NULLS ON;SET QUOTED_IDENTIFIER ON;
GO
CREATE TABLE ctl.analytic_collection_region_selection (
 run_id UNIQUEIDENTIFIER NOT NULL REFERENCES ctl.analytic_lab_run(run_id),revision INT NOT NULL,
 valid_from DATE NOT NULL,valid_to_exclusive DATE NOT NULL,reference_release_id BIGINT NOT NULL REFERENCES ref.reference_release(reference_release_id),
 normalization_version VARCHAR(32) NOT NULL,
 CONSTRAINT PK_ana_collection_region_selection PRIMARY KEY(run_id,revision,valid_from),
 CONSTRAINT CK_ana_collection_region_selection CHECK(revision BETWEEN 1 AND 100000 AND valid_from<valid_to_exclusive AND normalization_version='trim-upper-nfc-v1')
);
GO
CREATE TRIGGER ctl.trg_analytic_collection_region_selection ON ctl.analytic_collection_region_selection AFTER INSERT,UPDATE,DELETE AS
BEGIN
 SET NOCOUNT ON;
 IF EXISTS(SELECT 1 FROM deleted) THROW 53760,N'ANA_LOGISTICS_REGION_SELECTION_IMMUTABLE',1;
 IF EXISTS(SELECT 1 FROM inserted i LEFT JOIN ref.reference_release r ON r.reference_release_id=i.reference_release_id
 LEFT JOIN ref.reference_import_receipt seal ON seal.reference_release_id=r.reference_release_id
 LEFT JOIN ref.reference_release_ratification rat ON rat.reference_release_id=r.reference_release_id AND rat.activation_scope=N'SHADOW'
 WHERE r.family_code<>N'LOGISTICS_REGION' OR r.source_kind<>N'DETERMINISTIC_LOCAL'
 OR r.scope_code<>CONCAT(N'SYNTHETIC_REGION_LAB:',CONVERT(NVARCHAR(36),i.run_id),N':R',i.revision) COLLATE Latin1_General_100_BIN2
 OR seal.reference_release_id IS NULL OR rat.reference_release_id IS NULL OR i.valid_from<r.valid_from OR i.valid_to_exclusive>r.valid_to_exclusive
 OR EXISTS(SELECT 1 FROM ref.reference_release_revocation rev WHERE rev.reference_release_id=r.reference_release_id AND rev.activation_scope=N'SHADOW'))
 THROW 53761,N'ANA_LOGISTICS_REGION_SELECTION_SCOPE',1;
 IF EXISTS(SELECT 1 FROM inserted i JOIN ctl.analytic_collection_region_selection x WITH(UPDLOCK,HOLDLOCK)
 ON x.run_id=i.run_id AND x.revision=i.revision AND x.valid_from<>i.valid_from
 AND x.valid_from<i.valid_to_exclusive AND i.valid_from<x.valid_to_exclusive) THROW 53762,N'ANA_LOGISTICS_REGION_SELECTION_OVERLAP',1;
END;
GO
CREATE TYPE ref.analytic_collection_region_batch AS TABLE (
 kind VARCHAR(4) NOT NULL,key1 NVARCHAR(128) COLLATE Latin1_General_100_BIN2 NOT NULL,key2 NVARCHAR(128) COLLATE Latin1_General_100_BIN2 NOT NULL,
 region_code NVARCHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL,priority SMALLINT NOT NULL,
 PRIMARY KEY(kind,key1,key2)
);
GO
CREATE PROCEDURE ref.usp_import_analytic_collection_regions @run_id UNIQUEIDENTIFIER,@revision INT,@start DATE,@end DATE,
 @fingerprint CHAR(64),@fixture_bytes INT,@rows ref.analytic_collection_region_batch READONLY AS
BEGIN
 SET NOCOUNT ON;SET XACT_ABORT OFF;
 IF @@TRANCOUNT=0 OR @revision NOT BETWEEN 1 AND 100000 OR @fixture_bytes NOT BETWEEN 1 AND 32768
 OR @start>=@end OR DATEDIFF(day,@start,@end)>3660 OR (SELECT COUNT_BIG(*) FROM @rows) NOT BETWEEN 1 AND 100
 OR @fingerprint LIKE '%[^0-9a-f]%' OR NOT EXISTS(SELECT 1 FROM ctl.analytic_lab_run WHERE run_id=@run_id AND @start>=window_start AND @end<=window_end_exclusive)
 THROW 53763,N'ANA_LOGISTICS_REGION_IMPORT_BOUND',1;
 IF EXISTS(SELECT 1 FROM @rows WHERE kind NOT IN('CEP','CITY') OR priority NOT BETWEEN 1 AND 1000
 OR region_code NOT LIKE N'[A-Z]%' OR region_code LIKE N'%[^A-Z0-9_]%'
 OR (kind='CEP' AND(LEN(key1)<>8 OR LEN(key2)<>8 OR key1 LIKE N'%[^0-9]%' OR key2 LIKE N'%[^0-9]%' OR key1>key2))
 OR (kind='CITY' AND(LEN(key1)=0 OR DATALENGTH(key1)<>DATALENGTH(LTRIM(RTRIM(key1))) OR key1<>UPPER(key1)
 OR key2 NOT IN('AC','AL','AP','AM','BA','CE','DF','ES','GO','MA','MT','MS','MG','PA','PB','PR','PE','PI','RJ','RN','RS','RO','RR','SC','SP','SE','TO'))))
 THROW 53764,N'ANA_COLLECTION_REGION_IMPORT_ROW',1;
 EXEC ctl.usp_analytic_lab_lock @run_id,'DIMENSION';
 DECLARE @release BIGINT,@count BIGINT=(SELECT COUNT_BIG(*) FROM @rows),@outcome NVARCHAR(16),
 @scope NVARCHAR(128)=CONCAT(N'SYNTHETIC_REGION_LAB:',CONVERT(NVARCHAR(36),@run_id),N':R',@revision),
 @version NVARCHAR(64)=CONCAT(N'analytic-collection-region-v1-r',@revision);
 SELECT @release=reference_release_id FROM ctl.analytic_collection_region_selection WHERE run_id=@run_id AND revision=@revision AND valid_from=@start;
 IF @release IS NOT NULL
 BEGIN
  IF NOT EXISTS(SELECT 1 FROM ctl.analytic_collection_region_selection s JOIN ref.reference_release r ON r.reference_release_id=s.reference_release_id
  JOIN ref.reference_import_receipt receipt ON receipt.reference_release_id=r.reference_release_id
  WHERE s.run_id=@run_id AND s.revision=@revision AND s.valid_from=@start AND s.valid_to_exclusive=@end
  AND r.scope_code=@scope AND r.source_fingerprint=@fingerprint AND r.source_row_count=@count AND receipt.imported_byte_count=@fixture_bytes)
  OR EXISTS(SELECT kind,key1,key2,region_code,priority FROM @rows EXCEPT SELECT * FROM (SELECT 'CEP' kind,CONVERT(NVARCHAR(128),cep_start) key1,CONVERT(NVARCHAR(128),cep_end) key2,region_code,priority FROM ref.regiao_logistica_cep WHERE reference_release_id=@release UNION ALL SELECT 'CITY',normalized_city,CONVERT(NVARCHAR(128),uf),region_code,priority FROM ref.regiao_logistica_cidade_uf WHERE reference_release_id=@release) actual)
  OR EXISTS(SELECT * FROM (SELECT 'CEP' kind,CONVERT(NVARCHAR(128),cep_start) key1,CONVERT(NVARCHAR(128),cep_end) key2,region_code,priority FROM ref.regiao_logistica_cep WHERE reference_release_id=@release UNION ALL SELECT 'CITY',normalized_city,CONVERT(NVARCHAR(128),uf),region_code,priority FROM ref.regiao_logistica_cidade_uf WHERE reference_release_id=@release) actual EXCEPT SELECT kind,key1,key2,region_code,priority FROM @rows)
  THROW 53765,N'ANA_LOGISTICS_REGION_RETRY_DIVERGENT',1;
  SELECT @release reference_release_id,@count rows_imported;RETURN;
 END;
 IF EXISTS(SELECT 1 FROM ctl.analytic_collection_region_selection WHERE run_id=@run_id AND revision=@revision
 AND valid_from<@end AND @start<valid_to_exclusive) THROW 53762,N'ANA_LOGISTICS_REGION_SELECTION_OVERLAP',1;
 EXEC ref.usp_register_reference_release N'LOGISTICS_REGION',@scope,@version,1,N'DETERMINISTIC_LOCAL',N'ANALYTIC_REGION_FIXTURE_V1',
 @fingerprint,@count,@start,@end,N'SYNTHETIC_LABORATORY_ONLY',N'SYNTHETIC_REGION_AUTHOR',@release OUTPUT,@outcome OUTPUT;
 IF @outcome<>N'CREATED' THROW 53766,N'ANA_LOGISTICS_REGION_ORPHAN_RELEASE',1;
 INSERT ref.regiao_logistica_cep
 SELECT @release,N'LOGISTICS_REGION',CONVERT(CHAR(8),key1),CONVERT(CHAR(8),key2),region_code,priority,@start,@end,N'SYNTHETIC_LABORATORY_ONLY' FROM @rows WHERE kind='CEP';
 INSERT ref.regiao_logistica_cidade_uf
 SELECT @release,N'LOGISTICS_REGION',key1,N'trim-upper-nfc-v1',CONVERT(CHAR(2),key2),region_code,priority,@start,@end,N'SYNTHETIC_LABORATORY_ONLY' FROM @rows WHERE kind='CITY';
 INSERT ref.reference_import_receipt(reference_release_id,contract_version,content_fingerprint,imported_row_count,imported_byte_count,importer_role)
 VALUES(@release,N'governed-references-v1',@fingerprint,@count,@fixture_bytes,N'SYNTHETIC_REGION_IMPORTER');
 INSERT ref.reference_release_ratification(reference_release_id,activation_scope,approver_role,approval_evidence_ref,approval_fingerprint)
 VALUES(@release,N'SHADOW',N'SYNTHETIC_REGION_APPROVER',N'ANALYTIC_FIXTURE_ONLY_ROLLBACK',@fingerprint);
 INSERT ctl.analytic_collection_region_selection VALUES(@run_id,@revision,@start,@end,@release,'trim-upper-nfc-v1');
 SELECT @release reference_release_id,@count rows_imported;
END;
GO
CREATE FUNCTION ref.ufn_analytic_collection_region(@run_id UNIQUEIDENTIFIER,@revision INT,@date DATE)
RETURNS TABLE AS RETURN (
 SELECT s.reference_release_id,s.valid_from,s.valid_to_exclusive,s.normalization_version
 FROM ctl.analytic_collection_region_selection s
 JOIN ref.reference_release r ON r.reference_release_id=s.reference_release_id AND r.family_code=N'LOGISTICS_REGION'
 JOIN ref.reference_release_ratification rat ON rat.reference_release_id=r.reference_release_id AND rat.activation_scope=N'SHADOW'
 WHERE s.run_id=@run_id AND s.revision=@revision AND s.valid_from<=@date AND s.valid_to_exclusive>@date
 AND NOT EXISTS(SELECT 1 FROM ref.reference_release_revocation rev WHERE rev.reference_release_id=s.reference_release_id AND rev.activation_scope=N'SHADOW')
);
GO
