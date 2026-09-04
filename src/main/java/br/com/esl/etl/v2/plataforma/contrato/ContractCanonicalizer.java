package br.com.esl.etl.v2.plataforma.contrato;

import br.com.esl.etl.v2.plataforma.controle.ImmutableFingerprint;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

/** Encoding length-prefixed, ordenado e com separação de domínio para SHA-256. */
final class ContractCanonicalizer {

    private static final int MAXIMUM_CANONICAL_BYTES = 2_097_152;
    private static final String METADATA_VERSION = "source-metadata-v1";
    private static final String RESPONSE_VERSION = "source-response-v1";
    private static final String RELEASE_VERSION = "source-contract-release-v1";
    private static final String POLICY_VERSION = "contract-compatibility-policy-v3";
    private static final String CHANGE_VERSION = "contract-change-v2";
    private static final String GRAPHQL_VERSION = "graphql-document-v1";
    private static final String CONFIGURATION_VERSION = "contract-bound-configuration-v1";
    private static final String UNRECOGNIZED_TYPE_VERSION = "declared-type-v1";
    private static final String RESPONSE_PATH_BOUNDARY_VERSION =
            "contract-response-path-boundary-v1";
    private static final String SYNTHETIC_RESPONSE_PATH_BOUNDARY_VERSION =
            "synthetic-response-path-boundary-v1";
    private static final String SOURCE_SEMANTICS_VERSION = "source-contract-semantics-v1";

    private ContractCanonicalizer() {}

    static ImmutableFingerprint metadata(
            final ContractSourceKind sourceKind,
            final String documentReference,
            final String contractVersion,
            final ContractMetadata metadata) {
        final Encoder encoder = new Encoder(METADATA_VERSION);
        encoder.write(sourceKind.name());
        encoder.write(documentReference);
        encoder.write(contractVersion);
        encoder.write(metadata.elements().size());
        for (final ContractMetadata.Element element : metadata.elements()) {
            encoder.write(element.kind().name());
            encoder.write(element.path());
            encoder.write(element.declaredType().name());
            encoder.write(element.declaredTypeFingerprint().isPresent());
            element.declaredTypeFingerprint()
                    .ifPresent(
                            fingerprint -> {
                                encoder.write(fingerprint.version());
                                encoder.write(fingerprint.sha256());
                            });
        }
        encoder.write(metadata.approvedDocument().isPresent());
        metadata.approvedDocument()
                .ifPresent(
                        document -> {
                            final ImmutableFingerprint fingerprint = document.fingerprint();
                            encoder.write(fingerprint.version());
                            encoder.write(fingerprint.sha256());
                        });
        return encoder.finish(METADATA_VERSION);
    }

    static ImmutableFingerprint response(
            final ContractSourceKind sourceKind,
            final String documentReference,
            final String contractVersion,
            final ContractResponse response) {
        final Encoder encoder = new Encoder(RESPONSE_VERSION);
        encoder.write(sourceKind.name());
        encoder.write(documentReference);
        encoder.write(contractVersion);
        encoder.write(response.recordRoot());
        encoder.write(response.rootCardinality().name());
        encoder.write(response.observationState().name());
        encoder.write(response.keyPath());
        encoder.write(response.fields().size());
        for (final ContractResponse.Field field : response.fields()) {
            writeField(encoder, field);
        }
        return encoder.finish(RESPONSE_VERSION);
    }

    static ImmutableFingerprint release(
            final ContractSourceKind sourceKind,
            final String documentReference,
            final String contractVersion,
            final ImmutableFingerprint metadata,
            final ImmutableFingerprint response) {
        final Encoder encoder = new Encoder(RELEASE_VERSION);
        encoder.write(sourceKind.name());
        encoder.write(documentReference);
        encoder.write(contractVersion);
        encoder.write(metadata.version());
        encoder.write(metadata.sha256());
        encoder.write(response.version());
        encoder.write(response.sha256());
        return encoder.finish(contractVersion);
    }

    static ImmutableFingerprint policy(
            final String policyVersion,
            final ImmutableFingerprint baselineContract,
            final List<ContractAllowance> allowances,
            final List<ContractOpaquePath> opaquePaths) {
        Objects.requireNonNull(allowances, "As permissões de contrato são obrigatórias.");
        Objects.requireNonNull(opaquePaths, "Os paths opacos são obrigatórios.");
        final List<ContractAllowance> sorted = new ArrayList<>(allowances);
        sorted.sort(
                Comparator.comparing(ContractAllowance::component)
                        .thenComparing(
                                allowance ->
                                        allowance.responseFieldScope().map(Enum::name).orElse(""))
                        .thenComparing(ContractAllowance::path)
                        .thenComparing(ContractAllowance::kind)
                        .thenComparing(
                                allowance ->
                                        allowance
                                                .approvedResponseField()
                                                .map(ContractCanonicalizer::fieldSignature)
                                                .orElse(""))
                        .thenComparing(allowance -> allowance.changeSignature().version())
                        .thenComparing(allowance -> allowance.changeSignature().sha256()));
        final Encoder encoder = new Encoder(POLICY_VERSION);
        encoder.write(policyVersion);
        encoder.write(baselineContract.version());
        encoder.write(baselineContract.sha256());
        encoder.write(sorted.size());
        for (final ContractAllowance allowance : sorted) {
            encoder.write(allowance.component().name());
            encoder.write(allowance.kind().name());
            encoder.write(allowance.responseFieldScope().isPresent());
            allowance.responseFieldScope().ifPresent(scope -> encoder.write(scope.name()));
            encoder.write(allowance.approvedResponseField().isPresent());
            allowance
                    .approvedResponseField()
                    .ifPresent(field -> encoder.write(fieldSignature(field)));
            encoder.write(allowance.path());
            encoder.write(allowance.changeSignature().version());
            encoder.write(allowance.changeSignature().sha256());
        }
        final List<ContractOpaquePath> sortedOpaquePaths = new ArrayList<>(opaquePaths);
        sortedOpaquePaths.sort(
                Comparator.comparing(ContractOpaquePath::scope)
                        .thenComparing(ContractOpaquePath::path));
        encoder.write(sortedOpaquePaths.size());
        for (final ContractOpaquePath opaquePath : sortedOpaquePaths) {
            encoder.write(opaquePath.scope().name());
            encoder.write(opaquePath.path());
        }
        return encoder.finish(POLICY_VERSION);
    }

    static ImmutableFingerprint change(
            final SourceContractRelease baseline,
            final ContractChange.Component component,
            final ContractChange.Kind kind,
            final java.util.Optional<ContractResponse.FieldScope> responseFieldScope,
            final String path,
            final String structuralSignature) {
        final Encoder encoder = new Encoder(CHANGE_VERSION);
        encoder.write(baseline.contractFingerprint().version());
        encoder.write(baseline.contractFingerprint().sha256());
        encoder.write(component.name());
        encoder.write(kind.name());
        encoder.write(responseFieldScope.isPresent());
        responseFieldScope.ifPresent(scope -> encoder.write(scope.name()));
        encoder.write(path);
        encoder.write(structuralSignature);
        return encoder.finish(CHANGE_VERSION);
    }

    static String fieldSignature(final ContractResponse.Field field) {
        final StringBuilder signature =
                new StringBuilder()
                        .append(field.scope().name())
                        .append(':')
                        .append(field.cardinality().name())
                        .append(':')
                        .append(field.presence().name())
                        .append(':')
                        .append(field.nullable());
        for (final ContractResponse.JsonType type : field.jsonTypes()) {
            signature.append(':').append(type.name());
        }
        return signature.toString();
    }

    static String metadataSignature(final ContractMetadata.Element element) {
        return element.kind().name()
                + ':'
                + element.declaredType().name()
                + element.declaredTypeFingerprint()
                        .map(
                                fingerprint ->
                                        ':' + fingerprint.version() + ':' + fingerprint.sha256())
                        .orElse("");
    }

    static ImmutableFingerprint declaredTypeText(final String declaredType) {
        final Encoder encoder = new Encoder(UNRECOGNIZED_TYPE_VERSION);
        encoder.write(declaredType.strip().toLowerCase(java.util.Locale.ROOT));
        return encoder.finish(UNRECOGNIZED_TYPE_VERSION);
    }

    static ImmutableFingerprint graphQlDocument(final String normalizedDocument) {
        final Encoder encoder = new Encoder(GRAPHQL_VERSION);
        encoder.write(normalizedDocument);
        return encoder.finish(GRAPHQL_VERSION);
    }

    static ImmutableFingerprint boundConfiguration(
            final SourceContractRelease release,
            final ContractCompatibilityPolicy policy,
            final ImmutableFingerprint runtimeConfiguration,
            final ContractObservationLimits observationLimits) {
        final Encoder encoder = new Encoder(CONFIGURATION_VERSION);
        encoder.write(release.contractFingerprint().version());
        encoder.write(release.contractFingerprint().sha256());
        encoder.write(policy.fingerprint().version());
        encoder.write(policy.fingerprint().sha256());
        encoder.write(runtimeConfiguration.version());
        encoder.write(runtimeConfiguration.sha256());
        encoder.write(observationLimits.maximumDepth());
        encoder.write(observationLimits.maximumPaths());
        encoder.write(observationLimits.maximumNodes());
        return encoder.finish(runtimeConfiguration.version());
    }

    static ImmutableFingerprint responsePathBoundary(
            final SourceContractRelease release, final ContractCompatibilityPolicy policy) {
        final Encoder encoder = new Encoder(RESPONSE_PATH_BOUNDARY_VERSION);
        encoder.write(release.contractFingerprint().version());
        encoder.write(release.contractFingerprint().sha256());
        encoder.write(policy.fingerprint().version());
        encoder.write(policy.fingerprint().sha256());
        return encoder.finish(RESPONSE_PATH_BOUNDARY_VERSION);
    }

    static ImmutableFingerprint syntheticResponsePathBoundary() {
        return new Encoder(SYNTHETIC_RESPONSE_PATH_BOUNDARY_VERSION)
                .finish(SYNTHETIC_RESPONSE_PATH_BOUNDARY_VERSION);
    }

    static ImmutableFingerprint sourceSemantics(
            final String contractVersion, final String... orderedElements) {
        final Encoder encoder = new Encoder(SOURCE_SEMANTICS_VERSION);
        encoder.write(ContractText.version(contractVersion));
        Objects.requireNonNull(orderedElements, "Os elementos semânticos são obrigatórios.");
        if (orderedElements.length == 0 || orderedElements.length > 256) {
            throw new IllegalArgumentException(
                    "O contrato semântico deve ter entre um e 256 elementos.");
        }
        encoder.write(orderedElements.length);
        for (final String element : orderedElements) {
            encoder.write(element);
        }
        return encoder.finish(SOURCE_SEMANTICS_VERSION);
    }

    private static void writeField(final Encoder encoder, final ContractResponse.Field field) {
        encoder.write(field.scope().name());
        encoder.write(field.path());
        encoder.write(field.cardinality().name());
        encoder.write(field.presence().name());
        encoder.write(field.nullable());
        encoder.write(field.jsonTypes().size());
        for (final ContractResponse.JsonType type : field.jsonTypes()) {
            encoder.write(type.name());
        }
    }

    private static final class Encoder {

        private final MessageDigest digest;
        private int encodedBytes;

        private Encoder(final String domain) {
            try {
                digest = MessageDigest.getInstance("SHA-256");
            } catch (final NoSuchAlgorithmException exception) {
                throw new IllegalStateException("SHA-256 não está disponível.", exception);
            }
            write(domain);
        }

        private void write(final boolean value) {
            write(Boolean.toString(value));
        }

        private void write(final int value) {
            write(Integer.toString(value));
        }

        private void write(final String value) {
            Objects.requireNonNull(value, "O valor canônico é obrigatório.");
            final byte[] bytes = encodeStrictUtf8(value);
            final long prospective = (long) encodedBytes + Integer.BYTES + bytes.length;
            if (prospective > MAXIMUM_CANONICAL_BYTES) {
                throw new IllegalArgumentException("O contrato excede o limite canônico.");
            }
            digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
            digest.update(bytes);
            encodedBytes = (int) prospective;
        }

        private static byte[] encodeStrictUtf8(final String value) {
            final ByteBuffer encoded;
            try {
                encoded =
                        StandardCharsets.UTF_8
                                .newEncoder()
                                .onMalformedInput(CodingErrorAction.REPORT)
                                .onUnmappableCharacter(CodingErrorAction.REPORT)
                                .encode(CharBuffer.wrap(value));
            } catch (final CharacterCodingException exception) {
                throw new IllegalArgumentException(
                        "O valor canônico contém Unicode inválido.", exception);
            }
            final byte[] bytes = new byte[encoded.remaining()];
            encoded.get(bytes);
            return bytes;
        }

        private ImmutableFingerprint finish(final String version) {
            return new ImmutableFingerprint(version, HexFormat.of().formatHex(digest.digest()));
        }
    }
}
