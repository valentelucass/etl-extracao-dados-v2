-- V2-035b (fatia Usuários): projeção dimensional interna do estado atual já governado por V007.
-- Não é o contrato consumidor compatível, que continua pertencendo a V2-037.

SET XACT_ABORT ON;
SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;
GO

CREATE VIEW core.v_usuario_dimension_current_v1
WITH SCHEMABINDING
AS
    SELECT
        usuario.usuario_id,
        usuario.environment_name,
        usuario.source_instance,
        usuario.tenant_scope,
        usuario.source_key AS source_key_token,
        usuario.source_key_wire_type,
        usuario.name_presence,
        usuario.usuario_name,
        usuario.last_changed_at_utc,
        usuario.last_seen_at_utc
    FROM core.usuario AS usuario
    WHERE usuario.active = CONVERT(BIT, 1);
GO
