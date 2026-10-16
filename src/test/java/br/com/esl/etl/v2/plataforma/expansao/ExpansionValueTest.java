package br.com.esl.etl.v2.plataforma.expansao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.modulos.contasapagar.domain.ContaPagarObservation;
import br.com.esl.etl.v2.modulos.faturasporcliente.domain.FaturaClienteObservation;
import br.com.esl.etl.v2.modulos.inventario.domain.InventarioObservation;
import br.com.esl.etl.v2.modulos.raster.domain.RasterRoute;
import br.com.esl.etl.v2.modulos.raster.domain.RasterTrip;
import br.com.esl.etl.v2.modulos.sinistros.domain.SinistroObservation;
import br.com.esl.etl.v2.plataforma.expansao.ExpansionValue.Presence;
import br.com.esl.etl.v2.plataforma.expansao.ExpansionValue.Wire;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.ExpansionFieldParser;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

/** EXP-PRESENCE-WIRE-052: a contradictory envelope must never become a valid observation. */
class ExpansionValueTest {
    static Stream<Arguments> coherentValues() {
        return Stream.of(
                Arguments.of(Presence.ABSENT, Wire.ABSENT, null, null),
                Arguments.of(Presence.NULL, Wire.NULL, null, null),
                Arguments.of(Presence.VALUE, Wire.STRING, "001", "001"),
                Arguments.of(Presence.VALUE, Wire.INTEGER, "1", "1"),
                Arguments.of(Presence.VALUE, Wire.NUMBER, "1.2500", new BigDecimal("1.2500")),
                Arguments.of(Presence.VALUE, Wire.BOOLEAN, "false", false),
                Arguments.of(
                        Presence.VALUE, Wire.ARRAY, "[\"B\",\"A\",\"B\"]", List.of("B", "A", "B")),
                Arguments.of(
                        Presence.VALUE,
                        Wire.OBJECT,
                        "{\"synthetic\":true}",
                        Map.of("synthetic", true)));
    }

    @ParameterizedTest
    @MethodSource("coherentValues")
    void coherentRepresentationsPreserveTheirOriginalFacts(
            final Presence presence, final Wire wire, final String raw, final Object parsed) {
        final var field = new ExpansionValue<>(presence, wire, raw, parsed, null);
        assertEquals(presence, field.presence());
        assertEquals(wire, field.wire());
        assertEquals(raw, field.raw());
        assertSame(parsed, field.value());
        assertTrue(field.valid());
    }

    @ParameterizedTest
    @EnumSource(value = Wire.class, names = "ABSENT", mode = EnumSource.Mode.EXCLUDE)
    void absenceCannotClaimAnObservedWireRepresentation(final Wire wire) {
        assertWireConflict(() -> new ExpansionValue<>(Presence.ABSENT, wire, null, null, null));
    }

    @ParameterizedTest
    @EnumSource(value = Wire.class, names = "NULL", mode = EnumSource.Mode.EXCLUDE)
    void explicitNullCannotClaimAbsenceOrAValueRepresentation(final Wire wire) {
        assertWireConflict(() -> new ExpansionValue<>(Presence.NULL, wire, null, null, null));
    }

    @ParameterizedTest
    @EnumSource(
            value = Wire.class,
            names = {"ABSENT", "NULL"})
    void parsedValueCannotBeHiddenBehindAnAbsentOrNullWire(final Wire wire) {
        assertWireConflict(
                () -> new ExpansionValue<>(Presence.VALUE, wire, "synthetic", "synthetic", null));
    }

    @Test
    void parserPreservesAbsenceNullNumericScaleAndFailedRawWithoutMutatingSource() {
        final var source =
                JsonNodeFactory.instance
                        .objectNode()
                        .putNull("null")
                        .put("numeric", new BigDecimal("1.2500"))
                        .put("textual", "1.2500");
        source.putObject("invalid").put("synthetic", true);
        final var before = source.deepCopy();
        final var absent = ExpansionFieldParser.decimal(source, "missing");
        final var nil = ExpansionFieldParser.decimal(source, "null");
        final var numeric = ExpansionFieldParser.decimal(source, "numeric");
        final var textual = ExpansionFieldParser.decimal(source, "textual");
        final var invalid = ExpansionFieldParser.decimal(source, "invalid");

        assertEquals(Presence.ABSENT, absent.presence());
        assertEquals(Wire.ABSENT, absent.wire());
        assertEquals(Presence.NULL, nil.presence());
        assertEquals(Wire.NULL, nil.wire());
        assertEquals(Wire.NUMBER, numeric.wire());
        assertEquals(Wire.STRING, textual.wire());
        assertEquals("1.2500", numeric.raw());
        assertEquals("1.2500", textual.raw());
        assertEquals(numeric.value(), textual.value());
        assertEquals(new BigDecimal("1.25000000"), numeric.value());
        assertFalse(invalid.valid());
        assertEquals(Presence.VALUE, invalid.presence());
        assertEquals(Wire.OBJECT, invalid.wire());
        assertEquals(source.get("invalid").toString(), invalid.raw());
        assertNull(invalid.value());
        assertEquals("INVALID_INVALID", invalid.issue());
        assertEquals(before, source);
    }

    static Stream<Class<?>> affectedConsumers() {
        return Stream.of(
                ContaPagarObservation.class,
                FaturaClienteObservation.class,
                InventarioObservation.class,
                SinistroObservation.class,
                RasterTrip.class,
                RasterRoute.class);
    }

    @ParameterizedTest
    @MethodSource("affectedConsumers")
    void anInvalidParsedFieldVetoesTheRealConsumerWithoutErasingRaw(final Class<?> type)
            throws Exception {
        final var source = JsonNodeFactory.instance.objectNode();
        source.putObject("field").put("synthetic", true);
        final var before = source.deepCopy();
        final var rejected = ExpansionFieldParser.text(source, "field");
        final var observation = observation(type, rejected);

        assertFalse(valid(observation));
        assertFalse(rejected.valid());
        assertEquals(Wire.OBJECT, rejected.wire());
        assertEquals(source.get("field").toString(), rejected.raw());
        assertEquals("INVALID_FIELD", rejected.issue());
        assertEquals(before, source);
    }

    @ParameterizedTest
    @MethodSource("affectedConsumers")
    void optionalAbsenceAndExplicitNullRemainDistinctForTheRealConsumer(final Class<?> type)
            throws Exception {
        final var source = JsonNodeFactory.instance.objectNode().putNull("field");
        final var absent = ExpansionFieldParser.text(source, "missing");
        final var nil = ExpansionFieldParser.text(source, "field");
        assertTrue(valid(observation(type, absent)));
        assertTrue(valid(observation(type, nil)));
        assertEquals(Presence.ABSENT, absent.presence());
        assertEquals(Presence.NULL, nil.presence());
        assertNull(absent.raw());
        assertNull(nil.raw());
    }

    @ParameterizedTest
    @MethodSource("affectedConsumers")
    void contradictoryFieldPropagatesBeforeDeliveryAndPreservesRetainedObservation(
            final Class<?> type) throws Exception {
        final var absent = new ExpansionValue<>(Presence.ABSENT, Wire.ABSENT, null, null, null);
        final var previous = observation(type, absent);
        final List<Object> retained = new ArrayList<>(List.of(previous));
        final var deliveries = new AtomicInteger();
        final Consumer<Object> sink =
                row -> {
                    deliveries.incrementAndGet();
                    retained.add(row);
                };

        assertWireConflict(
                () -> {
                    final var contradictory =
                            new ExpansionValue<>(Presence.ABSENT, Wire.STRING, null, null, null);
                    sink.accept(observation(type, contradictory));
                });
        assertEquals(0, deliveries.get());
        assertEquals(1, retained.size());
        assertSame(previous, retained.get(0));
        assertTrue(valid(previous));
    }

    @Test
    void rawLimitAndParseDispositionRemainStrictWithoutTruncationOrCoercion() {
        final String boundary = "x".repeat(4000);
        assertEquals(
                boundary,
                new ExpansionValue<>(Presence.VALUE, Wire.STRING, boundary, boundary, null).raw());
        assertEquals(
                "EXP_RAW_BOUND",
                assertThrows(
                                IllegalArgumentException.class,
                                () ->
                                        new ExpansionValue<>(
                                                Presence.VALUE,
                                                Wire.STRING,
                                                boundary + "x",
                                                boundary,
                                                null))
                        .getMessage());
        assertEquals(
                "EXP_PARSE_DISPOSITION_REQUIRED",
                assertThrows(
                                IllegalArgumentException.class,
                                () ->
                                        new ExpansionValue<>(
                                                Presence.VALUE, Wire.STRING, "raw", null, null))
                        .getMessage());
        assertEquals(
                "EXP_PARSE_DISPOSITION_REQUIRED",
                assertThrows(
                                IllegalArgumentException.class,
                                () ->
                                        new ExpansionValue<>(
                                                Presence.VALUE,
                                                Wire.STRING,
                                                "raw",
                                                "value",
                                                "INVALID_FIELD"))
                        .getMessage());
    }

    private static Object observation(final Class<?> type, final ExpansionValue<?> first)
            throws Exception {
        final RecordComponent[] components = type.getRecordComponents();
        final Class<?>[] types = new Class<?>[components.length];
        final Object[] values = new Object[components.length];
        for (int index = 0; index < components.length; index++) {
            types[index] = components[index].getType();
            assertEquals(ExpansionValue.class, types[index]);
            values[index] =
                    index == 0
                            ? first
                            : new ExpansionValue<>(Presence.ABSENT, Wire.ABSENT, null, null, null);
        }
        return type.getConstructor(types).newInstance(values);
    }

    private static boolean valid(final Object observation) throws Exception {
        return (boolean) observation.getClass().getMethod("valid").invoke(observation);
    }

    private static void assertWireConflict(final org.junit.jupiter.api.function.Executable action) {
        assertEquals(
                "EXP_WIRE_PRESENCE_CONFLICT",
                assertThrows(IllegalArgumentException.class, action).getMessage());
    }
}
