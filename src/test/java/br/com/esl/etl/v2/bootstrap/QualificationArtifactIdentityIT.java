package br.com.esl.etl.v2.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;

import br.com.esl.etl.v2.plataforma.persistencia.coletas.ColetaTemporalLaboratorySession;
import br.com.esl.etl.v2.plataforma.qualificacao.QualificationGate;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

class QualificationArtifactIdentityIT {
    @TempDir Path folder;

    @Test
    @Timeout(240)
    void keysUnrelatedToOrdinalsReachRelationsFactsAndAllOutputOracles() throws Exception {
        final var paths = QualificationArtifactIdentityFixtures.write(folder);
        final var scenario =
                new LocalArtifactScenario(paths.input(), paths.oracle(), CancellationToken.none());
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            session.controlStatements(30, 100000);
            final var result = scenario.execute(session, CancellationToken.none());
            for (final var output : result.outputs()) {
                assertEquals(QualificationGate.State.PASS_LOCAL, output.gate(), output.toString());
            }
            assertEquals(19, result.outputs().size());
            assertEquals(35, result.scopes().size());
        }
    }
}
