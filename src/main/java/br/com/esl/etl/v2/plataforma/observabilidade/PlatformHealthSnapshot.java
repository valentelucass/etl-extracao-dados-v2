package br.com.esl.etl.v2.plataforma.observabilidade;

import java.time.Instant;
import java.util.Objects;

/** Readiness agregada e sanitizada; ausência/shape inválido do SQL sempre vira DOWN. */
public record PlatformHealthSnapshot(
        PlatformHealthStatus status,
        String reasonCode,
        long incompleteDataQualityRuns,
        long failedDataQualityRuns,
        long overdueQuarantineRows,
        long staleRunningExecutions,
        Instant observedAt) {

    public PlatformHealthSnapshot {
        status = Objects.requireNonNull(status, "O estado de health é obrigatório.");
        reasonCode = ObservabilityFields.upperCode(reasonCode, "O reason code de health");
        observedAt = Objects.requireNonNull(observedAt, "O horário do health é obrigatório.");
        if (incompleteDataQualityRuns < 0
                || failedDataQualityRuns < 0
                || overdueQuarantineRows < 0
                || staleRunningExecutions < 0) {
            throw new IllegalArgumentException("As contagens de health não podem ser negativas.");
        }
        String expectedReason;
        PlatformHealthStatus expectedStatus;
        if (incompleteDataQualityRuns > 0) {
            expectedStatus = PlatformHealthStatus.DOWN;
            expectedReason = "DQ_INCOMPLETE";
        } else if (failedDataQualityRuns > 0) {
            expectedStatus = PlatformHealthStatus.DOWN;
            expectedReason = "DQ_FAILED";
        } else if (overdueQuarantineRows > 0) {
            expectedStatus = PlatformHealthStatus.DOWN;
            expectedReason = "QUARANTINE_SLA_EXCEEDED";
        } else if (staleRunningExecutions > 0) {
            expectedStatus = PlatformHealthStatus.DEGRADED;
            expectedReason = "RUNNING_STALE";
        } else if (status == PlatformHealthStatus.DOWN) {
            expectedStatus = PlatformHealthStatus.DOWN;
            expectedReason = reasonCode;
            if (!reasonCode.equals("SQL_UNAVAILABLE")
                    && !reasonCode.equals("HEALTH_SUMMARY_ABSENT")
                    && !reasonCode.equals("HEALTH_SUMMARY_DUPLICATED")
                    && !reasonCode.equals("HEALTH_SUMMARY_DIVERGENT")) {
                throw new IllegalArgumentException("O reason code local de health é inválido.");
            }
        } else {
            expectedStatus = PlatformHealthStatus.UP;
            expectedReason = "PLATFORM_READY";
        }
        if (status != expectedStatus || !reasonCode.equals(expectedReason)) {
            throw new IllegalArgumentException("O status de health diverge das contagens.");
        }
    }

    public static PlatformHealthSnapshot down(final String reasonCode, final Instant observedAt) {
        return new PlatformHealthSnapshot(
                PlatformHealthStatus.DOWN, reasonCode, 0, 0, 0, 0, observedAt);
    }
}
