package br.com.esl.etl.v2.plataforma.observabilidade;

import br.com.esl.etl.v2.plataforma.SqlText;
import java.time.Instant;
import java.util.Objects;
import java.util.regex.Pattern;

final class ObservabilityFields {

    private static final Pattern UPPER_CODE = Pattern.compile("[A-Z][A-Z0-9_]{1,63}");
    private static final Pattern OWNER_ROLE = Pattern.compile("[a-z][a-z0-9_-]{1,63}");
    private static final Instant SQL_SERVER_MINIMUM = Instant.parse("0001-01-01T00:00:00Z");
    private static final Instant SQL_SERVER_MAXIMUM = Instant.parse("9999-12-31T23:59:59.999Z");

    private ObservabilityFields() {}

    static String upperCode(final String value, final String label) {
        final String canonical = exactAsciiTrimmed(value, label);
        if (!UPPER_CODE.matcher(canonical).matches()) {
            throw new IllegalArgumentException(label + " deve ser um código técnico canônico.");
        }
        return canonical;
    }

    static String ownerRole(final String value) {
        final String canonical = exactAsciiTrimmed(value, "O owner-papel");
        if (!OWNER_ROLE.matcher(canonical).matches()) {
            throw new IllegalArgumentException("O owner-papel é inválido.");
        }
        return canonical;
    }

    static Instant sqlServerInstant(final Instant value, final String label) {
        final Instant required = Objects.requireNonNull(value, label + " é obrigatório.");
        if (required.isBefore(SQL_SERVER_MINIMUM)
                || required.isAfter(SQL_SERVER_MAXIMUM)
                || required.getNano() % 1_000_000 != 0) {
            throw new IllegalArgumentException(
                    label + " deve caber em DATETIME2(3) sem arredondamento.");
        }
        return required;
    }

    private static String exactAsciiTrimmed(final String value, final String label) {
        Objects.requireNonNull(value, label + " é obrigatório.");
        final String canonical = SqlText.trimAsciiSpace(value);
        if (!canonical.equals(value)) {
            throw new IllegalArgumentException(label + " não pode conter espaços externos.");
        }
        return canonical;
    }
}
