package br.com.esl.etl.v2.plataforma.autorizacao;

/** Motivos técnicos limitados, próprios para auditoria e sem dados de identidade. */
public enum RuntimeAuthorizationReason {
    AUTHORIZED,
    IDENTITY_UNCONFIGURED,
    IDENTITY_MISSING,
    IDENTITY_INVALID,
    IDENTITY_NOT_YET_VALID,
    IDENTITY_EXPIRED,
    IDENTITY_BLOCKED,
    IDENTITY_REVOKED,
    IDENTITY_AUDIENCE_MISMATCH,
    AUTHORITY_UNAVAILABLE,
    ROLE_MAPPING_INVALID,
    ROLE_NOT_GRANTED,
    AUDIT_UNAVAILABLE,
    TEMPORAL_VALIDATION_UNAVAILABLE
}
