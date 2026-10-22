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
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarFile;

/** Own DEV artifacts held for pure startup; a bundle is never an effect authorization. */
final class RuntimeDevWorkspaceBundle implements AutoCloseable {
    static final String SCHEMA = "V2_DEV_MAIN_BUNDLE_V1";
    static final String JAR = "etl-dataexport-v2.jar";
    static final String CONFIGURATION = "runtime.private.properties";
    static final String REQUEST = "request.json";
    private final Path root;
    private final Map<String, HeldFile> files = new LinkedHashMap<>();
    private final List<HeldFile> held = new ArrayList<>();
    private String manifestSha256;
    private boolean closed;

    private RuntimeDevWorkspaceBundle(final Path root) {
        this.root = root;
    }

    static RuntimeDevWorkspaceBundle open(final Path manifest, final String profile)
            throws Exception {
        if (!C78LocalSqlTarget.PROFILE.equals(profile)
                || !"true".equals(System.getProperty("runtime.dev.workspace.enabled"))) {
            throw new Failure("DEV_WORKSPACE_EXPLICIT_OPT_IN_REQUIRED");
        }
        if (!System.getProperty("os.name", "").startsWith("Windows")) {
            throw new Failure("DEV_WORKSPACE_WINDOWS_FILE_PROTECTION_REQUIRED");
        }
        C78LocalTlsScope.validate(C78LocalSqlTarget.approved(C78LocalSqlTarget.APPROVAL_REFERENCE));
        for (final String option :
                java.lang.management.ManagementFactory.getRuntimeMXBean().getInputArguments()) {
            if (option.startsWith("-javaagent")
                    || option.startsWith("-agentlib")
                    || option.startsWith("-agentpath")
                    || option.startsWith("-Xbootclasspath")
                    || option.startsWith("--patch-module")
                    || option.startsWith("-Djava.system.class.loader")) {
                throw new Failure("DEV_WORKSPACE_CODE_OVERLAY_REFUSED");
            }
        }
        final Path absolute = localPath(manifest);
        final Path jar =
                localPath(
                        Path.of(
                                RuntimeDevWorkspaceBundle.class
                                        .getProtectionDomain()
                                        .getCodeSource()
                                        .getLocation()
                                        .toURI()));
        if (!JAR.equals(jar.getFileName().toString())
                || !jar.getParent().equals(absolute.getParent())
                || !Path.of(System.getProperty("java.class.path"))
                        .toAbsolutePath()
                        .normalize()
                        .equals(jar)) {
            throw new Failure("DEV_WORKSPACE_OWN_JAR_CODESOURCE_REQUIRED");
        }
        final var bundle = new RuntimeDevWorkspaceBundle(absolute.getParent());
        try {
            final var manifestFile = bundle.hold(absolute, 65_536);
            bundle.manifestSha256 = manifestFile.hash();
            final var entries = parse(manifestFile.bytes(65_536));
            for (final var entry : entries.entrySet()) {
                final var file = bundle.hold(bundle.root.resolve(entry.getKey()), 67_108_864);
                if (!entry.getValue().equals(file.hash())) {
                    throw new Failure("DEV_WORKSPACE_FILE_HASH_CHANGED");
                }
                bundle.files.put(entry.getKey(), file);
            }
            try (var archive = new JarFile(jar.toFile())) {
                final var header = archive.getManifest();
                if (header == null
                        || !Main.class
                                .getName()
                                .equals(header.getMainAttributes().getValue("Main-Class"))) {
                    throw new Failure("DEV_WORKSPACE_MAIN_ENTRY_REQUIRED");
                }
                final String classpath = header.getMainAttributes().getValue("Class-Path");
                if (classpath == null || classpath.length() > 16_384) {
                    throw new Failure("DEV_WORKSPACE_CLASSPATH_REQUIRED");
                }
                final var dependencies = new HashSet<String>();
                for (final String name : classpath.trim().split(" +")) {
                    if (!name.matches("lib/[A-Za-z0-9_.-]+\\.jar")
                            || !dependencies.add(name)
                            || !entries.containsKey(name)) {
                        throw new Failure("DEV_WORKSPACE_CLASSPATH_UNPINNED");
                    }
                }
                final var declared = new HashSet<>(entries.keySet());
                declared.removeAll(Set.of(JAR, CONFIGURATION, REQUEST));
                if (!dependencies.equals(declared)
                        || archive.getEntry("runtime-laboratory.properties") != null) {
                    throw new Failure("DEV_WORKSPACE_LABORATORY_OR_CLASSPATH_REFUSED");
                }
            }
            return bundle;
        } catch (final Exception | Error failure) {
            try {
                bundle.close();
            } catch (final IOException cleanup) {
                failure.addSuppressed(cleanup);
            }
            throw failure;
        }
    }

    static Map<String, String> parse(final byte[] bytes) throws IOException {
        if (bytes.length > 65_536) {
            throw new Failure("DEV_WORKSPACE_MANIFEST_LIMIT");
        }
        final JsonNode document;
        try {
            document =
                    JsonMapper.builder()
                            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                            .build()
                            .readTree(bytes);
        } catch (final IOException failure) {
            throw new Failure("DEV_WORKSPACE_MANIFEST_JSON_INVALID");
        }
        final Set<String> keys =
                Set.of(
                        "schema",
                        "mode",
                        "profile",
                        "target",
                        "leafSHA256",
                        "authorityAdmitted",
                        "sourceAdmitted",
                        "sqlAdmitted",
                        "files");
        if (document == null || !document.isObject()) {
            throw new Failure("DEV_WORKSPACE_MANIFEST_SCHEMA");
        }
        final var actual = new HashSet<String>();
        document.fieldNames().forEachRemaining(actual::add);
        if (!actual.equals(keys)
                || !text(document, "schema", SCHEMA)
                || !text(document, "mode", "PREPARE_ONLY")
                || !text(document, "profile", C78LocalSqlTarget.PROFILE)
                || !text(document, "target", "localhost:1433/ETL_SISTEMA_V2_SHADOW")
                || !text(document, "leafSHA256", C78LocalTlsScope.LEAF_SHA256)) {
            throw new Failure("DEV_WORKSPACE_MANIFEST_SCOPE");
        }
        for (final String key : List.of("authorityAdmitted", "sourceAdmitted", "sqlAdmitted")) {
            if (!document.get(key).isBoolean() || document.get(key).booleanValue()) {
                throw new Failure("DEV_WORKSPACE_BUNDLE_IS_NOT_AUTHORITY");
            }
        }
        final var entries = document.get("files");
        if (!entries.isArray() || entries.size() < 4 || entries.size() > 32) {
            throw new Failure("DEV_WORKSPACE_ENTRY_LIMIT");
        }
        final var result = new LinkedHashMap<String, String>();
        final var folded = new HashSet<String>();
        for (final var entry : entries) {
            final var fields = new HashSet<String>();
            entry.fieldNames().forEachRemaining(fields::add);
            if (!entry.isObject()
                    || !fields.equals(Set.of("path", "sha256"))
                    || !entry.get("path").isTextual()
                    || !entry.get("sha256").isTextual()) {
                throw new Failure("DEV_WORKSPACE_ENTRY_SCHEMA");
            }
            final String name = entry.get("path").textValue();
            final String hash = entry.get("sha256").textValue();
            if (!(Set.of(JAR, CONFIGURATION, REQUEST).contains(name)
                            || name.matches("lib/[A-Za-z0-9_.-]+\\.jar"))
                    || name.contains("..")
                    || !hash.matches("[a-f0-9]{64}")
                    || !folded.add(name.toLowerCase(java.util.Locale.ROOT))) {
                throw new Failure("DEV_WORKSPACE_ENTRY_PATH_OR_HASH");
            }
            result.put(name, hash);
        }
        if (!result.keySet().containsAll(Set.of(JAR, CONFIGURATION, REQUEST))) {
            throw new Failure("DEV_WORKSPACE_REQUIRED_ENTRY_MISSING");
        }
        return Map.copyOf(result);
    }

    private static boolean text(final JsonNode node, final String field, final String expected) {
        return node.get(field).isTextual() && expected.equals(node.get(field).textValue());
    }

    private static Path localPath(final Path value) throws IOException {
        final String raw = value.toString();
        if (raw.startsWith("\\\\")
                || raw.startsWith("//")
                || raw.contains("://")
                || raw.substring(Math.min(2, raw.length())).contains(":")) {
            throw new Failure("DEV_WORKSPACE_LOCAL_PATH_REQUIRED");
        }
        final var absolute = value.toAbsolutePath().normalize();
        if (!absolute.toRealPath().equals(absolute)
                || !Files.isRegularFile(absolute, LinkOption.NOFOLLOW_LINKS)
                || Files.isSymbolicLink(absolute)) {
            throw new Failure("DEV_WORKSPACE_LINK_OR_FILE_REFUSED");
        }
        return absolute;
    }

    private HeldFile hold(final Path path, final long limit) throws IOException {
        final Path actual = localPath(path);
        if (!actual.startsWith(root)) {
            throw new Failure("DEV_WORKSPACE_ESCAPE_REFUSED");
        }
        final var channel =
                FileChannel.open(
                        actual,
                        Set.of(
                                StandardOpenOption.READ,
                                ExtendedOpenOption.NOSHARE_WRITE,
                                ExtendedOpenOption.NOSHARE_DELETE));
        FileLock lock = null;
        try {
            lock = channel.tryLock(0, Long.MAX_VALUE, true);
            if (lock == null || channel.size() > limit) {
                throw new Failure("DEV_WORKSPACE_FILE_LEASE_OR_LIMIT");
            }
            final var file = new HeldFile(channel, lock, limit);
            file.expectedHash = file.hash();
            held.add(file);
            return file;
        } catch (final IOException | RuntimeException failure) {
            if (lock != null) {
                lock.close();
            }
            channel.close();
            throw failure;
        }
    }

    Path configuration() {
        requireOpen();
        return root.resolve(CONFIGURATION);
    }

    Path request() {
        requireOpen();
        return root.resolve(REQUEST);
    }

    private void requireOpen() {
        if (closed) {
            throw new IllegalStateException("DEV_WORKSPACE_LEASE_CLOSED");
        }
    }

    String manifestSha256() {
        return manifestSha256;
    }

    int pinnedFiles() {
        return files.size();
    }

    String entrySha256(final String name) {
        requireOpen();
        final var file = files.get(name);
        if (file == null) {
            throw new IllegalArgumentException("DEV_WORKSPACE_ENTRY_NOT_PINNED");
        }
        return file.expectedHash;
    }

    @Override
    public void close() throws IOException {
        if (closed) {
            return;
        }
        closed = true;
        IOException failure = null;
        for (int index = held.size() - 1; index >= 0; index--) {
            final var file = held.get(index);
            try {
                if (!file.expectedHash.equals(file.hash())) {
                    throw new Failure("DEV_WORKSPACE_FILE_CHANGED_DURING_LEASE");
                }
            } catch (final IOException current) {
                failure = accumulate(failure, current);
            } finally {
                try {
                    file.lock.close();
                } catch (final IOException current) {
                    failure = accumulate(failure, current);
                } finally {
                    try {
                        file.channel.close();
                    } catch (final IOException current) {
                        failure = accumulate(failure, current);
                    }
                }
            }
        }
        if (failure != null) {
            throw failure;
        }
    }

    private static IOException accumulate(final IOException first, final IOException next) {
        if (first == null) {
            return next;
        }
        first.addSuppressed(next);
        return first;
    }

    static final class Failure extends IOException {
        private static final long serialVersionUID = 1L;

        Failure(final String code) {
            super(code);
        }
    }

    private static final class HeldFile {
        private final FileChannel channel;
        private final FileLock lock;
        private final long limit;
        private String expectedHash;

        HeldFile(final FileChannel channel, final FileLock lock, final long limit) {
            this.channel = channel;
            this.lock = lock;
            this.limit = limit;
        }

        byte[] bytes(final int maximum) throws IOException {
            if (channel.size() > maximum) {
                throw new Failure("DEV_WORKSPACE_FILE_LIMIT");
            }
            final var bytes = ByteBuffer.allocate((int) channel.size());
            channel.position(0);
            while (bytes.hasRemaining()) {
                if (channel.read(bytes) < 0) {
                    throw new Failure("DEV_WORKSPACE_FILE_TRUNCATED");
                }
            }
            return bytes.array();
        }

        String hash() throws IOException {
            try {
                final var digest = MessageDigest.getInstance("SHA-256");
                final var bytes = ByteBuffer.allocate(8192);
                channel.position(0);
                long count = 0;
                while (channel.read(bytes) != -1) {
                    count += bytes.position();
                    if (count > limit) {
                        throw new Failure("DEV_WORKSPACE_FILE_LIMIT");
                    }
                    bytes.flip();
                    digest.update(bytes);
                    bytes.clear();
                }
                return HexFormat.of().formatHex(digest.digest());
            } catch (final java.security.NoSuchAlgorithmException failure) {
                throw new Failure("DEV_WORKSPACE_HASH_UNAVAILABLE");
            }
        }
    }
}
