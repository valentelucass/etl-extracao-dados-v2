package br.com.esl.etl.v2.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.plataforma.persistencia.coletas.ColetaTemporalLaboratorySession;
import br.com.esl.etl.v2.plataforma.qualificacao.QualificationGate;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken;
import java.nio.file.Path;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class QualificationArtifactScenarioIT {
    @TempDir Path folder;

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    @Timeout(240)
    void independentlySuppliedInputsAndOraclesCrossAllNineteenContractsAndFiveGrains(
            final boolean alternate) throws Exception {
        final var files = QualificationArtifactScenarioFixtures.write(folder, alternate, 2);
        final var scenario =
                new LocalArtifactScenario(files.input(), files.oracle(), CancellationToken.none());
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            session.controlStatements(30, 100000);
            final var result = scenario.execute(session, CancellationToken.none());
            assertEquals(19, result.outputs().size());
            assertEquals(35, result.scopes().size());
            for (final var comparison : result.outputs()) {
                assertTrue(comparison.differences() == 0, comparison::toString);
            }
            assertEquals(QualificationGate.State.PASS_LOCAL, result.selected().state());
        }
    }
}
