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
import java.util.List;
import org.junit.jupiter.api.Test;

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

    private static JsonNode node(final String document) {
        try {
            return JSON.readTree(document);
        } catch (final JsonProcessingException exception) {
            throw new AssertionError(exception);
        }
    }
}
