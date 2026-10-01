package br.com.esl.etl.v2.plataforma.persistencia.coletas;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HexFormat;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class Coletas6908SyntheticPolicyTest {
    @Test
    void seedsSeparateBindingsOnTheTrialConnectionThenRollsBothPolicyTablesBack() throws Exception {
        final var physical = new FakePhysical();
        final var reference =
                ColetaShadowRollbackTrial.execute(
                        physical.session(),
                        trial ->
                                Coletas6908SyntheticPolicy.seed(
                                        trial, "source-6908", "tenant-6908"));
        assertTrue(reference.version().startsWith("coletas6908-synthetic-"));
        assertEquals(64, reference.sha256().length());
        assertEquals("source-6908", physical.source);
        assertEquals("tenant-6908", physical.tenant);
        assertEquals(1, physical.policyRowsObserved);
        assertEquals(4, physical.checkRowsObserved);
        assertEquals(0, physical.policyRows);
        assertEquals(0, physical.checkRows);
        assertEquals(1, physical.physicalConnections);
        assertEquals(0, physical.commits);
        assertEquals(2, physical.rollbacks);
        assertEquals(10, physical.policyTimeout);
    }

    @Test
    void rejectsScopeMismatchAndStillRollsBackBothTables() throws Exception {
        final var physical = new FakePhysical();
        physical.mismatchedScope = true;
        final var failure =
                assertThrows(
                        SQLException.class,
                        () ->
                                ColetaShadowRollbackTrial.execute(
                                        physical.session(),
                                        trial ->
                                                Coletas6908SyntheticPolicy.seed(
                                                        trial, "source-6908", "tenant-6908")));
        assertEquals("COL_6908_POLICY_READBACK_MISMATCH", failure.getMessage());
        assertEquals(0, physical.policyRows);
        assertEquals(0, physical.checkRows);
        assertEquals(2, physical.rollbacks);
    }

    @Test
    void rejectsIncompleteCheckReadbackAndDoesNotLeaveRows() throws Exception {
        final var physical = new FakePhysical();
        physical.incompleteChecks = true;
        final var failure =
                assertThrows(
                        SQLException.class,
                        () ->
                                ColetaShadowRollbackTrial.execute(
                                        physical.session(),
                                        trial ->
                                                Coletas6908SyntheticPolicy.seed(
                                                        trial, "source-6908", "tenant-6908")));
        assertEquals("COL_6908_POLICY_READBACK_MISMATCH", failure.getMessage());
        assertEquals(0, physical.policyRows);
        assertEquals(0, physical.checkRows);
    }

    @Test
    void propagatesFenceFailureWithoutGrantOrRetry() throws Exception {
        final var physical = new FakePhysical();
        physical.fenceFailure = true;
        final var failure =
                assertThrows(
                        SQLException.class,
                        () ->
                                ColetaShadowRollbackTrial.execute(
                                        physical.session(),
                                        trial ->
                                                Coletas6908SyntheticPolicy.seed(
                                                        trial, "source-6908", "tenant-6908")));
        assertEquals(52500, failure.getErrorCode());
        assertEquals(1, physical.seedCalls);
        assertEquals(0, physical.policyRows);
        assertEquals(0, physical.checkRows);
    }

    @Test
    void sqlUsesV022MaterialAndDoesNotOwnTheTransactionOrPrivileges() throws Exception {
        final String sql = Coletas6908SyntheticPolicy.script();
        final String upper = sql.toUpperCase(Locale.ROOT);
        assertTrue(sql.contains("N'dq-scope-v1|'"));
        assertTrue(sql.contains("DATALENGTH(@source), N':', @source"));
        assertTrue(sql.contains("DATALENGTH(@tenant), N':', @tenant"));
        assertTrue(sql.contains("N'dq-policy-v1|'"));
        assertTrue(sql.contains("CONVERT(NVARCHAR(33), @now, 126)"));
        assertTrue(sql.contains("N'coletas'"));
        assertTrue(sql.contains("N'BACKFILL'"));
        assertTrue(sql.contains("@@TRANCOUNT < 2"));
        assertFalse(upper.contains("BEGIN TRAN"));
        assertFalse(upper.contains("COMMIT TRAN"));
        assertFalse(upper.contains("ROLLBACK TRAN"));
        assertFalse(upper.contains("GRANT "));
        assertFalse(upper.contains("EXECUTE AS"));
        assertEquals(
                "a39bd25f0407296592d6b1a3eb3194e146f2461706e5020ae176dcf3ec91cfb0",
                Coletas6908SyntheticPolicy.scopeFingerprint("source-6908", "tenant-6908"));
    }

    private static Object value(final Class<?> type) {
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

    private static final class FakePhysical {
        private int depth;
        private int physicalConnections = 1;
        private int commits;
        private int rollbacks;
        private int policyRows;
        private int checkRows;
        private int policyRowsObserved;
        private int checkRowsObserved;
        private int seedCalls;
        private int policyTimeout;
        private boolean mismatchedScope;
        private boolean incompleteChecks;
        private boolean fenceFailure;
        private String source;
        private String tenant;
        private String version;

        private ColetaTemporalLaboratorySession session() throws Exception {
            final var connection =
                    (Connection)
                            Proxy.newProxyInstance(
                                    Connection.class.getClassLoader(),
                                    new Class<?>[] {Connection.class},
                                    (proxy, method, args) ->
                                            switch (method.getName()) {
                                                case "createStatement" -> beginStatement();
                                                case "prepareStatement" ->
                                                        prepared((String) args[0]);
                                                case "rollback" -> {
                                                    rollbacks++;
                                                    depth = 0;
                                                    policyRows = 0;
                                                    checkRows = 0;
                                                    yield null;
                                                }
                                                case "commit" -> {
                                                    commits++;
                                                    yield null;
                                                }
                                                case "isClosed" -> false;
                                                default -> value(method.getReturnType());
                                            });
            final var constructor =
                    ColetaTemporalLaboratorySession.class.getDeclaredConstructor(Connection.class);
            constructor.setAccessible(true);
            return constructor.newInstance(connection);
        }

        private Statement beginStatement() {
            return (Statement)
                    Proxy.newProxyInstance(
                            Statement.class.getClassLoader(),
                            new Class<?>[] {Statement.class},
                            (proxy, method, args) -> {
                                if (method.getName().equals("execute")) {
                                    depth = 2;
                                    return false;
                                }
                                return value(method.getReturnType());
                            });
        }

        private PreparedStatement prepared(final String sql) {
            final boolean seed = sql.contains("INSERT INTO ctl.data_quality_policy");
            return (PreparedStatement)
                    Proxy.newProxyInstance(
                            PreparedStatement.class.getClassLoader(),
                            new Class<?>[] {PreparedStatement.class},
                            (proxy, method, args) -> {
                                if (method.getName().equals("setString") && seed) {
                                    switch ((int) args[0]) {
                                        case 1 -> version = (String) args[1];
                                        case 2 -> source = (String) args[1];
                                        case 3 -> tenant = (String) args[1];
                                        default -> throw new SQLException("UNEXPECTED_BINDING");
                                    }
                                }
                                if (method.getName().equals("setQueryTimeout") && seed) {
                                    policyTimeout = (int) args[0];
                                }
                                if (method.getName().equals("executeQuery")) {
                                    if (!seed) {
                                        return rows(2, 1, 77);
                                    }
                                    seedCalls++;
                                    if (fenceFailure) {
                                        throw new SQLException("FENCE", "42000", 52500);
                                    }
                                    policyRows = 1;
                                    checkRows = incompleteChecks ? 3 : 4;
                                    policyRowsObserved = policyRows;
                                    checkRowsObserved = checkRows;
                                    return rows(0, 0, 0);
                                }
                                return value(method.getReturnType());
                            });
        }

        private ResultSet rows(final int transactionDepth, final int state, final int spid) {
            final boolean transaction = transactionDepth > 0;
            return (ResultSet)
                    Proxy.newProxyInstance(
                            ResultSet.class.getClassLoader(),
                            new Class<?>[] {ResultSet.class},
                            new java.lang.reflect.InvocationHandler() {
                                private int cursor;

                                @Override
                                public Object invoke(
                                        final Object proxy,
                                        final java.lang.reflect.Method method,
                                        final Object[] args)
                                        throws Exception {
                                    if (method.getName().equals("next")) {
                                        return ++cursor == 1;
                                    }
                                    if (method.getName().equals("getInt")) {
                                        return switch ((int) args[0]) {
                                            case 1 -> depth;
                                            case 2 -> state;
                                            case 3 -> spid;
                                            default -> 0;
                                        };
                                    }
                                    if (method.getName().equals("getLong")) {
                                        return (long) ((int) args[0] == 4 ? policyRows : checkRows);
                                    }
                                    if (method.getName().equals("getString") && !transaction) {
                                        return switch ((int) args[0]) {
                                            case 1 -> version;
                                            case 2 ->
                                                    HexFormat.of()
                                                            .formatHex(
                                                                    MessageDigest.getInstance(
                                                                                    "SHA-256")
                                                                            .digest(
                                                                                    version
                                                                                            .getBytes(
                                                                                                    StandardCharsets
                                                                                                            .UTF_8)));
                                            case 3 ->
                                                    mismatchedScope
                                                            ? "0".repeat(64)
                                                            : Coletas6908SyntheticPolicy
                                                                    .scopeFingerprint(
                                                                            source, tenant);
                                            default -> null;
                                        };
                                    }
                                    return value(method.getReturnType());
                                }
                            });
        }
    }
}
