package br.com.esl.etl.v2.plataforma.autorizacao;

import java.sql.Connection;
import java.util.function.UnaryOperator;

/** Fault instrumentation around the real administered authority, including every trust check. */
public final class RuntimeUsersPhysicalAuthority {
    private RuntimeUsersPhysicalAuthority() {}

    public static WindowsSqlRuntimeAuthorization create(final UnaryOperator<Connection> observer) {
        try {
            if (!System.getProperty("os.name", "").startsWith("Windows")) {
                throw new IllegalStateException("WINDOWS_AUTHENTICATION_REQUIRED");
            }
            AdministeredArtifactVerifier.verify();
            final var configuration = RuntimeAuthorityConfiguration.load();
            return new WindowsSqlRuntimeAuthorization(
                    configuration, () -> observer.apply(AdministeredSqlConnection.open()));
        } catch (final java.io.IOException | RuntimeException failure) {
            throw new DurableAuthorizationException(
                    DurableAuthorizationException.Reason.UNCONFIGURED, failure);
        }
    }
}
