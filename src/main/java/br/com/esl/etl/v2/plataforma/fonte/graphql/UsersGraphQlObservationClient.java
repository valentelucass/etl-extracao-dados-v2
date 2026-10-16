package br.com.esl.etl.v2.plataforma.fonte.graphql;

import br.com.esl.etl.v2.modulos.usuarios.aplicacao.UsuarioGraphQlNodeMapper;
import br.com.esl.etl.v2.modulos.usuarios.domain.UsuarioStageDisposition;
import br.com.esl.etl.v2.modulos.usuarios.domain.UsuarioStageRecord;
import br.com.esl.etl.v2.plataforma.configuracao.GraphQlRuntimeConfigurationFingerprint;
import br.com.esl.etl.v2.plataforma.configuracao.GraphQlSourceConfiguration;
import br.com.esl.etl.v2.plataforma.configuracao.SecretProvider;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken;
import br.com.esl.etl.v2.plataforma.resiliencia.EslRequestGovernor;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Supplier;

/** Opt-in, memory-only observation of one initial Users page; never a snapshot traversal. */
public final class UsersGraphQlObservationClient {

    private final GraphQlGateway gateway;
    private final CancellationToken cancellation;
    private final Runnable deadlineCheckpoint;
    private final AtomicBoolean used = new AtomicBoolean();
    private final BooleanSupplier sourceWindow;

    UsersGraphQlObservationClient(
            final GraphQlGateway gateway,
            final CancellationToken cancellation,
            final Runnable deadlineCheckpoint) {
        this(gateway, cancellation, deadlineCheckpoint, () -> true);
    }

    UsersGraphQlObservationClient(
            final GraphQlGateway gateway,
            final CancellationToken cancellation,
            final Runnable deadlineCheckpoint,
            final BooleanSupplier sourceWindow) {
        requireWindow(sourceWindow);
        this.gateway = Objects.requireNonNull(gateway, "USERS_OBS_GATEWAY_REQUIRED");
        this.cancellation = Objects.requireNonNull(cancellation, "USERS_OBS_CANCEL_REQUIRED");
        this.deadlineCheckpoint =
                Objects.requireNonNull(deadlineCheckpoint, "USERS_OBS_DEADLINE_REQUIRED");
        this.sourceWindow = sourceWindow;
    }

    public static UsersGraphQlObservationClient create(
            final boolean enabled,
            final GraphQlSourceConfiguration source,
            final SecretProvider secrets,
            final EslRequestGovernor governor,
            final EslRequestGovernor.Cycle cycle,
            final Clock clock,
            final GraphQlContractObservationConfiguration observation,
            final CancellationToken cancellation) {
        throw new IllegalStateException("SOURCE_WINDOW_CLOSED");
    }

    /** SRC-WINDOW-001: source and token are resolved only inside an explicitly admitted window. */
    public static UsersGraphQlObservationClient createForSourceWindow(
            final BooleanSupplier sourceWindow,
            final boolean enabled,
            final Supplier<GraphQlSourceConfiguration> sourceProvider,
            final SecretProvider secrets,
            final EslRequestGovernor governor,
            final EslRequestGovernor.Cycle cycle,
            final Clock clock,
            final GraphQlContractObservationConfiguration observation,
            final CancellationToken cancellation) {
        requireWindow(sourceWindow);
        final var source =
                Objects.requireNonNull(sourceProvider, "USERS_OBS_SOURCE_PROVIDER_REQUIRED").get();
        requireWindow(sourceWindow);
        validateLaunch(enabled, source, System::getProperty);
        Objects.requireNonNull(clock, "USERS_OBS_CLOCK_REQUIRED");
        Objects.requireNonNull(observation, "USERS_OBS_CONTRACT_REQUIRED");
        Objects.requireNonNull(cancellation, "USERS_OBS_CANCEL_REQUIRED");
        Objects.requireNonNull(cycle, "USERS_OBS_CYCLE_REQUIRED")
                .verifyCancellationToken(cancellation);
        cycle.verifyGovernor(Objects.requireNonNull(governor, "USERS_OBS_GOVERNOR_REQUIRED"));
        cycle.verifyPolicy(source.resiliencePolicy());
        governor.verifyPolicy(source.resiliencePolicy());
        if (observation.operation() != GraphQlReadOperation.USERS_SNAPSHOT
                || !observation
                        .runtimeConfigurationFingerprint()
                        .equals(GraphQlRuntimeConfigurationFingerprint.from(source))) {
            throw new IllegalArgumentException("USERS_OBS_CONTRACT_MISMATCH");
        }
        cancellation.throwIfCancellationRequested();
        requireWindow(sourceWindow);
        final var factory = new GraphQlHttpGatewayFactory(source, secrets, governor, clock);
        final AtomicBoolean attempted = new AtomicBoolean();
        // The raw observed gateway retains strict parsing; snapshot acceptance is deliberately
        // absent.
        final GraphQlGateway gateway =
                factory.observedForOperation(
                        cycle,
                        GraphQlReadOperation.USERS_SNAPSHOT,
                        observation,
                        operation -> {
                            requireWindow(sourceWindow);
                            cancellation.throwIfCancellationRequested();
                            if (operation != GraphQlReadOperation.USERS_SNAPSHOT
                                    || !attempted.compareAndSet(false, true)) {
                                throw new IllegalStateException("USERS_OBS_HTTP_CAP");
                            }
                        });
        return new UsersGraphQlObservationClient(
                gateway,
                cancellation,
                () -> cycle.beginWorkload(GraphQlReadOperation.USERS_SNAPSHOT.workload()),
                sourceWindow);
    }

    private static void requireWindow(final BooleanSupplier window) {
        try {
            if (window != null && window.getAsBoolean()) {
                return;
            }
        } catch (final RuntimeException ignored) {
            // Do not leak caller details or treat unknown admission as open.
        }
        throw new IllegalStateException("SOURCE_WINDOW_CLOSED");
    }

    static void validateLaunch(
            final boolean enabled,
            final GraphQlSourceConfiguration source,
            final Function<String, String> properties) {
        if (!enabled) {
            throw new IllegalArgumentException("USERS_OBS_OPT_IN_REQUIRED");
        }
        Objects.requireNonNull(source, "USERS_OBS_SOURCE_REQUIRED");
        final var settings = source.settings();
        final var policy = source.resiliencePolicy();
        if (!"https".equalsIgnoreCase(settings.endpoint().getScheme())
                || settings.retryPolicy().maxAttempts() != 1
                || settings.requestTimeout().compareTo(Duration.ofSeconds(30)) > 0
                || settings.maxResponseBytes() > 10L * 1024L * 1024L
                || policy.minimumRequestInterval().compareTo(Duration.ofSeconds(2)) < 0
                || policy.maxRequestsPerCycle() != 1
                || policy.maxRequestsPerWorkload() != 1
                || policy.maxRepartitions() != 0
                || !policy.requestTimeout().equals(settings.requestTimeout())
                || policy.cycleTimeout().compareTo(Duration.ofSeconds(60)) > 0) {
            throw new IllegalArgumentException("USERS_OBS_LIMITS_INVALID");
        }
        Objects.requireNonNull(properties, "USERS_OBS_PROPERTIES_REQUIRED");
        if (!"true".equals(properties.apply("jdk.httpclient.disableRetryConnect"))
                || !"false".equals(properties.apply("jdk.httpclient.enableAllMethodRetry"))
                || !"1".equals(properties.apply("jdk.httpclient.redirects.retrylimit"))
                || "true"
                        .equalsIgnoreCase(
                                properties.apply(
                                        "jdk.internal.httpclient.disableHostnameVerification"))) {
            throw new IllegalArgumentException("USERS_OBS_JDK_RETRY_POLICY_REQUIRED");
        }
    }

    public Receipt observe() {
        requireWindow(sourceWindow);
        if (!used.compareAndSet(false, true)) {
            throw new IllegalStateException("USERS_OBS_ALREADY_USED");
        }
        cancellation.throwIfCancellationRequested();
        deadlineCheckpoint.run();
        requireWindow(sourceWindow);
        final GraphQlPageResponse page =
                gateway.fetch(
                        GraphQlPageRequest.initial(
                                GraphQlReadOperation.USERS_SNAPSHOT,
                                GraphQlQueryParameters.enabledUsers(),
                                1));
        cancellation.throwIfCancellationRequested();
        requireWindow(sourceWindow);
        if (page.nodeCount() > 1
                || page.distinctRootKeys() != page.nodeCount()
                || page.contractObservation().isEmpty()
                || page.hasNextPage() && page.endCursor().isEmpty()) {
            throw new IllegalStateException("USERS_OBS_PAGE_INVALID");
        }
        final var mapper = new UsuarioGraphQlNodeMapper();
        final List<UsuarioStageRecord> records = new ArrayList<>(1);
        page.forEachNode(node -> records.add(mapper.map(records.size() + 1, node)));
        cancellation.throwIfCancellationRequested();
        deadlineCheckpoint.run();
        requireWindow(sourceWindow);
        return new Receipt(records, page.hasNextPage(), page.responseBytes());
    }

    /**
     * Typed data remains in memory; this receipt is neither SQL retention nor contract approval.
     */
    public static final class Receipt {
        private final List<UsuarioStageRecord> records;
        private final boolean hasNextPage;
        private final long responseBytes;

        private Receipt(
                final List<UsuarioStageRecord> records,
                final boolean hasNextPage,
                final long responseBytes) {
            this.records = List.copyOf(records);
            this.hasNextPage = hasNextPage;
            this.responseBytes = responseBytes;
        }

        public List<UsuarioStageRecord> records() {
            return records;
        }

        public boolean hasNextPage() {
            return hasNextPage;
        }

        public long responseBytes() {
            return responseBytes;
        }

        public int httpCalls() {
            return 1;
        }

        public int pageNumber() {
            return 1;
        }

        public int requestedFirst() {
            return 1;
        }

        public int physicalRows() {
            return records.size();
        }

        public long quarantinedRows() {
            return records.stream()
                    .filter(row -> row.disposition() == UsuarioStageDisposition.QUARANTINE)
                    .count();
        }

        public Status status() {
            return records.isEmpty() ? Status.NO_DATA_OBSERVED : Status.PARTIAL_CAPTURE;
        }

        public boolean sourceCompletenessProven() {
            return false;
        }

        public boolean contractAccepted() {
            return false;
        }

        @Override
        public String toString() {
            return "UsersObservation[status="
                    + status()
                    + ", rows="
                    + physicalRows()
                    + ", quarantined="
                    + quarantinedRows()
                    + ", hasNextPage="
                    + hasNextPage
                    + ", httpCalls=1, complete=false]";
        }
    }

    public enum Status {
        NO_DATA_OBSERVED,
        PARTIAL_CAPTURE
    }
}
