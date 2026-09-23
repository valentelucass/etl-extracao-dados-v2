package br.com.esl.etl.v2.bootstrap;

import static br.com.esl.etl.v2.bootstrap.ExpansionLaboratoryLocalIntegrationIT.CLOCK;
import static br.com.esl.etl.v2.bootstrap.ExpansionLaboratoryLocalIntegrationIT.DATE;
import static br.com.esl.etl.v2.bootstrap.ExpansionLaboratoryLocalIntegrationIT.NONE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.plataforma.controle.ExecutionMode;
import br.com.esl.etl.v2.plataforma.expansao.ExpansionProjection;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportTemplate;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.ExpansionSyntheticSource;
import br.com.esl.etl.v2.plataforma.persistencia.coletas.ColetaTemporalLaboratorySession;
import br.com.esl.etl.v2.plataforma.persistencia.expansao.JdbcExpansionQueries;
import br.com.esl.etl.v2.plataforma.persistencia.expansao.JdbcExpansionQueries.Vertical;
import br.com.esl.etl.v2.plataforma.persistencia.expansao.JdbcExpansionReferences;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

class ExpansionLaboratoryProjectionsIT {
    @ParameterizedTest
    @EnumSource(Vertical.class)
    void fourTypedProjectionsHaveStableGrainBoundedPaginationAndLineage(final Vertical vertical)
            throws SQLException {
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            final UUID run = UUID.randomUUID();
            final var runtime = ExpansionLaboratoryLocalIntegrationIT.runtime(session, run, 3, 100);
            final var template = template(vertical);
            final var capture =
                    runtime.capture(
                            template,
                            DATE,
                            ExecutionMode.BOOTSTRAP,
                            null,
                            ExpansionLaboratoryFixtures.source(template, 2, 3),
                            NONE);
            new JdbcExpansionReferences(session, CLOCK)
                    .importPackaged(run, 1, DATE, DATE.plusDays(10));
            final var queries = new JdbcExpansionQueries(session);
            final var first = queries.detailPage(run, vertical, 1, 0, 1);
            assertEquals(1, first.size());
            assertEquals(capture.executionId(), first.get(0).lineage().execution());
            final var rest =
                    queries.detailPage(run, vertical, 1, first.get(0).lineage().cursor(), 100);
            assertEquals(3, rest.size());
            assertTrue(rest.get(0).lineage().cursor() > first.get(0).lineage().cursor());
            assertFalse(first.get(0).lineage().unresolvedConflict());
            assertEquals(0, queries.detailPage(UUID.randomUUID(), vertical, 1, 0, 100).size());
            assertThrows(
                    IllegalArgumentException.class,
                    () -> queries.detailPage(run, vertical, 1, 0, 101));
            if (first.get(0) instanceof ExpansionProjection.Payable cap) {
                assertEquals(new BigDecimal("100.00000000"), cap.rootAmount());
                assertEquals("ABERTO", cap.paymentState());
                assertEquals("Adiantamento", cap.typeLabel());
                assertEquals("4. Custos Variáveis", cap.classificationLabel());
                assertNotNull(cap.labelRelease());
                final var missing =
                        (ExpansionProjection.Payable)
                                queries.detailPage(run, vertical, 2, 0, 1).get(0);
                assertNull(missing.typeLabel());
                assertEquals(cap.typeRaw(), missing.typeRaw());
                assertNull(missing.labelRelease());
            }
            if (first.get(0) instanceof ExpansionProjection.InvoiceCustomer fat) {
                assertTrue(fat.hasInvoice());
                assertEquals("RESOLVED", fat.fiscalState());
                assertEquals("Autorizado", fat.statusLabel());
                assertEquals("cnpj:00000000000000", fat.clientKey());
                assertEquals("ABSENT_UNSOURCED_LEGACY", fat.nfseSeriesState());
            }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"faturado", " aguardando faturamento ", ""})
    void placeholdersShareOneFalseInvoiceSemantics(final String placeholder) throws SQLException {
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            final UUID run = UUID.randomUUID();
            final var runtime = ExpansionLaboratoryLocalIntegrationIT.runtime(session, run, 3, 100);
            final var data =
                    ExpansionLaboratoryFixtures.data(DataExportTemplate.FATURAS_POR_CLIENTE);
            data.put("fit_ant_document", placeholder);
            final var row =
                    ExpansionLaboratoryFixtures.envelope(
                            DataExportTemplate.FATURAS_POR_CLIENTE, data, 1, 1, 1);
            runtime.capture(
                    DataExportTemplate.FATURAS_POR_CLIENTE,
                    DATE,
                    ExecutionMode.BOOTSTRAP,
                    null,
                    new ExpansionSyntheticSource(page -> page == 1 ? "[" + row + "]" : "[]"),
                    NONE);
            final var projection =
                    (ExpansionProjection.InvoiceCustomer)
                            new JdbcExpansionQueries(session)
                                    .detailPage(run, Vertical.FAT, 1, 0, 1)
                                    .get(0);
            assertFalse(projection.hasInvoice());
            assertEquals(placeholder, projection.documentRaw());
        }
    }

    @Test
    void unresolvedFiscalDualPreservesBothNumbersAndTieConflictBlocksExistingProjection()
            throws SQLException {
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            final UUID run = UUID.randomUUID();
            final var runtime = ExpansionLaboratoryLocalIntegrationIT.runtime(session, run, 3, 100);
            final var data =
                    ExpansionLaboratoryFixtures.data(DataExportTemplate.FATURAS_POR_CLIENTE);
            data.put("fit_nse_number", 9);
            final var row =
                    ExpansionLaboratoryFixtures.envelope(
                            DataExportTemplate.FATURAS_POR_CLIENTE, data, 1, 1, 1);
            runtime.capture(
                    DataExportTemplate.FATURAS_POR_CLIENTE,
                    DATE,
                    ExecutionMode.BOOTSTRAP,
                    null,
                    new ExpansionSyntheticSource(page -> page == 1 ? "[" + row + "]" : "[]"),
                    NONE);
            final var query = new JdbcExpansionQueries(session);
            final var projection =
                    (ExpansionProjection.InvoiceCustomer)
                            query.detailPage(run, Vertical.FAT, 1, 0, 1).get(0);
            assertEquals("UNRESOLVED", projection.fiscalState());
            assertNull(projection.officialNumber());
            assertEquals("7", projection.cteNumber());
            assertEquals("9", projection.nfseNumber());
            data.put("total", "122.00");
            final var divergent =
                    ExpansionLaboratoryFixtures.envelope(
                            DataExportTemplate.FATURAS_POR_CLIENTE, data, 1, 1, 1);
            final var conflict =
                    runtime.capture(
                            DataExportTemplate.FATURAS_POR_CLIENTE,
                            DATE,
                            ExecutionMode.INCREMENTAL,
                            null,
                            new ExpansionSyntheticSource(
                                    page -> page == 1 ? "[" + divergent + "]" : "[]"),
                            NONE);
            assertEquals(1, conflict.receipt().quarantine());
            assertTrue(
                    query.detailPage(run, Vertical.FAT, 1, 0, 1)
                            .get(0)
                            .lineage()
                            .unresolvedConflict());
        }
    }

    private static DataExportTemplate template(final Vertical vertical) {
        return switch (vertical) {
            case CAP -> DataExportTemplate.CONTAS_A_PAGAR;
            case FAT -> DataExportTemplate.FATURAS_POR_CLIENTE;
            case INV -> DataExportTemplate.INVENTARIO;
            case SIN -> DataExportTemplate.SINISTROS;
        };
    }
}
