-- Physical Java/JDBC/SQL proof exposed an EXCEPT collation mismatch with V010's BIN2 presence.
-- Preserve applied V027. Align the typed root projection; no semantic comparison is weakened.
SET XACT_ABORT ON;
ALTER TABLE core.coleta_temporal_laboratory
    ALTER COLUMN sequence_code_presence NVARCHAR(8) COLLATE Latin1_General_100_BIN2 NOT NULL;
GO
