package br.com.esl.etl.v2.plataforma.fonte.dataexport;

import br.com.esl.etl.v2.plataforma.contrato.ContractMetadata;
import br.com.esl.etl.v2.plataforma.contrato.ContractResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.List;
import java.util.Optional;

/** Closed packaged declaration for synthetic analytical 6399 fixtures; no provider discovery. */
final class AnalyticManifestContract {
    private AnalyticManifestContract() {}

    static void append(
            final List<ContractResponse.Field> fields,
            final List<ContractMetadata.Element> metadata) {
        try (var input =
                AnalyticManifestContract.class.getResourceAsStream(
                        "/analytic-laboratory/manifest-fields.synthetic.json")) {
            if (input == null) {
                throw new IllegalStateException("ANA_MANIFEST_CONTRACT_MISSING");
            }
            final byte[] bytes = input.readNBytes(32769);
            if (bytes.length > 32768) {
                throw new IllegalStateException("ANA_MANIFEST_CONTRACT_BOUND");
            }
            final var data = new ObjectMapper().readTree(bytes);
            if (!data.path("version").asText().equals("analytic-manifest-capture-v1")
                    || !data.path("provenance").asText().equals("FIXTURE_SINTETICA_EXPLICITA")
                    || !data.path("fields").isArray()
                    || data.path("fields").size() != 92) {
                throw new IllegalStateException("ANA_MANIFEST_CONTRACT_DECLARATION");
            }
            for (final var definition : data.path("fields")) {
                final String name = definition.path("name").asText();
                if (!name.matches("[a-z][a-z0-9_]{1,95}")) {
                    throw new IllegalStateException("ANA_MANIFEST_CONTRACT_PATH");
                }
                final var type =
                        ContractResponse.JsonType.valueOf(definition.path("wire").asText());
                final boolean required = name.equals("sequence_code");
                final String path = "/" + name;
                fields.removeIf(field -> field.path().equals(path));
                metadata.removeIf(element -> element.path().equals(name));
                fields.add(
                        new ContractResponse.Field(
                                path,
                                type == ContractResponse.JsonType.ARRAY
                                        ? ContractResponse.Cardinality.ARRAY
                                        : ContractResponse.Cardinality.SCALAR,
                                required
                                        ? ContractResponse.Presence.REQUIRED
                                        : ContractResponse.Presence.OPTIONAL,
                                !required,
                                List.of(type)));
                metadata.add(
                        ContractMetadata.Element.fromDeclaredType(
                                ContractMetadata.ElementKind.DATA_FIELD,
                                name,
                                Optional.of(type.name().toLowerCase(java.util.Locale.ROOT))));
                if (type == ContractResponse.JsonType.ARRAY) {
                    fields.add(
                            new ContractResponse.Field(
                                    path + "/*",
                                    ContractResponse.Cardinality.SCALAR,
                                    ContractResponse.Presence.OPTIONAL,
                                    true,
                                    List.of(ContractResponse.JsonType.STRING)));
                }
            }
        } catch (final IOException failure) {
            throw new IllegalStateException("ANA_MANIFEST_CONTRACT_INVALID", failure);
        }
    }
}
