package br.com.esl.etl.v2.modulos.coletas.domain;

import static br.com.esl.etl.v2.modulos.coletas.domain.ColetaAttributePresence.ABSENT;
import static br.com.esl.etl.v2.modulos.coletas.domain.ColetaAttributePresence.NULL;
import static br.com.esl.etl.v2.modulos.coletas.domain.ColetaAttributePresence.VALUE;
import static br.com.esl.etl.v2.modulos.coletas.domain.ColetaObservedRootComparator.ExpectedProvenance.CAPTURED_6908_BOUNDED_TRAVERSAL;
import static br.com.esl.etl.v2.modulos.coletas.domain.ColetaObservedRootComparator.ExpectedProvenance.CAPTURED_6908_PAGE_BEFORE_MAPPER;
import static br.com.esl.etl.v2.modulos.coletas.domain.ColetaObservedRootComparator.ExpectedProvenance.INDEPENDENT_WINDOW_ORACLE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.modulos.coletas.domain.ColetaObservedRootComparator.Binding;
import br.com.esl.etl.v2.modulos.coletas.domain.ColetaObservedRootComparator.ExpectedRoot;
import br.com.esl.etl.v2.modulos.coletas.domain.ColetaObservedRootComparator.ObservedRow;
import br.com.esl.etl.v2.plataforma.identidade.FirstWaveIdentityContract;
import br.com.esl.etl.v2.plataforma.identidade.ScopedSourceIdentity;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ColetaObservedRootComparatorTest {

    private static final UUID COHORT = UUID.fromString("00000000-0000-0000-0000-000000000690");
    private static final Binding PAGE =
            new Binding("ESL", "tenant-a", LocalDate.of(2026, 9, 1), "a".repeat(64), COHORT, 1);
    private static final Binding TRAVERSAL =
            new Binding("ESL", "tenant-a", LocalDate.of(2026, 9, 1), "a".repeat(64), COHORT, 0);

    @Test
    void reportsBothSidesOfARealRootSetDifference() {
        final var result =
                ColetaObservedRootComparator.compare(
                        CAPTURED_6908_PAGE_BEFORE_MAPPER,
                        PAGE,
                        PAGE,
                        List.of(expected(10), expected(20)),
                        List.of(row(10, 1), row(30, 1)));

        assertEquals(2, result.expectedRoots());
        assertEquals(2, result.observedRoots());
        assertEquals(1, result.expectedOnlyRoots());
        assertEquals(1, result.observedOnlyRoots());
        assertFalse(result.observedRootSetsEqual());
        assertEquals("PARIDADE_DA_PAGINA_OBSERVADA", result.evidenceLabel());
    }

    @Test
    void physicalExpansionIsNotASecondRootAndRequiresItsOwnOracle() {
        final var result =
                ColetaObservedRootComparator.compare(
                        CAPTURED_6908_PAGE_BEFORE_MAPPER,
                        PAGE,
                        PAGE,
                        List.of(
                                new ExpectedRoot(id(10), OptionalInt.of(3), Map.of()),
                                expected(20)),
                        List.of(row(10, 0), row(10, 0), row(10, 0), row(20, 0)));

        assertEquals(2, result.observedRoots());
        assertEquals(4, result.observedPhysicalRows());
        assertEquals(2, result.repeatedRootRows());
        assertEquals(1, result.physicalOracleRoots());
        assertEquals(0, result.physicalRowCountMismatches());
        assertTrue(result.declaredObservationsMatch());
        assertFalse(result.sourceCompletenessProven());
        assertFalse(result.childCompletenessProven());
        assertFalse(result.terminalityProven());

        final var missingMultiplicityOracle =
                ColetaObservedRootComparator.compare(
                        INDEPENDENT_WINDOW_ORACLE,
                        PAGE,
                        PAGE,
                        List.of(expected(10)),
                        List.of(row(10, 1), row(10, 1), row(10, 1)));
        assertEquals(0, missingMultiplicityOracle.physicalOracleRoots());
        assertEquals("COMPARACAO_DE_RAIZES_OBSERVADAS", missingMultiplicityOracle.evidenceLabel());
        assertTrue(missingMultiplicityOracle.observedRootSetsEqual());
        assertFalse(missingMultiplicityOracle.sourceCompletenessProven());
        assertFalse(missingMultiplicityOracle.childCompletenessProven());
    }

    @Test
    void detectsDuplicateExpectedRootsPageOverlapAndWrongPhysicalCount() {
        final var result =
                ColetaObservedRootComparator.compare(
                        CAPTURED_6908_BOUNDED_TRAVERSAL,
                        TRAVERSAL,
                        TRAVERSAL,
                        List.of(
                                new ExpectedRoot(id(10), OptionalInt.of(3), Map.of(), Set.of(1, 2)),
                                new ExpectedRoot(id(10), OptionalInt.empty(), Map.of(), Set.of(1))),
                        List.of(row(10, 0), row(10, 0)));

        assertEquals(1, result.duplicateExpectedRoots());
        assertEquals(1, result.repeatedRootRows());
        assertEquals(1, result.rootsAcrossPages());
        assertEquals(1, result.physicalRowCountMismatches());
        assertFalse(result.observedRootSetsEqual());
        assertFalse(result.declaredObservationsMatch());
    }

    @Test
    void boundedTraversalDoesNotConfuseBatchesWithPagesAndFlagsSourceOverlap() throws Exception {
        final var expectedRoots =
                List.of(
                        new ExpectedRoot(id(10), OptionalInt.of(101), Map.of(), Set.of(1)),
                        new ExpectedRoot(id(20), OptionalInt.of(1), Map.of(), Set.of(2)));
        final List<ObservedRow> stagedRows = new ArrayList<>();
        for (int index = 0; index < 101; index++) {
            stagedRows.add(row(10, 0));
        }
        stagedRows.add(row(20, 0));
        final var disjoint =
                ColetaObservedRootComparator.compare(
                        CAPTURED_6908_BOUNDED_TRAVERSAL,
                        TRAVERSAL,
                        TRAVERSAL,
                        expectedRoots,
                        stagedRows);
        assertEquals("PARIDADE_DA_TRAVESSIA_LIMITADA_OBSERVADA", disjoint.evidenceLabel());
        assertEquals(2, disjoint.observedRoots());
        assertEquals(102, disjoint.observedPhysicalRows());
        assertEquals(100, disjoint.repeatedRootRows());
        assertEquals(2, disjoint.physicalOracleRoots());
        assertEquals(0, disjoint.physicalRowCountMismatches());
        assertEquals(0, disjoint.rootsAcrossPages());
        assertEquals(0, disjoint.presenceComparedCells());
        assertTrue(disjoint.declaredObservationsMatch());
        assertFalse(disjoint.terminalityProven());
        assertFalse(disjoint.sourceCompletenessProven());

        final List<ObservedRow> mappedRows = new ArrayList<>();
        for (int index = 0; index < 101; index++) {
            mappedRows.add(row(10, 1));
        }
        mappedRows.add(row(20, 2));
        final var mapped =
                ColetaObservedRootComparator.compare(
                        CAPTURED_6908_BOUNDED_TRAVERSAL,
                        TRAVERSAL,
                        TRAVERSAL,
                        expectedRoots,
                        mappedRows);
        assertEquals(0, mapped.rootsAcrossPages());
        assertEquals(0, mapped.pageSetMismatches());
        assertTrue(mapped.declaredObservationsMatch());

        final List<ObservedRow> batchMistakenForPage = new ArrayList<>(mappedRows);
        batchMistakenForPage.set(100, row(10, 2));
        final var wrongAttribution =
                ColetaObservedRootComparator.compare(
                        CAPTURED_6908_BOUNDED_TRAVERSAL,
                        TRAVERSAL,
                        TRAVERSAL,
                        expectedRoots,
                        batchMistakenForPage);
        assertEquals(1, wrongAttribution.rootsAcrossPages());
        assertEquals(1, wrongAttribution.pageSetMismatches());
        assertFalse(wrongAttribution.declaredObservationsMatch());

        final var overlappedSource =
                ColetaObservedRootComparator.compare(
                        CAPTURED_6908_BOUNDED_TRAVERSAL,
                        TRAVERSAL,
                        TRAVERSAL,
                        List.of(
                                new ExpectedRoot(
                                        id(10), OptionalInt.of(101), Map.of(), Set.of(1, 2)),
                                new ExpectedRoot(id(20), OptionalInt.of(1), Map.of(), Set.of(2))),
                        stagedRows);
        assertTrue(overlappedSource.observedRootSetsEqual());
        assertEquals(0, overlappedSource.physicalRowCountMismatches());
        assertEquals(1, overlappedSource.rootsAcrossPages());
        assertFalse(overlappedSource.declaredObservationsMatch());
        final String receipt = new ObjectMapper().writeValueAsString(overlappedSource);
        assertFalse(receipt.contains("INTEGER:10"));
        assertFalse(receipt.contains("tenant-a"));
        assertFalse(receipt.contains("observedRowsByRoot"));
    }

    @Test
    void mappedSourcePagesDetectOverlapAndRejectWrongOrPartialAttribution() {
        final var expected =
                List.of(new ExpectedRoot(id(10), OptionalInt.of(2), Map.of(), Set.of(1, 2)));
        final var matched =
                ColetaObservedRootComparator.compare(
                        CAPTURED_6908_BOUNDED_TRAVERSAL,
                        TRAVERSAL,
                        TRAVERSAL,
                        expected,
                        List.of(row(10, 1), row(10, 2)));
        assertEquals(1, matched.rootsAcrossPages());
        assertEquals(0, matched.pageSetMismatches());
        assertEquals(0, matched.physicalRowCountMismatches());
        assertFalse(matched.declaredObservationsMatch());

        final var wrongMap =
                ColetaObservedRootComparator.compare(
                        CAPTURED_6908_BOUNDED_TRAVERSAL,
                        TRAVERSAL,
                        TRAVERSAL,
                        List.of(new ExpectedRoot(id(10), OptionalInt.of(2), Map.of(), Set.of(1))),
                        List.of(row(10, 1), row(10, 2)));
        assertEquals(1, wrongMap.pageSetMismatches());
        assertFalse(wrongMap.declaredObservationsMatch());

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        ColetaObservedRootComparator.compare(
                                CAPTURED_6908_BOUNDED_TRAVERSAL,
                                TRAVERSAL,
                                TRAVERSAL,
                                expected,
                                List.of(row(10, 1), row(10, 0))));
    }

    @Test
    void capturedProvenanceRequiresItsDeclaredPageShape() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        ColetaObservedRootComparator.compare(
                                CAPTURED_6908_BOUNDED_TRAVERSAL, PAGE, PAGE, List.of(), List.of()));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        ColetaObservedRootComparator.compare(
                                CAPTURED_6908_BOUNDED_TRAVERSAL,
                                TRAVERSAL,
                                TRAVERSAL,
                                List.of(expected(10)),
                                List.of(row(10, 0))));
        final var unknownStagingPage =
                ColetaObservedRootComparator.compare(
                        CAPTURED_6908_BOUNDED_TRAVERSAL,
                        TRAVERSAL,
                        TRAVERSAL,
                        List.of(new ExpectedRoot(id(10), OptionalInt.of(1), Map.of(), Set.of(1))),
                        List.of(row(10, 0)));
        assertTrue(unknownStagingPage.declaredObservationsMatch());
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        ColetaObservedRootComparator.compare(
                                CAPTURED_6908_PAGE_BEFORE_MAPPER,
                                PAGE,
                                PAGE,
                                List.of(expected(10)),
                                List.of(row(10, 2))));
        final var empty =
                ColetaObservedRootComparator.compare(
                        CAPTURED_6908_BOUNDED_TRAVERSAL,
                        TRAVERSAL,
                        TRAVERSAL,
                        List.of(),
                        List.of());
        assertTrue(empty.observedRootSetsEqual());
        assertFalse(empty.terminalityProven());
        assertFalse(empty.sourceCompletenessProven());
        assertFalse(empty.childCompletenessProven());
    }

    @Test
    void distinguishesAbsentNullValueUnknownAndConflictingPresence() {
        final var result =
                ColetaObservedRootComparator.compare(
                        CAPTURED_6908_PAGE_BEFORE_MAPPER,
                        PAGE,
                        PAGE,
                        List.of(
                                new ExpectedRoot(
                                        id(10),
                                        OptionalInt.empty(),
                                        Map.of(
                                                "sequence_code",
                                                ABSENT,
                                                "status",
                                                NULL,
                                                "request_date",
                                                VALUE)),
                                new ExpectedRoot(
                                        id(20), OptionalInt.empty(), Map.of("sequence_code", NULL)),
                                new ExpectedRoot(
                                        id(30),
                                        OptionalInt.empty(),
                                        Map.of("sequence_code", VALUE))),
                        List.of(
                                new ObservedRow(
                                        id(10),
                                        1,
                                        Map.of(
                                                "sequence_code",
                                                NULL,
                                                "status",
                                                NULL,
                                                "request_date",
                                                VALUE)),
                                new ObservedRow(id(20), 1, Map.of()),
                                new ObservedRow(id(30), 1, Map.of("sequence_code", VALUE)),
                                new ObservedRow(id(30), 1, Map.of("sequence_code", ABSENT))));

        assertTrue(result.observedRootSetsEqual());
        assertEquals(3, result.presenceComparedCells());
        assertEquals(1, result.presenceMismatches());
        assertEquals(1, result.presenceMissingCells());
        assertEquals(1, result.presenceConflictingCells());
        assertFalse(result.declaredObservationsMatch());
    }

    @Test
    void requiresMatchingCallerBindingAndNeverTurnsEmptyEqualityIntoCompleteness()
            throws Exception {
        final var empty =
                ColetaObservedRootComparator.compare(
                        CAPTURED_6908_PAGE_BEFORE_MAPPER, PAGE, PAGE, List.of(), List.of());
        assertTrue(empty.observedRootSetsEqual());
        assertFalse(empty.terminalityProven());
        assertFalse(empty.sourceCompletenessProven());
        assertFalse(empty.childCompletenessProven());
        assertEquals("PARIDADE_DA_PAGINA_OBSERVADA", empty.evidenceLabel());

        final Binding differentDate =
                new Binding("ESL", "tenant-a", LocalDate.of(2026, 9, 2), "a".repeat(64), COHORT, 1);
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        ColetaObservedRootComparator.compare(
                                CAPTURED_6908_PAGE_BEFORE_MAPPER,
                                PAGE,
                                differentDate,
                                List.of(),
                                List.of()));
        final Binding differentCut =
                new Binding(
                        "ESL",
                        "tenant-a",
                        PAGE.requestDate(),
                        PAGE.contractFingerprint(),
                        UUID.randomUUID(),
                        1);
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        ColetaObservedRootComparator.compare(
                                CAPTURED_6908_PAGE_BEFORE_MAPPER,
                                PAGE,
                                differentCut,
                                List.of(),
                                List.of()));
        final Binding differentContract =
                new Binding("ESL", "tenant-a", PAGE.requestDate(), "b".repeat(64), COHORT, 1);
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        ColetaObservedRootComparator.compare(
                                CAPTURED_6908_PAGE_BEFORE_MAPPER,
                                PAGE,
                                differentContract,
                                List.of(),
                                List.of()));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        ColetaObservedRootComparator.compare(
                                CAPTURED_6908_PAGE_BEFORE_MAPPER,
                                new Binding(
                                        "ESL",
                                        "tenant-a",
                                        PAGE.requestDate(),
                                        PAGE.contractFingerprint(),
                                        COHORT,
                                        0),
                                PAGE,
                                List.of(),
                                List.of()));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        ColetaObservedRootComparator.compare(
                                CAPTURED_6908_PAGE_BEFORE_MAPPER,
                                PAGE,
                                PAGE,
                                List.of(expected(10)),
                                List.of(new ObservedRow(id("tenant-b", 10), 1, Map.of()))));

        final var nonEmpty =
                ColetaObservedRootComparator.compare(
                        CAPTURED_6908_PAGE_BEFORE_MAPPER,
                        PAGE,
                        PAGE,
                        List.of(expected(10)),
                        List.of(row(10, 1)));
        final String serialized = new ObjectMapper().writeValueAsString(nonEmpty);
        final var receipt = new ObjectMapper().readTree(serialized);
        assertFalse(receipt.has("observedRowsByRoot"));
        assertFalse(receipt.has("identity"));
        assertFalse(receipt.has("sourceInstance"));
        assertFalse(receipt.has("tenantScope"));
        assertFalse(serialized.contains("INTEGER:10"));
        assertFalse(serialized.contains("tenant-a"));
        assertFalse(nonEmpty.toString().contains("INTEGER:10"));
        assertFalse(PAGE.toString().contains("tenant-a"));
    }

    @Test
    void collectionAccessorsAreCopiedAndBoundedByThePilotContract() {
        final Map<String, ColetaAttributePresence> thirteen = new HashMap<>();
        for (final String name :
                List.of(
                        "id",
                        "sequence_code",
                        "status",
                        "status_updated_at",
                        "finish_date",
                        "service_date",
                        "request_date",
                        "cancellation_reason",
                        "manifesto",
                        "pick_item_id",
                        "fit_p_m_pck_sequence_code",
                        "frete",
                        "pck_mik_mft_sequence_code")) {
            thirteen.put(name, ABSENT);
        }
        final var expected =
                new ExpectedRoot(id(10), OptionalInt.of(4), thirteen, Set.of(1, 2, 3, 4));
        final var observed = new ObservedRow(id(10), 1, thirteen);
        assertEquals(13, expected.fieldPresence().size());
        assertEquals(4, expected.sourcePages().size());
        assertEquals(13, observed.fieldPresence().size());
        thirteen.clear();
        assertEquals(13, expected.fieldPresence().size());
        assertEquals(13, observed.fieldPresence().size());
        assertThrows(
                UnsupportedOperationException.class,
                () -> expected.fieldPresence().put("status", VALUE));
        assertThrows(UnsupportedOperationException.class, () -> expected.sourcePages().add(1));
        assertThrows(
                UnsupportedOperationException.class,
                () -> observed.fieldPresence().put("status", VALUE));

        for (int index = 0; index < 14; index++) {
            thirteen.put("field_" + index, ABSENT);
        }
        assertThrows(
                IllegalArgumentException.class,
                () -> new ExpectedRoot(id(10), OptionalInt.empty(), thirteen));
        assertThrows(IllegalArgumentException.class, () -> new ObservedRow(id(10), 1, thirteen));
        assertThrows(
                IllegalArgumentException.class,
                () -> new ExpectedRoot(id(10), OptionalInt.empty(), Map.of("alias", ABSENT)));
        assertThrows(
                IllegalArgumentException.class,
                () -> new ExpectedRoot(id(10), OptionalInt.of(5), Map.of(), Set.of(1, 2, 3, 4, 5)));
        assertThrows(IllegalArgumentException.class, () -> new ObservedRow(id(10), 5, Map.of()));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new Binding(
                                "ESL", "tenant-a", PAGE.requestDate(), "a".repeat(64), COHORT, 5));
    }

    private static ExpectedRoot expected(final int sourceId) {
        return new ExpectedRoot(id(sourceId), OptionalInt.empty(), Map.of());
    }

    private static ObservedRow row(final int sourceId, final int page) {
        return new ObservedRow(id(sourceId), page, Map.of());
    }

    private static ScopedSourceIdentity id(final int sourceId) {
        return id("tenant-a", sourceId);
    }

    private static ScopedSourceIdentity id(final String tenant, final int sourceId) {
        return new ScopedSourceIdentity(
                "ESL",
                tenant,
                FirstWaveIdentityContract.Entity.COLETAS,
                new ScopedSourceIdentity.SourceKey(
                        ScopedSourceIdentity.WireType.INTEGER, "INTEGER:" + sourceId));
    }
}
