package br.com.esl.etl.v2.contratos.caracterizacao;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Defesa final contra campos livres ou dados operacionais em relatórios e receipts. */
public final class CharacterizationReportSanitizer {

    private static final Set<String> FORBIDDEN_FIELDS =
            Set.of(
                    "url",
                    "endpoint",
                    "token",
                    "header",
                    "headers",
                    "payload",
                    "cursor",
                    "rawcursor",
                    "businessid",
                    "businessidhash",
                    "businesskey",
                    "name",
                    "document",
                    "licenseplate",
                    "branch",
                    "sourceinstance",
                    "tenantscope",
                    "rawvalue",
                    "secret",
                    "recordcontent");

    private CharacterizationReportSanitizer() {}

    public static void requireSanitized(final JsonNode value) {
        if (value == null) {
            throw new IllegalArgumentException("O relatório sanitizado é obrigatório.");
        }
        inspect(value);
    }

    private static void inspect(final JsonNode value) {
        if (value.isObject()) {
            final Iterator<Map.Entry<String, JsonNode>> fields = value.fields();
            while (fields.hasNext()) {
                final Map.Entry<String, JsonNode> field = fields.next();
                final String normalizedName =
                        field.getKey().toLowerCase(Locale.ROOT).replace("_", "").replace("-", "");
                if (FORBIDDEN_FIELDS.stream()
                        .anyMatch(
                                forbidden ->
                                        normalizedName.equals(forbidden)
                                                || normalizedName.startsWith(forbidden)
                                                || normalizedName.endsWith(forbidden))) {
                    throw new IllegalArgumentException("O relatório contém campo proibido.");
                }
                inspect(field.getValue());
            }
        } else if (value.isArray()) {
            value.forEach(CharacterizationReportSanitizer::inspect);
        } else if (value.isTextual()) {
            final String text = value.textValue();
            if (text.contains("://") || text.regionMatches(true, 0, "Bearer ", 0, 7)) {
                throw new IllegalArgumentException("O relatório contém valor proibido.");
            }
        }
    }
}
