package br.com.esl.etl.v2.plataforma.autorizacao;

import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntryPermission;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.AclFileAttributeView;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;

/**
 * Checks the installed local artifact before SQL; the protected Windows directory is the trust
 * anchor.
 */
final class AdministeredArtifactVerifier {
    private static final Path ROOT = Path.of("C:\\ProgramData\\EslEtlV2\\app-bloco54");
    private static final Path ROOT_B55 = Path.of("C:\\ProgramData\\EslEtlV2\\app-bloco55");
    private static final Path ROOT_B60 = Path.of("C:\\ProgramData\\EslEtlV2\\app-bloco60");
    private static final Set<AclEntryPermission> WRITES =
            Set.of(
                    AclEntryPermission.WRITE_DATA, AclEntryPermission.APPEND_DATA,
                    AclEntryPermission.WRITE_NAMED_ATTRS, AclEntryPermission.WRITE_ATTRIBUTES,
                    AclEntryPermission.DELETE, AclEntryPermission.DELETE_CHILD,
                    AclEntryPermission.WRITE_ACL, AclEntryPermission.WRITE_OWNER);

    private AdministeredArtifactVerifier() {}

    static void verify() throws IOException {
        try {
            final Path jar =
                    Path.of(
                                    AdministeredArtifactVerifier.class
                                            .getProtectionDomain()
                                            .getCodeSource()
                                            .getLocation()
                                            .toURI())
                            .toAbsolutePath()
                            .normalize();
            final Path revision = jar.getParent();
            if (!jar.getFileName().toString().equals("etl-dataexport-v2.jar")
                    || revision == null
                    || !Set.of(ROOT, ROOT_B55, ROOT_B60).contains(revision.getParent())
                    || !revision.getFileName().toString().matches("[a-f0-9]{16}")) {
                throw new IOException("ADMINISTERED_ARTIFACT_LOCATION_REQUIRED");
            }
            final Set<String> writers;
            try (var resource =
                    AdministeredArtifactVerifier.class.getResourceAsStream(
                            "/runtime-artifact-writers.json")) {
                if (resource == null) {
                    throw new IOException("ADMINISTERED_ARTIFACT_WRITERS_REQUIRED");
                }
                final byte[] bytes = resource.readNBytes(2049);
                if (bytes.length > 2048) {
                    throw new IOException("ADMINISTERED_ARTIFACT_WRITERS_LIMIT");
                }
                final var names = json().readTree(bytes);
                if (!names.isArray()
                        || names.size() != 2
                        || !names.get(0).isTextual()
                        || !names.get(1).isTextual()) {
                    throw new IOException("ADMINISTERED_ARTIFACT_WRITERS_INVALID");
                }
                writers = Set.of(names.get(0).textValue(), names.get(1).textValue());
            }
            verifyInstalled(
                    jar,
                    revision.getParent(),
                    java.time.Instant.now(),
                    file -> checkAcl(file, writers));
        } catch (final java.net.URISyntaxException | RuntimeException failure) {
            throw new IOException("ADMINISTERED_ARTIFACT_INVALID", failure);
        }
    }

    @FunctionalInterface
    interface FileProtection {
        void check(Path file) throws IOException;
    }

    static void verifyInstalled(
            final Path jar,
            final Path expectedRoot,
            final java.time.Instant now,
            final FileProtection protection)
            throws IOException {
        try {
            final Path revision = jar.getParent();
            if (!jar.getFileName().toString().equals("etl-dataexport-v2.jar")
                    || revision == null
                    || !expectedRoot.equals(revision.getParent())
                    || !revision.getFileName().toString().matches("[a-f0-9]{16}")) {
                throw new IOException("ADMINISTERED_ARTIFACT_LOCATION_REQUIRED");
            }
            protection.check(expectedRoot);
            protection.check(revision);
            final Path manifest = revision.resolve("manifest.json");
            protection.check(manifest);
            if (!hash(manifest, 65536).startsWith(revision.getFileName().toString())) {
                throw new IOException("ADMINISTERED_MANIFEST_HASH_REJECTED");
            }
            final var document = json().readTree(Files.readAllBytes(manifest));
            final int block =
                    switch (expectedRoot.getFileName().toString()) {
                        case "app-bloco60" -> 60;
                        case "app-bloco55" -> 55;
                        default -> 54;
                    };
            if (document.path("block").asInt() != block
                    || block == 60
                            && (!"2026-09-16T00:00:00Z".equals(document.path("validUntil").asText())
                                    || !"2026-09-09T00:00:00Z"
                                            .equals(document.path("validFrom").asText())
                                    || now.isBefore(
                                            java.time.Instant.parse("2026-09-09T00:00:00Z")))
                    || block == 55
                            && !"2026-10-07T22:34:30.615Z"
                                    .equals(document.path("validUntil").asText())
                    || !document.path("database").asText().equals("localhost/ETL_SISTEMA_V2_SHADOW")
                    || !now.isBefore(
                            java.time.Instant.parse(document.path("validUntil").asText()))) {
                throw new IOException("ADMINISTERED_MANIFEST_SCOPE_REJECTED");
            }
            final var entries = document.path("files");
            if (!entries.isArray() || entries.isEmpty() || entries.size() > 128) {
                throw new IOException("ADMINISTERED_MANIFEST_LIMIT");
            }
            final var seen = new HashSet<String>();
            for (final var entry : entries) {
                final String name = entry.path("path").asText();
                final String expected = entry.path("sha256").asText();
                if (!name.matches("[A-Za-z0-9_./-]+")
                        || name.contains("..")
                        || name.startsWith("/")
                        || !seen.add(name.toLowerCase(java.util.Locale.ROOT))
                        || !expected.matches("[a-f0-9]{64}")) {
                    throw new IOException("ADMINISTERED_MANIFEST_ENTRY_INVALID");
                }
                final Path file = revision.resolve(name);
                for (Path parent = file.getParent();
                        !parent.equals(revision);
                        parent = parent.getParent()) {
                    protection.check(parent);
                }
                protection.check(file);
                if (!hash(file, 67108864).equals(expected)) {
                    throw new IOException("ADMINISTERED_ARTIFACT_HASH_REJECTED");
                }
            }
            if (!seen.contains("etl-dataexport-v2.jar") || !seen.contains("runtime.properties")) {
                throw new IOException("ADMINISTERED_ARTIFACT_ENTRY_MISSING");
            }
            try (var archive = new java.util.jar.JarFile(jar.toFile())) {
                final String dependencies =
                        archive.getManifest().getMainAttributes().getValue("Class-Path");
                if (dependencies == null || dependencies.length() > 16384) {
                    throw new IOException("ADMINISTERED_CLASSPATH_REQUIRED");
                }
                for (final String dependency : dependencies.trim().split(" +")) {
                    if (!dependency.matches("lib/[A-Za-z0-9_.-]+\\.jar")
                            || !seen.contains(dependency.toLowerCase(java.util.Locale.ROOT))) {
                        throw new IOException("ADMINISTERED_DEPENDENCY_UNPINNED");
                    }
                }
            }
        } catch (final RuntimeException failure) {
            throw new IOException("ADMINISTERED_ARTIFACT_INVALID", failure);
        }
    }

    private static JsonMapper json() {
        return JsonMapper.builder()
                .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .build();
    }

    private static void checkAcl(final Path file, final Set<String> writers) throws IOException {
        final var attributes =
                Files.readAttributes(
                        file,
                        java.nio.file.attribute.BasicFileAttributes.class,
                        LinkOption.NOFOLLOW_LINKS);
        if (attributes.isSymbolicLink() || attributes.isOther() || Files.isWritable(file)) {
            throw new IOException("ADMINISTERED_ARTIFACT_MUTABLE_OR_LINKED");
        }
        final var view =
                Files.getFileAttributeView(
                        file, AclFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
        if (view == null) {
            throw new IOException("WINDOWS_ARTIFACT_ACL_REQUIRED");
        }
        validateAcl(view.getOwner().getName(), view.getAcl(), writers);
    }

    static void validateAcl(
            final String owner, final List<AclEntry> entries, final Set<String> writers)
            throws IOException {
        if (!writers.contains(owner) || entries.isEmpty() || entries.size() > 32) {
            throw new IOException("ADMINISTERED_ARTIFACT_OWNER_OR_ACL_REJECTED");
        }
        for (final var entry : entries) {
            if (entry.type() != AclEntryType.ALLOW
                    || !writers.contains(entry.principal().getName())
                            && entry.permissions().stream().anyMatch(WRITES::contains)) {
                throw new IOException("ADMINISTERED_ARTIFACT_WRITER_REJECTED");
            }
        }
    }

    static String hash(final Path file, final long maximumBytes) throws IOException {
        if (Files.size(file) > maximumBytes) {
            throw new IOException("ADMINISTERED_ARTIFACT_FILE_LIMIT");
        }
        try (var input = Files.newInputStream(file)) {
            final var digest = MessageDigest.getInstance("SHA-256");
            final byte[] buffer = new byte[8192];
            long total = 0;
            int count;
            while ((count = input.read(buffer)) != -1) {
                total += count;
                if (total > maximumBytes) {
                    throw new IOException("ADMINISTERED_ARTIFACT_FILE_LIMIT");
                }
                digest.update(buffer, 0, count);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (final java.security.NoSuchAlgorithmException failure) {
            throw new IOException("ARTIFACT_HASH_UNAVAILABLE", failure);
        }
    }
}
