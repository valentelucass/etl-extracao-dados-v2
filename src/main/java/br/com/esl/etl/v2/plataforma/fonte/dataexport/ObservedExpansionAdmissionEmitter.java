package br.com.esl.etl.v2.plataforma.fonte.dataexport;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.core.StreamReadFeature;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.UUID;

/** Envelope encoding is not issuance. Only a private, consumed guard seal can mint a capability. */
public final class ObservedExpansionAdmissionEmitter {
    public static final String PROJECTION_VERSION = "observed-expansion-admission-v1";
    private static final JsonFactory FACTORY =
            JsonFactory.builder()
                    .streamReadConstraints(
                            StreamReadConstraints.builder()
                                    .maxNestingDepth(35)
                                    .maxNameLength(256)
                                    .maxNumberLength(1000)
                                    .maxStringLength(20_000_000)
                                    .build())
                    .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                    .build();

    private ObservedExpansionAdmissionEmitter() {}

    /** This returns only text. It cannot issue an AdmissionCapability from DTOs or JSON. */
    static String prepareEnvelope(
            final ObservedExpansionSchemaGuard.BoundSchema bound,
            final ObservedExpansionSchemaGuard.Inspection inspection,
            final ObservedExpansionPageProjection core,
            final ObservedExpansionPageProjector.ProjectionProof proof) {
        Objects.requireNonNull(bound);
        bound.verifyProjectorProof(inspection, core, proof);
        if (core.status() == ObservedExpansionPageProjection.Status.MAPPED) {
            if (core.pageIssue() != null
                    || core.physicalRows().isEmpty()
                    || core.physicalRows().getAsInt() != inspection.physicalRows().orElseThrow()) {
                throw new IllegalArgumentException("OBS_EXP_ADMISSION_MAPPED_COUNT");
            }
        } else if (core.pageIssue() == null
                || core.physicalRows().isPresent()
                || !core.records().isEmpty()) {
            throw new IllegalArgumentException("OBS_EXP_ADMISSION_STRUCTURE_COUNT_MUST_BE_UNKNOWN");
        }
        final var plan = inspection.rawPage().request().plan();
        final String sqlSha = bound.schemaResourceSha256().toUpperCase(Locale.ROOT);
        try {
            requireWellFormedUtf16(core.projectionJson());
            validateProducedProtocol(core, plan);
            final var output = new StringWriter(core.projectionJson().length() + 256);
            try (var json = FACTORY.createGenerator(output)) {
                json.writeStartObject();
                json.writeStringField("projectionVersion", PROJECTION_VERSION);
                json.writeStringField("schemaRelease", bound.schemaRelease());
                json.writeStringField("schemaSha256", sqlSha);
                json.writeFieldName("core");
                json.writeRawValue(core.projectionJson());
                json.writeEndObject();
            }
            final String envelope = output.toString();
            if (2L * envelope.length() > plan.limits().maximumProjectionBytes()) {
                throw new IllegalArgumentException("OBS_EXP_ADMISSION_ENVELOPE_BYTES");
            }
            return envelope;
        } catch (final IOException failure) {
            throw new IllegalArgumentException("OBS_EXP_ADMISSION_PRODUCED_JSON_INVALID", failure);
        }
    }

    /** No public route returns this seal, and its constructor is private inside the guard. */
    static AdmissionCapability materialize(final ObservedExpansionSchemaGuard.EmissionProof seal) {
        Objects.requireNonNull(seal);
        if (!seal.projectorProof().consumed()) {
            throw new IllegalArgumentException("OBS_EXP_ADMISSION_COMMITTED_PROOF_REQUIRED");
        }
        return new AdmissionCapability(seal);
    }

    /** Format checks on actual produced text; never reconstruct a truth oracle from a DTO. */
    private static void validateProducedProtocol(
            final ObservedExpansionPageProjection core, final ObservedExpansionCapturePlan plan)
            throws IOException {
        try (var parser = FACTORY.createParser(core.projectionJson())) {
            if (parser.nextToken() != JsonToken.START_OBJECT) {
                throw invalidProtocol();
            }
            final var seen = new HashSet<String>();
            while (parser.nextToken() != JsonToken.END_OBJECT) {
                if (parser.currentToken() != JsonToken.FIELD_NAME) {
                    throw invalidProtocol();
                }
                final String name = parser.currentName();
                if (!seen.add(name)) {
                    throw invalidProtocol();
                }
                final JsonToken value = parser.nextToken();
                switch (name) {
                    case "projectionVersion" -> {
                        if (value != JsonToken.VALUE_STRING
                                || !"observed-expansion-core-v1".equals(parser.getText())) {
                            throw invalidProtocol();
                        }
                    }
                    case "sourceSchemaValidation" -> {
                        if (value != JsonToken.VALUE_STRING
                                || !"NOT_PERFORMED_BY_CORE".equals(parser.getText())) {
                            throw invalidProtocol();
                        }
                    }
                    case "mapperId" -> {
                        final String expected =
                                plan.template().vertical()
                                        + "@"
                                        + ObservedExpansionCapturePlan.BASELINE_SHA256;
                        if (value != JsonToken.VALUE_STRING || !expected.equals(parser.getText())) {
                            throw invalidProtocol();
                        }
                    }
                    case "templateId" -> {
                        if (value != JsonToken.VALUE_NUMBER_INT
                                || parser.getLongValue() != plan.template().templateId()) {
                            throw invalidProtocol();
                        }
                    }
                    case "pageIssue" -> {
                        if (core.pageIssue() == null) {
                            if (value != JsonToken.VALUE_NULL) {
                                throw invalidProtocol();
                            }
                        } else if (value != JsonToken.VALUE_STRING
                                || !core.pageIssue().name().equals(parser.getText())) {
                            throw invalidProtocol();
                        }
                    }
                    case "records" -> {
                        if (value != JsonToken.START_ARRAY) {
                            throw invalidProtocol();
                        }
                        int count = 0;
                        while (parser.nextToken() != JsonToken.END_ARRAY) {
                            if (parser.currentToken() != JsonToken.START_OBJECT
                                    || ++count > plan.limits().maximumPhysicalRowsPerPage()) {
                                throw invalidProtocol();
                            }
                            parser.skipChildren();
                        }
                        if (count != core.records().size()) {
                            throw invalidProtocol();
                        }
                    }
                    default -> throw invalidProtocol();
                }
            }
            if (seen.size() != 6 || parser.nextToken() != null) {
                throw invalidProtocol();
            }
        }
    }

    private static IllegalArgumentException invalidProtocol() {
        return new IllegalArgumentException("OBS_EXP_ADMISSION_PRODUCED_CORE_PROTOCOL");
    }

    private static void requireWellFormedUtf16(final String text) {
        for (int index = 0; index < text.length(); index++) {
            final char unit = text.charAt(index);
            if (Character.isHighSurrogate(unit)) {
                if (++index >= text.length() || !Character.isLowSurrogate(text.charAt(index))) {
                    throw new IllegalArgumentException("OBS_EXP_ADMISSION_CORE_UTF16");
                }
            } else if (Character.isLowSurrogate(unit)) {
                throw new IllegalArgumentException("OBS_EXP_ADMISSION_CORE_UTF16");
            }
        }
    }

    public static final class AdmissionCapability {
        private final String schemaRelease;
        private final String schemaSha256;
        private final ObservedExpansionPageProjection core;
        private final ObservedExpansionPageProjector.ProjectionProof producer;
        private final String json;
        private final int pages;
        private final long rows;
        private final long issueRows;
        private final long rawBytes;
        private final long coreBytes;
        private final long envelopeBytes;

        private AdmissionCapability(final ObservedExpansionSchemaGuard.EmissionProof seal) {
            schemaRelease = seal.schemaRelease();
            schemaSha256 = seal.schemaSha256();
            core = seal.core();
            producer = seal.projectorProof();
            json = seal.envelope();
            pages = seal.pages();
            rows = seal.rows();
            issueRows = seal.issueRows();
            rawBytes = seal.rawBytes();
            coreBytes = seal.coreBytes();
            envelopeBytes = seal.envelopeBytes();
        }

        public String projectionVersion() {
            return PROJECTION_VERSION;
        }

        public String schemaRelease() {
            return schemaRelease;
        }

        public String schemaSha256() {
            return schemaSha256;
        }

        public ObservedExpansionRawPage rawPage() {
            return core.rawPage();
        }

        public ObservedExpansionPageProjection coreProjection() {
            return core;
        }

        public ObservedExpansionPageProjection.Status parseState() {
            return core.status();
        }

        public OptionalInt physicalRows() {
            return core.physicalRows();
        }

        public UUID captureExecutionId() {
            return rawPage().request().plan().captureExecutionId();
        }

        public String sourceInstance() {
            return rawPage().request().plan().sourceInstance();
        }

        public String tenantScope() {
            return rawPage().request().plan().tenantScope();
        }

        public String projectorProofVersion() {
            return producer.protocolVersion();
        }

        public ObservedExpansionPageProjector.Progress coreProgressBefore() {
            return producer.before();
        }

        public ObservedExpansionPageProjector.Progress coreProgressAfter() {
            return producer.after();
        }

        public int admittedPages() {
            return pages;
        }

        public long admittedRows() {
            return rows;
        }

        public long admittedIssueRows() {
            return issueRows;
        }

        public long admittedRawBytes() {
            return rawBytes;
        }

        public long admittedCoreBytes() {
            return coreBytes;
        }

        public long admittedEnvelopeBytes() {
            return envelopeBytes;
        }

        public String json() {
            return json;
        }

        public long byteLengthUtf16Le() {
            return 2L * json.length();
        }

        public byte[] utf16Le() {
            return json.getBytes(StandardCharsets.UTF_16LE);
        }

        @Override
        public String toString() {
            return "ObservedExpansionAdmissionCapability[id="
                    + captureExecutionId()
                    + ",state="
                    + parseState()
                    + ",scope=GENUINE_PROJECTOR_AND_WIRE_ONLY]";
        }
    }
}
