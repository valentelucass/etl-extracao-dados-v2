package br.com.esl.etl.v2.plataforma.fonte.dataexport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class CurlSnapshotRecoveryTest {
    @Test
    void recoversOnePageAfterTheTwoObservedSourceFailures() throws Exception {
        final var attempts = new AtomicInteger();
        final var waits = new ArrayList<Integer>();
        final var page = CurlSnapshotWire.JSON.createArrayNode().addObject().put("id", 1);
        final var result =
                CurlSnapshotWire.requestWithRecovery(
                        () ->
                                switch (attempts.incrementAndGet()) {
                                    case 1 ->
                                            throw new CurlSnapshotWire.RequestFailure(
                                                    500, 0, false);
                                    case 2 ->
                                            throw new CurlSnapshotWire.RequestFailure(422, 0, true);
                                    default -> page;
                                },
                        (delay, failure) -> waits.add(delay));
        assertSame(page, result);
        assertEquals(3, attempts.get());
        assertEquals(List.of(15, 45), waits);
    }

    @Test
    void persistentTimeoutStopsAfterThreeAttemptsAndBudgetRefusalStopsEarlier() {
        final var attempts = new AtomicInteger();
        final var timeout = new CurlSnapshotWire.RequestFailure(200, 28, false);
        final CurlSnapshotWire.Request request =
                () -> {
                    attempts.incrementAndGet();
                    throw timeout;
                };
        assertSame(
                timeout,
                assertThrows(
                        CurlSnapshotWire.RequestFailure.class,
                        () ->
                                CurlSnapshotWire.requestWithRecovery(
                                        request, (delay, failure) -> true)));
        assertEquals(3, attempts.get());
        assertSame(
                timeout,
                assertThrows(
                        CurlSnapshotWire.RequestFailure.class,
                        () ->
                                CurlSnapshotWire.requestWithRecovery(
                                        request, (delay, failure) -> false)));
        assertEquals(4, attempts.get());
    }

    @Test
    void authenticationRateLimitInvalidQueryAndTruncatedBySizeFailWithoutRetry() {
        final var permanent =
                List.of(
                        new CurlSnapshotWire.RequestFailure(401, 0, false),
                        new CurlSnapshotWire.RequestFailure(403, 0, false),
                        new CurlSnapshotWire.RequestFailure(422, 0, false),
                        new CurlSnapshotWire.RequestFailure(429, 0, false),
                        new CurlSnapshotWire.RequestFailure(200, 63, false));
        for (final var problem : permanent) {
            assertSame(
                    problem,
                    assertThrows(
                            CurlSnapshotWire.RequestFailure.class,
                            () ->
                                    CurlSnapshotWire.requestWithRecovery(
                                            () -> {
                                                throw problem;
                                            },
                                            (delay, failure) -> {
                                                throw new AssertionError("Unexpected retry");
                                            })));
        }
    }

    @Test
    void recognizesOnlyTheExactEslTimeoutCode() throws Exception {
        assertTrue(
                CurlSnapshotWire.sourceTimeout(
                        CurlSnapshotWire.JSON.readTree(
                                "{\"errors\":[\"timeout\"],\"formatted_errors\":[\"Tempo limite excedido\"]}")));
        assertFalse(
                CurlSnapshotWire.sourceTimeout(
                        CurlSnapshotWire.JSON.readTree(
                                "{\"errors\":[\"required_filter_blank\"]}")));
        assertFalse(
                CurlSnapshotWire.sourceTimeout(
                        CurlSnapshotWire.JSON.readTree(
                                "{\"errors\":[\"timeout\",\"required_filter_blank\"]}")));
    }
}
