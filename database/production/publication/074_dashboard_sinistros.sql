-- Relatório ESL 6392. A função projeta os formatos de 44 e 46 campos com os nomes atuais.
CREATE FUNCTION core.ufn_dashboard_sinistro (@registro nvarchar(max))
RETURNS TABLE
AS RETURN (
    SELECT
        j.sequence_code AS [Nº do Sinistro],
        j.opening_at_date AS [Data abertura],
        j.occurrence_at_date AS [Data ocorrência],
        CASE WHEN j.occurrence_at_time LIKE N'____-__-__T%'
                THEN CONVERT(time(7), CONVERT(datetimeoffset(7), j.occurrence_at_time, 127))
                ELSE CONVERT(time(7), NULLIF(j.occurrence_at_time, N'')) END AS [Hora ocorrência],
        j.expected_solution_date AS [Previsão de solução],
        j.insurance_claim_location AS [Local do sinistro],
        j.informed_by AS [Informado por],
        rctrc.itens AS [RCTRC],
        rctac.itens AS [RCTAC],
        rcfdc.itens AS [RCFDC],
        j.insurance_claim_commentary AS [Comentário do sinistro],
        j.list_of_claimed_products AS [Relação dos produtos sinistrados],
        NULLIF(TRIM(j.insurer_communication_contact_name), N'') AS [Responsável da Seguradora],
        j.insurer_communication_date AS [Data de Comunicação a Seguradora],
        NULLIF(TRIM(j.customer_communication_contact_name), N'') AS [Responsável do Cliente],
        j.customer_communication_date AS [Data de Comunicação ao Cliente],
        NULLIF(TRIM(j.icm_ire_cpy_nickname), N'') AS [Seguradora/Nome],
        j.policy_number AS [Nº da apólice],
        j.brokers_case_number AS [Nº do processo da corretora],
        j.bo_number AS [Nº do BO],
        j.finished_at_date AS [Data finalização],
        CASE WHEN j.finished_at_time LIKE N'____-__-__T%'
                THEN CONVERT(time(7), CONVERT(datetimeoffset(7), j.finished_at_time, 127))
                ELSE CONVERT(time(7), NULLIF(j.finished_at_time, N'')) END AS [Hora finalização],
        j.finished_commentary AS [Comentário da finalização],
        j.internal_description AS [Descrição interna],
        j.invoices_count AS [Quantidade de documentos],
        j.icm_fis_fit_corporation_sequence_number AS [Minuta],
        j.icm_fis_ioe_number AS [Nota Fiscal/Número],
        j.invoices_volumes AS [Volumes],
        j.invoices_weight AS [Peso],
        j.invoices_value AS [Valor NF],
        j.icm_fis_fit_pyr_nickname AS [Pagador do frete/Nome fantasia],
        j.customer_debits_subtotal AS [valor a pagar ao cliente],
        j.customer_credit_entries_subtotal AS [Valor nota de crédito ao cliente],
        j.responsible_credits_subtotal AS [Valor a receber do responsável],
        j.responsible_debit_entries_subtotal AS [Valor nota de débito do responsável],
        j.insurer_credits_subtotal AS [Valor a receber da seguradora],
        j.insurance_claim_total AS [Resultado final],
        j.icm_crn_psn_nickname AS [Filial/Nome],
        j.icm_dvr_iil_name AS [Motorista/Nome],
        j.icm_fer_name AS [Usuário/Nome],
        j.icm_vie_license_plate AS [Veículo/Placa],
        j.icm_ttt_ore_description AS [Ocorrência],
        j.icm_ttt_ore_code AS [Ocorrência/Código],
        j.icm_ttt_treatment_at AS [Tratativa/Data tratativa],
        CASE j.icm_ttt_dealing_type
               WHEN N'send_to_occurrences_dealing' THEN N'Enviar para tratativas de ocorrências'
               WHEN N'send_to_damages_dealing' THEN N'Enviar para tratativas de avarias'
               WHEN N'send_to_incidents_dealing' THEN N'Enviar para tratativas de sinistros'
               ELSE j.icm_ttt_dealing_type END AS [Tratativa/Tipo tratativa],
        CASE j.icm_ttt_solution_type
               WHEN N'redelivery_with_freight_charge' THEN N'Gerar reentrega com cobrança de frete'
               WHEN N'redelivery_without_freight_charge' THEN N'Liberar reentrega sem cobrança de frete'
               WHEN N'return_with_freight_charge' THEN N'Gerar devolução com cobrança de frete'
               WHEN N'return_without_freight_charge' THEN N'Liberar devolução sem cobrança de frete'
               WHEN N'continue_transport' THEN N'Continuar o transporte'
               WHEN N'finish_transport' THEN N'Finalizar o transporte'
               WHEN N'reverse_pick_generate_freight' THEN N'Gerar novo frete para coleta reversa'
               WHEN N'reverse_pick_reschedule' THEN N'Reagendar coleta reversa'
               ELSE j.icm_ttt_solution_type END AS [Tratativa/Solução]
    FROM OPENJSON(@registro) WITH (
        sequence_code decimal(38,0) '$.sequence_code',
        opening_at_date date '$.opening_at_date',
        occurrence_at_date date '$.occurrence_at_date',
        occurrence_at_time nvarchar(64) '$.occurrence_at_time',
        expected_solution_date date '$.expected_solution_date',
        insurance_claim_location nvarchar(max) '$.insurance_claim_location',
        informed_by nvarchar(max) '$.informed_by',
        rctrc nvarchar(max) '$.rctrc' AS JSON,
        rctac nvarchar(max) '$.rctac' AS JSON,
        rcfdc nvarchar(max) '$.rcfdc' AS JSON,
        insurance_claim_commentary nvarchar(max) '$.insurance_claim_commentary',
        list_of_claimed_products nvarchar(max) '$.list_of_claimed_products',
        insurer_communication_contact_name nvarchar(max) '$.insurer_communication_contact_name',
        insurer_communication_date date '$.insurer_communication_date',
        customer_communication_contact_name nvarchar(max) '$.customer_communication_contact_name',
        customer_communication_date date '$.customer_communication_date',
        icm_ire_cpy_nickname nvarchar(max) '$.icm_ire_cpy_nickname',
        policy_number nvarchar(max) '$.policy_number',
        brokers_case_number nvarchar(max) '$.brokers_case_number',
        bo_number nvarchar(max) '$.bo_number',
        finished_at_date date '$.finished_at_date',
        finished_at_time nvarchar(64) '$.finished_at_time',
        finished_commentary nvarchar(max) '$.finished_commentary',
        internal_description nvarchar(max) '$.internal_description',
        invoices_count decimal(38,0) '$.invoices_count',
        icm_fis_fit_corporation_sequence_number decimal(38,0) '$.icm_fis_fit_corporation_sequence_number',
        icm_fis_ioe_number nvarchar(128) '$.icm_fis_ioe_number',
        invoices_volumes decimal(38,0) '$.invoices_volumes',
        invoices_weight decimal(28,8) '$.invoices_weight',
        invoices_value decimal(28,8) '$.invoices_value',
        icm_fis_fit_pyr_nickname nvarchar(max) '$.icm_fis_fit_pyr_nickname',
        customer_debits_subtotal decimal(28,8) '$.customer_debits_subtotal',
        customer_credit_entries_subtotal decimal(28,8) '$.customer_credit_entries_subtotal',
        responsible_credits_subtotal decimal(28,8) '$.responsible_credits_subtotal',
        responsible_debit_entries_subtotal decimal(28,8) '$.responsible_debit_entries_subtotal',
        insurer_credits_subtotal decimal(28,8) '$.insurer_credits_subtotal',
        insurance_claim_total decimal(28,8) '$.insurance_claim_total',
        icm_crn_psn_nickname nvarchar(max) '$.icm_crn_psn_nickname',
        icm_dvr_iil_name nvarchar(max) '$.icm_dvr_iil_name',
        icm_fer_name nvarchar(max) '$.icm_fer_name',
        icm_vie_license_plate nvarchar(max) '$.icm_vie_license_plate',
        icm_ttt_ore_description nvarchar(max) '$.icm_ttt_ore_description',
        icm_ttt_ore_code decimal(38,0) '$.icm_ttt_ore_code',
        icm_ttt_treatment_at datetimeoffset(7) '$.icm_ttt_treatment_at',
        icm_ttt_dealing_type nvarchar(max) '$.icm_ttt_dealing_type',
        icm_ttt_solution_type nvarchar(max) '$.icm_ttt_solution_type'
    ) AS j
    OUTER APPLY (
        SELECT STRING_AGG(a.value, N', ') WITHIN GROUP (ORDER BY CONVERT(int, a.[key])) AS itens
        FROM OPENJSON(j.rctrc) AS a
    ) AS rctrc
    OUTER APPLY (
        SELECT STRING_AGG(a.value, N', ') WITHIN GROUP (ORDER BY CONVERT(int, a.[key])) AS itens
        FROM OPENJSON(j.rctac) AS a
    ) AS rctac
    OUTER APPLY (
        SELECT STRING_AGG(a.value, N', ') WITHIN GROUP (ORDER BY CONVERT(int, a.[key])) AS itens
        FROM OPENJSON(j.rcfdc) AS a
    ) AS rcfdc
);
GO
-- Uma linha por registro da última captura de cada partição; inclui documentos e tratativas repetidos.
CREATE VIEW pub.dashboard_sinistros AS
SELECT s.*
FROM stg.production_export_latest AS l
CROSS APPLY core.ufn_dashboard_sinistro(JSON_QUERY(l.source_envelope, '$.record')) AS s
WHERE l.entity_name = 'SINISTROS'
  AND (SELECT COUNT_BIG(DISTINCT source_scope) FROM ctl.production_ingestion_coverage
       WHERE entity_name = 'SINISTROS') = 1;
GO
-- O ESL não informa um identificador comum do evento. Esta leitura oferece o histórico
-- do mesmo documento e código. Cada ocorrência aparece uma vez por sinistro/documento.
CREATE VIEW pub.dashboard_sinistros_ocorrencias_documento AS
WITH documentos AS (
    SELECT DISTINCT [Nº do Sinistro], [Minuta], [Nota Fiscal/Número], [Ocorrência/Código]
    FROM pub.dashboard_sinistros
    WHERE [Nº do Sinistro] IS NOT NULL AND [Minuta] IS NOT NULL
      AND NULLIF(TRIM([Nota Fiscal/Número]), N'') IS NOT NULL AND [Ocorrência/Código] IS NOT NULL
)
SELECT d.[Nº do Sinistro], d.[Minuta], d.[Nota Fiscal/Número], d.[Ocorrência/Código],
       o.descricao, o.ocorrido_em, o.filial, o.status_entrega, o.cte, o.escopo, o.data_particao, o.ordem
FROM documentos AS d
JOIN pub.dashboard_performance_ocorrencias AS o
  ON o.minuta = TRY_CONVERT(bigint, d.[Minuta]) AND o.nota_fiscal = d.[Nota Fiscal/Número]
 AND o.codigo = TRY_CONVERT(int, d.[Ocorrência/Código])
WHERE EXISTS (SELECT 1 FROM ctl.production_ingestion_coverage AS c
              WHERE c.entity_name = 'SINISTROS' AND c.source_scope = o.escopo);
