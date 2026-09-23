package br.com.esl.etl.v2.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.plataforma.autorizacao.DurableAuthorizationException;
import br.com.esl.etl.v2.plataforma.autorizacao.RuntimeAction;
import br.com.esl.etl.v2.plataforma.configuracao.RuntimeConfiguration;
import br.com.esl.etl.v2.plataforma.configuracao.RuntimeConfigurationFactory;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.RuntimeBootstrapTestFixture;
import br.com.esl.etl.v2.plataforma.resiliencia.RuntimeExitCategory;
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

class RuntimeOperationalExecutionTest {
    private static final Instant NOW = Instant.parse("2036-03-19T18:42:10Z");
    @TempDir Path directory;

    @Test
    void freezesOneExplicitColetasDependencyAndKeepsFreshAuthorizationOutOfOccurrenceIdentity()
            throws Exception {
        final var predecessor = document("COLETAS");
        final var parent = document("FRETES").put("dependencyRequest", predecessor.toString());
        final var file = directory.resolve("dag.json");
        Files.writeString(file, parent.toString());
        final var first = RuntimeOperationalRequest.read(configuration(), file);
        assertEquals(
                br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportTemplate.COLETAS,
                first.dependency.template);
        parent.put("invocationId", UUID.randomUUID().toString());
        Files.writeString(file, parent.toString());
        final var next = RuntimeOperationalRequest.read(configuration(), file);
        assertEquals(first.plan.fingerprint(), next.plan.fingerprint());
        assertFalse(first.dependency.invocation.equals(next.dependency.invocation));
        predecessor.put("endExclusive", NOW.plusSeconds(7200).toString());
        parent.put("dependencyRequest", predecessor.toString());
        Files.writeString(file, parent.toString());
        assertThrows(
                IllegalArgumentException.class,
                () -> RuntimeOperationalRequest.read(configuration(), file));
        predecessor.put("dependencyRequest", "{}");
        parent.put("dependencyRequest", predecessor.toString());
        Files.writeString(file, parent.toString());
        assertThrows(
                IllegalArgumentException.class,
                () -> RuntimeOperationalRequest.read(configuration(), file));
    }

    private Path configurationFile() throws Exception {
        String text = Files.readString(Path.of("config/application.example.properties"));
        text =
                text.replace("dataexport.enabled=false", "dataexport.enabled=true")
                        .replace("shadow.audit.enabled=false", "shadow.audit.enabled=true");
        text =
                text.replaceAll("(?m)^# (dataexport\\.[^\\r\\n]+)$", "$1")
                        .replaceAll("(?m)^# (shadow\\.[^\\r\\n]+)$", "$1");
        text =
                text.replace("<host-autorizado>", "source.example.test")
                        .replace("<identificador-estavel-nao-secreto>", "synthetic-source")
                        .replace("<escopo-nao-secreto>", "synthetic-tenant")
                        .replace("127.0.0.1:<porta-local>", "localhost")
                        .replace("trustServerCertificate=true", "trustServerCertificate=false");
        final Path file = directory.resolve("runtime.properties");
        Files.writeString(file, text);
        return file;
    }

    private RuntimeConfiguration configuration() throws Exception {
        return new RuntimeConfigurationFactory()
                .load(
                        configurationFile(),
                        new Properties(),
                        Map.of(),
                        Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private com.fasterxml.jackson.databind.node.ObjectNode document(final String template) {
        return com.fasterxml.jackson.databind.node.JsonNodeFactory.instance
                .objectNode()
                .put("invocationId", UUID.randomUUID().toString())
                .put("executionId", UUID.randomUUID().toString())
                .put("cycleId", UUID.randomUUID().toString())
                .put("template", template)
                .put("mode", "BACKFILL")
                .put("start", NOW.toString())
                .put("endExclusive", NOW.plusSeconds(3600).toString())
                .put("replayOf", "")
                .put("idempotencyKey", UUID.randomUUID().toString())
                .put("businessStart", "2036-03-19")
                .put("businessEnd", "2036-03-19")
                .put("leaseSeconds", "60")
                .put("pageSize", "3")
                .put("maximumPages", "4")
                .put("maximumRows", "100")
                .put("maximumDistinctRoots", "100")
                .put("qualityVersion", "synthetic-quality-1")
                .put("qualityFingerprint", "b".repeat(64))
                .put("compatibilityVersion", "strict-1");
    }

    private Path write(final com.fasterxml.jackson.databind.JsonNode document) throws Exception {
        final Path file = directory.resolve(UUID.randomUUID() + ".json");
        Files.writeString(file, document.toString());
        return file;
    }

    @Test
    void bothOfficialHandlersUseRealPipelineAndRecoverWithoutSourceComposition() throws Exception {
        for (final String template : new String[] {"COLETAS", "FRETES"}) {
            final var configuration = configuration();
            final var request =
                    RuntimeOperationalRequest.read(configuration, write(document(template)));
            final var fixture = new RuntimeBootstrapTestFixture(NOW);
            final RuntimeOperationalExecution.SourceGateways gateways =
                    (config, req, cancel, observation) -> fixture.gateways(observation);
            assertEquals(
                    RuntimeExitCategory.DEGRADED,
                    RuntimeOperationalExecution.execute(
                            configuration,
                            request,
                            RuntimeAction.STATUS,
                            fixture.dataSource(),
                            gateways));
            assertEquals(0, fixture.fetches);
            assertEquals(
                    RuntimeExitCategory.SUCCESS,
                    RuntimeOperationalExecution.execute(
                            configuration,
                            request,
                            RuntimeAction.RUN,
                            fixture.dataSource(),
                            gateways));
            final int fetches = fixture.fetches;
            assertEquals(2, fetches);
            final RuntimeOperationalExecution.SourceGateways forbidden =
                    (config, req, cancel, observation) -> {
                        throw new AssertionError("source cannot be composed for recovery/status");
                    };
            assertEquals(
                    RuntimeExitCategory.SUCCESS,
                    RuntimeOperationalExecution.execute(
                            configuration,
                            request,
                            RuntimeAction.STATUS,
                            fixture.dataSource(),
                            forbidden));
            assertEquals(
                    RuntimeExitCategory.SUCCESS,
                    RuntimeOperationalExecution.execute(
                            configuration,
                            request,
                            RuntimeAction.FORCE_RUN,
                            fixture.dataSource(),
                            forbidden));
            assertEquals(fetches, fixture.fetches);
        }
    }

    @Test
    void requestFingerprintSurvivesNewAuthorizationButBindsLimitsAndRejectsMalformedInput()
            throws Exception {
        final var configuration = configuration();
        final var document = document("COLETAS");
        final var original = RuntimeOperationalRequest.read(configuration, write(document));
        document.put("invocationId", UUID.randomUUID().toString());
        assertEquals(
                original.plan.fingerprint(),
                RuntimeOperationalRequest.read(configuration, write(document)).plan.fingerprint());
        document.put("maximumRows", "99");
        assertFalse(
                original.plan
                        .fingerprint()
                        .equals(
                                RuntimeOperationalRequest.read(configuration, write(document))
                                        .plan
                                        .fingerprint()));
        document.put("unexpected", true);
        final Path invalid = write(document);
        assertThrows(
                IllegalArgumentException.class,
                () -> RuntimeOperationalRequest.read(configuration, invalid));
        final Path duplicate = directory.resolve("duplicate.json");
        Files.writeString(duplicate, "{\"mode\":\"BACKFILL\",\"mode\":\"REPLAY\"}");
        assertThrows(
                IllegalArgumentException.class,
                () -> RuntimeOperationalRequest.read(configuration, duplicate));
        assertFalse(original.toString().contains("source"));
    }

    @Test
    void officialCompositionAndCliRemainDeniedWithoutAdministeredArtifact() throws Exception {
        final var configuration = configuration();
        final Path request = write(document("COLETAS"));
        assertThrows(
                DurableAuthorizationException.class,
                () ->
                        new RuntimeCompositionRoot(configuration)
                                .executeOperationalRequest(request, RuntimeAction.RUN));
        assertThrows(
                DurableAuthorizationException.class,
                () ->
                        new RuntimeCompositionRoot(configuration)
                                .executeOperationalRequest(
                                        request,
                                        RuntimeAction.RUN,
                                        ignored -> {},
                                        new java.io.InputStream() {
                                            @Override
                                            public int read() {
                                                throw new AssertionError(
                                                        "Control must not be opened before authorization");
                                            }
                                        }));
        final var output = new java.io.ByteArrayOutputStream();
        final var error = new java.io.ByteArrayOutputStream();
        try (var out = new java.io.PrintStream(output);
                var err = new java.io.PrintStream(error)) {
            final int code =
                    Main.run(
                            new String[] {
                                "run",
                                "--config",
                                configurationFile().toString(),
                                "--request",
                                request.toString()
                            },
                            out,
                            err,
                            new Main.RuntimeDependencies(
                                    new Properties(),
                                    Map.of(),
                                    Clock.fixed(NOW, ZoneOffset.UTC),
                                    new RuntimeConfigurationFactory()));
            assertEquals(20, code);
            assertTrue(error.toString().contains("UNCONFIGURED"));
            assertEquals("", output.toString());
        }
    }
}
