package br.com.esl.etl.v2.plataforma.reconciliacao.exportacao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Authored policies exercise the mechanism; no test asserts an observed ESL label or Native format.
 */
class SinistrosContentAdapterTest {
    @TempDir Path directory;

    @Test
    void all44ColumnsAuthoredTotalsAndOccurrencesAreComparedWithoutAcceptance() throws Exception {
        final var result = fixture(2).compare();
        assertTrue(result.path("contentEqualUnderCandidatePresentation").booleanValue());
        assertEquals(44, countTrue(result.path("comparison").path("columnsEqual")));
        assertEquals(12, countTrue(result.path("comparison").path("totalsEqual")));
        assertEquals(2, result.path("sourceRows").intValue());
        assertEquals(2, result.path("uiRows").intValue());
        assertTrue(result.path("openingDateVerifiedForEveryRowBothSides").booleanValue());
        assertTrue(result.path("pinsVerifiedBeforeAndAfter").booleanValue());
        for (final String flag :
                List.of(
                        "contentAccepted",
                        "entityQualified",
                        "productionAuthorized",
                        "mainResumeAllowed",
                        "presentationPoliciesQualified",
                        "catalogBindingPoliciesQualified",
                        "sourceCompletenessProven",
                        "snapshotIdentityProven",
                        "deduplicationPerformed")) {
            assertFalse(result.path(flag).booleanValue(), flag);
        }
    }

    @Test
    void finalCoordinate44IsPreservedAndComparedBeyondTheFirst32Columns() throws Exception {
        final var fixture = fixture(1);
        fixture.nativeRows.get(0).put(44, SinistrosContentFixture.text("Authored other"));
        final var result = fixture.compare();
        assertEquals(43, countTrue(result.path("comparison").path("columnsEqual")));
        assertFalse(result.path("comparison").path("columnsEqual").get(43).booleanValue());
        assertFalse(result.path("contentEqualUnderCandidatePresentation").booleanValue());
    }

    @Test
    void equalColumnBagsAndTotalsDoNotHideBrokenSequenceAssociations() throws Exception {
        final var fixture = fixture(2);
        final var first = fixture.nativeRows.get(0).get(40);
        fixture.nativeRows.get(0).put(40, fixture.nativeRows.get(1).get(40));
        fixture.nativeRows.get(1).put(40, first);
        final var result = fixture.compare();
        assertEquals(44, countTrue(result.path("comparison").path("columnsEqual")));
        assertEquals(12, countTrue(result.path("comparison").path("totalsEqual")));
        assertEquals(2, result.path("comparison").path("changedIds").intValue());
        assertFalse(result.path("comparison").path("fullTuplesEqual").booleanValue());
    }

    @Test
    void duplicateSequenceOccurrencesAreNeverDeduplicated() throws Exception {
        final var fixture = fixture(1);
        fixture.records.add(fixture.records.get(0).deepCopy());
        fixture.nativeRows.add(new LinkedHashMap<>(fixture.nativeRows.get(0)));
        assertTrue(fixture.compare().path("contentEqualUnderCandidatePresentation").booleanValue());
        fixture.nativeRows.remove(1);
        final var result = fixture.compare();
        assertEquals(2, result.path("sourceRows").intValue());
        assertEquals(1, result.path("comparison").path("changedMultiplicityIds").intValue());
        assertFalse(result.path("contentEqualUnderCandidatePresentation").booleanValue());
    }

    @Test
    void nullAbsentAndEmptyRequireSeparateExplicitPoliciesAndRetainOriginalValues()
            throws Exception {
        final var fixture = fixture(1);
        fixture.records.get(0).putNull("internal_description");
        fixture.nativeRows.get(0).remove(22);
        final var strict = fixture.compare();
        assertFalse(strict.path("contentEqualUnderCandidatePresentation").booleanValue());
        fixture.field(22).put("nullPresentation", "NULL_NATIVE_ABSENT");
        final var scoped = fixture.compare();
        assertTrue(scoped.path("contentEqualUnderCandidatePresentation").booleanValue());
        assertEquals(
                strict.path("originalValueProjection"), scoped.path("originalValueProjection"));
        fixture.records.get(0).put("internal_description", "");
        assertFalse(
                fixture.compare().path("contentEqualUnderCandidatePresentation").booleanValue());
        fixture.field(22).put("emptyStringPresentation", "EMPTY_NATIVE_ABSENT");
        assertTrue(fixture.compare().path("contentEqualUnderCandidatePresentation").booleanValue());
    }

    @Test
    void blankClockNeedsExactNumericCarrierAndExplicitNullPolicy() throws Exception {
        final var fixture = fixture(1);
        fixture.records.get(0).putNull("finished_at_time");
        fixture.nativeRows.get(0).put(20, new SinistrosContentFixture.Cell(null, null, 4));
        final var strict = fixture.compare();
        assertFalse(strict.path("contentEqualUnderCandidatePresentation").booleanValue());
        fixture.field(20).put("nullPresentation", "NULL_NATIVE_BLANK");
        final var scoped = fixture.compare();
        assertTrue(scoped.path("contentEqualUnderCandidatePresentation").booleanValue());
        assertEquals(
                strict.path("originalValueProjection"), scoped.path("originalValueProjection"));
        fixture.nativeRows.get(0).put(20, new SinistrosContentFixture.Cell(null, null, 0));
        assertThrows(IllegalArgumentException.class, fixture::compare);
    }

    @Test
    void enumMapsAreExplicitScopedAndRefuseUnknownSourceOrNativeLabels() throws Exception {
        for (final int column : List.of(43, 44)) {
            final var fixture = fixture(1);
            final String name = fixture.field(column).path("sourceField").textValue();
            fixture.records.get(0).put(name, "unobserved_code");
            assertThrows(IllegalArgumentException.class, fixture::compare);
            fixture.records.get(0).put(name, "authored_pending");
            fixture.nativeRows
                    .get(0)
                    .put(column, SinistrosContentFixture.text("Unobserved Native label"));
            assertThrows(IllegalArgumentException.class, fixture::compare);
        }
    }

    @Test
    void threeArrayFieldsKeepTheirOrderAndDuplicatesInTheOriginalAxis() throws Exception {
        for (final int column : List.of(8, 9, 10)) {
            final var fixture = fixture(1);
            final String name = fixture.field(column).path("sourceField").textValue();
            fixture.records
                    .get(0)
                    .putArray(name)
                    .add("Authored A")
                    .add("Authored B")
                    .add("Authored A");
            final var result = fixture.compare();
            assertFalse(
                    result.path("comparison").path("columnsEqual").get(column - 1).booleanValue());
            assertFalse(result.path("contentEqualUnderCandidatePresentation").booleanValue());
            fixture.nativeRows
                    .get(0)
                    .put(
                            column,
                            SinistrosContentFixture.text("Authored A | Authored B | Authored A"));
            assertTrue(
                    fixture.compare()
                            .path("contentEqualUnderCandidatePresentation")
                            .booleanValue());
        }
    }

    @Test
    void arraySeparatorCannotBeAssumedAndNonStringItemsAreRefused() throws Exception {
        final var fixture = fixture(1);
        fixture.field(8).remove("arraySeparator");
        assertThrows(IllegalArgumentException.class, fixture::compare);
        fixture.field(8).put("arraySeparator", " | ");
        fixture.records.get(0).putArray("rctrc").add("A").add(4);
        assertThrows(IllegalArgumentException.class, fixture::compare);
    }

    @Test
    void openingDateMustBeCivilAndInTheExactRequestedPeriodOnBothSides() throws Exception {
        final var fixture = fixture(1);
        fixture.records.get(0).put("opening_at_date", "2026-10-19");
        assertThrows(IllegalArgumentException.class, fixture::compare);
        fixture.records.get(0).put("opening_at_date", "2026-10-13T00:00:00-03:00");
        assertThrows(IllegalArgumentException.class, fixture::compare);
        fixture.records.get(0).put("opening_at_date", "2026-10-13");
        fixture.nativeRows
                .get(0)
                .put(
                        2,
                        new SinistrosContentFixture.Cell(
                                null,
                                new java.math.BigDecimal(SinistrosContentFixture.serial("0"))
                                        .add(java.math.BigDecimal.TEN)
                                        .toPlainString(),
                                3));
        assertThrows(IllegalArgumentException.class, fixture::compare);
    }

    @Test
    void sequenceKeyAdmitsGenericIntegralNumberWithoutInventingStringCoercion() throws Exception {
        final var fixture = fixture(1);
        fixture.sequenceCodeLexeme = "1.0e0";
        assertTrue(fixture.compare().path("contentEqualUnderCandidatePresentation").booleanValue());
        fixture.sequenceCodeLexeme = "1.5";
        assertThrows(ArithmeticException.class, fixture::compare);
        fixture.sequenceCodeLexeme = "0";
        assertThrows(IllegalArgumentException.class, fixture::compare);
        fixture.sequenceCodeLexeme = "9223372036854775808";
        assertThrows(IllegalArgumentException.class, fixture::compare);
        fixture.sequenceCodeLexeme = null;
        fixture.records.get(0).put("sequence_code", "1");
        assertThrows(IllegalArgumentException.class, fixture::compare);
    }

    @Test
    void rawNumericLexemeAndNegativeZeroRemainDistinctFromPresentationEncoding() throws Exception {
        final var fixture = fixture(1);
        fixture.numericLexeme = "1e-1";
        final var exponent = fixture.compare();
        assertTrue(exponent.path("contentEqualUnderCandidatePresentation").booleanValue());
        fixture.numericLexeme = "0.1";
        final var plain = fixture.compare();
        assertEquals(exponent.path("fullValueProjection"), plain.path("fullValueProjection"));
        assertNotEquals(
                exponent.path("originalValueProjection"), plain.path("originalValueProjection"));
        fixture.records.get(0).put("insurance_claim_total", "-0");
        fixture.nativeRows.get(0).put(35, SinistrosContentFixture.number("-0"));
        assertTrue(fixture.compare().path("contentEqualUnderCandidatePresentation").booleanValue());
        fixture.nativeRows.get(0).put(35, SinistrosContentFixture.number("0"));
        assertFalse(
                fixture.compare().path("contentEqualUnderCandidatePresentation").booleanValue());
    }

    @Test
    void catalogCannotSwapBindingsKindsCoordinatesTotalsOrAuthority() throws Exception {
        for (final String mutation : List.of("field", "kind", "coordinate", "total", "authority")) {
            final var fixture = fixture(1);
            switch (mutation) {
                case "field" -> fixture.field(43).put("sourceField", "icm_ttt_solution_type");
                case "kind" -> fixture.field(6).put("kind", "enum");
                case "coordinate" -> fixture.field(44).put("nativeColumn", 43);
                case "total" -> fixture.field(44).put("total", true);
                default -> fixture.catalog.put("presentationQualified", true);
            }
            assertThrows(IllegalArgumentException.class, fixture::compare);
        }
    }

    @Test
    void draftCatalogAndInheritedNumericModelAreRefusedBeforeBusinessComparison() throws Exception {
        final var fixture = fixture(1);
        fixture.catalog.put("catalogNature", "DRAFT_REQUIRES_OBSERVED_NATIVE");
        assertThrows(IllegalArgumentException.class, fixture::compare);
        fixture.catalog.put("catalogNature", "AUTHORED_SYNTHETIC");
        fixture.field(35).put("nativeNumberEncoding", "G16");
        assertThrows(IllegalArgumentException.class, fixture::compare);
    }

    @Test
    void fullJsonlRequiresAll44FieldsCorrectEnvelopeAndRealEof() throws Exception {
        for (final String mutation : List.of("lf", "extra", "missing", "trailing", "template")) {
            final var fixture = fixture(1);
            switch (mutation) {
                case "lf" -> fixture.finalLf = false;
                case "extra" -> fixture.records.get(0).put("unknown", "preserved but not admitted");
                case "missing" -> fixture.records.get(0).remove("icm_ttt_solution_type");
                case "trailing" -> fixture.sourceSuffix = "{}\n";
                default ->
                        fixture.template =
                                ExportValidationSupport.JSON.getNodeFactory().numberNode(10633);
            }
            assertThrows(IllegalArgumentException.class, fixture::compare);
        }
    }

    @Test
    void catalogChangeBetweenCalculationAndPostcheckIsRefused() throws Exception {
        final var fixture = fixture(1);
        final var pins = fixture.pins();
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SinistrosContentAdapter.compareWithPostcheckProbe(
                                fixture.request,
                                pins.source(),
                                pins.workbook(),
                                pins.catalog(),
                                SinistrosContentFixture.budget(),
                                () -> {
                                    try {
                                        Files.writeString(pins.catalog().path(), "{}");
                                    } catch (final java.io.IOException invalid) {
                                        throw new java.io.UncheckedIOException(invalid);
                                    }
                                }));
    }

    @Test
    void scopeAndKeyPoliciesCannotCollapseNullOrEmpty() throws Exception {
        for (final int column : List.of(1, 2)) {
            final var fixture = fixture(1);
            fixture.field(column).put("nullPresentation", "NULL_NATIVE_ABSENT");
            assertThrows(IllegalArgumentException.class, fixture::compare);
        }
    }

    @Test
    void clockPolicyCannotBeOmittedOrAttachedToAnUnrelatedField() throws Exception {
        final var fixture = fixture(1);
        fixture.field(4).remove("sourceTimeEncoding");
        assertThrows(IllegalArgumentException.class, fixture::compare);
        fixture.field(4).put("sourceTimeEncoding", "ISO_LOCAL_TIME_SECOND_V1");
        fixture.field(7).put("nativeTimeEncoding", "FRACTION_DAY_NEAREST_SECOND_V1");
        assertThrows(IllegalArgumentException.class, fixture::compare);
    }

    @Test
    void alteredDateClockAndTimestampCarriersAreNotQuietlyInterpreted() throws Exception {
        for (final int column : List.of(3, 4, 42)) {
            final var fixture = fixture(1);
            final var old = fixture.nativeRows.get(0).get(column);
            fixture.nativeRows
                    .get(0)
                    .put(column, new SinistrosContentFixture.Cell(old.type(), old.value(), 0));
            assertThrows(IllegalArgumentException.class, fixture::compare);
        }
    }

    @Test
    void emptyComparisonCannotClaimSuccessfulEquality() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> fixture(0).compare());
    }

    @Test
    void anAllAbsentTotalDoesNotBecomeAReadyZeroEvenWhenItsColumnMatches() throws Exception {
        final var fixture = fixture(1);
        fixture.records.get(0).putNull("invoices_count");
        fixture.nativeRows.get(0).remove(23);
        fixture.field(23).put("nullPresentation", "NULL_NATIVE_ABSENT");
        final var result = fixture.compare();
        assertTrue(result.path("comparison").path("columnsEqual").get(22).booleanValue());
        assertFalse(result.path("comparison").path("totalsReady").get(0).booleanValue());
        assertFalse(result.path("contentEqualUnderCandidatePresentation").booleanValue());
    }

    @Test
    void dateBearingClockComparesFullCivilValuesAndRetainsOriginalDateOffsetAndEncoding()
            throws Exception {
        final var fixture = dateBearingFixture();
        final var result = fixture.compare();
        assertTrue(result.path("contentEqualUnderCandidatePresentation").booleanValue());
        assertTrue(result.path("originalClockDateOffsetAndNativeEncodingRetained").booleanValue());
        fixture.records.get(0).put("occurrence_at_time", "2026-10-14T12:00:00-03:00");
        final var changed = fixture.compare();
        assertFalse(changed.path("comparison").path("columnsEqual").get(3).booleanValue());
        assertFalse(changed.path("contentEqualUnderCandidatePresentation").booleanValue());
    }

    @Test
    void nativeClockDateIsNotIgnoredEvenWhenItsDisplayedTimeIsUnchanged() throws Exception {
        final var fixture = dateBearingFixture();
        fixture.nativeRows
                .get(0)
                .put(
                        20,
                        new SinistrosContentFixture.Cell(
                                null,
                                new java.math.BigDecimal(SinistrosContentFixture.serial("0.5"))
                                        .add(java.math.BigDecimal.ONE)
                                        .toPlainString(),
                                4));
        final var result = fixture.compare();
        assertFalse(result.path("comparison").path("columnsEqual").get(19).booleanValue());
        assertFalse(result.path("contentEqualUnderCandidatePresentation").booleanValue());
    }

    @Test
    void dateBearingClockCannotBeComparedWithOnlyALocalSourceClockOrMixedPolicyPair()
            throws Exception {
        final var fixture = dateBearingFixture();
        fixture.records.get(0).put("occurrence_at_time", "12:00:00");
        assertThrows(IllegalArgumentException.class, fixture::compare);
        fixture.records.get(0).put("occurrence_at_time", "2026-10-13T12:00:00-03:00");
        fixture.field(4).put("nativeTimeEncoding", "FRACTION_DAY_NEAREST_SECOND_V1");
        assertThrows(IllegalArgumentException.class, fixture::compare);
    }

    @Test
    void dateBearingNativeClockCannotRoundAcrossMidnightIntoAnInventedSourceDay() throws Exception {
        final var fixture = dateBearingFixture();
        fixture.records.get(0).put("occurrence_at_time", "2026-10-14T00:00:00-03:00");
        fixture.nativeRows
                .get(0)
                .put(
                        4,
                        new SinistrosContentFixture.Cell(
                                null, SinistrosContentFixture.serial("0.999999999"), 4));
        assertThrows(IllegalArgumentException.class, fixture::compare);
    }

    @Test
    void claimLocationIsFreeTextAndCannotAcquireARecordByRecordEnumDictionary() throws Exception {
        final var fixture = fixture(1);
        final String narrative = "Authored location with accents: depósito / setor B, registro 123";
        fixture.records.get(0).put("insurance_claim_location", narrative);
        fixture.nativeRows.get(0).put(6, SinistrosContentFixture.text(narrative));
        assertTrue(fixture.compare().path("contentEqualUnderCandidatePresentation").booleanValue());
        ((com.fasterxml.jackson.databind.node.ObjectNode) fixture.catalog.path("enumLabels"))
                .putObject("insurance_claim_location")
                .put(narrative, narrative);
        assertThrows(IllegalArgumentException.class, fixture::compare);
    }

    @Test
    void emptyArrayNeedsItsOwnExplicitAbsentPolicyAndRetainsTheOriginalArrayAtom()
            throws Exception {
        for (final int column : List.of(8, 9, 10)) {
            final var fixture = fixture(1);
            final String field = fixture.field(column).path("sourceField").textValue();
            fixture.records.get(0).putArray(field);
            fixture.nativeRows.get(0).remove(column);
            final var strict = fixture.compare();
            assertFalse(strict.path("contentEqualUnderCandidatePresentation").booleanValue());
            fixture.field(column).put("emptyArrayPresentation", "EMPTY_ARRAY_NATIVE_ABSENT");
            final var scoped = fixture.compare();
            assertTrue(scoped.path("contentEqualUnderCandidatePresentation").booleanValue());
            assertEquals(
                    strict.path("originalValueProjection"), scoped.path("originalValueProjection"));
        }
    }

    @Test
    void oneEmptyStringElementIsNotAnEmptyArrayAndMustNotCollapseToAbsent() throws Exception {
        final var fixture = fixture(1);
        fixture.records.get(0).putArray("rctrc").add("");
        fixture.nativeRows.get(0).remove(8);
        fixture.field(8).put("emptyArrayPresentation", "EMPTY_ARRAY_NATIVE_ABSENT");
        assertFalse(
                fixture.compare().path("contentEqualUnderCandidatePresentation").booleanValue());
        fixture.nativeRows.get(0).put(8, SinistrosContentFixture.text(""));
        assertTrue(fixture.compare().path("contentEqualUnderCandidatePresentation").booleanValue());
    }

    @Test
    void nullRemainsDistinctFromEmptyArrayAndUsesOnlyItsOwnPolicy() throws Exception {
        final var fixture = fixture(1);
        fixture.records.get(0).putNull("rctrc");
        fixture.nativeRows.get(0).remove(8);
        fixture.field(8).put("emptyArrayPresentation", "EMPTY_ARRAY_NATIVE_ABSENT");
        assertFalse(
                fixture.compare().path("contentEqualUnderCandidatePresentation").booleanValue());
        fixture.field(8).put("nullPresentation", "NULL_NATIVE_ABSENT");
        assertTrue(fixture.compare().path("contentEqualUnderCandidatePresentation").booleanValue());
        fixture.records.get(0).putArray("rctrc");
        fixture.field(8).put("emptyArrayPresentation", "STRICT");
        assertFalse(
                fixture.compare().path("contentEqualUnderCandidatePresentation").booleanValue());
    }

    @Test
    void emptyArrayPolicyIsRequiredScopedAndCannotBeAttachedToTextFields() throws Exception {
        for (final String mutation : List.of("missing", "unknown", "foreign")) {
            final var fixture = fixture(1);
            switch (mutation) {
                case "missing" -> fixture.field(8).remove("emptyArrayPresentation");
                case "unknown" ->
                        fixture.field(8).put("emptyArrayPresentation", "INVENT_A_DEFAULT");
                default ->
                        fixture.field(6).put("emptyArrayPresentation", "EMPTY_ARRAY_NATIVE_ABSENT");
            }
            assertThrows(IllegalArgumentException.class, fixture::compare);
        }
    }

    private SinistrosContentFixture dateBearingFixture() throws Exception {
        final var fixture = fixture(1);
        for (final int column : List.of(4, 15, 20)) {
            fixture.field(column)
                    .put("sourceTimeEncoding", "SOURCE_OFFSET_DATE_CLOCK_SECOND_V1")
                    .put("nativeTimeEncoding", "NATIVE_DATE_TIME_NEAREST_SECOND_V1");
            fixture.records
                    .get(0)
                    .put(
                            fixture.field(column).path("sourceField").textValue(),
                            "2026-10-13T12:00:00-03:00");
            fixture.nativeRows
                    .get(0)
                    .put(
                            column,
                            new SinistrosContentFixture.Cell(
                                    null, SinistrosContentFixture.serial("0.5"), 4));
        }
        return fixture;
    }

    private SinistrosContentFixture fixture(final int count) throws Exception {
        return new SinistrosContentFixture(directory, count);
    }

    private static long countTrue(final JsonNode array) {
        long result = 0;
        for (final var item : array) {
            result += item.booleanValue() ? 1 : 0;
        }
        return result;
    }
}
