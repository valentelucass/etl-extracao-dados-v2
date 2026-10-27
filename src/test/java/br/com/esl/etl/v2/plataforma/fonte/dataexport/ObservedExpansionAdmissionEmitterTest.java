package br.com.esl.etl.v2.plataforma.fonte.dataexport;

import static br.com.esl.etl.v2.plataforma.fonte.dataexport.ObservedExpansionSchemaFixtures.bound;
import static br.com.esl.etl.v2.plataforma.fonte.dataexport.ObservedExpansionSchemaFixtures.plan;
import static br.com.esl.etl.v2.plataforma.fonte.dataexport.ObservedExpansionSchemaFixtures.raw;
import static br.com.esl.etl.v2.plataforma.fonte.dataexport.ObservedExpansionSchemaFixtures.row;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

final class ObservedExpansionAdmissionEmitterTest {
    @Test
    void fourPropertyEnvelopeKeepsGenuineCoreAndExternalPlanScope() throws Exception {
        final var plan = plan(ObservedExpansionCaptureTemplate.CAP);
        final var guard = bound(plan);
        final var raw = raw(plan, row(plan.template()));
        final var inspected = guard.inspect(raw);
        final var step = guard.project(inspected);
        final var admitted = guard.admit(inspected, step);
        final var json = new ObjectMapper().readTree(admitted.json());
        assertEquals(4, json.size());
        assertEquals("observed-expansion-admission-v1", json.path("projectionVersion").asText());
        assertEquals(guard.schemaRelease(), json.path("schemaRelease").asText());
        assertEquals(
                guard.schemaResourceSha256().toUpperCase(java.util.Locale.ROOT),
                admitted.schemaSha256());
        assertEquals(admitted.schemaSha256(), json.path("schemaSha256").asText());
        assertEquals(6, json.path("core").size());
        assertEquals(
                "NOT_PERFORMED_BY_CORE", json.path("core").path("sourceSchemaValidation").asText());
        assertSame(step.projection(), admitted.coreProjection());
        assertSame(raw, admitted.rawPage());
        assertEquals(plan.sourceInstance(), admitted.sourceInstance());
        assertEquals(plan.tenantScope(), admitted.tenantScope());
        assertEquals(plan.captureExecutionId(), admitted.captureExecutionId());
        assertEquals(ObservedExpansionPageProjection.Status.MAPPED, admitted.parseState());
    }

    @Test
    void falseSchemaGuardCannotProjectOrAdmitAnExternalGenuineStep() {
        final var plan = plan(ObservedExpansionCaptureTemplate.CAP);
        final var guard = bound(plan);
        final var changed = row(plan.template());
        changed.put("undeclared", "offline");
        final var raw = raw(plan, changed);
        final var inspected = guard.inspect(raw);
        final var external = new ObservedExpansionPageProjector(plan).projectNextWithProof(raw);
        assertFalse(inspected.schemaMatched());
        assertThrows(IllegalArgumentException.class, () -> guard.project(inspected));
        assertThrows(IllegalArgumentException.class, () -> guard.admit(inspected, external));
    }

    @Test
    void anotherBindingCannotReplayEvenSamePlanAndRealProducerProof() {
        final var plan = plan(ObservedExpansionCaptureTemplate.CAP);
        final var first = bound(plan);
        final var second = bound(plan);
        final var raw = raw(plan, row(plan.template()));
        final var inspected = first.inspect(raw);
        final var step = first.project(inspected);
        assertThrows(IllegalArgumentException.class, () -> second.admit(second.inspect(raw), step));
        first.admit(inspected, step);
        assertThrows(IllegalArgumentException.class, () -> first.admit(inspected, step));
    }

    @Test
    void differentOrClonedRawCannotSubstituteForInspectedRawAndActualResult() {
        final var plan = plan(ObservedExpansionCaptureTemplate.CAP);
        final var guard = bound(plan);
        final var original = raw(plan, row(plan.template()));
        final var inspection = guard.inspect(original);
        final var step = guard.project(inspection);
        final var clone =
                new ObservedExpansionRawPage(
                        original.request(), original.receivedAt(), original.body());
        final var forged =
                new ObservedExpansionPageProjection(
                        clone,
                        step.projection().status(),
                        step.projection().pageIssue(),
                        step.projection().records(),
                        step.projection().projectionJson());
        assertThrows(
                IllegalArgumentException.class,
                () -> guard.admit(inspection, forged, step.proof()));
        assertThrows(IllegalArgumentException.class, () -> guard.admit(guard.inspect(clone), step));
        assertFalse(step.proof().consumed());
        guard.admit(inspection, step);
    }

    @Test
    void pageAndPlanProofLinksCannotBeSubstituted() {
        final var plan = plan(ObservedExpansionCaptureTemplate.CAP);
        final var guard = bound(plan);
        final var firstRaw = raw(plan, row(plan.template()));
        final var first = guard.inspect(firstRaw);
        final var firstStep = guard.project(first);
        guard.admit(first, firstStep);
        final var secondRaw =
                new ObservedExpansionRawPage(
                        plan.request(2), firstRaw.receivedAt(), firstRaw.body());
        final var second = guard.inspect(secondRaw);
        final var secondStep = guard.project(second);
        assertThrows(IllegalArgumentException.class, () -> guard.admit(first, secondStep));
        final var admitted = guard.admit(second, secondStep);
        assertEquals(2, admitted.rawPage().request().page());
        final var foreign = plan(plan.template());
        final var foreignStep =
                new ObservedExpansionPageProjector(foreign)
                        .projectNextWithProof(raw(foreign, row(foreign.template())));
        assertThrows(IllegalArgumentException.class, () -> guard.admit(second, foreignStep));
    }

    @Test
    void evenSelfConsistentDtoJsonCopiesDoNotHaveProducerProvenance() {
        final var plan = plan(ObservedExpansionCaptureTemplate.CAP);
        final var guard = bound(plan);
        final var inspection = guard.inspect(raw(plan, row(plan.template())));
        final var step = guard.project(inspection);
        final var original = step.projection();
        for (final String json :
                List.of(
                        original.projectionJson(),
                        original.projectionJson()
                                .replace("observed-expansion-core-v1", "fake-core-v1"),
                        original.projectionJson().replace("NOT_PERFORMED_BY_CORE", "MATCHED"),
                        original.projectionJson().replaceFirst("offline", "forged"))) {
            final var copied = copyCore(original, json);
            assertThrows(
                    IllegalArgumentException.class,
                    () -> guard.admit(inspection, copied, step.proof()));
        }
        assertFalse(step.proof().consumed());
        guard.admit(inspection, step);
    }

    @Test
    void forgedAdmissionJsonWithRealReleaseHashIsNotAuthority() {
        final var plan = plan(ObservedExpansionCaptureTemplate.CAP);
        final var guard = bound(plan);
        final String fake =
                "{\"projectionVersion\":\"observed-expansion-admission-v1\","
                        + "\"schemaRelease\":\""
                        + guard.schemaRelease()
                        + "\","
                        + "\"schemaSha256\":\""
                        + guard.schemaResourceSha256().toUpperCase(java.util.Locale.ROOT)
                        + "\",\"core\":{}}";
        final var inspection = guard.inspect(raw(plan, fake));
        assertFalse(inspection.schemaMatched());
        assertThrows(IllegalArgumentException.class, () -> guard.project(inspection));
    }

    @Test
    void actualProducedCoreTextIsEmittedVerbatimAndAlternateDtoLexemesAreRejected() {
        final var plan = plan(ObservedExpansionCaptureTemplate.CAP);
        final var guard = bound(plan);
        final var originalRaw = raw(plan, "[ " + row(plan.template()) + " ]");
        final var inspected = guard.inspect(originalRaw);
        final var step = guard.project(inspected);
        final String alternate = "\n " + step.projection().projectionJson() + " \n";
        assertThrows(
                IllegalArgumentException.class,
                () -> guard.admit(inspected, copyCore(step.projection(), alternate), step.proof()));
        final var capability = guard.admit(inspected, step);
        assertTrue(capability.json().endsWith(step.projection().projectionJson() + "}"));
        assertArrayEquals(originalRaw.body(), capability.rawPage().body());
        assertEquals(
                capability.json(),
                new String(capability.utf16Le(), java.nio.charset.StandardCharsets.UTF_16LE));
    }

    @Test
    void genuineMapperStructureIssueHasUnknownCountAndActualProgress() {
        final var plan = plan(ObservedExpansionCaptureTemplate.SIN);
        final var guard = bound(plan);
        final var row = row(plan.template());
        row.put("insurance_claim_commentary", "x".repeat(4001));
        final var inspected = guard.inspect(raw(plan, row));
        final var step = guard.project(inspected);
        assertEquals(
                ObservedExpansionPageProjection.Issue.MAPPER_REJECTED,
                step.projection().pageIssue());
        final var capability = guard.admit(inspected, step);
        assertEquals(
                ObservedExpansionPageProjection.Status.STRUCTURE_ISSUE, capability.parseState());
        assertTrue(capability.physicalRows().isEmpty());
        assertEquals(
                ObservedExpansionPageProjector.State.STRUCTURE_ISSUE,
                capability.coreProgressAfter().state());
        assertTrue(step.proof().consumed());
    }

    @Test
    void inventedAllowedStructureEnumsAreRejectedByActualResultIdentity() {
        final var plan = plan(ObservedExpansionCaptureTemplate.CAP);
        final var guard = bound(plan);
        final var inspected = guard.inspect(raw(plan, row(plan.template())));
        final var step = guard.project(inspected);
        assertEquals(ObservedExpansionPageProjection.Status.MAPPED, step.projection().status());
        for (final var issue :
                List.of(
                        ObservedExpansionPageProjection.Issue.MAPPER_REJECTED,
                        ObservedExpansionPageProjection.Issue.PROJECTION_BYTES_EXCEEDED,
                        ObservedExpansionPageProjection.Issue.PHYSICAL_ROW_BOUND)) {
            final var fake =
                    new ObservedExpansionPageProjection(
                            inspected.rawPage(),
                            ObservedExpansionPageProjection.Status.STRUCTURE_ISSUE,
                            issue,
                            List.of(),
                            ObservedExpansionProjectionJson.serialize(plan, List.of(), issue));
            assertThrows(
                    IllegalArgumentException.class,
                    () -> guard.admit(inspected, fake, step.proof()));
        }
        assertFalse(step.proof().consumed());
    }

    @Test
    void envelopeOverheadStillMustFitWithoutConsumingOrAdvancingProof() {
        final var probePlan = plan(ObservedExpansionCaptureTemplate.CAP);
        final var sample = row(probePlan.template());
        final var probeCore =
                new ObservedExpansionPageProjector(probePlan).projectNext(raw(probePlan, sample));
        final int limit = (int) probeCore.projectionByteLength() + 2;
        final var defaults = probePlan.limits();
        final var limits =
                new ObservedExpansionCaptureLimits(
                        defaults.maximumPageBytes(),
                        limit,
                        defaults.maximumRawBytes(),
                        defaults.maximumProjectionTotalBytes(),
                        defaults.maximumPhysicalRowsPerPage(),
                        defaults.maximumRowsPerExecution(),
                        defaults.maximumPages(),
                        defaults.maximumJsonNodes());
        final var plan =
                new ObservedExpansionCapturePlan(
                        java.util.UUID.randomUUID(),
                        probePlan.template(),
                        probePlan.sourceInstance(),
                        probePlan.tenantScope(),
                        probePlan.businessWindow(),
                        probePlan.sourceZone(),
                        probePlan.transport(),
                        probePlan.orderBy(),
                        probePlan.envelope(),
                        probePlan.per(),
                        limits);
        final var guard = bound(plan);
        final var inspected = guard.inspect(raw(plan, sample));
        final var step = guard.project(inspected);
        assertThrows(IllegalArgumentException.class, () -> guard.admit(inspected, step));
        assertFalse(step.proof().consumed());
        assertEquals(0, guard.admittedPages());
    }

    private static ObservedExpansionPageProjection copyCore(
            final ObservedExpansionPageProjection original, final String json) {
        return new ObservedExpansionPageProjection(
                original.rawPage(),
                original.status(),
                original.pageIssue(),
                original.records(),
                json);
    }
}
