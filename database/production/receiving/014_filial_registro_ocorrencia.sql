IF DB_NAME() <> N'ETL_SISTEMA_V2' OR NOT EXISTS (
    SELECT 1 FROM ctl.production_export_template
    WHERE entity_name='OCORRENCIAS' AND report_id=12072 AND expected_field_count=20
) THROW 52113,'OCCURRENCE_TEMPLATE_REQUIRED',1;

UPDATE ctl.production_export_field
SET field_name='ice_uer_crn_psn_nickname'
WHERE entity_name='OCORRENCIAS' AND field_name='ice_fit_fln_cln_nickname';
IF @@ROWCOUNT<>1 THROW 52114,'OCCURRENCE_PREVIOUS_FIELD_REQUIRED',1;
