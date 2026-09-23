package br.com.esl.etl.v2.modulos.raster.domain;

import java.time.Instant;

/** Nanoseconds and original offset survive SQL datetime precision; sentinel has no instant. */
public record RasterTime(Instant instant, Integer offsetSeconds, boolean sentinel) {
    public RasterTime {
        if (sentinel != (instant == null) || sentinel != (offsetSeconds == null)) {
            throw new IllegalArgumentException("RAS_TIME_DISPOSITION");
        }
    }
}
