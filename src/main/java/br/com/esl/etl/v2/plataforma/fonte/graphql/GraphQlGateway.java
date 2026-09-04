package br.com.esl.etl.v2.plataforma.fonte.graphql;

import br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken;

/** Porta de saída read-only para uma única página GraphQL. */
@FunctionalInterface
public interface GraphQlGateway {

    GraphQlPageResponse fetch(GraphQlPageRequest request);

    /** Gate de capability: lambdas/raw gateways não são travessias operacionais válidas. */
    default void verifyCancellationToken(final CancellationToken cancellationToken) {
        throw new IllegalStateException(
                "O gateway GraphQL não está vinculado ao cancelamento da travessia.");
    }
}
