package br.com.esl.etl.v2.plataforma.fonte.dataexport;

import br.com.esl.etl.v2.plataforma.contrato.ContractMetadata;
import br.com.esl.etl.v2.plataforma.contrato.ContractResponse;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** Exact synthetic wire policy for the 31 named 6908 fields; missing attributes stay lateral. */
final class AnalyticCollectionContract {
    private AnalyticCollectionContract() {}

    static void append(
            final List<ContractResponse.Field> fields,
            final List<ContractMetadata.Element> metadata) {
        try (var input =
                AnalyticCollectionContract.class.getResourceAsStream(
                        "/analytic-laboratory/collection-fields.synthetic.json")) {
            if (input == null) {
                throw new IllegalStateException("ANA_COLLECTION_CONTRACT_MISSING");
            }
            final byte[] bytes = input.readNBytes(16385);
            if (bytes.length > 16384) {
                throw new IllegalStateException("ANA_COLLECTION_CONTRACT_BOUND");
            }
            final var data =
                    new ObjectMapper()
                            .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
                            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                            .readTree(bytes);
            if (!data.path("version").asText().equals("synthetic-analytic-collection-v1")
                    || !data.path("provenance").asText().equals("FIXTURE_SINTETICA_EXPLICITA")
                    || !data.path("fields").isArray()
                    || data.path("fields").size() != 31) {
                throw new IllegalStateException("ANA_COLLECTION_CONTRACT_DECLARATION");
            }
            final var names = new HashSet<String>();
            for (final var declaration : data.path("fields")) {
                final String name = declaration.path("name").asText();
                if (!name.matches("[a-z][a-z0-9_]{1,95}") || !names.add(name)) {
                    throw new IllegalStateException("ANA_COLLECTION_CONTRACT_PATH");
                }
                final var type =
                        ContractResponse.JsonType.valueOf(declaration.path("wire").asText());
                if (type != ContractResponse.JsonType.INTEGER
                        && type != ContractResponse.JsonType.STRING) {
                    throw new IllegalStateException("ANA_COLLECTION_CONTRACT_WIRE");
                }
                final boolean required = name.equals("id");
                final String path = "/" + name;
                fields.removeIf(field -> field.path().equals(path));
                metadata.removeIf(element -> element.path().equals(name));
                fields.add(
                        new ContractResponse.Field(
                                path,
                                ContractResponse.Cardinality.SCALAR,
                                required
                                        ? ContractResponse.Presence.REQUIRED
                                        : ContractResponse.Presence.OPTIONAL,
                                !required,
                                List.of(type)));
                metadata.add(
                        ContractMetadata.Element.fromDeclaredType(
                                ContractMetadata.ElementKind.DATA_FIELD,
                                name,
                                Optional.of(type.name().toLowerCase(Locale.ROOT))));
            }
        } catch (final IOException failure) {
            throw new IllegalStateException("ANA_COLLECTION_CONTRACT_INVALID", failure);
        }
    }
}
