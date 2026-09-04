package br.com.esl.etl.v2.plataforma.observabilidade;

import java.sql.SQLException;
import java.util.Objects;

/** Falha sanitizada do boundary SQL; a mensagem do driver nunca é interpolada. */
public final class ObservabilityPersistenceException extends RuntimeException {

    private static final long serialVersionUID = 1L;
    private final String operationCode;
    private final ObservabilityPersistenceFailureKind kind;

    public ObservabilityPersistenceException(
            final String operationCode,
            final ObservabilityPersistenceFailureKind kind,
            final SQLException cause) {
        super(
                "A operação de observabilidade falhou: " + requiredCode(operationCode) + ".",
                Objects.requireNonNull(cause, "A causa SQL é obrigatória."));
        this.operationCode = requiredCode(operationCode);
        this.kind = Objects.requireNonNull(kind, "A classificação da falha é obrigatória.");
    }

    public String operationCode() {
        return operationCode;
    }

    public ObservabilityPersistenceFailureKind kind() {
        return kind;
    }

    private static String requiredCode(final String value) {
        final String required =
                Objects.requireNonNull(value, "O código da operação é obrigatório.");
        if (!required.matches("[A-Z][A-Z0-9_]{1,63}")) {
            throw new IllegalArgumentException("O código da operação é inválido.");
        }
        return required;
    }
}
