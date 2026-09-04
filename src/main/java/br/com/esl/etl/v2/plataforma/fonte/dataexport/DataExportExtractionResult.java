package br.com.esl.etl.v2.plataforma.fonte.dataexport;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Resumo sanitizado de uma travessia que terminou em página vazia.
 *
 * <p>O término local não comprova snapshot, cobertura ou ordenação estável da fonte.
 */
public record DataExportExtractionResult(
        UUID executionId,
        DataExportTemplate template,
        int pagesFetched,
        long recordsDelivered,
        int terminalPage,
        Instant startedAt,
        Instant completedAt,
        DataExportTraversalVerification traversalVerification) {

    public DataExportExtractionResult(
            final UUID executionId,
            final DataExportTemplate template,
            final int pagesFetched,
            final long recordsDelivered,
            final int terminalPage,
            final Instant startedAt,
            final Instant completedAt) {
        this(
                executionId,
                template,
                pagesFetched,
                recordsDelivered,
                terminalPage,
                startedAt,
                completedAt,
                DataExportTraversalVerification.LOCAL_TERMINAL_UNVERIFIED);
    }

    public DataExportExtractionResult {
        Objects.requireNonNull(executionId, "O identificador da execução é obrigatório.");
        Objects.requireNonNull(template, "O template Data Export é obrigatório.");
        Objects.requireNonNull(startedAt, "O início da execução é obrigatório.");
        Objects.requireNonNull(completedAt, "O término da execução é obrigatório.");
        traversalVerification =
                Objects.requireNonNull(
                        traversalVerification, "A verificação da travessia é obrigatória.");
        if (pagesFetched < 1) {
            throw new IllegalArgumentException("A execução deve ter ao menos uma página buscada.");
        }
        if (recordsDelivered < 0) {
            throw new IllegalArgumentException("A quantidade de registros não pode ser negativa.");
        }
        if (terminalPage < 1) {
            throw new IllegalArgumentException("A página terminal deve ser maior que zero.");
        }
        if (completedAt.isBefore(startedAt)) {
            throw new IllegalArgumentException(
                    "O término não pode ser anterior ao início da execução.");
        }
    }
}
