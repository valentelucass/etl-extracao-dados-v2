package br.com.esl.etl.v2.plataforma.relacional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/** Explicit laboratory budgets; these values never declare a provider SLA. */
public record RelationalLaboratoryPolicy(
        LocalDate start,
        LocalDate end,
        int maximumRows,
        int maximumClaim,
        int maximumAttempts,
        int leaseSeconds,
        int retrySeconds,
        int expansionDays,
        int pageSize,
        int maximumPages) {
    public RelationalLaboratoryPolicy {
        Objects.requireNonNull(start);
        Objects.requireNonNull(end);
        if (end.isBefore(start)
                || ChronoUnit.DAYS.between(start, end) > 366
                || maximumRows < 1
                || maximumRows > 100_000
                || maximumClaim < 1
                || maximumClaim > 100
                || maximumAttempts < 1
                || maximumAttempts > 10
                || leaseSeconds < 1
                || leaseSeconds > 300
                || retrySeconds < 1
                || retrySeconds > 3600
                || expansionDays < 0
                || expansionDays > 31
                || pageSize < 1
                || pageSize > 100
                || maximumPages < 2
                || maximumPages > 10_000) {
            throw new IllegalArgumentException("REL_LAB_POLICY_BOUND");
        }
    }

    public void validateDate(final LocalDate date) {
        if (date.isBefore(start.minusDays(expansionDays))
                || date.isAfter(end.plusDays(expansionDays))) {
            throw new IllegalArgumentException("REL_LAB_WINDOW_BOUND");
        }
    }

    public String fingerprint() {
        return RelationalCaptureContracts.digest(
                "synthetic-relational-policy-v1|"
                        + start
                        + "|"
                        + end
                        + "|"
                        + maximumRows
                        + "|"
                        + maximumClaim
                        + "|"
                        + maximumAttempts
                        + "|"
                        + leaseSeconds
                        + "|"
                        + retrySeconds
                        + "|"
                        + expansionDays
                        + "|"
                        + pageSize
                        + "|"
                        + maximumPages);
    }
}
