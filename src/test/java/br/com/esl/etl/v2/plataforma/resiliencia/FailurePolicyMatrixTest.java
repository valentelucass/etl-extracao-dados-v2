package br.com.esl.etl.v2.plataforma.resiliencia;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.plataforma.controle.ExecutionState;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class FailurePolicyMatrixTest {

    private final FailurePolicyMatrix matrix = new FailurePolicyMatrix();

    @Test
    void coversEveryFailureKindWithAValidDecision() {
        for (final FailureKind kind : FailureKind.values()) {
            final FailureDecision decision =
                    matrix.decide(kind, new FailurePolicyContext(true, true));
            assertTrue(decision.action() != null, kind.name());
        }
    }

    @Test
    void retriesOnlyTransientFailuresWhileBudgetExists() {
        for (final FailureKind kind :
                new FailureKind[] {
                    FailureKind.RATE_LIMIT,
                    FailureKind.REQUEST_TIMEOUT,
                    FailureKind.HTTP_5XX,
                    FailureKind.SOURCE_UNAVAILABLE
                }) {
            assertEquals(
                    FailureAction.RETRY,
                    matrix.decide(kind, new FailurePolicyContext(true, false)).action());
            assertEquals(
                    FailureAction.ABORT,
                    matrix.decide(kind, FailurePolicyContext.exhausted()).action());
        }
        for (final FailureKind kind :
                new FailureKind[] {
                    FailureKind.CIRCUIT_OPEN,
                    FailureKind.STEP_TIMEOUT,
                    FailureKind.CYCLE_TIMEOUT,
                    FailureKind.SOURCE_BUDGET_EXHAUSTED,
                    FailureKind.WORKLOAD_BUDGET_EXHAUSTED
                }) {
            assertEquals(
                    FailureAction.ABORT,
                    matrix.decide(kind, new FailurePolicyContext(true, true)).action());
        }
    }

    @Test
    void repartitionsOnlyProven422AndBlocksRequiredDependents() {
        assertEquals(
                FailureAction.REPARTITION,
                matrix.decide(
                                FailureKind.WINDOW_TOO_LARGE_HTTP_422,
                                new FailurePolicyContext(false, true))
                        .action());
        assertEquals(
                FailureAction.ABORT,
                matrix.decide(
                                FailureKind.UNCLASSIFIED_HTTP_422,
                                new FailurePolicyContext(false, true))
                        .action());

        final FailureDecision blocked =
                matrix.decide(
                        FailureKind.REQUIRED_DEPENDENCY_FAILED, FailurePolicyContext.exhausted());
        assertEquals(FailureAction.BLOCK, blocked.action());
        assertEquals(ExecutionState.BLOCKED, blocked.terminalState().orElseThrow());
        assertTrue(blocked.blocksDependents());
    }

    @Test
    void separatesConfigAuthFromSourceContractAndOptionalDegradation() {
        assertEquals(
                RuntimeExitCategory.CONFIG_AUTH,
                matrix.decide(FailureKind.AUTHORIZATION, FailurePolicyContext.exhausted())
                        .exitCategory());
        assertEquals(
                RuntimeExitCategory.SOURCE_DQ,
                matrix.decide(FailureKind.CONTRACT_DRIFT, FailurePolicyContext.exhausted())
                        .exitCategory());
        final FailureDecision optional =
                matrix.decide(
                        FailureKind.OPTIONAL_DEPENDENCY_FAILED, FailurePolicyContext.exhausted());
        assertEquals(FailureAction.DEGRADE, optional.action());
        assertFalse(optional.blocksDependents());
        assertTrue(optional.blocksCheckpoint());
    }

    @Test
    void refusesImpossibleDecisions() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new FailureDecision(
                                FailureAction.RETRY,
                                Optional.of(ExecutionState.FAILED),
                                RuntimeExitCategory.SUCCESS,
                                true,
                                false));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new FailureDecision(
                                FailureAction.ABORT,
                                Optional.of(ExecutionState.PUBLISHED),
                                RuntimeExitCategory.SOURCE_DQ,
                                true,
                                true));
    }
}
