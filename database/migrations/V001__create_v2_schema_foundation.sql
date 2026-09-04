-- Fundação Flyway V2. Não cria banco, login, usuário, job ou objeto fora deste banco.
-- Objetos de domínio, staging, control plane, referências, fatos e contratos entram apenas
-- nas migrations das tarefas donas, depois de contrato e identidade aprovados.

SET XACT_ABORT ON;

IF SCHEMA_ID(N'shadow') IS NOT NULL
BEGIN
    THROW 51200, N'O schema histórico shadow não pertence à fundação V2 limpa.', 1;
END;

IF SCHEMA_ID(N'ctl') IS NULL
BEGIN
    EXEC(N'CREATE SCHEMA ctl AUTHORIZATION dbo;');
END;

IF SCHEMA_ID(N'stg') IS NULL
BEGIN
    EXEC(N'CREATE SCHEMA stg AUTHORIZATION dbo;');
END;

IF SCHEMA_ID(N'core') IS NULL
BEGIN
    EXEC(N'CREATE SCHEMA core AUTHORIZATION dbo;');
END;

IF SCHEMA_ID(N'ref') IS NULL
BEGIN
    EXEC(N'CREATE SCHEMA ref AUTHORIZATION dbo;');
END;

IF SCHEMA_ID(N'mart') IS NULL
BEGIN
    EXEC(N'CREATE SCHEMA mart AUTHORIZATION dbo;');
END;

IF SCHEMA_ID(N'pub') IS NULL
BEGIN
    EXEC(N'CREATE SCHEMA pub AUTHORIZATION dbo;');
END;

IF SCHEMA_ID(N'recon') IS NULL
BEGIN
    EXEC(N'CREATE SCHEMA recon AUTHORIZATION dbo;');
END;
GO
