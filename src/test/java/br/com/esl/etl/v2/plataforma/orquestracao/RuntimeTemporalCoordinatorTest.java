package br.com.esl.etl.v2.plataforma.orquestracao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.plataforma.controle.ExecutionMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RuntimeTemporalCoordinatorTest {
    private static final Instant START = Instant.parse("2024-02-01T00:00:00Z");

    static RuntimeTemporalPolicy policy() {
        return new RuntimeTemporalPolicy(
                "synthetic-v1",
                ZoneId.of("Etc/UTC"),
                ExecutionMode.INCREMENTAL,
                RuntimeWindowStrategy.INTERVAL,
                RuntimeTemporalPolicy.Cadence.CIVIL_DAY,
                LocalTime.MIDNIGHT,
                Duration.ofHours(2),
                Duration.ofHours(1),
                Duration.ofHours(4),
                Duration.ofHours(2),
                2,
                3,
                4,
                1,
                List.of());
    }

    private static RuntimeTemporalStore.Gap gap(final int day, final String state) {
        return new RuntimeTemporalStore.Gap(
                new UUID(0, day + 1),
                START.plusSeconds(day * 86400L),
                START.plusSeconds((day + 1) * 86400L),
                state);
    }

    @Test
    void declaredPlanIgnoresOtherCalendarPlansInTheSameNamespaceWithoutJumpingItsOwnGap() {
        final var store = new Store();
        final var coordinator = new RuntimeTemporalCoordinator(store);
        store.rows = List.of(gap(0, "PUBLISHED"), gap(1, "PUBLISHED"), gap(20, "FAILED"));
        final var expected = List.of(new UUID(0, 1), new UUID(0, 2));
        final var result = coordinator.reconcile("a".repeat(64), policy(), START, expected);
        assertEquals(START.plusSeconds(2 * 86400), result.contiguousEnd());
        assertEquals(0, result.degraded());
        assertFalse(result.limitReached());
        store.rows = List.of(gap(0, "NOT_STARTED"), gap(1, "PUBLISHED"), gap(20, "FAILED"));
        assertEquals(
                START,
                coordinator.reconcile("a".repeat(64), policy(), START, expected).contiguousEnd());
        store.rows = List.of(gap(1, "PUBLISHED"));
        assertThrows(
                IllegalStateException.class,
                () -> coordinator.reconcile("a".repeat(64), policy(), START, expected));
    }

    @Test
    void missingOrTruncatedPlanSummariesRemainLimitedAndDuplicateExpectationsAreRefused() {
        final var store = new Store();
        final var coordinator = new RuntimeTemporalCoordinator(store);
        final var expected = List.of(new UUID(0, 1), new UUID(0, 2));
        store.rows = List.of(gap(0, "PUBLISHED"));
        final var result = coordinator.reconcile("a".repeat(64), policy(), START, expected);
        assertEquals(START.plusSeconds(86400), result.contiguousEnd());
        assertTrue(result.limitReached());
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        coordinator.reconcile(
                                "a".repeat(64),
                                policy(),
                                START,
                                List.of(expected.get(0), expected.get(0))));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        coordinator.reconcile(
                                "a".repeat(64),
                                policy(),
                                START,
                                java.util.stream.IntStream.range(0, 65)
                                        .mapToObj(value -> new UUID(0, value))
                                        .toList()));
    }

    @Test
    void restartRetainsMissingOccurrenceAndOutOfOrderPublicationCannotSkipIt() {
        final var store = new Store();
        final var coordinator = new RuntimeTemporalCoordinator(store);
        final UUID plan = UUID.randomUUID();
        final var first =
                coordinator.persistCatchUp(
                        plan,
                        "a".repeat(64),
                        policy(),
                        LocalDate.of(2024, 2, 1),
                        START.plusSeconds(10 * 86400));
        assertEquals(
                first,
                coordinator.persistCatchUp(
                        plan,
                        "a".repeat(64),
                        policy(),
                        LocalDate.of(2024, 2, 1),
                        START.plusSeconds(10 * 86400)));
        assertTrue(first.backlogRemaining());
        assertEquals(3, store.persisted);
        store.rows =
                List.of(
                        gap(0, "PUBLISHED"),
                        gap(1, "NOT_STARTED"),
                        gap(2, "PUBLISHED"),
                        gap(3, "EXTRACTING"));
        final var recovered =
                new RuntimeTemporalCoordinator(store).reconcile("a".repeat(64), policy(), START);
        assertEquals(START.plusSeconds(86400), recovered.contiguousEnd());
        assertEquals(new UUID(0, 2), recovered.pending().get(0).occurrence().execution());
        assertEquals(
                RuntimeTemporalCoordinator.NextAction.START_ORIGINAL,
                recovered.pending().get(0).action());
        assertEquals(
                RuntimeTemporalCoordinator.NextAction.RECOVER_ORIGINAL,
                recovered.pending().get(1).action());
        assertTrue(recovered.limitReached());
        assertThrows(UnsupportedOperationException.class, () -> recovered.pending().clear());
        store.rows = List.of(gap(0, "PUBLISHED"), gap(1, "PUBLISHED"), gap(2, "PUBLISHED"));
        assertEquals(
                START.plusSeconds(3 * 86400),
                coordinator.reconcile("a".repeat(64), policy(), START).contiguousEnd());
    }

    @Test
    void degradedLimitRequiresExplicitReplayAndNeverInventsLeaseRecovery() {
        final var store = new Store();
        final var coordinator = new RuntimeTemporalCoordinator(store);
        store.rows = List.of(gap(0, "FAILED"), gap(1, "PUBLISHED"));
        final var result = coordinator.reconcile("a".repeat(64), policy(), START);
        assertEquals(START, result.contiguousEnd());
        assertEquals(1, result.degraded());
        assertFalse(result.limitReached());
        assertEquals(
                RuntimeTemporalCoordinator.NextAction.EXPLICIT_REPLAY_REQUIRED,
                result.pending().get(0).action());
        store.rows = List.of(gap(0, "FAILED"), gap(1, "CANCELLED"));
        assertThrows(
                IllegalStateException.class,
                () -> coordinator.reconcile("a".repeat(64), policy(), START));
        store.rows = List.of(gap(1, "PUBLISHED"));
        assertThrows(
                IllegalStateException.class,
                () -> coordinator.reconcile("a".repeat(64), policy(), START));
        store.rows = List.of(gap(0, "PUBLISHED"), gap(0, "PUBLISHED"));
        assertThrows(
                IllegalStateException.class,
                () -> coordinator.reconcile("a".repeat(64), policy(), START));
        store.rows =
                List.of(
                        gap(0, "PUBLISHED"),
                        gap(1, "PUBLISHED"),
                        gap(2, "PUBLISHED"),
                        gap(3, "PUBLISHED"),
                        gap(4, "PUBLISHED"));
        assertThrows(
                IllegalStateException.class,
                () -> coordinator.reconcile("a".repeat(64), policy(), START));
        store.badReceipt = true;
        assertThrows(
                IllegalStateException.class,
                () ->
                        coordinator.persistCatchUp(
                                UUID.randomUUID(),
                                "a".repeat(64),
                                policy(),
                                LocalDate.of(2024, 2, 1),
                                START.plusSeconds(864000)));
        assertThrows(IllegalArgumentException.class, () -> gap(0, "FORGED"));
    }

    @Test
    void temporalFingerprintCommitsAllExplicitMaterialUsingSqlUtf16() throws Exception {
        final var policy = policy();
        final String expected =
                java.util.HexFormat.of()
                        .formatHex(
                                java.security.MessageDigest.getInstance("SHA-256")
                                        .digest(
                                                policy.material()
                                                        .getBytes(
                                                                java.nio.charset.StandardCharsets
                                                                        .UTF_16LE)));
        assertEquals(expected, policy.fingerprint().sha256());
        final var json =
                new com.fasterxml.jackson.databind.ObjectMapper().readTree(policy.material());
        assertEquals(15, json.size());
        assertEquals(4, json.get("maximumReconciliation").asInt());
        assertEquals("Etc/UTC", json.get("zone").asText());
    }

    private static final class Store implements RuntimeTemporalStore {
        List<Gap> rows = List.of();
        int persisted;
        boolean badReceipt;

        @Override
        public int persist(
                final UUID plan,
                final String hash,
                final RuntimeTemporalPolicy policy,
                final RuntimeTemporalPlanner.Result result) {
            persisted = result.windows().size();
            return badReceipt ? -1 : persisted;
        }

        @Override
        public List<Gap> readGapPage(final String hash, final int maximum, final Instant after) {
            assertEquals(4, maximum);
            return rows;
        }
    }
}
