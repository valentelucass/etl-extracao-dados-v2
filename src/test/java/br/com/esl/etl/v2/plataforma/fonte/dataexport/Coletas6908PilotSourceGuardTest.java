package br.com.esl.etl.v2.plataforma.fonte.dataexport;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.plataforma.configuracao.DataExportClientSettings;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class Coletas6908PilotSourceGuardTest {

    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-09-30T01:00:00Z"), ZoneOffset.UTC);
    private static final LocalDate CLOSED_DAY = LocalDate.of(2026, 9, 28);
    private static final long TEN_MIB = 10L * 1024 * 1024;

    @Test
    void acceptsBoundedTraversalForClosedSingleDayRequestDateThroughGetQuery() {
        final DataExportPageRequest request = request(CLOSED_DAY, CLOSED_DAY, 1, 100);

        assertDoesNotThrow(
                () ->
                        Coletas6908PilotSourceGuard.validateTraversal(
                                settings(), request, limits(2, 1_000, 100), CLOCK));
        final String query =
                URLDecoder.decode(
                        new DataExportQueryEncoder()
                                .appendQuery(
                                        URI.create(
                                                "https://synthetic.invalid"
                                                        + "/api/analytics/reports/6908/data"),
                                        request,
                                        settings().sourceZone())
                                .getRawQuery(),
                        StandardCharsets.UTF_8);
        assertTrue(query.contains("search[picks][request_date]=2026-09-28 - 2026-09-28"));
        assertTrue(query.contains("page=1"));
        assertTrue(query.contains("per=100"));
        assertTrue(query.contains("order_by=sequence_code asc"));
    }

    @Test
    void rejectsSourceSettingsThatCouldChangeTransportRetryOrHttpBounds() {
        for (final DataExportClientSettings invalid :
                List.of(
                        settings(DataExportTransport.POST_JSON, 1, Duration.ofSeconds(30), TEN_MIB),
                        settings(
                                DataExportTransport.GET_WITH_QUERY,
                                2,
                                Duration.ofSeconds(30),
                                TEN_MIB),
                        settings(
                                DataExportTransport.GET_WITH_QUERY,
                                1,
                                Duration.ofSeconds(31),
                                TEN_MIB),
                        settings(
                                DataExportTransport.GET_WITH_QUERY,
                                1,
                                Duration.ofSeconds(30),
                                TEN_MIB + 1))) {
            final IllegalArgumentException failure =
                    assertThrows(
                            IllegalArgumentException.class,
                            () ->
                                    Coletas6908PilotSourceGuard.validateTraversal(
                                            invalid,
                                            request(CLOSED_DAY, CLOSED_DAY, 1, 100),
                                            limits(2, 1_000, 100),
                                            CLOCK));
            assertEquals("COLETAS_6908_PILOT_SOURCE_LIMITS_REQUIRED", failure.getMessage());
        }
    }

    @Test
    void rejectsUnclosedOrBroaderWindowAndWrongPageSemantics() {
        for (final DataExportPageRequest invalid :
                List.of(
                        request(CLOSED_DAY, CLOSED_DAY.plusDays(1), 1, 100),
                        request(CLOSED_DAY.plusDays(1), CLOSED_DAY.plusDays(1), 1, 100),
                        request(CLOSED_DAY, CLOSED_DAY, 2, 100),
                        request(CLOSED_DAY, CLOSED_DAY, 1, 101),
                        new DataExportPageRequest(
                                DataExportTemplate.FRETES,
                                new BusinessDateRange(CLOSED_DAY, CLOSED_DAY),
                                Optional.empty(),
                                1,
                                100,
                                DataExportTemplate.FRETES.defaultOrderBy()),
                        new DataExportPageRequest(
                                DataExportTemplate.COLETAS,
                                new BusinessDateRange(CLOSED_DAY, CLOSED_DAY),
                                Optional.of(
                                        new SourceDateTimeRange(
                                                Instant.parse("2026-09-28T00:00:00Z"),
                                                Instant.parse("2026-09-28T23:59:59Z"))),
                                1,
                                100,
                                DataExportTemplate.COLETAS.defaultOrderBy()),
                        new DataExportPageRequest(
                                DataExportTemplate.COLETAS,
                                new BusinessDateRange(CLOSED_DAY, CLOSED_DAY),
                                Optional.empty(),
                                1,
                                100,
                                List.of("id asc")))) {
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            Coletas6908PilotSourceGuard.validateTraversal(
                                    settings(), invalid, limits(2, 1_000, 100), CLOCK));
        }
    }

    @Test
    void rejectsUnboundedTraversalBeforeFirstPage() {
        for (final DataExportExtractionLimits invalid :
                List.of(
                        limits(1, 1_000, 100),
                        limits(10, 1_000, 100),
                        limits(2, 1_001, 100),
                        limits(2, 1_000, 101))) {
            final IllegalArgumentException failure =
                    assertThrows(
                            IllegalArgumentException.class,
                            () ->
                                    Coletas6908PilotSourceGuard.validateTraversal(
                                            settings(),
                                            request(CLOSED_DAY, CLOSED_DAY, 1, 100),
                                            invalid,
                                            CLOCK));
            assertEquals("COLETAS_6908_PILOT_TRAVERSAL_LIMITS_REQUIRED", failure.getMessage());
        }
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        Coletas6908PilotSourceGuard.validateTraversal(
                                settings(),
                                request(CLOSED_DAY, CLOSED_DAY, 1, 100),
                                limits(2, 1_000, 99),
                                CLOCK));
    }

    private static DataExportExtractionLimits limits(
            final int pages, final long physicalRows, final int maxPer) {
        return new DataExportExtractionLimits(pages, physicalRows, maxPer);
    }

    private static DataExportPageRequest request(
            final LocalDate first, final LocalDate last, final int page, final int per) {
        return new DataExportPageRequest(
                DataExportTemplate.COLETAS,
                new BusinessDateRange(first, last),
                Optional.empty(),
                page,
                per,
                DataExportTemplate.COLETAS.defaultOrderBy());
    }

    private static DataExportClientSettings settings() {
        return settings(DataExportTransport.GET_WITH_QUERY, 1, Duration.ofSeconds(30), TEN_MIB);
    }

    private static DataExportClientSettings settings(
            final DataExportTransport transport,
            final int attempts,
            final Duration timeout,
            final long responseBytes) {
        return new DataExportClientSettings(
                URI.create("https://synthetic.invalid"),
                ZoneId.of("America/Sao_Paulo"),
                timeout,
                transport,
                new DataExportRetryPolicy(attempts, Duration.ZERO, Duration.ZERO),
                responseBytes);
    }
}
