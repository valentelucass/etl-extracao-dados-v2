package br.com.esl.etl.v2.bootstrap;

import static br.com.esl.etl.v2.bootstrap.ExpansionLaboratoryLocalIntegrationIT.DATE;
import static br.com.esl.etl.v2.bootstrap.ExpansionLaboratoryLocalIntegrationIT.NONE;
import static br.com.esl.etl.v2.bootstrap.ExpansionLaboratoryLocalIntegrationIT.scalar;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.plataforma.controle.ExecutionMode;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportTemplate;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.ExpansionSyntheticSource;
import br.com.esl.etl.v2.plataforma.persistencia.coletas.ColetaTemporalLaboratorySession;
import br.com.esl.etl.v2.plataforma.persistencia.expansao.JdbcExpansionLaboratory;
import br.com.esl.etl.v2.plataforma.persistencia.expansao.JdbcExpansionQueries;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.sql.SQLException;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class ExpansionLaboratoryAdversarialIT {
    @ParameterizedTest
    @EnumSource(
            value = DataExportTemplate.class,
            names = {"CONTAS_A_PAGAR", "FATURAS_POR_CLIENTE", "INVENTARIO", "SINISTROS"})
    void staleTieCorrectionInactiveAmountsAndExplicitReactivation(final DataExportTemplate template)
            throws SQLException {
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            final UUID run = UUID.randomUUID();
            final var runtime = ExpansionLaboratoryLocalIntegrationIT.runtime(session, run, 2, 100);
            final var first = row(template, 1, 1, "100", 1, 1, true, false);
            final var second = row(template, 2, 2, "100", 1, 1, true, false);
            assertEquals(2, capture(runtime, template, first, second).receipt().inserts());
            assertEquals(
                    1,
                    capture(runtime, template, row(template, 1, 1, "100", 2, 1, true, false))
                            .receipt()
                            .updates());
            assertEquals(
                    1,
                    capture(runtime, template, row(template, 1, 1, "50", 1, 1, true, false))
                            .receipt()
                            .stale());
            assertEquals(
                    1,
                    capture(runtime, template, row(template, 1, 1, "200", 2, 1, true, false))
                            .receipt()
                            .quarantine());
            assertEquals(
                    1,
                    capture(runtime, template, row(template, 1, 1, "100", 2, 2, true, false))
                            .receipt()
                            .updates());
            assertEquals(
                    1,
                    capture(runtime, template, row(template, 1, 1, "200", 2, 3, false, false))
                            .receipt()
                            .updates());
            assertEquals(
                    100,
                    scalar(
                            session,
                            "SELECT CONVERT(BIGINT,amount) FROM core.expansion_lab_root WHERE run_id=?",
                            run));
            capture(runtime, template, row(template, 1, 2, "100", 2, 3, false, false));
            assertEquals(
                    0,
                    scalar(
                            session,
                            "SELECT COUNT_BIG(*) FROM core.expansion_lab_root WHERE run_id=? AND active=1",
                            run));
            final var vertical =
                    JdbcExpansionQueries.Vertical.valueOf(
                            JdbcExpansionLaboratory.vertical(template));
            assertEquals(
                    0,
                    new JdbcExpansionQueries(session).detailPage(run, vertical, 1, 0, 100).size());
            assertEquals(
                    1,
                    capture(runtime, template, row(template, 1, 2, "100", 2, 4, true, false))
                            .receipt()
                            .quarantine());
            assertEquals(
                    1,
                    capture(runtime, template, row(template, 1, 2, "100", 2, 5, true, true))
                            .receipt()
                            .updates());
            assertEquals(
                    100,
                    scalar(
                            session,
                            "SELECT CONVERT(BIGINT,amount) FROM core.expansion_lab_root WHERE run_id=?",
                            run));
            assertEquals(
                    1,
                    new JdbcExpansionQueries(session).detailPage(run, vertical, 1, 0, 100).size());
        }
    }

    @ParameterizedTest
    @EnumSource(
            value = DataExportTemplate.class,
            names = {"CONTAS_A_PAGAR", "FATURAS_POR_CLIENTE", "INVENTARIO", "SINISTROS"})
    void sqlRetainsAbsentNullNumericZeroStringZeroAndInvalidDecimal(
            final DataExportTemplate template) throws SQLException {
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            final UUID run = UUID.randomUUID();
            final var runtime = ExpansionLaboratoryLocalIntegrationIT.runtime(session, run, 2, 100);
            final String field = amountField(template);
            final var rows = new ObjectNode[5];
            for (int index = 0; index < rows.length; index++) {
                rows[index] = row(template, index + 1, index + 1, "0", 1, 1, true, false);
            }
            ((ObjectNode) rows[0].path("data")).remove(field);
            ((ObjectNode) rows[1].path("data")).putNull(field);
            ((ObjectNode) rows[2].path("data")).put(field, 0);
            ((ObjectNode) rows[4].path("data")).put(field, "1.000000001");
            final var result = capture(runtime, template, rows);
            assertEquals(5, result.receipt().observations());
            assertEquals(1, result.receipt().quarantine());
            // Identifiers come exclusively from the fixed template/field switch in this test.
            final String table =
                    "stg.expansion_lab_"
                            + JdbcExpansionLaboratory.vertical(template)
                                    .toLowerCase(java.util.Locale.ROOT);
            try (var connection = session.getConnection();
                    var sql =
                            connection.prepareStatement(
                                    "SELECT d.["
                                            + field
                                            + "_p],d.["
                                            + field
                                            + "_w],d.["
                                            + field
                                            + "_raw],d.["
                                            + field
                                            + "] FROM "
                                            + table
                                            + " d JOIN ctl.expansion_lab_capture c ON c.execution_id=d.execution_id WHERE "
                                            + "c.run_id=? ORDER BY d.occurrence")) {
                sql.setQueryTimeout(10);
                sql.setString(1, run.toString());
                try (var data = sql.executeQuery()) {
                    for (final String presence :
                            new String[] {"ABSENT", "NULL", "VALUE", "VALUE", "VALUE"}) {
                        assertTrue(data.next());
                        assertEquals(presence, data.getString(1));
                        if (data.getRow() == 3) {
                            assertEquals("INTEGER", data.getString(2));
                            assertEquals(0, data.getBigDecimal(4).signum());
                        }
                        if (data.getRow() == 4) {
                            assertEquals("STRING", data.getString(2));
                            assertEquals(0, data.getBigDecimal(4).signum());
                        }
                        if (data.getRow() == 5) {
                            assertEquals("1.000000001", data.getString(3));
                        }
                    }
                }
            }
        }
    }

    @ParameterizedTest
    @EnumSource(
            value = DataExportTemplate.class,
            names = {"CONTAS_A_PAGAR", "FATURAS_POR_CLIENTE", "INVENTARIO", "SINISTROS"})
    void typedSyntheticRootsAndExactTextNeverCollapseIntoSourceCandidate(
            final DataExportTemplate template) throws SQLException {
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            final UUID run = UUID.randomUUID();
            final var runtime = ExpansionLaboratoryLocalIntegrationIT.runtime(session, run, 2, 100);
            final var rows = new ObjectNode[4];
            for (int index = 0; index < rows.length; index++) {
                rows[index] = row(template, index + 1, 1, "100", 1, 1, true, false);
            }
            ((ObjectNode) rows[0].path("binding")).put("root", 7);
            ((ObjectNode) rows[1].path("binding")).put("root", "7");
            ((ObjectNode) rows[2].path("binding")).put("root", "synthetic-root-A");
            ((ObjectNode) rows[3].path("binding")).put("root", "synthetic-root-a");
            assertEquals(4, capture(runtime, template, rows).receipt().inserts());
            assertEquals(
                    4,
                    scalar(
                            session,
                            "SELECT COUNT_BIG(*) FROM core.expansion_lab_root WHERE run_id=?",
                            run));
            ((ObjectNode) rows[0].path("binding")).put("root", "synthetic-root-A ");
            assertThrows(RuntimeException.class, () -> capture(runtime, template, rows[0]));
            assertEquals(
                    4,
                    scalar(
                            session,
                            "SELECT COUNT_BIG(*) FROM core.expansion_lab_root WHERE run_id=?",
                            run));
        }
    }

    @ParameterizedTest
    @EnumSource(
            value = DataExportTemplate.class,
            names = {"CONTAS_A_PAGAR", "FATURAS_POR_CLIENTE", "INVENTARIO", "SINISTROS"})
    void cancellationAndBudgetCloseResourcesAndCannotLeaveCompleteCapture(
            final DataExportTemplate template) throws SQLException {
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            final UUID run = UUID.randomUUID();
            final var runtime = ExpansionLaboratoryLocalIntegrationIT.runtime(session, run, 1, 1);
            final var cancelled = new AtomicBoolean();
            final var closed = new AtomicBoolean();
            final var value = row(template, 1, 1, "100", 1, 1, true, false);
            final var source =
                    new ExpansionSyntheticSource(
                                    page -> {
                                        if (page == 2) {
                                            cancelled.set(true);
                                        }
                                        return page == 1 ? "[" + value + "]" : "[]";
                                    })
                            .observed(
                                    new ExpansionSyntheticSource.Observer() {
                                        @Override
                                        public void captureClosed() {
                                            closed.set(true);
                                        }
                                    });
            assertThrows(
                    RuntimeException.class,
                    () ->
                            runtime.capture(
                                    template,
                                    DATE,
                                    ExecutionMode.BACKFILL,
                                    null,
                                    source,
                                    cancelled::get));
            assertTrue(closed.get());
            assertTrue(cancelled.get());
            assertEquals(
                    0,
                    scalar(
                            session,
                            "SELECT COUNT_BIG(*) FROM ctl.expansion_lab_capture WHERE run_id=?",
                            run));
            assertEquals(
                    0,
                    scalar(
                            session,
                            "SELECT COUNT_BIG(*) FROM stg.expansion_lab_observation WHERE run_id=?",
                            run));
            assertThrows(
                    RuntimeException.class,
                    () ->
                            capture(
                                    runtime,
                                    template,
                                    value,
                                    row(template, 2, 2, "100", 1, 1, true, false)));
            assertEquals(
                    0,
                    scalar(
                            session,
                            "SELECT COUNT_BIG(*) FROM core.expansion_lab_root WHERE run_id=?",
                            run));
            assertTrue(session.releasedConnections() > 0);
        }
    }

    static LocalExpansionRuntime.Capture capture(
            final LocalExpansionRuntime runtime,
            final DataExportTemplate template,
            final ObjectNode... rows)
            throws SQLException {
        return runtime.capture(
                template,
                DATE,
                ExecutionMode.BACKFILL,
                null,
                new ExpansionSyntheticSource(
                        page -> {
                            final var array =
                                    com.fasterxml.jackson.databind.node.JsonNodeFactory.instance
                                            .arrayNode();
                            // One physical occurrence per page keeps the helper inside every
                            // declared page bound.
                            if (page <= rows.length) {
                                array.add(rows[page - 1]);
                            }
                            return array.toString();
                        }),
                NONE);
    }

    static ObjectNode row(
            final DataExportTemplate template,
            final int occurrence,
            final int component,
            final String amount,
            final int day,
            final int revision,
            final boolean active,
            final boolean reactivation) {
        final var data = ExpansionLaboratoryFixtures.data(template);
        data.put(amountField(template), amount);
        final String timestamp = "2036-04-0" + (day + 1) + "T12:00:00.123456789-03:00";
        final String clockField =
                switch (template) {
                    case CONTAS_A_PAGAR -> "created_at";
                    case FATURAS_POR_CLIENTE -> "fit_fhe_cte_issued_at";
                    case INVENTARIO -> "cnr_c_s_fit_dpn_performance_finished_at";
                    case SINISTROS -> "icm_ttt_treatment_at";
                    default -> throw new IllegalArgumentException("EXP_TEST_TEMPLATE");
                };
        data.put(clockField, timestamp);
        if (template == DataExportTemplate.FATURAS_POR_CLIENTE) {
            // Keep the other four freshness inputs below the timestamp controlled by this test.
            data.put("fit_ant_ils_due_date", "2036-04-01");
            data.put("fit_ant_ils_original_due_date", "2036-04-01");
        }
        final var row =
                ExpansionLaboratoryFixtures.envelope(template, data, occurrence, 1, component);
        ((ObjectNode) row.path("binding"))
                .put("revision", revision)
                .put("active", active)
                .put("reactivation", reactivation);
        return row;
    }

    private static String amountField(final DataExportTemplate template) {
        return switch (template) {
            case CONTAS_A_PAGAR -> "value";
            case FATURAS_POR_CLIENTE -> "fit_ant_value";
            case INVENTARIO -> "cnr_c_s_fit_invoices_value";
            case SINISTROS -> "insurance_claim_total";
            default -> throw new IllegalArgumentException("EXP_TEST_TEMPLATE");
        };
    }
}
