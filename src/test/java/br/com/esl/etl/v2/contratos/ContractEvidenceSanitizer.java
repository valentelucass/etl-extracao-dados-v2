package br.com.esl.etl.v2.contratos;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/** Validação de defesa em profundidade para os poucos textos permitidos no summary. */
final class ContractEvidenceSanitizer {

    private static final Pattern FIELD_NAME = Pattern.compile("[A-Za-z][A-Za-z0-9_]{0,127}");
    private static final Pattern FILTER_NAME =
            Pattern.compile("[A-Za-z][A-Za-z0-9_]*(?:\\.[A-Za-z][A-Za-z0-9_]*){0,3}");
    private static final Set<String> JSON_TYPES =
            Set.of("null", "string", "integer", "number", "boolean", "array", "object", "other");
    private static final Set<String> TEXTUAL_FORMATS =
            Set.of("blank", "text", "date", "local-date-time", "offset-date-time");

    private ContractEvidenceSanitizer() {}

    static String fieldName(final String value, final String message) {
        return identifier(value, FIELD_NAME, message);
    }

    static String filterName(final String value, final String message) {
        return identifier(value, FILTER_NAME, message);
    }

    static List<String> fieldNames(final List<String> values, final String message) {
        return values(values, value -> fieldName(value, message));
    }

    static List<String> filterNames(final List<String> values, final String message) {
        return values(values, value -> filterName(value, message));
    }

    static List<String> jsonTypes(final List<String> values) {
        return closedValues(values, JSON_TYPES, "Os tipos JSON da evidência são inválidos.");
    }

    static List<String> textualFormats(final List<String> values) {
        return closedValues(
                values, TEXTUAL_FORMATS, "Os formatos textuais da evidência são inválidos.");
    }

    private static String identifier(
            final String value, final Pattern pattern, final String message) {
        if (value == null || !pattern.matcher(value).matches()) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }

    private static List<String> closedValues(
            final List<String> values, final Set<String> allowedValues, final String message) {
        return values(
                values,
                value -> {
                    if (!allowedValues.contains(value)) {
                        throw new IllegalArgumentException(message);
                    }
                    return value;
                });
    }

    private static List<String> values(
            final List<String> values,
            final java.util.function.Function<String, String> validator) {
        Objects.requireNonNull(values, "Os valores sanitizados são obrigatórios.");
        return List.copyOf(values.stream().map(validator).toList());
    }
}
