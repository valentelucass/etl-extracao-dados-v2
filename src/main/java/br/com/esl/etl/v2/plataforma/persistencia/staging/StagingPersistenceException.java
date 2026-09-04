package br.com.esl.etl.v2.plataforma.persistencia.staging;

import java.sql.SQLException;
import java.util.Objects;

/**
 * Falha sanitizada do boundary SQL do kernel, com a causa JDBC preservada para tratamento superior.
 */
public final class StagingPersistenceException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public StagingPersistenceException(final String operation, final SQLException cause) {
        super(
                "Não foi possível concluir " + required(operation) + " no kernel de staging.",
                Objects.requireNonNull(cause, "A causa SQL é obrigatória."));
    }

    private static String required(final String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("A operação de staging é obrigatória.");
        }
        return value.trim();
    }
}
