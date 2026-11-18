package br.com.esl.etl.v2.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.plataforma.configuracao.RuntimeConfigurationFactory;
import br.com.esl.etl.v2.plataforma.reconciliacao.exportacao.ColetasIndependentRequest;
import br.com.esl.etl.v2.plataforma.reconciliacao.exportacao.ExportValidationSupport;
import br.com.esl.etl.v2.plataforma.reconciliacao.exportacao.IndependentExportRequest;
import br.com.esl.etl.v2.plataforma.reconciliacao.exportacao.ManifestosIndependentRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class IndependentExportCommandTest {
    @TempDir Path directory;

    @Test
    void realMainRoutesManifestosDiagnosticAndRefusesInvalidPrivateInputsWithoutLeakingValues()
            throws Exception {
        final byte[] bytes =
                "{\"schema\":\"PRIVATE_SENTINEL\"}"
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8);
        final Path path = directory.resolve("invalid-private-input.json");
        Files.write(path, bytes);
        final var out = new ByteArrayOutputStream();
        final var err = new ByteArrayOutputStream();
        final Path result = directory.resolve("result.json");
        final int code =
                runMain(
                        new String[] {
                            "independent-export",
                            "diagnose-manifestos",
                            "--input",
                            path.toString(),
                            "--sha256",
                            ExportValidationSupport.sha256(bytes),
                            "--output",
                            result.toString()
                        },
                        new PrintStream(out),
                        new PrintStream(err));
        assertEquals(2, code);
        assertEquals("", out.toString());
        assertEquals(
                "INDEPENDENT_EXPORT_REFUSED_NO_RESULT" + System.lineSeparator(), err.toString());
        assertFalse(Files.exists(result));
    }

    @Test
    void realMainDispatchesExplicitOfflineProfile() throws Exception {
        final var input = ExportValidationSupport.JSON.createObjectNode();
        input.put("schema", "INDEPENDENT_EXPORT_REQUEST_V1");
        input.put("attemptId", UUID.randomUUID().toString());
        input.put("profile", IndependentExportRequest.PROFILE);
        input.put("entity", "FRETES");
        input.put("reportId", 6389);
        input.put("periodStart", "2024-02-01");
        input.put("periodEnd", "2024-02-02");
        input.put("zone", "America/Sao_Paulo");
        final var filters = input.putObject("filters");
        IndependentExportRequest.FRETES_FILTERS.forEach(name -> filters.put(name, ""));
        filters.put("freights[service_at]", "2024-02-01 - 2024-02-02");
        final var bytes = ExportValidationSupport.JSON.writeValueAsBytes(input);
        final var path = directory.resolve("request.json");
        Files.write(path, bytes);
        final var out = new ByteArrayOutputStream();
        final var err = new ByteArrayOutputStream();
        final int code =
                runMain(
                        new String[] {
                            "independent-export",
                            "plan",
                            "--input",
                            path.toString(),
                            "--sha256",
                            ExportValidationSupport.sha256(bytes)
                        },
                        new PrintStream(out),
                        new PrintStream(err));
        assertEquals(0, code);
        assertTrue(out.toString().contains("PENDING_OPERATOR_SESSION_AND_CLICK_OBSERVATION"));
        assertTrue(out.toString().contains("\"HTTP\":0"));
        final var result = ExportValidationSupport.JSON.readTree(out.toByteArray());
        assertEquals(IndependentExportRequest.plan(input), result);
        assertEquals("POST", result.path("method").asText());
        assertEquals("/api/analytics/reports/6389/export", result.path("path").asText());
        assertEquals("", err.toString());
    }

    @Test
    void malformedProfileCannotDispatch() {
        assertEquals(
                2,
                runMain(
                        new String[] {"independent-export", "request"},
                        new PrintStream(new ByteArrayOutputStream()),
                        new PrintStream(new ByteArrayOutputStream())));
    }

    @Test
    void realMainPlansManifestosWithoutContentCollectionOrFileExportAuthority() throws Exception {
        final var invocation = plan(manifestos());
        assertEquals(0, invocation.code());
        assertEquals("", invocation.err());
        final var result = ExportValidationSupport.JSON.readTree(invocation.out());
        assertEquals(ManifestosIndependentRequest.PROFILE, result.path("profile").asText());
        assertEquals(6399, result.path("reportId").intValue());
        assertEquals("GET_WITH_QUERY", result.path("transport").asText());
        assertEquals("GET", result.path("method").asText());
        assertEquals("/api/analytics/reports/6399/data", result.path("dataPath").asText());
        assertEquals(18, result.path("filters").size());
        for (final String flag :
                List.of(
                        "sourceQueryAuthorized",
                        "contentQualified",
                        "fileExportQualified",
                        "mainResumeAllowed",
                        "runtimeAdopted",
                        "snapshotIdentityProven",
                        "paginationQualified")) {
            assertTrue(result.path(flag).isBoolean(), flag);
            assertFalse(result.path(flag).booleanValue(), flag);
        }
        for (final String counter : List.of("Source", "HTTP", "SQL")) {
            assertEquals(0, result.required(counter).intValue());
        }
        assertFalse(result.has("body"));
        assertFalse(invocation.out().contains("/export"));
    }

    @Test
    void realMainStillDispatchesColetasWithItsOriginalPlanSemantics() throws Exception {
        final var input =
                manifestos()
                        .put("schema", "COLETAS_INDEPENDENT_REQUEST_V1")
                        .put("profile", ColetasIndependentRequest.PROFILE)
                        .put("entity", "COLETAS")
                        .put("reportId", 6908);
        final var filters = input.putObject("filters");
        ColetasIndependentRequest.FILTERS.forEach(name -> filters.put(name, ""));
        filters.put("picks[request_date]", "2024-02-01 - 2024-02-02");
        final var invocation = plan(input);
        assertEquals(0, invocation.code());
        assertEquals("", invocation.err());
        final var result = ExportValidationSupport.JSON.readTree(invocation.out());
        assertEquals(ColetasIndependentRequest.plan(input), result);
        assertEquals("/api/analytics/reports/6908/data", result.path("dataPath").asText());
        assertFalse(result.has("contentQualified"));
        assertFalse(result.has("method"));
    }

    @Test
    void realMainRefusesMixedEntityTemplateProfileAndSchemaWithoutFallingBack() throws Exception {
        for (final String[] change :
                new String[][] {
                    {"entity", "FRETES"},
                    {"entity", "COLETAS"},
                    {"schema", "INDEPENDENT_EXPORT_REQUEST_V1"},
                    {"schema", "COLETAS_INDEPENDENT_REQUEST_V1"},
                    {"profile", IndependentExportRequest.PROFILE},
                    {"profile", ColetasIndependentRequest.PROFILE},
                    {"profile", "MANIFESTOS_6399_UNKNOWN"}
                }) {
            assertRefused(plan(manifestos().put(change[0], change[1])));
        }
        assertRefused(plan(manifestos().put("reportId", 6389)));
        assertRefused(plan(manifestos().put("reportId", 6908)));
        assertRefused(plan(manifestos().put("reportId", "6399")));
    }

    @Test
    void realMainRefusesMissingExtraUnqualifiedAndNonemptyAuxiliaryFilters() throws Exception {
        final var missing = manifestos();
        ((ObjectNode) missing.required("filters")).remove("scopes[with_ciot]");
        assertRefused(plan(missing));
        for (final String name :
                List.of("service_date", "manifests.service_date", "manifests[with_ciot]")) {
            final var extra = manifestos();
            ((ObjectNode) extra.required("filters")).put(name, "");
            assertRefused(plan(extra));
        }
        final var auxiliary = manifestos();
        ((ObjectNode) auxiliary.required("filters"))
                .put("manifests[status]", "private-filter-value");
        assertRefused(plan(auxiliary));
    }

    @Test
    void realMainRefusesInvalidPeriodsAndIgnoredPaginationInsteadOfEmittingAPlan()
            throws Exception {
        assertRefused(plan(manifestos().put("periodEnd", "2024-01-31")));
        assertRefused(plan(manifestos().put("periodEnd", "2024-02-30")));
        assertRefused(plan(manifestos().put("page", 0)));
        assertRefused(plan(manifestos().put("per", 101)));
    }

    @Test
    void realMainRefusesDuplicateFilterNamesAtThePinnedJsonBoundary() throws Exception {
        final String input = ExportValidationSupport.JSON.writeValueAsString(manifestos());
        final String duplicated =
                input.replace(
                        "\"manifests[service_date]\":",
                        "\"manifests[service_date]\":\"2024-02-01 - 2024-02-02\",\"manifests[service_date]\":");
        assertFalse(input.equals(duplicated));
        final byte[] bytes = duplicated.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        assertRefused(planBytes(bytes, ExportValidationSupport.sha256(bytes)));
    }

    @Test
    void realMainRefusesAChangedManifestosInputPin() throws Exception {
        final byte[] bytes = ExportValidationSupport.JSON.writeValueAsBytes(manifestos());
        final String digest = ExportValidationSupport.sha256(bytes);
        final String otherDigest = (digest.startsWith("0") ? "1" : "0") + digest.substring(1);
        assertRefused(planBytes(bytes, otherDigest));
    }

    private static ObjectNode manifestos() {
        final var input =
                ExportValidationSupport.JSON
                        .createObjectNode()
                        .put("schema", "MANIFESTOS_INDEPENDENT_REQUEST_V1")
                        .put("profile", ManifestosIndependentRequest.PROFILE)
                        .put("entity", "MANIFESTOS")
                        .put("reportId", 6399)
                        .put("attemptId", UUID.randomUUID().toString())
                        .put("periodStart", "2024-02-01")
                        .put("periodEnd", "2024-02-02")
                        .put("zone", "America/Sao_Paulo");
        final var filters = input.putObject("filters");
        ManifestosIndependentRequest.FILTERS.forEach(name -> filters.put(name, ""));
        filters.put(ManifestosIndependentRequest.SERVICE_DATE_FILTER, "2024-02-01 - 2024-02-02");
        return input;
    }

    private Invocation plan(final JsonNode input) throws Exception {
        final byte[] bytes = ExportValidationSupport.JSON.writeValueAsBytes(input);
        return planBytes(bytes, ExportValidationSupport.sha256(bytes));
    }

    private Invocation planBytes(final byte[] bytes, final String digest) throws Exception {
        final Path path = directory.resolve(UUID.randomUUID() + ".json");
        Files.write(path, bytes);
        final var out = new ByteArrayOutputStream();
        final var err = new ByteArrayOutputStream();
        final int code =
                runMain(
                        new String[] {
                            "independent-export",
                            "plan",
                            "--input",
                            path.toString(),
                            "--sha256",
                            digest
                        },
                        new PrintStream(out),
                        new PrintStream(err));
        return new Invocation(
                code,
                out.toString(java.nio.charset.StandardCharsets.UTF_8),
                err.toString(java.nio.charset.StandardCharsets.UTF_8));
    }

    private static void assertRefused(final Invocation invocation) {
        assertEquals(2, invocation.code());
        assertEquals("", invocation.out());
        assertEquals(
                "INDEPENDENT_EXPORT_REFUSED_NO_RESULT" + System.lineSeparator(), invocation.err());
    }

    private record Invocation(int code, String out, String err) {}

    private static int runMain(final String[] args, final PrintStream out, final PrintStream err) {
        return Main.run(
                args,
                out,
                err,
                new Main.RuntimeDependencies(
                        new java.util.Properties(),
                        java.util.Map.of(),
                        java.time.Clock.fixed(
                                java.time.Instant.parse("2025-03-10T16:00:00Z"),
                                java.time.ZoneOffset.UTC),
                        new RuntimeConfigurationFactory()));
    }
}
