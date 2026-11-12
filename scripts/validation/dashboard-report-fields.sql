SET NOCOUNT ON;
IF (SELECT COUNT(*) FROM ctl.production_export_additional_field WHERE entity_name='COLETAS')<>9
   OR (SELECT COUNT(*) FROM ctl.production_export_additional_field WHERE entity_name='FRETES')<>36
   OR (SELECT COUNT(*) FROM ctl.production_export_additional_field WHERE entity_name='FATURAS_POR_CLIENTE') NOT IN (0,2)
   OR EXISTS (SELECT 1 FROM ctl.production_export_additional_field
              WHERE entity_name NOT IN ('COLETAS','FRETES','FATURAS_POR_CLIENTE'))
   OR EXISTS (
       SELECT 1 FROM ctl.production_export_additional_field a
       JOIN ctl.production_export_field f ON f.entity_name=a.entity_name AND f.field_name=a.field_name
   ) THROW 52032,'DASHBOARD_REPORT_FIELD_LIST_INVALID',1;

IF (SELECT COUNT(*) FROM ctl.production_export_field WHERE entity_name='COLETAS')<>31
   OR (SELECT COUNT(*) FROM ctl.production_export_field WHERE entity_name='FRETES')<>114
   OR (SELECT COUNT(*) FROM ctl.production_export_field WHERE entity_name='FATURAS_POR_CLIENTE')<>52
   OR NOT EXISTS (
       SELECT 1 FROM sys.sql_expression_dependencies
       WHERE referencing_id=OBJECT_ID('stg.usp_production_ingestion_store','P')
         AND referenced_id=OBJECT_ID('ctl.production_export_additional_field','U')
   ) THROW 52033,'DASHBOARD_REPORT_BASELINE_OR_STORE_INVALID',1;

SELECT (
    SELECT JSON_QUERY((
       SELECT entity_name,field_name FROM ctl.production_export_additional_field ORDER BY entity_name,field_name FOR JSON PATH
    )) AS additionalFields FOR JSON PATH, WITHOUT_ARRAY_WRAPPER
);
