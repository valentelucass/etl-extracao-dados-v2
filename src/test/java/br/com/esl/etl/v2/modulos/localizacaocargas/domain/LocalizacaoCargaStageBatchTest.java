package br.com.esl.etl.v2.modulos.localizacaocargas.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class LocalizacaoCargaStageBatchTest {
    private static final Instant NOW = Instant.parse("2036-03-20T12:00:00Z");

    @Test
    void exposesOnlyIndexedAccessToOneBoundedPage() {
        final var batch =
                new LocalizacaoCargaStageBatch(
                        UUID.randomUUID(),
                        1,
                        List.of(LocalizacaoCargaStageRecord.quarantine(1, "SYNTHETIC_CASE")),
                        NOW);

        assertEquals(1, batch.size());
        assertEquals(1, batch.recordAt(0).inputOrdinal());
        assertThrows(IndexOutOfBoundsException.class, () -> batch.recordAt(1));
    }

    @Test
    void rejectsEmptyDuplicateAndMoreThanOneHundredRecords() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new LocalizacaoCargaStageBatch(UUID.randomUUID(), 1, List.of(), NOW));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new LocalizacaoCargaStageBatch(
                                UUID.randomUUID(),
                                1,
                                List.of(
                                        LocalizacaoCargaStageRecord.quarantine(1, "FIRST_CASE"),
                                        LocalizacaoCargaStageRecord.quarantine(1, "SECOND_CASE")),
                                NOW));

        final List<LocalizacaoCargaStageRecord> tooMany = new ArrayList<>();
        for (int ordinal = 1; ordinal <= 100; ordinal++) {
            tooMany.add(LocalizacaoCargaStageRecord.quarantine(ordinal, "BOUNDED_CASE"));
        }
        tooMany.add(LocalizacaoCargaStageRecord.quarantine(100, "OVER_LIMIT_CASE"));
        assertThrows(
                IllegalArgumentException.class,
                () -> new LocalizacaoCargaStageBatch(UUID.randomUUID(), 1, tooMany, NOW));
    }

    @Test
    void manyPagesRemainIndependentMicrobatches() {
        for (int page = 1; page <= 500; page++) {
            final var batch =
                    new LocalizacaoCargaStageBatch(
                            UUID.randomUUID(),
                            page,
                            List.of(LocalizacaoCargaStageRecord.quarantine(1, "PAGE_CASE")),
                            NOW);
            assertEquals(1, batch.size());
        }
    }
}
