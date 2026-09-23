package br.com.esl.etl.v2.plataforma.autorizacao;

import br.com.esl.etl.v2.plataforma.orquestracao.RuntimeExecutionPlan;
import br.com.esl.etl.v2.plataforma.orquestracao.RuntimeExecutionPlanItem;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;

/** Scope is descriptive, never an ALLOW. All execution material is bound before authority I/O. */
public final class RuntimeAuthorizationScope {
    private final UUID invocation;
    private final UUID execution;
    private final RuntimeAction action;
    private final String material;
    private final String hash;

    private RuntimeAuthorizationScope(
            final UUID invocation,
            final RuntimeAction action,
            final RuntimeExecutionPlan plan,
            final RuntimeExecutionPlanItem item,
            final long referenceReleaseId) {
        this.invocation = Objects.requireNonNull(invocation);
        this.action = Objects.requireNonNull(action);
        execution = item.request().executionId();
        final var p = item.partition();
        final var r = item.request();
        final var d = item.definition();
        if (action == RuntimeAction.REPLAY && r.replayOfExecutionId().isEmpty()
                || action != RuntimeAction.REPLAY
                        && action != RuntimeAction.STATUS
                        && p.mode() == br.com.esl.etl.v2.plataforma.controle.ExecutionMode.REPLAY
                || action == RuntimeAction.SWEEP_APPLY
                || action == RuntimeAction.SWEEP_PREVIEW) {
            throw new IllegalArgumentException("AUTHORIZATION_ACTION_MODE");
        }
        final var scope =
                JsonNodeFactory.instance
                        .objectNode()
                        .put("version", "runtime-scope-v1")
                        .put("invocation", invocation.toString())
                        .put("action", action.name())
                        .put("execution", execution.toString())
                        .put("environment", p.environment())
                        .put("source", p.sourceInstance())
                        .put("tenant", p.tenantScope())
                        .put("workload", d.id().value())
                        .put("entity", p.entity())
                        .put("mode", p.mode().name())
                        .put("start", p.partitionStart().toString())
                        .put("endExclusive", p.partitionEndExclusive().toString())
                        .put("strategy", r.windowStrategy().name())
                        .put("idempotency", r.idempotencyKey())
                        .put("replayOf", r.replayOfExecutionId().map(UUID::toString).orElse(""))
                        .put("cycle", plan.cycleId().toString())
                        .put("planVersion", plan.fingerprint().version())
                        .put("planHash", plan.fingerprint().sha256())
                        .put("contractVersion", d.contract().version())
                        .put("contractHash", d.contract().sha256())
                        .put("configurationVersion", d.configuration().version())
                        .put("configurationHash", d.configuration().sha256());
        if (referenceReleaseId != 0) {
            if (referenceReleaseId < 1
                    || !p.entity().equals("cotacoes")
                    || p.mode() != br.com.esl.etl.v2.plataforma.controle.ExecutionMode.BACKFILL) {
                throw new IllegalArgumentException("TARIFF_SCOPE_MISMATCH");
            }
            scope.put("version", "runtime-scope-tariff-v1")
                    .put("referenceReleaseId", Long.toString(referenceReleaseId));
        }
        material = scope.toString();
        if (material.length() > 4000) {
            throw new IllegalArgumentException("AUTHORIZATION_SCOPE_LIMIT");
        }
        try {
            hash =
                    HexFormat.of()
                            .formatHex(
                                    MessageDigest.getInstance("SHA-256")
                                            .digest(material.getBytes(StandardCharsets.UTF_16LE)));
        } catch (final NoSuchAlgorithmException impossible) {
            throw new ExceptionInInitializerError(impossible);
        }
    }

    public static RuntimeAuthorizationScope from(
            final UUID invocation, final RuntimeAction action, final RuntimeExecutionPlan plan) {
        return from(invocation, action, plan, 0);
    }

    public static RuntimeAuthorizationScope from(
            final UUID invocation,
            final RuntimeAction action,
            final RuntimeExecutionPlan plan,
            final long referenceReleaseId) {
        Objects.requireNonNull(plan);
        if (plan.executionCount() != 1) {
            throw new IllegalArgumentException("ONE_OCCURRENCE_PER_CAPABILITY_REQUIRED");
        }
        final RuntimeExecutionPlanItem[] item = new RuntimeExecutionPlanItem[1];
        plan.forEach(value -> item[0] = value);
        return new RuntimeAuthorizationScope(invocation, action, plan, item[0], referenceReleaseId);
    }

    public UUID invocationId() {
        return invocation;
    }

    public UUID executionId() {
        return execution;
    }

    public RuntimeAction action() {
        return action;
    }

    String material() {
        return material;
    }

    String hash() {
        return hash;
    }

    @Override
    public String toString() {
        return "RuntimeAuthorizationScope[redacted]";
    }
}
