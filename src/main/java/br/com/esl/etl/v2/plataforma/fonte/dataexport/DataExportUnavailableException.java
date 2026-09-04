package br.com.esl.etl.v2.plataforma.fonte.dataexport;

import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

/** Indica indisponibilidade transitória após a política local de retry se esgotar. */
public final class DataExportUnavailableException extends IllegalStateException {

    private static final long serialVersionUID = 1L;

    private final int templateId;
    private final int httpStatus;
    private final String retryAfter;

    public DataExportUnavailableException(final int templateId, final String reason) {
        this(templateId, reason, Optional.empty());
    }

    public DataExportUnavailableException(
            final int templateId, final String reason, final Throwable cause) {
        super(message(templateId, reason), cause);
        this.templateId = validateTemplateId(templateId);
        this.httpStatus = 0;
        this.retryAfter = null;
    }

    public DataExportUnavailableException(
            final int templateId, final String reason, final Optional<String> retryAfter) {
        super(message(templateId, reason));
        this.templateId = validateTemplateId(templateId);
        this.httpStatus = 0;
        this.retryAfter =
                Objects.requireNonNull(retryAfter, "O cabeçalho Retry-After é obrigatório.")
                        .orElse(null);
    }

    /** Indisponibilidade HTTP estruturada, preservando somente status e Retry-After observável. */
    public DataExportUnavailableException(
            final int templateId, final int httpStatus, final Optional<String> retryAfter) {
        super(message(templateId, "HTTP " + validateHttpStatus(httpStatus)));
        this.templateId = validateTemplateId(templateId);
        this.httpStatus = httpStatus;
        this.retryAfter =
                Objects.requireNonNull(retryAfter, "O cabeçalho Retry-After é obrigatório.")
                        .orElse(null);
    }

    public int templateId() {
        return templateId;
    }

    /**
     * Cabeçalho observado, se a indisponibilidade HTTP o forneceu; não é aplicado automaticamente.
     */
    public Optional<String> retryAfter() {
        return Optional.ofNullable(retryAfter);
    }

    /** Status HTTP observado, quando a indisponibilidade veio de uma resposta HTTP. */
    public OptionalInt httpStatus() {
        return httpStatus == 0 ? OptionalInt.empty() : OptionalInt.of(httpStatus);
    }

    private static String message(final int templateId, final String reason) {
        validateTemplateId(templateId);
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("O motivo de indisponibilidade é obrigatório.");
        }
        return "Data Export temporariamente indisponível para o template "
                + templateId
                + ": "
                + reason
                + ".";
    }

    private static int validateTemplateId(final int value) {
        if (value <= 0) {
            throw new IllegalArgumentException("O template Data Export deve ser positivo.");
        }
        return value;
    }

    private static int validateHttpStatus(final int value) {
        if (value < 100 || value > 599) {
            throw new IllegalArgumentException("O status HTTP de indisponibilidade é inválido.");
        }
        return value;
    }
}
