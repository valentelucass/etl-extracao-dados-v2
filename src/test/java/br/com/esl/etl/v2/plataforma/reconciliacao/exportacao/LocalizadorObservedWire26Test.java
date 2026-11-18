package br.com.esl.etl.v2.plataforma.reconciliacao.exportacao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.modulos.localizacaocargas.aplicacao.LocalizacaoCargaDataExportRecordMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

class LocalizadorObservedWire26Test {
    @Test
    void currentTwentySixFieldsAndEightTableFieldFiltersUseOnlyTheObservedBijection()
            throws Exception {
        final var shape = LocalizadorObservedWire26.shape();
        final var info = LocalizadorCaptureFixture.currentQualifiedInfo();
        assertEquals(26, info.required("fields").size());
        assertEquals(8, info.required("filters").size());
        assertEquals(
                "OBSERVED_QUALIFIED_BIJECTION8_V1",
                LocalizadorObservedWire26.validateInfoNames(info, shape));
        assertEquals(LocalizadorIndependentRequest.HISTORICAL_FILTER_NAMES, shape.filters());
        assertEquals(
                "HISTORICAL_SIMPLE_NAMES_V1",
                LocalizadorObservedWire26.validateInfoNames(
                        LocalizadorCaptureFixture.info(), shape));
    }

    @Test
    void qualifiedDirectNamesAndDataEnvelopeUseTheSameExactEightNameBijection() throws Exception {
        final var shape = LocalizadorObservedWire26.shape();
        final var info = LocalizadorCaptureFixture.info();
        final var filters = info.putArray("filters");
        for (final String name :
                List.of(
                        "freights.service_at",
                        "fit_freight_location.current_location_id",
                        "freights.payer_id",
                        "freights.type",
                        "freights.status",
                        "fit_fis_invoice.number",
                        "fit_fis_invoice.key",
                        "scopes.by_updated_at")) {
            filters.add(name);
        }
        assertEquals(
                "OBSERVED_QUALIFIED_BIJECTION8_V1",
                LocalizadorObservedWire26.validateInfoNames(info, shape));
        final var envelope = ExportValidationSupport.JSON.createObjectNode();
        envelope.set("data", info);
        assertEquals(
                "OBSERVED_QUALIFIED_BIJECTION8_V1",
                LocalizadorObservedWire26.validateInfoNames(envelope, shape));
    }

    @Test
    void qualifiedNamesRejectWrongQualifierMixedSetsDuplicateMissingAndFieldDrift()
            throws Exception {
        final var shape = LocalizadorObservedWire26.shape();
        for (final String mutation :
                List.of("wrongQualifier", "mixed", "duplicate", "missing", "extra", "fieldDrift")) {
            final var info = LocalizadorCaptureFixture.currentQualifiedInfo();
            final var filters = (ArrayNode) info.required("filters");
            switch (mutation) {
                case "wrongQualifier" -> ((ObjectNode) filters.get(0)).put("table", "other");
                case "mixed" ->
                        filters.set(
                                0,
                                ExportValidationSupport.JSON
                                        .getNodeFactory()
                                        .textNode("service_at"));
                case "duplicate" -> filters.add(filters.get(0).deepCopy());
                case "missing" -> filters.remove(0);
                case "extra" -> filters.add("other.status");
                default -> ((ObjectNode) info.required("fields")).remove("type");
            }
            if ("duplicate".equals(mutation)) {
                assertThrows(
                        IllegalStateException.class,
                        () -> LocalizadorObservedWire26.validateInfoNames(info, shape));
            } else {
                assertThrows(
                        IllegalArgumentException.class,
                        () -> LocalizadorObservedWire26.validateInfoNames(info, shape));
            }
        }
    }

    @Test
    void archivedNestedResponseHasTwentySixRootsAndOneArrayItemRule() throws Exception {
        final var shape = LocalizadorObservedWire26.shape();
        assertEquals(26, shape.fields().size());
        assertEquals(27, shape.paths().size());
        assertEquals(8, shape.filters().size());
        assertTrue(shape.paths().containsKey("/invoices_mapping/*"));
        assertTrue(
                LocalizadorObservedWire26.qualified(
                        LocalizadorCaptureFixture.row(1), shape, new TreeMap<>()));
    }

    @Test
    void nullableIsSeparateFromJsonTypesAndOnlyTheTwoHistoricalFieldsPermitNull() throws Exception {
        final var shape = LocalizadorObservedWire26.shape();
        final var row = LocalizadorCaptureFixture.row(1);
        assertTrue(LocalizadorObservedWire26.qualified(row, shape, new TreeMap<>()));
        row.putNull("fit_pyr_nickname");
        final var refusals = new TreeMap<String, Long>();
        assertFalse(LocalizadorObservedWire26.qualified(row, shape, refusals));
        assertEquals(1L, refusals.get("/fit_pyr_nickname:NULL"));
    }

    @Test
    void arrayElementsAndMissingOrExtraFieldsHaveSeparateWireEvidence() throws Exception {
        final var shape = LocalizadorObservedWire26.shape();
        final var row = LocalizadorCaptureFixture.row(1);
        row.putArray("invoices_mapping").add("B").add(1);
        row.remove("type");
        row.put("unobserved", "authored");
        final var refusals = new TreeMap<String, Long>();
        assertFalse(LocalizadorObservedWire26.qualified(row, shape, refusals));
        assertEquals(1L, refusals.get("/invoices_mapping/*:INTEGER"));
        assertEquals(1L, refusals.get("/type:ABSENT"));
        assertEquals(1L, refusals.get("UNAPPROVED_SOURCE_PATH"));
    }

    @Test
    void infoNamesAcceptDirectAndDataEnvelopeWithoutInventingCurrentDeclaredTypes()
            throws Exception {
        final var shape = LocalizadorObservedWire26.shape();
        final var info = LocalizadorCaptureFixture.info();
        LocalizadorObservedWire26.validateInfoNames(info, shape);
        final var envelope = ExportValidationSupport.JSON.createObjectNode();
        envelope.set("data", info);
        LocalizadorObservedWire26.validateInfoNames(envelope, shape);
        ((com.fasterxml.jackson.databind.node.ObjectNode) info.required("fields")).remove("type");
        assertThrows(
                IllegalArgumentException.class,
                () -> LocalizadorObservedWire26.validateInfoNames(info, shape));
    }

    @Test
    void originalRecordIsAnOffsetSliceAndPreservesNegativeZeroAndEscapes() throws Exception {
        final String record =
                "{\"service_at\":\"2026-10-13\",\"invoices_volumes\":-0,\"text\":\"\\u0041\"}";
        final String line =
                "{\"entity\":\"LOCALIZADOR\",\"templateId\":8656,\"record\":" + record + "}";
        assertEquals(record, LocalizadorObservedWire26.originalRecord(line));
        assertThrows(
                IllegalArgumentException.class,
                () -> LocalizadorObservedWire26.originalRecord(line + "{}"));
    }

    @Test
    void observedMapperRemainsSeparateFromDefaultSeventeenAndKeepsStringLexemes() throws Exception {
        final var row = LocalizadorCaptureFixture.row(1);
        assertEquals(
                "UNAPPROVED_SOURCE_PATH",
                new LocalizacaoCargaDataExportRecordMapper()
                        .map(1, row.toString())
                        .quarantineReasonCode());
        final var observed = LocalizacaoCargaDataExportRecordMapper.observed20261024();
        assertFalse(observed.map(1, row.toString()).quarantined());
        final String negativeZero =
                row.toString().replace("\"invoices_volumes\":0", "\"invoices_volumes\":-0");
        final var mapped = observed.map(1, negativeZero);
        assertTrue(mapped.quarantined());
        assertEquals("-0", mapped.invoicesVolumesRaw());
        assertEquals("INVALID_INVOICES_VOLUMES", mapped.quarantineReasonCode());
    }
}
