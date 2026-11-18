package br.com.esl.etl.v2.plataforma.reconciliacao.exportacao;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** End-to-end public adapter tests, with authored JSONL and XLSX bytes only. Not executed here. */
class ColetasCommonAdapterContract31DraftTest {
    @TempDir Path directory;

    private ColetasCommon31SyntheticFixture fixture() {
        return new ColetasCommon31SyntheticFixture(directory);
    }

    private static void equalPair(JsonNode result) {
        assertEquals("CONTENT_EQUAL_FOR_THIS_PAIR", result.path("status").asText());
        assertEquals(0, result.path("pendingCellDecisions").asLong());
        assertTrue(result.path("allOccurrencesCompared").asBoolean());
        assertEquals(31, result.path("comparison").path("columnsEqual").size());
        for (JsonNode column : result.path("comparison").path("columnsEqual")) {
            assertTrue(column.asBoolean());
        }
        assertTrue(result.path("comparison").path("fullTuplesEqual").asBoolean());
    }

    private static void pending(JsonNode result) {
        assertEquals("PENDING_FIELD_REPRESENTATION_EVIDENCE", result.path("status").asText());
        assertTrue(result.path("pendingCellDecisions").asLong() > 0);
        assertFalse(result.path("allOccurrencesCompared").asBoolean());
    }

    @Test
    void comparesEveryOneOfThe31CommonFieldsWithoutGrantingPublication() throws Exception {
        JsonNode result = fixture().compare();
        equalPair(result);
        assertEquals(1, result.path("sourceRows").asLong());
        assertEquals(1, result.path("uiRows").asLong());
        assertFalse(result.path("snapshotIdentityProven").asBoolean());
        assertFalse(result.path("semanticParityApproved").asBoolean());
        assertFalse(result.path("presentationEvidenceAuthenticationVerified").asBoolean());
        assertFalse(result.path("mainResumeAllowed").asBoolean());
        assertFalse(result.path("dashboardConsumptionVerified").asBoolean());
        assertFalse(result.path("deduplicationPerformed").asBoolean());
    }

    @Test
    void permitsExtraApiFieldsAndPreservesTheirPinnedBytesAndFractions() throws Exception {
        var f = fixture();
        f.source
                .putObject("synthetic_api_extension")
                .put("do_not_drop", "SYNTHETIC EXTRA")
                .putNull("explicit_null");
        equalPair(f.compare());
        assertArrayEquals(f.writtenSource, Files.readAllBytes(f.sourcePin.path()));
        JsonNode retained = ExportValidationSupport.JSON.readTree(f.writtenSource);
        assertEquals(
                "SYNTHETIC EXTRA",
                retained.path("synthetic_api_extension").path("do_not_drop").asText());
        assertEquals("2036-03-20T10:11:12.123456789-03:00", retained.path("created_at").asText());
        assertTrue(retained.path("synthetic_api_extension").path("explicit_null").isNull());
    }

    @Test
    void neverSubstitutesANestedLookalikeForTheDeclaredFlatSourcePath() throws Exception {
        var f = fixture();
        f.source.remove("pck_cor_nickname");
        f.source.putObject("pck_cor").put("nickname", "SYNTHETIC-8");
        JsonNode result = f.compare();
        assertFalse(result.path("comparison").path("columnsEqual").get(7).asBoolean());
        assertFalse(result.path("comparison").path("fullTuplesEqual").asBoolean());
    }

    @Test
    void detectsARegressionInTheLastCommonColumn() throws Exception {
        var f = fixture();
        f.row.set(
                30, ColetasCommon31SyntheticFixture.Cell.text("DIFFERENT SYNTHETIC VEHICLE TYPE"));
        JsonNode result = f.compare();
        assertEquals(
                "PENDING_DIFFERENCE_INVESTIGATION_AND_NEW_PAIR", result.path("status").asText());
        assertFalse(result.path("comparison").path("columnsEqual").get(30).asBoolean());
    }

    @Test
    void absentAndNullStayDifferentWithoutAFieldSpecificOmissionDecision() throws Exception {
        var f = fixture();
        f.source.putNull("pck_cor_nickname");
        f.row.set(7, null);
        JsonNode result = f.compare();
        assertTrue(result.path("allOccurrencesCompared").asBoolean());
        assertFalse(result.path("comparison").path("columnsEqual").get(7).asBoolean());
    }

    @Test
    void theSameAbsentTextFieldCanCompareAsAbsentOnBothSides() throws Exception {
        var f = fixture();
        f.source.remove("pck_cor_nickname");
        f.row.set(7, null);
        equalPair(f.compare());
    }

    @Test
    void nullOmissionAppliesOnlyToTheNamedField() throws Exception {
        var f = fixture();
        f.source.putNull("pck_cor_nickname");
        f.row.set(7, null);
        f.policy.withArray("nullOmissionItems").add("item_123130");
        equalPair(f.compare());
        f.source.putNull("pck_crn_psn_nickname");
        f.row.set(1, null);
        JsonNode result = f.compare();
        assertTrue(result.path("comparison").path("columnsEqual").get(7).asBoolean());
        assertFalse(result.path("comparison").path("columnsEqual").get(1).asBoolean());
    }

    @Test
    void anIdentityCanNeverBeAddedToTheNullOmissionPolicy() {
        var f = fixture();
        f.policy.withArray("nullOmissionItems").add("item_221149");
        assertThrows(IllegalArgumentException.class, f::compare);
    }

    @Test
    void zeroVolumesAreValuesAndDoNotBecomeNull() throws Exception {
        var f = fixture();
        f.source.put("invoices_volumes", 0);
        f.row.set(8, ColetasCommon31SyntheticFixture.Cell.number("0"));
        equalPair(f.compare());
        f.source.putNull("invoices_volumes");
        assertFalse(f.compare().path("comparison").path("columnsEqual").get(8).asBoolean());
    }

    @Test
    void physicallyBlankTextIsNotAnAbsentCellOrAnEmptyString() throws Exception {
        var f = fixture();
        f.source.put("pck_cor_nickname", "");
        f.row.set(7, ColetasCommon31SyntheticFixture.Cell.blank(0));
        assertFalse(f.compare().path("comparison").path("columnsEqual").get(7).asBoolean());
        f.row.set(7, ColetasCommon31SyntheticFixture.Cell.text(""));
        equalPair(f.compare());
    }

    @Test
    void blankDatesNeedBothAFieldDecisionAndTheExactCarrier() throws Exception {
        var f = fixture();
        f.source.putNull("service_date");
        f.row.set(5, ColetasCommon31SyntheticFixture.Cell.blank(1));
        assertFalse(f.compare().path("comparison").path("columnsEqual").get(5).asBoolean());
        f.policy.withArray("dateBlankItems").add("item_123121");
        equalPair(f.compare());
        f.row.set(5, ColetasCommon31SyntheticFixture.Cell.blank(0));
        assertFalse(f.compare().path("comparison").path("columnsEqual").get(5).asBoolean());
    }

    @Test
    void duplicateOccurrencesAreCountedAndAMissingDuplicateIsDetected() throws Exception {
        var f = fixture();
        f.sources.add(f.source.deepCopy());
        f.rows.add(new ArrayList<>(f.row));
        equalPair(f.compare());
        f.rows.remove(1);
        JsonNode result = f.compare();
        assertEquals(2, result.path("sourceRows").asLong());
        assertEquals(1, result.path("uiRows").asLong());
        assertEquals(1, result.path("comparison").path("apiOnlyOccurrences").asLong());
        assertEquals(1, result.path("comparison").path("changedMultiplicityIds").asLong());
        assertFalse(result.path("deduplicationPerformed").asBoolean());
    }

    @Test
    void matchingColumnMarginalsCannotHideAnIncorrectAssociationBetweenIds() throws Exception {
        var f = fixture();
        var secondSource =
                f.source.deepCopy().put("id", 202).put("pck_cor_nickname", "SYNTHETIC CLIENT B");
        f.sources.add(secondSource);
        var secondNative = new ArrayList<>(f.row);
        secondNative.set(0, ColetasCommon31SyntheticFixture.Cell.number("202"));
        secondNative.set(7, ColetasCommon31SyntheticFixture.Cell.text("SYNTHETIC-8"));
        f.row.set(7, ColetasCommon31SyntheticFixture.Cell.text("SYNTHETIC CLIENT B"));
        f.rows.add(secondNative);
        JsonNode result = f.compare();
        assertTrue(result.path("comparison").path("columnsEqual").get(7).asBoolean());
        assertEquals(2, result.path("comparison").path("changedIds").asLong());
        assertFalse(result.path("comparison").path("fullTuplesEqual").asBoolean());
    }

    @Test
    void aKnownActionUsesTheLiteralDisplayAndNeverHtmlEntitySubstitution() throws Exception {
        var f = fixture();
        f.source.put("pck_pus_ore_trigger", "finish");
        f.row.set(22, ColetasCommon31SyntheticFixture.Cell.text("Baixar Entrega/Coleta"));
        equalPair(f.compare());
        f.row.set(22, ColetasCommon31SyntheticFixture.Cell.text("Baixar&nbsp;Entrega/Coleta"));
        assertFalse(f.compare().path("comparison").path("columnsEqual").get(22).asBoolean());
    }

    @Test
    void aKnownStatusCannotUseAnUnrelatedDisplayLabel() throws Exception {
        var f = fixture();
        f.source.put("status", "in_transit");
        f.row.set(12, ColetasCommon31SyntheticFixture.Cell.text("Em trânsito"));
        equalPair(f.compare());
        f.row.set(12, ColetasCommon31SyntheticFixture.Cell.text("Pendente"));
        assertFalse(f.compare().path("comparison").path("columnsEqual").get(12).asBoolean());
    }

    @Test
    void anUnknownStatusStaysPendingInsteadOfInventingATranslation() throws Exception {
        var f = fixture();
        f.source.put("status", "SYNTHETIC_FUTURE_STATUS");
        pending(f.compare());
    }

    @Test
    void anUnknownActionStaysPendingInsteadOfReusingTheRawCodeAsDisplay() throws Exception {
        var f = fixture();
        f.source.put("pck_pus_ore_trigger", "SYNTHETIC_FUTURE_ACTION");
        pending(f.compare());
    }

    @Test
    void fractionalVolumesAreNotSilentlyRoundedToAnInteger() throws Exception {
        var f = fixture();
        f.source.put("invoices_volumes", new BigDecimal("2.5"));
        pending(f.compare());
    }

    @Test
    void textualVolumesAreNotCoercedToAnInteger() throws Exception {
        var f = fixture();
        f.source.put("invoices_volumes", "2");
        pending(f.compare());
    }

    @Test
    void aFractionalNativeVolumeCannotMatchAnIntegralSource() throws Exception {
        var f = fixture();
        f.row.set(8, ColetasCommon31SyntheticFixture.Cell.number("2.5"));
        pending(f.compare());
    }

    @Test
    void aNumericLookingStringIdentityDoesNotAcquireNumericIdentity() throws Exception {
        var f = fixture();
        f.source.put("id", "101");
        pending(f.compare());
    }

    @Test
    void twoDecimalDisplayPlacesDoNotAuthorizeRoundingSourceQuantities() throws Exception {
        var f = fixture();
        f.source.put("invoices_weight", new BigDecimal("1.250001"));
        JsonNode result = f.compare();
        assertFalse(result.path("comparison").path("columnsEqual").get(9).asBoolean());
        assertEquals(
                "PENDING_DIFFERENCE_INVESTIGATION_AND_NEW_PAIR", result.path("status").asText());
    }

    @Test
    void unverifiedNumericAndDateCarriersStayPending() throws Exception {
        var f = fixture();
        f.row.set(9, ColetasCommon31SyntheticFixture.Cell.number("1.25", 0));
        pending(f.compare());
        f.evidence
                .withObject("columnCarriers")
                .withArray("item_135308")
                .add("XLSX:1900:builtin:0:apply=1:base=0:fmtId=0");
        equalPair(f.compare());
        f.row.set(9, ColetasCommon31SyntheticFixture.Cell.number("1.25", 3));
        var date = f.row.get(5);
        f.row.set(5, ColetasCommon31SyntheticFixture.Cell.number(date.value(), 0));
        pending(f.compare());
    }

    @Test
    void scopedWholeSecondDisplayDoesNotRewriteSourceFractions() throws Exception {
        var f = fixture();
        equalPair(f.compare());
        assertArrayEquals(f.writtenSource, Files.readAllBytes(f.sourcePin.path()));
        assertTrue(
                new String(f.writtenSource, java.nio.charset.StandardCharsets.UTF_8)
                        .contains(".987654321-03:00"));
        f.policy.putArray("timestampIntegerSecondDisplayItems").add("item_135309");
        pending(f.compare());
    }

    @Test
    void anEquivalentUtcInstantCannotReplaceTheDeclaredLocalWallTimestamp() throws Exception {
        var f = fixture();
        f.source.put("created_at", "2036-03-20T13:11:12.123456789Z");
        pending(f.compare());
    }

    @Test
    void invalidCalendarDatesAndTheExcelPhantomDayStayPending() throws Exception {
        var f = fixture();
        f.source.put("service_date", "2036-02-30");
        pending(f.compare());
        f.source.put("service_date", "2036-03-21");
        f.row.set(5, ColetasCommon31SyntheticFixture.Cell.number("60", 1));
        pending(f.compare());
    }

    @Test
    void rowsOutsideTheRequestedPeriodAreRejected() {
        var f = fixture();
        f.source.put("request_date", "2036-03-27");
        assertThrows(IllegalArgumentException.class, f::compare);
    }

    @Test
    void theUnqualified1904CalendarIsRejected() {
        var f = fixture();
        f.epoch = "1904";
        assertThrows(IllegalArgumentException.class, f::compare);
    }

    @Test
    void swappedHeadersAndAnExtraNativeColumnAreRejected() {
        var f = fixture();
        java.util.Collections.swap(f.headers, 1, 7);
        assertThrows(IllegalArgumentException.class, f::compare);
        java.util.Collections.swap(f.headers, 1, 7);
        f.headers.add("SYNTHETIC EXTRA UI COLUMN");
        assertThrows(IllegalArgumentException.class, f::compare);
    }

    @Test
    void aPolicyFromAnotherPairAndEvidenceFromAnotherCatalogAreRejected() {
        var f = fixture();
        f.policyMutation = policy -> policy.put("sourceSha256", "0".repeat(64));
        assertThrows(IllegalArgumentException.class, f::compare);
        f.policyMutation = ignored -> {};
        f.evidenceMutation = evidence -> evidence.put("catalogSha256", "0".repeat(64));
        assertThrows(IllegalArgumentException.class, f::compare);
    }

    @Test
    void nonObjectRowsAndDuplicateJsonKeysAreRejected() {
        var f = fixture();
        f.rawOverride = "[]\n";
        assertThrows(IllegalArgumentException.class, f::compare);
        f.rawOverride = "{\"id\":101,\"id\":202,\"request_date\":\"2036-03-20\"}\n";
        assertThrows(com.fasterxml.jackson.core.JsonProcessingException.class, f::compare);
    }
}
