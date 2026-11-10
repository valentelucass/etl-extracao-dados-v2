package br.com.esl.etl.v2.plataforma.reconciliacao.exportacao;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;

/** Bounded inputs shared by the explicit independent-export profile. */
public final class ExportValidationSupport {
    public static final JsonMapper JSON =
            JsonMapper.builder()
                    .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                    .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
                    .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                    .addModule(new JavaTimeModule())
                    .build();

    private ExportValidationSupport() {}

    static {
        JSON.getFactory()
                .setStreamReadConstraints(
                        StreamReadConstraints.builder()
                                .maxNestingDepth(64)
                                .maxNumberLength(1024)
                                .maxStringLength(32768)
                                .build());
    }

    public static void require(final boolean condition, final String code) {
        if (!condition) {
            throw new IllegalArgumentException(code);
        }
    }

    public static String text(final JsonNode node, final String field) {
        final var value = node.required(field);
        require(value.isTextual() && !value.textValue().isBlank(), "EXPORT_TEXT_REQUIRED");
        return value.textValue();
    }

    public static String sha256(final byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (final NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA256_UNAVAILABLE", impossible);
        }
    }

    public static byte[] canonicalBytes(final JsonNode node) throws IOException {
        if (node.isObject()) {
            final var sorted = JSON.createObjectNode();
            final var names = new java.util.TreeSet<String>();
            node.fieldNames().forEachRemaining(names::add);
            for (final String name : names) {
                sorted.set(name, JSON.readTree(canonicalBytes(node.get(name))));
            }
            return JSON.writeValueAsBytes(sorted);
        }
        if (node.isArray()) {
            final var array = JSON.createArrayNode();
            for (final var value : node) {
                array.add(JSON.readTree(canonicalBytes(value)));
            }
            return JSON.writeValueAsBytes(array);
        }
        return JSON.writeValueAsBytes(node);
    }

    public record Pin(
            @JsonSerialize(using = NativePinPathSerializer.class) Path path,
            String sha256,
            long bytes) {
        public Pin {
            require(path != null && path.isAbsolute(), "EXPORT_ABSOLUTE_PIN_REQUIRED");
            path = path.normalize();
            require(sha256 != null && sha256.matches("[a-f0-9]{64}"), "EXPORT_DIGEST");
            require(bytes > 0 && bytes <= 8388608, "EXPORT_PIN_BOUND");
        }

        public static Pin from(final JsonNode node) {
            require(
                    node.required("bytes").isIntegralNumber()
                            && node.get("bytes").canConvertToLong(),
                    "EXPORT_PIN_SIZE");
            return new Pin(
                    Path.of(text(node, "path")),
                    text(node, "sha256"),
                    node.get("bytes").longValue());
        }

        public byte[] read(final int maximum, final Budget budget) throws IOException {
            budget.check();
            require(bytes <= maximum, "EXPORT_INPUT_BOUND");
            for (Path part = path; part != null; part = part.getParent()) {
                require(!Files.isSymbolicLink(part), "EXPORT_LINK_REFUSED");
            }
            require(Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS), "EXPORT_REGULAR_FILE");
            final byte[] value;
            try (var stream = Files.newInputStream(path)) {
                value = stream.readNBytes(Math.toIntExact(bytes + 1));
            }
            require(
                    value.length == bytes && sha256.equals(ExportValidationSupport.sha256(value)),
                    "EXPORT_PIN_CHANGED");
            budget.check();
            return value;
        }

        public JsonNode json(final int maximum, final Budget budget) throws IOException {
            final byte[] value = read(maximum, budget);
            try (var parser = JSON.createParser(value)) {
                final JsonNode node = JSON.readTree(parser);
                require(node != null && parser.nextToken() == null, "EXPORT_JSON_TRAILING");
                return node;
            }
        }
    }

    /** Pin JSON uses the absolute native path accepted by Pin.from, never Path's file URI codec. */
    public static final class NativePinPathSerializer extends JsonSerializer<Path> {
        @Override
        public void serialize(
                final Path value, final JsonGenerator output, final SerializerProvider provider)
                throws IOException {
            require(value != null && value.isAbsolute(), "EXPORT_ABSOLUTE_PIN_REQUIRED");
            output.writeString(value.toString());
        }
    }

    /** Monotonic deadline; all parsers and accumulators use the same invocation budget. */
    public static final class Budget {
        private final long started = System.nanoTime();
        private final long maximumNanos;
        private long cells;

        public Budget(final Duration duration) {
            require(
                    duration != null
                            && !duration.isNegative()
                            && !duration.isZero()
                            && duration.compareTo(Duration.ofSeconds(150)) <= 0,
                    "EXPORT_TIME_BOUND");
            maximumNanos = duration.toNanos();
        }

        public void check() {
            require(System.nanoTime() - started < maximumNanos, "EXPORT_DEADLINE");
        }

        public void cell() {
            check();
            require(++cells <= 1200000, "EXPORT_CELL_BOUND");
        }
    }
}
