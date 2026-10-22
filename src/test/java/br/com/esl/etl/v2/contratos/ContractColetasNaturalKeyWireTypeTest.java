package br.com.esl.etl.v2.contratos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.modulos.coletas.aplicacao.ColetaDataExportRecordMapper;
import br.com.esl.etl.v2.modulos.coletas.domain.ColetaAttributePresence;
import br.com.esl.etl.v2.modulos.coletas.domain.ColetaStageDisposition;
import br.com.esl.etl.v2.plataforma.contrato.ContractResponse;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportColetasContractCatalog;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportTemplate;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/**
 * ADR0043 lines 25-28 and the current Coletas release require INTEGER sequence_code when populated;
 * cross-protocol textual GraphQL identifiers do not authorize erasing a type change between two
 * Data Export observations. No HTTP, SQL, provider, external payload, or new source fixture is
 * used.
 */
class ContractColetasNaturalKeyWireTypeTest {
    @Test
    void rejectsAStringNaturalKeyInARepeatedTraversalOfTheIntegerContract() throws Exception {
        final List<JsonNode> integerRows = existingExpandedRows();
        final List<JsonNode> stringRows = textualNaturalKeys(integerRows);
        assertCurrentIntegerContract();
        final var first =
                List.of(
                        new ContractObservedPage(1, 1, integerRows.subList(0, 2)),
                        new ContractObservedPage(2, 1, integerRows.subList(2, 3)),
                        new ContractObservedPage(3, 1, integerRows.subList(3, 4)),
                        new ContractObservedPage(4, 1, List.of()));
        final var second = pages(3, stringRows);

        final var result =
                ContractParityComparator.compareRepeatedPagination(
                        first, second, DataExportTemplate.COLETAS, "sequence_code");

        assertEquals(4, result.firstRunRecordCount());
        assertEquals(4, result.secondRunRecordCount());
        assertEquals(3, result.firstRunEntityCount());
        assertEquals(3, result.secondRunEntityCount());
        assertEquals(3, result.firstRunNonEmptyPageCount());
        assertTrue(result.firstRunHasTerminalEmptyPage());
        assertTrue(result.secondRunHasTerminalEmptyPage());
        assertFalse(result.hasInternallyConsistentTraversal());
    }

    @Test
    void refusesTheDailyUnionBeforeReturningWhenNaturalKeyWireTypesDrift() throws Exception {
        final List<JsonNode> integerRows = existingExpandedRows();
        final List<JsonNode> stringRows = textualNaturalKeys(integerRows);
        assertCurrentIntegerContract();
        final var window =
                new ContractDateWindow(LocalDate.of(2026, 4, 13), LocalDate.of(2026, 4, 14));
        final var visited = new AtomicInteger();

        assertThrows(
                IllegalStateException.class,
                () ->
                        ContractRemoteEvidenceRunner.dataExportRowsForIdentity(
                                DataExportTemplate.COLETAS,
                                window,
                                pages(3, integerRows),
                                day -> {
                                    visited.incrementAndGet();
                                    return pages(
                                            3,
                                            day.startInclusive().getDayOfMonth() == 13
                                                    ? stringRows.subList(0, 3)
                                                    : stringRows.subList(3, 4));
                                }));
        assertEquals(2, visited.get());
    }

    @Test
    void keepsIntegerExpansionAndTheTextualGraphQlCrosswalk() throws Exception {
        final List<JsonNode> integerRows = existingExpandedRows();
        final List<JsonNode> longIntegerRows =
                integerRows.stream()
                        .map(
                                row -> {
                                    final ObjectNode changed = row.deepCopy();
                                    changed.put(
                                            "sequence_code", row.path("sequence_code").longValue());
                                    return (JsonNode) changed;
                                })
                        .toList();
        final var first =
                List.of(
                        new ContractObservedPage(1, 1, integerRows.subList(0, 2)),
                        new ContractObservedPage(2, 1, integerRows.subList(2, 3)),
                        new ContractObservedPage(3, 1, integerRows.subList(3, 4)),
                        new ContractObservedPage(4, 1, List.of()));
        final var result =
                ContractParityComparator.compareRepeatedPagination(
                        first,
                        pages(3, longIntegerRows),
                        DataExportTemplate.COLETAS,
                        "sequence_code");
        assertTrue(result.hasInternallyConsistentTraversal());
        assertEquals(4, result.firstRunRecordCount());
        assertEquals(3, result.firstRunEntityCount());
        ContractColetasWindowIdentityBridge.verify(integerRows, longIntegerRows);
        final var identity =
                ContractParityComparator.compareColetas(
                        integerRows,
                        List.of(
                                graphQl(integerRows.get(0)),
                                graphQl(integerRows.get(2)),
                                graphQl(integerRows.get(3))));
        assertTrue(identity.hasNoCriticalDivergence());
    }

    @Test
    void leavesGenericScalarPaginationAndFreightIdentityUnchanged() throws Exception {
        final List<JsonNode> stringRows = textualNaturalKeys(existingExpandedRows());
        final var generic =
                ContractParityComparator.compareRepeatedPagination(
                        pages(3, stringRows), pages(3, stringRows), "sequence_code");
        assertTrue(generic.hasInternallyConsistentTraversal());

        final JsonNode freight =
                new ObjectMapper()
                        .readTree("{\"id\":910001,\"corporation_sequence_number\":\"710001\"}");
        final var freightPagination =
                ContractParityComparator.compareRepeatedPagination(
                        pages(1, List.of(freight)),
                        pages(1, List.of(freight)),
                        DataExportTemplate.FRETES,
                        "corporation_sequence_number");
        assertTrue(freightPagination.hasInternallyConsistentTraversal());
        final var freightIdentity =
                ContractParityComparator.compareFretes(
                        List.of(freight),
                        List.of(
                                new ContractGraphQlFreteIdentity(
                                        Optional.of("910001"),
                                        Optional.of("710001"),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty())));
        assertTrue(freightIdentity.hasNoCriticalDivergence());
    }

    @Test
    void preservesANullableCaptureWithoutClaimingNaturalKeyIdentity() throws Exception {
        final var field =
                DataExportColetasContractCatalog.release()
                        .response()
                        .find("/sequence_code")
                        .orElseThrow();
        assertTrue(field.nullable());
        assertEquals(ContractResponse.Presence.OPTIONAL, field.presence());
        final ObjectNode row = existingExpandedRows().get(0).deepCopy();
        row.putNull("sequence_code");
        final var mapped = new ColetaDataExportRecordMapper().map(1, row);
        assertEquals(ColetaStageDisposition.VALID, mapped.disposition());
        assertEquals(ColetaAttributePresence.NULL, mapped.sequenceCodePresence());
        final var identity =
                ContractParityComparator.compareColetas(
                        List.of(row),
                        List.of(
                                new ContractGraphQlColetaIdentity(
                                        Optional.of(row.path("id").asText()),
                                        Optional.empty(),
                                        List.of())));
        assertFalse(identity.dataExportNaturalKeysComplete());
        assertFalse(identity.sourceIdsComparable());
        assertFalse(identity.hasNoCriticalDivergence());
        final var pagination =
                ContractParityComparator.compareRepeatedPagination(
                        pages(1, List.of(row)),
                        pages(1, List.of(row)),
                        DataExportTemplate.COLETAS,
                        "sequence_code");
        assertFalse(pagination.hasInternallyConsistentTraversal());
    }

    @Test
    void rejectsAnInvalidColetaWireTypeEvenWhenGraphQlHasTheSameText() throws Exception {
        final JsonNode integerRow = existingExpandedRows().get(0);
        final var identity =
                ContractParityComparator.compareColetas(
                        textualNaturalKeys(List.of(integerRow)), List.of(graphQl(integerRow)));
        assertTrue(identity.volumesEqual());
        assertFalse(identity.dataExportNaturalKeysComplete());
        assertFalse(identity.sourceIdsComparable());
        assertFalse(identity.hasNoCriticalDivergence());
    }

    @Test
    void rejectsATypeConflictInsideThePhysicalExpansionOfOneRoot() throws Exception {
        final List<JsonNode> integerRows = existingExpandedRows();
        final List<JsonNode> mixed =
                List.of(integerRows.get(0), textualNaturalKeys(integerRows).get(1));
        final var result =
                ContractParityComparator.compareRepeatedPagination(
                        pages(1, mixed),
                        pages(1, mixed),
                        DataExportTemplate.COLETAS,
                        "sequence_code");
        assertEquals(2, result.firstRunRecordCount());
        assertEquals(1, result.firstRunEntityCount());
        assertFalse(result.firstRunKeysComplete());
        assertFalse(result.hasInternallyConsistentTraversal());
        assertThrows(
                IllegalStateException.class,
                () -> ContractColetasWindowIdentityBridge.verify(mixed, mixed));
    }

    private static ContractGraphQlColetaIdentity graphQl(final JsonNode row) {
        return new ContractGraphQlColetaIdentity(
                Optional.of(row.path("id").asText()),
                Optional.of(row.path("sequence_code").asText()),
                List.of());
    }

    private static void assertCurrentIntegerContract() {
        assertEquals(
                List.of(ContractResponse.JsonType.INTEGER),
                DataExportColetasContractCatalog.release()
                        .response()
                        .find("/sequence_code")
                        .orElseThrow()
                        .jsonTypes());
    }

    private static List<JsonNode> existingExpandedRows() throws Exception {
        try (var input =
                Objects.requireNonNull(
                        ContractColetasNaturalKeyWireTypeTest.class.getResourceAsStream(
                                "/contracts/first-wave/6908-per-3-page-1.synthetic.json"))) {
            final var data = new ObjectMapper().readTree(input).path("data");
            final List<JsonNode> rows = new ArrayList<>();
            data.forEach(rows::add);
            assertEquals(4, rows.size());
            assertTrue(rows.stream().allMatch(row -> row.path("sequence_code").isIntegralNumber()));
            return List.copyOf(rows);
        }
    }

    private static List<JsonNode> textualNaturalKeys(final List<JsonNode> rows) {
        return rows.stream()
                .map(
                        row -> {
                            final ObjectNode changed = row.deepCopy();
                            changed.put("sequence_code", row.path("sequence_code").asText());
                            return (JsonNode) changed;
                        })
                .toList();
    }

    private static List<ContractObservedPage> pages(final int per, final List<JsonNode> rows) {
        return List.of(
                new ContractObservedPage(1, per, rows),
                new ContractObservedPage(2, per, List.of()));
    }
}
