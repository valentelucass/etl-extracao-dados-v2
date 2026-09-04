package br.com.esl.etl.v2.plataforma.controle;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Calendar;
import java.util.Objects;
import java.util.TimeZone;
import java.util.UUID;
import javax.sql.DataSource;

/** Adaptador SQL Server do control plane, limitado a procedures parametrizadas de {@code ctl}. */
public final class JdbcSqlServerControlPlane implements ControlPlane {

    private static final String START_EXECUTION =
            "{call ctl.usp_control_plane_start_execution(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)}";
    private static final String REGISTER_SOURCE =
            "{call ctl.usp_control_plane_register_source(?, ?, ?)}";
    private static final String START_CYCLE =
            "{call ctl.usp_control_plane_start_cycle(?, ?, ?, ?)}";
    private static final String HEARTBEAT =
            "{call ctl.usp_control_plane_heartbeat_lease(?, ?, ?, ?)}";
    private static final String RECORD_PAGE =
            "{call ctl.usp_control_plane_record_page(?, ?, ?, ?, ?, ?, ?, ?, ?, ?)}";
    private static final String RECORD_COUNTS =
            "{call ctl.usp_control_plane_record_counts(?, ?, ?, ?, ?, ?, ?, ?, ?)}";
    private static final String TRANSITION =
            "{call ctl.usp_control_plane_transition_execution(?, ?, ?, ?, ?)}";
    private static final String REGISTER_FRONTIER =
            "{call ctl.usp_control_plane_register_incremental_frontier(?, ?, ?, ?, ?, ?)}";
    private static final String RECOVER_STALE =
            "{call ctl.usp_control_plane_recover_stale_executions()}";

    private final DataSource dataSource;

    public JdbcSqlServerControlPlane(final DataSource dataSource) {
        this.dataSource =
                Objects.requireNonNull(dataSource, "O DataSource do control plane é obrigatório.");
    }

    @Override
    public void registerSource(final ControlPlaneSource source) {
        Objects.requireNonNull(source, "A fonte é obrigatória.");
        invoke(
                "o registro da fonte",
                REGISTER_SOURCE,
                statement -> {
                    statement.setString(1, source.sourceInstance());
                    statement.setString(2, source.sourceKind());
                    bindInstant(statement, 3, source.registeredAt());
                });
    }

    @Override
    public void startCycle(final ControlPlaneCycle cycle) {
        Objects.requireNonNull(cycle, "O ciclo é obrigatório.");
        invoke(
                "o planejamento do ciclo",
                START_CYCLE,
                statement -> {
                    statement.setString(1, cycle.cycleId().toString());
                    statement.setString(2, cycle.plan().version());
                    statement.setString(3, cycle.plan().sha256());
                    bindInstant(statement, 4, cycle.plannedAt());
                });
    }

    @Override
    public void startExecution(final ControlPlaneStart start) {
        Objects.requireNonNull(start, "O pedido de início é obrigatório.");
        invoke(
                "o início da execução",
                START_EXECUTION,
                statement -> {
                    int parameter = 1;
                    statement.setString(parameter++, start.executionId().toString());
                    statement.setString(parameter++, start.cycleId().toString());
                    parameter = bindPartition(statement, parameter, start.partition());
                    statement.setString(parameter++, start.windowStrategy());
                    statement.setString(parameter++, start.contract().version());
                    statement.setString(parameter++, start.contract().sha256());
                    statement.setString(parameter++, start.configuration().version());
                    statement.setString(parameter++, start.configuration().sha256());
                    statement.setString(parameter++, start.idempotencyKey());
                    if (start.replayOfExecutionId().isPresent()) {
                        statement.setString(
                                parameter++, start.replayOfExecutionId().orElseThrow().toString());
                    } else {
                        statement.setNull(parameter++, java.sql.Types.VARCHAR);
                    }
                    statement.setInt(
                            parameter++, Math.toIntExact(start.leaseDuration().toSeconds()));
                    bindInstant(statement, parameter, start.startedAt());
                });
    }

    @Override
    public void heartbeat(
            final UUID executionId, final Instant heartbeatAt, final Duration leaseExtension) {
        Objects.requireNonNull(executionId, "O execution_id é obrigatório.");
        Objects.requireNonNull(heartbeatAt, "O horário de heartbeat é obrigatório.");
        validateLeaseExtension(leaseExtension);
        invoke(
                "o heartbeat da lease",
                HEARTBEAT,
                statement -> {
                    statement.setString(1, executionId.toString());
                    bindInstant(statement, 2, heartbeatAt);
                    statement.setInt(3, Math.toIntExact(leaseExtension.toSeconds()));
                    bindInstant(statement, 4, heartbeatAt.plus(leaseExtension));
                });
    }

    @Override
    public void recordPage(final ControlPlanePage page) {
        Objects.requireNonNull(page, "A auditoria de página é obrigatória.");
        invoke(
                "a página da execução",
                RECORD_PAGE,
                statement -> {
                    statement.setString(1, page.executionId().toString());
                    statement.setInt(2, page.pageNumber());
                    statement.setInt(3, page.pageAttempt());
                    statement.setInt(4, page.requestedPageSize());
                    statement.setLong(5, page.physicalRows());
                    statement.setLong(6, page.distinctRootKeys());
                    statement.setLong(7, page.responseBytes());
                    statement.setBoolean(
                            8,
                            page.terminality()
                                    == ControlPlanePageTerminality.DATA_EXPORT_EMPTY_PAGE);
                    bindInstant(statement, 9, page.readAt());
                    statement.setString(10, page.terminality().name());
                });
    }

    @Override
    public void recordCounts(final ControlPlaneCounts counts) {
        Objects.requireNonNull(counts, "As contagens são obrigatórias.");
        invoke(
                "as contagens da execução",
                RECORD_COUNTS,
                statement -> {
                    statement.setString(1, counts.executionId().toString());
                    statement.setString(2, counts.phase());
                    statement.setLong(3, counts.physicalRows());
                    statement.setLong(4, counts.distinctRootKeys());
                    statement.setLong(5, counts.duplicateRows());
                    statement.setLong(6, counts.validRows());
                    statement.setLong(7, counts.quarantinedRootKeys());
                    bindInstant(statement, 8, counts.recordedAt());
                    statement.setLong(9, counts.unidentifiedQuarantineRows());
                });
    }

    @Override
    public void transition(final ControlPlaneTransition transition) {
        Objects.requireNonNull(transition, "A transição é obrigatória.");
        invoke(
                "a transição da execução",
                TRANSITION,
                statement -> {
                    statement.setString(1, transition.executionId().toString());
                    statement.setString(2, transition.expectedCurrentState().name());
                    statement.setString(3, transition.nextState().name());
                    statement.setString(4, transition.reasonCode());
                    bindInstant(statement, 5, transition.transitionedAt());
                });
    }

    @Override
    public void registerIncrementalFrontier(
            final ExecutionPartitionKey incrementalPartition,
            final Instant initialContiguousEnd,
            final Instant registeredAt) {
        Objects.requireNonNull(incrementalPartition, "A partição incremental é obrigatória.");
        if (incrementalPartition.mode() != ExecutionMode.INCREMENTAL) {
            throw new IllegalArgumentException(
                    "A fronteira operacional só existe para modo incremental.");
        }
        Objects.requireNonNull(initialContiguousEnd, "A fronteira inicial é obrigatória.");
        Objects.requireNonNull(registeredAt, "O horário de registro é obrigatório.");
        invoke(
                "a fronteira incremental",
                REGISTER_FRONTIER,
                statement -> {
                    statement.setString(1, incrementalPartition.environment());
                    statement.setString(2, incrementalPartition.sourceInstance());
                    statement.setString(3, incrementalPartition.tenantScope());
                    statement.setString(4, incrementalPartition.entity());
                    bindInstant(statement, 5, initialContiguousEnd);
                    bindInstant(statement, 6, registeredAt);
                });
    }

    @Override
    public ControlPlaneRecoveryResult recoverStaleExecutions() {
        try (Connection connection = dataSource.getConnection();
                CallableStatement statement = connection.prepareCall(RECOVER_STALE);
                ResultSet resultSet = statement.executeQuery()) {
            if (!resultSet.next()) {
                throw invalidRecoveryResult();
            }
            final long recoveredExecutions = resultSet.getLong("recovered_executions");
            if (resultSet.wasNull() || recoveredExecutions < 0 || resultSet.next()) {
                throw invalidRecoveryResult();
            }
            return new ControlPlaneRecoveryResult(recoveredExecutions);
        } catch (final SQLException exception) {
            throw new ControlPlanePersistenceException(
                    "a recuperação de leases expiradas", exception);
        }
    }

    private static SQLException invalidRecoveryResult() {
        return new SQLException("O resultado agregado de recovery é inválido.");
    }

    private void invoke(
            final String operation,
            final String statementSql,
            final CallableStatementBinder binder) {
        try (Connection connection = dataSource.getConnection();
                CallableStatement statement = connection.prepareCall(statementSql)) {
            binder.bind(statement);
            statement.executeUpdate();
        } catch (final SQLException exception) {
            throw new ControlPlanePersistenceException(operation, exception);
        }
    }

    private static int bindPartition(
            final CallableStatement statement,
            final int firstParameter,
            final ExecutionPartitionKey key)
            throws SQLException {
        int parameter = firstParameter;
        statement.setString(parameter++, key.environment());
        statement.setString(parameter++, key.sourceInstance());
        statement.setString(parameter++, key.tenantScope());
        statement.setString(parameter++, key.entity());
        statement.setString(parameter++, key.mode().name());
        bindInstant(statement, parameter++, key.partitionStart());
        bindInstant(statement, parameter++, key.partitionEndExclusive());
        return parameter;
    }

    private static void validateLeaseExtension(final Duration extension) {
        Objects.requireNonNull(extension, "A extensão de lease é obrigatória.");
        if (extension.isNegative()
                || extension.isZero()
                || extension.compareTo(Duration.ofHours(24)) > 0
                || extension.toMillis() % 1_000 != 0) {
            throw new IllegalArgumentException(
                    "A extensão de lease deve ter segundos inteiros até 24 horas.");
        }
    }

    private static void bindInstant(
            final CallableStatement statement, final int parameter, final Instant instant)
            throws SQLException {
        statement.setTimestamp(parameter, Timestamp.from(instant), utcCalendar());
    }

    private static Calendar utcCalendar() {
        return Calendar.getInstance(TimeZone.getTimeZone("UTC"));
    }

    @FunctionalInterface
    private interface CallableStatementBinder {

        void bind(CallableStatement statement) throws SQLException;
    }
}
