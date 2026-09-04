package br.com.esl.etl.v2.plataforma.fonte.dataexport;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Página recebida com o contexto necessário para staging e auditoria futuros. */
public record DataExportReadPage(
        UUID executionId,
        DataExportPageRequest request,
        DataExportPageResponse response,
        Instant readAt) {

    public DataExportReadPage {
        Objects.requireNonNull(executionId, "O identificador da execução é obrigatório.");
        Objects.requireNonNull(request, "A requisição Data Export é obrigatória.");
        Objects.requireNonNull(response, "A resposta Data Export é obrigatória.");
        Objects.requireNonNull(readAt, "O horário de leitura é obrigatório.");
    }

    public int page() {
        return request.page();
    }
}
