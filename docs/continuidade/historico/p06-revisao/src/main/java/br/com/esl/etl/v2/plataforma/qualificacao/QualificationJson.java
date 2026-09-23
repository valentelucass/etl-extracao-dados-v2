package br.com.esl.etl.v2.plataforma.qualificacao;

import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.cfg.JsonNodeFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Set;

/** Bounded, strict UTF-8 and JSON shared by the local package and campaign boundaries. */
public final class QualificationJson {
    private QualificationJson() {}

    public static JsonNode read(final Path path, final int maximumBytes) throws IOException {
        regular(path);
        if (Files.size(path) > maximumBytes) {
            throw new IllegalArgumentException("QUAL_JSON_SIZE");
        }
        try (var input = Files.newInputStream(path)) {
            return parse(input.readNBytes(maximumBytes + 1), maximumBytes);
        }
    }

    public static JsonNode parse(final byte[] bytes, final int maximumBytes) throws IOException {
        if (bytes.length == 0 || bytes.length > maximumBytes) {
            throw new IllegalArgumentException("QUAL_JSON_SIZE");
        }
        final String text;
        try {
            text =
                    StandardCharsets.UTF_8
                            .newDecoder()
                            .onMalformedInput(CodingErrorAction.REPORT)
                            .onUnmappableCharacter(CodingErrorAction.REPORT)
                            .decode(ByteBuffer.wrap(bytes))
                            .toString();
        } catch (final CharacterCodingException failure) {
            throw new IllegalArgumentException("QUAL_JSON_UTF8", failure);
        }
        if (text.indexOf('\ufffd') >= 0 || text.charAt(0) == '\ufeff') {
            throw new IllegalArgumentException("QUAL_JSON_UTF8");
        }
        final var mapper =
                JsonMapper.builder()
                        .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                        .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
                        .disable(JsonNodeFeature.STRIP_TRAILING_BIGDECIMAL_ZEROES)
                        .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                        .build();
        mapper.getFactory()
                .setStreamReadConstraints(
                        StreamReadConstraints.builder()
                                .maxNestingDepth(24)
                                .maxStringLength(131072)
                                .maxNumberLength(40)
                                .build());
        return mapper.readTree(text);
    }

    public static void fields(final JsonNode node, final String... names) {
        if (!node.isObject()) {
            throw new IllegalArgumentException("QUAL_JSON_OBJECT");
        }
        final var actual = new HashSet<String>();
        node.fieldNames().forEachRemaining(actual::add);
        if (!actual.equals(Set.of(names))) {
            throw new IllegalArgumentException("QUAL_JSON_MEMBERS");
        }
    }

    public static String text(final JsonNode node, final String key, final int maximum) {
        final var value = node.path(key);
        if (!value.isTextual()
                || value.textValue().isEmpty()
                || value.textValue().length() > maximum) {
            throw new IllegalArgumentException("QUAL_JSON_TEXT");
        }
        return value.textValue();
    }

    public static String digest(final JsonNode node, final String key) {
        final String value = text(node, key, 64);
        if (!value.matches("[a-f0-9]{64}")) {
            throw new IllegalArgumentException("QUAL_JSON_DIGEST");
        }
        return value;
    }

    public static int number(final JsonNode node, final String key, final int min, final int max) {
        final var value = node.path(key);
        if (!value.isIntegralNumber()
                || !value.canConvertToInt()
                || value.intValue() < min
                || value.intValue() > max) {
            throw new IllegalArgumentException("QUAL_JSON_INTEGER");
        }
        return value.intValue();
    }

    public static boolean flag(final JsonNode node, final String key) {
        if (!node.path(key).isBoolean()) {
            throw new IllegalArgumentException("QUAL_JSON_BOOLEAN");
        }
        return node.path(key).booleanValue();
    }

    public static void array(final JsonNode node, final int min, final int max) {
        if (!node.isArray() || node.size() < min || node.size() > max) {
            throw new IllegalArgumentException("QUAL_JSON_ARRAY");
        }
    }

    public static void regular(final Path path) throws IOException {
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalArgumentException("QUAL_FILE_REGULAR_REQUIRED");
        }
        Path current = path.toAbsolutePath().normalize();
        while (current != null) {
            if (Files.isSymbolicLink(current)
                    || !current.toRealPath()
                            .equals(current.toRealPath(LinkOption.NOFOLLOW_LINKS))) {
                throw new IllegalArgumentException("QUAL_FILE_LINK");
            }
            current = current.getParent();
        }
        if (path.getFileName().toString().contains(":")) {
            throw new IllegalArgumentException("QUAL_FILE_ADS");
        }
    }

    public static String sha256(final byte[] bytes) {
        return HexFormat.of().formatHex(algorithm().digest(bytes));
    }

    public static String sha256(final Path path) throws IOException {
        regular(path);
        final var digest = algorithm();
        try (var input = Files.newInputStream(path)) {
            final var buffer = new byte[16384];
            int count;
            while ((count = input.read(buffer)) != -1) {
                digest.update(buffer, 0, count);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static MessageDigest algorithm() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (final NoSuchAlgorithmException impossible) {
            throw new ExceptionInInitializerError(impossible);
        }
    }
}
