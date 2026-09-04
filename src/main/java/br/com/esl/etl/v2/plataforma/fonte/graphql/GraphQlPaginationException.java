package br.com.esl.etl.v2.plataforma.fonte.graphql;

/** Falha fail-closed sem cursor, payload ou dado de negócio na mensagem. */
public final class GraphQlPaginationException extends IllegalStateException {

    private static final long serialVersionUID = 1L;

    private final Reason reason;

    public GraphQlPaginationException(final Reason reason) {
        super(
                "A travessia GraphQL falhou por "
                        + java.util.Objects.requireNonNull(reason).name()
                        + ".");
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }

    public enum Reason {
        EMPTY_PAGE,
        MISSING_CURSOR,
        REPEATED_CURSOR,
        PAGE_LIMIT,
        NODE_LIMIT,
        PAGE_SIZE_EXCEEDED
    }
}
