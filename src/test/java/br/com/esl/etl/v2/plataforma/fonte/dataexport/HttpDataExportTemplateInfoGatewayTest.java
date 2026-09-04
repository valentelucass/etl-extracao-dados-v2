package br.com.esl.etl.v2.plataforma.fonte.dataexport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.plataforma.configuracao.DataExportProperties;
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
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class HttpDataExportTemplateInfoGatewayTest {

    @Test
    void requestsTypedMetadataFromTheInfoEndpoint() throws Exception {
        try (ServerSocket serverSocket = new ServerSocket(0)) {
            serverSocket.setSoTimeout(5_000);
            final ExecutorService executor = Executors.newSingleThreadExecutor();
            try {
                final Future<String> capturedRequest =
                        executor.submit(
                                () ->
                                        receiveAndRespond(
                                                serverSocket,
                                                "200 OK",
                                                "{\"fields\":[{\"name\":\"id\",\"type\":\"integer\"}],"
                                                        + "\"filters\":[{\"name\":\"picks.request_date\"}]}"));
                final DataExportTemplateInfo info =
                        gateway(serverSocket, 1_024L).fetchInfo(DataExportTemplate.COLETAS);

                assertEquals(6908, info.template().templateId());
                assertTrue(info.jsonContentTypeDeclared());
                assertEquals(
                        List.of("id"),
                        info.fields().stream()
                                .map(DataExportMetadataField::technicalName)
                                .toList());
                assertEquals(
                        List.of("picks.request_date"),
                        info.filters().stream()
                                .map(DataExportMetadataField::technicalName)
                                .toList());
                final String request = capturedRequest.get(5, TimeUnit.SECONDS);
                assertTrue(request.startsWith("GET /api/analytics/reports/6908/info HTTP/1.1"));
                assertTrue(request.contains("Authorization: Bearer test-token"));
            } finally {
                executor.shutdownNow();
            }
        }
    }

    @Test
    void rejectsA2xxErrorEnvelopeFromTheInfoEndpoint() throws Exception {
        try (ServerSocket serverSocket = new ServerSocket(0)) {
            serverSocket.setSoTimeout(5_000);
            final ExecutorService executor = Executors.newSingleThreadExecutor();
            try {
                final Future<String> capturedRequest =
                        executor.submit(
                                () ->
                                        receiveAndRespond(
                                                serverSocket,
                                                "200 OK",
                                                "{\"data\":{},\"errors\":[{\"message\":\"synthetic\"}]}"));

                assertThrows(
                        IllegalStateException.class,
                        () -> gateway(serverSocket, 1_024L).fetchInfo(DataExportTemplate.FRETES));
                assertTrue(
                        capturedRequest
                                .get(5, TimeUnit.SECONDS)
                                .startsWith("GET /api/analytics/reports/6389/info HTTP/1.1"));
            } finally {
                executor.shutdownNow();
            }
        }
    }

    @Test
    void rejectsNoContentFromTheInfoEndpoint() throws Exception {
        try (ServerSocket serverSocket = new ServerSocket(0)) {
            serverSocket.setSoTimeout(5_000);
            final ExecutorService executor = Executors.newSingleThreadExecutor();
            try {
                final Future<String> capturedRequest =
                        executor.submit(
                                () -> receiveAndRespond(serverSocket, "204 No Content", ""));

                final IllegalStateException exception =
                        assertThrows(
                                IllegalStateException.class,
                                () ->
                                        gateway(serverSocket, 1_024L)
                                                .fetchInfo(DataExportTemplate.COLETAS));

                assertEquals(
                        "Data Export retornou HTTP 204 sem metadados para o template 6908.",
                        exception.getMessage());
                assertTrue(
                        capturedRequest
                                .get(5, TimeUnit.SECONDS)
                                .startsWith("GET /api/analytics/reports/6908/info HTTP/1.1"));
            } finally {
                executor.shutdownNow();
            }
        }
    }

    @Test
    void exposesRetryAfterWithoutApplyingItAutomatically() throws Exception {
        try (ServerSocket serverSocket = new ServerSocket(0)) {
            serverSocket.setSoTimeout(5_000);
            final ExecutorService executor = Executors.newSingleThreadExecutor();
            try {
                final Future<String> capturedRequest =
                        executor.submit(
                                () ->
                                        receiveAndRespond(
                                                serverSocket,
                                                "429 Too Many Requests",
                                                "{}",
                                                Optional.of("120")));

                final DataExportUnavailableException exception =
                        assertThrows(
                                DataExportUnavailableException.class,
                                () ->
                                        gateway(serverSocket, 1_024L)
                                                .fetchInfo(DataExportTemplate.COLETAS));

                assertEquals(Optional.of("120"), exception.retryAfter());
                assertEquals(429, exception.httpStatus().orElseThrow());
                assertTrue(
                        capturedRequest
                                .get(5, TimeUnit.SECONDS)
                                .startsWith("GET /api/analytics/reports/6908/info HTTP/1.1"));
            } finally {
                executor.shutdownNow();
            }
        }
    }

    @Test
    void limitsMetadataBytesBeforeParsing() throws Exception {
        try (ServerSocket serverSocket = new ServerSocket(0)) {
            serverSocket.setSoTimeout(5_000);
            final ExecutorService executor = Executors.newSingleThreadExecutor();
            try {
                final Future<String> capturedRequest =
                        executor.submit(
                                () ->
                                        receiveAndRespond(
                                                serverSocket,
                                                "200 OK",
                                                "{\"fields\":[{\"name\":\"id\"}]}"));

                assertThrows(
                        DataExportResponseLimitExceededException.class,
                        () -> gateway(serverSocket, 8L).fetchInfo(DataExportTemplate.COLETAS));
                assertTrue(
                        capturedRequest
                                .get(5, TimeUnit.SECONDS)
                                .startsWith("GET /api/analytics/reports/6908/info HTTP/1.1"));
            } finally {
                executor.shutdownNow();
            }
        }
    }

    @Test
    void sanitizesMalformedMetadataJsonWithoutRetainingTheResponsePayload() throws Exception {
        try (ServerSocket serverSocket = new ServerSocket(0)) {
            serverSocket.setSoTimeout(5_000);
            final ExecutorService executor = Executors.newSingleThreadExecutor();
            try {
                final Future<String> capturedRequest =
                        executor.submit(
                                () ->
                                        receiveAndRespond(
                                                serverSocket,
                                                "200 OK",
                                                "{\"fields\":[{\"name\":\"payload-value\"}"));

                final IllegalStateException exception =
                        assertThrows(
                                IllegalStateException.class,
                                () ->
                                        gateway(serverSocket, 1_024L)
                                                .fetchInfo(DataExportTemplate.COLETAS));

                assertEquals(
                        "Resposta JSON inválida nos metadados do template 6908.",
                        exception.getMessage());
                assertNull(exception.getCause());
                assertFalse(exception.toString().contains("payload-value"));
                assertTrue(
                        capturedRequest
                                .get(5, TimeUnit.SECONDS)
                                .startsWith("GET /api/analytics/reports/6908/info HTTP/1.1"));
            } finally {
                executor.shutdownNow();
            }
        }
    }

    private static HttpDataExportTemplateInfoGateway gateway(
            final ServerSocket serverSocket, final long maxResponseBytes) {
        final DataExportProperties properties =
                new DataExportProperties(
                        URI.create("http://127.0.0.1:" + serverSocket.getLocalPort()),
                        "test-token",
                        ZoneId.of("America/Sao_Paulo"),
                        Duration.ofSeconds(5),
                        DataExportTransport.GET_WITH_BODY,
                        new DataExportRetryPolicy(1, Duration.ZERO, Duration.ZERO),
                        maxResponseBytes);
        return new HttpDataExportTemplateInfoGateway(
                java.net.http.HttpClient.newHttpClient(),
                properties,
                new ObjectMapper(),
                duration -> {});
    }

    private static String receiveAndRespond(
            final ServerSocket serverSocket, final String status, final String responseBody)
            throws IOException {
        return receiveAndRespond(serverSocket, status, responseBody, Optional.empty());
    }

    private static String receiveAndRespond(
            final ServerSocket serverSocket,
            final String status,
            final String responseBody,
            final Optional<String> retryAfter)
            throws IOException {
        try (Socket socket = serverSocket.accept()) {
            final String request = readRequest(socket);
            writeResponse(socket, status, responseBody, retryAfter);
            return request;
        }
    }

    private static String readRequest(final Socket socket) throws IOException {
        final BufferedReader reader =
                new BufferedReader(
                        new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        final StringBuilder request = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null && !line.isEmpty()) {
            request.append(line).append('\n');
        }
        return request.toString();
    }

    private static void writeResponse(
            final Socket socket,
            final String status,
            final String responseBody,
            final Optional<String> retryAfter)
            throws IOException {
        final OutputStreamWriter writer =
                new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8);
        writer.write("HTTP/1.1 " + status + "\r\n");
        writer.write("Content-Type: application/json\r\n");
        writer.write(
                "Content-Length: " + responseBody.getBytes(StandardCharsets.UTF_8).length + "\r\n");
        retryAfter.ifPresent(value -> writeRetryAfter(writer, value));
        writer.write("Connection: close\r\n\r\n");
        writer.write(responseBody);
        writer.flush();
    }

    private static void writeRetryAfter(final OutputStreamWriter writer, final String value) {
        try {
            writer.write("Retry-After: " + value + "\r\n");
        } catch (final IOException exception) {
            throw new IllegalStateException(
                    "Não foi possível escrever Retry-After no servidor de teste.", exception);
        }
    }
}
