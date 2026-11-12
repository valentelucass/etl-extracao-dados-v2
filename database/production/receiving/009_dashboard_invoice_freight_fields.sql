IF DB_NAME()<>N'ETL_SISTEMA_V2' THROW 52010,'DASHBOARD_REPORT_FIELDS_REQUIRE_V2',1;
IF NOT EXISTS (
    SELECT 1 FROM ctl.production_export_template
    WHERE entity_name='FATURAS_POR_CLIENTE' AND report_id=4924 AND expected_field_count=52
) OR NOT EXISTS (
    SELECT 1 FROM sys.sql_expression_dependencies
    WHERE referencing_id=OBJECT_ID('stg.usp_production_ingestion_store','P')
      AND referenced_id=OBJECT_ID('ctl.production_export_additional_field','U')
) THROW 52051,'DASHBOARD_INVOICE_FIELDS_REQUIRE_BASELINE',1;

INSERT ctl.production_export_additional_field(entity_name,field_name) VALUES
 ('FATURAS_POR_CLIENTE','created_at'),
 ('FATURAS_POR_CLIENTE','invoices_weight');
