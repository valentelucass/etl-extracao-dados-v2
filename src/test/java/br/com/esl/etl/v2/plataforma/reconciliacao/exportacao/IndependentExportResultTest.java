package br.com.esl.etl.v2.plataforma.reconciliacao.exportacao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class IndependentExportResultTest {
    @TempDir Path directory;

    @Test
    void executedDiagnosticPinBindsInvocationConfigAndExactPairBeforeReturn() throws Exception {
        final var fixture = new ExportPairFixture(directory);
        final var manifest = fixture.manifest();
        final var pin =
                ExportValidationSupport.Pin.from(
                        fixture.pin(
                                "pair.json",
                                ExportValidationSupport.JSON.writeValueAsBytes(manifest)));
        final var diagnostic = ExportPairFixture.compare(manifest);
        final var budget = new ExportValidationSupport.Budget(Duration.ofSeconds(10));
        final var clock = Clock.fixed(Instant.parse("2025-03-10T16:00:00Z"), ZoneOffset.UTC);
        final var receipt =
                IndependentExportResult.persist(
                        directory.resolve("result.json"),
                        diagnostic,
                        pin,
                        clock.instant(),
                        clock,
                        budget);
        final var stored =
                ExportValidationSupport.Pin.from(receipt.required("diagnosticPin"))
                        .json(262144, budget);
        assertEquals(receipt.path("executionId"), stored.path("executionId"));
        assertEquals(manifest.required("configPin"), receipt.required("configPin"));
        assertEquals(manifest.required("api"), receipt.required("api"));
        assertEquals(manifest.required("ui"), receipt.required("ui"));
        assertEquals(pin.sha256(), receipt.path("manifestPin").path("sha256").asText());
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        IndependentExportResult.persist(
                                directory.resolve("result.json"),
                                diagnostic,
                                pin,
                                clock.instant(),
                                clock,
                                budget));
    }
}
