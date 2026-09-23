package br.com.esl.etl.v2.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import br.com.esl.etl.v2.plataforma.controle.ExecutionMode;
import org.junit.jupiter.api.Test;

class AnalyticScenarioReplayLineageTest {
    @Test
    void replayPreservesTheOriginalBootstrapRevisionAcrossIntermediateCycles() {
        final int bootstrap =
                AnalyticScenarioRuntime.bootstrapRevision(ExecutionMode.BOOTSTRAP, 17, null);
        final int incremental =
                AnalyticScenarioRuntime.bootstrapRevision(ExecutionMode.INCREMENTAL, 31, bootstrap);
        final int backfill =
                AnalyticScenarioRuntime.bootstrapRevision(ExecutionMode.BACKFILL, 44, incremental);
        final int replay =
                AnalyticScenarioRuntime.bootstrapRevision(ExecutionMode.REPLAY, 58, backfill);

        assertEquals(17, bootstrap);
        assertEquals(17, incremental);
        assertEquals(17, backfill);
        assertEquals(17, replay);
    }

    @Test
    void nonBootstrapCycleRequiresAnOriginalBootstrapLineage() {
        assertThrows(
                IllegalArgumentException.class,
                () -> AnalyticScenarioRuntime.bootstrapRevision(ExecutionMode.REPLAY, 2, null));
    }
}
