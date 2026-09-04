package br.com.esl.etl.v2.plataforma.controle;

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

class ControlPlaneSqlContractTest {

    private static final Path DATABASE_DIRECTORY = Path.of("database");
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void keepsTheControlPlaneManifestAndFingerprintAligned() throws Exception {
        final Path manifestPath = DATABASE_DIRECTORY.resolve("manifest/control-plane.json");
        final JsonNode manifest = OBJECT_MAPPER.readTree(manifestPath.toFile());

        assertEquals(4, manifest.path("manifestVersion").asInt());
        assertEquals("V2-020b", manifest.path("roadmapTask").asText());
        assertEquals("ctl", manifest.path("schema").asText());
        assertEquals(
                List.of(
                        "environment_name",
                        "source_instance",
                        "tenant_scope",
                        "entity_name",
                        "execution_mode",
                        "partition_start_utc",
                        "partition_end_exclusive_utc"),
                OBJECT_MAPPER.convertValue(manifest.path("semanticPartitionKey"), List.class));
        assertTrue(manifest.path("runtime").path("proceduresOnly").asBoolean());
        assertEquals(
                "ATOMIC_POSITIVE_PATH_AVAILABLE",
                manifest.path("publication").path("status").asText());
        assertEquals(
                "core.usp_apply_reconcile_publish_execution",
                manifest.path("publication").path("runtimeEntrypoint").asText());
        assertTrue(manifest.path("publication").path("runtimeExecute").asBoolean());
        assertTrue(manifest.path("publication").path("positivePathAvailable").asBoolean());
        assertEquals(
                "ctl.usp_control_plane_publish_execution",
                manifest.path("publication")
                        .path("controlPlaneEntrypoint")
                        .path("procedure")
                        .asText());
        assertEquals(
                "CONTAINED_FAIL_CLOSED",
                manifest.path("publication")
                        .path("controlPlaneEntrypoint")
                        .path("status")
                        .asText());
        assertFalse(
                manifest.path("publication")
                        .path("controlPlaneEntrypoint")
                        .path("runtimeExecute")
                        .asBoolean());
        assertTrue(
                manifest.path("publication")
                        .path("controlPlaneEntrypoint")
                        .path("ownerOnly")
                        .asBoolean());
        assertEquals(
                "ctl.usp_control_plane_recover_stale_executions",
                manifest.path("recoveryProtocol").path("runtimeEntrypoint").asText());
        assertTrue(manifest.path("recoveryProtocol").path("runtimeExecute").asBoolean());
        assertTrue(manifest.path("recoveryProtocol").path("callerParameters").isEmpty());
        assertFalse(manifest.path("recoveryProtocol").path("callerTimestamp").asBoolean());
        assertTrue(
                manifest.path("recoveryProtocol").path("authoritativeDatabaseClock").asBoolean());
        assertEquals(
                "recovered_executions",
                manifest.path("recoveryProtocol").path("resultColumn").asText());
        assertEquals(
                "O(1)", manifest.path("recoveryProtocol").path("resultCardinalityBound").asText());
        assertEquals(
                List.of("core.usp_apply_reconcile_publish_execution"),
                OBJECT_MAPPER.convertValue(
                        manifest.path("runtime").path("crossSchemaGrantedProcedures"), List.class));
        assertTrue(
                manifest.path("runtime")
                        .path("grantedProcedures")
                        .toString()
                        .contains("usp_control_plane_recover_stale_executions"));
        assertTrue(
                manifest.path("runtime")
                        .path("ownerOnlyProcedures")
                        .toString()
                        .contains("usp_control_plane_publish_execution"));
        assertEquals(
                List.of("partition_id", "published_execution_id"),
                OBJECT_MAPPER.convertValue(
                        manifest.path("publication")
                                .path("executionOwnershipForeignKeys")
                                .path("pointer")
                                .path("columns"),
                        List.class));
        assertEquals(
                List.of("partition_id", "execution_id"),
                OBJECT_MAPPER.convertValue(
                        manifest.path("publication")
                                .path("executionOwnershipForeignKeys")
                                .path("event")
                                .path("columns"),
                        List.class));
        assertFalse(manifest.path("stateProtocol").path("genericTransitionCanPromote").asBoolean());
        assertFalse(
                manifest.path("stateProtocol").path("genericTransitionCanReconcile").asBoolean());
        assertFalse(
                manifest.path("leaseProtocol").path("startCallerClockAuthoritative").asBoolean());
        assertTrue(
                manifest.path("leaseProtocol")
                        .path("databaseClockCapturedAfterLeaseLocks")
                        .asBoolean());
        assertEquals(
                "all_persisted_nvarchar_columns",
                manifest.path("semanticTextCollation").path("scope").asText());
        assertEquals(
                "trim_only_ASCII_U+0020_after_length_validation_and_reject_noncanonical_storage",
                manifest.path("semanticTextCollation").path("outerSpacePolicy").asText());
        assertEquals(
                "explicit_Latin1_General_100_BIN2",
                manifest.path("semanticTextCollation")
                        .path("procedureVariableComparison")
                        .asText());
        assertEquals(
                "PROHIBITED",
                manifest.path("textInputProtocol").path("silentPreBodyTruncation").asText());
        assertEquals(
                "trim_ASCII_U+0020_then_require_first_[A-Z]_and_remaining_[A-Z0-9_]_with_length_2_to_64; no_case_fold",
                manifest.path("textInputProtocol").path("reasonCodes").asText());
        assertEquals(
                "physical_rows_without_source_key",
                manifest.path("countGrain").path("unidentified_quarantine_rows").asText());
        assertEquals("PROHIBITED", manifest.path("replayProtocol").path("selfReference").asText());
        assertEquals(
                "STAGING_KERNEL",
                manifest.path("countPhaseProtocol").path("runtimeReservedExactPhase").asText());
        assertTrue(
                manifest.path("countPhaseProtocol")
                        .path("caseVariantsAreDistinctExternalPhases")
                        .asBoolean());
        assertEquals(
                "returns_before_lease_fencing",
                manifest.path("eventIdempotency").path("exactRetry").asText());
        assertTrue(manifest.path("sensitiveStorage").has("payload"));

        final String expectedFingerprint =
                read(DATABASE_DIRECTORY.resolve("manifest/control-plane.sha256"))
                        .strip()
                        .split("\\s+")[0];
        assertEquals(expectedFingerprint, sha256(manifestPath));
    }

    @Test
    void keepsTheMigrationBaselineAndValidatorsAsOneControlPlaneContract() {
        final String migration =
                read(DATABASE_DIRECTORY.resolve("migrations/V003__create_control_plane.sql"));
        final String baseline =
                read(DATABASE_DIRECTORY.resolve("baseline/001_schema_foundation_baseline.sql"));
        final String validator =
                read(DATABASE_DIRECTORY.resolve("validation/003_validate_control_plane.sql"));
        final String exercise =
                read(
                        DATABASE_DIRECTORY.resolve(
                                "validation/004_exercise_control_plane_baseline_rollback.sql"));

        assertTrue(migration.contains("UQ_ctl_execution_partition_semantic_key"));
        assertTrue(migration.contains("UX_ctl_execution_lease_active_partition"));
        assertTrue(migration.contains("STALE_LEASE_RECOVERED"));
        assertTrue(
                migration.contains(
                        "DENY INSERT, UPDATE, DELETE, SELECT ON SCHEMA::ctl TO v2_runtime"));
        assertTrue(
                migration.contains(
                        "plan_version NVARCHAR(128) COLLATE Latin1_General_100_BIN2 NOT NULL"));
        assertTrue(
                migration.contains(
                        "physical_rows = distinct_root_keys + duplicate_rows + unidentified_quarantine_rows"));
        assertTrue(migration.contains("distinct_root_keys = valid_rows + quarantined_root_keys"));
        assertTrue(migration.contains("origin_partition.environment_name = @environment_name"));
        assertTrue(migration.contains("origin_partition.source_instance = @source_instance"));
        assertTrue(migration.contains("origin_partition.tenant_scope = @tenant_scope"));
        assertTrue(migration.contains("origin_partition.entity_name = @entity_name"));
        assertTrue(migration.contains("origin_partition.execution_mode IN ("));
        assertTrue(migration.contains("@replay_of_execution_id = @execution_id"));
        assertTrue(
                migration.contains(
                        "ABS(DATEDIFF_BIG(SECOND, @started_at_utc, @database_now_utc)) > 300"));
        assertTrue(migration.contains("heartbeat_at_utc = @database_now_utc"));
        assertTrue(
                migration.contains(
                        "@terminal_empty_page, @terminal_evidence_kind,\n        @database_now_utc"));
        assertTrue(migration.contains("released_at_utc = @database_now_utc"));
        assertTrue(
                migration.contains(
                        "VALUES (@source_instance, @source_kind, 1, @database_now_utc)"));
        assertTrue(
                migration.contains(
                        "VALUES (@cycle_id, @plan_version, LOWER(@plan_fingerprint), @database_now_utc)"));
        assertFalse(migration.contains("@existing_planned_at_utc"));
        assertTrue(migration.contains("@environment_name NVARCHAR(MAX)"));
        assertTrue(migration.contains("@contract_fingerprint NVARCHAR(MAX)"));
        assertTrue(migration.contains("DATALENGTH(@contract_fingerprint) > 128"));
        assertTrue(migration.contains("@idempotency_key NVARCHAR(MAX)"));
        assertTrue(migration.contains("IF DATALENGTH(@environment_name)"));
        assertTrue(
                migration.indexOf("IF DATALENGTH(@environment_name)")
                        < migration.indexOf("SET @environment_name = LTRIM"));
        assertTrue(
                migration.contains("OUTPUT deleted.next_attempt_number INTO @allocated_attempt"));
        assertTrue(migration.contains("OUTPUT deleted.next_transition_sequence"));
        assertFalse(migration.contains("MAX(attempt_number)"));
        assertFalse(migration.contains("MAX(transition_sequence)"));
        assertTrue(
                migration.contains(
                        "@count_phase COLLATE Latin1_General_100_BIN2 = N'STAGING_KERNEL'"));
        assertTrue(migration.contains("FROM ctl.execution_page_audit WITH (UPDLOCK, HOLDLOCK)"));
        assertTrue(migration.contains("FROM ctl.execution_count WITH (UPDLOCK, HOLDLOCK)"));
        assertTrue(migration.contains("FROM ctl.execution_state_event WITH (UPDLOCK, HOLDLOCK)"));
        assertTrue(migration.contains("THROW 51334"));
        assertTrue(migration.contains("THROW 51335"));
        assertTrue(
                migration.contains(
                        "LEFT(reason_code, 1) COLLATE Latin1_General_100_BIN2 LIKE '[A-Z]'"));
        assertTrue(
                migration.contains(
                        "LEFT(@reason_code, 1) COLLATE Latin1_General_100_BIN2 NOT LIKE '[A-Z]'"));
        assertTrue(
                migration.contains(
                        "Publicação indisponível sem evidência de reconciliação e commit atômico"));
        assertFalse(
                migration.contains(
                        "N'ctl', N'usp_control_plane_publish_execution', N'v2_runtime'"));
        assertTrue(
                migration.contains(
                        "N'ctl', N'usp_control_plane_recover_stale_executions', N'v2_runtime'"));
        assertTrue(
                migration.contains(
                        "CREATE OR ALTER PROCEDURE ctl.usp_control_plane_recover_stale_executions\nAS"));
        assertFalse(migration.contains("@recovered_at_utc"));
        assertFalse(migration.contains("@recovery_cutoff_utc"));
        assertTrue(migration.contains("DECLARE @database_now_utc DATETIME2(3) = SYSUTCDATETIME()"));
        assertTrue(migration.contains("SELECT @recovered_executions AS recovered_executions"));
        assertTrue(migration.contains("CREATE TABLE ctl.execution_publication_event"));
        assertTrue(
                migration.contains(
                        "CONSTRAINT FK_ctl_partition_publication_pointer_partition_execution"));
        assertTrue(
                migration.contains(
                        "CONSTRAINT FK_ctl_execution_publication_event_partition_execution"));
        assertFalse(migration.contains("CREATE TABLE ctl.payload"));
        assertTrue(baseline.contains("V003__create_control_plane.sql"));
        assertTrue(validator.contains("Control plane V2 validado com sucesso."));
        assertTrue(exercise.contains("ROLLBACK TRANSACTION"));
        assertTrue(exercise.contains("atalho manual STAGED para PROMOTED deveria falhar"));
        assertTrue(
                exercise.contains("Runtime ainda possui atalho administrativo ou de publicação"));
        assertTrue(
                exercise.contains("Heartbeat confiou no relógio do caller em vez do SQL Server"));
        assertTrue(exercise.contains("Replay de outro namespace semântico deveria falhar"));
        assertTrue(exercise.contains("Retry tardio exato deve retornar pelo fast-path"));
        assertTrue(
                exercise.contains(
                        "Registro de fonte ou planejamento confiaram no relógio do caller"));
        assertTrue(exercise.contains("COLLATION_KeyA"));
        assertTrue(exercise.contains("N'SYNTHETIC_TENANT   '"));
        assertTrue(exercise.contains("NCHAR(9) + N'COLLATION_Tab' + NCHAR(9)"));
        assertTrue(exercise.contains("(N'_X')"));
        assertTrue(exercise.contains("(N'1X')"));
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
        return HexFormat.of()
                .formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));
    }
}
