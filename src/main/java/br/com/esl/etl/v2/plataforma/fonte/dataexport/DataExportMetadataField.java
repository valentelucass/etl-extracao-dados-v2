package br.com.esl.etl.v2.plataforma.fonte.dataexport;

import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/** Campo ou filtro declarado em metadados de um template Data Export. */
public record DataExportMetadataField(
        String technicalName, Optional<String> declaredType, Optional<String> label) {

    private static final Pattern TECHNICAL_NAME =
            Pattern.compile("[A-Za-z_][A-Za-z0-9_.:~/-]{0,255}");

    public DataExportMetadataField {
        technicalName = requireTechnicalName(technicalName);
        declaredType = sanitizeOptional(declaredType, 256, "O tipo declarado é obrigatório.");
        label = sanitizeOptional(label, 1_024, "O rótulo é obrigatório.");
    }

    private static Optional<String> sanitizeOptional(
            final Optional<String> value, final int maximumLength, final String message) {
        Objects.requireNonNull(value, message);
        return value.map(text -> requireText(text, maximumLength, message));
    }

    private static String requireTechnicalName(final String value) {
        final String normalized =
                requireText(value, 256, "O nome técnico do metadado é obrigatório.");
        if (!TECHNICAL_NAME.matcher(normalized).matches()) {
            throw new IllegalArgumentException("O nome técnico do metadado é inválido.");
        }
        return normalized;
    }

    private static String requireText(
            final String value, final int maximumLength, final String message) {
        if (value == null || value.isBlank() || value.length() > maximumLength) {
            throw new IllegalArgumentException(message);
        }
        final String normalized = value.trim();
        if (normalized.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException(message);
        }
        return normalized;
    }

    @Override
    public String toString() {
        return "DataExportMetadataField[declaredTypePresent="
                + declaredType.isPresent()
                + ", labelPresent="
                + label.isPresent()
                + "]";
    }
}
