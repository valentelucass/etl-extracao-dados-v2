SET XACT_ABORT ON;
IF DB_NAME() <> N'ETL_SISTEMA_V2' THROW 52012, 'DELIVERY_ARCHIVE_REQUIRE_V2', 1;

BEGIN TRANSACTION;

DECLARE @row_date_check sysname;
SELECT @row_date_check = name
FROM sys.check_constraints
WHERE parent_object_id = OBJECT_ID(N'ctl.production_export_template')
  AND definition = N'([row_date_field] IS NOT NULL OR [entity_name]=''MANIFESTOS'')'
  AND is_disabled = 0
  AND is_not_trusted = 0;

IF @row_date_check IS NULL
   OR EXISTS (SELECT 1 FROM ctl.production_export_template
              WHERE entity_name = 'COMPROVANTES_ENTREGA' OR report_id = 11361)
    THROW 52013, 'DELIVERY_ARCHIVE_CATALOG_PRECONDITION', 1;

DECLARE @drop nvarchar(500) = N'ALTER TABLE ctl.production_export_template DROP CONSTRAINT '
                            + QUOTENAME(@row_date_check) + N';';
EXEC sys.sp_executesql @drop;

-- The ESL filters these two reports by a source date absent from their output rows.
ALTER TABLE ctl.production_export_template WITH CHECK
ADD CONSTRAINT CK_production_export_template_row_date
CHECK (row_date_field IS NOT NULL OR entity_name IN ('MANIFESTOS', 'COMPROVANTES_ENTREGA'));

INSERT ctl.production_export_template (entity_name, report_id, row_date_field, expected_field_count)
VALUES ('COMPROVANTES_ENTREGA', 11361, NULL, 23);

INSERT ctl.production_export_field (entity_name, field_name) VALUES
 ('COMPROVANTES_ENTREGA', 'fag_crn_psn_nickname'),
 ('COMPROVANTES_ENTREGA', 'sequence_code'),
 ('COMPROVANTES_ENTREGA', 'name'),
 ('COMPROVANTES_ENTREGA', 'status'),
 ('COMPROVANTES_ENTREGA', 'fag_fhs_fit_crn_psn_nickname'),
 ('COMPROVANTES_ENTREGA', 'fag_fhs_fit_pyr_nickname'),
 ('COMPROVANTES_ENTREGA', 'fag_fhs_fit_corporation_sequence_number'),
 ('COMPROVANTES_ENTREGA', 'fag_fhs_fit_fhe_cte_number'),
 ('COMPROVANTES_ENTREGA', 'fag_fhs_fit_invoices_mapping'),
 ('COMPROVANTES_ENTREGA', 'fag_fhs_fte_has_delivery_receipt'),
 ('COMPROVANTES_ENTREGA', 'fag_fhs_fit_d_t_created_at'),
 ('COMPROVANTES_ENTREGA', 'fag_fhs_fit_dyn_drt_nickname'),
 ('COMPROVANTES_ENTREGA', 'fag_fhs_fit_fln_cln_nickname'),
 ('COMPROVANTES_ENTREGA', 'fag_fhs_fit_fln_updated_at'),
 ('COMPROVANTES_ENTREGA', 'fag_fhs_fit_status'),
 ('COMPROVANTES_ENTREGA', 'fag_fhs_fit_emission_type'),
 ('COMPROVANTES_ENTREGA', 'fag_fhs_fit_service_type'),
 ('COMPROVANTES_ENTREGA', 'fag_fhs_fit_operation_type'),
 ('COMPROVANTES_ENTREGA', 'fag_fhs_fit_type'),
 ('COMPROVANTES_ENTREGA', 'fag_fhs_fit_fte_flt_ron_sequence_code'),
 ('COMPROVANTES_ENTREGA', 'fag_fhs_fit_fte_flt_ron_document_number'),
 ('COMPROVANTES_ENTREGA', 'fag_fhs_fit_fte_llt_ron_sequence_code'),
 ('COMPROVANTES_ENTREGA', 'fag_fhs_fit_fte_llt_ron_document_number');

COMMIT TRANSACTION;
