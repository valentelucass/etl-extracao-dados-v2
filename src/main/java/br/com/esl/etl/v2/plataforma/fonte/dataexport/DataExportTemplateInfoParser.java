package br.com.esl.etl.v2.plataforma.fonte.dataexport;

import br.com.esl.etl.v2.plataforma.contrato.ContractMetadata;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Converte o documento de {@code /info} em metadados, sem reter o corpo remoto. */
public final class DataExportTemplateInfoParser {

    public ParsedTemplateInfo parse(final JsonNode response) {
        Objects.requireNonNull(response, "A resposta de metadados Data Export é obrigatória.");
        rejectErrorEnvelope(response);
        final JsonNode document = unwrapData(response);
        rejectErrorEnvelope(document);
        if (!document.isObject()) {
            throw new IllegalStateException("Os metadados Data Export devem ser um objeto JSON.");
        }
        final List<DataExportMetadataField> fields =
                parseCollection(
                        document.path("fields"), "fields", true, ContractMetadata.MAXIMUM_ELEMENTS);
        final List<DataExportMetadataField> filters =
                parseCollection(
                        document.path("filters"),
                        "filters",
                        false,
                        ContractMetadata.MAXIMUM_ELEMENTS - fields.size());
        return new ParsedTemplateInfo(fields, filters);
    }

    private JsonNode unwrapData(final JsonNode response) {
        if (response.isObject() && response.has("data")) {
            final JsonNode data = response.get("data");
            if (data == null || data.isNull()) {
                throw new IllegalStateException(
                        "Os metadados Data Export não contêm dados válidos.");
            }
            return data;
        }
        return response;
    }

    private List<DataExportMetadataField> parseCollection(
            final JsonNode value,
            final String collectionName,
            final boolean required,
            final int maximumItems) {
        if (value.isMissingNode() || value.isNull()) {
            if (required) {
                throw new IllegalStateException(
                        "Os metadados Data Export não declaram " + collectionName + ".");
            }
            return List.of();
        }
        if (value.size() > maximumItems) {
            throw new IllegalStateException("Os metadados Data Export excedem o limite de itens.");
        }
        final List<DataExportMetadataField> fields = new ArrayList<>();
        if (value.isArray()) {
            for (final JsonNode item : value) {
                fields.add(parseItem(item, null, collectionName));
            }
        } else if (value.isObject()) {
            final Iterator<java.util.Map.Entry<String, JsonNode>> entries = value.fields();
            while (entries.hasNext()) {
                final java.util.Map.Entry<String, JsonNode> entry = entries.next();
                fields.add(parseItem(entry.getValue(), entry.getKey(), collectionName));
            }
        } else {
            throw new IllegalStateException(
                    "Os metadados Data Export declaram "
                            + collectionName
                            + " em formato inválido.");
        }
        ensureUniqueTechnicalNames(fields, collectionName);
        return List.copyOf(fields);
    }

    private DataExportMetadataField parseItem(
            final JsonNode value, final String fallbackName, final String collectionName) {
        if (value.isTextual()) {
            if (fallbackName != null) {
                return new DataExportMetadataField(
                        requiredText(fallbackName, collectionName),
                        Optional.of(value.asText().trim()),
                        Optional.empty());
            }
            return new DataExportMetadataField(
                    requiredText(value.asText(), collectionName),
                    Optional.empty(),
                    Optional.empty());
        }
        if (!value.isObject()) {
            throw new IllegalStateException(
                    "Um item de " + collectionName + " deve ser objeto ou texto.");
        }
        final String technicalName =
                firstText(value, "name", "field", "key", "technical_name", "technicalName")
                        .orElseGet(() -> requiredText(fallbackName, collectionName));
        return new DataExportMetadataField(
                technicalName,
                firstText(value, "type", "data_type", "dataType"),
                firstText(value, "label", "title", "description"));
    }

    private Optional<String> firstText(final JsonNode value, final String... names) {
        for (final String name : names) {
            final JsonNode candidate = value.get(name);
            if (candidate != null && candidate.isTextual() && !candidate.asText().isBlank()) {
                return Optional.of(candidate.asText().trim());
            }
        }
        return Optional.empty();
    }

    private void ensureUniqueTechnicalNames(
            final List<DataExportMetadataField> fields, final String collectionName) {
        final Set<String> names = new HashSet<>();
        for (final DataExportMetadataField field : fields) {
            if (!names.add(field.technicalName().toLowerCase(Locale.ROOT))) {
                throw new IllegalStateException(
                        "Os metadados Data Export repetem " + collectionName + ".");
            }
        }
    }

    private void rejectErrorEnvelope(final JsonNode value) {
        if (value.isObject() && (value.has("error") || value.has("errors"))) {
            throw new IllegalStateException("Os metadados Data Export contêm um envelope de erro.");
        }
    }

    private String requiredText(final String value, final String collectionName) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    "Um item de " + collectionName + " não declara nome técnico.");
        }
        return value.trim();
    }

    /** Resultado independente do transporte, usado para montar a sonda tipada. */
    public record ParsedTemplateInfo(
            List<DataExportMetadataField> fields, List<DataExportMetadataField> filters) {

        public ParsedTemplateInfo {
            fields =
                    List.copyOf(
                            Objects.requireNonNull(
                                    fields, "Os campos declarados são obrigatórios."));
            filters =
                    List.copyOf(
                            Objects.requireNonNull(
                                    filters, "Os filtros declarados são obrigatórios."));
        }
    }
}
