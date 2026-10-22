package br.com.esl.etl.v2.contratos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ContractParityComparatorTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void joinsColetaIdentityBySequenceCodeBeforeComparingCanonicalIds() throws Exception {
        final ContractIdentityParityResult result =
                ContractParityComparator.compareColetas(
                        List.of(
                                row("{\"id\":900001,\"sequence_code\":700001}"),
                                row("{\"id\":900002,\"sequence_code\":700002}")),
                        List.of(
                                new ContractGraphQlColetaIdentity(
                                        Optional.of("900001"), Optional.of("700001"), List.of()),
                                new ContractGraphQlColetaIdentity(
                                        Optional.of("900002"), Optional.of("700002"), List.of())));

        assertTrue(result.hasNoCriticalDivergence());
        assertTrue(result.naturalKeySetsEqual());
        assertTrue(result.naturalKeyToGraphQlSourceIdOneToOne());
        assertTrue(result.sourceIdsEqual());
        assertFalse(result.toString().contains("900001"));
        assertFalse(result.toString().contains("700001"));
    }

    @Test
    void rejectsColetaParityWhenCanonicalIdsDifferAfterTheSequenceCodeJoin() throws Exception {
        final ContractIdentityParityResult result =
                ContractParityComparator.compareColetas(
                        List.of(row("{\"id\":900002,\"sequence_code\":700001}")),
                        List.of(
                                new ContractGraphQlColetaIdentity(
                                        Optional.of("900001"), Optional.of("700001"), List.of())));

        assertTrue(result.naturalKeySetsEqual());
        assertTrue(result.naturalKeyToGraphQlSourceIdOneToOne());
        assertTrue(result.sourceIdsComparable());
        assertFalse(result.sourceIdsEqual());
        assertFalse(result.hasNoCriticalDivergence());
        assertFalse(result.toString().contains("900001"));
        assertFalse(result.toString().contains("700001"));
    }

    @Test
    void comparesExpandedColetaRowsAsOneEntityPerDataExportId() throws Exception {
        final ContractIdentityParityResult result =
                ContractParityComparator.compareColetas(
                        List.of(
                                row("{\"id\":900001,\"sequence_code\":700001}"),
                                row("{\"id\":900001,\"sequence_code\":700001}"),
                                row("{\"id\":900002,\"sequence_code\":700002}")),
                        List.of(
                                new ContractGraphQlColetaIdentity(
                                        Optional.of("900001"), Optional.of("700001"), List.of()),
                                new ContractGraphQlColetaIdentity(
                                        Optional.of("900002"), Optional.of("700002"), List.of())));

        assertTrue(result.volumesEqual());
        assertTrue(result.dataExportNaturalKeysUnique());
        assertTrue(result.dataExportIdsUnique());
        assertTrue(result.hasNoCriticalDivergence());
    }

    @Test
    void leavesFreteIdentityOpenWhenTheMinutaIsDuplicatedOrAnIdIsMissing() throws Exception {
        final ContractIdentityParityResult result =
                ContractParityComparator.compareFretes(
                        List.of(
                                row("{\"id\":900002,\"corporation_sequence_number\":800001}"),
                                row("{\"id\":900003,\"corporation_sequence_number\":800001}")),
                        List.of(
                                new ContractGraphQlFreteIdentity(
                                        Optional.empty(),
                                        Optional.of("800001"),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty())));

        assertFalse(result.dataExportNaturalKeysUnique());
        assertFalse(result.sourceIdsComparable());
        assertFalse(result.hasNoCriticalDivergence());
    }

    @Test
    void detectsUnstableOrIncompletePaginationWithoutRetainingKeysInTheResult() throws Exception {
        final ContractPaginationResult result =
                ContractParityComparator.compareRepeatedPagination(
                        List.of(
                                observed(1, 1, row("{\"id\":900001,\"sequence_code\":700001}")),
                                observed(2, 1, row("{\"id\":900002,\"sequence_code\":700002}")),
                                observed(3, 1)),
                        List.of(
                                observed(1, 1, row("{\"id\":900002,\"sequence_code\":700002}")),
                                observed(2, 1, row("{\"id\":900001,\"sequence_code\":700001}")),
                                observed(3, 1)),
                        "sequence_code");

        assertTrue(result.firstRunHasTerminalEmptyPage());
        assertTrue(result.secondRunHasTerminalEmptyPage());
        assertTrue(result.firstRunHasNoIntermediateEmptyPage());
        assertTrue(result.firstRunPageNumbersConsecutive());
        assertTrue(result.firstRunEntityCountsWithinPageSize());
        assertFalse(result.hasAtLeastThreeNonEmptyPagesInBothRuns());
        assertTrue(result.keySetsStable());
        assertTrue(result.entityKeyAssociationsStable());
        assertFalse(result.orderedKeysStable());
        assertFalse(result.toString().contains("700001"));
    }

    @Test
    void rejectsDisjointIdsWithTheSameOrderedNaturalKeysAcrossPageSizes() throws Exception {
        final ContractPaginationResult result =
                ContractParityComparator.compareRepeatedPagination(
                        List.of(
                                observed(1, 1, row("{\"id\":900001,\"sequence_code\":700001}")),
                                observed(2, 1, row("{\"id\":900002,\"sequence_code\":700002}")),
                                observed(3, 1)),
                        List.of(
                                observed(
                                        1,
                                        2,
                                        row("{\"id\":910001,\"sequence_code\":700001}"),
                                        row("{\"id\":910002,\"sequence_code\":700002}")),
                                observed(2, 2)),
                        "sequence_code");

        assertTrue(result.entityCountsEqual());
        assertTrue(result.orderedKeysStable());
        assertTrue(result.keySetsStable());
        assertFalse(result.entityKeyAssociationsStable());
        assertFalse(result.hasInternallyConsistentTraversal());
    }

    @Test
    void rejectsPermutedIdAssociationsWithTheSameIdsAndOrderedNaturalKeys() throws Exception {
        final ContractPaginationResult result =
                ContractParityComparator.compareRepeatedPagination(
                        List.of(
                                observed(1, 1, row("{\"id\":900001,\"sequence_code\":700001}")),
                                observed(2, 1, row("{\"id\":900002,\"sequence_code\":700002}")),
                                observed(3, 1)),
                        List.of(
                                observed(
                                        1,
                                        2,
                                        row("{\"id\":900002,\"sequence_code\":700001}"),
                                        row("{\"id\":900001,\"sequence_code\":700002}")),
                                observed(2, 2)),
                        "sequence_code");

        assertTrue(result.entityCountsEqual());
        assertTrue(result.orderedKeysStable());
        assertTrue(result.keySetsStable());
        assertFalse(result.entityKeyAssociationsStable());
        assertFalse(result.hasInternallyConsistentTraversal());
    }

    @Test
    void preservesAssociationsAcrossPageSizesAndPhysicalExpansionWithoutExposingKeys()
            throws Exception {
        final JsonNode first = row("{\"id\":900001,\"sequence_code\":700001}");
        final JsonNode second = row("{\"id\":900002,\"sequence_code\":700002}");
        final ContractPaginationResult result =
                ContractParityComparator.compareRepeatedPagination(
                        List.of(
                                observed(1, 1, first, first, first),
                                observed(2, 1, second, second),
                                observed(3, 1)),
                        List.of(observed(1, 2, first, first, second), observed(2, 2)),
                        "sequence_code");

        assertEquals(5, result.firstRunRecordCount());
        assertEquals(3, result.secondRunRecordCount());
        assertTrue(result.entityKeyAssociationsStable());
        assertTrue(result.hasInternallyConsistentTraversal());
        final String evidence = objectMapper.writeValueAsString(result) + result;
        for (final String value : List.of("900001", "900002", "700001", "700002")) {
            assertFalse(evidence.contains(value));
        }
    }

    @Test
    void rejectsUnverifiableIdsOrNaturalKeysEvenWhenBothRunsRepeatThem() throws Exception {
        for (final String invalid :
                List.of(
                        "{\"id\":null,\"sequence_code\":700001}",
                        "{\"id\":{},\"sequence_code\":700001}",
                        "{\"id\":[],\"sequence_code\":700001}",
                        "{\"id\":900001,\"sequence_code\":null}",
                        "{\"id\":900001,\"sequence_code\":{}}",
                        "{\"id\":900001,\"sequence_code\":[]}")) {
            final List<ContractObservedPage> run =
                    List.of(observed(1, 1, row(invalid)), observed(2, 1));
            final ContractPaginationResult result =
                    ContractParityComparator.compareRepeatedPagination(run, run, "sequence_code");

            assertTrue(result.entityKeyAssociationsStable());
            assertFalse(result.hasInternallyConsistentTraversal());
        }
    }

    @Test
    void stableAssociationsDoNotExcuseAnEntityRepeatedAcrossPages() throws Exception {
        final JsonNode record = row("{\"id\":900001,\"sequence_code\":700001}");
        final List<ContractObservedPage> run =
                List.of(observed(1, 1, record), observed(2, 1, record), observed(3, 1));
        final ContractPaginationResult result =
                ContractParityComparator.compareRepeatedPagination(run, run, "sequence_code");

        assertTrue(result.entityKeyAssociationsStable());
        assertFalse(result.firstRunEntityIdsUniqueAcrossPages());
        assertFalse(result.hasInternallyConsistentTraversal());
    }

    @Test
    void stableAssociationsStillRequireAnObservedTerminalEmptyPage() throws Exception {
        final List<ContractObservedPage> run =
                List.of(observed(1, 1, row("{\"id\":900001,\"sequence_code\":700001}")));
        final ContractPaginationResult result =
                ContractParityComparator.compareRepeatedPagination(run, run, "sequence_code");

        assertTrue(result.entityKeyAssociationsStable());
        assertFalse(result.firstRunHasTerminalEmptyPage());
        assertFalse(result.hasInternallyConsistentTraversal());
    }

    @Test
    void rejectsAnIntermediateEmptyPageAndMoreEntitiesThanTheRequestedPageSize() throws Exception {
        final List<ContractObservedPage> invalidRun =
                List.of(
                        observed(1, 1, row("{\"id\":900001,\"sequence_code\":700001}")),
                        observed(2, 1),
                        observed(3, 1, row("{\"id\":900002,\"sequence_code\":700002}")),
                        observed(4, 1));
        final List<ContractObservedPage> expandedRun =
                List.of(
                        observed(
                                1,
                                1,
                                row("{\"id\":900001,\"sequence_code\":700001}"),
                                row("{\"id\":900001,\"sequence_code\":700001}")),
                        observed(2, 1));
        final List<ContractObservedPage> tooManyEntitiesRun =
                List.of(
                        observed(
                                1,
                                1,
                                row("{\"id\":900001,\"sequence_code\":700001}"),
                                row("{\"id\":900002,\"sequence_code\":700002}")),
                        observed(2, 1));

        final ContractPaginationResult intermediateEmpty =
                ContractParityComparator.compareRepeatedPagination(
                        invalidRun, invalidRun, "sequence_code");
        final ContractPaginationResult expanded =
                ContractParityComparator.compareRepeatedPagination(
                        expandedRun, expandedRun, "sequence_code");
        final ContractPaginationResult tooManyEntities =
                ContractParityComparator.compareRepeatedPagination(
                        tooManyEntitiesRun, tooManyEntitiesRun, "sequence_code");

        assertFalse(intermediateEmpty.firstRunHasNoIntermediateEmptyPage());
        assertFalse(intermediateEmpty.hasInternallyConsistentTraversal());
        assertTrue(expanded.firstRunEntityCountsWithinPageSize());
        assertTrue(expanded.hasInternallyConsistentTraversal());
        assertFalse(tooManyEntities.firstRunEntityCountsWithinPageSize());
        assertFalse(tooManyEntities.hasInternallyConsistentTraversal());
    }

    @Test
    void reportsWhenTheProvidedPopulatedWindowHasAtLeastThreeNonEmptyPages() throws Exception {
        final List<ContractObservedPage> populatedWindow =
                List.of(
                        observed(1, 1, row("{\"id\":900001,\"sequence_code\":700001}")),
                        observed(2, 1, row("{\"id\":900002,\"sequence_code\":700002}")),
                        observed(3, 1, row("{\"id\":900003,\"sequence_code\":700003}")),
                        observed(4, 1));

        final ContractPaginationResult result =
                ContractParityComparator.compareRepeatedPagination(
                        populatedWindow, populatedWindow, "sequence_code");

        assertTrue(result.hasAtLeastThreeNonEmptyPagesInBothRuns());
    }

    private static ContractObservedPage observed(
            final int pageNumber, final int pageSize, final JsonNode... records) {
        return new ContractObservedPage(pageNumber, pageSize, List.of(records));
    }

    private JsonNode row(final String value) throws Exception {
        return objectMapper.readTree(value);
    }
}
