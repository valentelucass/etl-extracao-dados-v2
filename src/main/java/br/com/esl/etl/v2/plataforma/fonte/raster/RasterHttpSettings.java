package br.com.esl.etl.v2.plataforma.fonte.raster;

import java.net.URI;
import java.time.Duration;
import java.time.ZoneId;

/** RAS-HTTP-OBS-001: opt-in observation only; no provider or production defaults. */
public record RasterHttpSettings(
        boolean enabled,
        URI endpoint,
        Duration timeout,
        int maximumResponseBytes,
        int maximumTrips,
        int maximumStops,
        ZoneId zone) {
    public static final String PATH = "/datasnap/rest/TWebService/%22getEventoFimViagem%22";

    public RasterHttpSettings {
        if (enabled) {
            if (endpoint == null
                    || !"https".equals(endpoint.getScheme())
                    || endpoint.getHost() == null
                    || endpoint.getHost().isBlank()
                    || endpoint.getPort() == 0
                    || endpoint.getPort() > 65535
                    || !PATH.equals(endpoint.getRawPath())
                    || endpoint.getRawUserInfo() != null
                    || endpoint.getRawQuery() != null
                    || endpoint.getRawFragment() != null) {
                throw new IllegalArgumentException("RAS_HTTP_TARGET");
            }
            if (timeout == null
                    || timeout.compareTo(Duration.ofMillis(1)) < 0
                    || timeout.compareTo(Duration.ofSeconds(30)) > 0
                    || maximumResponseBytes < 1
                    || maximumResponseBytes > RasterResponseParser.MAX_BYTES
                    || maximumTrips < 1
                    || maximumTrips >= 500
                    || maximumStops < 1
                    || maximumStops > 1000
                    || !ZoneId.of("America/Sao_Paulo").equals(zone)) {
                throw new IllegalArgumentException("RAS_HTTP_LIMITS");
            }
        }
    }

    public static RasterHttpSettings disabled() {
        return new RasterHttpSettings(false, null, null, 0, 0, 0, null);
    }

    @Override
    public String toString() {
        return "RasterHttpSettings[enabled=" + enabled + ",endpoint=REDACTED]";
    }
}
