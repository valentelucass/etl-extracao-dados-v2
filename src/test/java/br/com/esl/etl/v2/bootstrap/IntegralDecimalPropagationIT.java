package br.com.esl.etl.v2.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class IntegralDecimalPropagationIT {
    @TempDir Path folder;

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    @Timeout(360)
    void exactNumericInputsReachTheirSqlColumnsAndWrongRoundedOracleFails(final boolean alternate)
            throws Exception {
        final var spec =
                new IntegralArtifactFixtures.Spec(alternate, alternate ? 24 : 2, false, true, true);
        final var file = IntegralArtifactFixtures.write(folder, spec);
        final var input =
                new DeclaredIntegralInputs(
                        PinnedLocalJson.open(file, 16384), CancellationToken.none());
        final var correct = IntegralArtifactReplayIT.verifier(folder);
        final var wrong = IntegralArtifactReplayIT.verifier(folder, roundedRasterOracle());
        final var wrongFiscal = IntegralArtifactReplayIT.verifier(folder, wrongFiscalOracle());
        final var started = Instant.now();
        final UUID id = UUID.randomUUID();
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            session.controlStatements(30, 100000);
            final var runtime =
                    new AnalyticScenarioRuntime(
                            session, Clock.systemUTC(), AnalyticScenarioObserver.NONE, input);
            final var run =
                    runtime.start(
                            id, spec.roots(), spec.pageSize(), AnalyticScenarioRuntime.Fault.NONE);
            final var cycle =
                    runtime.capture(
                            run,
                            ExecutionMode.BOOTSTRAP,
                            spec.revision(),
                            false,
                            null,
                            CancellationToken.none());
            IntegralArtifactReplayIT.exact(
                    correct.verifyIntegral(
                            session, run, cycle, input, started, CancellationToken.none()));
            final var counterproof =
                    wrong.verifyIntegral(
                            session, run, cycle, input, started, CancellationToken.none());
            assertEquals(
                    QualificationGate.State.BLOCKED_DEPENDENCY, counterproof.selected().state());
            assertEquals(
                    18,
                    counterproof.outputs().stream()
                            .filter(output -> output.differences() == 0)
                            .count());
            assertTrue(counterproof.outputs().get(12).differences() > 0);
            final var fiscalCounterproof =
                    wrongFiscal.verifyIntegral(
                            session, run, cycle, input, started, CancellationToken.none());
            assertEquals(
                    QualificationGate.State.BLOCKED_DEPENDENCY,
                    fiscalCounterproof.selected().state());
            assertEquals(
                    18,
                    fiscalCounterproof.outputs().stream()
                            .filter(output -> output.differences() == 0)
                            .count());
            assertTrue(fiscalCounterproof.outputs().get(0).differences() > 0);
            final var sweep =
                    new LocalAnalyticCollectionSweep(
                                    session,
                                    id,
                                    run.relational(),
                                    input.policy(),
                                    input.clock(),
                                    Clock.systemUTC())
                            .observe(
                                    input.sweep().snapshot(id),
                                    UUID.randomUUID(),
                                    input.sweep().inputs(id, CancellationToken.none()),
                                    CancellationToken.none());
            assertEquals(33, sweep.preview().size());
            try (var connection = session.getConnection();
                    var statement =
                            connection.prepareStatement(
                                    "SELECT COUNT_BIG(*) FROM core.analytic_raster_stop WHERE run_id=? AND km_percorrido_entrega=?")) {
                statement.setQueryTimeout(30);
                statement.setString(1, id.toString());
                statement.setBigDecimal(
                        2, new java.math.BigDecimal(alternate ? "0.00000002" : "0.00000001"));
                try (var rows = statement.executeQuery()) {
                    assertTrue(rows.next());
                    assertEquals(spec.roots(), rows.getLong(1));
                }
            }
            session.rollback();
            assertEquals(
                    0,
                    IntegralArtifactResilienceIT.count(
                            session,
                            id,
                            "SELECT COUNT_BIG(*) FROM ctl.analytic_lab_run WHERE run_id=?"));
            IntegralArtifactFixtures.save(
                    Path.of("target", "states-decimal-" + spec.tag() + ".json"),
                    IntegralArtifactFixtures.object()
                            .put("families", 11)
                            .put("facts", 5)
                            .put("sqlOutputs", 19)
                            .put("sweepResponsibilities", 33)
                            .put("roots", spec.roots())
                            .put("pageSize", spec.pageSize())
                            .put(
                                    "sql13WrongOracleDifferences",
                                    counterproof.outputs().get(12).differences())
                            .put("rollbackRunRows", 0)
                            .put("fiscalTupleBoundaries", true)
                            .put(
                                    "sql01WrongFiscalDifferences",
                                    fiscalCounterproof.outputs().get(0).differences())
                            .put("sourceCalls", 0));
        }
    }

    private Path roundedRasterOracle() throws Exception {
        return wrongOracle(12, 31, "0.00000000", "raster-decimal");
    }

    private Path wrongFiscalOracle() throws Exception {
        final var columns =
                br.com.esl.etl.v2.plataforma.analitico.AnalyticSqlCatalog.columns(
                        br.com.esl.etl.v2.plataforma.analitico.AnalyticSqlContract.SQL_01);
        final int column =
                java.util.stream.IntStream.range(0, columns.size())
                        .filter(index -> columns.get(index).name().equals("NFS-e/Série"))
                        .findFirst()
                        .orElseThrow();
        return wrongOracle(0, column, "SERIE-INDEPENDENTE-ERRADA", "fiscal-key");
    }

    private Path wrongOracle(
            final int outputIndex, final int column, final String wrongValue, final String tag)
            throws Exception {
        final var oracle =
                (ObjectNode) IntegralArtifactFixtures.read(folder.resolve("oracle.json"));
        final var manifestFile = folder.resolve("outputs/manifest.json");
        final var manifest = (ObjectNode) IntegralArtifactFixtures.read(manifestFile);
        final var output = (ObjectNode) manifest.path("outputs").path(outputIndex);
        assertEquals(outputIndex == 0 ? "SQL-01" : "SQL-13", output.path("id").textValue());
        final var batch = output.path("batches").path(0);
        final var rows =
                (ArrayNode)
                        IntegralArtifactFixtures.read(
                                manifestFile.getParent().resolve(batch.path("file").textValue()));
        // Wrong expected values are authored before any SQL execution.
        ((ArrayNode) rows.path(0)).set(column, JsonNodeFactory.instance.textNode(wrongValue));
        final var badRows = folder.resolve("outputs/wrong-" + tag + ".json");
        IntegralArtifactFixtures.save(badRows, rows);
        ((ArrayNode) output.path("batches"))
                .set(0, IntegralArtifactFixtures.pin(badRows.getParent(), badRows));
        final var badManifest = folder.resolve("outputs/wrong-" + tag + "-manifest.json");
        IntegralArtifactFixtures.save(badManifest, manifest);
        oracle.set("outputs", IntegralArtifactFixtures.pin(folder, badManifest));
        final var result = folder.resolve("wrong-" + tag + "-oracle.json");
        IntegralArtifactFixtures.save(result, oracle);
        return result;
    }
}
