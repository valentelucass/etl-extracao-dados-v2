package br.com.esl.etl.v2.plataforma.fonte.graphql;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.LocalDate;
import java.util.Objects;

/** Filtros fechados por operação; não aceita mapa ou variável GraphQL arbitrária. */
public final class GraphQlQueryParameters {

    private final GraphQlReadOperation operation;
    private final LocalDate startDate;
    private final LocalDate endDate;

    private GraphQlQueryParameters(
            final GraphQlReadOperation operation,
            final LocalDate startDate,
            final LocalDate endDate) {
        this.operation = Objects.requireNonNull(operation, "A operação GraphQL é obrigatória.");
        this.startDate = startDate;
        this.endDate = endDate;
    }

    public static GraphQlQueryParameters enabledUsers() {
        return new GraphQlQueryParameters(GraphQlReadOperation.USERS_SNAPSHOT, null, null);
    }

    public static GraphQlQueryParameters picksForDate(final LocalDate requestDate) {
        return new GraphQlQueryParameters(
                GraphQlReadOperation.PICKS_TRANSITIONAL_SIDECAR,
                Objects.requireNonNull(requestDate, "A data de Coletas é obrigatória."),
                null);
    }

    public static GraphQlQueryParameters picksTemporalForDate(final LocalDate requestDate) {
        return new GraphQlQueryParameters(
                GraphQlReadOperation.PICKS_TEMPORAL_REFERENCE,
                Objects.requireNonNull(requestDate, "A data de Coletas é obrigatória."),
                null);
    }

    public static GraphQlQueryParameters freightsForWindow(
            final LocalDate startDate, final LocalDate endDate) {
        Objects.requireNonNull(startDate, "O início da janela de Fretes é obrigatório.");
        Objects.requireNonNull(endDate, "O fim da janela de Fretes é obrigatório.");
        if (endDate.isBefore(startDate)) {
            throw new IllegalArgumentException("A janela de Fretes está invertida.");
        }
        return new GraphQlQueryParameters(
                GraphQlReadOperation.FREIGHTS_TRANSITIONAL_SIDECAR, startDate, endDate);
    }

    public GraphQlReadOperation operation() {
        return operation;
    }

    void writeTo(final ObjectNode target) {
        Objects.requireNonNull(target, "O objeto de parâmetros é obrigatório.");
        switch (operation) {
            case USERS_SNAPSHOT -> target.put("enabled", true);
            case PICKS_TRANSITIONAL_SIDECAR, PICKS_TEMPORAL_REFERENCE ->
                    target.put("requestDate", startDate.toString());
            case FREIGHTS_TRANSITIONAL_SIDECAR ->
                    target.put("serviceAt", startDate + " - " + endDate);
        }
    }

    @Override
    public String toString() {
        return "GraphQlQueryParameters[operation=" + operation + ", values=<redacted>]";
    }
}
