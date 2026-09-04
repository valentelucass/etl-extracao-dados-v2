package br.com.esl.etl.v2.contratos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.plataforma.fonte.dataexport.BusinessDateRange;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportPageRequest;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportTemplate;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
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

class ContractDataExportProbeTest {

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
        if (previousGateValue == null) {
            System.clearProperty("contract.tests.enabled");
        } else {
            System.setProperty("contract.tests.enabled", previousGateValue);
        }
        if (previousProfileMarkerValue == null) {
            System.clearProperty("contract.tests.profile.active");
        } else {
            System.setProperty("contract.tests.profile.active", previousProfileMarkerValue);
        }
    }

    @Test
    void refusesToOpenADataExportConnectionWithoutTheExplicitGate() {
        System.clearProperty("contract.tests.enabled");
        final ContractDataExportProbe probe = probe(1, new ContractRunGuard());

        final IllegalStateException exception =
                assertThrows(
                        IllegalStateException.class,
                        () -> probe.fetchInfo(DataExportTemplate.COLETAS));

        assertTrue(exception.getMessage().contains("contract.tests.enabled=true"));
    }

    @Test
    void countsInfoAndDataAttemptsAgainstTheSameTemplateBudget() throws Exception {
        try (ServerSocket serverSocket = new ServerSocket(0)) {
            serverSocket.setSoTimeout(5_000);
            final ExecutorService executor = Executors.newSingleThreadExecutor();
            try {
                final Future<List<String>> requests =
                        executor.submit(
                                () ->
                                        receiveResponses(
                                                serverSocket,
                                                List.of(
                                                        "{\"fields\":[{\"name\":\"id\"}]}",
                                                        "{\"data\":{\"id\":900001}}"),
                                                List.of("200 OK", "200 OK")));
                final ContractDataExportProbe probe =
                        probe(serverSocket.getLocalPort(), new ContractRunGuard(), 2);

                probe.fetchInfo(DataExportTemplate.COLETAS);
                probe.fetchPage(coletasRequest());
                assertThrows(
                        ContractRemoteCallLimitExceededException.class,
                        () -> probe.fetchPage(coletasRequest()));

                assertEquals(2, probe.callsMade(DataExportTemplate.COLETAS));
                final List<String> captured = requests.get(5, TimeUnit.SECONDS);
                assertEquals(2, captured.size());
                assertTrue(
                        captured.get(0)
                                .startsWith("GET /api/analytics/reports/6908/info HTTP/1.1"));
                assertTrue(
                        captured.get(1)
                                .startsWith("GET /api/analytics/reports/6908/data HTTP/1.1"));
            } finally {
                executor.shutdownNow();
            }
        }
    }

    @Test
    void permitsOnlyOneInfoCallPerTemplateBeforeOpeningAnotherConnection() throws Exception {
        try (ServerSocket serverSocket = new ServerSocket(0)) {
            serverSocket.setSoTimeout(5_000);
            final ExecutorService executor = Executors.newSingleThreadExecutor();
            try {
                final Future<List<String>> requests =
                        executor.submit(
                                () ->
                                        receiveResponses(
                                                serverSocket,
                                                List.of("{\"fields\":[{\"name\":\"id\"}]}"),
                                                List.of("200 OK")));
                final ContractDataExportProbe probe =
                        probe(serverSocket.getLocalPort(), new ContractRunGuard(), 2);

                probe.fetchInfo(DataExportTemplate.COLETAS);
                assertThrows(
                        ContractRemoteCallException.class,
                        () -> probe.fetchInfo(DataExportTemplate.COLETAS));

                assertEquals(1, requests.get(5, TimeUnit.SECONDS).size());
            } finally {
                executor.shutdownNow();
            }
        }
    }

    @Test
    void stopsTheSharedRunAfterADataExportRateLimit() throws Exception {
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
                                                List.of(Optional.of("120"))));
                final ContractRunGuard runGuard = new ContractRunGuard();
                final ContractDataExportProbe probe =
                        probe(serverSocket.getLocalPort(), runGuard, 2);

                final ContractRateLimitExceededException exception =
                        assertThrows(
                                ContractRateLimitExceededException.class,
                                () -> probe.fetchInfo(DataExportTemplate.COLETAS));

                assertEquals(Optional.of("120"), exception.retryAfter());
                assertTrue(runGuard.isStopped());
                assertThrows(
                        ContractRunStoppedException.class, () -> probe.fetchPage(coletasRequest()));
                assertEquals(1, requests.get(5, TimeUnit.SECONDS).size());
            } finally {
                executor.shutdownNow();
            }
        }
    }

    @Test
    void doesNotTryAnAlternativeTransportWhenTheApprovedTransportIsRejected() throws Exception {
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
                final ContractDataExportProbe probe =
                        probe(serverSocket.getLocalPort(), new ContractRunGuard(), 2);

                assertThrows(
                        ContractRemoteCallException.class, () -> probe.fetchPage(coletasRequest()));

                assertEquals(1, requests.get(5, TimeUnit.SECONDS).size());
                assertEquals(1, probe.callsMade(DataExportTemplate.COLETAS));
            } finally {
                executor.shutdownNow();
            }
        }
    }

    @Test
    void dropsTheTransportCauseBeforeAFailsafeReportCanExposeIt() throws Exception {
        final int unusedPort;
        try (ServerSocket serverSocket = new ServerSocket(0)) {
            unusedPort = serverSocket.getLocalPort();
        }
        final ContractDataExportProbe probe = probe(unusedPort, new ContractRunGuard());

        final ContractRemoteCallException exception =
                assertThrows(
                        ContractRemoteCallException.class,
                        () -> probe.fetchInfo(DataExportTemplate.COLETAS));

        assertFalse(exception.getMessage().contains("127.0.0.1"));
        assertEquals(null, exception.getCause());
    }

    private static ContractDataExportProbe probe(final int port, final ContractRunGuard runGuard) {
        return probe(port, runGuard, 1);
    }

    private static ContractDataExportProbe probe(
            final int port, final ContractRunGuard runGuard, final int maxCallsPerTemplate) {
        return new ContractDataExportProbe(configuration(port, maxCallsPerTemplate), runGuard);
    }

    private static ContractTestConfiguration configuration(
            final int port, final int maxCallsPerTemplate) {
        final Map<String, String> environment = new HashMap<>();
        environment.put("CONTRACT_DATAEXPORT_BASE_URL", "http://127.0.0.1:" + port);
        environment.put("CONTRACT_DATAEXPORT_TOKEN", "synthetic-dataexport-token");
        environment.put("CONTRACT_GRAPHQL_URL", "http://127.0.0.1:" + port + "/graphql");
        environment.put("CONTRACT_GRAPHQL_TOKEN", "synthetic-graphql-token");
        environment.put("CONTRACT_SOURCE_TIMEZONE", "America/Sao_Paulo");
        environment.put("CONTRACT_6908_MAX_CALLS", String.valueOf(maxCallsPerTemplate));
        environment.put("CONTRACT_6389_MAX_CALLS", String.valueOf(maxCallsPerTemplate));
        environment.put("CONTRACT_DATAEXPORT_TRANSPORT", "GET_WITH_BODY");
        environment.put("CONTRACT_PAGE_SIZE_A", "1");
        environment.put("CONTRACT_PAGE_SIZE_B", "2");
        environment.put("CONTRACT_6908_POPULATED_WINDOW", "2026-08-13..2026-08-13");
        environment.put("CONTRACT_6908_EMPTY_WINDOW", "2026-08-14..2026-08-14");
        environment.put("CONTRACT_6908_LATE_CHANGE_BUSINESS_WINDOW", "2026-08-13..2026-08-13");
        environment.put(
                "CONTRACT_6908_LATE_CHANGE_UPDATED_AT_WINDOW",
                "2026-08-20T00:00:00Z..2026-08-20T23:59:59Z");
        environment.put("CONTRACT_6389_POPULATED_WINDOW", "2026-08-13..2026-08-13");
        environment.put("CONTRACT_6389_EMPTY_WINDOW", "2026-08-14..2026-08-14");
        environment.put("CONTRACT_6389_LATE_CHANGE_BUSINESS_WINDOW", "2026-08-13..2026-08-13");
        environment.put(
                "CONTRACT_6389_LATE_CHANGE_UPDATED_AT_WINDOW",
                "2026-08-20T00:00:00Z..2026-08-20T23:59:59Z");
        return ContractTestConfiguration.from(environment);
    }

    private static DataExportPageRequest coletasRequest() {
        return DataExportPageRequest.forTemplate(
                DataExportTemplate.COLETAS,
                new BusinessDateRange(LocalDate.of(2026, 8, 13), LocalDate.of(2026, 8, 13)),
                null,
                1);
    }

    private static List<String> receiveResponses(
            final ServerSocket serverSocket, final List<String> bodies, final List<String> statuses)
            throws IOException {
        return receiveResponses(serverSocket, bodies, statuses, defaultRetryAfters(bodies.size()));
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

    private static List<Optional<String>> defaultRetryAfters(final int size) {
        final List<Optional<String>> retryAfters = new ArrayList<>();
        for (int index = 0; index < size; index++) {
            retryAfters.add(Optional.empty());
        }
        return List.copyOf(retryAfters);
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
}
