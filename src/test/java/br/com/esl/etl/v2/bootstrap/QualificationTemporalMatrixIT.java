package br.com.esl.etl.v2.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.plataforma.persistencia.coletas.ColetaTemporalLaboratorySession;
import br.com.esl.etl.v2.plataforma.qualificacao.QualificationConfiguration;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

class QualificationTemporalMatrixIT {
    @Test
    @Timeout(180)
    void plansBindRealCapturesAndTerminalFrontiersAcrossMatrixLateDataGapAndDst() throws Exception {
        final var config =
                QualificationConfiguration.read(
                        Path.of(
                                "src/main/resources/qualification-laboratory/config.synthetic.json"));
        final var metrics = new QualificationMetrics(config, () -> {});
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            session.controlStatements(config.querySeconds(), config.maximumJdbcCalls());
            final var result =
                    new QualificationTemporalMatrix(session, metrics, CancellationToken.none())
                            .execute();
            assertTrue(result.path("passed").booleanValue());
            assertEquals(5, result.path("workloads").size());
            assertTrue(result.path("frontiers").path("gapClosureAdvancesBoth").booleanValue());
            assertEquals(
                    23, result.path("civilAndAdmission").path("actualPartitionHours").intValue());
            final var counters = metrics.snapshot();
            assertTrue(counters.records() > 40);
            assertTrue(
                    counters.inputs().stream()
                            .allMatch(row -> row.inFlight() == 0 && row.retainedPageBytes() == 0));
            assertEquals(0, session.openControlledStatements());
        }
    }
}
