package br.com.esl.etl.v2.bootstrap;

import br.com.esl.etl.v2.plataforma.controle.ImmutableFingerprint;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

/** TEST-RETAINED-SCOPE-001: one explicitly retained, partial local occurrence. */
final class Coletas6908TestRetainedRequest {
    static final String SOURCE = "TEST_RETAINED_SOURCE_20261005";
    static final String TENANT = "TEST_RETAINED_SCOPE_20261005";
    static final LocalDate DAY = LocalDate.of(2026, 10, 1);
    static final int MAXIMUM_ROWS = 1000;
    static final long MAXIMUM_BYTES = 10L * 1024 * 1024;
    private static final Set<String> KEYS =
            Set.of(
                    "schema",
                    "executionId",
                    "cycleId",
                    "sourceInstance",
                    "tenantScope",
                    "businessDate",
                    "retainData",
                    "per",
                    "page",
                    "maximumRows",
                    "maximumHttpCalls",
                    "maximumResponseBytes",
                    "requestTimeoutSeconds",
                    "deadlineSeconds");
    final UUID executionId;
    final UUID cycleId;
    final ImmutableFingerprint fingerprint;

    private Coletas6908TestRetainedRequest(final JsonNode root) {
        if (root == null || !root.isObject()) {
            throw new IllegalArgumentException("TEST_REQUEST_SCHEMA");
        }
        final var actual = new java.util.HashSet<String>();
        root.fieldNames().forEachRemaining(actual::add);
        if (!actual.equals(KEYS)) {
            throw new IllegalArgumentException("TEST_REQUEST_SCHEMA");
        }
        final var material = new TreeMap<String, String>();
        for (final String key : KEYS) {
            if (!root.get(key).isTextual() || root.get(key).textValue().length() > 128) {
                throw new IllegalArgumentException("TEST_REQUEST_STRING_REQUIRED");
            }
            material.put(key, root.get(key).textValue());
        }
        require(material, "schema", "test-retained-coletas-v1");
        require(material, "sourceInstance", SOURCE);
        require(material, "tenantScope", TENANT);
        require(material, "businessDate", DAY.toString());
        require(material, "retainData", "true");
        require(material, "per", "1");
        require(material, "page", "1");
        require(material, "maximumRows", "1000");
        require(material, "maximumHttpCalls", "2");
        require(material, "maximumResponseBytes", Long.toString(MAXIMUM_BYTES));
        require(material, "requestTimeoutSeconds", "30");
        require(material, "deadlineSeconds", "60");
        executionId = identifier(material.get("executionId"));
        cycleId = identifier(material.get("cycleId"));
        if (executionId.equals(cycleId)) {
            throw new IllegalArgumentException("TEST_REQUEST_IDENTITIES");
        }
        try {
            fingerprint =
                    new ImmutableFingerprint(
                            "test-retained-request-v1",
                            HexFormat.of()
                                    .formatHex(
                                            MessageDigest.getInstance("SHA-256")
                                                    .digest(
                                                            material.toString()
                                                                    .getBytes(
                                                                            StandardCharsets
                                                                                    .UTF_8))));
        } catch (final NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("TEST_REQUEST_DIGEST", impossible);
        }
    }

    static Coletas6908TestRetainedRequest read(final Path path) throws IOException {
        try (var stream = Files.newInputStream(path)) {
            return parse(stream.readNBytes(8193));
        }
    }

    static Coletas6908TestRetainedRequest parse(final byte[] bytes) throws IOException {
        if (bytes.length > 8192) {
            throw new IllegalArgumentException("TEST_REQUEST_LIMIT");
        }
        final var mapper =
                JsonMapper.builder()
                        .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                        .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                        .build();
        return new Coletas6908TestRetainedRequest(mapper.readTree(bytes));
    }

    private static UUID identifier(final String value) {
        final UUID result = UUID.fromString(value);
        if (!result.toString().equals(value) || result.equals(new UUID(0, 0))) {
            throw new IllegalArgumentException("TEST_REQUEST_IDENTITIES");
        }
        return result;
    }

    private static void require(
            final java.util.Map<String, String> values, final String key, final String expected) {
        if (!expected.equals(values.get(key))) {
            throw new IllegalArgumentException("TEST_REQUEST_SCOPE_OR_LIMIT");
        }
    }

    @Override
    public String toString() {
        return "Coletas6908TestRetainedRequest[redacted]";
    }
}
