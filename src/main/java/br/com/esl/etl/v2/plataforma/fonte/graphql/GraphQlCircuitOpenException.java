package br.com.esl.etl.v2.plataforma.fonte.graphql;

import java.util.Objects;

/** Circuito aberto sem endpoint, cursor ou payload na mensagem. */
public final class GraphQlCircuitOpenException extends IllegalStateException {

    private static final long serialVersionUID = 1L;

    private final GraphQlReadOperation operation;

    public GraphQlCircuitOpenException(final GraphQlReadOperation operation) {
        super(
                "Circuit breaker GraphQL aberto para a operação "
                        + Objects.requireNonNull(operation, "A operação é obrigatória.").name()
                        + ".");
        this.operation = operation;
    }

    public GraphQlReadOperation operation() {
        return operation;
    }
}
