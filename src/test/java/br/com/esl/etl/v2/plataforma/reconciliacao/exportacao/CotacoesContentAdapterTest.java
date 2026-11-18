package br.com.esl.etl.v2.plataforma.reconciliacao.exportacao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CotacoesContentAdapterTest {
    @TempDir Path directory;

    @Test
    void pinnedCatalogHasExact37CoordinatesElevenTotalsAndOnlyObservedScopedPolicies()
            throws Exception {
        final var catalog = CotacoesContentAdapter.catalog();
        assertEquals(37, catalog.path("columns").size());
        assertEquals(11, catalog.path("totalColumnCount").intValue());
        int totals = 0;
        int coordinate = 0;
        final Set<String> nullAbsent =
                Set.of(
                        "qoe_qes_collect_subtotal",
                        "qoe_qes_delivery_subtotal",
                        "qoe_qes_destination_postal_code",
                        "qoe_qes_diy_name",
                        "qoe_qes_diy_sae_code",
                        "qoe_qes_fon_name",
                        "qoe_qes_freight_comments",
                        "qoe_qes_invoices_value",
                        "qoe_qes_invoices_volumes",
                        "qoe_qes_itr_subtotal",
                        "qoe_qes_ony_name",
                        "qoe_qes_ony_sae_code",
                        "qoe_qes_origin_postal_code",
                        "qoe_qes_other_fees",
                        "qoe_qes_real_weight",
                        "qoe_qes_status",
                        "qoe_qes_taxed_weight",
                        "qoe_qes_tde_subtotal",
                        "qoe_qes_total",
                        "qoe_qes_cre_name",
                        "qoe_qes_sdr_nickname",
                        "qoe_qes_rpt_nickname",
                        "qoe_qes_disapprove_comments",
                        "qoe_qes_fit_fdt_subtotal");
        final Set<String> emptyAbsent =
                Set.of(
                        "qoe_qes_sdr_document",
                        "qoe_qes_rpt_document",
                        "qoe_qes_origin_postal_code",
                        "qoe_qes_destination_postal_code",
                        "qoe_qes_freight_comments");
        for (final var field : catalog.required("columns")) {
            assertEquals(++coordinate, field.path("nativeColumn").intValue());
            totals += field.required("total").booleanValue() ? 1 : 0;
            final String name = field.path("sourceField").asText();
            final String expected =
                    coordinate == 17
                            ? "TIMESTAMP_NULL_NATIVE_BLANK"
                            : coordinate == 18
                                    ? "DATE_NULL_NATIVE_BLANK"
                                    : nullAbsent.contains(name) ? "NULL_NATIVE_ABSENT" : "STRICT";
            assertEquals(expected, field.path("nullPresentation").asText());
            assertEquals(
                    emptyAbsent.contains(name) ? "EMPTY_NATIVE_ABSENT" : "STRICT",
                    field.path("emptyStringPresentation").asText());
            if (coordinate == 17 || coordinate == 18) {
                assertEquals(
                        coordinate == 17
                                ? CotacoesContentAdapter.TIMESTAMP_CARRIER
                                : CotacoesContentAdapter.DATE_CARRIER,
                        field.path("nativeNullCarrier").asText());
            } else {
                assertFalse(field.has("nativeNullCarrier"));
            }
        }
        assertEquals(11, totals);
        assertFalse(catalog.path("presentationQualified").booleanValue());
        assertFalse(catalog.path("contentAccepted").booleanValue());
        assertEquals(3, catalog.path("enumLabels").path("qoe_qes_status").size());
        assertEquals(
                "Aprovado",
                catalog.path("enumLabels").path("qoe_qes_status").path("approved").asText());
        assertEquals(
                "Pendente",
                catalog.path("enumLabels").path("qoe_qes_status").path("pending").asText());
        assertEquals(
                "Reprovado",
                catalog.path("enumLabels").path("qoe_qes_status").path("disapproved").asText());
    }

    @Test
    void fullPairComparesEveryColumnTupleKeyMultiplicityAndElevenExactTotals() throws Exception {
        final var result = fixture(2).compare();
        assertTrue(result.path("contentEqualUnderCandidatePresentation").booleanValue());
        assertEquals(37, trueCount(result.path("comparison").path("columnsEqual")));
        assertEquals(11, trueCount(result.path("comparison").path("totalsEqual")));
        assertEquals(11, trueCount(result.path("comparison").path("totalsReady")));
        assertEquals(2, result.path("sourceRows").intValue());
        assertEquals(2, result.path("uiRows").intValue());
        assertEquals(37, result.path("columnDiagnostics").size());
        for (final String projection : List.of("fullValueProjection", "originalValueProjection")) {
            for (final String side : List.of("api", "ui")) {
                assertTrue(
                        result.path(projection)
                                .path(side)
                                .path("completeAllValues")
                                .booleanValue());
                assertEquals(37, result.path(projection).path(side).path("columnBags").size());
            }
        }
        assertTrue(result.path("pinsVerifiedBeforeAndAfter").booleanValue());
        assertTrue(result.path("requestedAtDateVerifiedForEveryRowBothSides").booleanValue());
        for (final String flag :
                List.of(
                        "trustedSourceCompleteness",
                        "sourceCompletenessProven",
                        "contentAccepted",
                        "presentationPoliciesQualified",
                        "entityQualified",
                        "mainResumeAllowed",
                        "productionAuthorized",
                        "sourceQueryAuthorized",
                        "floatArithmeticUsed",
                        "epsilonUsed",
                        "deduplicationPerformed")) {
            assertTrue(result.path(flag).isBoolean(), flag);
            assertFalse(result.path(flag).booleanValue(), flag);
        }
    }

    @Test
    void changesInColumn37CannotDisappearBehindA31ColumnPrefix() throws Exception {
        final var fixture = fixture(2);
        fixture.nativeRows.get(0).put(37, CotacoesContentFixture.number("0.2"));
        final var result = fixture.compare();
        assertEquals(36, trueCount(result.path("comparison").path("columnsEqual")));
        assertEquals(10, trueCount(result.path("comparison").path("totalsEqual")));
        assertFalse(result.path("comparison").path("columnsEqual").get(36).booleanValue());
        assertFalse(result.path("comparison").path("fullTuplesEqual").booleanValue());
    }

    @Test
    void swapsKeepColumnBagsAndTotalsEqualButBreakAssociationsForBothIds() throws Exception {
        final var fixture = fixture(2);
        fixture.records.get(0).put("qoe_qes_other_fees", "0.2");
        fixture.nativeRows.get(1).put(37, CotacoesContentFixture.number("0.2"));
        final var result = fixture.compare();
        assertEquals(37, trueCount(result.path("comparison").path("columnsEqual")));
        assertEquals(11, trueCount(result.path("comparison").path("totalsEqual")));
        assertEquals(2, result.path("comparison").path("changedIds").intValue());
        assertEquals(2, result.path("comparison").path("changedIdsByColumn").get(36).intValue());
        assertFalse(result.path("comparison").path("fullTuplesEqual").booleanValue());
    }

    @Test
    void repeatedRootsRemainOccurrencesAndMissingOneChangesMultiplicity() throws Exception {
        final var fixture = fixture(1);
        fixture.records.add(fixture.records.get(0).deepCopy());
        fixture.nativeRows.add(new LinkedHashMap<>(fixture.nativeRows.get(0)));
        assertTrue(fixture.compare().path("contentEqualUnderCandidatePresentation").booleanValue());
        fixture.nativeRows.remove(1);
        final var result = fixture.compare();
        assertEquals(2, result.path("sourceRows").intValue());
        assertEquals(1, result.path("comparison").path("apiDistinctIds").intValue());
        assertEquals(1, result.path("comparison").path("apiOnlyOccurrences").intValue());
        assertEquals(1, result.path("comparison").path("changedMultiplicityIds").intValue());
    }

    @Test
    void strictNullAbsentAndEmptyRemainDifferentWithoutInventingZero() throws Exception {
        final var fixture = fixture(1);
        fixture.records.get(0).putNull("qoe_qes_other_fees");
        fixture.nativeRows.get(0).remove(37);
        final var nulled = fixture.compare();
        assertFalse(nulled.path("comparison").path("columnsEqual").get(36).booleanValue());
        assertFalse(nulled.path("comparison").path("totalsReady").get(10).booleanValue());
        fixture.records.get(0).put("qoe_qes_disapprove_comments", "");
        fixture.nativeRows.get(0).remove(29);
        assertFalse(
                fixture.compare().path("comparison").path("columnsEqual").get(28).booleanValue());
    }

    @Test
    void explicitFieldNullAbsencePolicyChangesOnlyPresentationAndNeverQualifiesAllAbsentTotal()
            throws Exception {
        final var fixture = fixture(1);
        fixture.records.get(0).putNull("qoe_qes_other_fees");
        fixture.nativeRows.get(0).remove(37);
        final var strict = fixture.compare();
        fixture.field(37).put("nullPresentation", "NULL_NATIVE_ABSENT");
        final var scoped = fixture.compare();
        assertTrue(scoped.path("comparison").path("columnsEqual").get(36).booleanValue());
        assertFalse(scoped.path("comparison").path("totalsReady").get(10).booleanValue());
        assertFalse(scoped.path("comparison").path("totalsEqual").get(10).booleanValue());
        assertEquals(
                strict.path("originalValueProjection"), scoped.path("originalValueProjection"));
        fixture.nativeRows.get(0).put(37, new CotacoesContentFixture.Cell(null, null, 0));
        assertFalse(
                fixture.compare().path("comparison").path("columnsEqual").get(36).booleanValue());
    }

    @Test
    void explicitEmptyAbsencePolicyDoesNotSilentlyIncludeNullOrBlank() throws Exception {
        final var fixture = fixture(1);
        fixture.field(29).put("emptyStringPresentation", "EMPTY_NATIVE_ABSENT");
        fixture.records.get(0).put("qoe_qes_disapprove_comments", "");
        fixture.nativeRows.get(0).remove(29);
        final var empty = fixture.compare();
        assertTrue(empty.path("contentEqualUnderCandidatePresentation").booleanValue());
        fixture.records.get(0).putNull("qoe_qes_disapprove_comments");
        final var nulled = fixture.compare();
        assertFalse(nulled.path("comparison").path("columnsEqual").get(28).booleanValue());
        assertNotEquals(
                bag(empty, "originalValueProjection", "api", 28),
                bag(nulled, "originalValueProjection", "api", 28));
    }

    @Test
    void dateAndTimestampNullBlankPoliciesRequireTheirExactObservedCarriers() throws Exception {
        for (final int column : new int[] {17, 18}) {
            final var fixture = fixture(1);
            final boolean date = column == 18;
            final String name = fixture.field(column).path("sourceField").asText();
            fixture.records.get(0).putNull(name);
            fixture.nativeRows
                    .get(0)
                    .put(column, new CotacoesContentFixture.Cell(null, null, date ? 3 : 2));
            final var strict = fixture.compare();
            assertFalse(
                    strict.path("comparison").path("columnsEqual").get(column - 1).booleanValue());
            fixture.field(column)
                    .put(
                            "nullPresentation",
                            date ? "DATE_NULL_NATIVE_BLANK" : "TIMESTAMP_NULL_NATIVE_BLANK")
                    .put(
                            "nativeNullCarrier",
                            date
                                    ? CotacoesContentAdapter.DATE_CARRIER
                                    : CotacoesContentAdapter.TIMESTAMP_CARRIER);
            final var scoped = fixture.compare();
            assertTrue(scoped.path("contentEqualUnderCandidatePresentation").booleanValue());
            assertEquals(
                    strict.path("originalValueProjection"), scoped.path("originalValueProjection"));
            fixture.nativeRows.get(0).remove(column);
            assertFalse(
                    fixture.compare()
                            .path("comparison")
                            .path("columnsEqual")
                            .get(column - 1)
                            .booleanValue());
            fixture.nativeRows.get(0).put(column, new CotacoesContentFixture.Cell(null, null, 0));
            assertThrows(IllegalArgumentException.class, fixture::compare);
        }
    }

    @Test
    void unknownAndForeignPresentationPoliciesCannotApplyToScopeOrOtherKinds() throws Exception {
        final var fixture = fixture(1);
        for (final String policy :
                List.of(
                        "NULL_IS_ZERO",
                        "SCOPED_NULL_NATIVE_OMISSION_OR_BLANK",
                        "DATE_NULL_NATIVE_BLANK")) {
            final var catalog = fixture.catalog.deepCopy();
            ((ObjectNode) catalog.path("columns").get(0)).put("nullPresentation", policy);
            assertThrows(
                    IllegalArgumentException.class,
                    () -> CotacoesContentAdapter.verifyCatalog(catalog));
        }
        for (final int column : new int[] {2, 3}) {
            final var catalog = fixture.catalog.deepCopy();
            ((ObjectNode) catalog.path("columns").get(column - 1))
                    .put("nullPresentation", "NULL_NATIVE_ABSENT");
            assertThrows(
                    IllegalArgumentException.class,
                    () -> CotacoesContentAdapter.verifyCatalog(catalog));
        }
    }

    @Test
    void catalogCoordinateFieldTitleKindAndTotalsCannotBeRebound() throws Exception {
        final var original = fixture(1).catalog;
        for (final String property :
                List.of("sourceField", "title", "kind", "nativeColumn", "total")) {
            final var catalog = original.deepCopy();
            final var field = (ObjectNode) catalog.path("columns").get(36);
            if ("nativeColumn".equals(property)) {
                field.put(property, 36);
            } else if ("total".equals(property)) {
                field.put(property, false);
            } else {
                field.put(property, "foreign");
            }
            assertThrows(
                    IllegalArgumentException.class,
                    () -> CotacoesContentAdapter.verifyCatalog(catalog));
        }
    }

    @Test
    void enumLabelsComeOnlyFromCatalogOnEachSideAndMissingLabelsRefuse() throws Exception {
        final var fixture = fixture(1);
        fixture.records.get(0).put("qoe_qes_status", "unobserved");
        assertEquals(
                "COTACOES_SOURCE_ENUM_UNOBSERVED",
                assertThrows(IllegalArgumentException.class, fixture::compare).getMessage());
        fixture.records.get(0).put("qoe_qes_status", "authored_pending");
        fixture.nativeRows.get(0).put(16, CotacoesContentFixture.text("Unobserved label"));
        assertEquals(
                "COTACOES_NATIVE_ENUM_UNOBSERVED",
                assertThrows(IllegalArgumentException.class, fixture::compare).getMessage());
        fixture.catalog.remove("enumLabels");
        assertEquals(
                "COTACOES_SOURCE_ENUM_UNOBSERVED",
                assertThrows(IllegalArgumentException.class, fixture::compare).getMessage());
    }

    @Test
    void numericDecimalRequiresNumberWhileDecimalStringRequiresExactString() throws Exception {
        final var fixture = fixture(1);
        fixture.records.get(0).put("qoe_qes_invoices_volumes", "0.1");
        assertEquals(
                "COTACOES_SOURCE_NUMERIC_DECIMAL_TYPE",
                assertThrows(IllegalArgumentException.class, fixture::compare).getMessage());
        fixture.records.get(0).put("qoe_qes_invoices_volumes", new java.math.BigDecimal("0.1"));
        fixture.records.get(0).put("qoe_qes_other_fees", new java.math.BigDecimal("0.1"));
        assertEquals(
                "COTACOES_SOURCE_DECIMAL_STRING_TYPE",
                assertThrows(IllegalArgumentException.class, fixture::compare).getMessage());
        for (final String lexeme : List.of(" 0.1", "0.1 ", "1,25", "+1", "NaN", "1e129")) {
            fixture.records.get(0).put("qoe_qes_other_fees", lexeme);
            assertThrows(IllegalArgumentException.class, fixture::compare);
        }
    }

    @Test
    void exactEncodingAllowsSameBinary64ButKeepsRawLexemeAndSignedZeroDifferences()
            throws Exception {
        final var fixture = fixture(1);
        final var baseline = fixture.compare();
        fixture.numericLexeme = "0.10000000000000001";
        final var same = fixture.compare();
        assertTrue(same.path("contentEqualUnderCandidatePresentation").booleanValue());
        assertNotEquals(
                bag(baseline, "originalValueProjection", "api", 11),
                bag(same, "originalValueProjection", "api", 11));
        fixture.nativeRows.get(0).put(12, CotacoesContentFixture.number("0.10000000000000002"));
        assertFalse(
                fixture.compare().path("comparison").path("columnsEqual").get(11).booleanValue());
        fixture.records.get(0).put("qoe_qes_other_fees", "-0");
        fixture.nativeRows.get(0).put(37, CotacoesContentFixture.number("0"));
        assertFalse(
                fixture.compare().path("comparison").path("columnsEqual").get(36).booleanValue());
    }

    @Test
    void numericTokensRetainExponentAndTrailingZeroSpellingInOriginalAtom() throws Exception {
        final String line =
                "{\"entity\":\"COTACOES\",\"templateId\":6906,\"record\":{\"x\":7.2500e+1,\"sequence_code\":1}}";
        final var record = CotacoesContentAdapter.record(line);
        assertEquals("7.2500e+1", CotacoesContentAdapter.original(record.get("x")).value());
        assertEquals("JSON:NUMBER", CotacoesContentAdapter.original(record.get("x")).carrier());
        assertEquals(
                "JSON:INTEGER",
                CotacoesContentAdapter.original(record.get("sequence_code")).carrier());
    }

    @Test
    void wholeSecondTimestampKeyKeepsFractionsAndReportsNativeEncodingIndependently()
            throws Exception {
        final var fixture = fixture(1);
        final var baseline = fixture.compare();
        fixture.records.get(0).put("requested_at", "2026-10-13T12:00:00.123456-03:00");
        final var fractional = fixture.compare();
        assertTrue(fractional.path("contentEqualUnderCandidatePresentation").booleanValue());
        assertNotEquals(
                bag(baseline, "originalValueProjection", "api", 1),
                bag(fractional, "originalValueProjection", "api", 1));
        fixture.nativeRows
                .get(0)
                .put(
                        2,
                        new CotacoesContentFixture.Cell(
                                null, CotacoesContentFixture.serial("0.5000000001"), 2));
        final var nativeFractional = fixture.compare();
        assertTrue(nativeFractional.path("contentEqualUnderCandidatePresentation").booleanValue());
        assertEquals(
                1,
                nativeFractional
                        .path("nativeTimestampNoncanonicalWholeSecondEncodings")
                        .intValue());
        assertNotEquals(
                bag(fractional, "originalValueProjection", "ui", 1),
                bag(nativeFractional, "originalValueProjection", "ui", 1));
    }

    @Test
    void dateIsExactCivilIsoAndIntegral1900SerialWithoutDay60OrTimestampTruncation()
            throws Exception {
        final var fixture = fixture(1);
        for (final String value :
                List.of("2026-02-30", " 2026-10-13", "2026-10-13 ", "2026-10-13T00:00:00-03:00")) {
            fixture.records.get(0).put("qoe_qes_fit_nse_issued_at", value);
            assertThrows(IllegalArgumentException.class, fixture::compare);
        }
        fixture.records.get(0).put("qoe_qes_fit_nse_issued_at", "2026-10-13");
        for (final String value : List.of("60", "0", "46308.5", "2958466")) {
            fixture.nativeRows.get(0).put(18, new CotacoesContentFixture.Cell(null, value, 3));
            assertThrows(IllegalArgumentException.class, fixture::compare);
        }
    }

    @Test
    void requestedAtMustBeWithinPeriodIndependentlyForSourceAndNativeColumn2() throws Exception {
        final var fixture = fixture(1);
        fixture.records.get(0).put("requested_at", "2026-10-19T00:00:00-03:00");
        assertEquals(
                "COTACOES_REQUESTED_AT_OUTSIDE_PERIOD",
                assertThrows(IllegalArgumentException.class, fixture::compare).getMessage());
        fixture.records.get(0).put("requested_at", "2026-10-13T12:00:00-03:00");
        fixture.nativeRows
                .get(0)
                .put(
                        2,
                        new CotacoesContentFixture.Cell(
                                null, CotacoesContentFixture.serial("6"), 2));
        assertEquals(
                "COTACOES_REQUESTED_AT_OUTSIDE_PERIOD",
                assertThrows(IllegalArgumentException.class, fixture::compare).getMessage());
        fixture.records.get(0).put("requested_at", "2026-10-13T15:00:00Z");
        assertThrows(IllegalArgumentException.class, fixture::compare);
    }

    @Test
    void exactCarriersAndEpochPreventTextNumericAndDateStyleAliases() throws Exception {
        final var fixture = fixture(1);
        fixture.epoch = "1";
        assertEquals(
                "COTACOES_QUALIFIED_EPOCH_1900_ONLY",
                assertThrows(IllegalArgumentException.class, fixture::compare).getMessage());
        fixture.epoch = "0";
        fixture.nativeRows.get(0).put(37, CotacoesContentFixture.text("0.1"));
        assertThrows(IllegalArgumentException.class, fixture::compare);
        fixture.nativeRows.get(0).put(37, new CotacoesContentFixture.Cell(null, "0.1", 2));
        assertEquals(
                "COTACOES_NATIVE_EXACT_CARRIER",
                assertThrows(IllegalArgumentException.class, fixture::compare).getMessage());
        fixture.nativeRows.get(0).put(37, CotacoesContentFixture.number("0.1"));
        fixture.timestampFormat = "dd/mm/yyyy hh:mm:ss";
        assertThrows(IllegalArgumentException.class, fixture::compare);
    }

    @Test
    void sequenceCodeDoesNotAliasTextDecimalZeroOrOverflowToPositiveLong() throws Exception {
        for (final String value : List.of("\"1\"", "1.0", "0", "-1", "9223372036854775808")) {
            final var fixture = fixture(1);
            fixture.sequenceCodeLexeme = value;
            assertTrue(
                    new String(fixture.sourceBytes(), StandardCharsets.UTF_8)
                            .contains("\"sequence_code\":" + value));
            assertThrows(IllegalArgumentException.class, fixture::compare);
        }
        final var fixture = fixture(1);
        fixture.nativeRows.get(0).put(3, CotacoesContentFixture.number("1.5"));
        assertThrows(ArithmeticException.class, fixture::compare);
    }

    @Test
    void wrongEnvelopeMissingFieldDuplicateMemberTrailingJsonAndMissingFinalLfRefuse()
            throws Exception {
        final var fixture = fixture(1);
        fixture.entity = "MANIFESTOS";
        assertThrows(IllegalArgumentException.class, fixture::compare);
        fixture.entity = "COTACOES";
        fixture.template = ExportValidationSupport.JSON.getNodeFactory().numberNode(6399);
        assertThrows(IllegalArgumentException.class, fixture::compare);
        fixture.template = ExportValidationSupport.JSON.getNodeFactory().numberNode(6906);
        fixture.finalLf = false;
        assertEquals(
                "COTACOES_COMPLETE_JSONL_FINAL_LF",
                assertThrows(IllegalArgumentException.class, fixture::compare).getMessage());
        fixture.finalLf = true;
        fixture.sourceSuffix = "{}\n";
        assertThrows(IllegalArgumentException.class, fixture::compare);
        fixture.sourceSuffix = "";
        fixture.records.get(0).remove("qoe_qes_other_fees");
        assertEquals(
                "COTACOES_REQUIRED_SOURCE_FIELD",
                assertThrows(IllegalArgumentException.class, fixture::compare).getMessage());
        final String duplicate =
                "{\"entity\":\"COTACOES\",\"templateId\":6906,\"record\":{\"x\":1,\"x\":2}}";
        assertThrows(Exception.class, () -> CotacoesContentAdapter.record(duplicate));
        final String extra = "{\"entity\":\"COTACOES\",\"templateId\":6906,\"record\":{}} {}";
        assertThrows(IllegalArgumentException.class, () -> CotacoesContentAdapter.record(extra));
    }

    @Test
    void rawTextWhitespaceAndUnicodeNeverUseMapperTrimOrNfc() throws Exception {
        final var fixture = fixture(1);
        fixture.records.get(0).put("qoe_uer_name", "  Jose\u0301  ");
        fixture.nativeRows.get(0).put(19, CotacoesContentFixture.text("José"));
        final var result = fixture.compare();
        assertFalse(result.path("comparison").path("columnsEqual").get(18).booleanValue());
        fixture.nativeRows.get(0).put(19, CotacoesContentFixture.text("  Jose\u0301  "));
        assertTrue(fixture.compare().path("contentEqualUnderCandidatePresentation").booleanValue());
    }

    @Test
    void pinsAreCheckedBeforeAndAfterAndTruncatedWorkbookCannotYieldPartialEquality()
            throws Exception {
        final var fixture = fixture(1);
        final var pins = fixture.pins();
        assertEquals(
                "EXPORT_PIN_CHANGED",
                assertThrows(
                                IllegalArgumentException.class,
                                () ->
                                        CotacoesContentAdapter.compareWithCatalog(
                                                fixture.request,
                                                pins.source(),
                                                pins.workbook(),
                                                CotacoesContentFixture.budget(),
                                                fixture.catalog,
                                                () -> {
                                                    try {
                                                        Files.writeString(
                                                                pins.source().path(),
                                                                "tampered",
                                                                StandardCharsets.UTF_8);
                                                    } catch (final java.io.IOException invalid) {
                                                        throw new UncheckedIOException(invalid);
                                                    }
                                                }))
                        .getMessage());
        final byte[] book = fixture.workbookBytes();
        final var truncated = fixture.pin("truncated.xlsx", Arrays.copyOf(book, book.length - 1));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        CotacoesContentAdapter.compareWithCatalog(
                                fixture.request,
                                fixture.pin("source.jsonl", fixture.sourceBytes()),
                                truncated,
                                CotacoesContentFixture.budget(),
                                fixture.catalog,
                                () -> {}));
    }

    private CotacoesContentFixture fixture(final int count) throws Exception {
        return new CotacoesContentFixture(directory, count);
    }

    private static int trueCount(final JsonNode values) {
        int result = 0;
        for (final var value : values) {
            result += value.isBoolean() && value.booleanValue() ? 1 : 0;
        }
        return result;
    }

    private static JsonNode bag(
            final JsonNode result, final String projection, final String side, final int index) {
        return result.path(projection).path(side).path("columnBags").get(index);
    }
}
