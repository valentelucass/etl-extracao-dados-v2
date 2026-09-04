package br.com.esl.etl.v2.plataforma.contrato;

import br.com.esl.etl.v2.plataforma.controle.ControlPlaneStart;
import br.com.esl.etl.v2.plataforma.controle.ExecutionMode;
import br.com.esl.etl.v2.plataforma.controle.ExecutionPartitionKey;
import br.com.esl.etl.v2.plataforma.controle.ImmutableFingerprint;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Fixtures estruturais sintéticas compartilhadas pelos testes do gate de promoção. */
public final class ContractTestSupport {

    private ContractTestSupport() {}

    public static ContractMetadata metadata() {
        return new ContractMetadata(
                List.of(
                        new ContractMetadata.Element(
                                ContractMetadata.ElementKind.DATA_FIELD,
                                "id",
                                ContractMetadata.DeclaredType.STRING),
                        new ContractMetadata.Element(
                                ContractMetadata.ElementKind.DATA_FIELD,
                                "status",
                                ContractMetadata.DeclaredType.STRING),
                        new ContractMetadata.Element(
                                ContractMetadata.ElementKind.DATA_FILTER,
                                "request_date",
                                ContractMetadata.DeclaredType.DATE)),
                Optional.empty());
    }

    public static ContractResponse response() {
        return new ContractResponse(
                "/data",
                ContractResponse.Cardinality.ARRAY,
                ContractResponse.ObservationState.POPULATED,
                "/id",
                List.of(
                        new ContractResponse.Field(
                                ContractResponse.FieldScope.ENVELOPE,
                                "/data",
                                ContractResponse.Cardinality.ARRAY,
                                ContractResponse.Presence.REQUIRED,
                                false,
                                List.of(ContractResponse.JsonType.ARRAY)),
                        field(
                                "/id",
                                ContractResponse.Presence.REQUIRED,
                                false,
                                ContractResponse.JsonType.STRING),
                        field(
                                "/status",
                                ContractResponse.Presence.REQUIRED,
                                false,
                                ContractResponse.JsonType.STRING),
                        field(
                                "/note",
                                ContractResponse.Presence.OPTIONAL,
                                true,
                                ContractResponse.JsonType.STRING)));
    }

    public static SourceContractRelease release() {
        return SourceContractRelease.create(
                ContractSourceKind.DATA_EXPORT,
                "dataexport-synthetic",
                "2026-08-30.1",
                metadata(),
                response());
    }

    public static ContractCompatibilityPolicy policy(final SourceContractRelease release) {
        return ContractCompatibilityPolicy.create(
                "policy-1", release.contractFingerprint(), List.of());
    }

    public static ContractPromotionPermit promotionPermit(final UUID executionId) {
        return promotionPermit(
                executionId,
                SourceDataEffect.SHADOW_UPSERT,
                SourceCompletenessStatus.PROVEN_COMPLETE);
    }

    public static ContractPromotionPermit promotionPermit(
            final UUID executionId,
            final SourceDataEffect dataEffect,
            final SourceCompletenessStatus completenessStatus) {
        final SourceContractRelease release = release();
        final ContractCompatibilityPolicy policy = policy(release);
        final ContractExecutionBinding binding =
                ContractExecutionBinding.create(
                        executionId,
                        release,
                        policy,
                        new ImmutableFingerprint("runtime-1", "d".repeat(64)));
        final ContractRunGuard guard =
                new ContractRunGuard(
                        binding, release, policy, controlPlaneStart(binding), alert -> {});
        guard.bindCompletenessStatus(completenessStatus);
        guard.validateMetadata(release.metadata());
        guard.observeDataExportResponse(1, release.response());
        guard.observeDataExportResponse(2, emptyResponse(release));
        guard.markDataExportTraversalTerminal(2);
        guard.markDataExportCompletionAuditSucceeded(2);
        return guard.complete(dataEffect);
    }

    public static ContractExecutionContext executionContext(final UUID executionId) {
        final SourceContractRelease release = release();
        final ContractCompatibilityPolicy policy = policy(release);
        final ContractExecutionBinding binding =
                ContractExecutionBinding.create(
                        executionId,
                        release,
                        policy,
                        new ImmutableFingerprint("runtime-1", "d".repeat(64)));
        return new ContractRunGuard(
                        binding, release, policy, controlPlaneStart(binding), alert -> {})
                .executionContext();
    }

    public static ControlPlaneStart controlPlaneStart(final ContractExecutionBinding binding) {
        final Instant start = Instant.parse("2026-08-30T00:00:00Z");
        return new ControlPlaneStart(
                binding.executionId(),
                UUID.fromString("00000000-0000-0000-0000-000000000099"),
                new ExecutionPartitionKey(
                        "LOCAL_SHADOW",
                        "synthetic-source",
                        "singleton",
                        "synthetic-entity",
                        ExecutionMode.INCREMENTAL,
                        start,
                        start.plusSeconds(60)),
                "interval",
                binding.contractFingerprint(),
                binding.configurationFingerprint(),
                "synthetic-idempotency-key",
                Optional.empty(),
                Duration.ofMinutes(1),
                start);
    }

    public static ContractResponse emptyResponse(final SourceContractRelease release) {
        return new ContractResponse(
                release.response().recordRoot(),
                release.response().rootCardinality(),
                ContractResponse.ObservationState.EMPTY,
                release.response().keyPath(),
                release.response().fields().stream()
                        .filter(field -> field.scope() == ContractResponse.FieldScope.ENVELOPE)
                        .toList());
    }

    public static ContractResponse.Field field(
            final String path,
            final ContractResponse.Presence presence,
            final boolean nullable,
            final ContractResponse.JsonType type) {
        return new ContractResponse.Field(
                path, ContractResponse.Cardinality.SCALAR, presence, nullable, List.of(type));
    }
}
