package br.com.esl.etl.v2.plataforma.reconciliacao.exportacao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class ExportFileLifecycleTest {
    private static final Instant NOW = Instant.parse("2025-03-10T15:00:00Z");
    private static final String FILTERS =
            "{\"freights\":{\"service_at\":\"2025-03-03 - 2025-03-09\"}}";

    private ExportFileLifecycle.Control control(final int cap) throws Exception {
        return new ExportFileLifecycle.Control(
                UUID.randomUUID().toString(),
                "123",
                "a".repeat(64),
                "b".repeat(64),
                ExportValidationSupport.sha256(
                        ExportValidationSupport.canonicalBytes(
                                ExportValidationSupport.JSON.readTree(FILTERS))),
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString(),
                ExportFileLifecycle.State.ENQUEUED,
                0,
                cap,
                NOW.plusSeconds(120),
                NOW);
    }

    private static ExportFileLifecycle.Admission admission(final ExportFileLifecycle.Control c) {
        return new ExportFileLifecycle.Admission(
                c.attemptId(),
                c.fileId(),
                c.postReceiptSha256(),
                c.latestReceiptSha256(),
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString());
    }

    private static IndependentExportCycle.Reply reply(final int status, final String state) {
        return new IndependentExportCycle.Reply(
                status,
                ("{\"id\":123,\"report_id\":6389,\"status\":\""
                                + state
                                + "\",\"filters\":"
                                + FILTERS
                                + ("generated".equals(state)
                                        ? ",\"download_url\":\"https://example.invalid/private\""
                                        : "")
                                + "}")
                        .getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void generatingIsPendingAndDoesNotMeanHttpFailureOrTimeout() throws Exception {
        final var before = control(2);
        final var plan = ExportFileLifecycle.plan(before, admission(before), NOW.plusSeconds(3));
        final var fact =
                ExportFileLifecycle.observe(
                        before, plan, reply(200, "generating"), NOW.plusSeconds(4));
        final var after = ExportFileLifecycle.confirmPersisted(before, fact, fact.sha256());
        assertEquals(ExportFileLifecycle.State.GENERATING, after.state());
        assertEquals(1, after.pollsUsed());
        final var next = ExportFileLifecycle.plan(after, admission(after), NOW.plusSeconds(7));
        assertEquals("/api/analytics/report_files/123", next.path());
        assertEquals(fact.sha256(), next.previousReceiptSha256());
    }

    @Test
    void unconfirmedReceiptCannotAdvanceControl() throws Exception {
        final var before = control(2);
        final var fact =
                ExportFileLifecycle.observe(
                        before,
                        ExportFileLifecycle.plan(before, admission(before), NOW.plusSeconds(3)),
                        reply(200, "generating"),
                        NOW.plusSeconds(4));
        assertThrows(
                IllegalArgumentException.class,
                () -> ExportFileLifecycle.confirmPersisted(before, fact, "f".repeat(64)));
    }

    @Test
    void swappedAdmissionOrRepeatedNonceCannotPoll() throws Exception {
        final var c = control(2);
        final var wrong = admission(control(2));
        assertThrows(
                IllegalArgumentException.class,
                () -> ExportFileLifecycle.plan(c, wrong, NOW.plusSeconds(3)));
        final var repeated =
                new ExportFileLifecycle.Admission(
                        c.attemptId(),
                        c.fileId(),
                        c.postReceiptSha256(),
                        c.latestReceiptSha256(),
                        UUID.randomUUID().toString(),
                        c.lastNonce());
        assertThrows(
                IllegalArgumentException.class,
                () -> ExportFileLifecycle.plan(c, repeated, NOW.plusSeconds(3)));
    }

    @Test
    void noBudgetDeadlineOrCooldownCannotPoll() throws Exception {
        final var none = control(0);
        assertThrows(
                IllegalArgumentException.class,
                () -> ExportFileLifecycle.plan(none, admission(none), NOW.plusSeconds(3)));
        final var c = control(2);
        assertThrows(
                IllegalArgumentException.class,
                () -> ExportFileLifecycle.plan(c, admission(c), NOW.plusSeconds(2)));
        assertThrows(
                IllegalArgumentException.class,
                () -> ExportFileLifecycle.plan(c, admission(c), c.deadline()));
    }

    @Test
    void wrongFileFiltersOrMissingGeneratedDownloadAreRefused() throws Exception {
        final var c = control(2);
        final var plan = ExportFileLifecycle.plan(c, admission(c), NOW.plusSeconds(3));
        for (final String invalid :
                List.of(
                        new String(reply(200, "generating").body(), StandardCharsets.UTF_8)
                                .replace("123", "124"),
                        new String(reply(200, "generating").body(), StandardCharsets.UTF_8)
                                .replace("03-09", "03-08"),
                        new String(reply(200, "generating").body(), StandardCharsets.UTF_8)
                                .replace("generating", "generated"))) {
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            ExportFileLifecycle.observe(
                                    c,
                                    plan,
                                    new IndependentExportCycle.Reply(
                                            200, invalid.getBytes(StandardCharsets.UTF_8)),
                                    NOW.plusSeconds(4)));
        }
    }

    @Test
    void driveAutomaticallyAdmitsAndRecordsEachPollWithinOneBudget() throws Exception {
        final var events = new ArrayList<String>();
        final var clock = new FakeClock();
        final var journal = journal(events, false);
        final var poller =
                new ExportFilePoller(
                        control(3),
                        new IndependentExportCycle.SerialChannel(clock.nanos::get),
                        (path, timeout) -> {
                            events.add("GET");
                            return reply(
                                    200,
                                    events.stream().filter("GET"::equals).count() == 1
                                            ? "generating"
                                            : "generated");
                        },
                        journal,
                        clock);
        final var result = poller.drive(clock::advance);
        assertEquals("PENDING_DOWNLOAD_AND_INDEPENDENT_REFERENCE", result.status());
        assertEquals(2, result.control().pollsUsed());
        assertEquals(
                List.of("ADMIT", "RESERVE", "GET", "COMMIT", "ADMIT", "RESERVE", "GET", "COMMIT"),
                events);
        assertEquals(Duration.ofSeconds(6).toNanos(), clock.nanos.get());
    }

    @Test
    void generatingAtCapRemainsResumableAndNeverRepeatsPost() throws Exception {
        final var events = new ArrayList<String>();
        final var clock = new FakeClock();
        final var poller =
                new ExportFilePoller(
                        control(2),
                        new IndependentExportCycle.SerialChannel(clock.nanos::get),
                        (path, timeout) -> {
                            events.add("GET");
                            return reply(200, "generating");
                        },
                        journal(events, false),
                        clock);
        assertEquals("PENDING_POLL_BUDGET_EXHAUSTED", poller.drive(clock::advance).status());
        assertEquals(2, events.stream().filter("GET"::equals).count());
        assertTrue(events.stream().noneMatch("POST"::equals));
    }

    @Test
    void failedPersistenceBlocksAllSubsequentQueries() throws Exception {
        final var events = new ArrayList<String>();
        final var clock = new FakeClock();
        final var poller =
                new ExportFilePoller(
                        control(3),
                        new IndependentExportCycle.SerialChannel(clock.nanos::get),
                        (path, timeout) -> {
                            events.add("GET");
                            return reply(200, "generating");
                        },
                        journal(events, true),
                        clock);
        assertThrows(IllegalArgumentException.class, () -> poller.drive(clock::advance));
        assertEquals("PENDING_RECONCILIATION", poller.drive(clock::advance).status());
        assertEquals(1, events.stream().filter("GET"::equals).count());
    }

    @Test
    void stop429IsRecordedAndCannotBeRetriedByDriver() throws Exception {
        final var events = new ArrayList<String>();
        final var clock = new FakeClock();
        final var poller =
                new ExportFilePoller(
                        control(3),
                        new IndependentExportCycle.SerialChannel(clock.nanos::get),
                        (path, timeout) -> {
                            events.add("GET");
                            return reply(429, "generating");
                        },
                        journal(events, false),
                        clock);
        assertEquals("TERMINAL_STOPPED", poller.drive(clock::advance).status());
        assertEquals(1, events.stream().filter("GET"::equals).count());
        assertEquals("COMMIT", events.get(events.size() - 1));
    }

    @Test
    void supplierErrorIsTerminalEvenWithHttp200() throws Exception {
        final var c = control(1);
        final var fact =
                ExportFileLifecycle.observe(
                        c,
                        ExportFileLifecycle.plan(c, admission(c), NOW.plusSeconds(3)),
                        reply(200, "error"),
                        NOW.plusSeconds(4));
        assertEquals(ExportFileLifecycle.State.ERROR, fact.state());
    }

    private static ExportFilePoller.AdmittingJournal journal(
            final List<String> events, final boolean fail) {
        return new ExportFilePoller.AdmittingJournal() {
            @Override
            public ExportFileLifecycle.Admission nextAdmission(
                    final ExportFileLifecycle.Control c) {
                events.add("ADMIT");
                return admission(c);
            }

            @Override
            public void reserve(final ExportFileLifecycle.PollPlan plan) {
                events.add("RESERVE");
            }

            @Override
            public String appendAndConfirm(final ExportFileLifecycle.Receipt receipt)
                    throws Exception {
                events.add("COMMIT");
                return fail ? "f".repeat(64) : receipt.sha256();
            }
        };
    }

    private static final class FakeClock extends Clock {
        private final AtomicLong nanos = new AtomicLong();

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(final ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return NOW.plusNanos(nanos.get());
        }

        void advance(final Duration delay) {
            nanos.addAndGet(delay.toNanos());
        }
    }
}
