package br.com.esl.etl.v2.contratos.caracterizacao.qfnd02;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class Qfnd02OfflineBoundaryTest {

    private static final Path IMPLEMENTATION =
            Path.of("src/test/java/br/com/esl/etl/v2/contratos/caracterizacao/qfnd02");
    private static final Path RESOURCES =
            Path.of("src/test/resources/contracts/v2-012/extensions/q-fnd-02");

    @Test
    void remainsTestOnlyOfflineAndDoesNotTouchRuntimeOrExternalConfiguration() throws IOException {
        try (Stream<Path> mainFiles = Files.walk(Path.of("src/main/java"))) {
            for (final Path mainFile : mainFiles.filter(Files::isRegularFile).toList()) {
                assertFalse(
                        mainFile.toString().toLowerCase().contains("qfnd02"),
                        mainFile + " materializa Q-FND-02 em src/main");
                assertFalse(
                        Files.readString(mainFile).toLowerCase().contains("qfnd02"),
                        mainFile + " referencia Q-FND-02 em src/main");
            }
        }
        final List<String> forbidden =
                List.of(
                        "java.net",
                        "java.sql",
                        "System.getenv",
                        "System.getProperty",
                        "HttpClient",
                        "ProcessBuilder",
                        "DataExportTemplate",
                        "jdbc:",
                        "http://",
                        "https://");
        try (Stream<Path> files = Files.list(IMPLEMENTATION)) {
            for (final Path file :
                    files.filter(Files::isRegularFile)
                            .filter(path -> !path.getFileName().toString().endsWith("Test.java"))
                            .toList()) {
                final String content = Files.readString(file);
                for (final String marker : forbidden) {
                    assertFalse(content.contains(marker), file + " contém boundary proibida");
                }
            }
        }
    }

    @Test
    void keepsAdditiveResourceDirectoriesClosedAtTwoEntityProfilesAndFixtures() throws IOException {
        assertEquals(
                Set.of("fretes-6389.profile.json", "localizacao-8656.profile.json"),
                names(RESOURCES.resolve("profiles")));
        assertEquals(
                Set.of("fretes-6389.synthetic.json", "localizacao-8656.synthetic.json"),
                names(RESOURCES.resolve("fixtures")));
    }

    @Test
    void fixturesRemainSyntheticAndCoordinateFree() throws IOException {
        try (Stream<Path> files = Files.list(RESOURCES.resolve("fixtures"))) {
            for (final Path file : files.filter(Files::isRegularFile).toList()) {
                final String content = Files.readString(file);
                assertTrue(content.contains("\"evidenceClassification\": \"SYNTHETIC_FIXTURE\""));
                assertTrue(content.contains("\"providerEvidence\": \"NOT_EXECUTED\""));
                assertFalse(content.contains("://"));
            }
        }
    }

    @Test
    void receiptBoundaryChecksReparsePointsAndRealPathContainment() throws IOException {
        final String source = Files.readString(IMPLEMENTATION.resolve("Qfnd02ReceiptWriter.java"));
        assertTrue(source.contains("BasicFileAttributes"));
        assertTrue(source.contains("LinkOption.NOFOLLOW_LINKS"));
        assertTrue(source.contains("attributes.isOther()"));
        assertTrue(source.contains("directory.toRealPath()"));
        assertTrue(source.contains("realDirectory.startsWith(realTarget)"));
    }

    private static Set<String> names(final Path path) throws IOException {
        try (Stream<Path> files = Files.list(path)) {
            return files.filter(Files::isRegularFile)
                    .map(item -> item.getFileName().toString())
                    .collect(Collectors.toUnmodifiableSet());
        }
    }
}
