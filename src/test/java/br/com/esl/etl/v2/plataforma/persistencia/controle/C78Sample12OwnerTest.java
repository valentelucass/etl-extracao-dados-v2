package br.com.esl.etl.v2.plataforma.persistencia.controle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.bootstrap.RuntimeDevEngineBankBinding;
import br.com.esl.etl.v2.modulos.coletas.domain.ColetaObservedRootComparator.ExpectedRoot;
import br.com.esl.etl.v2.plataforma.autorizacao.RuntimeAction;
import br.com.esl.etl.v2.plataforma.autorizacao.RuntimeAuthorizationScope;
import br.com.esl.etl.v2.plataforma.controle.ExecutionMode;
import br.com.esl.etl.v2.plataforma.controle.ImmutableFingerprint;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportExtractionLimits;
import br.com.esl.etl.v2.plataforma.identidade.FirstWaveIdentityContract;
import br.com.esl.etl.v2.plataforma.identidade.ScopedSourceIdentity;
import br.com.esl.etl.v2.plataforma.orquestracao.RuntimeExecutionRequest;
import br.com.esl.etl.v2.plataforma.orquestracao.RuntimePlanningRequest;
import br.com.esl.etl.v2.plataforma.orquestracao.RuntimeWindowStrategy;
import br.com.esl.etl.v2.plataforma.orquestracao.RuntimeWorkloadDefinition;
import br.com.esl.etl.v2.plataforma.orquestracao.RuntimeWorkloadId;
import br.com.esl.etl.v2.plataforma.orquestracao.RuntimeWorkloadRegistry;
import br.com.esl.etl.v2.plataforma.resiliencia.RuntimeExitCategory;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;

/**
 * JDBC protocol components only. Fixtures grant no admission and execute no physical SQL/source.
 */
class C78Sample12OwnerTest {
    static C78Sample12Selection selection() {
        var historical = C78DevEngineBankAdapterTest.scope();
        var id = new RuntimeWorkloadId("coletas");
        var hash = new ImmutableFingerprint("component-v1", "a".repeat(64));
        var now = Instant.parse("2024-01-01T00:00:00Z");
        var definition =
                new RuntimeWorkloadDefinition(
                        id,
                        "DATA_EXPORT",
                        "component-source",
                        "component-tenant",
                        "coletas",
                        hash,
                        hash,
                        Duration.ofSeconds(60));
        var request =
                new RuntimeExecutionRequest(
                        UUID.randomUUID(),
                        id,
                        ExecutionMode.BACKFILL,
                        RuntimeWindowStrategy.INTERVAL,
                        now,
                        now.plusSeconds(60),
                        "component-idempotency");
        var plan =
                RuntimeWorkloadRegistry.of(definition)
                        .plan(
                                new RuntimePlanningRequest(
                                        UUID.randomUUID(), "LOCAL_SHADOW", hash, now, request));
        var scope =
                new RuntimeDevEngineBankBinding.Scope(
                        historical.configuration(),
                        RuntimeAuthorizationScope.from(UUID.randomUUID(), RuntimeAction.RUN, plan),
                        historical.target(),
                        historical.bundleManifestSHA256(),
                        historical.configurationSHA256(),
                        historical.requestSHA256(),
                        historical.planSHA256(),
                        historical.template(),
                        1,
                        new DataExportExtractionLimits(1, 12, 1));
        return C78Sample12Selection.from(scope, plan);
    }

    static RuntimeDevEngineBankBinding.SampleFinish finish(
            C78Sample12Selection selection, int rows) {
        var root =
                new ExpectedRoot(
                        new ScopedSourceIdentity(
                                selection.item().partition().sourceInstance(),
                                selection.item().partition().tenantScope(),
                                FirstWaveIdentityContract.Entity.COLETAS,
                                new ScopedSourceIdentity.SourceKey(
                                        ScopedSourceIdentity.WireType.INTEGER, "INTEGER:1")),
                        OptionalInt.of(rows),
                        Map.of(),
                        Set.of(1));
        var expectation =
                new RuntimeDevEngineBankBinding.SampleExpectation(
                        selection.item().request().executionId(),
                        rows,
                        List.of(root),
                        Map.of(1, 1));
        return new RuntimeDevEngineBankBinding.SampleFinish(
                Optional.of(expectation),
                RuntimeExitCategory.DEGRADED,
                RuntimeDevEngineBankBinding.SampleOutcome.PARTIAL_BOUNDARY);
    }

    @Test
    void oneAndTwelveRequireBindingSetsAndThreeCleanupReads() throws Exception {
        for (int rows : List.of(1, 12)) {
            var f = new Fixture(rows);
            var baseline = C78Sample12Owner.baseline(f.connection(), f.selection, f::before);
            var finish = finish(f.selection, rows);
            assertTrue(C78Sample12Owner.compare(f.connection(), f.selection, finish, f::before));
            var result =
                    C78Sample12Owner.rollbackAndClose(
                            f.connection(),
                            f.selection,
                            baseline,
                            finish,
                            true,
                            f::before,
                            () -> f.charges);
            assertTrue(result.matches(finish));
            assertEquals(6, f.sql.size());
            assertEquals(6, result.sqlStatementsUsed());
            assertEquals(2, f.rollbacks);
            assertEquals(0, f.transaction);
            assertTrue(f.closed);
            assertEquals("SELECT @@TRANCOUNT,XACT_STATE()", f.sql.get(3));
            assertEquals("SELECT @@TRANCOUNT,XACT_STATE()", f.sql.get(5));
            assertTrue(f.sql.get(4).contains("sys.dm_tran_locks"));
            assertFalse(
                    f.sql.stream()
                            .anyMatch(sql -> sql.contains("COMMIT") || sql.startsWith("SET ")));
            assertEquals(List.of(10, 10, 10, 10, 10, 10), f.timeouts);
        }
    }

    @Test
    void symmetricSetOrMultiplicityDivergenceRefusesBeforeAcceptance() throws Exception {
        for (int column : List.of(2, 4, 5, 6, 7, 8, 9, 10, 11, 12)) {
            var f = new Fixture(12);
            f.badSetColumn = column;
            var failure =
                    assertThrows(
                            SQLException.class,
                            () ->
                                    C78Sample12Owner.compare(
                                            f.connection(),
                                            f.selection,
                                            finish(f.selection, 12),
                                            f::before));
            assertEquals("DEV_SAMPLE_OWNER_READBACK_MISMATCH", failure.getMessage());
        }
    }

    @Test
    void exactCaseAndWhitespaceOfControlStateAreRequired() throws Exception {
        for (String state : List.of("failed", "FAILED ", "FÁILED")) {
            var f = new Fixture(1);
            f.state = state;
            assertThrows(
                    SQLException.class,
                    () ->
                            C78Sample12Owner.compare(
                                    f.connection(),
                                    f.selection,
                                    finish(f.selection, 1),
                                    f::before));
        }
    }

    @Test
    void changedScopedSnapshotCannotClaimRollbackAbsence() throws Exception {
        var f = new Fixture(1);
        var baseline = C78Sample12Owner.baseline(f.connection(), f.selection, f::before);
        f.changedBaseline = true;
        assertThrows(
                SQLException.class,
                () ->
                        C78Sample12Owner.rollbackAndClose(
                                f.connection(),
                                f.selection,
                                baseline,
                                finish(f.selection, 1),
                                false,
                                f::before,
                                () -> f.charges));
        assertTrue(f.closed);
        assertEquals(2, f.rollbacks);
    }

    @Test
    void remainingOwnXRefusesAndRollsBackTheReadTransaction() throws Exception {
        var f = new Fixture(1);
        var baseline = C78Sample12Owner.baseline(f.connection(), f.selection, f::before);
        f.releasedX = 1;
        assertThrows(
                SQLException.class,
                () ->
                        C78Sample12Owner.rollbackAndClose(
                                f.connection(),
                                f.selection,
                                baseline,
                                finish(f.selection, 1),
                                false,
                                f::before,
                                () -> f.charges));
        assertTrue(f.closed);
        assertEquals(2, f.rollbacks);
    }

    @Test
    void failedRollbackStillClosesAndPreservesBothCleanupCauses() throws Exception {
        var f = new Fixture(1);
        var baseline = C78Sample12Owner.baseline(f.connection(), f.selection, f::before);
        f.failRollback = true;
        f.failClose = true;
        var failure =
                assertThrows(
                        SQLException.class,
                        () ->
                                C78Sample12Owner.rollbackAndClose(
                                        f.connection(),
                                        f.selection,
                                        baseline,
                                        finish(f.selection, 1),
                                        false,
                                        f::before,
                                        () -> f.charges));
        assertEquals("COMPONENT_ROLLBACK", failure.getMessage());
        assertEquals(1, failure.getSuppressed().length);
        assertEquals(1, failure.getSuppressed()[0].getSuppressed().length);
        assertTrue(f.closed);
    }

    @Test
    void rollbackErrorCannotSkipPhysicalCloseAndRemainsPrimary() throws Exception {
        var f = new Fixture(1);
        var baseline = C78Sample12Owner.baseline(f.connection(), f.selection, f::before);
        f.rollbackError = new AssertionError("COMPONENT_ROLLBACK_FATAL");
        f.closeError = new AssertionError("COMPONENT_CLOSE_FATAL");
        var failure =
                assertThrows(
                        AssertionError.class,
                        () ->
                                C78Sample12Owner.rollbackAndClose(
                                        f.connection(),
                                        f.selection,
                                        baseline,
                                        finish(f.selection, 1),
                                        false,
                                        f::before,
                                        () -> f.charges));
        assertSame(f.rollbackError, failure);
        assertEquals(2, f.rollbacks);
        assertEquals(1, f.closeAttempts);
        assertFalse(f.closed);
        assertEquals(1, failure.getSuppressed().length);
        assertSame(f.closeError, failure.getSuppressed()[0]);
    }

    @Test
    void readbackErrorRetainsIndependentRollbackAndCloseFailures() throws Exception {
        var f = new Fixture(1);
        var baseline = C78Sample12Owner.baseline(f.connection(), f.selection, f::before);
        f.readbackError = new AssertionError("COMPONENT_READBACK_FATAL");
        f.rollbackErrorAfter = 1;
        f.rollbackError = new AssertionError("COMPONENT_ROLLBACK_FATAL");
        f.closeError = new AssertionError("COMPONENT_CLOSE_FATAL");
        var failure =
                assertThrows(
                        AssertionError.class,
                        () ->
                                C78Sample12Owner.rollbackAndClose(
                                        f.connection(),
                                        f.selection,
                                        baseline,
                                        finish(f.selection, 1),
                                        false,
                                        f::before,
                                        () -> f.charges));
        assertSame(f.readbackError, failure);
        assertSame(f.rollbackError, failure.getSuppressed()[0]);
        assertSame(f.closeError, failure.getSuppressed()[0].getSuppressed()[0]);
        assertEquals(1, f.closeAttempts);
        assertFalse(f.closed);
    }

    @Test
    void physicalCloseErrorNeverReturnsAVerifiedReceipt() throws Exception {
        var f = new Fixture(1);
        var baseline = C78Sample12Owner.baseline(f.connection(), f.selection, f::before);
        f.closeError = new AssertionError("COMPONENT_CLOSE_FATAL");
        assertSame(
                f.closeError,
                assertThrows(
                        AssertionError.class,
                        () ->
                                C78Sample12Owner.rollbackAndClose(
                                        f.connection(),
                                        f.selection,
                                        baseline,
                                        finish(f.selection, 1),
                                        false,
                                        f::before,
                                        () -> f.charges)));
        assertEquals(2, f.rollbacks);
        assertEquals(1, f.closeAttempts);
        assertFalse(f.closed);
    }

    @Test
    void nullAndWrongShapeNeverBecomeConfirmedScalar() throws Exception {
        var f = new Fixture(1);
        f.nullScalar = true;
        assertThrows(
                SQLException.class,
                () -> C78Sample12Owner.baseline(f.connection(), f.selection, f::before));
        assertFalse(f.closed);
    }

    @Test
    void failedOrCancelledFinishHasNoInventedExpectedSet() throws Exception {
        for (var outcome :
                List.of(
                        RuntimeDevEngineBankBinding.SampleOutcome.FAILED,
                        RuntimeDevEngineBankBinding.SampleOutcome.CANCELLED)) {
            var f = new Fixture(1);
            var baseline = C78Sample12Owner.baseline(f.connection(), f.selection, f::before);
            var finish =
                    new RuntimeDevEngineBankBinding.SampleFinish(
                            Optional.empty(), RuntimeExitCategory.CANCELLED, outcome);
            assertFalse(C78Sample12Owner.compare(f.connection(), f.selection, finish, f::before));
            var result =
                    C78Sample12Owner.rollbackAndClose(
                            f.connection(),
                            f.selection,
                            baseline,
                            finish,
                            false,
                            f::before,
                            () -> f.charges);
            assertTrue(result.matches(finish));
            assertFalse(result.expectationVerified());
            assertEquals(0, result.physicalRowsVerified());
            assertEquals(4, f.sql.size());
        }
    }

    @Test
    void sqlKeepsScopedPredicatesExactKeyLengthAndNoLegacyLabels() throws Exception {
        var f = new Fixture(1);
        C78Sample12Owner.compare(f.connection(), f.selection, finish(f.selection, 1), f::before);
        String sql = f.sql.get(1);
        assertTrue(sql.contains("DATALENGTH(source_key)"));
        assertTrue(sql.contains("missing_stage") && sql.contains("extra_stage"));
        assertTrue(sql.contains("input_record_ordinal>?"));
        assertFalse(
                f.sql.stream()
                        .anyMatch(
                                s ->
                                        s.contains("ctl.execution_audit")
                                                || s.contains("ctl.page_audit")
                                                || s.contains("failure_category")));
    }

    static class Fixture {
        final C78Sample12Selection selection = selection();
        final List<String> sql = new ArrayList<>();
        final List<Integer> timeouts = new ArrayList<>();
        final int rows;
        int charges, rollbacks, transaction = 1, badSetColumn, releasedX, closeAttempts;
        int rollbackErrorAfter;
        Error rollbackError, closeError, readbackError, isClosedError;
        String state = "FAILED";
        boolean closed, changedBaseline, nullScalar, failRollback, failClose;

        Fixture(int rows) {
            this.rows = rows;
        }

        void before() {
            charges++;
        }

        Connection connection() {
            return (Connection)
                    Proxy.newProxyInstance(
                            getClass().getClassLoader(),
                            new Class<?>[] {Connection.class},
                            (p, m, a) ->
                                    switch (m.getName()) {
                                        case "createStatement" -> statement(null);
                                        case "prepareStatement" -> statement((String) a[0]);
                                        case "rollback" -> {
                                            rollbacks++;
                                            if (rollbackError != null
                                                    && rollbacks > rollbackErrorAfter) {
                                                throw rollbackError;
                                            }
                                            if (failRollback) {
                                                throw new SQLException("COMPONENT_ROLLBACK");
                                            }
                                            transaction = 0;
                                            yield null;
                                        }
                                        case "close" -> {
                                            closeAttempts++;
                                            if (closeError != null) {
                                                throw closeError;
                                            }
                                            closed = true;
                                            if (failClose) {
                                                throw new SQLException("COMPONENT_CLOSE");
                                            }
                                            yield null;
                                        }
                                        case "isClosed" -> {
                                            if (isClosedError != null) {
                                                throw isClosedError;
                                            }
                                            yield closed;
                                        }
                                        case "commit", "setAutoCommit" ->
                                                throw new AssertionError("OWNER_COMMIT_FORBIDDEN");
                                        default -> primitive(m.getReturnType());
                                    });
        }

        Object statement(String prepared) {
            return Proxy.newProxyInstance(
                    getClass().getClassLoader(),
                    new Class<?>[] {prepared == null ? Statement.class : PreparedStatement.class},
                    (p, m, a) -> {
                        if (m.getName().equals("setQueryTimeout")) {
                            timeouts.add((Integer) a[0]);
                            return null;
                        }
                        if (m.getName().equals("executeQuery")) {
                            String text = prepared == null ? (String) a[0] : prepared;
                            sql.add(text);
                            if (readbackError != null) {
                                throw readbackError;
                            }
                            if (text.equals("SELECT @@TRANCOUNT,XACT_STATE()")) {
                                return result(new Object[] {transaction, transaction});
                            }
                            if (text.contains("WITH unit")) {
                                transaction = 1;
                                Object[] values = new Object[19];
                                java.util.Arrays.fill(values, 0L);
                                for (int i = 14; i < 18; i++) {
                                    values[i] = changedBaseline && rollbacks > 0 ? "[{}]" : "[]";
                                }
                                values[18] = rollbacks > 0 ? (long) releasedX : 29L;
                                if (nullScalar) {
                                    values[0] = null;
                                }
                                return result(values);
                            }
                            if (text.contains("WITH expected")) {
                                Object[] values = {
                                    1L,
                                    (long) rows,
                                    1L,
                                    (long) rows,
                                    0L,
                                    0L,
                                    (long) rows,
                                    0L,
                                    0L,
                                    0L,
                                    (long) rows,
                                    0L
                                };
                                if (badSetColumn != 0) {
                                    values[badSetColumn - 1] =
                                            ((Long) values[badSetColumn - 1]) + 1L;
                                }
                                return result(values);
                            }
                            var item = selection.item();
                            var part = item.partition();
                            return result(
                                    new Object[] {
                                        part.environment(),
                                        part.sourceInstance(),
                                        part.tenantScope(),
                                        "coletas",
                                        "BACKFILL",
                                        Timestamp.from(part.partitionStart()),
                                        Timestamp.from(part.partitionEndExclusive()),
                                        item.definition().contract().version(),
                                        item.definition().contract().sha256(),
                                        item.definition().configuration().version(),
                                        item.definition().configuration().sha256(),
                                        selection.plan().cycleId().toString(),
                                        selection.plan().fingerprint().version(),
                                        selection.plan().fingerprint().sha256(),
                                        item.request().idempotencyKey(),
                                        item.request().windowStrategy().name(),
                                        state,
                                        1L,
                                        1L,
                                        0L,
                                        0L,
                                        0L,
                                        0L,
                                        0L,
                                        0L,
                                        0L,
                                        1L,
                                        1L
                                    });
                        }
                        return primitive(m.getReturnType());
                    });
        }

        static ResultSet result(Object[] values) {
            int[] position = {-1};
            boolean[] wasNull = {false};
            var metadata =
                    (ResultSetMetaData)
                            Proxy.newProxyInstance(
                                    Fixture.class.getClassLoader(),
                                    new Class<?>[] {ResultSetMetaData.class},
                                    (p, m, a) ->
                                            m.getName().equals("getColumnCount")
                                                    ? values.length
                                                    : primitive(m.getReturnType()));
            return (ResultSet)
                    Proxy.newProxyInstance(
                            Fixture.class.getClassLoader(),
                            new Class<?>[] {ResultSet.class},
                            (p, m, a) -> {
                                if (m.getName().equals("next")) {
                                    return ++position[0] == 0;
                                }
                                if (m.getName().equals("getMetaData")) {
                                    return metadata;
                                }
                                if (m.getName().equals("wasNull")) {
                                    return wasNull[0];
                                }
                                if (m.getName().startsWith("get")
                                        && a != null
                                        && a[0] instanceof Integer column) {
                                    Object value = values[column - 1];
                                    wasNull[0] = value == null;
                                    return switch (m.getName()) {
                                        case "getLong" ->
                                                value == null ? 0L : ((Number) value).longValue();
                                        case "getInt" ->
                                                value == null ? 0 : ((Number) value).intValue();
                                        case "getString" -> value == null ? null : value.toString();
                                        case "getTimestamp" -> value;
                                        default -> primitive(m.getReturnType());
                                    };
                                }
                                return primitive(m.getReturnType());
                            });
        }

        static Object primitive(Class<?> type) {
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
