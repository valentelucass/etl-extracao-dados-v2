package br.com.esl.etl.v2.contratos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ContractRunGuardTest {

    @Test
    void stopsEveryBudgetAfterAnyEntityReachesItsApprovedCallCap() {
        final ContractRunGuard guard = new ContractRunGuard();
        final ContractRemoteCallBudget coletas = new ContractRemoteCallBudget(1);
        final ContractRemoteCallBudget fretes = new ContractRemoteCallBudget(1);

        guard.reserveCall(coletas);
        assertThrows(
                ContractRemoteCallLimitExceededException.class, () -> guard.reserveCall(coletas));
        assertTrue(guard.isStopped());
        assertThrows(ContractRunStoppedException.class, () -> guard.reserveCall(fretes));
        assertEquals(0, fretes.callsMade());
    }
}
