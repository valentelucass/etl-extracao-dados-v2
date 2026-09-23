package br.com.esl.etl.v2.bootstrap;

import br.com.esl.etl.v2.plataforma.autorizacao.RuntimeAction;
import br.com.esl.etl.v2.plataforma.autorizacao.WindowsSqlRuntimeAuthorization;
import br.com.esl.etl.v2.plataforma.configuracao.RuntimeConfiguration;
import br.com.esl.etl.v2.plataforma.orquestracao.RuntimeTemporalCoordinator;
import br.com.esl.etl.v2.plataforma.orquestracao.RuntimeTemporalPlanner;
import br.com.esl.etl.v2.plataforma.persistencia.controle.BoundedRuntimeDataSource;
import br.com.esl.etl.v2.plataforma.persistencia.controle.JdbcSqlServerTemporalPlan;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Consumer;
import javax.sql.DataSource;

/** An explicit execution of one planned window, with a separate receipt for plan persistence. */
final class RuntimeTemporalBinding {
    final RuntimeTemporalOperation operation;
    final RuntimeTemporalPlanner.Window window;
    private final ObjectNode document;

    private RuntimeTemporalBinding(final ObjectNode policy, final int ordinal) {
        document = policy.deepCopy();
        operation = new RuntimeTemporalOperation(document);
        if (ordinal < 1 || ordinal > operation.result.windows().size()) {
            throw new IllegalArgumentException("TEMPORAL_WINDOW_ORDINAL");
        }
        window = operation.result.windows().get(ordinal - 1);
    }

    static RuntimeTemporalBinding read(
            final RuntimeConfiguration configuration, final JsonNode root) throws Exception {
        if (!root.has("temporalPolicy") && !root.has("temporalWindow")) {
            return null;
        }
        if (!root.has("temporalPolicy") || !root.has("temporalWindow")) {
            throw new IllegalArgumentException("TEMPORAL_BINDING_PAIR_REQUIRED");
        }
        final var policy =
                RuntimeOperationalRequest.parseDocument(root.get("temporalPolicy").textValue());
        if (!(policy instanceof ObjectNode object)) {
            throw new IllegalArgumentException("TEMPORAL_BINDING_DOCUMENT");
        }
        final var binding =
                new RuntimeTemporalBinding(
                        object, Integer.parseInt(root.get("temporalWindow").textValue()));
        final var source = configuration.dataExport().orElseThrow();
        if (!"LOCAL_SHADOW".equals(configuration.environment().name())
                || !"LOCAL_V2".equals(source.sourceInstance())
                || !"LOCAL_V2".equals(source.tenantScope())
                || !source.settings().sourceZone().equals(binding.operation.policy.zone())
                || binding.operation.policy.strategy()
                        != br.com.esl.etl.v2.plataforma.orquestracao.RuntimeWindowStrategy
                                .INTERVAL) {
            throw new IllegalArgumentException("TEMPORAL_EXECUTION_LOCAL_NAMESPACE_ZONE_REQUIRED");
        }
        final var expected =
                binding.apply(
                        configuration,
                        root.deepCopy(),
                        UUID.fromString(root.get("invocationId").textValue()));
        for (final String field :
                List.of(
                        "executionId",
                        "cycleId",
                        "template",
                        "mode",
                        "start",
                        "endExclusive",
                        "replayOf",
                        "idempotencyKey",
                        "businessStart",
                        "businessEnd")) {
            if (!expected.get(field).equals(root.get(field))) {
                throw new IllegalArgumentException("TEMPORAL_EXECUTION_BINDING_MISMATCH");
            }
        }
        if ("fretes".equals(binding.operation.workload) && !root.has("dependencyRequest")) {
            throw new IllegalArgumentException("TEMPORAL_FRETES_PREDECESSOR_REQUIRED");
        }
        return binding;
    }

    static ObjectNode request(
            final RuntimeConfiguration configuration,
            final ObjectNode policy,
            final ObjectNode blueprint,
            final int ordinal) {
        final var binding = new RuntimeTemporalBinding(policy, ordinal);
        final UUID invocation =
                UUID.nameUUIDFromBytes(
                        (binding.operation.invocation + "|execute|" + ordinal)
                                .getBytes(StandardCharsets.UTF_8));
        final var result = binding.apply(configuration, blueprint.deepCopy(), invocation);
        result.put("temporalPolicy", binding.canonicalDocument());
        result.put("temporalWindow", Integer.toString(ordinal));
        return result;
    }

    private ObjectNode apply(
            final RuntimeConfiguration configuration,
            final ObjectNode target,
            final UUID invocation) {
        final var frozen = operation.freeze(configuration, window);
        return target.put("invocationId", invocation.toString())
                .put("executionId", frozen.scope().executionId().toString())
                .put("cycleId", frozen.cycle().toString())
                .put("template", operation.workload.toUpperCase(Locale.ROOT))
                .put("mode", operation.policy.mode().name())
                .put("start", window.partitionStart().toString())
                .put("endExclusive", window.endExclusive().toString())
                .put("replayOf", "")
                .put("idempotencyKey", frozen.cycle().toString())
                .put(
                        "businessStart",
                        window.partitionStart()
                                .atZone(operation.policy.zone())
                                .toLocalDate()
                                .toString())
                .put(
                        "businessEnd",
                        window.endExclusive()
                                .atZone(operation.policy.zone())
                                .toLocalDate()
                                .minusDays(1)
                                .toString());
    }

    String canonicalDocument() {
        final var fields = new TreeMap<String, String>();
        document.fields()
                .forEachRemaining(
                        entry -> fields.put(entry.getKey(), entry.getValue().textValue()));
        fields.put("invocation", new UUID(0, 0).toString());
        return new com.fasterxml.jackson.databind.ObjectMapper().valueToTree(fields).toString();
    }

    void initializeIncrementalFrontier(
            final br.com.esl.etl.v2.plataforma.controle.ControlPlane control,
            final br.com.esl.etl.v2.plataforma.controle.ExecutionPartitionKey partition,
            final java.time.Instant observedAt) {
        if (operation.policy.mode()
                        == br.com.esl.etl.v2.plataforma.controle.ExecutionMode.INCREMENTAL
                && window.equals(operation.result.windows().get(0))) {
            if (!partition.partitionStart().equals(window.partitionStart())
                    || !partition.partitionEndExclusive().equals(window.endExclusive())
                    || partition.mode() != operation.policy.mode()
                    || !partition.entity().equals(operation.workload)) {
                throw new IllegalArgumentException("TEMPORAL_INITIAL_FRONTIER_BINDING_MISMATCH");
            }
            // Only the declared first window initializes. SQL refuses an existing different
            // frontier; later windows and recovery never reset it.
            control.registerIncrementalFrontier(partition, window.partitionStart(), observedAt);
        }
    }

    void persistBeforeFirstAttempt(
            final RuntimeConfiguration configuration, final UUID invocation) {
        final var fresh =
                document.deepCopy()
                        .put(
                                "invocation",
                                UUID.nameUUIDFromBytes(
                                                (invocation + "|temporal-persistence")
                                                        .getBytes(StandardCharsets.UTF_8))
                                        .toString());
        final var intent = new RuntimeTemporalOperation(fresh);
        final var frozen = intent.freeze(configuration, window);
        final var authority = WindowsSqlRuntimeAuthorization.fromAdministeredArtifact();
        final var capability = authority.authorize(frozen.scope());
        authority.consumeAndExecute(
                capability,
                frozen.scope(),
                () -> {
                    final int count =
                            new JdbcSqlServerTemporalPlan(
                                            new BoundedRuntimeDataSource(
                                                    configuration.shadowStorage()))
                                    .persist(
                                            frozen.cycle(),
                                            frozen.namespace(),
                                            operation.policy,
                                            new RuntimeTemporalPlanner.Result(
                                                    List.of(window), false, false));
                    if (count != 1) {
                        throw new IllegalStateException("TEMPORAL_EXECUTION_PLAN_UNCONFIRMED");
                    }
                    return count;
                });
    }

    void reconcile(
            final RuntimeConfiguration configuration,
            final DataSource dataSource,
            final Consumer<String> diagnostic) {
        reconcile(configuration, new JdbcSqlServerTemporalPlan(dataSource), diagnostic);
    }

    void reconcile(
            final RuntimeConfiguration configuration,
            final br.com.esl.etl.v2.plataforma.orquestracao.RuntimeTemporalStore store,
            final Consumer<String> diagnostic) {
        final var result =
                new RuntimeTemporalCoordinator(store)
                        .reconcile(
                                operation.freeze(configuration, window).namespace(),
                                operation.policy,
                                operation.result.windows().get(0).partitionStart(),
                                operation.plannedOccurrences(configuration));
        diagnostic.accept(
                "TEMPORAL_EXECUTION_RECONCILIATION contiguous="
                        + result.contiguousEnd()
                        + " pending="
                        + result.pending().size()
                        + " degraded="
                        + result.degraded()
                        + " limit="
                        + result.limitReached());
    }

    void requireAction(final RuntimeAction action) {
        if (action != RuntimeAction.RUN && action != RuntimeAction.STATUS) {
            throw new IllegalArgumentException("TEMPORAL_EXECUTION_RUN_OR_STATUS_ONLY");
        }
    }
}
