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
    CONSTRAINT CK_ctl_execution_audit_template CHECK (template_id IN (6908, 6389)),
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

CREATE TABLE ctl.source_watermark (
    entity_name NVARCHAR(50) NOT NULL,
    template_id INT NOT NULL,
    watermark_state NVARCHAR(24) NOT NULL CONSTRAINT DF_ctl_source_watermark_state DEFAULT (N'UNVERIFIED'),
    source_watermark_utc DATETIME2(0) NULL,
    confirmed_execution_id UNIQUEIDENTIFIER NULL,
    confirmed_at DATETIME2(3) NULL,
    CONSTRAINT PK_ctl_source_watermark PRIMARY KEY CLUSTERED (entity_name),
    CONSTRAINT UQ_ctl_source_watermark_template UNIQUE (template_id),
    CONSTRAINT FK_ctl_source_watermark_execution
        FOREIGN KEY (confirmed_execution_id) REFERENCES ctl.execution_audit (execution_id),
    CONSTRAINT CK_ctl_source_watermark_template CHECK (template_id IN (6908, 6389)),
    CONSTRAINT CK_ctl_source_watermark_state CHECK (
        (watermark_state = N'UNVERIFIED'
            AND source_watermark_utc IS NULL
            AND confirmed_execution_id IS NULL
            AND confirmed_at IS NULL)
        OR (watermark_state = N'CONFIRMED'
            AND source_watermark_utc IS NOT NULL
            AND confirmed_execution_id IS NOT NULL
            AND confirmed_at IS NOT NULL)
    )
);
GO
