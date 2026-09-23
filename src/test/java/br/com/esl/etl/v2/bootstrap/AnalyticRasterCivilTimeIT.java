package br.com.esl.etl.v2.bootstrap;

import static br.com.esl.etl.v2.bootstrap.AnalyticLaboratoryRasterIT.scalar;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.plataforma.controle.ExecutionMode;
import br.com.esl.etl.v2.plataforma.fonte.raster.RasterWindow;
import br.com.esl.etl.v2.plataforma.persistencia.analitico.JdbcRasterLaboratory;
import br.com.esl.etl.v2.plataforma.persistencia.coletas.ColetaTemporalLaboratorySession;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class AnalyticRasterCivilTimeIT {
    @ParameterizedTest
    @CsvSource({
        "2018-11-03T23:30:00-03:00,2018-11-04T01:30:00-02:00,60",
        "2019-02-16T23:30:00-02:00,2019-02-16T23:30:00-03:00,60",
        "1969-12-31T23:59:00Z,1970-01-01T00:01:00Z,2"
    })
    void offsetEvidenceCrossesDstAndUnixEpochWithoutInventingCivilDuration(
            final String start, final String end, final int minutes) throws Exception {
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            final UUID run = UUID.randomUUID();
            final var window = initialize(session, run, start);
            final var row = row(start);
            ((ObjectNode) row.path("ColetasEntregas").get(0)).put("DataHoraPrevChegada", end);
            final var capture =
                    new LocalRasterRuntime(
                                    session,
                                    AnalyticLaboratoryRasterIT.CLOCK,
                                    AnalyticLaboratoryRasterIT.ZONE)
                            .capture(
                                    run,
                                    ExecutionMode.BOOTSTRAP,
                                    window,
                                    AnalyticLaboratoryRasterIT.source(
                                            "[" + row + "]", 1, 1, 1, true),
                                    10,
                                    100,
                                    CancellationToken.none());
            assertEquals(0, capture.receipt().quarantine());
            try (var connection = session.getConnection();
                    var sql =
                            connection.prepareStatement(
                                    "SELECT transit_minutes,duration_provenance,data_hora_prev_ini_raster,"
                                            + "data_hora_prev_chegada_parada "
                                            + "FROM pub.analytic_lab_sql_13 WHERE run_id=?")) {
                sql.setString(1, run.toString());
                sql.setQueryTimeout(10);
                try (var result = sql.executeQuery()) {
                    assertTrue(result.next());
                    assertEquals(minutes, result.getInt(1));
                    assertEquals("PREDICTED_DIFFERENCE", result.getString(2));
                    assertEquals(
                            OffsetDateTime.parse(start), result.getObject(3, OffsetDateTime.class));
                    assertEquals(
                            OffsetDateTime.parse(end), result.getObject(4, OffsetDateTime.class));
                    assertFalse(result.next());
                }
            }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"2018-11-04T00:30:00", "2019-02-16T23:30:00"})
    void civilGapAndOverlapWithoutOffsetRemainQuarantined(final String civil) throws Exception {
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            final UUID run = UUID.randomUUID();
            final var window = initialize(session, run, civil);
            final var capture =
                    new LocalRasterRuntime(
                                    session,
                                    AnalyticLaboratoryRasterIT.CLOCK,
                                    AnalyticLaboratoryRasterIT.ZONE)
                            .capture(
                                    run,
                                    ExecutionMode.BOOTSTRAP,
                                    window,
                                    AnalyticLaboratoryRasterIT.source(
                                            "[" + row(civil) + "]", 1, 1, 1, true),
                                    10,
                                    100,
                                    CancellationToken.none());
            assertTrue(capture.receipt().quarantine() > 0);
            assertEquals(
                    0,
                    scalar(
                            session,
                            "SELECT COUNT_BIG(*) FROM pub.analytic_lab_sql_13 WHERE run_id=?",
                            run));
        }
    }

    private static RasterWindow initialize(
            final ColetaTemporalLaboratorySession session, final UUID run, final String value)
            throws Exception {
        final var date = LocalDate.parse(value.substring(0, 10));
        new JdbcRasterLaboratory(session, AnalyticLaboratoryRasterIT.CLOCK)
                .start(run, date, date.plusDays(2), AnalyticLaboratoryRasterIT.ZONE, 1000, 10);
        return new RasterWindow(date, date.plusDays(2));
    }

    private static ObjectNode row(final String start) throws Exception {
        return ((ObjectNode) new ObjectMapper().readTree(AnalyticLaboratoryRasterIT.ROW))
                .put("DataHoraPrevIni", start)
                .put("TempoTotalViagem", -1);
    }
}
