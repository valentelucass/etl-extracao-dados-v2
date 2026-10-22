package br.com.esl.etl.v2.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.plataforma.configuracao.RuntimeConfiguration;
import br.com.esl.etl.v2.plataforma.configuracao.RuntimeConfigurationFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Input and metadata only: no temporal persistence, authority, datasource or source client. */
class RuntimeTemporalInputIntegrityTest {
    private static final String CANARY = "TEMPORAL_INPUT_PRIVATE_CANARY";
    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-09-08T00:00:00Z"), ZoneOffset.UTC);
    private static final ObjectMapper JSON = new ObjectMapper();

    @TempDir Path directory;

    @ParameterizedTest
    @ValueSource(strings = {"MISSING", "OVERSIZED", "DUPLICATE", "TRAILING"})
    void unreadableOrAmbiguousPolicyCannotProduceAPartialPlanOrExposeItsInput(final String kind)
            throws Exception {
        configuration("LOCAL_V2", "LOCAL_V2", "America/Sao_Paulo");
        final var configurationFile = directory.resolve("configuration.properties");
        final var configurationBefore = Files.readAllBytes(configurationFile);
        final String valid = policy().toString();
        final var accepted = directory.resolve("accepted-policy.json");
        // The read bound is inclusive. Only the extra byte in OVERSIZED must be refused.
        final String acceptedContents =
                "OVERSIZED".equals(kind)
                        ? valid + " ".repeat(16384 - valid.getBytes(StandardCharsets.UTF_8).length)
                        : valid;
        Files.writeString(accepted, acceptedContents);
        assertEquals(3, RuntimeTemporalOperation.read(accepted).result.windows().size());
        final var positive = plan(configurationFile, accepted);
        assertEquals(0, positive.exit());
        assertTrue(positive.out().startsWith("TEMPORAL_PREVIEW effects=0 windows=3"));
        assertEquals("", positive.err());

        final var refused = directory.resolve(CANARY + ".json");
        final String contents =
                switch (kind) {
                    case "MISSING" -> null;
                    case "OVERSIZED" -> acceptedContents + " ";
                    case "DUPLICATE" -> "{\"version\":\"" + CANARY + "\"," + valid.substring(1);
                    case "TRAILING" -> valid + " {\"" + CANARY + "\":true}";
                    default -> throw new AssertionError(kind);
                };
        if (contents != null) {
            Files.writeString(refused, contents);
        }
        final var failure =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> RuntimeTemporalOperation.read(refused));
        assertEquals(
                "OVERSIZED".equals(kind)
                        ? "TEMPORAL_DOCUMENT_LIMIT"
                        : "TEMPORAL_DOCUMENT_UNAVAILABLE",
                failure.getMessage());
        final var reply = plan(configurationFile, refused);
        assertEquals(2, reply.exit());
        assertEquals("", reply.out());
        assertEquals("Pedido temporal inválido.", reply.err().strip());
        assertFalse(reply.err().contains(CANARY));
        assertFalse(reply.err().contains("jdbc:"));
        assertEquals(acceptedContents, Files.readString(accepted));
        assertEquals(
                new String(configurationBefore, StandardCharsets.UTF_8),
                Files.readString(configurationFile));
        if (contents == null) {
            assertFalse(Files.exists(refused));
        } else {
            assertEquals(contents, Files.readString(refused));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"MALFORMED", "TOO_MANY"})
    void invalidBlackoutDeclarationsCannotSilentlyTurnIntoAnExportableCalendar(final String kind)
            throws Exception {
        final var configuration = configuration("LOCAL_V2", "LOCAL_V2", "America/Sao_Paulo");
        final var baseline = policy();
        final var operation = new RuntimeTemporalOperation(baseline);
        assertEquals(3, operation.result.windows().size());
        assertFalse(operation.result.blockedByBlackout());
        final var changed =
                baseline.deepCopy()
                        .put(
                                "blackouts",
                                "MALFORMED".equals(kind)
                                        ? "2024-02-28/2024-02-29/" + CANARY
                                        : String.join(
                                                ",",
                                                Collections.nCopies(65, "2024-02-28/2024-02-29")));
        final var input = directory.resolve(CANARY + "-blackouts.json");
        final String contents = changed.toString();
        Files.writeString(input, contents);
        final var failure =
                assertThrows(
                        IllegalArgumentException.class, () -> RuntimeTemporalOperation.read(input));
        assertEquals(
                "MALFORMED".equals(kind) ? "TEMPORAL_BLACKOUT_SCHEMA" : "TEMPORAL_BLACKOUT_LIMIT",
                failure.getMessage());
        final var reply = plan(directory.resolve("configuration.properties"), input);
        assertEquals(2, reply.exit());
        assertEquals("", reply.out());
        assertEquals("Pedido temporal inválido.", reply.err().strip());
        assertFalse(reply.err().contains(CANARY));
        assertEquals(contents, Files.readString(input));
        assertNotNull(RuntimeTemporalBinding.read(configuration, boundRequest(configuration)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"POLICY_ONLY", "ORDINAL_ONLY", "NON_OBJECT"})
    void temporalBindingCannotBeDowngradedByOmittingHalfOrChangingThePolicyShape(final String kind)
            throws Exception {
        final var configuration = configuration("LOCAL_V2", "LOCAL_V2", "America/Sao_Paulo");
        final var complete = boundRequest(configuration);
        assertNotNull(RuntimeTemporalBinding.read(configuration, complete));
        final var incomplete = complete.deepCopy();
        switch (kind) {
            case "POLICY_ONLY" -> incomplete.remove("temporalWindow");
            case "ORDINAL_ONLY" -> incomplete.remove("temporalPolicy");
            case "NON_OBJECT" -> incomplete.put("temporalPolicy", "[]");
            default -> throw new AssertionError(kind);
        }
        final var before = incomplete.deepCopy();
        final var failure =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> RuntimeTemporalBinding.read(configuration, incomplete));
        assertEquals(
                "NON_OBJECT".equals(kind)
                        ? "TEMPORAL_BINDING_DOCUMENT"
                        : "TEMPORAL_BINDING_PAIR_REQUIRED",
                failure.getMessage());
        assertEquals(before, incomplete);
        assertNotNull(RuntimeTemporalBinding.read(configuration, complete));
    }

    @ParameterizedTest
    @ValueSource(strings = {"SOURCE", "TENANT", "ZONE"})
    void aBoundWindowCannotBeReinterpretedUnderAnotherSourceTenantOrZone(final String kind)
            throws Exception {
        final var configuration = configuration("LOCAL_V2", "LOCAL_V2", "America/Sao_Paulo");
        final var request = boundRequest(configuration);
        assertNotNull(RuntimeTemporalBinding.read(configuration, request));
        final var changed = request.deepCopy();
        if ("ZONE".equals(kind)) {
            final var foreignPolicy =
                    (ObjectNode) JSON.readTree(changed.path("temporalPolicy").textValue());
            foreignPolicy.put("zone", "Etc/UTC");
            changed.put("temporalPolicy", foreignPolicy.toString());
        }
        final var before = changed.deepCopy();
        final var foreign =
                configuration(
                        "SOURCE".equals(kind) ? "FOREIGN_SOURCE" : "LOCAL_V2",
                        "TENANT".equals(kind) ? "FOREIGN_TENANT" : "LOCAL_V2",
                        "America/Sao_Paulo");
        final var failure =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> RuntimeTemporalBinding.read(foreign, changed));
        assertEquals("TEMPORAL_EXECUTION_LOCAL_NAMESPACE_ZONE_REQUIRED", failure.getMessage());
        assertEquals(before, changed);
        assertNotNull(RuntimeTemporalBinding.read(configuration, request));
    }

    private RuntimeConfiguration configuration(
            final String source, final String tenant, final String zone) throws Exception {
        final var file = directory.resolve("configuration.properties");
        if (!Files.exists(file)) {
            Files.writeString(
                    file,
                    Files.readString(Path.of("config/application.example.properties"))
                            .replaceAll("(?m)^# (dataexport\\.[^\\r\\n]+)$", "$1")
                            .replaceAll("(?m)^# (shadow\\.[^\\r\\n]+)$", "$1")
                            .replace("shadow.audit.enabled=false", "shadow.audit.enabled=true")
                            .replace("127.0.0.1:<porta-local>", "localhost")
                            .replace(
                                    "trustServerCertificate=true", "trustServerCertificate=false"));
        }
        return new RuntimeConfigurationFactory()
                .load(file, new Properties(), environment(source, tenant, zone), CLOCK);
    }

    private static Map<String, String> environment(
            final String source, final String tenant, final String zone) {
        return Map.of(
                "V2_DATAEXPORT_ENABLED", "true",
                "V2_DATAEXPORT_BASE_URL", "http://127.0.0.1:19540",
                "V2_DATAEXPORT_SOURCE_INSTANCE", source,
                "V2_DATAEXPORT_TENANT_SCOPE", tenant,
                "V2_DATAEXPORT_TIMEZONE", zone);
    }

    private static ObjectNode policy() throws Exception {
        return (ObjectNode)
                JSON.readTree(
                        Files.readAllBytes(
                                Path.of("config/laboratory/bloco54-temporal-coletas.json")));
    }

    private static ObjectNode boundRequest(final RuntimeConfiguration configuration)
            throws Exception {
        final var blueprint =
                JSON.createObjectNode()
                        .put("invocationId", UUID.randomUUID().toString())
                        .put("executionId", UUID.randomUUID().toString())
                        .put("cycleId", UUID.randomUUID().toString())
                        .put("template", "COLETAS")
                        .put("mode", "BACKFILL")
                        .put("start", "2024-01-01T03:00:00Z")
                        .put("endExclusive", "2024-01-02T03:00:00Z")
                        .put("replayOf", "")
                        .put("idempotencyKey", "blueprint")
                        .put("businessStart", "2024-01-01")
                        .put("businessEnd", "2024-01-01")
                        .put("leaseSeconds", "30")
                        .put("pageSize", "2")
                        .put("maximumPages", "4")
                        .put("maximumRows", "16")
                        .put("maximumDistinctRoots", "16")
                        .put("qualityVersion", "test-quality-v1")
                        .put("qualityFingerprint", "b".repeat(64))
                        .put("compatibilityVersion", "strict-v1");
        return RuntimeTemporalBinding.request(configuration, policy(), blueprint, 1);
    }

    private static Reply plan(final Path configuration, final Path policy) throws Exception {
        final var stdout = new ByteArrayOutputStream();
        final var stderr = new ByteArrayOutputStream();
        final int exit;
        try (var out = new PrintStream(stdout, true, StandardCharsets.UTF_8);
                var err = new PrintStream(stderr, true, StandardCharsets.UTF_8)) {
            exit =
                    Main.run(
                            new String[] {
                                "plan",
                                "--config",
                                configuration.toString(),
                                "--temporal",
                                policy.toString()
                            },
                            out,
                            err,
                            new Main.RuntimeDependencies(
                                    new Properties(),
                                    environment("LOCAL_V2", "LOCAL_V2", "America/Sao_Paulo"),
                                    CLOCK,
                                    new RuntimeConfigurationFactory()));
        }
        return new Reply(
                exit,
                stdout.toString(StandardCharsets.UTF_8),
                stderr.toString(StandardCharsets.UTF_8));
    }

    private record Reply(int exit, String out, String err) {}
}
