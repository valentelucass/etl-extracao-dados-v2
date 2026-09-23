package br.com.esl.etl.v2.plataforma.qualificacao;

import br.com.esl.etl.v2.plataforma.analitico.AnalyticScenarioVariant;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Source-wire expectations, independently reconstructed from packaged input literals. */
public final class QualificationWireOracle {
    private final Map<String, ObjectNode> seeds = new HashMap<>();
    private final Map<String, List<String>> fields = new HashMap<>();
    private final DeclaredWireRows declared;

    public QualificationWireOracle() throws IOException {
        this(AnalyticScenarioVariant.BASELINE);
    }

    public QualificationWireOracle(final AnalyticScenarioVariant variant) throws IOException {
        this(variant, null);
    }

    public QualificationWireOracle(
            final AnalyticScenarioVariant variant, final DeclaredWireRows declared)
            throws IOException {
        this.declared = declared;
        java.util.Objects.requireNonNull(variant);
        for (final var entity :
                List.of("CAP", "FAT", "INV", "SIN", "MAN", "COL", "COT", "FRE", "LOC")) {
            final String name =
                    switch (entity) {
                        case "MAN" -> "analytic-laboratory/manifest";
                        case "COL" -> "analytic-laboratory/collection";
                        case "COT" -> "analytic-laboratory/quote";
                        case "FRE", "LOC" ->
                                "expansion-laboratory/" + entity.toLowerCase(Locale.ROOT);
                        default -> "analytic-laboratory/" + entity.toLowerCase(Locale.ROOT);
                    };
            final var seed = read(name);
            seeds.put(entity, seed);
            final var names = new ArrayList<String>();
            if (entity.equals("COL") || entity.equals("COT")) {
                final String catalog = entity.equals("COL") ? "collection" : "quote";
                for (final var field :
                        read("analytic-laboratory/" + catalog + "-fields").path("fields")) {
                    names.add(field.path("name").asText());
                }
            } else {
                seed.fieldNames().forEachRemaining(names::add);
                names.remove("synthetic_fixture");
                if (entity.equals("CAP") && !names.contains("comments")) {
                    names.add("comments");
                }
            }
            fields.put(entity, List.copyOf(names));
        }
        if (variant == AnalyticScenarioVariant.VALUES_AND_NULLS) {
            final var representative = read("analytic-laboratory/qualification-representative");
            seeds.get("FRE").setAll((ObjectNode) representative.path("freight"));
            seeds.get("LOC").setAll((ObjectNode) representative.path("location"));
        }
    }

    private static ObjectNode read(final String name) throws IOException {
        try (var stream =
                QualificationWireOracle.class.getResourceAsStream("/" + name + ".synthetic.json")) {
            if (stream == null) {
                throw new IllegalArgumentException("QUAL_WIRE_FIXTURE_MISSING");
            }
            final var node = QualificationJson.parse(stream.readNBytes(32769), 32768);
            if (!node.isObject()) {
                throw new IllegalArgumentException("QUAL_WIRE_FIXTURE_OBJECT");
            }
            return (ObjectNode) node;
        }
    }

    public ObjectNode source(
            final String entity,
            final int root,
            final int component,
            final int revision,
            final boolean correction) {
        if (declared != null) {
            return declared.source(entity, root, component, revision, correction);
        }
        if (!seeds.containsKey(entity)
                || root < 1
                || root > 32
                || component < 1
                || component > 2
                || revision < 1
                || revision > 1000) {
            throw new IllegalArgumentException("QUAL_WIRE_SCOPE");
        }
        final var value = seeds.get(entity).deepCopy();
        switch (entity) {
            case "CAP" ->
                    value.put("ant_ils_sequence_code", root)
                            .put("ant_ces_value", component == 1 ? "40.00" : "60.00")
                            .put("ant_ils_pas_value", component == 1 ? "40.00" : "60.00");
            case "FAT" ->
                    value.put("id", root * 10 + component).put("corporation_sequence_number", root);
            case "INV" ->
                    value.put("sequence_code", root)
                            .put("cnr_c_s_fit_corporation_sequence_number", root * 10 + component);
            case "SIN" ->
                    value.put("sequence_code", root)
                            .put("icm_fis_fit_corporation_sequence_number", root * 10 + component)
                            .put("icm_fis_ioe_number", "synthetic-occurrence-" + component);
            case "FRE" ->
                    value.put("id", 300000 + root)
                            .put("corporation_sequence_number", 600000 + root);
            case "LOC" -> value.put("corporation_sequence_number", 600000 + root);
            case "COL" ->
                    value.put("id", 200000 + root)
                            .put("sequence_code", 100000 + root)
                            .put("synthetic_item_key", root);
            case "COT" -> value.put("sequence_code", 9999 + root);
            case "MAN" -> {
                value.put("sequence_code", root)
                        .put("mft_pfs_pck_sequence_code", 100000 + root)
                        .put(
                                "mft_mfs_key",
                                "1".repeat(40) + String.format(Locale.ROOT, "%04d", root))
                        .put(
                                "finished_at",
                                "2036-04-0"
                                        + (correction ? 2 : 1)
                                        + "T12:00:00."
                                        + String.format(Locale.ROOT, "%09d", revision)
                                        + "Z");
                if (correction) {
                    value.put("departured_at", "2036-04-02T12:00:00Z")
                            .put("mft_crn_psn_nickname", "SYNTHETIC BRANCH B");
                }
            }
            default -> throw new IllegalArgumentException("QUAL_WIRE_ENTITY");
        }
        return value;
    }

    public int expansionRootOrdinal(
            final String entity, final String rootType, final String rootKey) {
        return declared == null
                ? Integer.parseInt(rootKey.substring(rootKey.lastIndexOf('-') + 1))
                : declared.rootOrdinal(entity, rootType, rootKey);
    }

    public int expansionComponentOrdinal(
            final String entity,
            final String rootType,
            final String rootKey,
            final String partType,
            final String partKey,
            final String componentType,
            final String componentKey) {
        return declared == null
                ? Integer.parseInt(componentKey.substring("component-".length()))
                : declared.componentOrdinal(
                        entity, rootType, rootKey, partType, partKey, componentType, componentKey);
    }

    public int sourceOrdinal(final String entity, final String key) {
        if (declared != null && declared.integral()) {
            final int colon = key.indexOf(':');
            if (colon < 1) {
                throw new IllegalArgumentException("INTEGRAL_WIRE_KEY");
            }
            return declared.rootOrdinal(entity, key.substring(0, colon), key.substring(colon + 1));
        }
        return Integer.parseInt(key.substring(8))
                - switch (entity) {
                    case "COL" -> 200000;
                    case "COT" -> 9999;
                    default -> 0;
                };
    }

    public String manifestKey(final int root, final int revision, final boolean correction) {
        return declared != null && declared.integral()
                ? QualificationJson.text(
                        declared.envelope("COL", root, 1, revision, correction), "manifestKey", 256)
                : "INTEGER:" + root;
    }

    public int sourceRows(
            final String entity, final int root, final int revision, final boolean correction) {
        return declared != null && declared.integral()
                ? QualificationJson.number(
                        declared.envelope(entity, root, 1, revision, correction),
                        "sourceRows",
                        1,
                        1000)
                : 3;
    }

    public ObjectNode collectionLateral(
            final int root, final int revision, final boolean correction, final long supplement)
            throws IOException {
        return declared != null && declared.integral()
                ? lateral(
                        declared.envelope("COL", root, 1, revision, correction).path("lateral"),
                        supplement)
                : lateral(supplement);
    }

    public String componentIdentity(final String entity, final int root, final int component) {
        return declared == null
                ? QualificationOracles.legacyComponentIdentity(entity, root, component)
                : declared.componentIdentity(entity, root, component);
    }

    /**
     * Technical members are exact IDs read from the capture's own receipts, with no business
     * values.
     */
    public boolean compare(
            final String entity,
            final ObjectNode input,
            final JsonNode actual,
            final ObjectNode technical) {
        if (!actual.isObject() || !fields.containsKey(entity)) {
            return false;
        }
        if (entity.equals("FRE") || entity.equals("LOC")) {
            return equivalentJson(input, actual);
        }
        if (entity.equals("MAN")) {
            return manifest(input, actual, technical);
        }
        if (entity.equals("COL")) {
            return collection(input, actual, technical);
        }
        final var expected = technical.deepCopy();
        for (final String name : fields.get(entity)) {
            if (expected.has(name)) {
                throw new IllegalArgumentException("QUAL_WIRE_TECHNICAL_COLLISION");
            }
            expected.set(name, field(input.get(name), false));
        }
        return equivalentJson(expected, actual);
    }

    private static ObjectNode field(final JsonNode value, final boolean numericWire) {
        final var field = JsonNodeFactory.instance.objectNode();
        final boolean absent = value == null || value.isMissingNode();
        final String presence = absent ? "ABSENT" : value.isNull() ? "NULL" : "VALUE";
        final String wire =
                absent
                        ? "ABSENT"
                        : value.isNull()
                                ? "NULL"
                                : value.isTextual()
                                        ? "STRING"
                                        : value.isIntegralNumber()
                                                ? "INTEGER"
                                                : value.isNumber()
                                                        ? "NUMBER"
                                                        : value.isBoolean()
                                                                ? "BOOLEAN"
                                                                : value.isArray()
                                                                        ? "ARRAY"
                                                                        : "OBJECT";
        field.put("presence", presence);
        if (numericWire) {
            if (absent) {
                field.putNull("wire");
            } else {
                field.put(
                        "wire",
                        switch (wire) {
                            case "NULL" -> 0;
                            case "STRING" -> 1;
                            case "INTEGER", "NUMBER" -> 2;
                            case "BOOLEAN" -> 3;
                            case "ARRAY" -> 4;
                            default -> 5;
                        });
            }
        } else {
            field.put("wire", wire);
        }
        if (!presence.equals("VALUE")) {
            field.putNull("raw");
        } else {
            field.put("raw", value.isTextual() ? value.textValue() : value.toString());
        }
        return field;
    }

    private boolean manifest(
            final ObjectNode input, final JsonNode actual, final ObjectNode technical) {
        return manifestDifference(input, actual, technical).isEmpty();
    }

    /** A bounded field coordinate for diagnostics; neither value nor identity is emitted. */
    public String manifestDifference(
            final ObjectNode input, final JsonNode actual, final ObjectNode technical) {
        final var expectedNames = new HashSet<String>();
        technical.fieldNames().forEachRemaining(expectedNames::add);
        expectedNames.addAll(fields.get("MAN"));
        if (!names(actual).equals(expectedNames)) {
            return "MEMBER_SET";
        }
        final var technicalKeys = technical.fieldNames();
        while (technicalKeys.hasNext()) {
            final var key = technicalKeys.next();
            if (!equivalentJson(technical.get(key), actual.get(key))) {
                return key;
            }
        }
        for (final var name : fields.get("MAN")) {
            final var wanted = input.path(name);
            final var observed = actual.get(name);
            if (wanted.isArray()) {
                if (!observed.isTextual() || !wanted.toString().equals(observed.textValue())) {
                    return name;
                }
            } else if (wanted.isTextual() && wanted.textValue().matches("[0-9]{4}-[0-9T:.+Z-]+")) {
                try {
                    final var expectedTime = OffsetDateTime.parse(wanted.textValue());
                    final var actualTime = OffsetDateTime.parse(observed.asText());
                    if (!expectedTime
                            .withNano(expectedTime.getNano() / 100 * 100)
                            .equals(actualTime)) {
                        return name;
                    }
                } catch (final java.time.DateTimeException invalid) {
                    return name;
                }
            } else if (wanted.isTextual() && wanted.textValue().matches("[0-9]+\\.[0-9]{8}")) {
                if (!observed.isNumber()
                        || new BigDecimal(wanted.textValue()).compareTo(observed.decimalValue())
                                != 0) {
                    return name;
                }
            } else if (!equivalentJson(wanted, observed)) {
                return name;
            }
        }
        return "";
    }

    private boolean collection(
            final ObjectNode input, final JsonNode actual, final ObjectNode technical) {
        final var expected = technical.deepCopy();
        final var dataExport = expected.putObject("dataExport");
        for (final var name : fields.get("COL")) {
            dataExport.set(name, field(input.get(name), true));
        }
        return equivalentJson(expected, actual);
    }

    /** JSON has one number kind; JVM IntNode/LongNode storage is not wire semantics. */
    private static boolean equivalentJson(final JsonNode expected, final JsonNode actual) {
        if (expected == null || actual == null) {
            return expected == actual;
        }
        if (expected.isNumber() && actual.isNumber()) {
            return expected.decimalValue().compareTo(actual.decimalValue()) == 0;
        }
        if (expected.isObject() && actual.isObject()) {
            if (!names(expected).equals(names(actual))) {
                return false;
            }
            for (final var name : names(expected)) {
                if (!equivalentJson(expected.get(name), actual.get(name))) {
                    return false;
                }
            }
            return true;
        }
        if (expected.isArray() && actual.isArray()) {
            if (expected.size() != actual.size()) {
                return false;
            }
            for (int index = 0; index < expected.size(); index++) {
                if (!equivalentJson(expected.get(index), actual.get(index))) {
                    return false;
                }
            }
            return true;
        }
        return expected.equals(actual);
    }

    public static Set<String> names(final JsonNode node) {
        if (node == null || !node.isObject() || node.size() > 128) {
            throw new IllegalArgumentException("QUAL_WIRE_FIELD_BOUND");
        }
        final var names = new HashSet<String>();
        node.fieldNames().forEachRemaining(names::add);
        return Set.copyOf(names);
    }

    public static ObjectNode lateral(final long supplement) throws IOException {
        if (supplement < 1) {
            throw new IllegalArgumentException("QUAL_WIRE_SUPPLEMENT_ID");
        }
        return lateral(read("analytic-laboratory/collection-supplement").path("data"), supplement);
    }

    private static ObjectNode lateral(final JsonNode input, final long supplement) {
        final var result = JsonNodeFactory.instance.objectNode();
        final Map<String, String> paths =
                Map.ofEntries(
                        Map.entry("request_hour", "/requestHour"),
                                Map.entry("vehicle_type_id", "/vehicleTypeId"),
                        Map.entry("customer_name", "/customer/name"),
                                Map.entry("customer_document", "/customer/cnpj"),
                        Map.entry("address_line", "/pickAddress/line1"),
                                Map.entry("address_number", "/pickAddress/number"),
                        Map.entry("address_complement", "/pickAddress/line2"),
                                Map.entry("branch_source_id", "/corporation/id"),
                        Map.entry("cancellation_user_id", "/cancellationUserId"),
                                Map.entry("destroy_reason", "/destroyReason"),
                        Map.entry("destroy_user_id", "/destroyUserId"),
                                Map.entry("status_updated_at", "/statusUpdatedAt"));
        for (final var entry : paths.entrySet()) {
            final var expected = field(input.at(entry.getValue()), false);
            expected.put("supplementId", supplement);
            result.set(entry.getKey(), expected);
        }
        return result;
    }
}
