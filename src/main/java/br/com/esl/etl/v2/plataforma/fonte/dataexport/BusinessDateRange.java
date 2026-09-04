package br.com.esl.etl.v2.plataforma.fonte.dataexport;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

/** Janela inclusiva da data de negócio exigida pelos templates. */
public record BusinessDateRange(LocalDate startInclusive, LocalDate endInclusive)
        implements DataExportFilterValue {

    private static final DateTimeFormatter SOURCE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE;

    public BusinessDateRange {
        Objects.requireNonNull(startInclusive, "A data inicial é obrigatória.");
        Objects.requireNonNull(endInclusive, "A data final é obrigatória.");
        if (endInclusive.isBefore(startInclusive)) {
            throw new IllegalArgumentException(
                    "A data final não pode ser anterior à data inicial.");
        }
    }

    @Override
    public String formatForSource(final ZoneId sourceZone) {
        Objects.requireNonNull(sourceZone, "O timezone da fonte é obrigatório.");
        return SOURCE_FORMAT.format(startInclusive) + " - " + SOURCE_FORMAT.format(endInclusive);
    }
}
