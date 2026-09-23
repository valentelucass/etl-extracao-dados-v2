-- ADR0047: additive compatibility for JdbcDataExportExtractionAudit, Coletas only.
-- Historical scripts remain immutable. No watermark, principal or grant is installed.
-- Flyway migration: metadados sanitizados de auditoria e controle de watermark.
-- Nenhuma tabela desta migration retém payload, URL, token, cabeçalho, ID de negócio ou hash.

SET XACT_ABORT ON;
GO

CREATE TABLE ctl.execution_audit (
    execution_id UNIQUEIDENTIFIER NOT NULL,
    template_id INT NOT NULL,
    business_window_start DATE NOT NULL,
    business_window_end DATE NOT NULL,
    updated_at_window_start DATETIME2(0) NULL,
    updated_at_window_end DATETIME2(0) NULL,
    status NVARCHAR(20) NOT NULL,
    pages_fetched INT NOT NULL CONSTRAINT DF_ctl_execution_audit_pages_fetched DEFAULT (0),
    records_delivered BIGINT NOT NULL CONSTRAINT DF_ctl_execution_audit_records_delivered DEFAULT (0),
    terminal_page INT NULL,
    traversal_verification NVARCHAR(64) NULL,
    failure_category NVARCHAR(100) NULL,
    started_at DATETIME2(3) NOT NULL,
    completed_at DATETIME2(3) NULL,
    created_at DATETIME2(3) NOT NULL CONSTRAINT DF_ctl_execution_audit_created_at DEFAULT SYSUTCDATETIME(),
    updated_at DATETIME2(3) NOT NULL CONSTRAINT DF_ctl_execution_audit_updated_at DEFAULT SYSUTCDATETIME(),
    CONSTRAINT PK_ctl_execution_audit PRIMARY KEY CLUSTERED (execution_id),
    CONSTRAINT CK_ctl_execution_audit_template CHECK (template_id = 6908),
    CONSTRAINT CK_ctl_execution_audit_status CHECK (status IN (N'STARTED', N'COMPLETED', N'FAILED')),
    CONSTRAINT CK_ctl_execution_audit_business_window
        CHECK (business_window_end >= business_window_start),
    CONSTRAINT CK_ctl_execution_audit_updated_window
        CHECK (
            (updated_at_window_start IS NULL AND updated_at_window_end IS NULL)
            OR (updated_at_window_start IS NOT NULL
                AND updated_at_window_end IS NOT NULL
                AND updated_at_window_end >= updated_at_window_start)
        ),
    CONSTRAINT CK_ctl_execution_audit_volumes CHECK (pages_fetched >= 0 AND records_delivered >= 0),
    CONSTRAINT CK_ctl_execution_audit_terminal_page CHECK (terminal_page IS NULL OR terminal_page >= 1)
);
GO

CREATE INDEX IX_ctl_execution_audit_status_started_at
    ON ctl.execution_audit (status, started_at DESC);
GO

CREATE TABLE ctl.page_audit (
    execution_id UNIQUEIDENTIFIER NOT NULL,
    page_number INT NOT NULL,
    requested_per INT NOT NULL,
    record_count INT NOT NULL,
    distinct_entity_count INT NOT NULL,
    is_terminal BIT NOT NULL CONSTRAINT DF_ctl_page_audit_is_terminal DEFAULT (0),
    read_at DATETIME2(3) NOT NULL,
    CONSTRAINT PK_ctl_page_audit PRIMARY KEY CLUSTERED (execution_id, page_number),
    CONSTRAINT FK_ctl_page_audit_execution
        FOREIGN KEY (execution_id) REFERENCES ctl.execution_audit (execution_id),
    CONSTRAINT CK_ctl_page_audit_page_number CHECK (page_number >= 1),
    CONSTRAINT CK_ctl_page_audit_requested_per CHECK (requested_per >= 1),
    CONSTRAINT CK_ctl_page_audit_record_count CHECK (record_count >= 0),
    CONSTRAINT CK_ctl_page_audit_distinct_entities
        CHECK (distinct_entity_count >= 0 AND distinct_entity_count <= requested_per)
);
GO

CREATE INDEX IX_ctl_page_audit_read_at
    ON ctl.page_audit (read_at DESC, execution_id);
GO

-- Flyway migration: procedures idempotentes da auditoria de sombra.
-- A role de runtime recebe apenas EXECUTE nestas procedures; DDL fica fora do runtime.

SET XACT_ABORT ON;
GO

CREATE OR ALTER PROCEDURE ctl.usp_audit_execution_started
    @execution_id UNIQUEIDENTIFIER,
    @template_id INT,
    @business_window_start DATE,
    @business_window_end DATE,
    @updated_at_window_start DATETIME2(0) = NULL,
    @updated_at_window_end DATETIME2(0) = NULL,
    @started_at DATETIME2(3)
AS
BEGIN
    SET NOCOUNT ON;

    IF @business_window_end < @business_window_start
    BEGIN
        THROW 51080, N'Janela de negócio inválida para auditoria de sombra.', 1;
    END;

    IF (@updated_at_window_start IS NULL AND @updated_at_window_end IS NOT NULL)
       OR (@updated_at_window_start IS NOT NULL AND @updated_at_window_end IS NULL)
       OR (@updated_at_window_end < @updated_at_window_start)
    BEGIN
        THROW 51081, N'Janela de atualização inválida para auditoria de sombra.', 1;
    END;

    MERGE ctl.execution_audit WITH (HOLDLOCK) AS target
    USING (
        SELECT
            @execution_id AS execution_id,
            @template_id AS template_id,
            @business_window_start AS business_window_start,
            @business_window_end AS business_window_end,
            @updated_at_window_start AS updated_at_window_start,
            @updated_at_window_end AS updated_at_window_end,
            @started_at AS started_at
    ) AS source
        ON target.execution_id = source.execution_id
    WHEN NOT MATCHED THEN
        INSERT (
            execution_id,
            template_id,
            business_window_start,
            business_window_end,
            updated_at_window_start,
            updated_at_window_end,
            status,
            started_at
        )
        VALUES (
            source.execution_id,
            source.template_id,
            source.business_window_start,
            source.business_window_end,
            source.updated_at_window_start,
            source.updated_at_window_end,
            N'STARTED',
            source.started_at
        );
END;
GO

CREATE OR ALTER PROCEDURE ctl.usp_audit_page_read
    @execution_id UNIQUEIDENTIFIER,
    @page_number INT,
    @requested_per INT,
    @record_count INT,
    @distinct_entity_count INT,
    @read_at DATETIME2(3)
AS
BEGIN
    SET NOCOUNT ON;

    IF @page_number < 1
       OR @requested_per < 1
       OR @record_count < 0
       OR @distinct_entity_count < 0
       OR @distinct_entity_count > @requested_per
    BEGIN
        THROW 51082, N'Página inválida para auditoria de sombra.', 1;
    END;

    MERGE ctl.page_audit WITH (HOLDLOCK) AS target
    USING (
        SELECT
            @execution_id AS execution_id,
            @page_number AS page_number,
            @requested_per AS requested_per,
            @record_count AS record_count,
            @distinct_entity_count AS distinct_entity_count,
            @read_at AS read_at
    ) AS source
        ON target.execution_id = source.execution_id
        AND target.page_number = source.page_number
    WHEN MATCHED THEN
        UPDATE SET
            requested_per = source.requested_per,
            record_count = source.record_count,
            distinct_entity_count = source.distinct_entity_count,
            read_at = source.read_at
    WHEN NOT MATCHED THEN
        INSERT (
            execution_id,
            page_number,
            requested_per,
            record_count,
            distinct_entity_count,
            read_at
        )
        VALUES (
            source.execution_id,
            source.page_number,
            source.requested_per,
            source.record_count,
            source.distinct_entity_count,
            source.read_at
        );
END;
GO

CREATE OR ALTER PROCEDURE ctl.usp_audit_execution_completed
    @execution_id UNIQUEIDENTIFIER,
    @pages_fetched INT,
    @records_delivered BIGINT,
    @terminal_page INT,
    @completed_at DATETIME2(3),
    @traversal_verification NVARCHAR(64)
AS
BEGIN
    SET NOCOUNT ON;

    IF @pages_fetched < 1 OR @records_delivered < 0 OR @terminal_page < 1
    BEGIN
        THROW 51083, N'Conclusão inválida para auditoria de sombra.', 1;
    END;

    UPDATE ctl.execution_audit
       SET status = N'COMPLETED',
           pages_fetched = @pages_fetched,
           records_delivered = @records_delivered,
           terminal_page = @terminal_page,
           traversal_verification = @traversal_verification,
           failure_category = NULL,
           completed_at = @completed_at,
           updated_at = SYSUTCDATETIME()
     WHERE execution_id = @execution_id
       AND status IN (N'STARTED', N'COMPLETED');

    IF @@ROWCOUNT = 0
    BEGIN
        THROW 51084, N'Execução de sombra não pode ser concluída no estado atual.', 1;
    END;

    UPDATE ctl.page_audit
       SET is_terminal = CASE WHEN page_number = @terminal_page THEN 1 ELSE 0 END
     WHERE execution_id = @execution_id;
END;
GO

CREATE OR ALTER PROCEDURE ctl.usp_audit_execution_failed
    @execution_id UNIQUEIDENTIFIER,
    @pages_fetched INT,
    @records_delivered BIGINT,
    @failed_at DATETIME2(3),
    @failure_category NVARCHAR(100)
AS
BEGIN
    SET NOCOUNT ON;

    IF @pages_fetched < 0 OR @records_delivered < 0 OR @failure_category IS NULL OR LEN(@failure_category) = 0
    BEGIN
        THROW 51085, N'Falha inválida para auditoria de sombra.', 1;
    END;

    UPDATE ctl.execution_audit
       SET status = N'FAILED',
           pages_fetched = @pages_fetched,
           records_delivered = @records_delivered,
           terminal_page = NULL,
           traversal_verification = NULL,
           failure_category = @failure_category,
           completed_at = @failed_at,
           updated_at = SYSUTCDATETIME()
     WHERE execution_id = @execution_id
       AND status IN (N'STARTED', N'FAILED');

    IF @@ROWCOUNT = 0
    BEGIN
        THROW 51086, N'Execução de sombra não pode falhar no estado atual.', 1;
    END;
END;
GO

