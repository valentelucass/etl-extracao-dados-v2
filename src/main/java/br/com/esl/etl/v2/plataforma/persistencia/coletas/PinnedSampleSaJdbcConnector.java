package br.com.esl.etl.v2.plataforma.persistencia.coletas;

import br.com.esl.etl.v2.plataforma.configuracao.PinnedSampleFailureEvidence;
import br.com.esl.etl.v2.plataforma.configuracao.PinnedSampleSocketFactory;
import br.com.esl.etl.v2.plataforma.configuracao.PinnedSampleTlsScope;
import br.com.esl.etl.v2.plataforma.configuracao.PinnedSampleTrustManager;
import br.com.esl.etl.v2.plataforma.configuracao.SecretKey;
import br.com.esl.etl.v2.plataforma.configuracao.SecretProvider;
import br.com.esl.etl.v2.plataforma.configuracao.ShadowStorageProperties;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Objects;
import java.util.Properties;

/**
 * Separate JDBC connector with supported driver extensions; no application SQL/session settings.
 */
public final class PinnedSampleSaJdbcConnector {
    private static final String FIXED_URL =
            "jdbc:sqlserver://localhost:1433;databaseName=ETL_SISTEMA_V2_SHADOW"
                    + ";integratedSecurity=false;encrypt=true;trustServerCertificate=false";

    private PinnedSampleSaJdbcConnector() {}

    /** Typed diagnostic fields contain only fixed labels, never the driver message/cause. */
    public static final class ConnectionFailure extends SQLException {
        private static final long serialVersionUID = 1L;
        private final String pinReason;
        private final boolean tlsCauseObserved;
        private final PinnedSampleFailureEvidence evidence;

        private ConnectionFailure(
                final String code,
                final String state,
                final int vendorCode,
                final String pinReason,
                final boolean tlsCauseObserved) {
            this(
                    code,
                    state,
                    vendorCode,
                    pinReason,
                    tlsCauseObserved,
                    PinnedSampleFailureEvidence.capture(null, null, false));
        }

        private ConnectionFailure(
                final String code,
                final String state,
                final int vendorCode,
                final String pinReason,
                final boolean tlsCauseObserved,
                final PinnedSampleFailureEvidence evidence) {
            super(code, state, vendorCode);
            this.pinReason = pinReason;
            this.tlsCauseObserved = tlsCauseObserved;
            this.evidence = evidence;
        }

        public String pinReason() {
            return pinReason;
        }

        public boolean tlsCauseObserved() {
            return tlsCauseObserved;
        }

        public PinnedSampleFailureEvidence failureEvidence() {
            return evidence;
        }
    }

    public static Connection open(
            final ShadowStorageProperties target, final SecretProvider secrets)
            throws SQLException {
        PinnedSampleTlsScope.validate(target);
        final Properties credentials = new Properties();
        try {
            final String password;
            try {
                password =
                        Objects.requireNonNull(secrets)
                                .require(SecretKey.SHADOW_SAMPLE_SA_PASSWORD);
                if (password == null || password.isBlank() || password.length() > 4096) {
                    throw new IllegalStateException("COL_SAMPLE_SQL_PIN_SECRET_INVALID");
                }
            } catch (final RuntimeException | Error secretFailure) {
                throw new IllegalStateException("COL_SAMPLE_SQL_PIN_SECRET_UNAVAILABLE");
            }
            credentials.setProperty("user", "sa");
            credentials.setProperty("password", password);
            return openCredentials(target, credentials);
        } finally {
            credentials.clear();
        }
    }

    static Connection openCredentials(
            final ShadowStorageProperties target, final Properties credentials)
            throws SQLException {
        return openCredentials(
                target,
                credentials,
                DriverManager::getConnection,
                () -> PinnedSampleTlsScope.enter(target));
    }

    static Connection openCredentials(
            final ShadowStorageProperties target,
            final Properties credentials,
            final ColetaTemporalLaboratorySession.SqlConnections connector,
            final java.util.function.Supplier<PinnedSampleTlsScope> scopes)
            throws SQLException {
        PinnedSampleTlsScope.validate(target);
        if (!"sa".equals(credentials.getProperty("user"))
                || credentials.getProperty("password") == null
                || credentials.getProperty("password").isBlank()) {
            throw new IllegalArgumentException("COL_SAMPLE_SQL_PIN_SA_CREDENTIALS_REQUIRED");
        }
        final Properties fixed = new Properties();
        fixed.setProperty("user", "sa");
        fixed.setProperty("password", credentials.getProperty("password"));
        configureDriver(fixed);
        Connection connection = null;
        final var scope = scopes.get();
        try (scope) {
            connection = connector.open(FIXED_URL, fixed);
            scope.acceptConnection();
            return connection;
        } catch (final Exception | LinkageError failure) {
            final SQLException safe;
            final String code = "COL_SAMPLE_SQL_PIN_CONNECT_FAILED_" + scope.failureClass();
            Throwable closeFailure = null;
            if (connection != null) {
                try {
                    connection.close();
                } catch (final SQLException | RuntimeException | Error cleanup) {
                    closeFailure = cleanup;
                }
            }
            final var evidence =
                    PinnedSampleFailureEvidence.capture(
                            failure,
                            closeFailure,
                            scope.socketCloseUncertain() || closeFailure != null);
            if (failure instanceof SQLException sql) {
                final String state = sql.getSQLState();
                safe =
                        new ConnectionFailure(
                                code,
                                state != null && state.matches("[A-Z0-9]{5}") ? state : null,
                                sql.getErrorCode(),
                                scope.failureClass(),
                                evidence.tlsObserved(),
                                evidence);
            } else {
                safe =
                        new ConnectionFailure(
                                code,
                                null,
                                0,
                                scope.failureClass(),
                                evidence.tlsObserved(),
                                evidence);
            }
            if (evidence.cleanupUncertain()) {
                safe.addSuppressed(new SQLException("COL_SAMPLE_SQL_PIN_CLEANUP_CLOSE_UNCERTAIN"));
            }
            throw safe;
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
        fixed.setProperty("socketFactoryClass", PinnedSampleSocketFactory.class.getName());
        fixed.setProperty("trustManagerClass", PinnedSampleTrustManager.class.getName());
        fixed.setProperty("applicationName", "Coletas6908PinnedSample");
    }
}
