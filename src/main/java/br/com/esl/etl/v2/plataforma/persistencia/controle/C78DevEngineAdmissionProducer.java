package br.com.esl.etl.v2.plataforma.persistencia.controle;

import br.com.esl.etl.v2.bootstrap.RuntimeDevEngineBankBinding;
import br.com.esl.etl.v2.bootstrap.RuntimeDevEngineBankBinding.MissingGate;
import br.com.esl.etl.v2.bootstrap.RuntimeDevEngineBankBinding.Refusal;
import br.com.esl.etl.v2.bootstrap.RuntimeDevEngineBankBinding.Scope;
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
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Concrete Root gate-inspection producer on the real adapter path. The qualified Root issuer,
 * actual native parent command/start and inherited pipe supply provenance; serialized labels and
 * hashes alone do not. Its scope is inspection only: it cannot mint a physical admission.
 */
public final class C78DevEngineAdmissionProducer implements RuntimeDevEngineBankBinding.Admission {
    private static final String ISSUER_SHA =
            "97d46fb10a779766825f77ac5c8dbc3de3236b32d8266e340853babb7f0c3a8c";
    private static final String CONTROLLER_SHA =
            "7d04572030a5ecd0de383e13e643fe38ea8b3b86e12be40ef2bcf464fc3db5d8";
    private static final String PYTHON_SHA =
            "1326cf78aeef6a027dee9fc991cdf275b113a7360e6d7db3c3e196b49fad1b10";
    private static final String REPORT39_SHA =
            "2640f9cffdd6b170870a3c1f52852420740149e2dcf71a3b401865713cd1e7fd";
    private final Scope scope;
    private final Path path;
    private final String sha;
    private final RuntimeDevEngineBankBinding.Admission sourcePreflight;
    private C78DevEngineDispatch files;
    private JsonNode descriptor;
    private boolean rootVerified;
    private boolean closed;
    private boolean reading;
    private final java.util.concurrent.ExecutorService reader =
            Executors.newSingleThreadExecutor(
                    task -> {
                        final var thread = new Thread(task, "c78-root-gate-inspection");
                        thread.setDaemon(true);
                        return thread;
                    });

    private C78DevEngineAdmissionProducer(
            final Scope scope,
            final Path path,
            final String sha,
            final RuntimeDevEngineBankBinding.Admission sourcePreflight) {
        this.scope = java.util.Objects.requireNonNull(scope);
        this.path = path;
        this.sha = sha;
        this.sourcePreflight = sourcePreflight;
    }

    /** Source preflight must be the actual Runtime/Fontes reader, never a quota/permit fixture. */
    public static C78DevEngineAdmissionProducer inspect(
            final Scope scope,
            final Path dispatch,
            final String dispatchSHA256,
            final RuntimeDevEngineBankBinding.Admission sourcePreflight) {
        return new C78DevEngineAdmissionProducer(scope, dispatch, dispatchSHA256, sourcePreflight);
    }

    @Override
    public synchronized void verify() throws SQLException {
        verifyRoot(scope);
        // Source admission is a separate dependent gate, not a prerequisite for local inspection.
        if (sourcePreflight == null) {
            throw missing(MissingGate.SOURCE_QUOTA, "CURRENT_SOURCE_PREFLIGHT_NOT_SUPPLIED");
        }
        sourcePreflight.verify();
        // The inspected original report is an antecedent, not current physical qualification.
        throw missing(MissingGate.PHYSICAL_CONTRACT, "CURRENT_SELECTED_SQL_SURFACE_NOT_QUALIFIED");
    }

    synchronized void verifyRoot(final Scope actual) throws SQLException {
        if (closed || !scope.equals(actual) || path == null || sha == null) {
            throw missing(MissingGate.ADMISSION, "ROOT_INSPECTION_DISPATCH_NOT_SUPPLIED");
        }
        try {
            if (files == null) {
                files = C78DevEngineDispatch.readInspection(path, sha, scope);
                descriptor = files.inspectionDescriptor();
            }
            files.checkpoint();
            validateDescriptor();
            final Path unit = path.toAbsolutePath().normalize().getParent().getParent();
            final JsonNode authority = files.reference("rootAuthority", MissingGate.ADMISSION);
            final JsonNode claim = files.reference("issuerClaim", MissingGate.ADMISSION);
            final Path issuer = files.codeReference("rootIssuer", ISSUER_SHA);
            final Path controller = files.codeReference("bankController", CONTROLLER_SHA);
            if (!unit.getFileName().toString().equals("hermesf4eda62e83d1")
                    || !issuer.equals(unit.resolve("root/IssueGateInspection01.py"))
                    || !controller.equals(unit.resolve("banco01/RunC78GateInspection01.py"))
                    || !authority
                            .path("schema")
                            .asText()
                            .equals("ROOT_C78_GATE_INSPECTION_SINGLE_UNIT_AUTHORITY_V1")
                    || !authority.path("decision").asText().equals("ADMIT_ONE_GATE_INSPECTION")
                    || !authority.path("request").asText().equals("HERMES_f4eda62e83d1")
                    || !authority.path("issuerSHA256").asText().equals(ISSUER_SHA)
                    || !authority
                            .path("dispatchFilename")
                            .asText()
                            .equals(path.getFileName().toString())
                    || !authority
                            .path("controller")
                            .path("path")
                            .asText()
                            .equals(controller.toString())
                    || !authority
                            .path("controller")
                            .path("sha256")
                            .asText()
                            .equals(CONTROLLER_SHA)) {
                throw new IOException("ROOT_GATE_ACTUAL_AUTHORITY_BINDING");
            }
            for (final String name :
                    List.of(
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
                            "target",
                            "leafSHA256",
                            "limits",
                            "rootIssuer",
                            "bankController",
                            "catalogReport")) {
                if (!authority.path("dispatch").path(name).equals(descriptor.path(name))) {
                    throw new IOException("ROOT_GATE_FROZEN_SCOPE_CHANGED");
                }
            }
            final String id = descriptor.path("dispatchId").asText();
            final Path claimPath = unit.resolve("root/issuer-claim-" + id + ".json");
            if (!Path.of(descriptor.path("issuerClaim").path("path").asText()).equals(claimPath)
                    || !claim.path("dispatchId").equals(descriptor.path("dispatchId"))
                    || !claim.path("nonce").equals(descriptor.path("nonce"))
                    || !claim.path("parentPID").equals(descriptor.path("parentPID"))
                    || !claim.path("parentStartUtc").equals(descriptor.path("parentStartUtc"))
                    || !claim.path("authoritySHA256")
                            .equals(descriptor.path("rootAuthority").path("sha256"))
                    || !claim.path("state").asText().equals("CONSUMED_BEFORE_DISPATCH_NO_RETRY")) {
                throw new IOException("ROOT_GATE_EXCLUSIVE_CLAIM");
            }
            final var parent = ProcessHandle.current().parent().orElseThrow();
            if (parent.pid() != descriptor.path("parentPID").longValue()
                    || !parent.isAlive()
                    || !parent.info()
                            .startInstant()
                            .orElseThrow()
                            .equals(Instant.parse(descriptor.path("parentStartUtc").asText()))) {
                throw new IOException("ROOT_GATE_ACTUAL_PARENT");
            }
            if (!rootVerified) {
                verifyPython(Path.of(parent.info().command().orElseThrow()));
                final Path authorityPath =
                        Path.of(descriptor.path("rootAuthority").path("path").asText());
                final Path bundle = ownBundle();
                C78DevRootActor.verify(
                        parent,
                        parent.info().startInstant().orElseThrow(),
                        List.of(
                                controller.toString(), authorityPath.toString(),
                                descriptor.path("rootAuthority").path("sha256").asText(),
                                        bundle.toString()),
                        remaining());
                exchange(parent);
                rootVerified = true;
            }
            files.codeReference("catalogReport", REPORT39_SHA);
            final JsonNode catalog =
                    files.reference("catalogReport", MissingGate.PHYSICAL_CONTRACT);
            if (!catalog.path("schema").asText().equals("C78_DEV_CATALOG_REPORT_V1")
                    || catalog.path("selectsAttempted").intValue() != 12) {
                throw new IOException("ROOT_GATE_ORIGINAL_REPORT39");
            }
            files.checkpoint();
        } catch (final IOException | RuntimeException failure) {
            final Refusal refused =
                    missing(MissingGate.ADMISSION, "ROOT_INSPECTION_PROVENANCE_NOT_VERIFIED");
            refused.getCause().addSuppressed(failure);
            throw refused;
        }
    }

    private void validateDescriptor() throws IOException {
        if (!descriptor.path("schema").asText().equals("V2_DEV_C78_GATE_INSPECTION_DISPATCH_V1")
                || !descriptor.path("request").asText().equals("HERMES_f4eda62e83d1")
                || !descriptor.path("operation").asText().equals("INSPECT_GATES_ONLY")
                || !descriptor.path("dispatchId").asText().matches("C78GATE01-[a-f0-9]{32}")
                || !descriptor.path("nonce").asText().matches("[a-f0-9]{64}")
                || !descriptor
                        .path("invocationId")
                        .asText()
                        .equals(scope.authorization().invocationId().toString())
                || !descriptor.path("target").asText().equals(C78LocalSqlTarget.URL)
                || !descriptor.path("leafSHA256").asText().equals(C78LocalTlsScope.LEAF_SHA256)
                || !descriptor
                        .path("bundleManifestSHA256")
                        .asText()
                        .equals(scope.bundleManifestSHA256())
                || !descriptor
                        .path("configurationSHA256")
                        .asText()
                        .equals(scope.configurationSHA256())
                || !descriptor.path("requestSHA256").asText().equals(scope.requestSHA256())
                || !descriptor.path("planSHA256").asText().equals(scope.planSHA256())
                || !descriptor.path("parentPID").isIntegralNumber()
                || !descriptor.path("parentPID").canConvertToLong()
                || descriptor.path("parentPID").longValue() < 1) {
            throw new IOException("ROOT_GATE_DESCRIPTOR");
        }
        for (final String name :
                List.of(
                        "connections",
                        "statements",
                        "HTTP",
                        "provider",
                        "commit",
                        "promotion",
                        "retry",
                        "unitSeconds")) {
            final JsonNode value = descriptor.path("limits").path(name);
            if (!value.isIntegralNumber()
                    || !value.canConvertToInt()
                    || value.intValue() != (name.equals("unitSeconds") ? 60 : 0)) {
                throw new IOException("ROOT_GATE_ZERO_EFFECT_LIMITS");
            }
        }
    }

    private static Path ownBundle() throws IOException {
        try {
            final Path jar =
                    Path.of(
                                    C78DevEngineAdmissionProducer.class
                                            .getProtectionDomain()
                                            .getCodeSource()
                                            .getLocation()
                                            .toURI())
                            .toAbsolutePath()
                            .normalize();
            if (!jar.getFileName().toString().equals("etl-dataexport-v2.jar")) {
                throw new IOException("ROOT_GATE_ACTUAL_LEASED_JAR_REQUIRED");
            }
            return jar.getParent().resolve("manifest.json");
        } catch (final java.net.URISyntaxException bad) {
            throw new IOException("ROOT_GATE_CODE_SOURCE", bad);
        }
    }

    private static void verifyPython(final Path path) throws IOException {
        try (var channel =
                FileChannel.open(
                        path,
                        Set.of(
                                StandardOpenOption.READ,
                                LinkOption.NOFOLLOW_LINKS,
                                ExtendedOpenOption.NOSHARE_WRITE,
                                ExtendedOpenOption.NOSHARE_DELETE))) {
            final MessageDigest digest = MessageDigest.getInstance("SHA-256");
            final ByteBuffer bytes = ByteBuffer.allocate(8192);
            int count, total = 0;
            while ((count = channel.read(bytes)) != -1) {
                total += count;
                if (total > 2_000_000) {
                    throw new IOException("ROOT_GATE_PARENT_IMAGE_LIMIT");
                }
                digest.update(bytes.array(), 0, count);
                bytes.clear();
            }
            if (!HexFormat.of().formatHex(digest.digest()).equals(PYTHON_SHA)) {
                throw new IOException("ROOT_GATE_PARENT_IMAGE_NOT_QUALIFIED");
            }
        } catch (final java.security.NoSuchAlgorithmException bad) {
            throw new IOException("ROOT_GATE_DIGEST", bad);
        }
    }

    private long remaining() {
        return Math.max(
                1,
                Duration.between(
                                Instant.now(),
                                Instant.parse(descriptor.path("deadlineUtc").asText()))
                        .toMillis());
    }

    private void exchange(final ProcessHandle parent) throws IOException {
        final var json =
                JsonMapper.builder()
                        .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                        .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                        .build();
        final var event = json.createObjectNode();
        event.put("kind", "ENGINE_GATE_REQUEST")
                .put("dispatchId", descriptor.path("dispatchId").asText())
                .put("dispatchSHA256", sha)
                .put("nonce", descriptor.path("nonce").asText())
                .put("parentPID", parent.pid())
                .put("childPID", ProcessHandle.current().pid())
                .put("invocationId", scope.authorization().invocationId().toString())
                .put(
                        "rootAuthoritySHA256",
                        descriptor.path("rootAuthority").path("sha256").asText());
        System.out.println(event);
        System.out.flush();
        reading = true;
        final var future =
                reader.submit(
                        () -> {
                            final var bytes = new java.io.ByteArrayOutputStream();
                            for (int index = 0; index < 8192; index++) {
                                final int value = System.in.read();
                                if (value == '\n') {
                                    return json.readTree(bytes.toByteArray());
                                }
                                if (value < 32 || value > 126) {
                                    throw new IOException("ROOT_GATE_PRIVATE_FRAME");
                                }
                                bytes.write(value);
                            }
                            throw new IOException("ROOT_GATE_PRIVATE_FRAME_LIMIT");
                        });
        try {
            final JsonNode reply = future.get(Math.min(remaining(), 10_000), TimeUnit.MILLISECONDS);
            event.put("kind", "ENGINE_GATE_PERMIT");
            verifyPrivateReply(event, reply);
        } catch (final InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IOException("ROOT_GATE_PRIVATE_INTERRUPTED", interrupted);
        } catch (final java.util.concurrent.ExecutionException
                | java.util.concurrent.TimeoutException failure) {
            throw new IOException("ROOT_GATE_PRIVATE_REFUSED", failure);
        } finally {
            future.cancel(true);
        }
    }

    /** JSON wire integers may parse as IntNode even when the request used LongNode. */
    static void verifyPrivateReply(final JsonNode expected, final JsonNode reply)
            throws IOException {
        if (reply == null || !reply.isObject() || reply.size() != 8) {
            throw new IOException("ROOT_GATE_PRIVATE_REPLY_SHAPE");
        }
        for (final String name :
                List.of(
                        "kind",
                        "dispatchId",
                        "dispatchSHA256",
                        "nonce",
                        "invocationId",
                        "rootAuthoritySHA256")) {
            final JsonNode wanted = expected.path(name);
            final JsonNode received = reply.path(name);
            if (!wanted.isTextual()
                    || !received.isTextual()
                    || !wanted.textValue().equals(received.textValue())) {
                throw new IOException("ROOT_GATE_LIVE_PRIVATE_PERMIT_BINDING");
            }
        }
        for (final String name : List.of("parentPID", "childPID")) {
            final JsonNode wanted = expected.path(name);
            final JsonNode received = reply.path(name);
            if (!wanted.isIntegralNumber()
                    || !wanted.canConvertToLong()
                    || wanted.longValue() < 1
                    || !received.isIntegralNumber()
                    || !received.canConvertToLong()
                    || received.longValue() < 1) {
                throw new IOException("ROOT_GATE_PRIVATE_REPLY_PID_TYPE");
            }
            if (wanted.longValue() != received.longValue()) {
                throw new IOException("ROOT_GATE_LIVE_PRIVATE_PERMIT_BINDING");
            }
        }
    }

    static Refusal missing(final MissingGate gate, final String reason) {
        final var refusal = new Refusal(gate);
        refusal.initCause(new SQLException(reason));
        return refusal;
    }

    @Override
    public RuntimeDevEngineBankBinding.Session open() throws SQLException {
        throw missing(MissingGate.ADMISSION, "ROOT_GATE_INSPECTION_HAS_NO_PHYSICAL_AUTHORITY");
    }

    @Override
    public synchronized void close() throws SQLException {
        if (!closed) {
            closed = true;
            SQLException failure = null;
            reader.shutdownNow();
            try {
                if (reading) {
                    System.in.close();
                }
                if (!reader.awaitTermination(1, TimeUnit.SECONDS)) {
                    failure = new SQLException("ROOT_GATE_PRIVATE_CLOSE_UNCERTAIN");
                }
            } catch (final IOException | InterruptedException cleanup) {
                if (cleanup instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                failure = new SQLException("ROOT_GATE_PRIVATE_CLOSE_FAILED", cleanup);
            }
            try {
                if (files != null) {
                    files.close();
                }
            } catch (final SQLException cleanup) {
                if (failure == null) {
                    failure = cleanup;
                } else {
                    failure.addSuppressed(cleanup);
                }
            }
            try {
                if (sourcePreflight != null) {
                    sourcePreflight.close();
                }
            } catch (final SQLException cleanup) {
                if (failure == null) {
                    failure = cleanup;
                } else {
                    failure.addSuppressed(cleanup);
                }
            }
            if (failure != null) {
                throw failure;
            }
        }
    }
}
