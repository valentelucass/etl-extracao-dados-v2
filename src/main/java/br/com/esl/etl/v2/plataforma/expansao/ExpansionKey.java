package br.com.esl.etl.v2.plataforma.expansao;

import java.util.Objects;

/** An explicitly declared synthetic key, distinct from every observed source candidate. */
public record ExpansionKey(Kind kind, String value) {
    public ExpansionKey {
        Objects.requireNonNull(kind);
        Objects.requireNonNull(value);
        if (value.isEmpty()
                || value.length() > 64
                || !value.equals(value.strip())
                || value.chars().anyMatch(Character::isISOControl)
                || (kind == Kind.INTEGER && !value.matches("-?(0|[1-9][0-9]{0,37})"))) {
            throw new IllegalArgumentException("EXP_SYNTHETIC_KEY_INVALID");
        }
    }

    public enum Kind {
        INTEGER,
        STRING
    }
}
