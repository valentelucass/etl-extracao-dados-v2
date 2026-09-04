package br.com.esl.etl.v2.plataforma.fonte.dataexport;

import java.util.Objects;
import java.util.Optional;

/** Resposta HTTP sanitizada, compartilhada pelos adaptadores Data Export. */
record DataExportHttpResponse(
        int statusCode, String body, Optional<String> retryAfter, boolean jsonContentTypeDeclared) {

    DataExportHttpResponse(
            final int statusCode, final String body, final Optional<String> retryAfter) {
        this(statusCode, body, retryAfter, false);
    }

    DataExportHttpResponse {
        Objects.requireNonNull(body, "O corpo HTTP é obrigatório.");
        retryAfter = Objects.requireNonNull(retryAfter, "O cabeçalho Retry-After é obrigatório.");
    }

    @Override
    public String toString() {
        return "DataExportHttpResponse[statusCode="
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
