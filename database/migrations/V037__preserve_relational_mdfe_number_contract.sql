-- ADR0048 / REL-LAB-10: preserve V012's canonical arbitrary-precision MDF-e number.
-- The previous lab projection narrowed a valid NVARCHAR(128) integer to BIGINT.
SET XACT_ABORT ON;
ALTER TABLE stg.relational_lab_mdfe DROP CONSTRAINT CK_relational_lab_mdfe;
ALTER TABLE stg.relational_lab_mdfe
    ALTER COLUMN mdfe_number NVARCHAR(128) COLLATE Latin1_General_100_BIN2 NOT NULL;
ALTER TABLE stg.relational_lab_mdfe ADD CONSTRAINT CK_relational_lab_mdfe CHECK(
    mdfe_key NOT LIKE '%[^0-9]%'
    AND mdfe_number LIKE N'[1-9]%' AND mdfe_number NOT LIKE N'%[^0-9]%'
    AND DATALENGTH(mdfe_number)=2*LEN(mdfe_number));
GO
