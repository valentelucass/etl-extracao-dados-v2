package br.com.esl.etl.v2.plataforma.contrato;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.plataforma.controle.ControlPlaneStart;
import br.com.esl.etl.v2.plataforma.controle.ImmutableFingerprint;
import br.com.esl.etl.v2.plataforma.persistencia.staging.StagingBatch;
import br.com.esl.etl.v2.plataforma.persistencia.staging.StagingPromotionKernel;
import br.com.esl.etl.v2.plataforma.persistencia.staging.StagingPublicationResult;
import br.com.esl.etl.v2.plataforma.qualidade.DataQualityPromotionPermit;
import br.com.esl.etl.v2.plataforma.qualidade.DataQualityTestSupport;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ContractRunGuardTest {

    @Test
    void bindsBothComponentVersionsAndPolicyToTheControlPlaneOccurrence() {
        final Fixture fixture = fixture(UUID.randomUUID(), List.of());
        final ContractExecutionBinding binding = fixture.binding();
        final ControlPlaneStart start = fixture.start();

        binding.verify(start);
        assertEquals(fixture.release().metadataFingerprint(), binding.metadataFingerprint());
        assertEquals(fixture.release().responseFingerprint(), binding.responseFingerprint());
        assertEquals(fixture.release().contractFingerprint(), start.contract());
        assertEquals(binding.configurationFingerprint(), start.configuration());
        assertEquals("contract-compatibility-policy-v3", binding.policyFingerprint().version());
        assertEquals("runtime-v1", start.configuration().version());

        final Fixture other = fixture(UUID.randomUUID(), List.of());
        final ContractDriftException exception =
                assertThrows(ContractDriftException.class, () -> binding.verify(other.start()));
        assertEquals(ContractDriftException.Reason.EXECUTION_BINDING_MISMATCH, exception.reason());
    }

    @Test
    void metadataAloneEmptyOnlyAndMissingTerminalEvidenceNeverProduceAPermit() {
        final Fixture metadataOnly = fixture(UUID.randomUUID(), List.of());
        metadataOnly.guard().validateMetadata(metadataOnly.release().metadata());
        assertReason(
                ContractDriftException.Reason.RESPONSE_EVIDENCE_REQUIRED,
                metadataOnly.guard()::complete);

        final Fixture emptyOnly = fixture(UUID.randomUUID(), List.of());
        emptyOnly.guard().validateMetadata(emptyOnly.release().metadata());
        emptyOnly
                .guard()
                .observeDataExportResponse(
                        1, ContractTestSupport.emptyResponse(emptyOnly.release()));
        emptyOnly.guard().markDataExportTraversalTerminal(1);
        emptyOnly.guard().markDataExportCompletionAuditSucceeded(1);
        assertReason(
                ContractDriftException.Reason.RESPONSE_EVIDENCE_REQUIRED,
                emptyOnly.guard()::complete);

        final Fixture noTerminal = fixture(UUID.randomUUID(), List.of());
        noTerminal.guard().validateMetadata(noTerminal.release().metadata());
        noTerminal.guard().observeDataExportResponse(1, noTerminal.release().response());
        assertReason(
                ContractDriftException.Reason.TRAVERSAL_TERMINAL_EVIDENCE_REQUIRED,
                noTerminal.guard()::complete);
    }

    @Test
    void validatesManyPagesWithConstantStateAndIssuesAReusablePermitAfterTerminalEmpty() {
        final Fixture fixture = fixture(UUID.randomUUID(), List.of());
        final ContractRunGuard guard = fixture.guard();
        guard.validateMetadata(fixture.release().metadata());
        for (int page = 1; page <= 10_000; page++) {
            guard.observeDataExportResponse(page, fixture.release().response());
        }
        guard.observeDataExportResponse(
                10_001, ContractTestSupport.emptyResponse(fixture.release()));
        guard.markDataExportTraversalTerminal(10_001);
        guard.markDataExportCompletionAuditSucceeded(10_001);

        final ContractPromotionPermit permit = guard.complete();

        assertEquals(10_001, guard.responsePages());
        assertEquals(fixture.binding().executionId(), permit.executionId());
        assertEquals(fixture.binding().contractFingerprint(), permit.contractFingerprint());
        assertEquals(
                fixture.binding().configurationFingerprint(), permit.configurationFingerprint());
        assertEquals(SourceDataEffect.SHADOW_UPSERT, permit.dataEffect());
        assertFalse(permit.alertObserved());
        assertFalse(permit.toString().contains(permit.executionId().toString()));
    }

    @Test
    void graphQlUsesTheProviderNeutralObservationAndTerminalEvidencePath() {
        final SourceContractRelease release = graphQlRelease();
        final ContractCompatibilityPolicy policy =
                ContractCompatibilityPolicy.create(
                        "graphql-policy-v1", release.contractFingerprint(), List.of());
        final ContractExecutionBinding binding =
                ContractExecutionBinding.create(
                        UUID.randomUUID(),
                        release,
                        policy,
                        new ImmutableFingerprint("runtime-v1", "d".repeat(64)));
        final ControlPlaneStart start = ContractTestSupport.controlPlaneStart(binding);
        final ContractRunGuard guard =
                new ContractRunGuard(binding, release, policy, start, alert -> {});

        guard.bindCompletenessStatus(SourceCompletenessStatus.PROVEN_COMPLETE);
        guard.validateMetadata(release.metadata());
        guard.observeResponse(release.response());
        guard.markTraversalTerminal(ContractTraversalTerminalEvidence.graphQlPageInfoTerminal());

        assertEquals(binding.executionId(), guard.complete().executionId());
        assertEquals(1, guard.responsePages());
    }

    @Test
    void lateDriftPreventsTheKernelFromPreparingOrPublishing() {
        final Fixture fixture = fixture(UUID.randomUUID(), List.of());
        final ContractRunGuard guard = fixture.guard();
        final RecordingKernel kernel = new RecordingKernel();
        guard.validateMetadata(fixture.release().metadata());
        guard.observeDataExportResponse(1, fixture.release().response());
        final ContractResponse changedType =
                replaceStatus(
                        fixture.release(),
                        ContractTestSupport.field(
                                "/status",
                                ContractResponse.Presence.REQUIRED,
                                false,
                                ContractResponse.JsonType.INTEGER));

        final ContractDriftException drift =
                assertThrows(
                        ContractDriftException.class,
                        () -> guard.observeDataExportResponse(2, changedType));
        assertEquals(ContractDriftException.Reason.BREAKING_CHANGE, drift.reason());
        assertThrows(
                ContractDriftException.class, () -> kernel.prepareCandidateSet(guard.complete()));
        assertEquals(0, kernel.prepareCalls);
        assertEquals(0, kernel.publishCalls);
        assertFalse(drift.getMessage().contains("status"));
    }

    @Test
    void aValidPermitIsMandatoryForBothKernelTransitions() {
        final Fixture fixture = fixture(UUID.randomUUID(), List.of());
        final RecordingKernel kernel = new RecordingKernel();
        final ContractRunGuard guard = fixture.guard();
        guard.validateMetadata(fixture.release().metadata());
        guard.observeDataExportResponse(1, fixture.release().response());
        guard.observeDataExportResponse(2, ContractTestSupport.emptyResponse(fixture.release()));
        guard.markDataExportTraversalTerminal(2);
        guard.markDataExportCompletionAuditSucceeded(2);
        final ContractPromotionPermit permit = guard.complete();

        kernel.prepareCandidateSet(permit);
        kernel.applyReconcileAndPublish(
                permit, DataQualityTestSupport.promotionPermit(permit.executionId()));

        assertEquals(1, kernel.prepareCalls);
        assertEquals(1, kernel.publishCalls);
        assertEquals(permit.executionId(), kernel.lastExecutionId);
        assertThrows(ContractDriftException.class, guard::complete);
        assertThrows(
                ContractDriftException.class,
                () -> guard.observeDataExportResponse(3, fixture.release().response()));
    }

    @Test
    void acceptedCompatibleChangeSurvivesAsAnExplicitAlertOnThePermit() {
        final SourceContractRelease release = ContractTestSupport.release();
        final ContractResponse nullableStatus =
                replaceStatus(
                        release,
                        ContractTestSupport.field(
                                "/status",
                                ContractResponse.Presence.REQUIRED,
                                true,
                                ContractResponse.JsonType.STRING));
        final ContractCompatibilityPolicy emptyPolicy = ContractTestSupport.policy(release);
        final ContractChange compatible =
                new ContractValidator(release, emptyPolicy)
                        .classifyResponse(nullableStatus)
                        .changes()
                        .get(0);
        final ContractCompatibilityPolicy policy =
                ContractCompatibilityPolicy.create(
                        "policy-alert-v1",
                        release.contractFingerprint(),
                        List.of(ContractAllowance.forChange(compatible)));
        final UUID executionId = UUID.randomUUID();
        final ContractExecutionBinding binding =
                ContractExecutionBinding.create(
                        executionId,
                        release,
                        policy,
                        new ImmutableFingerprint("runtime-v1", "e".repeat(64)));
        final ContractRunGuard guard =
                new ContractRunGuard(
                        binding,
                        release,
                        policy,
                        ContractTestSupport.controlPlaneStart(binding),
                        alert -> {});

        guard.bindCompletenessStatus(SourceCompletenessStatus.PROVEN_COMPLETE);
        guard.validateMetadata(release.metadata());
        guard.observeDataExportResponse(1, nullableStatus);
        guard.observeDataExportResponse(2, ContractTestSupport.emptyResponse(release));
        guard.markDataExportTraversalTerminal(2);
        guard.markDataExportCompletionAuditSucceeded(2);
        final ContractPromotionPermit permit = guard.complete();

        assertTrue(guard.alertObserved());
        assertTrue(permit.alertObserved());
    }

    @Test
    void emitsEveryDistinctAllowedChangeOnceAndFailsClosedWhenTheAlertSinkFails() {
        final SourceContractRelease release = ContractTestSupport.release();
        final ContractResponse nullableStatus =
                replaceStatus(
                        release,
                        ContractTestSupport.field(
                                "/status",
                                ContractResponse.Presence.REQUIRED,
                                true,
                                ContractResponse.JsonType.STRING));
        final ContractResponse optionalExtra =
                addField(
                        release,
                        ContractTestSupport.field(
                                "/extra",
                                ContractResponse.Presence.OPTIONAL,
                                true,
                                ContractResponse.JsonType.STRING));
        final ContractValidator classifier =
                new ContractValidator(release, ContractTestSupport.policy(release));
        final ContractChange statusChange =
                classifier.classifyResponse(nullableStatus).changes().get(0);
        final ContractChange extraChange =
                classifier.classifyResponse(optionalExtra).changes().get(0);
        final ContractCompatibilityPolicy policy =
                ContractCompatibilityPolicy.create(
                        "policy-alerts-v1",
                        release.contractFingerprint(),
                        List.of(
                                ContractAllowance.forChange(statusChange),
                                ContractAllowance.forChange(extraChange)));
        final UUID executionId = UUID.randomUUID();
        final ContractExecutionBinding binding =
                ContractExecutionBinding.create(
                        executionId,
                        release,
                        policy,
                        new ImmutableFingerprint("runtime-v1", "e".repeat(64)));
        final List<ContractCompatibilityAlert> alerts = new ArrayList<>();
        final ContractRunGuard guard =
                new ContractRunGuard(
                        binding,
                        release,
                        policy,
                        ContractTestSupport.controlPlaneStart(binding),
                        alerts::add);

        guard.validateMetadata(release.metadata());
        guard.observeDataExportResponse(1, nullableStatus);
        guard.observeDataExportResponse(2, nullableStatus);
        guard.observeDataExportResponse(3, optionalExtra);

        assertEquals(2, alerts.size());
        assertEquals(
                2,
                alerts.stream()
                        .map(ContractCompatibilityAlert::changeSignature)
                        .distinct()
                        .count());
        assertTrue(alerts.stream().allMatch(alert -> alert.executionId().equals(executionId)));

        final ContractRunGuard failingSink =
                new ContractRunGuard(
                        binding,
                        release,
                        policy,
                        ContractTestSupport.controlPlaneStart(binding),
                        alert -> {
                            throw new IllegalStateException("synthetic-alert-sink-failure");
                        });
        failingSink.validateMetadata(release.metadata());
        assertThrows(
                IllegalStateException.class,
                () -> failingSink.observeDataExportResponse(1, nullableStatus));
        assertThrows(ContractDriftException.class, failingSink::complete);
    }

    @Test
    void bindsObservationLimitsAndRejectsUnsafeRuntimeVersions() {
        final SourceContractRelease release = ContractTestSupport.release();
        final ContractCompatibilityPolicy policy = ContractTestSupport.policy(release);
        final UUID executionId = UUID.randomUUID();
        final ImmutableFingerprint runtime = new ImmutableFingerprint("runtime-v1", "d".repeat(64));
        final ContractExecutionBinding defaults =
                ContractExecutionBinding.create(executionId, release, policy, runtime);
        final ContractExecutionBinding tighter =
                ContractExecutionBinding.create(
                        executionId,
                        release,
                        policy,
                        runtime,
                        new ContractObservationLimits(8, 512, 5_000));

        assertNotEquals(defaults.configurationFingerprint(), tighter.configurationFingerprint());
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        ContractExecutionBinding.create(
                                executionId,
                                release,
                                policy,
                                new ImmutableFingerprint("runtime\nv1", "d".repeat(64))));
    }

    @Test
    void failsClosedOnInvalidOrderingBindingOrAdapterEvidence() {
        final Fixture fixture = fixture(UUID.randomUUID(), List.of());
        assertReason(
                ContractDriftException.Reason.METADATA_EVIDENCE_REQUIRED,
                () -> fixture.guard().observeDataExportResponse(1, fixture.release().response()));

        final Fixture terminal = fixture(UUID.randomUUID(), List.of());
        terminal.guard().validateMetadata(terminal.release().metadata());
        terminal.guard()
                .observeDataExportResponse(
                        1, ContractTestSupport.emptyResponse(terminal.release()));
        terminal.guard().markDataExportTraversalTerminal(1);
        assertReason(
                ContractDriftException.Reason.VALIDATION_ALREADY_TERMINAL,
                () -> terminal.guard().observeDataExportResponse(2, terminal.release().response()));

        final Fixture adapterFailure = fixture(UUID.randomUUID(), List.of());
        assertReason(
                ContractDriftException.Reason.RESPONSE_EVIDENCE_REQUIRED,
                () ->
                        adapterFailure
                                .guard()
                                .failClosed(
                                        ContractDriftException.Reason.RESPONSE_EVIDENCE_REQUIRED));

        final SourceContractRelease otherRelease =
                SourceContractRelease.create(
                        fixture.release().sourceKind(),
                        fixture.release().documentReference(),
                        "different-v1",
                        fixture.release().metadata(),
                        fixture.release().response());
        final ContractCompatibilityPolicy otherPolicy =
                ContractCompatibilityPolicy.create(
                        "policy-v1", otherRelease.contractFingerprint(), List.of());
        assertThrows(
                ContractDriftException.class,
                () ->
                        new ContractRunGuard(
                                fixture.binding(),
                                otherRelease,
                                otherPolicy,
                                fixture.start(),
                                alert -> {}));
    }

    private static Fixture fixture(
            final UUID executionId, final List<ContractAllowance> allowances) {
        final SourceContractRelease release = ContractTestSupport.release();
        final ContractCompatibilityPolicy policy =
                ContractCompatibilityPolicy.create(
                        "policy-v1", release.contractFingerprint(), allowances);
        final ContractExecutionBinding binding =
                ContractExecutionBinding.create(
                        executionId,
                        release,
                        policy,
                        new ImmutableFingerprint("runtime-v1", "d".repeat(64)));
        final ControlPlaneStart start = ContractTestSupport.controlPlaneStart(binding);
        final ContractRunGuard guard =
                new ContractRunGuard(binding, release, policy, start, alert -> {});
        guard.bindCompletenessStatus(SourceCompletenessStatus.PROVEN_COMPLETE);
        return new Fixture(release, policy, binding, start, guard);
    }

    private static SourceContractRelease graphQlRelease() {
        final ApprovedGraphQlDocument document =
                ApprovedGraphQlDocument.approve(
                        "query Individual($enabled: Boolean!) { individual(enabled: $enabled) { id } }");
        final ContractMetadata metadata =
                new ContractMetadata(
                        List.of(
                                new ContractMetadata.Element(
                                        ContractMetadata.ElementKind.GRAPHQL_SELECTION,
                                        "/individual/id",
                                        ContractMetadata.DeclaredType.STRING),
                                new ContractMetadata.Element(
                                        ContractMetadata.ElementKind.GRAPHQL_ARGUMENT,
                                        "/individual/enabled",
                                        ContractMetadata.DeclaredType.BOOLEAN)),
                        java.util.Optional.of(document));
        final ContractResponse response =
                new ContractResponse(
                        "/data/individual",
                        ContractResponse.Cardinality.ARRAY,
                        ContractResponse.ObservationState.POPULATED,
                        "/id",
                        List.of(
                                new ContractResponse.Field(
                                        ContractResponse.FieldScope.ENVELOPE,
                                        "/data",
                                        ContractResponse.Cardinality.OBJECT,
                                        ContractResponse.Presence.REQUIRED,
                                        false,
                                        List.of(ContractResponse.JsonType.OBJECT)),
                                new ContractResponse.Field(
                                        ContractResponse.FieldScope.ENVELOPE,
                                        "/data/individual",
                                        ContractResponse.Cardinality.ARRAY,
                                        ContractResponse.Presence.REQUIRED,
                                        false,
                                        List.of(ContractResponse.JsonType.ARRAY)),
                                ContractTestSupport.field(
                                        "/id",
                                        ContractResponse.Presence.REQUIRED,
                                        false,
                                        ContractResponse.JsonType.STRING)));
        return SourceContractRelease.create(
                ContractSourceKind.GRAPHQL, "graphql-individual", "query-v1", metadata, response);
    }

    private static ContractResponse replaceStatus(
            final SourceContractRelease release, final ContractResponse.Field replacement) {
        final List<ContractResponse.Field> fields = new ArrayList<>();
        for (final ContractResponse.Field field : release.response().fields()) {
            fields.add("/status".equals(field.path()) ? replacement : field);
        }
        return new ContractResponse(
                release.response().recordRoot(),
                release.response().rootCardinality(),
                ContractResponse.ObservationState.POPULATED,
                release.response().keyPath(),
                fields);
    }

    private static ContractResponse addField(
            final SourceContractRelease release, final ContractResponse.Field addition) {
        final List<ContractResponse.Field> fields = new ArrayList<>(release.response().fields());
        fields.add(addition);
        return new ContractResponse(
                release.response().recordRoot(),
                release.response().rootCardinality(),
                ContractResponse.ObservationState.POPULATED,
                release.response().keyPath(),
                fields);
    }

    private static void assertReason(
            final ContractDriftException.Reason reason,
            final org.junit.jupiter.api.function.Executable executable) {
        final ContractDriftException exception =
                assertThrows(ContractDriftException.class, executable);
        assertEquals(reason, exception.reason());
    }

    private record Fixture(
            SourceContractRelease release,
            ContractCompatibilityPolicy policy,
            ContractExecutionBinding binding,
            ControlPlaneStart start,
            ContractRunGuard guard) {}

    private static final class RecordingKernel implements StagingPromotionKernel {

        private int prepareCalls;
        private int publishCalls;
        private UUID lastExecutionId;

        @Override
        public void stage(final StagingBatch batch) {
            throw new UnsupportedOperationException("Staging não pertence a este teste.");
        }

        @Override
        public void prepareCandidateSet(final ContractPromotionPermit contractPermit) {
            prepareCalls++;
            lastExecutionId = contractPermit.executionId();
        }

        @Override
        public StagingPublicationResult applyReconcileAndPublish(
                final ContractPromotionPermit contractPermit,
                final DataQualityPromotionPermit dataQualityPermit) {
            publishCalls++;
            lastExecutionId = contractPermit.executionId();
            return null;
        }
    }
}
