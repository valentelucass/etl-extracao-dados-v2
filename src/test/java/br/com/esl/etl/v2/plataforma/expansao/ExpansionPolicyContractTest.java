package br.com.esl.etl.v2.plataforma.expansao;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import br.com.esl.etl.v2.modulos.faturasporcliente.domain.FaturaClienteRules.FiscalPolicy;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class ExpansionPolicyContractTest {
    private static final LocalDate START = LocalDate.of(2036, 4, 1);

    @Test
    void acceptsClosedStartOpenEndAndMaximumBoundedWindow() {
        final var policy = policy(START, START.plusDays(31), 100, 10000, 100000);
        assertDoesNotThrow(() -> policy.validate(START));
        assertDoesNotThrow(() -> policy.validate(START.plusDays(30)));
        assertEquals(100, policy.pageSize());
        for (final LocalDate outside : List.of(START.minusDays(1), START.plusDays(31))) {
            assertEquals(
                    "EXP_PARTITION_BOUND",
                    assertThrows(IllegalArgumentException.class, () -> policy.validate(outside))
                            .getMessage());
        }
        assertThrows(IllegalArgumentException.class, () -> policy.validate(null));
    }

    @Test
    void rejectsEmptyOversizedAndUnboundedPagination() {
        for (final LocalDate end : List.of(START, START.minusDays(1), START.plusDays(32))) {
            assertThrows(IllegalArgumentException.class, () -> policy(START, end, 1, 1, 1));
        }
        for (final int pageSize : List.of(0, 101)) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> policy(START, START.plusDays(1), pageSize, 1, 1));
        }
        for (final int maximumPages : List.of(0, 10001)) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> policy(START, START.plusDays(1), 1, maximumPages, 1));
        }
        for (final int maximumRows : List.of(0, 100001)) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> policy(START, START.plusDays(1), 1, 1, maximumRows));
        }
        assertThrows(
                NullPointerException.class,
                () ->
                        new ExpansionPolicy(
                                null,
                                START.plusDays(1),
                                START,
                                1,
                                1,
                                1,
                                FiscalPolicy.SYNTHETIC_CTE));
        assertThrows(
                NullPointerException.class,
                () -> new ExpansionPolicy(START, START.plusDays(1), START, 1, 1, 1, null));
    }

    private static ExpansionPolicy policy(
            final LocalDate start,
            final LocalDate end,
            final int pageSize,
            final int maximumPages,
            final int maximumRows) {
        return new ExpansionPolicy(
                start, end, START, pageSize, maximumPages, maximumRows, FiscalPolicy.SYNTHETIC_CTE);
    }
}
