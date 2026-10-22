package br.com.esl.etl.v2.bootstrap;

import br.com.esl.etl.v2.plataforma.configuracao.C78DevPeerProof;
import br.com.esl.etl.v2.plataforma.persistencia.controle.C78DevCatalogAdmission;
import br.com.esl.etl.v2.plataforma.persistencia.controle.C78DevCatalogPlan;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.sun.nio.file.ExtendedOpenOption;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.LinkOption;
import java.nio.file.StandardOpenOption;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

/** Finite private Bank-controller IPC, same actual socket, single claim, no permissive default. */
final class RuntimeDevCatalogControl
        implements C78DevCatalogAdmission, C78DevPeerProof, AutoCloseable {
    static final String SCHEMA = "V2_DEV_C78_CATALOG_CONTROL_V1";
    private final RuntimeDevCatalogDispatch dispatch;
    private final InputStream input;
    private final PrintStream output;
    private final AtomicBoolean reading = new AtomicBoolean();
    private final java.util.concurrent.ExecutorService reader =
            Executors.newSingleThreadExecutor(
                    task -> {
                        final var thread = new Thread(task, "c78-owned-control-reader");
                        thread.setDaemon(true);
                        return thread;
                    });
    private FileChannel claim;
    private boolean started;
    private boolean peerProved;
    private boolean outcomeWritten;

    RuntimeDevCatalogControl(
            final RuntimeDevCatalogDispatch dispatch,
            final InputStream input,
            final PrintStream output) {
        this.dispatch = java.util.Objects.requireNonNull(dispatch);
        this.input = java.util.Objects.requireNonNull(input);
        this.output = java.util.Objects.requireNonNull(output);
    }

    @Override
    public void beforeOpen(final C78DevCatalogPlan plan) throws SQLException {
        try {
            if (started
                    || !plan.invocationId().equals(dispatch.invocation())
                    || !plan.target()
                            .jdbcUrl()
                            .equals(dispatch.descriptor.path("target").textValue())) {
                throw RuntimeDevCatalogDispatch.refused("START_BINDING_OR_REPLAY");
            }
            validatePlanSelection(plan, dispatch.descriptor);
            dispatch.checkDeadline();
            final var event = event("START_REQUEST");
            publish(event);
            validateFrame(
                    readFrame(), dispatch.descriptor, dispatch.sha256, "START_PERMIT", null, null);
            dispatch.checkDeadline();
            RuntimeDevCatalogDispatch.localDirectory(dispatch.occurrence);
            claim =
                    FileChannel.open(
                            dispatch.occurrence.resolve("runtime-claim.json"),
                            Set.of(
                                    StandardOpenOption.CREATE_NEW,
                                    StandardOpenOption.READ,
                                    StandardOpenOption.WRITE,
                                    LinkOption.NOFOLLOW_LINKS,
                                    ExtendedOpenOption.NOSHARE_WRITE,
                                    ExtendedOpenOption.NOSHARE_DELETE));
            write(claim, new ObjectMapper().writeValueAsBytes(event));
            started = true;
        } catch (final IOException | RuntimeException failure) {
            throw new SQLException("DEV_CATALOG_START_ADMISSION_REFUSED");
        }
    }

    @Override
    public void verify(final Tuple tuple) throws IOException {
        if (!started || peerProved || outcomeWritten) {
            throw RuntimeDevCatalogDispatch.refused("PEER_PHASE");
        }
        dispatch.checkDeadline();
        final Instant published = Instant.now();
        final var event = event("PRELOGIN_PEER_REQUEST");
        event.put("tuple", tuple);
        event.put("publishedUtc", published.toString());
        publish(event);
        validateFrame(
                readFrame(),
                dispatch.descriptor,
                dispatch.sha256,
                "PRELOGIN_PEER_PERMIT",
                tuple,
                published);
        dispatch.checkDeadline();
        peerProved = true;
    }

    @Override
    public void afterClose(
            final C78DevCatalogPlan plan,
            final boolean connectionClosed,
            final int selectsAttempted)
            throws SQLException {
        try {
            if (!started
                    || outcomeWritten
                    || !plan.invocationId().equals(dispatch.invocation())
                    || selectsAttempted < 0
                    || selectsAttempted > C78DevCatalogPlan.MAX_SELECTS
                    || selectsAttempted > 0 && !peerProved) {
                throw RuntimeDevCatalogDispatch.refused("OUTCOME_PHASE");
            }
            validatePlanSelection(plan, dispatch.descriptor);
            final var receipt = event("JDBC_OUTCOME");
            receipt.put("connectionClosed", connectionClosed);
            receipt.put("selectsAttempted", selectsAttempted);
            receipt.put("preloginPeerProved", peerProved);
            receipt.put("sourceExecuted", false);
            receipt.put("captureExecuted", false);
            receipt.put("engineExecuted", false);
            try (var channel =
                    FileChannel.open(
                            dispatch.occurrence.resolve("runtime-jdbc-outcome.json"),
                            StandardOpenOption.CREATE_NEW,
                            StandardOpenOption.WRITE,
                            LinkOption.NOFOLLOW_LINKS)) {
                write(channel, new ObjectMapper().writeValueAsBytes(receipt));
            }
            outcomeWritten = true;
        } catch (final IOException | RuntimeException failure) {
            throw new SQLException("DEV_CATALOG_OUTCOME_UNCONFIRMED");
        }
    }

    static void validatePlanSelection(final C78DevCatalogPlan plan, final JsonNode descriptor)
            throws IOException {
        try {
            if (!plan.equals(
                    RuntimeDevCatalogExecution.planForDispatch(
                            descriptor, plan.invocationId(), plan.target()))) {
                throw RuntimeDevCatalogDispatch.refused("PLAN_OPERATION_OR_SCOPE_BINDING");
            }
        } catch (final IllegalArgumentException | NullPointerException malformed) {
            throw RuntimeDevCatalogDispatch.refused("PLAN_OPERATION_OR_SCOPE_BINDING");
        }
    }

    private LinkedHashMap<String, Object> event(final String kind) {
        final var event = new LinkedHashMap<String, Object>();
        event.put("schema", SCHEMA);
        event.put("kind", kind);
        for (final var name : Set.of("dispatchId", "invocationId", "nonce", "parentPID")) {
            event.put(name, dispatch.descriptor.get(name));
        }
        event.put("dispatchSHA256", dispatch.sha256);
        event.put("childPID", ProcessHandle.current().pid());
        return event;
    }

    private void publish(final Map<String, Object> event) throws IOException {
        final String bytes = new ObjectMapper().writeValueAsString(event);
        if (bytes.length() > 8192) {
            throw RuntimeDevCatalogDispatch.refused("EVENT_LIMIT");
        }
        output.println(bytes);
        output.flush();
        if (output.checkError()) {
            throw RuntimeDevCatalogDispatch.refused("CONTROL_OUTPUT");
        }
    }

    private byte[] readFrame() throws IOException {
        dispatch.checkDeadline();
        final long remaining =
                Duration.between(
                                Instant.now(),
                                Instant.parse(dispatch.descriptor.path("deadlineUtc").textValue()))
                        .toMillis();
        if (remaining <= 0) {
            throw new SocketTimeoutException("DEV_CATALOG_CONTROL_DEADLINE");
        }
        final var future =
                reader.submit(
                        () -> {
                            reading.set(true);
                            try {
                                return readLine(input);
                            } finally {
                                reading.set(false);
                            }
                        });
        try {
            return future.get(Math.min(10_000, remaining), TimeUnit.MILLISECONDS);
        } catch (final TimeoutException timeout) {
            future.cancel(true);
            throw new SocketTimeoutException("DEV_CATALOG_CONTROL_DEADLINE");
        } catch (final InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw RuntimeDevCatalogDispatch.refused("CONTROL_INTERRUPTED");
        } catch (final ExecutionException failed) {
            throw RuntimeDevCatalogDispatch.refused("CONTROL_READ");
        }
    }

    static byte[] readLine(final InputStream input) throws IOException {
        final var line = new ByteArrayOutputStream();
        for (int index = 0; index <= 8192; index++) {
            final int next = input.read();
            if (next == '\n' && line.size() > 0) {
                return line.toByteArray();
            }
            if (next == '\r' && line.size() > 0 && input.read() == '\n') {
                return line.toByteArray();
            }
            if (next < 32 || next > 126 || index == 8192) {
                throw RuntimeDevCatalogDispatch.refused("CONTROL_EOF_SHAPE_OR_LIMIT");
            }
            line.write(next);
        }
        throw RuntimeDevCatalogDispatch.refused("CONTROL_LIMIT");
    }

    static void validateFrame(
            final byte[] bytes,
            final JsonNode descriptor,
            final String digest,
            final String kind,
            final Tuple tuple,
            final Instant published)
            throws IOException {
        if (bytes.length > 8192) {
            throw RuntimeDevCatalogDispatch.refused("CONTROL_LIMIT");
        }
        final JsonNode node;
        try {
            node =
                    JsonMapper.builder()
                            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                            .build()
                            .readTree(bytes);
        } catch (final IOException malformed) {
            throw RuntimeDevCatalogDispatch.refused("CONTROL_JSON");
        }
        final var keys =
                new HashSet<>(
                        Set.of(
                                "schema",
                                "kind",
                                "dispatchId",
                                "invocationId",
                                "nonce",
                                "parentPID",
                                "dispatchSHA256",
                                "childPID",
                                "permit"));
        if (tuple != null) {
            keys.addAll(Set.of("tuple", "nativeProof"));
        }
        RuntimeDevCatalogDispatch.keys(node, keys);
        RuntimeDevCatalogDispatch.exact(node, "schema", SCHEMA);
        RuntimeDevCatalogDispatch.exact(node, "kind", kind);
        RuntimeDevCatalogDispatch.exact(node, "dispatchSHA256", digest);
        RuntimeDevCatalogDispatch.flag(node, "permit", true);
        for (final var key : Set.of("dispatchId", "invocationId", "nonce", "parentPID")) {
            if (!descriptor.path(key).equals(node.path(key))) {
                throw RuntimeDevCatalogDispatch.refused("CONTROL_BINDING");
            }
        }
        if (!node.path("childPID").isIntegralNumber()
                || node.path("childPID").longValue() != ProcessHandle.current().pid()) {
            throw RuntimeDevCatalogDispatch.refused("CONTROL_CHILD");
        }
        if (tuple != null) {
            final var actual = node.path("tuple");
            RuntimeDevCatalogDispatch.keys(
                    actual, Set.of("localAddress", "localPort", "remoteAddress", "remotePort"));
            if (!new ObjectMapper().valueToTree(tuple).equals(actual)) {
                throw RuntimeDevCatalogDispatch.refused("CONTROL_TUPLE");
            }
            final var proof = node.path("nativeProof");
            RuntimeDevCatalogDispatch.keys(
                    proof,
                    Set.of(
                            "service",
                            "pid",
                            "rawCreationPRE",
                            "rawCreationPOST",
                            "version",
                            "sha256",
                            "loadedImage",
                            "microsoftCacheOnly",
                            "reverseTupleMatches",
                            "observedUtc",
                            "evidenceSHA256"));
            final var expected = descriptor.path("nativeImage");
            for (final var key : Set.of("service", "pid", "version", "sha256")) {
                if (!expected.path(key).equals(proof.path(key))) {
                    throw RuntimeDevCatalogDispatch.refused("NATIVE_BINDING");
                }
            }
            for (final var key : Set.of("rawCreationPRE", "rawCreationPOST")) {
                RuntimeDevCatalogDispatch.exact(
                        proof, key, expected.path("rawCreation").textValue());
            }
            RuntimeDevCatalogDispatch.flag(proof, "loadedImage", true);
            RuntimeDevCatalogDispatch.flag(proof, "microsoftCacheOnly", true);
            RuntimeDevCatalogDispatch.match(proof, "evidenceSHA256", "[a-f0-9]{64}");
            if (!proof.path("reverseTupleMatches").isIntegralNumber()
                    || proof.path("reverseTupleMatches").longValue() != 1) {
                throw RuntimeDevCatalogDispatch.refused("NATIVE_TUPLE");
            }
            try {
                final var observed = Instant.parse(proof.path("observedUtc").textValue());
                if (published == null
                        || observed.isBefore(published)
                        || observed.isAfter(Instant.now())) {
                    throw RuntimeDevCatalogDispatch.refused("NATIVE_FRESHNESS");
                }
            } catch (final RuntimeException invalid) {
                throw RuntimeDevCatalogDispatch.refused("NATIVE_TIME");
            }
        }
    }

    private static void write(final FileChannel channel, final byte[] bytes) throws IOException {
        final var buffer = ByteBuffer.wrap(bytes);
        while (buffer.hasRemaining()) {
            channel.write(buffer);
        }
        channel.force(true);
    }

    @Override
    public void close() throws IOException {
        reader.shutdownNow();
        IOException failure = null;
        try {
            if (!reader.awaitTermination(1, TimeUnit.SECONDS) || reading.get()) {
                failure = RuntimeDevCatalogDispatch.refused("CONTROL_READER_CLOSE_UNCERTAIN");
            } else {
                input.close();
            }
        } catch (final InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            failure = RuntimeDevCatalogDispatch.refused("CONTROL_CLOSE_INTERRUPTED");
        } catch (final IOException current) {
            failure = current;
        }
        try {
            if (claim != null) {
                claim.close();
            }
        } catch (final IOException current) {
            if (failure == null) {
                failure = current;
            } else {
                failure.addSuppressed(current);
            }
        }
        if (failure != null) {
            throw failure;
        }
    }
}
