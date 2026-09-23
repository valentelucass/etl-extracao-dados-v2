package br.com.esl.etl.v2.plataforma.relacional;

import java.time.LocalDate;
import java.util.Objects;

/** Explicit fixture assertion; equal numbers or aliases cannot construct this evidence. */
public record RelationalBinding(
        String evidenceId,
        Relation relation,
        Key origin,
        Key originComponent,
        Key target,
        Key targetComponent,
        LocalDate targetDate,
        int revision,
        Cardinality cardinality) {
    public enum Relation {
        MC,
        CF
    }

    public enum Cardinality {
        ONE_TO_ONE,
        ONE_TO_MANY,
        MANY_TO_MANY
    }

    public enum WireType {
        INTEGER,
        STRING,
        ROOT
    }

    public record Key(WireType type, String value) {
        public Key {
            Objects.requireNonNull(type);
            Objects.requireNonNull(value);
            if (value.isEmpty()
                    || value.length() > 120
                    || (type == WireType.INTEGER && !value.matches("0|[1-9][0-9]*"))
                    || (type == WireType.ROOT && !value.equals("ROOT"))
                    || (type == WireType.STRING && !value.equals(value.strip()))
                    || value.chars().anyMatch(c -> c < 32 || c == 127)) {
                throw new IllegalArgumentException("REL_LAB_KEY_INVALID");
            }
        }

        public String storage() {
            return type == WireType.ROOT ? "ROOT" : type.name() + ":" + value;
        }

        public static Key integer(final long value) {
            return new Key(WireType.INTEGER, Long.toString(value));
        }

        public static Key root() {
            return new Key(WireType.ROOT, "ROOT");
        }
    }

    public RelationalBinding {
        Objects.requireNonNull(evidenceId);
        Objects.requireNonNull(relation);
        Objects.requireNonNull(origin);
        Objects.requireNonNull(originComponent);
        Objects.requireNonNull(target);
        Objects.requireNonNull(targetComponent);
        Objects.requireNonNull(targetDate);
        Objects.requireNonNull(cardinality);
        if (!evidenceId.matches("synthetic-[a-zA-Z0-9_-]{1,54}")
                || revision < 1
                || revision > 1000
                || origin.type() != WireType.INTEGER
                || origin.value().equals("0")
                || target.type() != WireType.INTEGER
                || target.value().equals("0")
                || originComponent.type() == WireType.ROOT
                || (relation == Relation.MC) != (targetComponent.type() == WireType.ROOT)) {
            throw new IllegalArgumentException("REL_LAB_BINDING_INVALID");
        }
    }
}
