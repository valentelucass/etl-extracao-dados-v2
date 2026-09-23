package br.com.esl.etl.v2.plataforma.fonte.raster;

import br.com.esl.etl.v2.modulos.raster.aplicacao.RasterMapper;
import br.com.esl.etl.v2.modulos.raster.aplicacao.RasterTripDto;
import br.com.esl.etl.v2.modulos.raster.domain.RasterStop;
import br.com.esl.etl.v2.modulos.raster.domain.RasterTripObservation;
import br.com.esl.etl.v2.plataforma.expansao.ExpansionValue.Presence;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.cfg.JsonNodeFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.time.ZoneId;
import java.util.Objects;

/** One bounded response, no retained collection of the traversal. */
public final class RasterResponseParser {
    public static final int MAX_BYTES = 10 * 1024 * 1024;
    private final ObjectMapper mapper;
    private final ZoneId zone;

    public RasterResponseParser(final ZoneId zone) {
        this.zone = Objects.requireNonNull(zone);
        mapper =
                JsonMapper.builder(
                                JsonFactory.builder()
                                        .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                                        .streamReadConstraints(
                                                StreamReadConstraints.builder()
                                                        .maxNestingDepth(16)
                                                        .maxStringLength(4000)
                                                        .maxNumberLength(40)
                                                        .build())
                                        .build())
                        .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
                        .disable(JsonNodeFeature.STRIP_TRAILING_BIGDECIMAL_ZEROES)
                        .build();
    }

    public Counts parse(final byte[] body, final Sink sink) throws IOException, SQLException {
        Objects.requireNonNull(sink);
        if (body == null || body.length == 0 || body.length > MAX_BYTES) {
            throw new IllegalArgumentException("RAS_RESPONSE_BOUND");
        }
        final String text =
                StandardCharsets.UTF_8
                        .newDecoder()
                        .onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT)
                        .decode(ByteBuffer.wrap(body))
                        .toString();
        try (var parser = mapper.createParser(text)) {
            final JsonNode root = mapper.readTree(parser);
            if (parser.nextToken() != null) {
                throw new IllegalArgumentException("RAS_TRAILING_CONTENT");
            }
            final var counter = new Counter();
            visit(root, sink, counter, 0);
            return new Counts(counter.trips, counter.stops);
        }
    }

    private void visit(final JsonNode node, final Sink sink, final Counter counter, final int depth)
            throws SQLException {
        if (node == null || node.isNull() || depth > 4) {
            throw new IllegalArgumentException("RAS_ENVELOPE_MISSING");
        }
        if (node.isArray()) {
            if (node.size() > 500) {
                throw new IllegalArgumentException("RAS_RESPONSE_CAP");
            }
            for (final JsonNode child : node) {
                visit(child, sink, counter, depth + 1);
            }
            return;
        }
        if (!node.isObject() || node.hasNonNull("error") || node.hasNonNull("Erro")) {
            throw new IllegalArgumentException("RAS_ERROR_ENVELOPE");
        }
        final JsonNode result = RasterFieldParser.select(node, "result", "Result");
        final JsonNode trips = RasterFieldParser.select(node, "Viagens", "viagens");
        final JsonNode code = RasterFieldParser.select(node, "CodSolicitacao", "codSolicitacao");
        final int shapes =
                (result != null ? 1 : 0) + (trips != null ? 1 : 0) + (code != null ? 1 : 0);
        if (shapes != 1) {
            throw new IllegalArgumentException("RAS_ENVELOPE_AMBIGUOUS");
        }
        if (result != null || trips != null) {
            visit(result != null ? result : trips, sink, counter, depth + 1);
            return;
        }
        if (++counter.trips > 500) {
            throw new IllegalArgumentException("RAS_RESPONSE_CAP");
        }
        final var dto = new RasterTripDto(node);
        final JsonNode route = RasterFieldParser.select(dto.value(), "Rota", "rota");
        final JsonNode stops =
                RasterFieldParser.select(dto.value(), "ColetasEntregas", "coletasEntregas");
        if (route != null && !route.isNull() && !route.isObject()) {
            throw new IllegalArgumentException("RAS_ROUTE_OBJECT");
        }
        final var trip =
                new RasterTripObservation(
                        RasterMapper.trip(dto.value(), zone),
                        RasterMapper.route(route, zone),
                        presence(route),
                        presence(stops));
        sink.trip(counter.trips, trip);
        if (stops != null && !stops.isNull()) {
            if (!stops.isArray() || stops.size() > 500) {
                throw new IllegalArgumentException("RAS_STOPS_SHAPE_BOUND");
            }
            for (int ordinal = 0; ordinal < stops.size(); ordinal++) {
                if (!stops.get(ordinal).isObject()) {
                    throw new IllegalArgumentException("RAS_STOP_OBJECT");
                }
                counter.stops++;
                sink.stop(counter.trips, ordinal + 1, RasterMapper.stop(stops.get(ordinal), zone));
            }
        }
    }

    private static Presence presence(final JsonNode node) {
        return node == null ? Presence.ABSENT : node.isNull() ? Presence.NULL : Presence.VALUE;
    }

    public record Counts(int trips, int stops) {}

    private static final class Counter {
        private int trips;
        private int stops;
    }

    public interface Sink {
        void trip(int physicalPosition, RasterTripObservation observation) throws SQLException;

        void stop(int tripPosition, int physicalPosition, RasterStop observation)
                throws SQLException;
    }
}
