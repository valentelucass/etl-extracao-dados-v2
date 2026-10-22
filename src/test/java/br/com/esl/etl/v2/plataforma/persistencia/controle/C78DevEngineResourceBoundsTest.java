package br.com.esl.etl.v2.plataforma.persistencia.controle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.bootstrap.RuntimeDevEngineBankBinding;
import br.com.esl.etl.v2.bootstrap.RuntimeDevEngineBankBinding.MissingGate;
import br.com.esl.etl.v2.bootstrap.RuntimeDevEngineBankBinding.Refusal;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/**
 * Resource bounds with synthetic controls only; none of these fixtures grants physical authority.
 */
class C78DevEngineResourceBoundsTest {
    @Test
    void sixArtifactsRefuseSeventhBeforeLeaseAndCloseAllHandles() throws Exception {
        final var f = fixture();
        final C78DevEngineDispatch control = f.control();
        try {
            control.verify();
            // Sixth descriptive artifact is loaded explicitly to exercise the resource cap;
            // supplier quota JSON is no longer a source authority or a local-unit prerequisite.
            final Method reference = method("reference", String.class, MissingGate.class);
            reference.invoke(control, "sourceQuota", MissingGate.ADMISSION);
            control.verify();
            assertEquals(6, fieldMap(control, "inputs").size());
            final Map<?, ?> leases = fieldMap(control, "leases");
            assertEquals(6, leases.size());
            final Path extra = f.unit.resolve("root/extra.json");
            Files.writeString(extra, "{}");
            ((ObjectNode) field(control, "dispatch"))
                    .putObject("extra")
                    .put("path", extra.toString())
                    .put(
                            "sha256",
                            C78DevEngineBankAdapterTest.Fixture.hash(Files.readAllBytes(extra)));
            final var failure =
                    assertThrows(
                            InvocationTargetException.class,
                            () -> reference.invoke(control, "extra", MissingGate.ADMISSION));
            assertTrue(failure.getCause() instanceof Refusal);
            assertEquals(6, leases.size());
            assertEquals(6, fieldMap(control, "inputs").size());
            assertFalse(leases.containsKey(extra));
            Files.writeString(extra, "no lease was acquired");
            control.close();
            for (final Object handle : leases.values()) {
                assertFalse(((FileChannel) handle).isOpen());
            }
            control.close();
        } finally {
            control.close();
        }
    }

    @Test
    void ancestorsAllow64AndReject65BeforeAccumulation() throws Exception {
        final var f = fixture();
        try (var control = f.control()) {
            final Map<?, ?> ancestors = fieldMap(control, "ancestors");
            final Method check = method("checkAncestors", Path.class);
            int index = 0;
            while (ancestors.size() < 64) {
                final Path file = f.unit.resolve("ancestor-" + index++);
                Files.writeString(file, "owned metadata fixture");
                check.invoke(control, file);
            }
            assertEquals(64, ancestors.size());
            final Path overflow = f.unit.resolve("ancestor-overflow");
            Files.writeString(overflow, "owned metadata fixture");
            final var failure =
                    assertThrows(
                            InvocationTargetException.class, () -> check.invoke(control, overflow));
            assertEquals("DEV_ENGINE_RESOURCE_LIMIT", failure.getCause().getMessage());
            assertEquals(64, ancestors.size());
            assertFalse(ancestors.containsKey(overflow));
            check.invoke(control, f.path);
            assertEquals(64, ancestors.size());
        }
    }

    @Test
    void sourcePathsEnforceLengthAndDepthBeforeFilesystemLookup() throws Exception {
        final Method bounded = method("boundedPath", Path.class);
        bounded.invoke(null, Path.of("C:/" + "a".repeat(509)));
        bounded.invoke(null, Path.of("C:/" + "a/".repeat(31) + "a"));
        final Method local = method("local", Path.class);
        for (final Path path :
                new Path[] {
                    Path.of("C:/" + "a".repeat(510)), Path.of("C:/" + "a/".repeat(32) + "a")
                }) {
            final var failure =
                    assertThrows(InvocationTargetException.class, () -> local.invoke(null, path));
            assertTrue(failure.getCause() instanceof IOException);
            assertEquals("DEV_ENGINE_PATH_LIMIT", failure.getCause().getMessage());
        }
        final var f = fixture();
        assertEquals(f.path.toAbsolutePath().normalize(), local.invoke(null, f.path));
    }

    @Test
    void statementHandlesCapBeforePrepareReleaseReuseAndOwnerCleanup() throws Exception {
        final var jdbc = new TrackingJdbc();
        try (var h = new Harness(jdbc)) {
            final Connection borrower = h.session.dataSource().getConnection();
            final CallableStatement[] handles = new CallableStatement[64];
            for (int index = 0; index < handles.length; index++) {
                handles[index] = borrower.prepareCall(CALL);
            }
            assertEquals(64, jdbc.prepares);
            assertEquals(64, jdbc.live);
            assertEquals(64, tracked(h.session));
            assertEquals(0, jdbc.executes);
            assertThrows(Refusal.class, () -> borrower.prepareCall(CALL));
            assertEquals(64, jdbc.prepares);
            handles[0].close();
            handles[0].close();
            assertEquals(63, tracked(h.session));
            borrower.prepareCall(CALL);
            assertEquals(65, jdbc.prepares);
            assertEquals(64, jdbc.live);
            h.session.close();
            assertEquals(0, tracked(h.session));
            assertEquals(0, jdbc.live);
            assertEquals(65, jdbc.handleCloses);
            assertThrows(SQLException.class, () -> borrower.prepareCall(CALL));
            assertEquals(65, jdbc.prepares);
            borrower.close();
            h.session.close();
        }
        assertEquals(1, jdbc.rollbacks);
        assertEquals(1, jdbc.closes);
        assertEquals(0, jdbc.commits);
    }

    @Test
    void failedPrepareAndTimeoutSetupDoNotLeakSuccessfulCleanupSlots() throws Exception {
        final var jdbc = new TrackingJdbc();
        try (var h = new Harness(jdbc);
                var borrower = h.session.dataSource().getConnection()) {
            jdbc.failPrepare = true;
            assertThrows(SQLException.class, () -> borrower.prepareCall(CALL));
            assertEquals(0, tracked(h.session));
            jdbc.failPrepare = false;
            jdbc.failTimeout = true;
            assertThrows(SQLException.class, () -> borrower.prepareCall(CALL));
            assertEquals(0, tracked(h.session));
            assertEquals(0, jdbc.live);
            assertEquals(1, jdbc.handleCloses);
            jdbc.failTimeout = false;
            try (var handle = borrower.prepareCall(CALL)) {
                assertFalse(handle.isClosed());
                assertEquals(1, tracked(h.session));
            }
            assertEquals(0, tracked(h.session));
        }
        assertEquals(0, jdbc.live);
    }

    @Test
    void failedCloseRetainsSlotAndOwnerCleanupPreservesFailure() throws Exception {
        final var jdbc = new TrackingJdbc();
        try (var h = new Harness(jdbc);
                var borrower = h.session.dataSource().getConnection()) {
            jdbc.failTimeout = true;
            jdbc.failClosingHandle = 1;
            final SQLException setup =
                    assertThrows(SQLException.class, () -> borrower.prepareCall(CALL));
            assertEquals("synthetic-timeout", setup.getMessage());
            assertEquals(1, setup.getSuppressed().length);
            assertEquals(1, tracked(h.session));
            jdbc.failTimeout = false;
            final var handle = borrower.prepareCall(CALL);
            jdbc.failClosingHandle = 2;
            assertThrows(SQLException.class, handle::close);
            assertEquals(2, tracked(h.session));
            for (int index = 2; index < 64; index++) {
                borrower.prepareCall(CALL);
            }
            assertThrows(Refusal.class, () -> borrower.prepareCall(CALL));
            assertEquals(64, jdbc.prepares);
            final SQLException cleanup = assertThrows(SQLException.class, h.session::close);
            assertEquals("synthetic-statement-close", cleanup.getMessage());
            assertEquals(1, jdbc.rollbacks);
            assertEquals(1, jdbc.closes);
            assertEquals(0, tracked(h.session));
            assertEquals(0, jdbc.live);
            h.session.close();
        }
    }

    private static final String CALL =
            "{call stg.usp_stage_coleta_record(" + "?,".repeat(22) + "?)}";

    private static C78DevEngineBankAdapterTest.Fixture fixture() throws Exception {
        return new C78DevEngineBankAdapterTest.Fixture(C78DevEngineBankAdapterTest.scope());
    }

    private static Object field(final Object owner, final String name) throws Exception {
        final var field = owner.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(owner);
    }

    private static Map<?, ?> fieldMap(final Object owner, final String name) throws Exception {
        return (Map<?, ?>) field(owner, name);
    }

    private static int tracked(final RuntimeDevEngineBankBinding.Session session) throws Exception {
        return ((Set<?>) field(session, "statements")).size();
    }

    private static Method method(final String name, final Class<?>... types) throws Exception {
        final Method method = C78DevEngineDispatch.class.getDeclaredMethod(name, types);
        method.setAccessible(true);
        return method;
    }

    private static final class Harness implements AutoCloseable {
        final InputStream originalIn = System.in;
        final PrintStream originalOut = System.out;
        final RuntimeDevEngineBankBinding.Admission admission;
        final RuntimeDevEngineBankBinding.Session session;

        Harness(final TrackingJdbc jdbc) throws Exception {
            final var f = fixture();
            System.setIn(f.frames());
            System.setOut(new PrintStream(new ByteArrayOutputStream()));
            try {
                admission =
                        f.adapter(
                                        new C78DevEngineBankAdapterTest.SyntheticAdmission(),
                                        new AtomicInteger(),
                                        new AtomicInteger(),
                                        jdbc)
                                .prepare(f.scope);
                session = admission.open();
            } catch (final Exception | Error failure) {
                System.setIn(originalIn);
                System.setOut(originalOut);
                throw failure;
            }
        }

        @Override
        public void close() throws SQLException {
            try {
                admission.close();
            } finally {
                System.setIn(originalIn);
                System.setOut(originalOut);
            }
        }
    }

    private static final class TrackingJdbc extends C78DevEngineBankAdapterTest.SyntheticJdbc {
        int prepares, live, handleCloses, executes;
        int failClosingHandle;
        boolean failPrepare, failTimeout;

        @Override
        Connection connection() {
            final Connection delegate = super.connection();
            return (Connection)
                    Proxy.newProxyInstance(
                            getClass().getClassLoader(),
                            new Class<?>[] {Connection.class},
                            (proxy, method, args) -> {
                                if (method.getName().equals("prepareCall")) {
                                    prepares++;
                                    if (failPrepare) {
                                        throw new SQLException("synthetic-prepare");
                                    }
                                    return callable(prepares);
                                }
                                try {
                                    final Object result = method.invoke(delegate, args);
                                    if (method.getName().equals("close")) {
                                        live = 0;
                                    }
                                    return result;
                                } catch (final InvocationTargetException failure) {
                                    throw failure.getCause();
                                }
                            });
        }

        private CallableStatement callable(final int id) {
            live++;
            final var closed = new AtomicBoolean();
            return (CallableStatement)
                    Proxy.newProxyInstance(
                            getClass().getClassLoader(),
                            new Class<?>[] {CallableStatement.class},
                            (proxy, method, args) -> {
                                if (method.getName().equals("close")) {
                                    if (id == failClosingHandle) {
                                        throw new SQLException("synthetic-statement-close");
                                    }
                                    if (closed.compareAndSet(false, true)) {
                                        live--;
                                        handleCloses++;
                                    }
                                }
                                if (method.getName().equals("setQueryTimeout") && failTimeout) {
                                    throw new SQLException("synthetic-timeout");
                                }
                                if (method.getName().startsWith("execute")) {
                                    executes++;
                                }
                                return defaultValue(method.getReturnType());
                            });
        }
    }
}
