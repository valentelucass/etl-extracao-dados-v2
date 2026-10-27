package br.com.esl.etl.v2.plataforma.fonte.dataexport;

import br.com.esl.etl.v2.plataforma.contrato.ContractMetadata;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

/** Observed-wire validator only. No result here is source/storage/promotion authority. */
public final class ObservedExpansionSchemaGuard {
    private ObservedExpansionSchemaGuard() {}

    public enum Issue {
        INVALID_UTF8,
        INVALID_JSON_OR_NODE_BOUND,
        ROOT_ARRAY_REQUIRED,
        PHYSICAL_ROW_BOUND,
        RECORD_OBJECT_REQUIRED,
        UNDECLARED_FIELD,
        MISSING_FIELD,
        NULL_NOT_OBSERVED,
        NON_NULL_TYPE_UNOBSERVED,
        WIRE_TYPE,
        ARRAY_ITEM_TYPE
    }

    /** observedInfoTemplate must come from the trusted INFO request/receipt context upstream. */
    public static BoundSchema bind(
            final ObservedExpansionCapturePlan plan,
            final ObservedExpansionSchemaCatalog.Selection selection,
            final int observedInfoTemplate,
            final ContractMetadata observedInfo) {
        Objects.requireNonNull(plan);
        Objects.requireNonNull(selection);
        Objects.requireNonNull(observedInfo);
        if (plan.template() != selection.template()
                || plan.template().templateId() != observedInfoTemplate
                || plan.envelope() != ObservedExpansionCapturePlan.Envelope.ROOT_ARRAY) {
            throw new IllegalArgumentException("OBS_EXP_SCHEMA_PLAN_TEMPLATE_OR_ENVELOPE");
        }
        final var schema = ObservedExpansionSchemaCatalog.schema(selection);
        if (!schema.metadata().equals(observedInfo)) {
            throw new IllegalArgumentException("OBS_EXP_SCHEMA_INFO_DRIFT");
        }
        return new BoundSchema(plan, schema);
    }

    public static final class BoundSchema {
        private final ObservedExpansionCapturePlan plan;
        private final ObservedExpansionSchemaCatalog.Schema schema;
        private final ObservedExpansionPageProjector projector;
        private ObservedExpansionPageProjector.ProjectionProof lastAdmittedProof;
        private int admittedPages;
        private long admittedRows;
        private long admittedIssueRows;
        private long admittedRawBytes;
        private long admittedCoreBytes;
        private long admittedEnvelopeBytes;

        private BoundSchema(
                final ObservedExpansionCapturePlan plan,
                final ObservedExpansionSchemaCatalog.Schema schema) {
            this.plan = plan;
            this.schema = schema;
            this.projector = new ObservedExpansionPageProjector(plan);
        }

        public String schemaRelease() {
            return schema.selection().schemaRelease();
        }

        public String schemaResourceSha256() {
            return schema.selection().schemaResourceSha256();
        }

        /** Run the one private projector after this binding actually inspected these raw bytes. */
        public synchronized ObservedExpansionPageProjector.ProvenProjection project(
                final Inspection inspection) {
            if (!owns(inspection) || !inspection.schemaMatched()) {
                throw new IllegalArgumentException(
                        "OBS_EXP_SCHEMA_PROOF_BEFORE_PROJECTION_REQUIRED");
            }
            return projector.projectNextWithProof(inspection.rawPage());
        }

        public synchronized ObservedExpansionAdmissionEmitter.AdmissionCapability admit(
                final Inspection inspection,
                final ObservedExpansionPageProjector.ProvenProjection step) {
            if (step == null) {
                throw new IllegalArgumentException("OBS_EXP_PROJECTOR_PROOF_REQUIRED");
            }
            return admit(inspection, step.projection(), step.proof());
        }

        /** No DTO-only overload exists: genuine producer proof is mandatory for every result. */
        public synchronized ObservedExpansionAdmissionEmitter.AdmissionCapability admit(
                final Inspection inspection,
                final ObservedExpansionPageProjection core,
                final ObservedExpansionPageProjector.ProjectionProof proof) {
            verifyProjectorProof(inspection, core, proof);
            final String envelope =
                    ObservedExpansionAdmissionEmitter.prepareEnvelope(
                            this, inspection, core, proof);
            final long nextEnvelopeBytes = admittedEnvelopeBytes + 2L * envelope.length();
            if (nextEnvelopeBytes > plan.limits().maximumProjectionTotalBytes()) {
                throw new IllegalArgumentException("OBS_EXP_ADMISSION_ENVELOPE_TOTAL_BYTES");
            }
            proof.consume(projector, plan, inspection.rawPage(), core, lastAdmittedProof);
            lastAdmittedProof = proof;
            admittedPages++;
            admittedRows = proof.after().nextOccurrence() - 1;
            admittedIssueRows = proof.after().issueRows();
            admittedRawBytes = proof.after().rawBytes();
            admittedCoreBytes = proof.after().projectionBytes();
            admittedEnvelopeBytes = nextEnvelopeBytes;
            final var emission =
                    new EmissionProof(
                            this,
                            core,
                            proof,
                            envelope,
                            admittedPages,
                            admittedRows,
                            admittedIssueRows,
                            admittedRawBytes,
                            admittedCoreBytes,
                            admittedEnvelopeBytes);
            return ObservedExpansionAdmissionEmitter.materialize(emission);
        }

        synchronized void verifyProjectorProof(
                final Inspection inspection,
                final ObservedExpansionPageProjection core,
                final ObservedExpansionPageProjector.ProjectionProof proof) {
            if (!owns(inspection) || !inspection.schemaMatched()) {
                throw new IllegalArgumentException("OBS_EXP_ADMISSION_SCHEMA_PROOF_REQUIRED");
            }
            if (proof == null || core == null) {
                throw new IllegalArgumentException("OBS_EXP_PROJECTOR_PROOF_REQUIRED");
            }
            proof.verify(projector, plan, inspection.rawPage(), core, lastAdmittedProof);
            if (admittedPages != proof.before().nextPage() - 1
                    || admittedRows != proof.before().nextOccurrence() - 1
                    || admittedIssueRows != proof.before().issueRows()
                    || admittedRawBytes != proof.before().rawBytes()
                    || admittedCoreBytes != proof.before().projectionBytes()) {
                throw new IllegalArgumentException("OBS_EXP_ADMISSION_ALL_PROGRESS_REQUIRED");
            }
        }

        boolean owns(final Inspection inspection) {
            return inspection != null
                    && inspection.owner == this
                    && plan.equals(inspection.rawPage().request().plan());
        }

        public synchronized int admittedPages() {
            return admittedPages;
        }

        public synchronized long admittedRows() {
            return admittedRows;
        }

        public synchronized long admittedIssueRows() {
            return admittedIssueRows;
        }

        public synchronized long admittedRawBytes() {
            return admittedRawBytes;
        }

        public synchronized long admittedCoreBytes() {
            return admittedCoreBytes;
        }

        public synchronized long admittedEnvelopeBytes() {
            return admittedEnvelopeBytes;
        }

        public Inspection inspect(final ObservedExpansionRawPage rawPage) {
            Objects.requireNonNull(rawPage);
            if (!plan.equals(rawPage.request().plan())) {
                throw new IllegalArgumentException("OBS_EXP_SCHEMA_EXACT_PLAN_MISMATCH");
            }
            final String text;
            try {
                text =
                        StandardCharsets.UTF_8
                                .newDecoder()
                                .onMalformedInput(CodingErrorAction.REPORT)
                                .onUnmappableCharacter(CodingErrorAction.REPORT)
                                .decode(ByteBuffer.wrap(rawPage.body()))
                                .toString();
            } catch (final CharacterCodingException failure) {
                return rejected(rawPage, Issue.INVALID_UTF8);
            }
            final JsonNode root;
            try {
                root = DataExportStrictJsonParser.readTree(text, plan.limits().maximumJsonNodes());
            } catch (final JsonProcessingException failure) {
                return rejected(rawPage, Issue.INVALID_JSON_OR_NODE_BOUND);
            }
            if (root == null || !root.isArray()) {
                return rejected(rawPage, Issue.ROOT_ARRAY_REQUIRED);
            }
            if (root.size() > plan.limits().maximumPhysicalRowsPerPage()) {
                return rejected(rawPage, Issue.PHYSICAL_ROW_BOUND);
            }
            for (final JsonNode row : root) {
                if (!row.isObject()) {
                    return rejected(rawPage, Issue.RECORD_OBJECT_REQUIRED);
                }
                final var names = row.fieldNames();
                while (names.hasNext()) {
                    if (!schema.fields().containsKey(names.next())) {
                        return rejected(rawPage, Issue.UNDECLARED_FIELD);
                    }
                }
                for (final var field : schema.fields().values()) {
                    final JsonNode value = row.get(field.path());
                    if (value == null) {
                        if (field.required()) {
                            return rejected(rawPage, Issue.MISSING_FIELD);
                        }
                        continue;
                    }
                    if (value.isNull()) {
                        if (!field.wireTypes().contains(ObservedExpansionSchemaCatalog.Wire.NULL)) {
                            return rejected(rawPage, Issue.NULL_NOT_OBSERVED);
                        }
                        continue;
                    }
                    if (field.unknownNonNull()) {
                        return rejected(rawPage, Issue.NON_NULL_TYPE_UNOBSERVED);
                    }
                    final var wire = wire(value);
                    if (wire == null || !field.wireTypes().contains(wire)) {
                        return rejected(rawPage, Issue.WIRE_TYPE);
                    }
                    if (value.isArray()) {
                        for (final JsonNode item : value) {
                            final var itemWire = wire(item);
                            if (itemWire == null || !field.arrayItemTypes().contains(itemWire)) {
                                return rejected(rawPage, Issue.ARRAY_ITEM_TYPE);
                            }
                        }
                    }
                }
            }
            return new Inspection(this, rawPage, schema.selection(), null, root.size());
        }

        private Inspection rejected(final ObservedExpansionRawPage raw, final Issue issue) {
            return new Inspection(this, raw, schema.selection(), issue, null);
        }
    }

    /**
     * Not returned by any public factory: created only by BoundSchema after consuming real proof.
     */
    static final class EmissionProof {
        private final String schemaRelease;
        private final String schemaSha256;
        private final ObservedExpansionPageProjection core;
        private final ObservedExpansionPageProjector.ProjectionProof projectorProof;
        private final String envelope;
        private final int pages;
        private final long rows;
        private final long issueRows;
        private final long rawBytes;
        private final long coreBytes;
        private final long envelopeBytes;

        private EmissionProof(
                final BoundSchema bound,
                final ObservedExpansionPageProjection core,
                final ObservedExpansionPageProjector.ProjectionProof projectorProof,
                final String envelope,
                final int pages,
                final long rows,
                final long issueRows,
                final long rawBytes,
                final long coreBytes,
                final long envelopeBytes) {
            this.schemaRelease = bound.schemaRelease();
            this.schemaSha256 = bound.schemaResourceSha256().toUpperCase(Locale.ROOT);
            this.core = core;
            this.projectorProof = projectorProof;
            this.envelope = envelope;
            this.pages = pages;
            this.rows = rows;
            this.issueRows = issueRows;
            this.rawBytes = rawBytes;
            this.coreBytes = coreBytes;
            this.envelopeBytes = envelopeBytes;
        }

        String schemaRelease() {
            return schemaRelease;
        }

        String schemaSha256() {
            return schemaSha256;
        }

        ObservedExpansionPageProjection core() {
            return core;
        }

        ObservedExpansionPageProjector.ProjectionProof projectorProof() {
            return projectorProof;
        }

        String envelope() {
            return envelope;
        }

        int pages() {
            return pages;
        }

        long rows() {
            return rows;
        }

        long issueRows() {
            return issueRows;
        }

        long rawBytes() {
            return rawBytes;
        }

        long coreBytes() {
            return coreBytes;
        }

        long envelopeBytes() {
            return envelopeBytes;
        }
    }

    /** A successful data check tied to its original immutable bytes; it is not a runtime permit. */
    public static final class CheckedPage {
        private final ObservedExpansionRawPage rawPage;
        private final ObservedExpansionSchemaCatalog.Selection selection;

        private CheckedPage(
                final ObservedExpansionRawPage rawPage,
                final ObservedExpansionSchemaCatalog.Selection selection) {
            this.rawPage = rawPage;
            this.selection = selection;
        }

        public ObservedExpansionRawPage rawPage() {
            return rawPage;
        }

        public String schemaRelease() {
            return selection.schemaRelease();
        }

        public String schemaResourceSha256() {
            return selection.schemaResourceSha256();
        }

        @Override
        public String toString() {
            return "ObservedExpansionSchemaCheckedPage[template="
                    + selection.templateId()
                    + ",scope=WIRE_ONLY]";
        }
    }

    /** Rejection retains raw bytes and unknown physical count, without exposing source values. */
    public static final class Inspection {
        private final BoundSchema owner;
        private final ObservedExpansionRawPage rawPage;
        private final ObservedExpansionSchemaCatalog.Selection selection;
        private final Issue issue;
        private final Integer physicalRows;

        private Inspection(
                final BoundSchema owner,
                final ObservedExpansionRawPage rawPage,
                final ObservedExpansionSchemaCatalog.Selection selection,
                final Issue issue,
                final Integer physicalRows) {
            this.owner = owner;
            this.rawPage = rawPage;
            this.selection = selection;
            this.issue = issue;
            this.physicalRows = physicalRows;
        }

        public ObservedExpansionRawPage rawPage() {
            return rawPage;
        }

        public boolean schemaMatched() {
            return issue == null;
        }

        public Issue issue() {
            return issue;
        }

        public String schemaRelease() {
            return selection.schemaRelease();
        }

        public String schemaResourceSha256() {
            return selection.schemaResourceSha256();
        }

        public String validationScope() {
            return "OBSERVED_WIRE_MATCH_ONLY";
        }

        public OptionalInt physicalRows() {
            return physicalRows == null ? OptionalInt.empty() : OptionalInt.of(physicalRows);
        }

        public Optional<CheckedPage> checkedPage() {
            return issue == null
                    ? Optional.of(new CheckedPage(rawPage, selection))
                    : Optional.empty();
        }

        @Override
        public String toString() {
            return "ObservedExpansionSchemaInspection[template="
                    + selection.templateId()
                    + ",matched="
                    + schemaMatched()
                    + ",issue="
                    + issue
                    + "]";
        }
    }

    private static ObservedExpansionSchemaCatalog.Wire wire(final JsonNode value) {
        if (value.isNull()) {
            return ObservedExpansionSchemaCatalog.Wire.NULL;
        }
        if (value.isTextual()) {
            return ObservedExpansionSchemaCatalog.Wire.STRING;
        }
        if (value.isBoolean()) {
            return ObservedExpansionSchemaCatalog.Wire.BOOLEAN;
        }
        if (value.isIntegralNumber()) {
            return ObservedExpansionSchemaCatalog.Wire.INTEGER;
        }
        if (value.isNumber()) {
            return ObservedExpansionSchemaCatalog.Wire.NUMBER;
        }
        if (value.isArray()) {
            return ObservedExpansionSchemaCatalog.Wire.ARRAY;
        }
        return null;
    }
}
