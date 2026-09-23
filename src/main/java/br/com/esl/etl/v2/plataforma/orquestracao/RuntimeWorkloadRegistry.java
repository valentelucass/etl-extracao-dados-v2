package br.com.esl.etl.v2.plataforma.orquestracao;

import br.com.esl.etl.v2.plataforma.controle.ExecutionPartitionKey;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Registry tipado que produz uma ordem topológica determinística e recusa dependências ausentes.
 */
public final class RuntimeWorkloadRegistry {

    private final Map<RuntimeWorkloadId, RuntimeWorkloadDefinition> definitions;

    private RuntimeWorkloadRegistry(
            final Map<RuntimeWorkloadId, RuntimeWorkloadDefinition> definitions) {
        this.definitions = Map.copyOf(definitions);
    }

    public static RuntimeWorkloadRegistry of(
            final RuntimeWorkloadDefinition... workloadDefinitions) {
        if (workloadDefinitions == null
                || workloadDefinitions.length == 0
                || workloadDefinitions.length > 64) {
            throw new IllegalArgumentException("O registry exige entre um e 64 workloads.");
        }
        final Map<RuntimeWorkloadId, RuntimeWorkloadDefinition> definitions = new HashMap<>();
        Arrays.stream(workloadDefinitions)
                .map(definition -> Objects.requireNonNull(definition, "O workload é obrigatório."))
                .forEach(
                        definition -> {
                            if (definitions.putIfAbsent(definition.id(), definition) != null) {
                                throw new IllegalArgumentException(
                                        "O workload foi declarado mais de uma vez.");
                            }
                        });
        definitions
                .values()
                .forEach(
                        definition ->
                                definition.forEachDependency(
                                        dependency -> {
                                            if (!definitions.containsKey(dependency)) {
                                                throw new IllegalArgumentException(
                                                        "Uma dependência do workload não está registrada.");
                                            }
                                        }));
        validateAcyclic(definitions);
        return new RuntimeWorkloadRegistry(definitions);
    }

    public RuntimeExecutionPlan plan(final RuntimePlanningRequest request) {
        Objects.requireNonNull(request, "O pedido de planejamento é obrigatório.");
        final Map<RuntimeWorkloadId, RuntimeExecutionRequest> requested =
                requestedByWorkload(request);
        final List<RuntimeWorkloadId> ids = new ArrayList<>(requested.keySet());
        ids.sort(RuntimeWorkloadId::compareTo);
        final Set<RuntimeWorkloadId> visiting = new HashSet<>();
        final Set<RuntimeWorkloadId> visited = new HashSet<>();
        final List<RuntimeExecutionPlanItem> ordered = new ArrayList<>();
        for (final RuntimeWorkloadId id : ids) {
            visit(id, requested, visiting, visited, ordered, request.environment());
        }
        return new RuntimeExecutionPlan(
                request.cycleId(),
                request.environment(),
                request.plan(),
                request.plannedAt(),
                ordered);
    }

    private Map<RuntimeWorkloadId, RuntimeExecutionRequest> requestedByWorkload(
            final RuntimePlanningRequest request) {
        final Map<RuntimeWorkloadId, RuntimeExecutionRequest> requested = new HashMap<>();
        final Set<java.util.UUID> executionIds = new HashSet<>();
        final Set<String> idempotencyKeys = new HashSet<>();
        request.forEachExecution(
                execution -> {
                    if (!executionIds.add(execution.executionId())
                            || !idempotencyKeys.add(execution.idempotencyKey())) {
                        throw new IllegalArgumentException(
                                "Ocorrência ou idempotência duplicada no plano.");
                    }
                    if (!definitions.containsKey(execution.workloadId())) {
                        throw new IllegalArgumentException(
                                "O workload solicitado não está registrado.");
                    }
                    if (requested.putIfAbsent(execution.workloadId(), execution) != null) {
                        throw new IllegalArgumentException(
                                "Uma mesma partição de workload foi solicitada mais de uma vez.");
                    }
                });
        return requested;
    }

    private void visit(
            final RuntimeWorkloadId id,
            final Map<RuntimeWorkloadId, RuntimeExecutionRequest> requested,
            final Set<RuntimeWorkloadId> visiting,
            final Set<RuntimeWorkloadId> visited,
            final List<RuntimeExecutionPlanItem> ordered,
            final String environment) {
        if (visited.contains(id)) {
            return;
        }
        if (!visiting.add(id)) {
            throw new IllegalArgumentException("O DAG de workloads contém um ciclo.");
        }
        final RuntimeWorkloadDefinition definition = definitions.get(id);
        definition.forEachDependency(
                dependency -> {
                    if (!requested.containsKey(dependency)) {
                        throw new IllegalArgumentException(
                                "Uma dependência obrigatória não foi incluída no plano.");
                    }
                    visit(dependency, requested, visiting, visited, ordered, environment);
                });
        final RuntimeExecutionRequest execution = requested.get(id);
        ordered.add(
                new RuntimeExecutionPlanItem(
                        definition,
                        execution,
                        new ExecutionPartitionKey(
                                environment,
                                definition.sourceInstance(),
                                definition.tenantScope(),
                                definition.entity(),
                                execution.mode(),
                                execution.partitionStart(),
                                execution.partitionEndExclusive())));
        visiting.remove(id);
        visited.add(id);
    }

    private static void validateAcyclic(
            final Map<RuntimeWorkloadId, RuntimeWorkloadDefinition> definitions) {
        final Set<RuntimeWorkloadId> visiting = new HashSet<>();
        final Set<RuntimeWorkloadId> visited = new HashSet<>();
        for (final RuntimeWorkloadId id : definitions.keySet()) {
            validateAcyclic(id, definitions, visiting, visited);
        }
    }

    private static void validateAcyclic(
            final RuntimeWorkloadId id,
            final Map<RuntimeWorkloadId, RuntimeWorkloadDefinition> definitions,
            final Set<RuntimeWorkloadId> visiting,
            final Set<RuntimeWorkloadId> visited) {
        if (visited.contains(id)) {
            return;
        }
        if (!visiting.add(id)) {
            throw new IllegalArgumentException("O DAG de workloads contém um ciclo.");
        }
        definitions
                .get(id)
                .forEachDependency(
                        dependency -> validateAcyclic(dependency, definitions, visiting, visited));
        visiting.remove(id);
        visited.add(id);
    }
}
