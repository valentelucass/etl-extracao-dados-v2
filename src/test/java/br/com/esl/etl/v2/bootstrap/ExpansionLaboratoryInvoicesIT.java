package br.com.esl.etl.v2.bootstrap;

import static br.com.esl.etl.v2.bootstrap.ExpansionLaboratoryLocalIntegrationIT.CLOCK;
import static br.com.esl.etl.v2.bootstrap.ExpansionLaboratoryLocalIntegrationIT.DATE;
import static br.com.esl.etl.v2.bootstrap.ExpansionLaboratoryLocalIntegrationIT.NONE;
import static br.com.esl.etl.v2.bootstrap.ExpansionLaboratoryLocalIntegrationIT.scalar;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import br.com.esl.etl.v2.plataforma.controle.ExecutionMode;
import br.com.esl.etl.v2.plataforma.expansao.ExpansionRelation;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportTemplate;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.ExpansionSyntheticSource;
import br.com.esl.etl.v2.plataforma.persistencia.coletas.ColetaTemporalLaboratorySession;
import br.com.esl.etl.v2.plataforma.persistencia.expansao.JdbcExpansionMaterializations;
import br.com.esl.etl.v2.plataforma.persistencia.expansao.JdbcExpansionQueries;
import br.com.esl.etl.v2.plataforma.persistencia.expansao.JdbcExpansionReferences;
import br.com.esl.etl.v2.plataforma.persistencia.expansao.JdbcExpansionRelations;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.UUID;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ExpansionLaboratoryInvoicesIT {
    @Test
    void oneTitleAmountDespiteTwoDocumentsAndRepeatedPhysicalLines() throws SQLException {
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            final UUID run = UUID.randomUUID();
            prepare(session, run, 2, fat(2, 1, row -> {}));
            final var material = new JdbcExpansionMaterializations(session, CLOCK);
            final UUID receipt = UUID.randomUUID();
            final var result =
                    material.invoices(
                            run, receipt, 1, ExecutionMode.BOOTSTRAP, true, DATE, DATE.plusDays(3));
            assertEquals(new JdbcExpansionMaterializations.Receipt(2, 2, 0, 0, 2, 0), result);
            final var rows = new JdbcExpansionQueries(session).invoiceFactsPage(run, 0, 100);
            assertEquals(2, rows.size());
            assertEquals(new BigDecimal("100.00000000"), rows.get(0).operationalValue());
            assertEquals(2, rows.get(0).documents());
            assertEquals(1, rows.get(0).freights());
            assertEquals("Faturado", rows.get(0).processState());
            assertEquals("vencido", rows.get(0).paymentState());
            assertEquals(5, rows.get(0).daysPastDue());
            assertEquals(DATE, rows.get(0).baseDate());
            assertEquals("cnpj:00000000000000", rows.get(0).clientKey());
            assertEquals(
                    200,
                    scalar(
                            session,
                            "SELECT CONVERT(BIGINT,SUM(operational_value)) FROM mart.expansion_lab_invoice "
                                    + "WHERE run_id=? AND disposition='READY'",
                            run));
            assertEquals(
                    result,
                    material.invoices(
                            run,
                            receipt,
                            1,
                            ExecutionMode.BOOTSTRAP,
                            true,
                            DATE,
                            DATE.plusDays(3)));
            assertEquals(
                    2,
                    material.invoices(
                                    run,
                                    UUID.randomUUID(),
                                    1,
                                    ExecutionMode.REPLAY,
                                    true,
                                    DATE,
                                    DATE.plusDays(3))
                            .noops());
            assertEquals(
                    2,
                    scalar(
                            session,
                            "SELECT COUNT_BIG(*) FROM mart.expansion_lab_invoice_history h JOIN "
                                    + "mart.expansion_lab_invoice f ON f.root_id=h.root_id WHERE f.run_id=?",
                            run));
        }
    }

    @Test
    void correctedIssueDateUpdatesSameRootAndLeavesUnrelatedPartitionUntouched()
            throws SQLException {
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            final UUID run = UUID.randomUUID();
            final var runtime = prepare(session, run, 2, fat(2, 1, row -> {}));
            final var material = new JdbcExpansionMaterializations(session, CLOCK);
            final var queries = new JdbcExpansionQueries(session);
            material.invoices(
                    run,
                    UUID.randomUUID(),
                    1,
                    ExecutionMode.BOOTSTRAP,
                    true,
                    DATE,
                    DATE.plusDays(3));
            final long root = queries.invoiceFactsPage(run, 0, 1).get(0).rootId();
            runtime.capture(
                    DataExportTemplate.FATURAS_POR_CLIENTE,
                    DATE.plusDays(1),
                    ExecutionMode.INCREMENTAL,
                    null,
                    fat(1, 2, row -> row.put("fit_ant_issue_date", "2036-04-02")),
                    NONE);
            final var corrected =
                    material.invoices(
                            run,
                            UUID.randomUUID(),
                            1,
                            ExecutionMode.INCREMENTAL,
                            false,
                            DATE.plusDays(1),
                            DATE.plusDays(2));
            assertEquals(1, corrected.candidates());
            assertEquals(1, corrected.updates());
            assertEquals(0, corrected.inserts());
            final var facts = queries.invoiceFactsPage(run, 0, 100);
            assertEquals(2, facts.size());
            assertEquals(root, facts.get(0).rootId());
            assertEquals(DATE.plusDays(1), facts.get(0).issueDate());
            assertEquals(DATE, facts.get(1).issueDate());
            assertEquals(
                    200,
                    scalar(
                            session,
                            "SELECT CONVERT(BIGINT,SUM(operational_value)) FROM mart.expansion_lab_invoice "
                                    + "WHERE run_id=?",
                            run));
        }
    }

    @ParameterizedTest
    @CsvSource({
        "ZERO,0.00000000",
        "NULL,120.00000000",
        "BOTH_NULL,0.00000000",
        "MAX,99999999999999999999.99999999"
    })
    void explicitFinancialFallbackPreservesZeroNullAndDecimalLimit(
            final String input, final String expected) throws SQLException {
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            final UUID run = UUID.randomUUID();
            prepare(
                    session,
                    run,
                    1,
                    fat(
                            1,
                            1,
                            row -> {
                                switch (input) {
                                    case "ZERO" -> row.put("fit_ant_value", "0");
                                    case "NULL" -> row.putNull("fit_ant_value");
                                    case "BOTH_NULL" -> {
                                        row.putNull("fit_ant_value");
                                        row.putNull("total");
                                    }
                                    case "MAX" -> row.put("fit_ant_value", expected);
                                    default ->
                                            throw new IllegalArgumentException("UNKNOWN_FIXTURE");
                                }
                            }));
            final var receipt =
                    new JdbcExpansionMaterializations(session, CLOCK)
                            .invoices(
                                    run,
                                    UUID.randomUUID(),
                                    1,
                                    ExecutionMode.BOOTSTRAP,
                                    true,
                                    DATE,
                                    DATE.plusDays(3));
            assertEquals(1, receipt.ready());
            assertEquals(
                    new BigDecimal(expected),
                    new JdbcExpansionQueries(session)
                            .invoiceFactsPage(run, 0, 1)
                            .get(0)
                            .operationalValue());
        }
    }

    @ParameterizedTest
    @CsvSource({
        "NULL_DATE,NULL_DATE",
        "DUAL,FISCAL_UNRESOLVED",
        "CONFLICT,FIELD_CONFLICT",
        "REFERENCE,REFERENCE_MISSING"
    })
    void fullRetainsAnAuditableBlockedRow(final String input, final String expected)
            throws SQLException {
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            final UUID run = UUID.randomUUID();
            prepare(
                    session,
                    run,
                    1,
                    fat(
                            1,
                            1,
                            row -> {
                                if (input.equals("NULL_DATE")) {
                                    row.putNull("fit_ant_issue_date");
                                }
                                if (input.equals("DUAL")) {
                                    row.put("fit_nse_number", 9);
                                }
                                if (input.equals("CONFLICT") && row.path("id").asInt() == 2) {
                                    row.put("fit_ant_value", "200");
                                }
                            }));
            final var receipt =
                    new JdbcExpansionMaterializations(session, CLOCK)
                            .invoices(
                                    run,
                                    UUID.randomUUID(),
                                    input.equals("REFERENCE") ? 2 : 1,
                                    ExecutionMode.BOOTSTRAP,
                                    true,
                                    DATE,
                                    DATE.plusDays(3));
            assertEquals(1, receipt.blocked());
            assertEquals(0, receipt.ready());
            final var fact = new JdbcExpansionQueries(session).invoiceFactsPage(run, 0, 1).get(0);
            assertEquals(expected, fact.disposition());
            if (input.equals("NULL_DATE")) {
                assertNull(fact.issueDate());
                assertEquals(DATE, fact.monthlyReferenceDate());
            }
            if (input.equals("CONFLICT")) {
                assertNull(fact.operationalValue());
            }
        }
    }

    static LocalExpansionRuntime prepare(
            final ColetaTemporalLaboratorySession session,
            final UUID run,
            final int roots,
            final ExpansionSyntheticSource source)
            throws SQLException {
        final var runtime = ExpansionLaboratoryLocalIntegrationIT.runtime(session, run, 3, 100);
        runtime.capture(
                DataExportTemplate.FATURAS_POR_CLIENTE,
                DATE,
                ExecutionMode.BOOTSTRAP,
                null,
                source,
                NONE);
        ExpansionLaboratoryRelationsIT.dependency(session, run)
                .capture(
                        DataExportTemplate.FRETES,
                        DATE,
                        ExecutionMode.BOOTSTRAP,
                        null,
                        ExpansionDependencyFixtures.source(DataExportTemplate.FRETES, roots, 3),
                        NONE);
        final var relations = new JdbcExpansionRelations(session, CLOCK);
        relations.bind(
                run,
                ExpansionLaboratoryRelationsIT.bindings(roots).stream()
                        .filter(b -> b.kind() == ExpansionRelation.Kind.FAT_DOCUMENT_FREIGHT)
                        .toList(),
                NONE);
        relations.resolve(run);
        new JdbcExpansionReferences(session, CLOCK).importPackaged(run, 1, DATE, DATE.plusDays(10));
        return runtime;
    }

    static ExpansionSyntheticSource fat(
            final int roots, final int revision, final Consumer<ObjectNode> mutation) {
        return new ExpansionSyntheticSource(
                page -> {
                    final var array = new ObjectMapper().createArrayNode();
                    for (int ordinal = (page - 1) * 3;
                            ordinal < Math.min(page * 3, roots * 3);
                            ordinal++) {
                        final int root = ordinal / 3 + 1, component = ordinal % 3 == 1 ? 2 : 1;
                        final var data =
                                ExpansionLaboratoryFixtures.data(
                                        DataExportTemplate.FATURAS_POR_CLIENTE);
                        data.put("id", component);
                        mutation.accept(data);
                        final var row =
                                ExpansionLaboratoryFixtures.envelope(
                                        DataExportTemplate.FATURAS_POR_CLIENTE,
                                        data,
                                        ordinal + 1,
                                        root,
                                        component);
                        ((ObjectNode) row.get("binding")).put("revision", revision);
                        array.add(row);
                    }
                    return array.toString();
                });
    }
}
