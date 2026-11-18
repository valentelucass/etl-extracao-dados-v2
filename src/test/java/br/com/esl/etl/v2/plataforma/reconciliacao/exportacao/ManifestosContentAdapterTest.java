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
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ManifestosContentAdapterTest {
    @TempDir Path directory;

    @Test
    void pinnedCandidateHas91ItemsAndExactly50DeclaredTotalsWithoutQualification()
            throws Exception {
        final var catalog = ManifestosContentAdapter.catalog();
        int totals = 0;
        int decimalTotals = 0;
        int integerTotals = 0;
        for (final var field : catalog.required("columns")) {
            if (field.required("total").booleanValue()) {
                totals++;
                decimalTotals += "decimal".equals(field.path("kind").asText()) ? 1 : 0;
                integerTotals += "integer".equals(field.path("kind").asText()) ? 1 : 0;
            }
        }
        assertEquals(91, catalog.path("columns").size());
        assertEquals(50, totals);
        assertEquals(31, decimalTotals);
        assertEquals(19, integerTotals);
        assertEquals(2, catalog.path("idColumnIndex").intValue());
        assertEquals("sequence_code", catalog.path("columns").get(2).path("sourceField").asText());
        assertEquals(
                "Finalizado", catalog.path("enumLabels").path("status").path("closed").asText());
        assertFalse(catalog.required("presentationQualified").booleanValue());
        assertFalse(catalog.required("contentAccepted").booleanValue());
    }

    @Test
    void comparesEveryValueTupleAssociationMultiplicityAndAll50TotalsForAuthoredPair()
            throws Exception {
        final var result = fixture(2).compare();
        assertTrue(result.path("contentEqualUnderCandidatePresentation").booleanValue());
        assertEquals("CONTENT_EQUAL_UNDER_CANDIDATE_PRESENTATION", result.path("status").asText());
        assertEquals(2, result.path("sourceRows").intValue());
        assertEquals(2, result.path("uiRows").intValue());
        assertEquals(91, result.path("comparison").path("columnsEqual").size());
        assertEquals(50, result.path("comparison").path("totalsEqual").size());
        assertTrue(allTrue(result.path("comparison").path("columnsEqual")));
        assertTrue(allTrue(result.path("comparison").path("totalsReady")));
        assertTrue(allTrue(result.path("comparison").path("totalsEqual")));
        for (final String side : List.of("api", "ui")) {
            assertTrue(
                    result.path("fullValueProjection")
                            .path(side)
                            .path("completeAllValues")
                            .booleanValue());
            assertEquals(
                    91, result.path("fullValueProjection").path(side).path("columnBags").size());
            assertEquals(50, result.path("fullValueProjection").path(side).path("totals").size());
            assertTrue(
                    result.path("originalValueProjection")
                            .path(side)
                            .path("completeAllValues")
                            .booleanValue());
        }
        assertTrue(result.path("pinsVerifiedBeforeAndAfter").booleanValue());
        assertTrue(result.path("allOccurrencesCompared").booleanValue());
        assertTrue(result.path("diagnosticOnly").booleanValue());
        for (final String flag :
                List.of(
                        "contentAccepted",
                        "presentationPoliciesQualified",
                        "snapshotIdentityProven",
                        "temporalCausalityProven",
                        "entityQualified",
                        "mainResumeAllowed",
                        "runtimeAdopted",
                        "globalIdentityProven",
                        "sourceCompletenessProven",
                        "sourceQueryAuthorized",
                        "fileExportQualified",
                        "dashboardConsumptionVerified",
                        "productionAuthorized",
                        "persistenceAuthorized",
                        "serviceDateFilterVerified")) {
            assertTrue(result.path(flag).isBoolean(), flag);
            assertFalse(result.path(flag).booleanValue(), flag);
        }
    }

    @Test
    void twoMobileValueChangesRemainTwoDifferentOccurrencesAndOnly90ColumnsEqual()
            throws Exception {
        final var fixture = fixture(2);
        fixture.records.forEach(record -> record.put("mft_fis_fit_mey_mobile_receipt", true));
        final var result = fixture.compare();
        assertEquals(
                "PENDING_DIFFERENCE_INVESTIGATION_AND_NEW_PAIR", result.path("status").asText());
        assertFalse(result.path("contentEqualUnderCandidatePresentation").booleanValue());
        assertEquals(90, trueCount(result.path("comparison").path("columnsEqual")));
        assertEquals(2, result.path("comparison").path("apiOnlyOccurrences").intValue());
        assertEquals(2, result.path("comparison").path("uiOnlyOccurrences").intValue());
        assertEquals(2, result.path("comparison").path("changedIdsByColumn").get(90).intValue());
        assertFalse(result.path("temporalCausalityProven").booleanValue());
    }

    @Test
    void highColumn64ValueChangeIsVisibleInBothItsBagAndItsTotal() throws Exception {
        final var fixture = fixture(2);
        fixture.nativeRows.get(0).put(64, ManifestosContentFixture.number("0.2"));
        final var result = fixture.compare();
        assertFalse(result.path("comparison").path("columnsEqual").get(63).booleanValue());
        assertEquals(90, trueCount(result.path("comparison").path("columnsEqual")));
        assertEquals(49, trueCount(result.path("comparison").path("totalsEqual")));
        assertFalse(result.path("comparison").path("fullTuplesEqual").booleanValue());
    }

    @Test
    void swapsAt64And91KeepGlobalBagsAndTotalsEqualButBreakTypedIdAssociations() throws Exception {
        for (final int column : new int[] {64, 91}) {
            final var fixture = fixture(2);
            if (column == 64) {
                fixture.records.get(1).put("fleet_costs_subtotal", "0.2");
                fixture.nativeRows.get(0).put(64, ManifestosContentFixture.number("0.2"));
            } else {
                fixture.records.get(0).put("mft_fis_fit_mey_mobile_receipt", true);
                fixture.nativeRows.get(1).put(91, ManifestosContentFixture.text("Sim"));
            }
            final var result = fixture.compare();
            assertTrue(allTrue(result.path("comparison").path("columnsEqual")));
            assertTrue(allTrue(result.path("comparison").path("totalsEqual")));
            assertFalse(result.path("comparison").path("fullTuplesEqual").booleanValue());
            assertEquals(2, result.path("comparison").path("changedIds").intValue());
            assertEquals(2, result.path("comparison").path("apiOnlyOccurrences").intValue());
            assertEquals(
                    2,
                    result.path("comparison")
                            .path("changedIdsByColumn")
                            .get(column - 1)
                            .intValue());
        }
    }

    @Test
    void duplicateOccurrencesAreRetainedAndMissingOneChangesMultiplicity() throws Exception {
        final var fixture = fixture(1);
        fixture.records.add(fixture.records.get(0).deepCopy());
        fixture.nativeRows.add(new LinkedHashMap<>(fixture.nativeRows.get(0)));
        final var equal = fixture.compare();
        assertTrue(equal.path("contentEqualUnderCandidatePresentation").booleanValue());
        assertEquals(2, equal.path("comparison").path("apiRows").intValue());
        assertEquals(1, equal.path("comparison").path("apiDistinctIds").intValue());
        assertFalse(equal.path("deduplicationPerformed").booleanValue());
        fixture.nativeRows.remove(1);
        final var changed = fixture.compare();
        assertFalse(changed.path("contentEqualUnderCandidatePresentation").booleanValue());
        assertEquals(1, changed.path("comparison").path("changedMultiplicityIds").intValue());
        assertEquals(1, changed.path("comparison").path("apiOnlyOccurrences").intValue());
    }

    @Test
    void mobileNullAndFalseShareOnlyTheVisualLabelWhileRawProjectionKeepsTheirDifference()
            throws Exception {
        final var fixture = fixture(1);
        final var falseResult = fixture.compare();
        fixture.records.get(0).putNull("mft_fis_fit_mey_mobile_receipt");
        final var nullResult = fixture.compare();
        assertTrue(nullResult.path("contentEqualUnderCandidatePresentation").booleanValue());
        assertEquals(
                bag(falseResult, "fullValueProjection", "api", 90),
                bag(nullResult, "fullValueProjection", "api", 90));
        assertNotEquals(
                bag(falseResult, "originalValueProjection", "api", 90),
                bag(nullResult, "originalValueProjection", "api", 90));
        assertEquals(
                bag(falseResult, "originalValueProjection", "ui", 90),
                bag(nullResult, "originalValueProjection", "ui", 90));
    }

    @Test
    void emptyCommentOmissionIsScopedAndDoesNotEraseOriginalEmptyVersusNull() throws Exception {
        final var fixture = fixture(1);
        fixture.records.get(0).put("mft_aoe_comments", "");
        fixture.nativeRows.get(0).remove(88);
        final var empty = fixture.compare();
        assertTrue(empty.path("contentEqualUnderCandidatePresentation").booleanValue());
        fixture.records.get(0).putNull("mft_aoe_comments");
        final var nulled = fixture.compare();
        assertTrue(nulled.path("contentEqualUnderCandidatePresentation").booleanValue());
        assertNotEquals(
                bag(empty, "originalValueProjection", "api", 87),
                bag(nulled, "originalValueProjection", "api", 87));
        fixture.records.get(0).put("mft_man_name", "");
        fixture.nativeRows.get(0).remove(4);
        assertEquals(
                "MANIFESTOS_NATIVE_OMISSION_NOT_QUALIFIED",
                assertThrows(IllegalArgumentException.class, fixture::compare).getMessage());
    }

    @Test
    void scopedNullOmissionAndBlankStaySeparateInNativeRawPresence() throws Exception {
        final var fixture = fixture(1);
        fixture.records.get(0).putNull("mft_s_n_svs_sge_pyr_nickname");
        fixture.nativeRows.get(0).remove(5);
        final var absent = fixture.compare();
        fixture.nativeRows.get(0).put(5, new ManifestosContentFixture.Cell(null, null, 0));
        final var blank = fixture.compare();
        assertTrue(absent.path("contentEqualUnderCandidatePresentation").booleanValue());
        assertTrue(blank.path("contentEqualUnderCandidatePresentation").booleanValue());
        assertNotEquals(
                bag(absent, "originalValueProjection", "ui", 4),
                bag(blank, "originalValueProjection", "ui", 4));
        fixture.records.get(0).remove("mft_s_n_svs_sge_pyr_nickname");
        assertEquals(
                "MANIFESTOS_REQUIRED_SOURCE_FIELD",
                assertThrows(IllegalArgumentException.class, fixture::compare).getMessage());
    }

    @Test
    void arrivalAndClosingHaveDistinctCorrectFieldsAndCannotBeInterchanged() throws Exception {
        final var fixture = fixture(1);
        assertEquals(
                "closed_at", fixture.catalog.path("columns").get(11).path("sourceField").asText());
        assertEquals(
                "finished_at",
                fixture.catalog.path("columns").get(12).path("sourceField").asText());
        assertNotEquals(
                fixture.records.get(0).get("closed_at"), fixture.records.get(0).get("finished_at"));
        assertTrue(fixture.compare().path("contentEqualUnderCandidatePresentation").booleanValue());
        final var arrival = fixture.nativeRows.get(0).get(12);
        fixture.nativeRows.get(0).put(12, fixture.nativeRows.get(0).get(13));
        fixture.nativeRows.get(0).put(13, arrival);
        final var changed = fixture.compare();
        assertFalse(changed.path("comparison").path("columnsEqual").get(11).booleanValue());
        assertFalse(changed.path("comparison").path("columnsEqual").get(12).booleanValue());
        assertEquals(89, trueCount(changed.path("comparison").path("columnsEqual")));
    }

    @Test
    void unknownSourceEnumAndUnknownNativeLabelAreRefused() throws Exception {
        final var source = fixture(1);
        source.records.get(0).put("status", "unobserved");
        assertEquals(
                "MANIFESTOS_SOURCE_ENUM_UNOBSERVED",
                assertThrows(IllegalArgumentException.class, source::compare).getMessage());
        final var nativeBook = fixture(1);
        nativeBook.nativeRows.get(0).put(19, ManifestosContentFixture.text("Finalizada"));
        assertEquals(
                "MANIFESTOS_NATIVE_ENUM_UNOBSERVED",
                assertThrows(IllegalArgumentException.class, nativeBook::compare).getMessage());
    }

    @Test
    void decimalEqualityUsesExactEncodingAndNeverAnEpsilonOrDisplayRounding() throws Exception {
        final var fixture = fixture(1);
        final var baseline = fixture.compare();
        fixture.records.get(0).put("fleet_costs_subtotal", "0.10000000000000001");
        final var sameEncoding = fixture.compare();
        assertTrue(sameEncoding.path("contentEqualUnderCandidatePresentation").booleanValue());
        assertNotEquals(
                bag(baseline, "originalValueProjection", "api", 63),
                bag(sameEncoding, "originalValueProjection", "api", 63));
        fixture.nativeRows.get(0).put(64, ManifestosContentFixture.number("0.10000000000000002"));
        assertFalse(
                fixture.compare().path("comparison").path("columnsEqual").get(63).booleanValue());
        fixture.records.get(0).put("fleet_costs_subtotal", "-0");
        fixture.nativeRows.get(0).put(64, ManifestosContentFixture.number("0"));
        assertFalse(
                fixture.compare().path("comparison").path("columnsEqual").get(63).booleanValue());
    }

    @Test
    void explicitWholeSecondKeyPreservesSourceFractionsAndReportsNoncanonicalNativeEncoding()
            throws Exception {
        final var fixture = fixture(1);
        final var original = fixture.compare();
        fixture.records.get(0).put("created_at", "2024-02-01T12:00:00.123456-03:00");
        final var fractions = fixture.compare();
        assertTrue(fractions.path("contentEqualUnderCandidatePresentation").booleanValue());
        assertNotEquals(
                bag(original, "originalValueProjection", "api", 1),
                bag(fractions, "originalValueProjection", "api", 1));
        fixture.nativeRows
                .get(0)
                .put(
                        2,
                        new ManifestosContentFixture.Cell(
                                null, ManifestosContentFixture.serial("0.5000000001"), 1));
        final var nativeFraction = fixture.compare();
        assertTrue(nativeFraction.path("contentEqualUnderCandidatePresentation").booleanValue());
        assertEquals(
                1,
                nativeFraction
                        .required("nativeTimestampNoncanonicalWholeSecondEncodings")
                        .intValue());
        assertFalse(
                nativeFraction
                        .path("nativeTimestampEncodingEqualityRequiredForVisualKey")
                        .booleanValue());
        assertFalse(nativeFraction.path("contentAccepted").booleanValue());
        assertNotEquals(
                bag(fractions, "originalValueProjection", "ui", 1),
                bag(nativeFraction, "originalValueProjection", "ui", 1));
    }

    @Test
    void aChangedDisplayedSecondRemainsADifferenceWithOriginalEncodingPreserved() throws Exception {
        final var fixture = fixture(1);
        fixture.nativeRows
                .get(0)
                .put(
                        2,
                        new ManifestosContentFixture.Cell(
                                null, ManifestosContentFixture.serial("0.50001"), 1));
        final var diagnostic = fixture.compare();
        assertFalse(diagnostic.path("contentEqualUnderCandidatePresentation").booleanValue());
        assertFalse(diagnostic.path("comparison").path("columnsEqual").get(1).booleanValue());
        assertEquals(1, diagnostic.path("comparison").path("changedIds").intValue());
        assertTrue(diagnostic.path("originalTimestampEncodingRetained").booleanValue());
        assertFalse(diagnostic.path("contentAccepted").booleanValue());
    }

    @Test
    void timestampGeneralWrong164FormatAnd1904EpochAreNotQualified() throws Exception {
        final var general = fixture(1);
        general.nativeRows
                .get(0)
                .put(2, ManifestosContentFixture.number(ManifestosContentFixture.serial("0.5")));
        assertEquals(
                "MANIFESTOS_NATIVE_EXACT_CARRIER",
                assertThrows(IllegalArgumentException.class, general::compare).getMessage());
        final var format = fixture(1);
        format.timestampFormat = "DD/MM/YYYY";
        assertEquals(
                "MANIFESTOS_NATIVE_EXACT_CARRIER",
                assertThrows(IllegalArgumentException.class, format::compare).getMessage());
        final var epoch = fixture(1);
        epoch.epoch = "1";
        assertEquals(
                "MANIFESTOS_QUALIFIED_EPOCH_1900_ONLY",
                assertThrows(IllegalArgumentException.class, epoch::compare).getMessage());
    }

    @Test
    void sourceTypesMissingFieldsAndUnscopedNullsAreRefusedWithoutSkippingRows() throws Exception {
        for (final String field :
                List.of("sequence_code", "manual_km", "created_at", "mft_crn_psn_nickname")) {
            final var fixture = fixture(1);
            fixture.records.get(0).putNull(field);
            assertThrows(IllegalArgumentException.class, fixture::compare);
        }
        final var missing = fixture(1);
        missing.records.get(0).remove("mft_fis_fit_mey_mobile_receipt");
        assertEquals(
                "MANIFESTOS_REQUIRED_SOURCE_FIELD",
                assertThrows(IllegalArgumentException.class, missing::compare).getMessage());
        final var integer = fixture(1);
        integer.records.get(0).put("sequence_code", "1");
        assertEquals(
                "MANIFESTOS_SOURCE_INTEGER_TYPE",
                assertThrows(IllegalArgumentException.class, integer::compare).getMessage());
        integer.records.get(0).put("sequence_code", new BigDecimal("1.5"));
        assertThrows(ArithmeticException.class, integer::compare);
        final var decimal = fixture(1);
        decimal.records.get(0).put("fleet_costs_subtotal", new BigDecimal("0.1"));
        assertEquals(
                "MANIFESTOS_SOURCE_DECIMAL_STRING_TYPE",
                assertThrows(IllegalArgumentException.class, decimal::compare).getMessage());
    }

    @Test
    void arraysPreserveOrderedRepeatedStringsAndRejectUnqualifiedChildrenOrNesting()
            throws Exception {
        for (final String presentation : List.of("Alpha: Beta", "Beta: Alpha: Alpha")) {
            final var fixture = fixture(1);
            fixture.nativeRows.get(0).put(75, ManifestosContentFixture.text(presentation));
            assertFalse(
                    fixture.compare()
                            .path("comparison")
                            .path("columnsEqual")
                            .get(74)
                            .booleanValue());
        }
        for (final String array : List.of("[1]", "[\"flat\"]", "[[1]]", "[[[\"deep\"]]]")) {
            final var fixture = fixture(1);
            fixture.records
                    .get(0)
                    .set(
                            "mft_mte_unloading_recipient_names",
                            ExportValidationSupport.JSON.readTree(array));
            assertThrows(IllegalArgumentException.class, fixture::compare);
        }
    }

    @Test
    void emptyNestedArrayOmissionDoesNotEraseOriginalArrayStructure() throws Exception {
        final var fixture = fixture(1);
        fixture.records
                .get(0)
                .set(
                        "mft_mte_unloading_recipient_names",
                        ExportValidationSupport.JSON.readTree("[[]]"));
        fixture.nativeRows.get(0).remove(75);
        final var nested = fixture.compare();
        assertTrue(nested.path("contentEqualUnderCandidatePresentation").booleanValue());
        fixture.records.get(0).putArray("mft_mte_unloading_recipient_names");
        final var empty = fixture.compare();
        assertTrue(empty.path("contentEqualUnderCandidatePresentation").booleanValue());
        assertNotEquals(
                bag(nested, "originalValueProjection", "api", 74),
                bag(empty, "originalValueProjection", "api", 74));
    }

    @Test
    void envelopeIdentityDuplicateJsonAndIncompleteJsonlAreRefused() throws Exception {
        final var entity = fixture(1);
        entity.entity = "COLETAS";
        assertThrows(IllegalArgumentException.class, entity::compare);
        final var template = fixture(1);
        template.template = ExportValidationSupport.JSON.getNodeFactory().textNode("6399");
        assertThrows(IllegalArgumentException.class, template::compare);
        final var fixture = fixture(1);
        final var pins = fixture.pins();
        final byte[] bytes = fixture.sourceBytes();
        assertEquals(
                "MANIFESTOS_COMPLETE_JSONL_FINAL_LF",
                assertThrows(
                                IllegalArgumentException.class,
                                () ->
                                        ManifestosContentAdapter.compare(
                                                fixture.request,
                                                fixture.pin(
                                                        "truncated.jsonl",
                                                        Arrays.copyOf(bytes, bytes.length - 1)),
                                                pins.workbook(),
                                                ManifestosContentFixture.budget()))
                        .getMessage());
        final String json = new String(bytes, StandardCharsets.UTF_8);
        final String duplicate =
                json.replace("\"sequence_code\":1", "\"sequence_code\":1,\"sequence_code\":1");
        assertNotEquals(json, duplicate);
        assertThrows(
                IOException.class,
                () ->
                        ManifestosContentAdapter.compare(
                                fixture.request,
                                fixture.pin(
                                        "duplicate.jsonl",
                                        duplicate.getBytes(StandardCharsets.UTF_8)),
                                pins.workbook(),
                                ManifestosContentFixture.budget()));
    }

    @Test
    void sourceAndWorkbookPinsAreCheckedBeforeReadingAndAgainBeforeReturning() throws Exception {
        for (final boolean source : new boolean[] {true, false}) {
            final var fixture = fixture(1);
            final var pins = fixture.pins();
            final var selected = source ? pins.source() : pins.workbook();
            final String digest = selected.sha256();
            final String wrong = (digest.startsWith("0") ? "1" : "0") + digest.substring(1);
            final var changed =
                    new ExportValidationSupport.Pin(selected.path(), wrong, selected.bytes());
            assertEquals(
                    "EXPORT_PIN_CHANGED",
                    assertThrows(
                                    IllegalArgumentException.class,
                                    () ->
                                            ManifestosContentAdapter.compare(
                                                    fixture.request,
                                                    source ? changed : pins.source(),
                                                    source ? pins.workbook() : changed,
                                                    ManifestosContentFixture.budget()))
                            .getMessage());
            assertEquals(
                    "EXPORT_PIN_CHANGED",
                    assertThrows(
                                    IllegalArgumentException.class,
                                    () ->
                                            ManifestosContentAdapter.compareWithPostcheckProbe(
                                                    fixture.request,
                                                    pins.source(),
                                                    pins.workbook(),
                                                    ManifestosContentFixture.budget(),
                                                    () -> {
                                                        try {
                                                            Files.write(
                                                                    selected.path(),
                                                                    new byte[] {0});
                                                        } catch (final IOException error) {
                                                            throw new UncheckedIOException(error);
                                                        }
                                                    }))
                            .getMessage());
        }
    }

    @Test
    void requestCannotInjectPolicyAndCreationDateDoesNotProveServiceDateFilter() throws Exception {
        final var fixture = fixture(1);
        fixture.request.put("nullPolicy", "ANY_NULL_IS_FALSE");
        assertThrows(IllegalArgumentException.class, fixture::compare);
        fixture.request.remove("nullPolicy");
        fixture.request.put("periodStart", "2024-03-01").put("periodEnd", "2024-03-01");
        ((com.fasterxml.jackson.databind.node.ObjectNode) fixture.request.required("filters"))
                .put(ManifestosIndependentRequest.SERVICE_DATE_FILTER, "2024-03-01 - 2024-03-01");
        assertFalse(fixture.compare().path("serviceDateFilterVerified").booleanValue());
    }

    @Test
    void rawNumericLexemeSurvivesAnIntegralPresentationKey() throws Exception {
        final var fixture = fixture(1);
        final var baseline = fixture.compare();
        final var pins = fixture.pins();
        final String json = new String(fixture.sourceBytes(), StandardCharsets.UTF_8);
        final String integralDecimal = json.replace("\"sequence_code\":1", "\"sequence_code\":1.0");
        assertNotEquals(json, integralDecimal);
        final var result =
                ManifestosContentAdapter.compare(
                        fixture.request,
                        fixture.pin(
                                "lexeme.jsonl", integralDecimal.getBytes(StandardCharsets.UTF_8)),
                        pins.workbook(),
                        ManifestosContentFixture.budget());
        assertTrue(result.path("contentEqualUnderCandidatePresentation").booleanValue());
        assertNotEquals(
                bag(baseline, "originalValueProjection", "api", 2),
                bag(result, "originalValueProjection", "api", 2));
    }

    @Test
    void numericBooleanAndTimestampStyleForDecimalCannotBorrowTheObservedPresentation()
            throws Exception {
        final var bool = fixture(1);
        bool.nativeRows.get(0).put(91, new ManifestosContentFixture.Cell("b", "0", 0));
        assertEquals(
                "MANIFESTOS_NATIVE_LITERAL_TEXT_REQUIRED",
                assertThrows(IllegalArgumentException.class, bool::compare).getMessage());
        final var decimal = fixture(1);
        decimal.nativeRows.get(0).put(64, new ManifestosContentFixture.Cell(null, "0.1", 1));
        assertEquals(
                "MANIFESTOS_NATIVE_EXACT_CARRIER",
                assertThrows(IllegalArgumentException.class, decimal::compare).getMessage());
    }

    @Test
    void invalidUtf8AndUnqualifiedTimestampOffsetAreRefused() throws Exception {
        final var offset = fixture(1);
        offset.records.get(0).put("created_at", "2024-02-01T12:00:00Z");
        assertThrows(IllegalArgumentException.class, offset::compare);
        final var fixture = fixture(1);
        final var pins = fixture.pins();
        final byte[] invalid = fixture.sourceBytes();
        invalid[0] = (byte) 0xff;
        assertThrows(
                IOException.class,
                () ->
                        ManifestosContentAdapter.compare(
                                fixture.request,
                                fixture.pin("invalid-utf8.jsonl", invalid),
                                pins.workbook(),
                                ManifestosContentFixture.budget()));
    }

    @Test
    void pinnedAbsenceAggregatePoliciesAreExclusiveToTheTwoObservedTrailerCapacities()
            throws Exception {
        final var catalog = ManifestosContentAdapter.catalog();
        int policies = 0;
        for (final var field : catalog.required("columns")) {
            if (field.has("emptyAggregatePolicy")) {
                policies++;
                final int column = field.path("nativeColumn").intValue();
                assertTrue(column == 84 || column == 86);
                assertEquals(
                        column == 84 ? "mft_tl1_weight_capacity" : "mft_tl2_weight_capacity",
                        field.path("sourceField").textValue());
                assertEquals(
                        "MANIFESTOS_COLUMN" + column + "_ABSENT_ONLY_BAGS_EQUAL_V1",
                        field.path("emptyAggregatePolicy").textValue());
            }
            if ("enum".equals(field.path("kind").textValue())) {
                assertEquals(
                        field.path("nativeColumn").intValue() == 46
                                ? "SCOPED_EMPTY_NATIVE_OMISSION"
                                : "NONE",
                        field.path("emptyStringPresentation").textValue());
            }
        }
        assertEquals(2, policies);
        assertEquals(
                "Pendente",
                catalog.path("enumLabels").path("mft_cat_cot_status").path("pending").textValue());
        assertEquals("NONE", catalog.path("columns").get(45).path("nullPresentation").textValue());
        assertEquals(
                "SCOPED_NULL_NATIVE_OMISSION_OR_BLANK",
                catalog.path("columns").get(20).path("nullPresentation").textValue());
    }

    @Test
    void authored165NullAbsentPairHas49NumericTotalsAndOneAbsenceComparisonWithoutInventingZero()
            throws Exception {
        final var fixture = absentCapacity(165);
        final String rawSha = ExportValidationSupport.sha256(fixture.sourceBytes());
        final var result = fixture.compare();
        assertTrue(result.path("contentEqualUnderCandidatePresentation").booleanValue());
        assertEquals(165, result.path("sourceRows").intValue());
        assertEquals(165, result.path("uiRows").intValue());
        assertTrue(allTrue(result.path("comparison").path("columnsEqual")));
        assertTrue(result.path("comparison").path("fullTuplesEqual").booleanValue());
        assertEquals(49, trueCount(result.path("comparison").path("totalsEqual")));
        assertEquals(49, trueCount(result.path("comparison").path("totalsReady")));
        assertFalse(result.path("comparison").path("totalsEqual").get(49).booleanValue());
        assertEquals(50, result.path("totalComparisonsEqual").size());
        assertTrue(allTrue(result.path("totalComparisonsEqual")));
        assertEquals(49, result.path("numericTotalsEqual").intValue());
        assertEquals(1, result.path("absenceOnlyTotalsEqual").intValue());
        assertEquals(
                "MANIFESTOS_COLUMN86_ABSENT_ONLY_BAGS_EQUAL_V1",
                result.path("totalComparisonPolicies").get(49).textValue());
        assertFalse(result.path("absenceEqualityIsNumericZero").booleanValue());
        for (final String side : List.of("api", "ui")) {
            final var total = result.path("fullValueProjection").path(side).path("totals").get(49);
            assertEquals(85, total.path("index").intValue());
            assertFalse(total.path("ready").booleanValue());
            assertEquals("0", total.path("exactDecimalNumerator").textValue());
            assertEquals("1", total.path("exactDecimalDenominator").textValue());
            assertEquals("0", total.path("sumOfEncodedCellUnits2PowMinus1074").textValue());
            assertEquals(165, total.path("nonvalueBag").path("occurrences").intValue());
            assertEquals(
                    0, result.path("originalValueProjection").path(side).path("totals").size());
        }
        assertEquals(
                atomBag(new ExactExportTable.Atom("NULL", ""), 165),
                bag(result, "originalValueProjection", "api", 85));
        assertEquals(
                atomBag(new ExactExportTable.Atom("ABSENT", ""), 165),
                bag(result, "originalValueProjection", "ui", 85));
        assertEquals(
                atomBag(new ExactExportTable.Atom("ABSENT", ""), 165),
                bag(result, "fullValueProjection", "api", 85));
        assertTrue(
                fixture.records.stream()
                        .allMatch(record -> record.path("mft_tl2_weight_capacity").isNull()));
        assertEquals(rawSha, ExportValidationSupport.sha256(fixture.sourceBytes()));
        assertEquals(rawSha, result.path("sourceSha256").textValue());
        assertFalse(result.path("contentAccepted").booleanValue());
        assertFalse(result.path("entityQualified").booleanValue());
        assertFalse(result.path("productionAuthorized").booleanValue());
    }

    @Test
    void oneRealCapacityNumberIncludingActualZeroUsesTheUnchangedNumericComparison()
            throws Exception {
        for (final String number : List.of("0", "3.5")) {
            final var fixture = absentCapacity(2);
            fixture.records.get(0).put("mft_tl2_weight_capacity", number);
            fixture.nativeRows.get(0).put(86, ManifestosContentFixture.number(number));
            final var result = fixture.compare();
            assertTrue(result.path("contentEqualUnderCandidatePresentation").booleanValue());
            assertTrue(result.path("comparison").path("totalsReady").get(49).booleanValue());
            assertTrue(result.path("comparison").path("totalsEqual").get(49).booleanValue());
            assertEquals(50, result.path("numericTotalsEqual").intValue());
            assertEquals(0, result.path("absenceOnlyTotalsEqual").intValue());
            assertEquals(
                    "NUMERIC_READY_REQUIRED",
                    result.path("totalComparisonPolicies").get(49).textValue());
        }
    }

    @Test
    void unobservedBlankCapacityDoesNotBorrowAbsentAggregatePolicy() throws Exception {
        final var fixture = absentCapacity(1);
        fixture.nativeRows.get(0).put(86, new ManifestosContentFixture.Cell(null, null, 0));
        final var result = fixture.compare();
        assertFalse(result.path("contentEqualUnderCandidatePresentation").booleanValue());
        assertFalse(result.path("totalComparisonsEqual").get(49).booleanValue());
        assertEquals(0, result.path("absenceOnlyTotalsEqual").intValue());
        assertFalse(result.path("comparison").path("columnsEqual").get(85).booleanValue());
        assertNotEquals(
                bag(result, "originalValueProjection", "ui", 85),
                bag(result, "fullValueProjection", "ui", 85));
    }

    @Test
    void absentBagQueryRejectsTypedNullBlankTextBooleanNumberAndUnobservedCarrier()
            throws Exception {
        for (final var atom :
                List.of(
                        new ExactExportTable.Atom("NULL", ""),
                        new ExactExportTable.Atom("BLANK", ""),
                        new ExactExportTable.Atom("TEXT", ""),
                        new ExactExportTable.Atom("BOOLEAN", "false"),
                        new ExactExportTable.Atom("NUMBER", "0"),
                        new ExactExportTable.Atom("ABSENT", "", "UNOBSERVED"))) {
            final var left = atomTable(atom, 1);
            final var right = atomTable(atom, 1);
            assertFalse(left.absentOnlyBagEquals(right, 1));
            assertEquals(
                    "NUMBER".equals(atom.type()),
                    left.compare(right).totalsEqual().get(0).booleanValue());
        }
        for (final var cell :
                List.of(
                        ManifestosContentFixture.text(""),
                        ManifestosContentFixture.text("0"),
                        new ManifestosContentFixture.Cell("b", "0", 0))) {
            final var fixture = absentCapacity(1);
            fixture.nativeRows.get(0).put(86, cell);
            assertThrows(IllegalArgumentException.class, fixture::compare);
        }
    }

    @Test
    void anotherAllAbsentTaxTotalRemainsUnreadyAndUnequal() throws Exception {
        final var fixture = fixture(1);
        fixture.records.get(0).putNull("mft_a_t_inss_value");
        fixture.nativeRows.get(0).remove(72);
        final var result = fixture.compare();
        assertTrue(allTrue(result.path("comparison").path("columnsEqual")));
        assertTrue(result.path("comparison").path("fullTuplesEqual").booleanValue());
        assertFalse(result.path("contentEqualUnderCandidatePresentation").booleanValue());
        assertFalse(result.path("comparison").path("totalsReady").get(43).booleanValue());
        assertFalse(result.path("totalComparisonsEqual").get(43).booleanValue());
        assertEquals(49, result.path("numericTotalsEqual").intValue());
        assertEquals(0, result.path("absenceOnlyTotalsEqual").intValue());
    }

    @Test
    void numericPresenceOnOnlyOneCapacitySideCannotBeAcceptedAsAbsence() throws Exception {
        for (final boolean source : new boolean[] {true, false}) {
            final var fixture = absentCapacity(1);
            if (source) {
                fixture.records.get(0).put("mft_tl2_weight_capacity", "0");
            } else {
                fixture.nativeRows.get(0).put(86, ManifestosContentFixture.number("0"));
            }
            final var result = fixture.compare();
            assertFalse(result.path("contentEqualUnderCandidatePresentation").booleanValue());
            assertFalse(result.path("totalComparisonsEqual").get(49).booleanValue());
            assertEquals(0, result.path("absenceOnlyTotalsEqual").intValue());
        }
    }

    @Test
    void absenceEqualityRetainsDuplicatesAndRejectsChangedMultiplicity() throws Exception {
        final var fixture = absentCapacity(1);
        fixture.records.add(fixture.records.get(0).deepCopy());
        fixture.nativeRows.add(new LinkedHashMap<>(fixture.nativeRows.get(0)));
        final var equal = fixture.compare();
        assertTrue(equal.path("contentEqualUnderCandidatePresentation").booleanValue());
        assertEquals(1, equal.path("absenceOnlyTotalsEqual").intValue());
        assertEquals(2, equal.path("sourceRows").intValue());
        assertEquals(1, equal.path("comparison").path("apiDistinctIds").intValue());
        fixture.nativeRows.remove(1);
        final var changed = fixture.compare();
        assertFalse(changed.path("contentEqualUnderCandidatePresentation").booleanValue());
        assertEquals(0, changed.path("absenceOnlyTotalsEqual").intValue());
        assertFalse(changed.path("totalComparisonsEqual").get(49).booleanValue());
        assertEquals(1, changed.path("comparison").path("changedMultiplicityIds").intValue());
    }

    @Test
    void effective50TotalComparisonsDoNotHideTupleOrTypedIdAssociationDifferences()
            throws Exception {
        final var fixture = absentCapacity(2);
        fixture.records.get(1).put("fleet_costs_subtotal", "0.2");
        fixture.nativeRows.get(0).put(64, ManifestosContentFixture.number("0.2"));
        final var result = fixture.compare();
        assertTrue(allTrue(result.path("comparison").path("columnsEqual")));
        assertTrue(allTrue(result.path("totalComparisonsEqual")));
        assertEquals(49, result.path("numericTotalsEqual").intValue());
        assertEquals(1, result.path("absenceOnlyTotalsEqual").intValue());
        assertFalse(result.path("comparison").path("fullTuplesEqual").booleanValue());
        assertEquals(2, result.path("comparison").path("changedIds").intValue());
        assertFalse(result.path("contentEqualUnderCandidatePresentation").booleanValue());
    }

    @Test
    void
            absentBagQueryRequiresPositiveRowsEqualMultiplicityAndSealedMappingsWithoutChangingDefaultCompare() {
        final var absent = new ExactExportTable.Atom("ABSENT", "");
        final var one = atomTable(absent, 1);
        final var equal = atomTable(absent, 1);
        assertTrue(one.absentOnlyBagEquals(equal, 1));
        assertFalse(one.compare(equal).contentEqual());
        assertFalse(one.compare(equal).totalsReady().get(0));
        assertFalse(one.compare(equal).totalsEqual().get(0));
        assertFalse(one.absentOnlyBagEquals(atomTable(absent, 2), 1));
        assertFalse(atomTable(absent, 0).absentOnlyBagEquals(atomTable(absent, 0), 1));
        assertThrows(IllegalArgumentException.class, () -> one.absentOnlyBagEquals(equal, 2));
        final var unsealed =
                new ExactExportTable(
                        List.of(
                                new ExactExportTable.Column("ID", 1, 1, false),
                                new ExactExportTable.Column("Value", 2, 2, true)),
                        0,
                        ManifestosContentFixture.budget());
        assertThrows(IllegalArgumentException.class, () -> unsealed.absentOnlyBagEquals(one, 1));
    }

    @Test
    void observedCiotPendingIsPendenteWhileExistingCiotNullOmissionStaysQualified()
            throws Exception {
        final var fixture = fixture(1);
        fixture.records.get(0).put("mft_cat_cot_status", "pending");
        fixture.nativeRows.get(0).put(21, ManifestosContentFixture.text("Pendente"));
        assertTrue(fixture.compare().path("contentEqualUnderCandidatePresentation").booleanValue());
        fixture.nativeRows.get(0).put(21, ManifestosContentFixture.text("Encerrado"));
        assertFalse(
                fixture.compare().path("contentEqualUnderCandidatePresentation").booleanValue());
        fixture.records.get(0).putNull("mft_cat_cot_status");
        fixture.nativeRows.get(0).remove(21);
        assertTrue(fixture.compare().path("contentEqualUnderCandidatePresentation").booleanValue());
    }

    @Test
    void
            observedEmptyDriverContractMatchesAbsentCellAndRetainsRawStringWithoutQualifyingNullOrBlank()
                    throws Exception {
        final var fixture = fixture(1);
        fixture.records.get(0).put("mft_mdr_contract_type", "");
        fixture.nativeRows.get(0).remove(46);
        final var result = fixture.compare();
        assertTrue(result.path("contentEqualUnderCandidatePresentation").booleanValue());
        assertTrue(fixture.records.get(0).path("mft_mdr_contract_type").isTextual());
        assertEquals("", fixture.records.get(0).path("mft_mdr_contract_type").textValue());
        assertEquals(
                atomBag(new ExactExportTable.Atom("TEXT", ""), 1),
                bag(result, "originalValueProjection", "api", 45));
        assertEquals(
                atomBag(new ExactExportTable.Atom("ABSENT", ""), 1),
                bag(result, "fullValueProjection", "api", 45));
        fixture.nativeRows.get(0).put(46, new ManifestosContentFixture.Cell(null, null, 0));
        assertThrows(IllegalArgumentException.class, fixture::compare);
        fixture.nativeRows.get(0).remove(46);
        fixture.records.get(0).putNull("mft_mdr_contract_type");
        assertEquals(
                "MANIFESTOS_SOURCE_NULL_NOT_QUALIFIED",
                assertThrows(IllegalArgumentException.class, fixture::compare).getMessage());
    }

    @Test
    void otherEmptyEnumsAndNumericEmptyStringsCannotBorrowDriverContractOmission()
            throws Exception {
        for (final String field :
                List.of(
                        "status",
                        "mft_cat_cot_status",
                        "mdfe_status",
                        "contract_type",
                        "cargo_type")) {
            final var fixture = fixture(1);
            fixture.records.get(0).put(field, "");
            assertEquals(
                    "MANIFESTOS_SOURCE_ENUM_UNOBSERVED",
                    assertThrows(IllegalArgumentException.class, fixture::compare).getMessage());
        }
        for (final String field : List.of("mft_tl2_weight_capacity", "mft_tl1_weight_capacity")) {
            final var fixture = fixture(1);
            fixture.records.get(0).put(field, "");
            assertThrows(IllegalArgumentException.class, fixture::compare);
        }
    }

    @Test
    void observedFinishedAtNullAndInTransitLabelPreserveRawValuesAcrossEightOccurrences()
            throws Exception {
        final var fixture = fixture(1);
        fixture.records.get(0).put("status", "in_transit").putNull("finished_at");
        fixture.nativeRows.get(0).put(19, ManifestosContentFixture.text("Em trânsito"));
        fixture.nativeRows.get(0).put(13, new ManifestosContentFixture.Cell("n", null, 2));
        for (int index = 1; index < 8; index++) {
            fixture.records.add(fixture.records.get(0).deepCopy());
            fixture.nativeRows.add(new LinkedHashMap<>(fixture.nativeRows.get(0)));
        }
        final var result = fixture.compare();
        assertTrue(result.path("contentEqualUnderCandidatePresentation").booleanValue());
        assertEquals(8, result.path("sourceRows").intValue());
        assertEquals(1, result.path("comparison").path("apiDistinctIds").intValue());
        assertEquals(2, fixture.nativeRows.get(0).get(13).style());
        assertTrue(fixture.records.get(0).path("finished_at").isNull());
        assertEquals(
                atomBag(new ExactExportTable.Atom("NULL", ""), 8),
                bag(result, "originalValueProjection", "api", 12));
        assertEquals(
                atomBag(
                        new ExactExportTable.Atom(
                                "BLANK",
                                "",
                                fixture.catalog.path("nativeTimestampCarrier").textValue()),
                        8),
                bag(result, "originalValueProjection", "ui", 12));
        assertNotEquals(
                bag(result, "originalValueProjection", "api", 18),
                bag(result, "fullValueProjection", "api", 18));
        fixture.nativeRows.forEach(row -> row.remove(13));
        final var absent = fixture.compare();
        assertTrue(absent.path("contentEqualUnderCandidatePresentation").booleanValue());
        assertNotEquals(
                bag(result, "originalValueProjection", "ui", 12),
                bag(absent, "originalValueProjection", "ui", 12));
    }

    @Test
    void finishedAtBlankNeedsTheQualifiedTimestampCarrierAndDoesNotQualifyOtherTimestampNulls()
            throws Exception {
        final var general = fixture(1);
        general.records.get(0).putNull("finished_at");
        general.nativeRows.get(0).put(13, new ManifestosContentFixture.Cell("n", null, 0));
        assertEquals(
                "MANIFESTOS_NULL_BLANK_CARRIER",
                assertThrows(IllegalArgumentException.class, general::compare).getMessage());
        final var other = fixture(1);
        other.records.get(0).putNull("created_at");
        other.nativeRows.get(0).put(2, new ManifestosContentFixture.Cell("n", null, 2));
        assertEquals(
                "MANIFESTOS_SOURCE_NULL_NOT_QUALIFIED",
                assertThrows(IllegalArgumentException.class, other::compare).getMessage());
    }

    @Test
    void observedInTransitDoesNotQualifyUnknownStatusOrAnUnobservedNativeSpelling()
            throws Exception {
        final var unknown = fixture(1);
        unknown.records.get(0).put("status", "unobserved_status");
        assertEquals(
                "MANIFESTOS_SOURCE_ENUM_UNOBSERVED",
                assertThrows(IllegalArgumentException.class, unknown::compare).getMessage());
        final var spelling = fixture(1);
        spelling.records.get(0).put("status", "in_transit");
        spelling.nativeRows.get(0).put(19, ManifestosContentFixture.text("Em transito"));
        assertEquals(
                "MANIFESTOS_NATIVE_ENUM_UNOBSERVED",
                assertThrows(IllegalArgumentException.class, spelling::compare).getMessage());
    }

    @Test
    void observedTrailerAbsencesRetainRawNullAndAbsentBagsWithoutFiftyNumericTotals()
            throws Exception {
        final var fixture = absentCapacity(1);
        fixture.records.get(0).putNull("mft_tl1_weight_capacity");
        fixture.nativeRows.get(0).remove(84);
        final String sourceSha = ExportValidationSupport.sha256(fixture.sourceBytes());
        final var result = fixture.compare();
        assertTrue(result.path("contentEqualUnderCandidatePresentation").booleanValue());
        assertEquals(48, result.path("numericTotalsEqual").intValue());
        assertEquals(2, result.path("absenceOnlyTotalsEqual").intValue());
        assertEquals(50, trueCount(result.path("totalComparisonsEqual")));
        assertEquals(48, trueCount(result.path("comparison").path("totalsReady")));
        assertFalse(result.path("absenceEqualityIsNumericZero").booleanValue());
        assertEquals(2, result.path("emptyAggregatePolicies").size());
        for (final int column : List.of(84, 86)) {
            final int total = column == 84 ? 48 : 49;
            assertEquals(
                    "MANIFESTOS_COLUMN" + column + "_ABSENT_ONLY_BAGS_EQUAL_V1",
                    result.path("totalComparisonPolicies").get(total).textValue());
            assertEquals(
                    atomBag(new ExactExportTable.Atom("NULL", ""), 1),
                    bag(result, "originalValueProjection", "api", column - 1));
            assertEquals(
                    atomBag(new ExactExportTable.Atom("ABSENT", ""), 1),
                    bag(result, "originalValueProjection", "ui", column - 1));
            assertEquals(
                    atomBag(new ExactExportTable.Atom("ABSENT", ""), 1),
                    bag(result, "fullValueProjection", "api", column - 1));
            assertFalse(
                    result.path("fullValueProjection")
                            .path("api")
                            .path("totals")
                            .get(total)
                            .path("ready")
                            .booleanValue());
        }
        assertEquals(sourceSha, ExportValidationSupport.sha256(fixture.sourceBytes()));
    }

    @Test
    void firstTrailerRealZeroAndNumberRemainNumericWhenSecondTrailerIsAbsent() throws Exception {
        for (final String number : List.of("0", "3.5")) {
            final var fixture = absentCapacity(2);
            fixture.records.forEach(record -> record.putNull("mft_tl1_weight_capacity"));
            fixture.nativeRows.forEach(row -> row.remove(84));
            fixture.records.get(0).put("mft_tl1_weight_capacity", number);
            fixture.nativeRows.get(0).put(84, ManifestosContentFixture.number(number));
            final var result = fixture.compare();
            assertTrue(result.path("contentEqualUnderCandidatePresentation").booleanValue());
            assertEquals(49, result.path("numericTotalsEqual").intValue());
            assertEquals(1, result.path("absenceOnlyTotalsEqual").intValue());
            assertTrue(result.path("comparison").path("totalsReady").get(48).booleanValue());
            assertEquals(
                    "NUMERIC_READY_REQUIRED",
                    result.path("totalComparisonPolicies").get(48).textValue());
            assertFalse(result.path("comparison").path("totalsReady").get(49).booleanValue());
            assertEquals(
                    "MANIFESTOS_COLUMN86_ABSENT_ONLY_BAGS_EQUAL_V1",
                    result.path("totalComparisonPolicies").get(49).textValue());
        }
    }

    @Test
    void capacityBlankNativeNullAndOneSidedZeroCannotBorrowAbsentPolicy() throws Exception {
        final var catalog = ManifestosContentAdapter.catalog();
        for (final int column : List.of(84, 86)) {
            final String field =
                    column == 84 ? "mft_tl1_weight_capacity" : "mft_tl2_weight_capacity";
            final int total = column == 84 ? 48 : 49;
            final var blank = fixture(1);
            blank.records.get(0).putNull(field);
            blank.nativeRows.get(0).put(column, new ManifestosContentFixture.Cell("n", null, 0));
            final var result = blank.compare();
            assertFalse(result.path("contentEqualUnderCandidatePresentation").booleanValue());
            assertFalse(result.path("totalComparisonsEqual").get(total).booleanValue());
            for (final var atom :
                    List.of(
                            new ExactExportTable.Atom("NULL", ""),
                            new ExactExportTable.Atom("ABSENT", "", "UNOBSERVED"))) {
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                ManifestosContentAdapter.nativeValue(
                                        catalog.path("columns").get(column - 1), atom, catalog));
            }
            for (final boolean source : new boolean[] {true, false}) {
                final var oneSide = fixture(1);
                oneSide.records.get(0).putNull(field);
                oneSide.nativeRows.get(0).remove(column);
                if (source) {
                    oneSide.records.get(0).put(field, "0");
                } else {
                    oneSide.nativeRows.get(0).put(column, ManifestosContentFixture.number("0"));
                }
                final var different = oneSide.compare();
                assertFalse(
                        different.path("contentEqualUnderCandidatePresentation").booleanValue());
                assertFalse(different.path("totalComparisonsEqual").get(total).booleanValue());
            }
        }
    }

    @Test
    void capacityPoliciesRejectWrongCoordinateFieldTypeModelAndCrossedPolicy() throws Exception {
        final var catalog = ManifestosContentAdapter.catalog();
        for (final int column : List.of(84, 86)) {
            for (final String mutation :
                    List.of(
                            "coordinate",
                            "coordinateType",
                            "overflow",
                            "field",
                            "kind",
                            "total",
                            "model",
                            "policy",
                            "nullPolicy",
                            "emptyPolicy")) {
                final JsonNode copy = catalog.deepCopy();
                final var field = (ObjectNode) copy.path("columns").get(column - 1);
                switch (mutation) {
                    case "coordinate" -> field.put("nativeColumn", column + 1);
                    case "coordinateType" -> field.put("nativeColumn", Integer.toString(column));
                    case "overflow" -> field.put("nativeColumn", 4294967296L + column);
                    case "field" -> field.put("sourceField", "unobserved_capacity_field");
                    case "kind" -> field.put("kind", "integer");
                    case "total" -> field.put("total", false);
                    case "model" -> field.put("totalKind", "DECIMAL_EXACT");
                    case "policy" ->
                            field.put(
                                    "emptyAggregatePolicy",
                                    "MANIFESTOS_COLUMN"
                                            + (column == 84 ? 86 : 84)
                                            + "_ABSENT_ONLY_BAGS_EQUAL_V1");
                    case "nullPolicy" -> field.put("nullPresentation", "NONE");
                    case "emptyPolicy" ->
                            field.put("emptyStringPresentation", "SCOPED_EMPTY_NATIVE_OMISSION");
                    default -> throw new AssertionError(mutation);
                }
                assertFalse(ManifestosContentAdapter.absenceTotalPolicy(field));
                assertThrows(
                        IllegalArgumentException.class,
                        () -> ManifestosContentAdapter.verifyCatalog(copy));
            }
        }
        for (final int column : List.of(72, 73, 74)) {
            final JsonNode copy = catalog.deepCopy();
            final var field = (ObjectNode) copy.path("columns").get(column - 1);
            field.put("emptyAggregatePolicy", "MANIFESTOS_COLUMN84_ABSENT_ONLY_BAGS_EQUAL_V1");
            assertFalse(ManifestosContentAdapter.absenceTotalPolicy(field));
            assertThrows(
                    IllegalArgumentException.class,
                    () -> ManifestosContentAdapter.verifyCatalog(copy));
        }
    }

    private ManifestosContentFixture absentCapacity(final int rows) throws Exception {
        final var fixture = fixture(rows);
        fixture.records.forEach(record -> record.putNull("mft_tl2_weight_capacity"));
        fixture.nativeRows.forEach(cells -> cells.remove(86));
        return fixture;
    }

    private static ExactExportTable atomTable(final ExactExportTable.Atom value, final int rows) {
        final var table =
                new ExactExportTable(
                        List.of(
                                new ExactExportTable.Column("ID", 1, 1, false),
                                new ExactExportTable.Column("Value", 2, 2, true)),
                        0,
                        ManifestosContentFixture.budget());
        for (int index = 0; index < rows; index++) {
            table.add(List.of(new ExactExportTable.Atom("NUMBER", "1"), value));
        }
        table.seal();
        return table;
    }

    private static JsonNode atomBag(final ExactExportTable.Atom value, final int rows) {
        return atomTable(value, rows)
                .fullValueProjection()
                .required("columnBags")
                .get(1)
                .required("bag");
    }

    @Test
    void closedAtNullMatchesOnlyObservedTimestampBlankAndPreservesBothRawRepresentations()
            throws Exception {
        final var fixture = fixture(1);
        fixture.records.get(0).putNull("closed_at");
        fixture.nativeRows.get(0).put(12, new ManifestosContentFixture.Cell("n", null, 2));
        final byte[] before = fixture.sourceBytes();
        final var result = fixture.compare();
        assertTrue(result.path("contentEqualUnderCandidatePresentation").booleanValue());
        assertEquals(
                atomBag(new ExactExportTable.Atom("NULL", ""), 1),
                bag(result, "originalValueProjection", "api", 11));
        assertEquals(
                atomBag(
                        new ExactExportTable.Atom(
                                "BLANK",
                                "",
                                fixture.catalog.path("nativeTimestampCarrier").textValue()),
                        1),
                bag(result, "originalValueProjection", "ui", 11));
        assertEquals(
                bag(result, "fullValueProjection", "api", 11),
                bag(result, "fullValueProjection", "ui", 11));
        assertEquals(
                ExportValidationSupport.sha256(before),
                ExportValidationSupport.sha256(fixture.sourceBytes()));
    }

    @Test
    void closedAtNullDoesNotQualifyAbsentWrongCarrierNativeNullOrOtherTimestampNull()
            throws Exception {
        final var catalog = ManifestosContentAdapter.catalog();
        for (final var atom :
                List.of(
                        new ExactExportTable.Atom("ABSENT", ""),
                        new ExactExportTable.Atom("NULL", ""),
                        new ExactExportTable.Atom(
                                "BLANK", "", catalog.path("nativeDecimalCarrier").textValue()))) {
            assertEquals(
                    "MANIFESTOS_CLOSED_AT_NATIVE_BLANK_CARRIER",
                    assertThrows(
                                    IllegalArgumentException.class,
                                    () ->
                                            ManifestosContentAdapter.nativeValue(
                                                    catalog.path("columns").get(11), atom, catalog))
                            .getMessage());
        }
        final var other = fixture(1);
        other.records.get(0).putNull("created_at");
        other.nativeRows.get(0).put(2, new ManifestosContentFixture.Cell("n", null, 2));
        assertEquals(
                "MANIFESTOS_SOURCE_NULL_NOT_QUALIFIED",
                assertThrows(IllegalArgumentException.class, other::compare).getMessage());
    }

    @Test
    void closedAtPolicyCannotMoveToAnotherFieldCoordinateKindOrTotal() throws Exception {
        final var catalog = ManifestosContentAdapter.catalog();
        for (final String mutation :
                List.of("coordinate", "field", "kind", "total", "otherTimestamp")) {
            final var copy = catalog.deepCopy();
            final var field = (ObjectNode) copy.path("columns").get(11);
            switch (mutation) {
                case "coordinate" -> field.put("nativeColumn", 12.5);
                case "field" -> field.put("sourceField", "unobserved_closed_at");
                case "kind" -> field.put("kind", "decimal");
                case "total" -> field.put("total", true);
                default ->
                        ((ObjectNode) copy.path("columns").get(1))
                                .put(
                                        "nullPresentation",
                                        field.path("nullPresentation").textValue());
            }
            assertThrows(
                    IllegalArgumentException.class,
                    () -> ManifestosContentAdapter.verifyCatalog(copy));
        }
    }

    @Test
    void ciotCloseErrorUsesOnlyTheExactObservedLabelAndKeepsRawCode() throws Exception {
        final var fixture = fixture(1);
        final var field = fixture.catalog.path("columns");
        int column = -1;
        for (final var item : field) {
            if ("mft_cat_cot_status".equals(item.path("sourceField").asText())) {
                column = item.path("nativeColumn").intValue();
            }
        }
        assertTrue(column > 0);
        fixture.records.get(0).put("mft_cat_cot_status", "close_error");
        fixture.nativeRows
                .get(0)
                .put(column, ManifestosContentFixture.text("Erro no encerramento"));
        final var result = fixture.compare();
        assertTrue(result.path("contentEqualUnderCandidatePresentation").booleanValue());
        assertNotEquals(
                bag(result, "originalValueProjection", "api", column - 1),
                bag(result, "fullValueProjection", "api", column - 1));
        fixture.nativeRows
                .get(0)
                .put(column, ManifestosContentFixture.text("Erro no Encerramento"));
        assertEquals(
                "MANIFESTOS_NATIVE_ENUM_UNOBSERVED",
                assertThrows(IllegalArgumentException.class, fixture::compare).getMessage());
        fixture.records.get(0).put("mft_cat_cot_status", "close_error_unknown");
        assertEquals(
                "MANIFESTOS_SOURCE_ENUM_UNOBSERVED",
                assertThrows(IllegalArgumentException.class, fixture::compare).getMessage());
    }

    @Test
    void mdfeIssuedUsesOnlyExactObservedEmitidoLabelPreservingRawAndRejectingUnknowns()
            throws Exception {
        final var fixture = fixture(1);
        int column = -1;
        for (final var field : fixture.catalog.path("columns")) {
            if ("mdfe_status".equals(field.path("sourceField").asText())) {
                column = field.path("nativeColumn").intValue();
            }
        }
        assertTrue(column > 0);
        fixture.records.get(0).put("mdfe_status", "issued");
        fixture.nativeRows.get(0).put(column, ManifestosContentFixture.text("Emitido"));
        final String before = ExportValidationSupport.sha256(fixture.sourceBytes());
        final var result = fixture.compare();
        assertTrue(result.path("contentEqualUnderCandidatePresentation").booleanValue());
        assertNotEquals(
                bag(result, "originalValueProjection", "api", column - 1),
                bag(result, "fullValueProjection", "api", column - 1));
        assertEquals(before, ExportValidationSupport.sha256(fixture.sourceBytes()));
        assertEquals("issued", fixture.records.get(0).path("mdfe_status").textValue());
        fixture.nativeRows.get(0).put(column, ManifestosContentFixture.text("emitido"));
        assertEquals(
                "MANIFESTOS_NATIVE_ENUM_UNOBSERVED",
                assertThrows(IllegalArgumentException.class, fixture::compare).getMessage());
        fixture.records.get(0).put("mdfe_status", "issued_unknown");
        assertEquals(
                "MANIFESTOS_SOURCE_ENUM_UNOBSERVED",
                assertThrows(IllegalArgumentException.class, fixture::compare).getMessage());
    }

    private ManifestosContentFixture fixture(final int rows) throws Exception {
        return new ManifestosContentFixture(directory, rows);
    }

    private static JsonNode bag(
            final JsonNode result, final String axis, final String side, final int index) {
        return result.required(axis)
                .required(side)
                .required("columnBags")
                .get(index)
                .required("bag");
    }

    private static boolean allTrue(final JsonNode values) {
        return values.isArray() && !values.isEmpty() && trueCount(values) == values.size();
    }

    private static int trueCount(final JsonNode values) {
        int count = 0;
        for (final var value : values) {
            count += value.isBoolean() && value.booleanValue() ? 1 : 0;
        }
        return count;
    }
}
