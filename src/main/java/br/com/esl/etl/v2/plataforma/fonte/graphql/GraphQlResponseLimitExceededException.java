package br.com.esl.etl.v2.plataforma.fonte.graphql;

/** Resposta recusada por bytes antes da desserialização. */
public final class GraphQlResponseLimitExceededException extends IllegalStateException {

    private static final long serialVersionUID = 1L;

    private final GraphQlReadOperation operation;
    private final long maximumBytes;
    private final long observedBytes;

    public GraphQlResponseLimitExceededException(
            final GraphQlReadOperation operation,
            final long maximumBytes,
            final long observedBytes) {
        super("A resposta GraphQL excedeu o limite de bytes da operação.");
        this.operation = java.util.Objects.requireNonNull(operation, "A operação é obrigatória.");
        if (maximumBytes < 1 || observedBytes < 1) {
            throw new IllegalArgumentException("Os tamanhos da resposta são inválidos.");
        }
        this.maximumBytes = maximumBytes;
        this.observedBytes = observedBytes;
    }

    public GraphQlReadOperation operation() {
        return operation;
    }

    public long maximumBytes() {
        return maximumBytes;
    }

    public long observedBytes() {
        return observedBytes;
    }
}
