package br.com.esl.etl.v2.plataforma.reconciliacao.exportacao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SinistrosSourceCaptureMainTest {
    @TempDir Path directory;

    @Test
    void plansThreeDistinctWeeksWithSixFiltersSeventeenHttpAndNoSourceAuthority() throws Exception {
        String fingerprint = null;
        final var urls = new HashSet<String>();
        for (final var dates :
                List.of(
                        new String[] {"2026-10-12", "2026-10-18"},
                        new String[] {"2026-10-19", "2026-10-25"},
                        new String[] {"2026-10-26", "2026-11-01"})) {
            try (var fixture = new SinistrosCaptureFixture(directory, false)) {
                final var input =
                        input(fixture, SinistrosCaptureFixture.request(dates[0], dates[1]));
                final var result = SinistrosSourceCaptureMain.plan(input, budget());
                if (fingerprint == null) {
                    fingerprint = result.path("sourceConfigurationSha256").textValue();
                }
                assertEquals(fingerprint, result.path("sourceConfigurationSha256").textValue());
                assertEquals(16, result.path("finiteDataRequests").size());
                assertEquals(17, result.path("maximumHttpRequests").intValue());
                assertEquals(210, result.path("sourceSeconds").intValue());
                assertEquals(8388608, result.path("maximumRetainedBytes").intValue());
                assertEquals(0, result.path("Source").intValue());
                assertEquals(0, result.path("HTTP").intValue());
                assertFalse(result.path("sourceQueryAuthorized").booleanValue());
                assertTrue(urls.add(result.path("finiteDataRequests").get(0).textValue()));
            }
        }
    }

    @Test
    void planningCannotAddAuthorityOrUseNonHttpsOrAnOriginWithCredentialsOrPath() throws Exception {
        try (var fixture = new SinistrosCaptureFixture(directory, false)) {
            for (final String origin :
                    List.of(
                            "http://capture.invalid",
                            "https://u@capture.invalid",
                            "https://capture.invalid/base")) {
                final var input = input(fixture, fixture.request);
                ((ObjectNode) input.path("sourceSettings")).put("apiOrigin", origin);
                assertThrows(
                        IllegalArgumentException.class,
                        () -> SinistrosSourceCaptureMain.plan(input, budget()));
            }
            final var input = input(fixture, fixture.request).put("Source", 1);
            assertThrows(
                    IllegalArgumentException.class,
                    () -> SinistrosSourceCaptureMain.plan(input, budget()));
        }
    }

    @Test
    void grantMustBindExactSinistrosProfilePeriodUnitAndFiniteCapsNotManifestosOrPageScope()
            throws Exception {
        try (var fixture = new SinistrosCaptureFixture(directory, false)) {
            final var request = fixture.request;
            final var requestPin = fixture.pin(request);
            final var codePin =
                    fixture.pin(
                            ExportValidationSupport.JSON
                                    .createObjectNode()
                                    .put("syntheticCodeUnit", true));
            final String root = UUID.randomUUID().toString();
            final var context =
                    ExportValidationSupport.JSON.createObjectNode().put("executingRootUUID", root);
            final var valid = grant(requestPin, codePin, request, root);
            SinistrosSourceCaptureMain.requireGrant(
                    valid, context, requestPin, request, codePin, "c".repeat(64));
            for (final String mutation :
                    List.of(
                            "entity",
                            "report",
                            "profile",
                            "unit",
                            "caps",
                            "expired",
                            "rawPage",
                            "approved")) {
                final var changed = valid.deepCopy();
                switch (mutation) {
                    case "entity" -> changed.put("entity", "MANIFESTOS");
                    case "report" -> changed.put("reportId", 6399);
                    case "profile" -> changed.put("profile", "MANIFESTOS_6399_NATIVE91_V1");
                    case "unit" -> changed.put("codeUnitSha256", "d".repeat(64));
                    case "caps" -> changed.put("HTTPMaximum", 18);
                    case "expired" ->
                            changed.put("expiresUtc", Instant.now().minusSeconds(1).toString());
                    case "rawPage" -> changed.put("pageIndex", 1);
                    default -> changed.put("approved", false);
                }
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                SinistrosSourceCaptureMain.requireGrant(
                                        changed,
                                        context,
                                        requestPin,
                                        request,
                                        codePin,
                                        "c".repeat(64)));
            }
        }
    }

    @Test
    void captureWithoutPhysicalPinsIsRefusedBeforeNetworkAndPrivateDetailsAreNotPrinted()
            throws Exception {
        try (var fixture = new SinistrosCaptureFixture(directory, false)) {
            final var input =
                    input(fixture, fixture.request)
                            .put("schema", "ROOT_SINISTROS_SOURCE_EXECUTION_INPUT_V1");
            final var pin = fixture.pin(input);
            final var out = new ByteArrayOutputStream();
            final var err = new ByteArrayOutputStream();
            assertEquals(
                    2,
                    SinistrosSourceCaptureMain.run(
                            new String[] {
                                "capture",
                                "--input",
                                pin.path().toString(),
                                "--sha256",
                                pin.sha256()
                            },
                            new PrintStream(out),
                            new PrintStream(err)));
            assertEquals("", out.toString());
            assertEquals(
                    "SINISTROS_SOURCE_EXPLICIT_FIELDS" + System.lineSeparator(), err.toString());
            assertEquals(0, fixture.port.infoCalls);
            assertEquals(0, fixture.port.pageCalls);
        }
    }

    private static ObjectNode input(final SinistrosCaptureFixture fixture, final ObjectNode request)
            throws Exception {
        final var input =
                ExportValidationSupport.JSON
                        .createObjectNode()
                        .put("schema", "SINISTROS_SOURCE_PLAN_INPUT_V1");
        input.set("requestPin", ExportValidationSupport.JSON.valueToTree(fixture.pin(request)));
        input.putObject("sourceSettings")
                .put("sourceInstance", "synthetic-sinistros")
                .put("tenantScope", "synthetic-scope")
                .put("apiOrigin", "https://capture.invalid");
        return input;
    }

    private static ObjectNode grant(
            final ExportValidationSupport.Pin requestPin,
            final ExportValidationSupport.Pin codePin,
            final ObjectNode request,
            final String root) {
        final var grant =
                ExportValidationSupport.JSON
                        .createObjectNode()
                        .put("schema", "ROOT_SINISTROS_FINITE_SOURCE_GRANT_V1")
                        .put("approved", true)
                        .put("entity", "SINISTROS")
                        .put("reportId", 6392)
                        .put("profile", SinistrosIndependentRequest.PROFILE)
                        .put("executingRootUUID", root)
                        .put("attemptId", request.path("attemptId").textValue())
                        .put("periodStart", request.path("periodStart").textValue())
                        .put("periodEnd", request.path("periodEnd").textValue())
                        .put("zone", "America/Sao_Paulo")
                        .put("sourceConfigurationSha256", "c".repeat(64))
                        .put("codeUnitSha256", codePin.sha256())
                        .put("OriginalMaximum", 1)
                        .put("SourceMaximum", 1)
                        .put("INFOHTTPMaximum", 1)
                        .put("HTTPMaximum", 17)
                        .put("DATAHTTPMaximum", 16)
                        .put("SourceSeconds", 210)
                        .put("OriginalSeconds", 300)
                        .put("OriginalBytes", 1073741824L)
                        .put("per", 100)
                        .put("SQL", 0)
                        .put("retryMaximum", 0)
                        .put("redirectMaximum", 0)
                        .put("createdUtc", Instant.now().minusSeconds(2).toString())
                        .put("expiresUtc", Instant.now().plusSeconds(290).toString());
        grant.set("requestPin", ExportValidationSupport.JSON.valueToTree(requestPin));
        return grant;
    }

    private static ExportValidationSupport.Budget budget() {
        return new ExportValidationSupport.Budget(Duration.ofSeconds(30));
    }
}
