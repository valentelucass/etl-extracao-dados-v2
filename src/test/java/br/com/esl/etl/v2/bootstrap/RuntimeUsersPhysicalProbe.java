package br.com.esl.etl.v2.bootstrap;

import br.com.esl.etl.v2.plataforma.autorizacao.RuntimeAction;
import br.com.esl.etl.v2.plataforma.autorizacao.RuntimeUsersPhysicalAuthority;
import br.com.esl.etl.v2.plataforma.configuracao.RuntimeConfigurationFactory;
import br.com.esl.etl.v2.plataforma.persistencia.controle.BoundedRuntimeDataSource;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Clock;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;

/** Test-classpath fault controller; positive CLI qualification uses the separate official JAR. */
public final class RuntimeUsersPhysicalProbe {
    private RuntimeUsersPhysicalProbe() {}

    enum Fault {
        NONE,
        PREPARE_ACK_LOST,
        SEAL_ACK_LOST,
        APPLY_ACK_LOST,
        AUDIT_ACK_LOST,
        DQ_ACK_LOST,
        CONSUME_ACK_LOST,
        MUTATE_TENANT,
        MUTATE_ENTITY,
        MUTATE_CONTRACT,
        MUTATE_CONFIGURATION,
        MUTATE_PROTOCOL,
        DQ_MISSING,
        STAGE_FAILURE,
        AUDIT_NULL,
        HALT_BEFORE_PREPARE,
        HALT_AFTER_PREPARE,
        HALT_BEFORE_SEAL,
        HALT_AFTER_SEAL,
        HALT_BEFORE_APPLY,
        HALT_AFTER_APPLY
    }

    public static void main(final String[] args) {
        if (args.length != 4
                || !"ADOPTED_B60_PHYSICAL_PACKAGE".equals(System.getenv("B60_PHYSICAL_PROBE"))) {
            throw new IllegalArgumentException("PHYSICAL_PACKAGE_REQUIRED");
        }
        final var configuration =
                new RuntimeConfigurationFactory()
                        .load(
                                Path.of(args[0]),
                                new Properties(),
                                System.getenv(),
                                Clock.systemUTC());
        final var endpoint = configuration.graphQl().orElseThrow().settings().endpoint();
        if (!"http://127.0.0.1:62160/graphql".equals(endpoint.toString())) {
            throw new IllegalArgumentException("OWN_LOOPBACK_REQUIRED");
        }
        final var fault = Fault.valueOf(args[3]);
        final var commands = new AtomicInteger();
        final var triggered = new AtomicBoolean();
        final var root =
                new RuntimeCompositionRoot(
                        configuration,
                        () ->
                                RuntimeUsersPhysicalAuthority.create(
                                        connection ->
                                                connection(connection, fault, commands, triggered)),
                        (config, request, action, diagnostic, controls) -> {
                            final var attempts = new RuntimeHttpAttempts(5);
                            try {
                                return RuntimeOperationalExecution.execute(
                                        config,
                                        request,
                                        action,
                                        observe(
                                                new BoundedRuntimeDataSource(
                                                        config.shadowStorage()),
                                                fault,
                                                commands,
                                                triggered),
                                        RuntimeOperationalExecution.observedSourceGateways(
                                                attempts),
                                        diagnostic,
                                        controls);
                            } finally {
                                diagnostic.accept(
                                        RuntimeOperationalExecution.sourceAttemptsSummary(
                                                request, attempts));
                                diagnostic.accept(
                                        "B60_SQL_SUBMISSIONS workload="
                                                + commands.get()
                                                + " fault_triggered="
                                                + triggered.get());
                            }
                        });
        final var result =
                root.executeOperationalRequest(
                        Path.of(args[1]), RuntimeAction.valueOf(args[2]), System.out::println);
        System.exit(result.code());
    }

    static DataSource observe(
            final DataSource delegate,
            final Fault fault,
            final AtomicInteger commands,
            final AtomicBoolean triggered) {
        return (DataSource)
                Proxy.newProxyInstance(
                        RuntimeUsersPhysicalProbe.class.getClassLoader(),
                        new Class<?>[] {DataSource.class},
                        (proxy, method, args) -> {
                            final Object value = invoke(delegate, method, args);
                            return value instanceof Connection connection
                                    ? connection(connection, fault, commands, triggered)
                                    : value;
                        });
    }

    private static Connection connection(
            final Connection delegate,
            final Fault fault,
            final AtomicInteger commands,
            final AtomicBoolean triggered) {
        return (Connection)
                Proxy.newProxyInstance(
                        RuntimeUsersPhysicalProbe.class.getClassLoader(),
                        new Class<?>[] {Connection.class},
                        (proxy, method, args) -> {
                            final Object value = invoke(delegate, method, args);
                            if (value instanceof Statement statement) {
                                final String sql =
                                        args != null
                                                        && args.length > 0
                                                        && args[0] instanceof String text
                                                ? text
                                                : "";
                                return statement(statement, sql, fault, commands, triggered);
                            }
                            return value;
                        });
    }

    private static Statement statement(
            final Statement delegate,
            final String sql,
            final Fault fault,
            final AtomicInteger commands,
            final AtomicBoolean triggered) {
        final Class<?> type =
                delegate instanceof java.sql.CallableStatement
                        ? java.sql.CallableStatement.class
                        : delegate instanceof java.sql.PreparedStatement
                                ? java.sql.PreparedStatement.class
                                : Statement.class;
        final String[] operation = {""};
        return (Statement)
                Proxy.newProxyInstance(
                        RuntimeUsersPhysicalProbe.class.getClassLoader(),
                        new Class<?>[] {type},
                        (proxy, method, args) -> {
                            if (method.getName().equals("setString")
                                    && args != null
                                    && Integer.valueOf(
                                                    sql.contains("usp_runtime_authorization")
                                                            ? 1
                                                            : 2)
                                            .equals(args[0])) {
                                operation[0] = (String) args[1];
                            }
                            if (method.getName().equals("setString") && args != null) {
                                final int parameter = (Integer) args[0];
                                final boolean start =
                                        sql.contains("usp_control_plane_start_execution");
                                if (start
                                                && (fault == Fault.MUTATE_TENANT && parameter == 5
                                                        || fault == Fault.MUTATE_ENTITY
                                                                && parameter == 6
                                                        || fault == Fault.MUTATE_CONTRACT
                                                                && parameter == 11
                                                        || fault == Fault.MUTATE_CONFIGURATION
                                                                && parameter == 13)
                                        || fault == Fault.MUTATE_PROTOCOL
                                                && parameter == 2
                                                && sql.contains("usp_control_plane_register_source")
                                        || fault == Fault.DQ_MISSING
                                                && parameter == 2
                                                && sql.contains(
                                                        "usp_evaluate_execution_data_quality")) {
                                    args[1] =
                                            fault == Fault.MUTATE_PROTOCOL
                                                    ? "DATA_EXPORT"
                                                    : "B60_INCOMPATIBLE";
                                    triggered.set(true);
                                }
                                if (fault == Fault.AUDIT_NULL
                                        && parameter == 10
                                        && sql.contains("usp_control_plane_record_page")) {
                                    args[1] = null;
                                    triggered.set(true);
                                }
                            }
                            final boolean executes = method.getName().startsWith("execute");
                            final String phase = phase(sql, operation[0]);
                            if (executes) {
                                if (commands.incrementAndGet() > 128) {
                                    throw new SQLException("PHYSICAL_SQL_SUBMISSION_LIMIT");
                                }
                                if (fault == Fault.STAGE_FAILURE
                                        && sql.contains("usp_stage_usuario_record")
                                        && triggered.compareAndSet(false, true)) {
                                    throw new SQLException("B60_BEFORE_STAGE_SUBMISSION");
                                }
                                if (fault.name().equals("HALT_BEFORE_" + phase)
                                        && triggered.compareAndSet(false, true)) {
                                    halt();
                                }
                            }
                            final Object value = invoke(delegate, method, args);
                            if (executes
                                    && fault.name().equals("HALT_AFTER_" + phase)
                                    && triggered.compareAndSet(false, true)) {
                                halt();
                            }
                            if ((executes && !phase.equals("CONSUME")
                                            || method.getName().equals("getMoreResults")
                                                    && phase.equals("CONSUME"))
                                    && fault.name().equals(phase + "_ACK_LOST")
                                    && triggered.compareAndSet(false, true)) {
                                // Discard the client result; the controller must read SQL
                                // independently before retry.
                                throw new SQLException("B60_CLIENT_CONFIRMATION_DISCARDED");
                            }
                            return value;
                        });
    }

    private static String phase(final String sql, final String operation) {
        if (sql.contains("usp_runtime_authorization") && "CONSUME".equals(operation)) {
            return "CONSUME";
        }
        if (sql.contains("usp_prepare_staged_execution")) {
            return "PREPARE";
        }
        if (sql.contains("usp_runtime_recovery") && "SEAL".equals(operation)) {
            return "SEAL";
        }
        if (sql.contains("usp_apply_reconcile_publish_usuarios")) {
            return "APPLY";
        }
        if (sql.contains("usp_control_plane_record_page")) {
            return "AUDIT";
        }
        if (sql.contains("usp_evaluate_execution_data_quality")) {
            return "DQ";
        }
        return "UNSELECTED";
    }

    private static void halt() {
        System.out.println("B60_OWN_JVM_HALTED_AT_JDBC_BOUNDARY");
        System.out.flush();
        Runtime.getRuntime().halt(86);
    }

    private static Object invoke(final Object target, final Method method, final Object[] args)
            throws Throwable {
        try {
            return method.invoke(target, args);
        } catch (final InvocationTargetException failure) {
            throw failure.getCause();
        }
    }
}
