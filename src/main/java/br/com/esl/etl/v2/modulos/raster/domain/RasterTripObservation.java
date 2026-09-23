package br.com.esl.etl.v2.modulos.raster.domain;

import br.com.esl.etl.v2.plataforma.expansao.ExpansionValue.Presence;
import java.util.Objects;

/** Structured route and stop presence are independent from the scalar trip attributes. */
public record RasterTripObservation(
        RasterTrip trip, RasterRoute route, Presence routePresence, Presence stopsPresence) {
    public RasterTripObservation {
        Objects.requireNonNull(trip);
        Objects.requireNonNull(route);
        Objects.requireNonNull(routePresence);
        Objects.requireNonNull(stopsPresence);
    }

    public boolean valid() {
        return trip.valid() && route.valid();
    }
}
