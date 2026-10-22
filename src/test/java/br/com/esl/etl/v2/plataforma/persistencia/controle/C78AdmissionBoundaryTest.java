package br.com.esl.etl.v2.plataforma.persistencia.controle;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.bootstrap.RuntimeDevEngineBankBinding;
import br.com.esl.etl.v2.plataforma.configuracao.C78LocalSqlTarget;
import br.com.esl.etl.v2.plataforma.configuracao.C78LocalTlsScope;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Validators and invalid private-wire inputs only. Reflection never changes rootVerified, supplies
 * a permit, invokes the native observer, or opens Source/SQL/provider boundaries.
 */
@Isolated("Owns System.in/out only while checking invalid in-memory protocol frames")
class C78AdmissionBoundaryTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String HASH = "a".repeat(64);
    @TempDir Path temporary;

    @ParameterizedTest
    @ValueSource(
            strings = {
                "schema",
                "request",
                "operation",
                "dispatchId",
                "nonce",
                "invocationId",
                "target",
                "leafSHA256",
                "bundleManifestSHA256",
                "configurationSHA256",
                "requestSHA256",
                "planSHA256"
            })
    void inspectionDescriptorRejectsEveryCrossScopeBinding(final String field) throws Exception {
        try (var state = state(Kind.INSPECTION, new SourceSpy())) {
            assertDoesNotThrow(() -> invoke(state.producer, "validateDescriptor"));
            state.descriptor.put(field, "SYNTHETIC_WRONG_BINDING");
            assertEquals(
                    "ROOT_GATE_DESCRIPTOR",
                    assertThrows(
                                    IOException.class,
                                    () -> invoke(state.producer, "validateDescriptor"))
                            .getMessage());
            assertRootStillUnverified(state.producer);
        }
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "schema",
                "request",
                "operation",
                "dispatchId",
                "nonce",
                "invocationId",
                "target",
                "leafSHA256",
                "bundleManifestSHA256",
                "configurationSHA256",
                "requestSHA256",
                "planSHA256"
            })
    void sampleDescriptorRejectsEveryCrossScopeBinding(final String field) throws Exception {
        final var fixture = new C78Sample12AdmissionProducerTest.LocalFixture(false);
        final Path unissued =
                temporary.resolve("hermesf4eda62e83d1/root/never-issued-dispatch.json");
        final ObjectNode candidate = descriptor(fixture.selected.scope());
        candidate
                .put("schema", "V2_DEV_C78_SAMPLE12_DISPATCH_V1")
                .put("operation", "SAMPLE12_ROLLBACK_ONLY")
                .put("dispatchId", "C78SAMPLE12-" + "0".repeat(32));
        try (var controls = fixture.controls();
                var producer =
                        C78Sample12AdmissionProducer.sample12(
                                fixture.selected, unissued, fixture.hash, null)) {
            // Only the pure descriptor validator sees this unissued namespace. The existing
            // local-contract lease remains a fixture and cannot grant Root admission.
            set(producer, "descriptor", candidate);
            set(producer, "files", controls);
            assertDoesNotThrow(() -> invoke(producer, "validateDescriptor"));
            candidate.put(field, "SYNTHETIC_WRONG_BINDING");
            assertEquals(
                    "ROOT_GATE_DESCRIPTOR",
                    assertThrows(IOException.class, () -> invoke(producer, "validateDescriptor"))
                            .getMessage());
            assertRootStillUnverified(producer);
            assertFalse(Files.exists(fixture.occurrence.resolve("runtime-engine-claim.json")));
            assertThrows(IllegalStateException.class, controls::sampleSecret);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "1.5", "\"1\"", "true", "null", "9223372036854775808"})
    void parentPidRequiresAPositiveIntegralWireLong(final String invalid) throws Exception {
        for (final Kind kind : Kind.values()) {
            try (var state = state(kind, new SourceSpy())) {
                state.descriptor.set("parentPID", JSON.readTree(invalid));
                assertEquals(
                        "ROOT_GATE_DESCRIPTOR",
                        assertThrows(
                                        IOException.class,
                                        () -> invoke(state.producer, "validateDescriptor"))
                                .getMessage());
                assertRootStillUnverified(state.producer);
            }
        }
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "connections",
                "statements",
                "HTTP",
                "provider",
                "commit",
                "promotion",
                "retry",
                "unitSeconds"
            })
    void inspectionKeepsEveryZeroEffectLimitTypedAndClosed(final String field) throws Exception {
        try (var state = state(Kind.INSPECTION, new SourceSpy())) {
            for (final String invalid : List.of("null", "true", "\"0\"", "0.5", "2147483648")) {
                final ObjectNode limits = state.descriptor.withObject("limits");
                limits.set(field, JSON.readTree(invalid));
                assertEquals(
                        "ROOT_GATE_ZERO_EFFECT_LIMITS",
                        assertThrows(
                                        IOException.class,
                                        () -> invoke(state.producer, "validateDescriptor"))
                                .getMessage());
            }
            state.descriptor.withObject("limits").put(field, field.equals("unitSeconds") ? 61 : 1);
            assertEquals(
                    "ROOT_GATE_ZERO_EFFECT_LIMITS",
                    assertThrows(
                                    IOException.class,
                                    () -> invoke(state.producer, "validateDescriptor"))
                            .getMessage());
            assertRootStillUnverified(state.producer);
        }
    }

    @ParameterizedTest
    @EnumSource(Kind.class)
    void missingDispatchOrClosedProducerNeverConsultsSource(final Kind kind) throws Exception {
        final var source = new SourceSpy();
        final var state = state(kind, source);
        try {
            assertEquals(
                    RuntimeDevEngineBankBinding.MissingGate.ADMISSION,
                    assertThrows(RuntimeDevEngineBankBinding.Refusal.class, state.producer::verify)
                            .gate());
            assertEquals(0, source.verifications);
            assertEquals(0, source.opens);
            assertRootStillUnverified(state.producer);
        } finally {
            state.close();
        }
        assertEquals(1, source.closes);
        assertEquals(
                RuntimeDevEngineBankBinding.MissingGate.ADMISSION,
                assertThrows(RuntimeDevEngineBankBinding.Refusal.class, state.producer::verify)
                        .gate());
        state.close();
        assertEquals(1, source.closes);
    }

    @ParameterizedTest
    @EnumSource(Kind.class)
    void aLooseClassesDirectoryCannotStandInForTheLeasedProductJar(final Kind kind) {
        assertEquals(
                "ROOT_GATE_ACTUAL_LEASED_JAR_REQUIRED",
                assertThrows(
                                IOException.class,
                                () -> invokeStatic(kind.type, "ownBundle", new Class<?>[0]))
                        .getMessage());
    }

    @ParameterizedTest
    @EnumSource(Kind.class)
    @EnabledOnOs(OS.WINDOWS)
    void imagePinsRejectWrongAndOversizeBytesAndReleaseTheirReadLease(final Kind kind)
            throws Exception {
        final Path image = temporary.resolve("unqualified-image.bin");
        Files.write(image, new byte[32]);
        assertEquals(
                "ROOT_GATE_PARENT_IMAGE_NOT_QUALIFIED",
                assertThrows(
                                IOException.class,
                                () ->
                                        invokeStatic(
                                                kind.type,
                                                "verifyPython",
                                                new Class<?>[] {Path.class},
                                                image))
                        .getMessage());
        Files.delete(image);
        Files.write(image, new byte[2_000_001]);
        assertEquals(
                "ROOT_GATE_PARENT_IMAGE_LIMIT",
                assertThrows(
                                IOException.class,
                                () ->
                                        invokeStatic(
                                                kind.type,
                                                "verifyPython",
                                                new Class<?>[] {Path.class},
                                                image))
                        .getMessage());
        Files.delete(image);
        assertFalse(Files.exists(image));
        assertThrows(
                IOException.class,
                () -> invokeStatic(kind.type, "verifyPython", new Class<?>[] {Path.class}, image));
    }

    @ParameterizedTest
    @EnumSource(Kind.class)
    void remainingWaitUsesTheOriginalDeadlineWithoutRenewingIt(final Kind kind) throws Exception {
        try (var state = state(kind, new SourceSpy())) {
            state.descriptor.put("deadlineUtc", Instant.EPOCH.toString());
            assertEquals(1L, invoke(state.producer, "remaining"));
            final Instant deadline = Instant.now().plusSeconds(60);
            state.descriptor.put("deadlineUtc", deadline.toString());
            final long remaining = (long) invoke(state.producer, "remaining");
            assertTrue(remaining > 0 && remaining <= 60_000);
            assertEquals(deadline.toString(), state.descriptor.path("deadlineUtc").textValue());
            assertRootStillUnverified(state.producer);
        }
    }

    static Stream<Arguments> invalidFrames() {
        return Stream.of(Kind.values())
                .flatMap(
                        kind ->
                                Stream.of(
                                        Arguments.of(kind, "EOF", new byte[0]),
                                        Arguments.of(kind, "CONTROL", new byte[] {0}),
                                        Arguments.of(kind, "NON_ASCII", new byte[] {127}),
                                        Arguments.of(
                                                kind,
                                                "OVERSIZE",
                                                "x"
                                                        .repeat(8192)
                                                        .getBytes(StandardCharsets.US_ASCII)),
                                        Arguments.of(
                                                kind,
                                                "DUPLICATE",
                                                "{\"x\":1,\"x\":2}\n"
                                                        .getBytes(StandardCharsets.US_ASCII)),
                                        Arguments.of(
                                                kind,
                                                "TRAILING",
                                                "{}{}\n".getBytes(StandardCharsets.US_ASCII)),
                                        Arguments.of(
                                                kind,
                                                "WRONG_SHAPE",
                                                "{}\n".getBytes(StandardCharsets.US_ASCII))));
    }

    @ParameterizedTest(name = "{0} rejects {1}")
    @MethodSource("invalidFrames")
    void invalidPrivateFramesCannotBecomePermits(
            final Kind kind, final String reason, final byte[] input) throws Exception {
        final var source = new SourceSpy();
        final var originalInput = System.in;
        final var originalOutput = System.out;
        final var bytes = new ByteArrayOutputStream();
        try (var output = new PrintStream(bytes, true, StandardCharsets.UTF_8)) {
            System.setIn(new ByteArrayInputStream(input));
            System.setOut(output);
            try (var state = state(kind, source)) {
                final IOException refusal =
                        assertThrows(
                                IOException.class,
                                () ->
                                        invoke(
                                                state.producer,
                                                "exchange",
                                                new Class<?>[] {ProcessHandle.class},
                                                ProcessHandle.current()));
                assertEquals(
                        reason.equals("WRONG_SHAPE")
                                ? "ROOT_GATE_PRIVATE_REPLY_SHAPE"
                                : "ROOT_GATE_PRIVATE_REFUSED",
                        refusal.getMessage());
                if (!reason.equals("WRONG_SHAPE")) {
                    assertTrue(
                            refusal.getCause() instanceof java.util.concurrent.ExecutionException);
                }
                final JsonNode request = JSON.readTree(bytes.toByteArray());
                assertEquals("ENGINE_GATE_REQUEST", request.path("kind").textValue());
                assertEquals(8, request.size());
                assertEquals(0, source.verifications);
                assertEquals(0, source.opens);
                assertRootStillUnverified(state.producer);
            }
            assertEquals(1, source.closes);
        } finally {
            System.setIn(originalInput);
            System.setOut(originalOutput);
        }
    }

    @ParameterizedTest
    @EnumSource(Kind.class)
    void interruptedPrivateWaitPreservesInterruptAndStillClosesOwnedReader(final Kind kind)
            throws Exception {
        final var originalInput = System.in;
        final var originalOutput = System.out;
        final var source = new SourceSpy();
        try (var output = new PrintStream(new ByteArrayOutputStream())) {
            System.setIn(
                    new java.io.InputStream() {
                        @Override
                        public int read() throws IOException {
                            try {
                                new java.util.concurrent.CountDownLatch(1).await();
                                return -1;
                            } catch (final InterruptedException interrupted) {
                                Thread.currentThread().interrupt();
                                throw new IOException("OWN_TEST_READER_CANCELLED", interrupted);
                            }
                        }
                    });
            System.setOut(output);
            try (var state = state(kind, source)) {
                Thread.currentThread().interrupt();
                try {
                    assertEquals(
                            "ROOT_GATE_PRIVATE_INTERRUPTED",
                            assertThrows(
                                            IOException.class,
                                            () ->
                                                    invoke(
                                                            state.producer,
                                                            "exchange",
                                                            new Class<?>[] {ProcessHandle.class},
                                                            ProcessHandle.current()))
                                    .getMessage());
                    assertTrue(Thread.currentThread().isInterrupted());
                    assertRootStillUnverified(state.producer);
                } finally {
                    Thread.interrupted();
                }
            }
            assertEquals(1, source.closes);
        } finally {
            Thread.interrupted();
            System.setIn(originalInput);
            System.setOut(originalOutput);
        }
    }

    @Test
    void failedSampleRequestWriteDoesNotBeginReadingAReply() throws Exception {
        final var originalInput = System.in;
        final var originalOutput = System.out;
        final var reads = new java.util.concurrent.atomic.AtomicInteger();
        final var source = new SourceSpy();
        try (var output =
                new PrintStream(
                        new OutputStream() {
                            @Override
                            public void write(final int value) throws IOException {
                                throw new IOException("OWN_TEST_OUTPUT_REFUSAL");
                            }
                        })) {
            System.setIn(
                    new ByteArrayInputStream(new byte[0]) {
                        @Override
                        public synchronized int read() {
                            reads.incrementAndGet();
                            return -1;
                        }
                    });
            System.setOut(output);
            try (var state = state(Kind.SAMPLE, source)) {
                assertEquals(
                        "ROOT_GATE_PRIVATE_WRITE_FAILED",
                        assertThrows(
                                        IOException.class,
                                        () ->
                                                invoke(
                                                        state.producer,
                                                        "exchange",
                                                        new Class<?>[] {ProcessHandle.class},
                                                        ProcessHandle.current()))
                                .getMessage());
                assertEquals(0, reads.get());
                assertRootStillUnverified(state.producer);
            }
            assertEquals(1, source.closes);
        } finally {
            System.setIn(originalInput);
            System.setOut(originalOutput);
        }
    }

    @ParameterizedTest
    @EnumSource(Kind.class)
    void inputCloseFailureRetainsPrimaryAndStillClosesSourceExactlyOnce(final Kind kind)
            throws Exception {
        final var originalInput = System.in;
        final var originalOutput = System.out;
        final var source = new SourceSpy();
        source.closeFailure = new SQLException("OWN_TEST_SOURCE_CLOSE");
        final var closeFailure = new IOException("OWN_TEST_INPUT_CLOSE");
        try (var output = new PrintStream(new ByteArrayOutputStream())) {
            System.setIn(
                    new ByteArrayInputStream(new byte[0]) {
                        @Override
                        public void close() throws IOException {
                            throw closeFailure;
                        }
                    });
            System.setOut(output);
            final var state = state(kind, source);
            try {
                assertThrows(
                        IOException.class,
                        () ->
                                invoke(
                                        state.producer,
                                        "exchange",
                                        new Class<?>[] {ProcessHandle.class},
                                        ProcessHandle.current()));
                final SQLException failure = assertThrows(SQLException.class, state::close);
                assertEquals("ROOT_GATE_PRIVATE_CLOSE_FAILED", failure.getMessage());
                assertSame(closeFailure, failure.getCause());
                assertEquals(1, failure.getSuppressed().length);
                assertSame(source.closeFailure, failure.getSuppressed()[0]);
                assertEquals(1, source.closes);
                state.close();
                assertEquals(1, source.closes);
            } finally {
                state.close();
            }
        } finally {
            System.setIn(originalInput);
            System.setOut(originalOutput);
        }
    }

    private State state(final Kind kind, final SourceSpy source) throws Exception {
        final RuntimeDevEngineBankBinding.Scope scope;
        final RuntimeDevEngineBankBinding.Admission producer;
        final Path path = temporary.resolve("hermesf4eda62e83d1/root/never-issued-dispatch.json");
        if (kind == Kind.SAMPLE) {
            final var selected = C78Sample12OwnerTest.selection();
            scope = selected.scope();
            producer = C78Sample12AdmissionProducer.sample12(selected, path, HASH, source);
        } else {
            scope = C78DevEngineBankAdapterTest.scope();
            producer = C78DevEngineAdmissionProducer.inspect(scope, path, HASH, source);
        }
        final ObjectNode descriptor = descriptor(scope);
        if (kind == Kind.SAMPLE) {
            descriptor
                    .put("schema", "V2_DEV_C78_SAMPLE12_DISPATCH_V1")
                    .put("operation", "SAMPLE12_ROLLBACK_ONLY")
                    .put("dispatchId", "C78SAMPLE12-" + "0".repeat(32));
        }
        set(producer, "descriptor", descriptor);
        return new State(producer, descriptor);
    }

    private static ObjectNode descriptor(final RuntimeDevEngineBankBinding.Scope scope) {
        final ObjectNode descriptor = JSON.createObjectNode();
        descriptor
                .put("schema", "V2_DEV_C78_GATE_INSPECTION_DISPATCH_V1")
                .put("request", "HERMES_f4eda62e83d1")
                .put("operation", "INSPECT_GATES_ONLY")
                .put("dispatchId", "C78GATE01-" + "0".repeat(32))
                .put("nonce", HASH)
                .put("invocationId", scope.authorization().invocationId().toString())
                .put("target", C78LocalSqlTarget.URL)
                .put("leafSHA256", C78LocalTlsScope.LEAF_SHA256)
                .put("bundleManifestSHA256", scope.bundleManifestSHA256())
                .put("configurationSHA256", scope.configurationSHA256())
                .put("requestSHA256", scope.requestSHA256())
                .put("planSHA256", scope.planSHA256())
                .put("parentPID", ProcessHandle.current().pid())
                .put("deadlineUtc", Instant.now().plusSeconds(60).toString());
        descriptor.withObject("rootAuthority").put("sha256", HASH);
        final ObjectNode limits = descriptor.putObject("limits");
        for (final String field :
                List.of(
                        "connections",
                        "statements",
                        "HTTP",
                        "provider",
                        "commit",
                        "promotion",
                        "retry")) {
            limits.put(field, 0);
        }
        limits.put("unitSeconds", 60);
        return descriptor;
    }

    private static void assertRootStillUnverified(final Object producer) throws Exception {
        final var root = producer.getClass().getDeclaredField("rootVerified");
        root.setAccessible(true);
        assertFalse(root.getBoolean(producer));
    }

    private static void set(final Object owner, final String name, final Object value)
            throws Exception {
        final var field = owner.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(owner, value);
    }

    private static Object invoke(final Object owner, final String name) throws Exception {
        return invoke(owner, name, new Class<?>[0]);
    }

    private static Object invoke(
            final Object owner,
            final String name,
            final Class<?>[] types,
            final Object... arguments)
            throws Exception {
        return invokeMethod(owner.getClass(), owner, name, types, arguments);
    }

    private static Object invokeStatic(
            final Class<?> type,
            final String name,
            final Class<?>[] types,
            final Object... arguments)
            throws Exception {
        return invokeMethod(type, null, name, types, arguments);
    }

    private static Object invokeMethod(
            final Class<?> type,
            final Object owner,
            final String name,
            final Class<?>[] types,
            final Object... arguments)
            throws Exception {
        final var method = type.getDeclaredMethod(name, types);
        method.setAccessible(true);
        try {
            return method.invoke(owner, arguments);
        } catch (final InvocationTargetException failure) {
            if (failure.getCause() instanceof Exception exception) {
                throw exception;
            }
            if (failure.getCause() instanceof Error error) {
                throw error;
            }
            throw new AssertionError(failure.getCause());
        }
    }

    private enum Kind {
        INSPECTION(C78DevEngineAdmissionProducer.class),
        SAMPLE(C78Sample12AdmissionProducer.class);

        final Class<?> type;

        Kind(final Class<?> type) {
            this.type = type;
        }
    }

    private record State(RuntimeDevEngineBankBinding.Admission producer, ObjectNode descriptor)
            implements AutoCloseable {
        @Override
        public void close() throws SQLException {
            producer.close();
        }
    }

    private static final class SourceSpy implements RuntimeDevEngineBankBinding.Admission {
        int verifications;
        int opens;
        int closes;
        SQLException closeFailure;

        @Override
        public void verify() {
            verifications++;
            throw new AssertionError("SOURCE_MUST_NOT_BE_CONSULTED");
        }

        @Override
        public RuntimeDevEngineBankBinding.Session open() {
            opens++;
            throw new AssertionError("NO_SOURCE_OR_SQL_OPEN_AUTHORITY");
        }

        @Override
        public void close() throws SQLException {
            closes++;
            if (closeFailure != null) {
                throw closeFailure;
            }
        }
    }
}
