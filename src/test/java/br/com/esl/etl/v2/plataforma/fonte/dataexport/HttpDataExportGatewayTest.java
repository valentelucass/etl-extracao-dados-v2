package br.com.esl.etl.v2.plataforma.fonte.dataexport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.plataforma.configuracao.DataExportProperties;
import br.com.esl.etl.v2.plataforma.contrato.ContractCompatibilityPolicy;
import br.com.esl.etl.v2.plataforma.contrato.ContractObservationLimits;
import br.com.esl.etl.v2.plataforma.contrato.ContractResponsePathBoundary;
import br.com.esl.etl.v2.plataforma.contrato.ContractTestSupport;
import br.com.esl.etl.v2.plataforma.contrato.SourceContractRelease;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class HttpDataExportGatewayTest {

    @Test
    void sendsScopeAsSiblingAndNormalizesDataResponse() throws Exception {
        try (ServerSocket serverSocket = new ServerSocket(0)) {
            serverSocket.setSoTimeout(5_000);
            final ExecutorService executor = Executors.newSingleThreadExecutor();
            try {
                final Future<String> capturedRequest =
                        executor.submit(() -> receiveAndRespond(serverSocket));
                final DataExportProperties properties =
                        new DataExportProperties(
                                URI.create("http://127.0.0.1:" + serverSocket.getLocalPort()),
                                "test-token",
                                ZoneId.of("America/Sao_Paulo"),
                                Duration.ofSeconds(5),
                                DataExportTransport.GET_WITH_BODY,
                                new DataExportRetryPolicy(1, Duration.ZERO, Duration.ZERO));
                final HttpDataExportGateway gateway =
                        new HttpDataExportGateway(
                                properties,
                                DataExportHttpAttemptObserver.noop(),
                                new DataExportContractObservationConfiguration(
                                        DataExportTemplate.COLETAS,
                                        DataExportResponseForm.ENVELOPE_DATA_ARRAY,
                                        "/id",
                                        ContractObservationLimits.runtimeDefaults(),
                                        runtimePathBoundary(),
                                        new br.com.esl.etl.v2.plataforma.controle
                                                .ImmutableFingerprint(
                                                "runtime-v1", "d".repeat(64))));
                final DataExportPageResponse page =
                        gateway.fetch(
                                DataExportPageRequest.forTemplate(
                                        DataExportTemplate.COLETAS,
                                        new BusinessDateRange(
                                                LocalDate.of(2026, 8, 13),
                                                LocalDate.of(2026, 8, 13)),
                                        new SourceDateTimeRange(
                                                Instant.parse("2026-08-13T03:00:00Z"),
                                                Instant.parse("2026-08-14T02:59:59Z")),
                                        1));
                final String request = capturedRequest.get(5, TimeUnit.SECONDS);

                assertTrue(request.startsWith("GET /api/analytics/reports/6908/data HTTP/1.1"));
                assertTrue(request.contains("\"picks\":{\"request_date\""));
                assertTrue(request.contains("\"scopes\":{\"by_updated_at\""));
                assertEquals(1, page.records().size());
                assertEquals("1", page.records().get(0).path("id").asText());
                assertTrue(page.contractObservation().isPresent());
            } finally {
                executor.shutdownNow();
            }
        }
    }

    private static ContractResponsePathBoundary runtimePathBoundary() {
        final SourceContractRelease release = ContractTestSupport.release();
        final ContractCompatibilityPolicy policy = ContractTestSupport.policy(release);
        return ContractResponsePathBoundary.forRuntime(release, policy);
    }

    @Test
    void exposesOnlyTheJsonContentTypeDeclarationInPageDiagnostics() throws Exception {
        try (ServerSocket serverSocket = new ServerSocket(0)) {
            serverSocket.setSoTimeout(5_000);
            final ExecutorService executor = Executors.newSingleThreadExecutor();
            try {
                final Future<String> capturedRequest =
                        executor.submit(() -> receiveAndRespond(serverSocket));
                final HttpDataExportGateway gateway =
                        new HttpDataExportGateway(
                                java.net.http.HttpClient.newHttpClient(),
                                properties(serverSocket, 1_024L),
                                new ObjectMapper(),
                                duration -> {});

                final DataExportPageFetch pageFetch =
                        gateway.fetchWithDiagnostics(requestForColetas());

                assertTrue(pageFetch.jsonContentTypeDeclared());
                assertTrue(
                        capturedRequest
                                .get(5, TimeUnit.SECONDS)
                                .startsWith("GET /api/analytics/reports/6908/data HTTP/1.1"));
            } finally {
                executor.shutdownNow();
            }
        }
    }

    @Test
    void rejectsMoreDistinctEntitiesThanRequestedDirectlyAtTheGateway() throws Exception {
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
                                                "{\"data\":[{\"id\":\"1\"},{\"id\":\"2\"},{\"id\":\"3\"}]}"));
                final DataExportProperties properties =
                        new DataExportProperties(
                                URI.create("http://127.0.0.1:" + serverSocket.getLocalPort()),
                                "test-token",
                                ZoneId.of("America/Sao_Paulo"),
                                Duration.ofSeconds(5),
                                DataExportTransport.GET_WITH_QUERY,
                                new DataExportRetryPolicy(1, Duration.ZERO, Duration.ZERO));
                final HttpDataExportGateway gateway =
                        new HttpDataExportGateway(
                                java.net.http.HttpClient.newHttpClient(),
                                properties,
                                new ObjectMapper(),
                                duration -> {});
                final DataExportPageRequest request =
                        new DataExportPageRequest(
                                DataExportTemplate.COLETAS,
                                new BusinessDateRange(
                                        LocalDate.of(2026, 8, 13), LocalDate.of(2026, 8, 13)),
                                Optional.empty(),
                                1,
                                2,
                                DataExportTemplate.COLETAS.defaultOrderBy());

                final IllegalStateException exception =
                        assertThrows(
                                IllegalStateException.class,
                                () -> gateway.fetchWithDiagnostics(request));

                assertEquals(
                        "O template 6908 retornou 3 entidades distintas na página 1, acima do per solicitado de"
                                + " 2.",
                        exception.getMessage());
                final String httpRequest = capturedRequest.get(5, TimeUnit.SECONDS);
                assertTrue(httpRequest.startsWith("GET /api/analytics/reports/6908/data?"));
                assertTrue(httpRequest.contains("page=1"));
                assertTrue(httpRequest.contains("per=2"));
                assertFalse(httpRequest.contains("Content-Type:"));
            } finally {
                executor.shutdownNow();
            }
        }
    }

    @Test
    void fallsBackToGetQueryAfterGetBodyIsRejected() throws Exception {
        try (ServerSocket serverSocket = new ServerSocket(0)) {
            serverSocket.setSoTimeout(5_000);
            final ExecutorService executor = Executors.newSingleThreadExecutor();
            try {
                final Future<List<String>> capturedRequests =
                        executor.submit(() -> receiveFallbackThenSuccess(serverSocket));
                final DataExportProperties properties =
                        new DataExportProperties(
                                URI.create("http://127.0.0.1:" + serverSocket.getLocalPort()),
                                "test-token",
                                ZoneId.of("America/Sao_Paulo"),
                                Duration.ofSeconds(5),
                                DataExportTransport.GET_WITH_BODY,
                                new DataExportRetryPolicy(1, Duration.ZERO, Duration.ZERO));
                final HttpDataExportGateway gateway =
                        new HttpDataExportGateway(
                                java.net.http.HttpClient.newHttpClient(),
                                properties,
                                new ObjectMapper(),
                                duration -> {},
                                DataExportHttpAttemptObserver.noop(),
                                DataExportTransportFallbackPolicy.ALLOW_COMPATIBLE_FALLBACK);
                final DataExportPageResponse page =
                        gateway.fetch(
                                DataExportPageRequest.forTemplate(
                                        DataExportTemplate.FRETES,
                                        new BusinessDateRange(
                                                LocalDate.of(2026, 8, 13),
                                                LocalDate.of(2026, 8, 13)),
                                        new SourceDateTimeRange(
                                                Instant.parse("2026-08-13T03:00:00Z"),
                                                Instant.parse("2026-08-14T02:59:59Z")),
                                        1));
                final List<String> requests = capturedRequests.get(5, TimeUnit.SECONDS);

                assertEquals(2, requests.size());
                assertTrue(
                        requests.get(0)
                                .startsWith("GET /api/analytics/reports/6389/data HTTP/1.1"));
                assertTrue(requests.get(1).startsWith("GET /api/analytics/reports/6389/data?"));
                assertTrue(requests.get(1).contains("search%5Bscopes%5D%5Bby_updated_at%5D"));
                assertEquals("1", page.records().get(0).path("id").asText());
            } finally {
                executor.shutdownNow();
            }
        }
    }

    @Test
    void usesGetQueryFirstWhenItIsThePreferredTransport() throws Exception {
        try (ServerSocket serverSocket = new ServerSocket(0)) {
            serverSocket.setSoTimeout(5_000);
            final ExecutorService executor = Executors.newSingleThreadExecutor();
            try {
                final Future<String> capturedRequest =
                        executor.submit(() -> receiveAndRespond(serverSocket));
                final DataExportProperties properties =
                        new DataExportProperties(
                                URI.create("http://127.0.0.1:" + serverSocket.getLocalPort()),
                                "test-token",
                                ZoneId.of("America/Sao_Paulo"),
                                Duration.ofSeconds(5),
                                DataExportTransport.GET_WITH_QUERY,
                                new DataExportRetryPolicy(1, Duration.ZERO, Duration.ZERO));
                final HttpDataExportGateway gateway =
                        new HttpDataExportGateway(
                                java.net.http.HttpClient.newHttpClient(),
                                properties,
                                new ObjectMapper(),
                                duration -> {});

                final DataExportPageResponse page = gateway.fetch(requestForColetas());
                final String request = capturedRequest.get(5, TimeUnit.SECONDS);

                assertTrue(request.startsWith("GET /api/analytics/reports/6908/data?"));
                assertFalse(request.contains("Content-Type:"));
                assertEquals("1", page.records().get(0).path("id").asText());
            } finally {
                executor.shutdownNow();
            }
        }
    }

    @Test
    void usesPostFirstWhenItIsThePreferredTransport() throws Exception {
        try (ServerSocket serverSocket = new ServerSocket(0)) {
            serverSocket.setSoTimeout(5_000);
            final ExecutorService executor = Executors.newSingleThreadExecutor();
            try {
                final Future<String> capturedRequest =
                        executor.submit(() -> receiveAndRespond(serverSocket));
                final DataExportProperties properties =
                        new DataExportProperties(
                                URI.create("http://127.0.0.1:" + serverSocket.getLocalPort()),
                                "test-token",
                                ZoneId.of("America/Sao_Paulo"),
                                Duration.ofSeconds(5),
                                DataExportTransport.POST_JSON,
                                new DataExportRetryPolicy(1, Duration.ZERO, Duration.ZERO));
                final HttpDataExportGateway gateway =
                        new HttpDataExportGateway(
                                java.net.http.HttpClient.newHttpClient(),
                                properties,
                                new ObjectMapper(),
                                duration -> {});

                final DataExportPageResponse page = gateway.fetch(requestForColetas());
                final String request = capturedRequest.get(5, TimeUnit.SECONDS);

                assertTrue(request.startsWith("POST /api/analytics/reports/6908/data HTTP/1.1"));
                assertTrue(request.contains("Content-Type: application/json"));
                assertTrue(request.contains("\"page\":\"1\""));
                assertEquals("1", page.records().get(0).path("id").asText());
            } finally {
                executor.shutdownNow();
            }
        }
    }

    @Test
    void fallsBackWhenTheEndpointDoesNotImplementTheCurrentTransport() throws Exception {
        try (ServerSocket serverSocket = new ServerSocket(0)) {
            serverSocket.setSoTimeout(5_000);
            final ExecutorService executor = Executors.newSingleThreadExecutor();
            try {
                final Future<List<String>> capturedRequests =
                        executor.submit(
                                () ->
                                        receiveFallbackThenSuccess(
                                                serverSocket, "501 Not Implemented"));
                final DataExportProperties properties =
                        new DataExportProperties(
                                URI.create("http://127.0.0.1:" + serverSocket.getLocalPort()),
                                "test-token",
                                ZoneId.of("America/Sao_Paulo"),
                                Duration.ofSeconds(5),
                                DataExportTransport.GET_WITH_BODY,
                                new DataExportRetryPolicy(1, Duration.ZERO, Duration.ZERO));
                final HttpDataExportGateway gateway =
                        new HttpDataExportGateway(
                                java.net.http.HttpClient.newHttpClient(),
                                properties,
                                new ObjectMapper(),
                                duration -> {},
                                DataExportHttpAttemptObserver.noop(),
                                DataExportTransportFallbackPolicy.ALLOW_COMPATIBLE_FALLBACK);

                final DataExportPageResponse page =
                        gateway.fetch(
                                DataExportPageRequest.forTemplate(
                                        DataExportTemplate.COLETAS,
                                        new BusinessDateRange(
                                                LocalDate.of(2026, 8, 13),
                                                LocalDate.of(2026, 8, 13)),
                                        null,
                                        1));

                final List<String> requests = capturedRequests.get(5, TimeUnit.SECONDS);
                assertEquals(2, requests.size());
                assertTrue(requests.get(1).startsWith("GET /api/analytics/reports/6908/data?"));
                assertEquals("1", page.records().get(0).path("id").asText());
            } finally {
                executor.shutdownNow();
            }
        }
    }

    @Test
    void stopsAfterTheThreeBoundedTransportAlternativesAreRejected() throws Exception {
        try (ServerSocket serverSocket = new ServerSocket(0)) {
            serverSocket.setSoTimeout(5_000);
            final ExecutorService executor = Executors.newSingleThreadExecutor();
            try {
                final Future<List<String>> capturedRequests =
                        executor.submit(() -> receiveRejectedTransports(serverSocket));
                final HttpDataExportGateway gateway =
                        new HttpDataExportGateway(
                                java.net.http.HttpClient.newHttpClient(),
                                properties(serverSocket, 1_024L),
                                new ObjectMapper(),
                                duration -> {},
                                DataExportHttpAttemptObserver.noop(),
                                DataExportTransportFallbackPolicy.ALLOW_COMPATIBLE_FALLBACK);

                final IllegalStateException exception =
                        assertThrows(
                                IllegalStateException.class,
                                () -> gateway.fetch(requestForColetas()));
                final List<String> requests = capturedRequests.get(5, TimeUnit.SECONDS);

                assertEquals(3, requests.size());
                assertTrue(
                        requests.get(0)
                                .startsWith("GET /api/analytics/reports/6908/data HTTP/1.1"));
                assertTrue(requests.get(1).startsWith("GET /api/analytics/reports/6908/data?"));
                assertTrue(
                        requests.get(2)
                                .startsWith("POST /api/analytics/reports/6908/data HTTP/1.1"));
                assertEquals(
                        "Nenhum transporte Data Export foi aceito para o template 6908. Último HTTP: 415.",
                        exception.getMessage());
            } finally {
                executor.shutdownNow();
            }
        }
    }

    @Test
    void doesNotFallbackForABadRequestThatMayIndicateAContractError() throws Exception {
        try (ServerSocket serverSocket = new ServerSocket(0)) {
            serverSocket.setSoTimeout(5_000);
            final ExecutorService executor = Executors.newSingleThreadExecutor();
            try {
                final Future<String> capturedRequest =
                        executor.submit(
                                () -> receiveAndClose(serverSocket, "400 Bad Request", "{}"));
                final DataExportProperties properties =
                        new DataExportProperties(
                                URI.create("http://127.0.0.1:" + serverSocket.getLocalPort()),
                                "test-token",
                                ZoneId.of("America/Sao_Paulo"),
                                Duration.ofMillis(500),
                                DataExportTransport.GET_WITH_BODY,
                                new DataExportRetryPolicy(1, Duration.ZERO, Duration.ZERO));
                final HttpDataExportGateway gateway =
                        new HttpDataExportGateway(
                                java.net.http.HttpClient.newHttpClient(),
                                properties,
                                new ObjectMapper(),
                                duration -> {});

                final IllegalStateException exception =
                        assertThrows(
                                IllegalStateException.class,
                                () ->
                                        gateway.fetch(
                                                DataExportPageRequest.forTemplate(
                                                        DataExportTemplate.COLETAS,
                                                        new BusinessDateRange(
                                                                LocalDate.of(2026, 8, 13),
                                                                LocalDate.of(2026, 8, 13)),
                                                        null,
                                                        1)));

                assertEquals(IllegalStateException.class, exception.getClass());
                assertEquals(
                        "Data Export retornou HTTP 400 para o template 6908.",
                        exception.getMessage());
                assertTrue(
                        capturedRequest
                                .get(5, TimeUnit.SECONDS)
                                .startsWith("GET /api/analytics/reports/6908/data HTTP/1.1"));
            } finally {
                executor.shutdownNow();
            }
        }
    }

    @Test
    void doesNotTryAnAlternativeTransportByDefault() throws Exception {
        try (ServerSocket serverSocket = new ServerSocket(0)) {
            serverSocket.setSoTimeout(5_000);
            final ExecutorService executor = Executors.newSingleThreadExecutor();
            try {
                final List<DataExportHttpAttempt> observedAttempts = new ArrayList<>();
                final Future<String> capturedRequest =
                        executor.submit(
                                () ->
                                        receiveAndRespond(
                                                serverSocket, "405 Method Not Allowed", "{}"));
                final HttpDataExportGateway gateway =
                        new HttpDataExportGateway(
                                java.net.http.HttpClient.newHttpClient(),
                                properties(serverSocket, 1_024L),
                                new ObjectMapper(),
                                duration -> {},
                                observedAttempts::add);

                assertThrows(IllegalStateException.class, () -> gateway.fetch(requestForColetas()));
                assertTrue(
                        capturedRequest
                                .get(5, TimeUnit.SECONDS)
                                .startsWith("GET /api/analytics/reports/6908/data HTTP/1.1"));
                assertEquals(
                        List.of(new DataExportHttpAttempt(6908, "data-GET_WITH_BODY")),
                        observedAttempts);
            } finally {
                executor.shutdownNow();
            }
        }
    }

    @Test
    void rejectsAResponseAboveTheDeclaredByteLimitBeforeJsonParsing() throws Exception {
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
                                                "{\"data\":{\"id\":\"1\"}}"));
                final HttpDataExportGateway gateway =
                        new HttpDataExportGateway(
                                java.net.http.HttpClient.newHttpClient(),
                                properties(serverSocket, 8L),
                                new ObjectMapper(),
                                duration -> {});

                final DataExportResponseLimitExceededException exception =
                        assertThrows(
                                DataExportResponseLimitExceededException.class,
                                () -> gateway.fetch(requestForColetas()));

                assertEquals(8L, exception.maxResponseBytes());
                assertTrue(
                        capturedRequest
                                .get(5, TimeUnit.SECONDS)
                                .startsWith("GET /api/analytics/reports/6908/data HTTP/1.1"));
            } finally {
                executor.shutdownNow();
            }
        }
    }

    @Test
    void rejectsAnOversizedChunkedResponseBeforeJsonParsing() throws Exception {
        try (ServerSocket serverSocket = new ServerSocket(0)) {
            serverSocket.setSoTimeout(5_000);
            final ExecutorService executor = Executors.newSingleThreadExecutor();
            try {
                final Future<String> capturedRequest =
                        executor.submit(
                                () ->
                                        receiveAndRespondChunked(
                                                serverSocket, "{\"data\":{\"id\":\"1\"}}"));
                final HttpDataExportGateway gateway =
                        new HttpDataExportGateway(
                                java.net.http.HttpClient.newHttpClient(),
                                properties(serverSocket, 8L),
                                new ObjectMapper(),
                                duration -> {});

                assertThrows(
                        DataExportResponseLimitExceededException.class,
                        () -> gateway.fetch(requestForColetas()));
                assertTrue(
                        capturedRequest
                                .get(5, TimeUnit.SECONDS)
                                .startsWith("GET /api/analytics/reports/6908/data HTTP/1.1"));
            } finally {
                executor.shutdownNow();
            }
        }
    }

    @Test
    void rejectsNoContentBecauseItIsNotAnExtractableJsonPage() throws Exception {
        try (ServerSocket serverSocket = new ServerSocket(0)) {
            serverSocket.setSoTimeout(5_000);
            final ExecutorService executor = Executors.newSingleThreadExecutor();
            try {
                final Future<String> capturedRequest =
                        executor.submit(
                                () -> receiveAndRespond(serverSocket, "204 No Content", ""));
                final HttpDataExportGateway gateway =
                        new HttpDataExportGateway(
                                java.net.http.HttpClient.newHttpClient(),
                                properties(serverSocket, 1_024L),
                                new ObjectMapper(),
                                duration -> {});

                final IllegalStateException exception =
                        assertThrows(
                                IllegalStateException.class,
                                () -> gateway.fetch(requestForColetas()));

                assertEquals(
                        "Data Export retornou HTTP 204 sem uma página JSON para o template 6908.",
                        exception.getMessage());
                assertTrue(
                        capturedRequest
                                .get(5, TimeUnit.SECONDS)
                                .startsWith("GET /api/analytics/reports/6908/data HTTP/1.1"));
            } finally {
                executor.shutdownNow();
            }
        }
    }

    @Test
    void rejectsAnEmptySuccessBodyBeforeItCanBecomeARecord() throws Exception {
        try (ServerSocket serverSocket = new ServerSocket(0)) {
            serverSocket.setSoTimeout(5_000);
            final ExecutorService executor = Executors.newSingleThreadExecutor();
            try {
                final Future<String> capturedRequest =
                        executor.submit(() -> receiveAndRespond(serverSocket, "200 OK", ""));
                final HttpDataExportGateway gateway =
                        new HttpDataExportGateway(
                                java.net.http.HttpClient.newHttpClient(),
                                properties(serverSocket, 1_024L),
                                new ObjectMapper(),
                                duration -> {});

                final IllegalStateException exception =
                        assertThrows(
                                IllegalStateException.class,
                                () -> gateway.fetch(requestForColetas()));

                assertEquals(
                        "Resposta JSON vazia no template Data Export 6908.",
                        exception.getMessage());
                assertTrue(
                        capturedRequest
                                .get(5, TimeUnit.SECONDS)
                                .startsWith("GET /api/analytics/reports/6908/data HTTP/1.1"));
            } finally {
                executor.shutdownNow();
            }
        }
    }

    @Test
    void sanitizesMalformedJsonWithoutRetainingTheResponsePayload() throws Exception {
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
                                                "{\"secret\":\"payload-value\""));
                final HttpDataExportGateway gateway =
                        new HttpDataExportGateway(
                                java.net.http.HttpClient.newHttpClient(),
                                properties(serverSocket, 1_024L),
                                new ObjectMapper(),
                                duration -> {});

                final IllegalStateException exception =
                        assertThrows(
                                IllegalStateException.class,
                                () -> gateway.fetch(requestForColetas()));

                assertEquals(
                        "Resposta JSON inválida no template Data Export 6908.",
                        exception.getMessage());
                assertNull(exception.getCause());
                assertFalse(exception.toString().contains("payload-value"));
                assertTrue(
                        capturedRequest
                                .get(5, TimeUnit.SECONDS)
                                .startsWith("GET /api/analytics/reports/6908/data HTTP/1.1"));
            } finally {
                executor.shutdownNow();
            }
        }
    }

    @Test
    void rejectsMalformedUtf8WithoutReplacementOrRetainedBytes() throws Exception {
        try (ServerSocket serverSocket = new ServerSocket(0)) {
            serverSocket.setSoTimeout(5_000);
            final ExecutorService executor = Executors.newSingleThreadExecutor();
            try {
                final byte[] malformed =
                        new byte[] {'{', '"', 'i', 'd', '"', ':', '"', (byte) 0xC3, '(', '"', '}'};
                final Future<String> capturedRequest =
                        executor.submit(() -> receiveAndRespondBytes(serverSocket, malformed));
                final HttpDataExportGateway gateway =
                        new HttpDataExportGateway(
                                java.net.http.HttpClient.newHttpClient(),
                                properties(serverSocket, 1_024L),
                                new ObjectMapper(),
                                duration -> {});

                final IllegalStateException exception =
                        assertThrows(
                                IllegalStateException.class,
                                () -> gateway.fetch(requestForColetas()));

                assertEquals(
                        "Data Export retornou UTF-8 inválido para o template 6908.",
                        exception.getMessage());
                assertNull(exception.getCause());
                assertFalse(exception.toString().contains("\uFFFD"));
                assertTrue(
                        capturedRequest
                                .get(5, TimeUnit.SECONDS)
                                .startsWith("GET /api/analytics/reports/6908/data HTTP/1.1"));
            } finally {
                executor.shutdownNow();
            }
        }
    }

    @Test
    void classifiesExhaustedTransientHttpFailureAsUnavailable() throws Exception {
        try (ServerSocket serverSocket = new ServerSocket(0)) {
            serverSocket.setSoTimeout(5_000);
            final ExecutorService executor = Executors.newSingleThreadExecutor();
            try {
                final Future<String> capturedRequest =
                        executor.submit(
                                () ->
                                        receiveAndRespond(
                                                serverSocket, "503 Service Unavailable", "{}"));
                final DataExportProperties properties =
                        new DataExportProperties(
                                URI.create("http://127.0.0.1:" + serverSocket.getLocalPort()),
                                "test-token",
                                ZoneId.of("America/Sao_Paulo"),
                                Duration.ofSeconds(5),
                                DataExportTransport.GET_WITH_BODY,
                                new DataExportRetryPolicy(1, Duration.ZERO, Duration.ZERO));
                final HttpDataExportGateway gateway =
                        new HttpDataExportGateway(
                                java.net.http.HttpClient.newHttpClient(),
                                properties,
                                new ObjectMapper(),
                                duration -> {});

                assertThrows(
                        DataExportUnavailableException.class,
                        () ->
                                gateway.fetch(
                                        DataExportPageRequest.forTemplate(
                                                DataExportTemplate.COLETAS,
                                                new BusinessDateRange(
                                                        LocalDate.of(2026, 8, 13),
                                                        LocalDate.of(2026, 8, 13)),
                                                null,
                                                1)));

                assertTrue(
                        capturedRequest
                                .get(5, TimeUnit.SECONDS)
                                .startsWith("GET /api/analytics/reports/6908/data HTTP/1.1"));
            } finally {
                executor.shutdownNow();
            }
        }
    }

    private static String receiveAndRespond(final ServerSocket serverSocket) throws IOException {
        return receiveAndRespond(serverSocket, "200 OK", "{\"data\":[{\"id\":\"1\"}]}");
    }

    private static String receiveAndRespond(
            final ServerSocket serverSocket, final String status, final String responseBody)
            throws IOException {
        try (Socket socket = serverSocket.accept()) {
            final String request = readRequest(socket);
            writeResponse(socket, status, responseBody);
            return request;
        }
    }

    private static String receiveAndClose(
            final ServerSocket serverSocket, final String status, final String responseBody)
            throws IOException {
        try {
            return receiveAndRespond(serverSocket, status, responseBody);
        } finally {
            serverSocket.close();
        }
    }

    private static String receiveAndRespondChunked(
            final ServerSocket serverSocket, final String responseBody) throws IOException {
        try (Socket socket = serverSocket.accept()) {
            final String request = readRequest(socket);
            final byte[] responseBytes = responseBody.getBytes(StandardCharsets.UTF_8);
            final OutputStreamWriter writer =
                    new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8);
            writer.write("HTTP/1.1 200 OK\r\n");
            writer.write("Content-Type: application/json\r\n");
            writer.write("Transfer-Encoding: chunked\r\n");
            writer.write("Connection: close\r\n\r\n");
            writer.write(Integer.toHexString(responseBytes.length));
            writer.write("\r\n");
            writer.write(responseBody);
            writer.write("\r\n0\r\n\r\n");
            writer.flush();
            return request;
        }
    }

    private static String receiveAndRespondBytes(
            final ServerSocket serverSocket, final byte[] responseBody) throws IOException {
        try (Socket socket = serverSocket.accept()) {
            final String request = readRequest(socket);
            final String headers =
                    "HTTP/1.1 200 OK\r\n"
                            + "Content-Type: application/json\r\n"
                            + "Content-Length: "
                            + responseBody.length
                            + "\r\nConnection: close\r\n\r\n";
            final OutputStream output = socket.getOutputStream();
            output.write(headers.getBytes(StandardCharsets.US_ASCII));
            output.write(responseBody);
            output.flush();
            return request;
        }
    }

    private static List<String> receiveFallbackThenSuccess(final ServerSocket serverSocket)
            throws IOException {
        return receiveFallbackThenSuccess(serverSocket, "405 Method Not Allowed");
    }

    private static List<String> receiveFallbackThenSuccess(
            final ServerSocket serverSocket, final String rejectedStatus) throws IOException {
        final List<String> requests = new ArrayList<>();
        try (Socket firstSocket = serverSocket.accept()) {
            requests.add(readRequest(firstSocket));
            writeResponse(firstSocket, rejectedStatus, "{}");
        }
        try (Socket secondSocket = serverSocket.accept()) {
            requests.add(readRequest(secondSocket));
            writeResponse(secondSocket, "200 OK", "{\"data\":{\"id\":\"1\"}}");
        }
        return List.copyOf(requests);
    }

    private static List<String> receiveRejectedTransports(final ServerSocket serverSocket)
            throws IOException {
        final List<String> requests = new ArrayList<>();
        for (final String status :
                List.of(
                        "405 Method Not Allowed",
                        "411 Length Required",
                        "415 Unsupported Media Type")) {
            try (Socket socket = serverSocket.accept()) {
                requests.add(readRequest(socket));
                writeResponse(socket, status, "{}");
            }
        }
        return List.copyOf(requests);
    }

    private static DataExportProperties properties(
            final ServerSocket serverSocket, final long maxResponseBytes) {
        return new DataExportProperties(
                URI.create("http://127.0.0.1:" + serverSocket.getLocalPort()),
                "test-token",
                ZoneId.of("America/Sao_Paulo"),
                Duration.ofSeconds(5),
                DataExportTransport.GET_WITH_BODY,
                new DataExportRetryPolicy(1, Duration.ZERO, Duration.ZERO),
                maxResponseBytes);
    }

    private static DataExportPageRequest requestForColetas() {
        return DataExportPageRequest.forTemplate(
                DataExportTemplate.COLETAS,
                new BusinessDateRange(LocalDate.of(2026, 8, 13), LocalDate.of(2026, 8, 13)),
                null,
                1);
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
        if (contentLength > 0) {
            final char[] body = new char[contentLength];
            int offset = 0;
            while (offset < contentLength) {
                final int charactersRead = reader.read(body, offset, contentLength - offset);
                if (charactersRead < 0) {
                    break;
                }
                offset += charactersRead;
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
}
