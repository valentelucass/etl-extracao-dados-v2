package br.com.esl.etl.v2.plataforma.controle;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Metadados sanitizados e imutáveis de uma tentativa de página, sem payload, URL ou chave de
 * negócio. {@code readAt} é apenas a observação do caller; o SQL Server persiste o relógio técnico
 * capturado após o fencing.
 */
public record ControlPlanePage(
        UUID executionId,
        int pageNumber,
        int pageAttempt,
        int requestedPageSize,
        long physicalRows,
        long distinctRootKeys,
        long responseBytes,
        ControlPlanePageTerminality terminality,
        Instant readAt) {

    /** Compatibilidade tipada para os produtores Data Export existentes. */
    public ControlPlanePage(
            final UUID executionId,
            final int pageNumber,
            final int pageAttempt,
            final int requestedPageSize,
            final long physicalRows,
            final long distinctRootKeys,
            final long responseBytes,
            final boolean terminalEmptyPage,
            final Instant readAt) {
        this(
                executionId,
                pageNumber,
                pageAttempt,
                requestedPageSize,
                physicalRows,
                distinctRootKeys,
                responseBytes,
                terminalEmptyPage
                        ? ControlPlanePageTerminality.DATA_EXPORT_EMPTY_PAGE
                        : ControlPlanePageTerminality.NONE,
                readAt);
    }

    public ControlPlanePage {
        executionId = Objects.requireNonNull(executionId, "O execution_id é obrigatório.");
        readAt = Objects.requireNonNull(readAt, "O horário de leitura é obrigatório.");
        terminality = Objects.requireNonNull(terminality, "A terminalidade é obrigatória.");
        if (pageNumber < 1 || pageAttempt < 1 || requestedPageSize < 1) {
            throw new IllegalArgumentException(
                    "Página, tentativa e tamanho solicitado devem ser positivos.");
        }
        if (physicalRows < 0 || distinctRootKeys < 0 || responseBytes < 0) {
            throw new IllegalArgumentException("As contagens de página não podem ser negativas.");
        }
        if (distinctRootKeys > physicalRows) {
            throw new IllegalArgumentException(
                    "Chaves-raiz distintas não podem superar linhas físicas.");
        }
        if (terminality == ControlPlanePageTerminality.DATA_EXPORT_EMPTY_PAGE
                && physicalRows != 0) {
            throw new IllegalArgumentException("Uma página terminal vazia não pode conter linhas.");
        }
        if (terminality == ControlPlanePageTerminality.GRAPHQL_PAGE_INFO && physicalRows == 0) {
            throw new IllegalArgumentException(
                    "A terminalidade GraphQL exige a página populada observada.");
        }
    }
}
