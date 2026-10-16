package br.com.esl.etl.v2.bootstrap;

import br.com.esl.etl.v2.plataforma.fonte.dataexport.CurrentSourceInfoEvidence;

import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.sun.nio.file.ExtendedOpenOption;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.sql.SQLException;
import java.util.HexFormat;
import java.util.Set;

/** Reads the actual Fontes diagnosis adopted by Root; it issues no source permission. */
final class RuntimeDevSourceEvidenceInspection implements RuntimeDevEngineBankBinding.Admission {
    private static final String FACTS_SHA256 =
            "7ac0ef84e7b6db51264786558d47e5f758afd6ccb9b345dcebc750df59d8e2b9";
    private final Path unit;
    private final PrintStream diagnostic;
    private final br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportTemplate template;
    private boolean closed;
    private boolean currentScopeHoldObserved;
    private final CurrentSourceInfoEvidence currentEvidence;
    private CurrentSourceInfoEvidence.AcceptedInfo acceptedCurrent;
    private boolean currentEvidenceRefused;

    RuntimeDevSourceEvidenceInspection(
            final Path unit,
            final br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportTemplate template,
            final PrintStream diagnostic) {
        this(unit, template, diagnostic, null);
    }

    RuntimeDevSourceEvidenceInspection(
            final Path unit,
            final br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportTemplate template,
            final PrintStream diagnostic,
            final CurrentSourceInfoEvidence currentEvidence) {
        this.unit = unit.toAbsolutePath().normalize();
        this.template = java.util.Objects.requireNonNull(template);
        this.diagnostic = java.util.Objects.requireNonNull(diagnostic);
        this.currentEvidence = currentEvidence;
    }

    @Override
    public void verify() throws SQLException {
        currentScopeHoldObserved = false;
        if (template != br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportTemplate.COLETAS) {
            // Root's current inspection is for Coletas; no 6908 hold is inherited by Fretes.
            throw new RuntimeDevEngineBankBinding.Refusal(
                    RuntimeDevEngineBankBinding.MissingGate.ADMISSION);
        }
        if (currentEvidence != null) {
            verifyAcceptedCurrent();
            return;
        }
        if (closed || !unit.getFileName().toString().equals("hermesf4eda62e83d1")) {
            throw new RuntimeDevEngineBankBinding.Refusal(
                    RuntimeDevEngineBankBinding.MissingGate.SOURCE_QUOTA);
        }
        try {
            final Path path = unit.resolve("fontes01/minimal-causal-rule02.json");
            if (!path.toRealPath().equals(path)) {
                throw new IOException("DEV_SOURCE_FACTS_LINK_REFUSED");
            }
            try (var channel =
                    FileChannel.open(
                            path,
                            Set.of(
                                    StandardOpenOption.READ,
                                    LinkOption.NOFOLLOW_LINKS,
                                    ExtendedOpenOption.NOSHARE_WRITE,
                                    ExtendedOpenOption.NOSHARE_DELETE))) {
                if (channel.size() > 65_536) {
                    throw new IOException("DEV_SOURCE_FACTS_LIMIT");
                }
                final var bytes = ByteBuffer.allocate((int) channel.size());
                while (bytes.hasRemaining()) {
                    if (channel.read(bytes) < 0) {
                        throw new IOException("DEV_SOURCE_FACTS_TRUNCATED");
                    }
                }
                final var digest = MessageDigest.getInstance("SHA-256");
                if (!HexFormat.of().formatHex(digest.digest(bytes.array())).equals(FACTS_SHA256)) {
                    throw new IOException("DEV_SOURCE_FACTS_CHANGED");
                }
                final JsonNode facts =
                        JsonMapper.builder()
                                .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                                .build()
                                .readTree(bytes.array());
                inspectHeldFacts(facts);
                currentScopeHoldObserved = true;
                diagnostic.println(
                        "{\"status\":\"DEV_SOURCE_EVIDENCE_INSPECTED_HELD\","
                            + "\"reasonCode\":\"STOP429_SAME_CURRENT_OPERATIONAL_SCOPE_NOT_PROVED\","
                            + "\"sourceReleased\":false,\"supplierClientCreated\":false,"
                            + "\"supplierTokenRead\":false,\"factsSHA256\":\""
                                + FACTS_SHA256
                                + "\"}");
            }
        } catch (final IOException
                | java.security.NoSuchAlgorithmException
                | RuntimeException bad) {
            final var refusal =
                    new RuntimeDevEngineBankBinding.Refusal(
                            RuntimeDevEngineBankBinding.MissingGate.SOURCE_QUOTA);
            refusal.initCause(bad);
            throw refusal;
        }
        throw new RuntimeDevEngineBankBinding.Refusal(
                RuntimeDevEngineBankBinding.MissingGate.SOURCE_QUOTA);
    }

    private void verifyAcceptedCurrent() throws SQLException {
        currentEvidenceRefused = true;
        if (closed || !unit.getFileName().toString().equals("hermesc54d6a25880d")) {
            throw new RuntimeDevEngineBankBinding.Refusal(
                    RuntimeDevEngineBankBinding.MissingGate.SOURCE_QUOTA);
        }
        // The bridge checks the actual live Bank-owned scope every time, without new HTTP.
        final CurrentSourceInfoEvidence.AcceptedInfo observed;
        try {
            observed = java.util.Objects.requireNonNull(currentEvidence.verifyCurrentScope());
        } catch (final SQLException | RuntimeException refused) {
            // A transport/parser cause may contain input; expose only the stable local gate.
            throw new RuntimeDevEngineBankBinding.Refusal(
                    RuntimeDevEngineBankBinding.MissingGate.SOURCE_QUOTA);
        }
        if (observed.templateId() != template.templateId()
                || !observed.requestId().equals("HERMES_c54d6a25880d")
                || (acceptedCurrent != null && !acceptedCurrent.equals(observed))) {
            throw new RuntimeDevEngineBankBinding.Refusal(
                    RuntimeDevEngineBankBinding.MissingGate.SOURCE_QUOTA);
        }
        acceptedCurrent = observed;
        currentEvidenceRefused = false;
        diagnostic.println(
                "{\"status\":\"DEV_SOURCE_CURRENT_INFO_VERIFIED\","
                        + "\"template\":6908,\"httpStatus\":"
                        + observed.httpStatus()
                        + ",\"verificationHTTP\":0,\"supplierClientCreated\":false,"
                        + "\"supplierTokenRead\":false,\"DATA200Guaranteed\":false}");
    }

    String gateDetailCode() {
        if (currentEvidenceRefused) {
            return "STOP429_CURRENT_INFO_OR_LIVE_INPUT_BINDING_NOT_PROVED";
        }
        return currentScopeHoldObserved
                ? "STOP429_SAME_CURRENT_OPERATIONAL_SCOPE_NOT_PROVED"
                : null;
    }

    // This inspection consumes a factual HOLD, not a matching positive flag or synthetic permit.
    static void inspectHeldFacts(final JsonNode facts) throws IOException {
        if (!facts.path("schema").asText().equals("F4_FONTES_MINIMAL_CAUSAL_SOURCE_RELEASE_RULE_V1")
                || !facts.path("artifactKind")
                        .asText()
                        .equals("CAUSAL_RULE_NOT_ADMISSION_OR_PERMIT")
                || !facts.path("request").asText().equals("HERMES_f4eda62e83d1")
                || !facts.path("pong").asText().equals("PONG_f4eda62e83d1_FONTES")
                || !facts.path("minimumCausalCriterion").isArray()
                || facts.path("minimumCausalCriterion").size() != 6
                || !facts.path("currentDisposition").path("sourceReleased").isBoolean()
                || facts.path("currentDisposition").path("sourceReleased").booleanValue()
                || !facts.path("currentDisposition")
                        .path("reason")
                        .asText()
                        .equals("STOP429_SAME_CURRENT_OPERATIONAL_SCOPE_NOT_PROVED")
                || !facts.path("observedEvidence")
                        .path("sameCurrentScopeProvedByLaterReceipt")
                        .isBoolean()
                || facts.path("observedEvidence")
                        .path("sameCurrentScopeProvedByLaterReceipt")
                        .booleanValue()) {
            throw new IOException("DEV_SOURCE_CURRENT_FACTS_NOT_QUALIFIED");
        }
    }

    @Override
    public RuntimeDevEngineBankBinding.Session open() throws SQLException {
        throw new RuntimeDevEngineBankBinding.Refusal(
                RuntimeDevEngineBankBinding.MissingGate.ADMISSION);
    }

    @Override
    public void close() {
        closed = true;
    }
}
