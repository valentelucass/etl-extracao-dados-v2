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
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ManifestosTransportTest {
    @TempDir Path directory;

    @Test
    void infoAndPageUseExplicitGetQueryAndRetainExactlyTwoOperationsWithoutRetries()
            throws Exception {
        final var targets = new ArrayList<URI>();
        try (var journal = journal()) {
            final var transport = transport(journal, DataExportTransport.GET_WITH_QUERY, targets);
            final var request = new ManifestosContentFixture(directory, 1).request;
            transport.api("GET", "/api/analytics/reports/6399/info", null, Duration.ofSeconds(30));
            transport.manifestosPage(request, 2, 100);
            assertEquals(2, targets.size());
            assertEquals(
                    "https://capture.invalid/api/analytics/reports/6399/info",
                    targets.get(0).toString());
            assertEquals(
                    "https://capture.invalid/api/analytics/reports/6399/data?"
                            + "search%5Bmanifests%5D%5Bservice_date%5D=2024-02-01%20-%202024-02-01&page=2&per=100",
                    targets.get(1).toASCIIString());
            assertEquals(2, journal.count("HTTP_ADMITTED"));
            assertEquals(2, journal.count("HTTP_COMPLETE"));
        }
    }

    @Test
    void wrongTemplateUnscopedDataAndInvalidRequestNeverReachPhysicalPort() throws Exception {
        final var targets = new ArrayList<URI>();
        try (var journal = journal()) {
            final var transport = transport(journal, DataExportTransport.GET_WITH_QUERY, targets);
            final var request = new ManifestosContentFixture(directory, 1).request;
            for (final String path :
                    new String[] {
                        "/api/analytics/reports/6399/data",
                        "/api/analytics/reports/6398/info",
                        "/api/analytics/reports/6399/info?extra=1"
                    }) {
                assertThrows(
                        IllegalArgumentException.class,
                        () -> transport.api("GET", path, null, Duration.ofSeconds(30)));
            }
            assertThrows(
                    IllegalArgumentException.class,
                    () -> transport.manifestosPage(request, 0, 100));
            assertThrows(
                    IllegalArgumentException.class,
                    () -> transport.manifestosPage(request, 1, 101));
            request.put("reportId", 6389);
            assertThrows(
                    IllegalArgumentException.class,
                    () -> transport.manifestosPage(request, 1, 100));
            assertTrue(targets.isEmpty());
            assertEquals(0, journal.count("HTTP_ADMITTED"));
        }
    }

    @Test
    void aDifferentConfiguredTransportDoesNotFallBackToGet() throws Exception {
        final var targets = new ArrayList<URI>();
        try (var journal = journal()) {
            final var transport = transport(journal, DataExportTransport.POST_JSON, targets);
            final var request = new ManifestosContentFixture(directory, 1).request;
            assertThrows(
                    IllegalArgumentException.class,
                    () -> transport.manifestosPage(request, 1, 100));
            assertTrue(targets.isEmpty());
        }
    }

    private CurlExportTransport transport(
            final PersistentExportJournal journal,
            final DataExportTransport mode,
            final ArrayList<URI> targets) {
        final var source =
                new DataExportSourceConfiguration(
                        "fixture",
                        "fixture",
                        new DataExportClientSettings(
                                URI.create("https://capture.invalid"),
                                ZoneId.of("America/Sao_Paulo"),
                                Duration.ofSeconds(30),
                                mode,
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
        return new CurlExportTransport(
                source,
                journal,
                (method, target, body, authenticated, maximum, timeout, deadline) -> {
                    assertEquals("GET", method);
                    assertNull(body);
                    assertTrue(authenticated);
                    assertTrue(maximum <= 8388608);
                    targets.add(target);
                    final var path = directory.resolve(UUID.randomUUID() + ".json");
                    final byte[] bytes = "[]".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
                    Files.write(path, bytes);
                    return new CurlExportTransport.Wire(
                            200,
                            new ExportValidationSupport.Pin(
                                    path.toAbsolutePath(),
                                    ExportValidationSupport.sha256(bytes),
                                    bytes.length),
                            Instant.now());
                },
                Clock.systemUTC(),
                Instant.now().plusSeconds(210),
                17,
                Set.of());
    }

    private PersistentExportJournal journal() throws Exception {
        return PersistentExportJournal.create(
                directory.resolve(UUID.randomUUID().toString()),
                "a".repeat(64),
                "b".repeat(64),
                UUID.randomUUID());
    }
}
