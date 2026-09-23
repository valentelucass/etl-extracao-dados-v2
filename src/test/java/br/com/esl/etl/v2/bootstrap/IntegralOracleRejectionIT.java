package br.com.esl.etl.v2.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.plataforma.controle.ExecutionMode;
import br.com.esl.etl.v2.plataforma.persistencia.coletas.ColetaTemporalLaboratorySession;
import br.com.esl.etl.v2.plataforma.qualificacao.PinnedLocalJson;
import br.com.esl.etl.v2.plataforma.qualificacao.QualificationGate;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

class IntegralOracleRejectionIT {
    @TempDir Path folder;

    enum Error {
        VALUE,
        KEY,
        CARDINALITY,
        PRECISION,
        FACT
    }

    @Test
    @Timeout(240)
    void independentWrongExpectationsNeverPassAgainstTheSamePhysicalCapture() throws Exception {
        final Path inputFile = IntegralArtifactFixtures.write(folder, false, 2);
        final var input =
                new DeclaredIntegralInputs(
                        PinnedLocalJson.open(inputFile, 16384), CancellationToken.none());
        final var valid = IntegralArtifactReplayIT.verifier(folder);
        final var wrong = new LinkedHashMap<Error, QualificationScenarioVerifier>();
        for (final var error : Error.values()) {
            wrong.put(error, IntegralArtifactReplayIT.verifier(folder, wrongOracle(error)));
        }
        // Every wrong value is authored before SQL; comparisons cannot become their source.
        final var started = Instant.now();
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            session.controlStatements(30, 100000);
            final var runtime =
                    new AnalyticScenarioRuntime(
                            session, Clock.systemUTC(), AnalyticScenarioObserver.NONE, input);
            final var run =
                    runtime.start(
                            UUID.randomUUID(),
                            2,
                            input.pageSize(),
                            AnalyticScenarioRuntime.Fault.NONE);
            final var cycle =
                    runtime.capture(
                            run,
                            ExecutionMode.BOOTSTRAP,
                            input.revision(),
                            false,
                            null,
                            CancellationToken.none());
            IntegralArtifactReplayIT.exact(
                    valid.verifyIntegral(
                            session, run, cycle, input, started, CancellationToken.none()));
            final var evidence = IntegralArtifactFixtures.array();
            for (final var entry : wrong.entrySet()) {
                if (entry.getKey() == Error.FACT) {
                    final var failure =
                            assertThrows(
                                    SQLException.class,
                                    () ->
                                            entry.getValue()
                                                    .verifyIntegral(
                                                            session,
                                                            run,
                                                            cycle,
                                                            input,
                                                            started,
                                                            CancellationToken.none()));
                    assertEquals("LOCAL_FACT_ORACLE_DIVERGENCE_MAT03", failure.getMessage());
                    evidence.addObject()
                            .put("mutation", entry.getKey().name())
                            .put("reason", failure.getMessage());
                } else {
                    final var result =
                            entry.getValue()
                                    .verifyIntegral(
                                            session,
                                            run,
                                            cycle,
                                            input,
                                            started,
                                            CancellationToken.none());
                    assertEquals(
                            QualificationGate.State.BLOCKED_DEPENDENCY, result.selected().state());
                    assertEquals(
                            18,
                            result.outputs().stream()
                                    .filter(output -> output.differences() == 0)
                                    .count());
                    assertTrue(result.outputs().get(1).differences() > 0);
                    evidence.addObject()
                            .put("mutation", entry.getKey().name())
                            .put("differences", result.outputs().get(1).differences());
                }
            }
            IntegralArtifactReplayIT.exact(
                    valid.verifyIntegral(
                            session, run, cycle, input, started, CancellationToken.none()));
            IntegralArtifactFixtures.save(
                    Path.of("target", "integral-oracle-counterproofs.json"), evidence);
        }
    }

    private Path wrongOracle(final Error error) throws Exception {
        final String label = error.name().toLowerCase(java.util.Locale.ROOT);
        final var root = (ObjectNode) IntegralArtifactFixtures.read(folder.resolve("oracle.json"));
        final boolean fact = error == Error.FACT;
        final String member = fact ? "facts" : "outputs";
        final Path manifestFile = folder.resolve(root.path(member).path("file").asText());
        final var manifest = (ObjectNode) IntegralArtifactFixtures.read(manifestFile);
        ObjectNode target = null;
        for (final var entry : manifest.path(fact ? "facts" : "outputs")) {
            if (entry.path("id").asText().equals(fact ? "MAT03" : "SQL-02")) {
                target = (ObjectNode) entry;
            }
        }
        final var selected = java.util.Objects.requireNonNull(target);
        final var pin = fact ? selected : selected.path("batches").path(0);
        final var rows =
                (ArrayNode)
                        IntegralArtifactFixtures.read(
                                manifestFile.getParent().resolve(pin.path("file").asText()));
        if (fact) {
            ((ObjectNode) rows.path(0)).put("amount", "9999.00000000");
        } else if (error == Error.CARDINALITY) {
            rows.remove(1);
            selected.put("rows", 1);
        } else {
            final var row = (ArrayNode) rows.path(0);
            row.set(
                    error == Error.KEY ? 1 : 18,
                    error == Error.KEY
                            ? JsonNodeFactory.instance.numberNode(9999991L)
                            : JsonNodeFactory.instance.textNode(
                                    error == Error.PRECISION ? "143.25000001" : "9999.00000000"));
        }
        final Path badRows = manifestFile.getParent().resolve("wrong-" + label + "-rows.json");
        IntegralArtifactFixtures.save(badRows, rows);
        final var newPin = IntegralArtifactFixtures.pin(manifestFile.getParent(), badRows);
        if (fact) {
            selected.put("file", newPin.path("file").asText())
                    .put("sha256", newPin.path("sha256").asText());
        } else {
            ((ArrayNode) selected.path("batches")).set(0, newPin);
        }
        final Path badManifest =
                manifestFile.getParent().resolve("wrong-" + label + "-manifest.json");
        IntegralArtifactFixtures.save(badManifest, manifest);
        root.set(member, IntegralArtifactFixtures.pin(folder, badManifest));
        final Path result = folder.resolve("wrong-" + label + "-oracle.json");
        IntegralArtifactFixtures.save(result, root);
        return result;
    }
}
