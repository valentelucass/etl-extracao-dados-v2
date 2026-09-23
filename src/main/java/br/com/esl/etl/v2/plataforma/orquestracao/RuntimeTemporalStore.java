package br.com.esl.etl.v2.plataforma.orquestracao;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Persistence boundary for a finite temporal plan and ordered SQL summaries. */
public interface RuntimeTemporalStore {
    record Gap(UUID execution, Instant start, Instant endExclusive, String state) {
        public Gap {
            Objects.requireNonNull(execution);
            if (!start.isBefore(endExclusive)
                    || !java.util.Set.of(
                                    "NOT_STARTED",
                                    "PLANNED",
                                    "EXTRACTING",
                                    "EXTRACTED",
                                    "STAGED",
                                    "PROMOTED",
                                    "RECONCILED",
                                    "PUBLISHED",
                                    "FAILED",
                                    "CANCELLED",
                                    "BLOCKED",
                                    "SKIPPED",
                                    "NOT_APPLICABLE",
                                    "DEGRADED")
                            .contains(state)) {
                throw new IllegalArgumentException("TEMPORAL_GAP_INVALID");
            }
        }
    }

    int persist(
            UUID plan,
            String namespaceHash,
            RuntimeTemporalPolicy policy,
            RuntimeTemporalPlanner.Result result);

    List<Gap> readGapPage(String namespaceHash, int maximum, Instant after);
}
