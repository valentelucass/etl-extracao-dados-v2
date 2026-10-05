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
import java.util.Optional;

/**
 * Bank-issued evidence binds this one invocation. Its physical assertions remain Bank's
 * responsibility.
 */
final class Coletas6908SamplePreflight {
    static final String PIN_VERSION = "coletas-6908-sample-shared-preflight-sa-pin-v1";
    static final String PIN_IDENTITY = "SQL_SAMPLE_MANUAL_EXACT_LEAF_PIN_DNS_EXCEPTION_V1";
    static final String PIN_APPROVAL = "HERMES325fac56a833";
    static final String PIN_SHA256 =
            "BF723AE91FC90F3CC448122366C90650CBC2C6F5CD4A849F4B3C88C68E7A43A0";

    private Coletas6908SamplePreflight() {}

    static Optional<SharedAdmission> validate(
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
                || !authenticationMatches(root, configuration)
                || !"READY_SINGLE_SAMPLE_TRIAL".equals(root.path("status").asText())
                || !"localhost/ETL_SISTEMA_V2_SHADOW".equals(root.path("target").asText())
                || !"MATCHES_VERSIONED_PROCEDURES".equals(root.path("schemaEvidence").asText())
                || !isolationMatches(root)
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
        if (shared(root)) {
            if (!root.path("executionDeadlineUtc").isTextual()) {
                throw new IllegalArgumentException("COL_SAMPLE_SHARED_DEADLINE_REQUIRED");
            }
            final var deadline = Instant.parse(root.path("executionDeadlineUtc").textValue());
            if (!deadline.isAfter(now)
                    || deadline.isAfter(until)
                    || Duration.between(now, deadline).compareTo(Duration.ofSeconds(60)) > 0) {
                throw new IllegalArgumentException("COL_SAMPLE_SHARED_DEADLINE_REQUIRED");
            }
            final var admission =
                    new SharedAdmission(
                            root.path("isolationReference").textValue(), deadline, pinned(root));
            admission.check(configuration.clock());
            return Optional.of(admission);
        }
        return Optional.empty();
    }

    private static boolean shared(final com.fasterxml.jackson.databind.JsonNode root) {
        return "coletas-6908-sample-shared-preflight-sa-v1".equals(root.path("version").asText())
                || pinned(root);
    }

    private static boolean pinned(final com.fasterxml.jackson.databind.JsonNode root) {
        return PIN_VERSION.equals(root.path("version").asText());
    }

    private static boolean isolationMatches(final com.fasterxml.jackson.databind.JsonNode root) {
        if (shared(root)) {
            return "SHARED_INSTANCE_SHADOW_SAMPLE_ONLY".equals(root.path("mode").asText())
                    && "ETL_SISTEMA_V2_SHADOW".equals(root.path("isolationScope").asText())
                    && "TRANSACTION_OBJECT_X_LOCKS"
                            .equals(root.path("writerIsolationMode").asText())
                    && root.path("ownConnectionLoopbackOnly").isBoolean()
                    && root.path("ownConnectionLoopbackOnly").booleanValue()
                    && root.path("noConflictingShadowWriters").isBoolean()
                    && root.path("noConflictingShadowWriters").booleanValue()
                    && root.path("v1OutsideProtectedFootprint").isBoolean()
                    && root.path("v1OutsideProtectedFootprint").booleanValue()
                    && root.path("v1WindowVerified").isBoolean()
                    && root.path("v1WindowVerified").booleanValue()
                    && root.path("isolationReference").isTextual()
                    && !root.path("isolationReference").textValue().isBlank()
                    && !root.has("noConsumers")
                    && !root.has("loopbackOnly");
        }
        return root.path("loopbackOnly").isBoolean()
                && root.path("loopbackOnly").booleanValue()
                && root.path("noConsumers").isBoolean()
                && root.path("noConsumers").booleanValue();
    }

    private static boolean authenticationMatches(
            final com.fasterxml.jackson.databind.JsonNode root,
            final RuntimeConfiguration configuration) {
        if (configuration.shadowStorage().usesSampleSaAuthentication()) {
            if (configuration.shadowStorage().usesSampleSqlPinAuthentication()) {
                return pinned(root)
                        && PIN_IDENTITY.equals(root.path("identityMode").asText())
                        && PIN_APPROVAL.equals(root.path("manualApprovalReference").asText())
                        && PIN_SHA256.equals(root.path("leafSha256").asText())
                        && root.path("dnsHostnameValidated").isBoolean()
                        && !root.path("dnsHostnameValidated").booleanValue()
                        && trueBoolean(root, "tlsEncrypted")
                        && trueBoolean(root, "tlsLeafPinVerified")
                        && trueBoolean(root, "tlsPeerLoopbackVerified")
                        && trueBoolean(root, "sqlAuthVerified")
                        && "sa".equals(root.path("sqlPrincipal").asText())
                        && !root.has("tlsVerified")
                        && !root.has("windowsAuthVerified");
            }
            return ("coletas-6908-sample-preflight-sa-v1".equals(root.path("version").asText())
                            || "coletas-6908-sample-shared-preflight-sa-v1"
                                    .equals(root.path("version").asText()))
                    && root.path("sqlAuthVerified").isBoolean()
                    && root.path("sqlAuthVerified").booleanValue()
                    && "sa".equals(root.path("sqlPrincipal").asText())
                    && root.path("tlsVerified").isBoolean()
                    && root.path("tlsVerified").booleanValue()
                    && !root.has("windowsAuthVerified");
        }
        return "coletas-6908-sample-preflight-v1".equals(root.path("version").asText())
                && root.path("windowsAuthVerified").isBoolean()
                && root.path("windowsAuthVerified").booleanValue();
    }

    private static boolean trueBoolean(
            final com.fasterxml.jackson.databind.JsonNode root, final String name) {
        return root.path(name).isBoolean() && root.path(name).booleanValue();
    }

    static String sha256(final Path file) throws Exception {
        return HexFormat.of()
                .formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file)));
    }

    record SharedAdmission(
            String isolationReference, Instant executionDeadlineUtc, boolean sqlPinned) {
        SharedAdmission(final String isolationReference, final Instant executionDeadlineUtc) {
            this(isolationReference, executionDeadlineUtc, false);
        }

        void check(final java.time.Clock clock) {
            if (Thread.currentThread().isInterrupted()
                    || !clock.instant().isBefore(executionDeadlineUtc)) {
                throw new IllegalStateException("COL_SAMPLE_SHARED_DEADLINE_OR_CANCELLED");
            }
            if (sqlPinned
                    && (clock.instant().isBefore(Instant.parse("2026-10-03T20:13:47Z"))
                            || clock.instant().isAfter(Instant.parse("2056-10-03T20:13:47Z")))) {
                throw new IllegalStateException("COL_SAMPLE_PIN_CERTIFICATE_VALIDITY_REQUIRED");
            }
        }
    }
}
