package br.com.esl.etl.v2.bootstrap;

import static br.com.esl.etl.v2.plataforma.reconciliacao.exportacao.ExportValidationSupport.require;
import static br.com.esl.etl.v2.plataforma.reconciliacao.exportacao.ExportValidationSupport.text;

import br.com.esl.etl.v2.plataforma.configuracao.DataExportRuntimeConfigurationFingerprint;
import br.com.esl.etl.v2.plataforma.configuracao.RuntimeConfiguration;
import br.com.esl.etl.v2.plataforma.reconciliacao.exportacao.ColetasIndependentRequest;
import br.com.esl.etl.v2.plataforma.reconciliacao.exportacao.ExportValidationSupport;
import br.com.esl.etl.v2.plataforma.reconciliacao.exportacao.IndependentExportRequest;
import br.com.esl.etl.v2.plataforma.reconciliacao.exportacao.IndependentExportRuntime;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Duration;
import java.time.LocalDate;
import java.util.UUID;

/** Pre-authorization checks contain no Source, credential, process or SQL operation. */
final class RuntimeIndependentExportExecution {
    private RuntimeIndependentExportExecution() {}

    static void validate(
            final RuntimeConfiguration configuration,
            final RuntimeOperationalRequest operational,
            final ExportValidationSupport.Pin selectionPin,
            final JsonNode selection)
            throws Exception {
        final var budget = new ExportValidationSupport.Budget(Duration.ofSeconds(10));
        selectionPin.read(262144, budget);
        require(
                "INDEPENDENT_EXPORT_OPERATIONAL_SELECTION_V1".equals(text(selection, "schema"))
                        && operational.users == null
                        && operational.dependency == null
                        && operational.updatedAt.isEmpty()
                        && operational.plan.executionCount() == 1,
                "EXPORT_OPERATIONAL_SINGLE_ENTITY_SCOPE");
        final var source = configuration.dataExport().orElseThrow();
        final var duration = Duration.ofSeconds(selection.path("maximumDurationSeconds").asInt());
        require(
                duration.compareTo(source.resiliencePolicy().cycleTimeout()) <= 0
                        && duration.compareTo(source.resiliencePolicy().stepTimeout()) <= 0,
                "EXPORT_DECLARED_SOURCE_CYCLE_BUDGET");
        require(
                DataExportRuntimeConfigurationFingerprint.from(source)
                        .sha256()
                        .equals(text(selection, "sourceConfigurationSha256")),
                "EXPORT_SOURCE_CONFIGURATION_BINDING");
        final var request =
                ExportValidationSupport.Pin.from(selection.required("requestPin"))
                        .json(262144, budget);
        final String profile = text(selection, "profile");
        if (IndependentExportRequest.PROFILE.equals(profile)) {
            IndependentExportRequest.validate(request);
            require("FRETES".equals(operational.template.name()), "EXPORT_OPERATIONAL_TEMPLATE");
        } else {
            require(
                    ColetasIndependentRequest.PROFILE.equals(profile),
                    "EXPORT_SUPPORTED_OPERATIONAL_PROFILE");
            ColetasIndependentRequest.validate(request);
            require("COLETAS".equals(operational.template.name()), "EXPORT_OPERATIONAL_TEMPLATE");
        }
        require(
                profile.equals(text(request, "profile"))
                        && LocalDate.parse(text(request, "periodStart"))
                                .equals(operational.dates.startInclusive())
                        && LocalDate.parse(text(request, "periodEnd"))
                                .equals(operational.dates.endInclusive())
                        && configuration.businessZone().getId().equals(text(request, "zone")),
                "EXPORT_OPERATIONAL_FILTER_WINDOW_BINDING");
        final UUID attempt = UUID.fromString(text(request, "attemptId"));
        operational.plan.forEach(
                item ->
                        require(
                                attempt.equals(item.request().executionId()),
                                "EXPORT_OPERATIONAL_EXECUTION_BINDING"));
        IndependentExportRuntime.validate(selection, budget);
    }

    static ObjectNode execute(
            final RuntimeConfiguration configuration,
            final ExportValidationSupport.Pin selectionPin,
            final JsonNode selection,
            final long invocationStartedNanos,
            final java.time.Instant invocationStartedAt)
            throws Exception {
        // The caller has just consumed the existing WindowsSqlRuntimeAuthorization capability.
        return IndependentExportRuntime.execute(
                configuration,
                selectionPin,
                selection,
                invocationStartedNanos,
                invocationStartedAt);
    }
}
