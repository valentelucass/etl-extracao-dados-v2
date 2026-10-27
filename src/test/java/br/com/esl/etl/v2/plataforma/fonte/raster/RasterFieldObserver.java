package br.com.esl.etl.v2.plataforma.fonte.raster;

import br.com.esl.etl.v2.modulos.raster.domain.RasterStop;
import br.com.esl.etl.v2.modulos.raster.domain.RasterTripObservation;
import br.com.esl.etl.v2.plataforma.expansao.ExpansionValue;
import br.com.esl.etl.v2.plataforma.expansao.ExpansionValue.Presence;
import br.com.esl.etl.v2.plataforma.expansao.ExpansionValue.Wire;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;

/** External bounded measurement, no identity adoption, payload persistence or JDBC capability. */
public final class RasterFieldObserver implements RasterResponseParser.Sink {
    private static final String[] TRIP =
            ("CodSolicitacao,Sequencial,CodFilial,StatusViagem,PlacaVeiculo,"
                            + "PlacaCarreta1,PlacaCarreta2,PlacaCarreta3,CPFMotorista1,CPFMotorista2,CNPJClienteOrig,"
                            + "CNPJClienteDest,CodIBGECidadeOrig,CodIBGECidadeDest,DataHoraPrevIni,DataHoraPrevFim,"
                            + "DataHoraRealIni,DataHoraRealFim,DataHoraIdentificouFimViagem,TempoTotalViagem,DentroPrazo,"
                            + "PercentualAtraso,RodouForaHorario,VelocidadeMedia,EventosVelocidade,DesviosDeRota,LinkTimeLine")
                    .split(",");
    private static final String[] STOP =
            ("Ordem,Tipo,CodIBGECidade,CNPJCliente,CodigoCliente,"
                            + "DataHoraPrevChegada,DataHoraPrevSaida,DataHoraRealChegada,DataHoraRealSaida,Latitude,Longitude,"
                            + "DentroPrazo,DiferencaTempo,KmPercorridoEntrega,KmRestanteEntrega,ChegouNaEntrega,"
                            + "DataHoraUltimaPosicao,LatitudeUltimaPosicao,LongitudeUltimaPosicao,ReferenciaUltimaPosicao")
                    .split(",");
    private static final String[] ROUTE = {"CodRota", "Descricao"};
    private final Map<String, Field> fields = new LinkedHashMap<>();
    private final Map<Key, Integer> roots = new LinkedHashMap<>();
    private final Map<StopKey, Integer> stopKeys = new LinkedHashMap<>();
    private final Map<Key, Integer> currentOrders = new LinkedHashMap<>();
    private final EnumMap<Presence, Integer> routes = new EnumMap<>(Presence.class);
    private final EnumMap<Presence, Integer> stopContainers = new EnumMap<>(Presence.class);
    private int trips;
    private int stops;
    private int validTripObservations;
    private int validStops;
    private int unavailableRootKeys;
    private int unavailableStopKeys;
    private int stopsWithoutParentCandidate;
    private int duplicatesWithinTrip;
    private int emptyPresentStopArrays;
    private int nonemptyPresentStopArrays;
    private int maximumChildren;
    private int currentChildren;
    private Key currentRoot;
    private Presence currentStopPresence;
    private boolean closed;

    public RasterFieldObserver() {
        seed("trip", TRIP);
        seed("stop", STOP);
        seed("route", ROUTE);
    }

    private void seed(String scope, String[] names) {
        for (String name : names) {
            fields.put(scope + "." + name, new Field());
        }
    }

    private void requireOpen() {
        if (closed) {
            throw new IllegalStateException("RASTER_MEASUREMENT_CLOSED");
        }
    }

    @Override
    public void trip(int position, RasterTripObservation observation) {
        requireOpen();
        if (position != trips + 1 || position > 499 || observation == null) {
            throw new IllegalArgumentException("RASTER_OBSERVER_TRIP_BOUND");
        }
        finishParent();
        trips++;
        var t = observation.trip();
        observe(
                "trip",
                TRIP,
                t.codSolicitacao(),
                t.sequencial(),
                t.codFilial(),
                t.statusViagem(),
                t.placaVeiculo(),
                t.placaCarreta1(),
                t.placaCarreta2(),
                t.placaCarreta3(),
                t.cpfMotorista1(),
                t.cpfMotorista2(),
                t.cnpjClienteOrig(),
                t.cnpjClienteDest(),
                t.codIbgeCidadeOrig(),
                t.codIbgeCidadeDest(),
                t.dataHoraPrevIni(),
                t.dataHoraPrevFim(),
                t.dataHoraRealIni(),
                t.dataHoraRealFim(),
                t.dataHoraIdentificouFimViagem(),
                t.tempoTotalViagem(),
                t.dentroPrazo(),
                t.percentualAtraso(),
                t.rodouForaHorario(),
                t.velocidadeMedia(),
                t.eventosVelocidade(),
                t.desviosDeRota(),
                t.linkTimeline());
        var r = observation.route();
        observe("route", ROUTE, r.codRota(), r.descricao());
        if (observation.valid()) {
            validTripObservations++;
        }
        routes.merge(observation.routePresence(), 1, Integer::sum);
        currentStopPresence = observation.stopsPresence();
        stopContainers.merge(currentStopPresence, 1, Integer::sum);
        currentRoot = key(t.codSolicitacao());
        if (currentRoot == null) {
            unavailableRootKeys++;
        } else {
            roots.merge(currentRoot, 1, Integer::sum);
        }
    }

    @Override
    public void stop(int parent, int position, RasterStop s) {
        requireOpen();
        if (parent != trips
                || trips == 0
                || position != currentChildren + 1
                || position > 500
                || stops >= 1000
                || currentStopPresence != Presence.VALUE
                || s == null) {
            throw new IllegalArgumentException("RASTER_OBSERVER_STOP_BOUND");
        }
        currentChildren++;
        stops++;
        observe(
                "stop",
                STOP,
                s.ordem(),
                s.tipo(),
                s.codIbgeCidade(),
                s.cnpjCliente(),
                s.codigoCliente(),
                s.dataHoraPrevChegada(),
                s.dataHoraPrevSaida(),
                s.dataHoraRealChegada(),
                s.dataHoraRealSaida(),
                s.latitude(),
                s.longitude(),
                s.dentroPrazo(),
                s.diferencaTempo(),
                s.kmPercorridoEntrega(),
                s.kmRestanteEntrega(),
                s.chegouNaEntrega(),
                s.dataHoraUltimaPosicao(),
                s.latitudeUltimaPosicao(),
                s.longitudeUltimaPosicao(),
                s.referenciaUltimaPosicao());
        if (s.valid()) {
            validStops++;
        }
        Key order = key(s.ordem());
        if (order != null && currentOrders.merge(order, 1, Integer::sum) > 1) {
            duplicatesWithinTrip++;
        }
        if (currentRoot == null) {
            stopsWithoutParentCandidate++;
        }
        if (order == null || currentRoot == null) {
            unavailableStopKeys++;
        } else {
            stopKeys.merge(new StopKey(currentRoot, order), 1, Integer::sum);
        }
    }

    private void observe(String scope, String[] names, ExpansionValue<?>... values) {
        if (names.length != values.length) {
            throw new IllegalStateException("FIXED_FIELD_SET_MISMATCH");
        }
        for (int i = 0; i < names.length; i++) {
            fields.get(scope + "." + names[i]).accept(values[i]);
        }
    }

    private static Key key(ExpansionValue<String> value) {
        return value.presence() == Presence.VALUE && value.issue() == null
                ? new Key(value.wire(), value.raw())
                : null;
    }

    private void finishParent() {
        if (currentStopPresence == Presence.VALUE) {
            if (currentChildren == 0) {
                emptyPresentStopArrays++;
            } else {
                nonemptyPresentStopArrays++;
            }
        }
        maximumChildren = Math.max(maximumChildren, currentChildren);
        currentChildren = 0;
        currentOrders.clear();
        currentRoot = null;
        currentStopPresence = null;
    }

    public JsonNode close(boolean observationCompleted, int httpStatus) {
        requireOpen();
        try {
            finishParent();
            var result = new LinkedHashMap<String, Object>();
            result.put("schema", "RASTER_FIELD_MEASUREMENT_V1");
            result.put("sourceObservationCompleted", observationCompleted);
            result.put(
                    "measurementScope",
                    observationCompleted ? "BOUNDED_RESPONSE" : "PARTIAL_OR_UNOBSERVED");
            result.put("httpStatus", httpStatus);
            result.put("physicalTrips", trips);
            result.put("physicalStops", stops);
            result.put("tripObservationsWithoutParseIssue", validTripObservations);
            result.put("stopsWithoutParseIssue", validStops);
            result.put("tripObservationsWithParseIssue", trips - validTripObservations);
            result.put("stopsWithParseIssue", stops - validStops);
            result.put("rootCandidateDistinct", roots.size());
            result.put("rootCandidateDuplicates", trips - unavailableRootKeys - roots.size());
            result.put("rootCandidateMaximumMultiplicity", maximum(roots));
            result.put("rootCandidateUnavailable", unavailableRootKeys);
            result.put("stopCandidateDistinct", stopKeys.size());
            result.put("stopCandidateDuplicates", stops - unavailableStopKeys - stopKeys.size());
            result.put("stopCandidateMaximumMultiplicity", maximum(stopKeys));
            result.put("stopCandidateUnavailable", unavailableStopKeys);
            result.put("stopsWithoutParentCandidate", stopsWithoutParentCandidate);
            result.put("duplicateOrdersWithinPhysicalTrip", duplicatesWithinTrip);
            result.put("routePresence", presence(routes));
            result.put("stopArrayPresence", presence(stopContainers));
            result.put("presentStopArraysWithZeroChildren", emptyPresentStopArrays);
            result.put("presentStopArraysWithChildren", nonemptyPresentStopArrays);
            result.put("maximumStopsInPhysicalTrip", maximumChildren);
            var attributes = new LinkedHashMap<String, Object>();
            int observed = 0;
            for (var entry : fields.entrySet()) {
                attributes.put(entry.getKey(), entry.getValue().metadata());
                if (entry.getValue().values > 0) {
                    observed++;
                }
            }
            result.put("attributeCount", fields.size());
            result.put("attributesWithValues", observed);
            result.put("attributes", attributes);
            result.put("sourceCompletenessProven", false);
            result.put("canonicalIdentityProven", false);
            result.put("globalIdentityProven", false);
            result.put("snapshotAcceptance", false);
            result.put("operationalAdmission", false);
            result.put("businessDataRetained", false);
            result.put("sql", 0);
            return new ObjectMapper().valueToTree(result);
        } finally {
            closed = true;
            roots.clear();
            stopKeys.clear();
            currentOrders.clear();
            currentRoot = null;
            currentStopPresence = null;
        }
    }

    private static int maximum(Map<?, Integer> values) {
        return values.values().stream().mapToInt(Integer::intValue).max().orElse(0);
    }

    private static Map<String, Integer> presence(Map<Presence, Integer> counts) {
        var result = new LinkedHashMap<String, Integer>();
        for (Presence p : Presence.values()) {
            result.put(p.name(), counts.getOrDefault(p, 0));
        }
        return result;
    }

    private record Key(Wire wire, String raw) {}

    private record StopKey(Key parent, Key order) {}

    private static final class Field {
        private final EnumMap<Presence, Integer> presences = new EnumMap<>(Presence.class);
        private final EnumMap<Wire, Integer> wires = new EnumMap<>(Wire.class);
        private int values;
        private int issues;

        void accept(ExpansionValue<?> value) {
            presences.merge(value.presence(), 1, Integer::sum);
            wires.merge(value.wire(), 1, Integer::sum);
            if (value.presence() == Presence.VALUE) {
                values++;
            }
            if (value.issue() != null) {
                issues++;
            }
        }

        Map<String, Object> metadata() {
            var counts = new LinkedHashMap<String, Integer>();
            for (Wire wire : Wire.values()) {
                counts.put(wire.name(), wires.getOrDefault(wire, 0));
            }
            return Map.of(
                    "state",
                    values == 0 ? "UNOBSERVED" : issues > 0 ? "OBSERVED_WITH_ISSUES" : "OBSERVED",
                    "presence",
                    presence(presences),
                    "wire",
                    counts,
                    "values",
                    values,
                    "parseIssues",
                    issues,
                    "parsedValues",
                    values - issues);
        }
    }
}
