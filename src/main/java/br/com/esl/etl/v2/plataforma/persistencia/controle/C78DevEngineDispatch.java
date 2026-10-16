package br.com.esl.etl.v2.plataforma.persistencia.controle;

import br.com.esl.etl.v2.bootstrap.RuntimeDevEngineBankBinding.MissingGate;
import br.com.esl.etl.v2.bootstrap.RuntimeDevEngineBankBinding.Refusal;
import br.com.esl.etl.v2.bootstrap.RuntimeDevEngineBankBinding.Scope;
import br.com.esl.etl.v2.plataforma.configuracao.C78DevPeerProof;
import br.com.esl.etl.v2.plataforma.configuracao.C78LocalSqlTarget;
import br.com.esl.etl.v2.plataforma.configuracao.C78LocalTlsScope;

import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.sun.nio.file.ExtendedOpenOption;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.sql.SQLException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/** Root evidence is descriptive until its live, exclusive Bank controller permits this unit. */
final class C78DevEngineDispatch implements C78DevPeerProof, AutoCloseable {
    private static final int MAX_INPUTS = 6;
    private static final int MAX_ANCESTORS = 64;
    private static final int MAX_PATH_CHARACTERS = 512;
    private static final int MAX_PATH_DEPTH = 32;
    private static final ObjectMapper JSON =
            JsonMapper.builder()
                    .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                    .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                    .build();
    private final Scope scope;
    private final Path unit;
    private final Path dispatchPath;
    private final String dispatchSHA;
    private final JsonNode dispatch;
    private C78Sample12Selection sampleSelection;
    private final Map<Path, String> inputs = new LinkedHashMap<>();
    private final Map<Path, FileChannel> leases = new LinkedHashMap<>();
    private final Map<Path, Object> ancestors = new LinkedHashMap<>();
    private boolean started;
    private boolean peerProved;
    private long provedSqlPID;
    private boolean closed;
    private boolean controlReadStarted;
    private boolean sampleSecretRead;
    private int statements;
    private boolean sampleCleanupReserved;
    private int cleanupStatements;
    private final java.util.concurrent.ExecutorService reader =
            Executors.newSingleThreadExecutor(
                    task -> {
                        final var thread = new Thread(task, "c78-engine-private-control");
                        thread.setDaemon(true);
                        return thread;
                    });

    private C78DevEngineDispatch(
            final Scope scope, final Path path, final String sha, final JsonNode dispatch) {
        this.scope = scope;
        dispatchPath = path;
        dispatchSHA = sha;
        this.dispatch = dispatch;
        unit = path.getParent().getParent();
        inputs.put(path, sha);
    }

    static C78DevEngineDispatch read(final Path path, final String sha, final Scope scope)
            throws SQLException {
        return read(path, sha, scope, false);
    }

    static C78DevEngineDispatch readInspection(final Path path, final String sha, final Scope scope)
            throws SQLException {
        return read(path, sha, scope, true);
    }

    static C78DevEngineDispatch readSample12(
            final Path path, final String sha, final C78Sample12Selection selection)
            throws SQLException {
        final var result = read(path, sha, selection.scope(), "sample12");
        result.sampleSelection = selection;
        return result;
    }

    private static C78DevEngineDispatch read(
            final Path path, final String sha, final Scope scope, final boolean inspection)
            throws SQLException {
        return read(path, sha, scope, inspection ? "gate" : "engine");
    }

    private static C78DevEngineDispatch read(
            final Path path, final String sha, final Scope scope, final String prefix)
            throws SQLException {
        try {
            final Path actual = local(path);
            if (!actual.getParent().getFileName().toString().equals("root")
                    || !actual.getFileName().toString().matches(prefix + "-dispatch[0-9]+\\.json")
                    || !sha.matches("[a-f0-9]{64}")) {
                throw new Refusal(MissingGate.ADMISSION);
            }
            final FileChannel lease = lease(actual);
            C78DevEngineDispatch result = null;
            try {
                result = new C78DevEngineDispatch(scope, actual, sha, pinned(lease, sha));
                result.leases.put(actual, lease);
                result.checkAncestors(actual);
                return result;
            } catch (final IOException | RuntimeException failure) {
                lease.close();
                throw failure;
            }
        } catch (final IOException | RuntimeException failure) {
            final Refusal refusal = new Refusal(MissingGate.ADMISSION);
            refusal.initCause(failure);
            throw refusal;
        }
    }

    private static Path local(final Path path) throws IOException {
        boundedPath(path);
        final Path absolute = path.toAbsolutePath().normalize();
        boundedPath(absolute);
        final String raw = path.toString();
        if (raw.startsWith("\\\\")
                || raw.startsWith("//")
                || raw.contains("://")
                || raw.substring(Math.min(2, raw.length())).contains(":")
                || Files.isSymbolicLink(absolute)
                || !absolute.toRealPath(LinkOption.NOFOLLOW_LINKS).equals(absolute)
                || !Files.isRegularFile(absolute, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("DEV_ENGINE_LOCAL_FILE");
        }
        return absolute;
    }

    private static void boundedPath(final Path path) throws IOException {
        if (path.toString().length() > MAX_PATH_CHARACTERS
                || path.getNameCount() > MAX_PATH_DEPTH) {
            throw new IOException("DEV_ENGINE_PATH_LIMIT");
        }
    }

    private static void reserveEntry(final Map<?, ?> entries, final Path path, final int maximum)
            throws IOException {
        if (!entries.containsKey(path) && entries.size() >= maximum) {
            throw new IOException("DEV_ENGINE_RESOURCE_LIMIT");
        }
    }

    private static String hash(final byte[] bytes) throws IOException {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (final java.security.NoSuchAlgorithmException failure) {
            throw new IOException("DEV_ENGINE_DIGEST", failure);
        }
    }

    private static FileChannel lease(final Path path) throws IOException {
        return FileChannel.open(
                path,
                Set.of(
                        StandardOpenOption.READ,
                        LinkOption.NOFOLLOW_LINKS,
                        ExtendedOpenOption.NOSHARE_WRITE,
                        ExtendedOpenOption.NOSHARE_DELETE));
    }

    private synchronized void checkAncestors(final Path file) throws IOException {
        boundedPath(file);
        for (Path path = file; path != null; path = path.getParent()) {
            reserveEntry(ancestors, path, MAX_ANCESTORS);
            final var attributes =
                    Files.readAttributes(
                            path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            if (attributes.isSymbolicLink()
                    || attributes.isOther()
                    || !path.toRealPath().equals(path)) {
                throw new IOException("DEV_ENGINE_ANCESTOR_REFUSED");
            }
            // This Windows JDK returns null fileKey. NOSHARE_DELETE anchors the open file
            // and prevents ancestor rename; creation/type are additional drift alarms, not IDs.
            final Object identity =
                    attributes.fileKey() != null
                            ? attributes.fileKey()
                            : java.util.List.of(
                                    attributes.creationTime(), attributes.isDirectory());
            final Object previous = ancestors.putIfAbsent(path, identity);
            if (previous != null && !previous.equals(identity)) {
                throw new IOException("DEV_ENGINE_ANCESTOR_CHANGED");
            }
        }
    }

    private static JsonNode pinned(final FileChannel channel, final String sha) throws IOException {
        return JSON.readTree(pinnedBytes(channel, sha));
    }

    private static byte[] pinnedBytes(final FileChannel channel, final String sha)
            throws IOException {
        if (!sha.matches("[a-f0-9]{64}")) {
            throw new IOException("DEV_ENGINE_INPUT_LIMIT");
        }
        final var output = new java.io.ByteArrayOutputStream();
        final ByteBuffer buffer = ByteBuffer.allocate(8192);
        channel.position(0);
        int read;
        while ((read = channel.read(buffer)) != -1) {
            if (output.size() > 2_000_000 - read) {
                throw new IOException("DEV_ENGINE_INPUT_LIMIT");
            }
            output.write(buffer.array(), 0, read);
            buffer.clear();
        }
        final byte[] bytes = output.toByteArray();
        if (!hash(bytes).equals(sha)) {
            throw new IOException("DEV_ENGINE_INPUT_CHANGED");
        }
        return bytes;
    }

    synchronized JsonNode reference(final String name, final MissingGate gate) throws SQLException {
        try {
            return pinned(referenceLease(name), dispatch.path(name).path("sha256").textValue());
        } catch (final IOException | RuntimeException failure) {
            throw new Refusal(gate);
        }
    }

    synchronized Path codeReference(final String name, final String expected) throws SQLException {
        try {
            if (!expected.equals(dispatch.path(name).path("sha256").textValue())) {
                throw new IOException("ROOT_GATE_CODE_NOT_REVIEWED");
            }
            pinnedBytes(referenceLease(name), expected);
            return Path.of(dispatch.path(name).path("path").textValue())
                    .toAbsolutePath()
                    .normalize();
        } catch (final IOException | RuntimeException failure) {
            throw new Refusal(MissingGate.ADMISSION);
        }
    }

    synchronized JsonNode inspectionDescriptor() {
        return dispatch.deepCopy();
    }

    private FileChannel referenceLease(final String name) throws IOException {
        try {
            final JsonNode ref = dispatch.path(name);
            final Path path = local(Path.of(ref.path("path").textValue()));
            if (!path.startsWith(unit)) {
                throw new IOException("DEV_ENGINE_REFERENCE_NAMESPACE");
            }
            final String sha = ref.path("sha256").textValue();
            if (sha == null || !sha.matches("[a-f0-9]{64}")) {
                throw new IOException("DEV_ENGINE_INPUT_LIMIT");
            }
            reserveEntry(inputs, path, MAX_INPUTS);
            reserveEntry(leases, path, MAX_INPUTS);
            checkAncestors(path);
            FileChannel handle = leases.get(path);
            if (handle == null) {
                handle = lease(path);
                leases.put(path, handle);
            }
            checkAncestors(path);
            inputs.put(path, sha);
            return handle;
        } catch (final RuntimeException failure) {
            throw new IOException("DEV_ENGINE_REFERENCE_REFUSED", failure);
        }
    }

    synchronized void verify() throws SQLException {
        if (sampleSelection != null) {
            C78Sample12AdmissionProducer.verifyLocal(this, sampleSelection);
            return;
        }
        checkpointTime();
        final String id = dispatch.path("dispatchId").asText();
        if (!dispatch.path("schema").asText().equals("V2_DEV_C78_ENGINE_DISPATCH_V1")
                || !dispatch.path("operation").asText().equals("ENGINE_ROLLBACK_NO_PROMOTION")
                || !id.matches("C78ENG01-[a-f0-9]{32}")
                || !dispatch.path("nonce").asText().matches("[a-f0-9]{64}")
                || !dispatch.path("invocationId")
                        .asText()
                        .equals(scope.authorization().invocationId().toString())
                || !dispatch.path("bundleManifestSHA256")
                        .asText()
                        .equals(scope.bundleManifestSHA256())
                || !dispatch.path("configurationSHA256")
                        .asText()
                        .equals(scope.configurationSHA256())
                || !dispatch.path("requestSHA256").asText().equals(scope.requestSHA256())
                || !dispatch.path("planSHA256").asText().equals(scope.planSHA256())
                || !dispatch.path("target").asText().equals(C78LocalSqlTarget.URL)
                || !dispatch.path("leafSHA256").asText().equals(C78LocalTlsScope.LEAF_SHA256)
                || scope.template().templateId() != 6908) {
            throw new Refusal(MissingGate.ADMISSION);
        }
        final var parent =
                ProcessHandle.current()
                        .parent()
                        .orElseThrow(() -> new Refusal(MissingGate.ADMISSION));
        if (!dispatch.path("parentPID").isIntegralNumber()
                || !dispatch.path("parentPID").canConvertToLong()
                || dispatch.path("parentPID").longValue() < 1
                || parent.pid() != dispatch.path("parentPID").longValue()
                || !parent.isAlive()
                || !parent.info()
                        .startInstant()
                        .orElseThrow(() -> new Refusal(MissingGate.ADMISSION))
                        .equals(Instant.parse(dispatch.path("parentStartUtc").asText()))) {
            throw new Refusal(MissingGate.ADMISSION);
        }
        final JsonNode authority = reference("rootAuthority", MissingGate.ADMISSION);
        final JsonNode issuer = reference("issuerClaim", MissingGate.ADMISSION);
        if (!authority.path("owner").asText().equals("Root")
                || !authority.path("operation").asText().equals("ENGINE_ROLLBACK_NO_PROMOTION")
                || !authority.path("dispatchId").asText().equals(id)
                || !authority
                        .path("authorizationReference")
                        .asText()
                        .matches("[A-Za-z0-9_:-]{12,160}")
                || !issuer.path("dispatchId").asText().equals(id)
                || !issuer.path("authoritySHA256")
                        .asText()
                        .equals(dispatch.path("rootAuthority").path("sha256").asText())
                || !issuer.path("state").asText().equals("CONSUMED_BEFORE_DISPATCH_NO_RETRY")) {
            throw new Refusal(MissingGate.ADMISSION);
        }
        final JsonNode reservation = reference("reservation", MissingGate.BUDGET);
        final JsonNode limits = dispatch.path("limits");
        for (final String name :
                Set.of(
                        "connections",
                        "statements",
                        "querySeconds",
                        "unitSeconds",
                        "HTTP",
                        "rows",
                        "commit",
                        "promotion",
                        "retry")) {
            if (!limits.path(name).isIntegralNumber()
                    || !limits.path(name).canConvertToInt()
                    || limits.path(name).intValue() < 0) {
                throw new Refusal(MissingGate.BUDGET);
            }
        }
        if (!reservation.path("dispatchId").asText().equals(id)
                || !reservation.path("limits").equals(limits)
                || limits.path("connections").asInt() != 1
                || limits.path("statements").asInt() < C78DevEngineSession.TABLES.size() + 4
                || limits.path("statements").asInt() > 128
                || limits.path("querySeconds").asInt() != 10
                || limits.path("unitSeconds").asInt() != 60
                || limits.path("HTTP").asInt() < 1
                || limits.path("HTTP").asInt() > 2
                || limits.path("rows").asInt() < 1
                || limits.path("rows").asInt() > 1000
                || limits.path("commit").asInt(-1) != 0
                || limits.path("promotion").asInt(-1) != 0
                || limits.path("retry").asInt(-1) != 0
                || scope.pageSize() != 1
                || scope.extractionLimits().maxPages() != 1
                || scope.extractionLimits().maxRecords() > limits.path("rows").asLong()) {
            throw new Refusal(MissingGate.BUDGET);
        }
        final JsonNode contracts = reference("physicalContract", MissingGate.PHYSICAL_CONTRACT);
        if (contracts
                .path("schema")
                .asText()
                .equals("BANCO_C78_MAIN_COLETAS_CAPTURE_SQL_SURFACE_V1")) {
            // The actual Main uses ordinary RUN, including recovery/promotion branches.
            // A capture-only receipt cannot remove its baseline audit/contract gates.
            throw new Refusal(MissingGate.PHYSICAL_CONTRACT);
        } else {
            if (!contracts.path("schema").asText().equals("BANCO_CATALOG39_OFFLINE_COMPARISON_V1")
                    || contracts.path("columns").isEmpty()
                    || contracts.path("javaCalls").isEmpty()) {
                throw new Refusal(MissingGate.PHYSICAL_CONTRACT);
            }
            for (final JsonNode column : contracts.path("columns")) {
                if (!column.path("status").asText().equals("CONFIRMED")) {
                    throw new Refusal(MissingGate.PHYSICAL_CONTRACT);
                }
            }
            for (final JsonNode call : contracts.path("javaCalls")) {
                if (!call.path("reportCovered").asBoolean()
                        || !call.path("arityMatches").asBoolean()) {
                    throw new Refusal(MissingGate.PHYSICAL_CONTRACT);
                }
            }
        }
        final JsonNode plan = dispatch.path("writerProtectionPlan");
        if (!plan.path("mechanism").asText().equals("OWNED_TRANSACTION_TABLE_X_READBACK")
                || !plan.path("tables").isArray()
                || plan.path("tables").size() < 26
                || plan.path("tables").size() > 40) {
            throw new Refusal(MissingGate.WRITER_PROTECTION);
        }
        final Set<String> tables = new java.util.HashSet<>();
        for (final JsonNode table : plan.path("tables")) {
            if (!table.asText().matches("(?:ctl|stg|core|recon)\\.[a-z_]{1,80}")
                    || !tables.add(table.asText())) {
                throw new Refusal(MissingGate.WRITER_PROTECTION);
            }
        }
        if (!tables.equals(C78DevEngineSession.TABLES)) {
            throw new Refusal(MissingGate.WRITER_PROTECTION);
        }
        // Source permission is verified independently by Admission.verify before this local
        // control check and by every session checkpoint. Supplier bucket/TTL/saldo JSON was
        // project-authored, not an authenticated source receipt (Root F4 EVENTO06).
        // This class validates local unit limits; it cannot authenticate or release STOP429.
        // These receipts do not create permission: start() still requires the live parent permit.
        checkpoint();
    }

    private void checkpointTime() throws SQLException {
        try {
            final Instant issued = Instant.parse(dispatch.path("issuedUtc").asText());
            final Instant deadline = Instant.parse(dispatch.path("deadlineUtc").asText());
            if (closed
                    || issued.isAfter(Instant.now())
                    || !deadline.isAfter(Instant.now())
                    || java.time.Duration.between(issued, deadline).toMillis() > 60_000) {
                throw new Refusal(MissingGate.BUDGET);
            }
        } catch (final RuntimeException failure) {
            throw new Refusal(MissingGate.ADMISSION);
        }
    }

    synchronized void checkpoint() throws SQLException {
        checkpointTime();
        for (final var pin : inputs.entrySet()) {
            try {
                checkAncestors(pin.getKey());
                pinnedBytes(leases.get(pin.getKey()), pin.getValue());
            } catch (final IOException | RuntimeException failure) {
                throw new Refusal(MissingGate.ADMISSION);
            }
        }
    }

    synchronized void consumeStatement() throws SQLException {
        checkpoint();
        if (statements
                >= dispatch.path("limits").path("statements").asInt()
                        - (sampleCleanupReserved ? 3 : 0)) {
            throw new Refusal(MissingGate.BUDGET);
        }
        statements++;
    }

    synchronized void reserveSampleCleanup() throws SQLException {
        if (sampleCleanupReserved || statements != 0) {
            throw new Refusal(MissingGate.BUDGET);
        }
        sampleCleanupReserved = true;
    }

    synchronized void requireSampleCallProofBudget() throws SQLException {
        checkpoint();
        if (!sampleCleanupReserved
                || statements > dispatch.path("limits").path("statements").intValue() - 5) {
            throw new Refusal(MissingGate.BUDGET);
        }
    }

    synchronized void consumeSampleCleanup() throws SQLException {
        checkpoint();
        if (!sampleCleanupReserved
                || cleanupStatements >= 3
                || statements >= dispatch.path("limits").path("statements").asInt()) {
            throw new Refusal(MissingGate.BUDGET);
        }
        cleanupStatements++;
        statements++;
    }

    synchronized int statementsUsed() {
        return statements;
    }

    JsonNode protectionTables() {
        return dispatch.path("writerProtectionPlan").path("tables");
    }

    int maximumRows() {
        return dispatch.path("limits").path("rows").asInt();
    }

    boolean allowedCall(final String sql) {
        final var matcher =
                java.util.regex.Pattern.compile(
                                "\\{call"
                                    + " ([a-z_]+\\.[a-z_]+)\\(\\s*(\\?(?:\\s*,\\s*\\?)*)?\\s*\\)\\}")
                        .matcher(sql);
        if (!matcher.matches() || matcher.group(1).startsWith("core.")) {
            return false;
        }
        final int expected =
                switch (matcher.group(1)) {
                    case "ctl.usp_control_plane_register_source" -> 3;
                    case "ctl.usp_control_plane_start_cycle" -> 4;
                    case "ctl.usp_control_plane_start_execution" -> 18;
                    case "ctl.usp_control_plane_heartbeat_lease" -> 4;
                    case "ctl.usp_control_plane_record_page" -> 10;
                    case "ctl.usp_control_plane_record_counts" -> 9;
                    case "ctl.usp_control_plane_transition_execution" -> 5;
                    case "ctl.usp_runtime_recovery" -> 8;
                    case "ctl.usp_runtime_status" -> 8;
                    case "stg.usp_stage_coleta_record" -> 23;
                    case "stg.usp_stage_coleta_exact_time" -> 9;
                    case "recon.usp_runtime_raise_alert" -> 7;
                    default -> -1;
                };
        final int actual =
                matcher.group(2) == null
                        ? 0
                        : (int) matcher.group(2).chars().filter(value -> value == '?').count();
        return expected == actual;
    }

    synchronized void start() throws SQLException {
        if (started) {
            throw new Refusal(MissingGate.ADMISSION);
        }
        verify();
        if (sampleSelection != null
                && dispatch.path("limits").path("connections").intValue() != 1) {
            throw new Refusal(MissingGate.ADMISSION);
        }
        exchange("ENGINE_START_REQUEST", "ENGINE_START_PERMIT", null);
        try {
            final Path source = Path.of(dispatch.path("occurrenceDirectory").asText());
            boundedPath(source);
            final Path occurrence = source.toAbsolutePath().normalize();
            boundedPath(occurrence.resolve("runtime-engine-claim.json"));
            if (!occurrence.equals(
                            unit.resolve("banco01/occurrences")
                                    .resolve(dispatch.path("dispatchId").asText()))
                    || !occurrence.toRealPath().equals(occurrence)) {
                throw new IOException("DEV_ENGINE_OCCURRENCE");
            }
            Files.writeString(
                    occurrence.resolve("runtime-engine-claim.json"),
                    "{\"state\":\"CONSUMED_NO_RETRY\"}\n",
                    StandardOpenOption.CREATE_NEW);
            started = true;
        } catch (final IOException | RuntimeException failure) {
            throw new Refusal(MissingGate.ADMISSION);
        }
    }

    synchronized String sampleSecret() {
        if (!started
                || closed
                || sampleSelection == null
                || sampleSecretRead
                || dispatch.path("limits").path("provider").intValue() != 1) {
            throw new IllegalStateException("C78_SAMPLE12_LAZY_SECRET_NOT_ADMITTED");
        }
        sampleSecretRead = true;
        try {
            exchange("ENGINE_SECRET_REQUEST", "ENGINE_SECRET_PERMIT", null);
        } catch (SQLException refused) {
            throw new IllegalStateException("C78_SAMPLE12_LIVE_SECRET_ADMISSION_REFUSED", refused);
        }
        final var future = reader.submit(() -> C78Sample12SecretFrame.read(System.in));
        controlReadStarted = true;
        try {
            checkpoint();
            long remaining =
                    java.time.Duration.between(
                                    Instant.now(),
                                    Instant.parse(dispatch.path("deadlineUtc").textValue()))
                            .toMillis();
            return future.get(Math.max(1, Math.min(remaining, 10_000)), TimeUnit.MILLISECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("C78_SAMPLE12_LAZY_SECRET_INTERRUPTED", interrupted);
        } catch (Exception refused) {
            throw new IllegalStateException(
                    "C78_SAMPLE12_EXISTING_PRIVATE_SECRET_REFUSED", refused);
        } finally {
            future.cancel(true);
        }
    }

    private void exchange(final String request, final String permit, final Tuple tuple)
            throws SQLException {
        checkpoint();
        try {
            final Instant published = Instant.now();
            final var event = JSON.createObjectNode();
            event.put("kind", request)
                    .put("dispatchId", dispatch.path("dispatchId").asText())
                    .put("dispatchSHA256", dispatchSHA)
                    .put("nonce", dispatch.path("nonce").asText())
                    .put("childPID", ProcessHandle.current().pid())
                    .put("parentPID", dispatch.path("parentPID").longValue())
                    .put("invocationId", dispatch.path("invocationId").asText());
            if (tuple != null) {
                event.set("tuple", JSON.valueToTree(tuple));
            }
            System.out.println(JSON.writeValueAsString(event));
            System.out.flush();
            final var future =
                    reader.submit(
                            () -> {
                                final var bytes = new java.io.ByteArrayOutputStream();
                                for (int index = 0; index < 32768; index++) {
                                    final int value = System.in.read();
                                    if (value < 0) {
                                        throw new IOException("DEV_ENGINE_PRIVATE_CONTROL_EOF");
                                    }
                                    if (value == '\n') {
                                        return JSON.readTree(bytes.toByteArray());
                                    }
                                    bytes.write(value);
                                }
                                throw new IOException("DEV_ENGINE_PRIVATE_CONTROL_LIMIT");
                            });
            controlReadStarted = true;
            final long remaining =
                    java.time.Duration.between(
                                    Instant.now(),
                                    Instant.parse(dispatch.path("deadlineUtc").asText()))
                            .toMillis();
            final JsonNode response;
            try {
                response =
                        future.get(Math.max(1, Math.min(remaining, 10_000)), TimeUnit.MILLISECONDS);
            } finally {
                future.cancel(true);
            }
            if (!response.path("kind").asText().equals(permit)
                    || !response.path("dispatchId").equals(event.path("dispatchId"))
                    || !response.path("dispatchSHA256").equals(event.path("dispatchSHA256"))
                    || !response.path("nonce").equals(event.path("nonce"))
                    || !response.path("childPID").isIntegralNumber()
                    || !response.path("childPID").canConvertToLong()
                    || response.path("childPID").longValue() != ProcessHandle.current().pid()
                    || !response.path("parentPID").isIntegralNumber()
                    || !response.path("parentPID").canConvertToLong()
                    || response.path("parentPID").longValue()
                            != dispatch.path("parentPID").longValue()
                    || !response.path("invocationId").equals(dispatch.path("invocationId"))
                    || !response.path("rootAuthoritySHA256")
                            .equals(dispatch.path("rootAuthority").path("sha256"))
                    || tuple != null && !response.path("tuple").equals(event.path("tuple"))) {
                throw new IOException("DEV_ENGINE_PRIVATE_PERMIT_BINDING");
            }
            if (tuple != null) {
                final JsonNode proof = response.path("nativeProof");
                final JsonNode expected = dispatch.path("nativeImage");
                if (!proof.path("pid").isIntegralNumber()
                        || !proof.path("pid").canConvertToLong()
                        || proof.path("pid").longValue() < 1
                        || !expected.path("pid").isIntegralNumber()
                        || !expected.path("pid").canConvertToLong()
                        || proof.path("pid").longValue() != expected.path("pid").longValue()
                        || !proof.path("service").asText().equals("MSSQLSERVER")
                        || !proof.path("version").asText().equals("16.0.1000.6")
                        || !proof.path("sha256").asText().matches("[a-f0-9]{64}")
                        || !proof.path("sha256").equals(expected.path("sha256"))
                        || !proof.path("rawCreationPRE").asText().matches("[0-9]{18}")
                        || !proof.path("rawCreationPRE").equals(expected.path("rawCreation"))
                        || !proof.path("rawCreationPOST").equals(expected.path("rawCreation"))
                        || !proof.path("loadedImage").isBoolean()
                        || !proof.path("loadedImage").booleanValue()
                        || !proof.path("microsoftCacheOnly").isBoolean()
                        || !proof.path("microsoftCacheOnly").booleanValue()
                        || !proof.path("reverseTupleMatches").isIntegralNumber()
                        || !proof.path("reverseTupleMatches").canConvertToInt()
                        || proof.path("reverseTupleMatches").intValue() != 1
                        || !proof.path("evidenceSHA256").asText().matches("[a-f0-9]{64}")) {
                    throw new IOException("DEV_ENGINE_CURRENT_NATIVE_BINDING");
                }
                final Instant observed = Instant.parse(proof.path("observedUtc").asText());
                if (observed.isBefore(published) || observed.isAfter(Instant.now())) {
                    throw new IOException("DEV_ENGINE_CURRENT_NATIVE_TIME");
                }
                provedSqlPID = proof.path("pid").longValue();
            }
            checkpoint();
        } catch (final InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new SQLException("DEV_ENGINE_CONTROL_INTERRUPTED", failure);
        } catch (final Exception failure) {
            throw new SQLException("DEV_ENGINE_LIVE_CONTROLLER_REFUSED", failure);
        }
    }

    synchronized long provedSqlPID() {
        return provedSqlPID;
    }

    @Override
    public synchronized void verify(final Tuple tuple) throws IOException {
        if (!started || peerProved || closed) {
            throw new IOException("DEV_ENGINE_PEER_PHASE");
        }
        try {
            exchange("ENGINE_PRELOGIN_PEER_REQUEST", "ENGINE_PRELOGIN_PEER_PERMIT", tuple);
            peerProved = true;
        } catch (final SQLException failure) {
            throw new IOException("DEV_ENGINE_NATIVE_PEER_REFUSED", failure);
        }
    }

    @Override
    public synchronized void close() throws SQLException {
        if (!closed) {
            closed = true;
            reader.shutdownNow();
            SQLException failure = null;
            try {
                if (controlReadStarted) {
                    System.in.close();
                }
                if (!reader.awaitTermination(1, TimeUnit.SECONDS)) {
                    throw new SQLException("DEV_ENGINE_CONTROL_CLOSE_UNCERTAIN");
                }
            } catch (final IOException uncertain) {
                failure = new SQLException("DEV_ENGINE_CONTROL_CLOSE_UNCERTAIN", uncertain);
            } catch (final InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                failure = new SQLException("DEV_ENGINE_CONTROL_CLOSE_INTERRUPTED", interrupted);
            } catch (final SQLException uncertain) {
                failure = uncertain;
            }
            for (final FileChannel lease : leases.values()) {
                try {
                    lease.close();
                } catch (final IOException cleanup) {
                    if (failure == null) {
                        failure = new SQLException("DEV_ENGINE_INPUT_LEASE_CLOSE", cleanup);
                    } else {
                        failure.addSuppressed(cleanup);
                    }
                }
            }
            if (failure != null) {
                throw failure;
            }
        }
    }
}
