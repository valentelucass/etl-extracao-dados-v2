package br.com.esl.etl.v2.plataforma.fonte.dataexport;

import static br.com.esl.etl.v2.plataforma.fonte.dataexport.ObservedExpansionCoreFixtures.plan;
import static br.com.esl.etl.v2.plataforma.fonte.dataexport.ObservedExpansionCoreFixtures.raw;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class ObservedExpansionStructureIssueTest {
    @Test
    void malformedDuplicateOrConcatenatedJsonRetainsRawWithUnknownCount() {
        for (final String source :
                new String[] {"[", "[{\"comments\":1,\"comments\":2}]", "[] []"}) {
            final var plan = plan(ObservedExpansionCaptureTemplate.CAP);
            final var projector = new ObservedExpansionPageProjector(plan);
            final var page = projector.projectNext(raw(plan, 1, source));
            assertIssue(page, ObservedExpansionPageProjection.Issue.INVALID_JSON);
            assertArrayEquals(source.getBytes(StandardCharsets.UTF_8), page.rawPage().body());
            assertEquals(1L, projector.nextOccurrence());
            assertEquals(ObservedExpansionPageProjector.State.STRUCTURE_ISSUE, projector.state());
        }
    }

    @Test
    void malformedUtf8IsNotSilentlyReplaced() {
        final var plan = plan(ObservedExpansionCaptureTemplate.CAP);
        final byte[] bytes = {(byte) 0xc3, 0x28};
        final var page =
                new ObservedExpansionPageProjector(plan)
                        .projectNext(
                                new ObservedExpansionRawPage(
                                        plan.request(1), Instant.EPOCH, bytes));
        assertIssue(page, ObservedExpansionPageProjection.Issue.INVALID_UTF8);
        assertArrayEquals(bytes, page.rawPage().body());
    }

    @Test
    void sourceEnvelopeMustMatchTheFrozenPolicyWithoutFallback() {
        final var rootPlan = plan(ObservedExpansionCaptureTemplate.INV);
        assertIssue(
                new ObservedExpansionPageProjector(rootPlan)
                        .projectNext(raw(rootPlan, 1, "{\"data\":[]}")),
                ObservedExpansionPageProjection.Issue.ENVELOPE_SHAPE);
        final var dataPlan =
                new ObservedExpansionCapturePlan(
                        UUID.randomUUID(),
                        rootPlan.template(),
                        rootPlan.sourceInstance(),
                        rootPlan.tenantScope(),
                        rootPlan.businessWindow(),
                        rootPlan.sourceZone(),
                        rootPlan.transport(),
                        rootPlan.orderBy(),
                        ObservedExpansionCapturePlan.Envelope.DATA_OBJECT,
                        rootPlan.per(),
                        rootPlan.limits());
        final var empty =
                new ObservedExpansionPageProjector(dataPlan)
                        .projectNext(raw(dataPlan, 1, "{\"data\":[]}"));
        assertEquals(0, empty.physicalRows().orElseThrow());
        assertIssue(
                new ObservedExpansionPageProjector(dataPlan).projectNext(raw(dataPlan, 1, "[]")),
                ObservedExpansionPageProjection.Issue.ENVELOPE_SHAPE);
        assertIssue(
                new ObservedExpansionPageProjector(dataPlan)
                        .projectNext(raw(dataPlan, 1, "{\"data\":[],\"extra\":1}")),
                ObservedExpansionPageProjection.Issue.ENVELOPE_SHAPE);
    }

    @Test
    void scalarNullNestedArrayOrErrorRowDoesNotBecomeAnEmptyMappedPage() {
        for (final String source :
                new String[] {"[null]", "[1]", "[[]]", "[{\"error\":null}]", "[{\"errors\":[]}]"}) {
            final var plan = plan(ObservedExpansionCaptureTemplate.CAP);
            final var page =
                    new ObservedExpansionPageProjector(plan).projectNext(raw(plan, 1, source));
            assertIssue(page, ObservedExpansionPageProjection.Issue.RECORD_SHAPE);
        }
    }

    @Test
    void lateMapperRefusalDoesNotExposeAnIncompleteMappedPrefix() {
        final var plan = plan(ObservedExpansionCaptureTemplate.SIN);
        final var projector = new ObservedExpansionPageProjector(plan);
        projector.projectNext(raw(plan, 1, "[{},{}]"));
        final String source = "[{}, {\"insurance_claim_commentary\":\"" + "x".repeat(4001) + "\"}]";
        final var page = projector.projectNext(raw(plan, 2, source));
        assertIssue(page, ObservedExpansionPageProjection.Issue.MAPPER_REJECTED);
        assertEquals(3L, projector.nextOccurrence());
        assertArrayEquals(source.getBytes(StandardCharsets.UTF_8), page.rawPage().body());
        assertThrows(IllegalStateException.class, () -> projector.projectNext(raw(plan, 3, "[]")));
    }

    @Test
    void physicalRowsAreBoundedIndependentlyFromRequestedPer() {
        final var limits =
                new ObservedExpansionCaptureLimits(4096, 65536, 16384, 262144, 2, 4, 4, 1000);
        final var plan = plan(ObservedExpansionCaptureTemplate.INV, limits);
        final var mapped =
                new ObservedExpansionPageProjector(plan).projectNext(raw(plan, 1, "[{},{}]"));
        assertEquals(1, plan.per());
        assertEquals(2, mapped.physicalRows().orElseThrow());
        final var refused =
                new ObservedExpansionPageProjector(plan).projectNext(raw(plan, 1, "[{},{},{}]"));
        assertIssue(refused, ObservedExpansionPageProjection.Issue.PHYSICAL_ROW_BOUND);
    }

    @Test
    void executionRowBudgetDoesNotResetOnTheNextPage() {
        final var limits =
                new ObservedExpansionCaptureLimits(4096, 65536, 16384, 262144, 2, 3, 4, 1000);
        final var plan = plan(ObservedExpansionCaptureTemplate.CAP, limits);
        final var projector = new ObservedExpansionPageProjector(plan);
        projector.projectNext(raw(plan, 1, "[{},{}]"));
        assertIssue(
                projector.projectNext(raw(plan, 2, "[{},{}]")),
                ObservedExpansionPageProjection.Issue.PHYSICAL_ROW_BOUND);
        assertEquals(3L, projector.nextOccurrence());
    }

    @Test
    void jsonNodeBoundRetainsOriginalBodyAndDoesNotReparseWithALargerBudget() {
        final var limits =
                new ObservedExpansionCaptureLimits(4096, 65536, 16384, 262144, 2, 4, 4, 1);
        final var plan = plan(ObservedExpansionCaptureTemplate.CAP, limits);
        assertIssue(
                new ObservedExpansionPageProjector(plan).projectNext(raw(plan, 1, "[{}]")),
                ObservedExpansionPageProjection.Issue.INVALID_JSON);
    }

    @Test
    void sidecarOverflowReturnsOnlyBoundedDiagnosticWithUnknownCount() {
        final var limits =
                new ObservedExpansionCaptureLimits(4096, 1024, 16384, 4096, 2, 4, 4, 1000);
        final var plan = plan(ObservedExpansionCaptureTemplate.CAP, limits);
        final var page = new ObservedExpansionPageProjector(plan).projectNext(raw(plan, 1, "[{}]"));
        assertIssue(page, ObservedExpansionPageProjection.Issue.PROJECTION_BYTES_EXCEEDED);
        assertTrue(page.projectionByteLength() <= 1024);
        assertArrayEquals("[{}]".getBytes(StandardCharsets.UTF_8), page.rawPage().body());
    }

    @Test
    void rawBodyRejectsOversizeBeforeMakingAnInternalCopy() {
        final var limits = new ObservedExpansionCaptureLimits(2, 65536, 8, 262144, 2, 4, 4, 1000);
        final var plan = plan(ObservedExpansionCaptureTemplate.CAP, limits);
        assertThrows(
                IllegalArgumentException.class,
                () -> new ObservedExpansionRawPage(plan.request(1), Instant.EPOCH, new byte[3]));
        assertThrows(
                IllegalArgumentException.class,
                () -> new ObservedExpansionRawPage(plan.request(1), Instant.EPOCH, new byte[0]));
    }

    @Test
    void missingRepeatedAndForeignPagesCannotAdvanceTheCounter() {
        final var plan = plan(ObservedExpansionCaptureTemplate.CAP);
        final var projector = new ObservedExpansionPageProjector(plan);
        assertThrows(
                IllegalArgumentException.class, () -> projector.projectNext(raw(plan, 2, "[{}]")));
        final var foreign = plan(ObservedExpansionCaptureTemplate.CAP);
        assertThrows(
                IllegalArgumentException.class,
                () -> projector.projectNext(raw(foreign, 1, "[{}]")));
        assertEquals(1L, projector.nextOccurrence());
        projector.projectNext(raw(plan, 1, "[{}]"));
        assertThrows(
                IllegalArgumentException.class, () -> projector.projectNext(raw(plan, 1, "[{}]")));
        assertEquals(2L, projector.nextOccurrence());
    }

    @Test
    void pageLimitIsPartialAndNeverAnInferredTerminal() {
        final var limits =
                new ObservedExpansionCaptureLimits(4096, 65536, 4096, 65536, 2, 2, 1, 1000);
        final var plan = plan(ObservedExpansionCaptureTemplate.CAP, limits);
        final var projector = new ObservedExpansionPageProjector(plan);
        projector.projectNext(raw(plan, 1, "[{}]"));
        assertEquals(ObservedExpansionPageProjector.State.PARTIAL_LIMIT, projector.state());
    }

    @Test
    void rawExecutionBytesStopTheSessionWithoutResetOrRetry() {
        final var limits =
                new ObservedExpansionCaptureLimits(1024, 65536, 1024, 262144, 2, 4, 4, 1000);
        final var plan = plan(ObservedExpansionCaptureTemplate.CAP, limits);
        final var projector = new ObservedExpansionPageProjector(plan);
        final String source = "[{\"comments\":\"" + "x".repeat(600) + "\"}]";
        projector.projectNext(raw(plan, 1, source));
        assertThrows(
                IllegalStateException.class, () -> projector.projectNext(raw(plan, 2, source)));
        assertEquals(ObservedExpansionPageProjector.State.TOTAL_LIMIT, projector.state());
        assertEquals(2L, projector.nextOccurrence());
        assertThrows(IllegalStateException.class, () -> projector.projectNext(raw(plan, 2, "[]")));
    }

    @Test
    void projectionExecutionBytesHaveASeparateCumulativeBudget() {
        final var probePlan = plan(ObservedExpansionCaptureTemplate.CAP);
        final var probe =
                new ObservedExpansionPageProjector(probePlan)
                        .projectNext(raw(probePlan, 1, "[{}]"));
        final int cap = (int) probe.projectionByteLength() + 100;
        final var limits = new ObservedExpansionCaptureLimits(4096, cap, 16384, cap, 2, 4, 4, 1000);
        final var plan = plan(ObservedExpansionCaptureTemplate.CAP, limits);
        final var projector = new ObservedExpansionPageProjector(plan);
        projector.projectNext(raw(plan, 1, "[{}]"));
        assertThrows(
                IllegalStateException.class, () -> projector.projectNext(raw(plan, 2, "[{}]")));
        assertEquals(ObservedExpansionPageProjector.State.TOTAL_LIMIT, projector.state());
        assertEquals(2L, projector.nextOccurrence());
    }

    private static void assertIssue(
            final ObservedExpansionPageProjection page,
            final ObservedExpansionPageProjection.Issue issue) {
        assertEquals(ObservedExpansionPageProjection.Status.STRUCTURE_ISSUE, page.status());
        assertEquals(issue, page.pageIssue());
        assertTrue(page.physicalRows().isEmpty());
        assertTrue(page.firstOccurrence().isEmpty());
        assertTrue(page.lastOccurrence().isEmpty());
        assertTrue(page.records().isEmpty());
    }
}
