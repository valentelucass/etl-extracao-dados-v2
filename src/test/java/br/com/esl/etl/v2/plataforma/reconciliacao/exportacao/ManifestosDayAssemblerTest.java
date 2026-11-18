package br.com.esl.etl.v2.plataforma.reconciliacao.exportacao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ManifestosDayAssemblerTest {
    @TempDir Path directory;

    @Test
    void contiguousDisjointPagesAndTerminalEmptyCoverAllNativeRootsAndMultiplicity()
            throws Exception {
        final var fixture = new ManifestosDayFixture(directory, 2);
        final var result = assemble(fixture.completeInput());
        final var summary = result.path("summary");
        assertTrue(summary.path("comparisonPassed").booleanValue());
        assertTrue(summary.path("coverageComplete").booleanValue());
        assertTrue(summary.path("sourceTerminalEmptyPageObserved").booleanValue());
        assertEquals(2, summary.path("sourceRows").intValue());
        assertEquals(2, summary.path("distinctRoots").intValue());
        assertEquals(50, summary.path("numericTotalsEqual").intValue());
        assertEquals(0, summary.path("absenceOnlyTotalsEqual").intValue());
        assertEquals(50, result.path("totalComparisonsEqual").size());
        assertEquals(2, result.path("privateRootEvidence").size());
        assertFalse(summary.path("globalDistinctIdsSummed").booleanValue());
        assertFalse(summary.path("contentAccepted").booleanValue());
        assertFalse(summary.path("productionAuthorized").booleanValue());
    }

    @Test
    void fullDayWithoutNumbersOnlyQualifiesThePinnedColumn86AbsenceComparison() throws Exception {
        final var fixture = new ManifestosDayFixture(directory, 2);
        fixture.data.records.forEach(record -> record.putNull("mft_tl2_weight_capacity"));
        fixture.data.nativeRows.forEach(row -> row.remove(86));
        fixture.refreshWorkbook();
        final var result = assemble(fixture.completeInput());
        assertEquals(49, result.path("summary").path("numericTotalsEqual").intValue());
        assertEquals(1, result.path("summary").path("absenceOnlyTotalsEqual").intValue());
        final var total = result.path("aggregateTotals").get(49);
        assertEquals("ABSENCE_ONLY_EQUAL_NO_NUMERIC_SUM", total.path("comparisonKind").textValue());
        assertFalse(total.path("api").path("ready").booleanValue());
        assertEquals(0, total.path("api").path("numericOccurrences").intValue());
    }

    @Test
    void absenceOnlyFragmentIsNeutralWhenAnotherPageContainsAnActualNumericZero() throws Exception {
        final var fixture = new ManifestosDayFixture(directory, 2);
        fixture.data.records.get(0).putNull("mft_tl2_weight_capacity");
        fixture.data.nativeRows.get(0).remove(86);
        fixture.data.records.get(1).put("mft_tl2_weight_capacity", "0");
        fixture.data.nativeRows.get(1).put(86, ManifestosContentFixture.number("0"));
        fixture.refreshWorkbook();
        final var result = assemble(fixture.completeInput());
        assertEquals(50, result.path("summary").path("numericTotalsEqual").intValue());
        assertEquals(0, result.path("summary").path("absenceOnlyTotalsEqual").intValue());
        assertEquals(
                1,
                result.path("aggregateTotals")
                        .get(49)
                        .path("api")
                        .path("numericOccurrences")
                        .intValue());
        assertTrue(result.path("aggregateTotals").get(49).path("api").path("ready").booleanValue());
    }

    @Test
    void gapsRepeatsAndReorderedPageIndicesAreRefused() throws Exception {
        final var fixture = new ManifestosDayFixture(directory, 2);
        for (final int index : List.of(1, 3, 4)) {
            final var input = fixture.completeInput();
            ((ObjectNode) input.path("pages").get(1)).put("pageIndex", index);
            refused(input);
        }
    }

    @Test
    void overlapCannotDuplicateAWholeRootAcrossTwoOtherwiseEqualPages() throws Exception {
        final var fixture = new ManifestosDayFixture(directory, 1);
        final var input =
                fixture.input(
                        List.of(
                                fixture.entry(1, fixture.data.records),
                                fixture.entry(2, fixture.data.records),
                                fixture.entry(3, List.of())));
        assertEquals(
                "MANIFESTOS_DAY_OVERLAPPING_PAGE_ROOTS",
                assertThrows(IllegalArgumentException.class, () -> assemble(input)).getMessage());
    }

    @Test
    void nativeOnlyRootsCannotBeHiddenByAPassedSubsetPage() throws Exception {
        final var fixture = new ManifestosDayFixture(directory, 2);
        final var input =
                fixture.input(
                        List.of(
                                fixture.entry(1, List.of(fixture.data.records.get(0))),
                                fixture.entry(2, List.of())));
        assertEquals(
                "MANIFESTOS_DAY_NATIVE_ONLY_OR_MISSING_COVERAGE",
                assertThrows(IllegalArgumentException.class, () -> assemble(input)).getMessage());
    }

    @Test
    void selectedMultiplicityDifferencePreventsDayPromotion() throws Exception {
        final var fixture = new ManifestosDayFixture(directory, 1);
        fixture.data.nativeRows.add(new java.util.LinkedHashMap<>(fixture.data.nativeRows.get(0)));
        fixture.refreshWorkbook();
        refused(
                fixture.input(
                        List.of(
                                fixture.entry(1, fixture.data.records),
                                fixture.entry(2, List.of()))));
    }

    @Test
    void differentFingerprintBuildOrWorkbookPinIsRefused() throws Exception {
        final var fixture = new ManifestosDayFixture(directory, 2);
        final var fingerprint =
                fixture.completeInput().put("sourceConfigurationSha256", "c".repeat(64));
        refused(fingerprint);
        final var build = fixture.completeInput();
        build.set(
                "codeUnitPin",
                ExportValidationSupport.JSON.valueToTree(
                        fixture.pin(
                                ExportValidationSupport.JSON
                                        .createObjectNode()
                                        .put("otherBuild", true))));
        refused(build);
        final var workbook = fixture.completeInput();
        workbook.set(
                "nativeWorkbookPin",
                ExportValidationSupport.JSON.valueToTree(
                        fixture.data.pin("copy.xlsx", fixture.data.workbookBytes())));
        refused(workbook);
        final var catalog = fixture.completeInput();
        final var entry = (ObjectNode) catalog.path("pages").get(0);
        final var proof = fixture.read(entry.required("comparisonPin"));
        ((ObjectNode) proof.required("summary")).put("catalogSha256", "c".repeat(64));
        entry.set("comparisonPin", ExportValidationSupport.JSON.valueToTree(fixture.pin(proof)));
        refused(catalog);
    }

    @Test
    void nativeAllRootEvidenceMustBeIdenticalAcrossEveryPage() throws Exception {
        final var fixture = new ManifestosDayFixture(directory, 2);
        final var input = fixture.completeInput();
        final var entry = (ObjectNode) input.path("pages").get(1);
        final var proof = fixture.read(entry.required("comparisonPin"));
        ((ObjectNode) proof.path("privateRootEvidence").path("nativeAllRoots").get(1))
                .put("value", "3");
        entry.set("comparisonPin", ExportValidationSupport.JSON.valueToTree(fixture.pin(proof)));
        refused(input);
    }

    @Test
    void terminalMustBeUniqueLastAndActuallyEmptyWithNoComparisonProof() throws Exception {
        final var fixture = new ManifestosDayFixture(directory, 2);
        refused(fixture.input(List.of(fixture.entry(1, fixture.data.records))));
        refused(fixture.input(List.of(fixture.entry(1, List.of()), fixture.entry(2, List.of()))));
        final var input = fixture.completeInput();
        ((ObjectNode) input.path("pages").get(2))
                .set("comparisonPin", input.path("pages").get(0).required("comparisonPin"));
        refused(input);
    }

    @Test
    void unqualifiedNonnumericShardCannotBecomeANeutralNumericFragment() throws Exception {
        final var fixture = new ManifestosDayFixture(directory, 2);
        fixture.data.records.forEach(record -> record.putNull("mft_a_t_inss_value"));
        fixture.data.nativeRows.forEach(row -> row.remove(72));
        fixture.refreshWorkbook();
        assertEquals(
                "MANIFESTOS_DAY_NONNUMERIC_SHARD_NOT_NEUTRAL",
                assertThrows(
                                IllegalArgumentException.class,
                                () -> assemble(fixture.completeInput()))
                        .getMessage());
    }

    @Test
    void emptyDayRequiresPageOneRawEmptyAndHeaderOnlyNativeEofWithoutFiftyZeroTotals()
            throws Exception {
        final var fixture = new ManifestosDayFixture(directory, 0);
        final var input = fixture.input(List.of(fixture.entry(1, List.of())));
        final var result = assemble(input);
        final var summary = result.path("summary");
        assertTrue(summary.path("emptyDayProven").booleanValue());
        assertTrue(summary.path("comparisonPassed").booleanValue());
        assertFalse(summary.path("totalComparisonsApplicable").booleanValue());
        assertEquals(0, result.path("aggregateTotals").size());
        assertEquals(0, result.path("totalComparisonsEqual").size());
        assertEquals(0, summary.path("equalTotals").intValue());
        assertEquals(0, summary.path("sourceRows").intValue());
        assertEquals(0, result.path("privateRootEvidence").size());
        final var capture = fixture.read(input.path("pages").get(0).required("capturePin"));
        assertEquals(2, capture.path("dataPin").path("bytes").intValue());
        final var nonempty = new ManifestosDayFixture(directory, 1);
        refused(nonempty.input(List.of(nonempty.entry(1, List.of()))));
        refused(fixture.input(List.of(fixture.entry(2, List.of()))));
    }

    @Test
    void allProofAndWorkbookPinsAreRecheckedAfterComposition() throws Exception {
        final var fixture = new ManifestosDayFixture(directory, 2);
        final var input = fixture.completeInput();
        final var selected =
                ExportValidationSupport.Pin.from(
                        input.path("pages").get(0).required("comparisonPin"));
        assertEquals(
                "EXPORT_PIN_CHANGED",
                assertThrows(
                                IllegalArgumentException.class,
                                () ->
                                        ManifestosDayAssembler.assembleWithPostcheckProbe(
                                                input,
                                                ManifestosContentFixture.budget(),
                                                () -> {
                                                    try {
                                                        Files.write(
                                                                selected.path(), new byte[] {0});
                                                    } catch (final IOException failure) {
                                                        throw new UncheckedIOException(failure);
                                                    }
                                                }))
                        .getMessage());
    }

    @Test
    void exactDayRationalAndDyadicAccumulatorsMatchTheUnpartitionedAuthoredPair() throws Exception {
        final var fixture = new ManifestosDayFixture(directory, 2);
        final var result = assemble(fixture.completeInput());
        final var whole = fixture.data.compare().path("fullValueProjection");
        for (int index = 0; index < 50; index++) {
            for (final String side : List.of("api", "ui")) {
                for (final String axis :
                        List.of(
                                "exactDecimalNumerator",
                                "exactDecimalDenominator",
                                "sumOfEncodedCellUnits2PowMinus1074")) {
                    assertEquals(
                            whole.path(side).path("totals").get(index).path(axis),
                            result.path("aggregateTotals").get(index).path(side).path(axis));
                }
            }
        }
    }

    @Test
    void unknownOrUnclosedCaptureAndChangedShardProofsAreRefused() throws Exception {
        final var fixture = new ManifestosDayFixture(directory, 2);
        final var unknown = fixture.completeInput();
        final var entry = (ObjectNode) unknown.path("pages").get(0);
        final var capture =
                fixture.read(entry.required("capturePin")).put("wireOutcomeUnknown", true);
        entry.set("capturePin", ExportValidationSupport.JSON.valueToTree(fixture.pin(capture)));
        refused(unknown);
        final var unclosed = fixture.completeInput();
        ((ObjectNode) unclosed.path("pages").get(0))
                .set(
                        "captureClosurePin",
                        ExportValidationSupport.JSON.valueToTree(
                                fixture.pin(
                                        ExportValidationSupport.JSON
                                                .createObjectNode()
                                                .put("success", true)
                                                .put("originalClosedKnown", false)
                                                .put("stateRestored", true)
                                                .put("slotRetained", false))));
        refused(unclosed);
        final var changed = fixture.completeInput();
        final var part = (ObjectNode) changed.path("pages").get(0);
        final var proof = fixture.read(part.required("comparisonPin"));
        ((ObjectNode) proof.path("aggregateShards").get(0).path("api"))
                .put("numericOccurrences", 0);
        part.set("comparisonPin", ExportValidationSupport.JSON.valueToTree(fixture.pin(proof)));
        refused(changed);
    }

    @Test
    void bothTrailerAbsencesQualifyTwoDailyComparisonsWithoutNumericSums() throws Exception {
        final var fixture = new ManifestosDayFixture(directory, 2);
        for (final int column : List.of(84, 86)) {
            final String field =
                    column == 84 ? "mft_tl1_weight_capacity" : "mft_tl2_weight_capacity";
            fixture.data.records.forEach(record -> record.putNull(field));
            fixture.data.nativeRows.forEach(row -> row.remove(column));
        }
        fixture.refreshWorkbook();
        final var result = assemble(fixture.completeInput());
        assertTrue(result.path("summary").path("comparisonPassed").booleanValue());
        assertEquals(48, result.path("summary").path("numericTotalsEqual").intValue());
        assertEquals(2, result.path("summary").path("absenceOnlyTotalsEqual").intValue());
        assertEquals(50, result.path("summary").path("equalTotals").intValue());
        for (final int index : List.of(48, 49)) {
            final var total = result.path("aggregateTotals").get(index);
            assertEquals(
                    "ABSENCE_ONLY_EQUAL_NO_NUMERIC_SUM", total.path("comparisonKind").textValue());
            assertFalse(total.path("api").path("ready").booleanValue());
            assertEquals(0, total.path("api").path("numericOccurrences").intValue());
            assertEquals(
                    "ABSENT",
                    total.path("api").path("nonvalueAtoms").get(0).path("type").textValue());
            assertEquals(
                    2,
                    total.path("api").path("nonvalueAtoms").get(0).path("occurrences").intValue());
            assertEquals(total.path("api"), total.path("ui"));
        }
    }

    @Test
    void bothCapacityColumnsKeepActualZeroAndNumbersNumericAcrossAbsencePages() throws Exception {
        for (final String number : List.of("0", "3.5")) {
            final var fixture = new ManifestosDayFixture(directory, 2);
            for (final int column : List.of(84, 86)) {
                final String field =
                        column == 84 ? "mft_tl1_weight_capacity" : "mft_tl2_weight_capacity";
                fixture.data.records.get(0).putNull(field);
                fixture.data.nativeRows.get(0).remove(column);
                fixture.data.records.get(1).put(field, number);
                fixture.data.nativeRows.get(1).put(column, ManifestosContentFixture.number(number));
            }
            fixture.refreshWorkbook();
            final var result = assemble(fixture.completeInput());
            assertEquals(50, result.path("summary").path("numericTotalsEqual").intValue());
            assertEquals(0, result.path("summary").path("absenceOnlyTotalsEqual").intValue());
            for (final int index : List.of(48, 49)) {
                final var total = result.path("aggregateTotals").get(index);
                assertEquals("NUMERIC_TOTAL_EQUAL", total.path("comparisonKind").textValue());
                assertTrue(total.path("api").path("ready").booleanValue());
                assertEquals(1, total.path("api").path("numericOccurrences").intValue());
                assertEquals(
                        "ABSENT",
                        total.path("api").path("nonvalueAtoms").get(0).path("type").textValue());
                assertEquals(
                        1,
                        total.path("api")
                                .path("nonvalueAtoms")
                                .get(0)
                                .path("occurrences")
                                .intValue());
                assertEquals(total.path("api"), total.path("ui"));
            }
        }
    }

    @Test
    void nullAndBlankShardAtomsCannotBorrowEitherCapacityAbsencePolicy() throws Exception {
        for (final int column : List.of(84, 86)) {
            for (final String type : List.of("NULL", "BLANK")) {
                final var fixture = new ManifestosDayFixture(directory, 2);
                final String field =
                        column == 84 ? "mft_tl1_weight_capacity" : "mft_tl2_weight_capacity";
                fixture.data.records.forEach(record -> record.putNull(field));
                fixture.data.nativeRows.forEach(row -> row.remove(column));
                fixture.refreshWorkbook();
                final var input = fixture.completeInput();
                final var entry = (ObjectNode) input.path("pages").get(0);
                final var proof = fixture.read(entry.required("comparisonPin"));
                for (final String side : List.of("api", "ui")) {
                    final var shard =
                            (ObjectNode)
                                    proof.path("aggregateShards")
                                            .get(column == 84 ? 48 : 49)
                                            .path(side);
                    shard.put("absenceOnlyABSENT", false);
                    ((ObjectNode) shard.path("nonvalueAtoms").get(0)).put("type", type);
                }
                entry.set(
                        "comparisonPin",
                        ExportValidationSupport.JSON.valueToTree(fixture.pin(proof)));
                assertEquals(
                        "MANIFESTOS_DAY_NONNUMERIC_SHARD_NOT_NEUTRAL",
                        assertThrows(IllegalArgumentException.class, () -> assemble(input))
                                .getMessage());
            }
        }
    }

    private static ObjectNode assemble(final ObjectNode input) throws Exception {
        return ManifestosDayAssembler.assemble(input, ManifestosContentFixture.budget());
    }

    private static void refused(final ObjectNode input) {
        assertThrows(IllegalArgumentException.class, () -> assemble(input));
    }
}
