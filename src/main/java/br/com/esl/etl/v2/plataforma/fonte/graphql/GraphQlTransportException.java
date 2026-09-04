package br.com.esl.etl.v2.plataforma.fonte.graphql;

/**
 * Falha inesperada do transporte sem conservar endpoint, request ou causa potencialmente sensível.
 */
final class GraphQlTransportException extends IllegalStateException {

    private static final long serialVersionUID = 1L;

    GraphQlTransportException() {
        super("O transporte GraphQL falhou de forma não classificada.");
    }
}
