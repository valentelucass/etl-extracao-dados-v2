package br.com.esl.etl.v2.plataforma.autorizacao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.plataforma.controle.ExecutionMode;
import br.com.esl.etl.v2.plataforma.controle.ImmutableFingerprint;
import br.com.esl.etl.v2.plataforma.orquestracao.RuntimeExecutionRequest;
import br.com.esl.etl.v2.plataforma.orquestracao.RuntimePlanningRequest;
import br.com.esl.etl.v2.plataforma.orquestracao.RuntimeWindowStrategy;
import br.com.esl.etl.v2.plataforma.orquestracao.RuntimeWorkloadDefinition;
import br.com.esl.etl.v2.plataforma.orquestracao.RuntimeWorkloadId;
import br.com.esl.etl.v2.plataforma.orquestracao.RuntimeWorkloadRegistry;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class WindowsSqlRuntimeAuthorizationTest {
    @Test
    void concurrentConsumersHaveOneDurableWinnerAndLostConsumeAckNeverDispatches()
            throws Exception {
        final var scope = scope(RuntimeAction.RUN, "synthetic-tenant");
        final var jdbc = new Jdbc(scope);
        final var authority = new WindowsSqlRuntimeAuthorization(configuration(), jdbc::connection);
        final var capability = authority.authorize(scope);
        final var executions = new java.util.concurrent.atomic.AtomicInteger();
        final var workers = java.util.concurrent.Executors.newFixedThreadPool(2);
        final var barrier = new java.util.concurrent.CyclicBarrier(2);
        try {
            final java.util.concurrent.Callable<Boolean> task =
                    () -> {
                        barrier.await(5, java.util.concurrent.TimeUnit.SECONDS);
                        try {
                            authority.consumeAndExecute(
                                    capability, scope, executions::incrementAndGet);
                            return true;
                        } catch (final DurableAuthorizationException refused) {
                            assertEquals(
                                    DurableAuthorizationException.Reason.ALREADY_CONSUMED,
                                    refused.reason());
                            return false;
                        }
                    };
            final var first = workers.submit(task);
            final var second = workers.submit(task);
            assertTrue(
                    first.get(5, java.util.concurrent.TimeUnit.SECONDS)
                            ^ second.get(5, java.util.concurrent.TimeUnit.SECONDS));
            assertEquals(1, executions.get());
        } finally {
            workers.shutdownNow();
            assertTrue(workers.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS));
        }
        final var uncertain = new Jdbc(scope);
        final var other =
                new WindowsSqlRuntimeAuthorization(configuration(), uncertain::connection);
        final var receipt = other.authorize(scope);
        uncertain.mutation = "ack";
        assertThrows(
                DurableAuthorizationException.class,
                () -> other.consumeAndExecute(receipt, scope, executions::incrementAndGet));
        assertTrue(uncertain.consumed.get());
        assertEquals(1, executions.get());
        uncertain.mutation = "";
        assertEquals(
                DurableAuthorizationException.Reason.ALREADY_CONSUMED,
                assertThrows(
                                DurableAuthorizationException.class,
                                () ->
                                        other.consumeAndExecute(
                                                receipt, scope, executions::incrementAndGet))
                        .reason());
    }

    @Test
    void expiredRevokedOrChangedPolicyAtConsumptionCannotComposeWork() {
        final var scope = scope(RuntimeAction.RUN, "synthetic-tenant");
        for (final String reason :
                new String[] {
                    "expired-between-authorize-and-use", "mapping-revoked", "policy-replaced"
                }) {
            final var jdbc = new Jdbc(scope);
            final var authority =
                    new WindowsSqlRuntimeAuthorization(configuration(), jdbc::connection);
            final var capability = authority.authorize(scope);
            jdbc.fail = new SQLException("SYNTHETIC_" + reason, "", 52414);
            final var failure =
                    assertThrows(
                            DurableAuthorizationException.class,
                            () ->
                                    authority.consumeAndExecute(
                                            capability,
                                            scope,
                                            () -> {
                                                throw new AssertionError("UNAUTHORIZED_WORK");
                                            }));
            assertEquals(DurableAuthorizationException.Reason.DENIED, failure.reason());
            assertTrue(failure.internalCause().isPresent());
            assertFalse(jdbc.consumed.get());
        }
    }

    private static final UUID INVOCATION = UUID.fromString("00000000-0000-0000-0000-000000005301");
    private static final UUID EXECUTION = UUID.fromString("00000000-0000-0000-0000-000000005302");
    private static final Instant NOW = Instant.parse("2026-09-07T19:00:00Z");

    static RuntimeAuthorityConfiguration configuration() {
        final var values = new Properties();
        values.setProperty("server", "SYNTHETIC-SQL");
        values.setProperty("database", "SYNTHETIC_AUTHORITY");
        values.setProperty("authorityId", INVOCATION.toString());
        values.setProperty(
                "policyFingerprint", RuntimeAuthorizationPolicy.standard().fingerprint().sha256());
        return new RuntimeAuthorityConfiguration(values);
    }

    private static RuntimeAuthorizationScope scope(
            final RuntimeAction action, final String tenant) {
        final var id = new RuntimeWorkloadId("coletas");
        final var hash = new ImmutableFingerprint("test-v1", "b".repeat(64));
        final var definition =
                new RuntimeWorkloadDefinition(
                        id,
                        "DATA_EXPORT",
                        "synthetic-source",
                        tenant,
                        "coletas",
                        hash,
                        hash,
                        Duration.ofSeconds(30));
        final var request =
                new RuntimeExecutionRequest(
                        EXECUTION,
                        id,
                        ExecutionMode.BACKFILL,
                        RuntimeWindowStrategy.INTERVAL,
                        NOW,
                        NOW.plusSeconds(3600),
                        "synthetic-idempotency");
        return RuntimeAuthorizationScope.from(
                INVOCATION,
                action,
                RuntimeWorkloadRegistry.of(definition)
                        .plan(
                                new RuntimePlanningRequest(
                                        EXECUTION, "LOCAL_SHADOW", hash, NOW, request)));
    }

    @Test
    void requiresDurableReceiptThenConsumptionBeforeBusinessComposition() {
        final var scope = scope(RuntimeAction.RUN, "synthetic-tenant");
        final var jdbc = new Jdbc(scope);
        final var authority = new WindowsSqlRuntimeAuthorization(configuration(), jdbc::connection);
        final var capability = authority.authorize(scope);
        assertFalse(jdbc.consumed.get());
        assertEquals(INVOCATION, capability.invocationId());
        assertEquals(NOW.plusSeconds(30), capability.validUntil());
        assertEquals(
                "done",
                authority.consumeAndExecute(
                        capability,
                        scope,
                        () -> {
                            assertTrue(jdbc.consumed.get());
                            return "done";
                        }));
        final var duplicate =
                assertThrows(
                        DurableAuthorizationException.class,
                        () -> authority.consumeAndExecute(capability, scope, () -> "never"));
        assertEquals(DurableAuthorizationException.Reason.ALREADY_CONSUMED, duplicate.reason());
        assertTrue(duplicate.internalCause().isPresent());
        assertFalse(capability.toString().contains(INVOCATION.toString()));
        assertFalse(scope.toString().contains("tenant"));
        assertEquals(EXECUTION, scope.executionId());
        assertEquals(RuntimeAction.RUN, scope.action());
    }

    @Test
    void deniesScopeSubstitutionAndRevocationAtConsumption() {
        final var scope = scope(RuntimeAction.RUN, "synthetic-tenant");
        final var jdbc = new Jdbc(scope);
        final var authority = new WindowsSqlRuntimeAuthorization(configuration(), jdbc::connection);
        final var capability = authority.authorize(scope);
        assertEquals(
                DurableAuthorizationException.Reason.SCOPE_CHANGED,
                assertThrows(
                                DurableAuthorizationException.class,
                                () ->
                                        authority.consumeAndExecute(
                                                capability,
                                                scope(RuntimeAction.RUN, "another-tenant"),
                                                () -> true))
                        .reason());
        assertEquals(
                DurableAuthorizationException.Reason.SCOPE_CHANGED,
                assertThrows(
                                DurableAuthorizationException.class,
                                () ->
                                        authority.consumeAndExecute(
                                                capability,
                                                scope(RuntimeAction.STATUS, "synthetic-tenant"),
                                                () -> true))
                        .reason());
        jdbc.fail = new SQLException("synthetic-private-revocation", "", 52414);
        final var revoked =
                assertThrows(
                        DurableAuthorizationException.class,
                        () -> authority.consumeAndExecute(capability, scope, () -> true));
        assertTrue(revoked.internalCause().isPresent());
        assertFalse(revoked.toString().contains("private"));
        assertFalse(jdbc.consumed.get());
    }

    @Test
    void rejectsMissingDuplicateMalformedAndUnacknowledgedReceipts() {
        final var scope = scope(RuntimeAction.RUN, "synthetic-tenant");
        for (final String mutation :
                new String[] {
                    "missing",
                    "duplicate",
                    "hash",
                    "policy",
                    "time",
                    "mapping",
                    "decision",
                    "target",
                    "password",
                    "context",
                    "ack"
                }) {
            final var jdbc = new Jdbc(scope);
            jdbc.mutation = mutation;
            assertThrows(
                    DurableAuthorizationException.class,
                    () ->
                            new WindowsSqlRuntimeAuthorization(configuration(), jdbc::connection)
                                    .authorize(scope),
                    mutation);
            assertFalse(jdbc.consumed.get());
        }
        final var jdbc = new Jdbc(scope);
        jdbc.denied = true;
        final var denied =
                assertThrows(
                        DurableAuthorizationException.class,
                        () ->
                                new WindowsSqlRuntimeAuthorization(
                                                configuration(), jdbc::connection)
                                        .authorize(scope));
        assertEquals(DurableAuthorizationException.Reason.DENIED, denied.reason());
        assertTrue(denied.internalCause().isEmpty());
    }

    @Test
    void defaultArtifactIsUnconfiguredAndConfigurationHasClosedSyntax() {
        assertEquals(
                DurableAuthorizationException.Reason.UNCONFIGURED,
                assertThrows(
                                DurableAuthorizationException.class,
                                WindowsSqlRuntimeAuthorization::fromAdministeredArtifact)
                        .reason());
        assertThrows(
                IllegalArgumentException.class,
                () -> new RuntimeAuthorityConfiguration(new Properties()));
        final var configuration = configuration();
        assertTrue(configuration.jdbcUrl().contains("trustServerCertificate=false"));
        assertFalse(configuration.toString().contains("SYNTHETIC"));
        assertThrows(
                IllegalArgumentException.class,
                () -> scope(RuntimeAction.REPLAY, "synthetic-tenant"));
        assertThrows(
                IllegalArgumentException.class,
                () -> scope(RuntimeAction.SWEEP_APPLY, "synthetic-tenant"));
    }

    static final class Jdbc {
        final RuntimeAuthorizationScope scope;
        final AtomicBoolean consumed = new AtomicBoolean();
        String mutation = "";
        boolean denied;
        SQLException fail;

        Jdbc(final RuntimeAuthorizationScope scope) {
            this.scope = scope;
        }

        Connection connection() {
            return proxy(
                    Connection.class,
                    (method, args) -> {
                        if (method.equals("prepareStatement")) {
                            return statement(false);
                        }
                        if (method.equals("prepareCall")) {
                            return statement(true);
                        }
                        return null;
                    });
        }

        private java.sql.CallableStatement statement(final boolean authorization) {
            final var parameters = new java.util.HashMap<Integer, Object>();
            return proxy(
                    java.sql.CallableStatement.class,
                    (method, args) -> {
                        if (method.startsWith("set")
                                && args.length > 1
                                && args[0] instanceof Integer index) {
                            parameters.put(index, args[1]);
                        }
                        if (method.equals("executeQuery")) {
                            if (!authorization) {
                                return rows(
                                        Map.of(
                                                1,
                                                mutation.equals("target")
                                                        ? "other"
                                                        : "SYNTHETIC-SQL",
                                                2,
                                                "SYNTHETIC_AUTHORITY",
                                                3,
                                                mutation.equals("password") ? "SQL" : "NTLM",
                                                4,
                                                mutation.equals("context") ? 0 : 1),
                                        false);
                            }
                            if (fail != null) {
                                throw fail;
                            }
                            final boolean consuming = "CONSUME".equals(parameters.get(1));
                            if (consuming && !consumed.compareAndSet(false, true)) {
                                throw new SQLException("SYNTHETIC_ALREADY_CONSUMED", "", 52415);
                            }
                            final var row = new java.util.HashMap<Object, Object>();
                            row.put("receipt_id", EXECUTION.toString());
                            row.put(
                                    "decision",
                                    mutation.equals("decision")
                                            ? "TRUE"
                                            : denied ? "DENY" : "ALLOW");
                            row.put("reason", "AUTHORIZED");
                            row.put("audit_reference", INVOCATION.toString());
                            row.put("mapping_version", mutation.equals("mapping") ? 0L : 1L);
                            row.put("scope_version", 1L);
                            row.put(
                                    "policy_fingerprint",
                                    mutation.equals("policy")
                                            ? "c".repeat(64)
                                            : RuntimeAuthorizationPolicy.standard()
                                                    .fingerprint()
                                                    .sha256());
                            row.put(
                                    "scope_hash",
                                    mutation.equals("hash") ? "c".repeat(64) : scope.hash());
                            row.put("authorized_at_utc", Timestamp.from(NOW));
                            row.put(
                                    "valid_until_utc",
                                    Timestamp.from(
                                            mutation.equals("time") ? NOW : NOW.plusSeconds(30)));
                            row.put("fence", consuming ? 1L : 0L);
                            return rows(row, true);
                        }
                        if (method.equals("getMoreResults")) {
                            if (mutation.equals("ack")) {
                                throw new SQLException("SYNTHETIC_COMMIT_ACK_UNKNOWN");
                            }
                            return false;
                        }
                        if (method.equals("getUpdateCount")) {
                            return -1;
                        }
                        return null;
                    });
        }

        private java.sql.ResultSet rows(final Map<?, ?> values, final boolean authorization) {
            final int[] index = {0};
            return proxy(
                    java.sql.ResultSet.class,
                    (method, args) -> {
                        if (method.equals("next")) {
                            return ++index[0]
                                    <= (authorization && mutation.equals("missing")
                                            ? 0
                                            : authorization && mutation.equals("duplicate")
                                                    ? 2
                                                    : 1);
                        }
                        if (method.equals("getMetaData")) {
                            return proxy(
                                    java.sql.ResultSetMetaData.class,
                                    (name, ignored) -> name.equals("getColumnCount") ? 11 : null);
                        }
                        if (method.startsWith("get") && args.length > 0) {
                            return values.get(args[0]);
                        }
                        return null;
                    });
        }
    }

    @FunctionalInterface
    private interface Call {
        Object call(String method, Object[] args) throws Throwable;
    }

    private static <T> T proxy(final Class<T> type, final Call call) {
        return type.cast(
                Proxy.newProxyInstance(
                        type.getClassLoader(),
                        new Class<?>[] {type},
                        (proxy, method, args) -> {
                            final Object value =
                                    call.call(
                                            method.getName(), args == null ? new Object[0] : args);
                            if (value != null || !method.getReturnType().isPrimitive()) {
                                return value;
                            }
                            if (method.getReturnType() == boolean.class) {
                                return false;
                            }
                            if (method.getReturnType() == int.class) {
                                return 0;
                            }
                            if (method.getReturnType() == long.class) {
                                return 0L;
                            }
                            return null;
                        }));
    }
}
