package br.com.esl.etl.v2.plataforma.autorizacao;

import br.com.esl.etl.v2.plataforma.controle.ExecutionMode;
import br.com.esl.etl.v2.plataforma.controle.ImmutableFingerprint;
import br.com.esl.etl.v2.plataforma.orquestracao.RuntimeExecutionRequest;
import br.com.esl.etl.v2.plataforma.orquestracao.RuntimePlanningRequest;
import br.com.esl.etl.v2.plataforma.orquestracao.RuntimeWindowStrategy;
import br.com.esl.etl.v2.plataforma.orquestracao.RuntimeWorkloadDefinition;
import br.com.esl.etl.v2.plataforma.orquestracao.RuntimeWorkloadId;
import br.com.esl.etl.v2.plataforma.orquestracao.RuntimeWorkloadRegistry;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Test classpath only: real Windows identity, real TLS, real SQL issuer and consumer. No fake
 * ALLOW.
 */
public final class Bloco54AuthorityHarness {
    private Bloco54AuthorityHarness() {}

    public static void main(final String[] args) throws Exception {
        if (args.length != 4) {
            throw new IllegalArgumentException("HARNESS_CLOSED_ARGUMENTS");
        }
        final String scenario = args[0];
        final UUID invocation = UUID.fromString(args[1]);
        final var scope =
                scope(
                        invocation,
                        ("DIRECT_EXISTING".equals(scenario) || scenario.startsWith("CONSUME_"))
                                ? UUID.fromString(args[2])
                                : invocation);
        final var authority =
                scenario.startsWith("LOST_")
                        ? new WindowsSqlRuntimeAuthorization(
                                RuntimeAuthorityConfiguration.load(),
                                () -> discardAcknowledgement(scenario))
                        : WindowsSqlRuntimeAuthorization.fromAdministeredArtifact();
        try {
            if ("DIRECT_SQL".equals(scenario)) {
                try (var connection = AdministeredSqlConnection.open();
                        var query =
                                connection.prepareCall(
                                        "{call ctl.usp_control_plane_register_source(?,?,?)}")) {
                    query.setQueryTimeout(10);
                    query.setNString(1, "LOCAL_V2");
                    query.setNString(2, "DATA_EXPORT");
                    query.setTimestamp(3, java.sql.Timestamp.from(Instant.now()));
                    try {
                        query.execute();
                        throw new AssertionError("DIRECT_SQL_WITHOUT_CONSUMPTION_SUCCEEDED");
                    } catch (final java.sql.SQLException expected) {
                        if (expected.getErrorCode() != 52500) {
                            throw expected;
                        }
                    }
                }
                System.out.println("DIRECT_SQL_FENCE_PASS");
                return;
            }
            final var capability = authority.authorize(scope);
            if ("AUTHORIZE_ONLY".equals(scenario)) {
                System.out.println("REAL_AUTHORIZE_ONLY_COMMIT_CONFIRMED_DISPATCH_ZERO");
                return;
            }
            if ("WAIT".equals(scenario)) {
                final long deadline = System.nanoTime() + Duration.ofSeconds(20).toNanos();
                while (!Files.exists(Path.of(args[3]))) {
                    if (System.nanoTime() >= deadline) {
                        throw new IllegalStateException("HARNESS_BARRIER_TIMEOUT");
                    }
                    Thread.sleep(25);
                }
            }
            if ("EXPIRE".equals(scenario)) {
                Thread.sleep(31000);
            }
            if ("FIELD".equals(scenario) || "RECEIPT".equals(scenario)) {
                final var material =
                        (com.fasterxml.jackson.databind.node.ObjectNode)
                                new com.fasterxml.jackson.databind.ObjectMapper()
                                        .readTree(scope.material());
                UUID receipt = capability.receipt;
                if ("FIELD".equals(scenario)) {
                    final String field = args[2];
                    if (!material.has(field)) {
                        throw new IllegalArgumentException("UNKNOWN_SCOPE_FIELD");
                    }
                    final String changed =
                            switch (field) {
                                case "invocation", "execution", "cycle", "replayOf" ->
                                        UUID.randomUUID().toString();
                                case "planHash", "contractHash", "configurationHash" ->
                                        "b".repeat(64);
                                case "start" -> "2024-01-01T00:00:01Z";
                                case "endExclusive" -> "2024-01-02T00:00:01Z";
                                case "action" -> "STATUS";
                                case "mode" -> "INCREMENTAL";
                                default -> material.get(field).textValue() + "_changed";
                            };
                    material.put(field, changed);
                } else {
                    receipt = UUID.randomUUID();
                }
                consumeTampered(scope, material.toString(), receipt);
                System.out.println("TAMPERED_SQL_CONSUMPTION_DENIED_DISPATCH_ZERO");
                return;
            }
            authority.consumeAndExecute(
                    capability,
                    scope,
                    () -> {
                        if ("CONSUME_STOP".equals(scenario)) {
                            System.out.println(
                                    "REAL_CONSUME_COMMITTED_HALT_BEFORE_BUSINESS_DISPATCH");
                            System.out.flush();
                            Runtime.getRuntime().halt(0);
                        }
                        System.out.println("REAL_CONSUME_ACK_CONFIRMED_TEST_SUPPLIER_ONLY");
                        return Boolean.TRUE;
                    });
            if ("DIRECT_EXISTING".equals(scenario)) {
                try (var connection = AdministeredSqlConnection.open();
                        var query =
                                connection.prepareCall("{call ctl.usp_runtime_observation(?)}")) {
                    query.setQueryTimeout(10);
                    query.setString(1, scope.executionId().toString());
                    try {
                        query.execute();
                        throw new AssertionError("EXISTING_SQL_SCOPE_MISMATCH_WAS_ACCEPTED");
                    } catch (final java.sql.SQLException expected) {
                        if (expected.getErrorCode() != 52510) {
                            throw expected;
                        }
                    }
                }
                System.out.println("EXISTING_SQL_OCCURRENCE_SCOPE_FENCE_PASS");
            }
            if ("REUSE".equals(scenario)) {
                authority.consumeAndExecute(
                        capability,
                        scope,
                        () -> {
                            throw new AssertionError("SECOND_DISPATCH");
                        });
            }
        } catch (final DurableAuthorizationException expected) {
            System.out.println("REAL_AUTHORITY_REJECTED reason=" + expected.reason());
            System.exit(20);
        }
    }

    private static void consumeTampered(
            final RuntimeAuthorizationScope scope, final String material, final UUID receipt)
            throws Exception {
        final var config = RuntimeAuthorityConfiguration.load();
        try (var connection = AdministeredSqlConnection.open();
                var query =
                        connection.prepareCall(
                                "{call ctl.usp_runtime_authorization(?,?,?,?,?,?,?,?)}")) {
            query.setQueryTimeout(10);
            query.setNString(1, "CONSUME");
            query.setString(2, scope.invocationId().toString());
            query.setString(3, config.authority.toString());
            query.setNString(4, "RUN");
            query.setString(5, scope.executionId().toString());
            query.setNString(6, material);
            query.setString(7, config.policy);
            query.setString(8, receipt.toString());
            try {
                query.execute();
                throw new AssertionError("TAMPERED_CONSUMPTION_SUCCEEDED");
            } catch (final java.sql.SQLException expected) {
                if (expected.getErrorCode() < 52411 || expected.getErrorCode() > 52415) {
                    throw expected;
                }
            }
        }
    }

    private static RuntimeAuthorizationScope scope(final UUID invocation, final UUID execution) {
        final var id = new RuntimeWorkloadId("coletas");
        final var fingerprint = new ImmutableFingerprint("bloco54-auth-v1", "a".repeat(64));
        final var definition =
                new RuntimeWorkloadDefinition(
                        id,
                        "DATA_EXPORT",
                        "LOCAL_V2",
                        "LOCAL_V2",
                        "coletas",
                        fingerprint,
                        fingerprint,
                        Duration.ofSeconds(30));
        final Instant start = Instant.parse("2024-01-01T00:00:00Z");
        return RuntimeAuthorizationScope.from(
                invocation,
                RuntimeAction.RUN,
                RuntimeWorkloadRegistry.of(definition)
                        .plan(
                                new RuntimePlanningRequest(
                                        execution,
                                        "LOCAL_SHADOW",
                                        fingerprint,
                                        Instant.now(),
                                        new RuntimeExecutionRequest(
                                                execution,
                                                id,
                                                ExecutionMode.BACKFILL,
                                                RuntimeWindowStrategy.INTERVAL,
                                                start,
                                                start.plusSeconds(86400),
                                                execution.toString()))));
    }

    private static java.sql.Connection discardAcknowledgement(final String scenario)
            throws java.sql.SQLException {
        final var connection = AdministeredSqlConnection.open();
        return (java.sql.Connection)
                java.lang.reflect.Proxy.newProxyInstance(
                        Bloco54AuthorityHarness.class.getClassLoader(),
                        new Class<?>[] {java.sql.Connection.class},
                        (proxy, method, args) -> {
                            final Object result;
                            try {
                                result = method.invoke(connection, args);
                            } catch (final java.lang.reflect.InvocationTargetException failure) {
                                throw failure.getCause();
                            }
                            if (result instanceof java.sql.CallableStatement statement) {
                                final String[] operation = {""};
                                return java.lang.reflect.Proxy.newProxyInstance(
                                        Bloco54AuthorityHarness.class.getClassLoader(),
                                        new Class<?>[] {java.sql.CallableStatement.class},
                                        (statementProxy, call, parameters) -> {
                                            if (call.getName().equals("setString")
                                                    && Integer.valueOf(1).equals(parameters[0])) {
                                                operation[0] = (String) parameters[1];
                                            }
                                            try {
                                                final Object value =
                                                        call.invoke(statement, parameters);
                                                if (call.getName().equals("executeQuery")
                                                        && (scenario.equals("LOST_CONSUME_ACK")
                                                                        && operation[0].equals(
                                                                                "CONSUME")
                                                                || scenario.equals(
                                                                                "LOST_AUTHORIZE_ACK")
                                                                        && operation[0].equals(
                                                                                "AUTHORIZE"))) {
                                                    ((java.sql.ResultSet) value).close();
                                                    throw new java.sql.SQLException(
                                                            "TEST_ACK_DISCARDED_AFTER_SQL_COMMIT");
                                                }
                                                return value;
                                            } catch (
                                                    final java.lang.reflect
                                                                    .InvocationTargetException
                                                            failure) {
                                                throw failure.getCause();
                                            }
                                        });
                            }
                            return result;
                        });
    }
}
