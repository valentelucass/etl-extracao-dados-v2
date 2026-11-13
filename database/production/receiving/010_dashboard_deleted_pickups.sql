IF DB_NAME()<>N'ETL_SISTEMA_V2' THROW 52010,'DASHBOARD_REPORT_FIELDS_REQUIRE_V2',1;

INSERT ctl.production_export_template(entity_name,report_id,row_date_field,expected_field_count)
VALUES('COLETAS_EXCLUIDAS',12899,'deleted_at',10);

INSERT ctl.production_export_field(entity_name,field_name) VALUES
 ('COLETAS_EXCLUIDAS','id'),
 ('COLETAS_EXCLUIDAS','sequence_code'),
 ('COLETAS_EXCLUIDAS','request_date'),
 ('COLETAS_EXCLUIDAS','invoices_value'),
 ('COLETAS_EXCLUIDAS','taxed_weight'),
 ('COLETAS_EXCLUIDAS','deleted_at'),
 ('COLETAS_EXCLUIDAS','destroy_reason'),
 ('COLETAS_EXCLUIDAS','dek_dyr_name'),
 ('COLETAS_EXCLUIDAS','dek_crn_psn_id'),
 ('COLETAS_EXCLUIDAS','dek_crn_psn_nickname');
