package br.com.esl.etl.v2.plataforma.persistencia.observabilidade;

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

class ObservabilityDataQualitySqlContractTest {

    private static final Path DATABASE = Path.of("database");
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void keepsManifestAndFingerprintAlignedWithTheFailClosedDesign() throws Exception {
        final Path manifestPath = DATABASE.resolve("manifest/observability-data-quality.json");
        final JsonNode manifest = OBJECT_MAPPER.readTree(manifestPath.toFile());

        assertEquals(2, manifest.path("manifestVersion").asInt());
        assertEquals("V2-023", manifest.path("roadmapTask").asText());
        assertEquals("COMPLETE_LOCAL", manifest.path("localState").asText());
        assertEquals(0, manifest.path("defaultState").path("productivePoliciesSeeded").asInt());
        assertFalse(manifest.path("defaultState").path("externalAlertDeliveryEnabled").asBoolean());
        assertEquals(
                "EXTERNAL_HOLD", manifest.path("externalActivationGate").path("status").asText());
        assertEquals(
                List.of(
                        "COUNT_EQUATION",
                        "PAGE_TERMINALITY",
                        "PROMOTION_RECONCILIATION",
                        "QUARANTINE_SLA"),
                OBJECT_MAPPER.convertValue(
                        manifest.path("dataQuality").path("supportedCommonChecks"), List.class));
        assertEquals(
                "SQL_SERVER", manifest.path("dataQuality").path("massProcessingOwner").asText());
        assertEquals(
                "FIXED_O_1_SUMMARY", manifest.path("dataQuality").path("javaResultShape").asText());
        assertEquals(
                "ABSOLUTE_AND_BASIS_POINTS",
                manifest.path("dataQuality").path("thresholdRule").asText());
        assertEquals("FAIL_CLOSED", manifest.path("dataQuality").path("partialExecution").asText());
        assertEquals(
                "LATEST_EFFECTIVE_ROW_MUST_BE_RATIFIED",
                manifest.path("dataQuality")
                        .path("policyBinding")
                        .path("effectiveSelection")
                        .asText());
        assertEquals(
                "REVALIDATED_AT_EVALUATION_HEALTH_AND_PUBLICATION",
                manifest.path("dataQuality")
                        .path("policyBinding")
                        .path("temporalQuarantineSla")
                        .asText());
        assertEquals(23, manifest.path("metricShape").path("fields").size());
        assertEquals(7, manifest.path("metricShape").path("equations").size());
        assertEquals(32, manifest.path("evidence").path("maximumSanitizedSamples").asInt());
        assertFalse(manifest.path("evidence").path("businessIdentifiers").asBoolean());
        assertFalse(manifest.path("durability").path("hardDeleteByApplication").asBoolean());
        assertEquals(5, manifest.path("leastPrivilege").path("runtimeEntrypoints").asInt());
        assertEquals(
                1_000_001,
                manifest.path("limits").path("maximumTotalLogEventsPerComponentProcess").asInt());
        assertEquals(
                "024_exercise_data_quality_thresholds_rollback.sql",
                manifest.path("localValidation").path("thresholdExercise").asText());
        assertEquals(
                "025_validate_observability_data_quality_showplan.sql",
                manifest.path("localValidation").path("showplanProbe").asText());

        final String recorded =
                read(DATABASE.resolve("manifest/observability-data-quality.sha256"))
                        .strip()
                        .split("\\s+")[0];
        assertEquals(recorded, sha256(manifestPath));
    }

    @Test
    void keepsMassChecksThresholdsSamplesAndPublicationGateInsideSql() {
        final String migration =
                read(DATABASE.resolve("migrations/V006__create_observability_data_quality.sql"));
        final String validator =
                read(DATABASE.resolve("validation/021_validate_observability_data_quality.sql"));
        final String exercise =
                read(
                        DATABASE.resolve(
                                "validation/022_exercise_observability_data_quality_rollback.sql"));
        final String migratorExercise =
                read(
                        DATABASE.resolve(
                                "validation/023_exercise_observability_data_quality_migrator_rollback.sql"));
        final String thresholdExercise =
                read(
                        DATABASE.resolve(
                                "validation/024_exercise_data_quality_thresholds_rollback.sql"));
        final String baseline =
                read(DATABASE.resolve("baseline/001_schema_foundation_baseline.sql"));

        for (final String token :
                List.of(
                        "CREATE TABLE ctl.data_quality_policy",
                        "CREATE TABLE ctl.data_quality_check_policy",
                        "CREATE TABLE recon.execution_data_quality_evaluation",
                        "CREATE TABLE recon.execution_data_quality_check_result",
                        "CREATE TABLE recon.execution_metric_snapshot",
                        "CREATE TABLE recon.observability_alert",
                        "sys.sp_getapplock",
                        "V2_APPLY_",
                        "COUNT_EQUATION",
                        "PAGE_TERMINALITY",
                        "PROMOTION_RECONCILIATION",
                        "QUARANTINE_SLA",
                        "CONVERT(DECIMAL(38, 0), measurement.failed_rows) * 10000",
                        "TOP (@maximum_sanitized_samples)",
                        "@maximum_sanitized_samples NOT BETWEEN 1 AND 32",
                        "A execução parcial dos checks foi recusada",
                        "trg_data_quality_policy_immutable",
                        "trg_data_quality_check_policy_immutable",
                        "trg_execution_publication_requires_data_quality",
                        "evaluation.evaluation_state <> N'PASSED'")) {
            assertTrue(migration.contains(token), token);
        }
        assertFalse(migration.contains("sp_executesql"));
        assertFalse(migration.contains("TRUNCATE TABLE"));
        assertFalse(migration.contains("INSERT INTO ctl.data_quality_policy ("));

        assertTrue(validator.contains("Observabilidade e Data Quality V2 validadas com sucesso."));
        assertTrue(validator.contains("Grant produtivo V2-023 fora dos cinco entrypoints."));
        assertTrue(exercise.contains("evaluation_state = N'PASSED'"));
        assertTrue(exercise.contains("evaluation_state = N'FAILED'"));
        assertTrue(exercise.contains("@publication_gate_error <> 51615"));
        assertTrue(exercise.contains("ROLLBACK TRANSACTION"));
        assertTrue(migratorExercise.contains("EXECUTE AS USER"));
        assertTrue(migratorExercise.contains("V006__create_observability_data_quality.sql"));
        assertTrue(thresholdExercise.contains("failure_basis_points = 5000"));
        assertTrue(thresholdExercise.contains("CHECK_WITHIN_THRESHOLD"));
        assertTrue(thresholdExercise.contains("crossing temporal do SLA"));
        assertTrue(thresholdExercise.contains("O trigger não reavaliou"));
        assertTrue(baseline.contains("V006__create_observability_data_quality.sql"));
    }

    @Test
    void keepsJsonLogsWithoutRawIdentifiersArgumentsOrThrowables() {
        final String logback = read(Path.of("src/main/resources/logback.xml"));
        final String staticGate =
                read(Path.of("scripts/validation/Test-ObservabilityDataQualityManifest.ps1"));

        assertTrue(logback.contains("ch.qos.logback.classic.encoder.JsonEncoder"));
        assertTrue(logback.contains("<withKVPList>true</withKVPList>"));
        assertTrue(logback.contains("<withMDC>false</withMDC>"));
        assertTrue(logback.contains("<withMessage>false</withMessage>"));
        assertTrue(logback.contains("<withFormattedMessage>false</withFormattedMessage>"));
        assertTrue(logback.contains("<withArguments>false</withArguments>"));
        assertTrue(logback.contains("<withThrowable>false</withThrowable>"));
        assertTrue(logback.contains("<maxHistory>0</maxHistory>"));
        assertTrue(logback.contains("<root level=\"OFF\">"));
        assertTrue(
                logback.contains(
                        "br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportHttpExecutor"));
        assertTrue(
                logback.contains(
                        "br.com.esl.etl.v2.plataforma.fonte.dataexport.HttpDataExportGateway"));
        assertFalse(logback.contains("execution_id"));
        assertTrue(staticGate.contains("LoggerFactory"));
        assertTrue(staticGate.contains("Slf4jStructuredLogSink.java"));
    }

    private static String read(final Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (final IOException exception) {
            throw new IllegalStateException("Não foi possível ler o contrato V2-023.", exception);
        }
    }

    private static String sha256(final Path path) throws IOException, NoSuchAlgorithmException {
        return HexFormat.of()
                .formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));
    }
}
