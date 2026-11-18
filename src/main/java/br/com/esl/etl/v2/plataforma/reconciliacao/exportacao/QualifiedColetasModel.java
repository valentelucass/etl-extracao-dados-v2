package br.com.esl.etl.v2.plataforma.reconciliacao.exportacao;

import static br.com.esl.etl.v2.plataforma.reconciliacao.exportacao.ExactTaggedValues.canonical;
import static br.com.esl.etl.v2.plataforma.reconciliacao.exportacao.ExactTaggedValues.decode;
import static br.com.esl.etl.v2.plataforma.reconciliacao.exportacao.ExactTaggedValues.encode;
import static br.com.esl.etl.v2.plataforma.reconciliacao.exportacao.ExactTaggedValues.hash;
import static br.com.esl.etl.v2.plataforma.reconciliacao.exportacao.ExactTaggedValues.integer;
import static br.com.esl.etl.v2.plataforma.reconciliacao.exportacao.ExactTaggedValues.list;
import static br.com.esl.etl.v2.plataforma.reconciliacao.exportacao.ExactTaggedValues.map;
import static br.com.esl.etl.v2.plataforma.reconciliacao.exportacao.ExactTaggedValues.obj;
import static br.com.esl.etl.v2.plataforma.reconciliacao.exportacao.ExactTaggedValues.plus;
import static br.com.esl.etl.v2.plataforma.reconciliacao.exportacao.ExactTaggedValues.string;
import static br.com.esl.etl.v2.plataforma.reconciliacao.exportacao.ExactTaggedValues.truth;
import static br.com.esl.etl.v2.plataforma.reconciliacao.exportacao.ExactTaggedValues.tuple;
import static br.com.esl.etl.v2.plataforma.reconciliacao.exportacao.ExportValidationSupport.require;

import br.com.esl.etl.v2.plataforma.reconciliacao.exportacao.ExactTaggedValues.Tuple;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Integer-only permanent decisions ported from civil63/precision42/causal8/encoding16. */
public final class QualifiedColetasModel {
    private static final Set<String> DATES = Set.of("item_124818", "item_123121", "item_123134");
    private static final Set<String> TIMES = Set.of("item_135309", "item_131395");
    private static final Set<String> DECIMALS = Set.of("item_135308", "item_123123", "item_123124");
    private static final LocalDate EPOCH = LocalDate.of(1899, 12, 30);
    private static final DateTimeFormatter SECOND =
            DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss");

    private QualifiedColetasModel() {}

    static boolean carrierRole(final String kind, final String carrier) {
        // Binary64 compares the numeric literal, with no decimal display rounding.
        // The frozen precision/encoding model admits a resolved General format.
        if ("decimal".equals(kind)
                && carrier != null
                && carrier.startsWith("XLSX:1900:builtin:0:")
                && carrier.endsWith(":fmtId=0")) {
            return true;
        }
        if (carrier == null
                || !carrier.startsWith("XLSX:1900:")
                || !carrier.contains(":apply=1:")) {
            return false;
        }
        return switch (kind) {
            case "date" ->
                    carrier.startsWith("XLSX:1900:DD/MM/YYYY:") && carrier.endsWith(":fmtId=165");
            case "timestamp" ->
                    carrier.startsWith("XLSX:1900:DD/MM/YYYY HH:MM:SS:")
                            && carrier.endsWith(":fmtId=164");
            case "decimal" ->
                    carrier.startsWith("XLSX:1900:0.00:")
                            || carrier.startsWith("XLSX:1900:#,##0.00:")
                            || carrier.startsWith("XLSX:1900:builtin:2:")
                            || carrier.startsWith("XLSX:1900:builtin:4:");
            default -> false;
        };
    }

    /**
     * Inputs only. Unknown operations fail; there is no expected-result argument or vector lookup.
     */
    public static JsonNode evaluate(final String operation, final JsonNode tagged)
            throws Exception {
        final var args = map(decode(tagged));
        final Object value;
        try {
            value =
                    switch (operation) {
                        case "precision._ratio" -> {
                            final var ratio = ratio(args.get("lexeme"));
                            yield tuple(ratio.numerator(), ratio.denominator());
                        }
                        case "precision._serial_wall_second" ->
                                wallSecond(
                                        integer(args.get("numerator"), "PRECISION_INTEGER_RATIO"),
                                        integer(
                                                args.get("denominator"),
                                                "PRECISION_INTEGER_RATIO"));
                        case "precision.binary64_positive_ratio_bits" ->
                                ExactBinary64.positiveRatioBits(
                                        integer(
                                                args.get("numerator"),
                                                "ROOT_BINARY64_BOUNDED_POSITIVE_INTEGER_RATIO"),
                                        integer(
                                                args.get("denominator"),
                                                "ROOT_BINARY64_BOUNDED_POSITIVE_INTEGER_RATIO"));
                        case "precision.canonical_whole_second_encoding" ->
                                canonicalSecond(
                                        string(args.get("lexeme")), string(args.get("civil")));
                        case "encoding.enc_decimal_bits" -> {
                            final var ratio = ratio(args.get("lexeme"));
                            yield tuple(
                                    ExactBinary64.ratioBits(
                                            ratio.numerator(),
                                            ratio.denominator(),
                                            string(args.get("lexeme")).startsWith("-")),
                                    ratio.numerator(),
                                    ratio.denominator());
                        }
                        case "encoding.enc_ratio_bits" ->
                                ExactBinary64.ratioBits(
                                        integer(
                                                args.get("numerator"),
                                                "ENCODING_INTEGER_RATIO_REQUIRED"),
                                        integer(
                                                args.get("denominator"),
                                                "ENCODING_INTEGER_RATIO_REQUIRED"),
                                        truth(args.get("negative_zero")));
                        case "encoding.enc_dyadic_units" ->
                                ExactBinary64.dyadicUnits(string(args.get("bits")));
                        case "encoding.enc_dyadic_pair" -> dyadicPair(args.get("units"));
                        case "civil._mask_add" -> mask(args.get("mask"), args.get("column"));
                        case "civil.civil_decision" ->
                                civil(
                                        string(args.get("item")),
                                        map(args.get("atom")),
                                        string(args.get("side")),
                                        map(args.get("context")),
                                        nullableMap(args.get("cell")),
                                        args.get("cell_proof"),
                                        args.get("date_null_blank_items"));
                        case "civil.null_omitted_decision" -> omitted(args);
                        case "precision.cell_style" -> {
                            scope(args);
                            yield style(
                                    string(args.get("item")),
                                    map(args.get("atom")),
                                    map(args.get("context")),
                                    nullableMap(args.get("cell")),
                                    args.get("proof"));
                        }
                        case "precision.timestamp_decision" -> {
                            scope(args);
                            yield timestamp(
                                    string(args.get("item")),
                                    map(args.get("atom")),
                                    string(args.get("side")),
                                    map(args.get("context")),
                                    nullableMap(args.get("cell")),
                                    args.get("proof"),
                                    args.get("enabled_items"));
                        }
                        case "precision._delta" ->
                                delta(
                                        map(args.get("left")),
                                        map(args.get("right")),
                                        top(args.get("top_n")));
                        case "precision.compare_timestamp_bags" -> timestampBags(args);
                        default ->
                                throw new IllegalStateException("DIFFERENTIAL_UNKNOWN_OPERATION");
                    };
        } catch (final IllegalArgumentException refused) {
            final String reason = refused.getMessage();
            require(
                    reason != null && reason.matches("[A-Z0-9_]{1,120}"),
                    "DIFFERENTIAL_UNEXPECTED_REFUSAL");
            return encode(obj("status", "REFUSED", "reason", reason));
        }
        return encode(obj("status", "RETURNED", "value", value));
    }

    static String sourceCivil(final String literal) {
        require(
                literal != null && literal.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}"),
                "EXACT_ISO_DATE_ONLY_REQUIRED_NO_TRIM_OR_TIMESTAMP_TRUNCATION");
        try {
            final var day = LocalDate.parse(literal);
            require(day.getYear() >= 1 && day.getYear() <= 9999, "INVALID_ISO_CIVIL_DATE");
            return day.toString();
        } catch (final java.time.DateTimeException invalid) {
            throw new IllegalArgumentException("INVALID_ISO_CIVIL_DATE");
        }
    }

    static String sourceTimestamp(final String literal) {
        require(
                literal != null
                        && literal.matches(
                                "[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}(\\.[0-9]{1,128})?-03:00"),
                "PRECISION_EXACT_LOCAL_ISO_MINUS03_REQUIRED");
        try {
            sourceCivil(literal.substring(0, 10));
            return LocalDateTime.parse(literal.substring(0, 19)).format(SECOND);
        } catch (final java.time.DateTimeException | IllegalArgumentException invalid) {
            throw new IllegalArgumentException("PRECISION_INVALID_LOCAL_CIVIL_TIMESTAMP");
        }
    }

    static String nativeTimestamp(final String lexeme) {
        final var ratio = ExactBinary64.ratio(lexeme);
        return wallSecond(ratio.numerator(), ratio.denominator());
    }

    static String nativeCivil(final String lexeme) {
        require(
                lexeme != null
                        && lexeme.length() <= 128
                        && lexeme.matches("-?(?:0|[1-9][0-9]*)(?:\\.[0-9]+)?(?:[eE][+-]?[0-9]+)?"),
                "EXACT_BOUNDED_SERIAL_LEXEME_REQUIRED");
        final BigDecimal serial;
        try {
            serial = new BigDecimal(lexeme);
        } catch (final NumberFormatException invalid) {
            throw new IllegalArgumentException("SERIAL_DECIMAL_EXPONENT_UNAVAILABLE");
        }
        require(
                serial.compareTo(BigDecimal.valueOf(61)) >= 0
                        && serial.compareTo(BigDecimal.valueOf(2958465)) <= 0
                        && serial.stripTrailingZeros().scale() <= 0,
                "INTEGRAL_SERIAL_61_TO_2958465_ONLY_NO_FRACTION_OR_FAKE_DAY60");
        return EPOCH.plusDays(serial.longValueExact()).toString();
    }

    private static ExactBinary64.Ratio ratio(final Object lexeme) {
        require(lexeme instanceof String, "PRECISION_EXACT_NUMERIC_LEXEME128");
        return ExactBinary64.ratio((String) lexeme);
    }

    private static String wallSecond(final BigInteger numerator, final BigInteger denominator) {
        require(
                numerator.compareTo(denominator.multiply(BigInteger.valueOf(61))) >= 0
                        && numerator.compareTo(denominator.multiply(BigInteger.valueOf(2958466)))
                                < 0,
                "PRECISION_SERIAL_1900_RANGE_EXCLUDES_FAKE60");
        require(denominator.signum() > 0, "PRECISION_SERIAL_1900_RANGE_EXCLUDES_FAKE60");
        final var seconds =
                numerator
                        .multiply(BigInteger.valueOf(172800))
                        .add(denominator)
                        .divide(denominator.shiftLeft(1));
        final long maximum =
                java.time.temporal.ChronoUnit.DAYS.between(EPOCH, LocalDate.of(9999, 12, 31))
                                * 86400
                        + 86399;
        require(
                seconds.signum() >= 0 && seconds.compareTo(BigInteger.valueOf(maximum)) <= 0,
                "PRECISION_ROUNDING_OUTSIDE_DATETIME_RANGE");
        return EPOCH.atStartOfDay().plusSeconds(seconds.longValueExact()).format(SECOND);
    }

    private static Map<Object, Object> canonicalSecond(final String lexeme, final String civil) {
        final var ratio = ExactBinary64.ratio(lexeme);
        final var moment = LocalDateTime.parse(civil, SECOND);
        final long seconds =
                java.time.temporal.ChronoUnit.SECONDS.between(EPOCH.atStartOfDay(), moment);
        final String nativeBits =
                ExactBinary64.positiveRatioBits(ratio.numerator(), ratio.denominator());
        final String canonicalBits =
                ExactBinary64.positiveRatioBits(
                        BigInteger.valueOf(seconds), BigInteger.valueOf(86400));
        return obj(
                "nativeWholeSecondCanonicalBinary64",
                nativeBits.equals(canonicalBits),
                "nativeBinary64Bits",
                nativeBits,
                "wholeSecondCanonicalBinary64Bits",
                canonicalBits,
                "canonicalEncodingModel",
                "IEEE754_BINARY64_NEAREST_EVEN_FROM_EXACT_RATIOS",
                "epsilonUsed",
                false,
                "floatArithmeticUsed",
                false);
    }

    private static Tuple dyadicPair(final Object value) {
        final var units = integer(value, "ENCODING_BOUNDED_EXACT_CELL_SUM");
        require(units.abs().bitLength() <= 2112, "ENCODING_BOUNDED_EXACT_CELL_SUM");
        if (units.signum() == 0) {
            return tuple(BigInteger.ZERO, BigInteger.ZERO);
        }
        final int remove = Math.min(1074, units.abs().getLowestSetBit());
        return tuple(units.shiftRight(remove), BigInteger.valueOf(1074 - remove));
    }

    private static BigInteger mask(final Object maskValue, final Object columnValue) {
        final var mask = integer(maskValue, "VALID_31BIT_ROW_MASK");
        require(mask.signum() >= 0 && mask.bitLength() <= 31, "VALID_31BIT_ROW_MASK");
        final var column = integer(columnValue, "CELL_WITHIN_BOUND_31_COLUMNS");
        require(
                column.compareTo(BigInteger.ONE) >= 0
                        && column.compareTo(BigInteger.valueOf(31)) <= 0,
                "CELL_WITHIN_BOUND_31_COLUMNS");
        final int bit = column.intValue() - 1;
        require(!mask.testBit(bit), "UNIQUE_XML_CELL_IN_ROW");
        return mask.setBit(bit);
    }

    private static Map<Object, Object> civil(
            final String item,
            final Map<Object, Object> atom,
            final String side,
            final Map<Object, Object> context,
            final Map<Object, Object> cell,
            final Object proof,
            final Object allowedValue) {
        require(
                DATES.contains(item) && Set.of("SOURCE", "ESL").contains(side),
                "ONLY_THREE_CIVIL_DATE_FIELDS");
        final var allowed = list(allowedValue);
        require(
                allowed.size() <= 3 && DATES.containsAll(allowed),
                "EXPLICIT_DATE_NULL_BLANK_FIELD_ALLOWLIST");
        require(
                "1900".equals(map(context.get("epoch")).get("effectiveDateSystem")),
                "VERIFIED_1900_CONTEXT_REQUIRED");
        explicit(atom, "EXPLICIT_ORIGINAL_ATOM");
        final var base =
                obj(
                        "item",
                        item,
                        "originalAtom",
                        atom,
                        "available",
                        false,
                        "key",
                        null,
                        "policyApproved",
                        false,
                        "originalAtomUnchanged",
                        true,
                        "cellProof",
                        proof);
        final Object value = atom.get("value");
        if (allowed.contains(item) && "SOURCE".equals(side) && nullAtom(atom)) {
            return plus(
                    base,
                    "available",
                    true,
                    "key",
                    tuple("DATE_NULL_OR_VERIFIED_PRESENT_BLANK", item),
                    "reason",
                    "EXPLICIT_SOURCE_NULL_UNDER_FIELD_SPECIFIC_DATE_BLANK_POLICY",
                    "interpretedBy",
                    "ROOT_PROPOSED_EXPORT_PRESENTATION_POLICY");
        }
        if (!"VALUE".equals(atom.get("presence"))) {
            require(value == null, "NONVALUE_WITHOUT_PAYLOAD");
            return plus(
                    base,
                    "available",
                    true,
                    "key",
                    tuple("ORIGINAL_STRICT_NONVALUE", atom.get("presence"), atom.get("type")),
                    "reason",
                    "PRESENCE_AND_NATIVE_TYPE_UNCHANGED_NO_EQUIVALENCE");
        }
        if ("SOURCE".equals(side)) {
            if (!"string".equals(atom.get("type")) || !(value instanceof String literal)) {
                return plus(
                        base,
                        "reason",
                        "EXACT_ISO_DATE_ONLY_REQUIRED_NO_TRIM_OR_TIMESTAMP_TRUNCATION");
            }
            try {
                return plus(
                        base,
                        "available",
                        true,
                        "key",
                        tuple("CIVIL_DATE", sourceCivil(literal)),
                        "reason",
                        "EXACT_ISO_CIVIL_VALUE");
            } catch (final IllegalArgumentException invalid) {
                return plus(base, "reason", invalid.getMessage());
            }
        }
        if (cell == null || proof == null) {
            return plus(base, "reason", "PRESENT_CELL_AND_VERIFIED_REFERENCE_REQUIRED");
        }
        final var header = map(map(context.get("headers")).get(item));
        if (!java.util.Objects.equals(cell.get("sheet"), context.get("sheet"))
                || !java.util.Objects.equals(cell.get("column"), header.get("column"))) {
            return plus(base, "reason", "EXACT_BOUND_DATE_COLUMN_REQUIRED");
        }
        final var attributes = map(cell.get("xmlAttributes"));
        final Object styleLiteral = attributes.getOrDefault("s", "0");
        final var xf = xf(context, styleLiteral);
        final Object fmt = xf.get("numFmtId");
        final Object code = map(context.get("formats")).get(fmt);
        if ("PRESENT_BLANK_XML_CELL".equals(cell.get("nativeState"))) {
            final boolean noPayload = noPayload(cell, "v", "f", "is", "opaque");
            final var blank =
                    obj(
                            "nativeState",
                            cell.get("nativeState"),
                            "styleIndexLiteral",
                            styleLiteral,
                            "numFmtId",
                            fmt,
                            "formatCode",
                            code,
                            "noValueFormulaInlineOrOpaque",
                            noPayload,
                            "stylesPin",
                            context.get("stylesPin"),
                            "epochMetadataPin",
                            context.get("inventoryPin"));
            final boolean verified =
                    "VALUE".equals(atom.get("presence"))
                            && "excel:present-blank".equals(atom.get("type"))
                            && "".equals(value)
                            && rowInRange(cell.get("row"), context.get("physicalRows"))
                            && numericType(attributes)
                            && "165".equals(fmt)
                            && "DD/MM/YYYY".equals(code)
                            && "1".equals(xf.get("applyNumberFormat"))
                            && noPayload;
            if (allowed.contains(item) && verified) {
                return plus(
                        base,
                        "available",
                        true,
                        "key",
                        tuple("DATE_NULL_OR_VERIFIED_PRESENT_BLANK", item),
                        "reason",
                        "VERIFIED_PRESENT_BLANK_DATE_CELL_UNDER_FIELD_SPECIFIC_POLICY",
                        "blankEvidence",
                        blank,
                        "interpretedBy",
                        "ROOT_PROPOSED_EXPORT_PRESENTATION_POLICY");
            }
            return plus(
                    base,
                    "reason",
                    allowed.contains(item)
                            ? "PRESENT_BLANK_DATE_POLICY_EVIDENCE_INCOMPLETE"
                            : "PRESENT_BLANK_REQUIRES_SEPARATE_DATE_NULL_POLICY",
                    "blankEvidence",
                    blank);
        }
        if (!"NUMERIC_LITERAL_XML".equals(cell.get("nativeState"))
                || !noPayload(cell, "f", "opaque", "is")
                || !numericType(attributes)) {
            return plus(base, "reason", "BOUND_NUMERIC_LITERAL_WITHOUT_FORMULA_OR_OPAQUE_REQUIRED");
        }
        final var values = sequenceOrEmpty(cell.get("v"));
        if (values.size() != 1
                || !java.util.Objects.equals(map(values.get(0)).get("characterData"), value)
                || !"number".equals(atom.get("type"))) {
            return plus(base, "reason", "ORIGINAL_NATIVE_NUMERIC_ATOM_AND_XML_VALUE_MUST_MATCH");
        }
        if (!resolvedStyle(context, styleLiteral)) {
            return plus(base, "reason", "CELL_STYLE_INDEX_NOT_RESOLVED");
        }
        if (!"165".equals(fmt)
                || !"DD/MM/YYYY".equals(code)
                || !"1".equals(xf.get("applyNumberFormat"))) {
            return plus(base, "reason", "EXACT_DATE_ONLY_STYLE_NOT_VERIFIED");
        }
        try {
            final String day = nativeCivil(string(value));
            return plus(
                    base,
                    "available",
                    true,
                    "key",
                    tuple("CIVIL_DATE", day),
                    "reason",
                    "EXACT_1900_INTEGRAL_DATE_ONLY_VALUE",
                    "styleEvidence",
                    obj(
                            "styleIndexLiteral",
                            styleLiteral,
                            "styleIndex",
                            new BigInteger((String) styleLiteral),
                            "numFmtId",
                            fmt,
                            "formatCode",
                            code,
                            "stylesPin",
                            context.get("stylesPin"),
                            "epochMetadataPin",
                            context.get("inventoryPin")));
        } catch (final IllegalArgumentException invalid) {
            return plus(base, "reason", invalid.getMessage());
        }
    }

    private static Map<Object, Object> style(
            final String item,
            final Map<Object, Object> atom,
            final Map<Object, Object> context,
            final Map<Object, Object> cell,
            final Object proof) {
        require(TIMES.contains(item) || DECIMALS.contains(item), "PRECISION_ONLY_FIVE_FIELDS");
        require(
                atom.keySet().equals(Set.of("presence", "type", "value")),
                "PRECISION_EXPLICIT_ORIGINAL_ATOM");
        final var result =
                obj(
                        "available",
                        false,
                        "reason",
                        "PRECISION_PRESENT_VERIFIED_CELL_REQUIRED",
                        "nativeState",
                        cell == null ? null : cell.get("nativeState"),
                        "styleIndexLiteral",
                        null,
                        "numFmtId",
                        null,
                        "formatCode",
                        null,
                        "applyNumberFormat",
                        null,
                        "numericLiteralVerified",
                        false,
                        "twoPlaceFormatVerified",
                        false,
                        "timestamp164Verified",
                        false,
                        "cellRecordSha256",
                        proof == null ? null : map(proof).get("recordSha256"),
                        "numericConversionApplied",
                        false,
                        "policyApproved",
                        false);
        if (cell == null || !truth(proof)) {
            return result;
        }
        final var header = map(map(context.get("allHeaders31")).get(item));
        if (!java.util.Objects.equals(cell.get("sheet"), context.get("sheet"))
                || !java.util.Objects.equals(cell.get("column"), header.get("column"))
                || !rowInRange(cell.get("row"), context.get("physicalRows"))) {
            return plus(result, "reason", "PRECISION_BOUND_COLUMN_SHEET_ROW_REQUIRED");
        }
        final var attributes =
                cell.containsKey("xmlAttributes") ? map(cell.get("xmlAttributes")) : obj();
        final Object literal = attributes.getOrDefault("s", "0");
        result.put("styleIndexLiteral", literal);
        if (!resolvedStyle(context, literal)) {
            return plus(result, "reason", "PRECISION_STYLE_INDEX_UNRESOLVED");
        }
        final var xf = xf(context, literal);
        final Object fmt = xf.get("numFmtId");
        Object code = map(context.get("formats")).get(fmt);
        final var builtins = Map.of("2", "0.00", "4", "#,##0.00");
        if (builtins.containsKey(fmt)) {
            if (code != null && !code.equals(builtins.get(fmt))) {
                return plus(
                        result,
                        "reason",
                        "PRECISION_BUILTIN_FORMAT_CONTRADICTION",
                        "numFmtId",
                        fmt);
            }
            code = builtins.get(fmt);
        }
        if (code != null && (!(code instanceof String text) || text.length() > 512)) {
            return plus(result, "reason", "PRECISION_FORMAT_CODE_BOUND", "numFmtId", fmt);
        }
        final var values = sequenceOrEmpty(cell.get("v"));
        final boolean numeric =
                "VALUE".equals(atom.get("presence"))
                        && "number".equals(atom.get("type"))
                        && atom.get("value") instanceof String
                        && "NUMERIC_LITERAL_XML".equals(cell.get("nativeState"))
                        && numericType(attributes)
                        && noPayload(cell, "f", "is", "opaque")
                        && values.size() == 1
                        && java.util.Objects.equals(
                                map(values.get(0)).get("characterData"), atom.get("value"));
        final Object apply = xf.get("applyNumberFormat");
        return plus(
                result,
                "available",
                true,
                "reason",
                "PRECISION_NATIVE_STYLE_RESOLVED",
                "numFmtId",
                fmt,
                "formatCode",
                code,
                "applyNumberFormat",
                apply,
                "numericLiteralVerified",
                numeric,
                "twoPlaceFormatVerified",
                "1".equals(apply)
                        && (builtins.containsKey(fmt)
                                || "0.00".equals(code)
                                || "#,##0.00".equals(code)),
                "timestamp164Verified",
                "1".equals(apply) && "164".equals(fmt) && "DD/MM/YYYY HH:MM:SS".equals(code),
                "formatSource",
                builtins.containsKey(fmt)
                        ? "BUILTIN2_OR4"
                        : "PINNED_EXPLICIT_FORMAT_OR_UNRESOLVED_BUILTIN");
    }

    private static Map<Object, Object> timestamp(
            final String item,
            final Map<Object, Object> atom,
            final String side,
            final Map<Object, Object> context,
            final Map<Object, Object> cell,
            final Object proof,
            final Object enabledValue) {
        require(
                TIMES.contains(item) && Set.of("SOURCE", "ESL").contains(side),
                "PRECISION_ONLY_TWO_TIMESTAMPS");
        final var enabled = list(enabledValue);
        require(
                enabled.size() <= 2 && TIMES.containsAll(enabled),
                "PRECISION_EXPLICIT_TIMESTAMP_ALLOWLIST");
        explicit(atom, "PRECISION_EXPLICIT_ORIGINAL_ATOM");
        final var result =
                obj(
                        "item",
                        item,
                        "available",
                        false,
                        "key",
                        null,
                        "originalAtomUnchanged",
                        true,
                        "originalAtomSha256",
                        hash(atom),
                        "policyApproved",
                        false,
                        "UTCConversionApplied",
                        false,
                        "trimOrLocaleApplied",
                        false);
        if (!enabled.contains(item)) {
            return plus(result, "reason", "PRECISION_TIMESTAMP_POLICY_NOT_ENABLED");
        }
        if (!"VALUE".equals(atom.get("presence"))) {
            require(atom.get("value") == null, "PRECISION_NONVALUE_WITHOUT_PAYLOAD");
            return plus(
                    result,
                    "available",
                    true,
                    "key",
                    tuple("ORIGINAL_STRICT_NONVALUE", atom.get("presence"), atom.get("type")),
                    "reason",
                    "PRECISION_NULL_ABSENT_UNCHANGED_NO_EQUIVALENCE");
        }
        if ("SOURCE".equals(side)) {
            final Object value = atom.get("value");
            if (!"string".equals(atom.get("type"))
                    || !(value instanceof String literal)
                    || literal.length() > 256) {
                return plus(result, "reason", "PRECISION_EXACT_LOCAL_ISO_MINUS03_REQUIRED");
            }
            try {
                final String civil = sourceTimestamp(literal);
                final String fraction =
                        literal.charAt(19) == '.'
                                ? literal.substring(20, literal.length() - 6)
                                : null;
                return plus(
                        result,
                        "available",
                        true,
                        "key",
                        tuple("LOCAL_WALL_SECOND", item, civil),
                        "reason",
                        "PRECISION_SOURCE_INTEGER_SECOND_COMPONENT",
                        "sourceOffsetLiteral",
                        "-03:00",
                        "sourceFractionLiteral",
                        fraction,
                        "sourceFractionIsPreservedExtraInformation",
                        true,
                        "sourceFractionRounded",
                        false);
            } catch (final IllegalArgumentException invalid) {
                return plus(result, "reason", invalid.getMessage());
            }
        }
        if (!"1900".equals(map(context.get("epoch")).get("effectiveDateSystem"))) {
            return plus(result, "reason", "PRECISION_VERIFIED_1900_EPOCH_REQUIRED");
        }
        final var style = style(item, atom, context, cell, proof);
        if (!truth(style.get("available"))
                || !truth(style.get("numericLiteralVerified"))
                || !truth(style.get("timestamp164Verified"))) {
            return plus(
                    result,
                    "reason",
                    "PRECISION_VERIFIED164_NUMERIC_TIMESTAMP_REQUIRED",
                    "style",
                    style);
        }
        try {
            final String lexeme = string(atom.get("value"));
            final String civil = nativeTimestamp(lexeme);
            final var answer =
                    plus(
                            result,
                            "available",
                            true,
                            "key",
                            tuple("LOCAL_WALL_SECOND", item, civil),
                            "reason",
                            "PRECISION_EXACT_SERIAL_PROJECTED_TO_DISPLAY_SECOND",
                            "style",
                            style,
                            "projection",
                            "FLOOR_EXACT_SERIAL_TIMES86400_PLUS_ONE_HALF",
                            "halfTiePolicy",
                            "TOWARD_LATER_SECOND_FOR_POSITIVE_SERIAL",
                            "rendererEquivalenceProven",
                            false,
                            "originalExactNumericComparisonPreserved",
                            true);
            answer.putAll(canonicalSecond(lexeme, civil));
            return answer;
        } catch (final IllegalArgumentException invalid) {
            return plus(result, "reason", invalid.getMessage(), "style", style);
        }
    }

    private static Map<Object, Object> omitted(final Map<Object, Object> args) {
        final var context = map(args.get("context"));
        require(
                truth(map(args.get("index_lease")).get("$sameContextLease")),
                "ONE_SHARED_CONTEXT_AND_LEASE");
        final String item = string(args.get("item"));
        final String side = string(args.get("side"));
        final var headers = map(context.get("allHeaders31"));
        require(
                list(args.get("allowed_items")).contains(item)
                        && headers.containsKey(item)
                        && Set.of("SOURCE", "ESL").contains(side),
                "NARROW_NULL_OMISSION_SUBJECT_REQUIRED");
        final var index = nullableMap(args.get("omission_index"));
        final var header = map(headers.get(item));
        final var rootHeader = map(headers.get("item_221149"));
        require(
                index != null
                        && Boolean.TRUE.equals(index.get("all31HeadersVerified"))
                        && "31BIT_COLUMN_MASK_PER_XML_ROW".equals(index.get("coordinateEncoding"))
                        && list(index.get("verifiedItems")).contains(item)
                        && list(index.get("verifiedColumns")).contains(header.get("column"))
                        && list(index.get("verifiedColumns")).contains(rootHeader.get("column")),
                "OMISSION_POLICY_COLUMN_MUST_BE_ACTUALLY_INDEXED");
        final var atom = map(args.get("atom"));
        final var original =
                obj(
                        "available",
                        true,
                        "key",
                        tuple(
                                "ORIGINAL_STRICT_ATOM",
                                new String(
                                        canonical(atom),
                                        java.nio.charset.StandardCharsets.US_ASCII)),
                        "originalAtom",
                        atom,
                        "policyApproved",
                        false,
                        "interpretedBy",
                        "ROOT_PROPOSED_EXPORT_PRESENTATION_POLICY");
        final var shared = tuple("EXPLICIT_SOURCE_NULL_OR_VERIFIED_OMITTED_XML_CELL", item);
        if ("SOURCE".equals(side)) {
            if (nullAtom(atom)) {
                require(
                        truth(args.get("source_reference")),
                        "EXPLICIT_SOURCE_NULL_NATIVE_REFERENCE_REQUIRED");
                return plus(
                        original, "key", shared, "sourceReference", args.get("source_reference"));
            }
            return original;
        }
        if (!atom.equals(
                obj("presence", "ABSENT", "type", "excel:absent-xml-cell", "value", null))) {
            return original;
        }
        final var row = nullableMap(args.get("esl_row"));
        require(
                row != null && Boolean.TRUE.equals(index.get("all31HeadersVerified")),
                "VERIFIED_OMISSION_AND_EXISTING_ROW_REQUIRED");
        final var refs = map(row.get("cell_references"));
        final var reference = map(refs.get(item));
        final var rootReference = map(refs.get("item_221149"));
        final var rootAtom = map(map(row.get("cells")).get("item_221149"));
        final Object rowId = reference.get("row");
        final Object maskValue = map(index.get("rowMasks")).get(rowId);
        final int column = ((BigInteger) header.get("column")).intValue() - 1;
        final int rootColumn = ((BigInteger) rootHeader.get("column")).intValue() - 1;
        require(
                "NATIVE6908".equals(reference.get("table"))
                        && java.util.Objects.equals(reference.get("sheet"), context.get("sheet"))
                        && java.util.Objects.equals(context.get("sheet"), index.get("sheet"))
                        && java.util.Objects.equals(reference.get("column"), header.get("column"))
                        && Boolean.TRUE.equals(reference.get("absence"))
                        && reference.get("cellRecord") == null
                        && reference.get("rawSpan") == null
                        && rowInRange(rowId, context.get("physicalRows"))
                        && maskValue instanceof BigInteger bits
                        && bits.signum() >= 0
                        && bits.bitLength() <= 31
                        && !bits.testBit(column)
                        && bits.testBit(rootColumn)
                        && "VALUE".equals(rootAtom.get("presence"))
                        && Set.of("number", "integer").contains(rootAtom.get("type"))
                        && java.util.Objects.equals(rootReference.get("row"), rowId)
                        && java.util.Objects.equals(
                                rootReference.get("sheet"), context.get("sheet"))
                        && java.util.Objects.equals(
                                rootReference.get("column"), rootHeader.get("column"))
                        && rootReference.get("cellRecord") != null
                        && Boolean.FALSE.equals(rootReference.get("absence")),
                "CELL_OMITTED_IN_BOUND_COLUMN_EXISTING_IDENTIFIED_ROW_NOT_MISSING_COLUMN");
        return plus(
                original,
                "key",
                shared,
                "omissionEvidence",
                obj(
                        "coordinate",
                        ((String) header.get("coordinate")).replaceAll("[0-9]+$", "") + rowId,
                        "rowMask31",
                        maskValue,
                        "rowIdentityCoordinate",
                        ((String) rootHeader.get("coordinate")).replaceAll("[0-9]+$", "") + rowId,
                        "rowIdentityReference",
                        rootReference,
                        "cellReference",
                        reference,
                        "originalPartPins",
                        index.get("originalPartPins"),
                        "all31HeadersVerified",
                        true));
    }

    private static Map<Object, Object> delta(
            final Map<Object, Object> left, final Map<Object, Object> right, final int top) {
        final var digest = sha();
        final var examples = new ArrayList<Object>();
        long keys = 0;
        BigInteger occurrences = BigInteger.ZERO;
        for (final var entry : left.entrySet()) {
            final var count = (BigInteger) entry.getValue();
            final var difference =
                    count.subtract(
                            (BigInteger) right.getOrDefault(entry.getKey(), BigInteger.ZERO));
            if (difference.signum() > 0) {
                keys++;
                occurrences = occurrences.add(difference);
                final var value = obj("key", list(entry.getKey()), "multiplicity", difference);
                digest.update(canonical(value));
                digest.update((byte) '\n');
                if (examples.size() < top) {
                    examples.add(value);
                }
            }
        }
        return obj(
                "distinctKeys",
                keys,
                "occurrences",
                occurrences,
                "firstEntries",
                examples,
                "examplesComplete",
                keys <= top,
                "completeDeltaSha256",
                java.util.HexFormat.of().withUpperCase().formatHex(digest.digest()));
    }

    private static Map<Object, Object> timestampBags(final Map<Object, Object> args) {
        scope(args);
        final int top = top(args.get("top_n"));
        final String item = string(args.get("item"));
        final var context = map(args.get("context"));
        final var source = list(args.get("source_rows"));
        final var nativeRows = list(args.get("esl_rows"));
        require(
                source.size() <= 2000 && nativeRows.size() <= 2000,
                "PRECISION_FINITE_COMPLETE_ROOT_ROWS");
        final var resolved = list(map(args.get("resolve_cell")).get("$syntheticResolvedCells"));
        final var stream = sha();
        final var bags = new ArrayList<Map<Object, Object>>();
        final var issues = new ArrayList<Map<Object, Object>>();
        for (int sideIndex = 0; sideIndex < 2; sideIndex++) {
            final String side = sideIndex == 0 ? "SOURCE" : "ESL";
            final var rows = sideIndex == 0 ? source : nativeRows;
            final var bag = new LinkedHashMap<Object, Object>();
            final var reasons = new LinkedHashMap<Object, Object>();
            final var examples = new ArrayList<Object>();
            long pending = 0, fractions = 0, canonical = 0;
            for (int index = 0; index < rows.size(); index++) {
                final var row = map(rows.get(index));
                final var atom = map(map(row.get("cells")).get(item));
                final Object reference = map(row.get("cell_references")).get(item);
                Map<Object, Object> cell = null;
                Object proof = null;
                if (sideIndex == 1) {
                    require(
                            index < resolved.size()
                                    && java.util.Objects.equals(
                                            map(resolved.get(index)).get("reference"), reference),
                            "DIFFERENTIAL_SYNTHETIC_CELL_REFERENCE");
                    cell = nullableMap(map(resolved.get(index)).get("cell"));
                    proof = map(resolved.get(index)).get("proof");
                }
                final var decision =
                        timestamp(
                                item, atom, side, context, cell, proof, args.get("enabled_items"));
                stream.update(
                        canonical(
                                obj(
                                        "side",
                                        side,
                                        "reference",
                                        reference,
                                        "originalAtom",
                                        atom,
                                        "decision",
                                        decision)));
                stream.update((byte) '\n');
                if (sideIndex == 0 && decision.get("sourceFractionLiteral") != null) {
                    fractions++;
                }
                if (sideIndex == 1
                        && Boolean.TRUE.equals(
                                decision.get("nativeWholeSecondCanonicalBinary64"))) {
                    canonical++;
                }
                if (Boolean.TRUE.equals(decision.get("available"))) {
                    bag.merge(
                            decision.get("key"),
                            BigInteger.ONE,
                            (a, b) -> ((BigInteger) a).add((BigInteger) b));
                } else {
                    pending++;
                    reasons.merge(
                            decision.get("reason"),
                            BigInteger.ONE,
                            (a, b) -> ((BigInteger) a).add((BigInteger) b));
                    require(reasons.size() <= 32, "PRECISION_FINITE_PENDING_REASONS32");
                    if (examples.size() < top) {
                        examples.add(
                                obj(
                                        "reference",
                                        reference,
                                        "reason",
                                        decision.get("reason"),
                                        "originalAtomSha256",
                                        decision.get("originalAtomSha256")));
                    }
                }
            }
            bags.add(bag);
            issues.add(
                    obj(
                            "occurrences",
                            pending,
                            "reasonCounts",
                            reasons,
                            "firstExamples",
                            examples,
                            "examplesComplete",
                            pending <= top,
                            "sourceFractionOccurrencesPreserved",
                            fractions,
                            "nativeCanonicalWholeSecondOccurrences",
                            canonical));
        }
        final boolean unresolved =
                issues.stream()
                        .anyMatch(issue -> ((Number) issue.get("occurrences")).longValue() != 0);
        final var answer =
                obj(
                        "item",
                        item,
                        "candidateAxis",
                        "EXACT_LOCAL_WALL_INTEGER_SECOND_MINUS03_VS_VERIFIED164",
                        "equal",
                        unresolved ? null : bags.get(0).equals(bags.get(1)),
                        "availableForAllOccurrences",
                        !unresolved,
                        "sourceOccurrences",
                        source.size(),
                        "eslOccurrences",
                        nativeRows.size(),
                        "sourceOnly",
                        delta(bags.get(0), bags.get(1), top),
                        "eslOnly",
                        delta(bags.get(1), bags.get(0), top),
                        "pending",
                        issues,
                        "differencesArePartial",
                        unresolved,
                        "examplesMaximumPerSide",
                        top,
                        "completeDecisionStreamSha256",
                        java.util.HexFormat.of().withUpperCase().formatHex(stream.digest()),
                        "allOccurrencesDigested",
                        true,
                        "styleEvidencePins",
                        obj(
                                "inventory",
                                context.get("inventoryPin"),
                                "styles",
                                context.get("stylesPin")),
                        "externalOccurrencesPaired",
                        false,
                        "deduplicationApplied",
                        false,
                        "policyApproved",
                        false,
                        "UTCConversionApplied",
                        false,
                        "originalAtomsUnchanged",
                        true,
                        "joint31EqualityProven",
                        false);
        require(canonical(answer).length <= 32768, "PRECISION_TIMESTAMP_DIAGNOSTIC32K");
        return answer;
    }

    private static java.security.MessageDigest sha() {
        try {
            return java.security.MessageDigest.getInstance("SHA-256");
        } catch (final java.security.NoSuchAlgorithmException absent) {
            throw new IllegalStateException(absent);
        }
    }

    private static void explicit(final Map<Object, Object> atom, final String code) {
        require(
                atom.keySet().equals(Set.of("presence", "type", "value"))
                        && Set.of("VALUE", "NULL", "ABSENT").contains(atom.get("presence")),
                code);
    }

    private static boolean nullAtom(final Map<Object, Object> atom) {
        return atom.equals(obj("presence", "NULL", "type", "null", "value", null));
    }

    private static Map<Object, Object> nullableMap(final Object value) {
        return value == null ? null : map(value);
    }

    private static List<Object> sequenceOrEmpty(final Object value) {
        return value == null ? List.of() : list(value);
    }

    private static boolean noPayload(final Map<Object, Object> value, final String... fields) {
        for (final String field : fields) {
            if (truth(value.get(field))) {
                return false;
            }
        }
        return true;
    }

    private static boolean numericType(final Map<Object, Object> attrs) {
        return attrs.get("t") == null || "n".equals(attrs.get("t"));
    }

    private static boolean rowInRange(final Object row, final Object maximum) {
        return row instanceof BigInteger number
                && number.compareTo(BigInteger.TWO) >= 0
                && number.compareTo((BigInteger) maximum) <= 0;
    }

    private static boolean resolvedStyle(final Map<Object, Object> context, final Object literal) {
        return literal instanceof String text
                && text.matches("[0-9]{1,4}")
                && new BigInteger(text)
                                .compareTo(BigInteger.valueOf(list(context.get("xfs")).size()))
                        < 0;
    }

    private static Map<Object, Object> xf(final Map<Object, Object> context, final Object literal) {
        return resolvedStyle(context, literal)
                ? map(list(context.get("xfs")).get(Integer.parseInt((String) literal)))
                : obj();
    }

    private static void scope(final Map<Object, Object> args) {
        require(
                truth(map(map(args.get("context")).get("_ctx")).get("$boundedSyntheticContext"))
                        && truth(map(args.get("index_lease")).get("$sameContextLease")),
                "PRECISION_ONE_SHARED_CONTEXT_AND_LEASE");
    }

    private static int top(final Object value) {
        final var number = integer(value, "PRECISION_TOP_N_ZERO_TO3");
        require(
                number.signum() >= 0 && number.compareTo(BigInteger.valueOf(3)) <= 0,
                "PRECISION_TOP_N_ZERO_TO3");
        return number.intValue();
    }
}
