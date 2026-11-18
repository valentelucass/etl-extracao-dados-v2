package br.com.esl.etl.v2.plataforma.reconciliacao.exportacao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Authored policy contracts only; no labels, carriers or records are supplier evidence. */
class ContasPagarPresentationPolicyTest {
    private static final String TRUNCATE = "SOURCE_OFFSET_CIVIL_TRUNCATE_FRACTION_V1";
    @TempDir Path directory;

    private static void classification(final ContasPagarContentFixture f) {
        f.field(3).put("textEncoding", "OBSERVED_CLASSIFICATION_LABELS_V1");
        f.catalog
                .putObject("classificationLabels")
                .put("authored_a", "Authored category A")
                .put("authored_b", "Authored category B");
    }

    @Test
    void classificationDefaultsToLiteralFreeTextWithoutLabels() throws Exception {
        final var f = new ContasPagarContentFixture(directory, 1);
        f.records.get(0).put("ant_ils_pas_ant_classification", "Authored / free text 4.2");
        f.nativeRows.get(0).put(3, ContasPagarContentFixture.text("Authored / free text 4.2"));
        assertTrue(f.compare().path("contentEqualUnderCandidatePresentation").booleanValue());
        assertEquals("text", f.field(3).path("kind").textValue());
        assertFalse(f.catalog.has("classificationLabels"));
    }

    @Test
    void explicitClassificationLabelsCompareAll28WhileOriginalCodesRemainLiteral()
            throws Exception {
        final var f = new ContasPagarContentFixture(directory, 2);
        classification(f);
        f.records.get(0).put("ant_ils_pas_ant_classification", "authored_a");
        f.records.get(1).put("ant_ils_pas_ant_classification", "authored_b");
        f.nativeRows.get(0).put(3, ContasPagarContentFixture.text("Authored category A"));
        f.nativeRows.get(1).put(3, ContasPagarContentFixture.text("Authored category B"));
        final var result = f.compare();
        assertTrue(result.path("contentEqualUnderCandidatePresentation").booleanValue());
        assertEquals(
                result.path("fullValueProjection").path("api"),
                result.path("fullValueProjection").path("ui"));
        assertNotEquals(
                result.path("originalValueProjection").path("api"),
                result.path("originalValueProjection").path("ui"));
        assertEquals(
                "authored_a", f.records.get(0).path("ant_ils_pas_ant_classification").textValue());
    }

    @Test
    void unknownSourceClassificationIsRefusedWithoutFallbackToItsLiteralCode() throws Exception {
        final var f = new ContasPagarContentFixture(directory, 1);
        classification(f);
        f.records.get(0).put("ant_ils_pas_ant_classification", "unobserved_authored_code");
        assertThrows(IllegalArgumentException.class, f::compare);
    }

    @Test
    void unknownNativeClassificationLabelIsRefused() throws Exception {
        final var f = new ContasPagarContentFixture(directory, 1);
        classification(f);
        f.records.get(0).put("ant_ils_pas_ant_classification", "authored_a");
        f.nativeRows.get(0).put(3, ContasPagarContentFixture.text("Unobserved authored label"));
        assertThrows(IllegalArgumentException.class, f::compare);
    }

    @Test
    void classificationTextEncodingCannotApplyToOtherTextOrAnEnumKind() throws Exception {
        final var f = new ContasPagarContentFixture(directory, 1);
        classification(f);
        f.field(18).put("textEncoding", "OBSERVED_CLASSIFICATION_LABELS_V1");
        assertThrows(
                IllegalArgumentException.class,
                () -> ContasPagarContentAdapter.verifyCatalog(f.catalog));
        f.field(18).remove("textEncoding");
        f.field(3).put("kind", "enum");
        assertThrows(
                IllegalArgumentException.class,
                () -> ContasPagarContentAdapter.verifyCatalog(f.catalog));
    }

    @Test
    void labelsMustBeFlatBoundedAndCoupledToTheExplicitPolicy() throws Exception {
        final var f = new ContasPagarContentFixture(directory, 1);
        f.catalog.putObject("classificationLabels").put("authored_a", "A");
        assertThrows(
                IllegalArgumentException.class,
                () -> ContasPagarContentAdapter.verifyCatalog(f.catalog));
        classification(f);
        f.catalog.putObject("classificationLabels");
        assertThrows(
                IllegalArgumentException.class,
                () -> ContasPagarContentAdapter.verifyCatalog(f.catalog));
        f.catalog.putObject("classificationLabels").putObject("nested").put("code", "label");
        assertThrows(
                IllegalArgumentException.class,
                () -> ContasPagarContentAdapter.verifyCatalog(f.catalog));
    }

    @Test
    void classificationNullAndEmptyStillNeedTheirOwnExplicitAbsencePolicies() throws Exception {
        final var f = new ContasPagarContentFixture(directory, 1);
        classification(f);
        f.records.get(0).putNull("ant_ils_pas_ant_classification");
        f.nativeRows.get(0).remove(3);
        assertFalse(f.compare().path("contentEqualUnderCandidatePresentation").booleanValue());
        f.field(3).put("nullPresentation", "NULL_NATIVE_ABSENT");
        assertTrue(f.compare().path("contentEqualUnderCandidatePresentation").booleanValue());
        f.records.get(0).put("ant_ils_pas_ant_classification", "");
        assertThrows(IllegalArgumentException.class, f::compare);
        f.field(3).put("emptyStringPresentation", "EMPTY_NATIVE_ABSENT");
        assertTrue(f.compare().path("contentEqualUnderCandidatePresentation").booleanValue());
    }

    @Test
    void truncateFractionKeepsTheCivilSecondAndNeverRoundsOrConvertsOffset() {
        assertEquals(
                "2026-10-13T23:59:59",
                ContasPagarContentAdapter.sourceTimestamp(
                        "2026-10-13T23:59:59.999999999+18:00", TRUNCATE));
        assertEquals(
                "2026-10-13T00:00:00",
                ContasPagarContentAdapter.sourceTimestamp(
                        "2026-10-13T00:00:00.999-03:00", TRUNCATE));
        assertEquals(
                "2026-10-13T12:00:00",
                ContasPagarContentAdapter.sourceTimestamp("2026-10-13T12:00:00.001Z", TRUNCATE));
    }

    @Test
    void originalWholeSecondApiAndPolicyStillRejectNonzeroFraction() {
        final String wholeSecond = "SOURCE_OFFSET_CIVIL_WHOLE_SECOND_V1";
        assertThrows(
                IllegalArgumentException.class,
                () -> ContasPagarContentAdapter.sourceTimestamp("2026-10-13T12:00:00.001-03:00"));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        ContasPagarContentAdapter.sourceTimestamp(
                                "2026-10-13T12:00:00.001-03:00", wholeSecond));
        assertEquals(
                "2026-10-13T12:00:00",
                ContasPagarContentAdapter.sourceTimestamp("2026-10-13T12:00:00.000000000+02:00"));
    }

    @Test
    void truncateStillRequiresAValidBoundedOffsetTimestamp() {
        for (final String value :
                new String[] {
                    "2026-10-13T12:00:00",
                    "2026-10-13T12:00:00.0000000001Z",
                    " 2026-10-13T12:00:00Z"
                }) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> ContasPagarContentAdapter.sourceTimestamp(value, TRUNCATE));
        }
        assertThrows(
                java.time.DateTimeException.class,
                () ->
                        ContasPagarContentAdapter.sourceTimestamp(
                                "2026-02-30T12:00:00.1Z", TRUNCATE));
    }

    @Test
    void truncatedPresentationDoesNotEraseFractionOrOffsetFromTheOriginalAxis() throws Exception {
        final var f = new ContasPagarContentFixture(directory, 1);
        f.records.get(0).put("created_at", "2026-10-13T12:00:00.987-03:00");
        assertThrows(IllegalArgumentException.class, f::compare);
        f.field(14).put("sourceTimestampEncoding", TRUNCATE);
        final var first = f.compare();
        assertTrue(first.path("contentEqualUnderCandidatePresentation").booleanValue());
        f.records.get(0).put("created_at", "2026-10-13T12:00:00.123456789+02:00");
        final var second = f.compare();
        assertTrue(second.path("contentEqualUnderCandidatePresentation").booleanValue());
        assertEquals(first.path("fullValueProjection"), second.path("fullValueProjection"));
        assertNotEquals(
                first.path("originalValueProjection").path("api"),
                second.path("originalValueProjection").path("api"));
    }
}
