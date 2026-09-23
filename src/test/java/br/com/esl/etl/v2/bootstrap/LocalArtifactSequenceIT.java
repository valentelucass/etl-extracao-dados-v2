package br.com.esl.etl.v2.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;

import br.com.esl.etl.v2.plataforma.persistencia.coletas.ColetaTemporalLaboratorySession;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class LocalArtifactSequenceIT {
    @TempDir Path folder;

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    @Timeout(720)
    void declaredStepsReuseSqlStateAndCompareEveryOutput(final boolean alternate) throws Exception {
        final var file = IntegralSequenceFixtures.write(folder, alternate, 2);
        final var sequence = new LocalArtifactSequence(file, CancellationToken.none());
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            session.controlStatements(60, 100000);
            final var results =
                    sequence.execute(
                            session,
                            UUID.randomUUID(),
                            AnalyticScenarioObserver.NONE,
                            CancellationToken.none(),
                            result -> {});
            assertEquals(3, results.size());
            for (final var result : results) {
                IntegralArtifactReplayIT.exact(result.comparison());
            }
            assertEquals(results.get(0).sourceRevision(), results.get(1).sourceRevision());
            assertEquals(results.get(0).sourceRevision() + 1, results.get(2).sourceRevision());
        }
    }
}
