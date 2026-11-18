package br.com.esl.etl.v2.plataforma.reconciliacao.exportacao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ManifestosPageAdapterTest {
    @TempDir Path directory;

    @Test
    void equalPageRetainsAllNativeRootsAndReportsUncomparedNativeOnlyOccurrences()
            throws Exception {
        final var fixture = fixture(3);
        final var result = compare(fixture, List.of(fixture.records.get(1)));
        final var summary = result.required("summary");
        assertTrue(summary.path("pageValuesEqual").booleanValue());
        assertEquals(1, summary.path("sourceRows").intValue());
        assertEquals(3, summary.path("nativeAllRows").intValue());
        assertEquals(1, summary.path("nativeSelectedRows").intValue());
        assertEquals(2, summary.path("nativeOutsidePageRows").intValue());
        assertEquals(3, result.path("privateRootEvidence").path("nativeAllRoots").size());
        assertEquals(1, rootCount(result, "sourceRoots", "2"));
        assertEquals(1, rootCount(result, "nativeSelectedRoots", "2"));
        assertEquals(1, rootCount(result, "nativeAllRoots", "1"));
        assertFalse(summary.path("fullPeriodComparison").booleanValue());
        assertFalse(summary.path("coverageComplete").booleanValue());
        assertFalse(summary.path("comparisonPassed").booleanValue());
        assertFalse(summary.path("sourceTerminalEmptyPageVerified").booleanValue());
        assertTrue(summary.path("nativeReadToEof").booleanValue());
    }

    @Test
    void sourceRootMissingFromNativeFailsPageEqualityWithoutDroppingTheSourceRoot()
            throws Exception {
        final var fixture = fixture(2);
        fixture.nativeRows.remove(0);
        final var result = compare(fixture, List.of(fixture.records.get(0)));
        assertFalse(result.path("summary").path("pageValuesEqual").booleanValue());
        assertEquals(1, result.path("summary").path("sourceRootsMissingFromNative").intValue());
        assertEquals(0, result.path("summary").path("nativeSelectedRows").intValue());
        assertEquals(1, result.path("comparison").path("apiOnlyIds").intValue());
        assertEquals(1, rootCount(result, "sourceRoots", "1"));
        assertEquals(0, rootCount(result, "nativeSelectedRoots", "1"));
    }

    @Test
    void everyNativeOccurrenceOfSelectedRootMustMatchIncludingDuplicates() throws Exception {
        final var fixture = fixture(1);
        fixture.nativeRows.add(new LinkedHashMap<>(fixture.nativeRows.get(0)));
        final var one = compare(fixture, List.of(fixture.records.get(0)));
        assertFalse(one.path("summary").path("pageValuesEqual").booleanValue());
        assertEquals(2, rootCount(one, "nativeSelectedRoots", "1"));
        assertEquals(1, one.path("comparison").path("changedMultiplicityIds").intValue());
        final var duplicate =
                compare(
                        fixture,
                        List.of(fixture.records.get(0), fixture.records.get(0).deepCopy()));
        assertTrue(duplicate.path("summary").path("pageValuesEqual").booleanValue());
        assertEquals(2, rootCount(duplicate, "sourceRoots", "1"));
        assertEquals(1, duplicate.path("summary").path("sourceDistinctRoots").intValue());
    }

    @Test
    void pageUsesTheExistingExactIntegerAndDecimalSourceTypeConverters() throws Exception {
        for (final String field : List.of("sequence_code", "fleet_costs_subtotal")) {
            final var fixture = fixture(1);
            if ("sequence_code".equals(field)) {
                fixture.records.get(0).put(field, "1");
            } else {
                fixture.records.get(0).put(field, 0.1);
            }
            assertThrows(IllegalArgumentException.class, () -> compare(fixture, fixture.records));
        }
    }

    @Test
    void sharedObservedEnumAndAbsencePoliciesDoNotForceFiftyQualifiedShardTotals()
            throws Exception {
        final var fixture = fixture(1);
        fixture.records
                .get(0)
                .put("mft_cat_cot_status", "pending")
                .put("mft_mdr_contract_type", "")
                .putNull("mft_tl1_weight_capacity")
                .putNull("mft_tl2_weight_capacity");
        fixture.nativeRows.get(0).put(21, ManifestosContentFixture.text("Pendente"));
        for (final int column : List.of(46, 84, 86)) {
            fixture.nativeRows.get(0).remove(column);
        }
        final var result = compare(fixture, fixture.records);
        assertTrue(result.path("summary").path("pageValuesEqual").booleanValue());
        assertFalse(result.path("comparison").path("totalsReady").get(48).booleanValue());
        assertFalse(result.path("comparison").path("totalsReady").get(49).booleanValue());
        assertFalse(result.path("comparison").path("totalsEqual").get(49).booleanValue());
        assertFalse(result.has("totalComparisonsEqual"));
        assertTrue(result.path("summary").path("totalsAreShardDiagnostics").booleanValue());
        assertEquals(0, shard(result, 86, "api").path("numericOccurrences").intValue());
        assertTrue(shard(result, 86, "api").path("absenceOnlyABSENT").booleanValue());
        assertTrue(shard(result, 84, "api").path("absenceOnlyABSENT").booleanValue());
        assertTrue(shard(result, 84, "api").path("allAtomsQualified").booleanValue());
    }

    @Test
    void
            shardEvidenceCountsActualNumericOccurrencesAndRetainsMergeableNonvalueAtomsAndAccumulators()
                    throws Exception {
        final var fixture = fixture(2);
        fixture.records.get(0).putNull("mft_tl2_weight_capacity");
        fixture.nativeRows.get(0).remove(86);
        final var result = compare(fixture, fixture.records);
        for (final String side : List.of("api", "ui")) {
            final var total = shard(result, 86, side);
            assertEquals(1, total.path("numericOccurrences").intValue());
            assertEquals(1, total.path("nonvalueOccurrences").intValue());
            assertEquals(1, total.path("nonvalueBag").path("occurrences").intValue());
            assertTrue(total.path("ready").booleanValue());
            assertFalse(total.path("absenceOnlyABSENT").booleanValue());
            assertEquals("ABSENT", total.path("nonvalueAtoms").get(0).path("type").textValue());
            assertEquals(1, total.path("nonvalueAtoms").get(0).path("occurrences").intValue());
            assertTrue(total.path("allAtomsQualified").booleanValue());
        }
        assertEquals(shard(result, 86, "api"), shard(result, 86, "ui"));
    }

    @Test
    void rootSelectionCanonicalizesIntegralNumericKeysWhileRetainingTheRawLexeme()
            throws Exception {
        final var fixture = fixture(1);
        final var baseline = compare(fixture, fixture.records);
        final String original = new String(raw(fixture.records), StandardCharsets.UTF_8);
        final byte[] changed =
                original.replace("\"sequence_code\":1", "\"sequence_code\":1.0")
                        .getBytes(StandardCharsets.UTF_8);
        assertNotEquals(original, new String(changed, StandardCharsets.UTF_8));
        final var result = compareBytes(fixture, changed);
        assertTrue(result.path("summary").path("pageValuesEqual").booleanValue());
        assertEquals(1, rootCount(result, "sourceRoots", "1"));
        assertNotEquals(
                baseline.path("originalValueProjection").path("api").path("columnBags").get(2),
                result.path("originalValueProjection").path("api").path("columnBags").get(2));
    }

    @Test
    void sourcePageCannotContainMoreThanOneHundredDistinctRoots() throws Exception {
        final var fixture = fixture(101);
        assertEquals(
                "MANIFESTOS_PAGE_100_ROOT_BOUND",
                assertThrows(
                                IllegalArgumentException.class,
                                () -> compare(fixture, fixture.records))
                        .getMessage());
    }

    @Test
    void eachRawPageRecordMustHaveExactlyThePinnedNinetyOneFields() throws Exception {
        for (final boolean missing : new boolean[] {true, false}) {
            final var fixture = fixture(1);
            if (missing) {
                fixture.records.get(0).remove("mft_fis_fit_mey_mobile_receipt");
            } else {
                fixture.records.get(0).put("unqualified_extra", "value");
            }
            assertEquals(
                    "MANIFESTOS_PAGE_EXACT91_FIELDS",
                    assertThrows(
                                    IllegalArgumentException.class,
                                    () -> compare(fixture, fixture.records))
                            .getMessage());
        }
    }

    @Test
    void emptyArrayIsReservedForAnEmptyDayProofAndNonemptySourceAgainstHeaderOnlyUiFails()
            throws Exception {
        final var fixture = fixture(1);
        assertEquals(
                "MANIFESTOS_PAGE_EMPTY_REQUIRES_DAY_PROOF",
                assertThrows(IllegalArgumentException.class, () -> compare(fixture, List.of()))
                        .getMessage());
        fixture.nativeRows.clear();
        final var result = compare(fixture, fixture.records);
        assertEquals(0, result.path("summary").path("nativeAllRows").intValue());
        assertFalse(result.path("summary").path("pageValuesEqual").booleanValue());
        assertFalse(result.path("summary").path("coverageComplete").booleanValue());
    }

    @Test
    void arrayParserRequiresStrictUtf8DuplicateFreeJsonAndPhysicalEof() throws Exception {
        final var fixture = fixture(1);
        final String body = new String(raw(fixture.records), StandardCharsets.UTF_8);
        assertEquals(
                "MANIFESTOS_PAGE_RAW_ARRAY_EOF",
                assertThrows(
                                IllegalArgumentException.class,
                                () ->
                                        compareBytes(
                                                fixture,
                                                (body + "[]").getBytes(StandardCharsets.UTF_8)))
                        .getMessage());
        assertThrows(
                IOException.class,
                () ->
                        compareBytes(
                                fixture,
                                body.substring(0, body.length() - 1)
                                        .getBytes(StandardCharsets.UTF_8)));
        final String duplicate =
                body.replace("\"sequence_code\":1", "\"sequence_code\":1,\"sequence_code\":1");
        assertThrows(
                IOException.class,
                () -> compareBytes(fixture, duplicate.getBytes(StandardCharsets.UTF_8)));
        final byte[] invalid = raw(fixture.records);
        invalid[0] = (byte) 0xff;
        assertThrows(IOException.class, () -> compareBytes(fixture, invalid));
        assertThrows(
                IOException.class,
                () -> compareBytes(fixture, body.getBytes(StandardCharsets.UTF_16LE)));
    }

    @Test
    void completeNativeReaderChecksEofAndAllNinetyOneHeadersBeforeReturningASubset()
            throws Exception {
        final var fixture = fixture(2);
        final var page = fixture.pin("page.json", raw(List.of(fixture.records.get(0))));
        final byte[] book = fixture.workbookBytes();
        final var truncated = fixture.pin("truncated.xlsx", Arrays.copyOf(book, book.length - 4));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        ManifestosPageAdapter.comparePage(
                                fixture.request,
                                page,
                                truncated,
                                ManifestosContentFixture.budget()));
        ((ObjectNode) fixture.catalog.path("columns").get(90)).put("title", "Unqualified header");
        assertEquals(
                "EXPORT_HEADER_BINDING",
                assertThrows(
                                IllegalArgumentException.class,
                                () -> compare(fixture, List.of(fixture.records.get(0))))
                        .getMessage());
    }

    @Test
    void unselectedNativeRowsCannotHideAnInvalidRootKey() throws Exception {
        final var fixture = fixture(2);
        fixture.nativeRows.get(1).put(3, ManifestosContentFixture.text("2"));
        assertEquals(
                "MANIFESTOS_NATIVE_NUMERIC_TYPE",
                assertThrows(
                                IllegalArgumentException.class,
                                () -> compare(fixture, List.of(fixture.records.get(0))))
                        .getMessage());
    }

    @Test
    void rawAndNativePinsAreCheckedBeforeAndAfterEveryPageComparison() throws Exception {
        for (final boolean source : new boolean[] {true, false}) {
            final var fixture = fixture(1);
            final var page = fixture.pin("page.json", raw(fixture.records));
            final var book = fixture.pin("native.xlsx", fixture.workbookBytes());
            final var selected = source ? page : book;
            final String hash = selected.sha256();
            final var changed =
                    new ExportValidationSupport.Pin(
                            selected.path(),
                            (hash.startsWith("0") ? "1" : "0") + hash.substring(1),
                            selected.bytes());
            assertEquals(
                    "EXPORT_PIN_CHANGED",
                    assertThrows(
                                    IllegalArgumentException.class,
                                    () ->
                                            ManifestosPageAdapter.comparePage(
                                                    fixture.request,
                                                    source ? changed : page,
                                                    source ? book : changed,
                                                    ManifestosContentFixture.budget()))
                            .getMessage());
            assertEquals(
                    "EXPORT_PIN_CHANGED",
                    assertThrows(
                                    IllegalArgumentException.class,
                                    () ->
                                            ManifestosPageAdapter.comparePageWithPostcheckProbe(
                                                    fixture.request,
                                                    page,
                                                    book,
                                                    ManifestosContentFixture.budget(),
                                                    () -> {
                                                        try {
                                                            Files.write(
                                                                    selected.path(),
                                                                    new byte[] {0});
                                                        } catch (final IOException failure) {
                                                            throw new UncheckedIOException(failure);
                                                        }
                                                    }))
                            .getMessage());
        }
    }

    @Test
    void requestStaysUnchangedAndPublicSummaryExcludesPrivateRootsProjectionsAndAggregateValues()
            throws Exception {
        final var fixture = fixture(1);
        final var before = fixture.request.deepCopy();
        final var result = compare(fixture, fixture.records);
        assertEquals(before, fixture.request);
        assertEquals("MANIFESTOS_PAGE_COMPARISON_PRIVATE_V1", result.path("schema").textValue());
        assertTrue(result.path("containsPrivateRootEvidence").booleanValue());
        final var summary = result.path("summary");
        for (final String name :
                List.of(
                        "privateRootEvidence",
                        "aggregateShards",
                        "fullValueProjection",
                        "originalValueProjection")) {
            assertFalse(summary.has(name));
            assertTrue(result.has(name));
        }
        for (final String name :
                List.of(
                        "contentAccepted",
                        "entityQualified",
                        "productionAuthorized",
                        "Source",
                        "HTTP",
                        "SQL")) {
            if (summary.path(name).isBoolean()) {
                assertFalse(summary.path(name).booleanValue());
            } else {
                assertEquals(0, summary.path(name).intValue());
            }
        }
        assertTrue(summary.path("pinsVerifiedBeforeAndAfter").booleanValue());
    }

    @Test
    void
            full3108NativeRowsAnd1700PageOccurrencesFitTheSameCellBudgetWithoutComparingExcludedValues()
                    throws Exception {
        final var fixture = fixture(200);
        final var first = new ArrayList<>(fixture.records);
        final var nativeFirst = new ArrayList<>(fixture.nativeRows);
        fixture.records.clear();
        fixture.nativeRows.clear();
        for (int repeat = 0; repeat < 17; repeat++) {
            for (int index = 0; index < 100; index++) {
                fixture.records.add(first.get(index).deepCopy());
                fixture.nativeRows.add(new LinkedHashMap<>(nativeFirst.get(index)));
            }
        }
        for (int index = 0; index < 1408; index++) {
            fixture.nativeRows.add(new LinkedHashMap<>(nativeFirst.get(100 + index % 100)));
        }
        final var result = compare(fixture, fixture.records);
        final var summary = result.path("summary");
        assertTrue(summary.path("pageValuesEqual").booleanValue());
        assertEquals(3108, summary.path("nativeAllRows").intValue());
        assertEquals(1700, summary.path("nativeSelectedRows").intValue());
        assertEquals(1700, summary.path("sourceRows").intValue());
        assertEquals(1408, summary.path("nativeOutsidePageRows").intValue());
        assertEquals(100, summary.path("sourceDistinctRoots").intValue());
        assertEquals(200, summary.path("nativeAllDistinctRoots").intValue());
        assertEquals(901628, summary.path("limits").path("logicalCellVisits").longValue());
        assertEquals(1200000, summary.path("limits").path("maximumLogicalCellVisits").intValue());
        assertEquals(17, rootCount(result, "sourceRoots", "1"));
        assertFalse(summary.path("coverageComplete").booleanValue());
    }

    @Test
    void
            valuesOutsidePageAreRetainedForCoverageAndMustBeConvertedWhenTheirRootIsEventuallySelected()
                    throws Exception {
        final var fixture = fixture(2);
        fixture.nativeRows.get(1).put(19, ManifestosContentFixture.text("Unobserved status"));
        final var first = compare(fixture, List.of(fixture.records.get(0)));
        assertTrue(first.path("summary").path("pageValuesEqual").booleanValue());
        assertFalse(first.path("summary").path("allNativeRootValuesCompared").booleanValue());
        assertEquals(1, rootCount(first, "nativeAllRoots", "2"));
        assertThrows(
                IllegalArgumentException.class,
                () -> compare(fixture, List.of(fixture.records.get(1))));
    }

    @Test
    void pageDoesNotQualifyOtherTimestampNullOrUnknownStatusPresentation() throws Exception {
        for (final boolean nullable : new boolean[] {true, false}) {
            final var fixture = fixture(1);
            if (nullable) {
                fixture.records.get(0).putNull("created_at");
            } else {
                fixture.records.get(0).put("status", "unobserved_status");
            }
            assertThrows(IllegalArgumentException.class, () -> compare(fixture, fixture.records));
        }
    }

    @Test
    void observedInTransitAndFinishedAtBlankTimestampStyleTwoUseTheSameSharedConverters()
            throws Exception {
        final var fixture = fixture(2);
        fixture.records.get(0).put("status", "in_transit").putNull("finished_at");
        fixture.nativeRows.get(0).put(19, ManifestosContentFixture.text("Em trânsito"));
        fixture.nativeRows.get(0).put(13, new ManifestosContentFixture.Cell("n", null, 2));
        final var result = compare(fixture, List.of(fixture.records.get(0)));
        assertTrue(result.path("summary").path("pageValuesEqual").booleanValue());
        assertEquals(1, result.path("summary").path("nativeOutsidePageRows").intValue());
        assertEquals(2, fixture.nativeRows.get(0).get(13).style());
        assertTrue(fixture.records.get(0).path("finished_at").isNull());
        assertFalse(result.path("summary").path("coverageComplete").booleanValue());
    }

    @Test
    void bothCapacityShardsRetainRawNullAndNeverTreatNativeBlankAsAbsent() throws Exception {
        for (final int column : List.of(84, 86)) {
            final var fixture = fixture(1);
            final String field =
                    column == 84 ? "mft_tl1_weight_capacity" : "mft_tl2_weight_capacity";
            fixture.records.get(0).putNull(field);
            fixture.nativeRows.get(0).remove(column);
            final var result = compare(fixture, fixture.records);
            assertTrue(result.path("summary").path("pageValuesEqual").booleanValue());
            assertTrue(fixture.records.get(0).path(field).isNull());
            assertFalse(
                    result.path("originalValueProjection")
                            .path("api")
                            .path("columnBags")
                            .get(column - 1)
                            .equals(
                                    result.path("fullValueProjection")
                                            .path("api")
                                            .path("columnBags")
                                            .get(column - 1)));
            for (final String side : List.of("api", "ui")) {
                final var total = shard(result, column, side);
                assertTrue(total.path("absenceOnlyABSENT").booleanValue());
                assertFalse(total.path("ready").booleanValue());
                assertEquals(0, total.path("numericOccurrences").intValue());
                assertEquals("ABSENT", total.path("nonvalueAtoms").get(0).path("type").textValue());
            }
            fixture.nativeRows.get(0).put(column, new ManifestosContentFixture.Cell("n", null, 0));
            final var blank = compare(fixture, fixture.records);
            assertFalse(blank.path("summary").path("pageValuesEqual").booleanValue());
            assertFalse(shard(blank, column, "ui").path("absenceOnlyABSENT").booleanValue());
            assertEquals(
                    "NULL",
                    shard(blank, column, "ui")
                            .path("nonvalueAtoms")
                            .get(0)
                            .path("type")
                            .textValue());
        }
    }

    private ManifestosContentFixture fixture(final int rows) throws Exception {
        return new ManifestosContentFixture(directory, rows);
    }

    private static byte[] raw(final List<?> records) throws Exception {
        return ExportValidationSupport.JSON.writeValueAsBytes(records);
    }

    private ObjectNode compare(final ManifestosContentFixture fixture, final List<?> records)
            throws Exception {
        return compareBytes(fixture, raw(records));
    }

    private ObjectNode compareBytes(final ManifestosContentFixture fixture, final byte[] pageBytes)
            throws Exception {
        return ManifestosPageAdapter.comparePage(
                fixture.request,
                fixture.pin("page.json", pageBytes),
                fixture.pin("native.xlsx", fixture.workbookBytes()),
                ManifestosContentFixture.budget());
    }

    private static long rootCount(final JsonNode result, final String name, final String root) {
        for (final var entry : result.path("privateRootEvidence").path(name)) {
            if (root.equals(entry.path("value").textValue())) {
                return entry.path("occurrences").longValue();
            }
        }
        return 0;
    }

    private static JsonNode shard(final JsonNode result, final int column, final String side) {
        for (final var shard : result.required("aggregateShards")) {
            if (shard.path("nativeColumn").intValue() == column) {
                return shard.required(side);
            }
        }
        throw new AssertionError("Missing total column " + column);
    }
}
