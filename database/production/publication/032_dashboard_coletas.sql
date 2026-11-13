CREATE OR ALTER VIEW pub.vw_coletas_powerbi
AS
WITH active_rows AS (
    SELECT j.*,l.source_scope,l.stored_utc,
           JSON_QUERY(l.source_envelope,'$.record') AS metadata,
           ROW_NUMBER() OVER(PARTITION BY l.source_scope,j.id ORDER BY
               j.updated_at DESC,l.stored_utc DESC,j.manifest DESC,l.business_date DESC,l.occurrence_ordinal) AS rn
    FROM stg.production_export_latest l
    CROSS APPLY OPENJSON(l.source_envelope,'$.record') WITH (
        id nvarchar(50), sequence_code bigint, request_date date, request_hour nvarchar(80),
        service_date date, finish_date date, updated_at datetimeoffset(7), status nvarchar(100),
        invoices_volumes int, invoices_weight decimal(18,3), taxed_weight decimal(18,3),
        invoices_value decimal(18,2), manifest bigint '$.pck_mik_mft_sequence_code',
        customer nvarchar(255) '$.pck_cor_nickname', customer_document nvarchar(50) '$.pck_cor_document',
        street nvarchar(500) '$.pck_pds_line_1', street_number nvarchar(50) '$.pck_pds_number',
        complement nvarchar(255) '$.pck_pds_line_2', city nvarchar(255) '$.pck_pds_cty_name',
        neighborhood nvarchar(255) '$.pck_pds_neighborhood', state_code nvarchar(10) '$.pck_pds_cty_sae_code',
        postal_code nvarchar(20) '$.pck_pds_postal_code', branch_id bigint '$.pck_crn_id',
        branch nvarchar(255) '$.pck_crn_psn_nickname', user_name nvarchar(255) '$.pck_uer_name',
        cancellation_reason nvarchar(max), cancel_user_id bigint '$.pck_ctr_id',
        cancel_user nvarchar(255) '$.pck_ctr_name', last_occurrence nvarchar(50) '$.pck_loe_ore_description',
        occurrence_action nvarchar(255) '$.pck_pus_ore_trigger', attempts int '$.pck_mik_attempt_number'
    ) j
    WHERE l.entity_name='COLETAS' AND j.id IS NOT NULL
), deletion_rows AS (
    SELECT j.*,l.source_scope,l.stored_utc,
           JSON_QUERY(l.source_envelope,'$.record') AS metadata,
           ROW_NUMBER() OVER(PARTITION BY l.source_scope,j.id ORDER BY
               j.deleted_at DESC,l.stored_utc DESC,l.occurrence_ordinal) AS rn
    FROM stg.production_export_latest l
    CROSS APPLY OPENJSON(l.source_envelope,'$.record') WITH (
        id nvarchar(50), sequence_code bigint, request_date date, deleted_at datetimeoffset(7),
        invoices_value decimal(18,2), taxed_weight decimal(18,3), destroy_reason nvarchar(max),
        deletion_user nvarchar(255) '$.dek_dyr_name', branch nvarchar(255) '$.dek_crn_psn_nickname'
    ) j
    WHERE l.entity_name='COLETAS_EXCLUIDAS' AND j.id IS NOT NULL
), source_scopes AS (
    SELECT DISTINCT source_scope FROM ctl.production_ingestion_coverage
    WHERE entity_name IN ('COLETAS','COLETAS_EXCLUIDAS')
)
SELECT CONVERT(nvarchar(50),COALESCE(a.id,d.id)) AS [ID],
       COALESCE(a.sequence_code,d.sequence_code) AS [Coleta],
       COALESCE(a.request_date,d.request_date) AS [Solicitacao],
       CONVERT(time(0),COALESCE(TRY_CONVERT(time(0),SUBSTRING(a.request_hour,12,8)),
           TRY_CONVERT(time(0),a.request_hour),'00:00:00')) AS [Hora (Solicitacao)],
       a.service_date AS [Agendamento],a.finish_date AS [Finalizacao],
       CONVERT(nvarchar(100),CASE WHEN x.deleted=1 THEN N'Excluída' ELSE
           CASE LOWER(TRIM(a.status))
               WHEN N'pending' THEN N'Pendente' WHEN N'treatment' THEN N'Em tratativa'
               WHEN N'manifested' THEN N'Manifestada' WHEN N'in_transit' THEN N'Em trânsito'
               WHEN N'draft' THEN N'Rascunho' WHEN N'finished' THEN N'Finalizada' WHEN N'done' THEN N'Coletada'
               WHEN N'canceled' THEN N'Cancelada' WHEN N'cancelled' THEN N'Cancelada' ELSE a.status
           END END) AS [Status],
       x.deleted AS [Excluída na Origem],a.invoices_volumes AS [Volumes],a.invoices_weight AS [Peso Real],
       COALESCE(a.taxed_weight,d.taxed_weight) AS [Peso Taxado],
       COALESCE(a.invoices_value,d.invoices_value) AS [Valor NF],a.manifest AS [Numero Manifesto],
       CONVERT(bigint,NULL) AS [Veiculo],a.customer AS [Cliente],a.customer_document AS [Cliente Doc],
       a.street AS [Local da Coleta],a.street_number AS [Numero],a.complement AS [Complemento],
       a.city AS [Cidade],a.neighborhood AS [Bairro],a.state_code AS [UF],a.postal_code AS [CEP],
       CONVERT(nvarchar(255),CONCAT_WS(N' - ',NULLIF(TRIM(a.city),N''),NULLIF(TRIM(a.state_code),N''))) AS [Região da Coleta],
       CONVERT(nvarchar(264),CONCAT(COALESCE(NULLIF(TRIM(a.city),N''),N'Sem cidade'),N' - ',
           COALESCE(NULLIF(UPPER(TRIM(a.state_code)),N''),N'Sem UF'))) AS [Região Logística],
       a.branch_id AS [Filial ID],
       CONVERT(nvarchar(255),CASE WHEN x.deleted=1 THEN COALESCE(d.branch,a.branch) ELSE a.branch END) AS [Filial],
       a.user_name AS [Usuario],a.cancellation_reason AS [Motivo Cancel.],
       a.cancel_user_id AS [Usuario Cancel. ID],a.cancel_user AS [Usuario Cancel. Nome],
       CASE WHEN x.deleted=1 THEN d.destroy_reason END AS [Motivo Exclusao],
       CONVERT(bigint,NULL) AS [Usuario Exclusao ID],
       CASE WHEN x.deleted=1 THEN d.deletion_user END AS [Usuario Exclusao Nome],
       CONVERT(nvarchar(50),CASE WHEN x.deleted=1 THEN CONVERT(nvarchar(50),d.deleted_at,127) END) AS [Status Atualizado Em],
       CONVERT(datetimeoffset(0),CASE WHEN x.deleted=1 THEN d.deleted_at END) AS [Status Atualizado Em (normalizado)],
       a.last_occurrence AS [Última Ocorrência],a.occurrence_action AS [Ação da Ocorrência],
       a.attempts AS [Nº Tentativas],
       CASE WHEN x.deleted=1 THEN d.metadata ELSE a.metadata END AS [Metadata],
       CONVERT(datetime2(7),CASE WHEN x.deleted=1 THEN d.stored_utc ELSE a.stored_utc END) AS [Data de extracao]
FROM (SELECT * FROM active_rows WHERE rn=1) a
FULL OUTER JOIN (SELECT * FROM deletion_rows WHERE rn=1) d ON d.source_scope=a.source_scope AND d.id=a.id
CROSS APPLY (SELECT CONVERT(bit,CASE WHEN d.id IS NOT NULL AND (a.updated_at IS NULL OR d.deleted_at>=a.updated_at)
    THEN 1 ELSE 0 END) AS deleted) x
WHERE (SELECT COUNT(*) FROM source_scopes)=1;
