package br.com.esl.etl.v2.plataforma.fonte.raster;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/** Half-open civil window. A null pair never means full. */
public record RasterWindow(LocalDate start, LocalDate endExclusive) {
    public RasterWindow {
        if (start == null
                || endExclusive == null
                || !start.isBefore(endExclusive)
                || ChronoUnit.DAYS.between(start, endExclusive) > 3660) {
            throw new IllegalArgumentException("RAS_WINDOW");
        }
    }

    public long days() {
        return ChronoUnit.DAYS.between(start, endExclusive);
    }
}
