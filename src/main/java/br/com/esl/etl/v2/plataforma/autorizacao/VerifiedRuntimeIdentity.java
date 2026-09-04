package br.com.esl.etl.v2.plataforma.autorizacao;

import java.time.Instant;
import java.util.Objects;

/**
 * Identidade provider-neutral que um adaptador confiável já verificou e mapeou para papéis
 * internos.
 */
public record VerifiedRuntimeIdentity(
        PrincipalAuditReference auditReference,
        RuntimePrincipalKind kind,
        RuntimeRoleSet roles,
        Instant validFrom,
        Instant expiresAt,
        AuthorizationPolicyFingerprint authorityPolicyFingerprint) {

    public VerifiedRuntimeIdentity {
        Objects.requireNonNull(auditReference, "A referência de auditoria é obrigatória.");
        Objects.requireNonNull(kind, "A classificação da identidade é obrigatória.");
        Objects.requireNonNull(roles, "Os papéis verificados são obrigatórios.");
        Objects.requireNonNull(validFrom, "O início da validade é obrigatório.");
        Objects.requireNonNull(expiresAt, "O término da validade é obrigatório.");
        Objects.requireNonNull(
                authorityPolicyFingerprint, "A impressão da política da autoridade é obrigatória.");
        if (!validFrom.isBefore(expiresAt)) {
            throw new IllegalArgumentException(
                    "O término da validade deve ser posterior ao início.");
        }
    }

    @Override
    public String toString() {
        return "VerifiedRuntimeIdentity[redacted]";
    }
}
