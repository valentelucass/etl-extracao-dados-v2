package br.com.esl.etl.v2.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.modulos.raster.domain.RasterStop;
import br.com.esl.etl.v2.modulos.raster.domain.RasterTripObservation;
import br.com.esl.etl.v2.plataforma.expansao.ExpansionValue;
import br.com.esl.etl.v2.plataforma.fonte.raster.RasterResponseParser;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class StatesRasterDecimalTest {
    @ParameterizedTest
    @ValueSource(
            strings = {
                "12345678.12345678",
                "99999999999999999999.99999999",
                "0.00000001",
                "-0.00000001",
                "0.00000000"
            })
    void numericWirePreservesExactDecimalAtTripAndStop(final String literal) throws Exception {
        final var values = parse(literal);
        for (final var value : values) {
            assertTrue(value.valid(), literal);
            assertEquals(new BigDecimal(literal).setScale(8), value.value(), literal);
            assertEquals(ExpansionValue.Wire.NUMBER, value.wire());
        }
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "1.0000000000000001",
                "123.123456789",
                "999999999999999999999.0",
                "1e1000000",
                "1e-1000000"
            })
    void forbiddenPrecisionCannotBecomeAValidRoundedNumber(final String literal) throws Exception {
        for (final var value : parse(literal)) {
            assertFalse(value.valid(), literal);
            assertEquals("RAS_INVALID_FIELD", value.issue());
        }
    }

    private static java.util.List<ExpansionValue<BigDecimal>> parse(final String literal)
            throws Exception {
        final var values = new java.util.ArrayList<ExpansionValue<BigDecimal>>();
        new RasterResponseParser(ZoneId.of("America/Sao_Paulo"))
                .parse(
                        ("{\"CodSolicitacao\":1,\"PercentualAtraso\":"
                                        + literal
                                        + ",\"ColetasEntregas\":[{\"KmPercorridoEntrega\":"
                                        + literal
                                        + "}]}")
                                .getBytes(StandardCharsets.UTF_8),
                        new RasterResponseParser.Sink() {
                            @Override
                            public void trip(
                                    final int position, final RasterTripObservation value) {
                                values.add(value.trip().percentualAtraso());
                            }

                            @Override
                            public void stop(
                                    final int trip, final int position, final RasterStop value) {
                                values.add(value.kmPercorridoEntrega());
                            }
                        });
        assertEquals(2, values.size());
        return values;
    }
}
