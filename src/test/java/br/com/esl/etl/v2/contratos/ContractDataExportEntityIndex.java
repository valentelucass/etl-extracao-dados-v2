package br.com.esl.etl.v2.contratos;

import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportTemplate;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Agrupa linhas físicas de um relatório detalhado pela entidade que o parâmetro {@code per} limita.
 *
 * <p>Os templates 6908 e 6389 podem repetir uma entidade em várias linhas. Este tipo existe só no
 * harness de contrato e mantém identificadores exclusivamente em memória.
 */
final class ContractDataExportEntityIndex {

    private final int physicalRecordCount;
    private final boolean entityIdsVerifiable;
    private final List<Entity> entities;

    private ContractDataExportEntityIndex(
            final int physicalRecordCount,
            final boolean entityIdsVerifiable,
            final List<Entity> entities) {
        this.physicalRecordCount = physicalRecordCount;
        this.entityIdsVerifiable = entityIdsVerifiable;
        this.entities = List.copyOf(entities);
    }

    static ContractDataExportEntityIndex forTemplate(
            final DataExportTemplate template, final List<JsonNode> records) {
        Objects.requireNonNull(template, "O template Data Export é obrigatório.");
        return from(records, template.paginationEntityField());
    }

    static ContractDataExportEntityIndex from(
            final List<JsonNode> records, final String entityIdField) {
        Objects.requireNonNull(records, "Os registros Data Export são obrigatórios.");
        if (entityIdField == null || entityIdField.isBlank()) {
            throw new IllegalArgumentException("O campo de entidade Data Export é obrigatório.");
        }
        final Map<JsonNode, List<JsonNode>> rowsByEntityId = new LinkedHashMap<>();
        boolean allEntityIdsVerifiable = true;
        for (final JsonNode record : records) {
            final JsonNode entityId = scalarNode(record, entityIdField);
            if (entityId == null) {
                allEntityIdsVerifiable = false;
                continue;
            }
            rowsByEntityId.computeIfAbsent(entityId, ignored -> new ArrayList<>()).add(record);
        }
        final List<Entity> entities =
                rowsByEntityId.entrySet().stream()
                        .map(entry -> new Entity(entry.getKey(), entry.getValue()))
                        .toList();
        return new ContractDataExportEntityIndex(records.size(), allEntityIdsVerifiable, entities);
    }

    int physicalRecordCount() {
        return physicalRecordCount;
    }

    boolean entityIdsVerifiable() {
        return entityIdsVerifiable;
    }

    int entityCount() {
        return entities.size();
    }

    boolean entityLimitWithin(final int requestedPageSize) {
        if (requestedPageSize <= 0) {
            throw new IllegalArgumentException("O tamanho solicitado da página deve ser positivo.");
        }
        return entityIdsVerifiable && entityCount() <= requestedPageSize;
    }

    List<Entity> entities() {
        return entities;
    }

    @Override
    public String toString() {
        return "ContractDataExportEntityIndex[physicalRecordCount="
                + physicalRecordCount
                + ", entityIdsVerifiable="
                + entityIdsVerifiable
                + ", entityCount="
                + entityCount()
                + "]";
    }

    static final class Entity {

        private final JsonNode id;
        private final List<JsonNode> records;

        private Entity(final JsonNode id, final List<JsonNode> records) {
            this.id = Objects.requireNonNull(id, "O ID da entidade é obrigatório.");
            this.records = List.copyOf(records);
        }

        JsonNode id() {
            return id;
        }

        Optional<String> consistentScalar(final String fieldName) {
            if (fieldName == null || fieldName.isBlank()) {
                throw new IllegalArgumentException("O campo escalar é obrigatório.");
            }
            String observedValue = null;
            for (final JsonNode record : records) {
                final JsonNode value = scalarNode(record, fieldName);
                if (value == null) {
                    return Optional.empty();
                }
                final String normalizedValue = normalize(value.asText());
                if (normalizedValue == null) {
                    return Optional.empty();
                }
                if (observedValue == null) {
                    observedValue = normalizedValue;
                } else if (!observedValue.equals(normalizedValue)) {
                    return Optional.empty();
                }
            }
            return Optional.ofNullable(observedValue);
        }

        @Override
        public String toString() {
            return "ContractDataExportEntityIndex.Entity[redacted]";
        }
    }

    private static JsonNode scalarNode(final JsonNode record, final String fieldName) {
        if (record == null || !record.isObject()) {
            return null;
        }
        final JsonNode value = record.get(fieldName);
        if (value == null || value.isNull() || !value.isValueNode()) {
            return null;
        }
        return value;
    }

    private static String normalize(final String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
