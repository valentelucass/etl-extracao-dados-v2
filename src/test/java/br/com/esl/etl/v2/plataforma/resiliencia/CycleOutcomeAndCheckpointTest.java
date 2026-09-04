package br.com.esl.etl.v2.plataforma.resiliencia;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.plataforma.controle.ExecutionState;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class CycleOutcomeAndCheckpointTest {

    @Test
    void keepsIndependentPublicationButDerivesFailureExitOrderIndependently() {
        final FailurePolicyMatrix matrix = new FailurePolicyMatrix();
        final FailureDecision sourceFailure =
                matrix.decide(FailureKind.SQL_FAILURE, FailurePolicyContext.exhausted());
        final FailureDecision cancellation =
                matrix.decide(FailureKind.CANCELLATION, FailurePolicyContext.exhausted());

        final CycleOutcomeAccumulator firstOrder = new CycleOutcomeAccumulator();
        firstOrder.recordPublished();
        firstOrder.recordDecision(cancellation);
        firstOrder.recordDecision(sourceFailure);
        final CycleOutcomeAccumulator reverseOrder = new CycleOutcomeAccumulator();
        reverseOrder.recordDecision(sourceFailure);
        reverseOrder.recordDecision(cancellation);
        reverseOrder.recordPublished();

        final CycleOutcome first = firstOrder.snapshot();
        final CycleOutcome reverse = reverseOrder.snapshot();
        assertEquals(1, first.publishedEntities());
        assertEquals(ExecutionState.FAILED, first.state());
        assertEquals(RuntimeExitCategory.SOURCE_DQ, first.exitCategory());
        assertEquals(first, reverse);
    }

    @Test
    void partialEntityNeverAdvancesItsCheckpointAndResolvedRetryCanPublish() {
        final CheckpointDecisionPolicy checkpointPolicy = new CheckpointDecisionPolicy();
        final FailureDecision retry =
                new FailurePolicyMatrix()
                        .decide(FailureKind.RATE_LIMIT, new FailurePolicyContext(true, false));

        assertFalse(
                checkpointPolicy.canAdvanceEntityCheckpoint(
                        ExecutionState.PUBLISHED, true, true, Optional.of(retry)));
        assertFalse(
                checkpointPolicy.canAdvanceEntityCheckpoint(
                        ExecutionState.DEGRADED, true, true, Optional.empty()));
        assertFalse(
                checkpointPolicy.canAdvanceEntityCheckpoint(
                        ExecutionState.PUBLISHED, false, true, Optional.empty()));
        assertTrue(
                checkpointPolicy.canAdvanceEntityCheckpoint(
                        ExecutionState.PUBLISHED, true, true, Optional.empty()));
    }

    @Test
    void refusesIncoherentCycleSummary() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new CycleOutcome(
                                1,
                                0,
                                1,
                                0,
                                0,
                                0,
                                ExecutionState.PUBLISHED,
                                RuntimeExitCategory.SUCCESS));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new CycleOutcome(
                                0,
                                0,
                                1,
                                0,
                                1,
                                0,
                                ExecutionState.FAILED,
                                RuntimeExitCategory.CANCELLED));
    }

    @Test
    void acceptsEveryCoherentCycleStateAndRejectsNonTerminalState() {
        assertEquals(
                ExecutionState.PLANNED,
                new CycleOutcome(
                                0,
                                0,
                                0,
                                0,
                                0,
                                0,
                                ExecutionState.PLANNED,
                                RuntimeExitCategory.SUCCESS)
                        .state());
        assertEquals(
                ExecutionState.PUBLISHED,
                new CycleOutcome(
                                1,
                                0,
                                0,
                                0,
                                0,
                                0,
                                ExecutionState.PUBLISHED,
                                RuntimeExitCategory.SUCCESS)
                        .state());
        assertEquals(
                ExecutionState.DEGRADED,
                new CycleOutcome(
                                1,
                                1,
                                0,
                                0,
                                0,
                                0,
                                ExecutionState.DEGRADED,
                                RuntimeExitCategory.DEGRADED)
                        .state());
        assertEquals(
                ExecutionState.FAILED,
                new CycleOutcome(
                                1,
                                0,
                                1,
                                0,
                                0,
                                0,
                                ExecutionState.FAILED,
                                RuntimeExitCategory.SOURCE_DQ)
                        .state());
        assertEquals(
                ExecutionState.CANCELLED,
                new CycleOutcome(
                                1,
                                0,
                                0,
                                0,
                                1,
                                0,
                                ExecutionState.CANCELLED,
                                RuntimeExitCategory.CANCELLED)
                        .state());
        assertEquals(
                ExecutionState.SKIPPED,
                new CycleOutcome(
                                0,
                                0,
                                0,
                                0,
                                0,
                                1,
                                ExecutionState.SKIPPED,
                                RuntimeExitCategory.SUCCESS)
                        .state());
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new CycleOutcome(
                                0,
                                0,
                                0,
                                0,
                                0,
                                0,
                                ExecutionState.EXTRACTING,
                                RuntimeExitCategory.SUCCESS));
    }

    @Test
    void rejectsEachNegativeCycleCounter() {
        for (int index = 0; index < 6; index++) {
            final long[] counts = new long[6];
            counts[index] = -1;
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            new CycleOutcome(
                                    counts[0],
                                    counts[1],
                                    counts[2],
                                    counts[3],
                                    counts[4],
                                    counts[5],
                                    ExecutionState.PLANNED,
                                    RuntimeExitCategory.SUCCESS));
        }
    }
}
