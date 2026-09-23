package br.com.esl.etl.v2.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.modulos.raster.domain.RasterStop;
import br.com.esl.etl.v2.modulos.raster.domain.RasterTripObservation;
import br.com.esl.etl.v2.plataforma.expansao.ExpansionValue.Presence;
import br.com.esl.etl.v2.plataforma.expansao.ExpansionValue.Wire;
import br.com.esl.etl.v2.plataforma.fonte.raster.RasterFieldParser;
import br.com.esl.etl.v2.plataforma.fonte.raster.RasterResponseParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class AnalyticLaboratoryRasterParserTest {
    private static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");
    private static final String TRIP =
            "{\"CodSolicitacao\":\"1\",\"Sequencial\":2,\"CodFilial\":3,\"ColetasEntregas\":[{\"Tipo\":\"Entrega\"}]}";

    @ParameterizedTest
    @ValueSource(
            strings = {
                "%s",
                "[%s]",
                "{\"Viagens\":%s}",
                "{\"result\":{\"Viagens\":[%s]}}",
                "{\"result\":[{\"Viagens\":%s}]}"
            })
    void envelopesKeepCandidateTypesAndNeverInventStopOrder(final String envelope)
            throws Exception {
        final var sink = new Collector();
        final var count =
                new RasterResponseParser(ZONE)
                        .parse(envelope.formatted(TRIP).getBytes(StandardCharsets.UTF_8), sink);
        assertEquals(1, count.trips());
        assertEquals(1, count.stops());
        assertEquals(Wire.STRING, sink.trip.trip().codSolicitacao().wire());
        assertEquals(Wire.INTEGER, sink.trip.trip().sequencial().wire());
        assertEquals("3", sink.trip.trip().codFilial().value());
        assertEquals(Presence.ABSENT, sink.stop.ordem().presence());
        assertNull(sink.stop.ordem().value());
        assertEquals(1, sink.stopPosition);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "{}",
                "null",
                "{\"result\":null}",
                "{\"Viagens\":null}",
                "{\"result\":[],\"Viagens\":[]}",
                "{\"error\":\"fixture-error\",\"Viagens\":[]}",
                "{\"result\":[{}]}",
                "{\"CodSolicitacao\":1,\"codSolicitacao\":2}",
                "{\"CodSolicitacao\":1,\"ColetasEntregas\":{}}",
                "{\"CodSolicitacao\":1,\"ColetasEntregas\":[null]}",
                "{\"CodSolicitacao\":1,\"Rota\":[]}"
            })
    void invalidEnvelopesAreNeverSuccessfulEmpty(final String input) {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new RasterResponseParser(ZONE)
                                .parse(input.getBytes(StandardCharsets.UTF_8), new Collector()));
    }

    @Test
    void triStateSentinelNanoAndDstAreExplicit() throws Exception {
        final var mapper = new ObjectMapper();
        assertEquals(
                Presence.ABSENT,
                RasterFieldParser.time(mapper.readTree("{}"), ZONE, "at").presence());
        assertEquals(
                Presence.NULL,
                RasterFieldParser.time(mapper.readTree("{\"at\":null}"), ZONE, "at").presence());
        final var sentinel =
                RasterFieldParser.time(
                        mapper.readTree("{\"at\":\"1900-01-01 00:00:00\"}"), ZONE, "at");
        assertTrue(sentinel.value().sentinel());
        assertNull(sentinel.value().instant());
        assertEquals("1900-01-01 00:00:00", sentinel.raw());
        assertFalse(
                RasterFieldParser.time(
                                mapper.readTree("{\"at\":\"1900-01-01garbage\"}"), ZONE, "at")
                        .valid());
        assertFalse(
                RasterFieldParser.time(
                                mapper.readTree("{\"at\":\"1900-01-01T99:00:00\"}"), ZONE, "at")
                        .valid());
        final var precise =
                RasterFieldParser.time(
                        mapper.readTree("{\"at\":\"2036-04-01T10:00:00.123456789-03:00\"}"),
                        ZONE,
                        "at");
        assertEquals(123456789, precise.value().instant().getNano());
        assertEquals(-10800, precise.value().offsetSeconds());
        assertFalse(
                RasterFieldParser.time(
                                mapper.readTree("{\"at\":\"2018-11-04T00:30:00\"}"), ZONE, "at")
                        .valid());
        assertFalse(
                RasterFieldParser.time(
                                mapper.readTree("{\"at\":\"2019-02-16T23:30:00\"}"), ZONE, "at")
                        .valid());
        assertFalse(
                RasterFieldParser.time(mapper.readTree("{\"at\":\"invalid\"}"), ZONE, "at")
                        .valid());
    }

    @Test
    void malformedUtf8DuplicateKeysAndTrailingJsonFail() {
        final var parser = new RasterResponseParser(ZONE);
        assertThrows(
                java.io.IOException.class,
                () -> parser.parse(new byte[] {(byte) 0xc3, 0x28}, new Collector()));
        assertThrows(
                java.io.IOException.class,
                () ->
                        parser.parse(
                                "{\"CodSolicitacao\":1,\"CodSolicitacao\":2}"
                                        .getBytes(StandardCharsets.UTF_8),
                                new Collector()));
        assertThrows(
                IllegalArgumentException.class,
                () -> parser.parse("[] []".getBytes(StandardCharsets.UTF_8), new Collector()));
    }

    private static final class Collector implements RasterResponseParser.Sink {
        private RasterTripObservation trip;
        private RasterStop stop;
        private int stopPosition;

        @Override
        public void trip(final int position, final RasterTripObservation observation) {
            trip = observation;
        }

        @Override
        public void stop(final int tripPosition, final int position, final RasterStop observation) {
            stop = observation;
            stopPosition = position;
        }
    }
}
