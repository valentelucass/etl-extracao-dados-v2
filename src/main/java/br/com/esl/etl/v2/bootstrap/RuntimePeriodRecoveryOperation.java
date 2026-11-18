package br.com.esl.etl.v2.bootstrap;

import br.com.esl.etl.v2.plataforma.autorizacao.RuntimeAction;
import br.com.esl.etl.v2.plataforma.autorizacao.RuntimeAuthorizationScope;
import br.com.esl.etl.v2.plataforma.autorizacao.WindowsSqlRuntimeAuthorization;
import br.com.esl.etl.v2.plataforma.configuracao.RuntimeConfiguration;
import br.com.esl.etl.v2.plataforma.orquestracao.RuntimePeriodRecovery;
import br.com.esl.etl.v2.plataforma.orquestracao.RuntimeTemporalPlanner;
import br.com.esl.etl.v2.plataforma.orquestracao.RuntimeTemporalPolicy;
import br.com.esl.etl.v2.plataforma.orquestracao.RuntimeTemporalStore;
import br.com.esl.etl.v2.plataforma.persistencia.controle.BoundedRuntimeDataSource;
import br.com.esl.etl.v2.plataforma.persistencia.controle.JdbcSqlServerTemporalPlan;
import br.com.esl.etl.v2.plataforma.resiliencia.RuntimeExitCategory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Explicit recovery command. Every SQL read, plan write and source dispatch retains authority. */
final class RuntimePeriodRecoveryOperation {
    private RuntimePeriodRecoveryOperation() {}

    static int run(
            final RuntimeConfiguration configuration,
            final RuntimeTemporalOperation operation,
            final Path blueprint,
            final Consumer<String> diagnostic)
            throws Exception {
        final var composition = new RuntimeCompositionRoot(configuration);
        composition.validateConfiguration();
        BoundedRuntimeDataSource.validateTarget(configuration.shadowStorage());
        final var requests = requests(configuration, operation, blueprint);
        if (requests.isEmpty()) {
            diagnostic.accept(
                    "PERIOD_RECOVERY no_due_window=1 backlog="
                            + operation.result.backlogRemaining()
                            + " blackout="
                            + operation.result.blockedByBlackout()
                            + " scheduler=0");
            return operation.result.blockedByBlackout() ? RuntimeExitCategory.SOURCE_DQ.code() : 0;
        }
        final String namespace =
                operation.freeze(configuration, operation.result.windows().get(0)).namespace();
        final var sql =
                new JdbcSqlServerTemporalPlan(
                        new BoundedRuntimeDataSource(configuration.shadowStorage()));
        final var store =
                new AuthorizedStore(
                        operation,
                        namespace,
                        requests,
                        sql,
                        new Authority() {
                            @Override
                            public <T> T execute(
                                    final RuntimeAuthorizationScope scope,
                                    final Supplier<T> effect) {
                                final var authority =
                                        WindowsSqlRuntimeAuthorization.fromAdministeredArtifact();
                                return authority.consumeAndExecute(
                                        authority.authorize(scope), scope, effect);
                            }
                        });
        final var exit = new RuntimeExitCategory[] {RuntimeExitCategory.SUCCESS};
        final var result =
                new RuntimePeriodRecovery(store)
                        .recover(
                                namespace,
                                operation.policy,
                                operation.first,
                                operation.observed,
                                (pending, window) ->
                                        exit[0] =
                                                exit[0].combine(
                                                        composition.executeFrozen(
                                                                requests.get(
                                                                        window.partitionStart()),
                                                                RuntimeAction.RUN,
                                                                diagnostic,
                                                                null)));
        diagnostic.accept(
                "PERIOD_RECOVERY contiguous="
                        + result.contiguousEnd()
                        + " resumeFrom="
                        + result.contiguousEnd().atZone(operation.policy.zone()).toLocalDate()
                        + " dispatched="
                        + result.dispatched()
                        + " pending="
                        + result.pending().size()
                        + " action="
                        + (result.pending().isEmpty() ? "NONE" : result.pending().get(0).action())
                        + " backlog="
                        + result.backlogRemaining()
                        + " blackout="
                        + result.blockedByBlackout()
                        + " scheduler=0");
        if (!result.pending().isEmpty() || result.blockedByBlackout()) {
            exit[0] = exit[0].combine(RuntimeExitCategory.SOURCE_DQ);
        } else if (result.backlogRemaining()) {
            exit[0] = exit[0].combine(RuntimeExitCategory.DEGRADED);
        }
        return exit[0].code();
    }

    static Map<Instant, RuntimeOperationalRequest> requests(
            final RuntimeConfiguration configuration,
            final RuntimeTemporalOperation operation,
            final Path blueprint)
            throws Exception {
        if (operation.policy.mode() != br.com.esl.etl.v2.plataforma.controle.ExecutionMode.BACKFILL
                || !operation.policy.lookback().isZero()) {
            throw new IllegalArgumentException("PERIOD_RECOVERY_FULL_BUSINESS_RANGE_REQUIRED");
        }
        final var requests = new LinkedHashMap<Instant, RuntimeOperationalRequest>();
        for (final var window : operation.result.windows()) {
            final var documents =
                    new com.fasterxml.jackson.databind.ObjectMapper()
                            .readTree(
                                    operation
                                            .forRecoveryWindow(window)
                                            .operationalRequests(configuration, blueprint));
            if (documents.size() != 1) {
                throw new IllegalArgumentException("ONE_RECOVERY_OCCURRENCE_REQUIRED");
            }
            final var request =
                    RuntimeOperationalRequest.fromDocument(configuration, documents.get(0));
            requests.put(request.temporal.window.partitionStart(), request);
        }
        return Map.copyOf(requests);
    }

    interface Authority {
        <T> T execute(RuntimeAuthorizationScope scope, Supplier<T> effect);
    }

    // Package-local test seam; no CLI, environment or configuration override for authority.
    static final class AuthorizedStore implements RuntimeTemporalStore {
        private final RuntimeTemporalOperation operation;
        private final String namespace;
        private final Map<Instant, RuntimeOperationalRequest> requests;
        private final RuntimeTemporalStore delegate;
        private final Authority authority;
        private int reads;

        AuthorizedStore(
                final RuntimeTemporalOperation operation,
                final String namespace,
                final Map<Instant, RuntimeOperationalRequest> requests,
                final RuntimeTemporalStore delegate,
                final Authority authority) {
            this.operation = operation;
            this.namespace = namespace;
            this.requests = Map.copyOf(requests);
            this.delegate = delegate;
            this.authority = authority;
        }

        @Override
        public int persist(
                final UUID plan,
                final String namespace,
                final RuntimeTemporalPolicy policy,
                final RuntimeTemporalPlanner.Result result) {
            if (result.windows().size() != 1) {
                throw new IllegalArgumentException("ONE_OCCURRENCE_PER_CAPABILITY_REQUIRED");
            }
            final var window = result.windows().get(0);
            final var request = requireRequest(namespace, window.partitionStart());
            if (!window.equals(request.temporal.window)
                    || !policy.equals(operation.policy)
                    || !plan.equals(request.plan.cycleId())) {
                throw new IllegalArgumentException("PERIOD_RECOVERY_PERSISTENCE_SCOPE_MISMATCH");
            }
            return authorized(
                    request,
                    RuntimeAction.RUN,
                    "plan",
                    () -> delegate.persist(plan, namespace, policy, result));
        }

        @Override
        public List<Gap> readGapPage(
                final String namespace, final int maximum, final Instant after) {
            final var request = requireRequest(namespace, after);
            if (maximum < 1 || maximum > operation.policy.maximumReconciliation()) {
                throw new IllegalArgumentException("TEMPORAL_RECONCILIATION_LIMIT");
            }
            return authorized(
                    request,
                    RuntimeAction.STATUS,
                    "read-" + ++reads,
                    () -> delegate.readGapPage(namespace, maximum, after));
        }

        private RuntimeOperationalRequest requireRequest(
                final String actualNamespace, final Instant start) {
            final var request = requests.get(start);
            if (!namespace.equals(actualNamespace) || request == null) {
                throw new IllegalArgumentException("PERIOD_RECOVERY_NAMESPACE_WINDOW_MISMATCH");
            }
            return request;
        }

        private <T> T authorized(
                final RuntimeOperationalRequest request,
                final RuntimeAction action,
                final String phase,
                final Supplier<T> effect) {
            final UUID invocation =
                    UUID.nameUUIDFromBytes(
                            (operation.invocation
                                            + "|period-recovery|"
                                            + phase
                                            + "|"
                                            + request.plan.cycleId())
                                    .getBytes(StandardCharsets.UTF_8));
            final var scope =
                    RuntimeAuthorizationScope.from(
                            invocation, action, request.plan, request.referenceReleaseId);
            return authority.execute(scope, effect);
        }
    }
}
