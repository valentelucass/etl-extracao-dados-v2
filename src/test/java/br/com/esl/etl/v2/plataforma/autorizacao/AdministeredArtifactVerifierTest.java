package br.com.esl.etl.v2.plataforma.autorizacao;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntryPermission;
import java.nio.file.attribute.AclEntryType;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AdministeredArtifactVerifierTest {
    private static final Set<String> WRITERS = Set.of("admin", "system");

    private static AclEntry entry(final String name, final AclEntryPermission permission) {
        return AclEntry.newBuilder()
                .setType(AclEntryType.ALLOW)
                .setPrincipal(() -> name)
                .setPermissions(permission)
                .build();
    }

    @Test
    void aRuntimeReaderCannotBePromotedToWriterOrOwner() {
        final var reader = entry("runtime", AclEntryPermission.READ_DATA);
        assertDoesNotThrow(
                () ->
                        AdministeredArtifactVerifier.validateAcl(
                                "admin",
                                List.of(reader, entry("system", AclEntryPermission.WRITE_DATA)),
                                WRITERS));
        assertThrows(
                IOException.class,
                () ->
                        AdministeredArtifactVerifier.validateAcl(
                                "runtime", List.of(reader), WRITERS));
        for (final var permission :
                List.of(
                        AclEntryPermission.WRITE_DATA,
                        AclEntryPermission.APPEND_DATA,
                        AclEntryPermission.DELETE_CHILD,
                        AclEntryPermission.WRITE_ACL,
                        AclEntryPermission.WRITE_OWNER)) {
            assertThrows(
                    IOException.class,
                    () ->
                            AdministeredArtifactVerifier.validateAcl(
                                    "admin",
                                    List.of(reader, entry("runtime", permission)),
                                    WRITERS));
        }
    }

    @Test
    void aForeignWriterOrUnknownAclCannotBeHiddenByAnAdministratorEntry() {
        final var admin = entry("admin", AclEntryPermission.WRITE_DATA);
        assertThrows(
                IOException.class,
                () ->
                        AdministeredArtifactVerifier.validateAcl(
                                "admin",
                                List.of(admin, entry("foreign", AclEntryPermission.DELETE)),
                                WRITERS));
        assertThrows(
                IOException.class,
                () -> AdministeredArtifactVerifier.validateAcl("admin", List.of(), WRITERS));
        assertThrows(
                IOException.class,
                () ->
                        AdministeredArtifactVerifier.validateAcl(
                                "admin",
                                List.of(
                                        AclEntry.newBuilder(admin)
                                                .setType(AclEntryType.DENY)
                                                .build()),
                                WRITERS));
    }

    @Test
    void contentChangesAreDetectedAndOversizedFilesAreRefused(@TempDir final Path directory)
            throws Exception {
        final var file = directory.resolve("entry");
        Files.writeString(file, "first");
        final String before = AdministeredArtifactVerifier.hash(file, 5);
        Files.writeString(file, "other");
        assertNotEquals(before, AdministeredArtifactVerifier.hash(file, 5));
        assertThrows(IOException.class, () -> AdministeredArtifactVerifier.hash(file, 4));
        assertThrows(IOException.class, () -> AdministeredArtifactVerifier.verify());
    }
}
