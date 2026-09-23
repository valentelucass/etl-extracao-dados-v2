package br.com.esl.etl.v2.plataforma.qualificacao;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

/** Full-path validation must observe changes and reject linked ancestors without caching. */
class QualificationJsonPathTest {
    @TempDir Path directory;

    @Test
    void deepRegularFilesAreRehashedAndNonFilesAreRefused() throws Exception {
        final Path parent = Files.createDirectories(directory.resolve("a/b/c/d/e/f/g/h"));
        final Path file = Files.writeString(parent.resolve("input.json"), "{}");
        assertDoesNotThrow(() -> QualificationJson.regular(file));
        final String first = QualificationJson.sha256(file);
        Files.writeString(file, "{\"changed\":true}");
        assertNotEquals(first, QualificationJson.sha256(file));
        assertThrows(IllegalArgumentException.class, () -> QualificationJson.regular(parent));
        assertThrows(
                IllegalArgumentException.class,
                () -> QualificationJson.regular(parent.resolve("missing.json")));
    }

    @Test
    @Timeout(30)
    void replacingAValidatedAncestorWithAJunctionOrLinkIsRefusedOnTheNextRead() throws Exception {
        final Path current = Files.createDirectory(directory.resolve("current"));
        final Path first = Files.writeString(current.resolve("input.json"), "{}");
        assertDoesNotThrow(() -> QualificationJson.regular(first));
        final Path other = Files.createDirectories(directory.resolve("other/deep"));
        Files.writeString(other.resolve("input.json"), "{}");
        Files.delete(first);
        Files.delete(current);
        link(current, other);
        try {
            assertEquals(
                    "QUAL_FILE_LINK",
                    assertThrows(
                                    IllegalArgumentException.class,
                                    () -> QualificationJson.sha256(current.resolve("input.json")))
                            .getMessage());
            final Path nested = Files.createDirectories(other.resolve("nested/child"));
            Files.writeString(nested.resolve("input.json"), "{}");
            assertEquals(
                    "QUAL_FILE_LINK",
                    assertThrows(
                                    IllegalArgumentException.class,
                                    () ->
                                            QualificationJson.regular(
                                                    current.resolve("nested/child/input.json")))
                            .getMessage());
        } finally {
            // Remove only the test-owned link, before JUnit cleans its private directory.
            Files.delete(current);
        }
        assertTrue(Files.isRegularFile(other.resolve("input.json")));
    }

    private void link(final Path link, final Path target) throws Exception {
        if (!System.getProperty("os.name").startsWith("Windows")) {
            Files.createSymbolicLink(link, target);
            return;
        }
        final Path log = directory.resolve("junction.log");
        final var process =
                new ProcessBuilder(
                                "pwsh.exe",
                                "-NoProfile",
                                "-NonInteractive",
                                "-Command",
                                "New-Item -ItemType Junction -ErrorAction Stop -Path '"
                                        + link.toString().replace("'", "''")
                                        + "' -Target '"
                                        + target.toString().replace("'", "''")
                                        + "' | Out-Null")
                        .redirectErrorStream(true)
                        .redirectOutput(log.toFile())
                        .start();
        try {
            assertTrue(process.waitFor(10, TimeUnit.SECONDS), "junction helper exceeded its bound");
            assertEquals(0, process.exitValue(), "junction helper failed");
        } finally {
            if (process.isAlive()) {
                process.descendants().forEach(ProcessHandle::destroyForcibly);
                process.destroyForcibly();
                assertTrue(process.waitFor(5, TimeUnit.SECONDS));
            }
        }
    }
}
