package br.com.esl.etl.v2.plataforma.fonte.graphql;

import br.com.esl.etl.v2.plataforma.controle.ControlPlane;
import br.com.esl.etl.v2.plataforma.controle.ControlPlanePage;
import br.com.esl.etl.v2.plataforma.controle.ControlPlanePageTerminality;
import java.util.Objects;
import java.util.UUID;

/** Persiste somente contagens e terminalidade local; cursor, payload e chaves não atravessam. */
public final class ControlPlaneGraphQlExtractionAudit implements GraphQlExtractionAudit {

    private final ControlPlane controlPlane;
    private final UUID expectedExecution;
    private final GraphQlReadOperation expectedOperation;
    private ExecutionStarted started;
    private java.time.Instant lastAt;
    private int pages;
    private long nodes;
    private boolean terminal;
    private boolean failed;
    private GraphQlExtractionResult completed;

    public ControlPlaneGraphQlExtractionAudit(final ControlPlane controlPlane) {
        this(controlPlane, null, null);
    }

    public ControlPlaneGraphQlExtractionAudit(
            final ControlPlane controlPlane,
            final UUID executionId,
            final GraphQlReadOperation operation) {
        this.controlPlane =
                Objects.requireNonNull(controlPlane, "O control plane GraphQL é obrigatório.");
        if ((executionId == null) != (operation == null)) {
            throw new IllegalArgumentException("GRAPHQL_AUDIT_BINDING_REQUIRED");
        }
        expectedExecution = executionId;
        expectedOperation = operation;
    }

    @Override
    public void executionStarted(final ExecutionStarted event) {
        Objects.requireNonNull(event, "O início GraphQL é obrigatório.");
        require(started == null && !failed && completed == null);
        require(
                expectedExecution == null
                        || expectedExecution.equals(event.executionId())
                                && expectedOperation == event.operation());
        started = event;
        lastAt = event.at();
    }

    @Override
    public void pageRead(final PageRead event) {
        final PageRead required = Objects.requireNonNull(event, "A página GraphQL é obrigatória.");
        require(started != null && !failed && completed == null && !terminal);
        require(
                started.executionId().equals(required.executionId())
                        && started.operation() == required.operation()
                        && required.requestedPageSize() == started.requestedPageSize()
                        && required.pageNumber() == pages + 1
                        && required.pageNumber() <= started.maximumPages()
                        && required.nodeCount() > 0
                        && required.nodeCount() <= started.requestedPageSize()
                        && nodes + required.nodeCount() <= started.maximumNodes()
                        && !required.at().isBefore(lastAt)
                        && (!required.hasNextPage()
                                || required.pageNumber() < started.maximumPages()));
        // An acknowledgement lost during persistence cannot later authorize completion.
        failed = true;
        controlPlane.recordPage(
                new ControlPlanePage(
                        required.executionId(),
                        required.pageNumber(),
                        1,
                        required.requestedPageSize(),
                        required.nodeCount(),
                        required.distinctRootKeys(),
                        required.responseBytes(),
                        required.hasNextPage()
                                ? ControlPlanePageTerminality.NONE
                                : ControlPlanePageTerminality.GRAPHQL_PAGE_INFO,
                        required.at()));
        pages++;
        nodes += required.nodeCount();
        terminal = !required.hasNextPage();
        lastAt = required.at();
        failed = false;
    }

    @Override
    public void executionCompleted(final GraphQlExtractionResult result) {
        Objects.requireNonNull(result, "A conclusão GraphQL é obrigatória.");
        require(started != null && !failed && completed == null && terminal);
        require(
                started.executionId().equals(result.executionId())
                        && started.operation() == result.operation()
                        && started.at().equals(result.startedAt())
                        && !result.completedAt().isBefore(lastAt)
                        && pages == result.pagesFetched()
                        && nodes == result.nodesDelivered()
                        && result.traversalVerification()
                                == GraphQlTraversalVerification
                                        .LOCAL_PAGE_INFO_TERMINAL_UNVERIFIED);
        completed = result;
    }

    @Override
    public void executionFailed(final ExecutionFailed event) {
        Objects.requireNonNull(event, "A falha GraphQL é obrigatória.");
        failed = true;
        completed = null;
    }

    public void verifyCompletion(final GraphQlExtractionResult result) {
        require(!failed && completed != null && completed.equals(Objects.requireNonNull(result)));
    }

    private void require(final boolean valid) {
        if (!valid) {
            failed = true;
            completed = null;
            throw new IllegalStateException("GRAPHQL_AUDIT_EVIDENCE_INVALID");
        }
    }
}
