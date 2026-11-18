package br.com.esl.etl.v2.plataforma.reconciliacao.exportacao;

import static br.com.esl.etl.v2.plataforma.reconciliacao.exportacao.ExportValidationSupport.require;
import static br.com.esl.etl.v2.plataforma.reconciliacao.exportacao.ExportValidationSupport.text;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/** Finite status queries are separate admitted operations, never HTTP retries or repeated POSTs. */
public final class ExportFileLifecycle {
    public enum State {
        ENQUEUED,
        GENERATING,
        GENERATED,
        ERROR,
        STOPPED,
        UNKNOWN
    }

    public record Control(
            String attemptId,
            String fileId,
            String postReceiptSha256,
            String latestReceiptSha256,
            String expectedFiltersSha256,
            String lastOperationId,
            String lastNonce,
            State state,
            int pollsUsed,
            int maximumPolls,
            Instant budgetIssuedAt,
            Instant deadline,
            Instant lastResponseAt) {
        public Control(
                final String attemptId,
                final String fileId,
                final String postReceiptSha256,
                final String latestReceiptSha256,
                final String expectedFiltersSha256,
                final String lastOperationId,
                final String lastNonce,
                final State state,
                final int pollsUsed,
                final int maximumPolls,
                final Instant deadline,
                final Instant lastResponseAt) {
            this(
                    attemptId,
                    fileId,
                    postReceiptSha256,
                    latestReceiptSha256,
                    expectedFiltersSha256,
                    lastOperationId,
                    lastNonce,
                    state,
                    pollsUsed,
                    maximumPolls,
                    lastResponseAt,
                    deadline,
                    lastResponseAt);
        }

        public Control {
            UUID.fromString(attemptId);
            UUID.fromString(lastOperationId);
            UUID.fromString(lastNonce);
            require(fileId != null && fileId.matches("[1-9][0-9]{0,17}"), "EXPORT_FILE_ID");
            require(
                    postReceiptSha256 != null
                            && postReceiptSha256.matches("[a-f0-9]{64}")
                            && latestReceiptSha256 != null
                            && latestReceiptSha256.matches("[a-f0-9]{64}"),
                    "EXPORT_RECEIPT_PIN");
            require(
                    expectedFiltersSha256 != null && expectedFiltersSha256.matches("[a-f0-9]{64}"),
                    "EXPORT_FROZEN_FILTER_PIN");
            require(
                    state != null
                            && maximumPolls >= 0
                            && maximumPolls <= 64
                            && pollsUsed >= 0
                            && pollsUsed <= maximumPolls
                            && deadline != null
                            && lastResponseAt != null
                            && budgetIssuedAt != null
                            && !deadline.isBefore(lastResponseAt)
                            && !deadline.isBefore(budgetIssuedAt)
                            && Duration.between(budgetIssuedAt, deadline)
                                            .compareTo(Duration.ofSeconds(240))
                                    <= 0,
                    "EXPORT_FILE_FINITE_CONTROL");
        }
    }

    public record Admission(
            String attemptId,
            String fileId,
            String postReceiptSha256,
            String previousReceiptSha256,
            String operationId,
            String nonce) {}

    public record PollPlan(
            String path,
            String operationId,
            String nonce,
            int ordinal,
            Duration timeout,
            String previousReceiptSha256) {}

    /** Metadata only. A generated response does not prove download or content parity. */
    public record Receipt(
            String attemptId,
            String fileId,
            String postReceiptSha256,
            String previousReceiptSha256,
            String operationId,
            String nonce,
            int ordinal,
            int httpStatus,
            String responseSha256,
            Instant receivedAt,
            State state) {
        public String sha256() throws java.io.IOException {
            return ExportValidationSupport.sha256(
                    ExportValidationSupport.JSON.writeValueAsBytes(this));
        }
    }

    private ExportFileLifecycle() {}

    public static PollPlan plan(
            final Control control, final Admission admission, final Instant now) {
        require(
                control.state() == State.ENQUEUED || control.state() == State.GENERATING,
                "EXPORT_FILE_NOT_POLLABLE");
        require(control.pollsUsed() < control.maximumPolls(), "EXPORT_POLL_BUDGET_PENDING");
        require(now.isBefore(control.deadline()), "EXPORT_POLL_DEADLINE_PENDING");
        require(
                !now.isBefore(control.lastResponseAt().plusSeconds(3)),
                "EXPORT_THREE_SECONDS_AFTER_RESPONSE");
        require(
                admission != null
                        && control.attemptId().equals(admission.attemptId())
                        && control.fileId().equals(admission.fileId())
                        && control.postReceiptSha256().equals(admission.postReceiptSha256())
                        && control.latestReceiptSha256().equals(admission.previousReceiptSha256()),
                "EXPORT_POLL_ADMISSION_BINDING");
        UUID.fromString(admission.operationId());
        UUID.fromString(admission.nonce());
        require(
                !control.lastOperationId().equals(admission.operationId())
                        && !control.lastNonce().equals(admission.nonce()),
                "EXPORT_POLL_NEW_OPERATION_NONCE");
        final var remaining = Duration.between(now, control.deadline());
        return new PollPlan(
                "/api/analytics/report_files/" + control.fileId(),
                admission.operationId(),
                admission.nonce(),
                control.pollsUsed() + 1,
                remaining.compareTo(Duration.ofSeconds(30)) < 0
                        ? remaining
                        : Duration.ofSeconds(30),
                control.latestReceiptSha256());
    }

    public static Receipt observe(
            final Control control,
            final PollPlan plan,
            final IndependentExportCycle.Reply reply,
            final Instant receivedAt)
            throws java.io.IOException {
        require(
                plan.ordinal() == control.pollsUsed() + 1
                        && plan.previousReceiptSha256().equals(control.latestReceiptSha256())
                        && plan.path().equals("/api/analytics/report_files/" + control.fileId()),
                "EXPORT_POLL_PLAN_BINDING");
        State state = State.STOPPED;
        if (reply.status() >= 200 && reply.status() < 300) {
            final JsonNode response;
            try (var parser = ExportValidationSupport.JSON.createParser(reply.body())) {
                response = ExportValidationSupport.JSON.readTree(parser);
                require(
                        response != null && parser.nextToken() == null,
                        "EXPORT_POLL_RESPONSE_JSON");
            }
            require(
                    control.fileId().equals(response.path("id").asText())
                            && response.path("id").isIntegralNumber()
                            && response.path("report_id").asInt() == 6389,
                    "EXPORT_POLL_RESPONSE_ID_REPORT");
            require(
                    control.expectedFiltersSha256()
                            .equals(
                                    ExportValidationSupport.sha256(
                                            ExportValidationSupport.canonicalBytes(
                                                    IndependentExportRequest.canonicalNativeFilters(
                                                            response.required("filters"))))),
                    "EXPORT_POLL_FROZEN_FILTER_BINDING");
            state =
                    switch (text(response, "status")) {
                        case "enqueued" -> State.ENQUEUED;
                        case "generating" -> State.GENERATING;
                        case "generated" -> State.GENERATED;
                        case "error" -> State.ERROR;
                        default -> throw new IllegalArgumentException("EXPORT_POLL_STATE_UNKNOWN");
                    };
            require(
                    state != State.GENERATED
                            || response.path("download_url").isTextual()
                                    && !response.path("download_url").textValue().isBlank(),
                    "EXPORT_GENERATED_WITHOUT_DOWNLOAD");
        }
        require(
                !receivedAt.isAfter(control.deadline())
                        && !receivedAt.isBefore(control.lastResponseAt()),
                "EXPORT_POLL_RESPONSE_DEADLINE");
        return new Receipt(
                control.attemptId(),
                control.fileId(),
                control.postReceiptSha256(),
                control.latestReceiptSha256(),
                plan.operationId(),
                plan.nonce(),
                plan.ordinal(),
                reply.status(),
                ExportValidationSupport.sha256(reply.body()),
                receivedAt,
                state);
    }

    public static Control confirmPersisted(
            final Control previous, final Receipt receipt, final String persistedReceiptSha256)
            throws java.io.IOException {
        require(
                receipt.sha256().equals(persistedReceiptSha256)
                        && previous.latestReceiptSha256().equals(receipt.previousReceiptSha256())
                        && previous.attemptId().equals(receipt.attemptId())
                        && previous.fileId().equals(receipt.fileId())
                        && previous.postReceiptSha256().equals(receipt.postReceiptSha256())
                        && receipt.ordinal() == previous.pollsUsed() + 1,
                "EXPORT_POLL_EFFECT_NOT_CONFIRMED");
        return new Control(
                previous.attemptId(),
                previous.fileId(),
                previous.postReceiptSha256(),
                persistedReceiptSha256,
                previous.expectedFiltersSha256(),
                receipt.operationId(),
                receipt.nonce(),
                receipt.state(),
                receipt.ordinal(),
                previous.maximumPolls(),
                previous.budgetIssuedAt(),
                previous.deadline(),
                receipt.receivedAt());
    }

    /** Called only for an explicit newly consumed Root selection; never an automatic renewal. */
    public static Control regrant(
            final Control previous,
            final int additional,
            final Instant issuedAt,
            final Instant deadline) {
        require(
                (previous.state() == State.ENQUEUED || previous.state() == State.GENERATING)
                        && additional > 0
                        && additional <= 10
                        && previous.pollsUsed() + additional <= 64
                        && !issuedAt.isBefore(previous.lastResponseAt()),
                "EXPORT_EXPLICIT_CONTINUATION_GRANT");
        return new Control(
                previous.attemptId(),
                previous.fileId(),
                previous.postReceiptSha256(),
                previous.latestReceiptSha256(),
                previous.expectedFiltersSha256(),
                previous.lastOperationId(),
                previous.lastNonce(),
                previous.state(),
                previous.pollsUsed(),
                previous.pollsUsed() + additional,
                issuedAt,
                deadline,
                previous.lastResponseAt());
    }

    /** Main's offline planner uses the same state machine as the single-query source port. */
    public static ObjectNode statusPlan(final JsonNode input, final Instant now) {
        require(
                "INDEPENDENT_EXPORT_STATUS_REQUEST_V1".equals(text(input, "schema")),
                "EXPORT_STATUS_REQUEST_SCHEMA");
        final var c = input.required("control");
        final var a = input.required("admission");
        final var control =
                new Control(
                        text(c, "attemptId"),
                        text(c, "fileId"),
                        text(c, "postReceiptSha256"),
                        text(c, "latestReceiptSha256"),
                        text(c, "expectedFiltersSha256"),
                        text(c, "lastOperationId"),
                        text(c, "lastNonce"),
                        State.valueOf(text(c, "state")),
                        c.required("pollsUsed").intValue(),
                        c.required("maximumPolls").intValue(),
                        Instant.parse(text(c, "deadline")),
                        Instant.parse(text(c, "lastResponseAt")));
        final var admission =
                new Admission(
                        text(a, "attemptId"),
                        text(a, "fileId"),
                        text(a, "postReceiptSha256"),
                        text(a, "previousReceiptSha256"),
                        text(a, "operationId"),
                        text(a, "nonce"));
        final var result = ExportValidationSupport.JSON.createObjectNode();
        result.put("schema", "INDEPENDENT_EXPORT_STATUS_PLAN_V1");
        result.put("fileId", control.fileId());
        result.put("method", "GET");
        result.set("plan", ExportValidationSupport.JSON.valueToTree(plan(control, admission, now)));
        result.put("effectAdmissionVerified", false);
        result.put("HTTP", 0);
        result.put("mainResumeAllowed", false);
        return result;
    }
}
