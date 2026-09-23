package br.com.esl.etl.v2.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.plataforma.persistencia.coletas.ColetaTemporalLaboratorySession;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken;
import br.com.esl.etl.v2.plataforma.resiliencia.ExecutionDeadlines;
import br.com.esl.etl.v2.plataforma.resiliencia.ResilienceCancelledException;
import br.com.esl.etl.v2.plataforma.resiliencia.ResilienceTimeoutException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.sql.SQLException;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class IntegralArtifactResilienceIT {
    @TempDir Path folder;

    enum Fault {
        CANCEL,
        DEADLINE,
        LEASE,
        PIN_DRIFT
    }

    @ParameterizedTest
    @EnumSource(Fault.class)
    @Timeout(180)
    void intermediateSourceFailureCannotMaterializeOrSealTheCompleteChain(final Fault fault)
            throws Exception {
        final Path file = IntegralArtifactFixtures.write(folder, false, 2);
        final var manifest = IntegralArtifactFixtures.read(file);
        final Path captureFile =
                folder.resolve(manifest.path("sources").path("MAN").path("file").asText());
        final var capture = IntegralArtifactFixtures.read(captureFile);
        final Path page =
                captureFile
                        .getParent()
                        .resolve(capture.path("pages").path(0).path("file").asText());
        final var injected = new AtomicBoolean();
        final var cancelled = new AtomicBoolean();
        final var ticker = new AtomicLong();
        final var deadline =
                ExecutionDeadlines.start(Duration.ofSeconds(60), ticker::get, cancelled::get);
        final CancellationToken token =
                () -> {
                    deadline.checkpointCycle();
                    return false;
                };
        final var scenario = new LocalArtifactScenario(file, folder.resolve("oracle.json"), token);
        final UUID run = UUID.randomUUID();
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            session.controlStatements(30, 100000);
            final AnalyticScenarioObserver observer =
                    new AnalyticScenarioObserver() {
                        @Override
                        public AnalyticScenarioObserver forInput(final Input input) {
                            if (input != Input.MAN) {
                                return NONE;
                            }
                            return new AnalyticScenarioObserver() {
                                @Override
                                public void beforeFetch() {
                                    if (fault == Fault.PIN_DRIFT
                                            && injected.compareAndSet(false, true)) {
                                        try {
                                            Files.writeString(
                                                    page, "\n", StandardOpenOption.APPEND);
                                        } catch (final java.io.IOException failure) {
                                            throw new java.io.UncheckedIOException(failure);
                                        }
                                    }
                                }

                                @Override
                                public void batchStaged(final int records) {
                                    if (fault == Fault.PIN_DRIFT
                                            || !injected.compareAndSet(false, true)) {
                                        return;
                                    }
                                    switch (fault) {
                                        case CANCEL -> cancelled.set(true);
                                        case DEADLINE ->
                                                ticker.set(Duration.ofSeconds(61).toNanos());
                                        case LEASE -> releaseLease(session, run);
                                        default ->
                                                throw new IllegalStateException("UNEXPECTED_FAULT");
                                    }
                                }
                            };
                        }
                    };
            final var failure =
                    assertThrows(
                            Exception.class,
                            () -> scenario.execute(session, token, observer, run, () -> {}));
            assertTrue(
                    injected.get(),
                    "The requested fault must be reached after the expansion captures");
            final Class<? extends Throwable> expected =
                    switch (fault) {
                        case CANCEL -> ResilienceCancelledException.class;
                        case DEADLINE -> ResilienceTimeoutException.class;
                        case LEASE -> SQLException.class;
                        case PIN_DRIFT -> IllegalArgumentException.class;
                    };
            assertTrue(
                    causedBy(
                            failure, expected, fault == Fault.PIN_DRIFT ? "LOCAL_PIN_BYTES" : null),
                    () -> "Unexpected failure: " + failure.getClass().getSimpleName());
            assertEquals(
                    fault == Fault.LEASE ? 0 : 4,
                    count(
                            session,
                            run,
                            "SELECT COUNT_BIG(*) FROM ctl.expansion_lab_capture WHERE run_id=(SELECT expansion_run"
                                    + " FROM ctl.analytic_lab_source_group WHERE run_id=?)"));
            assertEquals(
                    0,
                    count(
                            session,
                            run,
                            "SELECT COUNT_BIG(*) FROM mart.analytic_freight_operational WHERE run_id=?"));
            assertEquals(
                    0,
                    count(
                            session,
                            run,
                            "SELECT COUNT_BIG(*) FROM recon.analytic_collection_sweep_cycle WHERE run_id=?"));
            assertEquals(0, session.openControlledStatements());
            session.rollback();
            assertEquals(
                    0,
                    count(
                            session,
                            run,
                            "SELECT COUNT_BIG(*) FROM ctl.analytic_lab_run WHERE run_id=?"));
            IntegralArtifactFixtures.save(
                    Path.of(
                            "target",
                            "integral-fault-"
                                    + fault.name().toLowerCase(java.util.Locale.ROOT)
                                    + ".json"),
                    IntegralArtifactFixtures.object()
                            .put("injectedAfterExpansionCaptures", 4)
                            .put("expectedCause", expected.getSimpleName())
                            .put("dependentFacts", 0)
                            .put("sweepReceipts", 0)
                            .put("runRowsAfterRollback", 0));
        }
    }

    private static void releaseLease(
            final ColetaTemporalLaboratorySession session, final UUID run) {
        try {
            assertEquals(
                    4,
                    count(
                            session,
                            run,
                            "SELECT COUNT_BIG(*) FROM ctl.expansion_lab_capture WHERE run_id=(SELECT expansion_run"
                                    + " FROM ctl.analytic_lab_source_group WHERE run_id=?)"));
        } catch (final SQLException failure) {
            throw new IllegalStateException(failure);
        }
        try (var connection = session.getConnection();
                var sql =
                        connection.prepareStatement(
                                "UPDATE l SET released_at_utc=SYSUTCDATETIME() FROM ctl.execution_lease l"
                                        + " JOIN ctl.execution_partition p ON p.current_execution_id=l.execution_id"
                                        + " WHERE p.source_instance=? AND p.tenant_scope=?"
                                        + " AND p.current_state='EXTRACTING' AND l.released_at_utc IS NULL")) {
            sql.setQueryTimeout(10);
            sql.setString(1, "SYNTHETIC_CARRIER_A");
            sql.setString(2, "SYNTHETIC_WEST");
            assertEquals(1, sql.executeUpdate());
        } catch (final SQLException failure) {
            throw new IllegalStateException(failure);
        }
    }

    static long count(
            final ColetaTemporalLaboratorySession session, final UUID run, final String query)
            throws SQLException {
        try (var connection = session.getConnection();
                var sql = connection.prepareStatement(query)) {
            sql.setQueryTimeout(30);
            sql.setString(1, run.toString());
            try (var row = sql.executeQuery()) {
                assertTrue(row.next());
                return row.getLong(1);
            }
        }
    }

    private static boolean causedBy(
            final Throwable failure, final Class<? extends Throwable> type, final String reason) {
        for (Throwable cursor = failure; cursor != null; cursor = cursor.getCause()) {
            if (type.isInstance(cursor) && (reason == null || reason.equals(cursor.getMessage()))) {
                return true;
            }
        }
        return false;
    }
}
