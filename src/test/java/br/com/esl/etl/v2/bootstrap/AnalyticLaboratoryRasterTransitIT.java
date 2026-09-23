package br.com.esl.etl.v2.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.plataforma.controle.ExecutionMode;
import br.com.esl.etl.v2.plataforma.persistencia.coletas.ColetaTemporalLaboratorySession;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class AnalyticLaboratoryRasterTransitIT {
    @ParameterizedTest
    @CsvSource({
        "0,0,DIRECT,00:00",
        "43200,43200,DIRECT,720:00",
        "-1,150,PREDICTED_DIFFERENCE,02:30",
        "43201,150,PREDICTED_DIFFERENCE,02:30"
    })
    void directInclusiveRangePrecedesFallbackAndAllLegacyColumnsHavePhysicalMetadata(
            final int direct, final int expected, final String provenance, final String label)
            throws Exception {
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            final UUID run = UUID.randomUUID();
            final var runtime = AnalyticLaboratoryRasterIT.start(session, run);
            final String row =
                    AnalyticLaboratoryRasterIT.ROW
                            .replace("\"TempoTotalViagem\":90", "\"TempoTotalViagem\":" + direct)
                            .replace(
                                    "\"Tipo\":\"Entrega\"",
                                    "\"Ordem\":2,\"Tipo\":\"Entrega\",\"DataHoraPrevChegada\":\"2036-04-01T10:30:00-03:00\"");
            runtime.capture(
                    run,
                    ExecutionMode.BOOTSTRAP,
                    AnalyticLaboratoryRasterIT.WINDOW,
                    AnalyticLaboratoryRasterIT.source("[" + row + "]", 1, 1, 1, true),
                    10,
                    100,
                    CancellationToken.none());
            try (var connection = session.getConnection();
                    var statement =
                            connection.prepareStatement(
                                    "SELECT * FROM pub.analytic_lab_sql_13 WHERE run_id=?")) {
                statement.setQueryTimeout(10);
                statement.setString(1, run.toString());
                try (var result = statement.executeQuery()) {
                    final var metadata = result.getMetaData();
                    assertEquals(53, metadata.getColumnCount());
                    assertEquals("cod_solicitacao", metadata.getColumnName(1));
                    assertEquals("data_extracao_raster", metadata.getColumnName(37));
                    assertEquals(java.sql.Types.INTEGER, metadata.getColumnType(43));
                    assertTrue(result.next());
                    assertEquals(expected, result.getInt("transit_minutes"));
                    assertEquals(provenance, result.getString("duration_provenance"));
                    assertEquals(label, result.getString("TRANSIT TIME"));
                    assertEquals("ORIGEM/SP", result.getString("ORIGEM - SM"));
                    assertEquals("DESTINO/RJ", result.getString("DESTINO - SM"));
                    assertEquals("ORIGEM", result.getString("ORIGEM"));
                    assertEquals("DESTINO", result.getString("DESTINO"));
                    assertEquals("2º", result.getString("ORDEM"));
                    assertEquals("08:00", result.getString("HORÁRIO CORTE"));
                    assertEquals("10:30", result.getString("PREV. CHEGADA (destino)"));
                    assertNull(result.getObject("data_hora_real_fim"));
                    assertEquals(
                            0,
                            AnalyticLaboratoryRasterIT.scalar(
                                    session,
                                    "SELECT COUNT_BIG(*) FROM core.analytic_raster_stop s LEFT JOIN core.analytic_raster_trip t"
                                            + " ON t.run_id=s.run_id AND t.trip_key=s.trip_key WHERE s.run_id=? AND t.trip_key IS NULL",
                                    run));
                }
            }
        }
    }

    @Test
    void identicalRevisionConflictBlocksProjectionAndHigherRevisionResolves() throws Exception {
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            final UUID run = UUID.randomUUID();
            final var runtime = AnalyticLaboratoryRasterIT.start(session, run);
            final String original = "[" + AnalyticLaboratoryRasterIT.ROW + "]";
            runtime.capture(
                    run,
                    ExecutionMode.BOOTSTRAP,
                    AnalyticLaboratoryRasterIT.WINDOW,
                    AnalyticLaboratoryRasterIT.source(original, 1, 1, 1, true),
                    10,
                    100,
                    CancellationToken.none());
            final String changed = original.replace("SYNTHETIC-A", "SYNTHETIC-B");
            final var conflict =
                    runtime.capture(
                            run,
                            ExecutionMode.BACKFILL,
                            AnalyticLaboratoryRasterIT.WINDOW,
                            AnalyticLaboratoryRasterIT.source(changed, 1, 1, 1, true),
                            10,
                            100,
                            CancellationToken.none());
            assertEquals(1, conflict.receipt().quarantine());
            assertEquals(
                    0,
                    AnalyticLaboratoryRasterIT.scalar(
                            session,
                            "SELECT COUNT_BIG(*) FROM pub.analytic_lab_sql_13 WHERE run_id=?",
                            run));
            runtime.capture(
                    run,
                    ExecutionMode.BACKFILL,
                    AnalyticLaboratoryRasterIT.WINDOW,
                    AnalyticLaboratoryRasterIT.source(changed, 1, 1, 2, true),
                    10,
                    100,
                    CancellationToken.none());
            assertEquals(
                    1,
                    AnalyticLaboratoryRasterIT.scalar(
                            session,
                            "SELECT COUNT_BIG(*) FROM pub.analytic_lab_sql_13 WHERE run_id=?",
                            run));
        }
    }

    @Test
    void typedObservationCannotBeChangedBehindComparisonBytes() throws Exception {
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            final UUID run = UUID.randomUUID();
            final var runtime = AnalyticLaboratoryRasterIT.start(session, run);
            runtime.capture(
                    run,
                    ExecutionMode.BOOTSTRAP,
                    AnalyticLaboratoryRasterIT.WINDOW,
                    AnalyticLaboratoryRasterIT.source(
                            "[" + AnalyticLaboratoryRasterIT.ROW + "]", 1, 1, 1, true),
                    10,
                    100,
                    CancellationToken.none());
            try (var connection = session.getConnection();
                    var statement =
                            connection.prepareStatement(
                                    "UPDATE stg.analytic_raster_trip SET tempo_total_viagem=99 WHERE capture_id IN"
                                            + " (SELECT capture_id FROM ctl.analytic_raster_capture WHERE run_id=?)")) {
                statement.setString(1, run.toString());
                statement.setQueryTimeout(10);
                assertThrows(java.sql.SQLException.class, statement::executeUpdate);
            }
        }
    }
}
