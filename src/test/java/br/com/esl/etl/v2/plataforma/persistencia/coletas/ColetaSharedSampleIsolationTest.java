package br.com.esl.etl.v2.plataforma.persistencia.coletas;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.modulos.coletas.domain.ColetaObservedRootComparator.Binding;
import br.com.esl.etl.v2.modulos.coletas.domain.ColetaObservedRootComparator.ExpectedRoot;
import br.com.esl.etl.v2.modulos.coletas.domain.ColetaStageBatch;
import br.com.esl.etl.v2.plataforma.configuracao.ShadowStorageProperties;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/** JDBC fakes model SQL Server object X versus ordinary DML IX; no physical SQL proof. */
class ColetaSharedSampleIsolationTest {
    private static final String FLAG = "coletas6908.sample.shared.enabled";

    @Test
    void sharedBankCounterRefusesTwoEntitiesEvenIfRuntimeGuardWereBypassed() throws Exception {
        opted(
                () -> {
                    final var fixtureType =
                            Class.forName(
                                    ColetaSampleSaTrialTest.class.getName()
                                            + "$ExistingStagingFake");
                    final var constructor = fixtureType.getDeclaredConstructor();
                    constructor.setAccessible(true);
                    final Object fixture = constructor.newInstance();
                    final UUID run = UUID.randomUUID();
                    final var expectedRun = fixtureType.getDeclaredField("expectedRun");
                    expectedRun.setAccessible(true);
                    expectedRun.set(fixture, run);
                    final var counts = fixtureType.getDeclaredField("entityCounts");
                    counts.setAccessible(true);
                    counts.set(fixture, new long[] {2});
                    final var original =
                            (ColetaTemporalLaboratorySession)
                                    fixtureCall(fixture, "sampleSession", new Class<?>[] {});
                    final var physical =
                            ColetaTemporalLaboratorySession.class.getDeclaredField("physical");
                    physical.setAccessible(true);
                    final var fake = new Fake();
                    fake.adapterDelegate = (Connection) physical.get(original);
                    final var failure =
                            assertThrows(
                                    SQLException.class,
                                    () ->
                                            execute(
                                                    fake,
                                                    trial -> {
                                                        trial.stage(
                                                                (ColetaStageBatch)
                                                                        fixtureCall(
                                                                                fixture,
                                                                                "batch",
                                                                                new Class<?>[] {
                                                                                    UUID.class,
                                                                                    int.class,
                                                                                    int.class
                                                                                },
                                                                                run,
                                                                                1,
                                                                                1),
                                                                CancellationToken.none());
                                                        throw new AssertionError(
                                                                "TWO_ENTITIES_CANNOT_REACH_READBACK");
                                                    }));
                    assertEquals("COL_SAMPLE_SA_ENTITY_LIMIT", failure.getMessage());
                    fixtureCall(fixture, "assertRolledBack", new Class<?>[] {int.class}, 1);
                    assertTrue(fake.locks.isEmpty());
                });
    }

    @Test
    void stagingReadbackUsesSameProtectedPhysicalSessionUntilRollback() throws Exception {
        opted(
                () -> {
                    final var fixtureType =
                            Class.forName(
                                    ColetaSampleSaTrialTest.class.getName()
                                            + "$ExistingStagingFake");
                    final var constructor = fixtureType.getDeclaredConstructor();
                    constructor.setAccessible(true);
                    final Object fixture = constructor.newInstance();
                    final UUID run = UUID.randomUUID();
                    final var expectedRun = fixtureType.getDeclaredField("expectedRun");
                    expectedRun.setAccessible(true);
                    expectedRun.set(fixture, run);
                    fixtureCall(
                            fixture,
                            "set",
                            new Class<?>[] {String.class, Object.class},
                            "verificationRun",
                            run);
                    final var original =
                            (ColetaTemporalLaboratorySession)
                                    fixtureCall(fixture, "sampleSession", new Class<?>[] {});
                    final var physical =
                            ColetaTemporalLaboratorySession.class.getDeclaredField("physical");
                    physical.setAccessible(true);
                    final var fake = new Fake();
                    fake.adapterDelegate = (Connection) physical.get(original);
                    execute(
                            fake,
                            trial -> {
                                assertEquals(11, fake.locks.size());
                                trial.stage(
                                        (ColetaStageBatch)
                                                fixtureCall(
                                                        fixture,
                                                        "batch",
                                                        new Class<?>[] {
                                                            UUID.class, int.class, int.class
                                                        },
                                                        run,
                                                        1,
                                                        1),
                                        CancellationToken.none());
                                final var result =
                                        trial.verifyObservedSample(
                                                (Binding)
                                                        fixtureCall(
                                                                fixture,
                                                                "helper",
                                                                new Class<?>[] {
                                                                    String.class,
                                                                    Class[].class,
                                                                    Object[].class
                                                                },
                                                                "sampleBinding",
                                                                new Class<?>[] {UUID.class},
                                                                new Object[] {run}),
                                                List.of(
                                                        (ExpectedRoot)
                                                                fixtureCall(
                                                                        fixture,
                                                                        "helper",
                                                                        new Class<?>[] {
                                                                            String.class,
                                                                            Class[].class,
                                                                            Object[].class
                                                                        },
                                                                        "expectedRoot",
                                                                        new Class<?>[] {},
                                                                        new Object[] {})),
                                                Map.of(1, 1),
                                                CancellationToken.none());
                                assertTrue(result.matches());
                                assertEquals(1, result.observedPhysicalRows());
                                assertFalse(result.windowCompletenessProven());
                                assertEquals(11, fake.locks.size());
                                assertFalse(fake.ordinaryWriterCanWrite("stg.execution_record"));
                                trial.checkpoint();
                                return null;
                            });
                    fixtureCall(fixture, "assertRolledBack", new Class<?>[] {int.class}, 1);
                    assertTrue(fake.locks.isEmpty());
                    assertEquals(1, fake.opens);
                    assertEquals(1, fake.closes);
                });
    }

    @Test
    void expiredSetupCannotStartTransactionAcquireLocksOrDispatch() throws Exception {
        opted(
                () -> {
                    final var fake = new Fake();
                    final var session = open(fake);
                    final var started =
                            ColetaTemporalLaboratorySession.class.getDeclaredField(
                                    "sharedAdmissionStartedNanos");
                    started.setAccessible(true);
                    started.setLong(session, System.nanoTime() - TimeUnit.SECONDS.toNanos(61));
                    final var failure =
                            assertThrows(
                                    SQLException.class,
                                    () ->
                                            ColetaShadowRollbackTrial.execute(
                                                    session,
                                                    trial -> {
                                                        fake.handler = true;
                                                        return null;
                                                    }));
                    assertEquals("COL_SAMPLE_SHARED_ADMISSION_DEADLINE", failure.getMessage());
                    assertEquals(0, fake.depth);
                    assertEquals(0, fake.lockAttempts);
                    assertFalse(fake.handler);
                    assertEquals(1, fake.rollbacks);
                    assertEquals(1, fake.closes);
                });
    }

    private static Object fixtureCall(
            final Object fixture,
            final String name,
            final Class<?>[] types,
            final Object... arguments)
            throws ReflectiveOperationException {
        final var method = fixture.getClass().getDeclaredMethod(name, types);
        method.setAccessible(true);
        return method.invoke(fixture, arguments);
    }

    @Test
    void existingOrdinaryWriterRefusesAdmissionBeforeWorkAndReleasesResources() throws Exception {
        opted(
                () -> {
                    final var fake = new Fake();
                    fake.writer = "ctl.execution_attempt";
                    final var failure =
                            assertThrows(
                                    SQLException.class,
                                    () ->
                                            execute(
                                                    fake,
                                                    trial -> {
                                                        fake.handler = true;
                                                        return null;
                                                    }));
                    assertEquals(1222, failure.getErrorCode());
                    assertFalse(fake.handler);
                    assertEquals(1, fake.lockAttempts);
                    assertEquals(1, fake.rollbacks);
                    assertEquals(1, fake.closes);
                    assertTrue(fake.locks.isEmpty());
                });
    }

    @Test
    void writerRacingBeforeFinalLockIsRefusedWithoutDispatchOrRetry() throws Exception {
        opted(
                () -> {
                    final var fake = new Fake();
                    fake.racingWriter = true;
                    assertThrows(
                            SQLException.class,
                            () ->
                                    execute(
                                            fake,
                                            trial -> {
                                                fake.handler = true;
                                                return null;
                                            }));
                    assertFalse(fake.handler);
                    assertEquals(11, fake.lockAttempts);
                    assertEquals(1, fake.opens);
                    assertTrue(fake.locks.isEmpty());
                    assertEquals(1, fake.closes);
                });
    }

    @Test
    void emptyFootprintIsProtectedBeforeWorkAndNoncooperativeDmlCannotRaceReadback()
            throws Exception {
        opted(
                () -> {
                    final var fake = new Fake();
                    execute(
                            fake,
                            trial -> {
                                fake.handler = true;
                                assertEquals(11, fake.locks.size());
                                assertEquals(0, fake.lockTimeout);
                                assertEquals("ctl.execution_attempt", fake.locks.get(0));
                                assertEquals("stg.execution_record", fake.locks.get(10));
                                for (final String object : List.copyOf(fake.locks)) {
                                    assertFalse(fake.ordinaryWriterCanWrite(object));
                                }
                                assertTrue(
                                        fake.ordinaryWriterCanWrite("OTHER_DATABASE.same_table"));
                                trial.checkpoint();
                                try (var loan = trial.controlPlaneDataSource().getConnection()) {
                                    loan.commit();
                                    assertThrows(
                                            SQLException.class, () -> loan.setAutoCommit(true));
                                    assertThrows(
                                            SQLException.class,
                                            () -> loan.setCatalog("ETL_SISTEMA"));
                                    assertThrows(
                                            SQLException.class,
                                            () -> loan.unwrap(Connection.class));
                                }
                                assertThrows(
                                        IllegalStateException.class,
                                        () -> trial.prepare(null, CancellationToken.none()));
                                return null;
                            });
                    assertTrue(fake.handler);
                    assertEquals(0, fake.commits);
                    assertEquals(2, fake.rollbacks);
                    assertEquals(1, fake.closes);
                    assertTrue(fake.ordinaryWriterCanWrite("stg.execution_record"));
                });
    }

    @Test
    void sameDepthNewTransactionAndLostLockCannotPassCheckpoint() throws Exception {
        opted(
                () -> {
                    for (final boolean replaceTransaction : List.of(false, true)) {
                        final var fake = new Fake();
                        final var failure =
                                assertThrows(
                                        SQLException.class,
                                        () ->
                                                execute(
                                                        fake,
                                                        trial -> {
                                                            if (replaceTransaction) {
                                                                fake.transactionId++;
                                                            } else {
                                                                fake.locks.remove(0);
                                                            }
                                                            trial.checkpoint();
                                                            return null;
                                                        }));
                        assertEquals("COL_SHADOW_TRANSACTION_DRIFT", failure.getMessage());
                        assertEquals(2, fake.rollbacks);
                        assertEquals(1, fake.closes);
                    }
                });
    }

    @Test
    void unreadableEncryptionRefusesBeforeTransactionAndLocks() throws Exception {
        opted(
                () -> {
                    final var fake = new Fake();
                    fake.encrypted = false;
                    assertThrows(SQLException.class, () -> execute(fake, trial -> null));
                    assertEquals(0, fake.settings);
                    assertEquals(0, fake.lockAttempts);
                    assertEquals(1, fake.closes);
                });
    }

    @Test
    void sharedFlagRefusalDoesNotReadSecretOrConnect() throws Exception {
        opted(
                () -> {
                    System.clearProperty(FLAG);
                    final var fake = new Fake();
                    assertThrows(IllegalStateException.class, () -> open(fake));
                    assertEquals(0, fake.secretReads);
                    assertEquals(0, fake.opens);
                });
    }

    @Test
    void workOrRollbackFailureStillClosesWithoutPhysicalCommit() throws Exception {
        opted(
                () -> {
                    final var fake = new Fake();
                    fake.failRollback = true;
                    final var failure =
                            assertThrows(
                                    IllegalStateException.class,
                                    () ->
                                            execute(
                                                    fake,
                                                    trial -> {
                                                        throw new IllegalStateException(
                                                                "SYNTHETIC_WORK_FAILURE");
                                                    }));
                    assertEquals("SYNTHETIC_WORK_FAILURE", failure.getMessage());
                    assertTrue(failure.getSuppressed().length > 0);
                    assertEquals(1, fake.closes);
                    assertEquals(0, fake.commits);
                });
    }

    private static void opted(final ColetaSampleSaSessionTest.Action action) throws Exception {
        final String previous = System.getProperty(FLAG);
        System.setProperty(FLAG, "true");
        try {
            ColetaSampleSaSessionTest.opted(action);
        } finally {
            if (previous == null) {
                System.clearProperty(FLAG);
            } else {
                System.setProperty(FLAG, previous);
            }
        }
    }

    private static <T> T execute(final Fake fake, final ColetaShadowRollbackTrial.Work<T> work)
            throws Exception {
        return ColetaShadowRollbackTrial.execute(open(fake), work);
    }

    private static ColetaTemporalLaboratorySession open(final Fake fake) throws Exception {
        final var target = ShadowStorageProperties.enabledSampleSa(ColetaSampleSaSessionTest.URL);
        final br.com.esl.etl.v2.plataforma.configuracao.SecretProvider secrets =
                key -> {
                    fake.secretReads++;
                    return ColetaSampleSaSessionTest.FAKE;
                };
        return ColetaTemporalLaboratorySession.openSharedSampleSa(
                target,
                secrets,
                (url, properties) -> {
                    assertEquals("0", properties.getProperty("connectRetryCount"));
                    assertTrue(url.contains("encrypt=true;trustServerCertificate=false"));
                    fake.opens++;
                    return fake.connection();
                });
    }

    private static final class Fake {
        Connection adapterDelegate;
        final List<String> locks = new ArrayList<>();
        String writer;
        boolean racingWriter;
        boolean encrypted = true;
        boolean handler;
        boolean failRollback;
        int lockTimeout = 1800;
        int opens;
        int secretReads;
        int settings;
        int lockAttempts;
        int commits;
        int rollbacks;
        int closes;
        int depth;
        long transactionId = 100;

        boolean ordinaryWriterCanWrite(final String object) {
            return !locks.contains(object);
        }

        Connection connection() {
            return (Connection)
                    Proxy.newProxyInstance(
                            Connection.class.getClassLoader(),
                            new Class<?>[] {Connection.class},
                            (proxy, method, args) ->
                                    switch (method.getName()) {
                                        case "createStatement" -> statement(null);
                                        case "prepareStatement" -> {
                                            final String sql = (String) args[0];
                                            yield adapterDelegate == null
                                                            || sql.contains("TABLOCKX")
                                                            || sql.contains(
                                                                    "sys.dm_tran_current_transaction")
                                                    ? statement(sql)
                                                    : delegated(method, args);
                                        }
                                        case "rollback" -> {
                                            rollbacks++;
                                            locks.clear();
                                            if (adapterDelegate != null) {
                                                delegated(method, args);
                                            }
                                            if (failRollback) {
                                                throw new SQLException("SYNTHETIC_ROLLBACK");
                                            }
                                            yield null;
                                        }
                                        case "close" -> {
                                            closes++;
                                            if (adapterDelegate != null) {
                                                delegated(method, args);
                                            }
                                            yield null;
                                        }
                                        case "commit" -> {
                                            commits++;
                                            yield null;
                                        }
                                        default ->
                                                adapterDelegate == null
                                                        ? value(method.getReturnType())
                                                        : delegated(method, args);
                                    });
        }

        private Object delegated(final Method method, final Object[] arguments) throws Throwable {
            try {
                return method.invoke(adapterDelegate, arguments);
            } catch (final InvocationTargetException failure) {
                throw failure.getCause();
            }
        }

        Statement statement(final String preparedSql) {
            return (Statement)
                    Proxy.newProxyInstance(
                            Statement.class.getClassLoader(),
                            new Class<?>[] {
                                preparedSql == null ? Statement.class : PreparedStatement.class
                            },
                            (proxy, method, args) ->
                                    switch (method.getName()) {
                                        case "execute" -> {
                                            final String sql = (String) args[0];
                                            if (sql.startsWith("BEGIN")) {
                                                depth = 2;
                                            } else if (sql.startsWith("SET")) {
                                                settings++;
                                                lockTimeout =
                                                        sql.contains("LOCK_TIMEOUT 0;") ? 0 : 1800;
                                            }
                                            yield true;
                                        }
                                        case "executeQuery" -> {
                                            final String sql =
                                                    preparedSql == null
                                                            ? (String) args[0]
                                                            : preparedSql;
                                            if (sql.contains("TABLOCKX")) {
                                                assertEquals(0, lockTimeout);
                                                assertTrue(sql.contains("HOLDLOCK"));
                                                assertFalse(sql.contains("NOLOCK"));
                                                final String object =
                                                        sql.substring(
                                                                sql.indexOf(" FROM ") + 6,
                                                                sql.indexOf(" WITH "));
                                                lockAttempts++;
                                                if (racingWriter && lockAttempts == 1) {
                                                    writer = "stg.execution_record";
                                                }
                                                if (object.equals(writer)) {
                                                    throw new SQLException(
                                                            "SYNTHETIC_SENSITIVE_DRIVER_TEXT",
                                                            "HYT00",
                                                            1222);
                                                }
                                                locks.add(object);
                                                yield rows(false, true);
                                            }
                                            yield rows(sql.contains("CASE WHEN DB_NAME"), false);
                                        }
                                        default -> value(method.getReturnType());
                                    });
        }

        ResultSet rows(final boolean identity, final boolean empty) {
            final int[] reads = {0};
            return (ResultSet)
                    Proxy.newProxyInstance(
                            ResultSet.class.getClassLoader(),
                            new Class<?>[] {ResultSet.class},
                            (proxy, method, args) ->
                                    switch (method.getName()) {
                                        case "next" -> !empty && ++reads[0] == 1;
                                        case "getString" -> "127.0.0.1";
                                        case "getLong" -> transactionId;
                                        case "getInt" ->
                                                identity
                                                        ? ((int) args[0] == 4 && !encrypted ? 0 : 1)
                                                        : switch ((int) args[0]) {
                                                            case 1 -> depth;
                                                            case 2 -> 1;
                                                            case 3 -> 17;
                                                            case 5 -> locks.size();
                                                            default -> 0;
                                                        };
                                        default -> value(method.getReturnType());
                                    });
        }

        private static Object value(final Class<?> type) {
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
