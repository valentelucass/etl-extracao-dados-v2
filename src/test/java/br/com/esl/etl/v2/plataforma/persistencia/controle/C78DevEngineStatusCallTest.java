package br.com.esl.etl.v2.plataforma.persistencia.controle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.bootstrap.RuntimeDevEngineBankBinding;
import br.com.esl.etl.v2.plataforma.controle.ControlPlaneStart;
import br.com.esl.etl.v2.plataforma.controle.ExecutionMode;
import br.com.esl.etl.v2.plataforma.controle.ExecutionPartitionKey;
import br.com.esl.etl.v2.plataforma.controle.ImmutableFingerprint;
import br.com.esl.etl.v2.plataforma.orquestracao.RuntimeRecoveryException;
import br.com.esl.etl.v2.plataforma.qualidade.DataQualityPolicyReference;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;

/** Exact live caller/whitelist seam; component handles never connect or execute SQL. */
class C78DevEngineStatusCallTest {
    @Test
    void actualReadOnlyReadCallerWithEightBindsIsAdmittedByNamedWhitelist() throws Exception {
        final String sql = captureActualCaller();
        assertEquals("{call ctl.usp_runtime_status(?, ?, ?, ?, ?, ?, ?, ?)}", sql);
        try (var guard = guard()) {
            assertTrue(guard.allowedCall(sql));
        }
    }

    @Test
    void compactAndArgumentWhitespaceKeepTheSameNamedEightArity() throws Exception {
        try (var guard = guard()) {
            assertTrue(guard.allowedCall("{call ctl.usp_runtime_status(?,?,?,?,?,?,?,?)}"));
            assertTrue(
                    guard.allowedCall(
                            "{call ctl.usp_runtime_status( ? , ? , ? , ? , ? , ? , ? , ? )}"));
            assertTrue(guard.allowedCall("{call ctl.usp_runtime_recovery(?,?,?,?,?,?,?,?)}"));
        }
    }

    @Test
    void wrongArityOtherNamesCoreLiteralArgumentsAndSqlOverridesRemainRefused() throws Exception {
        try (var guard = guard()) {
            for (final String sql :
                    List.of(
                            "{call ctl.usp_runtime_status()}",
                            "{call ctl.usp_runtime_status(?,?,?,?,?,?,?)}",
                            "{call ctl.usp_runtime_status(?,?,?,?,?,?,?,?,?)}",
                            "{call ctl.usp_runtime_status('READ',?,?,?,?,?,?,?)}",
                            "{call ctl.usp_runtime_status(@operation,?,?,?,?,?,?,?)}",
                            "{call ctl.usp_runtime_status(?,?,?,?,?,?,?,1)}",
                            "{call ctl.usp_runtime_stats(?,?,?,?,?,?,?,?)}",
                            "{call dbo.usp_runtime_status(?,?,?,?,?,?,?,?)}",
                            "{call core.usp_runtime_status(?,?,?,?,?,?,?,?)}",
                            "{CALL ctl.usp_runtime_status(?,?,?,?,?,?,?,?)}",
                            "{call ctl.USP_RUNTIME_STATUS(?,?,?,?,?,?,?,?)}",
                            "{call [ctl].[usp_runtime_status](?,?,?,?,?,?,?,?)}",
                            "{call ctl.usp_runtime_status(?,?,?,?,?,?,?,?)};SELECT 1",
                            "{call ctl.usp_runtime_status(?,?,?,?,?,?,?,?)} ",
                            "SELECT 1")) {
                assertFalse(guard.allowedCall(sql), sql);
            }
        }
    }

    private static C78DevEngineDispatch guard() throws Exception {
        final var constructor =
                C78DevEngineDispatch.class.getDeclaredConstructor(
                        RuntimeDevEngineBankBinding.Scope.class,
                        Path.class,
                        String.class,
                        com.fasterxml.jackson.databind.JsonNode.class);
        constructor.setAccessible(true);
        return constructor.newInstance(
                C78DevEngineBankAdapterTest.scope(),
                Path.of(System.getProperty("bank.test.output")).resolve("root/component.json"),
                "a".repeat(64),
                new ObjectMapper().createObjectNode());
    }

    private static String captureActualCaller() throws Exception {
        final String[] emitted = {null};
        final Set<Integer> binds = new HashSet<>();
        final String[] operation = {null};
        final boolean[] closed = {false, false};
        final CallableStatement statement =
                (CallableStatement)
                        Proxy.newProxyInstance(
                                C78DevEngineStatusCallTest.class.getClassLoader(),
                                new Class<?>[] {CallableStatement.class},
                                (proxy, method, arguments) -> {
                                    if (method.getName().equals("setString")) {
                                        binds.add((Integer) arguments[0]);
                                        if ((Integer) arguments[0] == 2) {
                                            operation[0] = (String) arguments[1];
                                        }
                                    }
                                    if (method.getName().equals("executeQuery")) {
                                        throw new SQLException(
                                                "COMPONENT_CAPTURE_NO_SQL_EXECUTION");
                                    }
                                    if (method.getName().equals("close")) {
                                        closed[0] = true;
                                    }
                                    return null;
                                });
        final Connection connection =
                (Connection)
                        Proxy.newProxyInstance(
                                C78DevEngineStatusCallTest.class.getClassLoader(),
                                new Class<?>[] {Connection.class},
                                (proxy, method, arguments) -> {
                                    if (method.getName().equals("prepareCall")) {
                                        emitted[0] = (String) arguments[0];
                                        return statement;
                                    }
                                    if (method.getName().equals("close")) {
                                        closed[1] = true;
                                    }
                                    return null;
                                });
        final DataSource source =
                (DataSource)
                        Proxy.newProxyInstance(
                                C78DevEngineStatusCallTest.class.getClassLoader(),
                                new Class<?>[] {DataSource.class},
                                (proxy, method, arguments) ->
                                        switch (method.getName()) {
                                            case "getLoginTimeout" -> 5;
                                            case "getConnection" -> connection;
                                            default -> null;
                                        });
        final var caller =
                new JdbcSqlServerRuntimeRecovery(
                        source, Duration.ofSeconds(10), Duration.ofSeconds(20), true, 0);
        final var fingerprint = new ImmutableFingerprint("component", "a".repeat(64));
        final var start =
                new ControlPlaneStart(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        new ExecutionPartitionKey(
                                "shadow",
                                "component",
                                "component",
                                "coletas",
                                ExecutionMode.BACKFILL,
                                Instant.parse("2026-10-13T00:00:00Z"),
                                Instant.parse("2026-10-14T00:00:00Z")),
                        "FULL",
                        fingerprint,
                        fingerprint,
                        "component-only",
                        Optional.empty(),
                        Duration.ofSeconds(30),
                        Instant.now());
        final var call =
                JdbcSqlServerRuntimeRecovery.class.getDeclaredMethod(
                        "call",
                        String.class,
                        ControlPlaneStart.class,
                        ImmutableFingerprint.class,
                        String.class,
                        DataQualityPolicyReference.class,
                        String.class,
                        CancellationToken.class);
        call.setAccessible(true);
        try {
            call.invoke(
                    caller,
                    "READ",
                    start,
                    fingerprint,
                    "component-only",
                    new DataQualityPolicyReference("component", "b".repeat(64)),
                    "",
                    CancellationToken.none());
            throw new AssertionError("component execution must stop");
        } catch (final InvocationTargetException failure) {
            assertTrue(failure.getCause() instanceof RuntimeRecoveryException);
        }
        assertEquals(Set.of(1, 2, 3, 4, 5, 6, 7, 8), binds);
        assertEquals("READ", operation[0]);
        assertTrue(closed[0] && closed[1]);
        return emitted[0];
    }
}
