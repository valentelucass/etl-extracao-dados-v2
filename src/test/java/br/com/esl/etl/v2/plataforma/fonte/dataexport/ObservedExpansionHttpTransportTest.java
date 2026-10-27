package br.com.esl.etl.v2.plataforma.fonte.dataexport;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.plataforma.resiliencia.EslWorkload;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

/** PREPARED ONLY. Requires qualified core-r02 plus adopted q08; never executed in this task. */
class ObservedExpansionHttpTransportTest {
    @ParameterizedTest
    @EnumSource(ObservedExpansionCaptureTemplate.class)
    void preservesSubscriberBytesAndClosedRequestForEachTemplate(
            final ObservedExpansionCaptureTemplate template) throws Exception {
        final var f = new ObservedHttpTransportTestSupport.Fixture(template);
        final byte[] wire =
                "[ {\"n\":0.1000,\"text\":\"\\u0061\",\"array\":[2,2]} ]\n"
                        .getBytes(StandardCharsets.UTF_8);
        f.client.responses.add(ObservedHttpTransportTestSupport.Wire.json(wire));
        final var raw = f.prepare().fetch(f.plan.request(1));
        assertArrayEquals(wire, raw.body());
        assertEquals(1, f.cycle.sourceRequests());
        final var sent =
                new com.fasterxml.jackson.databind.ObjectMapper()
                        .readTree(f.client.requestBodies.get(0));
        assertEquals(f.plan.request(1).jsonBody(), sent);
        assertFalse(sent.has("order_by"));
        assertEquals("50", sent.path("per").asText());
        assertEquals("GET", f.client.requests.get(0).method());
        assertEquals(
                f.source
                        .settings()
                        .materialize(key -> "synthetic-token")
                        .endpointFor(template.templateId()),
                f.client.requests.get(0).uri());
        assertTrue(
                f.client.requests.get(0).timeout().orElseThrow().compareTo(Duration.ofSeconds(2))
                        <= 0);
        final byte[] copy = raw.body();
        copy[0] = 0;
        assertArrayEquals(wire, raw.body());
        assertFalse(raw.toString().contains("\\u0061"));
        if (template == ObservedExpansionCaptureTemplate.CAP) {
            assertEquals(
                    sent.path("search").path("accounting_debits").path("issue_date"),
                    sent.path("search").path("accounting_debits").path("created_at"));
        }
    }

    @Test
    void completeInvalidUtf8ReachesCoreBeforeDecodeAndBecomesStructureIssue() {
        final var f =
                new ObservedHttpTransportTestSupport.Fixture(ObservedExpansionCaptureTemplate.INV);
        final byte[] wire = {(byte) 0xc3, (byte) 0x28};
        f.client.responses.add(ObservedHttpTransportTestSupport.Wire.json(wire));
        final var raw = f.prepare().fetch(f.plan.request(1));
        assertArrayEquals(wire, raw.body());
        final var projection = new ObservedExpansionPageProjector(f.plan).projectNext(raw);
        assertEquals(ObservedExpansionPageProjection.Issue.INVALID_UTF8, projection.pageIssue());
        assertTrue(projection.physicalRows().isEmpty());
        assertEquals("NOT_PERFORMED_BY_CORE", projection.sourceSchemaValidation());
    }

    @Test
    void twoSemanticallyEqualJsonPagesKeepDifferentLexicalBytes() {
        final var f =
                new ObservedHttpTransportTestSupport.Fixture(ObservedExpansionCaptureTemplate.SIN);
        final byte[] first = "[{\"x\":1}]".getBytes(StandardCharsets.UTF_8);
        final byte[] second = "[ { \"x\" : 1 } ]\n".getBytes(StandardCharsets.UTF_8);
        f.client.responses.add(ObservedHttpTransportTestSupport.Wire.json(first));
        f.client.responses.add(ObservedHttpTransportTestSupport.Wire.json(second));
        final var transport = f.prepare();
        assertArrayEquals(first, transport.fetch(f.plan.request(1)).body());
        assertArrayEquals(second, transport.fetch(f.plan.request(2)).body());
        assertTrue(f.nanos.get() >= Duration.ofSeconds(3).toNanos());
    }

    @ParameterizedTest
    @ValueSource(ints = {204, 206, 301, 302, 307, 401, 405, 415, 429, 500})
    void firstNon200StopsWithoutRetryFallbackOrFollowingPage(final int status) {
        final var f =
                new ObservedHttpTransportTestSupport.Fixture(ObservedExpansionCaptureTemplate.FAT);
        f.client.responses.add(
                new ObservedHttpTransportTestSupport.Wire(
                        status,
                        Map.of("Content-Type", List.of("application/json")),
                        "[]".getBytes(StandardCharsets.UTF_8),
                        false));
        final var transport = f.prepare();
        assertThrows(RuntimeException.class, () -> transport.fetch(f.plan.request(1)));
        assertThrows(RuntimeException.class, () -> transport.fetch(f.plan.request(1)));
        assertEquals(1, f.client.requests.size());
        assertEquals(1, f.cycle.sourceRequests());
    }

    @Test
    void ioFailureDoesNotEscapeProviderTextOrRetry() {
        final var f =
                new ObservedHttpTransportTestSupport.Fixture(ObservedExpansionCaptureTemplate.CAP);
        f.client.failure =
                new javax.net.ssl.SSLHandshakeException(
                        ObservedHttpTransportTestSupport.PRIVATE_BODY);
        final var failure =
                assertThrows(
                        DataExportUnavailableException.class,
                        () -> f.prepare().fetch(f.plan.request(1)));
        assertNull(failure.getCause());
        assertFalse(failure.toString().contains(ObservedHttpTransportTestSupport.PRIVATE_BODY));
        assertEquals(1, f.client.requests.size());
    }

    @Test
    void streamFailureNeverReturnsPrefixAsCompleteRawPage() {
        final var f =
                new ObservedHttpTransportTestSupport.Fixture(ObservedExpansionCaptureTemplate.CAP);
        f.client.responses.add(
                new ObservedHttpTransportTestSupport.Wire(
                        200,
                        Map.of("Content-Type", List.of("application/json")),
                        "[1".getBytes(StandardCharsets.UTF_8),
                        true));
        assertThrows(RuntimeException.class, () -> f.prepare().fetch(f.plan.request(1)));
        assertEquals(1, f.client.requests.size());
    }

    @Test
    void cancelledCompletionIsRejectedBeforeRawDelivery() {
        final var f =
                new ObservedHttpTransportTestSupport.Fixture(ObservedExpansionCaptureTemplate.CAP);
        f.client.responses.add(
                ObservedHttpTransportTestSupport.Wire.json("[]".getBytes(StandardCharsets.UTF_8)));
        f.client.beforeCompletion = () -> f.cancelled.set(true);
        assertThrows(
                br.com.esl.etl.v2.plataforma.resiliencia.ResilienceCancelledException.class,
                () -> f.prepare().fetch(f.plan.request(1)));
        assertEquals(1, f.client.requests.size());
    }

    @Test
    void expiredRequestBudgetIsRejectedBeforeRawDelivery() {
        final var f =
                new ObservedHttpTransportTestSupport.Fixture(ObservedExpansionCaptureTemplate.CAP);
        f.client.responses.add(
                ObservedHttpTransportTestSupport.Wire.json("[]".getBytes(StandardCharsets.UTF_8)));
        f.client.beforeCompletion = () -> f.nanos.addAndGet(Duration.ofSeconds(3).toNanos());
        assertThrows(
                br.com.esl.etl.v2.plataforma.resiliencia.ResilienceTimeoutException.class,
                () -> f.prepare().fetch(f.plan.request(1)));
    }

    @Test
    void normalInfoAndRawDataShareSameGovernorAndWorkloadBudget() {
        final var policy = ObservedHttpTransportTestSupport.policy(1, Duration.ofSeconds(3));
        final var f =
                new ObservedHttpTransportTestSupport.Fixture(
                        ObservedExpansionCaptureTemplate.INV,
                        DataExportTransport.GET_WITH_BODY,
                        1,
                        Duration.ofSeconds(2),
                        java.net.URI.create("https://synthetic.invalid/v1"),
                        policy);
        f.client.responses.add(
                ObservedHttpTransportTestSupport.Wire.json(
                        "{\"fields\":[\"id\"],\"filters\":[]}".getBytes(StandardCharsets.UTF_8)));
        final var raw = f.prepare(); // Materialized policy must be checked BEFORE normal INFO.
        f.factory
                .forWorkload(f.cycle, EslWorkload.INVENTARIO)
                .templateInfoGateway()
                .fetchInfo(DataExportTemplate.INVENTARIO);
        assertThrows(
                br.com.esl.etl.v2.plataforma.resiliencia.ResilienceBudgetExceededException.class,
                () -> raw.fetch(f.plan.request(1)));
        assertEquals(1, f.client.requests.size());
        assertEquals(1, f.cycle.sourceRequests());
    }

    @Test
    void declaredOversizeNeverReturnsRawPage() {
        final var f =
                new ObservedHttpTransportTestSupport.Fixture(ObservedExpansionCaptureTemplate.INV);
        f.client.responses.add(
                new ObservedHttpTransportTestSupport.Wire(
                        200,
                        Map.of(
                                "Content-Type",
                                List.of("application/json"),
                                "Content-Length",
                                List.of("8388609")),
                        new byte[] {'[', ']'},
                        false));
        assertThrows(
                DataExportResponseLimitExceededException.class,
                () -> f.prepare().fetch(f.plan.request(1)));
        assertEquals(1, f.client.requests.size());
    }

    @Test
    void cumulativeRawBudgetIsAppliedBeforeAnotherSend() {
        final var f =
                new ObservedHttpTransportTestSupport.Fixture(ObservedExpansionCaptureTemplate.INV);
        final var l = new ObservedExpansionCaptureLimits(2, 1024, 4, 4096, 1, 2, 4, 100);
        final var p =
                new ObservedExpansionCapturePlan(
                        f.plan.captureExecutionId(),
                        f.plan.template(),
                        f.plan.sourceInstance(),
                        f.plan.tenantScope(),
                        f.plan.businessWindow(),
                        f.plan.sourceZone(),
                        f.plan.transport(),
                        f.plan.orderBy(),
                        f.plan.envelope(),
                        f.plan.per(),
                        l);
        f.client.responses.add(ObservedHttpTransportTestSupport.Wire.json(new byte[] {'[', ']'}));
        f.client.responses.add(ObservedHttpTransportTestSupport.Wire.json(new byte[] {'[', ']'}));
        final var transport =
                f.factory.prepareObservedTransport(
                        p,
                        f.cycle,
                        f.governor,
                        f.cancellation,
                        DataExportHttpAttemptObserver.noop());
        assertArrayEquals(new byte[] {'[', ']'}, transport.fetch(p.request(1)).body());
        assertArrayEquals(new byte[] {'[', ']'}, transport.fetch(p.request(2)).body());
        assertThrows(IllegalStateException.class, () -> transport.fetch(p.request(3)));
        assertEquals(2, f.client.requests.size());
    }

    @Test
    void nonContiguousPageIsRejectedBeforeWire() {
        final var f =
                new ObservedHttpTransportTestSupport.Fixture(ObservedExpansionCaptureTemplate.INV);
        final var transport = f.prepare();
        assertThrows(IllegalStateException.class, () -> transport.fetch(f.plan.request(2)));
        assertEquals(0, f.client.requests.size());
    }

    @Test
    void data429StopsSiblingPreparedCaptureAndNewPreparations() {
        final var f =
                new ObservedHttpTransportTestSupport.Fixture(ObservedExpansionCaptureTemplate.INV);
        final var first = f.prepare();
        final var sibling = f.prepare();
        f.client.responses.add(
                new ObservedHttpTransportTestSupport.Wire(
                        429,
                        Map.of("Content-Type", List.of("application/json")),
                        new byte[] {'[', ']'},
                        false));
        assertThrows(DataExportUnavailableException.class, () -> first.fetch(f.plan.request(1)));
        assertThrows(IllegalStateException.class, () -> sibling.fetch(f.plan.request(1)));
        assertThrows(IllegalStateException.class, f::prepare);
        assertEquals(1, f.client.requests.size());
    }

    @Test
    void explicitStopFromFutureInfoDriverPreventsRawData() {
        final var f =
                new ObservedHttpTransportTestSupport.Fixture(ObservedExpansionCaptureTemplate.INV);
        final var transport = f.prepare();
        transport.stopSource();
        assertThrows(IllegalStateException.class, () -> transport.fetch(f.plan.request(1)));
        assertThrows(IllegalStateException.class, f::prepare);
        assertEquals(0, f.client.requests.size());
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(
            strings = {"cl_overflow", "cl_mismatch", "stream_error", "cancelled", "timeout"})
    void responseInfo429StopsSharedSourceBeforeEveryLaterFailure(final String failureKind) {
        final var f =
                new ObservedHttpTransportTestSupport.Fixture(ObservedExpansionCaptureTemplate.INV);
        final var first = f.prepare();
        final var sibling = f.prepare();
        final var headers = new java.util.HashMap<String, List<String>>();
        headers.put("Content-Type", List.of("application/json"));
        if (failureKind.equals("cl_overflow")) {
            headers.put("Content-Length", List.of("8388609"));
        }
        if (failureKind.equals("cl_mismatch")) {
            headers.put("Content-Length", List.of("3"));
        }
        f.client.responses.add(
                new ObservedHttpTransportTestSupport.Wire(
                        429, headers, new byte[] {'[', ']'}, failureKind.equals("stream_error")));
        if (failureKind.equals("cancelled")) {
            f.client.beforeCompletion = () -> f.cancelled.set(true);
        }
        if (failureKind.equals("timeout")) {
            f.client.beforeCompletion = () -> f.nanos.addAndGet(Duration.ofSeconds(3).toNanos());
        }
        assertThrows(RuntimeException.class, () -> first.fetch(f.plan.request(1)));
        final var siblingFailure =
                assertThrows(IllegalStateException.class, () -> sibling.fetch(f.plan.request(1)));
        assertEquals("OBS_CAPTURE_TRANSPORT_SEQUENCE", siblingFailure.getMessage());
        // A fresh live cycle/token avoids attributing the refusal to the old cancellation/deadline.
        final var freshCancellation =
                br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken.none();
        final var freshCycle = f.governor.beginCycle(freshCancellation);
        final var prepareFailure =
                assertThrows(
                        IllegalStateException.class,
                        () ->
                                f.factory.prepareObservedTransport(
                                        f.plan,
                                        freshCycle,
                                        f.governor,
                                        freshCancellation,
                                        DataExportHttpAttemptObserver.noop()));
        assertEquals("OBS_CAPTURE_SOURCE_STOPPED", prepareFailure.getMessage());
        assertEquals(1, f.client.requests.size());
        assertEquals(0, freshCycle.sourceRequests());
    }

    @Test
    void callbackIsSealedSingleUseAndCannotExposeHeaderOrCredential() {
        assertTrue(DataExportRawBodyCallback.class.isSealed());
        assertArrayEquals(
                new Class<?>[] {ObservedExpansionHttpTransport.RawCapture.class},
                DataExportRawBodyCallback.class.getPermittedSubclasses());
        final var plan =
                ObservedHttpTransportTestSupport.plan(ObservedExpansionCaptureTemplate.INV);
        final var capture =
                new ObservedExpansionHttpTransport.RawCapture(
                        plan.request(1), new java.util.concurrent.atomic.AtomicBoolean());
        assertThrows(IllegalStateException.class, capture::page);
        capture.received(new byte[] {'[', ']'}, java.time.Instant.EPOCH);
        assertThrows(
                IllegalStateException.class,
                () -> capture.received(new byte[] {'[', ']'}, java.time.Instant.EPOCH));
        assertFalse(capture.toString().contains("synthetic-token"));
    }
}
