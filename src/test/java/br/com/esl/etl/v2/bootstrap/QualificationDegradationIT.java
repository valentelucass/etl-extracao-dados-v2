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
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class QualificationDegradationIT {
    @ParameterizedTest
    @EnumSource(
            value = AnalyticScenarioRuntime.Fault.class,
            names = "NONE",
            mode = EnumSource.Mode.EXCLUDE)
    @Timeout(180)
    void failedInputBlocksItsDependentScopesWhileCaptacaoStillComparesExactly(
            final AnalyticScenarioRuntime.Fault fault) throws Exception {
        final var file =
                Path.of("src/main/resources/qualification-laboratory/outputs.synthetic.json");
        final var hash = QualificationJson.sha256(file);
        final var binding =
                new QualificationComparator.Binding(
                        hash, hash, hash, "INDEPENDENT_SYNTHETIC_RULES_V1");
        final var verifier =
                new QualificationScenarioVerifier(
                        QualificationOracles.read(file), binding, binding);
        final var started = Instant.now();
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            final var runtime = new AnalyticScenarioRuntime(session, Clock.systemUTC());
            final var run = runtime.start(java.util.UUID.randomUUID(), 2, 2, fault);
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
            assertEquals(QualificationGate.State.BLOCKED_DEPENDENCY, result.selected().state());
            assertEquals(QualificationGate.State.PASS_LOCAL, result.scopes().get("SQL-06").state());
            assertEquals(19, result.outputs().size());
            assertTrue(
                    result.outputs().stream().allMatch(value -> value.differences() == 0),
                    () ->
                            result.outputs().stream()
                                    .filter(value -> value.differences() > 0)
                                    .toList()
                                    .toString());
            final String blocked =
                    switch (fault) {
                        case RASTER_INCOMPLETE -> "SQL-13";
                        case MANIFEST_FLEET_MISSING -> "SQL-08";
                        case FINANCIAL_REFERENCE_MISSING -> "MAT03";
                        case COLLECTION_SNAPSHOT_INVALID -> "SQL-03";
                        default -> throw new IllegalArgumentException("QUAL_TEST_FAULT");
                    };
            assertEquals(
                    QualificationGate.State.BLOCKED_DEPENDENCY,
                    result.scopes().get(blocked).state());
            assertEquals(AnalyticScenarioRuntime.START, cycle.status().nextDate());
            if (fault == AnalyticScenarioRuntime.Fault.RASTER_INCOMPLETE) {
                final var mutant = QualificationJson.read(file, 524288);
                ((com.fasterxml.jackson.databind.node.ObjectNode)
                                mutant.path("contracts").get(5).path("columns").get(2))
                        .put("value", "SYNTHETIC-WRONG-CAP");
                final var divergent =
                        new QualificationScenarioVerifier(
                                        QualificationOracles.parse(mutant), binding, binding)
                                .verify(
                                        session,
                                        run,
                                        List.of(cycle),
                                        started,
                                        List.of(AnalyticSqlContract.values()),
                                        CancellationToken.none());
                assertEquals(QualificationGate.State.FAILED, divergent.selected().state());
                assertEquals(
                        QualificationGate.State.BLOCKED_DEPENDENCY,
                        divergent.scopes().get("SQL-13").state());
                assertTrue(divergent.outputs().get(5).differences() > 0);
            }
        }
    }
}
