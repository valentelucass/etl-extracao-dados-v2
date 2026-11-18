package br.com.esl.etl.v2.plataforma.reconciliacao.exportacao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class IndependentExportCycleTest {
    @TempDir Path directory;

    private static IndependentExportCycle.Reply response(final int status) {
        return new IndependentExportCycle.Reply(
                status,
                ("{\"id\":7,\"report_id\":6389,"
                                + "\"status\":\"enqueued\",\"show_url\":\"/api/analytics/report_files/7\","
                                + "\"filters\":{\"freights\":{\"service_at\":\"2025-03-03 - 2025-03-09\"}}}")
                        .getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void onePostCreatesPendingFileAndCannotBeRepeated() throws Exception {
        final var calls = new AtomicInteger();
        final var cycle =
                new IndependentExportCycle(
                        new IndependentExportCycle.SerialChannel(() -> 0),
                        (path, body, timeout) -> {
                            calls.incrementAndGet();
                            assertEquals("/api/analytics/reports/6389/export", path);
                            assertEquals(Duration.ofSeconds(30), timeout);
                            return response(200);
                        },
                        journal(),
                        Duration.ofSeconds(30));
        final var request = new ExportPairFixture(directory).request;
        assertTrue(cycle.submit(request).state().startsWith("PENDING_"));
        assertThrows(IllegalArgumentException.class, () -> cycle.submit(request));
        assertEquals(1, calls.get());
    }

    @Test
    void stop429PreventsAnotherCycleOnSameChannelWithoutRetry() throws Exception {
        final var calls = new AtomicInteger();
        final var clock = new AtomicLong();
        final var channel = new IndependentExportCycle.SerialChannel(clock::get);
        final IndependentExportCycle.Port port =
                (path, body, timeout) -> {
                    calls.incrementAndGet();
                    return response(429);
                };
        final var request = new ExportPairFixture(directory).request;
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new IndependentExportCycle(channel, port, journal(), Duration.ofSeconds(30))
                                .submit(request));
        clock.set(Duration.ofHours(1).toNanos());
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new IndependentExportCycle(channel, port, journal(), Duration.ofSeconds(30))
                                .submit(request));
        assertEquals(1, calls.get());
    }

    @Test
    void threeSecondsAreMeasuredAfterResponseCompletion() throws Exception {
        final var clock = new AtomicLong();
        final var calls = new AtomicInteger();
        final var channel = new IndependentExportCycle.SerialChannel(clock::get);
        final IndependentExportCycle.Port port =
                (path, body, timeout) -> {
                    calls.incrementAndGet();
                    clock.addAndGet(Duration.ofSeconds(10).toNanos());
                    return response(200);
                };
        final var request = new ExportPairFixture(directory).request;
        new IndependentExportCycle(channel, port, journal(), Duration.ofSeconds(30))
                .submit(request);
        clock.addAndGet(Duration.ofMillis(2999).toNanos());
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new IndependentExportCycle(channel, port, journal(), Duration.ofSeconds(30))
                                .submit(request));
        clock.addAndGet(Duration.ofMillis(1).toNanos());
        new IndependentExportCycle(channel, port, journal(), Duration.ofSeconds(30))
                .submit(request);
        assertEquals(2, calls.get());
    }

    @Test
    void redirectIsTerminalWithoutFollowup() throws Exception {
        final var calls = new AtomicInteger();
        final var cycle =
                new IndependentExportCycle(
                        new IndependentExportCycle.SerialChannel(() -> 0),
                        (path, body, timeout) -> {
                            calls.incrementAndGet();
                            return response(302);
                        },
                        journal(),
                        Duration.ofSeconds(30));
        assertThrows(
                IllegalArgumentException.class,
                () -> cycle.submit(new ExportPairFixture(directory).request));
        assertEquals(1, calls.get());
    }

    @Test
    void unknownWireOutcomeConsumesCycle() throws Exception {
        final var calls = new AtomicInteger();
        final var request = new ExportPairFixture(directory).request;
        final var cycle =
                new IndependentExportCycle(
                        new IndependentExportCycle.SerialChannel(() -> 0),
                        (path, body, timeout) -> {
                            calls.incrementAndGet();
                            throw new java.io.IOException("synthetic");
                        },
                        journal(),
                        Duration.ofSeconds(30));
        assertThrows(java.io.IOException.class, () -> cycle.submit(request));
        assertThrows(IllegalArgumentException.class, () -> cycle.submit(request));
        assertEquals(1, calls.get());
    }

    @Test
    void unboundedTimeoutIsRefusedBeforeTransport() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new IndependentExportCycle(
                                new IndependentExportCycle.SerialChannel(() -> 0),
                                (path, body, timeout) -> response(200),
                                journal(),
                                Duration.ofSeconds(31)));
    }

    @Test
    void reusedPostAttemptCannotCreateAnotherFileThroughANewCycle() throws Exception {
        final var calls = new AtomicInteger();
        final var clock = new AtomicLong();
        final var channel = new IndependentExportCycle.SerialChannel(clock::get);
        final var ledger = journal();
        final IndependentExportCycle.Port port =
                (path, body, timeout) -> {
                    calls.incrementAndGet();
                    return response(200);
                };
        final var request = new ExportPairFixture(directory).request;
        final var submitted =
                new IndependentExportCycle(channel, port, ledger, Duration.ofSeconds(30))
                        .submit(request);
        final var control = submitted.pendingControl(3, submitted.responseAt().plusSeconds(90));
        assertEquals(submitted.postReceiptSha256(), control.postReceiptSha256());
        clock.set(Duration.ofSeconds(4).toNanos());
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new IndependentExportCycle(channel, port, ledger, Duration.ofSeconds(30))
                                .submit(request));
        assertEquals(1, calls.get());
    }

    @Test
    void responseBeyondDeadlineIsUnknownAndCannotBecomeSuccess() throws Exception {
        final var clock = new AtomicLong();
        final var cycle =
                new IndependentExportCycle(
                        new IndependentExportCycle.SerialChannel(clock::get),
                        (path, body, timeout) -> {
                            clock.set(Duration.ofSeconds(31).toNanos());
                            return response(200);
                        },
                        journal(),
                        Duration.ofSeconds(30));
        assertThrows(
                IllegalArgumentException.class,
                () -> cycle.submit(new ExportPairFixture(directory).request));
    }

    private static IndependentExportCycle.PostJournal journal() {
        return new IndependentExportCycle.PostJournal() {
            private final java.util.Set<String> consumed = new java.util.HashSet<>();

            @Override
            public void reserve(final String attempt, final String digest) {
                ExportValidationSupport.require(consumed.add(attempt), "REPEATED_POST");
            }

            @Override
            public String appendAndConfirm(final IndependentExportCycle.PostReceipt receipt)
                    throws Exception {
                return receipt.sha256();
            }
        };
    }
}
