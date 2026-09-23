package br.com.esl.etl.v2.bootstrap;

import static br.com.esl.etl.v2.bootstrap.ExpansionLaboratoryLocalIntegrationIT.CLOCK;
import static br.com.esl.etl.v2.bootstrap.ExpansionLaboratoryLocalIntegrationIT.DATE;
import static br.com.esl.etl.v2.bootstrap.ExpansionLaboratoryLocalIntegrationIT.NONE;
import static br.com.esl.etl.v2.bootstrap.ExpansionLaboratoryLocalIntegrationIT.scalar;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import br.com.esl.etl.v2.plataforma.controle.ExecutionMode;
import br.com.esl.etl.v2.plataforma.expansao.ExpansionFreightTerms;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportTemplate;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.ExpansionDependencySource;
import br.com.esl.etl.v2.plataforma.persistencia.coletas.ColetaTemporalLaboratorySession;
import br.com.esl.etl.v2.plataforma.persistencia.expansao.JdbcExpansionMaterializations;
import br.com.esl.etl.v2.plataforma.persistencia.expansao.JdbcExpansionQueries;
import br.com.esl.etl.v2.plataforma.persistencia.expansao.JdbcExpansionReferences;
import br.com.esl.etl.v2.plataforma.persistencia.expansao.JdbcExpansionRelations;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ExpansionLaboratoryRevenueIT {
    @Test
    void composedFourPipelinesAndTwoLoadsHaveIndependentManualFinancialOracles()
            throws SQLException {
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            final UUID run = UUID.randomUUID();
            prepare(session, run, 2, true);
            final var material = new JdbcExpansionMaterializations(session, CLOCK);
            final UUID receipt = UUID.randomUUID();
            assertEquals(
                    2,
                    material.invoices(
                                    run,
                                    UUID.randomUUID(),
                                    1,
                                    ExecutionMode.BOOTSTRAP,
                                    true,
                                    DATE,
                                    DATE.plusDays(3))
                            .ready());
            final var result =
                    material.revenue(
                            run, receipt, 1, ExecutionMode.BOOTSTRAP, true, DATE, DATE.plusDays(3));
            assertEquals(new JdbcExpansionMaterializations.Receipt(2, 2, 0, 0, 2, 0), result);
            final var rows = new JdbcExpansionQueries(session).revenueFactsPage(run, 0, 100);
            assertEquals(2, rows.size());
            final var row = rows.get(0);
            assertEquals(new BigDecimal("120.00000000"), row.revenueValue());
            assertEquals(LocalDate.of(2036, 4, 5), row.originalReferenceDate());
            assertEquals(LocalDate.of(2036, 4, 4), row.billingReferenceDate());
            assertEquals("SYNTHETIC_BRANCH", row.branchCode());
            assertEquals(3, row.volumes());
            assertEquals("LOCALIZACAO_CAPTURE", row.volumeProvenance());
            assertEquals(1, row.invoices());
            assertEquals(2, row.documents());
            assertEquals(
                    240,
                    scalar(
                            session,
                            "SELECT CONVERT(BIGINT,SUM(revenue_value)) FROM mart.expansion_lab_revenue WHERE run_id=?",
                            run));
            assertEquals(
                    200,
                    scalar(
                            session,
                            "SELECT CONVERT(BIGINT,SUM(operational_value)) FROM mart.expansion_lab_invoice "
                                    + "WHERE run_id=?",
                            run));
            assertEquals(
                    result,
                    material.revenue(
                            run,
                            receipt,
                            1,
                            ExecutionMode.BOOTSTRAP,
                            true,
                            DATE,
                            DATE.plusDays(3)));
            assertEquals(
                    2,
                    material.revenue(
                                    run,
                                    UUID.randomUUID(),
                                    1,
                                    ExecutionMode.REPLAY,
                                    true,
                                    DATE,
                                    DATE.plusDays(3))
                            .noops());
        }
    }

    @Test
    void referenceDateCorrectionAndLateLocationKeepOneFreightAndOneRevenue() throws SQLException {
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            final UUID run = UUID.randomUUID();
            prepare(session, run, 1, false);
            final var material = new JdbcExpansionMaterializations(session, CLOCK);
            final var queries = new JdbcExpansionQueries(session);
            material.revenue(
                    run,
                    UUID.randomUUID(),
                    1,
                    ExecutionMode.BOOTSTRAP,
                    true,
                    DATE,
                    DATE.plusDays(3));
            final var before = queries.revenueFactsPage(run, 0, 1).get(0);
            assertEquals(2, before.volumes());
            assertEquals("FRETE_SYNTHETIC_TERMS", before.volumeProvenance());
            final var dependency = ExpansionLaboratoryRelationsIT.dependency(session, run);
            dependency.capture(
                    DataExportTemplate.LOCALIZACAO_CARGAS,
                    DATE,
                    ExecutionMode.BACKFILL,
                    null,
                    ExpansionDependencyFixtures.source(DataExportTemplate.LOCALIZACAO_CARGAS, 1, 3),
                    NONE);
            new JdbcExpansionRelations(session, CLOCK).resolve(run);
            dependency.capture(
                    DataExportTemplate.FRETES,
                    DATE.plusDays(1),
                    ExecutionMode.INCREMENTAL,
                    null,
                    ExpansionDependencyFixtures.source(DataExportTemplate.FRETES, 1, 3)
                            .withFinancialBindings(
                                    key ->
                                            ExpansionLaboratoryFreightTermsIT.terms(
                                                    key, 2, LocalDate.of(2036, 4, 7))),
                    NONE);
            final var changed =
                    material.revenue(
                            run,
                            UUID.randomUUID(),
                            1,
                            ExecutionMode.INCREMENTAL,
                            false,
                            DATE.plusDays(1),
                            DATE.plusDays(2));
            assertEquals(1, changed.updates());
            assertEquals(0, changed.inserts());
            final var after = queries.revenueFactsPage(run, 0, 100);
            assertEquals(1, after.size());
            assertEquals(before.dependencyId(), after.get(0).dependencyId());
            assertEquals(LocalDate.of(2036, 4, 7), after.get(0).billingReferenceDate());
            assertEquals(3, after.get(0).volumes());
            assertEquals(new BigDecimal("120.00000000"), after.get(0).revenueValue());
        }
    }

    @ParameterizedTest
    @CsvSource({
        "COURTESY,COURTESY,0.00000000",
        "BLOCK,BILLING_BLOCK,0.00000000",
        "BLOCK_ONLY,READY,120.00000000",
        "INELIGIBLE,INELIGIBLE,0.00000000",
        "NULL_DATE,NULL_DATE,0.00000000",
        "CANCELLED,CANCELLED,0.00000000",
        "ZERO,READY,0.00000000",
        "UNKNOWN_PAYER,PAYER_UNMAPPED,NULL",
        "REFERENCE,REFERENCE_MISSING,NULL",
        "NULL_VALUE,VALUE_MISSING,NULL"
    })
    void exclusionsAndMissingReferencesUseCatalogRulesAndPreserveProvenance(
            final String input, final String disposition, final String expected)
            throws SQLException {
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            final UUID run = UUID.randomUUID();
            prepare(session, run, 1, true);
            final var data = ExpansionDependencyFixtures.data(DataExportTemplate.FRETES, 1);
            data.put("cte_created_at", "2036-04-02T13:00:00Z");
            if (input.equals("CANCELLED")) {
                data.put("status", "cancelled");
            }
            if (input.equals("ZERO")) {
                data.put("total", "0");
            }
            if (input.equals("NULL_VALUE")) {
                data.putNull("total");
            }
            ExpansionLaboratoryRelationsIT.dependency(session, run)
                    .capture(
                            DataExportTemplate.FRETES,
                            DATE.plusDays(1),
                            ExecutionMode.INCREMENTAL,
                            null,
                            new ExpansionDependencySource(
                                            page -> page == 1 ? "[" + data + "]" : "[]")
                                    .withFinancialBindings(key -> changedTerms(key, input)),
                            NONE);
            new JdbcExpansionRelations(session, CLOCK).resolve(run);
            new JdbcExpansionMaterializations(session, CLOCK)
                    .revenue(
                            run,
                            UUID.randomUUID(),
                            input.equals("REFERENCE") ? 2 : 1,
                            ExecutionMode.BOOTSTRAP,
                            true,
                            DATE,
                            DATE.plusDays(3));
            final var fact = new JdbcExpansionQueries(session).revenueFactsPage(run, 0, 1).get(0);
            assertEquals(disposition, fact.disposition());
            if (expected.equals("NULL")) {
                assertNull(fact.revenueValue());
            } else {
                assertEquals(new BigDecimal(expected), fact.revenueValue());
            }
            if (input.equals("CANCELLED")) {
                assertEquals("FRETE_WITH_EXPLICIT_CTE", fact.cancellationProvenance());
            }
        }
    }

    static ExpansionFreightTerms changedTerms(final String key, final String input) {
        final var original = ExpansionDependencyFixtures.financialTerms(key);
        return new ExpansionFreightTerms(
                key,
                2,
                input.equals("NULL_DATE") ? null : original.billingReferenceDate(),
                input.equals("BLOCK")
                        ? "BLOQUEIO para ANULAÇÃO"
                        : input.equals("BLOCK_ONLY") ? "bloqueio" : original.classification(),
                input.equals("COURTESY"),
                !input.equals("INELIGIBLE"),
                original.fallbackVolumes(),
                input.equals("UNKNOWN_PAYER")
                        ? "0000000000000000000000000000000000000000000000000000000000000002"
                        : original.payerToken(),
                original.currency(),
                original.unit(),
                true,
                original.evidence());
    }

    static LocalExpansionRuntime prepare(
            final ColetaTemporalLaboratorySession session,
            final UUID run,
            final int roots,
            final boolean location)
            throws SQLException {
        final var runtime = ExpansionLaboratoryLocalIntegrationIT.runtime(session, run, 3, 100);
        for (final var template :
                List.of(
                        DataExportTemplate.CONTAS_A_PAGAR,
                        DataExportTemplate.FATURAS_POR_CLIENTE,
                        DataExportTemplate.INVENTARIO,
                        DataExportTemplate.SINISTROS)) {
            runtime.capture(
                    template,
                    DATE,
                    ExecutionMode.BOOTSTRAP,
                    null,
                    ExpansionLaboratoryFixtures.source(template, roots, 3),
                    NONE);
        }
        final var dependency = ExpansionLaboratoryRelationsIT.dependency(session, run);
        dependency.capture(
                DataExportTemplate.FRETES,
                DATE,
                ExecutionMode.BOOTSTRAP,
                null,
                ExpansionDependencyFixtures.source(DataExportTemplate.FRETES, roots, 3)
                        .withFinancialBindings(ExpansionDependencyFixtures::financialTerms),
                NONE);
        if (location) {
            dependency.capture(
                    DataExportTemplate.LOCALIZACAO_CARGAS,
                    DATE,
                    ExecutionMode.BOOTSTRAP,
                    null,
                    ExpansionDependencyFixtures.source(
                            DataExportTemplate.LOCALIZACAO_CARGAS, roots, 3),
                    NONE);
        }
        final var relations = new JdbcExpansionRelations(session, CLOCK);
        relations.bind(run, ExpansionLaboratoryRelationsIT.bindings(roots), NONE);
        relations.resolve(run);
        new JdbcExpansionReferences(session, CLOCK).importPackaged(run, 1, DATE, DATE.plusDays(10));
        return runtime;
    }
}
