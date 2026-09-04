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
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class SchemaFoundationSqlContractTest {

    private static final Path DATABASE_DIRECTORY = Path.of("database");
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void keepsOnlyTheCleanFlywayFoundationInTheActiveMigrationPath() throws IOException {
        final Path migrations = DATABASE_DIRECTORY.resolve("migrations");
        final List<String> migrationNames;
        try (Stream<Path> files = Files.list(migrations)) {
            migrationNames = files.map(path -> path.getFileName().toString()).sorted().toList();
        }

        assertEquals(
                List.of(
                        "V001__create_v2_schema_foundation.sql",
                        "V002__create_v2_database_roles.sql",
                        "V003__create_control_plane.sql",
                        "V004__create_staging_promotion_kernel.sql",
                        "V005__create_staging_lifecycle.sql",
                        "V006__create_observability_data_quality.sql",
                        "V007__create_usuarios_current_history.sql",
                        "V008__create_governed_references.sql",
                        "V009__create_usuario_dimension_current_view.sql"),
                migrationNames);

        final String allMigrations =
                migrationNames.stream()
                        .map(name -> read(DATABASE_DIRECTORY.resolve("migrations").resolve(name)))
                        .reduce("", String::concat);

        assertFalse(allMigrations.contains("CREATE TABLE ctl.execution_audit"));
        assertFalse(allMigrations.contains("CREATE TABLE ctl.page_audit"));
        assertFalse(allMigrations.contains("CREATE TABLE ctl.source_watermark ("));
        assertFalse(allMigrations.contains("CREATE SCHEMA shadow"));
        assertTrue(allMigrations.contains("CREATE TABLE ctl.execution_partition"));
        assertTrue(
                Files.exists(
                        DATABASE_DIRECTORY.resolve(
                                "evidence/historical-shadow-v001-v003/migrations/"
                                        + "V003__create_shadow_audit_procedures.sql")));
    }

    @Test
    void keepsTheBaselineAndValidatorAlignedWithTheSchemaManifest() throws Exception {
        final JsonNode manifest =
                OBJECT_MAPPER.readTree(
                        DATABASE_DIRECTORY.resolve("manifest/schema-foundation.json").toFile());

        assertEquals(3, manifest.path("manifestVersion").asInt());
        assertEquals("V2-019", manifest.path("roadmapTask").asText());
        assertEquals("ctl", manifest.path("flywayHistory").path("schema").asText());
        assertEquals(
                "flyway_schema_history", manifest.path("flywayHistory").path("table").asText());
        assertFalse(
                manifest.path("flywayHistory").path("includedInStructuralComparison").asBoolean());
        assertEquals(
                List.of("ctl", "stg", "core", "ref", "mart", "pub", "recon"),
                OBJECT_MAPPER.convertValue(manifest.path("schemas"), List.class));
        assertEquals("shadow", manifest.path("forbiddenSchemas").get(0).asText());
        assertEquals("v2_schema_owner", manifest.path("schemaOwner").path("name").asText());
        assertEquals("WITHOUT_LOGIN", manifest.path("schemaOwner").path("authentication").asText());
        assertEquals(
                List.of("CONNECT"),
                OBJECT_MAPPER.convertValue(
                        manifest.path("schemaOwner").path("databaseGrants"), List.class));
        assertEquals(
                "DENIED_FOR_DBO_AND_SCHEMA_OWNER",
                manifest.path("schemaOwner").path("publicImpersonation").asText());
        assertTrue(manifest.path("roles").has("v2_migrator"));
        assertTrue(manifest.path("roles").has("v2_runtime"));
        assertTrue(manifest.path("roles").has("v2_retention_governor"));
        assertTrue(manifest.path("roles").has("v2_lifecycle_reviewer"));
        assertTrue(manifest.path("roles").has("v2_lifecycle_operator"));
        assertTrue(manifest.path("roles").has("v2_archive_restorer"));
        assertEquals(
                List.of("dbo.usp_publish_v2_procedure_grant"),
                OBJECT_MAPPER.convertValue(
                        manifest.path("foundationObjects").path("procedures"), List.class));
        assertEquals(
                List.of("dbo.v2_procedure_grant_allowlist"),
                OBJECT_MAPPER.convertValue(
                        manifest.path("foundationObjects").path("userTables"), List.class));
        assertEquals(26, manifest.path("grantPublisher").path("allowedTriplets").asInt());
        assertEquals(
                List.of("ALTER", "TAKE OWNERSHIP"),
                OBJECT_MAPPER.convertValue(
                        manifest.path("grantPublisher").path("publicProcedurePermissionsDenied"),
                        List.class));

        final String baseline =
                read(DATABASE_DIRECTORY.resolve("baseline/001_schema_foundation_baseline.sql"));
        final String validator =
                read(DATABASE_DIRECTORY.resolve("validation/001_validate_schema_foundation.sql"));
        final String exercise =
                read(
                        DATABASE_DIRECTORY.resolve(
                                "validation/002_exercise_schema_foundation_baseline_rollback.sql"));

        assertTrue(baseline.contains("V001__create_v2_schema_foundation.sql"));
        assertTrue(baseline.contains("V002__create_v2_database_roles.sql"));
        assertTrue(baseline.contains("V005__create_staging_lifecycle.sql"));
        assertTrue(baseline.contains("V006__create_observability_data_quality.sql"));
        assertTrue(baseline.contains("V007__create_usuarios_current_history.sql"));
        assertTrue(baseline.contains("V008__create_governed_references.sql"));
        assertTrue(baseline.contains("V009__create_usuario_dimension_current_view.sql"));
        assertTrue(validator.contains("Fundação de schema V2 validada com sucesso."));
        assertTrue(validator.contains("Objeto antecipado antes da migration dona."));
        assertTrue(exercise.contains("ROLLBACK TRANSACTION"));
        assertTrue(exercise.contains("001_reset_historical_shadow_for_schema_foundation.sql"));
    }

    @Test
    void keepsTheManifestFingerprintCurrentAndTheRuntimeWithoutFoundationDml() throws Exception {
        final Path manifestPath = DATABASE_DIRECTORY.resolve("manifest/schema-foundation.json");
        final String expectedFingerprint =
                read(DATABASE_DIRECTORY.resolve("manifest/schema-foundation.sha256"))
                        .strip()
                        .split("\\s+")[0];

        assertEquals(expectedFingerprint, sha256(manifestPath));

        final String rolesMigration =
                read(DATABASE_DIRECTORY.resolve("migrations/V002__create_v2_database_roles.sql"));
        assertTrue(rolesMigration.contains("CREATE ROLE v2_migrator"));
        assertTrue(rolesMigration.contains("CREATE ROLE v2_runtime"));
        assertTrue(rolesMigration.contains("CREATE ROLE v2_retention_governor"));
        assertTrue(rolesMigration.contains("CREATE ROLE v2_lifecycle_reviewer"));
        assertTrue(rolesMigration.contains("CREATE ROLE v2_lifecycle_operator"));
        assertTrue(rolesMigration.contains("CREATE ROLE v2_archive_restorer"));
        assertTrue(rolesMigration.contains("CREATE USER v2_schema_owner WITHOUT LOGIN"));
        assertTrue(
                rolesMigration.contains("ALTER AUTHORIZATION ON SCHEMA::ctl TO v2_schema_owner"));
        assertTrue(rolesMigration.contains("DENY IMPERSONATE ON USER::dbo TO v2_schema_owner"));
        assertTrue(rolesMigration.contains("CREATE TABLE dbo.v2_procedure_grant_allowlist"));
        assertTrue(
                rolesMigration.contains(
                        "DENY SELECT, INSERT, UPDATE, DELETE, ALTER, TAKE OWNERSHIP"));
        assertTrue(rolesMigration.contains("GRANT CONNECT TO v2_runtime"));
        assertTrue(rolesMigration.contains("WITH EXECUTE AS OWNER"));
        assertTrue(rolesMigration.contains("Entrypoint com EXECUTE AS não pode ser publicado."));
        assertTrue(rolesMigration.contains("DENY IMPERSONATE ON USER::dbo TO public"));
        assertTrue(rolesMigration.contains("DENY IMPERSONATE ON USER::v2_schema_owner TO public"));
        assertTrue(
                rolesMigration.contains("ON OBJECT::dbo.usp_publish_v2_procedure_grant TO public"));
        assertTrue(
                rolesMigration.contains(
                        "GRANT EXECUTE ON OBJECT::dbo.usp_publish_v2_procedure_grant TO v2_migrator"));
        assertTrue(rolesMigration.contains("DENY CREATE TABLE TO v2_runtime"));
        assertTrue(rolesMigration.contains("DENY ALTER ON SCHEMA::ctl TO v2_runtime"));
        assertFalse(rolesMigration.contains("GRANT INSERT ON SCHEMA::ctl TO v2_runtime"));
        assertFalse(rolesMigration.contains("GRANT EXECUTE ON SCHEMA::ctl TO v2_runtime"));
        assertTrue(
                read(DATABASE_DIRECTORY.resolve("validation/001_validate_schema_foundation.sql"))
                        .contains("ROLE_OWNERSHIP"));
    }

    private static String read(final Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (final IOException exception) {
            throw new IllegalStateException(
                    "Não foi possível ler o contrato SQL local.", exception);
        }
    }

    private static String sha256(final Path path) throws IOException, NoSuchAlgorithmException {
        final byte[] bytes = Files.readAllBytes(path);
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}
