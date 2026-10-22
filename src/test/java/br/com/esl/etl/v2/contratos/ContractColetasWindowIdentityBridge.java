package br.com.esl.etl.v2.contratos;

import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportTemplate;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Compara somente as identidades já lidas; não observa nem autoriza fonte ou cobertura. */
final class ContractColetasWindowIdentityBridge {
    private ContractColetasWindowIdentityBridge() {}

    static void verify(final List<JsonNode> fullWindowRows, final List<JsonNode> dailyUnionRows) {
        if (!associations(fullWindowRows).equals(associations(dailyUnionRows))) {
            throw mismatch();
        }
    }

    private static Map<JsonNode, String> associations(final List<JsonNode> rows) {
        if (rows == null) {
            throw mismatch();
        }
        final var entities =
                ContractDataExportEntityIndex.forTemplate(DataExportTemplate.COLETAS, rows);
        if (!entities.entityIdsVerifiable()) {
            throw mismatch();
        }
        final Map<JsonNode, String> associations = new LinkedHashMap<>();
        final Set<String> naturalKeys = new HashSet<>();
        for (final var entity : entities.entities()) {
            final var key = entity.consistentInteger("sequence_code");
            if (key.isEmpty()
                    || entity.id().isTextual() && entity.id().textValue().isBlank()
                    || !naturalKeys.add(key.orElseThrow())) {
                throw mismatch();
            }
            associations.put(entity.id(), key.orElseThrow());
        }
        return Map.copyOf(associations);
    }

    private static IllegalStateException mismatch() {
        return new IllegalStateException("COLETAS_FULL_WINDOW_DAILY_IDENTITY_MISMATCH");
    }
}
