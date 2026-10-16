package br.com.esl.etl.v2.plataforma.persistencia.devlocal;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * DEV-FS-001: synthetic files only, immutable hash-linked records and an exclusive cooperative
 * lock. An interrupted STARTED run never becomes completed or loses artifacts through absence
 * inference.
 */
public final class DevLocalLedger implements AutoCloseable {
    public enum State {
        STARTED,
        COMPLETED,
        CANCELLED,
        FAILED,
        STOPPED
    }

    public static final class Failure extends IOException {
        private static final long serialVersionUID = 1L;
        private final String code;

        private Failure(final String code) {
            super("DEV_LOCAL_" + code);
            this.code = code;
        }

        public String code() {
            return code;
        }
    }

    public record Event(long sequence, String type, String sha256, long bytes) {}

    public record Output(String name, String sha256, long bytes) {}

    public record Run(
            String executionId,
            String inputSha256,
            State state,
            String reasonCode,
            List<Event> events,
            List<Output> outputs) {
        public Run {
            if (events == null
                    || outputs == null
                    || (long) events.size() + outputs.size() > MAX_RECORDS) {
                throw new IllegalArgumentException("DEV_LOCAL_RUN_METADATA_LIMIT");
            }
            events = List.copyOf(events);
            outputs = List.copyOf(outputs);
        }
    }

    private record Entry(
            long sequence,
            String previous,
            String operation,
            String run,
            String first,
            String second,
            String third,
            long size) {}

    private record StartIntent(
            String run, String input, int sequence, String previous, String fileSha256) {}

    private static final String MARKER = "DEV_LOCAL_SYNTHETIC_V1\n";
    private static final int EVENT_BYTES = 1_048_576;
    private static final int OUTPUT_BYTES = 4_194_304;
    private static final int MAX_RECORDS = 100_000;
    private static final int MAX_RUNS = 1000;
    private static final String START_INTENTS = "start-intents";
    private static final int START_INTENT_BYTES = 512;
    private final Path root;
    private final byte[] identity;
    private final String workspaceId;
    private final Object directoryKey;
    private final Object lockKey;
    private final FileChannel channel;
    private final FileLock lock;
    private boolean closed;
    private boolean uncertain;

    private DevLocalLedger(
            final Path root, final byte[] identity, final FileChannel channel, final FileLock lock)
            throws IOException {
        this.root = root;
        this.identity = identity.clone();
        this.workspaceId = utf8(identity).substring(MARKER.length()).strip();
        this.directoryKey = attributes(root).fileKey();
        this.lockKey = attributes(root.resolve("workspace.lock")).fileKey();
        this.channel = channel;
        this.lock = lock;
    }

    public static DevLocalLedger create(final Path workspace) throws IOException {
        final Path root = requested(workspace);
        checkDirectory(root.getParent());
        if (Files.exists(root, LinkOption.NOFOLLOW_LINKS)) {
            throw refused("WORKSPACE_EXISTS");
        }
        try {
            Files.createDirectory(root);
        } catch (final FileAlreadyExistsException failure) {
            throw refused("WORKSPACE_EXISTS");
        }
        writeNew(
                root.resolve("identity.txt"),
                (MARKER + UUID.randomUUID() + "\n").getBytes(StandardCharsets.UTF_8));
        Files.createDirectory(root.resolve("journal"));
        Files.createDirectory(root.resolve("runs"));
        writeNew(root.resolve("workspace.lock"), new byte[0]);
        return open(root);
    }

    public static DevLocalLedger open(final Path workspace) throws IOException {
        final Path root = requested(workspace);
        checkDirectory(root);
        checkDirectory(root.resolve("journal"));
        checkDirectory(root.resolve("runs"));
        final byte[] identity = readFile(root.resolve("identity.txt"), 256);
        final String text = utf8(identity);
        if (!text.startsWith(MARKER) || !text.endsWith("\n")) {
            throw refused("WORKSPACE_IDENTITY");
        }
        try {
            if (!UUID.fromString(text.substring(MARKER.length()).strip())
                    .toString()
                    .equals(text.substring(MARKER.length()).strip())) {
                throw refused("WORKSPACE_IDENTITY");
            }
        } catch (final IllegalArgumentException failure) {
            throw refused("WORKSPACE_IDENTITY");
        }
        checkFile(root.resolve("workspace.lock"));
        final FileChannel channel =
                FileChannel.open(
                        root.resolve("workspace.lock"),
                        StandardOpenOption.WRITE,
                        LinkOption.NOFOLLOW_LINKS);
        FileLock lock = null;
        try {
            lock = channel.tryLock();
            if (lock == null) {
                throw refused("WORKSPACE_LOCKED");
            }
            final var ledger = new DevLocalLedger(root, identity, channel, lock);
            ledger.load();
            return ledger;
        } catch (final OverlappingFileLockException failure) {
            channel.close();
            throw refused("WORKSPACE_LOCKED");
        } catch (final IOException | RuntimeException failure) {
            try {
                if (lock != null) {
                    lock.release();
                }
            } catch (final IOException cleanup) {
                failure.addSuppressed(refused("LOCK_CLOSE_UNCERTAIN"));
            } finally {
                try {
                    channel.close();
                } catch (final IOException cleanup) {
                    failure.addSuppressed(refused("CHANNEL_CLOSE_UNCERTAIN"));
                }
            }
            throw failure;
        }
    }

    public Path workspace() {
        return root;
    }

    public String workspaceId() {
        return workspaceId;
    }

    public synchronized Run start(final String executionId, final String inputSha256)
            throws IOException {
        name(executionId);
        hash(inputSha256);
        final var data = load();
        final var existing = data.runs.get(executionId);
        if (existing != null) {
            if (!existing.input.equals(inputSha256)) {
                throw refused("EXECUTION_INPUT_CONFLICT");
            }
            if (existing.state == State.STARTED) {
                throw refused("RECOVERY_REQUIRED");
            }
            return existing.snapshot();
        }
        if (data.runs.keySet().stream().anyMatch(id -> id.equalsIgnoreCase(executionId))) {
            throw refused("EXECUTION_CASE_CONFLICT");
        }
        if (data.pendingStart != null) {
            if (data.pendingStart.run().equals(executionId)
                    && !data.pendingStart.input().equals(inputSha256)) {
                throw refused("EXECUTION_INPUT_CONFLICT");
            }
            throw refused("START_RECONCILIATION_REQUIRED");
        }
        room(data);
        if (data.runs.size() >= MAX_RUNS) {
            throw refused("RUN_LIMIT");
        }
        final Path run = startDirectory(executionId);
        if (Files.exists(run, LinkOption.NOFOLLOW_LINKS)) {
            throw refused("ORPHAN_RUN_CONFLICT");
        }
        final StartIntent intent = writeStartIntent(data, executionId, inputSha256);
        data.pendingStart = intent;
        return completeStart(data, intent);
    }

    /**
     * Reconciles a verified own start only. STARTED requires an explicit caller decision; this
     * method never retries business work, adopts an unproven orphan or claims completion.
     */
    public synchronized Run reconcileStart(final String executionId, final String inputSha256)
            throws IOException {
        name(executionId);
        hash(inputSha256);
        final var data = load();
        final var intent = data.startIntents.get(executionId);
        if (intent == null) {
            throw refused("START_INTENT_REQUIRED");
        }
        if (!intent.input().equals(inputSha256)) {
            throw refused("EXECUTION_INPUT_CONFLICT");
        }
        final var existing = data.runs.get(executionId);
        if (existing != null) {
            return existing.snapshot();
        }
        room(data);
        if (data.runs.size() >= MAX_RUNS) {
            throw refused("RUN_LIMIT");
        }
        return completeStart(data, intent);
    }

    public synchronized List<String> pendingStartIds() throws IOException {
        final var intent = load().pendingStart;
        return intent == null ? List.of() : List.of(intent.run());
    }

    public synchronized void event(
            final String executionId, final String type, final String syntheticPayload)
            throws IOException {
        code(type);
        if (syntheticPayload == null) {
            throw refused("PAYLOAD_REQUIRED");
        }
        final byte[] bytes;
        try {
            final var buffer =
                    StandardCharsets.UTF_8
                            .newEncoder()
                            .onMalformedInput(CodingErrorAction.REPORT)
                            .onUnmappableCharacter(CodingErrorAction.REPORT)
                            .encode(java.nio.CharBuffer.wrap(syntheticPayload));
            bytes = new byte[buffer.remaining()];
            buffer.get(bytes);
        } catch (final CharacterCodingException failure) {
            throw refused("PAYLOAD_UTF8");
        }
        if (bytes.length > EVENT_BYTES) {
            throw refused("EVENT_BYTES_LIMIT");
        }
        final var data = load();
        noPendingStart(data);
        final var run = active(data, executionId);
        room(data);
        final long sequence = run.events.size() + 1L;
        persist(artifact(executionId, "events", eventName(sequence)), bytes);
        append(
                data,
                "EVENT",
                executionId,
                Long.toString(sequence),
                type,
                sha256(bytes),
                bytes.length);
    }

    public synchronized Output output(
            final String executionId, final String outputName, final byte[] syntheticBytes)
            throws IOException {
        name(outputName);
        if (syntheticBytes == null || syntheticBytes.length > OUTPUT_BYTES) {
            throw refused("OUTPUT_BYTES_LIMIT");
        }
        final var data = load();
        noPendingStart(data);
        final var run = active(data, executionId);
        room(data);
        if (run.outputs.containsKey(outputName.toLowerCase(Locale.ROOT))) {
            throw refused("OUTPUT_EXISTS");
        }
        final byte[] bytes = syntheticBytes.clone();
        persist(artifact(executionId, "outputs", outputName), bytes);
        final String digest = sha256(bytes);
        append(data, "OUTPUT", executionId, outputName, digest, "-", bytes.length);
        return new Output(outputName, digest, bytes.length);
    }

    public synchronized Run finish(
            final String executionId, final State terminalState, final String reasonCode)
            throws IOException {
        code(reasonCode);
        if (terminalState == null || terminalState == State.STARTED) {
            throw refused("TERMINAL_STATE_REQUIRED");
        }
        final var data = load();
        noPendingStart(data);
        final var run = required(data, executionId);
        if (run.state != State.STARTED) {
            if (run.state == terminalState && run.reason.equals(reasonCode)) {
                return run.snapshot();
            }
            throw refused("TERMINAL_CONFLICT");
        }
        room(data);
        if (terminalState == State.COMPLETED && !orphanFiles(executionId).isEmpty()) {
            throw refused("ORPHAN_ARTIFACTS_REQUIRE_RECOVERY");
        }
        final byte[] reason = (reasonCode + "\n").getBytes(StandardCharsets.UTF_8);
        persist(artifact(executionId, "reasons", "terminal.txt"), reason);
        append(
                data,
                "TERMINAL",
                executionId,
                terminalState.name(),
                reasonCode,
                sha256(reason),
                reason.length);
        return readback(executionId);
    }

    public synchronized Run recover(final String executionId, final String reasonCode)
            throws IOException {
        final var run = required(load(), executionId);
        if (run.state != State.STARTED) {
            throw refused("RECOVERY_STATE");
        }
        return finish(executionId, State.STOPPED, reasonCode);
    }

    public synchronized Run readback(final String executionId) throws IOException {
        return required(load(), executionId).snapshot();
    }

    public synchronized List<Run> runs() throws IOException {
        return load().runs.values().stream().map(MutableRun::snapshot).toList();
    }

    public synchronized byte[] readOutput(final String executionId, final String outputName)
            throws IOException {
        name(outputName);
        final var run = required(load(), executionId);
        final var output = run.outputs.get(outputName.toLowerCase(Locale.ROOT));
        if (output == null) {
            throw refused("OUTPUT_NOT_FOUND");
        }
        final byte[] bytes =
                readFile(artifact(executionId, "outputs", output.name()), OUTPUT_BYTES);
        verifyBytes(bytes, output.sha256(), output.bytes());
        return bytes;
    }

    public synchronized String readEvent(final String executionId, final long eventSequence)
            throws IOException {
        final var run = required(load(), executionId);
        if (eventSequence < 1 || eventSequence > run.events.size()) {
            throw refused("EVENT_NOT_FOUND");
        }
        final var event = run.events.get((int) eventSequence - 1);
        final byte[] bytes =
                readFile(artifact(executionId, "events", eventName(eventSequence)), EVENT_BYTES);
        verifyBytes(bytes, event.sha256(), event.bytes());
        return utf8(bytes);
    }

    public synchronized List<String> orphanFiles(final String executionId) throws IOException {
        final var run = required(load(), executionId);
        final Set<String> known = new java.util.HashSet<>();
        run.events.forEach(event -> known.add("events/" + eventName(event.sequence())));
        run.outputs.values().forEach(output -> known.add("outputs/" + output.name()));
        if (run.state != State.STARTED) {
            known.add("reasons/terminal.txt");
        }
        final List<String> orphans = new ArrayList<>();
        for (final String directory : List.of("events", "outputs", "reasons")) {
            final Path parent = artifact(executionId, directory, "placeholder").getParent();
            checkDirectory(parent);
            try (var paths = Files.list(parent)) {
                final var entries = paths.limit(MAX_RECORDS + 1L).toList();
                if (entries.size() > MAX_RECORDS) {
                    throw refused("ARTIFACT_LIMIT");
                }
                for (final Path path : entries) {
                    checkFile(path);
                    final String relative = directory + "/" + path.getFileName();
                    if (!known.contains(relative)) {
                        orphans.add(relative);
                    }
                }
            }
        }
        return List.copyOf(orphans);
    }

    private static final class MutableRun {
        private final String id;
        private final String input;
        private State state = State.STARTED;
        private String reason = "";
        private final List<Event> events = new ArrayList<>();
        private final Map<String, Output> outputs = new LinkedHashMap<>();

        private MutableRun(final String id, final String input) {
            this.id = id;
            this.input = input;
        }

        private Run snapshot() {
            return new Run(id, input, state, reason, events, new ArrayList<>(outputs.values()));
        }
    }

    private static final class Data {
        private final Map<String, MutableRun> runs = new LinkedHashMap<>();
        private final Map<String, Entry> starts = new LinkedHashMap<>();
        private final Map<String, StartIntent> startIntents = new LinkedHashMap<>();
        private StartIntent pendingStart;
        private int records;
        private String previous;

        private Data(final String previous) {
            this.previous = previous;
        }
    }

    private Data load() throws IOException {
        fence();
        final var data = new Data(sha256(identity));
        final List<Path> entries;
        try (var files = Files.list(root.resolve("journal"))) {
            entries = journalEntriesLimited(files, MAX_RECORDS);
        }
        for (final Path path : entries) {
            final int ordinal = data.records + 1;
            if (!path.getFileName().toString().equals(journalName(ordinal))) {
                throw refused("JOURNAL_SEQUENCE");
            }
            final byte[] bytes = readFile(path, 4096);
            final String[] lines = utf8(bytes).split("\n", -1);
            if (lines.length != 3
                    || !lines[2].isEmpty()
                    || !sha256((lines[0] + "\n").getBytes(StandardCharsets.UTF_8))
                            .equals(lines[1])) {
                throw refused("JOURNAL_CORRUPT");
            }
            final String[] fields = lines[0].split("\t", -1);
            if (fields.length != 8) {
                throw refused("JOURNAL_CORRUPT");
            }
            final Entry entry;
            try {
                entry =
                        new Entry(
                                Long.parseLong(fields[0]),
                                fields[1],
                                fields[2],
                                fields[3],
                                fields[4],
                                fields[5],
                                fields[6],
                                Long.parseLong(fields[7]));
            } catch (final NumberFormatException failure) {
                throw refused("JOURNAL_CORRUPT");
            }
            if (entry.sequence() != ordinal || !entry.previous().equals(data.previous)) {
                throw refused("JOURNAL_CHAIN");
            }
            apply(data, entry);
            data.previous = sha256(bytes);
            data.records++;
        }
        readStartIntents(data);
        return data;
    }

    static List<Path> journalEntriesLimited(final Stream<Path> files, final int maximum)
            throws IOException {
        if (maximum < 1 || maximum > MAX_RECORDS) {
            throw refused("JOURNAL_LIMIT");
        }
        // Limit the input to sorting too; sorted() alone buffers the complete upstream directory.
        final var entries = files.limit(maximum + 1L).sorted().toList();
        if (entries.size() > maximum) {
            throw refused("JOURNAL_LIMIT");
        }
        return entries;
    }

    private void apply(final Data data, final Entry entry) throws IOException {
        name(entry.run());
        if (entry.operation().equals("START")) {
            hash(entry.first());
            if (data.runs.keySet().stream().anyMatch(id -> id.equalsIgnoreCase(entry.run()))
                    || data.runs.size() >= MAX_RUNS
                    || !entry.second().equals("-")
                    || !entry.third().equals("-")
                    || entry.size() != 0) {
                throw refused("JOURNAL_TRANSITION");
            }
            for (final String directory : List.of("events", "outputs", "reasons")) {
                checkDirectory(artifact(entry.run(), directory, "placeholder").getParent());
            }
            data.runs.put(entry.run(), new MutableRun(entry.run(), entry.first()));
            data.starts.put(entry.run(), entry);
            return;
        }
        final var run = active(data, entry.run());
        switch (entry.operation()) {
            case "EVENT" -> {
                code(entry.second());
                final long sequence = run.events.size() + 1L;
                if (!entry.first().equals(Long.toString(sequence))) {
                    throw refused("JOURNAL_TRANSITION");
                }
                verify(
                        artifact(entry.run(), "events", eventName(sequence)),
                        entry.third(),
                        entry.size(),
                        EVENT_BYTES);
                run.events.add(new Event(sequence, entry.second(), entry.third(), entry.size()));
            }
            case "OUTPUT" -> {
                name(entry.first());
                if (!entry.third().equals("-")
                        || run.outputs.containsKey(entry.first().toLowerCase(Locale.ROOT))) {
                    throw refused("JOURNAL_TRANSITION");
                }
                verify(
                        artifact(entry.run(), "outputs", entry.first()),
                        entry.second(),
                        entry.size(),
                        OUTPUT_BYTES);
                run.outputs.put(
                        entry.first().toLowerCase(Locale.ROOT),
                        new Output(entry.first(), entry.second(), entry.size()));
            }
            case "TERMINAL" -> {
                final State state;
                try {
                    state = State.valueOf(entry.first());
                } catch (final IllegalArgumentException failure) {
                    throw refused("JOURNAL_TRANSITION");
                }
                code(entry.second());
                if (state == State.STARTED) {
                    throw refused("JOURNAL_TRANSITION");
                }
                final Path reason = artifact(entry.run(), "reasons", "terminal.txt");
                verify(reason, entry.third(), entry.size(), 128);
                if (!utf8(readFile(reason, 128)).equals(entry.second() + "\n")) {
                    throw refused("REASON_CONFLICT");
                }
                run.state = state;
                run.reason = entry.second();
            }
            default -> throw refused("JOURNAL_OPERATION");
        }
    }

    private void append(
            final Data data,
            final String operation,
            final String run,
            final String first,
            final String second,
            final String third,
            final long size)
            throws IOException {
        fence();
        if (data.pendingStart != null
                && (!operation.equals("START") || !run.equals(data.pendingStart.run()))) {
            throw refused("START_RECONCILIATION_REQUIRED");
        }
        if (data.records >= MAX_RECORDS) {
            throw refused("JOURNAL_LIMIT");
        }
        final String body =
                String.join(
                                "\t",
                                Integer.toString(data.records + 1),
                                data.previous,
                                operation,
                                run,
                                first,
                                second,
                                third,
                                Long.toString(size))
                        + "\n";
        final String digest = sha256(body.getBytes(StandardCharsets.UTF_8));
        persist(
                root.resolve("journal").resolve(journalName(data.records + 1)),
                (body + digest + "\n").getBytes(StandardCharsets.UTF_8));
    }

    private static void noPendingStart(final Data data) throws IOException {
        if (data.pendingStart != null) {
            throw refused("START_RECONCILIATION_REQUIRED");
        }
    }

    private Path startDirectory(final String executionId) throws IOException {
        checkDirectory(root.resolve("runs"));
        try (var files = Files.list(root.resolve("runs"))) {
            final var entries = files.limit(MAX_RUNS + 1L).toList();
            if (entries.size() > MAX_RUNS) {
                throw refused("RUN_DIRECTORY_LIMIT");
            }
            for (final Path entry : entries) {
                final String actual = entry.getFileName().toString();
                if (actual.equalsIgnoreCase(executionId) && !actual.equals(executionId)) {
                    throw refused("EXECUTION_CASE_CONFLICT");
                }
            }
        }
        return root.resolve("runs").resolve(executionId);
    }

    private StartIntent writeStartIntent(
            final Data data, final String executionId, final String inputSha256)
            throws IOException {
        final Path directory = root.resolve(START_INTENTS);
        if (!Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) {
            fence();
            Files.createDirectory(directory);
        }
        checkDirectory(directory);
        if (data.startIntents.size() >= MAX_RUNS) {
            throw refused("START_INTENT_LIMIT");
        }
        final String body =
                String.join(
                                "\t",
                                "START_INTENT_V1",
                                workspaceId,
                                sha256(identity),
                                executionId,
                                inputSha256,
                                Integer.toString(data.records + 1),
                                data.previous)
                        + "\n";
        final byte[] bytes =
                (body + sha256(body.getBytes(StandardCharsets.UTF_8)) + "\n")
                        .getBytes(StandardCharsets.UTF_8);
        if (bytes.length > START_INTENT_BYTES) {
            throw refused("START_INTENT_BYTES");
        }
        final Path path = directory.resolve(executionId + ".intent");
        persist(path, bytes);
        final var intent = readStartIntent(path);
        data.startIntents.put(executionId, intent);
        return intent;
    }

    private void readStartIntents(final Data data) throws IOException {
        final Path directory = root.resolve(START_INTENTS);
        // Legacy V1 workspaces have no intent directory and keep their journal format.
        if (!Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        checkDirectory(directory);
        final List<Path> entries;
        try (var files = Files.list(directory)) {
            entries = files.limit(MAX_RUNS + 1L).sorted().toList();
        }
        if (entries.size() > MAX_RUNS) {
            throw refused("START_INTENT_LIMIT");
        }
        final var folded = new java.util.HashSet<String>();
        for (final Path path : entries) {
            final var intent = readStartIntent(path);
            if (!folded.add(intent.run().toLowerCase(Locale.ROOT))) {
                throw refused("START_INTENT_DUPLICATE");
            }
            data.startIntents.put(intent.run(), intent);
            final var start = data.starts.get(intent.run());
            if (start != null) {
                if (!intent.input().equals(start.first())
                        || intent.sequence() != start.sequence()
                        || !intent.previous().equals(start.previous())) {
                    throw refused("START_INTENT_JOURNAL_CONFLICT");
                }
            } else {
                if (data.runs.keySet().stream().anyMatch(id -> id.equalsIgnoreCase(intent.run()))) {
                    throw refused("EXECUTION_CASE_CONFLICT");
                }
                if (intent.sequence() != data.records + 1
                        || !intent.previous().equals(data.previous)) {
                    throw refused("START_INTENT_JOURNAL_CONFLICT");
                }
                if (data.pendingStart != null) {
                    throw refused("START_INTENT_PENDING_CONFLICT");
                }
                data.pendingStart = intent;
            }
        }
    }

    private StartIntent readStartIntent(final Path path) throws IOException {
        final byte[] bytes = readFile(path, START_INTENT_BYTES);
        final String[] lines = utf8(bytes).split("\n", -1);
        if (lines.length != 3
                || !lines[2].isEmpty()
                || !sha256((lines[0] + "\n").getBytes(StandardCharsets.UTF_8)).equals(lines[1])) {
            throw refused("START_INTENT_CORRUPT");
        }
        final String[] fields = lines[0].split("\t", -1);
        if (fields.length != 7
                || !fields[0].equals("START_INTENT_V1")
                || !fields[1].equals(workspaceId)
                || !fields[2].equals(sha256(identity))) {
            throw refused("START_INTENT_WORKSPACE_CONFLICT");
        }
        name(fields[3]);
        hash(fields[4]);
        hash(fields[6]);
        if (!path.getFileName().toString().equals(fields[3] + ".intent")) {
            throw refused("START_INTENT_NAME_CONFLICT");
        }
        final int sequence;
        try {
            sequence = Integer.parseInt(fields[5]);
        } catch (final NumberFormatException failure) {
            throw refused("START_INTENT_SEQUENCE");
        }
        if (sequence < 1
                || sequence > MAX_RECORDS
                || !Integer.toString(sequence).equals(fields[5])) {
            throw refused("START_INTENT_SEQUENCE");
        }
        return new StartIntent(fields[3], fields[4], sequence, fields[6], sha256(bytes));
    }

    private void validateStartMaterial(final Path run) throws IOException {
        if (!Files.exists(run, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        checkDirectory(run);
        try (var files = Files.list(run)) {
            final var children = files.limit(4).toList();
            if (children.size() > 3) {
                throw refused("START_MATERIAL_CONFLICT");
            }
            for (final Path child : children) {
                if (!Set.of("events", "outputs", "reasons")
                        .contains(child.getFileName().toString())) {
                    throw refused("START_MATERIAL_CONFLICT");
                }
                checkDirectory(child);
                try (var contents = Files.list(child)) {
                    if (contents.findAny().isPresent()) {
                        throw refused("START_MATERIAL_CONFLICT");
                    }
                }
            }
        }
    }

    private void createStartDirectories(final Path run) throws IOException {
        fence();
        if (!Files.exists(run, LinkOption.NOFOLLOW_LINKS)) {
            Files.createDirectory(run);
        }
        for (final String child : List.of("events", "outputs", "reasons")) {
            final Path path = run.resolve(child);
            if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
                Files.createDirectory(path);
            }
        }
    }

    private Run completeStart(final Data data, final StartIntent intent) throws IOException {
        final Path run = startDirectory(intent.run());
        // All existing material is checked before filling any missing directory or journal slot.
        validateStartMaterial(run);
        final Path intentPath = root.resolve(START_INTENTS).resolve(intent.run() + ".intent");
        verify(
                intentPath,
                intent.fileSha256(),
                readFile(intentPath, START_INTENT_BYTES).length,
                START_INTENT_BYTES);
        createStartDirectories(run);
        validateStartMaterial(run);
        verify(
                intentPath,
                intent.fileSha256(),
                readFile(intentPath, START_INTENT_BYTES).length,
                START_INTENT_BYTES);
        append(data, "START", intent.run(), intent.input(), "-", "-", 0);
        return readback(intent.run());
    }

    private void persist(final Path path, final byte[] bytes) throws IOException {
        fence();
        checkDirectory(path.getParent());
        if (Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
            throw refused("ARTIFACT_EXISTS");
        }
        try {
            writeNew(path, bytes);
        } catch (final IOException failure) {
            uncertain = true;
            throw refused("WRITE_UNCERTAIN");
        }
    }

    private void fence() throws IOException {
        if (closed) {
            throw refused("CLOSED");
        }
        if (uncertain) {
            throw refused("WRITE_UNCERTAIN");
        }
        if (!lock.isValid() || !channel.isOpen()) {
            throw refused("WORKSPACE_FENCE_LOST");
        }
        checkFile(root.resolve("workspace.lock"));
        if (!Objects.equals(lockKey, attributes(root.resolve("workspace.lock")).fileKey())) {
            throw refused("WORKSPACE_FENCE_LOST");
        }
        checkDirectory(root);
        if (!Objects.equals(directoryKey, attributes(root).fileKey())
                || !Arrays.equals(identity, readFile(root.resolve("identity.txt"), 256))) {
            throw refused("WORKSPACE_IDENTITY_CHANGED");
        }
        checkDirectory(root.resolve("journal"));
        checkDirectory(root.resolve("runs"));
    }

    private Path artifact(final String executionId, final String directory, final String file)
            throws IOException {
        name(executionId);
        name(file);
        final Path path =
                root.resolve("runs").resolve(executionId).resolve(directory).resolve(file);
        if (!path.normalize().startsWith(root)) {
            throw refused("PATH_ESCAPE");
        }
        return path;
    }

    private static MutableRun required(final Data data, final String id) throws IOException {
        name(id);
        final var run = data.runs.get(id);
        if (run == null) {
            throw refused("RUN_NOT_FOUND");
        }
        return run;
    }

    private static MutableRun active(final Data data, final String id) throws IOException {
        final var run = required(data, id);
        if (run.state != State.STARTED) {
            throw refused("RUN_TERMINAL");
        }
        return run;
    }

    private static Path requested(final Path workspace) throws IOException {
        if (workspace == null) {
            throw refused("WORKSPACE_REQUIRED");
        }
        final Path root = workspace.toAbsolutePath().normalize();
        if (root.getParent() == null
                || root.getFileName() == null
                || root.toString().startsWith("\\\\")) {
            throw refused("WORKSPACE_LOCAL_REQUIRED");
        }
        name(root.getFileName().toString());
        return root;
    }

    private static void name(final String value) throws IOException {
        if (value == null
                || !value.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,79}")
                || value.endsWith(".")
                || value.toUpperCase(Locale.ROOT)
                        .matches("(?:CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9])(?:[.].*)?")) {
            throw refused("NAME_INVALID");
        }
    }

    private static void code(final String value) throws IOException {
        if (value == null || !value.matches("[A-Z][A-Z0-9_]{0,79}")) {
            throw refused("CODE_INVALID");
        }
    }

    private static void hash(final String value) throws IOException {
        if (value == null || !value.matches("[a-f0-9]{64}")) {
            throw refused("HASH_INVALID");
        }
    }

    private static BasicFileAttributes attributes(final Path path) throws IOException {
        final var attributes =
                Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (attributes.isSymbolicLink() || attributes.isOther()) {
            throw refused("LINK_REFUSED");
        }
        return attributes;
    }

    private static void checkDirectory(final Path path) throws IOException {
        if (path == null) {
            throw refused("DIRECTORY_REQUIRED");
        }
        for (Path current = path; current != null; current = current.getParent()) {
            if (!attributes(current).isDirectory()) {
                throw refused("DIRECTORY_REQUIRED");
            }
        }
        if (!path.toRealPath().equals(path.toAbsolutePath().normalize())) {
            throw refused("LINK_REFUSED");
        }
    }

    private static void checkFile(final Path path) throws IOException {
        checkDirectory(path.getParent());
        if (!attributes(path).isRegularFile()) {
            throw refused("REGULAR_FILE_REQUIRED");
        }
    }

    private static byte[] readFile(final Path path, final int limit) throws IOException {
        checkFile(path);
        try (var reader =
                FileChannel.open(path, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS)) {
            final long size = reader.size();
            if (size < 0 || size > limit) {
                throw refused("READ_BYTES_LIMIT");
            }
            final ByteBuffer bytes = ByteBuffer.allocate((int) size);
            while (bytes.hasRemaining()) {
                if (reader.read(bytes) < 0) {
                    throw refused("READ_CHANGED");
                }
            }
            if (reader.size() != size) {
                throw refused("READ_CHANGED");
            }
            return bytes.array();
        }
    }

    private static void verify(
            final Path path, final String expectedHash, final long expectedSize, final int limit)
            throws IOException {
        hash(expectedHash);
        final byte[] bytes = readFile(path, limit);
        verifyBytes(bytes, expectedHash, expectedSize);
    }

    private static void verifyBytes(
            final byte[] bytes, final String expectedHash, final long expectedSize)
            throws IOException {
        if (bytes.length != expectedSize || !sha256(bytes).equals(expectedHash)) {
            throw refused("ARTIFACT_HASH_CONFLICT");
        }
    }

    private static void room(final Data data) throws IOException {
        if (data.records >= MAX_RECORDS) {
            throw refused("JOURNAL_LIMIT");
        }
    }

    private static void writeNew(final Path path, final byte[] bytes) throws IOException {
        checkDirectory(path.getParent());
        try (var writer =
                FileChannel.open(
                        path,
                        StandardOpenOption.CREATE_NEW,
                        StandardOpenOption.WRITE,
                        LinkOption.NOFOLLOW_LINKS)) {
            final var buffer = ByteBuffer.wrap(bytes);
            while (buffer.hasRemaining()) {
                writer.write(buffer);
            }
            writer.force(true);
        }
    }

    private static String utf8(final byte[] bytes) throws IOException {
        try {
            return StandardCharsets.UTF_8
                    .newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
        } catch (final CharacterCodingException failure) {
            throw refused("UTF8_INVALID");
        }
    }

    public static String sha256(final byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (final NoSuchAlgorithmException failure) {
            throw new IllegalStateException("DEV_LOCAL_SHA256_UNAVAILABLE", failure);
        }
    }

    private static String journalName(final long sequence) {
        return String.format(Locale.ROOT, "%08d.entry", sequence);
    }

    private static String eventName(final long sequence) {
        return String.format(Locale.ROOT, "%08d.txt", sequence);
    }

    private static Failure refused(final String code) {
        return new Failure(code);
    }

    @Override
    public synchronized void close() throws IOException {
        if (closed) {
            return;
        }
        closed = true;
        IOException failure = null;
        try {
            lock.release();
        } catch (final IOException error) {
            failure = refused("LOCK_CLOSE_UNCERTAIN");
        }
        try {
            channel.close();
        } catch (final IOException error) {
            if (failure == null) {
                failure = refused("CHANNEL_CLOSE_UNCERTAIN");
            } else {
                failure.addSuppressed(refused("CHANNEL_CLOSE_UNCERTAIN"));
            }
        }
        if (failure != null) {
            throw failure;
        }
    }
}
