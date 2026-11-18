package br.com.esl.etl.v2.plataforma.reconciliacao.exportacao;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.plataforma.qualificacao.CampaignJournal;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CampaignJournalFactBatchTest {
    private static final String SELECTION = "a".repeat(64);
    private static final String CODE = "b".repeat(64);
    private static final String EVIDENCE = "c".repeat(64);

    @TempDir Path directory;

    @Test
    void factTripletMatchesThreeHistoricalAppendsByteForByteAfterReopen() throws Exception {
        final var legacy = campaign("legacy");
        final var batched = campaign("batched");
        final UUID nonce = UUID.randomUUID();
        legacy.append("fact-one", nonce, CampaignJournal.Kind.RESERVED, "FACT_APPEND");
        legacy.append("fact-one", nonce, CampaignJournal.Kind.STARTED, "FACT_APPEND");
        final var expected =
                legacy.append("fact-one", nonce, CampaignJournal.Kind.EVIDENCE, EVIDENCE);
        final var actual = batched.appendFactRecord("fact-one", nonce, EVIDENCE);
        assertEquals(expected, actual);
        final var reopened = CampaignJournal.open(batched.root(), SELECTION, CODE);
        assertEquals(legacy.read(), reopened.read());
        assertEquals(
                List.of(
                        CampaignJournal.Kind.RESERVED,
                        CampaignJournal.Kind.STARTED,
                        CampaignJournal.Kind.EVIDENCE),
                reopened.read().stream().map(CampaignJournal.Event::kind).toList());
        for (int sequence = 1; sequence <= 3; sequence++) {
            final String name = String.format(Locale.ROOT, "%04d.json", sequence);
            assertArrayEquals(
                    Files.readAllBytes(legacy.root().resolve(name)),
                    Files.readAllBytes(reopened.root().resolve(name)));
            assertFalse(Files.exists(reopened.root().resolve(name + ".partial")));
        }
        assertEquals("OUTCOME_UNKNOWN", reopened.status().get("fact-one").state().name());
    }

    @Test
    void persistentFactsKeepTheirPayloadAndReopenWithTheSameContentHashGuard() throws Exception {
        final Path cycle = directory.resolve("persistent");
        final UUID attempt = UUID.randomUUID();
        final var payload =
                ExportValidationSupport.JSON.createObjectNode().put("synthetic", "value");
        final String sha;
        try (var journal = PersistentExportJournal.create(cycle, SELECTION, CODE, attempt)) {
            sha = journal.record("SYNTHETIC_FACT", payload);
            assertEquals(payload, journal.latest("SYNTHETIC_FACT"));
            assertEquals(1, journal.count("SYNTHETIC_FACT"));
        }
        final byte[] retained = Files.readAllBytes(cycle.resolve("facts").resolve(sha + ".json"));
        try (var journal = PersistentExportJournal.open(cycle, SELECTION, CODE, attempt)) {
            assertEquals(sha, journal.latestPin("SYNTHETIC_FACT").sha256());
            assertEquals(payload, journal.latest("SYNTHETIC_FACT"));
            assertArrayEquals(
                    retained, Files.readAllBytes(journal.latestPin("SYNTHETIC_FACT").path()));
            assertEquals(0, journal.startedWires());
        }
        assertEquals(
                5, CampaignJournal.open(cycle.resolve("control"), SELECTION, CODE).read().size());
        Files.writeString(
                cycle.resolve("facts").resolve(sha + ".json"), "{}", StandardCharsets.UTF_8);
        assertThrows(
                IllegalArgumentException.class,
                () -> PersistentExportJournal.open(cycle, SELECTION, CODE, attempt));
    }

    @Test
    void earlierEventCorruptionStopsBatchBeforeAnyNewEventIsPublished() throws Exception {
        final var journal = campaign("corrupted");
        journal.appendFactRecord("fact-one", UUID.randomUUID(), EVIDENCE);
        final Path first = journal.root().resolve("0001.json");
        final String original = Files.readString(first);
        Files.writeString(
                first, original.replace("FACT_APPEND", "FACT_XPPEND"), StandardCharsets.UTF_8);
        assertThrows(
                IllegalArgumentException.class,
                () -> journal.appendFactRecord("fact-two", UUID.randomUUID(), EVIDENCE));
        assertThrows(
                IllegalArgumentException.class,
                () -> CampaignJournal.open(journal.root(), SELECTION, CODE));
        assertFalse(Files.exists(journal.root().resolve("0004.json")));
    }

    @Test
    void invalidCaseNonceEvidenceAndAttemptReuseNeverPublishAnotherTriplet() throws Exception {
        final var journal = campaign("invalid");
        final UUID nonce = UUID.randomUUID();
        for (final String invalid : List.of("", "Uppercase", "space case", "x".repeat(41))) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> journal.appendFactRecord(invalid, nonce, EVIDENCE));
        }
        assertThrows(
                IllegalArgumentException.class,
                () -> journal.appendFactRecord("valid-case", null, EVIDENCE));
        for (final String invalid : List.of("", "A".repeat(64), "c".repeat(63), "not-a-digest")) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> journal.appendFactRecord("valid-case", nonce, invalid));
        }
        assertThrows(
                IllegalArgumentException.class,
                () -> journal.appendFactRecord("valid-case", nonce, null));
        assertTrue(journal.read().isEmpty());
        journal.appendFactRecord("valid-case", nonce, EVIDENCE);
        assertThrows(
                IllegalArgumentException.class,
                () -> journal.appendFactRecord("valid-case", UUID.randomUUID(), EVIDENCE));
        assertThrows(
                IllegalArgumentException.class,
                () -> journal.appendFactRecord("another-case", nonce, EVIDENCE));
        assertEquals(3, journal.read().size());
        assertFalse(Files.exists(journal.root().resolve("0004.json")));
    }

    @Test
    void batchHonorsTheExisting512EventCeilingAtBothSidesOfTheTripletBoundary() throws Exception {
        final var exact = campaign("limit-exact");
        seedReservations(exact, 509);
        assertEquals(509, exact.read().size());
        assertEquals(
                512, exact.appendFactRecord("last-fact", UUID.randomUUID(), EVIDENCE).sequence());
        assertEquals(512, CampaignJournal.open(exact.root(), SELECTION, CODE).read().size());
        assertThrows(
                IllegalArgumentException.class,
                () -> exact.appendFactRecord("overflow", UUID.randomUUID(), EVIDENCE));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        exact.append(
                                "overflow",
                                UUID.randomUUID(),
                                CampaignJournal.Kind.RESERVED,
                                "SYNTHETIC"));
        assertFalse(Files.exists(exact.root().resolve("0513.json")));

        final var tooFull = campaign("limit-refused");
        seedReservations(tooFull, 510);
        assertThrows(
                IllegalArgumentException.class,
                () -> tooFull.appendFactRecord("last-fact", UUID.randomUUID(), EVIDENCE));
        assertEquals(510, CampaignJournal.open(tooFull.root(), SELECTION, CODE).read().size());
        assertFalse(Files.exists(tooFull.root().resolve("0511.json")));
    }

    @Test
    void defaultEventChainFailureAfterReservationOrStartLeavesAnUnacceptedPersistentPrefix()
            throws Exception {
        for (final var failAfter :
                List.of(CampaignJournal.Kind.RESERVED, CampaignJournal.Kind.STARTED)) {
            final Path cycle = directory.resolve("prefix-" + failAfter.name());
            final UUID attempt = UUID.randomUUID();
            final var chain = new FailingChain(cycle.resolve("control"), failAfter, false);
            try (var journal =
                    PersistentExportJournal.syntheticFixture(
                            cycle, SELECTION, CODE, attempt, true, chain)) {
                assertThrows(
                        IOException.class,
                        () ->
                                journal.record(
                                        "SYNTHETIC_FACT",
                                        ExportValidationSupport.JSON.createObjectNode()));
            }
            final int prefix = failAfter == CampaignJournal.Kind.RESERVED ? 1 : 2;
            assertEquals(prefix, chain.factAppendCalls);
            final var readback =
                    CampaignJournal.open(cycle.resolve("control"), SELECTION, CODE).read();
            assertEquals(2 + prefix, readback.size());
            assertEquals(failAfter, readback.get(readback.size() - 1).kind());
            assertThrows(
                    IllegalArgumentException.class,
                    () -> PersistentExportJournal.open(cycle, SELECTION, CODE, attempt));
        }
    }

    @Test
    void envelopePreparedBeforeReservationCannotBecomeAnAdmissionReceiptOrAuthority()
            throws Exception {
        final Path cycle = directory.resolve("orphan");
        final UUID attempt = UUID.randomUUID();
        final var chain = new FailingChain(cycle.resolve("control"), null, true);
        try (var journal =
                PersistentExportJournal.syntheticFixture(
                        cycle, SELECTION, CODE, attempt, true, chain)) {
            assertThrows(
                    IOException.class,
                    () ->
                            journal.record(
                                    "HTTP_ADMITTED",
                                    ExportValidationSupport.JSON.createObjectNode()));
        }
        try (var facts = Files.list(cycle.resolve("facts"))) {
            assertEquals(2, facts.count());
        }
        assertEquals(0, chain.factAppendCalls);
        assertEquals(
                2, CampaignJournal.open(cycle.resolve("control"), SELECTION, CODE).read().size());
        try (var journal = PersistentExportJournal.open(cycle, SELECTION, CODE, attempt)) {
            assertNull(journal.latest("HTTP_ADMITTED"));
            assertEquals(0, journal.count("HTTP_ADMITTED"));
            assertEquals(0, journal.count("HTTP_COMPLETE"));
            assertEquals(0, journal.startedWires());
            assertThrows(IllegalArgumentException.class, journal::resumeControl);
        }
    }

    @Test
    void genericAppendRetainsBarrierRollbackTerminalAndNonceRulesBesideFactBatches()
            throws Exception {
        final var journal = campaign("generic-history");
        final UUID nonce = UUID.randomUUID();
        journal.append("legacy", nonce, CampaignJournal.Kind.RESERVED, "SYNTHETIC");
        journal.append("legacy", nonce, CampaignJournal.Kind.STARTED, "SYNTHETIC");
        journal.append("legacy", nonce, CampaignJournal.Kind.BARRIER, "SYNTHETIC_BARRIER");
        journal.appendFactRecord("independent-fact", UUID.randomUUID(), EVIDENCE);
        journal.append("legacy", nonce, CampaignJournal.Kind.ROLLBACK, "SYNTHETIC_ROLLBACK");
        journal.append("legacy", nonce, CampaignJournal.Kind.TERMINAL, "PASS_LOCAL");
        final var reopened = CampaignJournal.open(journal.root(), SELECTION, CODE);
        assertEquals(8, reopened.read().size());
        assertEquals("PASS_LOCAL", reopened.status().get("legacy").state().name());
        assertEquals("OUTCOME_UNKNOWN", reopened.status().get("independent-fact").state().name());
        assertThrows(
                IllegalArgumentException.class,
                () -> reopened.append("legacy", nonce, CampaignJournal.Kind.STARTED, "SYNTHETIC"));
        assertEquals(8, reopened.read().size());
    }

    private CampaignJournal campaign(final String name) throws IOException {
        return CampaignJournal.create(directory.resolve(name), SELECTION, CODE);
    }

    /**
     * Authored valid prefix avoids hundreds of forced appends just to reach the unchanged bound.
     */
    private static void seedReservations(final CampaignJournal journal, final int count)
            throws IOException {
        String previous =
                ExportValidationSupport.sha256(
                        Files.readAllBytes(journal.root().resolve("header.json")));
        for (int sequence = 1; sequence <= count; sequence++) {
            final String key = "authored-" + sequence;
            final var node =
                    ExportValidationSupport.JSON
                            .createObjectNode()
                            .put("version", 1)
                            .put("sequence", sequence)
                            .put("campaign", SELECTION)
                            .put("package", CODE)
                            .put("previous", previous)
                            .put("case", key)
                            .put(
                                    "nonce",
                                    UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8))
                                            .toString())
                            .put("kind", "RESERVED")
                            .put("detail", "SYNTHETIC");
            final byte[] bytes = (node + "\n").getBytes(StandardCharsets.UTF_8);
            Files.write(
                    journal.root().resolve(String.format(Locale.ROOT, "%04d.json", sequence)),
                    bytes);
            previous = ExportValidationSupport.sha256(bytes);
        }
    }

    /** Leaves EventChain.appendFactRecord at its three-append default for compatibility tests. */
    private static final class FailingChain implements PersistentExportJournal.EventChain {
        private final Path control;
        private final CampaignJournal.Kind failAfter;
        private final boolean beforeReservation;
        private CampaignJournal selected;
        private int factAppendCalls;

        private FailingChain(
                final Path control,
                final CampaignJournal.Kind failAfter,
                final boolean beforeReservation) {
            this.control = control;
            this.failAfter = failAfter;
            this.beforeReservation = beforeReservation;
        }

        @Override
        public CampaignJournal.Event append(
                final String caseId,
                final UUID nonce,
                final CampaignJournal.Kind kind,
                final String detail)
                throws IOException {
            if (selected == null) {
                selected = CampaignJournal.create(control, SELECTION, CODE);
            }
            final boolean fact = caseId.startsWith("export-fact-");
            if (fact && beforeReservation && kind == CampaignJournal.Kind.RESERVED) {
                throw new IOException("AUTHORED_BEFORE_FACT_RESERVATION");
            }
            final var event = selected.append(caseId, nonce, kind, detail);
            if (fact) {
                factAppendCalls++;
                if (kind == failAfter) {
                    throw new IOException("AUTHORED_AFTER_FACT_PREFIX");
                }
            }
            return event;
        }

        @Override
        public List<CampaignJournal.Event> read() throws IOException {
            return selected.read();
        }
    }
}
