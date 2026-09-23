package br.com.esl.etl.v2.plataforma.expansao;

import java.util.Objects;

/** Versioned lateral binding; a capture ordinal only addresses an observation in that capture. */
public record ExpansionBinding(
        ExpansionKey root,
        ExpansionKey part,
        ExpansionKey component,
        int revision,
        String evidence,
        String currency,
        String unit,
        boolean additiveAllocation,
        boolean active,
        boolean reactivation) {
    public ExpansionBinding {
        Objects.requireNonNull(root);
        Objects.requireNonNull(part);
        Objects.requireNonNull(component);
        if (revision < 1
                || revision > 1000
                || evidence == null
                || !evidence.matches("synthetic-[a-z0-9-]{1,64}")
                || currency == null
                || !currency.matches("[A-Z]{3}")
                || !"MAJOR".equals(unit)) {
            throw new IllegalArgumentException("EXP_BINDING_CONTRACT");
        }
    }
}
