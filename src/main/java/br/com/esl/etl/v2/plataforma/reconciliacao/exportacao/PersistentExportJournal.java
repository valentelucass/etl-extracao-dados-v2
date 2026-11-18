package br.com.esl.etl.v2.plataforma.reconciliacao.exportacao;

import static br.com.esl.etl.v2.plataforma.reconciliacao.exportacao.ExportValidationSupport.require;

import br.com.esl.etl.v2.plataforma.qualificacao.CampaignJournal;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Concrete durable adapters for the source cycle. Uses the existing immutable CampaignJournal
 * chain, plus separately pinned response/state facts. No journal event grants execution authority.
 */
public final class PersistentExportJournal
        implements IndependentExportCycle.PostJournal,
                ExportFilePoller.AdmittingJournal,
                AutoCloseable {
    private static final String CASE = "independent-export";
    private static final Pattern DIGEST_PATTERN = Pattern.compile("[a-f0-9]{64}");
    private static final Pattern KIND_PATTERN = Pattern.compile("[A-Z_]{1,32}");
    private final Path facts;
    private final EventChain journal;
    private final UUID attempt;
    private final FileChannel lockChannel;
    private final FileLock lock;
    private final boolean forceFacts;

    private record StoredFact(
            String kind, String payloadSha256, String envelopeSha256, JsonNode value) {}

    interface EventChain {
        CampaignJournal.Event append(
                String caseId, UUID nonce, CampaignJournal.Kind kind, String detail)
                throws IOException;

        default CampaignJournal.Event appendFactRecord(
                final String caseId, final UUID nonce, final String evidenceSha)
                throws IOException {
            append(caseId, nonce, CampaignJournal.Kind.RESERVED, "FACT_APPEND");
            append(caseId, nonce, CampaignJournal.Kind.STARTED, "FACT_APPEND");
            return append(caseId, nonce, CampaignJournal.Kind.EVIDENCE, evidenceSha);
        }

        List<CampaignJournal.Event> read() throws IOException;
    }

    private static EventChain durableChain(final CampaignJournal selected) {
        return new EventChain() {
            @Override
            public CampaignJournal.Event append(
                    final String caseId,
                    final UUID nonce,
                    final CampaignJournal.Kind kind,
                    final String detail)
                    throws IOException {
                return selected.append(caseId, nonce, kind, detail);
            }

            @Override
            public CampaignJournal.Event appendFactRecord(
                    final String caseId, final UUID nonce, final String evidenceSha)
                    throws IOException {
                return selected.appendFactRecord(caseId, nonce, evidenceSha);
            }

            @Override
            public List<CampaignJournal.Event> read() throws IOException {
                return selected.read();
            }
        };
    }

    private PersistentExportJournal(
            final Path directory,
            final String selectionSha,
            final String codeSha,
            final UUID attempt,
            final boolean create,
            final boolean forceFacts,
            final EventChain fixtureChain)
            throws IOException {
        this.attempt = attempt;
        this.forceFacts = forceFacts;
        require(
                directory.isAbsolute()
                        && DIGEST_PATTERN.matcher(selectionSha).matches()
                        && DIGEST_PATTERN.matcher(codeSha).matches(),
                "EXPORT_JOURNAL_BINDING");
        if (create) {
            Files.createDirectory(directory);
            Files.createDirectory(directory.resolve("facts"));
            Files.createFile(directory.resolve("owner.lock"));
        }
        for (Path part = directory; part != null; part = part.getParent()) {
            require(!Files.isSymbolicLink(part), "EXPORT_JOURNAL_LINK");
        }
        this.facts = directory.resolve("facts");
        this.lockChannel =
                FileChannel.open(directory.resolve("owner.lock"), StandardOpenOption.WRITE);
        FileLock acquired = null;
        EventChain opened = null;
        try {
            acquired = lockChannel.tryLock();
            require(acquired != null, "EXPORT_JOURNAL_OWNER_BUSY");
            opened =
                    fixtureChain == null
                            ? durableChain(
                                    create
                                            ? CampaignJournal.create(
                                                    directory.resolve("control"),
                                                    selectionSha,
                                                    codeSha)
                                            : CampaignJournal.open(
                                                    directory.resolve("control"),
                                                    selectionSha,
                                                    codeSha))
                            : fixtureChain;
            if (create) {
                opened.append(CASE, attempt, CampaignJournal.Kind.RESERVED, "SOURCE_CYCLE");
                opened.append(CASE, attempt, CampaignJournal.Kind.STARTED, "SOURCE_CYCLE");
            }
            final List<CampaignJournal.Event> events = opened.read();
            readEntries(events);
        } catch (final IOException | RuntimeException failure) {
            if (acquired != null) {
                acquired.close();
            }
            lockChannel.close();
            throw failure;
        }
        this.lock = acquired;
        this.journal = opened;
    }

    public static PersistentExportJournal create(
            final Path directory,
            final String selectionSha,
            final String codeSha,
            final UUID attempt)
            throws IOException {
        return new PersistentExportJournal(
                directory, selectionSha, codeSha, attempt, true, true, null);
    }

    public static PersistentExportJournal open(
            final Path directory,
            final String selectionSha,
            final String codeSha,
            final UUID attempt)
            throws IOException {
        return new PersistentExportJournal(
                directory, selectionSha, codeSha, attempt, false, true, null);
    }

    // Composition fixtures provide their own event port and do not prove physical durability.
    // Public production factories always select CampaignJournal and force every fact.
    static PersistentExportJournal syntheticFixture(
            final Path directory,
            final String selectionSha,
            final String codeSha,
            final UUID attempt,
            final boolean create,
            final EventChain chain)
            throws IOException {
        return new PersistentExportJournal(
                directory,
                selectionSha,
                codeSha,
                attempt,
                create,
                false,
                java.util.Objects.requireNonNull(chain));
    }

    private String fact(final Object value) throws IOException {
        final byte[] bytes = ExportValidationSupport.JSON.writeValueAsBytes(value);
        require(bytes.length > 0 && bytes.length <= 262144, "EXPORT_JOURNAL_FACT_BOUND");
        final String sha = ExportValidationSupport.sha256(bytes);
        final Path path = facts.resolve(sha + ".json");
        if (!Files.exists(path)) {
            try (var file =
                    FileChannel.open(
                            path, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
                final var buffer = ByteBuffer.wrap(bytes);
                while (buffer.hasRemaining()) {
                    file.write(buffer);
                }
                if (forceFacts) {
                    file.force(true);
                }
            }
        }
        final byte[] readback = Files.readAllBytes(path);
        require(
                sha.equals(ExportValidationSupport.sha256(readback)),
                "EXPORT_JOURNAL_FACT_READBACK");
        return sha;
    }

    private JsonNode readFact(final String sha) throws IOException {
        final Path path = facts.resolve(sha + ".json");
        require(
                DIGEST_PATTERN.matcher(sha).matches()
                        && !Files.isSymbolicLink(path)
                        && Files.size(path) <= 262144,
                "EXPORT_JOURNAL_FACT_PIN");
        final byte[] bytes = Files.readAllBytes(path);
        require(sha.equals(ExportValidationSupport.sha256(bytes)), "EXPORT_JOURNAL_FACT_DRIFT");
        return ExportValidationSupport.JSON.readTree(bytes);
    }

    public synchronized String record(final String kind, final Object value) throws IOException {
        require(KIND_PATTERN.matcher(kind).matches(), "EXPORT_JOURNAL_KIND");
        final var events = journal.read();
        final var entries = readEntries(events);
        require(events.size() <= 509, "EXPORT_JOURNAL_FACT_BUDGET_EXHAUSTED_PENDING");
        final int ordinal = entries.size() + 1;
        final String caseId = factCase(ordinal);
        final UUID nonce = factNonce(ordinal);
        // This case describes appending one fact, not another Source request or qualification.
        // Prepare durable content-addressed facts before publishing the logical triplet.
        // An orphan fact is not a journal event and cannot authorize any operation.
        final String sha = fact(value);
        final String previous =
                entries.isEmpty()
                        ? events.get(1).sha256()
                        : entries.get(entries.size() - 1).envelopeSha256();
        final var envelope =
                ExportValidationSupport.JSON
                        .createObjectNode()
                        .put("schema", "INDEPENDENT_EXPORT_JOURNAL_FACT_V2")
                        .put("attemptId", attempt.toString())
                        .put("ordinal", ordinal)
                        .put("kind", kind)
                        .put("payloadSha256", sha)
                        .put("previousEnvelopeSha256", previous)
                        .put("cycleStartedEventSha256", events.get(1).sha256());
        final String evidence = fact(envelope);
        final var event = journal.appendFactRecord(caseId, nonce, evidence);
        final var reread = journal.read();
        require(
                reread.get(reread.size() - 1).sha256().equals(event.sha256()),
                "EXPORT_CHAIN_READBACK");
        final var confirmed = readEntries(reread);
        require(
                confirmed.size() == ordinal
                        && confirmed.get(ordinal - 1).payloadSha256().equals(sha),
                "EXPORT_FACT_ENVELOPE_READBACK");
        return sha;
    }

    public synchronized JsonNode latest(final String kind) throws IOException {
        JsonNode found = null;
        for (final var entry : readEntries(journal.read())) {
            if (kind.equals(entry.kind())) {
                found = entry.value();
            }
        }
        return found;
    }

    public synchronized long count(final String kind) throws IOException {
        return readEntries(journal.read()).stream()
                .filter(entry -> kind.equals(entry.kind()))
                .count();
    }

    public synchronized long startedWires() throws IOException {
        long count = 0;
        for (final var entry : readEntries(journal.read())) {
            if ("ROOT_WIRE_RECEIPT".equals(entry.kind())) {
                count += entry.value().path("started").asBoolean() ? 1 : 0;
            }
        }
        return count;
    }

    public synchronized ExportValidationSupport.Pin latestPin(final String kind)
            throws IOException {
        String sha = null;
        for (final var entry : readEntries(journal.read())) {
            if (kind.equals(entry.kind())) {
                sha = entry.payloadSha256();
            }
        }
        require(sha != null, "EXPORT_FACT_NOT_AVAILABLE");
        return pin(sha);
    }

    public synchronized ExportValidationSupport.Pin pin(final String sha) throws IOException {
        readFact(sha);
        final Path path = facts.resolve(sha + ".json");
        return new ExportValidationSupport.Pin(path, sha, Files.size(path));
    }

    public synchronized ExportValidationSupport.Pin rawResponse(final String expectedSha)
            throws IOException {
        for (final var entry : readEntries(journal.read())) {
            if ("ROOT_WIRE_RECEIPT".equals(entry.kind())) {
                final var receipt = entry.value();
                final var pin = receipt.path("bodyPin");
                if (expectedSha.equals(pin.path("sha256").asText())) {
                    return ExportValidationSupport.Pin.from(pin);
                }
            }
        }
        throw new IllegalArgumentException("EXPORT_PERSISTED_RESPONSE_NOT_FOUND");
    }

    public synchronized void requireRequestBudget(final int maximum) throws IOException {
        final var entries = readEntries(journal.read());
        final long used =
                entries.stream().filter(entry -> "HTTP_ADMITTED".equals(entry.kind())).count();
        final long complete =
                entries.stream().filter(entry -> "HTTP_COMPLETE".equals(entry.kind())).count();
        require(used == complete, "EXPORT_WIRE_RECONCILIATION_REQUIRED");
        require(used < maximum, "EXPORT_HTTP_BUDGET_EXHAUSTED_PENDING");
    }

    @Override
    public synchronized void reserve(final String attemptId, final String requestSha256)
            throws IOException {
        require(
                attempt.toString().equals(attemptId) && latest("POST_RESERVED") == null,
                "EXPORT_POST_ALREADY_RESERVED_RESUME_FILE");
        record(
                "POST_RESERVED",
                ExportValidationSupport.JSON
                        .createObjectNode()
                        .put("attemptId", attemptId)
                        .put("requestSha256", requestSha256));
    }

    @Override
    public synchronized String appendAndConfirm(final IndependentExportCycle.PostReceipt receipt)
            throws IOException {
        require(
                latest("POST_RESERVED") != null
                        && latest("POST_RESPONSE") == null
                        && attempt.toString().equals(receipt.attemptId()),
                "EXPORT_POST_RECEIPT_SEQUENCE");
        final String sha = record("POST_RESPONSE", receipt);
        require(sha.equals(receipt.sha256()), "EXPORT_POST_RECEIPT_HASH");
        return sha;
    }

    public synchronized void initial(
            final IndependentExportCycle.Submission submission,
            final ExportFileLifecycle.Control control)
            throws IOException {
        require(
                latest("INITIAL_CONTROL") == null
                        && latest("POST_RESPONSE") != null
                        && control.attemptId().equals(attempt.toString())
                        && control.fileId().equals(submission.fileId()),
                "EXPORT_INITIAL_CONTROL_BINDING");
        record("SUBMISSION", submission);
        record("INITIAL_CONTROL", control);
    }

    @Override
    public synchronized ExportFileLifecycle.Admission nextAdmission(
            final ExportFileLifecycle.Control control) throws IOException {
        require(
                control.attemptId().equals(attempt.toString()) && !hasUnresolvedPoll(),
                "EXPORT_UNRESOLVED_EFFECT");
        final var admission =
                new ExportFileLifecycle.Admission(
                        control.attemptId(),
                        control.fileId(),
                        control.postReceiptSha256(),
                        control.latestReceiptSha256(),
                        UUID.randomUUID().toString(),
                        UUID.randomUUID().toString());
        record("POLL_ADMISSION", admission);
        return admission;
    }

    @Override
    public synchronized void reserve(final ExportFileLifecycle.PollPlan plan) throws IOException {
        final var issued = latest("POLL_ADMISSION");
        require(
                issued != null
                        && !hasUnresolvedPoll()
                        && plan.operationId().equals(issued.path("operationId").asText())
                        && plan.nonce().equals(issued.path("nonce").asText())
                        && plan.previousReceiptSha256()
                                .equals(issued.path("previousReceiptSha256").asText()),
                "EXPORT_POLL_RESERVATION_BINDING");
        record("POLL_RESERVED", plan);
    }

    @Override
    public synchronized String appendAndConfirm(final ExportFileLifecycle.Receipt receipt)
            throws IOException {
        final var reserved = latest("POLL_RESERVED");
        require(
                reserved != null
                        && hasUnresolvedPoll()
                        && receipt.operationId().equals(reserved.path("operationId").asText())
                        && receipt.nonce().equals(reserved.path("nonce").asText())
                        && receipt.ordinal() == reserved.path("ordinal").asInt(),
                "EXPORT_POLL_RECEIPT_SEQUENCE");
        final String sha = record("POLL_RESPONSE", receipt);
        require(sha.equals(receipt.sha256()), "EXPORT_POLL_RECEIPT_HASH");
        return sha;
    }

    public synchronized boolean hasUnresolvedPoll() throws IOException {
        int reserved = 0;
        int observed = 0;
        for (final var entry : readEntries(journal.read())) {
            reserved += "POLL_RESERVED".equals(entry.kind()) ? 1 : 0;
            observed += "POLL_RESPONSE".equals(entry.kind()) ? 1 : 0;
        }
        require(observed <= reserved && reserved - observed <= 1, "EXPORT_POLL_CHAIN_COUNT");
        return reserved != observed;
    }

    public synchronized ExportFileLifecycle.Control resumeControl() throws IOException {
        require(!hasUnresolvedPoll(), "EXPORT_RECONCILIATION_REQUIRED_NO_NEW_GET");
        final var initial = latest("INITIAL_CONTROL");
        require(initial != null, "EXPORT_POST_RECONCILIATION_REQUIRED_NO_NEW_POST");
        var control =
                ExportValidationSupport.JSON.treeToValue(
                        initial, ExportFileLifecycle.Control.class);
        for (final var entry : readEntries(journal.read())) {
            if ("POLL_BUDGET_CONTROL".equals(entry.kind())) {
                final var fact = entry.value();
                require(
                        control.latestReceiptSha256()
                                .equals(fact.path("previousReceiptSha256").asText()),
                        "EXPORT_REPLAY_BUDGET_PREDECESSOR");
                control =
                        ExportValidationSupport.JSON.treeToValue(
                                fact.required("control"), ExportFileLifecycle.Control.class);
            }
            if ("POLL_RESPONSE".equals(entry.kind())) {
                final String sha = entry.payloadSha256();
                final var receipt =
                        ExportValidationSupport.JSON.treeToValue(
                                entry.value(), ExportFileLifecycle.Receipt.class);
                control = ExportFileLifecycle.confirmPersisted(control, receipt, sha);
            }
        }
        return control;
    }

    public synchronized void regrant(
            final JsonNode grant,
            final String selectionSha,
            final java.time.Instant issuedAt,
            final java.time.Instant deadline)
            throws IOException {
        final String id = ExportValidationSupport.text(grant, "grantId");
        UUID.fromString(id);
        for (final var entry : readEntries(journal.read())) {
            if ("POLL_BUDGET_CONTROL".equals(entry.kind())) {
                require(
                        !id.equals(entry.value().path("grantId").asText()),
                        "EXPORT_CONTINUATION_GRANT_ALREADY_USED");
            }
        }
        final var previous = resumeControl();
        require(
                previous.latestReceiptSha256()
                                .equals(
                                        ExportValidationSupport.text(
                                                grant, "previousReceiptSha256"))
                        && grant.path("additionalPolls").isIntegralNumber(),
                "EXPORT_CONTINUATION_RECEIPT_BINDING");
        final var renewed =
                ExportFileLifecycle.regrant(
                        previous, grant.path("additionalPolls").asInt(), issuedAt, deadline);
        record(
                "POLL_BUDGET_CONTROL",
                ExportValidationSupport.JSON
                        .createObjectNode()
                        .put("grantId", id)
                        .put("selectionSha256", selectionSha)
                        .put("previousReceiptSha256", previous.latestReceiptSha256())
                        .set("control", ExportValidationSupport.JSON.valueToTree(renewed)));
    }

    private static String factCase(final int ordinal) {
        return String.format(java.util.Locale.ROOT, "export-fact-%06d", ordinal);
    }

    private UUID factNonce(final int ordinal) {
        return UUID.nameUUIDFromBytes(
                (attempt + "|FACT_APPEND|" + ordinal).getBytes(StandardCharsets.UTF_8));
    }

    private List<StoredFact> readEntries(final List<CampaignJournal.Event> events)
            throws IOException {
        require(
                events.size() >= 2
                        && CASE.equals(events.get(0).caseId())
                        && CASE.equals(events.get(1).caseId())
                        && attempt.equals(events.get(0).nonce())
                        && attempt.equals(events.get(1).nonce())
                        && events.get(0).kind() == CampaignJournal.Kind.RESERVED
                        && events.get(1).kind() == CampaignJournal.Kind.STARTED
                        && "SOURCE_CYCLE".equals(events.get(0).detail())
                        && "SOURCE_CYCLE".equals(events.get(1).detail()),
                "EXPORT_JOURNAL_ATTEMPT");
        require((events.size() - 2) % 3 == 0, "EXPORT_FACT_APPEND_RECONCILIATION_REQUIRED");
        final List<StoredFact> entries = new ArrayList<>();
        final String cycle = events.get(1).sha256();
        String previous = cycle;
        for (int index = 2; index < events.size(); index += 3) {
            final int ordinal = entries.size() + 1;
            final String caseId = factCase(ordinal);
            final UUID nonce = factNonce(ordinal);
            final var reserved = events.get(index);
            final var started = events.get(index + 1);
            final var evidence = events.get(index + 2);
            for (final var event : List.of(reserved, started, evidence)) {
                require(
                        caseId.equals(event.caseId()) && nonce.equals(event.nonce()),
                        "EXPORT_FACT_CASE_BINDING");
            }
            require(
                    reserved.kind() == CampaignJournal.Kind.RESERVED
                            && started.kind() == CampaignJournal.Kind.STARTED
                            && evidence.kind() == CampaignJournal.Kind.EVIDENCE
                            && "FACT_APPEND".equals(reserved.detail())
                            && "FACT_APPEND".equals(started.detail())
                            && DIGEST_PATTERN.matcher(evidence.detail()).matches(),
                    "EXPORT_FACT_EVENT_SHAPE");
            final var envelope = readFact(evidence.detail());
            require(
                    "INDEPENDENT_EXPORT_JOURNAL_FACT_V2".equals(envelope.path("schema").asText())
                            && attempt.toString().equals(envelope.path("attemptId").asText())
                            && envelope.path("ordinal").isIntegralNumber()
                            && envelope.path("ordinal").asInt() == ordinal
                            && cycle.equals(envelope.path("cycleStartedEventSha256").asText())
                            && previous.equals(envelope.path("previousEnvelopeSha256").asText())
                            && KIND_PATTERN.matcher(envelope.path("kind").asText()).matches(),
                    "EXPORT_FACT_ENVELOPE_BINDING");
            final String payload = envelope.path("payloadSha256").asText();
            entries.add(
                    new StoredFact(
                            envelope.path("kind").asText(),
                            payload,
                            evidence.detail(),
                            readFact(payload)));
            previous = evidence.detail();
        }
        return List.copyOf(entries);
    }

    @Override
    public void close() throws IOException {
        try {
            lock.close();
        } finally {
            lockChannel.close();
        }
    }
}
