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
                        "V009__create_usuario_dimension_current_view.sql",
                        "V010__create_coletas_shadow_vertical.sql",
                        "V011__create_cotacoes_shadow_vertical.sql",
                        "V012__create_manifestos_shadow_vertical.sql",
                        "V013__create_fretes_shadow_vertical.sql",
                        "V014__create_localizacao_cargas_shadow_vertical.sql",
                        "V015__create_runtime_durable_recovery.sql",
                        "V016__create_windows_runtime_authority.sql",
                        "V017__create_runtime_temporal_plan.sql",
                        "V018__enforce_durable_runtime_consumers.sql",
                        "V019__create_scoped_runtime_observability.sql",
                        "V020__bind_existing_runtime_occurrences.sql",
                        "V021__bind_durable_temporal_intent.sql",
                        "V022__extend_five_vertical_runtime.sql",
                        "V023__correct_scoped_runtime_output_projection.sql",
                        "V024__bind_source_protocols_and_users_runtime.sql",
                        "V025__restore_coletas_extraction_audit.sql",
                        "V026__create_coletas_temporal_reference.sql",
                        "V027__create_coletas_exact_temporal_laboratory.sql",
                        "V028__align_coletas_laboratory_presence_collation.sql",
                        "V029__create_relational_laboratory.sql",
                        "V030__capture_relational_laboratory.sql",
                        "V031__resolve_relational_laboratory.sql",
                        "V032__recompose_relational_laboratory.sql",
                        "V033__align_relational_capture_components.sql",
                        "V034__strengthen_relational_cohorts_and_binding_dates.sql",
                        "V035__seal_relational_reconciliation_completeness.sql",
                        "V036__guard_relational_key_padding.sql",
                        "V037__preserve_relational_mdfe_number_contract.sql",
                        "V038__create_expansion_laboratory_captures.sql",
                        "V039__apply_expansion_laboratory.sql",
                        "V040__capture_expansion_dependencies.sql",
                        "V041__resolve_expansion_relations.sql",
                        "V042__consume_expansion_references.sql",
                        "V043__project_expansion_financial_inputs.sql",
                        "V044__fence_expansion_captures_and_conflicts.sql",
                        "V045__materialize_expansion_invoices.sql",
                        "V046__capture_expansion_freight_terms.sql",
                        "V047__materialize_expansion_revenue.sql",
                        "V048__expansion_revenue_exact_comparison.sql",
                        "V049__expansion_partition_recomposition.sql",
                        "V050__expansion_active_amount_reducers.sql",
                        "V051__expansion_sinistro_array_contract.sql",
                        "V052__create_analytic_raster_laboratory.sql",
                        "V053__apply_analytic_raster_laboratory.sql",
                        "V054__project_analytic_raster_transit.sql",
                        "V055__create_analytic_governed_references.sql",
                        "V056__bind_analytic_sources_and_dimensions.sql",
                        "V057__capture_analytic_freight_attributes.sql",
                        "V058__materialize_analytic_freight_operational.sql",
                        "V059__align_analytic_location_forecast_path.sql",
                        "V060__project_analytic_freight_and_location.sql",
                        "V061__bind_analytic_freight_capture_contract.sql",
                        "V062__align_analytic_freight_capture_application.sql",
                        "V063__bind_analytic_current_and_unloading_branches.sql",
                        "V064__prepare_analytic_manifest_attributes.sql",
                        "V065__bind_analytic_manifest_lifecycle.sql",
                        "V066__align_analytic_manifest_preparation_collation.sql",
                        "V067__materialize_analytic_collectors.sql",
                        "V068__preserve_analytic_manifest_exact_freshness.sql",
                        "V069__link_analytic_manifest_snapshot_cohort.sql",
                        "V070__bind_analytic_manifest_freight_paths.sql",
                        "V071__consume_analytic_owned_fleet_references.sql",
                        "V072__materialize_analytic_manifests.sql",
                        "V073__project_analytic_manifest_consumption.sql",
                        "V074__compare_analytic_manifest_instants.sql",
                        "V075__align_relational_collection_alias_wrapper.sql",
                        "V076__scope_manifest_relations_to_current_components.sql",
                        "V077__compare_relational_manifest_competence_instants.sql",
                        "V078__resolve_manifest_vehicle_roles.sql",
                        "V079__project_analytic_inventory_and_incidents.sql",
                        "V080__project_analytic_financial_consumption.sql",
                        "V081__project_analytic_internal_monitoring.sql",
                        "V082__recognize_analytic_monitoring_quarantine_reasons.sql",
                        "V083__capture_analytic_quote_attributes.sql",
                        "V084__consume_analytic_quote_tariffs.sql",
                        "V085__prepare_analytic_quote_snapshots.sql",
                        "V086__project_analytic_quote_consumption.sql",
                        "V087__bind_analytic_quote_runtime_reference.sql",
                        "V088__bind_analytic_collection_supplements.sql",
                        "V089__prepare_analytic_collection_snapshots.sql",
                        "V090__bind_analytic_collection_regions.sql",
                        "V091__project_analytic_collection_queries.sql",
                        "V092__guard_synthetic_collection_absence.sql",
                        "V093__prove_raster_windows_and_last_observation.sql",
                        "V094__isolate_analytic_materialization_failures.sql",
                        "V095__batch_analytic_manifest_composition_seals.sql",
                        "V096__seal_analytic_scenario_publication.sql",
                        "V097__monitor_analytic_raster_and_materializations.sql",
                        "V098__bind_analytic_scenario_source_windows.sql",
                        "V099__preserve_freight_terminal_omission_freshness.sql",
                        "V100__bind_explicit_integral_source_scope.sql",
                        "V101__accept_bound_integral_freight_capture.sql",
                        "V102__prove_declared_collection_preview.sql",
                        "V103__declare_complete_manifest_collection_sets.sql",
                        "V104__revise_quote_reference_with_preserved_source.sql"),
                migrationNames);

        final String allMigrations =
                migrationNames.stream()
                        .map(name -> read(DATABASE_DIRECTORY.resolve("migrations").resolve(name)))
                        .reduce("", String::concat);

        final String withoutColetasAudit =
                migrationNames.stream()
                        .filter(name -> !name.equals("V025__restore_coletas_extraction_audit.sql"))
                        .map(name -> read(migrations.resolve(name)))
                        .reduce("", String::concat);
        assertFalse(withoutColetasAudit.contains("CREATE TABLE ctl.execution_audit"));
        assertFalse(withoutColetasAudit.contains("CREATE TABLE ctl.page_audit"));
        final String coletasAudit =
                read(migrations.resolve("V025__restore_coletas_extraction_audit.sql"));
        assertTrue(coletasAudit.contains("CREATE TABLE ctl.execution_audit"));
        assertTrue(coletasAudit.contains("CREATE TABLE ctl.page_audit"));
        assertFalse(coletasAudit.contains("GRANT "));
        assertFalse(coletasAudit.contains("CREATE ROLE"));
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

        assertEquals(5, manifest.path("manifestVersion").asInt());
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
        assertEquals(41, manifest.path("grantPublisher").path("allowedTriplets").asInt());
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
        assertTrue(baseline.contains("V010__create_coletas_shadow_vertical.sql"));
        assertTrue(baseline.contains("V011__create_cotacoes_shadow_vertical.sql"));
        assertTrue(baseline.contains("V012__create_manifestos_shadow_vertical.sql"));
        assertTrue(baseline.contains("V013__create_fretes_shadow_vertical.sql"));
        assertTrue(baseline.contains("V014__create_localizacao_cargas_shadow_vertical.sql"));
        assertTrue(baseline.contains("V015__create_runtime_durable_recovery.sql"));
        assertTrue(baseline.contains("V024__bind_source_protocols_and_users_runtime.sql"));
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
