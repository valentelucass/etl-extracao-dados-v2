package br.com.esl.etl.v2.plataforma.orquestracao;

import br.com.esl.etl.v2.plataforma.contrato.ContractDriftException;
import br.com.esl.etl.v2.plataforma.contrato.ContractPromotionPermit;
import br.com.esl.etl.v2.plataforma.contrato.ContractSourceKind;
import br.com.esl.etl.v2.plataforma.contrato.SourceDataEffect;
import br.com.esl.etl.v2.plataforma.controle.ControlPlane;
import br.com.esl.etl.v2.plataforma.controle.ControlPlaneStart;
import br.com.esl.etl.v2.plataforma.controle.ControlPlaneTransition;
import br.com.esl.etl.v2.plataforma.controle.ExecutionMode;
import br.com.esl.etl.v2.plataforma.controle.ExecutionState;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.ControlPlaneDataExportExtractionAudit;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportExtractionAudit;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportExtractionResult;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportTemplate;
import br.com.esl.etl.v2.plataforma.fonte.graphql.ControlPlaneGraphQlExtractionAudit;
import br.com.esl.etl.v2.plataforma.fonte.graphql.GraphQlExtractionAudit;
import br.com.esl.etl.v2.plataforma.fonte.graphql.GraphQlExtractionResult;
import br.com.esl.etl.v2.plataforma.fonte.graphql.GraphQlReadOperation;
import br.com.esl.etl.v2.plataforma.persistencia.staging.ShadowPromotionGateway;
import br.com.esl.etl.v2.plataforma.persistencia.staging.StagingPublicationResult;
import br.com.esl.etl.v2.plataforma.qualidade.DataQualityEvaluationException;
import br.com.esl.etl.v2.plataforma.qualidade.DataQualityEvaluationRequest;
import br.com.esl.etl.v2.plataforma.qualidade.DataQualityPolicyReference;
import br.com.esl.etl.v2.plataforma.qualidade.DataQualityPromotionPermit;
import br.com.esl.etl.v2.plataforma.qualidade.FailClosedDataQualityEngine;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken;
import br.com.esl.etl.v2.plataforma.resiliencia.FailureKind;
import br.com.esl.etl.v2.plataforma.resiliencia.ResilienceCancelledException;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CancellationException;

/** Uma ocorrência local, serial e evidence-bound; não é capability de autorização operacional. */
public final class RuntimeExecutionSession {
    private final RuntimeExecutionPlanItem item;
    private final ControlPlaneStart start;
    private final ControlPlane control;
    private final Clock clock;
    private final CancellationToken cancellation;
    private Instant lastHeartbeat;
    private ExecutionState state = ExecutionState.EXTRACTING;
    private Phase phase = Phase.INGESTION;
    private boolean uncertain;
    private ControlPlaneDataExportExtractionAudit audit;
    private ControlPlaneGraphQlExtractionAudit graphQlAudit;
    private ContractPromotionPermit graphQlSeal;
    private DataQualityPolicyReference graphQlPolicy;
    private ContractPromotionPermit contract;
    private DataQualityPromotionPermit quality;
    private ShadowPromotionGateway promotion;
    private FailClosedDataQualityEngine qualityEngine;
    private DataQualityPolicyReference qualityPolicy;
    private StagingPublicationResult publication;
    private RuntimeException failureCause;
    private RuntimeRecoveryPort durable;
    private br.com.esl.etl.v2.plataforma.controle.ImmutableFingerprint planFingerprint;

    RuntimeExecutionSession(
            final RuntimeExecutionPlanItem item,
            final ControlPlaneStart start,
            final ControlPlane control,
            final Clock clock,
            final CancellationToken cancellation) {
        this.item = Objects.requireNonNull(item);
        this.start = Objects.requireNonNull(start);
        this.control = Objects.requireNonNull(control);
        this.clock = Objects.requireNonNull(clock);
        this.cancellation = Objects.requireNonNull(cancellation);
        lastHeartbeat = start.startedAt();
    }

    public ControlPlaneStart start() {
        return start;
    }

    public Clock clock() {
        return clock;
    }

    void durableRecovery(
            final RuntimeRecoveryPort port,
            final br.com.esl.etl.v2.plataforma.controle.ImmutableFingerprint plan) {
        durable = port;
        planFingerprint = plan;
    }

    /**
     * O recibo nasce depois do guard e da auditoria, antes das transições sujeitas a ack perdido.
     */
    public void sealTraversal(
            final DataExportExtractionResult result,
            final ContractPromotionPermit permit,
            final DataQualityPolicyReference policy) {
        if (audit == null || state != ExecutionState.EXTRACTING || phase != Phase.INGESTION) {
            throw new IllegalStateException("RUNTIME_EXTRACTION_EVIDENCE_REQUIRED");
        }
        audit.verifyCompletion(result);
        verifyPermit(permit);
        if (durable != null) {
            cancellation().throwIfCancellationRequested();
            uncertain = true;
            durable.seal(start, planFingerprint, permit, policy, cancellation);
            uncertain = false;
        }
    }

    public void sealTraversal(
            final GraphQlExtractionResult result,
            final ContractPromotionPermit permit,
            final DataQualityPolicyReference policy) {
        verifyGraphQlCompletion(result);
        verifyPermit(permit);
        Objects.requireNonNull(policy);
        if (graphQlSeal != null) {
            throw new IllegalStateException("RUNTIME_TRAVERSAL_ALREADY_SEALED");
        }
        cancellation().throwIfCancellationRequested();
        // Users keeps the audited traversal in memory until candidate DQ passes.
        // A partial attempt without the later durable seal requires readback/replay.
        graphQlSeal = permit;
        graphQlPolicy = policy;
    }

    RuntimeExecutionResult recovered(final RuntimeRecoverySnapshot snapshot) {
        if (snapshot.publication().isPresent()) {
            verifyPublication(snapshot.publication().orElseThrow());
            publication = snapshot.publication().orElseThrow();
            state = ExecutionState.PUBLISHED;
            phase = Phase.FINISHED;
            return result();
        }
        if (snapshot.reason() == RuntimeRecoverySnapshot.Reason.TERMINAL) {
            state = snapshot.state().orElseThrow();
            return switch (state) {
                case CANCELLED ->
                        terminal(RuntimeExecutionResult.Status.CANCELLED, FailureKind.CANCELLATION);
                case BLOCKED ->
                        terminal(
                                RuntimeExecutionResult.Status.BLOCKED,
                                FailureKind.REQUIRED_DEPENDENCY_FAILED);
                default -> terminal(RuntimeExecutionResult.Status.FAILED, FailureKind.SQL_FAILURE);
            };
        }
        // Recusa de recuperação não grava um terminal nem infere ausência de commit.
        uncertain = true;
        return failed(new IllegalStateException("RUNTIME_RECOVERY_" + snapshot.reason()));
    }

    void verifyLease() {
        final Instant now = clock.instant();
        control.heartbeat(start.executionId(), now, start.leaseDuration());
        lastHeartbeat = now;
    }

    /** Renovação cooperativa limitada a uma chamada por terço da lease, sem thread auxiliar. */
    public CancellationToken cancellation() {
        return () -> {
            if (cancellation.isCancellationRequested()) {
                return true;
            }
            if (publication == null) {
                final Instant now = clock.instant();
                if (now.isBefore(lastHeartbeat)) {
                    throw new IllegalStateException("RUNTIME_CLOCK_REGRESSED");
                }
                if (!now.isBefore(lastHeartbeat.plus(start.leaseDuration().dividedBy(3)))) {
                    control.heartbeat(start.executionId(), now, start.leaseDuration());
                    lastHeartbeat = now;
                }
            }
            return false;
        };
    }

    public DataExportExtractionAudit extractionAudit(final DataExportTemplate template) {
        if (audit != null
                || graphQlAudit != null
                || phase != Phase.INGESTION
                || !start.partition()
                        .entity()
                        .equals(template.name().toLowerCase(java.util.Locale.ROOT))) {
            throw new IllegalStateException("RUNTIME_AUDIT_BINDING_INVALID");
        }
        audit = new ControlPlaneDataExportExtractionAudit(control, start.executionId(), template);
        return audit;
    }

    public GraphQlExtractionAudit extractionAudit(final GraphQlReadOperation operation) {
        if (audit != null
                || graphQlAudit != null
                || phase != Phase.INGESTION
                || state != ExecutionState.EXTRACTING
                || operation != GraphQlReadOperation.USERS_SNAPSHOT
                || !item.definition().sourceKind().equals("GRAPHQL")
                || (start.partition().mode() != ExecutionMode.BACKFILL
                        && start.partition().mode() != ExecutionMode.REPLAY)
                || !start.windowStrategy().equals("FULL")
                || !start.partition().entity().equals("usuarios")) {
            throw new IllegalStateException("RUNTIME_AUDIT_BINDING_INVALID");
        }
        graphQlAudit =
                new ControlPlaneGraphQlExtractionAudit(control, start.executionId(), operation);
        return graphQlAudit;
    }

    private void verifyGraphQlCompletion(final GraphQlExtractionResult result) {
        if (graphQlAudit == null
                || phase != Phase.INGESTION
                || state != ExecutionState.EXTRACTING) {
            throw new IllegalStateException("RUNTIME_EXTRACTION_EVIDENCE_REQUIRED");
        }
        graphQlAudit.verifyCompletion(result);
    }

    public void staged(final GraphQlExtractionResult result) {
        verifyGraphQlCompletion(result);
        if (graphQlSeal == null) {
            throw new IllegalStateException("RUNTIME_TRAVERSAL_SEAL_REQUIRED");
        }
        cancellation().throwIfCancellationRequested();
        transition(ExecutionState.EXTRACTED, "TRAVERSAL_AUDITED");
        cancellation().throwIfCancellationRequested();
        transition(ExecutionState.STAGED, "STAGING_COMPLETED");
    }

    public void staged(final DataExportExtractionResult result) {
        if (audit == null || phase != Phase.INGESTION || state != ExecutionState.EXTRACTING) {
            throw new IllegalStateException("RUNTIME_EXTRACTION_EVIDENCE_REQUIRED");
        }
        audit.verifyCompletion(result);
        cancellation().throwIfCancellationRequested();
        transition(ExecutionState.EXTRACTED, "TRAVERSAL_AUDITED");
        cancellation().throwIfCancellationRequested();
        transition(ExecutionState.STAGED, "STAGING_COMPLETED");
    }

    public void promote(
            final ContractPromotionPermit permit,
            final ShadowPromotionGateway gateway,
            final FailClosedDataQualityEngine engine,
            final DataQualityPolicyReference policy) {
        if (state != ExecutionState.STAGED || phase != Phase.INGESTION) {
            throw new IllegalStateException("RUNTIME_STAGING_EVIDENCE_REQUIRED");
        }
        verifyPermit(permit);
        if (graphQlAudit != null && (graphQlSeal != permit || !graphQlPolicy.equals(policy))) {
            throw new IllegalArgumentException("RUNTIME_GRAPHQL_SEAL_BINDING_INVALID");
        }
        contract = permit;
        promotion = Objects.requireNonNull(gateway);
        qualityEngine = Objects.requireNonNull(engine);
        qualityPolicy = Objects.requireNonNull(policy);
        phase = Phase.PREPARING;
        finishPromotion();
    }

    private void finishPromotion() {
        if (phase == Phase.PREPARING) {
            cancellation().throwIfCancellationRequested();
            uncertain = true;
            promotion.prepareCandidateSet(contract, cancellation());
            uncertain = false;
            state = ExecutionState.PROMOTED;
            phase = Phase.QUALITY;
        }
        if (phase == Phase.QUALITY) {
            cancellation().throwIfCancellationRequested();
            quality =
                    qualityEngine.evaluate(
                            new DataQualityEvaluationRequest(start.executionId(), qualityPolicy));
            cancellation().throwIfCancellationRequested();
            phase = graphQlAudit == null ? Phase.PUBLISHING : Phase.SEALING;
        }
        if (phase == Phase.SEALING) {
            cancellation().throwIfCancellationRequested();
            if (durable != null) {
                uncertain = true;
                durable.seal(start, planFingerprint, graphQlSeal, graphQlPolicy, cancellation);
                uncertain = false;
            }
            phase = Phase.PUBLISHING;
        }
        // Em recuperação não renova lease: um commit anterior já pode tê-la liberado.
        cancellation.throwIfCancellationRequested();
        uncertain = true;
        final StagingPublicationResult result =
                promotion.applyReconcileAndPublish(contract, quality, cancellation);
        verifyPublication(result);
        publication = result;
        uncertain = false;
        state = ExecutionState.PUBLISHED;
        phase = Phase.FINISHED;
        // Cancelamento posterior não desfaz um recibo confirmado.
    }

    /**
     * Retoma somente o mesmo comando idempotente e os permits vivos, sem reextração/replanejamento.
     */
    public synchronized RuntimeExecutionResult recoverPromotion() {
        if (graphQlAudit != null
                || !uncertain
                || contract == null
                || (phase != Phase.PREPARING && phase != Phase.PUBLISHING)) {
            throw new IllegalStateException("RUNTIME_DURABLE_READBACK_REQUIRED");
        }
        try {
            finishPromotion();
            return result();
        } catch (final RuntimeException exception) {
            return failed(exception);
        }
    }

    private void verifyPermit(final ContractPromotionPermit permit) {
        Objects.requireNonNull(permit);
        if (!start.executionId().equals(permit.executionId())
                || !start.contract().equals(permit.contractFingerprint())
                || !start.configuration().equals(permit.configurationFingerprint())
                || permit.dataEffect() != SourceDataEffect.SHADOW_UPSERT) {
            throw new IllegalArgumentException("RUNTIME_PROMOTION_BINDING_INVALID");
        }
        if (graphQlAudit != null
                && (permit.sourceKind() != ContractSourceKind.GRAPHQL
                        || !permit.documentReference()
                                .equals(GraphQlReadOperation.USERS_SNAPSHOT.documentReference()))) {
            throw new IllegalArgumentException("RUNTIME_GRAPHQL_PROMOTION_BINDING_INVALID");
        }
    }

    private void verifyPublication(final StagingPublicationResult result) {
        if (result == null
                || !start.executionId().equals(result.executionId())
                || (start.partition().mode() == ExecutionMode.INCREMENTAL)
                        != result.incrementalFrontierAfter().isPresent()) {
            throw new IllegalStateException("RUNTIME_PUBLICATION_EVIDENCE_INVALID");
        }
    }

    void markStartUncertain() {
        uncertain = true;
    }

    void markStarted() {
        uncertain = false;
    }

    RuntimeExecutionResult blocked() {
        try {
            transition(ExecutionState.BLOCKED, "REQUIRED_DEPENDENCY_FAILED");
            return terminal(
                    RuntimeExecutionResult.Status.BLOCKED, FailureKind.REQUIRED_DEPENDENCY_FAILED);
        } catch (final RuntimeException exception) {
            return failed(exception);
        }
    }

    RuntimeExecutionResult failed(final RuntimeException exception) {
        failureCause = Objects.requireNonNull(exception);
        if (publication != null) {
            return result();
        }
        final boolean cancelled =
                exception instanceof ResilienceCancelledException
                        || exception instanceof CancellationException;
        final FailureKind kind = classify(exception);
        if (!uncertain) {
            try {
                transition(
                        cancelled ? ExecutionState.CANCELLED : ExecutionState.FAILED,
                        cancelled ? "CANCELLATION_REQUESTED" : "RUNTIME_WORKLOAD_FAILED");
            } catch (final RuntimeException persistenceFailure) {
                exception.addSuppressed(persistenceFailure);
                uncertain = true;
            }
        }
        if (uncertain) {
            return new RuntimeExecutionResult(
                    item.definition().id(),
                    RuntimeExecutionResult.Status.RECOVERY_REQUIRED,
                    Optional.empty(),
                    Optional.of(FailureKind.SQL_FAILURE),
                    Optional.of(this));
        }
        return terminal(
                cancelled
                        ? RuntimeExecutionResult.Status.CANCELLED
                        : RuntimeExecutionResult.Status.FAILED,
                kind);
    }

    RuntimeExecutionResult result() {
        if (publication == null) {
            return failed(new IllegalStateException("RUNTIME_PUBLICATION_EVIDENCE_REQUIRED"));
        }
        return new RuntimeExecutionResult(
                item.definition().id(),
                RuntimeExecutionResult.Status.PUBLISHED,
                Optional.of(publication),
                Optional.empty(),
                Optional.empty());
    }

    private static FailureKind classify(final RuntimeException exception) {
        if (exception instanceof ResilienceCancelledException
                || exception instanceof CancellationException) {
            return FailureKind.CANCELLATION;
        }
        if (exception instanceof ContractDriftException) {
            return FailureKind.CONTRACT_DRIFT;
        }
        if (exception instanceof DataQualityEvaluationException) {
            return FailureKind.CRITICAL_DATA_QUALITY;
        }
        if (exception instanceof IllegalArgumentException) {
            return FailureKind.CONFIGURATION;
        }
        return FailureKind.SQL_FAILURE;
    }

    /** Causa retida para diagnóstico interno; nunca emitida pelo resultado sanitizado. */
    public Optional<RuntimeException> failureCause() {
        return Optional.ofNullable(failureCause);
    }

    private RuntimeExecutionResult terminal(
            final RuntimeExecutionResult.Status status, final FailureKind kind) {
        phase = Phase.FINISHED;
        return new RuntimeExecutionResult(
                item.definition().id(),
                status,
                Optional.empty(),
                Optional.of(kind),
                Optional.empty());
    }

    private void transition(final ExecutionState next, final String reason) {
        uncertain = true;
        control.transition(
                new ControlPlaneTransition(
                        start.executionId(), state, next, reason, clock.instant()));
        uncertain = false;
        state = next;
    }

    @Override
    public String toString() {
        return "RuntimeExecutionSession[state=" + state + ", uncertain=" + uncertain + "]";
    }

    private enum Phase {
        INGESTION,
        PREPARING,
        QUALITY,
        SEALING,
        PUBLISHING,
        FINISHED
    }
}
