package br.com.esl.etl.v2.plataforma.fonte.dataexport;

import static br.com.esl.etl.v2.plataforma.fonte.dataexport.ObservedExpansionCoreFixtures.plan;
import static br.com.esl.etl.v2.plataforma.fonte.dataexport.ObservedExpansionCoreFixtures.raw;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.plataforma.expansao.ExpansionCaptured;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import org.junit.jupiter.api.Test;

final class ObservedExpansionConstructorBoundsTest {
    @Test
    void fiftyFourFieldsAreRejectedBeforeCopyIteratorStreamOrElementAccess() {
        final var sample = sample(ObservedExpansionCaptureTemplate.FAT);
        assertThrows(
                IllegalArgumentException.class,
                () -> new ObservedExpansionProjectedRecord(1, sample.captured(), poison(54)));
    }

    @Test
    void eachTypeEnforcesItsExactFieldCountBeforeElementAccess() {
        final int[] counts = {28, 53, 26, 44};
        int index = 0;
        for (final var template : ObservedExpansionCaptureTemplate.values()) {
            final var sample = sample(template);
            assertEquals(counts[index], sample.fields().size());
            final int tooMany = counts[index++] + 1;
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            new ObservedExpansionProjectedRecord(
                                    1, sample.captured(), poison(tooMany)));
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            new ObservedExpansionProjectedRecord(
                                    1, sample.captured(), poison(sample.fields().size() - 1)));
        }
    }

    @Test
    void hardOccurrence2001IsRejectedBeforeFieldCopy() {
        final var sample = sample(ObservedExpansionCaptureTemplate.FAT);
        final var tooFar = new ExpansionCaptured<>(2001, sample.captured().observation(), null);
        assertThrows(
                IllegalArgumentException.class,
                () -> new ObservedExpansionProjectedRecord(1, tooFar, poison(53)));
    }

    @Test
    void fieldListCannotReplaceBoundedCopyWithItsOwnToArrayOrIterator() {
        final var sample = sample(ObservedExpansionCaptureTemplate.FAT);
        final var caller = indexedOnly(sample.fields());
        final var result = new ObservedExpansionProjectedRecord(1, sample.captured(), caller);
        assertEquals(53, result.fields().size());
        assertThrows(UnsupportedOperationException.class, () -> result.fields().clear());
    }

    @Test
    void duplicateFieldPathIsRejectedWithinTheBoundedSnapshot() {
        final var sample = sample(ObservedExpansionCaptureTemplate.FAT);
        final var fields = new ArrayList<>(sample.fields());
        fields.set(1, fields.get(0));
        assertThrows(
                IllegalArgumentException.class,
                () -> new ObservedExpansionProjectedRecord(1, sample.captured(), fields));
    }

    @Test
    void oneThousandAndOneRecordsAreRejectedBeforeElementAccess() {
        final var plan = plan(ObservedExpansionCaptureTemplate.CAP);
        assertThrows(IllegalArgumentException.class, () -> page(raw(plan, 1, "[]"), poison(1001)));
    }

    @Test
    void smallerPlanPageLimitIsCheckedBeforeAnyCopyOrStream() {
        final var plan = boundedPlan(2, 4);
        assertThrows(IllegalArgumentException.class, () -> page(raw(plan, 1, "[]"), poison(3)));
    }

    @Test
    void customExecutionLimitAppliesToGlobalOccurrencesNotOnlyPageSize() {
        final var plan = boundedPlan(2, 3);
        final var sample = sample(plan.template());
        final var records = List.of(at(sample, 1, 3), at(sample, 2, 4));
        assertThrows(IllegalArgumentException.class, () -> page(raw(plan, 2, "[{},{}]"), records));
    }

    @Test
    void duplicateOccurrenceDuplicateOrdinalGapAndReverseOrderAreRejected() {
        final var plan = plan(ObservedExpansionCaptureTemplate.CAP);
        final var sample = sample(plan.template());
        final var invalid =
                List.of(
                        List.of(at(sample, 1, 1), at(sample, 2, 1)),
                        List.of(at(sample, 1, 1), at(sample, 1, 2)),
                        List.of(at(sample, 1, 1), at(sample, 2, 3)),
                        List.of(at(sample, 2, 2), at(sample, 1, 1)));
        for (final var records : invalid) {
            assertThrows(
                    IllegalArgumentException.class, () -> page(raw(plan, 1, "[{},{}]"), records));
        }
    }

    @Test
    void firstPageStartsAtOneAndLaterPageCannotReuseAnEarlierOccurrence() {
        final var plan = plan(ObservedExpansionCaptureTemplate.CAP);
        final var sample = sample(plan.template());
        assertThrows(
                IllegalArgumentException.class,
                () -> page(raw(plan, 1, "[{}]"), List.of(at(sample, 1, 2))));
        assertThrows(
                IllegalArgumentException.class,
                () -> page(raw(plan, 3, "[{}]"), List.of(at(sample, 1, 2))));
    }

    @Test
    void pageStartCannotExceedTheMaximumCapacityOfEarlierPages() {
        final var plan = boundedPlan(2, 6);
        final var sample = sample(plan.template());
        assertThrows(
                IllegalArgumentException.class,
                () -> page(raw(plan, 2, "[{}]"), List.of(at(sample, 1, 4))));
        final var valid =
                page(raw(plan, 2, "[{},{}]"), List.of(at(sample, 1, 3), at(sample, 2, 4)));
        assertEquals(4L, valid.lastOccurrence().orElseThrow());
    }

    @Test
    void anotherTemplateRecordCannotEnterThePageContext() {
        final var plan = plan(ObservedExpansionCaptureTemplate.CAP);
        final var fat = sample(ObservedExpansionCaptureTemplate.FAT);
        assertThrows(
                IllegalArgumentException.class, () -> page(raw(plan, 1, "[{}]"), List.of(fat)));
        final List<ObservedExpansionProjectedRecord> withNull =
                java.util.Arrays.asList((ObservedExpansionProjectedRecord) null);
        assertThrows(NullPointerException.class, () -> page(raw(plan, 1, "[{}]"), withNull));
    }

    @Test
    void identicalPayloadWithDistinctPhysicalAddressesRemainsTwoRowsAndImmutable() {
        final var plan = plan(ObservedExpansionCaptureTemplate.CAP);
        final var sample = sample(plan.template());
        final var source = new ArrayList<>(List.of(at(sample, 1, 1), at(sample, 2, 2)));
        final var result = page(raw(plan, 1, "[{},{}]"), source);
        source.clear();
        assertEquals(2, result.physicalRows().orElseThrow());
        assertEquals(result.records().get(0).fields(), result.records().get(1).fields());
        assertThrows(UnsupportedOperationException.class, () -> result.records().clear());
    }

    @Test
    void pageCopyUsesOnlyBoundedIndexedElementsNotCallerToArrayOrIterator() {
        final var plan = plan(ObservedExpansionCaptureTemplate.CAP);
        final var sample = sample(plan.template());
        final var caller = indexedOnly(List.of(at(sample, 1, 1), at(sample, 2, 2)));
        final var result = page(raw(plan, 1, "[{},{}]"), caller);
        assertEquals(2, result.records().size());
    }

    @Test
    void globalOccurrence2000IsValidWhenThirdPageAndPlanContextAllowIt() {
        final var plan = plan(ObservedExpansionCaptureTemplate.CAP);
        final var sample = sample(plan.template());
        final var result =
                page(raw(plan, 3, "[{},{}]"), List.of(at(sample, 1, 1999), at(sample, 2, 2000)));
        assertEquals(2000L, result.lastOccurrence().orElseThrow());
        assertEquals("NOT_PERFORMED_BY_CORE", result.sourceSchemaValidation());
    }

    @Test
    void structureIssueRefusesAnyRecordsBeforeTouchingThemAndKeepsUnknownCount() {
        final var plan = plan(ObservedExpansionCaptureTemplate.CAP);
        final var raw = raw(plan, 1, "[");
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new ObservedExpansionPageProjection(
                                raw,
                                ObservedExpansionPageProjection.Status.STRUCTURE_ISSUE,
                                ObservedExpansionPageProjection.Issue.INVALID_JSON,
                                poison(1),
                                "{}"));
        final var empty =
                new ObservedExpansionPageProjection(
                        raw,
                        ObservedExpansionPageProjection.Status.STRUCTURE_ISSUE,
                        ObservedExpansionPageProjection.Issue.INVALID_JSON,
                        List.of(),
                        "{}");
        assertTrue(empty.physicalRows().isEmpty());
    }

    private static ObservedExpansionProjectedRecord sample(
            final ObservedExpansionCaptureTemplate template) {
        final var plan = plan(template);
        return new ObservedExpansionPageProjector(plan)
                .projectNext(raw(plan, 1, "[{}]"))
                .records()
                .get(0);
    }

    private static ObservedExpansionProjectedRecord at(
            final ObservedExpansionProjectedRecord sample,
            final int ordinal,
            final long occurrence) {
        return new ObservedExpansionProjectedRecord(
                ordinal,
                new ExpansionCaptured<>(occurrence, sample.captured().observation(), null),
                sample.fields());
    }

    private static ObservedExpansionPageProjection page(
            final ObservedExpansionRawPage raw,
            final List<ObservedExpansionProjectedRecord> records) {
        return new ObservedExpansionPageProjection(
                raw, ObservedExpansionPageProjection.Status.MAPPED, null, records, "{}");
    }

    private static ObservedExpansionCapturePlan boundedPlan(
            final int pageRows, final int executionRows) {
        final var original = plan(ObservedExpansionCaptureTemplate.CAP);
        final var limits =
                new ObservedExpansionCaptureLimits(
                        4096, 65536, 16384, 262144, pageRows, executionRows, 4, 1000);
        return new ObservedExpansionCapturePlan(
                original.captureExecutionId(),
                original.template(),
                original.sourceInstance(),
                original.tenantScope(),
                original.businessWindow(),
                original.sourceZone(),
                original.transport(),
                original.orderBy(),
                original.envelope(),
                original.per(),
                limits);
    }

    private static <T> List<T> poison(final int size) {
        return new AbstractList<>() {
            @Override
            public int size() {
                return size;
            }

            @Override
            public T get(final int index) {
                throw new AssertionError("Element accessed before size rejection");
            }

            @Override
            public Object[] toArray() {
                throw new AssertionError("Unbounded caller copy attempted");
            }

            @Override
            public Iterator<T> iterator() {
                throw new AssertionError("Unbounded caller iteration attempted");
            }
        };
    }

    private static <T> List<T> indexedOnly(final List<T> bounded) {
        return new AbstractList<>() {
            @Override
            public int size() {
                return bounded.size();
            }

            @Override
            public T get(final int index) {
                return bounded.get(index);
            }

            @Override
            public Object[] toArray() {
                throw new AssertionError("Caller toArray must not be used");
            }

            @Override
            public Iterator<T> iterator() {
                throw new AssertionError("Caller iterator must not be used");
            }
        };
    }
}
