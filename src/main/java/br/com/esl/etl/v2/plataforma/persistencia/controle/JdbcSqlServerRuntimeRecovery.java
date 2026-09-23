package br.com.esl.etl.v2.plataforma.persistencia.controle;

import br.com.esl.etl.v2.plataforma.contrato.ContractPromotionPermit;
import br.com.esl.etl.v2.plataforma.contrato.ContractSourceKind;
import br.com.esl.etl.v2.plataforma.contrato.SourceDataEffect;
import br.com.esl.etl.v2.plataforma.controle.ControlPlaneStart;
import br.com.esl.etl.v2.plataforma.controle.ExecutionMode;
import br.com.esl.etl.v2.plataforma.controle.ExecutionState;
import br.com.esl.etl.v2.plataforma.controle.ImmutableFingerprint;
import br.com.esl.etl.v2.plataforma.orquestracao.RuntimeRecoveryException;
import br.com.esl.etl.v2.plataforma.orquestracao.RuntimeRecoveryMaterial;
import br.com.esl.etl.v2.plataforma.orquestracao.RuntimeRecoveryPort;
import br.com.esl.etl.v2.plataforma.orquestracao.RuntimeRecoveryRequest;
import br.com.esl.etl.v2.plataforma.orquestracao.RuntimeRecoverySnapshot;
import br.com.esl.etl.v2.plataforma.persistencia.staging.StagingPublicationResult;
import br.com.esl.etl.v2.plataforma.qualidade.DataQualityPolicyReference;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javax.sql.DataSource;

/** API local, sem grants/identidade operacional. Não reemite permits (ADR 0034). */
public final class JdbcSqlServerRuntimeRecovery implements RuntimeRecoveryPort {
    private final DataSource dataSource;
    private final int querySeconds;
    private final int networkMillis;
    private final boolean readOnly;
    private final long referenceReleaseId;

    public JdbcSqlServerRuntimeRecovery(
            final DataSource dataSource,
            final Duration queryTimeout,
            final Duration networkTimeout) {
        this(dataSource, queryTimeout, networkTimeout, false);
    }

    public JdbcSqlServerRuntimeRecovery(
            final DataSource dataSource,
            final Duration queryTimeout,
            final Duration networkTimeout,
            final boolean readOnly) {
        this(dataSource, queryTimeout, networkTimeout, readOnly, 0);
    }

    public JdbcSqlServerRuntimeRecovery(
            final DataSource dataSource,
            final Duration queryTimeout,
            final Duration networkTimeout,
            final boolean readOnly,
            final long referenceReleaseId) {
        if (referenceReleaseId < 0) {
            throw new IllegalArgumentException("TARIFF_REFERENCE_INVALID");
        }
        this.referenceReleaseId = referenceReleaseId;
        this.readOnly = readOnly;
        this.dataSource = Objects.requireNonNull(dataSource);
        querySeconds = seconds(queryTimeout);
        networkMillis = Math.multiplyExact(seconds(networkTimeout), 1000);
        try {
            if (dataSource.getLoginTimeout() < 1 || dataSource.getLoginTimeout() > 30) {
                throw new IllegalArgumentException("RUNTIME_RECOVERY_LOGIN_TIMEOUT_REQUIRED");
            }
        } catch (final SQLException exception) {
            throw new IllegalStateException("RUNTIME_RECOVERY_CONNECTION_CONFIGURATION", exception);
        }
    }

    private static int seconds(final Duration timeout) {
        Objects.requireNonNull(timeout);
        if (timeout.getSeconds() < 1 || timeout.getSeconds() > 30 || timeout.getNano() != 0) {
            throw new IllegalArgumentException("RUNTIME_RECOVERY_TIMEOUT_INVALID");
        }
        return Math.toIntExact(timeout.getSeconds());
    }

    @Override
    public void seal(
            final ControlPlaneStart start,
            final ImmutableFingerprint plan,
            final ContractPromotionPermit permit,
            final DataQualityPolicyReference policy,
            final CancellationToken cancellation) {
        Objects.requireNonNull(permit);
        if (!permit.executionId().equals(start.executionId())
                || !permit.contractFingerprint().equals(start.contract())
                || !permit.configurationFingerprint().equals(start.configuration())
                || permit.dataEffect() != SourceDataEffect.SHADOW_UPSERT
                || !sourceMatches(permit.sourceKind(), permit.documentReference(), start)) {
            throw new IllegalArgumentException("RUNTIME_RECOVERY_PERMIT_MISMATCH");
        }
        call(
                "SEAL",
                start,
                plan,
                RuntimeRecoveryMaterial.contract(permit),
                policy,
                "",
                cancellation);
    }

    @Override
    public RuntimeRecoverySnapshot read(
            final RuntimeRecoveryRequest request, final CancellationToken cancellation) {
        verifyRequestSource(request);
        return call(
                "READ",
                request.start(),
                request.plan(),
                RuntimeRecoveryMaterial.contract(request.contract()),
                request.policy(),
                "",
                cancellation);
    }

    @Override
    public void resume(
            final RuntimeRecoveryRequest request,
            final String revision,
            final CancellationToken cancellation) {
        verifyRequestSource(request);
        if (revision == null || !revision.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("RUNTIME_RECOVERY_REVISION_INVALID");
        }
        call(
                "RESUME",
                request.start(),
                request.plan(),
                RuntimeRecoveryMaterial.contract(request.contract()),
                request.policy(),
                revision,
                cancellation);
    }

    private RuntimeRecoverySnapshot call(
            final String operation,
            final ControlPlaneStart start,
            final ImmutableFingerprint plan,
            final String material,
            final DataQualityPolicyReference policy,
            final String revision,
            final CancellationToken cancellation) {
        Objects.requireNonNull(cancellation).throwIfCancellationRequested();
        if (readOnly && !operation.equals("READ")) {
            throw new IllegalStateException("STATUS_CANNOT_MUTATE_RUNTIME");
        }
        // Dois workers exclusivos desta chamada: cancel pode bloquear sem impedir network timeout.
        final ScheduledThreadPoolExecutor deadlines =
                new ScheduledThreadPoolExecutor(
                        2,
                        runnable -> {
                            final Thread thread = new Thread(runnable, "runtime-recovery-deadline");
                            thread.setDaemon(true);
                            return thread;
                        });
        final AtomicReference<SQLException> cancellationFailure = new AtomicReference<>();
        try (Connection connection = dataSource.getConnection()) {
            connection.setNetworkTimeout(deadlines, networkMillis);
            try (CallableStatement statement =
                    connection.prepareCall(
                            "{call ctl."
                                    + (readOnly ? "usp_runtime_status" : "usp_runtime_recovery")
                                    + (referenceReleaseId == 0
                                            ? "(?, ?, ?, ?, ?, ?, ?, ?)}"
                                            : "(?, ?, ?, ?, ?, ?, ?, ?, ?)}"))) {
                statement.setQueryTimeout(querySeconds);
                statement.setFetchSize(2);
                statement.setString(1, start.executionId().toString());
                statement.setString(2, operation);
                statement.setString(3, RuntimeRecoveryMaterial.identity(start, plan));
                statement.setString(4, material);
                statement.setString(5, policy.version());
                statement.setString(6, policy.sha256());
                statement.setString(7, revision);
                statement.setString(8, start.partition().entity());
                if (referenceReleaseId != 0) {
                    statement.setLong(9, referenceReleaseId);
                }
                final var cancellationTask =
                        deadlines.scheduleWithFixedDelay(
                                () -> {
                                    if (cancellation.isCancellationRequested()) {
                                        try {
                                            statement.cancel();
                                        } catch (final SQLException exception) {
                                            cancellationFailure.compareAndSet(null, exception);
                                        }
                                    }
                                },
                                0,
                                50,
                                TimeUnit.MILLISECONDS);
                try {
                    cancellation.throwIfCancellationRequested();
                    if (!operation.equals("READ")) {
                        boolean resultSet = statement.execute();
                        while (resultSet || statement.getUpdateCount() != -1) {
                            resultSet =
                                    statement.getMoreResults(
                                            java.sql.Statement.CLOSE_CURRENT_RESULT);
                        }
                        return null;
                    }
                    try (ResultSet rows = statement.executeQuery()) {
                        if (!rows.next()) {
                            throw new IllegalStateException("RUNTIME_RECOVERY_ROW_MISSING");
                        }
                        final RuntimeRecoverySnapshot result = snapshot(rows, start);
                        if (rows.next()) {
                            throw new IllegalStateException("RUNTIME_RECOVERY_ROWS_EXCEEDED");
                        }
                        return result;
                    }
                } finally {
                    cancellationTask.cancel(false);
                }
            }
        } catch (final SQLException exception) {
            if (cancellationFailure.get() != null) {
                exception.addSuppressed(cancellationFailure.get());
            }
            throw new RuntimeRecoveryException(
                    exception.getErrorCode() == 52305
                            ? RuntimeRecoverySnapshot.Reason.STATE_CHANGED
                            : RuntimeRecoverySnapshot.Reason.UNAVAILABLE,
                    exception);
        } catch (final IllegalArgumentException
                | IllegalStateException
                | NullPointerException exception) {
            throw new RuntimeRecoveryException(
                    RuntimeRecoverySnapshot.Reason.INCONSISTENT, exception);
        } finally {
            deadlines.shutdownNow();
        }
    }

    private static boolean sourceMatches(
            final ContractSourceKind kind, final String document, final ControlPlaneStart start) {
        if (start.partition().entity().equals("usuarios")) {
            return kind == ContractSourceKind.GRAPHQL
                    && document.equals(
                            br.com.esl.etl.v2.plataforma.fonte.graphql.GraphQlReadOperation
                                    .USERS_SNAPSHOT
                                    .documentReference())
                    && (start.partition().mode() == ExecutionMode.BACKFILL
                            || start.partition().mode() == ExecutionMode.REPLAY)
                    && start.windowStrategy().equals("FULL");
        }
        return kind == ContractSourceKind.DATA_EXPORT
                && document.equals("dataexport-" + start.partition().entity());
    }

    private static void verifyRequestSource(final RuntimeRecoveryRequest request) {
        if (!sourceMatches(
                request.contract().sourceKind(),
                request.contract().documentReference(),
                request.start())) {
            throw new IllegalArgumentException("RUNTIME_RECOVERY_SOURCE_MISMATCH");
        }
    }

    private static RuntimeRecoverySnapshot snapshot(
            final ResultSet rows, final ControlPlaneStart start) throws SQLException {
        final var reason = RuntimeRecoverySnapshot.Reason.valueOf(rows.getString("reason"));
        final String state = rows.getString("execution_state");
        final Optional<StagingPublicationResult> publication;
        if (reason == RuntimeRecoverySnapshot.Reason.PUBLISHED) {
            final var utc = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"));
            final var receipt =
                    new StagingPublicationResult(
                            UUID.fromString(rows.getString("execution_id")),
                            requiredCount(rows, "candidate_rows"),
                            requiredCount(rows, "inserted_rows"),
                            requiredCount(rows, "updated_rows"),
                            requiredCount(rows, "reactivated_rows"),
                            requiredCount(rows, "noop_rows"),
                            requiredCount(rows, "stale_noop_rows"),
                            rows.getTimestamp("reconciled_at_utc", utc).toInstant(),
                            rows.getTimestamp("published_at_utc", utc).toInstant(),
                            instant(rows.getTimestamp("incremental_frontier_before_utc", utc)),
                            instant(rows.getTimestamp("incremental_frontier_after_utc", utc)));
            if (!receipt.executionId().equals(start.executionId())
                    || (start.partition().mode() == ExecutionMode.INCREMENTAL)
                            != receipt.incrementalFrontierAfter().isPresent()) {
                throw new IllegalStateException("RUNTIME_RECOVERY_RECEIPT_MISMATCH");
            }
            publication = Optional.of(receipt);
        } else {
            publication = Optional.empty();
        }
        return new RuntimeRecoverySnapshot(
                reason,
                Optional.ofNullable(state).map(ExecutionState::valueOf),
                rows.getBoolean("lease_valid"),
                rows.getBoolean("contract_verified"),
                requiredCount(rows, "candidate_rows"),
                RuntimeRecoverySnapshot.Quality.valueOf(rows.getString("quality")),
                rows.getString("revision"),
                publication);
    }

    private static Optional<Instant> instant(final Timestamp value) {
        return Optional.ofNullable(value).map(Timestamp::toInstant);
    }

    private static long requiredCount(final ResultSet rows, final String column)
            throws SQLException {
        final long value = rows.getLong(column);
        if (rows.wasNull() || value < 0) {
            throw new IllegalStateException("RUNTIME_RECOVERY_COUNT_INVALID");
        }
        return value;
    }
}
