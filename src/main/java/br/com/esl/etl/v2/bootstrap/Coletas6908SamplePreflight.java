package br.com.esl.etl.v2.bootstrap;

import br.com.esl.etl.v2.plataforma.configuracao.RuntimeConfiguration;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;

/**
 * Bank-issued evidence binds this one invocation. Its physical assertions remain Bank's
 * responsibility.
 */
final class Coletas6908SamplePreflight {
    private Coletas6908SamplePreflight() {}

    static void validate(
            final Path evidence,
            final Path config,
            final Path plan,
            final Path request,
            final RuntimeConfiguration configuration,
            final RuntimeOperationalRequest frozen)
            throws Exception {
        final byte[] bytes;
        try (var input = Files.newInputStream(evidence)) {
            bytes = input.readNBytes(8193);
        }
        if (bytes.length == 0 || bytes.length > 8192) {
            throw new IllegalArgumentException("COL_SAMPLE_BANK_PREFLIGHT_REQUIRED");
        }
        final var root =
                JsonMapper.builder()
                        .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                        .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                        .build()
                        .readTree(bytes);
        if (!root.isObject()
                || !"coletas-6908-sample-preflight-v1".equals(root.path("version").asText())
                || !"READY_SINGLE_SAMPLE_TRIAL".equals(root.path("status").asText())
                || !"localhost/ETL_SISTEMA_V2_SHADOW".equals(root.path("target").asText())
                || !"MATCHES_VERSIONED_PROCEDURES".equals(root.path("schemaEvidence").asText())
                || !root.path("windowsAuthVerified").isBoolean()
                || !root.path("windowsAuthVerified").booleanValue()
                || !root.path("loopbackOnly").isBoolean()
                || !root.path("loopbackOnly").booleanValue()
                || !root.path("noConsumers").isBoolean()
                || !root.path("noConsumers").booleanValue()
                || !root.path("rollbackOnly").isBoolean()
                || !root.path("rollbackOnly").booleanValue()
                || !root.path("reservationReference").isTextual()
                || root.path("reservationReference").asText().isBlank()
                || !sha256(config).equals(root.path("configSha256").asText())
                || !sha256(plan).equals(root.path("planSha256").asText())
                || !sha256(request).equals(root.path("requestSha256").asText())
                || !frozen.binding
                        .contractFingerprint()
                        .sha256()
                        .equals(root.path("contractFingerprint").asText())
                || !frozen.binding
                        .configurationFingerprint()
                        .sha256()
                        .equals(root.path("configurationFingerprint").asText())) {
            throw new IllegalArgumentException("COL_SAMPLE_BANK_PREFLIGHT_REQUIRED");
        }
        final var issued = Instant.parse(root.path("issuedAtUtc").asText());
        final var until = Instant.parse(root.path("validUntilUtc").asText());
        final var now = configuration.clock().instant();
        if (issued.isAfter(now)
                || !until.isAfter(now)
                || !until.isAfter(issued)
                || Duration.between(issued, until).compareTo(Duration.ofMinutes(5)) > 0) {
            throw new IllegalArgumentException("COL_SAMPLE_BANK_PREFLIGHT_EXPIRED");
        }
    }

    static String sha256(final Path file) throws Exception {
        return HexFormat.of()
                .formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file)));
    }
}
