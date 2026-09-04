package br.com.esl.etl.v2.plataforma.fonte.graphql;

import java.util.Objects;

/** Envelope inválido sem conservar mensagem, payload ou valores remotos. */
public final class GraphQlResponseException extends IllegalStateException {

    private static final long serialVersionUID = 1L;

    private final Reason reason;

    public GraphQlResponseException(final Reason reason) {
        super("A resposta GraphQL foi recusada por " + Objects.requireNonNull(reason).name() + ".");
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }

    public enum Reason {
        INVALID_JSON,
        INVALID_UTF8,
        INVALID_CONTENT_TYPE,
        HTTP_STATUS,
        GRAPHQL_ERRORS,
        INVALID_ENVELOPE,
        INVALID_CONNECTION,
        INVALID_EDGE,
        INVALID_NODE,
        INVALID_PAGE_INFO
    }
}
