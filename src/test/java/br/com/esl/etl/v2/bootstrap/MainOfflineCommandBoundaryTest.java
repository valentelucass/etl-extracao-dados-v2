package br.com.esl.etl.v2.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.plataforma.configuracao.C78LocalSqlTarget;
import br.com.esl.etl.v2.plataforma.configuracao.RuntimeConfigurationFactory;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
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
import org.junit.jupiter.params.provider.ValueSource;

/** Public command denials before bundle materialization, request execution or a provider. */
class MainOfflineCommandBoundaryTest {
    private static final String CANARY = "SYNTHETIC_PRIVATE_ARGUMENT_CANARY";
    private static final String INVALID_PATH = "\0" + CANARY;
    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-10-16T21:00:00Z"), ZoneOffset.UTC);
    @TempDir Path temporary;

    @ParameterizedTest
    @ValueSource(strings = {"--help", "--version", "config"})
    void invalidPublicArityPrintsStaticUsageWithoutEchoingTheExtraArgument(final String command)
            throws Exception {
        final var reply = invoke(new Properties(), Map.of(), command, CANARY);
        assertEquals(2, reply.exit());
        assertTrue(reply.out().contains("Comandos disponíveis:"));
        assertFalse(reply.err().isBlank());
        assertSanitized(reply);
    }

    @ParameterizedTest
    @ValueSource(strings = {"MISSING_PROFILE", "WRONG_SELECTOR", "ENGINE_AND_CATALOG"})
    void ambiguousDevCommandsRefuseBeforeConstructingTheManifestPath(final String shape)
            throws Exception {
        final String[] arguments =
                switch (shape) {
                    case "MISSING_PROFILE" -> new String[] {"run", "--dev-bundle", INVALID_PATH};
                    case "WRONG_SELECTOR" ->
                            new String[] {
                                "run", "--dev-bundle", INVALID_PATH, "--wrong-profile", CANARY
                            };
                    default ->
                            new String[] {
                                "run",
                                "--dev-bundle",
                                INVALID_PATH,
                                "--profile",
                                C78LocalSqlTarget.PROFILE,
                                "--dev-engine",
                                "--dev-catalog"
                            };
                };
        final var reply = invoke(new Properties(), Map.of(), arguments);
        assertEquals(2, reply.exit());
        assertEquals("DEV_WORKSPACE_CLI_INVALID", reply.err().strip());
        assertEquals("", reply.out());
        assertSanitized(reply);
    }

    @Test
    void engineOptInIsRequiredBeforeAnInvalidPathOrEnvironmentCanBeConsidered() throws Exception {
        final var reply =
                invoke(
                        new Properties(),
                        Map.of("V2_DATAEXPORT_SOURCE_INSTANCE", CANARY),
                        "run",
                        "--dev-bundle",
                        INVALID_PATH,
                        "--profile",
                        C78LocalSqlTarget.PROFILE,
                        "--dev-engine");
        assertEquals(20, reply.exit());
        assertEquals("DEV_ENGINE_EXPLICIT_OPT_IN_REQUIRED", reply.err().strip());
        assertEquals("", reply.out());
        assertSanitized(reply);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "ABCDEFABCDEFABCDEFABCDEFABCDEFABCDEFABCDEFABCDEFABCDEFABCDEFABCDEFABCD",
                "abc"
            })
    void engineDispatchRejectsNonCanonicalDigestBeforePathOrEnvironment(final String digest)
            throws Exception {
        final var properties = new Properties();
        properties.setProperty("runtime.dev.engine.enabled", "true");
        final var reply =
                invoke(
                        properties,
                        Map.of("V2_DATAEXPORT_SOURCE_INSTANCE", CANARY),
                        "run",
                        "--dev-bundle",
                        INVALID_PATH,
                        "--profile",
                        C78LocalSqlTarget.PROFILE,
                        "--dev-engine",
                        INVALID_PATH,
                        "--dispatch-sha256",
                        digest);
        assertEquals(20, reply.exit());
        assertEquals("DEV_ENGINE_DISPATCH_DIGEST_INVALID", reply.err().strip());
        assertEquals("", reply.out());
        assertSanitized(reply);
        assertEquals("true", properties.getProperty("runtime.dev.engine.enabled"));
    }

    @Test
    void nonEmptyConfigurationEnvironmentRefusesBeforeManifestPathMaterialization()
            throws Exception {
        final var reply =
                invoke(
                        new Properties(),
                        Map.of("V2_DATAEXPORT_SOURCE_INSTANCE", CANARY),
                        "run",
                        "--dev-bundle",
                        INVALID_PATH,
                        "--profile",
                        C78LocalSqlTarget.PROFILE);
        assertEquals(2, reply.exit());
        assertEquals("DEV_WORKSPACE_CONFIG_ENV_OVERRIDE_REFUSED", reply.err().strip());
        assertEquals("", reply.out());
        assertSanitized(reply);
    }

    @Test
    void blankEnvironmentDoesNotBecomeAnOverrideOrAuthorizeAnUnapprovedProfile() throws Exception {
        final var manifest = temporary.resolve(CANARY + ".json");
        final var reply =
                invoke(
                        new Properties(),
                        Map.of("V2_DATAEXPORT_SOURCE_INSTANCE", " \t"),
                        "run",
                        "--dev-bundle",
                        manifest.toString(),
                        "--profile",
                        "UNAPPROVED");
        assertEquals(20, reply.exit());
        assertEquals("DEV_WORKSPACE_EXPLICIT_OPT_IN_REQUIRED", reply.err().strip());
        assertEquals("", reply.out());
        assertFalse(Files.exists(manifest));
        assertSanitized(reply);
    }

    @Test
    void invalidDevPathPublishesOnlyExceptionTypeAndOwnedFrameWithoutInputContents()
            throws Exception {
        final var reply =
                invoke(
                        new Properties(),
                        Map.of(),
                        "run",
                        "--dev-bundle",
                        INVALID_PATH,
                        "--profile",
                        C78LocalSqlTarget.PROFILE);
        assertEquals(2, reply.exit());
        assertEquals("", reply.out());
        assertTrue(
                reply.err()
                        .contains(
                                "DEV_WORKSPACE_PREPARATION_REFUSED type=java.nio.file.InvalidPathException"));
        assertTrue(reply.err().contains("frame=br.com.esl.etl.v2.bootstrap.Main.runDevWorkspace:"));
        assertFalse(reply.err().contains("DEV_MAIN_PREPARED"));
        assertSanitized(reply);
    }

    @ParameterizedTest
    @ValueSource(strings = {"run", "status"})
    void scopedCommandsReportOnlyTheTwoApprovedTargetGatesBeforeReadingRequest(final String command)
            throws Exception {
        final var request = temporary.resolve(CANARY + "-not-created.json");
        for (final boolean unverifiedTls : new boolean[] {false, true}) {
            final var configuration =
                    configuration(unverifiedTls ? "tls" : "disabled", unverifiedTls);
            final byte[] before = Files.readAllBytes(configuration);
            final var reply =
                    invoke(
                            new Properties(),
                            Map.of(),
                            command,
                            "--config",
                            configuration.toString(),
                            "--request",
                            request.toString());
            assertEquals(2, reply.exit());
            assertEquals("", reply.out());
            assertEquals(
                    "Configuração ou pedido operacional inválido: "
                            + (unverifiedTls
                                    ? "VERIFIED_LOCAL_WORKLOAD_TLS_REQUIRED"
                                    : "WINDOWS_WORKLOAD_TARGET_REQUIRED")
                            + ".",
                    reply.err().strip());
            assertFalse(Files.exists(request));
            assertEquals(
                    new String(before, StandardCharsets.UTF_8), Files.readString(configuration));
            assertSanitized(reply);
        }
    }

    @Test
    void malformedRequestDoesNotExposeItsContentsAfterPureTargetValidation() throws Exception {
        final var configuration = temporary.resolve("strict-metadata.properties");
        Files.writeString(
                configuration,
                baseConfiguration()
                        + "shadow.audit.enabled=true\nshadow.target-kind=LOCAL_EPHEMERAL\n"
                        + "shadow.jdbc-url=jdbc:sqlserver://localhost;"
                        + "databaseName=ETL_SISTEMA_V2_SHADOW;integratedSecurity=true;"
                        + "encrypt=true;trustServerCertificate=false\n");
        final var request = temporary.resolve(CANARY + ".json");
        final String contents = "{\"unapproved\":\"" + CANARY + "\"}";
        Files.writeString(request, contents);
        final var reply =
                invoke(
                        new Properties(),
                        Map.of(),
                        "status",
                        "--config",
                        configuration.toString(),
                        "--request",
                        request.toString());
        assertEquals(2, reply.exit());
        assertEquals("", reply.out());
        assertEquals("Configuração ou pedido operacional inválido.", reply.err().strip());
        assertEquals(contents, Files.readString(request));
        assertSanitized(reply);
    }

    @ParameterizedTest
    @ValueSource(strings = {"dry-run", "plan"})
    void invalidConfigurationCannotProduceASuccessfulOfflinePlanOrEchoItsValue(final String command)
            throws Exception {
        final var configuration = temporary.resolve(CANARY + ".properties");
        final String contents = baseConfiguration() + "unapproved.option=" + CANARY + "\n";
        Files.writeString(configuration, contents);
        final var reply =
                invoke(new Properties(), Map.of(), command, "--config", configuration.toString());
        assertEquals(2, reply.exit());
        assertEquals("", reply.out());
        assertEquals("Configuração inválida.", reply.err().strip());
        assertEquals(contents, Files.readString(configuration));
        assertSanitized(reply);
    }

    private Path configuration(final String name, final boolean unverifiedTls) throws Exception {
        final var file = temporary.resolve(name + ".properties");
        Files.writeString(
                file,
                baseConfiguration()
                        + (unverifiedTls
                                ? "shadow.audit.enabled=true\nshadow.target-kind=LOCAL_EPHEMERAL\n"
                                        + "shadow.jdbc-url=jdbc:sqlserver://localhost;"
                                        + "databaseName=ETL_SISTEMA_V2_SHADOW;integratedSecurity=true;"
                                        + "encrypt=true;trustServerCertificate=true\n"
                                : "shadow.audit.enabled=false\n"));
        return file;
    }

    private static String baseConfiguration() {
        return "runtime.environment=LOCAL_SHADOW\n"
                + "runtime.business-timezone=America/Sao_Paulo\n"
                + "dataexport.enabled=false\ngraphql.enabled=false\n";
    }

    private static Reply invoke(
            final Properties properties,
            final Map<String, String> environment,
            final String... arguments)
            throws Exception {
        final var stdout = new ByteArrayOutputStream();
        final var stderr = new ByteArrayOutputStream();
        final int exit;
        try (var out = new PrintStream(stdout, true, StandardCharsets.UTF_8);
                var err = new PrintStream(stderr, true, StandardCharsets.UTF_8)) {
            exit =
                    Main.run(
                            arguments,
                            out,
                            err,
                            new Main.RuntimeDependencies(
                                    properties,
                                    environment,
                                    CLOCK,
                                    new RuntimeConfigurationFactory()));
        }
        return new Reply(
                exit,
                stdout.toString(StandardCharsets.UTF_8),
                stderr.toString(StandardCharsets.UTF_8));
    }

    private static void assertSanitized(final Reply reply) {
        assertFalse(reply.out().contains(CANARY));
        assertFalse(reply.err().contains(CANARY));
        assertFalse(reply.out().contains("jdbc:sqlserver"));
        assertFalse(reply.err().contains("jdbc:sqlserver"));
    }

    private record Reply(int exit, String out, String err) {}
}
