package br.com.esl.etl.v2.plataforma.fonte.dataexport;

import java.util.Objects;

/** Limites explícitos que impedem travessias sem fim ou consumo excessivo da fonte. */
public record DataExportExtractionLimits(int maxPages, long maxRecords, int maxPageSize) {

    public DataExportExtractionLimits {
        if (maxPages < 1) {
            throw new IllegalArgumentException("O máximo de páginas deve ser maior que zero.");
        }
        if (maxRecords < 1) {
            throw new IllegalArgumentException("O máximo de registros deve ser maior que zero.");
        }
        if (maxPageSize < 1) {
            throw new IllegalArgumentException("O máximo por página deve ser maior que zero.");
        }
    }

    public void validate(final DataExportPageRequest request) {
        Objects.requireNonNull(request, "A requisição Data Export é obrigatória.");
        if (request.page() != 1) {
            throw new IllegalArgumentException(
                    "A travessia Data Export deve começar na primeira página.");
        }
        if (request.pageSize() > maxPageSize) {
            throw new IllegalArgumentException(
                    "O tamanho da página "
                            + request.pageSize()
                            + " excede o limite configurado de "
                            + maxPageSize
                            + ".");
        }
    }
}
