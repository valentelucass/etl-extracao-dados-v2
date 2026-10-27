package br.com.esl.etl.v2.plataforma.fonte.dataexport;

import com.fasterxml.jackson.core.JsonFactory;
import java.io.IOException;
import java.io.StringWriter;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Business-bearing values stay in RAM. Only technicalJson() is an external-output surface. */
final class ObservedExpansionCaptureResult {
    enum Outcome {
        LOCAL_TERMINAL,
        PARTIAL_PAGE_LIMIT,
        INFO_429,
        INFO_SOURCE_STOPPED,
        INFO_FAILED,
        INFO_SCHEMA_REJECTED,
        DATA_FAILED,
        SCHEMA_STRUCTURE_ISSUE,
        CORE_STRUCTURE_ISSUE,
        CORE_FAILED,
        ENVELOPE_FAILED,
        PROJECTION_TOTAL_BOUND,
        CANCELLED
    }

    private final ObservedExpansionCapturePlan plan;
    private final Outcome outcome;
    private final List<Page> pages;
    private final int infoAttempts;
    private final int dataAttempts;

    ObservedExpansionCaptureResult(
            final ObservedExpansionCapturePlan plan,
            final Outcome outcome,
            final List<Page> pages,
            final int infoAttempts,
            final int dataAttempts) {
        this.plan = Objects.requireNonNull(plan);
        this.outcome = Objects.requireNonNull(outcome);
        this.pages = List.copyOf(pages);
        this.infoAttempts = infoAttempts;
        this.dataAttempts = dataAttempts;
        if (infoAttempts < 0
                || infoAttempts > 1
                || dataAttempts < 0
                || dataAttempts > plan.limits().maximumPages()
                || infoAttempts + dataAttempts > 5
                || pages.size() > dataAttempts
                || pages.size() > plan.limits().maximumPages()) {
            throw new IllegalArgumentException("OBS_DRIVER_RESULT_BOUND");
        }
        long rawBytes = 0;
        long envelopeBytes = 0;
        for (int index = 0; index < this.pages.size(); index++) {
            final Page page = this.pages.get(index);
            if (!plan.equals(page.raw.request().plan()) || page.raw.request().page() != index + 1) {
                throw new IllegalArgumentException("OBS_DRIVER_RESULT_SEQUENCE");
            }
            rawBytes += page.raw.byteLength();
            if (page.envelope != null) {
                envelopeBytes += page.envelope.byteLengthUtf16Le();
            }
        }
        if (rawBytes > plan.limits().maximumRawBytes()
                || envelopeBytes > plan.limits().maximumProjectionTotalBytes()) {
            throw new IllegalArgumentException("OBS_DRIVER_RESULT_TOTAL_BOUND");
        }
        if (outcome == Outcome.LOCAL_TERMINAL) {
            if (this.pages.isEmpty() || !this.pages.get(this.pages.size() - 1).emptyMapped()) {
                throw new IllegalArgumentException("OBS_DRIVER_TERMINAL_EVIDENCE_REQUIRED");
            }
        }
        if (outcome == Outcome.PARTIAL_PAGE_LIMIT
                && (this.pages.size() != plan.limits().maximumPages()
                        || this.pages.stream().anyMatch(page -> !page.nonEmptyMapped()))) {
            throw new IllegalArgumentException("OBS_DRIVER_PARTIAL_EVIDENCE_REQUIRED");
        }
    }

    Outcome outcome() {
        return outcome;
    }

    List<Page> pages() {
        return pages;
    }

    int infoAttempts() {
        return infoAttempts;
    }

    int dataAttempts() {
        return dataAttempts;
    }

    /** Explicit whitelist: never serializes a DTO, raw page, field value, cursor or exception. */
    String technicalJson() {
        final var output = new StringWriter(1024);
        try (var json = new JsonFactory().createGenerator(output)) {
            json.writeStartObject();
            json.writeStringField("receiptVersion", "observed-expansion-driver-tech-v1");
            json.writeStringField("captureExecutionId", plan.captureExecutionId().toString());
            json.writeNumberField("templateId", plan.template().templateId());
            json.writeStringField("outcome", outcome.name());
            json.writeNumberField("observerAcceptedInfoAttempts", infoAttempts);
            json.writeNumberField("observerAcceptedDataAttempts", dataAttempts);
            json.writeNumberField("receivedPages", pages.size());
            json.writeStringField("physicalAuthority", "NOT_GRANTED_BY_DRIVER");
            json.writeStringField("coreSchemaValidation", "NOT_PERFORMED_BY_CORE");
            json.writeBooleanField("globalCompletenessProven", false);
            json.writeArrayFieldStart("pages");
            for (final Page page : pages) {
                json.writeStartObject();
                json.writeNumberField("page", page.raw.request().page());
                json.writeNumberField("receivedBytes", page.raw.byteLength());
                json.writeBooleanField("schemaAccepted", page.schemaAccepted());
                if (page.inspection != null && page.inspection.issue() != null) {
                    json.writeStringField("schemaIssue", page.inspection.issue().name());
                } else {
                    json.writeNullField("schemaIssue");
                }
                if (page.core != null) {
                    json.writeStringField("coreState", page.core.status().name());
                    if (page.core.pageIssue() != null) {
                        json.writeStringField("coreIssue", page.core.pageIssue().name());
                    } else {
                        json.writeNullField("coreIssue");
                    }
                } else {
                    json.writeNullField("coreState");
                    json.writeNullField("coreIssue");
                }
                json.writeBooleanField("admissionEnvelopePresent", page.envelope != null);
                if (page.envelope != null
                        && page.core.status() == ObservedExpansionPageProjection.Status.MAPPED) {
                    json.writeNumberField(
                            "mappedPhysicalRows", page.core.physicalRows().orElseThrow());
                    json.writeNumberField("fieldIssueRows", page.core.fieldIssueRows());
                } else {
                    json.writeNullField("mappedPhysicalRows");
                    json.writeNullField("fieldIssueRows");
                }
                json.writeNumberField(
                        "admissionEnvelopeBytesUtf16Le",
                        page.envelope == null ? 0 : page.envelope.byteLengthUtf16Le());
                json.writeEndObject();
            }
            json.writeEndArray();
            json.writeEndObject();
        } catch (final IOException failure) {
            throw new IllegalStateException("OBS_DRIVER_TECHNICAL_RECEIPT_ENCODING");
        }
        final String receipt = output.toString();
        if (receipt.length() > 16_384) {
            throw new IllegalStateException("OBS_DRIVER_RECEIPT_BOUND");
        }
        return receipt;
    }

    @Override
    public String toString() {
        return "ObservedExpansionCaptureResult[template="
                + plan.template().templateId()
                + ",outcome="
                + outcome
                + ",pages="
                + pages.size()
                + ",scope=RAM_NO_AUTHORITY]";
    }

    static final class Page {
        private final ObservedExpansionRawPage raw;
        private final ObservedExpansionSchemaGuard.Inspection inspection;
        private final ObservedExpansionPageProjection core;
        private final ObservedExpansionAdmissionEmitter.AdmissionCapability envelope;

        private Page(
                final ObservedExpansionRawPage raw,
                final ObservedExpansionSchemaGuard.Inspection inspection,
                final ObservedExpansionPageProjection core,
                final ObservedExpansionAdmissionEmitter.AdmissionCapability envelope) {
            this.raw = Objects.requireNonNull(raw);
            this.inspection = inspection;
            this.core = core;
            this.envelope = envelope;
            if (inspection != null && inspection.rawPage() != raw
                    || core != null
                            && (inspection == null
                                    || !inspection.schemaMatched()
                                    || core.rawPage() != raw
                                    || !core.sourceSchemaValidation()
                                            .equals("NOT_PERFORMED_BY_CORE"))
                    || envelope != null
                            && (core == null
                                    || envelope.rawPage() != raw
                                    || envelope.coreProjection() != core
                                    || !envelope.schemaRelease().equals(inspection.schemaRelease())
                                    || !envelope.schemaSha256()
                                            .equals(
                                                    inspection
                                                            .schemaResourceSha256()
                                                            .toUpperCase(java.util.Locale.ROOT)))) {
                throw new IllegalArgumentException("OBS_DRIVER_RAW_GUARD_CORE_BINDING");
            }
        }

        static Page rawOnly(final ObservedExpansionRawPage raw) {
            return new Page(raw, null, null, null);
        }

        static Page inspected(final ObservedExpansionSchemaGuard.Inspection inspection) {
            return new Page(inspection.rawPage(), inspection, null, null);
        }

        static Page projected(
                final ObservedExpansionSchemaGuard.Inspection inspection,
                final ObservedExpansionPageProjector.ProvenProjection step) {
            return new Page(
                    inspection.rawPage(),
                    inspection,
                    Objects.requireNonNull(step).projection(),
                    null);
        }

        static Page admitted(
                final ObservedExpansionSchemaGuard.Inspection inspection,
                final ObservedExpansionPageProjector.ProvenProjection step,
                final ObservedExpansionAdmissionEmitter.AdmissionCapability envelope) {
            Objects.requireNonNull(step);
            Objects.requireNonNull(envelope);
            if (!step.proof().consumed()
                    || envelope.coreProjection() != step.projection()
                    || envelope.coreProgressBefore() != step.proof().before()
                    || envelope.coreProgressAfter() != step.proof().after()) {
                throw new IllegalArgumentException("OBS_DRIVER_GENUINE_ADMITTED_STEP_REQUIRED");
            }
            return new Page(inspection.rawPage(), inspection, step.projection(), envelope);
        }

        ObservedExpansionRawPage rawPage() {
            return raw;
        }

        boolean schemaAccepted() {
            return inspection != null && inspection.schemaMatched();
        }

        Optional<ObservedExpansionPageProjection> core() {
            return Optional.ofNullable(core);
        }

        Optional<ObservedExpansionAdmissionEmitter.AdmissionCapability> envelope() {
            return Optional.ofNullable(envelope);
        }

        private boolean emptyMapped() {
            return envelope != null
                    && core.status() == ObservedExpansionPageProjection.Status.MAPPED
                    && core.physicalRows().orElseThrow() == 0;
        }

        private boolean nonEmptyMapped() {
            return envelope != null
                    && core.status() == ObservedExpansionPageProjection.Status.MAPPED
                    && core.physicalRows().orElseThrow() > 0;
        }

        @Override
        public String toString() {
            return "ObservedExpansionCapturePage[page="
                    + raw.request().page()
                    + ",bytes="
                    + raw.byteLength()
                    + ",schemaAccepted="
                    + schemaAccepted()
                    + ",envelope="
                    + (envelope != null)
                    + "]";
        }
    }
}
