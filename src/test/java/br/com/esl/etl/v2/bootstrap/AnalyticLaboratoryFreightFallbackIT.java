package br.com.esl.etl.v2.bootstrap;

import static br.com.esl.etl.v2.bootstrap.AnalyticLaboratoryFreightOperationalIT.CLOCK;
import static br.com.esl.etl.v2.bootstrap.AnalyticLaboratoryFreightOperationalIT.DATE;
import static br.com.esl.etl.v2.bootstrap.AnalyticLaboratoryFreightOperationalIT.bind;
import static br.com.esl.etl.v2.bootstrap.AnalyticLaboratoryFreightOperationalIT.capture;
import static br.com.esl.etl.v2.bootstrap.AnalyticLaboratoryFreightOperationalIT.request;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import br.com.esl.etl.v2.modulos.faturasporcliente.domain.FaturaClienteRules.FiscalPolicy;
import br.com.esl.etl.v2.plataforma.controle.ExecutionMode;
import br.com.esl.etl.v2.plataforma.expansao.ExpansionKey;
import br.com.esl.etl.v2.plataforma.expansao.ExpansionPolicy;
import br.com.esl.etl.v2.plataforma.expansao.ExpansionRelation;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportTemplate;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.ExpansionDependencySource;
import br.com.esl.etl.v2.plataforma.persistencia.analitico.JdbcAnalyticMaterializations;
import br.com.esl.etl.v2.plataforma.persistencia.coletas.ColetaTemporalLaboratorySession;
import br.com.esl.etl.v2.plataforma.persistencia.expansao.JdbcExpansionRelations;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken;
import java.sql.SQLException;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AnalyticLaboratoryFreightFallbackIT {
    @Test
    void locationFallbackUsesDeclaredSourcePathAndCanonicalEdgesBeforeAnyFanOut() throws Exception {
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            final var fixture = AnalyticLaboratoryDimensionsIT.start(session);
            bind(session, fixture);
            capture(
                    session,
                    fixture,
                    1,
                    "120",
                    true,
                    false,
                    "done",
                    null,
                    row -> row.putNull("data_previsao_entrega"));
            location(session, fixture, 1);
            final var repository = new JdbcAnalyticMaterializations(session, CLOCK);
            assertEquals(
                    2,
                    repository.freight(request(fixture.run()), CancellationToken.none()).ready());
            assertEquals(
                    1,
                    scalar(
                            session,
                            fixture.run(),
                            "SELECT COUNT_BIG(*) FROM pub.analytic_lab_freight_operational WHERE run_id=? AND indi"
                                    + "cator='PE'"
                                    + " AND forecast_date='20360402' AND forecast_provenance='LOCALIZACAO_CAPTURE'"
                                    + " AND volumes=3 AND volume_provenance='LOCALIZACAO_CAPTURE'"));
            location(session, fixture, 2);
            assertEquals(
                    0,
                    repository.freight(request(fixture.run()), CancellationToken.none()).ready());
            assertEquals(
                    2,
                    scalar(
                            session,
                            fixture.run(),
                            "SELECT COUNT_BIG(*) FROM mart.analytic_freight_operational c JOIN mart.analytic_freig"
                                    + "ht_operational_observation o"
                                    + " ON o.observation_id=c.observation_id WHERE c.run_id=? AND o.disposition='LOCATION_CO"
                                    + "NFLICT'"));
        }
    }

    @Test
    void nfseFallbackNegativeCubageAndOutOfDeadlineAreRetainedAsDifferentRules() throws Exception {
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            final var fixture = AnalyticLaboratoryDimensionsIT.start(session);
            bind(session, fixture);
            capture(
                    session,
                    fixture,
                    1,
                    "120",
                    true,
                    false,
                    "done",
                    "2036-04-04T03:00:00Z",
                    row -> {
                        for (final var name :
                                List.of("cte_id", "chave_cte", "numero_cte", "serie_cte")) {
                            row.putNull(name);
                        }
                        row.put("total_cubic_volume", "-1.00000000");
                    });
            assertEquals(
                    2,
                    new JdbcAnalyticMaterializations(session, CLOCK)
                            .freight(request(fixture.run()), CancellationToken.none())
                            .ready());
            assertEquals(
                    2,
                    scalar(
                            session,
                            fixture.run(),
                            "SELECT COUNT_BIG(*) FROM pub.analytic_lab_freight_operational WHERE run_id=? AND docu"
                                    + "ment_type=N'NFS-E'"
                                    + " AND performance_days=2 AND performance_code=2 AND performance_status=N'FORA DO PRAZO"
                                    + "' AND is_cubed=1"));
            assertEquals(
                    1,
                    scalar(
                            session,
                            fixture.run(),
                            "SELECT COUNT_BIG(*) FROM pub.analytic_lab_sql_02 WHERE run_id=?"
                                    + " AND [Documento Oficial/Tipo]=N'NFS-e'"
                                    + " AND [Documento Oficial/XML]=N'<synthetic/>'"));
        }
    }

    @Test
    void excessSourceAmountScaleBlocksIndicatorsBeforeSqlCanRoundTheValue() throws Exception {
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            final var fixture = AnalyticLaboratoryDimensionsIT.start(session);
            bind(session, fixture);
            assertEquals(
                    "ANA_FREIGHT_ATTRIBUTES_CAPTURE_REQUIRED",
                    assertThrows(
                                    SQLException.class,
                                    () ->
                                            capture(
                                                    session,
                                                    fixture,
                                                    1,
                                                    "120.123456789",
                                                    true,
                                                    false,
                                                    "done",
                                                    null,
                                                    row -> {}))
                            .getMessage());
            assertEquals(
                    0,
                    new JdbcAnalyticMaterializations(session, CLOCK)
                            .freight(request(fixture.run()), CancellationToken.none())
                            .ready());
            assertEquals(
                    1,
                    scalar(
                            session,
                            fixture.run(),
                            "SELECT COUNT_BIG(*) FROM ctl.analytic_lab_source_group g JOIN ctl.expansion_lab_depen"
                                    + "dency_capture c"
                                    + " ON c.run_id=g.expansion_run WHERE g.run_id=? AND c.entity='FRETE' AND c.state='COMPLETE'"
                                    + " AND c.observed=1 AND c.quarantine=1 AND c.inserts=0 AND c.updates=0"));
        }
    }

    private static void location(
            final ColetaTemporalLaboratorySession session,
            final AnalyticLaboratoryDimensionsIT.Fixture fixture,
            final int index)
            throws Exception {
        final var row =
                ExpansionDependencyFixtures.data(DataExportTemplate.LOCALIZACAO_CARGAS, index);
        row.put("fit_dpn_delivery_prediction_at", "2036-04-03T02:59:59.999999999Z");
        final var source =
                new ExpansionDependencySource(
                        page -> page == 1 ? "[" + row + "," + row + "]" : "[]");
        final var policy =
                new ExpansionPolicy(
                        DATE, DATE.plusDays(3), DATE, 2, 100, 1000, FiscalPolicy.SYNTHETIC_CTE);
        new LocalExpansionDependencyRuntime(
                        session, fixture.expansion(), policy, CLOCK, Clock.systemUTC())
                .capture(
                        DataExportTemplate.LOCALIZACAO_CARGAS,
                        DATE,
                        ExecutionMode.BOOTSTRAP,
                        null,
                        source,
                        CancellationToken.none());
        final var unused = new ExpansionKey(ExpansionKey.Kind.STRING, "synthetic-unused");
        final var relation =
                new ExpansionRelation(
                        "synthetic-mat01-location-" + index,
                        1,
                        ExpansionRelation.Kind.LOC_FREIGHT,
                        new ExpansionKey(
                                ExpansionKey.Kind.INTEGER, Integer.toString(600000 + index)),
                        unused,
                        unused,
                        unused,
                        "INTEGER:300001",
                        DATE,
                        ExpansionRelation.Cardinality.MANY_TO_MANY,
                        true,
                        "synthetic-mat01-location");
        final var relations = new JdbcExpansionRelations(session, CLOCK);
        relations.bind(fixture.expansion(), List.of(relation), CancellationToken.none());
        relations.resolve(fixture.expansion());
    }

    private static long scalar(
            final ColetaTemporalLaboratorySession session, final UUID run, final String sql)
            throws SQLException {
        return AnalyticLaboratoryRasterIT.scalar(session, sql, run);
    }
}
