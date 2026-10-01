package br.com.esl.etl.v2.plataforma.persistencia.coletas;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class JdbcColetaTemporalResultTest {
    @Test
    void reconciliationTotalsMustBalanceExactlyWithoutNegativeCounts() {
        final var accepted = new JdbcColetaTemporalLaboratory.Result(5, 1, 2, 2, true);
        assertEquals(5, accepted.considered());
        assertEquals(1, accepted.inserted());
        assertEquals(2, accepted.updated());
        assertEquals(2, accepted.noop());
        for (final long[] counts :
                new long[][] {
                    {-1, 0, 0, 0}, {1, -1, 1, 1}, {1, 1, -1, 1}, {1, 1, 1, -1}, {5, 1, 1, 1}
                }) {
            assertEquals(
                    "COL_LAB_RECONCILIATION_INVALID",
                    assertThrows(
                                    IllegalArgumentException.class,
                                    () ->
                                            new JdbcColetaTemporalLaboratory.Result(
                                                    counts[0], counts[1], counts[2], counts[3],
                                                    false))
                            .getMessage());
        }
    }
}
