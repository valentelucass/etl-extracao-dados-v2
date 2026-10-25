package br.com.esl.etl.v2.plataforma.fonte.dataexport;

import br.com.esl.etl.v2.plataforma.contrato.ContractMetadata;
import br.com.esl.etl.v2.plataforma.contrato.ContractResponse;
import br.com.esl.etl.v2.plataforma.contrato.ContractSourceKind;
import br.com.esl.etl.v2.plataforma.contrato.SourceContractRelease;
import br.com.esl.etl.v2.plataforma.controle.ImmutableFingerprint;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.Optional;

/** Independently observed structure; selection does not grant source or storage admission. */
public final class DataExportObservedFretesContractCatalog {
    public static final String VERSION = "2026-10-24.fretes-observed-curl.v1";
    private static final String RESOURCE =
            "/contracts/dataexport/fretes-6389-observed-20261024.json";
    private static final int MAXIMUM_RESOURCE_BYTES = 65_536;
    private static final String RESOURCE_SHA256 =
            "19b4d5d9e093a1ba1c88978edd46ef5755f9d6f35772b3efa8213d3f89ad6adb";
    private static final String RELEASE_SHA256 =
            "ad072100143c765705cd7b01e581f19ce3e5bb9a38d181f9cfeae4bcbd141b35";

    private DataExportObservedFretesContractCatalog() {}

    public static SourceContractRelease release() {
        return Holder.RELEASE;
    }

    private static SourceContractRelease load() {
        try (var input =
                DataExportObservedFretesContractCatalog.class.getResourceAsStream(RESOURCE)) {
            return read(input);
        } catch (final IOException | NoSuchAlgorithmException failure) {
            throw new IllegalStateException("OBSERVED_FRETES_RESOURCE_UNAVAILABLE", failure);
        }
    }

    static SourceContractRelease read(final InputStream input)
            throws IOException, NoSuchAlgorithmException {
        if (input == null) {
            throw new IllegalStateException("OBSERVED_FRETES_RESOURCE_MISSING");
        }
        final byte[] bytes = input.readNBytes(MAXIMUM_RESOURCE_BYTES + 1);
        if (bytes.length > MAXIMUM_RESOURCE_BYTES
                || !RESOURCE_SHA256.equals(
                        HexFormat.of()
                                .formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)))) {
            throw new IllegalStateException("OBSERVED_FRETES_RESOURCE_MISMATCH");
        }
        final JsonNode root =
                JsonMapper.builder()
                        .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                        .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                        .build()
                        .readTree(bytes);
        final var release =
                SourceContractRelease.create(
                        ContractSourceKind.valueOf(root.path("sourceKind").asText()),
                        root.path("documentReference").asText(),
                        root.path("contractVersion").asText(),
                        metadata(root.path("metadata")),
                        response(root.path("response")));
        final var fingerprints = root.path("fingerprints");
        if (!VERSION.equals(release.contractVersion())
                || !RELEASE_SHA256.equals(release.contractFingerprint().sha256())
                || !release.metadataFingerprint()
                        .sha256()
                        .equals(fingerprints.path("metadata").asText())
                || !release.responseFingerprint()
                        .sha256()
                        .equals(fingerprints.path("response").asText())
                || !release.contractFingerprint()
                        .sha256()
                        .equals(fingerprints.path("release").asText())) {
            throw new IllegalStateException("OBSERVED_FRETES_FINGERPRINT_MISMATCH");
        }
        return release;
    }

    private static ContractMetadata metadata(final JsonNode root) {
        final var elements = new ArrayList<ContractMetadata.Element>();
        for (final var element : root) {
            final var fingerprint = element.path("declaredTypeFingerprint");
            elements.add(
                    new ContractMetadata.Element(
                            ContractMetadata.ElementKind.valueOf(element.path("kind").asText()),
                            element.path("path").asText(),
                            ContractMetadata.DeclaredType.valueOf(
                                    element.path("declaredType").asText()),
                            fingerprint.isObject()
                                    ? Optional.of(
                                            new ImmutableFingerprint(
                                                    fingerprint.path("version").asText(),
                                                    fingerprint.path("sha256").asText()))
                                    : Optional.empty()));
        }
        return new ContractMetadata(elements, Optional.empty());
    }

    private static ContractResponse response(final JsonNode root) {
        final var fields = new ArrayList<ContractResponse.Field>();
        for (final var field : root.path("fields")) {
            final var types = new ArrayList<ContractResponse.JsonType>();
            for (final var type : field.path("jsonTypes")) {
                types.add(ContractResponse.JsonType.valueOf(type.asText()));
            }
            fields.add(
                    new ContractResponse.Field(
                            ContractResponse.FieldScope.valueOf(field.path("scope").asText()),
                            field.path("path").asText(),
                            ContractResponse.Cardinality.valueOf(
                                    field.path("cardinality").asText()),
                            ContractResponse.Presence.valueOf(field.path("presence").asText()),
                            field.path("nullable").asBoolean(),
                            types));
        }
        return new ContractResponse(
                root.path("recordRoot").asText(),
                ContractResponse.Cardinality.valueOf(root.path("rootCardinality").asText()),
                ContractResponse.ObservationState.valueOf(root.path("observationState").asText()),
                root.path("keyPath").asText(),
                fields);
    }

    private static final class Holder {
        private static final SourceContractRelease RELEASE = load();
    }
}
