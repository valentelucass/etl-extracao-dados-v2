package br.com.esl.etl.v2.plataforma.controle;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProgressiveDataGateSqlContractTest {

    private static final Path DATABASE_DIRECTORY = Path.of("database");

    @Test
    void keepsTheProgressiveGateBoundToBothManifestsAndAllActiveMigrations() {
        final String gate =
                read(
                        DATABASE_DIRECTORY.resolve(
                                "validation/005_validate_progressive_data_gate.sql"));
        final String exercise =
                read(
                        DATABASE_DIRECTORY.resolve(
                                "validation/006_exercise_progressive_data_gate_rollback.sql"));

        assertTrue(gate.contains("UQ_ctl_execution_partition_semantic_key"));
        assertTrue(gate.contains("CK_ctl_execution_count_equation"));
        assertTrue(gate.contains("UX_ctl_execution_lease_active_partition"));
        assertTrue(gate.contains("CK_ctl_execution_attempt_transition_sequence_lifecycle_bound"));
        assertTrue(gate.contains("CK_ctl_execution_state_event_lifecycle_bound"));
        assertTrue(gate.contains("ufn_invalid_execution_state_ledger"));
        assertTrue(gate.contains("Constraint fora do contrato V001-V017."));
        assertTrue(gate.contains("Permissão direta fora do contrato mínimo V001-V017."));
        assertTrue(gate.contains("ROLE_MEMBERSHIP"));
        assertTrue(gate.contains("Gate progressivo de dados V2 validado com sucesso."));

        assertTrue(exercise.contains("004_exercise_control_plane_baseline_rollback.sql"));
        assertTrue(exercise.contains("V001__create_v2_schema_foundation.sql"));
        assertTrue(exercise.contains("V002__create_v2_database_roles.sql"));
        assertTrue(exercise.contains("V003__create_control_plane.sql"));
        assertTrue(exercise.contains("V004__create_staging_promotion_kernel.sql"));
        assertTrue(exercise.contains("V005__create_staging_lifecycle.sql"));
        assertTrue(exercise.contains("V006__create_observability_data_quality.sql"));
        assertTrue(exercise.contains("V007__create_usuarios_current_history.sql"));
        assertTrue(exercise.contains("V008__create_governed_references.sql"));
        assertTrue(exercise.contains("V009__create_usuario_dimension_current_view.sql"));
        assertTrue(exercise.contains("V010__create_coletas_shadow_vertical.sql"));
        assertTrue(exercise.contains("V011__create_cotacoes_shadow_vertical.sql"));
        assertTrue(exercise.contains("V012__create_manifestos_shadow_vertical.sql"));
        assertTrue(exercise.contains("V013__create_fretes_shadow_vertical.sql"));
        assertTrue(exercise.contains("V014__create_localizacao_cargas_shadow_vertical.sql"));
        assertTrue(exercise.contains("V015__create_runtime_durable_recovery.sql"));
        assertTrue(exercise.contains("005_validate_progressive_data_gate.sql"));
        assertTrue(exercise.contains("007_validate_staging_promotion_kernel.sql"));
        assertTrue(exercise.contains("009_validate_atomic_publication_protocol.sql"));
        assertTrue(exercise.contains("012_validate_staging_lifecycle.sql"));
        assertTrue(exercise.contains("021_validate_observability_data_quality.sql"));
        assertTrue(exercise.contains("026_validate_usuarios_current_history.sql"));
        assertTrue(exercise.contains("030_validate_governed_references.sql"));
        assertTrue(exercise.contains("035_validate_usuarios_dimension_current.sql"));
        assertTrue(exercise.contains("042_validate_manifestos_shadow_vertical.sql"));
        assertTrue(exercise.contains("044_validate_fretes_shadow_vertical.sql"));
        assertTrue(exercise.contains("046_validate_localizacao_cargas_shadow_vertical.sql"));
        assertTrue(exercise.contains("ROLLBACK TRANSACTION"));
    }

    @Test
    void keepsTheLocalRunnerFailClosedOnTheAuthorizedShadowTarget() {
        final String runner = read(Path.of("scripts/validation/Invoke-ProgressiveDataGate.ps1"));
        final String staticCheck = read(Path.of("scripts/validation/Test-ProgressiveDataGate.ps1"));
        final List<String> sqlcmdInputInvocations =
                runner.lines().filter(line -> line.contains(" -i ")).toList();

        assertTrue(runner.contains("$targetDatabase = 'ETL_SISTEMA_V2_SHADOW'"));
        assertTrue(runner.contains("-S localhost"));
        assertTrue(runner.contains("-d master"));
        assertFalse(sqlcmdInputInvocations.isEmpty());
        assertTrue(
                sqlcmdInputInvocations.stream()
                        .allMatch(
                                invocation ->
                                        invocation.contains("-S localhost")
                                                && invocation.contains("-E")
                                                && invocation.contains("-f 65001")
                                                && invocation.contains("-d $targetDatabase")
                                                && invocation.contains("_rollback.sql")
                                                && invocation.contains("-b")));
        assertTrue(runner.contains("006_exercise_progressive_data_gate_rollback.sql"));
        assertTrue(runner.contains("008_exercise_staging_promotion_kernel_rollback.sql"));
        assertTrue(runner.contains("010_exercise_atomic_publication_rollback.sql"));
        assertTrue(runner.contains("011_exercise_contract_permit_rollback.sql"));
        assertTrue(runner.contains("013_exercise_staging_lifecycle_rollback.sql"));
        assertTrue(runner.contains("022_exercise_observability_data_quality_rollback.sql"));
        assertTrue(
                runner.contains("023_exercise_observability_data_quality_migrator_rollback.sql"));
        assertTrue(runner.contains("024_exercise_data_quality_thresholds_rollback.sql"));
        assertTrue(runner.contains("027_exercise_usuarios_current_history_rollback.sql"));
        assertTrue(runner.contains("029_exercise_usuarios_late_sidecar_rollback.sql"));
        assertTrue(runner.contains("031_exercise_governed_references_rollback.sql"));
        assertTrue(runner.contains("032_exercise_governed_references_migrator_rollback.sql"));
        assertTrue(runner.contains("033_exercise_governed_references_negative_rollback.sql"));
        assertTrue(runner.contains("036_exercise_usuarios_dimension_current_rollback.sql"));
        assertTrue(runner.contains("043_exercise_manifestos_shadow_vertical_rollback.sql"));
        assertTrue(runner.contains("045_exercise_fretes_shadow_vertical_rollback.sql"));
        assertTrue(runner.contains("047_exercise_localizacao_cargas_shadow_vertical_rollback.sql"));
        assertTrue(runner.contains("Test-ProgressiveDataGate.ps1"));
        assertTrue(runner.contains("Test-AtomicPublicationConcurrency.ps1"));
        assertTrue(runner.contains("Test-StagingLifecycleConcurrency.ps1"));
        assertTrue(runner.contains("Test-ObservabilityDataQualityShowplan.ps1"));
        assertTrue(runner.contains("Test-GovernedReferencesConcurrency.ps1"));
        assertTrue(runner.contains("Test-GovernedReferencesShowplan.ps1"));
        assertTrue(runner.contains("Test-UsuariosDimensionCurrentShowplan.ps1"));
        assertTrue(runner.contains("Test-ManifestosShadowConcurrency.ps1"));
        assertTrue(runner.contains("Test-FretesShadowConcurrency.ps1"));
        assertTrue(runner.contains("Test-LocalizacaoCargasShadowConcurrency.ps1"));
        assertTrue(staticCheck.contains("Test-SchemaFoundationManifest.ps1"));
        assertTrue(staticCheck.contains("Test-ControlPlaneManifest.ps1"));
        assertTrue(staticCheck.contains("V003__create_control_plane.sql"));
        assertTrue(staticCheck.contains("V004__create_staging_promotion_kernel.sql"));
        assertTrue(staticCheck.contains("V005__create_staging_lifecycle.sql"));
        assertTrue(staticCheck.contains("V006__create_observability_data_quality.sql"));
        assertTrue(staticCheck.contains("V007__create_usuarios_current_history.sql"));
        assertTrue(staticCheck.contains("V008__create_governed_references.sql"));
        assertTrue(staticCheck.contains("V009__create_usuario_dimension_current_view.sql"));
        assertTrue(staticCheck.contains("Test-ObservabilityDataQualityManifest.ps1"));
        assertTrue(staticCheck.contains("Test-GovernedReferencesManifest.ps1"));
        assertTrue(staticCheck.contains("Test-UsuariosDimensionCurrentManifest.ps1"));
        assertTrue(staticCheck.contains("Test-ManifestosV2026ShadowVertical.ps1"));
        assertTrue(staticCheck.contains("Test-FretesV2011ShadowVertical.ps1"));
        assertTrue(staticCheck.contains("Test-LocalizacaoCargasV2028ShadowVertical.ps1"));
        final String concurrencyProbe =
                read(Path.of("scripts/validation/Test-AtomicPublicationConcurrency.ps1"));
        assertTrue(concurrencyProbe.contains("sys.sp_getapplock"));
        assertTrue(concurrencyProbe.contains("CONTENTION_CONFIRMED"));
        assertTrue(concurrencyProbe.contains("LOCK_REACQUIRED_AFTER_ROLLBACK"));
        final String lifecycleConcurrencyProbe =
                read(Path.of("scripts/validation/Test-StagingLifecycleConcurrency.ps1"));
        assertTrue(lifecycleConcurrencyProbe.contains("V2_STAGING_LIFECYCLE"));
        assertTrue(lifecycleConcurrencyProbe.contains("LIFECYCLE_CONTENTION_CONFIRMED"));
    }

    private static String read(final Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (final IOException exception) {
            throw new IllegalStateException("Não foi possível ler o gate SQL local.", exception);
        }
    }
}
