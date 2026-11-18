package br.com.esl.etl.v2.plataforma.orquestracao;

import br.com.esl.etl.v2.plataforma.controle.ImmutableFingerprint;
import br.com.esl.etl.v2.plataforma.reconciliacao.exportacao.ExportValidationSupport;
import java.io.IOException;
import java.util.List;

/**
 * Binds the explicit file/common31 profile and every budget/input pin into existing authority
 * scope.
 */
public final class IndependentExportPlanBinding {
    private IndependentExportPlanBinding() {}

    public static RuntimeExecutionPlan bind(
            final RuntimeExecutionPlan base,
            final String selectionSha,
            final String sourceConfigurationSha,
            final String profile)
            throws IOException {
        ExportValidationSupport.require(
                base.executionCount() == 1
                        && selectionSha.matches("[a-f0-9]{64}")
                        && sourceConfigurationSha.matches("[a-f0-9]{64}")
                        && List.of("FRETES_6389_EXPORT_XLSX_V1", "COLETAS_6908_NATIVE31_V1")
                                .contains(profile),
                "EXPORT_AUTHORIZATION_PLAN_BINDING");
        final RuntimeExecutionPlanItem[] selected = new RuntimeExecutionPlanItem[1];
        base.forEach(item -> selected[0] = item);
        final var old = selected[0];
        final var definition = old.definition();
        ExportValidationSupport.require(
                "DATA_EXPORT".equals(definition.sourceKind())
                        && (profile.startsWith("FRETES") ? "fretes" : "coletas")
                                .equals(definition.entity()),
                "EXPORT_AUTHORIZATION_ENTITY");
        final var material =
                ExportValidationSupport.JSON
                        .createObjectNode()
                        .put("schema", "INDEPENDENT_EXPORT_RUNTIME_BINDING_V1")
                        .put("selectionSha256", selectionSha)
                        .put("sourceConfigurationSha256", sourceConfigurationSha)
                        .put("profile", profile)
                        .put("basePlanSha256", base.fingerprint().sha256())
                        .put("baseContractSha256", definition.contract().sha256());
        final String hash =
                ExportValidationSupport.sha256(ExportValidationSupport.canonicalBytes(material));
        final var configuration = new ImmutableFingerprint("independent-export-runtime-v1", hash);
        final var contract =
                new ImmutableFingerprint(
                        "independent-export-contract-v1",
                        ExportValidationSupport.sha256(
                                ExportValidationSupport.canonicalBytes(
                                        material.deepCopy()
                                                .put(
                                                        "axis",
                                                        "EXPLICIT_SEPARATE_SOURCE_CONTRACT"))));
        final var bound =
                new RuntimeWorkloadDefinition(
                        definition.id(),
                        definition.sourceKind(),
                        definition.sourceInstance(),
                        definition.tenantScope(),
                        definition.entity(),
                        contract,
                        configuration,
                        definition.leaseDuration());
        final var item = new RuntimeExecutionPlanItem(bound, old.request(), old.partition());
        return new RuntimeExecutionPlan(
                base.cycleId(),
                base.environment(),
                new ImmutableFingerprint("independent-export-plan-v1", hash),
                base.plannedAt(),
                List.of(item));
    }
}
