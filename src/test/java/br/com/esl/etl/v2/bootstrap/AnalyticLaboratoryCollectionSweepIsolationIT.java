package br.com.esl.etl.v2.bootstrap;

import static br.com.esl.etl.v2.bootstrap.AnalyticLaboratoryFreightOperationalIT.CLOCK;
import static br.com.esl.etl.v2.bootstrap.AnalyticLaboratoryFreightOperationalIT.DATE;
import static br.com.esl.etl.v2.bootstrap.AnalyticLaboratoryRasterIT.scalar;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import br.com.esl.etl.v2.plataforma.analitico.SyntheticCollectionSnapshot;
import br.com.esl.etl.v2.plataforma.persistencia.analitico.JdbcAnalyticCollectionSweep;
import br.com.esl.etl.v2.plataforma.persistencia.analitico.JdbcAnalyticMaterializations;
import br.com.esl.etl.v2.plataforma.persistencia.coletas.ColetaTemporalLaboratorySession;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken;
import java.sql.SQLException;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AnalyticLaboratoryCollectionSweepIsolationIT {
    @Test
    void fullMaterializationAndAnotherRunCannotConfirmOrClearCandidate() throws Exception {
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            final var f = AnalyticLaboratoryCollectionSupplementIT.start(session);
            AnalyticLaboratoryCollectionQueriesIT.prepare(session, f);
            AnalyticLaboratoryCollectionSweepIT.runtime(session, f)
                    .observe(
                            new SyntheticCollectionSnapshot(f.run(), DATE, 2, true),
                            UUID.randomUUID(),
                            CancellationToken.none());
            new JdbcAnalyticMaterializations(session, CLOCK)
                    .freight(
                            AnalyticLaboratoryFreightOperationalIT.request(f.run()),
                            CancellationToken.none());
            final var other = AnalyticLaboratoryCollectionSupplementIT.start(session);
            AnalyticLaboratoryCollectionQueriesIT.prepare(session, other);
            AnalyticLaboratoryCollectionSweepIT.runtime(session, other)
                    .observe(
                            new SyntheticCollectionSnapshot(other.run(), DATE, 2, false),
                            UUID.randomUUID(),
                            CancellationToken.none());
            assertEquals(
                    1,
                    scalar(
                            session,
                            "SELECT COUNT_BIG(*) FROM recon.analytic_collection_absence WHERE run_id=? AND confirm"
                                    + "ations=1 AND confirmed=0",
                            f.run()));
            assertEquals(
                    0,
                    scalar(
                            session,
                            "SELECT COUNT_BIG(*) FROM recon.analytic_collection_absence WHERE run_id=?",
                            other.run()));
            assertEquals(
                    1,
                    scalar(
                            session,
                            "SELECT COUNT_BIG(*) FROM recon.analytic_collection_sweep_application a JOIN recon.ana"
                                    + "lytic_collection_sweep_cycle c ON c.cycle_id=a.cycle_id WHERE c.run_id=?",
                            f.run()));
        }
    }

    @Test
    void olderPreparedObservationCannotAdvanceAfterNewerApplication() throws Exception {
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            final var f = AnalyticLaboratoryCollectionSupplementIT.start(session);
            AnalyticLaboratoryCollectionQueriesIT.prepare(session, f);
            final var snapshot = new SyntheticCollectionSnapshot(f.run(), DATE, 2, true);
            final var gateway = new JdbcAnalyticCollectionSweep(session);
            final var older =
                    gateway.prepare(
                            snapshot,
                            UUID.randomUUID(),
                            AnalyticLaboratoryCollectionSweepIT.captures(session, f, 1),
                            CancellationToken.none());
            final var newer =
                    gateway.prepare(
                            snapshot,
                            UUID.randomUUID(),
                            AnalyticLaboratoryCollectionSweepIT.captures(session, f, 1),
                            CancellationToken.none());
            assertEquals(1, gateway.apply(newer, CancellationToken.none()).candidates());
            assertEquals(
                    53780,
                    assertThrows(
                                    SQLException.class,
                                    () -> gateway.apply(older, CancellationToken.none()))
                            .getErrorCode());
            assertEquals(
                    1,
                    scalar(
                            session,
                            "SELECT COUNT_BIG(*) FROM recon.analytic_collection_absence WHERE run_id=? AND confirm"
                                    + "ations=1 AND confirmed=0",
                            f.run()));
        }
    }
}
