-- V2-035a: fundação offline de referências governadas.
-- Nenhuma linha produtiva, release ratificado, aprovação, principal ou membership nasce aqui.
-- Consumidores futuros devem fixar release_id explicitamente; não existe ponteiro/default corrente.

SET XACT_ABORT ON;
SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;

CREATE TABLE ref.reference_release (
    reference_release_id BIGINT IDENTITY(1, 1) NOT NULL,
    family_code NVARCHAR(32) COLLATE Latin1_General_100_BIN2 NOT NULL,
    scope_code NVARCHAR(128) COLLATE Latin1_General_100_BIN2 NOT NULL,
    release_version NVARCHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL,
    schema_version SMALLINT NOT NULL,
    source_kind NVARCHAR(32) COLLATE Latin1_General_100_BIN2 NOT NULL,
    source_artifact_ref NVARCHAR(128) COLLATE Latin1_General_100_BIN2 NOT NULL,
    source_fingerprint CHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL,
    source_row_count BIGINT NOT NULL,
    valid_from DATE NOT NULL,
    valid_to_exclusive DATE NOT NULL,
    reason_code NVARCHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL,
    author_role NVARCHAR(128) COLLATE Latin1_General_100_BIN2 NOT NULL,
    recorded_at_utc DATETIME2(3) NOT NULL
        CONSTRAINT DF_ref_reference_release_recorded_at DEFAULT (SYSUTCDATETIME()),
    CONSTRAINT PK_ref_reference_release PRIMARY KEY CLUSTERED (reference_release_id),
    CONSTRAINT UQ_ref_reference_release_identity UNIQUE (
        family_code, scope_code, release_version
    ),
    CONSTRAINT UQ_ref_reference_release_family UNIQUE (
        reference_release_id, family_code
    ),
    CONSTRAINT CK_ref_reference_release_family CHECK (
        family_code IN (
            N'CALENDAR', N'PICK_STATUS', N'BRANCH_OPERATIONS', N'OWNED_FLEET',
            N'BRANCH_ATTRIBUTION', N'CUBAGE_EXCLUSION', N'LOGISTICS_REGION',
            N'QUOTE_TARIFF'
        )
    ),
    CONSTRAINT CK_ref_reference_release_identity_text CHECK (
        DATALENGTH(scope_code) = DATALENGTH(LTRIM(RTRIM(scope_code)))
        AND DATALENGTH(release_version) = DATALENGTH(LTRIM(RTRIM(release_version)))
        AND LEN(scope_code) BETWEEN 1 AND 128
        AND LEN(release_version) BETWEEN 1 AND 64
        AND scope_code NOT LIKE N'%[' + NCHAR(0) + N'-' + NCHAR(31) + N']%'
        AND release_version NOT LIKE N'%[' + NCHAR(0) + N'-' + NCHAR(31) + N']%'
    ),
    CONSTRAINT CK_ref_reference_release_schema_version CHECK (schema_version = 1),
    CONSTRAINT CK_ref_reference_release_source CHECK (
        source_kind IN (N'DETERMINISTIC_LOCAL', N'AUTHORIZED_EXPORT', N'OWNER_AUTHORED')
        AND source_artifact_ref COLLATE Latin1_General_100_BIN2 LIKE N'[A-Z]%'
        AND source_artifact_ref COLLATE Latin1_General_100_BIN2 NOT LIKE N'%[^-A-Z0-9_]%'
        AND LEN(source_artifact_ref) BETWEEN 3 AND 128
        AND source_row_count BETWEEN 0 AND 100000
    ),
    CONSTRAINT CK_ref_reference_release_fingerprint CHECK (
        LEN(source_fingerprint) = 64
        AND source_fingerprint COLLATE Latin1_General_100_BIN2 NOT LIKE '%[^0-9a-f]%'
    ),
    CONSTRAINT CK_ref_reference_release_validity CHECK (valid_from < valid_to_exclusive),
    CONSTRAINT CK_ref_reference_release_governance CHECK (
        reason_code COLLATE Latin1_General_100_BIN2 LIKE N'[A-Z]%'
        AND reason_code COLLATE Latin1_General_100_BIN2 NOT LIKE N'%[^A-Z0-9_]%'
        AND LEN(reason_code) BETWEEN 3 AND 64
        AND author_role COLLATE Latin1_General_100_BIN2 LIKE N'[A-Z]%'
        AND author_role COLLATE Latin1_General_100_BIN2 NOT LIKE N'%[^A-Z0-9_]%'
        AND LEN(author_role) BETWEEN 3 AND 128
    )
);
GO

CREATE INDEX IX_ref_reference_release_scope_validity
    ON ref.reference_release (family_code, scope_code, valid_from, valid_to_exclusive)
    INCLUDE (reference_release_id, release_version, source_fingerprint, source_row_count);
GO

-- Registro idempotente do envelope; não importa conteúdo e não recebe GRANT nesta migration.
CREATE PROCEDURE ref.usp_register_reference_release
    @family_code NVARCHAR(32),
    @scope_code NVARCHAR(128),
    @release_version NVARCHAR(64),
    @schema_version SMALLINT,
    @source_kind NVARCHAR(32),
    @source_artifact_ref NVARCHAR(128),
    @source_fingerprint CHAR(64),
    @source_row_count BIGINT,
    @valid_from DATE,
    @valid_to_exclusive DATE,
    @reason_code NVARCHAR(64),
    @author_role NVARCHAR(128),
    @reference_release_id BIGINT OUTPUT,
    @registration_outcome NVARCHAR(16) OUTPUT
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    DECLARE @started_transaction BIT = 0;
    DECLARE @lock_result INT;
    DECLARE @lock_resource NVARCHAR(255) = CONCAT(
        N'V2_REF_REGISTER_', LOWER(CONVERT(CHAR(64), HASHBYTES(
            'SHA2_256', CONVERT(VARBINARY(MAX), CONCAT(
                DATALENGTH(@family_code), N':', @family_code, N'|',
                DATALENGTH(@scope_code), N':', @scope_code, N'|',
                DATALENGTH(@release_version), N':', @release_version
            ))
        ), 2))
    );
    SET @reference_release_id = NULL;
    SET @registration_outcome = NULL;

    IF @@TRANCOUNT = 0
    BEGIN
        BEGIN TRANSACTION;
        SET @started_transaction = 1;
    END;

    BEGIN TRY
        EXEC @lock_result = sys.sp_getapplock
            @Resource = @lock_resource,
            @LockMode = 'Exclusive',
            @LockOwner = 'Transaction',
            @LockTimeout = 5000,
            @DbPrincipal = 'public';
        IF @lock_result < 0
            THROW 52037, N'Não foi possível serializar o registro da release.', 1;

        SELECT @reference_release_id = reference_release_id
        FROM ref.reference_release WITH (UPDLOCK, HOLDLOCK)
        WHERE family_code = @family_code
          AND scope_code = @scope_code
          AND release_version = @release_version;

        IF @reference_release_id IS NOT NULL
        BEGIN
            IF NOT EXISTS (
                SELECT 1 FROM ref.reference_release
                WHERE reference_release_id = @reference_release_id
                  AND schema_version = @schema_version
                  AND source_kind = @source_kind
                  AND source_artifact_ref = @source_artifact_ref
                  AND source_fingerprint = @source_fingerprint
                  AND source_row_count = @source_row_count
                  AND valid_from = @valid_from
                  AND valid_to_exclusive = @valid_to_exclusive
                  AND reason_code = @reason_code
                  AND author_role = @author_role
            )
                THROW 52038, N'Identidade de release já existe com envelope divergente.', 1;
            SET @registration_outcome = N'REPLAY';
        END
        ELSE
        BEGIN
            INSERT INTO ref.reference_release (
                family_code, scope_code, release_version, schema_version, source_kind,
                source_artifact_ref, source_fingerprint, source_row_count, valid_from,
                valid_to_exclusive, reason_code, author_role
            ) VALUES (
                @family_code, @scope_code, @release_version, @schema_version, @source_kind,
                @source_artifact_ref, @source_fingerprint, @source_row_count, @valid_from,
                @valid_to_exclusive, @reason_code, @author_role
            );
            SET @reference_release_id = CONVERT(BIGINT, SCOPE_IDENTITY());
            SET @registration_outcome = N'CREATED';
        END;

        IF @started_transaction = 1 COMMIT TRANSACTION;
    END TRY
    BEGIN CATCH
        IF @started_transaction = 1 AND XACT_STATE() <> 0 ROLLBACK TRANSACTION;
        THROW;
    END CATCH;
END;
GO

-- O recibo sela o conteúdo físico antes de qualquer ratificação. O fingerprint é calculado
-- pelo importador limitado conforme o contrato canônico versionado; V008 não concede DML.
CREATE TABLE ref.reference_import_receipt (
    reference_release_id BIGINT NOT NULL,
    contract_version NVARCHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL,
    content_fingerprint CHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL,
    imported_row_count BIGINT NOT NULL,
    imported_byte_count BIGINT NOT NULL,
    importer_role NVARCHAR(128) COLLATE Latin1_General_100_BIN2 NOT NULL,
    recorded_at_utc DATETIME2(3) NOT NULL
        CONSTRAINT DF_ref_reference_import_receipt_at DEFAULT (SYSUTCDATETIME()),
    CONSTRAINT PK_ref_reference_import_receipt PRIMARY KEY CLUSTERED (reference_release_id),
    CONSTRAINT FK_ref_reference_import_receipt_release FOREIGN KEY (reference_release_id)
        REFERENCES ref.reference_release (reference_release_id),
    CONSTRAINT CK_ref_reference_import_receipt_contract CHECK (
        contract_version = N'governed-references-v1'
        AND LEN(content_fingerprint) = 64
        AND content_fingerprint COLLATE Latin1_General_100_BIN2 NOT LIKE '%[^0-9a-f]%'
        AND imported_row_count BETWEEN 0 AND 100000
        AND imported_byte_count BETWEEN 1 AND 16777216
        AND importer_role COLLATE Latin1_General_100_BIN2 LIKE N'[A-Z]%'
        AND importer_role COLLATE Latin1_General_100_BIN2 NOT LIKE N'%[^A-Z0-9_]%'
        AND LEN(importer_role) BETWEEN 3 AND 128
    )
);
GO

CREATE TABLE ref.reference_release_ratification (
    reference_release_id BIGINT NOT NULL,
    activation_scope NVARCHAR(16) COLLATE Latin1_General_100_BIN2 NOT NULL,
    approver_role NVARCHAR(128) COLLATE Latin1_General_100_BIN2 NOT NULL,
    approval_evidence_ref NVARCHAR(128) COLLATE Latin1_General_100_BIN2 NOT NULL,
    approval_fingerprint CHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL,
    ratified_at_utc DATETIME2(3) NOT NULL
        CONSTRAINT DF_ref_reference_ratification_at DEFAULT (SYSUTCDATETIME()),
    CONSTRAINT PK_ref_reference_release_ratification PRIMARY KEY CLUSTERED (
        reference_release_id, activation_scope
    ),
    CONSTRAINT FK_ref_reference_ratification_release FOREIGN KEY (reference_release_id)
        REFERENCES ref.reference_release (reference_release_id),
    CONSTRAINT CK_ref_reference_ratification_scope CHECK (
        activation_scope IN (N'SHADOW', N'PRODUCTION')
    ),
    CONSTRAINT CK_ref_reference_ratification_approval CHECK (
        approver_role COLLATE Latin1_General_100_BIN2 LIKE N'[A-Z]%'
        AND approver_role COLLATE Latin1_General_100_BIN2 NOT LIKE N'%[^A-Z0-9_]%'
        AND LEN(approver_role) BETWEEN 3 AND 128
        AND approval_evidence_ref COLLATE Latin1_General_100_BIN2 LIKE N'[A-Z]%'
        AND approval_evidence_ref COLLATE Latin1_General_100_BIN2
            NOT LIKE N'%[^-A-Z0-9_]%'
        AND LEN(approval_evidence_ref) BETWEEN 3 AND 128
        AND LEN(approval_fingerprint) = 64
        AND approval_fingerprint COLLATE Latin1_General_100_BIN2
            NOT LIKE '%[^0-9a-f]%'
    )
);
GO

CREATE TABLE ref.reference_release_revocation (
    reference_release_id BIGINT NOT NULL,
    activation_scope NVARCHAR(16) COLLATE Latin1_General_100_BIN2 NOT NULL,
    revoker_role NVARCHAR(128) COLLATE Latin1_General_100_BIN2 NOT NULL,
    reason_code NVARCHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL,
    evidence_ref NVARCHAR(128) COLLATE Latin1_General_100_BIN2 NOT NULL,
    evidence_fingerprint CHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL,
    revoked_at_utc DATETIME2(3) NOT NULL
        CONSTRAINT DF_ref_reference_revocation_at DEFAULT (SYSUTCDATETIME()),
    CONSTRAINT PK_ref_reference_release_revocation PRIMARY KEY CLUSTERED (
        reference_release_id, activation_scope
    ),
    CONSTRAINT FK_ref_reference_revocation_ratification FOREIGN KEY (
        reference_release_id, activation_scope
    ) REFERENCES ref.reference_release_ratification (reference_release_id, activation_scope),
    CONSTRAINT CK_ref_reference_revocation_governance CHECK (
        activation_scope IN (N'SHADOW', N'PRODUCTION')
        AND revoker_role COLLATE Latin1_General_100_BIN2 LIKE N'[A-Z]%'
        AND revoker_role COLLATE Latin1_General_100_BIN2 NOT LIKE N'%[^A-Z0-9_]%'
        AND LEN(revoker_role) BETWEEN 3 AND 128
        AND reason_code COLLATE Latin1_General_100_BIN2 LIKE N'[A-Z]%'
        AND reason_code COLLATE Latin1_General_100_BIN2 NOT LIKE N'%[^A-Z0-9_]%'
        AND LEN(reason_code) BETWEEN 3 AND 64
        AND evidence_ref COLLATE Latin1_General_100_BIN2 LIKE N'[A-Z]%'
        AND evidence_ref COLLATE Latin1_General_100_BIN2 NOT LIKE N'%[^-A-Z0-9_]%'
        AND LEN(evidence_ref) BETWEEN 3 AND 128
        AND LEN(evidence_fingerprint) = 64
        AND evidence_fingerprint COLLATE Latin1_General_100_BIN2
            NOT LIKE '%[^0-9a-f]%'
    )
);
GO

CREATE TABLE ref.calendario (
    reference_release_id BIGINT NOT NULL,
    family_code NVARCHAR(32) COLLATE Latin1_General_100_BIN2 NOT NULL,
    calendar_date DATE NOT NULL,
    date_key INT NOT NULL,
    iso_weekday TINYINT NOT NULL,
    is_weekend BIT NOT NULL,
    holiday_kind NVARCHAR(16) COLLATE Latin1_General_100_BIN2 NOT NULL,
    holiday_code NVARCHAR(64) COLLATE Latin1_General_100_BIN2 NULL,
    holiday_label NVARCHAR(128) COLLATE Latin1_General_100_BIN2 NULL,
    is_business_day BIT NOT NULL,
    billing_reference_date DATE NOT NULL,
    CONSTRAINT PK_ref_calendario PRIMARY KEY CLUSTERED (
        reference_release_id, calendar_date
    ),
    CONSTRAINT UQ_ref_calendario_date_key UNIQUE (reference_release_id, date_key),
    CONSTRAINT FK_ref_calendario_release FOREIGN KEY (reference_release_id, family_code)
        REFERENCES ref.reference_release (reference_release_id, family_code),
    CONSTRAINT FK_ref_calendario_billing_date FOREIGN KEY (
        reference_release_id, billing_reference_date
    ) REFERENCES ref.calendario (reference_release_id, calendar_date),
    CONSTRAINT CK_ref_calendario_family CHECK (family_code = N'CALENDAR'),
    CONSTRAINT CK_ref_calendario_civil CHECK (
        date_key = YEAR(calendar_date) * 10000 + MONTH(calendar_date) * 100 + DAY(calendar_date)
        AND iso_weekday =
            ((DATEDIFF(DAY, CONVERT(DATE, '19000101', 112), calendar_date) % 7 + 7) % 7) + 1
        AND is_weekend = CASE WHEN
            ((DATEDIFF(DAY, CONVERT(DATE, '19000101', 112), calendar_date) % 7 + 7) % 7) + 1
            IN (6, 7) THEN 1 ELSE 0 END
    ),
    CONSTRAINT CK_ref_calendario_holiday CHECK (
        holiday_kind IN (N'NONE', N'NATIONAL', N'OPTIONAL', N'OPERATIONAL')
        AND (
            holiday_kind = N'NONE' AND holiday_code IS NULL AND holiday_label IS NULL
            OR holiday_kind <> N'NONE'
               AND holiday_code COLLATE Latin1_General_100_BIN2 LIKE N'[A-Z]%'
               AND holiday_code COLLATE Latin1_General_100_BIN2 NOT LIKE N'%[^A-Z0-9_]%'
               AND holiday_label IS NOT NULL AND LEN(holiday_label) BETWEEN 1 AND 128
        )
    ),
    CONSTRAINT CK_ref_calendario_business_reference CHECK (
        billing_reference_date <= calendar_date
        AND (
            is_business_day = 1 AND is_weekend = 0
                AND holiday_kind = N'NONE' AND billing_reference_date = calendar_date
            OR is_business_day = 0
               AND (is_weekend = 1 OR holiday_kind <> N'NONE')
               AND billing_reference_date < calendar_date
        )
    )
);
GO

CREATE INDEX IX_ref_calendario_business_date
    ON ref.calendario (reference_release_id, is_business_day, calendar_date)
    INCLUDE (billing_reference_date, holiday_kind, holiday_code);
GO

-- Compatibilidade explícita com ColetaStatusPolicy.normalize do legado para o domínio ASCII:
-- trim, lowercase, hífen e cada espaço viram underscore. Valor fora do domínio falha fechado.
CREATE FUNCTION ref.ufn_normalize_pick_status_v1 (@raw NVARCHAR(200))
RETURNS NVARCHAR(50)
AS
BEGIN
    IF @raw IS NULL RETURN NULL;
    DECLARE @normalized NVARCHAR(200) = LOWER(
        LTRIM(RTRIM(@raw)) COLLATE Latin1_General_100_BIN2
    ) COLLATE Latin1_General_100_BIN2;
    SET @normalized = REPLACE(REPLACE(@normalized, N'-', N'_'), N' ', N'_');
    IF LEN(@normalized) NOT BETWEEN 1 AND 50
       OR @normalized COLLATE Latin1_General_100_BIN2 NOT LIKE N'[a-z]%'
       OR @normalized COLLATE Latin1_General_100_BIN2 LIKE N'%[^a-z0-9_]%'
        RETURN NULL;
    RETURN CONVERT(NVARCHAR(50), @normalized) COLLATE Latin1_General_100_BIN2;
END;
GO

CREATE TABLE ref.status_coleta (
    reference_release_id BIGINT NOT NULL,
    family_code NVARCHAR(32) COLLATE Latin1_General_100_BIN2 NOT NULL,
    raw_status NVARCHAR(50) COLLATE Latin1_General_100_BIN2 NOT NULL,
    normalization_version NVARCHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL,
    canonical_status NVARCHAR(50) COLLATE Latin1_General_100_BIN2 NOT NULL,
    display_label NVARCHAR(100) COLLATE Latin1_General_100_BIN2 NOT NULL,
    is_terminal BIT NOT NULL,
    valid_from DATE NOT NULL,
    valid_to_exclusive DATE NOT NULL,
    reason_code NVARCHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL,
    CONSTRAINT PK_ref_status_coleta PRIMARY KEY CLUSTERED (
        reference_release_id, raw_status, valid_from
    ),
    CONSTRAINT FK_ref_status_coleta_release FOREIGN KEY (reference_release_id, family_code)
        REFERENCES ref.reference_release (reference_release_id, family_code),
    CONSTRAINT CK_ref_status_coleta_family CHECK (family_code = N'PICK_STATUS'),
    CONSTRAINT CK_ref_status_coleta_codes CHECK (
        raw_status COLLATE Latin1_General_100_BIN2 LIKE N'[a-z]%'
        AND raw_status COLLATE Latin1_General_100_BIN2 NOT LIKE N'%[^a-z0-9_]%'
        AND normalization_version = N'legacy-pick-status-v1'
        AND canonical_status COLLATE Latin1_General_100_BIN2 LIKE N'[a-z]%'
        AND canonical_status COLLATE Latin1_General_100_BIN2 NOT LIKE N'%[^a-z0-9_]%'
        AND raw_status <> N'*' AND canonical_status <> N'*'
        AND LEN(display_label) BETWEEN 1 AND 100
    ),
    CONSTRAINT CK_ref_status_coleta_validity CHECK (valid_from < valid_to_exclusive),
    CONSTRAINT CK_ref_status_coleta_reason CHECK (
        reason_code COLLATE Latin1_General_100_BIN2 LIKE N'[A-Z]%'
        AND reason_code COLLATE Latin1_General_100_BIN2 NOT LIKE N'%[^A-Z0-9_]%'
    )
);
GO

CREATE INDEX IX_ref_status_coleta_lookup
    ON ref.status_coleta (reference_release_id, raw_status, valid_from, valid_to_exclusive)
    INCLUDE (normalization_version, canonical_status, display_label, is_terminal);
GO

CREATE TABLE ref.filial_operacional (
    reference_release_id BIGINT NOT NULL,
    family_code NVARCHAR(32) COLLATE Latin1_General_100_BIN2 NOT NULL,
    branch_code NVARCHAR(32) COLLATE Latin1_General_100_BIN2 NOT NULL,
    branch_label NVARCHAR(128) COLLATE Latin1_General_100_BIN2 NOT NULL,
    reason_code NVARCHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL,
    CONSTRAINT PK_ref_filial_operacional PRIMARY KEY CLUSTERED (
        reference_release_id, branch_code
    ),
    CONSTRAINT FK_ref_filial_operacional_release FOREIGN KEY (
        reference_release_id, family_code
    ) REFERENCES ref.reference_release (reference_release_id, family_code),
    CONSTRAINT CK_ref_filial_operacional_family CHECK (family_code = N'BRANCH_OPERATIONS'),
    CONSTRAINT CK_ref_filial_operacional_code CHECK (
        branch_code COLLATE Latin1_General_100_BIN2 LIKE N'[A-Z]%'
        AND branch_code COLLATE Latin1_General_100_BIN2 NOT LIKE N'%[^A-Z0-9_]%'
        AND LEN(branch_label) BETWEEN 1 AND 128
        AND reason_code COLLATE Latin1_General_100_BIN2 LIKE N'[A-Z]%'
        AND reason_code COLLATE Latin1_General_100_BIN2 NOT LIKE N'%[^A-Z0-9_]%'
    )
);
GO

CREATE TABLE ref.regiao_destino_alias (
    reference_release_id BIGINT NOT NULL,
    family_code NVARCHAR(32) COLLATE Latin1_General_100_BIN2 NOT NULL,
    alias_scope NVARCHAR(32) COLLATE Latin1_General_100_BIN2 NOT NULL,
    normalized_alias NVARCHAR(128) COLLATE Latin1_General_100_BIN2 NOT NULL,
    normalization_version NVARCHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL,
    branch_code NVARCHAR(32) COLLATE Latin1_General_100_BIN2 NOT NULL,
    valid_from DATE NOT NULL,
    valid_to_exclusive DATE NOT NULL,
    reason_code NVARCHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL,
    CONSTRAINT PK_ref_regiao_destino_alias PRIMARY KEY CLUSTERED (
        reference_release_id, alias_scope, normalization_version,
        normalized_alias, valid_from
    ),
    CONSTRAINT FK_ref_regiao_destino_alias_release FOREIGN KEY (
        reference_release_id, family_code
    ) REFERENCES ref.reference_release (reference_release_id, family_code),
    CONSTRAINT FK_ref_regiao_destino_alias_branch FOREIGN KEY (
        reference_release_id, branch_code
    ) REFERENCES ref.filial_operacional (reference_release_id, branch_code),
    CONSTRAINT CK_ref_regiao_destino_alias_family CHECK (
        family_code = N'BRANCH_OPERATIONS'
    ),
    CONSTRAINT CK_ref_regiao_destino_alias_text CHECK (
        alias_scope IN (N'DESTINATION', N'OPERATIONAL', N'SOURCE')
        AND LEN(normalized_alias) BETWEEN 1 AND 128
        AND normalized_alias <> N'*'
        AND DATALENGTH(normalized_alias) = DATALENGTH(LTRIM(RTRIM(normalized_alias)))
        AND normalized_alias NOT LIKE N'%[' + NCHAR(0) + N'-' + NCHAR(31) + N']%'
        AND normalization_version COLLATE Latin1_General_100_BIN2 LIKE N'[a-z0-9]%'
        AND normalization_version COLLATE Latin1_General_100_BIN2
            NOT LIKE N'%[^-a-z0-9_.]%'
        AND reason_code COLLATE Latin1_General_100_BIN2 LIKE N'[A-Z]%'
        AND reason_code COLLATE Latin1_General_100_BIN2 NOT LIKE N'%[^A-Z0-9_]%'
    ),
    CONSTRAINT CK_ref_regiao_destino_alias_validity CHECK (
        valid_from < valid_to_exclusive
    )
);
GO

CREATE INDEX IX_ref_regiao_destino_alias_lookup
    ON ref.regiao_destino_alias (
        reference_release_id, alias_scope, normalization_version,
        normalized_alias, valid_from, valid_to_exclusive
    ) INCLUDE (branch_code);
GO

CREATE TABLE ref.filial_operacional_documento (
    reference_release_id BIGINT NOT NULL,
    family_code NVARCHAR(32) COLLATE Latin1_General_100_BIN2 NOT NULL,
    document_token CHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL,
    token_scheme_version NVARCHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL,
    branch_code NVARCHAR(32) COLLATE Latin1_General_100_BIN2 NOT NULL,
    valid_from DATE NOT NULL,
    valid_to_exclusive DATE NOT NULL,
    reason_code NVARCHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL,
    CONSTRAINT PK_ref_filial_operacional_documento PRIMARY KEY CLUSTERED (
        reference_release_id, token_scheme_version, document_token, valid_from
    ),
    CONSTRAINT FK_ref_filial_operacional_documento_release FOREIGN KEY (
        reference_release_id, family_code
    ) REFERENCES ref.reference_release (reference_release_id, family_code),
    CONSTRAINT FK_ref_filial_operacional_documento_branch FOREIGN KEY (
        reference_release_id, branch_code
    ) REFERENCES ref.filial_operacional (reference_release_id, branch_code),
    CONSTRAINT CK_ref_filial_operacional_documento_family CHECK (
        family_code = N'BRANCH_OPERATIONS'
    ),
    CONSTRAINT CK_ref_filial_operacional_documento_token CHECK (
        LEN(document_token) = 64
        AND document_token COLLATE Latin1_General_100_BIN2 NOT LIKE '%[^0-9a-f]%'
        AND token_scheme_version COLLATE Latin1_General_100_BIN2 LIKE N'[a-z0-9]%'
        AND token_scheme_version COLLATE Latin1_General_100_BIN2
            NOT LIKE N'%[^-a-z0-9_.]%'
    ),
    CONSTRAINT CK_ref_filial_operacional_documento_validity CHECK (
        valid_from < valid_to_exclusive
    ),
    CONSTRAINT CK_ref_filial_operacional_documento_reason CHECK (
        reason_code COLLATE Latin1_General_100_BIN2 LIKE N'[A-Z]%'
        AND reason_code COLLATE Latin1_General_100_BIN2 NOT LIKE N'%[^A-Z0-9_]%'
    )
);
GO

CREATE INDEX IX_ref_filial_operacional_documento_lookup
    ON ref.filial_operacional_documento (
        reference_release_id, token_scheme_version, document_token,
        valid_from, valid_to_exclusive
    ) INCLUDE (branch_code);
GO

CREATE TABLE ref.frota_propria_documento (
    reference_release_id BIGINT NOT NULL,
    family_code NVARCHAR(32) COLLATE Latin1_General_100_BIN2 NOT NULL,
    classification_scope NVARCHAR(32) COLLATE Latin1_General_100_BIN2 NOT NULL,
    document_token CHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL,
    token_scheme_version NVARCHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL,
    valid_from DATE NOT NULL,
    valid_to_exclusive DATE NOT NULL,
    reason_code NVARCHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL,
    CONSTRAINT PK_ref_frota_propria_documento PRIMARY KEY CLUSTERED (
        reference_release_id, classification_scope, token_scheme_version,
        document_token, valid_from
    ),
    CONSTRAINT FK_ref_frota_propria_documento_release FOREIGN KEY (
        reference_release_id, family_code
    ) REFERENCES ref.reference_release (reference_release_id, family_code),
    CONSTRAINT CK_ref_frota_propria_documento_family CHECK (family_code = N'OWNED_FLEET'),
    CONSTRAINT CK_ref_frota_propria_documento_token CHECK (
        classification_scope = N'DRIVER_OWNERSHIP'
        AND LEN(document_token) = 64
        AND document_token COLLATE Latin1_General_100_BIN2 NOT LIKE '%[^0-9a-f]%'
        AND token_scheme_version COLLATE Latin1_General_100_BIN2 LIKE N'[a-z0-9]%'
        AND token_scheme_version COLLATE Latin1_General_100_BIN2
            NOT LIKE N'%[^-a-z0-9_.]%'
    ),
    CONSTRAINT CK_ref_frota_propria_documento_validity CHECK (
        valid_from < valid_to_exclusive
    ),
    CONSTRAINT CK_ref_frota_propria_documento_reason CHECK (
        reason_code COLLATE Latin1_General_100_BIN2 LIKE N'[A-Z]%'
        AND reason_code COLLATE Latin1_General_100_BIN2 NOT LIKE N'%[^A-Z0-9_]%'
    )
);
GO

CREATE INDEX IX_ref_frota_propria_documento_lookup
    ON ref.frota_propria_documento (
        reference_release_id, classification_scope, token_scheme_version, document_token,
        valid_from, valid_to_exclusive
    );
GO

CREATE TABLE ref.classificacao_frota_alias (
    reference_release_id BIGINT NOT NULL,
    family_code NVARCHAR(32) COLLATE Latin1_General_100_BIN2 NOT NULL,
    classification_scope NVARCHAR(32) COLLATE Latin1_General_100_BIN2 NOT NULL,
    alias_scope NVARCHAR(32) COLLATE Latin1_General_100_BIN2 NOT NULL,
    normalized_alias NVARCHAR(128) COLLATE Latin1_General_100_BIN2 NOT NULL,
    normalization_version NVARCHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL,
    contract_class NVARCHAR(32) COLLATE Latin1_General_100_BIN2 NOT NULL,
    valid_from DATE NOT NULL,
    valid_to_exclusive DATE NOT NULL,
    reason_code NVARCHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL,
    CONSTRAINT PK_ref_classificacao_frota_alias PRIMARY KEY CLUSTERED (
        reference_release_id, classification_scope, alias_scope, normalization_version,
        normalized_alias, valid_from
    ),
    CONSTRAINT FK_ref_classificacao_frota_alias_release FOREIGN KEY (
        reference_release_id, family_code
    ) REFERENCES ref.reference_release (reference_release_id, family_code),
    CONSTRAINT CK_ref_classificacao_frota_alias_family CHECK (family_code = N'OWNED_FLEET'),
    CONSTRAINT CK_ref_classificacao_frota_alias_contract CHECK (
        (
            classification_scope = N'DRIVER_OWNERSHIP'
            AND alias_scope = N'DRIVER_OWNERSHIP_CONTRACT'
            AND contract_class IN (N'AGGREGATE', N'THIRD_PARTY')
            OR classification_scope = N'VEHICLE_DRIVER_CONTRACT'
               AND alias_scope = N'VEHICLE_CONTRACT'
               AND contract_class IN (N'AGGREGATE', N'DRIVER')
            OR classification_scope = N'VEHICLE_DRIVER_CONTRACT'
               AND alias_scope = N'DRIVER_CONTRACT'
               AND contract_class IN (N'COMPANY', N'AGGREGATE', N'THIRD_PARTY', N'UNSPECIFIED')
        )
        AND LEN(normalized_alias) BETWEEN 1 AND 128 AND normalized_alias <> N'*'
        AND DATALENGTH(normalized_alias) = DATALENGTH(LTRIM(RTRIM(normalized_alias)))
        AND normalized_alias NOT LIKE N'%[' + NCHAR(0) + N'-' + NCHAR(31) + N']%'
        AND normalization_version COLLATE Latin1_General_100_BIN2 LIKE N'[a-z0-9]%'
        AND normalization_version COLLATE Latin1_General_100_BIN2 NOT LIKE N'%[^-a-z0-9_.]%'
        AND reason_code COLLATE Latin1_General_100_BIN2 LIKE N'[A-Z]%'
        AND reason_code COLLATE Latin1_General_100_BIN2 NOT LIKE N'%[^A-Z0-9_]%'
        AND valid_from < valid_to_exclusive
    )
);
GO

CREATE INDEX IX_ref_classificacao_frota_alias_lookup
    ON ref.classificacao_frota_alias (
        reference_release_id, classification_scope, alias_scope, normalization_version,
        normalized_alias, valid_from, valid_to_exclusive
    ) INCLUDE (contract_class);
GO

CREATE TABLE ref.classificacao_frota_matriz (
    reference_release_id BIGINT NOT NULL,
    family_code NVARCHAR(32) COLLATE Latin1_General_100_BIN2 NOT NULL,
    classification_scope NVARCHAR(32) COLLATE Latin1_General_100_BIN2 NOT NULL,
    vehicle_contract_class NVARCHAR(32) COLLATE Latin1_General_100_BIN2 NOT NULL,
    driver_contract_class NVARCHAR(32) COLLATE Latin1_General_100_BIN2 NOT NULL,
    classification_code NVARCHAR(32) COLLATE Latin1_General_100_BIN2 NOT NULL,
    valid_from DATE NOT NULL,
    valid_to_exclusive DATE NOT NULL,
    reason_code NVARCHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL,
    CONSTRAINT PK_ref_classificacao_frota_matriz PRIMARY KEY CLUSTERED (
        reference_release_id, classification_scope,
        vehicle_contract_class, driver_contract_class, valid_from
    ),
    CONSTRAINT FK_ref_classificacao_frota_matriz_release FOREIGN KEY (
        reference_release_id, family_code
    ) REFERENCES ref.reference_release (reference_release_id, family_code),
    CONSTRAINT CK_ref_classificacao_frota_matriz_family CHECK (family_code = N'OWNED_FLEET'),
    CONSTRAINT CK_ref_classificacao_frota_matriz_contract CHECK (
        classification_scope = N'VEHICLE_DRIVER_CONTRACT'
        AND vehicle_contract_class IN (N'AGGREGATE', N'DRIVER')
        AND driver_contract_class IN (N'COMPANY', N'AGGREGATE', N'THIRD_PARTY', N'UNSPECIFIED')
        AND classification_code IN (
            N'FLEET', N'FLEET_PLUS_PX', N'AGGREGATE', N'THIRD_PARTY'
        )
        AND reason_code COLLATE Latin1_General_100_BIN2 LIKE N'[A-Z]%'
        AND reason_code COLLATE Latin1_General_100_BIN2 NOT LIKE N'%[^A-Z0-9_]%'
        AND valid_from < valid_to_exclusive
    )
);
GO

CREATE INDEX IX_ref_classificacao_frota_matriz_lookup
    ON ref.classificacao_frota_matriz (
        reference_release_id, classification_scope,
        vehicle_contract_class, driver_contract_class,
        valid_from, valid_to_exclusive
    ) INCLUDE (classification_code);
GO

CREATE TABLE ref.classificacao_frota_excecao_token (
    reference_release_id BIGINT NOT NULL,
    family_code NVARCHAR(32) COLLATE Latin1_General_100_BIN2 NOT NULL,
    classification_scope NVARCHAR(32) COLLATE Latin1_General_100_BIN2 NOT NULL,
    exception_scope NVARCHAR(32) COLLATE Latin1_General_100_BIN2 NOT NULL,
    subject_token CHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL,
    token_scheme_version NVARCHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL,
    required_vehicle_contract_class NVARCHAR(32) COLLATE Latin1_General_100_BIN2 NULL,
    classification_code NVARCHAR(32) COLLATE Latin1_General_100_BIN2 NOT NULL,
    priority SMALLINT NOT NULL,
    valid_from DATE NOT NULL,
    valid_to_exclusive DATE NOT NULL,
    reason_code NVARCHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL,
    CONSTRAINT PK_ref_classificacao_frota_excecao_token PRIMARY KEY CLUSTERED (
        reference_release_id, classification_scope, exception_scope,
        token_scheme_version, subject_token, valid_from
    ),
    CONSTRAINT FK_ref_classificacao_frota_excecao_token_release FOREIGN KEY (
        reference_release_id, family_code
    ) REFERENCES ref.reference_release (reference_release_id, family_code),
    CONSTRAINT CK_ref_classificacao_frota_excecao_token_family CHECK (
        family_code = N'OWNED_FLEET'
    ),
    CONSTRAINT CK_ref_classificacao_frota_excecao_token_contract CHECK (
        exception_scope = N'OWNER_NAME_TOKEN'
        AND (
            classification_scope = N'DRIVER_OWNERSHIP'
            AND required_vehicle_contract_class IS NULL
            AND classification_code = N'FLEET'
            OR classification_scope = N'VEHICLE_DRIVER_CONTRACT'
               AND required_vehicle_contract_class = N'AGGREGATE'
               AND classification_code = N'FLEET_PLUS_PX'
        )
        AND LEN(subject_token) = 64
        AND subject_token COLLATE Latin1_General_100_BIN2 NOT LIKE '%[^0-9a-f]%'
        AND token_scheme_version COLLATE Latin1_General_100_BIN2 LIKE N'[a-z0-9]%'
        AND token_scheme_version COLLATE Latin1_General_100_BIN2 NOT LIKE N'%[^-a-z0-9_.]%'
        AND priority BETWEEN 1 AND 1000
        AND reason_code COLLATE Latin1_General_100_BIN2 LIKE N'[A-Z]%'
        AND reason_code COLLATE Latin1_General_100_BIN2 NOT LIKE N'%[^A-Z0-9_]%'
        AND valid_from < valid_to_exclusive
    )
);
GO

CREATE INDEX IX_ref_classificacao_frota_excecao_lookup
    ON ref.classificacao_frota_excecao_token (
        reference_release_id, classification_scope, exception_scope,
        token_scheme_version, subject_token,
        valid_from, valid_to_exclusive, priority
    ) INCLUDE (
        required_vehicle_contract_class, classification_code
    );
GO

CREATE TABLE ref.atribuicao_filial (
    reference_release_id BIGINT NOT NULL,
    family_code NVARCHAR(32) COLLATE Latin1_General_100_BIN2 NOT NULL,
    payer_document_token CHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL,
    token_scheme_version NVARCHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL,
    branch_reference_release_id BIGINT NOT NULL,
    branch_code NVARCHAR(32) COLLATE Latin1_General_100_BIN2 NOT NULL,
    valid_from DATE NOT NULL,
    valid_to_exclusive DATE NOT NULL,
    reason_code NVARCHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL,
    CONSTRAINT PK_ref_atribuicao_filial PRIMARY KEY CLUSTERED (
        reference_release_id, token_scheme_version, payer_document_token, valid_from
    ),
    CONSTRAINT FK_ref_atribuicao_filial_release FOREIGN KEY (
        reference_release_id, family_code
    ) REFERENCES ref.reference_release (reference_release_id, family_code),
    CONSTRAINT FK_ref_atribuicao_filial_branch FOREIGN KEY (
        branch_reference_release_id, branch_code
    ) REFERENCES ref.filial_operacional (reference_release_id, branch_code),
    CONSTRAINT CK_ref_atribuicao_filial_family CHECK (
        family_code = N'BRANCH_ATTRIBUTION'
    ),
    CONSTRAINT CK_ref_atribuicao_filial_token CHECK (
        LEN(payer_document_token) = 64
        AND payer_document_token COLLATE Latin1_General_100_BIN2 NOT LIKE '%[^0-9a-f]%'
        AND token_scheme_version COLLATE Latin1_General_100_BIN2 LIKE N'[a-z0-9]%'
        AND token_scheme_version COLLATE Latin1_General_100_BIN2
            NOT LIKE N'%[^-a-z0-9_.]%'
    ),
    CONSTRAINT CK_ref_atribuicao_filial_text CHECK (
        branch_code COLLATE Latin1_General_100_BIN2 LIKE N'[A-Z]%'
        AND branch_code COLLATE Latin1_General_100_BIN2 NOT LIKE N'%[^A-Z0-9_]%'
        AND reason_code COLLATE Latin1_General_100_BIN2 LIKE N'[A-Z]%'
        AND reason_code COLLATE Latin1_General_100_BIN2 NOT LIKE N'%[^A-Z0-9_]%'
    ),
    CONSTRAINT CK_ref_atribuicao_filial_validity CHECK (
        valid_from < valid_to_exclusive
    )
);
GO

CREATE INDEX IX_ref_atribuicao_filial_lookup
    ON ref.atribuicao_filial (
        reference_release_id, token_scheme_version, payer_document_token,
        valid_from, valid_to_exclusive
    ) INCLUDE (branch_reference_release_id, branch_code);
GO

CREATE INDEX IX_ref_atribuicao_filial_branch_dependency
    ON ref.atribuicao_filial (
        branch_reference_release_id, reference_release_id
    ) INCLUDE (branch_code, valid_from, valid_to_exclusive);
GO

CREATE TABLE ref.pagador_exclusao_cubagem (
    reference_release_id BIGINT NOT NULL,
    family_code NVARCHAR(32) COLLATE Latin1_General_100_BIN2 NOT NULL,
    payer_document_token CHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL,
    token_scheme_version NVARCHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL,
    valid_from DATE NOT NULL,
    valid_to_exclusive DATE NOT NULL,
    reason_code NVARCHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL,
    CONSTRAINT PK_ref_pagador_exclusao_cubagem PRIMARY KEY CLUSTERED (
        reference_release_id, token_scheme_version, payer_document_token, valid_from
    ),
    CONSTRAINT FK_ref_pagador_exclusao_cubagem_release FOREIGN KEY (
        reference_release_id, family_code
    ) REFERENCES ref.reference_release (reference_release_id, family_code),
    CONSTRAINT CK_ref_pagador_exclusao_cubagem_family CHECK (
        family_code = N'CUBAGE_EXCLUSION'
    ),
    CONSTRAINT CK_ref_pagador_exclusao_cubagem_token CHECK (
        LEN(payer_document_token) = 64
        AND payer_document_token COLLATE Latin1_General_100_BIN2 NOT LIKE '%[^0-9a-f]%'
        AND token_scheme_version COLLATE Latin1_General_100_BIN2 LIKE N'[a-z0-9]%'
        AND token_scheme_version COLLATE Latin1_General_100_BIN2
            NOT LIKE N'%[^-a-z0-9_.]%'
    ),
    CONSTRAINT CK_ref_pagador_exclusao_cubagem_validity CHECK (
        valid_from < valid_to_exclusive
    ),
    CONSTRAINT CK_ref_pagador_exclusao_cubagem_reason CHECK (
        reason_code COLLATE Latin1_General_100_BIN2 LIKE N'[A-Z]%'
        AND reason_code COLLATE Latin1_General_100_BIN2 NOT LIKE N'%[^A-Z0-9_]%'
    )
);
GO

CREATE INDEX IX_ref_pagador_exclusao_cubagem_lookup
    ON ref.pagador_exclusao_cubagem (
        reference_release_id, token_scheme_version, payer_document_token,
        valid_from, valid_to_exclusive
    );
GO

CREATE TABLE ref.regiao_logistica_cep (
    reference_release_id BIGINT NOT NULL,
    family_code NVARCHAR(32) COLLATE Latin1_General_100_BIN2 NOT NULL,
    cep_start CHAR(8) COLLATE Latin1_General_100_BIN2 NOT NULL,
    cep_end CHAR(8) COLLATE Latin1_General_100_BIN2 NOT NULL,
    region_code NVARCHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL,
    priority SMALLINT NOT NULL,
    valid_from DATE NOT NULL,
    valid_to_exclusive DATE NOT NULL,
    reason_code NVARCHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL,
    CONSTRAINT PK_ref_regiao_logistica_cep PRIMARY KEY CLUSTERED (
        reference_release_id, cep_start, cep_end, valid_from
    ),
    CONSTRAINT FK_ref_regiao_logistica_cep_release FOREIGN KEY (
        reference_release_id, family_code
    ) REFERENCES ref.reference_release (reference_release_id, family_code),
    CONSTRAINT CK_ref_regiao_logistica_cep_family CHECK (
        family_code = N'LOGISTICS_REGION'
    ),
    CONSTRAINT CK_ref_regiao_logistica_cep_range CHECK (
        LEN(cep_start) = 8 AND cep_start NOT LIKE '%[^0-9]%'
        AND LEN(cep_end) = 8 AND cep_end NOT LIKE '%[^0-9]%'
        AND cep_start <= cep_end
        AND priority BETWEEN 1 AND 1000
    ),
    CONSTRAINT CK_ref_regiao_logistica_cep_text CHECK (
        region_code COLLATE Latin1_General_100_BIN2 LIKE N'[A-Z]%'
        AND region_code COLLATE Latin1_General_100_BIN2 NOT LIKE N'%[^A-Z0-9_]%'
        AND reason_code COLLATE Latin1_General_100_BIN2 LIKE N'[A-Z]%'
        AND reason_code COLLATE Latin1_General_100_BIN2 NOT LIKE N'%[^A-Z0-9_]%'
    ),
    CONSTRAINT CK_ref_regiao_logistica_cep_validity CHECK (
        valid_from < valid_to_exclusive
    )
);
GO

CREATE INDEX IX_ref_regiao_logistica_cep_lookup
    ON ref.regiao_logistica_cep (
        reference_release_id, cep_start, cep_end, valid_from, valid_to_exclusive, priority
    ) INCLUDE (region_code);
GO

CREATE TABLE ref.regiao_logistica_cidade_uf (
    reference_release_id BIGINT NOT NULL,
    family_code NVARCHAR(32) COLLATE Latin1_General_100_BIN2 NOT NULL,
    normalized_city NVARCHAR(128) COLLATE Latin1_General_100_BIN2 NOT NULL,
    city_normalization_version NVARCHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL,
    uf CHAR(2) COLLATE Latin1_General_100_BIN2 NOT NULL,
    region_code NVARCHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL,
    priority SMALLINT NOT NULL,
    valid_from DATE NOT NULL,
    valid_to_exclusive DATE NOT NULL,
    reason_code NVARCHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL,
    CONSTRAINT PK_ref_regiao_logistica_cidade_uf PRIMARY KEY CLUSTERED (
        reference_release_id, uf, city_normalization_version, normalized_city, valid_from
    ),
    CONSTRAINT FK_ref_regiao_logistica_cidade_uf_release FOREIGN KEY (
        reference_release_id, family_code
    ) REFERENCES ref.reference_release (reference_release_id, family_code),
    CONSTRAINT CK_ref_regiao_logistica_cidade_uf_family CHECK (
        family_code = N'LOGISTICS_REGION'
    ),
    CONSTRAINT CK_ref_regiao_logistica_cidade_uf_key CHECK (
        LEN(normalized_city) BETWEEN 1 AND 128 AND normalized_city <> N'*'
        AND DATALENGTH(normalized_city) = DATALENGTH(LTRIM(RTRIM(normalized_city)))
        AND normalized_city NOT LIKE N'%[' + NCHAR(0) + N'-' + NCHAR(31) + N']%'
        AND city_normalization_version COLLATE Latin1_General_100_BIN2 LIKE N'[a-z0-9]%'
        AND city_normalization_version COLLATE Latin1_General_100_BIN2
            NOT LIKE N'%[^-a-z0-9_.]%'
        AND uf IN (
            'AC', 'AL', 'AP', 'AM', 'BA', 'CE', 'DF', 'ES', 'GO', 'MA', 'MT', 'MS',
            'MG', 'PA', 'PB', 'PR', 'PE', 'PI', 'RJ', 'RN', 'RS', 'RO', 'RR', 'SC',
            'SP', 'SE', 'TO'
        )
        AND priority BETWEEN 1 AND 1000
    ),
    CONSTRAINT CK_ref_regiao_logistica_cidade_uf_text CHECK (
        region_code COLLATE Latin1_General_100_BIN2 LIKE N'[A-Z]%'
        AND region_code COLLATE Latin1_General_100_BIN2 NOT LIKE N'%[^A-Z0-9_]%'
        AND reason_code COLLATE Latin1_General_100_BIN2 LIKE N'[A-Z]%'
        AND reason_code COLLATE Latin1_General_100_BIN2 NOT LIKE N'%[^A-Z0-9_]%'
    ),
    CONSTRAINT CK_ref_regiao_logistica_cidade_uf_validity CHECK (
        valid_from < valid_to_exclusive
    )
);
GO

CREATE INDEX IX_ref_regiao_logistica_cidade_uf_lookup
    ON ref.regiao_logistica_cidade_uf (
        reference_release_id, uf, city_normalization_version,
        normalized_city, valid_from, valid_to_exclusive, priority
    ) INCLUDE (region_code);
GO

CREATE TABLE ref.tarifa_rota_uf (
    reference_release_id BIGINT NOT NULL,
    family_code NVARCHAR(32) COLLATE Latin1_General_100_BIN2 NOT NULL,
    origin_uf CHAR(2) COLLATE Latin1_General_100_BIN2 NOT NULL,
    destination_uf CHAR(2) COLLATE Latin1_General_100_BIN2 NOT NULL,
    coverage_state NVARCHAR(16) COLLATE Latin1_General_100_BIN2 NOT NULL,
    minimum_amount DECIMAL(19, 4) NULL,
    currency_code CHAR(3) COLLATE Latin1_General_100_BIN2 NOT NULL,
    unit_code NVARCHAR(32) COLLATE Latin1_General_100_BIN2 NOT NULL,
    rounding_mode NVARCHAR(24) COLLATE Latin1_General_100_BIN2 NOT NULL,
    valid_from DATE NOT NULL,
    valid_to_exclusive DATE NOT NULL,
    reason_code NVARCHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL,
    CONSTRAINT PK_ref_tarifa_rota_uf PRIMARY KEY CLUSTERED (
        reference_release_id, origin_uf, destination_uf, valid_from
    ),
    CONSTRAINT FK_ref_tarifa_rota_uf_release FOREIGN KEY (
        reference_release_id, family_code
    ) REFERENCES ref.reference_release (reference_release_id, family_code),
    CONSTRAINT CK_ref_tarifa_rota_uf_family CHECK (family_code = N'QUOTE_TARIFF'),
    CONSTRAINT CK_ref_tarifa_rota_uf_route CHECK (
        origin_uf IN (
            'AC', 'AL', 'AP', 'AM', 'BA', 'CE', 'DF', 'ES', 'GO', 'MA', 'MT', 'MS',
            'MG', 'PA', 'PB', 'PR', 'PE', 'PI', 'RJ', 'RN', 'RS', 'RO', 'RR', 'SC',
            'SP', 'SE', 'TO'
        )
        AND destination_uf IN (
            'AC', 'AL', 'AP', 'AM', 'BA', 'CE', 'DF', 'ES', 'GO', 'MA', 'MT', 'MS',
            'MG', 'PA', 'PB', 'PR', 'PE', 'PI', 'RJ', 'RN', 'RS', 'RO', 'RR', 'SC',
            'SP', 'SE', 'TO'
        )
    ),
    CONSTRAINT CK_ref_tarifa_rota_uf_value CHECK (
        coverage_state IN (N'PRICED', N'UNAVAILABLE')
        AND (
            coverage_state = N'PRICED' AND minimum_amount > CONVERT(DECIMAL(19, 4), 0)
            OR coverage_state = N'UNAVAILABLE' AND minimum_amount IS NULL
        )
        AND currency_code COLLATE Latin1_General_100_BIN2 LIKE '[A-Z][A-Z][A-Z]'
        AND unit_code IN (N'PER_SHIPMENT', N'PER_WEIGHT', N'PER_VOLUME')
        AND rounding_mode IN (N'HALF_UP', N'HALF_EVEN', N'DOWN', N'UP')
    ),
    CONSTRAINT CK_ref_tarifa_rota_uf_validity CHECK (valid_from < valid_to_exclusive),
    CONSTRAINT CK_ref_tarifa_rota_uf_reason CHECK (
        reason_code COLLATE Latin1_General_100_BIN2 LIKE N'[A-Z]%'
        AND reason_code COLLATE Latin1_General_100_BIN2 NOT LIKE N'%[^A-Z0-9_]%'
    )
);
GO

CREATE INDEX IX_ref_tarifa_rota_uf_lookup
    ON ref.tarifa_rota_uf (
        reference_release_id, origin_uf, destination_uf, valid_from, valid_to_exclusive
    ) INCLUDE (coverage_state, minimum_amount, currency_code, unit_code, rounding_mode);
GO

-- Seed candidato determinístico: não é persistido, ratificado nem selecionável como produção.
CREATE VIEW ref.v_status_coleta_seed_candidate_v1
AS
SELECT
    seed_ordinal,
    raw_status,
    CONVERT(NVARCHAR(64), N'legacy-pick-status-v1') COLLATE Latin1_General_100_BIN2
        AS normalization_version,
    canonical_status,
    display_label,
    is_terminal,
    CONVERT(NVARCHAR(64), N'pick-status-candidate-v1') COLLATE Latin1_General_100_BIN2
        AS seed_version
FROM (VALUES
    (CONVERT(TINYINT, 1), N'pending', N'pending', N'Pendente', CONVERT(BIT, 0)),
    (CONVERT(TINYINT, 2), N'treatment', N'treatment', N'Em tratativa', CONVERT(BIT, 0)),
    (CONVERT(TINYINT, 3), N'manifested', N'manifested', N'Manifestada', CONVERT(BIT, 0)),
    (CONVERT(TINYINT, 4), N'in_transit', N'in_transit', N'Em trânsito', CONVERT(BIT, 0)),
    (CONVERT(TINYINT, 5), N'draft', N'draft', N'Rascunho', CONVERT(BIT, 0)),
    (CONVERT(TINYINT, 6), N'finished', N'finished', N'Finalizada', CONVERT(BIT, 1)),
    (CONVERT(TINYINT, 7), N'done', N'done', N'Coletada', CONVERT(BIT, 1)),
    (CONVERT(TINYINT, 8), N'canceled', N'canceled', N'Cancelada', CONVERT(BIT, 1)),
    (CONVERT(TINYINT, 9), N'cancelled', N'canceled', N'Cancelada', CONVERT(BIT, 1))
) AS seed (seed_ordinal, raw_status, canonical_status, display_label, is_terminal);
GO

-- Calendário civil rolante, independente de LANGUAGE/DATEFIRST/clock e limitado a 3.660 dias
-- ativos. Quando valid_from não é útil, a função inclui contexto contínuo desde o último dia útil
-- dos 31 dias anteriores; a ratificação separa esse lookback limitado da vigência ativa.
CREATE FUNCTION ref.ufn_calendar_seed_candidate_v1 (
    @window_start DATE,
    @window_end_exclusive DATE
)
RETURNS @calendar TABLE (
    calendar_date DATE NOT NULL PRIMARY KEY,
    date_key INT NOT NULL,
    iso_weekday TINYINT NOT NULL,
    is_weekend BIT NOT NULL,
    holiday_kind NVARCHAR(16) COLLATE Latin1_General_100_BIN2 NOT NULL,
    holiday_code NVARCHAR(64) COLLATE Latin1_General_100_BIN2 NULL,
    holiday_label NVARCHAR(128) COLLATE Latin1_General_100_BIN2 NULL,
    is_business_day BIT NOT NULL,
    billing_reference_date DATE NULL
)
AS
BEGIN
    DECLARE @active_span_days INT = DATEDIFF(DAY, @window_start, @window_end_exclusive);
    IF @window_start IS NULL OR @window_end_exclusive IS NULL
       OR @window_start >= @window_end_exclusive
       OR @active_span_days > 3660
       OR @window_start < CONVERT(DATE, '00010201', 112)
        RETURN;

    DECLARE @generation_start DATE = DATEADD(DAY, -31, @window_start);
    DECLARE @generated_span_days INT = DATEDIFF(DAY, @generation_start, @window_end_exclusive);

    ;WITH digit (n) AS (
        SELECT n FROM (VALUES (0),(1),(2),(3),(4),(5),(6),(7),(8),(9)) AS digits (n)
    ),
    numbers AS (
        SELECT ones.n + tens.n * 10 + hundreds.n * 100 + thousands.n * 1000 AS day_offset
        FROM digit AS ones
        CROSS JOIN digit AS tens
        CROSS JOIN digit AS hundreds
        CROSS JOIN digit AS thousands
    ),
    civil AS (
        SELECT DATEADD(DAY, day_offset, @generation_start) AS calendar_date
        FROM numbers
        WHERE day_offset < @generated_span_days
    ),
    classified AS (
        SELECT calendar_date,
               CONVERT(TINYINT,
                   ((DATEDIFF(DAY, CONVERT(DATE, '19000101', 112), calendar_date) % 7 + 7) % 7) + 1
               ) AS iso_weekday
        FROM civil
    )
    INSERT INTO @calendar (
        calendar_date, date_key, iso_weekday, is_weekend, holiday_kind,
        holiday_code, holiday_label, is_business_day, billing_reference_date
    )
    SELECT calendar_date,
           YEAR(calendar_date) * 10000 + MONTH(calendar_date) * 100 + DAY(calendar_date),
           iso_weekday,
           CONVERT(BIT, CASE WHEN iso_weekday IN (6, 7) THEN 1 ELSE 0 END),
           N'NONE', NULL, NULL,
           CONVERT(BIT, CASE WHEN iso_weekday IN (6, 7) THEN 0 ELSE 1 END),
           NULL
    FROM classified;

    UPDATE calendar_row
    SET holiday_kind = N'NATIONAL',
        holiday_code = CASE
            WHEN MONTH(calendar_date) = 1 AND DAY(calendar_date) = 1 THEN N'NEW_YEAR'
            WHEN MONTH(calendar_date) = 4 AND DAY(calendar_date) = 21 THEN N'TIRADENTES'
            WHEN MONTH(calendar_date) = 5 AND DAY(calendar_date) = 1 THEN N'LABOUR_DAY'
            WHEN MONTH(calendar_date) = 9 AND DAY(calendar_date) = 7 THEN N'INDEPENDENCE_DAY'
            WHEN MONTH(calendar_date) = 10 AND DAY(calendar_date) = 12 THEN N'APARECIDA_DAY'
            WHEN MONTH(calendar_date) = 11 AND DAY(calendar_date) = 2 THEN N'ALL_SOULS_DAY'
            WHEN MONTH(calendar_date) = 11 AND DAY(calendar_date) = 15 THEN N'REPUBLIC_DAY'
            WHEN MONTH(calendar_date) = 11 AND DAY(calendar_date) = 20 THEN N'BLACK_CONSCIOUSNESS_DAY'
            WHEN MONTH(calendar_date) = 12 AND DAY(calendar_date) = 25 THEN N'CHRISTMAS'
        END,
        holiday_label = CASE
            WHEN MONTH(calendar_date) = 1 AND DAY(calendar_date) = 1
                THEN N'Confraternização Universal'
            WHEN MONTH(calendar_date) = 4 AND DAY(calendar_date) = 21 THEN N'Tiradentes'
            WHEN MONTH(calendar_date) = 5 AND DAY(calendar_date) = 1 THEN N'Dia do Trabalho'
            WHEN MONTH(calendar_date) = 9 AND DAY(calendar_date) = 7
                THEN N'Independência do Brasil'
            WHEN MONTH(calendar_date) = 10 AND DAY(calendar_date) = 12
                THEN N'Nossa Senhora Aparecida'
            WHEN MONTH(calendar_date) = 11 AND DAY(calendar_date) = 2 THEN N'Finados'
            WHEN MONTH(calendar_date) = 11 AND DAY(calendar_date) = 15
                THEN N'Proclamação da República'
            WHEN MONTH(calendar_date) = 11 AND DAY(calendar_date) = 20
                THEN N'Consciência Negra'
            WHEN MONTH(calendar_date) = 12 AND DAY(calendar_date) = 25 THEN N'Natal'
        END
    FROM @calendar AS calendar_row
    WHERE MONTH(calendar_date) * 100 + DAY(calendar_date) IN (
        101, 421, 501, 907, 1012, 1102, 1115, 1225
    )
       OR YEAR(calendar_date) >= 2024
          AND MONTH(calendar_date) = 11 AND DAY(calendar_date) = 20;

    DECLARE @easter TABLE (
        calendar_year SMALLINT NOT NULL PRIMARY KEY,
        easter_sunday DATE NOT NULL
    );
    ;WITH years AS (
        SELECT DISTINCT CONVERT(SMALLINT, YEAR(calendar_date)) AS calendar_year
        FROM @calendar
    ),
    a AS (
        SELECT calendar_year, calendar_year % 19 AS a,
               calendar_year / 100 AS b, calendar_year % 100 AS c
        FROM years
    ),
    b AS (
        SELECT *, b / 4 AS d, b % 4 AS e, (b + 8) / 25 AS f,
               (b - ((b + 8) / 25) + 1) / 3 AS g,
               c / 4 AS i, c % 4 AS k
        FROM a
    ),
    c AS (
        SELECT *, (19 * a + b - d - g + 15) % 30 AS h FROM b
    ),
    d AS (
        SELECT *, (32 + 2 * e + 2 * i - h - k) % 7 AS l FROM c
    ),
    e AS (
        SELECT *, (a + 11 * h + 22 * l) / 451 AS m FROM d
    )
    INSERT INTO @easter (calendar_year, easter_sunday)
    SELECT calendar_year,
           DATEFROMPARTS(
               calendar_year,
               (h + l - 7 * m + 114) / 31,
               ((h + l - 7 * m + 114) % 31) + 1
           )
    FROM e;

    DECLARE @movable_holiday TABLE (
        calendar_date DATE NOT NULL PRIMARY KEY,
        holiday_kind NVARCHAR(16) COLLATE Latin1_General_100_BIN2 NOT NULL,
        holiday_code NVARCHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL,
        holiday_label NVARCHAR(128) COLLATE Latin1_General_100_BIN2 NOT NULL
    );
    INSERT INTO @movable_holiday (
        calendar_date, holiday_kind, holiday_code, holiday_label
    )
    SELECT DATEADD(DAY, holiday_definition.day_offset, easter.easter_sunday),
           holiday_definition.holiday_kind,
           holiday_definition.holiday_code,
           holiday_definition.holiday_label
    FROM @easter AS easter
    CROSS JOIN (VALUES
        (-2, N'NATIONAL', N'GOOD_FRIDAY', N'Sexta-feira Santa'),
        (-48, N'OPTIONAL', N'CARNIVAL_MONDAY', N'Carnaval - segunda-feira'),
        (-47, N'OPTIONAL', N'CARNIVAL_TUESDAY', N'Carnaval - terça-feira'),
        (60, N'OPTIONAL', N'CORPUS_CHRISTI', N'Corpus Christi')
    ) AS holiday_definition (day_offset, holiday_kind, holiday_code, holiday_label);

    UPDATE calendar_row
    SET holiday_kind = movable.holiday_kind,
        holiday_code = movable.holiday_code,
        holiday_label = movable.holiday_label
    FROM @calendar AS calendar_row
    INNER JOIN @movable_holiday AS movable
        ON movable.calendar_date = calendar_row.calendar_date;

    UPDATE @calendar
    SET is_business_day = CONVERT(BIT, CASE
        WHEN is_weekend = 1 OR holiday_kind <> N'NONE' THEN 0 ELSE 1
    END);

    ;WITH billing_reference AS (
        SELECT calendar_date,
               MAX(CASE WHEN is_business_day = 1 THEN calendar_date END) OVER (
                   ORDER BY calendar_date ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW
               ) AS billing_reference_date
        FROM @calendar
    )
    UPDATE target
    SET billing_reference_date = source.billing_reference_date
    FROM @calendar AS target
    INNER JOIN billing_reference AS source ON source.calendar_date = target.calendar_date;

    DECLARE @first_calendar_date DATE;
    SELECT @first_calendar_date = CASE
        WHEN EXISTS (
            SELECT 1 FROM @calendar
            WHERE calendar_date = @window_start AND is_business_day = 1
        ) THEN @window_start
        ELSE (
            SELECT MAX(calendar_date) FROM @calendar
            WHERE calendar_date < @window_start AND is_business_day = 1
        )
    END;

    IF @first_calendar_date IS NULL
    BEGIN
        DELETE FROM @calendar;
        RETURN;
    END;

    DELETE FROM @calendar WHERE calendar_date < @first_calendar_date;

    RETURN;
END;
GO

-- Releases e ledgers são append-only; recibo, ratificação e revogação são fatos novos.
CREATE TRIGGER ref.trg_reference_release_immutable
ON ref.reference_release
AFTER UPDATE, DELETE
AS
BEGIN
    SET NOCOUNT ON;
    THROW 51900, N'Release de referência é imutável; publique nova versão.', 1;
END;
GO

CREATE TRIGGER ref.trg_reference_import_receipt_immutable
ON ref.reference_import_receipt
AFTER UPDATE, DELETE
AS
BEGIN
    SET NOCOUNT ON;
    THROW 51941, N'Recibo de importação é imutável; publique nova release.', 1;
END;
GO

CREATE TRIGGER ref.trg_reference_ratification_immutable
ON ref.reference_release_ratification
AFTER UPDATE, DELETE
AS
BEGIN
    SET NOCOUNT ON;
    THROW 51901, N'Ratificação de referência é imutável; revogue-a de forma append-only.', 1;
END;
GO

CREATE TRIGGER ref.trg_reference_revocation_immutable
ON ref.reference_release_revocation
AFTER UPDATE, DELETE
AS
BEGIN
    SET NOCOUNT ON;
    THROW 51902, N'Revogação de referência é imutável.', 1;
END;
GO

-- O recibo é unitário, toma lock na release-mãe e sela fingerprint, limites e cardinalidade
-- física. O mesmo lock é tomado por toda escrita de conteúdo, eliminando content-vs-seal.
CREATE TRIGGER ref.trg_reference_import_receipt_guard
ON ref.reference_import_receipt
AFTER INSERT
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;

    IF (SELECT COUNT_BIG(*) FROM inserted) <> 1
        THROW 51942, N'Um recibo deve selar exatamente uma release por transação.', 1;

    DECLARE @release_id BIGINT;
    DECLARE @family NVARCHAR(32);
    DECLARE @expected_fingerprint CHAR(64);
    DECLARE @expected_rows BIGINT;
    DECLARE @release_recorded_at DATETIME2(3);
    SELECT @release_id = receipt.reference_release_id,
           @family = release_definition.family_code,
           @expected_fingerprint = release_definition.source_fingerprint,
           @expected_rows = release_definition.source_row_count,
           @release_recorded_at = release_definition.recorded_at_utc
    FROM inserted AS receipt
    INNER JOIN ref.reference_release AS release_definition WITH (UPDLOCK, HOLDLOCK)
        ON release_definition.reference_release_id = receipt.reference_release_id;

    DECLARE @actual_rows BIGINT = CASE @family
        WHEN N'CALENDAR' THEN
            (SELECT COUNT_BIG(*) FROM ref.calendario WHERE reference_release_id = @release_id)
        WHEN N'PICK_STATUS' THEN
            (SELECT COUNT_BIG(*) FROM ref.status_coleta WHERE reference_release_id = @release_id)
        WHEN N'BRANCH_OPERATIONS' THEN
            (SELECT COUNT_BIG(*) FROM ref.filial_operacional WHERE reference_release_id = @release_id)
            + (SELECT COUNT_BIG(*) FROM ref.regiao_destino_alias WHERE reference_release_id = @release_id)
            + (SELECT COUNT_BIG(*) FROM ref.filial_operacional_documento WHERE reference_release_id = @release_id)
        WHEN N'OWNED_FLEET' THEN
            (SELECT COUNT_BIG(*) FROM ref.frota_propria_documento WHERE reference_release_id = @release_id)
            + (SELECT COUNT_BIG(*) FROM ref.classificacao_frota_alias WHERE reference_release_id = @release_id)
            + (SELECT COUNT_BIG(*) FROM ref.classificacao_frota_matriz WHERE reference_release_id = @release_id)
            + (SELECT COUNT_BIG(*) FROM ref.classificacao_frota_excecao_token WHERE reference_release_id = @release_id)
        WHEN N'BRANCH_ATTRIBUTION' THEN
            (SELECT COUNT_BIG(*) FROM ref.atribuicao_filial WHERE reference_release_id = @release_id)
        WHEN N'CUBAGE_EXCLUSION' THEN
            (SELECT COUNT_BIG(*) FROM ref.pagador_exclusao_cubagem WHERE reference_release_id = @release_id)
        WHEN N'LOGISTICS_REGION' THEN
            (SELECT COUNT_BIG(*) FROM ref.regiao_logistica_cep WHERE reference_release_id = @release_id)
            + (SELECT COUNT_BIG(*) FROM ref.regiao_logistica_cidade_uf WHERE reference_release_id = @release_id)
        WHEN N'QUOTE_TARIFF' THEN
            (SELECT COUNT_BIG(*) FROM ref.tarifa_rota_uf WHERE reference_release_id = @release_id)
        ELSE -1
    END;

    IF EXISTS (
        SELECT 1 FROM inserted
        WHERE content_fingerprint <> @expected_fingerprint
           OR imported_row_count <> @expected_rows
           OR imported_row_count <> @actual_rows
           OR recorded_at_utc < @release_recorded_at
    )
        THROW 51943, N'Recibo não confere com envelope, conteúdo físico ou cronologia.', 1;
END;
GO

-- A ratificação requer conteúdo previamente selado, separa autor/aprovador e serializa
-- releases do mesmo family/scope/activation antes de impedir vigências concorrentes.
CREATE TRIGGER ref.trg_reference_ratification_guard
ON ref.reference_release_ratification
AFTER INSERT
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;

    IF (SELECT COUNT_BIG(*) FROM inserted) <> 1
        THROW 51903, N'Ratificação deve ocorrer uma release por transação.', 1;

    DECLARE @release_id BIGINT;
    DECLARE @family NVARCHAR(32);
    DECLARE @scope NVARCHAR(128);
    DECLARE @activation_scope NVARCHAR(16);
    DECLARE @lock_resource NVARCHAR(255);
    DECLARE @lock_result INT;

    SELECT @release_id = inserted.reference_release_id,
           @family = release_definition.family_code,
           @scope = release_definition.scope_code,
           @activation_scope = inserted.activation_scope
    FROM inserted
    INNER JOIN ref.reference_release AS release_definition WITH (UPDLOCK, HOLDLOCK)
        ON release_definition.reference_release_id = inserted.reference_release_id;

    SET @lock_resource = CONCAT(
        N'V2_REF_RATIFY_', LOWER(CONVERT(CHAR(64), HASHBYTES(
            'SHA2_256', CONVERT(VARBINARY(MAX), CONCAT(
                DATALENGTH(@family), N':', @family, N'|',
                DATALENGTH(@scope), N':', @scope, N'|',
                DATALENGTH(@activation_scope), N':', @activation_scope
            ))
        ), 2))
    );
    EXEC @lock_result = sys.sp_getapplock
        @Resource = @lock_resource,
        @LockMode = 'Exclusive',
        @LockOwner = 'Transaction',
        @LockTimeout = 5000,
        @DbPrincipal = 'public';
    IF @lock_result < 0
        THROW 51904, N'Não foi possível serializar a ratificação da referência.', 1;

    IF EXISTS (
        SELECT 1
        FROM inserted
        INNER JOIN ref.reference_release AS release_definition
            ON release_definition.reference_release_id = inserted.reference_release_id
        INNER JOIN ref.reference_import_receipt AS receipt
            ON receipt.reference_release_id = inserted.reference_release_id
        WHERE inserted.approver_role = release_definition.author_role
           OR inserted.approver_role = receipt.importer_role
    )
        THROW 51905, N'Aprovador deve ser distinto do autor e do importador.', 1;

    IF EXISTS (
        SELECT 1
        FROM inserted
        INNER JOIN ref.reference_release AS release_definition
            ON release_definition.reference_release_id = inserted.reference_release_id
        INNER JOIN ref.reference_import_receipt AS receipt
            ON receipt.reference_release_id = inserted.reference_release_id
        WHERE inserted.ratified_at_utc < release_definition.recorded_at_utc
           OR inserted.ratified_at_utc < receipt.recorded_at_utc
    )
        THROW 51947, N'Ratificação não pode anteceder release ou recibo.', 1;

    IF NOT EXISTS (
        SELECT 1 FROM ref.reference_import_receipt
        WHERE reference_release_id = @release_id
    )
        THROW 51906, N'Release sem recibo de importação íntegro não pode ser ratificada.', 1;

    IF @family = N'CALENDAR' AND (
        (SELECT COUNT_BIG(*)
         FROM ref.calendario AS calendar_row
         INNER JOIN ref.reference_release AS release_definition
             ON release_definition.reference_release_id = calendar_row.reference_release_id
         WHERE calendar_row.reference_release_id = @release_id
           AND calendar_row.calendar_date >= release_definition.valid_from
           AND calendar_row.calendar_date < release_definition.valid_to_exclusive)
            <> (SELECT DATEDIFF(DAY, valid_from, valid_to_exclusive)
                FROM ref.reference_release WHERE reference_release_id = @release_id)
        OR (SELECT DATEDIFF(DAY, valid_from, valid_to_exclusive)
            FROM ref.reference_release WHERE reference_release_id = @release_id) > 3660
        OR (SELECT MIN(calendar_date) FROM ref.calendario WHERE reference_release_id = @release_id)
            < (SELECT CASE
                    WHEN valid_from < CONVERT(DATE, '00010201', 112)
                        THEN CONVERT(DATE, '00010101', 112)
                    ELSE DATEADD(DAY, -31, valid_from)
                END
               FROM ref.reference_release WHERE reference_release_id = @release_id)
        OR (SELECT MIN(calendar_date) FROM ref.calendario WHERE reference_release_id = @release_id)
            > (SELECT valid_from FROM ref.reference_release WHERE reference_release_id = @release_id)
        OR (SELECT MAX(calendar_date) FROM ref.calendario WHERE reference_release_id = @release_id)
            <> (SELECT DATEADD(DAY, -1, valid_to_exclusive)
                FROM ref.reference_release WHERE reference_release_id = @release_id)
        OR (SELECT COUNT_BIG(*) FROM ref.calendario WHERE reference_release_id = @release_id)
            <> (SELECT DATEDIFF(
                    DAY,
                    (SELECT MIN(calendar_date) FROM ref.calendario
                     WHERE reference_release_id = @release_id),
                    valid_to_exclusive
                )
                FROM ref.reference_release WHERE reference_release_id = @release_id)
        OR EXISTS (
            SELECT 1
            FROM ref.reference_release AS release_definition
            INNER JOIN ref.calendario AS active_start
                ON active_start.reference_release_id = release_definition.reference_release_id
               AND active_start.calendar_date = release_definition.valid_from
            CROSS APPLY (
                SELECT MIN(calendar_date) AS first_calendar_date
                FROM ref.calendario
                WHERE reference_release_id = release_definition.reference_release_id
            ) AS first_row
            LEFT JOIN ref.calendario AS lookback_anchor
                ON lookback_anchor.reference_release_id = release_definition.reference_release_id
               AND lookback_anchor.calendar_date = first_row.first_calendar_date
            WHERE release_definition.reference_release_id = @release_id
              AND (
                  active_start.is_business_day = 1
                      AND first_row.first_calendar_date <> release_definition.valid_from
                  OR active_start.is_business_day = 0
                     AND (
                         first_row.first_calendar_date >= release_definition.valid_from
                         OR lookback_anchor.is_business_day <> 1
                         OR EXISTS (
                             SELECT 1 FROM ref.calendario AS later_lookback_business
                             WHERE later_lookback_business.reference_release_id = @release_id
                               AND later_lookback_business.is_business_day = 1
                               AND later_lookback_business.calendar_date
                                   > first_row.first_calendar_date
                               AND later_lookback_business.calendar_date
                                   < release_definition.valid_from
                         )
                     )
              )
        )
        OR EXISTS (
            SELECT 1
            FROM ref.calendario AS calendar_row
            LEFT JOIN ref.calendario AS billing_day
                ON billing_day.reference_release_id = calendar_row.reference_release_id
               AND billing_day.calendar_date = calendar_row.billing_reference_date
               AND billing_day.is_business_day = 1
            WHERE calendar_row.reference_release_id = @release_id
              AND calendar_row.is_business_day = 0
              AND (
                  billing_day.calendar_date IS NULL
                  OR EXISTS (
                      SELECT 1 FROM ref.calendario AS later_business_day
                      WHERE later_business_day.reference_release_id = calendar_row.reference_release_id
                        AND later_business_day.is_business_day = 1
                        AND later_business_day.calendar_date > calendar_row.billing_reference_date
                        AND later_business_day.calendar_date < calendar_row.calendar_date
                  )
              )
        )
    )
        THROW 51944, N'Calendário não cobre a release continuamente ou referência útil diverge.', 1;

    IF @family = N'BRANCH_ATTRIBUTION'
    BEGIN
        DECLARE @locked_branch_release_count BIGINT;
        SELECT @locked_branch_release_count = COUNT_BIG(*)
        FROM ref.reference_release WITH (UPDLOCK, HOLDLOCK)
        WHERE reference_release_id IN (
            SELECT DISTINCT branch_reference_release_id
            FROM ref.atribuicao_filial
            WHERE reference_release_id = @release_id
        );
        IF @locked_branch_release_count <> (
            SELECT COUNT_BIG(*) FROM (
                SELECT DISTINCT branch_reference_release_id
                FROM ref.atribuicao_filial
                WHERE reference_release_id = @release_id
            ) AS branch_releases
        ) THROW 51949, N'Release-mãe de uma atribuição não pôde ser bloqueada.', 1;
    END;

    IF @family = N'BRANCH_ATTRIBUTION' AND EXISTS (
        SELECT 1
        FROM ref.atribuicao_filial AS attribution
        LEFT JOIN ref.reference_release_ratification AS branch_ratification
            ON branch_ratification.reference_release_id = attribution.branch_reference_release_id
           AND branch_ratification.activation_scope = @activation_scope
        LEFT JOIN ref.reference_release_revocation AS branch_revocation
            ON branch_revocation.reference_release_id = attribution.branch_reference_release_id
           AND branch_revocation.activation_scope = @activation_scope
        WHERE attribution.reference_release_id = @release_id
          AND (branch_ratification.reference_release_id IS NULL
               OR branch_revocation.reference_release_id IS NOT NULL)
    )
        THROW 51945, N'Atribuição depende de release de filiais ativa no mesmo escopo.', 1;

    IF EXISTS (
        SELECT 1
        FROM inserted AS new_ratification
        INNER JOIN ref.reference_release AS new_release
            ON new_release.reference_release_id = new_ratification.reference_release_id
        INNER JOIN ref.reference_release AS existing_release WITH (UPDLOCK, HOLDLOCK,
            INDEX(IX_ref_reference_release_scope_validity))
            ON existing_release.family_code = new_release.family_code
           AND existing_release.scope_code = new_release.scope_code
           AND existing_release.reference_release_id <> new_release.reference_release_id
           AND new_release.valid_from < existing_release.valid_to_exclusive
           AND existing_release.valid_from < new_release.valid_to_exclusive
        INNER JOIN ref.reference_release_ratification AS existing_ratification
            ON existing_ratification.reference_release_id = existing_release.reference_release_id
           AND existing_ratification.activation_scope = new_ratification.activation_scope
        LEFT JOIN ref.reference_release_revocation AS existing_revocation
            ON existing_revocation.reference_release_id = existing_release.reference_release_id
           AND existing_revocation.activation_scope = existing_ratification.activation_scope
        WHERE existing_revocation.reference_release_id IS NULL
    )
        THROW 51907, N'Releases ratificadas não podem possuir vigências sobrepostas.', 1;
END;
GO

CREATE TRIGGER ref.trg_reference_revocation_guard
ON ref.reference_release_revocation
AFTER INSERT
AS
BEGIN
    SET NOCOUNT ON;
    IF EXISTS (
        SELECT 1
        FROM inserted AS revocation
        INNER JOIN ref.reference_release_ratification AS ratification
            ON ratification.reference_release_id = revocation.reference_release_id
           AND ratification.activation_scope = revocation.activation_scope
        WHERE revocation.revoked_at_utc < ratification.ratified_at_utc
    )
        THROW 51946, N'Revogação não pode anteceder sua ratificação.', 1;

    IF EXISTS (
        SELECT 1
        FROM inserted AS branch_revocation
        INNER JOIN ref.reference_release AS branch_release WITH (UPDLOCK, HOLDLOCK)
            ON branch_release.reference_release_id = branch_revocation.reference_release_id
           AND branch_release.family_code = N'BRANCH_OPERATIONS'
        INNER JOIN ref.atribuicao_filial AS attribution WITH (
            UPDLOCK, HOLDLOCK, INDEX(IX_ref_atribuicao_filial_branch_dependency)
        )
            ON attribution.branch_reference_release_id = branch_release.reference_release_id
        INNER JOIN ref.reference_release_ratification AS attribution_ratification WITH (
            UPDLOCK, HOLDLOCK
        ) ON attribution_ratification.reference_release_id = attribution.reference_release_id
           AND attribution_ratification.activation_scope = branch_revocation.activation_scope
        LEFT JOIN ref.reference_release_revocation AS attribution_revocation WITH (
            UPDLOCK, HOLDLOCK
        ) ON attribution_revocation.reference_release_id = attribution.reference_release_id
           AND attribution_revocation.activation_scope = branch_revocation.activation_scope
        WHERE attribution_revocation.reference_release_id IS NULL
    )
        THROW 51948, N'Release de filiais ainda sustenta atribuição ativa no mesmo escopo.', 1;
END;
GO

-- Toda tabela de conteúdo é insert-only, aceita escrita somente antes do recibo e toma lock na
-- release-mãe. Os triggers temporais acrescentam contenção de vigência e não sobreposição.
CREATE TRIGGER ref.trg_calendario_insert_guard
ON ref.calendario
AFTER INSERT, UPDATE, DELETE
AS
BEGIN
    SET NOCOUNT ON;
    IF EXISTS (SELECT 1 FROM deleted)
        THROW 51910, N'Calendário de uma release é imutável.', 1;
    DECLARE @locked_release_count BIGINT;
    SELECT @locked_release_count = COUNT_BIG(*)
    FROM ref.reference_release WITH (UPDLOCK, HOLDLOCK)
    WHERE reference_release_id IN (SELECT reference_release_id FROM inserted);
    IF @locked_release_count <> (
        SELECT COUNT_BIG(*) FROM (
            SELECT DISTINCT reference_release_id FROM inserted
        ) AS inserted_releases
    ) THROW 52040, N'Release-mãe do calendário não pôde ser bloqueada.', 1;
    IF EXISTS (
        SELECT 1 FROM inserted
        INNER JOIN ref.reference_import_receipt AS receipt
            ON receipt.reference_release_id = inserted.reference_release_id
    )
        THROW 51911, N'Release selada não aceita novas datas.', 1;
    IF EXISTS (
        SELECT 1 FROM inserted
        INNER JOIN ref.reference_release AS release_definition
            ON release_definition.reference_release_id = inserted.reference_release_id
        WHERE inserted.calendar_date < CASE
                  WHEN release_definition.valid_from < CONVERT(DATE, '00010201', 112)
                      THEN CONVERT(DATE, '00010101', 112)
                  ELSE DATEADD(DAY, -31, release_definition.valid_from)
              END
           OR inserted.calendar_date >= release_definition.valid_to_exclusive
    )
        THROW 52010, N'Data do calendário está fora da vigência ou lookback limitado.', 1;
END;
GO

CREATE TRIGGER ref.trg_status_coleta_insert_guard
ON ref.status_coleta
AFTER INSERT, UPDATE, DELETE
AS
BEGIN
    SET NOCOUNT ON;
    IF EXISTS (SELECT 1 FROM deleted)
        THROW 51912, N'Status de uma release é imutável.', 1;
    DECLARE @locked_release_count BIGINT;
    SELECT @locked_release_count = COUNT_BIG(*)
    FROM ref.reference_release WITH (UPDLOCK, HOLDLOCK)
    WHERE reference_release_id IN (SELECT reference_release_id FROM inserted);
    IF @locked_release_count <> (
        SELECT COUNT_BIG(*) FROM (
            SELECT DISTINCT reference_release_id FROM inserted
        ) AS inserted_releases
    ) THROW 52040, N'Release-mãe dos status não pôde ser bloqueada.', 1;
    IF EXISTS (
        SELECT 1 FROM inserted
        INNER JOIN ref.reference_import_receipt AS receipt
            ON receipt.reference_release_id = inserted.reference_release_id
    )
        THROW 51913, N'Release selada não aceita novos status.', 1;
    IF EXISTS (
        SELECT 1 FROM inserted
        INNER JOIN ref.reference_release AS release_definition
            ON release_definition.reference_release_id = inserted.reference_release_id
        WHERE inserted.valid_from < release_definition.valid_from
           OR inserted.valid_to_exclusive > release_definition.valid_to_exclusive
    )
        THROW 52011, N'Vigência de status está fora da release.', 1;
    IF EXISTS (
        SELECT 1 FROM inserted AS candidate
        INNER JOIN ref.status_coleta AS existing WITH (
            UPDLOCK, HOLDLOCK, INDEX(IX_ref_status_coleta_lookup)
        ) ON existing.reference_release_id = candidate.reference_release_id
           AND existing.raw_status = candidate.raw_status
           AND existing.valid_from <> candidate.valid_from
           AND candidate.valid_from < existing.valid_to_exclusive
           AND existing.valid_from < candidate.valid_to_exclusive
    )
        THROW 51914, N'Vigências de status não podem se sobrepor.', 1;
END;
GO

CREATE TRIGGER ref.trg_filial_operacional_insert_guard
ON ref.filial_operacional
AFTER INSERT, UPDATE, DELETE
AS
BEGIN
    SET NOCOUNT ON;
    IF EXISTS (SELECT 1 FROM deleted)
        THROW 51915, N'Filial de uma release é imutável.', 1;
    DECLARE @locked_release_count BIGINT;
    SELECT @locked_release_count = COUNT_BIG(*)
    FROM ref.reference_release WITH (UPDLOCK, HOLDLOCK)
    WHERE reference_release_id IN (SELECT reference_release_id FROM inserted);
    IF @locked_release_count <> (
        SELECT COUNT_BIG(*) FROM (
            SELECT DISTINCT reference_release_id FROM inserted
        ) AS inserted_releases
    ) THROW 52040, N'Release-mãe das filiais não pôde ser bloqueada.', 1;
    IF EXISTS (
        SELECT 1 FROM inserted
        INNER JOIN ref.reference_import_receipt AS receipt
            ON receipt.reference_release_id = inserted.reference_release_id
    )
        THROW 51916, N'Release selada não aceita novas filiais.', 1;
END;
GO

CREATE TRIGGER ref.trg_regiao_destino_alias_insert_guard
ON ref.regiao_destino_alias
AFTER INSERT, UPDATE, DELETE
AS
BEGIN
    SET NOCOUNT ON;
    IF EXISTS (SELECT 1 FROM deleted)
        THROW 51917, N'Alias de uma release é imutável.', 1;
    DECLARE @locked_release_count BIGINT;
    SELECT @locked_release_count = COUNT_BIG(*)
    FROM ref.reference_release WITH (UPDLOCK, HOLDLOCK)
    WHERE reference_release_id IN (SELECT reference_release_id FROM inserted);
    IF @locked_release_count <> (
        SELECT COUNT_BIG(*) FROM (
            SELECT DISTINCT reference_release_id FROM inserted
        ) AS inserted_releases
    ) THROW 52040, N'Release-mãe dos aliases não pôde ser bloqueada.', 1;
    IF EXISTS (
        SELECT 1 FROM inserted
        INNER JOIN ref.reference_import_receipt AS receipt
            ON receipt.reference_release_id = inserted.reference_release_id
    )
        THROW 51918, N'Release selada não aceita novos aliases.', 1;
    IF EXISTS (
        SELECT 1 FROM inserted
        INNER JOIN ref.reference_release AS release_definition
            ON release_definition.reference_release_id = inserted.reference_release_id
        WHERE inserted.valid_from < release_definition.valid_from
           OR inserted.valid_to_exclusive > release_definition.valid_to_exclusive
    )
        THROW 52012, N'Vigência de alias está fora da release.', 1;
    IF EXISTS (
        SELECT 1 FROM inserted AS candidate
        INNER JOIN ref.regiao_destino_alias AS existing WITH (
            UPDLOCK, HOLDLOCK, INDEX(IX_ref_regiao_destino_alias_lookup)
        ) ON existing.reference_release_id = candidate.reference_release_id
           AND existing.alias_scope = candidate.alias_scope
           AND existing.normalization_version = candidate.normalization_version
           AND existing.normalized_alias = candidate.normalized_alias
           AND existing.valid_from <> candidate.valid_from
           AND candidate.valid_from < existing.valid_to_exclusive
           AND existing.valid_from < candidate.valid_to_exclusive
    )
        THROW 51919, N'Vigências do mesmo alias não podem se sobrepor.', 1;
END;
GO

CREATE TRIGGER ref.trg_filial_documento_insert_guard
ON ref.filial_operacional_documento
AFTER INSERT, UPDATE, DELETE
AS
BEGIN
    SET NOCOUNT ON;
    IF EXISTS (SELECT 1 FROM deleted)
        THROW 51920, N'Documento de filial de uma release é imutável.', 1;
    DECLARE @locked_release_count BIGINT;
    SELECT @locked_release_count = COUNT_BIG(*)
    FROM ref.reference_release WITH (UPDLOCK, HOLDLOCK)
    WHERE reference_release_id IN (SELECT reference_release_id FROM inserted);
    IF @locked_release_count <> (
        SELECT COUNT_BIG(*) FROM (
            SELECT DISTINCT reference_release_id FROM inserted
        ) AS inserted_releases
    ) THROW 52040, N'Release-mãe dos documentos de filial não pôde ser bloqueada.', 1;
    IF EXISTS (
        SELECT 1 FROM inserted
        INNER JOIN ref.reference_import_receipt AS receipt
            ON receipt.reference_release_id = inserted.reference_release_id
    )
        THROW 51921, N'Release selada não aceita novos documentos de filial.', 1;
    IF EXISTS (
        SELECT 1 FROM inserted
        INNER JOIN ref.reference_release AS release_definition
            ON release_definition.reference_release_id = inserted.reference_release_id
        WHERE inserted.valid_from < release_definition.valid_from
           OR inserted.valid_to_exclusive > release_definition.valid_to_exclusive
    )
        THROW 52013, N'Vigência de documento de filial está fora da release.', 1;
    IF EXISTS (
        SELECT 1 FROM inserted AS candidate
        INNER JOIN ref.filial_operacional_documento AS existing WITH (
            UPDLOCK, HOLDLOCK, INDEX(IX_ref_filial_operacional_documento_lookup)
        ) ON existing.reference_release_id = candidate.reference_release_id
           AND (existing.token_scheme_version) = candidate.token_scheme_version
           AND (existing.document_token) = candidate.document_token
           AND existing.valid_from <> candidate.valid_from
           AND candidate.valid_from < existing.valid_to_exclusive
           AND existing.valid_from < candidate.valid_to_exclusive
    )
        THROW 51922, N'Vigências do mesmo documento de filial não podem se sobrepor.', 1;
END;
GO

CREATE TRIGGER ref.trg_frota_propria_insert_guard
ON ref.frota_propria_documento
AFTER INSERT, UPDATE, DELETE
AS
BEGIN
    SET NOCOUNT ON;
    IF EXISTS (SELECT 1 FROM deleted)
        THROW 51923, N'Documento de frota de uma release é imutável.', 1;
    DECLARE @locked_release_count BIGINT;
    SELECT @locked_release_count = COUNT_BIG(*)
    FROM ref.reference_release WITH (UPDLOCK, HOLDLOCK)
    WHERE reference_release_id IN (SELECT reference_release_id FROM inserted);
    IF @locked_release_count <> (
        SELECT COUNT_BIG(*) FROM (
            SELECT DISTINCT reference_release_id FROM inserted
        ) AS inserted_releases
    ) THROW 52040, N'Release-mãe dos documentos de frota não pôde ser bloqueada.', 1;
    IF EXISTS (
        SELECT 1 FROM inserted
        INNER JOIN ref.reference_import_receipt AS receipt
            ON receipt.reference_release_id = inserted.reference_release_id
    )
        THROW 51924, N'Release selada não aceita novos documentos de frota.', 1;
    IF EXISTS (
        SELECT 1 FROM inserted
        INNER JOIN ref.reference_release AS release_definition
            ON release_definition.reference_release_id = inserted.reference_release_id
        WHERE inserted.valid_from < release_definition.valid_from
           OR inserted.valid_to_exclusive > release_definition.valid_to_exclusive
    )
        THROW 52014, N'Vigência de documento de frota está fora da release.', 1;
    IF EXISTS (
        SELECT 1 FROM inserted AS candidate
        INNER JOIN ref.frota_propria_documento AS existing WITH (
            UPDLOCK, HOLDLOCK, INDEX(IX_ref_frota_propria_documento_lookup)
        ) ON existing.reference_release_id = candidate.reference_release_id
           AND existing.classification_scope = candidate.classification_scope
           AND (existing.token_scheme_version) = candidate.token_scheme_version
           AND (existing.document_token) = candidate.document_token
           AND existing.valid_from <> candidate.valid_from
           AND candidate.valid_from < existing.valid_to_exclusive
           AND existing.valid_from < candidate.valid_to_exclusive
    )
        THROW 51925, N'Vigências do mesmo documento de frota não podem se sobrepor.', 1;
END;
GO

CREATE TRIGGER ref.trg_classificacao_frota_alias_insert_guard
ON ref.classificacao_frota_alias
AFTER INSERT, UPDATE, DELETE
AS
BEGIN
    SET NOCOUNT ON;
    IF EXISTS (SELECT 1 FROM deleted)
        THROW 52020, N'Alias de frota de uma release é imutável.', 1;
    DECLARE @locked_release_count BIGINT;
    SELECT @locked_release_count = COUNT_BIG(*)
    FROM ref.reference_release WITH (UPDLOCK, HOLDLOCK)
    WHERE reference_release_id IN (SELECT reference_release_id FROM inserted);
    IF @locked_release_count <> (
        SELECT COUNT_BIG(*) FROM (
            SELECT DISTINCT reference_release_id FROM inserted
        ) AS inserted_releases
    ) THROW 52040, N'Release-mãe dos aliases de frota não pôde ser bloqueada.', 1;
    IF EXISTS (
        SELECT 1 FROM inserted
        INNER JOIN ref.reference_import_receipt AS receipt
            ON receipt.reference_release_id = inserted.reference_release_id
    )
        THROW 52021, N'Release selada não aceita novos aliases de frota.', 1;
    IF EXISTS (
        SELECT 1 FROM inserted
        INNER JOIN ref.reference_release AS release_definition
            ON release_definition.reference_release_id = inserted.reference_release_id
        WHERE inserted.valid_from < release_definition.valid_from
           OR inserted.valid_to_exclusive > release_definition.valid_to_exclusive
    )
        THROW 52022, N'Vigência de alias de frota está fora da release.', 1;
    IF EXISTS (
        SELECT 1 FROM inserted AS candidate
        INNER JOIN ref.classificacao_frota_alias AS existing WITH (
            UPDLOCK, HOLDLOCK, INDEX(IX_ref_classificacao_frota_alias_lookup)
        ) ON existing.reference_release_id = candidate.reference_release_id
           AND existing.classification_scope = candidate.classification_scope
           AND existing.alias_scope = candidate.alias_scope
           AND existing.normalization_version = candidate.normalization_version
           AND existing.normalized_alias = candidate.normalized_alias
           AND existing.valid_from <> candidate.valid_from
           AND candidate.valid_from < existing.valid_to_exclusive
           AND existing.valid_from < candidate.valid_to_exclusive
    )
        THROW 52023, N'Vigências do mesmo alias de frota não podem se sobrepor.', 1;
END;
GO

CREATE TRIGGER ref.trg_classificacao_frota_matriz_insert_guard
ON ref.classificacao_frota_matriz
AFTER INSERT, UPDATE, DELETE
AS
BEGIN
    SET NOCOUNT ON;
    IF EXISTS (SELECT 1 FROM deleted)
        THROW 52024, N'Matriz de frota de uma release é imutável.', 1;
    DECLARE @locked_release_count BIGINT;
    SELECT @locked_release_count = COUNT_BIG(*)
    FROM ref.reference_release WITH (UPDLOCK, HOLDLOCK)
    WHERE reference_release_id IN (SELECT reference_release_id FROM inserted);
    IF @locked_release_count <> (
        SELECT COUNT_BIG(*) FROM (
            SELECT DISTINCT reference_release_id FROM inserted
        ) AS inserted_releases
    ) THROW 52040, N'Release-mãe da matriz de frota não pôde ser bloqueada.', 1;
    IF EXISTS (
        SELECT 1 FROM inserted
        INNER JOIN ref.reference_import_receipt AS receipt
            ON receipt.reference_release_id = inserted.reference_release_id
    )
        THROW 52025, N'Release selada não aceita novas linhas da matriz de frota.', 1;
    IF EXISTS (
        SELECT 1 FROM inserted
        INNER JOIN ref.reference_release AS release_definition
            ON release_definition.reference_release_id = inserted.reference_release_id
        WHERE inserted.valid_from < release_definition.valid_from
           OR inserted.valid_to_exclusive > release_definition.valid_to_exclusive
    )
        THROW 52026, N'Vigência da matriz de frota está fora da release.', 1;
    IF EXISTS (
        SELECT 1 FROM inserted AS candidate
        INNER JOIN ref.classificacao_frota_matriz AS existing WITH (
            UPDLOCK, HOLDLOCK, INDEX(IX_ref_classificacao_frota_matriz_lookup)
        ) ON existing.reference_release_id = candidate.reference_release_id
           AND existing.classification_scope = candidate.classification_scope
           AND existing.vehicle_contract_class = candidate.vehicle_contract_class
           AND existing.driver_contract_class = candidate.driver_contract_class
           AND existing.valid_from <> candidate.valid_from
           AND candidate.valid_from < existing.valid_to_exclusive
           AND existing.valid_from < candidate.valid_to_exclusive
    )
        THROW 52027, N'Vigências da mesma célula da matriz não podem se sobrepor.', 1;
END;
GO

CREATE TRIGGER ref.trg_classificacao_frota_excecao_insert_guard
ON ref.classificacao_frota_excecao_token
AFTER INSERT, UPDATE, DELETE
AS
BEGIN
    SET NOCOUNT ON;
    IF EXISTS (SELECT 1 FROM deleted)
        THROW 52028, N'Exceção de frota de uma release é imutável.', 1;
    DECLARE @locked_release_count BIGINT;
    SELECT @locked_release_count = COUNT_BIG(*)
    FROM ref.reference_release WITH (UPDLOCK, HOLDLOCK)
    WHERE reference_release_id IN (SELECT reference_release_id FROM inserted);
    IF @locked_release_count <> (
        SELECT COUNT_BIG(*) FROM (
            SELECT DISTINCT reference_release_id FROM inserted
        ) AS inserted_releases
    ) THROW 52040, N'Release-mãe das exceções de frota não pôde ser bloqueada.', 1;
    IF EXISTS (
        SELECT 1 FROM inserted
        INNER JOIN ref.reference_import_receipt AS receipt
            ON receipt.reference_release_id = inserted.reference_release_id
    )
        THROW 52029, N'Release selada não aceita novas exceções de frota.', 1;
    IF EXISTS (
        SELECT 1 FROM inserted
        INNER JOIN ref.reference_release AS release_definition
            ON release_definition.reference_release_id = inserted.reference_release_id
        WHERE inserted.valid_from < release_definition.valid_from
           OR inserted.valid_to_exclusive > release_definition.valid_to_exclusive
    )
        THROW 52030, N'Vigência de exceção de frota está fora da release.', 1;
    IF EXISTS (
        SELECT 1 FROM inserted AS candidate
        INNER JOIN ref.classificacao_frota_excecao_token AS existing WITH (
            UPDLOCK, HOLDLOCK, INDEX(IX_ref_classificacao_frota_excecao_lookup)
        ) ON existing.reference_release_id = candidate.reference_release_id
           AND existing.classification_scope = candidate.classification_scope
           AND existing.exception_scope = candidate.exception_scope
           AND (existing.token_scheme_version) = candidate.token_scheme_version
           AND (existing.subject_token) = candidate.subject_token
           AND existing.valid_from <> candidate.valid_from
           AND candidate.valid_from < existing.valid_to_exclusive
           AND existing.valid_from < candidate.valid_to_exclusive
    )
        THROW 52031, N'Vigências da mesma exceção de frota não podem se sobrepor.', 1;
END;
GO

CREATE TRIGGER ref.trg_atribuicao_filial_insert_guard
ON ref.atribuicao_filial
AFTER INSERT, UPDATE, DELETE
AS
BEGIN
    SET NOCOUNT ON;
    IF EXISTS (SELECT 1 FROM deleted)
        THROW 51926, N'Atribuição de uma release é imutável.', 1;
    DECLARE @locked_release_count BIGINT;
    SELECT @locked_release_count = COUNT_BIG(*)
    FROM ref.reference_release WITH (UPDLOCK, HOLDLOCK)
    WHERE reference_release_id IN (SELECT reference_release_id FROM inserted);
    IF @locked_release_count <> (
        SELECT COUNT_BIG(*) FROM (
            SELECT DISTINCT reference_release_id FROM inserted
        ) AS inserted_releases
    ) THROW 52040, N'Release-mãe das atribuições não pôde ser bloqueada.', 1;
    IF EXISTS (
        SELECT 1 FROM inserted
        INNER JOIN ref.reference_import_receipt AS receipt
            ON receipt.reference_release_id = inserted.reference_release_id
    )
        THROW 51927, N'Release selada não aceita novas atribuições.', 1;
    IF EXISTS (
        SELECT 1 FROM inserted
        INNER JOIN ref.reference_release AS own_release
            ON own_release.reference_release_id = inserted.reference_release_id
        INNER JOIN ref.reference_release AS branch_release
            ON branch_release.reference_release_id = inserted.branch_reference_release_id
        WHERE inserted.valid_from < own_release.valid_from
           OR inserted.valid_to_exclusive > own_release.valid_to_exclusive
           OR inserted.valid_from < branch_release.valid_from
           OR inserted.valid_to_exclusive > branch_release.valid_to_exclusive
    )
        THROW 52032, N'Vigência da atribuição está fora de sua release ou da release de filial.', 1;
    IF EXISTS (
        SELECT 1 FROM inserted AS candidate
        INNER JOIN ref.atribuicao_filial AS existing WITH (
            UPDLOCK, HOLDLOCK, INDEX(IX_ref_atribuicao_filial_lookup)
        ) ON existing.reference_release_id = candidate.reference_release_id
           AND (existing.token_scheme_version) = candidate.token_scheme_version
           AND (existing.payer_document_token) = candidate.payer_document_token
           AND existing.valid_from <> candidate.valid_from
           AND candidate.valid_from < existing.valid_to_exclusive
           AND existing.valid_from < candidate.valid_to_exclusive
    )
        THROW 51928, N'Vigências da mesma atribuição não podem se sobrepor.', 1;
END;
GO

CREATE TRIGGER ref.trg_exclusao_cubagem_insert_guard
ON ref.pagador_exclusao_cubagem
AFTER INSERT, UPDATE, DELETE
AS
BEGIN
    SET NOCOUNT ON;
    IF EXISTS (SELECT 1 FROM deleted)
        THROW 51929, N'Exclusão de cubagem de uma release é imutável.', 1;
    DECLARE @locked_release_count BIGINT;
    SELECT @locked_release_count = COUNT_BIG(*)
    FROM ref.reference_release WITH (UPDLOCK, HOLDLOCK)
    WHERE reference_release_id IN (SELECT reference_release_id FROM inserted);
    IF @locked_release_count <> (
        SELECT COUNT_BIG(*) FROM (
            SELECT DISTINCT reference_release_id FROM inserted
        ) AS inserted_releases
    ) THROW 52040, N'Release-mãe das exclusões de cubagem não pôde ser bloqueada.', 1;
    IF EXISTS (
        SELECT 1 FROM inserted
        INNER JOIN ref.reference_import_receipt AS receipt
            ON receipt.reference_release_id = inserted.reference_release_id
    )
        THROW 51930, N'Release selada não aceita novas exclusões de cubagem.', 1;
    IF EXISTS (
        SELECT 1 FROM inserted
        INNER JOIN ref.reference_release AS release_definition
            ON release_definition.reference_release_id = inserted.reference_release_id
        WHERE inserted.valid_from < release_definition.valid_from
           OR inserted.valid_to_exclusive > release_definition.valid_to_exclusive
    )
        THROW 52033, N'Vigência da exclusão de cubagem está fora da release.', 1;
    IF EXISTS (
        SELECT 1 FROM inserted AS candidate
        INNER JOIN ref.pagador_exclusao_cubagem AS existing WITH (
            UPDLOCK, HOLDLOCK, INDEX(IX_ref_pagador_exclusao_cubagem_lookup)
        ) ON existing.reference_release_id = candidate.reference_release_id
           AND (existing.token_scheme_version) = candidate.token_scheme_version
           AND (existing.payer_document_token) = candidate.payer_document_token
           AND existing.valid_from <> candidate.valid_from
           AND candidate.valid_from < existing.valid_to_exclusive
           AND existing.valid_from < candidate.valid_to_exclusive
    )
        THROW 51931, N'Vigências da mesma exclusão de cubagem não podem se sobrepor.', 1;
END;
GO

CREATE TRIGGER ref.trg_regiao_logistica_cep_insert_guard
ON ref.regiao_logistica_cep
AFTER INSERT, UPDATE, DELETE
AS
BEGIN
    SET NOCOUNT ON;
    IF EXISTS (SELECT 1 FROM deleted)
        THROW 51932, N'Região CEP de uma release é imutável.', 1;
    DECLARE @locked_release_count BIGINT;
    SELECT @locked_release_count = COUNT_BIG(*)
    FROM ref.reference_release WITH (UPDLOCK, HOLDLOCK)
    WHERE reference_release_id IN (SELECT reference_release_id FROM inserted);
    IF @locked_release_count <> (
        SELECT COUNT_BIG(*) FROM (
            SELECT DISTINCT reference_release_id FROM inserted
        ) AS inserted_releases
    ) THROW 52040, N'Release-mãe das regiões CEP não pôde ser bloqueada.', 1;
    IF EXISTS (
        SELECT 1 FROM inserted
        INNER JOIN ref.reference_import_receipt AS receipt
            ON receipt.reference_release_id = inserted.reference_release_id
    )
        THROW 51933, N'Release selada não aceita novas regiões CEP.', 1;
    IF EXISTS (
        SELECT 1 FROM inserted
        INNER JOIN ref.reference_release AS release_definition
            ON release_definition.reference_release_id = inserted.reference_release_id
        WHERE inserted.valid_from < release_definition.valid_from
           OR inserted.valid_to_exclusive > release_definition.valid_to_exclusive
    )
        THROW 52034, N'Vigência de região CEP está fora da release.', 1;
    IF EXISTS (
        SELECT 1 FROM inserted AS candidate
        INNER JOIN ref.regiao_logistica_cep AS existing WITH (
            UPDLOCK, HOLDLOCK, INDEX(IX_ref_regiao_logistica_cep_lookup)
        ) ON existing.reference_release_id = candidate.reference_release_id
           AND NOT (
               existing.cep_start = candidate.cep_start
               AND existing.cep_end = candidate.cep_end
               AND existing.valid_from = candidate.valid_from
           )
           AND candidate.cep_start <= existing.cep_end
           AND existing.cep_start <= candidate.cep_end
           AND candidate.valid_from < existing.valid_to_exclusive
           AND existing.valid_from < candidate.valid_to_exclusive
    )
        THROW 51934, N'Faixas CEP vigentes não podem se sobrepor.', 1;
END;
GO

CREATE TRIGGER ref.trg_regiao_logistica_cidade_insert_guard
ON ref.regiao_logistica_cidade_uf
AFTER INSERT, UPDATE, DELETE
AS
BEGIN
    SET NOCOUNT ON;
    IF EXISTS (SELECT 1 FROM deleted)
        THROW 51935, N'Região cidade/UF de uma release é imutável.', 1;
    DECLARE @locked_release_count BIGINT;
    SELECT @locked_release_count = COUNT_BIG(*)
    FROM ref.reference_release WITH (UPDLOCK, HOLDLOCK)
    WHERE reference_release_id IN (SELECT reference_release_id FROM inserted);
    IF @locked_release_count <> (
        SELECT COUNT_BIG(*) FROM (
            SELECT DISTINCT reference_release_id FROM inserted
        ) AS inserted_releases
    ) THROW 52040, N'Release-mãe das regiões cidade/UF não pôde ser bloqueada.', 1;
    IF EXISTS (
        SELECT 1 FROM inserted
        INNER JOIN ref.reference_import_receipt AS receipt
            ON receipt.reference_release_id = inserted.reference_release_id
    )
        THROW 51936, N'Release selada não aceita novas regiões cidade/UF.', 1;
    IF EXISTS (
        SELECT 1 FROM inserted
        INNER JOIN ref.reference_release AS release_definition
            ON release_definition.reference_release_id = inserted.reference_release_id
        WHERE inserted.valid_from < release_definition.valid_from
           OR inserted.valid_to_exclusive > release_definition.valid_to_exclusive
    )
        THROW 52035, N'Vigência de região cidade/UF está fora da release.', 1;
    IF EXISTS (
        SELECT 1 FROM inserted AS candidate
        INNER JOIN ref.regiao_logistica_cidade_uf AS existing WITH (
            UPDLOCK, HOLDLOCK, INDEX(IX_ref_regiao_logistica_cidade_uf_lookup)
        ) ON existing.reference_release_id = candidate.reference_release_id
           AND existing.uf = candidate.uf
           AND existing.city_normalization_version = candidate.city_normalization_version
           AND existing.normalized_city = candidate.normalized_city
           AND existing.valid_from <> candidate.valid_from
           AND candidate.valid_from < existing.valid_to_exclusive
           AND existing.valid_from < candidate.valid_to_exclusive
    )
        THROW 51937, N'Vigências da mesma cidade/UF não podem se sobrepor.', 1;
END;
GO

CREATE TRIGGER ref.trg_tarifa_rota_uf_insert_guard
ON ref.tarifa_rota_uf
AFTER INSERT, UPDATE, DELETE
AS
BEGIN
    SET NOCOUNT ON;
    IF EXISTS (SELECT 1 FROM deleted)
        THROW 51938, N'Tarifa de uma release é imutável.', 1;
    DECLARE @locked_release_count BIGINT;
    SELECT @locked_release_count = COUNT_BIG(*)
    FROM ref.reference_release WITH (UPDLOCK, HOLDLOCK)
    WHERE reference_release_id IN (SELECT reference_release_id FROM inserted);
    IF @locked_release_count <> (
        SELECT COUNT_BIG(*) FROM (
            SELECT DISTINCT reference_release_id FROM inserted
        ) AS inserted_releases
    ) THROW 52040, N'Release-mãe das tarifas não pôde ser bloqueada.', 1;
    IF EXISTS (
        SELECT 1 FROM inserted
        INNER JOIN ref.reference_import_receipt AS receipt
            ON receipt.reference_release_id = inserted.reference_release_id
    )
        THROW 51939, N'Release selada não aceita novas tarifas.', 1;
    IF EXISTS (
        SELECT 1 FROM inserted
        INNER JOIN ref.reference_release AS release_definition
            ON release_definition.reference_release_id = inserted.reference_release_id
        WHERE inserted.valid_from < release_definition.valid_from
           OR inserted.valid_to_exclusive > release_definition.valid_to_exclusive
    )
        THROW 52036, N'Vigência de tarifa está fora da release.', 1;
    IF EXISTS (
        SELECT 1 FROM inserted AS candidate
        INNER JOIN ref.tarifa_rota_uf AS existing WITH (
            UPDLOCK, HOLDLOCK, INDEX(IX_ref_tarifa_rota_uf_lookup)
        ) ON existing.reference_release_id = candidate.reference_release_id
           AND existing.origin_uf = candidate.origin_uf
           AND existing.destination_uf = candidate.destination_uf
           AND existing.valid_from <> candidate.valid_from
           AND candidate.valid_from < existing.valid_to_exclusive
           AND existing.valid_from < candidate.valid_to_exclusive
    )
        THROW 51940, N'Vigências da mesma rota tarifária não podem se sobrepor.', 1;
END;
GO

-- Nenhum GRANT é publicado por V008. O runtime e public permanecem sem SELECT/DML direto em ref;
-- futuras leituras set-based só poderão ser expostas por objetos aprovados e ownership chain.
