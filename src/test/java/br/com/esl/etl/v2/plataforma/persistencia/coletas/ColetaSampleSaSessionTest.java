package br.com.esl.etl.v2.plataforma.persistencia.coletas;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.plataforma.configuracao.SecretKey;
import br.com.esl.etl.v2.plataforma.configuracao.ShadowStorageProperties;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ColetaSampleSaSessionTest {
    static final String URL =
            "jdbc:sqlserver://localhost;databaseName=ETL_SISTEMA_V2_SHADOW"
                    + ";integratedSecurity=false;encrypt=true;trustServerCertificate=false";
    static final String FAKE = "  synthetic-only-password \t ";
    private static final List<String> FLAGS =
            List.of(
                    "shadow.local.integration.enabled",
                    "shadow.local.integration.profile.active",
                    "coletas6908.sample.enabled",
                    "coletas6908.sample.sa.enabled");

    static void opted(final Action action) throws Exception {
        final var previous = new Properties();
        for (final String flag : FLAGS) {
            if (System.getProperty(flag) != null) {
                previous.setProperty(flag, System.getProperty(flag));
            }
            System.setProperty(flag, "true");
        }
        try {
            action.run();
        } finally {
            for (final String flag : FLAGS) {
                if (previous.containsKey(flag)) {
                    System.setProperty(flag, previous.getProperty(flag));
                } else {
                    System.clearProperty(flag);
                }
            }
        }
    }

    @FunctionalInterface
    interface Action {
        void run() throws Exception;
    }

    @Test
    void allFourFlagsAndTargetAreCheckedBeforeSecretAndConnector() throws Exception {
        opted(
                () -> {
                    final var reads = new AtomicInteger();
                    final var probe = new Probe();
                    for (final String flag : FLAGS) {
                        System.clearProperty(flag);
                        assertThrows(
                                IllegalStateException.class,
                                () ->
                                        ColetaTemporalLaboratorySession.openSampleSa(
                                                ShadowStorageProperties.enabledSampleSa(URL),
                                                key -> {
                                                    reads.incrementAndGet();
                                                    return FAKE;
                                                },
                                                probe::open));
                        System.setProperty(flag, "true");
                    }
                    assertThrows(
                            IllegalArgumentException.class,
                            () ->
                                    ColetaTemporalLaboratorySession.openSampleSa(
                                            ShadowStorageProperties.disabled(),
                                            key -> {
                                                reads.incrementAndGet();
                                                return FAKE;
                                            },
                                            probe::open));
                    assertEquals(0, reads.get());
                    assertEquals(0, probe.opens);
                });
    }

    @Test
    void opensOneFakeSqlSaConnectionWithExactPasswordAndClearsOfferedProperties() throws Exception {
        opted(
                () -> {
                    final var probe = new Probe();
                    try (var session =
                            ColetaTemporalLaboratorySession.openSampleSa(
                                    ShadowStorageProperties.enabledSampleSa(URL),
                                    key -> {
                                        assertEquals(SecretKey.SHADOW_SAMPLE_SA_PASSWORD, key);
                                        return FAKE;
                                    },
                                    probe::open)) {
                        assertTrue(session.sampleSaOnly());
                        assertEquals(1, probe.opens);
                        assertTrue(probe.offered.isEmpty());
                        assertFalse(probe.url.contains(FAKE));
                        assertTrue(
                                probe.url.endsWith(
                                        ";loginTimeout=5;queryTimeout=20;cancelQueryTimeout=5;socketTimeout=30000"));
                        assertFalse(probe.autoCommit);
                        try (var loan = session.getConnection()) {
                            loan.commit();
                            assertEquals(1, session.suppressedCommits());
                            assertThrows(SQLException.class, () -> loan.setAutoCommit(true));
                            assertThrows(SQLException.class, () -> loan.setCatalog("ETL_SISTEMA"));
                        }
                        assertEquals(0, probe.commits);
                    }
                    assertEquals(List.of("rollback", "close"), probe.lifecycle);
                });
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "localhost",
                "127.0.0.999",
                "127.1",
                "192.0.2.1",
                "::ffff:192.0.2.1",
                ":::1",
                "::1%zone",
                "::gggg",
                " 127.0.0.1 "
            })
    void refusesHostMalformedAndNonLoopbackPeersBeforeSessionSettings(final String peer)
            throws Exception {
        opted(
                () -> {
                    final var probe = new Probe();
                    probe.localPeer = peer;
                    assertThrows(
                            SQLException.class,
                            () ->
                                    ColetaTemporalLaboratorySession.openSampleSa(
                                            ShadowStorageProperties.enabledSampleSa(URL),
                                            ignored -> FAKE,
                                            probe::open));
                    assertEquals(0, probe.settings);
                    assertTrue(probe.offered.isEmpty());
                    assertEquals(List.of("rollback", "close"), probe.lifecycle);
                });
    }

    @ParameterizedTest
    @ValueSource(
            strings = {"127.0.0.1", "::1", "0:0:0:0:0:0:0:1", "::ffff:127.0.0.1", "::FFFF:7F00:1"})
    void acceptsRecognizedLiteralLoopbackWithoutResolver(final String peer) throws Exception {
        opted(
                () -> {
                    final var probe = new Probe();
                    probe.localPeer = peer;
                    probe.clientPeer = peer;
                    try (var session =
                            ColetaTemporalLaboratorySession.openSampleSa(
                                    ShadowStorageProperties.enabledSampleSa(URL),
                                    ignored -> FAKE,
                                    probe::open)) {
                        assertTrue(session.sampleSaOnly());
                    }
                });
    }

    @Test
    void identityAuthDatabaseLoginTransactionOrExtraRowRefusalClosesBeforeHandler()
            throws Exception {
        opted(
                () -> {
                    for (final String defect : List.of("identity", "client", "extra")) {
                        final var probe = new Probe();
                        probe.identity = defect.equals("identity") ? 0 : 1;
                        probe.clientPeer = defect.equals("client") ? "192.0.2.2" : "127.0.0.1";
                        probe.extraRow = defect.equals("extra");
                        assertThrows(
                                SQLException.class,
                                () ->
                                        ColetaTemporalLaboratorySession.openSampleSa(
                                                ShadowStorageProperties.enabledSampleSa(URL),
                                                ignored -> FAKE,
                                                probe::open));
                        assertEquals(0, probe.settings);
                        assertEquals(List.of("rollback", "close"), probe.lifecycle);
                    }
                });
    }

    @Test
    void providerDriverAndCleanupFailuresCannotExposeSyntheticCredential() throws Exception {
        opted(
                () -> {
                    final var probe = new Probe();
                    final var missing =
                            assertThrows(
                                    IllegalStateException.class,
                                    () ->
                                            ColetaTemporalLaboratorySession.openSampleSa(
                                                    ShadowStorageProperties.enabledSampleSa(URL),
                                                    key -> {
                                                        throw new IllegalStateException(FAKE);
                                                    },
                                                    probe::open));
                    assertNull(missing.getCause());
                    assertFalse(missing.toString().contains(FAKE));
                    assertEquals(0, probe.opens);
                    final var driver =
                            assertThrows(
                                    SQLException.class,
                                    () ->
                                            ColetaTemporalLaboratorySession.openSampleSa(
                                                    ShadowStorageProperties.enabledSampleSa(URL),
                                                    key -> FAKE,
                                                    (url, properties) -> {
                                                        probe.offered = properties;
                                                        throw new SQLException(
                                                                FAKE, "28000", 18456);
                                                    }));
                    assertNull(driver.getCause());
                    assertFalse(driver.toString().contains(FAKE));
                    assertEquals("28000", driver.getSQLState());
                    assertEquals(18456, driver.getErrorCode());
                    assertTrue(probe.offered.isEmpty());
                    probe.identity = 0;
                    probe.failCleanup = true;
                    final var cleanup =
                            assertThrows(
                                    SQLException.class,
                                    () ->
                                            ColetaTemporalLaboratorySession.openSampleSa(
                                                    ShadowStorageProperties.enabledSampleSa(URL),
                                                    key -> FAKE,
                                                    probe::open));
                    assertEquals(2, cleanup.getSuppressed().length);
                    for (final var failure : cleanup.getSuppressed()) {
                        assertFalse(failure.toString().contains(FAKE));
                        assertNull(failure.getCause());
                    }
                });
    }

    static final class Probe {
        String url;
        Properties offered;
        int opens;
        int commits;
        int settings;
        int identity = 1;
        int depth;
        boolean autoCommit = true;
        boolean extraRow;
        boolean failCleanup;
        String localPeer = "127.0.0.1";
        String clientPeer = "127.0.0.1";
        final List<String> lifecycle = new ArrayList<>();

        Connection open(final String value, final Properties properties) {
            opens++;
            url = value;
            offered = properties;
            assertEquals("sa", properties.getProperty("user"));
            assertEquals(FAKE, properties.getProperty("password"));
            assertEquals("SqlPassword", properties.getProperty("authentication"));
            assertEquals("0", properties.getProperty("connectRetryCount"));
            return (Connection)
                    Proxy.newProxyInstance(
                            Connection.class.getClassLoader(),
                            new Class<?>[] {Connection.class},
                            (proxy, method, args) ->
                                    switch (method.getName()) {
                                        case "setAutoCommit" -> {
                                            autoCommit = (boolean) args[0];
                                            yield null;
                                        }
                                        case "getAutoCommit" -> autoCommit;
                                        case "createStatement" -> statement();
                                        case "prepareStatement" -> transactionStatement();
                                        case "commit" -> {
                                            commits++;
                                            yield null;
                                        }
                                        case "rollback", "close" -> {
                                            lifecycle.add(method.getName());
                                            if (failCleanup) {
                                                throw new SQLException(FAKE);
                                            }
                                            yield null;
                                        }
                                        default -> defaultValue(method.getReturnType());
                                    });
        }

        private Statement statement() {
            return (Statement)
                    Proxy.newProxyInstance(
                            Statement.class.getClassLoader(),
                            new Class<?>[] {Statement.class},
                            (proxy, method, args) ->
                                    switch (method.getName()) {
                                        case "executeQuery" ->
                                                rows(
                                                        ((String) args[0])
                                                                .contains("CASE WHEN DB_NAME"));
                                        case "execute" -> {
                                            if (((String) args[0]).startsWith("BEGIN")) {
                                                depth = 2;
                                            } else {
                                                settings++;
                                            }
                                            yield true;
                                        }
                                        default -> defaultValue(method.getReturnType());
                                    });
        }

        private PreparedStatement transactionStatement() {
            return (PreparedStatement)
                    Proxy.newProxyInstance(
                            PreparedStatement.class.getClassLoader(),
                            new Class<?>[] {PreparedStatement.class},
                            (proxy, method, args) ->
                                    method.getName().equals("executeQuery")
                                            ? rows(false)
                                            : defaultValue(method.getReturnType()));
        }

        private ResultSet rows(final boolean identityQuery) {
            final int[] reads = {0};
            return (ResultSet)
                    Proxy.newProxyInstance(
                            ResultSet.class.getClassLoader(),
                            new Class<?>[] {ResultSet.class},
                            (proxy, method, args) ->
                                    switch (method.getName()) {
                                        case "next" ->
                                                ++reads[0] <= (identityQuery && extraRow ? 2 : 1);
                                        case "getInt" ->
                                                identityQuery
                                                        ? identity
                                                        : ((int) args[0] == 1
                                                                ? depth
                                                                : (int) args[0] == 2 ? 1 : 17);
                                        case "getString" ->
                                                (int) args[0] == 2 ? localPeer : clientPeer;
                                        default -> defaultValue(method.getReturnType());
                                    });
        }

        private static Object defaultValue(final Class<?> type) {
            if (type == boolean.class) {
                return false;
            }
            if (type == int.class) {
                return 0;
            }
            if (type == long.class) {
                return 0L;
            }
            return null;
        }
    }
}
