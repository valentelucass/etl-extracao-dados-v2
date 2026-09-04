package br.com.esl.etl.v2.plataforma.fonte.graphql;

import br.com.esl.etl.v2.plataforma.contrato.ContractDriftException;
import br.com.esl.etl.v2.plataforma.contrato.ContractResponse;
import br.com.esl.etl.v2.plataforma.contrato.ContractRunGuard;
import br.com.esl.etl.v2.plataforma.contrato.ContractSourceKind;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken;
import java.util.Objects;

/** Vincula documento, configuração e resposta antes de permitir travessia produtiva. */
final class GraphQlContractGate {

    private GraphQlContractGate() {}

    static GraphQlGateway enforce(
            final GraphQlGateway gateway,
            final GraphQlContractObservationConfiguration configuration,
            final ContractRunGuard guard,
            final CancellationToken cancellationToken) {
        final GraphQlGateway requiredGateway =
                Objects.requireNonNull(gateway, "O gateway GraphQL é obrigatório.");
        final GraphQlContractObservationConfiguration requiredConfiguration =
                Objects.requireNonNull(configuration, "A configuração GraphQL é obrigatória.");
        final ContractRunGuard requiredGuard =
                Objects.requireNonNull(guard, "O gate de contrato é obrigatório.");
        final CancellationToken requiredCancellation =
                Objects.requireNonNull(cancellationToken, "O cancelamento GraphQL é obrigatório.");
        requiredGateway.verifyCancellationToken(requiredCancellation);
        final GraphQlReadOperation operation = requiredConfiguration.operation();
        GraphQlTransitionalFieldCatalog.validate();
        requiredGuard.verifySource(ContractSourceKind.GRAPHQL, operation.documentReference());
        requiredGuard.bindCompletenessStatus(operation.completenessStatus());
        requiredGuard.verifyObservationBinding(
                requiredConfiguration.runtimeConfigurationFingerprint(),
                requiredConfiguration.observationLimits(),
                requiredConfiguration.responsePathBoundary().fingerprint(),
                GraphQlContractAdapter.recordRoot(operation),
                ContractResponse.Cardinality.ARRAY,
                GraphQlContractAdapter.APPROVED_KEY_PATH);
        requiredGuard.validateMetadata(GraphQlContractAdapter.metadata(operation));
        if (GraphQlTransitionalFieldCatalog.shadowUpsertBlocked(operation)) {
            requiredGuard.blockPromotionForSourceState();
        }

        return new GraphQlGateway() {
            @Override
            public GraphQlPageResponse fetch(final GraphQlPageRequest request) {
                if (request.operation() != operation) {
                    requiredGuard.failClosed(
                            ContractDriftException.Reason.EXECUTION_BINDING_MISMATCH);
                }
                requiredGuard.verifySource(
                        ContractSourceKind.GRAPHQL, operation.documentReference());
                try {
                    final GraphQlPageResponse response =
                            Objects.requireNonNull(
                                    requiredGateway.fetch(request),
                                    "O gateway GraphQL retornou uma página nula.");
                    if (response.contractObservation().isEmpty()) {
                        requiredGuard.failClosed(
                                ContractDriftException.Reason.RESPONSE_EVIDENCE_REQUIRED);
                    }
                    if (!response.observationLimits()
                                    .orElseThrow()
                                    .equals(requiredGuard.observationLimits())
                            || !response.observationBoundaryFingerprint()
                                    .orElseThrow()
                                    .equals(requiredGuard.responsePathBoundary().fingerprint())) {
                        requiredGuard.failClosed(
                                ContractDriftException.Reason.EXECUTION_BINDING_MISMATCH);
                    }
                    requiredGuard.observeGraphQlResponse(
                            response.contractObservation().orElseThrow());
                    return response.validatedBy(requiredGuard.executionContext());
                } catch (final RuntimeException exception) {
                    requiredGuard.invalidateEvidence();
                    throw exception;
                }
            }

            @Override
            public void verifyCancellationToken(final CancellationToken candidate) {
                if (requiredCancellation
                        != Objects.requireNonNull(
                                candidate, "O cancelamento GraphQL é obrigatório.")) {
                    requiredGuard.failClosed(
                            ContractDriftException.Reason.EXECUTION_BINDING_MISMATCH);
                }
            }
        };
    }
}
