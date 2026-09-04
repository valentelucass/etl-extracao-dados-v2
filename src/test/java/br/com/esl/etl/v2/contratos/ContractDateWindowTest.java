package br.com.esl.etl.v2.contratos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class ContractDateWindowTest {

    @Test
    void splitsLongFreteWindowsIntoInclusiveBlocksOfAtMostThirtyDays() {
        final ContractDateWindow window =
                new ContractDateWindow(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 2, 4));

        assertEquals(
                List.of(
                        new ContractDateWindow(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 30)),
                        new ContractDateWindow(
                                LocalDate.of(2026, 1, 31), LocalDate.of(2026, 2, 4))),
                window.asWindowsOfAtMostDays(30));
        assertThrows(IllegalArgumentException.class, () -> window.asWindowsOfAtMostDays(0));
    }
}
