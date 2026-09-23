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
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class SequenceFailureIT {
    @TempDir Path folder;

    @ParameterizedTest
    @EnumSource(IntegralArtifactResilienceIT.Fault.class)
    @Timeout(300)
    void failedCollectionBlocksBothFreightConsumersAndEveryLaterStage(
            final IntegralArtifactResilienceIT.Fault fault) throws Exception {
        final var file = IntegralSequenceFixtures.scheduled(folder, false, 2);
        final var captureFile = folder.resolve("initial/sources/col/capture.json");
        final var capture = IntegralArtifactFixtures.read(captureFile);
        final var page =
                captureFile.getParent().resolve(capture.path("pages").get(0).path("file").asText());
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
        final var sequence = new LocalArtifactSequence(file, token);
        final var run = UUID.randomUUID();
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            session.controlStatements(60, 100000);
            final var observer =
                    new AnalyticScenarioObserver() {
                        @Override
                        public AnalyticScenarioObserver forInput(final Input input) {
                            if (input != Input.COL) {
                                return NONE;
                            }
                            return new AnalyticScenarioObserver() {
                                @Override
                                public void beforeFetch() {
                                    if (fault == IntegralArtifactResilienceIT.Fault.PIN_DRIFT
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
                                    if (fault == IntegralArtifactResilienceIT.Fault.PIN_DRIFT
                                            || !injected.compareAndSet(false, true)) {
                                        return;
                                    }
                                    switch (fault) {
                                        case CANCEL -> cancelled.set(true);
                                        case DEADLINE ->
                                                ticker.set(Duration.ofSeconds(61).toNanos());
                                        case LEASE -> release(session);
                                        default ->
                                                throw new IllegalStateException("UNEXPECTED_FAULT");
                                    }
                                }
                            };
                        }
                    };
            final var stages = new java.util.ArrayList<LocalArtifactSequence.StageResult>();
            final var failure =
                    assertThrows(
                            Exception.class,
                            () -> sequence.execute(session, run, observer, token, stages::add));
            assertTrue(injected.get());
            final Class<? extends Throwable> expected =
                    switch (fault) {
                        case CANCEL -> ResilienceCancelledException.class;
                        case DEADLINE -> ResilienceTimeoutException.class;
                        case LEASE -> java.sql.SQLException.class;
                        case PIN_DRIFT -> IllegalArgumentException.class;
                    };
            boolean found = false;
            for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
                found |=
                        expected.isInstance(cause)
                                && (fault != IntegralArtifactResilienceIT.Fault.PIN_DRIFT
                                        || "LOCAL_PIN_BYTES".equals(cause.getMessage()));
            }
            assertTrue(found, failure.getClass().getSimpleName());
            assertTrue(stages.isEmpty());
            for (final String query :
                    java.util.List.of(
                            "SELECT COUNT_BIG(*) FROM ctl.expansion_lab_capture WHERE run_id=(SELECT expansion_run "
                                    + "FROM ctl.analytic_lab_source_group WHERE run_id=?)",
                            "SELECT COUNT_BIG(*) FROM mart.analytic_freight_operational WHERE run_id=?",
                            "SELECT COUNT_BIG(*) FROM ctl.analytic_scenario_cycle WHERE run_id=? AND state='COMPLETE'")) {
                assertEquals(0, IntegralArtifactResilienceIT.count(session, run, query));
            }
            assertEquals(0, session.openControlledStatements());
        }
    }

    private static void release(final ColetaTemporalLaboratorySession session) {
        try (var connection = session.getConnection();
                var sql =
                        connection.prepareStatement(
                                "UPDATE l SET released_at_utc=SYSUTCDATETIME() FROM ctl.execution_lease l "
                                        + "JOIN ctl.execution_partition p ON p.current_execution_id=l.execution_id "
                                        + "WHERE p.source_instance=? AND p.tenant_scope=? AND p.entity_name=? "
                                        + "AND p.current_state='EXTRACTING' AND l.released_at_utc IS NULL")) {
            sql.setQueryTimeout(10);
            sql.setString(1, "SYNTHETIC_CARRIER_A");
            sql.setString(2, "SYNTHETIC_WEST");
            sql.setString(3, "coletas");
            assertEquals(1, sql.executeUpdate());
        } catch (final java.sql.SQLException failure) {
            throw new IllegalStateException(failure);
        }
    }
}
