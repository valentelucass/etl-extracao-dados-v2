package br.com.esl.etl.v2.plataforma.autorizacao;

import java.io.IOException;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;

/** Administration pins this resource in the distributed artifact, never in CLI/environment. */
final class RuntimeAuthorityConfiguration {
    final String server;
    final String database;
    final UUID authority;
    final String policy;

    RuntimeAuthorityConfiguration(final Properties values) {
        if (!values.stringPropertyNames()
                .equals(Set.of("server", "database", "authorityId", "policyFingerprint"))) {
            throw new IllegalArgumentException("AUTHORITY_CONFIGURATION_SCHEMA");
        }
        server = values.getProperty("server");
        database = values.getProperty("database");
        authority = UUID.fromString(values.getProperty("authorityId"));
        policy = values.getProperty("policyFingerprint");
        if (!server.matches("[A-Za-z0-9][A-Za-z0-9.-]{0,127}")
                || !database.matches("[A-Za-z][A-Za-z0-9_]{0,127}")
                || !policy.matches("[a-f0-9]{64}")
                || !policy.equals(RuntimeAuthorizationPolicy.standard().fingerprint().sha256())) {
            throw new IllegalArgumentException("AUTHORITY_CONFIGURATION_INVALID");
        }
    }

    static RuntimeAuthorityConfiguration load() throws IOException {
        try (var input =
                RuntimeAuthorityConfiguration.class.getResourceAsStream(
                        "/runtime-authority.properties")) {
            if (input == null) {
                throw new IOException("AUTHORITY_NOT_PROVISIONED");
            }
            final byte[] bytes = input.readNBytes(4097);
            if (bytes.length > 4096) {
                throw new IOException("AUTHORITY_CONFIGURATION_LIMIT");
            }
            final Properties properties =
                    new Properties() {
                        private static final long serialVersionUID = 1L;

                        @Override
                        public synchronized Object put(final Object key, final Object value) {
                            if (containsKey(key)) {
                                throw new IllegalArgumentException("AUTHORITY_DUPLICATE_KEY");
                            }
                            return super.put(key, value);
                        }
                    };
            properties.load(
                    new java.io.StringReader(
                            new String(bytes, java.nio.charset.StandardCharsets.US_ASCII)));
            return new RuntimeAuthorityConfiguration(properties);
        }
    }

    String jdbcUrl() {
        return "jdbc:sqlserver://localhost;hostNameInCertificate="
                + server
                + ";databaseName="
                + database
                + ";integratedSecurity=true;encrypt=true;trustServerCertificate=false"
                + ";trustManagerClass=br.com.esl.etl.v2.plataforma.autorizacao.PinnedSqlTrustManager"
                + ";loginTimeout=10;socketTimeout=20000";
    }

    @Override
    public String toString() {
        return "RuntimeAuthorityConfiguration[redacted]";
    }
}
