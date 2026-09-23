package br.com.esl.etl.v2.plataforma.expansao;

import br.com.esl.etl.v2.modulos.faturasporcliente.domain.FaturaClienteRules.FiscalPolicy;
import java.time.LocalDate;
import java.util.Objects;

public record ExpansionPolicy(
        LocalDate start,
        LocalDate endExclusive,
        LocalDate businessDate,
        int pageSize,
        int maximumPages,
        int maximumRows,
        FiscalPolicy fiscalPolicy) {
    public ExpansionPolicy {
        Objects.requireNonNull(start);
        Objects.requireNonNull(endExclusive);
        Objects.requireNonNull(businessDate);
        Objects.requireNonNull(fiscalPolicy);
        if (!start.isBefore(endExclusive)
                || start.plusDays(31).isBefore(endExclusive)
                || pageSize < 1
                || pageSize > 100
                || maximumPages < 1
                || maximumPages > 10000
                || maximumRows < 1
                || maximumRows > 100000) {
            throw new IllegalArgumentException("EXP_POLICY_BOUND");
        }
    }

    public void validate(final LocalDate date) {
        if (date == null || date.isBefore(start) || !date.isBefore(endExclusive)) {
            throw new IllegalArgumentException("EXP_PARTITION_BOUND");
        }
    }
}
