package br.com.esl.etl.v2.plataforma.reconciliacao.exportacao;

import static br.com.esl.etl.v2.plataforma.reconciliacao.exportacao.ExportValidationSupport.require;

import java.math.BigInteger;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Set;

/**
 * Explicit candidate clock policies. A date-bearing clock always retains its complete civil date in
 * the key.
 */
final class QualifiedSinistrosModel44 {
    static final Set<String> SOURCE_CLOCK_ENCODINGS =
            Set.of("ISO_LOCAL_TIME_SECOND_V1", "SOURCE_OFFSET_DATE_CLOCK_SECOND_V1");
    static final String NATIVE_CLOCK_ENCODING = "FRACTION_DAY_NEAREST_SECOND_V1";
    static final String NATIVE_DATE_CLOCK_ENCODING = "NATIVE_DATE_TIME_NEAREST_SECOND_V1";
    private static final LocalDate EPOCH = LocalDate.of(1899, 12, 30);
    private static final DateTimeFormatter SECOND = DateTimeFormatter.ofPattern("HH:mm:ss");

    private QualifiedSinistrosModel44() {}

    static String sourceTime(final String literal, final String encoding) {
        require(
                SOURCE_CLOCK_ENCODINGS.contains(encoding)
                        && literal != null
                        && literal.length() <= 180,
                "SINISTROS_EXPLICIT_BOUNDED_CLOCK_POLICY");
        try {
            if ("ISO_LOCAL_TIME_SECOND_V1".equals(encoding)) {
                require(
                        literal.matches("[0-9]{2}:[0-9]{2}:[0-9]{2}(\\.0{1,128})?"),
                        "SINISTROS_EXACT_LOCAL_CLOCK_NO_TRIM_OR_FRACTION_LOSS");
                return LocalTime.parse(literal.substring(0, 8)).format(SECOND);
            }
            require(
                    literal.matches(
                            "[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}(\\.0{1,128})?(?:Z|[+-][0-9]{2}:[0-9]{2})"),
                    "SINISTROS_DATE_BEARING_CLOCK_REQUIRES_OFFSET_DATE_NO_TRIM_OR_FRACTION_LOSS");
            final String day = QualifiedColetasModel.sourceCivil(literal.substring(0, 10));
            require(
                    !LocalDate.parse(day).isBefore(EPOCH.plusDays(61)),
                    "SINISTROS_SOURCE_DATE_CLOCK_1900_RANGE");
            final int offsetStart =
                    literal.endsWith("Z") ? literal.length() - 1 : literal.length() - 6;
            ZoneOffset.of(literal.substring(offsetStart));
            LocalDateTime.parse(literal.substring(0, 19));
            return day + "T" + LocalTime.parse(literal.substring(11, 19)).format(SECOND);
        } catch (final DateTimeException invalid) {
            throw new IllegalArgumentException("SINISTROS_INVALID_SOURCE_CLOCK");
        }
    }

    static String nativeTime(final String lexeme, final String encoding) {
        if (NATIVE_DATE_CLOCK_ENCODING.equals(encoding)) {
            final var ratio = ExactBinary64.ratio(lexeme);
            final String civil = QualifiedColetasModel.nativeTimestamp(lexeme);
            final LocalDate exactSerialDay =
                    EPOCH.plusDays(ratio.numerator().divide(ratio.denominator()).longValueExact());
            require(
                    exactSerialDay.toString().equals(civil.substring(0, 10)),
                    "SINISTROS_DATE_CLOCK_ROUNDING_MUST_NOT_ROLL_TO_NEXT_DAY");
            return civil;
        }
        require(NATIVE_CLOCK_ENCODING.equals(encoding), "SINISTROS_EXPLICIT_NATIVE_CLOCK_POLICY");
        final var ratio = ExactBinary64.ratio(lexeme);
        require(
                ratio.denominator().signum() > 0
                        && ratio.numerator().signum() >= 0
                        && ratio.numerator().compareTo(ratio.denominator()) < 0,
                "SINISTROS_CLOCK_FRACTION_DAY_ZERO_TO_ONE_NO_WRAP");
        final var seconds =
                ratio.numerator()
                        .multiply(BigInteger.valueOf(172800))
                        .add(ratio.denominator())
                        .divide(ratio.denominator().shiftLeft(1));
        require(
                seconds.compareTo(BigInteger.valueOf(86400)) < 0,
                "SINISTROS_CLOCK_ROUNDING_MUST_NOT_ROLL_TO_NEXT_DAY");
        return LocalTime.ofSecondOfDay(seconds.longValueExact()).format(SECOND);
    }
}
