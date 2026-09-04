package br.com.esl.etl.v2.plataforma.fonte.graphql;

/** Retry-After remoto recusado sem registrar seu valor. */
final class GraphQlRetryAfterLimitExceededException extends IllegalStateException {

    private static final long serialVersionUID = 1L;

    GraphQlRetryAfterLimitExceededException() {
        super("O Retry-After GraphQL excede o teto compartilhado.");
    }
}
