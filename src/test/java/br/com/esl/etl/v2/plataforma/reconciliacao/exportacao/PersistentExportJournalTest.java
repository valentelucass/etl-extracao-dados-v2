package br.com.esl.etl.v2.plataforma.reconciliacao.exportacao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PersistentExportJournalTest {
    @TempDir Path directory;
    private static final Instant NOW = Instant.parse("2036-03-20T15:00:00Z");
    private final UUID attempt = UUID.randomUUID();

    private PersistentExportJournal create() throws Exception {
        return PersistentExportJournal.create(
                directory.resolve("cycle"), "a".repeat(64), "b".repeat(64), attempt);
    }

    private PersistentExportJournal open() throws Exception {
        return PersistentExportJournal.open(
                directory.resolve("cycle"), "a".repeat(64), "b".repeat(64), attempt);
    }

    private void initial(final PersistentExportJournal journal) throws Exception {
        journal.reserve(attempt.toString(), "c".repeat(64));
        final var receipt =
                new IndependentExportCycle.PostReceipt(
                        attempt.toString(), "c".repeat(64), 200, "d".repeat(64), NOW);
        final String sha = journal.appendAndConfirm(receipt);
        final var filters =
                ExportValidationSupport.JSON.readTree(
                        "{\"freights\":{\"service_at\":\"2036-03-20 - 2036-03-26\"}}");
        final var submitted =
                new IndependentExportCycle.Submission(
                        "PENDING",
                        attempt.toString(),
                        "123",
                        sha,
                        ExportValidationSupport.sha256(
                                ExportValidationSupport.canonicalBytes(filters)),
                        NOW,
                        false);
        journal.initial(submitted, submitted.pendingControl(1, NOW.plusSeconds(120)));
    }

    @Test
    void reservedPostCannotBeRepeatedAfterRestart() throws Exception {
        try (var journal = create()) {
            journal.reserve(attempt.toString(), "c".repeat(64));
        }
        try (var journal = open()) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> journal.reserve(attempt.toString(), "c".repeat(64)));
            assertThrows(IllegalArgumentException.class, journal::resumeControl);
        }
    }

    @Test
    void anUnknownWireOutcomeBlocksEveryLaterHttpAdmissionAfterRestart() throws Exception {
        try (var journal = create()) {
            journal.record(
                    "HTTP_ADMITTED",
                    ExportValidationSupport.JSON.createObjectNode().put("operation", "synthetic"));
        }
        try (var journal = open()) {
            assertThrows(IllegalArgumentException.class, () -> journal.requireRequestBudget(2));
            assertEquals(1, journal.count("HTTP_ADMITTED"));
            assertEquals(0, journal.count("HTTP_COMPLETE"));
        }
    }

    @Test
    void reservedPollWithoutReceiptCannotBeTurnedIntoANewOperation() throws Exception {
        try (var journal = create()) {
            initial(journal);
            final var control = journal.resumeControl();
            final var admission = journal.nextAdmission(control);
            journal.reserve(ExportFileLifecycle.plan(control, admission, NOW.plusSeconds(3)));
        }
        try (var journal = open()) {
            assertTrue(journal.hasUnresolvedPoll());
            assertThrows(IllegalArgumentException.class, journal::resumeControl);
        }
    }

    @Test
    void confirmedPollReplaysExactlyAndExplicitContinuationKeepsTheSameFileAndCounts()
            throws Exception {
        String prior;
        try (var journal = create()) {
            initial(journal);
            final var control = journal.resumeControl();
            final var plan =
                    ExportFileLifecycle.plan(
                            control, journal.nextAdmission(control), NOW.plusSeconds(3));
            journal.reserve(plan);
            final var reply =
                    new IndependentExportCycle.Reply(
                            200,
                            ("{\"id\":123,\"report_id\":6389,\"status\":\"generating\",\"filters\":"
                                            + "{\"freights\":{\"service_at\":\"2036-03-20 - 2036-03-26\"}}}")
                                    .getBytes(StandardCharsets.UTF_8));
            final var receipt =
                    ExportFileLifecycle.observe(control, plan, reply, NOW.plusSeconds(4));
            prior = journal.appendAndConfirm(receipt);
        }
        try (var journal = open()) {
            final var replayed = journal.resumeControl();
            assertEquals(prior, replayed.latestReceiptSha256());
            assertEquals(1, replayed.pollsUsed());
            final var grant =
                    ExportValidationSupport.JSON
                            .createObjectNode()
                            .put("grantId", UUID.randomUUID().toString())
                            .put("previousReceiptSha256", prior)
                            .put("additionalPolls", 2);
            journal.regrant(grant, "e".repeat(64), NOW.plusSeconds(3600), NOW.plusSeconds(3720));
            final var renewed = journal.resumeControl();
            assertEquals("123", renewed.fileId());
            assertEquals(replayed.postReceiptSha256(), renewed.postReceiptSha256());
            assertEquals(1, renewed.pollsUsed());
            assertEquals(3, renewed.maximumPolls());
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            journal.regrant(
                                    grant,
                                    "e".repeat(64),
                                    NOW.plusSeconds(3600),
                                    NOW.plusSeconds(3720)));
            assertThrows(
                    IllegalArgumentException.class,
                    () -> journal.reserve(attempt.toString(), "c".repeat(64)));
        }
    }

    @Test
    void regrantCannotChangeThePreviousReceiptOrInventAnUnboundedBudget() throws Exception {
        try (var journal = create()) {
            initial(journal);
            final var grant =
                    ExportValidationSupport.JSON
                            .createObjectNode()
                            .put("grantId", UUID.randomUUID().toString())
                            .put("previousReceiptSha256", "f".repeat(64))
                            .put("additionalPolls", 2);
            assertThrows(
                    IllegalArgumentException.class,
                    () -> journal.regrant(grant, "e".repeat(64), NOW, NOW.plusSeconds(120)));
            final var prior = journal.resumeControl();
            assertThrows(
                    IllegalArgumentException.class,
                    () -> ExportFileLifecycle.regrant(prior, 11, NOW, NOW.plusSeconds(120)));
            assertThrows(
                    IllegalArgumentException.class,
                    () -> ExportFileLifecycle.regrant(prior, 1, NOW, NOW.plusSeconds(241)));
        }
    }
}
