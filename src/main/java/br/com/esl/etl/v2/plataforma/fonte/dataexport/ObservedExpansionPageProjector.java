package br.com.esl.etl.v2.plataforma.fonte.dataexport;

import br.com.esl.etl.v2.modulos.contasapagar.aplicacao.ContaPagarDataExportMapper;
import br.com.esl.etl.v2.modulos.contasapagar.domain.ContaPagarObservation;
import br.com.esl.etl.v2.modulos.faturasporcliente.aplicacao.FaturaClienteDataExportMapper;
import br.com.esl.etl.v2.modulos.faturasporcliente.domain.FaturaClienteObservation;
import br.com.esl.etl.v2.modulos.inventario.aplicacao.InventarioDataExportMapper;
import br.com.esl.etl.v2.modulos.inventario.domain.InventarioObservation;
import br.com.esl.etl.v2.modulos.sinistros.aplicacao.SinistroDataExportMapper;
import br.com.esl.etl.v2.modulos.sinistros.domain.SinistroObservation;
import br.com.esl.etl.v2.plataforma.expansao.ExpansionCaptured;
import br.com.esl.etl.v2.plataforma.expansao.ExpansionValue;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Stateful pure-memory projector. It performs no observed-schema or runtime admission. */
public final class ObservedExpansionPageProjector {
    public enum State {
        OPEN,
        LOCAL_TERMINAL,
        PARTIAL_LIMIT,
        STRUCTURE_ISSUE,
        TOTAL_LIMIT
    }

    private final ObservedExpansionCapturePlan plan;
    private int nextPage = 1;
    private long nextOccurrence = 1;
    private long rawBytes;
    private long projectionBytes;
    private long issueRows;
    private State state = State.OPEN;
    private ProjectionProof lastIssuedProof;

    public ObservedExpansionPageProjector(final ObservedExpansionCapturePlan plan) {
        this.plan = Objects.requireNonNull(plan);
    }

    /** Returns all-or-unknown physical count; never a truncated partial mapper page. */
    public synchronized ObservedExpansionPageProjection projectNext(
            final ObservedExpansionRawPage raw) {
        return projectNextWithProof(raw).projection();
    }

    /**
     * The only producer of proof: an actual complete step of this execution, not an existing DTO.
     */
    public synchronized ProvenProjection projectNextWithProof(final ObservedExpansionRawPage raw) {
        Objects.requireNonNull(raw);
        if (state != State.OPEN) {
            throw new IllegalStateException("OBS_CAPTURE_PROJECTOR_CLOSED");
        }
        if (!plan.equals(raw.request().plan()) || raw.request().page() != nextPage) {
            throw new IllegalArgumentException("OBS_CAPTURE_PAGE_SEQUENCE");
        }
        if (rawBytes + raw.byteLength() > plan.limits().maximumRawBytes()) {
            state = State.TOTAL_LIMIT;
            throw new IllegalStateException("OBS_CAPTURE_RAW_TOTAL_BOUND");
        }
        final Progress before = progress();
        Draft draft = map(raw);
        String json;
        try {
            json = ObservedExpansionProjectionJson.serialize(plan, draft.records(), draft.issue());
        } catch (final ObservedExpansionProjectionJson.LimitExceeded failure) {
            draft = issue(ObservedExpansionPageProjection.Issue.PROJECTION_BYTES_EXCEEDED);
            json = ObservedExpansionProjectionJson.serialize(plan, draft.records(), draft.issue());
        }
        if (projectionBytes + 2L * json.length() > plan.limits().maximumProjectionTotalBytes()) {
            state = State.TOTAL_LIMIT;
            throw new IllegalStateException("OBS_CAPTURE_PROJECTION_TOTAL_BOUND");
        }
        final var status =
                draft.issue() == null
                        ? ObservedExpansionPageProjection.Status.MAPPED
                        : ObservedExpansionPageProjection.Status.STRUCTURE_ISSUE;
        final var page =
                new ObservedExpansionPageProjection(
                        raw, status, draft.issue(), draft.records(), json);
        rawBytes += raw.byteLength();
        projectionBytes += page.projectionByteLength();
        issueRows += page.fieldIssueRows();
        nextPage++;
        if (status == ObservedExpansionPageProjection.Status.STRUCTURE_ISSUE) {
            state = State.STRUCTURE_ISSUE;
        } else {
            nextOccurrence += page.records().size();
            if (page.records().isEmpty()) {
                state = State.LOCAL_TERMINAL;
            } else if (nextPage > plan.limits().maximumPages()) {
                state = State.PARTIAL_LIMIT;
            }
        }
        final Progress after = progress();
        final var proof =
                new ProjectionProof(this, plan, raw, page, lastIssuedProof, before, after);
        lastIssuedProof = proof;
        return new ProvenProjection(page, proof);
    }

    /** Execution-local counters, not a provider data snapshot or runtime authority. */
    public static final class Progress {
        private final int nextPage;
        private final long nextOccurrence;
        private final long rawBytes;
        private final long projectionBytes;
        private final long issueRows;
        private final State state;

        private Progress(
                final int nextPage,
                final long nextOccurrence,
                final long rawBytes,
                final long projectionBytes,
                final long issueRows,
                final State state) {
            this.nextPage = nextPage;
            this.nextOccurrence = nextOccurrence;
            this.rawBytes = rawBytes;
            this.projectionBytes = projectionBytes;
            this.issueRows = issueRows;
            this.state = state;
        }

        public int nextPage() {
            return nextPage;
        }

        public long nextOccurrence() {
            return nextOccurrence;
        }

        public long rawBytes() {
            return rawBytes;
        }

        public long projectionBytes() {
            return projectionBytes;
        }

        public long issueRows() {
            return issueRows;
        }

        public State state() {
            return state;
        }

        private boolean sameAs(final Progress other) {
            return other != null
                    && nextPage == other.nextPage
                    && nextOccurrence == other.nextOccurrence
                    && rawBytes == other.rawBytes
                    && projectionBytes == other.projectionBytes
                    && issueRows == other.issueRows
                    && state == other.state;
        }
    }

    public static final class ProvenProjection {
        private final ObservedExpansionPageProjection projection;
        private final ProjectionProof proof;

        private ProvenProjection(
                final ObservedExpansionPageProjection projection, final ProjectionProof proof) {
            this.projection = projection;
            this.proof = proof;
        }

        public ObservedExpansionPageProjection projection() {
            return projection;
        }

        public ProjectionProof proof() {
            return proof;
        }
    }

    /** Private construction only at the actual projector completion site above. */
    public static final class ProjectionProof {
        private final ObservedExpansionPageProjector owner;
        private final ObservedExpansionCapturePlan plan;
        private final ObservedExpansionRawPage raw;
        private final ObservedExpansionPageProjection result;
        private final ProjectionProof previous;
        private final Progress before;
        private final Progress after;
        private boolean consumed;

        private ProjectionProof(
                final ObservedExpansionPageProjector owner,
                final ObservedExpansionCapturePlan plan,
                final ObservedExpansionRawPage raw,
                final ObservedExpansionPageProjection result,
                final ProjectionProof previous,
                final Progress before,
                final Progress after) {
            this.owner = owner;
            this.plan = plan;
            this.raw = raw;
            this.result = result;
            this.previous = previous;
            this.before = before;
            this.after = after;
        }

        public String protocolVersion() {
            return "observed-expansion-projector-proof-v1";
        }

        public Progress before() {
            return before;
        }

        public Progress after() {
            return after;
        }

        public synchronized boolean consumed() {
            return consumed;
        }

        synchronized void verify(
                final ObservedExpansionPageProjector expectedOwner,
                final ObservedExpansionCapturePlan expectedPlan,
                final ObservedExpansionRawPage expectedRaw,
                final ObservedExpansionPageProjection expectedResult,
                final ProjectionProof expectedPrevious) {
            if (consumed
                    || owner != expectedOwner
                    || plan != expectedPlan
                    || raw != expectedRaw
                    || result != expectedResult
                    || result.rawPage() != raw
                    || previous != expectedPrevious
                    || !plan.equals(raw.request().plan())) {
                throw new IllegalArgumentException("OBS_CAPTURE_GENUINE_PROJECTOR_PROOF_REQUIRED");
            }
            if (previous == null) {
                if (before.nextPage != 1
                        || before.nextOccurrence != 1
                        || before.rawBytes != 0
                        || before.projectionBytes != 0
                        || before.issueRows != 0
                        || before.state != State.OPEN) {
                    throw new IllegalArgumentException("OBS_CAPTURE_PROOF_INITIAL_PROGRESS");
                }
            } else if (previous.owner != owner || !before.sameAs(previous.after)) {
                throw new IllegalArgumentException("OBS_CAPTURE_PROOF_CHAIN_PROGRESS");
            }
            final long mapped =
                    result.status() == ObservedExpansionPageProjection.Status.MAPPED
                            ? result.records().size()
                            : 0;
            final State expectedState =
                    result.status() == ObservedExpansionPageProjection.Status.STRUCTURE_ISSUE
                            ? State.STRUCTURE_ISSUE
                            : mapped == 0
                                    ? State.LOCAL_TERMINAL
                                    : after.nextPage > plan.limits().maximumPages()
                                            ? State.PARTIAL_LIMIT
                                            : State.OPEN;
            if (before.state != State.OPEN
                    || raw.request().page() != before.nextPage
                    || after.nextPage != before.nextPage + 1
                    || after.nextOccurrence != before.nextOccurrence + mapped
                    || after.rawBytes != before.rawBytes + raw.byteLength()
                    || after.projectionBytes
                            != before.projectionBytes + result.projectionByteLength()
                    || after.issueRows != before.issueRows + result.fieldIssueRows()
                    || after.rawBytes > plan.limits().maximumRawBytes()
                    || after.projectionBytes > plan.limits().maximumProjectionTotalBytes()
                    || after.nextOccurrence > plan.limits().maximumRowsPerExecution() + 1L
                    || after.state != expectedState) {
                throw new IllegalArgumentException("OBS_CAPTURE_PROOF_ALL_ACCUMULATORS");
            }
        }

        synchronized void consume(
                final ObservedExpansionPageProjector expectedOwner,
                final ObservedExpansionCapturePlan expectedPlan,
                final ObservedExpansionRawPage expectedRaw,
                final ObservedExpansionPageProjection expectedResult,
                final ProjectionProof expectedPrevious) {
            verify(expectedOwner, expectedPlan, expectedRaw, expectedResult, expectedPrevious);
            consumed = true;
        }
    }

    private Progress progress() {
        return new Progress(nextPage, nextOccurrence, rawBytes, projectionBytes, issueRows, state);
    }

    private Draft map(final ObservedExpansionRawPage raw) {
        final String text;
        try {
            text =
                    StandardCharsets.UTF_8
                            .newDecoder()
                            .onMalformedInput(CodingErrorAction.REPORT)
                            .onUnmappableCharacter(CodingErrorAction.REPORT)
                            .decode(ByteBuffer.wrap(raw.body()))
                            .toString();
        } catch (final CharacterCodingException failure) {
            return issue(ObservedExpansionPageProjection.Issue.INVALID_UTF8);
        }
        final JsonNode root;
        try {
            root = DataExportStrictJsonParser.readTree(text, plan.limits().maximumJsonNodes());
        } catch (final JsonProcessingException failure) {
            return issue(ObservedExpansionPageProjection.Issue.INVALID_JSON);
        }
        final JsonNode rows;
        if (plan.envelope() == ObservedExpansionCapturePlan.Envelope.ROOT_ARRAY) {
            if (root == null || !root.isArray()) {
                return issue(ObservedExpansionPageProjection.Issue.ENVELOPE_SHAPE);
            }
            rows = root;
        } else {
            if (root == null
                    || !root.isObject()
                    || root.size() != 1
                    || !root.has("data")
                    || !root.get("data").isArray()) {
                return issue(ObservedExpansionPageProjection.Issue.ENVELOPE_SHAPE);
            }
            rows = root.get("data");
        }
        if (rows.size() > plan.limits().maximumPhysicalRowsPerPage()
                || nextOccurrence - 1 + rows.size() > plan.limits().maximumRowsPerExecution()) {
            return issue(ObservedExpansionPageProjection.Issue.PHYSICAL_ROW_BOUND);
        }
        final List<ObservedExpansionProjectedRecord> records = new ArrayList<>(rows.size());
        int ordinal = 0;
        for (final JsonNode row : rows) {
            ordinal++;
            if (!row.isObject() || row.has("error") || row.has("errors")) {
                return issue(ObservedExpansionPageProjection.Issue.RECORD_SHAPE);
            }
            final JsonNode envelope = JsonNodeFactory.instance.objectNode().set("data", row);
            final int mapperOrdinal = (ordinal - 1) % 100 + 1;
            final long occurrence = nextOccurrence + ordinal - 1;
            try {
                records.add(project(ordinal, occurrence, mapperOrdinal, envelope));
            } catch (final IllegalArgumentException failure) {
                return issue(ObservedExpansionPageProjection.Issue.MAPPER_REJECTED);
            }
        }
        return new Draft(List.copyOf(records), null);
    }

    private ObservedExpansionProjectedRecord project(
            final int ordinal,
            final long occurrence,
            final int mapperOrdinal,
            final JsonNode envelope) {
        return switch (plan.template()) {
            case CAP -> {
                final var value = new ContaPagarDataExportMapper().map(mapperOrdinal, envelope);
                yield new ObservedExpansionProjectedRecord(
                        ordinal, new ExpansionCaptured<>(occurrence, value, null), fields(value));
            }
            case FAT -> {
                final var value = new FaturaClienteDataExportMapper().map(mapperOrdinal, envelope);
                yield new ObservedExpansionProjectedRecord(
                        ordinal, new ExpansionCaptured<>(occurrence, value, null), fields(value));
            }
            case INV -> {
                final var value = new InventarioDataExportMapper().map(mapperOrdinal, envelope);
                yield new ObservedExpansionProjectedRecord(
                        ordinal, new ExpansionCaptured<>(occurrence, value, null), fields(value));
            }
            case SIN -> {
                final var value = new SinistroDataExportMapper().map(mapperOrdinal, envelope);
                yield new ObservedExpansionProjectedRecord(
                        ordinal, new ExpansionCaptured<>(occurrence, value, null), fields(value));
            }
        };
    }

    private record Draft(
            List<ObservedExpansionProjectedRecord> records,
            ObservedExpansionPageProjection.Issue issue) {}

    private static Draft issue(final ObservedExpansionPageProjection.Issue issue) {
        return new Draft(List.of(), issue);
    }

    private static ObservedExpansionProjectedField field(
            final String path,
            final ObservedExpansionProjectedField.Kind kind,
            final ExpansionValue<?> value) {
        return new ObservedExpansionProjectedField(path, kind, value);
    }

    private static List<ObservedExpansionProjectedField> fields(final ContaPagarObservation value) {
        return List.of(
                field(
                        "ant_aln_name",
                        ObservedExpansionProjectedField.Kind.TEXT,
                        value.antAlnName()),
                field(
                        "ant_ces_acr_name",
                        ObservedExpansionProjectedField.Kind.TEXT,
                        value.antCesAcrName()),
                field(
                        "ant_ces_value",
                        ObservedExpansionProjectedField.Kind.DECIMAL,
                        value.antCesValue()),
                field(
                        "ant_crn_psn_nickname",
                        ObservedExpansionProjectedField.Kind.TEXT,
                        value.antCrnPsnNickname()),
                field(
                        "ant_ils_atn_liquidation_date",
                        ObservedExpansionProjectedField.Kind.DATE,
                        value.antIlsAtnLiquidationDate()),
                field(
                        "ant_ils_atn_reconciled",
                        ObservedExpansionProjectedField.Kind.BOOLEAN,
                        value.antIlsAtnReconciled()),
                field(
                        "ant_ils_atn_transaction_date",
                        ObservedExpansionProjectedField.Kind.DATE,
                        value.antIlsAtnTransactionDate()),
                field(
                        "ant_ils_comments",
                        ObservedExpansionProjectedField.Kind.TEXT,
                        value.antIlsComments()),
                field(
                        "ant_ils_expense_description",
                        ObservedExpansionProjectedField.Kind.TEXT,
                        value.antIlsExpenseDescription()),
                field(
                        "ant_ils_pas_ant_classification",
                        ObservedExpansionProjectedField.Kind.TEXT,
                        value.antIlsPasAntClassification()),
                field(
                        "ant_ils_pas_ant_name",
                        ObservedExpansionProjectedField.Kind.TEXT,
                        value.antIlsPasAntName()),
                field(
                        "ant_ils_pas_value",
                        ObservedExpansionProjectedField.Kind.DECIMAL,
                        value.antIlsPasValue()),
                field(
                        "ant_ils_sequence_code",
                        ObservedExpansionProjectedField.Kind.INTEGER,
                        value.antIlsSequenceCode()),
                field(
                        "ant_rir_name",
                        ObservedExpansionProjectedField.Kind.TEXT,
                        value.antRirName()),
                field(
                        "ant_uer_name",
                        ObservedExpansionProjectedField.Kind.TEXT,
                        value.antUerName()),
                field("comments", ObservedExpansionProjectedField.Kind.TEXT, value.comments()),
                field(
                        "competence_month",
                        ObservedExpansionProjectedField.Kind.INTEGER,
                        value.competenceMonth()),
                field(
                        "competence_year",
                        ObservedExpansionProjectedField.Kind.INTEGER,
                        value.competenceYear()),
                field(
                        "created_at",
                        ObservedExpansionProjectedField.Kind.INSTANT,
                        value.createdAt()),
                field(
                        "discount_value",
                        ObservedExpansionProjectedField.Kind.DECIMAL,
                        value.discountValue()),
                field("document", ObservedExpansionProjectedField.Kind.TEXT, value.document()),
                field(
                        "interest_value",
                        ObservedExpansionProjectedField.Kind.DECIMAL,
                        value.interestValue()),
                field("issue_date", ObservedExpansionProjectedField.Kind.DATE, value.issueDate()),
                field("paid", ObservedExpansionProjectedField.Kind.BOOLEAN, value.paid()),
                field(
                        "paid_value",
                        ObservedExpansionProjectedField.Kind.DECIMAL,
                        value.paidValue()),
                field("type", ObservedExpansionProjectedField.Kind.TEXT, value.type()),
                field("value", ObservedExpansionProjectedField.Kind.DECIMAL, value.value()),
                field(
                        "value_to_pay",
                        ObservedExpansionProjectedField.Kind.DECIMAL,
                        value.valueToPay()));
    }

    private static List<ObservedExpansionProjectedField> fields(
            final FaturaClienteObservation value) {
        return List.of(
                field("comments", ObservedExpansionProjectedField.Kind.TEXT, value.comments()),
                field(
                        "corporation_sequence_number",
                        ObservedExpansionProjectedField.Kind.INTEGER,
                        value.corporationSequenceNumber()),
                field("courtesy", ObservedExpansionProjectedField.Kind.BOOLEAN, value.courtesy()),
                field(
                        "emission_type",
                        ObservedExpansionProjectedField.Kind.TEXT,
                        value.emissionType()),
                field(
                        "finished_at",
                        ObservedExpansionProjectedField.Kind.INSTANT,
                        value.finishedAt()),
                field(
                        "fit_ant_ant_name",
                        ObservedExpansionProjectedField.Kind.TEXT,
                        value.fitAntAntName()),
                field(
                        "fit_ant_discount_value",
                        ObservedExpansionProjectedField.Kind.DECIMAL,
                        value.fitAntDiscountValue()),
                field(
                        "fit_ant_document",
                        ObservedExpansionProjectedField.Kind.TEXT,
                        value.fitAntDocument()),
                field(
                        "fit_ant_ils_atn_transaction_date",
                        ObservedExpansionProjectedField.Kind.DATE,
                        value.fitAntIlsAtnTransactionDate()),
                field(
                        "fit_ant_ils_due_date",
                        ObservedExpansionProjectedField.Kind.DATE,
                        value.fitAntIlsDueDate()),
                field(
                        "fit_ant_ils_original_due_date",
                        ObservedExpansionProjectedField.Kind.DATE,
                        value.fitAntIlsOriginalDueDate()),
                field(
                        "fit_ant_interest_value",
                        ObservedExpansionProjectedField.Kind.DECIMAL,
                        value.fitAntInterestValue()),
                field(
                        "fit_ant_issue_date",
                        ObservedExpansionProjectedField.Kind.DATE,
                        value.fitAntIssueDate()),
                field(
                        "fit_ant_tat_account_number",
                        ObservedExpansionProjectedField.Kind.TEXT,
                        value.fitAntTatAccountNumber()),
                field(
                        "fit_ant_tat_agency_number",
                        ObservedExpansionProjectedField.Kind.TEXT,
                        value.fitAntTatAgencyNumber()),
                field(
                        "fit_ant_tat_bnk_name",
                        ObservedExpansionProjectedField.Kind.TEXT,
                        value.fitAntTatBnkName()),
                field(
                        "fit_ant_tat_bro_description",
                        ObservedExpansionProjectedField.Kind.TEXT,
                        value.fitAntTatBroDescription()),
                field(
                        "fit_ant_tat_custom_instruction",
                        ObservedExpansionProjectedField.Kind.TEXT,
                        value.fitAntTatCustomInstruction()),
                field(
                        "fit_ant_value",
                        ObservedExpansionProjectedField.Kind.DECIMAL,
                        value.fitAntValue()),
                field(
                        "fit_crn_psn_nickname",
                        ObservedExpansionProjectedField.Kind.TEXT,
                        value.fitCrnPsnNickname()),
                field(
                        "fit_d_t_created_at",
                        ObservedExpansionProjectedField.Kind.INSTANT,
                        value.fitDTCreatedAt()),
                field(
                        "fit_diy_sae_name",
                        ObservedExpansionProjectedField.Kind.TEXT,
                        value.fitDiySaeName()),
                field(
                        "fit_dyn_drt_nickname",
                        ObservedExpansionProjectedField.Kind.TEXT,
                        value.fitDynDrtNickname()),
                field(
                        "fit_fhe_cte_issued_at",
                        ObservedExpansionProjectedField.Kind.INSTANT,
                        value.fitFheCteIssuedAt()),
                field(
                        "fit_fhe_cte_key",
                        ObservedExpansionProjectedField.Kind.TEXT,
                        value.fitFheCteKey()),
                field(
                        "fit_fhe_cte_number",
                        ObservedExpansionProjectedField.Kind.INTEGER,
                        value.fitFheCteNumber()),
                field(
                        "fit_fhe_cte_status",
                        ObservedExpansionProjectedField.Kind.TEXT,
                        value.fitFheCteStatus()),
                field(
                        "fit_fhe_cte_status_result",
                        ObservedExpansionProjectedField.Kind.TEXT,
                        value.fitFheCteStatusResult()),
                field(
                        "fit_fsn_name",
                        ObservedExpansionProjectedField.Kind.TEXT,
                        value.fitFsnName()),
                field(
                        "fit_fte_foe_ore_description",
                        ObservedExpansionProjectedField.Kind.TEXT,
                        value.fitFteFoeOreDescription()),
                field(
                        "fit_fte_has_delivery_receipt",
                        ObservedExpansionProjectedField.Kind.BOOLEAN,
                        value.fitFteHasDeliveryReceipt()),
                field(
                        "fit_fte_invoices_order_number",
                        ObservedExpansionProjectedField.Kind.STRINGS,
                        value.fitFteInvoicesOrderNumber()),
                field(
                        "fit_nse_number",
                        ObservedExpansionProjectedField.Kind.INTEGER,
                        value.fitNseNumber()),
                field(
                        "fit_pyr_cor_billing_cycle",
                        ObservedExpansionProjectedField.Kind.TEXT,
                        value.fitPyrCorBillingCycle()),
                field(
                        "fit_pyr_cor_billing_due_in_days",
                        ObservedExpansionProjectedField.Kind.INTEGER,
                        value.fitPyrCorBillingDueInDays()),
                field(
                        "fit_pyr_document",
                        ObservedExpansionProjectedField.Kind.TEXT,
                        value.fitPyrDocument()),
                field(
                        "fit_pyr_name",
                        ObservedExpansionProjectedField.Kind.TEXT,
                        value.fitPyrName()),
                field(
                        "fit_rpt_document",
                        ObservedExpansionProjectedField.Kind.TEXT,
                        value.fitRptDocument()),
                field(
                        "fit_rpt_name",
                        ObservedExpansionProjectedField.Kind.TEXT,
                        value.fitRptName()),
                field(
                        "fit_sdr_document",
                        ObservedExpansionProjectedField.Kind.TEXT,
                        value.fitSdrDocument()),
                field(
                        "fit_sdr_name",
                        ObservedExpansionProjectedField.Kind.TEXT,
                        value.fitSdrName()),
                field(
                        "fit_sps_slr_psn_name",
                        ObservedExpansionProjectedField.Kind.TEXT,
                        value.fitSpsSlrPsnName()),
                field("id", ObservedExpansionProjectedField.Kind.INTEGER, value.id()),
                field(
                        "invoices_mapping",
                        ObservedExpansionProjectedField.Kind.STRINGS,
                        value.invoicesMapping()),
                field(
                        "nfse_number",
                        ObservedExpansionProjectedField.Kind.IDENTIFIER,
                        value.nfseNumber()),
                field(
                        "payment_type",
                        ObservedExpansionProjectedField.Kind.TEXT,
                        value.paymentType()),
                field(
                        "reference_number",
                        ObservedExpansionProjectedField.Kind.TEXT,
                        value.referenceNumber()),
                field(
                        "service_at",
                        ObservedExpansionProjectedField.Kind.INSTANT,
                        value.serviceAt()),
                field(
                        "service_type",
                        ObservedExpansionProjectedField.Kind.TEXT,
                        value.serviceType()),
                field("status", ObservedExpansionProjectedField.Kind.TEXT, value.status()),
                field(
                        "third_party_ctes_value",
                        ObservedExpansionProjectedField.Kind.DECIMAL,
                        value.thirdPartyCtesValue()),
                field("total", ObservedExpansionProjectedField.Kind.DECIMAL, value.total()),
                field("type", ObservedExpansionProjectedField.Kind.TEXT, value.type()));
    }

    private static List<ObservedExpansionProjectedField> fields(final InventarioObservation value) {
        return List.of(
                field(
                        "cnr_c_s_fit_corporation_sequence_number",
                        ObservedExpansionProjectedField.Kind.INTEGER,
                        value.cnrCSFitCorporationSequenceNumber()),
                field(
                        "cnr_c_s_fit_dpn_delivery_prediction_at",
                        ObservedExpansionProjectedField.Kind.INSTANT,
                        value.cnrCSFitDpnDeliveryPredictionAt()),
                field(
                        "cnr_c_s_fit_dpn_performance_finished_at",
                        ObservedExpansionProjectedField.Kind.INSTANT,
                        value.cnrCSFitDpnPerformanceFinishedAt()),
                field(
                        "cnr_c_s_fit_dyn_drt_nickname",
                        ObservedExpansionProjectedField.Kind.TEXT,
                        value.cnrCSFitDynDrtNickname()),
                field(
                        "cnr_c_s_fit_dyn_name",
                        ObservedExpansionProjectedField.Kind.TEXT,
                        value.cnrCSFitDynName()),
                field(
                        "cnr_c_s_fit_fte_lce_occurrence_at",
                        ObservedExpansionProjectedField.Kind.INSTANT,
                        value.cnrCSFitFteLceOccurrenceAt()),
                field(
                        "cnr_c_s_fit_fte_lce_ore_description",
                        ObservedExpansionProjectedField.Kind.TEXT,
                        value.cnrCSFitFteLceOreDescription()),
                field(
                        "cnr_c_s_fit_invoices_mapping",
                        ObservedExpansionProjectedField.Kind.STRINGS,
                        value.cnrCSFitInvoicesMapping()),
                field(
                        "cnr_c_s_fit_invoices_value",
                        ObservedExpansionProjectedField.Kind.DECIMAL,
                        value.cnrCSFitInvoicesValue()),
                field(
                        "cnr_c_s_fit_invoices_volumes",
                        ObservedExpansionProjectedField.Kind.INTEGER,
                        value.cnrCSFitInvoicesVolumes()),
                field(
                        "cnr_c_s_fit_pyr_nickname",
                        ObservedExpansionProjectedField.Kind.TEXT,
                        value.cnrCSFitPyrNickname()),
                field(
                        "cnr_c_s_fit_real_weight",
                        ObservedExpansionProjectedField.Kind.DECIMAL,
                        value.cnrCSFitRealWeight()),
                field(
                        "cnr_c_s_fit_rpt_ads_cty_name",
                        ObservedExpansionProjectedField.Kind.TEXT,
                        value.cnrCSFitRptAdsCtyName()),
                field(
                        "cnr_c_s_fit_rpt_nickname",
                        ObservedExpansionProjectedField.Kind.TEXT,
                        value.cnrCSFitRptNickname()),
                field(
                        "cnr_c_s_fit_sdr_ads_cty_name",
                        ObservedExpansionProjectedField.Kind.TEXT,
                        value.cnrCSFitSdrAdsCtyName()),
                field(
                        "cnr_c_s_fit_sdr_nickname",
                        ObservedExpansionProjectedField.Kind.TEXT,
                        value.cnrCSFitSdrNickname()),
                field(
                        "cnr_c_s_fit_taxed_weight",
                        ObservedExpansionProjectedField.Kind.DECIMAL,
                        value.cnrCSFitTaxedWeight()),
                field(
                        "cnr_c_s_fit_total_cubic_volume",
                        ObservedExpansionProjectedField.Kind.DECIMAL,
                        value.cnrCSFitTotalCubicVolume()),
                field(
                        "cnr_c_s_read_volumes",
                        ObservedExpansionProjectedField.Kind.INTEGER,
                        value.cnrCSReadVolumes()),
                field(
                        "cnr_cis_eoe_psn_name",
                        ObservedExpansionProjectedField.Kind.TEXT,
                        value.cnrCisEoePsnName()),
                field(
                        "cnr_crn_psn_nickname",
                        ObservedExpansionProjectedField.Kind.TEXT,
                        value.cnrCrnPsnNickname()),
                field(
                        "finished_at",
                        ObservedExpansionProjectedField.Kind.INSTANT,
                        value.finishedAt()),
                field(
                        "sequence_code",
                        ObservedExpansionProjectedField.Kind.INTEGER,
                        value.sequenceCode()),
                field(
                        "started_at",
                        ObservedExpansionProjectedField.Kind.INSTANT,
                        value.startedAt()),
                field("status", ObservedExpansionProjectedField.Kind.TEXT, value.status()),
                field("type", ObservedExpansionProjectedField.Kind.TEXT, value.type()));
    }

    private static List<ObservedExpansionProjectedField> fields(final SinistroObservation value) {
        return List.of(
                field("bo_number", ObservedExpansionProjectedField.Kind.TEXT, value.boNumber()),
                field(
                        "brokers_case_number",
                        ObservedExpansionProjectedField.Kind.TEXT,
                        value.brokersCaseNumber()),
                field(
                        "customer_communication_contact_name",
                        ObservedExpansionProjectedField.Kind.TEXT,
                        value.customerCommunicationContactName()),
                field(
                        "customer_communication_date",
                        ObservedExpansionProjectedField.Kind.DATE,
                        value.customerCommunicationDate()),
                field(
                        "customer_communication_time",
                        ObservedExpansionProjectedField.Kind.TEXT,
                        value.customerCommunicationTime()),
                field(
                        "customer_credit_entries_subtotal",
                        ObservedExpansionProjectedField.Kind.DECIMAL,
                        value.customerCreditEntriesSubtotal()),
                field(
                        "customer_debits_subtotal",
                        ObservedExpansionProjectedField.Kind.DECIMAL,
                        value.customerDebitsSubtotal()),
                field(
                        "expected_solution_date",
                        ObservedExpansionProjectedField.Kind.DATE,
                        value.expectedSolutionDate()),
                field(
                        "finished_at_date",
                        ObservedExpansionProjectedField.Kind.DATE,
                        value.finishedAtDate()),
                field(
                        "finished_at_time",
                        ObservedExpansionProjectedField.Kind.TIME,
                        value.finishedAtTime()),
                field(
                        "finished_commentary",
                        ObservedExpansionProjectedField.Kind.TEXT,
                        value.finishedCommentary()),
                field(
                        "icm_crn_psn_nickname",
                        ObservedExpansionProjectedField.Kind.TEXT,
                        value.icmCrnPsnNickname()),
                field(
                        "icm_dvr_iil_name",
                        ObservedExpansionProjectedField.Kind.TEXT,
                        value.icmDvrIilName()),
                field(
                        "icm_fer_name",
                        ObservedExpansionProjectedField.Kind.TEXT,
                        value.icmFerName()),
                field(
                        "icm_fis_fit_corporation_sequence_number",
                        ObservedExpansionProjectedField.Kind.INTEGER,
                        value.icmFisFitCorporationSequenceNumber()),
                field(
                        "icm_fis_fit_pyr_nickname",
                        ObservedExpansionProjectedField.Kind.TEXT,
                        value.icmFisFitPyrNickname()),
                field(
                        "icm_fis_ioe_number",
                        ObservedExpansionProjectedField.Kind.IDENTIFIER,
                        value.icmFisIoeNumber()),
                field(
                        "icm_ttt_dealing_type",
                        ObservedExpansionProjectedField.Kind.TEXT,
                        value.icmTttDealingType()),
                field(
                        "icm_ttt_ore_code",
                        ObservedExpansionProjectedField.Kind.INTEGER,
                        value.icmTttOreCode()),
                field(
                        "icm_ttt_ore_description",
                        ObservedExpansionProjectedField.Kind.TEXT,
                        value.icmTttOreDescription()),
                field(
                        "icm_ttt_solution_type",
                        ObservedExpansionProjectedField.Kind.TEXT,
                        value.icmTttSolutionType()),
                field(
                        "icm_ttt_treatment_at",
                        ObservedExpansionProjectedField.Kind.INSTANT,
                        value.icmTttTreatmentAt()),
                field(
                        "icm_vie_license_plate",
                        ObservedExpansionProjectedField.Kind.TEXT,
                        value.icmVieLicensePlate()),
                field("informed_by", ObservedExpansionProjectedField.Kind.TEXT, value.informedBy()),
                field(
                        "insurance_claim_commentary",
                        ObservedExpansionProjectedField.Kind.TEXT,
                        value.insuranceClaimCommentary()),
                field(
                        "insurance_claim_location",
                        ObservedExpansionProjectedField.Kind.TEXT,
                        value.insuranceClaimLocation()),
                field(
                        "insurance_claim_total",
                        ObservedExpansionProjectedField.Kind.DECIMAL,
                        value.insuranceClaimTotal()),
                field(
                        "insurer_credits_subtotal",
                        ObservedExpansionProjectedField.Kind.DECIMAL,
                        value.insurerCreditsSubtotal()),
                field(
                        "internal_description",
                        ObservedExpansionProjectedField.Kind.TEXT,
                        value.internalDescription()),
                field(
                        "invoices_count",
                        ObservedExpansionProjectedField.Kind.INTEGER,
                        value.invoicesCount()),
                field(
                        "invoices_value",
                        ObservedExpansionProjectedField.Kind.DECIMAL,
                        value.invoicesValue()),
                field(
                        "invoices_volumes",
                        ObservedExpansionProjectedField.Kind.INTEGER,
                        value.invoicesVolumes()),
                field(
                        "invoices_weight",
                        ObservedExpansionProjectedField.Kind.DECIMAL,
                        value.invoicesWeight()),
                field(
                        "list_of_claimed_products",
                        ObservedExpansionProjectedField.Kind.TEXT,
                        value.listOfClaimedProducts()),
                field(
                        "occurrence_at_date",
                        ObservedExpansionProjectedField.Kind.DATE,
                        value.occurrenceAtDate()),
                field(
                        "occurrence_at_time",
                        ObservedExpansionProjectedField.Kind.TIME,
                        value.occurrenceAtTime()),
                field(
                        "opening_at_date",
                        ObservedExpansionProjectedField.Kind.DATE,
                        value.openingAtDate()),
                field(
                        "policy_number",
                        ObservedExpansionProjectedField.Kind.TEXT,
                        value.policyNumber()),
                field("rcfdc", ObservedExpansionProjectedField.Kind.STRINGS, value.rcfdc()),
                field("rctac", ObservedExpansionProjectedField.Kind.STRINGS, value.rctac()),
                field("rctrc", ObservedExpansionProjectedField.Kind.STRINGS, value.rctrc()),
                field(
                        "responsible_credits_subtotal",
                        ObservedExpansionProjectedField.Kind.DECIMAL,
                        value.responsibleCreditsSubtotal()),
                field(
                        "responsible_debit_entries_subtotal",
                        ObservedExpansionProjectedField.Kind.DECIMAL,
                        value.responsibleDebitEntriesSubtotal()),
                field(
                        "sequence_code",
                        ObservedExpansionProjectedField.Kind.INTEGER,
                        value.sequenceCode()));
    }

    public synchronized State state() {
        return state;
    }

    public synchronized int nextPage() {
        return nextPage;
    }

    public synchronized long nextOccurrence() {
        return nextOccurrence;
    }

    public synchronized long rawBytes() {
        return rawBytes;
    }

    public synchronized long projectionBytes() {
        return projectionBytes;
    }
}
