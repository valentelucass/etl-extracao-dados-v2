package br.com.esl.etl.v2.plataforma.contrato;

import br.com.esl.etl.v2.plataforma.controle.ControlPlaneStart;
import br.com.esl.etl.v2.plataforma.controle.ImmutableFingerprint;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/** Gate O(1) por ocorrência: valida metadata e cada página sem acumular a travessia. */
public final class ContractRunGuard {

    private final ContractExecutionBinding binding;
    private final ContractResponse baselineResponse;
    private final ContractValidator validator;
    private final ContractResponsePathBoundary responsePathBoundary;
    private final ContractAlertSink alertSink;
    private boolean metadataValidated;
    private boolean populatedResponseObserved;
    private final Set<ImmutableFingerprint> emittedAlertSignatures = new HashSet<>();
    private boolean traversalTerminalObserved;
    private boolean dataExportCompletionAuditPending;
    private boolean dataExportCompletionAuditSucceeded;
    private boolean graphQlAdapterTraversal;
    private boolean graphQlCompletionAuditPending;
    private boolean graphQlCompletionAuditSucceeded;
    private boolean sourceStateBlocksPromotion;
    private SourceCompletenessStatus sourceCompletenessStatus;
    private boolean alertObserved;
    private long responsePages;
    private ContractResponse.ObservationState lastResponseState;
    private Integer lastDataExportPage;
    private State state = State.OPEN;

    public ContractRunGuard(
            final ContractExecutionBinding binding,
            final SourceContractRelease release,
            final ContractCompatibilityPolicy policy,
            final ControlPlaneStart controlPlaneStart,
            final ContractAlertSink alertSink) {
        this.binding = Objects.requireNonNull(binding, "O binding da ocorrência é obrigatório.");
        Objects.requireNonNull(release, "O release de contrato é obrigatório.");
        Objects.requireNonNull(policy, "A política de contrato é obrigatória.");
        if (!binding.matches(release, policy)) {
            throw new ContractDriftException(
                    ContractDriftException.Reason.EXECUTION_BINDING_MISMATCH, 0, 0);
        }
        baselineResponse = release.response();
        binding.verify(
                Objects.requireNonNull(controlPlaneStart, "O início da ocorrência é obrigatório."));
        validator = new ContractValidator(release, policy);
        responsePathBoundary = ContractResponsePathBoundary.forRuntime(release, policy);
        this.alertSink = Objects.requireNonNull(alertSink, "O sink de alerta é obrigatório.");
    }

    public synchronized ContractValidationResult validateMetadata(final ContractMetadata observed) {
        requireOpen();
        try {
            final ContractValidationResult result = validator.validateMetadata(observed);
            emitAlerts(result);
            metadataValidated = true;
            return result;
        } catch (final RuntimeException exception) {
            state = State.FAILED;
            throw exception;
        }
    }

    synchronized ContractValidationResult observeResponse(final ContractResponse observed) {
        if (binding.sourceKind() == ContractSourceKind.DATA_EXPORT) {
            state = State.FAILED;
            throw new ContractDriftException(
                    ContractDriftException.Reason.TRAVERSAL_TERMINAL_EVIDENCE_REQUIRED, 0, 0);
        }
        return observeResponseInternal(observed);
    }

    /**
     * Observa uma página GraphQL pelo boundary produtivo, sem expor o guard genérico ao adapter.
     */
    public synchronized ContractValidationResult observeGraphQlResponse(
            final ContractResponse observed) {
        if (binding.sourceKind() != ContractSourceKind.GRAPHQL) {
            state = State.FAILED;
            throw new ContractDriftException(
                    ContractDriftException.Reason.TRAVERSAL_TERMINAL_EVIDENCE_REQUIRED, 0, 0);
        }
        final ContractValidationResult result = observeResponseInternal(observed);
        graphQlAdapterTraversal = true;
        return result;
    }

    /** Preflight sem mutação: impede chamada remota quando a sequência já é inválida. */
    public synchronized void verifyNextDataExportPage(final int page) {
        requireOpen();
        if (!metadataValidated) {
            state = State.FAILED;
            throw new ContractDriftException(
                    ContractDriftException.Reason.METADATA_EVIDENCE_REQUIRED, 0, 0);
        }
        if (binding.sourceKind() != ContractSourceKind.DATA_EXPORT
                || page < 1
                || lastDataExportPage == null && page != 1
                || lastDataExportPage != null
                        && (lastDataExportPage == Integer.MAX_VALUE
                                || page != lastDataExportPage + 1)) {
            state = State.FAILED;
            throw new ContractDriftException(
                    ContractDriftException.Reason.TRAVERSAL_TERMINAL_EVIDENCE_REQUIRED, 0, 0);
        }
    }

    /** Observa uma página Data Export e prova que a travessia não saltou nem repetiu páginas. */
    public synchronized ContractValidationResult observeDataExportResponse(
            final int page, final ContractResponse observed) {
        verifyNextDataExportPage(page);
        final ContractValidationResult result = observeResponseInternal(observed);
        lastDataExportPage = page;
        return result;
    }

    private ContractValidationResult observeResponseInternal(final ContractResponse observed) {
        requireOpen();
        if (!metadataValidated) {
            state = State.FAILED;
            throw new ContractDriftException(
                    ContractDriftException.Reason.METADATA_EVIDENCE_REQUIRED, 0, 0);
        }
        if (traversalTerminalObserved) {
            state = State.FAILED;
            throw new ContractDriftException(
                    ContractDriftException.Reason.VALIDATION_ALREADY_TERMINAL, 0, 0);
        }
        if (lastResponseState == ContractResponse.ObservationState.EMPTY) {
            state = State.FAILED;
            throw new ContractDriftException(
                    ContractDriftException.Reason.VALIDATION_ALREADY_TERMINAL, 0, 0);
        }
        try {
            final ContractValidationResult result = validator.validateResponse(observed);
            responsePages = Math.incrementExact(responsePages);
            populatedResponseObserved |=
                    observed.observationState() == ContractResponse.ObservationState.POPULATED;
            lastResponseState = observed.observationState();
            emitAlerts(result);
            return result;
        } catch (final RuntimeException exception) {
            state = State.FAILED;
            throw exception;
        }
    }

    synchronized void markTraversalTerminal(final ContractTraversalTerminalEvidence evidence) {
        if (binding.sourceKind() == ContractSourceKind.DATA_EXPORT) {
            state = State.FAILED;
            throw new ContractDriftException(
                    ContractDriftException.Reason.TRAVERSAL_TERMINAL_EVIDENCE_REQUIRED, 0, 0);
        }
        markTraversalTerminalInternal(evidence);
    }

    private void markTraversalTerminalInternal(final ContractTraversalTerminalEvidence evidence) {
        requireOpen();
        final ContractTraversalTerminalEvidence required =
                Objects.requireNonNull(evidence, "A evidência terminal é obrigatória.");
        if (!metadataValidated
                || responsePages == 0
                || required.sourceKind() != binding.sourceKind()) {
            state = State.FAILED;
            throw new ContractDriftException(
                    ContractDriftException.Reason.TRAVERSAL_TERMINAL_EVIDENCE_REQUIRED, 0, 0);
        }
        if (required.mode()
                        == ContractTraversalTerminalEvidence.Mode.DATA_EXPORT_SEQUENTIAL_EMPTY_PAGE
                && lastResponseState != ContractResponse.ObservationState.EMPTY) {
            state = State.FAILED;
            throw new ContractDriftException(
                    ContractDriftException.Reason.TRAVERSAL_TERMINAL_EVIDENCE_REQUIRED, 0, 0);
        }
        traversalTerminalObserved = true;
    }

    synchronized boolean hasDataExportTraversal() {
        return state == State.OPEN && lastDataExportPage != null;
    }

    synchronized void markDataExportTraversalTerminal(final int terminalPage) {
        requireOpen();
        if (binding.sourceKind() != ContractSourceKind.DATA_EXPORT
                || lastDataExportPage == null
                || terminalPage != lastDataExportPage
                || lastResponseState != ContractResponse.ObservationState.EMPTY) {
            state = State.FAILED;
            throw new ContractDriftException(
                    ContractDriftException.Reason.TRAVERSAL_TERMINAL_EVIDENCE_REQUIRED, 0, 0);
        }
        markTraversalTerminalInternal(ContractTraversalTerminalEvidence.dataExportEmptyPage());
        dataExportCompletionAuditPending = true;
    }

    synchronized void markDataExportCompletionAuditSucceeded(final int terminalPage) {
        requireOpen();
        if (!dataExportCompletionAuditPending
                || !traversalTerminalObserved
                || lastDataExportPage == null
                || terminalPage != lastDataExportPage) {
            state = State.FAILED;
            throw new ContractDriftException(
                    ContractDriftException.Reason.TRAVERSAL_TERMINAL_EVIDENCE_REQUIRED, 0, 0);
        }
        dataExportCompletionAuditSucceeded = true;
        dataExportCompletionAuditPending = false;
    }

    synchronized void markGraphQlTraversalTerminal() {
        requireOpen();
        if (binding.sourceKind() != ContractSourceKind.GRAPHQL || !graphQlAdapterTraversal) {
            state = State.FAILED;
            throw new ContractDriftException(
                    ContractDriftException.Reason.TRAVERSAL_TERMINAL_EVIDENCE_REQUIRED, 0, 0);
        }
        markTraversalTerminalInternal(ContractTraversalTerminalEvidence.graphQlPageInfoTerminal());
        graphQlCompletionAuditPending = true;
    }

    synchronized void markGraphQlCompletionAuditSucceeded() {
        requireOpen();
        if (!graphQlCompletionAuditPending || !traversalTerminalObserved) {
            state = State.FAILED;
            throw new ContractDriftException(
                    ContractDriftException.Reason.TRAVERSAL_TERMINAL_EVIDENCE_REQUIRED, 0, 0);
        }
        graphQlCompletionAuditSucceeded = true;
        graphQlCompletionAuditPending = false;
    }

    /** Fecha o caminho atual de staging, que é exclusivamente shadow upsert. */
    public synchronized ContractPromotionPermit complete() {
        return complete(SourceDataEffect.SHADOW_UPSERT);
    }

    public synchronized ContractPromotionPermit complete(final SourceDataEffect dataEffect) {
        requireOpen();
        final SourceDataEffect requiredEffect =
                Objects.requireNonNull(dataEffect, "O efeito de dados é obrigatório.");
        if (sourceCompletenessStatus == null) {
            state = State.FAILED;
            throw new ContractDriftException(
                    ContractDriftException.Reason.EXECUTION_BINDING_MISMATCH, 0, 0);
        }
        if (!metadataValidated) {
            state = State.FAILED;
            throw new ContractDriftException(
                    ContractDriftException.Reason.METADATA_EVIDENCE_REQUIRED, 0, 0);
        }
        if (!populatedResponseObserved) {
            state = State.FAILED;
            throw new ContractDriftException(
                    ContractDriftException.Reason.RESPONSE_EVIDENCE_REQUIRED, 0, 0);
        }
        if (!traversalTerminalObserved) {
            state = State.FAILED;
            throw new ContractDriftException(
                    ContractDriftException.Reason.TRAVERSAL_TERMINAL_EVIDENCE_REQUIRED, 0, 0);
        }
        if (dataExportCompletionAuditPending
                || !dataExportCompletionAuditSucceeded && lastDataExportPage != null) {
            state = State.FAILED;
            throw new ContractDriftException(
                    ContractDriftException.Reason.TRAVERSAL_TERMINAL_EVIDENCE_REQUIRED, 0, 0);
        }
        if (graphQlCompletionAuditPending
                || graphQlAdapterTraversal && !graphQlCompletionAuditSucceeded) {
            state = State.FAILED;
            throw new ContractDriftException(
                    ContractDriftException.Reason.TRAVERSAL_TERMINAL_EVIDENCE_REQUIRED, 0, 0);
        }
        if (sourceStateBlocksPromotion) {
            state = State.FAILED;
            throw new ContractDriftException(
                    ContractDriftException.Reason.SOURCE_STATE_NOT_PROMOTABLE, 0, 0);
        }
        if (!sourceCompletenessStatus.permits(requiredEffect)) {
            state = State.FAILED;
            throw new ContractDriftException(
                    ContractDriftException.Reason.SOURCE_STATE_NOT_PROMOTABLE, 0, 0);
        }
        state = State.COMPLETED;
        return new ContractPromotionPermit(binding, requiredEffect, alertObserved);
    }

    public synchronized long responsePages() {
        return responsePages;
    }

    public synchronized boolean alertObserved() {
        return alertObserved;
    }

    public synchronized void verifySource(
            final ContractSourceKind sourceKind, final String documentReference) {
        requireOpen();
        try {
            binding.verifySource(sourceKind, documentReference);
        } catch (final RuntimeException exception) {
            state = State.FAILED;
            throw exception;
        }
    }

    /**
     * Preflight estrutural e de configuração. Deve ocorrer antes de qualquer delegate capaz de
     * abrir I/O.
     */
    public synchronized void verifyObservationBinding(
            final ImmutableFingerprint runtimeConfigurationFingerprint,
            final ContractObservationLimits observationLimits,
            final ImmutableFingerprint responsePathBoundaryFingerprint,
            final String recordRoot,
            final ContractResponse.Cardinality rootCardinality,
            final String keyPath) {
        requireOpen();
        if (!binding.runtimeConfigurationFingerprint()
                        .equals(
                                Objects.requireNonNull(
                                        runtimeConfigurationFingerprint,
                                        "O fingerprint runtime é obrigatório."))
                || !binding.observationLimits()
                        .equals(
                                Objects.requireNonNull(
                                        observationLimits,
                                        "Os limites de observação são obrigatórios."))
                || !responsePathBoundary
                        .fingerprint()
                        .equals(
                                Objects.requireNonNull(
                                        responsePathBoundaryFingerprint,
                                        "O fingerprint da fronteira é obrigatório."))
                || !baselineResponse.recordRoot().equals(recordRoot)
                || baselineResponse.rootCardinality()
                        != Objects.requireNonNull(
                                rootCardinality, "A cardinalidade da raiz é obrigatória.")
                || !baselineResponse.keyPath().equals(keyPath)) {
            state = State.FAILED;
            throw new ContractDriftException(
                    ContractDriftException.Reason.EXECUTION_BINDING_MISMATCH, 0, 0);
        }
    }

    public synchronized ContractExecutionContext executionContext() {
        requireOpen();
        return new ContractExecutionContext(binding.executionId(), this);
    }

    public synchronized ContractObservationLimits observationLimits() {
        requireOpen();
        return binding.observationLimits();
    }

    /** Fronteira exata do baseline e das adições previamente permitidas para esta ocorrência. */
    public synchronized ContractResponsePathBoundary responsePathBoundary() {
        requireOpen();
        return responsePathBoundary;
    }

    /** Registra uma evidência ausente/inválida encontrada por um adapter antes de fechar o gate. */
    public synchronized void failClosed(final ContractDriftException.Reason reason) {
        requireOpen();
        state = State.FAILED;
        throw new ContractDriftException(
                Objects.requireNonNull(reason, "O motivo é obrigatório."), 0, 0);
    }

    /** Invalida a ocorrência quando um adapter falha antes de produzir evidência estrutural. */
    public synchronized void invalidateEvidence() {
        if (state == State.OPEN) {
            state = State.FAILED;
        }
    }

    /** Registra que o contrato pode ser observado, mas seu estado ainda não autoriza promoção. */
    public synchronized void blockPromotionForSourceState() {
        requireOpen();
        sourceStateBlocksPromotion = true;
    }

    /** Liga a política de completude da fonte antes da primeira observação da ocorrência. */
    public synchronized void bindCompletenessStatus(final SourceCompletenessStatus status) {
        requireOpen();
        final SourceCompletenessStatus required =
                Objects.requireNonNull(status, "O estado de completude é obrigatório.");
        if (metadataValidated || responsePages != 0) {
            state = State.FAILED;
            throw new ContractDriftException(
                    ContractDriftException.Reason.EXECUTION_BINDING_MISMATCH, 0, 0);
        }
        if (sourceCompletenessStatus != null && sourceCompletenessStatus != required) {
            state = State.FAILED;
            throw new ContractDriftException(
                    ContractDriftException.Reason.EXECUTION_BINDING_MISMATCH, 0, 0);
        }
        sourceCompletenessStatus = required;
    }

    private void emitAlerts(final ContractValidationResult result) {
        if (result.status() != ContractValidationResult.Status.ACCEPTED_WITH_ALERT) {
            return;
        }
        for (final ContractChange change : result.diff().changes()) {
            if (emittedAlertSignatures.add(change.signature())) {
                alertSink.compatibleChangeAccepted(
                        new ContractCompatibilityAlert(
                                binding.executionId(),
                                change.component(),
                                change.kind(),
                                change.signature()));
            }
        }
        alertObserved = true;
    }

    private void requireOpen() {
        if (state != State.OPEN) {
            throw new ContractDriftException(
                    ContractDriftException.Reason.VALIDATION_ALREADY_TERMINAL, 0, 0);
        }
    }

    private enum State {
        OPEN,
        FAILED,
        COMPLETED
    }
}
