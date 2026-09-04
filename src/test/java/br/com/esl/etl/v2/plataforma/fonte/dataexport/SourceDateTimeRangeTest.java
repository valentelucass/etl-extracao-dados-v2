package br.com.esl.etl.v2.plataforma.fonte.dataexport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

class SourceDateTimeRangeTest {

    @Test
    void formatsInstantsUsingConfiguredSourceTimezoneWithoutOffset() {
        final SourceDateTimeRange range =
                new SourceDateTimeRange(
                        Instant.parse("2026-08-13T03:00:00Z"),
                        Instant.parse("2026-08-14T02:59:59Z"));

        assertEquals(
                "2026-08-13 00:00:00 - 2026-08-13 23:59:59",
                range.formatForSource(ZoneId.of("America/Sao_Paulo")));
    }

    @Test
    void rejectsSubsecondWatermarksToAvoidSilentTruncation() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new SourceDateTimeRange(
                                Instant.parse("2026-08-13T03:00:00.001Z"),
                                Instant.parse("2026-08-13T03:00:01Z")));
    }
}
