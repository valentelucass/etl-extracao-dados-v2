package br.com.esl.etl.v2.bootstrap;

import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Set;

/** Explicit synthetic configuration; this class never loads an environment or provider. */
record DevLocalMainConfiguration(
        int scale, int batchSize, int maximumInFlight, long maximumBytes, int deadlineSeconds) {
    private static final int MAXIMUM_CONFIG_BYTES = 4096;
    private static final Set<String> FIELDS =
            Set.of(
                    "synthetic",
                    "scale",
                    "batchSize",
                    "maximumInFlight",
                    "maximumBytes",
                    "deadlineSeconds");
    private static final ObjectMapper MAPPER =
            JsonMapper.builder(
                            com.fasterxml.jackson.core.JsonFactory.builder()
                                    .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                                    .streamReadConstraints(
                                            StreamReadConstraints.builder()
                                                    .maxNestingDepth(8)
                                                    .maxStringLength(256)
                                                    .maxNumberLength(20)
                                                    .build())
                                    .build())
                    .build();

    DevLocalMainConfiguration {
        if (!Set.of(2, 4, 8, 16).contains(scale)
                || batchSize < 1
                || batchSize > 16
                || maximumInFlight != 1
                || maximumBytes < 4096
                || maximumBytes > 4L * 1024 * 1024
                || deadlineSeconds < 1
                || deadlineSeconds > 60) {
            throw new IllegalArgumentException("DEV_LOCAL_CONFIG_LIMIT");
        }
    }

    static DevLocalMainConfiguration sample() {
        return new DevLocalMainConfiguration(2, 4, 1, 1024L * 1024, 30);
    }

    static DevLocalMainConfiguration read(final Path path) throws IOException {
        final var absolute = path.toAbsolutePath().normalize();
        if (!absolute.getFileName().toString().endsWith(".json")
                || !Files.isRegularFile(absolute, LinkOption.NOFOLLOW_LINKS)
                || Files.isSymbolicLink(absolute)) {
            throw new IllegalArgumentException("DEV_LOCAL_CONFIG_FILE_REQUIRED");
        }
        for (var parent = absolute.getParent(); parent != null; parent = parent.getParent()) {
            final var attributes =
                    Files.readAttributes(
                            parent,
                            java.nio.file.attribute.BasicFileAttributes.class,
                            LinkOption.NOFOLLOW_LINKS);
            if (attributes.isSymbolicLink() || attributes.isOther() || !attributes.isDirectory()) {
                throw new IllegalArgumentException("DEV_LOCAL_CONFIG_LINK_REFUSED");
            }
        }
        try (var input = Files.newInputStream(absolute, LinkOption.NOFOLLOW_LINKS)) {
            final var bytes = input.readNBytes(MAXIMUM_CONFIG_BYTES + 1);
            if (bytes.length == 0 || bytes.length > MAXIMUM_CONFIG_BYTES) {
                throw new IllegalArgumentException("DEV_LOCAL_CONFIG_BYTES");
            }
            return parse(bytes);
        }
    }

    static DevLocalMainConfiguration parse(final byte[] bytes) throws IOException {
        if (bytes.length == 0 || bytes.length > MAXIMUM_CONFIG_BYTES) {
            throw new IllegalArgumentException("DEV_LOCAL_CONFIG_BYTES");
        }
        final JsonNode json;
        try (var parser = MAPPER.createParser(bytes)) {
            json = MAPPER.readTree(parser);
            if (parser.nextToken() != null) {
                throw new IllegalArgumentException("DEV_LOCAL_CONFIG_TRAILING_CONTENT");
            }
        }
        if (json == null || !json.isObject() || json.size() != FIELDS.size()) {
            throw new IllegalArgumentException("DEV_LOCAL_CONFIG_SCHEMA");
        }
        final var names = json.fieldNames();
        while (names.hasNext()) {
            if (!FIELDS.contains(names.next())) {
                throw new IllegalArgumentException("DEV_LOCAL_CONFIG_SCHEMA");
            }
        }
        if (!json.path("synthetic").isBoolean() || !json.path("synthetic").booleanValue()) {
            throw new IllegalArgumentException("DEV_LOCAL_SYNTHETIC_REQUIRED");
        }
        return new DevLocalMainConfiguration(
                integer(json, "scale"),
                integer(json, "batchSize"),
                integer(json, "maximumInFlight"),
                integer(json, "maximumBytes"),
                integer(json, "deadlineSeconds"));
    }

    private static int integer(final JsonNode json, final String field) {
        final var value = json.path(field);
        if (!value.isIntegralNumber() || !value.canConvertToInt()) {
            throw new IllegalArgumentException("DEV_LOCAL_CONFIG_INTEGER_REQUIRED");
        }
        return value.intValue();
    }

    ObjectNode document() {
        return MAPPER.createObjectNode()
                .put("synthetic", true)
                .put("scale", scale)
                .put("batchSize", batchSize)
                .put("maximumInFlight", maximumInFlight)
                .put("maximumBytes", maximumBytes)
                .put("deadlineSeconds", deadlineSeconds);
    }
}
