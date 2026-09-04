package br.com.esl.etl.v2.plataforma.persistencia.staging;

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

class StagingLifecycleSqlContractTest {

    private static final Path DATABASE_DIRECTORY = Path.of("database");
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void keepsTheCompleteLocalLifecycleManifestAndFingerprintAligned() throws Exception {
        final Path manifestPath = DATABASE_DIRECTORY.resolve("manifest/staging-lifecycle.json");
        final JsonNode manifest = OBJECT_MAPPER.readTree(manifestPath.toFile());

        assertEquals(3, manifest.path("manifestVersion").asInt());
        assertEquals("V2-045a", manifest.path("roadmapTask").asText());
        assertEquals("COMPLETE_LOCAL", manifest.path("localState").asText());
        assertEquals(
                "EXTERNAL_HOLD", manifest.path("externalActivationGate").path("status").asText());
        assertEquals(
                "V2-045b", manifest.path("externalActivationGate").path("roadmapTask").asText());
        assertEquals(0, manifest.path("defaultState").path("retentionPoliciesSeeded").asInt());
        assertFalse(manifest.path("defaultState").path("automaticPurge").asBoolean());
        assertFalse(manifest.path("defaultState").path("productiveTtlApproved").asBoolean());
        assertEquals(
                List.of("BLOCKED", "FAILED", "CANCELLED", "DEGRADED"),
                asList(manifest.path("terminalStates").path("nonPublished")));
        assertEquals(
                List.of("SKIPPED", "NOT_APPLICABLE"),
                asList(manifest.path("terminalStates").path("noLoad")));
        assertEquals(7, manifest.path("objects").path("ctlTables").size());
        assertEquals(14, manifest.path("objects").path("reconTables").size());
        assertEquals(8, manifest.path("objects").path("publicProcedures").size());
        assertEquals(5, manifest.path("objects").path("internalProcedures").size());
        assertEquals(4, manifest.path("objects").path("functions").size());
        assertEquals(17, manifest.path("objects").path("indexes").size());
        assertEquals(10, manifest.path("archive").path("liveSources").size());
        assertEquals(10, manifest.path("archive").path("archiveTables").size());
        assertTrue(manifest.path("archive").path("cryptographicContentAttestation").asBoolean());
        assertEquals(
                "staging-archive-manifest-v3",
                manifest.path("archive").path("manifestFingerprintVersion").asText());
        assertEquals(
                List.of("stg.execution_candidate", "stg.execution_record"),
                asList(manifest.path("purge").path("hardDeleteAllowlist")));
        assertEquals(
                "FAIL_CLOSED", manifest.path("purge").path("reappearedStagingOnRetry").asText());
        assertEquals(
                "BASE_ROW_MATCHES_DISTINCT_DATA_OWNER_AND_COMPLIANCE_EVIDENCE_AND_ROLES_WITH_NO_REVOKED_EVENT",
                manifest.path("eligibility").path("policyRatification").asText());
        assertEquals(
                "BOUNDED_CONTIGUOUS_SEMANTIC_CHAIN_ORIGIN_CARDINALITY_CLOCK_AND_LAST_EVENT_MUST_MATCH_EXECUTION_ATTEMPT",
                manifest.path("eligibility").path("terminalStateLedger").asText());
        assertEquals(
                "BOUNDED_CORRELATED_ITVF_BEFORE_OVERSIZED_FILTER_TOP_AND_CUMULATIVE_ADMISSION",
                manifest.path("eligibility").path("extensionArchiveBudget").asText());
        assertEquals(
                4096,
                manifest.path("safetyLimits")
                        .path("maximumLegalHoldLedgerRowsPerExecution")
                        .asInt());
        assertEquals(
                7,
                manifest.path("safetyLimits")
                        .path("maximumTerminalStateEventsPerExecution")
                        .asInt());
        assertFalse(manifest.path("restore").path("repopulatesActiveStaging").asBoolean());
        assertEquals(0, manifest.path("restore").path("contentReaderEntrypoints").size());
        assertFalse(manifest.path("leastPrivilege").path("v2RuntimeLifecycleExecute").asBoolean());
        assertEquals(
                "DISTINCT_DATA_OWNER_AND_COMPLIANCE_EVIDENCE",
                manifest.path("logLifecycle").path("ratification").asText());
        assertEquals(
                "FAIL_CLOSED_BEFORE_DISABLED_OR_LEGAL_HOLD_RETURN",
                manifest.path("logLifecycle").path("inertPolicyHistory").asText());
        assertEquals(
                "dbo.usp_publish_v2_procedure_grant",
                manifest.path("leastPrivilege").path("migrationGrantPublisher").asText());
        assertFalse(
                manifest.path("localValidation").path("showplan").path("scaleClaim").asBoolean());
        assertEquals(10, manifest.path("localValidation").path("rollbackExercises").size());

        final String expectedFingerprint =
                read(DATABASE_DIRECTORY.resolve("manifest/staging-lifecycle.sha256"))
                        .strip()
                        .split("\\s+")[0];
        assertEquals(expectedFingerprint, sha256(manifestPath));
    }

    @Test
    void keepsPlanArchivePurgeRestoreAndLogLifecycleFailClosed() {
        final String migration =
                read(DATABASE_DIRECTORY.resolve("migrations/V005__create_staging_lifecycle.sql"));
        final String validator =
                read(DATABASE_DIRECTORY.resolve("validation/012_validate_staging_lifecycle.sql"));
        final String exercise =
                read(
                        DATABASE_DIRECTORY.resolve(
                                "validation/013_exercise_staging_lifecycle_rollback.sql"));
        final String ratification =
                read(
                        DATABASE_DIRECTORY.resolve(
                                "validation/014_exercise_staging_policy_ratification_rollback.sql"));
        final String migrator =
                read(
                        DATABASE_DIRECTORY.resolve(
                                "validation/015_exercise_staging_lifecycle_migrator_rollback.sql"));
        final String holdLedger =
                read(
                        DATABASE_DIRECTORY.resolve(
                                "validation/016_exercise_staging_legal_hold_integrity_rollback.sql"));
        final String fingerprint =
                read(
                        DATABASE_DIRECTORY.resolve(
                                "validation/018_exercise_staging_fingerprint_ascii_rollback.sql"));
        final String holdRetry =
                read(
                        DATABASE_DIRECTORY.resolve(
                                "validation/019_exercise_staging_legal_hold_retry_integrity_rollback.sql"));
        final String terminalLedger =
                read(
                        DATABASE_DIRECTORY.resolve(
                                "validation/020_exercise_staging_terminal_ledger_integrity_rollback.sql"));
        final String usuariosMigration =
                read(
                        DATABASE_DIRECTORY.resolve(
                                "migrations/V007__create_usuarios_current_history.sql"));
        final String lateSidecar =
                read(
                        DATABASE_DIRECTORY.resolve(
                                "validation/029_exercise_usuarios_late_sidecar_rollback.sql"));
        final String runner = read(Path.of("scripts/validation/Invoke-ProgressiveDataGate.ps1"));
        final String logLifecycle =
                read(Path.of("scripts/operations/Invoke-GovernedLogLifecycle.ps1"));
        final String logLifecycleTest =
                read(Path.of("scripts/validation/Test-GovernedLogLifecycle.ps1"));
        final String showplan =
                read(Path.of("scripts/validation/Test-StagingLifecycleShowplan.ps1"));
        final String baseline =
                read(DATABASE_DIRECTORY.resolve("baseline/001_schema_foundation_baseline.sql"));
        final String logback = read(Path.of("src/main/resources/logback.xml"));

        for (final String token :
                List.of(
                        "DATA_OWNER_APPROVED",
                        "COMPLIANCE_APPROVED",
                        "terminal_at_utc <= @cutoff_at_utc",
                        "@Resource = N'V2_STAGING_LIFECYCLE'",
                        "ctl.ufn_invalid_staging_legal_hold(@execution_id)",
                        "data_owner_evidence_fingerprint COLLATE Latin1_General_100_BIN2 <>",
                        "release_authority_evidence_fingerprint IS NOT NULL",
                        "release_owner_role IS NOT NULL",
                        "ctl.ufn_invalid_execution_state_ledger",
                        "IX_ctl_staging_legal_hold_execution",
                        "TOP (@remaining_hold_ledger_probe_rows + 1)",
                        "CREATE FUNCTION recon.ufn_staging_lifecycle_extension_archive_budget",
                        "extension_budget.additional_archive_bytes",
                        "staging-archive-manifest-v3",
                        "archive-content-set-v1",
                        "archive-row-json-v1",
                        "INSERT INTO recon.execution_promotion_result_archive",
                        "Retry de purge encontrou staging reaparecido",
                        "DELETE stage_candidate",
                        "DELETE stage_record",
                        "CAST(1 AS BIT) AS read_only",
                        "usp_publish_v2_procedure_grant")) {
            assertTrue(migration.contains(token), token);
        }
        assertFalse(migration.contains("CREATE OR ALTER PROCEDURE recon.usp_read_staging_restore"));
        assertFalse(migration.contains("TRUNCATE TABLE"));
        assertFalse(migration.contains("ON DELETE CASCADE"));
        assertFalse(migration.contains("retention_days DEFAULT"));

        for (final String token :
                List.of(
                        "TOP (@maximum_extension_rows + 1)",
                        "INDEX(UQ_stg_usuario_record_execution_stage), FORCESEEK",
                        "typed.execution_id = @execution_id",
                        "THROW 51703")) {
            assertTrue(usuariosMigration.contains(token), token);
        }
        assertTrue(lateSidecar.contains("@late_sidecar_error <> 51703"));
        assertTrue(lateSidecar.contains("ROLLBACK TRANSACTION"));

        for (final String token :
                List.of(
                        "@expected_index_keys",
                        "@expected_foreign_key_columns",
                        "@expected_fingerprint_parameters",
                        "ufn_staging_lifecycle_extension_archive_budget",
                        "@maximum_extension_rows",
                        "type_definition.name = N'nvarchar'",
                        "@purge_delete_statement_count",
                        "foreign_key.is_not_trusted = 0",
                        "actual_key.is_descending_key = 0",
                        "ATTACK_SURFACE",
                        "Lifecycle governado de staging V2 validado com sucesso.")) {
            assertTrue(validator.contains(token), token);
        }
        assertTrue(exercise.contains("O dry-run alterou staging."));
        assertTrue(exercise.contains("O purge ultrapassou o conjunto terminal autorizado."));
        assertTrue(exercise.contains("O purge removeu evidência durável ou estado core."));
        assertTrue(exercise.contains("O restore repopulou staging ativo."));
        assertTrue(ratification.contains("@ratification_error <> 51524"));
        assertTrue(migrator.contains("EXECUTE AS USER = N'v2_migrator_lifecycle_probe'"));
        assertTrue(migrator.contains("EXECUTE AS USER = N'v2_schema_owner'"));
        assertTrue(migrator.contains("@forbidden_grant_error <> 51222"));
        assertTrue(migrator.contains("@forbidden_grant_count_after <> 0"));
        assertTrue(migrator.contains("HAS_PERMS_BY_NAME(N'dbo', N'USER', N'IMPERSONATE')"));
        assertTrue(holdLedger.contains("@ledger_error <> 51526"));
        assertTrue(holdLedger.contains("@null_release_fingerprint_error <> 547"));
        assertTrue(holdLedger.contains("@null_release_owner_error <> 547"));
        assertTrue(fingerprint.contains("@fingerprint_error <> 51503"));
        assertTrue(fingerprint.contains("NCHAR(0xFF11)"));
        assertTrue(
                fingerprint.contains(
                        "@non_ascii_fingerprint, 1, @owner_evidence_fingerprint, N'data-owner'"));
        assertTrue(fingerprint.contains("@compliance_evidence_fingerprint, N'compliance'"));
        assertTrue(holdRetry.contains("@retry_error <> 51526"));
        assertTrue(terminalLedger.contains("@terminal_ledger_error <> 51527"));
        assertTrue(runner.contains("018_exercise_staging_fingerprint_ascii_rollback.sql"));
        assertTrue(runner.contains("019_exercise_staging_legal_hold_retry_integrity_rollback.sql"));
        assertTrue(runner.contains("020_exercise_staging_terminal_ledger_integrity_rollback.sql"));
        assertTrue(runner.contains("029_exercise_usuarios_late_sidecar_rollback.sql"));
        assertTrue(showplan.contains("operationalWarnings=0"));
        assertTrue(showplan.contains("IX_ctl_execution_attempt_lifecycle"));
        assertTrue(showplan.contains("UQ_stg_usuario_record_execution_stage"));
        assertTrue(showplan.contains("typedBudgetSeek=1"));

        for (final String token :
                List.of(
                        "maximumEntriesScanned",
                        "maximumFilesPerRun",
                        "sourceOperationLeafName",
                        "sourceLockLeafName",
                        "archiveRoot",
                        "evaluationTimeUtcTicks",
                        "dataOwnerEvidenceFingerprint -cne",
                        "Assert-InertLifecycleIntegrity",
                        "Copy-StreamExactly",
                        "Flush($true)")) {
            assertTrue(logLifecycle.contains(token), token);
        }
        assertTrue(
                logLifecycleTest.contains(
                        "Lifecycle governado de logs validado com fixtures sintéticas."));
        assertTrue(
                logLifecycleTest.contains(
                        "Política de logs aceitou a mesma evidência para data owner e compliance."));
        assertTrue(
                logLifecycleTest.contains(
                        "Política disabled mascarou marker ACTIVE e tombstone pendente."));
        assertTrue(baseline.contains("V005__create_staging_lifecycle.sql"));
        assertTrue(logback.contains("<maxHistory>0</maxHistory>"));
    }

    @SuppressWarnings("unchecked")
    private static List<String> asList(final JsonNode node) {
        return OBJECT_MAPPER.convertValue(node, List.class);
    }

    private static String read(final Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (final IOException exception) {
            throw new IllegalStateException("Não foi possível ler o contrato local.", exception);
        }
    }

    private static String sha256(final Path path) throws IOException, NoSuchAlgorithmException {
        return HexFormat.of()
                .formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));
    }
}
