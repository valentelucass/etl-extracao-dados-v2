package br.com.esl.etl.v2.contratos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.plataforma.configuracao.DataExportProperties;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportRetryPolicy;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportTransport;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class Contract4924DataExportProbeTest {

    private String previousGateValue;
    private String previousProfileMarkerValue;

    @BeforeEach
    void enableTheExplicitContractGateForTheLocalHttpServer() {
        previousGateValue = System.getProperty("contract.tests.enabled");
        previousProfileMarkerValue = System.getProperty("contract.tests.profile.active");
        System.setProperty("contract.tests.enabled", "true");
        System.setProperty("contract.tests.profile.active", "true");
    }

    @AfterEach
    void restoreTheExplicitContractGate() {
        restoreProperty("contract.tests.enabled", previousGateValue);
        restoreProperty("contract.tests.profile.active", previousProfileMarkerValue);
    }

    @Test
    void refusesTheAuxiliaryProbeBeforeOpeningASocketWhenTheGateIsAbsent() throws Exception {
        try (ServerSocket serverSocket = new ServerSocket(0)) {
            serverSocket.setSoTimeout(200);
            System.clearProperty("contract.tests.enabled");
            final Contract4924DataExportProbe probe = probe(serverSocket.getLocalPort(), 2);

            assertThrows(IllegalStateException.class, probe::fetchInfo);
            assertThrows(SocketTimeoutException.class, serverSocket::accept);
        }
    }

    @Test
    void observesInfoAndClosedWindowWithoutRetainingThePayload() throws Exception {
        try (ServerSocket serverSocket = new ServerSocket(0)) {
            serverSocket.setSoTimeout(5_000);
            final ExecutorService executor = Executors.newSingleThreadExecutor();
            try {
                final Future<List<String>> requests =
                        executor.submit(
                                () ->
                                        receiveResponses(
                                                serverSocket,
                                                List.of(infoResponse(), dataResponse()),
                                                List.of("200 OK", "200 OK")));
                final Contract4924DataExportProbe probe = probe(serverSocket.getLocalPort(), 2);

                final Contract4924TemplateInfo info = probe.fetchInfo();
                final Contract4924PageObservation page = probe.fetchClosedWindowSample();

                final List<String> captured = requests.get(5, TimeUnit.SECONDS);
                assertEquals(2, captured.size());
                assertTrue(
                        captured.get(0)
                                .startsWith("GET /api/analytics/reports/4924/info HTTP/1.1"));
                assertTrue(
                        captured.get(1)
                                .startsWith("GET /api/analytics/reports/4924/data HTTP/1.1"));
                assertTrue(captured.get(1).contains("\"closed_at\""));
                assertEquals(2, info.declaredFields().size());
                assertEquals(1, page.records().size());
                assertEquals(2, probe.callsMade());
                assertFalse(
                        page.toString().contains("35123456789012345678901234567890123456789012"));
                assertFalse(
                        page.asPayloadEvidence()
                                .toString()
                                .contains("35123456789012345678901234567890123456789012"));
            } finally {
                executor.shutdownNow();
            }
        }
    }

    @Test
    void doesNotFallbackWhenTheApprovedTransportIsRejected() throws Exception {
        try (ServerSocket serverSocket = new ServerSocket(0)) {
            serverSocket.setSoTimeout(5_000);
            final ExecutorService executor = Executors.newSingleThreadExecutor();
            try {
                final Future<List<String>> requests =
                        executor.submit(
                                () ->
                                        receiveResponses(
                                                serverSocket,
                                                List.of("{}"),
                                                List.of("405 Method Not Allowed")));
                final Contract4924DataExportProbe probe = probe(serverSocket.getLocalPort(), 2);

                assertThrows(ContractRemoteCallException.class, probe::fetchClosedWindowSample);
                assertEquals(1, requests.get(5, TimeUnit.SECONDS).size());
                assertEquals(1, probe.callsMade());
            } finally {
                executor.shutdownNow();
            }
        }
    }

    @Test
    void usesTheApprovedQueryTransportWithoutASyntheticRequestEnvelope() throws Exception {
        try (ServerSocket serverSocket = new ServerSocket(0)) {
            serverSocket.setSoTimeout(5_000);
            final ExecutorService executor = Executors.newSingleThreadExecutor();
            try {
                final Future<List<String>> requests =
                        executor.submit(
                                () ->
                                        receiveResponses(
                                                serverSocket,
                                                List.of("{\"data\":[]}"),
                                                List.of("200 OK")));
                final Contract4924DataExportProbe probe =
                        probe(
                                serverSocket.getLocalPort(),
                                1,
                                new ContractRunGuard(),
                                DataExportTransport.GET_WITH_QUERY);

                probe.fetchClosedWindowSample();

                final String request = requests.get(5, TimeUnit.SECONDS).get(0);
                assertTrue(request.startsWith("GET /api/analytics/reports/4924/data?"));
                assertTrue(request.contains("search%5Binvoices%5D%5Bclosed_at%5D="));
                assertTrue(request.contains("order_by=invoice_number"));
                assertFalse(request.contains("request="));
            } finally {
                executor.shutdownNow();
            }
        }
    }

    @Test
    void stopsTheSharedRunOnTheFirstRateLimitAndDoesNotOpenAnotherConnection() throws Exception {
        try (ServerSocket serverSocket = new ServerSocket(0)) {
            serverSocket.setSoTimeout(5_000);
            final ExecutorService executor = Executors.newSingleThreadExecutor();
            try {
                final Future<List<String>> requests =
                        executor.submit(
                                () ->
                                        receiveResponses(
                                                serverSocket,
                                                List.of("{}"),
                                                List.of("429 Too Many Requests"),
                                                List.of(Optional.of("5"))));
                final ContractRunGuard runGuard = new ContractRunGuard();
                final Contract4924DataExportProbe probe =
                        probe(serverSocket.getLocalPort(), 2, runGuard);

                assertThrows(ContractRateLimitExceededException.class, probe::fetchInfo);
                assertTrue(runGuard.isStopped());
                assertThrows(ContractRunStoppedException.class, probe::fetchClosedWindowSample);
                assertEquals(1, requests.get(5, TimeUnit.SECONDS).size());
            } finally {
                executor.shutdownNow();
            }
        }
    }

    @Test
    void enforcesTheAuxiliaryCallCapBeforeASecondConnection() throws Exception {
        try (ServerSocket serverSocket = new ServerSocket(0)) {
            serverSocket.setSoTimeout(5_000);
            final ExecutorService executor = Executors.newSingleThreadExecutor();
            try {
                final Future<List<String>> requests =
                        executor.submit(
                                () ->
                                        receiveResponses(
                                                serverSocket,
                                                List.of("{\"fields\":[],\"filters\":[]}"),
                                                List.of("200 OK")));
                final Contract4924DataExportProbe probe = probe(serverSocket.getLocalPort(), 1);

                probe.fetchInfo();
                assertThrows(
                        ContractRemoteCallLimitExceededException.class,
                        probe::fetchClosedWindowSample);
                assertEquals(1, requests.get(5, TimeUnit.SECONDS).size());
            } finally {
                executor.shutdownNow();
            }
        }
    }

    @Test
    void permitsOnlyOneAuxiliaryInfoCallBeforeOpeningAnotherConnection() throws Exception {
        try (ServerSocket serverSocket = new ServerSocket(0)) {
            serverSocket.setSoTimeout(5_000);
            final ExecutorService executor = Executors.newSingleThreadExecutor();
            try {
                final Future<List<String>> requests =
                        executor.submit(
                                () ->
                                        receiveResponses(
                                                serverSocket,
                                                List.of("{\"fields\":[],\"filters\":[]}"),
                                                List.of("200 OK")));
                final Contract4924DataExportProbe probe = probe(serverSocket.getLocalPort(), 2);

                probe.fetchInfo();
                assertThrows(ContractRemoteCallException.class, probe::fetchInfo);

                assertEquals(1, requests.get(5, TimeUnit.SECONDS).size());
            } finally {
                executor.shutdownNow();
            }
        }
    }

    @Test
    void sanitizesInvalidJsonWithoutKeepingTheRemoteBodyOrCause() throws Exception {
        try (ServerSocket serverSocket = new ServerSocket(0)) {
            serverSocket.setSoTimeout(5_000);
            final ExecutorService executor = Executors.newSingleThreadExecutor();
            try {
                final Future<List<String>> requests =
                        executor.submit(
                                () ->
                                        receiveResponses(
                                                serverSocket,
                                                List.of("remote-payload-900001"),
                                                List.of("200 OK")));
                final Contract4924DataExportProbe probe = probe(serverSocket.getLocalPort(), 1);

                final ContractRemoteCallException exception =
                        assertThrows(ContractRemoteCallException.class, probe::fetchInfo);

                assertFalse(exception.getMessage().contains("900001"));
                assertEquals(null, exception.getCause());
                assertEquals(1, requests.get(5, TimeUnit.SECONDS).size());
            } finally {
                executor.shutdownNow();
            }
        }
    }

    private static Contract4924DataExportProbe probe(final int port, final int maximumCalls) {
        return probe(port, maximumCalls, new ContractRunGuard());
    }

    private static Contract4924DataExportProbe probe(
            final int port, final int maximumCalls, final ContractRunGuard runGuard) {
        return probe(port, maximumCalls, runGuard, DataExportTransport.GET_WITH_BODY);
    }

    private static Contract4924DataExportProbe probe(
            final int port,
            final int maximumCalls,
            final ContractRunGuard runGuard,
            final DataExportTransport transport) {
        final Map<String, String> environment =
                new HashMap<>(Contract4924ConfigurationTest.validEnvironment());
        environment.put("CONTRACT_4924_TRANSPORT", transport.name());
        environment.put("CONTRACT_4924_MAX_CALLS", String.valueOf(maximumCalls));
        return new Contract4924DataExportProbe(
                properties(port),
                Contract4924Configuration.optionalFrom(environment).orElseThrow(),
                runGuard);
    }

    private static DataExportProperties properties(final int port) {
        return new DataExportProperties(
                URI.create("http://127.0.0.1:" + port),
                "synthetic-dataexport-token",
                ZoneId.of("America/Sao_Paulo"),
                Duration.ofSeconds(5),
                DataExportTransport.GET_WITH_BODY,
                new DataExportRetryPolicy(1, Duration.ZERO, Duration.ZERO),
                1024 * 1024);
    }

    private static List<String> receiveResponses(
            final ServerSocket serverSocket, final List<String> bodies, final List<String> statuses)
            throws IOException {
        return receiveResponses(serverSocket, bodies, statuses, retryAfters(bodies.size()));
    }

    private static List<String> receiveResponses(
            final ServerSocket serverSocket,
            final List<String> bodies,
            final List<String> statuses,
            final List<Optional<String>> retryAfters)
            throws IOException {
        final List<String> requests = new ArrayList<>();
        for (int index = 0; index < bodies.size(); index++) {
            try (Socket socket = serverSocket.accept()) {
                requests.add(readRequest(socket));
                writeResponse(
                        socket, statuses.get(index), bodies.get(index), retryAfters.get(index));
            }
        }
        return List.copyOf(requests);
    }

    private static List<Optional<String>> retryAfters(final int size) {
        final List<Optional<String>> values = new ArrayList<>();
        for (int index = 0; index < size; index++) {
            values.add(Optional.empty());
        }
        return List.copyOf(values);
    }

    private static String infoResponse() throws IOException {
        return fixture("4924-info.synthetic.json");
    }

    private static String dataResponse() throws IOException {
        return fixture("4924-data.synthetic.json");
    }

    private static String fixture(final String name) throws IOException {
        final String resource = "/contracts/" + name;
        try (InputStream input =
                Contract4924DataExportProbeTest.class.getResourceAsStream(resource)) {
            if (input == null) {
                throw new IOException("Fixture sintética não encontrada.");
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static String readRequest(final Socket socket) throws IOException {
        final BufferedReader reader =
                new BufferedReader(
                        new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        final StringBuilder request = new StringBuilder();
        int contentLength = 0;
        String line;
        while ((line = reader.readLine()) != null && !line.isEmpty()) {
            request.append(line).append('\n');
            if (line.regionMatches(true, 0, "Content-Length:", 0, "Content-Length:".length())) {
                contentLength = Integer.parseInt(line.substring("Content-Length:".length()).trim());
            }
        }
        final char[] body = new char[contentLength];
        int read = 0;
        while (read < contentLength) {
            final int received = reader.read(body, read, contentLength - read);
            if (received < 0) {
                break;
            }
            read += received;
        }
        request.append(body, 0, read);
        return request.toString();
    }

    private static void writeResponse(
            final Socket socket,
            final String status,
            final String body,
            final Optional<String> retryAfter)
            throws IOException {
        final OutputStreamWriter writer =
                new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8);
        writer.write("HTTP/1.1 " + status + "\r\n");
        writer.write("Content-Type: application/json\r\n");
        writer.write("Content-Length: " + body.getBytes(StandardCharsets.UTF_8).length + "\r\n");
        if (retryAfter.isPresent()) {
            writer.write("Retry-After: " + retryAfter.orElseThrow() + "\r\n");
        }
        writer.write("Connection: close\r\n\r\n");
        writer.write(body);
        writer.flush();
    }

    private static void restoreProperty(final String key, final String previousValue) {
        if (previousValue == null) {
            System.clearProperty(key);
        } else {
            System.setProperty(key, previousValue);
        }
    }
}
