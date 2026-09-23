package br.com.esl.etl.v2.plataforma.analitico;

import br.com.esl.etl.v2.plataforma.controle.ExecutionMode;
import java.time.LocalDate;
import java.util.UUID;

/** Explicit local scope; full must also equal the persisted run interval in SQL. */
public record AnalyticMaterializationRequest(
        UUID run,
        UUID receipt,
        int referenceRevision,
        ExecutionMode mode,
        boolean full,
        LocalDate start,
        LocalDate endExclusive) {
    public AnalyticMaterializationRequest {
        if (run == null
                || receipt == null
                || referenceRevision < 1
                || referenceRevision > 100000
                || mode == null
                || mode == ExecutionMode.SWEEP
                || start == null
                || endExclusive == null
                || !start.isBefore(endExclusive)
                || start.plusDays(3660).isBefore(endExclusive)) {
            throw new IllegalArgumentException("ANA_MATERIALIZATION_SCOPE");
        }
    }
}
