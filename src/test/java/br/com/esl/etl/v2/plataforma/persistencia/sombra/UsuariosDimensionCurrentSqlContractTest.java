package br.com.esl.etl.v2.plataforma.persistencia.sombra;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.Test;

class UsuariosDimensionCurrentSqlContractTest {

    private static final Path DATABASE_DIRECTORY = Path.of("database");
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void keepsTheInternalDimensionManifestBoundToV2033WithoutPublishingAConsumerContract()
            throws Exception {
        final Path manifestPath =
                DATABASE_DIRECTORY.resolve("manifest/usuarios-dimension-current.json");
        final JsonNode manifest = OBJECT_MAPPER.readTree(manifestPath.toFile());

        assertEquals(1, manifest.path("manifestVersion").asInt());
        assertEquals("V2-035b-USUARIOS", manifest.path("roadmapTask").asText());
        assertEquals("IMPLEMENTED_IN_SHADOW", manifest.path("localState").asText());
        assertEquals(
                "V009__create_usuario_dimension_current_view.sql",
                manifest.path("migration").asText());
        assertEquals("V2-033", manifest.path("prerequisite").path("roadmapTask").asText());
        assertEquals("core.usuario", manifest.path("prerequisite").path("current").asText());
        assertEquals(
                "core.usuario_history", manifest.path("prerequisite").path("history").asText());
        assertEquals("core", manifest.path("object").path("schema").asText());
        assertEquals(
                "v_usuario_dimension_current_v1", manifest.path("object").path("name").asText());
        assertEquals(
                List.of(
                        "usuario_id",
                        "environment_name",
                        "source_instance",
                        "tenant_scope",
                        "source_key_token",
                        "source_key_wire_type",
                        "name_presence",
                        "usuario_name",
                        "last_changed_at_utc",
                        "last_seen_at_utc"),
                OBJECT_MAPPER.convertValue(manifest.path("object").path("columns"), List.class));
        assertEquals(
                List.of("usuario_id"),
                OBJECT_MAPPER.convertValue(manifest.path("grain").path("primary"), List.class));
        assertEquals(1, manifest.path("grain").path("maximumRowsPerCurrentUser").asInt());
        assertFalse(manifest.path("grain").path("historyCanMultiplyGrain").asBoolean());
        assertEquals(
                "DEFERRED_TO_V2_037", manifest.path("consumerBoundary").path("pubObject").asText());
        assertEquals(
                "pub.vw_dim_usuarios",
                manifest.path("consumerBoundary").path("legacyAlias").asText());
        assertFalse(manifest.path("consumerBoundary").path("approvedConsumerContract").asBoolean());
        assertEquals(0, manifest.path("security").path("selectGrants").size());
        assertFalse(manifest.path("security").path("publicSelect").asBoolean());
        assertFalse(manifest.path("security").path("runtimeSelect").asBoolean());

        final String recordedFingerprint =
                read(DATABASE_DIRECTORY.resolve("manifest/usuarios-dimension-current.sha256"))
                        .strip()
                        .split("\\s+")[0];
        assertEquals(recordedFingerprint, sha256(manifestPath));
        assertEquals(
                sha256(DATABASE_DIRECTORY.resolve("manifest/usuarios-current-history.json")),
                manifest.path("prerequisite").path("manifestFingerprint").asText());
    }

    @Test
    void keepsV009AsADirectCurrentOnlyProjectionWithoutGrantOrCompatibilityAliases() {
        final String migration =
                read(
                        DATABASE_DIRECTORY.resolve(
                                "migrations/V009__create_usuario_dimension_current_view.sql"));

        assertTrue(migration.contains("CREATE VIEW core.v_usuario_dimension_current_v1"));
        assertTrue(migration.contains("WITH SCHEMABINDING"));
        assertTrue(migration.contains("usuario.source_key AS source_key_token"));
        assertTrue(migration.contains("FROM core.usuario AS usuario"));
        assertTrue(migration.contains("usuario.active = CONVERT(BIT, 1)"));
        assertFalse(migration.contains("usuario_history"));
        assertFalse(migration.contains("pub.vw_dim_usuarios"));
        assertFalse(migration.contains("LTRIM"));
        assertFalse(migration.contains("RTRIM"));
        assertFalse(migration.contains("DISTINCT"));
        assertFalse(migration.contains("CREATE INDEX"));
        assertFalse(migration.contains("GRANT "));
        assertFalse(migration.contains("DENY "));
    }

    @Test
    void keepsStructuralReplaySecurityAndShowplanEvidenceRollbackOnly() {
        final String validator =
                read(
                        DATABASE_DIRECTORY.resolve(
                                "validation/035_validate_usuarios_dimension_current.sql"));
        final String exercise =
                read(
                        DATABASE_DIRECTORY.resolve(
                                "validation/036_exercise_usuarios_dimension_current_rollback.sql"));
        final String showplan =
                read(
                        DATABASE_DIRECTORY.resolve(
                                "validation/037_validate_usuarios_dimension_current_showplan.sql"));
        final String manifestGate =
                read(Path.of("scripts/validation/Test-UsuariosDimensionCurrentManifest.ps1"));
        final String showplanGate =
                read(Path.of("scripts/validation/Test-UsuariosDimensionCurrentShowplan.ps1"));

        assertTrue(validator.contains("SCHEMABINDING"));
        assertTrue(validator.contains("source_key_token"));
        assertTrue(validator.contains("UQ_core_usuario_source"));
        assertTrue(validator.contains("pub.vw_dim_usuarios"));
        assertTrue(validator.contains("public/v2_runtime"));
        assertTrue(validator.contains("Dimensão current de Usuários V2 validada com sucesso."));
        assertTrue(exercise.contains("INTEGER:4101"));
        assertTrue(exercise.contains("STRING:4101"));
        assertTrue(exercise.contains("core.usuario_history"));
        assertTrue(exercise.contains("HAS_PERMS_BY_NAME"));
        assertTrue(exercise.contains("ROLLBACK TRANSACTION"));
        assertTrue(showplan.contains("SET SHOWPLAN_XML ON"));
        assertTrue(showplan.contains("dimension.usuario_id = @usuario_id"));
        assertTrue(showplan.contains("dimension.[source_key_token] = @source_key_token"));
        assertTrue(showplan.contains("USUARIOS_DIMENSION_CURRENT_SHOWPLAN_ROLLED_BACK"));
        assertTrue(manifestGate.contains("Test-UsuariosDimensionCurrentShowplan.ps1"));
        assertTrue(showplanGate.contains("PK_core_usuario"));
        assertTrue(showplanGate.contains("UQ_core_usuario_source"));
        assertTrue(showplanGate.contains("conversionWarnings=0"));
        assertTrue(showplanGate.contains("operationalWarnings=0"));
    }

    private static String read(final Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (final IOException exception) {
            throw new IllegalStateException(
                    "Não foi possível ler o contrato dimensional local.", exception);
        }
    }

    private static String sha256(final Path path) throws IOException, NoSuchAlgorithmException {
        final byte[] bytes = Files.readAllBytes(path);
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}
