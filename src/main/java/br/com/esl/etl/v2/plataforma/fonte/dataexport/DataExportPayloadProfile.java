package br.com.esl.etl.v2.plataforma.fonte.dataexport;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Pattern;

/** Resume nomes, presença, nulidade, tipos e formas temporais sem reter valores de negócio. */
public record DataExportPayloadProfile(
        int recordCount, List<DataExportPayloadFieldProfile> fields) {

    private static final Pattern DATE = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");
    private static final Pattern LOCAL_DATE_TIME =
            Pattern.compile("\\d{4}-\\d{2}-\\d{2}[ T]\\d{2}:\\d{2}:\\d{2}(?:\\.\\d{1,9})?");
    private static final Pattern OFFSET_DATE_TIME =
            Pattern.compile(
                    "\\d{4}-\\d{2}-\\d{2}[ T]\\d{2}:\\d{2}:\\d{2}(?:\\.\\d{1,9})?(?:Z|[+-]\\d{2}:?\\d{2})");

    public DataExportPayloadProfile {
        if (recordCount < 0) {
            throw new IllegalArgumentException("A quantidade de registros não pode ser negativa.");
        }
        fields =
                List.copyOf(
                        Objects.requireNonNull(fields, "Os campos do perfil são obrigatórios."));
    }

    public static DataExportPayloadProfile fromRecords(final List<JsonNode> records) {
        Objects.requireNonNull(records, "Os registros Data Export são obrigatórios.");
        final Map<String, MutableFieldProfile> profiles = new TreeMap<>();
        for (final JsonNode record : records) {
            if (record == null || !record.isObject()) {
                throw new IllegalArgumentException(
                        "Cada registro Data Export deve ser um objeto JSON.");
            }
            record.fields()
                    .forEachRemaining(
                            entry ->
                                    profiles.computeIfAbsent(
                                                    entry.getKey(),
                                                    ignored -> new MutableFieldProfile())
                                            .observe(entry.getValue()));
        }
        final List<DataExportPayloadFieldProfile> fields = new ArrayList<>();
        profiles.forEach((name, profile) -> fields.add(profile.toImmutable(name)));
        return new DataExportPayloadProfile(records.size(), fields);
    }

    private static String jsonType(final JsonNode value) {
        if (value == null || value.isNull()) {
            return "null";
        }
        if (value.isTextual()) {
            return "string";
        }
        if (value.isIntegralNumber()) {
            return "integer";
        }
        if (value.isFloatingPointNumber()) {
            return "number";
        }
        if (value.isBoolean()) {
            return "boolean";
        }
        if (value.isArray()) {
            return "array";
        }
        if (value.isObject()) {
            return "object";
        }
        return "other";
    }

    private static String textualFormat(final String value) {
        if (value.isBlank()) {
            return "blank";
        }
        if (OFFSET_DATE_TIME.matcher(value).matches()) {
            return "offset-date-time";
        }
        if (LOCAL_DATE_TIME.matcher(value).matches()) {
            return "local-date-time";
        }
        if (DATE.matcher(value).matches()) {
            return "date";
        }
        return "text";
    }

    private static final class MutableFieldProfile {

        private int presentCount;
        private int nullCount;
        private final Set<String> jsonTypes = new TreeSet<>();
        private final Set<String> textualFormats = new TreeSet<>();

        void observe(final JsonNode value) {
            presentCount++;
            jsonTypes.add(jsonType(value));
            if (value == null || value.isNull()) {
                nullCount++;
            } else if (value.isTextual()) {
                textualFormats.add(textualFormat(value.asText()));
            }
        }

        DataExportPayloadFieldProfile toImmutable(final String technicalName) {
            return new DataExportPayloadFieldProfile(
                    technicalName,
                    presentCount,
                    nullCount,
                    List.copyOf(jsonTypes),
                    List.copyOf(textualFormats));
        }
    }
}
