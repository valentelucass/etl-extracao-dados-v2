package br.com.esl.etl.v2.plataforma.qualificacao;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class QualifiedPackageClasspathTest {
    @TempDir Path directory;

    @Test
    void acceptsManifestLauncherAndExactExpandedChildLibrariesInEitherLibraryOrder()
            throws Exception {
        final var payload = payload();
        assertDoesNotThrow(() -> payload.verifyRuntimeClasspath(cp("etl-dataexport-v2.jar")));
        assertDoesNotThrow(
                () ->
                        payload.verifyRuntimeClasspath(
                                cp("etl-dataexport-v2.jar", "lib/a.jar", "lib/b.jar")));
        assertDoesNotThrow(
                () ->
                        payload.verifyRuntimeClasspath(
                                cp("etl-dataexport-v2.jar", "lib/b.jar", "lib/a.jar")));
    }

    @Test
    void refusesAdditionalMissingRepeatedReorderedAndUnsealedEntries() throws Exception {
        final var payload = payload();
        Files.writeString(directory.resolve("foreign.jar"), "synthetic external library");
        for (final String value :
                List.of(
                        cp("etl-dataexport-v2.jar", "lib/a.jar"),
                        cp("etl-dataexport-v2.jar", "lib/a.jar", "lib/a.jar"),
                        cp("lib/a.jar", "etl-dataexport-v2.jar", "lib/b.jar"),
                        cp("etl-dataexport-v2.jar", "lib/a.jar", "foreign.jar"),
                        cp("etl-dataexport-v2.jar", "lib/a.jar", "lib/b.jar", "foreign.jar"),
                        cp("etl-dataexport-v2.jar", "lib/a.jar", "policy.json"))) {
            assertThrows(
                    IllegalArgumentException.class, () -> payload.verifyRuntimeClasspath(value));
        }
    }

    @Test
    void refusesEmptyClasspathSegmentsAndDirectories() throws Exception {
        final var payload = payload();
        for (final String value :
                List.of(
                        "",
                        " ",
                        cp("etl-dataexport-v2.jar") + File.pathSeparator,
                        cp("etl-dataexport-v2.jar", "lib/a.jar", "lib"))) {
            assertThrows(
                    IllegalArgumentException.class, () -> payload.verifyRuntimeClasspath(value));
        }
        assertThrows(IllegalArgumentException.class, () -> payload.verifyRuntimeClasspath(null));
    }

    @Test
    void refusesExternalCopyEvenWhenItsBytesMatchASealedLibrary() throws Exception {
        final var payload = payload();
        Files.copy(directory.resolve("lib/b.jar"), directory.resolve("copy.jar"));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        payload.verifyRuntimeClasspath(
                                cp("etl-dataexport-v2.jar", "lib/a.jar", "copy.jar")));
    }

    private QualifiedPackage payload() throws Exception {
        Files.createDirectories(directory.resolve("lib"));
        final var members = new LinkedHashMap<String, QualifiedPackage.Member>();
        for (final String name :
                List.of("etl-dataexport-v2.jar", "lib/a.jar", "lib/b.jar", "policy.json")) {
            final var file = Files.writeString(directory.resolve(name), "synthetic " + name);
            final String role =
                    name.startsWith("lib/")
                            ? "DEPENDENCY"
                            : name.endsWith(".jar") ? "APPLICATION" : "POLICY";
            members.put(
                    name,
                    new QualifiedPackage.Member(
                            name,
                            Files.size(file),
                            QualificationJson.sha256(file),
                            name.endsWith(".jar") ? "JAR" : "JSON",
                            role));
        }
        return new QualifiedPackage(directory, "a".repeat(64), "b".repeat(64), members);
    }

    private String cp(final String... names) {
        return String.join(
                File.pathSeparator,
                java.util.Arrays.stream(names)
                        .map(name -> directory.resolve(name).toString())
                        .toList());
    }
}
