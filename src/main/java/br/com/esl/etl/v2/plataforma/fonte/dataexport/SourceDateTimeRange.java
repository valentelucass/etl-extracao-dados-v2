package br.com.esl.etl.v2.plataforma.fonte.dataexport;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

/** Janela inclusiva de atualização, persistida como instant e enviada como hora local da fonte. */
public record SourceDateTimeRange(Instant startInclusive, Instant endInclusive)
        implements DataExportFilterValue {

    private static final DateTimeFormatter SOURCE_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public SourceDateTimeRange {
        Objects.requireNonNull(startInclusive, "O início da atualização é obrigatório.");
        Objects.requireNonNull(endInclusive, "O fim da atualização é obrigatório.");
        if (startInclusive.getNano() != 0 || endInclusive.getNano() != 0) {
            throw new IllegalArgumentException(
                    "A API Data Export trabalha com precisão de segundos.");
        }
        if (endInclusive.isBefore(startInclusive)) {
            throw new IllegalArgumentException(
                    "O fim da atualização não pode ser anterior ao início.");
        }
    }

    @Override
    public String formatForSource(final ZoneId sourceZone) {
        Objects.requireNonNull(sourceZone, "O timezone da fonte é obrigatório.");
        return SOURCE_FORMAT.format(startInclusive.atZone(sourceZone))
                + " - "
                + SOURCE_FORMAT.format(endInclusive.atZone(sourceZone));
    }
}
