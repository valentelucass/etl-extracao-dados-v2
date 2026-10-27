package br.com.esl.etl.v2.bootstrap;

import br.com.esl.etl.v2.plataforma.fonte.dataexport.BusinessDateRange;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.ObservedExpansionCaptureTemplate;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Metadata only. Neither this document nor its execution ID authorizes Source or SQL. */
record ObservedStagingRequest(
        UUID executionId,
        ObservedExpansionCaptureTemplate template,
        BusinessDateRange businessWindow) {
    private static final int MAXIMUM_BYTES = 16_384;
    private static final Set<String> KEYS =
            Set.of("requestVersion", "executionId", "template", "businessStart", "businessEnd");

    ObservedStagingRequest {
        java.util.Objects.requireNonNull(executionId);
        java.util.Objects.requireNonNull(template);
        java.util.Objects.requireNonNull(businessWindow);
    }

    static ObservedStagingRequest read(final Path file) {
        try (var input = Files.newInputStream(java.util.Objects.requireNonNull(file))) {
            return parse(input.readNBytes(MAXIMUM_BYTES + 1));
        } catch (final IOException | RuntimeException refused) {
            throw invalid(); // No path, caller value, message, cause or suppressed exception
            // escapes.
        }
    }

    static ObservedStagingRequest parse(final byte[] bytes) {
        if (bytes == null || bytes.length < 1 || bytes.length > MAXIMUM_BYTES) {
            throw invalid();
        }
        try {
            final JsonNode root =
                    JsonMapper.builder()
                            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                            .build()
                            .readTree(bytes);
            if (root == null || !root.isObject()) {
                throw invalid();
            }
            final var actual = new HashSet<String>();
            root.fieldNames().forEachRemaining(actual::add);
            if (!actual.equals(KEYS)) {
                throw invalid();
            }
            for (final String key : KEYS) {
                if (!root.get(key).isTextual()) {
                    throw invalid();
                }
            }
            if (!"observed-staging-trial-v1".equals(root.get("requestVersion").textValue())) {
                throw invalid();
            }
            final String id = root.get("executionId").textValue();
            final UUID execution = UUID.fromString(id);
            if (!id.equals(execution.toString())) {
                throw invalid();
            }
            final String start = root.get("businessStart").textValue();
            final String end = root.get("businessEnd").textValue();
            if (!start.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")
                    || !end.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) {
                throw invalid();
            }
            final LocalDate first = LocalDate.parse(start);
            final LocalDate last = LocalDate.parse(end);
            if (first.getYear() < 1 || last.getYear() < 1) {
                throw invalid(); // SQL DATE has no year zero; do not defer this to the writer.
            }
            return new ObservedStagingRequest(
                    execution,
                    ObservedExpansionCaptureTemplate.valueOf(root.get("template").textValue()),
                    new BusinessDateRange(first, last));
        } catch (final IOException | RuntimeException refused) {
            throw invalid();
        }
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("OBSERVED_STAGING_REQUEST_INVALID");
    }

    @Override
    public String toString() {
        return "ObservedStagingRequest[metadataOnly,authority=false]";
    }
}
