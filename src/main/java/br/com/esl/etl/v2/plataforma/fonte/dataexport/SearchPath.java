package br.com.esl.etl.v2.plataforma.fonte.dataexport;

import java.util.Objects;
import java.util.regex.Pattern;

/** Caminho de filtro dentro de {@code search} no contrato Data Export. */
public record SearchPath(String root, String field) {

    private static final Pattern SEGMENT_PATTERN = Pattern.compile("[a-z][a-z0-9_]*");

    public SearchPath {
        validateSegment(root, "root");
        validateSegment(field, "field");
    }

    public String asQueryParameterName() {
        return "search[" + root + "][" + field + "]";
    }

    private static void validateSegment(final String value, final String name) {
        Objects.requireNonNull(value, name + " é obrigatório.");
        if (!SEGMENT_PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException(
                    name + " inválido para filtro Data Export: " + value);
        }
    }
}
