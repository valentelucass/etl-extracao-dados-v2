package br.com.esl.etl.v2.plataforma.fonte.graphql;

import br.com.esl.etl.v2.plataforma.controle.ControlPlane;
import br.com.esl.etl.v2.plataforma.controle.ControlPlanePage;
import br.com.esl.etl.v2.plataforma.controle.ControlPlanePageTerminality;
import java.util.Objects;

/** Persiste somente contagens e terminalidade local; cursor, payload e chaves não atravessam. */
public final class ControlPlaneGraphQlExtractionAudit implements GraphQlExtractionAudit {

    private final ControlPlane controlPlane;

    public ControlPlaneGraphQlExtractionAudit(final ControlPlane controlPlane) {
        this.controlPlane =
                Objects.requireNonNull(controlPlane, "O control plane GraphQL é obrigatório.");
    }

    @Override
    public void executionStarted(final ExecutionStarted event) {
        Objects.requireNonNull(event, "O início GraphQL é obrigatório.");
    }

    @Override
    public void pageRead(final PageRead event) {
        final PageRead required = Objects.requireNonNull(event, "A página GraphQL é obrigatória.");
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
    }

    @Override
    public void executionCompleted(final GraphQlExtractionResult result) {
        Objects.requireNonNull(result, "A conclusão GraphQL é obrigatória.");
    }

    @Override
    public void executionFailed(final ExecutionFailed event) {
        Objects.requireNonNull(event, "A falha GraphQL é obrigatória.");
    }
}
