package br.com.esl.etl.v2.plataforma.autorizacao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class AdministeredArtifactManifestTest {
    @TempDir Path directory;
    private static final Instant NOW = Instant.parse("2026-09-08T00:00:00Z");

    private record Installation(Path root, Path jar) {}

    private Installation fixture(
            final String classPath, final java.util.function.Consumer<ObjectNode> change)
            throws Exception {
        return fixture(classPath, change, List.of());
    }

    private Installation fixture(
            final String classPath,
            final java.util.function.Consumer<ObjectNode> change,
            final List<String> extraFiles)
            throws Exception {
        final Path staging =
                Files.createDirectories(directory.resolve(UUID.randomUUID().toString()));
        Files.createDirectories(staging.resolve("lib"));
        Files.writeString(staging.resolve("runtime.properties"), "configuration");
        Files.writeString(staging.resolve("lib/driver.jar"), "independent-dependency-bytes");
        final var metadata = new java.util.jar.Manifest();
        metadata.getMainAttributes().putValue("Manifest-Version", "1.0");
        if (classPath != null) {
            metadata.getMainAttributes().putValue("Class-Path", classPath);
        }
        try (var jar =
                new java.util.jar.JarOutputStream(
                        Files.newOutputStream(staging.resolve("etl-dataexport-v2.jar")),
                        metadata)) {
            jar.putNextEntry(new java.util.jar.JarEntry("entry"));
            jar.write(new byte[] {1, 2, 3});
            jar.closeEntry();
        }
        final var manifest =
                new ObjectMapper()
                        .createObjectNode()
                        .put("block", 54)
                        .put("database", "localhost/ETL_SISTEMA_V2_SHADOW")
                        .put("validUntil", "2027-01-01T00:00:00Z");
        final var entries = manifest.putArray("files");
        final var names =
                new java.util.ArrayList<>(
                        List.of("etl-dataexport-v2.jar", "runtime.properties", "lib/driver.jar"));
        for (final String name : extraFiles) {
            Files.createDirectories(staging.resolve(name).getParent());
            Files.writeString(staging.resolve(name), "independent-nested-probe-class-bytes");
            names.add(name);
        }
        for (final String name : names) {
            entries.addObject()
                    .put("path", name)
                    .put(
                            "sha256",
                            HexFormat.of()
                                    .formatHex(
                                            MessageDigest.getInstance("SHA-256")
                                                    .digest(
                                                            Files.readAllBytes(
                                                                    staging.resolve(name)))));
        }
        change.accept(manifest);
        final byte[] bytes = new ObjectMapper().writeValueAsBytes(manifest);
        final String digest =
                HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        final Path root =
                Files.createDirectories(
                        directory.resolve(
                                manifest.path("block").asInt() == 55
                                                || manifest.path("block").asInt() == 60
                                        ? "app-bloco" + manifest.path("block").asInt()
                                        : "protected-" + UUID.randomUUID()));
        final Path revision = Files.createDirectories(root.resolve(digest.substring(0, 16)));
        Files.createDirectories(revision.resolve("lib"));
        for (final String name : names) {
            Files.createDirectories(revision.resolve(name).getParent());
            Files.copy(staging.resolve(name), revision.resolve(name));
        }
        Files.write(revision.resolve("manifest.json"), bytes);
        return new Installation(root, revision.resolve("etl-dataexport-v2.jar"));
    }

    @Test
    void acceptsPinnedNestedProbeClassesAndStillRejectsTheirModifiedBytes() throws Exception {
        final String nested =
                "probe/br/com/esl/etl/v2/bootstrap/RuntimeUsersPhysicalProbe$Fault.class";
        final var installation =
                fixture(
                        "lib/driver.jar",
                        manifest ->
                                manifest.put("block", 60)
                                        .put("validFrom", "2026-09-09T00:00:00Z")
                                        .put("validUntil", "2026-09-16T00:00:00Z"),
                        List.of(nested));
        final var now = Instant.parse("2026-09-10T00:00:00Z");
        AdministeredArtifactVerifier.verifyInstalled(
                installation.jar(), installation.root(), now, ignored -> {});
        Files.writeString(installation.jar().getParent().resolve(nested), "tampered-probe-class");
        final var failure =
                assertThrows(
                        IOException.class,
                        () ->
                                AdministeredArtifactVerifier.verifyInstalled(
                                        installation.jar(),
                                        installation.root(),
                                        now,
                                        ignored -> {}));
        assertEquals("ADMINISTERED_ARTIFACT_HASH_REJECTED", failure.getMessage());
    }

    @Test
    void acceptsB60OnlyInsideItsOwnWindowWithoutInheritingB55Validity() throws Exception {
        final var installation =
                fixture(
                        "lib/driver.jar",
                        manifest ->
                                manifest.put("block", 60)
                                        .put("validFrom", "2026-09-09T00:00:00Z")
                                        .put("validUntil", "2026-09-16T00:00:00Z"));
        AdministeredArtifactVerifier.verifyInstalled(
                installation.jar(),
                installation.root(),
                Instant.parse("2026-09-09T00:00:00Z"),
                ignored -> {});
        for (final var instant : List.of(NOW, Instant.parse("2026-09-16T00:00:00Z"))) {
            assertThrows(
                    IOException.class,
                    () ->
                            AdministeredArtifactVerifier.verifyInstalled(
                                    installation.jar(),
                                    installation.root(),
                                    instant,
                                    ignored -> {}));
        }
        final var inherited =
                fixture(
                        "lib/driver.jar",
                        manifest ->
                                manifest.put("block", 60)
                                        .put("validFrom", "2026-09-09T00:00:00Z")
                                        .put("validUntil", "2026-10-07T22:34:30.615Z"));
        assertThrows(
                IOException.class,
                () ->
                        AdministeredArtifactVerifier.verifyInstalled(
                                inherited.jar(),
                                inherited.root(),
                                Instant.parse("2026-09-10T00:00:00Z"),
                                ignored -> {}));
    }

    @Test
    void acceptsTheNewProtectedRootOnlyWithTheOriginalValidity() throws Exception {
        final var installation =
                fixture(
                        "lib/driver.jar",
                        manifest ->
                                manifest.put("block", 55)
                                        .put("validUntil", "2026-10-07T22:34:30.615Z"));
        AdministeredArtifactVerifier.verifyInstalled(
                installation.jar(), installation.root(), NOW, ignored -> {});
        assertThrows(
                IOException.class,
                () ->
                        AdministeredArtifactVerifier.verifyInstalled(
                                installation.jar(),
                                installation.root(),
                                Instant.parse("2026-10-07T22:34:30.615Z"),
                                ignored -> {}));
    }

    @Test
    void rejectsAnExtendedBlock55ValidityEvenWhenTheManifestHashMatches() throws Exception {
        final var installation = fixture("lib/driver.jar", manifest -> manifest.put("block", 55));
        assertThrows(
                IOException.class,
                () ->
                        AdministeredArtifactVerifier.verifyInstalled(
                                installation.jar(), installation.root(), NOW, ignored -> {}));
    }

    @Test
    void checksTheRootManifestJarAndEveryDependencyBeforeAcceptingAnInstallation()
            throws Exception {
        final var installation = fixture("lib/driver.jar", ignored -> {});
        final var checked = new java.util.HashSet<Path>();
        AdministeredArtifactVerifier.verifyInstalled(
                installation.jar(), installation.root(), NOW, checked::add);
        assertTrue(checked.contains(installation.root()));
        assertTrue(checked.contains(installation.jar()));
        assertTrue(checked.contains(installation.jar().getParent().resolve("lib/driver.jar")));
        assertEquals(7, checked.size());
        assertThrows(
                IOException.class,
                () ->
                        AdministeredArtifactVerifier.verifyInstalled(
                                installation.jar(), directory, NOW, checked::add));
        assertThrows(
                IOException.class,
                () ->
                        AdministeredArtifactVerifier.verifyInstalled(
                                installation.jar(),
                                installation.root(),
                                NOW,
                                file -> {
                                    throw new IOException("PROTECTION_REFUSED");
                                }));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "changed-file",
                "changed-manifest",
                "missing-file",
                "duplicate",
                "traversal",
                "invalid-hash",
                "scope",
                "expiry",
                "empty-files",
                "missing-required"
            })
    void alteredOrIncompleteInstallationsAreRefused(final String attack) throws Exception {
        final var installation =
                fixture(
                        "lib/driver.jar",
                        manifest -> {
                            final var files =
                                    (com.fasterxml.jackson.databind.node.ArrayNode)
                                            manifest.get("files");
                            switch (attack) {
                                case "duplicate" -> files.add(files.get(0).deepCopy());
                                case "traversal" ->
                                        ((ObjectNode) files.get(0)).put("path", "../outside.jar");
                                case "invalid-hash" ->
                                        ((ObjectNode) files.get(0)).put("sha256", "invalid");
                                case "scope" -> manifest.put("database", "UNAPPROVED");
                                case "expiry" -> manifest.put("validUntil", "2020-01-01T00:00:00Z");
                                case "empty-files" -> files.removeAll();
                                case "missing-required" -> files.remove(1);
                                default -> {
                                    // Byte and file removal cases mutate the installation below.
                                }
                            }
                        });
        final Path revision = installation.jar().getParent();
        if (attack.equals("changed-file")) {
            Files.writeString(revision.resolve("lib/driver.jar"), "changed");
        } else if (attack.equals("changed-manifest")) {
            Files.writeString(
                    revision.resolve("manifest.json"),
                    " ",
                    java.nio.file.StandardOpenOption.APPEND);
        } else if (attack.equals("missing-file")) {
            Files.delete(revision.resolve("lib/driver.jar"));
        }
        assertThrows(
                IOException.class,
                () ->
                        AdministeredArtifactVerifier.verifyInstalled(
                                installation.jar(), installation.root(), NOW, ignored -> {}));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {"../outside.jar", "lib/unlisted.jar", "https://invalid.example/driver.jar"})
    void classpathCannotEscapeTheHashedDependencySet(final String classPath) throws Exception {
        final var installation = fixture(classPath, ignored -> {});
        assertThrows(
                IOException.class,
                () ->
                        AdministeredArtifactVerifier.verifyInstalled(
                                installation.jar(), installation.root(), NOW, ignored -> {}));
    }
}
