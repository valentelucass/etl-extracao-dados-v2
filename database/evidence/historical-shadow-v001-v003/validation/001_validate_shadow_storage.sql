-- Validação somente leitura do schema de sombra já migrado.
-- Nunca execute contra um banco de domínio ou o banco legado.

SET NOCOUNT ON;

DECLARE @failures TABLE (
    category NVARCHAR(40) NOT NULL,
    object_name NVARCHAR(256) NOT NULL,
    detail NVARCHAR(400) NOT NULL
);

INSERT INTO @failures (category, object_name, detail)
SELECT N'SCHEMA', expected.schema_name, N'Schema de sombra ausente.'
FROM (VALUES (N'ctl'), (N'stg'), (N'shadow'), (N'recon')) AS expected(schema_name)
WHERE SCHEMA_ID(expected.schema_name) IS NULL;

INSERT INTO @failures (category, object_name, detail)
SELECT N'TABLE', expected.object_name, N'Tabela de controle ausente.'
FROM (VALUES (N'ctl.execution_audit'), (N'ctl.page_audit'), (N'ctl.source_watermark')) AS expected(object_name)
WHERE OBJECT_ID(expected.object_name, N'U') IS NULL;

INSERT INTO @failures (category, object_name, detail)
SELECT N'PROCEDURE', expected.object_name, N'Procedure de auditoria ausente.'
FROM (
    VALUES
        (N'ctl.usp_audit_execution_started'),
        (N'ctl.usp_audit_page_read'),
        (N'ctl.usp_audit_execution_completed'),
        (N'ctl.usp_audit_execution_failed')
) AS expected(object_name)
WHERE OBJECT_ID(expected.object_name, N'P') IS NULL;

INSERT INTO @failures (category, object_name, detail)
SELECT N'ROLE', N'v2_shadow_runtime', N'Role de runtime ausente.'
WHERE DATABASE_PRINCIPAL_ID(N'v2_shadow_runtime') IS NULL;

IF EXISTS (SELECT 1 FROM @failures)
BEGIN
    SELECT category, object_name, detail
    FROM @failures
    ORDER BY category, object_name;
    THROW 51087, N'Schema de sombra incompleto.', 1;
END;

PRINT N'Schema de sombra validado com sucesso.';
