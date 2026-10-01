package br.com.esl.etl.v2.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SequenceAgendaPlanTest {
    @TempDir Path directory;

    @Test
    void plannedFamilyWindowAndExecutionIdentityRemainStableWithoutSql() throws Exception {
        final var sequence =
                new LocalArtifactSequence(
                        IntegralSequenceFixtures.scheduled(directory, false, 2),
                        CancellationToken.none());
        final var step = sequence.steps().get(0);
        final var inputs = new DeclaredIntegralInputs(step.input(), CancellationToken.none());
        final var agenda = new SequenceAgenda(step.schedule(), step.mode(), inputs);

        final var collection = agenda.window("COL");
        final var freight = agenda.window("FRE");
        assertEquals(inputs.captureDate(), collection.dates().startInclusive());
        assertEquals(collection.dates(), freight.dates());
        assertThrows(IllegalArgumentException.class, () -> agenda.window("UNKNOWN"));

        final var run = UUID.fromString("00000000-0000-0000-0000-000000000101");
        final var execution = agenda.execution(run, step.id(), "COL");
        assertEquals(execution, agenda.execution(run, step.id(), "COL"));
        assertNotEquals(execution, agenda.execution(run, step.id(), "FRE"));
        assertNotEquals(execution, agenda.execution(run, "later", "COL"));
    }
}
