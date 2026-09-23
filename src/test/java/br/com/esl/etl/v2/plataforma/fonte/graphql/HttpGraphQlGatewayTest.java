package br.com.esl.etl.v2.plataforma.fonte.graphql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.plataforma.configuracao.GraphQlClientSettings;
import br.com.esl.etl.v2.plataforma.configuracao.GraphQlSourceConfiguration;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationSignal;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken;
import br.com.esl.etl.v2.plataforma.resiliencia.EslRequestGovernor;
import br.com.esl.etl.v2.plataforma.resiliencia.EslResiliencePolicy;
import br.com.esl.etl.v2.plataforma.resiliencia.MonotonicTicker;
import br.com.esl.etl.v2.plataforma.resiliencia.ResilienceCancelledException;
import br.com.esl.etl.v2.plataforma.resiliencia.ResilienceSleeper;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

class HttpGraphQlGatewayTest {

    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-08-31T12:00:00Z"), ZoneOffset.UTC);

    @Test
    void malformedUtf8IsRejectedWithoutCauseOrRetryAndReleasesItsPermit() throws Exception {
        try (var server = new ServerSocket(0, 1, java.net.InetAddress.getLoopbackAddress())) {
            server.setSoTimeout(3000);
            final ExecutorService worker = Executors.newSingleThreadExecutor();
            try {
                final var reply =
                        worker.submit(
                                () -> {
                                    try (var socket = server.accept()) {
                                        readRequest(socket.getInputStream());
                                        final var output = socket.getOutputStream();
                                        output.write(
                                                ("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\n"
                                                                + "Content-Length: 2\r\nConnection: close\r\n\r\n")
                                                        .getBytes(StandardCharsets.US_ASCII));
                                        output.write(new byte[] {(byte) 0xc3, 0x28});
                                        output.flush();
                                    }
                                    return null;
                                });
                final var runtime = runtime(server, policy(Duration.ofSeconds(1)), 3);

                final var failure =
                        assertThrows(
                                GraphQlResponseException.class,
                                () ->
                                        runtime.securedGateway()
                                                .fetch(
                                                        GraphQlTestSupport.request(
                                                                GraphQlReadOperation
                                                                        .USERS_SNAPSHOT)));

                reply.get(3, TimeUnit.SECONDS);
                assertEquals(GraphQlResponseException.Reason.INVALID_UTF8, failure.reason());
                assertNull(failure.getCause());
                assertEquals(0, failure.getSuppressed().length);
                assertEquals(1, runtime.cycle().sourceRequests());
                assertEquals(1, runtime.governor().availablePermits());
            } finally {
                worker.shutdownNow();
                assertTrue(worker.awaitTermination(3, TimeUnit.SECONDS));
            }
        }
    }

    @Test
    void observesLoopbackRetryAndFailureBeforeAnyCompletedPage() throws Exception {
        try (ServerSocket server =
                new ServerSocket(0, 1, java.net.InetAddress.getLoopbackAddress())) {
            final var calls = new AtomicInteger();
            final var attempts = new AtomicInteger();
            final var policy = policy(Duration.ofSeconds(1));
            final var runtime =
                    runtime(
                            server,
                            policy,
                            2,
                            governor(policy),
                            CancellationToken.none(),
                            operation -> {
                                assertEquals(GraphQlReadOperation.USERS_SNAPSHOT, operation);
                                attempts.incrementAndGet();
                            });
            final ExecutorService executor = Executors.newSingleThreadExecutor();
            try {
                final var task =
                        executor.submit(
                                () ->
                                        serveCountingResponses(
                                                server,
                                                calls,
                                                List.of(
                                                        jsonResponse(503, ""),
                                                        jsonResponse(
                                                                200,
                                                                "{\"errors\":[{\"message\":\"synthetic\"}],\"data\":null}"))));
                assertThrows(
                        GraphQlResponseException.class,
                        () ->
                                runtime.securedGateway()
                                        .fetch(
                                                GraphQlTestSupport.request(
                                                        GraphQlReadOperation.USERS_SNAPSHOT)));
                task.get(3, TimeUnit.SECONDS);
                assertEquals(2, calls.get());
                assertEquals(calls.get(), attempts.get());
                assertEquals(1, runtime.governor().availablePermits());
            } finally {
                executor.shutdownNow();
                assertTrue(executor.awaitTermination(3, TimeUnit.SECONDS));
            }
        }
    }

    @Test
    void sendsOnlyPostWithStaticDocumentTypedVariablesAndBoundContract() throws Exception {
        try (ServerSocket server = new ServerSocket(0)) {
            final AtomicReference<String> requestCapture = new AtomicReference<>();
            final ExecutorService executor = Executors.newSingleThreadExecutor();
            try {
                final Future<?> serverTask =
                        executor.submit(
                                () ->
                                        serveResponses(
                                                server,
                                                requestCapture::set,
                                                List.of(jsonResponse(200, validBody()))));
                final Runtime runtime = runtime(server, policy(Duration.ofSeconds(1)), 1);
                final GraphQlPageResponse page =
                        runtime.securedGateway()
                                .fetch(
                                        GraphQlTestSupport.request(
                                                GraphQlReadOperation.USERS_SNAPSHOT));

                serverTask.get(3, TimeUnit.SECONDS);
                final String observed = requestCapture.get();
                assertTrue(observed.startsWith("POST /graphql HTTP/1.1"));
                assertTrue(
                        observed.contains(
                                "Authorization: " + String.join(" ", "Bearer", "synthetic-token")));
                assertTrue(observed.contains("\"operationName\":\"V2UsersSnapshot\""));
                assertTrue(observed.contains("\"enabled\":true"));
                assertTrue(observed.contains("\"first\":20"));
                assertTrue(observed.contains("\"after\":null"));
                assertFalse(observed.contains("mutation"));
                assertEquals(1, page.nodeCount());
                assertTrue(page.contractObservation().isPresent());
                assertEquals(1, runtime.cycle().sourceRequests());
            } finally {
                executor.shutdownNow();
                assertTrue(executor.awaitTermination(3, TimeUnit.SECONDS));
            }
        }
    }

    @Test
    void retriesOnlyTransientStatusAndNeverRetriesGraphQlOrPermanentErrors() throws Exception {
        try (ServerSocket retryServer = new ServerSocket(0)) {
            final AtomicInteger calls = new AtomicInteger();
            final ExecutorService executor = Executors.newSingleThreadExecutor();
            try {
                final Future<?> task =
                        executor.submit(
                                () ->
                                        serveCountingResponses(
                                                retryServer,
                                                calls,
                                                List.of(
                                                        jsonResponse(503, ""),
                                                        jsonResponse(200, validBody()))));
                final Runtime runtime = runtime(retryServer, policy(Duration.ofSeconds(1)), 2);
                assertEquals(
                        1,
                        runtime.securedGateway()
                                .fetch(
                                        GraphQlTestSupport.request(
                                                GraphQlReadOperation.USERS_SNAPSHOT))
                                .nodeCount());
                task.get(3, TimeUnit.SECONDS);
                assertEquals(2, calls.get());
                assertEquals(2, runtime.cycle().sourceRequests());
            } finally {
                executor.shutdownNow();
                assertTrue(executor.awaitTermination(3, TimeUnit.SECONDS));
            }
        }

        assertSingleAttemptFailure(
                jsonResponse(400, "ignored"),
                GraphQlResponseException.class,
                GraphQlResponseException.Reason.HTTP_STATUS);
        assertSingleAttemptFailure(
                jsonResponse(202, validBody()),
                GraphQlResponseException.class,
                GraphQlResponseException.Reason.HTTP_STATUS);
        assertSingleAttemptFailure(
                jsonResponse(206, validBody()),
                GraphQlResponseException.class,
                GraphQlResponseException.Reason.HTTP_STATUS);
        assertSingleAttemptFailure(
                jsonResponse(
                        200, "{\"errors\":[{\"message\":\"synthetic-sensitive\"}],\"data\":null}"),
                GraphQlResponseException.class,
                GraphQlResponseException.Reason.GRAPHQL_ERRORS);
        assertSingleAttemptFailure(
                new SyntheticResponse(200, "text/plain", validBody()),
                GraphQlResponseException.class,
                GraphQlResponseException.Reason.INVALID_CONTENT_TYPE);
        assertSingleAttemptFailure(
                new SyntheticResponse(200, "application/jsonp", validBody()),
                GraphQlResponseException.class,
                GraphQlResponseException.Reason.INVALID_CONTENT_TYPE);
    }

    @Test
    void requestDeadlineCancelsTheExchangeAndReleasesTheSharedPermit() throws Exception {
        try (ServerSocket server = new ServerSocket(0)) {
            server.setSoTimeout(3_000);
            final CountDownLatch requestRead = new CountDownLatch(1);
            final ExecutorService serverExecutor = Executors.newSingleThreadExecutor();
            final ExecutorService clientExecutor = Executors.newSingleThreadExecutor();
            try {
                final Future<Integer> connectionsClosed =
                        serverExecutor.submit(() -> serveHanging(server, requestRead, 2));
                final EslResiliencePolicy shortPolicy = policy(Duration.ofMillis(150));
                final Runtime runtime = runtime(server, shortPolicy, 2);
                final Future<?> call =
                        clientExecutor.submit(
                                () ->
                                        runtime.securedGateway()
                                                .fetch(
                                                        GraphQlTestSupport.request(
                                                                GraphQlReadOperation
                                                                        .USERS_SNAPSHOT)));
                assertTrue(requestRead.await(2, TimeUnit.SECONDS));
                final ExecutionException failure =
                        assertThrows(ExecutionException.class, () -> call.get(3, TimeUnit.SECONDS));
                assertTrue(failure.getCause() instanceof GraphQlUnavailableException);
                final GraphQlUnavailableException unavailable =
                        (GraphQlUnavailableException) failure.getCause();
                assertEquals(
                        GraphQlUnavailableException.Reason.REQUEST_TIMEOUT, unavailable.reason());
                assertEquals(2, connectionsClosed.get(3, TimeUnit.SECONDS));
                assertEquals(1, runtime.governor().availablePermits());
                assertEquals(2, runtime.cycle().sourceRequests());
            } finally {
                clientExecutor.shutdownNow();
                serverExecutor.shutdownNow();
                assertTrue(clientExecutor.awaitTermination(3, TimeUnit.SECONDS));
                assertTrue(serverExecutor.awaitTermination(3, TimeUnit.SECONDS));
            }
        }
    }

    @Test
    void finalRateLimitResponseEmbargoesTheNextRequestWithoutRealWaiting() throws Exception {
        try (ServerSocket server = new ServerSocket(0)) {
            final AtomicInteger calls = new AtomicInteger();
            final ExecutorService executor = Executors.newSingleThreadExecutor();
            try {
                final Future<?> task =
                        executor.submit(
                                () ->
                                        serveCountingResponses(
                                                server,
                                                calls,
                                                List.of(
                                                        new SyntheticResponse(
                                                                429, "application/json", "", "1"),
                                                        jsonResponse(200, validBody()))));
                final MutableTicker ticker = new MutableTicker();
                final EslResiliencePolicy policy = policy(Duration.ofSeconds(1));
                final EslRequestGovernor governor =
                        new EslRequestGovernor(policy, ticker, ticker::advance);
                final Runtime runtime = runtime(server, policy, 1, governor);

                final GraphQlUnavailableException limited =
                        assertThrows(
                                GraphQlUnavailableException.class,
                                () ->
                                        runtime.securedGateway()
                                                .fetch(
                                                        GraphQlTestSupport.request(
                                                                GraphQlReadOperation
                                                                        .USERS_SNAPSHOT)));
                assertEquals(GraphQlUnavailableException.Reason.HTTP_TRANSIENT, limited.reason());
                final GraphQlTestSupport.Fixture nextFixture =
                        GraphQlTestSupport.fixture(
                                GraphQlReadOperation.USERS_SNAPSHOT,
                                GraphQlTestSupport.usersPage(
                                        false, null, "{\"id\":930001,\"name\":\"Synthetic\"}"),
                                runtime.factory().runtimeConfigurationFingerprint());
                final GraphQlGateway nextGateway =
                        runtime.factory()
                                .forOperation(
                                        runtime.cycle(),
                                        GraphQlReadOperation.USERS_SNAPSHOT,
                                        nextFixture.configuration(),
                                        nextFixture.guard(),
                                        runtime.cancellationToken());
                assertEquals(
                        1,
                        nextGateway
                                .fetch(
                                        GraphQlTestSupport.request(
                                                GraphQlReadOperation.USERS_SNAPSHOT))
                                .nodeCount());

                task.get(3, TimeUnit.SECONDS);
                assertEquals(2, calls.get());
                assertTrue(ticker.readNanos() >= Duration.ofSeconds(1).toNanos());
                assertEquals(2, runtime.cycle().sourceRequests());
            } finally {
                executor.shutdownNow();
                assertTrue(executor.awaitTermination(3, TimeUnit.SECONDS));
            }
        }
    }

    @Test
    void retriesAnIoFailureOnceAndThenSucceeds() throws Exception {
        try (ServerSocket server = new ServerSocket(0)) {
            final AtomicInteger calls = new AtomicInteger();
            final ExecutorService executor = Executors.newSingleThreadExecutor();
            try {
                final Future<?> task =
                        executor.submit(() -> serveDisconnectThenResponse(server, calls));
                final Runtime runtime = runtime(server, policy(Duration.ofSeconds(1)), 2);

                assertEquals(
                        1,
                        runtime.securedGateway()
                                .fetch(
                                        GraphQlTestSupport.request(
                                                GraphQlReadOperation.USERS_SNAPSHOT))
                                .nodeCount());

                task.get(3, TimeUnit.SECONDS);
                assertEquals(2, calls.get());
                assertEquals(2, runtime.cycle().sourceRequests());
            } finally {
                executor.shutdownNow();
                assertTrue(executor.awaitTermination(3, TimeUnit.SECONDS));
            }
        }
    }

    @Test
    void rejectsRetryAfterAboveTheSharedCapWithoutRetrying() throws Exception {
        try (ServerSocket server = new ServerSocket(0)) {
            final AtomicInteger calls = new AtomicInteger();
            final ExecutorService executor = Executors.newSingleThreadExecutor();
            try {
                final Future<?> task =
                        executor.submit(
                                () ->
                                        serveCountingResponses(
                                                server,
                                                calls,
                                                List.of(
                                                        new SyntheticResponse(
                                                                429,
                                                                "application/json",
                                                                "",
                                                                "2"))));
                final Runtime runtime = runtime(server, policy(Duration.ofSeconds(1)), 2);

                assertThrows(
                        GraphQlRetryAfterLimitExceededException.class,
                        () ->
                                runtime.securedGateway()
                                        .fetch(
                                                GraphQlTestSupport.request(
                                                        GraphQlReadOperation.USERS_SNAPSHOT)));

                task.get(3, TimeUnit.SECONDS);
                assertEquals(1, calls.get());
                assertEquals(1, runtime.cycle().sourceRequests());
                assertEquals(1, runtime.governor().availablePermits());
            } finally {
                executor.shutdownNow();
                assertTrue(executor.awaitTermination(3, TimeUnit.SECONDS));
            }
        }
    }

    @Test
    void inFlightCancellationClosesTheExchangeReleasesPermitAndDoesNotRetry() throws Exception {
        try (ServerSocket server = new ServerSocket(0)) {
            server.setSoTimeout(3_000);
            final CountDownLatch requestRead = new CountDownLatch(1);
            final ExecutorService serverExecutor = Executors.newSingleThreadExecutor();
            final ExecutorService clientExecutor = Executors.newSingleThreadExecutor();
            try {
                final Future<Integer> connectionsClosed =
                        serverExecutor.submit(() -> serveHanging(server, requestRead, 1));
                final CancellationSignal cancellation = new CancellationSignal();
                final EslResiliencePolicy policy = policy(Duration.ofSeconds(2));
                final Runtime runtime = runtime(server, policy, 3, governor(policy), cancellation);
                final Future<?> call =
                        clientExecutor.submit(
                                () ->
                                        runtime.securedGateway()
                                                .fetch(
                                                        GraphQlTestSupport.request(
                                                                GraphQlReadOperation
                                                                        .USERS_SNAPSHOT)));

                assertTrue(requestRead.await(2, TimeUnit.SECONDS));
                assertTrue(cancellation.cancel());
                final ExecutionException failure =
                        assertThrows(ExecutionException.class, () -> call.get(3, TimeUnit.SECONDS));
                assertTrue(failure.getCause() instanceof ResilienceCancelledException);
                assertEquals(1, connectionsClosed.get(3, TimeUnit.SECONDS));
                assertEquals(1, runtime.cycle().sourceRequests());
                assertEquals(1, runtime.governor().availablePermits());
            } finally {
                clientExecutor.shutdownNow();
                serverExecutor.shutdownNow();
                assertTrue(clientExecutor.awaitTermination(3, TimeUnit.SECONDS));
                assertTrue(serverExecutor.awaitTermination(3, TimeUnit.SECONDS));
            }
        }
    }

    @Test
    void factoryRejectsObservationFromAnotherRuntimeBeforeOpeningIo() throws Exception {
        try (ServerSocket server = new ServerSocket(0)) {
            final EslResiliencePolicy policy = policy(Duration.ofSeconds(1));
            final GraphQlSourceConfiguration source = source(server, policy, 1);
            final EslRequestGovernor governor = governor(policy);
            final GraphQlHttpGatewayFactory factory =
                    new GraphQlHttpGatewayFactory(
                            source, key -> "synthetic-token", governor, CLOCK);
            final GraphQlTestSupport.Fixture unrelated = GraphQlTestSupport.usersFixture();
            final CancellationToken cancellation = CancellationToken.none();

            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            factory.forOperation(
                                    governor.beginCycle(cancellation),
                                    GraphQlReadOperation.USERS_SNAPSHOT,
                                    unrelated.configuration(),
                                    unrelated.guard(),
                                    cancellation));
            assertEquals(1, governor.availablePermits());
        }
    }

    @Test
    void factoryRejectsForeignGovernorPolicyAndCancellationBeforeIo() throws Exception {
        try (ServerSocket server = new ServerSocket(0)) {
            final EslResiliencePolicy policy = policy(Duration.ofSeconds(1));
            final EslRequestGovernor expectedGovernor = governor(policy);
            final GraphQlSourceConfiguration source = source(server, policy, 1);
            final GraphQlHttpGatewayFactory factory =
                    new GraphQlHttpGatewayFactory(
                            source, key -> "synthetic-token", expectedGovernor, CLOCK);
            final GraphQlTestSupport.Fixture fixture =
                    GraphQlTestSupport.fixture(
                            GraphQlReadOperation.USERS_SNAPSHOT,
                            GraphQlTestSupport.usersPage(
                                    false, null, "{\"id\":930001,\"name\":\"Synthetic\"}"),
                            factory.runtimeConfigurationFingerprint());
            final CancellationToken cancellation = CancellationToken.none();
            final EslRequestGovernor foreignGovernor = governor(policy);
            final AtomicInteger rejectedSecretReads = new AtomicInteger();
            final EslRequestGovernor.Cycle foreignCycle = foreignGovernor.beginCycle(cancellation);
            final EslRequestGovernor.Cycle expectedCycle =
                    expectedGovernor.beginCycle(cancellation);

            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            factory.forOperation(
                                    foreignCycle,
                                    GraphQlReadOperation.USERS_SNAPSHOT,
                                    fixture.configuration(),
                                    fixture.guard(),
                                    cancellation));
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            factory.forOperation(
                                    expectedCycle,
                                    GraphQlReadOperation.USERS_SNAPSHOT,
                                    fixture.configuration(),
                                    fixture.guard(),
                                    new CancellationSignal()));
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            new GraphQlHttpGatewayFactory(
                                    source,
                                    key -> {
                                        rejectedSecretReads.incrementAndGet();
                                        return "synthetic-token";
                                    },
                                    governor(policy(Duration.ofMillis(500))),
                                    CLOCK));
            assertEquals(1, expectedGovernor.availablePermits());
            assertEquals(1, foreignGovernor.availablePermits());
            assertEquals(0, expectedCycle.sourceRequests());
            assertEquals(0, foreignCycle.sourceRequests());
            assertEquals(0, rejectedSecretReads.get());
        }
    }

    private static void assertSingleAttemptFailure(
            final SyntheticResponse response,
            final Class<? extends RuntimeException> expectedType,
            final GraphQlResponseException.Reason expectedReason)
            throws Exception {
        try (ServerSocket server = new ServerSocket(0)) {
            final AtomicInteger calls = new AtomicInteger();
            final ExecutorService executor = Executors.newSingleThreadExecutor();
            try {
                final Future<?> task =
                        executor.submit(
                                () -> serveCountingResponses(server, calls, List.of(response)));
                final Runtime runtime = runtime(server, policy(Duration.ofSeconds(1)), 3);
                final RuntimeException failure =
                        assertThrows(
                                expectedType,
                                () ->
                                        runtime.securedGateway()
                                                .fetch(
                                                        GraphQlTestSupport.request(
                                                                GraphQlReadOperation
                                                                        .USERS_SNAPSHOT)));
                task.get(3, TimeUnit.SECONDS);
                assertEquals(expectedReason, ((GraphQlResponseException) failure).reason());
                assertFalse(failure.getMessage().contains("synthetic-sensitive"));
                assertEquals(1, calls.get());
                assertEquals(1, runtime.cycle().sourceRequests());
            } finally {
                executor.shutdownNow();
                assertTrue(executor.awaitTermination(3, TimeUnit.SECONDS));
            }
        }
    }

    private static Runtime runtime(
            final ServerSocket server,
            final EslResiliencePolicy policy,
            final int maximumAttempts) {
        return runtime(server, policy, maximumAttempts, governor(policy), CancellationToken.none());
    }

    private static Runtime runtime(
            final ServerSocket server,
            final EslResiliencePolicy policy,
            final int maximumAttempts,
            final EslRequestGovernor governor) {
        return runtime(server, policy, maximumAttempts, governor, CancellationToken.none());
    }

    private static Runtime runtime(
            final ServerSocket server,
            final EslResiliencePolicy policy,
            final int maximumAttempts,
            final EslRequestGovernor governor,
            final CancellationToken cancellationToken) {
        return runtime(
                server,
                policy,
                maximumAttempts,
                governor,
                cancellationToken,
                GraphQlHttpAttemptObserver.noop());
    }

    private static Runtime runtime(
            final ServerSocket server,
            final EslResiliencePolicy policy,
            final int maximumAttempts,
            final EslRequestGovernor governor,
            final CancellationToken cancellationToken,
            final GraphQlHttpAttemptObserver attempts) {
        final GraphQlSourceConfiguration source = source(server, policy, maximumAttempts);
        final GraphQlHttpGatewayFactory factory =
                new GraphQlHttpGatewayFactory(source, key -> "synthetic-token", governor, CLOCK);
        final GraphQlTestSupport.Fixture fixture =
                GraphQlTestSupport.fixture(
                        GraphQlReadOperation.USERS_SNAPSHOT,
                        GraphQlTestSupport.usersPage(
                                false, null, "{\"id\":930001,\"name\":\"Synthetic\"}"),
                        factory.runtimeConfigurationFingerprint());
        final EslRequestGovernor.Cycle cycle = governor.beginCycle(cancellationToken);
        final GraphQlGateway secured =
                factory.forOperation(
                        cycle,
                        GraphQlReadOperation.USERS_SNAPSHOT,
                        fixture.configuration(),
                        fixture.guard(),
                        cancellationToken,
                        attempts);
        return new Runtime(governor, cycle, factory, secured, cancellationToken);
    }

    private static GraphQlSourceConfiguration source(
            final ServerSocket server,
            final EslResiliencePolicy policy,
            final int maximumAttempts) {
        return new GraphQlSourceConfiguration(
                "synthetic-source",
                "synthetic-tenant",
                new GraphQlClientSettings(
                        URI.create("http://127.0.0.1:" + server.getLocalPort() + "/graphql"),
                        Duration.ofSeconds(2),
                        new GraphQlRetryPolicy(
                                maximumAttempts, Duration.ZERO, Duration.ofSeconds(1)),
                        16_384),
                policy);
    }

    private static EslRequestGovernor governor(final EslResiliencePolicy policy) {
        return new EslRequestGovernor(
                policy, MonotonicTicker.systemTicker(), ResilienceSleeper.threadSleeper());
    }

    private static EslResiliencePolicy policy(final Duration requestTimeout) {
        return new EslResiliencePolicy(
                Duration.ZERO,
                1,
                20,
                20,
                requestTimeout,
                Duration.ofSeconds(3),
                Duration.ofSeconds(5),
                Duration.ofSeconds(1),
                0,
                3,
                Duration.ofSeconds(1));
    }

    private static SyntheticResponse jsonResponse(final int status, final String body) {
        return new SyntheticResponse(status, "application/json", body);
    }

    private static String validBody() {
        return GraphQlTestSupport.usersEnvelope(
                "[{\"node\":{\"id\":930001,\"name\":\"Synthetic\"}}]", false, "null");
    }

    private static void serveResponses(
            final ServerSocket server,
            final Consumer<String> requestCapture,
            final List<SyntheticResponse> responses) {
        try {
            for (final SyntheticResponse response : responses) {
                try (Socket socket = server.accept()) {
                    requestCapture.accept(readRequest(socket.getInputStream()));
                    writeResponse(socket.getOutputStream(), response);
                }
            }
        } catch (final IOException exception) {
            throw new IllegalStateException("Falha no servidor GraphQL sintético.", exception);
        }
    }

    private static void serveCountingResponses(
            final ServerSocket server,
            final AtomicInteger calls,
            final List<SyntheticResponse> responses) {
        serveResponses(server, ignored -> calls.incrementAndGet(), responses);
    }

    private static int serveHanging(
            final ServerSocket server,
            final CountDownLatch requestRead,
            final int expectedConnections) {
        int closedConnections = 0;
        try {
            for (int index = 0; index < expectedConnections; index++) {
                try (Socket socket = server.accept()) {
                    socket.setSoTimeout(2_000);
                    readRequest(socket.getInputStream());
                    requestRead.countDown();
                    try {
                        if (socket.getInputStream().read() < 0) {
                            closedConnections++;
                        }
                    } catch (final SocketException exception) {
                        closedConnections++;
                    }
                }
            }
            return closedConnections;
        } catch (final IOException exception) {
            throw new IllegalStateException("Falha no servidor GraphQL pendente.", exception);
        } finally {
            requestRead.countDown();
        }
    }

    private static void serveDisconnectThenResponse(
            final ServerSocket server, final AtomicInteger calls) {
        try {
            try (Socket first = server.accept()) {
                readRequest(first.getInputStream());
                calls.incrementAndGet();
            }
            try (Socket second = server.accept()) {
                readRequest(second.getInputStream());
                calls.incrementAndGet();
                writeResponse(second.getOutputStream(), jsonResponse(200, validBody()));
            }
        } catch (final IOException exception) {
            throw new IllegalStateException("Falha no servidor GraphQL sintético.", exception);
        }
    }

    private static String readRequest(final InputStream input) throws IOException {
        final ByteArrayOutputStream headers = new ByteArrayOutputStream();
        int state = 0;
        while (state < 4) {
            final int value = input.read();
            if (value < 0) {
                throw new IOException("Request sintética terminou antes dos headers.");
            }
            headers.write(value);
            state =
                    switch (state) {
                        case 0 -> value == '\r' ? 1 : 0;
                        case 1 -> value == '\n' ? 2 : 0;
                        case 2 -> value == '\r' ? 3 : 0;
                        case 3 -> value == '\n' ? 4 : 0;
                        default -> state;
                    };
        }
        final String headerText = headers.toString(StandardCharsets.ISO_8859_1);
        int contentLength = 0;
        for (final String line : headerText.split("\\r\\n")) {
            if (line.toLowerCase(java.util.Locale.ROOT).startsWith("content-length:")) {
                contentLength = Integer.parseInt(line.substring(line.indexOf(':') + 1).trim());
            }
        }
        final byte[] body = input.readNBytes(contentLength);
        if (body.length != contentLength) {
            throw new IOException("Request sintética terminou antes do body.");
        }
        return headerText + new String(body, StandardCharsets.UTF_8);
    }

    private static void writeResponse(final OutputStream output, final SyntheticResponse response)
            throws IOException {
        final byte[] body = response.body().getBytes(StandardCharsets.UTF_8);
        final String statusText = response.status() == 200 ? "OK" : "Synthetic";
        final String headers =
                "HTTP/1.1 "
                        + response.status()
                        + ' '
                        + statusText
                        + "\r\nContent-Type: "
                        + response.contentType()
                        + "\r\nContent-Length: "
                        + body.length
                        + (response.retryAfter() == null
                                ? ""
                                : "\r\nRetry-After: " + response.retryAfter())
                        + "\r\nConnection: close\r\n\r\n";
        output.write(headers.getBytes(StandardCharsets.ISO_8859_1));
        output.write(body);
        output.flush();
    }

    private record SyntheticResponse(
            int status, String contentType, String body, String retryAfter) {

        private SyntheticResponse(final int status, final String contentType, final String body) {
            this(status, contentType, body, null);
        }
    }

    private static final class MutableTicker implements MonotonicTicker {

        private long nowNanos;

        @Override
        public long readNanos() {
            return nowNanos;
        }

        private void advance(final Duration duration) {
            nowNanos = Math.addExact(nowNanos, duration.toNanos());
        }
    }

    private record Runtime(
            EslRequestGovernor governor,
            EslRequestGovernor.Cycle cycle,
            GraphQlHttpGatewayFactory factory,
            GraphQlGateway securedGateway,
            CancellationToken cancellationToken) {}
}
