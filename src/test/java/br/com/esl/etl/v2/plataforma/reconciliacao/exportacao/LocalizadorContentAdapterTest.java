package br.com.esl.etl.v2.plataforma.reconciliacao.exportacao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalizadorContentAdapterTest {
    @TempDir Path directory;

    @Test
    void comparesAll26ColumnsFourTotalsAndFourCompleteKeylessProjections() throws Exception {
        final var result = new LocalizadorContentFixture(directory, 2).compare();
        assertTrue(result.path("contentEqualUnderCandidatePresentation").booleanValue());
        assertEquals(26, result.path("comparison").path("columnsEqual").size());
        assertEquals(4, result.path("comparison").path("totalsEqual").size());
        assertEquals(26, result.path("columnDiagnostics").size());
        for (final String axis :
                java.util.List.of("fullValueProjection", "originalValueProjection")) {
            for (final String side : java.util.List.of("api", "ui")) {
                assertTrue(result.path(axis).path(side).path("completeAllValues").booleanValue());
                assertEquals(26, result.path(axis).path(side).path("columnBags").size());
                assertFalse(result.path(axis).path(side).has("idMultiplicityBag"));
            }
        }
        assertFalse(result.path("rootIdentityQualified").booleanValue());
        assertFalse(result.path("minutaCandidateUsedAsIdentity").booleanValue());
        assertFalse(result.path("contentAccepted").booleanValue());
    }

    @Test
    void repeatedMinutaCandidatesDoNotDiscardAnyPhysicalOccurrence() throws Exception {
        final var fixture = new LocalizadorContentFixture(directory, 2);
        for (int row = 0; row < 2; row++) {
            fixture.records.get(row).put("corporation_sequence_number", 1);
            fixture.nativeRows.get(row).put(2, LocalizadorContentFixture.number("1"));
        }
        fixture.records.add(fixture.records.get(0).deepCopy());
        fixture.nativeRows.add(new LinkedHashMap<>(fixture.nativeRows.get(0)));
        assertEquals(3, fixture.compare().path("sourceRows").intValue());
        fixture.nativeRows.remove(2);
        assertFalse(
                fixture.compare().path("contentEqualUnderCandidatePresentation").booleanValue());
    }

    @Test
    void physicalRowOrderCanChangeWhileDuplicateMultiplicitiesRemainExact() throws Exception {
        final var fixture = new LocalizadorContentFixture(directory, 3);
        Collections.reverse(fixture.nativeRows);
        assertTrue(fixture.compare().path("contentEqualUnderCandidatePresentation").booleanValue());
    }

    @Test
    void sameColumnBagsAndTotalsDoNotHideChangedFullTuples() throws Exception {
        final var fixture = new LocalizadorContentFixture(directory, 2);
        fixture.records.get(0).put("total", "1");
        fixture.records.get(1).put("total", "2");
        fixture.nativeRows.get(0).put(20, LocalizadorContentFixture.number("2"));
        fixture.nativeRows.get(1).put(20, LocalizadorContentFixture.number("1"));
        final var result = fixture.compare();
        assertFalse(result.path("comparison").path("fullTuplesEqual").booleanValue());
        for (final var equal : result.path("comparison").path("columnsEqual")) {
            assertTrue(equal.booleanValue());
        }
        for (final var equal : result.path("comparison").path("totalsEqual")) {
            assertTrue(equal.booleanValue());
        }
    }

    @Test
    void numericCellChangeIsDetectedWithoutToleranceOrRoundingTotals() throws Exception {
        final var fixture = new LocalizadorContentFixture(directory, 1);
        fixture.nativeRows.get(0).put(19, LocalizadorContentFixture.number("0.10000000000000002"));
        assertFalse(
                fixture.compare().path("contentEqualUnderCandidatePresentation").booleanValue());
    }

    @Test
    void nfArrayOrderAndDuplicateItemsArePreserved() throws Exception {
        final var fixture = new LocalizadorContentFixture(directory, 1);
        fixture.nativeRows.get(0).put(3, LocalizadorContentFixture.text("Authored A | Authored B"));
        assertFalse(
                fixture.compare().path("contentEqualUnderCandidatePresentation").booleanValue());
        fixture.nativeRows
                .get(0)
                .put(3, LocalizadorContentFixture.text("Authored B | Authored A | Authored A"));
        assertFalse(
                fixture.compare().path("contentEqualUnderCandidatePresentation").booleanValue());
    }

    @Test
    void emptyArrayAbsentPolicyIsExplicitAndDoesNotCollapseOneEmptyItemOrNull() throws Exception {
        final var fixture = new LocalizadorContentFixture(directory, 1);
        fixture.records.get(0).putArray("invoices_mapping");
        fixture.nativeRows.get(0).remove(3);
        assertFalse(
                fixture.compare().path("contentEqualUnderCandidatePresentation").booleanValue());
        fixture.field(3).put("emptyArrayPresentation", "EMPTY_ARRAY_NATIVE_ABSENT");
        assertTrue(fixture.compare().path("contentEqualUnderCandidatePresentation").booleanValue());
        fixture.records.get(0).putArray("invoices_mapping").add("");
        assertFalse(
                fixture.compare().path("contentEqualUnderCandidatePresentation").booleanValue());
        fixture.records.get(0).putNull("invoices_mapping");
        assertFalse(
                fixture.compare().path("contentEqualUnderCandidatePresentation").booleanValue());
    }

    @Test
    void arrayPolicyCannotBeMovedToTextAndNestedItemsAreRefused() throws Exception {
        final var fixture = new LocalizadorContentFixture(directory, 1);
        fixture.field(1).put("emptyArrayPresentation", "STRICT");
        assertThrows(IllegalArgumentException.class, fixture::compare);
        fixture.field(1).remove("emptyArrayPresentation");
        fixture.records.get(0).putArray("invoices_mapping").addObject().put("invented", "value");
        assertThrows(IllegalArgumentException.class, fixture::compare);
    }

    @Test
    void local1000ItemArrayAndLargeLiteralRecordAreNotReducedToTheCapLineLimit() throws Exception {
        final var fixture = new LocalizadorContentFixture(directory, 1);
        final var array = fixture.records.get(0).putArray("invoices_mapping");
        for (int item = 0; item < 1000; item++) {
            array.add("x".repeat(300));
        }
        final String line =
                new String(fixture.sourceBytes(), java.nio.charset.StandardCharsets.UTF_8)
                        .stripTrailing();
        assertTrue(line.length() > 262144 && line.length() < 1048576);
        final var raw = LocalizadorContentAdapter.record(line).get("invoices_mapping");
        assertEquals(1000, raw.value().size());
        assertEquals(
                "EXPORT_ATOM_BOUND",
                assertThrows(
                                IllegalArgumentException.class,
                                () -> LocalizadorContentAdapter.original(raw))
                        .getMessage());
        assertEquals(
                "EXPORT_ATOM_BOUND",
                assertThrows(
                                IllegalArgumentException.class,
                                () ->
                                        LocalizadorContentAdapter.sourceValue(
                                                fixture.field(3), raw, fixture.catalog))
                        .getMessage());
        array.removeAll();
        for (int item = 0; item < 1000; item++) {
            array.add("x");
        }
        final var bounded = new LocalizadorContentAdapter.Raw(array, null);
        assertEquals(1000, bounded.value().size());
        assertEquals(
                String.join(
                        fixture.field(3).path("arraySeparator").textValue(),
                        java.util.Collections.nCopies(1000, "x")),
                LocalizadorContentAdapter.sourceValue(fixture.field(3), bounded, fixture.catalog)
                        .value());
        array.add("extra");
        final var oversized = new LocalizadorContentAdapter.Raw(array, null);
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        LocalizadorContentAdapter.sourceValue(
                                fixture.field(3), oversized, fixture.catalog));
    }

    @Test
    void threeEnumDictionariesAreExplicitAndUnknownCodesOrLabelsRefuse() throws Exception {
        for (final String name : java.util.List.of("type", "fit_fln_status", "service_type")) {
            final var fixture = new LocalizadorContentFixture(directory, 1);
            fixture.records.get(0).put(name, "unobserved");
            assertThrows(IllegalArgumentException.class, fixture::compare);
        }
        final var fixture = new LocalizadorContentFixture(directory, 1);
        fixture.nativeRows.get(0).put(4, LocalizadorContentFixture.text("unobserved label"));
        assertThrows(IllegalArgumentException.class, fixture::compare);
    }

    @Test
    void nullableTimestampBlankRequiresItsExactCarrierAndExplicitPolicy() throws Exception {
        final var fixture = new LocalizadorContentFixture(directory, 1);
        fixture.records.get(0).putNull("finished_at");
        fixture.nativeRows.get(0).put(8, new LocalizadorContentFixture.Cell(null, null, 2));
        assertFalse(
                fixture.compare().path("contentEqualUnderCandidatePresentation").booleanValue());
        fixture.field(8).put("nullPresentation", "NULL_NATIVE_BLANK");
        assertTrue(fixture.compare().path("contentEqualUnderCandidatePresentation").booleanValue());
        fixture.nativeRows.get(0).put(8, new LocalizadorContentFixture.Cell(null, null, 0));
        assertThrows(IllegalArgumentException.class, fixture::compare);
    }

    @Test
    void timestampFractionsAndOffsetsRemainOriginalAndTruncationRequiresDeclaredPolicy()
            throws Exception {
        final var fixture = new LocalizadorContentFixture(directory, 1);
        fixture.records.get(0).put("finished_at", "2026-10-13T12:00:00.123456789+02:00");
        assertThrows(IllegalArgumentException.class, fixture::compare);
        fixture.field(8).put("sourceTimestampEncoding", "SOURCE_OFFSET_CIVIL_TRUNCATE_FRACTION_V1");
        final var result = fixture.compare();
        assertTrue(result.path("contentEqualUnderCandidatePresentation").booleanValue());
        assertNotEquals(
                result.path("originalValueProjection").path("api"),
                result.path("originalValueProjection").path("ui"));
        assertEquals(
                "2026-10-13T12:00:00",
                LocalizadorContentAdapter.sourceTimestamp("2026-10-13T12:00:00+02:00"));
    }

    @Test
    void serviceDateScopeUsesCivilDateWithoutConvertingOffsetToUtc() throws Exception {
        final var fixture = new LocalizadorContentFixture(directory, 1);
        fixture.records.get(0).put("service_at", "2026-10-19T00:00:00+14:00");
        assertThrows(IllegalArgumentException.class, fixture::compare);
    }

    @Test
    void candidateMinutaCannotBecomeTotalOrIdentityAndBindingsCannotSwap() throws Exception {
        final var fixture = new LocalizadorContentFixture(directory, 1);
        fixture.field(2).put("total", true);
        fixture.catalog.put("totalColumnCount", 5);
        assertThrows(IllegalArgumentException.class, fixture::compare);
        fixture.field(2).put("total", false);
        fixture.catalog.put("totalColumnCount", 4);
        fixture.catalog.put("idColumnIndex", 1);
        assertThrows(IllegalArgumentException.class, fixture::compare);
        fixture.catalog.remove("idColumnIndex");
        fixture.field(1).put("sourceField", "corporation_sequence_number");
        assertThrows(IllegalArgumentException.class, fixture::compare);
    }

    @Test
    void decimalEncodingAndTimestampCarrierAreNeverInheritedFromAnotherEntity() throws Exception {
        final var fixture = new LocalizadorContentFixture(directory, 1);
        fixture.field(19)
                .put("sourceNumberEncoding", "SOURCE_BINARY64_DECIMAL16_HALF_EVEN_BINARY64_V1");
        assertThrows(IllegalArgumentException.class, fixture::compare);
        fixture.field(19).remove("sourceNumberEncoding");
        fixture.nativeRows
                .get(0)
                .put(
                        6,
                        new LocalizadorContentFixture.Cell(
                                null, LocalizadorContentFixture.serial("0.5"), 3));
        assertThrows(IllegalArgumentException.class, fixture::compare);
    }

    @Test
    void literalNumericTokensAndAllOriginalArrayItemsSurviveTheSourceBoundary() throws Exception {
        final var fixture = new LocalizadorContentFixture(directory, 1);
        fixture.sequenceCodeLexeme = "1e0";
        final String line =
                new String(fixture.sourceBytes(), java.nio.charset.StandardCharsets.UTF_8)
                        .stripTrailing();
        final var value =
                LocalizadorContentAdapter.original(
                        LocalizadorContentAdapter.record(line).get("corporation_sequence_number"));
        assertEquals("1e0", value.value());
        assertEquals("JSON:NUMBER", value.carrier());
        assertTrue(
                LocalizadorContentAdapter.record(line).get("invoices_mapping").value().isArray());
    }

    @Test
    void unknownEnvelopeIncompleteJsonlAndChangedPinnedInputAreRefused() throws Exception {
        final var fixture = new LocalizadorContentFixture(directory, 1);
        fixture.entity = "CONTAS_A_PAGAR";
        assertThrows(IllegalArgumentException.class, fixture::compare);
        fixture.entity = "LOCALIZADOR";
        fixture.finalLf = false;
        assertThrows(IllegalArgumentException.class, fixture::compare);
        fixture.finalLf = true;
        final var pins = fixture.pins();
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        LocalizadorContentAdapter.compareWithPostcheckProbe(
                                fixture.request,
                                pins.source(),
                                pins.workbook(),
                                pins.catalog(),
                                LocalizadorContentFixture.budget(),
                                () -> {
                                    try {
                                        Files.writeString(pins.source().path(), "changed");
                                    } catch (final java.io.IOException refused) {
                                        throw new IllegalStateException(refused);
                                    }
                                }));
    }
}
