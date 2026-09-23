package br.com.esl.etl.v2.plataforma.orquestracao;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Novo agregado e diagnósticos limitados; causas são apenas para diagnóstico interno. */
public final class RuntimeRecoveryDispatchResult {
    private final RuntimeDispatchResult dispatch;
    private final Map<RuntimeWorkloadId, RuntimeRecoverySnapshot> snapshots;
    private final Map<RuntimeWorkloadId, RuntimeException> causes;

    RuntimeRecoveryDispatchResult(
            final RuntimeDispatchResult dispatch,
            final Map<RuntimeWorkloadId, RuntimeRecoverySnapshot> snapshots,
            final Map<RuntimeWorkloadId, RuntimeException> causes) {
        this.dispatch = Objects.requireNonNull(dispatch);
        this.snapshots = Map.copyOf(snapshots);
        this.causes = Map.copyOf(causes);
    }

    public RuntimeDispatchResult dispatch() {
        return dispatch;
    }

    public RuntimeRecoverySnapshot snapshot(final RuntimeWorkloadId id) {
        return Objects.requireNonNull(snapshots.get(id));
    }

    public Optional<RuntimeException> failureCause(final RuntimeWorkloadId id) {
        return Optional.ofNullable(causes.get(id));
    }

    @Override
    public String toString() {
        return "RuntimeRecoveryDispatchResult[redacted]";
    }
}
