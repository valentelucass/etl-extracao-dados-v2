package br.com.esl.etl.v2.modulos.manifestos.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.modulos.manifestos.aplicacao.ManifestoDataExportRecordMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ManifestoStageBatchTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void boundsThePhysicalPageAndHidesExecutionIdentity() throws Exception {
        final ManifestoStageRecord record =
                new ManifestoDataExportRecordMapper()
                        .map(
                                1,
                                JSON.readTree(
                                        "{\"sequence_code\":91,\"finished_at\":\"2036-01-01T10:00:00Z\"}"));
        final UUID executionId = UUID.fromString("00000000-0000-0000-0000-000000006399");
        final ManifestoStageBatch batch =
                new ManifestoStageBatch(
                        executionId, 1, List.of(record), Instant.parse("2036-01-01T10:00:01Z"));
        assertEquals(1, batch.size());
        assertEquals(record, batch.recordAt(0));
        assertTrue(batch.toString().contains("<redacted>"));
        assertThrows(
                IllegalArgumentException.class,
                () -> new ManifestoStageBatch(executionId, 0, List.of(record), Instant.now()));
        assertThrows(
                IllegalArgumentException.class,
                () -> new ManifestoStageBatch(executionId, 1, List.of(), Instant.now()));
    }

    @Test
    void valueObjectsDoNotExposePayloadOrIdentifiersInToString() {
        final String payloadMarker = "synthetic-sensitive-payload";
        final String mdfeKey = "9".repeat(44);
        final String mdfeNumber = "987654321";
        final String metricMarker = "123456.789";

        final String fieldText = ManifestoFieldValue.value('"' + payloadMarker + '"').toString();
        final String mdfeText =
                new ManifestoMdfeObservation(mdfeKey, new BigInteger(mdfeNumber)).toString();
        final String metricText =
                ManifestoMetricValue.value(new BigDecimal(metricMarker)).toString();

        assertFalse(fieldText.contains(payloadMarker));
        assertFalse(mdfeText.contains(mdfeKey));
        assertFalse(mdfeText.contains(mdfeNumber));
        assertFalse(metricText.contains(metricMarker));
        assertTrue(fieldText.contains("<redacted>"));
        assertTrue(mdfeText.contains("<redacted>"));
        assertTrue(metricText.contains("<redacted>"));
    }
}
