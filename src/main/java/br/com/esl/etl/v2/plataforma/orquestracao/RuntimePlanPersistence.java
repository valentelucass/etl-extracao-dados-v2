package br.com.esl.etl.v2.plataforma.orquestracao;

import br.com.esl.etl.v2.plataforma.controle.ControlPlane;
import br.com.esl.etl.v2.plataforma.controle.ControlPlaneCycle;
import br.com.esl.etl.v2.plataforma.controle.ControlPlaneRecoveryResult;
import br.com.esl.etl.v2.plataforma.controle.ControlPlaneSource;
import br.com.esl.etl.v2.plataforma.controle.ControlPlaneStart;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/** Persiste ciclo e ocorrências; startExecution adquire lease e registra EXTRACTING no SQL. */
public final class RuntimePlanPersistence {

    private final ControlPlane controlPlane;

    public RuntimePlanPersistence(final ControlPlane controlPlane) {
        this.controlPlane = Objects.requireNonNull(controlPlane, "O control plane é obrigatório.");
    }

    public RuntimePlanPersistenceReceipt persist(final RuntimeExecutionPlan plan) {
        Objects.requireNonNull(plan, "O plano é obrigatório.");
        final ControlPlaneRecoveryResult recovery = controlPlane.recoverStaleExecutions();
        controlPlane.startCycle(
                new ControlPlaneCycle(plan.cycleId(), plan.fingerprint(), plan.plannedAt()));
        final Set<String> registeredSources = new HashSet<>();
        final int[] plannedExecutions = {0};
        plan.forEach(
                item -> {
                    final String sourceKey =
                            item.definition().sourceKind()
                                    + '\u0000'
                                    + item.definition().sourceInstance();
                    if (registeredSources.add(sourceKey)) {
                        controlPlane.registerSource(
                                new ControlPlaneSource(
                                        item.definition().sourceInstance(),
                                        item.definition().sourceKind(),
                                        plan.plannedAt()));
                    }
                    controlPlane.startExecution(
                            new ControlPlaneStart(
                                    item.request().executionId(),
                                    plan.cycleId(),
                                    item.partition(),
                                    item.request().windowStrategy().name(),
                                    item.definition().contract(),
                                    item.definition().configuration(),
                                    item.request().idempotencyKey(),
                                    item.request().replayOfExecutionId(),
                                    item.definition().leaseDuration(),
                                    plan.plannedAt()));
                    plannedExecutions[0] = Math.incrementExact(plannedExecutions[0]);
                });
        return new RuntimePlanPersistenceReceipt(
                plan.cycleId(),
                recovery.recoveredExecutions(),
                registeredSources.size(),
                plannedExecutions[0]);
    }

    /** Abre o ciclo; a aquisição de leases permanece adiada até cada despacho serial. */
    public ControlPlaneRecoveryResult begin(final RuntimeExecutionPlan plan) {
        Objects.requireNonNull(plan, "O plano é obrigatório.");
        // A scoped dispatch cannot mutate expired attempts from unrelated namespaces.
        // Recovery of the requested occurrence belongs to the explicit durable protocol.
        controlPlane.startCycle(
                new ControlPlaneCycle(plan.cycleId(), plan.fingerprint(), plan.plannedAt()));
        return new ControlPlaneRecoveryResult(0);
    }
}
