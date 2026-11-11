CREATE OR ALTER VIEW pub.vw_dim_motoristas
AS
SELECT DISTINCT CONVERT(nvarchar(255), UPPER(TRIM(j.driver_name COLLATE Latin1_General_CI_AS))) AS NomeMotorista,
       CONVERT(nvarchar(255), j.branch_name) COLLATE Latin1_General_CI_AS AS Filial
FROM stg.production_export_latest AS l
CROSS APPLY OPENJSON(l.source_envelope, '$.record') WITH (
    driver_name nvarchar(max) '$.mft_mdr_iil_name',
    branch_name nvarchar(max) '$.mft_crn_psn_nickname'
) AS j
WHERE l.entity_name = 'MANIFESTOS'
  AND TRIM(j.driver_name) <> N''
  AND j.driver_name COLLATE Latin1_General_CI_AS NOT LIKE N'%MOTORISTA%'
  AND (SELECT COUNT_BIG(DISTINCT source_scope)
       FROM ctl.production_ingestion_coverage WHERE entity_name = 'MANIFESTOS') = 1;
