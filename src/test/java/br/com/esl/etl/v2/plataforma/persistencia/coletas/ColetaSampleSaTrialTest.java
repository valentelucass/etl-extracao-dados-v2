package br.com.esl.etl.v2.plataforma.persistencia.coletas;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.modulos.coletas.domain.ColetaObservedRootComparator.Binding;
import br.com.esl.etl.v2.modulos.coletas.domain.ColetaObservedRootComparator.ExpectedRoot;
import br.com.esl.etl.v2.modulos.coletas.domain.ColetaStageBatch;
import br.com.esl.etl.v2.modulos.coletas.domain.ColetaStageRecord;
import br.com.esl.etl.v2.plataforma.configuracao.ShadowStorageProperties;
import br.com.esl.etl.v2.plataforma.identidade.ScopedSourceIdentity;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ColetaSampleSaTrialTest {
    @Test
    void saSessionCannotBecomeTraversalPromotionAndAlwaysRollsBack() throws Exception {
        ColetaSampleSaSessionTest.opted(
                () -> {
                    for (final String action : List.of("prepare", "promote", "promotedId")) {
                        final var probe = new ColetaSampleSaSessionTest.Probe();
                        final var session =
                                ColetaTemporalLaboratorySession.openSampleSa(
                                        ShadowStorageProperties.enabledSampleSa(
                                                ColetaSampleSaSessionTest.URL),
                                        key -> ColetaSampleSaSessionTest.FAKE,
                                        probe::open);
                        final var failure =
                                assertThrows(
                                        IllegalStateException.class,
                                        () ->
                                                ColetaShadowRollbackTrial.execute(
                                                        session,
                                                        trial -> {
                                                            trial.checkpoint();
                                                            switch (action) {
                                                                case "prepare" ->
                                                                        trial.prepare(
                                                                                null,
                                                                                CancellationToken
                                                                                        .none());
                                                                case "promote" ->
                                                                        trial.promote(
                                                                                null,
                                                                                null,
                                                                                CancellationToken
                                                                                        .none());
                                                                default ->
                                                                        trial.promotedExecutionId();
                                                            }
                                                            return null;
                                                        }));
                        assertEquals("COL_SAMPLE_SA_PROMOTION_FORBIDDEN", failure.getMessage());
                        assertEquals(0, probe.commits);
                        assertEquals(List.of("rollback", "rollback", "close"), probe.lifecycle);
                    }
                });
    }

    @Test
    void publicSaEntryRefusesMissingOptInBeforeSecretOrWork() {
        final String prior = System.getProperty("coletas6908.sample.sa.enabled");
        try {
            System.clearProperty("coletas6908.sample.sa.enabled");
            assertThrows(
                    IllegalStateException.class,
                    () ->
                            ColetaShadowRollbackTrial.executeSampleSa(
                                    ShadowStorageProperties.enabledSampleSa(
                                            ColetaSampleSaSessionTest.URL),
                                    key -> {
                                        throw new AssertionError("SECRET_NOT_EXPECTED");
                                    },
                                    trial -> {
                                        throw new AssertionError("WORK_NOT_EXPECTED");
                                    }));
        } finally {
            if (prior == null) {
                System.clearProperty("coletas6908.sample.sa.enabled");
            } else {
                System.setProperty("coletas6908.sample.sa.enabled", prior);
            }
        }
    }

    @Test
    void saStagingReadbackRetainsBindingWithoutTerminalPromotionOrPhysicalCommit()
            throws Exception {
        final var fixture = new ExistingStagingFake();
        final UUID run = UUID.randomUUID();
        fixture.expectedRun = run;
        fixture.set("verificationRun", run);
        ColetaShadowRollbackTrial.execute(
                fixture.sampleSession(),
                trial -> {
                    trial.stage(fixture.batch(run, 1, 1), CancellationToken.none());
                    final var result =
                            trial.verifyObservedSample(
                                    (Binding)
                                            fixture.helper(
                                                    "sampleBinding",
                                                    new Class<?>[] {UUID.class},
                                                    run),
                                    List.of(
                                            (ExpectedRoot)
                                                    fixture.helper(
                                                            "expectedRoot", new Class<?>[] {})),
                                    Map.of(1, 1),
                                    CancellationToken.none());
                    assertTrue(result.matches());
                    assertEquals(1, result.observedPhysicalRows());
                    assertEquals(0, result.presenceComparedCells());
                    assertFalse(result.windowCompletenessProven());
                    trial.checkpoint();
                    return null;
                });
        assertFalse(fixture.get("preparedQueries").toString().contains("core."));
        fixture.assertRolledBack(1);
    }

    @Test
    void saRowLimitAccumulatesAcrossMicrobatchesAndStopsBeforeEleventhStage() throws Exception {
        final var fixture = new ExistingStagingFake();
        final UUID run = UUID.randomUUID();
        fixture.expectedRun = run;
        final var failure =
                assertThrows(
                        SQLException.class,
                        () ->
                                ColetaShadowRollbackTrial.execute(
                                        fixture.sampleSession(),
                                        trial -> {
                                            for (int number = 1; number <= 10; number++) {
                                                trial.stage(
                                                        fixture.batch(run, number, 100),
                                                        CancellationToken.none());
                                            }
                                            trial.stage(
                                                    fixture.batch(run, 11, 1),
                                                    CancellationToken.none());
                                            return null;
                                        }));
        assertEquals("COL_SAMPLE_SA_ROW_LIMIT", failure.getMessage());
        fixture.assertRolledBack(10);
    }

    @Test
    void saPageAndEntityLimitsFailClosedAndRetainRollback() throws Exception {
        for (final String defect : List.of("page", "sixEntities", "missingKey")) {
            final var fixture = new ExistingStagingFake();
            fixture.set("auditedPage", defect.equals("page") ? 2 : 1);
            final UUID run = UUID.randomUUID();
            fixture.expectedRun = run;
            fixture.entityCounts = new long[] {defect.equals("sixEntities") ? 6 : 1};
            final var records = new ArrayList<ColetaStageRecord>();
            for (int ordinal = 1; ordinal <= (defect.equals("sixEntities") ? 6 : 1); ordinal++) {
                records.add(
                        ColetaStageRecord.quarantine(
                                ordinal,
                                defect.equals("missingKey")
                                        ? null
                                        : new ScopedSourceIdentity.SourceKey(
                                                ScopedSourceIdentity.WireType.INTEGER,
                                                "INTEGER:" + ordinal),
                                "SYNTHETIC_QUARANTINE"));
            }
            final var batch =
                    new ColetaStageBatch(run, 1, records, Instant.parse("2026-10-03T12:00:00Z"));
            final var failure =
                    assertThrows(
                            SQLException.class,
                            () ->
                                    ColetaShadowRollbackTrial.execute(
                                            fixture.sampleSession(),
                                            trial -> {
                                                trial.stage(batch, CancellationToken.none());
                                                return null;
                                            }));
            assertEquals(
                    switch (defect) {
                        case "page" -> "COL_SAMPLE_SA_SINGLE_PAGE_REQUIRED";
                        case "sixEntities" -> "COL_SAMPLE_SA_ENTITY_LIMIT";
                        default -> "COL_SAMPLE_SA_ENTITY_REQUIRED";
                    },
                    failure.getMessage());
            fixture.assertRolledBack(defect.equals("sixEntities") ? 1 : 0);
            assertEquals(defect.equals("sixEntities") ? 1 : 0, fixture.countQueries);
        }
    }

    @Test
    void sqlCounterIsAuthoritativeForFiveThenSixEntitiesAcrossMicrobatches() throws Exception {
        final var fixture = new ExistingStagingFake();
        final UUID run = UUID.randomUUID();
        fixture.expectedRun = run;
        fixture.entityCounts = new long[] {5, 6};
        final var failure =
                assertThrows(
                        SQLException.class,
                        () ->
                                ColetaShadowRollbackTrial.execute(
                                        fixture.sampleSession(),
                                        trial -> {
                                            trial.stage(
                                                    fixture.batch(run, 1, 1),
                                                    CancellationToken.none());
                                            trial.stage(
                                                    fixture.batch(run, 2, 1),
                                                    CancellationToken.none());
                                            throw new AssertionError(
                                                    "SIX_ENTITIES_MUST_NOT_REACH_READBACK");
                                        }));
        assertEquals("COL_SAMPLE_SA_ENTITY_LIMIT", failure.getMessage());
        assertEquals(2, fixture.countQueries);
        fixture.assertRolledBack(2);
    }

    @Test
    void unobservableNullZeroOrExtraScalarResultCannotReachReadback() throws Exception {
        for (final String defect : List.of("missing", "null", "zero", "extra")) {
            final var fixture = new ExistingStagingFake();
            final UUID run = UUID.randomUUID();
            fixture.expectedRun = run;
            fixture.missingCount = defect.equals("missing");
            fixture.nullCount = defect.equals("null");
            fixture.extraCount = defect.equals("extra");
            fixture.entityCounts = new long[] {defect.equals("zero") ? 0 : 1};
            final var failure =
                    assertThrows(
                            SQLException.class,
                            () ->
                                    ColetaShadowRollbackTrial.execute(
                                            fixture.sampleSession(),
                                            trial -> {
                                                trial.stage(
                                                        fixture.batch(run, 1, 1),
                                                        CancellationToken.none());
                                                throw new AssertionError(
                                                        "INVALID_COUNTER_MUST_NOT_REACH_READBACK");
                                            }));
            assertEquals(
                    defect.equals("missing")
                            ? "COL_SAMPLE_SA_ENTITY_COUNT_UNOBSERVABLE"
                            : "COL_SAMPLE_SA_ENTITY_LIMIT",
                    failure.getMessage());
            assertEquals(1, fixture.countQueries);
            fixture.assertRolledBack(1);
        }
    }

    /** Reuses the established fake JDBC/readback model without changing the Windows fixture. */
    private static final class ExistingStagingFake {
        private final Class<?> type =
                Class.forName(ColetaShadowRollbackTrialTest.class.getName() + "$FakePhysical");
        private final Object jdbc;
        private UUID expectedRun;
        private long[] entityCounts = {1};
        private int countQueries;
        private boolean missingCount;
        private boolean nullCount;
        private boolean extraCount;

        ExistingStagingFake() throws ReflectiveOperationException {
            final var constructor = type.getDeclaredConstructor();
            constructor.setAccessible(true);
            jdbc = constructor.newInstance();
        }

        ColetaTemporalLaboratorySession sampleSession() throws ReflectiveOperationException {
            final var method = type.getDeclaredMethod("session");
            method.setAccessible(true);
            final var original = method.invoke(jdbc);
            final var physical = ColetaTemporalLaboratorySession.class.getDeclaredField("physical");
            physical.setAccessible(true);
            final Connection delegate = (Connection) physical.get(original);
            final Connection samePhysical =
                    (Connection)
                            Proxy.newProxyInstance(
                                    Connection.class.getClassLoader(),
                                    new Class<?>[] {Connection.class},
                                    (proxy, called, arguments) -> {
                                        if (called.getName().equals("prepareStatement")
                                                && ((String) arguments[0])
                                                        .startsWith(
                                                                "SELECT COUNT_BIG(DISTINCT source_key")) {
                                            assertEquals(
                                                    "SELECT COUNT_BIG(DISTINCT source_key COLLATE Latin1_General_100_BIN2)"
                                                            + " FROM stg.execution_record WHERE execution_id=?",
                                                    arguments[0]);
                                            return entityCounter();
                                        }
                                        try {
                                            return called.invoke(delegate, arguments);
                                        } catch (final InvocationTargetException failure) {
                                            throw failure.getCause();
                                        }
                                    });
            final var constructor =
                    ColetaTemporalLaboratorySession.class.getDeclaredConstructor(
                            Connection.class, boolean.class);
            constructor.setAccessible(true);
            return constructor.newInstance(samePhysical, true);
        }

        private PreparedStatement entityCounter() {
            final String[] binding = {null};
            final int[] timeout = {0};
            return (PreparedStatement)
                    Proxy.newProxyInstance(
                            PreparedStatement.class.getClassLoader(),
                            new Class<?>[] {PreparedStatement.class},
                            (proxy, called, arguments) -> {
                                switch (called.getName()) {
                                    case "setString" -> {
                                        assertEquals(1, arguments[0]);
                                        binding[0] = (String) arguments[1];
                                    }
                                    case "setQueryTimeout" -> timeout[0] = (int) arguments[0];
                                    case "executeQuery" -> {
                                        assertEquals(expectedRun.toString(), binding[0]);
                                        assertEquals(10, timeout[0]);
                                        assertTrue((int) get("stagedBatches") > 0);
                                        final long count =
                                                entityCounts[
                                                        Math.min(
                                                                countQueries++,
                                                                entityCounts.length - 1)];
                                        final int[] reads = {0};
                                        return (ResultSet)
                                                Proxy.newProxyInstance(
                                                        ResultSet.class.getClassLoader(),
                                                        new Class<?>[] {ResultSet.class},
                                                        (result, method, values) ->
                                                                switch (method.getName()) {
                                                                    case "next" ->
                                                                            !missingCount
                                                                                    && ++reads[0]
                                                                                            <= (extraCount
                                                                                                    ? 2
                                                                                                    : 1);
                                                                    case "getLong" -> {
                                                                        assertEquals(1, values[0]);
                                                                        yield count;
                                                                    }
                                                                    case "wasNull" -> nullCount;
                                                                    default ->
                                                                            primitiveDefault(
                                                                                    method
                                                                                            .getReturnType());
                                                                });
                                    }
                                    default -> {
                                        return primitiveDefault(called.getReturnType());
                                    }
                                }
                                return primitiveDefault(called.getReturnType());
                            });
        }

        private static Object primitiveDefault(final Class<?> type) {
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

        Object get(final String name) throws ReflectiveOperationException {
            final var field = type.getDeclaredField(name);
            field.setAccessible(true);
            return field.get(jdbc);
        }

        void set(final String name, final Object value) throws ReflectiveOperationException {
            final var field = type.getDeclaredField(name);
            field.setAccessible(true);
            field.set(jdbc, value);
        }

        Object helper(final String name, final Class<?>[] parameters, final Object... values)
                throws ReflectiveOperationException {
            final var method =
                    ColetaShadowRollbackTrialTest.class.getDeclaredMethod(name, parameters);
            method.setAccessible(true);
            return method.invoke(null, values);
        }

        ColetaStageBatch batch(final UUID run, final int number, final int size)
                throws ReflectiveOperationException {
            return (ColetaStageBatch)
                    helper(
                            "batch",
                            new Class<?>[] {UUID.class, int.class, int.class},
                            run,
                            number,
                            size);
        }

        void assertRolledBack(final int staged) throws ReflectiveOperationException {
            assertEquals(staged, get("stagedBatches"));
            assertEquals(0, get("physicalCommits"));
            assertEquals(List.of("rollback", "rollback", "close"), get("lifecycle"));
        }
    }
}
