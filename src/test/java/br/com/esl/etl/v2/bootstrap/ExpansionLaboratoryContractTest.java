package br.com.esl.etl.v2.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.modulos.contasapagar.aplicacao.ContaPagarDataExportMapper;
import br.com.esl.etl.v2.modulos.contasapagar.domain.ContaPagarRules;
import br.com.esl.etl.v2.modulos.faturasporcliente.domain.FaturaClienteRules;
import br.com.esl.etl.v2.modulos.inventario.aplicacao.InventarioDataExportMapper;
import br.com.esl.etl.v2.modulos.inventario.domain.InventarioRules;
import br.com.esl.etl.v2.modulos.sinistros.aplicacao.SinistroDataExportMapper;
import br.com.esl.etl.v2.modulos.sinistros.domain.SinistroRules;
import br.com.esl.etl.v2.plataforma.expansao.ExpansionFreshness;
import br.com.esl.etl.v2.plataforma.expansao.ExpansionKey;
import br.com.esl.etl.v2.plataforma.expansao.ExpansionValue;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.BusinessDateRange;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportPageRequest;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportTemplate;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.ExpansionFieldParser;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.ExpansionSyntheticSource;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.SearchPath;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

class ExpansionLaboratoryContractTest {
    @ParameterizedTest
    @EnumSource(
            value = DataExportTemplate.class,
            names = {"CONTAS_A_PAGAR", "FATURAS_POR_CLIENTE", "INVENTARIO", "SINISTROS"})
    void contractKeyIsEnvelopeOccurrenceAndNeverAnEslRoot(final DataExportTemplate template) {
        final var release = ExpansionSyntheticSource.release(template);
        assertEquals("/capture_occurrence", release.response().keyPath());
        assertTrue(release.response().find("/data").isPresent());
        final var data = ExpansionLaboratoryFixtures.data(template);
        assertFalse(data.has("capture_occurrence"));
        assertFalse(data.has("binding"));
        final var envelope = ExpansionLaboratoryFixtures.envelope(template, data, 1, 1, 1);
        assertTrue(envelope.path("binding").isObject());
    }

    @Test
    void capturesAndOracleUseBothCapFilters() {
        final var date = LocalDate.of(2036, 4, 1);
        final var request =
                DataExportPageRequest.forTemplate(
                        DataExportTemplate.CONTAS_A_PAGAR,
                        new BusinessDateRange(date, date),
                        null,
                        1);
        assertEquals(2, request.filters().size());
        assertEquals(
                request.businessDateWindow(),
                request.filters().get(new SearchPath("accounting_debits", "created_at")));
    }

    @Test
    void decimalZeroNullAbsentAndOverflowHaveDifferentDispositions() {
        final var row =
                JsonNodeFactory.instance
                        .objectNode()
                        .put("zero", "0")
                        .putNull("null")
                        .put("bad", "100000000000000000000.00");
        assertEquals(
                "0.00000000", ExpansionFieldParser.decimal(row, "zero").value().toPlainString());
        assertEquals(
                ExpansionValue.Presence.NULL, ExpansionFieldParser.decimal(row, "null").presence());
        assertEquals(
                ExpansionValue.Presence.ABSENT,
                ExpansionFieldParser.decimal(row, "missing").presence());
        assertFalse(ExpansionFieldParser.decimal(row, "bad").valid());
        assertEquals("100000000000000000000.00", ExpansionFieldParser.decimal(row, "bad").raw());
    }

    @Test
    void capUsesMaximumBoundaryAndPreservesAccountingExpansion() {
        final var template = DataExportTemplate.CONTAS_A_PAGAR;
        final var data = ExpansionLaboratoryFixtures.data(template);
        final var row =
                new ContaPagarDataExportMapper()
                        .map(1, ExpansionLaboratoryFixtures.envelope(template, data, 1, 1, 1));
        assertEquals(
                ExpansionFreshness.civilEnd(LocalDate.of(2036, 4, 1)),
                ContaPagarRules.freshness(row));
        assertEquals("100.00000000", row.valueToPay().value().toPlainString());
        assertEquals("ABERTO", ContaPagarRules.payment(row));
        assertEquals("Advance", ContaPagarRules.typeToken(row.type().value()));
    }

    @Test
    void civilBoundaryIsExclusiveAndDstUsesIanaRules() {
        final var value = ExpansionFreshness.civilEnd(LocalDate.of(2018, 11, 3));
        assertEquals(Instant.parse("2018-11-04T03:00:00Z").getEpochSecond(), value.second());
        assertTrue(
                value.compareTo(ExpansionFreshness.instant(Instant.parse("2018-11-04T03:00:00Z")))
                        < 0);
    }

    @Test
    void inventoryPreservesDuplicateAndReorderedMappingStrings() {
        final var template = DataExportTemplate.INVENTARIO;
        final var data = ExpansionLaboratoryFixtures.data(template);
        data.putArray("cnr_c_s_fit_invoices_mapping").add("B").add("A").add("B");
        final var row =
                new InventarioDataExportMapper()
                        .map(1, ExpansionLaboratoryFixtures.envelope(template, data, 1, 1, 1));
        assertEquals(
                java.util.List.of("B", "A", "B"), row.cnrCSFitInvoicesMapping().value().items());
        assertTrue(InventarioRules.proofAttached(row.cnrCSFitFteLceOreDescription().value()));
        assertFalse(InventarioRules.proofAttached("anexado entrega comprovante"));
    }

    @Test
    void sinistrosKeepPreciseTimeAndUseOpeningFallback() {
        final var template = DataExportTemplate.SINISTROS;
        final var data = ExpansionLaboratoryFixtures.data(template);
        data.putNull("icm_ttt_treatment_at");
        final var row =
                new SinistroDataExportMapper()
                        .map(1, ExpansionLaboratoryFixtures.envelope(template, data, 1, 1, 2));
        assertEquals(123456789, row.occurrenceAtTime().value().getNano());
        assertEquals(
                ExpansionFreshness.civilStart(LocalDate.of(2036, 4, 1)),
                SinistroRules.freshness(row));
        assertEquals("synthetic-occurrence-2", row.icmFisIoeNumber().value());
    }

    @ParameterizedTest
    @ValueSource(strings = {"faturado", " aguardando faturamento ", "", " "})
    void placeholderNeverMeansBilled(final String value) {
        assertFalse(FaturaClienteRules.realDocument(value));
    }

    @Test
    void typedKeysDoNotCollapseAndPaddingIsRejected() {
        assertNotEquals(
                new ExpansionKey(ExpansionKey.Kind.INTEGER, "1"),
                new ExpansionKey(ExpansionKey.Kind.STRING, "1"));
        assertNotEquals(
                new ExpansionKey(ExpansionKey.Kind.STRING, "A"),
                new ExpansionKey(ExpansionKey.Kind.STRING, "a"));
        assertThrows(
                IllegalArgumentException.class,
                () -> new ExpansionKey(ExpansionKey.Kind.STRING, "A "));
    }
}
