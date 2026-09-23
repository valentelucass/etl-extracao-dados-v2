package br.com.esl.etl.v2.plataforma.orquestracao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import br.com.esl.etl.v2.plataforma.controle.ControlPlane;
import br.com.esl.etl.v2.plataforma.controle.ControlPlaneCounts;
import br.com.esl.etl.v2.plataforma.controle.ControlPlaneCycle;
import br.com.esl.etl.v2.plataforma.controle.ControlPlanePage;
import br.com.esl.etl.v2.plataforma.controle.ControlPlaneRecoveryResult;
import br.com.esl.etl.v2.plataforma.controle.ControlPlaneSource;
import br.com.esl.etl.v2.plataforma.controle.ControlPlaneStart;
import br.com.esl.etl.v2.plataforma.controle.ControlPlaneTransition;
import br.com.esl.etl.v2.plataforma.controle.ExecutionMode;
import br.com.esl.etl.v2.plataforma.controle.ExecutionPartitionKey;
import br.com.esl.etl.v2.plataforma.controle.ImmutableFingerprint;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RuntimeWorkloadRegistryTest {

    private static final Instant NOW = Instant.parse("2026-09-04T18:00:00Z");
    private static final Instant START = Instant.parse("2026-09-03T03:00:00Z");
    private static final Instant END = Instant.parse("2026-09-04T03:00:00Z");
    private static final ImmutableFingerprint PLAN =
            new ImmutableFingerprint("runtime-plan-v1", "a".repeat(64));
    private static final ImmutableFingerprint CONTRACT =
            new ImmutableFingerprint("contract-v1", "b".repeat(64));
    private static final ImmutableFingerprint CONFIGURATION =
            new ImmutableFingerprint("configuration-v1", "c".repeat(64));

    @Test
    void plansDependenciesBeforeDependentsRegardlessOfRequestOrder() {
        final RuntimeWorkloadId coletas = new RuntimeWorkloadId("coletas");
        final RuntimeWorkloadId fretes = new RuntimeWorkloadId("fretes");
        final RuntimeExecutionPlan plan =
                RuntimeWorkloadRegistry.of(definition(coletas), definition(fretes, coletas))
                        .plan(request(fretes, coletas));
        final StringBuilder order = new StringBuilder();

        plan.forEach(item -> order.append(item.definition().id().value()).append(';'));

        assertEquals("coletas;fretes;", order.toString());
        assertEquals("LOCAL_SHADOW", plan.environment());
        assertEquals(2, plan.executionCount());
    }

    @Test
    void rejectsPlanThatOmitsAnObligatoryDependency() {
        final RuntimeWorkloadId coletas = new RuntimeWorkloadId("coletas");
        final RuntimeWorkloadId fretes = new RuntimeWorkloadId("fretes");
        final RuntimePlanningRequest request =
                new RuntimePlanningRequest(
                        UUID.fromString("00000000-0000-0000-0000-000000000701"),
                        "LOCAL_SHADOW",
                        PLAN,
                        NOW,
                        execution(fretes, "fretes-only"));

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        RuntimeWorkloadRegistry.of(definition(coletas), definition(fretes, coletas))
                                .plan(request));
    }

    @Test
    void rejectsCyclesInTheDeclaredDag() {
        final RuntimeWorkloadId coletas = new RuntimeWorkloadId("coletas");
        final RuntimeWorkloadId fretes = new RuntimeWorkloadId("fretes");

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        RuntimeWorkloadRegistry.of(
                                definition(coletas, fretes), definition(fretes, coletas)));
    }

    @Test
    void persistsOnlyRecoverySourceCycleAndPlannedOccurrences() {
        final RuntimeWorkloadId coletas = new RuntimeWorkloadId("coletas");
        final RuntimeWorkloadId fretes = new RuntimeWorkloadId("fretes");
        final RuntimeExecutionPlan plan =
                RuntimeWorkloadRegistry.of(definition(coletas), definition(fretes, coletas))
                        .plan(request(fretes, coletas));
        final RecordingControlPlane controlPlane = new RecordingControlPlane();

        final RuntimePlanPersistenceReceipt receipt =
                new RuntimePlanPersistence(controlPlane).persist(plan);

        assertEquals(1, controlPlane.recoveryCalls);
        assertEquals(1, controlPlane.cycleStarts);
        assertEquals(1, controlPlane.sourceRegistrations);
        assertEquals(2, controlPlane.executionStarts);
        assertEquals(2, receipt.plannedExecutions());
        assertEquals(3, receipt.recoveredExecutions());
    }

    @Test
    void renewsLeaseAndCancelsOnlyAnAlreadyPlannedExecution() {
        final RuntimeWorkloadId coletas = new RuntimeWorkloadId("coletas");
        final RuntimeExecutionPlan plan =
                RuntimeWorkloadRegistry.of(definition(coletas)).plan(request(coletas));
        final RecordingControlPlane controlPlane = new RecordingControlPlane();
        final RuntimeExecutionControl control = new RuntimeExecutionControl(controlPlane);
        final RuntimeExecutionPlanItem[] item = new RuntimeExecutionPlanItem[1];
        plan.forEach(value -> item[0] = value);

        control.heartbeat(item[0], NOW);
        control.cancelBeforeStart(item[0], NOW);

        assertEquals(1, controlPlane.heartbeats);
        assertEquals(1, controlPlane.transitions);
    }

    @Test
    void refusesToCreateAPlanWithoutAnExplicitEnvironment() {
        final RuntimeWorkloadId coletas = new RuntimeWorkloadId("coletas");

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new RuntimePlanningRequest(
                                UUID.randomUUID(),
                                null,
                                PLAN,
                                NOW,
                                execution(coletas, "missing-environment")));
    }

    @Test
    void refusesInvalidIdentifiersWindowsAndPersistenceReceipts() {
        final RuntimeWorkloadId coletas = new RuntimeWorkloadId("coletas");

        assertThrows(IllegalArgumentException.class, () -> new RuntimeWorkloadId("Coletas"));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new RuntimeExecutionRequest(
                                UUID.randomUUID(),
                                coletas,
                                ExecutionMode.INCREMENTAL,
                                RuntimeWindowStrategy.INTERVAL,
                                START,
                                START,
                                "same-window"));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new RuntimeExecutionRequest(
                                UUID.randomUUID(),
                                coletas,
                                ExecutionMode.INCREMENTAL,
                                RuntimeWindowStrategy.INTERVAL,
                                START,
                                END,
                                "  "));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new RuntimePlanningRequest(
                                UUID.randomUUID(),
                                " ",
                                PLAN,
                                NOW,
                                execution(coletas, "environment")));
        assertThrows(
                IllegalArgumentException.class,
                () -> new RuntimePlanPersistenceReceipt(UUID.randomUUID(), 0, 0, 0));
    }

    @Test
    void refusesUnknownAndDuplicateWorkloadsBeforePersistingAPlan() {
        final RuntimeWorkloadId coletas = new RuntimeWorkloadId("coletas");
        final RuntimeWorkloadId fretes = new RuntimeWorkloadId("fretes");
        final RuntimeWorkloadRegistry registry = RuntimeWorkloadRegistry.of(definition(coletas));
        final RuntimePlanningRequest duplicate =
                new RuntimePlanningRequest(
                        UUID.randomUUID(),
                        "LOCAL_SHADOW",
                        PLAN,
                        NOW,
                        execution(coletas, "first"),
                        execution(coletas, "second"));

        assertThrows(IllegalArgumentException.class, () -> registry.plan(request(fretes)));
        assertThrows(IllegalArgumentException.class, () -> registry.plan(duplicate));
    }

    @Test
    void refusesPlanItemsThatDoNotMatchTheirWorkloadDefinition() {
        final RuntimeWorkloadId coletas = new RuntimeWorkloadId("coletas");
        final RuntimeWorkloadId fretes = new RuntimeWorkloadId("fretes");

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new RuntimeExecutionPlanItem(
                                definition(coletas),
                                execution(fretes, "mismatch"),
                                new ExecutionPartitionKey(
                                        "LOCAL_SHADOW",
                                        "synthetic-esl",
                                        "synthetic-tenant",
                                        "coletas",
                                        ExecutionMode.INCREMENTAL,
                                        START,
                                        END)));
    }

    private static RuntimeWorkloadDefinition definition(
            final RuntimeWorkloadId id, final RuntimeWorkloadId... dependencies) {
        return new RuntimeWorkloadDefinition(
                id,
                "ESL",
                "synthetic-esl",
                "synthetic-tenant",
                id.value(),
                CONTRACT,
                CONFIGURATION,
                Duration.ofMinutes(5),
                dependencies);
    }

    private static RuntimePlanningRequest request(final RuntimeWorkloadId... ids) {
        final RuntimeExecutionRequest[] requests = new RuntimeExecutionRequest[ids.length];
        for (int index = 0; index < ids.length; index++) {
            requests[index] = execution(ids[index], "key-" + ids[index].value());
        }
        return new RuntimePlanningRequest(
                UUID.fromString("00000000-0000-0000-0000-000000000700"),
                "LOCAL_SHADOW",
                PLAN,
                NOW,
                requests);
    }

    private static RuntimeExecutionRequest execution(
            final RuntimeWorkloadId id, final String idempotencyKey) {
        return new RuntimeExecutionRequest(
                UUID.nameUUIDFromBytes(
                        idempotencyKey.getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                id,
                ExecutionMode.INCREMENTAL,
                RuntimeWindowStrategy.INTERVAL,
                START,
                END,
                idempotencyKey);
    }

    private static final class RecordingControlPlane implements ControlPlane {
        private int recoveryCalls;
        private int sourceRegistrations;
        private int cycleStarts;
        private int executionStarts;
        private int heartbeats;
        private int transitions;

        @Override
        public void registerSource(final ControlPlaneSource source) {
            sourceRegistrations++;
        }

        @Override
        public void startCycle(final ControlPlaneCycle cycle) {
            cycleStarts++;
        }

        @Override
        public void startExecution(final ControlPlaneStart start) {
            executionStarts++;
        }

        @Override
        public void heartbeat(
                final UUID executionId, final Instant heartbeatAt, final Duration leaseExtension) {
            heartbeats++;
        }

        @Override
        public void recordPage(final ControlPlanePage page) {}

        @Override
        public void recordCounts(final ControlPlaneCounts counts) {}

        @Override
        public void transition(final ControlPlaneTransition transition) {
            transitions++;
        }

        @Override
        public void registerIncrementalFrontier(
                final ExecutionPartitionKey incrementalPartition,
                final Instant initialContiguousEnd,
                final Instant registeredAt) {}

        @Override
        public ControlPlaneRecoveryResult recoverStaleExecutions() {
            recoveryCalls++;
            return new ControlPlaneRecoveryResult(3);
        }
    }
}
