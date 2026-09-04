package br.com.esl.etl.v2.plataforma.fonte.dataexport;

import br.com.esl.etl.v2.plataforma.resiliencia.FailureKind;
import java.util.Objects;
import java.util.Optional;

/** Classifica 422 sem inspecionar mensagem, payload, filtro ou texto livre. */
public final class DataExport422FailureClassifier {

    public FailureKind classify(
            final int statusCode, final Optional<DataExport422ErrorCategory> structuredCategory) {
        if (statusCode != 422) {
            throw new IllegalArgumentException("O classificador aceita somente HTTP 422.");
        }
        Objects.requireNonNull(structuredCategory, "A categoria estruturada é obrigatória.");
        return structuredCategory
                        .filter(category -> category == DataExport422ErrorCategory.WINDOW_TOO_LARGE)
                        .isPresent()
                ? FailureKind.WINDOW_TOO_LARGE_HTTP_422
                : FailureKind.UNCLASSIFIED_HTTP_422;
    }
}
