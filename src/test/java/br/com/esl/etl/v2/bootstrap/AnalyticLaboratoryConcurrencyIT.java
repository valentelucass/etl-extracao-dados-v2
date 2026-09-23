package br.com.esl.etl.v2.bootstrap;

import static br.com.esl.etl.v2.bootstrap.AnalyticLaboratoryFreightOperationalIT.CLOCK;
import static br.com.esl.etl.v2.bootstrap.AnalyticLaboratoryFreightOperationalIT.DATE;
import static br.com.esl.etl.v2.bootstrap.AnalyticLaboratoryRasterIT.scalar;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.plataforma.analitico.AnalyticDimensionBinding.Entity;
import br.com.esl.etl.v2.plataforma.analitico.AnalyticDimensionBinding.Role;
import br.com.esl.etl.v2.plataforma.analitico.AnalyticMaterializationRequest;
import br.com.esl.etl.v2.plataforma.controle.ExecutionMode;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.RelationalSyntheticSource;
import br.com.esl.etl.v2.plataforma.fonte.raster.RasterWindow;
import br.com.esl.etl.v2.plataforma.persistencia.analitico.JdbcAnalyticDimensions;
import br.com.esl.etl.v2.plataforma.persistencia.analitico.JdbcAnalyticMaterializations;
import br.com.esl.etl.v2.plataforma.persistencia.analitico.JdbcRasterLaboratory;
import br.com.esl.etl.v2.plataforma.persistencia.coletas.ColetaTemporalLaboratorySession;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class AnalyticLaboratoryConcurrencyIT {
    @ParameterizedTest
    @ValueSource(strings = {"RASTER", "MAT01", "MAT02", "MAT05"})
    void physicalOwnersContendInActualProcedureAndConsumerRecoversAfterRollback(final String lane)
            throws Exception {
        try (var first = ColetaTemporalLaboratorySession.openFromEnvironment();
                var second = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            assertNotEquals(scalar(first, "SELECT @@SPID"), scalar(second, "SELECT @@SPID"));
            final UUID run = UUID.randomUUID();
            final UUID capture = prepare(first, run, lane);
            apply(first, run, lane, capture);
            assertTrue(consume(first, run, lane) > 0);
            try (var connection = second.getConnection()) {
                connection.setSavepoint();
            }
            final SQLException busy =
                    assertThrows(SQLException.class, () -> apply(second, run, lane, capture));
            assertEquals(53502, busy.getErrorCode());
            assertEquals(1, scalar(second, "SELECT XACT_STATE()"));
            first.rollback();
            final UUID recovered = prepare(second, run, lane);
            apply(second, run, lane, recovered);
            assertTrue(consume(second, run, lane) > 0);
            second.rollback();
            assertEquals(
                    0,
                    scalar(
                            second,
                            "SELECT COUNT_BIG(*) FROM ctl.analytic_lab_run WHERE run_id=?",
                            run));
        }
    }

    private static UUID prepare(
            final ColetaTemporalLaboratorySession session, final UUID run, final String lane)
            throws Exception {
        if (lane.equals("RASTER")) {
            return AnalyticLaboratoryRasterIT.start(session, run)
                    .capture(
                            run,
                            ExecutionMode.BOOTSTRAP,
                            new RasterWindow(DATE, DATE.plusDays(3)),
                            AnalyticRasterFixtures.source(1, 1),
                            10,
                            100,
                            CancellationToken.none())
                    .capture();
        }
        final var f =
                AnalyticLaboratoryDimensionsIT.start(
                        session, RelationalSyntheticSource.analyticManifestContracts(), run);
        if (lane.equals("MAT01")) {
            AnalyticLaboratoryFreightOperationalIT.bind(session, f);
            AnalyticLaboratoryFreightOperationalIT.capture(
                    session, f, 1, "120", true, false, "done", "2036-04-01T14:00:00Z", row -> {});
            return null;
        }
        final var setup = AnalyticLaboratoryManifestsIT.setup(session, f, false, true);
        if (lane.equals("MAT05")) {
            return null;
        }
        final UUID inventory = AnalyticLaboratoryCollectorsIT.inventory(session, f, 2);
        new JdbcAnalyticDimensions(session)
                .bindBatch(
                        run,
                        List.of(
                                AnalyticLaboratoryCollectorsIT.binding(
                                        Entity.MAN,
                                        "INTEGER:1",
                                        setup.execution(),
                                        Role.UNLOADING_BRANCH,
                                        "synthetic-branch-b",
                                        1,
                                        null),
                                AnalyticLaboratoryCollectorsIT.binding(
                                        Entity.INV,
                                        "STRING:INV-root-1",
                                        inventory,
                                        Role.BRANCH,
                                        "synthetic-branch-a",
                                        1,
                                        null),
                                AnalyticLaboratoryCollectorsIT.binding(
                                        Entity.INV,
                                        "STRING:INV-root-2",
                                        inventory,
                                        Role.BRANCH,
                                        "synthetic-branch-b",
                                        1,
                                        null)),
                        CancellationToken.none());
        return null;
    }

    private static void apply(
            final ColetaTemporalLaboratorySession session,
            final UUID run,
            final String lane,
            final UUID capture)
            throws SQLException {
        if (lane.equals("RASTER")) {
            new JdbcRasterLaboratory(session, CLOCK).apply(run, capture);
            return;
        }
        final var request =
                new AnalyticMaterializationRequest(
                        run,
                        UUID.randomUUID(),
                        lane.equals("MAT01") ? 1 : 2,
                        ExecutionMode.BACKFILL,
                        true,
                        DATE,
                        DATE.plusDays(3));
        final var repository = new JdbcAnalyticMaterializations(session, CLOCK);
        switch (lane) {
            case "MAT01" -> repository.freight(request, CancellationToken.none());
            case "MAT02" -> repository.collectors(request, CancellationToken.none());
            case "MAT05" -> repository.manifests(request, CancellationToken.none());
            default -> throw new IllegalArgumentException("TEST_LANE");
        }
    }

    private static long consume(
            final ColetaTemporalLaboratorySession session, final UUID run, final String lane)
            throws SQLException {
        final String table =
                switch (lane) {
                    case "RASTER" -> "pub.analytic_lab_sql_13";
                    case "MAT01" -> "pub.analytic_lab_freight_operational";
                    case "MAT02" -> "pub.analytic_lab_collectors";
                    case "MAT05" -> "pub.analytic_lab_manifests";
                    default -> throw new IllegalArgumentException("TEST_LANE");
                };
        return scalar(session, "SELECT COUNT_BIG(*) FROM " + table + " WHERE run_id=?", run);
    }
}
