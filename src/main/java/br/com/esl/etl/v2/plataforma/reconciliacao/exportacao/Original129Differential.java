package br.com.esl.etl.v2.plataforma.reconciliacao.exportacao;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;

/** DRAFT candidate; Root must compile/run against a pinned Java port outside P. */
public final class Original129Differential {
    private Original129Differential() {}

    /** Adapter receives inputs only; expected Python results are never supplied. */
    public interface JavaPort {
        JsonNode evaluate(String operation, JsonNode exactTaggedArguments) throws Exception;
    }

    public static void main(final String[] args) throws Exception {
        if (args.length != 4) {
            throw new IllegalArgumentException(
                    "ORACLE_PATH_SHA256_BYTES_AND_JAVA_ADAPTER_CLASS_REQUIRED");
        }
        final var path = Path.of(args[0]);
        if (!path.isAbsolute() || Files.size(path) > 16777216) {
            throw new IllegalArgumentException("BOUNDED_ABSOLUTE_SYNTHETIC_ORACLE_REQUIRED");
        }
        final byte[] bytes = Files.readAllBytes(path);
        final var digest =
                java.util.HexFormat.of()
                        .formatHex(
                                java.security.MessageDigest.getInstance("SHA-256").digest(bytes));
        if (bytes.length != Long.parseLong(args[2]) || !digest.equalsIgnoreCase(args[1])) {
            throw new IllegalArgumentException("ROOT_REVIEW_ORACLE_PIN_REQUIRED");
        }
        final var mapper = new ObjectMapper();
        final var root = mapper.readTree(bytes);
        if (!"ROOT_COLETAS_ORIGINAL129_SYNTHETIC_ORACLE0721_V1".equals(root.path("schema").asText())
                || !root.path("syntheticOnly").asBoolean()
                || root.path("businessFiles").asInt(-1) != 0
                || root.path("mechanismCases").asInt() != 129
                || root.path("originalGuards").size() != 129) {
            throw new IllegalArgumentException("ORIGINAL129_SYNTHETIC_CONTRACT_REQUIRED");
        }
        final var sources = new java.util.HashMap<String, String>();
        root.required("frozenSourceSha256")
                .fields()
                .forEachRemaining(entry -> sources.put(entry.getKey(), entry.getValue().asText()));
        if (!sources.equals(
                java.util.Map.of(
                        "civil", "E319657F17D9E0B88F657EE35A64902E78772B12B53330009C5036B9E3EF72A0",
                        "precision",
                                "FC5B40A3D22269BFBF6A2CB89ACE7E13AFBB240FF41A75D0379793D3B3D87165",
                        "encoding",
                                "1D85A7E702658A9A153BDEA48E47730B0AEEF25F128D052DBCEDEB48587DF45D"))) {
            throw new IllegalArgumentException("ORIGINAL129_FROZEN_PYTHON_SOURCES_REQUIRED");
        }
        final var adapter =
                (JavaPort) Class.forName(args[3]).getDeclaredConstructor().newInstance();
        final var records = root.required("records");
        if (!records.isArray() || records.isEmpty() || records.size() > 4096) {
            throw new IllegalArgumentException("COMPLETE_BOUNDED_TRACE_REQUIRED");
        }
        final var used = new HashSet<Integer>();
        final var names = new HashSet<String>();
        final var families = new java.util.HashMap<String, Integer>();
        for (final var guard : root.required("originalGuards")) {
            final var identity =
                    guard.required("family").asText() + ":" + guard.required("name").asText();
            if (!names.add(identity) || guard.required("traceIds").isEmpty()) {
                throw new IllegalArgumentException("UNIQUE_129_CASES_WITH_TRACE_REQUIRED");
            }
            families.merge(guard.required("family").asText(), 1, Integer::sum);
            for (final var id : guard.required("traceIds")) {
                if (!id.isInt() || id.intValue() < 1 || id.intValue() > records.size()) {
                    throw new IllegalArgumentException("VALID_GUARD_TRACE_REFERENCE_REQUIRED");
                }
                if (!used.add(id.intValue())) {
                    throw new IllegalArgumentException("TRACE_MUST_BELONG_TO_ONE_ORIGINAL_GUARD");
                }
            }
        }
        if (!families.equals(
                        java.util.Map.of(
                                "CIVIL63",
                                63,
                                "PRECISION42",
                                42,
                                "BINARY_CAUSAL8",
                                8,
                                "ENCODING16",
                                16))
                || used.size() != records.size()) {
            throw new IllegalArgumentException("EXACT_ORIGINAL_63_42_8_16_COVERAGE_REQUIRED");
        }
        int sequence = 0;
        for (final var record : records) {
            if (record.required("sequence").intValue() != ++sequence) {
                throw new IllegalArgumentException("CONTIGUOUS_TRACE_REQUIRED");
            }
            final var actual =
                    adapter.evaluate(
                            record.required("operation").textValue(),
                            record.required("arguments").deepCopy());
            if (!record.required("result").equals(actual)) {
                // Emit the synthetic sequence only; do not log data from product adapters.
                throw new AssertionError("JAVA_PYTHON_DIFFERENTIAL_SEQUENCE_" + sequence);
            }
        }
        System.out.println("ORIGINAL129_DIFFERENTIAL_PASS guards=129 records=" + sequence);
    }
}
