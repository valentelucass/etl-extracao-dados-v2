package br.com.esl.etl.v2.plataforma.fonte.dataexport;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

class DataExportQueryEncoderTest {

    @Test
    void encodesScopeAsItsOwnSearchRootForGetFallback() {
        final DataExportPageRequest request =
                DataExportPageRequest.forTemplate(
                        DataExportTemplate.COLETAS,
                        new BusinessDateRange(LocalDate.of(2026, 8, 13), LocalDate.of(2026, 8, 13)),
                        new SourceDateTimeRange(
                                Instant.parse("2026-08-13T03:00:00Z"),
                                Instant.parse("2026-08-14T02:59:59Z")),
                        1);

        final URI uri =
                new DataExportQueryEncoder()
                        .appendQuery(
                                URI.create("https://example.test/api/analytics/reports/6908/data"),
                                request,
                                ZoneId.of("America/Sao_Paulo"));
        final String decodedQuery = URLDecoder.decode(uri.getRawQuery(), StandardCharsets.UTF_8);

        assertTrue(
                uri.getRawQuery()
                        .contains(
                                "search%5Bpicks%5D%5Brequest_date%5D=2026-08-13%20-%202026-08-13"));
        assertTrue(uri.getRawQuery().contains("order_by=sequence_code%20asc"));
        assertFalse(uri.getRawQuery().contains("+"));
        assertTrue(decodedQuery.contains("search[picks][request_date]=2026-08-13 - 2026-08-13"));
        assertTrue(
                decodedQuery.contains(
                        "search[scopes][by_updated_at]=2026-08-13 00:00:00 - 2026-08-13 23:59:59"));
        assertTrue(decodedQuery.contains("page=1"));
        assertTrue(decodedQuery.contains("per=100"));
    }
}
