package br.com.esl.etl.v2.plataforma.reconciliacao.exportacao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FaturasClienteTransportTest {
    @TempDir Path directory;

    @Test
    void infoAndDataReachOnlyTheDeclared4924GetRoutesWithOriginalBodyLimits() throws Exception {
        try (var fixture = new FaturasClienteCaptureFixture(directory, false)) {
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
                                fixture.pin(FaturasClienteCaptureFixture.array()),
                                fixture.clock.instant());
                    };
            final var transport =
                    new FaturasClienteTransport(
                            source(DataExportTransport.GET_WITH_QUERY),
                            fixture.journal,
                            wire,
                            fixture.clock,
                            fixture.deadline,
                            17);
            transport.api("GET", "/api/analytics/reports/4924/info", null, Duration.ofSeconds(30));
            transport.page(fixture.request, 1, 100);
            assertEquals(2, urls.size());
            assertEquals("/api/analytics/reports/4924/info", urls.get(0).getPath());
            assertEquals("/api/analytics/reports/4924/data", urls.get(1).getPath());
            assertTrue(
                    urls.get(1).getRawQuery().startsWith("search%5Bfreights%5D%5Bservice_at%5D="));
            assertFalse(urls.get(1).getRawQuery().contains("by_updated_at"));
            assertTrue(urls.get(1).getRawQuery().endsWith("&page=1&per=100"));
            assertEquals(2, fixture.journal.count("HTTP_ADMITTED"));
            assertEquals(2, fixture.journal.count("HTTP_COMPLETE"));
        }
    }

    @Test
    void noGeneric4924DataOrPostFallbackAndNoWrongPreferredTransportIsIntroduced()
            throws Exception {
        try (var fixture = new FaturasClienteCaptureFixture(directory, false)) {
            final int[] calls = {0};
            final OwnedExportWire wire =
                    (method, target, body, authentication, maximum, timeout, deadline) -> {
                        calls[0]++;
                        throw new IllegalStateException("UNEXPECTED_FIXTURE_WIRE");
                    };
            final var transport =
                    new FaturasClienteTransport(
                            source(DataExportTransport.GET_WITH_QUERY),
                            fixture.journal,
                            wire,
                            fixture.clock,
                            fixture.deadline,
                            17);
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            transport.api(
                                    "GET",
                                    "/api/analytics/reports/4924/data",
                                    null,
                                    Duration.ofSeconds(30)));
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            transport.api(
                                    "POST",
                                    "/api/analytics/reports/4924/data",
                                    null,
                                    Duration.ofSeconds(30)));
            final var bodyTransport =
                    new FaturasClienteTransport(
                            source(DataExportTransport.GET_WITH_BODY),
                            fixture.journal,
                            wire,
                            fixture.clock,
                            fixture.deadline,
                            17);
            assertThrows(
                    IllegalArgumentException.class,
                    () -> bodyTransport.page(fixture.request, 1, 100));
            assertEquals(0, calls[0]);
        }
    }

    private static DataExportSourceConfiguration source(final DataExportTransport preferred) {
        return new DataExportSourceConfiguration(
                "synthetic-contas-pagar",
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
