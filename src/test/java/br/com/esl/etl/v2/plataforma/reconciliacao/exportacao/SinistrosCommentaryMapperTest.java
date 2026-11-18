package br.com.esl.etl.v2.plataforma.reconciliacao.exportacao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.modulos.sinistros.aplicacao.SinistroDataExportMapper;
import br.com.esl.etl.v2.plataforma.expansao.ExpansionValue;
import br.com.esl.etl.v2.plataforma.expansao.ExpansionValue.Presence;
import br.com.esl.etl.v2.plataforma.expansao.ExpansionValue.Wire;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.ExpansionFieldParser;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Synthetic field-boundary regression; no provider data, Source admission or Native policy. */
class SinistrosCommentaryMapperTest {
    private static final String COMMENTARY = "insurance_claim_commentary";

    @Test
    void accepts1024AndObservedLongTextUpToTheExisting4000RawBoundWithoutChangingTheEnvelope()
            throws Exception {
        for (final int length : new int[] {1024, 1025, 1557, 4000}) {
            final String literal = "C".repeat(length);
            final var row = SinistrosCaptureFixture.row(1).put(COMMENTARY, literal);
            final var generic = ExpansionFieldParser.text(row, COMMENTARY);
            assertEquals(length <= 1024, generic.valid());
            final var result = assertAccepted(row, literal);
            if (length == 1024) {
                assertEquals(generic, result);
            }
        }
    }

    @Test
    void commentary4001StillThrowsTheExistingRawBoundRatherThanTruncatingOrQuarantining()
            throws Exception {
        final var row = SinistrosCaptureFixture.row(1).put(COMMENTARY, "C".repeat(4001));
        final String before = row.toString();
        final var envelope = ExportValidationSupport.JSON.createObjectNode();
        envelope.set("data", row);
        assertEquals(
                "EXP_RAW_BOUND",
                assertThrows(
                                IllegalArgumentException.class,
                                () -> new SinistroDataExportMapper().map(1, envelope))
                        .getMessage());
        assertEquals(
                "EXP_RAW_BOUND",
                assertThrows(
                                IllegalArgumentException.class,
                                () -> ExpansionFieldParser.text(row, COMMENTARY))
                        .getMessage());
        assertEquals(before, row.toString());
    }

    @Test
    void absentNullAndEmptyStringKeepTheirSeparateGenericPresenceWireAndRawSemantics()
            throws Exception {
        final var row = SinistrosCaptureFixture.row(1);
        row.remove(COMMENTARY);
        final var absent = mapCommentary(row);
        assertEquals(
                new ExpansionValue<String>(Presence.ABSENT, Wire.ABSENT, null, null, null), absent);
        assertEquals(ExpansionFieldParser.text(row, COMMENTARY), absent);
        row.putNull(COMMENTARY);
        final var nulled = mapCommentary(row);
        assertEquals(
                new ExpansionValue<String>(Presence.NULL, Wire.NULL, null, null, null), nulled);
        assertEquals(ExpansionFieldParser.text(row, COMMENTARY), nulled);
        row.put(COMMENTARY, "");
        final var empty = assertAccepted(row, "");
        assertEquals(ExpansionFieldParser.text(row, COMMENTARY), empty);
        assertEquals(Presence.VALUE, empty.presence());
        assertEquals(Wire.STRING, empty.wire());
    }

    @Test
    void numericCommentaryKeepsItsWireRawAndTypedQuarantineInsteadOfBecomingText()
            throws Exception {
        final var row = SinistrosCaptureFixture.row(1).put(COMMENTARY, 7);
        final String before = row.toString();
        final var envelope = ExportValidationSupport.JSON.createObjectNode();
        envelope.set("data", row);
        final var observation = new SinistroDataExportMapper().map(1, envelope);
        final var result = observation.insuranceClaimCommentary();
        assertFalse(observation.valid());
        assertFalse(result.valid());
        assertEquals(Presence.VALUE, result.presence());
        assertEquals(Wire.INTEGER, result.wire());
        assertEquals("7", result.raw());
        assertNull(result.value());
        assertEquals("INVALID_INSURANCE_CLAIM_COMMENTARY", result.issue());
        assertEquals(ExpansionFieldParser.text(row, COMMENTARY), result);
        assertEquals(before, row.toString());
    }

    @Test
    void everyOtherMappedTextFieldStillRejects1025WhileLongCommentaryRemainsValid()
            throws Exception {
        for (final String field :
                List.of(
                        "bo_number",
                        "brokers_case_number",
                        "customer_communication_contact_name",
                        "customer_communication_time",
                        "finished_commentary",
                        "icm_crn_psn_nickname",
                        "icm_dvr_iil_name",
                        "icm_fer_name",
                        "icm_fis_fit_pyr_nickname",
                        "icm_ttt_dealing_type",
                        "icm_ttt_ore_description",
                        "icm_ttt_solution_type",
                        "icm_vie_license_plate",
                        "informed_by",
                        "insurance_claim_location",
                        "internal_description",
                        "list_of_claimed_products",
                        "policy_number")) {
            final String literal = "F".repeat(1025);
            final var row =
                    SinistrosCaptureFixture.row(1)
                            .put(COMMENTARY, "C".repeat(1557))
                            .put(field, literal);
            final String before = row.toString();
            final var envelope = ExportValidationSupport.JSON.createObjectNode();
            envelope.set("data", row);
            final var observation = new SinistroDataExportMapper().map(1, envelope);
            assertFalse(observation.valid(), field);
            assertTrue(observation.insuranceClaimCommentary().valid(), field);
            final var generic = ExpansionFieldParser.text(row, field);
            assertFalse(generic.valid(), field);
            assertEquals(literal, generic.raw(), field);
            assertNull(generic.value(), field);
            assertEquals(before, row.toString(), field);
        }
    }

    @Test
    void supplementaryUnicodeCombiningMarksAndWhitespaceRemainExactAndUseUtf16Bounds()
            throws Exception {
        final String literal = " \tA\u0301" + "\uD83D\uDE00".repeat(1996) + " \r\n ";
        assertEquals(4000, literal.length());
        assertTrue(literal.codePointCount(0, literal.length()) < literal.length());
        final var row = SinistrosCaptureFixture.row(1).put(COMMENTARY, literal);
        final var result = assertAccepted(row, literal);
        assertEquals(literal, result.raw());
        assertEquals(literal, result.value());
        row.put(COMMENTARY, literal + "x");
        final var envelope = ExportValidationSupport.JSON.createObjectNode();
        envelope.set("data", row);
        assertEquals(
                "EXP_RAW_BOUND",
                assertThrows(
                                IllegalArgumentException.class,
                                () -> new SinistroDataExportMapper().map(1, envelope))
                        .getMessage());
    }

    private static ExpansionValue<String> assertAccepted(
            final ObjectNode row, final String literal) {
        final var envelope = ExportValidationSupport.JSON.createObjectNode();
        envelope.set("data", row);
        final String before = envelope.toString();
        final var observation = new SinistroDataExportMapper().map(1, envelope);
        assertTrue(observation.valid());
        final var result = observation.insuranceClaimCommentary();
        assertTrue(result.valid());
        assertEquals(Presence.VALUE, result.presence());
        assertEquals(Wire.STRING, result.wire());
        assertEquals(literal, result.raw());
        assertEquals(literal, result.value());
        assertNull(result.issue());
        assertEquals(before, envelope.toString());
        return result;
    }

    private static ExpansionValue<String> mapCommentary(final ObjectNode row) {
        final var envelope = ExportValidationSupport.JSON.createObjectNode();
        envelope.set("data", row);
        return new SinistroDataExportMapper().map(1, envelope).insuranceClaimCommentary();
    }
}
