package br.com.esl.etl.v2.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import br.com.esl.etl.v2.plataforma.analitico.SyntheticCollectionSnapshot;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.RelationalSyntheticSource;
import br.com.esl.etl.v2.plataforma.persistencia.analitico.JdbcAnalyticCollectionSweep;
import br.com.esl.etl.v2.plataforma.persistencia.coletas.ColetaTemporalLaboratorySession;
import br.com.esl.etl.v2.plataforma.qualificacao.PinnedLocalJson;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class IntegralSweepEdgesIT {
    @TempDir Path folder;

    enum Fault {
        LOST_PAGE,
        UNKNOWN_KEY,
        SAME_TOTAL_WRONG_ROOT_COUNTS,
        WRONG_OWNER
    }

    @ParameterizedTest
    @EnumSource(Fault.class)
    @Timeout(120)
    void incompleteOrMisownedEvidenceCannotProduceAReceipt(final Fault fault) throws Exception {
        final Path path = IntegralArtifactFixtures.write(folder, false, 2);
        final var input =
                new DeclaredIntegralInputs(
                        PinnedLocalJson.open(path, 16384), CancellationToken.none());
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            session.controlStatements(30, 100000);
            final var run =
                    new AnalyticScenarioRuntime(
                                    session,
                                    Clock.systemUTC(),
                                    AnalyticScenarioObserver.NONE,
                                    input)
                            .start(
                                    UUID.randomUUID(),
                                    2,
                                    input.pageSize(),
                                    AnalyticScenarioRuntime.Fault.NONE);
            final var original = input.sweep().snapshot(run.id());
            final var universe = new ArrayList<>(original.declaredUniverse());
            if (fault == Fault.UNKNOWN_KEY) {
                universe.set(0, new SyntheticCollectionSnapshot.Root("INTEGER:9999991", 3));
            } else if (fault == Fault.SAME_TOTAL_WRONG_ROOT_COUNTS) {
                universe.set(0, new SyntheticCollectionSnapshot.Root(universe.get(0).key(), 4));
                universe.set(1, new SyntheticCollectionSnapshot.Root(universe.get(1).key(), 2));
            }
            final var snapshot =
                    new SyntheticCollectionSnapshot(run.id(), input.start(), 2, false, universe);
            final var captures = new ArrayList<CollectionSweepInput>();
            for (final var capture : input.sweep().inputs(run.id(), CancellationToken.none())) {
                captures.add(
                        new CollectionSweepInput(
                                run.id(), input.start(), snapshot.fingerprint(), capture.source()));
            }
            if (fault == Fault.LOST_PAGE) {
                final String page =
                        Files.readString(folder.resolve("sweep/observation-1-page-1.json"));
                captures.set(
                        0,
                        new CollectionSweepInput(
                                run.id(),
                                input.start(),
                                snapshot.fingerprint(),
                                new RelationalSyntheticSource(number -> number == 1 ? page : "[]")
                                        .withAnalyticCollectionDetails()));
            }
            final var planner = new SweepResponsibilityPlanner();
            final var bindings =
                    new ArrayList<>(
                            planner.bindings(
                                    snapshot.scope().scopeFingerprint(), snapshot.fingerprint()));
            if (fault == Fault.WRONG_OWNER) {
                for (int index = 0; index < bindings.size(); index++) {
                    final var binding = bindings.get(index);
                    if (!binding.parent().isEmpty()) {
                        bindings.set(
                                index,
                                new SweepResponsibilityPlanner.Binding(
                                        binding.id(), "", binding.scope(), binding.snapshot()));
                        break;
                    }
                }
            }
            final var sweep =
                    new LocalAnalyticCollectionSweep(
                            session,
                            run.id(),
                            run.relational(),
                            input.policy(),
                            input.clock(),
                            Clock.systemUTC());
            final long before = session.preparedStatements();
            if (fault == Fault.WRONG_OWNER) {
                final var failure =
                        assertThrows(
                                IllegalArgumentException.class,
                                () ->
                                        sweep.observe(
                                                snapshot,
                                                UUID.randomUUID(),
                                                captures,
                                                bindings,
                                                CancellationToken.none()));
                assertEquals("SWEEP_PLAN_PARENT_CONTRACT", failure.getMessage());
                assertEquals(before, session.preparedStatements());
            } else {
                final var failure =
                        assertThrows(
                                SQLException.class,
                                () ->
                                        sweep.observe(
                                                snapshot,
                                                UUID.randomUUID(),
                                                captures,
                                                bindings,
                                                CancellationToken.none()));
                assertEquals(fault == Fault.LOST_PAGE ? 53775 : 53777, failure.getErrorCode());
            }
            assertEquals(
                    0,
                    IntegralArtifactResilienceIT.count(
                            session,
                            run.id(),
                            "SELECT COUNT_BIG(*) FROM recon.analytic_collection_sweep_cycle WHERE run_id=?"));
            assertEquals(
                    0,
                    IntegralArtifactResilienceIT.count(
                            session,
                            run.id(),
                            "SELECT COUNT_BIG(*) FROM ctl.relational_lab_capture WHERE run_id=(SELECT relational_run"
                                    + " FROM ctl.analytic_lab_source_group WHERE run_id=?)"));
        }
    }

    @Test
    @Timeout(120)
    void completeDeclaredProofReplaysItsReceiptButBothApplyBoundariesRefuseIt() throws Exception {
        final Path path = IntegralArtifactFixtures.write(folder, false, 2);
        final var input =
                new DeclaredIntegralInputs(
                        PinnedLocalJson.open(path, 16384), CancellationToken.none());
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            session.controlStatements(30, 100000);
            final var run =
                    new AnalyticScenarioRuntime(
                                    session,
                                    Clock.systemUTC(),
                                    AnalyticScenarioObserver.NONE,
                                    input)
                            .start(
                                    UUID.randomUUID(),
                                    2,
                                    input.pageSize(),
                                    AnalyticScenarioRuntime.Fault.NONE);
            final var observation =
                    new LocalAnalyticCollectionSweep(
                                    session,
                                    run.id(),
                                    run.relational(),
                                    input.policy(),
                                    input.clock(),
                                    Clock.systemUTC())
                            .observe(
                                    input.sweep().snapshot(run.id()),
                                    UUID.randomUUID(),
                                    input.sweep().inputs(run.id(), CancellationToken.none()),
                                    CancellationToken.none());
            assertEquals(33, observation.preview().size());
            assertNull(observation.result());
            final var proof = observation.proof();
            final var gateway = new JdbcAnalyticCollectionSweep(session);
            assertEquals(
                    proof,
                    gateway.prepare(
                            proof.snapshot(),
                            proof.cycle(),
                            proof.captures(),
                            CancellationToken.none()));
            assertEquals(
                    "ANA_COLLECTION_DECLARED_PREVIEW_ONLY",
                    assertThrows(
                                    IllegalArgumentException.class,
                                    () -> gateway.apply(proof, CancellationToken.none()))
                            .getMessage());
            try (var connection = session.getConnection();
                    var sql =
                            connection.prepareStatement(
                                    "EXEC recon.usp_apply_analytic_collection_sweep ?,?,?,?")) {
                sql.setQueryTimeout(30);
                sql.setString(1, run.id().toString());
                sql.setString(2, proof.cycle().toString());
                sql.setString(3, proof.receiptFingerprint());
                sql.setString(4, "PREVIEW_ELIGIBLE_NO_APPLY_CAPABILITY");
                assertEquals(53861, assertThrows(SQLException.class, sql::execute).getErrorCode());
            }
            assertEquals(
                    0,
                    IntegralArtifactResilienceIT.count(
                            session,
                            run.id(),
                            "SELECT COUNT_BIG(*) FROM recon.analytic_collection_absence WHERE run_id=?"));
        }
    }
}
