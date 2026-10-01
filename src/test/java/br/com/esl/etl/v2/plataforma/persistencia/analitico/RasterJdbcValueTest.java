package br.com.esl.etl.v2.plataforma.persistencia.analitico;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import br.com.esl.etl.v2.modulos.raster.domain.RasterTime;
import br.com.esl.etl.v2.plataforma.expansao.ExpansionValue;
import br.com.esl.etl.v2.plataforma.expansao.ExpansionValue.Presence;
import br.com.esl.etl.v2.plataforma.expansao.ExpansionValue.Wire;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RasterJdbcValueTest {
    @Test
    void bindsWirePresenceAndTypedValuesAtContiguousIndexes() throws Exception {
        final Map<Integer, Object> bound = new HashMap<>();
        final var statement = recording(bound);
        assertEquals(5, RasterJdbcValue.text(statement, 1, value(Wire.STRING, "x", "x")));
        assertEquals(9, RasterJdbcValue.count(statement, 5, value(Wire.INTEGER, "12", 12)));
        assertEquals(
                13,
                RasterJdbcValue.decimal(
                        statement, 9, value(Wire.NUMBER, "1.25", new BigDecimal("1.25"))));
        assertEquals("VALUE", bound.get(1));
        assertEquals("STRING", bound.get(2));
        assertEquals("x", bound.get(3));
        assertEquals("x", bound.get(4));
        assertEquals(12, bound.get(8));
        assertEquals(new BigDecimal("1.25"), bound.get(12));
    }

    @Test
    void preservesInstantNanosecondsOffsetAndSentinelDispositions() throws Exception {
        final Map<Integer, Object> bound = new HashMap<>();
        final var statement = recording(bound);
        final var instant = Instant.parse("2036-04-01T12:30:00.123456789Z");
        assertEquals(
                8,
                RasterJdbcValue.time(
                        statement,
                        1,
                        value(
                                Wire.STRING,
                                "synthetic-time",
                                new RasterTime(instant, 3600, false))));
        assertEquals(instant.getEpochSecond(), bound.get(4));
        assertEquals(123456789, bound.get(5));
        assertEquals(3600, bound.get(6));
        assertEquals(false, bound.get(7));

        assertEquals(
                15,
                RasterJdbcValue.time(
                        statement,
                        8,
                        value(Wire.STRING, "sentinel", new RasterTime(null, null, true))));
        assertNull(bound.get(11));
        assertNull(bound.get(12));
        assertNull(bound.get(13));
        assertEquals(true, bound.get(14));

        assertEquals(
                22,
                RasterJdbcValue.time(
                        statement,
                        15,
                        new ExpansionValue<>(Presence.NULL, Wire.NULL, null, null, null)));
        assertNull(bound.get(18));
        assertNull(bound.get(21));
    }

    private static <T> ExpansionValue<T> value(final Wire wire, final String raw, final T parsed) {
        return new ExpansionValue<>(Presence.VALUE, wire, raw, parsed, null);
    }

    private static PreparedStatement recording(final Map<Integer, Object> bound) {
        return (PreparedStatement)
                Proxy.newProxyInstance(
                        PreparedStatement.class.getClassLoader(),
                        new Class<?>[] {PreparedStatement.class},
                        (proxy, method, args) -> {
                            if (method.getName().startsWith("set")) {
                                bound.put((Integer) args[0], args[1]);
                            }
                            return null;
                        });
    }
}
