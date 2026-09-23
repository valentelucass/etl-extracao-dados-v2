package br.com.esl.etl.v2.contratos.mapping;

import br.com.esl.etl.v2.contratos.caracterizacao.CharacterizationReportSanitizer;
import br.com.esl.etl.v2.contratos.caracterizacao.StrictUtf8JsonLoader;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.CharacterizationParserAccess;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/** Bounded, local comparison of actual parser/mapper output with independent assertions. */
final class MapperCharacterization {
    static final int MAXIMUM_BYTES = 65_536;
    static final int MAXIMUM_ROWS = 1_000;
    static final int MAXIMUM_PAGES = 100;
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final StrictUtf8JsonLoader LOADER = new StrictUtf8JsonLoader();

    private MapperCharacterization() {}

    static Report compare(
            final MapperProjection.Entity entity,
            final int expectedPages,
            final PageSource source,
            final Expectations expectations) {
        Objects.requireNonNull(entity);
        Objects.requireNonNull(source);
        Objects.requireNonNull(expectations);
        final Report report = new Report(entity);
        if (expectedPages < 1 || expectedPages > MAXIMUM_PAGES) {
            return report.incomplete("PAGE_LIMIT");
        }
        for (int page = 1; page <= expectedPages; page++) {
            final JsonNode envelope;
            final String document;
            try (InputStream input = source.open(page)) {
                if (input == null) {
                    return report.incomplete("MISSING_PAGE");
                }
                final byte[] bytes = readBounded(input);
                // Q-FND-01's parser-injection constructor is package-private and frozen.
                // Its public bounded loader validates UTF-8/structure; the original bytes then
                // enter the production parser, retaining decimals and all wire lexemes.
                LOADER.load(
                        bytes,
                        MAXIMUM_BYTES,
                        new StrictUtf8JsonLoader.JsonStructureLimits(16, 256, 4_096));
                document = new String(bytes, StandardCharsets.UTF_8);
                envelope = CharacterizationParserAccess.parse(document);
            } catch (final LimitException error) {
                return report.incomplete("BYTE_LIMIT");
            } catch (final IOException error) {
                return report.incomplete("READ_FAILURE");
            } catch (final IllegalArgumentException error) {
                return report.incomplete("INVALID_JSON_OR_STRUCTURE_LIMIT");
            }
            if (!envelope.isObject() || !envelope.path("data").isArray()) {
                return report.incomplete("INVALID_ENVELOPE");
            }
            final JsonNode rows = envelope.get("data");
            if (rows.size() > MAXIMUM_ROWS - report.rows) {
                return report.incomplete("ROW_LIMIT");
            }
            final JsonNode expected = expectations.page(page);
            if (expected == null || !expected.isArray() || expected.size() != rows.size()) {
                return report.incomplete("EXPECTED_ROW_COUNT_MISMATCH");
            }
            final List<String> rawRows;
            try {
                rawRows = CharacterizationParserAccess.rowDocuments(document);
            } catch (final IOException error) {
                return report.incomplete("ROW_LEXEME_FAILURE");
            }
            if (rawRows.size() != rows.size()) {
                return report.incomplete("ROW_LEXEME_COUNT_MISMATCH");
            }
            report.pages++;
            for (int row = 0; row < rows.size(); row++) {
                final Map<String, String> observed;
                try {
                    // One-row microbatch: ordinal is never a source identity or tie breaker.
                    observed = MapperProjection.observe(entity, 1, rows.get(row), rawRows.get(row));
                } catch (final RuntimeException error) {
                    // Exception messages/causes may contain payload. Never expose them in a
                    // receipt.
                    return report.incomplete("MAPPER_FAILURE");
                }
                report.rows++;
                report.counts.merge("/quarantine:" + observed.get("/quarantine"), 1, Integer::sum);
                if ("NONE".equals(observed.get("/quarantine"))) {
                    report.valid++;
                } else {
                    report.quarantined++;
                }
                final JsonNode checks = expected.get(row);
                if (!checks.isObject()
                        || !checks.path("/quarantine").isTextual()
                        || checks.size() > 256) {
                    return report.incomplete("INVALID_EXPECTATIONS");
                }
                final var fields = checks.fields();
                while (fields.hasNext()) {
                    final var field = fields.next();
                    if (!observed.containsKey(field.getKey()) || !field.getValue().isTextual()) {
                        return report.incomplete("UNKNOWN_EXPECTATION_PATH");
                    }
                    report.comparisons++;
                    if (!Objects.equals(
                            observed.get(field.getKey()), field.getValue().textValue())) {
                        report.difference(field.getKey(), "VALUE_MISMATCH");
                    }
                }
                observed.forEach(
                        (path, value) -> {
                            if (path.endsWith("/presence") || path.endsWith("/wireType")) {
                                report.counts.merge(path + ":" + value, 1, Integer::sum);
                            }
                        });
            }
        }
        return report;
    }

    private static byte[] readBounded(final InputStream input) throws IOException {
        final ByteArrayOutputStream output = new ByteArrayOutputStream();
        final byte[] buffer = new byte[4_096];
        while (true) {
            final int read =
                    input.read(
                            buffer, 0, Math.min(buffer.length, MAXIMUM_BYTES - output.size() + 1));
            if (read < 0) {
                return output.toByteArray();
            }
            if (read == 0) {
                throw new IOException("Non-progressing local input");
            }
            if (read > MAXIMUM_BYTES - output.size()) {
                throw new LimitException();
            }
            output.write(buffer, 0, read);
        }
    }

    @FunctionalInterface
    interface PageSource {
        InputStream open(int page) throws IOException;
    }

    @FunctionalInterface
    interface Expectations {
        JsonNode page(int page);
    }

    private static final class LimitException extends IOException {
        private static final long serialVersionUID = 1L;
    }

    static final class Report {
        private final MapperProjection.Entity entity;
        private boolean complete = true;
        private int pages;
        private int rows;
        private int valid;
        private int quarantined;
        private int comparisons;
        private final Map<String, Integer> differences = new TreeMap<>();
        private final Map<String, Integer> counts = new TreeMap<>();

        private Report(final MapperProjection.Entity entity) {
            this.entity = entity;
        }

        private Report incomplete(final String reason) {
            complete = false;
            difference("/envelope", reason);
            return this;
        }

        private void difference(final String path, final String reason) {
            differences.merge(path + ":" + reason, 1, Integer::sum);
        }

        boolean matches() {
            return complete && differences.isEmpty();
        }

        ObjectNode sanitized() {
            final ObjectNode result = JSON.createObjectNode();
            result.put("entity", entity.name());
            result.put("evidence", "SYNTHETIC_LOCAL_PARSER_MAPPER");
            result.put("providerEvidence", "NOT_EXECUTED");
            result.put("completeLocalCase", complete);
            result.put("status", !complete ? "INCOMPLETE" : matches() ? "MATCH" : "DIVERGED");
            result.put("pages", pages);
            result.put("rows", rows);
            result.put("valid", valid);
            result.put("quarantined", quarantined);
            result.put("comparisons", comparisons);
            final var items = result.putArray("differences");
            differences.forEach(
                    (key, count) -> {
                        final var item = items.addObject();
                        final int separator = key.lastIndexOf(':');
                        item.put("path", key.substring(0, separator));
                        item.put("rule", entity.rule());
                        item.put("reason", key.substring(separator + 1));
                        item.put("count", count);
                    });
            final var observations = result.putArray("fieldCounts");
            counts.forEach(
                    (key, count) -> {
                        final var item = observations.addObject();
                        final int separator = key.lastIndexOf(':');
                        item.put("path", key.substring(0, separator));
                        item.put("state", key.substring(separator + 1));
                        item.put("count", count);
                    });
            CharacterizationReportSanitizer.requireSanitized(result);
            return result;
        }

        @Override
        public String toString() {
            return sanitized().toString();
        }
    }
}
