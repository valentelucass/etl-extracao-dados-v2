-- Audit queries retain candidate/confirmed absence. Only the separately guarded Sweep application writes absence.
SET ANSI_NULLS ON;SET QUOTED_IDENTIFIER ON;
GO
CREATE INDEX IX_ana_collection_supplement_effective ON stg.analytic_collection_supplement(run_id,source_key,source_execution,revision DESC);
GO
CREATE TABLE recon.analytic_collection_absence (
 run_id UNIQUEIDENTIFIER NOT NULL,source_key NVARCHAR(128) COLLATE Latin1_General_100_BIN2 NOT NULL,
 absent_since DATETIME2(7) NOT NULL,confirmations TINYINT NOT NULL,confirmed BIT NOT NULL,excluded_at DATETIME2(7) NULL,
 reason VARCHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL,first_cycle UNIQUEIDENTIFIER NOT NULL,last_cycle UNIQUEIDENTIFIER NOT NULL,
 last_reconciled_at DATETIME2(7) NOT NULL,last_capture UNIQUEIDENTIFIER NOT NULL REFERENCES ctl.analytic_collection_preparation(execution_id),
 CONSTRAINT PK_ana_collection_absence PRIMARY KEY(run_id,source_key),
 CONSTRAINT FK_ana_collection_absence_root FOREIGN KEY(run_id,source_key) REFERENCES core.analytic_collection_current(run_id,source_key),
 CONSTRAINT CK_ana_collection_absence CHECK(reason='SYNTHETIC_COMPLETE_SNAPSHOT_ABSENCE' AND
 ((confirmations=1 AND confirmed=0 AND excluded_at IS NULL AND first_cycle=last_cycle) OR(confirmations=2 AND confirmed=1 AND excluded_at IS NOT NULL AND first_cycle<>last_cycle)))
);
GO
CREATE VIEW core.analytic_collection_supplement_effective AS
SELECT c.run_id,c.source_key,c.snapshot_id,f0.[request_hour],f0.[request_hour_p],f0.[request_hour_w],f0.[request_hour_raw],f0.supplement_id [request_hour_supplement_id],f1.[vehicle_type_id],f1.[vehicle_type_id_p],f1.[vehicle_type_id_w],f1.[vehicle_type_id_raw],f1.supplement_id [vehicle_type_id_supplement_id],f2.[customer_name],f2.[customer_name_p],f2.[customer_name_w],f2.[customer_name_raw],f2.supplement_id [customer_name_supplement_id],f3.[customer_document],f3.[customer_document_p],f3.[customer_document_w],f3.[customer_document_raw],f3.supplement_id [customer_document_supplement_id],f4.[address_line],f4.[address_line_p],f4.[address_line_w],f4.[address_line_raw],f4.supplement_id [address_line_supplement_id],f5.[address_number],f5.[address_number_p],f5.[address_number_w],f5.[address_number_raw],f5.supplement_id [address_number_supplement_id],f6.[address_complement],f6.[address_complement_p],f6.[address_complement_w],f6.[address_complement_raw],f6.supplement_id [address_complement_supplement_id],f7.[branch_source_id],f7.[branch_source_id_p],f7.[branch_source_id_w],f7.[branch_source_id_raw],f7.supplement_id [branch_source_id_supplement_id],f8.[cancellation_user_id],f8.[cancellation_user_id_p],f8.[cancellation_user_id_w],f8.[cancellation_user_id_raw],f8.supplement_id [cancellation_user_id_supplement_id],f8.cancellation_user_key,f9.[destroy_reason],f9.[destroy_reason_p],f9.[destroy_reason_w],f9.[destroy_reason_raw],f9.supplement_id [destroy_reason_supplement_id],f10.[destroy_user_id],f10.[destroy_user_id_p],f10.[destroy_user_id_w],f10.[destroy_user_id_raw],f10.supplement_id [destroy_user_id_supplement_id],f10.destroy_user_key,f11.[status_updated_at],f11.[status_updated_at_p],f11.[status_updated_at_w],f11.[status_updated_at_raw],f11.supplement_id [status_updated_at_supplement_id],f11.status_updated_at_nano
FROM core.analytic_collection_current c
OUTER APPLY(SELECT TOP(1) a.* FROM stg.analytic_collection_supplement a
 JOIN core.analytic_collection_snapshot s ON s.run_id=a.run_id AND s.source_key=a.source_key AND s.source_execution=a.source_execution
 WHERE a.run_id=c.run_id AND a.source_key=c.source_key AND s.snapshot_id<=c.snapshot_id AND a.[request_hour_p]<>'ABSENT'
 ORDER BY s.snapshot_id DESC,a.revision DESC) f0
OUTER APPLY(SELECT TOP(1) a.* FROM stg.analytic_collection_supplement a
 JOIN core.analytic_collection_snapshot s ON s.run_id=a.run_id AND s.source_key=a.source_key AND s.source_execution=a.source_execution
 WHERE a.run_id=c.run_id AND a.source_key=c.source_key AND s.snapshot_id<=c.snapshot_id AND a.[vehicle_type_id_p]<>'ABSENT'
 ORDER BY s.snapshot_id DESC,a.revision DESC) f1
OUTER APPLY(SELECT TOP(1) a.* FROM stg.analytic_collection_supplement a
 JOIN core.analytic_collection_snapshot s ON s.run_id=a.run_id AND s.source_key=a.source_key AND s.source_execution=a.source_execution
 WHERE a.run_id=c.run_id AND a.source_key=c.source_key AND s.snapshot_id<=c.snapshot_id AND a.[customer_name_p]<>'ABSENT'
 ORDER BY s.snapshot_id DESC,a.revision DESC) f2
OUTER APPLY(SELECT TOP(1) a.* FROM stg.analytic_collection_supplement a
 JOIN core.analytic_collection_snapshot s ON s.run_id=a.run_id AND s.source_key=a.source_key AND s.source_execution=a.source_execution
 WHERE a.run_id=c.run_id AND a.source_key=c.source_key AND s.snapshot_id<=c.snapshot_id AND a.[customer_document_p]<>'ABSENT'
 ORDER BY s.snapshot_id DESC,a.revision DESC) f3
OUTER APPLY(SELECT TOP(1) a.* FROM stg.analytic_collection_supplement a
 JOIN core.analytic_collection_snapshot s ON s.run_id=a.run_id AND s.source_key=a.source_key AND s.source_execution=a.source_execution
 WHERE a.run_id=c.run_id AND a.source_key=c.source_key AND s.snapshot_id<=c.snapshot_id AND a.[address_line_p]<>'ABSENT'
 ORDER BY s.snapshot_id DESC,a.revision DESC) f4
OUTER APPLY(SELECT TOP(1) a.* FROM stg.analytic_collection_supplement a
 JOIN core.analytic_collection_snapshot s ON s.run_id=a.run_id AND s.source_key=a.source_key AND s.source_execution=a.source_execution
 WHERE a.run_id=c.run_id AND a.source_key=c.source_key AND s.snapshot_id<=c.snapshot_id AND a.[address_number_p]<>'ABSENT'
 ORDER BY s.snapshot_id DESC,a.revision DESC) f5
OUTER APPLY(SELECT TOP(1) a.* FROM stg.analytic_collection_supplement a
 JOIN core.analytic_collection_snapshot s ON s.run_id=a.run_id AND s.source_key=a.source_key AND s.source_execution=a.source_execution
 WHERE a.run_id=c.run_id AND a.source_key=c.source_key AND s.snapshot_id<=c.snapshot_id AND a.[address_complement_p]<>'ABSENT'
 ORDER BY s.snapshot_id DESC,a.revision DESC) f6
OUTER APPLY(SELECT TOP(1) a.* FROM stg.analytic_collection_supplement a
 JOIN core.analytic_collection_snapshot s ON s.run_id=a.run_id AND s.source_key=a.source_key AND s.source_execution=a.source_execution
 WHERE a.run_id=c.run_id AND a.source_key=c.source_key AND s.snapshot_id<=c.snapshot_id AND a.[branch_source_id_p]<>'ABSENT'
 ORDER BY s.snapshot_id DESC,a.revision DESC) f7
OUTER APPLY(SELECT TOP(1) a.* FROM stg.analytic_collection_supplement a
 JOIN core.analytic_collection_snapshot s ON s.run_id=a.run_id AND s.source_key=a.source_key AND s.source_execution=a.source_execution
 WHERE a.run_id=c.run_id AND a.source_key=c.source_key AND s.snapshot_id<=c.snapshot_id AND a.[cancellation_user_id_p]<>'ABSENT'
 ORDER BY s.snapshot_id DESC,a.revision DESC) f8
OUTER APPLY(SELECT TOP(1) a.* FROM stg.analytic_collection_supplement a
 JOIN core.analytic_collection_snapshot s ON s.run_id=a.run_id AND s.source_key=a.source_key AND s.source_execution=a.source_execution
 WHERE a.run_id=c.run_id AND a.source_key=c.source_key AND s.snapshot_id<=c.snapshot_id AND a.[destroy_reason_p]<>'ABSENT'
 ORDER BY s.snapshot_id DESC,a.revision DESC) f9
OUTER APPLY(SELECT TOP(1) a.* FROM stg.analytic_collection_supplement a
 JOIN core.analytic_collection_snapshot s ON s.run_id=a.run_id AND s.source_key=a.source_key AND s.source_execution=a.source_execution
 WHERE a.run_id=c.run_id AND a.source_key=c.source_key AND s.snapshot_id<=c.snapshot_id AND a.[destroy_user_id_p]<>'ABSENT'
 ORDER BY s.snapshot_id DESC,a.revision DESC) f10
OUTER APPLY(SELECT TOP(1) a.* FROM stg.analytic_collection_supplement a
 JOIN core.analytic_collection_snapshot s ON s.run_id=a.run_id AND s.source_key=a.source_key AND s.source_execution=a.source_execution
 WHERE a.run_id=c.run_id AND a.source_key=c.source_key AND s.snapshot_id<=c.snapshot_id AND a.[status_updated_at_p]<>'ABSENT'
 ORDER BY s.snapshot_id DESC,a.revision DESC) f11;
GO
CREATE VIEW core.analytic_lab_collection_projection AS
SELECT s.id [ID],s.sequence_code [Coleta],s.request_date [Solicitacao],
 CONVERT(TIME(0),DATEADD(SECOND,CONVERT(INT,COALESCE(l.request_hour,0)/1000000000),CONVERT(DATETIME2(0),'19000101'))) [Hora (Solicitacao)],
 s.service_date [Agendamento],s.finish_date [Finalizacao],
 CASE WHEN absence.confirmations>=1 THEN N'Excluída' ELSE COALESCE(s.status_label,s.status) END [Status],
 CONVERT(BIT,COALESCE(absence.confirmed,0)) [Excluída na Origem],s.invoices_volumes [Volumes],s.invoices_weight [Peso Real],s.taxed_weight [Peso Taxado],s.invoices_value [Valor NF],
 manifest.sequence_code [Numero Manifesto],TRY_CONVERT(BIGINT,l.vehicle_type_id) [Veiculo],l.customer_name [Cliente],l.customer_document [Cliente Doc],
 l.address_line [Local da Coleta],l.address_number [Numero],l.address_complement [Complemento],s.pck_pds_cty_name [Cidade],s.pck_pds_neighborhood [Bairro],s.pck_pds_cty_sae_code [UF],s.pck_pds_postal_code [CEP],
 CASE WHEN s.pck_pds_cty_name IS NOT NULL AND s.pck_pds_cty_sae_code IS NOT NULL THEN CONCAT(s.pck_pds_cty_name,N' - ',s.pck_pds_cty_sae_code) ELSE COALESCE(s.pck_pds_cty_name,s.pck_pds_cty_sae_code) END [Região da Coleta],
 COALESCE(cep.region_code,city.region_code,CONCAT(COALESCE(clean.city,N'Sem cidade'),N' - ',COALESCE(clean.uf,N'Sem UF'))) [Região Logística],
 TRY_CONVERT(BIGINT,l.branch_source_id) [Filial ID],branch.raw_name [Filial],s.pck_uer_name [Usuario],s.cancellation_reason [Motivo Cancel.],
 TRY_CONVERT(BIGINT,l.cancellation_user_id) [Usuario Cancel. ID],COALESCE(cancelUser.usuario_name,CONVERT(NVARCHAR(40),l.cancellation_user_id)) [Usuario Cancel. Nome],
 l.destroy_reason [Motivo Exclusao],TRY_CONVERT(BIGINT,l.destroy_user_id) [Usuario Exclusao ID],COALESCE(destroyUser.usuario_name,CONVERT(NVARCHAR(40),l.destroy_user_id)) [Usuario Exclusao Nome],
 JSON_VALUE(original.payload_json,'$.status_updated_at') [Status Atualizado Em],
 CONVERT(DATETIME2(7),core.ufn_analytic_raster_time(s.fresh_second,s.fresh_nano,0)) [Status Atualizado Em (normalizado)],
 s.status_label [Última Ocorrência],s.occurrence_action [Ação da Ocorrência],s.attempt_count [Nº Tentativas],
 (SELECT s.snapshot_id,s.previous_snapshot,s.source_execution,s.representative_stage,
 a.[id_p] [dataExport.id.presence],a.[id_w] [dataExport.id.wire],a.[id_raw] [dataExport.id.raw],a.[pck_crn_psn_nickname_p] [dataExport.pck_crn_psn_nickname.presence],a.[pck_crn_psn_nickname_w] [dataExport.pck_crn_psn_nickname.wire],a.[pck_crn_psn_nickname_raw] [dataExport.pck_crn_psn_nickname.raw],a.[sequence_code_p] [dataExport.sequence_code.presence],a.[sequence_code_w] [dataExport.sequence_code.wire],a.[sequence_code_raw] [dataExport.sequence_code.raw],a.[created_at_p] [dataExport.created_at.presence],a.[created_at_w] [dataExport.created_at.wire],a.[created_at_raw] [dataExport.created_at.raw],a.[request_date_p] [dataExport.request_date.presence],a.[request_date_w] [dataExport.request_date.wire],a.[request_date_raw] [dataExport.request_date.raw],a.[service_date_p] [dataExport.service_date.presence],a.[service_date_w] [dataExport.service_date.wire],a.[service_date_raw] [dataExport.service_date.raw],a.[finish_date_p] [dataExport.finish_date.presence],a.[finish_date_w] [dataExport.finish_date.wire],a.[finish_date_raw] [dataExport.finish_date.raw],a.[pck_cor_nickname_p] [dataExport.pck_cor_nickname.presence],a.[pck_cor_nickname_w] [dataExport.pck_cor_nickname.wire],a.[pck_cor_nickname_raw] [dataExport.pck_cor_nickname.raw],a.[invoices_volumes_p] [dataExport.invoices_volumes.presence],a.[invoices_volumes_w] [dataExport.invoices_volumes.wire],a.[invoices_volumes_raw] [dataExport.invoices_volumes.raw],a.[invoices_weight_p] [dataExport.invoices_weight.presence],a.[invoices_weight_w] [dataExport.invoices_weight.wire],a.[invoices_weight_raw] [dataExport.invoices_weight.raw],a.[taxed_weight_p] [dataExport.taxed_weight.presence],a.[taxed_weight_w] [dataExport.taxed_weight.wire],a.[taxed_weight_raw] [dataExport.taxed_weight.raw],a.[invoices_value_p] [dataExport.invoices_value.presence],a.[invoices_value_w] [dataExport.invoices_value.wire],a.[invoices_value_raw] [dataExport.invoices_value.raw],a.[status_p] [dataExport.status.presence],a.[status_w] [dataExport.status.wire],a.[status_raw] [dataExport.status.raw],a.[pck_pds_cty_name_p] [dataExport.pck_pds_cty_name.presence],a.[pck_pds_cty_name_w] [dataExport.pck_pds_cty_name.wire],a.[pck_pds_cty_name_raw] [dataExport.pck_pds_cty_name.raw],a.[pck_pds_cty_sae_code_p] [dataExport.pck_pds_cty_sae_code.presence],a.[pck_pds_cty_sae_code_w] [dataExport.pck_pds_cty_sae_code.wire],a.[pck_pds_cty_sae_code_raw] [dataExport.pck_pds_cty_sae_code.raw],a.[pck_pds_postal_code_p] [dataExport.pck_pds_postal_code.presence],a.[pck_pds_postal_code_w] [dataExport.pck_pds_postal_code.wire],a.[pck_pds_postal_code_raw] [dataExport.pck_pds_postal_code.raw],a.[pck_pds_neighborhood_p] [dataExport.pck_pds_neighborhood.presence],a.[pck_pds_neighborhood_w] [dataExport.pck_pds_neighborhood.wire],a.[pck_pds_neighborhood_raw] [dataExport.pck_pds_neighborhood.raw],a.[pck_prn_name_p] [dataExport.pck_prn_name.presence],a.[pck_prn_name_w] [dataExport.pck_prn_name.wire],a.[pck_prn_name_raw] [dataExport.pck_prn_name.raw],a.[pck_prn_drt_nickname_p] [dataExport.pck_prn_drt_nickname.presence],a.[pck_prn_drt_nickname_w] [dataExport.pck_prn_drt_nickname.wire],a.[pck_prn_drt_nickname_raw] [dataExport.pck_prn_drt_nickname.raw],a.[updated_at_p] [dataExport.updated_at.presence],a.[updated_at_w] [dataExport.updated_at.wire],a.[updated_at_raw] [dataExport.updated_at.raw],a.[pck_loe_ore_description_p] [dataExport.pck_loe_ore_description.presence],a.[pck_loe_ore_description_w] [dataExport.pck_loe_ore_description.wire],a.[pck_loe_ore_description_raw] [dataExport.pck_loe_ore_description.raw],a.[pck_pus_ore_description_p] [dataExport.pck_pus_ore_description.presence],a.[pck_pus_ore_description_w] [dataExport.pck_pus_ore_description.wire],a.[pck_pus_ore_description_raw] [dataExport.pck_pus_ore_description.raw],a.[pck_pus_ore_trigger_p] [dataExport.pck_pus_ore_trigger.presence],a.[pck_pus_ore_trigger_w] [dataExport.pck_pus_ore_trigger.wire],a.[pck_pus_ore_trigger_raw] [dataExport.pck_pus_ore_trigger.raw],a.[pck_uer_name_p] [dataExport.pck_uer_name.presence],a.[pck_uer_name_w] [dataExport.pck_uer_name.wire],a.[pck_uer_name_raw] [dataExport.pck_uer_name.raw],a.[pck_ctr_name_p] [dataExport.pck_ctr_name.presence],a.[pck_ctr_name_w] [dataExport.pck_ctr_name.wire],a.[pck_ctr_name_raw] [dataExport.pck_ctr_name.raw],a.[cancellation_reason_p] [dataExport.cancellation_reason.presence],a.[cancellation_reason_w] [dataExport.cancellation_reason.wire],a.[cancellation_reason_raw] [dataExport.cancellation_reason.raw],a.[pck_mik_attempt_number_p] [dataExport.pck_mik_attempt_number.presence],a.[pck_mik_attempt_number_w] [dataExport.pck_mik_attempt_number.wire],a.[pck_mik_attempt_number_raw] [dataExport.pck_mik_attempt_number.raw],a.[pck_mik_mft_crn_psn_nickname_p] [dataExport.pck_mik_mft_crn_psn_nickname.presence],a.[pck_mik_mft_crn_psn_nickname_w] [dataExport.pck_mik_mft_crn_psn_nickname.wire],a.[pck_mik_mft_crn_psn_nickname_raw] [dataExport.pck_mik_mft_crn_psn_nickname.raw],a.[pck_mik_mft_sequence_code_p] [dataExport.pck_mik_mft_sequence_code.presence],a.[pck_mik_mft_sequence_code_w] [dataExport.pck_mik_mft_sequence_code.wire],a.[pck_mik_mft_sequence_code_raw] [dataExport.pck_mik_mft_sequence_code.raw],a.[pck_mik_mft_vie_license_plate_p] [dataExport.pck_mik_mft_vie_license_plate.presence],a.[pck_mik_mft_vie_license_plate_w] [dataExport.pck_mik_mft_vie_license_plate.wire],a.[pck_mik_mft_vie_license_plate_raw] [dataExport.pck_mik_mft_vie_license_plate.raw],a.[pck_mik_mft_vie_vee_name_p] [dataExport.pck_mik_mft_vie_vee_name.presence],a.[pck_mik_mft_vie_vee_name_w] [dataExport.pck_mik_mft_vie_vee_name.wire],a.[pck_mik_mft_vie_vee_name_raw] [dataExport.pck_mik_mft_vie_vee_name.raw],
 l.[request_hour_p] [graphqlLateral.request_hour.presence],l.[request_hour_w] [graphqlLateral.request_hour.wire],l.[request_hour_raw] [graphqlLateral.request_hour.raw],l.[request_hour_supplement_id] [graphqlLateral.request_hour.supplementId],l.[vehicle_type_id_p] [graphqlLateral.vehicle_type_id.presence],l.[vehicle_type_id_w] [graphqlLateral.vehicle_type_id.wire],l.[vehicle_type_id_raw] [graphqlLateral.vehicle_type_id.raw],l.[vehicle_type_id_supplement_id] [graphqlLateral.vehicle_type_id.supplementId],l.[customer_name_p] [graphqlLateral.customer_name.presence],l.[customer_name_w] [graphqlLateral.customer_name.wire],l.[customer_name_raw] [graphqlLateral.customer_name.raw],l.[customer_name_supplement_id] [graphqlLateral.customer_name.supplementId],l.[customer_document_p] [graphqlLateral.customer_document.presence],l.[customer_document_w] [graphqlLateral.customer_document.wire],l.[customer_document_raw] [graphqlLateral.customer_document.raw],l.[customer_document_supplement_id] [graphqlLateral.customer_document.supplementId],l.[address_line_p] [graphqlLateral.address_line.presence],l.[address_line_w] [graphqlLateral.address_line.wire],l.[address_line_raw] [graphqlLateral.address_line.raw],l.[address_line_supplement_id] [graphqlLateral.address_line.supplementId],l.[address_number_p] [graphqlLateral.address_number.presence],l.[address_number_w] [graphqlLateral.address_number.wire],l.[address_number_raw] [graphqlLateral.address_number.raw],l.[address_number_supplement_id] [graphqlLateral.address_number.supplementId],l.[address_complement_p] [graphqlLateral.address_complement.presence],l.[address_complement_w] [graphqlLateral.address_complement.wire],l.[address_complement_raw] [graphqlLateral.address_complement.raw],l.[address_complement_supplement_id] [graphqlLateral.address_complement.supplementId],l.[branch_source_id_p] [graphqlLateral.branch_source_id.presence],l.[branch_source_id_w] [graphqlLateral.branch_source_id.wire],l.[branch_source_id_raw] [graphqlLateral.branch_source_id.raw],l.[branch_source_id_supplement_id] [graphqlLateral.branch_source_id.supplementId],l.[cancellation_user_id_p] [graphqlLateral.cancellation_user_id.presence],l.[cancellation_user_id_w] [graphqlLateral.cancellation_user_id.wire],l.[cancellation_user_id_raw] [graphqlLateral.cancellation_user_id.raw],l.[cancellation_user_id_supplement_id] [graphqlLateral.cancellation_user_id.supplementId],l.[destroy_reason_p] [graphqlLateral.destroy_reason.presence],l.[destroy_reason_w] [graphqlLateral.destroy_reason.wire],l.[destroy_reason_raw] [graphqlLateral.destroy_reason.raw],l.[destroy_reason_supplement_id] [graphqlLateral.destroy_reason.supplementId],l.[destroy_user_id_p] [graphqlLateral.destroy_user_id.presence],l.[destroy_user_id_w] [graphqlLateral.destroy_user_id.wire],l.[destroy_user_id_raw] [graphqlLateral.destroy_user_id.raw],l.[destroy_user_id_supplement_id] [graphqlLateral.destroy_user_id.supplementId],l.[status_updated_at_p] [graphqlLateral.status_updated_at.presence],l.[status_updated_at_w] [graphqlLateral.status_updated_at.wire],l.[status_updated_at_raw] [graphqlLateral.status_updated_at.raw],l.[status_updated_at_supplement_id] [graphqlLateral.status_updated_at.supplementId],
 JSON_QUERY((SELECT lineage.stage_record_id FROM core.analytic_collection_lineage lineage WHERE lineage.snapshot_id=s.snapshot_id ORDER BY lineage.stage_record_id FOR JSON PATH)) [lineage],
 manifest.source_key [manifest.sourceKey],manifest.link_id [manifest.linkId],regions.reference_release_id [regionRelease],branch.binding_id [branchBinding]
 FOR JSON PATH,INCLUDE_NULL_VALUES,WITHOUT_ARRAY_WRAPPER) [Metadata],
 pointer.latest_extracted_at [Data de extracao],
 pointer.run_id,pointer.source_key,s.snapshot_id,s.source_execution,pointer.last_observation,s.business_date,
 selection.revision reference_revision,selection.reference_release_id,regions.reference_release_id region_release,
 branch.binding_id branch_binding_id,s.pck_crn_psn_nickname branch_raw,s.pck_prn_name source_region_raw,s.pck_prn_drt_nickname source_logistics_region_raw,
 s.status raw_status,s.status_label status_label,absence.absent_since,absence.confirmations,absence.excluded_at,absence.reason absence_reason,absence.last_cycle,absence.last_reconciled_at,
 CONVERT(VARCHAR(40),CASE WHEN selection.reference_release_id IS NULL THEN 'REFERENCE_MISSING'
 WHEN ISNULL(branch.disposition,'MISSING')<>'RESOLVED' THEN 'BRANCH_UNRESOLVED'
 WHEN regions.reference_release_id IS NULL THEN 'REGION_RELEASE_UNRESOLVED' ELSE 'READY' END) disposition
FROM core.analytic_collection_current pointer JOIN core.analytic_collection_snapshot s ON s.snapshot_id=pointer.snapshot_id
JOIN stg.analytic_collection_attributes a ON a.stage_record_id=s.representative_stage
JOIN stg.coleta_record original ON original.stage_record_id=s.representative_stage
JOIN ctl.analytic_lab_source_group grouping ON grouping.run_id=pointer.run_id
LEFT JOIN core.analytic_collection_supplement_effective l ON l.run_id=pointer.run_id AND l.source_key=pointer.source_key
LEFT JOIN recon.analytic_collection_absence absence ON absence.run_id=pointer.run_id AND absence.source_key=pointer.source_key
LEFT JOIN ctl.analytic_lab_reference_selection selection ON selection.run_id=pointer.run_id AND selection.valid_from<=s.business_date AND selection.valid_to_exclusive>s.business_date
OUTER APPLY(SELECT * FROM ref.ufn_analytic_dimension(pointer.run_id,selection.revision,s.business_date) d WHERE d.entity='COL' AND d.source_key=pointer.source_key AND d.role='BRANCH') branch
OUTER APPLY ref.ufn_analytic_collection_region(pointer.run_id,selection.revision,s.business_date) regions
OUTER APPLY(SELECT NULLIF(REPLACE(REPLACE(REPLACE(LTRIM(RTRIM(s.pck_pds_postal_code)),N'-',N''),N'.',N''),N' ',N''),N'') cep,
 NULLIF(LTRIM(RTRIM(s.pck_pds_cty_name)),N'') city,NULLIF(UPPER(LTRIM(RTRIM(s.pck_pds_cty_sae_code))),N'') uf) clean
OUTER APPLY(SELECT TOP(1) r.region_code FROM ref.regiao_logistica_cep r WHERE r.reference_release_id=regions.reference_release_id AND r.valid_from<=s.business_date AND r.valid_to_exclusive>s.business_date
 AND LEN(clean.cep)=8 AND clean.cep NOT LIKE N'%[^0-9]%' AND clean.cep>=r.cep_start AND clean.cep<=r.cep_end ORDER BY r.priority,r.cep_start DESC,r.cep_end) cep
OUTER APPLY(SELECT TOP(1) r.region_code FROM ref.regiao_logistica_cidade_uf r WHERE r.reference_release_id=regions.reference_release_id AND r.valid_from<=s.business_date AND r.valid_to_exclusive>s.business_date
 AND cep.region_code IS NULL AND r.normalized_city=UPPER(clean.city) AND r.uf=clean.uf AND r.city_normalization_version=regions.normalization_version COLLATE Latin1_General_100_BIN2 ORDER BY r.priority) city
OUTER APPLY(SELECT u.usuario_name FROM core.usuario u WHERE u.source_key=l.cancellation_user_key AND u.active=1
 AND EXISTS(SELECT 1 FROM ctl.analytic_lab_execution_source b WHERE b.run_id=pointer.run_id AND b.entity='USUARIO' AND b.execution_id=u.last_seen_execution_id)) cancelUser
OUTER APPLY(SELECT u.usuario_name FROM core.usuario u WHERE u.source_key=l.destroy_user_key AND u.active=1
 AND EXISTS(SELECT 1 FROM ctl.analytic_lab_execution_source b WHERE b.run_id=pointer.run_id AND b.entity='USUARIO' AND b.execution_id=u.last_seen_execution_id)) destroyUser
OUTER APPLY(SELECT TOP(1) m.sequence_code,m.source_key,mc.link_id FROM core.relational_lab_link mc
 JOIN core.analytic_manifest_current currentMan ON currentMan.run_id=pointer.run_id AND currentMan.source_key=mc.origin_key
 JOIN core.analytic_manifest_snapshot m ON m.snapshot_id=currentMan.snapshot_id
 JOIN ref.analytic_lab_manifest_state_current state ON state.run_id=pointer.run_id AND state.source_key=m.source_key AND state.active=1
 WHERE mc.run_id=grouping.relational_run AND mc.relation_kind='MC' AND mc.target_key=pointer.source_key AND mc.active=1
 AND EXISTS(SELECT 1 FROM core.analytic_manifest_snapshot_lineage lineage JOIN stg.manifesto_observation o ON o.manifesto_observation_id=lineage.source_observation_id
 CROSS APPLY OPENJSON(o.payload_json) p WHERE lineage.snapshot_id=m.snapshot_id AND p.[key]=N'mft_pfs_pck_sequence_code' AND p.type=2 AND CONCAT(N'INTEGER:',p.value) COLLATE Latin1_General_100_BIN2=mc.origin_component)
 ORDER BY m.sequence_code DESC,TRY_CONVERT(BIGINT,SUBSTRING(m.source_key,9,128)) DESC,mc.link_id DESC) manifest;
GO
CREATE VIEW pub.analytic_lab_sql_03 AS SELECT * FROM core.analytic_lab_collection_projection WHERE disposition='READY';
GO
CREATE VIEW pub.analytic_lab_sql_04 AS SELECT [ID],[Coleta],[Solicitacao],[Filial],raw_status [Status ESL bruto],COALESCE(status_label,raw_status) [Status antes da exclusão],
 CONVERT(NVARCHAR(32),N'Excluída na origem') [Situação de sincronização],absent_since [Ausente desde],confirmations [Confirmações de ausência],excluded_at [Excluída em],
 absence_reason [Motivo],last_cycle [Execução de confirmação],last_reconciled_at [Última reconciliação],
 run_id,source_key,snapshot_id,source_execution,last_observation,business_date,reference_revision,reference_release_id,region_release,disposition
 FROM core.analytic_lab_collection_projection WHERE disposition='READY' AND [Excluída na Origem]=1;
GO
