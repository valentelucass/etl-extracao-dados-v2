package br.com.esl.etl.v2.bootstrap;

import static br.com.esl.etl.v2.bootstrap.AnalyticLaboratoryFreightOperationalIT.CLOCK;
import static br.com.esl.etl.v2.bootstrap.AnalyticLaboratoryFreightOperationalIT.DATE;
import static br.com.esl.etl.v2.bootstrap.AnalyticLaboratoryRasterIT.scalar;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.plataforma.analitico.SyntheticCollectionSnapshot;
import br.com.esl.etl.v2.plataforma.controle.ExecutionMode;
import br.com.esl.etl.v2.plataforma.persistencia.analitico.JdbcAnalyticCollectionSweep;
import br.com.esl.etl.v2.plataforma.persistencia.analitico.JdbcAnalyticCollectionSweep.Prepared;
import br.com.esl.etl.v2.plataforma.persistencia.coletas.ColetaTemporalLaboratorySession;
import br.com.esl.etl.v2.plataforma.relacional.RelationalLaboratoryPolicy;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken;
import java.sql.SQLException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AnalyticLaboratoryCollectionSweepIT {
    @Test
    void firstObservationIsCandidateSecondConfirmsAndReappearanceClearsAbsenceFields()
            throws Exception {
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            final var f = AnalyticLaboratoryCollectionSupplementIT.start(session);
            AnalyticLaboratoryCollectionQueriesIT.prepare(session, f);
            final var runtime = runtime(session, f);
            final var snapshot = new SyntheticCollectionSnapshot(f.run(), DATE, 2, true);
            final var first =
                    runtime.observe(snapshot, UUID.randomUUID(), CancellationToken.none());
            assertEquals(1, first.result().candidates());
            assertEquals(0, first.result().confirmations());
            assertEquals(
                    1,
                    scalar(
                            session,
                            "SELECT COUNT_BIG(*) FROM pub.analytic_lab_sql_03 WHERE run_id=? AND [Status]=N'Excluí"
                                    + "da' AND [Excluída na Origem]=0",
                            f.run()));
            assertEquals(
                    0,
                    scalar(
                            session,
                            "SELECT COUNT_BIG(*) FROM pub.analytic_lab_sql_04 WHERE run_id=?",
                            f.run()));
            final var gateway = new JdbcAnalyticCollectionSweep(session);
            assertEquals(first.result(), gateway.apply(first.proof(), CancellationToken.none()));
            assertEquals(
                    first.proof(),
                    gateway.prepare(
                            snapshot,
                            first.proof().cycle(),
                            first.proof().captures(),
                            CancellationToken.none()));
            assertEquals(
                    1,
                    scalar(
                            session,
                            "SELECT COUNT_BIG(*) FROM recon.analytic_collection_absence WHERE run_id=? AND confirm"
                                    + "ations=1",
                            f.run()));
            final var second =
                    runtime.observe(snapshot, UUID.randomUUID(), CancellationToken.none());
            assertEquals(1, second.result().confirmations());
            try (var connection = session.getConnection();
                    var sql =
                            connection.prepareStatement(
                                    "SELECT * FROM pub.analytic_lab_sql_04 WHERE run_id=? AND reference_revision=1")) {
                sql.setQueryTimeout(20);
                sql.setString(1, f.run().toString());
                try (var row = sql.executeQuery()) {
                    assertTrue(row.next());
                    for (int column = 1; column <= 13; column++) {
                        assertNotNull(
                                row.getObject(column), row.getMetaData().getColumnLabel(column));
                    }
                    assertEquals(200001, row.getLong("ID"));
                    assertEquals(2, row.getInt("Confirmações de ausência"));
                    assertEquals("Excluída na origem", row.getString("Situação de sincronização"));
                    assertFalse(row.next());
                }
            }
            final var present =
                    runtime.observe(
                            new SyntheticCollectionSnapshot(f.run(), DATE, 2, false),
                            UUID.randomUUID(),
                            CancellationToken.none());
            assertEquals(1, present.result().reactivated());
            assertEquals(
                    0,
                    scalar(
                            session,
                            "SELECT COUNT_BIG(*) FROM pub.analytic_lab_sql_04 WHERE run_id=?",
                            f.run()));
            assertEquals(
                    1,
                    scalar(
                            session,
                            "SELECT COUNT_BIG(*) FROM recon.analytic_collection_absence WHERE run_id=? AND confirm"
                                    + "ations=0 AND confirmed=0 AND absent_since IS NULL AND excluded_at IS NULL AND reason "
                                    + "IS NULL AND first_cycle IS NULL AND last_cycle IS NULL",
                            f.run()));
            assertEquals(
                    1,
                    scalar(
                            session,
                            "SELECT COUNT_BIG(*) FROM pub.analytic_lab_sql_03 WHERE run_id=? AND [Status]<>N'Exclu"
                                    + "ída' AND [Excluída na Origem]=0",
                            f.run()));
            assertEquals(
                    12,
                    scalar(
                            session,
                            "SELECT COUNT_BIG(*) FROM recon.analytic_collection_sweep_proof p JOIN recon.analytic_"
                                    + "collection_sweep_cycle c ON c.cycle_id=p.cycle_id WHERE c.run_id=?",
                            f.run()));
        }
    }

    @Test
    void reusedObservationDivergentReceiptAndUnprovedCaptureCannotConfirm() throws Exception {
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            final var f = AnalyticLaboratoryCollectionSupplementIT.start(session);
            AnalyticLaboratoryCollectionQueriesIT.prepare(session, f);
            final var snapshot = new SyntheticCollectionSnapshot(f.run(), DATE, 2, true);
            final var first =
                    runtime(session, f)
                            .observe(snapshot, UUID.randomUUID(), CancellationToken.none());
            final var gateway = new JdbcAnalyticCollectionSweep(session);
            assertEquals(
                    53774,
                    assertThrows(
                                    SQLException.class,
                                    () ->
                                            gateway.prepare(
                                                    snapshot,
                                                    UUID.randomUUID(),
                                                    first.proof().captures(),
                                                    CancellationToken.none()))
                            .getErrorCode());
            final var bad =
                    new Prepared(
                            snapshot,
                            first.proof().cycle(),
                            first.proof().captures(),
                            "a".repeat(64),
                            1,
                            first.proof().pages());
            assertEquals(
                    53778,
                    assertThrows(
                                    SQLException.class,
                                    () -> gateway.apply(bad, CancellationToken.none()))
                            .getErrorCode());
            final var missing =
                    new Prepared(
                            snapshot,
                            UUID.randomUUID(),
                            first.proof().captures(),
                            "b".repeat(64),
                            1,
                            first.proof().pages());
            assertEquals(
                    53778,
                    assertThrows(
                                    SQLException.class,
                                    () -> gateway.apply(missing, CancellationToken.none()))
                            .getErrorCode());
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            gateway.prepare(
                                    snapshot,
                                    UUID.randomUUID(),
                                    List.of(
                                            first.proof().captures().get(0),
                                            first.proof().captures().get(0),
                                            first.proof().captures().get(2),
                                            first.proof().captures().get(3)),
                                    CancellationToken.none()));
            assertEquals(
                    1,
                    scalar(
                            session,
                            "SELECT COUNT_BIG(*) FROM recon.analytic_collection_sweep_application a JOIN recon.ana"
                                    + "lytic_collection_sweep_cycle c ON c.cycle_id=a.cycle_id WHERE c.run_id=?",
                            f.run()));
            assertEquals(
                    1,
                    scalar(
                            session,
                            "SELECT COUNT_BIG(*) FROM recon.analytic_collection_absence WHERE run_id=? AND confirm"
                                    + "ations=1 AND confirmed=0",
                            f.run()));
        }
    }

    @Test
    void partialEmptyAndNullKeyLeaveAbsenceAndCheckpointUnchanged() throws Exception {
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            final var f = AnalyticLaboratoryCollectionSupplementIT.start(session);
            AnalyticLaboratoryCollectionQueriesIT.prepare(session, f);
            final var snapshot = new SyntheticCollectionSnapshot(f.run(), DATE, 2, true);
            runtime(session, f).observe(snapshot, UUID.randomUUID(), CancellationToken.none());
            final var gateway = new JdbcAnalyticCollectionSweep(session);
            final var partial = captures(session, f, 1);
            assertEquals(
                    53775,
                    assertThrows(
                                    SQLException.class,
                                    () ->
                                            gateway.prepare(
                                                    new SyntheticCollectionSnapshot(
                                                            f.run(), DATE, 3, true),
                                                    UUID.randomUUID(),
                                                    partial,
                                                    CancellationToken.none()))
                            .getErrorCode());
            final var empty = captures(session, f, 0);
            assertEquals(
                    53775,
                    assertThrows(
                                    SQLException.class,
                                    () ->
                                            gateway.prepare(
                                                    snapshot,
                                                    UUID.randomUUID(),
                                                    empty,
                                                    CancellationToken.none()))
                            .getErrorCode());
            final var invalidKey =
                    assertThrows(
                            IllegalStateException.class,
                            () ->
                                    AnalyticLaboratoryCollectionsIT.capture(
                                            session,
                                            f,
                                            AnalyticCollectionsFixtures.data().putNull("id"),
                                            null));
            assertTrue(invalidKey.getMessage().contains("ausente, nulo ou não escalar"));
            assertEquals(
                    1,
                    scalar(
                            session,
                            "SELECT COUNT_BIG(*) FROM recon.analytic_collection_sweep_application a JOIN recon.ana"
                                    + "lytic_collection_sweep_cycle c ON c.cycle_id=a.cycle_id WHERE c.run_id=?",
                            f.run()));
            assertEquals(
                    1,
                    scalar(
                            session,
                            "SELECT COUNT_BIG(*) FROM recon.analytic_collection_absence WHERE run_id=? AND confirm"
                                    + "ations=1 AND confirmed=0",
                            f.run()));
        }
    }

    static LocalAnalyticCollectionSweep runtime(
            final ColetaTemporalLaboratorySession session,
            final AnalyticLaboratoryDimensionsIT.Fixture f) {
        return new LocalAnalyticCollectionSweep(
                session, f.run(), f.relational(), policy(), CLOCK, Clock.systemUTC());
    }

    static List<UUID> captures(
            final ColetaTemporalLaboratorySession session,
            final AnalyticLaboratoryDimensionsIT.Fixture f,
            final int roots)
            throws Exception {
        final var capture =
                new LocalAnalyticCollectionRuntime(
                        session, f.run(), f.relational(), policy(), CLOCK, Clock.systemUTC());
        final var result = new ArrayList<UUID>(4);
        for (int ordinal = 0; ordinal < 4; ordinal++) {
            result.add(
                    capture.capture(
                                    DATE,
                                    ExecutionMode.BACKFILL,
                                    null,
                                    AnalyticCollectionsFixtures.source(2, roots, 2),
                                    CancellationToken.none())
                            .source()
                            .executionId());
        }
        return List.copyOf(result);
    }

    private static RelationalLaboratoryPolicy policy() {
        return new RelationalLaboratoryPolicy(
                DATE, DATE.plusDays(2), 1000, 10, 3, 60, 2, 0, 2, 100);
    }
}
