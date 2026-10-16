package br.com.esl.etl.v2.plataforma.fonte.raster;

import java.time.LocalDate;

/** Explicit source civil bounds; no inferred translation from the internal half-open window. */
public record RasterObservationRequest(
        LocalDate startInclusive, LocalDate endInclusive, String environment, String status) {
    public RasterObservationRequest {
        if (startInclusive == null
                || !startInclusive.equals(endInclusive)
                || !boundedText(environment)
                || !boundedText(status)) {
            throw new IllegalArgumentException("RAS_OBSERVATION_REQUEST");
        }
    }

    private static boolean boundedText(final String value) {
        return value != null
                && !value.isBlank()
                && value.length() <= 80
                && value.chars().noneMatch(Character::isISOControl);
    }

    @Override
    public String toString() {
        return "RasterObservationRequest[explicitCivilDay=true,parameters=REDACTED]";
    }
}
