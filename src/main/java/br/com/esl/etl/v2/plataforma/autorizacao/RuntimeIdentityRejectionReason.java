package br.com.esl.etl.v2.plataforma.autorizacao;

/** Categorias internas e sanitizadas de falha na verificação de identidade. */
public enum RuntimeIdentityRejectionReason {
    UNCONFIGURED,
    MISSING,
    INVALID,
    BLOCKED,
    REVOKED,
    AUDIENCE_MISMATCH,
    AUTHORITY_UNAVAILABLE,
    ROLE_MAPPING_INVALID
}
