IF (SELECT COUNT(*) FROM ctl.production_export_field WHERE entity_name='OCORRENCIAS')<>20
    OR NOT EXISTS (SELECT 1 FROM ctl.production_export_field WHERE entity_name='OCORRENCIAS' AND field_name='ice_uer_crn_psn_nickname')
    OR EXISTS (SELECT 1 FROM ctl.production_export_field WHERE entity_name='OCORRENCIAS' AND field_name='ice_fit_fln_cln_nickname')
    THROW 52115,'OCCURRENCE_REGISTRATION_BRANCH_CONTRACT_REQUIRED',1;
SELECT 12072 AS relatorio,20 AS campos,'ice_uer_crn_psn_nickname' AS filialRegistro FOR JSON PATH,WITHOUT_ARRAY_WRAPPER;
