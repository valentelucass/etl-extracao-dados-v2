package br.com.esl.etl.v2.plataforma.reconciliacao.exportacao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ContasPagarContentAdapterTest {
    @TempDir Path directory;

    @Test
    void comparesAll28ValuesAndSevenExactTotalsWithoutInventingRootIdentifiers() throws Exception {
        final var result = new ContasPagarContentFixture(directory, 2).compare();
        assertTrue(result.path("contentEqualUnderCandidatePresentation").booleanValue());
        assertEquals(28, result.path("comparison").path("columnsEqual").size());
        assertEquals(7, result.path("comparison").path("totalsEqual").size());
        assertFalse(result.path("rootIdentityQualified").booleanValue());
        assertFalse(result.path("parcelCandidateUsedAsIdentity").booleanValue());
        assertFalse(result.path("fullValueProjection").path("api").has("idMultiplicityBag"));
    }

    @Test
    void repeatedAndNullParcelCandidatesRemainOrdinaryPhysicalOccurrences() throws Exception {
        final var fixture = new ContasPagarContentFixture(directory, 2);
        for (int row = 0; row < 2; row++) {
            fixture.records.get(row).putNull("ant_ils_sequence_code");
            fixture.nativeRows.get(row).remove(1);
        }
        fixture.field(1).put("nullPresentation", "NULL_NATIVE_ABSENT");
        assertTrue(fixture.compare().path("contentEqualUnderCandidatePresentation").booleanValue());
        fixture.records.add(fixture.records.get(0).deepCopy());
        fixture.nativeRows.add(new LinkedHashMap<>(fixture.nativeRows.get(0)));
        assertEquals(3, fixture.compare().path("sourceRows").intValue());
        fixture.nativeRows.remove(2);
        assertFalse(
                fixture.compare().path("contentEqualUnderCandidatePresentation").booleanValue());
    }

    @Test
    void issueDateScopesEveryRowWhileCreatedBeforeTheWeekRemainsIncluded() throws Exception {
        final var fixture = new ContasPagarContentFixture(directory, 1);
        fixture.records.get(0).put("created_at", "2026-10-01T12:00:00-03:00");
        final long day =
                java.time.temporal.ChronoUnit.DAYS.between(
                        java.time.LocalDate.of(1899, 12, 30), java.time.LocalDate.of(2026, 10, 1));
        fixture.nativeRows.get(0).put(14, new ContasPagarContentFixture.Cell(null, day + ".5", 2));
        final var result = fixture.compare();
        assertTrue(result.path("contentEqualUnderCandidatePresentation").booleanValue());
        assertFalse(result.path("createdAtScopeEnforced").booleanValue());
        fixture.records.get(0).put("issue_date", "2026-10-11");
        assertThrows(IllegalArgumentException.class, fixture::compare);
    }

    @Test
    void swappingRateioValuesPreservesColumnTotalsButChangesFullTuples() throws Exception {
        final var fixture = new ContasPagarContentFixture(directory, 2);
        fixture.records.get(0).put("ant_ces_value", "1");
        fixture.records.get(1).put("ant_ces_value", "2");
        fixture.nativeRows.get(0).put(7, ContasPagarContentFixture.number("2"));
        fixture.nativeRows.get(1).put(7, ContasPagarContentFixture.number("1"));
        final var result = fixture.compare();
        assertFalse(result.path("contentEqualUnderCandidatePresentation").booleanValue());
        assertFalse(result.path("comparison").path("fullTuplesEqual").booleanValue());
        for (final var equal : result.path("comparison").path("columnsEqual")) {
            assertTrue(equal.booleanValue());
        }
        for (final var equal : result.path("comparison").path("totalsEqual")) {
            assertTrue(equal.booleanValue());
        }
    }

    @Test
    void unorderedRowsCompareEquallyWithDuplicateMultiplicitiesPreserved() throws Exception {
        final var fixture = new ContasPagarContentFixture(directory, 3);
        Collections.reverse(fixture.nativeRows);
        assertTrue(fixture.compare().path("contentEqualUnderCandidatePresentation").booleanValue());
    }

    @Test
    void classificationIsFreeTextAndNeverAnInventedEnum() throws Exception {
        final var fixture = new ContasPagarContentFixture(directory, 1);
        fixture.records.get(0).put("ant_ils_pas_ant_classification", "4. Custos / 4.1 Variáveis");
        fixture.nativeRows
                .get(0)
                .put(3, ContasPagarContentFixture.text("4. Custos / 4.1 Variáveis"));
        assertTrue(fixture.compare().path("contentEqualUnderCandidatePresentation").booleanValue());
        fixture.field(3).put("kind", "enum");
        assertThrows(IllegalArgumentException.class, fixture::compare);
    }

    @Test
    void nativeBooleanCellsUseTheirActualEmptyCarrierAndRemainDistinctFromText() throws Exception {
        final var fixture = new ContasPagarContentFixture(directory, 1);
        fixture.field(17).put("booleanEncoding", "NATIVE_BOOLEAN_V1");
        fixture.nativeRows.get(0).put(17, new ContasPagarContentFixture.Cell("b", "0", 0));
        assertTrue(fixture.compare().path("contentEqualUnderCandidatePresentation").booleanValue());
        fixture.nativeRows.get(0).put(17, ContasPagarContentFixture.text("false"));
        assertThrows(IllegalArgumentException.class, fixture::compare);
    }

    @Test
    void nullAbsentBlankAndEmptyPoliciesMustBeExplicitForEachField() throws Exception {
        final var fixture = new ContasPagarContentFixture(directory, 1);
        fixture.records.get(0).putNull("comments");
        fixture.nativeRows.get(0).remove(18);
        assertFalse(
                fixture.compare().path("contentEqualUnderCandidatePresentation").booleanValue());
        fixture.field(18).put("nullPresentation", "NULL_NATIVE_ABSENT");
        assertTrue(fixture.compare().path("contentEqualUnderCandidatePresentation").booleanValue());
        fixture.records.get(0).put("comments", "");
        assertFalse(
                fixture.compare().path("contentEqualUnderCandidatePresentation").booleanValue());
        fixture.field(18).put("emptyStringPresentation", "EMPTY_NATIVE_ABSENT");
        assertTrue(fixture.compare().path("contentEqualUnderCandidatePresentation").booleanValue());
    }

    @Test
    void blankNumericCellRequiresTheDeclaredCarrierAndDoesNotBecomeZero() throws Exception {
        final var fixture = new ContasPagarContentFixture(directory, 1);
        fixture.records.get(0).putNull("ant_ils_atn_liquidation_date");
        fixture.nativeRows.get(0).put(15, new ContasPagarContentFixture.Cell(null, null, 3));
        assertFalse(
                fixture.compare().path("contentEqualUnderCandidatePresentation").booleanValue());
        fixture.field(15).put("nullPresentation", "NULL_NATIVE_BLANK");
        assertTrue(fixture.compare().path("contentEqualUnderCandidatePresentation").booleanValue());
        fixture.nativeRows.get(0).put(15, new ContasPagarContentFixture.Cell(null, null, 0));
        assertThrows(IllegalArgumentException.class, fixture::compare);
    }

    @Test
    void unknownBusinessEnumOrBooleanLabelCannotBeGuessed() throws Exception {
        final var fixture = new ContasPagarContentFixture(directory, 1);
        fixture.records.get(0).put("type", "unobserved");
        assertThrows(IllegalArgumentException.class, fixture::compare);
        fixture.records.get(0).put("type", "authored_pending");
        fixture.nativeRows.get(0).put(27, ContasPagarContentFixture.text("guessed"));
        assertThrows(IllegalArgumentException.class, fixture::compare);
    }

    @Test
    void declaredTimestampKeepsCivilClockAndRetainsOriginalOffsetWithoutTruncatingPrecision()
            throws Exception {
        assertEquals(
                "2026-10-13T12:00:00",
                ContasPagarContentAdapter.sourceTimestamp("2026-10-13T12:00:00+02:00"));
        assertThrows(
                IllegalArgumentException.class,
                () -> ContasPagarContentAdapter.sourceTimestamp("2026-10-13T12:00:00.001-03:00"));
        assertThrows(
                IllegalArgumentException.class,
                () -> ContasPagarContentAdapter.sourceTimestamp("2026-10-13T12:00:00"));
    }

    @Test
    void originalAxesRetainNumericSpellingAndNegativeZero() throws Exception {
        final var fixture = new ContasPagarContentFixture(directory, 1);
        fixture.records.get(0).put("value", "-0");
        fixture.nativeRows.get(0).put(23, ContasPagarContentFixture.number("-0"));
        final var result = fixture.compare();
        assertTrue(result.path("contentEqualUnderCandidatePresentation").booleanValue());
        assertFalse(
                result.path("originalValueProjection")
                        .path("api")
                        .equals(result.path("originalValueProjection").path("ui")));
        final var raw =
                ContasPagarContentAdapter.record(
                        new String(fixture.sourceBytes(), java.nio.charset.StandardCharsets.UTF_8)
                                .replace(
                                        "\"ant_ils_sequence_code\":1",
                                        "\"ant_ils_sequence_code\":1e0")
                                .strip());
        assertEquals(
                "1e0",
                ContasPagarContentAdapter.original(raw.get("ant_ils_sequence_code")).value());
    }

    @Test
    void aCatalogCannotIntroduceAFakeIdentityDropColumnsOrClaimQualification() throws Exception {
        for (final String mutation :
                new String[] {"identity", "qualified", "missing", "duplicate", "total"}) {
            final var fixture = new ContasPagarContentFixture(directory, 1);
            switch (mutation) {
                case "identity" -> fixture.catalog.put("idColumnIndex", 0);
                case "qualified" -> fixture.catalog.put("presentationQualified", true);
                case "missing" ->
                        ((com.fasterxml.jackson.databind.node.ArrayNode)
                                        fixture.catalog.get("columns"))
                                .remove(27);
                case "duplicate" -> fixture.field(28).put("sourceField", "document");
                default -> fixture.field(2).put("total", true);
            }
            assertThrows(IllegalArgumentException.class, fixture::compare);
        }
    }

    @Test
    void sourceEnvelopeRejectsWrongTemplateExtraFieldNestedValueAndIncompleteJsonl()
            throws Exception {
        for (final String mutation : new String[] {"template", "extra", "nested", "lf", "suffix"}) {
            final var fixture = new ContasPagarContentFixture(directory, 1);
            switch (mutation) {
                case "template" ->
                        fixture.template =
                                ExportValidationSupport.JSON.getNodeFactory().numberNode(6392);
                case "extra" -> fixture.records.get(0).put("fake", 1);
                case "nested" -> fixture.records.get(0).putArray("comments").add("x");
                case "lf" -> fixture.finalLf = false;
                default -> fixture.sourceSuffix = "{}\n";
            }
            assertThrows(IllegalArgumentException.class, fixture::compare);
        }
    }

    @Test
    void nativeHeaderWidthEpochAndScopeAreVerified() throws Exception {
        for (final String mutation : new String[] {"epoch", "header", "date"}) {
            final var fixture = new ContasPagarContentFixture(directory, 1);
            if ("epoch".equals(mutation)) {
                fixture.epoch = "1";
            } else if ("header".equals(mutation)) {
                fixture.field(2).put("nativeColumn", 29);
            } else {
                fixture.nativeRows.get(0).put(9, ContasPagarContentFixture.number("1"));
            }
            assertThrows(IllegalArgumentException.class, fixture::compare);
        }
    }

    @Test
    void postcheckRefusesSourceMutationAfterTheComparison() throws Exception {
        final var fixture = new ContasPagarContentFixture(directory, 1);
        final var pins = fixture.pins();
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        ContasPagarContentAdapter.compareWithPostcheckProbe(
                                fixture.request,
                                pins.source(),
                                pins.workbook(),
                                pins.catalog(),
                                ContasPagarContentFixture.budget(),
                                () -> {
                                    try {
                                        Files.writeString(pins.source().path(), "changed");
                                    } catch (final java.io.IOException error) {
                                        throw new java.io.UncheckedIOException(error);
                                    }
                                }));
    }
}
