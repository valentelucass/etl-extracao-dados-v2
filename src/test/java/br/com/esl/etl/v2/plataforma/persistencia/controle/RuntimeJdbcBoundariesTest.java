package br.com.esl.etl.v2.plataforma.persistencia.controle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.plataforma.configuracao.ShadowStorageProperties;
import br.com.esl.etl.v2.plataforma.configuracao.ShadowStorageTargetKind;
import br.com.esl.etl.v2.plataforma.controle.ExecutionMode;
import br.com.esl.etl.v2.plataforma.orquestracao.RuntimeTemporalPlanner;
import br.com.esl.etl.v2.plataforma.orquestracao.RuntimeTemporalPolicy;
import br.com.esl.etl.v2.plataforma.orquestracao.RuntimeWindowStrategy;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

class RuntimeJdbcBoundariesTest {
    private static final Instant START = Instant.parse("2024-02-01T00:00:00Z");
    private static final String HASH = "a".repeat(64);

    private static RuntimeTemporalPolicy policy() {
        return new RuntimeTemporalPolicy(
                "synthetic-v1",
                ZoneId.of("Etc/UTC"),
                ExecutionMode.BACKFILL,
                RuntimeWindowStrategy.INTERVAL,
                RuntimeTemporalPolicy.Cadence.CIVIL_DAY,
                LocalTime.MIDNIGHT,
                Duration.ZERO,
                Duration.ZERO,
                Duration.ofHours(2),
                Duration.ofHours(1),
                1,
                2,
                4,
                0,
                List.of());
    }

    @Test
    void temporalWriteBindsDeterministicOccurrenceAndRequiresCompleteCommitAcknowledgement()
            throws Exception {
        final var jdbc = new Jdbc();
        final var store = new JdbcSqlServerTemporalPlan(jdbc.source());
        final var result =
                new RuntimeTemporalPlanner()
                        .plan(policy(), LocalDate.of(2024, 2, 1), START.plusSeconds(86400));
        final UUID plan = UUID.randomUUID();
        assertEquals(1, store.persist(plan, HASH, policy(), result));
        assertEquals(20, jdbc.timeout);
        assertEquals(policy().fingerprint().sha256(), jdbc.parameters.get(4));
        final String material = jdbc.parameters.get(6).toString();
        assertEquals(1, store.persist(plan, HASH, policy(), result));
        assertEquals(material, jdbc.parameters.get(6));
        assertEquals(2, jdbc.closed);
        assertEquals(
                0,
                store.persist(
                        plan,
                        HASH,
                        policy(),
                        new RuntimeTemporalPlanner.Result(List.of(), false, false)));
        assertEquals(2, jdbc.closed);
        jdbc.ack = true;
        assertThrows(
                IllegalStateException.class, () -> store.persist(plan, HASH, policy(), result));
        jdbc.ack = false;
        jdbc.count = 0;
        assertThrows(
                IllegalStateException.class, () -> store.persist(plan, HASH, policy(), result));
        jdbc.count = 1;
        jdbc.duplicates = true;
        assertThrows(
                IllegalStateException.class, () -> store.persist(plan, HASH, policy(), result));
        jdbc.duplicates = false;
        jdbc.unavailable = true;
        final var failure =
                assertThrows(
                        IllegalStateException.class,
                        () -> store.persist(plan, HASH, policy(), result));
        assertTrue(failure.getCause() instanceof SQLException);
        assertThrows(
                IllegalArgumentException.class,
                () -> store.persist(plan, "invalid", policy(), result));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        store.persist(
                                plan,
                                HASH,
                                policy(),
                                new RuntimeTemporalPlanner.Result(
                                        List.of(
                                                result.windows().get(0),
                                                result.windows().get(0),
                                                result.windows().get(0)),
                                        false,
                                        false)));
    }

    @Test
    void boundedGapReadRejectsOverflowAndOverlappingSummaries() {
        final var jdbc = new Jdbc();
        final var store = new JdbcSqlServerTemporalPlan(jdbc.source());
        jdbc.gaps = List.of(gap(0, "NOT_STARTED"), gap(1, "PUBLISHED"));
        final var result = store.readGapPage(HASH, 2, START);
        assertEquals(2, result.size());
        assertEquals("PUBLISHED", result.get(1).state());
        assertEquals(3, jdbc.fetchSize);
        assertThrows(UnsupportedOperationException.class, () -> result.clear());
        assertThrows(IllegalStateException.class, () -> store.readGapPage(HASH, 1, START));
        jdbc.gaps = List.of(gap(0, "PUBLISHED"), gap(0, "EXTRACTING"));
        assertThrows(IllegalStateException.class, () -> store.readGapPage(HASH, 2, START));
        jdbc.unavailable = true;
        assertThrows(IllegalStateException.class, () -> store.readGapPage(HASH, 2, START));
        assertThrows(IllegalArgumentException.class, () -> store.readGapPage(HASH, 0, START));
        assertThrows(IllegalArgumentException.class, () -> store.readGapPage(HASH, 65, START));
        assertThrows(IllegalArgumentException.class, () -> store.readGapPage(null, 2, START));
    }

    @Test
    void operationalWorkloadRefusesUnverifiedTlsBeforeOpeningAnyConnection() {
        for (final String tls :
                new String[] {"", ";encrypt=false", ";encrypt=true;trustServerCertificate=true"}) {
            final var target =
                    ShadowStorageProperties.enabled(
                            ShadowStorageTargetKind.LOCAL_EPHEMERAL,
                            "jdbc:sqlserver://localhost;databaseName=ETL_SISTEMA_V2_SHADOW;integratedSecurity=true"
                                    + tls,
                            null);
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            new BoundedRuntimeDataSource(
                                    target,
                                    url -> {
                                        throw new AssertionError(
                                                "TLS rejection must precede connection");
                                    }));
        }
    }

    @Test
    void workloadConnectionsValidateTargetLimitConcurrencyAndReleaseOnEveryClose()
            throws Exception {
        final var jdbc = new Jdbc();
        final var target =
                ShadowStorageProperties.enabled(
                        ShadowStorageTargetKind.LOCAL_EPHEMERAL,
                        "jdbc:sqlserver://localhost;databaseName=ETL_SISTEMA_V2_SHADOW;integratedSecurity=true"
                                + ";encrypt=true;trustServerCertificate=false",
                        null);
        final var source =
                new BoundedRuntimeDataSource(
                        target,
                        url -> {
                            assertTrue(url.endsWith(";loginTimeout=10;socketTimeout=20000"));
                            return jdbc.open();
                        });
        final var connections = new java.util.ArrayList<Connection>();
        for (int i = 0; i < 2; i++) {
            connections.add(source.getConnection());
        }
        assertThrows(SQLException.class, source::getConnection);
        for (final var connection : connections) {
            connection.close();
            connection.close();
        }
        assertEquals(2, jdbc.closed);
        try (var connection = source.getConnection();
                var statement = connection.createStatement()) {
            assertNotNull(statement);
            assertEquals(20, jdbc.timeout);
        }
        jdbc.targetValid = false;
        for (int i = 0; i < 5; i++) {
            assertThrows(SQLException.class, source::getConnection);
        }
        jdbc.targetValid = true;
        jdbc.unavailable = true;
        for (int i = 0; i < 5; i++) {
            assertThrows(SQLException.class, source::getConnection);
        }
        assertThrows(
                IllegalArgumentException.class,
                () -> new BoundedRuntimeDataSource(ShadowStorageProperties.disabled()));
        assertThrows(SQLException.class, () -> source.getConnection("unused", "unused"));
        assertThrows(SQLException.class, () -> source.unwrap(Connection.class));
        assertThrows(UnsupportedOperationException.class, () -> source.setLoginTimeout(1));
        assertThrows(UnsupportedOperationException.class, () -> source.setLogWriter(null));
        assertNull(source.getLogWriter());
        assertFalse(source.isWrapperFor(DataSource.class));
        assertEquals(10, source.getLoginTimeout());
        assertNotNull(source.getParentLogger());
        assertFalse(source.toString().contains("localhost"));
    }

    @ParameterizedTest
    @CsvSource({"false,false", "false,true", "true,false", "true,true"})
    void closesAStatementWhoseTimeoutCannotBeSet(final boolean callable, final boolean failClose)
            throws Exception {
        final var jdbc = new Jdbc();
        final var primary = new SQLException("SYNTHETIC_TIMEOUT_FAILURE");
        final var cleanup = new SQLException("SYNTHETIC_STATEMENT_CLOSE_FAILURE");
        jdbc.timeoutFailure = primary;
        jdbc.statementCloseFailure = failClose ? cleanup : null;
        final var target =
                ShadowStorageProperties.enabled(
                        ShadowStorageTargetKind.LOCAL_EPHEMERAL,
                        "jdbc:sqlserver://localhost;databaseName=ETL_SISTEMA_V2_SHADOW;integratedSecurity=true"
                                + ";encrypt=true;trustServerCertificate=false",
                        null);
        final var source = new BoundedRuntimeDataSource(target, url -> jdbc.open());

        try (var connection = source.getConnection()) {
            final var failure =
                    assertThrows(
                            SQLException.class,
                            () -> {
                                if (callable) {
                                    connection.prepareCall("synthetic");
                                } else {
                                    connection.createStatement();
                                }
                            });
            assertSame(primary, failure);
            assertEquals(1, jdbc.closedWorkloadStatements);
            assertEquals(failClose ? 1 : 0, failure.getSuppressed().length);
            if (failClose) {
                assertSame(cleanup, failure.getSuppressed()[0]);
            }
            assertEquals(0, jdbc.closed);
        }
        assertEquals(1, jdbc.closed);
    }

    @Test
    void releasesThePermitWhenTheOpenerRejectsItsConfiguration() throws Exception {
        final var jdbc = new Jdbc();
        final var primary = new IllegalArgumentException("SYNTHETIC_CONFIGURATION_REFUSED");
        final boolean[] refused = {true};
        final var target =
                ShadowStorageProperties.enabled(
                        ShadowStorageTargetKind.LOCAL_EPHEMERAL,
                        "jdbc:sqlserver://localhost;databaseName=ETL_SISTEMA_V2_SHADOW;integratedSecurity=true"
                                + ";encrypt=true;trustServerCertificate=false",
                        null);
        final var source =
                new BoundedRuntimeDataSource(
                        target,
                        url -> {
                            if (refused[0]) {
                                throw primary;
                            }
                            return jdbc.open();
                        });

        for (int attempt = 0; attempt < 3; attempt++) {
            assertSame(
                    primary, assertThrows(IllegalArgumentException.class, source::getConnection));
        }
        assertEquals(0, jdbc.closed);
        refused[0] = false;
        try (var connection = source.getConnection()) {
            assertNotNull(connection);
        }
        assertEquals(1, jdbc.closed);
    }

    @ParameterizedTest
    @MethodSource("resourceFailures")
    void statementSetupAlwaysClosesItsResourceAndPreservesTheOriginalFailure(
            final String failureKind, final String cleanupKind) throws Exception {
        final var jdbc = new Jdbc();
        final Throwable failure = resourceFailure(failureKind);
        final Throwable cleanup =
                cleanupKind.equals("SAME") ? failure : resourceFailure(cleanupKind);
        jdbc.timeoutFailure = failure;
        jdbc.statementCloseFailure = cleanup;
        final var source = boundedSource(jdbc);

        try (var connection = source.getConnection()) {
            assertSame(failure, assertThrows(failure.getClass(), connection::createStatement));
            assertEquals(1, jdbc.closedWorkloadStatements);
            assertEquals(cleanup == failure ? 0 : 1, failure.getSuppressed().length);
            if (cleanup != failure) {
                assertSame(cleanup, failure.getSuppressed()[0]);
            }
            assertEquals(0, jdbc.closed);
        }
        assertEquals(1, jdbc.closed);
    }

    @ParameterizedTest
    @MethodSource("resourceFailures")
    void failedPreflightClosesTheConnectionAndReleasesItsPermitAfterAnyCleanupFailure(
            final String failureKind, final String cleanupKind) throws Exception {
        final var jdbc = new Jdbc();
        final Throwable failure = resourceFailure(failureKind);
        final Throwable cleanup =
                cleanupKind.equals("SAME") ? failure : resourceFailure(cleanupKind);
        jdbc.preflightFailure = failure;
        jdbc.connectionCloseFailure = cleanup;
        final var source = boundedSource(jdbc);

        assertSame(failure, assertThrows(failure.getClass(), source::getConnection));
        assertEquals(1, jdbc.closed);
        assertEquals(cleanup == failure ? 0 : 1, failure.getSuppressed().length);
        if (cleanup != failure) {
            assertSame(cleanup, failure.getSuppressed()[0]);
        }
        jdbc.preflightFailure = null;
        jdbc.connectionCloseFailure = null;
        try (var first = source.getConnection();
                var second = source.getConnection()) {
            assertNotNull(first);
            assertNotNull(second);
            assertThrows(SQLException.class, source::getConnection);
        }
        assertEquals(3, jdbc.closed);
    }

    @Test
    void failedOpenerReleasesItsPermitEvenWhenItThrowsAnError() throws Exception {
        final var jdbc = new Jdbc();
        final var failure = new AssertionError("SYNTHETIC_OPEN_ERROR");
        final boolean[] fail = {true};
        final var source =
                new BoundedRuntimeDataSource(
                        boundedTarget(),
                        url -> {
                            if (fail[0]) {
                                throw failure;
                            }
                            return jdbc.open();
                        });
        for (int attempt = 0; attempt < 3; attempt++) {
            assertSame(failure, assertThrows(AssertionError.class, source::getConnection));
        }
        fail[0] = false;
        try (var first = source.getConnection();
                var second = source.getConnection()) {
            assertNotNull(first);
            assertNotNull(second);
            assertThrows(SQLException.class, source::getConnection);
        }
        assertEquals(2, jdbc.closed);
    }

    @Test
    void overlappingClosesReleaseOnePermitOnlyAfterThePhysicalCloseFinishes() throws Exception {
        final var jdbc = new Jdbc();
        final var source = boundedSource(jdbc);
        final var started = new CountDownLatch(1);
        final var finish = new CountDownLatch(1);
        final var attempts = new AtomicInteger();
        jdbc.closeAction =
                () -> {
                    if (attempts.incrementAndGet() == 1) {
                        started.countDown();
                        try {
                            if (!finish.await(5, TimeUnit.SECONDS)) {
                                throw new IllegalStateException("SYNTHETIC_CLOSE_NOT_RELEASED");
                            }
                        } catch (final InterruptedException failure) {
                            Thread.currentThread().interrupt();
                            throw new IllegalStateException("SYNTHETIC_CLOSE_INTERRUPTED", failure);
                        }
                    }
                };
        final var executor = Executors.newFixedThreadPool(2);
        final var first = source.getConnection();
        try (var second = source.getConnection()) {
            assertNotNull(second);
            final var original =
                    executor.submit(
                            () -> {
                                first.close();
                                return null;
                            });
            try {
                assertTrue(started.await(5, TimeUnit.SECONDS));
                executor.submit(
                                () -> {
                                    first.close();
                                    return null;
                                })
                        .get(5, TimeUnit.SECONDS);
                assertEquals(1, attempts.get());
                assertThrows(SQLException.class, source::getConnection);
            } finally {
                finish.countDown();
                original.get(5, TimeUnit.SECONDS);
            }
            try (var replacement = source.getConnection()) {
                assertNotNull(replacement);
                assertThrows(SQLException.class, source::getConnection);
            }
        } finally {
            finish.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
            first.close();
        }
        assertEquals(3, jdbc.closed);
    }

    private static Stream<Arguments> resourceFailures() {
        return Stream.of("SQL", "RUNTIME", "ERROR")
                .flatMap(
                        primary ->
                                Stream.of("SQL", "RUNTIME", "ERROR", "SAME")
                                        .map(cleanup -> Arguments.of(primary, cleanup)));
    }

    private static Throwable resourceFailure(final String kind) {
        return switch (kind) {
            case "SQL" -> new SQLException("SYNTHETIC_RESOURCE_SQL");
            case "RUNTIME" -> new IllegalStateException("SYNTHETIC_RESOURCE_RUNTIME");
            case "ERROR" -> new AssertionError("SYNTHETIC_RESOURCE_ERROR");
            default -> throw new IllegalArgumentException("SYNTHETIC_FAILURE_KIND");
        };
    }

    private static BoundedRuntimeDataSource boundedSource(final Jdbc jdbc) {
        return new BoundedRuntimeDataSource(boundedTarget(), url -> jdbc.open());
    }

    private static ShadowStorageProperties boundedTarget() {
        return ShadowStorageProperties.enabled(
                ShadowStorageTargetKind.LOCAL_EPHEMERAL,
                "jdbc:sqlserver://localhost;databaseName=ETL_SISTEMA_V2_SHADOW;integratedSecurity=true"
                        + ";encrypt=true;trustServerCertificate=false",
                null);
    }

    private static Map<Object, Object> gap(final int day, final String state) {
        return Map.of(
                "execution_id",
                new UUID(0, day + 1).toString(),
                "partition_start_utc",
                Timestamp.from(START.plusSeconds(day * 86400L)),
                "partition_end_exclusive_utc",
                Timestamp.from(START.plusSeconds((day + 1) * 86400L)),
                "state",
                state);
    }

    private static <T> T proxy(final Class<T> type, final InvocationHandler handler) {
        return type.cast(
                Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, handler));
    }

    private static final class Jdbc {
        final Map<Integer, Object> parameters = new HashMap<>();
        List<Map<Object, Object>> gaps = List.of();
        int closed;
        int timeout;
        int fetchSize;
        long count = 1;
        boolean ack;
        boolean duplicates;
        boolean unavailable;
        boolean targetValid = true;
        Throwable timeoutFailure;
        Throwable statementCloseFailure;
        Throwable preflightFailure;
        Throwable connectionCloseFailure;
        Runnable closeAction;
        int closedWorkloadStatements;

        DataSource source() {
            return proxy(
                    DataSource.class,
                    (p, m, a) -> m.getName().equals("getConnection") ? open() : null);
        }

        Connection open() throws SQLException {
            if (unavailable) {
                throw new SQLException("SYNTHETIC_UNAVAILABLE");
            }
            return proxy(
                    Connection.class,
                    (p, m, a) -> {
                        if (m.getName().equals("close")) {
                            if (closeAction != null) {
                                closeAction.run();
                            }
                            closed++;
                            if (connectionCloseFailure != null) {
                                throw connectionCloseFailure;
                            }
                            return null;
                        }
                        if (m.getName().equals("createStatement")) {
                            return statement("preflight", Statement.class);
                        }
                        if (m.getName().equals("prepareCall")) {
                            return statement(a[0].toString(), CallableStatement.class);
                        }
                        return null;
                    });
        }

        <T> T statement(final String sql, final Class<T> type) {
            final boolean[] workload = {false};
            return proxy(
                    type,
                    (p, m, a) -> {
                        switch (m.getName()) {
                            case "setQueryTimeout":
                                timeout = (int) a[0];
                                workload[0] = timeout == 20;
                                if (!workload[0] && preflightFailure != null) {
                                    throw preflightFailure;
                                }
                                if (workload[0] && timeoutFailure != null) {
                                    throw timeoutFailure;
                                }
                                return null;
                            case "close":
                                if (workload[0]) {
                                    closedWorkloadStatements++;
                                    if (statementCloseFailure != null) {
                                        throw statementCloseFailure;
                                    }
                                }
                                return null;
                            case "setFetchSize":
                                fetchSize = (int) a[0];
                                return null;
                            case "setString", "setNString", "setInt", "setTimestamp":
                                parameters.put((int) a[0], a[1]);
                                return null;
                            case "getMoreResults":
                                return ack;
                            case "getUpdateCount":
                                return -1;
                            case "executeQuery":
                                final List<Map<Object, Object>> values =
                                        sql.equals("preflight")
                                                ? List.of(Map.of(1, targetValid ? 1 : 0))
                                                : sql.contains("temporal_plan")
                                                        ? duplicates
                                                                ? List.of(
                                                                        Map.of(1, count),
                                                                        Map.of(1, count))
                                                                : List.of(Map.of(1, count))
                                                        : gaps;
                                final int[] index = {-1};
                                return proxy(
                                        ResultSet.class,
                                        (r, n, b) -> {
                                            if (n.getName().equals("next")) {
                                                return ++index[0] < values.size();
                                            }
                                            if (n.getName().startsWith("get")) {
                                                return values.get(index[0]).get(b[0]);
                                            }
                                            return null;
                                        });
                            default:
                                return null;
                        }
                    });
        }
    }
}
