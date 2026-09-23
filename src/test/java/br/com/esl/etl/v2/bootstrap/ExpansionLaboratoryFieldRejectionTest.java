package br.com.esl.etl.v2.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.modulos.contasapagar.aplicacao.ContaPagarDataExportMapper;
import br.com.esl.etl.v2.modulos.faturasporcliente.aplicacao.FaturaClienteDataExportMapper;
import br.com.esl.etl.v2.modulos.inventario.aplicacao.InventarioDataExportMapper;
import br.com.esl.etl.v2.modulos.sinistros.aplicacao.SinistroDataExportMapper;
import br.com.esl.etl.v2.plataforma.expansao.ExpansionObservation;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportTemplate;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.nio.file.Path;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

/** Every catalog field must independently veto an observation with an invalid wire shape. */
class ExpansionLaboratoryFieldRejectionTest {
    static Stream<Arguments> invalidFields() throws Exception {
        final var catalog =
                new ObjectMapper()
                        .readTree(
                                Path.of("docs/catalogos/macrobloco-expansao/campos-tipados.json")
                                        .toFile());
        assertEquals(4, catalog.path("specs").size());
        return StreamSupport.stream(catalog.path("specs").spliterator(), false)
                .flatMap(
                        spec ->
                                StreamSupport.stream(spec.path("fields").spliterator(), false)
                                        .map(
                                                field ->
                                                        Arguments.of(
                                                                DataExportTemplate.valueOf(
                                                                        spec.path("template")
                                                                                .asText()),
                                                                field.path("name").asText())));
    }

    @ParameterizedTest(name = "{0} rejects invalid {1}")
    @MethodSource("invalidFields")
    void aSingleInvalidFieldCannotBePromoted(
            final DataExportTemplate template, final String field) {
        final var envelope = JsonNodeFactory.instance.objectNode();
        final var data = envelope.putObject("data");
        assertTrue(map(template, 1, envelope).valid());
        data.putObject(field).put("invalid_shape", true);
        assertFalse(map(template, 1, envelope).valid(), field);
        data.putNull(field);
        assertTrue(
                map(template, 1, envelope).valid(), "NULL presence is distinct from invalid shape");
    }

    @ParameterizedTest
    @EnumSource(
            value = DataExportTemplate.class,
            names = {"CONTAS_A_PAGAR", "FATURAS_POR_CLIENTE", "INVENTARIO", "SINISTROS"})
    void occurrenceBoundsAreCheckedBeforeReadingTheEnvelope(final DataExportTemplate template) {
        for (final int occurrence : new int[] {-1, 0, 101}) {
            final var failure =
                    assertThrows(
                            IllegalArgumentException.class, () -> map(template, occurrence, null));
            assertEquals("EXP_OCCURRENCE_BOUND", failure.getMessage());
        }
        final var envelope = JsonNodeFactory.instance.objectNode();
        envelope.putObject("data");
        assertTrue(map(template, 100, envelope).valid());
    }

    @ParameterizedTest
    @EnumSource(
            value = DataExportTemplate.class,
            names = {"CONTAS_A_PAGAR", "FATURAS_POR_CLIENTE", "INVENTARIO", "SINISTROS"})
    void absentNullAndArrayDataAreEnvelopeErrors(final DataExportTemplate template) {
        final var envelope = JsonNodeFactory.instance.objectNode();
        assertThrows(IllegalArgumentException.class, () -> map(template, 1, envelope));
        envelope.putNull("data");
        assertThrows(IllegalArgumentException.class, () -> map(template, 1, envelope));
        envelope.putArray("data");
        assertThrows(IllegalArgumentException.class, () -> map(template, 1, envelope));
    }

    private static ExpansionObservation map(
            final DataExportTemplate template, final int occurrence, final JsonNode envelope) {
        return switch (template) {
            case CONTAS_A_PAGAR -> new ContaPagarDataExportMapper().map(occurrence, envelope);
            case FATURAS_POR_CLIENTE ->
                    new FaturaClienteDataExportMapper().map(occurrence, envelope);
            case INVENTARIO -> new InventarioDataExportMapper().map(occurrence, envelope);
            case SINISTROS -> new SinistroDataExportMapper().map(occurrence, envelope);
            default -> throw new IllegalArgumentException("EXP_TEST_TEMPLATE");
        };
    }
}
