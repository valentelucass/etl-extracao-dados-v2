package br.com.esl.etl.v2.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.plataforma.analitico.AnalyticSqlContract;
import br.com.esl.etl.v2.plataforma.controle.ExecutionMode;
import br.com.esl.etl.v2.plataforma.persistencia.coletas.ColetaTemporalLaboratorySession;
import br.com.esl.etl.v2.plataforma.qualificacao.QualificationComparator;
import br.com.esl.etl.v2.plataforma.qualificacao.QualificationGate;
import br.com.esl.etl.v2.plataforma.qualificacao.QualificationJson;
import br.com.esl.etl.v2.plataforma.qualificacao.QualificationOracles;
import br.com.esl.etl.v2.plataforma.qualificacao.QualificationTopology;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

class QualificationScenarioVerifierIT {
    @Test
    @Timeout(240)
    void oneVerifierConsumesAllNineteenOutputsAndThirtyFiveUniqueDependencyScopesAtSixteenRoots()
            throws Exception {
        final var file =
                Path.of("src/main/resources/qualification-laboratory/outputs.synthetic.json");
        final var binding =
                new QualificationComparator.Binding(
                        QualificationJson.sha256(Path.of("pom.xml")),
                        QualificationJson.sha256(
                                Path.of(
                                        "src/main/resources/analytic-laboratory/fat.synthetic.json")),
                        QualificationJson.sha256(file),
                        "INDEPENDENT_SYNTHETIC_RULES_V1");
        final var verifier =
                new QualificationScenarioVerifier(
                        QualificationOracles.read(file), binding, binding);
        final var started = Instant.now();
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            final var runtime = new AnalyticScenarioRuntime(session, Clock.systemUTC());
            final var run = runtime.start(16, 4);
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
            assertEquals(35, result.scopes().size());
            assertEquals(19, result.outputs().size());
            assertEquals(
                    QualificationGate.State.PASS_LOCAL,
                    result.selected().state(),
                    () ->
                            result.outputs().stream()
                                    .filter(r -> r.differences() > 0)
                                    .toList()
                                    .toString());
            assertTrue(result.retainedTechnicalRecords() <= 197);
            final var missingFleet = new HashMap<>(result.scopes());
            missingFleet.put(
                    "SQL-16",
                    new QualificationGate(
                            "SQL-16",
                            QualificationGate.State.FAILED,
                            "INTENTIONAL_REFERENCE_DRIFT",
                            "ORACLE"));
            final var gates = QualificationTopology.evaluate(missingFleet);
            assertEquals(QualificationGate.State.BLOCKED_DEPENDENCY, gates.get("MAT05").state());
            assertEquals(QualificationGate.State.BLOCKED_DEPENDENCY, gates.get("SQL-08").state());
            assertEquals(QualificationGate.State.PASS_LOCAL, gates.get("SQL-06").state());
        }
    }
}
