package br.com.esl.etl.v2.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.plataforma.configuracao.RuntimeConfigurationFactory;
import br.com.esl.etl.v2.plataforma.reconciliacao.exportacao.ColetasIndependentRequest;
import br.com.esl.etl.v2.plataforma.reconciliacao.exportacao.ExportValidationSupport;
import br.com.esl.etl.v2.plataforma.reconciliacao.exportacao.IndependentExportRequest;
import br.com.esl.etl.v2.plataforma.reconciliacao.exportacao.MainProfileFretesFixtureBridge;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Real Main/CLI Source0 checks. Does not claim an authorized live-run integration proof. */
class OperationalProfileMainSource0CandidateTest {
    @TempDir Path directory;

    record Answer(int code, String out, String err) {}

    private Answer main(final String... args) {
        final var out = new ByteArrayOutputStream();
        final var err = new ByteArrayOutputStream();
        final int code =
                Main.run(
                        args,
                        new PrintStream(out),
                        new PrintStream(err),
                        new Main.RuntimeDependencies(
                                new Properties(),
                                Map.of(),
                                Clock.fixed(Instant.parse("2025-03-10T16:00:00Z"), ZoneOffset.UTC),
                                new RuntimeConfigurationFactory()));
        return new Answer(code, out.toString(), err.toString());
    }

    private Answer operational(final String selected, final String actual) throws Exception {
        final var selection =
                ExportValidationSupport.JSON.createObjectNode().put("profile", actual);
        final byte[] raw = ExportValidationSupport.JSON.writeValueAsBytes(selection);
        final Path path = directory.resolve("selection.json");
        Files.write(path, raw);
        return main(
                "run",
                "--profile",
                selected,
                "--config",
                directory.resolve("not-configured.properties").toString(),
                "--operational-request",
                directory.resolve("no-authority.json").toString(),
                "--selection",
                path.toString(),
                "--sha256",
                ExportValidationSupport.sha256(raw));
    }

    @Test
    void realMainRejectsUnsupportedProfileBeforeLoadingConfiguration() throws Exception {
        final var result = operational("USUARIOS", "USUARIOS");
        assertEquals(2, result.code());
        assertEquals("", result.out());
        assertTrue(result.err().contains("OPERATIONAL_EXPORT_PROFILE_NOT_SUPPORTED"));
    }

    @Test
    void realMainBindsFretesProfileToSelectionBeforeAuthority() throws Exception {
        final var result =
                operational(IndependentExportRequest.PROFILE, ColetasIndependentRequest.PROFILE);
        assertEquals(2, result.code());
        assertTrue(result.err().contains("OPERATIONAL_EXPORT_PROFILE_SELECTION_MISMATCH"));
    }

    @Test
    void realMainBindsColetasProfileToSelectionBeforeAuthority() throws Exception {
        final var result =
                operational(ColetasIndependentRequest.PROFILE, IndependentExportRequest.PROFILE);
        assertEquals(2, result.code());
        assertTrue(result.err().contains("OPERATIONAL_EXPORT_PROFILE_SELECTION_MISMATCH"));
    }

    @Test
    void realMainRoutesFretesButCannotActivateSourceBySelectingProfile() throws Exception {
        final var result =
                operational(IndependentExportRequest.PROFILE, IndependentExportRequest.PROFILE);
        assertEquals(2, result.code());
        assertTrue(result.err().contains("INDEPENDENT_EXPORT_OPERATIONAL_REFUSED_OR_UNCERTAIN"));
        assertEquals("", result.out());
    }

    @Test
    void realMainRoutesColetasButCannotActivateSourceBySelectingProfile() throws Exception {
        final var result =
                operational(ColetasIndependentRequest.PROFILE, ColetasIndependentRequest.PROFILE);
        assertEquals(2, result.code());
        assertTrue(result.err().contains("INDEPENDENT_EXPORT_OPERATIONAL_REFUSED_OR_UNCERTAIN"));
        assertEquals("", result.out());
    }

    @Test
    void realMainDoesNotFallbackForIncompleteExplicitProfileCommand() {
        final var result = main("run", "--profile", IndependentExportRequest.PROFILE);
        assertEquals(2, result.code());
        assertTrue(result.err().contains("OPERATIONAL_EXPORT_PROFILE_USAGE"));
    }

    @Test
    void realMainKeepsOrdinaryRunSeparateFromCandidateProfile() {
        final var result = main("run");
        assertEquals(2, result.code());
        assertFalse(result.err().contains("OPERATIONAL_EXPORT_PROFILE"));
    }

    @Test
    void realMainPlansColetasWithoutSourceOrSql() throws Exception {
        final var request =
                ExportValidationSupport.JSON
                        .createObjectNode()
                        .put("schema", "COLETAS_INDEPENDENT_REQUEST_V1")
                        .put("attemptId", UUID.randomUUID().toString())
                        .put("profile", ColetasIndependentRequest.PROFILE)
                        .put("entity", "COLETAS")
                        .put("reportId", 6908)
                        .put("zone", "America/Sao_Paulo")
                        .put("periodStart", "2025-03-03")
                        .put("periodEnd", "2025-03-09");
        final var filters = request.putObject("filters");
        ColetasIndependentRequest.FILTERS.forEach(name -> filters.put(name, ""));
        filters.put("picks[request_date]", "2025-03-03 - 2025-03-09");
        final byte[] raw = ExportValidationSupport.JSON.writeValueAsBytes(request);
        final Path path = directory.resolve("coletas-plan.json");
        Files.write(path, raw);
        final var result =
                main(
                        "independent-export",
                        "plan",
                        "--input",
                        path.toString(),
                        "--sha256",
                        ExportValidationSupport.sha256(raw));
        assertEquals(0, result.code());
        final JsonNode planned = ExportValidationSupport.JSON.readTree(result.out());
        assertEquals(0, planned.path("HTTP").asInt());
        assertEquals(0, planned.path("SQL").asInt());
        assertFalse(planned.path("mainResumeAllowed").asBoolean());
    }

    private Answer compare(final boolean different) throws Exception {
        final ObjectNode pair = MainProfileFretesFixtureBridge.pair(directory, different);
        final byte[] raw = ExportValidationSupport.JSON.writeValueAsBytes(pair);
        final Path manifest = directory.resolve("main-pair.json");
        Files.write(manifest, raw);
        return main(
                "independent-export",
                "compare",
                "--input",
                manifest.toString(),
                "--sha256",
                ExportValidationSupport.sha256(raw),
                "--output",
                directory.resolve("main-result.json").toString());
    }

    @Test
    void realMainComparesFullSyntheticFretesAndPublishesClosedReceipt() throws Exception {
        final var result = compare(false);
        assertEquals(0, result.code());
        final JsonNode receipt = ExportValidationSupport.JSON.readTree(result.out());
        assertEquals("CONTENT_EQUAL_FOR_THIS_PAIR", receipt.path("status").asText());
        final var pin = ExportValidationSupport.Pin.from(receipt.required("diagnosticPin"));
        final var diagnostic =
                pin.json(
                        262144,
                        new ExportValidationSupport.Budget(java.time.Duration.ofSeconds(5)));
        assertEquals(114, diagnostic.path("comparison").path("columnsEqual").size());
        assertEquals(50, diagnostic.path("comparison").path("totalsEqual").size());
        assertTrue(diagnostic.path("comparison").path("fullTuplesEqual").asBoolean());
        assertTrue(diagnostic.has("fullValueProjection"));
        assertFalse(receipt.path("mainResumeAllowed").asBoolean());
        assertFalse(receipt.path("dashboardConsumptionVerified").asBoolean());
        assertEquals(0, receipt.path("HTTP").asInt());
        assertEquals(0, receipt.path("SQL").asInt());
    }

    @Test
    void realMainKeepsSyntheticDifferenceNonzeroAndDoesNotQualifyIt() throws Exception {
        final var result = compare(true);
        assertEquals(3, result.code());
        final JsonNode receipt = ExportValidationSupport.JSON.readTree(result.out());
        assertEquals(
                "PENDING_DIFFERENCE_INVESTIGATION_AND_NEW_PAIR", receipt.path("status").asText());
        assertFalse(receipt.path("mainResumeAllowed").asBoolean());
    }
}
