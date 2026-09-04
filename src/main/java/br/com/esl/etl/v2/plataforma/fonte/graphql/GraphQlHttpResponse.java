package br.com.esl.etl.v2.plataforma.fonte.graphql;

import java.util.Objects;
import java.util.Optional;

/** Resposta HTTP sanitizada; corpos de erro são descartados pelo body handler. */
record GraphQlHttpResponse(
        int statusCode, String body, Optional<String> retryAfter, boolean jsonContentTypeDeclared) {

    GraphQlHttpResponse {
        if (statusCode < 100 || statusCode > 599) {
            throw new IllegalArgumentException("O status HTTP é inválido.");
        }
        body = Objects.requireNonNull(body, "O corpo HTTP é obrigatório.");
        retryAfter = Objects.requireNonNull(retryAfter, "O Retry-After é obrigatório.");
    }

    @Override
    public String toString() {
        return "GraphQlHttpResponse[statusCode="
                + statusCode
                + ", bodyLength="
                + body.length()
                + ", retryAfterObserved="
                + retryAfter.isPresent()
                + ", jsonContentTypeDeclared="
                + jsonContentTypeDeclared
                + "]";
    }
}
