package br.com.esl.etl.v2.plataforma.fonte.dataexport;

import static br.com.esl.etl.v2.plataforma.fonte.dataexport.ObservedExpansionSchemaFixtures.bound;
import static br.com.esl.etl.v2.plataforma.fonte.dataexport.ObservedExpansionSchemaFixtures.plan;
import static br.com.esl.etl.v2.plataforma.fonte.dataexport.ObservedExpansionSchemaFixtures.raw;
import static br.com.esl.etl.v2.plataforma.fonte.dataexport.ObservedExpansionSchemaFixtures.row;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.plataforma.expansao.ExpansionValue;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

final class ObservedExpansionProducerProvenanceTest {
    @Test
    void missingProofDoesNotTurnAnInternallyConsistentDtoIntoAuthority() {
        final var plan = plan(ObservedExpansionCaptureTemplate.CAP);
        final var guard = bound(plan);
        final var inspected = guard.inspect(raw(plan, row(plan.template())));
        final var dto = new ObservedExpansionPageProjector(plan).projectNext(inspected.rawPage());
        assertThrows(IllegalArgumentException.class, () -> guard.admit(inspected, dto, null));
        assertEquals(0, guard.admittedPages());
    }

    @Test
    void fabricatedMappedFieldContentCannotBorrowARealMappedProof() {
        final var plan = plan(ObservedExpansionCaptureTemplate.CAP);
        final var guard = bound(plan);
        final var inspected = guard.inspect(raw(plan, row(plan.template())));
        final var step = guard.project(inspected);
        final var realRow = step.projection().records().get(0);
        final var fields = new ArrayList<>(realRow.fields());
        for (int index = 0; index < fields.size(); index++) {
            if (fields.get(index).path().equals("comments")) {
                fields.set(
                        index,
                        new ObservedExpansionProjectedField(
                                "comments",
                                ObservedExpansionProjectedField.Kind.TEXT,
                                new ExpansionValue<>(
                                        ExpansionValue.Presence.VALUE,
                                        ExpansionValue.Wire.STRING,
                                        "fabricated",
                                        "fabricated",
                                        null)));
            }
        }
        final var fakeRow = new ObservedExpansionProjectedRecord(1, realRow.captured(), fields);
        final var fakeRows = List.of(fakeRow);
        final var fake =
                new ObservedExpansionPageProjection(
                        inspected.rawPage(),
                        ObservedExpansionPageProjection.Status.MAPPED,
                        null,
                        fakeRows,
                        ObservedExpansionProjectionJson.serialize(plan, fakeRows, null));
        assertThrows(
                IllegalArgumentException.class, () -> guard.admit(inspected, fake, step.proof()));
        assertFalse(step.proof().consumed());
        guard.admit(inspected, step);
    }

    @Test
    void fabricatedMappedResultCannotBorrowARealStructuralProof() {
        final var plan = plan(ObservedExpansionCaptureTemplate.SIN);
        final var guard = bound(plan);
        final var source = row(plan.template());
        source.put("insurance_claim_commentary", "x".repeat(4001));
        final var inspected = guard.inspect(raw(plan, source));
        final var step = guard.project(inspected);
        assertEquals(
                ObservedExpansionPageProjection.Status.STRUCTURE_ISSUE, step.projection().status());
        final var goodRows =
                new ObservedExpansionPageProjector(plan)
                        .projectNext(raw(plan, row(plan.template())))
                        .records();
        final var fake =
                new ObservedExpansionPageProjection(
                        inspected.rawPage(),
                        ObservedExpansionPageProjection.Status.MAPPED,
                        null,
                        goodRows,
                        ObservedExpansionProjectionJson.serialize(plan, goodRows, null));
        assertThrows(
                IllegalArgumentException.class, () -> guard.admit(inspected, fake, step.proof()));
        assertFalse(step.proof().consumed());
    }

    @Test
    void secondProofCannotSkipTheFirstAdmittedPrefix() {
        final var plan = plan(ObservedExpansionCaptureTemplate.CAP);
        final var guard = bound(plan);
        final var first = guard.inspect(raw(plan, row(plan.template())));
        final var firstStep = guard.project(first);
        final var second = guard.inspect(page(plan, 2, first.rawPage()));
        final var secondStep = guard.project(second);
        assertThrows(IllegalArgumentException.class, () -> guard.admit(second, secondStep));
        assertEquals(0, guard.admittedPages());
        guard.admit(first, firstStep);
        guard.admit(second, secondStep);
        assertEquals(2, guard.admittedPages());
    }

    @Test
    void standaloneProjectorEvenWithSamePlanAndRawIsNotTheBoundProducer() {
        final var plan = plan(ObservedExpansionCaptureTemplate.CAP);
        final var guard = bound(plan);
        final var inspected = guard.inspect(raw(plan, row(plan.template())));
        final var external =
                new ObservedExpansionPageProjector(plan).projectNextWithProof(inspected.rawPage());
        assertThrows(IllegalArgumentException.class, () -> guard.admit(inspected, external));
        final var actual = guard.project(inspected);
        guard.admit(inspected, actual);
    }

    @Test
    void consumedProofCannotBeReusedWithinOrAcrossBindings() {
        final var plan = plan(ObservedExpansionCaptureTemplate.CAP);
        final var guard = bound(plan);
        final var inspected = guard.inspect(raw(plan, row(plan.template())));
        final var step = guard.project(inspected);
        guard.admit(inspected, step);
        assertTrue(step.proof().consumed());
        assertThrows(IllegalArgumentException.class, () -> guard.admit(inspected, step));
        final var other = bound(plan);
        assertThrows(
                IllegalArgumentException.class,
                () -> other.admit(other.inspect(inspected.rawPage()), step));
    }

    @Test
    void realExecutionRowAccumulatorProducesThePhysicalBoundRefusal() {
        final var base = plan(ObservedExpansionCaptureTemplate.CAP);
        final var limits =
                new ObservedExpansionCaptureLimits(
                        8 * 1024 * 1024,
                        16 * 1024 * 1024,
                        32L * 1024 * 1024,
                        64L * 1024 * 1024,
                        2,
                        3,
                        4,
                        100000);
        final var plan = withLimits(base, limits);
        final var guard = bound(plan);
        final String body = "[" + row(plan.template()) + "," + row(plan.template()) + "]";
        final var first = guard.inspect(raw(plan, body));
        final var firstStep = guard.project(first);
        final var firstCap = guard.admit(first, firstStep);
        final var second = guard.inspect(page(plan, 2, first.rawPage()));
        final var secondStep = guard.project(second);
        assertEquals(3L, secondStep.proof().before().nextOccurrence());
        assertEquals(
                ObservedExpansionPageProjection.Issue.PHYSICAL_ROW_BOUND,
                secondStep.projection().pageIssue());
        final var secondCap = guard.admit(second, secondStep);
        assertTrue(secondCap.physicalRows().isEmpty());
        assertEquals(2L, guard.admittedRows());
        assertEquals(
                first.rawPage().byteLength() + second.rawPage().byteLength(),
                guard.admittedRawBytes());
        assertEquals(
                firstCap.byteLengthUtf16Le() + secondCap.byteLengthUtf16Le(),
                guard.admittedEnvelopeBytes());
        assertEquals(
                firstStep.projection().projectionByteLength()
                        + secondStep.projection().projectionByteLength(),
                guard.admittedCoreBytes());
        assertEquals(firstStep.projection().fieldIssueRows(), guard.admittedIssueRows());
    }

    @Test
    void envelopeCumulativeBudgetCannotBeReplacedWithCoreByteTotals() {
        final var base = plan(ObservedExpansionCaptureTemplate.CAP);
        final var source = raw(base, row(base.template()));
        final long coreBytes =
                new ObservedExpansionPageProjector(base).projectNext(source).projectionByteLength();
        final var old = base.limits();
        final var limits =
                new ObservedExpansionCaptureLimits(
                        old.maximumPageBytes(),
                        (int) coreBytes + 1024,
                        old.maximumRawBytes(),
                        2 * coreBytes + 100,
                        old.maximumPhysicalRowsPerPage(),
                        old.maximumRowsPerExecution(),
                        old.maximumPages(),
                        old.maximumJsonNodes());
        final var plan = withLimits(base, limits);
        final var guard = bound(plan);
        final var first = guard.inspect(raw(plan, row(plan.template())));
        final var firstStep = guard.project(first);
        final var firstCap = guard.admit(first, firstStep);
        final var second = guard.inspect(page(plan, 2, first.rawPage()));
        final var secondStep = guard.project(second);
        assertEquals(
                ObservedExpansionPageProjection.Status.MAPPED, secondStep.projection().status());
        assertThrows(IllegalArgumentException.class, () -> guard.admit(second, secondStep));
        assertFalse(secondStep.proof().consumed());
        assertEquals(1, guard.admittedPages());
        assertEquals(firstCap.byteLengthUtf16Le(), guard.admittedEnvelopeBytes());
    }

    @Test
    void actualProofCarriesAllExecutionCountersAndStates() {
        final var plan = plan(ObservedExpansionCaptureTemplate.CAP);
        final var guard = bound(plan);
        final var inspected = guard.inspect(raw(plan, row(plan.template())));
        final var step = guard.project(inspected);
        final var before = step.proof().before();
        final var after = step.proof().after();
        assertEquals(1, before.nextPage());
        assertEquals(1L, before.nextOccurrence());
        assertEquals(0L, before.rawBytes());
        assertEquals(0L, before.projectionBytes());
        assertEquals(0L, before.issueRows());
        assertEquals(ObservedExpansionPageProjector.State.OPEN, before.state());
        assertEquals(2, after.nextPage());
        assertEquals(2L, after.nextOccurrence());
        assertEquals(inspected.rawPage().byteLength(), after.rawBytes());
        assertEquals(step.projection().projectionByteLength(), after.projectionBytes());
        assertEquals(step.projection().fieldIssueRows(), after.issueRows());
        assertEquals(ObservedExpansionPageProjector.State.OPEN, after.state());
        assertEquals("observed-expansion-projector-proof-v1", step.proof().protocolVersion());
    }

    @Test
    void actualProjectionByteLimitCanIssueAStructuralResultWithGenuineProof() {
        final var base = plan(ObservedExpansionCaptureTemplate.CAP);
        final var old = base.limits();
        final var plan =
                withLimits(
                        base,
                        new ObservedExpansionCaptureLimits(
                                old.maximumPageBytes(),
                                1024,
                                old.maximumRawBytes(),
                                4096,
                                old.maximumPhysicalRowsPerPage(),
                                old.maximumRowsPerExecution(),
                                old.maximumPages(),
                                old.maximumJsonNodes()));
        final var guard = bound(plan);
        final var inspected = guard.inspect(raw(plan, row(plan.template())));
        final var step = guard.project(inspected);
        assertEquals(
                ObservedExpansionPageProjection.Issue.PROJECTION_BYTES_EXCEEDED,
                step.projection().pageIssue());
        final var capability = guard.admit(inspected, step);
        assertEquals(
                ObservedExpansionPageProjection.Status.STRUCTURE_ISSUE, capability.parseState());
        assertTrue(capability.physicalRows().isEmpty());
    }

    @Test
    void malformedSourceCannotReachThePrivateProjectorByAFalseGuard() {
        final var plan = plan(ObservedExpansionCaptureTemplate.CAP);
        final var guard = bound(plan);
        final var inspected = guard.inspect(raw(plan, "["));
        assertFalse(inspected.schemaMatched());
        assertThrows(IllegalArgumentException.class, () -> guard.project(inspected));
        assertEquals(0, guard.admittedPages());
    }

    @Test
    void orderedAdmissionsStillWorkAfterTheRealProducerAlreadyReachedLocalTerminal() {
        final var plan = plan(ObservedExpansionCaptureTemplate.CAP);
        final var guard = bound(plan);
        final var first = guard.inspect(raw(plan, row(plan.template())));
        final var step1 = guard.project(first);
        final var second = guard.inspect(page(plan, 2, first.rawPage()));
        final var step2 = guard.project(second);
        final var terminal =
                guard.inspect(
                        new ObservedExpansionRawPage(
                                plan.request(3),
                                java.time.Instant.EPOCH,
                                "[]".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        final var step3 = guard.project(terminal);
        assertEquals(
                ObservedExpansionPageProjector.State.LOCAL_TERMINAL, step3.proof().after().state());
        guard.admit(first, step1);
        guard.admit(second, step2);
        guard.admit(terminal, step3);
        assertEquals(3, guard.admittedPages());
        assertEquals(2L, guard.admittedRows());
    }

    @Test
    void legacyDtoOnlyMethodDoesNotExposeAnAdmissionProofFactory() {
        final var plan = plan(ObservedExpansionCaptureTemplate.CAP);
        final var inspectedRaw = raw(plan, row(plan.template()));
        final var dto = new ObservedExpansionPageProjector(plan).projectNext(inspectedRaw);
        final var guard = bound(plan);
        final var inspected = guard.inspect(inspectedRaw);
        assertThrows(IllegalArgumentException.class, () -> guard.admit(inspected, dto, null));
        guard.admit(inspected, guard.project(inspected));
    }

    @Test
    void immutableEarlierProgressDoesNotTurnIntoTheOwnersLaterMutableState() {
        final var plan = plan(ObservedExpansionCaptureTemplate.CAP);
        final var guard = bound(plan);
        final var first = guard.inspect(raw(plan, row(plan.template())));
        final var firstStep = guard.project(first);
        final var previous = firstStep.proof().after();
        final long rawBefore = previous.rawBytes();
        final var second = guard.inspect(page(plan, 2, first.rawPage()));
        final var secondStep = guard.project(second);
        assertEquals(rawBefore, previous.rawBytes());
        assertEquals(2, previous.nextPage());
        assertEquals(3, secondStep.proof().after().nextPage());
        assertEquals(
                rawBefore + second.rawPage().byteLength(), secondStep.proof().after().rawBytes());
    }

    private static ObservedExpansionRawPage page(
            final ObservedExpansionCapturePlan plan,
            final int number,
            final ObservedExpansionRawPage source) {
        return new ObservedExpansionRawPage(
                plan.request(number), source.receivedAt(), source.body());
    }

    private static ObservedExpansionCapturePlan withLimits(
            final ObservedExpansionCapturePlan original,
            final ObservedExpansionCaptureLimits limits) {
        return new ObservedExpansionCapturePlan(
                java.util.UUID.randomUUID(),
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
}
