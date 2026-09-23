-- Explicit source-bound roles; current location text does not infer a branch, unloading lexical order does not attribute it.
SET ANSI_NULLS ON; SET QUOTED_IDENTIFIER ON;
GO
ALTER TABLE ref.analytic_lab_dimension_binding DROP CONSTRAINT CK_analytic_dimension_binding;
ALTER TABLE ref.analytic_lab_dimension_binding ADD CONSTRAINT CK_analytic_dimension_binding CHECK(revision BETWEEN 1 AND 100000 AND valid_from<valid_to_exclusive
 AND entity IN('FRETE','LOC','MAN','COL','CAP','FAT','INV','SIN','RAS','USUARIO','COT')
 AND entity_key LIKE 'synthetic-%' AND evidence LIKE 'synthetic-%'
 AND ((dimension_kind='FILIAL' AND role IN('BRANCH','DEST_BRANCH','PERFORMANCE_BRANCH','CURRENT_BRANCH','UNLOADING_BRANCH'))
 OR(dimension_kind='CLIENTE' AND role IN('PAYER','SENDER','RECIPIENT','CLIENT'))
 OR(dimension_kind='VEICULO' AND role IN('TRACTOR','TRAILER1','TRAILER2'))
 OR(dimension_kind='MOTORISTA' AND role='DRIVER') OR(dimension_kind='PLANOCONTAS' AND role='ACCOUNT')
 OR(dimension_kind='USUARIO' AND role IN('USER','CANCEL_USER','DESTROY_USER'))));
GO
ALTER VIEW pub.analytic_lab_sql_07 AS SELECT
 CAST(lc.service_at AS TIME(0)) AS [Hora (Solicitacao)],
 lc.sequence_number AS [N° Minuta],
 REPLACE(lc.type, 'Freight::', '') AS [Tipo],
 lc.service_at AS [Data do frete],
 lc.invoices_volumes AS [Volumes],
 lc.taxed_weight AS [Peso Taxado],
 lc.taxed_weight_decimal AS [Peso Taxado Decimal],
 lc.invoices_value AS [Valor NF],
 lc.invoices_value_decimal AS [Valor NF Decimal],
 lc.total_value AS [Valor Frete],
 lc.service_type AS [Tipo Serviço],
 lc.branch_nickname AS [Filial Emissora],
 lc.predicted_delivery_at AS [Previsão Entrega/Previsão de entrega],
 lc.destination_location_name AS [Região Destino],
 lc.destination_branch_nickname AS [Filial Destino],
 lc.destination_branch_nickname AS [Responsável pela Região de Destino],
 COALESCE(regiao_alias.label,N'SEM_MAP') AS [Sigla Responsável Região Destino],
 lc.classification AS [Classificação],
 COALESCE(status_label.label,lc.status COLLATE Latin1_General_100_BIN2) AS [Status Carga],
 COALESCE(lc.status_normalized, NULLIF(LOWER(LTRIM(RTRIM(lc.status))), ''), N'sem_status') AS [Status Normalizado],
 CASE WHEN COALESCE(lc.status_normalized, LOWER(LTRIM(RTRIM(lc.status)))) IN (N'finished', N'delivered', N'canceled', N'cancelled', N'finalizado', N'entregue', N'cancelado') THEN 1 ELSE 0 END AS [Status Terminal],
 CASE WHEN COALESCE(lc.status_normalized, LOWER(LTRIM(RTRIM(lc.status)))) IN (N'canceled', N'cancelled', N'cancelado') THEN 1 ELSE 0 END AS [Cancelado Flag],
 CASE WHEN current_branch.binding_id IS NOT NULL AND current_branch.disposition<>'RESOLVED' THEN NULL ELSE COALESCE(lc.status_branch_nickname COLLATE Latin1_General_100_BIN2,current_branch.normalized_name) END AS [Filial Atual],
 lc.origin_location_name AS [Região Origem],
 lc.origin_branch_nickname AS [Filial Origem],
 lc.fit_fln_cln_nickname AS [Localização Atual],
 lc.localizacao_hash AS [Hash Localização],
 lc.metadata AS [Metadata],
 lc.data_extracao AS [Data de extracao],
 lc.run_id,lc.dependency_id,lc.source_key,lc.business_date,selection.revision reference_revision,lc.stage_record_id,lc.disposition,
 selection.reference_release_id,
 CONVERT(VARCHAR(32),CASE WHEN lc.status_branch_nickname IS NOT NULL THEN 'SOURCE_CAPTURE'
 WHEN current_branch.disposition='RESOLVED' THEN 'EXPLICIT_CURRENT_BINDING'
 WHEN current_branch.binding_id IS NOT NULL THEN current_branch.disposition ELSE 'UNSOURCED_LEGACY' END) status_branch_nickname_provenance,
 current_branch.binding_id current_branch_binding_id,current_branch.entity_key current_branch_key,
 CONVERT(VARCHAR(24),CASE WHEN regiao_alias.label IS NULL THEN 'UNMAPPED' ELSE 'GOVERNED_ALIAS' END) region_provenance
 FROM core.analytic_lab_location_projection lc
 JOIN ctl.analytic_lab_reference_selection selection ON selection.run_id=lc.run_id AND selection.valid_from<=lc.business_date AND selection.valid_to_exclusive>lc.business_date
 LEFT JOIN ref.analytic_lab_label status_label ON status_label.reference_release_id=selection.reference_release_id AND status_label.category='LOC_STATUS' AND status_label.raw_value=lc.status COLLATE Latin1_General_100_BIN2
 LEFT JOIN ref.analytic_lab_label regiao_alias ON regiao_alias.reference_release_id=selection.reference_release_id AND regiao_alias.category='REGION_ALIAS'
 AND regiao_alias.raw_value=UPPER(LTRIM(RTRIM(lc.destination_branch_nickname))) COLLATE Latin1_General_100_BIN2
 OUTER APPLY(SELECT * FROM ref.ufn_analytic_dimension(lc.run_id,selection.revision,lc.business_date) d
 WHERE d.entity='LOC' AND d.source_key=lc.source_key AND d.role='CURRENT_BRANCH') current_branch
 WHERE lc.disposition='READY';
GO
