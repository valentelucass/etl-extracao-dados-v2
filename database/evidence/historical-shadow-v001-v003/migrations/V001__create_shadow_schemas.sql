-- Flyway migration: cria somente os schemas internos do banco de sombra já provisionado.
-- Não cria banco, login, usuário, job, synonym ou objeto em ETL_SISTEMA.

SET XACT_ABORT ON;
GO

IF SCHEMA_ID(N'ctl') IS NULL
BEGIN
    EXEC(N'CREATE SCHEMA ctl AUTHORIZATION dbo;');
END;
GO

IF SCHEMA_ID(N'stg') IS NULL
BEGIN
    EXEC(N'CREATE SCHEMA stg AUTHORIZATION dbo;');
END;
GO

IF SCHEMA_ID(N'shadow') IS NULL
BEGIN
    EXEC(N'CREATE SCHEMA shadow AUTHORIZATION dbo;');
END;
GO

IF SCHEMA_ID(N'recon') IS NULL
BEGIN
    EXEC(N'CREATE SCHEMA recon AUTHORIZATION dbo;');
END;
GO
