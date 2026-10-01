package br.com.esl.etl.v2.plataforma.persistencia.coletas;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;

class LaboratoryJdbcBudgetTest {
    @Test
    void rejectsUnboundedAllowanceAndStopsBeforeExcessSqlCall() throws Exception {
        for (final long invalid : new long[] {0, 100001}) {
            assertEquals(
                    "COL_LAB_JDBC_CALL_POLICY",
                    assertThrows(
                                    IllegalArgumentException.class,
                                    () -> new LaboratoryJdbcBudget(invalid))
                            .getMessage());
        }
        final var budget = new LaboratoryJdbcBudget(2);
        budget.reserve();
        budget.reserve();
        assertEquals(2, budget.used());
        assertEquals(
                "COL_LAB_JDBC_CALL_LIMIT",
                assertThrows(SQLException.class, budget::reserve).getMessage());
        assertEquals(2, budget.used());
    }

    @Test
    void concurrentReservationsShareOneAtomicPhysicalLimit() throws Exception {
        final var budget = new LaboratoryJdbcBudget(4);
        final var start = new CountDownLatch(1);
        final var workers = Executors.newFixedThreadPool(8);
        try {
            final var futures = new ArrayList<java.util.concurrent.Future<Boolean>>();
            for (int i = 0; i < 8; i++) {
                futures.add(
                        workers.submit(
                                () -> {
                                    start.await();
                                    try {
                                        budget.reserve();
                                        return true;
                                    } catch (final SQLException expected) {
                                        return false;
                                    }
                                }));
            }
            start.countDown();
            int granted = 0;
            for (final var future : futures) {
                granted += future.get() ? 1 : 0;
            }
            assertEquals(4, granted);
            assertEquals(4, budget.used());
        } finally {
            workers.shutdownNow();
        }
    }
}
