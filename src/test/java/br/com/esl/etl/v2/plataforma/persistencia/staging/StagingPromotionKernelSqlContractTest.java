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

class StagingPromotionKernelSqlContractTest {

    private static final Path DATABASE_DIRECTORY = Path.of("database");
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void keepsTheKernelManifestAndFingerprintAligned() throws Exception {
        final Path manifestPath =
                DATABASE_DIRECTORY.resolve("manifest/staging-promotion-kernel.json");
        final JsonNode manifest = OBJECT_MAPPER.readTree(manifestPath.toFile());

        assertEquals(5, manifest.path("manifestVersion").asInt());
        assertEquals("V2-044", manifest.path("roadmapTask").asText());
        assertEquals("V2-045a", manifest.path("lifecycleEvolutionTask").asText());
        assertEquals(
                List.of(
                        "stg.usp_stage_record",
                        "core.usp_prepare_staged_execution",
                        "core.usp_apply_reconcile_publish_execution"),
                OBJECT_MAPPER.convertValue(manifest.path("procedures"), List.class));
        assertEquals(
                List.of(
                        "stg.usp_stage_record",
                        "core.usp_prepare_staged_execution",
                        "core.usp_apply_reconcile_publish_execution"),
                OBJECT_MAPPER.convertValue(
                        manifest.path("runtime").path("grantedProcedures"), List.class));
        assertTrue(manifest.path("runtime").path("proceduresOnly").asBoolean());
        assertEquals("PROHIBITED", manifest.path("storage").path("rawPayload").asText());
        assertEquals("ROW_NUMBER", manifest.path("deduplication").path("strategy").asText());
        assertEquals(
                "SAME_TRANSACTION_ONLY",
                manifest.path("transactionProtocol")
                        .path("coreMutationBeforePublication")
                        .asText());
        assertEquals(
                "database_clock_captured_after_lease_locks",
                manifest.path("clockAuthority").path("fencing").asText());
        assertEquals(
                "all_persisted_nvarchar_columns_and_promotion_key_table_variables",
                manifest.path("semanticTextCollation").path("scope").asText());
        assertEquals(
                "trim_only_ASCII_U+0020_after_length_validation_and_reject_noncanonical_storage",
                manifest.path("semanticTextCollation").path("outerSpacePolicy").asText());
        assertEquals(
                "PROHIBITED",
                manifest.path("textInputProtocol").path("silentPreBodyTruncation").asText());
        assertEquals(
                "trim_ASCII_U+0020_then_require_first_[A-Z]_and_remaining_[A-Z0-9_]_with_length_2_to_64; no_case_fold",
                manifest.path("textInputProtocol").path("reasonCodes").asText());
        assertTrue(manifest.path("quarantineCounting").has("unidentified_quarantine_rows"));
        assertTrue(
                manifest.path("transactionProtocol")
                        .path("promotionRetry")
                        .asText()
                        .startsWith("PROMOTED exact retry first requires"));
        assertTrue(
                manifest.path("transactionProtocol")
                        .path("publicationRetry")
                        .asText()
                        .startsWith("PUBLISHED exact retry first requires"));
        assertTrue(
                manifest.path("transactionProtocol")
                        .path("internalCountPersistence")
                        .asText()
                        .startsWith("direct INSERT"));
        assertEquals(
                "core.usp_apply_reconcile_publish_execution",
                manifest.path("transactionProtocol").path("atomicEntrypoint").asText());
        assertTrue(manifest.path("transactionProtocol").path("application").isArray());
        assertEquals(6, manifest.path("transactionProtocol").path("application").size());
        assertTrue(manifest.path("transactionProtocol").path("publication").isArray());
        assertTrue(
                manifest.path("transactionProtocol")
                        .path("newMutationFencing")
                        .asText()
                        .contains("PROMOTED apply/publication require both"));
        assertTrue(
                manifest.path("transactionProtocol")
                        .path("publication")
                        .toString()
                        .contains("lease_release_revalidates_active_database_clock_lease"));
        assertEquals(5, manifest.path("contractPermit").path("parameterOrder").size());
        assertEquals(
                "execution_id",
                manifest.path("contractPermit").path("parameterOrder").path(0).asText());
        assertEquals(
                "configuration_fingerprint",
                manifest.path("contractPermit").path("parameterOrder").path(4).asText());
        assertEquals(51418, manifest.path("contractPermit").path("mismatchError").asInt());
        assertEquals(
                "database/validation/011_exercise_contract_permit_rollback.sql",
                manifest.path("contractPermit").path("rollbackExercise").asText());
        assertFalse(manifest.path("contractPermit").path("cryptographicAttestation").asBoolean());
        assertEquals(
                "REMOVED_BY_V005_AFTER_TYPED_ARCHIVE_CONTRACT",
                manifest.path("durableLineage").path("legacyBlockingForeignKeys").asText());

        final String expectedFingerprint =
                read(DATABASE_DIRECTORY.resolve("manifest/staging-promotion-kernel.sha256"))
                        .strip()
                        .split("\\s+")[0];
        assertEquals(expectedFingerprint, sha256(manifestPath));
    }

    @Test
    void keepsTheMigrationBaselineValidatorAndExerciseAsOneKernelContract() {
        final String migration =
                read(
                        DATABASE_DIRECTORY.resolve(
                                "migrations/V004__create_staging_promotion_kernel.sql"));
        final String baseline =
                read(DATABASE_DIRECTORY.resolve("baseline/001_schema_foundation_baseline.sql"));
        final String validator =
                read(
                        DATABASE_DIRECTORY.resolve(
                                "validation/007_validate_staging_promotion_kernel.sql"));
        final String exercise =
                read(
                        DATABASE_DIRECTORY.resolve(
                                "validation/008_exercise_staging_promotion_kernel_rollback.sql"));
        final String candidatePreparation =
                procedureDefinition(migration, "core.usp_prepare_staged_execution");
        final String atomicPublication =
                procedureDefinition(migration, "core.usp_apply_reconcile_publish_execution");

        assertTrue(migration.contains("ROW_NUMBER() OVER"));
        assertTrue(migration.contains("UQ_stg_execution_record_input"));
        assertTrue(migration.contains("PK_stg_execution_candidate"));
        assertTrue(migration.contains("UQ_recon_quarantine_record_stage"));
        assertTrue(migration.contains("CK_ctl_execution_promotion_result_counts"));
        assertTrue(migration.contains("CREATE TABLE recon.execution_candidate_application"));
        assertTrue(migration.contains("CREATE TABLE recon.execution_reconciliation_result"));
        assertTrue(migration.contains("CK_recon_execution_reconciliation_result_counts"));
        assertTrue(migration.contains("CANDIDATE_SET_PREPARED"));
        assertTrue(migration.contains("EQUAL_FRESHNESS_CONFLICT"));
        assertTrue(migration.contains("UNKNOWN_FRESHNESS_CONFLICT"));
        assertTrue(migration.contains("unidentified_quarantine_rows BIGINT NOT NULL"));
        assertTrue(
                migration.contains(
                        "physical_rows = distinct_root_keys + duplicate_rows + unidentified_quarantine_rows"));
        assertTrue(
                migration.contains("distinct_root_keys = candidate_rows + quarantined_root_keys"));
        assertTrue(
                migration.contains(
                        "@duplicate_rows = @physical_rows - @distinct_rows - @unidentified_quarantine_rows"));
        assertTrue(migration.contains("@quarantine_reason_code, @database_now_utc"));
        assertTrue(migration.contains("@source_key NVARCHAR(MAX)"));
        assertTrue(migration.contains("@row_fingerprint_version NVARCHAR(MAX)"));
        assertTrue(migration.contains("@source_row_hash NVARCHAR(MAX)"));
        assertTrue(migration.contains("DATALENGTH(@source_row_hash) > 128"));
        assertTrue(migration.contains("DATALENGTH(@source_key) > 512"));
        assertTrue(
                migration.indexOf("IF DATALENGTH(@source_key)")
                        < migration.indexOf("SET @source_key = NULLIF(LTRIM"));
        assertFalse(migration.contains("persisted.staged_at_utc <> @staged_at_utc"));
        assertTrue(migration.contains("OUTPUT deleted.next_transition_sequence"));
        assertFalse(migration.contains("MAX(transition_sequence)"));
        assertContractPermitParameters(candidatePreparation, "core.usp_prepare_staged_execution");
        assertContractPermitParameters(
                atomicPublication, "core.usp_apply_reconcile_publish_execution");
        assertTrue(candidatePreparation.contains("THROW 51418"));
        assertTrue(atomicPublication.contains("THROW 51418"));
        assertTrue(
                candidatePreparation.indexOf("THROW 51418")
                        < candidatePreparation.indexOf(
                                "@execution_state COLLATE Latin1_General_100_BIN2 = N'PROMOTED'"));
        assertTrue(
                atomicPublication.indexOf("THROW 51418")
                        < atomicPublication.indexOf(
                                "@execution_state COLLATE Latin1_General_100_BIN2 = N'PUBLISHED'"));
        assertFalse(candidatePreparation.contains("UPDATE core.entity_record_state"));
        assertFalse(candidatePreparation.contains("INSERT INTO core.entity_record_state"));
        assertFalse(candidatePreparation.contains("partition_publication_pointer"));
        assertFalse(candidatePreparation.contains("incremental_publication_watermark"));
        assertTrue(atomicPublication.contains("INSERT INTO core.entity_record_state"));
        assertTrue(atomicPublication.contains("UPDATE current_record"));
        assertTrue(atomicPublication.contains("INSERT INTO recon.execution_candidate_application"));
        assertTrue(atomicPublication.contains("INSERT INTO recon.execution_reconciliation_result"));
        assertTrue(atomicPublication.contains("INSERT INTO ctl.partition_publication_pointer"));
        assertTrue(atomicPublication.contains("UPDATE ctl.incremental_publication_watermark"));
        assertTrue(atomicPublication.contains("N'PROMOTED', N'RECONCILED'"));
        assertTrue(atomicPublication.contains("N'RECONCILED', N'PUBLISHED'"));
        assertTrue(atomicPublication.contains("N'CANDIDATE_SET_RECONCILED'"));
        assertTrue(atomicPublication.contains("N'RECONCILIATION_PUBLISHED'"));
        assertTrue(atomicPublication.contains("result.quarantined_stage_rows = 0"));
        assertTrue(
                atomicPublication.contains(
                        "Frescor igual ou desconhecido possui conteúdo divergente"));
        assertTrue(
                atomicPublication.contains(
                        "application_disposition IN (N'NO_OP', N'STALE_NO_OP')"));
        assertTrue(atomicPublication.contains("SET released_at_utc = @published_at_utc"));
        assertTrue(atomicPublication.contains("expires_at_utc > SYSUTCDATETIME()"));
        assertTrue(atomicPublication.contains("@execution_id AS execution_id"));
        assertTrue(atomicPublication.contains("@candidate_rows AS candidate_rows"));
        assertTrue(atomicPublication.contains("@inserted_rows AS inserted_rows"));
        assertTrue(atomicPublication.contains("@updated_rows AS updated_rows"));
        assertTrue(atomicPublication.contains("@reactivated_rows AS reactivated_rows"));
        assertTrue(atomicPublication.contains("@noop_rows AS noop_rows"));
        assertTrue(atomicPublication.contains("@stale_noop_rows AS stale_noop_rows"));
        assertTrue(atomicPublication.contains("@reconciled_at_utc AS reconciled_at_utc"));
        assertTrue(atomicPublication.contains("@published_at_utc AS published_at_utc"));
        assertTrue(
                atomicPublication.contains(
                        "@incremental_frontier_before_utc AS incremental_frontier_before_utc"));
        assertTrue(
                atomicPublication.contains(
                        "@incremental_frontier_after_utc AS incremental_frontier_after_utc"));
        assertTrue(
                atomicPublication.indexOf(
                                "@execution_state COLLATE Latin1_General_100_BIN2 = N'PUBLISHED'")
                        < atomicPublication.indexOf(
                                "FROM ctl.execution_lease AS lease WITH (UPDLOCK, HOLDLOCK)"));
        assertTrue(migration.contains("N'core', N'usp_prepare_staged_execution', N'v2_runtime'"));
        assertTrue(
                migration.contains(
                        "N'core', N'usp_apply_reconcile_publish_execution', N'v2_runtime'"));
        assertFalse(migration.contains("MERGE "));
        assertFalse(migration.contains("payload_json"));
        assertTrue(migration.contains("INSERT INTO ctl.execution_count"));
        assertFalse(migration.contains("EXEC ctl.usp_control_plane_record_counts"));
        assertTrue(migration.contains("THROW 51403"));
        assertTrue(migration.contains("THROW 51412"));
        assertTrue(migration.contains("@validation_disposition COLLATE Latin1_General_100_BIN2"));
        assertTrue(
                migration.contains(
                        "LEFT(reason_code, 1) COLLATE Latin1_General_100_BIN2 LIKE '[A-Z]'"));
        assertTrue(
                migration.contains(
                        "LEFT(@quarantine_reason_code, 1) COLLATE Latin1_General_100_BIN2"));
        assertTrue(baseline.contains("V004__create_staging_promotion_kernel.sql"));
        assertTrue(validator.contains("Kernel de staging e promoção V2 validado com sucesso."));
        assertTrue(validator.contains("core.usp_apply_reconcile_publish_execution"));
        assertTrue(validator.contains("N'execution_reconciliation_result'"));
        assertTrue(exercise.contains("A promoção alterou core antes da publicação reconciliada"));
        assertTrue(exercise.contains("A promoção não pode avançar watermark incremental."));
        assertTrue(exercise.contains("atalho manual PROMOTED para RECONCILED deveria falhar"));
        assertTrue(exercise.contains("unidentified_quarantine_rows = 1"));
        assertTrue(exercise.contains("SOURCE_KEY_MISSING"));
        assertTrue(exercise.contains("N'synthetic-KeyA'"));
        assertTrue(exercise.contains("N'synthetic-ação'"));
        assertTrue(
                exercise.contains(
                        "Chave oversized com o mesmo prefixo foi truncada silenciosamente"));
        assertTrue(exercise.contains("physical_rows = 14"));
        assertTrue(exercise.contains("NCHAR(9) + N'synthetic-tab' + NCHAR(9)"));
        assertTrue(
                exercise.contains(
                        "Retry com lease expirada duplicou evidência de staging ou promoção"));
        assertTrue(exercise.contains("(N'_X')"));
        assertTrue(exercise.contains("(N'1X')"));
        assertTrue(exercise.contains("ROLLBACK TRANSACTION"));
    }

    private static String procedureDefinition(
            final String migration, final String qualifiedProcedureName) {
        final String marker = "CREATE OR ALTER PROCEDURE " + qualifiedProcedureName;
        final int start = migration.indexOf(marker);
        if (start < 0) {
            throw new IllegalArgumentException("Procedure ausente na migration: " + marker);
        }
        final int end = migration.indexOf("\nGO", start);
        if (end < 0) {
            throw new IllegalArgumentException("Terminator GO ausente para a procedure: " + marker);
        }
        return migration.substring(start, end).replace("\r\n", "\n");
    }

    private static void assertContractPermitParameters(
            final String procedure, final String qualifiedProcedureName) {
        final int bodyStart = procedure.indexOf("\nAS\nBEGIN");
        assertTrue(bodyStart > 0);
        assertEquals(
                "CREATE OR ALTER PROCEDURE "
                        + qualifiedProcedureName
                        + "\n    @execution_id UNIQUEIDENTIFIER,"
                        + "\n    @contract_version NVARCHAR(MAX),"
                        + "\n    @contract_fingerprint NVARCHAR(MAX),"
                        + "\n    @configuration_version NVARCHAR(MAX),"
                        + "\n    @configuration_fingerprint NVARCHAR(MAX)",
                procedure.substring(0, bodyStart));
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
