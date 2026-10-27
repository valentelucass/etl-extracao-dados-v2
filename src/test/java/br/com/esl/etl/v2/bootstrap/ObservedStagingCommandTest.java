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

class ObservedStagingCommandTest {
    @TempDir Path directory;
    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-10-25T10:00:00Z"), ZoneOffset.UTC);

    @ParameterizedTest
    @EnumSource(ObservedExpansionCaptureTemplate.class)
    void plansUsingRealPinnedResourcesWithNoSecretOrPhysicalClaim(
            final ObservedExpansionCaptureTemplate template) throws Exception {
        final var request = directory.resolve("request.json");
        Files.writeString(request, ObservedStagingRequestTest.document(template.name()).toString());
        final var out = new ByteArrayOutputStream();
        final var err = new ByteArrayOutputStream();
        assertEquals(0, run("plan", configuration(1), request, out, err));
        assertEquals("", err.toString(StandardCharsets.UTF_8));
        final var json = new JsonMapper().readTree(out.toByteArray());
        assertEquals("PLAN_ONLY", json.path("mode").textValue());
        assertEquals(template.templateId(), json.path("templateId").intValue());
        assertEquals(50, json.path("per").intValue());
        assertEquals(1, json.path("maximumInfoCalls").intValue());
        assertEquals(4, json.path("maximumDataCalls").intValue());
        assertTrue(json.path("resourcePinsChecked").booleanValue());
        for (final String key :
                new String[] {
                    "physicalAuthorityGranted",
                    "infoSchemaObserved",
                    "globalCompletenessProven",
                    "domainIdentityProven",
                    "retainedData"
                }) {
            assertFalse(json.path(key).booleanValue(), key);
        }
        assertEquals(0, json.path("sourceHttp").intValue());
        assertEquals(0, json.path("sqlConnections").intValue());
    }

    @Test
    void trialCannotTreatASelectorAsRootAuthorityOrTouchAnyDependency() {
        final var out = new ByteArrayOutputStream();
        final var err = new ByteArrayOutputStream();
        try (var output = new PrintStream(out, true, StandardCharsets.UTF_8);
                var error = new PrintStream(err, true, StandardCharsets.UTF_8)) {
            // Null dependencies + invalid NUL paths prove the refusal precedes parsing/loader
            // access.
            assertEquals(
                    RuntimeExitCategory.CONFIG_AUTH.code(),
                    ObservedStagingCommand.run(
                            new String[] {
                                "observed-staging",
                                "rollback-trial",
                                "--config",
                                "\0",
                                "--request",
                                "\0"
                            },
                            output,
                            error,
                            null,
                            null,
                            null,
                            null));
        }
        assertEquals("", out.toString(StandardCharsets.UTF_8));
        assertEquals(
                "OBSERVED_STAGING_GENUINE_ROOT_ISSUER_AND_OWNER_NOT_BOUND" + System.lineSeparator(),
                err.toString(StandardCharsets.UTF_8));
    }

    @Test
    void additionalCallerFlagsCannotSelectAProviderOrClaimAuthority() {
        final var out = new ByteArrayOutputStream();
        final var err = new ByteArrayOutputStream();
        try (var output = new PrintStream(out, true, StandardCharsets.UTF_8);
                var error = new PrintStream(err, true, StandardCharsets.UTF_8)) {
            assertEquals(
                    2,
                    ObservedStagingCommand.run(
                            new String[] {
                                "observed-staging",
                                "plan",
                                "--config",
                                "unused",
                                "--request",
                                "unused",
                                "--root-authorized=true"
                            },
                            output,
                            error,
                            null,
                            null,
                            null,
                            null));
        }
        assertEquals("", out.toString(StandardCharsets.UTF_8));
        assertEquals(
                "OBSERVED_STAGING_CLI_INVALID" + System.lineSeparator(),
                err.toString(StandardCharsets.UTF_8));
    }

    @Test
    void missingRequestCannotEchoCallerMaterialOrLoadConfiguration() {
        final var out = new ByteArrayOutputStream();
        final var err = new ByteArrayOutputStream();
        assertEquals(
                2,
                run(
                        "plan",
                        directory.resolve("unused.properties"),
                        directory.resolve("SENSITIVE_SENTINEL.json"),
                        out,
                        err));
        assertEquals("", out.toString(StandardCharsets.UTF_8));
        assertEquals(
                "OBSERVED_STAGING_PLAN_REFUSED" + System.lineSeparator(),
                err.toString(StandardCharsets.UTF_8));
    }

    @ParameterizedTest
    @ValueSource(ints = {2, 3})
    void plansRejectRetryPolicyThatWouldExceedOneAttempt(final int attempts) throws Exception {
        final var request = directory.resolve("request.json");
        Files.writeString(request, ObservedStagingRequestTest.document("CAP").toString());
        final var out = new ByteArrayOutputStream();
        final var err = new ByteArrayOutputStream();
        assertEquals(2, run("plan", configuration(attempts), request, out, err));
        assertEquals("", out.toString(StandardCharsets.UTF_8));
        assertEquals(
                "OBSERVED_STAGING_PLAN_REFUSED" + System.lineSeparator(),
                err.toString(StandardCharsets.UTF_8));
    }

    private int run(
            final String operation,
            final Path configuration,
            final Path request,
            final ByteArrayOutputStream out,
            final ByteArrayOutputStream err) {
        try (var output = new PrintStream(out, true, StandardCharsets.UTF_8);
                var error = new PrintStream(err, true, StandardCharsets.UTF_8)) {
            return ObservedStagingCommand.run(
                    new String[] {
                        "observed-staging",
                        operation,
                        "--config",
                        configuration.toString(),
                        "--request",
                        request.toString()
                    },
                    output,
                    error,
                    new RuntimeConfigurationFactory(),
                    new Properties(),
                    Map.of(),
                    CLOCK);
        }
    }

    @Test
    void plansRefuseParallelSourceSettingsBeforeAnyRootApiIsNeeded() throws Exception {
        final var configuration = configuration(1);
        final var values = new Properties();
        try (var reader = Files.newBufferedReader(configuration)) {
            values.load(reader);
        }
        values.setProperty("dataexport.resilience.max-in-flight", "2");
        final var serialized = new StringWriter();
        values.store(serialized, "Local metadata fixture, no Root or provider.");
        Files.writeString(configuration, serialized.toString());
        final var request = directory.resolve("parallel-request.json");
        Files.writeString(request, ObservedStagingRequestTest.document("CAP").toString());
        final var out = new ByteArrayOutputStream();
        final var err = new ByteArrayOutputStream();
        assertEquals(2, run("plan", configuration, request, out, err));
        assertEquals("", out.toString(StandardCharsets.UTF_8));
        assertEquals(
                "OBSERVED_STAGING_PLAN_REFUSED" + System.lineSeparator(),
                err.toString(StandardCharsets.UTF_8));
    }

    private Path configuration(final int attempts) throws Exception {
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
        values.setProperty("dataexport.retry.max-attempts", Integer.toString(attempts));
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
        values.store(serialized, "Local metadata fixture. No token or operational authority.");
        final var path = directory.resolve("configuration-" + attempts + ".properties");
        Files.writeString(path, serialized.toString());
        return path;
    }
}
