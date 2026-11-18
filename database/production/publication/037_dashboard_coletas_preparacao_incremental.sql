-- Candidato de publicacao. O contrato atual continua disponivel durante o preenchimento.
-- Aplicar somente pelo repositorio ETL, com comparacao de paridade e carga antes da troca.
CREATE TABLE ctl.dashboard_projection_dependency (
    projection_name sysname NOT NULL,
    entity_name varchar(40) NOT NULL,
    CONSTRAINT PK_dashboard_projection_dependency PRIMARY KEY (projection_name, entity_name),
    CONSTRAINT FK_dashboard_projection_template FOREIGN KEY (entity_name)
        REFERENCES ctl.production_export_template(entity_name)
);

CREATE TABLE ctl.dashboard_projection_partition (
    projection_name sysname NOT NULL,
    entity_name varchar(40) NOT NULL,
    source_scope char(64) COLLATE Latin1_General_100_BIN2 NOT NULL,
    business_date date NOT NULL,
    applied_run_id uniqueidentifier NOT NULL,
    capture_utc datetime2(7) NOT NULL,
    published_utc datetime2(7) NOT NULL,
    CONSTRAINT PK_dashboard_projection_partition PRIMARY KEY
        (projection_name, entity_name, source_scope, business_date),
    CONSTRAINT FK_dashboard_projection_dependency FOREIGN KEY (projection_name, entity_name)
        REFERENCES ctl.dashboard_projection_dependency(projection_name, entity_name),
    CONSTRAINT UQ_dashboard_projection_run UNIQUE (projection_name, applied_run_id)
);

INSERT ctl.dashboard_projection_dependency (projection_name, entity_name)
VALUES (N'COLETAS', 'COLETAS'), (N'COLETAS', 'COLETAS_EXCLUIDAS');

-- Os lotes continuam no recebimento. O índice relacional permite recalcular só os IDs afetados.
CREATE TABLE core.dashboard_pickup_occurrence_index (
    run_id uniqueidentifier NOT NULL,
    occurrence_ordinal int NOT NULL,
    pickup_id nvarchar(50) NOT NULL,
    entity_name varchar(40) NOT NULL,
    version_at datetimeoffset(7) NULL,
    capture_utc datetime2(7) NOT NULL,
    manifest bigint NULL,
    business_date date NOT NULL,
    CONSTRAINT PK_dashboard_pickup_occurrence_index PRIMARY KEY (run_id, occurrence_ordinal)
);
CREATE INDEX IX_dashboard_pickup_active_version
    ON core.dashboard_pickup_occurrence_index
        (pickup_id, version_at DESC, capture_utc DESC, manifest DESC, business_date DESC, occurrence_ordinal)
    INCLUDE (run_id) WHERE entity_name = 'COLETAS' WITH (MAXDOP = 1);
CREATE INDEX IX_dashboard_pickup_deleted_version
    ON core.dashboard_pickup_occurrence_index (pickup_id, version_at DESC, capture_utc DESC, occurrence_ordinal)
    INCLUDE (run_id) WHERE entity_name = 'COLETAS_EXCLUIDAS' WITH (MAXDOP = 1);

SELECT TOP (0)
    [ID],
    [Coleta],
    [Solicitacao],
    [Hora (Solicitacao)],
    [Agendamento],
    [Finalizacao],
    [Status],
    [Excluída na Origem],
    [Volumes],
    [Peso Real],
    [Peso Taxado],
    [Valor NF],
    [Numero Manifesto],
    [Veiculo],
    [Cliente],
    [Cliente Doc],
    [Local da Coleta],
    [Numero],
    [Complemento],
    [Cidade],
    [Bairro],
    [UF],
    [CEP],
    [Região da Coleta],
    [Região Logística],
    [Filial ID],
    [Filial],
    [Usuario],
    [Motivo Cancel.],
    [Usuario Cancel. ID],
    [Usuario Cancel. Nome],
    [Motivo Exclusao],
    [Usuario Exclusao ID],
    [Usuario Exclusao Nome],
    [Status Atualizado Em],
    [Status Atualizado Em (normalizado)],
    [Última Ocorrência],
    [Ação da Ocorrência],
    [Nº Tentativas],
    [Metadata],
    [Data de extracao]
INTO core.dashboard_pickup_current
FROM pub.vw_coletas_powerbi OPTION (MAXDOP 1);
ALTER TABLE core.dashboard_pickup_current ALTER COLUMN [ID] nvarchar(50) NOT NULL;
ALTER TABLE core.dashboard_pickup_current ADD
    em_publicacao bit NOT NULL CONSTRAINT DF_dashboard_pickup_publicado DEFAULT (1),
    CONSTRAINT PK_dashboard_pickup_current PRIMARY KEY ([ID]);
CREATE INDEX IX_dashboard_pickup_current_periodo
    ON core.dashboard_pickup_current ([Solicitacao], [Filial])
    INCLUDE ([Status], [Excluída na Origem], [Data de extracao])
    WHERE em_publicacao = 1 WITH (MAXDOP = 1);
GO

CREATE OR ALTER VIEW pub.vw_dashboard_particoes_pendentes
AS
SELECT d.projection_name, c.entity_name, c.source_scope, c.business_date,
       c.latest_run_id, p.applied_run_id, c.stored_utc, p.published_utc
FROM ctl.dashboard_projection_dependency d
JOIN ctl.production_ingestion_coverage c ON c.entity_name = d.entity_name
LEFT JOIN ctl.dashboard_projection_partition p ON p.projection_name = d.projection_name
    AND p.entity_name = c.entity_name AND p.source_scope = c.source_scope AND p.business_date = c.business_date
WHERE p.applied_run_id IS NULL OR p.applied_run_id <> c.latest_run_id;
GO

CREATE OR ALTER VIEW pub.vw_coletas_powerbi_preparada
AS
SELECT
    [ID],
    [Coleta],
    [Solicitacao],
    [Hora (Solicitacao)],
    [Agendamento],
    [Finalizacao],
    [Status],
    [Excluída na Origem],
    [Volumes],
    [Peso Real],
    [Peso Taxado],
    [Valor NF],
    [Numero Manifesto],
    [Veiculo],
    [Cliente],
    [Cliente Doc],
    [Local da Coleta],
    [Numero],
    [Complemento],
    [Cidade],
    [Bairro],
    [UF],
    [CEP],
    [Região da Coleta],
    [Região Logística],
    [Filial ID],
    [Filial],
    [Usuario],
    [Motivo Cancel.],
    [Usuario Cancel. ID],
    [Usuario Cancel. Nome],
    [Motivo Exclusao],
    [Usuario Exclusao ID],
    [Usuario Exclusao Nome],
    [Status Atualizado Em],
    [Status Atualizado Em (normalizado)],
    [Última Ocorrência],
    [Ação da Ocorrência],
    [Nº Tentativas],
    [Metadata],
    [Data de extracao]
FROM core.dashboard_pickup_current
WHERE em_publicacao = 1;
GO

CREATE OR ALTER PROCEDURE core.usp_dashboard_preparar_coletas
    @entity_name varchar(40), @source_scope char(64), @business_date date
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    SET LOCK_TIMEOUT 3000;
    IF @@TRANCOUNT <> 0 THROW 52030, 'COLETAS_OUTER_TRANSACTION_REFUSED', 1;
    IF @entity_name IS NULL OR @entity_name NOT IN ('COLETAS', 'COLETAS_EXCLUIDAS')
        OR @source_scope IS NULL OR @business_date IS NULL
        THROW 52031, 'COLETAS_PARTITION_REQUIRED', 1;

    BEGIN TRY
        BEGIN TRANSACTION;
        DECLARE @lock int;
        EXEC @lock = sys.sp_getapplock @Resource = N'dashboard:coletas:publicar',
            @LockMode = 'Exclusive', @LockOwner = 'Transaction', @LockTimeout = 0;
        IF @lock < 0 THROW 52032, 'COLETAS_PUBLICATION_BUSY', 1;

        IF (SELECT COUNT(DISTINCT source_scope) FROM ctl.production_ingestion_coverage
                WHERE entity_name IN ('COLETAS', 'COLETAS_EXCLUIDAS')) <> 1
            THROW 52033, 'COLETAS_SINGLE_SOURCE_REQUIRED', 1;

        DECLARE @run uniqueidentifier, @previous uniqueidentifier, @expected int, @capture datetime2(7);
        SELECT @run = c.latest_run_id, @expected = r.row_count, @capture = c.stored_utc
        FROM ctl.production_ingestion_coverage c
        JOIN ctl.production_ingestion_run r ON r.run_id = c.latest_run_id AND r.run_state = 'STORED'
            AND r.source_scope = c.source_scope AND r.entity_name = c.entity_name
            AND r.period_start = c.business_date AND r.period_end = c.business_date
        WHERE c.entity_name = @entity_name AND c.source_scope = @source_scope AND c.business_date = @business_date;
        IF @run IS NULL OR @expected IS NULL OR @expected < 0 OR @expected > 100000
            THROW 52034, 'COLETAS_COMPLETE_BOUNDED_CAPTURE_REQUIRED', 1;

        SELECT @previous = applied_run_id
        FROM ctl.dashboard_projection_partition
        WHERE projection_name = N'COLETAS' AND entity_name = @entity_name
            AND source_scope = @source_scope AND business_date = @business_date;
        IF @previous = @run
        BEGIN
            COMMIT;
            RETURN;
        END;

        INSERT core.dashboard_pickup_occurrence_index
            (run_id, occurrence_ordinal, pickup_id, entity_name, version_at, capture_utc, manifest, business_date)
        SELECT @run, o.occurrence_ordinal, CONVERT(nvarchar(50), j.pickup_id), @entity_name,
               CONVERT(datetimeoffset(7), CASE WHEN @entity_name='COLETAS' THEN JSON_VALUE(o.source_envelope,'$.record.updated_at')
                   ELSE JSON_VALUE(o.source_envelope,'$.record.deleted_at') END), @capture,
               CASE WHEN @entity_name='COLETAS' THEN CONVERT(bigint,JSON_VALUE(o.source_envelope,'$.record.pck_mik_mft_sequence_code')) END,
               @business_date
        FROM stg.production_export_occurrence o
        CROSS APPLY (VALUES (JSON_VALUE(o.source_envelope, '$.record.id'))) j(pickup_id)
        WHERE o.run_id = @run AND NULLIF(TRIM(j.pickup_id), N'') IS NOT NULL
            AND DATALENGTH(j.pickup_id) <= 100
        OPTION (MAXDOP 1, MAX_GRANT_PERCENT = 0.5);
        IF @@ROWCOUNT <> @expected THROW 52035, 'COLETAS_IDENTITY_COUNT_MISMATCH', 1;

        CREATE TABLE #afetadas (pickup_id nvarchar(50) COLLATE DATABASE_DEFAULT NOT NULL PRIMARY KEY);
        INSERT #afetadas (pickup_id)
        SELECT DISTINCT pickup_id FROM core.dashboard_pickup_occurrence_index
        WHERE run_id IN (@run, @previous)
        OPTION (MAXDOP 1, MAX_GRANT_PERCENT = 0.5);

        -- Índice derivado: mantém só os lotes aplicados. Os envelopes recebidos são imutáveis.
        DELETE FROM core.dashboard_pickup_occurrence_index WHERE run_id = @previous;

        UPDATE ctl.dashboard_projection_partition
        SET applied_run_id = @run, capture_utc = @capture, published_utc = SYSUTCDATETIME()
        WHERE projection_name = N'COLETAS' AND entity_name = @entity_name
            AND source_scope = @source_scope AND business_date = @business_date;
        IF @@ROWCOUNT = 0
            INSERT ctl.dashboard_projection_partition
                (projection_name, entity_name, source_scope, business_date, applied_run_id, capture_utc, published_utc)
            VALUES (N'COLETAS', @entity_name, @source_scope, @business_date, @run, @capture, SYSUTCDATETIME());

        -- Cada busca lê a primeira versão confirmada na ordem da projeção canônica.
        SELECT k.pickup_id, a.run_id AS active_run, a.occurrence_ordinal AS active_ordinal,
               a.capture_utc AS active_capture, d.run_id AS deleted_run, d.occurrence_ordinal AS deleted_ordinal,
               d.capture_utc AS deleted_capture
        INTO #vencedoras
        FROM #afetadas k
        OUTER APPLY (
            SELECT TOP (1) i.run_id, i.occurrence_ordinal, i.capture_utc
            FROM core.dashboard_pickup_occurrence_index i
            WHERE i.pickup_id=k.pickup_id AND i.entity_name='COLETAS'
            ORDER BY i.version_at DESC,i.capture_utc DESC,i.manifest DESC,i.business_date DESC,i.occurrence_ordinal
        ) a
        OUTER APPLY (
            SELECT TOP (1) i.run_id, i.occurrence_ordinal, i.capture_utc
            FROM core.dashboard_pickup_occurrence_index i
            WHERE i.pickup_id=k.pickup_id AND i.entity_name='COLETAS_EXCLUIDAS'
            ORDER BY i.version_at DESC,i.capture_utc DESC,i.occurrence_ordinal
        ) d
        WHERE a.run_id IS NOT NULL OR d.run_id IS NOT NULL
        OPTION (LOOP JOIN, MAXDOP 1, MAX_GRANT_PERCENT = 0.5);

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
        INTO #atualizadas
        FROM #vencedoras k
        OUTER APPLY (
            SELECT j.*, k.active_capture AS stored_utc, JSON_QUERY(o.source_envelope,'$.record') AS metadata
            FROM stg.production_export_occurrence o
            CROSS APPLY (SELECT TOP (1) * FROM OPENJSON(o.source_envelope,'$.record') WITH (
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
            )) j
            WHERE o.run_id=k.active_run AND o.occurrence_ordinal=k.active_ordinal
        ) a
        OUTER APPLY (
            SELECT j.*, k.deleted_capture AS stored_utc, JSON_QUERY(o.source_envelope,'$.record') AS metadata
            FROM stg.production_export_occurrence o
            CROSS APPLY (SELECT TOP (1) * FROM OPENJSON(o.source_envelope,'$.record') WITH (
                id nvarchar(50), sequence_code bigint, request_date date, deleted_at datetimeoffset(7),
                invoices_value decimal(18,2), taxed_weight decimal(18,3), destroy_reason nvarchar(max),
                deletion_user nvarchar(255) '$.dek_dyr_name', branch nvarchar(255) '$.dek_crn_psn_nickname'
            )) j
            WHERE o.run_id=k.deleted_run AND o.occurrence_ordinal=k.deleted_ordinal
        ) d
        CROSS APPLY (SELECT CONVERT(bit,CASE WHEN d.id IS NOT NULL AND (a.updated_at IS NULL OR d.deleted_at>=a.updated_at)
            THEN 1 ELSE 0 END) AS deleted) x
        OPTION (LOOP JOIN, MAXDOP 1, MAX_GRANT_PERCENT = 0.5);

        UPDATE p SET em_publicacao = 1,
            p.[Coleta] = n.[Coleta],
            p.[Solicitacao] = n.[Solicitacao],
            p.[Hora (Solicitacao)] = n.[Hora (Solicitacao)],
            p.[Agendamento] = n.[Agendamento],
            p.[Finalizacao] = n.[Finalizacao],
            p.[Status] = n.[Status],
            p.[Excluída na Origem] = n.[Excluída na Origem],
            p.[Volumes] = n.[Volumes],
            p.[Peso Real] = n.[Peso Real],
            p.[Peso Taxado] = n.[Peso Taxado],
            p.[Valor NF] = n.[Valor NF],
            p.[Numero Manifesto] = n.[Numero Manifesto],
            p.[Veiculo] = n.[Veiculo],
            p.[Cliente] = n.[Cliente],
            p.[Cliente Doc] = n.[Cliente Doc],
            p.[Local da Coleta] = n.[Local da Coleta],
            p.[Numero] = n.[Numero],
            p.[Complemento] = n.[Complemento],
            p.[Cidade] = n.[Cidade],
            p.[Bairro] = n.[Bairro],
            p.[UF] = n.[UF],
            p.[CEP] = n.[CEP],
            p.[Região da Coleta] = n.[Região da Coleta],
            p.[Região Logística] = n.[Região Logística],
            p.[Filial ID] = n.[Filial ID],
            p.[Filial] = n.[Filial],
            p.[Usuario] = n.[Usuario],
            p.[Motivo Cancel.] = n.[Motivo Cancel.],
            p.[Usuario Cancel. ID] = n.[Usuario Cancel. ID],
            p.[Usuario Cancel. Nome] = n.[Usuario Cancel. Nome],
            p.[Motivo Exclusao] = n.[Motivo Exclusao],
            p.[Usuario Exclusao ID] = n.[Usuario Exclusao ID],
            p.[Usuario Exclusao Nome] = n.[Usuario Exclusao Nome],
            p.[Status Atualizado Em] = n.[Status Atualizado Em],
            p.[Status Atualizado Em (normalizado)] = n.[Status Atualizado Em (normalizado)],
            p.[Última Ocorrência] = n.[Última Ocorrência],
            p.[Ação da Ocorrência] = n.[Ação da Ocorrência],
            p.[Nº Tentativas] = n.[Nº Tentativas],
            p.[Metadata] = n.[Metadata],
            p.[Data de extracao] = n.[Data de extracao]
        FROM core.dashboard_pickup_current p JOIN #atualizadas n ON n.[ID] = p.[ID]
        OPTION (LOOP JOIN, MAXDOP 1, MAX_GRANT_PERCENT = 0.5);

        INSERT core.dashboard_pickup_current (
            [ID],
            [Coleta],
            [Solicitacao],
            [Hora (Solicitacao)],
            [Agendamento],
            [Finalizacao],
            [Status],
            [Excluída na Origem],
            [Volumes],
            [Peso Real],
            [Peso Taxado],
            [Valor NF],
            [Numero Manifesto],
            [Veiculo],
            [Cliente],
            [Cliente Doc],
            [Local da Coleta],
            [Numero],
            [Complemento],
            [Cidade],
            [Bairro],
            [UF],
            [CEP],
            [Região da Coleta],
            [Região Logística],
            [Filial ID],
            [Filial],
            [Usuario],
            [Motivo Cancel.],
            [Usuario Cancel. ID],
            [Usuario Cancel. Nome],
            [Motivo Exclusao],
            [Usuario Exclusao ID],
            [Usuario Exclusao Nome],
            [Status Atualizado Em],
            [Status Atualizado Em (normalizado)],
            [Última Ocorrência],
            [Ação da Ocorrência],
            [Nº Tentativas],
            [Metadata],
            [Data de extracao])
        SELECT
            [ID],
            [Coleta],
            [Solicitacao],
            [Hora (Solicitacao)],
            [Agendamento],
            [Finalizacao],
            [Status],
            [Excluída na Origem],
            [Volumes],
            [Peso Real],
            [Peso Taxado],
            [Valor NF],
            [Numero Manifesto],
            [Veiculo],
            [Cliente],
            [Cliente Doc],
            [Local da Coleta],
            [Numero],
            [Complemento],
            [Cidade],
            [Bairro],
            [UF],
            [CEP],
            [Região da Coleta],
            [Região Logística],
            [Filial ID],
            [Filial],
            [Usuario],
            [Motivo Cancel.],
            [Usuario Cancel. ID],
            [Usuario Cancel. Nome],
            [Motivo Exclusao],
            [Usuario Exclusao ID],
            [Usuario Exclusao Nome],
            [Status Atualizado Em],
            [Status Atualizado Em (normalizado)],
            [Última Ocorrência],
            [Ação da Ocorrência],
            [Nº Tentativas],
            [Metadata],
            [Data de extracao]
        FROM #atualizadas n
        WHERE NOT EXISTS (SELECT 1 FROM core.dashboard_pickup_current p WHERE p.[ID] = n.[ID])
        OPTION (LOOP JOIN, MAXDOP 1, MAX_GRANT_PERCENT = 0.5);

        UPDATE p SET em_publicacao = 0
        FROM core.dashboard_pickup_current p JOIN #afetadas a ON a.pickup_id = p.[ID]
        WHERE p.em_publicacao = 1 AND NOT EXISTS (SELECT 1 FROM #atualizadas n WHERE n.[ID] = p.[ID])
        OPTION (LOOP JOIN, MAXDOP 1, MAX_GRANT_PERCENT = 0.5);

        -- O recebimento pode substituir a partição durante o preparo. Só a confirmação
        -- final precisa segurar a cobertura até o commit.
        IF NOT EXISTS (SELECT 1 FROM ctl.production_ingestion_coverage WITH (UPDLOCK, HOLDLOCK)
            WHERE entity_name = @entity_name AND source_scope = @source_scope
              AND business_date = @business_date AND latest_run_id = @run)
            THROW 52036, 'COLETAS_CAPTURE_SUPERSEDED', 1;
        IF EXISTS (SELECT 1 FROM ctl.production_ingestion_coverage WITH (HOLDLOCK)
            WHERE entity_name IN ('COLETAS','COLETAS_EXCLUIDAS') AND source_scope<>@source_scope)
            THROW 52033, 'COLETAS_SINGLE_SOURCE_REQUIRED', 1;

        COMMIT;
    END TRY
    BEGIN CATCH
        IF XACT_STATE() <> 0 ROLLBACK;
        THROW;
    END CATCH;
END;
GO
