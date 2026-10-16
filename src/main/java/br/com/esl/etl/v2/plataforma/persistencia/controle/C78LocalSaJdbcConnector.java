package br.com.esl.etl.v2.plataforma.persistencia.controle;

import br.com.esl.etl.v2.plataforma.configuracao.C78DevPeerProof;
import br.com.esl.etl.v2.plataforma.configuracao.C78LocalDriverPolicy;
import br.com.esl.etl.v2.plataforma.configuracao.C78LocalSocketFactory;
import br.com.esl.etl.v2.plataforma.configuracao.C78LocalSqlTarget;
import br.com.esl.etl.v2.plataforma.configuracao.C78LocalTlsScope;
import br.com.esl.etl.v2.plataforma.configuracao.C78LocalTrustManager;
import br.com.esl.etl.v2.plataforma.configuracao.SecretKey;
import br.com.esl.etl.v2.plataforma.configuracao.SecretProvider;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;
import java.util.Properties;
import java.util.function.Supplier;

/** Isolated opt-in connector; this class runs no application SQL or session settings. */
public final class C78LocalSaJdbcConnector {
    @FunctionalInterface
    interface Connections {
        Connection open(String url, Properties fixed) throws SQLException;
    }

    private C78LocalSaJdbcConnector() {}

    public static final class ConnectionFailure extends SQLException {
        private static final long serialVersionUID = 1L;
        private final String pinReason;
        private final boolean cleanupUncertain;

        private ConnectionFailure(final String pinReason, final boolean cleanupUncertain) {
            super("C78_LOCAL_SHADOW_SQL_PIN_CONNECT_FAILED_" + pinReason);
            this.pinReason = pinReason;
            this.cleanupUncertain = cleanupUncertain;
            if (cleanupUncertain) {
                addSuppressed(new SQLException("C78_LOCAL_SHADOW_SQL_PIN_CLOSE_UNCERTAIN"));
            }
        }

        public String pinReason() {
            return pinReason;
        }

        public boolean cleanupUncertain() {
            return cleanupUncertain;
        }
    }

    public static Connection open(final C78LocalSqlTarget target, final SecretProvider secrets)
            throws SQLException {
        C78LocalTlsScope.validate(target);
        final var driver = C78LocalDriverPolicy.load();
        return open(target, secrets, driver::connect, () -> C78LocalTlsScope.enter(target));
    }

    public static Connection openDev(
            final C78LocalSqlTarget target,
            final SecretProvider secrets,
            final C78DevPeerProof proof)
            throws SQLException {
        C78LocalTlsScope.validate(target);
        Objects.requireNonNull(proof);
        final var driver = C78LocalDriverPolicy.load();
        return open(
                target, secrets, driver::connect, () -> C78LocalTlsScope.enterDev(target, proof));
    }

    static Connection open(
            final C78LocalSqlTarget target,
            final SecretProvider secrets,
            final Connections connector,
            final Supplier<C78LocalTlsScope> scopes)
            throws SQLException {
        C78LocalTlsScope.validate(target);
        final var fixed = new Properties();
        C78LocalTlsScope scope = null;
        Connection connection = null;
        try {
            scope = Objects.requireNonNull(scopes.get());
            try (final var ownedScope = scope) {
                final String password;
                try {
                    password =
                            Objects.requireNonNull(secrets)
                                    .require(SecretKey.SHADOW_SAMPLE_SA_PASSWORD);
                    if (password == null || password.isBlank() || password.length() > 4096) {
                        throw new IllegalStateException("C78_LOCAL_SHADOW_SECRET_INVALID");
                    }
                } catch (final RuntimeException | Error unavailable) {
                    throw new IllegalStateException("C78_LOCAL_SHADOW_SECRET_UNAVAILABLE");
                }
                fixed.setProperty("user", "sa");
                fixed.setProperty("password", password);
                configureDriver(fixed);
                connection = Objects.requireNonNull(connector.open(C78LocalSqlTarget.URL, fixed));
                ownedScope.acceptConnection();
                return connection;
            }
        } catch (final Exception | Error failure) {
            boolean closeUncertain = scope != null && scope.socketCloseUncertain();
            if (connection != null) {
                try {
                    connection.close();
                } catch (final SQLException | RuntimeException | Error cleanup) {
                    closeUncertain = true;
                }
            }
            throw new ConnectionFailure(
                    scope == null ? "SCOPE_CREATION_REFUSED" : scope.failureClass(),
                    closeUncertain);
        } finally {
            fixed.clear();
        }
    }

    static void configureDriver(final Properties fixed) {
        fixed.setProperty("authentication", "SqlPassword");
        fixed.setProperty("connectRetryCount", "0");
        fixed.setProperty("loginTimeout", "5");
        fixed.setProperty("socketTimeout", "10000");
        fixed.setProperty("queryTimeout", "10");
        fixed.setProperty("cancelQueryTimeout", "5");
        fixed.setProperty("transparentNetworkIPResolution", "false");
        fixed.setProperty("multiSubnetFailover", "false");
        fixed.setProperty("socketFactoryClass", C78LocalSocketFactory.class.getName());
        fixed.setProperty("trustManagerClass", C78LocalTrustManager.class.getName());
        fixed.setProperty("applicationName", "V2C78LocalShadow");
        fixed.setProperty("sslProtocol", "TLSv1.2");
    }
}
