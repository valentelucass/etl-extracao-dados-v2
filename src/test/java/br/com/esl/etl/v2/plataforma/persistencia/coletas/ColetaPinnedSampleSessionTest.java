package br.com.esl.etl.v2.plataforma.persistencia.coletas;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.plataforma.configuracao.PinnedSampleTlsScope;
import br.com.esl.etl.v2.plataforma.configuracao.ShadowStorageProperties;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ColetaPinnedSampleSessionTest {
    private static final String PIN = "coletas6908.sample.sql-pin.enabled";
    private static final String SHARED = "coletas6908.sample.shared.enabled";

    private static void opted(final ColetaSampleSaSessionTest.Action action) throws Exception {
        final String previousPin = System.getProperty(PIN);
        final String previousShared = System.getProperty(SHARED);
        System.setProperty(PIN, "true");
        System.setProperty(SHARED, "true");
        try {
            ColetaSampleSaSessionTest.opted(action);
        } finally {
            if (previousPin == null) {
                System.clearProperty(PIN);
            } else {
                System.setProperty(PIN, previousPin);
            }
            if (previousShared == null) {
                System.clearProperty(SHARED);
            } else {
                System.setProperty(SHARED, previousShared);
            }
        }
    }

    private static ShadowStorageProperties target() {
        return ShadowStorageProperties.enabledSampleSqlPin(
                ColetaSampleSaSessionTest.URL, PinnedSampleTlsScope.APPROVAL_REFERENCE);
    }

    @Test
    void connectorPureAdmissionRejectsGlobalOverridesBeforeSecret() throws Exception {
        opted(
                () -> {
                    final var reads = new AtomicInteger();
                    for (final String name :
                            new String[] {
                                "javax.net.ssl.trustStore",
                                "javax.net.ssl.trustStoreType",
                                "javax.net.ssl.trustStorePassword",
                                "javax.net.ssl.trustStoreProvider",
                                "javax.net.ssl.keyStore",
                                "javax.net.ssl.keyStoreType",
                                "javax.net.ssl.keyStorePassword",
                                "javax.net.ssl.keyStoreProvider",
                                "javax.net.debug",
                                "java.security.properties"
                            }) {
                        final String previous = System.getProperty(name);
                        System.setProperty(name, "");
                        try {
                            assertThrows(
                                    IllegalStateException.class,
                                    () ->
                                            PinnedSampleSaJdbcConnector.open(
                                                    target(),
                                                    key -> {
                                                        reads.incrementAndGet();
                                                        return "synthetic-only";
                                                    }));
                        } finally {
                            if (previous == null) {
                                System.clearProperty(name);
                            } else {
                                System.setProperty(name, previous);
                            }
                        }
                    }
                    assertEquals(0, reads.get());
                });
    }

    @Test
    void pinnedOptinsAndProfileAreCheckedBeforeSecretAndConnector() throws Exception {
        opted(
                () -> {
                    final var secrets = new AtomicInteger();
                    final var connections = new AtomicInteger();
                    for (final String flag :
                            new String[] {
                                PIN,
                                SHARED,
                                "shadow.local.integration.enabled",
                                "shadow.local.integration.profile.active",
                                "coletas6908.sample.enabled",
                                "coletas6908.sample.sa.enabled"
                            }) {
                        System.clearProperty(flag);
                        assertThrows(
                                IllegalStateException.class,
                                () ->
                                        ColetaTemporalLaboratorySession.openPinnedSharedSampleSa(
                                                target(),
                                                key -> {
                                                    secrets.incrementAndGet();
                                                    return ColetaSampleSaSessionTest.FAKE;
                                                },
                                                (url, p) -> {
                                                    connections.incrementAndGet();
                                                    return null;
                                                }));
                        System.setProperty(flag, "true");
                    }
                    assertThrows(
                            IllegalArgumentException.class,
                            () ->
                                    ColetaTemporalLaboratorySession.openPinnedSharedSampleSa(
                                            ShadowStorageProperties.enabledSampleSa(
                                                    ColetaSampleSaSessionTest.URL),
                                            key -> {
                                                secrets.incrementAndGet();
                                                return ColetaSampleSaSessionTest.FAKE;
                                            },
                                            (url, p) -> {
                                                connections.incrementAndGet();
                                                return null;
                                            }));
                    assertThrows(
                            IllegalArgumentException.class,
                            () ->
                                    ColetaTemporalLaboratorySession.openSharedSampleSa(
                                            target(),
                                            key -> {
                                                secrets.incrementAndGet();
                                                return ColetaSampleSaSessionTest.FAKE;
                                            },
                                            (url, p) -> {
                                                connections.incrementAndGet();
                                                return null;
                                            }));
                    assertEquals(0, secrets.get());
                    assertEquals(0, connections.get());
                });
    }

    static final class ExistingSharedFake {
        final Class<?> type;
        final Object value;

        ExistingSharedFake() throws Exception {
            type = Class.forName(ColetaSharedSampleIsolationTest.class.getName() + "$Fake");
            final var constructor = type.getDeclaredConstructor();
            constructor.setAccessible(true);
            value = constructor.newInstance();
        }

        Connection connection() throws Exception {
            final var method = type.getDeclaredMethod("connection");
            method.setAccessible(true);
            return (Connection) method.invoke(value);
        }

        int count(final String field) throws Exception {
            final var f = type.getDeclaredField(field);
            f.setAccessible(true);
            return f.getInt(value);
        }

        void set(final String field, final Object data) throws Exception {
            final var f = type.getDeclaredField(field);
            f.setAccessible(true);
            f.set(value, data);
        }
    }

    private static ColetaTemporalLaboratorySession open(final ExistingSharedFake fake)
            throws Exception {
        final Connection physical = fake.connection();
        return ColetaTemporalLaboratorySession.openPinnedSharedSampleSa(
                target(),
                ignored -> ColetaSampleSaSessionTest.FAKE,
                (url, credentials) -> {
                    assertEquals(
                            ColetaSampleSaSessionTest.FAKE, credentials.getProperty("password"));
                    assertEquals("sa", credentials.getProperty("user"));
                    return physical;
                });
    }

    @Test
    void pinnedEntryRetainsObjectLockIsolationCommitBlockPromotionRefusalAndRollback()
            throws Exception {
        opted(
                () -> {
                    final var fake = new ExistingSharedFake();
                    final var session = open(fake);
                    final var failure =
                            assertThrows(
                                    IllegalStateException.class,
                                    () ->
                                            ColetaShadowRollbackTrial.execute(
                                                    session,
                                                    trial -> {
                                                        try (var loan =
                                                                trial.controlPlaneDataSource()
                                                                        .getConnection()) {
                                                            loan.commit();
                                                            assertThrows(
                                                                    SQLException.class,
                                                                    () -> loan.setAutoCommit(true));
                                                            assertThrows(
                                                                    SQLException.class,
                                                                    () ->
                                                                            loan.setCatalog(
                                                                                    "ETL_SISTEMA"));
                                                        }
                                                        assertEquals(
                                                                ColetaSharedSampleIsolation.OBJECTS,
                                                                fake.count("lockAttempts"));
                                                        return trial.promotedExecutionId();
                                                    }));
                    assertEquals("COL_SAMPLE_SA_PROMOTION_FORBIDDEN", failure.getMessage());
                    assertEquals(0, fake.count("commits"));
                    assertTrue(fake.count("rollbacks") >= 1);
                    assertEquals(1, fake.count("closes"));
                });
    }

    @Test
    void ordinaryShadowWriterRaceAndUnencryptedSessionRefuseWithoutHandler() throws Exception {
        opted(
                () -> {
                    for (final String defect : new String[] {"racingWriter", "encrypted"}) {
                        final var fake = new ExistingSharedFake();
                        fake.set(defect, defect.equals("racingWriter"));
                        final var handlers = new AtomicInteger();
                        assertThrows(
                                SQLException.class,
                                () ->
                                        ColetaShadowRollbackTrial.execute(
                                                open(fake),
                                                trial -> {
                                                    handlers.incrementAndGet();
                                                    return null;
                                                }));
                        assertEquals(0, handlers.get());
                        assertEquals(0, fake.count("commits"));
                        assertTrue(fake.count("rollbacks") >= 1);
                        assertEquals(1, fake.count("closes"));
                    }
                });
    }
}
