package br.com.esl.etl.v2.plataforma.fonte.graphql;

import java.util.Objects;
import java.util.Optional;

/** Requisição imutável; somente o streamer pode avançar o cursor na mesma execução. */
public final class GraphQlPageRequest {

    private final GraphQlReadOperation operation;
    private final GraphQlQueryParameters parameters;
    private final int pageSize;
    private final Optional<GraphQlCursor> after;

    private GraphQlPageRequest(
            final GraphQlReadOperation operation,
            final GraphQlQueryParameters parameters,
            final int pageSize,
            final Optional<GraphQlCursor> after) {
        this.operation = Objects.requireNonNull(operation, "A operação GraphQL é obrigatória.");
        this.parameters =
                Objects.requireNonNull(parameters, "Os parâmetros GraphQL são obrigatórios.");
        if (parameters.operation() != operation) {
            throw new IllegalArgumentException("Os parâmetros não correspondem à operação.");
        }
        if (pageSize < 1 || pageSize > operation.maximumPageSize()) {
            throw new IllegalArgumentException("O tamanho da página GraphQL está fora do limite.");
        }
        this.pageSize = pageSize;
        this.after = Objects.requireNonNull(after, "O cursor opcional é obrigatório.");
    }

    public static GraphQlPageRequest initial(
            final GraphQlReadOperation operation,
            final GraphQlQueryParameters parameters,
            final int pageSize) {
        GraphQlTransitionalFieldCatalog.validate();
        return new GraphQlPageRequest(operation, parameters, pageSize, Optional.empty());
    }

    public GraphQlReadOperation operation() {
        return operation;
    }

    public GraphQlQueryParameters parameters() {
        return parameters;
    }

    public int pageSize() {
        return pageSize;
    }

    public Optional<GraphQlCursor> after() {
        return after;
    }

    GraphQlPageRequest next(final GraphQlCursor cursor) {
        return new GraphQlPageRequest(operation, parameters, pageSize, Optional.of(cursor));
    }

    @Override
    public String toString() {
        return "GraphQlPageRequest[operation="
                + operation
                + ", pageSize="
                + pageSize
                + ", after=<redacted>]";
    }
}
