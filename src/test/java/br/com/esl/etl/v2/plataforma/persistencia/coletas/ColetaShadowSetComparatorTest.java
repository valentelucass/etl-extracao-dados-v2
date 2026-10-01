package br.com.esl.etl.v2.plataforma.persistencia.coletas;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.modulos.coletas.domain.ColetaAttributePresence;
import br.com.esl.etl.v2.modulos.coletas.domain.ColetaObservedRootComparator.ExpectedRoot;
import br.com.esl.etl.v2.plataforma.identidade.FirstWaveIdentityContract;
import br.com.esl.etl.v2.plataforma.identidade.ScopedSourceIdentity;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ColetaShadowSetComparatorTest {
    @Test
    void bilateralSetDifferenceKeepsPhysicalMultiplicityAndScopeInSql() throws Exception {
        final var jdbc = new FakeComparison(new long[] {1, 2, 3, 2, 3, 2, 0, 0, 0, 0, 3, 0});
        final var expected = List.of(root("INTEGER:1", 2), root("INTEGER:2", 1));
        final var result =
                ColetaShadowSetComparator.compare(jdbc.session(), UUID.randomUUID(), expected);
        assertTrue(result.matches());
        assertEquals("SCOPED_SQL_SET", result.provenance());
        assertFalse(result.windowCompletenessProven());
        assertFalse(result.childCompletenessProven());
        assertTrue(
                jdbc.sql.contains(
                        "FROM expected EXCEPT SELECT source_key,physical_rows FROM staged"));
        assertTrue(
                jdbc.sql.contains(
                        "FROM staged EXCEPT SELECT source_key,physical_rows FROM expected"));
        assertTrue(jdbc.sql.contains("FROM expected EXCEPT SELECT source_key FROM published"));
        assertTrue(jdbc.sql.contains("FROM published EXCEPT SELECT source_key FROM expected"));
        assertTrue(jdbc.sql.contains("c.last_seen_execution_id=?"));
        assertTrue(jdbc.sql.contains("p.source_instance=? AND p.tenant_scope=?"));
        assertEquals(8, jdbc.bindings.size());
        assertFalse(jdbc.sql.contains("INTEGER:1"));
    }

    @Test
    void rootSetEqualityCannotHideLostDuplicateOrQuarantine() {
        final var lostDuplicate =
                new ColetaShadowSetComparator.Result(1, 2, 3, 2, 2, 2, 1, 1, 0, 0, 2, 0);
        final var quarantine =
                new ColetaShadowSetComparator.Result(1, 2, 3, 2, 3, 2, 0, 0, 0, 0, 4, 1);
        assertFalse(lostDuplicate.matches());
        assertFalse(quarantine.matches());
    }

    @Test
    void missingOracleOrMixedTenantIsRejectedBeforeJdbc() throws Exception {
        final var jdbc = new FakeComparison(new long[12]);
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        ColetaShadowSetComparator.compare(
                                jdbc.session(),
                                UUID.randomUUID(),
                                List.of(
                                        new ExpectedRoot(
                                                root("INTEGER:1", 1).identity(),
                                                OptionalInt.empty(),
                                                Map.of()))));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        ColetaShadowSetComparator.compare(
                                jdbc.session(),
                                UUID.randomUUID(),
                                List.of(root("INTEGER:1", 1), root("INTEGER:1", 1))));
        assertTrue(jdbc.bindings.isEmpty());
    }

    @Test
    void stageReaderPreservesPresenceWithoutClaimingChildCompleteness() throws Exception {
        final var jdbc = new FakeComparison(new long[12]);
        jdbc.rows =
                List.<Object[]>of(
                        new Object[] {1, "INTEGER:1", "{\"status\":\"NULL\"}"},
                        new Object[] {2, "INTEGER:1", "{\"status\":\"NULL\"}"},
                        new Object[] {3, "INTEGER:1", "{\"status\":\"NULL\"}"});
        final var observed =
                ColetaShadowSetComparator.readBoundedBatchRows(
                        jdbc.session(),
                        UUID.randomUUID(),
                        root("INTEGER:1", 1).identity(),
                        3,
                        "112");
        assertEquals(3, observed.size());
        assertEquals(ColetaAttributePresence.NULL, observed.get(0).fieldPresence().get("status"));
        assertEquals(1, observed.get(0).page());
        assertEquals(1, observed.get(1).page());
        assertEquals(2, observed.get(2).page());
        assertEquals(
                "COL_SHADOW_READER_PAGE_UNBOUND",
                assertThrows(
                                SQLException.class,
                                () ->
                                        ColetaShadowSetComparator.readBoundedBatchRows(
                                                jdbc.session(),
                                                UUID.randomUUID(),
                                                root("INTEGER:1", 1).identity(),
                                                3,
                                                "1"))
                        .getMessage());
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        ColetaShadowSetComparator.readBoundedBatchRows(
                                jdbc.session(),
                                UUID.randomUUID(),
                                root("INTEGER:1", 1).identity(),
                                3,
                                "1".repeat(1001)));
    }

    @Test
    void observedBindingComesFromPersistedScopeContractAndCivilDay() throws Exception {
        final UUID run = UUID.randomUUID();
        final var jdbc = new FakeComparison(new long[12]);
        jdbc.rows =
                List.<Object[]>of(
                        new Object[] {
                            "source-a",
                            "tenant-a",
                            Timestamp.from(Instant.parse("2026-09-30T03:00:00Z")),
                            Timestamp.from(Instant.parse("2026-10-01T03:00:00Z")),
                            "a".repeat(64),
                            run.toString(),
                            1L,
                            2
                        });
        final var binding = ColetaShadowSetComparator.readObservedBinding(jdbc.session(), run);
        assertEquals(LocalDate.of(2026, 9, 30), binding.requestDate());
        assertEquals(run, binding.comparisonCohort());
        assertEquals("a".repeat(64), binding.contractFingerprint());
        assertEquals(0, binding.sourcePage());
        assertTrue(
                jdbc.sql.contains("FROM ctl.execution_attempt a JOIN ctl.execution_partition p"));
        jdbc.rows =
                List.<Object[]>of(
                        new Object[] {
                            "source-a",
                            "tenant-a",
                            Timestamp.from(Instant.parse("2026-09-30T03:00:00Z")),
                            Timestamp.from(Instant.parse("2026-10-01T02:59:00Z")),
                            "a".repeat(64),
                            run.toString(),
                            1L,
                            2
                        });
        assertEquals(
                "COL_SHADOW_WINDOW_NOT_ONE_CIVIL_DAY",
                assertThrows(
                                SQLException.class,
                                () ->
                                        ColetaShadowSetComparator.readObservedBinding(
                                                jdbc.session(), run))
                        .getMessage());
    }

    private static ExpectedRoot root(final String key, final int rows) {
        return new ExpectedRoot(
                new ScopedSourceIdentity(
                        "source-a",
                        "tenant-a",
                        FirstWaveIdentityContract.Entity.COLETAS,
                        new ScopedSourceIdentity.SourceKey(
                                ScopedSourceIdentity.WireType.INTEGER, key)),
                OptionalInt.of(rows),
                Map.of());
    }

    private static final class FakeComparison {
        private final long[] aggregate;
        private final List<Object> bindings = new ArrayList<>();
        private List<Object[]> rows = List.of();
        private String sql;

        private FakeComparison(final long[] aggregate) {
            this.aggregate = aggregate;
        }

        private ColetaTemporalLaboratorySession session() throws Exception {
            final var connection =
                    (Connection)
                            Proxy.newProxyInstance(
                                    Connection.class.getClassLoader(),
                                    new Class<?>[] {Connection.class},
                                    (proxy, method, args) -> {
                                        if (method.getName().equals("prepareStatement")) {
                                            sql = (String) args[0];
                                            return prepared();
                                        }
                                        return null;
                                    });
            final var constructor =
                    ColetaTemporalLaboratorySession.class.getDeclaredConstructor(Connection.class);
            constructor.setAccessible(true);
            return constructor.newInstance(connection);
        }

        private PreparedStatement prepared() {
            return (PreparedStatement)
                    Proxy.newProxyInstance(
                            PreparedStatement.class.getClassLoader(),
                            new Class<?>[] {PreparedStatement.class},
                            (proxy, method, args) -> {
                                if (method.getName().startsWith("set")
                                        && args != null
                                        && args.length == 2) {
                                    bindings.add(args[1]);
                                }
                                if (method.getName().equals("executeQuery")) {
                                    return resultRows();
                                }
                                return null;
                            });
        }

        private ResultSet resultRows() {
            return (ResultSet)
                    Proxy.newProxyInstance(
                            ResultSet.class.getClassLoader(),
                            new Class<?>[] {ResultSet.class},
                            new java.lang.reflect.InvocationHandler() {
                                private int cursor;

                                @Override
                                public Object invoke(
                                        final Object proxy,
                                        final java.lang.reflect.Method method,
                                        final Object[] args) {
                                    if (method.getName().equals("next")) {
                                        return ++cursor == 1
                                                || (rows.size() > 1 && cursor <= rows.size());
                                    }
                                    if (method.getName().equals("getLong")) {
                                        return rows.isEmpty()
                                                ? aggregate[(int) args[0] - 1]
                                                : ((Number) rows.get(cursor - 1)[(int) args[0] - 1])
                                                        .longValue();
                                    }
                                    if (method.getName().equals("getInt")) {
                                        return ((Number) rows.get(cursor - 1)[(int) args[0] - 1])
                                                .intValue();
                                    }
                                    if (method.getName().equals("wasNull")) {
                                        return false;
                                    }
                                    if (method.getName().equals("getString")) {
                                        return rows.get(cursor - 1)[(int) args[0] - 1];
                                    }
                                    if (method.getName().equals("getTimestamp")) {
                                        return rows.get(cursor - 1)[(int) args[0] - 1];
                                    }
                                    return null;
                                }
                            });
        }
    }
}
