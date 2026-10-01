package br.com.esl.etl.v2.bootstrap;

import br.com.esl.etl.v2.plataforma.controle.ControlPlane;
import br.com.esl.etl.v2.plataforma.controle.ControlPlaneCounts;
import br.com.esl.etl.v2.plataforma.controle.ControlPlaneCycle;
import br.com.esl.etl.v2.plataforma.controle.ControlPlanePage;
import br.com.esl.etl.v2.plataforma.controle.ControlPlaneRecoveryResult;
import br.com.esl.etl.v2.plataforma.controle.ControlPlaneSource;
import br.com.esl.etl.v2.plataforma.controle.ControlPlaneStart;
import br.com.esl.etl.v2.plataforma.controle.ControlPlaneTransition;
import br.com.esl.etl.v2.plataforma.controle.ExecutionPartitionKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Confere a transacao compartilhada imediatamente depois de cada procedure de controle. */
final class Coletas6908PilotControlPlane implements ControlPlane {
    private final ControlPlane delegate;
    private final Runnable checkpoint;

    Coletas6908PilotControlPlane(final ControlPlane delegate, final Runnable checkpoint) {
        this.delegate = Objects.requireNonNull(delegate);
        this.checkpoint = Objects.requireNonNull(checkpoint);
    }

    private void call(final Runnable operation) {
        checkpoint.run();
        operation.run();
        checkpoint.run();
    }

    @Override
    public void registerSource(final ControlPlaneSource source) {
        call(() -> delegate.registerSource(source));
    }

    @Override
    public void startCycle(final ControlPlaneCycle cycle) {
        call(() -> delegate.startCycle(cycle));
    }

    @Override
    public void startExecution(final ControlPlaneStart start) {
        call(() -> delegate.startExecution(start));
    }

    @Override
    public void heartbeat(final UUID executionId, final Instant at, final Duration extension) {
        call(() -> delegate.heartbeat(executionId, at, extension));
    }

    @Override
    public void recordPage(final ControlPlanePage page) {
        call(() -> delegate.recordPage(page));
    }

    @Override
    public void recordCounts(final ControlPlaneCounts counts) {
        call(() -> delegate.recordCounts(counts));
    }

    @Override
    public void transition(final ControlPlaneTransition transition) {
        call(() -> delegate.transition(transition));
    }

    @Override
    public void registerIncrementalFrontier(
            final ExecutionPartitionKey partition, final Instant end, final Instant registeredAt) {
        call(() -> delegate.registerIncrementalFrontier(partition, end, registeredAt));
    }

    @Override
    public ControlPlaneRecoveryResult recoverStaleExecutions() {
        final var result = delegate.recoverStaleExecutions();
        checkpoint.run();
        return result;
    }
}
