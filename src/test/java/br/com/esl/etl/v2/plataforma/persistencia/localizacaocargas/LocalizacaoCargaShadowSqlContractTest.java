package br.com.esl.etl.v2.plataforma.persistencia.localizacaocargas;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class LocalizacaoCargaShadowSqlContractTest {
    private static final Path MIGRATION =
            Path.of("database/migrations/V014__create_localizacao_cargas_shadow_vertical.sql");
    private static final Path VALIDATOR =
            Path.of("database/validation/046_validate_localizacao_cargas_shadow_vertical.sql");
    private static final Path EXERCISE =
            Path.of(
                    "database/validation/047_exercise_localizacao_cargas_shadow_vertical_rollback.sql");

    @Test
    void migrationKeepsExactlyTwoVerticalEntrypointsAndThePluralCoreRoot() throws Exception {
        final String sql = Files.readString(MIGRATION);
        final var procedures =
                Pattern.compile(
                                "(?im)^CREATE OR ALTER PROCEDURE (?:stg|core)\\.usp_(?:stage|apply)[^\\r\\n]+$")
                        .matcher(sql)
                        .results()
                        .toList();

        assertEquals(2, procedures.size());
        assertTrue(sql.contains("CREATE TABLE core.localizacao_cargas"));
        assertTrue(sql.contains("stg.usp_stage_localizacao_carga_record"));
        assertTrue(sql.contains("core.usp_apply_reconcile_publish_localizacao_cargas"));
        assertTrue(sql.contains("BLOCKED_NO_COMPLETENESS_PROOF"));
        assertTrue(sql.contains("UNSOURCED_LEGACY"));
        assertTrue(sql.contains("FREIGHT_VOLUME_FALLBACK_DEFERRED_RELATION_AND_PUBLICATION"));
        assertFalse(
                Pattern.compile(
                                "(?i)\\bMERGE\\b|CREATE\\s+VIEW\\s+pub\\."
                                        + "|JOIN\\s+core\\.frete|REFERENCES\\s+core\\.frete"
                                        + "|DELETE\\s+FROM\\s+core\\.localizacao"
                                        + "|\\bactive\\s*=\\s*0|\\bTOP\\s*\\(?\\s*1")
                        .matcher(sql)
                        .find());
    }

    @Test
    void validatorsUseTheClosedPresenceShapeAndRollbackOnlyExercise() throws Exception {
        final String validator = Files.readString(VALIDATOR);
        final String exercise = Files.readString(EXERCISE);

        assertTrue(validator.contains("LOCALIZACAO_CARGAS_SHADOW_VERTICAL_MISSING"));
        assertTrue(validator.contains("core.localizacao_cargas"));
        assertFalse(validator.contains("LIKE N'%sequence_number%'"));
        assertTrue(exercise.contains("BEGIN TRANSACTION;"));
        assertTrue(exercise.contains("ROLLBACK TRANSACTION;"));
        assertTrue(exercise.contains("047_exercise_localizacao_cargas_shadow_vertical_rollback"));
        assertTrue(exercise.contains("$.fields."));
        assertTrue(exercise.contains("QUARANTINE_RAW_PRESERVED"));
    }

    @Test
    void manifestBindsTheExactContractIdentityMatrixAndDeferredRelation() throws Exception {
        final var manifest =
                new ObjectMapper()
                        .readTree(
                                Path.of("database/manifest/localizacao-cargas-shadow-vertical.json")
                                        .toFile());
        assertEquals("V2-028", manifest.path("roadmapTask").asText());
        assertEquals("V10", manifest.path("route").asText());
        assertEquals(47, manifest.path("block").asInt());
        assertEquals(8656, manifest.path("templateId").asInt());
        assertEquals(
                "da95fc17fc3db2fae7635c5456166d72af844b1d826e84ab6c2ba8b6f1b65c24",
                manifest.path("contract").path("releaseFingerprint").asText());
        assertEquals(
                "14af11dce5fd7696238907b72f8f6c8c77f86a4cff3045b489da5eb132c0d6f0",
                manifest.path("identity").path("fingerprint").asText());
        assertEquals(17, manifest.path("contract").path("contractedLocalCandidatePaths").size());
        assertFalse(manifest.path("volume").path("materializedRelation").asBoolean(true));
        assertEquals(
                "core.localizacao_cargas", manifest.path("persistence").path("current").asText());
    }
}
