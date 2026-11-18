-- Uma linha por ocorrência no lote confirmado. O número da cotação pode se repetir.
INSERT ctl.dashboard_projection_dependency (projection_name,entity_name) VALUES (N'COTACOES','COTACOES');
SELECT TOP (0) CAST(NULL AS uniqueidentifier) AS run_id,CAST(NULL AS int) AS occurrence_ordinal,v.*
INTO core.dashboard_quote_occurrence FROM pub.vw_cotacoes_powerbi v;
ALTER TABLE core.dashboard_quote_occurrence ALTER COLUMN run_id uniqueidentifier NOT NULL;
ALTER TABLE core.dashboard_quote_occurrence ALTER COLUMN occurrence_ordinal int NOT NULL;
ALTER TABLE core.dashboard_quote_occurrence ADD CONSTRAINT PK_dashboard_quote_occurrence PRIMARY KEY(run_id,occurrence_ordinal);
CREATE INDEX IX_dashboard_quote_periodo ON core.dashboard_quote_occurrence ([Data Cotação],[Filial])
    INCLUDE ([N° Cotação]) WITH (MAXDOP=1);
GO
CREATE OR ALTER VIEW pub.vw_cotacoes_powerbi_preparada
AS
SELECT
    [N° Cotação],
    [Data Cotação],
    [Hora (Solicitacao)],
    [Data de extracao],
    [Filial],
    [Unidade],
    [Unidade_Origem],
    [Solicitante],
    [Usuário],
    [Usuario Key],
    [Setor_Usuario],
    [Empresa],
    [Cliente Pagador],
    [CNPJ/CPF Cliente],
    [Pagador/Nome fantasia],
    [Cliente Grupo],
    [Cliente],
    [Cidade Origem],
    [UF Origem],
    [CEP Origem],
    [Origem],
    [Cidade Destino],
    [UF Destino],
    [CEP Destino],
    [Destino],
    [Trecho],
    [Volume],
    [Peso real],
    [Peso taxado],
    [Valor NF],
    [Valor frete],
    [Min. Frete/KG],
    [Tabela],
    [Tipo de operação],
    [Metadata],
    [Status Conversão],
    [Status_Sistema],
    [Status_Sistema_CTe],
    [Status_Sistema_NFSe],
    [Refino_CTe],
    [Motivo Perda],
    [Observações para o frete],
    [CT-e/Data de emissão],
    [Nfse/Data de emissão],
    [Remetente/CNPJ],
    [Remetente/Nome fantasia],
    [Destinatário/CNPJ],
    [Destinatário/Nome fantasia],
    [Descontos/Subtotal parcelas],
    [Trechos/ITR],
    [Trechos/TDE],
    [Trechos/Coleta],
    [Trechos/Entrega],
    [Trechos/Outros valores]
FROM core.dashboard_quote_occurrence;
GO
CREATE OR ALTER PROCEDURE core.usp_dashboard_preparar_cotacoes
    @entity_name varchar(40), @source_scope char(64), @business_date date
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    SET LOCK_TIMEOUT 3000;
    IF @@TRANCOUNT<>0 THROW 52060,'COTACOES_OUTER_TRANSACTION_REFUSED',1;
    IF @entity_name IS NULL OR @entity_name<>'COTACOES' OR @source_scope IS NULL OR @business_date IS NULL
        THROW 52061,'COTACOES_PARTITION_REQUIRED',1;
    BEGIN TRY
        BEGIN TRANSACTION;
        DECLARE @lock int, @run uniqueidentifier, @old uniqueidentifier, @expected int, @capture datetime2(7);
        EXEC @lock=sys.sp_getapplock @Resource=N'dashboard:cotacoes:publicar', @LockMode='Exclusive',
            @LockOwner='Transaction', @LockTimeout=0;
        IF @lock<0 THROW 52062,'COTACOES_PUBLICATION_BUSY',1;
        IF (SELECT COUNT(DISTINCT source_scope) FROM ctl.production_ingestion_coverage WHERE entity_name=@entity_name)<>1
            THROW 52063,'COTACOES_SINGLE_SOURCE_REQUIRED',1;
        SELECT @run=c.latest_run_id,@capture=c.stored_utc,@expected=r.row_count
        FROM ctl.production_ingestion_coverage c JOIN ctl.production_ingestion_run r ON r.run_id=c.latest_run_id
            AND r.run_state='STORED' AND r.source_scope=c.source_scope AND r.entity_name=c.entity_name
            AND r.period_start=c.business_date AND r.period_end=c.business_date
        WHERE c.source_scope=@source_scope AND c.business_date=@business_date AND c.entity_name=@entity_name;
        IF @run IS NULL OR @expected IS NULL OR @expected NOT BETWEEN 0 AND 100000
            THROW 52064,'COTACOES_COMPLETE_BOUNDED_CAPTURE_REQUIRED',1;
        SELECT @old=applied_run_id FROM ctl.dashboard_projection_partition
        WHERE projection_name=N'COTACOES' AND source_scope=@source_scope AND entity_name=@entity_name AND business_date=@business_date;
        IF @old=@run BEGIN COMMIT; RETURN; END;
        IF EXISTS (SELECT 1 FROM stg.production_export_occurrence WHERE run_id=@run
                   AND ISNULL(ISJSON(JSON_QUERY(source_envelope,'$.record'),OBJECT),0)<>1)
            THROW 52065,'COTACOES_RECORD_REQUIRED',1;

        DECLARE @recebida datetime2(7)=CONVERT(datetime2(7), TODATETIMEOFFSET(@capture,'+00:00') AT TIME ZONE 'E. South America Standard Time');
        -- Campos e tarifa seguem a projeção de referência 002 sobre o lote selecionado.
        ;WITH quotes AS (
            SELECT l.occurrence_ordinal,
                CONVERT(bigint, j.sequence_code) AS numero,
                CONVERT(datetimeoffset(7), j.requested_at) AS solicitada,
                @recebida AS recebida,
                CONVERT(nvarchar(255), j.branch) AS filial,
                CONVERT(nvarchar(255), j.requester) AS solicitante,
                CONVERT(nvarchar(255), j.user_name) AS usuario,
                CONVERT(nvarchar(255), j.company_name) AS empresa,
                CONVERT(nvarchar(255), j.customer_name) AS pagador,
                CONVERT(nvarchar(14), j.customer_document) AS pagador_documento,
                CONVERT(nvarchar(255), j.customer_nickname) AS pagador_fantasia,
                CONVERT(nvarchar(100), j.origin_city) AS origem_cidade,
                CONVERT(nvarchar(2), j.origin_uf) AS origem_uf,
                CONVERT(nvarchar(10), j.origin_postal) AS origem_cep,
                CONVERT(nvarchar(100), j.destination_city) AS destino_cidade,
                CONVERT(nvarchar(2), j.destination_uf) AS destino_uf,
                CONVERT(nvarchar(10), j.destination_postal) AS destino_cep,
                CONVERT(int, j.volumes) AS volumes,
                CONVERT(nvarchar(50), j.real_weight) AS peso_real,
                CONVERT(decimal(18,3), j.taxed_weight) AS peso_taxado,
                CONVERT(decimal(18,2), j.invoices_value) AS valor_nf,
                CONVERT(decimal(18,2), j.freight_total) AS valor_frete,
                CONVERT(nvarchar(255), j.price_table) AS tabela,
                CONVERT(nvarchar(100), j.operation_type) AS operacao,
                JSON_QUERY(l.source_envelope, '$.record') AS metadata,
                j.disapprove_comments AS motivo_perda,
                j.freight_comments AS observacoes,
                CONVERT(datetimeoffset(7), j.cte_issued_at) AS cte_emissao,
                CASE WHEN RIGHT(j.nfse_issued_at, 1) = 'Z'
                       OR RIGHT(j.nfse_issued_at, 6) LIKE '[+-][0-9][0-9]:[0-9][0-9]'
                     THEN CONVERT(datetimeoffset(7), j.nfse_issued_at)
                     ELSE CONVERT(datetime2(7), j.nfse_issued_at, 126) AT TIME ZONE 'E. South America Standard Time'
                END AS nfse_emissao,
                CONVERT(nvarchar(14), j.sender_document) AS remetente_documento,
                CONVERT(nvarchar(255), j.sender_nickname) AS remetente_fantasia,
                CONVERT(nvarchar(14), j.receiver_document) AS destinatario_documento,
                CONVERT(nvarchar(255), j.receiver_nickname) AS destinatario_fantasia,
                CONVERT(decimal(18,6), j.discount_subtotal) AS descontos,
                CONVERT(decimal(18,6), j.itr_subtotal) AS itr,
                CONVERT(decimal(18,6), j.tde_subtotal) AS tde,
                CONVERT(decimal(18,6), j.collect_subtotal) AS coleta,
                CONVERT(decimal(18,6), j.delivery_subtotal) AS entrega,
                CONVERT(decimal(18,6), j.other_fees) AS outros
            FROM stg.production_export_occurrence l
            CROSS APPLY (SELECT TOP (1) * FROM OPENJSON(l.source_envelope, '$.record') WITH (
                sequence_code nvarchar(max) '$.sequence_code',
                requested_at nvarchar(max) '$.requested_at',
                branch nvarchar(max) '$.qoe_crn_psn_nickname',
                requester nvarchar(max) '$.requester_name',
                user_name nvarchar(max) '$.qoe_uer_name',
                company_name nvarchar(max) '$.qoe_crn_psn_name',
                customer_name nvarchar(max) '$.qoe_cor_name',
                customer_document nvarchar(max) '$.qoe_cor_document',
                customer_nickname nvarchar(max) '$.qoe_cor_nickname',
                origin_city nvarchar(max) '$.qoe_qes_ony_name',
                origin_uf nvarchar(max) '$.qoe_qes_ony_sae_code',
                origin_postal nvarchar(max) '$.qoe_qes_origin_postal_code',
                destination_city nvarchar(max) '$.qoe_qes_diy_name',
                destination_uf nvarchar(max) '$.qoe_qes_diy_sae_code',
                destination_postal nvarchar(max) '$.qoe_qes_destination_postal_code',
                volumes nvarchar(max) '$.qoe_qes_invoices_volumes',
                real_weight nvarchar(max) '$.qoe_qes_real_weight',
                taxed_weight nvarchar(max) '$.qoe_qes_taxed_weight',
                invoices_value nvarchar(max) '$.qoe_qes_invoices_value',
                freight_total nvarchar(max) '$.qoe_qes_total',
                price_table nvarchar(max) '$.qoe_qes_cre_name',
                operation_type nvarchar(max) '$.qoe_qes_fon_name',
                disapprove_comments nvarchar(max) '$.qoe_qes_disapprove_comments',
                freight_comments nvarchar(max) '$.qoe_qes_freight_comments',
                cte_issued_at nvarchar(max) '$.qoe_qes_fit_fhe_cte_issued_at',
                nfse_issued_at nvarchar(max) '$.qoe_qes_fit_nse_issued_at',
                sender_document nvarchar(max) '$.qoe_qes_sdr_document',
                sender_nickname nvarchar(max) '$.qoe_qes_sdr_nickname',
                receiver_document nvarchar(max) '$.qoe_qes_rpt_document',
                receiver_nickname nvarchar(max) '$.qoe_qes_rpt_nickname',
                discount_subtotal nvarchar(max) '$.qoe_qes_fit_fdt_subtotal',
                itr_subtotal nvarchar(max) '$.qoe_qes_itr_subtotal',
                tde_subtotal nvarchar(max) '$.qoe_qes_tde_subtotal',
                collect_subtotal nvarchar(max) '$.qoe_qes_collect_subtotal',
                delivery_subtotal nvarchar(max) '$.qoe_qes_delivery_subtotal',
                other_fees nvarchar(max) '$.qoe_qes_other_fees'
            )) j
            WHERE l.run_id=@run
        )
        INSERT core.dashboard_quote_occurrence (run_id, occurrence_ordinal,
            [N° Cotação],
            [Data Cotação],
            [Hora (Solicitacao)],
            [Data de extracao],
            [Filial],
            [Unidade],
            [Unidade_Origem],
            [Solicitante],
            [Usuário],
            [Usuario Key],
            [Setor_Usuario],
            [Empresa],
            [Cliente Pagador],
            [CNPJ/CPF Cliente],
            [Pagador/Nome fantasia],
            [Cliente Grupo],
            [Cliente],
            [Cidade Origem],
            [UF Origem],
            [CEP Origem],
            [Origem],
            [Cidade Destino],
            [UF Destino],
            [CEP Destino],
            [Destino],
            [Trecho],
            [Volume],
            [Peso real],
            [Peso taxado],
            [Valor NF],
            [Valor frete],
            [Min. Frete/KG],
            [Tabela],
            [Tipo de operação],
            [Metadata],
            [Status Conversão],
            [Status_Sistema],
            [Status_Sistema_CTe],
            [Status_Sistema_NFSe],
            [Refino_CTe],
            [Motivo Perda],
            [Observações para o frete],
            [CT-e/Data de emissão],
            [Nfse/Data de emissão],
            [Remetente/CNPJ],
            [Remetente/Nome fantasia],
            [Destinatário/CNPJ],
            [Destinatário/Nome fantasia],
            [Descontos/Subtotal parcelas],
            [Trechos/ITR],
            [Trechos/TDE],
            [Trechos/Coleta],
            [Trechos/Entrega],
            [Trechos/Outros valores])
        SELECT @run, q.occurrence_ordinal,
            q.numero AS [N° Cotação],
            q.solicitada AS [Data Cotação],
            CONVERT(time(0), q.solicitada) AS [Hora (Solicitacao)],
            q.recebida AS [Data de extracao],
            q.filial AS [Filial],
            q.filial AS [Unidade],
            q.filial AS [Unidade_Origem],
            q.solicitante AS [Solicitante],
            q.usuario AS [Usuário],
            NULLIF(LOWER(TRIM(q.usuario)), N'') AS [Usuario Key],
            q.usuario AS [Setor_Usuario],
            q.empresa AS [Empresa],
            q.pagador AS [Cliente Pagador],
            q.pagador_documento AS [CNPJ/CPF Cliente],
            q.pagador_fantasia AS [Pagador/Nome fantasia],
            q.pagador AS [Cliente Grupo],
            COALESCE(q.pagador_fantasia, q.pagador) AS [Cliente],
            q.origem_cidade AS [Cidade Origem],
            q.origem_uf AS [UF Origem],
            q.origem_cep AS [CEP Origem],
            CONVERT(nvarchar(105), CONCAT(q.origem_cidade, N' - ', q.origem_uf)) AS [Origem],
            q.destino_cidade AS [Cidade Destino],
            q.destino_uf AS [UF Destino],
            q.destino_cep AS [CEP Destino],
            CONVERT(nvarchar(105), CONCAT(q.destino_cidade, N' - ', q.destino_uf)) AS [Destino],
            CONVERT(nvarchar(213), CONCAT(q.origem_cidade, N' - ', q.origem_uf, N' x ', q.destino_cidade, N' - ', q.destino_uf)) AS [Trecho],
            q.volumes AS [Volume],
            q.peso_real AS [Peso real],
            q.peso_taxado AS [Peso taxado],
            q.valor_nf AS [Valor NF],
            q.valor_frete AS [Valor frete],
            CONVERT(decimal(3,2), COALESCE(CASE q.origem_uf
                WHEN 'SP' THEN CASE q.destino_uf WHEN 'SP' THEN 0.66 WHEN 'RJ' THEN 1.21 WHEN 'PR' THEN 0.74 WHEN 'RS' THEN 1.70 WHEN 'PE' THEN 1.54 END
                WHEN 'RJ' THEN CASE q.destino_uf WHEN 'SP' THEN 1.21 WHEN 'RJ' THEN 0.66 WHEN 'PR' THEN 0.98 WHEN 'RS' THEN 2.08 WHEN 'PE' THEN 1.95 END
                WHEN 'PR' THEN CASE q.destino_uf WHEN 'SP' THEN 0.74 WHEN 'RJ' THEN 0.88 WHEN 'PR' THEN 0.55 WHEN 'RS' THEN 0.98 WHEN 'PE' THEN 1.83 END
                WHEN 'RS' THEN CASE q.destino_uf WHEN 'SP' THEN 1.70 WHEN 'RJ' THEN 1.95 WHEN 'PR' THEN 1.46 WHEN 'RS' THEN 0.98 WHEN 'PE' THEN 2.20 END
                WHEN 'PE' THEN CASE q.destino_uf WHEN 'SP' THEN 1.46 WHEN 'RJ' THEN 2.20 WHEN 'PR' THEN 2.20 WHEN 'RS' THEN 2.32 WHEN 'PE' THEN 0.55 END END, 0.00)) AS [Min. Frete/KG],
            q.tabela AS [Tabela],
            q.operacao AS [Tipo de operação],
            q.metadata AS [Metadata],
            status.conversao AS [Status Conversão],
            status.conversao AS [Status_Sistema],
            CASE WHEN q.cte_emissao IS NOT NULL THEN 'Emitido' ELSE 'Pendente' END AS [Status_Sistema_CTe],
            CASE WHEN q.nfse_emissao IS NOT NULL THEN 'Emitida' ELSE 'Pendente' END AS [Status_Sistema_NFSe],
            CASE WHEN q.cte_emissao IS NOT NULL THEN 'Sim' ELSE 'Não' END AS [Refino_CTe],
            q.motivo_perda AS [Motivo Perda],
            q.observacoes AS [Observações para o frete],
            q.cte_emissao AS [CT-e/Data de emissão],
            q.nfse_emissao AS [Nfse/Data de emissão],
            q.remetente_documento AS [Remetente/CNPJ],
            q.remetente_fantasia AS [Remetente/Nome fantasia],
            q.destinatario_documento AS [Destinatário/CNPJ],
            q.destinatario_fantasia AS [Destinatário/Nome fantasia],
            q.descontos AS [Descontos/Subtotal parcelas],
            q.itr AS [Trechos/ITR],
            q.tde AS [Trechos/TDE],
            q.coleta AS [Trechos/Coleta],
            q.entrega AS [Trechos/Entrega],
            q.outros AS [Trechos/Outros valores]
        FROM quotes q
        CROSS APPLY (VALUES (CASE WHEN q.cte_emissao IS NOT NULL OR q.nfse_emissao IS NOT NULL THEN 'Convertida'
            WHEN LEN(q.motivo_perda) > 0 THEN 'Reprovada' ELSE 'Pendente' END)) status(conversao)
        OPTION(MAXDOP 1, MAX_GRANT_PERCENT=0.2);
        IF @@ROWCOUNT<>@expected THROW 52066,'COTACOES_CARDINALITY_MISMATCH',1;
        DELETE FROM core.dashboard_quote_occurrence WHERE run_id=@old;
        IF NOT EXISTS(SELECT 1 FROM ctl.production_ingestion_coverage WITH(UPDLOCK,HOLDLOCK)
            WHERE entity_name=@entity_name AND source_scope=@source_scope AND business_date=@business_date AND latest_run_id=@run)
            OR EXISTS(SELECT 1 FROM ctl.production_ingestion_coverage WITH(HOLDLOCK)
            WHERE entity_name=@entity_name AND source_scope<>@source_scope)
            THROW 52067,'COTACOES_CAPTURE_SUPERSEDED',1;
        UPDATE ctl.dashboard_projection_partition SET applied_run_id=@run,capture_utc=@capture,published_utc=SYSUTCDATETIME()
        WHERE projection_name=N'COTACOES' AND source_scope=@source_scope AND entity_name=@entity_name AND business_date=@business_date;
        IF @@ROWCOUNT=0 INSERT ctl.dashboard_projection_partition
            (projection_name,entity_name,source_scope,business_date,applied_run_id,capture_utc,published_utc)
            VALUES (N'COTACOES',@entity_name,@source_scope,@business_date,@run,@capture,SYSUTCDATETIME());
        COMMIT;
    END TRY
    BEGIN CATCH
        IF @@TRANCOUNT>0 ROLLBACK;
        THROW;
    END CATCH;
END;
