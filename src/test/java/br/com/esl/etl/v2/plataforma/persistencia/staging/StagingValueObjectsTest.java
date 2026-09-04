package br.com.esl.etl.v2.plataforma.persistencia.staging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import br.com.esl.etl.v2.plataforma.controle.ImmutableFingerprint;
import java.time.Instant;
import java.util.AbstractList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class StagingValueObjectsTest {

    private static final String A = "a".repeat(64);
    private static final String B = "b".repeat(64);

    @Test
    void normalizesAQuarantineRecordWithoutKeepingARequiredSourceKey() {
        final StagingRecord record =
                new StagingRecord(
                        1,
                        "  ",
                        null,
                        null,
                        null,
                        StagingDisposition.QUARANTINE,
                        " SOURCE_KEY_MISSING ");

        assertNull(record.sourceKey());
        assertEquals("SOURCE_KEY_MISSING", record.quarantineReasonCode());
    }

    @Test
    void rejectsAValidRecordWithoutTheMinimumDeduplicationContract() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new StagingRecord(
                                1,
                                "synthetic-key",
                                null,
                                new ImmutableFingerprint("presence-v1", A),
                                Instant.parse("2026-08-30T00:00:00Z"),
                                StagingDisposition.VALID,
                                null));
    }

    @Test
    void preservesOpaqueKeyCaseAndAccentAndRejectsAnOversizedSharedPrefix() {
        final String maximumPrefix = "K".repeat(256);
        final StagingRecord maximum = validRecord(1, maximumPrefix);
        final StagingRecord caseVariant = validRecord(2, "KeyA");
        final StagingRecord accentVariant = validRecord(3, "ação");

        assertEquals(maximumPrefix, maximum.sourceKey());
        assertEquals("KeyA", caseVariant.sourceKey());
        assertEquals("ação", accentVariant.sourceKey());
        assertThrows(IllegalArgumentException.class, () -> validRecord(4, maximumPrefix + "X"));
        assertThrows(IllegalArgumentException.class, () -> validRecord(4, maximumPrefix + " "));
    }

    @Test
    void trimsOnlyAsciiSpaceAndPreservesTabsInOpaqueStagingKeysAndVersions() {
        final StagingRecord tabbed =
                new StagingRecord(
                        1,
                        "\tKey\t",
                        new ImmutableFingerprint("\tRow-Version\t", A),
                        new ImmutableFingerprint("\tPresence-Version\t", B),
                        Instant.parse("2026-08-30T00:00:00Z"),
                        StagingDisposition.VALID,
                        null);

        assertEquals("\tKey\t", tabbed.sourceKey());
        assertEquals("\tRow-Version\t", tabbed.rowFingerprint().version());
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new StagingRecord(
                                2,
                                null,
                                null,
                                null,
                                null,
                                StagingDisposition.QUARANTINE,
                                "\tSOURCE_KEY_MISSING\t"));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new StagingRecord(
                                3,
                                null,
                                null,
                                null,
                                null,
                                StagingDisposition.QUARANTINE,
                                "source_key_missing"));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new StagingRecord(
                                4, null, null, null, null, StagingDisposition.QUARANTINE, "ß"));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new StagingRecord(
                                5, null, null, null, null, StagingDisposition.QUARANTINE, "_X"));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new StagingRecord(
                                6, null, null, null, null, StagingDisposition.QUARANTINE, "1X"));
    }

    @Test
    void validatesTheRawQuarantineReasonLengthBeforeTrimAndUppercase() {
        final String maximumReason = "Q".repeat(64);
        final StagingRecord maximum =
                new StagingRecord(
                        1, null, null, null, null, StagingDisposition.QUARANTINE, maximumReason);

        assertEquals(maximumReason, maximum.quarantineReasonCode());
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new StagingRecord(
                                2,
                                null,
                                null,
                                null,
                                null,
                                StagingDisposition.QUARANTINE,
                                maximumReason + " "));
    }

    @Test
    void keepsOnlyAnExplicitlyBoundedSingleBatch() {
        final StagingRecord record =
                new StagingRecord(
                        1,
                        "synthetic-key",
                        new ImmutableFingerprint("row-v1", A),
                        new ImmutableFingerprint("presence-v1", B),
                        Instant.parse("2026-08-30T00:00:00Z"),
                        StagingDisposition.VALID,
                        null);

        final StagingBatch batch =
                new StagingBatch(
                        UUID.fromString("00000000-0000-0000-0000-000000000201"),
                        1,
                        1,
                        List.of(record),
                        Instant.parse("2026-08-30T00:00:01Z"));

        assertEquals(1, batch.records().size());
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new StagingBatch(
                                batch.executionId(), 2, 0, List.of(record), batch.stagedAt()));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new StagingBatch(
                                batch.executionId(),
                                2,
                                1,
                                List.of(record, record),
                                batch.stagedAt()));
        assertThrows(
                IllegalArgumentException.class,
                () -> new StagingBatch(batch.executionId(), 2, 1, List.of(), batch.stagedAt()));
        assertThrows(
                NullPointerException.class,
                () ->
                        new StagingBatch(
                                batch.executionId(),
                                2,
                                1,
                                Arrays.asList((StagingRecord) null),
                                batch.stagedAt()));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new StagingBatch(
                                batch.executionId(),
                                2,
                                2,
                                List.of(record, record),
                                batch.stagedAt()));
        assertThrows(
                IllegalArgumentException.class,
                () -> validRecord(StagingBatch.ABSOLUTE_MAXIMUM_BATCH_SIZE + 1, "other-key"));
    }

    @Test
    void rejectsAnAdversarialListBeforeReadingBeyondTheExplicitBatchLimit() {
        final StagingRecord first = validRecord(1, "first-key");
        final StagingRecord second = validRecord(2, "second-key");
        final List<StagingRecord> adversarial =
                new AbstractList<>() {
                    @Override
                    public StagingRecord get(final int index) {
                        throw new AssertionError("O construtor deve usar o iterador bounded.");
                    }

                    @Override
                    public int size() {
                        return 1;
                    }

                    @Override
                    public Iterator<StagingRecord> iterator() {
                        return new Iterator<>() {
                            private int index;

                            @Override
                            public boolean hasNext() {
                                return index <= 2;
                            }

                            @Override
                            public StagingRecord next() {
                                if (index == 2) {
                                    throw new AssertionError(
                                            "O limite deve ser rejeitado antes do próximo next().");
                                }
                                return index++ == 0 ? first : second;
                            }
                        };
                    }
                };

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new StagingBatch(
                                UUID.fromString("00000000-0000-0000-0000-000000000201"),
                                1,
                                2,
                                adversarial,
                                Instant.parse("2026-08-30T00:00:01Z")));
    }

    @Test
    void validatesAnAggregateAtomicPublicationResult() {
        final Instant reconciledAt = Instant.parse("2026-08-30T00:00:01Z");
        final Instant publishedAt = reconciledAt.plusMillis(1);
        final Instant frontierBefore = Instant.parse("2026-08-29T00:00:00Z");
        final Instant frontierAfter = frontierBefore.plusSeconds(60);
        final StagingPublicationResult result =
                new StagingPublicationResult(
                        UUID.fromString("00000000-0000-0000-0000-000000000202"),
                        10,
                        2,
                        3,
                        1,
                        4,
                        2,
                        reconciledAt,
                        publishedAt,
                        Optional.of(frontierBefore),
                        Optional.of(frontierAfter));

        assertEquals(10, result.candidateRows());
        assertEquals(2, result.staleNoopRows());
        assertEquals(Optional.of(frontierAfter), result.incrementalFrontierAfter());

        final StagingPublicationResult nonIncremental =
                new StagingPublicationResult(
                        UUID.randomUUID(),
                        0,
                        0,
                        0,
                        0,
                        0,
                        0,
                        reconciledAt,
                        reconciledAt,
                        Optional.empty(),
                        Optional.empty());
        assertEquals(Optional.empty(), nonIncremental.incrementalFrontierBefore());
    }

    @Test
    void rejectsInconsistentPublicationCountsTimesAndFrontiers() {
        final UUID executionId = UUID.randomUUID();
        final Instant now = Instant.parse("2026-08-30T00:00:00Z");

        assertThrows(
                IllegalArgumentException.class,
                () -> publication(executionId, -1, 0, 0, 0, 0, 0, now, now));
        assertThrows(
                IllegalArgumentException.class,
                () -> publication(executionId, 2, 1, 0, 0, 0, 0, now, now));
        assertThrows(
                IllegalArgumentException.class,
                () -> publication(executionId, 1, 0, 0, 0, 1, 2, now, now));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        publication(
                                executionId, Long.MAX_VALUE, Long.MAX_VALUE, 1, 0, 0, 0, now, now));
        assertThrows(
                IllegalArgumentException.class,
                () -> publication(executionId, 0, 0, 0, 0, 0, 0, now, now.minusMillis(1)));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new StagingPublicationResult(
                                executionId,
                                0,
                                0,
                                0,
                                0,
                                0,
                                0,
                                now,
                                now,
                                Optional.of(now),
                                Optional.empty()));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new StagingPublicationResult(
                                executionId,
                                0,
                                0,
                                0,
                                0,
                                0,
                                0,
                                now,
                                now,
                                Optional.of(now),
                                Optional.of(now.minusMillis(1))));
    }

    private static StagingPublicationResult publication(
            final UUID executionId,
            final long candidateRows,
            final long insertedRows,
            final long updatedRows,
            final long reactivatedRows,
            final long noopRows,
            final long staleNoopRows,
            final Instant reconciledAt,
            final Instant publishedAt) {
        return new StagingPublicationResult(
                executionId,
                candidateRows,
                insertedRows,
                updatedRows,
                reactivatedRows,
                noopRows,
                staleNoopRows,
                reconciledAt,
                publishedAt,
                Optional.empty(),
                Optional.empty());
    }

    private static StagingRecord validRecord(final int ordinal, final String sourceKey) {
        return new StagingRecord(
                ordinal,
                sourceKey,
                new ImmutableFingerprint("Row-V1", A),
                new ImmutableFingerprint("Presence-V1", B),
                Instant.parse("2026-08-30T00:00:00Z"),
                StagingDisposition.VALID,
                null);
    }
}
