package br.com.esl.etl.v2.plataforma.expansao;

import java.util.List;

/** Ordered physical array, including duplicate strings. Positions are never documentary IDs. */
public record ExpansionStrings(List<String> items) {
    public ExpansionStrings {
        items = List.copyOf(items);
        if (items.size() > 32 || items.stream().anyMatch(s -> s.length() > 256)) {
            throw new IllegalArgumentException("EXP_MAPPING_BOUND");
        }
    }
}
