package br.com.esl.etl.v2.plataforma.reconciliacao.exportacao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** All Native presentation choices are authored fixtures, never assertions about the ESL writer. */
class InventarioContentAdapterTest {
    @TempDir Path directory;

    @Test
    void explicitAuthoredCatalogComparesAll26ColumnsSixAuthoredTotalsAndEveryOccurrence()
            throws Exception {
        final var result = fixture(2).compare();
        assertTrue(result.path("contentEqualUnderCandidatePresentation").booleanValue());
        assertEquals("AUTHORED_SYNTHETIC", result.path("catalogNature").textValue());
        assertEquals(26, trueCount(result.path("comparison").path("columnsEqual")));
        assertEquals(6, trueCount(result.path("comparison").path("totalsEqual")));
        assertEquals(2, result.path("sourceRows").intValue());
        assertEquals(2, result.path("uiRows").intValue());
        assertTrue(result.path("catalogPinnedExplicitly").booleanValue());
        assertTrue(result.path("pinsVerifiedBeforeAndAfter").booleanValue());
        assertTrue(result.path("startedAtDateVerifiedForEveryRowBothSides").booleanValue());
        for (final String flag :
                List.of(
                        "contentAccepted",
                        "entityQualified",
                        "productionAuthorized",
                        "mainResumeAllowed",
                        "catalogBindingPoliciesQualified",
                        "presentationPoliciesQualified",
                        "sourceCompletenessProven",
                        "snapshotIdentityProven",
                        "deduplicationPerformed")) {
            assertFalse(result.path(flag).booleanValue(), flag);
        }
    }

    @Test
    void lastColumnDifferencesAreComparedAfterAllEarlierColumns() throws Exception {
        final var fixture = fixture(2);
        fixture.nativeRows
                .get(0)
                .put(26, InventarioContentFixture.text("Authored changed occurrence"));
        final var result = fixture.compare();
        assertEquals(25, trueCount(result.path("comparison").path("columnsEqual")));
        assertFalse(result.path("comparison").path("columnsEqual").get(25).booleanValue());
        assertFalse(result.path("contentEqualUnderCandidatePresentation").booleanValue());
    }

    @Test
    void swappedValuesKeepColumnBagsAndTotalsEqualButExposeBrokenSequenceAssociations()
            throws Exception {
        final var fixture = fixture(2);
        final var first = fixture.nativeRows.get(0).get(26);
        fixture.nativeRows.get(0).put(26, fixture.nativeRows.get(1).get(26));
        fixture.nativeRows.get(1).put(26, first);
        final var result = fixture.compare();
        assertEquals(26, trueCount(result.path("comparison").path("columnsEqual")));
        assertEquals(6, trueCount(result.path("comparison").path("totalsEqual")));
        assertEquals(2, result.path("comparison").path("changedIds").intValue());
        assertFalse(result.path("comparison").path("fullTuplesEqual").booleanValue());
    }

    @Test
    void repeatedSequenceOccurrencesAreNeverDeduplicated() throws Exception {
        final var fixture = fixture(1);
        fixture.records.add(fixture.records.get(0).deepCopy());
        fixture.nativeRows.add(new LinkedHashMap<>(fixture.nativeRows.get(0)));
        assertTrue(fixture.compare().path("contentEqualUnderCandidatePresentation").booleanValue());
        fixture.nativeRows.remove(1);
        final var result = fixture.compare();
        assertEquals(2, result.path("sourceRows").intValue());
        assertEquals(1, result.path("comparison").path("apiDistinctIds").intValue());
        assertEquals(1, result.path("comparison").path("changedMultiplicityIds").intValue());
        assertFalse(result.path("contentEqualUnderCandidatePresentation").booleanValue());
    }

    @Test
    void nullAbsentEmptyAndBlankStayDifferentUntilAnExplicitFieldCandidateIsSelected()
            throws Exception {
        final var fixture = fixture(1);
        fixture.records.get(0).putNull("cnr_c_s_fit_fte_lce_ore_description");
        fixture.nativeRows.get(0).remove(26);
        final var strict = fixture.compare();
        assertFalse(strict.path("comparison").path("columnsEqual").get(25).booleanValue());
        fixture.field(26).put("nullPresentation", "NULL_NATIVE_ABSENT");
        final var scoped = fixture.compare();
        assertTrue(scoped.path("contentEqualUnderCandidatePresentation").booleanValue());
        assertEquals(
                strict.path("originalValueProjection"), scoped.path("originalValueProjection"));
        fixture.records.get(0).put("cnr_c_s_fit_fte_lce_ore_description", "");
        assertFalse(
                fixture.compare().path("comparison").path("columnsEqual").get(25).booleanValue());
        fixture.field(26).put("emptyStringPresentation", "EMPTY_NATIVE_ABSENT");
        assertTrue(fixture.compare().path("contentEqualUnderCandidatePresentation").booleanValue());
        fixture.nativeRows.get(0).put(26, new InventarioContentFixture.Cell(null, null, 0));
        assertThrows(IllegalArgumentException.class, fixture::compare);
    }

    @Test
    void timestampNullBlankRequiresExplicitExactCarrierAndLeavesTheOriginalAxesUntouched()
            throws Exception {
        final var fixture = fixture(1);
        fixture.records.get(0).putNull("finished_at");
        fixture.nativeRows.get(0).put(13, new InventarioContentFixture.Cell(null, null, 2));
        final var strict = fixture.compare();
        assertFalse(strict.path("comparison").path("columnsEqual").get(12).booleanValue());
        fixture.field(13).put("nullPresentation", "NULL_NATIVE_BLANK");
        final var scoped = fixture.compare();
        assertTrue(scoped.path("contentEqualUnderCandidatePresentation").booleanValue());
        assertEquals(
                strict.path("originalValueProjection"), scoped.path("originalValueProjection"));
        fixture.nativeRows.get(0).put(13, new InventarioContentFixture.Cell(null, null, 0));
        assertThrows(IllegalArgumentException.class, fixture::compare);
    }

    @Test
    void noRealEnumTranslationIsGuessedForUnknownSourceOrNativeLabels() throws Exception {
        final var fixture = fixture(1);
        fixture.records.get(0).put("status", "unobserved_status");
        assertEquals(
                "INVENTARIO_SOURCE_ENUM_UNOBSERVED",
                assertThrows(IllegalArgumentException.class, fixture::compare).getMessage());
        fixture.records.get(0).put("status", "authored_pending");
        fixture.nativeRows.get(0).put(14, InventarioContentFixture.text("unobserved native label"));
        assertEquals(
                "INVENTARIO_NATIVE_ENUM_UNOBSERVED",
                assertThrows(IllegalArgumentException.class, fixture::compare).getMessage());
    }

    @Test
    void arrayOrderAndDuplicateItemsStayInOriginalProjectionAndAChangedJoinRemainsVisible()
            throws Exception {
        final var fixture = fixture(1);
        final var baseline = fixture.compare();
        fixture.records
                .get(0)
                .putArray("cnr_c_s_fit_invoices_mapping")
                .add("Authored A")
                .add("Authored B");
        final var missing = fixture.compare();
        assertFalse(missing.path("comparison").path("columnsEqual").get(15).booleanValue());
        assertNotEquals(bag(baseline, 15), bag(missing, 15));
        fixture.records
                .get(0)
                .putArray("cnr_c_s_fit_invoices_mapping")
                .add("Authored B")
                .add("Authored A")
                .add("Authored A");
        assertFalse(
                fixture.compare().path("comparison").path("columnsEqual").get(15).booleanValue());
        assertTrue(baseline.path("arrayPresentationMayBeNonInjective").booleanValue());
        assertTrue(
                baseline.path("sourceArrayOrderAndDuplicatesRetainedInOriginalProjection")
                        .booleanValue());
    }

    @Test
    void arraySeparatorIsRequiredAndTheUiCommaSpaceHypothesisIsNotABuiltInRule() throws Exception {
        final var fixture = fixture(1);
        fixture.field(16).remove("arraySeparator");
        assertThrows(IllegalArgumentException.class, fixture::compare);
        fixture.field(16).put("arraySeparator", ", ");
        assertFalse(
                fixture.compare().path("comparison").path("columnsEqual").get(15).booleanValue());
        fixture.records.get(0).putArray("cnr_c_s_fit_invoices_mapping").add(9);
        assertEquals(
                "INVENTARIO_SOURCE_ARRAY_STRING_ITEM",
                assertThrows(IllegalArgumentException.class, fixture::compare).getMessage());
    }

    @Test
    void cotacoesSpecificG16ModelCannotBeInheritedWithoutInventarioEvidence() throws Exception {
        final var fixture = fixture(1);
        fixture.field(18)
                .put("nativeNumberEncoding", "COTACOES_BINARY64_G16_NATIVE_SERIALIZATION_V1");
        assertEquals(
                "INVENTARIO_EXPLICIT_NUMERIC_ENCODING",
                assertThrows(IllegalArgumentException.class, fixture::compare).getMessage());
    }

    @Test
    void directEncodingKeepsEquivalentNumericLexemesAndSignedZeroInTheOriginalAxes()
            throws Exception {
        final var fixture = fixture(1);
        final var baseline = fixture.compare();
        fixture.numericLexeme = "0.10000000000000001";
        final var equivalent = fixture.compare();
        assertTrue(equivalent.path("contentEqualUnderCandidatePresentation").booleanValue());
        assertNotEquals(bag(baseline, 20), bag(equivalent, 20));
        fixture.records.get(0).put("cnr_c_s_fit_invoices_value", "-0");
        fixture.nativeRows.get(0).put(17, InventarioContentFixture.number("0"));
        assertFalse(
                fixture.compare().path("comparison").path("columnsEqual").get(16).booleanValue());
    }

    @Test
    void anAllAbsentNumericTotalCannotQualifyByInventingZero() throws Exception {
        final var fixture = fixture(1);
        fixture.records.get(0).putNull("cnr_c_s_fit_invoices_value");
        fixture.nativeRows.get(0).remove(17);
        fixture.field(17).put("nullPresentation", "NULL_NATIVE_ABSENT");
        final var result = fixture.compare();
        assertTrue(result.path("comparison").path("columnsEqual").get(16).booleanValue());
        assertFalse(result.path("comparison").path("totalsReady").get(0).booleanValue());
        assertFalse(result.path("contentEqualUnderCandidatePresentation").booleanValue());
    }

    @Test
    void catalogPhysicalDriftDuringPostcheckCannotReturnAPositiveResult() throws Exception {
        final var fixture = fixture(1);
        final var pins = fixture.pins();
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        InventarioContentAdapter.compareWithPostcheckProbe(
                                fixture.request,
                                pins.source(),
                                pins.workbook(),
                                pins.catalog(),
                                InventarioContentFixture.budget(),
                                () -> {
                                    try {
                                        Files.writeString(pins.catalog().path(), "{}");
                                    } catch (final java.io.IOException error) {
                                        throw new UncheckedIOException(error);
                                    }
                                }));
    }

    @Test
    void recordReaderKeepsExponentSpellingAndArrayOrderWhileExact26FieldsAreRequired()
            throws Exception {
        final var record =
                InventarioContentAdapter.record(
                        "{\"entity\":\"INVENTARIO\",\"templateId\":10633,"
                                + "\"record\":{\"x\":7.2500e+1,\"a\":[\"B\",\"B\",\"A\"]}}");
        assertEquals("7.2500e+1", InventarioContentAdapter.original(record.get("x")).value());
        assertEquals(
                "[\"B\",\"B\",\"A\"]", InventarioContentAdapter.original(record.get("a")).value());
        assertEquals("JSON:ARRAY", InventarioContentAdapter.original(record.get("a")).carrier());
        final var fixture = fixture(1);
        fixture.records.get(0).put("extra_field", "hidden");
        assertThrows(IllegalArgumentException.class, fixture::compare);
        fixture.records.get(0).remove("extra_field");
        fixture.records.get(0).remove("status");
        assertThrows(IllegalArgumentException.class, fixture::compare);
    }

    @Test
    void jsonlFinalLfEnvelopeAndTrailingContentRemainMandatory() throws Exception {
        for (final String mutation : List.of("lf", "entity", "report", "suffix")) {
            final var fixture = fixture(1);
            switch (mutation) {
                case "lf" -> fixture.finalLf = false;
                case "entity" -> fixture.entity = "COTACOES";
                case "report" ->
                        fixture.template =
                                ExportValidationSupport.JSON.getNodeFactory().numberNode(6906);
                default -> fixture.sourceSuffix = "{}\n";
            }
            assertThrows(IllegalArgumentException.class, fixture::compare);
        }
    }

    @Test
    void scopeIsVerifiedAgainstStartedAtOnBothSides() throws Exception {
        final var fixture = fixture(1);
        fixture.records.get(0).put("started_at", "2026-10-19T00:00:00-03:00");
        assertThrows(IllegalArgumentException.class, fixture::compare);
        fixture.records.get(0).put("started_at", "2026-10-13T12:00:00-03:00");
        fixture.nativeRows
                .get(0)
                .put(
                        12,
                        new InventarioContentFixture.Cell(
                                null,
                                new java.math.BigDecimal(InventarioContentFixture.serial("0.5"))
                                        .add(java.math.BigDecimal.TEN)
                                        .toPlainString(),
                                2));
        assertThrows(IllegalArgumentException.class, fixture::compare);
    }

    @Test
    void coordinateTitleScopeKeyAndClaimedAuthorityCannotBeChangedInTheCandidate()
            throws Exception {
        for (final String mutation :
                List.of("coordinate", "title", "scope", "authority", "nature", "total")) {
            final var fixture = fixture(1);
            switch (mutation) {
                case "coordinate" -> fixture.field(26).put("nativeColumn", 25);
                case "title" -> fixture.field(26).put("title", "foreign title");
                case "scope" -> fixture.field(12).put("nullPresentation", "NULL_NATIVE_ABSENT");
                case "authority" -> fixture.catalog.put("contentAccepted", true);
                case "nature" -> fixture.catalog.put("catalogNature", "QUALIFIED_WITHOUT_EVIDENCE");
                default -> fixture.catalog.put("totalColumnCount", 5);
            }
            assertThrows(IllegalArgumentException.class, fixture::compare);
        }
    }

    @Test
    void sourceSequenceRequiresAnIntegralPositiveLongEvenWhenNativeWritesADecimalLexeme()
            throws Exception {
        for (final String value : List.of("0", "-1", "1.0", "9223372036854775808")) {
            final var fixture = fixture(1);
            fixture.sequenceCodeLexeme = value;
            assertThrows(IllegalArgumentException.class, fixture::compare);
        }
        final var fixture = fixture(1);
        fixture.nativeRows.get(0).put(9, InventarioContentFixture.number("1.0"));
        assertTrue(fixture.compare().path("contentEqualUnderCandidatePresentation").booleanValue());
    }

    @Test
    void noEmptyNonemptyComparisonIsInventedWhenEitherSideHasNoRows() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> fixture(0).compare());
    }

    private InventarioContentFixture fixture(final int count) throws Exception {
        return new InventarioContentFixture(directory, count);
    }

    private static int trueCount(final JsonNode array) {
        int count = 0;
        for (final var value : array) {
            count += value.isBoolean() && value.booleanValue() ? 1 : 0;
        }
        return count;
    }

    private static JsonNode bag(final JsonNode result, final int index) {
        return result.path("originalValueProjection").path("api").path("columnBags").get(index);
    }
}
