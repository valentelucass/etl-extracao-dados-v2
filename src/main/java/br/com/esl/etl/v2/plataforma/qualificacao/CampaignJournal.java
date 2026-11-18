package br.com.esl.etl.v2.plataforma.qualificacao;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

/** Immutable, hash-linked local control events. Missing terminal evidence is never inferred. */
public final class CampaignJournal {
    private static final Pattern DIGEST_PATTERN = Pattern.compile("[a-f0-9]{64}");
    private static final Pattern CASE_PATTERN = Pattern.compile("[a-z][a-z0-9-]{0,39}");
    private static final Pattern DETAIL_PATTERN = Pattern.compile("[A-Za-z0-9_:.,-]{1,256}");
    private static final Pattern EVENT_FILE_PATTERN = Pattern.compile("[0-9]{4}\\.json");

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
                || !DIGEST_PATTERN.matcher(campaignHash).matches()
                || packageHash == null
                || !DIGEST_PATTERN.matcher(packageHash).matches()) {
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
                || !CASE_PATTERN.matcher(caseId).matches()
                || nonce == null
                || kind == null
                || detail == null
                || !DETAIL_PATTERN.matcher(detail).matches()) {
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

    /**
     * One logical FACT_APPEND triplet; intermediate crash prefixes remain durable and unaccepted.
     */
    public Event appendFactRecord(final String caseId, final UUID nonce, final String evidenceSha)
            throws IOException {
        if (caseId == null
                || !CASE_PATTERN.matcher(caseId).matches()
                || nonce == null
                || evidenceSha == null
                || !DIGEST_PATTERN.matcher(evidenceSha).matches()) {
            throw new IllegalArgumentException("QUAL_JOURNAL_EVENT");
        }
        final Path lockPath = root.resolve("journal.lock");
        QualificationJson.regular(lockPath);
        try (var channel = FileChannel.open(lockPath, StandardOpenOption.WRITE);
                var lock = channel.tryLock()) {
            if (lock == null) {
                throw new IllegalStateException("QUAL_JOURNAL_BUSY");
            }
            final var events = new ArrayList<>(read());
            if (events.size() > 509) {
                throw new IllegalArgumentException("QUAL_JOURNAL_LIMIT");
            }
            Event appended = null;
            for (final Kind kind : List.of(Kind.RESERVED, Kind.STARTED, Kind.EVIDENCE)) {
                final String detail = kind == Kind.EVIDENCE ? evidenceSha : "FACT_APPEND";
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
                appended =
                        new Event(
                                sequence,
                                caseId,
                                nonce,
                                kind,
                                detail,
                                previous,
                                QualificationJson.sha256(path));
                events.add(appended);
            }
            return appended;
        }
    }

    public List<Event> read() throws IOException {
        final var headerPath = root.resolve("header.json");
        final var headerSnapshot = QualificationJson.readSnapshot(headerPath, 1024);
        final var header = headerSnapshot.node();
        QualificationJson.fields(header, "version", "campaign", "package");
        if (!"qualification-journal-v1".equals(header.path("version").asText())
                || !campaignHash.equals(header.path("campaign").asText())
                || !packageHash.equals(header.path("package").asText())) {
            throw new IllegalArgumentException("QUAL_JOURNAL_REVISION");
        }
        final var files = new ArrayList<Map.Entry<Path, QualificationJson.ReadSnapshot>>();
        final var readers = new SnapshotReaders(files);
        try (var entries = Files.list(root)) {
            final var iterator = entries.iterator();
            while (iterator.hasNext()) {
                final var file = iterator.next();
                final String name = file.getFileName().toString();
                if (name.equals("header.json") || name.equals("journal.lock")) {
                    QualificationJson.regular(file);
                    continue;
                }
                if (!EVENT_FILE_PATTERN.matcher(name).matches() || readers.size() >= 512) {
                    QualificationJson.regular(file);
                    throw new IllegalArgumentException("QUAL_JOURNAL_UNDECLARED_FILE");
                }
                // This call verifies the path before and after reading the exact bounded bytes.
                // Snapshots belong only to this fresh pass; no event or path check is cached.
                readers.submit(file);
            }
            readers.finish();
        } finally {
            readers.close();
        }
        files.sort(
                java.util.Comparator.comparing(entry -> entry.getKey().getFileName().toString()));
        final var result = new ArrayList<Event>();
        String previous = headerSnapshot.sha256();
        for (final var entry : files) {
            final var path = entry.getKey();
            final var snapshot = entry.getValue();
            final var json = snapshot.node();
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
                            snapshot.sha256());
            validateTransition(result, event.caseId(), event.nonce(), event.kind(), event.detail());
            result.add(event);
            previous = event.sha256();
        }
        return List.copyOf(result);
    }

    /** One fresh read only; workers retain no snapshots or paths across calls. */
    private static final class SnapshotReaders implements AutoCloseable {
        private static final long READ_NANOS = TimeUnit.SECONDS.toNanos(30);
        private static final long CLOSE_NANOS = TimeUnit.SECONDS.toNanos(5);
        private final long deadline = System.nanoTime() + READ_NANOS;
        private final ArrayList<Map.Entry<Path, QualificationJson.ReadSnapshot>> completed;
        private final ArrayDeque<Map.Entry<Path, Future<QualificationJson.ReadSnapshot>>> pending =
                new ArrayDeque<>();
        private final List<Thread> workers = new CopyOnWriteArrayList<>();
        private final ThreadPoolExecutor executor;

        private SnapshotReaders(
                final ArrayList<Map.Entry<Path, QualificationJson.ReadSnapshot>> completed)
                throws InterruptedIOException {
            if (Thread.currentThread().isInterrupted()) {
                throw new InterruptedIOException("QUAL_JOURNAL_SNAPSHOT_INTERRUPTED");
            }
            this.completed = completed;
            final String prefix = "qualification-journal-snapshot-" + UUID.randomUUID() + "-";
            final var sequence = new AtomicInteger();
            executor =
                    new ThreadPoolExecutor(
                            2,
                            2,
                            0L,
                            TimeUnit.MILLISECONDS,
                            new ArrayBlockingQueue<>(2),
                            runnable -> {
                                final var worker =
                                        new Thread(runnable, prefix + sequence.incrementAndGet());
                                worker.setDaemon(false);
                                workers.add(worker);
                                return worker;
                            },
                            new ThreadPoolExecutor.AbortPolicy());
        }

        private int size() {
            return completed.size() + pending.size();
        }

        private void submit(final Path path) throws IOException {
            pending.addLast(
                    Map.entry(
                            path,
                            executor.submit(() -> QualificationJson.readSnapshot(path, 4096))));
            if (pending.size() == 2) {
                completeFirst();
            }
        }

        private void finish() throws IOException {
            while (!pending.isEmpty()) {
                completeFirst();
            }
        }

        private void completeFirst() throws IOException {
            final var entry = pending.getFirst();
            final long remaining = deadline - System.nanoTime();
            if (remaining <= 0) {
                throw new IOException("QUAL_JOURNAL_SNAPSHOT_DEADLINE");
            }
            try {
                final var snapshot = entry.getValue().get(remaining, TimeUnit.NANOSECONDS);
                completed.add(Map.entry(entry.getKey(), snapshot));
                pending.removeFirst();
            } catch (final InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new InterruptedIOException("QUAL_JOURNAL_SNAPSHOT_INTERRUPTED");
            } catch (final TimeoutException timeout) {
                throw new IOException("QUAL_JOURNAL_SNAPSHOT_DEADLINE");
            } catch (final ExecutionException failed) {
                final Throwable cause = failed.getCause();
                if (cause instanceof IOException io) {
                    throw io;
                }
                if (cause instanceof RuntimeException runtime) {
                    throw runtime;
                }
                if (cause instanceof Error error) {
                    throw error;
                }
                throw new IOException("QUAL_JOURNAL_SNAPSHOT_FAILED", cause);
            }
        }

        @Override
        public void close() throws InterruptedIOException {
            for (final var entry : pending) {
                entry.getValue().cancel(true);
            }
            executor.shutdownNow();
            boolean interrupted = Thread.interrupted();
            boolean terminated = executor.isTerminated();
            final long closeDeadline = System.nanoTime() + CLOSE_NANOS;
            try {
                while (!terminated) {
                    final long remaining = closeDeadline - System.nanoTime();
                    if (remaining <= 0) {
                        break;
                    }
                    try {
                        terminated = executor.awaitTermination(remaining, TimeUnit.NANOSECONDS);
                    } catch (final InterruptedException duringClose) {
                        interrupted = true;
                    }
                }
                for (final Thread worker : workers) {
                    while (worker.isAlive()) {
                        final long remaining = closeDeadline - System.nanoTime();
                        if (remaining <= 0) {
                            break;
                        }
                        try {
                            TimeUnit.NANOSECONDS.timedJoin(worker, remaining);
                        } catch (final InterruptedException duringJoin) {
                            interrupted = true;
                        }
                    }
                }
            } finally {
                if (interrupted) {
                    Thread.currentThread().interrupt();
                }
            }
            if (!terminated || workers.stream().anyMatch(Thread::isAlive)) {
                // Never report cleanup or continue normal journaling while owned workers live.
                // Non-daemon workers keep the JVM inside the existing Original physical guard.
                throw new AssertionError("QUAL_JOURNAL_SNAPSHOT_POOL_NOT_TERMINATED");
            }
            if (interrupted || Thread.currentThread().isInterrupted()) {
                throw new InterruptedIOException("QUAL_JOURNAL_SNAPSHOT_INTERRUPTED");
            }
        }
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
                                    && DIGEST_PATTERN.matcher(detail).matches();
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
