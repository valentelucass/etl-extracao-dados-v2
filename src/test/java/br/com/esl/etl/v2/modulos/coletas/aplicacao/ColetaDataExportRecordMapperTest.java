package br.com.esl.etl.v2.modulos.coletas.aplicacao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.modulos.coletas.domain.ColetaAttributePresence;
import br.com.esl.etl.v2.modulos.coletas.domain.ColetaFreshnessOrigin;
import br.com.esl.etl.v2.modulos.coletas.domain.ColetaStageDisposition;
import br.com.esl.etl.v2.modulos.coletas.domain.ColetaStageRecord;
import br.com.esl.etl.v2.plataforma.identidade.ScopedSourceIdentity;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.TimeZone;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class ColetaDataExportRecordMapperTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private final ColetaDataExportRecordMapper mapper = new ColetaDataExportRecordMapper();

    @Test
    void mapsOneRecordWithTypedFreshnessStatusAndRelationCandidatesOnly() {
        final ColetaStageRecord record =
                mapper.map(
                        1,
                        node(
                                """
                                {"status":"done","sequence_code":70,"id":9,
                                 "status_updated_at":"invalid","finish_date":"2026-03-04",
                                 "cancellation_reason":"ignored by terminal",
                                 "manifesto":{"sequence_code":10},"pick_item_id":11,
                                 "pck_mik_mft_sequence_code":12}
                                """));

        assertEquals(ColetaStageDisposition.VALID, record.disposition());
        assertEquals(ScopedSourceIdentity.WireType.INTEGER, record.sourceKey().wireType());
        assertEquals("INTEGER:9", record.sourceKey().storageValue());
        assertEquals(ColetaAttributePresence.VALUE, record.sequenceCodePresence());
        assertEquals("70", record.sequenceCodeJson());
        assertEquals("done", record.status().code());
        assertEquals("Coletada", record.status().label());
        assertTrue(record.status().terminal());
        assertEquals("Coleta Realizada", record.status().occurrenceAction());
        assertEquals(1, record.status().attempts());
        assertEquals("invalid", record.freshnessRaw());
        assertEquals(ColetaFreshnessOrigin.FINISH_DATE, record.freshnessOrigin());
        assertEquals("2026-03-04T03:00:00Z", record.freshnessAtUtc().toString());
        assertTrue(record.fieldPresenceJson().contains("\"status\":\"VALUE\""));
        assertTrue(record.relationCandidatesJson().contains("\"pick_item_id\""));
        assertFalse(record.relationCandidatesJson().contains("canonical"));
        assertTrue(record.payloadJson().startsWith("{\"cancellation_reason\""));
    }

    @Test
    void preservesAbsentAndUnknownFieldsWithoutInventingStatusOrFreshness() {
        final ColetaStageRecord record =
                mapper.map(2, node("{" + "\"id\":10,\"status\":\"future\"}"));

        assertEquals(ColetaStageDisposition.VALID, record.disposition());
        assertEquals(ColetaAttributePresence.ABSENT, record.sequenceCodePresence());
        assertNull(record.sequenceCodeJson());
        assertNull(record.status().code());
        assertNull(record.status().label());
        assertFalse(record.status().terminal());
        assertEquals("Pendente", record.status().occurrenceAction());
        assertEquals(ColetaFreshnessOrigin.UNAVAILABLE, record.freshnessOrigin());
        assertNull(record.freshnessAtUtc());
        assertTrue(record.fieldPresenceJson().contains("\"sequence_code\":\"ABSENT\""));
    }

    @Test
    void quarantinesInvalidIdentityAndTypedFieldsWithoutLeakingValues() {
        final List<ColetaStageRecord> records =
                List.of(
                        mapper.map(1, node("{}")),
                        mapper.map(2, node("{\"id\":\"9\"}")),
                        mapper.map(3, node("{\"id\":9,\"sequence_code\":\"70\"}")),
                        mapper.map(4, node("{\"id\":9,\"status\":7}")),
                        mapper.map(5, node("{\"id\":9,\"cancellation_reason\":false}")));

        assertEquals("MISSING_SOURCE_KEY", records.get(0).quarantineReasonCode());
        assertEquals("INVALID_SOURCE_KEY_TYPE", records.get(1).quarantineReasonCode());
        assertEquals("INVALID_SEQUENCE_CODE_TYPE", records.get(2).quarantineReasonCode());
        assertEquals("INVALID_STATUS_TYPE", records.get(3).quarantineReasonCode());
        assertEquals("INVALID_CANCELLATION_REASON_TYPE", records.get(4).quarantineReasonCode());
        for (final ColetaStageRecord record : records) {
            assertEquals(ColetaStageDisposition.QUARANTINE, record.disposition());
            assertTrue(record.toString().contains("<redacted>"));
            assertFalse(record.toString().contains("INTEGER:9"));
        }
    }

    @Test
    void preservesZeroAndNegativeIntegralIdsWithoutCollapsingZeroValues() {
        final ColetaStageRecord zero =
                mapper.map(1, node("{\"id\":0,\"sequence_code\":0,\"pick_item_id\":0}"));
        final ColetaStageRecord negative = mapper.map(2, node("{\"id\":-12}"));

        assertEquals(ColetaStageDisposition.VALID, zero.disposition());
        assertEquals("INTEGER:0", zero.sourceKey().storageValue());
        assertEquals(ColetaAttributePresence.VALUE, zero.sequenceCodePresence());
        assertEquals("0", zero.sequenceCodeJson());
        assertTrue(zero.relationCandidatesJson().contains("\"value\":0"));
        assertEquals(ColetaStageDisposition.VALID, negative.disposition());
        assertEquals("INTEGER:-12", negative.sourceKey().storageValue());
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "2018-11-04T00:30:00", "2018-11-04 00:30:00", "04/11/2018 00:30:00",
                "04/11/2018 0:30:00", "2019-02-16T23:30:00", "16/02/2019 23:30:00"
            })
    void ambiguousOrNonexistentLocalTimePreservesRawAndUsesValidFallback(final String raw) {
        final var input =
                JSON.createObjectNode()
                        .put("id", 1)
                        .put("status_updated_at", raw)
                        .put("finish_date", "2026-09-09");
        final var record = mapper.map(1, input);
        assertEquals(raw, record.freshnessRaw());
        assertEquals(
                "VALUE", node(record.fieldPresenceJson()).path("status_updated_at").textValue());
        assertEquals(input, node(record.payloadJson()));
        assertEquals(ColetaFreshnessOrigin.FINISH_DATE, record.freshnessOrigin());
        assertEquals(Instant.parse("2026-09-09T03:00:00Z"), record.freshnessAtUtc());
        input.remove("finish_date");
        assertEquals(ColetaFreshnessOrigin.UNAVAILABLE, mapper.map(1, input).freshnessOrigin());
    }

    @ParameterizedTest
    @CsvSource({
        "2019-02-16T23:30:00-02:00,2019-02-17T01:30:00Z",
        "2019-02-16T23:30:00-03:00,2019-02-17T02:30:00Z",
        "2018-11-04T00:30:00-03:00,2018-11-04T03:30:00Z",
        "2026-09-09T23:30:00-03:00,2026-09-10T02:30:00Z",
        "2026-09-10T02:30:00Z,2026-09-10T02:30:00Z",
        "2026-09-09T23:30:00,2026-09-10T02:30:00Z",
        "2026-09-09 23:30:00,2026-09-10T02:30:00Z",
        "09/09/2026 9:30:00,2026-09-09T12:30:00Z",
        "09/09/2026 09:30:00,2026-09-09T12:30:00Z"
    })
    void explicitInstantOrUniqueLocalTimeWinsOverLaterBusinessDate(
            final String raw, final String utc) {
        final var record =
                mapper.map(
                        1,
                        JSON.createObjectNode()
                                .put("id", 1)
                                .put("status_updated_at", raw)
                                .put("finish_date", "2036-03-20"));
        assertEquals(ColetaFreshnessOrigin.STATUS_UPDATED_AT, record.freshnessOrigin());
        assertEquals(Instant.parse(utc), record.freshnessAtUtc());
        assertEquals(raw, record.freshnessRaw());
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "status_updated_at",
                "finish_date",
                "service_date",
                "request_date",
                "updated_at"
            })
    void absentNullEmptyInvalidAndValueRemainDistinct(final String field) {
        for (final String member : List.of("", "null", "\"\"", "\"invalid\"", "\"2026-09-09\"")) {
            final var input =
                    node(
                            "{\"id\":1"
                                    + (member.isEmpty() ? "" : ",\"" + field + "\":" + member)
                                    + "}");
            final var record = mapper.map(1, input);
            final var preserved = node(record.payloadJson());
            assertEquals(input, preserved);
            if (!field.equals("updated_at")) {
                assertEquals(
                        member.isEmpty() ? "ABSENT" : member.equals("null") ? "NULL" : "VALUE",
                        node(record.fieldPresenceJson()).path(field).textValue());
            }
            final boolean civilValue = member.equals("\"2026-09-09\"") && field.endsWith("_date");
            assertEquals(
                    civilValue ? Instant.parse("2026-09-09T03:00:00Z") : null,
                    record.freshnessAtUtc());
            if (field.equals("status_updated_at")) {
                assertEquals(
                        input.path(field).isTextual() ? input.path(field).textValue() : null,
                        record.freshnessRaw());
            }
        }
    }

    @Test
    void businessDateUsesFirstValidTimeAndFallbackOrderIsNotMaximumTimestamp() {
        final var input =
                JSON.createObjectNode()
                        .put("id", 1)
                        .put("status_updated_at", "bad")
                        .put("finish_date", "2018-11-04")
                        .put("service_date", "2026-09-10")
                        .put("request_date", "2036-03-20")
                        .put("updated_at", "2099-01-01T00:00:00Z");
        assertEquals(Instant.parse("2018-11-04T03:00:00Z"), mapper.map(1, input).freshnessAtUtc());
        assertEquals(ColetaFreshnessOrigin.FINISH_DATE, mapper.map(1, input).freshnessOrigin());
        input.put("finish_date", "2026-02-29");
        assertEquals(ColetaFreshnessOrigin.SERVICE_DATE, mapper.map(1, input).freshnessOrigin());
        assertEquals(Instant.parse("2026-09-10T03:00:00Z"), mapper.map(1, input).freshnessAtUtc());
        input.put("service_date", "2026-04-31");
        assertEquals(ColetaFreshnessOrigin.REQUEST_DATE, mapper.map(1, input).freshnessOrigin());
        input.putNull("request_date");
        assertEquals(ColetaFreshnessOrigin.UNAVAILABLE, mapper.map(1, input).freshnessOrigin());
    }

    @Test
    void hostTimezoneDoesNotChangeCivilDateOrUniqueLocalInstant() {
        final TimeZone previous = TimeZone.getDefault();
        try {
            for (final String host : List.of("UTC", "Asia/Tokyo", "Pacific/Honolulu")) {
                TimeZone.setDefault(TimeZone.getTimeZone(host));
                assertEquals(
                        Instant.parse("2026-09-09T03:00:00Z"),
                        mapper.map(1, node("{\"id\":1,\"finish_date\":\"2026-09-09\"}"))
                                .freshnessAtUtc());
                assertEquals(
                        Instant.parse("2026-09-09T12:00:00Z"),
                        mapper.map(
                                        1,
                                        node(
                                                "{\"id\":1,\"status_updated_at\":\"2026-09-09T09:00:00\"}"))
                                .freshnessAtUtc());
            }
        } finally {
            TimeZone.setDefault(previous);
        }
    }

    @Test
    void javaRetainsSubMillisecondOrderingAndEqualInstantDoesNotMergeStatuses() {
        final var first =
                mapper.map(
                        1,
                        node(
                                "{\"id\":1,\"status\":\"pending\",\"status_updated_at\":\"2026-09-09T12:00:00.123100Z\"}"));
        final var later =
                mapper.map(
                        2,
                        node(
                                "{\"id\":1,\"status\":\"done\",\"status_updated_at\":\"2026-09-09T12:00:00.123400Z\"}"));
        assertEquals(123100000, first.freshnessAtUtc().getNano());
        assertEquals(123400000, later.freshnessAtUtc().getNano());
        assertTrue(later.freshnessAtUtc().isAfter(first.freshnessAtUtc()));
        assertTrue(first.freshnessAtUtc().isBefore(later.freshnessAtUtc()));
        final var equal =
                mapper.map(
                        1,
                        node(
                                "{\"id\":1,\"status\":\"canceled\",\"status_updated_at\":\"2026-09-09T09:00:00.123400-03:00\"}"));
        assertEquals(later.freshnessAtUtc(), equal.freshnessAtUtc());
        assertEquals("done", later.status().code());
        assertEquals("canceled", equal.status().code());
        assertTrue(equal.status().terminal());
        assertFalse(first.status().terminal());
    }

    private static JsonNode node(final String document) {
        try {
            return JSON.readTree(document);
        } catch (final JsonProcessingException exception) {
            throw new AssertionError(exception);
        }
    }
}
