CREATE OR ALTER VIEW pub.vw_dim_veiculos
AS
SELECT CONVERT(nvarchar(10), UPPER(TRIM(j.plate COLLATE Latin1_General_CI_AS))) AS Placa,
       CONVERT(nvarchar(255), MAX(UPPER(TRIM(j.vehicle_type COLLATE Latin1_General_CI_AS)))) AS TipoVeiculo,
       CONVERT(nvarchar(255), MAX(UPPER(TRIM(j.vehicle_owner COLLATE Latin1_General_CI_AS)))) AS Proprietario,
       CONVERT(nvarchar(255), j.branch_name COLLATE Latin1_General_CI_AS) AS Filial
FROM stg.production_export_latest AS l
CROSS APPLY OPENJSON(l.source_envelope, '$.record') WITH (
    plate nvarchar(max) '$.mft_vie_license_plate',
    vehicle_type nvarchar(max) '$.mft_vie_vee_name',
    vehicle_owner nvarchar(max) '$.mft_vie_onr_name',
    branch_name nvarchar(max) '$.mft_crn_psn_nickname'
) AS j
WHERE l.entity_name = 'MANIFESTOS' AND TRIM(j.plate) <> N''
  AND (SELECT COUNT_BIG(DISTINCT source_scope)
       FROM ctl.production_ingestion_coverage WHERE entity_name = 'MANIFESTOS') = 1
GROUP BY UPPER(TRIM(j.plate COLLATE Latin1_General_CI_AS)), j.branch_name COLLATE Latin1_General_CI_AS;
