package br.com.esl.etl.v2.plataforma.fonte.dataexport;

import static br.com.esl.etl.v2.plataforma.fonte.dataexport.ObservedExpansionCoreFixtures.field;
import static br.com.esl.etl.v2.plataforma.fonte.dataexport.ObservedExpansionCoreFixtures.json;
import static br.com.esl.etl.v2.plataforma.fonte.dataexport.ObservedExpansionCoreFixtures.jsonField;
import static br.com.esl.etl.v2.plataforma.fonte.dataexport.ObservedExpansionCoreFixtures.plan;
import static br.com.esl.etl.v2.plataforma.fonte.dataexport.ObservedExpansionCoreFixtures.raw;
import static br.com.esl.etl.v2.plataforma.fonte.dataexport.ObservedExpansionCoreFixtures.rows;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.plataforma.expansao.ExpansionStrings;
import br.com.esl.etl.v2.plataforma.expansao.ExpansionValue;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;

final class ObservedExpansionPageProjectorTest {
    @Test
    void allFourMappersCrossBlocksAndNonemptyPagesWithoutCollidingOccurrences() {
        for (final var template : ObservedExpansionCaptureTemplate.values()) {
            final var plan = plan(template);
            final var projector = new ObservedExpansionPageProjector(plan);
            final var first = projector.projectNext(raw(plan, 1, rows("{}", 201)));
            final var second = projector.projectNext(raw(plan, 2, rows("{}", 17)));
            assertEquals(201, first.physicalRows().orElseThrow());
            assertEquals(17, second.physicalRows().orElseThrow());
            assertEquals(201L, first.lastOccurrence().orElseThrow());
            assertEquals(202L, second.firstOccurrence().orElseThrow());
            assertEquals(218L, second.lastOccurrence().orElseThrow());
            for (int i = 0; i < 201; i++) {
                assertEquals(i + 1L, first.records().get(i).occurrence());
                assertEquals(i + 1, first.records().get(i).pageOrdinal());
                assertNull(first.records().get(i).captured().binding());
            }
            final var terminal = projector.projectNext(raw(plan, 3, "[]"));
            assertEquals(0, terminal.physicalRows().orElseThrow());
            assertTrue(terminal.firstOccurrence().isEmpty());
            assertEquals(219L, projector.nextOccurrence());
            assertEquals(ObservedExpansionPageProjector.State.LOCAL_TERMINAL, projector.state());
            assertThrows(
                    IllegalStateException.class, () -> projector.projectNext(raw(plan, 4, "[]")));
        }
    }

    @Test
    void identicalRowsRemainSeparateAndNoTechnicalKeyEntersRawBody() {
        final var plan = plan(ObservedExpansionCaptureTemplate.SIN);
        final String source =
                rows("{\"sequence_code\":7,\"insurance_claim_commentary\":\"same\"}", 3);
        final var page = new ObservedExpansionPageProjector(plan).projectNext(raw(plan, 1, source));
        assertEquals(3, page.records().size());
        assertEquals(3L, page.lastOccurrence().orElseThrow());
        assertArrayEquals(source.getBytes(StandardCharsets.UTF_8), page.rawPage().body());
        assertFalse(
                new String(page.rawPage().body(), StandardCharsets.UTF_8)
                        .contains("capture_occurrence"));
        assertFalse(
                new String(page.rawPage().body(), StandardCharsets.UTF_8)
                        .contains("FIXTURE_SINTETICA"));
        for (final var record : page.records()) {
            assertEquals("7", field(record, "sequence_code").value().value());
            assertEquals("UNBOUND", record.bindingStatus());
        }
    }

    @Test
    void sixtySyntheticSinistrosKeepSevenLongCommentsWithRawAndIssue() throws Exception {
        final var plan = plan(ObservedExpansionCaptureTemplate.SIN);
        final var body = new StringBuilder("[");
        final var expected = new java.util.ArrayList<String>();
        for (int i = 0; i < 60; i++) {
            final String text = i < 7 ? "x".repeat(i == 6 ? 2241 : 1025 + i) : "ok";
            expected.add(text);
            if (i > 0) {
                body.append(',');
            }
            body.append("{\"insurance_claim_commentary\":\"").append(text).append("\"}");
        }
        body.append(']');
        final var page =
                new ObservedExpansionPageProjector(plan).projectNext(raw(plan, 1, body.toString()));
        assertEquals(60, page.physicalRows().orElseThrow());
        assertEquals(7, page.fieldIssueRows());
        assertEquals(ObservedExpansionPageProjection.Status.MAPPED, page.status());
        for (int i = 0; i < 60; i++) {
            final var record = page.records().get(i);
            final var field = field(record, "insurance_claim_commentary");
            assertEquals(expected.get(i), field.value().raw());
            assertEquals("UNBOUND", record.bindingStatus());
            if (i < 7) {
                assertNull(field.value().value());
                assertEquals("INVALID_INSURANCE_CLAIM_COMMENTARY", field.value().issue());
            } else {
                assertEquals(expected.get(i), field.value().value());
                assertNull(field.value().issue());
            }
        }
        assertEquals(60, json(page).path("records").size());
        assertEquals("NOT_PERFORMED_BY_CORE", page.sourceSchemaValidation());
    }

    @Test
    void typedCommentBoundaryDoesNotExpandAndRawBoundaryDoesNotTruncate() {
        for (final int length : new int[] {1024, 1025, 4000}) {
            final var plan = plan(ObservedExpansionCaptureTemplate.SIN);
            final var page =
                    new ObservedExpansionPageProjector(plan)
                            .projectNext(
                                    raw(
                                            plan,
                                            1,
                                            "[{\"insurance_claim_commentary\":\""
                                                    + "x".repeat(length)
                                                    + "\"}]"));
            final var value = field(page.records().get(0), "insurance_claim_commentary").value();
            assertEquals(length, value.raw().length());
            assertEquals(length == 1024, value.valid());
            assertEquals(length == 1024, value.value() != null);
        }
    }

    @Test
    void absentNullAndValueRemainDifferentInFieldsAndSidecar() throws Exception {
        final var plan = plan(ObservedExpansionCaptureTemplate.CAP);
        final var page =
                new ObservedExpansionPageProjector(plan)
                        .projectNext(
                                raw(plan, 1, "[{}, {\"comments\":null}, {\"comments\":\"\"}]"));
        assertEquals(
                ExpansionValue.Presence.ABSENT,
                field(page.records().get(0), "comments").value().presence());
        assertEquals(
                ExpansionValue.Presence.NULL,
                field(page.records().get(1), "comments").value().presence());
        assertEquals(
                ExpansionValue.Presence.VALUE,
                field(page.records().get(2), "comments").value().presence());
        assertNull(field(page.records().get(0), "comments").value().raw());
        assertEquals("", field(page.records().get(2), "comments").value().raw());
        assertEquals(3, json(page).path("records").size());
        assertTrue(jsonField(page, "comments").has("issue"));
        assertTrue(jsonField(page, "comments").path("issue").isNull());
    }

    @Test
    void orderedArrayDuplicatesSurviveBothRawAndTypedRepresentations() throws Exception {
        final var plan = plan(ObservedExpansionCaptureTemplate.INV);
        final var page =
                new ObservedExpansionPageProjector(plan)
                        .projectNext(
                                raw(
                                        plan,
                                        1,
                                        "[{\"cnr_c_s_fit_invoices_mapping\":[\"B\",\"A\",\"A\"]}]"));
        final var field = field(page.records().get(0), "cnr_c_s_fit_invoices_mapping");
        assertEquals(
                java.util.List.of("B", "A", "A"),
                ((ExpansionStrings) field.value().value()).items());
        final var typed = jsonField(page, "cnr_c_s_fit_invoices_mapping").path("typed");
        assertEquals(3, typed.size());
        assertEquals("B", typed.get(0).asText());
        assertEquals("A", typed.get(1).asText());
        assertEquals("A", typed.get(2).asText());
    }

    @Test
    void invalidArrayElementIsAnIssueWithoutDiscardingPhysicalRow() throws Exception {
        final var plan = plan(ObservedExpansionCaptureTemplate.FAT);
        final var page =
                new ObservedExpansionPageProjector(plan)
                        .projectNext(raw(plan, 1, "[{\"invoices_mapping\":[\"A\",4]}]"));
        assertEquals(1, page.physicalRows().orElseThrow());
        final var value = field(page.records().get(0), "invoices_mapping").value();
        assertEquals("[\"A\",4]", value.raw());
        assertNull(value.value());
        assertEquals("INVALID_INVOICES_MAPPING", value.issue());
        assertEquals("UNBOUND", page.records().get(0).bindingStatus());
        assertTrue(jsonField(page, "invoices_mapping").path("typed").isNull());
    }

    @Test
    void decimalScaleAndLargeIdentifierDoNotPassThroughFloatingPoint() throws Exception {
        final var cap = plan(ObservedExpansionCaptureTemplate.CAP);
        final var decimal =
                new ObservedExpansionPageProjector(cap)
                        .projectNext(raw(cap, 1, "[{\"value\":0.1000}]"));
        assertEquals(
                new BigDecimal("0.10000000"),
                field(decimal.records().get(0), "value").value().value());
        assertEquals("0.1000", field(decimal.records().get(0), "value").value().raw());
        assertEquals("0.10000000", jsonField(decimal, "value").path("typed").asText());
        assertTrue(jsonField(decimal, "value").path("typed").isTextual());
        final var fat = plan(ObservedExpansionCaptureTemplate.FAT);
        final var identifier =
                new ObservedExpansionPageProjector(fat)
                        .projectNext(raw(fat, 1, "[{\"id\":9007199254740993}]"));
        assertEquals("9007199254740993", jsonField(identifier, "id").path("typed").asText());
        assertEquals("UNBOUND", identifier.records().get(0).bindingStatus());
    }

    @Test
    void sourceClockKeepsItsTimeAndOriginalDateOffsetAndPrecision() throws Exception {
        final var plan = plan(ObservedExpansionCaptureTemplate.SIN);
        final String sourceTime = "2026-10-17T08:09:10.1234567-03:00";
        final var page =
                new ObservedExpansionPageProjector(plan)
                        .projectNext(
                                raw(plan, 1, "[{\"finished_at_time\":\"" + sourceTime + "\"}]"));
        final var value = field(page.records().get(0), "finished_at_time").value();
        assertEquals(sourceTime, value.raw());
        assertEquals(LocalTime.of(8, 9, 10, 123456700), value.value());
        assertEquals(
                LocalTime.of(8, 9, 10, 123456700).toString(),
                jsonField(page, "finished_at_time").path("typed").asText());
    }

    @Test
    void equalTreesDoNotReplaceDistinctOriginalLexicalBodies() {
        final String escaped = "[ { \"comments\" : \"\\u00e9\" } ]";
        final String literal = "[{\"comments\":\"é\"}]";
        final var p1 = plan(ObservedExpansionCaptureTemplate.CAP);
        final var p2 = plan(ObservedExpansionCaptureTemplate.CAP);
        final var a = new ObservedExpansionPageProjector(p1).projectNext(raw(p1, 1, escaped));
        final var b = new ObservedExpansionPageProjector(p2).projectNext(raw(p2, 1, literal));
        assertEquals("é", field(a.records().get(0), "comments").value().raw());
        assertEquals(
                field(a.records().get(0), "comments").value(),
                field(b.records().get(0), "comments").value());
        assertArrayEquals(escaped.getBytes(StandardCharsets.UTF_8), a.rawPage().body());
        assertArrayEquals(literal.getBytes(StandardCharsets.UTF_8), b.rawPage().body());
        assertFalse(java.util.Arrays.equals(a.rawPage().body(), b.rawPage().body()));
    }

    @Test
    void escapedUnpairedSurrogateIsLosslessInUtf16Sidecar() throws Exception {
        final var plan = plan(ObservedExpansionCaptureTemplate.CAP);
        final var page =
                new ObservedExpansionPageProjector(plan)
                        .projectNext(raw(plan, 1, "[{\"comments\":\"\\ud800\"}]"));
        final String expected = String.valueOf((char) 0xd800);
        assertEquals(expected, field(page.records().get(0), "comments").value().raw());
        assertEquals(expected, jsonField(page, "comments").path("raw").asText());
        assertEquals(
                page.projectionJson(),
                new String(page.projectionUtf16Le(), StandardCharsets.UTF_16LE));
        assertEquals(page.projectionByteLength(), page.projectionUtf16Le().length);
    }

    @Test
    void unknownFieldsStayInRawWithoutClaimingObservedSchemaApproval() {
        final var plan = plan(ObservedExpansionCaptureTemplate.CAP);
        final String source = "[{\"unknown_nested\":{\"items\":[1,1]}}]";
        final var page = new ObservedExpansionPageProjector(plan).projectNext(raw(plan, 1, source));
        assertArrayEquals(source.getBytes(StandardCharsets.UTF_8), page.rawPage().body());
        assertEquals("NOT_PERFORMED_BY_CORE", page.sourceSchemaValidation());
        assertEquals("NOT_PERFORMED_BY_CORE", plan.sourceSchemaValidation());
    }

    @Test
    void projectionAndRawGettersCannotMutateStoredResultsOrLeakViaToString() {
        final var plan = plan(ObservedExpansionCaptureTemplate.CAP);
        final String secretFixture = "OFFLINE_SENSITIVE_TEXT";
        final byte[] bytes =
                ("[{\"comments\":\"" + secretFixture + "\"}]").getBytes(StandardCharsets.UTF_8);
        final byte[] expected = bytes.clone();
        final var raw =
                new ObservedExpansionRawPage(plan.request(1), java.time.Instant.EPOCH, bytes);
        bytes[0] = 0;
        raw.body()[0] = 0;
        final var page = new ObservedExpansionPageProjector(plan).projectNext(raw);
        assertArrayEquals(expected, raw.body());
        assertThrows(UnsupportedOperationException.class, () -> page.records().clear());
        assertThrows(
                UnsupportedOperationException.class, () -> page.records().get(0).fields().clear());
        assertFalse(raw.toString().contains(secretFixture));
        assertFalse(page.toString().contains(secretFixture));
        assertFalse(page.records().get(0).toString().contains(secretFixture));
        assertFalse(field(page.records().get(0), "comments").toString().contains(secretFixture));
    }
}
