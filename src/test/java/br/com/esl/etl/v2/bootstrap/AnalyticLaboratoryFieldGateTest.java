package br.com.esl.etl.v2.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.modulos.fretes.aplicacao.FreightAnalyticAttributesMapper;
import br.com.esl.etl.v2.modulos.raster.aplicacao.RasterMapper;
import br.com.esl.etl.v2.modulos.raster.aplicacao.RasterTripDto;
import br.com.esl.etl.v2.plataforma.analitico.AnalyticMaterializationRequest;
import br.com.esl.etl.v2.plataforma.analitico.AnalyticSqlCatalog.Column;
import br.com.esl.etl.v2.plataforma.controle.ExecutionMode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** Independent field inventory: a lone invalid leaf must block an otherwise absent snapshot. */
class AnalyticLaboratoryFieldGateTest {
    private static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");

    static Stream<String> freightFields() throws Exception {
        final var catalog =
                new ObjectMapper()
                        .readTree(
                                Path.of("docs/catalogos/macrobloco-analitico/frete-atributos.json")
                                        .toFile());
        final var fields = new ArrayList<String>(90);
        for (final var field : catalog.path("fields")) {
            if ("SYNTHETIC_ATTRIBUTE_SNAPSHOT".equals(field.path("channel").asText())) {
                fields.add(field.path("name").asText());
            }
        }
        assertEquals(90, fields.size());
        return fields.stream();
    }

    @ParameterizedTest
    @MethodSource("freightFields")
    void eachFreightLeafIndependentlyControlsValidity(final String field) {
        final var input =
                JsonNodeFactory.instance
                        .objectNode()
                        .put("provenance", "FIXTURE_SINTETICA_EXPLICITA")
                        .put("version", "synthetic-freight-attributes-v1");
        final var attributes = input.putObject("attributes");
        final var mapper = new FreightAnalyticAttributesMapper();
        assertTrue(mapper.map(input).valid());
        attributes.putNull(field);
        assertTrue(mapper.map(input).valid());
        attributes.putObject(field).put("unexpected", true);
        assertFalse(mapper.map(input).valid(), field);
    }

    static Stream<Arguments> rasterFields() throws Exception {
        final var catalog =
                new ObjectMapper()
                        .readTree(
                                Path.of("docs/catalogos/macrobloco-analitico/raster-campos.json")
                                        .toFile());
        final var fields = new ArrayList<Arguments>(49);
        for (final var field : catalog) {
            if ("TYPED_ATTRIBUTE".equals(field.path("classification").asText())) {
                fields.add(
                        Arguments.of(
                                field.path("entity").asText(),
                                field.path("aliases").get(0).asText()));
            }
        }
        assertEquals(49, fields.size());
        return fields.stream();
    }

    @ParameterizedTest
    @MethodSource("rasterFields")
    void eachRasterLeafIndependentlyControlsValidity(final String entity, final String field) {
        final var input = JsonNodeFactory.instance.objectNode();
        assertTrue(validRaster(entity, input));
        input.putNull(field);
        assertTrue(validRaster(entity, input));
        input.putObject(field).put("unexpected", true);
        assertFalse(validRaster(entity, input), field);
    }

    private static boolean validRaster(
            final String entity, final com.fasterxml.jackson.databind.JsonNode input) {
        return switch (entity) {
            case "TRIP" -> RasterMapper.trip(input, ZONE).valid();
            case "STOP" -> RasterMapper.stop(input, ZONE).valid();
            case "ROUTE" -> RasterMapper.route(input, ZONE).valid();
            default -> throw new IllegalArgumentException("UNEXPECTED_CATALOG_ENTITY");
        };
    }

    @Test
    void sourceDtoRejectsNullAndNonObjectBeforeMapping() {
        assertThrows(IllegalArgumentException.class, () -> new RasterTripDto(null));
        assertThrows(
                IllegalArgumentException.class,
                () -> new RasterTripDto(JsonNodeFactory.instance.arrayNode()));
        assertTrue(new RasterTripDto(JsonNodeFactory.instance.objectNode()).value().isObject());
    }

    @Test
    void materializationRequiresFiniteOrderedDatesAndVersionedIntent() {
        final var run = UUID.randomUUID();
        final var receipt = UUID.randomUUID();
        final var start = LocalDate.of(2036, 4, 1);
        final var end = start.plusDays(1);
        final var mode = ExecutionMode.BOOTSTRAP;
        assertThrows(
                IllegalArgumentException.class,
                () -> new AnalyticMaterializationRequest(null, receipt, 1, mode, true, start, end));
        assertThrows(
                IllegalArgumentException.class,
                () -> new AnalyticMaterializationRequest(run, null, 1, mode, true, start, end));
        for (final int revision : new int[] {0, 100001}) {
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            new AnalyticMaterializationRequest(
                                    run, receipt, revision, mode, true, start, end));
        }
        for (final var invalidMode : new ExecutionMode[] {null, ExecutionMode.SWEEP}) {
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            new AnalyticMaterializationRequest(
                                    run, receipt, 1, invalidMode, true, start, end));
        }
        assertThrows(
                IllegalArgumentException.class,
                () -> new AnalyticMaterializationRequest(run, receipt, 1, mode, true, null, end));
        for (final var invalidEnd :
                new LocalDate[] {null, start, start.minusDays(1), start.plusDays(3661)}) {
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            new AnalyticMaterializationRequest(
                                    run, receipt, 1, mode, true, start, invalidEnd));
        }
        assertEquals(
                start.plusDays(3660),
                new AnalyticMaterializationRequest(
                                run, receipt, 100000, mode, true, start, start.plusDays(3660))
                        .endExclusive());
    }

    @Test
    void metadataRejectsInvalidShapeInsteadOfWeakeningTypedReader() {
        for (final int ordinal : new int[] {0, 124}) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> new Column(ordinal, "x", "int", 10, 0, false));
        }
        for (final String name : new String[] {null, " ", "x".repeat(129)}) {
            assertThrows(
                    IllegalArgumentException.class, () -> new Column(1, name, "int", 10, 0, false));
        }
        assertThrows(IllegalArgumentException.class, () -> new Column(1, "x", "xml", 10, 0, false));
        assertThrows(
                IllegalArgumentException.class, () -> new Column(1, "x", "decimal", -1, 0, false));
        for (final int scale : new int[] {-1, 39}) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> new Column(1, "x", "decimal", 38, scale, false));
        }
        assertEquals(38, new Column(123, "x".repeat(128), "decimal", 38, 38, true).scale());
    }
}
