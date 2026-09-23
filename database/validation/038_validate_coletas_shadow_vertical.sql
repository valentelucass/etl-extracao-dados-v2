-- Validação somente leitura da vertical Coletas 6908 em sombra.
SET NOCOUNT ON;

DECLARE @failures TABLE (category NVARCHAR(64), object_name NVARCHAR(256), detail NVARCHAR(512));
DECLARE @required_objects TABLE (schema_name SYSNAME, object_name SYSNAME, object_type CHAR(2));
INSERT INTO @required_objects VALUES
    (N'stg', N'coleta_record', N'U'),
    (N'ctl', N'coleta_promotion_result', N'U'),
    (N'core', N'coleta', N'U'),
    (N'ref', N'coleta_sequence_code_alias', N'U'),
    (N'recon', N'coleta_root_presence_observation', N'U'),
    (N'stg', N'usp_stage_coleta_record', N'P'),
    (N'core', N'usp_apply_reconcile_publish_coletas', N'P');

INSERT INTO @failures
SELECT N'OBJECT', CONCAT(required.schema_name, N'.', required.object_name), N'Objeto obrigatório ausente.'
FROM @required_objects AS required
WHERE OBJECT_ID(CONCAT(required.schema_name, N'.', required.object_name), required.object_type) IS NULL;

DECLARE @stage_definition NVARCHAR(MAX) = OBJECT_DEFINITION(OBJECT_ID(N'stg.usp_stage_coleta_record'));
DECLARE @apply_definition NVARCHAR(MAX) = OBJECT_DEFINITION(OBJECT_ID(N'core.usp_apply_reconcile_publish_coletas'));
DECLARE @retro_terminal_disposition INT = CHARINDEX(
    N'WHEN current_record.terminal = 0 AND typed.terminal = 1 THEN N''UPDATED''',
    @apply_definition);
DECLARE @stale_disposition INT = CHARINDEX(
    N'WHEN generic_application.application_disposition = N''STALE_NO_OP'' THEN N''STALE_NO_OP''',
    @apply_definition);
DECLARE @retro_terminal_apply INT = CHARINDEX(
    N'WHEN current_record.terminal = 0 AND typed.terminal = 1 THEN 1',
    @apply_definition);
DECLARE @stale_apply INT = CHARINDEX(
    N'WHEN generic_application.application_disposition = N''STALE_NO_OP'' THEN 0',
    @apply_definition);
IF @stage_definition IS NULL
   OR @stage_definition NOT LIKE N'%@input_record_ordinal NOT BETWEEN 1 AND 100%'
   OR @stage_definition NOT LIKE N'%coletas-status-v1%'
   OR @stage_definition NOT LIKE N'%coletas-envelope-v1%'
   OR @stage_definition NOT LIKE N'%coletas-presence-v1%'
   OR @stage_definition NOT LIKE N'%sidecar tardio%'
    INSERT INTO @failures VALUES (N'STAGE', N'stg.usp_stage_coleta_record',
        N'O boundary não prova limite, catálogo, fingerprint e fence de retry.');

IF @apply_definition IS NULL
   OR @apply_definition NOT LIKE N'%BEGIN TRANSACTION%'
   OR @apply_definition NOT LIKE N'%EXEC core.usp_apply_reconcile_publish_execution%'
   OR @retro_terminal_disposition = 0 OR @stale_disposition = 0
   OR @retro_terminal_disposition >= @stale_disposition
   OR @retro_terminal_apply = 0 OR @stale_apply = 0
   OR @retro_terminal_apply >= @stale_apply
   OR @apply_definition NOT LIKE N'%current_record.terminal = 1 AND typed.terminal = 0%'
   OR @apply_definition NOT LIKE N'%BLOCKED_NO_COMPLETENESS_PROOF%'
   OR @apply_definition LIKE N'%DEACTIVATED%'
   OR @apply_definition LIKE N'%MERGE %'
    INSERT INTO @failures VALUES (N'APPLY', N'core.usp_apply_reconcile_publish_coletas',
        N'A promoção não está atômica, set-based ou não aplica os dois CASE de COL-03 antes de stale.');

IF EXISTS (SELECT 1 FROM sys.objects AS object_definition
           WHERE object_definition.schema_id = SCHEMA_ID(N'pub')
             AND object_definition.name LIKE N'%coleta%')
    INSERT INTO @failures VALUES (N'PUBLICATION', N'pub',
        N'V2-010 não autoriza objeto publicado de Coletas.');

IF EXISTS (
    SELECT 1 FROM sys.database_permissions AS permission_definition
    WHERE permission_definition.grantee_principal_id = DATABASE_PRINCIPAL_ID(N'v2_runtime')
      AND permission_definition.state IN (N'G', N'W') AND permission_definition.class = 1
      AND permission_definition.major_id IN (
          OBJECT_ID(N'stg.coleta_record'), OBJECT_ID(N'core.coleta'),
          OBJECT_ID(N'ref.coleta_sequence_code_alias'), OBJECT_ID(N'recon.coleta_root_presence_observation')
      )
      AND permission_definition.permission_name IN (N'SELECT', N'INSERT', N'UPDATE', N'DELETE')
)
    INSERT INTO @failures VALUES (N'PERMISSION', N'v2_runtime',
        N'Runtime recebeu DML direto na vertical Coletas.');

IF EXISTS (SELECT 1 FROM @failures)
BEGIN
    SELECT category, object_name, detail FROM @failures ORDER BY category, object_name;
    THROW 51871, N'Contrato estrutural de Coletas divergiu.', 1;
END;

PRINT N'Coletas V2 em sombra validadas com sucesso.';
