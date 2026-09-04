package br.com.esl.etl.v2.contratos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ReadOnlyGraphQlParityHarnessTest {

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
    void paginatesMinimalColetaIdentitiesWithTheV1BusinessWindow() throws Exception {
        try (ServerSocket serverSocket = new ServerSocket(0)) {
            serverSocket.setSoTimeout(5_000);
            final ExecutorService executor = Executors.newSingleThreadExecutor();
            try {
                final Future<List<String>> capturedRequests =
                        executor.submit(
                                () ->
                                        receiveResponses(
                                                serverSocket,
                                                List.of(firstColetaPage(), finalColetaPage())));
                final ReadOnlyGraphQlParityHarness harness = harness(serverSocket);

                final List<ContractGraphQlColetaIdentity> rows =
                        harness.fetchAllColetas(
                                new ContractDateWindow(
                                        LocalDate.of(2026, 8, 13), LocalDate.of(2026, 8, 13)),
                                1,
                                new ContractRemoteCallBudget(2));

                final List<String> requests = capturedRequests.get(5, TimeUnit.SECONDS);
                final ObjectMapper objectMapper = new ObjectMapper();
                final JsonNode firstPayload = payloadOf(objectMapper, requests.get(0));
                final JsonNode secondPayload = payloadOf(objectMapper, requests.get(1));
                assertEquals(2, rows.size());
                assertEquals("pick-1", rows.get(0).sourceId().orElseThrow());
                assertEquals("sequence-2", rows.get(1).sequenceCode().orElseThrow());
                assertEquals("pick-item-1", rows.get(0).pickItemIds().get(0).orElseThrow());
                assertTrue(requests.get(0).startsWith("POST /graphql HTTP/1.1"));
                assertTrue(requests.get(0).contains("Authorization: Bearer test-contract-token"));
                assertTrue(firstPayload.path("query").asText().contains("ContractColetasReadOnly"));
                assertEquals(
                        "2026-08-13",
                        firstPayload.path("variables").path("params").path("requestDate").asText());
                assertTrue(firstPayload.path("variables").path("after").isNull());
                assertEquals("cursor-1", secondPayload.path("variables").path("after").asText());
                assertEquals(1, secondPayload.path("variables").path("first").asInt());
            } finally {
                executor.shutdownNow();
            }
        }
    }

    @Test
    void parsesOnlyTheMinimalFreteIdentityAndRelationshipFields() throws Exception {
        try (ServerSocket serverSocket = new ServerSocket(0)) {
            serverSocket.setSoTimeout(5_000);
            final ExecutorService executor = Executors.newSingleThreadExecutor();
            try {
                final Future<List<String>> capturedRequests =
                        executor.submit(
                                () -> receiveResponses(serverSocket, List.of(finalFretePage())));
                final ReadOnlyGraphQlParityHarness harness = harness(serverSocket);

                final ContractGraphQlPage<ContractGraphQlFreteIdentity> page =
                        harness.fetchFretesPage(
                                new ContractDateWindow(
                                        LocalDate.of(2026, 8, 13), LocalDate.of(2026, 8, 14)),
                                java.util.Optional.empty(),
                                2,
                                new ContractRemoteCallBudget(1));

                final List<String> requests = capturedRequests.get(5, TimeUnit.SECONDS);
                final JsonNode payload = payloadOf(new ObjectMapper(), requests.get(0));
                final ContractGraphQlFreteIdentity row = page.rows().get(0);
                assertEquals("freight-1", row.sourceId().orElseThrow());
                assertEquals("minuta-1", row.corporationSequenceNumber().orElseThrow());
                assertEquals("pick-item-1", row.pickItemId().orElseThrow());
                assertEquals("cte-key-1", row.cteKey().orElseThrow());
                assertEquals("123.45", row.total().orElseThrow().toPlainString());
                assertEquals("credit-1", row.accountingCreditId().orElseThrow());
                assertEquals("installment-1", row.accountingCreditInstallmentId().orElseThrow());
                assertEquals("reference-1", row.referenceNumber().orElseThrow());
                assertTrue(payload.path("query").asText().contains("ContractFretesReadOnly"));
                assertEquals(
                        "2026-08-13 - 2026-08-14",
                        payload.path("variables").path("params").path("serviceAt").asText());
            } finally {
                executor.shutdownNow();
            }
        }
    }

    @Test
    void splitsFreteParityIntoTheSameThirtyDayBusinessWindowsUsedByV1() throws Exception {
        try (ServerSocket serverSocket = new ServerSocket(0)) {
            serverSocket.setSoTimeout(5_000);
            final ExecutorService executor = Executors.newSingleThreadExecutor();
            try {
                final Future<List<String>> capturedRequests =
                        executor.submit(
                                () ->
                                        receiveResponses(
                                                serverSocket,
                                                List.of(finalFretePage(), finalFretePage())));
                final List<ContractGraphQlFreteIdentity> rows =
                        harness(serverSocket)
                                .fetchAllFretes(
                                        new ContractDateWindow(
                                                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 2, 4)),
                                        1,
                                        new ContractRemoteCallBudget(2));

                final List<String> requests = capturedRequests.get(5, TimeUnit.SECONDS);
                final ObjectMapper objectMapper = new ObjectMapper();
                assertEquals(2, rows.size());
                assertEquals(
                        "2026-01-01 - 2026-01-30",
                        payloadOf(objectMapper, requests.get(0))
                                .path("variables")
                                .path("params")
                                .path("serviceAt")
                                .asText());
                assertEquals(
                        "2026-01-31 - 2026-02-04",
                        payloadOf(objectMapper, requests.get(1))
                                .path("variables")
                                .path("params")
                                .path("serviceAt")
                                .asText());
            } finally {
                executor.shutdownNow();
            }
        }
    }

    @Test
    void rejectsAnIntervalForASingleGraphQlColetaPageBeforeOpeningAConnection() {
        final ReadOnlyGraphQlParityHarness harness =
                new ReadOnlyGraphQlParityHarness(
                        java.net.http.HttpClient.newHttpClient(),
                        URI.create("http://127.0.0.1:1/graphql"),
                        "test-contract-token",
                        Duration.ofSeconds(5),
                        new ObjectMapper());

        assertThrows(
                IllegalStateException.class,
                () ->
                        harness.fetchColetasPage(
                                new ContractDateWindow(
                                        LocalDate.of(2026, 8, 13), LocalDate.of(2026, 8, 14)),
                                java.util.Optional.empty(),
                                1,
                                new ContractRemoteCallBudget(1)));
    }

    @Test
    void stopsImmediatelyWhenTheSupplierReturnsRateLimit() throws Exception {
        try (ServerSocket serverSocket = new ServerSocket(0)) {
            serverSocket.setSoTimeout(5_000);
            final ExecutorService executor = Executors.newSingleThreadExecutor();
            try {
                final Future<List<String>> capturedRequests =
                        executor.submit(
                                () ->
                                        receiveResponses(
                                                serverSocket,
                                                List.of("{}"),
                                                "429 Too Many Requests"));
                final ContractRemoteCallBudget callBudget = new ContractRemoteCallBudget(2);
                final ContractRunGuard runGuard = new ContractRunGuard();
                final ReadOnlyGraphQlParityHarness harness =
                        new ReadOnlyGraphQlParityHarness(
                                java.net.http.HttpClient.newHttpClient(),
                                URI.create(
                                        "http://127.0.0.1:"
                                                + serverSocket.getLocalPort()
                                                + "/graphql"),
                                "test-contract-token",
                                Duration.ofSeconds(5),
                                new ObjectMapper(),
                                1_024L,
                                runGuard);

                assertThrows(
                        ContractRateLimitExceededException.class,
                        () ->
                                harness.fetchColetasPage(
                                        new ContractDateWindow(
                                                LocalDate.of(2026, 8, 13),
                                                LocalDate.of(2026, 8, 13)),
                                        java.util.Optional.empty(),
                                        1,
                                        callBudget));

                assertEquals(1, callBudget.callsMade());
                assertTrue(runGuard.isStopped());
                assertEquals(1, capturedRequests.get(5, TimeUnit.SECONDS).size());
                assertThrows(
                        ContractRunStoppedException.class,
                        () ->
                                harness.fetchColetasPage(
                                        new ContractDateWindow(
                                                LocalDate.of(2026, 8, 13),
                                                LocalDate.of(2026, 8, 13)),
                                        java.util.Optional.empty(),
                                        1,
                                        callBudget));
            } finally {
                executor.shutdownNow();
            }
        }
    }

    @Test
    void refusesToOpenAnHttpConnectionWithoutTheExplicitGate() throws Exception {
        System.clearProperty("contract.tests.enabled");
        try (ServerSocket serverSocket = new ServerSocket(0)) {
            serverSocket.setSoTimeout(250);

            assertThrows(
                    IllegalStateException.class,
                    () ->
                            harness(serverSocket)
                                    .fetchColetasPage(
                                            new ContractDateWindow(
                                                    LocalDate.of(2026, 8, 13),
                                                    LocalDate.of(2026, 8, 13)),
                                            java.util.Optional.empty(),
                                            1,
                                            new ContractRemoteCallBudget(1)));

            assertThrows(java.net.SocketTimeoutException.class, serverSocket::accept);
        }
    }

    @Test
    void refusesToOpenAnHttpConnectionWithoutTheFailsafeProfileMarker() throws Exception {
        System.clearProperty("contract.tests.profile.active");
        try (ServerSocket serverSocket = new ServerSocket(0)) {
            serverSocket.setSoTimeout(250);

            assertThrows(
                    IllegalStateException.class,
                    () ->
                            harness(serverSocket)
                                    .fetchColetasPage(
                                            new ContractDateWindow(
                                                    LocalDate.of(2026, 8, 13),
                                                    LocalDate.of(2026, 8, 13)),
                                            java.util.Optional.empty(),
                                            1,
                                            new ContractRemoteCallBudget(1)));

            assertThrows(java.net.SocketTimeoutException.class, serverSocket::accept);
        }
    }

    @Test
    void preventsTheNextPageBeforeSendingItWhenTheCallCapIsReached() throws Exception {
        try (ServerSocket serverSocket = new ServerSocket(0)) {
            serverSocket.setSoTimeout(5_000);
            final ExecutorService executor = Executors.newSingleThreadExecutor();
            try {
                final Future<List<String>> capturedRequests =
                        executor.submit(
                                () -> receiveResponses(serverSocket, List.of(firstColetaPage())));

                assertThrows(
                        ContractRemoteCallLimitExceededException.class,
                        () ->
                                harness(serverSocket)
                                        .fetchAllColetas(
                                                new ContractDateWindow(
                                                        LocalDate.of(2026, 8, 13),
                                                        LocalDate.of(2026, 8, 13)),
                                                1,
                                                new ContractRemoteCallBudget(1)));

                assertEquals(1, capturedRequests.get(5, TimeUnit.SECONDS).size());
            } finally {
                executor.shutdownNow();
            }
        }
    }

    private static ReadOnlyGraphQlParityHarness harness(final ServerSocket serverSocket) {
        return new ReadOnlyGraphQlParityHarness(
                java.net.http.HttpClient.newHttpClient(),
                URI.create("http://127.0.0.1:" + serverSocket.getLocalPort() + "/graphql"),
                "test-contract-token",
                Duration.ofSeconds(5),
                new ObjectMapper());
    }

    private static JsonNode payloadOf(final ObjectMapper objectMapper, final String request)
            throws IOException {
        final int payloadStart = request.indexOf("\n\n");
        return objectMapper.readTree(request.substring(payloadStart + 2));
    }

    private static List<String> receiveResponses(
            final ServerSocket serverSocket, final List<String> responseBodies) throws IOException {
        return receiveResponses(serverSocket, responseBodies, "200 OK");
    }

    private static List<String> receiveResponses(
            final ServerSocket serverSocket, final List<String> responseBodies, final String status)
            throws IOException {
        final List<String> requests = new ArrayList<>();
        for (final String responseBody : responseBodies) {
            try (Socket socket = serverSocket.accept()) {
                requests.add(readRequest(socket));
                writeResponse(socket, status, responseBody);
            }
        }
        return List.copyOf(requests);
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
        request.append('\n');
        if (contentLength > 0) {
            final char[] body = new char[contentLength];
            int offset = 0;
            while (offset < contentLength) {
                final int read = reader.read(body, offset, contentLength - offset);
                if (read < 0) {
                    break;
                }
                offset += read;
            }
            request.append(body, 0, offset);
        }
        return request.toString();
    }

    private static void writeResponse(
            final Socket socket, final String status, final String responseBody)
            throws IOException {
        final OutputStreamWriter writer =
                new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8);
        writer.write("HTTP/1.1 " + status + "\r\n");
        writer.write("Content-Type: application/json\r\n");
        writer.write(
                "Content-Length: " + responseBody.getBytes(StandardCharsets.UTF_8).length + "\r\n");
        writer.write("Connection: close\r\n\r\n");
        writer.write(responseBody);
        writer.flush();
    }

    private static String firstColetaPage() {
        return """
            {
              "data": {
                "pick": {
                  "edges": [{"node": {"id": "pick-1", "sequenceCode": "sequence-1", "pickItems": [{"id": "pick-item-1"}]}}],
                  "pageInfo": {"hasNextPage": true, "endCursor": "cursor-1"}
                }
              }
            }
            """;
    }

    private static String finalColetaPage() {
        return """
            {
              "data": {
                "pick": {
                  "edges": [{"node": {"id": "pick-2", "sequenceCode": "sequence-2", "pickItems": []}}],
                  "pageInfo": {"hasNextPage": false, "endCursor": null}
                }
              }
            }
            """;
    }

    private static String finalFretePage() {
        return """
            {
              "data": {
                "freight": {
                  "edges": [{
                    "node": {
                      "id": "freight-1",
                      "corporationSequenceNumber": "minuta-1",
                      "pickItemId": "pick-item-1",
                      "total": 123.45,
                      "accountingCreditId": "credit-1",
                      "accountingCreditInstallmentId": "installment-1",
                      "referenceNumber": "reference-1",
                      "cte": {"key": "cte-key-1"}
                    }
                  }],
                  "pageInfo": {"hasNextPage": false, "endCursor": null}
                }
              }
            }
            """;
    }
}
