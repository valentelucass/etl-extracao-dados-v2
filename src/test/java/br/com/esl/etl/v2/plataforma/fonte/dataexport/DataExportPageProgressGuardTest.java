package br.com.esl.etl.v2.plataforma.fonte.dataexport;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.DecimalNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class DataExportPageProgressGuardTest {

    private static final JsonNodeFactory NODES = JsonNodeFactory.instance;

    @Test
    void rejectsConsecutiveRepeatedPhysicalPagesWithASanitizedMessage() {
        final DataExportPageProgressGuard guard = new DataExportPageProgressGuard();
        final DataExportPageResponse page = page(record("private-id", "private-value"));
        guard.observe(page);

        final IllegalStateException failure =
                assertThrows(IllegalStateException.class, () -> guard.observe(page));

        assertEquals(
                "DATAEXPORT_PAGE_PROGRESS_NOT_OBSERVED: suspeita de repetição de página;"
                        + " o filtro probabilístico pode recusar uma página nova.",
                failure.getMessage());
    }

    @Test
    void rejectsAnEarlierPageAfterAnInterveningDifferentPage() {
        final DataExportPageProgressGuard guard = new DataExportPageProgressGuard();
        final DataExportPageResponse first = page(record("same-id", "A"));
        guard.observe(first);
        guard.observe(page(record("same-id", "B")));

        assertThrows(IllegalStateException.class, () -> guard.observe(first));
    }

    @Test
    void normalizesObjectPropertiesRecursively() {
        final ObjectNode first = record("1", "A");
        first.set("nested", NODES.objectNode().put("a", 1).put("b", 2));
        final ObjectNode reordered = NODES.objectNode();
        reordered.set("nested", NODES.objectNode().put("b", 2).put("a", 1));
        reordered.put("value", "A").put("id", "1");
        final DataExportPageProgressGuard guard = new DataExportPageProgressGuard();
        guard.observe(page(first));

        assertThrows(IllegalStateException.class, () -> guard.observe(page(reordered)));
    }

    @Test
    void allowsTheSameIdentifierWithDifferentPayload() {
        assertDifferent(page(record("1", "A")), page(record("1", "B")));
    }

    @Test
    void preservesPhysicalRowOrder() {
        final ObjectNode first = record("1", "A");
        final ObjectNode second = record("1", "B");

        assertDifferent(page(first, second), page(second, first));
    }

    @Test
    void preservesArrayOrder() {
        final ObjectNode first = record("1", "A");
        first.set("items", NODES.arrayNode().add(1).add(2));
        final ObjectNode second = record("1", "A");
        second.set("items", NODES.arrayNode().add(2).add(1));

        assertDifferent(page(first), page(second));
    }

    @Test
    void distinguishesAnAbsentPropertyFromAnExplicitNull() {
        assertDifferent(page(record("1", "A")), page(record("1", "A").putNull("note")));
    }

    @Test
    void distinguishesTextFromNumbers() {
        assertDifferent(
                page(NODES.objectNode().put("value", "1")),
                page(NODES.objectNode().put("value", 1)));
    }

    @Test
    void distinguishesDecimalFromInteger() {
        assertDifferent(
                page(NODES.objectNode().set("value", DecimalNode.valueOf(BigDecimal.ONE))),
                page(NODES.objectNode().put("value", 1)));
    }

    @Test
    void preservesDecimalScale() {
        assertDifferent(
                page(NODES.objectNode().set("value", DecimalNode.valueOf(new BigDecimal("1.0")))),
                page(NODES.objectNode().set("value", DecimalNode.valueOf(new BigDecimal("1.00")))));
    }

    @Test
    void preservesUnpairedUtf16CodeUnitsWithoutReplacementCollisions() {
        assertDifferent(page(record("1", "\uD800")), page(record("1", "\uD801")));
    }

    @Test
    void framesAdjacentStringsAndPropertyNamesByLength() {
        assertDifferent(
                page(NODES.objectNode().put("ab", "c")), page(NODES.objectNode().put("a", "bc")));
    }

    @Test
    void preservesExpandedRowsAndTheirMultiplicityWithoutDeduplicatingIds() {
        final ObjectNode first = record("1", "A");
        final ObjectNode second = record("1", "B");

        assertDifferent(page(first, second), page(first, second, second));
    }

    @Test
    void ignoresEmptyPagesRatherThanTreatingTerminalAsReplay() {
        final DataExportPageProgressGuard guard = new DataExportPageProgressGuard();
        guard.observe(page());
        guard.observe(page());

        assertDoesNotThrow(() -> guard.observe(page(record("1", "A"))));
    }

    private static void assertDifferent(
            final DataExportPageResponse first, final DataExportPageResponse second) {
        final DataExportPageProgressGuard guard = new DataExportPageProgressGuard();
        guard.observe(first);
        assertDoesNotThrow(() -> guard.observe(second));
    }

    private static ObjectNode record(final String id, final String value) {
        return NODES.objectNode().put("id", id).put("value", value);
    }

    private static DataExportPageResponse page(final JsonNode... records) {
        return new DataExportPageResponse(List.of(records));
    }
}
