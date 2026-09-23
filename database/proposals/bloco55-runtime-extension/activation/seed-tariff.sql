DECLARE @release BIGINT;
IF EXISTS(SELECT 1 FROM ref.reference_release WHERE scope_code=N'LOCAL_SHADOW/LOCAL_V2/LOCAL_V2/cotacoes' AND release_version=N'bloco55-tariff-v1') THROW 52852,N'TARIFF_ALREADY_EXISTS_RECONCILE',1;
INSERT ref.reference_release(family_code,scope_code,release_version,schema_version,source_kind,source_artifact_ref,source_fingerprint,source_row_count,valid_from,valid_to_exclusive,reason_code,author_role)
VALUES(N'QUOTE_TARIFF',N'LOCAL_SHADOW/LOCAL_V2/LOCAL_V2/cotacoes',N'bloco55-tariff-v1',1,N'DETERMINISTIC_LOCAL',N'B55_SYNTHETIC_TARIFF','dd60cb039196dfacdbf2a056929404b48ccd5601f1cdb36bb6b24dfa3eb4e101',2,'2018-01-01','2040-01-01',N'SYNTHETIC_LABORATORY_ONLY',N'LOCAL_LABORATORY_OWNER');
SET @release=SCOPE_IDENTITY();
INSERT ref.tarifa_rota_uf VALUES(@release,N'QUOTE_TARIFF','SP','RJ',N'PRICED',5,'BRL',N'PER_SHIPMENT',N'HALF_UP','2018-01-01','2040-01-01',N'SYNTHETIC_LABORATORY_ONLY'),
 (@release,N'QUOTE_TARIFF','RJ','SP',N'PRICED',7,'BRL',N'PER_SHIPMENT',N'HALF_UP','2018-01-01','2040-01-01',N'SYNTHETIC_LABORATORY_ONLY');
INSERT ref.reference_import_receipt(reference_release_id,contract_version,content_fingerprint,imported_row_count,imported_byte_count,importer_role)
VALUES(@release,N'governed-references-v1','dd60cb039196dfacdbf2a056929404b48ccd5601f1cdb36bb6b24dfa3eb4e101',2,591,N'SYNTHETIC_LABORATORY_IMPORTER');
INSERT ref.reference_release_ratification(reference_release_id,activation_scope,approver_role,approval_evidence_ref,approval_fingerprint)
VALUES(@release,N'SHADOW',N'SYNTHETIC_LABORATORY_APPROVER',N'B55_OWNER_ADOPTED_SECTION_3','dd60cb039196dfacdbf2a056929404b48ccd5601f1cdb36bb6b24dfa3eb4e101');