package br.com.esl.etl.v2.plataforma.qualidade;

import java.util.Objects;

/** Falha sanitizada e crítica: nunca é convertida em zero, vazio ou sucesso degradado. */
public final class DataQualityEvaluationException extends RuntimeException {

    private static final long serialVersionUID = 1L;
    private final String reasonCode;
    private final DataQualityFailureKind kind;

    public DataQualityEvaluationException(final String reasonCode) {
        this(reasonCode, DataQualityFailureKind.DETERMINISTIC);
    }

    public DataQualityEvaluationException(final String reasonCode, final Throwable cause) {
        this(reasonCode, DataQualityFailureKind.DETERMINISTIC, cause);
    }

    public DataQualityEvaluationException(
            final String reasonCode, final DataQualityFailureKind kind) {
        super(message(reasonCode));
        this.reasonCode = requiredCode(reasonCode);
        this.kind = Objects.requireNonNull(kind, "A classificação da falha é obrigatória.");
    }

    public DataQualityEvaluationException(
            final String reasonCode, final DataQualityFailureKind kind, final Throwable cause) {
        super(message(reasonCode), Objects.requireNonNull(cause, "A causa é obrigatória."));
        this.reasonCode = requiredCode(reasonCode);
        this.kind = Objects.requireNonNull(kind, "A classificação da falha é obrigatória.");
    }

    public String reasonCode() {
        return reasonCode;
    }

    public DataQualityFailureKind kind() {
        return kind;
    }

    private static String message(final String reasonCode) {
        return "O gate crítico de Data Quality falhou: " + requiredCode(reasonCode) + ".";
    }

    private static String requiredCode(final String reasonCode) {
        final String required = Objects.requireNonNull(reasonCode, "O reason code é obrigatório.");
        if (!required.matches("[A-Z][A-Z0-9_]{1,63}")) {
            throw new IllegalArgumentException("O reason code de Data Quality é inválido.");
        }
        return required;
    }
}
