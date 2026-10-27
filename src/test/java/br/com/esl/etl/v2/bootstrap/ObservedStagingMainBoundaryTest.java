package br.com.esl.etl.v2.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.plataforma.configuracao.RuntimeConfigurationFactory;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.ObservedExpansionCaptureTemplate;
import br.com.esl.etl.v2.plataforma.resiliencia.RuntimeExitCategory;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

class ObservedStagingMainBoundaryTest {
    @TempDir Path directory;

    @ParameterizedTest
    @EnumSource(ObservedExpansionCaptureTemplate.class)
    void plansTheFourTemplatesThroughTheActualMainEntry(
            final ObservedExpansionCaptureTemplate template) throws Exception {
        final var request = directory.resolve("request.json");
        Files.writeString(request, ObservedStagingRequestTest.document(template.name()).toString());
        final var result =
                invoke(
                        "observed-staging",
                        "plan",
                        "--config",
                        configuration().toString(),
                        "--request",
                        request.toString());
        assertEquals(0, result.exit());
        assertEquals("", result.error());
        final var receipt = new JsonMapper().readTree(result.out());
        assertEquals("PLAN_ONLY", receipt.path("mode").textValue());
        assertEquals(template.templateId(), receipt.path("templateId").intValue());
        assertEquals(50, receipt.path("per").intValue());
        assertEquals(1, receipt.path("maximumInfoCalls").intValue());
        assertEquals(4, receipt.path("maximumDataCalls").intValue());
        assertTrue(receipt.path("resourcePinsChecked").booleanValue());
        assertEquals(0, receipt.path("sourceHttp").intValue());
        assertEquals(0, receipt.path("sqlConnections").intValue());
        assertFalse(receipt.path("infoSchemaObserved").booleanValue());
        assertFalse(receipt.path("physicalAuthorityGranted").booleanValue());
        assertFalse(receipt.path("globalCompletenessProven").booleanValue());
        assertFalse(receipt.path("domainIdentityProven").booleanValue());
        assertFalse(receipt.path("retainedData").booleanValue());
    }

    @Test
    void actualMainRefusesTrialBeforeEitherPathCanBeOpened() {
        // Both paths are invalid. Reading/parsing either would yield code2 instead of CONFIG_AUTH.
        final var result =
                invoke("observed-staging", "rollback-trial", "--config", "\0", "--request", "\0");
        assertEquals(RuntimeExitCategory.CONFIG_AUTH.code(), result.exit());
        assertEquals("", result.out());
        assertEquals(
                "OBSERVED_STAGING_GENUINE_ROOT_ISSUER_AND_OWNER_NOT_BOUND" + System.lineSeparator(),
                result.error());
    }

    @ParameterizedTest
    @ValueSource(strings = {"MISSING_REQUEST", "EXTRA_FLAG"})
    void malformedObservedRouteCannotFallThroughToAnOrdinaryRun(final String variant) {
        final var result =
                variant.equals("MISSING_REQUEST")
                        ? invoke("observed-staging", "plan", "--config", "unused")
                        : invoke(
                                "observed-staging",
                                "rollback-trial",
                                "--config",
                                "unused",
                                "--request",
                                "unused",
                                "--root-authorized=true");
        assertEquals(2, result.exit());
        assertEquals("", result.out());
        assertEquals("OBSERVED_STAGING_CLI_INVALID" + System.lineSeparator(), result.error());
    }

    @Test
    void defaultHelpVersionAndUnknownCommandKeepTheExistingEntryBehavior() {
        final var explicit = invoke("--help");
        final var defaultHelp = invoke();
        assertEquals(0, explicit.exit());
        assertEquals(explicit, defaultHelp);
        assertTrue(explicit.out().contains("observed-staging plan --config"));
        assertTrue(explicit.out().contains("observed-staging rollback-trial --config"));
        assertTrue(
                explicit.out()
                        .contains(
                                "run --dev-bundle <manifest> --profile C78_LOCAL_SHADOW_SQL_PIN_V1"));
        assertTrue(explicit.out().contains("plan --config <arquivo>"));
        final var version = invoke("--version");
        assertEquals(0, version.exit());
        assertEquals("ETL Data Export V2 0.1.0-SNAPSHOT" + System.lineSeparator(), version.out());
        assertEquals("", version.error());
        final var unknown = invoke("unknown-synthetic-command");
        assertEquals(2, unknown.exit());
        assertEquals(explicit.out(), unknown.out());
        assertEquals("Comando desconhecido." + System.lineSeparator(), unknown.error());
    }

    private static Invocation invoke(final String... arguments) {
        final var out = new ByteArrayOutputStream();
        final var err = new ByteArrayOutputStream();
        final int exit;
        try (var output = new PrintStream(out, true, StandardCharsets.UTF_8);
                var error = new PrintStream(err, true, StandardCharsets.UTF_8)) {
            exit =
                    Main.run(
                            arguments,
                            output,
                            error,
                            new Main.RuntimeDependencies(
                                    new Properties(),
                                    Map.of(),
                                    Clock.fixed(
                                            Instant.parse("2026-10-25T10:00:00Z"), ZoneOffset.UTC),
                                    new RuntimeConfigurationFactory()));
        }
        return new Invocation(
                exit, out.toString(StandardCharsets.UTF_8), err.toString(StandardCharsets.UTF_8));
    }

    private record Invocation(int exit, String out, String error) {}

    private Path configuration() throws Exception {
        final String text =
                Files.readString(Path.of("config/application.example.properties"))
                        .replace("dataexport.enabled=false", "dataexport.enabled=true")
                        .replace("shadow.audit.enabled=false", "shadow.audit.enabled=true")
                        .replaceAll("(?m)^# (dataexport\\.[^\\r\\n]+)$", "$1")
                        .replaceAll("(?m)^# (shadow\\.[^\\r\\n]+)$", "$1")
                        .replace("<host-autorizado>", "source.example.test")
                        .replace("<identificador-estavel-nao-secreto>", "fixture-source")
                        .replace("<escopo-nao-secreto>", "fixture-tenant")
                        .replace("127.0.0.1:<porta-local>", "localhost")
                        .replace("trustServerCertificate=true", "trustServerCertificate=false");
        final var values = new Properties();
        values.load(new StringReader(text));
        values.setProperty("dataexport.transport", "GET_WITH_BODY");
        values.setProperty("dataexport.timeout-seconds", "30");
        values.setProperty("dataexport.max-response-bytes", "8388608");
        values.setProperty("dataexport.retry.max-attempts", "1");
        values.setProperty("dataexport.retry.initial-delay-ms", "0");
        values.setProperty("dataexport.retry.max-delay-ms", "0");
        values.setProperty("dataexport.resilience.minimum-request-interval-ms", "3000");
        values.setProperty("dataexport.resilience.max-requests-per-cycle", "20");
        values.setProperty("dataexport.resilience.max-requests-per-workload", "5");
        values.setProperty("dataexport.resilience.step-timeout-seconds", "150");
        values.setProperty("dataexport.resilience.cycle-timeout-seconds", "180");
        values.setProperty("dataexport.resilience.max-retry-after-ms", "0");
        values.setProperty("dataexport.resilience.max-repartitions", "0");
        final var serialized = new StringWriter();
        values.store(serialized, "Local Main metadata fixture; no secret or physical admission.");
        final var path = directory.resolve("configuration.properties");
        Files.writeString(path, serialized.toString());
        return path;
    }
}
