package br.com.esl.etl.v2.plataforma.qualificacao;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Immutable, hash-linked local control events. Missing terminal evidence is never inferred. */
public final class CampaignJournal {
    public enum Kind {
        RESERVED,
        DEFERRED,
        STARTED,
        BARRIER,
        EVIDENCE,
        ROLLBACK,
        TERMINAL,
        RECONCILED
    }

    public record Event(
            int sequence,
            String caseId,
            UUID nonce,
            Kind kind,
            String detail,
            String previous,
            String sha256) {}

    public record Status(QualificationGate.State state, UUID nonce, String reason) {}

    private final Path root;
    private final String campaignHash;
    private final String packageHash;

    private CampaignJournal(final Path root, final String campaignHash, final String packageHash) {
        if (campaignHash == null
                || !campaignHash.matches("[a-f0-9]{64}")
                || packageHash == null
                || !packageHash.matches("[a-f0-9]{64}")) {
            throw new IllegalArgumentException("QUAL_JOURNAL_PINS");
        }
        this.root = root.toAbsolutePath().normalize();
        this.campaignHash = campaignHash;
        this.packageHash = packageHash;
    }

    public static CampaignJournal create(
            final Path root, final String campaignHash, final String packageHash)
            throws IOException {
        final var journal = new CampaignJournal(root, campaignHash, packageHash);
        Files.createDirectory(journal.root);
        final var header =
                JsonNodeFactory.instance
                        .objectNode()
                        .put("version", "qualification-journal-v1")
                        .put("campaign", campaignHash)
                        .put("package", packageHash);
        atomic(journal.root.resolve("header.json"), header);
        Files.createFile(journal.root.resolve("journal.lock"));
        journal.read();
        return journal;
    }

    public static CampaignJournal open(
            final Path root, final String campaignHash, final String packageHash)
            throws IOException {
        final var journal = new CampaignJournal(root, campaignHash, packageHash);
        journal.read();
        return journal;
    }

    public Path root() {
        return root;
    }

    public Event append(final String caseId, final UUID nonce, final Kind kind, final String detail)
            throws IOException {
        if (caseId == null
                || !caseId.matches("[a-z][a-z0-9-]{0,39}")
                || nonce == null
                || kind == null
                || detail == null
                || !detail.matches("[A-Za-z0-9_:.,-]{1,256}")) {
            throw new IllegalArgumentException("QUAL_JOURNAL_EVENT");
        }
        final Path lockPath = root.resolve("journal.lock");
        QualificationJson.regular(lockPath);
        try (var channel = FileChannel.open(lockPath, StandardOpenOption.WRITE);
                var lock = channel.tryLock()) {
            if (lock == null) {
                throw new IllegalStateException("QUAL_JOURNAL_BUSY");
            }
            final var events = read();
            if (events.size() >= 512) {
                throw new IllegalArgumentException("QUAL_JOURNAL_LIMIT");
            }
            validateTransition(events, caseId, nonce, kind, detail);
            final String previous =
                    events.isEmpty()
                            ? QualificationJson.sha256(root.resolve("header.json"))
                            : events.get(events.size() - 1).sha256();
            final int sequence = events.size() + 1;
            final var node =
                    JsonNodeFactory.instance
                            .objectNode()
                            .put("version", 1)
                            .put("sequence", sequence)
                            .put("campaign", campaignHash)
                            .put("package", packageHash)
                            .put("previous", previous)
                            .put("case", caseId)
                            .put("nonce", nonce.toString())
                            .put("kind", kind.name())
                            .put("detail", detail);
            final var path =
                    root.resolve(String.format(java.util.Locale.ROOT, "%04d.json", sequence));
            atomic(path, node);
            return new Event(
                    sequence,
                    caseId,
                    nonce,
                    kind,
                    detail,
                    previous,
                    QualificationJson.sha256(path));
        }
    }

    public List<Event> read() throws IOException {
        final var headerPath = root.resolve("header.json");
        final var header = QualificationJson.read(headerPath, 1024);
        QualificationJson.fields(header, "version", "campaign", "package");
        if (!"qualification-journal-v1".equals(header.path("version").asText())
                || !campaignHash.equals(header.path("campaign").asText())
                || !packageHash.equals(header.path("package").asText())) {
            throw new IllegalArgumentException("QUAL_JOURNAL_REVISION");
        }
        final var files = new ArrayList<Path>();
        try (var entries = Files.list(root)) {
            final var iterator = entries.iterator();
            while (iterator.hasNext()) {
                final var file = iterator.next();
                QualificationJson.regular(file);
                final String name = file.getFileName().toString();
                if (name.equals("header.json") || name.equals("journal.lock")) {
                    continue;
                }
                if (!name.matches("[0-9]{4}\\.json") || files.size() >= 512) {
                    throw new IllegalArgumentException("QUAL_JOURNAL_UNDECLARED_FILE");
                }
                files.add(file);
            }
        }
        files.sort(java.util.Comparator.comparing(path -> path.getFileName().toString()));
        final var result = new ArrayList<Event>();
        String previous = QualificationJson.sha256(headerPath);
        for (final var path : files) {
            final var json = QualificationJson.read(path, 4096);
            QualificationJson.fields(
                    json,
                    "version",
                    "sequence",
                    "campaign",
                    "package",
                    "previous",
                    "case",
                    "nonce",
                    "kind",
                    "detail");
            final int sequence = QualificationJson.number(json, "sequence", 1, 512);
            if (QualificationJson.number(json, "version", 1, 1) != 1
                    || sequence != result.size() + 1
                    || !path.getFileName()
                            .toString()
                            .equals(String.format(java.util.Locale.ROOT, "%04d.json", sequence))
                    || !campaignHash.equals(json.path("campaign").asText())
                    || !packageHash.equals(json.path("package").asText())
                    || !previous.equals(json.path("previous").asText())) {
                throw new IllegalArgumentException("QUAL_JOURNAL_CHAIN");
            }
            final var event =
                    new Event(
                            sequence,
                            QualificationJson.text(json, "case", 40),
                            UUID.fromString(QualificationJson.text(json, "nonce", 36)),
                            Kind.valueOf(QualificationJson.text(json, "kind", 32)),
                            QualificationJson.text(json, "detail", 256),
                            previous,
                            QualificationJson.sha256(path));
            validateTransition(result, event.caseId(), event.nonce(), event.kind(), event.detail());
            result.add(event);
            previous = event.sha256();
        }
        return List.copyOf(result);
    }

    private static void validateTransition(
            final List<Event> events,
            final String caseId,
            final UUID nonce,
            final Kind kind,
            final String detail) {
        final var own = events.stream().filter(event -> event.caseId().equals(caseId)).toList();
        if (own.isEmpty()) {
            if (kind != Kind.RESERVED
                    || events.stream().anyMatch(event -> event.nonce().equals(nonce))) {
                throw new IllegalArgumentException("QUAL_JOURNAL_RESERVATION_REQUIRED");
            }
            return;
        }
        final var last = own.get(own.size() - 1);
        if (!last.nonce().equals(nonce)
                || kind == Kind.RESERVED
                || last.kind() == Kind.TERMINAL
                || last.kind() == Kind.DEFERRED
                || last.kind() == Kind.RECONCILED) {
            throw new IllegalArgumentException("QUAL_JOURNAL_ATTEMPT_CONFLICT");
        }
        final boolean allowed =
                switch (kind) {
                    case RESERVED -> false;
                    case DEFERRED ->
                            last.kind() == Kind.RESERVED && detail.equals("BLOCKED_DEPENDENCY");
                    case STARTED -> last.kind() == Kind.RESERVED;
                    case BARRIER ->
                            last.kind() == Kind.STARTED
                                    || last.kind() == Kind.BARRIER
                                    || last.kind() == Kind.ROLLBACK;
                    case EVIDENCE ->
                            (last.kind() == Kind.STARTED || last.kind() == Kind.BARRIER)
                                    && detail.matches("[a-f0-9]{64}");
                    case ROLLBACK ->
                            last.kind() == Kind.STARTED
                                    || last.kind() == Kind.BARRIER
                                    || last.kind() == Kind.EVIDENCE;
                    case TERMINAL ->
                            last.kind() == Kind.ROLLBACK
                                    && List.of(
                                                    "PASS_LOCAL",
                                                    "FAILED",
                                                    "BLOCKED_DEPENDENCY",
                                                    "CANCELLED")
                                            .contains(detail);
                    case RECONCILED ->
                            detail.equals("OUTCOME_UNKNOWN_ROLLBACK_CONFIRMED")
                                    || detail.equals("CANCELLED_ROLLBACK_CONFIRMED");
                };
        if (!allowed) {
            throw new IllegalArgumentException("QUAL_JOURNAL_TRANSITION");
        }
    }

    public Map<String, Status> status() throws IOException {
        final var result = new HashMap<String, Status>();
        for (final var event : read()) {
            final QualificationGate.State state;
            if (event.kind() == Kind.TERMINAL || event.kind() == Kind.DEFERRED) {
                state = QualificationGate.State.valueOf(event.detail());
            } else if (event.kind() == Kind.RECONCILED
                    && event.detail().equals("CANCELLED_ROLLBACK_CONFIRMED")) {
                state = QualificationGate.State.CANCELLED;
            } else {
                state = QualificationGate.State.OUTCOME_UNKNOWN;
            }
            result.put(event.caseId(), new Status(state, event.nonce(), event.detail()));
        }
        return Map.copyOf(result);
    }

    private static void atomic(final Path path, final JsonNode node) throws IOException {
        if (Files.exists(path)) {
            throw new IllegalArgumentException("QUAL_JOURNAL_IMMUTABLE");
        }
        final var temporary = path.resolveSibling(path.getFileName() + ".partial");
        try (var file =
                FileChannel.open(
                        temporary, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
            final var buffer = ByteBuffer.wrap((node + "\n").getBytes(StandardCharsets.UTF_8));
            while (buffer.hasRemaining()) {
                file.write(buffer);
            }
            file.force(true);
        }
        Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE);
    }
}
