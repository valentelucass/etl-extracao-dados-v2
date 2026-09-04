package br.com.esl.etl.v2.plataforma.contrato;

import br.com.esl.etl.v2.plataforma.controle.ImmutableFingerprint;
import java.util.Objects;

/** Release completo do contrato: modelos sanitizados, dois componentes e fingerprint composto. */
public record SourceContractRelease(
        ContractSourceKind sourceKind,
        String documentReference,
        String contractVersion,
        ContractMetadata metadata,
        ContractResponse response,
        ImmutableFingerprint metadataFingerprint,
        ImmutableFingerprint responseFingerprint,
        ImmutableFingerprint contractFingerprint) {

    public SourceContractRelease {
        sourceKind = Objects.requireNonNull(sourceKind, "A origem do contrato é obrigatória.");
        documentReference = ContractText.documentReference(documentReference);
        contractVersion = ContractText.version(contractVersion);
        metadata = Objects.requireNonNull(metadata, "A metadata do contrato é obrigatória.");
        response = Objects.requireNonNull(response, "A resposta do contrato é obrigatória.");
        validateDomain(sourceKind, metadata);
        validateResponse(response);

        final ImmutableFingerprint expectedMetadata =
                ContractCanonicalizer.metadata(
                        sourceKind, documentReference, contractVersion, metadata);
        final ImmutableFingerprint expectedResponse =
                ContractCanonicalizer.response(
                        sourceKind, documentReference, contractVersion, response);
        final ImmutableFingerprint expectedContract =
                ContractCanonicalizer.release(
                        sourceKind,
                        documentReference,
                        contractVersion,
                        expectedMetadata,
                        expectedResponse);
        metadataFingerprint =
                requireExact(
                        metadataFingerprint,
                        expectedMetadata,
                        "O fingerprint de metadata é inconsistente.");
        responseFingerprint =
                requireExact(
                        responseFingerprint,
                        expectedResponse,
                        "O fingerprint de resposta é inconsistente.");
        contractFingerprint =
                requireExact(
                        contractFingerprint,
                        expectedContract,
                        "O fingerprint composto de contrato é inconsistente.");
    }

    public static SourceContractRelease create(
            final ContractSourceKind sourceKind,
            final String documentReference,
            final String contractVersion,
            final ContractMetadata metadata,
            final ContractResponse response) {
        final ImmutableFingerprint metadataFingerprint =
                ContractCanonicalizer.metadata(
                        sourceKind, documentReference, contractVersion, metadata);
        final ImmutableFingerprint responseFingerprint =
                ContractCanonicalizer.response(
                        sourceKind, documentReference, contractVersion, response);
        return new SourceContractRelease(
                sourceKind,
                documentReference,
                contractVersion,
                metadata,
                response,
                metadataFingerprint,
                responseFingerprint,
                ContractCanonicalizer.release(
                        sourceKind,
                        documentReference,
                        contractVersion,
                        metadataFingerprint,
                        responseFingerprint));
    }

    @Override
    public String toString() {
        return "SourceContractRelease[sourceKind="
                + sourceKind
                + ", documentReference="
                + documentReference
                + ", contractVersion="
                + contractVersion
                + ", metadataFingerprintVersion="
                + metadataFingerprint.version()
                + ", responseFingerprintVersion="
                + responseFingerprint.version()
                + "]";
    }

    static void validateDomain(
            final ContractSourceKind sourceKind, final ContractMetadata metadata) {
        final boolean graphQlKinds =
                metadata.elements().stream()
                        .allMatch(
                                element ->
                                        element.kind()
                                                        == ContractMetadata.ElementKind
                                                                .GRAPHQL_SELECTION
                                                || element.kind()
                                                        == ContractMetadata.ElementKind
                                                                .GRAPHQL_ARGUMENT);
        final boolean dataExportKinds =
                metadata.elements().stream()
                        .allMatch(
                                element ->
                                        element.kind() == ContractMetadata.ElementKind.DATA_FIELD
                                                || element.kind()
                                                        == ContractMetadata.ElementKind
                                                                .DATA_FILTER);
        if (sourceKind == ContractSourceKind.GRAPHQL
                && (!graphQlKinds || metadata.approvedDocument().isEmpty())) {
            throw new IllegalArgumentException(
                    "Um contrato GraphQL exige query estática e paths GraphQL.");
        }
        if (sourceKind == ContractSourceKind.DATA_EXPORT
                && (!dataExportKinds || metadata.approvedDocument().isPresent())) {
            throw new IllegalArgumentException(
                    "Um contrato Data Export exige somente fields e filters de /info.");
        }
    }

    private static void validateResponse(final ContractResponse response) {
        if (response.observationState() != ContractResponse.ObservationState.POPULATED) {
            throw new IllegalArgumentException(
                    "O release exige uma resposta populada como baseline.");
        }
        final ContractResponse.Field key =
                response.find(response.keyPath())
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "O release não contém o path da chave."));
        if (key.presence() != ContractResponse.Presence.REQUIRED
                || key.nullable()
                || key.cardinality() != ContractResponse.Cardinality.SCALAR
                || key.jsonTypes().isEmpty()
                || key.jsonTypes().stream().anyMatch(type -> !type.isScalar())) {
            throw new IllegalArgumentException(
                    "A chave do release deve ser escalar, tipada, obrigatória e não nula.");
        }
    }

    private static ImmutableFingerprint requireExact(
            final ImmutableFingerprint actual,
            final ImmutableFingerprint expected,
            final String message) {
        if (!expected.equals(Objects.requireNonNull(actual, message))) {
            throw new IllegalArgumentException(message);
        }
        return actual;
    }
}
