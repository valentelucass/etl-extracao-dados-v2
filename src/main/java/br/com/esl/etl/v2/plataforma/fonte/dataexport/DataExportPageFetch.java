package br.com.esl.etl.v2.plataforma.fonte.dataexport;

import java.util.Objects;
import java.util.Optional;

/** Resultado da leitura HTTP de uma página, incluindo somente metadados de transporte seguros. */
public record DataExportPageFetch(
        DataExportPageResponse response,
        DataExportTransport acceptedTransport,
        int httpStatus,
        Optional<String> retryAfter,
        DataExportResponseForm responseForm,
        boolean jsonContentTypeDeclared) {

    public DataExportPageFetch(
            final DataExportPageResponse response,
            final DataExportTransport acceptedTransport,
            final int httpStatus,
            final Optional<String> retryAfter,
            final DataExportResponseForm responseForm) {
        this(response, acceptedTransport, httpStatus, retryAfter, responseForm, false);
    }

    public DataExportPageFetch {
        response = Objects.requireNonNull(response, "A página Data Export é obrigatória.");
        acceptedTransport =
                Objects.requireNonNull(acceptedTransport, "O transporte aceito é obrigatório.");
        if (httpStatus < 200 || httpStatus >= 300) {
            throw new IllegalArgumentException("Uma página Data Export exige HTTP de sucesso.");
        }
        retryAfter = Objects.requireNonNull(retryAfter, "O cabeçalho Retry-After é obrigatório.");
        responseForm =
                Objects.requireNonNull(
                        responseForm, "A forma da resposta Data Export é obrigatória.");
    }

    @Override
    public String toString() {
        return "DataExportPageFetch[recordCount="
                + response.recordCount()
                + ", acceptedTransport="
                + acceptedTransport
                + ", httpStatus="
                + httpStatus
                + ", retryAfterObserved="
                + retryAfter.isPresent()
                + ", responseForm="
                + responseForm
                + ", jsonContentTypeDeclared="
                + jsonContentTypeDeclared
                + "]";
    }
}
