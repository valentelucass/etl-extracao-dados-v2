package br.com.esl.etl.v2.plataforma.orquestracao;

import br.com.esl.etl.v2.plataforma.controle.ControlPlane;
import br.com.esl.etl.v2.plataforma.controle.ControlPlaneTransition;
import br.com.esl.etl.v2.plataforma.controle.ExecutionState;
import java.time.Instant;
import java.util.Objects;

/** Operações de lease e cancelamento explícito sobre uma ocorrência já registrada como PLANNED. */
public final class RuntimeExecutionControl {

    private final ControlPlane controlPlane;

    public RuntimeExecutionControl(final ControlPlane controlPlane) {
        this.controlPlane = Objects.requireNonNull(controlPlane, "O control plane é obrigatório.");
    }

    public void heartbeat(final RuntimeExecutionPlanItem item, final Instant observedAt) {
        final RuntimeExecutionPlanItem execution =
                Objects.requireNonNull(item, "A execução planejada é obrigatória.");
        controlPlane.heartbeat(
                execution.request().executionId(),
                Objects.requireNonNull(observedAt, "O instante de heartbeat é obrigatório."),
                execution.definition().leaseDuration());
    }

    public void cancelBeforeStart(final RuntimeExecutionPlanItem item, final Instant observedAt) {
        final RuntimeExecutionPlanItem execution =
                Objects.requireNonNull(item, "A execução planejada é obrigatória.");
        controlPlane.transition(
                new ControlPlaneTransition(
                        execution.request().executionId(),
                        ExecutionState.PLANNED,
                        ExecutionState.CANCELLED,
                        "CANCELLATION_REQUESTED",
                        Objects.requireNonNull(
                                observedAt, "O instante de cancelamento é obrigatório.")));
    }
}
