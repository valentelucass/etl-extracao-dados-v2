package br.com.esl.etl.v2.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

class MainTest {

    @TempDir Path temporaryDirectory;

    @Test
    void temporalPreviewHasNoEffectsAndMalformedOrUnconfiguredRunCannotSucceed() throws Exception {
        final var configuration = temporaryDirectory.resolve("runtime.properties");
        Files.writeString(
                configuration,
                "runtime.environment=LOCAL_SHADOW\nruntime.business-timezone=America/Sao_Paulo\n");
        final String temporal = "config/laboratory/bloco54-temporal-coletas.json";
        final var preview =
                runWithDependencies(
                        "plan", "--config", configuration.toString(), "--temporal", temporal);
        assertEquals(0, preview.exitCode());
        assertTrue(preview.standardOut().contains("TEMPORAL_PREVIEW effects=0 windows=3"));
        final var run =
                runWithDependencies(
                        "run", "--config", configuration.toString(), "--temporal", temporal);
        assertEquals(2, run.exitCode());
        assertFalse(run.standardOut().contains("TEMPORAL_PERSISTED"));
        final var bad = temporaryDirectory.resolve("bad.json");
        Files.writeString(bad, "{\"purpose\":\"unapproved\"}");
        assertEquals(
                2,
                runWithDependencies(
                                "run",
                                "--config",
                                configuration.toString(),
                                "--temporal",
                                bad.toString())
                        .exitCode());
        assertEquals(
                2,
                runWithDependencies(
                                "status",
                                "--config",
                                configuration.toString(),
                                "--temporal",
                                temporal)
                        .exitCode());
        assertEquals(
                2,
                runWithDependencies(
                                "plan",
                                "--config",
                                configuration.toString(),
                                "--temporal",
                                bad.toString())
                        .exitCode());
    }

    @Test
    void shouldPrintHelpWhenNoCommandIsProvided() {
        final CapturedOutput output = run();

        assertEquals(0, output.exitCode());
        assertTrue(output.standardOut().contains("Comandos disponíveis"));
        assertEquals("", output.standardError());
    }

    @Test
    void shouldPrintVersion() {
        final CapturedOutput output = run("--version");

        assertEquals(0, output.exitCode());
        assertTrue(output.standardOut().contains("0.1.0-SNAPSHOT"));
        assertEquals("", output.standardError());
    }

    @Test
    void shouldRejectUnknownCommandWithoutTerminatingTheTestProcess() {
        final CapturedOutput output = run("--unknown");

        assertEquals(2, output.exitCode());
        assertTrue(output.standardError().contains("Comando desconhecido"));
        assertTrue(output.standardOut().contains("Comandos disponíveis"));
    }

    @Test
    void mainShouldReturnNormallyForASuccessfulCommand() {
        final PrintStream originalOut = System.out;
        final ByteArrayOutputStream standardOut = new ByteArrayOutputStream();
        try (PrintStream captured = new PrintStream(standardOut, true, StandardCharsets.UTF_8)) {
            System.setOut(captured);
            Main.main(new String[] {"--version"});
        } finally {
            System.setOut(originalOut);
        }

        assertTrue(standardOut.toString(StandardCharsets.UTF_8).contains("0.1.0-SNAPSHOT"));
    }

    @Test
    void validatesConfigurationAndCreatesADryRunWithoutExternalEffects() throws Exception {
        final Path configuration = temporaryDirectory.resolve("runtime.properties");
        Files.writeString(
                configuration,
                "runtime.environment=LOCAL_SHADOW\nruntime.business-timezone=America/Sao_Paulo\n",
                StandardCharsets.UTF_8);

        final CapturedOutput validation =
                runWithDependencies("config", "validate", "--config", configuration.toString());
        final CapturedOutput dryRun =
                runWithDependencies("dry-run", "--config", configuration.toString());

        assertEquals(0, validation.exitCode());
        assertTrue(validation.standardOut().contains("Preflight válido"));
        assertTrue(
                validation
                        .standardOut()
                        .contains("autorizacaoOperacional=nao-configurada-deny-all"));
        assertEquals("", validation.standardError());
        assertEquals(0, dryRun.exitCode());
        assertTrue(dryRun.standardOut().contains("Dry-run sem efeitos"));
        assertEquals("", dryRun.standardError());
    }

    @Test
    void plansOfflineAndRefusesEveryOperationalHandlerWithTheStableConfigAuthCode()
            throws Exception {
        final Path configuration = temporaryDirectory.resolve("runtime.properties");
        Files.writeString(
                configuration,
                "runtime.environment=LOCAL_SHADOW\nruntime.business-timezone=America/Sao_Paulo\n",
                StandardCharsets.UTF_8);

        final CapturedOutput plan =
                runWithDependencies("plan", "--config", configuration.toString());

        assertEquals(0, plan.exitCode());
        assertTrue(plan.standardOut().contains("Planejamento offline sem efeitos"));
        for (final String command :
                new String[] {
                    "run", "replay", "sweep-preview", "sweep-apply", "force-run", "status"
                }) {
            final CapturedOutput operation =
                    runWithDependencies(command, "--config", configuration.toString());

            assertEquals(20, operation.exitCode());
            assertTrue(operation.standardError().contains("política deny-all"));
            assertFalse(operation.standardOut().contains(configuration.toString()));
            assertFalse(operation.standardError().contains(configuration.toString()));
        }
    }

    @Test
    void rejectsSecretOptionsBeforeReadingConfiguration() {
        final CapturedOutput output =
                runWithDependencies("dry-run", "--config", "missing.properties", "--api-token");

        assertEquals(2, output.exitCode());
        assertTrue(
                output.standardError().contains("Argumentos recusados pela política de segurança"));
        assertTrue(!output.standardError().contains("api-token"));
    }

    @Test
    void neverEchoesAnUnknownArgumentThatMayContainSecretMaterial() {
        final String sensitiveArgument = "--privateKey=synthetic-sensitive-value";

        final CapturedOutput output = runWithDependencies(sensitiveArgument);

        assertEquals(2, output.exitCode());
        assertTrue(!output.standardError().contains(sensitiveArgument));
        assertTrue(!output.standardOut().contains(sensitiveArgument));
    }

    @ParameterizedTest
    @ValueSource(strings = {"passWord", "pwd", "passphrase", "accessKey"})
    void rejectsSecretAliasesWithoutEchoingTheirNameOrValue(final String secretAlias) {
        final String sensitiveArgument = "--" + secretAlias + "=synthetic-sensitive-command-value";

        final CapturedOutput output = runWithDependencies(sensitiveArgument);

        assertEquals(2, output.exitCode());
        assertTrue(
                output.standardError().contains("Argumentos recusados pela política de segurança"));
        assertFalse(output.standardError().contains(secretAlias));
        assertFalse(output.standardError().contains("synthetic-sensitive-command-value"));
        assertFalse(output.standardOut().contains(secretAlias));
        assertFalse(output.standardOut().contains("synthetic-sensitive-command-value"));
    }

    @Test
    void rejectsAnInvalidConfigurationPathWithoutEchoingIt() {
        final String sensitivePath =
                "sensitive-configuration-path" + Character.toString(0) + ".properties";

        final CapturedOutput output =
                runWithDependencies("config", "validate", "--config", sensitivePath);

        assertEquals(2, output.exitCode());
        assertTrue(output.standardError().contains("Configuração inválida."));
        assertFalse(output.standardError().contains("sensitive-configuration-path"));
        assertFalse(output.standardOut().contains("sensitive-configuration-path"));
    }

    @Test
    void snapshotsGraphQlNonSecretsButNeverTheToken() {
        final Main.RuntimeDependencies dependencies =
                new Main.RuntimeDependencies(
                        new Properties(),
                        Map.of(
                                "V2_GRAPHQL_ENABLED", "true",
                                "V2_GRAPHQL_ENDPOINT", "https://graphql.example.test/graphql",
                                "V2_GRAPHQL_TOKEN", "synthetic-sensitive-value"),
                        Clock.fixed(Instant.parse("2026-08-30T15:00:00Z"), ZoneOffset.UTC),
                        new RuntimeConfigurationFactory());

        assertEquals("true", dependencies.environment().get("V2_GRAPHQL_ENABLED"));
        assertEquals(
                "https://graphql.example.test/graphql",
                dependencies.environment().get("V2_GRAPHQL_ENDPOINT"));
        assertEquals("", dependencies.environment().get("V2_GRAPHQL_TOKEN"));
        assertFalse(dependencies.environment().toString().contains("synthetic-sensitive-value"));
    }

    private CapturedOutput run(final String... args) {
        final ByteArrayOutputStream standardOut = new ByteArrayOutputStream();
        final ByteArrayOutputStream standardError = new ByteArrayOutputStream();
        final int exitCode;
        try (PrintStream out = new PrintStream(standardOut, true, StandardCharsets.UTF_8);
                PrintStream error = new PrintStream(standardError, true, StandardCharsets.UTF_8)) {
            exitCode = Main.run(args, out, error);
        }
        return new CapturedOutput(
                exitCode,
                standardOut.toString(StandardCharsets.UTF_8),
                standardError.toString(StandardCharsets.UTF_8));
    }

    private CapturedOutput runWithDependencies(final String... args) {
        final ByteArrayOutputStream standardOut = new ByteArrayOutputStream();
        final ByteArrayOutputStream standardError = new ByteArrayOutputStream();
        final int exitCode;
        try (PrintStream out = new PrintStream(standardOut, true, StandardCharsets.UTF_8);
                PrintStream error = new PrintStream(standardError, true, StandardCharsets.UTF_8)) {
            exitCode =
                    Main.run(
                            args,
                            out,
                            error,
                            new Main.RuntimeDependencies(
                                    new Properties(),
                                    Map.of(),
                                    Clock.fixed(
                                            Instant.parse("2026-08-30T15:00:00Z"), ZoneOffset.UTC),
                                    new RuntimeConfigurationFactory()));
        }
        return new CapturedOutput(
                exitCode,
                standardOut.toString(StandardCharsets.UTF_8),
                standardError.toString(StandardCharsets.UTF_8));
    }

    private record CapturedOutput(int exitCode, String standardOut, String standardError) {}
}
