package br.com.esl.etl.v2.plataforma.fonte.dataexport;

import br.com.esl.etl.v2.plataforma.contrato.ContractDriftException;
import br.com.esl.etl.v2.plataforma.contrato.ContractResponse;
import br.com.esl.etl.v2.plataforma.contrato.ContractRunGuard;
import br.com.esl.etl.v2.plataforma.contrato.ContractSourceKind;
import java.util.Objects;
import java.util.Optional;

/** Compõe os dois gateways oficiais com o mesmo gate de contrato da ocorrência. */
public final class DataExportContractGate {

    private DataExportContractGate() {}

    public static DataExportHttpGatewayBundle enforce(
            final DataExportHttpGatewayBundle gateways,
            final DataExportTemplate expectedTemplate,
            final ContractRunGuard guard) {
        return enforce(gateways, expectedTemplate, guard, Optional.empty());
    }

    public static DataExportHttpGatewayBundle enforce(
            final DataExportHttpGatewayBundle gateways,
            final DataExportPageRequest initialRequest,
            final ContractRunGuard guard) {
        Objects.requireNonNull(initialRequest, "A requisição inicial é obrigatória.");
        return enforce(gateways, initialRequest.template(), guard, Optional.of(initialRequest));
    }

    private static DataExportHttpGatewayBundle enforce(
            final DataExportHttpGatewayBundle gateways,
            final DataExportTemplate expectedTemplate,
            final ContractRunGuard guard,
            final Optional<DataExportPageRequest> initialRequest) {
        Objects.requireNonNull(gateways, "Os gateways Data Export são obrigatórios.");
        Objects.requireNonNull(expectedTemplate, "O template esperado é obrigatório.");
        Objects.requireNonNull(guard, "O gate de contrato é obrigatório.");
        guard.verifySource(
                ContractSourceKind.DATA_EXPORT,
                DataExportContractAdapter.documentReference(expectedTemplate));
        guard.bindCompletenessStatus(expectedTemplate.completenessStatus());
        if (gateways.observationConfiguration().isEmpty()) {
            guard.failClosed(ContractDriftException.Reason.EXECUTION_BINDING_MISMATCH);
        }
        final DataExportContractObservationConfiguration observationConfiguration =
                gateways.observationConfiguration().orElseThrow();
        final DataExportRequestProfile profile =
                initialRequest
                        .map(DataExportPageRequest::profile)
                        .orElse(DataExportRequestProfile.LEGACY);
        if (initialRequest.isPresent()) {
            if (!profile.approves(initialRequest.orElseThrow(), guard.sourceSelection())) {
                guard.failClosed(ContractDriftException.Reason.EXECUTION_BINDING_MISMATCH);
            }
        } else if (!DataExportRequestProfile.legacySelection(guard.sourceSelection())) {
            guard.failClosed(ContractDriftException.Reason.EXECUTION_BINDING_MISMATCH);
        }
        if (observationConfiguration.template() != expectedTemplate) {
            guard.failClosed(ContractDriftException.Reason.EXECUTION_BINDING_MISMATCH);
        }
        var selectedRuntime = observationConfiguration.runtimeConfigurationFingerprint();
        try {
            gateways.requireRequestProfile(profile);
            selectedRuntime = profile.runtimeFingerprint(selectedRuntime);
        } catch (final IllegalArgumentException invalid) {
            guard.failClosed(ContractDriftException.Reason.EXECUTION_BINDING_MISMATCH);
        }
        guard.verifyObservationBinding(
                selectedRuntime,
                observationConfiguration.observationLimits(),
                observationConfiguration.responsePathBoundary().fingerprint(),
                observationConfiguration.expectedResponseForm().recordRoot(),
                observationConfiguration.expectedResponseForm().rootCardinality(),
                observationConfiguration.approvedKeyPath());
        return DataExportHttpGatewayBundle.contractBound(
                request -> {
                    if (request.template() != expectedTemplate) {
                        guard.failClosed(ContractDriftException.Reason.EXECUTION_BINDING_MISMATCH);
                    }
                    if (!request.profile().equals(profile)
                            || !request.profile().approves(request, guard.sourceSelection())) {
                        guard.failClosed(ContractDriftException.Reason.EXECUTION_BINDING_MISMATCH);
                    }
                    guard.verifySource(
                            ContractSourceKind.DATA_EXPORT,
                            DataExportContractAdapter.documentReference(expectedTemplate));
                    guard.verifyNextDataExportPage(request.page());
                    try {
                        final DataExportPageResponse response =
                                Objects.requireNonNull(
                                        gateways.dataGateway().fetch(request),
                                        "O gateway Data Export retornou uma página nula.");
                        if (response.contractObservation().isEmpty()) {
                            guard.failClosed(
                                    ContractDriftException.Reason.RESPONSE_EVIDENCE_REQUIRED);
                        }
                        if (!response.observationLimits()
                                .orElseThrow()
                                .equals(guard.observationLimits())) {
                            guard.failClosed(
                                    ContractDriftException.Reason.EXECUTION_BINDING_MISMATCH);
                        }
                        if (!response.observationBoundaryFingerprint()
                                .orElseThrow()
                                .equals(guard.responsePathBoundary().fingerprint())) {
                            guard.failClosed(
                                    ContractDriftException.Reason.EXECUTION_BINDING_MISMATCH);
                        }
                        final ContractResponse observation =
                                response.contractObservation().orElseThrow();
                        guard.observeDataExportResponse(request.page(), observation);
                        return response;
                    } catch (final RuntimeException exception) {
                        guard.invalidateEvidence();
                        throw exception;
                    }
                },
                template -> {
                    if (template != expectedTemplate) {
                        guard.failClosed(ContractDriftException.Reason.EXECUTION_BINDING_MISMATCH);
                    }
                    guard.verifySource(
                            ContractSourceKind.DATA_EXPORT,
                            DataExportContractAdapter.documentReference(expectedTemplate));
                    final DataExportTemplateInfo info;
                    try {
                        info = gateways.templateInfoGateway().fetchInfo(template);
                        if (info.template() != expectedTemplate) {
                            guard.failClosed(
                                    ContractDriftException.Reason.EXECUTION_BINDING_MISMATCH);
                        }
                        guard.validateMetadata(DataExportContractAdapter.metadata(info));
                    } catch (final RuntimeException exception) {
                        guard.invalidateEvidence();
                        throw exception;
                    }
                    return info;
                },
                observationConfiguration,
                gateways);
    }
}
