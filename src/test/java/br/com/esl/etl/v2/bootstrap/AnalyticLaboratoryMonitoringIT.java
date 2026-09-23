package br.com.esl.etl.v2.bootstrap;

import static br.com.esl.etl.v2.bootstrap.AnalyticLaboratoryInventoryIncidentQueriesIT.capture;
import static br.com.esl.etl.v2.bootstrap.AnalyticLaboratoryInventoryIncidentQueriesIT.filled;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportTemplate;
import br.com.esl.etl.v2.plataforma.persistencia.coletas.ColetaTemporalLaboratorySession;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AnalyticLaboratoryMonitoringIT {
    @Test
    void scenarioExposesRasterFiveMaterializationsAndTerminalStatusWithHonestTiming()
            throws Exception {
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            final var runtime = new AnalyticScenarioRuntime(session, java.time.Clock.systemUTC());
            final var run = runtime.start(2, 2);
            final var cycle =
                    runtime.capture(
                            run,
                            br.com.esl.etl.v2.plataforma.controle.ExecutionMode.BOOTSTRAP,
                            1,
                            false,
                            null,
                            br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken.none());
            assertTrue(cycle.status().complete());
            assertEquals(
                    5,
                    AnalyticLaboratoryRasterIT.scalar(
                            session,
                            "SELECT COUNT_BIG(DISTINCT entity) FROM pub.analytic_lab_sql_10 WHERE run_id=? "
                                    + "AND provenance IN('ANALYTIC_MATERIALIZATION','EXPANSION_MATERIALIZATION')",
                            run.id()));
            assertEquals(
                    3,
                    AnalyticLaboratoryRasterIT.scalar(
                            session,
                            "SELECT COUNT_BIG(*) FROM pub.analytic_lab_sql_10 WHERE run_id=? "
                                    + "AND provenance='ANALYTIC_MATERIALIZATION' AND [Inicio] IS NULL "
                                    + "AND [Fim] IS NOT NULL AND [Duracao (s)] IS NULL AND [Data] IS NOT NULL "
                                    + "AND [Status]='COMPLETE' AND [Total Registros]>0",
                            run.id()));
            assertEquals(
                    1,
                    AnalyticLaboratoryRasterIT.scalar(
                            session,
                            "SELECT COUNT_BIG(*) FROM pub.analytic_lab_sql_10 WHERE run_id=? "
                                    + "AND provenance='RASTER_CAPTURE' AND [Inicio] IS NOT NULL AND [Fim] IS NULL "
                                    + "AND [Status]='APPLIED' AND [Total Registros]=12",
                            run.id()));
            assertEquals(
                    1,
                    AnalyticLaboratoryRasterIT.scalar(
                            session,
                            "SELECT COUNT_BIG(*) FROM pub.analytic_lab_sql_10 WHERE run_id=? "
                                    + "AND provenance='ANALYTIC_SCENARIO' AND [Status]='COMPLETE' AND [Fim] IS NOT NULL",
                            run.id()));
        }
    }

    @Test
    void nineColumnsExposeRealQuarantineAndTimingWithoutReturningSourceText() throws Exception {
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            final var fixture = AnalyticLaboratoryDimensionsIT.start(session);
            final var row = filled(DataExportTemplate.INVENTARIO);
            row.put("cnr_c_s_fit_invoices_value", "invalid-decimal");
            row.put("cnr_c_s_fit_pyr_nickname", "SYNTHETIC_PRIVATE_PAYLOAD_CANARY");
            final UUID execution =
                    capture(session, fixture, DataExportTemplate.INVENTARIO, 1, true, row);
            try (var connection = session.getConnection();
                    var sql =
                            connection.prepareStatement(
                                    "SELECT * FROM pub.analytic_lab_sql_10 WHERE run_id=? AND event_id=?")) {
                sql.setQueryTimeout(10);
                sql.setString(1, fixture.run().toString());
                sql.setString(2, execution.toString());
                try (var result = sql.executeQuery()) {
                    assertTrue(result.next());
                    final var names =
                            List.of(
                                    "Id",
                                    "Inicio",
                                    "Fim",
                                    "Duracao (s)",
                                    "Data",
                                    "Status",
                                    "Total Registros",
                                    "Categoria Erro",
                                    "Mensagem Erro");
                    for (int index = 0; index < names.size(); index++) {
                        assertEquals(
                                names.get(index), result.getMetaData().getColumnName(index + 1));
                        assertNotNull(result.getObject(index + 1), names.get(index));
                    }
                    assertEquals(execution, UUID.fromString(result.getString("Id")));
                    assertEquals("DEGRADED", result.getString("Status"));
                    assertEquals(3, result.getLong("Total Registros"));
                    assertEquals(3, result.getLong("quarantine"));
                    assertEquals("DATA_QUALITY", result.getString("Categoria Erro"));
                    assertEquals("SYNTHETIC_SOURCE_QUARANTINE", result.getString("Mensagem Erro"));
                    assertTrue(result.getBigDecimal("Duracao (s)").compareTo(BigDecimal.ZERO) >= 0);
                    for (int index = 1; index <= result.getMetaData().getColumnCount(); index++) {
                        final String value = result.getString(index);
                        assertFalse(value != null && value.contains("PRIVATE_PAYLOAD_CANARY"));
                        assertFalse(
                                result.getMetaData()
                                        .getColumnName(index)
                                        .toLowerCase(java.util.Locale.ROOT)
                                        .contains("payload"));
                    }
                    assertFalse(result.next());
                }
            }
        }
    }

    @Test
    void sourceGroupsKeepIndependentRunsAndDoNotMultiplyExecutionCounts() throws Exception {
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            final var first = AnalyticLaboratoryDimensionsIT.start(session);
            final var second = AnalyticLaboratoryDimensionsIT.start(session);
            capture(session, first, DataExportTemplate.INVENTARIO, 1, true);
            assertEquals(
                    3,
                    AnalyticLaboratoryRasterIT.scalar(
                            session,
                            "SELECT COUNT_BIG(*) FROM pub.analytic_lab_sql_10 WHERE run_id=?",
                            first.run()));
            assertEquals(
                    2,
                    AnalyticLaboratoryRasterIT.scalar(
                            session,
                            "SELECT COUNT_BIG(*) FROM pub.analytic_lab_sql_10 WHERE run_id=?",
                            second.run()));
            assertEquals(
                    0,
                    AnalyticLaboratoryRasterIT.scalar(
                            session,
                            "SELECT COUNT_BIG(*) FROM (SELECT event_id FROM pub.analytic_lab_sql_10 WHERE run_id=?"
                                    + " GROUP BY event_id HAVING COUNT_BIG(*)>1) duplicates",
                            first.run()));
        }
    }
}
