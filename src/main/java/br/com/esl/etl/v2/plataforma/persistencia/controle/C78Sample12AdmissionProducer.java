package br.com.esl.etl.v2.plataforma.persistencia.controle;

import br.com.esl.etl.v2.bootstrap.RuntimeDevEngineBankBinding;
import br.com.esl.etl.v2.bootstrap.RuntimeDevEngineBankBinding.MissingGate;
import br.com.esl.etl.v2.bootstrap.RuntimeDevEngineBankBinding.Refusal;
import br.com.esl.etl.v2.bootstrap.RuntimeDevEngineBankBinding.Scope;
import br.com.esl.etl.v2.plataforma.configuracao.C78LocalSqlTarget;
import br.com.esl.etl.v2.plataforma.configuracao.C78LocalTlsScope;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.CurrentSourceInfoEvidence;

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
 * Concrete selected SAMPLE12 producer on the real adapter path. The qualified Root issuer, actual
 * native parent command/start and inherited pipe supply provenance; serialized labels and hashes
 * alone do not. Source and physical admission remain separate. All-zero authority can never open a
 * connection.
 */
public final class C78Sample12AdmissionProducer implements RuntimeDevEngineBankBinding.Admission {
    private static final String PYTHON_SHA =
            "1326cf78aeef6a027dee9fc991cdf275b113a7360e6d7db3c3e196b49fad1b10";
    private static final String SQL_QUALIFICATION_SHA =
            "9b60e22c6dba8fed884a533d56307c306c716f1a065a557bdaf555dcfd0e8b43";
    private final Scope scope;
    private final C78Sample12Selection selection;
    private final Path path;
    private final String sha;
    private final RuntimeDevEngineBankBinding.Admission sourcePreflight;
    private C78DevEngineDispatch files;
    private JsonNode descriptor;
    private boolean rootVerified;
    private boolean closed;
    private boolean reading;
    private CurrentSourceInfoEvidence.AcceptedInfo acceptedSource;
    private boolean sourceRefused;
    private final java.util.concurrent.ExecutorService reader =
            Executors.newSingleThreadExecutor(
                    task -> {
                        final var thread = new Thread(task, "c78-root-gate-inspection");
                        thread.setDaemon(true);
                        return thread;
                    });

    private C78Sample12AdmissionProducer(
            final C78Sample12Selection selection,
            final Path path,
            final String sha,
            final java.util.function.Function<
                            CurrentSourceInfoEvidence, RuntimeDevEngineBankBinding.Admission>
                    sourceReaderFactory) {
        this.selection = java.util.Objects.requireNonNull(selection);
        this.scope = selection.scope();
        this.path = path;
        this.sha = sha;
        this.sourcePreflight = sourceReaderFactory.apply(this::verifyCurrentSource);
    }

    /** Source preflight must be the actual Runtime/Fontes reader, never a quota/permit fixture. */
    public static C78Sample12AdmissionProducer sample12(
            final C78Sample12Selection selection,
            final Path dispatch,
            final String dispatchSHA256,
            final RuntimeDevEngineBankBinding.Admission sourcePreflight) {
        return new C78Sample12AdmissionProducer(
                selection, dispatch, dispatchSHA256, ignored -> sourcePreflight);
    }

    /**
     * Direct consumer factory for the Source-owned typed reader. Its evidence uses this same
     * authenticated Root parent and private pipes; the parent owns the live input holder. The
     * reader factory constructs the actual reader without performing I/O. This method grants no
     * effects.
     */
    public static C78Sample12AdmissionProducer sample12Current(
            final C78Sample12Selection selection,
            final Path dispatch,
            final String dispatchSHA256,
            final java.util.function.Function<
                            CurrentSourceInfoEvidence, RuntimeDevEngineBankBinding.Admission>
                    sourceReaderFactory) {
        return new C78Sample12AdmissionProducer(
                selection,
                dispatch,
                dispatchSHA256,
                java.util.Objects.requireNonNull(sourceReaderFactory));
    }

    private synchronized CurrentSourceInfoEvidence.AcceptedInfo verifyCurrentSource()
            throws SQLException {
        if (sourceRefused) {
            throw missing(MissingGate.SOURCE_QUOTA, "CURRENT_SOURCE_LIVE_SCOPE_REFUSED");
        }
        verifyRoot(scope);
        try {
            final var event = privateEvent("ENGINE_SOURCE_VERIFY_REQUEST");
            final JsonNode reply = exchangeFrame(event);
            event.put("kind", "ENGINE_SOURCE_OBSERVATION");
            final var observed =
                    verifySourceReply(
                            event,
                            reply,
                            acceptedSource,
                            Instant.parse(descriptor.path("issuedUtc").asText()),
                            Instant.now());
            files.checkpoint();
            acceptedSource = observed;
            return observed;
        } catch (final IOException | RuntimeException failure) {
            sourceRefused = true;
            final var refusal =
                    missing(
                            MissingGate.SOURCE_QUOTA,
                            "STOP429_CURRENT_INFO_OR_LIVE_INPUT_BINDING_NOT_PROVED");
            refusal.getCause().addSuppressed(failure);
            throw refusal;
        }
    }

    @Override
    public synchronized void verify() throws SQLException {
        verifyRoot(scope);
        // Source admission is a separate dependent gate, not a prerequisite for local inspection.
        if (sourcePreflight == null) {
            throw missing(MissingGate.SOURCE_QUOTA, "CURRENT_SOURCE_PREFLIGHT_NOT_SUPPLIED");
        }
        sourcePreflight.verify();
        if (sourceZero()) {
            throw missing(MissingGate.ADMISSION, "ROOT_SAMPLE12_SOURCE0_HAS_NO_PHYSICAL_AUTHORITY");
        }
        files.verify();
    }

    synchronized void verifyRoot(final Scope actual) throws SQLException {
        if (closed || !scope.equals(actual) || path == null || sha == null) {
            throw missing(MissingGate.ADMISSION, "ROOT_INSPECTION_DISPATCH_NOT_SUPPLIED");
        }
        try {
            if (files == null) {
                files = C78DevEngineDispatch.readSample12(path, sha, selection);
                descriptor = files.inspectionDescriptor();
            }
            files.checkpoint();
            validateDescriptor();
            final Path unit = path.toAbsolutePath().normalize().getParent().getParent();
            final var namespace = C78Sample12Namespace.from(unit);
            final JsonNode authority = files.reference("rootAuthority", MissingGate.ADMISSION);
            final JsonNode claim = files.reference("issuerClaim", MissingGate.ADMISSION);
            final Path issuer = files.codeReference("rootIssuer", namespace.issuerSHA256);
            final Path controller =
                    files.codeReference("bankController", namespace.controllerSHA256);
            if (!issuer.equals(unit.resolve("root/" + namespace.issuerName))
                    || !controller.equals(unit.resolve("banco01/" + namespace.controllerName))
                    || !authority
                            .path("schema")
                            .asText()
                            .equals("ROOT_C78_SAMPLE12_SINGLE_UNIT_AUTHORITY_V1")
                    || !authority
                            .path("decision")
                            .asText()
                            .equals("ADMIT_ONE_SELECTED_SAMPLE12_UNIT")
                    || !authority.path("request").asText().equals(namespace.request)
                    || !authority.path("issuerSHA256").asText().equals(namespace.issuerSHA256)
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
                            .equals(namespace.controllerSHA256)) {
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
                            "mode",
                            "profile",
                            "nativeImage",
                            "occurrenceDirectory",
                            "writerProtectionPlan",
                            "limits",
                            "rootIssuer",
                            "bankController",
                            "sqlQualification")) {
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
            files.codeReference("sqlQualification", SQL_QUALIFICATION_SHA);
            verifyLocal(files, selection);
            files.checkpoint();
        } catch (final IOException | RuntimeException failure) {
            final Refusal refused =
                    missing(MissingGate.ADMISSION, "ROOT_INSPECTION_PROVENANCE_NOT_VERIFIED");
            refused.getCause().addSuppressed(failure);
            throw refused;
        }
    }

    private void validateDescriptor() throws IOException, SQLException {
        final var namespace =
                C78Sample12Namespace.from(
                        path.toAbsolutePath().normalize().getParent().getParent());
        if (!descriptor.path("schema").asText().equals("V2_DEV_C78_SAMPLE12_DISPATCH_V1")
                || !descriptor.path("request").asText().equals(namespace.request)
                || !descriptor.path("operation").asText().equals("SAMPLE12_ROLLBACK_ONLY")
                || !descriptor.path("dispatchId").asText().matches(namespace.dispatchPattern)
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
        verifyLocal(files, selection);
    }

    synchronized C78DevEngineDispatch controls(final Scope actual) throws SQLException {
        verifyRoot(actual);
        return files;
    }

    C78Sample12Selection selection() {
        return selection;
    }

    private boolean sourceZero() {
        return descriptor.path("mode").asText().equals("SOURCE0_PUBLIC_MAIN_NEGATIVE");
    }

    static void verifyLocal(final C78DevEngineDispatch files, final C78Sample12Selection selected)
            throws SQLException {
        files.checkpoint();
        final JsonNode d = files.inspectionDescriptor();
        final Scope actual = selected.scope();
        if (!d.path("schema").asText().equals("V2_DEV_C78_SAMPLE12_DISPATCH_V1")
                || !d.path("operation").asText().equals("SAMPLE12_ROLLBACK_ONLY")
                || !d.path("profile").asText().equals("C78_LOCAL_SHADOW_SQL_PIN_V1")
                || !d.path("invocationId")
                        .asText()
                        .equals(actual.authorization().invocationId().toString())
                || !d.path("bundleManifestSHA256").asText().equals(actual.bundleManifestSHA256())
                || !d.path("configurationSHA256").asText().equals(actual.configurationSHA256())
                || !d.path("requestSHA256").asText().equals(actual.requestSHA256())
                || !d.path("planSHA256").asText().equals(actual.planSHA256())
                || !d.path("target").asText().equals(C78LocalSqlTarget.URL)
                || !d.path("leafSHA256").asText().equals(C78LocalTlsScope.LEAF_SHA256)) {
            throw missing(MissingGate.ADMISSION, "ROOT_SAMPLE12_SELECTED_SCOPE_CHANGED");
        }
        final boolean zero = d.path("mode").asText().equals("SOURCE0_PUBLIC_MAIN_NEGATIVE");
        if (!zero && !d.path("mode").asText().equals("PHYSICAL_SAMPLE12_ROLLBACK_ONLY")) {
            throw missing(MissingGate.ADMISSION, "ROOT_SAMPLE12_MODE_NOT_ADMITTED");
        }
        final var limits = d.path("limits");
        final var values =
                java.util.Map.of(
                        "connections",
                        zero ? 0 : 1,
                        "statements",
                        zero ? 0 : 128,
                        "provider",
                        zero ? 0 : 1,
                        "HTTP",
                        zero ? 0 : 2,
                        "rows",
                        zero ? 0 : 12,
                        "querySeconds",
                        10,
                        "unitSeconds",
                        60,
                        "commit",
                        0,
                        "promotion",
                        0,
                        "retry",
                        0);
        if (!limits.isObject() || limits.size() != values.size()) {
            throw missing(MissingGate.BUDGET, "ROOT_SAMPLE12_EXACT_LIMITS_REQUIRED");
        }
        for (final var expected : values.entrySet()) {
            final var value = limits.path(expected.getKey());
            if (!value.isIntegralNumber()
                    || !value.canConvertToInt()
                    || value.intValue() != expected.getValue()) {
                throw missing(MissingGate.BUDGET, "ROOT_SAMPLE12_EXACT_LIMITS_REQUIRED");
            }
        }
        final var protection = d.path("writerProtectionPlan");
        if (!protection.path("mechanism").asText().equals("OWNED_TRANSACTION_TABLE_X_READBACK")
                || !protection.path("tables").isArray()
                || protection.path("tables").size() != 29) {
            throw missing(MissingGate.WRITER_PROTECTION, "ROOT_SAMPLE12_ALL29_REQUIRED");
        }
        final var tables = new java.util.HashSet<String>();
        for (var table : protection.path("tables")) {
            if (!table.isTextual() || !tables.add(table.textValue())) {
                throw missing(MissingGate.WRITER_PROTECTION, "ROOT_SAMPLE12_ALL29_REQUIRED");
            }
        }
        if (!tables.equals(C78DevEngineSession.TABLES)) {
            throw missing(MissingGate.WRITER_PROTECTION, "ROOT_SAMPLE12_ALL29_REQUIRED");
        }
        files.codeReference("sqlQualification", SQL_QUALIFICATION_SHA);
        final var contract = files.reference("sqlQualification", MissingGate.PHYSICAL_CONTRACT);
        if (!contract.path("schema")
                        .asText()
                        .equals("BANK_C78_SAMPLE12_INSTALLED_CONTRACT_BYTES_V1")
                || !contract.path("semantics")
                        .asText()
                        .equals("EXACT_FRESH_SAMPLE12_ONE_PAGE_ROLLBACK_ONLY")) {
            throw missing(MissingGate.PHYSICAL_CONTRACT, "ROOT_SAMPLE12_INSTALLED_BYTES_REQUIRED");
        }
        final var columns = contract.path("columnComparison").path("columns");
        final var exclusions =
                Set.of(
                        "ctl.execution_audit.status",
                        "ctl.execution_audit.traversal_verification",
                        "ctl.execution_audit.failure_category");
        if (!columns.isArray() || columns.size() != 312) {
            throw missing(
                    MissingGate.PHYSICAL_CONTRACT, "SAMPLE12_TYPE_PRESENCE_COVERAGE_REQUIRED");
        }
        int excluded = 0;
        for (final var column : columns) {
            final String name =
                    column.path("table").asText() + "." + column.path("column").asText();
            final var expected = column.path("expected");
            final var observed = column.path("actual");
            if (!expected.isObject() || !observed.isObject()) {
                throw missing(MissingGate.PHYSICAL_CONTRACT, "SAMPLE12_COLUMN_CONTRACT_UNOBSERVED");
            }
            for (String field :
                    List.of("type", "maxLength", "precision", "scale", "nullable", "identity")) {
                if (!expected.has(field) || !expected.path(field).equals(observed.path(field))) {
                    throw missing(
                            MissingGate.PHYSICAL_CONTRACT,
                            "SAMPLE12_COLUMN_TYPE_NULLABILITY_MISMATCH");
                }
            }
            final var differences = column.path("differences");
            if (exclusions.contains(name)) {
                if (differences.size() != 1 || !differences.has("collation")) {
                    throw missing(
                            MissingGate.PHYSICAL_CONTRACT, "SAMPLE12_EXCLUSION_NOT_COLLATION_ONLY");
                }
                excluded++;
            } else if (!differences.isObject() || !differences.isEmpty()) {
                throw missing(MissingGate.PHYSICAL_CONTRACT, "SAMPLE12_UNEXCLUDED_COLUMN_DRIFT");
            }
        }
        if (excluded != 3) {
            throw missing(
                    MissingGate.PHYSICAL_CONTRACT,
                    "SAMPLE12_EXACT_THREE_COLLATION_EXCLUSIONS_REQUIRED");
        }
        final var modules = contract.path("procedureFullDigestMatches").path("procedures");
        if (!modules.isArray() || modules.size() != 29) {
            throw missing(
                    MissingGate.PHYSICAL_CONTRACT,
                    "SAMPLE12_COMPLETE_INSTALLED_MODULE_COVERAGE_REQUIRED");
        }
        for (var module : modules) {
            if (!module.path("status").asText().equals("EXACT_FULL_UTF16_DIGEST_MATCH")) {
                throw missing(
                        MissingGate.PHYSICAL_CONTRACT, "SAMPLE12_INSTALLED_DEFINITION_MISMATCH");
            }
        }
        // Its exact reviewed bytes contain full installed catalog/functions/leaves and the
        // accepted finite closure. This pin grants neither authority nor future writer protection.
    }

    private static Path ownBundle() throws IOException {
        try {
            final Path jar =
                    Path.of(
                                    C78Sample12AdmissionProducer.class
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
        final var event = privateEvent("ENGINE_GATE_REQUEST");
        if (event.path("parentPID").longValue() != parent.pid()) {
            throw new IOException("ROOT_GATE_ACTUAL_PARENT");
        }
        final JsonNode reply = exchangeFrame(event);
        event.put("kind", "ENGINE_GATE_PERMIT");
        verifyPrivateReply(event, reply);
    }

    private com.fasterxml.jackson.databind.node.ObjectNode privateEvent(final String kind) {
        final var event = JsonMapper.builder().build().createObjectNode();
        return event.put("kind", kind)
                .put("dispatchId", descriptor.path("dispatchId").asText())
                .put("dispatchSHA256", sha)
                .put("nonce", descriptor.path("nonce").asText())
                .put("parentPID", descriptor.path("parentPID").longValue())
                .put("childPID", ProcessHandle.current().pid())
                .put("invocationId", scope.authorization().invocationId().toString())
                .put(
                        "rootAuthoritySHA256",
                        descriptor.path("rootAuthority").path("sha256").asText());
    }

    private JsonNode exchangeFrame(final JsonNode event) throws IOException {
        final var json =
                JsonMapper.builder()
                        .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                        .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                        .build();
        System.out.println(event);
        System.out.flush();
        if (System.out.checkError()) {
            throw new IOException("ROOT_GATE_PRIVATE_WRITE_FAILED");
        }
        reading = true;
        final var future = reader.submit(() -> readPrivateFrame(System.in, json));
        try {
            return future.get(Math.min(remaining(), 10_000), TimeUnit.MILLISECONDS);
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

    static JsonNode readPrivateFrame(final java.io.InputStream input, final JsonMapper json)
            throws IOException {
        final var bytes = new java.io.ByteArrayOutputStream();
        for (int index = 0; index < 8192; index++) {
            final int value = input.read();
            if (value == '\n') {
                return json.readTree(bytes.toByteArray());
            }
            if (value < 32 || value > 126) {
                throw new IOException("ROOT_GATE_PRIVATE_FRAME");
            }
            bytes.write(value);
        }
        throw new IOException("ROOT_GATE_PRIVATE_FRAME_LIMIT");
    }

    static CurrentSourceInfoEvidence.AcceptedInfo verifySourceReply(
            final JsonNode expected,
            final JsonNode reply,
            final CurrentSourceInfoEvidence.AcceptedInfo first,
            final Instant issued,
            final Instant now)
            throws IOException {
        if (reply == null || !reply.isObject() || reply.size() != 9 || !reply.has("observation")) {
            throw new IOException("SOURCE_PRIVATE_OBSERVATION_SHAPE");
        }
        final var binding = reply.deepCopy();
        ((com.fasterxml.jackson.databind.node.ObjectNode) binding).remove("observation");
        verifyPrivateReply(expected, binding);
        final var observation = reply.path("observation");
        if (!observation.isObject() || observation.size() != 8) {
            throw new IOException("SOURCE_PRIVATE_OBSERVATION_FIELDS");
        }
        for (final String name : List.of("requestId", "scopeBinding", "observedAt")) {
            if (!observation.path(name).isTextual()) {
                throw new IOException("SOURCE_PRIVATE_OBSERVATION_TEXT_TYPE");
            }
        }
        for (final String name :
                List.of("templateId", "httpStatus", "metadataFields", "attempts")) {
            if (!observation.path(name).isIntegralNumber()
                    || !observation.path(name).canConvertToInt()) {
                throw new IOException("SOURCE_PRIVATE_OBSERVATION_INTEGER_TYPE");
            }
        }
        if (!observation.path("responseBytes").isIntegralNumber()
                || !observation.path("responseBytes").canConvertToLong()) {
            throw new IOException("SOURCE_PRIVATE_OBSERVATION_BYTES_TYPE");
        }
        try {
            final var accepted =
                    new CurrentSourceInfoEvidence.AcceptedInfo(
                            observation.path("requestId").textValue(),
                            observation.path("scopeBinding").textValue(),
                            observation.path("templateId").intValue(),
                            observation.path("httpStatus").intValue(),
                            observation.path("responseBytes").longValue(),
                            observation.path("metadataFields").intValue(),
                            observation.path("attempts").intValue(),
                            Instant.parse(observation.path("observedAt").textValue()));
            if (accepted.observedAt().isBefore(issued)
                    || accepted.observedAt().isAfter(now)
                    || (first != null && !first.equals(accepted))) {
                throw new IOException("SOURCE_PRIVATE_FIRST_OBSERVATION_CHANGED");
            }
            return accepted;
        } catch (final IllegalArgumentException | java.time.DateTimeException invalid) {
            throw new IOException("SOURCE_PRIVATE_OBSERVATION_INVALID", invalid);
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
        return new C78DevEngineBankAdapter(path, sha, this).prepare(scope).open();
    }

    @Override
    public synchronized void close() throws SQLException {
        if (!closed) {
            closed = true;
            Throwable failure = null;
            reader.shutdownNow();
            try {
                if (reading) {
                    System.in.close();
                }
                if (!reader.awaitTermination(1, TimeUnit.SECONDS)) {
                    failure = new SQLException("ROOT_GATE_PRIVATE_CLOSE_UNCERTAIN");
                }
            } catch (final IOException | InterruptedException | RuntimeException | Error cleanup) {
                if (cleanup instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                failure = C78OwnedCleanup.failure(cleanup, "ROOT_GATE_PRIVATE_CLOSE_FAILED");
            }
            try {
                if (files != null) {
                    files.close();
                }
            } catch (final SQLException | RuntimeException | Error cleanup) {
                failure =
                        C78OwnedCleanup.combine(
                                failure,
                                C78OwnedCleanup.failure(cleanup, "SAMPLE12_FILES_CLOSE_FAILED"));
            }
            try {
                if (sourcePreflight != null) {
                    sourcePreflight.close();
                }
            } catch (final SQLException | RuntimeException | Error cleanup) {
                failure =
                        C78OwnedCleanup.combine(
                                failure,
                                C78OwnedCleanup.failure(
                                        cleanup, "SAMPLE12_SOURCE_READER_CLOSE_FAILED"));
            }
            C78OwnedCleanup.rethrow(failure);
        }
    }
}
