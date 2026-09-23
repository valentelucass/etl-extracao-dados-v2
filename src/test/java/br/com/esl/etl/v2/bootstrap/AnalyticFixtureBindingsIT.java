package br.com.esl.etl.v2.bootstrap;

import static br.com.esl.etl.v2.bootstrap.AnalyticLaboratoryFreightOperationalIT.DATE;
import static br.com.esl.etl.v2.bootstrap.AnalyticLaboratoryRasterIT.scalar;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.plataforma.analitico.AnalyticSqlContract;
import br.com.esl.etl.v2.plataforma.controle.ExecutionMode;
import br.com.esl.etl.v2.plataforma.persistencia.analitico.JdbcAnalyticFixtureBindings;
import br.com.esl.etl.v2.plataforma.persistencia.analitico.JdbcAnalyticQueries;
import br.com.esl.etl.v2.plataforma.persistencia.coletas.ColetaTemporalLaboratorySession;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AnalyticFixtureBindingsIT {
    @Test
    void currentCaptureProvenanceFeedsSixDimensionsWithoutMergingHomonymousUsers()
            throws Exception {
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            final var fixture = AnalyticLaboratoryDimensionsIT.start(session);
            new LocalAnalyticUsersRuntime(session, Clock.systemUTC())
                    .capture(
                            fixture.run(),
                            UUID.randomUUID(),
                            DATE,
                            ExecutionMode.BACKFILL,
                            null,
                            AnalyticUsersFixtures.source(fixture.run(), 21, false),
                            CancellationToken.none());
            final var bindings = new JdbcAnalyticFixtureBindings(session);
            assertEquals(
                    31,
                    bindings.bind(fixture.run(), 1, false, false, false, CancellationToken.none()));
            assertEquals(
                    31,
                    bindings.bind(fixture.run(), 1, false, false, false, CancellationToken.none()));
            final var queries = new JdbcAnalyticQueries(session);
            for (final var query :
                    List.of(
                            AnalyticSqlContract.SQL_14,
                            AnalyticSqlContract.SQL_15,
                            AnalyticSqlContract.SQL_16,
                            AnalyticSqlContract.SQL_17,
                            AnalyticSqlContract.SQL_18,
                            AnalyticSqlContract.SQL_19)) {
                assertTrue(
                        queries.read(
                                                fixture.run(),
                                                query,
                                                1,
                                                64,
                                                CancellationToken.none(),
                                                row -> {})
                                        .rows()
                                > 0,
                        query.name());
            }
            assertEquals(
                    21,
                    scalar(
                            session,
                            "SELECT COUNT_BIG(*) FROM pub.analytic_lab_sql_19 WHERE run_id=?",
                            fixture.run()));
            assertEquals(
                    1,
                    scalar(
                            session,
                            "SELECT COUNT_BIG(DISTINCT Nome) FROM pub.analytic_lab_sql_19 WHERE run_id=?",
                            fixture.run()));
        }
    }
}
