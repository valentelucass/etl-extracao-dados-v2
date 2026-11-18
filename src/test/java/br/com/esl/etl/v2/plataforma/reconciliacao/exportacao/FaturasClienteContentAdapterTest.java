package br.com.esl.etl.v2.plataforma.reconciliacao.exportacao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FaturasClienteContentAdapterTest {
    @TempDir Path directory;

    @Test
    void all52FieldsFourDeclaredTotalsAndFourIndependentProjectionsAreCompared() throws Exception {
        final var fixture = new FaturasClienteContentFixture(directory, 2);
        final var result = fixture.compare();
        assertTrue(result.path("contentEqualUnderCandidatePresentation").booleanValue());
        assertEquals(52, result.path("columns").intValue());
        assertEquals(4, result.path("totalColumns").intValue());
        assertEquals(2, result.path("sourceRows").longValue());
        assertEquals(52, result.required("columnDiagnostics").size());
        for (final String axis :
                java.util.List.of("fullValueProjection", "originalValueProjection")) {
            assertTrue(result.required(axis).has("api"));
            assertTrue(result.required(axis).has("ui"));
        }
        assertFalse(result.path("rootIdentityQualified").booleanValue());
        assertFalse(result.path("deduplicationPerformed").booleanValue());
        assertFalse(result.path("contentAccepted").booleanValue());
        assertFalse(result.path("floatArithmeticUsed").booleanValue());
    }

    @Test
    void changingAssociationsWithUnchangedColumnBagsFailsFullTupleEquality() throws Exception {
        final var fixture = new FaturasClienteContentFixture(directory, 2);
        final int payer = coordinate(fixture, "fit_pyr_name");
        final var first = fixture.nativeRows.get(0).get(payer);
        fixture.nativeRows.get(0).put(payer, fixture.nativeRows.get(1).get(payer));
        fixture.nativeRows.get(1).put(payer, first);
        final var result = fixture.compare();
        for (final var equal : result.required("comparison").required("columnsEqual")) {
            assertTrue(equal.booleanValue());
        }
        assertFalse(result.path("comparison").path("fullTuplesEqual").booleanValue());
        assertFalse(result.path("contentEqualUnderCandidatePresentation").booleanValue());
    }

    @Test
    void repeatedIdsAndEntireRowsRemainOccurrencesAndMissingOneDuplicateFails() throws Exception {
        final var fixture = new FaturasClienteContentFixture(directory, 1);
        fixture.records.add(fixture.records.get(0).deepCopy());
        fixture.nativeRows.add(new LinkedHashMap<>(fixture.nativeRows.get(0)));
        assertTrue(fixture.compare().path("contentEqualUnderCandidatePresentation").booleanValue());
        fixture.nativeRows.remove(1);
        final var result = fixture.compare();
        assertFalse(result.path("contentEqualUnderCandidatePresentation").booleanValue());
        assertEquals(1, result.path("comparison").path("apiOnlyOccurrences").longValue());
    }

    @Test
    void aDifferentBinary64DecimalCannotBeHiddenByDisplayRoundingOrEpsilon() throws Exception {
        final var fixture = new FaturasClienteContentFixture(directory, 1);
        fixture.nativeRows
                .get(0)
                .put(
                        coordinate(fixture, "total"),
                        FaturasClienteContentFixture.number("0.10000000000000002"));
        final var result = fixture.compare();
        assertFalse(result.path("contentEqualUnderCandidatePresentation").booleanValue());
        assertFalse(result.path("epsilonUsed").booleanValue());
        final var field = field(fixture, "total");
        field.put("sourceNumberEncoding", "SOURCE_BINARY64_DECIMAL16_HALF_EVEN_BINARY64_V1");
        assertThrows(IllegalArgumentException.class, fixture::compare);
    }

    @Test
    void emptyArraysNullAndOneEmptyItemHaveSeparateExplicitPresentations() throws Exception {
        final var fixture = new FaturasClienteContentFixture(directory, 1);
        final String name = "invoices_mapping";
        final var field = field(fixture, name);
        fixture.records.get(0).putArray(name);
        fixture.nativeRows.get(0).remove(coordinate(fixture, name));
        assertFalse(
                fixture.compare().path("contentEqualUnderCandidatePresentation").booleanValue());
        field.put("emptyArrayPresentation", "EMPTY_ARRAY_NATIVE_ABSENT");
        assertTrue(fixture.compare().path("contentEqualUnderCandidatePresentation").booleanValue());
        fixture.records.get(0).putArray(name).add("");
        assertFalse(
                fixture.compare().path("contentEqualUnderCandidatePresentation").booleanValue());
        fixture.records.get(0).putNull(name);
        assertFalse(
                fixture.compare().path("contentEqualUnderCandidatePresentation").booleanValue());
        field.put("nullPresentation", "NULL_NATIVE_ABSENT");
        assertTrue(fixture.compare().path("contentEqualUnderCandidatePresentation").booleanValue());
        fixture.records.get(0).putArray(name).add("A").add("B").add("A");
        fixture.nativeRows
                .get(0)
                .put(coordinate(fixture, name), FaturasClienteContentFixture.text("A | A | B"));
        assertFalse(
                fixture.compare().path("contentEqualUnderCandidatePresentation").booleanValue());
    }

    @Test
    void booleanWireAndNativeBooleanOrPinnedLabelsCannotBeInterchangedSilently() throws Exception {
        final var fixture = new FaturasClienteContentFixture(directory, 1);
        final var field = field(fixture, "courtesy");
        field.put("booleanEncoding", "NATIVE_BOOLEAN_V1");
        assertThrows(IllegalArgumentException.class, fixture::compare);
        fixture.nativeRows
                .get(0)
                .put(
                        coordinate(fixture, "courtesy"),
                        new FaturasClienteContentFixture.Cell("b", "0", 0));
        assertTrue(fixture.compare().path("contentEqualUnderCandidatePresentation").booleanValue());
        fixture.records.get(0).put("courtesy", "false");
        assertThrows(IllegalArgumentException.class, fixture::compare);
    }

    @Test
    void enumCodesRequirePinnedDisjointLabelsOrExplicitAbsenceAndNeverReuseNull() throws Exception {
        final var fixture = new FaturasClienteContentFixture(directory, 1);
        fixture.records.get(0).put("type", "unobserved-code");
        assertThrows(IllegalArgumentException.class, fixture::compare);
        fixture.records.get(0).put("type", "authored_pending");
        fixture.catalog.putObject("enumAbsentCodes").putArray("type").add("authored_pending");
        assertThrows(IllegalArgumentException.class, fixture::compare);
        ((ObjectNode) fixture.catalog.required("enumLabels").required("type"))
                .remove("authored_pending");
        fixture.nativeRows.get(0).remove(coordinate(fixture, "type"));
        assertTrue(fixture.compare().path("contentEqualUnderCandidatePresentation").booleanValue());
        fixture.records.get(0).putNull("type");
        assertFalse(
                fixture.compare().path("contentEqualUnderCandidatePresentation").booleanValue());
    }

    @Test
    void offsetFractionAndCarrierPoliciesAreExplicitWhileOnlyServiceCivilDayIsScoped()
            throws Exception {
        final var fixture = new FaturasClienteContentFixture(directory, 1);
        fixture.records.get(0).put("service_at", "2026-10-13T12:00:00.123-03:00");
        assertThrows(IllegalArgumentException.class, fixture::compare);
        field(fixture, "service_at")
                .put("sourceTimestampEncoding", "SOURCE_OFFSET_CIVIL_TRUNCATE_FRACTION_V1");
        assertTrue(fixture.compare().path("contentEqualUnderCandidatePresentation").booleanValue());
        field(fixture, "service_at").put("nativeCarrier", "XLSX:1900:wrong");
        assertThrows(IllegalArgumentException.class, fixture::compare);
        field(fixture, "service_at")
                .put("nativeCarrier", "XLSX:1900:DD/MM/YYYY HH:MM:SS:apply=1:base=0:fmtId=164");
        fixture.records.get(0).put("service_at", "2026-10-19T12:00:00-03:00");
        assertThrows(IllegalArgumentException.class, fixture::compare);
    }

    @Test
    void changedPinnedBytesAfterCompleteComparisonCannotProduceAPassingReceipt() throws Exception {
        final var fixture = new FaturasClienteContentFixture(directory, 1);
        final var pins = fixture.pins();
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        FaturasClienteContentAdapter.compareWithPostcheckProbe(
                                fixture.request,
                                pins.source(),
                                pins.workbook(),
                                pins.catalog(),
                                FaturasClienteContentFixture.budget(),
                                () -> {
                                    try {
                                        Files.writeString(pins.source().path(), "changed");
                                    } catch (java.io.IOException failure) {
                                        throw new IllegalStateException(failure);
                                    }
                                }));
    }

    @Test
    void billingCycleCanUseAnExplicitAuthoredEnumWithoutInventingAnyProviderCode()
            throws Exception {
        final var fixture = new FaturasClienteContentFixture(directory, 1);
        final var field = field(fixture, "fit_pyr_cor_billing_cycle");
        field.put("kind", "enum");
        FaturasClienteContentAdapter.verifyCatalog(fixture.catalog);
        assertThrows(IllegalArgumentException.class, fixture::compare);
        ((ObjectNode) fixture.catalog.required("enumLabels"))
                .putObject("fit_pyr_cor_billing_cycle")
                .put("authored-bounded-code", "Authored bounded label");
        fixture.records.get(0).put("fit_pyr_cor_billing_cycle", "authored-bounded-code");
        fixture.nativeRows
                .get(0)
                .put(
                        coordinate(fixture, "fit_pyr_cor_billing_cycle"),
                        FaturasClienteContentFixture.text("Authored bounded label"));
        final var result = fixture.compare();
        assertTrue(result.path("contentEqualUnderCandidatePresentation").booleanValue());
        assertFalse(result.path("catalogBindingPoliciesQualified").booleanValue());
        assertFalse(result.path("contentAccepted").booleanValue());
        assertFalse(result.path("entityQualified").booleanValue());
    }

    @Test
    void postgresArrayEmptyAndOneEmptyStringRemainDifferentInTheWholeRow() throws Exception {
        final var fixture = new FaturasClienteContentFixture(directory, 1);
        postgresPolicy(fixture);
        fixture.records.get(0).putArray("fit_fte_invoices_order_number");
        setOrderCell(fixture, "{}");
        assertTrue(fixture.compare().path("contentEqualUnderCandidatePresentation").booleanValue());
        setOrderCell(fixture, "[]");
        assertFalse(
                fixture.compare().path("contentEqualUnderCandidatePresentation").booleanValue());
        fixture.records.get(0).putArray("fit_fte_invoices_order_number").add("");
        setOrderCell(fixture, "{\"\"}");
        assertTrue(fixture.compare().path("contentEqualUnderCandidatePresentation").booleanValue());
        setOrderCell(fixture, "{}");
        assertFalse(
                fixture.compare().path("contentEqualUnderCandidatePresentation").booleanValue());
    }

    @Test
    void postgresArrayOrderAndRepeatedItemsCannotBeReorderedToForceAgreement() throws Exception {
        final var fixture = new FaturasClienteContentFixture(directory, 1);
        postgresPolicy(fixture);
        fixture.records.get(0).putArray("fit_fte_invoices_order_number").add("A").add("B").add("A");
        final var original = fixture.records.get(0).deepCopy();
        setOrderCell(fixture, "{A,B,A}");
        assertTrue(fixture.compare().path("contentEqualUnderCandidatePresentation").booleanValue());
        assertEquals(original, fixture.records.get(0));
        setOrderCell(fixture, "{A,A,B}");
        assertFalse(
                fixture.compare().path("contentEqualUnderCandidatePresentation").booleanValue());
    }

    @Test
    void postgresArrayQuotesOnlySyntaxAsciiWhitespaceAndNullWordsWithoutChangingUnicode()
            throws Exception {
        final var fixture = new FaturasClienteContentFixture(directory, 1);
        postgresPolicy(fixture);
        fixture.records
                .get(0)
                .putArray("fit_fte_invoices_order_number")
                .add("a b")
                .add("a,b")
                .add("a\"b")
                .add("a\\b")
                .add("NULL")
                .add("á")
                .add("😀")
                .add("a\u00a0b");
        setOrderCell(fixture, "{\"a b\",\"a,b\",\"a\\\"b\",\"a\\\\b\",\"NULL\",á,😀,a\u00a0b}");
        assertTrue(fixture.compare().path("contentEqualUnderCandidatePresentation").booleanValue());
        setOrderCell(fixture, "{\"a b\",\"a,b\",\"a\\\"b\",\"a\\\\b\",NULL,á,😀,a\u00a0b}");
        assertFalse(
                fixture.compare().path("contentEqualUnderCandidatePresentation").booleanValue());
    }

    @Test
    void postgresArrayPolicyRejectsUnknownEncodingWrongScopeAndAnAbsentEmptyArray()
            throws Exception {
        final var fixture = new FaturasClienteContentFixture(directory, 1);
        final var policy = postgresPolicy(fixture);
        policy.putNull("arrayTextEncoding");
        assertThrows(IllegalArgumentException.class, fixture::compare);
        policy.put("arrayTextEncoding", "UNKNOWN");
        assertThrows(IllegalArgumentException.class, fixture::compare);
        policy.put("arrayTextEncoding", "POSTGRES_TEXT_ARRAY_LITERAL_V1");
        policy.put("emptyArrayPresentation", "EMPTY_ARRAY_NATIVE_ABSENT");
        assertThrows(IllegalArgumentException.class, fixture::compare);
        policy.put("emptyArrayPresentation", "STRICT");
        final var invoices = field(fixture, "invoices_mapping");
        invoices.put("arrayTextEncoding", "POSTGRES_TEXT_ARRAY_LITERAL_V1");
        assertThrows(IllegalArgumentException.class, fixture::compare);
        invoices.remove("arrayTextEncoding");
        field(fixture, "fit_pyr_name").put("arrayTextEncoding", "JOIN_LITERAL_V1");
        assertThrows(IllegalArgumentException.class, fixture::compare);
    }

    @Test
    void postgresArrayNullRemainsAnExplicitNullPolicyAndDoesNotBecomeAnEmptyArray()
            throws Exception {
        final var fixture = new FaturasClienteContentFixture(directory, 1);
        final var policy = postgresPolicy(fixture);
        fixture.records.get(0).putNull("fit_fte_invoices_order_number");
        setOrderCell(fixture, "{}");
        assertFalse(
                fixture.compare().path("contentEqualUnderCandidatePresentation").booleanValue());
        fixture.nativeRows.get(0).remove(coordinate(fixture, "fit_fte_invoices_order_number"));
        policy.put("nullPresentation", "NULL_NATIVE_ABSENT");
        assertTrue(fixture.compare().path("contentEqualUnderCandidatePresentation").booleanValue());
    }

    private static ObjectNode postgresPolicy(final FaturasClienteContentFixture fixture) {
        final var policy = field(fixture, "fit_fte_invoices_order_number");
        policy.put("arrayTextEncoding", "POSTGRES_TEXT_ARRAY_LITERAL_V1");
        policy.put("arraySeparator", ",");
        policy.put("emptyArrayPresentation", "STRICT");
        return policy;
    }

    private static void setOrderCell(
            final FaturasClienteContentFixture fixture, final String value) {
        fixture.nativeRows
                .get(0)
                .put(
                        coordinate(fixture, "fit_fte_invoices_order_number"),
                        FaturasClienteContentFixture.text(value));
    }

    private static int coordinate(final FaturasClienteContentFixture fixture, final String source) {
        return field(fixture, source).path("nativeColumn").intValue();
    }

    private static ObjectNode field(
            final FaturasClienteContentFixture fixture, final String source) {
        for (final var field : fixture.catalog.required("columns")) {
            if (source.equals(field.path("sourceField").textValue())) {
                return (ObjectNode) field;
            }
        }
        throw new IllegalArgumentException("AUTHORED_FIELD_NOT_FOUND");
    }
}
