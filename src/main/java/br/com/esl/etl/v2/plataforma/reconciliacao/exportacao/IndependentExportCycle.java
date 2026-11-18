package br.com.esl.etl.v2.plataforma.reconciliacao.exportacao;

import static br.com.esl.etl.v2.plataforma.reconciliacao.exportacao.ExportValidationSupport.require;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.function.LongSupplier;

/**
 * One API submission per explicitly owned cycle. The source owner supplies the already admitted
 * single-wire-attempt adapter and shared ESL serial channel. UI observation remains operator-owned.
 * No polling, recovery, retry, redirect, fallback or recapture loop is implemented here.
 */
public final class IndependentExportCycle {
    public interface Port {
        /** Must perform exactly one wire attempt, with redirects/retries disabled by its owner. */
        Reply submit(String relativePath, byte[] body, Duration timeout) throws Exception;
    }

    public record Reply(int status, byte[] body) {
        public Reply {
            require(
                    status >= 100 && status <= 599 && body != null && body.length <= 262144,
                    "EXPORT_RESPONSE_BOUND");
            body = body.clone();
        }

        @Override
        public byte[] body() {
            return body.clone();
        }
    }

    public record PostReceipt(
            String attemptId,
            String requestSha256,
            int status,
            String responseSha256,
            Instant receivedAt) {
        public String sha256() throws java.io.IOException {
            return ExportValidationSupport.sha256(
                    ExportValidationSupport.JSON.writeValueAsBytes(this));
        }
    }

    public interface PostJournal {
        /** Durable owner admission; reject an already reserved attempt, including after restart. */
        void reserve(String attemptId, String requestSha256) throws Exception;

        /**
         * Persist and read back this response fact before parsing or advancing the file workflow.
         */
        String appendAndConfirm(PostReceipt receipt) throws Exception;
    }

    public record Submission(
            String state,
            String attemptId,
            String fileId,
            String postReceiptSha256,
            String expectedFiltersSha256,
            Instant responseAt,
            boolean snapshotIdentityProven) {
        public ExportFileLifecycle.Control pendingControl(
                final int maximumPolls, final Instant deadline) {
            return new ExportFileLifecycle.Control(
                    attemptId,
                    fileId,
                    postReceiptSha256,
                    postReceiptSha256,
                    expectedFiltersSha256,
                    attemptId,
                    attemptId,
                    ExportFileLifecycle.State.ENQUEUED,
                    0,
                    maximumPolls,
                    deadline,
                    responseAt);
        }
    }

    @FunctionalInterface
    interface SingleAttempt {
        Reply invoke(Duration timeout) throws Exception;
    }

    /**
     * One shared instance must cover all ESL calls of the owning executor, including other routes.
     */
    public static final class SerialChannel {
        private final LongSupplier nanoTime;
        private long responseAt;
        private boolean responded;
        private boolean stopped;

        public SerialChannel(final LongSupplier nanoTime) {
            this.nanoTime = Objects.requireNonNull(nanoTime);
        }

        synchronized Reply exchange(final SingleAttempt attempt, final Duration timeout)
                throws Exception {
            require(!stopped, "EXPORT_CHANNEL_STOPPED");
            require(
                    !responded
                            || nanoTime.getAsLong() - responseAt >= Duration.ofSeconds(3).toNanos(),
                    "EXPORT_THREE_SECONDS_AFTER_RESPONSE");
            try {
                final long started = nanoTime.getAsLong();
                final Reply reply = attempt.invoke(timeout);
                require(
                        nanoTime.getAsLong() - started < timeout.toNanos(),
                        "EXPORT_RESPONSE_AFTER_DEADLINE");
                if (reply.status() == 429 || reply.status() < 200 || reply.status() >= 300) {
                    stopped = true;
                }
                return reply;
            } catch (final Exception unknown) {
                stopped = true; // Unknown wire outcome is never retried by this cycle/channel.
                throw unknown;
            } finally {
                responseAt = nanoTime.getAsLong();
                responded = true;
            }
        }
    }

    private final SerialChannel channel;
    private final Port port;
    private final Duration timeout;
    private final PostJournal journal;
    private boolean consumed;

    public IndependentExportCycle(
            final SerialChannel channel,
            final Port port,
            final PostJournal journal,
            final Duration timeout) {
        require(
                timeout != null
                        && !timeout.isZero()
                        && !timeout.isNegative()
                        && timeout.compareTo(Duration.ofSeconds(30)) <= 0,
                "EXPORT_FINITE_REQUEST_TIMEOUT");
        this.channel = Objects.requireNonNull(channel);
        this.port = Objects.requireNonNull(port);
        this.timeout = timeout;
        this.journal = Objects.requireNonNull(journal);
    }

    public synchronized Submission submit(final JsonNode request) throws Exception {
        require(!consumed, "EXPORT_CYCLE_ALREADY_CONSUMED");
        final byte[] body =
                ExportValidationSupport.JSON.writeValueAsBytes(
                        IndependentExportRequest.body(request));
        consumed = true; // Includes timeout, parse failure and pre-dispatch channel refusal.
        final String attempt = ExportValidationSupport.text(request, "attemptId");
        final String requestDigest =
                ExportValidationSupport.sha256(ExportValidationSupport.canonicalBytes(request));
        journal.reserve(attempt, requestDigest);
        final Reply reply =
                channel.exchange(
                        remaining ->
                                port.submit("/api/analytics/reports/6389/export", body, remaining),
                        timeout);
        final var receipt =
                new PostReceipt(
                        attempt,
                        requestDigest,
                        reply.status(),
                        ExportValidationSupport.sha256(reply.body()),
                        Instant.now());
        final String confirmed = journal.appendAndConfirm(receipt);
        require(receipt.sha256().equals(confirmed), "EXPORT_POST_EFFECT_NOT_CONFIRMED");
        require(reply.status() != 429, "EXPORT_STOP_429");
        require(reply.status() >= 200 && reply.status() < 300, "EXPORT_STOP_NON_2XX");
        final JsonNode response;
        try (var parser = ExportValidationSupport.JSON.createParser(reply.body())) {
            response = ExportValidationSupport.JSON.readTree(parser);
            require(response != null && parser.nextToken() == null, "EXPORT_RESPONSE_JSON");
        }
        require(
                response.path("id").isIntegralNumber()
                        && response.get("id").canConvertToLong()
                        && response.get("id").longValue() > 0
                        && response.path("report_id").asInt() == 6389,
                "EXPORT_QUEUE_ID_REPORT");
        final String id = response.get("id").asText();
        require(
                ("/api/analytics/report_files/" + id).equals(response.path("show_url").asText()),
                "EXPORT_QUEUE_ROUTE");
        require("enqueued".equals(response.path("status").asText()), "EXPORT_QUEUE_STATE");
        final var filters = response.required("filters");
        final String period = request.path("filters").path("freights[service_at]").textValue();
        require(
                period.equals(
                        IndependentExportRequest.canonicalPeriod(
                                filters.path("freights").path("service_at").asText())),
                "EXPORT_QUEUE_FILTER_PERIOD");
        verifyEmptyOtherFilters(filters, "");
        return new Submission(
                "PENDING_GENERATION_DOWNLOAD_AND_INDEPENDENT_UI",
                attempt,
                id,
                confirmed,
                ExportValidationSupport.sha256(
                        ExportValidationSupport.canonicalBytes(
                                IndependentExportRequest.canonicalNativeFilters(filters))),
                receipt.receivedAt(),
                false);
    }

    private static void verifyEmptyOtherFilters(final JsonNode node, final String path) {
        require(node.isObject(), "EXPORT_QUEUE_FILTER_OBJECT");
        node.fields()
                .forEachRemaining(
                        entry -> {
                            final String field =
                                    path.isEmpty() ? entry.getKey() : path + "." + entry.getKey();
                            if (!"freights.service_at".equals(field)) {
                                final var value = entry.getValue();
                                if (value.isObject()) {
                                    verifyEmptyOtherFilters(value, field);
                                } else {
                                    require(
                                            value.isNull()
                                                    || value.isTextual()
                                                            && value.textValue().isEmpty()
                                                    || value.isArray() && value.isEmpty(),
                                            "EXPORT_QUEUE_OTHER_FILTER");
                                }
                            }
                        });
    }
}
