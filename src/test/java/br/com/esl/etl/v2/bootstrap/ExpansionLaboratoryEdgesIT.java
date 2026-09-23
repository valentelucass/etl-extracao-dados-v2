package br.com.esl.etl.v2.bootstrap;

import static br.com.esl.etl.v2.bootstrap.ExpansionLaboratoryLocalIntegrationIT.CLOCK;
import static br.com.esl.etl.v2.bootstrap.ExpansionLaboratoryLocalIntegrationIT.DATE;
import static br.com.esl.etl.v2.bootstrap.ExpansionLaboratoryLocalIntegrationIT.NONE;
import static br.com.esl.etl.v2.bootstrap.ExpansionLaboratoryLocalIntegrationIT.scalar;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.modulos.faturasporcliente.domain.FaturaClienteRules.FiscalPolicy;
import br.com.esl.etl.v2.plataforma.controle.ExecutionMode;
import br.com.esl.etl.v2.plataforma.expansao.ExpansionPolicy;
import br.com.esl.etl.v2.plataforma.expansao.ExpansionProjection;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportTemplate;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.ExpansionSyntheticSource;
import br.com.esl.etl.v2.plataforma.persistencia.coletas.ColetaTemporalLaboratorySession;
import br.com.esl.etl.v2.plataforma.persistencia.expansao.JdbcExpansionLaboratory;
import br.com.esl.etl.v2.plataforma.persistencia.expansao.JdbcExpansionMaterializations;
import br.com.esl.etl.v2.plataforma.persistencia.expansao.JdbcExpansionQueries;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

class ExpansionLaboratoryEdgesIT {
    @ParameterizedTest
    @EnumSource(
            value = DataExportTemplate.class,
            names = {"CONTAS_A_PAGAR", "FATURAS_POR_CLIENTE", "INVENTARIO", "SINISTROS"})
    void dstCaptureUsesTwentyThreeHoursAndExactFreshnessInSql(final DataExportTemplate template)
            throws SQLException {
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            final UUID run = UUID.randomUUID();
            final LocalDate date = LocalDate.of(2018, 11, 4);
            final var policy =
                    new ExpansionPolicy(
                            date, date.plusDays(1), date, 2, 10, 10, FiscalPolicy.UNRESOLVED);
            new JdbcExpansionLaboratory(session, CLOCK).start(run, policy);
            final var data = ExpansionLaboratoryFixtures.data(template);
            switch (template) {
                case CONTAS_A_PAGAR -> {
                    data.putNull("created_at");
                    data.putNull("ant_ils_atn_liquidation_date");
                    data.put("ant_ils_atn_transaction_date", "2018-11-04");
                }
                case FATURAS_POR_CLIENTE -> {
                    data.putNull("fit_fhe_cte_issued_at");
                    data.putNull("fit_ant_ils_atn_transaction_date");
                    data.put("fit_ant_issue_date", "2018-11-04");
                    data.put("fit_ant_ils_due_date", "2018-11-04");
                    data.put("fit_ant_ils_original_due_date", "2018-11-04");
                }
                case INVENTARIO ->
                        data.put(
                                "cnr_c_s_fit_dpn_performance_finished_at",
                                "2018-11-04T12:00:00.123456789-02:00");
                case SINISTROS -> {
                    data.putNull("icm_ttt_treatment_at");
                    data.put("opening_at_date", "2018-11-04");
                }
                default -> throw new IllegalArgumentException("EXP_TEST_TEMPLATE");
            }
            final var row = ExpansionLaboratoryFixtures.envelope(template, data, 1, 1, 1);
            final var runtime =
                    new LocalExpansionRuntime(session, run, policy, CLOCK, Clock.systemUTC());
            assertEquals(
                    1,
                    runtime.capture(
                                    template,
                                    date,
                                    ExecutionMode.BOOTSTRAP,
                                    null,
                                    new ExpansionSyntheticSource(
                                            page -> page == 1 ? "[" + row + "]" : "[]"),
                                    NONE)
                            .receipt()
                            .inserts());
            assertEquals(
                    23 * 3600,
                    scalar(
                            session,
                            "SELECT DATEDIFF_BIG(SECOND,p.partition_start_utc,p.partition_end_exclusive_utc) "
                                    + "FROM ctl.execution_partition p JOIN ctl.execution_attempt a ON "
                                    + "a.partition_id=p.partition_id "
                                    + "JOIN ctl.expansion_lab_capture c ON c.execution_id=a.execution_id WHERE c.run_id=?",
                            run));
            final String expected =
                    template == DataExportTemplate.SINISTROS
                            ? "2018-11-04T03:00:00Z"
                            : template == DataExportTemplate.INVENTARIO
                                    ? "2018-11-04T14:00:00Z"
                                    : "2018-11-05T02:00:00Z";
            assertEquals(
                    Instant.parse(expected).getEpochSecond(),
                    scalar(
                            session,
                            "SELECT fresh_second FROM stg.expansion_lab_observation WHERE run_id=?",
                            run));
            assertEquals(
                    template == DataExportTemplate.INVENTARIO ? 123456789 : 0,
                    scalar(
                            session,
                            "SELECT fresh_nano FROM stg.expansion_lab_observation WHERE run_id=?",
                            run));
        }
    }

    @ParameterizedTest
    @EnumSource(
            value = DataExportTemplate.class,
            names = {"FATURAS_POR_CLIENTE", "INVENTARIO"})
    void arrayOrderPaddingAndDuplicatesRemainPhysicalNotDocumentIdentity(
            final DataExportTemplate template) throws SQLException {
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            final UUID run = UUID.randomUUID();
            final var runtime = ExpansionLaboratoryLocalIntegrationIT.runtime(session, run, 2, 100);
            final String field =
                    template == DataExportTemplate.INVENTARIO
                            ? "cnr_c_s_fit_invoices_mapping"
                            : "invoices_mapping";
            final var first =
                    ExpansionLaboratoryAdversarialIT.row(template, 1, 1, "100", 1, 1, true, false);
            ((ObjectNode) first.path("data")).putArray(field).add("X").add("X ").add("x").add("X");
            final var capture = ExpansionLaboratoryAdversarialIT.capture(runtime, template, first);
            assertEquals(1, capture.receipt().inserts());
            try (var connection = session.getConnection();
                    var sql =
                            connection.prepareStatement(
                                    "SELECT text_value FROM stg.expansion_lab_array_item WHERE execution_id=? AND "
                                            + "field_name=? ORDER BY physical_position")) {
                sql.setQueryTimeout(10);
                sql.setString(1, capture.executionId().toString());
                sql.setString(2, field);
                try (var rows = sql.executeQuery()) {
                    for (final String value : new String[] {"X", "X ", "x", "X"}) {
                        assertTrue(rows.next());
                        assertEquals(value, rows.getString(1));
                    }
                }
            }
            final var reordered = first.deepCopy();
            ((ObjectNode) reordered.path("data"))
                    .putArray(field)
                    .add("X ")
                    .add("x")
                    .add("X")
                    .add("X");
            assertEquals(
                    1,
                    ExpansionLaboratoryAdversarialIT.capture(runtime, template, reordered)
                            .receipt()
                            .quarantine());
            assertEquals(
                    1,
                    scalar(
                            session,
                            "SELECT COUNT_BIG(*) FROM core.expansion_lab_root WHERE run_id=?",
                            run));
            assertEquals(
                    0,
                    scalar(
                            session,
                            "SELECT COUNT_BIG(*) FROM core.expansion_lab_link WHERE run_id=?",
                            run));
            final var overflow = first.deepCopy();
            final var array = ((ObjectNode) overflow.path("data")).putArray(field);
            for (int index = 0; index < 33; index++) {
                array.add("synthetic-array-item");
            }
            assertEquals(
                    1,
                    ExpansionLaboratoryAdversarialIT.capture(runtime, template, overflow)
                            .receipt()
                            .quarantine());
        }
    }

    @Test
    void staleInventoryProofIsCumulativeAcrossLaterNegativeObservation() throws SQLException {
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            final UUID run = UUID.randomUUID();
            final var runtime = ExpansionLaboratoryLocalIntegrationIT.runtime(session, run, 2, 100);
            final var template = DataExportTemplate.INVENTARIO;
            final var first =
                    ExpansionLaboratoryAdversarialIT.row(template, 1, 1, "100", 2, 1, true, false);
            ((ObjectNode) first.path("data"))
                    .put("cnr_c_s_fit_fte_lce_ore_description", "SYNTHETIC WITHOUT PROOF");
            ExpansionLaboratoryAdversarialIT.capture(runtime, template, first);
            final var old =
                    ExpansionLaboratoryAdversarialIT.row(template, 1, 1, "100", 1, 1, true, false);
            assertEquals(
                    1,
                    ExpansionLaboratoryAdversarialIT.capture(runtime, template, old)
                            .receipt()
                            .stale());
            final var latest =
                    ExpansionLaboratoryAdversarialIT.row(template, 1, 1, "100", 3, 1, true, false);
            ((ObjectNode) latest.path("data"))
                    .put("cnr_c_s_fit_fte_lce_ore_description", "SYNTHETIC WITHOUT PROOF");
            ExpansionLaboratoryAdversarialIT.capture(runtime, template, latest);
            final var row =
                    (ExpansionProjection.Inventory)
                            new JdbcExpansionQueries(session)
                                    .detailPage(run, JdbcExpansionQueries.Vertical.INV, 1, 0, 1)
                                    .get(0);
            assertTrue(row.proofAttached());
        }
    }

    @ParameterizedTest
    @CsvSource({"SYNTHETIC_CTE,7", "SYNTHETIC_NFSE,9"})
    void explicitFiscalAlternativesPreserveBothEvidences(
            final FiscalPolicy fiscal, final String official) throws SQLException {
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            final UUID run = UUID.randomUUID();
            final var policy =
                    new ExpansionPolicy(
                            DATE, DATE.plusDays(3), DATE.plusDays(14), 2, 100, 100, fiscal);
            new JdbcExpansionLaboratory(session, CLOCK).start(run, policy);
            final var row =
                    ExpansionLaboratoryAdversarialIT.row(
                            DataExportTemplate.FATURAS_POR_CLIENTE, 1, 1, "100", 1, 1, true, false);
            ((ObjectNode) row.path("data")).put("fit_nse_number", 9);
            ExpansionLaboratoryAdversarialIT.capture(
                    new LocalExpansionRuntime(session, run, policy, CLOCK, Clock.systemUTC()),
                    DataExportTemplate.FATURAS_POR_CLIENTE,
                    row);
            final var result =
                    (ExpansionProjection.InvoiceCustomer)
                            new JdbcExpansionQueries(session)
                                    .detailPage(run, JdbcExpansionQueries.Vertical.FAT, 1, 0, 1)
                                    .get(0);
            assertEquals("7", result.cteNumber());
            assertEquals("9", result.nfseNumber());
            assertEquals(official, result.officialNumber());
            assertEquals("RESOLVED", result.fiscalState());
            assertEquals("ABSENT_UNSOURCED_LEGACY", result.nfseSeriesState());
        }
    }

    @ParameterizedTest
    @CsvSource({
        "NONE,sem_fatura",
        "PAID,baixado",
        "UNDATED,sem_vencimento",
        "PAST,vencido",
        "FUTURE,a_vencer"
    })
    void materializedPaymentStatesUseInjectedBusinessDate(final String input, final String expected)
            throws SQLException {
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            final UUID run = UUID.randomUUID();
            ExpansionLaboratoryInvoicesIT.prepare(
                    session,
                    run,
                    1,
                    ExpansionLaboratoryInvoicesIT.fat(
                            1,
                            1,
                            data -> {
                                switch (input) {
                                    case "NONE" -> data.put("fit_ant_document", "Faturado");
                                    case "PAID" ->
                                            data.put(
                                                    "fit_ant_ils_atn_transaction_date",
                                                    "2036-04-12");
                                    case "UNDATED" -> data.putNull("fit_ant_ils_due_date");
                                    case "FUTURE" -> data.put("fit_ant_ils_due_date", "2036-04-20");
                                    case "PAST" -> data.put("fit_ant_ils_due_date", "2036-04-10");
                                    default ->
                                            throw new IllegalArgumentException(
                                                    "EXP_TEST_PAYMENT_CASE");
                                }
                            }));
            new JdbcExpansionMaterializations(session, CLOCK)
                    .invoices(
                            run,
                            UUID.randomUUID(),
                            1,
                            ExecutionMode.BOOTSTRAP,
                            true,
                            DATE,
                            DATE.plusDays(3));
            assertEquals(
                    expected,
                    new JdbcExpansionQueries(session)
                            .invoiceFactsPage(run, 0, 1)
                            .get(0)
                            .paymentState());
        }
    }
}
