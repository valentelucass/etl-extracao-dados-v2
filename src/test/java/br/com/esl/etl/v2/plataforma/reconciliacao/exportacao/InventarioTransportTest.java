package br.com.esl.etl.v2.plataforma.reconciliacao.exportacao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.plataforma.configuracao.DataExportClientSettings;
import br.com.esl.etl.v2.plataforma.configuracao.DataExportSourceConfiguration;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportRetryPolicy;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportTransport;
import br.com.esl.etl.v2.plataforma.resiliencia.EslResiliencePolicy;
import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class InventarioTransportTest {
    @TempDir Path directory;

    @Test
    void infoAndDataReachOnlyTheDeclared10633GetRoutesWithOriginalBodyLimits() throws Exception {
        try (var fixture = new InventarioCaptureFixture(directory, false)) {
            final var urls = new ArrayList<URI>();
            final OwnedExportWire wire =
                    (method, target, body, authentication, maximum, timeout, deadline) -> {
                        assertEquals("GET", method);
                        assertNull(body);
                        assertTrue(authentication);
                        assertEquals(urls.isEmpty() ? 262144 : 8388608, maximum);
                        assertTrue(timeout.compareTo(Duration.ofSeconds(30)) <= 0);
                        urls.add(target);
                        return new CurlExportTransport.Wire(
                                200,
                                fixture.pin(InventarioCaptureFixture.array()),
                                fixture.clock.instant());
                    };
            final var transport =
                    new CurlExportTransport(
                            source(DataExportTransport.GET_WITH_QUERY),
                            fixture.journal,
                            wire,
                            fixture.clock,
                            fixture.deadline,
                            17,
                            Set.of());
            transport.api("GET", "/api/analytics/reports/10633/info", null, Duration.ofSeconds(30));
            transport.inventarioPage(fixture.request, 1, 100);
            assertEquals(2, urls.size());
            assertEquals("/api/analytics/reports/10633/info", urls.get(0).getPath());
            assertEquals("/api/analytics/reports/10633/data", urls.get(1).getPath());
            assertTrue(
                    urls.get(1)
                            .getRawQuery()
                            .startsWith("search%5Bcheck_in_orders%5D%5Bstarted_at%5D="));
            assertEquals(2, fixture.journal.count("HTTP_ADMITTED"));
            assertEquals(2, fixture.journal.count("HTTP_COMPLETE"));
        }
    }

    @Test
    void noGeneric10633DataOrPostFallbackAndNoWrongPreferredTransportIsIntroduced()
            throws Exception {
        try (var fixture = new InventarioCaptureFixture(directory, false)) {
            final int[] calls = {0};
            final OwnedExportWire wire =
                    (method, target, body, authentication, maximum, timeout, deadline) -> {
                        calls[0]++;
                        throw new IllegalStateException("UNEXPECTED_FIXTURE_WIRE");
                    };
            final var transport =
                    new CurlExportTransport(
                            source(DataExportTransport.GET_WITH_QUERY),
                            fixture.journal,
                            wire,
                            fixture.clock,
                            fixture.deadline,
                            17,
                            Set.of());
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            transport.api(
                                    "GET",
                                    "/api/analytics/reports/10633/data",
                                    null,
                                    Duration.ofSeconds(30)));
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            transport.api(
                                    "POST",
                                    "/api/analytics/reports/10633/data",
                                    null,
                                    Duration.ofSeconds(30)));
            final var bodyTransport =
                    new CurlExportTransport(
                            source(DataExportTransport.GET_WITH_BODY),
                            fixture.journal,
                            wire,
                            fixture.clock,
                            fixture.deadline,
                            17,
                            Set.of());
            assertThrows(
                    IllegalArgumentException.class,
                    () -> bodyTransport.inventarioPage(fixture.request, 1, 100));
            assertEquals(0, calls[0]);
        }
    }

    private static DataExportSourceConfiguration source(final DataExportTransport preferred) {
        return new DataExportSourceConfiguration(
                "synthetic-inventario",
                "synthetic-scope",
                new DataExportClientSettings(
                        URI.create("https://capture.invalid"),
                        ZoneId.of("America/Sao_Paulo"),
                        Duration.ofSeconds(30),
                        preferred,
                        new DataExportRetryPolicy(1, Duration.ZERO, Duration.ZERO),
                        8388608),
                new EslResiliencePolicy(
                        Duration.ofSeconds(3),
                        1,
                        17,
                        17,
                        Duration.ofSeconds(30),
                        Duration.ofSeconds(210),
                        Duration.ofSeconds(210),
                        Duration.ZERO,
                        0,
                        1,
                        Duration.ofSeconds(60)));
    }
}
