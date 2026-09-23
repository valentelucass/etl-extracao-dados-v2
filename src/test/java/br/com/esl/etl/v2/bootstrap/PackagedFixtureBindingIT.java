package br.com.esl.etl.v2.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import br.com.esl.etl.v2.plataforma.qualificacao.QualificationJson;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

/** Offline package contract: no configuration, JDBC, native DLL, source or SQL is used. */
class PackagedFixtureBindingIT {
    @TempDir Path directory;

    @Test
    @Timeout(900)
    void generatedOraclesBindToTheJarAndRejectExplodedOrAlteredBindings() throws Exception {
        final Path jar = Path.of("target/etl-dataexport-v2.jar").toAbsolutePath();
        final String fingerprint = QualificationJson.sha256(jar);
        final Path output = directory.resolve("authored");
        try (var runtime = new PackagedFixtureRuntime(jar);
                var exploded = PackagedFixtureRuntime.exploded()) {
            assertEquals(jar.toRealPath(), runtime.location().toRealPath());
            assertEquals(Path.of("target/classes").toRealPath(), exploded.location().toRealPath());
            assertEquals(fingerprint, runtime.fingerprint());
            assertNotEquals(exploded.fingerprint(), fingerprint);
            runtime.author(output);
            int checked = 0;
            for (final String id : java.util.List.of("sequence-a", "sequence-b")) {
                final Path root = output.resolve("artifact-cases").resolve(id);
                final var sequence = QualificationJson.read(root.resolve("sequence.json"), 65536);
                runtime.verifySequence(root.resolve("sequence.json"));
                for (final var step : sequence.path("steps")) {
                    final Path input = root.resolve(step.path("input").path("file").asText());
                    final Path oracle = root.resolve(step.path("oracle").path("file").asText());
                    final var document = QualificationJson.read(oracle, 16384);
                    assertEquals(fingerprint, document.path("runtimeSha256").asText());
                    runtime.verifyOracle(input, oracle);
                    checked++;
                }
            }
            assertEquals(14, checked);
            final Path root = output.resolve("artifact-cases/sequence-a");
            final var first =
                    QualificationJson.read(root.resolve("sequence.json"), 65536)
                            .path("steps")
                            .get(0);
            final Path input = root.resolve(first.path("input").path("file").asText());
            assertEquals(
                    "LOCAL_SCENARIO_ORACLE_BINDING",
                    assertThrows(
                                    IllegalArgumentException.class,
                                    () -> exploded.verifySequence(root.resolve("sequence.json")))
                            .getMessage());
            final var original =
                    (ObjectNode)
                            QualificationJson.read(
                                    root.resolve(first.path("oracle").path("file").asText()),
                                    16384);
            for (final String field :
                    java.util.List.of("runtimeSha256", "inputSha256", "schemaSha256")) {
                final var altered = original.deepCopy();
                altered.put(
                        field,
                        field.equals("runtimeSha256") ? exploded.fingerprint() : "0".repeat(64));
                final Path negative = root.resolve("negative-" + field + ".json");
                Files.writeString(negative, altered.toString());
                assertEquals(
                        "LOCAL_SCENARIO_ORACLE_BINDING",
                        assertThrows(
                                        IllegalArgumentException.class,
                                        () -> runtime.verifyOracle(input, negative))
                                .getMessage());
            }
            final Path alteredJar = directory.resolve("etl-dataexport-v2.jar");
            Files.copy(jar, alteredJar);
            Files.writeString(
                    alteredJar,
                    "SYNTHETIC_ARTIFACT_CHANGE",
                    java.nio.file.StandardOpenOption.APPEND);
            assertNotEquals(fingerprint, QualificationJson.sha256(alteredJar));
            final Path libraries = Files.createDirectory(directory.resolve("lib"));
            try (var paths = Files.list(jar.getParent().resolve("lib"))) {
                for (final Path library : paths.toList()) {
                    Files.copy(library, libraries.resolve(library.getFileName()));
                }
            }
            try (var alteredRuntime = new PackagedFixtureRuntime(alteredJar)) {
                assertEquals(
                        "LOCAL_SCENARIO_ORACLE_BINDING",
                        assertThrows(
                                        IllegalArgumentException.class,
                                        () ->
                                                alteredRuntime.verifyOracle(
                                                        input,
                                                        root.resolve(
                                                                first.path("oracle")
                                                                        .path("file")
                                                                        .asText())))
                                .getMessage());
            }
        }
    }

    @Test
    void supervisorTestBridgeUsesPackagedRuntimeAndRealAssertions() throws Exception {
        try (var runtime =
                new PackagedFixtureRuntime(
                        Path.of("target/etl-dataexport-v2.jar").toAbsolutePath())) {
            runtime.invokeTest(getClass(), "assertPackagedContext");
            assertThrows(
                    AssertionError.class,
                    () -> runtime.invokeTest(getClass(), "assertionMustPropagate"));
        }
    }

    private void assertPackagedContext() throws Exception {
        final Path jar = Path.of("target/etl-dataexport-v2.jar").toAbsolutePath();
        assertEquals(QualificationJson.sha256(jar), LocalArtifactScenario.runtimeFingerprint());
        assertNotEquals(
                LocalArtifactScenario.class.getClassLoader(),
                com.microsoft.sqlserver.jdbc.SQLServerDataSource.class.getClassLoader());
        org.junit.jupiter.api.Assertions.assertFalse(
                PackagedFixtureRuntime.runIfExploded(getClass(), "assertPackagedContext"));
    }

    private void assertionMustPropagate() {
        org.junit.jupiter.api.Assertions.fail("SYNTHETIC_ASSERTION_PROPAGATION");
    }
}
