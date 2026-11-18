package br.com.esl.etl.v2.plataforma.reconciliacao.exportacao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class QualifiedSinistrosModel44Test {
    private static final String DATE_CLOCK = "SOURCE_OFFSET_DATE_CLOCK_SECOND_V1";
    private static final String LOCAL = "ISO_LOCAL_TIME_SECOND_V1";
    private static final String NATIVE = "FRACTION_DAY_NEAREST_SECOND_V1";

    @Test
    void offsetClockRetainsCompleteDateAndWallTimeWithoutConvertingToUtc() {
        assertEquals(
                "2000-01-01T12:34:56",
                QualifiedSinistrosModel44.sourceTime("2000-01-01T12:34:56-03:00", DATE_CLOCK));
        assertEquals(
                "2026-10-13T12:34:56",
                QualifiedSinistrosModel44.sourceTime("2026-10-13T12:34:56+05:30", DATE_CLOCK));
        assertEquals(
                "2026-10-13T12:34:56",
                QualifiedSinistrosModel44.sourceTime("2026-10-13T12:34:56Z", DATE_CLOCK));
    }

    @Test
    void localAndOffsetEncodingsRequireTheSelectedExplicitPolicy() {
        assertEquals(
                "00:00:00",
                QualifiedSinistrosModel44.sourceTime("00:00:00", "ISO_LOCAL_TIME_SECOND_V1"));
        assertEquals("23:59:59", QualifiedSinistrosModel44.sourceTime("23:59:59.000", LOCAL));
        assertThrows(
                IllegalArgumentException.class,
                () -> QualifiedSinistrosModel44.sourceTime("12:00:00", DATE_CLOCK));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        QualifiedSinistrosModel44.sourceTime(
                                "2000-01-01T12:00:00-03:00", "ISO_LOCAL_TIME_SECOND_V1"));
    }

    @Test
    void invalidClockFractionDateOrOffsetCannotBeTrimmedTruncatedOrNormalized() {
        for (final String invalid :
                new String[] {
                    " 12:00:00",
                    "12:00:00 ",
                    "12:00",
                    "24:00:00",
                    "23:59:60",
                    "12:00:00.1",
                    "2000-02-30T12:00:00-03:00",
                    "0000-01-01T12:00:00Z",
                    "2000-01-01T12:00:00+18:01",
                    "2000-01-01T12:00:00.0001-03:00"
                }) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> QualifiedSinistrosModel44.sourceTime(invalid, DATE_CLOCK),
                    invalid);
        }
        for (final String invalid :
                new String[] {
                    " 12:00:00", "12:00:00 ", "12:00", "24:00:00", "23:59:60", "12:00:00.1"
                }) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> QualifiedSinistrosModel44.sourceTime(invalid, LOCAL),
                    invalid);
        }
    }

    @Test
    void nativeClockUsesExactRationalNearestSecondAndDoesNotUseFloatingPoint() {
        assertEquals("00:00:00", QualifiedSinistrosModel44.nativeTime("0", NATIVE));
        assertEquals("12:00:00", QualifiedSinistrosModel44.nativeTime("0.5", NATIVE));
        assertEquals("06:00:00", QualifiedSinistrosModel44.nativeTime("2.5e-1", NATIVE));
        assertEquals("23:59:59", QualifiedSinistrosModel44.nativeTime("0.999988425925926", NATIVE));
    }

    @Test
    void nativeClockRejectsDateSerialNegativeFractionAndNextDayRounding() {
        for (final String invalid :
                new String[] {"-0.1", "1", "46308.5", "0.999999999", "NaN", "Infinity"}) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> QualifiedSinistrosModel44.nativeTime(invalid, NATIVE),
                    invalid);
        }
        assertThrows(
                IllegalArgumentException.class,
                () -> QualifiedSinistrosModel44.nativeTime("0.5", "G16"));
    }

    @Test
    void civilDatesRejectTimestampTruncationFractionsAndExcelFakeLeapDay() {
        assertEquals("2026-10-13", QualifiedColetasModel.sourceCivil("2026-10-13"));
        assertThrows(
                IllegalArgumentException.class,
                () -> QualifiedColetasModel.sourceCivil("2026-02-30"));
        assertThrows(
                IllegalArgumentException.class,
                () -> QualifiedColetasModel.sourceCivil("2026-10-13T00:00:00Z"));
        assertThrows(IllegalArgumentException.class, () -> QualifiedColetasModel.nativeCivil("60"));
        assertThrows(
                IllegalArgumentException.class, () -> QualifiedColetasModel.nativeCivil("46308.5"));
    }

    @Test
    void treatmentTimestampKeepsTheSeparateStrictMinus03Contract() {
        assertEquals(
                "2026-10-13T12:34:56",
                QualifiedColetasModel.sourceTimestamp("2026-10-13T12:34:56-03:00"));
        assertThrows(
                IllegalArgumentException.class,
                () -> QualifiedColetasModel.sourceTimestamp("2026-10-13T12:34:56Z"));
        assertThrows(
                IllegalArgumentException.class,
                () -> QualifiedColetasModel.sourceTimestamp("2026-10-13"));
    }

    @Test
    void dateBearingNativeClockPreservesTheFullSerialDayInItsKey() {
        assertEquals(
                "2026-10-13T12:00:00",
                QualifiedSinistrosModel44.nativeTime(
                        SinistrosContentFixture.serial("0.5"),
                        "NATIVE_DATE_TIME_NEAREST_SECOND_V1"));
        final String next =
                new java.math.BigDecimal(SinistrosContentFixture.serial("0.5"))
                        .add(java.math.BigDecimal.ONE)
                        .toPlainString();
        assertEquals(
                "2026-10-14T12:00:00",
                QualifiedSinistrosModel44.nativeTime(next, "NATIVE_DATE_TIME_NEAREST_SECOND_V1"));
    }

    @Test
    void dateBearingNativeClockRejectsFakeLeapDayPureFractionAndRoundedDayRollover() {
        for (final String invalid :
                new String[] {"60.5", "0.5", SinistrosContentFixture.serial("0.999999999")}) {
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            QualifiedSinistrosModel44.nativeTime(
                                    invalid, "NATIVE_DATE_TIME_NEAREST_SECOND_V1"),
                    invalid);
        }
    }

    @Test
    void policiesThatWouldDiscardAnOffsetClockDateAreNoLongerAdmitted() {
        for (final String obsolete :
                new String[] {
                    "ISO_OFFSET_CLOCK_SECOND_V1", "ISO_LOCAL_OR_OFFSET_CLOCK_SECOND_V1"
                }) {
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            QualifiedSinistrosModel44.sourceTime(
                                    "2000-01-01T12:00:00-03:00", obsolete));
        }
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        QualifiedSinistrosModel44.sourceTime(
                                "1900-02-28T12:00:00-03:00", DATE_CLOCK));
    }

    @Test
    void localClockCannotContainOffsetDateAndDateClockCannotContainOnlyLocalTime() {
        assertThrows(
                IllegalArgumentException.class,
                () -> QualifiedSinistrosModel44.sourceTime("2000-01-01T12:00:00-03:00", LOCAL));
        assertThrows(
                IllegalArgumentException.class,
                () -> QualifiedSinistrosModel44.sourceTime("12:00:00", DATE_CLOCK));
    }
}
