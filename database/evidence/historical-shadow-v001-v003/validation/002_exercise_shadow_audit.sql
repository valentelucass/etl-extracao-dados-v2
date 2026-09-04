-- Exercício transacional da auditoria de sombra.
-- Executar somente no banco ETL_SISTEMA_V2_SHADOW depois da validação estrutural.
-- Não deixa dados: toda a execução sintética é revertida ao final.

SET XACT_ABORT ON;
SET NOCOUNT ON;

BEGIN TRY
    BEGIN TRANSACTION;

    DECLARE @execution_id UNIQUEIDENTIFIER = NEWID();
    DECLARE @now DATETIME2(3) = SYSUTCDATETIME();

    EXEC ctl.usp_audit_execution_started
        @execution_id = @execution_id,
        @template_id = 6908,
        @business_window_start = '20260101',
        @business_window_end = '20260101',
        @updated_at_window_start = NULL,
        @updated_at_window_end = NULL,
        @started_at = @now;

    -- Doze linhas físicas podem representar dez entidades quando o relatório expande relações.
    EXEC ctl.usp_audit_page_read
        @execution_id = @execution_id,
        @page_number = 1,
        @requested_per = 10,
        @record_count = 12,
        @distinct_entity_count = 10,
        @read_at = @now;

    EXEC ctl.usp_audit_page_read
        @execution_id = @execution_id,
        @page_number = 2,
        @requested_per = 10,
        @record_count = 0,
        @distinct_entity_count = 0,
        @read_at = @now;

    EXEC ctl.usp_audit_execution_completed
        @execution_id = @execution_id,
        @pages_fetched = 2,
        @records_delivered = 12,
        @terminal_page = 2,
        @completed_at = @now,
        @traversal_verification = N'LOCAL_TERMINAL_UNVERIFIED';

    IF NOT EXISTS (
        SELECT 1
        FROM ctl.execution_audit
        WHERE execution_id = @execution_id
          AND status = N'COMPLETED'
          AND pages_fetched = 2
          AND records_delivered = 12
          AND terminal_page = 2
    )
    BEGIN
        THROW 51090, N'Execução sintética não foi auditada corretamente.', 1;
    END;

    IF NOT EXISTS (
        SELECT 1
        FROM ctl.page_audit
        WHERE execution_id = @execution_id
          AND page_number = 1
          AND requested_per = 10
          AND record_count = 12
          AND distinct_entity_count = 10
          AND is_terminal = 0
    )
       OR NOT EXISTS (
        SELECT 1
        FROM ctl.page_audit
        WHERE execution_id = @execution_id
          AND page_number = 2
          AND requested_per = 10
          AND record_count = 0
          AND distinct_entity_count = 0
          AND is_terminal = 1
    )
    BEGIN
        THROW 51091, N'Página sintética não foi auditada corretamente.', 1;
    END;

    INSERT INTO ctl.source_watermark (entity_name, template_id)
    VALUES (N'fretes', 6389);

    IF NOT EXISTS (
        SELECT 1
        FROM ctl.source_watermark
        WHERE entity_name = N'fretes'
          AND template_id = 6389
          AND watermark_state = N'UNVERIFIED'
          AND source_watermark_utc IS NULL
          AND confirmed_execution_id IS NULL
          AND confirmed_at IS NULL
    )
    BEGIN
        THROW 51092, N'Watermark não verificado não foi protegido corretamente.', 1;
    END;

    ROLLBACK TRANSACTION;
    PRINT N'Procedures de auditoria de sombra exercitadas e revertidas com sucesso.';
END TRY
BEGIN CATCH
    IF XACT_STATE() <> 0
    BEGIN
        ROLLBACK TRANSACTION;
    END;
    THROW;
END CATCH;
