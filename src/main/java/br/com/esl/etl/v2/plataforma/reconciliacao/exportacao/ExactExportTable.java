package br.com.esl.etl.v2.plataforma.reconciliacao.exportacao;

import static br.com.esl.etl.v2.plataforma.reconciliacao.exportacao.ExportValidationSupport.require;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Typed occurrences, never an entity set. No null/blank/zero or string/number coercion. */
public final class ExactExportTable {
    public record Atom(String type, String value, String carrier) {
        public Atom(final String type, final String value) {
            this(type, value, "");
        }

        public Atom {
            require(
                    type != null
                            && value != null
                            && carrier != null
                            && carrier.length() <= 2048
                            && value.length() <= 32768,
                    "EXPORT_ATOM_BOUND");
            require(
                    List.of("ABSENT", "NULL", "BLANK", "TEXT", "NUMBER", "BOOLEAN").contains(type),
                    "EXPORT_ATOM_TYPE");
            if ("NUMBER".equals(type)) {
                require(value.length() <= 1024, "EXPORT_NUMBER_BOUND");
                final var number = new BigDecimal(value);
                require(Math.abs((long) number.scale()) <= 1024, "EXPORT_NUMBER_SCALE");
                // Retain the native lexeme in full tuples; numeric sums are a separate axis.
            }
            require(
                    !List.of("ABSENT", "NULL", "BLANK").contains(type) || value.isEmpty(),
                    "EXPORT_NONVALUE_PAYLOAD");
            require(
                    !"BOOLEAN".equals(type) || List.of("true", "false").contains(value),
                    "EXPORT_BOOLEAN_ATOM");
        }

        public static Atom json(final JsonNode value) {
            if (value == null || value.isMissingNode()) {
                return new Atom("ABSENT", "");
            }
            if (value.isNull()) {
                return new Atom("NULL", "");
            }
            if (value.isTextual()) {
                return new Atom("TEXT", value.textValue());
            }
            if (value.isNumber()) {
                return new Atom("NUMBER", value.asText());
            }
            require(value.isBoolean(), "EXPORT_NON_SCALAR_REQUIRES_QUALIFIED_MAPPING");
            return new Atom("BOOLEAN", value.asText());
        }
    }

    public record Column(
            String name, int apiColumn, int uiColumn, boolean total, String totalKind) {
        public Column(
                final String name, final int apiColumn, final int uiColumn, final boolean total) {
            this(name, apiColumn, uiColumn, total, total ? "DECIMAL_EXACT" : "NONE");
        }

        public Column {
            require(name != null && !name.isBlank() && name.length() <= 256, "EXPORT_COLUMN_NAME");
            require(
                    apiColumn > 0 && apiColumn <= 256 && uiColumn > 0 && uiColumn <= 256,
                    "EXPORT_COLUMN_INDEX");
            require(
                    totalKind != null
                            && (total
                                    ? List.of(
                                                    "DECIMAL_EXACT",
                                                    "INTEGER_EXACT",
                                                    "BINARY64_ENCODING129",
                                                    "BINARY64_UNITS_129")
                                            .contains(totalKind)
                                    : "NONE".equals(totalKind)),
                    "EXPORT_TOTAL_MODEL");
        }
    }

    public record Total(
            BigDecimal sum, BigInteger dyadicUnits, Map<Atom, Long> nonNumbers, boolean ready) {}

    public record Comparison(
            long apiRows,
            long uiRows,
            boolean fullTuplesEqual,
            long apiOnlyOccurrences,
            long uiOnlyOccurrences,
            List<Boolean> columnsEqual,
            List<Boolean> totalsEqual,
            List<Boolean> exactDecimalTotalsEqual,
            List<Boolean> carrierTotalsEqual,
            List<Boolean> totalsReady,
            List<Long> changedIdsByColumn,
            long changedIds,
            long changedMultiplicityIds,
            long apiOnlyIds,
            long uiOnlyIds,
            long apiDistinctIds,
            long uiDistinctIds) {
        public boolean contentEqual() {
            return fullTuplesEqual
                    && totalsReady.stream().allMatch(Boolean::booleanValue)
                    && totalsEqual.stream().allMatch(Boolean::booleanValue);
        }
    }

    private final List<Column> columns;
    private final int keyIndex;
    private final ExportValidationSupport.Budget budget;
    private final Map<List<Atom>, Long> tuples = new HashMap<>();
    private final List<Map<Atom, Long>> columnBags = new ArrayList<>();
    private final Map<Atom, Map<List<Atom>, Long>> byId = new HashMap<>();
    private long rows;
    private boolean sealed;

    public ExactExportTable(
            final List<Column> columns,
            final int keyIndex,
            final ExportValidationSupport.Budget budget) {
        require(
                !columns.isEmpty()
                        && columns.size() <= 256
                        && keyIndex >= 0
                        && keyIndex < columns.size(),
                "EXPORT_MAPPING_BOUND");
        this.columns = List.copyOf(columns);
        this.keyIndex = keyIndex;
        this.budget = budget;
        columns.forEach(ignored -> columnBags.add(new HashMap<>()));
    }

    public void add(final List<Atom> values) {
        budget.check();
        require(!sealed && values.size() == columns.size() && rows < 12000, "EXPORT_ROW_BOUND");
        final var row = List.copyOf(values);
        final var identity = row.get(keyIndex);
        // Group by the complete typed ID value. Carrier differences remain in every full tuple.
        final var key = new Atom(identity.type(), identity.value());
        require(
                !List.of("ABSENT", "NULL", "BLANK").contains(key.type()) && !key.value().isEmpty(),
                "EXPORT_ID_REQUIRED");
        for (int index = 0; index < row.size(); index++) {
            budget.cell();
            columnBags.get(index).merge(row.get(index), 1L, Long::sum);
        }
        tuples.merge(row, 1L, Long::sum);
        byId.computeIfAbsent(key, ignored -> new HashMap<>()).merge(row, 1L, Long::sum);
        rows++;
    }

    public void seal() {
        budget.check();
        require(!sealed, "EXPORT_ALREADY_SEALED");
        sealed = true;
    }

    private Total total(final int index) {
        BigDecimal sum = BigDecimal.ZERO;
        BigInteger units = BigInteger.ZERO;
        long numeric = 0;
        boolean qualified = true;
        final Map<Atom, Long> nonNumbers = new HashMap<>();
        for (final var entry : columnBags.get(index).entrySet()) {
            budget.check();
            if ("NUMBER".equals(entry.getKey().type())) {
                final var value = new BigDecimal(entry.getKey().value());
                sum = sum.add(value.multiply(BigDecimal.valueOf(entry.getValue())));
                if ("BINARY64_UNITS_129".equals(columns.get(index).totalKind())) {
                    units =
                            units.add(
                                    value.toBigIntegerExact()
                                            .multiply(BigInteger.valueOf(entry.getValue())));
                } else if (!"DECIMAL_EXACT".equals(columns.get(index).totalKind())) {
                    units =
                            units.add(
                                    ExactBinary64.units(value)
                                            .multiply(BigInteger.valueOf(entry.getValue())));
                }
                if ("INTEGER_EXACT".equals(columns.get(index).totalKind())
                        && value.stripTrailingZeros().scale() > 0) {
                    qualified = false;
                }
                numeric += entry.getValue();
            } else {
                nonNumbers.put(entry.getKey(), entry.getValue());
                qualified &= List.of("ABSENT", "NULL", "BLANK").contains(entry.getKey().type());
            }
            require(
                    units.abs().bitLength() <= 2112
                            && sum.unscaledValue().abs().bitLength() <= 4096,
                    "EXPORT_TOTAL_SUM_BOUND");
        }
        return new Total(
                sum.stripTrailingZeros(), units, Map.copyOf(nonNumbers), qualified && numeric > 0);
    }

    public Comparison compare(final ExactExportTable ui) {
        require(
                sealed && ui.sealed && columns.equals(ui.columns) && keyIndex == ui.keyIndex,
                "EXPORT_TWO_SEALED_SAME_MAPPINGS");
        final List<Boolean> equalColumns = new ArrayList<>();
        final List<Boolean> equalTotals = new ArrayList<>();
        final List<Boolean> decimalTotals = new ArrayList<>();
        final List<Boolean> carrierTotals = new ArrayList<>();
        final List<Boolean> readyTotals = new ArrayList<>();
        for (int index = 0; index < columns.size(); index++) {
            budget.check();
            equalColumns.add(columnBags.get(index).equals(ui.columnBags.get(index)));
            if (columns.get(index).total()) {
                final var left = total(index);
                final var right = ui.total(index);
                final boolean ready =
                        left.ready()
                                && right.ready()
                                && left.nonNumbers().equals(right.nonNumbers());
                final boolean decimalEqual = left.sum().compareTo(right.sum()) == 0;
                final boolean carrierEqual = left.dyadicUnits().equals(right.dyadicUnits());
                readyTotals.add(ready);
                decimalTotals.add(decimalEqual);
                carrierTotals.add(carrierEqual);
                equalTotals.add(
                        ready
                                && (List.of("BINARY64_ENCODING129", "BINARY64_UNITS_129")
                                                .contains(columns.get(index).totalKind())
                                        ? carrierEqual
                                        : decimalEqual));
            }
        }
        long changed = 0;
        long multiplicity = 0;
        final var changedColumns =
                new ArrayList<Long>(java.util.Collections.nCopies(columns.size(), 0L));
        for (final var entry : byId.entrySet()) {
            budget.check();
            final var other = ui.byId.get(entry.getKey());
            if (other != null && !entry.getValue().equals(other)) {
                changed++;
                if (occurrences(entry.getValue()) != occurrences(other)) {
                    multiplicity++;
                }
                for (int index = 0; index < columns.size(); index++) {
                    if (!keyedColumn(entry.getValue(), index).equals(keyedColumn(other, index))) {
                        changedColumns.set(index, changedColumns.get(index) + 1);
                    }
                }
            }
        }
        return new Comparison(
                rows,
                ui.rows,
                tuples.equals(ui.tuples),
                difference(tuples, ui.tuples),
                difference(ui.tuples, tuples),
                List.copyOf(equalColumns),
                List.copyOf(equalTotals),
                List.copyOf(decimalTotals),
                List.copyOf(carrierTotals),
                List.copyOf(readyTotals),
                List.copyOf(changedColumns),
                changed,
                multiplicity,
                byId.keySet().stream().filter(key -> !ui.byId.containsKey(key)).count(),
                ui.byId.keySet().stream().filter(key -> !byId.containsKey(key)).count(),
                byId.size(),
                ui.byId.size());
    }

    /**
     * Read-only zero-based bag query; it grants no total policy and leaves compare/Total unchanged.
     */
    boolean absentOnlyBagEquals(final ExactExportTable other, final int index) {
        require(
                sealed
                        && other.sealed
                        && columns.equals(other.columns)
                        && keyIndex == other.keyIndex
                        && index >= 0
                        && index < columns.size(),
                "EXPORT_TWO_SEALED_SAME_MAPPINGS");
        budget.check();
        other.budget.check();
        final var absent = new Atom("ABSENT", "");
        final var left = columnBags.get(index);
        final var right = other.columnBags.get(index);
        return rows > 0
                && other.rows > 0
                && left.size() == 1
                && right.size() == 1
                && left.getOrDefault(absent, 0L) == rows
                && right.getOrDefault(absent, 0L) == other.rows
                && left.equals(right);
    }

    /**
     * Mergeable typed nonvalue evidence for private shards; numerical accumulators remain in the
     * projection.
     */
    com.fasterxml.jackson.databind.node.ObjectNode totalAtomEvidence(final int index) {
        require(
                sealed && index >= 0 && index < columns.size() && columns.get(index).total(),
                "EXPORT_SEALED_TOTAL_REQUIRED");
        final var aggregate = total(index);
        final long nonvalues =
                aggregate.nonNumbers().values().stream().mapToLong(Long::longValue).sum();
        final long numeric = rows - nonvalues;
        final boolean nonvaluesQualified =
                aggregate.nonNumbers().keySet().stream()
                        .allMatch(atom -> List.of("ABSENT", "NULL", "BLANK").contains(atom.type()));
        final var result =
                ExportValidationSupport.JSON
                        .createObjectNode()
                        .put("numericOccurrences", numeric)
                        .put("nonvalueOccurrences", nonvalues)
                        .put(
                                "allAtomsQualified",
                                aggregate.ready() || numeric == 0 && nonvaluesQualified)
                        .put("absenceOnlyABSENT", absentOnlyBagEquals(this, index));
        final var values = result.putArray("nonvalueAtoms");
        final var keys = new ArrayList<>(aggregate.nonNumbers().keySet());
        keys.sort(ExactExportTable::compareAtoms);
        for (final var atom : keys) {
            budget.check();
            values.addObject()
                    .put("type", atom.type())
                    .put("value", atom.value())
                    .put("carrier", atom.carrier())
                    .put("occurrences", aggregate.nonNumbers().get(atom));
        }
        return result;
    }

    /** Complete deterministic digests, never samples. The comparison above still uses full keys. */
    public com.fasterxml.jackson.databind.node.ObjectNode fullValueProjection() {
        require(sealed, "EXPORT_SEALED_PROJECTION_REQUIRED");
        final var result = ExportValidationSupport.JSON.createObjectNode();
        result.put("canonicalFormat", "EXACT_TYPED_LENGTH_PREFIX_UTF8_V1");
        result.put("ordering", "LEXICOGRAPHIC_JAVA_UTF16_TYPE_VALUE_CARRIER_THEN_TUPLE_LENGTH");
        result.put(
                "framing",
                "UTF8_BYTE_LENGTH_DECIMAL_COLON_BYTES;ATOM=TYPE,VALUE,CARRIER;TUPLE=WIDTH,ATOMS;ENTRY=KEY,COUNT");
        result.put("rows", rows);
        result.put("distinctIds", byId.size());
        result.put("completeAllValues", true);
        result.put("deduplicationPerformed", false);
        final var bags = result.putArray("columnBags");
        final var totals = result.putArray("totals");
        for (int index = 0; index < columns.size(); index++) {
            final var column = columns.get(index);
            final var entry = bags.addObject().put("index", index).put("name", column.name());
            entry.set("bag", digestAtoms(columnBags.get(index)));
            if (column.total()) {
                final var total = total(index);
                final var decimal = total.sum().stripTrailingZeros();
                final var denominator =
                        decimal.scale() > 0 ? BigInteger.TEN.pow(decimal.scale()) : BigInteger.ONE;
                final var numerator =
                        decimal.scale() < 0
                                ? decimal.unscaledValue()
                                        .multiply(BigInteger.TEN.pow(-decimal.scale()))
                                : decimal.unscaledValue();
                final var gcd = numerator.gcd(denominator);
                final var value =
                        totals.addObject()
                                .put("index", index)
                                .put("name", column.name())
                                .put("model", column.totalKind())
                                .put("ready", total.ready())
                                .put("exactDecimalNumerator", numerator.divide(gcd).toString())
                                .put("exactDecimalDenominator", denominator.divide(gcd).toString())
                                .put(
                                        "sumOfEncodedCellUnits2PowMinus1074",
                                        total.dyadicUnits().toString());
                value.set("nonvalueBag", digestAtoms(total.nonNumbers()));
            }
        }
        result.set("fullTupleBag", digestTuples(tuples));
        final var keyed = digest();
        final var ids = digest();
        final var keys = new ArrayList<>(byId.keySet());
        keys.sort(ExactExportTable::compareAtoms);
        for (final var key : keys) {
            budget.check();
            atom(keyed, key);
            field(keyed, digestTuples(byId.get(key)).path("sha256").textValue());
            atom(ids, key);
            field(ids, Long.toString(occurrences(byId.get(key))));
        }
        result.set("keyedTupleBags", descriptor(keyed, byId.size(), rows));
        result.set("idMultiplicityBag", descriptor(ids, byId.size(), rows));
        return result;
    }

    private com.fasterxml.jackson.databind.node.ObjectNode digestAtoms(final Map<Atom, Long> bag) {
        final var digest = digest();
        final var keys = new ArrayList<>(bag.keySet());
        keys.sort(ExactExportTable::compareAtoms);
        long count = 0;
        for (final var key : keys) {
            budget.check();
            atom(digest, key);
            field(digest, bag.get(key).toString());
            count += bag.get(key);
        }
        return descriptor(digest, keys.size(), count);
    }

    private com.fasterxml.jackson.databind.node.ObjectNode digestTuples(
            final Map<List<Atom>, Long> bag) {
        final var digest = digest();
        final var keys = new ArrayList<>(bag.keySet());
        keys.sort(
                (left, right) -> {
                    for (int i = 0; i < Math.min(left.size(), right.size()); i++) {
                        final int compared = compareAtoms(left.get(i), right.get(i));
                        if (compared != 0) {
                            return compared;
                        }
                    }
                    return Integer.compare(left.size(), right.size());
                });
        long count = 0;
        for (final var key : keys) {
            budget.check();
            field(digest, Integer.toString(key.size()));
            key.forEach(value -> atom(digest, value));
            field(digest, bag.get(key).toString());
            count += bag.get(key);
        }
        return descriptor(digest, keys.size(), count);
    }

    private static int compareAtoms(final Atom left, final Atom right) {
        final int type = left.type().compareTo(right.type());
        final int value = left.value().compareTo(right.value());
        return type != 0 ? type : value != 0 ? value : left.carrier().compareTo(right.carrier());
    }

    private static java.security.MessageDigest digest() {
        try {
            return java.security.MessageDigest.getInstance("SHA-256");
        } catch (final java.security.NoSuchAlgorithmException unavailable) {
            throw new IllegalStateException(unavailable);
        }
    }

    private static void atom(final java.security.MessageDigest digest, final Atom value) {
        field(digest, value.type());
        field(digest, value.value());
        field(digest, value.carrier());
    }

    private static void field(final java.security.MessageDigest digest, final String value) {
        final var bytes = value.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        digest.update(
                Integer.toString(bytes.length)
                        .getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        digest.update((byte) ':');
        digest.update(bytes);
    }

    private static com.fasterxml.jackson.databind.node.ObjectNode descriptor(
            final java.security.MessageDigest digest, final long keys, final long occurrences) {
        return ExportValidationSupport.JSON
                .createObjectNode()
                .put("sha256", java.util.HexFormat.of().formatHex(digest.digest()))
                .put("distinctKeys", keys)
                .put("occurrences", occurrences)
                .put("complete", true);
    }

    private static long occurrences(final Map<List<Atom>, Long> bag) {
        return bag.values().stream().mapToLong(Long::longValue).sum();
    }

    private Map<Atom, Long> keyedColumn(final Map<List<Atom>, Long> rowsByKey, final int column) {
        final Map<Atom, Long> values = new HashMap<>();
        rowsByKey.forEach(
                (row, count) -> {
                    budget.check();
                    values.merge(row.get(column), count, Long::sum);
                });
        return values;
    }

    private static long difference(
            final Map<List<Atom>, Long> left, final Map<List<Atom>, Long> right) {
        return left.entrySet().stream()
                .mapToLong(
                        entry ->
                                Math.max(
                                        0,
                                        entry.getValue() - right.getOrDefault(entry.getKey(), 0L)))
                .sum();
    }
}
