package br.com.esl.etl.v2.plataforma.reconciliacao.exportacao;

import static br.com.esl.etl.v2.plataforma.reconciliacao.exportacao.ExportValidationSupport.require;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Full physical row multisets for exports whose root identity has not been established. */
public final class KeylessExactExportTable {
    public record Comparison(
            long apiRows,
            long uiRows,
            boolean fullTuplesEqual,
            long apiOnlyOccurrences,
            long uiOnlyOccurrences,
            List<Boolean> columnsEqual,
            List<Boolean> totalsEqual,
            List<Boolean> totalsReady,
            List<Boolean> exactDecimalTotalsEqual,
            List<Boolean> carrierTotalsEqual) {
        public boolean contentEqual() {
            return fullTuplesEqual
                    && totalsReady.stream().allMatch(Boolean::booleanValue)
                    && totalsEqual.stream().allMatch(Boolean::booleanValue);
        }
    }

    private record Total(
            BigDecimal decimal,
            BigInteger units,
            Map<ExactExportTable.Atom, Long> nonvalues,
            boolean ready) {}

    private static final Comparator<ExactExportTable.Atom> ATOM_ORDER =
            Comparator.comparing(ExactExportTable.Atom::type)
                    .thenComparing(ExactExportTable.Atom::value)
                    .thenComparing(ExactExportTable.Atom::carrier);
    private final List<ExactExportTable.Column> columns;
    private final ExportValidationSupport.Budget budget;
    private final Map<List<ExactExportTable.Atom>, Long> tuples = new HashMap<>();
    private final List<Map<ExactExportTable.Atom, Long>> bags = new ArrayList<>();
    private long rows;
    private boolean sealed;

    public KeylessExactExportTable(
            final List<ExactExportTable.Column> columns,
            final ExportValidationSupport.Budget budget) {
        require(
                columns != null && !columns.isEmpty() && columns.size() <= 256 && budget != null,
                "KEYLESS_EXPORT_MAPPING_BOUND");
        this.columns = List.copyOf(columns);
        this.budget = budget;
        this.columns.forEach(ignored -> bags.add(new HashMap<>()));
    }

    public void add(final List<ExactExportTable.Atom> values) {
        budget.check();
        require(
                !sealed && values != null && values.size() == columns.size() && rows < 12000,
                "KEYLESS_EXPORT_ROW_BOUND");
        final var row = List.copyOf(values);
        row.forEach(
                atom -> {
                    strictUtf8(atom.value());
                    strictUtf8(atom.carrier());
                });
        for (int index = 0; index < row.size(); index++) {
            budget.cell();
            final var atom = row.get(index);
            bags.get(index).merge(atom, 1L, Long::sum);
        }
        tuples.merge(row, 1L, Long::sum);
        rows++;
    }

    public void seal() {
        budget.check();
        require(!sealed, "KEYLESS_EXPORT_ALREADY_SEALED");
        sealed = true;
    }

    public Comparison compare(final KeylessExactExportTable right) {
        require(
                right != null && sealed && right.sealed && columns.equals(right.columns),
                "KEYLESS_EXPORT_TWO_SEALED_SAME_MAPPINGS");
        final List<Boolean> equalColumns = new ArrayList<>();
        final List<Boolean> equalTotals = new ArrayList<>();
        final List<Boolean> readyTotals = new ArrayList<>();
        final List<Boolean> decimalTotals = new ArrayList<>();
        final List<Boolean> carrierTotals = new ArrayList<>();
        for (int index = 0; index < columns.size(); index++) {
            budget.check();
            equalColumns.add(bags.get(index).equals(right.bags.get(index)));
            if (columns.get(index).total()) {
                final var leftTotal = total(index);
                final var rightTotal = right.total(index);
                final boolean ready =
                        leftTotal.ready()
                                && rightTotal.ready()
                                && leftTotal.nonvalues().equals(rightTotal.nonvalues());
                final boolean decimalEqual =
                        leftTotal.decimal().compareTo(rightTotal.decimal()) == 0;
                final boolean carrierEqual = leftTotal.units().equals(rightTotal.units());
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
        return new Comparison(
                rows,
                right.rows,
                tuples.equals(right.tuples),
                only(tuples, right.tuples),
                only(right.tuples, tuples),
                List.copyOf(equalColumns),
                List.copyOf(equalTotals),
                List.copyOf(readyTotals),
                List.copyOf(decimalTotals),
                List.copyOf(carrierTotals));
    }

    private long only(
            final Map<List<ExactExportTable.Atom>, Long> left,
            final Map<List<ExactExportTable.Atom>, Long> right) {
        long count = 0;
        for (final var entry : left.entrySet()) {
            budget.check();
            count += Math.max(0L, entry.getValue() - right.getOrDefault(entry.getKey(), 0L));
        }
        return count;
    }

    private Total total(final int index) {
        BigDecimal decimal = BigDecimal.ZERO;
        BigInteger units = BigInteger.ZERO;
        long numeric = 0;
        boolean qualified = true;
        final Map<ExactExportTable.Atom, Long> nonvalues = new HashMap<>();
        for (final var entry : bags.get(index).entrySet()) {
            budget.check();
            final var atom = entry.getKey();
            if ("NUMBER".equals(atom.type())) {
                final var value = new BigDecimal(atom.value());
                decimal = decimal.add(value.multiply(BigDecimal.valueOf(entry.getValue())));
                final String model = columns.get(index).totalKind();
                if ("BINARY64_UNITS_129".equals(model)) {
                    units =
                            units.add(
                                    value.toBigIntegerExact()
                                            .multiply(BigInteger.valueOf(entry.getValue())));
                } else if (!"DECIMAL_EXACT".equals(model)) {
                    units =
                            units.add(
                                    ExactBinary64.units(value)
                                            .multiply(BigInteger.valueOf(entry.getValue())));
                }
                qualified &=
                        !"INTEGER_EXACT".equals(model) || value.stripTrailingZeros().scale() <= 0;
                numeric += entry.getValue();
            } else {
                nonvalues.put(atom, entry.getValue());
                qualified &= List.of("ABSENT", "NULL", "BLANK").contains(atom.type());
            }
            require(
                    units.abs().bitLength() <= 2112
                            && decimal.unscaledValue().abs().bitLength() <= 4096,
                    "KEYLESS_EXPORT_TOTAL_SUM_BOUND");
        }
        return new Total(
                decimal.stripTrailingZeros(),
                units,
                Map.copyOf(nonvalues),
                qualified && numeric > 0);
    }

    public ObjectNode fullValueProjection() {
        require(sealed, "KEYLESS_EXPORT_SEALED_PROJECTION_REQUIRED");
        final var result =
                ExportValidationSupport.JSON
                        .createObjectNode()
                        .put("canonicalFormat", "KEYLESS_EXACT_TYPED_LENGTH_PREFIX_UTF8_V1")
                        .put(
                                "ordering",
                                "LEXICOGRAPHIC_JAVA_UTF16_TYPE_VALUE_CARRIER_THEN_TUPLE_LENGTH")
                        .put(
                                "framing",
                                "UTF8_BYTE_LENGTH_DECIMAL_COLON_BYTES;ATOM=TYPE,VALUE,CARRIER;"
                                        + "TUPLE=WIDTH,ATOMS;ENTRY=KEY,COUNT")
                        .put("rows", rows)
                        .put("completeAllValues", true)
                        .put("deduplicationPerformed", false)
                        .put("rootIdentityQualified", false)
                        .put("keylessPhysicalOccurrences", true);
        final var columnBags = result.putArray("columnBags");
        final var totals = result.putArray("totals");
        for (int index = 0; index < columns.size(); index++) {
            final var column = columns.get(index);
            columnBags
                    .addObject()
                    .put("index", index)
                    .put("name", column.name())
                    .set("bag", digestAtoms(bags.get(index)));
            if (column.total()) {
                final var aggregate = total(index);
                final var decimal = aggregate.decimal();
                final var denominator =
                        decimal.scale() > 0 ? BigInteger.TEN.pow(decimal.scale()) : BigInteger.ONE;
                final var numerator =
                        decimal.scale() < 0
                                ? decimal.unscaledValue()
                                        .multiply(BigInteger.TEN.pow(-decimal.scale()))
                                : decimal.unscaledValue();
                final var gcd = numerator.gcd(denominator);
                totals.addObject()
                        .put("index", index)
                        .put("name", column.name())
                        .put("model", column.totalKind())
                        .put("ready", aggregate.ready())
                        .put("exactDecimalNumerator", numerator.divide(gcd).toString())
                        .put("exactDecimalDenominator", denominator.divide(gcd).toString())
                        .put("sumOfEncodedCellUnits2PowMinus1074", aggregate.units().toString())
                        .set("nonvalueBag", digestAtoms(aggregate.nonvalues()));
            }
        }
        final var keys = new ArrayList<>(tuples.keySet());
        keys.sort(
                (left, right) -> {
                    for (int index = 0; index < Math.min(left.size(), right.size()); index++) {
                        final int compared = ATOM_ORDER.compare(left.get(index), right.get(index));
                        if (compared != 0) {
                            return compared;
                        }
                    }
                    return Integer.compare(left.size(), right.size());
                });
        final var digest = digest();
        for (final var tuple : keys) {
            budget.check();
            field(digest, Integer.toString(tuple.size()));
            tuple.forEach(atom -> atom(digest, atom));
            field(digest, tuples.get(tuple).toString());
        }
        result.set("fullTupleBag", descriptor(digest, tuples.size(), rows));
        return result;
    }

    private ObjectNode digestAtoms(final Map<ExactExportTable.Atom, Long> bag) {
        final var keys = new ArrayList<>(bag.keySet());
        keys.sort(ATOM_ORDER);
        final var digest = digest();
        long count = 0;
        for (final var key : keys) {
            budget.check();
            atom(digest, key);
            field(digest, bag.get(key).toString());
            count += bag.get(key);
        }
        return descriptor(digest, keys.size(), count);
    }

    private static MessageDigest digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (final java.security.NoSuchAlgorithmException unavailable) {
            throw new IllegalStateException(unavailable);
        }
    }

    private static void atom(final MessageDigest digest, final ExactExportTable.Atom atom) {
        field(digest, atom.type());
        field(digest, atom.value());
        field(digest, atom.carrier());
    }

    private static byte[] strictUtf8(final String value) {
        try {
            final ByteBuffer buffer =
                    StandardCharsets.UTF_8
                            .newEncoder()
                            .onMalformedInput(CodingErrorAction.REPORT)
                            .onUnmappableCharacter(CodingErrorAction.REPORT)
                            .encode(CharBuffer.wrap(value));
            final byte[] bytes = new byte[buffer.remaining()];
            buffer.get(bytes);
            return bytes;
        } catch (final CharacterCodingException invalid) {
            throw new IllegalArgumentException("KEYLESS_EXPORT_UNPAIRED_SURROGATE", invalid);
        }
    }

    private static void field(final MessageDigest digest, final String value) {
        final byte[] bytes = strictUtf8(value);
        digest.update(Integer.toString(bytes.length).getBytes(StandardCharsets.US_ASCII));
        digest.update((byte) ':');
        digest.update(bytes);
    }

    private static ObjectNode descriptor(
            final MessageDigest digest, final long keys, final long occurrences) {
        return ExportValidationSupport.JSON
                .createObjectNode()
                .put("sha256", java.util.HexFormat.of().formatHex(digest.digest()))
                .put("distinctKeys", keys)
                .put("occurrences", occurrences)
                .put("complete", true);
    }
}
