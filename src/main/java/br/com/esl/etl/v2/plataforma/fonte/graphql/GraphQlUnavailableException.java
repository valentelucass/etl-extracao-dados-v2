package br.com.esl.etl.v2.plataforma.fonte.graphql;

import java.util.Objects;
import java.util.OptionalInt;

/** Indisponibilidade transitória após esgotar o retry único do transporte. */
public final class GraphQlUnavailableException extends IllegalStateException {

    private static final long serialVersionUID = 1L;

    private final GraphQlReadOperation operation;
    private final Reason reason;
    private final int httpStatus;

    public GraphQlUnavailableException(
            final GraphQlReadOperation operation, final Reason reason, final Throwable cause) {
        super(message(operation, reason));
        this.operation = operation;
        this.reason = reason;
        Objects.requireNonNull(cause, "A causa da indisponibilidade é obrigatória.");
        this.httpStatus = 0;
    }

    public GraphQlUnavailableException(final GraphQlReadOperation operation, final int httpStatus) {
        super(message(operation, Reason.HTTP_TRANSIENT));
        this.operation = Objects.requireNonNull(operation, "A operação é obrigatória.");
        this.reason = Reason.HTTP_TRANSIENT;
        if (httpStatus < 100 || httpStatus > 599) {
            throw new IllegalArgumentException("O status HTTP é inválido.");
        }
        this.httpStatus = httpStatus;
    }

    public GraphQlReadOperation operation() {
        return operation;
    }

    public Reason reason() {
        return reason;
    }

    public OptionalInt httpStatus() {
        return httpStatus == 0 ? OptionalInt.empty() : OptionalInt.of(httpStatus);
    }

    private static String message(final GraphQlReadOperation operation, final Reason reason) {
        Objects.requireNonNull(operation, "A operação é obrigatória.");
        Objects.requireNonNull(reason, "O motivo é obrigatório.");
        return "A origem GraphQL está temporariamente indisponível para a operação "
                + operation.name()
                + ".";
    }

    public enum Reason {
        IO,
        REQUEST_TIMEOUT,
        HTTP_TRANSIENT
    }
}
