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

/**
 * P1B candidate: pinned observed structures for three templates. This catalog does not select a
 * runtime profile, authorize source/storage, establish identity or grant completeness.
 *
 * <p>NUMBER evidence is the numeric family, not an observed INTEGER/fraction distinction. Array
 * descendants are pinned from the new structural evidence; unknown paths remain refused.
 */
public final class DataExportObservedEntityContractCatalog {
    private static final int MAXIMUM_RESOURCE_BYTES = 65_536;

    private DataExportObservedEntityContractCatalog() {}

    public static SourceContractRelease release(final DataExportTemplate template) {
        if (template == null) {
            throw new IllegalArgumentException("OBSERVED_P1B_TEMPLATE_REQUIRED");
        }
        return switch (template) {
            case MANIFESTOS -> Manifestos.RELEASE;
            case COTACOES -> Cotacoes.RELEASE;
            case LOCALIZACAO_CARGAS -> Localizacao.RELEASE;
            default -> throw new IllegalArgumentException("OBSERVED_P1B_TEMPLATE_UNSUPPORTED");
        };
    }

    private static SourceContractRelease load(final DataExportTemplate template) {
        final var pin = pin(template);
        try (var input =
                DataExportObservedEntityContractCatalog.class.getResourceAsStream(pin.resource())) {
            return read(template, pin.version(), input);
        } catch (final IOException | NoSuchAlgorithmException failure) {
            throw new IllegalStateException("OBSERVED_P1B_RESOURCE_UNAVAILABLE", failure);
        }
    }

    /**
     * Package-local injection for offline checks; expected hashes cannot be supplied by callers.
     */
    static SourceContractRelease read(
            final DataExportTemplate template, final String version, final InputStream input)
            throws IOException, NoSuchAlgorithmException {
        final var pin = pin(template);
        if (!pin.version().equals(version)) {
            throw new IllegalArgumentException("OBSERVED_P1B_SELECTION_MISMATCH");
        }
        if (input == null) {
            throw new IllegalStateException("OBSERVED_P1B_RESOURCE_MISSING");
        }
        final byte[] bytes = input.readNBytes(MAXIMUM_RESOURCE_BYTES + 1);
        if (bytes.length > MAXIMUM_RESOURCE_BYTES
                || !pin.resourceSha256()
                        .equals(
                                HexFormat.of()
                                        .formatHex(
                                                MessageDigest.getInstance("SHA-256")
                                                        .digest(bytes)))) {
            throw new IllegalStateException("OBSERVED_P1B_RESOURCE_MISMATCH");
        }
        final JsonNode root =
                JsonMapper.builder()
                        .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                        .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                        .build()
                        .readTree(bytes);
        if (!root.path("templateId").isIntegralNumber()
                || root.path("templateId").intValue() != pin.templateId()
                || !"DATA_EXPORT".equals(root.path("sourceKind").asText())
                || !DataExportContractAdapter.documentReference(template)
                        .equals(root.path("documentReference").asText())
                || !pin.version().equals(root.path("contractVersion").asText())) {
            throw new IllegalStateException("OBSERVED_P1B_SELECTION_MISMATCH");
        }
        final var release =
                SourceContractRelease.create(
                        ContractSourceKind.DATA_EXPORT,
                        root.path("documentReference").asText(),
                        root.path("contractVersion").asText(),
                        metadata(root.path("metadata")),
                        response(root.path("response")));
        final var hashes = root.path("fingerprints");
        if (!pin.metadataSha256().equals(release.metadataFingerprint().sha256())
                || !pin.responseSha256().equals(release.responseFingerprint().sha256())
                || !pin.releaseSha256().equals(release.contractFingerprint().sha256())
                || !pin.metadataSha256().equals(hashes.path("metadata").asText())
                || !pin.responseSha256().equals(hashes.path("response").asText())
                || !pin.releaseSha256().equals(hashes.path("release").asText())
                || !"$".equals(release.response().recordRoot())
                || !release.response().keyPath().equals("/" + template.paginationEntityField())
                || release.response().rootCardinality() != ContractResponse.Cardinality.ARRAY
                || release.response().fields().size() != pin.responseFieldCount()
                || release.response().fields().stream()
                                .filter(field -> field.path().indexOf('/', 1) < 0)
                                .count()
                        != pin.fieldCount()
                || release.metadata().elements().size() != pin.fieldCount() + pin.filterCount()) {
            throw new IllegalStateException("OBSERVED_P1B_FINGERPRINT_MISMATCH");
        }
        return release;
    }

    private static DataExportObservedEntityPins.Pin pin(final DataExportTemplate template) {
        return DataExportObservedEntityPins.ALL.stream()
                .filter(value -> value.template() == template)
                .findFirst()
                .orElseThrow(
                        () -> new IllegalArgumentException("OBSERVED_P1B_TEMPLATE_UNSUPPORTED"));
    }

    private static ContractMetadata metadata(final JsonNode root) {
        final var elements = new ArrayList<ContractMetadata.Element>();
        for (final var node : root) {
            final var hash = node.path("declaredTypeFingerprint");
            final var element =
                    new ContractMetadata.Element(
                            ContractMetadata.ElementKind.valueOf(node.path("kind").asText()),
                            node.path("path").asText(),
                            ContractMetadata.DeclaredType.valueOf(
                                    node.path("declaredType").asText()),
                            hash.isObject()
                                    ? Optional.of(
                                            new ImmutableFingerprint(
                                                    hash.path("version").asText(),
                                                    hash.path("sha256").asText()))
                                    : Optional.empty());
            if (element.kind() == ContractMetadata.ElementKind.DATA_FIELD
                    && element.declaredType() != ContractMetadata.DeclaredType.UNDECLARED) {
                throw new IllegalStateException("OBSERVED_P1B_UNDECLARED_INFO_REQUIRED");
            }
            elements.add(element);
        }
        return new ContractMetadata(elements, Optional.empty());
    }

    private static ContractResponse response(final JsonNode root) {
        final var fields = new ArrayList<ContractResponse.Field>();
        for (final var node : root.path("fields")) {
            final var types = new ArrayList<ContractResponse.JsonType>();
            for (final var type : node.path("jsonTypes")) {
                types.add(ContractResponse.JsonType.valueOf(type.asText()));
            }
            fields.add(
                    new ContractResponse.Field(
                            ContractResponse.FieldScope.valueOf(node.path("scope").asText()),
                            node.path("path").asText(),
                            ContractResponse.Cardinality.valueOf(node.path("cardinality").asText()),
                            ContractResponse.Presence.valueOf(node.path("presence").asText()),
                            node.path("nullable").asBoolean(),
                            types));
        }
        return new ContractResponse(
                root.path("recordRoot").asText(),
                ContractResponse.Cardinality.valueOf(root.path("rootCardinality").asText()),
                ContractResponse.ObservationState.valueOf(root.path("observationState").asText()),
                root.path("keyPath").asText(),
                fields);
    }

    private static final class Manifestos {
        private static final SourceContractRelease RELEASE = load(DataExportTemplate.MANIFESTOS);
    }

    private static final class Cotacoes {
        private static final SourceContractRelease RELEASE = load(DataExportTemplate.COTACOES);
    }

    private static final class Localizacao {
        private static final SourceContractRelease RELEASE =
                load(DataExportTemplate.LOCALIZACAO_CARGAS);
    }
}
