package br.com.esl.etl.v2.plataforma.fonte.dataexport;

import br.com.esl.etl.v2.plataforma.controle.ExecutionState;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;

/** Simulador de protocolo JDBC: não executa SQL nem prova transação/concorrência física. */
final class RuntimeSyntheticJdbc {
    final Map<String, Attempt> attempts = new HashMap<>();
    final List<String> operations = new ArrayList<>();
    final List<Integer> batchSizes = new ArrayList<>();
    final List<RuntimeUsersJdbc.Call> calls = new ArrayList<>();
    String failOperation;
    int failBatch;
    int batches;
    int commits;
    int rollbacks;
    int openConnections;
    int heartbeatCount;
    boolean failQuality;
    boolean omitPublication;
    boolean wrongPublication;
    boolean loseApplyResponse;
    boolean losePrepareResponse;
    boolean loseTransitionResponse;
    boolean loseStartResponse;
    boolean loseLease;
    Runnable afterBatch = () -> {};
    Runnable afterApply = () -> {};
    final Instant now;
    final RuntimeRecoverySyntheticState recovery = new RuntimeRecoverySyntheticState(this);

    RuntimeSyntheticJdbc(final Instant now) {
        this.now = now;
    }

    DataSource dataSource() {
        return proxy(
                DataSource.class,
                (object, method, args) -> {
                    if (method.getName().equals("getConnection")) {
                        return connection();
                    }
                    if (method.getName().equals("getLoginTimeout")) {
                        return recovery.loginTimeout;
                    }
                    return defaultValue(method.getReturnType());
                });
    }

    private Connection connection() {
        openConnections++;
        final List<Runnable> pending = new ArrayList<>();
        return proxy(
                Connection.class,
                (object, method, args) -> {
                    switch (method.getName()) {
                        case "setNetworkTimeout":
                            if (recovery.networkUnsupported) {
                                throw new java.sql.SQLFeatureNotSupportedException(
                                        "network timeout unavailable");
                            }
                            recovery.networkTimeout = (Integer) args[1];
                            return null;
                        case "prepareCall":
                            return statement((String) args[0], pending);
                        case "commit":
                            pending.forEach(Runnable::run);
                            pending.clear();
                            commits++;
                            return null;
                        case "rollback":
                            pending.clear();
                            rollbacks++;
                            return null;
                        case "close":
                            openConnections--;
                            return null;
                        default:
                            return defaultValue(method.getReturnType());
                    }
                });
    }

    private CallableStatement statement(final String sql, final List<Runnable> pending) {
        final String operation = sql.substring(6, sql.indexOf('('));
        final Map<Integer, Object> parameters = new HashMap<>();
        final List<Map<Integer, Object>> batch = new ArrayList<>();
        return proxy(
                CallableStatement.class,
                (object, method, args) -> {
                    final String name = method.getName();
                    if (name.equals("setQueryTimeout")) {
                        recovery.queryTimeout = (Integer) args[0];
                        return null;
                    }
                    if (name.equals("close")) {
                        recovery.statementCloses++;
                        return null;
                    }
                    if (name.equals("getUpdateCount")) {
                        return -1;
                    }
                    if (name.equals("cancel")) {
                        recovery.cancelCalls++;
                        return null;
                    }
                    if (name.startsWith("set") && args.length >= 2 && args[0] instanceof Integer) {
                        parameters.put((Integer) args[0], name.equals("setNull") ? null : args[1]);
                        return null;
                    }
                    if (name.equals("addBatch")) {
                        batch.add(new HashMap<>(parameters));
                        return null;
                    }
                    if (name.equals("executeBatch")) {
                        operations.add(operation);
                        batch.forEach(
                                row ->
                                        calls.add(
                                                new RuntimeUsersJdbc.Call(
                                                        operation, new HashMap<>(row))));
                        batches++;
                        if (batches == failBatch) {
                            throw new SQLException("synthetic batch failure");
                        }
                        batchSizes.add(batch.size());
                        for (final Map<Integer, Object> row : batch) {
                            final Attempt attempt = require(row.get(1).toString());
                            if (attempt.state != ExecutionState.EXTRACTING) {
                                throw new SQLException("stage state");
                            }
                            pending.add(() -> attempt.rows++);
                        }
                        afterBatch.run();
                        return new int[batch.size()];
                    }
                    if (name.equals("executeUpdate")
                            || name.equals("executeQuery")
                            || name.equals("execute")) {
                        operations.add(operation);
                        calls.add(new RuntimeUsersJdbc.Call(operation, new HashMap<>(parameters)));
                        if (operation.equals(failOperation)) {
                            throw new SQLException("synthetic unavailable");
                        }
                        final Map<String, Object> result = execute(operation, parameters);
                        if (name.equals("execute")) {
                            return false;
                        }
                        return name.equals("executeQuery") ? resultSet(result) : 1;
                    }
                    return defaultValue(method.getReturnType());
                });
    }

    Map<String, Object> execute(final String op, final Map<Integer, Object> p) throws SQLException {
        if (op.equals("ctl.usp_runtime_recovery") || op.equals("ctl.usp_runtime_status")) {
            return recovery.execute(p);
        }
        if (op.equals("ctl.usp_control_plane_recover_stale_executions")) {
            return Map.of("recovered_executions", 0L);
        }
        if (op.equals("ctl.usp_control_plane_start_cycle")) {
            recovery.cycles.put(
                    p.get(1).toString(),
                    new br.com.esl.etl.v2.plataforma.controle.ImmutableFingerprint(
                            p.get(2).toString(), p.get(3).toString()));
            return Map.of();
        }
        if (op.equals("ctl.usp_control_plane_register_source")) {
            return Map.of();
        }
        final String id = p.get(1).toString();
        if (op.equals("ctl.usp_control_plane_start_execution")) {
            if (!attempts.containsKey(id)) {
                attempts.put(id, new Attempt(p));
            }
            if (loseStartResponse) {
                loseStartResponse = false;
                throw new SQLException("lost start response");
            }
            return Map.of();
        }
        final Attempt attempt = require(id);
        if (op.equals("ctl.usp_control_plane_heartbeat_lease")) {
            heartbeatCount++;
            if (attempt.state.isTerminal()) {
                throw new SQLException("terminal execution has no lease", "S0001", 51330);
            }
            if (loseLease) {
                throw new SQLException("synthetic lease lost", "S0001", 51330);
            }
            return Map.of();
        }
        if (op.equals("ctl.usp_control_plane_record_page")) {
            if (attempt.state != ExecutionState.EXTRACTING) {
                throw new SQLException("page state");
            }
            attempt.pages++;
            attempt.auditedRows += ((Number) p.get(5)).longValue();
            attempt.terminal =
                    attempt.start.get(6).equals("usuarios")
                            ? "GRAPHQL_PAGE_INFO".equals(p.get(10))
                                    && Boolean.FALSE.equals(p.get(8))
                            : (Boolean) p.get(8);
            return Map.of();
        }
        if (op.equals("ctl.usp_control_plane_transition_execution")) {
            if (loseLease) {
                throw new SQLException("transition without active lease", "S0001", 51330);
            }
            final ExecutionState expected = ExecutionState.valueOf((String) p.get(2));
            final ExecutionState next = ExecutionState.valueOf((String) p.get(3));
            if (attempt.state != expected || !expected.canTransitionTo(next)) {
                throw new SQLException("transition mismatch");
            }
            attempt.state = next;
            if (loseTransitionResponse) {
                loseTransitionResponse = false;
                throw new SQLException("lost transition response");
            }
            return Map.of();
        }
        if (op.startsWith("core.usp_prepare_")) {
            verifyPermit(attempt, p);
            if (attempt.state != ExecutionState.STAGED
                    && attempt.state != ExecutionState.PROMOTED) {
                throw new SQLException("prepare state");
            }
            if (!attempt.terminal || attempt.rows != attempt.auditedRows) {
                throw new SQLException("incomplete staging");
            }
            attempt.state = ExecutionState.PROMOTED;
            attempt.preparedRows = attempt.rows;
            if (losePrepareResponse) {
                losePrepareResponse = false;
                throw new SQLException("lost prepare response");
            }
            return Map.of();
        }
        if (op.equals("recon.usp_evaluate_execution_data_quality")) {
            if (attempt.state != ExecutionState.PROMOTED) {
                throw new SQLException("quality state");
            }
            final Map<String, Object> result = new HashMap<>();
            result.put("execution_id", id);
            result.put("policy_version", p.get(2));
            result.put("policy_fingerprint", p.get(3));
            result.put("evaluation_fingerprint", "e".repeat(64));
            result.put("expected_checks", 4);
            result.put("completed_checks", 4);
            result.put("passed_checks", failQuality ? 3 : 4);
            result.put("failed_checks", failQuality ? 1 : 0);
            result.put("evaluated_rows", attempt.rows);
            result.put("failed_rows", failQuality ? 1L : 0L);
            result.put("evaluation_state", failQuality ? "FAILED" : "PASSED");
            result.put("evaluated_at_utc", Timestamp.from(now));
            attempt.qualityPassed = !failQuality;
            attempt.quality = result;
            return result;
        }
        if (op.startsWith("core.usp_apply_reconcile_publish_")) {
            verifyPermit(attempt, p);
            if (!attempt.qualityPassed) {
                throw new SQLException("quality required");
            }
            if (attempt.state != ExecutionState.PROMOTED
                    && attempt.state != ExecutionState.PUBLISHED) {
                throw new SQLException("apply state");
            }
            if (attempt.publication == null) {
                attempt.publication = publication(id, attempt);
                attempt.genericPublication = new HashMap<>(attempt.publication);
                if ((attempt.start.get(6).equals("coletas") && recovery.coletaTypedNoop)
                        || (attempt.start.get(6).equals("usuarios") && recovery.usersTypedNoop)) {
                    attempt.publication.put("inserted_rows", 0L);
                    attempt.publication.put("noop_rows", attempt.rows);
                }
                attempt.typedReceiptHash =
                        RuntimeRecoverySyntheticState.receiptHash(attempt.publication);
                attempt.applies++;
            }
            attempt.state = ExecutionState.PUBLISHED;
            afterApply.run();
            if (loseApplyResponse) {
                loseApplyResponse = false;
                throw new SQLException("lost commit acknowledgement");
            }
            if (omitPublication) {
                return null;
            }
            if (wrongPublication) {
                final Map<String, Object> wrong = new HashMap<>(attempt.publication);
                wrong.put("execution_id", "00000000-0000-0000-0000-000000000099");
                return wrong;
            }
            return attempt.publication;
        }
        throw new SQLException("unmodeled procedure");
    }

    private Map<String, Object> publication(final String id, final Attempt attempt) {
        final Map<String, Object> result = new HashMap<>();
        result.put("execution_id", id);
        result.put("candidate_rows", attempt.rows);
        result.put("inserted_rows", attempt.rows);
        result.put("updated_rows", 0L);
        result.put("reactivated_rows", 0L);
        result.put("noop_rows", 0L);
        result.put("stale_noop_rows", 0L);
        result.put("reconciled_at_utc", Timestamp.from(now));
        result.put("published_at_utc", Timestamp.from(now));
        result.put(
                "incremental_frontier_before_utc",
                attempt.mode.equals("INCREMENTAL") ? attempt.start.get(8) : null);
        result.put(
                "incremental_frontier_after_utc",
                attempt.mode.equals("INCREMENTAL") ? attempt.start.get(9) : null);
        return result;
    }

    private static void verifyPermit(final Attempt attempt, final Map<Integer, Object> p)
            throws SQLException {
        for (int index = 2; index <= 5; index++) {
            if (!p.get(index).equals(attempt.start.get(index + 9))) {
                throw new SQLException("permit mismatch");
            }
        }
    }

    private Attempt require(final String id) throws SQLException {
        final Attempt result = attempts.get(id);
        if (result == null) {
            throw new SQLException("execution missing");
        }
        return result;
    }

    private ResultSet resultSet(final Map<String, Object> row) {
        final int[] position = {0};
        final boolean[] wasNull = {false};
        return proxy(
                ResultSet.class,
                (object, method, args) -> {
                    if (method.getName().equals("next")) {
                        return ++position[0] == 1 && row != null;
                    }
                    if (method.getName().equals("wasNull")) {
                        return wasNull[0];
                    }
                    if (method.getName().startsWith("get")) {
                        if (method.getName().equals("getTimestamp")) {
                            recovery.timestampZone =
                                    args.length == 2
                                            ? ((java.util.Calendar) args[1]).getTimeZone().getID()
                                            : "IMPLICIT";
                        }
                        final Object value = row.get(args[0]);
                        wasNull[0] = value == null;
                        return switch (method.getName()) {
                            case "getLong" -> value == null ? 0L : ((Number) value).longValue();
                            case "getInt" -> value == null ? 0 : ((Number) value).intValue();
                            default -> value;
                        };
                    }
                    return defaultValue(method.getReturnType());
                });
    }

    private static <T> T proxy(final Class<T> type, final InvocationHandler handler) {
        return type.cast(
                Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, handler));
    }

    private static Object defaultValue(final Class<?> type) {
        if (type == boolean.class) {
            return false;
        }
        if (type == int.class) {
            return 0;
        }
        if (type == long.class) {
            return 0L;
        }
        return null;
    }

    static final class Attempt {
        final Map<Integer, Object> start;
        final String mode;
        ExecutionState state = ExecutionState.EXTRACTING;
        long rows;
        long auditedRows;
        int pages;
        boolean terminal;
        boolean qualityPassed;
        int applies;
        Map<String, Object> publication;
        Map<String, Object> genericPublication;
        String typedReceiptHash;
        Map<String, Object> quality;
        Map<Integer, Object> seal;
        String sealHash;
        long preparedRows;

        Attempt(final Map<Integer, Object> start) {
            this.start = new HashMap<>(start);
            mode = (String) start.get(7);
        }
    }
}
