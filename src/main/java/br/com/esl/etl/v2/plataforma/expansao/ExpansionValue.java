package br.com.esl.etl.v2.plataforma.expansao;

import java.util.Objects;

/** Presence and original wire representation survive parsing failure and canonicalization. */
public record ExpansionValue<T>(Presence presence, Wire wire, String raw, T value, String issue) {
    public ExpansionValue {
        Objects.requireNonNull(presence);
        Objects.requireNonNull(wire);
        if (raw != null && raw.length() > 4000) {
            throw new IllegalArgumentException("EXP_RAW_BOUND");
        }
        if (presence != Presence.VALUE && (raw != null || value != null || issue != null)) {
            throw new IllegalArgumentException("EXP_PRESENCE_CONFLICT");
        }
        if (presence == Presence.VALUE && (raw == null || (value == null) == (issue == null))) {
            throw new IllegalArgumentException("EXP_PARSE_DISPOSITION_REQUIRED");
        }
    }

    public boolean valid() {
        return issue == null;
    }

    public enum Presence {
        ABSENT,
        NULL,
        VALUE
    }

    public enum Wire {
        ABSENT,
        NULL,
        STRING,
        INTEGER,
        NUMBER,
        BOOLEAN,
        ARRAY,
        OBJECT
    }
}
