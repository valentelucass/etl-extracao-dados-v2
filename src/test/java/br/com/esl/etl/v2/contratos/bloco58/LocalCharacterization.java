package br.com.esl.etl.v2.contratos.bloco58;

import br.com.esl.etl.v2.contratos.caracterizacao.CharacterizationReportSanitizer;
import br.com.esl.etl.v2.contratos.caracterizacao.StrictUtf8JsonLoader;
import br.com.esl.etl.v2.plataforma.contrato.ContractObservationLimits;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/** Common test-only bounds and value-free comparison receipts; no protocol or domain policy. */
public final class LocalCharacterization {
    public static final int MAXIMUM_BYTES = 65_536;
    public static final int MAXIMUM_ROWS = 1_000;
    public static final int MAXIMUM_PAGES = 100;
    public static final ContractObservationLimits LIMITS =
            new ContractObservationLimits(16, 256, 4_096);
    private static final ObjectMapper JSON = new ObjectMapper();

    private LocalCharacterization() {}

    public enum Layer {
        NONE,
        INPUT,
        PARSER,
        CONTRACT,
        PAGE_LIMIT,
        TRAVERSAL,
        MAPPER,
        STAGING,
        CANCELLED
    }

    @FunctionalInterface
    public interface Pages {
        InputStream open(int page) throws IOException;
    }

    public static String read(final InputStream input) throws IOException {
        if (input == null) {
            throw new IOException("MISSING_PAGE");
        }
        final var output = new ByteArrayOutputStream();
        final byte[] buffer = new byte[8192];
        while (true) {
            final int count =
                    input.read(
                            buffer, 0, Math.min(buffer.length, MAXIMUM_BYTES - output.size() + 1));
            if (count < 0) {
                break;
            }
            if (count == 0) {
                throw new IOException("NON_PROGRESSING_INPUT");
            }
            if (count > MAXIMUM_BYTES - output.size()) {
                throw new IOException("BYTE_LIMIT");
            }
            output.write(buffer, 0, count);
        }
        final byte[] bytes = output.toByteArray();
        new StrictUtf8JsonLoader()
                .load(
                        bytes,
                        MAXIMUM_BYTES,
                        new StrictUtf8JsonLoader.JsonStructureLimits(16, 256, 4_096));
        return new String(bytes, StandardCharsets.UTF_8);
    }

    public static void put(
            final Map<String, String> values, final String path, final Object value) {
        values.put(path, value == null ? "NULL" : value.toString());
    }

    public static void field(
            final Map<String, String> values, final String path, final JsonNode value) {
        put(
                values,
                path + "/presence",
                value.isMissingNode() ? "ABSENT" : value.isNull() ? "NULL" : "VALUE");
        put(
                values,
                path + "/wireType",
                value.isMissingNode()
                        ? "ABSENT"
                        : value.isIntegralNumber()
                                ? "INTEGER"
                                : value.isFloatingPointNumber()
                                        ? "DECIMAL"
                                        : value.getNodeType().name());
    }

    public static final class Report {
        private Layer layer = Layer.INPUT;
        private Layer refusal = Layer.NONE;
        private String reason = "NONE";
        private int pages;
        private int mapped;
        private int staged;
        private int compared;
        private int quarantined;
        private boolean terminal;
        private final Map<String, Integer> differences = new TreeMap<>();
        private final Map<String, Integer> fieldCounts = new TreeMap<>();

        public void enter(final Layer value) {
            layer = Objects.requireNonNull(value);
        }

        public void page() {
            pages++;
        }

        public void mapped() {
            mapped++;
        }

        public void staged() {
            staged++;
        }

        public int mappedRows() {
            return mapped;
        }

        public int stagedRows() {
            return staged;
        }

        public int pagesRead() {
            return pages;
        }

        public Layer refusal() {
            return refusal;
        }

        public void terminal() {
            terminal = true;
        }

        public void failed(final RuntimeException failure) {
            if (failure
                    instanceof
                    br.com.esl.etl.v2.plataforma.resiliencia.ResilienceCancelledException) {
                refuse(Layer.CANCELLED, "CANCELLED");
            } else if (failure
                    instanceof br.com.esl.etl.v2.plataforma.contrato.ContractDriftException drift) {
                refuse(Layer.CONTRACT, drift.reason().name());
            } else if (failure
                    instanceof
                    br.com.esl.etl.v2.plataforma.fonte.graphql.GraphQlResponseException response) {
                refuse(Layer.PARSER, response.reason().name());
            } else if (failure
                    instanceof
                    br.com.esl.etl.v2.plataforma.fonte.graphql.GraphQlPaginationException
                                    pagination) {
                refuse(Layer.TRAVERSAL, pagination.reason().name());
            } else {
                refuse(layer, "LOCAL_FAILURE");
            }
        }

        public void refuse(final Layer value, final String code) {
            if (refusal == Layer.NONE) {
                refusal = value;
                reason = code.matches("[A-Z][A-Z0-9_]{0,80}") ? code : "REDACTED_FAILURE";
            }
        }

        public void compare(final JsonNode expected, final Map<String, String> actual) {
            if (expected == null
                    || !expected.isObject()
                    || expected.size() == 0
                    || expected.size() > 256) {
                difference("/expectations");
                return;
            }
            if (!"NONE".equals(actual.get("/quarantine"))) {
                quarantined++;
            }
            final var fields = expected.fields();
            while (fields.hasNext()) {
                final var field = fields.next();
                // Only projection-owned paths may reach the receipt; never arbitrary JSON keys.
                if (!actual.containsKey(field.getKey()) || !field.getValue().isTextual()) {
                    difference("/expectations");
                    continue;
                }
                compared++;
                if (!Objects.equals(actual.get(field.getKey()), field.getValue().textValue())) {
                    difference(field.getKey());
                }
            }
            actual.forEach(
                    (path, value) -> {
                        if (path.endsWith("/presence") || path.endsWith("/wireType")) {
                            fieldCounts.merge(path + ":" + value, 1, Integer::sum);
                        }
                    });
        }

        public void expect(
                final Layer expectedRefusal,
                final int expectedRows,
                final boolean expectedTerminal) {
            if (refusal != expectedRefusal) {
                difference("/flow/firstRefusal");
            }
            if (staged != expectedRows) {
                difference("/flow/stagedRows");
            }
            if (terminal != expectedTerminal) {
                difference("/flow/localTerminal");
            }
        }

        public void difference(final String path) {
            differences.merge(path, 1, Integer::sum);
        }

        public boolean matches() {
            return differences.isEmpty();
        }

        public ObjectNode sanitized() {
            final var node = JSON.createObjectNode();
            node.put("evidence", "SYNTHETIC_LOCAL_ONLY");
            node.put("providerEvidence", "NOT_EXECUTED");
            node.put("status", matches() ? "MATCH" : "DIVERGED");
            node.put("firstRefusal", refusal.name());
            node.put("reason", reason);
            node.put("pages", pages);
            node.put("mappedRows", mapped);
            node.put("stagedRows", staged);
            node.put("quarantined", quarantined);
            node.put("quarantineLayer", quarantined == 0 ? "NONE" : "MAPPER");
            node.put("comparisons", compared);
            node.put("localTerminal", terminal);
            node.put("snapshotProven", false);
            final var diff = node.putArray("differences");
            differences.forEach(
                    (path, count) -> {
                        final var item = diff.addObject();
                        item.put("path", path);
                        item.put("reason", "EXPECTATION_MISMATCH");
                        item.put("count", count);
                    });
            final var counts = node.putArray("fieldCounts");
            fieldCounts.forEach(
                    (key, count) -> {
                        final int split = key.lastIndexOf(':');
                        final var item = counts.addObject();
                        item.put("path", key.substring(0, split));
                        item.put("state", key.substring(split + 1));
                        item.put("count", count);
                    });
            CharacterizationReportSanitizer.requireSanitized(node);
            return node;
        }

        @Override
        public String toString() {
            return sanitized().toString();
        }
    }
}
