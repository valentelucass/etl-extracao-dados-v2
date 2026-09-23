package br.com.esl.etl.v2.plataforma.autorizacao;

import java.time.Instant;
import java.util.UUID;

/** No public constructor/factory. Only a checked durable SQL receipt can mint this capability. */
public final class DurableRuntimeCapability {
    final RuntimeAuthorizationScope scope;
    final UUID receipt;
    final UUID auditReference;
    final long mappingVersion;
    final long scopeVersion;
    final Instant authorizedAt;
    final Instant validUntil;

    DurableRuntimeCapability(
            final RuntimeAuthorizationScope scope,
            final UUID receipt,
            final UUID auditReference,
            final long mappingVersion,
            final long scopeVersion,
            final Instant authorizedAt,
            final Instant validUntil) {
        this.scope = scope;
        this.receipt = receipt;
        this.auditReference = auditReference;
        this.mappingVersion = mappingVersion;
        this.scopeVersion = scopeVersion;
        this.authorizedAt = authorizedAt;
        this.validUntil = validUntil;
    }

    public UUID invocationId() {
        return scope.invocationId();
    }

    public Instant validUntil() {
        return validUntil;
    }

    @Override
    public String toString() {
        return "DurableRuntimeCapability[redacted]";
    }
}
