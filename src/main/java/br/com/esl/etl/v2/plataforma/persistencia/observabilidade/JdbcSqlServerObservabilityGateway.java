package br.com.esl.etl.v2.plataforma.persistencia.observabilidade;

import br.com.esl.etl.v2.plataforma.observabilidade.ExecutionMetricSink;
import br.com.esl.etl.v2.plataforma.observabilidade.ExecutionMetricsSnapshot;
import br.com.esl.etl.v2.plataforma.observabilidade.ObservabilityPersistenceException;
import br.com.esl.etl.v2.plataforma.observabilidade.ObservabilityPersistenceFailureKind;
import br.com.esl.etl.v2.plataforma.observabilidade.OperationalAlert;
import br.com.esl.etl.v2.plataforma.observabilidade.OperationalAlertSink;
import br.com.esl.etl.v2.plataforma.observabilidade.PlatformHealthProbe;
import br.com.esl.etl.v2.plataforma.observabilidade.PlatformHealthSnapshot;
import br.com.esl.etl.v2.plataforma.observabilidade.PlatformHealthStatus;
import br.com.esl.etl.v2.plataforma.qualidade.DataQualityEvaluationException;
import br.com.esl.etl.v2.plataforma.qualidade.DataQualityEvaluationRequest;
import br.com.esl.etl.v2.plataforma.qualidade.DataQualityFailureKind;
import br.com.esl.etl.v2.plataforma.qualidade.DataQualityGateway;
import br.com.esl.etl.v2.plataforma.qualidade.DataQualityPolicyReference;
import br.com.esl.etl.v2.plataforma.qualidade.DataQualityRunSummary;
import br.com.esl.etl.v2.plataforma.qualidade.DataQualityState;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLTimeoutException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Calendar;
import java.util.Objects;
import java.util.TimeZone;
import java.util.UUID;
import javax.sql.DataSource;

/**
 * Adapter único para entrypoints agregados. Nenhum método devolve coleção ou materializa chaves.
 */
public final class JdbcSqlServerObservabilityGateway
        implements DataQualityGateway,
                ExecutionMetricSink,
                OperationalAlertSink,
                PlatformHealthProbe {

    private static final String EVALUATE_DATA_QUALITY =
            "{call recon.usp_evaluate_execution_data_quality(?, ?, ?)}";
    private static final String RECORD_METRIC =
            "{call recon.usp_record_execution_metric(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)}";
    private static final String RAISE_ALERT =
            "{call recon.usp_raise_observability_alert(?, ?, ?, ?, ?, ?, ?)}";
    private static final String OBSERVE_HEALTH = "{call ctl.usp_observe_platform_health(?)}";
    private final DataSource dataSource;
    private final Clock clock;
    private final int queryTimeoutSeconds;

    public JdbcSqlServerObservabilityGateway(
            final DataSource dataSource, final Clock clock, final Duration queryTimeout) {
        this.dataSource = Objects.requireNonNull(dataSource, "O DataSource é obrigatório.");
        this.clock = Objects.requireNonNull(clock, "O relógio é obrigatório.");
        final Duration requiredTimeout =
                Objects.requireNonNull(queryTimeout, "O timeout SQL é obrigatório.");
        final long timeoutSeconds = requiredTimeout.getSeconds();
        if (timeoutSeconds < 1 || timeoutSeconds > 3600 || requiredTimeout.getNano() != 0) {
            throw new IllegalArgumentException(
                    "O timeout SQL deve ter segundos inteiros entre 1 e 3.600.");
        }
        this.queryTimeoutSeconds = Math.toIntExact(timeoutSeconds);
    }

    @Override
    public DataQualityRunSummary evaluatePlatformIntegrity(
            final DataQualityEvaluationRequest request) {
        final DataQualityEvaluationRequest required =
                Objects.requireNonNull(request, "O pedido de Data Quality é obrigatório.");
        try (Connection connection = dataSource.getConnection();
                CallableStatement statement = connection.prepareCall(EVALUATE_DATA_QUALITY)) {
            statement.setQueryTimeout(queryTimeoutSeconds);
            statement.setFetchSize(2);
            statement.setString(1, required.executionId().toString());
            statement.setString(2, required.policyReference().version());
            statement.setString(3, required.policyReference().sha256());
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new InvalidDataQualitySummaryException();
                }
                final DataQualityRunSummary summary = readDataQualitySummary(resultSet);
                if (resultSet.next()) {
                    throw new InvalidDataQualitySummaryException();
                }
                return summary;
            }
        } catch (final SQLException exception) {
            throw classifyDataQualitySqlFailure(exception);
        } catch (final IllegalArgumentException | InvalidDataQualitySummaryException exception) {
            throw new DataQualityEvaluationException(
                    "SUMMARY_DIVERGENT", DataQualityFailureKind.DETERMINISTIC, exception);
        }
    }

    @Override
    public void record(final ExecutionMetricsSnapshot snapshot) {
        final ExecutionMetricsSnapshot required =
                Objects.requireNonNull(snapshot, "O snapshot de métricas é obrigatório.");
        try (Connection connection = dataSource.getConnection();
                CallableStatement statement = connection.prepareCall(RECORD_METRIC)) {
            statement.setQueryTimeout(queryTimeoutSeconds);
            statement.setString(1, required.executionId().toString());
            statement.setInt(2, required.sequence());
            statement.setLong(3, required.durationMilliseconds());
            statement.setLong(4, required.pages());
            statement.setLong(5, required.responseBytes());
            statement.setLong(6, required.physicalRows());
            statement.setLong(7, required.distinctRootKeys());
            statement.setLong(8, required.duplicateRows());
            statement.setLong(9, required.validRows());
            statement.setLong(10, required.quarantinedRootKeys());
            statement.setLong(11, required.unidentifiedQuarantineRows());
            statement.setLong(12, required.quarantinedStageRows());
            statement.setLong(13, required.candidateRows());
            statement.setLong(14, required.insertedRows());
            statement.setLong(15, required.updatedRows());
            statement.setLong(16, required.reactivatedRows());
            statement.setLong(17, required.noopRows());
            statement.setLong(18, required.staleNoopRows());
            statement.setLong(19, required.retryAttempts());
            statement.setLong(20, required.rateLimitResponses());
            statement.setLong(21, required.sourceLagMilliseconds());
            if (required.watermark().isPresent()) {
                bindInstant(statement, 22, required.watermark().orElseThrow());
            } else {
                statement.setNull(22, Types.TIMESTAMP);
            }
            bindInstant(statement, 23, required.capturedAt());
            statement.executeUpdate();
        } catch (final SQLException exception) {
            throw classifyPersistenceFailure("METRIC_RECORD_FAILED", exception);
        }
    }

    @Override
    public void raise(final OperationalAlert alert) {
        raiseAt(null, alert);
    }

    public void raiseScoped(final UUID execution, final OperationalAlert alert) {
        Objects.requireNonNull(execution);
        Objects.requireNonNull(alert);
        if (!br.com.esl.etl.v2.plataforma.observabilidade.CorrelationReference.fromExecutionId(
                        execution)
                .equals(alert.correlationReference())) {
            throw new IllegalArgumentException("ALERT_EXECUTION_CORRELATION_MISMATCH");
        }
        raiseAt(execution, alert);
    }

    private void raiseAt(final UUID execution, final OperationalAlert alert) {
        final OperationalAlert required =
                Objects.requireNonNull(alert, "O alerta operacional é obrigatório.");
        try (Connection connection = dataSource.getConnection();
                CallableStatement statement =
                        connection.prepareCall(
                                execution == null
                                        ? RAISE_ALERT
                                        : "{call recon.usp_runtime_raise_alert(?, ?, ?, ?, ?, ?, ?)}")) {
            statement.setQueryTimeout(queryTimeoutSeconds);
            statement.setString(
                    1,
                    execution == null
                            ? required.correlationReference().sha256()
                            : execution.toString());
            statement.setInt(2, required.sequence());
            statement.setString(3, required.severity().name());
            statement.setString(4, required.alertCode());
            statement.setString(5, required.ownerRole());
            statement.setLong(6, required.occurrenceCount());
            bindInstant(statement, 7, required.occurredAt());
            statement.executeUpdate();
        } catch (final SQLException exception) {
            throw classifyPersistenceFailure("ALERT_RECORD_FAILED", exception);
        }
    }

    @Override
    public PlatformHealthSnapshot readiness(final Duration maximumRunningAge) {
        final Duration required =
                Objects.requireNonNull(maximumRunningAge, "O limite de execução é obrigatório.");
        final long maximumSeconds = required.getSeconds();
        if (maximumSeconds < 1
                || maximumSeconds > Duration.ofDays(30).toSeconds()
                || required.getNano() != 0) {
            throw new IllegalArgumentException(
                    "O limite de execução deve ter segundos inteiros entre 1s e 30d.");
        }
        try (Connection connection = dataSource.getConnection();
                CallableStatement statement = connection.prepareCall(OBSERVE_HEALTH)) {
            statement.setQueryTimeout(queryTimeoutSeconds);
            statement.setFetchSize(2);
            statement.setLong(1, maximumSeconds);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return down("HEALTH_SUMMARY_ABSENT");
                }
                final PlatformHealthSnapshot snapshot = readHealthSnapshot(resultSet);
                if (resultSet.next()) {
                    return down("HEALTH_SUMMARY_DUPLICATED");
                }
                return snapshot;
            }
        } catch (final SQLException exception) {
            return down("SQL_UNAVAILABLE");
        } catch (final IllegalArgumentException | InvalidDataQualitySummaryException exception) {
            return down("HEALTH_SUMMARY_DIVERGENT");
        }
    }

    private DataQualityRunSummary readDataQualitySummary(final ResultSet resultSet)
            throws SQLException {
        return new DataQualityRunSummary(
                requiredUuid(resultSet, "execution_id"),
                new DataQualityPolicyReference(
                        requiredString(resultSet, "policy_version"),
                        requiredString(resultSet, "policy_fingerprint")),
                requiredString(resultSet, "evaluation_fingerprint"),
                requiredInt(resultSet, "expected_checks"),
                requiredInt(resultSet, "completed_checks"),
                requiredInt(resultSet, "passed_checks"),
                requiredInt(resultSet, "failed_checks"),
                requiredLong(resultSet, "evaluated_rows"),
                requiredLong(resultSet, "failed_rows"),
                DataQualityState.valueOf(requiredString(resultSet, "evaluation_state")),
                requiredInstant(resultSet, "evaluated_at_utc"));
    }

    private PlatformHealthSnapshot readHealthSnapshot(final ResultSet resultSet)
            throws SQLException {
        return new PlatformHealthSnapshot(
                PlatformHealthStatus.valueOf(requiredString(resultSet, "health_status")),
                requiredString(resultSet, "reason_code"),
                requiredLong(resultSet, "incomplete_data_quality_runs"),
                requiredLong(resultSet, "failed_data_quality_runs"),
                requiredLong(resultSet, "overdue_quarantine_rows"),
                requiredLong(resultSet, "stale_running_executions"),
                requiredInstant(resultSet, "observed_at_utc"));
    }

    private PlatformHealthSnapshot down(final String reasonCode) {
        return PlatformHealthSnapshot.down(reasonCode, clock.instant());
    }

    private static UUID requiredUuid(final ResultSet resultSet, final String column)
            throws SQLException {
        try {
            return UUID.fromString(requiredString(resultSet, column));
        } catch (final IllegalArgumentException exception) {
            throw new InvalidDataQualitySummaryException(exception);
        }
    }

    private static String requiredString(final ResultSet resultSet, final String column)
            throws SQLException {
        final String value = resultSet.getString(column);
        if (value == null) {
            throw new InvalidDataQualitySummaryException();
        }
        return value;
    }

    private static int requiredInt(final ResultSet resultSet, final String column)
            throws SQLException {
        final int value = resultSet.getInt(column);
        if (resultSet.wasNull()) {
            throw new InvalidDataQualitySummaryException();
        }
        return value;
    }

    private static long requiredLong(final ResultSet resultSet, final String column)
            throws SQLException {
        final long value = resultSet.getLong(column);
        if (resultSet.wasNull()) {
            throw new InvalidDataQualitySummaryException();
        }
        return value;
    }

    private static Instant requiredInstant(final ResultSet resultSet, final String column)
            throws SQLException {
        final Timestamp value = resultSet.getTimestamp(column, utcCalendar());
        if (value == null) {
            throw new InvalidDataQualitySummaryException();
        }
        return value.toInstant();
    }

    private static void bindInstant(
            final CallableStatement statement, final int parameter, final Instant instant)
            throws SQLException {
        statement.setTimestamp(parameter, Timestamp.from(instant), utcCalendar());
    }

    private static Calendar utcCalendar() {
        return Calendar.getInstance(TimeZone.getTimeZone("UTC"));
    }

    private static DataQualityEvaluationException classifyDataQualitySqlFailure(
            final SQLException exception) {
        final int errorCode = exception.getErrorCode();
        final String sqlState = exception.getSQLState();
        if ("HY008".equals(sqlState)) {
            return new DataQualityEvaluationException(
                    "SQL_CANCELLED", DataQualityFailureKind.CANCELLED, exception);
        }
        if (exception instanceof SQLTimeoutException
                || (sqlState != null && sqlState.startsWith("HYT"))) {
            return new DataQualityEvaluationException(
                    "SQL_TIMEOUT", DataQualityFailureKind.TRANSIENT, exception);
        }
        if (errorCode == 51603
                || errorCode == 1205
                || errorCode == 1222
                || "40001".equals(sqlState)) {
            return new DataQualityEvaluationException(
                    "SQL_TRANSIENT", DataQualityFailureKind.TRANSIENT, exception);
        }
        if (errorCode == 51601
                || errorCode == 51602
                || errorCode == 51604
                || errorCode == 51605
                || errorCode == 51606
                || errorCode == 51607
                || errorCode == 51608
                || errorCode == 51609
                || errorCode == 51616
                || errorCode == 547
                || errorCode == 2601
                || errorCode == 2627
                || (sqlState != null && (sqlState.startsWith("22") || sqlState.startsWith("23")))) {
            return new DataQualityEvaluationException(
                    "QUALITY_CONTRACT_REJECTED", DataQualityFailureKind.DETERMINISTIC, exception);
        }
        return new DataQualityEvaluationException(
                "SQL_UNAVAILABLE", DataQualityFailureKind.UNAVAILABLE, exception);
    }

    private static ObservabilityPersistenceException classifyPersistenceFailure(
            final String operationCode, final SQLException exception) {
        final int errorCode = exception.getErrorCode();
        final String sqlState = exception.getSQLState();
        final ObservabilityPersistenceFailureKind kind;
        if ("HY008".equals(sqlState)) {
            kind = ObservabilityPersistenceFailureKind.CANCELLED;
        } else if (exception instanceof SQLTimeoutException
                || errorCode == 1205
                || errorCode == 1222
                || "40001".equals(sqlState)
                || (sqlState != null && sqlState.startsWith("HYT"))) {
            kind = ObservabilityPersistenceFailureKind.TRANSIENT;
        } else if (errorCode == 51611
                || errorCode == 51612
                || errorCode == 51613
                || errorCode == 51619
                || errorCode == 51621
                || errorCode == 547) {
            kind = ObservabilityPersistenceFailureKind.DETERMINISTIC;
        } else {
            kind = ObservabilityPersistenceFailureKind.UNAVAILABLE;
        }
        return new ObservabilityPersistenceException(operationCode, kind, exception);
    }

    private static final class InvalidDataQualitySummaryException extends RuntimeException {

        private static final long serialVersionUID = 1L;

        private InvalidDataQualitySummaryException() {
            super("O resumo agregado de Data Quality é inválido.");
        }

        private InvalidDataQualitySummaryException(final Throwable cause) {
            super("O resumo agregado de Data Quality é inválido.", cause);
        }
    }
}
