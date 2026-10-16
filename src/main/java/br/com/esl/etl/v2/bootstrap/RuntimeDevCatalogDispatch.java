package br.com.esl.etl.v2.bootstrap;

import br.com.esl.etl.v2.plataforma.configuracao.C78LocalSqlTarget;
import br.com.esl.etl.v2.plataforma.configuracao.C78LocalTlsScope;

import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.sun.nio.file.ExtendedOpenOption;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Separate, current Root dispatch. Bundle hashes grant no effect authority. */
final class RuntimeDevCatalogDispatch implements AutoCloseable {
    static final String SCHEMA = "V2_DEV_C78_CATALOG_DISPATCH_V1";
    static final String SQL_IMAGE_SHA256 =
            "73dcd4a89ff178cf6b2978ee29dccd8898fe5c1977abac8208aa4ecb5ec43e32";
    static final Set<String> CORE =
            Set.of(
                    "bin/java.exe",
                    "lib/modules",
                    "conf/security/java.security",
                    "bin/server/jvm.dll",
                    "bin/java.dll",
                    "bin/net.dll",
                    "bin/nio.dll",
                    "bin/verify.dll",
                    "bin/zip.dll",
                    "bin/jimage.dll",
                    "bin/jli.dll");
    private final List<FileChannel> leases = new ArrayList<>();
    final JsonNode descriptor;
    final String sha256;
    final Path occurrence;
    private boolean closed;

    private RuntimeDevCatalogDispatch(
            final JsonNode descriptor, final String sha256, final Path occurrence) {
        this.descriptor = descriptor;
        this.sha256 = sha256;
        this.occurrence = occurrence;
    }

    static RuntimeDevCatalogDispatch open(
            final Path path,
            final String expectedSHA,
            final RuntimeDevWorkspaceBundle bundle,
            final RuntimeDevPreparedInvocation prepared)
            throws Exception {
        if (!expectedSHA.matches("[a-f0-9]{64}")) {
            throw refused("DIGEST");
        }
        final Path unit = bundle.configuration().getParent().getParent().getParent();
        final String expectedRequest = requestForUnit(unit);
        final Path actual = localFile(path);
        if (!actual.getParent().equals(unit.resolve("root"))
                || !actual.getFileName().toString().matches("catalog-dispatch[0-9]+\\.json")) {
            throw refused("ROOT_NAMESPACE");
        }
        final FileChannel descriptorLease = lease(actual);
        RuntimeDevCatalogDispatch dispatch = null;
        try {
            final byte[] bytes = bytes(descriptorLease, 65_536);
            if (!expectedSHA.equals(hash(bytes))) {
                throw refused("BYTES_CHANGED");
            }
            final JsonNode node = parse(bytes, expectedRequest);
            final Path directory =
                    localDirectory(Path.of(node.path("occurrenceDirectory").textValue()));
            final Path relative = unit.relativize(directory);
            if (!directory.startsWith(unit)
                    || !relative.toString()
                            .replace('\\', '/')
                            .matches(
                                    "banco[0-9]{2}/occurrences/"
                                            + node.path("dispatchId").textValue())) {
                throw refused("BANK_OCCURRENCE_NAMESPACE");
            }
            dispatch = new RuntimeDevCatalogDispatch(node, expectedSHA, directory);
            dispatch.leases.add(descriptorLease);
            dispatch.validateCurrent(bundle, prepared);
            final Path javaHome = Path.of(System.getProperty("java.home")).toRealPath();
            for (final JsonNode pin : node.path("runtimePins")) {
                final var core = lease(localFile(javaHome.resolve(pin.path("path").textValue())));
                dispatch.leases.add(core);
                if (!pin.path("sha256").textValue().equals(hash(core))) {
                    throw refused("RUNTIME_CORE_CHANGED");
                }
            }
            return dispatch;
        } catch (final Exception | Error failure) {
            if (dispatch == null) {
                descriptorLease.close();
            } else {
                try {
                    dispatch.close();
                } catch (final IOException cleanup) {
                    failure.addSuppressed(cleanup);
                }
            }
            throw failure;
        }
    }

    static String requestForUnit(final Path unit) throws IOException {
        return switch (unit.getFileName().toString()) {
            case "hermes8fdb85e78657" -> "HERMES_8fdb85e78657";
            case "hermesf4eda62e83d1" -> "HERMES_f4eda62e83d1";
            default -> throw refused("UNIT");
        };
    }

    static JsonNode parse(final byte[] bytes) throws IOException {
        return parse(bytes, "HERMES_8fdb85e78657");
    }

    static JsonNode parse(final byte[] bytes, final String expectedRequest) throws IOException {
        if (!Set.of("HERMES_8fdb85e78657", "HERMES_f4eda62e83d1").contains(expectedRequest)) {
            throw refused("UNIT");
        }
        if (bytes.length > 65_536) {
            throw refused("LIMIT");
        }
        final JsonNode node;
        try {
            node =
                    JsonMapper.builder()
                            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                            .build()
                            .readTree(bytes);
        } catch (final IOException malformed) {
            throw refused("JSON");
        }
        keys(
                node,
                Set.of(
                        "schema",
                        "request",
                        "operation",
                        "dispatchId",
                        "invocationId",
                        "nonce",
                        "bundleManifestSHA256",
                        "configurationSHA256",
                        "requestSHA256",
                        "planSHA256",
                        "profile",
                        "target",
                        "leafSHA256",
                        "sourceHeld",
                        "captureAdmitted",
                        "issuedUtc",
                        "deadlineUtc",
                        "parentPID",
                        "parentStartUtc",
                        "occurrenceDirectory",
                        "limits",
                        "runtimePins",
                        "nativeImage"));
        exact(node, "schema", SCHEMA);
        exact(node, "request", expectedRequest);
        exact(node, "operation", "CATALOG12_SELECT_ONLY");
        exact(node, "profile", C78LocalSqlTarget.PROFILE);
        exact(node, "target", C78LocalSqlTarget.URL);
        exact(node, "leafSHA256", C78LocalTlsScope.LEAF_SHA256);
        match(node, "dispatchId", "C78CAT01-[a-f0-9]{32}");
        match(node, "nonce", "[a-f0-9]{64}");
        for (final var key :
                List.of(
                        "bundleManifestSHA256",
                        "configurationSHA256",
                        "requestSHA256",
                        "planSHA256")) {
            match(node, key, "[a-f0-9]{64}");
        }
        try {
            final String id = node.path("invocationId").textValue();
            if (!UUID.fromString(id).toString().equals(id)) {
                throw refused("INVOCATION");
            }
            final Instant issued = Instant.parse(node.path("issuedUtc").textValue());
            final Instant deadline = Instant.parse(node.path("deadlineUtc").textValue());
            final long duration = Duration.between(issued, deadline).toMillis();
            if (duration <= 0 || duration > 60_000) {
                throw refused("DEADLINE");
            }
            Instant.parse(node.path("parentStartUtc").textValue());
        } catch (final RuntimeException malformed) {
            throw refused("TIME_OR_INVOCATION");
        }
        flag(node, "sourceHeld", true);
        flag(node, "captureAdmitted", false);
        positiveLong(node, "parentPID");
        match(node, "occurrenceDirectory", ".{1,2048}");
        final var limits = node.path("limits");
        keys(
                limits,
                Set.of(
                        "connections",
                        "selects",
                        "querySeconds",
                        "unitSeconds",
                        "retry",
                        "rowsPerQuery",
                        "queryChars",
                        "reportChars",
                        "frameBytes"));
        final boolean coverage = expectedRequest.equals("HERMES_f4eda62e83d1");
        final int[] expected = {
            1, 12, 10, 60, 0, 512, coverage ? 1_500_000 : 65536, coverage ? 1_500_000 : 262144, 8192
        };
        final String[] names = {
            "connections",
            "selects",
            "querySeconds",
            "unitSeconds",
            "retry",
            "rowsPerQuery",
            "queryChars",
            "reportChars",
            "frameBytes"
        };
        for (int i = 0; i < names.length; i++) {
            if (!limits.path(names[i]).isIntegralNumber()
                    || limits.path(names[i]).longValue() != expected[i]) {
                throw refused("LIMITS");
            }
        }
        final var pins = node.path("runtimePins");
        final var namesSeen = new HashSet<String>();
        if (!pins.isArray() || pins.size() != CORE.size()) {
            throw refused("CORE_PINS");
        }
        for (final var pin : pins) {
            keys(pin, Set.of("path", "sha256"));
            match(pin, "sha256", "[a-f0-9]{64}");
            if (!pin.path("path").isTextual()
                    || !CORE.contains(pin.path("path").textValue())
                    || !namesSeen.add(pin.path("path").textValue())) {
                throw refused("CORE_PINS");
            }
        }
        final var image = node.path("nativeImage");
        keys(image, Set.of("service", "pid", "rawCreation", "version", "sha256"));
        exact(image, "service", "MSSQLSERVER");
        exact(image, "version", "16.0.1000.6");
        exact(image, "sha256", SQL_IMAGE_SHA256);
        positiveLong(image, "pid");
        match(image, "rawCreation", "[1-9][0-9]{0,18}");
        return node;
    }

    void validateCurrent(
            final RuntimeDevWorkspaceBundle bundle, final RuntimeDevPreparedInvocation prepared)
            throws IOException {
        checkDeadline();
        if (!descriptor.path("bundleManifestSHA256").textValue().equals(bundle.manifestSha256())
                || !descriptor
                        .path("configurationSHA256")
                        .textValue()
                        .equals(bundle.entrySha256(RuntimeDevWorkspaceBundle.CONFIGURATION))
                || !descriptor
                        .path("requestSHA256")
                        .textValue()
                        .equals(bundle.entrySha256(RuntimeDevWorkspaceBundle.REQUEST))
                || !descriptor
                        .path("planSHA256")
                        .textValue()
                        .equals(prepared.request.plan.fingerprint().sha256())
                || invocation().equals(prepared.request.invocation)) {
            throw refused("PREPARATION_BINDING_OR_FRESH_INVOCATION");
        }
        final var parent = ProcessHandle.current().parent().orElseThrow(() -> refused("PARENT"));
        if (parent.pid() != descriptor.path("parentPID").longValue()
                || !parent.info()
                        .startInstant()
                        .orElseThrow(() -> refused("PARENT_START"))
                        .equals(Instant.parse(descriptor.path("parentStartUtc").textValue()))) {
            throw refused("PARENT_IDENTITY");
        }
    }

    void checkDeadline() throws IOException {
        final var now = Instant.now();
        if (closed
                || now.isBefore(Instant.parse(descriptor.path("issuedUtc").textValue()))
                || !now.isBefore(Instant.parse(descriptor.path("deadlineUtc").textValue()))) {
            throw refused("EXPIRED_OR_CLOSED");
        }
    }

    UUID invocation() {
        return UUID.fromString(descriptor.path("invocationId").textValue());
    }

    static void keys(final JsonNode node, final Set<String> expected) throws IOException {
        final var actual = new HashSet<String>();
        if (node == null || !node.isObject()) {
            throw refused("SCHEMA");
        }
        node.fieldNames().forEachRemaining(actual::add);
        if (!actual.equals(expected)) {
            throw refused("SCHEMA");
        }
    }

    static void exact(final JsonNode node, final String key, final String value)
            throws IOException {
        if (!node.path(key).isTextual() || !value.equals(node.path(key).textValue())) {
            throw refused("SCOPE");
        }
    }

    static void match(final JsonNode node, final String key, final String regex)
            throws IOException {
        if (!node.path(key).isTextual() || !node.path(key).textValue().matches(regex)) {
            throw refused("SHAPE");
        }
    }

    static void flag(final JsonNode node, final String key, final boolean expected)
            throws IOException {
        if (!node.path(key).isBoolean() || node.path(key).booleanValue() != expected) {
            throw refused("FLAGS");
        }
    }

    static void positiveLong(final JsonNode node, final String key) throws IOException {
        if (!node.path(key).isIntegralNumber()
                || !node.path(key).canConvertToLong()
                || node.path(key).longValue() <= 0) {
            throw refused("NUMBER");
        }
    }

    static Path localFile(final Path path) throws IOException {
        final Path actual = localDirectoryOrFile(path);
        if (!Files.isRegularFile(actual, LinkOption.NOFOLLOW_LINKS)) {
            throw refused("FILE");
        }
        return actual;
    }

    static Path localDirectory(final Path path) throws IOException {
        final Path actual = localDirectoryOrFile(path);
        if (!Files.isDirectory(actual, LinkOption.NOFOLLOW_LINKS)) {
            throw refused("DIRECTORY");
        }
        return actual;
    }

    private static Path localDirectoryOrFile(final Path path) throws IOException {
        final String raw = path.toString();
        final Path actual = path.toAbsolutePath().normalize();
        if (raw.startsWith("\\\\")
                || raw.startsWith("//")
                || raw.contains("://")
                || raw.substring(Math.min(2, raw.length())).contains(":")
                || !actual.toRealPath().equals(actual)
                || Files.isSymbolicLink(actual)) {
            throw refused("LOCAL_PATH");
        }
        return actual;
    }

    private static FileChannel lease(final Path path) throws IOException {
        return FileChannel.open(
                path,
                Set.of(
                        StandardOpenOption.READ,
                        ExtendedOpenOption.NOSHARE_WRITE,
                        ExtendedOpenOption.NOSHARE_DELETE));
    }

    private static byte[] bytes(final FileChannel channel, final int maximum) throws IOException {
        if (channel.size() > maximum) {
            throw refused("FILE_LIMIT");
        }
        final var bytes = ByteBuffer.allocate((int) channel.size());
        channel.position(0);
        while (bytes.hasRemaining()) {
            if (channel.read(bytes) < 0) {
                throw refused("FILE_TRUNCATED");
            }
        }
        return bytes.array();
    }

    private static String hash(final FileChannel channel) throws Exception {
        final var digest = MessageDigest.getInstance("SHA-256");
        final var buffer = ByteBuffer.allocate(16384);
        channel.position(0);
        while (channel.read(buffer) != -1) {
            buffer.flip();
            digest.update(buffer);
            buffer.clear();
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static String hash(final byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    static IOException refused(final String code) {
        return new IOException("DEV_CATALOG_" + code);
    }

    @Override
    public void close() throws IOException {
        closed = true;
        IOException failure = null;
        for (int index = leases.size() - 1; index >= 0; index--) {
            try {
                leases.get(index).close();
            } catch (final IOException current) {
                if (failure == null) {
                    failure = current;
                } else {
                    failure.addSuppressed(current);
                }
            }
        }
        if (failure != null) {
            throw failure;
        }
    }
}
