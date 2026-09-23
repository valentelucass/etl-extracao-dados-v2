package br.com.esl.etl.v2.plataforma.orquestracao;

import br.com.esl.etl.v2.plataforma.controle.ControlPlane;
import br.com.esl.etl.v2.plataforma.controle.ControlPlaneSource;
import br.com.esl.etl.v2.plataforma.controle.ControlPlaneStart;
import br.com.esl.etl.v2.plataforma.controle.ExecutionPartitionKey;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken;
import br.com.esl.etl.v2.plataforma.resiliencia.CycleOutcomeAccumulator;
import br.com.esl.etl.v2.plataforma.resiliencia.FailurePolicyContext;
import br.com.esl.etl.v2.plataforma.resiliencia.FailurePolicyMatrix;
import java.time.Clock;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Dispatcher interno serial de até 64 workloads. A CLI oficial não o compõe. */
public final class RuntimeDispatcher {
    private final ControlPlane control;
    private final Clock clock;
    private final RuntimeRecoveryPort durable;
    private final Map<RuntimeWorkloadId, RuntimeWorkloadHandler> handlers = new HashMap<>();

    public RuntimeDispatcher(
            final ControlPlane control,
            final Clock clock,
            final RuntimeDispatchBinding... bindings) {
        this(control, clock, null, bindings);
    }

    public RuntimeDispatcher(
            final ControlPlane control,
            final Clock clock,
            final RuntimeRecoveryPort durable,
            final RuntimeDispatchBinding... bindings) {
        this.control = Objects.requireNonNull(control);
        this.clock = Objects.requireNonNull(clock);
        this.durable = durable;
        if (bindings == null || bindings.length == 0 || bindings.length > 64) {
            throw new IllegalArgumentException("O dispatcher exige de um a 64 bindings.");
        }
        for (final RuntimeDispatchBinding binding : bindings) {
            Objects.requireNonNull(binding);
            if (handlers.putIfAbsent(binding.id(), binding.handler()) != null) {
                throw new IllegalArgumentException("Handler duplicado.");
            }
        }
    }

    public RuntimeDispatchResult dispatch(
            final RuntimeExecutionPlan plan, final CancellationToken cancellation) {
        Objects.requireNonNull(plan);
        Objects.requireNonNull(cancellation);
        validate(plan);
        final RuntimePlanPersistence persistence = new RuntimePlanPersistence(control);
        persistence.begin(plan);
        final Map<RuntimeWorkloadId, RuntimeExecutionResult> results = new HashMap<>();
        final Set<String> registered = new HashSet<>();
        final CycleOutcomeAccumulator aggregate = new CycleOutcomeAccumulator();
        final FailurePolicyMatrix matrix = new FailurePolicyMatrix();
        plan.forEach(
                item -> {
                    final RuntimeExecutionResult result =
                            execute(plan, item, cancellation, registered, results);
                    results.put(item.definition().id(), result);
                    if (result.status() == RuntimeExecutionResult.Status.PUBLISHED) {
                        aggregate.recordPublished();
                    } else {
                        aggregate.recordDecision(
                                matrix.decide(
                                        result.failure().orElseThrow(),
                                        FailurePolicyContext.exhausted()));
                    }
                });
        return new RuntimeDispatchResult(results, aggregate.snapshot());
    }

    private RuntimeExecutionResult execute(
            final RuntimeExecutionPlan plan,
            final RuntimeExecutionPlanItem item,
            final CancellationToken cancellation,
            final Set<String> registered,
            final Map<RuntimeWorkloadId, RuntimeExecutionResult> results) {
        final var start =
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
                        clock.instant());
        final var session = new RuntimeExecutionSession(item, start, control, clock, cancellation);
        session.durableRecovery(durable, plan.fingerprint());
        session.markStartUncertain();
        try {
            final String source = item.definition().sourceInstance();
            if (registered.add(source)) {
                control.registerSource(
                        new ControlPlaneSource(
                                source, item.definition().sourceKind(), clock.instant()));
            }
            control.startExecution(start);
            session.markStarted();
            session.verifyLease();
            final boolean[] blocked = {false};
            item.definition()
                    .forEachDependency(
                            dependency -> {
                                final RuntimeExecutionResult predecessor = results.get(dependency);
                                if (predecessor == null
                                        || predecessor.status()
                                                != RuntimeExecutionResult.Status.PUBLISHED) {
                                    blocked[0] = true;
                                }
                            });
            if (blocked[0]) {
                return session.blocked();
            }
            session.cancellation().throwIfCancellationRequested();
            handlers.get(item.definition().id()).execute(session);
            return session.result();
        } catch (final RuntimeException exception) {
            return session.failed(exception);
        }
    }

    /** Não registra ciclo/start e não executa recoverStaleExecutions ou handlers de extração. */
    public RuntimeRecoveryDispatchResult recover(
            final RuntimeExecutionPlan plan,
            final CancellationToken cancellation,
            final RuntimeRecoveryExpectation... expectations) {
        Objects.requireNonNull(durable, "RUNTIME_DURABLE_PORT_REQUIRED");
        Objects.requireNonNull(cancellation);
        validate(plan);
        if (expectations == null || expectations.length != plan.executionCount()) {
            throw new IllegalArgumentException("RUNTIME_RECOVERY_EXPECTATIONS_REQUIRED");
        }
        final Map<RuntimeWorkloadId, RuntimeRecoveryExpectation> expected = new HashMap<>();
        for (final var value : expectations) {
            if (expected.putIfAbsent(value.workload(), value) != null) {
                throw new IllegalArgumentException("RUNTIME_RECOVERY_EXPECTATION_DUPLICATE");
            }
        }
        plan.forEach(item -> Objects.requireNonNull(expected.get(item.definition().id())));
        final Map<RuntimeWorkloadId, RuntimeExecutionResult> results = new HashMap<>();
        final Map<RuntimeWorkloadId, RuntimeRecoverySnapshot> snapshots = new HashMap<>();
        final Map<RuntimeWorkloadId, RuntimeException> causes = new HashMap<>();
        final CycleOutcomeAccumulator aggregate = new CycleOutcomeAccumulator();
        final FailurePolicyMatrix matrix = new FailurePolicyMatrix();
        plan.forEach(
                item -> {
                    final var id = item.definition().id();
                    final var start =
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
                                    clock.instant());
                    final var session =
                            new RuntimeExecutionSession(item, start, control, clock, cancellation);
                    RuntimeRecoverySnapshot snapshot;
                    try {
                        final var expectation = expected.get(id);
                        final var request =
                                new RuntimeRecoveryRequest(
                                        start,
                                        plan.fingerprint(),
                                        expectation.contract(),
                                        expectation.policy());
                        // Mesmo cancelado, uma consulta limitada pode confirmar um commit anterior.
                        snapshot =
                                durable.read(
                                        request,
                                        cancellation.isCancellationRequested()
                                                ? CancellationToken.none()
                                                : cancellation);
                        if (snapshot.reason() == RuntimeRecoverySnapshot.Reason.ELIGIBLE) {
                            final boolean[] blocked = {false};
                            item.definition()
                                    .forEachDependency(
                                            dependency -> {
                                                final var predecessor = results.get(dependency);
                                                if (predecessor == null
                                                        || predecessor.status()
                                                                != RuntimeExecutionResult.Status
                                                                        .PUBLISHED) {
                                                    blocked[0] = true;
                                                }
                                            });
                            if (cancellation.isCancellationRequested()) {
                                snapshot =
                                        snapshot.withReason(
                                                RuntimeRecoverySnapshot.Reason.CANCELLED);
                            } else if (blocked[0]) {
                                snapshot =
                                        snapshot.withReason(
                                                RuntimeRecoverySnapshot.Reason
                                                        .DEPENDENCY_NOT_PUBLISHED);
                            } else {
                                try {
                                    durable.resume(request, snapshot.revision(), cancellation);
                                } catch (final RuntimeException exception) {
                                    causes.put(id, exception);
                                }
                                // Ack perdido é conciliado por leitura, nunca repetido neste turno.
                                snapshot = durable.read(request, CancellationToken.none());
                                if (snapshot.reason() == RuntimeRecoverySnapshot.Reason.ELIGIBLE
                                        && causes.containsKey(id)) {
                                    snapshot =
                                            snapshot.withReason(
                                                    cancellation.isCancellationRequested()
                                                            ? RuntimeRecoverySnapshot.Reason
                                                                    .CANCELLED
                                                            : causes.get(id)
                                                                            instanceof
                                                                            RuntimeRecoveryException
                                                                                            recoveryFailure
                                                                    ? recoveryFailure.reason()
                                                                    : RuntimeRecoverySnapshot.Reason
                                                                            .UNAVAILABLE);
                                }
                            }
                        }
                    } catch (final RuntimeException exception) {
                        causes.merge(
                                id,
                                exception,
                                (prior, next) -> {
                                    prior.addSuppressed(next);
                                    return prior;
                                });
                        snapshot =
                                RuntimeRecoverySnapshot.refused(
                                        exception
                                                        instanceof
                                                        RuntimeRecoveryException recoveryFailure
                                                ? recoveryFailure.reason()
                                                : RuntimeRecoverySnapshot.Reason.UNAVAILABLE);
                    }
                    snapshots.put(id, snapshot);
                    final var result = session.recovered(snapshot);
                    results.put(id, result);
                    if (result.status() == RuntimeExecutionResult.Status.PUBLISHED) {
                        aggregate.recordPublished();
                    } else {
                        aggregate.recordDecision(
                                matrix.decide(
                                        result.failure().orElseThrow(),
                                        FailurePolicyContext.exhausted()));
                    }
                });
        return new RuntimeRecoveryDispatchResult(
                new RuntimeDispatchResult(results, aggregate.snapshot()), snapshots, causes);
    }

    private void validate(final RuntimeExecutionPlan plan) {
        final Map<RuntimeWorkloadId, RuntimeExecutionPlanItem> preceding = new HashMap<>();
        final Map<String, String> kinds = new HashMap<>();
        plan.forEach(
                item -> {
                    if (!handlers.containsKey(item.definition().id())) {
                        throw new IllegalArgumentException("Handler obrigatório ausente.");
                    }
                    final String kind =
                            kinds.putIfAbsent(
                                    item.definition().sourceInstance(),
                                    item.definition().sourceKind());
                    if (kind != null && !kind.equals(item.definition().sourceKind())) {
                        throw new IllegalArgumentException("Família de fonte divergente.");
                    }
                    item.definition()
                            .forEachDependency(
                                    dependency -> {
                                        final RuntimeExecutionPlanItem previous =
                                                preceding.get(dependency);
                                        if (previous == null
                                                || !sameScopeAndWindow(
                                                        previous.partition(), item.partition())) {
                                            throw new IllegalArgumentException(
                                                    "Dependência diverge do namespace ou janela.");
                                        }
                                    });
                    preceding.put(item.definition().id(), item);
                });
    }

    private static boolean sameScopeAndWindow(
            final ExecutionPartitionKey left, final ExecutionPartitionKey right) {
        return left.environment().equals(right.environment())
                && left.sourceInstance().equals(right.sourceInstance())
                && left.tenantScope().equals(right.tenantScope())
                && left.mode() == right.mode()
                && left.partitionStart().equals(right.partitionStart())
                && left.partitionEndExclusive().equals(right.partitionEndExclusive());
    }
}
