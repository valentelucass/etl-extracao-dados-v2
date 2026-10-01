package br.com.esl.etl.v2.bootstrap;

import static br.com.esl.etl.v2.bootstrap.AnalyticLaboratoryFreightOperationalIT.CLOCK;
import static br.com.esl.etl.v2.bootstrap.AnalyticLaboratoryFreightOperationalIT.DATE;
import static br.com.esl.etl.v2.bootstrap.AnalyticLaboratoryRasterIT.scalar;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import br.com.esl.etl.v2.modulos.faturasporcliente.domain.FaturaClienteRules.FiscalPolicy;
import br.com.esl.etl.v2.plataforma.controle.ExecutionMode;
import br.com.esl.etl.v2.plataforma.expansao.ExpansionPolicy;
import br.com.esl.etl.v2.plataforma.persistencia.coletas.ColetaTemporalLaboratorySession;
import br.com.esl.etl.v2.plataforma.persistencia.expansao.JdbcExpansionLaboratory;
import br.com.esl.etl.v2.plataforma.persistencia.expansao.JdbcExpansionQueries;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AnalyticExpansionCaptureIT {
    @Test
    void packagedInputsHydrateMissingFreightAndReuseBothFactsAcrossFourModes() throws Exception {
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            final UUID run = UUID.randomUUID();
            RuntimePhaseEvidence.sql(
                    RuntimePhaseEvidence.Phase.EXPANSION_START,
                    () -> {
                        new JdbcExpansionLaboratory(session, CLOCK)
                                .start(
                                        run,
                                        new ExpansionPolicy(
                                                DATE,
                                                DATE.plusDays(3),
                                                DATE,
                                                2,
                                                100,
                                                1000,
                                                FiscalPolicy.SYNTHETIC_CTE));
                        return null;
                    });
            final var runtime =
                    new AnalyticExpansionCapture(session, run, CLOCK, Clock.systemUTC());
            int revision = 0;
            for (final var mode :
                    List.of(
                            ExecutionMode.BOOTSTRAP,
                            ExecutionMode.INCREMENTAL,
                            ExecutionMode.BACKFILL,
                            ExecutionMode.REPLAY)) {
                final var result =
                        runtime.capture(mode, ++revision, 2, true, CancellationToken.none());
                if (result.progress().complete() != 1) {
                    fail(mode + ":" + diagnostics(session, run));
                }
                assertEquals(1, result.progress().complete());
                assertEquals(0, result.progress().degraded());
                assertEquals(mode == ExecutionMode.BOOTSTRAP ? 1 : 0, result.hydrated());
                assertEquals(6, result.steps().size());
                final var query = new JdbcExpansionQueries(session);
                assertEquals(2, query.invoiceFactsPage(run, 0, 10).size());
                assertEquals(2, query.revenueFactsPage(run, 0, 10).size());
                assertEquals(
                        200,
                        scalar(
                                session,
                                "SELECT CONVERT(BIGINT,SUM(operational_value)) FROM mart.expansion_lab_invoice WHERE r"
                                        + "un_id=?",
                                run));
                assertEquals(
                        240,
                        scalar(
                                session,
                                "SELECT CONVERT(BIGINT,SUM(revenue_value)) FROM mart.expansion_lab_revenue WHERE run_id=?",
                                run));
                for (final var vertical : JdbcExpansionQueries.Vertical.values()) {
                    assertEquals(4, query.detailPage(run, vertical, 1, 0, 10).size());
                }
            }
            assertEquals(
                    25,
                    scalar(
                            session,
                            "SELECT COUNT_BIG(*) FROM recon.expansion_lab_step_capture WHERE run_id=?",
                            run));
            assertEquals(
                    1,
                    scalar(
                            session,
                            "SELECT COUNT_BIG(*) FROM ctl.expansion_lab_queue WHERE run_id=? AND state='RESOLVED'",
                            run));
            assertTrue(
                    DATE.isBefore(
                            CLOCK.instant().atZone(CLOCK.getZone()).toLocalDate().minusDays(3)));
        }
    }

    @Test
    void packagedManifestObservationsUseTypedPreparationAndDuplicateLineage() throws Exception {
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            final var fixture = AnalyticLaboratoryDimensionsIT.start(session, true);
            final var runtime =
                    AnalyticLaboratoryManifestPreparationIT.runtime(session, fixture).runtime();
            final var result =
                    runtime.capture(
                            DATE,
                            ExecutionMode.BOOTSTRAP,
                            null,
                            AnalyticScenarioFixtures.manifests(1, 2, 2, 1, false),
                            CancellationToken.none());
            assertEquals(
                    2,
                    scalar(
                            session,
                            "SELECT COUNT_BIG(*) FROM core.analytic_manifest_snapshot WHERE run_id=?",
                            fixture.run()));
            assertEquals(
                    6,
                    scalar(
                            session,
                            "SELECT COUNT_BIG(*) FROM core.analytic_manifest_snapshot_lineage l JOIN core.analytic"
                                    + "_manifest_snapshot s"
                                    + " ON s.snapshot_id=l.snapshot_id WHERE s.run_id=?",
                            fixture.run()));
            runtime.capture(
                    DATE,
                    ExecutionMode.REPLAY,
                    result.source().executionId(),
                    AnalyticScenarioFixtures.manifests(1, 2, 2, 1, false),
                    CancellationToken.none());
            assertEquals(
                    4,
                    scalar(
                            session,
                            "SELECT COUNT_BIG(*) FROM core.analytic_manifest_snapshot WHERE run_id=?",
                            fixture.run()));
            assertEquals(
                    2,
                    scalar(
                            session,
                            "SELECT COUNT_BIG(*) FROM core.analytic_manifest_current WHERE run_id=?",
                            fixture.run()));
        }
    }

    private static String diagnostics(final ColetaTemporalLaboratorySession session, final UUID run)
            throws java.sql.SQLException {
        try (var connection = session.getConnection();
                var statement =
                        connection.prepareStatement(
                                "SELECT TOP(12) reason,COUNT_BIG(*) n FROM ("
                                        + "SELECT CONCAT('INVOICE:',disposition) reason FROM mart.expansion_lab_invoice WHERE ru"
                                        + "n_id=?"
                                        + " UNION ALL SELECT CONCAT('REVENUE:',disposition) FROM mart.expansion_lab_revenue WHER"
                                        + "E run_id=?"
                                        + " UNION ALL SELECT CONCAT('LINK:',state) FROM core.expansion_lab_link WHERE run_id=?"
                                        + ") d GROUP BY reason ORDER BY reason")) {
            statement.setQueryTimeout(10);
            for (int index = 1; index <= 3; index++) {
                statement.setString(index, run.toString());
            }
            try (var rows = statement.executeQuery()) {
                final var text = new StringBuilder();
                while (rows.next()) {
                    text.append(rows.getString(1)).append('=').append(rows.getLong(2)).append(';');
                }
                return text.toString();
            }
        }
    }
}
