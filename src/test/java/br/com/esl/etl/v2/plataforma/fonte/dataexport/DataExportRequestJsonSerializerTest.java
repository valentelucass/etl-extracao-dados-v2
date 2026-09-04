package br.com.esl.etl.v2.plataforma.fonte.dataexport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

class DataExportRequestJsonSerializerTest {

    private static final ZoneId SOURCE_ZONE = ZoneId.of("America/Sao_Paulo");
    private final DataExportRequestJsonSerializer serializer =
            new DataExportRequestJsonSerializer(new ObjectMapper());

    @Test
    void serializesColetasWithScopeAsSiblingOfPicks() {
        final ObjectNode payload =
                serializer.serialize(
                        DataExportPageRequest.forTemplate(
                                DataExportTemplate.COLETAS,
                                new BusinessDateRange(
                                        LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 13)),
                                new SourceDateTimeRange(
                                        Instant.parse("2026-08-13T03:00:00Z"),
                                        Instant.parse("2026-08-14T02:59:59Z")),
                                2),
                        SOURCE_ZONE);

        assertEquals("2026-08-01 - 2026-08-13", payload.at("/search/picks/request_date").asText());
        assertEquals(
                "2026-08-13 00:00:00 - 2026-08-13 23:59:59",
                payload.at("/search/scopes/by_updated_at").asText());
        assertFalse(payload.at("/search/picks/by_updated_at").isValueNode());
        assertEquals("2", payload.path("page").asText());
        assertEquals("100", payload.path("per").asText());
    }

    @Test
    void serializesFretesWithServiceAtAndUpdateScope() {
        final ObjectNode payload =
                serializer.serialize(
                        DataExportPageRequest.forTemplate(
                                DataExportTemplate.FRETES,
                                new BusinessDateRange(
                                        LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 13)),
                                new SourceDateTimeRange(
                                        Instant.parse("2026-08-13T03:00:00Z"),
                                        Instant.parse("2026-08-14T02:59:59Z")),
                                1),
                        SOURCE_ZONE);

        assertEquals("2026-08-01 - 2026-08-13", payload.at("/search/freights/service_at").asText());
        assertEquals(
                "2026-08-13 00:00:00 - 2026-08-13 23:59:59",
                payload.at("/search/scopes/by_updated_at").asText());
        assertEquals("corporation_sequence_number asc", payload.path("order_by").asText());
    }
}
