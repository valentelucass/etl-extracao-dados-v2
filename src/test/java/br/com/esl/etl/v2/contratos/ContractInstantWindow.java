package br.com.esl.etl.v2.contratos;

import br.com.esl.etl.v2.plataforma.fonte.dataexport.SourceDateTimeRange;
import java.time.Instant;
import java.util.Objects;

/** Janela de atualização com offset explícito, evitando inferência de fuso no runner remoto. */
public record ContractInstantWindow(Instant startInclusive, Instant endInclusive) {

    private static final String RANGE_SEPARATOR = "..";

    public ContractInstantWindow {
        Objects.requireNonNull(startInclusive, "O início da janela de atualização é obrigatório.");
        Objects.requireNonNull(endInclusive, "O fim da janela de atualização é obrigatório.");
        if (endInclusive.isBefore(startInclusive)) {
            throw new IllegalArgumentException(
                    "O fim da janela de atualização não pode ser anterior ao início.");
        }
    }

    public static ContractInstantWindow parse(final String value, final String configurationKey) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    "A janela de atualização é obrigatória: " + configurationKey + ".");
        }
        final String[] bounds = value.trim().split("\\.\\.", -1);
        if (bounds.length != 2 || bounds[0].isBlank() || bounds[1].isBlank()) {
            throw new IllegalArgumentException(
                    "A janela de atualização é inválida: " + configurationKey + ".");
        }
        try {
            return new ContractInstantWindow(
                    Instant.parse(bounds[0].trim()), Instant.parse(bounds[1].trim()));
        } catch (final RuntimeException exception) {
            throw new IllegalArgumentException(
                    "A janela de atualização é inválida: " + configurationKey + ".");
        }
    }

    public SourceDateTimeRange asSourceDateTimeRange() {
        return new SourceDateTimeRange(startInclusive, endInclusive);
    }

    public static String rangeSeparator() {
        return RANGE_SEPARATOR;
    }
}
