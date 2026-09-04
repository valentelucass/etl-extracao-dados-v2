package br.com.esl.etl.v2.contratos;

import java.util.Locale;
import java.util.Optional;

/** Categoria não sensível do tipo declarado pelo {@code /info}. */
public enum ContractMetadataType {
    UNDECLARED,
    STRING,
    INTEGER,
    NUMBER,
    BOOLEAN,
    DATE,
    DATE_TIME,
    ARRAY,
    OBJECT,
    OTHER;

    public static ContractMetadataType fromDeclaredType(final Optional<String> declaredType) {
        if (declaredType.isEmpty()) {
            return UNDECLARED;
        }
        final String normalized = declaredType.orElseThrow().toLowerCase(Locale.ROOT);
        if (normalized.contains("datetime")
                || normalized.contains("date_time")
                || normalized.contains("timestamp")) {
            return DATE_TIME;
        }
        if (normalized.contains("date")) {
            return DATE;
        }
        if (normalized.contains("bool")) {
            return BOOLEAN;
        }
        if (normalized.contains("int") || normalized.contains("long")) {
            return INTEGER;
        }
        if (normalized.contains("decimal")
                || normalized.contains("float")
                || normalized.contains("double")
                || normalized.contains("number")
                || normalized.contains("numeric")) {
            return NUMBER;
        }
        if (normalized.contains("array") || normalized.contains("list")) {
            return ARRAY;
        }
        if (normalized.contains("object") || normalized.contains("json")) {
            return OBJECT;
        }
        if (normalized.contains("string")
                || normalized.contains("text")
                || normalized.contains("char")) {
            return STRING;
        }
        return OTHER;
    }
}
