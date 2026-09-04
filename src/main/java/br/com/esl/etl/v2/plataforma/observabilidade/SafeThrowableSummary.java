package br.com.esl.etl.v2.plataforma.observabilidade;

import java.sql.SQLException;
import java.sql.SQLTimeoutException;
import java.util.Objects;

/** Classificação limitada que nunca copia mensagem, stack trace nem causa textual. */
public record SafeThrowableSummary(
        String categoryCode,
        String sqlStateClass,
        int vendorCode,
        int observedCauseDepth,
        boolean capped) {

    public SafeThrowableSummary {
        categoryCode = ObservabilityFields.upperCode(categoryCode, "A categoria da falha");
        if (sqlStateClass != null && !sqlStateClass.matches("[A-Z0-9]{2}")) {
            throw new IllegalArgumentException("A classe SQLState é inválida.");
        }
        if (observedCauseDepth < 1) {
            throw new IllegalArgumentException("A profundidade observada deve ser positiva.");
        }
    }

    public static SafeThrowableSummary from(final Throwable failure, final int maximumCauseDepth) {
        Objects.requireNonNull(failure, "A falha é obrigatória.");
        if (maximumCauseDepth < 1 || maximumCauseDepth > 16) {
            throw new IllegalArgumentException("O limite de causas deve estar entre 1 e 16.");
        }
        int depth = 1;
        Throwable current = failure;
        while (depth < maximumCauseDepth && current.getCause() != null) {
            current = current.getCause();
            depth++;
        }
        final boolean capped = current.getCause() != null;
        if (failure instanceof SQLTimeoutException sqlTimeout) {
            return sqlSummary("TIMEOUT", sqlTimeout, depth, capped);
        }
        if (failure instanceof SQLException sqlFailure) {
            return sqlSummary("SQL_FAILURE", sqlFailure, depth, capped);
        }
        if (failure instanceof java.util.concurrent.TimeoutException) {
            return new SafeThrowableSummary("TIMEOUT", null, 0, depth, capped);
        }
        if (failure instanceof java.util.concurrent.CancellationException) {
            return new SafeThrowableSummary("CANCELLED", null, 0, depth, capped);
        }
        return new SafeThrowableSummary("RUNTIME_FAILURE", null, 0, depth, capped);
    }

    private static SafeThrowableSummary sqlSummary(
            final String category,
            final SQLException sqlFailure,
            final int depth,
            final boolean capped) {
        final String state = sqlFailure.getSQLState();
        final String stateClass =
                state != null && state.matches("[A-Za-z0-9]{5}")
                        ? state.substring(0, 2).toUpperCase(java.util.Locale.ROOT)
                        : null;
        return new SafeThrowableSummary(
                category, stateClass, sqlFailure.getErrorCode(), depth, capped);
    }
}
