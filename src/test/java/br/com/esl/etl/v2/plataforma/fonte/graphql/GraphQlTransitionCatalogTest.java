package br.com.esl.etl.v2.plataforma.fonte.graphql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.plataforma.contrato.ApprovedGraphQlDocument;
import br.com.esl.etl.v2.plataforma.contrato.ContractClassification;
import br.com.esl.etl.v2.plataforma.resiliencia.EslWorkload;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class GraphQlTransitionCatalogTest {

    @Test
    void documentsAreNamedStaticReadOnlyAndUsersAreCappedAtTwenty() throws Exception {
        GraphQlTransitionalFieldCatalog.validate();

        assertEquals(20, GraphQlReadOperation.USERS_SNAPSHOT.maximumPageSize());
        assertEquals(EslWorkload.USUARIOS, GraphQlReadOperation.USERS_SNAPSHOT.workload());
        for (final GraphQlReadOperation operation : GraphQlReadOperation.values()) {
            assertEquals(
                    operation.approvedDocument(),
                    ApprovedGraphQlDocument.approve(operation.documentText()));
            assertTrue(operation.documentText().startsWith("query " + operation.operationName()));
            assertFalse(operation.documentText().contains("mutation"));
            assertFalse(operation.documentText().contains("__"));
            assertFalse(operation.documentText().contains("9901"));
            assertFalse(operation.documentText().contains("updatedAt"));
            assertFalse(operation.toString().contains("query "));
        }

        final GraphQlPageRequest valid =
                GraphQlPageRequest.initial(
                        GraphQlReadOperation.USERS_SNAPSHOT,
                        GraphQlQueryParameters.enabledUsers(),
                        20);
        assertEquals(20, valid.pageSize());
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        GraphQlPageRequest.initial(
                                GraphQlReadOperation.USERS_SNAPSHOT,
                                GraphQlQueryParameters.enabledUsers(),
                                0));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        GraphQlPageRequest.initial(
                                GraphQlReadOperation.USERS_SNAPSHOT,
                                GraphQlQueryParameters.enabledUsers(),
                                21));
    }

    @Test
    void ledgerMatchesExactlyFourFiveAndTenLeavesWithGovernedExit() {
        final Map<GraphQlReadOperation, Integer> counts = new EnumMap<>(GraphQlReadOperation.class);
        for (final GraphQlTransitionalField field : GraphQlTransitionalField.values()) {
            counts.merge(field.operation(), 1, Math::addExact);
            assertFalse(field.path().isBlank());
            assertFalse(field.ownerRole().isBlank());
            assertTrue(field.deadlineGate().startsWith("V2-"));
            assertFalse(field.exitCriteria().isBlank());
            assertEquals("V2-040", field.removalTask());
            assertTrue(field.publicationBlocked());
            assertEquals(ContractClassification.TRANSITIONAL, field.classification());
            if (field.operation() == GraphQlReadOperation.USERS_SNAPSHOT) {
                assertEquals(
                        GraphQlTransitionalField.PromotionPolicy.SHADOW_UPSERT_ONLY,
                        field.promotionPolicy());
                assertEquals(
                        GraphQlTransitionalField.EvidenceLevel.IMPLEMENTED_IN_SHADOW,
                        field.evidenceLevel());
            } else {
                assertEquals(
                        GraphQlTransitionalField.PromotionPolicy.OBSERVATION_ONLY,
                        field.promotionPolicy());
                assertEquals(
                        GraphQlTransitionalField.EvidenceLevel.SYNTHETIC_ONLY,
                        field.evidenceLevel());
            }
        }

        assertEquals(19, GraphQlTransitionalField.values().length);
        assertEquals(4, counts.get(GraphQlReadOperation.USERS_SNAPSHOT));
        assertTrue(
                java.util.Arrays.stream(GraphQlTransitionalField.values())
                        .filter(field -> field.operation() == GraphQlReadOperation.USERS_SNAPSHOT)
                        .allMatch(field -> "V2-033".equals(field.deadlineGate())));
        assertEquals(5, counts.get(GraphQlReadOperation.PICKS_TRANSITIONAL_SIDECAR));
        assertEquals(10, counts.get(GraphQlReadOperation.FREIGHTS_TRANSITIONAL_SIDECAR));
        assertTrue(
                java.util.Arrays.stream(GraphQlTransitionalField.values())
                        .noneMatch(field -> field.path().contains("updatedAt")));
    }

    @Test
    void versionedLedgerMatchesTheEnumAndExactlyTheNineteenPortabilityRows() throws IOException {
        final Map<String, List<String>> ledger =
                rowsById(
                        readBoundedCsv(Path.of("docs/catalogos/graphql-transitorio.csv"), 256_000));
        final Map<String, List<String>> portability =
                rowsById(
                        readBoundedCsv(
                                Path.of("docs/catalogos/portabilidade/matriz-campos.csv"),
                                16_000_000));

        assertEquals(19, ledger.size());
        int portabilityMatches = 0;
        for (final GraphQlTransitionalField field : GraphQlTransitionalField.values()) {
            final String matrixId = field.name().replace('_', '-');
            final List<String> ledgerRow = ledger.get(matrixId);
            assertEquals(11, ledgerRow.size());
            assertEquals(field.operation().name(), ledgerRow.get(1));
            assertEquals(field.path(), ledgerRow.get(2));
            assertEquals(field.purpose().name(), ledgerRow.get(3));
            assertEquals(field.ownerRole(), ledgerRow.get(4));
            assertEquals(field.deadlineGate(), ledgerRow.get(5));
            assertEquals(field.exitCriteria(), ledgerRow.get(6));
            assertEquals(field.removalTask(), ledgerRow.get(7));
            assertEquals(field.evidenceLevel().name(), ledgerRow.get(8));
            assertEquals(field.promotionPolicy().name(), ledgerRow.get(9));
            assertEquals("YES", ledgerRow.get(10));

            final List<String> portabilityRow = portability.get(matrixId);
            assertEquals(42, portabilityRow.size());
            assertEquals("GRAPHQL_SELECTION", portabilityRow.get(1));
            assertEquals(expectedTemplate(field.operation()), portabilityRow.get(5));
            assertEquals(field.path().substring(1).replace('/', '.'), portabilityRow.get(7));
            assertEquals(field.ownerRole(), portabilityRow.get(33));
            assertTrue(portabilityRow.get(36).contains("V2-024"));
            assertEquals("YES", portabilityRow.get(41));
            portabilityMatches++;
        }
        assertEquals(19, portabilityMatches);
    }

    @Test
    void typedParametersSerializeWithoutAnArbitraryMapOrCursorLeak() throws Exception {
        final GraphQlRequestJsonSerializer serializer =
                new GraphQlRequestJsonSerializer(GraphQlTestSupport.MAPPER);
        final GraphQlPageRequest users =
                GraphQlTestSupport.request(GraphQlReadOperation.USERS_SNAPSHOT);
        final JsonNode usersJson = GraphQlTestSupport.MAPPER.readTree(serializer.serialize(users));

        assertEquals("V2UsersSnapshot", usersJson.path("operationName").asText());
        assertTrue(usersJson.path("variables").path("params").path("enabled").asBoolean());
        assertEquals(20, usersJson.path("variables").path("first").asInt());
        assertTrue(usersJson.path("variables").path("after").isNull());
        assertFalse(users.toString().contains("enabled"));

        final GraphQlPageRequest next = users.next(GraphQlCursor.observed("synthetic-cursor"));
        final String serialized = serializer.serialize(next);
        assertTrue(serialized.contains("synthetic-cursor"));
        assertFalse(next.toString().contains("synthetic-cursor"));
        assertFalse(next.after().orElseThrow().toString().contains("synthetic-cursor"));

        final GraphQlPageRequest picks =
                GraphQlTestSupport.request(GraphQlReadOperation.PICKS_TRANSITIONAL_SIDECAR);
        final GraphQlPageRequest freights =
                GraphQlTestSupport.request(GraphQlReadOperation.FREIGHTS_TRANSITIONAL_SIDECAR);
        final JsonNode picksJson = GraphQlTestSupport.MAPPER.readTree(serializer.serialize(picks));
        final JsonNode freightsJson =
                GraphQlTestSupport.MAPPER.readTree(serializer.serialize(freights));
        assertEquals("2026-08-30", picksJson.at("/variables/params/requestDate").asText());
        assertEquals(
                "2026-08-29 - 2026-08-30", freightsJson.at("/variables/params/serviceAt").asText());
        assertNotEquals(
                picks.operation().approvedDocument(), freights.operation().approvedDocument());
    }

    @Test
    void parametersCannotCrossOperationsOrInvertWindows() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        GraphQlPageRequest.initial(
                                GraphQlReadOperation.USERS_SNAPSHOT,
                                GraphQlQueryParameters.picksForDate(LocalDate.of(2026, 8, 30)),
                                20));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        GraphQlQueryParameters.freightsForWindow(
                                LocalDate.of(2026, 8, 31), LocalDate.of(2026, 8, 30)));
        assertThrows(IllegalArgumentException.class, () -> GraphQlCursor.observed(" "));
        assertThrows(
                IllegalArgumentException.class,
                () -> GraphQlCursor.observed("x".repeat(GraphQlCursor.MAXIMUM_UTF8_BYTES + 1)));
        assertEquals(
                GraphQlCursor.observed("é".repeat(GraphQlCursor.MAXIMUM_UTF8_BYTES / 2)),
                GraphQlCursor.observed("é".repeat(GraphQlCursor.MAXIMUM_UTF8_BYTES / 2)));
        assertThrows(
                IllegalArgumentException.class,
                () -> GraphQlCursor.observed("é".repeat(GraphQlCursor.MAXIMUM_UTF8_BYTES / 2 + 1)));
        assertThrows(IllegalArgumentException.class, () -> GraphQlCursor.observed("x\u0000"));
        assertThrows(IllegalArgumentException.class, () -> GraphQlCursor.observed("\uD800"));
        assertEquals(GraphQlCursor.observed("opaque"), GraphQlCursor.observed("opaque"));
        assertNotEquals(GraphQlCursor.observed("opaque-a"), GraphQlCursor.observed("opaque-b"));
    }

    private static String expectedTemplate(final GraphQlReadOperation operation) {
        return switch (operation) {
            case USERS_SNAPSHOT -> "QUERY_USUARIOS_SISTEMA";
            case PICKS_TRANSITIONAL_SIDECAR -> "QUERY_COLETAS";
            case FREIGHTS_TRANSITIONAL_SIDECAR -> "QUERY_FRETES";
        };
    }

    private static List<List<String>> readBoundedCsv(final Path path, final long maximumBytes)
            throws IOException {
        assertTrue(Files.size(path) <= maximumBytes);
        final List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
        assertFalse(lines.isEmpty());
        return lines.stream()
                .skip(1)
                .filter(line -> line.startsWith("\"GQL-"))
                .map(GraphQlTransitionCatalogTest::parseCsvLine)
                .toList();
    }

    private static Map<String, List<String>> rowsById(final List<List<String>> rows) {
        final Map<String, List<String>> indexed = new HashMap<>();
        for (final List<String> row : rows) {
            if (row.get(0).startsWith("GQL-") && indexed.put(row.get(0), row) != null) {
                throw new AssertionError("ID GraphQL duplicado no catálogo.");
            }
        }
        return Map.copyOf(indexed);
    }

    private static List<String> parseCsvLine(final String line) {
        final List<String> fields = new ArrayList<>();
        int index = 0;
        while (index < line.length()) {
            if (line.charAt(index++) != '"') {
                throw new AssertionError("CSV fora do formato quoted esperado.");
            }
            final StringBuilder field = new StringBuilder();
            boolean closed = false;
            while (index < line.length()) {
                final char character = line.charAt(index++);
                if (character != '"') {
                    field.append(character);
                } else if (index < line.length() && line.charAt(index) == '"') {
                    field.append('"');
                    index++;
                } else {
                    closed = true;
                    break;
                }
            }
            if (!closed || index < line.length() && line.charAt(index++) != ',') {
                throw new AssertionError("CSV truncado ou com separador inválido.");
            }
            fields.add(field.toString());
        }
        return List.copyOf(fields);
    }
}
