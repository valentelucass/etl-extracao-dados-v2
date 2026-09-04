package br.com.esl.etl.v2.plataforma.fonte.dataexport;

import br.com.esl.etl.v2.plataforma.resiliencia.EslRequestGovernor;
import java.time.Duration;
import java.util.Objects;

/** Adapta um workload do governor ESL ao executor HTTP Data Export. */
public final class EslDataExportHttpAttemptGovernor implements DataExportHttpAttemptGovernor {

    private final EslRequestGovernor.Cycle.Workload workload;

    public EslDataExportHttpAttemptGovernor(final EslRequestGovernor.Cycle.Workload workload) {
        this.workload = Objects.requireNonNull(workload, "O workload ESL é obrigatório.");
    }

    @Override
    public AttemptPermit acquire(final DataExportHttpAttempt attempt) {
        Objects.requireNonNull(attempt, "A tentativa HTTP é obrigatória.");
        final EslRequestGovernor.RequestPermit permit = workload.acquire();
        return new AttemptPermit() {
            @Override
            public Duration remainingTime() {
                return permit.remainingTime();
            }

            @Override
            public void checkpoint() {
                permit.checkpoint();
            }

            @Override
            public void close() {
                permit.close();
            }
        };
    }

    @Override
    public void awaitRetry(final Duration delay) {
        workload.awaitRetry(delay);
    }

    @Override
    public void imposeRateLimitEmbargo(final Duration delay) {
        workload.imposeRateLimitEmbargo(delay);
    }

    @Override
    public boolean appliesTerminalEmbargo() {
        return true;
    }

    @Override
    public Duration maximumRetryAfter() {
        return workload.maximumRetryAfter();
    }
}
