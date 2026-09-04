package br.com.esl.etl.v2.modulos.usuarios.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.plataforma.identidade.ScopedSourceIdentity;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class UsuarioStageValueObjectsTest {

    private static final UUID EXECUTION_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000331");
    private static final Instant OBSERVED_AT = Instant.parse("2026-09-01T15:00:00Z");
    private static final ScopedSourceIdentity.SourceKey SOURCE_KEY =
            new ScopedSourceIdentity.SourceKey(
                    ScopedSourceIdentity.WireType.STRING, "STRING:synthetic-user");

    @Test
    void preservesPresenceAndRedactsEverySensitiveValue() {
        final UsuarioStageRecord value =
                UsuarioStageRecord.valid(
                        1, SOURCE_KEY, UsuarioNamePresence.VALUE, "Nome sintético");
        final UsuarioStageRecord absent =
                UsuarioStageRecord.valid(2, SOURCE_KEY, UsuarioNamePresence.ABSENT, null);
        final UsuarioStageRecord explicitNull =
                UsuarioStageRecord.valid(3, SOURCE_KEY, UsuarioNamePresence.NULL, null);
        final UsuarioStageRecord quarantine =
                UsuarioStageRecord.quarantine(4, null, "MISSING_SOURCE_KEY");

        assertEquals(1, value.inputOrdinal());
        assertSame(SOURCE_KEY, value.sourceKey());
        assertEquals(UsuarioNamePresence.VALUE, value.namePresence());
        assertEquals("Nome sintético", value.name());
        assertEquals(UsuarioStageDisposition.VALID, value.disposition());
        assertNull(value.quarantineReasonCode());
        assertEquals(UsuarioNamePresence.ABSENT, absent.namePresence());
        assertEquals(UsuarioNamePresence.NULL, explicitNull.namePresence());
        assertNull(absent.name());
        assertNull(explicitNull.name());
        assertEquals(UsuarioStageDisposition.QUARANTINE, quarantine.disposition());
        assertEquals("MISSING_SOURCE_KEY", quarantine.quarantineReasonCode());
        assertNull(quarantine.sourceKey());
        assertNull(quarantine.namePresence());

        for (final UsuarioStageRecord record : List.of(value, absent, explicitNull, quarantine)) {
            assertTrue(record.toString().contains("<redacted>"));
            assertFalse(record.toString().contains("synthetic-user"));
            assertFalse(record.toString().contains("Nome sintético"));
        }
    }

    @Test
    void refusesInvalidNameDispositionOrdinalAndReasonCombinations() {
        assertThrows(
                IllegalArgumentException.class,
                () -> UsuarioStageRecord.valid(1, SOURCE_KEY, UsuarioNamePresence.VALUE, null));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        UsuarioStageRecord.valid(
                                1, SOURCE_KEY, UsuarioNamePresence.ABSENT, "unexpected"));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        UsuarioStageRecord.valid(
                                1, SOURCE_KEY, UsuarioNamePresence.NULL, "unexpected"));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        UsuarioStageRecord.valid(
                                1,
                                SOURCE_KEY,
                                UsuarioNamePresence.VALUE,
                                "x".repeat(UsuarioStageRecord.MAXIMUM_NAME_CHARACTERS + 1)));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        UsuarioStageRecord.valid(
                                1, SOURCE_KEY, UsuarioNamePresence.VALUE, "line\nbreak"));
        assertThrows(
                IllegalArgumentException.class,
                () -> UsuarioStageRecord.valid(1, SOURCE_KEY, UsuarioNamePresence.VALUE, "\uD800"));
        assertThrows(
                NullPointerException.class,
                () -> UsuarioStageRecord.valid(1, null, UsuarioNamePresence.NULL, null));
        assertThrows(
                NullPointerException.class,
                () -> UsuarioStageRecord.valid(1, SOURCE_KEY, null, null));
        assertThrows(
                IllegalArgumentException.class,
                () -> UsuarioStageRecord.quarantine(0, null, "MISSING_SOURCE_KEY"));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        UsuarioStageRecord.quarantine(
                                UsuarioStageBatch.MAXIMUM_PAGE_SIZE + 1,
                                null,
                                "MISSING_SOURCE_KEY"));
        assertThrows(
                IllegalArgumentException.class, () -> UsuarioStageRecord.quarantine(1, null, null));
        assertThrows(
                IllegalArgumentException.class,
                () -> UsuarioStageRecord.quarantine(1, null, "unsafe reason"));
    }

    @Test
    void copiesExactlyOneBoundedPageAndRejectsAmbiguousBatches() {
        final UsuarioStageRecord first =
                UsuarioStageRecord.valid(1, SOURCE_KEY, UsuarioNamePresence.VALUE, "A");
        final UsuarioStageRecord second =
                UsuarioStageRecord.quarantine(2, null, "MISSING_SOURCE_KEY");
        final List<UsuarioStageRecord> mutable = new ArrayList<>(List.of(first, second));
        final UsuarioStageBatch batch =
                new UsuarioStageBatch(EXECUTION_ID, 7, mutable, OBSERVED_AT);
        mutable.clear();

        assertEquals(EXECUTION_ID, batch.executionId());
        assertEquals(7, batch.batchNumber());
        assertEquals(2, batch.size());
        assertSame(first, batch.recordAt(0));
        assertSame(second, batch.recordAt(1));
        assertEquals(OBSERVED_AT, batch.observedAt());
        assertTrue(batch.toString().contains("batchNumber=7"));
        assertTrue(batch.toString().contains("size=2"));
        assertFalse(batch.toString().contains(EXECUTION_ID.toString()));

        assertThrows(
                IllegalArgumentException.class,
                () -> new UsuarioStageBatch(EXECUTION_ID, 0, List.of(first), OBSERVED_AT));
        assertThrows(
                IllegalArgumentException.class,
                () -> new UsuarioStageBatch(EXECUTION_ID, 1, List.of(), OBSERVED_AT));
        assertThrows(
                IllegalArgumentException.class,
                () -> new UsuarioStageBatch(EXECUTION_ID, 1, List.of(first, first), OBSERVED_AT));
        assertThrows(
                NullPointerException.class,
                () ->
                        new UsuarioStageBatch(
                                EXECUTION_ID,
                                1,
                                java.util.Arrays.asList(first, null),
                                OBSERVED_AT));
        assertThrows(
                NullPointerException.class,
                () -> new UsuarioStageBatch(null, 1, List.of(first), OBSERVED_AT));
        assertThrows(
                NullPointerException.class,
                () -> new UsuarioStageBatch(EXECUTION_ID, 1, null, OBSERVED_AT));
        assertThrows(
                NullPointerException.class,
                () -> new UsuarioStageBatch(EXECUTION_ID, 1, List.of(first), null));
    }

    @Test
    void stopsAtTheTwentyFirstRecordWithoutTrustingIterableSize() {
        final List<UsuarioStageRecord> twentyOne = new ArrayList<>();
        for (int ordinal = 1; ordinal <= UsuarioStageBatch.MAXIMUM_PAGE_SIZE; ordinal++) {
            twentyOne.add(
                    UsuarioStageRecord.valid(
                            ordinal, SOURCE_KEY, UsuarioNamePresence.ABSENT, null));
        }
        twentyOne.add(twentyOne.get(0));

        final IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> new UsuarioStageBatch(EXECUTION_ID, 1, twentyOne, OBSERVED_AT));

        assertEquals("O batch de Usuários excede uma página.", exception.getMessage());
    }
}
