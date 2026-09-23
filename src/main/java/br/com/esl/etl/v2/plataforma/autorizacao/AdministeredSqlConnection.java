package br.com.esl.etl.v2.plataforma.autorizacao;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/** Shared TLS configuration for authority and workload; connects only to the local SQL instance. */
public final class AdministeredSqlConnection {
    private AdministeredSqlConnection() {}

    public static Connection open() throws SQLException {
        try {
            if (!System.getProperty("os.name", "").startsWith("Windows")) {
                throw new SQLException("WINDOWS_SQL_REQUIRED");
            }
            final var configuration = RuntimeAuthorityConfiguration.load();
            if (!"ETL_SISTEMA_V2_SHADOW".equals(configuration.database)) {
                throw new SQLException("LOCAL_SHADOW_TARGET_REQUIRED");
            }
            // Validate the administered certificate even before the driver opens a socket.
            new PinnedSqlTrustManager();
            return LaboratorySqlBudget.openIfApplicable(
                    () -> DriverManager.getConnection(configuration.jdbcUrl()));
        } catch (final java.io.IOException | java.security.cert.CertificateException failure) {
            throw new SQLException("ADMINISTERED_SQL_TRUST_UNAVAILABLE", failure);
        }
    }
}
