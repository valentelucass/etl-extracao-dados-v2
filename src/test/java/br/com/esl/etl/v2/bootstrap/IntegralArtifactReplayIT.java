package br.com.esl.etl.v2.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.plataforma.controle.ExecutionMode;
import br.com.esl.etl.v2.plataforma.persistencia.coletas.ColetaTemporalLaboratorySession;
import br.com.esl.etl.v2.plataforma.qualificacao.DeclaredWireRows;
import br.com.esl.etl.v2.plataforma.qualificacao.PinnedLocalJson;
import br.com.esl.etl.v2.plataforma.qualificacao.QualificationComparator;
import br.com.esl.etl.v2.plataforma.qualificacao.QualificationGate;
import br.com.esl.etl.v2.plataforma.qualificacao.QualificationJson;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

class IntegralArtifactReplayIT {
    @TempDir Path folder;

    @Test
    @Timeout(300)
    void explicitRevisionReplaysExactlyAndLaterDeclaredValuesReachEveryDependentConsumer()
            throws Exception {
        final Path first = folder.resolve("first"), next = folder.resolve("next");
        final Path firstFile = IntegralArtifactFixtures.write(first, false, 2);
        final Path nextFile =
                IntegralArtifactFixtures.write(
                        next, new IntegralArtifactFixtures.Spec(false, 2, true));
        final var input =
                new DeclaredIntegralInputs(
                        PinnedLocalJson.open(firstFile, 16384), CancellationToken.none());
        final var advanced =
                new DeclaredIntegralInputs(
                        PinnedLocalJson.open(nextFile, 16384), CancellationToken.none());
        final var initialOracle = verifier(first);
        final var advancedOracle = verifier(next);
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
            final var cycles = new ArrayList<AnalyticScenarioRuntime.Cycle>();
            final var initial =
                    runtime.capture(
                            run,
                            ExecutionMode.BOOTSTRAP,
                            input.revision(),
                            false,
                            null,
                            CancellationToken.none());
            cycles.add(initial);
            exact(
                    initialOracle.verifyIntegral(
                            session, run, cycles, input, started, CancellationToken.none()));
            final var replay =
                    runtime.capture(
                            run,
                            ExecutionMode.REPLAY,
                            input.revision() + 1,
                            false,
                            initial,
                            CancellationToken.none());
            cycles.add(replay);
            assertEquals(input.revision(), replay.sourceRevision());
            exact(
                    initialOracle.verifyIntegral(
                            session, run, cycles, input, started, CancellationToken.none()));
            final var later =
                    new AnalyticScenarioRuntime(
                                    session,
                                    Clock.systemUTC(),
                                    AnalyticScenarioObserver.NONE,
                                    advanced)
                            .capture(
                                    run,
                                    ExecutionMode.BACKFILL,
                                    advanced.revision(),
                                    false,
                                    replay,
                                    CancellationToken.none());
            cycles.add(later);
            exact(
                    advancedOracle.verifyIntegral(
                            session, run, cycles, advanced, started, CancellationToken.none()));
            assertEquals(
                    2,
                    IntegralArtifactResilienceIT.count(
                            session,
                            run.expansion(),
                            "SELECT COUNT_BIG(*) FROM mart.expansion_lab_revenue WHERE run_id=? AND revenue_value=166.37500000"));
            IntegralArtifactFixtures.save(
                    Path.of("target", "integral-replay.json"),
                    IntegralArtifactFixtures.object()
                            .put("cycles", 3)
                            .put("outputsPerCycle", 19)
                            .put("independentFactsPerCycle", 5)
                            .put("originalRevenue", "143.25000000")
                            .put("laterRevenue", "166.37500000")
                            .put("replayedSourceRevision", 3)
                            .put("laterSourceRevision", 4));
        }
    }

    static QualificationScenarioVerifier verifier(final Path folder) throws Exception {
        return verifier(folder, folder.resolve("oracle.json"));
    }

    static QualificationScenarioVerifier verifier(final Path folder, final Path file)
            throws Exception {
        final var oracle = QualificationJson.read(file, 16384);
        final var binding =
                new QualificationComparator.Binding(
                        QualificationJson.digest(oracle, "runtimeSha256"),
                        QualificationJson.digest(oracle, "inputSha256"),
                        QualificationJson.sha256(file),
                        "INDEPENDENT_SYNTHETIC_RULES_V1");
        return new QualificationScenarioVerifier(
                new DeclaredSqlOracles(
                        PinnedLocalJson.reference(folder, oracle.path("outputs"), 524288),
                        CancellationToken.none()),
                binding,
                new DeclaredWireRows(
                        PinnedLocalJson.reference(folder, oracle.path("wire"), 262144)),
                new LocalFactOracle(
                        PinnedLocalJson.reference(folder, oracle.path("facts"), 16384)));
    }

    static void exact(final QualificationScenarioVerifier.Result result) {
        assertEquals(19, result.outputs().size());
        for (final var output : result.outputs()) {
            assertTrue(output.differences() == 0, output::toString);
        }
        assertEquals(QualificationGate.State.PASS_LOCAL, result.selected().state());
    }
}
