package br.com.esl.etl.v2.bootstrap;

import static br.com.esl.etl.v2.bootstrap.AnalyticLaboratoryFreightOperationalIT.CLOCK;
import static br.com.esl.etl.v2.bootstrap.AnalyticLaboratoryFreightOperationalIT.DATE;
import static br.com.esl.etl.v2.bootstrap.AnalyticLaboratoryRasterIT.scalar;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import br.com.esl.etl.v2.plataforma.controle.ExecutionMode;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.RelationalSyntheticSource;
import br.com.esl.etl.v2.plataforma.persistencia.analitico.JdbcAnalyticCollectionPreparation;
import br.com.esl.etl.v2.plataforma.persistencia.coletas.ColetaTemporalLaboratorySession;
import br.com.esl.etl.v2.plataforma.relacional.RelationalLaboratoryPolicy;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.sql.SQLException;
import java.time.Clock;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AnalyticLaboratoryCollectionsIT {
    @Test
    void typedRootDeduplicatesAndReplayKeepsBusinessSnapshotWithLatestObservation()
            throws Exception {
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            final var f = AnalyticLaboratoryCollectionSupplementIT.start(session);
            final var first = capture(session, f, AnalyticCollectionsFixtures.data(), null);
            assertEquals(3, first.preparation().physicalRows());
            assertEquals(1, first.preparation().updates());
            assertEquals(
                    3,
                    scalar(
                            session,
                            "SELECT COUNT_BIG(*) FROM core.analytic_collection_lineage l JOIN core.analytic_collec"
                                    + "tion_snapshot s ON s.snapshot_id=l.snapshot_id WHERE s.run_id=?",
                            f.run()));
            assertEquals(
                    1,
                    scalar(
                            session,
                            "SELECT COUNT_BIG(*) FROM core.analytic_collection_snapshot WHERE run_id=? AND id=2000"
                                    + "01 AND invoices_weight=7.125 AND updated_at_nano=123456789 AND pck_pds_cty_name=N'CAM"
                                    + "PINAS'",
                            f.run()));
            final var replay =
                    capture(
                            session,
                            f,
                            AnalyticCollectionsFixtures.data(),
                            first.source().executionId());
            assertEquals(1, replay.preparation().noops());
            assertEquals(
                    1,
                    scalar(
                            session,
                            "SELECT COUNT_BIG(*) FROM core.analytic_collection_snapshot WHERE run_id=?",
                            f.run()));
            assertEquals(
                    1,
                    scalar(
                            session,
                            "SELECT COUNT_BIG(*) FROM core.analytic_collection_current p JOIN ctl.analytic_collect"
                                    + "ion_preparation c ON c.execution_id=p.last_observation WHERE p.run_id=? AND c.noops=1",
                            f.run()));
            assertEquals(
                    replay.preparation(),
                    new JdbcAnalyticCollectionPreparation(session)
                            .prepare(
                                    f.run(),
                                    replay.source().executionId(),
                                    CancellationToken.none()));
        }
    }

    @Test
    void absentPreservesNullClearsAndTerminalWinsBeforeFreshness() throws Exception {
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            final var f = AnalyticLaboratoryCollectionSupplementIT.start(session);
            capture(session, f, AnalyticCollectionsFixtures.data(), null);
            final var changed = AnalyticCollectionsFixtures.data();
            changed.put("status_updated_at", "2036-04-01T10:00:01.123456789Z");
            changed.remove("pck_pds_neighborhood");
            changed.putNull("pck_prn_name");
            capture(session, f, changed, null);
            assertEquals(
                    1,
                    scalar(
                            session,
                            "SELECT COUNT_BIG(*) FROM core.analytic_collection_current c JOIN core.analytic_collec"
                                    + "tion_snapshot s ON s.snapshot_id=c.snapshot_id WHERE c.run_id=? AND s.pck_pds_neighbo"
                                    + "rhood IS NOT NULL AND s.pck_prn_name IS NULL AND s.pck_prn_name_p='NULL'",
                            f.run()));
            final var terminal =
                    changed.deepCopy()
                            .put("status", "finished")
                            .put("status_updated_at", "2036-04-01T09:00:00.123456789Z");
            capture(session, f, terminal, null);
            final var stale =
                    changed.deepCopy().put("status_updated_at", "2036-04-01T11:00:00.123456789Z");
            assertEquals(1, capture(session, f, stale, null).preparation().noops());
            assertEquals(
                    1,
                    scalar(
                            session,
                            "SELECT COUNT_BIG(*) FROM core.analytic_collection_current c JOIN core.analytic_collec"
                                    + "tion_snapshot s ON s.snapshot_id=c.snapshot_id WHERE c.run_id=? AND s.terminal=1 AND "
                                    + "s.status_code='finished' AND s.attempt_count=1",
                            f.run()));
        }
    }

    @Test
    void equalClockAttributeConflictRollsBackCaptureAndAllowsRecovery() throws Exception {
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            final var f = AnalyticLaboratoryCollectionSupplementIT.start(session);
            capture(session, f, AnalyticCollectionsFixtures.data(), null);
            final var changed =
                    AnalyticCollectionsFixtures.data().put("pck_uer_name", "OUTRO SINTÉTICO");
            assertEquals(
                    53751,
                    assertThrows(SQLException.class, () -> capture(session, f, changed, null))
                            .getErrorCode());
            assertEquals(
                    1,
                    scalar(
                            session,
                            "SELECT COUNT_BIG(*) FROM ctl.analytic_collection_preparation WHERE run_id=?",
                            f.run()));
            assertEquals(
                    1,
                    capture(session, f, AnalyticCollectionsFixtures.data(), null)
                            .preparation()
                            .noops());
        }
    }

    static LocalAnalyticCollectionRuntime.Capture capture(
            final ColetaTemporalLaboratorySession session,
            final AnalyticLaboratoryDimensionsIT.Fixture f,
            final ObjectNode data,
            final UUID replay)
            throws SQLException {
        final var source =
                new RelationalSyntheticSource(
                                page ->
                                        page == 1
                                                ? "[" + data + "," + data + "]"
                                                : page == 2 ? "[" + data + "]" : "[]")
                        .withAnalyticCollectionDetails();
        return new LocalAnalyticCollectionRuntime(
                        session,
                        f.run(),
                        f.relational(),
                        new RelationalLaboratoryPolicy(
                                DATE, DATE.plusDays(2), 1000, 10, 3, 60, 2, 0, 2, 100),
                        CLOCK,
                        Clock.systemUTC())
                .capture(
                        DATE,
                        replay == null ? ExecutionMode.BOOTSTRAP : ExecutionMode.REPLAY,
                        replay,
                        source,
                        CancellationToken.none());
    }
}
