package br.com.esl.etl.v2.modulos.coletas.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.plataforma.identidade.ScopedSourceIdentity;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ColetaStageValueObjectsTest {

    private static final UUID EXECUTION_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000010");
    private static final Instant OBSERVED_AT = Instant.parse("2026-09-04T20:00:00Z");
    private static final ScopedSourceIdentity.SourceKey SOURCE_KEY =
            new ScopedSourceIdentity.SourceKey(ScopedSourceIdentity.WireType.INTEGER, "INTEGER:10");

    @Test
    void preservesStatusPrecedenceAndRedactsTheEnvelope() {
        final ColetaStatus.Resolved canceled =
                ColetaStatus.resolve("cancelled", "motivo sintético");
        final ColetaStatus.Resolved finished = ColetaStatus.resolve("finished", "motivo sintético");
        final ColetaStageRecord valid = record(1, finished);
        final ColetaStageRecord quarantine =
                ColetaStageRecord.quarantine(2, null, "MISSING_SOURCE_KEY");

        assertEquals("motivo sintético", canceled.occurrenceAction());
        assertEquals("Coleta Realizada", finished.occurrenceAction());
        assertEquals(1, finished.attempts());
        assertEquals(ColetaStageDisposition.VALID, valid.disposition());
        assertEquals(ColetaStageDisposition.QUARANTINE, quarantine.disposition());
        assertNull(quarantine.payloadJson());
        assertTrue(canceled.toString().contains("<redacted>"));
        assertFalse(canceled.toString().contains("motivo sintético"));
        assertTrue(valid.toString().contains("<redacted>"));
        assertFalse(valid.toString().contains("INTEGER:10"));
    }

    @Test
    void rejectsInconsistentRecordAndBatchCombinations() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        ColetaStageRecord.valid(
                                1,
                                SOURCE_KEY,
                                ColetaAttributePresence.VALUE,
                                null,
                                "{}",
                                "{}",
                                "{}",
                                ColetaStatus.resolve("pending", null),
                                null,
                                null,
                                ColetaFreshnessOrigin.UNAVAILABLE));
        assertThrows(
                IllegalArgumentException.class,
                () -> ColetaStageRecord.quarantine(101, null, "MISSING_SOURCE_KEY"));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new ColetaStageBatch(
                                EXECUTION_ID,
                                0,
                                List.of(record(1, ColetaStatus.resolve("pending", null))),
                                OBSERVED_AT));
        assertThrows(
                IllegalArgumentException.class,
                () -> new ColetaStageBatch(EXECUTION_ID, 1, List.of(), OBSERVED_AT));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new ColetaStageBatch(
                                EXECUTION_ID,
                                1,
                                List.of(
                                        record(1, ColetaStatus.resolve("pending", null)),
                                        record(1, ColetaStatus.resolve("pending", null))),
                                OBSERVED_AT));

        final List<ColetaStageRecord> recordsWithNull = new ArrayList<>();
        recordsWithNull.add(null);
        final NullPointerException nullRecordException =
                assertThrows(
                        NullPointerException.class,
                        () -> new ColetaStageBatch(EXECUTION_ID, 1, recordsWithNull, OBSERVED_AT));
        assertEquals(
                "O batch de Coletas não aceita registro nulo.", nullRecordException.getMessage());
    }

    @Test
    void boundsAnIterableWithoutTrustingItsReportedSize() {
        final List<ColetaStageRecord> records = new ArrayList<>();
        for (int ordinal = 1; ordinal <= ColetaStageRecord.MAXIMUM_PAGE_SIZE; ordinal++) {
            records.add(record(ordinal, ColetaStatus.resolve("pending", null)));
        }
        assertEquals(
                ColetaStageRecord.MAXIMUM_PAGE_SIZE,
                new ColetaStageBatch(EXECUTION_ID, 1, records, OBSERVED_AT).size());
        records.add(records.get(0));

        final IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> new ColetaStageBatch(EXECUTION_ID, 1, records, OBSERVED_AT));

        assertEquals(
                "O batch de Coletas excede o limite de 100 registros.", exception.getMessage());
    }

    @Test
    void isolatesFirstSecondAbsenceAndReappearanceUntilTheAuthorizedCompletenessGate() {
        for (final ColetaAbsencePolicy.Observation observation :
                ColetaAbsencePolicy.Observation.values()) {
            final ColetaAbsencePolicy.Decision decision = ColetaAbsencePolicy.observe(observation);

            assertEquals("BLOCKED_NO_COMPLETENESS_PROOF", decision.completeness());
            assertEquals("NOT_EVALUATED", decision.evaluation());
            assertFalse(decision.changesActiveState());
        }
    }

    private static ColetaStageRecord record(final int ordinal, final ColetaStatus.Resolved status) {
        return ColetaStageRecord.valid(
                ordinal,
                SOURCE_KEY,
                ColetaAttributePresence.VALUE,
                "10",
                "{}",
                "{}",
                "{}",
                status,
                "2026-09-04T17:00:00-03:00",
                Instant.parse("2026-09-04T20:00:00Z"),
                ColetaFreshnessOrigin.STATUS_UPDATED_AT);
    }
}
