package br.com.esl.etl.v2.plataforma.fonte.dataexport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.plataforma.configuracao.DataExportProperties;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationSignal;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken;
import br.com.esl.etl.v2.plataforma.resiliencia.EslRequestGovernor;
import br.com.esl.etl.v2.plataforma.resiliencia.EslResiliencePolicy;
import br.com.esl.etl.v2.plataforma.resiliencia.EslWorkload;
import br.com.esl.etl.v2.plataforma.resiliencia.MonotonicTicker;
import br.com.esl.etl.v2.plataforma.resiliencia.ResilienceCancelledException;
import br.com.esl.etl.v2.plataforma.resiliencia.ResilienceSleeper;
import br.com.esl.etl.v2.plataforma.resiliencia.ResilienceTimeoutException;
import br.com.esl.etl.v2.plataforma.resiliencia.ResilienceTimeoutScope;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class DataExportHttpResilienceIntegrationTest {

    @Test
    void terminal429EmbargoesAnotherTemplateWithoutRetryStorm() throws Exception {
        try (ServerSocket server = new ServerSocket(0)) {
            server.setSoTimeout(5_000);
            final CountDownLatch firstAccepted = new CountDownLatch(1);
            final CountDownLatch releaseRateLimit = new CountDownLatch(1);
            final AtomicLong rateLimitSentAt = new AtomicLong();
            final AtomicLong secondAcceptedAt = new AtomicLong();
            final ExecutorService serverExecutor = Executors.newSingleThreadExecutor();
            final ExecutorService clients = Executors.newFixedThreadPool(2);
            try {
                final Future<?> serverTask =
                        serverExecutor.submit(
                                () ->
                                        serveRateLimitThenSuccess(
                                                server,
                                                firstAccepted,
                                                releaseRateLimit,
                                                rateLimitSentAt,
                                                secondAcceptedAt));
                final DataExportProperties properties = properties(server, Duration.ofSeconds(2));
                final EslResiliencePolicy policy = policy(Duration.ofSeconds(2));
                final EslRequestGovernor governor =
                        new EslRequestGovernor(
                                policy,
                                MonotonicTicker.systemTicker(),
                                ResilienceSleeper.threadSleeper());
                final EslRequestGovernor.Cycle cycle =
                        governor.beginCycle(CancellationToken.none());
                final DataExportHttpGatewayFactory factory =
                        new DataExportHttpGatewayFactory(properties, policy, Clock.systemUTC());
                final DataExportGateway coletas =
                        factory.forWorkload(cycle, EslWorkload.COLETAS).dataGateway();
                final DataExportGateway fretes =
                        factory.forWorkload(cycle, EslWorkload.FRETES).dataGateway();

                final Future<?> first =
                        clients.submit(() -> coletas.fetch(request(DataExportTemplate.COLETAS)));
                assertTrue(firstAccepted.await(2, TimeUnit.SECONDS));
                final Future<DataExportPageResponse> second =
                        clients.submit(() -> fretes.fetch(request(DataExportTemplate.FRETES)));
                releaseRateLimit.countDown();

                final ExecutionException rateLimited =
                        assertThrows(
                                ExecutionException.class, () -> first.get(3, TimeUnit.SECONDS));
                assertTrue(rateLimited.getCause() instanceof DataExportUnavailableException);
                assertEquals(0, second.get(4, TimeUnit.SECONDS).records().size());
                serverTask.get(2, TimeUnit.SECONDS);
                assertTrue(
                        secondAcceptedAt.get() - rateLimitSentAt.get()
                                >= Duration.ofMillis(800).toNanos());
                assertEquals(2, cycle.sourceRequests());
                assertEquals(1, governor.availablePermits());
            } finally {
                releaseRateLimit.countDown();
                clients.shutdownNow();
                serverExecutor.shutdownNow();
                assertTrue(clients.awaitTermination(3, TimeUnit.SECONDS));
                assertTrue(serverExecutor.awaitTermination(3, TimeUnit.SECONDS));
            }
        }
    }

    @Test
    void cancellationClosesHangingBodyReleasesPermitAndAllowsNextRequest() throws Exception {
        try (ServerSocket server = new ServerSocket(0)) {
            server.setSoTimeout(5_000);
            final CountDownLatch bodyStarted = new CountDownLatch(1);
            final ExecutorService serverExecutor = Executors.newSingleThreadExecutor();
            final ExecutorService clientExecutor = Executors.newSingleThreadExecutor();
            try {
                final Future<Boolean> connectionClosed =
                        serverExecutor.submit(() -> serveHangingThenSuccess(server, bodyStarted));
                final DataExportProperties properties = properties(server, Duration.ZERO);
                final EslResiliencePolicy policy = policy(Duration.ofSeconds(1));
                final EslRequestGovernor governor =
                        new EslRequestGovernor(
                                policy,
                                MonotonicTicker.systemTicker(),
                                ResilienceSleeper.threadSleeper());
                final CancellationSignal signal = new CancellationSignal();
                final DataExportHttpGatewayFactory factory =
                        new DataExportHttpGatewayFactory(properties, policy, Clock.systemUTC());
                final DataExportGateway firstGateway =
                        factory.forWorkload(governor.beginCycle(signal), EslWorkload.COLETAS)
                                .dataGateway();
                final Future<?> hanging =
                        clientExecutor.submit(
                                () -> firstGateway.fetch(request(DataExportTemplate.COLETAS)));

                assertTrue(bodyStarted.await(2, TimeUnit.SECONDS));
                signal.cancel();
                final ExecutionException cancelled =
                        assertThrows(
                                ExecutionException.class, () -> hanging.get(3, TimeUnit.SECONDS));
                assertTrue(cancelled.getCause() instanceof ResilienceCancelledException);
                assertEquals(1, governor.availablePermits());

                final DataExportGateway secondGateway =
                        factory.forWorkload(
                                        governor.beginCycle(CancellationToken.none()),
                                        EslWorkload.FRETES)
                                .dataGateway();
                assertEquals(
                        0,
                        secondGateway.fetch(request(DataExportTemplate.FRETES)).records().size());
                assertTrue(connectionClosed.get(3, TimeUnit.SECONDS));
                assertEquals(1, governor.availablePermits());
            } finally {
                clientExecutor.shutdownNow();
                serverExecutor.shutdownNow();
                assertTrue(clientExecutor.awaitTermination(3, TimeUnit.SECONDS));
                assertTrue(serverExecutor.awaitTermination(3, TimeUnit.SECONDS));
            }
        }
    }

    @Test
    void requestDeadlineCancelsHangingExchangeAndReleasesPermit() throws Exception {
        try (ServerSocket server = new ServerSocket(0)) {
            server.setSoTimeout(5_000);
            final CountDownLatch bodyStarted = new CountDownLatch(1);
            final ExecutorService serverExecutor = Executors.newSingleThreadExecutor();
            final ExecutorService clientExecutor = Executors.newSingleThreadExecutor();
            try {
                final Future<Boolean> connectionClosed =
                        serverExecutor.submit(() -> serveOneHangingResponse(server, bodyStarted));
                final DataExportProperties properties = properties(server, Duration.ZERO);
                final EslResiliencePolicy base = policy(Duration.ofSeconds(1));
                final EslResiliencePolicy shortRequest =
                        new EslResiliencePolicy(
                                base.minimumRequestInterval(),
                                base.maxInFlight(),
                                base.maxRequestsPerCycle(),
                                base.maxRequestsPerWorkload(),
                                Duration.ofSeconds(1),
                                base.stepTimeout(),
                                base.cycleTimeout(),
                                base.maxRetryAfter(),
                                base.maxRepartitions(),
                                base.circuitFailureThreshold(),
                                base.circuitCooldown());
                final EslRequestGovernor governor =
                        new EslRequestGovernor(
                                shortRequest,
                                MonotonicTicker.systemTicker(),
                                ResilienceSleeper.threadSleeper());
                final DataExportHttpAttemptGovernor attemptGovernor =
                        new EslDataExportHttpAttemptGovernor(
                                governor.beginCycle(CancellationToken.none())
                                        .beginWorkload(EslWorkload.COLETAS));
                final DataExportHttpExecutor executor =
                        new DataExportHttpExecutor(
                                HttpClient.newHttpClient(),
                                properties,
                                DataExportHttpAttemptObserver.noop(),
                                attemptGovernor,
                                new DataExportRetrySchedule(
                                        properties.retryPolicy(),
                                        Clock.systemUTC(),
                                        () -> 0.0d,
                                        attemptGovernor.maximumRetryAfter()));

                final Future<DataExportHttpResponse> call =
                        clientExecutor.submit(
                                () ->
                                        executor.executeWithRetry(
                                                DataExportTemplate.COLETAS.templateId(),
                                                "deadline-test",
                                                ignored ->
                                                        HttpRequest.newBuilder(
                                                                        properties.endpointFor(
                                                                                DataExportTemplate
                                                                                        .COLETAS
                                                                                        .templateId()))
                                                                .timeout(Duration.ofSeconds(5))
                                                                .GET()
                                                                .build()));
                assertTrue(bodyStarted.await(2, TimeUnit.SECONDS));
                final ExecutionException failure =
                        assertThrows(ExecutionException.class, () -> call.get(3, TimeUnit.SECONDS));
                assertTrue(failure.getCause() instanceof ResilienceTimeoutException);
                final ResilienceTimeoutException timeout =
                        (ResilienceTimeoutException) failure.getCause();
                assertEquals(ResilienceTimeoutScope.REQUEST, timeout.scope());
                assertTrue(connectionClosed.get(3, TimeUnit.SECONDS));
                assertEquals(1, governor.availablePermits());
            } finally {
                clientExecutor.shutdownNow();
                serverExecutor.shutdownNow();
                assertTrue(clientExecutor.awaitTermination(3, TimeUnit.SECONDS));
                assertTrue(serverExecutor.awaitTermination(3, TimeUnit.SECONDS));
            }
        }
    }

    private static void serveRateLimitThenSuccess(
            final ServerSocket server,
            final CountDownLatch firstAccepted,
            final CountDownLatch releaseRateLimit,
            final AtomicLong rateLimitSentAt,
            final AtomicLong secondAcceptedAt) {
        try (Socket first = server.accept()) {
            readHeaders(first.getInputStream());
            firstAccepted.countDown();
            if (!releaseRateLimit.await(2, TimeUnit.SECONDS)) {
                throw new IllegalStateException("A resposta 429 sintética não foi liberada.");
            }
            rateLimitSentAt.set(System.nanoTime());
            write(
                    first,
                    "HTTP/1.1 429 Too Many Requests\r\n"
                            + "Retry-After: 1\r\n"
                            + "Content-Length: 0\r\n"
                            + "Connection: close\r\n\r\n");
        } catch (final IOException | InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Falha no servidor 429 sintético.", exception);
        }
        try (Socket second = server.accept()) {
            secondAcceptedAt.set(System.nanoTime());
            readHeaders(second.getInputStream());
            writeSuccess(second);
        } catch (final IOException exception) {
            throw new IllegalStateException("Falha na resposta sintética de sucesso.", exception);
        }
    }

    private static boolean serveHangingThenSuccess(
            final ServerSocket server, final CountDownLatch bodyStarted) {
        final boolean closed = serveOneHangingResponse(server, bodyStarted);
        try (Socket second = server.accept()) {
            readHeaders(second.getInputStream());
            writeSuccess(second);
            return closed;
        } catch (final IOException exception) {
            throw new IllegalStateException("Falha na segunda resposta sintética.", exception);
        }
    }

    private static boolean serveOneHangingResponse(
            final ServerSocket server, final CountDownLatch bodyStarted) {
        try (Socket socket = server.accept()) {
            readHeaders(socket.getInputStream());
            write(
                    socket,
                    "HTTP/1.1 200 OK\r\n"
                            + "Content-Type: application/json\r\n"
                            + "Transfer-Encoding: chunked\r\n\r\n"
                            + "B\r\n{\"data\":[]}\r\n");
            bodyStarted.countDown();
            socket.setSoTimeout(3_000);
            try {
                return socket.getInputStream().read() == -1;
            } catch (final SocketException exception) {
                return true;
            } catch (final SocketTimeoutException exception) {
                return false;
            }
        } catch (final IOException exception) {
            throw new IllegalStateException("Falha na resposta pendurada sintética.", exception);
        }
    }

    private static void writeSuccess(final Socket socket) throws IOException {
        final byte[] body = "{\"data\":[]}".getBytes(StandardCharsets.UTF_8);
        write(
                socket,
                "HTTP/1.1 200 OK\r\n"
                        + "Content-Type: application/json\r\n"
                        + "Content-Length: "
                        + body.length
                        + "\r\nConnection: close\r\n\r\n"
                        + new String(body, StandardCharsets.UTF_8));
    }

    private static void write(final Socket socket, final String response) throws IOException {
        final OutputStream output = socket.getOutputStream();
        output.write(response.getBytes(StandardCharsets.UTF_8));
        output.flush();
    }

    private static void readHeaders(final InputStream input) throws IOException {
        final ByteArrayOutputStream captured = new ByteArrayOutputStream();
        int current;
        while ((current = input.read()) != -1) {
            captured.write(current);
            final byte[] bytes = captured.toByteArray();
            final int length = bytes.length;
            if (length >= 4
                    && bytes[length - 4] == '\r'
                    && bytes[length - 3] == '\n'
                    && bytes[length - 2] == '\r'
                    && bytes[length - 1] == '\n') {
                return;
            }
            if (length > 16_384) {
                throw new IOException("Cabeçalhos sintéticos excederam o limite.");
            }
        }
        throw new IOException("Conexão encerrada antes dos cabeçalhos sintéticos.");
    }

    private static DataExportProperties properties(
            final ServerSocket server, final Duration maximumRetryDelay) {
        return new DataExportProperties(
                URI.create("http://127.0.0.1:" + server.getLocalPort()),
                "synthetic-token",
                ZoneId.of("America/Sao_Paulo"),
                Duration.ofSeconds(5),
                DataExportTransport.GET_WITH_QUERY,
                new DataExportRetryPolicy(1, Duration.ZERO, maximumRetryDelay),
                1_024L);
    }

    private static EslResiliencePolicy policy(final Duration maximumRetryAfter) {
        return new EslResiliencePolicy(
                Duration.ZERO,
                1,
                20,
                10,
                Duration.ofSeconds(2),
                Duration.ofSeconds(5),
                Duration.ofSeconds(10),
                maximumRetryAfter,
                2,
                3,
                Duration.ofSeconds(1));
    }

    private static DataExportPageRequest request(final DataExportTemplate template) {
        return new DataExportPageRequest(
                template,
                new BusinessDateRange(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 1)),
                Optional.empty(),
                1,
                10,
                template.defaultOrderBy());
    }
}
