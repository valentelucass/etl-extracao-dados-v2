package br.com.esl.etl.v2.plataforma.fonte.dataexport;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class DataExportPageEntityLimitValidatorTest {

    @Test
    void acceptsExpandedPhysicalRowsWhenTheirDistinctEntitiesFitTheRequestedPer() {
        final DataExportPageResponse response =
                new DataExportPageResponse(List.of(row("1"), row("1"), row("2")));

        assertDoesNotThrow(() -> DataExportPageEntityLimitValidator.validate(request(2), response));
        assertEquals(
                2, DataExportPageEntityLimitValidator.countDistinctEntities(request(2), response));
    }

    @Test
    void rejectsMoreDistinctEntitiesThanTheRequestedPer() {
        final DataExportPageResponse response =
                new DataExportPageResponse(List.of(row("1"), row("2"), row("3")));

        final IllegalStateException exception =
                assertThrows(
                        IllegalStateException.class,
                        () -> DataExportPageEntityLimitValidator.validate(request(2), response));

        assertEquals(
                "O template 6908 retornou 3 entidades distintas na página 1, acima do per solicitado de 2.",
                exception.getMessage());
    }

    @Test
    void rejectsMissingNullOrStructuredPaginationEntities() {
        final DataExportPageResponse missing =
                new DataExportPageResponse(List.of(JsonNodeFactory.instance.objectNode()));
        final DataExportPageResponse nullEntity =
                new DataExportPageResponse(
                        List.of(JsonNodeFactory.instance.objectNode().putNull("id")));
        final ObjectNode structured = JsonNodeFactory.instance.objectNode();
        structured.set("id", JsonNodeFactory.instance.objectNode().put("value", "1"));
        final DataExportPageResponse structuredEntity =
                new DataExportPageResponse(List.of(structured));

        for (final DataExportPageResponse response :
                List.of(missing, nullEntity, structuredEntity)) {
            final IllegalStateException exception =
                    assertThrows(
                            IllegalStateException.class,
                            () ->
                                    DataExportPageEntityLimitValidator.validate(
                                            request(2), response));
            assertEquals(
                    "O template 6908 retornou o campo de entidade de paginação obrigatório 'id' ausente, nulo ou não escalar na página 1.",
                    exception.getMessage());
        }
    }

    private static DataExportPageRequest request(final int pageSize) {
        return new DataExportPageRequest(
                DataExportTemplate.COLETAS,
                new BusinessDateRange(LocalDate.of(2026, 8, 24), LocalDate.of(2026, 8, 24)),
                Optional.empty(),
                1,
                pageSize,
                DataExportTemplate.COLETAS.defaultOrderBy());
    }

    private static ObjectNode row(final String id) {
        return JsonNodeFactory.instance.objectNode().put("id", id);
    }
}
