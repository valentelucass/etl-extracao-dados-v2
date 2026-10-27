package br.com.esl.etl.v2.plataforma.fonte.raster;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.modulos.raster.domain.RasterStop;
import br.com.esl.etl.v2.modulos.raster.domain.RasterTripObservation;
import br.com.esl.etl.v2.plataforma.expansao.ExpansionValue;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Synthetic class-of-failure controls; no replay or retention of real355 observations. */
class RasterObservedCoordinateScale16Test {
    private static final String SIXTEEN = "-23.1234567891234567";

    private static final class Capture implements RasterResponseParser.Sink {
        RasterTripObservation trip;
        final List<RasterStop> stops = new ArrayList<>();
        final List<Integer> ordinals = new ArrayList<>();

        @Override
        public void trip(final int ordinal, final RasterTripObservation value) {
            trip = value;
        }

        @Override
        public void stop(final int parent, final int ordinal, final RasterStop value) {
            assertEquals(1, parent);
            stops.add(value);
            ordinals.add(ordinal);
        }
    }

    private static Capture parse(final String stop) throws Exception {
        return parseBody("{\"CodSolicitacao\":1,\"ColetasEntregas\":[" + stop + "]}");
    }

    private static Capture parseBody(final String body) throws Exception {
        final var c = new Capture();
        new RasterResponseParser(ZoneId.of("America/Sao_Paulo"))
                .parse(body.getBytes(StandardCharsets.UTF_8), c);
        return c;
    }

    private static RasterStop coordinates(final String latitude, final String longitude)
            throws Exception {
        return parse("{\"Latitude\":" + latitude + ",\"Longitude\":" + longitude + "}")
                .stops
                .get(0);
    }

    @Test
    void numericNineThroughSixteenFractionsAreExactForBothAxes() throws Exception {
        final String fraction = "1234567891234567";
        for (int scale = 9; scale <= 16; scale++) {
            final String literal = "-23." + fraction.substring(0, scale);
            final var stop = coordinates(literal, literal);
            for (var v : List.of(stop.latitude(), stop.longitude())) {
                assertTrue(v.valid());
                assertEquals(new BigDecimal(literal), v.value());
                assertEquals(scale, v.value().scale());
                assertEquals(ExpansionValue.Wire.NUMBER, v.wire());
            }
        }
    }

    @Test
    void sixteenthNonzeroDigitDoesNotRoundAway() throws Exception {
        assertEquals(new BigDecimal(SIXTEEN), coordinates(SIXTEEN, SIXTEEN).latitude().value());
        assertFalse(
                coordinates(SIXTEEN, SIXTEEN)
                        .latitude()
                        .value()
                        .equals(new BigDecimal("-23.12345679")));
    }

    @Test
    void legacyEightFractionTypedRepresentationIsUnchanged() throws Exception {
        assertEquals(
                new BigDecimal("-23.12000000"), coordinates("-23.12", "-23.12").latitude().value());
    }

    @Test
    void numericSeventeenthDigitIsRefusedWithoutTruncation() throws Exception {
        final var value = coordinates("-23.12345678912345678", "0").latitude();
        assertFalse(value.valid());
        assertNull(value.value());
        assertEquals("RAS_INVALID_FIELD", value.issue());
        assertTrue(value.raw().contains("12345678912345678"));
    }

    @Test
    void numericSeventeenthZeroIsAlsoOutsideTheFiniteObservedProfile() throws Exception {
        assertFalse(coordinates("-23.12345678912345670", "0").latitude().valid());
    }

    @Test
    void quotedNineFractionDoesNotReceiveAnInventedStringContract() throws Exception {
        assertFalse(coordinates("\"-23.123456789\"", "0").latitude().valid());
    }

    @Test
    void quotedEightFractionRetainsOldLexicalPolicy() throws Exception {
        final var v = coordinates("\"-23.12345678\"", "0").latitude();
        assertTrue(v.valid());
        assertEquals(ExpansionValue.Wire.STRING, v.wire());
        assertEquals(new BigDecimal("-23.12345678"), v.value());
    }

    @Test
    void commaPlusWhitespaceAndQuotedExponentStayInvalid() throws Exception {
        for (String literal : new String[] {"\"-23,1\"", "\"+23.1\"", "\" 23.1\"", "\"1e-9\""}) {
            assertFalse(coordinates(literal, "0").latitude().valid());
        }
    }

    @Test
    void integerWireRetainsItsTypeAndEightScaleCompatibility() throws Exception {
        final var v = coordinates("0", "0").latitude();
        assertEquals(ExpansionValue.Wire.INTEGER, v.wire());
        assertEquals(new BigDecimal("0.00000000"), v.value());
    }

    @Test
    void twentyIntegerDigitsPlusSixteenFractionDigitsFitProposedDecimal36Scale16()
            throws Exception {
        final String literal = "99999999999999999999.1234567891234567";
        final var v = coordinates(literal, literal).latitude();
        assertTrue(v.valid());
        assertEquals(36, v.value().precision());
        assertEquals(new BigDecimal(literal), v.value());
    }

    @Test
    void twentyOneIntegerDigitsStayInvalid() throws Exception {
        assertFalse(coordinates("999999999999999999999.1", "0").latitude().valid());
    }

    @Test
    void numericExponentWithinSixteenScaleIsPreservedExactly() throws Exception {
        assertEquals(
                new BigDecimal("0.0000000000000001"),
                coordinates("1e-16", "1e-16").latitude().value());
    }

    @Test
    void extremeNumericExponentCannotBypassFiniteBounds() throws Exception {
        assertFalse(coordinates("1e1000000", "0").latitude().valid());
        assertFalse(coordinates("1e-1000000", "0").latitude().valid());
    }

    @Test
    void noConventionalGeographicRangeIsInvented() throws Exception {
        assertTrue(coordinates("1000.123456789", "1000.123456789").latitude().valid());
    }

    @Test
    void otherSixDecimalFieldsKeepTheirEightScaleRule() throws Exception {
        final var c =
                parseBody(
                        "{\"CodSolicitacao\":1,\"PercentualAtraso\":1.123456789,\"VelocidadeMedia\":1.123456789,"
                                + "\"ColetasEntregas\":[{\"Latitude\":"
                                + SIXTEEN
                                + ",\"Longitude\":"
                                + SIXTEEN
                                + ","
                                + "\"KmPercorridoEntrega\":1.123456789,\"KmRestanteEntrega\":1.123456789,"
                                + "\"LatitudeUltimaPosicao\":1.123456789,\"LongitudeUltimaPosicao\":1.123456789}]}");
        final var s = c.stops.get(0);
        for (var v :
                List.of(
                        c.trip.trip().percentualAtraso(),
                        c.trip.trip().velocidadeMedia(),
                        s.kmPercorridoEntrega(),
                        s.kmRestanteEntrega(),
                        s.latitudeUltimaPosicao(),
                        s.longitudeUltimaPosicao())) {
            assertFalse(v.valid());
        }
        assertTrue(s.latitude().valid());
        assertTrue(s.longitude().valid());
        assertFalse(s.valid());
    }

    @Test
    void wrongWireRetainsRawAndIssueRatherThanSuccessfulNull() throws Exception {
        for (String literal : new String[] {"true", "[]", "{}"}) {
            final var v = coordinates(literal, "0").latitude();
            assertFalse(v.valid());
            assertNull(v.value());
            assertEquals(literal, v.raw());
        }
    }

    @Test
    void absentAndNullRemainDifferent() throws Exception {
        final var s = parse("{\"Longitude\":null}").stops.get(0);
        assertEquals(ExpansionValue.Presence.ABSENT, s.latitude().presence());
        assertEquals(ExpansionValue.Presence.NULL, s.longitude().presence());
    }

    @Test
    void conflictingAliasesRemainAWholePathRefusal() {
        assertThrows(
                IllegalArgumentException.class,
                () -> parse("{\"Latitude\":1.123456789,\"latitude\":2.123456789}"));
    }

    @Test
    void allFortyNineAttributesStillPassThroughTheOriginalMeasurement() throws Exception {
        final var observer = new RasterFieldObserver();
        new RasterResponseParser(ZoneId.of("America/Sao_Paulo"))
                .parse(full49().getBytes(StandardCharsets.UTF_8), observer);
        final var result = observer.close(true, 200);
        assertEquals(49, result.path("attributeCount").asInt());
        assertEquals(49, result.path("attributesWithValues").asInt());
        assertEquals(0, result.path("stopsWithParseIssue").asInt());
        assertEquals(
                1, result.path("attributes").path("stop.Latitude").path("parsedValues").asInt());
        assertFalse(result.path("canonicalIdentityProven").asBoolean(true));
    }

    @Test
    void threeHundredFiftyFiveRepeatedOrdersKeepEveryPhysicalOccurrence() throws Exception {
        final String stop =
                "{\"Ordem\":1,\"Latitude\":" + SIXTEEN + ",\"Longitude\":" + SIXTEEN + "}";
        final var c =
                parseBody(
                        "{\"CodSolicitacao\":1,\"ColetasEntregas\":["
                                + String.join(",", java.util.Collections.nCopies(355, stop))
                                + "]}");
        assertEquals(355, c.stops.size());
        assertEquals(355, c.ordinals.get(354));
        for (var s : c.stops) {
            assertTrue(s.latitude().valid());
            assertEquals("1", s.ordem().value());
        }
    }

    @Test
    void unrelatedInvalidFieldStillQuarantinesTheObservation() throws Exception {
        final var s =
                parse("{\"Ordem\":[],\"Latitude\":" + SIXTEEN + ",\"Longitude\":" + SIXTEEN + "}")
                        .stops
                        .get(0);
        assertTrue(s.latitude().valid());
        assertFalse(s.ordem().valid());
        assertFalse(s.valid());
    }

    private static String full49() {
        return "{\"CodSolicitacao\":1,\"Sequencial\":1,\"CodFilial\":1,\"StatusViagem\":\"SYNTHETIC\","
                + "\"PlacaVeiculo\":\"S\",\"PlacaCarreta1\":\"S\",\"PlacaCarreta2\":\"S\",\"PlacaCarreta3\":\"S\","
                + "\"CPFMotorista1\":\"S\",\"CPFMotorista2\":\"S\",\"CNPJClienteOrig\":\"S\",\"CNPJClienteDest\":\"S\","
                + "\"CodIBGECidadeOrig\":1,\"CodIBGECidadeDest\":1,\"DataHoraPrevIni\":\"2026-10-22T12:00:00Z\","
                + "\"DataHoraPrevFim\":\"2026-10-22T12:00:00Z\",\"DataHoraRealIni\":\"2026-10-22T12:00:00Z\","
                + "\"DataHoraRealFim\":\"2026-10-22T12:00:00Z\",\"DataHoraIdentificouFimViagem\":\"2026-10-22T12:00:00Z\","
                + "\"TempoTotalViagem\":1,\"DentroPrazo\":\"S\",\"PercentualAtraso\":1.0,\"RodouForaHorario\":\"S\","
                + "\"VelocidadeMedia\":1.0,\"EventosVelocidade\":1,\"DesviosDeRota\":1,\"LinkTimeLine\":\"S\","
                + "\"Rota\":{\"CodRota\":1,\"Descricao\":\"S\"},\"ColetasEntregas\":[{\"Ordem\":1,\"Tipo\":\"S\","
                + "\"CodIBGECidade\":1,\"CNPJCliente\":\"S\",\"CodigoCliente\":\"S\","
                + "\"DataHoraPrevChegada\":\"2026-10-22T12:00:00Z\",\"DataHoraPrevSaida\":\"2026-10-22T12:00:00Z\","
                + "\"DataHoraRealChegada\":\"2026-10-22T12:00:00Z\",\"DataHoraRealSaida\":\"2026-10-22T12:00:00Z\","
                + "\"Latitude\":"
                + SIXTEEN
                + ",\"Longitude\":"
                + SIXTEEN
                + ",\"DentroPrazo\":\"S\",\"DiferencaTempo\":\"S\","
                + "\"KmPercorridoEntrega\":1.0,\"KmRestanteEntrega\":1.0,\"ChegouNaEntrega\":\"S\","
                + "\"DataHoraUltimaPosicao\":\"2026-10-22T12:00:00Z\",\"LatitudeUltimaPosicao\":1.0,"
                + "\"LongitudeUltimaPosicao\":1.0,\"ReferenciaUltimaPosicao\":\"S\"}]}";
    }
}
