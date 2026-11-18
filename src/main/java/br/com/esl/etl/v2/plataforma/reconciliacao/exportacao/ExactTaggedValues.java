package br.com.esl.etl.v2.plataforma.reconciliacao.exportacao;

import static br.com.esl.etl.v2.plataforma.reconciliacao.exportacao.ExportValidationSupport.require;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/** Exact tagged Python-compatible values for the independent input-only differential port. */
final class ExactTaggedValues {
    record Tuple(List<Object> values) {}

    record InvalidFloat(String literal) {}

    private ExactTaggedValues() {}

    static Object decode(final JsonNode value) {
        if (value == null || value.isNull()) {
            return null;
        }
        if (value.isTextual()) {
            return value.textValue();
        }
        if (value.isBoolean()) {
            return value.booleanValue();
        }
        require(!value.isNumber(), "DIFFERENTIAL_EXACT_TAGGED_INTEGERS_REQUIRED");
        if (value.isArray()) {
            final var result = new ArrayList<Object>();
            value.forEach(child -> result.add(decode(child)));
            return result;
        }
        require(value.isObject(), "DIFFERENTIAL_TAGGED_VALUE");
        if (value.size() == 1) {
            if (value.has("$int")) {
                return new BigInteger(value.get("$int").textValue());
            }
            if (value.has("$invalidRuntimeFloat")) {
                return new InvalidFloat(value.get("$invalidRuntimeFloat").textValue());
            }
            if (value.has("$tuple")) {
                return new Tuple(list(decode(value.get("$tuple"))));
            }
            if (value.has("$set")) {
                return new LinkedHashSet<>(list(decode(value.get("$set"))));
            }
            if (value.has("$map")) {
                final var result = new LinkedHashMap<Object, Object>();
                for (final var pair : value.get("$map")) {
                    require(pair.isArray() && pair.size() == 2, "DIFFERENTIAL_MAP_PAIR");
                    require(!result.containsKey(decode(pair.get(0))), "DIFFERENTIAL_DUPLICATE_KEY");
                    result.put(decode(pair.get(0)), decode(pair.get(1)));
                }
                return result;
            }
        }
        final var result = new LinkedHashMap<Object, Object>();
        value.fields()
                .forEachRemaining(entry -> result.put(entry.getKey(), decode(entry.getValue())));
        return result;
    }

    static JsonNode encode(final Object value) {
        final var json = ExportValidationSupport.JSON;
        if (value == null) {
            return json.nullNode();
        }
        if (value instanceof String text) {
            return json.getNodeFactory().textNode(text);
        }
        if (value instanceof Boolean flag) {
            return json.getNodeFactory().booleanNode(flag);
        }
        if (value instanceof Number number) {
            return json.createObjectNode().put("$int", number.toString());
        }
        if (value instanceof InvalidFloat invalid) {
            return json.createObjectNode().put("$invalidRuntimeFloat", invalid.literal());
        }
        if (value instanceof Tuple tuple) {
            return json.createObjectNode().set("$tuple", encode(tuple.values()));
        }
        if (value instanceof Map<?, ?> map) {
            if (map.keySet().stream().allMatch(key -> key instanceof String)) {
                final var result = json.createObjectNode();
                map.forEach((key, child) -> result.set((String) key, encode(child)));
                return result;
            }
            final var pairs = json.createArrayNode();
            map.forEach((key, child) -> pairs.addArray().add(encode(key)).add(encode(child)));
            return json.createObjectNode().set("$map", pairs);
        }
        if (value instanceof Iterable<?> sequence) {
            final var result = json.createArrayNode();
            sequence.forEach(child -> result.add(encode(child)));
            return result;
        }
        throw new IllegalArgumentException("DIFFERENTIAL_RESULT_TYPE");
    }

    @SuppressWarnings("unchecked")
    static Map<Object, Object> map(final Object value) {
        require(value instanceof Map<?, ?>, "DIFFERENTIAL_OBJECT_REQUIRED");
        return (Map<Object, Object>) value;
    }

    @SuppressWarnings("unchecked")
    static List<Object> list(final Object value) {
        if (value instanceof Tuple tuple) {
            return tuple.values();
        }
        if (value instanceof java.util.Set<?> values) {
            return new ArrayList<>(values);
        }
        require(value instanceof List<?>, "DIFFERENTIAL_SEQUENCE_REQUIRED");
        return (List<Object>) value;
    }

    static Map<Object, Object> obj(final Object... pairs) {
        final var result = new LinkedHashMap<Object, Object>();
        require(pairs.length % 2 == 0, "DIFFERENTIAL_OBJECT_PAIRS");
        for (int index = 0; index < pairs.length; index += 2) {
            result.put(pairs[index], pairs[index + 1]);
        }
        return result;
    }

    static Tuple tuple(final Object... values) {
        return new Tuple(java.util.Arrays.asList(values));
    }

    static Map<Object, Object> plus(final Map<Object, Object> base, final Object... pairs) {
        final var result = new LinkedHashMap<>(base);
        result.putAll(obj(pairs));
        return result;
    }

    static BigInteger integer(final Object value, final String code) {
        require(value instanceof BigInteger, code);
        return (BigInteger) value;
    }

    static String string(final Object value) {
        return value instanceof String text ? text : null;
    }

    static boolean truth(final Object value) {
        if (value == null || Boolean.FALSE.equals(value)) {
            return false;
        }
        if (value instanceof String text) {
            return !text.isEmpty();
        }
        if (value instanceof Map<?, ?> values) {
            return !values.isEmpty();
        }
        if (value instanceof java.util.Collection<?> values) {
            return !values.isEmpty();
        }
        if (value instanceof BigInteger number) {
            return number.signum() != 0;
        }
        return true;
    }

    static byte[] canonical(final Object value) {
        final var result = new StringBuilder();
        append(result, value, 0);
        require(result.length() <= 2097152, "PRECISION_SMALL_CANONICAL_BOUND");
        return result.toString().getBytes(StandardCharsets.US_ASCII);
    }

    private static void append(final StringBuilder out, final Object value, final int depth) {
        require(depth <= 32, "PRECISION_SMALL_JSON_DEPTH32");
        if (value == null) {
            out.append("null");
        } else if (value instanceof Boolean || value instanceof Number) {
            out.append(value);
        } else if (value instanceof InvalidFloat invalid) {
            require(
                    invalid.literal().matches("-?[0-9]+\\.[0-9]+"),
                    "DIFFERENTIAL_INVALID_FLOAT_LITERAL");
            out.append(invalid.literal());
        } else if (value instanceof String text) {
            out.append('"');
            for (int index = 0; index < text.length(); index++) {
                final char ch = text.charAt(index);
                switch (ch) {
                    case '"' -> out.append("\\\"");
                    case '\\' -> out.append("\\\\");
                    case '\n' -> out.append("\\n");
                    case '\r' -> out.append("\\r");
                    case '\t' -> out.append("\\t");
                    case '\b' -> out.append("\\b");
                    case '\f' -> out.append("\\f");
                    default -> {
                        if (ch < 32 || ch >= 128) {
                            out.append("\\u")
                                    .append(String.format(java.util.Locale.ROOT, "%04x", (int) ch));
                        } else {
                            out.append(ch);
                        }
                    }
                }
            }
            out.append('"');
        } else if (value instanceof Map<?, ?> map) {
            require(
                    map.keySet().stream().allMatch(key -> key instanceof String),
                    "PRECISION_JSON_STRING_KEYS");
            final var keys = new ArrayList<String>();
            map.keySet().forEach(key -> keys.add((String) key));
            keys.sort(String::compareTo);
            out.append('{');
            boolean comma = false;
            for (final var key : keys) {
                if (comma) {
                    out.append(',');
                }
                comma = true;
                append(out, key, depth + 1);
                out.append(':');
                append(out, map.get(key), depth + 1);
            }
            out.append('}');
        } else {
            final var values = list(value);
            out.append('[');
            boolean comma = false;
            for (final var child : values) {
                if (comma) {
                    out.append(',');
                }
                comma = true;
                append(out, child, depth + 1);
            }
            out.append(']');
        }
    }

    static String hash(final Object value) {
        return ExportValidationSupport.sha256(canonical(value)).toUpperCase(java.util.Locale.ROOT);
    }
}
