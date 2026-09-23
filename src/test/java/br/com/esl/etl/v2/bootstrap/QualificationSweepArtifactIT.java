package br.com.esl.etl.v2.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.plataforma.persistencia.coletas.ColetaTemporalLaboratorySession;
import br.com.esl.etl.v2.plataforma.reconciliacao.sweep.SweepApplicability;
import br.com.esl.etl.v2.plataforma.reconciliacao.sweep.SweepPreviewAssessment;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken;
import java.nio.file.Path;
import java.time.LocalDate;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class QualificationSweepArtifactIT {
    @TempDir Path folder;

    @ParameterizedTest
    @ValueSource(ints = {3, 6})
    @Timeout(240)
    void explicitDayAndRootSetReachFourIndependentCapturesAbsenceConfirmationAndReappearance(
            final int roots) throws Exception {
        final var date = LocalDate.of(2036, 4, roots == 3 ? 2 : 3);
        final var file = QualificationSweepArtifactFixtures.write(folder, date, roots);
        final var program = new LocalCollectionSweepProgram(file, CancellationToken.none());
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            session.controlStatements(30, 100000);
            final var result = program.execute(session, CancellationToken.none());
            assertEquals(4, result.size());
            assertEquals(0, result.get(0).result().candidates());
            assertEquals(1, result.get(1).result().candidates());
            assertEquals(0, result.get(1).result().confirmations());
            assertEquals(1, result.get(2).result().confirmations());
            assertEquals(1, result.get(3).result().reactivated());
            for (final var observation : result) {
                assertEquals(date, observation.proof().snapshot().date());
                assertEquals(4, observation.proof().captures().stream().distinct().count());
                assertEquals(33, observation.preview().size());
                assertTrue(
                        observation.preview().stream()
                                .allMatch(
                                        row ->
                                                row.responsibility().applicability()
                                                                != SweepApplicability.ENABLED
                                                        && row.assessment().disposition()
                                                                == SweepPreviewAssessment
                                                                        .Disposition.BLOCKED));
            }
        }
    }
}
