-- Baseline SQLCMD do armazenamento de sombra.
-- Execute somente contra o banco V2 de sombra já aprovado. Não execute este arquivo pelo Flyway:
-- o Flyway usa os scripts versionados em ../migrations.
-- O baseline inclui exatamente as migrations atuais, em ordem, para manter a recriação equivalente.

:r "..\migrations\V001__create_shadow_schemas.sql"
:r "..\migrations\V002__create_shadow_audit_tables.sql"
:r "..\migrations\V003__create_shadow_audit_procedures.sql"
