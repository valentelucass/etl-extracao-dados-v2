package br.com.esl.etl.v2.bootstrap;

import br.com.esl.etl.v2.plataforma.persistencia.devlocal.DevLocalLedger;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken;
import br.com.esl.etl.v2.plataforma.resiliencia.ResilienceCancelledException;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.cfg.JsonNodeFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.util.Set;
import java.util.function.LongSupplier;

/** Explicit, bounded synthetic execution. It never constructs a production adapter. */
public final class DevLocalEngine {
    static final ObjectMapper JSON =
            JsonMapper.builder(
                            JsonFactory.builder()
                                    .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                                    .build())
                    .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
                    .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                    .disable(JsonNodeFeature.STRIP_TRAILING_BIGDECIMAL_ZEROES)
                    .build();
    static final String SCOPE = "SYNTHETIC_LOCAL_ONLY";
    private static final Set<String> OPTIONS =
            Set.of(
                    "synthetic",
                    "scale",
                    "batchSize",
                    "maximumInFlight",
                    "maximumBytes",
                    "deadlineSeconds",
                    "mode",
                    "referenceReleaseId",
                    "previousState",
                    "sweep",
                    "relationBindings");

    private DevLocalEngine() {}

    /** Computes a local document, without writing a workspace or granting SQL promotion. */
    public static JsonNode execute(final JsonNode characterizedDataset, final JsonNode options) {
        return execute(characterizedDataset, options, CancellationToken.none());
    }

    public static JsonNode sampleContract() {
        return DevLocalEngineSample.contract();
    }

    public static JsonNode execute(
            final JsonNode characterizedDataset,
            final JsonNode options,
            final CancellationToken cancellation) {
        try {
            return compute(characterizedDataset, options, cancellation);
        } catch (final ResilienceCancelledException cancelled) {
            final ObjectNode result = JSON.createObjectNode();
            result.put("synthetic", true);
            result.put("identityScope", SCOPE);
            result.put("mode", options.path("mode").asText("BOOTSTRAP"));
            result.put("status", "CANCELLED");
            result.put("reasonCode", "LOCAL_COOPERATIVE_CANCELLED");
            result.put("published", false);
            return result;
        }
    }

    private static JsonNode compute(
            final JsonNode characterizedDataset,
            final JsonNode options,
            final CancellationToken cancellation) {
        final Configuration config = configuration(options);
        validateDataset(characterizedDataset, config.maximumBytes());
        cancellation.throwIfCancellationRequested();
        final long began = System.nanoTime();
        final ObjectNode result = JSON.createObjectNode();
        result.put("synthetic", true);
        result.put("identityScope", SCOPE);
        result.put("mode", config.mode());
        if ("CANCEL".equals(config.mode()) || "ERROR".equals(config.mode())) {
            result.put("status", "CANCEL".equals(config.mode()) ? "CANCELLED" : "FAILED");
            result.put(
                    "reasonCode",
                    "CANCEL".equals(config.mode())
                            ? "LOCAL_CANCEL_REQUESTED"
                            : "LOCAL_INJECTED_ERROR");
            result.put("published", false);
            return result;
        }
        final JsonNode previous = options.path("previousState");
        final ObjectNode state =
                DevLocalEngineAnalytics.state(
                        characterizedDataset,
                        previous,
                        config.scale(),
                        config.batchSize(),
                        cancellation);
        final var entities = result.putArray("entities");
        for (final JsonNode entity : state.path("entities")) {
            final ObjectNode summary = entity.deepCopy();
            summary.remove("rows");
            summary.put("retainedRoots", entity.path("rows").size());
            entities.add(summary);
        }
        if (!options.has("relationBindings")
                && characterizedDataset.path("relationBindings").isArray()
                && !characterizedDataset.path("relationBindings").isEmpty()
                && characterizedDataset.path("relationBindingObservations").isMissingNode()) {
            throw new IllegalArgumentException("DEV_LOCAL_RELATION_OBSERVATIONS_REQUIRED");
        }
        result.set(
                "relations",
                DevLocalEngineAnalytics.relations(
                        state,
                        options.has("relationBindings")
                                ? options.path("relationBindings")
                                : characterizedDataset.has("relationBindings")
                                        ? characterizedDataset.path("relationBindings")
                                        : DevLocalEngineSample.forDataset(characterizedDataset),
                        options.has("relationBindings")
                                ? JSON.getNodeFactory().missingNode()
                                : characterizedDataset.path("relationBindingObservations")));
        result.set(
                "dimensions", DevLocalEngineAnalytics.dimensions(state, config.referenceRelease()));
        result.set("facts", DevLocalEngineAnalytics.facts(state, config.referenceRelease()));
        result.set("contracts", DevLocalEngineAnalytics.contracts());
        result.set("sweepPreview", DevLocalEngineSweep.preview(characterizedDataset));
        result.set(
                "absence",
                DevLocalEngineSweep.apply(
                        state, previous, options.path("sweep"), characterizedDataset));
        // SQL-only materializers retain metadata and guards; no replacement rows are manufactured.
        result.set("state", state);
        cancellation.throwIfCancellationRequested();
        if (System.nanoTime() - began > config.deadlineSeconds() * 1_000_000_000L) {
            throw new IllegalStateException("DEV_LOCAL_DEADLINE");
        }
        result.put("status", "COMPLETED");
        result.put("reasonCode", "LOCAL_COMPUTATION_COMPLETED");
        result.put("published", false);
        final ObjectNode metrics = result.putObject("metrics");
        metrics.put("scale", config.scale());
        metrics.put("batchSize", config.batchSize());
        metrics.put("maximumInFlight", 1);
        metrics.put("processedRows", state.path("processedRows").asInt());
        metrics.put("maximumBatchRows", state.path("maximumBatchRows").asInt());
        int retained = 0;
        for (final JsonNode entity : state.path("entities")) {
            retained += entity.path("rows").size();
        }
        metrics.put("retainedRows", retained);
        metrics.put("retainedStateBytes", bytes(state).length);
        metrics.put("retainedStateBoundPerEntity", 256);
        metrics.put("sqlCalls", 0);
        metrics.put("httpCalls", 0);
        metrics.put("physicalEvidence", false);
        metrics.put("elapsedNanos", System.nanoTime() - began);
        if (bytes(result).length > config.maximumBytes()) {
            throw new IllegalArgumentException("DEV_LOCAL_RESULT_BOUND");
        }
        return result;
    }

    /** Uses Banco's immutable protocol; same input replays readback instead of recomputing. */
    public static JsonNode execute(
            final DevLocalLedger ledger,
            final String executionId,
            final JsonNode characterizedDataset,
            final JsonNode options)
            throws IOException {
        return execute(ledger, executionId, characterizedDataset, options, System::nanoTime);
    }

    static JsonNode execute(
            final DevLocalLedger ledger,
            final String executionId,
            final JsonNode characterizedDataset,
            final JsonNode options,
            final LongSupplier monotonic)
            throws IOException {
        final Configuration config = configuration(options);
        final long began = monotonic.getAsLong();
        validateDataset(characterizedDataset, options.path("maximumBytes").asInt(1_048_576));
        final ObjectNode input = JSON.createObjectNode();
        input.set("dataset", characterizedDataset);
        input.set("options", options);
        final String fingerprint = DevLocalLedger.sha256(bytes(input));
        deadline(monotonic, began, config.deadlineSeconds());
        final DevLocalLedger.Run run = ledger.start(executionId, fingerprint);
        deadline(monotonic, began, config.deadlineSeconds());
        if (run.state() != DevLocalLedger.State.STARTED) {
            final JsonNode replay = JSON.readTree(ledger.readOutput(executionId, "result.json"));
            deadline(monotonic, began, config.deadlineSeconds());
            if (!SCOPE.equals(replay.path("identityScope").asText())
                    || !run.state().name().equals(replay.path("status").asText())
                    || !run.reasonCode().equals(replay.path("reasonCode").asText())) {
                throw new IOException("DEV_LOCAL_REPLAY_TERMINAL_MISMATCH");
            }
            return replay;
        }
        final JsonNode result;
        final DevLocalLedger.State terminal;
        try {
            ledger.event(executionId, "ENGINE_BEGIN", "SYNTHETIC_LOCAL_ONLY");
            deadline(monotonic, began, config.deadlineSeconds());
            result = execute(characterizedDataset, options);
            deadline(monotonic, began, config.deadlineSeconds());
            ledger.output(executionId, "result.json", bytes(result));
            deadline(monotonic, began, config.deadlineSeconds());
            terminal = DevLocalLedger.State.valueOf(result.path("status").asText());
            ledger.finish(executionId, terminal, result.path("reasonCode").asText());
        } catch (final RuntimeException failure) {
            ledger.event(executionId, "ENGINE_FAILED", "SYNTHETIC_LOCAL_ONLY");
            ledger.finish(executionId, DevLocalLedger.State.FAILED, "LOCAL_ENGINE_FAILED");
            throw failure;
        }
        // After the terminal write is known, a late caller never compensates its durable outcome.
        deadline(monotonic, began, config.deadlineSeconds());
        final DevLocalLedger.Run readback = ledger.readback(executionId);
        deadline(monotonic, began, config.deadlineSeconds());
        if (readback.state() != terminal || readback.outputs().size() != 1) {
            throw new IOException("DEV_LOCAL_TERMINAL_READBACK");
        }
        final JsonNode persisted = JSON.readTree(ledger.readOutput(executionId, "result.json"));
        deadline(monotonic, began, config.deadlineSeconds());
        return persisted;
    }

    private static void deadline(final LongSupplier monotonic, final long began, final int seconds)
            throws IOException {
        if (monotonic.getAsLong() - began >= seconds * 1_000_000_000L) {
            throw new IOException("DEV_LOCAL_CALLER_DEADLINE_EXPIRED_NO_RETRY");
        }
    }

    static byte[] bytes(final JsonNode value) {
        try {
            return JSON.writeValueAsBytes(value);
        } catch (final IOException impossible) {
            throw new IllegalStateException("DEV_LOCAL_JSON", impossible);
        }
    }

    private static Configuration configuration(final JsonNode options) {
        if (options == null
                || !options.isObject()
                || !options.path("synthetic").isBoolean()
                || !options.path("synthetic").booleanValue()) {
            throw new IllegalArgumentException("DEV_LOCAL_OPT_IN_REQUIRED");
        }
        options.fieldNames()
                .forEachRemaining(
                        name -> {
                            if (!OPTIONS.contains(name)) {
                                throw new IllegalArgumentException("DEV_LOCAL_OPTION_UNKNOWN");
                            }
                        });
        final int scale = integer(options, "scale", 2, 2, 16);
        if (!Set.of(2, 4, 8, 16).contains(scale)) {
            throw new IllegalArgumentException("DEV_LOCAL_SCALE");
        }
        final int batch = integer(options, "batchSize", 4, 1, 32);
        integer(options, "maximumInFlight", 1, 1, 1);
        final int maximum = integer(options, "maximumBytes", 1_048_576, 1024, 4_194_304);
        final int deadline = integer(options, "deadlineSeconds", 30, 1, 60);
        final long reference = integer(options, "referenceReleaseId", 1, 1, 100_000);
        if (options.has("mode") && !options.path("mode").isTextual()) {
            throw new IllegalArgumentException("DEV_LOCAL_MODE");
        }
        final String mode = options.path("mode").asText("BOOTSTRAP");
        if (!Set.of("BOOTSTRAP", "INCREMENTAL", "RECOMPOSE", "CANCEL", "ERROR").contains(mode)) {
            throw new IllegalArgumentException("DEV_LOCAL_MODE");
        }
        if (("INCREMENTAL".equals(mode) || "RECOMPOSE".equals(mode))
                && !options.path("previousState").isObject()) {
            throw new IllegalArgumentException("DEV_LOCAL_PREVIOUS_REQUIRED");
        }
        return new Configuration(scale, batch, maximum, deadline, reference, mode);
    }

    private static int integer(
            final JsonNode options,
            final String name,
            final int fallback,
            final int minimum,
            final int maximum) {
        if (!options.has(name)) {
            return fallback;
        }
        final JsonNode value = options.path(name);
        if (!value.isIntegralNumber()
                || !value.canConvertToInt()
                || value.intValue() < minimum
                || value.intValue() > maximum) {
            throw new IllegalArgumentException("DEV_LOCAL_OPTION_BOUND");
        }
        return value.intValue();
    }

    private static void validateDataset(final JsonNode dataset, final int maximum) {
        if (dataset == null
                || !dataset.isObject()
                || !dataset.path("synthetic").isBoolean()
                || !dataset.path("synthetic").booleanValue()
                || !"DEV_LOCAL_V1".equals(dataset.path("datasetVersion").asText())
                || !dataset.path("entities").isArray()
                || dataset.path("entities").size() != 11
                || bytes(dataset).length > Math.min(524_288, maximum)) {
            throw new IllegalArgumentException("DEV_LOCAL_DATASET_CONTRACT");
        }
    }

    private record Configuration(
            int scale,
            int batchSize,
            int maximumBytes,
            int deadlineSeconds,
            long referenceRelease,
            String mode) {}
}
