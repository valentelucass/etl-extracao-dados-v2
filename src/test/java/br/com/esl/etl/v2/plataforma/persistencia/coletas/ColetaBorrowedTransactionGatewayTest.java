package br.com.esl.etl.v2.plataforma.persistencia.coletas;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.modulos.coletas.domain.ColetaStageBatch;
import br.com.esl.etl.v2.modulos.coletas.domain.ColetaStageRecord;
import br.com.esl.etl.v2.plataforma.persistencia.staging.StagingPersistenceException;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken;
import br.com.esl.etl.v2.plataforma.resiliencia.ResilienceCancelledException;
import java.lang.reflect.Proxy;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;

/** Real gateway component with isolated JDBC seam; never a delivered physical backend. */
class ColetaBorrowedTransactionGatewayTest {
    private static ColetaStageBatch batch() {
        return new ColetaStageBatch(
                UUID.randomUUID(),
                1,
                List.of(ColetaStageRecord.quarantine(1, null, "OWN_TEST")),
                Instant.EPOCH);
    }

    @Test
    void borrowedUses23ActualBindingsAnd10SecondsWithoutChangingOwnerTransaction() {
        final var jdbc = new JdbcBoundary();
        JdbcSqlServerColetaStagingGateway.borrowedTransaction(jdbc.source(), 1).stage(batch());
        assertEquals(1, jdbc.borrows);
        assertEquals(23, jdbc.bound);
        assertEquals(10, jdbc.timeout);
        assertEquals(1, jdbc.executions);
        assertEquals(0, jdbc.autoChanges);
        assertEquals(0, jdbc.commits);
        assertEquals(0, jdbc.rollbacks);
        assertEquals(1, jdbc.connectionCloses);
        assertEquals(1, jdbc.statementCloses);
    }

    @Test
    void borrowedFailurePreservesSqlCauseWithoutRollingBackOwner() {
        final var jdbc = new JdbcBoundary();
        jdbc.failExecute = true;
        final var error =
                assertThrows(
                        StagingPersistenceException.class,
                        () ->
                                JdbcSqlServerColetaStagingGateway.borrowedTransaction(
                                                jdbc.source(), 1)
                                        .stage(batch()));
        assertTrue(error.getCause() instanceof SQLException);
        assertEquals(0, jdbc.rollbacks);
        assertEquals(0, jdbc.commits);
        assertEquals(1, jdbc.connectionCloses);
        assertEquals(1, jdbc.statementCloses);
    }

    @Test
    void cancellationBeforeBorrowAndAfterExecuteNeverControlsOwnerTransaction() {
        final var before = new JdbcBoundary();
        assertThrows(
                ResilienceCancelledException.class,
                () ->
                        JdbcSqlServerColetaStagingGateway.borrowedTransaction(before.source(), 1)
                                .stage(batch(), () -> true));
        assertEquals(0, before.borrows);
        final var after = new JdbcBoundary();
        assertThrows(
                ResilienceCancelledException.class,
                () ->
                        JdbcSqlServerColetaStagingGateway.borrowedTransaction(after.source(), 1)
                                .stage(batch(), () -> after.executions > 0));
        assertEquals(1, after.executions);
        assertEquals(0, after.rollbacks);
        assertEquals(0, after.commits);
        assertEquals(1, after.statementCloses);
        assertEquals(1, after.connectionCloses);
    }

    @Test
    void borrowedRefusesAutocommitAndBatchBudgetBeforeRawPrepare() {
        final var auto = new JdbcBoundary();
        auto.autoCommit = true;
        assertThrows(
                StagingPersistenceException.class,
                () ->
                        JdbcSqlServerColetaStagingGateway.borrowedTransaction(auto.source(), 1)
                                .stage(batch()));
        assertEquals(0, auto.prepares);
        final var limit = new JdbcBoundary();
        final var two =
                new ColetaStageBatch(
                        UUID.randomUUID(),
                        1,
                        List.of(
                                ColetaStageRecord.quarantine(1, null, "OWN_TEST"),
                                ColetaStageRecord.quarantine(2, null, "OWN_TEST")),
                        Instant.EPOCH);
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        JdbcSqlServerColetaStagingGateway.borrowedTransaction(limit.source(), 1)
                                .stage(two));
        assertEquals(0, limit.borrows);
        assertThrows(
                IllegalArgumentException.class,
                () -> JdbcSqlServerColetaStagingGateway.borrowedTransaction(limit.source(), 0));
        assertThrows(
                IllegalArgumentException.class,
                () -> JdbcSqlServerColetaStagingGateway.borrowedTransaction(limit.source(), 1001));
    }

    @Test
    void historicalConstructorStillOwnsCommitAndRollbackAnd20SecondTimeout() {
        final var success = new JdbcBoundary();
        new JdbcSqlServerColetaStagingGateway(success.source())
                .stage(batch(), CancellationToken.none());
        assertEquals(1, success.autoChanges);
        assertEquals(1, success.commits);
        assertEquals(20, success.timeout);
        final var failure = new JdbcBoundary();
        failure.failExecute = true;
        assertThrows(
                StagingPersistenceException.class,
                () -> new JdbcSqlServerColetaStagingGateway(failure.source()).stage(batch()));
        assertEquals(1, failure.rollbacks);
        assertEquals(0, failure.commits);
    }

    private static final class JdbcBoundary {
        int borrows,
                prepares,
                bound,
                timeout,
                executions,
                commits,
                rollbacks,
                autoChanges,
                connectionCloses,
                statementCloses;
        boolean autoCommit, failExecute;
        final boolean[] slots = new boolean[24];

        DataSource source() {
            return (DataSource)
                    Proxy.newProxyInstance(
                            getClass().getClassLoader(),
                            new Class<?>[] {DataSource.class},
                            (p, m, a) -> {
                                if (m.getName().equals("getConnection")) {
                                    borrows++;
                                    return connection();
                                }
                                return value(m.getReturnType());
                            });
        }

        Connection connection() {
            return (Connection)
                    Proxy.newProxyInstance(
                            getClass().getClassLoader(),
                            new Class<?>[] {Connection.class},
                            (p, m, a) ->
                                    switch (m.getName()) {
                                        case "getAutoCommit" -> autoCommit;
                                        case "setAutoCommit" -> {
                                            autoChanges++;
                                            assertEquals(false, a[0]);
                                            yield null;
                                        }
                                        case "commit" -> {
                                            commits++;
                                            yield null;
                                        }
                                        case "rollback" -> {
                                            rollbacks++;
                                            yield null;
                                        }
                                        case "close" -> {
                                            connectionCloses++;
                                            yield null;
                                        }
                                        case "prepareCall" -> {
                                            prepares++;
                                            assertTrue(
                                                    ((String) a[0])
                                                            .startsWith(
                                                                    "{call"
                                                                            + " stg.usp_stage_coleta_record("));
                                            assertEquals(
                                                    23,
                                                    ((String) a[0])
                                                            .chars()
                                                            .filter(c -> c == '?')
                                                            .count());
                                            yield statement();
                                        }
                                        default -> value(m.getReturnType());
                                    });
        }

        CallableStatement statement() {
            return (CallableStatement)
                    Proxy.newProxyInstance(
                            getClass().getClassLoader(),
                            new Class<?>[] {CallableStatement.class},
                            (p, m, a) -> {
                                if (m.getName().equals("setQueryTimeout")) {
                                    timeout = (Integer) a[0];
                                    return null;
                                }
                                if (m.getName().startsWith("set")) {
                                    final int slot = (Integer) a[0];
                                    if (!slots[slot]) {
                                        slots[slot] = true;
                                        bound++;
                                    }
                                    return null;
                                }
                                if (m.getName().equals("executeBatch")) {
                                    executions++;
                                    if (failExecute) {
                                        throw new SQLException("OWN_COMPONENT_FAILURE");
                                    }
                                    return new int[] {1};
                                }
                                if (m.getName().equals("close")) {
                                    statementCloses++;
                                    return null;
                                }
                                return value(m.getReturnType());
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
