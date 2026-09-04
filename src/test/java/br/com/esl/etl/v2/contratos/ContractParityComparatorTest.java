package br.com.esl.etl.v2.contratos;

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
        assertFalse(result.orderedKeysStable());
        assertFalse(result.toString().contains("700001"));
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
