package br.com.esl.etl.v2.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.plataforma.fonte.raster.RasterLoopbackTransport;
import br.com.esl.etl.v2.plataforma.fonte.raster.RasterTransportConfiguration;
import br.com.esl.etl.v2.plataforma.fonte.raster.RasterWindow;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class AnalyticLaboratoryRasterTransportTest {
    @Test
    void disabledMissingCredentialsAndRemoteTargetsFailBeforeIo() {
        final var disabled = RasterTransportConfiguration.disabled();
        assertFalse(disabled.enabled());
        assertThrows(
                IllegalArgumentException.class,
                () -> new RasterTransportConfiguration(true, null, null));
        assertThrows(
                IllegalArgumentException.class,
                () -> new RasterTransportConfiguration(true, "fixture-user", ""));
        final var local =
                new RasterTransportConfiguration(
                        true, "synthetic-raster-user", "synthetic-raster-password");
        assertFalse(local.toString().contains("synthetic-raster-password"));
        assertThrows(
                IllegalArgumentException.class,
                () -> new RasterLoopbackTransport(disabled, null, Duration.ofSeconds(5)));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new RasterLoopbackTransport(
                                local,
                                URI.create("https://example.invalid/"),
                                Duration.ofSeconds(5)));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new RasterLoopbackTransport(
                                new RasterTransportConfiguration(
                                        true, "fixture-user", "fixture-secret"),
                                null,
                                Duration.ofSeconds(5)));
    }

    @Test
    void actualLoopbackPostUsesCivilWindowAndRefusesRedirectWithoutRetry() throws Exception {
        final var calls = new AtomicInteger();
        final var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(
                "/",
                exchange -> {
                    final int call = calls.incrementAndGet();
                    assertEquals("POST", exchange.getRequestMethod());
                    final var body = new ObjectMapper().readTree(exchange.getRequestBody());
                    assertEquals("2036-04-01", body.get("DataInicial").asText());
                    assertEquals("2036-04-02", body.get("DataFinal").asText());
                    assertEquals("SYNTHETIC", body.get("Ambiente").asText());
                    if (call == 1) {
                        exchange.getResponseHeaders().set("Content-Type", "application/json");
                        final byte[] result = "{\"Viagens\":[]}".getBytes(StandardCharsets.UTF_8);
                        exchange.sendResponseHeaders(200, result.length);
                        exchange.getResponseBody().write(result);
                    } else {
                        exchange.getResponseHeaders()
                                .set("Location", "http://127.0.0.1:1/redirect");
                        exchange.sendResponseHeaders(302, -1);
                    }
                    exchange.close();
                });
        server.start();
        try {
            final var transport =
                    new RasterLoopbackTransport(
                            new RasterTransportConfiguration(
                                    true, "synthetic-raster-user", "synthetic-raster-password"),
                            URI.create(
                                    "http://127.0.0.1:"
                                            + server.getAddress().getPort()
                                            + RasterLoopbackTransport.PATH),
                            Duration.ofSeconds(5));
            final var window = new RasterWindow(LocalDate.of(2036, 4, 1), LocalDate.of(2036, 4, 3));
            assertTrue(
                    new String(transport.fetch(window), StandardCharsets.UTF_8)
                            .contains("Viagens"));
            assertThrows(java.io.IOException.class, () -> transport.fetch(window));
            assertEquals(2, calls.get());
        } finally {
            server.stop(0);
        }
    }
}
