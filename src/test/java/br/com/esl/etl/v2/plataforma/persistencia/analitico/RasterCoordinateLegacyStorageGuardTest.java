package br.com.esl.etl.v2.plataforma.persistencia.analitico;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.plataforma.expansao.ExpansionValue;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Fail-closed before parameter mutation; no driver or SQL is invoked. */
class RasterCoordinateLegacyStorageGuardTest {
    private static PreparedStatement statement(final List<String> calls) {
        return (PreparedStatement)
                Proxy.newProxyInstance(
                        PreparedStatement.class.getClassLoader(),
                        new Class<?>[] {PreparedStatement.class},
                        (proxy, method, args) -> {
                            if (method.getName().startsWith("set")) {
                                calls.add(method.getName() + ":" + args[0] + ":" + args[1]);
                                return null;
                            }
                            throw new AssertionError(
                                    "NO_SQL_OR_OTHER_STATEMENT_OPERATION_EXPECTED");
                        });
    }

    private static ExpansionValue<BigDecimal> value(final String literal) {
        return new ExpansionValue<>(
                ExpansionValue.Presence.VALUE,
                ExpansionValue.Wire.NUMBER,
                literal,
                new BigDecimal(literal),
                null);
    }

    @Test
    void ninthFractionCannotSilentlyReachCurrentDecimal28Scale8() {
        final var calls = new ArrayList<String>();
        final var failure =
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                RasterJdbcValue.coordinateLegacyStorage(
                                        statement(calls), 1, value("-23.123456789")));
        assertEquals("RAS_COORDINATE_SCALE16_STORAGE_NOT_ADOPTED", failure.getMessage());
        assertTrue(calls.isEmpty());
    }

    @Test
    void sixteenthFractionAlsoFailsBeforeAnyStatementSetter() {
        final var calls = new ArrayList<String>();
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        RasterJdbcValue.coordinateLegacyStorage(
                                statement(calls), 1, value("-23.1234567891234567")));
        assertTrue(calls.isEmpty());
    }

    @Test
    void legacyEightFractionBindsUnchanged() throws Exception {
        final var calls = new ArrayList<String>();
        assertEquals(
                5,
                RasterJdbcValue.coordinateLegacyStorage(
                        statement(calls), 1, value("-23.12345678")));
        assertEquals(
                List.of(
                        "setString:1:VALUE",
                        "setString:2:NUMBER",
                        "setString:3:-23.12345678",
                        "setBigDecimal:4:-23.12345678"),
                calls);
    }

    @Test
    void absentNullAndInvalidRemainDistinctAndDoNotBecomeZero() throws Exception {
        for (var v :
                List.of(
                        new ExpansionValue<BigDecimal>(
                                ExpansionValue.Presence.ABSENT,
                                ExpansionValue.Wire.ABSENT,
                                null,
                                null,
                                null),
                        new ExpansionValue<BigDecimal>(
                                ExpansionValue.Presence.NULL,
                                ExpansionValue.Wire.NULL,
                                null,
                                null,
                                null),
                        new ExpansionValue<BigDecimal>(
                                ExpansionValue.Presence.VALUE,
                                ExpansionValue.Wire.BOOLEAN,
                                "true",
                                null,
                                "RAS_INVALID_FIELD"))) {
            final var calls = new ArrayList<String>();
            RasterJdbcValue.coordinateLegacyStorage(statement(calls), 1, v);
            assertEquals("setString:1:" + v.presence().name(), calls.get(0));
            assertEquals("setBigDecimal:4:null", calls.get(3));
        }
    }

    @Test
    void genericDecimalBinderStillHasOriginalBehaviorForOtherFields() throws Exception {
        final var direct = new ArrayList<String>();
        final var guarded = new ArrayList<String>();
        RasterJdbcValue.decimal(statement(direct), 1, value("1.25000000"));
        RasterJdbcValue.coordinateLegacyStorage(statement(guarded), 1, value("1.25000000"));
        assertEquals(direct, guarded);
    }
}
