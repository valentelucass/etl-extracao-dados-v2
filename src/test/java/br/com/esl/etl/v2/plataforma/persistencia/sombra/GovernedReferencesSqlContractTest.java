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

class GovernedReferencesSqlContractTest {

    private static final Path DATABASE_DIRECTORY = Path.of("database");
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void keepsTheOfflineFoundationTypedBoundedAndWithoutProductiveDefaults() throws Exception {
        final JsonNode manifest =
                OBJECT_MAPPER.readTree(
                        DATABASE_DIRECTORY.resolve("manifest/governed-references.json").toFile());

        assertEquals(1, manifest.path("manifestVersion").asInt());
        assertEquals("V2-035a", manifest.path("roadmapTask").asText());
        assertEquals(
                "FOUNDATION_OFFLINE_COMPLETE_EXTERNAL_BASELINE_PENDING",
                manifest.path("localState").asText());
        assertFalse(manifest.path("productiveDefaults").asBoolean());
        assertFalse(manifest.path("migrationCreatesRows").asBoolean());
        assertEquals("EXPLICIT_RELEASE_ID_ONLY", manifest.path("releaseSelection").asText());
        assertEquals(3660, manifest.path("civilDatePolicy").path("maximumRequestedDays").asInt());
        assertEquals(31, manifest.path("civilDatePolicy").path("maximumLookbackDays").asInt());
        assertTrue(
                manifest.path("civilDatePolicy")
                        .path("candidateAddsBoundedLookbackWhenValidFromIsNotBusiness")
                        .asBoolean());
        assertFalse(manifest.path("civilDatePolicy").path("fixedHorizon").asBoolean());
        assertEquals(8, manifest.path("families").size());
        assertEquals(
                List.of(
                        "CALENDAR",
                        "PICK_STATUS",
                        "BRANCH_OPERATIONS",
                        "OWNED_FLEET",
                        "BRANCH_ATTRIBUTION",
                        "CUBAGE_EXCLUSION",
                        "LOGISTICS_REGION",
                        "QUOTE_TARIFF"),
                OBJECT_MAPPER.convertValue(
                        manifest.path("families").findValues("familyCode"), List.class));
        assertEquals(
                "PROHIBITED",
                manifest.path("importContract").path("javaFullDatasetCollections").asText());
        assertEquals(
                "SET_BASED_SQL",
                manifest.path("importContract").path("relationalValidationAndPromotion").asText());
        assertEquals(1000, manifest.path("importContract").path("maximumRowsInFlight").asInt());
        assertEquals(
                1, manifest.path("importContract").path("supportedSchemaVersions").path(0).asInt());
        assertEquals(
                "GOVERNED_REFERENCES_V1",
                manifest.path("importContract").path("packageFraming").path("magicLine").asText());
        assertEquals(3, manifest.path("packageIndexGoldenFixture").path("tables").size());
        assertEquals(
                2L,
                manifest
                        .path("packageIndexGoldenFixture")
                        .path("tables")
                        .findValues("rowCount")
                        .stream()
                        .filter(node -> node.asInt() == 0)
                        .count());
        assertTrue(
                manifest.path("importContract")
                        .path("releaseRegistrationEntrypointPublishedInV008")
                        .asBoolean());
        assertFalse(
                manifest.path("importContract")
                        .path("contentImportEntrypointPublishedInV008")
                        .asBoolean());
        assertFalse(
                manifest.path("importContract")
                        .path("importEntrypointPublishedInV008")
                        .asBoolean());
        assertEquals(14, manifest.path("tableContracts").size());
        assertEquals(
                List.of(
                        "ref.calendario",
                        "ref.status_coleta",
                        "ref.filial_operacional",
                        "ref.regiao_destino_alias",
                        "ref.filial_operacional_documento",
                        "ref.frota_propria_documento",
                        "ref.classificacao_frota_alias",
                        "ref.classificacao_frota_matriz",
                        "ref.classificacao_frota_excecao_token",
                        "ref.atribuicao_filial",
                        "ref.pagador_exclusao_cubagem",
                        "ref.regiao_logistica_cep",
                        "ref.regiao_logistica_cidade_uf",
                        "ref.tarifa_rota_uf"),
                OBJECT_MAPPER.convertValue(
                        manifest.path("tableContracts").findValues("table"), List.class));
        assertEquals(
                "ONE_IMMUTABLE_RECEIPT_BEFORE_RATIFICATION",
                manifest.path("governance").path("contentSeal").asText());
        assertTrue(
                manifest.path("governance")
                        .path("ratificationRequiresDistinctImporterAndApproverRoles")
                        .asBoolean());
        assertTrue(
                manifest.path("governance")
                        .path("ratificationRequiresReceiptSealedAgainstReleaseFingerprint")
                        .asBoolean());
        assertEquals(
                "SHA256_OF_APPROVAL_EVIDENCE_NOT_CONTENT_FINGERPRINT",
                manifest.path("governance").path("approvalFingerprintMeaning").asText());
        assertFalse(
                manifest.path("governance").has("ratificationRequiresMatchingFingerprintReceipt"));
        assertTrue(
                manifest.path("documentTokenContract")
                        .path("schemeVersionIsPartOfGrainAndLookup")
                        .asBoolean());
        assertEquals(
                "EXACT_BIN2_SCHEME_VERSION_AND_TOKEN",
                manifest.path("documentTokenContract").path("comparison").asText());
        assertTrue(
                manifest.path("canonicalKeyContract")
                        .path("normalizationVersionIsPartOfGrainAndLookup")
                        .asBoolean());
        assertEquals(
                "EXACT_BIN2_NORMALIZATION_VERSION_AND_KEY",
                manifest.path("canonicalKeyContract").path("comparison").asText());
        final JsonNode fleet = manifest.path("families").path(3);
        assertEquals("OWNED_FLEET", fleet.path("familyCode").asText());
        assertEquals(
                "NULL_OR_TRIMMED_U0020_EMPTY_ONLY",
                fleet.path("missingInputContract").path("definition").asText());
        assertEquals(
                "THIRD_PARTY_AFTER_MEMBERSHIP_AND_OWNER_EXCEPTION",
                fleet.path("missingInputContract").path("DRIVER_OWNERSHIP").asText());
        assertEquals(
                "UNSPECIFIED_AFTER_VEHICLE_ALIAS_AND_OWNER_EXCEPTION_THEN_MATRIX",
                fleet.path("missingInputContract").path("VEHICLE_DRIVER_CONTRACT").asText());
        assertEquals(
                "NO_MATCH_FAIL_CLOSED",
                fleet.path("matrixCoverageContract").path("missingCell").asText());

        final String migration =
                read(DATABASE_DIRECTORY.resolve("migrations/V008__create_governed_references.sql"));
        for (final String table :
                List.of(
                        "ref.reference_release",
                        "ref.reference_import_receipt",
                        "ref.calendario",
                        "ref.status_coleta",
                        "ref.filial_operacional",
                        "ref.regiao_destino_alias",
                        "ref.filial_operacional_documento",
                        "ref.frota_propria_documento",
                        "ref.classificacao_frota_alias",
                        "ref.classificacao_frota_matriz",
                        "ref.classificacao_frota_excecao_token",
                        "ref.atribuicao_filial",
                        "ref.pagador_exclusao_cubagem",
                        "ref.regiao_logistica_cep",
                        "ref.regiao_logistica_cidade_uf",
                        "ref.tarifa_rota_uf")) {
            assertTrue(migration.contains("CREATE TABLE " + table + " ("), table);
        }
        assertTrue(migration.contains("CREATE VIEW ref.v_status_coleta_seed_candidate_v1"));
        assertTrue(migration.contains("CREATE PROCEDURE ref.usp_register_reference_release"));
        assertTrue(migration.contains("CREATE FUNCTION ref.ufn_normalize_pick_status_v1"));
        assertTrue(migration.contains("LTRIM(RTRIM(@raw)) COLLATE Latin1_General_100_BIN2"));
        assertTrue(migration.contains("CREATE FUNCTION ref.ufn_calendar_seed_candidate_v1"));
        assertTrue(
                migration.contains(
                        "DECLARE @active_span_days INT = DATEDIFF(DAY, @window_start,"
                                + " @window_end_exclusive)"));
        assertTrue(migration.contains("OR @active_span_days > 3660"));
        assertTrue(migration.contains("DATEADD(DAY, -31, @window_start)"));
        assertTrue(migration.contains("schema_version = 1"));
        assertTrue(migration.contains("@locked_release_count"));
        assertTrue(migration.contains("IX_ref_atribuicao_filial_branch_dependency"));
        assertTrue(migration.contains("classification_scope = N'DRIVER_OWNERSHIP'"));
        assertTrue(migration.contains("classification_scope = N'VEHICLE_DRIVER_CONTRACT'"));
        assertTrue(migration.contains("required_vehicle_contract_class = N'AGGREGATE'"));
        assertTrue(migration.contains("Aprovador deve ser distinto do autor e do importador."));
        assertTrue(migration.contains("sys.sp_getapplock"));
        assertTrue(migration.contains("UPDLOCK, HOLDLOCK"));
        assertTrue(
                migration.contains("coverage_state = N'UNAVAILABLE' AND minimum_amount IS NULL"));
        assertFalse(migration.contains("CREATE TABLE pub."));
        assertFalse(migration.contains("GRANT SELECT ON SCHEMA::ref"));
        assertFalse(migration.contains("MERGE "));
    }

    @Test
    void keepsTheManifestFingerprintAndRollbackEvidenceCurrent() throws Exception {
        final Path manifestPath = DATABASE_DIRECTORY.resolve("manifest/governed-references.json");
        final String expectedFingerprint =
                read(DATABASE_DIRECTORY.resolve("manifest/governed-references.sha256"))
                        .strip()
                        .split("\\s+")[0];
        assertEquals(expectedFingerprint, sha256(manifestPath));

        final String validator =
                read(DATABASE_DIRECTORY.resolve("validation/030_validate_governed_references.sql"));
        final String exercise =
                read(
                        DATABASE_DIRECTORY.resolve(
                                "validation/031_exercise_governed_references_rollback.sql"));
        final String migratorExercise =
                read(
                        DATABASE_DIRECTORY.resolve(
                                "validation/032_exercise_governed_references_migrator_rollback.sql"));
        final String negativeExercise =
                read(
                        DATABASE_DIRECTORY.resolve(
                                "validation/033_exercise_governed_references_negative_rollback.sql"));
        final String showplanExercise =
                read(
                        DATABASE_DIRECTORY.resolve(
                                "validation/034_validate_governed_references_showplan.sql"));
        final String concurrencyGate =
                read(Path.of("scripts/validation/Test-GovernedReferencesConcurrency.ps1"));
        final String showplanGate =
                read(Path.of("scripts/validation/Test-GovernedReferencesShowplan.ps1"));

        assertTrue(validator.contains("Referências governadas V2 validadas com sucesso."));
        assertTrue(validator.contains("IX_ref_regiao_logistica_cep_lookup"));
        assertTrue(validator.contains("IX_ref_atribuicao_filial_branch_dependency"));
        assertTrue(
                validator.contains("Recibo diverge do envelope, conteúdo físico ou cronologia."));
        assertTrue(validator.contains("physical_content.actual_rows"));
        assertTrue(exercise.contains("V008 criou conteúdo ou ativação por default."));
        assertTrue(exercise.contains("20360229"));
        assertTrue(exercise.contains("Runtime obteve SELECT direto em ref."));
        assertTrue(exercise.contains("Políticas independentes e matriz sintética 2x4"));
        assertTrue(exercise.contains("Versões distintas de chave canônica ou token"));
        assertTrue(exercise.contains("Revogação ordenada atribuição→filial"));
        assertTrue(exercise.contains("ROLLBACK TRANSACTION"));
        assertTrue(migratorExercise.contains("EXECUTE AS USER = N'v2_migrator_references_probe'"));
        assertTrue(migratorExercise.contains("HAS_PERMS_BY_NAME"));
        assertTrue(migratorExercise.contains("ROLLBACK TRANSACTION"));
        assertTrue(negativeExercise.contains("Sobreposição tarifária não foi rejeitada."));
        assertTrue(negativeExercise.contains("Faixa CEP inválida não foi rejeitada."));
        assertTrue(negativeExercise.contains("Autor conseguiu aprovar a própria release."));
        assertTrue(negativeExercise.contains("Importador conseguiu aprovar o próprio recibo."));
        assertTrue(negativeExercise.contains("Cardinalidade física divergente não foi rejeitada."));
        assertTrue(negativeExercise.contains("Release ratificada aceitou conteúdo tardio."));
        assertTrue(negativeExercise.contains("Release governada aceitou UPDATE."));
        assertTrue(
                negativeExercise.contains(
                        "Vigências ratificadas sobrepostas não foram rejeitadas."));
        assertTrue(negativeExercise.contains("Replay divergente não foi rejeitado."));
        assertTrue(negativeExercise.contains("Calendário esparso foi ratificado."));
        assertTrue(negativeExercise.contains("Referência útil não exata foi ratificada."));
        assertTrue(
                negativeExercise.contains(
                        "Atribuição ativou sem release de filiais no mesmo escopo."));
        assertTrue(negativeExercise.contains("Schema de referência desconhecido foi aceito."));
        assertTrue(
                negativeExercise.contains("Alias na mesma normalization_version aceitou overlap."));
        assertTrue(
                negativeExercise.contains("Token no mesmo token_scheme_version aceitou overlap."));
        assertTrue(negativeExercise.contains("Lookback esparso ou anterior ao último dia útil"));
        assertTrue(
                negativeExercise.contains("Dia útil sem feriado foi classificado como não útil."));
        assertTrue(negativeExercise.contains("Matriz de frota aceitou classe do eixo errado."));
        assertTrue(
                negativeExercise.contains(
                        "Filial ativa foi revogada antes de sua atribuição dependente."));
        assertTrue(negativeExercise.contains("ROLLBACK TRANSACTION"));
        assertTrue(concurrencyGate.contains("trg_reference_ratification_guard"));
        assertTrue(concurrencyGate.contains("REFERENCES_CONTENTION_CONFIRMED"));
        assertTrue(concurrencyGate.contains("REFERENCES_OTHER_SCOPE_ISOLATED"));
        assertTrue(concurrencyGate.contains("REFERENCES_LOCK_REACQUIRED_AFTER_ROLLBACK"));
        assertTrue(concurrencyGate.contains("CONTENT_SEAL_CONTENTION_CONFIRMED"));
        assertTrue(concurrencyGate.contains("DEPENDENCY_REVOKE_BLOCKED_BY_RATIFICATION"));
        assertTrue(concurrencyGate.contains("DEPENDENCY_RATIFY_BLOCKED_BY_REVOCATION"));
        assertTrue(concurrencyGate.contains("REFERENCES_EPHEMERAL_DATABASE_DROPPED"));
        assertTrue(showplanExercise.contains("SET SHOWPLAN_XML ON"));
        assertTrue(showplanExercise.contains("GOVERNED_REFERENCES_SHOWPLAN_ROLLED_BACK"));
        assertTrue(showplanGate.contains("IX_ref_reference_release_scope_validity"));
        assertTrue(showplanGate.contains("IX_ref_tarifa_rota_uf_lookup"));
        assertTrue(showplanGate.contains("IX_ref_classificacao_frota_matriz_lookup"));
        assertTrue(showplanGate.contains("IX_ref_atribuicao_filial_branch_dependency"));
        assertTrue(showplanGate.contains("$conversionWarnings -ne 0"));
        assertTrue(showplanGate.contains("operationalWarnings=0"));
    }

    private static String read(final Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (final IOException exception) {
            throw new IllegalStateException(
                    "Não foi possível ler o contrato de referências.", exception);
        }
    }

    private static String sha256(final Path path) throws IOException, NoSuchAlgorithmException {
        final byte[] bytes = Files.readAllBytes(path);
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}
