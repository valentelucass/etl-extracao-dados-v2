package br.com.esl.etl.v2.plataforma.expansao;

import java.time.LocalDate;
import java.util.Objects;

/** Explicit versioned relation evidence; aliases and text positions cannot supply a link. */
public record ExpansionRelation(
        String bindingKey,
        int revision,
        Kind kind,
        ExpansionKey root,
        ExpansionKey part,
        ExpansionKey component,
        ExpansionKey document,
        String targetKey,
        LocalDate targetDate,
        Cardinality cardinality,
        boolean active,
        String evidence) {
    public ExpansionRelation {
        if (bindingKey == null
                || !bindingKey.matches("synthetic-[a-z0-9-]{1,48}")
                || revision < 1
                || revision > 1000
                || evidence == null
                || !evidence.matches("synthetic-[a-z0-9-]{1,48}")
                || targetKey == null
                || !targetKey.matches("INTEGER:[1-9][0-9]{0,17}")) {
            throw new IllegalArgumentException("EXP_RELATION_BINDING_REQUIRED");
        }
        Objects.requireNonNull(kind);
        Objects.requireNonNull(root);
        Objects.requireNonNull(part);
        Objects.requireNonNull(component);
        Objects.requireNonNull(document);
        Objects.requireNonNull(targetDate);
        Objects.requireNonNull(cardinality);
        if (kind == Kind.LOC_FREIGHT && root.kind() != ExpansionKey.Kind.INTEGER) {
            throw new IllegalArgumentException("EXP_LOC_INTEGER_KEY");
        }
    }

    public enum Kind {
        FAT_DOCUMENT_FREIGHT,
        INV_FREIGHT,
        SIN_FREIGHT,
        LOC_FREIGHT
    }

    public enum Cardinality {
        ONE_TO_ONE,
        ONE_TO_MANY,
        MANY_TO_MANY
    }

    @Override
    public String toString() {
        return "ExpansionRelation[kind=" + kind + ",revision=" + revision + "]";
    }
}
