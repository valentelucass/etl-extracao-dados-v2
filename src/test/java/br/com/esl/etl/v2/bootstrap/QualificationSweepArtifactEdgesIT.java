package br.com.esl.etl.v2.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.modulos.faturasporcliente.domain.FaturaClienteRules.FiscalPolicy;
import br.com.esl.etl.v2.plataforma.expansao.ExpansionPolicy;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.RelationalSyntheticSource;
import br.com.esl.etl.v2.plataforma.persistencia.analitico.JdbcAnalyticDimensions;
import br.com.esl.etl.v2.plataforma.persistencia.analitico.JdbcRasterLaboratory;
import br.com.esl.etl.v2.plataforma.persistencia.coletas.ColetaTemporalLaboratorySession;
import br.com.esl.etl.v2.plataforma.persistencia.expansao.JdbcExpansionLaboratory;
import br.com.esl.etl.v2.plataforma.persistencia.relacional.JdbcRelationalLaboratory;
import br.com.esl.etl.v2.plataforma.relacional.RelationalLaboratoryPolicy;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken;
import br.com.esl.etl.v2.plataforma.resiliencia.ResilienceCancelledException;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

class QualificationSweepArtifactEdgesIT {
    @TempDir Path folder;
    private static final LocalDate DATE = LocalDate.of(2036, 4, 3);

    @Test
    @Timeout(120)
    void alternateScopeCannotReserveAnyCapture() throws Exception {
        final var artifact = input();
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            final var context = start(session);
            final long before = session.preparedStatements();
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            context.sweep()
                                    .observe(
                                            artifact.snapshot(context.run()),
                                            UUID.randomUUID(),
                                            artifact.inputs(
                                                    UUID.randomUUID(), CancellationToken.none()),
                                            CancellationToken.none()));
            assertEquals(before, session.preparedStatements());
            empty(session, context);
        }
    }

    @Test
    @Timeout(120)
    void cancellationAfterActualFileBatchRollsBackAllFourObservationWork() throws Exception {
        final var artifact = input();
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            final var context = start(session);
            final var cancel = new AtomicBoolean();
            final var inputs = new ArrayList<>(artifact.inputs(context.run(), cancel::get));
            final var first = inputs.get(0);
            inputs.set(
                    0,
                    new CollectionSweepInput(
                            first.run(),
                            first.date(),
                            first.snapshotFingerprint(),
                            first.source()
                                    .observed(
                                            new RelationalSyntheticSource.Observer() {
                                                @Override
                                                public void batchStaged(final int records) {
                                                    cancel.set(true);
                                                }
                                            })));
            assertThrows(
                    ResilienceCancelledException.class,
                    () ->
                            context.sweep()
                                    .observe(
                                            artifact.snapshot(context.run()),
                                            UUID.randomUUID(),
                                            inputs,
                                            cancel::get));
            assertTrue(cancel.get());
            empty(session, context);
        }
    }

    @Test
    @Timeout(120)
    void releasedLeaseDuringFileCaptureBlocksFurtherStagingAndAnySweepPublication()
            throws Exception {
        final var artifact = input();
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            final var context = start(session);
            final var injected = new AtomicBoolean();
            final var inputs =
                    new ArrayList<>(artifact.inputs(context.run(), CancellationToken.none()));
            final var first = inputs.get(0);
            inputs.set(
                    0,
                    new CollectionSweepInput(
                            first.run(),
                            first.date(),
                            first.snapshotFingerprint(),
                            first.source()
                                    .observed(
                                            new RelationalSyntheticSource.Observer() {
                                                @Override
                                                public void batchStaged(final int records) {
                                                    if (!injected.compareAndSet(false, true)) {
                                                        return;
                                                    }
                                                    try (var connection = session.getConnection();
                                                            var sql =
                                                                    connection.prepareStatement(
                                                                            "UPDATE l SET released_at_utc=SYSUTCDATETIME()"
                                                                                    + " FROM ctl.execution_lease l"
                                                                                    + " JOIN ctl.execution_partition p"
                                                                                    + " ON p.current_execution_id=l.execution_id"
                                                                                    + " WHERE p.source_instance=?"
                                                                                    + " AND p.tenant_scope=?"
                                                                                    + " AND p.partition_start_utc=?"
                                                                                    + " AND p.current_state='EXTRACTING'"
                                                                                    + " AND l.released_at_utc IS NULL")) {
                                                        sql.setQueryTimeout(10);
                                                        sql.setString(
                                                                1, JdbcRelationalLaboratory.SOURCE);
                                                        sql.setString(
                                                                2, JdbcRelationalLaboratory.TENANT);
                                                        sql.setTimestamp(
                                                                3,
                                                                java.sql.Timestamp.from(
                                                                        DATE.atStartOfDay(
                                                                                        java.time
                                                                                                .ZoneOffset
                                                                                                .UTC)
                                                                                .toInstant()),
                                                                java.util.Calendar.getInstance(
                                                                        java.util.TimeZone
                                                                                .getTimeZone(
                                                                                        "UTC")));
                                                        assertEquals(1, sql.executeUpdate());
                                                    } catch (final SQLException failure) {
                                                        throw new IllegalStateException(
                                                                "SYNTHETIC_LEASE_INJECTION",
                                                                failure);
                                                    }
                                                }
                                            })));
            final var failure =
                    assertThrows(
                            Exception.class,
                            () ->
                                    context.sweep()
                                            .observe(
                                                    artifact.snapshot(context.run()),
                                                    UUID.randomUUID(),
                                                    inputs,
                                                    CancellationToken.none()));
            assertTrue(injected.get());
            assertTrue(sqlFailure(failure), failure.getClass().getSimpleName());
            empty(session, context);
        }
    }

    private CollectionSweepArtifact input() throws Exception {
        QualificationSweepArtifactFixtures.write(folder, DATE, 3);
        return new CollectionSweepArtifact(
                folder.resolve("phase-0.json"), CancellationToken.none());
    }

    private static Context start(final ColetaTemporalLaboratorySession session) throws Exception {
        final var zone = ZoneId.of("America/Sao_Paulo");
        final var clock = Clock.fixed(DATE.plusDays(2).atStartOfDay(zone).toInstant(), zone);
        final UUID run = UUID.randomUUID(),
                expansion = UUID.randomUUID(),
                relational = UUID.randomUUID();
        final var policy =
                new RelationalLaboratoryPolicy(DATE, DATE, 1000, 10, 3, 60, 2, 0, 2, 1000);
        new JdbcRasterLaboratory(session, Clock.systemUTC())
                .start(run, DATE, DATE.plusDays(1), zone, 100000, 1000);
        new JdbcExpansionLaboratory(session, clock)
                .start(
                        expansion,
                        new ExpansionPolicy(
                                DATE,
                                DATE.plusDays(1),
                                DATE,
                                2,
                                1000,
                                100000,
                                FiscalPolicy.SYNTHETIC_CTE));
        new JdbcRelationalLaboratory(session, clock)
                .start(relational, policy, RelationalSyntheticSource.analyticContracts());
        new JdbcAnalyticDimensions(session).associate(run, expansion, relational);
        return new Context(
                run,
                relational,
                new LocalAnalyticCollectionSweep(
                        session, run, relational, policy, clock, Clock.systemUTC()));
    }

    private static void empty(final ColetaTemporalLaboratorySession session, final Context context)
            throws Exception {
        assertEquals(
                0,
                ExpansionLaboratoryLocalIntegrationIT.scalar(
                        session,
                        "SELECT COUNT_BIG(*) FROM ctl.relational_lab_capture WHERE run_id=?",
                        context.relational()));
        assertEquals(
                0,
                ExpansionLaboratoryLocalIntegrationIT.scalar(
                        session,
                        "SELECT COUNT_BIG(*) FROM recon.analytic_collection_sweep_cycle WHERE run_id=?",
                        context.run()));
    }

    private static boolean sqlFailure(final Throwable error) {
        return error instanceof SQLException sql
                        && (sql.getErrorCode() == 51401
                                || sql.getNextException() != null
                                        && sqlFailure(sql.getNextException()))
                || error.getCause() != null && sqlFailure(error.getCause());
    }

    private record Context(UUID run, UUID relational, LocalAnalyticCollectionSweep sweep) {}
}
