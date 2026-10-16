package br.com.esl.etl.v2.plataforma.fonte.dataexport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.plataforma.configuracao.DataExportProperties;
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Flow;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

class HttpDataExportObservationClientTest {
    @ParameterizedTest
    @EnumSource(
            value = DataExportTemplate.class,
            names = {"COLETAS", "FRETES"},
            mode = EnumSource.Mode.EXCLUDE)
    void infoOnlyRoutesCannotInventRemotePerFromCandidateRoot(final DataExportTemplate template) {
        final var http = new FakeClient(physicalInfo(template), "{\"data\":[]}");
        assertEquals(
                "ROOT_PER_UNVERIFIED",
                assertThrows(
                                IllegalArgumentException.class,
                                () ->
                                        client(http, 4096)
                                                .observe(
                                                        DataExportObservedPageTest.request(
                                                                template)))
                        .getMessage());
        assertEquals(0, http.calls);
    }

    @ParameterizedTest
    @EnumSource(DataExportTemplate.class)
    void realExecutorAndInfoGatewayComposeAllNineRoutes(final DataExportTemplate template) {
        final var http =
                new FakeClient(
                        "{\"fields\":[\"id\"],\"filters\":[\""
                                + template.metadataBusinessDateFilterName()
                                + "\"]}",
                        "[]");
        final var gateway =
                new HttpDataExportTemplateInfoGateway(
                        http,
                        settings(4096, Duration.ofSeconds(1)).source(),
                        new com.fasterxml.jackson.databind.ObjectMapper(),
                        delay -> {
                            throw new AssertionError("NO_RETRY_OR_FALLBACK");
                        });
        final var info = gateway.fetchInfo(template);
        assertEquals(template, info.template());
        assertEquals(1, info.fields().size());
        assertEquals(1, http.calls);
        assertTrue(
                http.requests
                        .get(0)
                        .uri()
                        .getPath()
                        .endsWith("/" + template.templateId() + "/info"));
    }

    @ParameterizedTest
    @EnumSource(
            value = DataExportTemplate.class,
            names = {"COLETAS", "FRETES"})
    void realExecutorAndDataGatewayKeepIdPerAndStopContracts(final DataExportTemplate template) {
        final var day = java.time.LocalDate.of(2026, 10, 1);
        final var request =
                new DataExportPageRequest(
                        template,
                        new BusinessDateRange(day, day),
                        Optional.empty(),
                        1,
                        1,
                        template.defaultOrderBy());
        final var expanded = new FakeClient("{\"data\":[{\"id\":1},{\"id\":1}]}", "[]");
        final var result = dataGateway(expanded).fetchWithDiagnostics(request);
        assertEquals(2, result.response().recordCount());
        assertEquals(DataExportTransport.GET_WITH_QUERY, result.acceptedTransport());
        assertEquals(1, expanded.calls);
        assertTrue(expanded.requests.get(0).uri().getRawQuery().contains("page=1&per=1"));
        for (final var body :
                List.of(
                        "{\"data\":[{\"id\":null}]}",
                        "{\"data\":[{\"id\":1},{\"id\":2}]}",
                        "{\"data\":[{\"id\":1},")) {
            final var bad = new FakeClient(body, "[]");
            assertThrows(RuntimeException.class, () -> dataGateway(bad).fetch(request));
            assertEquals(1, bad.calls);
        }
        for (final int status : List.of(204, 301, 401, 429, 500)) {
            final var stopped = new FakeClient("{\"data\":[]}", "[]");
            stopped.status = status;
            assertThrows(RuntimeException.class, () -> dataGateway(stopped).fetch(request));
            assertEquals(1, stopped.calls);
        }
    }

    private static HttpDataExportGateway dataGateway(final FakeClient http) {
        return new HttpDataExportGateway(
                http,
                settings(4096, Duration.ofSeconds(1)).source(),
                new com.fasterxml.jackson.databind.ObjectMapper(),
                delay -> {
                    throw new AssertionError("NO_RETRY_OR_FALLBACK");
                });
    }

    @ParameterizedTest
    @EnumSource(DataExportTemplate.class)
    void closedWindowRefusesAllNineRoutesBeforeLazySource(final DataExportTemplate template) {
        final var reads = new java.util.concurrent.atomic.AtomicInteger();
        assertEquals(
                "SOURCE_WINDOW_CLOSED",
                assertThrows(
                                IllegalStateException.class,
                                () ->
                                        HttpDataExportObservationClient.createForSourceWindow(
                                                () -> false,
                                                () -> {
                                                    reads.incrementAndGet();
                                                    throw new AssertionError(template.name());
                                                }))
                        .getMessage());
        assertEquals(0, reads.get());
    }

    @Test
    void missingOrUnknownWindowCannotBecomeBlindProbe() {
        assertEquals(
                "SOURCE_WINDOW_CLOSED",
                assertThrows(
                                IllegalStateException.class,
                                () ->
                                        HttpDataExportObservationClient.createForSourceWindow(
                                                null, null))
                        .getMessage());
        assertEquals(
                "SOURCE_WINDOW_CLOSED",
                assertThrows(
                                IllegalStateException.class,
                                () ->
                                        HttpDataExportObservationClient.createForSourceWindow(
                                                () -> {
                                                    throw new IllegalStateException(
                                                            "synthetic-private-detail");
                                                },
                                                null))
                        .getMessage());
    }

    @Test
    void revokedWindowStopsBeforeInfoOrData() {
        final var open = new java.util.concurrent.atomic.AtomicBoolean(true);
        final var transport = new FakeClient(INFO, "{\"data\":[]}");
        final var client =
                new HttpDataExportObservationClient(
                        settings(4096, Duration.ofSeconds(1)),
                        transport,
                        HttpDataExportObservationClientTest::policy,
                        () -> transport.now,
                        transport::waitFor,
                        open::get);
        open.set(false);
        assertThrows(
                IllegalStateException.class,
                () ->
                        client.observe(
                                DataExportObservedPageTest.request(DataExportTemplate.FRETES)));
        assertEquals(0, transport.calls);

        open.set(true);
        final var between = new FakeClient(INFO, "{\"data\":[]}");
        final var second =
                new HttpDataExportObservationClient(
                        settings(4096, Duration.ofSeconds(1)),
                        between,
                        HttpDataExportObservationClientTest::policy,
                        () -> between.now,
                        delay -> {
                            between.waitFor(delay);
                            open.set(false);
                        },
                        open::get);
        assertThrows(
                IllegalStateException.class,
                () ->
                        second.observe(
                                DataExportObservedPageTest.request(DataExportTemplate.FRETES)));
        assertEquals(1, between.calls);
    }

    @Test
    void legacyPublicEntryCannotBypassClosedWindow() {
        assertEquals(
                "SOURCE_WINDOW_CLOSED",
                assertThrows(
                                IllegalStateException.class,
                                () -> new HttpDataExportObservationClient(null))
                        .getMessage());
    }

    private static final String INFO =
            "{\"fields\":[\"id\"],\"filters\":[\"freights.service_at\"]}";

    @Test
    void capHasExplicitMetadataOnlyOperationWithNoAutomaticDataOrIdentityClaim() throws Exception {
        final var transport =
                new FakeClient(
                        "{\"fields\":[\"ant_ils_sequence_code\"],\"filters\":[\"accounting_debits.issue_date\"]}",
                        "[]");
        final var client = client(transport, 4096);
        final var result = client.observeMetadata(DataExportTemplate.CONTAS_A_PAGAR);
        assertEquals(1, transport.calls);
        assertEquals(1, result.calls());
        assertEquals(200, result.httpStatus());
        assertEquals(1, result.metadata().fields().size());
        assertFalse(result.canonicalIdentityProven());
        assertFalse(result.paginationRootProven());
        assertTrue(transport.requests.get(0).uri().getPath().endsWith("/8636/info"));
        assertThrows(
                HttpDataExportObservationClient.Failure.class,
                () -> client.observeMetadata(DataExportTemplate.FRETES));
        assertEquals(1, transport.calls);
    }

    @Test
    void sourceRunsExactlyInfoAndFirstPageOnce() throws Exception {
        final var transport = new FakeClient(INFO, "{\"data\":[{\"id\":1},{\"id\":1}]}");
        final var client = client(transport, 4096);
        final var result =
                client.observe(DataExportObservedPageTest.request(DataExportTemplate.FRETES));
        assertEquals(2, result.calls());
        assertEquals(2, result.physicalRows());
        assertEquals(1, result.distinctRoots());
        assertEquals(200, result.infoStatus());
        assertEquals(200, result.dataStatus());
        assertEquals(2, transport.calls);
        assertEquals("GET", transport.requests.get(0).method());
        assertTrue(transport.requests.get(0).uri().getPath().endsWith("/6389/info"));
        assertTrue(transport.requests.get(1).uri().getRawQuery().contains("page=1&per=1"));
        assertThrows(
                HttpDataExportObservationClient.Failure.class,
                () ->
                        client.observe(
                                DataExportObservedPageTest.request(DataExportTemplate.FRETES)));
        assertEquals(2, transport.calls);
    }

    @ParameterizedTest
    @ValueSource(ints = {204, 301, 401, 429, 500})
    void firstNonSuccessStopsBeforeNextCall(final int status) {
        final var transport = new FakeClient(INFO, "fixture-sensitive");
        transport.status = status;
        final var failure =
                assertThrows(
                        HttpDataExportObservationClient.Failure.class,
                        () ->
                                client(transport, 4096)
                                        .observe(
                                                DataExportObservedPageTest.request(
                                                        DataExportTemplate.FRETES)));
        assertEquals("OBS_HTTP_STATUS", failure.code());
        assertEquals(status, failure.httpStatus());
        assertEquals(1, transport.calls);
        assertNull(failure.getCause());
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "{}",
                "{\"fields\":[\"other\"],\"filters\":[\"freights.service_at\"]}",
                "{\"fields\":[\"id\"],\"filters\":[]}",
                "{\"fields\":[],\"fields\":[]}",
                "{\"fields\":[\"id\"]} true",
                "{\"error\":\"fixture-secret\"}"
            })
    void invalidMetadataNeverReachesPage(final String info) {
        final var transport = new FakeClient(info, "[]");
        final var failure =
                assertThrows(
                        HttpDataExportObservationClient.Failure.class,
                        () ->
                                client(transport, 4096)
                                        .observe(
                                                DataExportObservedPageTest.request(
                                                        DataExportTemplate.FRETES)));
        assertEquals(1, transport.calls);
        assertNull(failure.getCause());
        assertFalse(failure.toString().contains("fixture-secret"));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "{\"data\":[{}]}",
                "{\"data\":[{\"id\":1},{\"id\":2}]}",
                "{\"data\":[{\"id\":1,\"id\":1}]}",
                "[] false",
                "{\"data\":[null]}",
                "{\"error\":\"fixture-secret\"}"
            })
    void invalidPageNeverReturnsAnObservation(final String page) {
        final var transport = new FakeClient(INFO, page);
        final var failure =
                assertThrows(
                        HttpDataExportObservationClient.Failure.class,
                        () ->
                                client(transport, 4096)
                                        .observe(
                                                DataExportObservedPageTest.request(
                                                        DataExportTemplate.FRETES)));
        assertEquals(2, transport.calls);
        assertNull(failure.getCause());
    }

    @Test
    void capCannotDispatchUsingOccurrenceOrFinancialChildKey() {
        final var transport = new FakeClient(INFO, "[]");
        assertEquals(
                "ROOT_PER_UNVERIFIED",
                assertThrows(
                                IllegalArgumentException.class,
                                () ->
                                        client(transport, 4096)
                                                .observe(
                                                        DataExportObservedPageTest.request(
                                                                DataExportTemplate.CONTAS_A_PAGAR)))
                        .getMessage());
        assertEquals(0, transport.calls);
    }

    @Test
    void bodyLimitCancelsAndMediaTypeIsRequired() {
        final var transport = new FakeClient(INFO, "[]");
        final var failure =
                assertThrows(
                        HttpDataExportObservationClient.Failure.class,
                        () ->
                                client(transport, 20)
                                        .observe(
                                                DataExportObservedPageTest.request(
                                                        DataExportTemplate.FRETES)));
        assertEquals("OBS_HTTP_BODY_BOUND", failure.code());
        assertTrue(transport.cancelled);
        final var wrong = new FakeClient(INFO, "[]");
        wrong.headers = Map.of("Content-Type", List.of("text/plain"));
        assertEquals(
                "OBS_HTTP_MEDIA_TYPE",
                assertThrows(
                                HttpDataExportObservationClient.Failure.class,
                                () ->
                                        client(wrong, 4096)
                                                .observe(
                                                        DataExportObservedPageTest.request(
                                                                DataExportTemplate.FRETES)))
                        .code());
    }

    @ParameterizedTest
    @ValueSource(strings = {"-1", "invalid", "1,2", "999999999999999999999", "5000"})
    void invalidDeclaredLengthIsRejectedWithoutRetry(final String length) {
        final var transport = new FakeClient(INFO, "[]");
        transport.headers =
                Map.of(
                        "Content-Type",
                        List.of("application/json"),
                        "Content-Length",
                        List.of(length));
        assertThrows(
                HttpDataExportObservationClient.Failure.class,
                () ->
                        client(transport, 4096)
                                .observe(
                                        DataExportObservedPageTest.request(
                                                DataExportTemplate.FRETES)));
        assertEquals(1, transport.calls);
    }

    @Test
    void timeoutCancelsAndTransportErrorsDoNotLeak() {
        final var transport = new FakeClient(INFO, "[]");
        transport.pending = true;
        final var client =
                new HttpDataExportObservationClient(
                        settings(4096, Duration.ofMillis(20)),
                        transport,
                        HttpDataExportObservationClientTest::policy,
                        () -> transport.now,
                        transport::waitFor);
        assertEquals(
                "OBS_HTTP_DEADLINE",
                assertThrows(
                                HttpDataExportObservationClient.Failure.class,
                                () ->
                                        client.observe(
                                                DataExportObservedPageTest.request(
                                                        DataExportTemplate.FRETES)))
                        .code());
        assertTrue(transport.future.isCancelled());
        assertEquals(1, transport.calls);
        final var error = new FakeClient(INFO, "[]");
        error.failure = new IllegalStateException("fixture-sensitive");
        final var observed =
                assertThrows(
                        HttpDataExportObservationClient.Failure.class,
                        () ->
                                client(error, 4096)
                                        .observe(
                                                DataExportObservedPageTest.request(
                                                        DataExportTemplate.FRETES)));
        assertNull(observed.getCause());
        assertFalse(observed.toString().contains("fixture-sensitive"));
    }

    @Test
    void malformedUtf8IsRejectedBeforeMetadataOrPayloadCanEscape() {
        final var transport = new FakeClient(INFO, "[]");
        transport.bytes = new byte[] {(byte) 0xc3, (byte) 0x28};
        assertEquals(
                "OBS_METADATA_INVALID",
                assertThrows(
                                HttpDataExportObservationClient.Failure.class,
                                () ->
                                        client(transport, 4096)
                                                .observe(
                                                        DataExportObservedPageTest.request(
                                                                DataExportTemplate.FRETES)))
                        .code());
        assertEquals(1, transport.calls);
    }

    @Test
    void jdkRetryFlagsAndTlsAreCheckedBeforeUse() {
        for (final var key :
                List.of(
                        "jdk.httpclient.disableRetryConnect",
                        "jdk.httpclient.enableAllMethodRetry",
                        "jdk.httpclient.redirects.retrylimit")) {
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            new HttpDataExportObservationClient(
                                    settings(4096, Duration.ofSeconds(1)),
                                    new FakeClient(INFO, "[]"),
                                    name -> name.equals(key) ? null : policy(name),
                                    () -> 0L,
                                    delay -> {
                                        throw new AssertionError("UNEXPECTED_WAIT");
                                    }));
        }
        final var settings = settings(4096, Duration.ofSeconds(1));
        assertFalse(settings.toString().contains("example.invalid"));
        if (!"true".equals(System.getProperty("jdk.httpclient.disableRetryConnect"))) {
            assertThrows(
                    IllegalStateException.class,
                    () -> new HttpDataExportObservationClient(settings));
        }
        assertThrows(
                IllegalArgumentException.class,
                () -> new DataExportObservationSettings(false, settings.source(), 100));
        assertThrows(
                IllegalArgumentException.class,
                () -> new DataExportObservationSettings(true, settings.source(), 101));
    }

    static DataExportObservationSettings settings(final int bytes, final Duration timeout) {
        return new DataExportObservationSettings(
                true,
                new DataExportProperties(
                        URI.create("https://example.invalid"),
                        "synthetic",
                        ZoneId.of("America/Sao_Paulo"),
                        timeout,
                        DataExportTransport.GET_WITH_QUERY,
                        new DataExportRetryPolicy(1, Duration.ZERO, Duration.ZERO),
                        bytes),
                100);
    }

    static HttpDataExportObservationClient client(final FakeClient transport, final int bytes) {
        return new HttpDataExportObservationClient(
                settings(bytes, Duration.ofSeconds(1)),
                transport,
                HttpDataExportObservationClientTest::policy,
                () -> transport.now,
                transport::waitFor);
    }

    @Test
    void dataStartsOnlyAfterTwoSecondsFollowingInfoCompletionWithoutRealSleep() throws Exception {
        final var transport = new FakeClient(INFO, "{\"data\":[{\"id\":1}]}");
        transport.infoDuration = Duration.ofMillis(800).toNanos();
        client(transport, 4096)
                .observe(DataExportObservedPageTest.request(DataExportTemplate.FRETES));
        assertEquals(List.of(0L, Duration.ofMillis(2800).toNanos()), transport.starts);
        assertEquals(1, transport.waits);
        assertEquals(Duration.ofSeconds(2).toNanos(), transport.waited);
        assertEquals(2, transport.calls);
    }

    @Test
    void absentDeadlineMarginStopsBeforeWaitOrSecondCall() {
        final var transport = new FakeClient(INFO, "[]");
        transport.infoDuration = Duration.ofSeconds(59).toNanos();
        final var client = client(transport, 4096);
        assertEquals(
                "OBS_UNIT_DEADLINE",
                assertThrows(
                                HttpDataExportObservationClient.Failure.class,
                                () ->
                                        client.observe(
                                                DataExportObservedPageTest.request(
                                                        DataExportTemplate.FRETES)))
                        .code());
        assertEquals(0, transport.waits);
        assertEquals(1, transport.calls);
        assertEquals(
                "OBS_ALREADY_USED",
                assertThrows(
                                HttpDataExportObservationClient.Failure.class,
                                () -> client.observeMetadata(DataExportTemplate.FRETES))
                        .code());
    }

    @Test
    void earlyWakeAndOversleepCannotDispatchData() {
        final var early = new FakeClient(INFO, "[]");
        early.waitAdjustment = -1;
        assertEquals(
                "OBS_INTERVAL_NOT_ELAPSED",
                assertThrows(
                                HttpDataExportObservationClient.Failure.class,
                                () ->
                                        client(early, 4096)
                                                .observe(
                                                        DataExportObservedPageTest.request(
                                                                DataExportTemplate.FRETES)))
                        .code());
        assertEquals(1, early.calls);
        final var late = new FakeClient(INFO, "[]");
        late.waitAdjustment = Duration.ofSeconds(60).toNanos();
        assertEquals(
                "OBS_UNIT_DEADLINE",
                assertThrows(
                                HttpDataExportObservationClient.Failure.class,
                                () ->
                                        client(late, 4096)
                                                .observe(
                                                        DataExportObservedPageTest.request(
                                                                DataExportTemplate.FRETES)))
                        .code());
        assertEquals(1, late.calls);
    }

    @Test
    void secondRequestTimeoutUsesRemainingUnitBudgetAndInfoOnlyDoesNotWait() throws Exception {
        final var transport = new FakeClient(INFO, "[]");
        transport.infoDuration = Duration.ofSeconds(30).toNanos();
        final var client =
                new HttpDataExportObservationClient(
                        settings(4096, Duration.ofSeconds(30)),
                        transport,
                        HttpDataExportObservationClientTest::policy,
                        () -> transport.now,
                        transport::waitFor);
        client.observe(DataExportObservedPageTest.request(DataExportTemplate.FRETES));
        assertEquals(Duration.ofSeconds(28), transport.requests.get(1).timeout().orElseThrow());
        final var info = new FakeClient(INFO, "[]");
        assertEquals(
                1, client(info, 4096).observeMetadata(DataExportTemplate.CONTAS_A_PAGAR).calls());
        assertEquals(1, info.calls);
        assertEquals(0, info.waits);
    }

    @Test
    void interruptedSpacingStopsBeforeDataAndPreservesInterrupt() {
        final var transport = new FakeClient(INFO, "[]");
        final var client =
                new HttpDataExportObservationClient(
                        settings(4096, Duration.ofSeconds(1)),
                        transport,
                        HttpDataExportObservationClientTest::policy,
                        () -> transport.now,
                        duration -> {
                            throw new InterruptedException("fixture-sensitive");
                        });
        try {
            final var failure =
                    assertThrows(
                            InterruptedException.class,
                            () ->
                                    client.observe(
                                            DataExportObservedPageTest.request(
                                                    DataExportTemplate.FRETES)));
            assertEquals("OBS_INTERRUPTED", failure.getMessage());
            assertTrue(Thread.currentThread().isInterrupted());
            assertEquals(1, transport.calls);
        } finally {
            Thread.interrupted();
        }
    }

    @ParameterizedTest
    @EnumSource(
            value = DataExportTemplate.class,
            names = {"COLETAS", "FRETES"},
            mode = EnumSource.Mode.EXCLUDE)
    void physicalModeUsesInfoAndOneFinitePageWithoutCanonicalRoot(final DataExportTemplate template)
            throws Exception {
        final var transport =
                new FakeClient(physicalInfo(template), "{\"data\":[{\"comments\":null}]}");
        transport.infoDuration = Duration.ofMillis(800).toNanos();
        final var client = client(transport, 4096);
        final var result = client.observePhysical(DataExportObservedPageTest.request(template));
        assertEquals(1, result.physicalRows());
        assertEquals(DataExportObservedPage.Verification.UNVERIFIED, result.sourceEntities());
        assertEquals(DataExportObservedPage.Verification.UNVERIFIED, result.remotePer());
        assertFalse(result.canonicalIdentityProven());
        assertFalse(result.paginationRootProven());
        assertEquals(2, transport.calls);
        assertEquals(List.of(0L, Duration.ofMillis(2800).toNanos()), transport.starts);
        assertEquals(Duration.ofSeconds(2).toNanos(), transport.waited);
        final var query =
                java.net.URLDecoder.decode(
                        transport.requests.get(1).uri().getRawQuery(), StandardCharsets.UTF_8);
        assertTrue(query.contains("page=1&per=1"));
        assertTrue(query.contains("2026-10-01 - 2026-10-01"));
        if (template == DataExportTemplate.CONTAS_A_PAGAR) {
            assertTrue(query.contains("search[accounting_debits][issue_date]="));
            assertTrue(query.contains("search[accounting_debits][created_at]="));
            assertEquals(1, new ExpansionObservedPageMapper().mapPhysical(result).size());
        }
        assertEquals(
                "OBS_ALREADY_USED",
                assertThrows(
                                HttpDataExportObservationClient.Failure.class,
                                () -> client.observeMetadata(template))
                        .code());
        assertEquals(2, transport.calls);
    }

    @Test
    void physicalModeRequiresEveryFilterAndDoesNotFallBackFromCurrentRootMode() throws Exception {
        final var cap = DataExportTemplate.CONTAS_A_PAGAR;
        for (final String metadata :
                List.of(
                        "{\"fields\":[\"comments\"],\"filters\":[\"accounting_debits.issue_date\"]}",
                        "{\"fields\":[],\"filters\":[\"accounting_debits.issue_date\",\"accounting_debits.created_at\"]}")) {
            final var transport = new FakeClient(metadata, "{\"data\":[]}");
            assertEquals(
                    "PHYSICAL_METADATA_REQUIRED_FIELD",
                    assertThrows(
                                    HttpDataExportObservationClient.Failure.class,
                                    () ->
                                            client(transport, 4096)
                                                    .observePhysical(
                                                            DataExportObservedPageTest.request(
                                                                    cap)))
                            .code());
            assertEquals(1, transport.calls);
        }
        final var transport = new FakeClient(physicalInfo(cap), "{\"data\":[]}");
        assertThrows(
                IllegalArgumentException.class,
                () -> client(transport, 4096).observe(DataExportObservedPageTest.request(cap)));
        assertEquals(0, transport.calls);
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        client(transport, 4096)
                                .observePhysical(
                                        DataExportObservedPageTest.request(
                                                DataExportTemplate.FRETES)));
        assertEquals(0, transport.calls);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "{\"data\":[{\"id\":1},{\"id\":1}]}",
                "{\"data\":[{\"comments\":null}],\"elsewhere\":[{\"id\":2}]}",
                "{\"data\":{\"comments\":null}}",
                "{\"data\":[{\"comments\":{\"children\":[1,2]}}]}",
                "{\"data\":[{\"comments\":[1,2]}]}",
                "{\"data\":[{\"invoices_mapping\":[\"one\",\"two\"]}]}",
                "{\"data\":[{\"invoices_mapping\":[\"one\"],\"fit_fte_invoices_order_number\":[\"two\"]}]}",
                "{\"data\":[{\"comments\":null,\"comments\":null}]}",
                "{\"data\":[]} false",
                "{\"data\":[{\"comments\":null}"
            })
    void physicalWholeBodyRefusalCannotReturnOrRetryAnyFirstRow(final String body) {
        final var template = DataExportTemplate.FATURAS_POR_CLIENTE;
        final var transport = new FakeClient(physicalInfo(template), body);
        final var client = client(transport, 4096);
        final var failure =
                assertThrows(
                        HttpDataExportObservationClient.Failure.class,
                        () -> client.observePhysical(DataExportObservedPageTest.request(template)));
        assertNull(failure.getCause());
        assertEquals(2, transport.calls);
        assertEquals(
                "OBS_ALREADY_USED",
                assertThrows(
                                HttpDataExportObservationClient.Failure.class,
                                () ->
                                        client.observePhysical(
                                                DataExportObservedPageTest.request(template)))
                        .code());
        assertEquals(2, transport.calls);
    }

    @Test
    void physicalEmptyPageRemainsNoDataObserved() throws Exception {
        final var template = DataExportTemplate.CONTAS_A_PAGAR;
        final var result =
                client(new FakeClient(physicalInfo(template), "{\"data\":[]}"), 4096)
                        .observePhysical(DataExportObservedPageTest.request(template));
        assertEquals(DataExportObservedPage.PhysicalStatus.NO_DATA_OBSERVED, result.status());
        assertFalse(result.sourceCompletenessProven());
        assertEquals(0, result.physicalRows());
        assertEquals(DataExportObservedPage.Verification.UNVERIFIED, result.sourceEntities());
    }

    @ParameterizedTest
    @ValueSource(ints = {204, 301, 401, 429, 500})
    void physicalStopsAtFirstNonSuccessInEitherPhase(final int status) {
        final var template = DataExportTemplate.CONTAS_A_PAGAR;
        for (final boolean first : List.of(false, true)) {
            final var transport = new FakeClient(physicalInfo(template), "{\"data\":[]}");
            if (first) {
                transport.status = status;
            } else {
                transport.dataStatus = status;
            }
            final var failure =
                    assertThrows(
                            HttpDataExportObservationClient.Failure.class,
                            () ->
                                    client(transport, 4096)
                                            .observePhysical(
                                                    DataExportObservedPageTest.request(template)));
            assertEquals("OBS_HTTP_STATUS", failure.code());
            assertEquals(status, failure.httpStatus());
            assertEquals(first ? 1 : 2, transport.calls);
        }
    }

    @Test
    void physicalDataByteLengthUtf8AndMediaFailuresNeverEscape() {
        final var template = DataExportTemplate.CONTAS_A_PAGAR;
        final var oversized = new FakeClient(physicalInfo(template), " ".repeat(4097));
        assertEquals(
                "OBS_HTTP_BODY_BOUND",
                assertThrows(
                                HttpDataExportObservationClient.Failure.class,
                                () ->
                                        client(oversized, 4096)
                                                .observePhysical(
                                                        DataExportObservedPageTest.request(
                                                                template)))
                        .code());
        assertTrue(oversized.cancelled);
        final var utf8 = new FakeClient(physicalInfo(template), "{}");
        utf8.dataBytes = new byte[] {(byte) 0xc3, (byte) 0x28};
        assertEquals(
                "PHYSICAL_DATA_INVALID",
                assertThrows(
                                HttpDataExportObservationClient.Failure.class,
                                () ->
                                        client(utf8, 4096)
                                                .observePhysical(
                                                        DataExportObservedPageTest.request(
                                                                template)))
                        .code());
        final var truncated = new FakeClient(physicalInfo(template), "{\"data\":[]}");
        truncated.dataHeaders =
                Map.of(
                        "Content-Type",
                        List.of("application/json"),
                        "Content-Length",
                        List.of("1000"));
        assertEquals(
                "OBS_HTTP_BODY_BOUND",
                assertThrows(
                                HttpDataExportObservationClient.Failure.class,
                                () ->
                                        client(truncated, 4096)
                                                .observePhysical(
                                                        DataExportObservedPageTest.request(
                                                                template)))
                        .code());
        final var media = new FakeClient(physicalInfo(template), "{\"data\":[]}");
        media.dataHeaders = Map.of("Content-Type", List.of("text/html"));
        assertEquals(
                "OBS_HTTP_MEDIA_TYPE",
                assertThrows(
                                HttpDataExportObservationClient.Failure.class,
                                () ->
                                        client(media, 4096)
                                                .observePhysical(
                                                        DataExportObservedPageTest.request(
                                                                template)))
                        .code());
    }

    @Test
    void physicalDeadlineAndShortWaitCannotDispatchData() {
        final var template = DataExportTemplate.CONTAS_A_PAGAR;
        final var late = new FakeClient(physicalInfo(template), "{\"data\":[]}");
        late.infoDuration = Duration.ofSeconds(59).toNanos();
        assertEquals(
                "OBS_UNIT_DEADLINE",
                assertThrows(
                                HttpDataExportObservationClient.Failure.class,
                                () ->
                                        client(late, 4096)
                                                .observePhysical(
                                                        DataExportObservedPageTest.request(
                                                                template)))
                        .code());
        assertEquals(1, late.calls);
        assertEquals(0, late.waits);
        final var early = new FakeClient(physicalInfo(template), "{\"data\":[]}");
        early.waitAdjustment = -1;
        assertEquals(
                "OBS_INTERVAL_NOT_ELAPSED",
                assertThrows(
                                HttpDataExportObservationClient.Failure.class,
                                () ->
                                        client(early, 4096)
                                                .observePhysical(
                                                        DataExportObservedPageTest.request(
                                                                template)))
                        .code());
        assertEquals(1, early.calls);
    }

    @Test
    void physicalRemainingDeadlineAndInterruptionGuardTheSecondRequest() throws Exception {
        final var template = DataExportTemplate.CONTAS_A_PAGAR;
        final var transport = new FakeClient(physicalInfo(template), "{\"data\":[]}");
        transport.infoDuration = Duration.ofSeconds(30).toNanos();
        final var client =
                new HttpDataExportObservationClient(
                        settings(4096, Duration.ofSeconds(30)),
                        transport,
                        HttpDataExportObservationClientTest::policy,
                        () -> transport.now,
                        transport::waitFor);
        assertEquals(
                0,
                client.observePhysical(DataExportObservedPageTest.request(template))
                        .physicalRows());
        assertEquals(Duration.ofSeconds(28), transport.requests.get(1).timeout().orElseThrow());

        final var late = new FakeClient(physicalInfo(template), "{\"data\":[]}");
        late.infoDuration = Duration.ofSeconds(30).toNanos();
        late.dataDuration = Duration.ofSeconds(28).toNanos();
        assertEquals(
                "OBS_UNIT_DEADLINE",
                assertThrows(
                                HttpDataExportObservationClient.Failure.class,
                                () ->
                                        client(late, 4096)
                                                .observePhysical(
                                                        DataExportObservedPageTest.request(
                                                                template)))
                        .code());
        assertEquals(2, late.calls);

        final var interrupted = new FakeClient(physicalInfo(template), "{\"data\":[]}");
        final var interruptClient =
                new HttpDataExportObservationClient(
                        settings(4096, Duration.ofSeconds(30)),
                        interrupted,
                        HttpDataExportObservationClientTest::policy,
                        () -> interrupted.now,
                        delay -> {
                            throw new InterruptedException("synthetic");
                        });
        try {
            assertThrows(
                    InterruptedException.class,
                    () ->
                            interruptClient.observePhysical(
                                    DataExportObservedPageTest.request(template)));
            assertTrue(Thread.currentThread().isInterrupted());
            assertEquals(1, interrupted.calls);
        } finally {
            Thread.interrupted();
        }
    }

    private static String physicalInfo(final DataExportTemplate template) {
        final var info = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
        final var fields = info.putArray("fields");
        for (final String name :
                List.of("comments", "id", "invoices_mapping", "fit_fte_invoices_order_number")) {
            fields.add(name);
        }
        final var filters = info.putArray("filters");
        DataExportObservedPageTest.request(template)
                .page()
                .filters()
                .keySet()
                .forEach(path -> filters.add(path.root() + "." + path.field()));
        return info.toString();
    }

    static String policy(final String key) {
        return Map.of(
                        "jdk.httpclient.disableRetryConnect",
                        "true",
                        "jdk.httpclient.enableAllMethodRetry",
                        "false",
                        "jdk.httpclient.redirects.retrylimit",
                        "1")
                .get(key);
    }

    private static final class FakeClient extends HttpClient {
        private final String info;
        private final String page;
        private int status = 200;
        private Integer dataStatus;
        private int calls;
        private final List<HttpRequest> requests = new ArrayList<>();
        private Map<String, List<String>> headers =
                Map.of("Content-Type", List.of("application/json"));
        private Map<String, List<String>> dataHeaders;
        private boolean cancelled;
        private boolean pending;
        private RuntimeException failure;
        private CompletableFuture<?> future;
        private byte[] bytes;
        private byte[] dataBytes;
        private long now;
        private long infoDuration;
        private long dataDuration;
        private long waitAdjustment;
        private long waited;
        private int waits;
        private final List<Long> starts = new ArrayList<>();

        private void waitFor(final Duration delay) {
            waits++;
            waited += delay.toNanos();
            now += delay.toNanos() + waitAdjustment;
        }

        FakeClient(final String info, final String page) {
            this.info = info;
            this.page = page;
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(
                final HttpRequest request, final HttpResponse.BodyHandler<T> handler) {
            requests.add(request);
            starts.add(now);
            calls++;
            final int responseStatus = calls == 2 && dataStatus != null ? dataStatus : status;
            final var responseHeaders = calls == 2 && dataHeaders != null ? dataHeaders : headers;
            final byte[] responseBytes = calls == 2 && dataBytes != null ? dataBytes : bytes;
            if (pending) {
                final var result = new CompletableFuture<HttpResponse<T>>();
                future = result;
                return result;
            }
            if (failure != null) {
                return CompletableFuture.failedFuture(failure);
            }
            try {
                final var responseInfo =
                        new HttpResponse.ResponseInfo() {
                            public int statusCode() {
                                return responseStatus;
                            }

                            public HttpHeaders headers() {
                                return HttpHeaders.of(responseHeaders, (name, value) -> true);
                            }

                            public Version version() {
                                return Version.HTTP_1_1;
                            }
                        };
                final var subscriber = handler.apply(responseInfo);
                subscriber.onSubscribe(
                        new Flow.Subscription() {
                            public void request(final long n) {}

                            public void cancel() {
                                cancelled = true;
                            }
                        });
                subscriber.onNext(
                        List.of(
                                ByteBuffer.wrap(
                                        responseBytes == null
                                                ? (calls == 1 ? info : page)
                                                        .getBytes(StandardCharsets.UTF_8)
                                                : responseBytes)));
                subscriber.onComplete();
                if (calls == 1) {
                    now += infoDuration;
                } else {
                    now += dataDuration;
                }
                return subscriber
                        .getBody()
                        .toCompletableFuture()
                        .thenApply(
                                payload ->
                                        new HttpResponse<T>() {
                                            public int statusCode() {
                                                return responseStatus;
                                            }

                                            public HttpRequest request() {
                                                return request;
                                            }

                                            public Optional<HttpResponse<T>> previousResponse() {
                                                return Optional.empty();
                                            }

                                            public HttpHeaders headers() {
                                                return responseInfo.headers();
                                            }

                                            public T body() {
                                                return payload;
                                            }

                                            public Optional<SSLSession> sslSession() {
                                                return Optional.empty();
                                            }

                                            public URI uri() {
                                                return request.uri();
                                            }

                                            public Version version() {
                                                return Version.HTTP_1_1;
                                            }
                                        });
            } catch (final RuntimeException error) {
                return CompletableFuture.failedFuture(error);
            }
        }

        public Optional<CookieHandler> cookieHandler() {
            return Optional.empty();
        }

        public Optional<Duration> connectTimeout() {
            return Optional.of(Duration.ofSeconds(1));
        }

        public Redirect followRedirects() {
            return Redirect.NEVER;
        }

        public Optional<ProxySelector> proxy() {
            return Optional.empty();
        }

        public SSLContext sslContext() {
            throw new UnsupportedOperationException();
        }

        public SSLParameters sslParameters() {
            final var tls = new SSLParameters();
            tls.setEndpointIdentificationAlgorithm("HTTPS");
            return tls;
        }

        public Optional<Authenticator> authenticator() {
            return Optional.empty();
        }

        public Version version() {
            return Version.HTTP_1_1;
        }

        public Optional<Executor> executor() {
            return Optional.empty();
        }

        public <T> HttpResponse<T> send(
                final HttpRequest request, final HttpResponse.BodyHandler<T> handler) {
            throw new UnsupportedOperationException();
        }

        public <T> CompletableFuture<HttpResponse<T>> sendAsync(
                final HttpRequest request,
                final HttpResponse.BodyHandler<T> handler,
                final HttpResponse.PushPromiseHandler<T> push) {
            throw new UnsupportedOperationException();
        }
    }
}
