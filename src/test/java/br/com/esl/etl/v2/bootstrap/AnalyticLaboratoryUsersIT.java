package br.com.esl.etl.v2.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.plataforma.controle.ExecutionMode;
import br.com.esl.etl.v2.plataforma.fonte.graphql.AnalyticUsersSyntheticSource;
import br.com.esl.etl.v2.plataforma.persistencia.coletas.ColetaTemporalLaboratorySession;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.sql.SQLException;
import java.time.Clock;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AnalyticLaboratoryUsersIT {
    @Test
    void relayCapturePublishesExistingCurrentHistoryAndSql19ConsumesOnlyThisRun() throws Exception {
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            final UUID run = UUID.randomUUID(),
                    first = UUID.randomUUID(),
                    replay = UUID.randomUUID();
            AnalyticLaboratoryRasterIT.start(session, run);
            final var source = users(run, 21, false);
            final var initial =
                    new LocalAnalyticUsersRuntime(session, Clock.systemUTC())
                            .capture(
                                    run,
                                    first,
                                    AnalyticLaboratoryRasterIT.DATE,
                                    ExecutionMode.BACKFILL,
                                    null,
                                    source,
                                    br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken
                                            .none());
            assertEquals(21, initial.insertedRows());
            assertEquals(
                    21,
                    AnalyticLaboratoryRasterIT.scalar(
                            session,
                            "SELECT COUNT_BIG(*) FROM pub.analytic_lab_sql_19 WHERE run_id=?",
                            run));
            try (var connection = session.getConnection();
                    var sql =
                            connection.prepareStatement(
                                    "SELECT TOP(1) * FROM pub.analytic_lab_sql_19 WHERE run_id=? ORDER BY [User ID]")) {
                sql.setString(1, run.toString());
                sql.setQueryTimeout(10);
                try (var row = sql.executeQuery()) {
                    assertTrue(row.next());
                    assertEquals("User ID", row.getMetaData().getColumnName(1));
                    assertEquals("Nome", row.getMetaData().getColumnName(2));
                    assertEquals("Data Atualizacao", row.getMetaData().getColumnName(3));
                    assertEquals("SYNTHETIC USER", row.getString(2));
                    assertEquals(" SYNTHETIC USER ", row.getString("raw_name"));
                    assertTrue(row.getLong("usuario_id") > 0);
                }
            }
            final var repeated =
                    new LocalAnalyticUsersRuntime(session, Clock.systemUTC())
                            .capture(
                                    run,
                                    replay,
                                    AnalyticLaboratoryRasterIT.DATE,
                                    ExecutionMode.REPLAY,
                                    first,
                                    users(run, 21, false),
                                    br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken
                                            .none());
            assertEquals(21, repeated.noopRows());
            assertEquals(
                    21,
                    AnalyticLaboratoryRasterIT.scalar(
                            session,
                            "SELECT COUNT_BIG(*) FROM pub.analytic_lab_sql_19 WHERE run_id=?",
                            run));
            final var updated =
                    new LocalAnalyticUsersRuntime(session, Clock.systemUTC())
                            .capture(
                                    run,
                                    UUID.randomUUID(),
                                    AnalyticLaboratoryRasterIT.DATE,
                                    ExecutionMode.BACKFILL,
                                    null,
                                    users(run, 21, true),
                                    br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken
                                            .none());
            assertEquals(1, updated.updatedRows());
            assertEquals(20, updated.noopRows());
            assertEquals(
                    1,
                    AnalyticLaboratoryRasterIT.scalar(
                            session,
                            "SELECT COUNT_BIG(*) FROM pub.analytic_lab_sql_19 WHERE run_id=? AND name_presence='NU"
                                    + "LL' AND [Nome] IS NULL",
                            run));
            assertEquals(
                    0,
                    AnalyticLaboratoryRasterIT.scalar(
                            session,
                            "SELECT COUNT_BIG(*) FROM pub.analytic_lab_sql_19 WHERE run_id=?",
                            UUID.randomUUID()));
        }
    }

    @Test
    void errorEnvelopeCannotPublishAnEmptyDimension() throws Exception {
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            final UUID run = UUID.randomUUID();
            AnalyticLaboratoryRasterIT.start(session, run);
            final var error =
                    assertThrows(
                            SQLException.class,
                            () ->
                                    new LocalAnalyticUsersRuntime(session, Clock.systemUTC())
                                            .capture(
                                                    run,
                                                    UUID.randomUUID(),
                                                    AnalyticLaboratoryRasterIT.DATE,
                                                    ExecutionMode.BACKFILL,
                                                    null,
                                                    new AnalyticUsersSyntheticSource(
                                                            page ->
                                                                    "{\"errors\":[{\"message\":\"synthetic-error\"}]}"),
                                                    br.com.esl.etl.v2.plataforma.resiliencia
                                                            .CancellationToken.none()));
            assertTrue(error.getMessage().startsWith("ANA_USERS_CAPTURE_"));
            assertEquals("GraphQlResponseException", error.getCause().getClass().getSimpleName());
            assertEquals(
                    0,
                    AnalyticLaboratoryRasterIT.scalar(
                            session,
                            "SELECT COUNT_BIG(*) FROM ctl.analytic_lab_execution_source WHERE run_id=?",
                            run));
        }
    }

    static AnalyticUsersSyntheticSource users(
            final UUID run, final int count, final boolean clearFirstName) {
        return new AnalyticUsersSyntheticSource(
                page -> {
                    final var envelope = JsonNodeFactory.instance.objectNode();
                    final var individual = envelope.putObject("data").putObject("individual");
                    final var edges = individual.putArray("edges");
                    final int end = Math.min(count, page * 20);
                    for (int index = (page - 1) * 20; index < end; index++) {
                        final var node = edges.addObject().putObject("node");
                        node.put("id", "synthetic-analytic-" + run + "-" + index);
                        if (clearFirstName && index == 0) {
                            node.putNull("name");
                        } else {
                            node.put("name", " SYNTHETIC USER ");
                        }
                    }
                    final var info =
                            individual.putObject("pageInfo").put("hasNextPage", end < count);
                    if (end < count) {
                        info.put("endCursor", "synthetic-page-" + page);
                    } else {
                        info.putNull("endCursor");
                    }
                    return envelope.toString();
                });
    }
}
