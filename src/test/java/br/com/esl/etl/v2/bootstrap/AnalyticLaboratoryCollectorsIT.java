package br.com.esl.etl.v2.bootstrap;

import static br.com.esl.etl.v2.bootstrap.AnalyticLaboratoryFreightOperationalIT.CLOCK;
import static br.com.esl.etl.v2.bootstrap.AnalyticLaboratoryFreightOperationalIT.DATE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.modulos.faturasporcliente.domain.FaturaClienteRules.FiscalPolicy;
import br.com.esl.etl.v2.plataforma.analitico.AnalyticDimensionBinding;
import br.com.esl.etl.v2.plataforma.analitico.AnalyticDimensionBinding.Entity;
import br.com.esl.etl.v2.plataforma.analitico.AnalyticDimensionBinding.Role;
import br.com.esl.etl.v2.plataforma.analitico.AnalyticManifestState;
import br.com.esl.etl.v2.plataforma.controle.ExecutionMode;
import br.com.esl.etl.v2.plataforma.expansao.ExpansionPolicy;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportTemplate;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.ExpansionSyntheticSource;
import br.com.esl.etl.v2.plataforma.persistencia.analitico.JdbcAnalyticDimensions;
import br.com.esl.etl.v2.plataforma.persistencia.analitico.JdbcAnalyticManifestState;
import br.com.esl.etl.v2.plataforma.persistencia.analitico.JdbcAnalyticMaterializations;
import br.com.esl.etl.v2.plataforma.persistencia.coletas.ColetaTemporalLaboratorySession;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AnalyticLaboratoryCollectorsIT {
    @Test
    void physicalExpansionCountsEachManifestAndInventoryOnceInItsAssignedBranch() throws Exception {
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            final var fixture = AnalyticLaboratoryDimensionsIT.start(session, true);
            final var row = AnalyticLaboratoryManifestCaptureIT.manifest();
            final var manifest =
                    AnalyticLaboratoryManifestPreparationIT.runtime(session, fixture)
                            .capture(
                                    DATE,
                                    ExecutionMode.BOOTSTRAP,
                                    null,
                                    page -> page == 1 ? "[" + row + "," + row + "]" : "[]");
            activate(session, fixture.run(), manifest.source().executionId(), 1, true, false);
            final UUID inv = inventory(session, fixture, 2);
            new JdbcAnalyticDimensions(session)
                    .bindBatch(
                            fixture.run(),
                            List.of(
                                    binding(
                                            Entity.MAN,
                                            "INTEGER:1",
                                            manifest.source().executionId(),
                                            Role.BRANCH,
                                            "synthetic-branch-a",
                                            1,
                                            null),
                                    binding(
                                            Entity.MAN,
                                            "INTEGER:1",
                                            manifest.source().executionId(),
                                            Role.UNLOADING_BRANCH,
                                            "synthetic-branch-b",
                                            1,
                                            null),
                                    binding(
                                            Entity.INV,
                                            "STRING:INV-root-1",
                                            inv,
                                            Role.BRANCH,
                                            "synthetic-branch-a",
                                            1,
                                            null),
                                    binding(
                                            Entity.INV,
                                            "STRING:INV-root-2",
                                            inv,
                                            Role.BRANCH,
                                            "synthetic-branch-b",
                                            1,
                                            null)),
                            CancellationToken.none());
            final var repository = new JdbcAnalyticMaterializations(session, CLOCK);
            final var request = AnalyticLaboratoryFreightOperationalIT.request(fixture.run());
            assertEquals(
                    new JdbcAnalyticMaterializations.Receipt(4, 4, 0, 0, 4, 0),
                    repository.collectors(request, CancellationToken.none()));
            assertEquals(
                    new JdbcAnalyticMaterializations.Receipt(4, 4, 0, 0, 4, 0),
                    repository.collectors(request, CancellationToken.none()));
            assertEquals(
                    new JdbcAnalyticMaterializations.Receipt(4, 0, 0, 4, 4, 0),
                    repository.collectors(
                            AnalyticLaboratoryFreightOperationalIT.request(fixture.run()),
                            CancellationToken.none()));
            try (var connection = session.getConnection();
                    var sql =
                            connection.prepareStatement(
                                    "SELECT * FROM pub.analytic_lab_collectors WHERE run_id=? ORDER BY reference_date,bran"
                                            + "ch_key")) {
                sql.setQueryTimeout(10);
                sql.setString(1, fixture.run().toString());
                try (var rows = sql.executeQuery()) {
                    assertTrue(rows.next());
                    assertEquals("synthetic-branch-a", rows.getString("branch_key"));
                    assertEquals(1, rows.getLong("issued"));
                    assertEquals(0, rows.getLong("unloaded"));
                    assertEquals(1, rows.getLong("scanned"));
                    assertEquals(1, rows.getLong("incomplete"));
                    assertEquals(1, rows.getLong("total"));
                    assertEquals(new BigDecimal("100.00000000"), rows.getBigDecimal("percentage"));
                    assertEquals("Geral", rows.getString("classification"));
                    assertEquals(
                            28, rows.getMetaData().getPrecision(rows.findColumn("percentage")));
                    assertEquals(8, rows.getMetaData().getScale(rows.findColumn("percentage")));
                    assertTrue(rows.next());
                    assertEquals("synthetic-branch-b", rows.getString("branch_key"));
                    assertEquals(0, rows.getLong("issued"));
                    assertEquals(1, rows.getLong("unloaded"));
                    assertEquals(1, rows.getLong("scanned"));
                    assertEquals(0, rows.getLong("incomplete"));
                    assertFalse(rows.next());
                }
            }
        }
    }

    @Test
    void changedDateAndUnloadingBindingRecomposeBothOldPartitionsAndExclusionRemovesTotals()
            throws Exception {
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            final var fixture = AnalyticLaboratoryDimensionsIT.start(session, true);
            inventory(session, fixture, 0);
            final var row = AnalyticLaboratoryManifestCaptureIT.manifest();
            final var runtime = AnalyticLaboratoryManifestPreparationIT.runtime(session, fixture);
            final var first =
                    runtime.capture(
                            DATE,
                            ExecutionMode.BOOTSTRAP,
                            null,
                            AnalyticLaboratoryManifestPreparationIT.source(row));
            activate(session, fixture.run(), first.source().executionId(), 1, true, false);
            final var dimensions = new JdbcAnalyticDimensions(session);
            dimensions.bindBatch(
                    fixture.run(),
                    List.of(
                            binding(
                                    Entity.MAN,
                                    "INTEGER:1",
                                    first.source().executionId(),
                                    Role.BRANCH,
                                    "synthetic-branch-a",
                                    1,
                                    null),
                            binding(
                                    Entity.MAN,
                                    "INTEGER:1",
                                    first.source().executionId(),
                                    Role.UNLOADING_BRANCH,
                                    "synthetic-branch-b",
                                    1,
                                    null)),
                    CancellationToken.none());
            final var repository = new JdbcAnalyticMaterializations(session, CLOCK);
            assertEquals(
                    2,
                    repository
                            .collectors(
                                    AnalyticLaboratoryFreightOperationalIT.request(fixture.run()),
                                    CancellationToken.none())
                            .ready());
            final var corrected =
                    row.deepCopy()
                            .put("created_at", "2036-04-02T12:00:00Z")
                            .put("finished_at", "2036-04-02T13:00:00Z");
            final var second =
                    runtime.capture(
                            DATE.plusDays(1),
                            ExecutionMode.BACKFILL,
                            null,
                            AnalyticLaboratoryManifestPreparationIT.source(corrected));
            dimensions.bindBatch(
                    fixture.run(),
                    List.of(
                            binding(
                                    Entity.MAN,
                                    "INTEGER:1",
                                    second.source().executionId(),
                                    Role.UNLOADING_BRANCH,
                                    "synthetic-branch-a",
                                    2,
                                    "synthetic-branch-b")),
                    CancellationToken.none());
            assertEquals(
                    2,
                    repository
                            .collectors(
                                    AnalyticLaboratoryFreightOperationalIT.request(fixture.run()),
                                    CancellationToken.none())
                            .updates());
            assertEquals(
                    1,
                    count(
                            session,
                            fixture.run(),
                            "SELECT COUNT_BIG(*) FROM pub.analytic_lab_collectors WHERE run_id=? AND reference_dat"
                                    + "e='20360402' AND branch_key='synthetic-branch-a' AND issued=1 AND unloaded=1 AND tota"
                                    + "l=2 AND percentage=0"));
            assertEquals(
                    2,
                    count(
                            session,
                            fixture.run(),
                            "SELECT COUNT_BIG(*) FROM mart.analytic_collector_daily c JOIN mart.analytic_collector"
                                    + "_daily_observation o ON o.observation_id=c.observation_id WHERE c.run_id=? AND c.refe"
                                    + "rence_date='20360401' AND o.active=0 AND o.total=0"));
            activate(session, fixture.run(), second.source().executionId(), 2, false, false);
            assertEquals(
                    2,
                    repository
                            .collectors(
                                    AnalyticLaboratoryFreightOperationalIT.request(fixture.run()),
                                    CancellationToken.none())
                            .blocked());
            assertEquals(
                    0,
                    count(
                            session,
                            fixture.run(),
                            "SELECT COUNT_BIG(*) FROM pub.analytic_lab_collectors WHERE run_id=?"));
            activate(session, fixture.run(), second.source().executionId(), 3, true, true);
            assertEquals(
                    2,
                    repository
                            .collectors(
                                    AnalyticLaboratoryFreightOperationalIT.request(fixture.run()),
                                    CancellationToken.none())
                            .ready());
        }
    }

    static UUID inventory(
            final ColetaTemporalLaboratorySession session,
            final AnalyticLaboratoryDimensionsIT.Fixture fixture,
            final int roots)
            throws Exception {
        final UUID execution = UUID.randomUUID();
        final var source =
                new ExpansionSyntheticSource(
                        page -> {
                            final var rows = JsonNodeFactory.instance.arrayNode();
                            final int begin = (page - 1) * 2, end = Math.min(roots * 3, begin + 2);
                            for (int index = begin; index < end; index++) {
                                final int root = index / 3 + 1, component = index % 3 == 1 ? 2 : 1;
                                final ObjectNode data =
                                        ExpansionLaboratoryFixtures.data(
                                                DataExportTemplate.INVENTARIO);
                                data.put("type", root == 1 ? "Picking" : "CheckIn::Order::Loading");
                                if (root == 1) {
                                    data.putNull("finished_at");
                                }
                                rows.add(
                                        ExpansionLaboratoryFixtures.envelope(
                                                DataExportTemplate.INVENTARIO,
                                                data,
                                                index + 1,
                                                root,
                                                component));
                            }
                            return rows.toString();
                        });
        final var policy =
                new ExpansionPolicy(
                        DATE, DATE.plusDays(3), DATE, 2, 100, 1000, FiscalPolicy.SYNTHETIC_CTE);
        new LocalExpansionRuntime(session, fixture.expansion(), policy, CLOCK, Clock.systemUTC())
                .capture(
                        execution,
                        DataExportTemplate.INVENTARIO,
                        DATE,
                        ExecutionMode.BOOTSTRAP,
                        null,
                        source,
                        CancellationToken.none());
        return execution;
    }

    static void activate(
            final ColetaTemporalLaboratorySession session,
            final UUID run,
            final UUID execution,
            final int revision,
            final boolean active,
            final boolean reactivate)
            throws Exception {
        new JdbcAnalyticManifestState(session)
                .bindBatch(
                        run,
                        List.of(
                                new AnalyticManifestState(
                                        "INTEGER:1", execution, revision, active, reactivate)),
                        CancellationToken.none());
    }

    static AnalyticDimensionBinding binding(
            final Entity entity,
            final String key,
            final UUID execution,
            final Role role,
            final String branch,
            final int revision,
            final String previous) {
        return new AnalyticDimensionBinding(
                entity,
                key,
                execution,
                role,
                branch,
                revision,
                DATE,
                DATE.plusDays(3),
                true,
                previous,
                "synthetic-collector-binding");
    }

    private static long count(
            final ColetaTemporalLaboratorySession session, final UUID run, final String query)
            throws Exception {
        return AnalyticLaboratoryRasterIT.scalar(session, query, run);
    }
}
