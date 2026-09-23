package br.com.esl.etl.v2.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.plataforma.analitico.AnalyticScenarioVariant;
import br.com.esl.etl.v2.plataforma.analitico.AnalyticSqlContract;
import br.com.esl.etl.v2.plataforma.controle.ExecutionMode;
import br.com.esl.etl.v2.plataforma.persistencia.coletas.ColetaTemporalLaboratorySession;
import br.com.esl.etl.v2.plataforma.qualificacao.QualificationComparator;
import br.com.esl.etl.v2.plataforma.qualificacao.QualificationGate;
import br.com.esl.etl.v2.plataforma.qualificacao.QualificationJson;
import br.com.esl.etl.v2.plataforma.qualificacao.QualificationOracles;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

class QualificationVariantsIT {
    @Test
    @Timeout(180)
    void populatedPerformanceLocationAndNullUserTraverseCapturesAndAllNineteenOracles()
            throws Exception {
        final var file =
                Path.of("src/main/resources/qualification-laboratory/outputs.synthetic.json");
        final String hash = QualificationJson.sha256(file);
        final var binding =
                new QualificationComparator.Binding(
                        hash, hash, hash, "INDEPENDENT_SYNTHETIC_RULES_V1");
        final var verifier =
                new QualificationScenarioVerifier(
                        QualificationOracles.read(file), binding, binding);
        final var started = Instant.now();
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            final var runtime =
                    new AnalyticScenarioRuntime(
                            session,
                            Clock.systemUTC(),
                            AnalyticScenarioObserver.NONE,
                            AnalyticScenarioVariant.VALUES_AND_NULLS);
            final var run =
                    runtime.start(UUID.randomUUID(), 2, 2, AnalyticScenarioRuntime.Fault.NONE);
            final var cycle =
                    runtime.capture(
                            run, ExecutionMode.BOOTSTRAP, 1, false, null, CancellationToken.none());
            final var result =
                    verifier.verify(
                            session,
                            run,
                            List.of(cycle),
                            started,
                            List.of(AnalyticSqlContract.values()),
                            CancellationToken.none());
            assertTrue(
                    result.outputs().stream().allMatch(row -> row.differences() == 0),
                    () ->
                            result.outputs().stream()
                                    .filter(row -> row.differences() > 0)
                                    .toList()
                                    .toString());
            assertEquals(QualificationGate.State.PASS_LOCAL, result.selected().state());
            assertEquals(19, result.outputs().size());
            assertEquals(35, result.scopes().size());
        }
    }
}
