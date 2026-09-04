package br.com.esl.etl.v2.contratos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportTemplate;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ContractRemoteExecutionTest {

    private String previousEnabled;
    private String previousProfileActive;

    @BeforeEach
    void enableTheLocalOnlyContractGate() {
        previousEnabled = System.getProperty("contract.tests.enabled");
        previousProfileActive = System.getProperty("contract.tests.profile.active");
        System.setProperty("contract.tests.enabled", "true");
        System.setProperty("contract.tests.profile.active", "true");
    }

    @AfterEach
    void restoreTheContractGate() {
        restoreProperty("contract.tests.enabled", previousEnabled);
        restoreProperty("contract.tests.profile.active", previousProfileActive);
    }

    @Test
    void sharesTheEntityCapAcrossGraphQlAndDataExport() throws Exception {
        try (ServerSocket serverSocket = new ServerSocket(0)) {
            serverSocket.setSoTimeout(5_000);
            final ExecutorService executor = Executors.newSingleThreadExecutor();
            try {
                final Future<String> request =
                        executor.submit(() -> serveGraphQlPage(serverSocket));
                final ContractRemoteExecution execution =
                        ContractRemoteExecution.open(configuration(serverSocket.getLocalPort()));

                execution.fetchAllColetas(
                        new ContractDateWindow(
                                LocalDate.of(2026, 8, 13), LocalDate.of(2026, 8, 13)),
                        1);

                assertThrows(
                        ContractRemoteCallLimitExceededException.class,
                        () -> execution.fetchInfo(DataExportTemplate.COLETAS));
                assertTrue(execution.isStopped());
                assertTrue(request.get(5, TimeUnit.SECONDS).startsWith("POST /graphql HTTP/1.1"));
            } finally {
                executor.shutdownNow();
            }
        }
    }

    @Test
    void wiresTheOptional4924ProbeWithoutOpeningANetworkConnection() {
        final Map<String, String> environment = new HashMap<>(environment(1));
        environment.putAll(Contract4924ConfigurationTest.validEnvironment());

        final ContractRemoteExecution execution =
                ContractRemoteExecution.open(ContractTestConfiguration.from(environment));

        assertTrue(execution.auxiliary4924Probe().isPresent());
        assertEquals(0, execution.auxiliary4924Probe().orElseThrow().callsMade());
    }

    private static ContractTestConfiguration configuration(final int port) {
        return ContractTestConfiguration.from(environment(port));
    }

    private static Map<String, String> environment(final int port) {
        final Map<String, String> environment = new HashMap<>();
        environment.put("CONTRACT_DATAEXPORT_BASE_URL", "http://127.0.0.1:" + port);
        environment.put("CONTRACT_DATAEXPORT_TOKEN", "synthetic-dataexport-token");
        environment.put("CONTRACT_GRAPHQL_URL", "http://127.0.0.1:" + port + "/graphql");
        environment.put("CONTRACT_GRAPHQL_TOKEN", "synthetic-graphql-token");
        environment.put("CONTRACT_SOURCE_TIMEZONE", "America/Sao_Paulo");
        environment.put("CONTRACT_6908_MAX_CALLS", "1");
        environment.put("CONTRACT_6389_MAX_CALLS", "2");
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
        return environment;
    }

    private static String serveGraphQlPage(final ServerSocket serverSocket) throws IOException {
        try (Socket socket = serverSocket.accept()) {
            final String request = readRequest(socket);
            final String body =
                    "{\"data\":{\"pick\":{\"edges\":[],\"pageInfo\":{\"hasNextPage\":false,\"endCursor\":null}}}}";
            final OutputStreamWriter writer =
                    new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8);
            writer.write("HTTP/1.1 200 OK\r\n");
            writer.write("Content-Type: application/json\r\n");
            writer.write(
                    "Content-Length: " + body.getBytes(StandardCharsets.UTF_8).length + "\r\n");
            writer.write("Connection: close\r\n\r\n");
            writer.write(body);
            writer.flush();
            return request;
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
        request.append('\n');
        if (contentLength > 0) {
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
        }
        return request.toString();
    }

    private static void restoreProperty(final String name, final String value) {
        if (value == null) {
            System.clearProperty(name);
        } else {
            System.setProperty(name, value);
        }
    }
}
